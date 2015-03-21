/**
 * P_346_CreateSuperCastExpression.java
 * Copyright © 1993-2015, The Avail Foundation, LLC.
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
package com.avail.interpreter.primitive;

import static com.avail.descriptor.ParseNodeTypeDescriptor.ParseNodeKind.*;
import static com.avail.descriptor.TypeDescriptor.Types.*;
import static com.avail.exceptions.AvailErrorCode.*;
import static com.avail.interpreter.Primitive.Flag.*;
import java.util.Arrays;
import java.util.List;
import com.avail.descriptor.*;
import com.avail.interpreter.*;

/**
 * <strong>Primitive 346:</strong> Transform a base expression and a type into
 * a {@linkplain SuperCastNodeDescriptor supercast phrase} that will use that
 * type for lookup.  Fail if the type is not a strict supertype of that which
 * will be produced by the expression.  Also fail if the expression is itself a
 * supertype, or if it is top-valued or bottom-valued.
 */
public final class P_346_CreateSuperCastExpression extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	public final static Primitive instance =
		new P_346_CreateSuperCastExpression().init(
			2, CanFold, CanInline);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter,
		final boolean skipReturnCheck)
	{
		assert args.size() == 2;
		final A_Phrase expression = args.get(0);
		final A_Type lookupType = args.get(1);

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
		final A_Phrase supercast = SuperCastNodeDescriptor.create(
			expression, lookupType);
		return interpreter.primitiveSuccess(supercast);
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return FunctionTypeDescriptor.create(
			TupleDescriptor.from(
				EXPRESSION_NODE.create(ANY.o()),
				InstanceMetaDescriptor.anyMeta()),
			SUPER_CAST_NODE.mostGeneralType());
	}

	@Override
	protected A_Type privateFailureVariableType ()
	{
		return AbstractEnumerationTypeDescriptor.withInstances(
			SetDescriptor.fromCollection(Arrays.asList(
				E_SUPERCAST_EXPRESSION_TYPE_MUST_NOT_BE_TOP_OR_BOTTOM.numericCode(),
				E_SUPERCAST_EXPRESSION_MUST_NOT_ALSO_BE_A_SUPERCAST.numericCode(),
				E_SUPERCAST_MUST_BE_STRICT_SUPERTYPE_OF_EXPRESSION_TYPE.numericCode())));
	}
}