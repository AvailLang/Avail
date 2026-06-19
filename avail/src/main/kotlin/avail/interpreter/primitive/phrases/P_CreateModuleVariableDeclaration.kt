/*
 * P_CreateModuleVariableDeclaration.kt
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

package avail.interpreter.primitive.phrases

import avail.descriptor.phrases.DeclarationPhraseDescriptor.Companion.newModuleVariable
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.A_Type
import avail.descriptor.representation.A_Variable
import avail.descriptor.representation.AvailObject
import avail.descriptor.representation.NilDescriptor.Companion.nil
import avail.descriptor.tokens.TokenDescriptor
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.PhraseTypeDescriptor.PhraseKind
import avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.MODULE_VARIABLE_PHRASE
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.TOKEN
import avail.descriptor.types.VariableTypeDescriptor.Companion.mostGeneralVariableType
import avail.interpreter.execution.Interpreter
import avail.interpreter.primitive.Primitive.Flag.CanInline
import avail.interpreter.primitive.Primitive.Flag.CannotFail
import avail.interpreter.primitive.Primitive2

/**
 * **Primitive:** Create a new
 * [module&#32;variable&#32;declaration][PhraseKind.MODULE_VARIABLE_PHRASE] from
 * the specified [token][TokenDescriptor] and actual [variable][A_Variable].
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
@Suppress("unused")
object P_CreateModuleVariableDeclaration : Primitive2(CanInline, CannotFail)
{
	override fun Interpreter.attempt2(
		arg1: AvailObject,
		arg2: AvailObject
	): A_BasicObject?
	{
		val variable = arg1
		val token = arg2
		return newModuleVariable(token, variable, nil, nil)
	}

	/**
	 * The variable might be an escaped variable, making it shared here.
	 */
	override fun mightMakeEscapedVariableShared(
		argumentTypes: List<A_Type>
	): Boolean = true

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(
				mostGeneralVariableType,
				TOKEN()),
			MODULE_VARIABLE_PHRASE.mostGeneralType)
}
