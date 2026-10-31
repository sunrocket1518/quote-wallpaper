package com.sunrocket.quotewallpaper

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/** 명언을 배경화면 이미지로 그림 */
object WallpaperPainter {
    private val palettes = mapOf(
        "공부" to intArrayOf(0xFF14304F.toInt(), 0xFF3B6EA8.toInt()),
        "일" to intArrayOf(0xFF3A1F12.toInt(), 0xFFB0602E.toInt()),
        "효율" to intArrayOf(0xFF0F3A2B.toInt(), 0xFF2C7A57.toInt()),
        "인생" to intArrayOf(0xFF2B1840.toInt(), 0xFF8A4F9E.toInt()),
    )
    private val defaultPalette = intArrayOf(0xFF1B1F24.toInt(), 0xFF46505C.toInt())

    fun render(context: Context, quote: Quote, scale: Float = 1f): Bitmap {
        val (screenW, screenH) = screenSize(context)
        val w = (screenW * scale).toInt().coerceAtLeast(1)
        val h = (screenH * scale).toInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        val colors = palettes[quote.category] ?: defaultPalette
        val bg = Paint().apply {
            shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), colors[0], colors[1], Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bg)

        val maxWidth = (w * 0.78f).toInt()
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            textSize = w * 0.064f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        }
        val authorPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xCCFFFFFF.toInt()
            textSize = w * 0.04f
        }
        val authorText = "— ${quote.author}"

        val quoteLayout = StaticLayout.Builder
            .obtain(quote.text, 0, quote.text.length, textPaint, maxWidth)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setLineSpacing(0f, 1.3f)
            .build()
        val authorLayout = StaticLayout.Builder
            .obtain(authorText, 0, authorText.length, authorPaint, maxWidth)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .build()

        val gap = w * 0.06f
        val totalHeight = quoteLayout.height + gap + authorLayout.height
        canvas.save()
        canvas.translate((w - maxWidth) / 2f, (h - totalHeight) / 2f)
        quoteLayout.draw(canvas)
        canvas.translate(0f, quoteLayout.height + gap)
        authorLayout.draw(canvas)
        canvas.restore()
        return bmp
    }

    fun apply(context: Context, quote: Quote, target: Int) {
        val bmp = render(context, quote)
        val flags = when (target) {
            Settings.TARGET_HOME -> WallpaperManager.FLAG_SYSTEM
            Settings.TARGET_LOCK -> WallpaperManager.FLAG_LOCK
            else -> WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
        }
        WallpaperManager.getInstance(context).setBitmap(bmp, null, true, flags)
    }

    private fun screenSize(context: Context): Pair<Int, Int> {
        val wm = context.getSystemService(WindowManager::class.java)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val b = wm.maximumWindowMetrics.bounds
            b.width() to b.height()
        } else {
            val dm = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(dm)
            dm.widthPixels to dm.heightPixels
        }
    }
}

/** 배경화면 바꾸기 공통 동작 */
object Changer {
    /** 설정에 맞는 다른 명언으로 바꿈. 맞는 명언이 없으면 null */
    fun changeRandom(context: Context): Quote? {
        val settings = Settings(context)
        val quote = QuoteRepository(context).pick(settings, settings.currentId) ?: return null
        setQuote(context, quote)
        return quote
    }

    fun setQuote(context: Context, quote: Quote) {
        val settings = Settings(context)
        WallpaperPainter.apply(context, quote, settings.target)
        settings.currentId = quote.id
    }
}

class WallpaperWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result = try {
        Changer.changeRandom(applicationContext)
        Result.success()
    } catch (e: Exception) {
        Result.retry()
    }
}

/** 정해진 주기마다 자동으로 바꾸는 예약 */
object Scheduler {
    private const val WORK_NAME = "quote_wallpaper_auto"

    fun enable(context: Context, hours: Int) {
        val request = PeriodicWorkRequestBuilder<WallpaperWorker>(hours.toLong(), TimeUnit.HOURS)
            .setInitialDelay(hours.toLong(), TimeUnit.HOURS)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE, request)
    }

    fun disable(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}
