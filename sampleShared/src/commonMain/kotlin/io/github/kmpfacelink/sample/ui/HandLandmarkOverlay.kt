package io.github.kmpfacelink.sample.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import io.github.kmpfacelink.model.HandJoint
import io.github.kmpfacelink.model.HandLandmarkPoint

private val TIP_JOINTS = setOf(
    HandJoint.THUMB_TIP,
    HandJoint.INDEX_FINGER_TIP,
    HandJoint.MIDDLE_FINGER_TIP,
    HandJoint.RING_FINGER_TIP,
    HandJoint.PINKY_TIP,
)

/**
 * Draws hand landmark dots and bone connections over the camera preview.
 */
@Composable
public fun HandLandmarkOverlay(
    landmarks: List<HandLandmarkPoint>,
    sourceImageWidth: Int,
    sourceImageHeight: Int,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val viewW = size.width
        val viewH = size.height

        if (sourceImageWidth <= 0 || sourceImageHeight <= 0) return@Canvas

        val imageAspect = sourceImageWidth.toFloat() / sourceImageHeight
        val viewAspect = viewW / viewH

        val scale: Float
        val offsetX: Float
        val offsetY: Float
        if (imageAspect > viewAspect) {
            scale = viewH / sourceImageHeight
            val renderedW = sourceImageWidth * scale
            offsetX = (renderedW - viewW) / 2f
            offsetY = 0f
        } else {
            scale = viewW / sourceImageWidth
            val renderedH = sourceImageHeight * scale
            offsetX = 0f
            offsetY = (renderedH - viewH) / 2f
        }

        val landmarkMap = landmarks.associateBy { it.joint }

        fun pos(joint: HandJoint): Offset? {
            val lm = landmarkMap[joint] ?: return null
            val px = lm.x * sourceImageWidth * scale - offsetX
            val py = lm.y * sourceImageHeight * scale - offsetY
            return Offset(px, py)
        }

        val boneColor = SampleColors.BoneColor.copy(alpha = 0.7f)
        val boneStroke = 2.dp.toPx()

        fun drawBone(from: HandJoint, to: HandJoint) {
            val a = pos(from) ?: return
            val b = pos(to) ?: return
            drawLine(color = boneColor, start = a, end = b, strokeWidth = boneStroke)
        }

        drawWristBones(::drawBone)
        drawFingerBones(::drawBone)
        drawPalmBones(::drawBone)

        val dotRadius = 3.dp.toPx()
        for (lm in landmarks) {
            val p = pos(lm.joint) ?: continue
            val color = if (lm.joint in TIP_JOINTS) SampleColors.TipColor else SampleColors.JointColor
            drawCircle(color = color, radius = dotRadius, center = p)
        }
    }
}

private inline fun drawWristBones(drawBone: (HandJoint, HandJoint) -> Unit) {
    drawBone(HandJoint.WRIST, HandJoint.THUMB_CMC)
    drawBone(HandJoint.WRIST, HandJoint.INDEX_FINGER_MCP)
    drawBone(HandJoint.WRIST, HandJoint.MIDDLE_FINGER_MCP)
    drawBone(HandJoint.WRIST, HandJoint.RING_FINGER_MCP)
    drawBone(HandJoint.WRIST, HandJoint.PINKY_MCP)
}

@Suppress("LongMethod")
private inline fun drawFingerBones(drawBone: (HandJoint, HandJoint) -> Unit) {
    drawBone(HandJoint.THUMB_CMC, HandJoint.THUMB_MCP)
    drawBone(HandJoint.THUMB_MCP, HandJoint.THUMB_IP)
    drawBone(HandJoint.THUMB_IP, HandJoint.THUMB_TIP)

    drawBone(HandJoint.INDEX_FINGER_MCP, HandJoint.INDEX_FINGER_PIP)
    drawBone(HandJoint.INDEX_FINGER_PIP, HandJoint.INDEX_FINGER_DIP)
    drawBone(HandJoint.INDEX_FINGER_DIP, HandJoint.INDEX_FINGER_TIP)

    drawBone(HandJoint.MIDDLE_FINGER_MCP, HandJoint.MIDDLE_FINGER_PIP)
    drawBone(HandJoint.MIDDLE_FINGER_PIP, HandJoint.MIDDLE_FINGER_DIP)
    drawBone(HandJoint.MIDDLE_FINGER_DIP, HandJoint.MIDDLE_FINGER_TIP)

    drawBone(HandJoint.RING_FINGER_MCP, HandJoint.RING_FINGER_PIP)
    drawBone(HandJoint.RING_FINGER_PIP, HandJoint.RING_FINGER_DIP)
    drawBone(HandJoint.RING_FINGER_DIP, HandJoint.RING_FINGER_TIP)

    drawBone(HandJoint.PINKY_MCP, HandJoint.PINKY_PIP)
    drawBone(HandJoint.PINKY_PIP, HandJoint.PINKY_DIP)
    drawBone(HandJoint.PINKY_DIP, HandJoint.PINKY_TIP)
}

private inline fun drawPalmBones(drawBone: (HandJoint, HandJoint) -> Unit) {
    drawBone(HandJoint.INDEX_FINGER_MCP, HandJoint.MIDDLE_FINGER_MCP)
    drawBone(HandJoint.MIDDLE_FINGER_MCP, HandJoint.RING_FINGER_MCP)
    drawBone(HandJoint.RING_FINGER_MCP, HandJoint.PINKY_MCP)
}
