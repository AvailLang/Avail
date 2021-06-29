/*
 * A_RawFunction.kt
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

import com.avail.descriptor.functions.CompiledCodeDescriptor.L1InstructionDecoder
import com.avail.descriptor.methods.A_Method
import com.avail.descriptor.module.A_Module
import com.avail.descriptor.numbers.A_Number
import com.avail.descriptor.phrases.A_Phrase
import com.avail.descriptor.phrases.BlockPhraseDescriptor
import com.avail.descriptor.representation.A_BasicObject
import com.avail.descriptor.representation.A_BasicObject.Companion.dispatch
import com.avail.descriptor.representation.AvailObject
import com.avail.descriptor.representation.NilDescriptor.Companion.nil
import com.avail.descriptor.tuples.A_String
import com.avail.descriptor.tuples.A_Tuple
import com.avail.descriptor.tuples.A_Tuple.Companion.tupleSize
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.FunctionTypeDescriptor
import com.avail.interpreter.Primitive
import com.avail.interpreter.execution.Interpreter
import com.avail.interpreter.levelOne.L1Operation
import com.avail.interpreter.levelTwo.L2Chunk
import com.avail.optimizer.L2Generator
import com.avail.optimizer.jvm.CheckedMethod
import com.avail.optimizer.jvm.CheckedMethod.Companion.instanceMethod
import com.avail.optimizer.jvm.ReferencedInGeneratedCode
import com.avail.performance.Statistic

/**
 * `A_RawFunction` is an interface that specifies the operations specific to
 * [function&#32;implementations][CompiledCodeDescriptor] in Avail.
 *
 * An [A_Function] refers to its raw function, plus any outer values captured
 * during function [closure][L1Operation.L1_doClose].
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
interface A_RawFunction : A_BasicObject {
	/**
	 * Helper method for transferring this object's longSlots into an
	 * [L1InstructionDecoder].  The receiver's descriptor must be a
	 * [CompiledCodeDescriptor].
	 *
	 * @param
	 *   instructionDecoder The [L1InstructionDecoder] to populate.
	 */
	fun setUpInstructionDecoder(instructionDecoder: L1InstructionDecoder)

	/**
	 * Answer the [function&#32;type][FunctionTypeDescriptor] associated with
	 * this raw function's closures.
	 *
	 * @return
	 *   The function type associated with this function implementation.
	 */
	@ReferencedInGeneratedCode
	fun functionType(): A_Type = dispatch { o_FunctionType(it) }

	/**
	 * Answer this raw function's [Primitive] or `null`.
	 *
	 * @return
	 *   The [Primitive], or null if this raw function is not primitive.
	 */
	@ReferencedInGeneratedCode
	fun codePrimitive(): Primitive? = dispatch { o_Primitive(it) }

	companion object
	{
		/**
		 * Answer the starting line number for the
		 * [block&#32;phrase][BlockPhraseDescriptor] that defines this raw
		 * function.
		 *
		 * @return
		 *   The starting line number, or zero (`0`) for synthetic function
		 *   implementations.
		 */
		val A_RawFunction.codeStartingLineNumber: Int
			get() = dispatch { o_StartingLineNumber(it) }

		/**
		 * Answer the [type][A_Type] of the `index`-th local constant.
		 *
		 * @param index
		 *   The one-based ordinal of the desired local constant.
		 * @return
		 *   The requested type.
		 */
		fun A_RawFunction.constantTypeAt(index: Int): A_Type =
			dispatch { o_ConstantTypeAt(it, index) }

		/**
		 * Set the countdown until reoptimization by the
		 * [Level&#32;Two&#32;translator][L2Generator].
		 *
		 * @param value
		 *   The countdown until reoptimization.
		 */
		fun A_RawFunction.countdownToReoptimize(value: Long) =
			dispatch { o_CountdownToReoptimize(it, value) }

		/**
		 * Atomically decrement the countdown to reoptimization by the
		 * [L2Generator]. If the count reaches zero (`0`), then lock this raw
		 * function, thereby blocking concurrent applications of [A_Function]s
		 * derived from this raw function, and then evaluate the argument in
		 * order to effect reoptimization.
		 *
		 * @param continuation
		 *   The action responsible for reoptimizing this function
		 *   implementation in the event that the countdown reaches zero (`0`).
		 */
		fun A_RawFunction.decrementCountdownToReoptimize(
			continuation: (Boolean) -> Unit
		) = dispatch { o_DecrementCountdownToReoptimize(it, continuation) }

		/**
		 * This raw function was found to be running in an interpreter during a
		 * periodic poll.  Decrease its countdown to reoptimization by the
		 * indicated amount, being careful not to drop below one (`1`).
		 */
		fun A_RawFunction.decreaseCountdownToReoptimizeFromPoll(delta: Long) =
			dispatch { o_DecreaseCountdownToReoptimizeFromPoll(it, delta) }

		/**
		 * For this raw function, compute the tuple of its declaration names
		 * (arguments, locals, constants).  This is useful for decompilation,
		 * for giving meaningful names to registers in L2 translations, and for
		 * presenting reified continuations.
		 *
		 * @return
		 *   The tuple of declaration names.
		 */
		val A_RawFunction.declarationNames: A_Tuple
			get() = dispatch { o_DeclarationNames(it) }

		/**
		 * Answer the tuple of line number deltas for this ram function.  Each
		 * entry encodes a signed offset in an unsigned entry.  There's an entry
		 * for each nybblecode (not for each nybble).  The encoding uses the
		 * absolute value of the delta from the previous instruction's line
		 * number, shifted left once, adding one for negatives.  This allows
		 * nybble tuples and byte tuples to be the usual representations for
		 * small functions.
		 *
		 * @return
		 *   The function type associated with this function implementation.
		 */
		val A_RawFunction.lineNumberEncodedDeltas: A_Tuple
			get() = dispatch { o_LineNumberEncodedDeltas(it) }

		/**
		 * Answer the `index`-th literal value of this [A_RawFunction].
		 *
		 * @param index
		 *   The one-based ordinal of the desired literal value.
		 * @return
		 *   The requested literal value.
		 */
		fun A_RawFunction.literalAt(index: Int): AvailObject =
			dispatch { o_LiteralAt(it, index) }

		/**
		 * Answer the [type][A_Type] of the `index`-th local variable.
		 *
		 * @param index
		 *   The one-based ordinal of the desired local variable.
		 * @return
		 *   The requested type.
		 */
		fun A_RawFunction.localTypeAt(index: Int): A_Type =
			dispatch { o_LocalTypeAt(it, index) }

		/**
		 * Answer the maximum depth of the stack needed by an [A_Continuation]
		 * representing the invocation of some [A_Function] that closes this
		 * [A_RawFunction].
		 *
		 * @return
		 *   The maximum stack depth for this function implementation.
		 */
		val A_RawFunction.maxStackDepth: Int
			get() = dispatch { o_MaxStackDepth(it) }

		/**
		 * Read or write the name of the [method][A_Method] associated with this
		 * raw function, or some other descriptive [A_String] if it's not a
		 * method body.
		 */
		var A_RawFunction.methodName: A_String
			get() = dispatch { o_MethodName(it) }
			set(methodName) = dispatch { o_SetMethodName(it, methodName) }

		/**
		 * Answer the [A_Module] that contains the
		 * [block&#32;phrase][BlockPhraseDescriptor] that defines this raw
		 * function.
		 *
		 * @return
		 *   The module, or [nil] for synthetic function implementations.
		 */
		val A_RawFunction.module: A_Module get() = dispatch { o_Module(it) }

		/**
		 * Answer the number of arguments expected by this raw function.
		 *
		 * @return
		 *   The arity of this raw function.
		 */
		@ReferencedInGeneratedCode
		fun A_RawFunction.numArgs(): Int = dispatch { o_NumArgs(it) }

		/**
		 * Answer the number of local constants specified by this
		 * [A_RawFunction].
		 *
		 * @return
		 *   The number of local constants of this function implementation.
		 */
		val A_RawFunction.numConstants: Int
			get() = dispatch { o_NumConstants(it) }

		/**
		 * Answer the number of literal values embedded into this
		 * [A_RawFunction].
		 *
		 * @return
		 *   The number of literal values of this function implementation.
		 */
		val A_RawFunction.numLiterals: Int
			get() = dispatch { o_NumLiterals(it) }

		/**
		 * Answer the number of local variables specified by this
		 * [A_RawFunction].
		 *
		 * @return
		 *   The number of local variables of this function implementation.
		 */
		val A_RawFunction.numLocals: Int
			get() = dispatch { o_NumLocals(it) }

		/**
		 * Answer how many nybbles are taken up by the nybblecodes of this raw
		 * function.
		 *
		 * @return
		 *   The [size][A_Tuple.tupleSize] of this raw function's [getNybbles].
		 */
		val A_RawFunction.numNybbles: Int
			get() = dispatch { o_NumNybbles(it) }

		/**
		 * Answer the number of outer variables specified by this
		 * [A_RawFunction].
		 *
		 * @return
		 *   The number of outer variables of this function implementation.
		 */
		val A_RawFunction.numOuters: Int
			get() = dispatch { o_NumOuters(it) }

		/**
		 * Answer the number of slots to reserve in a [A_Continuation] based on
		 * this raw function. This is the arity, plus number of local variables
		 * and constants, plus number of stack slots.
		 *
		 * @return
		 *   The number of continuation slots to reserve for executing this raw
		 *   function.
		 */
		val A_RawFunction.numSlots: Int
			get() = dispatch { o_NumSlots(it) }

		/**
		 * Answer the [tuple][A_Tuple] of nybblecodes that implements this raw
		 * function.
		 *
		 * @return
		 *   The instruction tuple for this function implementation.
		 */
		val A_RawFunction.nybbles: A_Tuple
			get() = dispatch { o_Nybbles(it) }

		/**
		 * Answer the [block&#32;phrase][BlockPhraseDescriptor] from which this
		 * raw function was constructed.  Answer [nil] if this information is
		 * not available.
		 *
		 * @return
		 *   The phrase or nil from which this raw function was created.
		 */
		val A_RawFunction.originatingPhrase: A_Phrase
			get() = dispatch { o_OriginatingPhrase(it) }

		/**
		 * Answer either
		 *   1. the [block&#32;phrase][BlockPhraseDescriptor] from which this
		 *      raw function was constructed,
		 *   2. the index ([A_Number]) that can fetch and reconstruct the phrase
		 *      from the repository, or
		 *   3. [nil] if this information is not available for this raw
		 *      function.
		 *
		 * @return
		 *   The phrase or nil from which this raw function was created.
		 */
		val A_RawFunction.originatingPhraseOrIndex: AvailObject
			get() = dispatch { o_OriginatingPhraseOrIndex(it) }

		/**
		 * Answer the [type][A_Type] of the `index`-th outer variable.
		 *
		 * @param index
		 *   The one-based ordinal of the desired outer variable.
		 * @return
		 *   The requested type.
		 */
		fun A_RawFunction.outerTypeAt(index: Int): A_Type =
			dispatch { o_OuterTypeAt(it, index) }

		/**
		 * Answer an [A_String] containing the concatenated names of the
		 * originating block's declarations.
		 */
		val A_RawFunction.packedDeclarationNames: A_String
			get() = dispatch { o_PackedDeclarationNames(it) }

		/**
		 * Answer a [Statistic] for recording returns from this raw function.
		 *
		 * @return
		 *   The statistic.
		 */
		val A_RawFunction.returnerCheckStat: Statistic
			get() = dispatch { o_ReturnerCheckStat(it) }

		/**
		 * Answer a [Statistic] for recording returns into this raw function.
		 *
		 * @return
		 *   The statistic.
		 */
		val A_RawFunction.returneeCheckStat: Statistic
			get() = dispatch { o_ReturneeCheckStat(it) }

		/**
		 * Answer the type that this raw function will produce if there is no
		 * primitive, or if the primitive fails and the nybblecodes run.
		 */
		val A_RawFunction.returnTypeIfPrimitiveFails: A_Type
			get() = dispatch { o_ReturnTypeIfPrimitiveFails(it) }

		/**
		 * Update the raw function's originating phrase, or record an external
		 * key ([A_Number]) that will allow it to be recovered from a repository
		 * on demand.
		 */
		fun A_RawFunction.setOriginatingPhraseOrIndex(
			phraseOrIndex: AvailObject
		) = dispatch { o_SetOriginatingPhraseOrIndex(it, phraseOrIndex) }

		/**
		 * Set the [chunk][L2Chunk] that implements this [A_RawFunction], and
		 * the countdown to reoptimization by the [L2Generator].
		 *
		 * @param chunk
		 *   The chunk to invoke whenever the [Interpreter] starts execution of
		 *   this function implementation}.
		 * @param countdown
		 *   The countdown to reoptimization by the Level Two translator.
		 */
		fun A_RawFunction.setStartingChunkAndReoptimizationCountdown(
			chunk: L2Chunk,
			countdown: Long
		) = dispatch {
			o_SetStartingChunkAndReoptimizationCountdown(it, chunk, countdown)
		}

		/**
		 * Answer the [L2Chunk] that the [interpreter][Interpreter] will run to
		 * simulate execution of this [A_RawFunction].
		 *
		 * @return
		 *   The backing chunk for this function implementation. This will be
		 *   the special [L2Chunk.unoptimizedChunk] prior to conversion by the
		 *   [L2Generator].
		 */
		val A_RawFunction.startingChunk: L2Chunk
			get() = dispatch { o_StartingChunk(it) }

		/**
		 * Atomically increment the total number of invocations of [A_Function]s
		 * based on this [A_RawFunction].
		 */
		fun A_RawFunction.tallyInvocation() = dispatch { o_TallyInvocation(it) }

		/**
		 * Answer the total number of invocations of this raw function.
		 *
		 * @return
		 *   The total number of invocations of this function implementation.
		 */
		fun A_RawFunction.totalInvocations(): Long =
			dispatch { o_TotalInvocations(it) }

		/** The [CheckedMethod] for [functionType].  */
		val functionTypeMethod: CheckedMethod = instanceMethod(
			A_RawFunction::class.java,
			A_RawFunction::functionType.name,
			A_Type::class.java)
	}
}
