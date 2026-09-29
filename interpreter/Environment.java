package interpreter;

import lexer.Token;
import java.util.HashMap;
import java.util.Map;

public class Environment {
    private final Environment enclosing;   // null for the global/outermost scope
    private final Map<String, Object> values = new HashMap<>();

    public Environment() {
        this.enclosing = null;
    }

    public Environment(Environment enclosing) {
        this.enclosing = enclosing;
    }

    public void define(String name, Object value) {
        values.put(name, value);   // let x = ...;  always creates in THIS scope
    }

    public Object get(Token name) {
        if (values.containsKey(name.lexeme())) {
            return values.get(name.lexeme());
        }
        if (enclosing != null) {
            return enclosing.get(name);   // not here? ask the outer scope
        }
        throw new RuntimeError(name, "Undefined variable '" + name.lexeme() + "'.");
    }

    public void assign(Token name, Object value) {
        if (values.containsKey(name.lexeme())) {
            values.put(name.lexeme(), value);
            return;
        }
        if (enclosing != null) {
            enclosing.assign(name, value);   // x = ...; must find an EXISTING x
            return;
        }
        throw new RuntimeError(name, "Undefined variable '" + name.lexeme() + "'.");
    }
}