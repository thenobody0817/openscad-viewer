package com.openscad.core

import org.locationtech.jts.algorithm.ConvexHull
import org.locationtech.jts.algorithm.Orientation
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.GeometryCollection
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.LinearRing
import org.locationtech.jts.geom.MultiPolygon
import org.locationtech.jts.geom.Polygon as JtsPolygon
import org.locationtech.jts.operation.buffer.BufferOp
import org.locationtech.jts.operation.buffer.BufferParameters
import org.locationtech.jts.triangulate.polygon.PolygonTriangulator
import kotlin.math.cos
import kotlin.math.sin

/**
 * 2D region kernel for the evaluator, backed by JTS.
 *
 * OpenSCAD mixes 2D shapes (`circle`, `square`, `polygon`) with the 2D
 * transforms and booleans, then lifts the result to 3D with `linear_extrude`.
 * JTS gives us robust polygon overlay (`union`/`difference`/`intersection`),
 * polygon buffering (`offset`), convex hull (`hull`) and ear-clip
 * triangulation (the extrude caps) instead of hand-rolling a clipper.
 *
 * Coordinates are the same millimetres the rest of the evaluator uses; the Z
 * axis only appears at extrusion time.
 */
object Scad2D {

    val factory: GeometryFactory = GeometryFactory()

    /** Empty polygonal geometry. */
    fun empty(): Geometry = factory.createPolygon()

    /** Keeps only the polygonal part of [g], unioning anything mixed. */
    fun polygonal(g: Geometry): Geometry = when (g) {
        is JtsPolygon, is MultiPolygon -> g
        is GeometryCollection -> {
            val parts = (0 until g.numGeometries)
                .map { g.getGeometryN(it) }
                .filter { it is JtsPolygon || it is MultiPolygon }
            when {
                parts.isEmpty() -> empty()
                parts.size == 1 -> parts.first()
                else -> parts.reduce { a, b -> a.union(b) }
            }
        }
        else -> empty()
    }

    fun circle(radius: Double, segments: Int): Geometry {
        if (radius <= 0.0) return empty()
        val n = segments.coerceAtLeast(3)
        val coords = Array(n + 1) { i ->
            val a = 2.0 * Math.PI * (i % n) / n
            Coordinate(radius * cos(a), radius * sin(a))
        }
        return factory.createPolygon(coords)
    }

    fun square(width: Double, height: Double, center: Boolean): Geometry {
        val x0 = if (center) -width / 2 else 0.0
        val y0 = if (center) -height / 2 else 0.0
        val coords = arrayOf(
            Coordinate(x0, y0),
            Coordinate(x0 + width, y0),
            Coordinate(x0 + width, y0 + height),
            Coordinate(x0, y0 + height),
            Coordinate(x0, y0),
        )
        return factory.createPolygon(coords)
    }

    /** Simple polygon from a flat list of points; holes (`paths`) are ignored. */
    fun polygon(points: List<Vec3>): Geometry {
        if (points.size < 3) return empty()
        val coords = ArrayList<Coordinate>(points.size + 1)
        points.forEach { coords += Coordinate(it.x, it.y) }
        coords += Coordinate(points.first().x, points.first().y)
        return factory.createPolygon(coords.toTypedArray())
    }

    fun union(a: Geometry, b: Geometry): Geometry = safe(a) { it.union(safe(b) { g -> g }) }

    fun difference(a: Geometry, b: Geometry): Geometry = safe(a) { it.difference(safe(b) { g -> g }) }

    fun intersection(a: Geometry, b: Geometry): Geometry = safe(a) { it.intersection(safe(b) { g -> g }) }

    fun unionAll(parts: List<Geometry>): Geometry {
        val nonEmpty = parts.filter { !it.isEmpty }
        if (nonEmpty.isEmpty()) return empty()
        return nonEmpty.reduce { acc, g -> union(acc, g) }
    }

    fun intersectionAll(parts: List<Geometry>): Geometry {
        val nonEmpty = parts.filter { !it.isEmpty }
        if (nonEmpty.isEmpty()) return empty()
        return nonEmpty.reduce { acc, g -> intersection(acc, g) }
    }

