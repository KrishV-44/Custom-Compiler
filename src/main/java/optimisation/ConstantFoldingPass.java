package optimisation;

import ir.IRInstruction;
import ir.IRValue;

import java.util.ArrayList;
import java.util.List;

public class ConstantFoldingPass implements OptimisationPass {

    @Override
    public String name() {
        return "Constant Folding";
    }

    @Override
    public List<IRInstruction> apply(List<IRInstruction> instructions) {
        List<IRInstruction> result = new ArrayList<>();

        for (IRInstruction instr : instructions) {
            result.add(foldInstruction(instr));
        }

        return result;
    }

    private IRInstruction foldInstruction(IRInstruction instr) {
        return switch (instr) {
            case IRInstruction.BinOp b -> foldBinOp(b);
            case IRInstruction.UnaryOp u -> foldUnaryOp(u);
            default -> instr;   // Copy, Call, Label, Jump, CondJump, Return: nothing to fold
        };
    }

    private IRInstruction foldBinOp(IRInstruction.BinOp b) {
        if (!(b.left() instanceof IRValue.Const left) || !(b.right() instanceof IRValue.Const right)) {
            return b;   // at least one operand isn't a constant yet, can't fold
        }

        Object result = switch (b.op()) {
            case "ADD" -> add(left.value(), right.value());
            case "SUB" -> arithmetic(left.value(), right.value(), (x, y) -> x - y, (x, y) -> x - y);
            case "MUL" -> arithmetic(left.value(), right.value(), (x, y) -> x * y, (x, y) -> x * y);
            case "DIV" -> divide(left.value(), right.value());
            case "MOD" -> arithmetic(left.value(), right.value(), (x, y) -> x % y, (x, y) -> x % y);

            case "LESS" -> compare(left.value(), right.value(), (x, y) -> x < y, (x, y) -> x < y);
            case "LESS_EQUAL" -> compare(left.value(), right.value(), (x, y) -> x <= y, (x, y) -> x <= y);
            case "GREATER" -> compare(left.value(), right.value(), (x, y) -> x > y, (x, y) -> x > y);
            case "GREATER_EQUAL" -> compare(left.value(), right.value(), (x, y) -> x >= y, (x, y) -> x >= y);

            case "EQUAL" -> left.value().equals(right.value());
            case "NOT_EQUAL" -> !left.value().equals(right.value());

            case "AND" -> (Boolean) left.value() && (Boolean) right.value();
            case "OR" -> (Boolean) left.value() || (Boolean) right.value();

            default -> null;   // unrecognised op — leave it alone rather than guess
        };

        if (result == null) return b;
        return new IRInstruction.Copy(b.dest(), new IRValue.Const(result));
    }

    private IRInstruction foldUnaryOp(IRInstruction.UnaryOp u) {
        if (!(u.operand() instanceof IRValue.Const operand)) {
            return u;
        }

        Object result = switch (u.op()) {
            case "NEG" -> negate(operand.value());
            case "NOT" -> !(Boolean) operand.value();
            default -> null;
        };

        if (result == null) return u;
        return new IRInstruction.Copy(u.dest(), new IRValue.Const(result));
    }

    // ---------- typed helpers ----------
    // Every op must handle both Int (Integer) and Float (Double) operands, since
    // the source language has both and the checker already guarantees left/right
    // share the same type here.

    private interface IntOp { int apply(int a, int b); }
    private interface DoubleOp { double apply(double a, double b); }
    private interface IntCompare { boolean apply(int a, int b); }
    private interface DoubleCompare { boolean apply(double a, double b); }

    private Object add(Object left, Object right) {
        if (left instanceof Integer a && right instanceof Integer b) return a + b;
        if (left instanceof Double a && right instanceof Double b) return a + b;
        if (left instanceof String a && right instanceof String b) return a + b;
        return null;
    }

    private Object arithmetic(Object left, Object right, IntOp intOp, DoubleOp doubleOp) {
        if (left instanceof Integer a && right instanceof Integer b) return intOp.apply(a, b);
        if (left instanceof Double a && right instanceof Double b) return doubleOp.apply(a, b);
        return null;
    }

    private Object divide(Object left, Object right) {
        if (left instanceof Integer a && right instanceof Integer b) {
            if (b == 0) return null;   // don't fold a division by zero. Let it fail at runtime instead
            return a / b;
        }
        if (left instanceof Double a && right instanceof Double b) {
            return a / b;
        }
        return null;
    }

    private Object compare(Object left, Object right, IntCompare intOp, DoubleCompare doubleOp) {
        if (left instanceof Integer a && right instanceof Integer b) return intOp.apply(a, b);
        if (left instanceof Double a && right instanceof Double b) return doubleOp.apply(a, b);
        return null;
    }

    private Object negate(Object value) {
        if (value instanceof Integer i) return -i;
        if (value instanceof Double d) return -d;
        return null;
    }
}