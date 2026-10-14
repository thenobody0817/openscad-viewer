package com.openscad.core

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ScadGeometryTest {

    /** Signed volume via the divergence theorem; valid for closed, outward-wound meshes. */
    private fun volume(solid: Solid): Double {
        var sum = 0.0
        for (tri in solid.triangles()) {
            val a = tri.vertices[0]
            val b = tri.vertices[1]
            val c = tri.vertices[2]
            sum += a.dot(b.cross(c))
        }
        return abs(sum) / 6.0
    }

    private fun eval(source: String): Solid {
        val result = ScadEvaluator().evaluate(source)
        assertNull(result.error, "unexpected error: ${result.error}")
        return assertNotNull(result.solid)
    }

    private fun assertNull(value: Any?, message: String? = null) =
        assertTrue(value == null, message ?: "expected null but was $value")

    private fun assertClose(expected: Double, actual: Double, tolerance: Double = 1e-6) {
        assertTrue(
            abs(expected - actual) <= tolerance * maxOf(1.0, abs(expected)),
            "expected $expected but was $actual",
        )
    }

    @Test
    fun `cube has six faces and correct volume`() {
        val solid = eval("cube(10);")
        assertEquals(6, solid.polygons.size)
        assertClose(1000.0, volume(solid))
    }

    @Test
    fun `centered cube spans negative to positive`() {
        val solid = eval("cube(10, center = true);")
        val bounds = solid.bounds
        assertClose(-5.0, bounds.min.x)
        assertClose(5.0, bounds.max.z)
    }

    @Test
    fun `difference removes an interior pocket`() {
        // 10-cube with a fully interior 4-cube removed.
        val solid = eval("difference(){ cube(10); translate([3,3,3]) cube(4); }")
        assertClose(1000.0 - 64.0, volume(solid), 1e-4)
    }

    @Test
    fun `union of overlapping cubes does not double count`() {
        val solid = eval("union(){ cube(10); translate([5,0,0]) cube(10); }")
        // Overlap is [5,10] x [0,10] x [0,10] = 500.
        assertClose(1500.0, volume(solid), 1e-4)
    }

    @Test
    fun `intersection of overlapping cubes is the overlap`() {
        val solid = eval("intersection(){ cube(10); translate([5,0,0]) cube(10); }")
        assertClose(500.0, volume(solid), 1e-4)
    }

    @Test
    fun `sphere volume approximates four thirds pi r cubed`() {
        val solid = eval("sphere(r = 10, \$fn = 64);")
        val expected = 4.0 / 3.0 * Math.PI * 1000.0
        assertTrue(volume(solid) in expected * 0.97..expected * 1.0001, "volume was ${volume(solid)}")
    }

    @Test
    fun `cylinder volume approximates pi r squared h`() {
        val solid = eval("cylinder(h = 20, r = 5, \$fn = 64);")
        val expected = Math.PI * 25.0 * 20.0
        assertTrue(volume(solid) in expected * 0.98..expected * 1.0001, "volume was ${volume(solid)}")
    }

    @Test
    fun `transforms compose without breaking volume`() {
        val solid = eval("translate([100,0,0]) rotate([0,0,45]) scale(2) cube(5);")
        // 5-cube scaled by 2 => 10-cube => 1000.
        assertClose(1000.0, volume(solid), 1e-4)
    }
}
