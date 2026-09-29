package semantic;

import ast.Expr;
import ast.Stmt;
import lexer.Token;
import java.util.ArrayList;
import java.util.List;

public class TypeChecker {
    private final List<SemanticError> errors = new ArrayList<>();
    private SymbolTable scope = new SymbolTable();
    private Type currentReturnType = null;   // non-null only while checking a function body

    public List<SemanticError> check(List<Stmt> statements) {
        checkStatements(statements);
        return errors;
    }

    // ---------- statements ----------

    private void checkStatements(List<Stmt> statements) {
        // pass 1: register function signatures so they can call each other and themselves
        for (Stmt s : statements) {
            if (s instanceof Stmt.Function fn) declareFunction(fn);
        }
        // pass 2: check everything
        for (Stmt s : statements) {
            checkStmt(s);
        }
    }

    private void checkStmt(Stmt stmt) {
        switch (stmt) {
            case Stmt.Let let -> checkLet(let);
            case Stmt.ExprStmt e -> checkExpr(e.expression());
            case Stmt.Block block -> checkBlock(block);
            case Stmt.If ifStmt -> checkIf(ifStmt);
            case Stmt.While whileStmt -> checkWhile(whileStmt);
            case Stmt.Return ret -> checkReturn(ret);
            case Stmt.Function fn -> checkFunction(fn);
        }
    }

    private void checkLet(Stmt.Let let) {
        String name = let.name().lexeme();
        Type initType = checkExpr(let.initialiser());   // check BEFORE declaring, so `let x = x;` fails

        Type varType;
        if (let.typeAnnotation() != null) {
            Type declared = resolveType(let.typeAnnotation());
            if (initType != Type.ERROR && declared != Type.ERROR && initType != declared) {
                error(let.name(), "Expected " + declared + " but found " + initType);
            }
            varType = declared;
        } else {
            varType = initType;
        }

        if (varType == Type.VOID) {
            error(let.name(), "Cannot store a Void value");
            varType = Type.ERROR;
        }

        // declare even if the type is ERROR, so later uses of the name don't cause "undefined" errors
        if (!scope.declare(new Symbol.VariableSymbol(name, varType))) {
            error(let.name(), "'" + name + "' is already declared in this scope");
        }
    }

    private void checkBlock(Stmt.Block block) {
        SymbolTable previous = scope;
        scope = new SymbolTable(previous);
        checkStatements(block.statements());
        scope = previous;
    }

    private void checkIf(Stmt.If ifStmt) {
        checkCondition(ifStmt.keyword(), ifStmt.condition());
        checkStmt(ifStmt.thenBranch());
        if (ifStmt.elseBranch() != null) {
            checkStmt(ifStmt.elseBranch());
        }
    }

    private void checkWhile(Stmt.While whileStmt) {
        checkCondition(whileStmt.keyword(), whileStmt.condition());
        checkStmt(whileStmt.body());
    }

    private void checkCondition(Token keyword, Expr condition) {
        Type condType = checkExpr(condition);
        if (condType != Type.ERROR && condType != Type.BOOL) {
            error(keyword, "Condition must be Bool, found " + condType);
        }
    }

    private void declareFunction(Stmt.Function fn) {
        List<Type> paramTypes = new ArrayList<>();
        for (Stmt.Param p : fn.params()) {
            paramTypes.add(resolveType(p.type()));
        }
        Type returnType = resolveType(fn.returnType());
        String name = fn.name().lexeme();
        if (!scope.declare(new Symbol.FunctionSymbol(name, paramTypes, returnType))) {
            error(fn.name(), "'" + name + "' is already declared in this scope");
        }
    }

    private void checkFunction(Stmt.Function fn) {
        // the signature was already declared in pass 1
        SymbolTable previousScope = scope;
        Type previousReturn = currentReturnType;

        scope = new SymbolTable(previousScope);
        for (Stmt.Param p : fn.params()) {
            Type paramType = resolveType(p.type());
            if (paramType == Type.VOID) {
                error(p.type(), "A parameter cannot be Void");
                paramType = Type.ERROR;
            }
            if (!scope.declare(new Symbol.VariableSymbol(p.name().lexeme(), paramType))) {
                error(p.name(), "Duplicate parameter '" + p.name().lexeme() + "'");
            }
        }
        currentReturnType = resolveType(fn.returnType());

        // body statements go straight into the parameter scope
        checkStatements(fn.body().statements());

        scope = previousScope;
        currentReturnType = previousReturn;
    }

