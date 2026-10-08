# Custom Compiler

A statically typed, C-like language with its own lexer, parser, type checker,
intermediate representation, optimiser, and a JVM bytecode backend —
built from scratch in Java, with no parser-generator or compiler framework.

```text
let x = 10;

fn factorial(n: Int) -> Int {
    if (n <= 1) {
        return 1;
    }
    return n * factorial(n - 1);
}

print(factorial(x));
```

Compiles down to real `.class` files that run on the standard JVM with
`java`, no interpreter required.

## Pipeline

```text
Source (.kr)
    │
    ▼
  Lexer            tokens
    │
    ▼
  Parser           AST (recursive descent)
    │
    ▼
  Type Checker      type-annotated AST, or a list of errors
    │
    ├──────────────► Interpreter        (tree-walking, for fast iteration)
    │
    ▼
  IR Generator       flat 3-address-style instructions
    │
    ▼
  Optimiser          constant folding, constant propagation,
    │                dead-code elimination (run to a fixed point)
    ▼
  Bytecode Generator  real JVM bytecode, via ASM
    │
    ▼
  .class file  →  runs with `java ClassName`
```

Every stage is hand-written. ASM is used only for the final step —
emitting bytecode instructions and writing a valid class file — not for
anything upstream of it.

## Language

See [`LANGUAGE.md`](LANGUAGE.md) for the full grammar, type rules, and
design decisions. Highlights:

- Static types: `Int`, `Float`, `Bool`, `String`, `Void`
- Type inference on `let` (optional explicit annotation)
- Functions with explicit parameter and return types
- `if` / `else` / `while`, recursion, nested scopes with shadowing
- No implicit numeric widening (`Int` and `Float` never mix silently)

Not yet implemented: arrays, closures, generics, comments.

## Build & run

Requires Java 21 and Maven.

```bash
mvn compile
```

### CLI

```bash
# Type-check only, report errors if any
mvn exec:java -Dexec.mainClass="Main" -Dexec.args="check examples/factorial.kr"

# Compile to a .class file
mvn exec:java -Dexec.mainClass="Main" -Dexec.args="build examples/factorial.kr"

# Compile and run immediately
mvn exec:java -Dexec.mainClass="Main" -Dexec.args="run examples/factorial.kr"
```

`build`/`run` write `<FileName>.class` to the project root, then `run`
executes it as a real `java` subprocess — identical to how anyone else
would run the compiled output.

## Example programs

See [`examples/`](examples/) for sample `.kr` programs, including a
recursive `factorial`.

## Project structure

```text
src/main/java/
├── lexer/           tokeniser
├── ast/              AST node definitions + a pretty-printer
├── parser/            recursive-descent parser
├── interpreter/       tree-walking reference implementation
├── semantic/           static type checker
├── ir/                  custom IR + generator + pretty-printer
├── optimisation/         constant folding, constant propagation, DCE
└── codegen/               JVM bytecode backend (ASM) + class file writer
```

## Status / limitations

Implemented and tested: the full pipeline above, for every type in the
language, including recursion and nested control flow.

Deliberately out of scope for now (see `LANGUAGE.md` for more):

- Arrays, closures, generics
- Common subexpression elimination, unreachable-block elimination
  (would need a real control-flow graph; current optimiser works on
  flat instruction lists)
- Missing-return-on-some-path detection in the type checker
- A formal automated test suite (currently verified through extensive
  manual testing during development)

## Why build this

Written to understand how a compiler actually works end to end — not
just parsing, but static typing, intermediate representations,
optimisation, and real machine code generation — rather than stopping
at "parses and interprets."
