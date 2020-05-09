/*
 * P_CreateSuperCastExpression.kt
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
package com.avail.interpreter.primitive.phrases

import com.avail.descriptor.phrases.SuperCastPhraseDescriptor
import com.avail.descriptor.phrases.SuperCastPhraseDescriptor.Companion.newSuperCastNode
import com.avail.descriptor.sets.SetDescriptor.set
import com.avail.descriptor.tuples.ObjectTupleDescriptor.tuple
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.AbstractEnumerationTypeDescriptor.enumerationWith
import com.avail.descriptor.types.FunctionTypeDescriptor.functionType
import com.avail.descriptor.types.InstanceMetaDescriptor.anyMeta
import com.avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.EXPRESSION_PHRASE
import com.avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.SUPER_CAST_PHRASE
import com.avail.descriptor.types.TypeDescriptor.Types.ANY
import com.avail.exceptions.AvailErrorCode.*
import com.avail.interpreter.execution.Interpreter
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Flag.CanFold
import com.avail.interpreter.Primitive.Flag.CanInline

/**
 * **Primitive:** Transform a base expression and a type into a
 * [supercast&#32;phrase][SuperCastPhraseDescriptor] that will use that type for
 * lookup.  Fail if the type is not a strict supertype of that which will be
 * produced by the expression.  Also fail if the expression is itself a
 * supertype, or if it is top-valued or bottom-valued.
 */
object P_CreateSuperCastExpression : Primitive(2, CanFold, CanInline)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(2)
		val expression = interpreter.argument(0)
		val lookupType = interpreter.argument(1)

		val expressionType = expression.expressionType()
		return when {
			expressionType.isBottom || expressionType.isTop ->
				interpreter.primitiveFailure(
					E_SUPERCAST_EXPRESSION_TYPE_MUST_NOT_BE_TOP_OR_BOTTOM)
			expression.phraseKindIsUnder(SUPER_CAST_PHRASE) ->
				interpreter.primitiveFailure(
					E_SUPERCAST_EXPRESSION_MUST_NOT_ALSO_BE_A_SUPERCAST)
			!expressionType.isSubtypeOf(lookupType)
					|| expressionType.equals(lookupType) ->
				interpreter.primitiveFailure(
					E_SUPERCAST_MUST_BE_STRICT_SUPERTYPE_OF_EXPRESSION_TYPE)
			else -> interpreter.primitiveSuccess(newSuperCastNode(expression, lookupType))
		}
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(
				EXPRESSION_PHRASE.create(ANY.o()),
				anyMeta()),
			SUPER_CAST_PHRASE.mostGeneralType())

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(set(
			E_SUPERCAST_EXPRESSION_TYPE_MUST_NOT_BE_TOP_OR_BOTTOM,
			E_SUPERCAST_EXPRESSION_MUST_NOT_ALSO_BE_A_SUPERCAST,
			E_SUPERCAST_MUST_BE_STRICT_SUPERTYPE_OF_EXPRESSION_TYPE))
}
