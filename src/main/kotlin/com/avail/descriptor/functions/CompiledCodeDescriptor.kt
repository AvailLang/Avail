/*
 * CompiledCodeDescriptor.kt
 * Copyright © 1993-2021, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 *  * Neither the name of the copyright holder nor the names of the contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
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
package com.avail.descriptor.functions

import com.avail.AvailRuntime
import com.avail.AvailRuntimeSupport
import com.avail.annotations.EnumField
import com.avail.annotations.HideFieldInDebugger
import com.avail.annotations.ThreadSafe
import com.avail.compiler.AvailRejectedParseException
import com.avail.descriptor.fiber.FiberDescriptor
import com.avail.descriptor.functions.CompiledCodeDescriptor.Companion.initialMutableDescriptor
import com.avail.descriptor.functions.CompiledCodeDescriptor.IntegerSlots.Companion.FRAME_SLOTS
import com.avail.descriptor.functions.CompiledCodeDescriptor.IntegerSlots.Companion.HASH
import com.avail.descriptor.functions.CompiledCodeDescriptor.IntegerSlots.Companion.NUM_ARGS
import com.avail.descriptor.functions.CompiledCodeDescriptor.IntegerSlots.Companion.NUM_CONSTANTS
import com.avail.descriptor.functions.CompiledCodeDescriptor.IntegerSlots.Companion.NUM_LOCALS
import com.avail.descriptor.functions.CompiledCodeDescriptor.IntegerSlots.Companion.NUM_OUTERS
import com.avail.descriptor.functions.CompiledCodeDescriptor.IntegerSlots.NYBBLECODES_
import com.avail.descriptor.functions.CompiledCodeDescriptor.ObjectSlots.FUNCTION_TYPE
import com.avail.descriptor.functions.CompiledCodeDescriptor.ObjectSlots.LITERAL_AT_
import com.avail.descriptor.module.A_Module
import com.avail.descriptor.module.A_Module.Companion.moduleName
import com.avail.descriptor.module.A_Module.Companion.originatingPhraseAtIndex
import com.avail.descriptor.numbers.A_Number
import com.avail.descriptor.numbers.A_Number.Companion.extractInt
import com.avail.descriptor.numbers.IntegerDescriptor.Companion.zero
import com.avail.descriptor.phrases.A_Phrase
import com.avail.descriptor.phrases.BlockPhraseDescriptor
import com.avail.descriptor.phrases.DeclarationPhraseDescriptor.DeclarationKind.ARGUMENT
import com.avail.descriptor.representation.A_BasicObject
import com.avail.descriptor.representation.AvailObject
import com.avail.descriptor.representation.AvailObject.Companion.newObjectIndexedIntegerIndexedDescriptor
import com.avail.descriptor.representation.AvailObjectFieldHelper
import com.avail.descriptor.representation.BitField
import com.avail.descriptor.representation.Descriptor
import com.avail.descriptor.representation.IntegerSlotsEnum
import com.avail.descriptor.representation.Mutability
import com.avail.descriptor.representation.NilDescriptor.Companion.nil
import com.avail.descriptor.representation.ObjectSlotsEnum
import com.avail.descriptor.tuples.A_String
import com.avail.descriptor.tuples.A_Tuple
import com.avail.descriptor.tuples.A_Tuple.Companion.concatenate
import com.avail.descriptor.tuples.A_Tuple.Companion.tupleCodePointAt
import com.avail.descriptor.tuples.A_Tuple.Companion.tupleIntAt
import com.avail.descriptor.tuples.A_Tuple.Companion.tupleSize
import com.avail.descriptor.tuples.NybbleTupleDescriptor
import com.avail.descriptor.tuples.NybbleTupleDescriptor.Companion.generateNybbleTupleFrom
import com.avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tupleFromList
import com.avail.descriptor.tuples.StringDescriptor.Companion.stringFrom
import com.avail.descriptor.tuples.TupleDescriptor.Companion.emptyTuple
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.A_Type.Companion.argsTupleType
import com.avail.descriptor.types.A_Type.Companion.isSubtypeOf
import com.avail.descriptor.types.A_Type.Companion.lowerBound
import com.avail.descriptor.types.A_Type.Companion.returnType
import com.avail.descriptor.types.A_Type.Companion.sizeRange
import com.avail.descriptor.types.A_Type.Companion.upperBound
import com.avail.descriptor.types.CompiledCodeTypeDescriptor.Companion.compiledCodeTypeForFunctionType
import com.avail.descriptor.types.CompiledCodeTypeDescriptor.Companion.mostGeneralCompiledCodeType
import com.avail.descriptor.types.FunctionTypeDescriptor
import com.avail.descriptor.types.TypeDescriptor.Types.MODULE
import com.avail.descriptor.types.TypeTag
import com.avail.interpreter.Primitive
import com.avail.interpreter.levelOne.L1Disassembler
import com.avail.interpreter.levelOne.L1OperandType
import com.avail.interpreter.levelOne.L1Operation
import com.avail.interpreter.levelOne.L1Operation.Companion.lookup
import com.avail.interpreter.levelTwo.L2Chunk
import com.avail.interpreter.levelTwo.L2Chunk.InvalidationReason.CODE_COVERAGE
import com.avail.interpreter.primitive.bootstrap.lexing.P_BootstrapLexerStringBody
import com.avail.optimizer.jvm.CheckedMethod
import com.avail.optimizer.jvm.CheckedMethod.Companion.instanceMethod
import com.avail.performance.Statistic
import com.avail.performance.StatisticReport.NON_PRIMITIVE_RETURNEE_TYPE_CHECKS
import com.avail.performance.StatisticReport.NON_PRIMITIVE_RETURNER_TYPE_CHECKS
import com.avail.serialization.SerializerOperation
import com.avail.utility.Strings.newlineTab
import com.avail.utility.cast
import com.avail.utility.json.JSONWriter
import java.util.Collections.newSetFromMap
import java.util.Collections.synchronizedSet
import java.util.IdentityHashMap
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.withLock
import kotlin.math.max

/**
 * A [compiled&#32;code][CompiledCodeDescriptor] object is created whenever a
 * block is compiled. It contains instructions and literals that encode how to
 * perform the block. In particular, its main feature is a
 * [tuple][NybbleTupleDescriptor] of nybbles that encode [L1Operation]s and
 * their [operands][L1OperandType].
 *
 * To refer to specific [Avail&#32;objects][AvailObject] from these
 * instructions, some operands act as indices into the [literals][o_LiteralAt]
 * that are stored within the compiled code object. There are also slots that
 * keep track of the number of arguments that this code expects to be invoked
 * with, and the number of slots to allocate for [continuations][A_Continuation]
 * that represent invocations of this code.
 *
 * Compiled code objects can not be directly invoked, as the
 * [block&#32;phrase][BlockPhraseDescriptor] they represent may refer to "outer"
 * variables in the enclosing scope. When this is the case, a
 * [function&#32;(closure)][FunctionDescriptor] must be constructed at runtime
 * to hold this information. When no such outer variables are needed, the
 * function itself can be constructed at compile time and stored as a literal.
 *
 * After the literal values, the rest of the [literals][o_LiteralAt] slots are:
 *  * outer types
 *  * local variable types
 *  * local constant types
 *
 * Note that the local variable types start with the primitive failure
 * variable's type, if this is a fallible primitive.
 *
 * @constructor
 *
 * @param mutability
 *   The [Mutability] of the resulting descriptor.  This should only be
 *   [Mutability.MUTABLE] for the [initialMutableDescriptor], and
 *   [Mutability.SHARED] for normal instances.
 * @param module
 *   The [module][A_Module] creating this raw function.
 * @param originatingPhraseOrIndex
 *   Usually a one-based index into the module's tuple of block phrases.  This
 *   mechanism allows the tuple to be loaded from a repository on demand,
 *   reducing the memory footprint when this information is not in use.  If the
 *   module is [nil], the value should be the originating phrase itself.
 * @param lineNumber
 *   The starting [lineNumber] of this function, if known, otherwise `0`.
 * @param lineNumberEncodedDeltas
 *   An encoded [A_Tuple] of line number deltas, one per nybblecode instruction.
 *   This is [nil] if the line number information is not available.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
open class CompiledCodeDescriptor protected constructor(
	mutability: Mutability,
	private val module: A_Module,
	@Volatile private var originatingPhraseOrIndex: AvailObject,
	private val packedDeclarationNames: A_String,
	private val lineNumber: Int,
	private val lineNumberEncodedDeltas: A_Tuple
) : Descriptor(
	mutability,
	TypeTag.RAW_FUNCTION_TAG,
	ObjectSlots::class.java,
	IntegerSlots::class.java
) {
	/** A descriptive [A_String] that names this [A_RawFunction]. */
	protected var methodName = unknownFunctionName

	/**
	 * The [L2Chunk] that should be invoked whenever this code is started. The
	 * chunk may no longer be [valid][L2Chunk.isValid], in which case the
	 * [L2Chunk.unoptimizedChunk] will be used instead until the next
	 * reoptimization.
	 */
	@Volatile
	private var startingChunk = L2Chunk.unoptimizedChunk

	/**
	 * An [InvocationStatistic] for tracking invocations of this
	 * [A_RawFunction].
	 */
	private val invocationStatistic = InvocationStatistic()

	/**
	 * The layout of integer slots for my instances.
	 */
	enum class IntegerSlots : IntegerSlotsEnum
	{
		/**
		 * A compound field consisting of the hash value computed at
		 * construction time, and the number of outer variables that my
		 * functions must lexically capture.
		 */
		HASH_AND_OUTERS,

		/**
		 * A compound field consisting of the total number of slots to allocate
		 * in an [A_Continuation] representing an activation of this raw
		 * function, the number of arguments, the number of local variables, and
		 * the number of local constants.
		 */
		NUM_SLOTS_ARGS_LOCALS_AND_CONSTANTS,

		/**
		 * The sequence of nybbles, in little-endian order, starting with an
		 * extra leading nybble indicating how many nybbles (0-15 of them) are
		 * actually unused in the final long.  The nybblecodes describe what
		 * [L1Operation] to perform.
		 *
		 * If there are no nybblecodes, do not reserve any longs.
		 *
		 * To compute the number of valid nybbles, produce zero if there are
		 * no longs, otherwise multiply the number of longs by 16, subtract the
		 * low nybble of the first long, and subtract one more to account for
		 * the space taken by that first nybble.
		 */
		@HideFieldInDebugger
		NYBBLECODES_;

		companion object {
			/**
			 * The hash value of this [compiled][CompiledCodeDescriptor].  It is
			 * computed at construction time.
			 */
			@HideFieldInDebugger
			val HASH = BitField(HASH_AND_OUTERS, 32, 32)

			/**
			 * The number of outer variables that must be captured by my
			 * [functions][FunctionDescriptor].
			 */
			@EnumField(
				describedBy = EnumField.Converter::class,
				lookupMethodName = "decimal")
			val NUM_OUTERS = BitField(HASH_AND_OUTERS, 0, 16)

			/**
			 * The number of [frame&#32;slots][A_Continuation.frameAt] to
			 * allocate for continuations running this code.
			 */
			@EnumField(
				describedBy = EnumField.Converter::class,
				lookupMethodName = "decimal")
			val FRAME_SLOTS =
				BitField(NUM_SLOTS_ARGS_LOCALS_AND_CONSTANTS, 48, 16)

			/**
			 * The number of [arguments][ARGUMENT] that this code expects.
			 */
			@EnumField(
				describedBy = EnumField.Converter::class,
				lookupMethodName = "decimal")
			val NUM_ARGS = BitField(NUM_SLOTS_ARGS_LOCALS_AND_CONSTANTS, 32, 16)

			/**
			 * The number of local variables declared in this code.  This does
			 * not include arguments or local constants.
			 */
			@EnumField(
				describedBy = EnumField.Converter::class,
				lookupMethodName = "decimal")
			val NUM_LOCALS =
				BitField(NUM_SLOTS_ARGS_LOCALS_AND_CONSTANTS, 16, 16)

			/**
			 * The number of local constants declared in this code.  These occur
			 * in the frame after the arguments and local variables.
			 */
			@EnumField(
				describedBy = EnumField.Converter::class,
				lookupMethodName = "decimal")
			val NUM_CONSTANTS =
				BitField(NUM_SLOTS_ARGS_LOCALS_AND_CONSTANTS, 0, 16)
		}
	}

	/**
	 * The layout of object slots for my instances.
	 */
	enum class ObjectSlots : ObjectSlotsEnum {
		/**
		 * The [type][FunctionTypeDescriptor] of any function based on this
		 * [compiled&#32;code][CompiledCodeDescriptor].
		 */
		FUNCTION_TYPE,

		/**
		 * The literal objects that are referred to numerically by some of the
		 * operands of [level&#32;one&#32;instructions][L1Operation] encoded in
		 * the [IntegerSlots.NYBBLECODES_].
		 */
		@HideFieldInDebugger
		LITERAL_AT_
	}

	/**
	 * A helper class that tracks invocation information in [AtomicLong]s.
	 * Since these require neither locks nor complete memory barriers, they're
	 * ideally suited for this purpose.
	 *
	 * TODO MvG - Put these directly into the CompiledCodeDescriptor instances,
	 * allocating a shared descriptor per code object.  Perhaps all the other
	 * fields should also be moved there (allowing the AvailObjects to reuse the
	 * common empty arrays).
	 */
	internal class InvocationStatistic {
		/**
		 * An [AtomicLong] holding a count of the total number of times this
		 * code has been invoked.  This statistic can be useful during
		 * optimization.
		 */
		val totalInvocations = AtomicLong(0)

		/**
		 * An [AtomicLong] that indicates how many more invocations can take
		 * place before the corresponding [L2Chunk] should be re-optimized.
		 */
		val countdownToReoptimize = AtomicLong(L2Chunk.countdownForNewCode)

		/** A statistic for all functions that return. */
		@Volatile
		var returnerCheckStat: Statistic? = null

		/** A statistic for all functions that are returned into. */
		@Volatile
		var returneeCheckStat: Statistic? = null

		/**
		 * A `boolean` indicating whether the current [A_RawFunction] has been
		 * run during the current code coverage session.
		 */
		@Volatile
		var hasRun = false
	}

	/**
	 * A mechanism for extracting consecutive operations and operands from an
	 * [A_RawFunction].
	 */
	class L1InstructionDecoder {
		/**
		 * The actual longSlots field from the current [A_RawFunction] being
		 * traced.
		 */
		var encodedInstructionsArray = emptyArray

		/**
		 * The long index just after consuming the last nybble.
		 */
		var finalLongIndex = -1

		/**
		 * The shift just after consuming the last nybble.
		 */
		var finalShift = -1

		/**
		 * The index of the current long in the [encodedInstructionsArray].
		 */
		private var longIndex = -1

		/**
		 * The current shift factor for extracting nybblecodes from the long at
		 * the current [longIndex].
		 */
		var shift = -1

		/**
		 * Set the pc.  This can be done independently of the call to
		 * [AvailObject.setUpInstructionDecoder].
		 *
		 * @param pc
		 *   The new one-based program counter.
		 */
		fun pc(pc: Int) {
			longIndex = baseIndexInArray + (pc shr 4)
			shift = pc and 15 shl 2
		}

		/**
		 * Answer the current one-based program counter.
		 *
		 * @return
		 *   The current one-based nybblecode index.
		 */
		fun pc() = (longIndex - baseIndexInArray shl 4) + (shift shr 2)

		/**
		 * Get one nybble from the stream of nybblecodes.
		 *
		 * @return
		 *   The consumed nybble.
		 */
		fun getNybble(): Int {
			val result =
				(encodedInstructionsArray[longIndex] shr shift and 15).toInt()
			val newShift = shift + 4
			// If shift has reached 64, increment longIndex.
			longIndex += newShift shr 6
			shift = newShift and 63
			return result
		}

		/**
		 * Consume the next nybblecode operation, but not operands.
		 *
		 * @return
		 *   The [L1Operation] consumed at the current position.
		 */
		fun getOperation(): L1Operation {
			var index = getNybble()
			if (index == 15) {
				index = 16 + getNybble()
			}
			return lookup(index)
		}

		/**
		 * Consume the next nybblecode operation, but not operands.
		 *
		 * @return
		 *   The ordinal of the [L1Operation] consumed at the current position.
		 */
		fun getOperationOrdinal(): Int {
			var index = getNybble()
			if (index == 15) {
				index = 16 + getNybble()
			}
			return index
		}

		/**
		 * Consume the next nybblecode operand.
		 *
		 * @return
		 *   The [Int] operand consumed at the current position.
		 */
		fun getOperand(): Int {
			val firstNybble = getNybble()
			val encodeShift = firstNybble shl 2
			var count = 15 and (-0x7bdeef0000000000L ushr encodeShift).toInt()
			var value = 0
			while (count-- > 0) {
				value = (value shl 4) + getNybble()
			}
			val lowOff = 15 and (0x00AAAA9876543210L ushr encodeShift).toInt()
			val highOff = 15 and (0x0032100000000000L ushr encodeShift).toInt()
			return value + lowOff + (highOff shl 4)
		}

		/**
		 * Answer whether the receiver has reached the end of its instructions.
		 *
		 * @return
		 *   `true` if there are no more instructions to consume, otherwise
		 *   `false`.
		 */
		fun atEnd() = longIndex == finalLongIndex && shift == finalShift

		override fun toString() = super.toString() + "(pc=${pc()})"

		companion object {
			/** The offset into the array of the first nybblecode.  */
			val baseIndexInArray = NYBBLECODES_.ordinal

			/** A reusable array of longs for initializing instances.  */
			private val emptyArray = LongArray(0)
		}
	}

	/**
	 * Contains and presents the details of this raw function pertinent to code
	 * coverage reporting.
	 *
	 * @constructor
	 *
	 * @param hasRun
	 *   Whether this raw function has been run during this code coverage
	 *   session.
	 * @param isTranslated
	 *   Whether this raw function has been translated during this code coverage
	 *   session.
	 * @param startingLineNumber
	 *   The starting line number of this raw function.
	 * @param moduleName
	 *   The module this raw function appears in.
	 * @param methodName
	 *   The method this raw function appears in.
	 *
	 * @author Leslie Schultz &lt;leslie@availlang.org&gt;
	 */
	class CodeCoverageReport internal constructor(
		private val hasRun: Boolean,
		private val isTranslated: Boolean,
		val startingLineNumber: Int,
		val moduleName: String,
		val methodName: String
	) : Comparable<CodeCoverageReport> {
		override fun compareTo(other: CodeCoverageReport): Int {
			val moduleComp = moduleName.compareTo(other.moduleName)
			if (moduleComp != 0) {
				return moduleComp
			}
			val lineComp =
				startingLineNumber.compareTo(other.startingLineNumber)
			return if (lineComp != 0) {
				lineComp
			} else methodName.compareTo(other.methodName)
		}

		override fun toString(): String = String.format(
			"%c %c  m: %s,  l: %d,  f: %s",
			if (hasRun) 'r' else ' ',
			if (isTranslated) 't' else ' ',
			moduleName,
			startingLineNumber,
			methodName)
	}

	override fun printObjectOnAvoidingIndent(
		self: AvailObject,
		builder: StringBuilder,
		recursionMap: IdentityHashMap<A_BasicObject, Void>,
		indent: Int
	) {
		super.printObjectOnAvoidingIndent(self, builder, recursionMap, indent)
		val longCount = self.variableIntegerSlotsCount()
		if (longCount > 0) {
			newlineTab(builder, indent)
			builder.append("Nybblecodes:\n")
			L1Disassembler(self).print(builder, recursionMap, indent + 1)
		}
	}

	override fun o_ConstantTypeAt(self: AvailObject, index: Int): A_Type
	{
		assert(1 <= index && index <= self.numConstants())
		return self.literalAt(self.numLiterals()
			- self.numConstants()
			+ index)
	}

	override fun o_CountdownToReoptimize(self: AvailObject, value: Long)
	{
		invocationStatistic.countdownToReoptimize.set(value)
	}

	override fun o_DeclarationNames(self: AvailObject): A_Tuple
	{
		val names = mutableListOf<A_String>()
		val limit = packedDeclarationNames.tupleSize()
		if (limit == 0) return emptyTuple
		var position = 1
		while (true)
		{
			if (packedDeclarationNames.tupleCodePointAt(position)
				== '\"'.toInt())
			{
				val token = try
				{
					P_BootstrapLexerStringBody.parseString(
						packedDeclarationNames, position, 1)
				}
				catch (e: AvailRejectedParseException)
				{
					throw RuntimeException("Invalid encoded declaration names")
				}
				names.add(token.literal())
				position += token.string().tupleSize()
			}
			else
			{
				val start = position
				while (position <= limit
					&& packedDeclarationNames.tupleCodePointAt(position)
						!= ','.toInt())
				{
					position++
				}
				names.add(packedDeclarationNames.copyStringFromToCanDestroy(
					start, position - 1, false))
			}
			if (position == limit + 1)
				return tupleFromList(names)
			if (packedDeclarationNames.tupleCodePointAt(position)
				!= ','.toInt())
			{
				throw RuntimeException("Invalid encoded declaration names")
			}
			position++
		}
	}

	override fun o_DecrementCountdownToReoptimize(
		self: AvailObject,
		continuation: (Boolean)->Unit)
	{
		val newCount =
			invocationStatistic.countdownToReoptimize.decrementAndGet()
		if (newCount <= 0) {
			// Either we just decremented past zero or someone else did.  Race
			// for a lock on the object.  First one through reoptimizes while
			// the others wait.
			synchronized(self) {
				// If the counter is still negative then either (1) it hasn't
				// been reset yet by reoptimization, or (2) it has been
				// reoptimized, the counter was reset to something positive,
				// but it has already been decremented back below zero.
				// Either way, reoptimize now.
				continuation(
					invocationStatistic.countdownToReoptimize.get() <= 0)
			}
		}
	}

	override fun o_DecreaseCountdownToReoptimizeFromPoll (
		self: AvailObject,
		delta: Long)
	{
		val counter = invocationStatistic.countdownToReoptimize
		do
		{
			val current = counter.get()
		}
		while (current <= 0
			|| !counter.compareAndSet(current, max(1, current - delta)))
	}

	/**
	 * {@inheritDoc}
	 *
	 * Show the types of local variables and outer variables.
	 */
	override fun o_DescribeForDebugger(
		self: AvailObject
	): Array<AvailObjectFieldHelper> = with(self) {
		val fields = mutableListOf(*super.o_DescribeForDebugger(self))
		fields.add(AvailObjectFieldHelper(
			self,
			DebuggerObjectSlots("Descriptor"),
			-1,
			this@CompiledCodeDescriptor))
		val disassembled = L1Disassembler(self).disassembledAsSlots()
		if (variableIntegerSlotsCount() > 0)
		{
			val moduleName = self.module().run {
				if (equalsNil()) "No module"
				else moduleName().asNativeString().split("/").last()
			}
			fields.add(
				AvailObjectFieldHelper(
					self,
					DebuggerObjectSlots("Disassembly"),
					-1,
					null,
					forcedName = "L1 Disassembly ($moduleName)",
					forcedChildren = disassembled.toTypedArray()))
		}
		val literalFields = mutableListOf<AvailObjectFieldHelper>()
		val baseLiterals =
			numLiterals() - numConstants() - numLocals() - numOuters()
		(1..baseLiterals).mapTo(literalFields) {
			AvailObjectFieldHelper(
				self, DebuggerObjectSlots("Base literal"), it, literalAt(it))
		}
		(1..numOuters()).mapTo(literalFields) {
			AvailObjectFieldHelper(
				self, DebuggerObjectSlots("Outer type"), it, outerTypeAt(it))
		}
		(1..numLocals()).mapTo(literalFields) {
			AvailObjectFieldHelper(
				self, DebuggerObjectSlots("Local type"), it, localTypeAt(it))
		}
		(1..numConstants()).mapTo(literalFields) {
			AvailObjectFieldHelper(
				self,
				DebuggerObjectSlots("Constant type"),
				it,
				constantTypeAt(it))
		}
		val allLiterals = (1..numLiterals()).map { literalAt(it) }
		fields.add(
			AvailObjectFieldHelper(
				self,
				DebuggerObjectSlots("All literals"),
				-1,
				tupleFromList(allLiterals),
				forcedName = "Literals",
				forcedChildren = literalFields.toTypedArray()))
		return fields.toTypedArray()
	}

	override fun o_Equals(self: AvailObject, another: A_BasicObject) =
		another.equalsCompiledCode(self)

	override fun o_EqualsCompiledCode(
		self: AvailObject,
		aCompiledCode: A_RawFunction
	) = self.sameAddressAs(aCompiledCode)

	override fun o_FunctionType(self: AvailObject) =
		self.slot(FUNCTION_TYPE)

	override fun o_Hash(self: AvailObject) = self.slot(HASH)

	override fun o_Kind(self: AvailObject): AvailObject =
		compiledCodeTypeForFunctionType(self.functionType())

	/**
	 * Note - Answers nil if there is no line number information.
	 *
	 * @param self
	 *   The raw function.
	 * @return
	 *   The tuple of encoded line number deltas, or nil.
	 */
	override fun o_LineNumberEncodedDeltas(self: AvailObject): A_Tuple =
		lineNumberEncodedDeltas

	override fun o_LiteralAt(self: AvailObject, index: Int) =
		self.slot(LITERAL_AT_, index)

	override fun o_LocalTypeAt(self: AvailObject, index: Int): A_Type {
		assert(1 <= index && index <= self.numLocals())
		return self.literalAt((self.numLiterals()
			- self.numConstants()
			- self.numLocals())
			+ index)
	}

	override fun o_MaxStackDepth(self: AvailObject) =
		(self.numSlots() - self.numArgs() - self.numLocals())

	override fun o_MethodName(self: AvailObject): A_String = methodName

	/**
	 * Answer the module in which this code occurs.
	 */
	override fun o_Module(self: AvailObject): A_Module = module

	override fun o_NameForDebugger(self: AvailObject) =
		super.o_NameForDebugger(self) + ": " + methodName

	override fun o_NumArgs(self: AvailObject) = self.slot(NUM_ARGS)

	override fun o_NumConstants(self: AvailObject) = self.slot(NUM_CONSTANTS)

	override fun o_NumLiterals(self: AvailObject) =
		self.variableObjectSlotsCount()

	override fun o_NumLocals(self: AvailObject) = self.slot(NUM_LOCALS)

	override fun o_NumNybbles(self: AvailObject): Int {
		val longCount = self.variableIntegerSlotsCount()
		if (longCount == 0) {
			// Special case: when there are no nybbles, don't reserve any longs.
			return 0
		}
		val firstLong = self.slot(NYBBLECODES_, 1)
		val unusedNybbles = firstLong.toInt() and 15
		return (longCount shl 4) - unusedNybbles - 1
	}

	override fun o_NumOuters(self: AvailObject) = self.slot(NUM_OUTERS)

	override fun o_NumSlots(self: AvailObject) = self.slot(FRAME_SLOTS)

	override fun o_Nybbles(self: AvailObject): A_Tuple
	{
		// Extract a tuple of nybbles.
		val longCount = self.variableIntegerSlotsCount()
		if (longCount == 0) {
			// Special case: when there are no nybbles, don't reserve any longs.
			return emptyTuple
		}
		val decoder = L1InstructionDecoder()
		self.setUpInstructionDecoder(decoder)
		decoder.pc(1)
		return generateNybbleTupleFrom(o_NumNybbles(self)) {
			decoder.getNybble()
		}
	}

	override fun o_OriginatingPhrase(self: AvailObject): A_Phrase
	{
		if (originatingPhraseOrIndex.isInt)
		{
			originatingPhraseOrIndex = module.originatingPhraseAtIndex(
				originatingPhraseOrIndex.extractInt()
			) as AvailObject
		}
		return originatingPhraseOrIndex
	}

	override fun o_OriginatingPhraseOrIndex(self: AvailObject): AvailObject =
		originatingPhraseOrIndex

	override fun o_OuterTypeAt(self: AvailObject, index: Int): A_Type
	{
		assert(1 <= index && index <= self.numOuters())
		return self.literalAt((self.numLiterals()
			- self.numConstants()
			- self.numLocals()
			- self.numOuters())
			+ index)
	}

	override fun o_PackedDeclarationNames(self: AvailObject): A_String =
		packedDeclarationNames

	override fun o_Primitive(self: AvailObject): Primitive? = null

	/**
	 * Answer the [Statistic] used to record the cost of explicitly type
	 * checking returns from the raw function.  These are also collected into
	 * the [.returnerCheckStatisticsByName], to ensure unloading/reloading
	 * a module will reuse the same statistic objects.
	 *
	 * @param self
	 *   The raw function.
	 * @return
	 *   A [Statistic], creating one if necessary.
	 */
	override fun o_ReturnerCheckStat(self: AvailObject): Statistic
	{
		var returnerStat = invocationStatistic.returnerCheckStat
		if (returnerStat === null) {
			// Look it up by name, creating it if necessary.
			val name = self.methodName()
			returnerStat = returnerCheckStatisticsByName.computeIfAbsent(
				name
			) {
				Statistic(
					NON_PRIMITIVE_RETURNER_TYPE_CHECKS,
					"Checked return from " + name.asNativeString())
			}
			invocationStatistic.returnerCheckStat = returnerStat
		}
		return returnerStat!!
	}

	/**
	 * Answer the [Statistic] used to record the cost of explicitly type
	 * checking returns back into the raw function.  These are also collected
	 * into the [.returneeCheckStatisticsByName], to ensure
	 * unloading/reloading a module will reuse the same statistic objects.
	 *
	 * @param self
	 *   The raw function.
	 * @return
	 *   A [Statistic], creating one if necessary.
	 */
	override fun o_ReturneeCheckStat(self: AvailObject): Statistic
	{
		var returneeStat = invocationStatistic.returneeCheckStat
		if (returneeStat === null) {
			// Look it up by name, creating it if necessary.
			val name = self.methodName()
			returneeStat = returneeCheckStatisticsByName.computeIfAbsent(
				name
			) {
				Statistic(
					NON_PRIMITIVE_RETURNEE_TYPE_CHECKS,
					"Checked return into " + name.asNativeString())
			}
			invocationStatistic.returneeCheckStat = returneeStat
		}
		return returneeStat!!
	}

	override fun o_ReturnTypeIfPrimitiveFails(self: AvailObject): A_Type =
		self.functionType().returnType()

	@ThreadSafe
	override fun o_SerializerOperation(self: AvailObject) =
		SerializerOperation.COMPILED_CODE

	override fun o_SetMethodName(
		self: AvailObject,
		methodName: A_String
	) {
		assert(mutability === Mutability.SHARED)
		assert(methodName.isString)
		methodName.makeShared()
		this.methodName = methodName
		// Now scan all sub-blocks. Some literals will be functions and some
		// will be compiled code objects.
		var counter = 1
		loop@for (i in 1..self.numLiterals())
		{
			val literal = self.literalAt(i)
			val subCode: A_RawFunction = when
			{
				literal.isFunction -> literal.code()
				literal.isInstanceOf(mostGeneralCompiledCodeType()) -> literal
				else -> continue@loop
			}
			subCode.setMethodName(
				concatenate(
					methodName,
					stringFrom("#${counter++}"),
					true
				).makeShared())
		}
	}

	override fun o_SetOriginatingPhraseOrIndex(
		self: AvailObject,
		phraseOrIndex: AvailObject
	)
	{
		originatingPhraseOrIndex = phraseOrIndex
	}


	override fun o_SetStartingChunkAndReoptimizationCountdown(
		self: AvailObject,
		chunk: L2Chunk,
		countdown: Long
	) {
		synchronized(self) { startingChunk = chunk }
		// Must be outside the synchronized section to ensure the write of
		// the new chunk is committed before the counter reset is visible.
		invocationStatistic.countdownToReoptimize.set(countdown)
	}

	override fun o_ShowValueInNameForDebugger(self: AvailObject) = false

	override fun o_StartingChunk(self: AvailObject): L2Chunk
	{
		val chunk = startingChunk
		if (chunk != L2Chunk.unoptimizedChunk) {
			L2Chunk.Generation.usedChunk(chunk)
		}
		return chunk
	}

	/**
	 * Answer the starting line number for this block of code.
	 */
	override fun o_StartingLineNumber(self: AvailObject) = lineNumber

	override fun o_TallyInvocation(self: AvailObject) {
		invocationStatistic.totalInvocations.incrementAndGet()
		invocationStatistic.hasRun = true
	}

	override fun o_TotalInvocations(self: AvailObject) =
		invocationStatistic.totalInvocations.get()

	/**
	 * Render the [receiver][AvailObject] as JSON.
	 *
	 * @param self
	 *   The receiver.
	 * @param writer
	 *   The [JSONWriter].
	 * @param writeFunctionType
	 *   How to write the [function&#32;type][FUNCTION_TYPE].
	 */
	private fun writeTo(
			self: AvailObject,
			writer: JSONWriter,
			writeFunctionType: A_Type.()->Unit
	) = writer.writeObject {
		at("kind") { write("function implementation") }
		at("outers") { write(self.slot(NUM_OUTERS)) }
		at("arguments") { write(self.slot(NUM_ARGS)) }
		at("locals") { write(self.slot(NUM_LOCALS)) }
		at("constants") { write(self.slot(NUM_CONSTANTS)) }
		at("maximum stack depth") { write(self.slot(FRAME_SLOTS)) }
		at("nybbles") { self.nybbles().writeTo(writer) }
		at("function type") { self.slot(FUNCTION_TYPE).writeFunctionType() }
		at("method") { self.methodName().writeTo(writer) }
		if (!module.equalsNil())
		{
			at("module") { self.module().moduleName().writeTo(writer) }
		}
		at("starting line number") { write(self.startingLineNumber()) }
		at("literals") {
			writeArray {
				val limit = self.variableObjectSlotsCount()
				for (i in 1..limit)
				{
					var literal: A_BasicObject = self.slot(LITERAL_AT_, i)
					if (literal.equalsNil())
					{
						// Value doesn't matter, but it can't be nil.  Use zero.
						literal = zero
					}
					literal.writeSummaryTo(writer)
				}
			}
		}
	}

	override fun o_WriteSummaryTo(self: AvailObject, writer: JSONWriter) =
		writeTo(self, writer) {
			writeSummaryTo(writer)
		}

	override fun o_WriteTo(self: AvailObject, writer: JSONWriter) =
		writeTo(self, writer) {
			writeTo(writer)
		}

	@Deprecated(
		"Not supported",
		ReplaceWith("""newCompiledCode(
			A_Tuple,
			Int,
			A_Type,
			Primitive?,
			A_Tuple,
			A_Tuple,
			A_Tuple,
			A_Tuple,
			A_Module,
			Int,
			A_Tuple,
			A_Phrase"""))
	override fun mutable() = unsupported

	@Deprecated(
		"Not supported",
		ReplaceWith("""newCompiledCode(
			A_Tuple,
			Int,
			A_Type,
			Primitive?,
			A_Tuple,
			A_Tuple,
			A_Tuple,
			A_Tuple,
			A_Module,
			Int,
			A_Tuple,
			A_Phrase"""))
	override fun immutable() = unsupported

	@Deprecated(
		"Not supported",
		ReplaceWith("""newCompiledCode(
			A_Tuple,
			Int,
			A_Type,
			Primitive?,
			A_Tuple,
			A_Tuple,
			A_Tuple,
			A_Tuple,
			A_Module,
			Int,
			A_Tuple,
			A_Phrase"""))
	override fun shared() = unsupported

	companion object {
		/** The set of all active [raw functions][CompiledCodeDescriptor]. */
		private val activeRawFunctions: MutableSet<A_RawFunction> =
			synchronizedSet(newSetFromMap(WeakHashMap()))

		/**
		 * Reset the code coverage details of all [A_RawFunction]s by discarding
		 * their L2 optimized chunks and clearing their flags.  When complete,
		 * resume the supplied action.
		 *
		 * @param resume
		 *   The continuation to be executed upon.
		 */
		fun resetCodeCoverageDetailsThen(resume: () -> Unit) =
			AvailRuntime.currentRuntime().whenLevelOneSafeDo(
				FiberDescriptor.commandPriority
			) {
				L2Chunk.invalidationLock.withLock {
					// Loop over each instance, setting the touched flag to
					// false and discarding optimizations.
					for (rawFunction in activeRawFunctions) {
						val self: AvailObject = rawFunction.cast()
						val descriptor: CompiledCodeDescriptor =
							self.descriptor().cast()
						descriptor.invocationStatistic.hasRun = false
						if (!descriptor.module.equalsNil()) {
							val chunk = descriptor.startingChunk
							if (chunk != L2Chunk.unoptimizedChunk)
							{
								chunk.invalidate(CODE_COVERAGE)
							}
						}
					}
					AvailRuntime.currentRuntime().whenLevelOneUnsafeDo(
						FiberDescriptor.commandPriority, resume)
				}
			}

		/**
		 * Collect and return the code coverage reports for all the raw
		 * functions.
		 *
		 * @param resume
		 *   The continuation to pass the return value to.
		 */
		fun codeCoverageReportsThen(
			resume: (List<CodeCoverageReport>) -> Unit
		) = AvailRuntime.currentRuntime().whenLevelOneSafeDo(
			FiberDescriptor.commandPriority
		) {
			val reports: MutableList<CodeCoverageReport> = mutableListOf()

			// Loop over each instance, creating its report object.
			for (rawFunction in activeRawFunctions) {
				val self: AvailObject = rawFunction.cast()
				val descriptor: CompiledCodeDescriptor =
					self.descriptor().cast()
				val module = descriptor.module
				if (!module.equalsNil()) {
					val report = CodeCoverageReport(
						descriptor.invocationStatistic.hasRun,
						descriptor.startingChunk != L2Chunk.unoptimizedChunk,
						descriptor.lineNumber,
						module.moduleName().asNativeString(),
						descriptor.methodName.asNativeString())
					if (!reports.contains(report)) {
						reports.add(report)
					}
				}
			}
			AvailRuntime.currentRuntime().whenLevelOneUnsafeDo(
				FiberDescriptor.commandPriority
			) { resume(reports) }
		}

		/** The [CheckedMethod] for [A_RawFunction.primitive].  */
		val codePrimitiveMethod: CheckedMethod = instanceMethod(
			A_RawFunction::class.java,
			A_RawFunction::primitive.name,
			Primitive::class.java)

		/** The Avail string "Unknown function".  */
		val unknownFunctionName: A_String =
			stringFrom("Unknown function").makeShared()

		/**
		 * Create a new compiled code object with the given properties.
		 *
		 * @param nybbles
		 *   The nybblecodes.
		 * @param stackDepth
		 *   The maximum stack depth.
		 * @param functionType
		 *   The type that the code's functions will have.
		 * @param primitive
		 *   Which [Primitive] to invoke, or `null`.
		 * @param returnTypeIfPrimitiveFails
		 *   The [A_Type] that will be returned by the body if this is not a
		 *   primitive, or if the primitive fails.
		 * @param literals
		 *   A tuple of literals.
		 * @param localVariableTypes
		 *   A tuple of types of local variables.
		 * @param localConstantTypes
		 *   A tuple of types of local constants.
		 * @param outerTypes
		 *   A tuple of types of outer (captured) variables.
		 * @param module
		 *   The module in which the code occurs, or nil.
		 * @param lineNumber
		 *   The module line number on which this code starts.
		 * @param lineNumberEncodedDeltas
		 *   A sequence of integers, one per L1 nybblecode instruction, encoding
		 *   the delta to add to the running line number to get to the line on
		 *   which the syntax that led to that nybblecode occurs.  It starts at
		 *   the given lineNumber.  Each encoded value is shifted left from the
		 *   delta magnitude, and the low bit is zero for a positive delta, and
		 *   one for a negative delta.  May be nil if line number information is
		 *   not intended to be captured.
		 * @param originatingPhraseOrIndex
		 *   Either the block [A_Phrase] from which this is built, an integer
		 *   ([A_Number]) that can be used to fetch the phrase from the module,
		 *   or [nil] if such a phrase does not exist.
		 * @param packedDeclarationNames
		 *   A packed [A_String] containing the names of the block's arguments,
		 *   locals, and constants.
		 * @return
		 *   The new compiled code object.
		 */
		fun newCompiledCode(
			nybbles: A_Tuple,
			stackDepth: Int,
			functionType: A_Type,
			primitive: Primitive?,
			returnTypeIfPrimitiveFails: A_Type,
			literals: A_Tuple,
			localVariableTypes: A_Tuple,
			localConstantTypes: A_Tuple,
			outerTypes: A_Tuple,
			module: A_Module,
			lineNumber: Int,
			lineNumberEncodedDeltas: A_Tuple,
			originatingPhraseOrIndex: AvailObject,
			packedDeclarationNames: A_String
		): AvailObject {
			if (primitive !== null) {
				// Sanity check for primitive blocks.  Use this to hunt incorrectly
				// specified primitive signatures.
				val canHaveCode = primitive.canHaveNybblecodes()
				assert(canHaveCode == nybbles.tupleSize() > 0)
				val restrictionSignature = primitive.blockTypeRestriction()
				assert(restrictionSignature.isSubtypeOf(functionType))
			} else {
				assert(nybbles.tupleSize() > 0)
			}
			val argCounts = functionType.argsTupleType().sizeRange()
			val numArgs = argCounts.lowerBound().extractInt()
			assert(argCounts.upperBound().extractInt() == numArgs)
			val numLocals = localVariableTypes.tupleSize()
			val numConstants = localConstantTypes.tupleSize()
			val numLiterals = literals.tupleSize()
			val numOuters = outerTypes.tupleSize()
			val numSlots = numArgs + numLocals + numConstants + stackDepth
			assert(numSlots in 0..0xFFFF)
			assert(numArgs in 0..0xFFFF)
			assert(numLocals in 0..0xFFFF)
			assert(numConstants in 0..0xFFFF)
			assert(numLiterals in 0..0xFFFF)
			assert(numOuters in 0..0xFFFF)
			assert(module.equalsNil() || module.isInstanceOf(MODULE.o))
			assert(lineNumber >= 0)
			val nybbleCount = nybbles.tupleSize()
			val code = newObjectIndexedIntegerIndexedDescriptor(
				numLiterals + numOuters + numLocals + numConstants,
				if (nybbleCount == 0) 0 else nybbleCount + 16 shr 4,
				initialMutableDescriptor)
			code.setSlot(FRAME_SLOTS, numSlots)
			code.setSlot(NUM_ARGS, numArgs)
			code.setSlot(NUM_LOCALS, numLocals)
			code.setSlot(NUM_CONSTANTS, numConstants)
			code.setSlot(NUM_OUTERS, numOuters)
			code.setSlot(FUNCTION_TYPE, functionType.makeShared())

			// Fill in the nybblecodes.
			if (nybbleCount > 0)
			{
				var longIndex = 1
				var currentLong = (15 - nybbleCount and 15).toLong()
				for (i in 1 .. nybbleCount)
				{
					val subIndex = i and 15
					if (subIndex == 0)
					{
						code.setSlot(NYBBLECODES_, longIndex++, currentLong)
						currentLong = 0
					}
					val nybble = nybbles.tupleIntAt(i).toLong()
					currentLong =
						currentLong or (nybble shl (subIndex shl 2))
				}
				// There's always a final write, either partial or full.
				code.setSlot(NYBBLECODES_, longIndex, currentLong)
			}

			// Fill in the literals.
			var literalIndex = 1
			for (tuple in listOf(
				literals, outerTypes, localVariableTypes, localConstantTypes)
			) {
				code.setSlotsFromTuple(
					LITERAL_AT_,
					literalIndex,
					tuple.makeShared(),
					1,
					tuple.tupleSize())
				literalIndex += tuple.tupleSize()
			}
			code.setSlot(HASH, AvailRuntimeSupport.nextNonzeroHash())
			if (primitive != null)
			{
				code.setDescriptor(
					PrimitiveCompiledCodeDescriptor(
						Mutability.SHARED,
						primitive,
						returnTypeIfPrimitiveFails,
						module.makeShared(),
						originatingPhraseOrIndex,
						packedDeclarationNames,
						lineNumber,
						lineNumberEncodedDeltas.makeShared()))
			}
			else
			{
				code.setDescriptor(
					CompiledCodeDescriptor(
						Mutability.SHARED,
						module.makeShared(),
						originatingPhraseOrIndex,
						packedDeclarationNames,
						lineNumber,
						lineNumberEncodedDeltas.makeShared()))
			}

			// Add the newborn raw function to the weak set being used for code
			// coverage tracking.
			activeRawFunctions.add(code)
			return code
		}

		/**
		 * The sole [mutable][Mutability.MUTABLE] descriptor, used only
		 * while initializing a new [A_RawFunction].
		 */
		private val initialMutableDescriptor =
			CompiledCodeDescriptor(Mutability.MUTABLE, nil, nil, nil, -1, nil)

		/**
		 * A [ConcurrentMap] from A_String to Statistic, used to record type
		 * checks during returns from raw functions having the indicated name.
		 */
		val returnerCheckStatisticsByName: ConcurrentMap<A_String, Statistic> =
			ConcurrentHashMap()

		/**
		 * A [ConcurrentMap] from A_String to Statistic, used to record type
		 * checks during returns into raw functions having the indicated name.
		 */
		val returneeCheckStatisticsByName: ConcurrentMap<A_String, Statistic> =
			ConcurrentHashMap()
	}
}
