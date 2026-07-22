package com.uniaball.downloader.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 入场动画：淡入 + 上滑。
 * 基于 Modifier.Node 实现，避免 Modifier.composed 为每个 item 创建独立子 composition。
 * 动画状态由 Node 自身的 coroutineScope 驱动 Animatable，draw 延迟读取其 value。
 * 视觉效果与原实现一致：alpha 0→1，translationY 24dp→0，tween(durationMillis)，前置 delayMillis。
 */
fun Modifier.entranceAnimation(
    delayMillis: Int = 0,
    durationMillis: Int = 300
): Modifier = this.then(EntranceAnimationElement(delayMillis, durationMillis))

private class EntranceAnimationElement(
    private val delayMillis: Int,
    private val durationMillis: Int
) : ModifierNodeElement<EntranceAnimationNode>() {
    override fun create(): EntranceAnimationNode = EntranceAnimationNode(delayMillis, durationMillis)
    override fun update(node: EntranceAnimationNode) {
        if (node.delayMillis != delayMillis || node.durationMillis != durationMillis) {
            node.delayMillis = delayMillis
            node.durationMillis = durationMillis
        }
    }
    override fun InspectorInfo.inspectableProperties() {
        name = "entranceAnimation"
        properties["delayMillis"] = delayMillis
        properties["durationMillis"] = durationMillis
    }
    override fun hashCode(): Int = delayMillis * 31 + durationMillis
    override fun equals(other: Any?): Boolean =
        other is EntranceAnimationElement && delayMillis == other.delayMillis && durationMillis == other.durationMillis
}

private class EntranceAnimationNode(
    var delayMillis: Int,
    var durationMillis: Int
) : Modifier.Node(), DrawModifierNode {

    private val alphaState = Animatable(0f)
    private val translationYState = Animatable(0f)
    private val alphaPaint = Paint()

    override fun onAttach() {
        super.onAttach()
        coroutineScope.launch {
            // 初始 translationY = 24dp 对应像素
            val startOffsetPx = with(density) { 24.dp.toPx() }
            translationYState.snapTo(startOffsetPx)
            delay(delayMillis.toLong())
            launch { alphaState.animateTo(1f, tween(durationMillis)) }
            launch { translationYState.animateTo(0f, tween(durationMillis)) }
        }
    }

    override fun ContentDrawScope.draw() {
        val a = alphaState.value
        val ty = translationYState.value
        if (a <= 0f) return // 完全透明时跳过绘制
        if (a >= 1f && ty == 0f) {
            drawContent()
            return
        }
        // 平移 + 透明度合成
        drawIntoCanvas { canvas ->
            canvas.save()
            canvas.translate(0f, ty)
            if (a < 1f) {
                alphaPaint.alpha = a
                canvas.saveLayer(Offset.Zero, size, alphaPaint)
                drawContent()
                canvas.restore()
            } else {
                drawContent()
            }
            canvas.restore()
        }
    }
}
