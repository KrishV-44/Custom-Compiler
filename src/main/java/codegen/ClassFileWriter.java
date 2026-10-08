package codegen;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ClassFileWriter {

    // Writes `bytecode` to <outputDir>/<className>.class, creating outputDir
    // if it doesn't exist yet. Returns the path actually written, so the
    // caller (e.g. a future CLI) can report it or hand it to `java` directly.
    public Path write(byte[] bytecode, String className, Path outputDir) throws IOException {
        Files.createDirectories(outputDir);
        Path target = outputDir.resolve(className + ".class");
        Files.write(target, bytecode);
        return target;
    }

    // Convenience overload: writes into the current working directory,
    // matching how Main has been writing files so far (e.g. "Factorial.class").
    public Path write(byte[] bytecode, String className) throws IOException {
        return write(bytecode, className, Path.of("."));
    }
}