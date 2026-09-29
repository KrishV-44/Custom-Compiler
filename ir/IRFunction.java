package ir;

import java.util.List;

public record IRFunction(String name, List<String> paramNames, List<IRInstruction> instructions) {}