    private void checkReturn(Stmt.Return ret) {
        if (currentReturnType == null) {
            error(ret.keyword(), "'return' outside a function");
            if (ret.value() != null) checkExpr(ret.value());
            return;
        }
        if (ret.value() == null) {
            if (currentReturnType != Type.VOID && currentReturnType != Type.ERROR) {
                error(ret.keyword(), "Expected a " + currentReturnType + " return value");
            }
            return;
        }
        Type t = checkExpr(ret.value());
        if (t != Type.ERROR && currentReturnType != Type.ERROR && t != currentReturnType) {
            error(ret.keyword(), "Expected " + currentReturnType + " but found " + t);
        }
    }

    // ---------- expressions ----------

    private Type checkExpr(Expr expr) {
        return switch (expr) {
            case Expr.Literal lit -> checkLit(lit);
            case Expr.Variable v -> checkVar(v);
            case Expr.Assign a -> checkAssign(a);
            case Expr.Unary u -> checkUnary(u);
            case Expr.Binary b -> checkBinary(b);
            case Expr.Call c -> checkCall(c);
        };
    }

    private Type checkLit(Expr.Literal lit) {
        Object value = lit.value();
        if (value instanceof Integer) return Type.INT;
        if (value instanceof Double)  return Type.FLOAT;
        if (value instanceof String)  return Type.STRING;
        if (value instanceof Boolean) return Type.BOOL;
        return Type.ERROR;
    }

    private Type badOperands(Token op, Type left, Type right) {
        error(op, "Cannot apply '" + op.lexeme() + "' to " + left + " and " + right);
        return Type.ERROR;
    }

    private Type checkBinary(Expr.Binary b) {
        Type left  = checkExpr(b.left());
        Type right = checkExpr(b.right());
        if (left == Type.ERROR || right == Type.ERROR) {
            return Type.ERROR;   // already reported, stay quiet
        }
        Token operator = b.operator();
        return switch (operator.type()) {
            case PLUS -> {
                if (left == right && (left == Type.INT || left == Type.FLOAT || left == Type.STRING)) yield left;
                yield badOperands(operator, left, right);
            }
            case MINUS, STAR, SLASH, PERCENT -> {
                if (left == right && (left == Type.INT || left == Type.FLOAT)) yield left;
                yield badOperands(operator, left, right);
            }
            case LESS, LESS_EQUAL, GREATER, GREATER_EQUAL -> {
                if (left == right && (left == Type.INT || left == Type.FLOAT)) yield Type.BOOL;
                yield badOperands(operator, left, right);
            }
            case EQUAL_EQUAL, BANG_EQUAL -> {
                if (left == right && left != Type.VOID) yield Type.BOOL;
                yield badOperands(operator, left, right);
            }
            case AND, OR -> {
                if (left == Type.BOOL && right == Type.BOOL) yield Type.BOOL;
                yield badOperands(operator, left, right);
            }
            default -> throw new IllegalStateException("Unknown binary operator: " + operator.lexeme());
        };
    }

    private Type checkUnary(Expr.Unary u) {
        Type right = checkExpr(u.right());
        if (right == Type.ERROR) return Type.ERROR;
        Token operator = u.operator();
        return switch (operator.type()) {
            case MINUS -> {
                if (right == Type.INT || right == Type.FLOAT) yield right;
                error(operator, "Cannot apply '-' to " + right);
                yield Type.ERROR;
            }
            case BANG -> {
                if (right == Type.BOOL) yield Type.BOOL;
                error(operator, "Cannot apply '!' to " + right);
                yield Type.ERROR;
            }
            default -> throw new IllegalStateException("Unknown unary operator: " + operator.lexeme());
        };
    }

    private Type checkVar(Expr.Variable v) {
        Symbol symbol = scope.lookup(v.name().lexeme());
        if (symbol == null) {
            error(v.name(), "Undefined variable '" + v.name().lexeme() + "'");
            return Type.ERROR;
        }
        if (symbol instanceof Symbol.VariableSymbol vs) return vs.type();
        error(v.name(), "'" + v.name().lexeme() + "' is a function, not a variable");
        return Type.ERROR;
    }

