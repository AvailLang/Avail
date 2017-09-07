/**
 * P_CreateUUIDByteTuple.java
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

package com.avail.interpreter.primitive.general;

import com.avail.descriptor.A_Type;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.ByteBufferTupleDescriptor;
import com.avail.descriptor.FunctionTypeDescriptor;
import com.avail.descriptor.IntegerRangeTypeDescriptor;
import com.avail.descriptor.TupleTypeDescriptor;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.UUID;

import static com.avail.descriptor.FunctionTypeDescriptor.functionType;
import static com.avail.descriptor.IntegerRangeTypeDescriptor.bytes;
import static com.avail.descriptor.IntegerRangeTypeDescriptor.singleInt;
import static com.avail.descriptor.TupleDescriptor.emptyTuple;
import static com.avail.descriptor.TupleTypeDescriptor.tupleTypeForSizesTypesDefaultType;
import static com.avail.interpreter.Primitive.Flag.*;

/**
 * <strong>Primitive:</strong> Answer a cryptographically strong
 * pseudo-random {@link UUID} as a 16-byte {@link ByteBufferTupleDescriptor
 * tuple}.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class P_CreateUUIDByteTuple
extends Primitive
{
	/**
	 * The sole instance of this primitive class. Accessed through reflection.
	 */
	public static final Primitive instance =
		new P_CreateUUIDByteTuple().init(
			0, CannotFail, CanInline, HasSideEffect);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter,
		final boolean skipReturnCheck)
	{
		assert args.size() == 0;
		final UUID uuid = UUID.randomUUID();
		final ByteBuffer bytes = ByteBuffer.allocateDirect(16);
		bytes.putLong(uuid.getMostSignificantBits());
		bytes.putLong(uuid.getLeastSignificantBits());
		bytes.flip();
		return interpreter.primitiveSuccess(
			ByteBufferTupleDescriptor.forByteBuffer(bytes));
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return functionType(
			emptyTuple(),
			tupleTypeForSizesTypesDefaultType(
				singleInt(16), emptyTuple(), bytes()));
	}
}
