package ir;

import ast.Expr;
import ast.Stmt;
import lexer.Token;
import semantic.Type;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class IRGenerator {
    private final Map<Expr, Type> expressionTypes;
    private List<IRInstruction> instructions;   // current function's growing list
    private int tempCounter = 0;
    private int labelCounter = 0;

    // ---------- scoping ----------
    // Mirrors semantic.SymbolTable's nested scopes, but maps a source name to a
    // unique IR name instead of a type. This stops two variables that share a
    // source name (e.g. shadowing in a nested block) from colliding into the
    // same IR name.
    private final Deque<Map<String, String>> scopes = new ArrayDeque<>();
    private int uniqueCounter = 0;

    public IRGenerator(Map<Expr, Type> expressionTypes) {
        this.expressionTypes = expressionTypes;
    }

    private void pushScope() {
        scopes.push(new HashMap<>());
    }

    private void popScope() {
        scopes.pop();
    }

    // creates a fresh unique IR name for a newly-declared source variable
    private String declareVar(String sourceName) {
        String uniqueName = (scopes.size() == 1) ? sourceName : sourceName + "$" + (++uniqueCounter);
        scopes.peek().put(sourceName, uniqueName);
        return uniqueName;
    }

    // finds the current IR name for a source variable, searching outward
    private String resolveVar(String sourceName) {
        for (Map<String, String> scope : scopes) {
            if (scope.containsKey(sourceName)) return scope.get(sourceName);
        }
        throw new IllegalStateException("Unresolved variable in IR generation: " + sourceName);
        // shouldn't happen: the type checker already guarantees this name exists
    }

    // ---------- entry point ----------

    public IRProgram generate(List<Stmt> statements) {
        List<IRFunction> functions = new ArrayList<>();
        List<IRInstruction> topLevel = new ArrayList<>();

        instructions = topLevel;
        pushScope();
        for (Stmt s : statements) {
            if (s instanceof Stmt.Function fn) {
                functions.add(generateFunction(fn));
            } else {
                genStmt(s);
            }
        }
        popScope();

        return new IRProgram(functions, topLevel);
    }

    private IRFunction generateFunction(Stmt.Function fn) {
        List<IRInstruction> previousInstructions = instructions;
        List<IRInstruction> functionInstructions = new ArrayList<>();
        instructions = functionInstructions;

        pushScope();
        List<String> paramNames = new ArrayList<>();
        for (Stmt.Param p : fn.params()) {
            String uniqueName = declareVar(p.name().lexeme());
            paramNames.add(uniqueName);
        }

        for (Stmt s : fn.body().statements()) {
            genStmt(s);
        }
        popScope();

        instructions = previousInstructions;
        return new IRFunction(fn.name().lexeme(), paramNames, functionInstructions);
    }

    // ---------- statements ----------

    private void genStmt(Stmt stmt) {
        switch (stmt) {
            case Stmt.Let let -> {
                IRValue value = genExpr(let.initialiser());
                String uniqueName = declareVar(let.name().lexeme());
                instructions.add(new IRInstruction.Copy(uniqueName, value));
            }
            case Stmt.ExprStmt e -> genExpr(e.expression());
            case Stmt.Block block -> {
                pushScope();
                for (Stmt s : block.statements()) {
                    genStmt(s);
                }
                popScope();
            }
            case Stmt.If ifStmt -> genIf(ifStmt);
            case Stmt.While whileStmt -> genWhile(whileStmt);
            case Stmt.Return ret -> {
                IRValue value = (ret.value() != null) ? genExpr(ret.value()) : null;
                instructions.add(new IRInstruction.Return(value));
            }
            case Stmt.Function fn -> { /* handled separately, see generate() */ }
        }
    }

    private void genIf(Stmt.If ifStmt) {
        IRValue cond = genExpr(ifStmt.condition());
        String thenLabel = newLabel();
        String endLabel = newLabel();

        if (ifStmt.elseBranch() == null) {
            instructions.add(new IRInstruction.CondJump(cond, thenLabel, endLabel));
            instructions.add(new IRInstruction.Label(thenLabel));
            genStmt(ifStmt.thenBranch());
            instructions.add(new IRInstruction.Label(endLabel));
        } else {
            String elseLabel = newLabel();
            instructions.add(new IRInstruction.CondJump(cond, thenLabel, elseLabel));
            instructions.add(new IRInstruction.Label(thenLabel));
            genStmt(ifStmt.thenBranch());
            instructions.add(new IRInstruction.Jump(endLabel));
            instructions.add(new IRInstruction.Label(elseLabel));
            genStmt(ifStmt.elseBranch());
            instructions.add(new IRInstruction.Label(endLabel));
        }
    }

    private void genWhile(Stmt.While whileStmt) {
        String loopStart = newLabel();
        String bodyLabel = newLabel();
        String endLabel = newLabel();

        instructions.add(new IRInstruction.Label(loopStart));
        IRValue cond = genExpr(whileStmt.condition());
        instructions.add(new IRInstruction.CondJump(cond, bodyLabel, endLabel));
        instructions.add(new IRInstruction.Label(bodyLabel));
        genStmt(whileStmt.body());
        instructions.add(new IRInstruction.Jump(loopStart));
        instructions.add(new IRInstruction.Label(endLabel));
    }

    // ---------- expressions ----------

    private IRValue genExpr(Expr expr) {
        return switch (expr) {
            case Expr.Literal lit -> new IRValue.Const(lit.value());
            case Expr.Binary b -> genBinary(b);
            case Expr.Unary u -> genUnary(u);
            case Expr.Variable v -> new IRValue.Temp(resolveVar(v.name().lexeme()));
            case Expr.Assign a -> {
                IRValue value = genExpr(a.value());
                String uniqueName = resolveVar(a.name().lexeme());   // must already exist
                instructions.add(new IRInstruction.Copy(uniqueName, value));
                yield new IRValue.Temp(uniqueName);
            }
            case Expr.Call c -> genCall(c);
        };
    }

    private IRValue genBinary(Expr.Binary b) {
        IRValue left = genExpr(b.left());
        IRValue right = genExpr(b.right());
        Type type = expressionTypes.get(b);   // the checker already computed this
        String op = mapOperator(b.operator(), type);

        String dest = newTemp();
        instructions.add(new IRInstruction.BinOp(dest, op, left, right));
        return new IRValue.Temp(dest);
    }

    private IRValue genUnary(Expr.Unary u) {
        IRValue operand = genExpr(u.right());
        String op = switch (u.operator().type()) {
            case MINUS -> "NEG";
            case BANG -> "NOT";
            default -> throw new IllegalStateException("Unhandled unary operator: " + u.operator().lexeme());
        };
        String dest = newTemp();
        instructions.add(new IRInstruction.UnaryOp(dest, op, operand));
        return new IRValue.Temp(dest);
    }

    private IRValue genCall(Expr.Call call) {
        String functionName = ((Expr.Variable) call.callee()).name().lexeme(); // type already verified

        List<IRValue> args = new ArrayList<>();
        for (Expr arg : call.arguments()) {
            args.add(genExpr(arg));
        }

        if (functionName.equals("print")) {
            String dest = newTemp();
            instructions.add(new IRInstruction.Call(dest, "print", args));
            return null;   // print returns Void; callers of a Void call shouldn't use the result
        }

        String dest = newTemp();
        instructions.add(new IRInstruction.Call(dest, functionName, args));
        return new IRValue.Temp(dest);
    }

    private String mapOperator(Token operator, Type type) {
        return switch (operator.type()) {
            case PLUS -> "ADD";
            case MINUS -> "SUB";
            case STAR -> "MUL";
            case SLASH -> "DIV";
            case PERCENT -> "MOD";

            case LESS -> "LESS";
            case LESS_EQUAL -> "LESS_EQUAL";
            case GREATER -> "GREATER";
            case GREATER_EQUAL -> "GREATER_EQUAL";
            case EQUAL_EQUAL -> "EQUAL";
            case BANG_EQUAL -> "NOT_EQUAL";

            case AND -> "AND";
            case OR -> "OR";

            default -> throw new IllegalStateException("Unhandled operator: " + operator.lexeme());
        };
    }

    // ---------- helpers ----------

    private String newTemp() {
        return "t" + (++tempCounter);
    }

    private String newLabel() {
        return "Block" + (labelCounter++);
    }
}