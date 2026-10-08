package interpreter;

import ast.Expr;
import ast.Stmt;
import lexer.Token;
import lexer.TokenType;
import java.util.List;
import java.util.ArrayList;

public class Interpreter {
    private Environment environment = new Environment();

    public Interpreter() {
        environment.define("print", new Callable() {
            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                System.out.println(stringify(arguments.get(0)));
                return null;
            }

            @Override
            public int arity() {
                return 1;
            }
        });
    }

    private String stringify(Object value) {
        if (value == null) return "null";
        return value.toString();
    }

    public void interpret(List<Stmt> statements) {
        try {
            for (Stmt stmt : statements) {
                execute(stmt);
            }
        } catch (RuntimeError e) {
            String location = (e.token != null) ? " [line " + e.token.line() + "]" : "";
            System.out.println("Runtime error: " + e.getMessage() + location);
        }
    }

    private void execute(Stmt stmt) {
        switch (stmt) {
            case Stmt.Let let -> {
                Object value = evaluate(let.initialiser());
                environment.define(let.name().lexeme(), value);
            }
            case Stmt.ExprStmt exprStmt -> evaluate(exprStmt.expression());
            case Stmt.Block block -> executeBlock(block.statements(), new Environment(environment));
            case Stmt.If ifStmt -> {
                if (isTruthy(evaluate(ifStmt.condition()))) {
                    execute(ifStmt.thenBranch());
                } else if (ifStmt.elseBranch() != null) {
                    execute(ifStmt.elseBranch());
                }
            }
            case Stmt.While whileStmt -> {
                while (isTruthy(evaluate(whileStmt.condition()))) {
                    execute(whileStmt.body());
                }
            }
            case Stmt.Return ret -> {
                Object value = (ret.value() != null) ? evaluate(ret.value()) : null;
                throw new ReturnException(value);
            }
            case Stmt.Function fn -> environment.define(fn.name().lexeme(), new UserFunction(fn, environment));
        }
    }

    void executeBlock(List<Stmt> statements, Environment newEnvironment) {
        Environment previous = this.environment;
        try {
            this.environment = newEnvironment;
            for (Stmt stmt : statements) {
                execute(stmt);
            }
        } finally {
            this.environment = previous;
        }
    }

    private boolean isTruthy(Object value) {
        if (value instanceof Boolean b) return b;
        throw new RuntimeError(null, "Condition must be a Bool.");
    }

    public Object evaluate(Expr expr) {
        return switch (expr) {
            case Expr.Literal lit -> lit.value();
            case Expr.Binary bin -> evaluateBinary(bin);
            case Expr.Unary un -> evaluateUnary(un);
            case Expr.Variable v -> environment.get(v.name());
            case Expr.Assign a -> {
                Object value = evaluate(a.value());
                environment.assign(a.name(), value);
                yield value;
            }
            case Expr.Call call -> {
                Object callee = evaluate(call.callee());

                List<Object> arguments = new ArrayList<>();
                for (Expr arg : call.arguments()) {
                    arguments.add(evaluate(arg));
                }

                if (!(callee instanceof Callable function)) {
                    throw new RuntimeError(call.paren(), "Can only call defined functions.");
                }

                if (arguments.size() != function.arity()) {
                    throw new RuntimeError(call.paren(),
                        "Expected " + function.arity() + " arguments but got " + arguments.size() + ".");
                }

                yield function.call(this, arguments);
            }
        };
    }

    private Object evaluateBinary(Expr.Binary bin) {
        Object left = evaluate(bin.left());
        Object right = evaluate(bin.right());
        Token op = bin.operator();

        return switch (op.type()) {
            case PLUS -> {
                if (left instanceof Integer && right instanceof Integer) {
                    yield (Integer) left + (Integer) right;
                }
                if (left instanceof Double && right instanceof Double) {
                    yield (Double) left + (Double) right;
                }
                if (left instanceof String && right instanceof String) {
                    yield (String) left + (String) right;
                }
                throw new RuntimeError(op, "Operands must both be Int, both Float, or both String.");
            }
            case MINUS -> numericOp(left, right, op, (a, b) -> a - b, (a, b) -> a - b);
            case STAR -> numericOp(left, right, op, (a, b) -> a * b, (a, b) -> a * b);
            case SLASH -> {
                if (left instanceof Integer && right instanceof Integer) {
                    if ((Integer) right == 0) throw new RuntimeError(op, "Division by zero.");
                    yield (Integer) left / (Integer) right;
                }
                if (left instanceof Double && right instanceof Double) {
                    yield (Double) left / (Double) right;
                }
                throw new RuntimeError(op, "Operands must both be Int or both be Float.");
            }
            case PERCENT -> numericOp(left, right, op, (a, b) -> a % b, (a, b) -> a % b);

            case LESS -> numericCompare(left, right, op, (a, b) -> a < b, (a, b) -> a < b);
            case LESS_EQUAL -> numericCompare(left, right, op, (a, b) -> a <= b, (a, b) -> a <= b);
            case GREATER -> numericCompare(left, right, op, (a, b) -> a > b, (a, b) -> a > b);
            case GREATER_EQUAL -> numericCompare(left, right, op, (a, b) -> a >= b, (a, b) -> a >= b);

            case EQUAL_EQUAL -> isEqual(left, right);
            case BANG_EQUAL -> !isEqual(left, right);

            case AND -> isTruthy(left) && isTruthy(right);
            case OR -> isTruthy(left) || isTruthy(right);

            default -> throw new RuntimeError(op, "Unsupported operator: " + op.lexeme());
        };
    }

    private interface IntOp { int apply(int a, int b); }
    private interface DoubleOp { double apply(double a, double b); }
    private interface IntCompare { boolean apply(int a, int b); }
    private interface DoubleCompare { boolean apply(double a, double b); }

    private Object numericOp(Object left, Object right, Token op, IntOp intOp, DoubleOp doubleOp) {
        if (left instanceof Integer && right instanceof Integer) {
            return intOp.apply((Integer) left, (Integer) right);
        }
        if (left instanceof Double && right instanceof Double) {
            return doubleOp.apply((Double) left, (Double) right);
        }
        throw new RuntimeError(op, "Operands must both be Int or both be Float.");
    }

    private Object numericCompare(Object left, Object right, Token op, IntCompare intOp, DoubleCompare doubleOp) {
        if (left instanceof Integer && right instanceof Integer) {
            return intOp.apply((Integer) left, (Integer) right);
        }
        if (left instanceof Double && right instanceof Double) {
            return doubleOp.apply((Double) left, (Double) right);
        }
        throw new RuntimeError(op, "Operands must both be Int or both be Float.");
    }

    private boolean isEqual(Object a, Object b) {
        if (a == null && b == null) return true;
        if (a == null) return false;
        return a.equals(b);
    }

    private Object evaluateUnary(Expr.Unary un) {
        Object right = evaluate(un.right());
        Token op = un.operator();

        return switch (op.type()) {
            case MINUS -> {
                if (right instanceof Integer) yield -(Integer) right;
                if (right instanceof Double) yield -(Double) right;
                throw new RuntimeError(op, "Operand must be a number.");
            }
            case BANG -> !isTruthy(right);
            default -> throw new RuntimeError(op, "Unsupported unary operator: " + op.lexeme());
        };
    }
}