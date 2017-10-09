/**
 * L2_REENTER_L1_CHUNK_FROM_RESTART.java
 * Copyright © 1993-2017, The Avail Foundation, LLC.
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
package com.avail.interpreter.levelTwo.operation;

import com.avail.descriptor.A_Continuation;
import com.avail.descriptor.A_Function;
import com.avail.descriptor.A_RawFunction;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.DeclarationNodeDescriptor;
import com.avail.descriptor.DeclarationNodeDescriptor.DeclarationKind;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.levelOne.L1Operation;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2Operation;
import com.avail.interpreter.primitive.controlflow
	.P_RestartContinuationWithArguments;

import static com.avail.descriptor.NilDescriptor.nil;
import static com.avail.descriptor.VariableDescriptor.newVariableWithOuterType;
import static com.avail.interpreter.Interpreter.debugL1;
import static com.avail.utility.Nulls.stripNull;

/**
 * This is the first instruction of the L1 interpreter's on-ramp for restarting
 * a continuation created by {@link L1Operation#L1Ext_doPushLabel} (i.e., a
 * {@link DeclarationNodeDescriptor} of kind {@link DeclarationKind#LABEL}.
 * Such a continuation has pc=0, which is otherwise invalid.  If the
 * continuation is being restarted by {@link
 * P_RestartContinuationWithArguments}, it will have already had its arguments
 * replaced.
 */
public class L2_REENTER_L1_CHUNK_FROM_RESTART extends L2Operation
{
	/**
	 * Initialize the sole instance.
	 */
	public static final L2Operation instance =
		new L2_REENTER_L1_CHUNK_FROM_RESTART().init();

	static final int[] emptyIntArray = new int[0];

	@Override
	public void step (
		final L2Instruction instruction,
		final Interpreter interpreter)
	{
		final A_Continuation continuation = interpreter.reifiedContinuation;
		// This should be guaranteed by the primitives (which should fail if
		// there's a problem).
		assert continuation.pc() == 0 : "Not a label continuation";

		interpreter.reifiedContinuation = continuation.caller();
		if (debugL1)
		{
			System.out.println("Restart label in L1");
		}

		// This should create the same layout as L2_PREPARE_NEW_FRAME_FOR_L1.
		final A_Function function = stripNull(interpreter.function);
		assert function == continuation.function();
		final A_RawFunction code = function.code();
		final int numArgs = code.numArgs();
		final int numLocals = code.numLocals();
		final int numSlots = code.numArgsAndLocalsAndStack();
		assert continuation.stackp() == numSlots + 1;
		// The L2 instructions that implement L1 don't reserve room for any
		// fixed registers, but they assume [0] is unused (to simplify
		// indexing).  I.e., pointers[1] <-> continuation.stackAt(1).
		interpreter.pointers = new AvailObject[numSlots + 1];
		interpreter.integers = emptyIntArray;
		int slotIndex = 1;
		// Populate the arguments from argsBuffer.
		while (slotIndex <= numArgs)
		{
			final AvailObject arg = continuation.argOrLocalOrStackAt(slotIndex);
			assert !arg.equalsNil();
			interpreter.pointerAtPut(slotIndex++, arg);
		}
		// Create actual local variables.
		for (int i = 1; i <= numLocals; i++)
		{
			interpreter.pointerAtPut(
				slotIndex++, newVariableWithOuterType(code.localTypeAt(i)));
		}
		// Fill the rest with nils.
		while (slotIndex <= numSlots)
		{
			interpreter.pointerAtPut(slotIndex++, nil);
		}
		interpreter.levelOneStepper.pc = 1;
		interpreter.levelOneStepper.stackp = numSlots + 1;
		assert code.primitive() == null;
	}

	@Override
	public boolean hasSideEffect ()
	{
		return true;
	}
}
