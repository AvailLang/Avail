/*
 * A_Continuation.kt
 * Copyright © 1993-2020, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice, this
 *    list of conditions and the following disclaimer in the documentation
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

import com.avail.descriptor.NilDescriptor
import com.avail.descriptor.NilDescriptor.nil
import com.avail.descriptor.methods.A_SemanticRestriction
import com.avail.descriptor.representation.A_BasicObject
import com.avail.descriptor.representation.AvailObject
import com.avail.interpreter.levelTwo.L2Chunk
import com.avail.interpreter.levelTwo.L2Instruction
import com.avail.optimizer.jvm.JVMChunk
import com.avail.optimizer.jvm.ReferencedInGeneratedCode

/**
 * `A_Continuation` is an interface that specifies the operations specific to
 * [continuations][ContinuationDescriptor] in Avail.  It's a sub-interface of
 * [A_BasicObject], the interface that defines the behavior that all
 * AvailObjects are required to support.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
interface A_Continuation : A_BasicObject {
	/**
	 * Answer the continuation frame slot at the given index.  The frame slots
	 * are numbered starting at 1, and consist of the arguments, primitive
	 * failure variable (if defined), locals, and then an operand stack that
	 * grows from the top down.
	 *
	 * @param index
	 *   The one-based index into this frame data.
	 * @return
	 *   The continuation's slot at the specified index.
	 */
	fun frameAt(index: Int): AvailObject

	/**
	 * Update the continuation frame slot at the given index.  The continuation
	 * must be mutable.  Frame slots are numbered starting at 1, and consist of
	 * the arguments, primitive failure variable (if defined), locals, and then
	 * an operand stack that grows from the top down.
	 *
	 * Answer the receiver to make generated chains more compact.
	 *
	 * @param index
	 *   The one-based index into this frame data.
	 * @param value
	 *   The value to write at that index.
	 * @return
	 *   The receiver.
	 */
	fun frameAtPut(index: Int, value: AvailObject): AvailObject

	/**
	 * The [continuation][ContinuationDescriptor] to which control will pass
	 * when this continuation returns.  May be [nil], indicating this is the
	 * outermost stack frame of its fiber and will produce a value from the
	 * fiber itself.
	 *
	 * @return
	 *   The calling continuation or nil.
	 */
	@ReferencedInGeneratedCode
	fun caller(): A_Continuation

	/**
	 * Answer the [function][FunctionDescriptor] for which this
	 * [continuation][ContinuationDescriptor] represents an evaluation.
	 *
	 * Also defined in [A_SemanticRestriction].
	 *
	 * @return
	 * The function.
	 */
	@ReferencedInGeneratedCode
	fun function(): A_Function

	/**
	 * Answer the level one program counter.  This is a one-based subscript that
	 * indicates the next instruction that will execute in this continuation's
	 * function's level one code.
	 *
	 * @return
	 *   The continuation's level one program counter.
	 */
	fun pc(): Int

	/**
	 * Answer the current depth of the argument stack within this continuation.
	 * This is a one-based index into the continuation's [frame][frameAt].  The
	 * stack pointer indexes the most recently pushed value.  The stack grows
	 * downwards, and the empty stack is indicated by a pointer just beyond the
	 * frame data.
	 *
	 * @return
	 *   The current stack pointer within this continuation.
	 */
	fun stackp(): Int

	/**
	 * Set both this continuation's [L2Chunk] and the corresponding offset into
	 * its [L2Chunk.instructions].
	 *
	 * @param chunk
	 *   This continuation's new [L2Chunk].
	 * @param offset
	 *   Where to resume executing the [L2Chunk]'s instructions.  The [JVMChunk]
	 *   translation interprets this offset via a generated switch statement.
	 */
	fun levelTwoChunkOffset(chunk: L2Chunk, offset: Int)

	/**
	 * Retrieve the stack element with the given offset.  Do not adjust the
	 * mutability of the returned value.
	 *
	 * @param slotIndex
	 *   Which stack element to retrieve.
	 * @return
	 *   The value that was on the stack.
	 */
	fun stackAt(slotIndex: Int): AvailObject

	/**
	 * If this continuation is already mutable just answer it; otherwise answer
	 * a mutable copy of it.
	 *
	 * @return
	 *   A mutable continuation like the receiver.
	 */
	fun ensureMutable(): A_Continuation

	/**
	 * Answer the current [L2Chunk] to run when resuming this continuation.
	 * Always check that the chunk is still [valid][L2Chunk.isValid], otherwise
	 * the [L2Chunk.unoptimizedChunk] should be resumed instead.
	 *
	 * @return
	 *   The L2Chunk to resume if the chunk is still valid.
	 */
	@ReferencedInGeneratedCode
	fun levelTwoChunk(): L2Chunk

	/**
	 * The offset within the [L2Chunk] at which to resume level two execution
	 * when this continuation is to be resumed.  Note that the chunk might have
	 * become invalidated, so check [L2Chunk.isValid]) before attempting to
	 * resume it.
	 *
	 * @return
	 *   The index of the [L2Instruction] at which to resume level two execution
	 *   if the [L2Chunk] is still valid.
	 */
	@ReferencedInGeneratedCode
	fun levelTwoOffset(): Int

	/**
	 * Also defined in [A_RawFunction].  The total number of "frame" slots in
	 * the receiver.  These slots are used to hold the arguments, local
	 * variables and constants, and the local operand stack on which the level
	 * one instruction set depends.
	 *
	 * @return
	 *   The number of arguments, locals, and stack slots in the receiver.
	 */
	fun numSlots(): Int

	/**
	 * Set both the [pc] and the [stack&#32;pointer]][stackp] of this
	 * continuation, which must be mutable.
	 *
	 * @param pc
	 *   The offset of the next level one instruction (within my function's
	 *   nybblecodes) at which to continue running.
	 * @param stackp
	 *   The current stack pointer within or just beyond my stack area.
	 */
	fun adjustPcAndStackp(pc: Int, stackp: Int)

	/**
	 * Create a copy of the receiver if it's not already mutable, then clobber
	 * the [caller] slot with the passed value.
	 *
	 * @param newCaller
	 *   The calling continuation.
	 * @return
	 *   The original or mutable copy of the original, modified to have the
	 *   specified caller.
	 */
	fun replacingCaller(newCaller: A_Continuation): A_Continuation

	/**
	 * Answer the line number associated with this continuation's current [pc].
	 *
	 * @return The line number within the defining module.
	 */
	fun currentLineNumber(): Int

	/**
	 * Answer the [ContinuationRegisterDumpDescriptor] object that was secretly
	 * stashed inside this continuation for an [L2Chunk]'s use.
	 *
	 * @return
	 *   A register dump object with a [ContinuationRegisterDumpDescriptor]
	 *   descriptor.
	 */
	fun registerDump(): AvailObject
}