/**
 * P_PojoArraySet.java
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
package com.avail.interpreter.primitive.pojos;

import static com.avail.descriptor.TypeDescriptor.Types.*;
import static com.avail.exceptions.AvailErrorCode.*;
import static com.avail.interpreter.Primitive.Flag.*;
import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.List;
import com.avail.descriptor.*;
import com.avail.exceptions.MarshalingException;
import com.avail.interpreter.*;

/**
 * <strong>Primitive:</strong> Overwrite the {@linkplain AvailObject
 * element} that resides at the given {@linkplain IntegerDescriptor
 * subscript} of the specified {@linkplain PojoTypeDescriptor pojo array
 * type}.
 */
public final class P_PojoArraySet extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	public final static Primitive instance =
		new P_PojoArraySet().init(
			3, CanFold, CanInline);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter,
		final boolean skipReturnCheck)
	{
		assert args.size() == 3;
		final A_BasicObject pojo = args.get(0);
		final A_Number subscript = args.get(1);
		final A_BasicObject value = args.get(2);
		final A_BasicObject rawPojo = pojo.rawPojo();
		final Object array = rawPojo.javaObject();
		final int index = subscript.extractInt();
		if (index > Array.getLength(array))
		{
			return interpreter.primitiveFailure(E_SUBSCRIPT_OUT_OF_BOUNDS);
		}
		final A_Type contentType = pojo.kind().contentType();
		if (!value.isInstanceOf(contentType))
		{
			return interpreter.primitiveFailure(
				E_CANNOT_STORE_INCORRECTLY_TYPED_VALUE);
		}
		try
		{
			final Class<?> marshaledType =
				(Class<?>) contentType.marshalToJava(null);
			Array.set(array, index - 1, value.marshalToJava(marshaledType));
		}
		catch (final MarshalingException e)
		{
			return interpreter.primitiveFailure(e);
		}
		return interpreter.primitiveSuccess(NilDescriptor.nil());
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return FunctionTypeDescriptor.create(
			TupleDescriptor.from(
				PojoTypeDescriptor.mostGeneralArrayType(),
				IntegerRangeTypeDescriptor.naturalNumbers(),
				ANY.o()),
			TOP.o());
	}

	@Override
	protected A_Type privateFailureVariableType ()
	{
		return AbstractEnumerationTypeDescriptor.withInstances(
			SetDescriptor.from(
				E_SUBSCRIPT_OUT_OF_BOUNDS,
				E_CANNOT_STORE_INCORRECTLY_TYPED_VALUE,
				E_JAVA_MARSHALING_FAILED));
	}
}
