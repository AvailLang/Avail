/**
 * ArithmeticTest.java
 * Copyright (c) 2011, Mark van Gulik.
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

package com.avail.test;

import static org.junit.Assert.*;
import org.junit.*;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.FloatDescriptor;
import com.avail.descriptor.DoubleDescriptor;

/**
 * Unit tests for the Avail arithmetic types.
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 */
public final class ArithmeticTest
{
	/**
	 * Test fixture: clear and then create all special objects well-known to the
	 * Avail runtime.
	 */
	@BeforeClass
	public static void initializeAllWellKnownObjects ()
	{
		AvailObject.clearAllWellKnownObjects();
		AvailObject.createAllWellKnownObjects();
	}

	/**
	 * Test fixture: clear all special objects.
	 */
	@AfterClass
	public static void clearAllWellKnownObjects ()
	{
		AvailObject.clearAllWellKnownObjects();
	}

	/**
	 * An array of doubles with which to test the arithmetic primitives.
	 */
	static double[] sampleDoubles =
	{
		0.0,
		-0.0,
		1.0,
		1.1,
		0.1,
		0.01,
		-3.7e-37,
		-3.7e37,
		3.7e-37,
		3.7e-37,
		Math.PI,
		Math.E,
		-1.234567890123456789e-300,
		-1.234567890123456789e300,
		1.234567890123456789e-300,
		1.234567890123456789e300,
		Double.NaN,
		Double.NEGATIVE_INFINITY,
		Double.POSITIVE_INFINITY
	};

	/**
	 * The precision to which the basic calculations should conform.  This
	 * should be treated as a fraction by which to multiply one of the results
	 * to get a scaled epsilon value, which the absolute value of the difference
	 * between the two values should be less than.
	 *
	 * <p>
	 * In particular, use fifty bits of precision to compare doubles.
	 * </p>
	 */
	static double DoubleEpsilon = Math.pow(0.5, 50.0);

	/**
	 * An array of floats with which to test the arithmetic primitives.
	 */
	static float[] sampleFloats =
	{
		0.0f,
		-0.0f,
		1.0f,
		1.1f,
		0.1f,
		0.01f,
		-3.7e-37f,
		-3.7e37f,
		3.7e-37f,
		3.7e-37f,
		(float)Math.PI,
		(float)Math.E,
		Float.NaN,
		Float.NEGATIVE_INFINITY,
		Float.POSITIVE_INFINITY
	};

	/**
	 * The precision to which the basic calculations should conform.  This
	 * should be treated as a fraction by which to multiply one of the results
	 * to get a scaled epsilon value, which the absolute value of the difference
	 * between the two values should be less than.
	 *
	 * <p>
	 * In particular, use twenty bits of precision to compare floats.
	 * </p>
	 */
	static float FloatEpsilon = (float)Math.pow(0.5, 20.0);

	/**
	 * Test some basic properties of {@linkplain FloatDescriptor Avail floats}.
	 */
	@Test
	public void testFloats ()
	{
		for (final float f1 : sampleFloats)
		{
			final AvailObject F1 = FloatDescriptor.fromFloat(f1);
			assertEquals(F1, F1);
			for (final float f2 : sampleFloats)
			{
				final AvailObject F2 = FloatDescriptor.fromFloat(f2);
				assertEquals(
					F1.plusCanDestroy(F2, false).extractFloat(),
					f1+f2,
					(f1+f2) * FloatEpsilon);
				assertEquals(
					F1.minusCanDestroy(F2, false).extractFloat(),
					f1-f2,
					(f1-f2) * FloatEpsilon);
				assertEquals(
					F1.timesCanDestroy(F2, false).extractFloat(),
					f1*f2,
					(f1*f2) * FloatEpsilon);
				assertEquals(
					F1.divideCanDestroy(F2, false).extractFloat(),
					f1/f2,
					(f1/f2) * FloatEpsilon);
			}
		}
	}
}
