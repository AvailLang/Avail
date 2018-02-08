/*
 * P_CreateSuperCastExpression.java
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
package com.avail.interpreter.primitive.phrases;

import com.avail.descriptor.A_Phrase;
import com.avail.descriptor.A_Type;
import com.avail.descriptor.SuperCastNodeDescriptor;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;
import com.avail.optimizer.jvm.ReferencedInGeneratedCode;

import static com.avail.descriptor.AbstractEnumerationTypeDescriptor
	.enumerationWith;
import static com.avail.descriptor.FunctionTypeDescriptor.functionType;
import static com.avail.descriptor.InstanceMetaDescriptor.anyMeta;
import static com.avail.descriptor.ParseNodeTypeDescriptor.ParseNodeKind
	.EXPRESSION_NODE;
import static com.avail.descriptor.ParseNodeTypeDescriptor.ParseNodeKind
	.SUPER_CAST_NODE;
import static com.avail.descriptor.SetDescriptor.set;
import static com.avail.descriptor.SuperCastNodeDescriptor.newSuperCastNode;
import static com.avail.descriptor.TupleDescriptor.tuple;
import static com.avail.descriptor.TypeDescriptor.Types.ANY;
import static com.avail.exceptions.AvailErrorCode.*;
import static com.avail.interpreter.Primitive.Flag.CanFold;
import static com.avail.interpreter.Primitive.Flag.CanInline;

/**
 * <strong>Primitive:</strong> Transform a base expression and a type into
 * a {@linkplain SuperCastNodeDescriptor supercast phrase} that will use that
 * type for lookup.  Fail if the type is not a strict supertype of that which
 * will be produced by the expression.  Also fail if the expression is itself a
 * supertype, or if it is top-valued or bottom-valued.
 */
public final class P_CreateSuperCastExpression extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	@ReferencedInGeneratedCode
	public static final Primitive instance =
		new P_CreateSuperCastExpression().init(
			2, CanFold, CanInline);

	@Override
	public Result attempt (
		final Interpreter interpreter)
	{
		interpreter.checkArgumentCount(2);
		final A_Phrase expression = interpreter.argument(0);
		final A_Type lookupType = interpreter.argument(1);

		final A_Type expressionType = expression.expressionType();
		if (expressionType.isBottom() || expressionType.isTop())
		{
			return interpreter.primitiveFailure(
				E_SUPERCAST_EXPRESSION_TYPE_MUST_NOT_BE_TOP_OR_BOTTOM);
		}
		if (expression.parseNodeKindIsUnder(SUPER_CAST_NODE))
		{
			return interpreter.primitiveFailure(
				E_SUPERCAST_EXPRESSION_MUST_NOT_ALSO_BE_A_SUPERCAST);
		}
		if (!expressionType.isSubtypeOf(lookupType)
			|| expressionType.equals(lookupType))
		{
			return interpreter.primitiveFailure(
				E_SUPERCAST_MUST_BE_STRICT_SUPERTYPE_OF_EXPRESSION_TYPE);
		}
		final A_Phrase supercast = newSuperCastNode(expression, lookupType);
		return interpreter.primitiveSuccess(supercast);
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return functionType(
			tuple(
				EXPRESSION_NODE.create(ANY.o()),
				anyMeta()),
			SUPER_CAST_NODE.mostGeneralType());
	}

	@Override
	protected A_Type privateFailureVariableType ()
	{
		return enumerationWith(set(
			E_SUPERCAST_EXPRESSION_TYPE_MUST_NOT_BE_TOP_OR_BOTTOM,
			E_SUPERCAST_EXPRESSION_MUST_NOT_ALSO_BE_A_SUPERCAST,
			E_SUPERCAST_MUST_BE_STRICT_SUPERTYPE_OF_EXPRESSION_TYPE));
	}
}
