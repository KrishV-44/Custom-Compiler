package codegen;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.FileOutputStream;
import java.io.IOException;

public class AsmSandbox {

    public static void main(String[] args) throws IOException {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);

        // Define the class itself: public class Sandbox { }
        cw.visit(
                Opcodes.V21,                  // target JVM version - must be <= your installed `java` version
                Opcodes.ACC_PUBLIC,            // class modifiers
                "Sandbox",                     // class name (no .class suffix)
                null,                           // generic signature - not used here
                "java/lang/Object",             // superclass, always Object for a plain class
                null                            // interfaces implemented - none
        );

        // Define: public static int answer() { return 42; }
        MethodVisitor mv = cw.visitMethod(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "answer",                       // method name
                "()I",                          // descriptor: no args, returns int
                null,
                null
        );

        mv.visitCode();
        mv.visitLdcInsn(42);        // push the constant 42 onto the operand stack
        mv.visitInsn(Opcodes.IRETURN);   // pop it, return it as an int
        mv.visitMaxs(0, 0);         // COMPUTE_MAXS fills in the real numbers for us
        mv.visitEnd();

        cw.visitEnd();

        byte[] bytecode = cw.toByteArray();

        try (FileOutputStream out = new FileOutputStream("Sandbox.class")) {
            out.write(bytecode);
        }

        System.out.println("Wrote Sandbox.class (" + bytecode.length + " bytes)");
    }
}