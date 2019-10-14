/*
 * P_CreateInitializingVariableDeclaration.java
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
import com.avail.descriptor.DeclarationPhraseDescriptor.newVariable
import com.avail.descriptor.FunctionTypeDescriptor.functionType
import com.avail.descriptor.InstanceMetaDescriptor.anyMeta
import com.avail.descriptor.NilDescriptor.nil
import com.avail.descriptor.ObjectTupleDescriptor.tuple
import com.avail.descriptor.PhraseTypeDescriptor.PhraseKind
import com.avail.descriptor.PhraseTypeDescriptor.PhraseKind.EXPRESSION_PHRASE
import com.avail.descriptor.PhraseTypeDescriptor.PhraseKind.LOCAL_VARIABLE_PHRASE
import com.avail.descriptor.TokenDescriptor
import com.avail.descriptor.TypeDescriptor.Types.ANY
import com.avail.descriptor.TypeDescriptor.Types.TOKEN
import com.avail.interpreter.Interpreter
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Flag.CanInline
import com.avail.interpreter.Primitive.Flag.CannotFail

/**
 * **Primitive:** Create an initializing [ ][PhraseKind.LOCAL_VARIABLE_PHRASE] from the
 * specified [token][TokenDescriptor], type, and [ ][PhraseKind.EXPRESSION_PHRASE].
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object P_CreateInitializingVariableDeclaration : Primitive(3, CanInline, CannotFail)
{

	override fun attempt(
		interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(3)
		val token = interpreter.argument(0)
		val type = interpreter.argument(1)
		val initializer = interpreter.argument(2)
		return interpreter.primitiveSuccess(
			newVariable(token, type, nil, initializer))
	}

	override fun privateBlockTypeRestriction(): A_Type
	{
		return functionType(
			tuple(
				TOKEN.o(),
				anyMeta(),
				EXPRESSION_PHRASE.create(ANY.o())),
			LOCAL_VARIABLE_PHRASE.mostGeneralType())
	}

}