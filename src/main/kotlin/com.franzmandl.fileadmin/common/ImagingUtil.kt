package com.franzmandl.fileadmin.common

import org.apache.commons.imaging.Imaging
import org.apache.commons.imaging.formats.jpeg.JpegImageMetadata
import org.apache.commons.imaging.formats.tiff.constants.TiffTagConstants
import java.awt.Color
import java.awt.Image
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.InputStream
import javax.imageio.ImageIO

object ImagingUtil {
    private val backgroundColor = Color(0xe9, 0xec, 0xef)

    fun scale(input: BufferedImage, maxDimension: Int): BufferedImage? {
        var width = input.width
        var height = input.height
        if (width <= maxDimension && height <= maxDimension) {
            return null
        }
        if (width < height) {
            width = maxDimension * width / height
            height = maxDimension
        } else {
            height = maxDimension * height / width
            width = maxDimension
        }
        val scaledImage = input.getScaledInstance(width, height, Image.SCALE_SMOOTH)
        val output = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        output.graphics.drawImage(scaledImage, 0, 0, backgroundColor, null)
        return output
    }

    fun toJpgByteArray(input: BufferedImage): ByteArray {
        val outputStream = ByteArrayOutputStream()
        ImageIO.write(input, "jpg", outputStream)
        return outputStream.toByteArray()
    }

    fun getExifOrientation(inputStream: InputStream, fileName: String): ExifOrientation {
        return when (val metadata = Imaging.getMetadata(inputStream, fileName)) {
            is JpegImageMetadata -> {
                val field = metadata.findExifValueWithExactMatch(TiffTagConstants.TIFF_TAG_ORIENTATION) ?: return ExifOrientation.Unset
                val raw = field.intValue
                ExifOrientation.Known.orientations[raw] ?: ExifOrientation.Unknown(raw)
            }

            else -> ExifOrientation.Unset
        }
    }

    fun transformImage(input: BufferedImage, transform: AffineTransform, orientation: ExifOrientation.Known): BufferedImage {
        val output = when (orientation) {
            ExifOrientation.Known.HorizontalNormal,
            ExifOrientation.Known.MirrorHorizontal,
            ExifOrientation.Known.Rotate180,
            ExifOrientation.Known.MirrorVertical -> BufferedImage(input.width, input.height, input.type)

            ExifOrientation.Known.MirrorHorizontalAndRotate270Cw,
            ExifOrientation.Known.Rotate90Cw,
            ExifOrientation.Known.MirrorHorizontalAndRotate90Cw,
            ExifOrientation.Known.Rotate270Cw -> BufferedImage(input.height, input.width, input.type)
        }
        val graphics = output.createGraphics()
        graphics.transform(transform)
        graphics.drawRenderedImage(input, null)
        return output
    }

    fun getTransformation(orientation: ExifOrientation.Known, width: Int, height: Int): AffineTransform {
        val transform = AffineTransform()
        when (orientation) {
            ExifOrientation.Known.HorizontalNormal -> Unit
            ExifOrientation.Known.MirrorHorizontal -> {
                transform.scale(-1.0, 1.0)
                transform.translate(-width.toDouble(), 0.0)
            }

            ExifOrientation.Known.Rotate180 -> {
                transform.translate(width.toDouble(), height.toDouble())
                transform.quadrantRotate(2)
            }

            ExifOrientation.Known.MirrorVertical -> {
                transform.scale(1.0, -1.0)
                transform.translate(0.0, -height.toDouble())
            }

            ExifOrientation.Known.MirrorHorizontalAndRotate270Cw -> {
                transform.quadrantRotate(3)
                transform.scale(-1.0, 1.0)
            }

            ExifOrientation.Known.Rotate90Cw -> {
                transform.translate(height.toDouble(), 0.0)
                transform.quadrantRotate(1)
            }

            ExifOrientation.Known.MirrorHorizontalAndRotate90Cw -> {
                transform.scale(-1.0, 1.0)
                transform.translate(-height.toDouble(), 0.0)
                transform.translate(0.0, width.toDouble())
                transform.quadrantRotate(3)
            }

            ExifOrientation.Known.Rotate270Cw -> {
                transform.translate(0.0, width.toDouble())
                transform.quadrantRotate(3)
            }
        }
        return transform
    }
}