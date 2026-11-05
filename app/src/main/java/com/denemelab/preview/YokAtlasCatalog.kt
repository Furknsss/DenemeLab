package com.denemelab.preview

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.text.Normalizer
import java.util.Locale

/**
 * YÖK Atlas JSON service integration (unofficial client, official data source).
 * No synthetic ranking, historical net or OBP values are ever supplied.
 * Upstream API can change, rate limit or become unavailable; UI must show errors.
 * The MIT-licensed yokatlas-py project was consulted for endpoint and field names:
 * https://github.com/saidsurucu/yokatlas-py
 */
data class AtlasLookup(val id: Int, val name: String, val type: String = "")

data class AtlasCatalog(
    val universities: List<AtlasLookup>,
    val programs: List<AtlasLookup>,
    val cities: List<AtlasLookup>
)

data class AtlasYear(
    val year: Int,
    val quota: Int?,
    val placed: Int?,
    val lastRank: Int?,
    val baseScore: Double?
)

data class AtlasProgram(
    val code: Long,
    val universityId: Int,
    val university: String,
    val programGroupId: Int,
    val program: String,
    val universityType: String,
    val language: String,
    val scholarship: String,
    val scoreType: String,
    val degree: String,
    val city: String,
    val years: List<AtlasYear>
) {
    val current: AtlasYear? get() = years.firstOrNull()
}

data class AtlasNet(
    val year: Int,
    val programCode: Long,
    val obp: Double?,
    val nets: Map<String, Double>
) {
    val tytTotal: Double get() = nets.filterKeys { it.startsWith("TYT") }.values.sum()
    val aytTotal: Double get() = nets.filterKeys { it.startsWith("AYT") }.values.sum()
}

data class AtlasResult(val programs: List<AtlasProgram>, val total: Int, val dataYear: Int?)

object YokAtlasCatalog {
    private const val ROOT = "https://yokatlas.yok.gov.tr"
    private val tr = Locale.forLanguageTag("tr-TR")
    @Volatile private var cachedCatalog: AtlasCatalog? = null

    fun normalize(text: String): String {
        val decomposed = Normalizer.normalize(text.lowercase(tr).replace('ı','i'), Normalizer.Form.NFD)
        return decomposed.replace(Regex("[\\u0300-\\u036f]"), "")
            .replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()
    }

    suspend fun catalog(): AtlasCatalog = withContext(Dispatchers.IO) {
        cachedCatalog?.let { return@withContext it }
        synchronized(this@YokAtlasCatalog) {
            cachedCatalog?.let { return@withContext it }
        }
        val universities = arrayRequest("/api/tercih-kilavuz/universiteler")
            .map { AtlasLookup(it.optInt("universiteId"), it.optString("universiteAdi")) }
            .filter { it.id > 0 && it.name.isNotBlank() }
        val programs = arrayRequest("/api/tercih-kilavuz/universite-programlar")
            .map { AtlasLookup(it.optInt("birimGrupId"), it.optString("birimGrupAdi"), it.optString("puanTuru")) }
            .filter { it.id > 0 && it.name.isNotBlank() }
        val cities = arrayRequest("/api/tercih-kilavuz/universite-iller")
            .map { AtlasLookup(it.optInt("ilKodu"), it.optString("ilAdi")) }
        if (universities.isEmpty() || programs.isEmpty()) throw IOException("Üniversite/program kataloğu boş döndü.")
        AtlasCatalog(universities, programs, cities).also { cachedCatalog = it }
    }

