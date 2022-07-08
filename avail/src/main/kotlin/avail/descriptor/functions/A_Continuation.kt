/*
 * A_Continuation.kt
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
import avail.descriptor.methods.A_SemanticRestriction
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.A_BasicObject.Companion.dispatch
import avail.descriptor.representation.AvailObject
import avail.descriptor.representation.NilDescriptor.Companion.nil
import avail.interpreter.levelTwo.L2Chunk
import avail.interpreter.levelTwo.L2Instruction
import avail.optimizer.jvm.ReferencedInGeneratedCode

/**
 * [A_Continuation] is an interface that specifies the operations specific to
 * [continuations][ContinuationDescriptor] in Avail.  It's a sub-interface of
 * [A_BasicObject], the interface that defines the behavior that all
 * AvailObjects are required to support.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
interface A_Continuation : A_BasicObject
{
	companion object
	{
		/**
		 * Answer the continuation frame slot at the given index.  The frame
		 * slots are numbered starting at 1, and consist of the arguments,
		 * primitive failure variable (if defined), locals, and then an operand
		 * stack that grows from the top down.
		 *
		 * @param index
		 *   The one-based index into this frame data.
		 * @return
		 *   The continuation's slot at the specified index.
		 */
		fun A_Continuation.frameAt(index: Int): AvailObject =
			dispatch { o_FrameAt(it, index) }

		/**
		 * Update the continuation frame slot at the given index.  The
		 * continuation must be mutable.  Frame slots are numbered starting at
		 * 1, and consist of the arguments, primitive failure variable (if
		 * defined), locals, and then an operand stack that grows from the top
		 * down.
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
		fun A_Continuation.frameAtPut(
			index: Int,
			value: AvailObject
		): AvailObject = dispatch { o_FrameAtPut(it, index, value) }

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
		fun A_Continuation.caller(): A_Continuation = dispatch { o_Caller(it) }

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
		fun A_Continuation.function(): A_Function = dispatch { o_Function(it) }

		/**
		 * Answer the level one program counter.  This is a one-based subscript
		 * that indicates the next instruction that will execute in this
		 * continuation's function's level one code.
		 *
		 * @return
		 *   The continuation's level one program counter.
		 */
		fun A_Continuation.pc(): Int = dispatch { o_Pc(it) }

		/**
		 * Answer the current depth of the argument stack within this
		 * continuation. This is a one-based index into the continuation's
		 * [frame][frameAt].  The stack pointer indexes the most recently pushed
		 * value.  The stack grows downwards, and the empty stack is indicated
		 * by a pointer just beyond the frame data.
		 *
		 * @return
		 *   The current stack pointer within this continuation.
		 */
		fun A_Continuation.stackp(): Int = dispatch { o_Stackp(it)}

		/**
		 * Retrieve the stack element with the given offset.  Do not adjust the
		 * mutability of the returned value.
		 *
		 * @param slotIndex
		 *   Which stack element to retrieve.
		 * @return
		 *   The value that was on the stack.
		 */
		fun A_Continuation.stackAt(slotIndex: Int): AvailObject =
			dispatch { o_StackAt(it, slotIndex) }

		/**
		 * If this continuation is already mutable just answer it; otherwise
		 * answer a mutable copy of it.
		 *
		 * @return
		 *   A mutable continuation like the receiver.
		 */
		fun A_Continuation.ensureMutable(): A_Continuation =
			dispatch { o_EnsureMutable(it) }

		/**
		 * Answer a continuation like this, but deoptimized for debugging.  If
		 * the continuation is already deoptimized, just return it.  This must
		 * be performed inside a [safe point][AvailRuntime.whenSafePointDo].
		 *
		 * @return
		 *   A mutable continuation like the receiver.
		 */
		fun A_Continuation.deoptimizedForDebugger(): A_Continuation =
			dispatch { o_DeoptimizedForDebugger(it) }

		/**
		 * Answer the current [L2Chunk] to run when resuming this continuation.
		 * Always check that the chunk is still [valid][L2Chunk.isValid],
		 * otherwise the [L2Chunk.unoptimizedChunk] should be resumed instead.
		 *
		 * @return
		 *   The L2Chunk to resume if the chunk is still valid.
		 */
		@ReferencedInGeneratedCode
		fun A_Continuation.levelTwoChunk(): L2Chunk =
			dispatch { o_LevelTwoChunk(it) }

		/**
		 * The offset within the [L2Chunk] at which to resume level two
		 * execution when this continuation is to be resumed.  Note that the
		 * chunk might have become invalidated, so check [L2Chunk.isValid])
		 * before attempting to resume it.
		 *
		 * @return
		 *   The index of the [L2Instruction] at which to resume level two
		 *   execution if the [L2Chunk] is still valid.
		 */
		@ReferencedInGeneratedCode
		fun A_Continuation.levelTwoOffset(): Int =
			dispatch { o_LevelTwoOffset(it) }

		/**
		 * Also defined in [A_RawFunction].  The total number of "frame" slots
		 * in the receiver.  These slots are used to hold the arguments, local
		 * variables and constants, and the local operand stack on which the
		 * level one instruction set depends.
		 *
		 * @return
		 *   The number of arguments, locals, and stack slots in the receiver.
		 */
		fun A_Continuation.numSlots(): Int = dispatch { o_NumSlots(it) }

		/**
		 * Set both the [pc] and the [stack&#32;pointer][stackp] of this
		 * continuation, which must be mutable.
		 *
		 * @param pc
		 *   The offset of the next level one instruction (within my function's
		 *   nybblecodes) at which to continue running.
		 * @param stackp
		 *   The current stack pointer within or just beyond my stack area.
		 */
		fun A_Continuation.adjustPcAndStackp(pc: Int, stackp: Int) =
			dispatch { o_AdjustPcAndStackp(it, pc, stackp) }

		/**
		 * Create a copy of the receiver if it's not already mutable, then
		 * clobber the [caller] slot with the passed value.
		 *
		 * @param newCaller
		 *   The calling continuation.
		 * @return
		 *   The original or mutable copy of the original, modified to have the
		 *   specified caller.
		 */
		fun A_Continuation.replacingCaller(
			newCaller: A_Continuation
		): A_Continuation = dispatch { o_ReplacingCaller(it, newCaller) }

		/**
		 * Answer the line number associated with this continuation's current
		 * [pc].
		 *
		 * @return The line number within the defining module.
		 */
		fun A_Continuation.currentLineNumber(topFrame: Boolean): Int =
			dispatch { o_CurrentLineNumber(it, topFrame) }

		/**
		 * Answer the [ContinuationRegisterDumpDescriptor] object that was
		 * secretly stashed inside this continuation for an [L2Chunk]'s use.
		 *
		 * @return
		 *   A register dump object with a [ContinuationRegisterDumpDescriptor]
		 *   descriptor.
		 */
		fun A_Continuation.registerDump(): AvailObject =
			dispatch { o_RegisterDump(it) }

		/**
		 * Determine which nybblecode index is "current" for this continuation.
		 * If this is not the top frame, use the instruction previous to the
		 * current [pc].
 		 */
		fun A_Continuation.highlightPc(isTopFrame: Boolean): Int =
			dispatch { o_HighlightPc(it, isTopFrame) }
	}
}
