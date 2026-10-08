package ir;

import semantic.Type;
import java.util.List;
import java.util.Map;

public record IRProgram(List<IRFunction> functions, List<IRInstruction> topLevel, Map<String, Type> topLevelVarTypes) {}