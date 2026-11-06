package com.sunrocket.quotewallpaper

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import kotlinx.coroutines.launch

class QuotesActivity : AppCompatActivity() {
    private lateinit var repo: QuoteRepository
    private lateinit var adapter: QuoteAdapter
    private lateinit var toggle: MaterialButtonToggleGroup
    private lateinit var empty: TextView
    private var showMine = false
    private var query = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_quotes)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        repo = QuoteRepository(this)
        empty = findViewById(R.id.empty)

        adapter = QuoteAdapter { onQuoteTap(it) }
        findViewById<RecyclerView>(R.id.list).apply {
            layoutManager = LinearLayoutManager(this@QuotesActivity)
            adapter = this@QuotesActivity.adapter
        }

        toggle = findViewById(R.id.toggle)
        toggle.check(R.id.btnFamous)
        toggle.addOnButtonCheckedListener { _, id, checked ->
            if (checked) {
                showMine = id == R.id.btnMine
                reload()
            }
        }

        findViewById<EditText>(R.id.search).doAfterTextChanged {
            query = it?.toString()?.trim().orEmpty()
            reload()
        }

        findViewById<Button>(R.id.btnAdd).setOnClickListener { showAddDialog() }
        reload()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun reload() {
        val base = if (showMine) repo.mine() else repo.famous()
        val list = base.filter {
            query.isEmpty() || it.text.contains(query, ignoreCase = true) || it.author.contains(query, ignoreCase = true)
        }
        adapter.submit(list)
        empty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        empty.text = when {
            query.isNotEmpty() -> "'$query'에 맞는 명언이 없어요"
            showMine -> "아직 쓴 명언이 없어요.\n아래 '명언 쓰기'로 첫 명언을 남겨보세요."
            else -> "명언이 없어요"
        }
    }

    private fun onQuoteTap(quote: Quote) {
        val options = mutableListOf("지금 배경화면으로 설정")
        if (quote.mine) {
            options += if (quote.isPublic) "비공개로 바꾸기" else "공개하기 (다른 사람도 볼 수 있음)"
            options += "삭제"
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(quote.text)
            .setItems(options.toTypedArray()) { _, which ->
                when (options[which]) {
                    "지금 배경화면으로 설정" -> applyQuote(quote)
                    "삭제" -> confirmDelete(quote)
                    else -> setPublic(quote, !quote.isPublic)
                }
            }
            .show()
    }

    private fun applyQuote(quote: Quote) {
        val app = applicationContext
        Thread {
            val result = runCatching { Changer.setQuote(app, quote) }
            runOnUiThread {
                val message = if (result.isSuccess) "배경화면을 바꿨어요" else "배경화면을 바꾸지 못했어요"
                Toast.makeText(app, message, Toast.LENGTH_SHORT).show()
            }
        }.start()
    }

    private fun setPublic(quote: Quote, makePublic: Boolean) {
        if (Cloud.uid() == null) {
            Toast.makeText(this, "공개하려면 먼저 '명언 둘러보기'에서 로그인해 주세요", Toast.LENGTH_LONG).show()
            return
        }
        lifecycleScope.launch {
            val result = runCatching {
                if (makePublic) Cloud.publish(quote) else Cloud.unpublish(quote.id)
            }
            if (result.isSuccess) {
                repo.setPublic(quote.id, makePublic)
                reload()
                toast(if (makePublic) "공개했어요. 다른 사람도 볼 수 있어요" else "비공개로 바꿨어요")
            } else {
                toast("서버에 저장하지 못했어요. 인터넷 연결을 확인해 주세요")
            }
        }
    }

    private fun confirmDelete(quote: Quote) {
        MaterialAlertDialogBuilder(this)
            .setMessage(if (quote.isPublic) "이 명언을 지울까요? 공개된 명언도 같이 지워져요." else "이 명언을 내 명언함에서 지울까요?")
            .setPositiveButton("삭제") { _, _ ->
                if (quote.isPublic) {
                    lifecycleScope.launch { runCatching { Cloud.unpublish(quote.id) } }
                }
                repo.deleteMine(quote.id)
                reload()
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun showAddDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_add_quote, null)
        val inputText = view.findViewById<EditText>(R.id.inputText)
        val inputAuthor = view.findViewById<EditText>(R.id.inputAuthor)
        val inputCategory = view.findViewById<Spinner>(R.id.inputCategory)
        val inputPublic = view.findViewById<MaterialSwitch>(R.id.inputPublic)
        inputCategory.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, Categories.LIST)

        MaterialAlertDialogBuilder(this)
            .setTitle("명언 쓰기")
            .setView(view)
            .setPositiveButton("저장") { _, _ ->
                val text = inputText.text.toString().trim().take(200)
                if (text.isEmpty()) {
                    toast("명언 내용을 입력해 주세요")
                    return@setPositiveButton
                }
                val author = inputAuthor.text.toString().trim().take(50).ifEmpty { "나" }
                val category = Categories.LIST[inputCategory.selectedItemPosition.coerceAtLeast(0)]
                val quote = repo.addMine(text, author, category)
                if (!showMine) toggle.check(R.id.btnMine) else reload()
                if (inputPublic.isChecked) setPublic(quote, true) else toast("내 명언함에 저장했어요")
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}

class QuoteAdapter(private val onTap: (Quote) -> Unit) : RecyclerView.Adapter<QuoteAdapter.Holder>() {
    private var items: List<Quote> = emptyList()

    @SuppressLint("NotifyDataSetChanged")
    fun submit(list: List<Quote>) {
        items = list
        notifyDataSetChanged()
    }

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val text: TextView = view.findViewById(R.id.quoteText)
        val meta: TextView = view.findViewById(R.id.quoteMeta)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_quote, parent, false))

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val quote = items[position]
        holder.text.text = quote.text
        val visibility = if (!quote.mine) "" else if (quote.isPublic) " · 공개" else " · 비공개"
        holder.meta.text = "— ${quote.author} · ${quote.category}$visibility"
        holder.itemView.setOnClickListener { onTap(quote) }
    }
}

class RemoteQuoteAdapter(private val onTap: (RemoteQuote) -> Unit) :
    RecyclerView.Adapter<QuoteAdapter.Holder>() {
    private var items: List<RemoteQuote> = emptyList()

    @SuppressLint("NotifyDataSetChanged")
    fun submit(list: List<RemoteQuote>) {
        items = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QuoteAdapter.Holder =
        QuoteAdapter.Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_quote, parent, false))

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: QuoteAdapter.Holder, position: Int) {
        val q = items[position]
        holder.text.text = q.text
        val author = q.author.ifBlank { q.ownerName }
        holder.meta.text = "— $author · ${q.category} · 올린 사람 ${q.ownerName}"
        holder.itemView.setOnClickListener { onTap(q) }
    }
}
