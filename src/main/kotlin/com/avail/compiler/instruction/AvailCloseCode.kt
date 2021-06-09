/*
 * AvailCloseCode.kt
 * Copyright © 1993-2021, The Avail Foundation, LLC.
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

import com.avail.descriptor.functions.CompiledCodeDescriptor
import com.avail.descriptor.functions.FunctionDescriptor
import com.avail.descriptor.tokens.A_Token
import com.avail.descriptor.tuples.A_Tuple
import com.avail.interpreter.levelOne.L1Operation.L1_doClose
import com.avail.io.NybbleOutputStream

/**
 * This instruction build a [function][FunctionDescriptor] from
 * [compiled&#32;code][CompiledCodeDescriptor] and some pushed variables.
 *
 * @property numCopiedVars
 *   The number of variables that have been pushed on the stack to be captured
 *   as outer variables of the resulting [function][FunctionDescriptor].
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @constructor
 *
 * Construct a new `AvailCloseCode`.
 *
 * @param relevantTokens
 *   The [A_Tuple] of [A_Token]s that are associated with this instruction.
 * @param numCopiedVars
 *   The number of already-pushed variables to capture in the function as outer
 *   variables.
 * @param codeIndex
 *   The index of the compiled code in the literals.
 */
class AvailCloseCode constructor(
	relevantTokens: A_Tuple,
	private val numCopiedVars: Int,
	codeIndex: Int) : AvailInstructionWithIndex(relevantTokens, codeIndex)
{
	override fun writeNybblesOn(aStream: NybbleOutputStream)
	{
		L1_doClose.writeTo(aStream)
		writeIntegerOn(numCopiedVars, aStream)
		writeIntegerOn(index, aStream)
	}
}
