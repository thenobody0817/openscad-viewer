package com.openscad.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CustomizerParserTest {

    private val sample = """
        // Parametric box
        /* [Box dimensions] */
        // Width of the box
        box_width = 50;   // [10:5:200]
        // Depth of the box
        box_depth = 30;   // [10:5:200]
        box_height = 20;  // [5:5:100]

        /* [Lid] */
        // Style of the lid
        lid_style = "snap"; // [snap, screw, hinge]
        show_lid = true;

        /* [Hidden] */
        epsilon = 0.01;

        module box(w, d, h) {
            difference() {
                cube([w, d, h], center = true);
                translate([0, 0, epsilon])
                    cube([w-4, d-4, h], center = true);
            }
        }

        box(box_width, box_depth, box_height);
        if (show_lid) translate([0, 0, 30]) box(box_width, box_depth, 4);
    """.trimIndent()

    @Test
    fun `finds the right sections and excludes hidden from visible`() {
        val model = CustomizerParser.parse(sample)
        assertEquals(listOf("Box dimensions", "Lid", "Hidden"), model.sections.map { it.name })
        assertEquals(listOf("Box dimensions", "Lid"), model.visibleSections.map { it.name })
        assertEquals(listOf("epsilon"), model.hiddenParameters.map { it.name })
    }

    @Test
    fun `classifies sliders with range and step`() {
        val p = param("box_width")
        assertEquals(ParamKind.SLIDER, p.kind)
        assertEquals(NumericRange(min = 10.0, step = 5.0, max = 200.0), p.range)
        assertEquals("Width of the box", p.description)
        assertEquals("50", p.defaultValue)
    }

    @Test
    fun `classifies dropdowns and strips quotes from labels`() {
        val p = param("lid_style")
        assertEquals(ParamKind.DROPDOWN, p.kind)
        assertEquals(listOf("snap", "screw", "hinge"), p.choices.map { it.label })
        // A bare enum annotation on a string parameter is a list of *strings*, so
        // the override literal it produces must keep its quotes.
        assertEquals(listOf("\"snap\"", "\"screw\"", "\"hinge\""), p.choices.map { it.value })
        assertTrue(p.choices.all { it.quoted })
    }

    @Test
    fun `classifies booleans as checkboxes`() {
        val p = param("show_lid")
        assertEquals(ParamKind.CHECKBOX, p.kind)
        assertEquals("true", p.defaultValue)
        assertNull(p.description)
    }

    @Test
    fun `hidden parameters are still parsed`() {
        val p = param("epsilon")
        assertEquals(ParamKind.NUMBER, p.kind)
        assertTrue(p.hidden)
    }

    @Test
    fun `does not treat module locals or calls as parameters`() {
        val names = CustomizerParser.parse(sample).allParameters.map { it.name }
        assertEquals(listOf("box_width", "box_depth", "box_height", "lid_style", "show_lid", "epsilon"), names)
    }

    @Test
    fun `line numbers point at the assignment`() {
        assertEquals(4, param("box_width").line)
        assertEquals(11, param("lid_style").line)
    }

    @Test
    fun `blank line breaks description association`() {
        val src = """
            // orphaned comment
            
            x = 1;
        """.trimIndent()
        assertNull(CustomizerParser.parse(src).allParameters.single().description)
    }

    @Test
    fun `ungrouped parameters appear in a null-named section`() {
        val src = "a = 1;\nb = 2;"
        val model = CustomizerParser.parse(src)
        assertEquals(listOf(null), model.sections.map { it.name })
        assertEquals(listOf("a", "b"), model.allParameters.map { it.name })
    }

    @Test
    fun `parses string dropdowns preserving the quoted literal`() {
        val src = "mode = \"a\"; // [\"a\", \"b\"]"
        val p = CustomizerParser.parse(src).allParameters.single()
        assertEquals(ParamKind.DROPDOWN, p.kind)
        assertEquals(listOf("a", "b"), p.choices.map { it.label })
        assertEquals(listOf("\"a\"", "\"b\""), p.choices.map { it.value })
        assertTrue(p.choices.all { it.quoted })
    }

    @Test
    fun `parses bare max annotation as number with override`() {
        val src = "segments = 32; // [256]"
        val p = CustomizerParser.parse(src).allParameters.single()
        assertEquals(ParamKind.NUMBER, p.kind)
        assertEquals(256.0, p.maxOverride)
    }

    @Test
    fun `display name humanises identifiers`() {
        assertEquals("Box width", param("box_width").displayName)
    }

    @Test
    fun `string enum choices serialise as quoted overrides`() {
        val src = "part = \"assembly\"; // [assembly, screen, diffuser]"
        val p = CustomizerParser.parse(src).allParameters.single()
        assertEquals(ParamKind.DROPDOWN, p.kind)
        assertEquals(listOf("assembly", "screen", "diffuser"), p.choices.map { it.label })
        assertEquals(listOf("\"assembly\"", "\"screen\"", "\"diffuser\""), p.choices.map { it.value })
    }

    @Test
    fun `numeric enum choices stay bare`() {
        val p = CustomizerParser.parse("mode = 1; // [1, 2, 3]").allParameters.single()
        assertEquals(listOf("1", "2", "3"), p.choices.map { it.value })
        assertTrue(p.choices.none { it.quoted })
    }

    @Test
    fun `list comprehension internals are not top-level parameters`() {
        val src = """
            n = 3;
            xs = [for (k = [0:n-1])
                let(a = k*2)
                [a, a+1]];
            tail = 5;
        """.trimIndent()
        assertEquals(listOf("n", "tail"), CustomizerParser.parse(src).allParameters.map { it.name })
    }

    private fun param(name: String): CustomizerParameter =
        CustomizerParser.parse(sample).allParameters.first { it.name == name }
}
