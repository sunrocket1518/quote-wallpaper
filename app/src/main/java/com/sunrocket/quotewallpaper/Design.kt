package com.sunrocket.quotewallpaper

import android.content.Context

/** 배경화면 꾸미기 설정 */
data class Design(
    val font: Int,
    val background: Int,
    val position: Int,
    val size: Int,
    val align: Int,
    val quoteMark: Boolean,
    val dateLine: Boolean,
) {
    fun save(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt("font", font)
            .putInt("bg", background)
            .putInt("position", position)
            .putInt("size", size)
            .putInt("align", align)
            .putBoolean("quoteMark", quoteMark)
            .putBoolean("dateLine", dateLine)
            .apply()
    }

    companion object {
        private const val PREFS = "settings"

        val FONT_LABELS = listOf("명조 굵게", "명조", "고딕 굵게", "고딕 얇게")

        const val BG_AUTO = 0
        const val BG_PHOTO = 7
        val BG_LABELS = listOf("분야별 자동", "밤하늘", "노을", "숲", "바다", "종이", "검정", "내 사진")

        /** 배경 번호 → (위 색, 아래 색, 밝은 배경인지) */
        val PALETTES: Map<Int, Triple<Int, Int, Boolean>> = mapOf(
            1 to Triple(c(0xFF0F1530), c(0xFF3A2F6B), false),
            2 to Triple(c(0xFF4A1D3F), c(0xFFE0784A), false),
            3 to Triple(c(0xFF0F3A2B), c(0xFF5C8A4A), false),
            4 to Triple(c(0xFF0B2E4A), c(0xFF2E8FA8), false),
            5 to Triple(c(0xFFF4EFE4), c(0xFFE6DCC6), true),
            6 to Triple(c(0xFF101010), c(0xFF222222), false),
        )

        val POSITION_LABELS = listOf("위", "가운데", "아래")
        val SIZE_LABELS = listOf("작게", "보통", "크게")
        val ALIGN_LABELS = listOf("가운데 정렬", "왼쪽 정렬")

        private fun c(v: Long): Int = v.toInt()

        fun load(context: Context): Design {
            val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            return Design(
                font = p.getInt("font", 0).coerceIn(0, FONT_LABELS.size - 1),
                background = p.getInt("bg", BG_AUTO).coerceIn(0, BG_LABELS.size - 1),
                position = p.getInt("position", 1).coerceIn(0, 2),
                size = p.getInt("size", 1).coerceIn(0, 2),
                align = p.getInt("align", 0).coerceIn(0, 1),
                quoteMark = p.getBoolean("quoteMark", true),
                dateLine = p.getBoolean("dateLine", false),
            )
        }
    }
}
