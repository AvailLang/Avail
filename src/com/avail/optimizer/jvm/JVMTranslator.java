/*
 * JVMTranslator.java
 * Copyright © 1993-2017, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * * Neither the name of the copyright holder nor the names of the contributors
 *   may be used to endorse or promote products derived set this software
 *   without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package com.avail.optimizer.jvm;

import com.avail.interpreter.Interpreter;
import com.avail.interpreter.levelTwo.L2Chunk;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2Operation;
import com.avail.optimizer.L2ControlFlowGraph;
import com.avail.optimizer.StackReifier;
import com.avail.performance.Statistic;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.logging.Level;

import static com.avail.optimizer.jvm.JVMCodeGenerationUtility.emitIntConstant;
import static org.objectweb.asm.ClassWriter.COMPUTE_FRAMES;
import static org.objectweb.asm.ClassWriter.COMPUTE_MAXS;
import static org.objectweb.asm.Opcodes.*;
import static org.objectweb.asm.Type.*;

/**
 * A {@code JVMTranslator} converts a single {@link L2Chunk} into a {@link
 * JVMChunk} in a naive fashion. Instruction selection is optimized, but no
 * other optimizations are attempted; all significant optimizations should occur
 * on the {@code L2Chunk}'s {@link L2ControlFlowGraph control flow graph} and be
 * reflected in the {@code L2Chunk} to be translated.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class JVMTranslator
{
	/**
	 * The current {@link L2Chunk chunk} being translated.
	 */
	public final L2Chunk chunk;

	/**
	 * The {@link ClassWriter} responsible for writing the {@link JVMChunk}
	 * subclass. The {@code ClassWriter} is configured to automatically compute
	 * stack map frames and method limits (e.g., stack depths).
	 */
	private final ClassWriter classWriter;

	/**
	 * The name of the generated class, formed from a {@link UUID} to ensure
	 * that no collisions occur.
	 */
	public final String className;

	/**
	 * The internal name of the generated class.
	 */
	public final String classInternalName;

	/**
	 * Construct a new {@code JVMTranslator} to translate the specified
	 * {@link L2Chunk} to a {@link JVMChunk}.
	 *
	 * @param chunk
	 *        The source L2Chunk.
	 */
	public JVMTranslator (final L2Chunk chunk)
	{
		this.chunk = chunk;
		classWriter = new ClassWriter(COMPUTE_MAXS | COMPUTE_FRAMES);
		className = String.format(
			"com.avail.optimizer.jvm.generated.JVMChunk_%s",
			UUID.randomUUID().toString().replace('-', '_'));
		classInternalName = className.replace('.', '/');
	}

	/**
	 * Answer the name of the JVM {@code private static final} field associated
	 * with the specified {@linkplain L2Instruction instruction}.
	 *
	 * @param instruction
	 *        The instruction.
	 * @return The name of the appropriate field.
	 */
	public static String instructionFieldName (final L2Instruction instruction)
	{
		//noinspection StringConcatenationMissingWhitespace
		return instruction.operation.name() + "_" + instruction.offset();
	}

	/**
	 * Generate all of the {@code static} fields of the target {@link JVMChunk}.
	 */
	private void generateStaticFields ()
	{
		for (final L2Instruction instruction : chunk.instructions)
		{
			final FieldVisitor field = classWriter.visitField(
				ACC_PRIVATE | ACC_STATIC | ACC_FINAL,
				instructionFieldName(instruction),
				getDescriptor(instruction.getClass()),
				null,
				null);
			field.visitAnnotation(getDescriptor(Nonnull.class), true);
			field.visitEnd();
		}
	}

	/**
	 * Generate the {@code static} initializer of the target {@link JVMChunk}.
	 */
	private void generateStaticInitializer ()
	{
		final MethodVisitor method = classWriter.visitMethod(
			ACC_STATIC | ACC_PUBLIC,
			"<clinit>",
			getMethodDescriptor(VOID_TYPE),
			null,
			null);
		method.visitCode();
		//noinspection StringConcatenationMissingWhitespace
		method.visitLdcInsn(getType("L" + classInternalName + ";"));
		method.visitMethodInsn(
			INVOKEVIRTUAL,
			getInternalName(Class.class),
			"getClassLoader",
			getMethodDescriptor(getType(ClassLoader.class)),
			false);
		method.visitTypeInsn(
			CHECKCAST,
			getInternalName(JVMChunkClassLoader.class));
		method.visitInsn(DUP);
		method.visitFieldInsn(
			GETFIELD,
			getInternalName(JVMChunkClassLoader.class),
			"parameters",
			getDescriptor(Object[].class));
		final L2Instruction[] instructions = chunk.instructions;
		for (int i = 0, limit = instructions.length; i < limit; i++)
		{
			if (i < limit - 1)
			{
				method.visitInsn(DUP);
			}
			emitIntConstant(method, i);
			method.visitInsn(AALOAD);
			final L2Instruction instruction = instructions[i];
			final Class<?> instructionClass = instruction.getClass();
			method.visitTypeInsn(
				CHECKCAST,
				getInternalName(instructionClass));
			method.visitFieldInsn(
				PUTSTATIC,
				classInternalName,
				instructionFieldName(instruction),
				getDescriptor(instructionClass));
		}
		method.visitInsn(ACONST_NULL);
		method.visitFieldInsn(
			PUTFIELD,
			getInternalName(JVMChunkClassLoader.class),
			"parameters",
			getDescriptor(Object[].class));
		method.visitInsn(RETURN);
		method.visitMaxs(0, 0);
		method.visitEnd();
	}

	/**
	 * Generate the default constructor [{@code ()V}] of the target {@link
	 * JVMChunk}.
	 */
	private void generateConstructorV ()
	{
		final MethodVisitor method = classWriter.visitMethod(
			ACC_PUBLIC,
			"<init>",
			getMethodDescriptor(VOID_TYPE),
			null,
			null);
		method.visitCode();
		method.visitVarInsn(ALOAD, receiverLocal());
		method.visitMethodInsn(
			INVOKESPECIAL,
			getInternalName(JVMChunk.class),
			"<init>",
			getMethodDescriptor(VOID_TYPE),
			false);
		method.visitInsn(RETURN);
		method.visitMaxs(0, 0);
		method.visitEnd();
	}

	/**
	 * Should {@linkplain #generateRunChunk()} generate code to record
	 * {@linkplain L2Instruction} timings?
	 */
	public static boolean debugRecordL2InstructionTimings = false;

	/**
	 * Answer the next JVM local. The initial value is chosen to skip over the
	 * Category-1 receiver and Category-1 {@link Interpreter} formal parameter.
	 */
	private int nextLocal = 2;

	/**
	 * Answer the next JVM local for use within generated code produced by
	 * {@linkplain #generateRunChunk()}.
	 *
	 * @param wide
	 *        {@code true} if the local should accommodate a Category-2 value,
	 *        {@code false} if the local should accommodate only a Category-1
	 *        value.
	 * @return A JVM local.
	 */
	public int nextLocal (final boolean wide)
	{
		final int local = nextLocal;
		nextLocal += wide ? 2 : 1;
		return local;
	}

	/**
	 * Answer the JVM local for the receiver of a generated implementation of
	 * {@link JVMChunk#runChunk(Interpreter)}.
	 *
	 * @return The receiver local.
	 */
	@SuppressWarnings({"unused", "MethodMayBeStatic"})
	public int receiverLocal ()
	{
		return 0;
	}

	/**
	 * Answer the JVM local for the {@link Interpreter} formal parameter of a
	 * generated implementation of {@link JVMChunk#runChunk(Interpreter)}.
	 *
	 * @return The {@link Interpreter} formal parameter local.
	 */
	@SuppressWarnings("MethodMayBeStatic")
	public int interpreterLocal ()
	{
		return 1;
	}

	private int nanosToExcludeBeforeStepLocal = -1;
	private int timeBeforeLocal = -1;
	private int deltaTimeLocal = -1;
	private int excludeLocal = -1;

	/**
	 * The JVM local for the {@link StackReifier} local of a generated
	 * implementation of {@link JVMChunk#runChunk(Interpreter)}.
	 */
	private int reifier = -1;

	/**
	 * Answer the JVM local for the {@link StackReifier} local of a generated
	 * implementation of {@link JVMChunk#runChunk(Interpreter)}.
	 *
	 * @return The {@link Interpreter} formal parameter local.
	 */
	public int reifierLocal ()
	{
		final int r = reifier;
		assert r != -1;
		return r;
	}

	/**
	 * The {@link Label}s that correspond to the {@link L2Instruction}s.
	 */
	public @Nullable Label[] instructionLabels;

	/**
	 * Generate JVM instructions as a prologue to collecting timing information
	 * for the execution of an {@link L2Instruction instruction}.
	 *
	 * @param method
	 *        The {@linkplain MethodVisitor method} into which the generated JVM
	 *        instructions will be written.
	 * @param instruction
	 *        The {@link L2Instruction} to time.
	 */
	public void generateRecordTimingsPrologue (
		final MethodVisitor method,
		@SuppressWarnings("unused") final L2Instruction instruction)
	{
		// long nanosToExcludeBeforeStep = interpreter.nanosToExclude;
		method.visitVarInsn(ALOAD, interpreterLocal());
		method.visitFieldInsn(
			GETFIELD,
			getInternalName(Interpreter.class),
			"nanosToExclude",
			LONG_TYPE.getDescriptor());
		method.visitVarInsn(LSTORE, nanosToExcludeBeforeStepLocal);
		// long timeBefore = System.nanoTime();
		method.visitMethodInsn(
			INVOKESTATIC,
			getInternalName(System.class),
			"nanoTime",
			getMethodDescriptor(LONG_TYPE),
			false);
		method.visitVarInsn(LSTORE, timeBeforeLocal);
	}

	/**
	 * Generate JVM instructions as an epilogue to collecting timing information
	 * for the execution of an {@link L2Instruction instruction}.
	 *
	 * @param method
	 *        The {@linkplain MethodVisitor method} into which the generated JVM
	 *        instructions will be written.
	 * @param instruction
	 *        The {@link L2Instruction} to time.
	 */
	public void generateRecordTimingsEpilogue (
		final MethodVisitor method,
		final L2Instruction instruction)
	{
		// long deltaTime = System.nanoTime() - timeBefore;
		method.visitMethodInsn(
			INVOKESTATIC,
			getInternalName(System.class),
			"nanoTime",
			getMethodDescriptor(LONG_TYPE),
			false);
		method.visitVarInsn(LLOAD, timeBeforeLocal);
		method.visitInsn(LSUB);
		method.visitVarInsn(LSTORE, deltaTimeLocal);
		// long exclude =
		//    interpreter.nanosToExclude - nanosToExcludeBeforeStep;
		method.visitVarInsn(ALOAD, interpreterLocal());
		method.visitFieldInsn(
			GETFIELD,
			getInternalName(Interpreter.class),
			"nanosToExclude",
			LONG_TYPE.getDescriptor());
		method.visitVarInsn(LLOAD, nanosToExcludeBeforeStepLocal);
		method.visitInsn(LSUB);
		method.visitVarInsn(LSTORE, excludeLocal);
		// instruction.operation.statisticInNanoseconds.record(
		//    deltaTime - exclude, interpreter.interpreterIndex);
		method.visitFieldInsn(
			GETSTATIC,
			classInternalName,
			instructionFieldName(instruction),
			getDescriptor(instruction.getClass()));
		method.visitFieldInsn(
			GETFIELD,
			getInternalName(L2Instruction.class),
			"operation",
			getDescriptor(L2Operation.class));
		method.visitFieldInsn(
			GETFIELD,
			getInternalName(L2Operation.class),
			"statisticInNanoseconds",
			getDescriptor(Statistic.class));
		method.visitVarInsn(LLOAD, deltaTimeLocal);
		method.visitVarInsn(LLOAD, excludeLocal);
		method.visitInsn(LSUB);
		method.visitInsn(L2D);
		method.visitVarInsn(ALOAD, interpreterLocal());
		method.visitFieldInsn(
			GETFIELD,
			getInternalName(Interpreter.class),
			"interpreterIndex",
			INT_TYPE.getDescriptor());
		method.visitMethodInsn(
			INVOKEVIRTUAL,
			getInternalName(Statistic.class),
			"record",
			getMethodDescriptor(VOID_TYPE, DOUBLE_TYPE, INT_TYPE),
			false);
		// interpreter.nanosToExclude =
		//    nanosToExcludeBeforeStep + deltaTime;
		method.visitVarInsn(ALOAD, interpreterLocal());
		method.visitVarInsn(LLOAD, nanosToExcludeBeforeStepLocal);
		method.visitVarInsn(LLOAD, deltaTimeLocal);
		method.visitInsn(LADD);
		method.visitFieldInsn(
			PUTFIELD,
			getInternalName(Interpreter.class),
			"nanosToExclude",
			LONG_TYPE.getDescriptor());
	}

	/**
	 * Generate a JVM instruction sequence that effects the invocation of
	 * an {@link L2Instruction}'s {@linkplain L2Instruction#action action}. The
	 * generated code sequences leaves a {@link StackReifier} or {@code null} on
	 * top of the stack.
	 *
	 * @param method
	 *        The {@linkplain MethodVisitor method} into which the generated JVM
	 *        instructions will be written.
	 * @param instruction
	 *        The {@link L2Instruction}.
	 */
	public void generateRunAction (
		final MethodVisitor method,
		final L2Instruction instruction)
	{
		method.visitFieldInsn(
			GETSTATIC,
			classInternalName,
			instructionFieldName(instruction),
			getDescriptor(instruction.getClass()));
		method.visitVarInsn(ALOAD, interpreterLocal());
		method.visitMethodInsn(
			INVOKEVIRTUAL,
			getInternalName(L2Instruction.class),
			"runAction",
			getMethodDescriptor(
				getType(StackReifier.class),
				getType(Interpreter.class)),
			false);
	}

	/**
	 * Generate the {@link JVMChunk#runChunk(Interpreter)} method of the target
	 * {@link JVMChunk}.
	 */
	private void generateRunChunk ()
	{
		final MethodVisitor method = classWriter.visitMethod(
			ACC_PUBLIC,
			"runChunk",
			getMethodDescriptor(
				getType(StackReifier.class),
				getType(Interpreter.class)),
			null,
			null);
		method.visitParameter("interpreter", ACC_FINAL);
		method.visitParameterAnnotation(
			0,
			getDescriptor(Nonnull.class),
			true);
		method.visitAnnotation(
			getDescriptor(Nullable.class),
			true);
		// Create all of the labels that we're going to need for the JVM
		// tableswitch instruction.
		final L2Instruction[] instructions = chunk.instructions;
		instructionLabels = new Label[instructions.length];
		final Label badOffsetLabel = new Label();
		for (int i = 0, limit = instructionLabels.length; i < limit; i++)
		{
			instructionLabels[i] = new Label();
		}
		method.visitCode();
		method.visitVarInsn(ALOAD, interpreterLocal());
		method.visitFieldInsn(
			GETFIELD,
			getInternalName(Interpreter.class),
			"offset",
			INT_TYPE.getDescriptor());
		method.visitTableSwitchInsn(
			0,
			instructionLabels.length - 1,
			badOffsetLabel,
			instructionLabels);
		if (debugRecordL2InstructionTimings)
		{
			nanosToExcludeBeforeStepLocal = nextLocal(true);
			timeBeforeLocal = nextLocal(true);
			deltaTimeLocal = nextLocal(true);
			excludeLocal = nextLocal(true);
		}
		// Set up the local for the StackReifier before we translate the
		// instructions.
		reifier = nextLocal(false);
		for (int i = 0, limit = instructions.length; i < limit; i++)
		{
			final L2Instruction instruction = instructions[i];
			method.visitLabel(instructionLabels[i]);
			instruction.translateToJVM(this, method);
		}
		// An L2Chunk always ends with an explicit transfer of control, so we
		// shouldn't generate a return here.
		method.visitLabel(badOffsetLabel);
		// JVMChunk.badOffset(interpreter.offset-1, «instructions.length»);
		method.visitVarInsn(ALOAD, interpreterLocal());
		method.visitFieldInsn(
			GETFIELD,
			getInternalName(Interpreter.class),
			"offset",
			INT_TYPE.getDescriptor());
		emitIntConstant(method,1);
		method.visitInsn(ISUB);
		emitIntConstant(method,instructions.length - 1);
		method.visitMethodInsn(
			INVOKESTATIC,
			getInternalName(JVMChunk.class),
			"badOffset",
			getMethodDescriptor(
				getType(RuntimeException.class),
				INT_TYPE,
				INT_TYPE),
			false);
		method.visitInsn(ATHROW);
		method.visitMaxs(0, 0);
		method.visitEnd();
	}

	/**
	 * {@code true} if the bytes of generated {@link JVMChunk} subclasses should
	 * be dumped to files for external debugging, {@code false} otherwise.
	 */
	public static boolean debugDumpClassBytesToFiles = false;

	/**
	 * The generated {@link JVMChunk}, or {@code null} if no chunk could be
	 * constructed.
	 */
	private @Nullable JVMChunk jvmChunk;

	/**
	 * Answer the generated {@link JVMChunk}.
	 *
	 * @return The generated {@code JVMChunk}, or {@code null} if no chunk could
	 *         be constructed.
	 */
	public @Nullable JVMChunk jvmChunk ()
	{
		return jvmChunk;
	}

	/**
	 * Dump the specified JVM class bytes to an appropriately named temporary
	 * file.
	 *
	 * @param classBytes
	 *        The class bytes.
	 */
	public void dumpClassBytesToFile (final byte[] classBytes)
	{
		try
		{
			final int lastSlash =
				classInternalName.lastIndexOf('/');
			final String pkg =
				classInternalName.substring(0, lastSlash);
			final Path tempDir = Paths.get("debug", "jvm");
			final Path dir = tempDir.resolve(Paths.get(pkg));
			Files.createDirectories(dir);
			final String base = classInternalName.substring(lastSlash + 1);
			final Path classFile = dir.resolve(base + ".class");
			Files.write(classFile, classBytes);
		}
		catch (final IOException e)
		{
			Interpreter.log(
				Interpreter.loggerDebugJVM,
				Level.WARNING,
				"unable to write class bytes for generated class {0}",
				classInternalName);
		}
	}

	/**
	 * Translate the embedded {@link L2Chunk} into a {@link JVMChunk}.
	 */
	public void translate ()
	{
		classWriter.visit(
			V1_8,
			ACC_PUBLIC | ACC_FINAL,
			classInternalName,
			null,
			JVMChunk.class.getName().replace('.', '/'),
			null);
		generateStaticFields();
		generateStaticInitializer();
		generateConstructorV();
		generateRunChunk();
		classWriter.visitEnd();
		final byte[] classBytes = classWriter.toByteArray();
		if (debugDumpClassBytesToFiles)
		{
			dumpClassBytesToFile(classBytes);
		}
		final JVMChunkClassLoader loader = new JVMChunkClassLoader();
		jvmChunk = loader.newJVMChunkFrom(
			chunk,
			className,
			classBytes,
			chunk.instructions);
	}
}
