package parser;

import ast.Expr;
import ast.Stmt;
import ast.Expr.*;
import ast.Stmt.*;
import lexer.Token;
import lexer.TokenType;
import java.util.ArrayList;
import java.util.List;

public class Parser {
    private final List<Token> tokens;
    private int current = 0;

    public Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    public List<Stmt> parseProgram() {
        List<Stmt> statements = new ArrayList<>();
        while (!isAtEnd()) {
            statements.add(declaration());
        }
        return statements;
    }


    // declaration -> functionDecl | statement
    private Stmt declaration() {
        if (match(TokenType.FN)) return functionDecl();
        return statement(); 
    }

    // statement -> letStmt | ifStmt | whileStmt | returnStmt | block | exprStmt
    private Stmt statement() { 
        if (match(TokenType.LET)) return letStmt();
        if (match(TokenType.IF)) return ifStmt();
        if (match(TokenType.WHILE)) return whileStmt();
        if (match(TokenType.RETURN)) return returnStmt();
        if (match(TokenType.LBRACE)) return block();
        return exprStmt(); 
    }

    // expression -> assignment
    private Expr expression() { 
        return assignment(); 
    }


    // ... one method per grammar rule ...

    private Stmt functionDecl() {
        Token name = consume(TokenType.IDENTIFIER, "Expected function name");
        consume(TokenType.LPAREN, "Expected '(' after function name");

        List<Param> params = new ArrayList<>();
        if (!check(TokenType.RPAREN)) {
            do {
                Token paramName = consume(TokenType.IDENTIFIER, "Expected parameter name");
                consume(TokenType.COLON, "Expected ':' after parameter name");
                Token paramType = consume(TokenType.IDENTIFIER, "Expected parameter type");
                params.add(new Param(paramName, paramType));
            } while (match(TokenType.COMMA));
        }

        consume(TokenType.RPAREN, "Expected ')' after parameters");
        consume(TokenType.THIN_ARROW, "Expected '->' after parameters");
        Token returnType = consume(TokenType.IDENTIFIER, "Expected return type");
        consume(TokenType.LBRACE, "Expected '{' before function body");

        Block body = block(); // block() already consumes the matching '}'
        return new Function(name, params, returnType, body);
    }

    // Statements
    private Stmt letStmt() {
        Token name = consume(TokenType.IDENTIFIER, "Expected variable name");
        Token typeAnnotation = null;
        
        if (match(TokenType.COLON)) {
            typeAnnotation = consume(TokenType.IDENTIFIER, "Expected type after ':'");
        }
        consume(TokenType.EQUAL, "Expected '=' after variable name");
        Expr initialiser = expression();
        consume(TokenType.SEMICOLON, "Expected ';' after variable declaration");
        return new Let(name, typeAnnotation, initialiser);
    }

    private Stmt ifStmt() {
        Token keyword = previous();
        consume(TokenType.LPAREN, "Expected '(' after 'if'");
        Expr condition = expression();
        consume(TokenType.RPAREN, "Expected ')' after condition");
        consume(TokenType.LBRACE, "Expected '{' before if-branch");

        Stmt thenBranch = block();
        Stmt elseBranch = null;
        if (match(TokenType.ELSE)) {
            if (match(TokenType.IF)) {
                elseBranch = ifStmt(); // supports `else if`
            } else {
                consume(TokenType.LBRACE, "Expected '{' before else-branch");
                elseBranch = block();
            }
        }
        return new If(keyword, condition, thenBranch, elseBranch);
    }

    private Stmt whileStmt() {
        Token keyword = previous();
        consume(TokenType.LPAREN, "Expected '(' after 'while'");
        Expr condition = expression();
        consume(TokenType.RPAREN, "Expected ')' after condition");
        consume(TokenType.LBRACE, "Expected '{' before while-body");
        Block body = block();
        return new While(keyword, condition, body);
    }

    private Stmt returnStmt() {
        Token keyword = previous();
        Expr value = null;

        if (!check(TokenType.SEMICOLON)) value = expression();

        consume(TokenType.SEMICOLON, "Expected ';' after return value");
        return new Return(keyword, value);
    }

    private Block block() {
        List<Stmt> statements = new ArrayList<>();
        while (!check(TokenType.RBRACE) && !isAtEnd()) {
            statements.add(declaration());
        }
        consume(TokenType.RBRACE, "Expected '}' after block");
        return new Block(statements);
    }

