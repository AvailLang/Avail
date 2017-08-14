/**
 * P_SpecialObject.java
 * Copyright © 1993-2017, The Avail Foundation, LLC.
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
package com.avail.interpreter.primitive.bootstrap;

import static com.avail.descriptor.ParseNodeTypeDescriptor.ParseNodeKind.*;
import static com.avail.exceptions.AvailErrorCode.E_NO_SPECIAL_OBJECT;
import static com.avail.interpreter.Primitive.Flag.*;
import java.util.List;
import com.avail.AvailRuntime;
import com.avail.descriptor.*;
import com.avail.interpreter.*;

/**
 * <strong>Primitive:</strong> Retrieve the {@linkplain
 * AvailRuntime#specialObject(int) special object} with the specified
 * ordinal.
 */
public final class P_SpecialObject extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	public static final Primitive instance =
		new P_SpecialObject().init(1, Unknown, Bootstrap);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter,
		final boolean skipReturnCheck)
	{
		assert args.size() == 1;
		final A_Phrase ordinalLiteral = args.get(0);
		final A_Number ordinal = ordinalLiteral.token().literal();
		if (!ordinal.isInt())
		{
			return interpreter.primitiveFailure(E_NO_SPECIAL_OBJECT);
		}
		final int i = ordinal.extractInt();
		final AvailObject result;
		try
		{
			result = AvailRuntime.specialObject(i);
		}
		catch (final ArrayIndexOutOfBoundsException e)
		{
			return interpreter.primitiveFailure(
				E_NO_SPECIAL_OBJECT);
		}
		return interpreter.primitiveSuccess(
			LiteralNodeDescriptor.syntheticFrom(result));
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return FunctionTypeDescriptor.create(
			TupleDescriptor.from(
				LITERAL_NODE.create(
					IntegerRangeTypeDescriptor.naturalNumbers())),
			LITERAL_NODE.mostGeneralType());
	}

	@Override
	protected A_Type privateFailureVariableType ()
	{
		return AbstractEnumerationTypeDescriptor.withInstances(
			SetDescriptor.from(
				E_NO_SPECIAL_OBJECT));
	}
}
