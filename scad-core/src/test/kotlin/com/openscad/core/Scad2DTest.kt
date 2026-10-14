package com.openscad.core

import kotlin.math.PI
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** 2D kernel + extrusion tests (JTS-backed overlay, offset, hull, triangulation). */
class Scad2DTest {

    private fun eval(source: String): EvalResult = ScadEvaluator().evaluate(source)

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

    private fun assertClose(expected: Double, actual: Double, relTol: Double = 0.02) {
        assertTrue(
            abs(expected - actual) <= relTol * maxOf(1.0, abs(expected)),
            "expected ~$expected but was $actual",
        )
    }

    @Test
    fun `extruded square has the right volume and bounds`() {
        val r = eval("linear_extrude(height = 2) square([10, 4]);")
        val solid = assertNotNull(r.solid)
        assertClose(80.0, volume(solid), 1e-9)
        assertClose(10.0, solid.bounds.size.x, 1e-9)
        assertClose(4.0, solid.bounds.size.y, 1e-9)
        assertClose(2.0, solid.bounds.size.z, 1e-9)
    }

    @Test
    fun `extruded circle approximates pi r squared h`() {
        val r = eval("linear_extrude(height = 3) circle(r = 5, ${'$'}fn = 128);")
        val solid = assertNotNull(r.solid)
        assertClose(PI * 25 * 3, volume(solid), 0.01)
    }

    @Test
    fun `2D difference cuts a hole that survives extrusion`() {
        val r = eval(
            "linear_extrude(height = 2) difference() { circle(r = 5, ${'$'}fn = 128); circle(r = 2, ${'$'}fn = 64); }",
        )
        val solid = assertNotNull(r.solid)
        assertClose(PI * (25 - 4) * 2, volume(solid), 0.02)
        // Disc-with-hole bounds stay the outer radius.
        assertClose(10.0, solid.bounds.size.x, 0.01)
    }

    @Test
    fun `offset expands the region`() {
        val r = eval("linear_extrude(height = 1) offset(r = 1) square([4, 4]);")
        val solid = assertNotNull(r.solid)
        // Sharp square of 4x4 grown by 1mm on every side, rounded corners.
        val expected = 6.0 * 6.0 - (4.0 - PI) // 36 - corner area lost to rounding
        assertClose(expected, volume(solid), 0.03)
    }

    @Test
    fun `hull of two circles spans both`() {
        val r = eval(
            "linear_extrude(height = 1) hull() { circle(r = 1, ${'$'}fn = 32); translate([10, 0]) circle(r = 1, ${'$'}fn = 32); }",
        )
        val solid = assertNotNull(r.solid)
        assertClose(12.0, solid.bounds.size.x, 0.01)
    }

    @Test
    fun `intersection of circles is the lens`() {
        val r = eval(
            "linear_extrude(height = 1) intersection() { circle(r = 5, ${'$'}fn = 128); translate([5, 0]) circle(r = 5, ${'$'}fn = 128); }",
        )
        val solid = assertNotNull(r.solid)
        // Lens from two radius-5 circles 5mm apart: positive but smaller than one disc.
        val v = volume(solid)
        assertTrue(v > 0 && v < PI * 25, "lens volume was $v")
    }

    @Test
    fun `mixed 2D and 3D children of a boolean warn and keep the 3D part`() {
        val r = eval("difference() { cube(4); circle(r = 1); }")
        val solid = assertNotNull(r.solid)
        assertTrue(solid.polygons.isNotEmpty())
        assertTrue(r.stats.warnings.any { it.contains("mixes") }, "warnings were ${r.stats.warnings}")
    }
}
