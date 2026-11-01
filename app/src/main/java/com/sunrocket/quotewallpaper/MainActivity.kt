package com.sunrocket.quotewallpaper

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.materialswitch.MaterialSwitch

class MainActivity : AppCompatActivity() {
    private lateinit var settings: Settings
    private lateinit var repo: QuoteRepository
    private lateinit var preview: ImageView
    private lateinit var previewEmpty: TextView
    private lateinit var status: TextView
    private lateinit var btnChange: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        settings = Settings(this)
        repo = QuoteRepository(this)

        preview = findViewById(R.id.preview)
        previewEmpty = findViewById(R.id.previewEmpty)
        status = findViewById(R.id.status)
        btnChange = findViewById(R.id.btnChange)

        setupSpinner(
            findViewById(R.id.spinnerCategory),
            Categories.WITH_ALL,
            Categories.WITH_ALL.indexOf(settings.category).coerceAtLeast(0),
        ) { settings.category = Categories.WITH_ALL[it] }

        setupSpinner(findViewById(R.id.spinnerSource), Settings.SOURCE_LABELS, settings.source) {
            settings.source = it
        }

        setupSpinner(findViewById(R.id.spinnerTarget), Settings.TARGET_LABELS, settings.target) {
            settings.target = it
        }

        setupSpinner(
            findViewById(R.id.spinnerInterval),
            Settings.INTERVAL_LABELS,
            Settings.INTERVAL_HOURS.indexOf(settings.intervalHours).coerceAtLeast(0),
        ) {
            settings.intervalHours = Settings.INTERVAL_HOURS[it]
            if (settings.autoEnabled) Scheduler.enable(this, settings.intervalHours)
            updateStatus()
        }

        val switchAuto = findViewById<MaterialSwitch>(R.id.switchAuto)
        switchAuto.isChecked = settings.autoEnabled
        switchAuto.setOnCheckedChangeListener { _, checked ->
            settings.autoEnabled = checked
            if (checked) Scheduler.enable(this, settings.intervalHours) else Scheduler.disable(this)
            updateStatus()
        }

        btnChange.setOnClickListener { changeNow() }
        findViewById<Button>(R.id.btnDesign).setOnClickListener {
            startActivity(Intent(this, DesignActivity::class.java))
        }
        findViewById<Button>(R.id.btnQuotes).setOnClickListener {
            startActivity(Intent(this, QuotesActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPreview()
        updateStatus()
    }

    private fun changeNow() {
        btnChange.isEnabled = false
        val app = applicationContext
        Thread {
            val result = runCatching { Changer.changeRandom(app) }
            runOnUiThread {
                btnChange.isEnabled = true
                val message = when {
                    result.isFailure -> "배경화면을 바꾸지 못했어요. 다시 시도해 주세요."
                    result.getOrNull() == null -> "고른 분야와 종류에 맞는 명언이 없어요. 내 명언함에 먼저 써보세요."
                    else -> "배경화면을 바꿨어요"
                }
                Toast.makeText(app, message, Toast.LENGTH_SHORT).show()
                refreshPreview()
            }
        }.start()
    }

    private fun refreshPreview() {
        val app = applicationContext
        Thread {
            val quote = repo.find(settings.currentId)
            val bmp = quote?.let { WallpaperPainter.render(app, it, scale = 0.4f) }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (bmp != null) {
                    preview.setImageBitmap(bmp)
                    preview.visibility = View.VISIBLE
                    previewEmpty.visibility = View.GONE
                } else {
                    preview.visibility = View.GONE
                    previewEmpty.visibility = View.VISIBLE
                }
            }
        }.start()
    }

    private fun updateStatus() {
        status.text = if (settings.autoEnabled) {
            val i = Settings.INTERVAL_HOURS.indexOf(settings.intervalHours).coerceAtLeast(0)
            "${Settings.INTERVAL_LABELS[i]}마다 자동으로 바뀌어요"
        } else {
            "자동 변경이 꺼져 있어요"
        }
    }

    private fun setupSpinner(spinner: Spinner, items: List<String>, selected: Int, onSelect: (Int) -> Unit) {
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, items)
        spinner.setSelection(selected, false)
        var last = selected
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position != last) {
                    last = position
                    onSelect(position)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }
}
