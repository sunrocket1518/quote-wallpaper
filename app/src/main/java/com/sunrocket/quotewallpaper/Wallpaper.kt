package com.sunrocket.quotewallpaper

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
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
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/** 명언을 배경화면 이미지로 그림 */
object WallpaperPainter {
    private val categoryPalettes = mapOf(
        "공부" to intArrayOf(0xFF14304F.toInt(), 0xFF3B6EA8.toInt()),
        "일" to intArrayOf(0xFF3A1F12.toInt(), 0xFFB0602E.toInt()),
        "효율" to intArrayOf(0xFF0F3A2B.toInt(), 0xFF2C7A57.toInt()),
        "인생" to intArrayOf(0xFF2B1840.toInt(), 0xFF8A4F9E.toInt()),
    )
    private val defaultPalette = intArrayOf(0xFF1B1F24.toInt(), 0xFF46505C.toInt())
    private val SIZE_SCALE = floatArrayOf(0.8f, 1f, 1.25f)

    fun photoFile(context: Context) = File(context.filesDir, "background.jpg")

    fun render(
        context: Context,
        quote: Quote,
        scale: Float = 1f,
        design: Design = Design.load(context),
    ): Bitmap {
        val (screenW, screenH) = screenSize(context)
        val w = (screenW * scale).toInt().coerceAtLeast(1)
        val h = (screenH * scale).toInt().coerceAtLeast(1)
        val wf = w.toFloat()
        val hf = h.toFloat()
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        // 1. 배경
        var photoDrawn = false
        if (design.background == Design.BG_PHOTO) {
            val file = photoFile(context)
            if (file.exists()) {
                BitmapFactory.decodeFile(file.path)?.let {
                    canvas.drawBitmap(it, null, RectF(0f, 0f, wf, hf), Paint(Paint.FILTER_BITMAP_FLAG))
                    canvas.drawColor(0x66000000) // 글자가 잘 보이게 살짝 어둡게
                    photoDrawn = true
                }
            }
        }
        val palette = Design.PALETTES[design.background]
        val light = !photoDrawn && palette?.third == true
        if (!photoDrawn) {
            val colors = if (palette != null) intArrayOf(palette.first, palette.second)
            else categoryPalettes[quote.category] ?: defaultPalette
            val bg = Paint().apply {
                shader = LinearGradient(0f, 0f, wf, hf, colors[0], colors[1], Shader.TileMode.CLAMP)
            }
            canvas.drawRect(0f, 0f, wf, hf, bg)
        }

        // 2. 글자 모양
        val ink = if (light) 0xFF2A2520.toInt() else 0xFFFFFFFF.toInt()
        val subInk = if (light) 0xB32A2520.toInt() else 0xCCFFFFFF.toInt()
        val markInk = if (light) 0x402A2520 else 0x55FFFFFF
        val typeface = when (design.font) {
            0 -> Typeface.create(Typeface.SERIF, Typeface.BOLD)
            1 -> Typeface.create(Typeface.SERIF, Typeface.NORMAL)
            2 -> Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            else -> Typeface.create("sans-serif-light", Typeface.NORMAL)
        }
        val sizeScale = SIZE_SCALE[design.size.coerceIn(0, 2)]
        val leftAligned = design.align == 1
        val alignment = if (leftAligned) Layout.Alignment.ALIGN_NORMAL else Layout.Alignment.ALIGN_CENTER

        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ink
            textSize = wf * 0.064f * sizeScale
            this.typeface = typeface
        }
        val subPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = subInk
            textSize = wf * 0.04f
        }
        if (photoDrawn) {
            textPaint.setShadowLayer(wf * 0.01f, 0f, wf * 0.003f, 0x99000000.toInt())
            subPaint.setShadowLayer(wf * 0.008f, 0f, wf * 0.002f, 0x99000000.toInt())
        }

        val maxWidth = (wf * 0.78f).toInt()
        val left = (wf - maxWidth) / 2f

        fun layout(text: String, paint: TextPaint, spacing: Float = 1f): StaticLayout =
            StaticLayout.Builder.obtain(text, 0, text.length, paint, maxWidth)
                .setAlignment(alignment)
                .setLineSpacing(0f, spacing)
                .build()

        val dateLayout = if (design.dateLine) {
            val date = SimpleDateFormat("M월 d일 EEEE", Locale.KOREAN).format(Date())
            layout("$date · ${quote.category}", subPaint)
        } else null
        val quoteLayout = layout(quote.text, textPaint, 1.3f)
        val authorLayout = layout("— ${quote.author}", subPaint)

        val markSize = wf * 0.2f
        val markSpace = if (design.quoteMark) markSize * 0.42f else 0f
        val smallGap = wf * 0.04f
        val gap = wf * 0.06f

        val total = (dateLayout?.let { it.height + smallGap } ?: 0f) +
            markSpace + quoteLayout.height + gap + authorLayout.height

        // 3. 세로 위치 (위: 잠금화면 시계 아래, 아래: 앱 아이콘 줄 위)
        val wanted = when (design.position) {
            0 -> hf * 0.28f
            2 -> hf * 0.80f - total
            else -> (hf - total) / 2f
        }
        var y = wanted.coerceIn(hf * 0.05f, maxOf(hf * 0.05f, hf * 0.95f - total))

        // 4. 그리기
        fun drawLayout(l: StaticLayout) {
            canvas.save()
            canvas.translate(left, y)
            l.draw(canvas)
            canvas.restore()
            y += l.height
        }

        dateLayout?.let {
            drawLayout(it)
            y += smallGap
        }
        if (design.quoteMark) {
            val markPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = markInk
                textSize = markSize
                this.typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
                textAlign = if (leftAligned) Paint.Align.LEFT else Paint.Align.CENTER
            }
            val x = if (leftAligned) left - markSize * 0.05f else wf / 2f
            canvas.drawText("“", x, y + markSize * 0.72f, markPaint)
            y += markSpace
        }
        drawLayout(quoteLayout)
        y += gap
        drawLayout(authorLayout)
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

    /** 고른 사진을 화면 크기에 맞게 잘라서 앱 안에 저장 */
    fun savePhoto(context: Context, uri: Uri) {
        val (sw, sh) = screenSize(context)
        val src: Bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                val sample = maxOf(1, minOf(info.size.width / sw, info.size.height / sh))
                decoder.setTargetSampleSize(sample)
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            val sample = maxOf(1, minOf(bounds.outWidth / sw, bounds.outHeight / sh))
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                ?: error("사진을 열 수 없어요")
        }
        val out = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888)
        val s = maxOf(sw.toFloat() / src.width, sh.toFloat() / src.height)
        val dw = src.width * s
        val dh = src.height * s
        val dst = RectF((sw - dw) / 2f, (sh - dh) / 2f, (sw + dw) / 2f, (sh + dh) / 2f)
        Canvas(out).drawBitmap(src, null, dst, Paint(Paint.FILTER_BITMAP_FLAG))
        photoFile(context).outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 90, it) }
    }

    fun screenSize(context: Context): Pair<Int, Int> {
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
