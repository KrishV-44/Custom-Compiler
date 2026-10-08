package lexer;


/*
type: what kind of token it is
lexeme: the exact text from the source ("10", "let", "==")
literal: the parsed value for literals (Integer 10, String "hello" without quotes), otherwise null
line, column: for error messages later
*/
public record Token(TokenType type, String lexeme, Object literal, int line, int column) {}