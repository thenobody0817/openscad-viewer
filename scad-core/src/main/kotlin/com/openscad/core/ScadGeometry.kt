package com.openscad.core

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/** Immutable 3D vector. */
data class Vec3(val x: Double, val y: Double, val z: Double) {
    operator fun plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
    operator fun times(s: Double) = Vec3(x * s, y * s, z * s)
    operator fun unaryMinus() = Vec3(-x, -y, -z)
    fun dot(o: Vec3) = x * o.x + y * o.y + z * o.z
    fun cross(o: Vec3) = Vec3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)
    fun length() = sqrt(dot(this))
    fun normalized(): Vec3 {
        val l = length()
        return if (l < 1e-12) Vec3(0.0, 0.0, 0.0) else Vec3(x / l, y / l, z / l)
    }
    fun lerp(o: Vec3, t: Double) = Vec3(x + (o.x - x) * t, y + (o.y - y) * t, z + (o.z - z) * t)
}

/** Axis-aligned bounding box. */
data class Bounds(val min: Vec3, val max: Vec3) {
    val size: Vec3 get() = max - min

    companion object {
        fun of(points: Iterable<Vec3>): Bounds {
            var lo = Vec3(Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE)
            var hi = Vec3(-Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE)
            for (p in points) {
                lo = Vec3(minOf(lo.x, p.x), minOf(lo.y, p.y), minOf(lo.z, p.z))
                hi = Vec3(maxOf(hi.x, p.x), maxOf(hi.y, p.y), maxOf(hi.z, p.z))
            }
            return Bounds(lo, hi)
        }
    }
}

/**
 * Row-major 4x4 matrix: element (row, col) lives at `m[row * 4 + col]`.
 *
 * Points are treated as column vectors, so composition `a * b` applies `b`
 * first. This matches `Mat4.rotationZ(a) * Mat4.rotationY(b) * p`.
 */
class Mat4(val m: DoubleArray = identityArray()) {

    companion object {
        fun identityArray() = DoubleArray(16).also {
            it[0] = 1.0; it[5] = 1.0; it[10] = 1.0; it[15] = 1.0
        }

        fun translation(x: Double, y: Double, z: Double) = Mat4().also {
            it.m[3] = x; it.m[7] = y; it.m[11] = z
        }

        fun scaling(x: Double, y: Double, z: Double) = Mat4().also {
            it.m[0] = x; it.m[5] = y; it.m[10] = z
        }

        fun rotationX(a: Double): Mat4 {
            val c = cos(a); val s = sin(a)
            return Mat4().also {
                it.m[5] = c; it.m[6] = -s
                it.m[9] = s; it.m[10] = c
            }
        }

        fun rotationY(a: Double): Mat4 {
            val c = cos(a); val s = sin(a)
            return Mat4().also {
                it.m[0] = c; it.m[2] = s
                it.m[8] = -s; it.m[10] = c
            }
        }

        fun rotationZ(a: Double): Mat4 {
            val c = cos(a); val s = sin(a)
            return Mat4().also {
                it.m[0] = c; it.m[1] = -s
                it.m[4] = s; it.m[5] = c
            }
        }
    }

    operator fun times(o: Mat4): Mat4 {
        val r = DoubleArray(16)
        for (i in 0..3) {
            for (j in 0..3) {
                var sum = 0.0
                for (k in 0..3) sum += m[i * 4 + k] * o.m[k * 4 + j]
                r[i * 4 + j] = sum
            }
        }
        return Mat4(r)
    }

    fun transform(p: Vec3): Vec3 {
        val x = m[0] * p.x + m[1] * p.y + m[2] * p.z + m[3]
        val y = m[4] * p.x + m[5] * p.y + m[6] * p.z + m[7]
        val z = m[8] * p.x + m[9] * p.y + m[10] * p.z + m[11]
        val w = m[12] * p.x + m[13] * p.y + m[14] * p.z + m[15]
        return if (w != 0.0 && w != 1.0) Vec3(x / w, y / w, z / w) else Vec3(x, y, z)
    }

    /** Transforms a direction (ignores translation). */
    fun transformDirection(v: Vec3): Vec3 {
        val x = m[0] * v.x + m[1] * v.y + m[2] * v.z
        val y = m[4] * v.x + m[5] * v.y + m[6] * v.z
        val z = m[8] * v.x + m[9] * v.y + m[10] * v.z
        return Vec3(x, y, z)
    }
}

/** A convex, planar face. Winding is counter-clockwise when seen from outside. */
data class Polygon(val vertices: List<Vec3>) {
    val normal: Vec3
    val w: Double

    init {
        val n = (vertices[1] - vertices[0]).cross(vertices[2] - vertices[1])
        val unit = n.normalized()
        normal = unit
        w = unit.dot(vertices[0])
    }

    fun flipped() = Polygon(vertices.reversed())

    fun transformed(mat: Mat4) = Polygon(vertices.map(mat::transform))
}

/** A boundary representation built from convex polygons. */
class Solid(val polygons: List<Polygon>) {

    val isEmpty: Boolean get() = polygons.isEmpty()

    val bounds: Bounds
        get() = Bounds.of(polygons.flatMap { it.vertices })

    fun transformed(mat: Mat4) = Solid(polygons.map { it.transformed(mat) })

    /** Flattens every polygon into triangles for rendering. */
    fun triangles(): List<Polygon> {
        val out = ArrayList<Polygon>()
        for (poly in polygons) {
            for (i in 1 until poly.vertices.size - 1) {
                out += Polygon(listOf(poly.vertices[0], poly.vertices[i], poly.vertices[i + 1]))
            }
        }
        return out
    }

