package com.openscad.core

// ---------------------------------------------------------------------------
// Statements
// ---------------------------------------------------------------------------

sealed interface Stmt

data object EmptyStmt : Stmt

class BlockStmt(val statements: List<Stmt>) : Stmt

class AssignStmt(val name: String, val expr: Expr) : Stmt

class ModuleDefStmt(val name: String, val params: List<Param>, val body: Stmt) : Stmt

class FunctionDefStmt(val name: String, val params: List<Param>, val expr: Expr) : Stmt

class IfStmt(val condition: Expr, val thenBranch: Stmt, val elseBranch: Stmt?) : Stmt

class ForStmt(val bindings: List<Binding>, val body: Stmt) : Stmt

class LetStmt(val bindings: List<Binding>, val body: Stmt) : Stmt

/** A module instantiation such as `translate([1,2,3]) child;` or `cube(5);`. */
class ModuleCallStmt(
    val name: String,
    val args: List<Arg>,
    val body: Stmt?,
    val modifier: Char = '\u0000',
) : Stmt

class Binding(val name: String, val expr: Expr)

class Param(val name: String, val default: Expr?)

class Arg(val name: String?, val expr: Expr)

// ---------------------------------------------------------------------------
// Expressions
// ---------------------------------------------------------------------------

sealed interface Expr

class NumberExpr(val value: Double) : Expr

class StringExpr(val value: String) : Expr

class BoolExpr(val value: Boolean) : Expr

data object UndefExpr : Expr

class IdentExpr(val name: String) : Expr

class ListExpr(val items: List<Expr>) : Expr

class RangeExpr(val start: Expr, val step: Expr?, val end: Expr) : Expr

class IndexExpr(val target: Expr, val index: Expr) : Expr

class UnaryExpr(val op: String, val operand: Expr) : Expr

class BinaryExpr(val op: String, val left: Expr, val right: Expr) : Expr

class TernaryExpr(val condition: Expr, val ifTrue: Expr, val ifFalse: Expr) : Expr

class CallExpr(val name: String, val args: List<Arg>) : Expr

/** `let (name = expr, …) body` — sequential bindings around an expression. */
class LetExpr(val bindings: List<Binding>, val body: Expr) : Expr

/**
 * A list comprehension: `[for (bindings) body]`, optionally filtered by
 * `if (condition)` between the bindings and the body.
 */
class ListCompExpr(
    val bindings: List<Binding>,
    val condition: Expr?,
    val body: Expr,
) : Expr
