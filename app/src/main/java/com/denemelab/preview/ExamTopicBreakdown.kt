package com.denemelab.preview

/** Exact wrong/blank allocation for one subtest, never more than its totals. */
data class ExamTopicIssue(
    val lessonKey: String,
    val lessonName: String,
    val topic: String,
    val kind: String, // "Yanlış" or "Boş"
    val count: Int,
    val reason: String = "Belirtilmedi",
    val provenance: String = "Manuel" // "Belge OCR" only when extracted
)

object TopicBreakdown {
    val reasons = listOf(
        "Belirtilmedi", "Bilgi eksikliği", "Dikkat hatası",
        "İşlem hatası", "Yorumlama", "Süre yetersizliği", "Diğer"
    )

    /** More choices can be added without making old stored topic labels invalid. */
    private val topics: Map<String, List<String>> = mapOf(
        "tyt_turkce" to listOf("Sözcükte anlam","Cümlede anlam","Paragrafta anlam","Paragrafta ana düşünce","Paragrafta yardımcı düşünce","Paragrafta yapı","Paragrafta anlatım teknikleri","Paragrafta düşünceyi geliştirme","Anlatım bozuklukları","Yazım kuralları","Noktalama işaretleri","Ses bilgisi","Sözcük türleri","İsim","Sıfat","Zamir","Zarf","Edat ve bağlaç","Fiiller","Fiilimsiler","Ek fiil","Cümlenin ögeleri","Cümle türleri","Sözcükte yapı"),
        "tyt_tarih" to listOf("Tarih bilimine giriş","İlk Çağ uygarlıkları","İlk Türk devletleri","İslam tarihi","Türk İslam devletleri","Türkiye Selçukluları","Osmanlı kuruluş","Osmanlı yükselme","Osmanlı kültür ve medeniyeti","Osmanlı diplomasi","Milli Mücadele","Atatürk ilke ve inkılapları","Türkiye Cumhuriyeti"),
        "tyt_cografya" to listOf("Doğa ve insan","Coğrafyanın bölümleri","Harita bilgisi","Koordinat sistemi","Dünyanın şekli ve hareketleri","İklim bilgisi","Atmosfer","Türkiye'nin iklimi","Yer şekilleri","Su kaynakları","Topraklar","Bitkiler","Nüfus","Göç","Yerleşme","Ekonomik faaliyetler","Bölgeler","Doğal afetler","Çevre sorunları"),
        "tyt_felsefe" to listOf("Felsefenin doğası","Bilgi felsefesi","Varlık felsefesi","Ahlak felsefesi","Siyaset felsefesi","Sanat felsefesi","Din felsefesi","Bilim felsefesi","Felsefi akımlar","Mantık"),
        "tyt_din" to listOf("Bilgi ve inanç","İslam ve ibadet","İslam düşüncesi","Ahlaki tutum ve davranışlar","Din ve hayat","Kur'an ve yorum","Hz. Muhammed","Dinler tarihi","Seçmeli felsefe"),
        "tyt_mat" to listOf("Temel kavramlar","Sayı basamakları","Bölme ve bölünebilme","EBOB EKOK","Rasyonel sayılar","Basit eşitsizlikler","Mutlak değer","Üslü sayılar","Köklü sayılar","Çarpanlara ayırma","Denklemler","Problemler","Sayı problemleri","Yaş problemleri","İşçi havuz problemleri","Hareket problemleri","Yüzde kar zarar","Karışım problemleri","Grafik ve tablo","Kümeler","Mantık","Fonksiyonlar","Permütasyon","Kombinasyon","Olasılık","Veri istatistik"),
        "tyt_geo" to listOf("Doğruda açılar","Üçgende açılar","Üçgende kenar bağıntıları","Üçgende alan","Açıortay","Kenarortay","Üçgende benzerlik","Üçgende eşlik","Dörtgenler","Çokgenler","Paralelkenar","Yamuk","Eşkenar dörtgen","Dikdörtgen","Kare","Çember ve daire","Analitik geometri","Katı cisimler"),
        "tyt_fizik" to listOf("Fizik bilimine giriş","Madde ve özellikleri","Hareket ve kuvvet","Newton yasaları","İş güç enerji","Isı ve sıcaklık","Genleşme","Basınç","Kaldırma kuvveti","Elektrostatik","Elektrik devreleri","Manyetizma","Dalgalar","Ses","Optik","Aydınlanma","Aynalar","Mercekler"),
        "tyt_kimya" to listOf("Kimya bilimi","Atom modelleri","Atom ve periyodik sistem","Kimyasal türler arası etkileşimler","Maddenin halleri","Kimyanın temel kanunları","Mol kavramı","Kimyasal tepkimeler","Karışımlar","Çözeltiler","Asitler bazlar tuzlar","Kimya her yerde","Doğa ve kimya"),
        "tyt_biyoloji" to listOf("Canlıların ortak özellikleri","İnorganik bileşikler","Organik bileşikler","Hücre","Hücre zarı","Madde geçişleri","Canlıların sınıflandırılması","Hücre bölünmeleri","Üreme","Kalıtım","Ekosistem ekolojisi","Güncel çevre sorunları"),
        "ayt_mat" to listOf("Fonksiyonlar","Polinomlar","İkinci dereceden denklemler","Parabol","Eşitsizlikler","Trigonometri","Logaritma","Diziler","Limit","Süreklilik","Türev","Türev uygulamaları","İntegral","İntegral uygulamaları","Analitik geometri","Çemberin analitiği","Olasılık","Permütasyon kombinasyon"),
        "ayt_fiz" to listOf("Vektörler","Bağıl hareket","Newton yasaları","Bir boyutta hareket","Enerji ve hareket","İtme momentum","Tork","Denge","Kütle merkezi","Basit makineler","Elektrik alan","Elektriksel potansiyel","Manyetizma","İndüksiyon","Alternatif akım","Transformatörler","Çembersel hareket","Basit harmonik hareket","Dalga mekaniği","Atom fiziği","Modern fizik"),
        "ayt_kim" to listOf("Modern atom teorisi","Gazlar","Sıvı çözeltiler","Kimyasal tepkimelerde enerji","Tepkime hızı","Kimyasal denge","Asit baz dengesi","Çözünürlük dengesi","Kimya ve elektrik","Organik kimya","Karbon kimyası","Enerji kaynakları"),
        "ayt_biy" to listOf("Sinir sistemi","Endokrin sistem","Duyu organları","Destek hareket sistemi","Sindirim","Dolaşım","Bağışıklık","Solunum","Boşaltım","Üreme ve gelişme","Genetik kod","Protein sentezi","Fotosentez","Kemosentez","Hücresel solunum","Bitki biyolojisi","Canlılar ve çevre"),
        "ayt_edeb" to listOf("Güzel sanatlar ve edebiyat","Şiir bilgisi","Söz sanatları","İslamiyet öncesi Türk edebiyatı","Halk edebiyatı","Divan edebiyatı","Tanzimat edebiyatı","Servetifünun","Fecriati","Milli edebiyat","Cumhuriyet dönemi Türk edebiyatı","Dünya edebiyatı","Edebi akımlar","Roman","Hikâye","Tiyatro","Şiir","Öğretici metinler","Edebiyat sanatçı eser"),
        "ayt_tar1" to listOf("Tarih bilimine giriş","İlk Türk devletleri","Türk İslam tarihi","Selçuklular","Osmanlı devlet ve toplum","Osmanlı modernleşmesi","Milli Mücadele","Atatürk ilke ve inkılapları","Cumhuriyet dönemi"),
        "ayt_cog1" to listOf("Ekosistem","Biyoçeşitlilik","Nüfus politikaları","Şehirleşme","Göç","Ekonomik faaliyetler","Doğal kaynaklar","Türkiye ekonomisi","Küresel ticaret","Çevre sorunları"),
        "ayt_tar2" to listOf("İlk Çağ","Orta Çağ","Osmanlı siyasi tarihi","Osmanlı kültür ve medeniyeti","Türkiye Cumhuriyeti tarihi","20. yüzyıl başları","İkinci Dünya Savaşı","Soğuk Savaş","Küreselleşme"),
        "ayt_cog2" to listOf("Doğal sistemler","Beşeri sistemler","Ekonomik faaliyetler","Türkiye coğrafyası","Ülkeler ve bölgeler","Küresel çevre sorunları","İklim değişikliği"),
        "ayt_fels" to listOf("Mantık","Psikoloji","Sosyoloji","Bilgi felsefesi","Varlık felsefesi","Ahlak felsefesi","Siyaset felsefesi","Bilim felsefesi"),
        "ayt_din" to listOf("İnanç","İslam ve ibadet","Ahlak","Kur'an yorum","Dinler tarihi","Din felsefesi")
    )

