package com.denemelab.preview

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.School
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import org.json.JSONObject
import java.util.Locale

private val Onyx = androidx.compose.ui.graphics.Color(0xFF111111)
private val Silver = androidx.compose.ui.graphics.Color(0xFF777777)
private val Hairline = androidx.compose.ui.graphics.Color(0xFFEAEAEA)
private val Shell = androidx.compose.ui.graphics.Color(0xFFF5F5F5)
private val White = androidx.compose.ui.graphics.Color.White

/**
 * Asset metadata only. Files themselves are not copied from the third-party
 * directory: that directory contains mostly unlicensed university trademarks.
 * Only individually declared free/PD Commons entries are in this manifest.
 * Exact YÖK ID + normalized institution name checks avoid mismatched identity.
 */
data class UniversityImage(
    val sourcePage: String,
    val license: String,
    val attribution: String
) {
    fun imageUrl(width: Int): String? {
        val prefix = "https://commons.wikimedia.org/wiki/File:"
        if (!sourcePage.startsWith(prefix)) return null
        val encodedName = sourcePage.removePrefix(prefix)
        if (encodedName.isBlank() || encodedName.contains("?")) return null
        // Commons Special:FilePath redirects to the original or a resized media thumbnail.
        return "https://commons.wikimedia.org/wiki/Special:FilePath/$encodedName?width=$width"
    }
}

data class UniversityIdentity(
    val yokId: Int,
    val name: String,
    val wikidataQid: String?,
    val logo: UniversityImage?,
    val campus: UniversityImage?
)

object UniversityVisuals {
    @Volatile private var cache: Map<Int, UniversityIdentity>? = null
    @Volatile private var imageLoader: ImageLoader? = null

    fun resolve(context: Context, universityId: Int, officialName: String): UniversityIdentity? {
        val record = index(context)[universityId] ?: return null
        // Do not show the wrong logo when an institution is renamed or an ID
        // is reused by a source. Pending aliases must be curated separately.
        if (YokAtlasCatalog.normalize(record.name) != YokAtlasCatalog.normalize(officialName)) return null
        return record
    }

    fun imageLoader(context: Context): ImageLoader {
        imageLoader?.let { return it }
        return synchronized(this) {
            imageLoader ?: ImageLoader.Builder(context.applicationContext)
                .components { add(SvgDecoder.Factory()) }
                .crossfade(140)
                .build()
                .also { imageLoader = it }
        }
    }

    private fun index(context: Context): Map<Int, UniversityIdentity> {
        cache?.let { return it }
        return synchronized(this) {
            cache ?: run {
                val json = context.assets.open("university_visuals.json")
                    .bufferedReader(Charsets.UTF_8).use { JSONObject(it.readText()) }
                val arr = json.getJSONArray("universities")
                val items = buildMap {
                    for (i in 0 until arr.length()) {
                        val university = arr.getJSONObject(i)
                        val id = university.optInt("id")
                        if (id <= 0) continue
                        fun picture(key: String): UniversityImage? {
                            val value = university.optJSONObject(key) ?: return null
                            val source = value.optString("source")
                            val license = value.optString("license")
                            val credit = value.optString("credit")
                            // Only vetted source references can show in the app.
                            if (!source.startsWith("https://commons.wikimedia.org/wiki/File:") ||
                                license.isBlank()) return null
                            return UniversityImage(source, license, credit)
                        }
                        put(id, UniversityIdentity(id, university.optString("name"),
                            university.optString("qid").takeUnless { it == "null" || it.isBlank() },
                            picture("logo"), picture("campus")))
                    }
                }
                items.also { cache = it }
            }
        }
    }
}

@Composable
fun UniversityLogo(universityId: Int, universityName: String, dimension: Dp = 45.dp) {
    val ctx = LocalContext.current
    val identity = remember(universityId, universityName) {
        UniversityVisuals.resolve(ctx, universityId, universityName)
    }
    val url = identity?.logo?.imageUrl(180)
    Surface(
        modifier = Modifier.size(dimension),
        shape = RoundedCornerShape(12.dp),
        color = White,
        border = BorderStroke(1.dp, Hairline)
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (url != null) {
                SubcomposeAsyncImage(
                    model = url,
                    imageLoader = UniversityVisuals.imageLoader(ctx),
                    contentDescription = "$universityName üniversite logosu",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(5.dp)
                ) {
                    when (painter.state) {
                        is coil.compose.AsyncImagePainter.State.Success -> SubcomposeAsyncImageContent()
                        else -> Icon(Icons.Rounded.School, contentDescription = null,
                            tint = Silver, modifier = Modifier.size(20.dp))
                    }
                }
            } else {
                Icon(Icons.Rounded.School, contentDescription = null,
                    tint = Silver, modifier = Modifier.size(20.dp))
            }
        }
    }
}

/**
 * Photo hero is intentionally used only on the full university detail screen:
 * not on every search row, not in the bottom navigation. No crowds of cards.
 * Without verified campus photo, a clean neutral background retains layout.
 */
@Composable
fun UniversityCampusBanner(universityId: Int, universityName: String) {
    val ctx = LocalContext.current
    val identity = remember(universityId, universityName) {
        UniversityVisuals.resolve(ctx, universityId, universityName)
    }
    val image = identity?.campus
    val url = image?.imageUrl(1000)
    Column(Modifier.fillMaxWidth()) {
        Surface(
            shape = RoundedCornerShape(21.dp),
            color = Shell,
            modifier = Modifier.fillMaxWidth().height(145.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (url != null) {
                    SubcomposeAsyncImage(
                        model = url, imageLoader = UniversityVisuals.imageLoader(ctx),
                        contentDescription = "$universityName kampüs fotoğrafı",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (painter.state is coil.compose.AsyncImagePainter.State.Success)
                            SubcomposeAsyncImageContent()
                        else Icon(Icons.Rounded.School, contentDescription = null,
                            tint = Silver, modifier = Modifier.size(32.dp))
                    }
                } else {
                    Icon(Icons.Rounded.School, contentDescription = null, tint = Silver,
                        modifier = Modifier.size(32.dp))
                }
            }
        }
        if (image != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                "Kampüs fotoğrafı: ${image.attribution} · ${image.license} · Wikimedia Commons",
                color = Silver, fontSize = 10.sp, maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun UniversityVisualCredits(universityId: Int, universityName: String, onOpen: (String) -> Unit) {
    val ctx = LocalContext.current
    val identity = remember(universityId, universityName) {
        UniversityVisuals.resolve(ctx, universityId, universityName)
    }
    val visuals = listOfNotNull(identity?.logo?.let { "Logo" to it },
        identity?.campus?.let { "Kampüs" to it })
    if (visuals.isNotEmpty()) {
        Column(Modifier.fillMaxWidth()) {
            Text("Görsel kaynakları", color = Onyx, fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp)
            Spacer(Modifier.height(5.dp))
            visuals.forEach { (kind, media) ->
                TextButton(onClick = { onOpen(media.sourcePage) },
                    contentPadding = PaddingValues(vertical = 4.dp, horizontal = 0.dp)) {
                    Text("$kind · ${media.attribution} · ${media.license} ↗",
                        fontSize = 11.sp, color = Silver, lineHeight = 15.sp)
                }
            }
            Text("Görseller ilgili kurum ve fotoğrafçılarına aittir; DenemeLab ile kurumsal iş birliği anlamına gelmez.",
                color = Silver, fontSize = 10.sp, lineHeight = 14.sp)
        }
    }
}
