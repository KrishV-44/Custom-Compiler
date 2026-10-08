package ir;

import semantic.Type;
import java.util.List;
import java.util.Map;

public record IRFunction(String name, List<String> paramNames, List<IRInstruction> instructions, Map<String, Type> varTypes) {}