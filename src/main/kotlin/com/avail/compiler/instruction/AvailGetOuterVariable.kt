/*
 * AvailGetOuterVariable.kt
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
import com.avail.descriptor.A_Token
import com.avail.descriptor.FunctionDescriptor
import com.avail.descriptor.tuples.A_Tuple
import com.avail.interpreter.levelOne.L1Operation.L1_doGetOuter
import com.avail.interpreter.levelOne.L1Operation.L1_doGetOuterClearing
import com.avail.io.NybbleOutputStream

/**
 * Push the value of an outer (lexically captured) variable.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @constructor
 *
 * Construct a new `AvailGetOuterVariable`.
 *
 * @param relevantTokens
 *   The [A_Tuple] of [A_Token]s that are associated with this instruction.
 * @param outerIndex
 *   The index of the variable in a [function's][FunctionDescriptor] list of
 *   outer variables.
 */
class AvailGetOuterVariable constructor(
	relevantTokens: A_Tuple,
	outerIndex: Int) : AvailGetVariable(relevantTokens, outerIndex)
{
	override val isOuterUse: Boolean
		get() = true

	override fun writeNybblesOn(aStream: NybbleOutputStream)
	{
		val op = if (canClear) L1_doGetOuterClearing else L1_doGetOuter
		op.writeTo(aStream)
		writeIntegerOn(index, aStream)
	}

	/**
	 * The instructions of a block are being iterated over.  Coordinate
	 * optimizations between instructions using localData and outerData, two
	 * [lists][List] manipulated by overrides of this method.  Treat each
	 * instruction as though it is the last one in the block, and save enough
	 * information in the lists to be able to undo consequences of this
	 * assumption when a later instruction shows it to be unwarranted.
	 *
	 * The data lists are keyed by local or outer index.  Each entry is either
	 * null or a [AvailVariableAccessNote], which keeps track of the
	 * previous time a get or push happened.
	 *
	 * I get the value of an outer, so it can't be an outer reference to an
	 * argument (they aren't wrapped in a variable).
	 */
	override fun fixUsageFlags(
		localData: MutableList<AvailVariableAccessNote?>,
		outerData: MutableList<AvailVariableAccessNote?>,
		codeGenerator: AvailCodeGenerator)
	{
		var note = outerData[index - 1]
		if (note === null)
		{
			note = AvailVariableAccessNote()
			outerData[index - 1] = note
		}
		note.previousGet?.canClear = false
		canClear = true
		note.previousGet = this
		note.previousPush?.isLastAccess = false
	}
}