    /**
     * Search query is resolved against official lookup IDs, preserving distinct
     * program codes for language / scholarship / state-foundation variants.
     */
    suspend fun search(
        lookups: AtlasCatalog,
        query: String,
        scoreType: String?,
        universityType: String?,
        page: Int = 0
    ): AtlasResult = withContext(Dispatchers.IO) {
        val needle = normalize(query)
        val universityIds = if (needle.isBlank()) emptyList() else
            lookups.universities.filter { normalize(it.name).contains(needle) }.take(60).map { it.id }
        val groupIds = if (needle.isBlank()) emptyList() else
            lookups.programs.filter { normalize(it.name).contains(needle) }
                .filter { scoreType == null || it.type == scoreType }.take(60).map { it.id }
        val code = needle.toLongOrNull()?.takeIf { needle.length == 9 }
        if (needle.isNotBlank() && universityIds.isEmpty() && groupIds.isEmpty() && code == null) {
            return@withContext AtlasResult(emptyList(), 0, null)
        }

        val queries = mutableListOf<JSONObject>()
        if (code != null) queries.add(filter(scoreType, universityType).put("kilavuzKodu", code))
        if (universityIds.isNotEmpty()) queries.add(
            filter(scoreType, universityType).put("universiteId", JSONArray(universityIds)))
        if (groupIds.isNotEmpty()) queries.add(
            filter(scoreType, universityType).put("birimGrupId", JSONArray(groupIds)))
        if (queries.isEmpty() && needle.isBlank()) queries.add(filter(scoreType, universityType))

        val all = mutableListOf<AtlasProgram>()
        var total = 0
        var year: Int? = null
        var failure: IOException? = null

        // Two independent search paths are unioned, never intersected;
        // typing a university OR a degree works like the reference screenshot.
        queries.forEach { f ->
            try {
                val raw = jsonRequest("/api/tercih-kilavuz/search",
                    JSONObject()
                        .put("filters", f)
                        .put("page", page.coerceAtLeast(0))
                        .put("size", 30)
                        .put("sortBy", "basariSirasi")
                        .put("direction", "ASC"))
                val content = raw.optJSONArray("content") ?: JSONArray()
                for (i in 0 until content.length()) {
                    val record = content.optJSONObject(i) ?: continue
                    parseProgram(record)?.let { all.add(it) }
                }
                total += raw.optInt("totalElements", all.size)
                if (raw.has("yil") && !raw.isNull("yil")) year = raw.optInt("yil")
            } catch (e: IOException) { failure = e }
        }
        if (all.isEmpty() && failure != null) throw failure!!
        AtlasResult(all.distinctBy { it.code }, total, year)
    }

    suspend fun lastPlacedNets(program: AtlasProgram): List<AtlasNet> = withContext(Dispatchers.IO) {
        if (program.programGroupId <= 0 || program.universityId <= 0) return@withContext emptyList()
        val f = JSONObject()
            .put("puanTuru", program.scoreType)
            .put("universiteId", program.universityId)
            .put("birimGrupId", program.programGroupId)
            .put("birimTuruId", JSONObject.NULL)
            .put("universiteTuru", JSONObject.NULL)
            .put("yil", JSONObject.NULL)
            .put("katsayi", JSONObject.NULL)
        val result = mutableListOf<AtlasNet>()
        for (page in 0..2) {
            val raw = jsonRequest("/api/netler/search",
                JSONObject().put("filters", f).put("page", page).put("size", 100))
            val rows = raw.optJSONArray("content") ?: JSONArray()
            for (i in 0 until rows.length()) {
                val row = rows.optJSONObject(i) ?: continue
                if (numberLong(row, "kilavuzKodu") != program.code) continue
                val items = linkedMapOf<String,Double>()
                listOf(
                    "tytTrkNet" to "TYT Türkçe", "tytSosNet" to "TYT Sosyal",
                    "tytMatNet" to "TYT Matematik", "tytFenNet" to "TYT Fen",
                    "aytMatNet" to "AYT Matematik", "aytFizNet" to "AYT Fizik",
                    "aytKimNet" to "AYT Kimya", "aytBioNet" to "AYT Biyoloji",
                    "aytTdeNet" to "AYT Edebiyat", "aytTrh1Net" to "AYT Tarih 1",
                    "aytCog1Net" to "AYT Coğrafya 1", "aytTrh2Net" to "AYT Tarih 2",
                    "aytCog2Net" to "AYT Coğrafya 2", "aytFelNet" to "AYT Felsefe",
                    "aytDinNet" to "AYT Din", "ydtYdilNet" to "YDT Dil"
                ).forEach { (key,label) -> numberDouble(row, key)?.let { items[label] = it } }
                result.add(AtlasNet(row.optInt("yil"), program.code, numberDouble(row, "obp"), items))
            }
            if (raw.optBoolean("last", true) || rows.length() == 0) break
        }
        result.filter { it.year > 0 }.distinctBy { it.year }.sortedByDescending { it.year }
    }

