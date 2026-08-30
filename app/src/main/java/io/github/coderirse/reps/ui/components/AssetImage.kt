package io.github.coderirse.reps.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Renders a question image from app assets, downsampled to keep memory sane.
 * Tap opens a fullscreen preview (docs/PRODUCT.md section 7.9).
 */
@Composable
fun AssetImage(
    assetPath: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val context = LocalContext.current
    var bitmap by remember(assetPath) { mutableStateOf<ImageBitmap?>(null) }
    var showFullscreen by remember(assetPath) { mutableStateOf(false) }

    LaunchedEffect(assetPath) {
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.assets.open(assetPath).use { BitmapFactory.decodeStream(it, null, bounds) }
                var sample = 1
                while (bounds.outWidth / (sample * 2) >= 1080) sample *= 2
                val options = BitmapFactory.Options().apply { inSampleSize = sample }
                context.assets.open(assetPath).use {
                    BitmapFactory.decodeStream(it, null, options)
                }?.asImageBitmap()
            }.getOrNull()
        }
    }

    bitmap?.let { bmp ->
        Image(
            bitmap = bmp,
            contentDescription = contentDescription,
            modifier = modifier
                .fillMaxWidth()
                .clickable { showFullscreen = true },
            contentScale = ContentScale.FillWidth,
        )
        if (showFullscreen) {
            Dialog(onDismissRequest = { showFullscreen = false }) {
                Image(
                    bitmap = bmp,
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.FillWidth,
                )
            }
        }
    }
}
