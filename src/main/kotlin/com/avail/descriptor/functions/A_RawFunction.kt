/*
 * A_RawFunction.kt
 * Copyright © 1993-2020, The Avail Foundation, LLC.
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
import com.avail.descriptor.methods.A_Definition
import com.avail.descriptor.methods.A_Method
import com.avail.descriptor.module.A_Module
import com.avail.descriptor.phrases.A_Phrase
import com.avail.descriptor.phrases.BlockPhraseDescriptor
import com.avail.descriptor.representation.A_BasicObject
import com.avail.descriptor.representation.AvailObject
import com.avail.descriptor.representation.NilDescriptor.Companion.nil
import com.avail.descriptor.tuples.A_String
import com.avail.descriptor.tuples.A_Tuple
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
	 * Answer the [type][A_Type] of the `index`-th local constant.
	 *
	 * @param index
	 *   The one-based ordinal of the desired local constant.
	 * @return
	 *   The requested type.
	 */
	fun constantTypeAt(index: Int): A_Type

	/**
	 * Set the countdown until reoptimization by the
	 * [Level&#32;Two&#32;translator][L2Generator].
	 *
	 * @param value
	 *   The countdown until reoptimization.
	 */
	fun countdownToReoptimize(value: Int)

	/**
	 * Atomically decrement the countdown to reoptimization by the
	 * [L2Generator]. If the count reaches zero (`0`), then lock this raw
	 * function, thereby blocking concurrent applications of [A_Function]s
	 * derived from this raw function, and then evaluate the argument in order
	 * to effect reoptimization.
	 *
	 * @param continuation
	 *   The action responsible for reoptimizing this function implementation in
	 *   the event that the countdown reaches zero (`0`).
	 */
	fun decrementCountdownToReoptimize(
		continuation: (Boolean) -> Unit)

	/**
	 * Answer the [function&#32;type][FunctionTypeDescriptor] associated with
	 * this raw function's closures.
	 *
	 * @return
	 *   The function type associated with this function implementation.
	 */
	@ReferencedInGeneratedCode
	fun functionType(): A_Type

	/**
	 * Answer the tuple of line number deltas for this ram function.  Each entry
	 * encodes a signed offset in an unsigned entry.  There's an entry for each
	 * nybblecode (not for each nybble).  The encoding uses the absolute value
	 * of the delta from the previous instruction's line number, shifted left
	 * once, adding one for negatives.  This allows nybble tuples and byte
	 * tuples to be the usual representations for small functions.
	 *
	 * @return
	 *   The function type associated with this function implementation.
	 */
	fun lineNumberEncodedDeltas(): A_Tuple

	/**
	 * Answer the `index`-th literal value of this [A_RawFunction].
	 *
	 * @param index
	 *   The one-based ordinal of the desired literal value.
	 * @return
	 *   The requested literal value.
	 */
	fun literalAt(index: Int): AvailObject

	/**
	 * Answer the [type][A_Type] of the `index`-th local variable.
	 *
	 * @param index
	 *   The one-based ordinal of the desired local variable.
	 * @return
	 *   The requested type.
	 */
	fun localTypeAt(index: Int): A_Type

	/**
	 * Answer the maximum depth of the stack needed by an [A_Continuation]
	 * representing the invocation of some [A_Function] that closes this
	 * [A_RawFunction].
	 *
	 * @return
	 *   The maximum stack depth for this function implementation.
	 */
	fun maxStackDepth(): Int

	/**
	 * Answer the name of the [method][A_Method] associated with this raw
	 * function.
	 *
	 * @return
	 *   The method name associated with this function implementation, or a
	 *   [string][A_String] that indicates that the provenance of the function
	 *   implementation is not known.
	 * @see [setMethodName]
	 */
	fun methodName(): A_String

	/**
	 * Answer the [A_Module] that contains the
	 * [block&#32;phrase][BlockPhraseDescriptor] that defines this raw function.
	 *
	 * @return
	 *   The module, or [nil] for synthetic function implementations.
	 */
	fun module(): A_Module

	/**
	 * Answer the number of arguments expected by this raw function.
	 *
	 * @return
	 *   The arity of this raw function.
	 */
	@ReferencedInGeneratedCode
	fun numArgs(): Int

	/**
	 * Answer the number of slots to reserve in a [A_Continuation] based on this
	 * raw function. This is the arity, plus number of local variables and
	 * constants, plus number of stack slots.
	 *
	 * @return
	 *   The number of continuation slots to reserve for executing this raw
	 *   function.
	 */
	fun numSlots(): Int

	/**
	 * Answer the number of literal values embedded into this [A_RawFunction].
	 *
	 * @return
	 *   The number of literal values of this function implementation.
	 */
	fun numLiterals(): Int

	/**
	 * Answer the number of local variables specified by this [A_RawFunction].
	 *
	 * @return
	 *   The number of local variables of this function implementation.
	 */
	fun numLocals(): Int

	/**
	 * Answer the number of local constants specified by this [A_RawFunction].
	 *
	 * @return
	 *   The number of local constants of this function implementation.
	 */
	fun numConstants(): Int

	/**
	 * Answer how many nybbles are taken up by the nybblecodes of this raw
	 * function.
	 *
	 * @return
	 *   The [size][A_Tuple.tupleSize] of this raw function's [nybbles].
	 */
	fun numNybbles(): Int

	/**
	 * Answer the number of outer variables specified by this [A_RawFunction].
	 *
	 * @return
	 *   The number of outer variables of this function implementation.
	 */
	fun numOuters(): Int

	/**
	 * Answer the [tuple][A_Tuple] of nybblecodes that implements this raw
	 * function.
	 *
	 * @return
	 *   The instruction tuple for this function implementation.
	 */
	fun nybbles(): A_Tuple

	/**
	 * Answer the [block&#32;phrase][BlockPhraseDescriptor] from which this raw
	 * function was constructed.  Answer [nil] if this information is not
	 * available.
	 *
	 * @return
	 *   The phrase or nil from which this raw function was created.
	 */
	fun originatingPhrase(): A_Phrase

	/**
	 * Answer the [type][A_Type] of the `index`-th outer variable.
	 *
	 * @param index
	 *   The one-based ordinal of the desired outer variable.
	 * @return
	 *   The requested type.
	 */
	fun outerTypeAt(index: Int): A_Type

	/**
	 * Answer this raw function's [Primitive] or `null`.
	 *
	 * @return
	 *   The [Primitive], or null if this raw function is not primitive.
	 */
	@ReferencedInGeneratedCode
	fun primitive(): Primitive?

	/**
	 * Answer the [primitive][Primitive] number associated with this `function
	 * implementation`. The [Interpreter] will execute the indicated primitive
	 * before falling back on the Avail code (in the event of failure only).
	 *
	 * @return
	 *   The primitive number, or zero (`0`) if the function implementation is
	 *   not linked to a primitive.
	 */
	fun primitiveNumber(): Int

	/**
	 * Answer a [Statistic] for recording returns from this raw function.
	 *
	 * @return
	 *   The statistic.
	 */
	fun returnerCheckStat(): Statistic

	/**
	 * Answer a [Statistic] for recording returns into this raw function.
	 *
	 * @return
	 *   The statistic.
	 */
	fun returneeCheckStat(): Statistic

	/**
	 * Specify that a [method][A_Method] with the given name includes a
	 * [definition][A_Definition] that (indirectly) includes this raw function.
	 *
	 * @param methodName
	 *   The method name to associate with this function implementation.
	 */
	fun setMethodName(methodName: A_String)

	/**
	 * Set the [chunk][L2Chunk] that implements this [A_RawFunction], and the
	 * countdown to reoptimization by the [L2Generator].
	 *
	 * @param chunk
	 *   The chunk to invoke whenever the [Interpreter] starts execution of this
	 *   function implementation}.
	 * @param countdown
	 *   The countdown to reoptimization by the Level Two translator.
	 */
	fun setStartingChunkAndReoptimizationCountdown(
		chunk: L2Chunk,
		countdown: Long)

	/**
	 * Helper method for transferring this object's longSlots into an
	 * [L1InstructionDecoder].  The receiver's descriptor must be a
	 * [CompiledCodeDescriptor].
	 *
	 * @param
	 *   instructionDecoder The [L1InstructionDecoder] to populate.
	 */
	fun setUpInstructionDecoder(
		instructionDecoder: L1InstructionDecoder)

	/**
	 * Answer the [L2Chunk] that the [interpreter][Interpreter] will run to
	 * simulate execution of this [A_RawFunction].
	 *
	 * @return
	 *   The backing chunk for this function implementation. This will be the
	 *   special [L2Chunk.unoptimizedChunk] prior to conversion by the
	 *   [L2Generator].
	 */
	fun startingChunk(): L2Chunk

	/**
	 * Answer the starting line number for the
	 * [block&#32;phrase][BlockPhraseDescriptor] that defines this raw function.
	 *
	 * @return
	 *   The starting line number, or zero (`0`) for synthetic function
	 *   implementations.
	 */
	fun startingLineNumber(): Int

	/**
	 * Atomically increment the total number of invocations of [A_Function]s
	 * based on this [A_RawFunction].
	 */
	fun tallyInvocation()

	/**
	 * Answer the total number of invocations of this raw function.
	 *
	 * @return
	 *   The total number of invocations of this function implementation.
	 */
	fun totalInvocations(): Long

	companion object {
		/** The [CheckedMethod] for [functionType].  */
		@JvmField
		val functionTypeMethod: CheckedMethod = instanceMethod(
			A_RawFunction::class.java,
			A_RawFunction::functionType.name,
			A_Type::class.java)
	}
}
