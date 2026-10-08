import lexer.Lexer;
import lexer.Token;
import parser.Parser;
import ast.Stmt;
import semantic.TypeChecker;
import semantic.SemanticError;
import ir.IRGenerator;
import ir.IRProgram;
import optimisation.*;
import codegen.BytecodeGenerator;
import codegen.ClassFileWriter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class Main {

    public static void main(String[] args) throws IOException, InterruptedException {
        if (args.length < 2) {
            System.err.println("Usage: compiler <build|run|check> <file.kr>");
            System.exit(1);
        }

        String command = args[0];
        Path sourcePath = Path.of(args[1]);
        String src = Files.readString(sourcePath);

        List<Token> tokens = new Lexer(src).scanTokens();
        List<Stmt> program = new Parser(tokens).parseProgram();

        TypeChecker checker = new TypeChecker();
        List<SemanticError> errors = checker.check(program);
        if (!errors.isEmpty()) {
            errors.forEach(System.err::println);
            System.exit(1);
        }

        if (command.equals("check")) {
            System.out.println("OK: no errors.");
            return;
        }

        IRGenerator generator = new IRGenerator(checker.getExpressionTypes());
        IRProgram ir = generator.generate(program);

        Optimiser optimiser = new Optimiser(List.of(
            new ConstantFoldingPass(),
            new ConstantPropagationPass(),
            new ConstantFoldingPass(),
            new DeadCodeEliminationPass()
        ));
        IRProgram optimised = optimiser.optimise(ir);

        String className = classNameFromFile(sourcePath);
        byte[] bytecode = new BytecodeGenerator().generateClass(className, optimised);
        Path written = new ClassFileWriter().write(bytecode, className);

        if (command.equals("build")) {
            System.out.println("Wrote " + written);
            return;
        }

        if (command.equals("run")) {
            runClass(className, written.getParent());
            return;
        }

        System.err.println("Unknown command: " + command);
        System.exit(1);
    }

    // "examples/factorial.kr" -> "Factorial" (JVM class names can't start
    // lowercase by convention, and can't contain dots/slashes)
    private static String classNameFromFile(Path sourcePath) {
        String fileName = sourcePath.getFileName().toString();
        String withoutExtension = fileName.contains(".")
                ? fileName.substring(0, fileName.lastIndexOf('.'))
                : fileName;
        return Character.toUpperCase(withoutExtension.charAt(0)) + withoutExtension.substring(1);
    }

    // Spawns a real `java ClassName` process so execution matches exactly how
    // anyone else would run the compiled output -- no special-casing, no
    // reflection tricks, just the same command you've been typing by hand.
    private static void runClass(String className, Path classDir) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder("java", "-cp", classDir.toString(), className);
        pb.inheritIO();   // let the child process's stdout/stderr print directly to this console
        Process process = pb.start();
        process.waitFor();
    }
}
/*


TypeChecker checker = new TypeChecker();
        List<SemanticError> errors = checker.check(program);
        if (errors.isEmpty()) {
            Map<Expr, Type> types = checker.getExpressionTypes();
            IRGenerator generator = new IRGenerator(types);
            IRProgram ir = generator.generate(program);
        }


String src = """
                    fn test_constant_folding() -> Int {
                        let fold_math: Int = (10 + 20) * 3 - (50 / 2);
                        let fold_bool = true and (false or true) and !false;
                        
                        if (fold_bool) {
                            return fold_math;
                        } else {
                            return 0;
                        }
                    }

                    fn test_cse_and_dce(x: Int, y: Int) -> Int {
                        let a: Int = (x * y) + (x * y);
                        let b = (x * y) + (x * y);
                        
                        let dead_var_1: Int = 9999;
                        let dead_var_2 = a + b;

                        if (false) {
                            print("This is entirely dead code and should be pruned.");
                        }

                        return a;
                    }

                    fn test_loop_optimizations(limit: Int) -> Int {
                        let accumulator: Int = 0;
                        let invariant_base = 42 * 10;

                        let i: Int = 0;
                        while (i < limit) {
                            let hoisted_candidate: Int = invariant_base * 2;
                            
                            if (i % 2 == 0) {
                                accumulator = accumulator + hoisted_candidate + (i * 2);
                            } else {
                                accumulator = accumulator + 1;
                            }
                            
                            i = i + 1;
                        }
                        
                        return accumulator;
                    }

                    fn small_helper(val: Int) -> Int {
                        return val * val + 5;
                    }

                    fn test_inlining() -> Int {
                        let sum: Int = 0;
                        let j = 1;
                        while (j <= 5) {
                            sum = sum + small_helper(j);
                            j = j + 1;
                        }
                        return sum;
                    }

                    print("=== STARTING ADVANCED OPTIMIZATION PASS TESTS ===");

                    print("1. Constant Folding Result [Expected: 65]:");
                    print(test_constant_folding());

                    print("2. CSE and DCE Result (x=4, y=5) [Expected: 40]:");
                    print(test_cse_and_dce(4, 5));

                    print("3. Loop Optimizations Result (limit=6) [Expected: 876]:");
                    print(test_loop_optimizations(6));

                    print("4. Function Inlining Result [Expected: 60]:");
                    print(test_inlining());

                    print("=== ADVANCED OPTIMIZATION TESTS COMPLETE ===");
                    """;
        List<Token> tokens = new Lexer(src).scanTokens();
        List<Stmt> program = new Parser(tokens).parseProgram();
        TypeChecker checker = new TypeChecker();
        List<SemanticError> errors = checker.check(program);   // no errors expected
        for (SemanticError error : errors) {
            System.out.println(error);
        }

        IRGenerator generator = new IRGenerator(checker.getExpressionTypes());

        IRProgram ir = generator.generate(program);
        System.out.println("--- before ---");
        System.out.println(IRPrinter.print(ir));

        Optimiser optimiser = new Optimiser(List.of(
            new ConstantFoldingPass(),
            new ConstantPropagationPass(),
            new ConstantFoldingPass(),
            new DeadCodeEliminationPass()
        ));
        IRProgram optimised = optimiser.optimise(ir);

        System.out.println("--- after ---");
        System.out.println(IRPrinter.print(optimised));


public static void main(String[] args) {
        String text = """
                        fn gcd(a: Int, b: Int) -> Int {
                            if (b == 0) {
                                return a;
                            }
                            return gcd(b, a - (a / b) * b);
                        }

                        fn process_metrics(score: Float, threshold: Float) -> Bool {
                            if (score >= threshold and score <= 100.0) {
                                print("Score is within optimal range.");
                                return true;
                            } else {
                                if (score < 0.0) {
                                    print("Error: Negative score detected!");
                                } else {
                                    print("Score exceeds threshold.");
                                }
                                return false;
                            }
                        }

                        print("=== Starting IR Generator Test Suite ===");

                        let base_factorial = factorial(5);
                        print(base_factorial); 

                        let greatest_common_divisor = gcd(48, 18);
                        print(greatest_common_divisor);

                        let app_title = "Compiler Verification Script";
                        let base_score: Float = 85.5;
                        let passing_mark: Float = 60.0;

                        print(app_title);
                        let is_valid = process_metrics(base_score, passing_mark);

                        let calc_y = (2 + 2) * 3;
                        let calc_z = 2 + 2 * 3;
                        let calc_h = calc_y + calc_z;
                        print(calc_h);

                        let loop_counter = 0;
                        let loop_active = true;

                        while (loop_active) {
                            if (loop_counter == 3) {
                                print("Reached iteration checkpoint 3");
                            } else {
                                if (loop_counter >= 5) {
                                    print("Terminating loop sequence.");
                                    loop_active = false;
                                }
                            }
                            loop_counter = loop_counter + 1;
                        }

                        let flag_p = true;
                        let flag_q = false;
                        let flag_r = true;
                        let flag_s = false;

                        print((flag_p and flag_q) or (flag_r or flag_s));
                    """;

        Lexer lexer = new Lexer(text);
        List<Token> tokens = lexer.scanTokens();
        for (Token token: tokens) {
            System.out.println(token.type() + " " + token.lexeme() + " " + token.literal() + " " + token.line() + " " + token.column());
        }

        Parser parser = new Parser(tokens);
        List<Stmt> statements = parser.parseProgram();
        
        // Print out the nicely formatted tree structure
        AstPrinter.printTree(statements);

        String src = """
            fn hofstadter_female(n: Int) -> Int {
                if (n == 0) {
                    return 1;
                }
                return n - hofstadter_male(hofstadter_female(n - 1));
            }

            fn hofstadter_male(n: Int) -> Int {
                if (n == 0) {
                    return 0;
                }
                return n - hofstadter_female(hofstadter_male(n - 1));
            }

            fn fibonacci(n: Int) -> Int {
                if (n <= 1) {
                    return n;
                }
                return fibonacci(n - 1) + fibonacci(n - 2);
            }

            fn test_scoping() -> Int {
                let scope_val = 100;
                {
                    let scope_val = 200;
                    {
                        let scope_val = 300;
                        print(scope_val);
                    }
                    print(scope_val);
                }
                print(scope_val);
                return scope_val;
            }

            fn test_precedence() -> Float {
                let complex_math = 10.0 + 5.0 * 2.0 - 8.0 / 4.0;
                return complex_math;
            }

            print("--- STARTING STRESS TEST ---");

            print("Fibonacci(8) [Expected: 21]:");
            print(fibonacci(8));

            print("Hofstadter Female(6):");
            print(hofstadter_female(6));

            print("Hofstadter Male(6):");
            print(hofstadter_male(6));

            print("Testing block scopes and variable shadowing:");
            let outer_result = test_scoping();
            print(outer_result);

            print("Operator precedence check (10 + 10 - 2):");
            print(test_precedence());

            let counter = 0;
            let accumulator = 0;
            while (counter < 10) {
                if (counter % 2 == 0 and counter > 0) {
                    accumulator = accumulator + (counter * 2);
                } else {
                    if (counter == 5) {
                        accumulator = accumulator + 50;
                    } else {
                        accumulator = accumulator + 1;
                    }
                }
                counter = counter + 1;
            }

            print("Loop accumulation final result:");
            print(accumulator);

            let flag_a = true;
            let flag_b = false;
            let flag_c = true;

            print("Complex boolean logic (true):");
            print((flag_a and !flag_b) or (flag_c and !(flag_a and flag_b)));

            print("--- STRESS TEST COMPLETE ---");
        """;

        List<Token> tokens2 = new Lexer(src).scanTokens();
        List<Stmt> program = new Parser(tokens2).parseProgram();
        new Interpreter().interpret(program);
        
    }
*/