package com.sunrocket.quotewallpaper

import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/** 이름으로 사람 찾기 */
class UserSearchActivity : AppCompatActivity() {
    private var results: List<Profile> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_search)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val input = findViewById<EditText>(R.id.userQuery)
        val list = findViewById<ListView>(R.id.userList)
        val progress = findViewById<ProgressBar>(R.id.userProgress)
        val empty = findViewById<TextView>(R.id.userEmpty)

        list.setOnItemClickListener { _, _, position, _ ->
            results.getOrNull(position)?.let { ProfileActivity.open(this, it.uid) }
        }

        input.setOnEditorActionListener { v, actionId, _ ->
            if (actionId != EditorInfo.IME_ACTION_SEARCH) return@setOnEditorActionListener false
            val q = v.text.toString().trim()
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                .hideSoftInputFromWindow(v.windowToken, 0)
            if (q.isEmpty()) return@setOnEditorActionListener true
            progress.visibility = View.VISIBLE
            empty.visibility = View.GONE
            lifecycleScope.launch {
                val result = runCatching { Cloud.searchUsers(q) }
                progress.visibility = View.GONE
                val hidden = Settings(this@UserSearchActivity).hiddenOwners
                results = result.getOrDefault(emptyList()).filter { it.uid !in hidden }
                val me = Cloud.uid()
                list.adapter = ArrayAdapter(
                    this@UserSearchActivity,
                    android.R.layout.simple_list_item_1,
                    results.map { if (it.uid == me) "${it.name} (나)" else it.name },
                )
                if (results.isEmpty()) {
                    empty.visibility = View.VISIBLE
                    empty.text = if (result.isFailure) "찾지 못했어요. 인터넷 연결을 확인해 주세요."
                    else "'$q'(으)로 시작하는 이름이 없어요"
                }
            }
            true
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
