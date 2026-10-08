package semantic;

import lexer.Token;

public record SemanticError(Token token, String message) {
    @Override
    public String toString() {
        String where = (token != null)
                ? "[line " + token.line() + ", col " + token.column() + "] "
                : "";
        return where + message;
    }
}