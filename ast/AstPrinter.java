package ast;

import java.util.List;

public class AstPrinter {

    public static void printTree(List<Stmt> statements) {
        for (Stmt stmt : statements) {
            printStmt(stmt, 0);
        }
    }

    private static void printStmt(Stmt stmt, int indent) {
        String pad = "  ".repeat(indent);
        switch (stmt) {
            case Stmt.Let let -> {
                System.out.println(pad + "Let (" + let.name().lexeme() + ")");
                if (let.initialiser() != null) {
                    printExpr(let.initialiser(), indent + 1);
                }
            }
            case Stmt.Function fn -> {
                System.out.println(pad + "Function Definition (" + fn.name().lexeme() + ")");
                System.out.println(pad + "  Body:");
                printStmt(fn.body(), indent + 2);
            }
            case Stmt.Block block -> {
                System.out.println(pad + "Block {");
                for (Stmt s : block.statements()) {
                    printStmt(s, indent + 1);
                }
                System.out.println(pad + "}");
            }
            case Stmt.If ifStmt -> {
                System.out.println(pad + "If Statement");
                System.out.println(pad + "  Condition:");
                printExpr(ifStmt.condition(), indent + 2);
                System.out.println(pad + "  Then Branch:");
                printStmt(ifStmt.thenBranch(), indent + 2);
                if (ifStmt.elseBranch() != null) {
                    System.out.println(pad + "  Else Branch:");
                    printStmt(ifStmt.elseBranch(), indent + 2);
                }
            }
            case Stmt.While whileStmt -> {
                System.out.println(pad + "While Loop");
                System.out.println(pad + "  Condition:");
                printExpr(whileStmt.condition(), indent + 2);
                System.out.println(pad + "  Body:");
                printStmt(whileStmt.body(), indent + 2);
            }
            case Stmt.Return ret -> {
                System.out.println(pad + "Return");
                if (ret.value() != null) {
                    printExpr(ret.value(), indent + 1);
                }
            }
            case Stmt.ExprStmt exprStmt -> {
                System.out.println(pad + "Expression Statement:");
                printExpr(exprStmt.expression(), indent + 1);
            }
        }
    }

    private static void printExpr(Expr expr, int indent) {
        String pad = "  ".repeat(indent);
        switch (expr) {
            case Expr.Literal lit -> 
                System.out.println(pad + "Literal: " + lit.value());
            case Expr.Variable var -> 
                System.out.println(pad + "Variable: " + var.name().lexeme());
            case Expr.Binary bin -> {
                System.out.println(pad + "Binary Operator [" + bin.operator().lexeme() + "]");
                printExpr(bin.left(), indent + 1);
                printExpr(bin.right(), indent + 1);
            }
            case Expr.Call call -> {
                System.out.println(pad + "Function Call");
                System.out.println(pad + "  Callee:");
                printExpr(call.callee(), indent + 2);
                System.out.println(pad + "  Arguments:");
                for (Expr arg : call.arguments()) {
                    printExpr(arg, indent + 2);
                }
            }
            case Expr.Assign assign -> {
                System.out.println(pad + "Assignment (" + assign.name().lexeme() + ")");
                printExpr(assign.value(), indent + 1);
            }
            case Expr.Unary unary -> {
                System.out.println(pad + "Unary Operator [" + unary.operator().lexeme() + "]");
                printExpr(unary.right(), indent + 1);
            }
        }
    }
}