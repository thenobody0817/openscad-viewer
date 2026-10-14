package com.openscad.core

/**
 * Parses the OpenSCAD **Customizer** metadata out of a source file.
 *
 * The Customizer is a *comment convention*, not part of the language grammar, so
 * this works line-by-line rather than over an AST:
 *
 *  - A block comment whose payload is `[Group name]` starts a section;
 *    `[Hidden]` marks it hidden.
 *  - A run of `//` description lines immediately above an assignment becomes that
 *    parameter's description (a blank line or code breaks the association).
 *  - A trailing `// [...]` comment on the assignment is the parameter annotation:
 *      - `// [min:max]` / `// [min:step:max]`  → slider
 *      - `// [a, b, c]`                        → dropdown
 *      - `// ["a", "b"]`                       → dropdown of strings
 *      - `// [max]`                            → number field with an upper bound
 *  - Only **top-level** assignments (brace depth 0) are parameters; assignments
 *    inside `module`/`function` bodies are local and ignored.
 *
 * The parser is tolerant: anything it cannot classify still surfaces as a
 * parameter with [ParamKind.UNKNOWN] or [ParamKind.NUMBER] rather than being lost.
 */
object CustomizerParser {

    private val ASSIGN = Regex("""^[ \t]*([A-Za-z_][A-Za-z0-9_]*)[ \t]*=[ \t]*(.*)$""")
    private val NUMBER = Regex("""^[+-]?(\d+\.?\d*|\.\d+)([eE][+-]?\d+)?$""")
    private val PURE_COMMENT = Regex("""^[ \t]*//(.*)$""")
    private val GROUP = Regex("""^[ \t]*/\*[ \t]*\[(.*?)\][ \t]*\*/[ \t]*$""")

    fun parse(source: String): CustomizerModel {
        val lines = source.split("\n")
        val sections = ArrayList<MutableSection>()
        var current: MutableSection? = null
        val pendingDescription = ArrayList<String>()
        var groupDepth = 0
        var inBlockComment = false

        fun openSection(name: String?): MutableSection {
            val hidden = name != null && name.equals("Hidden", ignoreCase = true)
            val section = MutableSection(if (name.isNullOrBlank()) null else name, hidden)
            sections.add(section)
            return section
        }

        for ((index, raw) in lines.withIndex()) {
            val lineNo = index + 1

            // 1) Section header (its own line, by convention).
            val groupMatch = GROUP.matchEntire(raw)
            if (groupMatch != null) {
                current = openSection(groupMatch.groupValues[1].trim())
                pendingDescription.clear()
                inBlockComment = updateBlockCommentState(raw, inBlockComment)
                continue
            }

            // 2) Blank line: breaks description association.
            if (raw.isBlank()) {
                pendingDescription.clear()
                inBlockComment = updateBlockCommentState(raw, inBlockComment)
                continue
            }

            // 3) Pure comment line: potential description.
            val commentMatch = PURE_COMMENT.matchEntire(raw)
            if (commentMatch != null) {
                val text = commentMatch.groupValues[1].trim()
                if (text.isNotEmpty() && !isBracketAnnotation(text)) {
                    pendingDescription.add(text)
                }
                inBlockComment = updateBlockCommentState(raw, inBlockComment)
                continue
            }

            // 4) Top-level assignment: a parameter.
            val assignMatch = ASSIGN.matchEntire(raw)
            if (assignMatch != null && groupDepth == 0) {
                val name = assignMatch.groupValues[1]
                val rest = assignMatch.groupValues[2]
                val (code, trailing) = splitCodeAndTrailingComment(rest)
                val valueText = code.trim().removeSuffix(";").trim()
                // A value whose brackets do not close on this line is the start of a
                // multi-line construct (e.g. a list comprehension). It is not a
                // Customizer parameter we can safely re-emit, so skip it.
                if (valueText.isNotEmpty() && isBalanced(valueText)) {
                    val section = current ?: openSection(null).also { current = it }
                    section.parameters += buildParameter(
                        name = name,
                        valueText = valueText,
                        trailingComment = trailing,
                        descriptionLines = pendingDescription.toList(),
                        lineNo = lineNo,
                        section = section,
                    )
                }
                pendingDescription.clear()
                groupDepth += groupDelta(raw, inBlockComment)
                inBlockComment = updateBlockCommentState(raw, inBlockComment)
                continue
            }

            // 5) Any other code line: track braces and drop stale descriptions.
            groupDepth += groupDelta(raw, inBlockComment)
            inBlockComment = updateBlockCommentState(raw, inBlockComment)
            pendingDescription.clear()
        }

        return CustomizerModel(
            sections = sections
                .filter { it.parameters.isNotEmpty() }
                .map { CustomizerSection(it.name, it.hidden, it.parameters) },
        )
    }

