package com.sunrocket.quotewallpaper

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

/** 공개 명언 둘러보기: 최신 / 팔로우 / 검색 */
class CommunityActivity : AppCompatActivity() {
    private lateinit var settings: Settings
    private lateinit var repo: QuoteRepository
    private lateinit var adapter: RemoteQuoteAdapter
    private lateinit var signedOut: View
    private lateinit var signedIn: View
    private lateinit var progress: ProgressBar
    private lateinit var empty: TextView
    private lateinit var search: EditText
    private var followingMode = false
    private var query = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_community)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        settings = Settings(this)
        repo = QuoteRepository(this)

        signedOut = findViewById(R.id.signedOut)
        signedIn = findViewById(R.id.signedIn)
        progress = findViewById(R.id.progress)
        empty = findViewById(R.id.empty)
        search = findViewById(R.id.remoteSearch)

        adapter = RemoteQuoteAdapter { onTap(it) }
        findViewById<RecyclerView>(R.id.remoteList).apply {
            layoutManager = LinearLayoutManager(this@CommunityActivity)
            adapter = this@CommunityActivity.adapter
        }

        findViewById<Button>(R.id.btnSignIn).setOnClickListener { signIn(it as Button) }
        findViewById<Button>(R.id.btnMyProfile).setOnClickListener {
            Cloud.uid()?.let { uid -> ProfileActivity.open(this, uid) }
        }
        findViewById<Button>(R.id.btnPeople).setOnClickListener {
            startActivity(Intent(this, UserSearchActivity::class.java))
        }

        val toggle = findViewById<MaterialButtonToggleGroup>(R.id.feedToggle)
        toggle.check(R.id.btnLatest)
        toggle.addOnButtonCheckedListener { _, id, checked ->
            if (checked) {
                followingMode = id == R.id.btnFollowing
                load()
            }
        }

        search.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                query = v.text.toString().trim()
                hideKeyboard(v)
                load()
                true
            } else false
        }
    }

    override fun onResume() {
        super.onResume()
        refreshUi()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun refreshUi() {
        val signed = Cloud.uid() != null
        signedOut.visibility = if (signed) View.GONE else View.VISIBLE
        signedIn.visibility = if (signed) View.VISIBLE else View.GONE
        if (signed) load()
    }

    private fun signIn(button: Button) {
        button.isEnabled = false
        lifecycleScope.launch {
            val result = runCatching { Cloud.signIn(this@CommunityActivity) }
            button.isEnabled = true
            result.onSuccess {
                toast("${it.name} 님, 반가워요")
                refreshUi()
            }.onFailure { e ->
                if (e !is GetCredentialCancellationException) {
                    toast("로그인하지 못했어요. 인터넷 연결과 구글 계정을 확인해 주세요")
                }
            }
        }
    }

    private fun load() {
        progress.visibility = View.VISIBLE
        empty.visibility = View.GONE
        val searching = query.isNotEmpty()
        val following = followingMode
        lifecycleScope.launch {
            val result = runCatching {
                when {
                    searching -> Cloud.search(query)
                    following -> Cloud.followingFeed()
                    else -> Cloud.latest()
                }
            }
            progress.visibility = View.GONE
            result.onSuccess { list ->
                val visible = settings.visible(list)
                if (following && !searching) repo.saveFollowingCache(visible.map { it.toQuote() })
                adapter.submit(visible)
                empty.visibility = if (visible.isEmpty()) View.VISIBLE else View.GONE
                empty.text = when {
                    searching -> "'$query'에 맞는 공개 명언이 없어요"
                    following -> "아직 팔로우한 사람이 없거나, 팔로우한 사람이 올린 명언이 없어요.\n'사람 찾기'에서 팔로우해 보세요."
                    else -> "아직 공개된 명언이 없어요.\n내 명언함에서 첫 명언을 공개해 보세요."
                }
            }.onFailure {
                adapter.submit(emptyList())
                empty.visibility = View.VISIBLE
                empty.text = "명언을 불러오지 못했어요. 인터넷 연결을 확인해 주세요."
            }
        }
    }

    private fun onTap(q: RemoteQuote) {
        QuoteActions.show(this, q) { load() }
    }

    private fun hideKeyboard(v: View) {
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(v.windowToken, 0)
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}

/** 공개 명언을 눌렀을 때 나오는 메뉴 (둘러보기·프로필 화면 공통) */
object QuoteActions {
    fun show(activity: AppCompatActivity, q: RemoteQuote, onHidden: () -> Unit) {
        val isMine = q.ownerUid == Cloud.uid()
        val options = mutableListOf("지금 배경화면으로 설정", "내 명언함에 저장")
        if (activity !is ProfileActivity) options += "${q.ownerName} 님 프로필 보기"
        if (!isMine) {
            options += "신고하기"
            options += "이 명언 숨기기"
        }
        MaterialAlertDialogBuilder(activity)
            .setTitle(q.text)
            .setItems(options.toTypedArray()) { _, which ->
                val choice = options[which]
                when {
                    choice == "지금 배경화면으로 설정" -> applyWallpaper(activity, q)
                    choice == "내 명언함에 저장" -> {
                        QuoteRepository(activity).addMine(q.text, q.author.ifBlank { q.ownerName }, q.category)
                        toast(activity, "내 명언함에 저장했어요")
                    }
                    choice.endsWith("프로필 보기") -> ProfileActivity.open(activity, q.ownerUid)
                    choice == "신고하기" -> report(activity, q, onHidden)
                    choice == "이 명언 숨기기" -> {
                        Settings(activity).hideQuote(q.id)
                        toast(activity, "이 명언을 숨겼어요")
                        onHidden()
                    }
                }
            }
            .show()
    }

    private fun applyWallpaper(activity: Activity, q: RemoteQuote) {
        val app = activity.applicationContext
        Thread {
            val ok = runCatching { Changer.setQuote(app, q.toQuote()) }.isSuccess
            activity.runOnUiThread { toast(app, if (ok) "배경화면을 바꿨어요" else "배경화면을 바꾸지 못했어요") }
        }.start()
    }

    private fun report(activity: AppCompatActivity, q: RemoteQuote, onHidden: () -> Unit) {
        val reasons = arrayOf("욕설·비하", "음란·폭력", "광고·도배", "기타")
        MaterialAlertDialogBuilder(activity)
            .setTitle("신고 이유")
            .setItems(reasons) { _, which ->
                activity.lifecycleScope.launch {
                    val ok = runCatching { Cloud.report(q.id, reasons[which]) }.isSuccess
                    Settings(activity).hideQuote(q.id)
                    toast(activity, if (ok) "신고했어요. 이 명언은 이제 안 보여요" else "신고를 보내지 못했지만, 이 명언은 숨겼어요")
                    onHidden()
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun toast(context: Context, message: String) =
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}
