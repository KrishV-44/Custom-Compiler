package codegen;

import ir.IRFunction;
import ir.IRInstruction;
import ir.IRValue;
import ir.IRProgram;
import semantic.Type;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BytecodeGenerator {

    // A function's full call signature, needed before generating ANY method body,
    // since a call site (including a recursive one, or a call to a function
    // defined later in the file) needs to know the callee's real descriptor.
    private record FunctionSignature(List<Type> paramTypes, Type returnType) {}

    public byte[] generateClass(String className, IRProgram program) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, className, null, "java/lang/Object", null);

        Map<String, FunctionSignature> signatures = new HashMap<>();
        for (IRFunction fn : program.functions()) {
            List<Type> paramTypes = new ArrayList<>();
            for (String param : fn.paramNames()) {
                paramTypes.add(fn.varTypes().get(param));
            }
            signatures.put(fn.name(), new FunctionSignature(paramTypes, inferReturnType(fn)));
        }

        for (IRFunction fn : program.functions()) {
            generateMethod(cw, className, fn, signatures);
        }
        generateMainMethod(cw, className, program.topLevel(), program.topLevelVarTypes(), signatures);

        cw.visitEnd();
        return cw.toByteArray();
    }

    // The type checker guarantees every reachable `return <expr>;` in a function
    // agrees on type, so finding the first one with a value tells us the whole
    // function's return type. No Return with a value anywhere means Void.
    private Type inferReturnType(IRFunction fn) {
        for (IRInstruction instr : fn.instructions()) {
            if (instr instanceof IRInstruction.Return r && r.value() != null) {
                return valueType(r.value(), fn.varTypes());
            }
        }
        return Type.VOID;
    }

    private void generateMainMethod(ClassWriter cw, String className, List<IRInstruction> topLevel,
                                     Map<String, Type> varTypes, Map<String, FunctionSignature> signatures) {
        MethodVisitor mv = cw.visitMethod(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "main", "([Ljava/lang/String;)V", null, null
        );

        mv.visitCode();
        Map<String, Integer> slots = assignSlots(List.of(), topLevel, varTypes);
        Map<String, Label> labels = createLabels(topLevel);
        for (IRInstruction instr : topLevel) {
            emit(mv, instr, slots, labels, className, varTypes, signatures);
        }
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private void generateMethod(ClassWriter cw, String className, IRFunction function,
                                 Map<String, FunctionSignature> signatures) {
        FunctionSignature sig = signatures.get(function.name());
        StringBuilder paramDesc = new StringBuilder();
        for (Type t : sig.paramTypes()) {
            paramDesc.append(typeDescriptor(t));
        }
        String descriptor = "(" + paramDesc + ")" + typeDescriptor(sig.returnType());

        MethodVisitor mv = cw.visitMethod(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                function.name(), descriptor, null, null
        );

        mv.visitCode();
        Map<String, Integer> slots = assignSlots(function.paramNames(), function.instructions(), function.varTypes());
        Map<String, Label> labels = createLabels(function.instructions());
        for (IRInstruction instr : function.instructions()) {
            emit(mv, instr, slots, labels, className, function.varTypes(), signatures);
        }
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private Map<String, Label> createLabels(List<IRInstruction> instructions) {
        Map<String, Label> labels = new HashMap<>();
        for (IRInstruction instr : instructions) {
            if (instr instanceof IRInstruction.Label l) {
                labels.put(l.name(), new Label());
            }
        }
        return labels;
    }

    // Doubles occupy TWO consecutive local variable slots on the JVM (a "category 2"
    // type); everything else here (int, boolean, a String reference) occupies one.
    // Getting this wrong corrupts every slot number after the first double.
    private Map<String, Integer> assignSlots(List<String> paramNames, List<IRInstruction> instructions,
                                              Map<String, Type> varTypes) {
        Map<String, Integer> slots = new HashMap<>();
        int next = 0;
        for (String param : paramNames) {
            slots.put(param, next);
            next += slotWidth(varTypes.get(param));
        }
        for (IRInstruction instr : instructions) {
            String dest = destinationOf(instr);
            if (dest != null && !slots.containsKey(dest)) {
                slots.put(dest, next);
                next += slotWidth(varTypes.get(dest));
            }
        }
        return slots;
    }

    private String destinationOf(IRInstruction instr) {
        return switch (instr) {
            case IRInstruction.BinOp b -> b.dest();
            case IRInstruction.UnaryOp u -> u.dest();
            case IRInstruction.Copy c -> c.dest();
            case IRInstruction.Call call -> call.dest();
            default -> null;
        };
    }

    // ---------- type -> JVM mapping ----------

    private String typeDescriptor(Type t) {
        return switch (t) {
            case INT -> "I";
            case FLOAT -> "D";
            case BOOL -> "Z";
            case STRING -> "Ljava/lang/String;";
            case VOID -> "V";
            default -> "I";   // ERROR: the type checker already rejected this program if this matters
        };
    }

    private int slotWidth(Type t) {
        return (t == Type.FLOAT) ? 2 : 1;
    }

    private int loadOpcode(Type t) {
        return switch (t) {
            case FLOAT -> Opcodes.DLOAD;
            case STRING -> Opcodes.ALOAD;
            default -> Opcodes.ILOAD;   // INT, BOOL
        };
    }

    private int storeOpcode(Type t) {
        return switch (t) {
            case FLOAT -> Opcodes.DSTORE;
            case STRING -> Opcodes.ASTORE;
            default -> Opcodes.ISTORE;
        };
    }

    private int returnOpcode(Type t) {
        return switch (t) {
            case FLOAT -> Opcodes.DRETURN;
            case STRING -> Opcodes.ARETURN;
            case VOID -> Opcodes.RETURN;
            default -> Opcodes.IRETURN;   // INT, BOOL
        };
    }

    // The type an IRValue carries: a Temp looks itself up, a Const is read off
    // the Java object it's already wrapping.
    private Type valueType(IRValue value, Map<String, Type> varTypes) {
        return switch (value) {
            case IRValue.Temp t -> varTypes.get(t.name());
            case IRValue.Const c -> {
                if (c.value() instanceof Integer) yield Type.INT;
                if (c.value() instanceof Double) yield Type.FLOAT;
                if (c.value() instanceof Boolean) yield Type.BOOL;
                if (c.value() instanceof String) yield Type.STRING;
                yield Type.ERROR;
            }
        };
    }

    // ---------- emission ----------

    private void emit(MethodVisitor mv, IRInstruction instr, Map<String, Integer> slots, Map<String, Label> labels,
                       String className, Map<String, Type> varTypes, Map<String, FunctionSignature> signatures) {
        switch (instr) {
            case IRInstruction.BinOp b -> {
                Type operandType = valueType(b.left(), varTypes);   // checker guarantees left/right agree

                if (operandType == Type.STRING && b.op().equals("ADD")) {
                    emitStringConcat(mv, b.left(), b.right(), slots, varTypes);
                } else if (operandType == Type.STRING) {
                    emitStringComparison(mv, b.op(), b.left(), b.right(), slots, varTypes);
                } else {
                    load(mv, b.left(), slots, varTypes);
                    load(mv, b.right(), slots, varTypes);
                    emitBinOp(mv, b.op(), operandType);
                }
                mv.visitVarInsn(storeOpcode(varTypes.get(b.dest())), slots.get(b.dest()));
            }

            case IRInstruction.UnaryOp u -> {
                Type type = varTypes.get(u.dest());
                load(mv, u.operand(), slots, varTypes);
                switch (u.op()) {
                    case "NEG" -> mv.visitInsn(type == Type.FLOAT ? Opcodes.DNEG : Opcodes.INEG);
                    case "NOT" -> {
                        mv.visitInsn(Opcodes.ICONST_1);
                        mv.visitInsn(Opcodes.IXOR);   // flips 0<->1
                    }
                    default -> throw new UnsupportedOperationException("Unhandled unary op: " + u.op());
                }
                mv.visitVarInsn(storeOpcode(type), slots.get(u.dest()));
            }

            case IRInstruction.Copy c -> {
                Type type = varTypes.get(c.dest());
                load(mv, c.src(), slots, varTypes);
                mv.visitVarInsn(storeOpcode(type), slots.get(c.dest()));
            }

            case IRInstruction.Return r -> {
                if (r.value() != null) {
                    Type type = valueType(r.value(), varTypes);
                    load(mv, r.value(), slots, varTypes);
                    mv.visitInsn(returnOpcode(type));
                } else {
                    mv.visitInsn(Opcodes.RETURN);
                }
            }

            case IRInstruction.Label l -> mv.visitLabel(labels.get(l.name()));

            case IRInstruction.Jump j -> mv.visitJumpInsn(Opcodes.GOTO, labels.get(j.label()));

            case IRInstruction.CondJump cj -> {
                load(mv, cj.condition(), slots, varTypes);   // always an int 0/1 (our Bool representation)
                mv.visitJumpInsn(Opcodes.IFNE, labels.get(cj.thenLabel()));
                mv.visitJumpInsn(Opcodes.GOTO, labels.get(cj.elseLabel()));
            }

            case IRInstruction.Call call -> {
                if (call.function().equals("print")) {
                    mv.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
                    Type argType = valueType(call.args().get(0), varTypes);
                    load(mv, call.args().get(0), slots, varTypes);
                    String desc = switch (argType) {
                        case FLOAT -> "(D)V";
                        case STRING -> "(Ljava/lang/String;)V";
                        case BOOL -> "(Z)V";
                        default -> "(I)V";   // INT
                    };
                    mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "println", desc, false);
                } else {
                    FunctionSignature sig = signatures.get(call.function());
                    for (IRValue arg : call.args()) {
                        load(mv, arg, slots, varTypes);
                    }
                    StringBuilder paramDesc = new StringBuilder();
                    for (Type t : sig.paramTypes()) {
                        paramDesc.append(typeDescriptor(t));
                    }
                    String desc = "(" + paramDesc + ")" + typeDescriptor(sig.returnType());
                    mv.visitMethodInsn(Opcodes.INVOKESTATIC, className, call.function(), desc, false);

                    if (call.dest() != null && sig.returnType() != Type.VOID) {
                        mv.visitVarInsn(storeOpcode(sig.returnType()), slots.get(call.dest()));
                    }
                }
            }
        }
    }

    private void load(MethodVisitor mv, IRValue value, Map<String, Integer> slots, Map<String, Type> varTypes) {
        switch (value) {
            case IRValue.Temp t -> mv.visitVarInsn(loadOpcode(varTypes.get(t.name())), slots.get(t.name()));
            case IRValue.Const c -> {
                if (c.value() instanceof Boolean b) {
                    mv.visitInsn(b ? Opcodes.ICONST_1 : Opcodes.ICONST_0);   // no boolean literal on the JVM
                } else {
                    mv.visitLdcInsn(c.value());   // works directly for Integer, Double, and String
                }
            }
        }
    }

    private void emitBinOp(MethodVisitor mv, String op, Type operandType) {
        boolean isFloat = (operandType == Type.FLOAT);
        switch (op) {
            case "ADD" -> mv.visitInsn(isFloat ? Opcodes.DADD : Opcodes.IADD);
            case "SUB" -> mv.visitInsn(isFloat ? Opcodes.DSUB : Opcodes.ISUB);
            case "MUL" -> mv.visitInsn(isFloat ? Opcodes.DMUL : Opcodes.IMUL);
            case "DIV" -> mv.visitInsn(isFloat ? Opcodes.DDIV : Opcodes.IDIV);
            case "MOD" -> mv.visitInsn(isFloat ? Opcodes.DREM : Opcodes.IREM);

            case "LESS" -> comparison(mv, isFloat, Opcodes.IF_ICMPLT, Opcodes.IFLT);
            case "LESS_EQUAL" -> comparison(mv, isFloat, Opcodes.IF_ICMPLE, Opcodes.IFLE);
            case "GREATER" -> comparison(mv, isFloat, Opcodes.IF_ICMPGT, Opcodes.IFGT);
            case "GREATER_EQUAL" -> comparison(mv, isFloat, Opcodes.IF_ICMPGE, Opcodes.IFGE);
            case "EQUAL" -> comparison(mv, isFloat, Opcodes.IF_ICMPEQ, Opcodes.IFEQ);
            case "NOT_EQUAL" -> comparison(mv, isFloat, Opcodes.IF_ICMPNE, Opcodes.IFNE);

            case "AND" -> mv.visitInsn(Opcodes.IAND);   // operands are always Bool (0/1 ints) here
            case "OR" -> mv.visitInsn(Opcodes.IOR);

            default -> throw new UnsupportedOperationException("Unhandled binary op: " + op);
        }
    }

    private void comparison(MethodVisitor mv, boolean isFloat, int intJumpOpcode, int floatJumpOpcode) {
        if (isFloat) {
            emitComparisonFloat(mv, floatJumpOpcode);
        } else {
            emitComparisonInt(mv, intJumpOpcode);
        }
    }

    // Pops two ints already on the stack, compares them, pushes 1 or 0.
    private void emitComparisonInt(MethodVisitor mv, int jumpOpcode) {
        Label trueLabel = new Label();
        Label endLabel = new Label();
        mv.visitJumpInsn(jumpOpcode, trueLabel);
        mv.visitInsn(Opcodes.ICONST_0);
        mv.visitJumpInsn(Opcodes.GOTO, endLabel);
        mv.visitLabel(trueLabel);
        mv.visitInsn(Opcodes.ICONST_1);
        mv.visitLabel(endLabel);
    }

    // The JVM has no IF_DCMPxx the way it has IF_ICMPxx -- doubles must first be
    // reduced to a single int via DCMPG (pushes -1/0/1), THEN compared against
    // zero with a plain single-operand jump (IFLT/IFLE/...).
    private void emitComparisonFloat(MethodVisitor mv, int singleOperandJumpOpcode) {
        mv.visitInsn(Opcodes.DCMPG);
        Label trueLabel = new Label();
        Label endLabel = new Label();
        mv.visitJumpInsn(singleOperandJumpOpcode, trueLabel);
        mv.visitInsn(Opcodes.ICONST_0);
        mv.visitJumpInsn(Opcodes.GOTO, endLabel);
        mv.visitLabel(trueLabel);
        mv.visitInsn(Opcodes.ICONST_1);
        mv.visitLabel(endLabel);
    }

    // "a" + "b": the JVM has no string-concatenation operator, so this is built
    // the same way javac itself lowers it -- a StringBuilder, two appends, toString.
    private void emitStringConcat(MethodVisitor mv, IRValue left, IRValue right,
                                   Map<String, Integer> slots, Map<String, Type> varTypes) {
        mv.visitTypeInsn(Opcodes.NEW, "java/lang/StringBuilder");
        mv.visitInsn(Opcodes.DUP);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V", false);

        load(mv, left, slots, varTypes);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append",
                "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);

        load(mv, right, slots, varTypes);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append",
                "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);

        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "toString",
                "()Ljava/lang/String;", false);
    }

    // Strings must be compared with .equals(), never with == (reference equality).
    private void emitStringComparison(MethodVisitor mv, String op, IRValue left, IRValue right,
                                       Map<String, Integer> slots, Map<String, Type> varTypes) {
        load(mv, left, slots, varTypes);
        load(mv, right, slots, varTypes);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "equals",
                "(Ljava/lang/Object;)Z", false);
        if (op.equals("NOT_EQUAL")) {
            mv.visitInsn(Opcodes.ICONST_1);
            mv.visitInsn(Opcodes.IXOR);   // flip the equals() result
        }
    }
}