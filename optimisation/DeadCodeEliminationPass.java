package optimisation;

import ir.IRInstruction;
import ir.IRValue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DeadCodeEliminationPass implements OptimisationPass {

    @Override
    public String name() {
        return "Dead Code Elimination";
    }

    @Override
    public List<IRInstruction> apply(List<IRInstruction> instructions) {
        Set<String> used = collectUsedNames(instructions);

        List<IRInstruction> result = new ArrayList<>();
        for (IRInstruction instr : instructions) {
            if (isDead(instr, used)) {
                continue;   // drop it
            }
            result.add(instr);
        }
        return result;
    }

    // True only for pure, side-effect-free instructions whose result is never read.
    // Call is never considered dead here, even if unused, since it may have side effects
    // (e.g. print). Labels, jumps, branches and returns have no "destination" and are
    // always kept.
    private boolean isDead(IRInstruction instr, Set<String> used) {
        return switch (instr) {
            case IRInstruction.BinOp b -> !used.contains(b.dest());
            case IRInstruction.UnaryOp u -> !used.contains(u.dest());
            case IRInstruction.Copy c -> !used.contains(c.dest());
            default -> false;
        };
    }

    // Every name referenced as an *operand* anywhere in the list — i.e. every place
    // a value is read rather than written. A destination that never shows up here
    // is a value nobody ever reads.
    private Set<String> collectUsedNames(List<IRInstruction> instructions) {
        Set<String> used = new HashSet<>();
        for (IRInstruction instr : instructions) {
            switch (instr) {
                case IRInstruction.BinOp b -> {
                    addIfTemp(b.left(), used);
                    addIfTemp(b.right(), used);
                }
                case IRInstruction.UnaryOp u -> addIfTemp(u.operand(), used);
                case IRInstruction.Copy c -> addIfTemp(c.src(), used);
                case IRInstruction.Call call -> {
                    for (IRValue arg : call.args()) addIfTemp(arg, used);
                }
                case IRInstruction.CondJump cj -> addIfTemp(cj.condition(), used);
                case IRInstruction.Return r -> {
                    if (r.value() != null) addIfTemp(r.value(), used);
                }
                case IRInstruction.Label l -> { /* no operands */ }
                case IRInstruction.Jump j -> { /* no operands */ }
            }
        }
        return used;
    }

    private void addIfTemp(IRValue value, Set<String> used) {
        if (value instanceof IRValue.Temp t) {
            used.add(t.name());
        }
    }
}