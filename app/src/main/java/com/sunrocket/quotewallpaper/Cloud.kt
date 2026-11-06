package com.sunrocket.quotewallpaper

import android.app.Activity
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

/** 서버(Firestore)에 올라간 공개 명언 */
data class RemoteQuote(
    val id: String,
    val text: String,
    val author: String,
    val category: String,
    val ownerUid: String,
    val ownerName: String,
    val createdAt: Long,
) {
    fun toQuote() = Quote("remote-$id", text, author.ifBlank { ownerName }, category, mine = false)
}

data class Profile(val uid: String, val name: String)

/**
 * 서버 구조
 * users/{uid}                  이름
 * users/{uid}/following/{uid}  내가 팔로우한 사람
 * quotes/{id}                  공개 명언 (id는 내 명언함의 id와 같음)
 * reports/{id}                 신고
 */
object Cloud {
    private val auth: FirebaseAuth get() = FirebaseAuth.getInstance()
    private val db: FirebaseFirestore get() = FirebaseFirestore.getInstance()
    private val wordSplit = Regex("[^\\p{L}\\p{N}]+")

    fun uid(): String? = auth.currentUser?.uid

    // ---------- 로그인 ----------

    /** 구글 계정으로 로그인. 사용자가 창을 닫으면 실패로 끝남 */
    suspend fun signIn(activity: Activity): Profile {
        val option = GetSignInWithGoogleOption.Builder(activity.getString(R.string.default_web_client_id)).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val result = CredentialManager.create(activity).getCredential(activity, request)
        val credential = result.credential
        if (credential !is CustomCredential ||
            credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            error("구글 계정 정보를 받지 못했어요")
        }
        val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
        auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await()
        return myProfile() ?: error("로그인하지 못했어요")
    }

    suspend fun signOut(activity: Activity) {
        auth.signOut()
        runCatching { CredentialManager.create(activity).clearCredentialState(ClearCredentialStateRequest()) }
    }

    // ---------- 프로필 ----------

    /** 내 프로필. 처음 로그인했으면 구글 이름으로 만들어 둠 */
    suspend fun myProfile(): Profile? {
        val user = auth.currentUser ?: return null
        val existing = db.collection("users").document(user.uid).get().await().getString("name")
        if (existing != null) return Profile(user.uid, existing)
        val name = (user.displayName ?: "이름 없음").trim().take(20).ifEmpty { "이름 없음" }
        writeName(user.uid, name)
        return Profile(user.uid, name)
    }

    suspend fun profile(uid: String): Profile? {
        val name = db.collection("users").document(uid).get().await().getString("name") ?: return null
        return Profile(uid, name)
    }

    /** 이름 바꾸기. 이미 올린 명언에 붙은 이름도 같이 바꿈 */
    suspend fun rename(name: String) {
        val uid = uid() ?: error("로그인이 필요해요")
        writeName(uid, name)
        val docs = db.collection("quotes").whereEqualTo("ownerUid", uid).get().await().documents
        for (doc in docs) doc.reference.update("ownerName", name).await()
    }

    private suspend fun writeName(uid: String, name: String) {
        db.collection("users").document(uid).set(
            mapOf(
                "name" to name,
                "nameLower" to name.lowercase(),
                "updatedAt" to FieldValue.serverTimestamp(),
            )
        ).await()
    }

    /** 이름이 q로 시작하는 사람 찾기 */
    suspend fun searchUsers(q: String): List<Profile> {
        val key = q.trim().lowercase()
        if (key.isEmpty()) return emptyList()
        return db.collection("users")
            .orderBy("nameLower")
            .startAt(key)
            .endAt(key + "")
            .limit(30)
            .get().await().documents
            .mapNotNull { d -> d.getString("name")?.let { Profile(d.id, it) } }
    }

    // ---------- 팔로우 ----------

    suspend fun followingIds(): List<String> {
        val me = uid() ?: return emptyList()
        return db.collection("users").document(me).collection("following").get().await().documents.map { it.id }
    }

    suspend fun isFollowing(target: String): Boolean {
        val me = uid() ?: return false
        return db.collection("users").document(me).collection("following").document(target).get().await().exists()
    }

