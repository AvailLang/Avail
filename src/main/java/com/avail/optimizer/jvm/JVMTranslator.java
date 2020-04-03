/*
 * JVMTranslator.java
 * Copyright © 1993-2019, The Avail Foundation, LLC.
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

import com.avail.AvailThread;
import com.avail.descriptor.A_Module;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.functions.A_RawFunction;
import com.avail.descriptor.representation.A_BasicObject;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.JavaLibrary;
import com.avail.interpreter.Primitive;
import com.avail.interpreter.levelOne.L1Disassembler;
import com.avail.interpreter.levelOne.L1Operation;
import com.avail.interpreter.levelTwo.L2Chunk;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2OperandDispatcher;
import com.avail.interpreter.levelTwo.operand.*;
import com.avail.interpreter.levelTwo.operation.L2_ENTER_L2_CHUNK;
import com.avail.interpreter.levelTwo.operation.L2_SAVE_ALL_AND_PC_TO_INT;
import com.avail.interpreter.levelTwo.register.L2BoxedRegister;
import com.avail.interpreter.levelTwo.register.L2IntRegister;
import com.avail.interpreter.levelTwo.register.L2Register;
import com.avail.interpreter.levelTwo.register.L2Register.RegisterKind;
import com.avail.optimizer.ExecutableChunk;
import com.avail.optimizer.L2ControlFlowGraph;
import com.avail.optimizer.L2ControlFlowGraphVisualizer;
import com.avail.optimizer.StackReifier;
import com.avail.performance.Statistic;
import com.avail.utility.evaluation.Continuation1NotNull;
import org.objectweb.asm.*;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.regex.Pattern;

import static com.avail.AvailRuntimeSupport.captureNanos;
import static com.avail.descriptor.NilDescriptor.nil;
import static com.avail.descriptor.functions.ContinuationDescriptor.createDummyContinuationMethod;
import static com.avail.interpreter.Interpreter.chunkField;
import static com.avail.interpreter.Interpreter.interpreterFunctionField;
import static com.avail.optimizer.StackReifier.pushContinuationActionMethod;
import static com.avail.optimizer.jvm.JVMTranslator.LiteralAccessor.invalidIndex;
import static com.avail.performance.StatisticReport.FINAL_JVM_TRANSLATION_TIME;
import static com.avail.utility.Nulls.stripNull;
import static com.avail.utility.Strings.traceFor;
import static java.util.Comparator.comparingInt;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.objectweb.asm.ClassWriter.COMPUTE_FRAMES;
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
	 * The source {@link A_RawFunction L1 code}.
	 */
	public final @Nullable A_RawFunction code;

	/** The descriptive (non-unique) name of this chunk. */
	private final String chunkName;

	/** The optional name of the source file associated with the new class. */
	private final @Nullable String sourceFileName;

	/**
	 * The {@link L2ControlFlowGraph} containing the instructions that are
	 * translated to JVM bytecodes.
	 */
	private final L2ControlFlowGraph controlFlowGraph;

	/** The array of {@link L2Instruction}s to translate to JVM bytecodes. */
	public final L2Instruction[] instructions;

	/**
	 * The {@link ClassWriter} responsible for writing the {@link JVMChunk}
	 * subclass. The {@code ClassWriter} is configured to automatically compute
	 * stack map frames and method limits (e.g., stack depths).
	 */
	final ClassWriter classWriter;

	/**
	 * The name of the generated class, formed from a {@link UUID} to ensure
	 * that no collisions occur.
	 */
	public final String className;

	/**
	 * The internal name of the generated class.
	 */
	final String classInternalName;

	/** The class file bytes that are produced. */
	private @Nullable byte[] classBytes = null;

	/**
	 * A regex {@link Pattern} to rewrite function names like '"foo_"[1][3]' to
	 * 'foo_#1#3'.
	 */
	private static final Pattern subblockRewriter =
		Pattern.compile("\\[(\\d+)]");

	/**
	 * A regex {@link Pattern} to strip out leading and trailing quotes from
	 * a potential class name.
	 */
	private static final Pattern classNameUnquoter =
		Pattern.compile("^[\"](.*)[\"]([^\"]*)$");

	/**
	 * A regex {@link Pattern} to find runs of characters that are forbidden in
	 * a class name, and will be replaced with a single {@code '%'}.
	 */
	private static final Pattern classNameForbiddenCharacters =
		Pattern.compile("[\\[\\]\\\\/.:;\"'\\p{Cntrl}]+");

	/** A regex {@link Pattern} to strip the prefix of a module name. */
	private static final Pattern moduleNameStripper =
		Pattern.compile("^.*/([^/]+)$");

	/**
	 * Whether to emit JVM instructions to invoke
	 * {@link Interpreter#traceL2(ExecutableChunk, int, String, Object)} before
	 * each {@link L2Instruction}.
	 */
	public static final boolean callTraceL2AfterEveryInstruction = false;

	/**
	 * Construct a new {@code JVMTranslator} to translate the specified array of
	 * {@link L2Instruction}s to a {@link JVMChunk}.
	 *
	 * @param code
	 *        The source {@linkplain A_RawFunction L1 code}, or {@code null} for
	 *        the {@linkplain L2Chunk#unoptimizedChunk unoptimized chunk}.
	 * @param sourceFileName
	 *        The name of the Avail source file that produced the {@link #code}.
	 *        Use {@code null} if no such file exists.
	 * @param controlFlowGraph
	 *        The {@link L2ControlFlowGraph} which produced the sequence of
	 *        instructions.
	 * @param chunkName
	 *        The descriptive (non-unique) name of the chunk being translated.
	 * @param instructions
	 *        The source {@link L2Instruction}s.
	 */
	public JVMTranslator (
		final @Nullable A_RawFunction code,
		final String chunkName,
		final @Nullable String sourceFileName,
		final L2ControlFlowGraph controlFlowGraph,
		final L2Instruction[] instructions)
	{
		this.code = code;
		this.controlFlowGraph = controlFlowGraph;
		this.chunkName = chunkName;
		this.sourceFileName = sourceFileName;
		this.instructions = instructions.clone();
		classWriter = new ClassWriter(COMPUTE_FRAMES);
		final A_Module module = code == null ? nil : code.module();
		final String moduleName = module == nil
			? "NoModule"
			: moduleNameStripper.matcher(module.moduleName().asNativeString())
				.replaceAll("$1");
		final String originalFunctionName = code == null
			? "DEFAULT"
			: code.methodName().asNativeString();
		String cleanFunctionName =
			subblockRewriter.matcher(originalFunctionName).replaceAll("#$1");
		cleanFunctionName =
			classNameUnquoter.matcher(cleanFunctionName).replaceAll("$1$2");
		cleanFunctionName =
			classNameForbiddenCharacters.matcher(cleanFunctionName)
				.replaceAll("\\%");
		final String safeUID = UUID.randomUUID().toString().replace('-', '_');
		className = "com.avail.optimizer.jvm.generated."
			+ moduleName
			+ "."
			+ cleanFunctionName
			+ " - "
			+ safeUID
			+ "."
			+ moduleName + " - "
			+ cleanFunctionName;
		classInternalName = className.replace('.', '/');
	}

	/**
	 * The {@link L2Instruction#isEntryPoint() entry points} into the
	 * {@link L2Chunk}, mapped to their {@link Label}s.
	 */
	private final Map<Integer, Label> entryPoints = new LinkedHashMap<>();

	/**
	 * As the code is being generated and we encounter an
	 * {@link L2_SAVE_ALL_AND_PC_TO_INT}, we examine its corresponding target
	 * block to figure out which registers actually have to be captured at the
	 * save, and restored at the {@link L2_ENTER_L2_CHUNK}.  At that point, we
	 * look up the <em>local numbers</em> from the {@link JVMTranslator} and
	 * record them by {@link RegisterKind} in this field.
	 *
	 * <p>During optimization, an edge from an {@link L2_SAVE_ALL_AND_PC_TO_INT}
	 * to its target {@link L2_ENTER_L2_CHUNK} is treated as though the jump
	 * happens immediately, so that liveness information can be kept accurate.
	 * The final code generation knows better, and simply saves and restores the
	 * locals that back registers that are considered live across this gap.</p>
	 *
	 * <p>The key of this map is the target {@link L2_ENTER_L2_CHUNK}
	 * instruction, and the value is a map from {@link RegisterKind} to the
	 * {@link List} of live <em>local numbers</em>.
	 */
	public final Map<L2Instruction, EnumMap<RegisterKind, List<Integer>>>
		liveLocalNumbersByKindPerEntryPoint = new HashMap<>();

	/**
	 * We're at a point where reification has been requested.  A
	 * {@link StackReifier} has already been stashed in the {@link Interpreter},
	 * and already-popped calls may have already queued actions in the reifier,
	 * to be executed in reverse order.
	 *
	 * <p>First, we stash the live registers in a bogus continuation that will
	 * resume at the specified target (onReification's target), which must be an
	 * {@link L2_ENTER_L2_CHUNK}. Then we create an action to invoke that
	 * continuation, and push that action onto the current StackReifier's action
	 * stack. Finally, we exit with the current reifier. When the
	 * {@link L2_ENTER_L2_CHUNK} is reached later, it will restore the registers
	 * and continue constructing the real continuation, with the knowledge that
	 * the {@link Interpreter#getReifiedContinuation()} represents the
	 * caller.</p>
	 *
	 * @param method
	 *        The JVM method being written.
	 * @param onReification
	 *        Where to jump to after everything below this frame has been fully
	 *        reified.
	 */
	public void generateReificationPreamble (
		final MethodVisitor method,
		final L2PcOperand onReification)
	{
		method.visitVarInsn(ALOAD, reifierLocal());
		// [reifier]
		method.visitInsn(DUP);
		// [reifier. reifier]
		loadInterpreter(method);
		interpreterFunctionField.generateRead(method);
		// [reifier. reifier, function]
		onReification.createAndPushRegisterDump(this, method);
		// [reifier. reifier, function, regDump]
		loadInterpreter(method);
		chunkField.generateRead(method);
		// [reifier. reifier, function, regDump, chunk]
		intConstant(method, onReification.offset());
		// [reifier. reifier, function, regDump, chunk, offset]
		createDummyContinuationMethod.generateCall(method);
		// [reifier. reifier, dummyContinuation]
		// Push an action to the current StackReifier which will run the dummy
		// continuation.
		pushContinuationActionMethod.generateCall(method);
		// [reifier]
		// Now return the same reifier to the next level out on the stack.
		method.visitInsn(ARETURN);
	}

	/**
	 * A {@code LiteralAccessor} aggregates means of accessing a literal {@link
	 * Object} in various contexts.
	 *
	 * @author Todd L Smith &lt;todd@availlang.org&gt;
	 */
	static final class LiteralAccessor
	{
		/**
		 * A sentinel value of {@link #classLoaderIndex} that represents no slot
		 * is needed in the {@link JVMChunkClassLoader}'s {@linkplain
		 * JVMChunkClassLoader#parameters parameters} array.
		 */
		static final int invalidIndex = -1;

		/**
		 * The index into the {@link JVMChunkClassLoader}'s {@linkplain
		 * JVMChunkClassLoader#parameters parameters} array at which the
		 * corresponding literal is located, or {@link #invalidIndex} if no slot
		 * is required.
		 */
		public final int classLoaderIndex;

		/**
		 * The name of the {@code private static final} field of the generated
		 * {@link JVMChunk} subclass in which the corresponding AvailObject is
		 * located, or {@code null} if no field is required.
		 */
		final @Nullable String fieldName;

		/**
		 * The {@link Consumer} that generates an access of the literal when
		 * {@linkplain Consumer#accept(Object) evaluated}.
		 */
		final Consumer<MethodVisitor> getter;

		/**
		 * The {@link Consumer} that generates storage of the literal when
		 * {@linkplain Consumer#accept(Object) evaluated}, or {@code null} if
		 * no such facility is required. The generated code assumes that the
		 * value to install is on top of the stack.
		 */
		final @Nullable Consumer<MethodVisitor> setter;

		/**
		 * Construct a new {@code LiteralAccessor}.
		 *
		 * @param classLoaderIndex
		 *        The index into the {@link JVMChunkClassLoader}'s {@linkplain
		 *        JVMChunkClassLoader#parameters parameters} array at which the
		 *        corresponding {@linkplain AvailObject literal} is located,
		 *        or {@link #invalidIndex} if no slot is required.
		 * @param fieldName
		 *        The name of the {@code private static final} field of the
		 *        generated {@link JVMChunk} subclass in which the corresponding
		 *        {@linkplain AvailObject literal} is located, or {@code null}
		 *        if no field is required.
		 * @param getter
		 *        The {@link Consumer} that generates an access of the literal
		 *        when {@linkplain Consumer#accept(Object) evaluated}.
		 * @param setter
		 *        The {@link Consumer} that generates storage of the literal
		 *        when {@linkplain Consumer#accept(Object) evaluated}, or
		 *        {@code null} if no such facility is required. The generated
		 *        code assumes that the value to install is on top of the stack.
		 */
		LiteralAccessor (
			final int classLoaderIndex,
			final @Nullable String fieldName,
			final Consumer<MethodVisitor> getter,
			final @Nullable Consumer<MethodVisitor> setter)
		{
			this.classLoaderIndex = classLoaderIndex;
			this.fieldName = fieldName;
			this.getter = getter;
			this.setter = setter;
		}
	}

	/**
	 * The {@linkplain Object literals} used by the {@link L2Chunk} that must be
	 * embedded into the translated {@link JVMChunk}, mapped to their
	 * {@linkplain LiteralAccessor accessors}.
	 */
	final Map<Object, LiteralAccessor> literals = new HashMap<>();

	/**
	 * Emit code to push the specified literal on top of the stack.
	 *
	 * @param method
	 *        The {@linkplain MethodVisitor method} into which the generated JVM
	 *        instructions will be written.
	 * @param object
	 *        The literal.
	 */
	@SuppressWarnings("OverloadedMethodsWithSameNumberOfParameters")
	public void literal (final MethodVisitor method, final Object object)
	{
		stripNull(literals.get(object)).getter.accept(method);
	}

	/**
	 * Throw an {@link UnsupportedOperationException}. It is never valid to
	 * treat an {@link L2Operand} as a JVM literal, so this method is marked as
	 * {@linkplain Deprecated} to protect against code cloning and refactoring
	 * errors by a programmer.
	 *
	 * @param method
	 *        Unused.
	 * @param operand
	 *        Unused.
	 */
	@SuppressWarnings({
		"unused",
		"MethodMayBeStatic",
		"OverloadedMethodsWithSameNumberOfParameters"
	})
	@Deprecated
	public void literal (final MethodVisitor method, final L2Operand operand)
	{
		throw new UnsupportedOperationException();
	}

	/**
	 * Throw an {@link UnsupportedOperationException}. It is never valid to
	 * treat an {@link L2Register} as a Java literal, so this method is marked
	 * as {@linkplain Deprecated} to protect against code cloning and
	 * refactoring errors by a programmer.
	 *
	 * @param method
	 *        Unused.
	 * @param reg
	 *        Unused.
	 */
	@SuppressWarnings({
		"unused",
		"MethodMayBeStatic",
		"OverloadedMethodsWithSameNumberOfParameters"
	})
	@Deprecated
	public void literal (final MethodVisitor method, final L2Register reg)
	{
		throw new UnsupportedOperationException();
	}

	/**
	 * The {@link L2PcOperand}'s encapsulated program counters, mapped to their
	 * {@linkplain Label labels}.
	 */
	final Map<Integer, Label> labels = new HashMap<>();

	/**
	 * Answer the {@link Label} for the specified {@link L2Instruction}
	 * {@linkplain L2Instruction#offset() offset}.
	 *
	 * @param offset
	 *        The offset.
	 * @return The requested {@code Label}.
	 */
	public Label labelFor (final int offset)
	{
		return stripNull(labels.get(offset));
	}

	/**
	 * The mapping of registers to locals, partitioned by kind.
	 *
	 * The {@link L2Register}s used by the {@link L2Chunk}, mapped to their
	 * JVM local indices.
	 */
	final EnumMap<RegisterKind, Map<Integer, Integer>> locals =
		Arrays.stream(RegisterKind.values()).collect(
			toMap(
				Function.identity(),
				k -> new HashMap<>(),
				(a, b) -> { throw new RuntimeException("Impossible"); },
				() -> new EnumMap<>(RegisterKind.class)));

	/**
	 * Answer the next JVM local. The initial value is chosen to skip over the
	 * Category-1 receiver and Category-1 {@link Interpreter} formal parameters.
	 */
	private int nextLocal = 4;

	/**
	 * Answer the next JVM local for use within generated code produced by
	 * {@linkplain #generateRunChunk()}.
	 *
	 * @param type
	 *        The {@linkplain Type type} of the local.
	 * @return A JVM local.
	 */
	public int nextLocal (final Type type)
	{
		assert type != VOID_TYPE;
		final int local = nextLocal;
		nextLocal += type.getSize();
		return local;
	}

	/**
	 * Answer the JVM local number for this register.  This is the position
	 * within the actual JVM stack frame layout.
	 *
	 * @param register The {@link L2Register}
	 * @return Its position in the JVM frame.
	 */
	public int localNumberFromRegister (final L2Register register)
	{
		return locals.get(register.registerKind()).get(register.finalIndex());
	}

	/**
	 * Generate a load of the local associated with the specified {@link
	 * L2Register}.
	 *
	 * @param method
	 *        The {@linkplain MethodVisitor method} into which the generated JVM
	 *        instructions will be written.
	 * @param register
	 *        A bound {@code L2Register}.
	 */
	public void load (
		final MethodVisitor method,
		final L2Register register)
	{
		method.visitVarInsn(
			register.registerKind().loadInstruction,
			localNumberFromRegister(register));
	}

	/**
	 * Generate a store into the local associated with the specified {@link
	 * L2Register}. The value to be stored should already be on top of
	 * the stack and correctly typed.
	 *
	 * @param method
	 *        The {@linkplain MethodVisitor method} into which the generated JVM
	 *        instructions will be written.
	 * @param register
	 *        A bound {@code L2Register}.
	 */
	public void store (
		final MethodVisitor method,
		final L2Register register)
	{
		method.visitVarInsn(
			register.registerKind().storeInstruction,
			localNumberFromRegister(register));
	}

	/** Helper for stripping "_TAG" from end of tag names. */
	static final Pattern tagEndPattern = Pattern.compile("_TAG$");

	/**
	 * A {@code JVMTranslationPreparer} acts upon its enclosing {@link
	 * JVMTranslator} and an {@link L2Operand} to map {@link L2Register}s to JVM
	 * {@linkplain #nextLocal(Type) locals}, map {@linkplain AvailObject
	 * literals} to {@code private static final} fields, and map {@linkplain
	 * L2PcOperand program counters} to {@link Label}s.
	 *
	 * @author Todd L Smith &lt;todd@availlang.org&gt;
	 */
	class JVMTranslationPreparer
	implements L2OperandDispatcher
	{
		/**
		 * The next unallocated index into the {@link JVMChunkClassLoader}'s
		 * {@linkplain JVMChunkClassLoader#parameters parameters} array at which
		 * a {@linkplain AvailObject literal} will be stored.
 		 */
		private int nextClassLoaderIndex = 0;

		@Override
		public void doOperand (final L2CommentOperand operand)
		{
			// Ignore comments; there's nowhere to put them in the translated
			// code, and not much to do with them even if we could.
		}

		@Override
		public void doOperand (final L2ConstantOperand operand)
		{
			recordLiteralObject(operand.object);
		}

		@Override
		public void doOperand (final L2IntImmediateOperand operand)
		{
			literals.computeIfAbsent(
				operand.value,
				object -> new LiteralAccessor(
					invalidIndex,
					null,
					method -> intConstant(method, (int) object),
					null));
		}

		@Override
		public void doOperand (final L2FloatImmediateOperand operand)
		{
			literals.computeIfAbsent(
				operand.value,
				object -> new LiteralAccessor(
					invalidIndex,
					null,
					method -> doubleConstant(method, (double) object),
					null));
		}

		@Override
		public void doOperand (final L2PcOperand operand)
		{
			if (operand.counter != null)
			{
				recordLiteralObject(operand.counter);
			}
			labels.computeIfAbsent(
				operand.offset(),
				pc -> new Label());
		}

		@Override
		public void doOperand (final L2PrimitiveOperand operand)
		{
			recordLiteralObject(operand.primitive);
		}

		@Override
		public void doOperand (final L2ReadIntOperand operand)
		{
			locals.get(RegisterKind.INTEGER).computeIfAbsent(
				operand.register().finalIndex(),
				i -> nextLocal(INT_TYPE));
		}

		@Override
		public void doOperand (final L2ReadFloatOperand operand)
		{
			locals.get(RegisterKind.FLOAT).computeIfAbsent(
				operand.register().finalIndex(),
				i -> nextLocal(DOUBLE_TYPE));
		}

		@Override
		public void doOperand (final L2ReadBoxedOperand operand)
		{
			locals.get(RegisterKind.BOXED).computeIfAbsent(
				operand.register().finalIndex(),
				i -> nextLocal(getType(AvailObject.class)));
		}

		@Override
		public void doOperand (final L2ReadBoxedVectorOperand vector)
		{
			vector.elements().forEach(this::doOperand);
		}

		@Override
		public void doOperand (final L2ReadIntVectorOperand vector)
		{
			vector.elements().forEach(this::doOperand);
		}

		@Override
		public void doOperand (final L2ReadFloatVectorOperand vector)
		{
			vector.elements().forEach(this::doOperand);
		}

		@Override
		public void doOperand (final L2SelectorOperand operand)
		{
			recordLiteralObject(operand.bundle);
		}

		@Override
		public void doOperand (final L2WriteIntOperand operand)
		{
			locals.get(RegisterKind.INTEGER).computeIfAbsent(
				operand.register().finalIndex(),
				i -> nextLocal(INT_TYPE));
		}

		@Override
		public void doOperand (final L2WriteFloatOperand operand)
		{
			locals.get(RegisterKind.FLOAT).computeIfAbsent(
				operand.register().finalIndex(),
				i -> nextLocal(DOUBLE_TYPE));
		}

		@Override
		public void doOperand (final L2WriteBoxedOperand operand)
		{
			locals.get(RegisterKind.BOXED).computeIfAbsent(
				operand.register().finalIndex(),
				i -> nextLocal(getType(AvailObject.class)));
		}

		/**
		 * Create a literal slot for the given arbitrary {@link Object}.
		 *
		 * @param value The actual literal value to capture.
		 */
		private void recordLiteralObject (final Object value)
		{
			literals.computeIfAbsent(
				value,
				object ->
				{
					// Choose an index and name for the literal.
					final int index = nextClassLoaderIndex++;
					final String description;
					if (value instanceof AvailObject)
					{
						description = tagEndPattern
							.matcher(((AvailObject) value).typeTag().name())
							.replaceAll("");
					}
					else if (value instanceof Primitive)
					{
						description = ((Primitive) value).name();
					}
					else
					{
						description = value.getClass().getSimpleName();
					}
					final String name = "literal_" + index + "_" + description;
					final Class<?> type = object.getClass();
					// Generate a field that will hold the literal at runtime.
					final FieldVisitor field = classWriter.visitField(
						ACC_PRIVATE | ACC_STATIC | ACC_FINAL,
						name,
						getDescriptor(type),
						null,
						null);
					field.visitAnnotation(getDescriptor(Nonnull.class), true);
					field.visitEnd();
					// Generate an accessor for the literal.
					return new LiteralAccessor(
						index,
						name,
						method -> method.visitFieldInsn(
							GETSTATIC,
							classInternalName,
							name,
							getDescriptor(type)),
						method ->
						{
							method.visitTypeInsn(
								CHECKCAST,
								getInternalName(type));
							method.visitFieldInsn(
								PUTSTATIC,
								classInternalName,
								name,
								getDescriptor(type));
						});
				});
		}
	}

	/**
	 * Prepare for JVM translation by {@linkplain JVMTranslationPreparer
	 * visiting} each of the {@link L2Instruction}s to be translated.
	 */
	void prepare ()
	{
		final JVMTranslationPreparer preparer = new JVMTranslationPreparer();
		for (final L2Instruction instruction : instructions)
		{
			if (instruction.isEntryPoint())
			{
				final Label label = new Label();
				entryPoints.put(instruction.offset(), label);
				labels.put(instruction.offset(), label);
			}
			instruction.operandsDo(
				operand ->  operand.dispatchOperand(preparer));
		}
	}

	/**
	 * Dump a trace of the specified {@linkplain Throwable exception} to an
	 * appropriately named file.
	 *
	 * @param e
	 *        The exception.
	 * @return The absolute path of the resultant file, or {@code null} if the
	 *         file could not be written.
	 */
	@SuppressWarnings("UnusedReturnValue")
	private @Nullable String dumpTraceToFile (final Throwable e)
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
			final Path traceFile = dir.resolve(base + ".trace");
			// Make the trace file potentially *much* smaller by truncating the
			// empty space reserved for per-instruction stack and locals to 5
			// spaces each.
			@SuppressWarnings("DynamicRegexReplaceableByCompiledPattern")
			final String trace = traceFor(e).replaceAll(
				" {6,}", "     ");
			final ByteBuffer buffer = StandardCharsets.UTF_8.encode(trace);
			final byte[] bytes = new byte[buffer.limit()];
			buffer.get(bytes);
			Files.write(traceFile, bytes);
			return traceFile.toAbsolutePath().toString();
		}
		catch (final IOException x)
		{
			Interpreter.log(
				Interpreter.loggerDebugJVM,
				Level.WARNING,
				"unable to write trace for failed generated class {0}",
				classInternalName);
			return null;
		}
	}

	/**
	 * Finish visiting the {@link MethodVisitor} by calling {@link
	 * MethodVisitor#visitMaxs(int, int) visitMaxs} and then {@link
	 * MethodVisitor#visitEnd() visitEnd}. If {@link #debugJVM} is {@code true},
	 * then an attempt will be made to write out a trace file.
	 *
	 * @param method
	 *        The {@linkplain MethodVisitor method} into which the generated JVM
	 *        instructions will be written.
	 */
	private void finishMethod (final MethodVisitor method)
	{
		method.visitMaxs(0, 0);
		try
		{
			method.visitEnd();
		}
		catch (final Exception e)
		{
			if (debugJVM)
			{
				Interpreter.log(
					Interpreter.loggerDebugJVM,
					Level.SEVERE,
					"translation failed for {0}",
					className);
				dumpTraceToFile(e);
			}
			throw e;
		}
	}

	/**
	 * Generate the {@code static} initializer of the target {@link JVMChunk}.
	 * The static initializer is responsible for moving any of the {@linkplain
	 * JVMChunkClassLoader#parameters parameters} of the {@link JVMChunk}
	 * subclass's {@link JVMChunkClassLoader} into appropriate {@code
	 * private static final} fields.
	 */
	void generateStaticInitializer ()
	{
		final MethodVisitor method = classWriter.visitMethod(
			ACC_STATIC | ACC_PUBLIC,
			"<clinit>",
			getMethodDescriptor(VOID_TYPE),
			null,
			null);
		method.visitCode();
		// :: «generated JVMChunk».class.getClassLoader()
		method.visitLdcInsn(getType("L" + classInternalName + ";"));
		JavaLibrary.getGetClassLoader().generateCall(method);
		method.visitTypeInsn(
			CHECKCAST,
			getInternalName(JVMChunkClassLoader.class));
		final List<LiteralAccessor> accessors =
			literals.values().stream()
				.sorted(comparingInt(accessor -> accessor.classLoaderIndex))
				.filter(accessor -> accessor.setter != null)
				.collect(toList());
		if (!accessors.isEmpty())
		{
			// :: «generated JVMChunk».class.getClassLoader().parameters
			method.visitInsn(DUP);
			JVMChunkClassLoader.parametersField.generateRead(method);
			final int limit = accessors.size();
			int i = 0;
			for (final LiteralAccessor accessor : accessors)
			{
				// :: literal_«i» = («typeof(literal_«i»)») parameters[«i»];
				if (i < limit - 1)
				{
					method.visitInsn(DUP);
				}
				intConstant(method, accessor.classLoaderIndex);
				method.visitInsn(AALOAD);
				stripNull(accessor.setter).accept(method);
				i++;
			}
		}
		// :: «generated JVMChunk».class.getClassLoader().parameters = null;
		method.visitInsn(ACONST_NULL);
		JVMChunkClassLoader.parametersField.generateWrite(method);
		method.visitInsn(RETURN);
		finishMethod(method);
	}

	/**
	 * Answer the JVM local for the receiver of a generated implementation of
	 * {@link JVMChunk#runChunk(Interpreter, int)}.
	 *
	 * @return The receiver local.
	 */
	private static int receiverLocal ()
	{
		return 0;
	}

	/**
	 * Generate access of the receiver (i.e., {@code this}).
	 *
	 * @param method
	 *        The {@linkplain MethodVisitor method} into which the generated JVM
	 *        instructions will be written.
	 */
	@SuppressWarnings({"MethodMayBeStatic", "WeakerAccess"})
	public void loadReceiver (final MethodVisitor method)
	{
		method.visitVarInsn(ALOAD, receiverLocal());
	}

	/**
	 * Answer the JVM local for the {@link Interpreter} formal parameter of a
	 * generated implementation of {@link JVMChunk#runChunk(Interpreter, int)}.
	 *
	 * @return The {@code Interpreter} formal parameter local.
	 */
	@SuppressWarnings("WeakerAccess")
	public static int interpreterLocal ()
	{
		return 1;
	}

	/**
	 * Generate access to the JVM local for the {@link Interpreter} formal
	 * parameter of a generated implementation of {@link
	 * JVMChunk#runChunk(Interpreter, int)}.
	 *
	 * @param method
	 *        The {@linkplain MethodVisitor method} into which the generated JVM
	 *        instructions will be written.
	 */
	@SuppressWarnings("MethodMayBeStatic")
	public void loadInterpreter (final MethodVisitor method)
	{
		method.visitVarInsn(ALOAD, interpreterLocal());
	}

	/**
	 * Answer the JVM local for the {@code offset} formal parameter of a
	 * generated implementation of {@link JVMChunk#runChunk(Interpreter, int)}.
	 *
	 * @return The {@code offset} formal parameter local.
	 */
	@SuppressWarnings({"MethodMayBeStatic", "WeakerAccess"})
	public int offsetLocal ()
	{
		return 2;
	}

	/**
	 * Answer the JVM local for the {@link StackReifier} local variable of a
	 * generated implementation of {@link JVMChunk#runChunk(Interpreter, int)}.
	 *
	 * @return The {@code StackReifier} local.
	 */
	@SuppressWarnings("MethodMayBeStatic")
	public int reifierLocal ()
	{
		return 3;
	}

	/**
	 * Emit the effect of loading a constant {@code int} to the specified
	 * {@link MethodVisitor}.
	 *
	 * @param method
	 *        The {@linkplain MethodVisitor method} into which the generated JVM
	 *        instructions will be written.
	 * @param value
	 *        The {@code int}.
	 */
	@SuppressWarnings("MethodMayBeStatic")
	public void intConstant (final MethodVisitor method, final int value)
	{
		switch (value)
		{
			case -1:
				method.visitInsn(ICONST_M1);
				break;
			case 0:
				method.visitInsn(ICONST_0);
				break;
			case 1:
				method.visitInsn(ICONST_1);
				break;
			case 2:
				method.visitInsn(ICONST_2);
				break;
			case 3:
				method.visitInsn(ICONST_3);
				break;
			case 4:
				method.visitInsn(ICONST_4);
				break;
			case 5:
				method.visitInsn(ICONST_5);
				break;
			default:
			{
				if (value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE)
				{
					method.visitIntInsn(BIPUSH, value);
				}
				else if (value >= Short.MIN_VALUE && value <= Short.MAX_VALUE)
				{
					method.visitIntInsn(SIPUSH, value);
				}
				else
				{
					method.visitLdcInsn(value);
				}
				break;
			}
		}
	}

	/**
	 * Emit the effect of loading a constant {@code long} to the specified
	 * {@link MethodVisitor}.
	 *
	 * @param method
	 *        The {@linkplain MethodVisitor method} into which the generated JVM
	 *        instructions will be written.
	 * @param value
	 *        The {@code long}.
	 */
	public void longConstant (final MethodVisitor method, final long value)
	{
		if (value == 0L)
		{
			method.visitInsn(LCONST_0);
		}
		else if (value == 1L)
		{
			method.visitInsn(LCONST_1);
		}
		else if (value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE)
		{
			intConstant(method, (int) value);
			// Emit a conversion, so that we end up with a long on the stack.
			method.visitInsn(I2L);
		}
		else
		{
			// This should emit an ldc2_w instruction, whose result type
			// is long; no conversion instruction is required.
			method.visitLdcInsn(value);
		}
	}

	/**
	 * Emit the effect of loading a constant {@code float} to the specified
	 * {@link MethodVisitor}.
	 *
	 * @param method
	 *        The {@linkplain MethodVisitor method} into which the generated JVM
	 *        instructions will be written.
	 * @param value
	 *        The {@code float}.
	 */
	@SuppressWarnings({"unused", "FloatingPointEquality"})
	public void floatConstant (final MethodVisitor method, final float value)
	{
		if (value == 0.0f)
		{
			method.visitInsn(FCONST_0);
		}
		else if (value == 1.0f)
		{
			method.visitInsn(FCONST_1);
		}
		else if (value == 2.0f)
		{
			method.visitInsn(FCONST_2);
		}
		// This is the largest absolute int value that will fit into the
		// mantissa of a normalized float, and therefore the largest value that
		// can be pushed and converted to float without loss of precision.
		else if (
			value >= -33_554_431 && value <= 33_554_431
				&& value == Math.floor(value))
		{
			intConstant(method, (int) value);
			method.visitInsn(I2F);
		}
		else
		{
			// This should emit an ldc instruction, whose result type is float;
			// no conversion instruction is required.
			method.visitLdcInsn(value);
		}
	}

	/**
	 * Emit the effect of loading a constant {@code double} to the specified
	 * {@link MethodVisitor}.
	 *
	 * @param method
	 *        The {@linkplain MethodVisitor method} into which the generated JVM
	 *        instructions will be written.
	 * @param value
	 *        The {@code double}.
	 */
	@SuppressWarnings("FloatingPointEquality")
	public void doubleConstant (final MethodVisitor method, final double value)
	{
		if (value == 0.0d)
		{
			method.visitInsn(DCONST_0);
		}
		else if (value == 1.0d)
		{
			method.visitInsn(DCONST_1);
		}
		// This is the largest absolute int value that will fit into the
		// mantissa of a normalized double, and therefore the largest absolute
		// value that can be pushed and converted to double without loss of
		// precision.
		else if (
			value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE
				&& value == Math.floor(value))
		{
			intConstant(method, (int) value);
			method.visitInsn(I2D);
		}
		// This is the largest absolute long value that will fit into the
		// mantissa of a normalized double, and therefore the largest absolute
		// value that can be pushed and converted to double without loss of
		// precision.
		else if (
			value >= -18_014_398_509_481_983L
				&& value <= 18_014_398_509_481_983L
				&& value == Math.floor(value))
		{
			longConstant(method, (long) value);
			method.visitInsn(L2D);
		}
		else
		{
			// This should emit an ldc2_w instruction, whose result type is
			// double; no conversion instruction is required.
			method.visitLdcInsn(value);
		}
	}

	/**
	 * Emit code to store each of the {@link L2IntRegister}s into a new
	 * {@code int} array. Leave the new array on top of the stack.
	 *
	 * @param method
	 *        The {@linkplain MethodVisitor method} into which the generated JVM
	 *        instructions will be written.
	 * @param operands
	 *        The {@link L2ReadIntOperand}s that hold the registers.
	 */
	@SuppressWarnings("unused")
	public void integerArray (
		final MethodVisitor method,
		final List<L2ReadIntOperand> operands)
	{
		if (operands.isEmpty())
		{
			JVMChunk.noIntsField.generateRead(method);
		}
		else
		{
			// :: array = new int[«limit»];
			final int limit = operands.size();
			intConstant(method, limit);
			method.visitIntInsn(NEWARRAY, T_INT);
			for (int i = 0; i < limit; i++)
			{
				// :: array[«i»] = «operands[i]»;
				method.visitInsn(DUP);
				intConstant(method, i);
				load(method, operands.get(i).register());
				method.visitInsn(IASTORE);
			}
		}
	}

	/**
	 * Emit code to store each of the {@link L2BoxedRegister}s into a new
	 * array. Leave the new array on top of the stack.
	 *
	 * @param method
	 *        The {@linkplain MethodVisitor method} into which the generated JVM
	 *        instructions will be written.
	 * @param operands
	 *        The {@link L2ReadBoxedOperand}s that hold the registers.
	 * @param arrayClass
	 *        The element type of the new array.
	 */
	public void objectArray (
		final MethodVisitor method,
		final List<L2ReadBoxedOperand> operands,
		final Class<? extends A_BasicObject> arrayClass)
	{
		if (operands.isEmpty())
		{
			JVMChunk.noObjectsField.generateRead(method);
		}
		else
		{
			// :: array = new «arrayClass»[«limit»];
			final int limit = operands.size();
			intConstant(method, limit);
			method.visitTypeInsn(ANEWARRAY, getInternalName(arrayClass));
			for (int i = 0; i < limit; i++)
			{
				// :: array[«i»] = «operands[i]»;
				method.visitInsn(DUP);
				intConstant(method, i);
				load(method, operands.get(i).register());
				method.visitInsn(AASTORE);
			}
		}
	}

	/**
	 * Answer the JVM branch {@linkplain Opcodes opcode} with the reversed
	 * sense.
	 *
	 * @param opcode
	 *        The JVM opcode, e.g., {@link Opcodes#IFEQ}, that decides between
	 *        the two branch targets.
	 * @return The branch opcode with the reversed sense.
	 */
	@SuppressWarnings({"MethodMayBeStatic", "WeakerAccess"})
	public int reverseOpcode (final int opcode)
	{
		final int reversedOpcode;
		switch (opcode)
		{
			case IFEQ:
				reversedOpcode = IFNE;
				break;
			case IFNE:
				reversedOpcode = IFEQ;
				break;
			case IFLT:
				reversedOpcode = IFGE;
				break;
			case IFLE:
				reversedOpcode = IFGT;
				break;
			case IFGE:
				reversedOpcode = IFLT;
				break;
			case IFGT:
				reversedOpcode = IFLE;
				break;
			case IF_ICMPEQ:
				reversedOpcode = IF_ICMPNE;
				break;
			case IF_ICMPNE:
				reversedOpcode = IF_ICMPEQ;
				break;
			case IF_ICMPLT:
				reversedOpcode = IF_ICMPGE;
				break;
			case IF_ICMPLE:
				reversedOpcode = IF_ICMPGT;
				break;
			case IF_ICMPGE:
				reversedOpcode = IF_ICMPLT;
				break;
			case IF_ICMPGT:
				reversedOpcode = IF_ICMPLE;
				break;
			case IF_ACMPEQ:
				reversedOpcode = IF_ACMPNE;
				break;
			case IF_ACMPNE:
				reversedOpcode = IF_ACMPEQ;
				break;
			case IFNULL:
				reversedOpcode = IFNONNULL;
				break;
			case IFNONNULL:
				reversedOpcode = IFNULL;
				break;
			default:
				assert false : String.format("bad opcode (%d)", opcode);
				throw new RuntimeException(
					String.format("bad opcode (%d)", opcode));
		}
		return reversedOpcode;
	}

	/**
	 * Emit code to unconditionally branch to the specified {@linkplain
	 * L2PcOperand program counter}.
	 *
	 * @param method
	 *        The {@linkplain MethodVisitor method} into which the generated JVM
	 *        instructions will be written.
	 * @param instruction
	 *        The {@link L2Instruction} that includes the operand.
	 * @param operand
	 *        The {@code L2PcOperand} that specifies the branch target.
	 */
	public void jump (
		final MethodVisitor method,
		final L2Instruction instruction,
		final L2PcOperand operand)
	{
		final int pc = operand.offset();
		// If the jump target is the very next instruction, then don't emit a
		// jump at all; just fall through.
		if (instruction.offset() != pc - 1)
		{
			method.visitJumpInsn(GOTO, labelFor(pc));
		}
	}

	/**
	 * Emit code to conditionally branch to one of the specified {@linkplain
	 * L2PcOperand program counters}.
	 *
	 * @param method
	 *        The {@linkplain MethodVisitor method} into which the generated JVM
	 *        instructions will be written.
	 * @param instruction
	 *        The {@link L2Instruction} that includes the operands.
	 * @param opcode
	 *        The JVM opcode, e.g., {@link Opcodes#IFEQ}, that decides between
	 *        the two branch targets.
	 * @param success
	 *        The {@code L2PcOperand} that specifies the branch target in the
	 *        event that the opcode succeeds, i.e., actually branches.
	 * @param failure
	 *        The {@code L2PcOperand} that specifies the branch target in the
	 *        event that the opcode fails, i.e., does not actually branch and
	 *        falls through to a branch.
	 * @param successCounter
	 *        An {@link LongAdder} to increment each time the branch is taken.
	 * @param failureCounter
	 *        An {@link LongAdder} to increment each time the branch falls
	 *        through.
	 */
	public void branch (
		final MethodVisitor method,
		final L2Instruction instruction,
		final int opcode,
		final L2PcOperand success,
		final L2PcOperand failure,
		final LongAdder successCounter,
		final LongAdder failureCounter)
	{
		final int offset = instruction.offset();
		final int successPc = success.offset();
		final int failurePc = failure.offset();
		if (offset == failurePc - 1)
		{
			generateBranch(
				method, opcode, successCounter, failureCounter, successPc);
			// Fall through to failurePc.
		}
		else
		{
			generateBranch(
				method,
				reverseOpcode(opcode),
				failureCounter,
				successCounter,
				failurePc);
			// If the success branch targets the next instruction, fall through,
			// otherwise jump to it.
			if (offset != successPc - 1)
			{
				method.visitJumpInsn(GOTO, labelFor(successPc));
			}
		}
	}

	/**
	 * Generate a branch, with associated counter tracking.  The generated Java
	 * bytecodes have this form:
	 *
	 * <pre>
	 *     jump to notTakenStub if the given opcode's condition <em>fails</em>
	 *     increment takenCounter
	 *     jump to takenPc
	 *     notTakenStub: increment notTakenCounter
	 *     (fall through)
	 *     notTakenPc:
	 *     ...
	 *     takenPc:
	 *     ...
	 * </pre>
	 *
	 * @param method
	 *        The {@link MethodVisitor} on which to generate the branch.
	 * @param branchOpcode
	 *        The opcode to effect the branch.  This will be reversed internally
	 *        to make it easier to increment the notTakenCounter before falling
	 *        through.
	 * @param takenCounter
	 *        The {@link LongAdder} to increment when the branch is taken.
	 * @param notTakenCounter
	 *        The {@link LongAdder} to increment when the branch is not taken.
	 * @param takenPc
	 *        The L2 program counter to jump to if the branch is taken.
	 */
	private void generateBranch (
		final MethodVisitor method,
		final int branchOpcode,
		final LongAdder takenCounter,
		final LongAdder notTakenCounter,
		final int takenPc)
	{
		final Label logNotTaken = new Label();
		method.visitJumpInsn(reverseOpcode(branchOpcode), logNotTaken);
		literal(method, takenCounter);
		JavaLibrary.getLongAdderIncrement().generateCall(method);
		method.visitJumpInsn(GOTO, labelFor(takenPc));
		method.visitLabel(logNotTaken);
		literal(method, notTakenCounter);
		JavaLibrary.getLongAdderIncrement().generateCall(method);
	}

	/**
	 * Emit code to conditionally branch to one of the specified {@linkplain
	 * L2PcOperand program counters}.
	 *
	 * @param method
	 *        The {@linkplain MethodVisitor method} into which the generated JVM
	 *        instructions will be written.
	 * @param instruction
	 *        The {@link L2Instruction} that includes the operands.
	 * @param opcode
	 *        The JVM opcode, e.g., {@link Opcodes#IFEQ}, that decides between
	 *        the two branch targets.
	 * @param success
	 *        The {@code L2PcOperand} that specifies the branch target in the
	 *        event that the opcode succeeds, i.e., actually branches.
	 * @param failure
	 *        The {@code L2PcOperand} that specifies the branch target in the
	 *        event that the opcode fails, i.e., does not actually branch and
	 *        falls through to a branch.
	 */
	public void branch (
		final MethodVisitor method,
		final L2Instruction instruction,
		final int opcode,
		final L2PcOperand success,
		final L2PcOperand failure)
	{
		final int offset = instruction.offset();
		final int successPc = success.offset();
		final int failurePc = failure.offset();
		if (offset == failurePc - 1)
		{
			// The failure branch targets the next instruction, so just fall
			// through to it.
			method.visitJumpInsn(opcode, labelFor(successPc));
		}
		else if (offset == successPc - 1)
		{
			// The success branch targets the next instruction, so reverse the
			// sense of the opcode and fall through.
			method.visitJumpInsn(reverseOpcode(opcode), labelFor(failurePc));
		}
		else
		{
			// Neither branch target is next, so emit the most general version
			// of the logic.
			method.visitJumpInsn(opcode, labelFor(successPc));
			method.visitJumpInsn(GOTO, labelFor(failurePc));
		}
	}

	/**
	 * Generate the default constructor [{@code ()V}] of the target {@link
	 * JVMChunk}.
	 */
	void generateConstructorV ()
	{
		final MethodVisitor method = classWriter.visitMethod(
			ACC_PUBLIC | ACC_MANDATED,
			"<init>",
			getMethodDescriptor(VOID_TYPE),
			null,
			null);
		method.visitCode();
		loadReceiver(method);
		JVMChunk.chunkConstructor.generateCall(method);
		method.visitInsn(RETURN);
		method.visitMaxs(0, 0);
		method.visitEnd();
	}

	/**
	 * Generate the {@link JVMChunk#name()} method of the target {@link
	 * JVMChunk}.
	 */
	void generateName ()
	{
		final MethodVisitor method = classWriter.visitMethod(
			ACC_PUBLIC,
			"name",
			getMethodDescriptor(getType(String.class)),
			null,
			null);
		method.visitCode();
		method.visitLdcInsn(chunkName);
		method.visitInsn(ARETURN);
		method.visitMaxs(0, 0);
		method.visitEnd();
	}

	/**
	 * Dump the {@linkplain L1Operation L1 instructions} that comprise the
	 * {@link A_RawFunction function} to an appropriately named file.
	 *
	 * @return The absolute path of the resultant file, for inclusion in a
	 *         {@link JVMChunkL1Source} annotation of the generated {@link
	 *         JVMChunk} subclass, or {@code null} if the file could not be
	 *         written.
	 */
	private @Nullable String dumpL1SourceToFile ()
	{
		final @Nullable A_RawFunction function = code;
		assert function != null;
		try
		{
			final StringBuilder builder = new StringBuilder();
			builder.append(chunkName);
			builder.append(":\n\n");
			L1Disassembler.disassemble(
				function, builder, new IdentityHashMap<>(), 0);
			final int lastSlash =
				classInternalName.lastIndexOf('/');
			final String pkg =
				classInternalName.substring(0, lastSlash);
			final Path tempDir = Paths.get("debug", "jvm");
			final Path dir = tempDir.resolve(Paths.get(pkg));
			Files.createDirectories(dir);
			final String base = classInternalName.substring(lastSlash + 1);
			final Path l1File = dir.resolve(base + ".l1");
			final ByteBuffer buffer =
				StandardCharsets.UTF_8.encode(builder.toString());
			final byte[] bytes = new byte[buffer.limit()];
			buffer.get(bytes);
			Files.write(l1File, bytes);
			return l1File.toAbsolutePath().toString();
		}
		catch (final IOException e)
		{
			Interpreter.log(
				Interpreter.loggerDebugJVM,
				Level.WARNING,
				"unable to write L1 for generated class {0}",
				classInternalName);
			return null;
		}
	}

	/**
	 * Dump the {@linkplain L2ControlFlowGraphVisualizer visualized} {@link
	 * L2ControlFlowGraph} for the {@link L2Chunk} to an appropriately named
	 * file.
	 *
	 * @return The absolute path of the resultant file, for inclusion in a
	 *         {@link JVMChunkL2Source} annotation of the generated {@link
	 *         JVMChunk} subclass.
	 */
	private @Nullable String dumpL2SourceToFile ()
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
			final Path l2File = dir.resolve(base + ".dot");
			final StringBuilder builder = new StringBuilder();
			final L2ControlFlowGraphVisualizer visualizer =
				new L2ControlFlowGraphVisualizer(
					base,
					chunkName,
					80,
					controlFlowGraph,
					true,
					false,
					builder);
			visualizer.visualize();
			final ByteBuffer buffer = StandardCharsets.UTF_8.encode(
				builder.toString());
			final byte[] bytes = new byte[buffer.limit()];
			buffer.get(bytes);
			Files.write(l2File, bytes);
			return l2File.toAbsolutePath().toString();
		}
		catch (final IOException|UncheckedIOException e)
		{
			Interpreter.log(
				Interpreter.loggerDebugJVM,
				Level.WARNING,
				"unable to write L2 for generated class {0}",
				classInternalName);
			return null;
		}
	}

	/**
	 * {@code true} to enable JVM debugging, {@code false} otherwise.  When
	 * enabled, the generated JVM code dumps verbose information just prior to
	 * each L2 instruction.
	 */
	public static boolean debugJVM = false;

	/**
	 * Generate the {@link JVMChunk#runChunk(Interpreter, int)} method of the
	 * target {@link JVMChunk}.
	 */
	void generateRunChunk ()
	{
		final MethodVisitor method = classWriter.visitMethod(
			ACC_PUBLIC,
			"runChunk",
			getMethodDescriptor(
				getType(StackReifier.class),
				getType(Interpreter.class),
				INT_TYPE),
			null,
			null);
		method.visitParameter("interpreter", ACC_FINAL);
		method.visitParameterAnnotation(
			0,
			getDescriptor(Nonnull.class),
			true);
		method.visitParameter("offset", ACC_FINAL);
		if (debugJVM)
		{
			// Note that we have to break the sources up if they are too large
			// for the constant pool.
			//noinspection VariableNotUsedInsideIf
			if (code != null)
			{
				final @Nullable String l1Path = dumpL1SourceToFile();
				if (l1Path != null)
				{
					final AnnotationVisitor annotation = method.visitAnnotation(
						getDescriptor(JVMChunkL1Source.class),
						true);
					annotation.visit("sourcePath", l1Path);
					annotation.visitEnd();
				}
			}
			final @Nullable String l2Path = dumpL2SourceToFile();
			if (l2Path != null)
			{
				final AnnotationVisitor annotation = method.visitAnnotation(
					getDescriptor(JVMChunkL2Source.class),
					true);
				annotation.visit("sourcePath", l2Path);
				annotation.visitEnd();
			}
		}
		method.visitAnnotation(
			getDescriptor(Nullable.class),
			true);
		// Emit the lookupswitch instruction to select among the entry points.
		final int[] offsets = new int[entryPoints.size()];
		final Label[] entries = new Label[entryPoints.size()];
		{
			int i = 0;
			for (final Entry<Integer, Label> entry : entryPoints.entrySet())
			{
				offsets[i] = entry.getKey();
				entries[i] = entry.getValue();
				i++;
			}
		}
		method.visitCode();
		final Label startLabel = new Label();
		method.visitLabel(startLabel);
		// :: switch (offset) {…}
		method.visitVarInsn(ILOAD, offsetLocal());
		final Label badOffsetLabel = new Label();
		method.visitLookupSwitchInsn(badOffsetLabel, offsets, entries);
		// Translate the instructions.
		final @Nullable AvailThread thread = AvailThread.currentOrNull();
		final @Nullable Interpreter interpreter =
			thread != null ? thread.interpreter : null;
		for (final L2Instruction instruction : instructions)
		{
			final Label label = labels.get(instruction.offset());
			if (label != null)
			{
				method.visitLabel(label);
				method.visitLineNumber(instruction.offset(), label);
			}
			if (callTraceL2AfterEveryInstruction)
			{
				loadReceiver(method);  // this, the executable chunk.
				intConstant(method, instruction.offset());
				// First line of the instruction toString
				//noinspection DynamicRegexReplaceableByCompiledPattern
				method.visitLdcInsn(instruction.toString().split("\\n", 2)[0]);
				// Output the first read operand's value, as an Object, or null.
				pushOneObject:
				{
					for (final L2Operand operand : instruction.operands())
					{
						if (operand instanceof L2ReadBoxedOperand)
						{
							load(
								method,
								((L2ReadBoxedOperand) operand).register());
							break pushOneObject;
						}
						if (operand instanceof L2ReadIntOperand)
						{
							load(
								method,
								((L2ReadIntOperand) operand).register());
							JavaLibrary
								.getJavaUnboxIntegerMethod()
								.generateCall(method);
							break pushOneObject;
						}
					}
					// No suitable operands were found.  Use null.
					method.visitInsn(ACONST_NULL);
				}
				Interpreter.traceL2Method.generateCall(method);
			}
			final long beforeTranslation = captureNanos();
			instruction.translateToJVM(this, method);
			final long afterTranslation = captureNanos();
			if (interpreter != null)
			{
				instruction.operation().jvmTranslationTime.record(
					afterTranslation - beforeTranslation,
					interpreter.interpreterIndex);
			}
		}
		// An L2Chunk always ends with an explicit transfer of control, so we
		// shouldn't generate a return here.
		method.visitLabel(badOffsetLabel);
		// Visit each of the local variables in order to bind them to artificial
		// register names. At present, we just claim that every variable is live
		// from the beginning of the method until the badOffsetLabel, but we can
		// always tighten this up later if we care.
		for (final Entry<RegisterKind, Map<Integer, Integer>> outerEntry :
			locals.entrySet())
		{
			final RegisterKind kind = outerEntry.getKey();
			for (final Entry<Integer, Integer> innerEntry :
				outerEntry.getValue().entrySet())
			{
				final int finalIndex = innerEntry.getKey();
				final int localIndex = innerEntry.getValue();
				method.visitLocalVariable(
					kind.prefix + finalIndex,
					kind.jvmTypeString,
					null,
					entries[0],
					badOffsetLabel,
					localIndex);
			}
		}
		// :: JVMChunk.badOffset(interpreter.offset);
		method.visitVarInsn(ALOAD, interpreterLocal());
		Interpreter.offsetField.generateRead(method);
		JVMChunk.badOffsetMethod.generateCall(method);
		method.visitInsn(ATHROW);
		final Label endLabel = new Label();
		method.visitLabel(endLabel);
		method.visitLocalVariable(
			"interpreter",
			getDescriptor(Interpreter.class),
			null,
			startLabel,
			endLabel,
			interpreterLocal());
		method.visitLocalVariable(
			"offset",
			INT_TYPE.getDescriptor(),
			null,
			startLabel,
			endLabel,
			offsetLocal());
		method.visitLocalVariable(
			"reifier",
			getDescriptor(StackReifier.class),
			null,
			startLabel,
			endLabel,
			reifierLocal());
		finishMethod(method);
	}

	/** The final phase of JVM code generation. */
	void classVisitEnd ()
	{
		classWriter.visitEnd();
	}

	/**
	 * The generated {@link JVMChunk}, or {@code null} if no chunk could be
	 * constructed.
	 */
	private @Nullable JVMChunk jvmChunk;

	/**
	 * Answer the generated {@link JVMChunk}.
	 *
	 * @return The generated {@code JVMChunk}.
	 */
	public JVMChunk jvmChunk ()
	{
		return stripNull(jvmChunk);
	}

	/**
	 * Dump the specified JVM class bytes to an appropriately named temporary
	 * file.
	 */
	private void dumpClassBytesToFile ()
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
			Files.write(classFile, stripNull(classBytes));
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
	 * Populate {@link #classBytes}, dumping to a file for debugging if indicated.
	 */
	void createClassBytes ()
	{
		classBytes = classWriter.toByteArray();
		if (debugJVM)
		{
			dumpClassBytesToFile();
		}
	}

	/**
	 * Actually load the generated class into the running JVM.  Note that a
	 * special {@link JVMChunkClassLoader} must be used, so that the static
	 * initialization has access to the necessary constants referenced from the
	 * bytecodes.
	 */
	void loadClass ()
	{
		final Object[] parameters = new Object[literals.size()];
		for (final Entry<Object, LiteralAccessor> entry : literals.entrySet())
		{
			final int index = entry.getValue().classLoaderIndex;
			if (index != invalidIndex)
			{
				parameters[index] = entry.getKey();
			}
		}
		final JVMChunkClassLoader loader = new JVMChunkClassLoader();
		jvmChunk = loader.newJVMChunkFrom(
			chunkName,
			className,
			stripNull(classBytes),
			parameters);
	}

	/** The JVM code generation phases, in order. */
	enum GenerationPhase {
		/** Prepare to generate the JVM translation. */
		PREPARE(JVMTranslator::prepare),

		/** Create the static &lt;clinit&gt; method for capturing constants. */
		GENERATE_STATIC_INITIALIZER(JVMTranslator::generateStaticInitializer),

		/** Prepare the default constructor, invoked once via reflection. */
		GENERATE_CONSTRUCTOR_V(JVMTranslator::generateConstructorV),

		/** Generate the name() method. */
		GENERATE_NAME(JVMTranslator::generateName),

		/** Generate the runChunk() method. */
		GENERATE_RUN_CHUNK(JVMTranslator::generateRunChunk),

		/** Indicate code emission has completed. */
		VISIT_END(JVMTranslator::classVisitEnd),

		/** Create a byte array that would be the content of a class file. */
		CREATE_CLASS_BYTES(JVMTranslator::createClassBytes),

		/** Load the class into the running system. */
		LOAD_CLASS(JVMTranslator::loadClass);


		/** The action to perform for this phase. */
		private final Continuation1NotNull<JVMTranslator> action;

		/** Statistic about this L2 -> JVM translation phase. */
		private final Statistic statistic =
			new Statistic(name(), FINAL_JVM_TRANSLATION_TIME);

		/**
		 * Initialize the enum value.
		 *
		 * @param action What to do for this phase.
		 */
		GenerationPhase (final Continuation1NotNull<JVMTranslator> action)
		{
			this.action = action;
		}

		/** A private array of phases, since Enum.values() makes a copy. */
		private static final GenerationPhase[] all = values();

		/**
		 * Execute all JVM generation phases.
		 * @param jvmTranslator The {@link JVMTranslator} for which to execute.
		 */
		static void executeAll (final JVMTranslator jvmTranslator)
		{
			final @Nullable AvailThread thread = AvailThread.currentOrNull();
			final @Nullable Interpreter interpreter =
				thread != null ? thread.interpreter : null;
			for (final GenerationPhase phase : GenerationPhase.all)
			{
				final long before = captureNanos();
				phase.action.value(jvmTranslator);
				if (interpreter != null)
				{
					phase.statistic.record(
						captureNanos() - before,
						interpreter.interpreterIndex);
				}
			}
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
		classWriter.visitSource(sourceFileName, null);
		GenerationPhase.executeAll(this);
	}
}