    companion object {
        val EMPTY = Solid(emptyList())

        fun union(solids: List<Solid>) = Solid(solids.flatMap { it.polygons })
    }
}

/** Primitive solid constructors honouring OpenSCAD's `$fn` / `$fa` / `$fs`. */
object Primitives {

    /**
     * Circle/rotation segment count. `$fn` wins when >= 3, otherwise the count is
     * derived from `$fa` (minimum angle) and `$fs` (minimum size).
     */
    fun fragments(radius: Double, fn: Double, fs: Double, fa: Double): Int {
        if (fn >= 3.0) return fn.toInt()
        val effectiveFs = if (fs <= 0.0) 2.0 else fs
        val effectiveFa = if (fa <= 0.0) 12.0 else fa
        val byAngle = 360.0 / effectiveFa
        val bySize = if (radius <= 0.0) 0.0 else (2 * PI * radius) / effectiveFs
        return max(max(byAngle, bySize), 5.0).toInt().coerceIn(5, 360)
    }

    fun cube(size: Vec3, center: Boolean): Solid {
        val ox = if (center) -size.x / 2 else 0.0
        val oy = if (center) -size.y / 2 else 0.0
        val oz = if (center) -size.z / 2 else 0.0
        val x = size.x; val y = size.y; val z = size.z
        fun p(a: Double, b: Double, c: Double) = Vec3(ox + a, oy + b, oz + c)

        val p000 = p(0.0, 0.0, 0.0); val p100 = p(x, 0.0, 0.0)
        val p110 = p(x, y, 0.0); val p010 = p(0.0, y, 0.0)
        val p001 = p(0.0, 0.0, z); val p101 = p(x, 0.0, z)
        val p111 = p(x, y, z); val p011 = p(0.0, y, z)

        return Solid(
            listOf(
                Polygon(listOf(p000, p010, p110, p100)), // -z
                Polygon(listOf(p001, p101, p111, p011)), // +z
                Polygon(listOf(p000, p100, p101, p001)), // -y
                Polygon(listOf(p010, p011, p111, p110)), // +y
                Polygon(listOf(p000, p001, p011, p010)), // -x
                Polygon(listOf(p100, p110, p111, p101)), // +x
            ),
        )
    }

    fun sphere(radius: Double, fn: Double, fs: Double, fa: Double): Solid {
        val segments = fragments(radius, fn, fs, fa)
        val rings = max(2, segments / 2)
        val polys = ArrayList<Polygon>()

        fun point(ring: Int, seg: Int): Vec3 {
            val phi = PI * ring / rings
            val theta = 2 * PI * seg / segments
            return Vec3(
                radius * sin(phi) * cos(theta),
                radius * sin(phi) * sin(theta),
                radius * cos(phi),
            )
        }

        for (r in 0 until rings) {
            for (s in 0 until segments) {
                val s1 = (s + 1) % segments
                val a = point(r, s)
                val b = point(r + 1, s)
                val c = point(r + 1, s1)
                val d = point(r, s1)
                when {
                    r == 0 -> polys += Polygon(listOf(a, c, d))        // top cap
                    r == rings - 1 -> polys += Polygon(listOf(a, b, d)) // bottom cap
                    else -> {
                        polys += Polygon(listOf(a, b, c))
                        polys += Polygon(listOf(a, c, d))
                    }
                }
            }
        }
        return Solid(polys)
    }

    fun cylinder(
        height: Double,
        r1: Double,
        r2: Double,
        center: Boolean,
        fn: Double,
        fs: Double,
        fa: Double,
    ): Solid {
        val segments = fragments(max(r1, r2), fn, fs, fa)
        val z0 = if (center) -height / 2 else 0.0
        val z1 = z0 + height
        val polys = ArrayList<Polygon>()

        fun ringPoint(r: Double, z: Double, i: Int) = Vec3(
            r * cos(2 * PI * i / segments),
            r * sin(2 * PI * i / segments),
            z,
        )

        for (i in 0 until segments) {
            val j = (i + 1) % segments
            val b0 = ringPoint(r1, z0, i)
            val b1 = ringPoint(r1, z0, j)
            val t0 = ringPoint(r2, z1, i)
            val t1 = ringPoint(r2, z1, j)
            when {
                r1 <= 0.0 && r2 > 0.0 -> polys += Polygon(listOf(b0, t1, t0)) // apex at bottom
                r2 <= 0.0 && r1 > 0.0 -> polys += Polygon(listOf(b0, b1, t0)) // apex at top
                r1 > 0.0 && r2 > 0.0 -> {
                    polys += Polygon(listOf(b0, b1, t1))
                    polys += Polygon(listOf(b0, t1, t0))
                }
            }
        }

        if (r1 > 0.0) {
            val c = Vec3(0.0, 0.0, z0)
            for (i in 0 until segments) {
                val j = (i + 1) % segments
                polys += Polygon(listOf(c, ringPoint(r1, z0, j), ringPoint(r1, z0, i)))
            }
        }
        if (r2 > 0.0) {
            val c = Vec3(0.0, 0.0, z1)
            for (i in 0 until segments) {
                val j = (i + 1) % segments
                polys += Polygon(listOf(c, ringPoint(r2, z1, i), ringPoint(r2, z1, j)))
            }
        }
        return Solid(polys)
    }

    fun polyhedron(points: List<Vec3>, faces: List<List<Int>>): Solid {
        val polys = ArrayList<Polygon>()
        for (face in faces) {
            // OpenSCAD lists faces clockwise seen from outside; reverse for outward normals.
            val verts = face.reversed().mapNotNull { points.getOrNull(it) }
            if (verts.size >= 3) polys += Polygon(verts)
        }
        return Solid(polys)
    }
}
