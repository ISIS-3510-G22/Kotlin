package com.example.plansync.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import coil.request.ImageRequest

object ImageCache {

    private const val MEMORY_PERCENT = 0.15
    private const val DISK_SIZE_BYTES = 50L * 1024 * 1024

    @Volatile
    private var loader: ImageLoader? = null

    fun loader(context: Context): ImageLoader = loader ?: synchronized(this) {
        loader ?: build(context.applicationContext).also { loader = it }
    }

    private fun build(context: Context) = ImageLoader.Builder(context)
        .memoryCache { MemoryCache.Builder(context).maxSizePercent(MEMORY_PERCENT).build() }
        .diskCache {
            DiskCache.Builder()
                .directory(context.cacheDir.resolve("image_cache"))
                .maxSizeBytes(DISK_SIZE_BYTES)
                .build()
        }
        .respectCacheHeaders(false)
        .crossfade(true)
        .build()
}

@Composable
fun CachedImage(
    url: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    placeholder: Painter? = null
) {
    val context = LocalContext.current
    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(url)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .build(),
        imageLoader = ImageCache.loader(context),
        contentDescription = contentDescription,
        contentScale = contentScale,
        placeholder = placeholder,
        error = placeholder,
        modifier = modifier
    )
}
