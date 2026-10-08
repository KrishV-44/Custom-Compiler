package optimisation;

import ir.IRInstruction;
import ir.IRValue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConstantPropagationPass implements OptimisationPass {

    @Override
    public String name() {
        return "Constant Propagation";
    }

    @Override
    public List<IRInstruction> apply(List<IRInstruction> instructions) {
        List<IRInstruction> result = new ArrayList<>();
        // maps a temp/variable name to the constant value it's currently known to hold
        Map<String, IRValue.Const> known = new HashMap<>();

        for (IRInstruction instr : instructions) {
            IRInstruction substituted = substitute(instr, known);
            result.add(substituted);
            updateKnown(substituted, known);
        }

        return result;
    }

    // Replace any Temp operand with its known constant, if we have one.
    // Only rewrites operands - this pass never folds; ConstantFoldingPass handles that,
    // typically run again afterward so newly-substituted constants get folded too.
    private IRInstruction substitute(IRInstruction instr, Map<String, IRValue.Const> known) {
        return switch (instr) {
            case IRInstruction.BinOp b ->
                new IRInstruction.BinOp(b.dest(), b.op(), resolve(b.left(), known), resolve(b.right(), known));

            case IRInstruction.UnaryOp u ->
                new IRInstruction.UnaryOp(u.dest(), u.op(), resolve(u.operand(), known));

            case IRInstruction.Copy c ->
                new IRInstruction.Copy(c.dest(), resolve(c.src(), known));

            case IRInstruction.Call call -> {
                List<IRValue> newArgs = new ArrayList<>();
                for (IRValue arg : call.args()) {
                    newArgs.add(resolve(arg, known));
                }
                yield new IRInstruction.Call(call.dest(), call.function(), newArgs);
            }

            case IRInstruction.CondJump cj ->
                new IRInstruction.CondJump(resolve(cj.condition(), known), cj.thenLabel(), cj.elseLabel());

            case IRInstruction.Return r ->
                new IRInstruction.Return(r.value() != null ? resolve(r.value(), known) : null);

            case IRInstruction.Label l -> l;
            case IRInstruction.Jump j -> j;
        };
    }

    private IRValue resolve(IRValue value, Map<String, IRValue.Const> known) {
        if (value instanceof IRValue.Temp t && known.containsKey(t.name())) {
            return known.get(t.name());
        }
        return value;
    }

    // After emitting an instruction, record or invalidate what we know about its destination.
    private void updateKnown(IRInstruction instr, Map<String, IRValue.Const> known) {
        switch (instr) {
            case IRInstruction.Copy c -> {
                if (c.src() instanceof IRValue.Const constVal) {
                    known.put(c.dest(), constVal);
                } else {
                    known.remove(c.dest());   // now holds a runtime value, no longer known
                }
            }
            case IRInstruction.BinOp b -> known.remove(b.dest());
            case IRInstruction.UnaryOp u -> known.remove(u.dest());
            case IRInstruction.Call call -> known.remove(call.dest());

            case IRInstruction.Label l -> known.clear();
            // A label is a jump target. Instructions from elsewhere can land here with
            // different values in scope, so nothing "known" before it can be trusted
            // after it without real control-flow analysis. Clearing is the safe choice.

            default -> { /* Jump, CondJump, Return: no destination, nothing to update */ }
        }
    }
}