    private Type checkAssign(Expr.Assign a) {
        Type valueType = checkExpr(a.value());   // check the right side first so its errors still get reported
        Symbol symbol = scope.lookup(a.name().lexeme());
        if (symbol == null) {
            error(a.name(), "Undefined variable '" + a.name().lexeme() + "'");
            return Type.ERROR;
        }
        if (!(symbol instanceof Symbol.VariableSymbol vs)) {
            error(a.name(), "'" + a.name().lexeme() + "' is a function, not a variable");
            return Type.ERROR;
        }
        if (valueType != Type.ERROR && vs.type() != Type.ERROR && valueType != vs.type()) {
            error(a.name(), "Cannot assign " + valueType + " to variable of type " + vs.type());
        }
        return vs.type();
    }

    private Type checkCall(Expr.Call call) {
        // built-in print: accepts any single non-Void value
        if (call.callee() instanceof Expr.Variable v && v.name().lexeme().equals("print")) {
            if (call.arguments().size() != 1) {
                error(call.paren(), "print takes exactly 1 argument");
                for (Expr arg : call.arguments()) checkExpr(arg);
            } else {
                Type argType = checkExpr(call.arguments().get(0));
                if (argType == Type.VOID) {
                    error(call.paren(), "Cannot print a Void value");
                }
            }
            return Type.VOID;
        }

        if (!(call.callee() instanceof Expr.Variable calleeVar)) {
            error(call.paren(), "Can only call functions");
            for (Expr arg : call.arguments()) checkExpr(arg);
            return Type.ERROR;
        }

        Symbol symbol = scope.lookup(calleeVar.name().lexeme());
        if (symbol == null) {
            error(calleeVar.name(), "Undefined function '" + calleeVar.name().lexeme() + "'");
            for (Expr arg : call.arguments()) checkExpr(arg);
            return Type.ERROR;
        }
        if (!(symbol instanceof Symbol.FunctionSymbol fn)) {
            error(calleeVar.name(), "'" + calleeVar.name().lexeme() + "' is not a function");
            for (Expr arg : call.arguments()) checkExpr(arg);
            return Type.ERROR;
        }

        // always check the arguments so their own errors are reported
        List<Type> argTypes = new ArrayList<>();
        for (Expr arg : call.arguments()) {
            argTypes.add(checkExpr(arg));
        }

        if (argTypes.size() != fn.paramTypes().size()) {
            error(call.paren(), "'" + fn.name() + "' expects " + fn.paramTypes().size()
                    + " arguments but got " + argTypes.size());
            return fn.returnType();
        }

        for (int i = 0; i < argTypes.size(); i++) {
            Type expected = fn.paramTypes().get(i);
            Type actual = argTypes.get(i);
            if (actual != Type.ERROR && expected != Type.ERROR && actual != expected) {
                error(tokenOf(call.arguments().get(i)) != null ? tokenOf(call.arguments().get(i)) : call.paren(),
                        "Argument " + (i + 1) + " of '" + fn.name() + "': expected " + expected + " but found " + actual);
            }
        }
        return fn.returnType();
    }

    // ---------- helpers ----------

    private Type resolveType(Token typeName) {
        return switch (typeName.lexeme()) {
            case "Int" -> Type.INT;
            case "Float" -> Type.FLOAT;
            case "Bool" -> Type.BOOL;
            case "String" -> Type.STRING;
            case "Void" -> Type.VOID;
            default -> {
                error(typeName, "Unknown type '" + typeName.lexeme() + "'");
                yield Type.ERROR;
            }
        };
    }

    // Finds a token to blame for an expression (literals have none, so may return null)
    private Token tokenOf(Expr expr) {
        return switch (expr) {
            case Expr.Literal lit -> null;
            case Expr.Variable v -> v.name();
            case Expr.Assign a -> a.name();
            case Expr.Unary u -> u.operator();
            case Expr.Binary b -> b.operator();
            case Expr.Call c -> c.paren();
        };
    }

    private void error(Token token, String message) {
        errors.add(new SemanticError(token, message));
    }
}