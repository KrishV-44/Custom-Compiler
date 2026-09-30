package optimisation;

import ir.IRFunction;
import ir.IRProgram;
import ir.IRInstruction;
import ir.IRPrinter;
import java.util.ArrayList;
import java.util.List;

public class Optimiser {
    private final List<OptimisationPass> passes;

    public Optimiser(List<OptimisationPass> passes) {
        this.passes = passes;
    }

    public IRProgram optimise(IRProgram program) {
        IRProgram current = program;
        for (int i = 0; i < 5; i++) {   // fixed cap avoids any theoretical infinite loop
            IRProgram next = runOnce(current);
            if (IRPrinter.print(next).equals(IRPrinter.print(current))) {
                return next;   // no change since last round - reached a fixed point
            }
            current = next;
        }
        return current;
    }

    private IRProgram runOnce(IRProgram program) {
        List<IRFunction> newFunctions = new ArrayList<>();
        for (IRFunction fn : program.functions()) {
            newFunctions.add(new IRFunction(fn.name(), fn.paramNames(), runPasses(fn.instructions())));
        }
        List<IRInstruction> newTopLevel = runPasses(program.topLevel());
        return new IRProgram(newFunctions, newTopLevel);
    }

    private List<IRInstruction> runPasses(List<IRInstruction> instructions) {
        List<IRInstruction> current = instructions;
        for (OptimisationPass pass : passes) {
            current = pass.apply(current);
        }
        return current;
    }
}