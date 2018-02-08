/*
 * P_DoubleTruncatedAsInteger.java
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
package com.avail.interpreter.primitive.doubles;

import com.avail.descriptor.A_Number;
import com.avail.descriptor.A_Type;
import com.avail.descriptor.DoubleDescriptor;
import com.avail.descriptor.IntegerDescriptor;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;
import com.avail.optimizer.jvm.ReferencedInGeneratedCode;

import static com.avail.descriptor.AbstractEnumerationTypeDescriptor
	.enumerationWith;
import static com.avail.descriptor.FunctionTypeDescriptor.functionType;
import static com.avail.descriptor.IntegerDescriptor.*;
import static com.avail.descriptor.IntegerRangeTypeDescriptor.extendedIntegers;
import static com.avail.descriptor.SetDescriptor.set;
import static com.avail.descriptor.TupleDescriptor.tuple;
import static com.avail.descriptor.TypeDescriptor.Types.DOUBLE;
import static com.avail.exceptions.AvailErrorCode
	.E_CANNOT_CONVERT_NOT_A_NUMBER_TO_INTEGER;
import static com.avail.interpreter.Primitive.Flag.CanFold;
import static com.avail.interpreter.Primitive.Flag.CanInline;
import static java.lang.Math.*;

/**
 * <strong>Primitive:</strong> Convert a {@linkplain DoubleDescriptor
 * double} to an {@linkplain IntegerDescriptor integer}, rounding towards
 * zero.
 */
public final class P_DoubleTruncatedAsInteger extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	@ReferencedInGeneratedCode
	public static final Primitive instance =
		new P_DoubleTruncatedAsInteger().init(
			1, CanFold, CanInline);

	@Override
	public Result attempt (
		final Interpreter interpreter)
	{
		interpreter.checkArgumentCount(1);
		final A_Number a = interpreter.argument(0);
		// Extract the top three 32-bit sections.  That guarantees 65 bits
		// of mantissa, which is more than a double actually captures.
		double d = a.extractDouble();
		if (Double.isNaN(d))
		{
			return interpreter.primitiveFailure(
				E_CANNOT_CONVERT_NOT_A_NUMBER_TO_INTEGER);
		}
		if (d >= Integer.MIN_VALUE && d <= Integer.MAX_VALUE)
		{
			// Common case -- it fits in an int.
			return interpreter.primitiveSuccess(fromInt((int) d));
		}
		final boolean neg = d < 0.0d;
		d = abs(d);
		final int exponent = getExponent(d);
		final int slots = (exponent + 31) >> 5;  // probably needs work
		A_Number out = createUninitializedInteger(slots);
		d = scalb(d, (1 - slots) << 5);
		for (int i = slots; i >= 1; --i)
		{
			final long intSlice = (int) d;
			out.rawUnsignedIntegerAtPut(i, (int) intSlice);
			d -= intSlice;
			d = scalb(d, 32);
		}
		out.trimExcessInts();
		if (neg)
		{
			out = zero().noFailMinusCanDestroy(out, true);
		}
		return interpreter.primitiveSuccess(out);
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return functionType(
			tuple(DOUBLE.o()),
			extendedIntegers());
	}

	@Override
	protected A_Type privateFailureVariableType ()
	{
		return enumerationWith(set(E_CANNOT_CONVERT_NOT_A_NUMBER_TO_INTEGER));
	}
}
