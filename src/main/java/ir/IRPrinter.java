package ir;

import java.util.List;

public class IRPrinter {

    public static String print(IRProgram program) {
        StringBuilder sb = new StringBuilder();

        for (IRFunction fn : program.functions()) {
            sb.append(fn.name()).append("(").append(String.join(", ", fn.paramNames())).append("):\n");
            printInstructions(fn.instructions(), sb);
            sb.append("\n");
        }

        if (!program.topLevel().isEmpty()) {
            sb.append("main:\n");
            printInstructions(program.topLevel(), sb);
        }

        return sb.toString();
    }

    private static void printInstructions(List<IRInstruction> instructions, StringBuilder sb) {
        for (IRInstruction instr : instructions) {
            // labels sit at the margin; everything else is indented, matching the roadmap's example
            if (instr instanceof IRInstruction.Label) {
                sb.append(formatInstruction(instr)).append("\n");
            } else {
                sb.append("    ").append(formatInstruction(instr)).append("\n");
            }
        }
    }

    private static String formatInstruction(IRInstruction instr) {
        return switch (instr) {
            case IRInstruction.BinOp b ->
                b.dest() + " = " + b.op() + " " + formatValue(b.left()) + ", " + formatValue(b.right());

            case IRInstruction.UnaryOp u ->
                u.dest() + " = " + u.op() + " " + formatValue(u.operand());

            case IRInstruction.Copy c ->
                c.dest() + " = " + formatValue(c.src());

            case IRInstruction.Call call ->
                call.dest() + " = CALL " + call.function() + ", " + formatArgs(call.args());

            case IRInstruction.Label l -> l.name() + ":";

            case IRInstruction.Jump j -> "JUMP " + j.label();

            case IRInstruction.CondJump cj ->
                "BRANCH " + formatValue(cj.condition()) + ", " + cj.thenLabel() + ", " + cj.elseLabel();

            case IRInstruction.Return r ->
                (r.value() != null) ? "RETURN " + formatValue(r.value()) : "RETURN";
        };
    }

    private static String formatValue(IRValue value) {
        return switch (value) {
            case IRValue.Temp t -> t.name();
            case IRValue.Const c -> formatConst(c.value());
        };
    }

    private static String formatConst(Object value) {
        if (value instanceof String s) return "\"" + s + "\"";
        return String.valueOf(value);
    }

    private static String formatArgs(List<IRValue> args) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < args.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(formatValue(args.get(i)));
        }
        return sb.toString();
    }
}