package com.denemelab.preview

import org.junit.Assert.*
import org.junit.Test

class PublisherEntryTest {
    @Test fun samePublisherMatchesAcrossTurkishCaseAndSpacing() {
        assertEquals(PublisherCatalog.normalize("3D Yayıncılık"),
            PublisherCatalog.normalize("3d   YAYINCILIK"))
        assertEquals(PublisherCatalog.normalize("Özdebir Yayınları"),
            PublisherCatalog.normalize("ozdebir yayinlari"))
        assertEquals(PublisherCatalog.normalize("İstanbul Ölçme"),
            PublisherCatalog.normalize("istanbul olcme"))
    }

    @Test fun publisherLogoRecordRoundTripsWithoutImageBytes() {
        val original = listOf(
            PublisherBrand(PublisherCatalog.normalize("3D Yayıncılık"),
                "3D Yayıncılık", "123e4567-e89b-12d3-a456-426614174000.png"),
            PublisherBrand(PublisherCatalog.normalize("Bilgi Sarmal"),
                "Bilgi Sarmal", "")
        )
        val decoded = PublisherCatalog.decode(PublisherCatalog.encode(original))
        assertEquals(2, decoded.size)
        assertEquals(original.sortedBy { it.displayName.lowercase(java.util.Locale.forLanguageTag("tr-TR")) },
            decoded)
        assertTrue(PublisherCatalog.decode("[{\"name\":\"\",\"logoFile\":\"bad.png\"}]").isEmpty())
    }

    @Test fun maliciousLogoPathRejected() {
        val raw = """[{"name":"Örnek","logoFile":"../../private/data.png"}]"""
        val result = PublisherCatalog.decode(raw)
        assertEquals(1, result.size)
        assertEquals("", result.first().logoFile)
    }

    @Test fun examRoundTripPreservesPublisherDateAndMood() {
        val day = 1793836800000L
        val exam = ExamRecord(
            id = 123L, name = "TYT TG 1", category = "Genel", examType = "TYT",
            subject = "", correct = 80, wrong = 20, blank = 20, createdAt = 1793900000000L,
            publisher = "3D Yayıncılık", examDate = day, mood = "Stresli"
        )
        val loaded = ExamJsonCodec.decode(ExamJsonCodec.encode(listOf(exam)))
        assertEquals(exam, loaded.single())
        assertEquals(day, loaded.single().examDate)
        assertEquals("3D Yayıncılık", loaded.single().publisher)
        assertEquals(75.0, loaded.single().net, 0.000001)
    }

    @Test fun previousRecordsDefaultToCreatedAtAndNoPublisher() {
        val legacy = """[{"id":1,"name":"Eski TYT","category":"Genel",
            "examType":"TYT","subject":"","correct":80,"wrong":20,
            "blank":20,"createdAt":1720000000000}]"""
        val old = ExamJsonCodec.decode(legacy).single()
        assertEquals("", old.publisher)
        assertEquals(1720000000000L, old.examDate)
        assertEquals("Normal", old.mood)
        assertEquals(75.0, old.net, 0.000001)
    }

    @Test fun detailedTytHas120QuestionsAndOldTytStaysValid() {
        val detailed = ExamStructure.subjectsForEntry("TYT", "")
        assertEquals(120, detailed.sumOf { it.questions })
        assertEquals(listOf(7, 7, 6), detailed.takeLast(3).map { it.questions })
        val fullNew = detailed.map { SubjectScore(it.key, it.title, it.questions, 0, 0) }
        val old = ExamStructure.tyt.map { SubjectScore(it.key, it.title, it.questions, 0, 0) }
        assertTrue(ExamStructure.valid(fullNew, "TYT", ""))
        assertTrue(ExamStructure.valid(old, "TYT", ""))
        assertFalse(ExamStructure.valid(fullNew.dropLast(1), "TYT", ""))
        val scores = fullNew.mapIndexed { index, subject ->
            if (index == 7) subject.copy(correct = 6, wrong = 1) else subject
        }
        assertEquals(5.75, ExamStructure.net(scores), 0.000001)
    }
}