    private fun filter(scoreType: String?, universityType: String?): JSONObject = JSONObject()
        .put("puanTuru", scoreType ?: JSONObject.NULL)
        .put("universiteId", JSONArray())
        .put("birimGrupId", JSONArray())
        .put("ilKodu", JSONArray())
        .put("birimTuruId", JSONObject.NULL)
        .put("universiteTuru", universityType ?: JSONObject.NULL)
        .put("bursOraniId", JSONObject.NULL)
        .put("ogrenimTuruId", JSONObject.NULL)
        .put("kilavuzKodu", JSONObject.NULL)
        .put("minBasariSirasi", JSONObject.NULL)
        .put("maxBasariSirasi", JSONObject.NULL)

    private fun parseProgram(v: JSONObject): AtlasProgram? {
        val code = numberLong(v, "kilavuzKodu") ?: return null
        val year = numberInt(v, "yil") ?: 0
        val historical = (0..3).mapNotNull { offset ->
            val suffix = if (offset == 0) "" else offset.toString()
            val y = year - offset
            if (y <= 0) null else AtlasYear(
                year = y,
                quota = numberInt(v, "kontenjan" + suffix),
                placed = numberInt(v, "gkY" + suffix),
                lastRank = numberInt(v, "basariSirasi" + suffix),
                baseScore = numberDouble(v, "minPuan" + suffix)
            )
        }
        return AtlasProgram(
            code = code,
            universityId = v.optInt("universiteId"),
            university = v.optString("universiteAdi"),
            programGroupId = v.optInt("birimGrupId"),
            program = v.optString("birimAdi"),
            universityType = v.optString("universiteTuru"),
            language = v.optString("ogrenimDiliAdi"),
            scholarship = v.optString("bursOraniAdi"),
            scoreType = v.optString("puanTuru"),
            degree = v.optString("birimTuruAdi"),
            city = v.optString("ilAdi"),
            years = historical
        )
    }

    private fun numberInt(v: JSONObject, key: String): Int? =
        numberLong(v, key)?.takeIf { it in Int.MIN_VALUE..Int.MAX_VALUE }?.toInt()

    private fun numberLong(v: JSONObject, key: String): Long? =
        if (!v.has(key) || v.isNull(key)) null
        else v.optString(key).replace(".", "").replace(" ", "").toLongOrNull()

    private fun numberDouble(v: JSONObject, key: String): Double? =
        if (!v.has(key) || v.isNull(key)) null
        else v.optString(key).replace(",", ".").toDoubleOrNull()

    private fun arrayRequest(path: String): List<JSONObject> {
        val raw = request(path, null)
        val arr = JSONArray(raw)
        return (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
    }

    private fun jsonRequest(path: String, body: JSONObject): JSONObject {
        return JSONObject(request(path, body))
    }

    private fun request(path: String, body: JSONObject?): String {
        val c = URL(ROOT + path).openConnection() as HttpURLConnection
        c.connectTimeout = 9_000
        c.readTimeout = 15_000
        c.requestMethod = if (body == null) "GET" else "POST"
        c.setRequestProperty("Accept", "application/json")
        c.setRequestProperty("User-Agent", "DenemeLab/0.3 (Android; YOKAtlas data reader)")
        if (body != null) {
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            c.doOutput = true
        }
        try {
            if (body != null) c.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = c.responseCode
            if (code == 429 || code == 418) throw IOException("YÖK Atlas istek sınırına ulaşıldı. Biraz sonra yeniden dene.")
            if (code !in 200..299) throw IOException("YÖK Atlas şu anda yanıt vermedi (HTTP $code).")
            val contentType = c.contentType.orEmpty()
            if (!contentType.contains("json", ignoreCase = true))
                throw IOException("YÖK Atlas beklenen JSON yanıtını vermedi.")
            return c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally { c.disconnect() }
    }
}
