package com.openscad.viewer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.openscad.core.Vec3
import com.openscad.viewer.render.RenderMesh
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Software 3D viewport: orthographic projection, painter's-algorithm depth sort
 * and two-sided Lambert shading, drawn with Compose [Canvas].
 *
 * It deliberately needs no GPU features beyond what Compose already uses, so it
 * renders identically on Waydroid, old devices and emulators. A Filament surface
 * can later be substituted behind the same `RenderState` contract.
 *
 * Gestures: one finger orbits, two fingers pinch-zoom and pan.
 */
@Composable
fun MeshViewport(
    state: RenderState,
    modifier: Modifier = Modifier,
) {
    var yaw by remember { mutableFloatStateOf(0.7f) }
    var pitch by remember { mutableFloatStateOf(0.45f) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }

    val mesh = (state as? RenderState.Ready)?.mesh

    Box(
        modifier = modifier
            .background(
                Brush.radialGradient(
                    listOf(Color(0xFF2A2B34), Color(0xFF1A1A20), Color(0xFF101014)),
                ),
            )
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.count { it.pressed }
                        if (pressed == 0) break

                        val zoomChange = event.calculateZoom()
                        val panChange = event.calculatePan()

                        if (pressed >= 2) {
                            if (zoomChange > 0f && zoomChange != 1f) {
                                zoom = (zoom * zoomChange).coerceIn(0.15f, 10f)
                            }
                            if (panChange != Offset.Zero) pan += panChange
                        } else if (panChange != Offset.Zero) {
                            yaw -= panChange.x * 0.012f
                            pitch = (pitch + panChange.y * 0.012f).coerceIn(-1.45f, 1.45f)
                        }
                        event.changes.forEach { it.consume() }
                    }
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            mesh?.let { drawMesh(it, yaw, pitch, zoom, pan) }
        }

        if (mesh != null) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(10.dp),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.88f),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Hud("tris", mesh.triangleCount.toString())
                    Hud(
                        "size",
                        "%.0f×%.0f×%.0f".format(
                            mesh.bounds.size.x,
                            mesh.bounds.size.y,
                            mesh.bounds.size.z,
                        ),
                    )
                }
            }
        }

        Surface(
            onClick = {
                yaw = 0.7f; pitch = 0.45f; zoom = 1f; pan = Offset.Zero
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f),
        ) {
            Text(
                text = "⤾ reset view",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }

        when (state) {
            is RenderState.Loading -> OverlayCard {
                CircularProgressIndicator(modifier = Modifier.padding(end = 12.dp))
                Text("Rendering…", style = MaterialTheme.typography.bodyMedium)
            }

            is RenderState.Failed -> OverlayCard {
                Column {
                    Text(
                        text = "Render failed",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            else -> Unit
        }
    }
}

@Composable
private fun Hud(key: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(
            text = key,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun OverlayCard(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Surface(
            modifier = Modifier.padding(12.dp),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f),
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) { content() }
        }
    }
}

private fun DrawScope.drawMesh(
    mesh: RenderMesh,
    yaw: Float,
    pitch: Float,
    zoom: Float,
    pan: Offset,
) {
    if (mesh.triangles.isEmpty()) return

    val cx = size.width / 2f
    val cy = size.height / 2f

    // Orbit camera around the model.
    val cosPitch = cos(pitch.toDouble())
    val sinPitch = sin(pitch.toDouble())
    val dir = Vec3(cosPitch * sin(yaw.toDouble()), sinPitch, cosPitch * cos(yaw.toDouble()))
    val eye = mesh.center + dir * (mesh.radius * 4.0)

    val forward = (mesh.center - eye).normalized()
    var right = forward.cross(Vec3(0.0, 1.0, 0.0))
    if (right.length() < 1e-9) right = Vec3(1.0, 0.0, 0.0)
    right = right.normalized()
    val up = right.cross(forward).normalized()

    val scale = min(size.width, size.height) * 0.40f / mesh.radius * zoom

    // Head-light anchored to the camera so the model stays readable while orbiting.
    val light = (up * 0.5 + right * -0.45 + dir * 0.72).normalized()

    val count = mesh.triangles.size
    val depth = FloatArray(count)
    val order = ArrayList<Int>(count)
    for (i in 0 until count) {
        val t = mesh.triangles[i]
        val centroid = (t.a + t.b + t.c) * (1.0 / 3.0)
        depth[i] = (centroid - eye).dot(forward).toFloat()
        order += i
    }
    order.sortWith(compareByDescending { depth[it] })

    val baseR = 0.965f
    val baseG = 0.851f
    val baseB = 0.290f
    val edgeColor = Color(0.16f, 0.12f, 0.0f, 0.55f)
    val drawEdges = count <= 8000

    val path = Path()

    fun screenX(p: Vec3) = (cx + ((p - eye).dot(right) * scale + pan.x)).toFloat()
    fun screenY(p: Vec3) = (cy - (p - eye).dot(up) * scale + pan.y).toFloat()

    for (index in order) {
        val t = mesh.triangles[index]
        val centroid = (t.a + t.b + t.c) * (1.0 / 3.0)
        val viewDir = (eye - centroid).normalized()

        // Two-sided shading: flip the normal toward the viewer so inconsistent
        // winding never produces black faces.
        val normal = if (t.normal.dot(viewDir) < 0) -t.normal else t.normal
        val lambert = max(0.0, normal.dot(light))
        val intensity = (0.26 + 0.74 * lambert).toFloat()

        path.rewind()
        path.moveTo(screenX(t.a), screenY(t.a))
        path.lineTo(screenX(t.b), screenY(t.b))
        path.lineTo(screenX(t.c), screenY(t.c))
        path.close()

        drawPath(path, Color(baseR * intensity, baseG * intensity, baseB * intensity))
        if (drawEdges) drawPath(path, edgeColor, style = Stroke(width = 0.7f))
    }
}
