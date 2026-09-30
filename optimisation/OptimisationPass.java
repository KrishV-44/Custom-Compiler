package optimisation;

import ir.IRInstruction;
import java.util.List;

public interface OptimisationPass {
    List<IRInstruction> apply(List<IRInstruction> instructions);
    String name();   // for before/after debug output
}