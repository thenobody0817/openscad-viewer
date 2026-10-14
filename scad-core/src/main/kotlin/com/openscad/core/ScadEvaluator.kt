package com.openscad.core

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan
import kotlin.random.Random
import org.locationtech.jts.geom.Geometry

class ScadEvaluationException(message: String) : RuntimeException(message)

data class EvalStats(
    val polygons: Int,
    val triangles: Int,
    val bounds: Bounds?,
    val echo: List<String>,
    val warnings: List<String>,
)

data class EvalResult(
    val solid: Solid?,
    val error: String?,
    val stats: EvalStats,
) {
    val ok: Boolean get() = error == null && solid != null
}

/** Result geometry of a statement: a 3D [Solid] or a 2D polygonal region. */
sealed interface Geom

class SolidGeom(val solid: Solid) : Geom

class AreaGeom(val area: Geometry) : Geom

private fun Geom.transformed(mat: Mat4): Geom = when (this) {
    is SolidGeom -> SolidGeom(solid.transformed(mat))
    is AreaGeom -> AreaGeom(Scad2D.transform(area, mat))
}

/**
 * Evaluates a parsed OpenSCAD program into a [Solid].
 *
 * Supports: `cube/sphere/cylinder/polyhedron`, `translate/rotate/scale/mirror/
 * multmatrix/color`, `union/difference/intersection`, user `module`s and
 * `function`s, `for`, `if`, `let`, `children()`, the usual expression grammar and
 * the common math/list builtins. Constructs that need a 2D kernel
 * (`linear_extrude`, `offset`, `projection`, `import`, …) are reported as
 * warnings and skipped rather than failing the render.
 */