    /** `offset(r = r)` rings corners; `offset(delta = d)` keeps them. */
    fun offset(g: Geometry, r: Double, delta: Double, segments: Int, round: Boolean): Geometry {
        val distance = if (r != 0.0) r else delta
        if (distance == 0.0) return g
        val join = if (round || r != 0.0) BufferParameters.JOIN_ROUND else BufferParameters.JOIN_MITRE
        val params = BufferParameters(
            segments.coerceIn(2, 512),
            join,
            BufferParameters.CAP_ROUND,
            5.0,
        )
        return safe(g) { BufferOp.bufferOp(it, distance, params) }
    }

    fun hull(g: Geometry): Geometry {
        if (g.isEmpty) return empty()
        val hulled = safe(g) { ConvexHull(it).convexHull }
        return polygonal(hulled)
    }

    fun transform(g: Geometry, mat: Mat4): Geometry {
        if (g.isEmpty) return g
        return safe(g) { geom ->
            val copy = geom.copy()
            for (c in copy.coordinates) {
                val p = mat.transform(Vec3(c.x, c.y, 0.0))
                c.x = p.x
                c.y = p.y
            }
            copy.geometryChanged()
            polygonal(copy)
        }
    }

    /**
     * Lifts a 2D region to a closed solid between [z0] and [z1].
     *
     * Caps are triangulated with JTS ear clipping (holes included); walls come
     * from every ring. Shells are emitted outward and holes inward, so the
     * surface winds consistently for both the viewport and STL export.
     */
    fun extrude(g: Geometry, z0: Double, z1: Double): Solid {
        val polygonal = polygonal(g)
        val polys = when (polygonal) {
            is JtsPolygon -> listOf(polygonal)
            is MultiPolygon -> (0 until polygonal.numGeometries).map { polygonal.getGeometryN(it) as JtsPolygon }
            else -> emptyList()
        }
        val out = ArrayList<Polygon>()
        for (p in polys) {
            if (p.isEmpty) continue
            val shell = orient(p.exteriorRing, ccw = true)
            val holes = (0 until p.numInteriorRing).map { orient(p.getInteriorRingN(it), ccw = false) }
            val withHoles = factory.createPolygon(shell, holes.toTypedArray())

            // Caps.
            val tri = try {
                PolygonTriangulator.triangulate(withHoles)
            } catch (t: Throwable) {
                null
            }
            if (tri != null) {
                for (i in 0 until tri.numGeometries) {
                    val t = tri.getGeometryN(i) as? JtsPolygon ?: continue
                    val c = t.exteriorRing.coordinates
                    if (c.size < 4) continue
                    val a = Vec3(c[0].x, c[0].y, 0.0)
                    val b = Vec3(c[1].x, c[1].y, 0.0)
                    val d = Vec3(c[2].x, c[2].y, 0.0)
                    val ccw = signedArea(c[0], c[1], c[2]) >= 0
                    val top = if (ccw) listOf(a, b, d) else listOf(a, d, b)
                    out += Polygon(top.map { Vec3(it.x, it.y, z1) })
                    out += Polygon(top.reversed().map { Vec3(it.x, it.y, z0) })
                }
            }

            // Walls.
            for (ring in listOf(shell) + holes) {
                val cs = ring.coordinates
                for (i in 0 until cs.size - 1) {
                    val a = cs[i]
                    val b = cs[i + 1]
                    if (a.x == b.x && a.y == b.y) continue
                    out += Polygon(
                        listOf(
                            Vec3(a.x, a.y, z0),
                            Vec3(b.x, b.y, z0),
                            Vec3(b.x, b.y, z1),
                            Vec3(a.x, a.y, z1),
                        ),
                    )
                }
            }
        }
        return Solid(out)
    }

    // ---- helpers -------------------------------------------------------------

    private fun orient(ring: LinearRing, ccw: Boolean): LinearRing {
        val coords = ring.coordinates
        val isCcw = Orientation.isCCW(coords)
        if (isCcw == ccw) return ring
        val reversed = coords.reversedArray()
        return factory.createLinearRing(reversed)
    }

    private fun signedArea(a: Coordinate, b: Coordinate, c: Coordinate): Double =
        (b.x - a.x) * (c.y - a.y) - (c.x - a.x) * (b.y - a.y)

    private inline fun safe(g: Geometry, op: (Geometry) -> Geometry): Geometry = try {
        polygonal(op(g))
    } catch (t: Throwable) {
        // Overlay can throw on a self-touching input; nudging with a zero-width
        // buffer repairs it, and the retry is enough for the models we render.
        try {
            polygonal(op(polygonal(g).buffer(0.0)))
        } catch (t2: Throwable) {
            empty()
        }
    }
}
