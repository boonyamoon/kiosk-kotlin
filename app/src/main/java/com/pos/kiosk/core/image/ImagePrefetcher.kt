package com.pos.kiosk.core.image

import android.content.Context
import coil3.SingletonImageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

/** Downloads images ahead of time so they show instantly, and still show when the network drops. */
interface ImagePrefetcher {
    fun prefetch(urls: List<String>)
}

/**
 * Warms Coil's disk cache only. The memory cache is skipped and the decode is tiny (64 px): a dozen
 * full-screen screensaver bitmaps held in memory would crowd out the menu photos.
 */
@Singleton
class CoilImagePrefetcher @Inject constructor(@ApplicationContext private val context: Context) : ImagePrefetcher {
    override fun prefetch(urls: List<String>) {
        val loader = SingletonImageLoader.get(context)
        urls.distinct().forEach { url ->
            loader.enqueue(
                ImageRequest.Builder(context)
                    .data(url)
                    .size(PREFETCH_DECODE_PX)
                    .memoryCachePolicy(CachePolicy.DISABLED)
                    .build(),
            )
        }
    }

    private companion object {
        const val PREFETCH_DECODE_PX = 64
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ImageModule {
    @Binds abstract fun imagePrefetcher(impl: CoilImagePrefetcher): ImagePrefetcher
}
