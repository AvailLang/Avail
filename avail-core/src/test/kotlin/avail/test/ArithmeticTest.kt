/*
 * ArithmeticTest.kt
 * Copyright © 1993-2021, The Avail Foundation, LLC.
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
package avail.test

import avail.descriptor.numbers.A_Number
import avail.descriptor.numbers.A_Number.Companion.asBigInteger
import avail.descriptor.numbers.A_Number.Companion.bitShift
import avail.descriptor.numbers.A_Number.Companion.divideCanDestroy
import avail.descriptor.numbers.A_Number.Companion.extractFloat
import avail.descriptor.numbers.A_Number.Companion.extractLong
import avail.descriptor.numbers.A_Number.Companion.isPositive
import avail.descriptor.numbers.A_Number.Companion.lessOrEqual
import avail.descriptor.numbers.A_Number.Companion.minusCanDestroy
import avail.descriptor.numbers.A_Number.Companion.numericCompare
import avail.descriptor.numbers.A_Number.Companion.plusCanDestroy
import avail.descriptor.numbers.A_Number.Companion.timesCanDestroy
import avail.descriptor.numbers.DoubleDescriptor.Companion.doubleTruncatedToExtendedInteger
import avail.descriptor.numbers.DoubleDescriptor.Companion.fromDouble
import avail.descriptor.numbers.FloatDescriptor
import avail.descriptor.numbers.FloatDescriptor.Companion.fromFloat
import avail.descriptor.numbers.IntegerDescriptor
import avail.descriptor.numbers.IntegerDescriptor.Companion.fromBigInteger
import avail.descriptor.numbers.IntegerDescriptor.Companion.fromInt
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.AvailObject
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.ArgumentsProvider
import org.junit.jupiter.params.provider.ArgumentsSource
import org.junit.jupiter.params.provider.MethodSource
import java.math.BigInteger
import java.util.stream.Collectors
import java.util.stream.Stream
import kotlin.math.abs
import kotlin.math.pow

/**
 * Unit tests for the Avail arithmetic types.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
class ArithmeticTest
{
	/**
	 * Test some basic properties of [Avail floats][FloatDescriptor].
	 *
	 * @param f
	 *   The float to check.
	 */
	@ParameterizedTest
	@MethodSource("sampleFloats")
	fun testFloats(f: Float)
	{
		val availFloat = fromFloat(f)
		if (!java.lang.Float.isNaN(f))
		{
			Assertions.assertEquals(f, availFloat.extractFloat)
			val availInt = doubleTruncatedToExtendedInteger(f.toDouble())
			if (Long.MIN_VALUE <= f && f <= Long.MAX_VALUE)
			{
				Assertions.assertTrue(availInt.isLong)
				Assertions.assertEquals(availInt.extractLong, f.toLong())
			}
			if (java.lang.Float.isInfinite(f))
			{
				Assertions.assertFalse(availInt.isFinite)
				Assertions.assertEquals(f > 0, availInt.isPositive)
			}
			else
			{
				val lower = doubleTruncatedToExtendedInteger(
					f - 1.toDouble())
				val upper = doubleTruncatedToExtendedInteger(
					f + 1.toDouble())
				assert(lower.lessOrEqual(availInt)
							&& availInt.lessOrEqual(upper))
			}
		}
	}

	/**
	 * Test some basic properties of pairs of [Avail floats][FloatDescriptor].
	 *
	 * @param f1
	 *   The first of two floats to combine.
	 * @param f2
	 *   The second of two floats to combine.
	 */
	@ParameterizedTest
	@MethodSource("floatPairs")
	fun testFloatPairs(f1: Float, f2: Float)
	{
		val availF1 = fromFloat(f1)
		val availF2 = fromFloat(f2)
		assertEqualFloatsOrNan(
			f1 + f2, availF1.plusCanDestroy(availF2, false).extractFloat)
		assertEqualFloatsOrNan(
			f1 - f2, availF1.minusCanDestroy(availF2, false).extractFloat)
		assertEqualFloatsOrNan(
			f1 * f2, availF1.timesCanDestroy(availF2, false).extractFloat)
		assertEqualFloatsOrNan(
			f1 / f2, availF1.divideCanDestroy(availF2, false).extractFloat)
	}

	/**
	 * Test [BigInteger] to [Avail integer][IntegerDescriptor]
	 * conversion.
	 *
	 * @param bigIntString
	 *   A hexadecimal string to test.
	 */
	@ParameterizedTest
	@MethodSource("bigIntegerHexConversions")
	fun testFromBigInteger(bigIntString: String)
	{
		checkBigIntegerHexString(bigIntString)
		checkBigIntegerHexString("-$bigIntString")
	}

	/**
	 * Test basic bit shifting.
	 */
	@Test
	fun testBitShift()
	{
		checkBitShift(BigInteger.valueOf(2), 94)
		for (baseNeighborhoodString in baseNeighborhoodsForShifting)
		{
			val baseNeighborhood = BigInteger(baseNeighborhoodString, 16)
			Assertions.assertEquals(
				baseNeighborhood.toString(16), baseNeighborhoodString)
			for (offset in baseOffsetsForShifting)
			{
				val base = baseNeighborhood.add(
					BigInteger.valueOf(offset.toLong()))
				val negativeBase = base.negate()
				for (shiftAmount in shiftAmounts)
				{
					checkBitShift(base, shiftAmount)
					checkBitShift(base, -shiftAmount)
					checkBitShift(negativeBase, shiftAmount)
					checkBitShift(negativeBase, -shiftAmount)
				}
			}
		}
	}

	/**
	 * Test helper for producing the Cartesian product of sample
	 * [BigInteger]s to add and subtract pairwise.
	 */
	class BigIntegerPairs : ArgumentsProvider
	{
		override fun provideArguments(
			context: ExtensionContext): Stream<out Arguments>
		{
			return additionAndSubtractionCases.stream()
				.flatMap { v1: BigInteger? ->
					additionAndSubtractionCases.stream()
						.map { v2: BigInteger? -> Arguments.of(v1, v2) }
				}
		}
	}

	/**
	 * Test some addition and subtraction cases.
	 *
	 * @param a
	 *   The first BigInteger to use.
	 * @param b
	 *   The second BigInteger to use.
	 */
	@DisplayName("Integer addition and subtraction")
	@ParameterizedTest(name = "{displayName} => case={arguments}")
	@ArgumentsSource(BigIntegerPairs::class)
	fun testAdditionAndSubtraction(
		a: BigInteger,
		b: BigInteger)
	{
		val integerA = fromBigInteger(a)
		val integerB = fromBigInteger(b)
		Assertions.assertEquals(a, integerA.asBigInteger())
		Assertions.assertEquals(a.toString(), integerA.toString())
		val sum1 = a.add(b)
		val sum2 = integerA.plusCanDestroy(integerB, false)
		Assertions.assertEquals(sum1, sum2.asBigInteger())
		val diff1 = a.subtract(b)
		val diff2 = integerA.minusCanDestroy(integerB, false)
		Assertions.assertEquals(diff1, diff2.asBigInteger())

		// Assume the values don't overflow double's limits.
		val doubleA: Double = a.toDouble()
		val doubleB: Double = b.toDouble()
		val orderByInteger = integerA.numericCompare(integerB)
		val orderByDouble = fromDouble(doubleA).numericCompare(
			fromDouble(doubleB))
		// Skip the case where the doubles don't have enough precision to
		// represent two nearby different integers.
		if (integerA.equals(integerB) || doubleA != doubleB)
		{
			Assertions.assertEquals(orderByInteger, orderByDouble)
			// Also check it against Java double comparisons.
			Assertions.assertEquals(doubleA < doubleB,
									orderByDouble.isLess())
			Assertions.assertEquals(doubleA == doubleB,
									orderByDouble.isEqual())
			Assertions.assertEquals(doubleA > doubleB,
									orderByDouble.isMore())
			Assertions.assertEquals(
				java.lang.Double.isNaN(doubleA)
					|| java.lang.Double.isNaN(doubleB),
				orderByDouble.isIncomparable())
		}
	} // TODO: [MvG] Write tests for doubles.

	companion object
	{
		/**
		 * An array of doubles with which to test the arithmetic primitives.
		 */
		val sampleDoubles = doubleArrayOf(
			0.0,
			-0.0,
			1.0,
			1.1,
			0.1,
			0.01,  // Near 31/32 bit boundary
			1.0e9,
			3.0e9,
			5.0e9,  // Near 63/64 bit boundary
			5.0e18,
			9.0e18,
			18.0e18,
			19.0e18,  // Near minimum and maximum ranges *of floats*.
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
			Double.POSITIVE_INFINITY)

		/**
		 * The precision to which the basic calculations should conform. This
		 * should be treated as a fraction by which to multiply one of the
		 * results to get a scaled epsilon value, which the absolute value of
		 * the difference between the two values should be less than.
		 *
		 * In particular, use fifty bits of precision to compare doubles.
		 */
		val DoubleEpsilon = 0.5.pow(50.0)

		/** An array of floats with which to test arithmetic. */
		private val sampleFloats = listOf(
			0.0f,
			-0.0f,
			1.0f,
			1.1f,
			0.1f,
			0.01f,  // Near 31/32 bit boundary
			1.0e9f,
			3.0e9f,
			5.0e9f,  // Near 63/64 bit boundary
			5.0e18f,
			9.0e18f,
			18.0e18f,
			19.0e18f,  // Near minimum and maximum ranges of floats.
			-3.7e-37f,
			-3.7e37f,
			3.7e-37f,
			3.7e-37f,
			Math.PI.toFloat(),
			Math.E.toFloat(),
			Float.NaN,
			Float.NEGATIVE_INFINITY,
			Float.POSITIVE_INFINITY)

		/**
		 * Answer the sample floats list.
		 *
		 * @return
		 *   A [List] of [Float]s.
		 */
		@Suppress("unused")
		@JvmStatic
		fun sampleFloats(): List<Float> = sampleFloats

		/**
		 * Produce all pairs of sample Avail [floats][FloatDescriptor].
		 *
		 * @return
		 *   A stream of [Arguments], each containing two sample `float`s.
		 */
		@Suppress("unused")
		@JvmStatic
		fun floatPairs(): Stream<Arguments> = sampleFloats.stream()
			.flatMap { f1: Float? ->
				sampleFloats.stream()
					.map { f2: Float? -> Arguments.of(f1, f2) }
			}

		/**
		 * The precision to which the basic calculations should conform. This
		 * should be treated as a fraction by which to multiply one of the
		 * results to get a scaled epsilon value, which the absolute value of
		 * the difference between the two values should be less than.
		 *
		 * In particular, use twenty bits of precision to compare floats.
		 */
		private val FloatEpsilon = 0.5.pow(20.0).toFloat()

		/**
		 * Check that two floats are either both NaNs or neither Nans but within
		 * a reasonable epsilon of each other.
		 *
		 * @param a
		 *   A `float`.
		 * @param b
		 *   A `float` to compare the first one to.
		 */
		fun assertEqualFloatsOrNan(a: Float, b: Float)
		{
			Assertions.assertEquals(
				java.lang.Float.isNaN(a),
				java.lang.Float.isNaN(b))
			if (!java.lang.Float.isNaN(a))
			{
				if (java.lang.Float.floatToRawIntBits(a) != java
						.lang
						.Float
						.floatToRawIntBits(b))
				{
					Assertions.assertEquals(a, b, abs(b * FloatEpsilon))
				}
			}
		}

		/**
		 * Values with which to test [BigInteger] conversion. Their negations
		 * are also tested.
		 */
		@Suppress("SpellCheckingInspection")
		private val bigIntegerHexConversions: List<String> = listOf(
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
			"7FFFFFFFFFFFFFFFFFFF",
			"80000000000000000000", "80000000000000000001",
			"FFFFFFFFFFFFFFFFFFFF",
			"100000000000000000000", "100000000000000000001",
			"123456789ABCDEF0123456789ABCDEF")

		/**
		 * Answer the big integer hex strings to test.
		 *
		 * @return
		 *   a [List] of hexadecimal [String]s.
		 */
		@Suppress("unused")
		@JvmStatic
		fun bigIntegerHexConversions(): List<String> =
			bigIntegerHexConversions

		/**
		 * Check that the [BigInteger] produced from the provided hex string
		 * is correctly convertible to an [Avail integer][IntegerDescriptor].
		 *
		 * @param bigIntHexString
		 *   A string containing a hexadecimal integer.
		 */
		private fun checkBigIntegerHexString(bigIntHexString: String)
		{
			val bigInt = BigInteger(bigIntHexString, 16)
			Assertions.assertEquals(
				bigIntHexString.uppercase(),
				bigInt.toString(16).uppercase())
			val availInt: A_BasicObject = fromBigInteger(bigInt)
			Assertions.assertEquals(
				bigInt.toString().uppercase(),
				availInt.toString().uppercase())
		}

		/**
		 * Hex strings used as bases for shift testing. Negatives of these are
		 * also tested, as well as
		 * small perturbations from [baseOffsetsForShifting].
		 */
		val baseNeighborhoodsForShifting = arrayOf(
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
			"100000000000000000000000000000000")

		/**
		 * Perturbations of bases used for testing shifts.
		 */
		val baseOffsetsForShifting = intArrayOf(
			-2, -1, 0, 1, 2)

		/**
		 * Shift amounts. Negatives of these are also tested.
		 */
		val shiftAmounts = intArrayOf(
			0, 1, 2, 3, 4, 5, 6, 7, 8, 9,
			10, 11, 12, 13, 14, 15, 16, 17, 18, 19,
			20, 21, 22, 23, 24, 25, 26, 27, 28, 29,
			30, 31, 32, 33, 34, 35,
			40,
			50,
			60, 61, 62, 63, 64, 65, 66,
			94, 95, 96, 97, 98, 99,
			1000)

		/**
		 * Check that the [bit shift][A_Number.bitShift] operation defined in
		 * [AvailObject] produces a result that agrees with [BigInteger]'s
		 * implementation. Use the provided `BigInteger` base and left shift
		 * `int`.
		 *
		 * @param base
		 *   A BigInteger.
		 * @param leftShift
		 *   How much to shift it left. May be negative.
		 */
		private fun checkBitShift(
			base: BigInteger,
			leftShift: Int)
		{
			val availInt = fromBigInteger(base)
			val availShift: A_Number = fromInt(leftShift)
			val shiftedAvailInt = availInt.bitShift(availShift, true)
			val availInt2 = fromBigInteger(base)
			val shiftedAvailInt2 = availInt2.bitShift(availShift, false)
			Assertions.assertEquals(shiftedAvailInt, shiftedAvailInt2)
			val shiftedBigInt = base.shiftLeft(leftShift)
			Assertions.assertEquals(
				fromBigInteger(shiftedBigInt), shiftedAvailInt)
		}

		/**
		 * A list of BigIntegers, initially written in hexadecimal, to use for
		 * testing integer addition and subtraction.
		 */
		@Suppress("SpellCheckingInspection")
		val additionAndSubtractionCases: MutableList<BigInteger> = Stream.of(
			"0",
			"1",
			"-1",
			"2",
			"-2",
			"FF",
			"100",
			"7FFFFFFF",
			"80000000",
			"FFFFFFFF",
			"100000000",
			"7FFFFFFFFFFFFFFF",
			"8000000000000000",
			"FFFFFFFFFFFFFFFF",
			"10000000000000000",
			"7FFFFFFFFFFFFFFFFFFFFFFF",
			"800000000000000000000000",
			"FFFFFFFFFFFFFFFFFFFFFFFF",
			"1000000000000000000000000",
			"7FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF",
			"80000000000000000000000000000000",
			"FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF",
			"100000000000000000000000000000000",
			"123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0",
			"EAA401FE39EEE260E091AA5B5D67BCE2A9B1911E9CE0A825C4A44EAC7D56"
		).map { x: String? -> BigInteger(x, 16) }.collect(
			Collectors.toList())
	}
}
