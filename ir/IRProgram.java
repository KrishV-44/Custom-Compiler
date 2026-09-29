package ir;

import java.util.List;

public record IRProgram(List<IRFunction> functions, List<IRInstruction> topLevel) {}