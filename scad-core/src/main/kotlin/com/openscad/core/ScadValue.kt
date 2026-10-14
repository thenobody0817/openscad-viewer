package com.openscad.core

/** A runtime value in the OpenSCAD expression language. */
sealed interface Val {
    fun display(): String
}

data class NumV(val v: Double) : Val {
    override fun display(): String =
        if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()
}

data class StrV(val v: String) : Val {
    override fun display(): String = v
}

data class BoolV(val v: Boolean) : Val {
    override fun display(): String = v.toString()
}

data object UndefV : Val {
    override fun display(): String = "undef"
}

data class RangeV(val start: Double, val step: Double, val end: Double) : Val {
    override fun display(): String = "[$start : $step : $end]"

    fun expand(): List<Val> {
        val out = ArrayList<Val>()
        if (step == 0.0) return out
        var x = start
        var guard = 0
        if (step > 0) {
            while (x <= end + 1e-9 && guard < 1_000_000) { out += NumV(x); x += step; guard++ }
        } else {
            while (x >= end - 1e-9 && guard < 1_000_000) { out += NumV(x); x += step; guard++ }
        }
        return out
    }
}

data class ListV(val items: List<Val>) : Val {
    override fun display(): String = "[" + items.joinToString(", ") { it.display() } + "]"
}

fun Val.asDouble(): Double? = when (this) {
    is NumV -> v
    is BoolV -> if (v) 1.0 else 0.0
    else -> null
}

fun Val.asBool(): Boolean = when (this) {
    is BoolV -> v
    is NumV -> v != 0.0
    is StrV -> v.isNotEmpty()
    is ListV -> items.isNotEmpty()
    is RangeV -> true
    UndefV -> false
}

fun Val.asList(): List<Val>? = when (this) {
    is ListV -> items
    is RangeV -> expand()
    else -> null
}

fun List<Val>.toVec3(): Vec3 = Vec3(
    this.getOrNull(0)?.asDouble() ?: 0.0,
    this.getOrNull(1)?.asDouble() ?: 0.0,
    this.getOrNull(2)?.asDouble() ?: 0.0,
)

fun Vec3.toListV(): ListV = ListV(listOf(NumV(x), NumV(y), NumV(z)))
