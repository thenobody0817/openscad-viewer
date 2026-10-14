package com.openscad.viewer.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openscad.core.CustomizerModel
import com.openscad.core.CustomizerParser
import com.openscad.core.CustomizerParameter
import com.openscad.core.EvalStats
import com.openscad.core.ScadEvaluator
import com.openscad.viewer.render.RenderMesh
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.MessageDigest

/** Lifecycle of the geometry pipeline. */
sealed interface RenderState {
    data object Idle : RenderState
    data object Loading : RenderState

    /**
     * A successful evaluation. [key] identifies the exact source + effective
     * parameters that produced this mesh, so an identical request can be served
     * from the current preview instead of re-rendering.
     */
    data class Ready(val mesh: RenderMesh, val stats: EvalStats, val key: String) : RenderState

    /** The newest request failed; the viewport must not show older geometry. */
    data class Failed(val message: String, val key: String) : RenderState
}

/**
 * Holds the working document and drives it through the OpenSCAD pipeline:
 * Customizer metadata for the UI, and full evaluation to a render mesh.
 *
 * Standard-library OpenSCAD overrides customizer values with `-D name=value`.
 * We do the equivalent by appending only the changed parameters as override
 * assignments, which the evaluator's top-level pass applies last so they win.
 */
class WorkspaceViewModel : ViewModel() {

    private val _fileName = MutableStateFlow("parametric_box.scad")
    val fileName: StateFlow<String> = _fileName.asStateFlow()

    private val _source = MutableStateFlow("")
    val source: StateFlow<String> = _source.asStateFlow()

    private val _model = MutableStateFlow(CustomizerModel(emptyList()))
    val model: StateFlow<CustomizerModel> = _model.asStateFlow()

    private val _values = MutableStateFlow<Map<String, String>>(emptyMap())
    val values: StateFlow<Map<String, String>> = _values.asStateFlow()

    private val _console = MutableStateFlow<List<ConsoleLine>>(emptyList())
    val console: StateFlow<List<ConsoleLine>> = _console.asStateFlow()

    private val _render = MutableStateFlow<RenderState>(RenderState.Idle)
    val render: StateFlow<RenderState> = _render.asStateFlow()

    private var renderJob: Job? = null

    /** Monotonic id of the newest submitted request; older jobs may not publish. */
    private var latestRequestId = 0L

    fun setSource(text: String, fileName: String = _fileName.value) {
        _fileName.value = fileName
        _source.value = text

        val parsed = CustomizerParser.parse(text)
        _model.value = parsed
        _values.value = parsed.allParameters.associate { it.name to it.defaultValue }

        scheduleRender()
    }

    fun setValue(name: String, value: String) {
        _values.value = _values.value + (name to value)
        scheduleRender()
    }

    fun resetValue(parameter: CustomizerParameter) {
        setValue(parameter.name, parameter.defaultValue)
    }

    /** Forces a fresh evaluation, e.g. from the ▶ Render button. */
    fun renderNow() = scheduleRender(force = true)

    private fun scheduleRender(force: Boolean = false) {
        val source = _source.value
        if (source.isBlank()) return

        val overrides = effectiveOverrides()
        val key = renderKey(source, overrides)

        // Nothing changed since the preview was produced: keep it (cached render).
        val shown = _render.value
        if (!force && shown is RenderState.Ready && shown.key == key) return

        val requestId = ++latestRequestId
        renderJob?.cancel()
        renderJob = viewModelScope.launch {
            _render.value = RenderState.Loading
            delay(120) // coalesce rapid parameter changes

            val augmented = withOverrides(source, overrides)
            val result = withContext(Dispatchers.Default) { ScadEvaluator().evaluate(augmented) }

            // A newer selector change superseded this request while it ran.
            if (requestId != latestRequestId) return@launch

            val solid = result.solid
            if (result.ok && solid != null && !solid.isEmpty) {
                val mesh = withContext(Dispatchers.Default) { RenderMesh.from(solid) }
                if (requestId != latestRequestId) return@launch
                _render.value = RenderState.Ready(mesh, result.stats, key)
                _console.value = describeSuccess(_fileName.value, result.stats)
            } else {
                val message = result.error ?: emptyGeometryReason(result.stats)
                _render.value = RenderState.Failed(message, key)
                _console.value = describeFailure(message, result.stats)
            }
        }
    }

    /**
     * Only parameters that differ from their parsed defaults are submitted as
     * overrides, so the appended block is a small, exact `-D` equivalent rather
     * than a re-statement of every top-level assignment (including derived ones).
     */
    private fun effectiveOverrides(): Map<String, String> {
        val defaults = _model.value.allParameters.associate { it.name to it.defaultValue }
        return _values.value.filter { (name, value) -> defaults[name] != value }
    }

    private fun renderKey(source: String, overrides: Map<String, String>): String {
        val canonical = buildString {
            append(source)
            append('\u0000')
            overrides.toSortedMap().forEach { (name, value) ->
                append(name).append('=').append(value).append('\n')
            }
        }
        return MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    /**
     * The evaluator reports success for a part built only from constructs it has
     * no kernel for (2D shapes, `linear_extrude`, `offset`, …) yet emits no
     * polygons. Surface that honestly instead of showing an empty viewport.
     */
    private fun emptyGeometryReason(stats: EvalStats): String {
        val unsupported = stats.warnings
            .filter { it.contains("2D/import kernel") || it.startsWith("unknown module") }
            .distinct()
        return buildString {
            append("This part produced no geometry. ")
            if (unsupported.isNotEmpty()) {
                append("The evaluator does not support the constructs it needs:\n")
                unsupported.forEach { append("• ").append(it).append('\n') }
            } else {
                append("The selected part evaluates to nothing.")
            }
        }.trim()
    }

    /** Appends the changed customizer overrides so the last top-level assignment wins. */
    private fun withOverrides(source: String, values: Map<String, String>): String {
        if (values.isEmpty()) return source
        val overrides = values.entries.joinToString("\n") { (name, value) -> "$name = $value;" }
        return source + "\n// --- viewer overrides ---\n" + overrides + "\n"
    }

    private fun describeSuccess(name: String, stats: EvalStats): List<ConsoleLine> = buildList {
        add(ConsoleLine(Level.INFO, "Rendered $name"))
        add(
            ConsoleLine(
                Level.OK,
                "${stats.triangles} triangles · ${stats.polygons} polygons" +
                    (stats.bounds?.let { " · ${format(it.size.x)}×${format(it.size.y)}×${format(it.size.z)} mm" } ?: ""),
            ),
        )
        stats.warnings.forEach { add(ConsoleLine(Level.WARN, it)) }
        stats.echo.forEach { add(ConsoleLine(Level.INFO, it)) }
    }

    private fun describeFailure(message: String, stats: EvalStats): List<ConsoleLine> = buildList {
        add(ConsoleLine(Level.ERROR, message))
        stats.warnings.forEach { add(ConsoleLine(Level.WARN, it)) }
    }

    private fun format(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString() else "%.1f".format(value)
}

enum class Level { INFO, OK, WARN, ERROR }

data class ConsoleLine(val level: Level, val text: String)
