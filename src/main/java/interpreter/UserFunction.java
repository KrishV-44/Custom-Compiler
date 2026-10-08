package interpreter;

import ast.Stmt;
import java.util.List;

public class UserFunction implements Callable {
    private final Stmt.Function declaration;
    private final Environment closure;

    public UserFunction(Stmt.Function declaration, Environment closure) {
        this.declaration = declaration;
        this.closure = closure;
    }

    @Override
    public Object call(Interpreter interpreter, List<Object> arguments) {
        Environment functionEnv = new Environment(closure);

        List<Stmt.Param> params = declaration.params();
        for (int i = 0; i < params.size(); i++) {
            functionEnv.define(params.get(i).name().lexeme(), arguments.get(i));
        }

        try {
            interpreter.executeBlock(declaration.body().statements(), functionEnv);
        } catch (ReturnException returnValue) {
            return returnValue.value;
        }

        return null;   // fell off the end with no explicit return
    }

    @Override
    public int arity() {
        return declaration.params().size();
    }

    public String name() {
        return declaration.name().lexeme();
    }
}