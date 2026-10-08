package lexer;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

import lexer.Token;
import lexer.TokenType;

public class Lexer {
    private final String source;
    private static final Map<String, TokenType> keywords = new HashMap<>();
    private final List<Token> tokens = new ArrayList<>();
    private int start = 0;    // start of the token being scanned
    private int current = 0;  // character we're looking at now
    private int line = 1;
    private int column = 1;
    private int startColumn = 1;  // column where the current token starts

    public Lexer(String source) { this.source = source; }

    static {
        keywords.put("let", TokenType.LET);
        keywords.put("fn", TokenType.FN);
        keywords.put("if", TokenType.IF);
        keywords.put("else", TokenType.ELSE);
        keywords.put("while", TokenType.WHILE);
        keywords.put("return", TokenType.RETURN);
        keywords.put("true", TokenType.TRUE);
        keywords.put("false", TokenType.FALSE);
        keywords.put("and", TokenType.AND);
        keywords.put("or", TokenType.OR);
    }

    public List<Token> scanTokens() {

        while (!isAtEnd(current)) {
            start = current;
            startColumn = column;
            scanToken();
        }
        tokens.add(new Token(TokenType.EOF, "", null, line, column));
        return tokens;
    }

    private void scanToken() {
        char c = getAndAdvance();
        switch (c) {
            case '(' -> addToken(TokenType.LPAREN, null); // Punctuation
            case ')' -> addToken(TokenType.RPAREN, null);
            case '[' -> addToken(TokenType.LBRACKET, null);
            case ']' -> addToken(TokenType.RBRACKET, null);
            case '{' -> addToken(TokenType.LBRACE, null);
            case '}' -> addToken(TokenType.RBRACE, null);
            case ',' -> addToken(TokenType.COMMA, null);
            case ':' -> addToken(TokenType.COLON, null);
            case ';' -> addToken(TokenType.SEMICOLON, null);

            case '+' -> addToken(TokenType.PLUS, null); // Operators
            case '-' -> {
                if (match('>')) {
                    addToken(TokenType.THIN_ARROW, null);
                } else {
                    addToken(TokenType.MINUS, null);
                }
            }
            case '%' -> addToken(TokenType.PERCENT, null);
            case '*' -> addToken(TokenType.STAR, null);
            case '/' -> addToken(TokenType.SLASH, null);
            case '=' -> {
                if (match('=')) {
                    addToken(TokenType.EQUAL_EQUAL, null);
                } else {
                    addToken(TokenType.EQUAL, null);
                }
            }
            case '>' -> {
                if (match('=')) {
                    addToken(TokenType.GREATER_EQUAL, null);
                } else {
                    addToken(TokenType.GREATER, null);
                }
            }
            case '<' -> {
                if (match('=')) {
                    addToken(TokenType.LESS_EQUAL, null);
                } else {
                    addToken(TokenType.LESS, null);
                }
            }
            case '!' -> {
                if (match('=')) {
                    addToken(TokenType.BANG_EQUAL, null);
                } else {
                    addToken(TokenType.BANG, null);
                }
            }
            case '"' -> string();
            case '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' -> number();
            case ' ', '\r', '\t', '\n' -> { }  // skip whitespace
            default -> {
                if (isAlpha(c)) {
                    identifier();
                } else {
                    throw new RuntimeException("[line " + line + "] Unexpected character '" + c + "'");
                }
            }
        }
    }

    private void identifier() {
        while (isAlphaNumeric(peek())) {
            getAndAdvance();
        }
        
        String text = source.substring(start, current);
        TokenType type = keywords.get(text); // Logic to get identifier or literal
        if (type == null) {
            type = TokenType.IDENTIFIER;
        }
        addToken(type, null);
    }

    private void number() {
        boolean isFloat = false;

        while (isDigit(peek())) {
            getAndAdvance();
        }

        // Look for a fractional part
        if (peek() == '.' && isDigit(peekNext())) {
            isFloat = true;
            getAndAdvance(); // Consume the '.'

            while (isDigit(peek())) {
                getAndAdvance();
            }
        }
        if (isFloat) { addToken(TokenType.FLOAT_LITERAL, Double.parseDouble(subStr(start,current))); }
        else { addToken(TokenType.INT_LITERAL, Integer.parseInt(subStr(start,current))); }
    }

    private char peekNext() {
        if (current + 1 >= source.length()) return '\0';
        return source.charAt(current + 1);
    }

    private void string() {
        while (peek() != '"' && !isAtEnd(current)) {
            //if (peek() == '\n') line++; // Support multi-line strings if needed
            getAndAdvance();
        }

        if (isAtEnd(current)) {
            throw new RuntimeException("[line " + line + "] Unterminated string.");
        }

        getAndAdvance(); // Consume the closing quote `"`

        // Optionally trim quotes from the stored literal value
        addToken(TokenType.STRING_LITERAL, subStr(start + 1, current - 1));
    }

    private char getAndAdvance() {
        char c = source.charAt(current);
        current++;
        if (c == '\n') {
            line++;
            column = 1;
        } else {
            column++;
        }
        return c;
    }

    private boolean match(char expected) {
        if (isAtEnd(current)) return false;
        if (source.charAt(current) != expected) return false;
        getAndAdvance();
        return true;
    }

    private String subStr(int a, int b) {
        return source.substring(a,b);
    }

    private void addToken(TokenType type, Object literal) {
        String text = source.substring(start, current);
        tokens.add(new Token(type, text, literal, line, startColumn));
    }

    private boolean isAtEnd(int index) {
        return index >= source.length();
    }

    private boolean isAlpha(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_';
    }

    private boolean isAlphaNumeric(char c) {
        return isAlpha(c) || isDigit(c);
    }

    private boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private char peek() {
        if (isAtEnd(current)) return '\0';
        return source.charAt(current);
    }

}