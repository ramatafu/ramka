package com.ramka.app.ui.chat

import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Material «Videocam» (filled). В базовом наборе material-icons его нет, а подключать весь
 * material-icons-extended ради одной иконки не нужно — векторы Material свободно копируются.
 */
internal val VideocamIcon: ImageVector by lazy {
    materialIcon(name = "Filled.Videocam") {
        materialPath {
            moveTo(17f, 10.5f)
            verticalLineTo(7f)
            curveToRelative(0f, -0.55f, -0.45f, -1f, -1f, -1f)
            horizontalLineTo(4f)
            curveToRelative(-0.55f, 0f, -1f, 0.45f, -1f, 1f)
            verticalLineToRelative(10f)
            curveToRelative(0f, 0.55f, 0.45f, 1f, 1f, 1f)
            horizontalLineToRelative(12f)
            curveToRelative(0.55f, 0f, 1f, -0.45f, 1f, -1f)
            verticalLineToRelative(-3.5f)
            lineToRelative(4f, 4f)
            verticalLineToRelative(-11f)
            lineToRelative(-4f, 4f)
            close()
        }
    }
}
