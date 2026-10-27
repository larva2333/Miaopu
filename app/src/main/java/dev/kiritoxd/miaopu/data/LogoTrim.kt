package dev.kiritoxd.miaopu.data

import android.graphics.Bitmap
import coil3.size.Size
import coil3.transform.Transformation

/**
 * League logos arrive as square canvases where the crest itself may occupy only a fraction of the
 * image (the rest is transparent padding). Fit-scaling such an image shrinks the visible mark, so
 * every consumer trims to the opaque bounding box first.
 */
fun Bitmap.trimTransparent(): Bitmap {
    val width = width
    val height = height
    val pixels = IntArray(width * height)
    getPixels(pixels, 0, width, 0, 0, width, height)
    var minX = width
    var minY = height
    var maxX = -1
    var maxY = -1
    for (y in 0 until height) {
        val row = y * width
        for (x in 0 until width) {
            if ((pixels[row + x] ushr 24) != 0) {
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
            }
        }
    }
    if (maxX < 0) return this
    if (minX == 0 && minY == 0 && maxX == width - 1 && maxY == height - 1) return this
    return Bitmap.createBitmap(this, minX, minY, maxX - minX + 1, maxY - minY + 1)
}

/** Coil transformation so every in-app logo render gets the same trimmed bitmap. */
internal class TrimTransparentTransformation : Transformation() {
    override val cacheKey: String = "miaopu-trim-transparent"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap = input.trimTransparent()
}
