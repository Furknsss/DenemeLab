package com.denemelab.preview

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class YokAtlasCatalogTest {
    @Test fun turkishSearchNormalizesAccentsAndWordSeparators() {
        assertEquals("istanbul universitesi", YokAtlasCatalog.normalize("İSTANBUL ÜNİVERSİTESİ"))
        assertEquals("istanbul universitesi", YokAtlasCatalog.normalize("Istanbul Universitesi"))
        assertEquals("elektrik elektronik", YokAtlasCatalog.normalize("Elektrik-Elektronik"))
        assertEquals("", YokAtlasCatalog.normalize("  "))
    }

    @Test fun parsesExactProgramCodeAndLanguageBursAndYearlyHistory() {
        val row = JSONObject()
            .put("kilavuzKodu", 102210277)
            .put("yil", 2026)
            .put("universiteId", 101)
            .put("universiteAdi", "ÖRNEK ÜNİVERSİTESİ")
            .put("birimGrupId", 303)
            .put("birimAdi", "ELEKTRİK-ELEKTRONİK MÜHENDİSLİĞİ (İNGİLİZCE)")
            .put("universiteTuru", "VAKIF")
            .put("ogrenimDiliAdi", "İNGİLİZCE")
            .put("bursOraniAdi", "%50 Burslu")
            .put("puanTuru", "SAY")
            .put("birimTuruAdi", "LİSANS")
            .put("ilAdi", "İSTANBUL")
            .put("basariSirasi", 12000)
            .put("basariSirasi1", 14700)
            .put("kontenjan", 35)
            .put("gkY", 34)
            .put("minPuan", 412.46)

        val program = YokAtlasCatalog.parseProgramRecord(row)
        assertNotNull(program)
        program!!
        assertEquals(102210277L, program.code)
        assertEquals("SAY", program.scoreType)
        assertEquals("VAKIF", program.universityType)
        assertEquals("%50 Burslu", program.scholarship)
        assertEquals("İNGİLİZCE", program.language)
        assertEquals(2026, program.current!!.year)
        assertEquals(12000, program.current!!.lastRank)
        assertEquals(35, program.current!!.quota)
        assertEquals(34, program.current!!.placed)
        assertEquals(412.46, program.current!!.baseScore!!, 0.00001)
        assertEquals(2025, program.years[1].year)
        assertEquals(14700, program.years[1].lastRank)
        assertEquals(null, program.years[2].lastRank)
    }

    @Test fun absentCodeMustNeverLookLikeRealProgram() {
        assertNull(YokAtlasCatalog.parseProgramRecord(JSONObject().put("yil", 2026)))
    }
}
