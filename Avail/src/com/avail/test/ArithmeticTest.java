/**
 * ArithmeticTest.java
 * Copyright © 1993-2013, Mark van Gulik and Todd L Smith.
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
import java.math.BigInteger;
import org.junit.*;
import com.avail.descriptor.*;

/**
 * Unit tests for the Avail arithmetic types.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
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
	static final double[] sampleDoubles =
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
	static final double DoubleEpsilon = Math.pow(0.5, 50.0);

	/**
	 * An array of floats with which to test the arithmetic primitives.
	 */
	static final float[] sampleFloats =
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
	static final float FloatEpsilon = (float)Math.pow(0.5, 20.0);

	/**
	 * Test some basic properties of {@linkplain FloatDescriptor Avail floats}.
	 */
	@Test
	public void testFloats ()
	{
		for (final float f1 : sampleFloats)
		{
			final A_Number F1 = FloatDescriptor.fromFloat(f1);
			assertEquals(F1, F1);
			for (final float f2 : sampleFloats)
			{
				final A_Number F2 = FloatDescriptor.fromFloat(f2);
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

	/**
	 * Values with which to test {@link BigInteger} conversion.  Their negations
	 * are also tested.
	 */
	static final String [] bigIntegerHexConversions =
	{
		"1", "2",
		"7F", "80", "81",
		"FF", "100", "101", "102",
		"7FFF", "8000", "8001",
		"FFFF", "10000", "10001",
		"7FFFFF", "800000", "800001",
		"FFFFFF", "1000000", "1000001",
		"7FFFFFFF", "80000000", "80000001",
		"FFFFFFFF", "100000000", "100000001",
		"7FFFFFFFFF", "8000000000", "8000000001",
		"FFFFFFFFFF", "10000000000", "10000000001",
		"7FFFFFFFFFFF", "800000000000", "800000000001",
		"FFFFFFFFFFFF", "1000000000000", "1000000000001",
		"7FFFFFFFFFFFFF", "80000000000000", "80000000000001",
		"FFFFFFFFFFFFFF", "100000000000000", "100000000000001",
		"7FFFFFFFFFFFFFFF", "8000000000000000", "8000000000000001",
		"FFFFFFFFFFFFFFFF", "10000000000000000", "10000000000000001",
		"7FFFFFFFFFFFFFFFFF", "800000000000000000", "800000000000000001",
		"FFFFFFFFFFFFFFFFFF", "1000000000000000000", "1000000000000000001",
		"7FFFFFFFFFFFFFFFFFFF", "80000000000000000000", "80000000000000000001",
		"FFFFFFFFFFFFFFFFFFFF", "100000000000000000000", "100000000000000000001",
		"123456789ABCDEF0123456789ABCDEF"
	};

	/**
	 * Check that the {@link BigInteger} produced from the provided hex string
	 * is correctly convertible to an {@linkplain IntegerDescriptor Avail
	 * integer}.
	 *
	 * @param bigIntHexString
	 */
	private void checkBigIntegerHexString (final String bigIntHexString)
	{
		final BigInteger bigInt = new BigInteger(bigIntHexString, 16);
		assertEquals(
			bigIntHexString.toUpperCase(),
			bigInt.toString(16).toUpperCase());
		final A_BasicObject availInt =
			IntegerDescriptor.fromBigInteger(bigInt);
		assertEquals(
			bigInt.toString().toUpperCase(),
			availInt.toString().toUpperCase());
	}

	/**
	 * Test {@link BigInteger} to {@linkplain IntegerDescriptor Avail integer}
	 * conversion.
	 */
	@Test
	public void testFromBigInteger ()
	{
		checkBigIntegerHexString("0");
		for (final String bigIntString : bigIntegerHexConversions)
		{
			checkBigIntegerHexString(bigIntString);
			checkBigIntegerHexString("-" + bigIntString);
		}
	}

	/**
	 * Hex strings used as bases for shift testing.  Negatives of these are
	 * also tested, as well as {@linkplain #baseOffsetsForShifting small
	 * perturbations}.
	 */
	static final String [] baseNeighborhoodsForShifting =
	{
		"0",
		"80",
		"8000",
		"80000000",
		"100000000",
		"8000000000000000",
		"10000000000000000",
		"800000000000000000000000",
		"1000000000000000000000000",
		"80000000000000000000000000000000",
		"100000000000000000000000000000000",
	};

	/**
	 * Perturbations of bases used for testing shifts.
	 */
	static final int [] baseOffsetsForShifting =
	{
		-2, -1, 0, 1, 2
	};

	/**
	 * Shift amounts.  Negatives of these are also tested.
	 */
	static final int [] shiftAmounts =
	{
		0, 1, 2, 3, 4, 5, 6, 7, 8, 9,
		10, 11, 12, 13, 14, 15, 16, 17, 18, 19,
		20, 21, 22, 23, 24, 25, 26, 27, 28, 29,
		30, 31, 32, 33, 34, 35,
		40,
		50,
		60, 61, 62, 63, 64, 65, 66,
		94, 95, 96, 97, 98, 99,
		1000
	};

	/**
	 * Check that the {@linkplain AvailObject#bitShift(A_Number, boolean)
	 * bit shift} operation defined in {@link AvailObject} produces a result
	 * that agrees with {@link BigInteger}'s implementation.  Use the provided
	 * {@code BigInteger} base and left shift {@code int}.
	 *
	 * @param base A BigInteger
	 * @param leftShift How much to shift it left.  May be negative.
	 */
	private void checkBitShift (
		final BigInteger base,
		final int leftShift)
	{
		final BigInteger shiftedBigInt = base.shiftLeft(leftShift);
		final A_Number availInt = IntegerDescriptor.fromBigInteger(base);
		final A_Number availShift = IntegerDescriptor.fromInt(leftShift);
		final A_Number shiftedAvailInt = availInt.bitShift(availShift, true);
		final A_BasicObject availInt2 = IntegerDescriptor.fromBigInteger(base);
		final A_BasicObject shiftedAvailInt2 =
			availInt2.bitShift(availShift, false);
		assertEquals(shiftedAvailInt, shiftedAvailInt2);
		assertEquals(
			IntegerDescriptor.fromBigInteger(shiftedBigInt),
			shiftedAvailInt);
	}

	/**
	 * Test basic bit shifting.
	 */
	@Test
	public void testBitShift ()
	{
		for (final String baseNeighborhoodString : baseNeighborhoodsForShifting)
		{
			final BigInteger baseNeighborhood =
				new BigInteger(baseNeighborhoodString, 16);
			assertEquals(baseNeighborhood.toString(16), baseNeighborhoodString);
			for (final int offset : baseOffsetsForShifting)
			{
				final BigInteger base = baseNeighborhood.add(
					BigInteger.valueOf(offset));
				final BigInteger negativeBase = base.negate();
				for (final int shiftAmount : shiftAmounts)
				{
					checkBitShift(base, shiftAmount);
					checkBitShift(base, -shiftAmount);
					checkBitShift(negativeBase, shiftAmount);
					checkBitShift(negativeBase, -shiftAmount);
				}
			}

		}
	}

	// TODO: [MvG] Write tests for doubles.
}
