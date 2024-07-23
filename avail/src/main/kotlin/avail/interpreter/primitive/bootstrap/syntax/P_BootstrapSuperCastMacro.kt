/*
 * P_BootstrapSuperCastMacro.kt
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

package avail.interpreter.primitive.bootstrap.syntax

import avail.compiler.AvailRejectedParseException
import avail.compiler.problems.CompilerDiagnostics.ParseNotificationLevel.STRONG
import avail.descriptor.phrases.A_Phrase.Companion.phraseExpressionType
import avail.descriptor.phrases.A_Phrase.Companion.token
import avail.descriptor.phrases.SuperCastPhraseDescriptor
import avail.descriptor.phrases.SuperCastPhraseDescriptor.Companion.newSuperCastNode
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.types.A_Type
import avail.descriptor.types.A_Type.Companion.isSubtypeOf
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.InstanceMetaDescriptor.Companion.anyMeta
import avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.EXPRESSION_PHRASE
import avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.LITERAL_PHRASE
import avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.SUPER_CAST_PHRASE
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.ANY
import avail.interpreter.Primitive
import avail.interpreter.Primitive.Flag.Bootstrap
import avail.interpreter.Primitive.Flag.CanInline
import avail.interpreter.Primitive.Flag.CannotFail
import avail.interpreter.execution.Interpreter

/**
 * The `P_BootstrapSuperCastMacro` primitive is used to create a
 * [super-cast&#32;phrase][SuperCastPhraseDescriptor].  This is used to control
 * method lookup, and is a generalization of the concept of `super` found in
 * some object-oriented languages.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
@Suppress("unused")
object P_BootstrapSuperCastMacro
	: Primitive(2, CannotFail, CanInline, Bootstrap)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(2)
		val expressionNode = interpreter.argument(0)
		val typeLiteral = interpreter.argument(1)

		val type = typeLiteral.token.literal()
		if (type.isTop || type.isBottom)
		{
			throw AvailRejectedParseException(
				STRONG,
				"supercast type to be something other than $type")
		}
		val expressionType = expressionNode.phraseExpressionType
		if (!expressionType.isSubtypeOf(type))
		{
			throw AvailRejectedParseException(
				STRONG,
				"supercast type ($type) to be a supertype of the "
					+ "expression's type ($expressionType)")
		}
		val superCast = newSuperCastNode(expressionNode, type)
		return interpreter.primitiveSuccess(superCast.makeImmutable())
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(
				EXPRESSION_PHRASE.create(ANY.o),
				LITERAL_PHRASE.create(anyMeta)),
			SUPER_CAST_PHRASE.mostGeneralType)
}
