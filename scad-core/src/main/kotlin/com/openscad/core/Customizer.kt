package com.openscad.core

/** How the Customizer UI should present a parameter. */
enum class ParamKind {
    /** Bounded numeric with `[min:max]` or `[min:step:max]` — render a slider. */
    SLIDER,

    /** Enumerated `[a, b, c]` — render a dropdown. */
    DROPDOWN,

    /** `true`/`false` — render a switch. */
    CHECKBOX,

    /** Unbounded (or `[max]`-bounded) numeric — render a number field. */
    NUMBER,

    /** Quoted string — render a text field. */
    TEXT,

    /** `[a, b, c]` value — render N number fields. */
    VECTOR,

    /** Could not be classified; render a read-only value. */
    UNKNOWN,
}

/**
 * One option of a [ParamKind.DROPDOWN].
 *
 * [value] is the literal to substitute (quotes included for strings), while
 * [label] is the display text (quotes stripped).
 */
data class Choice(
    val label: String,
    val value: String,
    val quoted: Boolean,
)

/** Inclusive bounds and step for [ParamKind.SLIDER]. */
data class NumericRange(
    val min: Double,
    val step: Double,
    val max: Double,
) {
    init {
        require(step > 0.0) { "step must be > 0" }
        require(max >= min) { "max must be >= min" }
    }

    /** Number of discrete stops, matching Material Slider's `steps` semantics. */
    val steps: Int get() = ((max - min) / step).toInt().coerceAtLeast(0)
}

/**
 * A single top-level assignment discovered by [CustomizerParser].
 */
data class CustomizerParameter(
    val name: String,
    val kind: ParamKind,
    /** Raw literal text of the default, e.g. `50`, `"snap"`, `[1,2,3]`, `true`. */
    val defaultValue: String,
    /** Description text harvested from the `//` lines immediately above. */
    val description: String? = null,
    val range: NumericRange? = null,
    val choices: List<Choice> = emptyList(),
    /** Upper bound from a bare `// [max]` annotation, if any. */
    val maxOverride: Double? = null,
    /** 1-based line number of the assignment, for editor navigation. */
    val line: Int = 0,
    /** Containing section name, or null for ungrouped top-level parameters. */
    val section: String? = null,
    val hidden: Boolean = false,
) {
    /** Human-friendly label derived from the identifier, e.g. `box_width` → `Box width`. */
    val displayName: String
        get() = name.replace('_', ' ').trim().replaceFirstChar(Char::uppercase)
}

/** A Customizer group: a `[Name]` section header and the parameters that follow it. */
data class CustomizerSection(
    /** null means "parameters that appeared before any group header". */
    val name: String?,
    val hidden: Boolean,
    val parameters: List<CustomizerParameter>,
)

/** The whole parsed Customizer surface for a source file. */
data class CustomizerModel(
    val sections: List<CustomizerSection>,
) {
    /** Every parameter, including hidden ones. */
    val allParameters: List<CustomizerParameter> get() = sections.flatMap { it.parameters }

    /** Sections that should be rendered (non-hidden and non-empty). */
    val visibleSections: List<CustomizerSection>
        get() = sections.filter { !it.hidden && it.parameters.isNotEmpty() }

    val hiddenParameters: List<CustomizerParameter>
        get() = sections.filter { it.hidden }.flatMap { it.parameters }

    val isEmpty: Boolean get() = visibleSections.isEmpty()
}
