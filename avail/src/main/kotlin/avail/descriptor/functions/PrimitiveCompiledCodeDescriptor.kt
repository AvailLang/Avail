/*
 * PrimitiveCompiledCodeDescriptor.kt
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

import avail.annotations.HideFieldInDebugger
import avail.descriptor.functions.A_Continuation.Companion.frameAt
import avail.descriptor.methods.MethodDescriptor.SpecialMethodAtom
import avail.descriptor.module.A_Module
import avail.descriptor.numbers.A_Number.Companion.extractInt
import avail.descriptor.phrases.A_Phrase
import avail.descriptor.phrases.DeclarationPhraseDescriptor.DeclarationKind.ARGUMENT
import avail.descriptor.representation.AvailObject
import avail.descriptor.representation.BitField
import avail.descriptor.representation.IntegerSlotsEnum
import avail.descriptor.representation.Mutability
import avail.descriptor.representation.NilDescriptor.Companion.nil
import avail.descriptor.representation.ObjectSlotsEnum
import avail.descriptor.tuples.A_String
import avail.descriptor.tuples.A_Tuple
import avail.descriptor.types.A_Type
import avail.descriptor.types.A_Type.Companion.argsTupleType
import avail.descriptor.types.A_Type.Companion.returnType
import avail.descriptor.types.A_Type.Companion.sizeRange
import avail.descriptor.types.A_Type.Companion.typeAtIndex
import avail.descriptor.types.A_Type.Companion.upperBound
import avail.descriptor.types.BottomTypeDescriptor.Companion.bottom
import avail.descriptor.types.FunctionTypeDescriptor
import avail.interpreter.Primitive
import avail.interpreter.execution.Interpreter
import avail.interpreter.levelOne.L1InstructionWriter
import avail.interpreter.levelOne.L1Operation
import avail.interpreter.levelTwo.L2Chunk

/**
 * This is a special case of a [CompiledCodeDescriptor] that specifies a
 * [Primitive] to be attempted, and only if it fails (if that's possible for
 * the specific primitive) should it fall back on running the L1 instructions.
 *
 * @constructor
 *
 * @param mutability
 *   The [Mutability] of the resulting descriptor.  This should only be
 *   [Mutability.MUTABLE] for the
 *   [CompiledCodeDescriptor.initialMutableDescriptor], and [Mutability.SHARED]
 *   for normal instances.
 * @param primitive
 *   The [Primitive] that should run when this code is invoked. If the primitive
 *   [fails][Interpreter.primitiveFailure], this [A_RawFunction]'s nybblecodes
 *   will be executed instead, as determined by the current [L2Chunk].
 * @param returnTypeIfPrimitiveFails
 *   The type that will be returned by the nybblecodes, if they run.
 * @param module
 *   The [module][A_Module] creating this primitive function.
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
 * @param lineNumber
 *   The starting [lineNumber] of this function, if known, otherwise `0`.
 * @param lineNumberEncodedDeltas
 *   An encoded [A_Tuple] of line number deltas, one per nybblecode instruction.
 *   This is [nil] if the line number information is not available.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
class PrimitiveCompiledCodeDescriptor internal constructor(
	mutability: Mutability,
	private val primitive: Primitive,
	private val returnTypeIfPrimitiveFails: A_Type,
	module: A_Module,
	originatingPhraseIndex: Int,
	originatingPhrase: A_Phrase,
	packedDeclarationNames: A_String,
	lineNumber: Int,
	lineNumberEncodedDeltas: A_Tuple
) : CompiledCodeDescriptor(
	mutability,
	module,
	originatingPhraseIndex,
	originatingPhrase,
	packedDeclarationNames,
	lineNumber,
	lineNumberEncodedDeltas
) {

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

		@Suppress("MemberVisibilityCanBePrivate")
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

			init
			{
				assert(
					CompiledCodeDescriptor.IntegerSlots.HASH_AND_OUTERS
						.ordinal
						== HASH_AND_OUTERS.ordinal)
				assert(
					CompiledCodeDescriptor.IntegerSlots
						.NUM_SLOTS_ARGS_LOCALS_AND_CONSTANTS.ordinal
						== NUM_SLOTS_ARGS_LOCALS_AND_CONSTANTS.ordinal)
				assert(
					CompiledCodeDescriptor.IntegerSlots.NYBBLECODES_.ordinal
						== NYBBLECODES_.ordinal)

				assert(
					CompiledCodeDescriptor.IntegerSlots.HASH
						.isSamePlaceAs(HASH))
				assert(
					CompiledCodeDescriptor.IntegerSlots.NUM_OUTERS
						.isSamePlaceAs(NUM_OUTERS))
				assert(
					CompiledCodeDescriptor.IntegerSlots.FRAME_SLOTS
						.isSamePlaceAs(FRAME_SLOTS))
				assert(
					CompiledCodeDescriptor.IntegerSlots.NUM_ARGS
						.isSamePlaceAs(NUM_ARGS))
				assert(
					CompiledCodeDescriptor.IntegerSlots.NUM_LOCALS
						.isSamePlaceAs(NUM_LOCALS))
				assert(
					CompiledCodeDescriptor.IntegerSlots.NUM_CONSTANTS
						.isSamePlaceAs(NUM_CONSTANTS))
			}
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
		LITERAL_AT_;

		companion object
		{
			init
			{
				assert(
					CompiledCodeDescriptor.ObjectSlots.FUNCTION_TYPE.ordinal
						== FUNCTION_TYPE.ordinal)
				assert(
					CompiledCodeDescriptor.ObjectSlots.LITERAL_AT_.ordinal
						== LITERAL_AT_.ordinal)
			}
		}
	}

	override fun o_NameForDebugger(self: AvailObject) =
		super.o_NameForDebugger(self) + " (${primitive.simpleName})"

	override fun o_ReturnTypeIfPrimitiveFails(self: AvailObject): A_Type =
		returnTypeIfPrimitiveFails

	override fun o_Primitive(self: AvailObject): Primitive = primitive

	companion object {
		/**
		 * Construct a bootstrapped [A_RawFunction] that uses the specified
		 * primitive.  The primitive failure code should invoke the
		 * [SpecialMethodAtom.CRASH]'s bundle with a tuple of passed arguments
		 * followed by the primitive failure value.
		 *
		 * @param primitive
		 *   The [Primitive] to use.
		 * @param module
		 *   The [module][A_Module] making this primitive function.
		 * @param lineNumber
		 *   The line number on which the new function should be said to begin.
		 * @return
		 *   A function.
		 */
		fun newPrimitiveRawFunction(
			primitive: Primitive,
			module: A_Module,
			lineNumber: Int
		): A_RawFunction {
			val writer = L1InstructionWriter(module, lineNumber, nil)
			writer.primitive = primitive
			val functionType = primitive.blockTypeRestriction()
			val argsTupleType = functionType.argsTupleType
			val numArgs = argsTupleType.sizeRange.upperBound.extractInt
			val argTypes = (1..numArgs).map { argsTupleType.typeAtIndex(it) }
			writer.argumentTypes(*argTypes.toTypedArray())
			writer.returnType = functionType.returnType
			writer.returnTypeIfPrimitiveFails = bottom
			primitive.writeDefaultFailureCode(lineNumber, writer, numArgs)
			return writer.compiledCode()
		}
	}
}
