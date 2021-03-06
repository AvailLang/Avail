/*
 * PrimitiveCompiledCodeDescriptor.kt
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

import com.avail.annotations.EnumField
import com.avail.annotations.HideFieldInDebugger
import com.avail.descriptor.methods.MethodDescriptor.SpecialMethodAtom
import com.avail.descriptor.module.A_Module
import com.avail.descriptor.numbers.A_Number.Companion.extractInt
import com.avail.descriptor.phrases.DeclarationPhraseDescriptor.DeclarationKind.ARGUMENT
import com.avail.descriptor.representation.AvailObject
import com.avail.descriptor.representation.BitField
import com.avail.descriptor.representation.IntegerSlotsEnum
import com.avail.descriptor.representation.Mutability
import com.avail.descriptor.representation.NilDescriptor.Companion.nil
import com.avail.descriptor.representation.ObjectSlotsEnum
import com.avail.descriptor.tuples.A_String
import com.avail.descriptor.tuples.A_Tuple
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.A_Type.Companion.argsTupleType
import com.avail.descriptor.types.A_Type.Companion.returnType
import com.avail.descriptor.types.A_Type.Companion.sizeRange
import com.avail.descriptor.types.A_Type.Companion.typeAtIndex
import com.avail.descriptor.types.A_Type.Companion.upperBound
import com.avail.descriptor.types.BottomTypeDescriptor.Companion.bottom
import com.avail.descriptor.types.FunctionTypeDescriptor
import com.avail.interpreter.Primitive
import com.avail.interpreter.execution.Interpreter
import com.avail.interpreter.levelOne.L1InstructionWriter
import com.avail.interpreter.levelOne.L1Operation
import com.avail.interpreter.levelTwo.L2Chunk

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
class PrimitiveCompiledCodeDescriptor constructor(
	mutability: Mutability,
	private val primitive: Primitive,
	private val returnTypeIfPrimitiveFails: A_Type,
	module: A_Module,
	originatingPhraseOrIndex: AvailObject,
	packedDeclarationNames: A_String,
	lineNumber: Int,
	lineNumberEncodedDeltas: A_Tuple
) : CompiledCodeDescriptor(
	mutability,
	module,
	originatingPhraseOrIndex,
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

		companion object
		{
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
		super.o_NameForDebugger(self) + "($primitive): " + methodName

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
			val argsTupleType = functionType.argsTupleType()
			val numArgs = argsTupleType.sizeRange().upperBound().extractInt()
			val argTypes = (1..numArgs).map { argsTupleType.typeAtIndex(it) }
			writer.argumentTypes(*argTypes.toTypedArray())
			writer.returnType = functionType.returnType()
			writer.returnTypeIfPrimitiveFails = bottom
			primitive.writeDefaultFailureCode(lineNumber, writer, numArgs)
			return writer.compiledCode()
		}
	}
}
