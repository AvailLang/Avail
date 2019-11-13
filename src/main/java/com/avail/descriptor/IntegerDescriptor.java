/*
 * IntegerDescriptor.java
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

package com.avail.descriptor;

import com.avail.annotations.AvailMethod;
import com.avail.descriptor.objects.A_BasicObject;
import com.avail.exceptions.ArithmeticException;
import com.avail.exceptions.AvailErrorCode;
import com.avail.exceptions.MarshalingException;
import com.avail.optimizer.jvm.ReferencedInGeneratedCode;
import com.avail.serialization.SerializerOperation;
import com.avail.utility.json.JSONWriter;

import javax.annotation.Nullable;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static com.avail.descriptor.AbstractNumberDescriptor.Order.EQUAL;
import static com.avail.descriptor.AbstractNumberDescriptor.Order.LESS;
import static com.avail.descriptor.AbstractNumberDescriptor.Order.MORE;
import static com.avail.descriptor.AvailObject.multiplier;
import static com.avail.descriptor.DoubleDescriptor.addDoubleAndIntegerCanDestroy;
import static com.avail.descriptor.DoubleDescriptor.compareDoubleAndInteger;
import static com.avail.descriptor.DoubleDescriptor.fromDoubleRecycling;
import static com.avail.descriptor.FloatDescriptor.fromFloatRecycling;
import static com.avail.descriptor.InfinityDescriptor.negativeInfinity;
import static com.avail.descriptor.InfinityDescriptor.positiveInfinity;
import static com.avail.descriptor.IntegerDescriptor.IntegerSlots.RAW_LONG_SLOTS_;
import static com.avail.descriptor.IntegerRangeTypeDescriptor.singleInteger;
import static com.avail.descriptor.Mutability.IMMUTABLE;
import static com.avail.descriptor.Mutability.MUTABLE;
import static com.avail.descriptor.Mutability.SHARED;
import static com.avail.descriptor.TypeDescriptor.Types.NUMBER;
import static com.avail.utility.Locks.lockWhile;
import static com.avail.utility.Locks.lockWhileNullable;
import static java.lang.Math.abs;
import static java.lang.Math.getExponent;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.Math.scalb;
import static java.util.Collections.singleton;

/**
 * An Avail {@linkplain IntegerDescriptor integer} is represented by a little
 * endian series of {@code int} slots.  The slots are really treated as
 * unsigned, except for the final slot which is considered signed.  The high bit
 * of the final slot (i.e., its sign bit) is the sign bit of the entire object.
 *
 * <p>
 * Avail integers should always occupy the fewest number of slots to
 * unambiguously indicate the represented integer.  A zero integer is
 * represented by a single int slot containing a zero {@code int}.  Any {@code
 * int} can be converted to an Avail integer by using a single slot, and any
 * {@code long} can be represented with at most two slots.
 * </p>
 *
 * <p>
 * Since Avail will soon (2015.09.28) require 8-byte alignment, its
 * representation has been updated to use 64-bit longs.  Rather than rewrite the
 * painstakingly difficult operations to uee 64-bit longs directly, we fetch and
 * update 32-bit ints using {@link AvailObject#intSlot(IntegerSlotsEnum, int)},
 * a temporary compatibility mechanism to make this global refactoring
 * tractable.  However, we also introduce the {@link #unusedIntsOfLastLong}
 * field in the descriptor to maintain the invariant that the number of occupied
 * 32-bit int fields is always minimized.
 * </p>
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class IntegerDescriptor
extends ExtendedIntegerDescriptor
{
	/**
	 * The layout of integer slots for my instances.
	 */
	public enum IntegerSlots
	implements IntegerSlotsEnum
	{
		/**
		 * <p>
		 * Avail integers should always occupy the fewest number of slots
		 * to unambiguously indicate the represented integer.  A zero integer is
		 * represented by a single slot containing a zero {@code int}.  Any
		 * {@code int} can be converted to an Avail integer by using a single
		 * slot, and any {@code long} can be represented with at most two slots.
		 * </p>
		 *
		 * <p>
		 * Thus, if the top slot is zero ({@code 0}), the second-from-top slot
		 * must have its upper bit set (fall in the range
		 * <span style="white-space:nowrap">{@code -0x80000000..-1}</span>),
		 * otherwise the last slot would be redundant.  Likewise, if the top
		 * slot is minus one ({code -1}), the second-from-top slot must have its
		 * upper bit clear (fall in the range <span
		 * style="white-space:nowrap">{@code 0..0x7FFFFFFF}</span>).
		 * </p>
		 */
		RAW_LONG_SLOTS_;
	}

	/**
	 * The number of ints of the last {@code long} that do not participate in
	 * the representation of the {@linkplain IntegerDescriptor integer}.
	 * Must be 0 or 1.
	 */
	private final byte unusedIntsOfLastLong;

	/**
	 * Answer the number of int slots in the passed integer object, which must
	 * not be an indirection.
	 *
	 * @param object The integer object.
	 * @return The number of occupied int slots in the object.
	 */
	public static int intCount (
		final AvailObject object)
	{
		final IntegerDescriptor descriptor =
			(IntegerDescriptor) object.descriptor();
		return (object.integerSlotsCount() << 1)
			- descriptor.unusedIntsOfLastLong;
	}

	@Override
	public void printObjectOnAvoidingIndent (
		final AvailObject object,
		final StringBuilder aStream,
		final IdentityHashMap<A_BasicObject, Void> recursionMap,
		final int indent)
	{
		if (object.isLong())
		{
			// The *vast* majority of uses, extends beyond 9 quintillion.
			aStream.append(object.extractLong());
		}
		else
		{
			A_Number magnitude = object;
			if (object.lessThan(zero()))
			{
				aStream.append('-');
				magnitude = zero().minusCanDestroy(object, false);
			}
			printBigInteger(magnitude, aStream, 0);
		}
	}

	/**
	 * A helper function for printing very large integers.
	 *
	 * @param magnitude
	 *        A positive integer to print.
	 * @param aStream
	 *        The {@link StringBuilder} on which to write this integer.
	 * @param minDigits
	 *        The minimum number of digits that must be printed, padding on the
	 *        left with zeroes as needed.
	 */
	private static void printBigInteger (
		final A_Number magnitude,
		final StringBuilder aStream,
		final int minDigits)
	{
		assert minDigits >= 0;
		if (magnitude.isLong())
		{
			// Use native printing.
			final long value = magnitude.extractLong();
			if (minDigits == 0)
			{
				aStream.append(value);
				return;
			}
			final String digits = Long.toString(value);
			for (int i = digits.length(); i < minDigits; i++)
			{
				aStream.append('0');
			}
			aStream.append(digits);
			return;
		}
		// It's bigger than a long, so divide and conquer.  Find the largest
		// (10^18)^(2^n) still less than the number, divide it to produce a
		// quotient and divisor, and print those recursively.
		int n = 0;
		A_Number nextDivisor = quintillionInteger;
		@Nullable A_Number previousDivisor;
		do
		{
			previousDivisor = nextDivisor;
			nextDivisor = cachedSquareOfQuintillion(++n);
		}
		while (nextDivisor.lessThan(magnitude));
		// We went one too far.  Decrement n and use previousDivisor.  Splitting
		// the number by dividing by previousDivisor assigns the low 18*(2^n)
		// digits to the remainder, and the rest to the quotient.
		n--;
		final int remainderDigits = 18 * (1<<n);
		assert minDigits == 0 || remainderDigits < minDigits;
		final A_Number quotient =
			magnitude.divideCanDestroy(previousDivisor, false);
		final A_Number remainder = magnitude.minusCanDestroy(
			quotient.timesCanDestroy(previousDivisor, false), false);
		printBigInteger(
			quotient,
			aStream,
			minDigits == 0 ? 0 : minDigits - remainderDigits);
		printBigInteger(
			remainder,
			aStream,
			remainderDigits);
	}

	@Override
	protected String o_NameForDebugger (final AvailObject object)
	{
		if (object.isLong())
		{
			final StringBuilder builder = new StringBuilder(80);
			builder.append("(Integer");
			if (isMutable())
			{
				builder.append("\u2133");
			}
			builder.append(") = ");
			final long longValue = object.extractLong();
			describeLong(
				longValue,
				intCount(object) == 1 ? 32 : 64,
				builder);
			builder.append(" = ");
			builder.append(longValue);
			return builder.toString();
		}
		return super.o_NameForDebugger(object);
	}

	@Override
	AvailObjectFieldHelper[] o_DescribeForDebugger (final AvailObject object)
	{
		if (object.isLong())
		{
			return new AvailObjectFieldHelper[0];
		}
		return super.o_DescribeForDebugger(object);
	}

	@Override @AvailMethod
	protected int o_RawSignedIntegerAt (final AvailObject object, final int subscript)
	{
		return object.intSlot(RAW_LONG_SLOTS_, subscript);
	}

	@Override @AvailMethod
	protected void o_RawSignedIntegerAtPut (
		final AvailObject object,
		final int subscript,
		final int value)
	{
		object.setIntSlot(RAW_LONG_SLOTS_, subscript, value);
	}

	@Override @AvailMethod
	protected boolean o_Equals (final AvailObject object, final A_BasicObject another)
	{
		return another.equalsInteger(object);
	}

	/**
	 * Compare two integers for equality.
	 */
	@Override @AvailMethod
	protected boolean o_EqualsInteger (
		final AvailObject object,
		final AvailObject anAvailInteger)
	{
		final int slotsCount = intCount(object);
		if (slotsCount != intCount(anAvailInteger))
		{
			// Assume integers being compared are always normalized (trimmed).
			return false;
		}
		for (int i = 1; i <= slotsCount; i++)
		{
			final int a = object.intSlot(RAW_LONG_SLOTS_, i);
			final int b = anAvailInteger.rawSignedIntegerAt(i);
			if (a != b)
			{
				return false;
			}
		}
		return true;
	}

	/**
	 * Check if this is an integer whose value equals the given int.
	 */
	@Override @AvailMethod
	protected boolean o_EqualsInt (
		final AvailObject object,
		final int theInt)
	{
		// Assume it's normalized (trimmed).
		return intCount(object) == 1
			&& object.intSlot(RAW_LONG_SLOTS_, 1) == theInt;
	}

	@Override @AvailMethod
	protected boolean o_IsInstanceOfKind (
		final AvailObject object,
		final A_Type aType)
	{
		if (aType.isSupertypeOfPrimitiveTypeEnum(NUMBER))
		{
			return true;
		}
		if (!aType.isIntegerRangeType())
		{
			return false;
		}
		if (aType.upperInclusive())
		{
			if (!object.lessOrEqual(aType.upperBound()))
			{
				return false;
			}
		}
		else if (!object.lessThan(aType.upperBound()))
		{
			return false;
		}

		if (aType.lowerInclusive())
		{
			return aType.lowerBound().lessOrEqual(object);
		}
		return aType.lowerBound().lessThan(object);
	}

	@Override @AvailMethod
	Order o_NumericCompare (
		final AvailObject object,
		final A_Number another)
	{
		return another.numericCompareToInteger(object).reverse();
	}

	@Override @AvailMethod
	protected int o_Hash (final AvailObject object)
	{
		if (object.isUnsignedByte())
		{
			return hashOfUnsignedByte(object.extractUnsignedByte());
		}
		return computeHashOfIntegerObject(object);
	}

	@Override @AvailMethod
	protected boolean o_IsFinite (final AvailObject object)
	{
		return true;
	}

	@Override @AvailMethod
	protected A_Type o_Kind (final AvailObject object)
	{
		object.makeImmutable();
		return singleInteger(object);
	}

	@Override @AvailMethod
	protected A_Number o_DivideCanDestroy (
		final AvailObject object,
		final A_Number aNumber,
		final boolean canDestroy)
	{
		return aNumber.divideIntoIntegerCanDestroy(object, canDestroy);
	}

	@Override @AvailMethod
	protected A_Number o_MinusCanDestroy (
		final AvailObject object,
		final A_Number aNumber,
		final boolean canDestroy)
	{
		return aNumber.subtractFromIntegerCanDestroy(object, canDestroy);
	}

	@Override @AvailMethod
	protected A_Number o_PlusCanDestroy (
		final AvailObject object,
		final A_Number aNumber,
		final boolean canDestroy)
	{
		return aNumber.addToIntegerCanDestroy(object, canDestroy);
	}

	@Override @AvailMethod
	protected A_Number o_TimesCanDestroy (
		final AvailObject object,
		final A_Number aNumber,
		final boolean canDestroy)
	{
		return aNumber.multiplyByIntegerCanDestroy(object, canDestroy);
	}

	@Override @AvailMethod
	protected boolean o_IsNybble (final AvailObject object)
	{
		if (intCount(object) > 1)
		{
			return false;
		}
		final int value = object.extractInt();
		return (value & 15) == value;
	}

	@Override @AvailMethod
	protected boolean o_IsSignedByte (final AvailObject object)
	{
		if (intCount(object) > 1)
		{
			return false;
		}
		final int value = object.extractInt();
		return value == (byte) value;
	}

	@Override @AvailMethod
	protected boolean o_IsUnsignedByte (final AvailObject object)
	{
		if (intCount(object) > 1)
		{
			return false;
		}
		final int value = object.extractInt();
		return (value & 255) == value;
	}

	@Override @AvailMethod
	protected boolean o_IsSignedShort (final AvailObject object)
	{
		if (intCount(object) > 1)
		{
			return false;
		}
		final int value = object.extractInt();
		return value == (short) value;
	}

	@Override @AvailMethod
	protected boolean o_IsUnsignedShort (final AvailObject object)
	{
		if (intCount(object) > 1)
		{
			return false;
		}
		final int value = object.extractInt();
		return (value & 65535) == value;
	}

	@Override @AvailMethod
	protected boolean o_IsInt (final AvailObject object)
	{
		return intCount(object) == 1;
	}

	@Override @AvailMethod
	protected boolean o_IsLong (final AvailObject object)
	{
		return intCount(object) <= 2;
	}

	@Override @AvailMethod
	byte o_ExtractNybble (final AvailObject object)
	{
		assert intCount(object) == 1;
		final int value = object.rawSignedIntegerAt(1);
		assert value == (value & 15) : "Value is out of range for a nybble";
		return (byte) value;
	}

	@Override @AvailMethod
	byte o_ExtractSignedByte (final AvailObject object)
	{
		assert intCount(object) == 1;
		final int value = object.rawSignedIntegerAt(1);
		assert value == (byte) value : "Value is out of range for a byte";
		return (byte) value;
	}

	@Override @AvailMethod
	short o_ExtractUnsignedByte (final AvailObject object)
	{
		assert intCount(object) == 1;
		final int value = object.rawSignedIntegerAt(1);
		assert value == (value & 255) : "Value is out of range for a byte";
		return (short) value;
	}

	@Override @AvailMethod
	short o_ExtractSignedShort (final AvailObject object)
	{
		assert intCount(object) == 1;
		final int value = object.rawSignedIntegerAt(1);
		assert value == (short) value : "Value is out of range for a short";
		return (short) value;
	}

	@Override @AvailMethod
	protected int o_ExtractUnsignedShort (final AvailObject object)
	{
		assert intCount(object) == 1;
		final int value = object.rawSignedIntegerAt(1);
		assert value == (value & 65535) : "Value is out of range for a short";
		return value;
	}

	@Override @AvailMethod
	protected int o_ExtractInt (final AvailObject object)
	{
		assert intCount(object) == 1 : "Integer value out of bounds";
		return object.rawSignedIntegerAt(1);
	}

	@Override @AvailMethod
	long o_ExtractLong (final AvailObject object)
	{
		assert
			intCount(object) >= 1 && intCount(object) <= 2
			: "Integer value out of bounds";

		if (intCount(object) == 1)
		{
			return object.rawSignedIntegerAt(1);
		}

		long value = object.rawSignedIntegerAt(1) & 0xffffffffL;
		value |= ((long) object.rawSignedIntegerAt(2)) << 32L;
		return value;
	}

	@Override @AvailMethod
	float o_ExtractFloat (final AvailObject object)
	{
		return (float) extractDoubleScaled(object, 0);
	}

	@Override @AvailMethod
	double o_ExtractDouble (final AvailObject object)
	{
		return extractDoubleScaled(object, 0);
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * Manually constructed accessor method.  Access the quad-byte using the
	 * native byte-ordering, but using little endian between quad-bytes (i.e.,
	 * least significant quad comes first).
	 * </p>
	 */
	@Override @AvailMethod
	long o_RawUnsignedIntegerAt (
		final AvailObject object,
		final int subscript)
	{
		final int signedInt = object.intSlot(RAW_LONG_SLOTS_, subscript);
		return signedInt & 0xFFFFFFFFL;
	}

	/**
	 * Manually constructed accessor method.  Overwrite the quad-byte using the
	 * native byte-ordering, but using little endian between quad-bytes (i.e.,
	 * least significant quad comes first).
	 */
	@Override @AvailMethod
	protected void o_RawUnsignedIntegerAtPut (
		final AvailObject object,
		final int subscript,
		final int value)
	{
		object.setIntSlot(RAW_LONG_SLOTS_, subscript, value);
	}

	@Override @AvailMethod
	protected void o_TrimExcessInts (final AvailObject object)
	{
		// Remove any redundant ints from my end.  Since I'm stored in Little
		// Endian representation, I can simply be truncated with no need to
		// shift data around.
		assert isMutable();
		int size = intCount(object);
		if (size > 1)
		{
			if (object.intSlot(RAW_LONG_SLOTS_, size) >= 0)
			{
				while (size > 1
						&& object.intSlot(RAW_LONG_SLOTS_, size) == 0
						&& object.intSlot(RAW_LONG_SLOTS_, size - 1) >= 0)
				{
					size--;
					if ((size & 1) == 0)
					{
						// Remove an entire long.
						object.truncateWithFillerForNewIntegerSlotsCount(
							(size + 1) >> 1);
					}
					else
					{
						// Safety: Zero the bytes if the size is now odd.
						object.setIntSlot(RAW_LONG_SLOTS_, size + 1, 0);
					}
				}
			}
			else
			{
				while (size > 1
						&& object.intSlot(RAW_LONG_SLOTS_, size) == -1
						&& object.intSlot(RAW_LONG_SLOTS_, size - 1) < 0)
				{
					size--;
					if ((size & 1) == 0)
					{
						// Remove an entire long.
						object.truncateWithFillerForNewIntegerSlotsCount(
							(size + 1) >> 1);
					}
					else
					{
						// Safety: Zero the bytes if the size is now odd.
						object.setIntSlot(RAW_LONG_SLOTS_, size + 1, 0);
					}
				}
			}
			object.descriptor = mutableFor(size);
		}
	}

	@Override @AvailMethod
	protected A_Number o_AddToInfinityCanDestroy (
		final AvailObject object,
		final Sign sign,
		final boolean canDestroy)
	{
		return sign == Sign.POSITIVE ? positiveInfinity() : negativeInfinity();
	}

	/**
	 * Choose the most spacious mutable {@linkplain AvailObject argument}.
	 *
	 * @param object
	 *        An Avail integer.
	 * @param another
	 *        The number of ints in the representation of object.
	 * @param objectIntCount
	 *        The size of object in ints.
	 * @param anotherIntCount
	 *        The number of ints in the representation of another.
	 * @return One of the arguments, or {@code null} if neither argument was
	 *         suitable.
	 */
	private @Nullable AvailObject largerMutableOf (
		final AvailObject object,
		final AvailObject another,
		final int objectIntCount,
		final int anotherIntCount)
	{
		final @Nullable AvailObject output;
		if (objectIntCount == anotherIntCount)
		{
			output =
				isMutable()
					? object
					: another.descriptor().isMutable()
						? another
						: null;
		}
		else if (objectIntCount > anotherIntCount)
		{
			output = isMutable() ? object : null;
		}
		else
		{
			output = another.descriptor().isMutable() ? another : null;
		}
		return output;
	}

	@Override @AvailMethod
	protected A_Number o_AddToIntegerCanDestroy (
		final AvailObject object,
		final AvailObject anInteger,
		final boolean canDestroy)
	{
		// This routine would be much quicker with access to machine carry
		// flags, but Java doesn't let us actually go down to the metal (nor do
		// C and C++). Our best recourse without reverting to assembly language
		// is to use 64-bit.
		final int objectSize = intCount(object);
		final int anIntegerSize = intCount(anInteger);
		@Nullable AvailObject output = canDestroy
			? largerMutableOf(object, anInteger, objectSize, anIntegerSize)
			: null;
		if (objectSize == 1 && anIntegerSize == 1)
		{
			// See if the (signed) sum will fit in 32 bits, the most common case
			// by far.
			final long sum = object.extractLong() + anInteger.extractLong();
			if (sum == (int) sum)
			{
				// Yes, it fits.  Clobber one of the inputs, or create a new
				// object if they were both immutable...
				if (output == null)
				{
					output = createUninitializedInteger(1);
				}
				assert intCount(output) == 1;
				output.rawSignedIntegerAtPut(1, (int) sum);
				return output;
			}
			// Doesn't fit in 32 bits; use two 32-bit words.
			return fromLong(sum);
		}
		// Set estimatedSize to the max of the input sizes. There will only
		// rarely be an overflow and at most by one cell. Underflows should also
		// be pretty rare, and they're handled by output.trimExcessInts().
		if (output == null)
		{
			output = createUninitializedInteger(max(objectSize, anIntegerSize));
		}
		final int outputSize = intCount(output);
		final long extendedObject =
			object.rawSignedIntegerAt(objectSize) >> 31 & 0xFFFFFFFFL;
		final long extendedAnInteger =
			anInteger.rawSignedIntegerAt(anIntegerSize) >> 31 & 0xFFFFFFFFL;
		long partial = 0;
		int lastInt = 0;
		// The object is always big enough to store the max of the number of
		// quads from each input, so after the loop we can check partial and the
		// upper bit of the result to see if another quad needs to be appended.
		for (int i = 1; i <= outputSize; i++)
		{
			partial += i > objectSize
				? extendedObject
				: object.rawUnsignedIntegerAt(i);
			partial += i > anIntegerSize
				? extendedAnInteger
				: anInteger.rawUnsignedIntegerAt(i);
			lastInt = (int) partial;
			output.rawSignedIntegerAtPut(i, lastInt);
			partial >>>= 32;
		}
		partial += extendedObject + extendedAnInteger;
		if (lastInt >> 31 != (int) partial)
		{
			// Top bit of last word no longer agrees with sign of result. Extend
			// it.
			final AvailObject newOutput =
				createUninitializedInteger(outputSize + 1);
			for (int i = 1; i <= outputSize; i++)
			{
				newOutput.setIntSlot(
					RAW_LONG_SLOTS_,
					i,
					output.intSlot(RAW_LONG_SLOTS_, i));
			}
			newOutput.rawSignedIntegerAtPut(outputSize + 1, (int) partial);
			// No need to truncate it in this case.
			return newOutput;
		}
		output.trimExcessInts();
		return output;
	}

	@Override
	protected A_Number o_AddToDoubleCanDestroy (
		final AvailObject object,
		final A_Number doubleObject,
		final boolean canDestroy)
	{
		final double d = addDoubleAndIntegerCanDestroy(
			doubleObject.extractDouble(), object, canDestroy);
		return fromDoubleRecycling(d, doubleObject, canDestroy);
	}

	@Override
	protected A_Number o_AddToFloatCanDestroy (
		final AvailObject object,
		final A_Number floatObject,
		final boolean canDestroy)
	{
		final double d = addDoubleAndIntegerCanDestroy(
			floatObject.extractDouble(), object, canDestroy);
		return fromFloatRecycling((float) d, floatObject, canDestroy);
	}

	@Override @AvailMethod
	protected A_Number o_DivideIntoInfinityCanDestroy (
		final AvailObject object,
		final Sign sign,
		final boolean canDestroy)
	{
		if (object.equals(zero()))
		{
			throw new ArithmeticException(
				AvailErrorCode.E_CANNOT_DIVIDE_BY_ZERO);
		}
		return object.greaterThan(zero()) ^ (sign == Sign.POSITIVE)
			? negativeInfinity()
			: positiveInfinity();
	}

	/**
	 * Choose a mutable {@linkplain AvailObject argument}.
	 *
	 * @param object
	 *        An Avail integer whose descriptor is the receiver.
	 * @param another
	 *        An integer.
	 * @return One of the arguments, or {@code null} if neither argument is
	 *         mutable.
	 */
	private @Nullable AvailObject mutableOf (
		final AvailObject object,
		final AvailObject another)
	{
		if (isMutable())
		{
			return object;
		}
		if (another.descriptor().isMutable())
		{
			return another;
		}
		return null;
	}

	@Override @AvailMethod
	protected A_Number o_DivideIntoIntegerCanDestroy (
		final AvailObject object,
		final AvailObject anInteger,
		final boolean canDestroy)
	{
		// Compute anInteger / object. Round towards negative infinity.
		if (object.equals(zero()))
		{
			throw new ArithmeticException(
				AvailErrorCode.E_CANNOT_DIVIDE_BY_ZERO);
		}
		if (anInteger.equals(zero()))
		{
			return anInteger;
		}
		if (object.lessThan(zero()))
		{
			// a/o for o<0:  use (-a/-o)
			final A_Number positiveNumerator =
				anInteger.subtractFromIntegerCanDestroy(zero, canDestroy);
			return object.subtractFromIntegerCanDestroy(zero, canDestroy)
				.divideIntoIntegerCanDestroy(
					(AvailObject) positiveNumerator, canDestroy);
		}
		if (anInteger.lessThan(zero()))
		{
			// a/o for a<0, o>0:  use -1-(-1-a)/o
			// e.g., -9/5  = -1-(-1+9)/5  = -1-8/5 = -2
			// e.g., -10/5 = -1-(-1+10)/5 = -1-9/5 = -2
			// e.g., -11/5 = -1-(-1+11)/5 = -1-10/5 = -3
			final A_Number minusOneMinusA =
				negativeOne().minusCanDestroy(anInteger, false);
			final A_Number quotient =
				minusOneMinusA.divideCanDestroy(object, true);
			return negativeOne().minusCanDestroy(quotient, true);
		}
		if (object.isLong() && anInteger.isLong())
		{
			final long numerator = anInteger.extractLong();
			final long denominator = object.extractLong();
			if (denominator == -1L)
			{
				// This also handles the only overflow case, -2^63/-1.
				return zero().minusCanDestroy(anInteger, canDestroy);
			}
			final long quotient = numerator / denominator;
			// Clobber one of the inputs, or create a new single-long object if
			// they were both immutable...
			final @Nullable AvailObject output = canDestroy
				? mutableOf(object, anInteger)
				: null;
			if (output != null)
			{
				// Eventually we can just do a single long write.
				output.setIntSlot(RAW_LONG_SLOTS_, 1, (int) quotient);
				output.setIntSlot(RAW_LONG_SLOTS_, 2, (int) (quotient >> 32L));
				// Distinguish between a long-sized and int-sized integer.
				output.descriptor = mutableFor(
					quotient == (int) quotient ? 1 : 2);
				return output;
			}
			return fromLong(quotient);
		}

		// For simplicity and efficiency, fall back on Java's BigInteger
		// implementation.
		final BigInteger numeratorBigInt = anInteger.asBigInteger();
		final BigInteger denominatorBigInt = object.asBigInteger();
		final BigInteger quotient = numeratorBigInt.divide(denominatorBigInt);
		return fromBigInteger(quotient);
	}

	@Override
	public A_Number o_DivideIntoDoubleCanDestroy (
		final AvailObject object,
		final A_Number doubleObject,
		final boolean canDestroy)
	{
		// This one is tricky.  The integer might be bigger than the maximum
		// double, but the product with a very small double may produce a value
		// that is still in range.  Avoid the overflow in that case by working
		// with a scaled down version of the integer: target a "significand"
		// below about 2^96.
		final int scale = max(intCount(object) - 4, 0) << 5;
		final double scaledIntAsDouble = extractDoubleScaled(object, scale);
		assert !Double.isInfinite(scaledIntAsDouble);
		final double scaledQuotient =
			doubleObject.extractDouble() / scaledIntAsDouble;
		final double quotient = scalb(scaledQuotient, scale);
		return fromDoubleRecycling(quotient, doubleObject, canDestroy);
	}

	@Override
	public A_Number o_DivideIntoFloatCanDestroy (
		final AvailObject object,
		final A_Number floatObject,
		final boolean canDestroy)
	{
		// This one is tricky.  The integer might be bigger than the maximum
		// double, but the product with a very small double may produce a value
		// that is still in range of a float.  Actually, I doubt this is
		// possible, but it's easier to just make it match the double case.
		// Avoid the overflow by working with a scaled down version of the
		// integer: target a "significand" below about 2^96.
		final int scale = max(intCount(object) - 4, 0) << 5;
		final double scaledIntAsDouble = extractDoubleScaled(object, scale);
		assert !Double.isInfinite(scaledIntAsDouble);
		final double scaledQuotient =
			floatObject.extractDouble() / scaledIntAsDouble;
		final double quotient = scalb(scaledQuotient, scale);
		return fromFloatRecycling((float) quotient, floatObject, canDestroy);
	}

	@Override @AvailMethod
	protected A_Number o_MultiplyByInfinityCanDestroy (
		final AvailObject object,
		final Sign sign,
		final boolean canDestroy)
	{
		if (object.equals(zero()))
		{
			throw new ArithmeticException(
				AvailErrorCode.E_CANNOT_MULTIPLY_ZERO_AND_INFINITY);
		}
		return object.greaterThan(zero()) ^ (sign == Sign.POSITIVE)
			? negativeInfinity()
			: positiveInfinity();
	}

	@Override @AvailMethod
	protected A_Number o_MultiplyByIntegerCanDestroy (
		final AvailObject object,
		final AvailObject anInteger,
		final boolean canDestroy)
	{
		@Nullable AvailObject output;
		if (object.isInt() && anInteger.isInt())
		{
			// See if the (signed) product will fit in 32 bits, the most common
			// case by far.
			final long prod = ((long) object.extractInt())
				* ((long) anInteger.extractInt());
			if (prod == (int) prod)
			{
				// Yes, it fits.  Clobber one of the inputs, or create a new
				// int-sized object if they were both immutable...
				output = canDestroy ? mutableOf(object, anInteger) : null;
				if (output == null)
				{
					output = createUninitializedInteger(1);
				}
				assert intCount(output) == 1;
				output.rawSignedIntegerAtPut(1, (int) prod);
				return output;
			}
			// Doesn't fit.  Worst case: -2^31 * -2^31 = +2^62, which fits in 64
			// bits, even with the sign.
			return fromLong(prod);
		}
		final int size1 = intCount(object);
		final int size2 = intCount(anInteger);
		// The following is a safe upper bound.  See below.
		final int targetSize = size1 + size2;
		output = createUninitializedInteger(targetSize);
		final long extension1 =
			(object.rawSignedIntegerAt(size1) >> 31) & 0xFFFFFFFFL;
		final long extension2 =
			(anInteger.rawSignedIntegerAt(size2) >> 31) & 0xFFFFFFFFL;

		// We can't recycle storage quite as easily here as we did for addition
		// and subtraction, because the intermediate sum would be clobbering one
		// of the multiplicands that we (may) need to scan again.  So always
		// allocate the new object.  The product will always fit in N+M cells if
		// the multiplicands fit in sizes N and M cells.  For proof, consider
		// the worst case (using bytes for the example).  In hex,
		// -80 * -80 = +4000, which fits.  Also, 7F*7F = 3F01, and
		// 7F*-80 = -3F80.  So no additional padding is necessary.  The scheme
		// we will use is to compute each word of the result, low to high, using
		// a carry of two words.  All quantities are treated as unsigned, but
		// the multiplicands are sign-extended as needed.  Multiplying two
		// one-word multiplicands yields a two word result, so we need to use
		// three words to properly hold the carry (it would take more than four
		// billion words to overflow this, and only one billion words are
		// addressable on a 32-bit machine).  The three-word intermediate value
		// is handled as two two-word accumulators, A and B.  B is considered
		// shifted by a word (to the left).  The high word of A is added to the
		// low word of B (with carry to the high word of B), and then the high
		// word of A is cleared.  This "shifts the load" to B for holding big
		// numbers without affecting their sum.  When a new two-word value needs
		// to be added in, this trick is employed, followed by directly adding
		// the two-word value to A, as long as we can ensure the *addition*
		// won't overflow, which is the case if the two-word value is an
		// unsigned product of two one-word values.  Since FF*FF=FE01, we can
		// safely add this to 00FF (getting FF00) without overflow.  Pretty
		// slick, huh?

		long low = 0;
		long high = 0;
		for (int i = 1; i <= targetSize; i++)
		{
			for (int k = 1, m = i; k <= i; k++, m--)
			{
				final long multiplicand1 = k > size1
					? extension1
					: object.rawUnsignedIntegerAt(k);
				final long multiplicand2 = m > size2
					? extension2
					: anInteger.rawUnsignedIntegerAt(m);
				low += multiplicand1 * multiplicand2;
				// Add upper of low to high.
				high += low >>> 32;
				// Subtract the same amount from low (clear upper word).
				low &= 0xFFFFFFFFL;
			}
			output.rawSignedIntegerAtPut(i, (int) low);
			low = high & 0xFFFFFFFFL;
			high >>>= 32;
		}
		// We can safely ignore any remaining bits from the partial products.
		output.trimExcessInts();
		return output;
	}

	@Override
	public A_Number o_MultiplyByDoubleCanDestroy (
		final AvailObject object,
		final A_Number doubleObject,
		final boolean canDestroy)
	{
		// This one is tricky.  The integer might be bigger than the maximum
		// double, but the product with a very small double may produce a value
		// that is still in range.  Avoid the overflow in that case by working
		// with a scaled down version of the integer: target a "significand"
		// below about 2^96.
		final int scale = max(intCount(object) - 4, 0) << 5;
		final double scaledIntAsDouble = extractDoubleScaled(object, scale);
		assert !Double.isInfinite(scaledIntAsDouble);
		final double scaledProduct =
			doubleObject.extractDouble() * scaledIntAsDouble;
		final double product = scalb(scaledProduct, scale);
		return fromDoubleRecycling(product, doubleObject, canDestroy);
	}

	@Override
	public A_Number o_MultiplyByFloatCanDestroy (
		final AvailObject object,
		final A_Number floatObject,
		final boolean canDestroy)
	{
		// This one is tricky.  The integer might be bigger than the maximum
		// double, but the product with a very small double may produce a value
		// that is still in range of a float.  Actually, I doubt this is
		// possible, but it's easier to just make it match the double case.
		// Avoid the overflow by working with a scaled down version of the
		// integer: target a "significand" below about 2^96.
		final int scale = max(intCount(object) - 4, 0) << 5;
		final double scaledIntAsDouble = extractDoubleScaled(object, scale);
		assert !Double.isInfinite(scaledIntAsDouble);
		final double scaledProduct =
			floatObject.extractDouble() * scaledIntAsDouble;
		final double product = scalb(scaledProduct, scale);
		return fromFloatRecycling((float) product, floatObject, canDestroy);
	}

	@Override @AvailMethod
	protected A_Number o_SubtractFromInfinityCanDestroy (
		final AvailObject object,
		final Sign sign,
		final boolean canDestroy)
	{
		return sign == Sign.POSITIVE
			? positiveInfinity()
			: negativeInfinity();
	}

	@Override @AvailMethod
	protected A_Number o_SubtractFromIntegerCanDestroy (
		final AvailObject object,
		final AvailObject anInteger,
		final boolean canDestroy)
	{
		// This routine would be much quicker with access to machine carry
		// flags, but Java doesn't let us actually go down to the metal (nor do
		// C and C++). Our best recourse without reverting to assembly language
		// is to use 64-bit arithmetic.
		final int objectSize = intCount(object);
		final int anIntegerSize = intCount(anInteger);
		@Nullable AvailObject output = canDestroy
			? largerMutableOf(object, anInteger, objectSize, anIntegerSize)
			: null;
		if (objectSize == 1 && anIntegerSize == 1)
		{
			// See if the (signed) difference will fit in 32 bits, the most
			// common case by far.
			final long diff = anInteger.extractLong() - object.extractLong();
			if (diff == (int) diff)
			{
				// Yes, it fits. Clobber one of the inputs, or create a new
				// object if they were both immutable...
				if (output == null)
				{
					output = createUninitializedInteger(1);
				}
				assert intCount(output) == 1;
				output.rawSignedIntegerAtPut(1, (int) diff);
				return output;
			}
			// Doesn't fit in 32 bits; use two 32-bit words.
			output = createUninitializedInteger(2);
			output.rawSignedIntegerAtPut(1, (int) diff);
			output.rawSignedIntegerAtPut(2, (int) (diff>>32));
			return output;
		}
		// Set estimatedSize to the max of the input sizes. There will only
		// rarely be an overflow and at most by one cell. Underflows should also
		// be pretty rare, and they're handled by output.trimExcessInts().
		if (output == null)
		{
			output = createUninitializedInteger(max(objectSize, anIntegerSize));
		}
		final int outputSize = intCount(output);
		final long extendedObject =
			(object.rawSignedIntegerAt(objectSize) >> 31) & 0xFFFFFFFFL;
		final long extendedAnInteger =
			(anInteger.rawSignedIntegerAt(anIntegerSize) >> 31) & 0xFFFFFFFFL;
		long partial = 1;
		int lastInt = 0;
		// The object is always big enough to store the max of the number of
		// quads from each input, so after the loop we can check partial and the
		// upper bit of the result to see if another quad needs to be appended.
		for (int i = 1; i <= outputSize; i++)
		{
			partial += i > anIntegerSize
				? extendedAnInteger
				: anInteger.rawUnsignedIntegerAt(i);
			partial += (i > objectSize
				? extendedObject
				: object.rawUnsignedIntegerAt(i)) ^ 0xFFFFFFFFL;
			lastInt = (int) partial;
			output.rawSignedIntegerAtPut(i, lastInt);
			partial >>>= 32;
		}
		partial += extendedAnInteger + (extendedObject ^ 0xFFFFFFFFL);
		if (lastInt >> 31 != (int) partial)
		{
			// Top bit of last word no longer agrees with sign of result.
			// Extend it.
			final AvailObject newOutput = createUninitializedInteger(outputSize + 1);
			for (int i = 1; i <= outputSize; i++)
			{
				newOutput.rawSignedIntegerAtPut(
					i, output.rawSignedIntegerAt(i));
			}
			newOutput.rawSignedIntegerAtPut(outputSize + 1, (int) partial);
			// No need to truncate it in this case.
			return newOutput;
		}
		output.trimExcessInts();
		return output;
	}

	@Override
	public A_Number o_SubtractFromDoubleCanDestroy (
		final AvailObject object,
		final A_Number doubleObject,
		final boolean canDestroy)
	{
		// Compute the negative (i.e., int-double)
		final double d = addDoubleAndIntegerCanDestroy(
			-doubleObject.extractDouble(), object, canDestroy);
		// Negate it to produce (double-int).
		return fromDoubleRecycling(-d, doubleObject, canDestroy);
	}

	@Override
	public A_Number o_SubtractFromFloatCanDestroy (
		final AvailObject object,
		final A_Number floatObject,
		final boolean canDestroy)
	{
		// Compute the negative (i.e., int-float)
		final double d = addDoubleAndIntegerCanDestroy(
			-floatObject.extractDouble(), object, canDestroy);
		// Negate it to produce (float-int).
		return fromFloatRecycling((float)-d, floatObject, canDestroy);
	}

	@Override @AvailMethod
	Order o_NumericCompareToInteger (
		final AvailObject object,
		final AvailObject anInteger)
	{
		final int size1 = intCount(object);
		final int size2 = intCount(anInteger);
		final int high1 = object.intSlot(RAW_LONG_SLOTS_, size1);
		final int high2 = anInteger.rawSignedIntegerAt(size2);
		final int composite1 = high1 >= 0 ? size1 : -size1;
		final int composite2 = high2 >= 0 ? size2 : -size2;
		if (composite1 != composite2)
		{
			return composite1 < composite2 ? LESS : MORE;
		}
		// The sizes and signs are the same.
		assert size1 == size2;
		assert high1 >= 0 == high2 >= 0;
		if (high1 != high2)
		{
			return high1 < high2 ? LESS : MORE;
		}
		for (int i = size1 - 1; i >= 1; i--)
		{
			final int a = object.intSlot(RAW_LONG_SLOTS_, i);
			final int b = anInteger.rawSignedIntegerAt(i);
			if (a != b)
			{
				return (a & 0xFFFFFFFFL) < (b & 0xFFFFFFFFL) ? LESS : MORE;
			}
		}
		return EQUAL;
	}

	@Override @AvailMethod
	Order o_NumericCompareToInfinity (
		final AvailObject object,
		final Sign sign)
	{
		return sign == Sign.POSITIVE ? LESS : MORE;
	}

	@Override @AvailMethod
	Order o_NumericCompareToDouble (
		final AvailObject object,
		final double aDouble)
	{
		return compareDoubleAndInteger(aDouble, object).reverse();
	}

	@Override @AvailMethod
	protected A_Number o_BitwiseAnd (
		final AvailObject object,
		final A_Number anInteger,
		final boolean canDestroy)
	{
		final int objectSize = intCount(object);
		final AvailObject anIntegerTraversed = anInteger.traversed();
		final int anIntegerSize = intCount(anIntegerTraversed);
		@Nullable AvailObject output = canDestroy
			? largerMutableOf(object, anIntegerTraversed, objectSize,
			anIntegerSize)
			: null;
		// Both integers are 32 bits. This is by far the most common case.
		if (objectSize == 1 && anIntegerSize == 1)
		{
			final int result = object.rawSignedIntegerAt(1)
				& anIntegerTraversed.rawSignedIntegerAt(1);
			if (output == null)
			{
				output = createUninitializedInteger(1);
			}
			output.rawSignedIntegerAtPut(1, result);
			return output;
		}
		// If neither of the inputs were suitable for destruction, then allocate
		// a new one whose size is that of the larger input.
		if (output == null)
		{
			output = createUninitializedInteger(max(objectSize, anIntegerSize));
		}
		// Handle larger integers.
		final int outputSize = intCount(output);
		final int extendedObject = object.rawSignedIntegerAt(objectSize) >> 31;
		final int extendedAnInteger =
			anIntegerTraversed.rawSignedIntegerAt(anIntegerSize) >> 31;
		for (int i = 1; i <= outputSize; i++)
		{
			final int objectWord = i > objectSize
				? extendedObject
				: object.rawSignedIntegerAt(i);
			final int anIntegerWord = i > anIntegerSize
				? extendedAnInteger
				: anIntegerTraversed.rawSignedIntegerAt(i);
			final int result = objectWord & anIntegerWord;
			output.rawSignedIntegerAtPut(i, result);
		}
		output.trimExcessInts();
		return output;
	}

	@Override @AvailMethod
	protected A_Number o_BitwiseOr (
		final AvailObject object,
		final A_Number anInteger,
		final boolean canDestroy)
	{
		final int objectSize = intCount(object);
		final AvailObject anIntegerTraversed = anInteger.traversed();
		final int anIntegerSize = intCount(anIntegerTraversed);
		@Nullable AvailObject output = canDestroy
			? largerMutableOf(object, anIntegerTraversed, objectSize,
			anIntegerSize)
			: null;
		// Both integers are 32 bits. This is by far the most common case.
		if (objectSize == 1 && anIntegerSize == 1)
		{
			final int result = object.rawSignedIntegerAt(1)
				| anIntegerTraversed.rawSignedIntegerAt(1);
			if (output == null)
			{
				output = createUninitializedInteger(1);
			}
			output.rawSignedIntegerAtPut(1, result);
			return output;
		}
		// If neither of the inputs were suitable for destruction, then allocate
		// a new one whose size is that of the larger input.
		if (output == null)
		{
			output = createUninitializedInteger(max(objectSize, anIntegerSize));
		}
		// Handle larger integers.
		final int outputSize = intCount(output);
		final int extendedObject = object.rawSignedIntegerAt(objectSize) >> 31;
		final int extendedAnInteger =
			anIntegerTraversed.rawSignedIntegerAt(anIntegerSize) >> 31;
		for (int i = 1; i <= outputSize; i++)
		{
			final int objectWord = i > objectSize
				? extendedObject
				: object.rawSignedIntegerAt(i);
			final int anIntegerWord = i > anIntegerSize
				? extendedAnInteger
				: anIntegerTraversed.rawSignedIntegerAt(i);
			final int result = objectWord | anIntegerWord;
			output.rawSignedIntegerAtPut(i, result);
		}
		output.trimExcessInts();
		return output;
	}

	@Override @AvailMethod
	protected A_Number o_BitwiseXor (
		final AvailObject object,
		final A_Number anInteger,
		final boolean canDestroy)
	{
		final int objectSize = intCount(object);
		final AvailObject anIntegerTraversed = anInteger.traversed();
		final int anIntegerSize = intCount(anIntegerTraversed);
		@Nullable AvailObject output = canDestroy
			? largerMutableOf(
				object, anIntegerTraversed, objectSize, anIntegerSize)
			: null;
		// Both integers are 32 bits. This is by far the most common case.
		if (objectSize == 1 && anIntegerSize == 1)
		{
			final int result = object.rawSignedIntegerAt(1)
				^ anIntegerTraversed.rawSignedIntegerAt(1);
			if (output == null)
			{
				output = createUninitializedInteger(1);
			}
			output.rawSignedIntegerAtPut(1, result);
			return output;
		}
		// If neither of the inputs were suitable for destruction, then allocate
		// a new one whose size is that of the larger input.
		if (output == null)
		{
			output = createUninitializedInteger(max(objectSize, anIntegerSize));
		}
		// Handle larger integers.
		final int outputSize = intCount(output);
		final int extendedObject = object.rawSignedIntegerAt(objectSize) >> 31;
		final int extendedAnInteger =
			anIntegerTraversed.rawSignedIntegerAt(anIntegerSize) >> 31;
		for (int i = 1; i <= outputSize; i++)
		{
			final int objectWord = i > objectSize
				? extendedObject
				: object.rawSignedIntegerAt(i);
			final int anIntegerWord = i > anIntegerSize
				? extendedAnInteger
				: anIntegerTraversed.rawSignedIntegerAt(i);
			final int result = objectWord ^ anIntegerWord;
			output.rawSignedIntegerAtPut(i, result);
		}
		output.trimExcessInts();
		return output;
	}

	/**
	 * Shift the given positive number to the left by the specified shift factor
	 * (number of bits), then truncate the representation to force bits above
	 * the specified position to be zeroed.  The shift factor may be
	 * negative, indicating a right shift by the corresponding positive amount,
	 * in which case truncation will still happen.
	 *
	 * <p>
	 * For example, shifting the binary number 1011<sub>2</sub> to the left by 2
	 * positions will produce 101100<sub>2</sub>, then truncating it to, say 5
	 * bits, would produce 01100<sub>2</sub>.  For a second example, the
	 * positive number 110101 can be shifted left by -2 positions (which is a
	 * right shift of 2) to get 1101, and a subsequent truncation to 10 bits
	 * would leave it unaffected.
	 * </p>
	 *
	 * @param object
	 *            The non-negative integer to shift and mask.
	 * @param shiftFactor
	 *            How much to shift left (may be negative to indicate a right
	 *            shift).
	 * @param truncationBits
	 *            A positive integer indicating how many low-order bits of the
	 *            shifted value should be preserved.
	 * @param canDestroy
	 *            Whether it is permitted to alter the original object if it
	 *            happens to be mutable.
	 * @return &#40;object &times; 2<sup>shiftFactor</sup>)
	 *            mod 2<sup>truncationBits</sup>
	 */
	@Override @AvailMethod
	protected A_Number o_BitShiftLeftTruncatingToBits (
		final AvailObject object,
		final A_Number shiftFactor,
		final A_Number truncationBits,
		final boolean canDestroy)
	{
		if (!truncationBits.isInt())
		{
			throw new ArithmeticException(
				AvailErrorCode.E_SHIFT_AND_TRUNCATE_REQUIRES_NON_NEGATIVE);
		}
		final int truncationInt = truncationBits.extractInt();
		if (truncationInt < 0)
		{
			throw new ArithmeticException(
				AvailErrorCode.E_SHIFT_AND_TRUNCATE_REQUIRES_NON_NEGATIVE);
		}
		final Order sign = object.numericCompareToInteger(zero);
		if (sign == LESS)
		{
			throw new ArithmeticException(
				AvailErrorCode.E_SHIFT_AND_TRUNCATE_REQUIRES_NON_NEGATIVE);
		}
		if (sign == EQUAL)
		{
			if (!canDestroy || isMutable())
			{
				object.makeImmutable();
			}
			// 0*2^n = 0
			return object;
		}
		if (!shiftFactor.isInt())
		{
			// e.g., 123 >> 999999999999999999 is 0
			// also 123 << 999999999999999999 truncated to N bits (N<2^31) is 0.
			return zero();
		}
		final int shiftInt = shiftFactor.extractInt();
		if (object.isLong())
		{
			final long baseLong = object.extractLong();
			final long shiftedLong = bitShiftLong(baseLong, shiftInt);
			if (shiftInt < 0
				|| truncationInt < 64
				|| bitShiftLong(shiftedLong, -shiftInt) == baseLong)
			{
				// Either a right shift, or a left shift that didn't lose bits,
				// or a left shift that will fit in a long after the truncation.
				// In these cases the result will still be a long.
				long resultLong = shiftedLong;
				if (truncationInt < 64)
				{
					resultLong &= (1L << truncationInt) - 1;
				}
				if (canDestroy && isMutable())
				{
					if (resultLong == (int) resultLong)
					{
						// Fits in an int.  Try to recycle.
						if (intCount(object) == 1)
						{
							object.rawSignedIntegerAtPut(1, (int) resultLong);
							return object;
						}
					}
					else
					{
						// *Fills* a long.  Try to recycle.
						if (intCount(object) == 2)
						{
							object.rawSignedIntegerAtPut(
								1,
								(int) resultLong);
							object.rawSignedIntegerAtPut(
								2,
								(int) (resultLong >> 32L));
							return object;
						}
					}
				}
				// Fall back and create a new integer object.
				return fromLong(resultLong);
			}
		}
		// Answer doesn't (necessarily) fit in a long.
		final int sourceSlots = intCount(object);
		int estimatedBits = (sourceSlots << 5) + shiftInt;
		estimatedBits = min(estimatedBits, truncationInt + 1);
		estimatedBits = max(estimatedBits, 1);
		final int slotCount = (estimatedBits + 31) >> 5;
		final A_Number result = createUninitializedInteger(slotCount);
		final int shortShift = shiftInt & 31;
		int sourceIndex = slotCount - (shiftInt >> 5);
		long accumulator = 0xDEADCAFEBABEBEEFL;
		// We range from slotCount+1 to 1 to pre-load the accumulator.
		for (int destIndex = slotCount + 1; destIndex >= 1; destIndex--)
		{
			final int nextWord =
				(1 <= sourceIndex && sourceIndex <= sourceSlots)
					? object.rawSignedIntegerAt(sourceIndex)
					: 0;
			accumulator <<= 32;
			accumulator |= (long) nextWord << shortShift;
			if (destIndex <= slotCount)
			{
				result.rawSignedIntegerAtPut(
					destIndex,
					(int) (accumulator >> 32));
			}
			sourceIndex--;
		}
		// Mask it if necessary to truncate some upper bits.
		int mask = (1 << (truncationInt & 31)) - 1;
		for (
			int destIndex = truncationInt >> 5;
			destIndex <= slotCount;
			destIndex++)
		{
			result.rawSignedIntegerAtPut(
				destIndex,
				result.rawSignedIntegerAt(destIndex) & mask);
			// Completely wipe any higher ints.
			mask = 0;
		}
		result.trimExcessInts();
		return result;
	}

	/**
	 * Shift the given integer to the left by the specified shift factor
	 * (number of bits).  The shift factor may be negative, indicating a right
	 * shift by the corresponding positive amount.
	 *
	 * @param object
	 *            The integer to shift.
	 * @param shiftFactor
	 *            How much to shift left (may be negative to indicate a right
	 *            shift).
	 * @param canDestroy
	 *            Whether it is permitted to alter the original object if it
	 *            happens to be mutable.
	 * @return &#x23a3;object &times; 2<sup>shiftFactor</sup>&#x23a6;
	 */
	@Override @AvailMethod
	protected A_Number o_BitShift (
		final AvailObject object,
		final A_Number shiftFactor,
		final boolean canDestroy)
	{
		if (object.equals(zero()))
		{
			if (!canDestroy || isMutable())
			{
				object.makeImmutable();
			}
			// 0*2^n = 0
			return object;
		}
		if (!shiftFactor.isInt())
		{
			if (shiftFactor.numericCompareToInteger(zero) == MORE)
			{
				// e.g., 123 << 999999999999999999 is too big
				throw new ArithmeticException(
					AvailErrorCode.E_TOO_LARGE_TO_REPRESENT);
			}
			if (object.numericCompareToInteger(zero) == MORE)
			{
				// e.g., 123 >> 999999999999999999 is 0
				return zero();
			}
			// e.g., -123 >> 999999999999999999 is -1
			return negativeOne();
		}
		final int shiftInt = shiftFactor.extractInt();
		if (object.isLong())
		{
			final long baseLong = object.extractLong();
			final long shiftedLong = arithmeticBitShiftLong(baseLong, shiftInt);
			if (shiftInt < 0
				|| arithmeticBitShiftLong(shiftedLong, -shiftInt) == baseLong)
			{
				// Either a right shift, or a left shift that didn't lose bits.
				// In these cases the result will still be a long.
				if (canDestroy && isMutable())
				{
					if (shiftedLong == (int) shiftedLong)
					{
						// Fits in an int.  Try to recycle.
						if (intCount(object) == 1)
						{
							object.rawSignedIntegerAtPut(1, (int) shiftedLong);
							return object;
						}
					}
					else
					{
						// *Fills* a long.  Try to recycle.
						if (intCount(object) == 2)
						{
							object.rawSignedIntegerAtPut(
								1,
								(int) shiftedLong);
							object.rawSignedIntegerAtPut(
								2,
								(int) (shiftedLong >> 32L));
							return object;
						}
					}
				}
				// Fall back and create a new integer object.
				return fromLong(shiftedLong);
			}
		}
		// Answer doesn't (necessarily) fit in a long.
		final int sourceSlots = intCount(object);
		int estimatedBits = (sourceSlots << 5) + shiftInt;
		estimatedBits = max(estimatedBits, 1);
		final int intSlotCount = (estimatedBits + 31) >> 5;
		final A_Number result = createUninitializedInteger(intSlotCount);
		final int shortShift = shiftInt & 31;
		int sourceIndex = intSlotCount - (shiftInt >> 5);
		long accumulator = 0xDEADCAFEBABEBEEFL;
		final int signExtension =
			object.numericCompareToInteger(zero) == LESS ? -1 : 0;
		// We range from slotCount+1 to 1 to pre-load the accumulator.
		for (int destIndex = intSlotCount + 1; destIndex >= 1; destIndex--)
		{
			final int nextWord =
				sourceIndex < 1
					? 0
					: sourceIndex > sourceSlots
						? signExtension
						: object.rawSignedIntegerAt(sourceIndex);
			accumulator <<= 32;
			accumulator |= (nextWord & 0xFFFFFFFFL) << shortShift;
			if (destIndex <= intSlotCount)
			{
				result.rawSignedIntegerAtPut(
					destIndex,
					(int) (accumulator >> 32));
			}
			sourceIndex--;
		}
		result.trimExcessInts();
		return result;
	}

	@Override @AvailMethod
	SerializerOperation o_SerializerOperation (
		final AvailObject object)
	{
		if (object.isInt())
		{
			final int intValue = object.extractInt();
			if (0 <= intValue && intValue <= 10)
			{
				switch (intValue)
				{
					case 0: return SerializerOperation.ZERO_INTEGER;
					case 1: return SerializerOperation.ONE_INTEGER;
					case 2: return SerializerOperation.TWO_INTEGER;
					case 3: return SerializerOperation.THREE_INTEGER;
					case 4: return SerializerOperation.FOUR_INTEGER;
					case 5: return SerializerOperation.FIVE_INTEGER;
					case 6: return SerializerOperation.SIX_INTEGER;
					case 7: return SerializerOperation.SEVEN_INTEGER;
					case 8: return SerializerOperation.EIGHT_INTEGER;
					case 9: return SerializerOperation.NINE_INTEGER;
					case 10: return SerializerOperation.TEN_INTEGER;
				}
			}
			if ((intValue & 0xFF) == intValue)
			{
				return SerializerOperation.BYTE_INTEGER;
			}
			if ((intValue & 0xFFFF) == intValue)
			{
				return SerializerOperation.SHORT_INTEGER;
			}
			return SerializerOperation.INT_INTEGER;
		}
		return SerializerOperation.BIG_INTEGER;
	}

	@Override
	Object o_MarshalToJava (
		final AvailObject object,
		final @Nullable Class<?> classHint)
	{
		// Force marshaling to java.math.BigInteger.
		if (BigInteger.class.equals(classHint))
		{
			return object.asBigInteger();
		}
		// Force marshaling to Java's primitive long type.
		if (Long.TYPE.equals(classHint) || Long.class.equals(classHint))
		{
			if (!object.isLong())
			{
				throw new MarshalingException();
			}
			return object.extractLong();
		}
		// Force marshaling to Java's primitive int type.
		if (Integer.TYPE.equals(classHint) || Integer.class.equals(classHint))
		{
			if (!object.isInt())
			{
				throw new MarshalingException();
			}
			return object.extractInt();
		}
		// Force marshaling to Java's primitive short type.
		if (Short.TYPE.equals(classHint) || Short.class.equals(classHint))
		{
			if (!object.isSignedShort())
			{
				throw new MarshalingException();
			}
			return object.extractSignedShort();
		}
		// Force marshaling to Java's primitive byte type.
		if (Byte.TYPE.equals(classHint) || Byte.class.equals(classHint))
		{
			if (!object.isSignedByte())
			{
				throw new MarshalingException();
			}
			return object.extractSignedByte();
		}
		// No useful hint was provided, so marshal to the smallest primitive
		// integral type able to express object's value.
		if (object.isLong())
		{
			final long longValue = object.extractLong();
			if (longValue == (byte) longValue)
			{
				return (byte) longValue;
			}
			if (longValue == (short) longValue)
			{
				return (short) longValue;
			}
			if (longValue == (int) longValue)
			{
				return (int) longValue;
			}
			return longValue;
		}
		return object.asBigInteger();
	}

	/**
	 * Answer a {@link BigInteger} that is the numerical equivalent of the given
	 * object, which is an Avail integer.
	 */
	@Override @AvailMethod
	public BigInteger o_AsBigInteger (final AvailObject object)
	{
		final int integerCount = intCount(object);
		if (integerCount <= 2)
		{
			return BigInteger.valueOf(object.extractLong());
		}
		final byte[] bytes = new byte[integerCount << 2];
		int b = 0;
		for (int i = integerCount; i > 0; i--)
		{
			final int integer = object.intSlot(RAW_LONG_SLOTS_, i);
			bytes[b++] = (byte) (integer >> 24);
			bytes[b++] = (byte) (integer >> 16);
			bytes[b++] = (byte) (integer >> 8);
			bytes[b++] = (byte) integer;
		}
		return new BigInteger(bytes);
	}

	@Override
	protected boolean o_IsNumericallyIntegral (final AvailObject object)
	{
		return true;
	}

	@Override
	protected void o_WriteTo (final AvailObject object, final JSONWriter writer)
	{
		if (object.isLong())
		{
			writer.write(object.extractLong());
		}
		else
		{
			writer.write(object.asBigInteger());
		}
	}

	/**
	 * Convert the specified Java {@code long} into an Avail {@linkplain
	 * IntegerDescriptor integer}.
	 *
	 * @param aLong A Java {@code long}.
	 * @return An {@link AvailObject}.
	 */
	public static AvailObject fromLong (final long aLong)
	{
		if (aLong == (aLong & 255))
		{
			return immutableByteObjects[(int) aLong];
		}
		if (aLong == (int) aLong)
		{
			final AvailObject result = createUninitializedInteger(1);
			result.setIntSlot(RAW_LONG_SLOTS_, 1, (int) aLong);
			return result;
		}
		final AvailObject result = createUninitializedInteger(2);
		result.setIntSlot(RAW_LONG_SLOTS_, 1, (int) aLong);
		result.setIntSlot(RAW_LONG_SLOTS_, 2, (int) (aLong >> 32L));
		return result;
	}

	/**
	 * Create an Avail integer that is the numerical equivalent of the given
	 * Java {@link BigInteger}.
	 *
	 * @param bigInteger The BigInteger to convert.
	 * @return An Avail integer representing the same number as the argument.
	 */
	public static A_Number fromBigInteger (final BigInteger bigInteger)
	{
		final byte[] bytes = bigInteger.toByteArray();
		if (bytes.length <= 8)
		{
			return fromLong(bigInteger.longValue());
		}
		final int intCount = (bytes.length + 3) >> 2;
		final AvailObject result = createUninitializedInteger(intCount);
		// Start with the least significant bits.
		int byteIndex = bytes.length;
		int destIndex;
		for (destIndex = 1; destIndex < intCount; destIndex++)
		{
			byteIndex -= 4;  // Zero-based index to start of four byte run
			final int intValue =
				((bytes[byteIndex] & 255) << 24)
				+ ((bytes[byteIndex + 1] & 255) << 16)
				+ ((bytes[byteIndex + 2] & 255) << 8)
				+ ((bytes[byteIndex + 3] & 255));
			result.setIntSlot(RAW_LONG_SLOTS_, destIndex, intValue);
		}
		// There are at least 4 bytes present (<=8 was special-cased), so create
		// an int from the leading (most significant) 4 bytes.  Some of these
		// bytes may have already been included in the previous int, but at
		// least one and at most four bytes will survive to be written to the
		// most significant int.
		int highInt =
			((bytes[0] & 255) << 24)
			+ ((bytes[1] & 255) << 16)
			+ ((bytes[2] & 255) << 8)
			+ (bytes[3] & 255);
		assert byteIndex >= 1 && byteIndex <= 4;
		// Now shift away the bytes already consumed.
		highInt >>= (4 - byteIndex) << 3;
		result.setIntSlot(RAW_LONG_SLOTS_, destIndex, highInt);
		// There should be no need to trim it...
		result.trimExcessInts();
		assert intCount(result) == intCount;
		return result;
	}

	/**
	 * Answer an Avail integer that holds the truncation of the {@code double}
	 * argument, rounded towards zero.
	 *
	 * @param aDouble
	 *            The object whose truncation should be encoded as an Avail
	 *            integer.
	 * @return An Avail integer.
	 */
	public static A_Number truncatedFromDouble (final double aDouble)
	{
		// Extract the top three 32-bit sections.  That guarantees 65 bits
		// of mantissa, which is more than a double actually captures.
		double truncated = aDouble;
		if (truncated >= Long.MIN_VALUE && truncated <= Long.MAX_VALUE)
		{
			// Common case -- it fits in a long.
			return fromLong((long) truncated);
		}
		final boolean neg = truncated < 0.0d;
		truncated = abs(truncated);
		final int exponent = getExponent(truncated);
		final int slots = (exponent + 31) >> 5;  // probably needs work
		final AvailObject out = createUninitializedInteger(slots);
		truncated = scalb(truncated, (1 - slots) << 5);
		for (int i = slots; i >= 1; --i)
		{
			final long intSlice = (int) truncated;
			out.setIntSlot(RAW_LONG_SLOTS_, i, (int) intSlice);
			truncated -= intSlice;
			truncated = scalb(truncated, 32);
		}
		out.trimExcessInts();
		if (neg)
		{
			return zero().noFailMinusCanDestroy(out, true);
		}
		return out;
	}

	/**
	 * Convert the specified Java {@code int} into an Avail {@linkplain
	 * IntegerDescriptor integer}.
	 *
	 * @param anInteger A Java {@code int}.
	 * @return An {@link AvailObject}.
	 */
	@ReferencedInGeneratedCode
	public static AvailObject fromInt (final int anInteger)
	{
		if (anInteger == (anInteger & 255))
		{
			return immutableByteObjects[anInteger];
		}
		final AvailObject result = createUninitializedInteger(1);
		result.setIntSlot(RAW_LONG_SLOTS_, 1, anInteger);
		return result;
	}

	/**
	 * Convert the specified byte-valued Java {@code short} into an Avail
	 * integer.
	 *
	 * @param anInteger A Java {@code int}.
	 * @return An {@link AvailObject}.
	 */
	public static AvailObject fromUnsignedByte (final short anInteger)
	{
		assert anInteger >= 0 && anInteger <= 255;
		return immutableByteObjects[anInteger];
	}

	/**
	 * Extract a {@code double} from this integer, but scale it down by the
	 * specified power of two in the process.  If the integer is beyond the
	 * scale of a double but the scale would bring it in range, don't overflow.
	 *
	 * @param object
	 *            The integer to convert to a double.
	 * @param exponentBias
	 *            The scale by which the result's exponent should be adjusted
	 *            (a positive number scales the result downward).
	 * @return
	 *            The integer times 2^-exponentBias, as a double.
	 */
	public static double extractDoubleScaled (
		final AvailObject object,
		final int exponentBias)
	{
		// Extract and scale the top three ints from anInteger, if available.
		// This guarantees at least 64 correct upper bits were extracted (if
		// available), which is better than a double can represent.
		final int slotsCount = intCount(object);
		final long high = object.rawSignedIntegerAt(slotsCount);
		double d = scalb(
			(double) high,
			((slotsCount - 1) << 5) - exponentBias);
		if (slotsCount > 1)
		{
			final long med = (high & ~0xFFFFFFFFL)
				+ object.rawUnsignedIntegerAt(slotsCount - 1);
			d += scalb(
				(double) med,
				((slotsCount - 2) << 5) - exponentBias);
			if (slotsCount > 2)
			{
				final long low = (high & ~0xFFFFFFFFL)
					+ object.rawUnsignedIntegerAt(slotsCount - 2);
				d += scalb(
					(double) low,
					((slotsCount - 3) << 5) - exponentBias);
			}
		}
		return d;
	}

	/**
	 * Answer an {@link AvailObject} representing the {@linkplain
	 * IntegerDescriptor integer} negative one (-1).
	 *
	 * @return The Avail integer negative one (-1).
	 */
	public static A_Number negativeOne ()
	{
		return negativeOne;
	}

	/**
	 * Answer the {@code int} hash value of the given {@code short} in the range
	 * 0..255 inclusive.
	 *
	 * @param anInteger
	 *        The {@code short} to be hashed.  It must be in the range 0..255
	 *        inclusive.
	 * @return The hash of the passed unsigned byte.
	 */
	static int hashOfUnsignedByte (final short anInteger)
	{
		return hashesOfUnsignedBytes[anInteger];
	}

	/** The initialization value for computing the hash of an integer. */
	private static final int initialHashValue = 0x13592884;

	/**
	 * The value to xor with after multiplying by the {@link
	 * AvailObject#multiplier} for each {@code int} of the integer.
	 */
	private static final int postMultiplyHashToggle = 0x95ffb59f;

	/**
	 * The value to add after performing a final extra multiply by {@link
	 * AvailObject#multiplier}.
	 */
	private static final int finalHashAddend = 0x5127ee66;


	/**
	 * Hash the passed {@code int}.  Note that it must have the same value as
	 * what {@link #computeHashOfIntegerObject(AvailObject)} would return, given
	 * an encoding of the {@code int} as an Avail integer.
	 *
	 * @param anInt The {@code int} to hash.
	 * @return The hash of the given {@code int}.
	 */
	static int computeHashOfInt (final int anInt)
	{
		int h = initialHashValue + anInt;
		h *= multiplier;
		h ^= postMultiplyHashToggle;
		h *= multiplier;
		h += finalHashAddend;
		return h;
	}

	/**
	 * Compute the hash of the given Avail integer object.  Note that if the
	 * input is within the range of an {@code int}, it should produce the same
	 * value as the equivalent invocation of {@link #computeHashOfInt(int)}.
	 *
	 * @param anIntegerObject
	 *        An Avail integer to be hashed.
	 * @return The hash of the given Avail integer.
	 */
	private static int computeHashOfIntegerObject (
		final AvailObject anIntegerObject)
	{
		int output = initialHashValue;
		for (int i = intCount(anIntegerObject); i > 0; i--)
		{
			output += anIntegerObject.rawSignedIntegerAt(i);
			output *= multiplier;
			output ^= postMultiplyHashToggle;
		}
		output *= multiplier;
		output += finalHashAddend;
		return output;
	}

	/**
	 * Create a mutable Avail integer with the specified number of uninitialized
	 * int slots.
	 *
	 * @param size The number of int slots to have in the result.
	 * @return An uninitialized, mutable integer.
	 */
	public static AvailObject createUninitializedInteger (final int size)
	{
		final IntegerDescriptor descriptor = mutableFor(size);
		return descriptor.create((size + 1) >> 1);
	}

	/**
	 * Construct a new {@code IntegerDescriptor}.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 * @param unusedIntsOfLastLong
	 *        The number of unused {@code int}s in the last {@code long}.  Must
	 *        be 0 or 1.
	 */
	private IntegerDescriptor (
		final Mutability mutability,
		final byte unusedIntsOfLastLong)
	{
		super(mutability, TypeTag.INTEGER_TAG, null, IntegerSlots.class);
		assert (unusedIntsOfLastLong & ~1) == 0;
		this.unusedIntsOfLastLong = unusedIntsOfLastLong;
	}

	/**
	 * The static list of descriptors of this kind, organized in such a way that
	 * they can be found by mutability and the number of unused ints in the last
	 * long.
	 */
	private static final IntegerDescriptor[] descriptors =
		new IntegerDescriptor[2 * 3];

	static {
		int i = 0;
		for (final byte excess : new byte[] {0,1})
		{
			for (final Mutability mut : Mutability.values())
			{
				descriptors[i++] = new IntegerDescriptor(mut, excess);
			}
		}
	}

	/**
	 * Answer the mutable descriptor that is suitable to describe an integer
	 * with the given number of int slots.
	 *
	 * @param size
	 *        How many int slots are in the large integer to be represented by
	 *        the descriptor.
	 * @return An {@code IntegerDescriptor} suitable for representing an integer
	 *         of the given mutability and int slot count.
	 */
	private static IntegerDescriptor mutableFor (
		final int size)
	{
		return descriptors[(size & 1) * 3];
	}

	@Override
	IntegerDescriptor mutable ()
	{
		return descriptors[
			(unusedIntsOfLastLong & 1) * 3 + MUTABLE.ordinal()];
	}

	@Override
	IntegerDescriptor immutable ()
	{
		return descriptors[
			(unusedIntsOfLastLong & 1) * 3 + IMMUTABLE.ordinal()];
	}

	@Override
	IntegerDescriptor shared ()
	{
		return descriptors[
			(unusedIntsOfLastLong & 1) * 3 + SHARED.ordinal()];
	}

	/**
	 * An array of 256 {@code int}s, corresponding to the hashes of the values
	 * 0..255 inclusive.
	 */
	private static final int[] hashesOfUnsignedBytes;

	static
	{
		final int[] hashes = new int [256];
		for (int i = 0; i <= 255; i++)
		{
			hashes[i] = computeHashOfInt(i);
		}
		hashesOfUnsignedBytes = hashes;
	}

	/**
	 * An array of 256 immutable {@linkplain IntegerDescriptor integers},
	 * corresponding with the indices 0..255 inclusive.  These make many kinds
	 * of calculations much more efficient than naively constructing a fresh
	 * {@link AvailObject} unconditionally.
	 */
	private static final AvailObject[] immutableByteObjects =
		new AvailObject[256];

	static
	{
		for (int i = 0; i <= 255; i++)
		{
			final AvailObject object = createUninitializedInteger(1);
			object.rawSignedIntegerAtPut(1, i);
			immutableByteObjects[i] = object.makeShared();
		}
	}

	/** An Avail integer representing zero (0). */
	private static final AvailObject zero = immutableByteObjects[0];

	/** An Avail integer representing one (1). */
	private static final AvailObject one = immutableByteObjects[1];

	/** An Avail integer representing two (2). */
	private static final AvailObject two = immutableByteObjects[2];

	/** An Avail integer representing ten (10). */
	private static final AvailObject ten = immutableByteObjects[10];

	/** The Avail integer negative one (-1). */
	private static final AvailObject negativeOne;

	static
	{
		final AvailObject neg = createUninitializedInteger(1);
		neg.rawSignedIntegerAtPut(1, -1);
		negativeOne = neg.makeShared();
	}

	/**
	 * Answer an {@link AvailObject} representing the {@linkplain
	 * IntegerDescriptor integer} zero (0).
	 *
	 * @return The Avail integer zero.
	 */
	public static A_Number zero ()
	{
		return zero;
	}

	/**
	 * Answer an {@link AvailObject} representing the {@linkplain
	 * IntegerDescriptor integer} one (1).
	 *
	 * @return The Avail integer one.
	 */
	public static A_Number one ()
	{
		return one;
	}

	/**
	 * Answer an {@link AvailObject} representing the {@linkplain
	 * IntegerDescriptor integer} two (2).
	 *
	 * @return The Avail integer two.
	 */
	public static A_Number two ()
	{
		return two;
	}

	/**
	 * One (U.S.) quintillion, which is 10^18.  This is the largest power of ten
	 * representable as a signed long.
	 */
	private static final long quintillionLong = 1_000_000_000_000_000_000L;

	/**
	 * One (U.S.) quintillion, which is 10^18.  This is the largest power of ten
	 * for which {@link #o_IsLong(AvailObject)} returns true.
	 */
	private static final A_Number quintillionInteger =
		fromLong(quintillionLong).makeShared();

	/**
	 * Successive squares of one (U.S.) quintillion, 10^18.  Element #n is
	 * equal to (10^18)^(2^n).  List access is protected by
	 * {@link #squaresOfQuintillionLock}.
	 */
	//@GuardedBy("squaresOfQuintillionLock")
	private static final List<A_Number> squaresOfQuintillion =
		new ArrayList<>(singleton(quintillionInteger));

	/** The lock that protects access to squaresOfQuintillion. */
	private static final ReadWriteLock squaresOfQuintillionLock =
		new ReentrantReadWriteLock();

	/**
	 * Answer the nth successive square of a (U.S.) quintillion, which will be
	 * (10^18)^(2^n).  N must be ≥ 0.  Cache this number for performance.
	 *
	 * @param n The number of times to successively square a quintillion.
	 * @return The value (10^18)^(2^n).
	 */
	public static A_Number cachedSquareOfQuintillion (
		final int n)
	{
		// Use a safe double-check mechanism.  Use a read-lock first.
		final @Nullable A_Number result = lockWhileNullable(
			squaresOfQuintillionLock.readLock(),
			() -> n < squaresOfQuintillion.size()
				? squaresOfQuintillion.get(n)
				: null);
		if (result != null)
		{
			// Most common case.
			return result;
		}
		// Otherwise, hold the write-lock.
		return lockWhile(
			squaresOfQuintillionLock.writeLock(),
			() ->
			{
				// Note that the list may have changed between releasing the
				// read-lock and acquiring the write-lock.  The for-loop should
				// accommodate that situation.
				for (int size = squaresOfQuintillion.size(); size <= n; size++)
				{
					final A_Number last = squaresOfQuintillion.get(size - 1);
					final A_Number next =
						last.timesCanDestroy(last, false).makeShared();
					squaresOfQuintillion.add(next);
				}
				return squaresOfQuintillion.get(n);
			});
	}
}
