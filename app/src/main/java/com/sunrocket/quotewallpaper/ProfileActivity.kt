package com.sunrocket.quotewallpaper

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

/** 한 사람의 프로필: 이름, 팔로우, 그 사람이 공개한 명언 */
class ProfileActivity : AppCompatActivity() {
    private lateinit var uid: String
    private var isMe = false
    private var following = false
    private var name = ""
    private lateinit var nameView: TextView
    private lateinit var btnFollow: Button
    private lateinit var progress: ProgressBar
    private lateinit var empty: TextView
    private lateinit var adapter: RemoteQuoteAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        uid = intent.getStringExtra(EXTRA_UID) ?: run { finish(); return }
        setContentView(R.layout.activity_profile)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        isMe = uid == Cloud.uid()

        nameView = findViewById(R.id.profileName)
        btnFollow = findViewById(R.id.btnFollow)
        progress = findViewById(R.id.profileProgress)
        empty = findViewById(R.id.profileEmpty)
        adapter = RemoteQuoteAdapter { QuoteActions.show(this, it) { load() } }
        findViewById<RecyclerView>(R.id.profileList).apply {
            layoutManager = LinearLayoutManager(this@ProfileActivity)
            adapter = this@ProfileActivity.adapter
        }

        val btnRename = findViewById<Button>(R.id.btnRename)
        val btnSignOut = findViewById<Button>(R.id.btnSignOut)
        val btnHideUser = findViewById<Button>(R.id.btnHideUser)
        val btnShowHidden = findViewById<Button>(R.id.btnShowHidden)

        btnFollow.visibility = if (isMe) View.GONE else View.VISIBLE
        btnHideUser.visibility = if (isMe) View.GONE else View.VISIBLE
        btnRename.visibility = if (isMe) View.VISIBLE else View.GONE
        btnSignOut.visibility = if (isMe) View.VISIBLE else View.GONE
        btnShowHidden.visibility = if (isMe) View.VISIBLE else View.GONE

        btnFollow.setOnClickListener { toggleFollow() }
        btnRename.setOnClickListener { showRename() }
        btnSignOut.setOnClickListener {
            lifecycleScope.launch {
                Cloud.signOut(this@ProfileActivity)
                toast("로그아웃했어요")
                finish()
            }
        }
        btnHideUser.setOnClickListener {
            Settings(this).hideOwner(uid)
            lifecycleScope.launch { runCatching { Cloud.unfollow(uid) } }
            toast("$name 님의 명언을 더 이상 보지 않아요")
            finish()
        }
        btnShowHidden.setOnClickListener {
            Settings(this).clearHidden()
            toast("숨긴 사람과 명언을 다시 보이게 했어요")
        }

        load()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun load() {
        progress.visibility = View.VISIBLE
        lifecycleScope.launch {
            val result = runCatching {
                val profile = Cloud.profile(uid)
                val quotes = Cloud.byOwner(uid)
                val follows = if (isMe) false else Cloud.isFollowing(uid)
                Triple(profile, quotes, follows)
            }
            progress.visibility = View.GONE
            result.onSuccess { (profile, quotes, follows) ->
                name = profile?.name ?: "알 수 없는 사용자"
                nameView.text = if (isMe) "$name (나)" else name
                title = name
                following = follows
                updateFollowButton()
                val visible = if (isMe) quotes else Settings(this@ProfileActivity).visible(quotes)
                adapter.submit(visible)
                empty.visibility = if (visible.isEmpty()) View.VISIBLE else View.GONE
                empty.text = if (isMe) "아직 공개한 명언이 없어요.\n내 명언함에서 명언을 눌러 '공개하기'를 골라 보세요."
                else "아직 공개한 명언이 없어요"
            }.onFailure {
                empty.visibility = View.VISIBLE
                empty.text = "프로필을 불러오지 못했어요. 인터넷 연결을 확인해 주세요."
            }
        }
    }

    private fun updateFollowButton() {
        btnFollow.text = if (following) "팔로우 중 (누르면 취소)" else "팔로우"
    }

    private fun toggleFollow() {
        btnFollow.isEnabled = false
        lifecycleScope.launch {
            val result = runCatching { if (following) Cloud.unfollow(uid) else Cloud.follow(uid) }
            btnFollow.isEnabled = true
            if (result.isSuccess) {
                following = !following
                updateFollowButton()
                toast(if (following) "$name 님을 팔로우했어요" else "팔로우를 취소했어요")
            } else {
                toast("처리하지 못했어요. 인터넷 연결을 확인해 주세요")
            }
        }
    }

    private fun showRename() {
        val input = EditText(this).apply {
            setText(name)
            inputType = InputType.TYPE_CLASS_TEXT
            setSelection(text.length)
        }
        val box = FrameLayout(this).apply {
            val pad = (20 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad / 2, pad, 0)
            addView(input)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("이름 바꾸기")
            .setMessage("다른 사람에게 보이는 이름이에요 (20자까지)")
            .setView(box)
            .setPositiveButton("저장") { _, _ ->
                val newName = input.text.toString().trim().take(20)
                if (newName.isEmpty()) return@setPositiveButton
                lifecycleScope.launch {
                    val ok = runCatching { Cloud.rename(newName) }.isSuccess
                    toast(if (ok) "이름을 바꿨어요" else "이름을 바꾸지 못했어요")
                    load()
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    companion object {
        private const val EXTRA_UID = "uid"

        fun open(context: Context, uid: String) {
            context.startActivity(Intent(context, ProfileActivity::class.java).putExtra(EXTRA_UID, uid))
        }
    }
}
