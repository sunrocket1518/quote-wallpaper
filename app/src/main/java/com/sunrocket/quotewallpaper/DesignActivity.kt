package com.sunrocket.quotewallpaper

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.Spinner
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.materialswitch.MaterialSwitch

class DesignActivity : AppCompatActivity() {
    private lateinit var design: Design
    private lateinit var settings: Settings
    private lateinit var repo: QuoteRepository
    private lateinit var preview: ImageView
    private lateinit var spinnerBg: Spinner
    private lateinit var btnPhoto: Button
    private var renderGeneration = 0

    private val pickPhoto = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri == null) {
            // 사진을 안 고르고 돌아오면 원래 배경으로 되돌림
            spinnerBg.setSelection(design.background)
            return@registerForActivityResult
        }
        val app = applicationContext
        Thread {
            val ok = runCatching { WallpaperPainter.savePhoto(app, uri) }.isSuccess
            runOnUiThread {
                if (ok) {
                    update(design.copy(background = Design.BG_PHOTO))
                } else {
                    Toast.makeText(app, "사진을 불러오지 못했어요. 다른 사진을 골라보세요.", Toast.LENGTH_SHORT).show()
                    spinnerBg.setSelection(design.background)
                }
            }
        }.start()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_design)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        settings = Settings(this)
        repo = QuoteRepository(this)
        design = Design.load(this)

        preview = findViewById(R.id.designPreview)
        spinnerBg = findViewById(R.id.spinnerBg)
        btnPhoto = findViewById(R.id.btnPhoto)

        findViewById<Spinner>(R.id.spinnerFont).bind(Design.FONT_LABELS, design.font) {
            update(design.copy(font = it))
        }
        spinnerBg.bind(Design.BG_LABELS, design.background) {
            if (it == Design.BG_PHOTO && !WallpaperPainter.photoFile(this).exists()) {
                launchPicker()
            } else {
                update(design.copy(background = it))
            }
        }
        findViewById<Spinner>(R.id.spinnerPosition).bind(Design.POSITION_LABELS, design.position) {
            update(design.copy(position = it))
        }
        findViewById<Spinner>(R.id.spinnerSize).bind(Design.SIZE_LABELS, design.size) {
            update(design.copy(size = it))
        }
        findViewById<Spinner>(R.id.spinnerAlign).bind(Design.ALIGN_LABELS, design.align) {
            update(design.copy(align = it))
        }
        findViewById<MaterialSwitch>(R.id.switchQuoteMark).apply {
            isChecked = design.quoteMark
            setOnCheckedChangeListener { _, checked -> update(design.copy(quoteMark = checked)) }
        }
        findViewById<MaterialSwitch>(R.id.switchDate).apply {
            isChecked = design.dateLine
            setOnCheckedChangeListener { _, checked -> update(design.copy(dateLine = checked)) }
        }

        btnPhoto.setOnClickListener { launchPicker() }
        findViewById<Button>(R.id.btnApplyDesign).setOnClickListener { applyNow(it as Button) }

        refresh()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun launchPicker() {
        pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    private fun update(newDesign: Design) {
        design = newDesign
        design.save(this)
        refresh()
    }

    private fun previewQuote(): Quote =
        repo.find(settings.currentId) ?: BuiltInQuotes.list.first()

    private fun refresh() {
        btnPhoto.visibility = if (design.background == Design.BG_PHOTO) View.VISIBLE else View.GONE
        val app = applicationContext
        val snapshot = design
        val quote = previewQuote()
        val generation = ++renderGeneration
        Thread {
            val bmp = runCatching { WallpaperPainter.render(app, quote, scale = 0.4f, design = snapshot) }.getOrNull()
            runOnUiThread {
                if (generation != renderGeneration || isFinishing || isDestroyed) return@runOnUiThread
                if (bmp != null) preview.setImageBitmap(bmp)
            }
        }.start()
    }

    private fun applyNow(button: Button) {
        button.isEnabled = false
        val app = applicationContext
        Thread {
            val result = runCatching {
                val quote = repo.find(settings.currentId) ?: repo.pick(settings, null) ?: BuiltInQuotes.list.first()
                Changer.setQuote(app, quote)
            }
            runOnUiThread {
                button.isEnabled = true
                val message = if (result.isSuccess) "이 디자인으로 배경화면을 바꿨어요" else "배경화면을 바꾸지 못했어요"
                Toast.makeText(app, message, Toast.LENGTH_SHORT).show()
            }
        }.start()
    }
}