    private Stmt exprStmt() {
        Expr expr = expression();
        consume(TokenType.SEMICOLON, "Expected ';' after expression");
        return new ExprStmt(expr);
    }

    // Expressions
    private Expr assignment() {
        Expr expr = logicOr();
        if (match(TokenType.EQUAL)) {
            Token equals = previous();
            Expr value = assignment();      // right-associative: recurse, don't loop
            if (expr instanceof Variable v) {
                return new Assign(v.name(), value);
            }
            throw error(equals, "Invalid assignment target");
        }
        return expr;
    }

    private Expr logicOr() {
        Expr expr = logicAnd();
        while (match(TokenType.OR)) {
            Token operator = previous();
            Expr right = logicAnd();
            expr = new Binary(expr, operator, right);
        }
        return expr;
    }

    private Expr logicAnd() {
        Expr expr = equality();
        while (match(TokenType.AND)) {
            Token operator = previous();
            Expr right = equality();
            expr = new Binary(expr, operator, right);
        }
        return expr;
    }

    private Expr equality() {
        Expr expr = comparison();
        while (match(TokenType.EQUAL_EQUAL, TokenType.BANG_EQUAL)) {
            Token operator = previous();
            Expr right = comparison();
            expr = new Binary(expr, operator, right);
        }
        return expr;
    }

    private Expr comparison() {
        Expr expr = term();
        while (match(TokenType.LESS, TokenType.LESS_EQUAL, TokenType.GREATER, TokenType.GREATER_EQUAL)) {
            Token operator = previous();
            Expr right = term();
            expr = new Binary(expr, operator, right);
        }
        return expr;
    }

    private Expr term() {
        Expr expr = factor();
        while (match(TokenType.PLUS, TokenType.MINUS)) {
            Token operator = previous();
            Expr right = factor();
            expr = new Binary(expr, operator, right);
        }
        return expr;
    }

    private Expr factor() {
        Expr expr = unary();
        while (match(TokenType.STAR, TokenType.SLASH, TokenType.PERCENT)) {
            Token operator = previous();
            Expr right = unary();
            expr = new Binary(expr, operator, right);
        }
        return expr;
    }

    private Expr unary() {
        if (match(TokenType.BANG, TokenType.MINUS)) {
            Token operator = previous();
            Expr right = unary();            // recurse: handles `--x`, `!!x`
            return new Unary(operator, right);
        }
        return call();
    }

    private Expr call() {
        Expr expr = primary();
        while (match(TokenType.LPAREN)) {
            expr = finishCall(expr);
        }
        return expr;
    }

    private Expr finishCall(Expr callee) {
        List<Expr> arguments = new ArrayList<>();
        if (!check(TokenType.RPAREN)) {
            do {
                arguments.add(expression());
            } while (match(TokenType.COMMA));
        }
        Token paren = consume(TokenType.RPAREN, "Expected ')' after arguments");
        return new Call(callee, paren, arguments);
    }

    private Expr primary() {
        if (match(TokenType.INT_LITERAL, TokenType.FLOAT_LITERAL, TokenType.STRING_LITERAL)) return new Literal(previous().literal());
        if (match(TokenType.TRUE))  return new Literal(true);
        if (match(TokenType.FALSE)) return new Literal(false);
        if (match(TokenType.IDENTIFIER)) return new Variable(previous());
        if (match(TokenType.LPAREN)) {
            Expr expr = expression();
            consume(TokenType.RPAREN, "Expected ')' after expression");
            return expr;
        }
        throw error(peek(), "Expected expression");
    }

    // --- helpers, same idea as the lexer's ---
    private boolean match(TokenType... types) { 
        for (TokenType type : types) {
            if (check(type)) {
                advance();
                return true;
            }
        }
        return false; 
    }

    private boolean check(TokenType type) { 
        if (isAtEnd()) return false;
        return peek().type() == type;
    }

    private Token advance() {
        if (!isAtEnd()) current++;
        return previous();
    }

    private boolean isAtEnd() { return peek().type() == TokenType.EOF; }
    private Token peek() { return tokens.get(current); }
    private Token previous() { return tokens.get(current - 1); }

    private Token consume(TokenType type, String message) {
        if (check(type)) return advance(); 
        throw error(peek(), message); 
    }

    private RuntimeException error(Token token, String message) {
        return new RuntimeException("[line " + token.line() + "] " + message + " at '" + token.lexeme() + "'");
    }
}