package ast;

import lexer.Token;
import java.util.List;

public sealed interface Stmt {
    record Let(Token name, Token typeAnnotation, Expr initialiser) implements Stmt {}
    // typeAnnotation is nullable - null when `let x = 5;` has no `: Type`

    record ExprStmt(Expr expression) implements Stmt {}
    // an expression used as a statement, e.g. a bare function call: print(x);

    record Block(List<Stmt> statements) implements Stmt {}

    record If(Token keyword, Expr condition, Stmt thenBranch, Stmt elseBranch) implements Stmt {}
    // elseBranch is nullable

    record While(Token keyword, Expr condition, Stmt body) implements Stmt {}

    record Return(Token keyword, Expr value) implements Stmt {}
    // value is nullable - bare `return;`

    record Function(Token name, List<Param> params, Token returnType, Stmt.Block body) implements Stmt {}
    record Param(Token name, Token type) {}
}