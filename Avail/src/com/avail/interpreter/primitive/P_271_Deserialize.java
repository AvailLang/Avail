/**
 * P_271_Deserialize.java
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

import static com.avail.descriptor.TypeDescriptor.Types.*;
import static com.avail.exceptions.AvailErrorCode.*;
import static com.avail.interpreter.Primitive.Flag.*;
import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import com.avail.descriptor.*;
import com.avail.interpreter.*;
import com.avail.serialization.Deserializer;

/**
 * <strong>Primitive 271</strong>: Answer a {@linkplain A_Tuple tuple}
 * comprising the objects encoded in the specified {@linkplain
 * IntegerRangeTypeDescriptor#bytes() byte} tuple, preserving their order.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class P_271_Deserialize
extends Primitive
{
	/**
	 * The sole instance of this primitive class. Accessed through reflection.
	 */
	public final static Primitive instance =
		new P_271_Deserialize().init(2, CanInline);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter,
		final boolean skipReturnCheck)
	{
		assert args.size() == 2;
		final A_Tuple bytes = args.get(0);
		final A_Module module = args.get(1);

		final byte[] byteArray;
		if (bytes.isByteArrayTuple())
		{
			byteArray = bytes.byteArray();
		}
		else if (bytes.isByteBufferTuple())
		{
			final ByteBuffer buffer = bytes.byteBuffer().slice();
			if (buffer.hasArray())
			{
				byteArray = buffer.array();
			}
			else
			{
				final int limit = buffer.limit();
				byteArray = new byte[limit];
				buffer.get(byteArray);
			}
		}
		else
		{
			final int limit = bytes.tupleSize();
			final ByteBuffer buffer = ByteBuffer.allocate(limit);
			bytes.transferIntoByteBuffer(1, limit, buffer);
			byteArray = buffer.array();
		}

		final ByteArrayInputStream in = new ByteArrayInputStream(byteArray);
		final List<A_BasicObject> values = new ArrayList<>();
		final Deserializer deserializer = new Deserializer(
			in, interpreter.runtime());
		deserializer.currentModule(module);
		try
		{
			A_BasicObject value;
			while ((value = deserializer.deserialize()) != null)
			{
				values.add(value);
			}
		}
		catch (final Exception e)
		{
			return interpreter.primitiveFailure(E_DESERIALIZATION_FAILED);
		}
		return interpreter.primitiveSuccess(TupleDescriptor.fromList(values));
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return FunctionTypeDescriptor.create(
			TupleDescriptor.from(
				TupleTypeDescriptor.zeroOrMoreOf(
					IntegerRangeTypeDescriptor.bytes()),
				MODULE.o()),
			TupleTypeDescriptor.zeroOrMoreOf(ANY.o()));
	}

	@Override
	protected A_Type privateFailureVariableType ()
	{
		return AbstractEnumerationTypeDescriptor.withInstance(
			E_DESERIALIZATION_FAILED.numericCode());
	}
}
