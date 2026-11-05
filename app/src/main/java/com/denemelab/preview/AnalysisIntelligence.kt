package com.denemelab.preview

import kotlin.math.sqrt

/** Honest analytics: no forecast or inferred untagged topic. */
data class StabilityInsight(
    val count: Int,
    val average: Double?,
    val spread: Double?,
    val direction: Double?,
    val status: String,
    val explanation: String
)
data class LessonRisk(
    val lesson: String,
    val lostPotential: Double,
    val wrong: Int,
    val blank: Int,
    val action: String
)
object AnalysisIntelligence {
    fun stability(history: List<ExamRecord>): StabilityInsight {
        val scores = history.takeLast(5).map { it.net }
        if (scores.size < 3) return StabilityInsight(scores.size,
            if(scores.isEmpty()) null else scores.average(),null,null,
            "Veri birikiyor","İstikrar değerlendirmesi için aynı türden en az 3 deneme gerekiyor.")
        val avg = scores.average()
        val deviation = sqrt(scores.sumOf { (it-avg)*(it-avg) } / scores.size)
        val direction = if(scores.size >= 4) {
            val half=scores.size/2
            scores.takeLast(half).average()-scores.take(half).average()
        } else null
        val label = when {
            deviation <= 2.5 -> "İstikrarlı"
            deviation <= 6.0 -> "Orta dalgalanma"
            else -> "Yüksek dalgalanma"
        }
        return StabilityInsight(scores.size,avg,deviation,direction,label,
            "Son ${scores.size} denemenin netleri kullanıldı. Farklı yayın zorlukları karşılaştırmayı etkileyebilir.")
    }

    fun lossRanking(insights: List<SubjectInsight>): List<LessonRisk> =
        insights.map {
            val reason = when {
                it.blank > it.wrong -> "Boşların nedenini incele; süre mi, bilgi mi?"
                it.wrong > 0 -> "Yanlışları konu ve hata nedeni ile etiketle."
                else -> "Koruma tekrarı yap."
            }
            LessonRisk(it.name,it.theoreticalRoom,it.wrong,it.blank,reason)
        }.sortedByDescending { it.lostPotential }

    fun prescription(reason: String): String = when(reason) {
        "Bilgi eksikliği" -> "15 dk konu özeti + 15 hedefli soru; ertesi gün mini tekrar"
        "İşlem hatası" -> "5 çözümlü örnek + 12 benzer işlem sorusu"
        "Dikkat hatası" -> "10 soruluk kontrollü set; soru köklerini işaretle"
        "Yorumlama" -> "8 farklı soru tipi + her yanlışın gerekçesini yaz"
        "Süre yetersizliği" -> "15 soruluk süreli set; çözüm başına zamanı kaydet"
        else -> "Yanlışın nedenini belirle; ardından hedefli tekrar yap"
    }
}
