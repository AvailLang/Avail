/*
 * AvailSuperCall.kt
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

import com.avail.descriptor.bundles.A_Bundle
import com.avail.descriptor.methods.MethodDescriptor
import com.avail.descriptor.tokens.A_Token
import com.avail.descriptor.tuples.A_Tuple
import com.avail.descriptor.types.TupleTypeDescriptor
import com.avail.interpreter.levelOne.L1Operation.L1Ext_doSuperCall
import com.avail.io.NybbleOutputStream

/**
 * This is a multi-method super-call instruction.  The opcode is followed by:
 *
 *  1. The index of the [message bundle][A_Bundle] (which itself refers to a
 *     [method][MethodDescriptor]), then
 *  2. The index of the literal that holds the expected return type for this
 *     call site, then
 *  3. A [tuple type][TupleTypeDescriptor] used to direct the method lookup.
 *
 * @property superUnionIndex
 *   The index of the literal that holds a tuple type which is used to direct
 *   method lookup.  The union of this tuple type and the type of the tuple of
 *   actual arguments at run time is used to look up the method.
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @constructor
 *
 * Construct a new `AvailSuperCall`.
 *
 * @param relevantTokens
 *   The [A_Tuple] of [A_Token]s that are associated with this instruction.
 * @param messageIndex
 *   The index of the literal that holds the message (a
 *   [method][MethodDescriptor]).
 * @param verifyIndex
 *   The index of the literal that holds the return type.
 * @param superUnionIndex
 *   The index of the literal that holds a tuple type used to direct method
 *   lookup.
 */
class AvailSuperCall constructor(
	relevantTokens: A_Tuple,
	messageIndex: Int,
	verifyIndex: Int,
	private val superUnionIndex: Int)
: AvailCall(relevantTokens, messageIndex, verifyIndex)
{
	override fun writeNybblesOn(aStream: NybbleOutputStream)
	{
		L1Ext_doSuperCall.writeTo(aStream)
		writeIntegerOn(index, aStream)
		writeIntegerOn(verifyIndex, aStream)
		writeIntegerOn(superUnionIndex, aStream)
	}
}
