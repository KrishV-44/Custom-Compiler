package ast;

import lexer.Token;
import java.util.List;
import ast.Expr.*;

public sealed interface Expr {
    record Literal(Object value) implements Expr {}
    // value holds: Integer, Double, String, Boolean

    record Variable(Token name) implements Expr {}
    // a bare identifier used as a value, e.g. `x`

    record Assign(Token name, Expr value) implements Expr {}
    // x = value

    record Unary(Token operator, Expr right) implements Expr {}
    // -x   !ok

    record Binary(Expr left, Token operator, Expr right) implements Expr {}
    // a + b, a == b, a and b, a < b   (arithmetic, comparison, and/or all fit here)

    record Call(Expr callee, Token paren, List<Expr> arguments) implements Expr {}
    // f(a, b)  - callee is usually a Variable; paren is kept for error locations
}