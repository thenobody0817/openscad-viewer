package com.openscad.core

/**
 * Constructive Solid Geometry over triangle/polygon soups, using the classic
 * BSP-tree algorithm (as popularised by csg.js).
 *
 * This is what makes `difference()` and `intersection()` actually work without
 * pulling in Manifold at runtime. It is exact intersection of half-spaces, not a
 * mesh boolean, so coplanar faces are handled robustly, but meshes must be
 * closed and consistently wound (which [Primitives] guarantees).
 */
object Csg {

    private const val EPS = 1e-5

    fun union(a: Solid, b: Solid): Solid {
        if (a.isEmpty) return b
        if (b.isEmpty) return a
        val na = Node(a.polygons)
        val nb = Node(b.polygons)
        na.clipTo(nb)
        nb.clipTo(na)
        nb.invert()
        nb.clipTo(na)
        nb.invert()
        na.build(nb.allPolygons())
        return Solid(na.allPolygons())
    }

    /**
     * Unions many solids, taking the expensive BSP path only where bounding boxes
     * overlap. Disjoint parts are simply concatenated, which keeps big `for`
     * loops (hundreds of separate pieces) fast.
     */
    fun unionAll(solids: List<Solid>): Solid {
        val parts = solids.filter { !it.isEmpty }
        if (parts.isEmpty()) return Solid.EMPTY
        var acc = parts.first()
        var accBounds = acc.bounds
        for (i in 1 until parts.size) {
            val next = parts[i]
            val nextBounds = next.bounds
            acc = if (boundsOverlap(accBounds, nextBounds)) union(acc, next)
            else Solid(acc.polygons + next.polygons)
            accBounds = Bounds(
                Vec3(
                    minOf(accBounds.min.x, nextBounds.min.x),
                    minOf(accBounds.min.y, nextBounds.min.y),
                    minOf(accBounds.min.z, nextBounds.min.z),
                ),
                Vec3(
                    maxOf(accBounds.max.x, nextBounds.max.x),
                    maxOf(accBounds.max.y, nextBounds.max.y),
                    maxOf(accBounds.max.z, nextBounds.max.z),
                ),
            )
        }
        return acc
    }

    private fun boundsOverlap(a: Bounds, b: Bounds): Boolean =
        a.min.x <= b.max.x && b.min.x <= a.max.x &&
            a.min.y <= b.max.y && b.min.y <= a.max.y &&
            a.min.z <= b.max.z && b.min.z <= a.max.z

    fun subtract(a: Solid, b: Solid): Solid {
        if (a.isEmpty || b.isEmpty) return a
        val na = Node(a.polygons)
        val nb = Node(b.polygons)
        na.invert()
        na.clipTo(nb)
        nb.clipTo(na)
        nb.invert()
        nb.clipTo(na)
        nb.invert()
        na.build(nb.allPolygons())
        na.invert()
        return Solid(na.allPolygons())
    }

    fun intersect(a: Solid, b: Solid): Solid {
        if (a.isEmpty || b.isEmpty) return Solid.EMPTY
        val na = Node(a.polygons)
        val nb = Node(b.polygons)
        na.invert()
        nb.clipTo(na)
        nb.invert()
        na.clipTo(nb)
        nb.clipTo(na)
        na.build(nb.allPolygons())
        na.invert()
        return Solid(na.allPolygons())
    }

    /** Cuts `solid` with a half-space, keeping the part where `normal·p <= w`. */
    fun clip(solid: Solid, normal: Vec3, w: Double): Solid {
        val node = Node(solid.polygons)
        node.clipTo(Plane(normal, w).toNode())
        return Solid(node.allPolygons())
    }

    private class Plane(val normal: Vec3, val w: Double) {
        fun flip() = Plane(-normal, -w)

        fun toNode(): Node {
            val n = Node()
            n.plane = this
            return n
        }

        fun splitPolygon(
            polygon: Polygon,
            coplanarFront: MutableList<Polygon>,
            coplanarBack: MutableList<Polygon>,
            front: MutableList<Polygon>,
            back: MutableList<Polygon>,
        ) {
            var polygonType = 0
            val types = IntArray(polygon.vertices.size)
            for (i in polygon.vertices.indices) {
                val t = normal.dot(polygon.vertices[i]) - w
                val type = if (t < -EPS) BACK else if (t > EPS) FRONT else COPLANAR
                polygonType = polygonType or type
                types[i] = type
            }

            when (polygonType) {
                COPLANAR ->
                    if (normal.dot(polygon.normal) > 0) coplanarFront += polygon else coplanarBack += polygon
                FRONT -> front += polygon
                BACK -> back += polygon
                else -> {
                    val f = ArrayList<Vec3>()
                    val b = ArrayList<Vec3>()
                    val n = polygon.vertices.size
                    for (i in 0 until n) {
                        val j = (i + 1) % n
                        val ti = types[i]
                        val tj = types[j]
                        val vi = polygon.vertices[i]
                        val vj = polygon.vertices[j]
                        if (ti != BACK) f += vi
                        if (ti != FRONT) b += vi
                        if ((ti or tj) == SPANNING) {
                            val denom = normal.dot(vj - vi)
                            val t = if (denom == 0.0) 0.0 else (w - normal.dot(vi)) / denom
                            val v = vi.lerp(vj, t)
                            f += v
                            b += v
                        }
                    }
                    if (f.size >= 3) front += Polygon(f)
                    if (b.size >= 3) back += Polygon(b)
                }
            }
        }

        companion object {
            const val COPLANAR = 0
            const val FRONT = 1
            const val BACK = 2
            const val SPANNING = 3
        }
    }

    private class Node(polygons: List<Polygon>? = null) {
        var plane: Plane? = null
        var front: Node? = null
        var back: Node? = null
        val polygons = ArrayList<Polygon>()

        init {
            if (polygons != null) build(polygons)
        }

        fun invert() {
            for (i in polygons.indices) polygons[i] = polygons[i].flipped()
            plane = plane?.flip()
            val temp = front
            front = back
            back = temp
            front?.invert()
            back?.invert()
        }

        fun clipPolygons(input: List<Polygon>): List<Polygon> {
            val pl = plane ?: return input.toList()
            var frontList = ArrayList<Polygon>()
            var backList = ArrayList<Polygon>()
            for (polygon in input) {
                pl.splitPolygon(polygon, frontList, backList, frontList, backList)
            }
            front?.let { frontList = ArrayList(it.clipPolygons(frontList)) }
            backList = back?.let { ArrayList(it.clipPolygons(backList)) } ?: ArrayList()
            return frontList + backList
        }

        fun clipTo(bsp: Node) {
            val clipped = bsp.clipPolygons(polygons)
            polygons.clear()
            polygons += clipped
            front?.clipTo(bsp)
            back?.clipTo(bsp)
        }

        fun allPolygons(): List<Polygon> {
            val result = ArrayList<Polygon>(polygons)
            front?.let { result += it.allPolygons() }
            back?.let { result += it.allPolygons() }
            return result
        }

        fun build(input: List<Polygon>) {
            if (input.isEmpty()) return
            if (plane == null) plane = Plane(input[0].normal, input[0].w)
            val pl = plane!!
            val frontList = ArrayList<Polygon>()
            val backList = ArrayList<Polygon>()
            for (polygon in input) {
                pl.splitPolygon(polygon, polygons, polygons, frontList, backList)
            }
            if (frontList.isNotEmpty()) {
                if (front == null) front = Node()
                front!!.build(frontList)
            }
            if (backList.isNotEmpty()) {
                if (back == null) back = Node()
                back!!.build(backList)
            }
        }
    }
}
