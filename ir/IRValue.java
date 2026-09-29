package ir;

public sealed interface IRValue {
    record Temp(String name) implements IRValue {}
    record Const(Object value) implements IRValue {}
}