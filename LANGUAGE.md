# Language Specification

A small, statically typed language, compiled to JVM bytecode. This is the
reference for the lexer, parser, type checker, and code generator — if the
code and this file disagree, fix one of them.

## 1. Example

```text
let name = "factorial test";

fn factorial(n: Int) -> Int {
    if (n <= 1) {
        return 1;
    }
    return n * factorial(n - 1);
}

print(factorial(10));
```

## 2. Design decisions

- **Variables:** `let name = value;` with the type inferred from the value.
  An optional annotation is allowed: `let x: Float = 3.0;`
- **Functions:** parameter types and return type are always written
  explicitly: `fn add(a: Int, b: Int) -> Int { ... }`
- **Statements** end with `;`. Blocks use `{ }`.
- **Conditions** are wrapped in parentheses: `if (x > 5) { ... }`
- **Logic operators** are words: `and`, `or`, and `!` for not. There is no
  `&&` or `||`.
- **Comments:** not supported yet.
- **Built-ins:** `print(value)` is a built-in function, not a keyword. It
  accepts exactly one `Int`, `Float`, `Bool`, or `String` argument.
- **No implicit numeric widening:** `Int` and `Float` never mix in an
  expression, and assigning one to the other is a type error.
- **Not implemented:** arrays, closures, generics, `break`/`continue`,
  `for` loops.

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
- `5.` and `.5` are not valid floats — a digit is required on both sides of the `.`.
- Type names (`Int`, `Float`, `Bool`, `String`, `Void`) are ordinary
  identifiers to the lexer; the parser and type checker recognise them.
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

Whitespace separates tokens and is otherwise ignored.

## 4. Syntax grammar (what the parser accepts)

Notation: `*` zero or more, `+` one or more, `?` optional, `|` alternative,
quoted text is a literal token, UPPERCASE is a token type.

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

Each grammar rule above is one precedence level — the parser is one method
per rule (recursive descent).

## 5. Types

| Type | Values | JVM representation |
|---|---|---|
| `Int` | whole numbers | `int` |
| `Float` | decimal numbers | `double` |
| `Bool` | `true`, `false` | `int` (0/1), surfaced as `boolean` at call boundaries |
| `String` | text | `java.lang.String` |
| `Void` | none | function return type only |

### Typing rules

- `let x = expr;` gives `x` the type of `expr`. With an annotation, `expr`
  must match it exactly — no implicit `Int` → `Float` widening.
- Arithmetic (`+ - * / %`) requires both operands `Int` or both `Float`.
  `+` also joins two `String`s (concatenation).
- Comparisons `< <= > >=` require two numbers of the same type (`Int` or
  `Float`) and produce `Bool`.
- `==` and `!=` require operands of the same type and produce `Bool`.
  `String` equality compares contents, not references.
- `and`, `or`, `!` require `Bool` operands and produce `Bool`.
- `if` and `while` conditions must be `Bool`.
- Function calls must pass the exact number of arguments, each matching
  its parameter's declared type.
- Every `return` must match the function's declared return type. A `Void`
  function returns nothing.
- Assigning to an undeclared variable is an error.
- Variables are block-scoped; inner scopes may shadow outer variables.
- Integer division truncates (`7 / 2` is `3`), matching Java/`int` semantics.

### Known gaps

- The type checker does not verify that every path through a non-`Void`
  function actually returns a value — a function that falls off the end
  without returning is accepted but will behave incorrectly at runtime.
- No array type yet, so there is no bounds checking to speak of.

## 6. Errors

| Stage | Example | Message shape |
|---|---|---|
| Lexer | `@`, unterminated string | `[line 3, col 7] Unexpected character '@'` |
| Parser | missing `;` | `[line 3, col 12] Expected ';' after expression` |
| Type checker | `let x: Int = "hi";` | `[line 7, col 5] Expected INT but found STRING` |

The CLI's `check` command reports every error found in one run (not just
the first) and exits non-zero if any exist.

## 7. Compilation pipeline

Source text goes through, in order: lexing, recursive-descent parsing,
static type checking, a custom flat 3-address-style IR, three chained
optimisation passes (constant folding, constant propagation, dead-code
elimination — run to a fixed point), and finally JVM bytecode generation
via ASM. The output is a real `.class` file, runnable with `java
ClassName` — no interpreter involved in the compiled path. A separate
tree-walking interpreter also exists as a fast reference implementation
and for differential testing against the compiled output.

## 8. Example programs

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

```text
fn isLarge(x: Float) -> Bool {
    return x > 100.0;
}

let greeting = "Hello, " + "world!";
print(greeting);
print(isLarge(250.5));
```
