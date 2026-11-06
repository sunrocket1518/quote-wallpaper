package com.sunrocket.quotewallpaper

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** 추천 명언 + 내 명언함 + 팔로우한 사람 명언(임시 저장) */
class QuoteRepository(context: Context) {
    private val prefs = context.getSharedPreferences("quotes", Context.MODE_PRIVATE)
    private val settings = Settings(context)

    fun famous(): List<Quote> = BuiltInQuotes.list

    fun mine(): List<Quote> = QuoteJson.parseList(prefs.getString("mine", "[]"), mine = true)

    /** 팔로우한 사람들의 공개 명언. 인터넷이 없을 때도 쓰려고 폰에 저장해 둠 */
    fun followingCache(): List<Quote> = QuoteJson.parseList(prefs.getString("following", "[]"), mine = false)

    fun saveFollowingCache(list: List<Quote>) {
        prefs.edit().putString("following", QuoteJson.toJson(list).toString()).apply()
    }

    fun all(): List<Quote> = famous() + mine()

    fun find(id: String?): Quote? {
        if (id == null) return null
        return all().firstOrNull { it.id == id }
            ?: followingCache().firstOrNull { it.id == id }
            ?: settings.currentQuote?.takeIf { it.id == id }
    }

    fun addMine(text: String, author: String, category: String, isPublic: Boolean = false): Quote {
        val quote = Quote("mine-" + UUID.randomUUID(), text, author, category, mine = true, isPublic = isPublic)
        saveMine(listOf(quote) + mine())
        return quote
    }

    fun deleteMine(id: String) = saveMine(mine().filterNot { it.id == id })

    fun setPublic(id: String, value: Boolean) =
        saveMine(mine().map { if (it.id == id) it.copy(isPublic = value) else it })

    /** 설정한 분야·종류에 맞는 명언 중 하나를 무작위로 고름 (지금 것과 겹치지 않게) */
    fun pick(settings: Settings, excludeId: String?): Quote? {
        val base = when (settings.source) {
            Settings.SOURCE_FAMOUS -> famous()
            Settings.SOURCE_MINE -> mine()
            Settings.SOURCE_FOLLOWING -> followingCache()
            else -> all()
        }
        val pool = base.filter { settings.category == Categories.ALL || it.category == settings.category }
        if (pool.isEmpty()) return null
        return pool.filter { it.id != excludeId }.ifEmpty { pool }.random()
    }

    private fun saveMine(list: List<Quote>) {
        prefs.edit().putString("mine", QuoteJson.toJson(list).toString()).apply()
    }
}

object QuoteJson {
    fun toJson(q: Quote): JSONObject = JSONObject()
        .put("id", q.id)
        .put("text", q.text)
        .put("author", q.author)
        .put("category", q.category)
        .put("public", q.isPublic)

    fun toJson(list: List<Quote>): JSONArray = JSONArray().also { arr -> list.forEach { arr.put(toJson(it)) } }

    fun fromJson(o: JSONObject, mine: Boolean): Quote = Quote(
        id = o.getString("id"),
        text = o.getString("text"),
        author = o.optString("author", "나"),
        category = o.optString("category", "인생"),
        mine = mine,
        isPublic = o.optBoolean("public", false),
    )

    fun parseList(raw: String?, mine: Boolean): List<Quote> {
        val arr = runCatching { JSONArray(raw ?: "[]") }.getOrElse { JSONArray() }
        return (0 until arr.length()).mapNotNull { i ->
            runCatching { fromJson(arr.getJSONObject(i), mine) }.getOrNull()
        }
    }
}

class Settings(context: Context) {
    private val p = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var category: String
        get() = p.getString("category", Categories.ALL) ?: Categories.ALL
        set(v) = p.edit().putString("category", v).apply()

    var source: Int
        get() = p.getInt("source", SOURCE_ALL).coerceIn(0, SOURCE_LABELS.size - 1)
        set(v) = p.edit().putInt("source", v).apply()

    var target: Int
        get() = p.getInt("target", TARGET_BOTH)
        set(v) = p.edit().putInt("target", v).apply()

    var intervalHours: Int
        get() = p.getInt("intervalHours", 24)
        set(v) = p.edit().putInt("intervalHours", v).apply()

    var autoEnabled: Boolean
        get() = p.getBoolean("autoEnabled", false)
        set(v) = p.edit().putBoolean("autoEnabled", v).apply()

    var currentId: String?
        get() = p.getString("currentId", null)
        set(v) = p.edit().putString("currentId", v).apply()

    /** 지금 배경화면에 있는 명언 (다른 사람 명언이어도 미리보기에 쓰려고 통째로 저장) */
    var currentQuote: Quote?
        get() = p.getString("currentQuote", null)?.let {
            runCatching { QuoteJson.fromJson(JSONObject(it), mine = false) }.getOrNull()
        }
        set(v) = p.edit().putString("currentQuote", v?.let { QuoteJson.toJson(it).toString() }).apply()

    /** 내가 숨긴 사람 / 숨긴 명언 */
    val hiddenOwners: Set<String> get() = p.getStringSet("hiddenOwners", emptySet())?.toSet() ?: emptySet()
    val hiddenQuotes: Set<String> get() = p.getStringSet("hiddenQuotes", emptySet())?.toSet() ?: emptySet()

    fun hideOwner(uid: String) = p.edit().putStringSet("hiddenOwners", hiddenOwners + uid).apply()
    fun hideQuote(id: String) = p.edit().putStringSet("hiddenQuotes", hiddenQuotes + id).apply()
    fun clearHidden() = p.edit().remove("hiddenOwners").remove("hiddenQuotes").apply()

    fun visible(list: List<RemoteQuote>): List<RemoteQuote> {
        val owners = hiddenOwners
        val quotes = hiddenQuotes
        return list.filter { it.ownerUid !in owners && it.id !in quotes }
    }

    companion object {
        const val SOURCE_ALL = 0
        const val SOURCE_FAMOUS = 1
        const val SOURCE_MINE = 2
        const val SOURCE_FOLLOWING = 3
        val SOURCE_LABELS = listOf("전체", "유명인 명언만", "내 명언만", "팔로우한 사람 명언")

        const val TARGET_HOME = 0
        const val TARGET_LOCK = 1
        const val TARGET_BOTH = 2
        val TARGET_LABELS = listOf("홈 화면", "잠금 화면", "홈 + 잠금 화면")

        val INTERVAL_HOURS = listOf(1, 3, 6, 12, 24)
        val INTERVAL_LABELS = listOf("1시간", "3시간", "6시간", "12시간", "하루")
    }
}
