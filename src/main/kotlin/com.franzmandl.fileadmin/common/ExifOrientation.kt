package com.franzmandl.fileadmin.common

import org.apache.commons.imaging.formats.tiff.constants.TiffTagConstants

sealed interface ExifOrientation {
    data object Unset : ExifOrientation

    data class Unknown(val value: Int) : ExifOrientation

    enum class Known(val value: Int) : ExifOrientation {
        HorizontalNormal(TiffTagConstants.ORIENTATION_VALUE_HORIZONTAL_NORMAL),
        MirrorHorizontal(TiffTagConstants.ORIENTATION_VALUE_MIRROR_HORIZONTAL),
        Rotate180(TiffTagConstants.ORIENTATION_VALUE_ROTATE_180),
        MirrorVertical(TiffTagConstants.ORIENTATION_VALUE_MIRROR_VERTICAL),
        MirrorHorizontalAndRotate270Cw(TiffTagConstants.ORIENTATION_VALUE_MIRROR_HORIZONTAL_AND_ROTATE_270_CW),
        Rotate90Cw(TiffTagConstants.ORIENTATION_VALUE_ROTATE_90_CW),
        MirrorHorizontalAndRotate90Cw(TiffTagConstants.ORIENTATION_VALUE_MIRROR_HORIZONTAL_AND_ROTATE_90_CW),
        Rotate270Cw(TiffTagConstants.ORIENTATION_VALUE_ROTATE_270_CW),
        ;

        companion object {
            val orientations = entries.associateBy { it.value }
        }
    }
}