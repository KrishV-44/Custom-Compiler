# Language Specification (v0.1)

A small, statically typed language. This document is the reference for the lexer, parser and type checker. If the code and this file disagree, fix one of them.

## 1. Example

```text
let name = "Lexer Test";

fn factorial(n: Int) -> Int {
    if (n <= 1) {
        return 1;
    }
    return n * factorial(n - 1);
}

let result = factorial(10);
print(result);
```

## 2. Design decisions

- **Variables:** `let name = value;` with the type inferred from the value. An optional annotation is allowed: `let x: Float = 3.0;`
- **Functions:** parameter types and return type are always written explicitly: `fn add(a: Int, b: Int) -> Int { ... }`
- **Statements** end with `;`. Blocks use `{ }`.
- **Conditions** are wrapped in parentheses: `if (x > 5) { ... }`
- **Logic operators** are words: `and`, `or`, and `!` for not. There is no `&&` or `||`.
- **Comments:** not supported yet.
- **Built-ins:** `print(value)` is a built-in function, not a keyword.
- **Not in v0.1:** arrays, closures, generics, `break`/`continue`, `for` loops.

## 3. Lexical grammar (what the lexer produces)

### Keywords

`let` `fn` `if` `else` `while` `return` `true` `false` `and` `or`

### Literals and identifiers

| Kind | Rule | Examples | Literal value |
|---|---|---|---|
| Identifier | letter or `_`, then letters, digits, `_` | `x`, `my_var`, `Int` | none |
| Int literal | one or more digits | `0`, `42` | `Integer` |
| Float literal | digits `.` digits | `3.14`, `0.5` | `Double` |
| String literal | `"` any characters except `"` `"` | `"hi"` | text without quotes |

Notes:
- `5.` and `.5` are not floats.
- Type names (`Int`, `Float`, `Bool`, `String`, `Void`) are ordinary identifiers. The parser and type checker recognise them.
- Negative numbers are not literals: `-5` is the operator `-` applied to `5`.

### Operators and punctuation

| Token | Symbol | Token | Symbol |
|---|---|---|---|
| `PLUS` | `+` | `LPAREN` / `RPAREN` | `(` `)` |
| `MINUS` | `-` | `LBRACE` / `RBRACE` | `{` `}` |
| `STAR` | `*` | `LBRACKET` / `RBRACKET` | `[` `]` |
| `SLASH` | `/` | `COMMA` | `,` |
| `PERCENT` | `%` | `COLON` | `:` |
| `EQUAL` | `=` | `SEMICOLON` | `;` |
| `EQUAL_EQUAL` | `==` | `THIN_ARROW` | `->` |
| `BANG` | `!` | `BANG_EQUAL` | `!=` |
| `LESS` / `LESS_EQUAL` | `<` `<=` | `GREATER` / `GREATER_EQUAL` | `>` `>=` |

Whitespace (space, tab, newline) separates tokens and is otherwise ignored.

## 4. Syntax grammar (what the parser accepts)

Notation: `*` zero or more, `+` one or more, `?` optional, `|` alternative, quoted text is a literal token, UPPERCASE is a token type.

```text
program       → declaration* EOF

declaration   → functionDecl
              | statement

functionDecl  → "fn" IDENTIFIER "(" parameters? ")" "->" type block
parameters    → parameter ( "," parameter )*
parameter     → IDENTIFIER ":" type
type          → IDENTIFIER            // Int, Float, Bool, String, Void

statement     → letStmt
              | ifStmt
              | whileStmt
              | returnStmt
              | block
              | exprStmt

letStmt       → "let" IDENTIFIER ( ":" type )? "=" expression ";"
ifStmt        → "if" "(" expression ")" block ( "else" ( ifStmt | block ) )?
whileStmt     → "while" "(" expression ")" block
returnStmt    → "return" expression? ";"
block         → "{" declaration* "}"
exprStmt      → expression ";"

expression    → assignment
assignment    → IDENTIFIER "=" assignment
              | logicOr
logicOr       → logicAnd ( "or" logicAnd )*
logicAnd      → equality ( "and" equality )*
equality      → comparison ( ( "==" | "!=" ) comparison )*
comparison    → term ( ( ">" | ">=" | "<" | "<=" ) term )*
term          → factor ( ( "+" | "-" ) factor )*
factor        → unary ( ( "*" | "/" | "%" ) unary )*
unary         → ( "!" | "-" ) unary
              | call
call          → primary ( "(" arguments? ")" )*
arguments     → expression ( "," expression )*
primary       → INT_LITERAL | FLOAT_LITERAL | STRING_LITERAL
              | "true" | "false"
              | IDENTIFIER
              | "(" expression ")"
```

### Precedence (highest to lowest)

| Level | Operators | Associativity |
|---|---|---|
| 1 | function call `f(x)` | left |
| 2 | unary `!` `-` | right |
| 3 | `*` `/` `%` | left |
| 4 | `+` `-` | left |
| 5 | `<` `<=` `>` `>=` | left |
| 6 | `==` `!=` | left |
| 7 | `and` | left |
| 8 | `or` | left |
| 9 | `=` (assignment) | right |

Each grammar rule above is one precedence level, so the parser is one method per rule (recursive descent).

## 5. Types

| Type | Values | Notes |
|---|---|---|
| `Int` | whole numbers | 32-bit |
| `Float` | decimal numbers | 64-bit double |
| `Bool` | `true`, `false` | |
| `String` | text | |
| `Void` | none | function return type only |

### Typing rules (v0.1)

- `let x = expr;` gives `x` the type of `expr`. With an annotation, `expr` must match it.
- Arithmetic (`+ - * / %`) needs both operands `Int` or both `Float`. `+` also joins two `String`s.
- Comparisons `< <= > >=` need two numbers of the same type and give `Bool`.
- `==` and `!=` need two operands of the same type and give `Bool`.
- `and`, `or`, `!` need `Bool` operands and give `Bool`.
- `if` and `while` conditions must be `Bool`.
- Function calls must pass the right number of arguments, each matching its parameter type.
- Every `return` must match the declared return type. A `Void` function returns nothing.
- Assigning to a variable that was never declared is an error.
- Variables are block-scoped. Inner scopes may shadow outer variables.

### Open decisions (settle these before the type checker)

- Should `Int` implicitly widen to `Float` (`let y: Float = 3;`)? The simplest rule is **no implicit conversion**, so that line is an error.
- Does integer `/` truncate (`7 / 2` is `3`)? The simplest rule is yes, as in Java.
- Are function declarations allowed inside blocks, or only at top level? The grammar currently allows both.
- Should top-level statements run directly, or should execution start from a `main` function? Currently statements run top to bottom.

## 6. Errors

| Stage | Example | Message shape |
|---|---|---|
| Lexer | `@`, unterminated string | `[line 3, col 7] Unexpected character '@'` |
| Parser | missing `;` | `[line 3, col 12] Expected ';' after expression` |
| Type checker | `let x: Int = "hi";` | `[line 7] Type error: expected Int, found String` |

## 7. Complete example programs

```text
// Not valid yet: comments are not supported.
```

```text
let a = 10;
let b = 3.5;
let ok = true and a > 5;

fn max(x: Int, y: Int) -> Int {
    if (x > y) {
        return x;
    } else {
        return y;
    }
}

fn countdown(n: Int) -> Void {
    while (n > 0) {
        print(n);
        n = n - 1;
    }
}

print(max(a, 4));
countdown(3);
```
