package com.denemelab.preview

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.compose.AsyncImagePainter

/**
 * One quiet image tile in the exam list. Missing logos never introduce
 * unlicensed visual assets or a broken image icon.
 */
@Composable
fun PublisherLogo(
    publisherName: String,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    pendingSelection: Uri? = null
) {
    val context = LocalContext.current
    val saved = PublisherCatalog.find(context, publisherName)
    val model: Any? = pendingSelection ?: saved?.let { PublisherCatalog.logoFile(context, it) }
    Surface(
        modifier = modifier.size(size),
        shape = RoundedCornerShape(14.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Color(0xFFE8E8E8))
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (model != null) {
                SubcomposeAsyncImage(
                    model = model,
                    contentDescription = if (publisherName.isNotBlank()) "$publisherName logosu" else "Yayın logosu",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(5.dp)
                ) {
                    if (painter.state is AsyncImagePainter.State.Success)
                        SubcomposeAsyncImageContent()
                    else PublisherLogoFallback(publisherName)
                }
            } else PublisherLogoFallback(publisherName)
        }
    }
}

@Composable
private fun PublisherLogoFallback(publisherName: String) {
    if (publisherName.isBlank()) {
        Icon(Icons.Rounded.AutoStories, contentDescription = null,
            modifier = Modifier.size(20.dp), tint = Color(0xFF888888))
    } else {
        Text(
            publisherName.trim().take(2).uppercase(),
            fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF333333),
            maxLines = 1, overflow = TextOverflow.Clip
        )
    }
}
