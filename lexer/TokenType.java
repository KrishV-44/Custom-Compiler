package lexer;

public enum TokenType {
    // Literals
    IDENTIFIER, INT_LITERAL, FLOAT_LITERAL, STRING_LITERAL,

    // Keywords & Logical Operators
    LET, FN, IF, ELSE, WHILE, RETURN, TRUE, FALSE, AND, OR,

    THIN_ARROW,

    // Operators
    PLUS, MINUS, STAR, SLASH, PERCENT,
    EQUAL, EQUAL_EQUAL, BANG, BANG_EQUAL,
    LESS, LESS_EQUAL, GREATER, GREATER_EQUAL,

    // Punctuation
    LPAREN, RPAREN, LBRACE, RBRACE, LBRACKET, RBRACKET,
    COMMA, COLON, SEMICOLON,

    EOF
}