/**
 * P_ComputeDigest.java
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

import com.avail.descriptor.A_Number;
import com.avail.descriptor.A_Tuple;
import com.avail.descriptor.A_Type;
import com.avail.descriptor.AvailObject;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

import static com.avail.descriptor.AbstractEnumerationTypeDescriptor
	.enumerationWith;
import static com.avail.descriptor.ByteArrayTupleDescriptor.tupleForByteArray;
import static com.avail.descriptor.FunctionTypeDescriptor.functionType;
import static com.avail.descriptor.IntegerRangeTypeDescriptor.bytes;
import static com.avail.descriptor.TupleDescriptor.tuple;
import static com.avail.descriptor.TupleDescriptor.tupleFromIntegerList;
import static com.avail.descriptor.TupleTypeDescriptor.oneOrMoreOf;
import static com.avail.descriptor.TupleTypeDescriptor.zeroOrMoreOf;
import static com.avail.interpreter.Primitive.Flag.*;
import static java.util.Arrays.asList;

/**
 * <strong>Primitive:</strong> Use a strong cryptographic message digest
 * algorithm to produce a hash of the specified {@linkplain A_Tuple tuple} of
 * bytes.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class P_ComputeDigest
extends Primitive
{
	/**
	 * The sole instance of this primitive class. Accessed through reflection.
	 */
	public static final Primitive instance =
		new P_ComputeDigest().init(2, CannotFail, CanFold, CanInline);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter)
	{
		assert args.size() == 2;
		final A_Number algorithm = args.get(0);
		final A_Tuple bytes = args.get(1);
		final MessageDigest digest;
		try
		{
			digest = MessageDigest.getInstance("SHA-" + algorithm);
		}
		catch (final NoSuchAlgorithmException e)
		{
			assert false :
				"these are standard digest algorithm available in all "
				+ "Java implementations";
			throw new RuntimeException(e);
		}
		final int size = bytes.tupleSize();
		final ByteBuffer buffer = ByteBuffer.allocateDirect(size);
		bytes.transferIntoByteBuffer(1, size, buffer);
		buffer.flip();
		digest.update(buffer);
		final byte[] digestBytes = digest.digest();
		final A_Tuple digestTuple = tupleForByteArray(digestBytes);
		return interpreter.primitiveSuccess(digestTuple);
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return functionType(
			tuple(
				enumerationWith(
					tupleFromIntegerList(asList(1, 256, 384, 512)).asSet()),
				zeroOrMoreOf(bytes())),
			oneOrMoreOf(bytes()));
	}
}
