package semantic;

import java.util.HashMap;
import java.util.Map;

public class SymbolTable {
    private final SymbolTable enclosing;
    private final Map<String, Symbol> symbols = new HashMap<>();

    public SymbolTable() { this(null); }
    public SymbolTable(SymbolTable enclosing) { this.enclosing = enclosing; }

    // false if the name already exists in THIS scope (redeclaration)
    public boolean declare(Symbol symbol) {
        if (symbols.containsKey(symbol.name())) return false;
        symbols.put(symbol.name(), symbol);
        return true;
    }

    // searches this scope, then outward. null if not found.
    public Symbol lookup(String name) {
        Symbol s = symbols.get(name);
        if (s != null) return s;
        return enclosing != null ? enclosing.lookup(name) : null;
    }
}