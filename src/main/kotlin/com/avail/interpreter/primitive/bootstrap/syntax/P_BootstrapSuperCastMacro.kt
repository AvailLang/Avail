/*
 * P_BootstrapSuperCastMacro.java
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

package com.avail.interpreter.primitive.bootstrap.syntax

import com.avail.compiler.AvailRejectedParseException
import com.avail.compiler.problems.CompilerDiagnostics.ParseNotificationLevel.STRONG
import com.avail.descriptor.A_Type
import com.avail.descriptor.FunctionTypeDescriptor.functionType
import com.avail.descriptor.InstanceMetaDescriptor.anyMeta
import com.avail.descriptor.ObjectTupleDescriptor.tuple
import com.avail.descriptor.PhraseTypeDescriptor.PhraseKind.*
import com.avail.descriptor.SuperCastPhraseDescriptor
import com.avail.descriptor.SuperCastPhraseDescriptor.newSuperCastNode
import com.avail.descriptor.TypeDescriptor.Types.ANY
import com.avail.interpreter.Interpreter
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Flag.*

/**
 * The `P_BootstrapSuperCastMacro` primitive is used to create a
 * [super-cast phrase][SuperCastPhraseDescriptor].  This is used to
 * control method lookup, and is a generalization of the concept of `super` found in some object-oriented languages.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
object P_BootstrapSuperCastMacro : Primitive(2, CannotFail, CanInline, Bootstrap)
{

	override fun attempt(
		interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(2)
		val expressionNode = interpreter.argument(0)
		val typeLiteral = interpreter.argument(1)

		val type = typeLiteral.token().literal()
		if (type.isTop || type.isBottom)
		{
			throw AvailRejectedParseException(
				STRONG,
				"supercast type to be something other than %s",
				type)
		}
		val expressionType = expressionNode.expressionType()
		if (!expressionType.isSubtypeOf(type))
		{
			throw AvailRejectedParseException(
				STRONG,
				"supercast type (%s) to be a supertype of the " + "expression's type (%s)",
				type,
				expressionType)
		}
		val superCast = newSuperCastNode(expressionNode, type)
		superCast.makeImmutable()
		return interpreter.primitiveSuccess(superCast)
	}

	override fun privateBlockTypeRestriction(): A_Type
	{
		return functionType(
			tuple(
				EXPRESSION_PHRASE.create(ANY.o()),
				LITERAL_PHRASE.create(anyMeta())),
			SUPER_CAST_PHRASE.mostGeneralType())
	}

}