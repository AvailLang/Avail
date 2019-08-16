/*
 * AvailPushLabel.java
 * Copyright © 1993-2018, The Avail Foundation, LLC.
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

package com.avail.compiler.instruction;

import com.avail.compiler.AvailCodeGenerator;
import com.avail.descriptor.A_Token;
import com.avail.descriptor.A_Tuple;
import com.avail.descriptor.ContinuationDescriptor;
import com.avail.descriptor.FunctionDescriptor;
import com.avail.interpreter.levelOne.L1Operation;
import com.avail.interpreter.primitive.controlflow.P_ExitContinuationWithResult;
import com.avail.interpreter.primitive.controlflow.P_RestartContinuation;
import com.avail.interpreter.primitive.controlflow.P_RestartContinuationWithArguments;

import javax.annotation.Nullable;
import java.io.ByteArrayOutputStream;
import java.util.List;

/**
 * I represent the use of a label.  When a label is <em>used</em>, it causes the
 * current {@linkplain ContinuationDescriptor continuation} to be copied.  The
 * copy is then reset to the state that existed when the current {@linkplain
 * FunctionDescriptor function} started running, resetting the program counter,
 * stack pointer, and stack slots, and creating new local variables.
 *
 * <p>
 * The new continuation can subsequently be {@linkplain
 * P_RestartContinuation restarted}, {@linkplain
 * P_RestartContinuationWithArguments restarted with new arguments}, or
 * {@linkplain P_ExitContinuationWithResult exited}.
 * </p>
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public class AvailPushLabel extends AvailInstruction
{
	/**
	 * Construct an instruction.  Capture the tokens that contributed to it.
	 *
	 * @param relevantTokens
	 *        The {@link A_Tuple} of {@link A_Token}s that are associated with
	 *        this instruction.
	 */
	public AvailPushLabel (final A_Tuple relevantTokens)
	{
		super(relevantTokens);
	}

	@Override
	public void writeNybblesOn (final ByteArrayOutputStream aStream)
	{
		L1Operation.L1Ext_doPushLabel.writeTo(aStream);
	}

	/**
	 * <p>Push a label, which is a {@linkplain ContinuationDescriptor
	 * continuation}.  Since the label can be restarted (which constructs new
	 * locals while reusing the arguments), or exited (which has no static
	 * effect on optimizations), I only have an effect on arguments and outer
	 * variables.  Scan all arguments and outer variables and ensure the most
	 * recent pushes are reset so that isLastAccess is false.</p>
	 */
	@Override
	public void fixUsageFlags (
		final List<AvailVariableAccessNote> localData,
		final List<AvailVariableAccessNote> outerData,
		final AvailCodeGenerator codeGenerator)
	{
		for (int index = 0; index < codeGenerator.numArgs(); index++)
		{
			@Nullable AvailVariableAccessNote note = localData.get(index);
			if (note == null)
			{
				note = new AvailVariableAccessNote();
				localData.set(index, note);
			}
			// If any argument was pushed before this pushLabel, set its
			// isLastAccess to false, as a restart will need to have these
			// arguments intact.
			final @Nullable AvailPushVariable previousPush = note.previousPush();
			if (previousPush != null)
			{
				previousPush.isLastAccess(false);
			}
		}

		for (final @Nullable AvailVariableAccessNote outerNote : outerData)
		{
			if (outerNote != null)
			{
				final @Nullable AvailPushVariable previousPush =
					outerNote.previousPush();
				if (previousPush != null)
				{
					// Make sure the previous outer push is not considered the
					// last access.
					previousPush.isLastAccess(false);
				}
				final @Nullable AvailGetVariable previousGet =
					outerNote.previousGet();
				if (previousGet != null)
				{
					// Make sure the previous outer get is not considered the
					// last use of the value.
					previousGet.canClear(false);
				}
			}
		}
	}
}
