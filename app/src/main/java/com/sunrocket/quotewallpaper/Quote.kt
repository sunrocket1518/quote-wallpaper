package com.sunrocket.quotewallpaper

data class Quote(
    val id: String,
    val text: String,
    val author: String,
    val category: String,
    val mine: Boolean,
)

object Categories {
    const val ALL = "전체"
    val LIST = listOf("공부", "일", "효율", "인생")
    val WITH_ALL = listOf(ALL) + LIST
}

/** 앱에 기본으로 들어 있는 추천 명언 */
object BuiltInQuotes {
    private fun q(id: Int, text: String, author: String, category: String) =
        Quote("famous-$id", text, author, category, mine = false)

    val list: List<Quote> = listOf(
        q(1, "배우고 때때로 익히면 또한 기쁘지 아니한가", "공자", "공부"),
        q(2, "아는 것이 힘이다", "프랜시스 베이컨", "공부"),
        q(3, "천재는 1%의 영감과 99%의 노력으로 이루어진다", "토머스 에디슨", "공부"),
        q(4, "오늘 할 수 있는 일을 내일로 미루지 마라", "벤저민 프랭클린", "공부"),
        q(5, "천 리 길도 한 걸음부터", "노자", "일"),
        q(6, "할 수 있다고 믿든 할 수 없다고 믿든, 당신이 옳다", "헨리 포드", "일"),
        q(7, "위대한 일을 하는 유일한 방법은 자신이 하는 일을 사랑하는 것이다", "스티브 잡스", "일"),
        q(8, "시간은 금이다", "벤저민 프랭클린", "효율"),
        q(9, "계획 없는 목표는 한낱 소망에 불과하다", "생텍쥐페리", "효율"),
        q(10, "단순함은 궁극의 정교함이다", "레오나르도 다 빈치", "효율"),
        q(11, "너 자신을 알라", "소크라테스", "인생"),
        q(12, "나는 생각한다, 고로 존재한다", "데카르트", "인생"),
        q(13, "인생은 가까이서 보면 비극이지만 멀리서 보면 희극이다", "찰리 채플린", "인생"),
        q(14, "살아 있는 한 희망은 있다", "키케로", "인생"),
    )
}
