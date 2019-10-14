/*
 * P_BlockStatements.java
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

package com.avail.interpreter.primitive.phrases

import com.avail.descriptor.A_Type
import com.avail.descriptor.BlockPhraseDescriptor
import com.avail.descriptor.FunctionTypeDescriptor.functionType
import com.avail.descriptor.ObjectTupleDescriptor.tuple
import com.avail.descriptor.PhraseTypeDescriptor.PhraseKind
import com.avail.descriptor.PhraseTypeDescriptor.PhraseKind.BLOCK_PHRASE
import com.avail.descriptor.PhraseTypeDescriptor.PhraseKind.PARSE_PHRASE
import com.avail.descriptor.TupleDescriptor
import com.avail.descriptor.TupleTypeDescriptor.zeroOrMoreOf
import com.avail.interpreter.Interpreter
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Flag.*

/**
 * **Primitive:** Answer the [tuple][TupleDescriptor]
 * of [statements][PhraseKind.PARSE_PHRASE] for the specified
 * [block][BlockPhraseDescriptor].
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object P_BlockStatements : Primitive(1, CannotFail, CanFold, CanInline)
{

	override fun attempt(
		interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(1)
		val block = interpreter.argument(0)
		return interpreter.primitiveSuccess(block.statementsTuple())
	}

	override fun privateBlockTypeRestriction(): A_Type
	{
		return functionType(
			tuple(
				BLOCK_PHRASE.mostGeneralType()),
			zeroOrMoreOf(PARSE_PHRASE.mostGeneralType()))
	}

}