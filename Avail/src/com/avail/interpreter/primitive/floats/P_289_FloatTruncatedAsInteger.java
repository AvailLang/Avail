/**
 * P_289_FloatTruncatedAsInteger.java
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
package com.avail.interpreter.primitive.floats;

import static com.avail.descriptor.TypeDescriptor.Types.FLOAT;
import static com.avail.interpreter.Primitive.Flag.*;
import static com.avail.exceptions.AvailErrorCode.E_CANNOT_CONVERT_NOT_A_NUMBER_TO_INTEGER;
import static java.lang.Math.*;
import java.util.List;
import com.avail.descriptor.*;
import com.avail.interpreter.*;

/**
 * <strong>Primitive 289:</strong> Convert a {@linkplain FloatDescriptor
 * float} to an {@linkplain IntegerDescriptor integer}, rounding towards
 * zero.
 */
public final class P_289_FloatTruncatedAsInteger extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	public final static Primitive instance =
		new P_289_FloatTruncatedAsInteger().init(
			1, CanFold, CanInline);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter,
		final boolean skipReturnCheck)
	{
		assert args.size() == 1;
		final A_Number a = args.get(0);
		// Extract the top two 32-bit sections.  That guarantees 33 bits
		// of mantissa, which is more than a float actually captures.
		float f = a.extractFloat();
		if (Float.isNaN(f))
		{
			return interpreter.primitiveFailure(
				E_CANNOT_CONVERT_NOT_A_NUMBER_TO_INTEGER);
		}
		final boolean neg = f < 0.0f;
		if (Float.isInfinite(f))
		{
			// Return the corresponding integral infinity.
			return interpreter.primitiveSuccess(
				neg
					? InfinityDescriptor.negativeInfinity()
					: InfinityDescriptor.positiveInfinity());
		}
		if (f >= Integer.MIN_VALUE && f <= Integer.MAX_VALUE)
		{
			// Common case -- it fits in an int.
			return interpreter.primitiveSuccess(
				IntegerDescriptor.fromInt((int)f));
		}
		f = abs(f);
		final int exponent = getExponent(f);
		final int slots = exponent + 31 / 32;  // probably needs work
		A_Number out = IntegerDescriptor.createUninitialized(slots);
		f = scalb(f, (1 - slots) * 32);
		for (int i = slots; i >= 1; --i)
		{
			final long intSlice = (int) f;
			out.rawUnsignedIntegerAtPut(i, (int)intSlice);
			f -= intSlice;
			f = scalb(f, 32);
		}
		out.trimExcessInts();
		if (neg)
		{
			out = IntegerDescriptor.zero().noFailMinusCanDestroy(out, true);
		}
		return interpreter.primitiveSuccess(out);
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return FunctionTypeDescriptor.create(
			TupleDescriptor.from(
				FLOAT.o()),
			IntegerRangeTypeDescriptor.extendedIntegers());
	}

	@Override
	protected A_Type privateFailureVariableType ()
	{
		return AbstractEnumerationTypeDescriptor.withInstance(
			E_CANNOT_CONVERT_NOT_A_NUMBER_TO_INTEGER.numericCode());
	}
}