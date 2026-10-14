package com.openscad.viewer.render

import com.openscad.core.Bounds
import com.openscad.core.Solid
import com.openscad.core.Vec3

/**
 * Flattened, render-ready form of a [Solid].
 *
 * Triangles are kept in world space with a per-face normal; the viewport projects
 * them itself. Doing the flattening once (instead of per frame) keeps gesture
 * handling responsive.
 */
class RenderMesh(
    val triangles: List<Tri>,
    val bounds: Bounds,
    val center: Vec3,
    val radius: Double,
) {
    data class Tri(val a: Vec3, val b: Vec3, val c: Vec3, val normal: Vec3)

    val triangleCount: Int get() = triangles.size

    companion object {
        fun from(solid: Solid): RenderMesh {
            val triangles = ArrayList<Tri>()
            for (polygon in solid.triangles()) {
                val a = polygon.vertices[0]
                val b = polygon.vertices[1]
                val c = polygon.vertices[2]
                val normal = (b - a).cross(c - b).normalized()
                triangles += Tri(a, b, c, normal)
            }

            val bounds = if (solid.isEmpty) {
                Bounds(Vec3(0.0, 0.0, 0.0), Vec3(0.0, 0.0, 0.0))
            } else {
                solid.bounds
            }
            val center = (bounds.min + bounds.max) * 0.5
            val radius = maxOf((bounds.max - bounds.min).length() / 2.0, 1e-6)
            return RenderMesh(triangles, bounds, center, radius)
        }
    }
}
