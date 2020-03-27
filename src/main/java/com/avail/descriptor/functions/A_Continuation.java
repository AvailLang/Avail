/*
 * A_Continuation.java
 * Copyright © 1993-2020, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice, this
 *     list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice, this
 *     list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
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

package com.avail.descriptor.functions;

import com.avail.descriptor.A_BasicObject;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.NilDescriptor;
import com.avail.descriptor.functions.ContinuationDescriptor.IntegerSlots;
import com.avail.descriptor.functions.ContinuationDescriptor.ObjectSlots;
import com.avail.descriptor.methods.A_SemanticRestriction;
import com.avail.interpreter.levelTwo.L2Chunk;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.optimizer.jvm.ReferencedInGeneratedCode;

/**
 * {@code A_Continuation} is an interface that specifies the operations specific
 * to {@linkplain ContinuationDescriptor continuations} in Avail.  It's a
 * sub-interface of {@link A_BasicObject}, the interface that defines the
 * behavior that all AvailObjects are required to support.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public interface A_Continuation
extends A_BasicObject
{
	/**
	 * Answer the continuation frame slot at the given index.  The frame slots
	 * are numbered starting at 1, and consist of the arguments, primitive
	 * failure variable (if defined), locals, and then an operand stack that
	 * grows from the top down.
	 *
	 * @param index The one-based index into this frame data.
	 * @return The continuation's slot at the specified index.
	 */
	AvailObject argOrLocalOrStackAt (int index);

	/**
	 * Update the continuation frame slot at the given index.  The continuation
	 * must be mutable.  Frame slots are numbered starting at 1, and consist of
	 * the arguments, primitive failure variable (if defined), locals, and then
	 * an operand stack that grows from the top down.
	 *
	 * @param index The one-based index into this frame data.
	 * @param value The value to write at that index.
	 */
	void argOrLocalOrStackAtPut (int index, AvailObject value);

	/**
	 * The {@linkplain ContinuationDescriptor continuation} to which control
	 * will pass when this continuation returns.  May be {@link
	 * NilDescriptor#nil nil}, indicating this is the outermost stack frame of
	 * its fiber and will produce a value from the fiber itself.
	 *
	 * @return The calling continuation or nil.
	 */
	@ReferencedInGeneratedCode
	A_Continuation caller ();

	/**
	 * Answer the {@linkplain FunctionDescriptor function} for which this
	 * {@linkplain ContinuationDescriptor continuation} represents an
	 * evaluation.
	 *
	 * <p>Also defined in {@link A_SemanticRestriction}.</p>
	 *
	 * @return The function.
	 */
	@ReferencedInGeneratedCode
	A_Function function ();

	/**
	 * Answer the level one program counter.  This is a one-based subscript that
	 * indicates the next instruction that will execute in this continuation's
	 * function's level one code.
	 *
	 * @return The continuation's level one program counter.
	 */
	int pc ();

	/**
	 * Answer the current depth of the argument stack within this continuation.
	 * This is a one-based index into the continuation's {@link
	 * ObjectSlots#FRAME_AT_ frame area}.  The stack
	 * pointer indexes the most recently pushed value.  The stack grows
	 * downwards, and the empty stack is indicated by a pointer just beyond the
	 * {@link ObjectSlots#FRAME_AT_ frame data}.
	 *
	 * @return The current stack pointer within this continuation.
	 */
	int stackp ();

	/**
	 * Set both this continuation's {@link L2Chunk} and the corresponding
	 * offset into its {@link L2Chunk#instructions}.
	 *
	 * @param chunk
	 *        This continuation's new {@code L2Chunk}.
	 * @param offset
	 *        Where to resume executing the {@code L2Chunk}'s instructions.
	 */
	void levelTwoChunkOffset (L2Chunk chunk, int offset);

	/**
	 * Retrieve the stack element with the given offset.  Do not adjust the
	 * mutability of the returned value.
	 *
	 * @param slotIndex Which stack element to retrieve.
	 * @return The value that was on the stack.
	 */
	AvailObject stackAt (int slotIndex);

	/**
	 * If this continuation is already mutable just answer it; otherwise answer
	 * a mutable copy of it.
	 *
	 * @return A mutable continuation like the receiver.
	 */
	A_Continuation ensureMutable ();

	/**
	 * Answer the current {@link L2Chunk} to run when resuming this {@linkplain
	 * ContinuationDescriptor continuation}.  Always check that the chunk is
	 * still {@linkplain L2Chunk#isValid() valid}, otherwise the {@linkplain
	 * L2Chunk#unoptimizedChunk} should be resumed instead.
	 *
	 * @return The L2Chunk to resume if the chunk is still valid.
	 */
	@ReferencedInGeneratedCode
	L2Chunk levelTwoChunk ();

	/**
	 * The offset within the {@link L2Chunk} at which to resume level two
	 * execution when this continuation is to be resumed.  Note that the chunk
	 * might have become invalidated, so check {@link L2Chunk#isValid()}) before
	 * attempting to resume it.
	 *
	 * @return The index of the {@link L2Instruction} at which to resume
	 *         level two execution if the {@code L2Chunk} is still valid.
	 */
	@ReferencedInGeneratedCode
	int levelTwoOffset ();

	/**
	 * Also defined in {@link A_RawFunction}.  The total number of "frame"
	 * slots in the receiver.  These slots are used to hold the arguments, local
	 * variables and constants, and the local operand stack on which the level
	 * one instruction set depends.
	 *
	 * @return The number of arguments, locals, and stack slots in the receiver.
	 */
	int numSlots ();

	/**
	 * Set both the {@link IntegerSlots#PROGRAM_COUNTER}
	 * and the {@link IntegerSlots#STACK_POINTER} of this
	 * continuation, which must be mutable.
	 *
	 * @param pc
	 *        The offset of the next level one instruction (within my function's
	 *        nybblecodes) at which to continue running.
	 * @param stackp
	 *        The current stack pointer within or just beyond my stack area.
	 */
	void adjustPcAndStackp (int pc, int stackp);

	/**
	 * Create a copy of the receiver if it's not already mutable, then clobber
	 * the {@link ObjectSlots#CALLER} slot with the
	 * passed value.
	 *
	 * @param newCaller The calling continuation.
	 * @return The original or mutable copy of the original, modified to have
	 *         the specified caller.
	 */
	A_Continuation replacingCaller (A_Continuation newCaller);

	/**
	 * Answer the line number associated with this continuation's current pc.
	 *
	 * @return The line number within the defining module.
	 */
	int currentLineNumber ();

	/**
	 * Answer the {@link ContinuationRegisterDumpDescriptor} object that was
	 * secretly stashed inside this continuation for an {@link L2Chunk}'s use.
	 *
	 * @return An {@link AvailObject} with a
	 *         {@link ContinuationRegisterDumpDescriptor} descriptor.
	 */
	AvailObject registerDump ();
}