    suspend fun follow(target: String) {
        val me = uid() ?: error("로그인이 필요해요")
        db.collection("users").document(me).collection("following").document(target)
            .set(mapOf("since" to FieldValue.serverTimestamp())).await()
    }

    suspend fun unfollow(target: String) {
        val me = uid() ?: error("로그인이 필요해요")
        db.collection("users").document(me).collection("following").document(target).delete().await()
    }

    // ---------- 명언 ----------

    suspend fun latest(): List<RemoteQuote> =
        db.collection("quotes")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(50)
            .get().await().documents.mapNotNull(::toRemote)

    suspend fun byOwner(ownerUid: String): List<RemoteQuote> =
        db.collection("quotes")
            .whereEqualTo("ownerUid", ownerUid)
            .limit(100)
            .get().await().documents.mapNotNull(::toRemote)
            .sortedByDescending { it.createdAt }

    suspend fun followingFeed(): List<RemoteQuote> {
        val ids = followingIds()
        val result = mutableListOf<RemoteQuote>()
        for (chunk in ids.chunked(30)) {
            result += db.collection("quotes")
                .whereIn("ownerUid", chunk)
                .limit(100)
                .get().await().documents.mapNotNull(::toRemote)
        }
        return result.sortedByDescending { it.createdAt }
    }

    /** 명언·작성자에 검색어가 들어간 공개 명언 */
    suspend fun search(q: String): List<RemoteQuote> {
        val words = q.lowercase().split(wordSplit).filter { it.isNotBlank() }
        val first = words.firstOrNull()?.take(10) ?: return emptyList()
        return db.collection("quotes")
            .whereArrayContains("keywords", first)
            .limit(100)
            .get().await().documents.mapNotNull(::toRemote)
            .filter { rq ->
                val hay = "${rq.text} ${rq.author} ${rq.ownerName}".lowercase()
                words.all { hay.contains(it) }
            }
            .sortedByDescending { it.createdAt }
    }

    /** 내 명언을 공개로 올림 (이미 있으면 덮어씀) */
    suspend fun publish(quote: Quote) {
        val me = myProfile() ?: error("로그인이 필요해요")
        db.collection("quotes").document(quote.id).set(
            mapOf(
                "text" to quote.text,
                "author" to quote.author,
                "category" to quote.category,
                "ownerUid" to me.uid,
                "ownerName" to me.name,
                "createdAt" to FieldValue.serverTimestamp(),
                "keywords" to keywords(quote.text, quote.author),
            )
        ).await()
    }

    suspend fun unpublish(id: String) {
        db.collection("quotes").document(id).delete().await()
    }

    suspend fun report(quoteId: String, reason: String) {
        val me = uid() ?: error("로그인이 필요해요")
        db.collection("reports").add(
            mapOf(
                "quoteId" to quoteId,
                "reporterUid" to me,
                "reason" to reason,
                "createdAt" to FieldValue.serverTimestamp(),
            )
        ).await()
    }

    /** 자동 변경(백그라운드)에서 쓰는 버전. 실패하면 null */
    fun followingFeedBlocking(): List<RemoteQuote>? {
        if (uid() == null) return null
        return runCatching { runBlocking { withTimeout(20_000) { followingFeed() } } }.getOrNull()
    }

    /** 검색용 단어 조각: "노력은" → 노, 노력, 노력은 */
    fun keywords(text: String, author: String): List<String> {
        val words = "$text $author".lowercase().split(wordSplit).filter { it.isNotBlank() }
        val set = LinkedHashSet<String>()
        for (w in words) for (i in 1..minOf(w.length, 10)) set.add(w.substring(0, i))
        return set.take(100)
    }

    private fun toRemote(doc: DocumentSnapshot): RemoteQuote? {
        val text = doc.getString("text") ?: return null
        val owner = doc.getString("ownerUid") ?: return null
        return RemoteQuote(
            id = doc.id,
            text = text,
            author = doc.getString("author") ?: "",
            category = doc.getString("category") ?: "인생",
            ownerUid = owner,
            ownerName = doc.getString("ownerName") ?: "",
            // 방금 올려서 서버 시간이 아직 없으면 맨 위로
            createdAt = doc.getTimestamp("createdAt")?.toDate()?.time ?: Long.MAX_VALUE,
        )
    }
}
