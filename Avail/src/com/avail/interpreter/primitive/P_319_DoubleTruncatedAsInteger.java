/**
 * P_319_DoubleTruncatedAsInteger.java
 * Copyright © 1993-2012, Mark van Gulik and Todd L Smith.
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

import static com.avail.descriptor.TypeDescriptor.Types.DOUBLE;
import static com.avail.interpreter.Primitive.Flag.*;
import static java.lang.Math.*;
import java.util.List;
import com.avail.descriptor.*;
import com.avail.interpreter.*;

/**
 * <strong>Primitive 319:</strong> Convert a {@linkplain DoubleDescriptor
 * double} to an {@linkplain IntegerDescriptor integer}, rounding towards
 * zero.
 */
public final class P_319_DoubleTruncatedAsInteger extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	public final static Primitive instance = new P_319_DoubleTruncatedAsInteger().init(
		1, CanFold, CannotFail);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter,
		final boolean skipReturnCheck)
	{
		final A_Number a = args.get(0);
		assert args.size() == 1;
		// Extract the top three 32-bit sections.  That guarantees 65 bits
		// of mantissa, which is more than a double actually captures.
		double d = a.extractDouble();
		if (d >= Integer.MIN_VALUE && d <= Integer.MAX_VALUE)
		{
			// Common case -- it fits in an int.
			return interpreter.primitiveSuccess(
				IntegerDescriptor.fromInt((int)d));
		}
		final boolean neg = d < 0.0d;
		d = abs(d);
		final int exponent = getExponent(d);
		final int slots = exponent + 31 / 32;  // probably needs work
		A_Number out = IntegerDescriptor.createUninitialized(slots);
		d = scalb(d, (1 - slots) * 32);
		for (int i = slots; i >= 1; --i)
		{
			final long intSlice = (int) d;
			out.rawUnsignedIntegerAtPut(i, (int)intSlice);
			d -= intSlice;
			d = scalb(d, 32);
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
				DOUBLE.o()),
			IntegerRangeTypeDescriptor.extendedIntegers());
	}
}