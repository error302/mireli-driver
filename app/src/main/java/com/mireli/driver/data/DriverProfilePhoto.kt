package com.mireli.driver.data

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val PhotoSide = 512
private const val MaxPhotoBytes = 192 * 1024

internal class InvalidDriverPhoto : Exception("Choose a readable photo from your gallery.")

internal suspend fun prepareDriverProfilePhoto(context: Context, uri: Uri): ByteArray =
    withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val bitmap = try {
            if (Build.VERSION.SDK_INT >= 28) decodePhoto(resolver, uri) else decodeLegacyPhoto(resolver, uri)
        } catch (_: Exception) {
            throw InvalidDriverPhoto()
        }
        try {
            val output = ByteArrayOutputStream()
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 78, output) || output.size() > MaxPhotoBytes) {
                throw InvalidDriverPhoto()
            }
            output.toByteArray()
        } finally {
            bitmap.recycle()
        }
    }

@RequiresApi(28)
private fun decodePhoto(resolver: ContentResolver, uri: Uri): Bitmap =
    ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
        val longestSide = maxOf(info.size.width, info.size.height)
        require(longestSide in 1..32_000)
        val scale = minOf(1.0, PhotoSide.toDouble() / longestSide)
        decoder.setTargetSize((info.size.width * scale).roundToInt().coerceAtLeast(1),
            (info.size.height * scale).roundToInt().coerceAtLeast(1))
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
    }

private fun decodeLegacyPhoto(resolver: ContentResolver, uri: Uri): Bitmap {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    val longestSide = maxOf(bounds.outWidth, bounds.outHeight)
    require(longestSide in 1..32_000)
    val orientation = resolver.openInputStream(uri)?.use { input ->
        runCatching { ExifInterface(input).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1) }.getOrDefault(1)
    }
    val options = BitmapFactory.Options().apply {
        while (longestSide.toDouble() / inSampleSize.coerceAtLeast(1) > PhotoSide) {
            inSampleSize = inSampleSize.coerceAtLeast(1) * 2
        }
    }
    val bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        ?: throw InvalidDriverPhoto()
    val transform = Matrix()
    when (orientation) {
        2 -> transform.setScale(-1f, 1f)
        3 -> transform.setRotate(180f)
        4 -> transform.setScale(1f, -1f)
        5 -> { transform.setRotate(90f); transform.postScale(-1f, 1f) }
        6 -> transform.setRotate(90f)
        7 -> { transform.setRotate(270f); transform.postScale(-1f, 1f) }
        8 -> transform.setRotate(270f)
    }
    if (transform.isIdentity) return bitmap
    return try {
        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, transform, true)
    } finally {
        bitmap.recycle()
    }
}