    // ---- parameter construction -------------------------------------------------

    private fun buildParameter(
        name: String,
        valueText: String,
        trailingComment: String?,
        descriptionLines: List<String>,
        lineNo: Int,
        section: MutableSection,
    ): CustomizerParameter {
        val description = descriptionLines.joinToString(" ").trim().ifEmpty { null }

        var range: NumericRange? = null
        var maxOverride: Double? = null
        val choices = ArrayList<Choice>()

        val bracket = extractBracket(trailingComment)
        // OpenSCAD treats an unquoted enum annotation on a string parameter as a
        // list of strings: `part = "assembly"; // [assembly, screen]`. The
        // override we later re-emit must therefore be a string literal
        // (`part = "screen";`), otherwise the evaluator resolves the bare word as
        // an undefined identifier and the selection silently falls through.
        val stringParam = isQuoted(valueText)
        if (bracket != null) {
            val parts = splitTopLevel(bracket, ',')
            if (parts.size > 1) {
                for (part in parts) {
                    val token = part.trim()
                    val quoted = token.length >= 2 && token.startsWith("\"") && token.endsWith("\"")
                    val label = if (quoted) token.substring(1, token.length - 1) else token
                    val value = when {
                        quoted -> token
                        stringParam -> quoteString(label)
                        else -> token
                    }
                    choices += Choice(label = label, value = value, quoted = quoted || stringParam)
                }
            } else if (parts.size == 1) {
                val single = parts[0].trim()
                val colonSegments = single.split(":").map { it.trim() }
                if (colonSegments.size in 2..3) {
                    val nums = colonSegments.map { it.toDoubleOrNull() }
                    if (nums.all { it != null }) {
                        range = when (colonSegments.size) {
                            2 -> NumericRange(min = nums[0]!!, step = 1.0, max = nums[1]!!)
                            else -> NumericRange(min = nums[0]!!, step = nums[1]!!, max = nums[2]!!)
                        }
                    }
                } else {
                    maxOverride = single.toDoubleOrNull()
                }
            }
        }

        val kind = classify(valueText, range, choices)

        return CustomizerParameter(
            name = name,
            kind = kind,
            defaultValue = valueText,
            description = description,
            range = range,
            choices = choices,
            maxOverride = maxOverride,
            line = lineNo,
            section = section.name,
            hidden = section.hidden,
        )
    }

    private fun classify(
        valueText: String,
        range: NumericRange?,
        choices: List<Choice>,
    ): ParamKind = when {
        choices.isNotEmpty() -> ParamKind.DROPDOWN
        range != null -> ParamKind.SLIDER
        valueText == "true" || valueText == "false" -> ParamKind.CHECKBOX
        isQuoted(valueText) -> ParamKind.TEXT
        NUMBER.matches(valueText) -> ParamKind.NUMBER
        valueText.startsWith("[") && valueText.endsWith("]") -> ParamKind.VECTOR
        else -> ParamKind.UNKNOWN
    }

    // ---- low-level scanning helpers --------------------------------------------

    /**
     * Splits `rest` (the text after `name =`) into the code portion up to and
     * including the terminating `;` at bracket depth 0, and the trailing `//`
     * comment text (without the `//`) if one follows.
     */
    private fun splitCodeAndTrailingComment(rest: String): Pair<String, String?> {
        var depth = 0
        var inString = false
        var i = 0
        var semicolon = -1
        while (i < rest.length) {
            val c = rest[i]
            when {
                inString -> {
                    if (c == '\\') { i += 2; continue }
                    if (c == '"') inString = false
                }
                c == '"' -> inString = true
                c == '[' || c == '(' || c == '{' -> depth++
                c == ']' || c == ')' || c == '}' -> depth--
                c == ';' && depth == 0 -> { semicolon = i }
            }
            if (semicolon >= 0) break
            i++
        }

        if (semicolon < 0) return rest to null

        val code = rest.substring(0, semicolon + 1)
        val after = rest.substring(semicolon + 1).trim()
        val trailing = if (after.startsWith("//")) after.removePrefix("//").trim() else null
        return code to trailing
    }

