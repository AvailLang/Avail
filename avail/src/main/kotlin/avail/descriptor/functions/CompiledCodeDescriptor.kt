/*
 * CompiledCodeDescriptor.kt
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
 *   may be used to endorse or promote products derived from this software
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
package avail.descriptor.functions

import avail.AvailRuntime
import avail.AvailRuntimeSupport
import avail.annotations.HideFieldInDebugger
import avail.annotations.ThreadSafe
import avail.compiler.AvailRejectedParseException
import avail.descriptor.fiber.FiberDescriptor
import avail.descriptor.functions.A_Continuation.Companion.frameAt
import avail.descriptor.functions.A_RawFunction.Companion.codeStartingLineNumber
import avail.descriptor.functions.A_RawFunction.Companion.constantTypeAt
import avail.descriptor.functions.A_RawFunction.Companion.literalAt
import avail.descriptor.functions.A_RawFunction.Companion.localTypeAt
import avail.descriptor.functions.A_RawFunction.Companion.methodName
import avail.descriptor.functions.A_RawFunction.Companion.module
import avail.descriptor.functions.A_RawFunction.Companion.numArgs
import avail.descriptor.functions.A_RawFunction.Companion.numConstants
import avail.descriptor.functions.A_RawFunction.Companion.numLiterals
import avail.descriptor.functions.A_RawFunction.Companion.numLocals
import avail.descriptor.functions.A_RawFunction.Companion.numOuters
import avail.descriptor.functions.A_RawFunction.Companion.numSlots
import avail.descriptor.functions.A_RawFunction.Companion.nybbles
import avail.descriptor.functions.A_RawFunction.Companion.outerTypeAt
import avail.descriptor.functions.A_RawFunction.Companion.shortMethodName
import avail.descriptor.functions.CompiledCodeDescriptor.Companion.initialMutableDescriptor
import avail.descriptor.functions.CompiledCodeDescriptor.Companion.returneeCheckStatisticsByName
import avail.descriptor.functions.CompiledCodeDescriptor.Companion.returnerCheckStatisticsByName
import avail.descriptor.functions.CompiledCodeDescriptor.IntegerSlots.Companion.FRAME_SLOTS
import avail.descriptor.functions.CompiledCodeDescriptor.IntegerSlots.Companion.HASH
import avail.descriptor.functions.CompiledCodeDescriptor.IntegerSlots.Companion.NUM_ARGS
import avail.descriptor.functions.CompiledCodeDescriptor.IntegerSlots.Companion.NUM_CONSTANTS
import avail.descriptor.functions.CompiledCodeDescriptor.IntegerSlots.Companion.NUM_LOCALS
import avail.descriptor.functions.CompiledCodeDescriptor.IntegerSlots.Companion.NUM_OUTERS
import avail.descriptor.functions.CompiledCodeDescriptor.IntegerSlots.NYBBLECODES_
import avail.descriptor.functions.CompiledCodeDescriptor.ObjectSlots.FUNCTION_TYPE
import avail.descriptor.functions.CompiledCodeDescriptor.ObjectSlots.LITERAL_AT_
import avail.descriptor.module.A_Module
import avail.descriptor.module.A_Module.Companion.moduleName
import avail.descriptor.module.A_Module.Companion.moduleNameNative
import avail.descriptor.module.A_Module.Companion.originatingPhraseAtIndex
import avail.descriptor.module.A_Module.Companion.shortModuleNameNative
import avail.descriptor.numbers.A_Number
import avail.descriptor.numbers.A_Number.Companion.extractInt
import avail.descriptor.numbers.IntegerDescriptor.Companion.zero
import avail.descriptor.phrases.A_Phrase
import avail.descriptor.phrases.A_Phrase.Companion.primitive
import avail.descriptor.phrases.A_Phrase.Companion.startingLineNumber
import avail.descriptor.phrases.BlockPhraseDescriptor
import avail.descriptor.phrases.DeclarationPhraseDescriptor.DeclarationKind.ARGUMENT
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.AbstractDescriptor.DebuggerObjectSlots.DUMMY_DEBUGGER_SLOT
import avail.descriptor.representation.AvailObject
import avail.descriptor.representation.AvailObject.Companion.newObjectIndexedIntegerIndexedDescriptor
import avail.descriptor.representation.AvailObjectFieldHelper
import avail.descriptor.representation.BitField
import avail.descriptor.representation.Descriptor
import avail.descriptor.representation.IntegerSlotsEnum
import avail.descriptor.representation.Mutability
import avail.descriptor.representation.NilDescriptor.Companion.nil
import avail.descriptor.representation.ObjectSlotsEnum
import avail.descriptor.tuples.A_String
import avail.descriptor.tuples.A_String.Companion.asNativeString
import avail.descriptor.tuples.A_String.Companion.copyStringFromToCanDestroy
import avail.descriptor.tuples.A_Tuple
import avail.descriptor.tuples.A_Tuple.Companion.concatenate
import avail.descriptor.tuples.A_Tuple.Companion.tupleCodePointAt
import avail.descriptor.tuples.A_Tuple.Companion.tupleIntAt
import avail.descriptor.tuples.A_Tuple.Companion.tupleSize
import avail.descriptor.tuples.NybbleTupleDescriptor
import avail.descriptor.tuples.NybbleTupleDescriptor.Companion.generateNybbleTupleFrom
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tupleFromList
import avail.descriptor.tuples.StringDescriptor.Companion.stringFrom
import avail.descriptor.tuples.TupleDescriptor.Companion.emptyTuple
import avail.descriptor.tuples.TupleDescriptor.Companion.tupleFromIntegerList
import avail.descriptor.types.A_Type
import avail.descriptor.types.A_Type.Companion.argsTupleType
import avail.descriptor.types.A_Type.Companion.isSubtypeOf
import avail.descriptor.types.A_Type.Companion.lowerBound
import avail.descriptor.types.A_Type.Companion.returnType
import avail.descriptor.types.A_Type.Companion.sizeRange
import avail.descriptor.types.A_Type.Companion.upperBound
import avail.descriptor.types.CompiledCodeTypeDescriptor.Companion.compiledCodeTypeForFunctionType
import avail.descriptor.types.CompiledCodeTypeDescriptor.Companion.mostGeneralCompiledCodeType
import avail.descriptor.types.FunctionTypeDescriptor
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.MODULE
import avail.descriptor.types.TypeTag
import avail.dispatch.LookupStatistics
import avail.exceptions.unsupported
import avail.interpreter.levelOne.L1Disassembler
import avail.interpreter.levelOne.L1OperandType
import avail.interpreter.levelOne.L1Operation
import avail.interpreter.levelOne.L1Operation.Companion.lookup
import avail.interpreter.levelOne.L1Operation.Ordinals.L1_doGetLocalClearing_ord
import avail.interpreter.levelOne.L1Operation.Ordinals.L1_doPushLastLocal_ord
import avail.interpreter.levelOne.L1Operation.Ordinals.L1_doPushLastOuter_ord
import avail.interpreter.levelOne.L1Operation.Ordinals.L1_doPushLiteral_ord
import avail.interpreter.levelTwo.L2Chunk
import avail.interpreter.levelTwo.L2Chunk.InvalidationReason.CODE_COVERAGE
import avail.interpreter.levelTwo.operation.L2_LOOKUP_BY_VALUES
import avail.interpreter.primitive.Primitive
import avail.interpreter.primitive.bootstrap.lexing.P_BootstrapLexerStringBody
import avail.interpreter.primitive.privatehelpers.P_GetGlobalVariableValue
import avail.interpreter.primitive.privatehelpers.P_PushArgument1
import avail.interpreter.primitive.privatehelpers.P_PushArgument2
import avail.interpreter.primitive.privatehelpers.P_PushArgument3
import avail.interpreter.primitive.privatehelpers.P_PushConstant
import avail.interpreter.primitive.privatehelpers.P_PushLastOuter
import avail.optimizer.DefaultL1ExecutableChunk
import avail.optimizer.DefaultL1ExecutableChunk.DefaultL1Chunk
import avail.optimizer.OptimizationLevel
import avail.optimizer.OptimizationLevel.Companion.countdownResetAfterEnoughFallbackLookups
import avail.optimizer.OptimizationLevel.Companion.maxSlowLookupsBeforeReoptimization
import avail.optimizer.jvm.CheckedMethod
import avail.optimizer.jvm.CheckedMethod.Companion.instanceMethod
import avail.performance.Statistic
import avail.performance.StatisticReport.DYNAMIC_LOOKUP_BY_CALLER
import avail.performance.StatisticReport.NON_PRIMITIVE_RETURNEE_TYPE_CHECKS
import avail.performance.StatisticReport.NON_PRIMITIVE_RETURNER_TYPE_CHECKS
import avail.serialization.SerializerOperation
import avail.utility.Strings.newlineTab
import avail.utility.cast
import org.availlang.json.JSONWriter
import java.util.Collections.newSetFromMap
import java.util.Collections.synchronizedSet
import java.util.IdentityHashMap
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.LongAdder
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
 * @param originatingPhraseIndex
 *   A one-based index into the module's tuple of block phrases.  If the module
 *   is unavailable or did not store such a phrase, this is -1 and the
 *   [originatingPhrase] contains the phrase from which this code was
 *   constructed.
 * @param originatingPhrase
 *   Either [nil] or the [A_Phrase] that this code was built from.  If it's nil,
 *   it can be reconstructed by asking the [module] to look up the phrase it has
 *   stored under the [originatingPhraseIndex], which may then be cached in this
 *   field.
 * @param packedDeclarationNames
 *   A packed [A_String] containing the names of the block's arguments, locals,
 *   constants, optional label, and outers.
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
	private var originatingPhraseIndex: Int,
	@Volatile private var originatingPhrase: A_Phrase,
	private val packedDeclarationNames: A_String,
	private val lineNumber: Int,
	private val lineNumberEncodedDeltas: A_Tuple
) : Descriptor(
	mutability,
	TypeTag.RAW_FUNCTION_TAG,
	ObjectSlots::class.java,
	IntegerSlots::class.java)
{
	/** A descriptive [A_String] that names this [A_RawFunction]. */
	protected var methodName = unknownFunctionName

	/**
	 * The [L2Chunk] that should be invoked whenever this code is started. The
	 * chunk may no longer be [valid][L2Chunk.isValid], in which case the
	 * [DefaultL1Chunk] will be used instead until the next reoptimization.
	 */
	@Volatile
	private var startingChunk: L2Chunk = DefaultL1Chunk

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

		companion object
		{
			/**
			 * The hash value of this [compiled][CompiledCodeDescriptor].  It is
			 * computed at construction time.
			 */
			val HASH = BitField(HASH_AND_OUTERS, 32, 32) { null }

			/**
			 * The number of outer variables that must be captured by my
			 * [functions][FunctionDescriptor].
			 */
			val NUM_OUTERS = BitField(HASH_AND_OUTERS, 0, 16, Int::toString)

			/**
			 * The number of [frame&#32;slots][A_Continuation.frameAt] to
			 * allocate for continuations running this code.
			 */
			val FRAME_SLOTS = BitField(
				NUM_SLOTS_ARGS_LOCALS_AND_CONSTANTS, 48, 16, Int::toString)

			/**
			 * The number of [arguments][ARGUMENT] that this code expects.
			 */
			val NUM_ARGS = BitField(
				NUM_SLOTS_ARGS_LOCALS_AND_CONSTANTS, 32, 16, Int::toString)

			/**
			 * The number of local variables declared in this code.  This does
			 * not include arguments or local constants.
			 */
			val NUM_LOCALS = BitField(
				NUM_SLOTS_ARGS_LOCALS_AND_CONSTANTS, 16, 16, Int::toString)

			/**
			 * The number of local constants declared in this code.  These occur
			 * in the frame after the arguments and local variables.
			 */
			val NUM_CONSTANTS = BitField(
				NUM_SLOTS_ARGS_LOCALS_AND_CONSTANTS, 0, 16, Int::toString)
		}
	}

	/**
	 * The layout of object slots for my instances.
	 */
	enum class ObjectSlots : ObjectSlotsEnum
	{
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
	internal class InvocationStatistic
	{
		/**
		 * A [LongAdder] holding a count of the total number of times this
		 * code has been invoked.  This statistic can be useful during
		 * optimization.
		 */
		val totalInvocations = LongAdder()

		/**
		 * An [AtomicLong] that indicates how many more invocations can take
		 * place before the corresponding [L2Chunk] should be re-optimized.
		 */
		val countdownToReoptimize =
			AtomicLong(OptimizationLevel.UNOPTIMIZED.countdown)

		/**
		 * A count of the number of [L2_LOOKUP_BY_VALUES] instructions in L2
		 * code that have run for this chunk.
		 */
		val fallbackLookupsSinceOptimization = AtomicLong(0L)

		/** A statistic for all functions that return. */
		@Volatile
		var returnerCheckStat: Statistic? = null

		/** A statistic for all functions that are returned into. */
		@Volatile
		var returneeCheckStat: Statistic? = null

		/**
		 * A statistic tracking the cost of fallback dynamic lookups performed
		 * within this raw function.
		 */
		@Volatile
		var lookupStat: LookupStatistics? = null

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
	class L1InstructionDecoder
	{
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
		 * Read or set the current one-based program counter.  This can be done
		 * independently of the call to [AvailObject.setUpInstructionDecoder].
		 */
		var pc: Int
			get() = (longIndex - baseIndexInArray shl 4) + (shift shr 2)
			set(value)
			{
				longIndex = baseIndexInArray + (value shr 4)
				shift = value and 15 shl 2
			}

		/**
		 * Get one nybble from the stream of nybblecodes.
		 *
		 * @return
		 *   The consumed nybble.
		 */
		fun getNybble(): Int
		{
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
		fun getOperation(): L1Operation
		{
			var index = getNybble()
			if (index == 15)
			{
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
		fun getOperationOrdinal(): Int
		{
			var index = getNybble()
			if (index == 15)
			{
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
		fun getOperand(): Int
		{
			val firstNybble = getNybble()
			val encodeShift = firstNybble shl 2
			var count = (-0x7bdeef0000000000L ushr encodeShift).toInt() and 15
			var value = 0
			while (count-- > 0)
			{
				value = (value shl 4) + getNybble()
			}
			val lowOff = (0x00AAAA9876543210L ushr encodeShift).toInt() and 15
			val highOff = (0x0032100000000000L ushr encodeShift).toInt() and 15
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

		override fun toString() = "${javaClass.simpleName} (pc=$pc)"

		companion object
		{
			/** The offset into the array of the first nybblecode. */
			val baseIndexInArray = NYBBLECODES_.ordinal

			init
			{
				assert(baseIndexInArray == NYBBLECODES_.ordinal)
			}

			/** A reusable empty array of longs for initializing instances. */
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
	) : Comparable<CodeCoverageReport>
	{
		override fun compareTo(other: CodeCoverageReport): Int
		{
			val moduleComp = moduleName.compareTo(other.moduleName)
			if (moduleComp != 0)
			{
				return moduleComp
			}
			val lineComp =
				startingLineNumber.compareTo(other.startingLineNumber)
			return if (lineComp != 0)
			{
				lineComp
			}
			else methodName.compareTo(other.methodName)
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
		recursionMap: IdentityHashMap<A_BasicObject, Unit>,
		indent: Int)
	{
		super.printObjectOnAvoidingIndent(self, builder, recursionMap, indent)
		val longCount = self.variableIntegerSlotsCount()
		if (longCount > 0)
		{
			builder.newlineTab(indent)
			builder.append("Nybblecodes:\n")
			L1Disassembler(self).print(builder, recursionMap, indent + 1)
		}
	}

	override fun o_ConstantTypeAt(self: AvailObject, index: Int): A_Type
	{
		assert(1 <= index && index <= self.numConstants)
		return self.literalAt(self.numLiterals - self.numConstants + index)
	}

	override fun o_CountdownToReoptimize(self: AvailObject, value: Long)
	{
		invocationStatistic.countdownToReoptimize.set(value)
	}

	override fun o_DeclarationNames(self: AvailObject): A_Tuple
	{
		val names = mutableListOf<A_String>()
		val limit = packedDeclarationNames.tupleSize
		if (limit == 0) return emptyTuple
		var position = 1
		while (true)
		{
			if (packedDeclarationNames.tupleCodePointAt(position) == '\"'.code)
			{
				val token = try
				{
					P_BootstrapLexerStringBody.parseString(
						packedDeclarationNames, position, 1, nil)
				}
				catch (e: AvailRejectedParseException)
				{
					throw RuntimeException("Invalid encoded declaration names")
				}
				names.add(token.literal())
				position += token.string().tupleSize
			}
			else
			{
				val start = position
				while (position <= limit
					&& packedDeclarationNames.tupleCodePointAt(position)
					!= ','.code)
				{
					position++
				}
				names.add(
					packedDeclarationNames.copyStringFromToCanDestroy(
						start, position - 1, false))
			}
			if (position == limit + 1)
				return tupleFromList(names).makeShared()
			if (packedDeclarationNames.tupleCodePointAt(position)
				!= ','.code)
			{
				throw RuntimeException("Invalid encoded declaration names")
			}
			position++
		}
	}

	override fun o_DecrementCountdownToReoptimize(self: AvailObject): Boolean
	{
		val countdown = invocationStatistic.countdownToReoptimize
		if (countdown.get().let { it == Long.MAX_VALUE || it <= 0 })
			return false
		var newValue: Long
		do
		{
			var counter = countdown.get()
			if (counter == Long.MAX_VALUE || counter <= 0) return false
			newValue = counter - 1
		} while (!countdown.compareAndSet(counter, newValue))
		return newValue == 0L
	}

	override fun o_DecreaseCountdownToReoptimizeFromPoll(
		self: AvailObject,
		delta: Long)
	{
		val countdown = invocationStatistic.countdownToReoptimize
		val current = countdown.get()
		when
		{
			// Ignore this poll if it's max or already negative (or zero).
			current == Long.MAX_VALUE || current <= 0 -> return
			// It's positive.  Decrease it by delta, but not below 2.
			current <= 0L -> return
			else ->
			{
				do
				{
					var counter = countdown.get()
					if (counter == Long.MAX_VALUE || counter <= 0) return
				} while (!countdown.compareAndSet(
						counter, max(2L, counter - delta)))
			}
		}
	}

	override fun o_EncounteredFallbackLookup(self: AvailObject)
	{
		val lookupsCount = invocationStatistic.fallbackLookupsSinceOptimization
			.incrementAndGet()
		// After many fallback lookups, we should always reoptimize the chunk,
		// whether its countdown is Long.MAX_VALUE or any other positive value.
		if (lookupsCount == maxSlowLookupsBeforeReoptimization)
		{
			// Reset the countdown to 1, but only if it was already positive.
			// If it was negative, it's already being reoptimized (which will
			// also zero this counter).
			invocationStatistic.countdownToReoptimize.updateAndGet {
				if (it > 0) countdownResetAfterEnoughFallbackLookups
				else it
			}
		}
	}

	/**
	 * {@inheritDoc}
	 *
	 * Show the types of local variables and outer variables.
	 */
	override fun o_DescribeForDebugger(
		self: AvailObject
	): Array<AvailObjectFieldHelper> = with(self)
	{
		val fields = mutableListOf(*super.o_DescribeForDebugger(self))
		fields.add(
			AvailObjectFieldHelper(
				self,
				DUMMY_DEBUGGER_SLOT,
				-1,
				this@CompiledCodeDescriptor,
				slotName = "Descriptor"))
		val disassembled = L1Disassembler(self).disassembledAsSlots()
		if (variableIntegerSlotsCount() > 0)
		{
			val moduleName = module.run {
				if (isNil) "No module"
				else shortModuleNameNative
			}
			val primString = when (primitive)
			{
				null -> ""
				else -> " ($primitive)"
			}
			fields.add(
				AvailObjectFieldHelper(
					self,
					DUMMY_DEBUGGER_SLOT,
					-1,
					null,
					slotName = "Disassembly",
					forcedName = "L1 Disassembly ($moduleName)$primString",
					forcedChildren = disassembled.toTypedArray()))
		}
		val literalFields = mutableListOf<AvailObjectFieldHelper>()
		val baseLiterals =
			numLiterals - numConstants - numLocals - numOuters
		(1 .. baseLiterals).mapTo(literalFields) {
			AvailObjectFieldHelper(
				self,
				DUMMY_DEBUGGER_SLOT,
				it,
				literalAt(it),
				slotName = "Base literal")
		}
		(1 .. numOuters).mapTo(literalFields) {
			AvailObjectFieldHelper(
				self,
				DUMMY_DEBUGGER_SLOT,
				it,
				outerTypeAt(it),
				slotName = "Outer type")
		}
		(1 .. numLocals).mapTo(literalFields) {
			AvailObjectFieldHelper(
				self,
				DUMMY_DEBUGGER_SLOT,
				it,
				localTypeAt(it),
				slotName = "Local type")
		}
		(1 .. numConstants).mapTo(literalFields) {
			AvailObjectFieldHelper(
				self,
				DUMMY_DEBUGGER_SLOT,
				it,
				constantTypeAt(it),
				slotName = "Constant type")
		}
		val allLiterals = (1 .. numLiterals).map { literalAt(it) }
		fields.add(
			AvailObjectFieldHelper(
				self,
				DUMMY_DEBUGGER_SLOT,
				-1,
				tupleFromList(allLiterals),
				slotName = "All literals",
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
		self[FUNCTION_TYPE]

	override fun o_Hash(self: AvailObject) = self[HASH]

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
		self[LITERAL_AT_, index]

	override fun o_LocalTypeAt(self: AvailObject, index: Int): A_Type
	{
		assert(1 <= index && index <= self.numLocals)
		return self.literalAt(
			(self.numLiterals
				- self.numConstants
				- self.numLocals)
				+ index)
	}

	override fun o_MaxStackDepth(self: AvailObject) =
		(self.numSlots - self.numArgs() - self.numLocals)

	override fun o_MethodName(self: AvailObject): A_String = methodName

	/**
	 * Answer the module in which this code occurs.
	 */
	override fun o_Module(self: AvailObject): A_Module = module

	override fun o_NameForDebugger(self: AvailObject) = buildString {
		append(super.o_NameForDebugger(self))
		append(": ")
		append(methodName)
		val moduleAndLine = module.run {
			if (isNil) "[No module]"
			else "[$shortModuleNameNative:${self.startingLineNumber}]"
		}
		append(moduleAndLine)
	}

	override fun o_NumArgs(self: AvailObject) = self[NUM_ARGS]

	override fun o_NumConstants(self: AvailObject) = self[NUM_CONSTANTS]

	override fun o_NumLiterals(self: AvailObject) =
		self.variableObjectSlotsCount()

	override fun o_NumLocals(self: AvailObject) = self[NUM_LOCALS]

	override fun o_NumNybbles(self: AvailObject): Int
	{
		val longCount = self.variableIntegerSlotsCount()
		if (longCount == 0)
		{
			// Special case: when there are no nybbles, don't reserve any longs.
			return 0
		}
		val firstLong = self[NYBBLECODES_, 1]
		val unusedNybbles = firstLong.toInt() and 15
		return (longCount shl 4) - unusedNybbles - 1
	}

	override fun o_NumOuters(self: AvailObject) = self[NUM_OUTERS]

	override fun o_NumSlots(self: AvailObject) = self[FRAME_SLOTS]

	override fun o_Nybbles(self: AvailObject): A_Tuple
	{
		// Extract a tuple of nybbles.
		val longCount = self.variableIntegerSlotsCount()
		if (longCount == 0)
		{
			// Special case: when there are no nybbles, don't reserve any longs.
			return emptyTuple
		}
		val decoder = L1InstructionDecoder()
		self.setUpInstructionDecoder(decoder, 1)
		return generateNybbleTupleFrom(o_NumNybbles(self)) {
			decoder.getNybble()
		}
	}

	override fun o_OriginatingPhrase(self: AvailObject): A_Phrase
	{
		var phrase = originatingPhrase
		if (phrase.isNil && originatingPhraseIndex != -1)
		{
			phrase = module.originatingPhraseAtIndex(originatingPhraseIndex)
			// This volatile write is idempotent, so no lock is needed.
			originatingPhrase = phrase
		}
		return phrase
	}

	override fun o_OriginatingPhraseIndex(self: AvailObject): Int =
		originatingPhraseIndex

	override fun o_OuterTypeAt(self: AvailObject, index: Int): A_Type
	{
		assert(1 <= index && index <= self.numOuters)
		return self.literalAt(
			(self.numLiterals
				- self.numConstants
				- self.numLocals
				- self.numOuters)
				+ index)
	}

	override fun o_PackedDeclarationNames(self: AvailObject): A_String =
		packedDeclarationNames

	override fun o_Primitive(self: AvailObject): Primitive? = null

	/**
	 * Answer the [Statistic] used to record the cost of explicitly type
	 * checking returns from the raw function.  These are also collected into
	 * the [returnerCheckStatisticsByName], to ensure unloading/reloading
	 * a module will reuse the same statistic objects.
	 *
	 * @param self
	 *   The raw function.
	 * @return
	 *   A [Statistic], creating one if necessary.
	 */
	override fun o_ReturnerCheckStat(self: AvailObject): Statistic
	{
		invocationStatistic.returnerCheckStat?.let { return it }
		synchronized(invocationStatistic) {
			invocationStatistic.returnerCheckStat?.let { return it }
			// Look it up by name, creating it if necessary.
			val name = self.methodName
			val returnerStat =
				returnerCheckStatisticsByName.computeIfAbsent(name) {
					Statistic(
						NON_PRIMITIVE_RETURNER_TYPE_CHECKS,
						"Checked return from " + name.asNativeString())
				}
			invocationStatistic.returnerCheckStat = returnerStat
			return returnerStat
		}
	}

	/**
	 * Answer the [Statistic] used to record the cost of explicitly type
	 * checking returns back into the raw function.  These are also collected
	 * into the [returneeCheckStatisticsByName], to ensure
	 * unloading/reloading a module will reuse the same statistic objects.
	 *
	 * @param self
	 *   The raw function.
	 * @return
	 *   A [Statistic], creating one if necessary.
	 */
	override fun o_ReturneeCheckStat(self: AvailObject): Statistic
	{
		invocationStatistic.returneeCheckStat?.let { return it }
		synchronized(invocationStatistic) {
			invocationStatistic.returneeCheckStat?.let { return it }
			// Look it up by name, creating it if necessary.
			val name = self.methodName
			val returneeStat =
				returneeCheckStatisticsByName.computeIfAbsent(name) {
					Statistic(
						NON_PRIMITIVE_RETURNEE_TYPE_CHECKS,
						"Checked return into " + name.asNativeString())
				}
			invocationStatistic.returneeCheckStat = returneeStat
			return returneeStat
		}
	}

	/**
	 * Answer the [Statistic] used to record the cost of fallback method lookups
	 * from within this raw function.  These are also collected into the
	 * [returneeCheckStatisticsByName], to ensure unloading/reloading a module
	 * will reuse the same statistic objects.
	 *
	 * @param self
	 *   The raw function.
	 * @return
	 *   A [Statistic], creating one if necessary.
	 */
	override fun o_LookupStat(self: AvailObject): LookupStatistics
	{
		invocationStatistic.lookupStat?.let { return it }
		synchronized(invocationStatistic) {
			invocationStatistic.lookupStat?.let { return it }
			// Look it up by name, creating it if necessary.
			val name = self.shortMethodName
			val lookupStat =
				dynamicLookupStatisticsByName.computeIfAbsent(name) {
					LookupStatistics(name, DYNAMIC_LOOKUP_BY_CALLER)
				}
			invocationStatistic.lookupStat = lookupStat
			return lookupStat
		}
	}

	override fun o_ReturnTypeIfPrimitiveFails(self: AvailObject): A_Type =
		self.functionType().returnType

	@ThreadSafe
	override fun o_SerializerOperation(self: AvailObject) =
		SerializerOperation.COMPILED_CODE

	override fun o_SetMethodName(
		self: AvailObject,
		methodName: A_String)
	{
		assert(mutability === Mutability.SHARED)
		assert(methodName.isString)
		methodName.makeShared()
		this.methodName = methodName
		// Now scan all sub-blocks. Some literals will be functions and some
		// will be compiled code objects.
		var counter = 1
		loop@ for (i in 1 .. self.numLiterals)
		{
			val literal = self.literalAt(i)
			val subCode: A_RawFunction = when
			{
				literal.isFunction -> literal.code()
				literal.isInstanceOf(mostGeneralCompiledCodeType()) -> literal
				else -> continue@loop
			}
			subCode.methodName = concatenate(
				methodName,
				stringFrom("#${counter++}"),
				true
			).makeShared()
		}
	}

	override fun o_SetOriginatingPhraseIndex(
		self: AvailObject,
		index: Int)
	{
		originatingPhraseIndex = index
	}

	override fun o_SetStartingChunkAndReoptimizationCountdown(
		self: AvailObject,
		chunk: L2Chunk,
		countdown: Long)
	{
		// First update the chunk.
		// Other threads may start running the new chunk, but they'll see a
		// negative countdown, so they can't make it reach exactly 0 (assuming
		// the 64-bit counter doesn't underflow).
		startingChunk = chunk
		// Now update the countdown to a positive value, allowing some future
		// decrement to reach exactly zero.
		invocationStatistic.countdownToReoptimize.set(countdown)
		// This races with code encountering fallback lookups in other threads,
		// failing to distinguish between fallbacks in the new chunk and
		// fallbacks in the old chunk.  It's almost certainly benign.
		invocationStatistic.fallbackLookupsSinceOptimization.set(0)
	}

	override fun o_ShowValueInNameForDebugger(self: AvailObject) = false

	override fun o_StartingChunk(self: AvailObject): L2Chunk
	{
		val chunk = startingChunk
		assert(chunk.isValid)
		if (chunk != DefaultL1ExecutableChunk)
		{
			L2Chunk.Generation.usedChunk(chunk)
		}
		// Memory pressure from L2Chunk generations causes a task to be queued
		// to do the (bulk) invalidation while all fibers are paused.  Therefore
		// we can't observe a chunk becoming invalid here.  However, which chunk
		// is the current startingChunk might change, due to *another* fiber
		// optimizing this code.  Just answer whichever chunk we found at first.
		assert(chunk.isValid)
		return chunk
	}

	/**
	 * Answer the starting line number for this block of code.
	 */
	override fun o_StartingLineNumber(self: AvailObject) = lineNumber

	override fun o_TallyInvocation(self: AvailObject)
	{
		invocationStatistic.run {
			totalInvocations.add(1)
			if (!hasRun) hasRun = true
		}
	}

	override fun o_TotalInvocations(self: AvailObject) =
		invocationStatistic.totalInvocations.toLong()

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
		at("outers") { write(self[NUM_OUTERS]) }
		at("arguments") { write(self[NUM_ARGS]) }
		at("locals") { write(self[NUM_LOCALS]) }
		at("constants") { write(self[NUM_CONSTANTS]) }
		at("maximum stack depth") { write(self[FRAME_SLOTS]) }
		at("nybbles") { self.nybbles.writeTo(writer) }
		at("function type") { self[FUNCTION_TYPE].writeFunctionType() }
		at("method") { self.methodName.writeTo(writer) }
		if (module.notNil)
		{
			at("module") { self.module.moduleName.writeTo(writer) }
		}
		at("starting line number") { write(self.codeStartingLineNumber) }
		at("literals") {
			writeArray {
				val limit = self.variableObjectSlotsCount()
				for (i in 1 .. limit)
				{
					var literal: A_BasicObject = self[LITERAL_AT_, i]
					if (literal.isNil)
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
		ReplaceWith("newCompiledCode"))
	override fun mutable() = unsupported

	@Deprecated(
		"Not supported",
		ReplaceWith("newCompiledCode"))
	override fun immutable() = unsupported

	@Deprecated(
		"Not supported",
		ReplaceWith("newCompiledCode"))
	override fun shared() = unsupported

	companion object
	{
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
		fun resetCodeCoverageDetailsThen(resume: ()->Unit) =
			AvailRuntime.currentRuntime().whenSafePointDo(
				FiberDescriptor.commandPriority
			) {
				L2Chunk.invalidationLock.withLock {
					// Loop over each instance, setting the touched flag to
					// false and discarding optimizations.
					for (rawFunction in activeRawFunctions)
					{
						val self: AvailObject = rawFunction.cast()
						val descriptor: CompiledCodeDescriptor =
							self.descriptor.cast()
						descriptor.invocationStatistic.hasRun = false
						if (descriptor.module.notNil)
						{
							val chunk = descriptor.startingChunk
							if (chunk != DefaultL1ExecutableChunk)
							{
								chunk.invalidate(CODE_COVERAGE)
							}
						}
					}
					AvailRuntime.currentRuntime().whenRunningInterpretersDo(
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
			resume: (List<CodeCoverageReport>)->Unit
		) = AvailRuntime.currentRuntime().whenSafePointDo(
			FiberDescriptor.commandPriority
		) {
			val reports: MutableList<CodeCoverageReport> = mutableListOf()

			// Loop over each instance, creating its report object.
			for (rawFunction in activeRawFunctions)
			{
				val self: AvailObject = rawFunction.cast()
				val descriptor: CompiledCodeDescriptor =
					self.descriptor.cast()
				val module = descriptor.module
				if (module.notNil)
				{
					val report = CodeCoverageReport(
						descriptor.invocationStatistic.hasRun,
						descriptor.startingChunk != DefaultL1ExecutableChunk,
						descriptor.lineNumber,
						module.moduleNameNative,
						descriptor.methodName.asNativeString())
					if (!reports.contains(report))
					{
						reports.add(report)
					}
				}
			}
			AvailRuntime.currentRuntime().whenRunningInterpretersDo(
				FiberDescriptor.commandPriority
			) { resume(reports) }
		}

		/** The [CheckedMethod] for [A_RawFunction.codePrimitive]. */
		val codePrimitiveMethod = instanceMethod(
			A_RawFunction::class.java,
			A_RawFunction::codePrimitive.name,
			Primitive::class.java)

		/** The Avail string "Unknown function". */
		val unknownFunctionName: A_String =
			stringFrom("Unknown function").makeShared()

		val specialPrimitivePatterns: Map<A_Tuple, Primitive> =
			mapOf(
				listOf(L1_doPushLiteral_ord, 1) to P_PushConstant,
				listOf(L1_doPushLastLocal_ord, 1) to P_PushArgument1,
				listOf(L1_doPushLastLocal_ord, 2) to P_PushArgument2,
				listOf(L1_doPushLastLocal_ord, 3) to P_PushArgument3,
				listOf(L1_doPushLastOuter_ord, 1) to P_PushLastOuter,
				listOf(L1_doGetLocalClearing_ord, 1) to P_GetGlobalVariableValue
			).mapKeys { (k, v) -> tupleFromIntegerList(k).makeShared() }

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
		 * @param originatingPhraseIndex
		 *   Either the block [A_Phrase] from which this is built, an integer
		 *   ([A_Number]) that can be used to fetch the phrase from the module,
		 *   or [nil] if such a phrase does not exist.
		 * @param packedDeclarationNames
		 *   A packed [A_String] containing the names of the block's arguments,
		 *   locals, constants, optional label, and outers.
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
			originatingPhraseIndex: Int,
			originatingPhrase: A_Phrase,
			packedDeclarationNames: A_String
		): AvailObject
		{
			val argCounts = functionType.argsTupleType.sizeRange
			val numArgs = argCounts.lowerBound.extractInt
			val numLiterals = literals.tupleSize
			val primitiveToUse = when (primitive)
			{
				null ->
				{
					// See if we should supply a special primitive for certain
					// forms of short functions.
					assert(nybbles.tupleSize > 0)
					specialPrimitivePatterns[nybbles]?.run {
						if (checkSpecialForm(numArgs, literals)) this
						else null
					}
				}
				else ->
				{
					// Sanity check for primitive blocks.  Use this to hunt
					// incorrectly specified primitive signatures.
					val canHaveCode = primitive.canHaveNybblecodes()
					assert(canHaveCode == nybbles.tupleSize > 0)
					val restrictionSignature = primitive.blockTypeRestriction()
					assert(restrictionSignature.isSubtypeOf(functionType))
					primitive
				}
			}
			assert(argCounts.upperBound.extractInt == numArgs)
			val numLocals = localVariableTypes.tupleSize
			val numConstants = localConstantTypes.tupleSize
			val numOuters = outerTypes.tupleSize
			val numSlots = numArgs + numLocals + numConstants + stackDepth
			assert(numSlots in 0 .. 0xFFFF)
			assert(numArgs in 0 .. 0xFFFF)
			assert(numLocals in 0 .. 0xFFFF)
			assert(numConstants in 0 .. 0xFFFF)
			assert(numLiterals in 0 .. 0xFFFF)
			assert(numOuters in 0 .. 0xFFFF)
			assert(module.isNil || module.isInstanceOf(MODULE()))
			assert(lineNumber >= 0)
			val nybbleCount = nybbles.tupleSize
			val code = newObjectIndexedIntegerIndexedDescriptor(
				numLiterals + numOuters + numLocals + numConstants,
				if (nybbleCount == 0) 0 else nybbleCount + 16 shr 4,
				initialMutableDescriptor)
			code[FRAME_SLOTS] = numSlots
			code[NUM_ARGS] = numArgs
			code[NUM_LOCALS] = numLocals
			code[NUM_CONSTANTS] = numConstants
			code[NUM_OUTERS] = numOuters
			code[FUNCTION_TYPE] = functionType.makeShared()

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
						code[NYBBLECODES_, longIndex++] = currentLong
						currentLong = 0
					}
					val nybble = nybbles.tupleIntAt(i).toLong()
					currentLong =
						currentLong or (nybble shl (subIndex shl 2))
				}
				// There's always a final write, either partial or full.
				code[NYBBLECODES_, longIndex] = currentLong
			}

			// Fill in the literals.
			var literalIndex = 1
			for (tuple in listOf(
				literals, outerTypes, localVariableTypes, localConstantTypes))
			{
				code.setSlotsFromTuple(
					LITERAL_AT_,
					literalIndex,
					tuple.makeShared(),
					1,
					tuple.tupleSize)
				literalIndex += tuple.tupleSize
			}
			code[HASH] = AvailRuntimeSupport.nextNonzeroHash()
			if (primitiveToUse != null)
			{
				code.descriptor =
					PrimitiveCompiledCodeDescriptor(
						Mutability.SHARED,
						primitiveToUse,
						returnTypeIfPrimitiveFails.makeShared(),
						module.makeShared(),
						originatingPhraseIndex,
						originatingPhrase.makeShared(),
						packedDeclarationNames.makeShared(),
						lineNumber,
						lineNumberEncodedDeltas.makeShared())
			}
			else
			{
				code.descriptor =
					CompiledCodeDescriptor(
						Mutability.SHARED,
						module.makeShared(),
						originatingPhraseIndex,
						originatingPhrase.makeShared(),
						packedDeclarationNames.makeShared(),
						lineNumber,
						lineNumberEncodedDeltas.makeShared())
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
		val initialMutableDescriptor = CompiledCodeDescriptor(
			Mutability.MUTABLE, nil, -1, nil, nil, -1, nil)

		/**
		 * A [ConcurrentMap] from [A_String] to [Statistic], used to record type
		 * checks during returns from raw functions having the indicated name.
		 */
		val returnerCheckStatisticsByName: ConcurrentMap<A_String, Statistic> =
			ConcurrentHashMap()

		/**
		 * A [ConcurrentMap] from [A_String] to [Statistic], used to record type
		 * checks during returns into raw functions having the indicated name.
		 */
		val returneeCheckStatisticsByName: ConcurrentMap<A_String, Statistic> =
			ConcurrentHashMap()

		/**
		 * A [ConcurrentMap] from [String] to [Statistic], used to record
		 * dynamic lookups by calling raw function name.
		 */
		val dynamicLookupStatisticsByName =
			ConcurrentHashMap<String, LookupStatistics>()
	}
}
