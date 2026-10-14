package com.openscad.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ScadEvaluatorTest {

    private fun result(source: String) = ScadEvaluator().evaluate(source)

    @Test
    fun `for loop emits one solid per iteration`() {
        val r = result("for (i = [0:3]) translate([i*10, 0, 0]) cube(5);")
        assertNotNull(r.solid)
        assertEquals(24, r.solid!!.polygons.size) // 4 cubes x 6 faces
    }

    @Test
    fun `user module can be called repeatedly`() {
        val r = result("module m() { cube(1); } m(); m();")
        assertEquals(12, r.solid!!.polygons.size)
    }

    @Test
    fun `children forwards the caller's geometry`() {
        val r = result(
            """
            module two() { children(0); children(1); }
            two() { cube(1); sphere(r = 1, ${'$'}fn = 8); }
            """.trimIndent(),
        )
        assertNotNull(r.solid)
        assertTrue(r.solid!!.polygons.size > 6, "expected cube + sphere polygons")
    }

    @Test
    fun `module parameters and defaults bind correctly`() {
        val r = result(
            """
            module bar(w, h = 5) { cube([w, w, h]); }
            bar(2);
            bar(3, 4);
            """.trimIndent(),
        )
        assertEquals(12, r.solid!!.polygons.size)
    }

    @Test
    fun `if chooses the correct branch`() {
        val a = result("if (true) cube(2); else cube(9);")
        val b = result("if (false) cube(2); else cube(9);")
        assertEquals(2.0, a.solid!!.bounds.max.x)
        assertEquals(9.0, b.solid!!.bounds.max.x)
    }

    @Test
    fun `arithmetic and variables evaluate`() {
        val r = result("w = 4; h = w * 2 + 1; cube([w, w, h]);")
        assertEquals(9.0, r.solid!!.bounds.max.z)
    }

    @Test
    fun `two-pass top level assignments are visible to modules`() {
        val r = result(
            """
            module box() { cube(size); }
            size = 7;
            box();
            """.trimIndent(),
        )
        assertEquals(7.0, r.solid!!.bounds.max.x)
    }

    @Test
    fun `unsupported modules are reported as warnings not failures`() {
        val r = result("import(\"nope.stl\"); cube(1);")
        assertNotNull(r.solid)
        assertTrue(r.stats.warnings.any { it.contains("import") }, "warnings were ${r.stats.warnings}")
    }

    @Test
    fun `linear_extrude renders a 2D child`() {
        val r = result("linear_extrude(height = 5) circle(r = 3, ${'$'}fn = 32);")
        assertNotNull(r.solid)
        assertTrue(r.solid!!.polygons.isNotEmpty())
        assertTrue(r.stats.warnings.none { it.contains("linear_extrude") }, "warnings were ${r.stats.warnings}")
    }

    @Test
    fun `list comprehension with let and if evaluates`() {
        val r = result(
            """
            squares = [for (i = [0:3]) let(s = i*i) s];
            evens = [for (i = [0:9]) if (i % 2 == 0) i];
            echo(squares, evens);
            cube(1);
            """.trimIndent(),
        )
        val echo = r.stats.echo.joinToString("\n")
        assertTrue(echo.contains("[0, 1, 4, 9]"), "echo was $echo")
        assertTrue(echo.contains("[0, 2, 4, 6, 8]"), "echo was $echo")
    }

    @Test
    fun `echo output is captured`() {
        val r = result("echo(\"hi\", 42); cube(1);")
        assertTrue(r.stats.echo.any { it.contains("hi") && it.contains("42") }, "echo was ${r.stats.echo}")
    }

    @Test
    fun `demo box model renders with a lid`() {
        val r = result(
            """
            // Parametric box
            /* [Box dimensions] */
            box_width = 50;   // [10:5:200]
            box_depth = 30;   // [10:5:200]
            box_height = 20;  // [5:5:100]

            /* [Lid] */
            lid_style = "snap"; // [snap, screw, hinge]
            show_lid = true;

            /* [Hidden] */
            wall = 2;

            module shell(w, d, h) {
                difference() {
                    cube([w, d, h], center = true);
                    translate([0, 0, wall])
                        cube([w - 2*wall, d - 2*wall, h], center = true);
                }
            }

            shell(box_width, box_depth, box_height);
            if (show_lid) translate([0, 0, box_height/2 + 6])
                cube([box_width, box_depth, 3], center = true);
            """.trimIndent(),
        )
        assertNotNull(r.solid, "error: ${r.error}")
        assertTrue(r.solid!!.polygons.size > 20, "expected a hollow shell plus lid")
        val bounds = r.solid!!.bounds
        assertEquals(50.0, bounds.size.x, 0.001)
        assertEquals(30.0, bounds.size.y, 0.001)
    }
}
