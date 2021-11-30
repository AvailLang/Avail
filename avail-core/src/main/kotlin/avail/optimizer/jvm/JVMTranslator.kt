/*
 * JVMTranslator.kt
 * Copyright © 1993-2021, The Avail Foundation, LLC.
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
package avail.optimizer.jvm

import avail.AvailRuntimeSupport
import avail.AvailThread
import avail.descriptor.atoms.A_Atom.Companion.atomName
import avail.descriptor.bundles.A_Bundle.Companion.message
import avail.descriptor.functions.A_RawFunction
import avail.descriptor.functions.A_RawFunction.Companion.methodName
import avail.descriptor.functions.A_RawFunction.Companion.module
import avail.descriptor.functions.ContinuationDescriptor.Companion.createDummyContinuationMethod
import avail.descriptor.module.A_Module.Companion.moduleName
import avail.descriptor.module.A_Module.Companion.moduleNameNative
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.AvailObject
import avail.descriptor.representation.NilDescriptor
import avail.descriptor.tuples.A_String
import avail.descriptor.types.CompiledCodeTypeDescriptor.Companion.mostGeneralCompiledCodeType
import avail.descriptor.types.FunctionTypeDescriptor.Companion.mostGeneralFunctionType
import avail.descriptor.types.TupleTypeDescriptor.Companion.stringType
import avail.descriptor.types.PrimitiveTypeDescriptor.Types
import avail.interpreter.JavaLibrary.getClassLoader
import avail.interpreter.JavaLibrary.javaUnboxIntegerMethod
import avail.interpreter.JavaLibrary.longAdderIncrement
import avail.interpreter.Primitive
import avail.interpreter.execution.Interpreter
import avail.interpreter.execution.Interpreter.Companion.log
import avail.interpreter.levelOne.L1Disassembler
import avail.interpreter.levelOne.L1Operation
import avail.interpreter.levelTwo.L2Chunk
import avail.interpreter.levelTwo.L2Instruction
import avail.interpreter.levelTwo.L2OperandDispatcher
import avail.interpreter.levelTwo.operand.L2ArbitraryConstantOperand
import avail.interpreter.levelTwo.operand.L2CommentOperand
import avail.interpreter.levelTwo.operand.L2ConstantOperand
import avail.interpreter.levelTwo.operand.L2FloatImmediateOperand
import avail.interpreter.levelTwo.operand.L2IntImmediateOperand
import avail.interpreter.levelTwo.operand.L2Operand
import avail.interpreter.levelTwo.operand.L2PcOperand
import avail.interpreter.levelTwo.operand.L2PcVectorOperand
import avail.interpreter.levelTwo.operand.L2PrimitiveOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedVectorOperand
import avail.interpreter.levelTwo.operand.L2ReadFloatOperand
import avail.interpreter.levelTwo.operand.L2ReadFloatVectorOperand
import avail.interpreter.levelTwo.operand.L2ReadIntOperand
import avail.interpreter.levelTwo.operand.L2ReadIntVectorOperand
import avail.interpreter.levelTwo.operand.L2SelectorOperand
import avail.interpreter.levelTwo.operand.L2WriteBoxedOperand
import avail.interpreter.levelTwo.operand.L2WriteFloatOperand
import avail.interpreter.levelTwo.operand.L2WriteIntOperand
import avail.interpreter.levelTwo.operation.L2_ENTER_L2_CHUNK
import avail.interpreter.levelTwo.operation.L2_SAVE_ALL_AND_PC_TO_INT
import avail.interpreter.levelTwo.register.L2BoxedRegister
import avail.interpreter.levelTwo.register.L2Register
import avail.interpreter.levelTwo.register.L2Register.RegisterKind
import avail.interpreter.levelTwo.register.L2Register.RegisterKind.BOXED_KIND
import avail.interpreter.levelTwo.register.L2Register.RegisterKind.FLOAT_KIND
import avail.interpreter.levelTwo.register.L2Register.RegisterKind.INTEGER_KIND
import avail.optimizer.L2ControlFlowGraph
import avail.optimizer.L2ControlFlowGraphVisualizer
import avail.optimizer.StackReifier
import avail.optimizer.jvm.JVMTranslator.LiteralAccessor.Companion.invalidIndex
import avail.performance.Statistic
import avail.performance.StatisticReport.FINAL_JVM_TRANSLATION_TIME
import avail.utility.Strings.traceFor
import avail.utility.structures.EnumMap
import avail.utility.structures.EnumMap.Companion.enumMap
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.ClassWriter.COMPUTE_FRAMES
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Opcodes.AALOAD
import org.objectweb.asm.Opcodes.AASTORE
import org.objectweb.asm.Opcodes.ACC_FINAL
import org.objectweb.asm.Opcodes.ACC_MANDATED
import org.objectweb.asm.Opcodes.ACC_PRIVATE
import org.objectweb.asm.Opcodes.ACC_PUBLIC
import org.objectweb.asm.Opcodes.ACC_STATIC
import org.objectweb.asm.Opcodes.ACONST_NULL
import org.objectweb.asm.Opcodes.ALOAD
import org.objectweb.asm.Opcodes.ANEWARRAY
import org.objectweb.asm.Opcodes.ARETURN
import org.objectweb.asm.Opcodes.ASM9
import org.objectweb.asm.Opcodes.ASTORE
import org.objectweb.asm.Opcodes.ATHROW
import org.objectweb.asm.Opcodes.BIPUSH
import org.objectweb.asm.Opcodes.CHECKCAST
import org.objectweb.asm.Opcodes.DCONST_0
import org.objectweb.asm.Opcodes.DCONST_1
import org.objectweb.asm.Opcodes.DSTORE
import org.objectweb.asm.Opcodes.DUP
import org.objectweb.asm.Opcodes.FCONST_0
import org.objectweb.asm.Opcodes.FCONST_1
import org.objectweb.asm.Opcodes.FCONST_2
import org.objectweb.asm.Opcodes.GETSTATIC
import org.objectweb.asm.Opcodes.GOTO
import org.objectweb.asm.Opcodes.I2L
import org.objectweb.asm.Opcodes.ICONST_0
import org.objectweb.asm.Opcodes.ICONST_1
import org.objectweb.asm.Opcodes.ICONST_2
import org.objectweb.asm.Opcodes.ICONST_3
import org.objectweb.asm.Opcodes.ICONST_4
import org.objectweb.asm.Opcodes.ICONST_5
import org.objectweb.asm.Opcodes.ICONST_M1
import org.objectweb.asm.Opcodes.IFEQ
import org.objectweb.asm.Opcodes.IFGE
import org.objectweb.asm.Opcodes.IFGT
import org.objectweb.asm.Opcodes.IFLE
import org.objectweb.asm.Opcodes.IFLT
import org.objectweb.asm.Opcodes.IFNE
import org.objectweb.asm.Opcodes.IFNONNULL
import org.objectweb.asm.Opcodes.IFNULL
import org.objectweb.asm.Opcodes.IF_ACMPEQ
import org.objectweb.asm.Opcodes.IF_ACMPNE
import org.objectweb.asm.Opcodes.IF_ICMPEQ
import org.objectweb.asm.Opcodes.IF_ICMPGE
import org.objectweb.asm.Opcodes.IF_ICMPGT
import org.objectweb.asm.Opcodes.IF_ICMPLE
import org.objectweb.asm.Opcodes.IF_ICMPLT
import org.objectweb.asm.Opcodes.IF_ICMPNE
import org.objectweb.asm.Opcodes.ILOAD
import org.objectweb.asm.Opcodes.ISTORE
import org.objectweb.asm.Opcodes.LCONST_0
import org.objectweb.asm.Opcodes.LCONST_1
import org.objectweb.asm.Opcodes.PUTSTATIC
import org.objectweb.asm.Opcodes.RETURN
import org.objectweb.asm.Opcodes.SIPUSH
import org.objectweb.asm.Opcodes.V11
//import org.objectweb.asm.Opcodes.V16
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.util.CheckClassAdapter
import sun.misc.Unsafe
import java.io.IOException
import java.io.UncheckedIOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.IdentityHashMap
import java.util.UUID
import java.util.concurrent.atomic.LongAdder
import java.util.function.Consumer
import java.util.logging.Level
import java.util.regex.Pattern
import javax.annotation.Nonnull
import javax.annotation.Nullable

/**
 * A `JVMTranslator` converts a single [L2Chunk] into a [JVMChunk] in a naive
 * fashion. Instruction selection is optimized, but no other optimizations are
 * attempted; all significant optimizations should occur on the `L2Chunk`'s
 * [control&#32;flow&#32;graph][L2ControlFlowGraph] and be reflected in the
 * `L2Chunk` to be translated.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @property code
 *   The source [L1&#32;code][A_RawFunction].
 * @property chunkName
 *   The descriptive (non-unique) name of this chunk.
 * @property sourceFileName
 *   The optional name of the source file associated with the new class.  Use
 *   `null` if no such file exists.
 * @property controlFlowGraph
 *   The [L2ControlFlowGraph] containing the instructions that are translated to
 *   JVM bytecodes.
 *
 * @constructor
 * Construct a new `JVMTranslator` to translate the specified array of
 * [L2Instruction]s to a [JVMChunk].
 *
 * @param code
 *   The source [L1&#32;code][A_RawFunction], or `null` for the
 *   [unoptimized&#32;chunk][L2Chunk.unoptimizedChunk].
 * @param chunkName
 *   The descriptive (non-unique) name of the chunk being translated.
 * @param sourceFileName
 *   The name of the Avail source file that produced the [code]. Use `null`
 *   if no such file exists.
 * @param controlFlowGraph
 *   The [L2ControlFlowGraph] which produced the sequence of instructions.
 * @param instructions
 *   The source [L2Instruction]s.
 */
