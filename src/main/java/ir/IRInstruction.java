package ir;

public sealed interface IRInstruction {
    record BinOp(String dest, String op, IRValue left, IRValue right) implements IRInstruction {}
    // t1 = ADD a, b

    record UnaryOp(String dest, String op, IRValue operand) implements IRInstruction {}
    // t1 = NEG a

    record Copy(String dest, IRValue src) implements IRInstruction {}
    // t1 = a (used for assignment, and for turning a value into a temp)

    record Call(String dest, String function, java.util.List<IRValue> args) implements IRInstruction {}
    // t1 = CALL factorial, t0

    record Label(String name) implements IRInstruction {}
    // Block0:

    record Jump(String label) implements IRInstruction {}
    // JUMP Block3

    record CondJump(IRValue condition, String thenLabel, String elseLabel) implements IRInstruction {}
    // BRANCH t1, Block1, Block2

    record Return(IRValue value) implements IRInstruction {}
    // RETURN t2 (value may be null for a Void return)
}