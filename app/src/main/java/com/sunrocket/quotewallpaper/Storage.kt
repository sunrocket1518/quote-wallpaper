package com.sunrocket.quotewallpaper

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** 추천 명언 + 내 명언함(폰 안에 저장) */
class QuoteRepository(context: Context) {
    private val prefs = context.getSharedPreferences("quotes", Context.MODE_PRIVATE)

    fun famous(): List<Quote> = BuiltInQuotes.list

    fun mine(): List<Quote> {
        val arr = JSONArray(prefs.getString("mine", "[]") ?: "[]")
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Quote(
                id = o.getString("id"),
                text = o.getString("text"),
                author = o.optString("author", "나"),
                category = o.optString("category", "인생"),
                mine = true,
            )
        }
    }

    fun all(): List<Quote> = famous() + mine()

    fun find(id: String?): Quote? = if (id == null) null else all().firstOrNull { it.id == id }

    fun addMine(text: String, author: String, category: String) {
        val quote = Quote("mine-" + UUID.randomUUID(), text, author, category, mine = true)
        save(listOf(quote) + mine())
    }

    fun deleteMine(id: String) = save(mine().filterNot { it.id == id })

    /** 설정한 분야·종류에 맞는 명언 중 하나를 무작위로 고름 (지금 것과 겹치지 않게) */
    fun pick(settings: Settings, excludeId: String?): Quote? {
        val pool = all().filter { q ->
            val categoryOk = settings.category == Categories.ALL || q.category == settings.category
            val sourceOk = when (settings.source) {
                Settings.SOURCE_FAMOUS -> !q.mine
                Settings.SOURCE_MINE -> q.mine
                else -> true
            }
            categoryOk && sourceOk
        }
        if (pool.isEmpty()) return null
        return pool.filter { it.id != excludeId }.ifEmpty { pool }.random()
    }

    private fun save(list: List<Quote>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject()
                    .put("id", it.id)
                    .put("text", it.text)
                    .put("author", it.author)
                    .put("category", it.category)
            )
        }
        prefs.edit().putString("mine", arr.toString()).apply()
    }
}

class Settings(context: Context) {
    private val p = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var category: String
        get() = p.getString("category", Categories.ALL) ?: Categories.ALL
        set(v) = p.edit().putString("category", v).apply()

    var source: Int
        get() = p.getInt("source", SOURCE_ALL)
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

    companion object {
        const val SOURCE_ALL = 0
        const val SOURCE_FAMOUS = 1
        const val SOURCE_MINE = 2
        val SOURCE_LABELS = listOf("전체", "유명인 명언만", "내 명언만")

        const val TARGET_HOME = 0
        const val TARGET_LOCK = 1
        const val TARGET_BOTH = 2
        val TARGET_LABELS = listOf("홈 화면", "잠금 화면", "홈 + 잠금 화면")

        val INTERVAL_HOURS = listOf(1, 3, 6, 12, 24)
        val INTERVAL_LABELS = listOf("1시간", "3시간", "6시간", "12시간", "하루")
    }
}