    fun choices(key: String): List<String> = topics[key].orEmpty() + "Diğer / özel kazanım"

    fun validate(all: List<ExamTopicIssue>, parts: List<SubjectScore>, branch: SubjectScore? = null): String? {
        val subjects = if (parts.isEmpty() && branch != null) listOf(branch) else parts
        val index = subjects.associateBy { it.key }
        for (issue in all) {
            if (index[issue.lessonKey] == null) return "Detaylandırmada bilinmeyen ders var."
            if (issue.topic.isBlank()) return "Konu seçilmesi gerekiyor."
            if (issue.count <= 0) return "Konu sayıları sıfırdan büyük olmalı."
            if (issue.kind !in listOf("Yanlış", "Boş")) return "Detay türü geçersiz."
            if (issue.reason !in reasons) return "Hata nedeni geçersiz."
        }
        for (subject in subjects) {
            val mine = all.filter { it.lessonKey == subject.key }
            if (mine.filter { it.kind == "Yanlış" }.sumOf { it.count } > subject.wrong)
                return "${subject.title} yanlış detayları toplam yanlışı aşıyor."
            if (mine.filter { it.kind == "Boş" }.sumOf { it.count } > subject.blank)
                return "${subject.title} boş detayları toplam boşu aşıyor."
        }
        return null
    }
}
