package com.fahadali.oneos.core.model

/** The complete embedded brand heading in each supplied export, in normalized coordinates. */
object ArtworkFraming {
    data class Heading(val left: Float, val top: Float, val right: Float, val bottom: Float)
    data class Transform(val scale: Float, val x: Float, val y: Float)
    fun heading(family: SystemFamily, wide: Boolean = false): Heading = when (family) {
        SystemFamily.INFINITYX -> Heading(.025f, .41f, .975f, .59f)
        // LineageOS 23 / 24 and Evolution X cards frame around the central logo/wordmark.
        SystemFamily.LINEAGE, SystemFamily.EVOX -> if (wide) Heading(.153f, .371f, .846f, .629f) else Heading(.153f, .406f, .845f, .594f)
    }
    fun transform(family: SystemFamily, sourceWidth: Int, sourceHeight: Int, width: Int, height: Int, reservedBottom: Float = 0f): Transform {
        require(sourceWidth > 0 && sourceHeight > 0 && width > 0 && height > 0)
        val heading = heading(family, sourceWidth.toFloat() / sourceHeight > .6f)
        val areaHeight = (height - reservedBottom.coerceIn(0f, height * .4f)).coerceAtLeast(1f)
        val cover = maxOf(width.toFloat() / sourceWidth, height.toFloat() / sourceHeight)
        // Keep the real bitmap covering the view: no edge stretching or side bands.
        val x = (width / 2f - (heading.left + heading.right) / 2f * sourceWidth * cover)
            .coerceIn(minOf(width - sourceWidth * cover, 0f), 0f)
        val y = (areaHeight / 2f - (heading.top + heading.bottom) / 2f * sourceHeight * cover)
            .coerceIn(minOf(height - sourceHeight * cover, 0f), 0f)
        return Transform(cover, x, y)
    }
}
