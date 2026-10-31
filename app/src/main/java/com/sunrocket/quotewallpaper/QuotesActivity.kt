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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder

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
        val options = if (quote.mine) arrayOf("지금 배경화면으로 설정", "삭제") else arrayOf("지금 배경화면으로 설정")
        MaterialAlertDialogBuilder(this)
            .setTitle(quote.text)
            .setItems(options) { _, which ->
                if (which == 0) applyQuote(quote) else confirmDelete(quote)
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

    private fun confirmDelete(quote: Quote) {
        MaterialAlertDialogBuilder(this)
            .setMessage("이 명언을 내 명언함에서 지울까요?")
            .setPositiveButton("삭제") { _, _ ->
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
        inputCategory.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, Categories.LIST)

        MaterialAlertDialogBuilder(this)
            .setTitle("명언 쓰기")
            .setView(view)
            .setPositiveButton("저장") { _, _ ->
                val text = inputText.text.toString().trim()
                if (text.isEmpty()) {
                    Toast.makeText(this, "명언 내용을 입력해 주세요", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val author = inputAuthor.text.toString().trim().ifEmpty { "나" }
                val category = Categories.LIST[inputCategory.selectedItemPosition.coerceAtLeast(0)]
                repo.addMine(text, author, category)
                if (!showMine) toggle.check(R.id.btnMine) else reload()
                Toast.makeText(this, "내 명언함에 저장했어요", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("취소", null)
            .show()
    }
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
        holder.meta.text = "— ${quote.author} · ${quote.category}"
        holder.itemView.setOnClickListener { onTap(quote) }
    }
}
