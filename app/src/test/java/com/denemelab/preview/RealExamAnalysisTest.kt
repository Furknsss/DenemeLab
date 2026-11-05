package com.denemelab.preview

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class RealExamAnalysisTest {
    @Test fun official2026TytStructureIs120() {
        val tyt = ExamStructure.subjects("TYT", "")
        assertEquals(listOf(40, 20, 40, 20), tyt.map { it.questions })
        assertEquals(120, tyt.sumOf { it.questions })
    }

    @Test fun official2026AytDifferentTracksAre80Each() {
        assertEquals(listOf(40, 14, 13, 13),
            ExamStructure.subjects("AYT", "SAY").map { it.questions })
        assertEquals(listOf(40, 24, 10, 6),
            ExamStructure.subjects("AYT", "EA").map { it.questions })
        assertEquals(listOf(24, 10, 6, 11, 11, 12, 6),
            ExamStructure.subjects("AYT", "SÖZ").map { it.questions })
        listOf("SAY", "EA", "SÖZ").forEach { mode ->
            assertEquals(80, ExamStructure.subjects("AYT", mode).sumOf { it.questions })
        }
        assertTrue(ExamStructure.subjects("AYT", "DİL").isEmpty())
    }

    @Test fun sumNetEqualsSubjectTotals() {
        val score = ExamStructure.tyt.map {
            SubjectScore(it.key, it.title, it.questions, it.questions / 2,
                it.questions / 4)
        }
        assertTrue(ExamStructure.valid(score, "TYT", ""))
        assertEquals(60, ExamStructure.correct(score))
        assertEquals(30, ExamStructure.wrong(score))
        assertEquals(30, ExamStructure.blank(score))
        assertEquals(52.5, ExamStructure.net(score), 1e-8)
    }

    @Test fun invalidCountsAndDuplicatedSubjectsCannotPass() {
        val template = ExamStructure.tyt
        val normal = template.map {
            SubjectScore(it.key, it.title, it.questions, 0, 0)
        }
        assertTrue(ExamStructure.valid(normal, "TYT", ""))
        assertFalse(ExamStructure.valid(normal.dropLast(1), "TYT", ""))
        assertFalse(ExamStructure.valid(normal.mapIndexed { i, it ->
            if (i == 2) it.copy(correct = 41) else it
        }, "TYT", ""))
        assertFalse(ExamStructure.valid(normal.mapIndexed { i, it ->
            if (i == 0) it.copy(wrong = -1) else it
        }, "TYT", ""))
        assertFalse(ExamStructure.valid(normal.mapIndexed { i, it ->
            if (i == 1) it.copy(key = normal[0].key) else it
        }, "TYT", ""))
        assertFalse(ExamStructure.valid(normal, "AYT", "SAY"))
    }

    @Test fun aytTrackComparisonsNeverMixSayAndEaOrUnspecifiedLegacy() {
        val say = exam("AYT", "SAY")
        val ea = exam("AYT", "EA")
        val unknown = exam("AYT", "")
        val say2 = exam("AYT", "SAY")
        assertTrue(ExamRules.comparable(say, say2))
        assertFalse(ExamRules.comparable(say, ea))
        assertFalse(ExamRules.comparable(say, unknown))
        assertFalse(ExamRules.comparable(say, exam("TYT", "")))
    }

    @Test fun persistedSubjectResultsRoundTripAndPreserveLegacy() {
        val scores = ExamStructure.subjects("AYT", "SAY").map {
            SubjectScore(it.key, it.title, it.questions, it.questions / 2, 0)
        }
        val newest = ExamRecord(2, "AYT SAY", "Genel", "AYT", "", 40, 0, 40,
            2, track = "SAY", parts = scores)
        val legacy = ExamRecord(1, "Eski TYT", "Genel", "TYT", "", 80, 20, 20, 1)
        val decoded = ExamJsonCodec.decode(ExamJsonCodec.encode(listOf(legacy, newest)))
        assertEquals(2, decoded.size)
        assertEquals(newest, decoded[0])
        assertEquals(legacy, decoded[1])
        assertTrue(decoded[1].parts.isEmpty())
        assertEquals(75.0, decoded[1].net, 1e-8)
    }

    @Test fun legacyJsonFromPreviousReleasesLoadsWithoutMutation() {
        val old = JSONArray().put(
            JSONObject().put("id", 11).put("name", "Geçmiş Deneme")
                .put("category", "Genel").put("examType", "TYT").put("subject", "")
                .put("correct", 75).put("wrong", 20).put("blank", 25)
                .put("createdAt", 99))
        val loaded = ExamJsonCodec.decode(old.toString())
        assertEquals(1, loaded.size)
        assertEquals(70.0, loaded.first().net, 1e-8)
        assertEquals("", loaded.first().track)
        assertTrue(loaded.first().parts.isEmpty())
        assertEquals(1, ExamJsonCodec.decode(ExamJsonCodec.encode(loaded)).size)
    }

    @Test fun corruptedSubjectMetadataIsNeverPresentedAsAccurateBreakdown() {
        val normal = ExamJsonCodec.encode(listOf(exam("TYT", "").copy(
            correct = 0, wrong = 0, blank = 120,
            parts = ExamStructure.tyt.map { SubjectScore(it.key,it.title,it.questions,0,0) }
        )))
        val json = JSONArray(normal)
        val entry = json.getJSONObject(0)
        entry.getJSONArray("parts").getJSONObject(0).put("correct", 30)
        val parsed = ExamJsonCodec.decode(json.toString())
        assertEquals(1, parsed.size)
        assertTrue(parsed.first().parts.isEmpty())
        assertEquals(0.0, parsed.first().net, 1e-8)
    }

    @Test fun oneMalformedRecordMustNotDeleteOthers() {
        val good = JSONObject()
            .put("id", 1).put("name", "Sağlam").put("category","Genel")
            .put("examType","TYT").put("subject","")
            .put("correct",80).put("wrong",20).put("blank",20)
            .put("createdAt",3)
        val array = JSONArray().put(JSONObject().put("id","invalid")).put(good)
        val loaded = ExamJsonCodec.decode(array.toString())
        assertEquals(1, loaded.size)
        assertEquals("Sağlam", loaded[0].name)
    }

    private fun exam(type: String, track: String) =
        ExamRecord(123, "Test", "Genel", type, "", 30, 10, 40, 1, track)
}
