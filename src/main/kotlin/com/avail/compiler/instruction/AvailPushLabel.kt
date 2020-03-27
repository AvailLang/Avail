/*
 * AvailPushLabel.kt
 * Copyright © 1993-2019, The Avail Foundation, LLC.
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

package com.avail.compiler.instruction

import com.avail.compiler.AvailCodeGenerator
import com.avail.descriptor.functions.ContinuationDescriptor
import com.avail.descriptor.functions.FunctionDescriptor
import com.avail.descriptor.tokens.A_Token
import com.avail.descriptor.tuples.A_Tuple
import com.avail.interpreter.levelOne.L1Operation.L1Ext_doPushLabel
import com.avail.interpreter.primitive.controlflow.P_ExitContinuationWithResultIf
import com.avail.interpreter.primitive.controlflow.P_RestartContinuation
import com.avail.interpreter.primitive.controlflow.P_RestartContinuationWithArguments
import com.avail.io.NybbleOutputStream

/**
 * I represent the use of a label.  When a label is *used*, it causes the
 * current [continuation][ContinuationDescriptor] to be copied.  The copy is
 * then reset to the state that existed when the current
 * [function][FunctionDescriptor] started running, resetting the program
 * counter, stack pointer, and stack slots, and creating new local variables.
 *
 * The new continuation can subsequently be [restart][P_RestartContinuation],
 * [restarted with new arguments][P_RestartContinuationWithArguments], or
 * [exited][P_ExitContinuationWithResultIf].
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @constructor
 *
 * Construct an instruction.  Capture the tokens that contributed to it.
 *
 * @param relevantTokens
 *   The [A_Tuple] of [A_Token]s that are associated with this instruction.
 */
class AvailPushLabel constructor(relevantTokens: A_Tuple)
	: AvailInstruction(relevantTokens)
{
	override fun writeNybblesOn(aStream: NybbleOutputStream) =
		L1Ext_doPushLabel.writeTo(aStream)

	/**
	 * Push a label, which is a [ continuation][ContinuationDescriptor].  Since
	 * the label can be restarted (which constructs new locals while reusing the
	 * arguments), or exited (which has no static effect on optimizations), I
	 * only have an effect on arguments and outer variables.  Scan all arguments
	 * and outer variables and ensure the most recent pushes are reset so that
	 * isLastAccess is false.
	 */
	override fun fixUsageFlags(
		localData: MutableList<AvailVariableAccessNote?>,
		outerData: MutableList<AvailVariableAccessNote?>,
		codeGenerator: AvailCodeGenerator)
	{
		for (index in 0 until codeGenerator.numArgs)
		{
			var note = localData[index]
			if (note === null)
			{
				note = AvailVariableAccessNote()
				localData[index] = note
			}
			// If any argument was pushed before this pushLabel, set its
			// isLastAccess to false, as a restart will need to have these
			// arguments intact.
			note.previousPush?.isLastAccess = false
		}

		for (outerNote in outerData)
		{
			if (outerNote !== null)
			{
				outerNote.previousPush?.isLastAccess = false
				outerNote.previousGet?.canClear = false
			}
		}
	}
}
