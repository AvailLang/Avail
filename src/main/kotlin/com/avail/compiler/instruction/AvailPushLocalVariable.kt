/*
 * AvailPushLocalVariable.kt
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
import com.avail.descriptor.tuples.A_Tuple
import com.avail.descriptor.ContinuationDescriptor
import com.avail.interpreter.levelOne.L1Operation.L1_doPushLastLocal
import com.avail.interpreter.levelOne.L1Operation.L1_doPushLocal
import com.avail.io.NybbleOutputStream

/**
 * Push either a local variable (the variable itself), a local constant, or an
 * argument.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @constructor
 *
 * Construct a new `AvailPushLocalVariable`.
 *
 * @param relevantTokens
 *   The [A_Tuple] of [A_Token]s that are associated with this instruction.
 * @param variableIndex
 *   The index that the local variable will occupy at runtime within a
 *   [continuation][ContinuationDescriptor].
 */
class AvailPushLocalVariable constructor(
	relevantTokens: A_Tuple,
	variableIndex: Int)
: AvailPushVariable(relevantTokens, variableIndex)
{
	override fun writeNybblesOn(aStream: NybbleOutputStream)
	{
		val op = if (isLastAccess) L1_doPushLastLocal else L1_doPushLocal
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
	 * The data lists are keyed by local or outer index.  Each entry is
	 * either null or a [AvailVariableAccessNote], which keeps track of
	 * the previous time a get or push happened.
	 *
	 * The receiver pushes a local variable or an argument.
	 */
	override fun fixUsageFlags(
		localData: MutableList<AvailVariableAccessNote?>,
		outerData: MutableList<AvailVariableAccessNote?>,
		codeGenerator: AvailCodeGenerator)
	{
		var note = localData[index - 1]
		if (note === null)
		{
			note = AvailVariableAccessNote()
			localData[index - 1] = note
		}
		// If there was a push before this one, set its isLastAccess to false,
		// as the receiver is clearly a later use.
		note.previousPush?.isLastAccess = false
		isLastAccess = true
		// If there was a get before this push, make sure its canClear flag is
		// false (the variable escapes).
		note.previousGet?.canClear = false
		note.previousPush = this
	}
}