@Suppress(
	"PARAMETER_NAME_CHANGED_ON_OVERRIDE",
	"UNUSED_PARAMETER",
	"MemberVisibilityCanBePrivate")
class JVMTranslator constructor(
	val code: A_RawFunction?,
	private val chunkName: String,
	private val sourceFileName: String?,
	private val controlFlowGraph: L2ControlFlowGraph,
	instructions: Array<L2Instruction>)
{
	/** The array of [L2Instruction]s to translate to JVM bytecodes. */
	val instructions: Array<L2Instruction> = instructions.clone()

	/**
	 * The [ClassWriter] responsible for writing the [JVMChunk] subclass. The
	 * `ClassWriter` is configured to automatically compute stack map frames and
	 * method limits (e.g., stack depths).
	 */
	val classNode = ClassNode(ASM9)

	/**
	 * The name of the generated class, formed from a [UUID] to ensure that no
	 * collisions occur.
	 */
	val className: String

	/**
	 * The internal name of the generated class.
	 */
	val classInternalName: String

	/** The class file bytes that are produced. */
	private var classBytes: ByteArray? = null

	/**
	 * The [entry&#32;points][L2Instruction.isEntryPoint] into the [L2Chunk],
	 * mapped to their [Label]s.
	 */
	private val entryPoints = mutableMapOf<Int, Label>()

	/**
	 * As the code is being generated and we encounter an
	 * [L2_SAVE_ALL_AND_PC_TO_INT], we examine its corresponding target block to
	 * figure out which registers actually have to be captured at the save, and
	 * restored at the [L2_ENTER_L2_CHUNK].  At that point, we look up the
	 * *local numbers* from the [JVMTranslator] and record them by
	 * [RegisterKind] in this field.
	 *
	 * During optimization, an edge from an [L2_SAVE_ALL_AND_PC_TO_INT] to its
	 * target [L2_ENTER_L2_CHUNK] is treated as though the jump happens
	 * immediately, so that liveness information can be kept accurate. The final
	 * code generation knows better, and simply saves and restores the locals
	 * that back registers that are considered live across this gap.
	 *
	 * The key of this map is the target [L2_ENTER_L2_CHUNK] instruction, and
	 * the value is a map from [RegisterKind] to the [List] of live *local
	 * numbers*.
	 */
	val liveLocalNumbersByKindPerEntryPoint =
		mutableMapOf<L2Instruction, EnumMap<RegisterKind, MutableList<Int>>>()

	/**
	 * We're at a point where reification has been requested.  A [StackReifier]
	 * has already been stashed in the [Interpreter], and already-popped calls
	 * may have already queued actions in the reifier, to be executed in reverse
	 * order.
	 *
	 * First, we stash the live registers in a bogus continuation that will
	 * resume at the specified target (onReification's target), which must be an
	 * [L2_ENTER_L2_CHUNK]. Then we create an action to invoke that
	 * continuation, and push that action onto the current StackReifier's action
	 * stack. Finally, we exit with the current reifier. When the
	 * [L2_ENTER_L2_CHUNK] is reached later, it will restore the registers and
	 * continue constructing the real continuation, with the knowledge that the
	 * [Interpreter.getReifiedContinuation] represents the caller.
	 *
	 * @param method
	 *   The JVM method being written.
	 * @param onReification
	 *   Where to jump to after everything below this frame has been fully
	 *   reified.
	 */
	fun generateReificationPreamble(
		method: MethodVisitor,
		onReification: L2PcOperand)
	{
		method.visitVarInsn(ALOAD, reifierLocal())
		// [reifier]
		loadInterpreter(method)
		// [reifier, interpreter]
		Interpreter.interpreterFunctionField.generateRead(method)
		// [reifier, function]
		onReification.createAndPushRegisterDumpArrays(this, method)
		// [reifier, function, AvailObject[], long[]]
		loadInterpreter(method)
		Interpreter.chunkField.generateRead(method)
		// [reifier, function, AvailObject[], long[], chunk]
		intConstant(method, onReification.offset())
		// [reifier, function, AvailObject[], long[], chunk, offset]
		createDummyContinuationMethod.generateCall(method)
		// [reifier, dummyContinuation]
		// Push an action to the current StackReifier which will run the dummy
		// continuation.
		StackReifier.pushContinuationActionMethod.generateCall(method)
		// [reifier]
		// Now return the reifier to the next level out on the stack.
		method.visitInsn(ARETURN)
	}

	/**
	 * A `LiteralAccessor` aggregates means of accessing a literal [Object] in
	 * various contexts.
	 *
	 * @author Todd L Smith &lt;todd@availlang.org&gt;
	 *
	 * @property classLoaderIndex
	 *   The index into the [JVMChunkClassLoader]'s
	 *   [parameters][JVMChunkClassLoader.parameters] array at which the
	 *   corresponding literal is located, or [invalidIndex] if no slot is
	 *   required.
	 * @property fieldName
	 *   The name of the `private static final` field of the generated
	 *   [JVMChunk] subclass in which the corresponding AvailObject is located,
	 *   or `null` if no field is required.
	 * @property getter
	 *   The [Consumer] that generates an access of the literal when
	 *   [evaluated][Consumer.accept].
	 * @property setter
	 *   The [Consumer] that generates storage of the literal when
	 *   [evaluated][Consumer.accept], or `null` if no such facility is
	 *   required. The generated code assumes that the value to install is on
	 *   top of the stack.
	 *
	 * @constructor
	 * Construct a new `LiteralAccessor`.
	 *
	 * @param classLoaderIndex
	 *   The index into the [JVMChunkClassLoader]'s
	 *   [parameters][JVMChunkClassLoader.parameters] array at which the
	 *   corresponding [literal][AvailObject] is located, or [invalidIndex] if
	 *   no slot is required.
	 * @param fieldName
	 *   The name of the `private static final` field of the generated
	 *   [JVMChunk] subclass in which the corresponding [literal][AvailObject]
	 *   is located, or `null` if no field is required.
	 * @param getter
	 *   The function that generates an access of the literal when evaluated.
	 * @param setter
	 *   The function that generates storage of the literal when evaluated, or
	 *   `null` if no such facility is required. The generated code assumes that
	 *   the value to install is on top of the stack.
	 */
	class LiteralAccessor constructor(
		val classLoaderIndex: Int,
		val fieldName: String?,
		val getter: (MethodVisitor) -> Unit,
		val setter: ((MethodVisitor) -> Unit)?)
	{
		override fun toString(): String =
			"Field: $fieldName ($classLoaderIndex)"

		companion object
		{
			/**
			 * A sentinel value of [classLoaderIndex] that represents no slot
			 * is needed in the [JVMChunkClassLoader]'s
			 * [parameters][JVMChunkClassLoader.parameters] array.
			 */
			const val invalidIndex = -1
		}
	}

	/**
	 * The [literals][Object] used by the [L2Chunk] that must be embedded into
	 * the translated [JVMChunk], mapped to their [accessors][LiteralAccessor].
	 */
	val literals = mutableMapOf<Any, LiteralAccessor>()

	/**
	 * Emit code to push the specified literal on top of the stack.
	 *
	 * @param method
	 *   The [method][MethodVisitor] into which the generated JVM instructions
	 *   will be written.
	 * @param any
	 *   The literal.
	 */
	fun literal(method: MethodVisitor, any: Any)
	{
		literals[any]!!.getter(method)
	}

	/**
	 * Throw an [UnsupportedOperationException]. It is never valid to treat an
	 * [L2Operand] as a JVM literal, so this method is marked as [Deprecated] to
	 * protect against code cloning and refactoring errors by a programmer.
	 *
	 * @param method
	 *   Unused.
	 * @param operand
	 *   Unused.
	 */
	@Deprecated("")
	fun literal(method: MethodVisitor?, operand: L2Operand)
	{
		throw UnsupportedOperationException()
	}

	/**
	 * Throw an [UnsupportedOperationException]. It is never valid to treat an
	 * [L2Register] as a Java literal, so this method is marked as [Deprecated]
	 * to protect against code cloning and refactoring errors by a programmer.
	 *
	 * @param method
	 *   Unused.
	 * @param reg
	 *   Unused.
	 */
	@Deprecated("")
	fun literal(method: MethodVisitor?, reg: L2Register?)
	{
		throw UnsupportedOperationException()
	}

	/**
	 * The start of the runChunk method, where the offset is used to jump to the
	 * start of the control flow graph, or the specified entry point.
	 */
	val methodHead = Label()

	/**
	 * An entry point near the end of the method, which jumps back to the
	 * [methodHead] for the purpose of performing a jump to a specified L2
	 * offset without having the Fernflower Java decompiler produce tons of
	 * spurious nested blocks, breaks, and duplicated code.  It's not very good.
	 */
	val jumper = Label()

	/**
	 * The [L2PcOperand]'s encapsulated program counters, mapped to their
	 * [labels][Label].
	 */
	val labels: MutableMap<Int, Label> = mutableMapOf()

	/**
	 * Answer the [Label] for the specified [L2Instruction]
	 * [offset][L2Instruction.offset].
	 *
	 * @param offset
	 *   The offset.
	 * @return
	 *   The requested `Label`.
	 */
	fun labelFor(offset: Int): Label = labels[offset]!!

	/**
	 * The mapping of registers to locals, partitioned by kind.
	 *
	 * The [L2Register]s used by the [L2Chunk], mapped to their JVM local
	 * indices.
	 */
	val locals = enumMap { _: RegisterKind -> mutableMapOf<Int, Int>() }

	/**
	 * Answer the next JVM local. The initial value is chosen to skip over the
	 * Category-1 receiver and Category-1 [Interpreter] formal parameters.
	 */
	private var nextLocal = 4

	/**
	 * Answer the next JVM local for use within generated code produced by
	 * [generateRunChunk].
	 *
	 * @param type
	 *   The [type][Type] of the local.
	 * @return
	 *   A JVM local.
	 */
	fun nextLocal(type: Type): Int
	{
		assert(type !== Type.VOID_TYPE)
		val local = nextLocal
		nextLocal += type.size
		return local
	}

	/**
	 * Answer the JVM local number for this register.  This is the position
	 * within the actual JVM stack frame layout.
	 *
	 * @param register
	 *   The [L2Register]
	 * @return
	 *   Its position in the JVM frame.
	 */
	fun localNumberFromRegister(register: L2Register): Int =
		locals[register.registerKind]!![register.finalIndex()]!!

	/**
	 * Generate a load of the local associated with the specified [L2Register].
	 *
	 * @param method
	 *   The [method][MethodVisitor] into which the generated JVM instructions
	 *   will be written.
	 * @param register
	 *   A bound `L2Register`.
	 */
	fun load(method: MethodVisitor, register: L2Register)
	{
		method.visitVarInsn(
			register.registerKind.loadInstruction,
			localNumberFromRegister(register))
	}

	/**
	 * Generate a store into the local associated with the specified
	 * [L2Register]. The value to be stored should already be on top of the
	 * stack and correctly typed.
	 *
	 * @param method
	 *   The [method][MethodVisitor] into which the generated JVM instructions
	 *   will be written.
	 * @param register
	 *   A bound `L2Register`.
	 */
	fun store(method: MethodVisitor, register: L2Register)
	{
		method.visitVarInsn(
			register.registerKind.storeInstruction,
			localNumberFromRegister(register))
	}
	/**
	 * A `JVMTranslationPreparer` acts upon its enclosing [JVMTranslator] and an
	 * [L2Operand] to map [L2Register]s to JVM [locals][nextLocal], map
	 * [literals][AvailObject] to `private static final` fields, and map
	 * [program&#32;counters][L2PcOperand] to [Label]s.
	 *
	 * @author Todd L Smith &lt;todd@availlang.org&gt;
	 */
	internal inner class JVMTranslationPreparer : L2OperandDispatcher
	{
		/**
		 * The next unallocated index into the [JVMChunkClassLoader]'s
		 * [parameters][JVMChunkClassLoader.parameters] array at which a
		 * [literal][AvailObject] will be stored.
		 */
		private var nextClassLoaderIndex = 0

		override fun doOperand(operand: L2ArbitraryConstantOperand)
		{
			recordLiteralObject(operand.constant)
		}

		override fun doOperand(operand: L2CommentOperand)
		{
			// Ignore comments; there's nowhere to put them in the translated
			// code, and not much to do with them even if we could.
		}

		override fun doOperand(operand: L2ConstantOperand)
		{
			recordLiteralObject(operand.constant)
		}

		override fun doOperand(operand: L2IntImmediateOperand)
		{
			literals.computeIfAbsent(operand.value) {
				LiteralAccessor(
					invalidIndex,
					null,
					{ method: MethodVisitor -> intConstant(method, it as Int) },
					null)
			}
		}

		override fun doOperand(operand: L2FloatImmediateOperand)
		{
			literals.computeIfAbsent(operand.value) { constant: Any ->
				LiteralAccessor(
					invalidIndex,
					null,
					{ method: MethodVisitor ->
						doubleConstant(method, constant as Double) },
					null)
			}
		}

		override fun doOperand(operand: L2PcOperand)
		{
			operand.counter?.let(this::recordLiteralObject)
			labels.computeIfAbsent(operand.offset()) { Label() }
		}

		override fun doOperand(operand: L2PrimitiveOperand)
		{
			recordLiteralObject(operand.primitive)
		}

		override fun doOperand(operand: L2ReadIntOperand)
		{
			locals[INTEGER_KIND]!!.computeIfAbsent(
				operand.register().finalIndex()) { nextLocal(Type.INT_TYPE) }
		}

		override fun doOperand(operand: L2ReadFloatOperand)
		{
			locals[FLOAT_KIND]!!.computeIfAbsent(
				operand.register().finalIndex()) { nextLocal(Type.DOUBLE_TYPE) }
		}

		override fun doOperand(operand: L2ReadBoxedOperand)
		{
			locals[BOXED_KIND]!!.computeIfAbsent(
				operand.register().finalIndex())
				{ nextLocal(Type.getType(AvailObject::class.java)) }
		}

		override fun doOperand(vector: L2ReadBoxedVectorOperand)
		{
			vector.elements().forEach(this::doOperand)
		}

		override fun doOperand(vector: L2ReadIntVectorOperand)
		{
			vector.elements().forEach(this::doOperand)
		}

		override fun doOperand(vector: L2ReadFloatVectorOperand)
		{
			vector.elements().forEach(this::doOperand)
		}

		override fun doOperand(operand: L2SelectorOperand)
		{
			recordLiteralObject(operand.bundle)
		}

		override fun doOperand(operand: L2WriteIntOperand)
		{
			locals[INTEGER_KIND]!!.computeIfAbsent(
				operand.register().finalIndex())
				{ nextLocal(Type.INT_TYPE) }
		}

		override fun doOperand(operand: L2WriteFloatOperand)
		{
			locals[FLOAT_KIND]!!.computeIfAbsent(
				operand.register().finalIndex())
				{ nextLocal(Type.DOUBLE_TYPE) }
		}

		override fun doOperand(operand: L2WriteBoxedOperand)
		{
			locals[BOXED_KIND]!!.computeIfAbsent(
				operand.register().finalIndex())
				{ nextLocal(Type.getType(AvailObject::class.java)) }
		}

		override fun doOperand(operand: L2PcVectorOperand)
		{
			operand.edges.forEach(this::doOperand)
		}

		/**
		 * Convert the [A_String] into a suitable suffix for a symbolic static
		 * constant name in Java decompilation and the debugger.
		 */
		private fun tidy(string: A_String): String =
			tidy(string.asNativeString())

		/**
		 * Convert the [String] into a suitable suffix for a symbolic static
		 * constant name in Java decompilation and the debugger.
		 */
		private fun tidy(string: String): String
		{
			val trimmed =
				if (string.length > 30) string.take(30) + "…"
				else string
			return buildString {
				trimmed.forEach { c ->
					@Suppress("SpellCheckingInspection")
					when (c)
					{
						'.' -> append("dot")
						';' -> append("semicolon")
						'[' -> append("opensquare")
						'/' -> append("slash")
						'\\' -> append("backslash")
						in '\u0000'..'\u0020' -> append("__")
						else -> append(c)
					}
				}
			}
		}

		/**
		 * Create a literal slot for the given arbitrary [Object].
		 *
		 * @param value
		 *   The actual literal value to capture.
		 */
		private fun recordLiteralObject(value: Any)
		{
			literals.computeIfAbsent(value) { constant: Any ->
				// Choose an index and name for the literal.
				val index = nextClassLoaderIndex++
				var name: String = when
				{
					value is Primitive -> value.name
					value !is AvailObject -> value.javaClass.simpleName
					value.isInstanceOf(stringType) ->
						"STRING_${tidy(value.asNativeString())}"
					value.isInstanceOfKind(Types.ATOM.o) ->
						"ATOM_${tidy(value.atomName)}"
					value.isInstanceOfKind(Types.MESSAGE_BUNDLE.o) ->
						"BUNDLE_${tidy(value.message.atomName)}"
					value.isInstanceOfKind(mostGeneralFunctionType()) ->
						"FUNCTION_${tidy(value.code().methodName)}"
					value.isInstanceOfKind(mostGeneralCompiledCodeType()) ->
						"CODE_${tidy(value.methodName)}"
					else ->
						"literal_" + tagEndPattern
							.matcher(value.makeShared().typeTag.name)
							.replaceAll("")
				}
				name += "_$index"
				val type: Class<*> = constant.javaClass
				// Generate a field that will hold the literal at runtime.
				val field = classNode.visitField(
					ACC_PRIVATE or ACC_STATIC or ACC_FINAL,
					name,
					Type.getDescriptor(type),
					null,
					null)
				field.visitAnnotation(
					Type.getDescriptor(Nonnull::class.java), true)
				field.visitEnd()
				LiteralAccessor(
					index,
					name,
					{ method: MethodVisitor ->
						method.visitFieldInsn(
							GETSTATIC,
							classInternalName,
							name,
							Type.getDescriptor(type))
					},
					{ method: MethodVisitor ->
						method.visitTypeInsn(
							CHECKCAST,
							Type.getInternalName(type))
						method.visitFieldInsn(
							PUTSTATIC,
							classInternalName,
							name,
							Type.getDescriptor(type))
					})
			}
		}
	}


	/**
	 * Prepare for JVM translation by [visiting][JVMTranslationPreparer] each of
	 * the [L2Instruction]s to be translated.
	 */
	fun prepare()
	{
		val preparer = JVMTranslationPreparer()
		instructions.forEach { instruction ->
			var include = instruction.isEntryPoint
			if (debugNicerJavaDecompilation && !include)
			{
				// Normally we only keep L2_ENTER_L2_CHUNK entry points in the
				// main switch, but when debugNicerJavaDecompilation is true,
				// we include every basic block's first instruction for extra
				// clarity.
				include =
					instruction.offset == instruction.basicBlock().offset()
			}
			if (include)
			{
				val label = Label()
				entryPoints[instruction.offset] = label
				labels[instruction.offset] = label
			}
			instruction.operands.forEach { it.dispatchOperand(preparer) }
		}
	}

	/**
	 * Dump a trace of the specified [exception][Throwable] to an appropriately
	 * named file.
	 *
	 * @param e
	 *   The exception.
	 * @return
	 *   The absolute path of the resultant file, or `null` if the file could
	 *   not be written.
	 */
	private fun dumpTraceToFile(e: Throwable): String? =
		try
		{
			val lastSlash = classInternalName.lastIndexOf('/')
			val pkg = classInternalName.substring(0, lastSlash)
			val tempDir = Paths.get("debug", "jvm")
			val dir = tempDir.resolve(Paths.get(pkg))
			Files.createDirectories(dir)
			val base = classInternalName.substring(lastSlash + 1)
			val traceFile = dir.resolve("$base.trace")
			// Make the trace file potentially *much* smaller by truncating the
			// empty space reserved for per-instruction stack and locals to 5
			// spaces each.
			val trace = traceFor(e).replace(
				" {6,}".toRegex(), "     ")
			val buffer = StandardCharsets.UTF_8.encode(trace)
			val bytes = ByteArray(buffer.limit())
			buffer[bytes]
			Files.write(traceFile, bytes)
			traceFile.toAbsolutePath().toString()
		}
		catch (x: IOException)
		{
			log(
				Interpreter.loggerDebugJVM,
				Level.WARNING,
				"unable to write trace for failed generated class {0}",
				classInternalName)
			null
		}

	/**
	 * Finish visiting the [MethodVisitor] by calling
	 * [MethodVisitor.visitMaxs] and then
	 * [visitEnd][MethodVisitor.visitEnd]. If [debugJVM] is `true`, then an
	 * attempt will be made to write out a trace file.
	 *
	 * @param method
	 *   The [method][MethodVisitor] into which the generated JVM instructions
	 *   will be written.
	 */
	@Suppress("SpellCheckingInspection")
	private fun finishMethod(method: MethodVisitor)
	{
		// These are useless formalisms to close the open method context, which
		// had no effect at the time of writing (2021.08.20). But they are still
		// required for canonical correctness, and the library might change.
		method.visitMaxs(0, 0)
		method.visitEnd()
		if (debugJVMCodeGeneration)
		{
			// Now we need to trick ASM into computing the stack map frames and
			// recording the maximum stack depth and locals so that we can
			// record them. This is essential for running the debug analyzer
			// when finishing the whole class.
			val methodNode = method as MethodNode
			val classWriter = ClassWriter(COMPUTE_FRAMES)
			classWriter.visit(
				classNode.version,
				classNode.access,
				classNode.name,
				classNode.signature,
				classNode.superName,
				classNode.interfaces.toTypedArray())
			val methodWriter = classWriter.visitMethod(
				methodNode.access,
				methodNode.name,
				methodNode.desc,
				methodNode.signature,
				methodNode.exceptions.toTypedArray())
			try
			{
				// Compute the stack map frames, including the maximum stack
				// depth and locals.
				methodNode.accept(methodWriter)
			} catch (e: Exception)
			{
				if (debugJVM)
				{
					log(
						Interpreter.loggerDebugJVM,
						Level.SEVERE,
						"stack map frame computation failed for {0}",
						className)
					dumpTraceToFile(e)
				}
				throw e
			}
			// Capture the maximum stack depth and locals, for spoonfeeding into
			// the checker.
			method.visitMaxs(
				unsafe.getInt(methodWriter, maxStackOffset),
				unsafe.getInt(methodWriter, maxLocalsOffset))
		}
	}

	/**
	 * Generate the `static` initializer of the target [JVMChunk]. The static
	 * initializer is responsible for moving any of the
	 * [parameters][JVMChunkClassLoader.parameters] of the [JVMChunk] subclass's
	 * [JVMChunkClassLoader] into appropriate `private static final` fields.
	 */
	fun generateStaticInitializer()
	{
		val method = classNode.visitMethod(
			ACC_STATIC or ACC_PUBLIC,
			"<clinit>",
			Type.getMethodDescriptor(Type.VOID_TYPE),
			null,
			null)
		method.visitCode()
		// :: «generated JVMChunk».class.getClassLoader()
		method.visitLdcInsn(Type.getType("L$classInternalName;"))
		getClassLoader.generateCall(method)
		method.visitTypeInsn(
			CHECKCAST,
			Type.getInternalName(JVMChunkClassLoader::class.java))
		val rawAccessors = literals.values.toMutableList()
		rawAccessors.sortBy { it.classLoaderIndex }
		val accessors =
			rawAccessors.filter {
				accessor: LiteralAccessor -> accessor.setter !== null
			}
		if (accessors.isNotEmpty())
		{
			// :: «generated JVMChunk».class.getClassLoader().parameters
			method.visitInsn(DUP)
			JVMChunkClassLoader.parametersField.generateRead(method)
			val limit = accessors.size
			for ((i, accessor) in accessors.withIndex())
			{
				// :: literal_«i» = («typeof(literal_«i»)») parameters[«i»];
				if (i < limit - 1)
				{
					method.visitInsn(DUP)
				}
				intConstant(method, accessor.classLoaderIndex)
				method.visitInsn(AALOAD)
				accessor.setter!!(method)
			}
		}
		// :: «generated JVMChunk».class.getClassLoader().parameters = null;
		method.visitInsn(ACONST_NULL)
		JVMChunkClassLoader.parametersField.generateWrite(method)
		method.visitInsn(RETURN)
		finishMethod(method)
	}

	/**
	 * Generate access of the receiver (i.e., `this`).
	 *
	 * @param method
	 *   The [method][MethodVisitor] into which the generated JVM instructions
	 *   will be written.
	 */
	fun loadReceiver(method: MethodVisitor)
	{
		method.visitVarInsn(ALOAD, receiverLocal())
	}

	/**
	 * Generate access to the JVM local for the [Interpreter] formal parameter
	 * of a generated implementation of [JVMChunk.runChunk].
	 *
	 * @param method
	 *   The [method][MethodVisitor] into which the generated JVM instructions
	 *   will be written.
	 */
	fun loadInterpreter(method: MethodVisitor)
	{
		method.visitVarInsn(ALOAD, interpreterLocal())
	}

	/**
	 * Answer the JVM local for the `offset` formal parameter of a generated
	 * implementation of [JVMChunk.runChunk].
	 *
	 * @return
	 *   The `offset` formal parameter local.
	 */
	fun offsetLocal(): Int = 2

	/**
	 * Answer the JVM local for the [StackReifier] local variable of a generated
	 * implementation of [JVMChunk.runChunk].
	 *
	 * @return
	 *   The `StackReifier` local.
	 */
	fun reifierLocal(): Int = 3

	/**
	 * Emit the effect of loading a constant `int` to the specified
	 * [MethodVisitor].
	 *
	 * @param method
	 *   The [method][MethodVisitor] into which the generated JVM instructions
	 *   will be written.
	 * @param value
	 * The `int`.
	 */
	fun intConstant(method: MethodVisitor, value: Int)
	{
		when (value)
		{
			-1 -> method.visitInsn(ICONST_M1)
			0 -> method.visitInsn(ICONST_0)
			1 -> method.visitInsn(ICONST_1)
			2 -> method.visitInsn(ICONST_2)
			3 -> method.visitInsn(ICONST_3)
			4 -> method.visitInsn(ICONST_4)
			5 -> method.visitInsn(ICONST_5)
			else ->
			{
				when (value)
				{
					in Byte.MIN_VALUE..Byte.MAX_VALUE ->
						method.visitIntInsn(BIPUSH, value)
					in Short.MIN_VALUE..Short.MAX_VALUE ->
						method.visitIntInsn(SIPUSH, value)
					else -> method.visitLdcInsn(value)
				}
			}
		}
	}

	/**
	 * Emit the effect of loading a constant `long` to the specified
	 * [MethodVisitor].
	 *
	 * @param method
	 *   The [method][MethodVisitor] into which the generated JVM instructions
	 *   will be written.
	 * @param value
	 *   The `long`.
	 */
	fun longConstant(method: MethodVisitor, value: Long)
	{
		when (value)
		{
			0L -> method.visitInsn(LCONST_0)
			1L -> method.visitInsn(LCONST_1)
			in Int.MIN_VALUE..Int.MAX_VALUE ->
			{
				intConstant(method, value.toInt())
				// Emit a conversion, so that we end up with a long on the stack.
				method.visitInsn(I2L)
			}
			// This should emit an ldc2_w instruction, whose result type
			// is long; no conversion instruction is required.
			else -> method.visitLdcInsn(value)
		}
	}

	/**
	 * Emit the effect of loading a constant `float` to the specified
	 * [MethodVisitor].
	 *
	 * @param method
	 *   The [method][MethodVisitor] into which the generated JVM instructions
	 *   will be written.
	 * @param value
	 *   The `float`.
	 */
	@Suppress("unused")
	fun floatConstant(method: MethodVisitor, value: Float)
	{
		when (value)
		{
			0.0f -> method.visitInsn(FCONST_0)
			1.0f -> method.visitInsn(FCONST_1)
			2.0f -> method.visitInsn(FCONST_2)
			// This should emit an ldc instruction, whose result type is float;
			// no conversion instruction is required.
			else -> method.visitLdcInsn(value)
		}
	}

	/**
	 * Emit the effect of loading a constant `double` to the specified
	 * [MethodVisitor].
	 *
	 * @param method
	 *   The [method][MethodVisitor] into which the generated JVM instructions
	 *   will be written.
	 * @param value
	 *   The `double`.
	 */
	fun doubleConstant(method: MethodVisitor, value: Double)
	{
		when (value)
		{
			0.0 -> method.visitInsn(DCONST_0)
			1.0 -> method.visitInsn(DCONST_1)
			// This should emit an ldc2_w instruction, whose result type is
			// double; no conversion instruction is required.
			else -> method.visitLdcInsn(value)
		}
	}

	/**
	 * Emit code to store each of the [L2BoxedRegister]s into a new array. Leave
	 * the new array on top of the stack.
	 *
	 * @param method
	 *   The [method][MethodVisitor] into which the generated JVM instructions
	 *   will be written.
	 * @param operands
	 *   The [L2ReadBoxedOperand]s that hold the registers.
	 * @param arrayClass
	 *   The element type of the new array.
	 */
	fun objectArray(
		method: MethodVisitor,
		operands: List<L2ReadBoxedOperand>,
		arrayClass: Class<out A_BasicObject>)
	{
		if (operands.isEmpty())
		{
			JVMChunk.noObjectsField.generateRead(method)
		}
		else
		{
			// :: array = new «arrayClass»[«limit»];
			val limit = operands.size
			intConstant(method, limit)
			method.visitTypeInsn(ANEWARRAY, Type.getInternalName(arrayClass))
			for (i in 0 until limit)
			{
				// :: array[«i»] = «operands[i]»;
				method.visitInsn(DUP)
				intConstant(method, i)
				load(method, operands[i].register())
				method.visitInsn(AASTORE)
			}
		}
	}

	/**
	 * Answer the JVM branch [opcode][Opcodes] with the reversed sense.
	 *
	 * @param opcode
	 *   The JVM opcode, e.g., [Opcodes.IFEQ], that decides between the two
	 *   branch targets.
	 * @return
	 *   The branch opcode with the reversed sense.
	 */
	@Suppress("SpellCheckingInspection")
	fun reverseOpcode(opcode: Int): Int =
		when (opcode)
		{
			IFEQ -> IFNE
			IFNE -> IFEQ
			IFLT -> IFGE
			IFLE -> IFGT
			IFGE -> IFLT
			IFGT -> IFLE
			IF_ICMPEQ -> IF_ICMPNE
			IF_ICMPNE -> IF_ICMPEQ
			IF_ICMPLT -> IF_ICMPGE
			IF_ICMPLE -> IF_ICMPGT
			IF_ICMPGE -> IF_ICMPLT
			IF_ICMPGT -> IF_ICMPLE
			IF_ACMPEQ -> IF_ACMPNE
			IF_ACMPNE -> IF_ACMPEQ
			IFNULL -> IFNONNULL
			IFNONNULL -> IFNULL
			else ->
			{
				assert(false) { "bad opcode ($opcode)" }
				throw RuntimeException("bad opcode ($opcode)")
			}
		}

	/**
	 * Emit code to unconditionally branch to the specified
	 * [program&#32;counter][L2PcOperand].  Skip if the edge indicates it
	 * follows the given [instruction].
	 *
	 * @param method
	 *   The [method][MethodVisitor] into which the generated JVM instructions
	 *   will be written.
	 * @param instruction
	 *   The [L2Instruction] that includes the operand.
	 * @param operand
	 *   The `L2PcOperand` that specifies the branch target.
	 */
	fun jump(
		method: MethodVisitor,
		instruction: L2Instruction,
		operand: L2PcOperand)
	{
		// If the jump target is the very next instruction, then don't emit a
		// jump at all; just fall through.
		if (operand.offset() != instruction.offset + 1)
		{
			jump(method, operand)
		}
	}

	/**
	 * Emit code to unconditionally branch to the specified
	 * [program&#32;counter][L2PcOperand].
	 *
	 * @param method
	 *   The [method][MethodVisitor] into which the generated JVM instructions
	 *   will be written.
	 * @param operand
	 *   The `L2PcOperand` that specifies the branch target.
	 */
	fun jump(
		method: MethodVisitor,
		operand: L2PcOperand)
	{
		val pc = operand.offset()
		if (debugNicerJavaDecompilation)
		{
			intConstant(method, pc)
			method.visitVarInsn(ISTORE, offsetLocal())
			method.visitJumpInsn(GOTO, jumper)
		}
		else
		{
			method.visitJumpInsn(GOTO, labelFor(pc))
		}
	}

	/**
	 * Emit code to jump to the target of the supplied edge conditionally, based
	 * on the supplied JVM branch opcode and the value on top of the stack.
	 * [program&#32;counter][L2PcOperand].  If condition is not satisfied,
	 * control continues at the next JVM instruction.
	 *
	 * @param method
	 *   The [method][MethodVisitor] into which the generated JVM instructions
	 *   will be written.
	 * @param branchOpcode
	 *   The JVM opcode for the branch instruction, as an [Int].
	 * @param edge
	 *   The [L2PcOperand] that indicates where to jump to.
	 */
	fun jumpIf(
		method: MethodVisitor,
		branchOpcode: Int,
		edge: L2PcOperand)
	{
		if (debugNicerJavaDecompilation)
		{
			val tempLabel = Label()
			method.visitJumpInsn(reverseOpcode(branchOpcode), tempLabel)
			intConstant(method, edge.offset())
			method.visitVarInsn(ISTORE, offsetLocal())
			method.visitJumpInsn(GOTO, jumper)
			method.visitLabel(tempLabel)
		}
		else
		{
			method.visitJumpInsn(branchOpcode, labelFor(edge.offset()))
		}
	}

	/**
	 * Emit code to conditionally branch to one of the specified
	 * [program&#32;counters][L2PcOperand].
	 *
	 * @param method
	 *   The [method][MethodVisitor] into which the generated JVM instructions
	 *   will be written.
	 * @param instruction
	 *   The [L2Instruction] that includes the operands.
	 * @param opcode
	 *  The JVM opcode, e.g., [Opcodes.IFEQ], that decides between the two
	 *  branch targets.
	 * @param success
	 *   The `L2PcOperand` that specifies the branch target in the event that
	 *   the opcode succeeds, i.e., actually branches.
	 * @param failure
	 *   The `L2PcOperand` that specifies the branch target in the event that
	 *   the opcode fails, i.e., does not actually branch and falls through to a
	 *   branch.
	 * @param successCounter
	 *   An [LongAdder] to increment each time the branch is taken.
	 * @param failureCounter
	 *   An [LongAdder] to increment each time the branch falls through.
	 */
	@Suppress("SpellCheckingInspection")
	fun branch(
		method: MethodVisitor,
		instruction: L2Instruction,
		opcode: Int,
		success: L2PcOperand,
		failure: L2PcOperand,
		successCounter: LongAdder,
		failureCounter: LongAdder)
	{
		val offset = instruction.offset
		if (offset + 1 == failure.offset())
		{
			// Note that *both* paths might lead to the next instruction.  This
			// is potentially useful for collecting stats about the frequency of
			// the branch directions.  Make sure to handle this case when
			// collecting all blocks that are targets of non-fallthrough
			// branches.
			generateBranch(
				method, opcode, successCounter, failureCounter, success)
			// Fall through to failurePc.
		}
		else
		{
			generateBranch(
				method,
				reverseOpcode(opcode),
				failureCounter,
				successCounter,
				failure)
			// If the success branch targets the next instruction, jump() will
			// fall through, otherwise it will jump to failure.
			jump(method, instruction, success)
		}
	}

	/**
	 * Generate a branch, with associated counter tracking.  The generated Java
	 * bytecodes have this form:
	 *
	 * * jump to notTakenStub if the given opcode's condition *fails*
	 * * increment takenCounter
	 * * jump to takenPc
	 * * notTakenStub: increment notTakenCounter
	 * * (fall through)
	 * * notTakenPc:
	 * * ...
	 * * takenPc:
	 * * ...
	 *
	 * @param method
	 *   The [MethodVisitor] on which to generate the branch.
	 * @param branchOpcode
	 *   The opcode to effect the branch.  This will be reversed internally to
	 *   make it easier to increment the notTakenCounter before falling through.
	 * @param takenCounter
	 *   The [LongAdder] to increment when the branch is taken.
	 * @param notTakenCounter
	 *   The [LongAdder] to increment when the branch is not taken.
	 * @param takenEdge
	 *   The [L2PcOperand] to jump to if the branch is taken.
	 */
	private fun generateBranch(
		method: MethodVisitor,
		branchOpcode: Int,
		takenCounter: LongAdder,
		notTakenCounter: LongAdder,
		takenEdge: L2PcOperand)
	{
		val logNotTaken = Label()
		method.visitJumpInsn(reverseOpcode(branchOpcode), logNotTaken)
		literal(method, takenCounter)
		longAdderIncrement.generateCall(method)
		jump(method, takenEdge)
		method.visitLabel(logNotTaken)
		literal(method, notTakenCounter)
		longAdderIncrement.generateCall(method)
	}

	/**
	 * Generate the default constructor [`()V`] of the target [JVMChunk].
	 */
	fun generateConstructorV()
	{
		val method = classNode.visitMethod(
			ACC_PUBLIC or ACC_MANDATED,
			"<init>",
			Type.getMethodDescriptor(Type.VOID_TYPE),
			null,
			null)
		method.visitCode()
		loadReceiver(method)
		JVMChunk.chunkConstructor.generateCall(method)
		method.visitInsn(RETURN)
		finishMethod(method)
	}

	/**
	 * Generate the [JVMChunk.name] method of the target [JVMChunk].
	 */
	fun generateName()
	{
		val method = classNode.visitMethod(
			ACC_PUBLIC,
			"name",
			Type.getMethodDescriptor(Type.getType(String::class.java)),
			null,
			null)
		method.visitCode()
		method.visitLdcInsn(chunkName)
		method.visitInsn(ARETURN)
		finishMethod(method)
	}

	/**
	 * Dump the [L1&#32;instructions][L1Operation] that comprise the
	 * [function][A_RawFunction] to an appropriately named file.
	 *
	 * @param fileName
	 *   A [Path] to the file that should be written to.  The directory has been
	 *   created already.
	 * @return
	 *   The absolute path of the resultant file, for inclusion in a
	 *   [JVMChunkL1Source] annotation of the generated [JVMChunk] subclass, or
	 *   `null` if the file could not be written.
	 */
	private fun dumpL1SourceToFile(fileName: Path): String? =
		try
		{
			val builder = StringBuilder()
			builder.append(chunkName)
			builder.append(":\n\n")
			val disassembler = L1Disassembler(code!!)
			disassembler.print(builder, IdentityHashMap(), 0)

			val buffer = StandardCharsets.UTF_8.encode(builder.toString())
			val bytes = ByteArray(buffer.limit())
			buffer.get(bytes)
			Files.write(fileName, bytes)
			fileName.toAbsolutePath().toString()
		}
		catch (e: IOException)
		{
			log(
				Interpreter.loggerDebugJVM,
				Level.WARNING,
				"unable to write L1 for generated class {0}",
				classInternalName)
			null
		}

	/**
	 * Dump the [visualized][L2ControlFlowGraphVisualizer] [L2ControlFlowGraph]
	 * for the [L2Chunk] to an appropriately named file.
	 *
	 * @param fileName
	 *   A [Path] to the file that should be written to.  The directory has been
	 *   created already.
	 * @return
	 *   The absolute path of the resultant file, for inclusion in a
	 *   [JVMChunkL2Source] annotation of the generated [JVMChunk] subclass.
	 */
	private fun dumpL2SourceToFile(fileName: Path): String? =
		try
		{
			val lastSlash = classInternalName.lastIndexOf('/')
			val builder = StringBuilder()
			val visualizer = L2ControlFlowGraphVisualizer(
				classInternalName.substring(lastSlash + 1),
				chunkName,
				80,
				controlFlowGraph,
				true,
				false,
				true,
				builder)
			visualizer.visualize()
			val buffer = StandardCharsets.UTF_8.encode(builder.toString())
			val bytes = ByteArray(buffer.limit())
			buffer.get(bytes)
			Files.write(fileName, bytes)
			fileName.toAbsolutePath().toString()
		}
		catch (e: IOException)
		{
			log(
				Interpreter.loggerDebugJVM,
				Level.WARNING,
				"unable to write L2 for generated class {0}",
				classInternalName)
			null
		}
		catch (e: UncheckedIOException)
		{
			log(
				Interpreter.loggerDebugJVM,
				Level.WARNING,
				"unable to write L2 for generated class {0}",
				classInternalName)
			null
		}

	/**
	 * Generate the [JVMChunk.runChunk] method of the target [JVMChunk].
	 */
	fun generateRunChunk()
	{
		val method = classNode.visitMethod(
			ACC_PUBLIC,
			"runChunk",
			Type.getMethodDescriptor(
				Type.getType(StackReifier::class.java),
				Type.getType(Interpreter::class.java),
				Type.INT_TYPE),
			null,
			null)
		method.visitParameter("interpreter", ACC_FINAL)
		method.visitParameterAnnotation(
			0,
			Type.getDescriptor(Nonnull::class.java),
			true)
		method.visitParameter(
			"offset",
			if (debugNicerJavaDecompilation) 0 else ACC_FINAL)
		if (debugJVM)
		{
			val lastSlash = classInternalName.lastIndexOf('/')
			val pkg = classInternalName.substring(0, lastSlash)
			val tempDir = Paths.get("debug", "jvm")
			val dir = tempDir.resolve(Paths.get(pkg))
			Files.createDirectories(dir)
			var baseFileName = classInternalName.substring(lastSlash + 1)
			if (baseFileName.length > 100)
			{
				// Protect against overly long filenames.
				baseFileName = baseFileName.substring(0, 100) + "…"
			}

			// Note that we have to break the sources up if they are too large
			// for the constant pool.
			if (code !== null)
			{
				val l1Path = dumpL1SourceToFile(dir.resolve("$baseFileName.l1"))
				if (l1Path !== null)
				{
					val annotation = method.visitAnnotation(
						Type.getDescriptor(JVMChunkL1Source::class.java),
						true)
					annotation.visit("sourcePath", l1Path)
					annotation.visitEnd()
				}
			}
			val l2Path = dumpL2SourceToFile(dir.resolve("$baseFileName.dot"))
			if (l2Path !== null)
			{
				val annotation = method.visitAnnotation(
					Type.getDescriptor(JVMChunkL2Source::class.java),
					true)
				annotation.visit("sourcePath", l2Path)
				annotation.visitEnd()
			}
		}
		method.visitAnnotation(
			Type.getDescriptor(Nullable::class.java),
			true)
		if (debugNicerJavaDecompilation)
		{
			// When jumps are implemented via setting the offset and jumping
			// back to the main switch, the verifier can no longer prove that
			// variables are set before use.  Explicitly initialize them here
			// instead, before the methodHead.
			for ((kind, localsOfKind) in locals)
			{
				for ((_, localIndex) in localsOfKind)
				{
					when (kind)
					{
						BOXED_KIND -> {
							method.visitInsn(ACONST_NULL)
							method.visitVarInsn(ASTORE, localIndex)
						}
						INTEGER_KIND -> {
							intConstant(method, 0)
							method.visitVarInsn(ISTORE, localIndex)
						}
						FLOAT_KIND -> {
							doubleConstant(method, 0.0)
							method.visitVarInsn(DSTORE, localIndex)
						}
					}
				}
			}
			// Also initialize the stackReifier local.
			method.visitInsn(ACONST_NULL)
			method.visitVarInsn(ASTORE, reifierLocal())
		}
		method.visitLabel(methodHead)
		// Emit the lookupswitch instruction to select among the entry points.
		val offsets = IntArray(entryPoints.size)
		val entries = arrayOfNulls<Label>(entryPoints.size)
		entryPoints.entries.forEachIndexed { i, (key, value) ->
			offsets[i] = key
			entries[i] = value
		}
		method.visitCode()
		// :: switch (offset) {…}
		method.visitVarInsn(ILOAD, offsetLocal())
		val badOffsetLabel = Label()
		method.visitLookupSwitchInsn(badOffsetLabel, offsets, entries)
		// Translate the instructions.
		for (instruction in instructions)
		{
			val label = labels[instruction.offset]
			if (label !== null)
			{
				method.visitLabel(label)
				method.visitLineNumber(instruction.offset, label)
			}
			@Suppress("ConstantConditionIf")
			if (callTraceL2AfterEveryInstruction)
			{
				loadReceiver(method) // this, the executable chunk.
				intConstant(method, instruction.offset)
				// First line of the instruction toString
				method.visitLdcInsn(
					instruction.toString()
						.split("\\n".toRegex(), 2).toTypedArray()[0])

				// Output the first read operand's value, as an Object, or null.
				run pushOneObject@
				{
					for (operand in instruction.operands)
					{
						if (operand is L2ReadBoxedOperand)
						{
							load(
								method,
								operand.register())
							return@pushOneObject
						}
						if (operand is L2ReadIntOperand)
						{
							load(
								method,
								operand.register())
							javaUnboxIntegerMethod.generateCall(method)
							return@pushOneObject
						}
					}
					// No suitable operands were found.  Use null.
					method.visitInsn(ACONST_NULL)
				}
				Interpreter.traceL2Method.generateCall(method)
			}
			instruction.translateToJVM(this, method)
		}
		// An L2Chunk always ends with an explicit transfer of control, so we
		// shouldn't generate a return here.
		method.visitLabel(badOffsetLabel)

		// :: JVMChunk.badOffset(interpreter.offset);
		method.visitVarInsn(ILOAD, offsetLocal())
		JVMChunk.badOffsetMethod.generateCall(method)
		method.visitInsn(ATHROW)

		if (debugNicerJavaDecompilation)
		{
			method.visitLabel(jumper)
			method.visitJumpInsn(GOTO, methodHead)
		}

		// Visit each of the local variables in order to bind them to artificial
		// register names. At present, we just claim that every variable is live
		// from the methodHead until the endLabel, but we can always tighten
		// this up later if we care.
		val endLabel = Label()
		method.visitLabel(endLabel)
		for ((kind, value) in locals)
		{
			for ((finalIndex, localIndex) in value)
			{
				method.visitLocalVariable(
					kind.prefix + finalIndex,
					kind.jvmTypeString,
					null,
					methodHead,
					endLabel,
					localIndex)
			}
		}
		method.visitLocalVariable(
			"interpreter",
			Type.getDescriptor(Interpreter::class.java),
			null,
			methodHead,
			endLabel,
			interpreterLocal())
		method.visitLocalVariable(
			"offset",
			Type.INT_TYPE.descriptor,
			null,
			methodHead,
			endLabel,
			offsetLocal())
		method.visitLocalVariable(
			"reifier",
			Type.getDescriptor(StackReifier::class.java),
			null,
			methodHead,
			endLabel,
			reifierLocal())
		finishMethod(method)
	}

	/** The final phase of JVM code generation. */
	fun classVisitEnd()
	{
		classNode.visitEnd()
	}

	/**
	 * The generated [JVMChunk], or `null` if no chunk could be constructed.
	 */
	private var jvmChunk: JVMChunk? = null

	/**
	 * Answer the generated [JVMChunk].
	 *
	 * @return
	 *   The generated `JVMChunk`.
	 */
	fun jvmChunk(): JVMChunk = jvmChunk!!

	/**
	 * Dump the specified JVM class bytes to an appropriately named temporary
	 * file.
	 */
	private fun dumpClassBytesToFile()
	{
		try
		{
			val lastSlash = classInternalName.lastIndexOf('/')
			val pkg = classInternalName.substring(0, lastSlash)
			val tempDir = Paths.get("debug", "jvm")
			val dir = tempDir.resolve(Paths.get(pkg))
			Files.createDirectories(dir)
			val base = classInternalName.substring(lastSlash + 1)
			val classFile = dir.resolve("$base.class")
			Files.write(classFile, classBytes!!)
		}
		catch (e: IOException)
		{
			log(
				Interpreter.loggerDebugJVM,
				Level.WARNING,
				"unable to write class bytes for generated class {0}",
				classInternalName)
		}
	}

	/**
	 * Populate [classBytes], dumping to a file for debugging if indicated.
	 */
	fun createClassBytes()
	{
		val writer = ClassWriter(COMPUTE_FRAMES)
		classNode.accept(writer)
		if (debugJVMCodeGeneration)
		{
			val checker = CheckClassAdapter(writer)
			classNode.accept(checker)
		}
		classBytes = writer.toByteArray()
		if (debugJVM)
		{
			dumpClassBytesToFile()
		}
	}

	/**
	 * Actually load the generated class into the running JVM.  Note that a
	 * special [JVMChunkClassLoader] must be used, so that the static
	 * initialization has access to the necessary constants referenced from the
	 * bytecodes.
	 */
	fun loadClass()
	{
		val validParamSet =
			literals.entries.filter { it.value.classLoaderIndex > -1 }
		val parameters = Array<Any>(validParamSet.size) { NilDescriptor.nil }
		for ((key, value) in validParamSet)
		{
			parameters[value.classLoaderIndex] = key
		}
		val loader = JVMChunkClassLoader()
		jvmChunk = loader.newJVMChunkFrom(
			chunkName,
			className,
			classBytes!!,
			parameters)
	}

	/**
	 * The JVM code generation phases, in order.
	 *
	 * @property action
	 *   The action to perform for this phase.
	 *
	 * @constructor
	 * Initialize the enum value.
	 *
	 * @param action
	 *   What to do for this phase.
	 */
	@Suppress("unused")
	internal enum class GenerationPhase constructor(
		private val action: (JVMTranslator) -> Unit)
	{
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

		/** Statistic about this L2 -> JVM translation phase. */
		private val statistic = Statistic(FINAL_JVM_TRANSLATION_TIME, name)

		companion object
		{
			/** A private array of phases, since Enum.values() makes a copy. */
			private val all = values()

			/**
			 * Execute all JVM generation phases.
			 *
			 * @param jvmTranslator
			 *   The [JVMTranslator] for which to execute.
			 */
			fun executeAll(jvmTranslator: JVMTranslator)
			{
				val thread = AvailThread.currentOrNull()
				val interpreter = thread?.interpreter
				for (phase in all)
				{
					val before = AvailRuntimeSupport.captureNanos()
					phase.action(jvmTranslator)
					if (interpreter !== null)
					{
						phase.statistic.record(
							AvailRuntimeSupport.captureNanos() - before,
							interpreter.interpreterIndex)
					}
				}
			}
		}
	}

	/**
	 * Translate the embedded [L2Chunk] into a [JVMChunk].
	 */
	fun translate()
	{
		classNode.visit(
			V11,
			ACC_PUBLIC or ACC_FINAL,
			classInternalName,
			null,
			JVMChunk::class.java.name.replace('.', '/'),
			null)
		classNode.visitSource(sourceFileName, null)
		GenerationPhase.executeAll(this)
	}

	companion object
	{
		/**
		 * When true, this produces slightly slower code that can be decompiled
		 * from bytecodes into Java code without introduce tons of duplicated
		 * code. The body of the method should be decompilable as something
		 * like:
		 *
		 * ```
		 * while(true) {
		 *   switch(offset) {
		 *     case 0:...
		 *     case 10:...
		 *     etc.
		 *   }
		 * }
		 * ```
		 *
		 * In this scenario, a jump to case X is coded as a write to the
		 * `offset` variable, followed by a jump to the loop head, which will
		 * look like an assignment and a continue statement.
		 *
		 * It's unclear how much slower this is than a direct jump (which is
		 * what is generated when this flag is false), but it's probably not a
		 * big difference.
		 */
		const val debugNicerJavaDecompilation = false

		/**
		 * A regex [Pattern] to rewrite function names like '"foo_"[1][3]' to
		 * 'foo_#1#3'.
		 */
		private val subblockRewriter =
			Pattern.compile("\\[(\\d+)]")

		/**
		 * A regex [Pattern] to strip out leading and trailing quotes from a
		 * potential class name.
		 */
		private val classNameUnquoter =
			Pattern.compile("^[\"](.*)[\"]([^\"]*)$")

		/**
		 * A regex [Pattern] to find runs of characters that are forbidden in a
		 * class name, and will be replaced with a single `'%'`.
		 */
		@Suppress("SpellCheckingInspection")
		private val classNameForbiddenCharacters =
			Pattern.compile("[\\[\\]\\\\/.:;\"'\\p{Cntrl}]+")

		/** A regex [Pattern] to strip the prefix of a module name. */
		private val moduleNameStripper =
			Pattern.compile("^.*/([^/]+)$")

		/**
		 * Whether to emit JVM instructions to invoke [Interpreter.traceL2]
		 * before each [L2Instruction].
		 *
		 * NOTE: This is a feature switch. If you want to enter the area of
		 * code that is protected by this switch, set the to true.
		 */
		const val callTraceL2AfterEveryInstruction = false

		/** Helper for stripping "_TAG" from end of tag names. */
		val tagEndPattern: Pattern = Pattern.compile("_TAG$")

		/**
		 * Answer the JVM local for the receiver of a generated implementation
		 * of [JVMChunk.runChunk].
		 *
		 * @return
		 *   The receiver local.
		 */
		private fun receiverLocal(): Int = 0

		/**
		 * Answer the JVM local for the [Interpreter] formal parameter of a
		 * generated implementation of [JVMChunk.runChunk].
		 *
		 * @return
		 *   The `Interpreter` formal parameter local.
		 */
		fun interpreterLocal(): Int = 1

		/**
		 * `true` to enable JVM debugging, `false` otherwise. When enabled, the
		 * generated JVM code dumps verbose information just prior to each L2
		 * instruction.
		 */
		var debugJVM = false

		/**
		 * `true` to enable aggressive sanity checking during JVM code
		 * generation.
		 */
		var debugJVMCodeGeneration = false

		/** The unsafe API. */
		private val unsafe by lazy {
			with(Unsafe::class.java.getDeclaredField("theUnsafe")) {
				isAccessible = true
				get(null) as Unsafe
			}
		}

		/**
		 * The offset of the `maxStack` field with a [MethodNode].
		 */
		private val maxStackOffset by lazy {
			val delegate = Class.forName("org.objectweb.asm.MethodWriter")
			unsafe.objectFieldOffset(delegate.getDeclaredField("maxStack"))
		}

		/**
		 * The offset of the `maxLocals` field with a [MethodNode].
		 */
		private val maxLocalsOffset by lazy {
			val delegate = Class.forName("org.objectweb.asm.MethodWriter")
			unsafe.objectFieldOffset(delegate.getDeclaredField("maxLocals"))
		}
	}

	init
	{
		val module = code?.module ?: NilDescriptor.nil
		val moduleName =
			if (module === NilDescriptor.nil)
			{
				"NoModule"
			}
			else
			{
				moduleNameStripper.matcher(module.moduleNameNative)
					.replaceAll("$1")
			}

		val originalFunctionName =
			if (code === null) "DEFAULT" else code.methodName.asNativeString()
		var cleanFunctionName =
			subblockRewriter.matcher(originalFunctionName).replaceAll("#$1")
		cleanFunctionName =
			classNameUnquoter.matcher(cleanFunctionName).replaceAll("$1$2")
		cleanFunctionName =
			classNameForbiddenCharacters.matcher(cleanFunctionName)
				.replaceAll("\\%")
		if (cleanFunctionName.length > 100)
		{
			cleanFunctionName = cleanFunctionName.substring(0, 100) + "%%%"
		}
		val safeUID = UUID.randomUUID().toString().replace('-', '_')
		className = (
			"avail.optimizer.jvm.generated.$moduleName.$cleanFunctionName"
				 + " - $safeUID.$moduleName - $cleanFunctionName")
		classInternalName = className.replace('.', '/')
	}
}
