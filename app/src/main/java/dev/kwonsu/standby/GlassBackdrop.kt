package dev.kwonsu.standby

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer

internal data class GlassBackdrop(val layer: GraphicsLayer, val offset: Offset)

/** 기존 그리기 순서를 유지하면서 유리 뒤쪽의 내용만 재사용한다. */
internal fun Modifier.recordGlassBackdrop(layer: GraphicsLayer?): Modifier =
    if (layer == null) this else drawWithContent {
        layer.record { this@drawWithContent.drawContent() }
        drawLayer(layer)
    }
