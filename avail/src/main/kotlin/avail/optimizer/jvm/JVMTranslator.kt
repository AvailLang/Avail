/*
 * JVMTranslator.kt
 * Copyright © 1993-2022, The Avail Foundation, LLC.
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
import avail.descriptor.functions.ContinuationDescriptor.Companion.createDummyContinuationMethod
import avail.descriptor.representation.A_Atom.Companion.atomName
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.A_Bundle.Companion.message
import avail.descriptor.representation.A_RawFunction
import avail.descriptor.representation.A_RawFunction.Companion.codeStartingLineNumber
import avail.descriptor.representation.A_RawFunction.Companion.methodName
import avail.descriptor.representation.A_RegisterDump
import avail.descriptor.representation.A_String
import avail.descriptor.representation.A_String.Companion.asNativeString
import avail.descriptor.representation.A_Tuple.Companion.tupleSize
import avail.descriptor.representation.AvailObject
import avail.descriptor.representation.NilDescriptor.Companion.nil
import avail.descriptor.types.CompiledCodeTypeDescriptor.Companion.mostGeneralCompiledCodeType
import avail.descriptor.types.FunctionTypeDescriptor.Companion.mostGeneralFunctionType
import avail.descriptor.types.PrimitiveTypeDescriptor.Types
import avail.descriptor.types.TupleTypeDescriptor.Companion.stringType
import avail.interpreter.JavaLibrary.getClassLoader
import avail.interpreter.JavaLibrary.javaUnboxDoubleMethod
import avail.interpreter.JavaLibrary.javaUnboxIntegerMethod
import avail.interpreter.JavaLibrary.longAdderIncrement
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
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedVectorOperand
import avail.interpreter.levelTwo.operand.L2ReadFloatOperand
import avail.interpreter.levelTwo.operand.L2ReadFloatVectorOperand
import avail.interpreter.levelTwo.operand.L2ReadIntOperand
import avail.interpreter.levelTwo.operand.L2ReadIntVectorOperand
import avail.interpreter.levelTwo.operand.L2ReadMixedVectorOperand
import avail.interpreter.levelTwo.operand.L2ReadOperand
import avail.interpreter.levelTwo.operand.L2WriteBoxedOperand
import avail.interpreter.levelTwo.operand.L2WriteBoxedVectorOperand
import avail.interpreter.levelTwo.operand.L2WriteFloatOperand
import avail.interpreter.levelTwo.operand.L2WriteIntOperand
import avail.interpreter.levelTwo.operation.L2_ENTER_L2_CHUNK
import avail.interpreter.levelTwo.operation.L2_SAVE_ALL_AND_PC_TO_INT
import avail.interpreter.levelTwo.register.BOXED_KIND
import avail.interpreter.levelTwo.register.FLOAT_KIND
import avail.interpreter.levelTwo.register.INTEGER_KIND
import avail.interpreter.levelTwo.register.L2BoxedRegister
import avail.interpreter.levelTwo.register.L2Register
import avail.interpreter.levelTwo.register.RegisterKind
import avail.interpreter.primitive.Primitive
import avail.optimizer.DefaultL1ExecutableChunk.DefaultEntryPoint
import avail.optimizer.DefaultL1ExecutableChunk.DefaultL1Chunk
import avail.optimizer.ExecutableChunk
import avail.optimizer.L2BasicBlock
import avail.optimizer.L2ControlFlowGraph
import avail.optimizer.L2ControlFlowGraphVisualizer
import avail.optimizer.L2Optimizer
import avail.optimizer.StackReifier
import avail.optimizer.jvm.CheckedField.Companion.staticField
import avail.optimizer.jvm.JVMTranslator.Companion.debugJVM
import avail.optimizer.jvm.JVMTranslator.Companion.emptyArrayOfObject
import avail.optimizer.jvm.JVMTranslator.Companion.prepareOutputDirectory
import avail.optimizer.jvm.JVMTranslator.LiteralAccessor.Companion.invalidIndex
import avail.performance.Statistic
import avail.performance.StatisticReport.FINAL_JVM_TRANSLATION_TIME
import avail.utility.Strings.traceFor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.ClassWriter.COMPUTE_FRAMES
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Opcodes.AALOAD
import org.objectweb.asm.Opcodes.ACC_FINAL
import org.objectweb.asm.Opcodes.ACC_MANDATED
import org.objectweb.asm.Opcodes.ACC_PRIVATE
import org.objectweb.asm.Opcodes.ACC_PUBLIC
import org.objectweb.asm.Opcodes.ACC_STATIC
import org.objectweb.asm.Opcodes.ACONST_NULL
import org.objectweb.asm.Opcodes.ALOAD
import org.objectweb.asm.Opcodes.ARETURN
import org.objectweb.asm.Opcodes.ASM9
import org.objectweb.asm.Opcodes.ATHROW
import org.objectweb.asm.Opcodes.BIPUSH
import org.objectweb.asm.Opcodes.CHECKCAST
import org.objectweb.asm.Opcodes.DCONST_0
import org.objectweb.asm.Opcodes.DCONST_1
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
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import java.io.IOException
import java.io.UncheckedIOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.IdentityHashMap
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.LongAdder
import java.util.function.Consumer
import java.util.logging.Level
import java.util.regex.Pattern
import javax.annotation.Nonnull
import javax.annotation.Nullable
import kotlin.io.path.writeText

/**
 * A [JVMTranslator] converts a single [L2Chunk] into a [JVMChunk] in a naive
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
 *   [unoptimized&#32;chunk][DefaultL1Chunk].
 * @param chunkName
 *   The descriptive (non-unique) name of the chunk being translated.
 * @param sourceFileName
 *   The name of the Avail source file that produced the [code]. Use `null`
 *   if no such file exists.
 * @param controlFlowGraph
 *   The [L2ControlFlowGraph] which produced the sequence of instructions.
 * @param instructions
 *   The source [L2Instruction]s to translate to JVM bytecodes.
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
	private val instructions: List<L2Instruction>,
	private val instrumentBranches: Boolean)
{
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
	 * A map from each offset of [entry&#32;points][L2Instruction.isEntryPoint]
	 * into the [L2Chunk], to their [Label].
	 */
	private val entryPoints = mutableMapOf<Int, Label>()

	/**
	 * A map from each offset of [entry&#32;points][L2Instruction.isEntryPoint]
	 * into the [L2Chunk] to a [Map].  The map is from [RegisterKind] to a
	 * [List] of live [L2Register] numbers of that kind.  The registers are
	 * restored from an [A_RegisterDump] during re-entry.  This all gets set up
	 * during generation of the preceding [L2_SAVE_ALL_AND_PC_TO_INT].
	 *
	 * During initial generation and optimization of the [L2ControlFlowGraph],
	 * the edge from the [L2_SAVE_ALL_AND_PC_TO_INT] to its [L2_ENTER_L2_CHUNK]
	 * is treated as an immediate jump, but during JVM translation, the former
	 * instruction captures the live registers into an [A_RegisterDump] and the
	 * latter instruction restores them.
	 */
	val entryPointLiveInfo =
		mutableMapOf<Int, Map<RegisterKind<*>, List<Int>>>()

	/** The current [MethodVisitor] being generated. */
	lateinit var method : MethodVisitor private set

	/**
	 * We're at a point where reification has been requested.  A [StackReifier]
	 * has already been stashed in the [Interpreter], and already-popped calls
	 * may have already queued actions in the reifier, to be executed in reverse
	 * order.
	 *
	 * First, we stash the live registers in a bogus continuation that will
	 * resume at the specified target ([onReification]'s target), which must be
	 * an [L2_ENTER_L2_CHUNK]. Then we create an action to invoke that
	 * continuation, and push that action onto the current StackReifier's action
	 * stack. Finally, we exit with the current reifier. When the
	 * [L2_ENTER_L2_CHUNK] is reached later, it will restore the registers and
	 * continue constructing the real continuation, with the knowledge that the
	 * [Interpreter.getReifiedContinuation] represents the caller.
	 *
	 * @param onReification
	 *   Where to jump to after everything below this frame has been fully
	 *   reified.
	 */
	fun generateReificationPreamble(
		onReification: L2PcOperand)
	{
		loadInterpreter()
		// :: [interpreter]
		load(Interpreter.currentReifierField)
		// :: [reifier]
		loadInterpreter()
		// :: [reifier, interpreter]
		load(Interpreter.interpreterFunctionField)
		// :: [reifier, fn]
		onReification.run {
			createAndPushRegisterDump(DefaultEntryPoint.RESUME)
		}
		// :: [reifier, fn, dump]
		loadInterpreter()
		// :: [reifier, fn, dump, interpreter]
		load(Interpreter.chunkField)
		// :: [reifier, fn, dump, chunk]
		intConstant(onReification.offset())
		// :: [reifier, fn, dump, chunk, offset]
		generateCall(createDummyContinuationMethod)
		// :: [reifier, dummyContinuation]
		// Push an action to the current StackReifier which will run the dummy
		// continuation.
		generateCall(StackReifier.pushContinuationActionMethod)
		// :: []
		// Return null to continue reification.
		method.visitInsn(ACONST_NULL)
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
	val locals = RegisterKind.all.associateWithTo(mutableMapOf()) {
		mutableMapOf<Int, Int>()
	}

	/**
	 * Answer the next JVM local. The initial value is chosen to skip over the
	 * Category-1 receiver and Category-1 [Interpreter] formal parameters.
	 */
	private var nextLocal = 3

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
	 * A local variable has just ended scope.  Ensure it was the most recently
	 * allocated local, and deallocate the slot(s) that were used for it.
	 *
	 * @param
	 *   The slot index of the most recently allocated JVM local.
	 * @param type
	 *   The [type][Type] of that local.
	 */
	fun endLocal(localNumber: Int, type: Type)
	{
		nextLocal -= type.size
		assert(nextLocal == localNumber)
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
	fun localNumberFromRegister(register: L2Register<*>): Int =
		locals[register.kind]!![register.finalIndex]!!

	/**
	 * Generate a load of the local associated with the specified [L2Register].
	 *
	 * @param read
	 *   An [L2ReadOperand] providing the value to push.
	 */
	fun load(read: L2ReadOperand<*>)
	{
		loadRegister(read.register())
	}

	/**
	 * Generate a load of the local associated with the specified [L2Register].
	 *
	 * @param register
	 *   A bound `L2Register`.
	 */
	fun loadRegister(register: L2Register<*>)
	{
		if (register.isConstant)
		{
			register.kind.run {
				jvmLoadConstant(register.constant!!)
			}
		}
		else
		{
			method.visitVarInsn(
				register.kind.jvmLoadInstruction,
				localNumberFromRegister(register))
		}
	}

	/**
	 * Generate a store into the local associated with the specified
	 * [L2Register]. The value to be stored should already be on top of the
	 * stack and correctly typed.
	 *
	 * @param register
	 *   A bound `L2Register`.
	 */
	fun store(register: L2Register<*>)
	{
		method.visitVarInsn(
			register.kind.jvmStoreInstruction,
			localNumberFromRegister(register))
	}

	/** Generate a write to the specified [CheckedField]. */
	fun store(checkedField: CheckedField)
	{
		checkedField.generateWrite(this)
	}

	/** Generate a call to the specified [CheckedMethod]. */
	fun generateCall(checkedMethod: CheckedMethod)
	{
		checkedMethod.generateCall(this)
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
			if (string.length > 50) string.take(40) + "…"
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
	 * The next unallocated index into the [JVMChunkClassLoader]'s
	 * [parameters][JVMChunkClassLoader.parameters] array at which a
	 * [literal][AvailObject] will be stored.
	 */
	private var nextClassLoaderIndex = 0

	/**
	 * Emit code to load the literal onto the stack.  If this literal has not
	 * yet been used, a static field is created, and arrangements are made to
	 * set it up during class initialization.  This is accomplished by storing
	 * an [Array] of objects in the [JVMChunkClassLoader] instance, and having
	 * the class initializer read it and write to each static field.
	 *
	 * @param value
	 *   The actual literal value to push.  Unboxed forms of [Int] and [Double]
	 *   have their own separate methods, since objectweb provides automatic
	 *   constant tracking for those.
	 */
	fun loadLiteralObject(
		value: Any)
	{
		val accessor = literals.computeIfAbsent(value) { constant: Any ->
			// Choose an index and name for the literal.
			val index = nextClassLoaderIndex++
			var name: String = when
			{
				value is Primitive -> value.name
				value is LongAdder -> "COUNTER_$index"
				value !is AvailObject -> value.javaClass.simpleName
				value.isInstanceOf(stringType) && value.tupleSize > 0 ->
					"STRING_${tidy(value.asNativeString())}"
				value.isInstanceOfKind(Types.ATOM()) ->
					"ATOM_${tidy(value.atomName)}"
				value.isInstanceOfKind(Types.MESSAGE_BUNDLE()) ->
					"BUNDLE_${tidy(value.message.atomName)}"
				value.isInstanceOfKind(mostGeneralFunctionType) ->
					"FUNCTION_${tidy(value.code().methodName)}"
				value.isInstanceOfKind(mostGeneralCompiledCodeType()) ->
					"CODE_${tidy(value.methodName)}"
				else -> "literal_" + value.makeShared().typeTag.shorterName
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
		accessor.getter(method)
	}


	/**
	 * Throw an [UnsupportedOperationException]. It is never valid to treat an
	 * [L2Operand] as a JVM literal, so this method is marked as [Deprecated] to
	 * protect against code cloning and refactoring errors by a programmer.
	 *
	 * @param operand
	 *   Unused.
	 */
	@Deprecated("L2Operands should not be captured as literals")
	fun loadLiteralObject(operand: L2Operand)
	{
		throw UnsupportedOperationException()
	}

	/**
	 * Throw an [UnsupportedOperationException]. It is never valid to treat an
	 * [L2Register] as a Java literal, so this method is marked as [Deprecated]
	 * to protect against code cloning and refactoring errors by a programmer.
	 *
	 * @param reg
	 *   Unused.
	 */
	@Deprecated("L2Registers should not be captured as literals")
	fun loadLiteralObject(reg: L2Register<*>?)
	{
		throw UnsupportedOperationException()
	}

	/**
	 * Prepare for JVM translation.
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
	 * A `JVMTranslationPreparer` acts upon its enclosing [JVMTranslator] and an
	 * [L2Operand] to map [L2Register]s to JVM [locals][nextLocal], map
	 * [literals][AvailObject] to `private static final` fields, and map
	 * [program&#32;counters][L2PcOperand] to [Label]s.
	 *
	 * @author Todd L Smith &lt;todd@availlang.org&gt;
	 */
	internal inner class JVMTranslationPreparer : L2OperandDispatcher
	{
		override fun doOperand(operand: L2CommentOperand) { }

		override fun doOperand(operand: L2ConstantOperand) { }

		override fun doOperand(operand: L2IntImmediateOperand) { }

		override fun doOperand(operand: L2FloatImmediateOperand) { }

		override fun doOperand(operand: L2ArbitraryConstantOperand<*>) { }

		override fun doOperand(operand: L2PcOperand)
		{
			labels.computeIfAbsent(operand.offset()) { Label() }
		}

		override fun doOperand(operand: L2ReadIntOperand)
		{
			if (operand.isConstantRead) return
			locals[INTEGER_KIND]!!.computeIfAbsent(
				operand.register().finalIndex) { nextLocal(Type.INT_TYPE) }
		}

		override fun doOperand(operand: L2ReadFloatOperand)
		{
			if (operand.isConstantRead) return
			locals[FLOAT_KIND]!!.computeIfAbsent(
				operand.register().finalIndex
			) { nextLocal(Type.DOUBLE_TYPE) }
		}

		override fun doOperand(operand: L2ReadBoxedOperand)
		{
			if (operand.isConstantRead) return
			locals[BOXED_KIND]!!.computeIfAbsent(
				operand.register().finalIndex
			) { nextLocal(Type.getType(AvailObject::class.java)) }
		}

		override fun doOperand(vector: L2ReadBoxedVectorOperand)
		{
			vector.elements.forEach(::doOperand)
		}

		override fun doOperand(vector: L2ReadIntVectorOperand)
		{
			vector.elements.forEach(::doOperand)
		}

		override fun doOperand(vector: L2ReadFloatVectorOperand)
		{
			vector.elements.forEach(::doOperand)
		}

		override fun doOperand(vector: L2ReadMixedVectorOperand)
		{
			vector.elements.forEach { it.dispatchOperand(this) }
		}

		override fun doOperand(operand: L2WriteIntOperand)
		{
			locals[INTEGER_KIND]!!.computeIfAbsent(
				operand.register().finalIndex
			) { nextLocal(Type.INT_TYPE) }
		}

		override fun doOperand(operand: L2WriteFloatOperand)
		{
			locals[FLOAT_KIND]!!.computeIfAbsent(
				operand.register().finalIndex
			) { nextLocal(Type.DOUBLE_TYPE) }
		}

		override fun doOperand(operand: L2WriteBoxedOperand)
		{
			locals[BOXED_KIND]!!.computeIfAbsent(
				operand.register().finalIndex
			) { nextLocal(Type.getType(AvailObject::class.java)) }
		}

		override fun doOperand(vector: L2WriteBoxedVectorOperand)
		{
			vector.elements.forEach(::doOperand)
		}

		override fun doOperand(operand: L2PcVectorOperand)
		{
			operand.edges.forEach(::doOperand)
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
	 * Finish visiting the [MethodVisitor] by calling [MethodVisitor.visitMaxs]
	 * and then [visitEnd][MethodVisitor.visitEnd]. If [debugJVM] is `true`,
	 * then an attempt will be made to write out a trace file.
	 */
	@Suppress("SpellCheckingInspection")
	private fun finishMethod()
	{
		// These are useless formalisms to close the open method context, which
		// had no effect at the time of writing (2021.08.20). But they are still
		// required for canonical correctness, and the library might change.
		method.visitMaxs(0, 0)
		method.visitEnd()
	}

	/**
	 * Generate the `static` initializer of the target [JVMChunk]. The static
	 * initializer is responsible for moving any of the
	 * [parameters][JVMChunkClassLoader.parameters] of the [JVMChunk] subclass's
	 * [JVMChunkClassLoader] into appropriate `private static final` fields.
	 */
	fun generateStaticInitializer()
	{
		method = classNode.visitMethod(
			ACC_STATIC or ACC_PUBLIC,
			"<clinit>",
			Type.getMethodDescriptor(Type.VOID_TYPE),
			null,
			null)
		method.visitCode()
		// :: «generated JVMChunk».class.getClassLoader()
		method.visitLdcInsn(Type.getType("L$classInternalName;"))
		generateCall(getClassLoader)
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
			load(JVMChunkClassLoader.parametersField)
			val limit = accessors.size
			accessors.forEachIndexed { i, accessor ->
				// :: literal_«i» = («typeof(literal_«i»)») parameters[«i»];
				if (i < limit - 1)
				{
					method.visitInsn(DUP)
				}
				intConstant(accessor.classLoaderIndex)
				method.visitInsn(AALOAD)
				accessor.setter!!(method)
			}
		}
		// :: «generated JVMChunk».class.getClassLoader().parameters = null;
		method.visitInsn(ACONST_NULL)
		store(JVMChunkClassLoader.parametersField)
		method.visitInsn(RETURN)
		finishMethod()
	}

	/**
	 * Generate access of the receiver (i.e., `this`).
	 */
	fun loadReceiver()
	{
		method.visitVarInsn(ALOAD, receiverLocal())
	}

	/**
	 * Generate access to the JVM local for the [Interpreter] formal parameter
	 * of a generated implementation of [JVMChunk.runChunk].
	 */
	fun loadInterpreter()
	{
		method.visitVarInsn(ALOAD, interpreterLocal())
	}

	/** Generate a read from the specified [CheckedField]. */
	fun load(checkedField: CheckedField)
	{
		checkedField.generateRead(this)
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
	 * Emit the effect of loading a constant `int`.
	 *
	 * @param value
	 *   The `int`.
	 */
	fun intConstant(value: Int)
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
			in Byte.MIN_VALUE..Byte.MAX_VALUE ->
				method.visitIntInsn(BIPUSH, value)
			in Short.MIN_VALUE..Short.MAX_VALUE ->
				method.visitIntInsn(SIPUSH, value)
			else -> method.visitLdcInsn(value)
		}
	}

	/**
	 * Emit the effect of loading a constant `long`.
	 *
	 * @param value
	 *   The `long`.
	 */
	fun longConstant(value: Long)
	{
		when (value)
		{
			0L -> method.visitInsn(LCONST_0)
			1L -> method.visitInsn(LCONST_1)
			in Int.MIN_VALUE..Int.MAX_VALUE ->
			{
				intConstant(value.toInt())
				// Emit a conversion, so that we end up with a long on the stack.
				method.visitInsn(I2L)
			}
			// This should emit an ldc2_w instruction, whose result type
			// is long; no conversion instruction is required.
			else -> method.visitLdcInsn(value)
		}
	}

	/**
	 * Emit the effect of loading a constant `float`.
	 *
	 * @param value
	 *   The `float`.
	 */
	@Suppress("unused")
	fun floatConstant(value: Float)
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
	 * Emit the effect of loading a constant `double`.
	 *
	 * @param value
	 *   The `double`.
	 */
	fun doubleConstant(value: Double)
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
	 * Emit code to store each of the values from the [L2ReadBoxedOperand]s into
	 * a new array. Leave the new array on top of the stack.
	 *
	 * @param readOperands
	 *   The [L2ReadBoxedOperand]s holding values to put in the array.
	 * @param arrayClass
	 *   The element type of the new array.
	 */
	fun objectArray(
		readOperands: List<L2ReadBoxedOperand>,
		arrayClass: Class<out A_BasicObject>)
	{
		objectArrayFromRegisters(readOperands.map { it.register() }, arrayClass)
	}


	/**
	 * Emit code to store each of the [L2Register]s, boxing into *Java* boxed
	 * values as needed, into a new array. Leave the new array on top of the
	 * stack.
	 *
	 * @param registers
	 *   The [L2Register]s holding values to put in the array, boxing into
	 *   *Java* boxed values if needed.
	 */
	fun arbitraryValueArrayFromRegisters(
		registers: List<L2Register<*>>)
	{
		val size = registers.size
		if (size == 0)
		{
			load(emptyArrayOfObjectField)
			return
		}
		intConstant(size)
		method.visitTypeInsn(
			Opcodes.ANEWARRAY,
			Type.getInternalName(Any::class.java))
		registers.forEachIndexed { i, register ->
			method.visitInsn(Opcodes.DUP)
			intConstant(i)
			loadRegister(register)
			when (register.kind)
			{
				INTEGER_KIND -> generateCall(javaUnboxIntegerMethod)
				FLOAT_KIND -> generateCall(javaUnboxDoubleMethod)
				else -> { }
			}
			method.visitInsn(Opcodes.AASTORE)
		}
	}

	/**
	 * Emit code to store each of the [L2BoxedRegister]s into a new array. Leave
	 * the new array on top of the stack.
	 *
	 * @param registers
	 *   The [L2BoxedRegister]s holding values to put in the array.
	 * @param arrayClass
	 *   The element type of the new array.
	 */
	fun objectArrayFromRegisters(
		registers: List<L2BoxedRegister>,
		arrayClass: Class<out A_BasicObject>)
	{
		val size = registers.size
		val factory = when (size)
		{
			0 ->
			{
				load(JVMChunk.noObjectsField)
				return
			}
			1 -> JVMChunk.createObjectArray1Method
			2 -> JVMChunk.createObjectArray2Method
			3 -> JVMChunk.createObjectArray3Method
			4 -> JVMChunk.createObjectArray4Method
			5 -> JVMChunk.createObjectArray5Method
			else ->
			{
				intConstant(size)
				method.visitTypeInsn(
					Opcodes.ANEWARRAY,
					Type.getInternalName(arrayClass))
				registers.forEachIndexed { i, register ->
					method.visitInsn(Opcodes.DUP)
					intConstant(i)
					loadRegister(register)
					method.visitInsn(Opcodes.AASTORE)
				}
				return
			}
		}
		for (local in registers)
		{
			loadRegister(local)
		}
		generateCall(factory)
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
			else -> throw AssertionError("bad opcode ($opcode)")
		}

	/**
	 * Emit code to unconditionally branch to the specified
	 * [program&#32;counter][L2PcOperand].  Skip if the edge indicates it
	 * follows the operand's owning instruction.
	 *
	 * @param operand
	 *   The [L2PcOperand] that specifies the branch target.
	 */
	fun jumpOrFallThrough(
		operand: L2PcOperand)
	{
		// If the jump target is the very next instruction, then don't emit a
		// jump at all; just fall through.
		if (operand.offset() != operand.instruction.offset + 1)
		{
			jump(operand)
		}
	}

	/**
	 * Emit code to unconditionally branch to the specified
	 * [program&#32;counter][L2PcOperand].
	 *
	 * @param operand
	 *   The [L2PcOperand] that specifies the branch target.
	 */
	fun jump(
		operand: L2PcOperand)
	{
		jump(operand.targetBlock())
	}

	/**
	 * Emit code to unconditionally branch to the specified [L2BasicBlock].
	 *
	 * @param target
	 *   The [L2BasicBlock] to jump to.
	 */
	fun jump(
		target: L2BasicBlock)
	{
		val pc = target.offset()
		if (debugNicerJavaDecompilation)
		{
			intConstant(pc)
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
	 * @param branchOpcode
	 *   The JVM opcode for the branch instruction, as an [Int].
	 * @param edge
	 *   The [L2PcOperand] that indicates where to jump to.
	 */
	fun jumpIf(
		branchOpcode: Int,
		edge: L2PcOperand)
	{
		if (debugNicerJavaDecompilation)
		{
			val tempLabel = Label()
			method.visitJumpInsn(reverseOpcode(branchOpcode), tempLabel)
			intConstant(edge.offset())
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
	 * @param instruction
	 *   The [L2Instruction] that includes the operands.
	 * @param opcode
	 *  The JVM opcode, e.g., [Opcodes.IFEQ], that decides between the two
	 *  branch targets.
	 * @param success
	 *   The [L2PcOperand] that specifies the branch target in the event that
	 *   the opcode succeeds, i.e., actually branches.
	 * @param failure
	 *   The [L2PcOperand] that specifies the branch target in the event that
	 *   the opcode fails, i.e., does not actually branch and falls through to a
	 *   branch.
	 * @param successCounter
	 *   An [LongAdder] to increment each time the branch is taken.
	 * @param failureCounter
	 *   An [LongAdder] to increment each time the branch falls through.
	 */
	@Suppress("SpellCheckingInspection")
	fun branch(
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
			generateBranch(opcode, successCounter, failureCounter, success)
			// Fall through to failurePc.
		}
		else
		{
			generateBranch(
				reverseOpcode(opcode),
				failureCounter,
				successCounter,
				failure)
			// If the success branch targets the next instruction, jump() will
			// fall through, otherwise it will jump to failure.
			jumpOrFallThrough(success)
		}
	}

	/**
	 * Generate a branch, with associated counter tracking if
	 * [instrumentBranches] is true.  In that case, the generated Java bytecodes
	 * have this form:
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
		branchOpcode: Int,
		takenCounter: LongAdder,
		notTakenCounter: LongAdder,
		takenEdge: L2PcOperand)
	{
		if (instrumentBranches)
		{
			// Ensure the branch edge updates the passed LongAdder.
			val logNotTaken = Label()
			method.visitJumpInsn(reverseOpcode(branchOpcode), logNotTaken)
			loadLiteralObject(takenCounter)
			generateCall(longAdderIncrement)
			jump(takenEdge)
			method.visitLabel(logNotTaken)
			loadLiteralObject(notTakenCounter)
			generateCall(longAdderIncrement)
		}
		else
		{
			// Ignore the LongAdders.
			method.visitJumpInsn(branchOpcode, labelFor(takenEdge.offset()))
		}
	}

	/**
	 * Generate the default constructor `[()V]` of the target [JVMChunk].
	 */
	fun generateConstructorV()
	{
		method = classNode.visitMethod(
			ACC_PUBLIC or ACC_MANDATED,
			"<init>",
			Type.getMethodDescriptor(Type.VOID_TYPE),
			null,
			null)
		method.visitCode()
		loadReceiver()
		JVMChunk.chunkConstructor.run {
			generateCall()
		}
		method.visitInsn(RETURN)
		finishMethod()
	}

	/**
	 * Generate the [JVMChunk.name] method of the target [JVMChunk].
	 */
	fun generateName()
	{
		method = classNode.visitMethod(
			ACC_PUBLIC,
			"name",
			Type.getMethodDescriptor(Type.getType(String::class.java)),
			null,
			null)
		method.visitCode()
		method.visitLdcInsn(chunkName)
		method.visitInsn(ARETURN)
		finishMethod()
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
	 * @param full
	 *   Whether to produce a graph with full detail.  The alternative (`false`)
	 *   indicates it should leave off details of instructions and manifests.
	 * @param fileName
	 *   A [Path] to the file that should be written to.  The directory has been
	 *   created already.
	 * @return
	 *   The absolute path of the resultant file, for inclusion in a
	 *   [JVMChunkL2Source] annotation of the generated [JVMChunk] subclass.
	 */
	private fun dumpL2GraphToFile(
		full: Boolean,
		fileName: Path
	): String? =
		try
		{
			val lastSlash = classInternalName.lastIndexOf('/')
			val builder = StringBuilder()
			val visualizer = L2ControlFlowGraphVisualizer(
				fileName = classInternalName.substring(lastSlash + 1),
				name = chunkName,
				charactersPerLine = 80,
				controlFlowGraph = controlFlowGraph,
				visualizeLiveness = full,
				visualizeManifest = full,
				visualizeRegisterDescriptions = full,
				accumulator = builder,
				deltaManifestOnly = false)
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
		method = classNode.visitMethod(
			ACC_PUBLIC,
			ExecutableChunk::runChunk.name,
			Type.getMethodDescriptor(
				Type.getType(A_BasicObject::class.java),
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
		var l2LineTableByL2: MutableList<Int>? = null
		if (debugJVM)
		{
			val lastSlash = classInternalName.lastIndexOf('/')
			val pkg = classInternalName.substring(0, lastSlash)
			val dir = baseDirectoryForGraphs.resolve(Paths.get(pkg))
			runCatching { Files.createDirectories(dir) }
			var baseFileName = classInternalName.substring(lastSlash + 1)
			code?.let {
				baseFileName = "%04d %s".format(
					it.codeStartingLineNumber,
					baseFileName)
			}
			if (baseFileName.length > 100)
			{
				// Protect against overly long filenames.
				baseFileName = baseFileName.take(100) + "…"
			}

			// Note that we have to break the sources up if they are too large
			// for the constant pool.
			if (code !== null)
			{
				val l1Path = runCatching {
					dumpL1SourceToFile(dir.resolve("$baseFileName.l1"))
				}.getOrNull()
				if (l1Path !== null)
				{
					val annotation = method.visitAnnotation(
						Type.getDescriptor(JVMChunkL1Source::class.java),
						true)
					annotation.visit("sourcePath", l1Path)
					annotation.visitEnd()
				}
			}
			val l2GraphPath = runCatching {
				dumpL2GraphToFile(true, dir.resolve("$baseFileName.dot"))
			}.getOrNull()
			if (l2GraphPath !== null)
			{
				val annotation = method.visitAnnotation(
					Type.getDescriptor(JVMChunkL2Source::class.java),
					true)
				annotation.visit("sourcePath", l2GraphPath)
				annotation.visitEnd()
			}
			runCatching {
				dumpL2GraphToFile(
					false, dir.resolve("$baseFileName.simple.dot"))
			}
			val l2TextPath = dir.resolve("$baseFileName.l2")
			l2LineTableByL2 = mutableListOf()
			var line = 1
			val builder = StringBuilder()
			instructions.forEach { instruction ->
				val block = instruction.basicBlock()
				if (instruction.offset == block.instructions()[0].offset)
				{
					builder.append("// #")
					builder.append(instruction.offset)
					builder.append(": ")
					block.zone?.let { z ->
						builder.append("[ZONE: ${z.zoneName}] ")
					}
					builder.append(block.name())
					builder.append('\n')
					line += block.name().count { it == '\n' } + 1
				}
				l2LineTableByL2.add(line)
				val instructionText = instruction.toString()
				builder.append(instructionText).append('\n')
				line += instructionText.count { it == '\n' } + 1
			}
			runCatching {
				l2TextPath.writeText(builder.toString())
			}
			classNode.sourceFile = l2TextPath.fileName.toString()
		}
		val endLabel = Label()
		method.visitAnnotation(
			Type.getDescriptor(Nullable::class.java),
			true)
		if (debugNicerJavaDecompilation)
		{
			// When jumps are implemented via setting the offset and jumping
			// back to the main switch, the verifier can no longer prove that
			// variables are set before use.  Explicitly initialize them here
			// instead, before the methodHead.
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
			// Initialize the register locals.
			locals
				.flatMap { (kind, value) ->
					value.map { (finalIndex, localIndex) ->
						Triple(kind, finalIndex, localIndex)
					}
				}
				.sortedBy(Triple<*, *, Int>::third)
				.forEach { (kind, finalIndex, localIndex) ->
					when (kind)
					{
						BOXED_KIND -> method.visitInsn(ACONST_NULL)
						INTEGER_KIND -> intConstant(0)
						FLOAT_KIND -> doubleConstant(0.0)
					}
					method.visitVarInsn(kind.jvmStoreInstruction, localIndex)
					method.visitLocalVariable(
						kind.prefix + finalIndex,
						kind.jvmTypeString,
						null,
						labelHere(),
						endLabel,
						localIndex)
				}
		}
		method.visitLabel(methodHead)
		// Emit the lookupswitch instruction to select among the entry points.
		// Thu lookupswitch requires its values to be in ascending order.
		val entriesList = entryPoints.entries.sortedBy(Map.Entry<Int, *>::key)
		val offsets = IntArray(entryPoints.size) { entriesList[it].key }
		val entries = Array(entryPoints.size) { entriesList[it].value }
		method.visitCode()
		// :: switch (offset) {…}
		method.visitVarInsn(ILOAD, offsetLocal())
		val badOffsetLabel = Label()
		method.visitLookupSwitchInsn(badOffsetLabel, offsets, entries)
		// Translate the instructions.
		for (instruction in instructions)
		{
			val label = labels[instruction.offset] ?: Label()
			method.visitLabel(label)
			method.visitLineNumber(
				when (l2LineTableByL2)
				{
					null -> instruction.offset
					else -> l2LineTableByL2[instruction.offset]
				},
				label)
			if (callTraceL2AfterEveryInstruction)
			{
				loadReceiver() // this, the executable chunk.
				loadInterpreter()
				intConstant(instruction.offset)
				// First line of the instruction toString.
				method.visitLdcInsn(
					instruction.toString()
						.split("\\n".toRegex(), 2)[0]
						.removeSuffix(":"))
				// Output any read register values, summarized.
				arbitraryValueArrayFromRegisters(instruction.sourceRegisters)
				// :; [chunk, interpreter, offset, duscription, valuesArray]
				generateCall(Interpreter.traceL2Method)
			}
			instruction.run {
				translateToJVM()
			}
		}
		// An L2Chunk always ends with an explicit transfer of control, so we
		// shouldn't generate a return here.
		method.visitLabel(badOffsetLabel)

		// :: JVMChunk.badOffset(interpreter.offset);
		method.visitVarInsn(ILOAD, offsetLocal())
		generateCall(JVMChunk.badOffsetMethod)
		method.visitInsn(ATHROW)

		if (debugNicerJavaDecompilation)
		{
			method.visitLabel(jumper)
			method.visitJumpInsn(GOTO, methodHead)
		}

		// Visit each of the local variables to bind them to artificial register
		// names. At present, we just claim that every variable is live from the
		// methodHead until the endLabel, but we can always tighten this up
		// later if we care.
		method.visitLabel(endLabel)
		finishMethod()
	}

	private fun labelHere(): Label =
		Label().also { method.visitLabel(it) }

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
		val parameters = Array<Any>(validParamSet.size) { nil }
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

		/** Prepare the default constructor, invoked once via reflection. */
		GENERATE_CONSTRUCTOR_V(JVMTranslator::generateConstructorV),

		/** Generate the name() method. */
		GENERATE_NAME(JVMTranslator::generateName),

		/** Generate the runChunk() method. */
		GENERATE_RUN_CHUNK(JVMTranslator::generateRunChunk),

		/**
		 * Create the static &lt;clinit&gt; method for capturing constants.
		 * This must happen after the method has created any [LiteralAccessor]
		 * entries in the [literals] map.
		 */
		GENERATE_STATIC_INITIALIZER(JVMTranslator::generateStaticInitializer),

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
			/** A private array of phases. */
			private val all = entries.toTypedArray()

			/**
			 * Execute all JVM generation phases.
			 *
			 * @param jvmTranslator
			 *   The [JVMTranslator] for which to execute.
			 */
			fun executeAll(jvmTranslator: JVMTranslator)
			{
				val interpreter = AvailThread.currentOrNull?.interpreter
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

	/**
	 * Given a list of input registers and list of corresponding output
	 * registers, move from each input to each output, but through temps to
	 * avoid clobbering them if the lists overlap.
	 */
	fun transferPairwise(
		inputs: List<L2Register<*>>,
		outputs: List<L2Register<*>>)
	{
		// Transfer from the sources to the corresponding destinations.  Most of
		// these pairs will have been assigned to the same register, and can be
		// elided.
		val transferPairs = (inputs zip outputs)
			.filter { (read, write) -> read.finalIndex != write.finalIndex }
		// It's possible that the read registers and write registers overlap
		// with each other, so use the JVM operand stack as temp storage.
		if (transferPairs.isNotEmpty())
		{
			// First push each (non-elided) read.
			transferPairs.forEach { (read, _) ->
				loadRegister(read)
			}
			// Now pop into each corresponding write register in reverse order.
			transferPairs.reversed().forEach { (_,  write) ->
				store(write)
			}
		}

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
			Pattern.compile("^\"(.*)\"([^\"]*)$")

		/**
		 * A regex [Pattern] to find runs of characters that are forbidden in a
		 * class name, and will be replaced with a single `'%'`.
		 */
		private val classNameForbiddenCharacters =
			Pattern.compile("""[\[\]\\/.:*?;"'<>|\p{Cntrl}]+""")

		/**
		 * A regex [Pattern] to locate things that should be replaced with an
		 * underscore, after other replacements have happened.
		 */
		private val classNameSpaceReplacement =
			Pattern.compile("\\s")

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
		var debugJVM = true // TODO false

		/**
		 * Counters for the class prefix names, to avoid name collisions.
		 */
		val nameCounters: MutableMap<String, AtomicInteger> =
			ConcurrentHashMap()

		/**
		 * The [Path] under which L2 optimizer trace information is recorded,
		 * when [debugJVM] is enabled.
		 */
		val baseDirectoryForGraphs: Path = Paths.get("debug", "jvm")

		/**
		 * Compute the JVM class name, output directory, and base file name,
		 * incrementing the per-function usage counter.  Creates the output
		 * directory when [debugJVM] is `true`.
		 *
		 * This single method is the canonical source of the naming and
		 * directory layout used for all L2 debug output.  Both [JVMTranslator]
		 * (for the final output files) and [L2Optimizer] (for per-pass
		 * snapshots, when [L2Optimizer.perPassL2] is `true`) call this method,
		 * each obtaining their own sibling output directory.
		 *
		 * @param pathData
		 *   The [CodeLoggingPathData] containing the naming fields.
		 * @return
		 *   A [Triple] of `(classInternalName, dir, baseFileName)`.
		 */
		fun prepareOutputDirectory(
			pathData: CodeLoggingPathData
		): Triple<String, Path, String>
		{
			val (moduleName, methodName, lineNumber) = pathData
			var cleanFunctionName =
				subblockRewriter.matcher(methodName).replaceAll("#$1")
			cleanFunctionName =
				classNameUnquoter.matcher(cleanFunctionName).replaceAll("$1$2")
			cleanFunctionName =
				classNameForbiddenCharacters.matcher(cleanFunctionName)
					.replaceAll("\\%")
			cleanFunctionName =
				classNameSpaceReplacement.matcher(cleanFunctionName).replaceAll("_")
			if (cleanFunctionName.length > 50)
			{
				cleanFunctionName = cleanFunctionName.take(25) + "%%%" +
					cleanFunctionName.takeLast(25)
			}
			val classDirPrefix =
				"avail.optimizer.jvm.generated.$moduleName.$cleanFunctionName"
			val counter = nameCounters
				.computeIfAbsent(classDirPrefix) { AtomicInteger(1) }
			val counterValue = counter.getAndIncrement()
			val counterString = " ($counterValue)"
			val lineString = "%04d_".format(lineNumber)
			val computedClassName = "avail.optimizer.jvm.generated." +
				"$moduleName.$lineString$cleanFunctionName$counterString." +
				cleanFunctionName
			val classInternalName = computedClassName.replace('.', '/')
			val lastSlash = classInternalName.lastIndexOf('/')
			val pkg = classInternalName.substring(0, lastSlash)
			var baseFileName = classInternalName.substring(lastSlash + 1)
			if (lineNumber != 0)
			{
				baseFileName = "%04d %s".format(lineNumber, baseFileName)
			}
			if (baseFileName.length > 100)
			{
				baseFileName = baseFileName.take(100) + "…"
			}
			val dir = baseDirectoryForGraphs.resolve(Paths.get(pkg))
			if (debugJVM) runCatching { Files.createDirectories(dir) }
			return Triple(classInternalName, dir, baseFileName)
		}

		/**
		 * Convenience overload of [prepareOutputDirectory] that builds a
		 * [CodeLoggingPathData] from [code] first.
		 *
		 * @param code
		 *   The [A_RawFunction] being compiled, or `null` for the default chunk.
		 * @return
		 *   A [Triple] of `(classInternalName, dir, baseFileName)`.
		 */
		fun prepareOutputDirectory(
			code: A_RawFunction?
		): Triple<String, Path, String> =
			prepareOutputDirectory(CodeLoggingPathData.from(code))

		/**
		 * A reusable empty array of Java `Object`.
		 */
		@ReferencedInGeneratedCode
		@JvmField
		val emptyArrayOfObject = emptyArray<Any>()

		/** A static [CheckedField] for accessing [emptyArrayOfObject]. */
		val emptyArrayOfObjectField = staticField(
			JVMTranslator::class.java,
			::emptyArrayOfObject.name,
			Array<Any>::class.java)
	}

	init
	{
		val computedInternalName = controlFlowGraph.reservedClassInternalName
			?: prepareOutputDirectory(code).first
		classInternalName = computedInternalName
		className = computedInternalName.replace('/', '.')
	}
}