    /** Extracts the payload of a leading `[ ... ]` annotation, or null. */
    private fun extractBracket(comment: String?): String? {
        if (comment == null) return null
        val trimmed = comment.trim()
        if (!trimmed.startsWith("[")) return null
        val close = trimmed.lastIndexOf(']')
        if (close <= 0) return null
        return trimmed.substring(1, close)
    }

    /** True when a comment is only a `[...]` annotation (so not a description). */
    private fun isBracketAnnotation(text: String): Boolean {
        val t = text.trim()
        return t.startsWith("[") && t.endsWith("]")
    }

    /** Splits on [separator] at depth 0 and outside strings. */
    private fun splitTopLevel(text: String, separator: Char): List<String> {
        val parts = ArrayList<String>()
        val sb = StringBuilder()
        var depth = 0
        var inString = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                inString -> {
                    if (c == '\\' && i + 1 < text.length) { sb.append(c).append(text[i + 1]); i += 2; continue }
                    if (c == '"') inString = false
                    sb.append(c)
                }
                c == '"' -> { inString = true; sb.append(c) }
                c == '[' || c == '(' || c == '{' -> { depth++; sb.append(c) }
                c == ']' || c == ')' || c == '}' -> { depth--; sb.append(c) }
                c == separator && depth == 0 -> { parts.add(sb.toString()); sb.clear() }
                else -> sb.append(c)
            }
            i++
        }
        parts.add(sb.toString())
        return parts
    }

    private fun isQuoted(text: String): Boolean =
        text.length >= 2 && text.startsWith("\"") && text.endsWith("\"")

    /** Renders [text] as an OpenSCAD double-quoted string literal. */
    private fun quoteString(text: String): String =
        "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    /**
     * Net group (`(`, `[`, `{` vs their closers) change contributed by a code
     * line, ignoring strings, line comments and block comments (with carry-in
     * [inBlockComment]). Bracket depth matters as well as brace depth: a
     * list-comprehension body is written as `name = [ ... ]` and its inner
     * `k = v,` lines are not top-level parameters.
     */
    private fun groupDelta(line: String, inBlockComment: Boolean): Int {
        var depthComment = inBlockComment
        var inString = false
        var delta = 0
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                depthComment -> {
                    if (c == '*' && i + 1 < line.length && line[i + 1] == '/') { depthComment = false; i++ }
                }
                inString -> {
                    if (c == '\\') { i++ }
                    else if (c == '"') inString = false
                }
                c == '/' && i + 1 < line.length && line[i + 1] == '/' -> return delta
                c == '/' && i + 1 < line.length && line[i + 1] == '*' -> { depthComment = true; i++ }
                c == '"' -> inString = true
                c == '(' || c == '[' || c == '{' -> delta++
                c == ')' || c == ']' || c == '}' -> delta--
            }
            i++
        }
        return delta
    }

    /** True when every group opened in [text] is closed within it. */
    private fun isBalanced(text: String): Boolean {
        var depth = 0
        var inString = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                inString -> {
                    if (c == '\\') { i++ } else if (c == '"') inString = false
                }
                c == '"' -> inString = true
                c == '(' || c == '[' || c == '{' -> depth++
                c == ')' || c == ']' || c == '}' -> {
                    depth--
                    if (depth < 0) return false
                }
            }
            i++
        }
        return depth == 0
    }

    private fun updateBlockCommentState(line: String, inBlockComment: Boolean): Boolean {
        var state = inBlockComment
        var inString = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                state -> if (c == '*' && i + 1 < line.length && line[i + 1] == '/') { state = false; i++ }
                inString -> {
                    if (c == '\\') { i++ } else if (c == '"') inString = false
                }
                c == '/' && i + 1 < line.length && line[i + 1] == '/' -> return state
                c == '/' && i + 1 < line.length && line[i + 1] == '*' -> { state = true; i++ }
                c == '"' -> inString = true
            }
            i++
        }
        return state
    }

    private class MutableSection(val name: String?, val hidden: Boolean) {
        val parameters = ArrayList<CustomizerParameter>()
    }
}