class ScadEvaluator(
    private val maxPolygons: Int = 400_000,
    private val maxDepth: Int = 60,
) {

    private val echoLines = ArrayList<String>()
    private val warnings = ArrayList<String>()
    private var random = Random(0)

    fun evaluate(source: String): EvalResult {
        echoLines.clear()
        warnings.clear()
        random = Random(source.hashCode())
        return try {
            val ast = ScadParser.parse(source)
            val root = Scope(null)
            root.vars["PI"] = NumV(PI)
            val geometry = evalTopLevel(ast, root, 0)
            val areas = geometry.filterIsInstance<AreaGeom>()
            if (areas.isNotEmpty()) {
                warnings += "2D geometry at the top level is not rendered; wrap it in linear_extrude()"
            }
            val solid = Solid.union(geometry.filterIsInstance<SolidGeom>().map { it.solid })
            if (solid.polygons.size > maxPolygons) {
                return EvalResult(null, "Model too complex (${solid.polygons.size} polygons)", emptyStats())
            }
            EvalResult(
                solid = solid,
                error = null,
                stats = EvalStats(
                    polygons = solid.polygons.size,
                    triangles = solid.triangles().size,
                    bounds = if (solid.isEmpty) null else solid.bounds,
                    echo = echoLines.toList(),
                    warnings = warnings.toList(),
                ),
            )
        } catch (t: Throwable) {
            EvalResult(null, t.message ?: t::class.simpleName, emptyStats())
        }
    }

    private fun emptyStats() = EvalStats(0, 0, null, echoLines.toList(), warnings.toList())

    // ---- scopes --------------------------------------------------------------

    private class Scope(val parent: Scope?) {
        val vars = HashMap<String, Val>()
        val modules = HashMap<String, ModuleDefStmt>()
        val functions = HashMap<String, FunctionDefStmt>()

        fun lookupVar(name: String): Val? {
            var s: Scope? = this
            while (s != null) {
                s.vars[name]?.let { return it }
                s = s.parent
            }
            return null
        }

        fun lookupModule(name: String): ModuleDefStmt? {
            var s: Scope? = this
            while (s != null) {
                s.modules[name]?.let { return it }
                s = s.parent
            }
            return null
        }

        fun lookupFunction(name: String): FunctionDefStmt? {
            var s: Scope? = this
            while (s != null) {
                s.functions[name]?.let { return it }
                s = s.parent
            }
            return null
        }
    }

    // ---- statement evaluation ------------------------------------------------

    private fun evalTopLevel(ast: Stmt, root: Scope, depth: Int): List<Geom> {
        if (ast !is BlockStmt) return evalStmt(ast, root, emptyList(), depth)

        // First pass: bind top-level assignments, modules and functions.
        for (statement in ast.statements) {
            when (statement) {
                is AssignStmt -> root.vars[statement.name] = evalExpr(statement.expr, root)
                is ModuleDefStmt -> root.modules[statement.name] = statement
                is FunctionDefStmt -> root.functions[statement.name] = statement
                else -> Unit
            }
        }

        // Second pass: geometry.
        val out = ArrayList<Geom>()
        for (statement in ast.statements) {
            when (statement) {
                is AssignStmt, is ModuleDefStmt, is FunctionDefStmt -> Unit
                else -> out += evalStmt(statement, root, emptyList(), depth)
            }
        }
        return out
    }

    private fun evalStmt(stmt: Stmt, scope: Scope, children: List<Geom>, depth: Int): List<Geom> {
        if (depth > maxDepth) throw ScadEvaluationException("Recursion too deep")
        return when (stmt) {
            EmptyStmt -> emptyList()

            is BlockStmt -> {
                val local = Scope(scope)
                val out = ArrayList<Geom>()
                for (statement in stmt.statements) {
                    when (statement) {
                        is ModuleDefStmt -> local.modules[statement.name] = statement
                        is FunctionDefStmt -> local.functions[statement.name] = statement
                        else -> out += evalStmt(statement, local, children, depth + 1)
                    }
                }
                out
            }

            is AssignStmt -> {
                scope.vars[stmt.name] = evalExpr(stmt.expr, scope)
                emptyList()
            }

            is ModuleDefStmt -> {
                scope.modules[stmt.name] = stmt
                emptyList()
            }

            is FunctionDefStmt -> {
                scope.functions[stmt.name] = stmt
                emptyList()
            }

            is IfStmt -> {
                if (evalExpr(stmt.condition, scope).asBool()) {
                    evalStmt(stmt.thenBranch, scope, children, depth + 1)
                } else {
                    stmt.elseBranch?.let { evalStmt(it, scope, children, depth + 1) } ?: emptyList()
                }
            }

            is ForStmt -> {
                val out = ArrayList<Geom>()
                fun loop(index: Int, current: Scope) {
                    if (index == stmt.bindings.size) {
                        out += evalStmt(stmt.body, current, children, depth + 1)
                        return
                    }
                    val binding = stmt.bindings[index]
                    val value = evalExpr(binding.expr, current)
                    val items = value.asList() ?: listOf(value)
                    for (item in items) {
                        val child = Scope(current)
                        child.vars[binding.name] = item
                        loop(index + 1, child)
                    }
                }
                loop(0, scope)
                out
            }

            is LetStmt -> {
                val local = Scope(scope)
                for (binding in stmt.bindings) {
                    local.vars[binding.name] = evalExpr(binding.expr, local)
                }
                evalStmt(stmt.body, local, children, depth + 1)
            }

            is ModuleCallStmt -> evalModuleCall(stmt, scope, children, depth)
        }
    }

    // ---- module instantiation ------------------------------------------------

    private fun evalModuleCall(
        call: ModuleCallStmt,
        scope: Scope,
        children: List<Geom>,
        depth: Int,
    ): List<Geom> {
        if (call.modifier == '*') return emptyList()

        if (call.name == "children") {
            val arg = call.args.firstOrNull() ?: return children
            return when (val value = evalExpr(arg.expr, scope)) {
                is RangeV -> value.expand().mapNotNull { it.asDouble()?.toInt() }.mapNotNull { children.getOrNull(it) }
                is ListV -> value.items.mapNotNull { it.asDouble()?.toInt() }.mapNotNull { children.getOrNull(it) }
                else -> listOfNotNull(children.getOrNull(value.asDouble()?.toInt() ?: 0))
            }
        }

        // `intersection_for` drives its own iteration over the bindings.
        if (call.name == "intersection_for") {
            val results = ArrayList<Geom>()
            fun loop(index: Int, current: Scope) {
                if (index == call.args.size) {
                    call.body?.let { results += evalStmt(it, current, children, depth + 1) }
                    return
                }
                val binding = call.args[index]
                val name = binding.name ?: return
                val value = evalExpr(binding.expr, current)
                val items = value.asList() ?: listOf(value)
                for (item in items) {
                    val next = Scope(current)
                    next.vars[name] = item
                    loop(index + 1, next)
                }
            }
            loop(0, scope)
            if (results.isEmpty()) return emptyList()
            return listOf(combine("intersection", results))
        }

        val childGeometry: List<Geom> =
            call.body?.let { evalStmt(it, scope, children, depth + 1) } ?: emptyList()

        scope.lookupModule(call.name)?.let { definition ->
            return invokeUserModule(definition, call, scope, childGeometry, depth + 1)
        }

        return evalBuiltinModule(call.name, call, scope, childGeometry, depth)
    }

    private fun invokeUserModule(
        definition: ModuleDefStmt,
        call: ModuleCallStmt,
        callerScope: Scope,
        childGeometry: List<Geom>,
        depth: Int,
    ): List<Geom> {
        val callScope = Scope(callerScope)
        bindParams(definition.params, call.args, callerScope, callScope)
        return evalStmt(definition.body, callScope, childGeometry, depth + 1)
    }

    private fun bindParams(params: List<Param>, args: List<Arg>, callerScope: Scope, callScope: Scope) {
        val named = HashMap<String, Arg>()
        val positional = ArrayList<Arg>()
        for (arg in args) if (arg.name != null) named[arg.name] = arg else positional += arg

        for ((index, param) in params.withIndex()) {
            val arg = named[param.name] ?: positional.getOrNull(index)
            callScope.vars[param.name] = when {
                arg != null -> evalExpr(arg.expr, callerScope)
                param.default != null -> evalExpr(param.default, callScope)
                else -> UndefV
            }
        }
    }

    // ---- builtin modules -----------------------------------------------------

    private fun evalBuiltinModule(
        name: String,
        call: ModuleCallStmt,
        scope: Scope,
        childGeometry: List<Geom>,
        depth: Int,
    ): List<Geom> {
        val fn = special(scope, "\$fn", 0.0)
        val fa = special(scope, "\$fa", 12.0)
        val fs = special(scope, "\$fs", 2.0)

        fun arg(i: Int): Val? = call.args.getOrNull(i)?.takeIf { it.name == null }?.let { evalExpr(it.expr, scope) }
        fun named(key: String): Val? = call.args.firstOrNull { it.name == key }?.let { evalExpr(it.expr, scope) }
        fun d(value: Val?, fallback: Double) = value?.asDouble() ?: fallback

        // ---- 2D primitives ----
        when (name) {
            "circle" -> {
                val radius = named("r")?.asDouble()
                    ?: named("d")?.asDouble()?.div(2)
                    ?: d(arg(0), 1.0)
                return listOf(AreaGeom(Scad2D.circle(radius, Primitives.fragments(radius, fn, fs, fa))))
            }
            "square" -> {
                val sizeValue = named("size") ?: arg(0)
                val list = sizeValue?.asList()
                val w = list?.getOrNull(0)?.asDouble() ?: d(sizeValue, 1.0)
                val h = list?.getOrNull(1)?.asDouble() ?: w
                val center = (named("center") ?: arg(1))?.asBool() ?: false
                return listOf(AreaGeom(Scad2D.square(w, h, center)))
            }
            "polygon" -> {
                val pts = (named("points") ?: arg(0))?.asList()
                    ?.mapNotNull { it.asList()?.toVec3() } ?: emptyList()
                return listOf(AreaGeom(Scad2D.polygon(pts)))
            }
        }

        // ---- transforms (preserve the child's 2D/3D kind) ----
        val transform: Mat4? = when (name) {
            "translate" -> {
                val v = (arg(0) ?: named("v"))?.asList()?.toVec3() ?: Vec3(0.0, 0.0, 0.0)
                Mat4.translation(v.x, v.y, v.z)
            }
            "scale" -> {
                val value = arg(0) ?: named("v")
                val list = value?.asList()
                val s = if (list != null) Vec3(
                    list.getOrNull(0)?.asDouble() ?: 1.0,
                    list.getOrNull(1)?.asDouble() ?: 1.0,
                    list.getOrNull(2)?.asDouble() ?: 1.0,
                ) else d(value, 1.0).let { Vec3(it, it, it) }
                Mat4.scaling(s.x, s.y, s.z)
            }
            "rotate" -> {
                val value = arg(0) ?: named("a")
                val list = value?.asList()
                if (list != null) {
                    Mat4.rotationZ(Math.toRadians(list.getOrNull(2)?.asDouble() ?: 0.0)) *
                        Mat4.rotationY(Math.toRadians(list.getOrNull(1)?.asDouble() ?: 0.0)) *
                        Mat4.rotationX(Math.toRadians(list.getOrNull(0)?.asDouble() ?: 0.0))
                } else {
                    Mat4.rotationZ(Math.toRadians(d(value, 0.0)))
                }
            }
            "mirror" -> {
                val n = (arg(0) ?: named("v"))?.asList()?.toVec3()?.normalized() ?: Vec3(1.0, 0.0, 0.0)
                val data = DoubleArray(16)
                data[0] = 1 - 2 * n.x * n.x; data[1] = -2 * n.x * n.y; data[2] = -2 * n.x * n.z
                data[4] = -2 * n.y * n.x; data[5] = 1 - 2 * n.y * n.y; data[6] = -2 * n.y * n.z
                data[8] = -2 * n.z * n.x; data[9] = -2 * n.z * n.y; data[10] = 1 - 2 * n.z * n.z
                data[15] = 1.0
                Mat4(data)
            }
            "multmatrix" -> {
                val rows = arg(0)?.asList()?.mapNotNull { it.asList()?.mapNotNull { c -> c.asDouble() } } ?: emptyList()
                val data = DoubleArray(16)
                data[0] = 1.0; data[5] = 1.0; data[10] = 1.0; data[15] = 1.0
                for (r in 0 until min(4, rows.size)) {
                    val row = rows[r]
                    for (c in 0 until min(4, row.size)) data[r * 4 + c] = row[c]
                }
                Mat4(data)
            }
            else -> null
        }
        if (transform != null) return childGeometry.map { it.transformed(transform) }
        if (name == "color" || name == "render" || name == "group") return childGeometry

        // ---- 2D offset ----
        if (name == "offset") {
            if (childGeometry.isEmpty()) return emptyList()
            if (childGeometry.any { it is SolidGeom }) {
                warnings += "offset() expects 2D children; 3D children were skipped"
            }
            val r = named("r")?.asDouble() ?: 0.0
            val delta = named("delta")?.asDouble() ?: 0.0
            val join = (named("join") as? StrV)?.v?.lowercase()
            val segments = if (fn >= 3.0) max(2, (fn / 4.0).toInt()) else 16
            val round = join == "round" || (join == null && r != 0.0)
            return childGeometry.map { child ->
                when (child) {
                    is AreaGeom -> AreaGeom(Scad2D.offset(child.area, r, delta, segments, round))
                    is SolidGeom -> SolidGeom(Solid.EMPTY)
                }
            }
        }

        // ---- extrusions ----
        if (name == "linear_extrude") {
            val h = named("height")?.asDouble() ?: d(arg(0), 1.0)
            val center = named("center")?.asBool() ?: false
            val z0 = if (center) -h / 2 else 0.0
            val z1 = z0 + h
            val parts = ArrayList<Solid>()
            for (child in childGeometry) {
                when (child) {
                    is AreaGeom -> parts += Scad2D.extrude(child.area, z0, z1)
                    is SolidGeom -> warnings += "linear_extrude() expects 2D children; a 3D child was skipped"
                }
            }
            return listOf(SolidGeom(Solid.union(parts)))
        }
        if (name == "rotate_extrude") {
            warnings += "rotate_extrude() is not supported and was skipped"
            return emptyList()
        }

        // ---- booleans ----
        if (name == "union" || name == "difference" || name == "intersection") {
            if (childGeometry.isEmpty()) return emptyList()
            return listOf(combine(name, childGeometry))
        }

        // ---- hull / minkowski ----
        if (name == "hull" || name == "minkowski") {
            val areas = childGeometry.filterIsInstance<AreaGeom>()
            val solids = childGeometry.filterIsInstance<SolidGeom>()
            if (areas.isNotEmpty() && solids.isEmpty()) {
                val merged = Scad2D.unionAll(areas.map { it.area })
                return listOf(AreaGeom(if (name == "hull") Scad2D.hull(merged) else merged))
            }
            if (areas.isNotEmpty() && solids.isNotEmpty()) {
                warnings += "$name() with mixed 2D/3D children; the 2D parts were skipped"
            }
            warnings += "$name() is approximated as union()"
            return if (solids.isEmpty()) emptyList() else listOf(SolidGeom(Csg.unionAll(solids.map { it.solid })))
        }

        return when (name) {
            "cube" -> {
                val sizeValue = named("size") ?: arg(0)
                val list = sizeValue?.asList()
                val size = if (list != null) list.toVec3() else d(sizeValue, 1.0).let { Vec3(it, it, it) }
                val center = (named("center") ?: arg(1))?.asBool() ?: false
                listOf(SolidGeom(Primitives.cube(size, center)))
            }

            "sphere" -> {
                val radius = named("r")?.asDouble()
                    ?: named("d")?.asDouble()?.div(2)
                    ?: d(arg(0), 1.0)
                listOf(SolidGeom(Primitives.sphere(radius, fn, fs, fa)))
            }

            "cylinder" -> {
                val h = named("h")?.asDouble() ?: d(arg(0), 1.0)
                val r = named("r")?.asDouble()
                val dia = named("d")?.asDouble()
                val baseR = r ?: dia?.div(2) ?: 1.0
                val r1 = named("r1")?.asDouble() ?: named("d1")?.asDouble()?.div(2) ?: baseR
                val r2 = named("r2")?.asDouble() ?: named("d2")?.asDouble()?.div(2) ?: named("r")?.asDouble() ?: dia?.div(2) ?: baseR
                val center = named("center")?.asBool() ?: false
                listOf(SolidGeom(Primitives.cylinder(h, r1, r2, center, fn, fs, fa)))
            }

            "polyhedron" -> {
                val points = (named("points") ?: arg(0))?.asList()
                    ?.mapNotNull { it.asList()?.toVec3() } ?: emptyList()
                val faces = (named("faces") ?: arg(1))?.asList()
                    ?.mapNotNull { it.asList()?.mapNotNull { v -> v.asDouble()?.toInt() } } ?: emptyList()
                listOf(SolidGeom(Primitives.polyhedron(points, faces)))
            }

            "let", "assign" -> {
                val local = Scope(scope)
                for (a in call.args) if (a.name != null) local.vars[a.name] = evalExpr(a.expr, scope)
                call.body?.let { evalStmt(it, local, emptyList(), depth + 1) } ?: emptyList()
            }

            "echo" -> {
                echoLines += "ECHO: " + call.args.joinToString(", ") { evalExpr(it.expr, scope).display() }
                emptyList()
            }

            "assert" -> {
                val condition = arg(0)?.asBool() ?: true
                if (!condition) {
                    val message = arg(1)?.display() ?: "assertion failed"
                    warnings += "ASSERT: $message"
                }
                emptyList()
            }

            "projection", "surface", "text",
            "import", "import_stl", "import_off", "import_dxf", "import_svg",
            "dxf_linear_extrude", "dxf_rotate_extrude",
            -> {
                warnings += "$name() needs a 2D/import kernel and was skipped"
                emptyList()
            }

            else -> {
                warnings += "unknown module $name()"
                emptyList()
            }
        }
    }

    /** Applies `union`/`difference`/`intersection` as a 2D or 3D operation. */
    private fun combine(name: String, children: List<Geom>): Geom {
        val areas = children.filterIsInstance<AreaGeom>()
        val solids = children.filterIsInstance<SolidGeom>()
        if (areas.isNotEmpty() && solids.isEmpty()) {
            val parts = areas.map { it.area }
            val result = when (name) {
                "union" -> Scad2D.unionAll(parts)
                "difference" -> parts.drop(1).fold(parts.first()) { acc, g -> Scad2D.difference(acc, g) }
                else -> Scad2D.intersectionAll(parts)
            }
            return AreaGeom(result)
        }
        if (areas.isNotEmpty() && solids.isNotEmpty()) {
            warnings += "$name() mixes 2D and 3D children; the 2D parts were skipped"
        }
        val parts = solids.map { it.solid }
        if (parts.isEmpty()) return SolidGeom(Solid.EMPTY)
        val solid = when (name) {
            "union" -> Csg.unionAll(parts)
            "difference" -> parts.drop(1).fold(parts.first()) { acc, s -> Csg.subtract(acc, s) }
            else -> parts.drop(1).fold(parts.first()) { acc, s -> Csg.intersect(acc, s) }
        }
        return SolidGeom(solid)
    }

    private fun special(scope: Scope, name: String, fallback: Double): Double =
        scope.lookupVar(name)?.asDouble() ?: fallback

    // ---- expression evaluation -----------------------------------------------

    private fun evalExpr(expr: Expr, scope: Scope): Val = when (expr) {
        is NumberExpr -> NumV(expr.value)
        is StringExpr -> StrV(expr.value)
        is BoolExpr -> BoolV(expr.value)
        UndefExpr -> UndefV
        is IdentExpr -> scope.lookupVar(expr.name) ?: UndefV
        is ListExpr -> ListV(expr.items.map { evalExpr(it, scope) })
        is RangeExpr -> RangeV(
            evalExpr(expr.start, scope).asDouble() ?: 0.0,
            expr.step?.let { evalExpr(it, scope).asDouble() } ?: 1.0,
            evalExpr(expr.end, scope).asDouble() ?: 0.0,
        )
        is IndexExpr -> {
            val target = evalExpr(expr.target, scope)
            val index = evalExpr(expr.index, scope).asDouble()?.toInt()
            when {
                target is StrV && index != null -> StrV(target.v.getOrNull(index)?.toString() ?: "")
                else -> {
                    val list = target.asList()
                    if (list != null && index != null) list.getOrNull(index) ?: UndefV else UndefV
                }
            }
        }
        is UnaryExpr -> when (expr.op) {
            "-" -> negate(evalExpr(expr.operand, scope))
            "!" -> BoolV(!evalExpr(expr.operand, scope).asBool())
            else -> UndefV
        }
        is BinaryExpr -> binary(expr.op, evalExpr(expr.left, scope), evalExpr(expr.right, scope))
        is TernaryExpr ->
            if (evalExpr(expr.condition, scope).asBool()) evalExpr(expr.ifTrue, scope)
            else evalExpr(expr.ifFalse, scope)
        is CallExpr -> callFunction(expr, scope)
        is LetExpr -> {
            val local = Scope(scope)
            for (binding in expr.bindings) local.vars[binding.name] = evalExpr(binding.expr, local)
            evalExpr(expr.body, local)
        }
        is ListCompExpr -> {
            val out = ArrayList<Val>()
            fun loop(index: Int, current: Scope) {
                if (index == expr.bindings.size) {
                    if (expr.condition == null || evalExpr(expr.condition, current).asBool()) {
                        out += evalExpr(expr.body, current)
                    }
                    return
                }
                val binding = expr.bindings[index]
                val value = evalExpr(binding.expr, current)
                val items = value.asList() ?: listOf(value)
                for (item in items) {
                    val next = Scope(current)
                    next.vars[binding.name] = item
                    loop(index + 1, next)
                }
            }
            loop(0, scope)
            ListV(out)
        }
    }

    private fun negate(value: Val): Val = when (value) {
        is NumV -> NumV(-value.v)
        is ListV -> ListV(value.items.map { negate(it) })
        else -> UndefV
    }

    private fun binary(op: String, a: Val, b: Val): Val {
        val la = a.asList()
        val lb = b.asList()
        return when (op) {
            "+" -> if (la != null && lb != null) {
                ListV(la.zip(lb).map { (x, y) -> NumV((x.asDouble() ?: 0.0) + (y.asDouble() ?: 0.0)) })
            } else {
                NumV((a.asDouble() ?: 0.0) + (b.asDouble() ?: 0.0))
            }

            "-" -> if (la != null && lb != null) {
                ListV(la.zip(lb).map { (x, y) -> NumV((x.asDouble() ?: 0.0) - (y.asDouble() ?: 0.0)) })
            } else {
                NumV((a.asDouble() ?: 0.0) - (b.asDouble() ?: 0.0))
            }

            "*" -> when {
                la != null && lb != null ->
                    ListV(la.zip(lb).map { (x, y) -> NumV((x.asDouble() ?: 0.0) * (y.asDouble() ?: 0.0)) })
                la != null -> ListV(la.map { NumV((it.asDouble() ?: 0.0) * (b.asDouble() ?: 0.0)) })
                lb != null -> ListV(lb.map { NumV((it.asDouble() ?: 0.0) * (a.asDouble() ?: 0.0)) })
                else -> NumV((a.asDouble() ?: 0.0) * (b.asDouble() ?: 0.0))
            }

            "/" -> if (la != null) {
                ListV(la.map { NumV((it.asDouble() ?: 0.0) / (b.asDouble() ?: 1.0)) })
            } else {
                NumV((a.asDouble() ?: 0.0) / (b.asDouble() ?: 1.0))
            }

            "%" -> NumV((a.asDouble() ?: 0.0) % (b.asDouble() ?: 1.0))
            "==" -> BoolV(valuesEqual(a, b))
            "!=" -> BoolV(!valuesEqual(a, b))
            "<" -> BoolV((a.asDouble() ?: 0.0) < (b.asDouble() ?: 0.0))
            "<=" -> BoolV((a.asDouble() ?: 0.0) <= (b.asDouble() ?: 0.0))
            ">" -> BoolV((a.asDouble() ?: 0.0) > (b.asDouble() ?: 0.0))
            ">=" -> BoolV((a.asDouble() ?: 0.0) >= (b.asDouble() ?: 0.0))
            "&&" -> BoolV(a.asBool() && b.asBool())
            "||" -> BoolV(a.asBool() || b.asBool())
            else -> UndefV
        }
    }

    private fun valuesEqual(a: Val, b: Val): Boolean = when {
        a is NumV && b is NumV -> abs(a.v - b.v) < 1e-12
        a is BoolV && b is BoolV -> a.v == b.v
        a is StrV && b is StrV -> a.v == b.v
        a is ListV && b is ListV ->
            a.items.size == b.items.size && a.items.indices.all { valuesEqual(a.items[it], b.items[it]) }
        a is RangeV && b is RangeV -> a.start == b.start && a.step == b.step && a.end == b.end
        a === UndefV && b === UndefV -> true
        else -> false
    }

    private fun callFunction(expr: CallExpr, scope: Scope): Val {
        scope.lookupFunction(expr.name)?.let { definition ->
            val callScope = Scope(scope)
            bindParams(definition.params, expr.args, scope, callScope)
            return evalExpr(definition.expr, callScope)
        }

        val args = expr.args
        fun a(i: Int): Val? = args.getOrNull(i)?.takeIf { it.name == null }?.let { evalExpr(it.expr, scope) }
        fun d(i: Int): Double = a(i)?.asDouble() ?: 0.0
        fun all(): List<Val> = args.map { evalExpr(it.expr, scope) }

        return when (expr.name) {
            "sin" -> NumV(sin(Math.toRadians(d(0))))
            "cos" -> NumV(cos(Math.toRadians(d(0))))
            "tan" -> NumV(tan(Math.toRadians(d(0))))
            "asin" -> NumV(Math.toDegrees(asin(d(0).coerceIn(-1.0, 1.0))))
            "acos" -> NumV(Math.toDegrees(acos(d(0).coerceIn(-1.0, 1.0))))
            "atan" -> NumV(Math.toDegrees(atan(d(0))))
            "atan2" -> NumV(Math.toDegrees(atan2(d(0), d(1))))
            "abs" -> NumV(abs(d(0)))
            "ceil" -> NumV(ceil(d(0)))
            "floor" -> NumV(floor(d(0)))
            "round" -> NumV(round(d(0)))
            "sign" -> NumV(sign(d(0)))
            "sqrt" -> NumV(sqrt(max(0.0, d(0))))
            "exp" -> NumV(exp(d(0)))
            "ln" -> NumV(ln(d(0)))
            "log" -> NumV(log10(d(0)))
            "pow" -> NumV(d(0).pow(d(1)))
            "max" -> NumV(all().mapNotNull { it.asDouble() }.maxOrNull() ?: 0.0)
            "min" -> NumV(all().mapNotNull { it.asDouble() }.minOrNull() ?: 0.0)
            "norm" -> NumV(a(0)?.asList()?.toVec3()?.length() ?: 0.0)
            "cross" -> {
                val u = a(0)?.asList()?.toVec3() ?: Vec3(0.0, 0.0, 0.0)
                val v = a(1)?.asList()?.toVec3() ?: Vec3(0.0, 0.0, 0.0)
                u.cross(v).toListV()
            }
            "len" -> {
                val value = a(0)
                val length = when {
                    value is StrV -> value.v.length
                    else -> value?.asList()?.size ?: 0
                }
                NumV(length.toDouble())
            }
            "concat" -> {
                val out = ArrayList<Val>()
                for (value in all()) {
                    val list = value.asList()
                    if (list != null) out += list else out += value
                }
                ListV(out)
            }
            "str" -> StrV(all().joinToString("") { it.display() })
            "chr" -> StrV(all().mapNotNull { it.asDouble()?.toInt() }.map { it.toChar() }.joinToString(""))
            "ord" -> NumV(((a(0) as? StrV)?.v?.firstOrNull()?.code ?: 0).toDouble())
            "rands" -> {
                val lo = d(0)
                val hi = d(1)
                val count = d(2).toInt().coerceIn(0, 100_000)
                // Honours the OpenSCAD seed argument so the layout is stable
                // across renders and does not depend on the source text.
                val seed = args.getOrNull(3)?.let { evalExpr(it.expr, scope).asDouble()?.toLong() } ?: 0L
                val rng = Random(seed)
                ListV((0 until count).map { NumV(lo + rng.nextDouble() * (hi - lo)) })
            }
            "is_undef" -> BoolV(a(0) === UndefV)
            "is_bool" -> BoolV(a(0) is BoolV)
            "is_num" -> BoolV(a(0) is NumV)
            "is_string" -> BoolV(a(0) is StrV)
            "is_list" -> BoolV(a(0) is ListV || a(0) is RangeV)
            "is_function" -> BoolV(false)
            "version" -> ListV(listOf(NumV(2025.0), NumV(1.0), NumV(1.0)))
            "parent_module" -> NumV(0.0)
            "search", "lookup" -> UndefV
            else -> {
                warnings += "unknown function ${expr.name}()"
                UndefV
            }
        }
    }
}
