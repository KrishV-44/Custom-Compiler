package semantic;

import java.util.List;

public sealed interface Symbol {
    String name();

    record VariableSymbol(String name, Type type) implements Symbol {}
    record FunctionSymbol(String name, List<Type> paramTypes, Type returnType) implements Symbol {}
}