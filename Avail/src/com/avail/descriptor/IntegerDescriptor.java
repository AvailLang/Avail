/**
 * IntegerDescriptor.java
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

package com.avail.descriptor;

import static com.avail.descriptor.AvailObject.Multiplier;
import static com.avail.descriptor.TypeDescriptor.Types.*;
import static java.lang.Math.*;
import static com.avail.descriptor.AbstractNumberDescriptor.Order.*;
import static com.avail.descriptor.IntegerDescriptor.IntegerSlots.*;
import java.math.BigInteger;
import java.util.*;
import com.avail.annotations.*;
import com.avail.exceptions.*;
import com.avail.exceptions.ArithmeticException;
import com.avail.serialization.SerializerOperation;

/**
 * An Avail {@linkplain IntegerDescriptor integer} is represented by a little
 * endian series of {@code int} slots.  The slots are really treated as
 * unsigned, except for the final slot which is considered signed.  The high bit
 * of the final slot (i.e., its sign bit) is the sign bit of the entire object.
 *
 * <p>
 * Avail integers should always occupy the fewest number of slots to
 * unambiguously indicate the represented integer.  A zero integer is
 * represented by a single slot containing a zero {@code int}.  Any {@code int}
 * can be converted to an Avail integer by using a single slot, and any {@code
 * long} can be represented with at most two slots.
 * </p>
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 */
public class IntegerDescriptor
extends ExtendedIntegerDescriptor
{
	/**
	 * The layout of integer slots for my instances.
	 */
	public enum IntegerSlots implements IntegerSlotsEnum
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
		 * <nobr>{@code -0x80000000..-1}</nobr>), otherwise the last slot would
		 * be redundant.  Likewise, if the top slot is minus one ({code -1}),
		 * the second-from-top slot must have its upper bit clear (fall in the
		 * range <nobr>{@code 0..0x7FFFFFFF}</nobr>).
		 * </p>
		 */
		RAW_SIGNED_INT_AT_
	}

	@Override @AvailMethod
	int o_RawSignedIntegerAt (
		final @NotNull AvailObject object,
		final int subscript)
	{
		return object.slot(RAW_SIGNED_INT_AT_, subscript);
	}

	@Override @AvailMethod
	void o_RawSignedIntegerAtPut (
		final @NotNull AvailObject object,
		final int subscript,
		final int value)
	{
		object.setSlot(RAW_SIGNED_INT_AT_, subscript, value);
	}

	@Override
	public void printObjectOnAvoidingIndent (
		final @NotNull AvailObject object,
		final StringBuilder aStream,
		final List<AvailObject> recursionList,
		final int indent)
	{
		final int integerCount = object.integerSlotsCount();
		if (integerCount <= 2)
		{
			aStream.append(object.extractLong());
		}
		else
		{
			aStream.append(object.asBigInteger());
		}
	}

	@Override @AvailMethod
	boolean o_Equals (
		final @NotNull AvailObject object,
		final AvailObject another)
	{
		return another.equalsInteger(object);
	}

	/**
	 * Compare two integers for equality.
	 */
	@Override @AvailMethod
	boolean o_EqualsInteger (
		final @NotNull AvailObject object,
		final AvailObject anAvailInteger)
	{
		final int slotsCount = object.integerSlotsCount();
		if (slotsCount != anAvailInteger.integerSlotsCount())
		{
			return false;
		}
		for (int i = 1; i <= slotsCount; i++)
		{
			final int a = object.slot(RAW_SIGNED_INT_AT_, i);
			final int b = anAvailInteger.slot(RAW_SIGNED_INT_AT_, i);
			if (a != b)
			{
				return false;
			}
		}
		return true;
	}

	@Override @AvailMethod
	boolean o_IsInstanceOfKind (
		final @NotNull AvailObject object,
		final AvailObject aType)
	{
		if (NUMBER.o().isSubtypeOf(aType))
		{
			return true;
		}
		else if (!aType.isIntegerRangeType())
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
			if (!aType.lowerBound().lessOrEqual(object))
			{
				return false;
			}
		}
		else if (!aType.lowerBound().lessThan(object))
		{
			return false;
		}
		return true;
	}

	@Override @AvailMethod
	Order o_NumericCompare (
		final @NotNull AvailObject object,
		final AvailObject another)
	{
		return another.numericCompareToInteger(object).reverse();
	}

	@Override @AvailMethod
	int o_Hash (
		final @NotNull AvailObject object)
	{
		if (object.isUnsignedByte())
		{
			return hashOfUnsignedByte(object.extractUnsignedByte());
		}
		return computeHashOfIntegerObject(object);
	}

	@Override @AvailMethod
	boolean o_IsFinite (
		final @NotNull AvailObject object)
	{
		return true;
	}

	@Override @AvailMethod
	@NotNull AvailObject o_Kind (
		final @NotNull AvailObject object)
	{
		object.makeImmutable();
		return IntegerRangeTypeDescriptor.singleInteger(object);
	}

	@Override @AvailMethod
	@NotNull AvailObject o_DivideCanDestroy (
		final @NotNull AvailObject object,
		final AvailObject aNumber,
		final boolean canDestroy)
	{
		return aNumber.divideIntoIntegerCanDestroy(object, canDestroy);
	}

	@Override @AvailMethod
	@NotNull AvailObject o_MinusCanDestroy (
		final @NotNull AvailObject object,
		final AvailObject aNumber,
		final boolean canDestroy)
	{
		return aNumber.subtractFromIntegerCanDestroy(object, canDestroy);
	}

	@Override @AvailMethod
	@NotNull AvailObject o_PlusCanDestroy (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aNumber,
		final boolean canDestroy)
	{
		return aNumber.addToIntegerCanDestroy(object, canDestroy);
	}

	@Override @AvailMethod
	@NotNull AvailObject o_TimesCanDestroy (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aNumber,
		final boolean canDestroy)
	{
		return aNumber.multiplyByIntegerCanDestroy(object, canDestroy);
	}

	@Override @AvailMethod
	boolean o_IsNybble (final @NotNull AvailObject object)
	{
		if (object.integerSlotsCount() > 1)
		{
			return false;
		}
		final int value = object.extractInt();
		return (value & 15) == value;
	}

	@Override @AvailMethod
	boolean o_IsSignedByte (final @NotNull AvailObject object)
	{
		if (object.integerSlotsCount() > 1)
		{
			return false;
		}
		final int value = object.extractInt();
		return value == (byte) value;
	}

	@Override @AvailMethod
	boolean o_IsUnsignedByte (final @NotNull AvailObject object)
	{
		if (object.integerSlotsCount() > 1)
		{
			return false;
		}
		final int value = object.extractInt();
		return (value & 255) == value;
	}

	@Override @AvailMethod
	boolean o_IsSignedShort (final @NotNull AvailObject object)
	{
		if (object.integerSlotsCount() > 1)
		{
			return false;
		}
		final int value = object.extractInt();
		return value == (short) value;
	}

	@Override @AvailMethod
	boolean o_IsUnsignedShort (final @NotNull AvailObject object)
	{
		if (object.integerSlotsCount() > 1)
		{
			return false;
		}
		final int value = object.extractInt();
		return (value & 65535) == value;
	}

	@Override @AvailMethod
	boolean o_IsInt (final @NotNull AvailObject object)
	{
		return object.integerSlotsCount() == 1;
	}

	@Override @AvailMethod
	boolean o_IsLong (final @NotNull AvailObject object)
	{
		return object.integerSlotsCount() <= 2;
	}

	@Override @AvailMethod
	byte o_ExtractNybble (final @NotNull AvailObject object)
	{
		assert object.integerSlotsCount() == 1;
		final int value = object.rawSignedIntegerAt(1);
		assert value == (value & 15) : "Value is out of range for a nybble";
		return (byte)value;
	}

	@Override @AvailMethod
	byte o_ExtractSignedByte (final @NotNull AvailObject object)
	{
		assert object.integerSlotsCount() == 1;
		final int value = object.rawSignedIntegerAt(1);
		assert value == (byte) value : "Value is out of range for a byte";
		return (byte) value;
	}

	@Override @AvailMethod
	short o_ExtractUnsignedByte (final @NotNull AvailObject object)
	{
		assert object.integerSlotsCount() == 1;
		final int value = object.rawSignedIntegerAt(1);
		assert value == (value & 255) : "Value is out of range for a byte";
		return (short)value;
	}

	@Override @AvailMethod
	short o_ExtractSignedShort (final @NotNull AvailObject object)
	{
		assert object.integerSlotsCount() == 1;
		final int value = object.rawSignedIntegerAt(1);
		assert value == (short) value : "Value is out of range for a short";
		return (short) value;
	}

	@Override @AvailMethod
	int o_ExtractUnsignedShort (final @NotNull AvailObject object)
	{
		assert object.integerSlotsCount() == 1;
		final int value = object.rawSignedIntegerAt(1);
		assert value == (value & 65535) : "Value is out of range for a short";
		return value;
	}

	@Override @AvailMethod
	int o_ExtractInt (final @NotNull AvailObject object)
	{
		assert object.integerSlotsCount() == 1 : "Integer value out of bounds";
		return object.rawSignedIntegerAt(1);
	}

	@Override @AvailMethod
	long o_ExtractLong (final @NotNull AvailObject object)
	{
		assert
			object.integerSlotsCount() >= 1 && object.integerSlotsCount() <= 2
			: "Integer value out of bounds";

		if (object.integerSlotsCount() == 1)
		{
			return object.rawSignedIntegerAt(1);
		}

		long value = object.rawSignedIntegerAt(1) & 0xffffffffL;
		value |= ((long) object.rawSignedIntegerAt(2)) << 32L;
		return value;
	}

	@Override @AvailMethod
	float o_ExtractFloat (final @NotNull AvailObject object)
	{
		return (float) extractDoubleScaled(object, 0);
	}

	@Override @AvailMethod
	double o_ExtractDouble (final @NotNull AvailObject object)
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
		final @NotNull AvailObject object,
		final int subscript)
	{
		final int signedInt = object.slot(RAW_SIGNED_INT_AT_, subscript);
		return signedInt & 0xFFFFFFFFL;
	}

	/**
	 * Manually constructed accessor method.  Overwrite the quad-byte using the
	 * native byte-ordering, but using little endian between quad-bytes (i.e.,
	 * least significant quad comes first).
	 */
	@Override @AvailMethod
	void o_RawUnsignedIntegerAtPut (
		final @NotNull AvailObject object,
		final int subscript,
		final int value)
	{
		object.setSlot(RAW_SIGNED_INT_AT_, subscript, value);
	}

	@Override @AvailMethod
	void o_TrimExcessInts (
		final @NotNull AvailObject object)
	{
		// Remove any redundant ints from my end.  Since I'm stored in Little
		// Endian representation, I can simply be truncated with no need to
		// shift data around.
		assert isMutable;
		int size = object.integerSlotsCount();
		if (size > 1)
		{
			if (object.rawSignedIntegerAt(size) >= 0)
			{
				while (size > 1
						&& object.rawSignedIntegerAt(size) == 0
						&& object.rawSignedIntegerAt(size - 1) >= 0)
				{
					size--;
					object.truncateWithFillerForNewIntegerSlotsCount(size);
				}
			}
			else
			{
				while (size > 1
						&& object.rawSignedIntegerAt(size) == -1
						&& object.rawSignedIntegerAt(size - 1) < 0)
				{
					size--;
					object.truncateWithFillerForNewIntegerSlotsCount(size);
				}
			}
		}
	}

	@Override @AvailMethod
	@NotNull AvailObject o_AddToInfinityCanDestroy (
		final @NotNull AvailObject object,
		final Sign sign,
		final boolean canDestroy)
	{
		return sign == Sign.POSITIVE
			? InfinityDescriptor.positiveInfinity()
			: InfinityDescriptor.negativeInfinity();
	}

	@Override @AvailMethod
	@NotNull AvailObject o_AddToIntegerCanDestroy (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anInteger,
		final boolean canDestroy)
	{
		// This routine would be much quicker with access to machine carry
		// flags, but Java doesn't let us actually go down to the metal (nor do
		// C and C++). Our best recourse without reverting to assembly language
		// is to use 64-bit.
		final int objectSize = object.integerSlotsCount();
		final int anIntegerSize = anInteger.integerSlotsCount();
		AvailObject output = null;
		if (canDestroy)
		{
			// Choose the most spacious one to destroy, but reject it if it's
			// immutable. Never choose the smaller one, even if it's the only
			// one that's mutable. If they're equal sized and mutable it doesn't
			// matter which we choose.
			if (objectSize == anIntegerSize)
			{
				output =
					isMutable
						? object
						: anInteger.descriptor().isMutable()
							? anInteger
							: null;
			}
			else if (objectSize > anIntegerSize)
			{
				output = isMutable ? object : null;
			}
			else
			{
				output = anInteger.descriptor().isMutable() ? anInteger : null;
			}
		}
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
					output = mutable().create(1);
				}
				assert output.integerSlotsCount() == 1;
				output.rawSignedIntegerAtPut(1, (int)sum);
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
			output = mutable().create(max(objectSize, anIntegerSize));
		}
		final int outputSize = output.integerSlotsCount();
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
			final AvailObject newOutput = mutable().create(outputSize + 1);
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
	AvailObject o_AddToDoubleCanDestroy (
		final AvailObject object,
		final AvailObject doubleObject,
		final boolean canDestroy)
	{
		final double d = DoubleDescriptor.addDoubleAndIntegerCanDestroy(
			doubleObject.extractDouble(),
			object,
			canDestroy);
		return DoubleDescriptor.objectFromDoubleRecycling(
			d,
			doubleObject,
			canDestroy);
	}

	@Override
	AvailObject o_AddToFloatCanDestroy (
		final AvailObject object,
		final AvailObject floatObject,
		final boolean canDestroy)
	{
		final double d = DoubleDescriptor.addDoubleAndIntegerCanDestroy(
			floatObject.extractDouble(),
			object,
			canDestroy);
		return FloatDescriptor.objectFromFloatRecycling(
			(float)d,
			floatObject,
			canDestroy);
	}

	@Override @AvailMethod
	@NotNull AvailObject o_DivideIntoInfinityCanDestroy (
		final @NotNull AvailObject object,
		final @NotNull Sign sign,
		final boolean canDestroy)
	{
		if (object.equals(zero()))
		{
			throw new ArithmeticException(
				AvailErrorCode.E_CANNOT_DIVIDE_BY_ZERO);
		}
		return object.greaterThan(zero()) ^ (sign == Sign.POSITIVE)
			? InfinityDescriptor.negativeInfinity()
			: InfinityDescriptor.positiveInfinity();
	}

	@Override @AvailMethod
	@NotNull AvailObject o_DivideIntoIntegerCanDestroy (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anInteger,
		final boolean canDestroy)
	{
		// Compute anInteger / object. Round towards ZERO. Expect the division
		// to take a lot of time, as I haven't optimized it much.
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
			return object.subtractFromIntegerCanDestroy(zero(), canDestroy)
				.divideIntoIntegerCanDestroy(anInteger, canDestroy)
				.subtractFromIntegerCanDestroy(zero(), canDestroy);
		}
		if (anInteger.lessThan(zero()))
		{
			return object
				.divideIntoIntegerCanDestroy(
					anInteger.subtractFromIntegerCanDestroy(zero(), canDestroy),
					canDestroy)
				.subtractFromIntegerCanDestroy(zero(), canDestroy);
		}
		if (object.isInt() && anInteger.isInt())
		{
			final long quotient = ((long)anInteger.extractInt())
				/ ((long)object.extractInt());
			// NOTE:  This test can ONLY fail for -2^31/-1 (which is a *long*).
			if (quotient == (int)quotient)
			{
				// Yes, it fits.  Clobber one of the inputs, or create a new
				// int-sized object if they were both immutable...
				AvailObject output = null;
				if (canDestroy)
				{
					if (isMutable)
					{
						output = object;
					}
					else if (anInteger.descriptor().isMutable())
					{
						output = anInteger;
					}
				}
				if (output == null)
				{
					output = mutable().create(1);
				}
				assert output.integerSlotsCount() == 1;
				output.rawSignedIntegerAtPut(1, (int)quotient);
				return output;
			}
			// Doesn't fit.  Worst case: -2^31 / -1 = 2^31, which easily fits in
			// 64 bits, even with the sign.
			return fromLong(quotient);
		}
		// Both integers are now positive, and the divisor is not zero. That
		// simplifies things quite a bit. Ok, we need to keep estimating the
		// quotient and reverse multiplying until our remainder is in
		// [0..divisor - 1]. Each pass through the loop we estimate
		// partialQuotient = remainder / object. This should be accurate to
		// about 50 bits. We then set remainder = remainder - (partialQuotient *
		// object). If remainder goes negative (due to overestimation), we
		// toggle a flag (saying whether it represents a positive or negative
		// quantity), then negate it to make it a positive integer. Also, we
		// either add partialQuotient to the fullQuotient or subtract it,
		// depending on the setting of this flag. At the end, we adjust the
		// remainder (in case it represents a negative value) and quotient
		// (accordingly). Note that we're using double precision floating point
		// math to do the estimation. Not all processors will do this well, so a
		// 32-bit fixed-point division estimation might be a more 'portable' way
		// to do this. The rest of the algorithm would stay the same, however.

		AvailObject remainder = anInteger;
		boolean remainderIsReallyNegative = false;
		AvailObject fullQuotient = zero();
		AvailObject partialQuotient = zero();

		final int divisorSlotsCount = object.integerSlotsCount();
		// Power of two by which to scale doubleDivisor to get actual value
		final long divisorScale = (divisorSlotsCount - 1) * 32L;
		final long divisorHigh = object.rawUnsignedIntegerAt(divisorSlotsCount);
		final long divisorMedium = divisorSlotsCount > 1
			? object.rawUnsignedIntegerAt(divisorSlotsCount - 1)
			: 0;
		final long divisorLow = divisorSlotsCount > 2
			? object.rawUnsignedIntegerAt(divisorSlotsCount - 2)
			: 0;
		final double doubleDivisor =
			scalb((double)divisorLow, -64) +
			scalb((double)divisorMedium, -32) +
			divisorHigh;

		while (remainder.greaterOrEqual(object))
		{
			// Estimate partialQuotient = remainder / object, using the
			// uppermost 3 words of each. Allow a slightly negative remainder
			// due to rounding.  Compensate at the bottom of the loop.
			final int dividendSlotsCount = remainder.integerSlotsCount();
		   // Power of two by which to scale doubleDividend to get actual value
			final long dividendScale = (dividendSlotsCount - 1) * 32L;
			final long dividendHigh =
				remainder.rawUnsignedIntegerAt(dividendSlotsCount);
			final long dividendMedium = dividendSlotsCount > 1
				? remainder.rawUnsignedIntegerAt(dividendSlotsCount - 1)
				: 0;
			final long dividendLow = dividendSlotsCount > 2
				? remainder.rawUnsignedIntegerAt(dividendSlotsCount - 2)
				: 0;
			final double doubleDividend =
				scalb((double)dividendLow, -64) +
				scalb((double)dividendMedium, -32) +
				dividendHigh;

			// Divide the doubles to estimate remainder / object. The estimate
			// should be very good since we extracted 96 bits of data, only
			// about 33 of which could be leading zero bits. The mantissas are
			// basically filled with as many bits as they can hold, so the
			// division should produce about as many bits of useful output.
			// After suitable truncation and conversion to an integer, this
			// quotient should produce about 50 bits of the final result.
			//
			// It's not obvious that it always converges, but here's my
			// reasoning. The first pass produces 50 accurate bits of quotient
			// (or completes by producing a small enough remainder). The
			// remainder from this pass is used as the dividend in the next
			// pass, and this is always at least 50 bits smaller than the
			// previous dividend. Eventually this leads to a remainder within a
			// factor of two of the dividend.
			//
			// Now say we have a very large divisor and that the first 50+ bits
			// of the divisor and (remaining) dividend agree exactly. In that
			// case the estimated division will still make progress, because it
			// will produce exactly 1.0d as the quotient, which causes the
			// remainder to decrease to <= the divisor (because we already got
			// it to within a factor of two above), thus terminating the loop.
			// Note that the quotient can't be <1.0d (when quotientScale is also
			// zero), since that can only happen when the remainder is truly
			// less than the divisor, which would have caused an exit after the
			// previous iteration. If it's >1.0d (or =1.0d) then we are making
			// progress each step, eliminating 50 actual bits, except on the
			// final iteration which must converge in at most one more step.
			// Note that we could have used just a few bits in the floating
			// point division and still always converged in time proportional to
			// the difference in bit lengths divided by the number
			final double doubleQuotient = doubleDividend / doubleDivisor;
			final long quotientScale = dividendScale - divisorScale;
			assert quotientScale >= 0L;

			// Include room for sign bit plus safety margin.
			partialQuotient = mutable().create(
				(int)((quotientScale + 2 >> 5) + 1));

			final long bitShift = quotientScale
				- (((long) partialQuotient.integerSlotsCount() - 1) << 5L);
			assert -100L < bitShift && bitShift < 100L;
			double scaledDoubleQuotient = scalb(doubleQuotient, (int)bitShift);
			for (int i = partialQuotient.integerSlotsCount(); i >= 1; --i)
			{
				long word = 0;
				if (scaledDoubleQuotient != 0.0d)
				{
					word = (long)scaledDoubleQuotient;
					assert word >= 0 && word <= 0xFFFFFFFFL;
					scaledDoubleQuotient -= word;
					scaledDoubleQuotient = scalb(scaledDoubleQuotient, 32);
				}
				partialQuotient.rawSignedIntegerAtPut(i, (int)word);
			}
			partialQuotient.trimExcessInts();

			if (remainderIsReallyNegative)
			{
				fullQuotient =
					partialQuotient.subtractFromIntegerCanDestroy(
						fullQuotient, false);
			}
			else
			{
				fullQuotient =
					partialQuotient.addToIntegerCanDestroy(fullQuotient, false);
			}
			remainder = remainder.noFailMinusCanDestroy(
				partialQuotient.noFailTimesCanDestroy(object, false),
				false);
			if (remainder.lessThan(zero()))
			{
				// Oops, we overestimated the partial quotient by a little bit.
				// I would guess this never gets much more than one part in
				// 2^50. Because of the way we've done the math, when the
				// problem is small enough the estimated division is always
				// correct. So we don't need to worry about this case near the
				// end, just when there are plenty of digits of accuracy
				// produced by the estimated division. So instead of correcting
				// the partial quotient and remainder, we simply toggle a flag
				// indicating whether the remainder we're actually dealing with
				// is positive or negative, and then negate the remainder to
				// keep it positive.
				remainder = remainder.subtractFromIntegerCanDestroy(
					zero(),
					false);
				remainderIsReallyNegative = !remainderIsReallyNegative;
			}
		}
		// At this point, we really have a remainder in [-object+1..object-1].
		// If the remainder is less than zero, adjust it to make it positive.
		// This just involves adding the divisor to it while decrementing the
		// quotient because the divisor doesn't quite go into the dividend as
		// many times as we thought.
		if (remainderIsReallyNegative && remainder.greaterThan(zero()))
		{
			// We fix the sign of remainder then add object all in one fell
			// swoop.
			remainder = remainder.subtractFromIntegerCanDestroy(object, false);
			fullQuotient =
				one().subtractFromIntegerCanDestroy(fullQuotient, false);
		}
		assert remainder.greaterOrEqual(zero()) && remainder.lessThan(object);
		return fullQuotient;
	}

	@Override
	public AvailObject o_DivideIntoDoubleCanDestroy (
		final AvailObject object,
		final AvailObject doubleObject,
		final boolean canDestroy)
	{
		// This one is tricky.  The integer might be bigger than the maximum
		// double, but the product with a very small double may produce a value
		// that is still in range.  Avoid the overflow in that case by working
		// with a scaled down version of the integer: target a "significand"
		// below about 2^96.
		final int scale = Math.max(object.integerSlotsCount() - 4, 0) * 32;
		final double scaledIntAsDouble = extractDoubleScaled(object, scale);
		assert !Double.isInfinite(scaledIntAsDouble);
		final double scaledQuotient =
			doubleObject.extractDouble() / scaledIntAsDouble;
		final double quotient = Math.scalb(scaledQuotient, scale);
		return DoubleDescriptor.objectFromDoubleRecycling(
			quotient,
			doubleObject,
			canDestroy);
	}

	@Override
	public AvailObject o_DivideIntoFloatCanDestroy (
		final AvailObject object,
		final AvailObject floatObject,
		final boolean canDestroy)
	{
		// This one is tricky.  The integer might be bigger than the maximum
		// double, but the product with a very small double may produce a value
		// that is still in range of a float.  Actually, I doubt this is
		// possible, but it's easier to just make it match the double case.
		// Avoid the overflow by working with a scaled down version of the
		// integer: target a "significand" below about 2^96.
		final int scale = Math.max(object.integerSlotsCount() - 4, 0) * 32;
		final double scaledIntAsDouble = extractDoubleScaled(object, scale);
		assert !Double.isInfinite(scaledIntAsDouble);
		final double scaledQuotient =
			floatObject.extractDouble() / scaledIntAsDouble;
		final double quotient = Math.scalb(scaledQuotient, scale);
		return FloatDescriptor.objectFromFloatRecycling(
			(float)quotient,
			floatObject,
			canDestroy);
	}

	@Override @AvailMethod
	@NotNull AvailObject o_MultiplyByInfinityCanDestroy (
		final @NotNull AvailObject object,
		final @NotNull Sign sign,
		final boolean canDestroy)
	{
		if (object.equals(zero()))
		{
			throw new ArithmeticException(
				AvailErrorCode.E_CANNOT_MULTIPLY_ZERO_AND_INFINITY);
		}
		return object.greaterThan(zero()) ^ (sign == Sign.POSITIVE)
			? InfinityDescriptor.negativeInfinity()
			: InfinityDescriptor.positiveInfinity();
	}

	@Override @AvailMethod
	@NotNull AvailObject o_MultiplyByIntegerCanDestroy (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anInteger,
		final boolean canDestroy)
	{
		AvailObject output = null;
		if (object.isInt() && anInteger.isInt())
		{
			// See if the (signed) product will fit in 32 bits, the most common
			// case by far.
			final long prod = ((long)object.extractInt())
				* ((long)anInteger.extractInt());
			if (prod == (int)prod)
			{
				// Yes, it fits.  Clobber one of the inputs, or create a new
				// int-sized object if they were both immutable...
				if (canDestroy)
				{
					if (isMutable)
					{
						output = object;
					}
					else if (anInteger.descriptor().isMutable())
					{
						output = anInteger;
					}
				}
				if (output == null)
				{
					output = mutable().create(1);
				}
				assert output.integerSlotsCount() == 1;
				output.rawSignedIntegerAtPut(1, (int)prod);
				return output;
			}
			// Doesn't fit.  Worst case: -2^31 * -2^31 = +2^62, which fits in 64
			// bits, even with the sign.
			return fromLong(prod);
		}
		final int size1 = object.integerSlotsCount();
		final int size2 = anInteger.integerSlotsCount();
		// The following is a safe upper bound.  See below.
		final int targetSize = size1 + size2;
		output = mutable().create(targetSize);
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
			output.rawSignedIntegerAtPut(i, (int)low);
			low = high & 0xFFFFFFFFL;
			high >>>= 32;
		}
		// We can safely ignore any remaining bits from the partial products.
		output.trimExcessInts();
		return output;
	}

	@Override
	public AvailObject o_MultiplyByDoubleCanDestroy (
		final AvailObject object,
		final AvailObject doubleObject,
		final boolean canDestroy)
	{
		// This one is tricky.  The integer might be bigger than the maximum
		// double, but the product with a very small double may produce a value
		// that is still in range.  Avoid the overflow in that case by working
		// with a scaled down version of the integer: target a "significand"
		// below about 2^96.
		final int scale = Math.max(object.integerSlotsCount() - 4, 0) * 32;
		final double scaledIntAsDouble = extractDoubleScaled(object, scale);
		assert !Double.isInfinite(scaledIntAsDouble);
		final double scaledProduct =
			doubleObject.extractDouble() * scaledIntAsDouble;
		final double product = Math.scalb(scaledProduct, scale);
		return DoubleDescriptor.objectFromDoubleRecycling(
			product,
			doubleObject,
			canDestroy);
	}

	@Override
	public AvailObject o_MultiplyByFloatCanDestroy (
		final AvailObject object,
		final AvailObject floatObject,
		final boolean canDestroy)
	{
		// This one is tricky.  The integer might be bigger than the maximum
		// double, but the product with a very small double may produce a value
		// that is still in range of a float.  Actually, I doubt this is
		// possible, but it's easier to just make it match the double case.
		// Avoid the overflow by working with a scaled down version of the
		// integer: target a "significand" below about 2^96.
		final int scale = Math.max(object.integerSlotsCount() - 4, 0) * 32;
		final double scaledIntAsDouble = extractDoubleScaled(object, scale);
		assert !Double.isInfinite(scaledIntAsDouble);
		final double scaledProduct =
			floatObject.extractDouble() * scaledIntAsDouble;
		final double product = Math.scalb(scaledProduct, scale);
		return FloatDescriptor.objectFromFloatRecycling(
			(float)product,
			floatObject,
			canDestroy);
	}

	@Override @AvailMethod
	@NotNull AvailObject o_SubtractFromInfinityCanDestroy (
		final @NotNull AvailObject object,
		final @NotNull Sign sign,
		final boolean canDestroy)
	{
		return sign == Sign.POSITIVE
			? InfinityDescriptor.positiveInfinity()
			: InfinityDescriptor.negativeInfinity();
	}

	@Override @AvailMethod
	@NotNull AvailObject o_SubtractFromIntegerCanDestroy (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anInteger,
		final boolean canDestroy)
	{
		// This routine would be much quicker with access to machine carry
		// flags, but Java doesn't let us actually go down to the metal (nor do
		// C and C++). Our best recourse without reverting to assembly language
		// is to use 64-bit arithmetic.
		final int objectSize = object.integerSlotsCount();
		final int anIntegerSize = anInteger.integerSlotsCount();
		AvailObject output = null;
		if (canDestroy)
		{
			// Choose the most spacious one to destroy, but reject it if it's
			// immutable. Never choose the smaller one, even if it's the only
			// one that's mutable. If they're equal sized and mutable it doesn't
			// matter which we choose.
			if (objectSize == anIntegerSize)
			{
				output = isMutable
					? object
					: anInteger.descriptor().isMutable()
						? anInteger
						: null;
			}
			else if (objectSize > anIntegerSize)
			{
				output = isMutable ? object : null;
			}
			else
			{
				output = anInteger.descriptor().isMutable() ? anInteger : null;
			}
		}
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
					output = mutable().create(1);
				}
				assert output.integerSlotsCount() == 1;
				output.rawSignedIntegerAtPut(1, (int)diff);
				return output;
			}
			// Doesn't fit in 32 bits; use two 32-bit words.
			output = mutable().create(2);
			output.rawSignedIntegerAtPut(1, (int)diff);
			output.rawSignedIntegerAtPut(2, (int)(diff>>32));
			return output;
		}
		// Set estimatedSize to the max of the input sizes. There will only
		// rarely be an overflow and at most by one cell. Underflows should also
		// be pretty rare, and they're handled by output.trimExcessInts().
		if (output == null)
		{
			output = mutable().create(max(objectSize, anIntegerSize));
		}
		final int outputSize = output.integerSlotsCount();
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
			final AvailObject newOutput = mutable().create(outputSize + 1);
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
	public AvailObject o_SubtractFromDoubleCanDestroy (
		final AvailObject object,
		final AvailObject doubleObject,
		final boolean canDestroy)
	{
		// Compute the negative (i.e., int-double)
		final double d = DoubleDescriptor.addDoubleAndIntegerCanDestroy(
			-doubleObject.extractDouble(),
			object,
			canDestroy);
		// Negate it to produce (double-int).
		return DoubleDescriptor.objectFromDoubleRecycling(
			-d,
			doubleObject,
			canDestroy);
	}

	@Override
	public AvailObject o_SubtractFromFloatCanDestroy (
		final AvailObject object,
		final AvailObject floatObject,
		final boolean canDestroy)
	{
		// Compute the negative (i.e., int-float)
		final double d = DoubleDescriptor.addDoubleAndIntegerCanDestroy(
			-floatObject.extractDouble(),
			object,
			canDestroy);
		// Negate it to produce (float-int).
		return FloatDescriptor.objectFromFloatRecycling(
			(float)-d,
			floatObject,
			canDestroy);
	}

	@Override @AvailMethod
	Order o_NumericCompareToInteger (
		final @NotNull AvailObject object,
		final AvailObject anInteger)
	{
		final int size1 = object.integerSlotsCount();
		final int size2 = anInteger.integerSlotsCount();
		final int high1 = object.slot(RAW_SIGNED_INT_AT_, size1);
		final int high2 = anInteger.slot(RAW_SIGNED_INT_AT_, size2);
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
			final int a = object.slot(RAW_SIGNED_INT_AT_, i);
			final int b = anInteger.slot(RAW_SIGNED_INT_AT_, i);
			if (a != b)
			{
				return (a & 0xFFFFFFFFL) < (b & 0xFFFFFFFFL) ? LESS : MORE;
			}
		}
		return EQUAL;
	}

	@Override @AvailMethod
	@NotNull Order o_NumericCompareToInfinity (
		final @NotNull AvailObject object,
		final @NotNull Sign sign)
	{
		return sign == Sign.POSITIVE ? LESS : MORE;
	}

	@Override @AvailMethod
	@NotNull Order o_NumericCompareToDouble (
		final @NotNull AvailObject object,
		final double aDouble)
	{
		return
			DoubleDescriptor.compareDoubleAndInteger(aDouble, object).reverse();
	}

	@Override @AvailMethod
	@NotNull AvailObject o_BitwiseAnd (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anInteger,
		final boolean canDestroy)
	{
		final int objectSize = object.integerSlotsCount();
		final int anIntegerSize = anInteger.integerSlotsCount();
		AvailObject output = null;
		if (canDestroy)
		{
			// Choose the most spacious one to destroy, but reject it if it's
			// immutable. Never choose the smaller one, even if it's the only
			// one that's mutable. If they're equal sized and mutable it doesn't
			// matter which we choose.
			if (objectSize == anIntegerSize)
			{
				output =
					isMutable
						? object
						: anInteger.descriptor().isMutable()
							? anInteger
							: null;
			}
			else if (objectSize > anIntegerSize)
			{
				output = isMutable ? object : null;
			}
			else
			{
				output = anInteger.descriptor().isMutable() ? anInteger : null;
			}
		}
		// Both integers are 32 bits. This is by far the most case case.
		if (objectSize == 1 && anIntegerSize == 1)
		{
			final int result = object.rawSignedIntegerAt(1)
				& anInteger.rawSignedIntegerAt(1);
			if (output == null)
			{
				output = mutable().create(1);
			}
			output.rawSignedIntegerAtPut(1, result);
			return output;
		}
		// If neither of the inputs were suitable for destruction, then allocate
		// a new one whose size is that of the larger input.
		if (output == null)
		{
			output = mutable().create(max(objectSize, anIntegerSize));
		}
		// Handle larger integers.
		final int outputSize = output.integerSlotsCount();
		final int extendedObject = object.rawSignedIntegerAt(objectSize) >> 31;
		final int extendedAnInteger =
			anInteger.rawSignedIntegerAt(anIntegerSize) >> 31;
		for (int i = 1; i <= outputSize; i++)
		{
			final int objectWord = i > objectSize
				? extendedObject
				: object.rawSignedIntegerAt(i);
			final int anIntegerWord = i > anIntegerSize
				? extendedAnInteger
				: anInteger.rawSignedIntegerAt(i);
			final int result = objectWord & anIntegerWord;
			output.rawSignedIntegerAtPut(i, result);
		}
		output.trimExcessInts();
		return output;
	}

	@Override @AvailMethod
	@NotNull AvailObject o_BitwiseOr (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anInteger,
		final boolean canDestroy)
	{
		final int objectSize = object.integerSlotsCount();
		final int anIntegerSize = anInteger.integerSlotsCount();
		AvailObject output = null;
		if (canDestroy)
		{
			// Choose the most spacious one to destroy, but reject it if it's
			// immutable. Never choose the smaller one, even if it's the only
			// one that's mutable. If they're equal sized and mutable it doesn't
			// matter which we choose.
			if (objectSize == anIntegerSize)
			{
				output =
					isMutable
						? object
						: anInteger.descriptor().isMutable()
							? anInteger
							: null;
			}
			else if (objectSize > anIntegerSize)
			{
				output = isMutable ? object : null;
			}
			else
			{
				output = anInteger.descriptor().isMutable() ? anInteger : null;
			}
		}
		// Both integers are 32 bits. This is by far the most case case.
		if (objectSize == 1 && anIntegerSize == 1)
		{
			final int result = object.rawSignedIntegerAt(1)
				| anInteger.rawSignedIntegerAt(1);
			if (output == null)
			{
				output = mutable().create(1);
			}
			output.rawSignedIntegerAtPut(1, result);
			return output;
		}
		// If neither of the inputs were suitable for destruction, then allocate
		// a new one whose size is that of the larger input.
		if (output == null)
		{
			output = mutable().create(max(objectSize, anIntegerSize));
		}
		// Handle larger integers.
		final int outputSize = output.integerSlotsCount();
		final int extendedObject = object.rawSignedIntegerAt(objectSize) >> 31;
		final int extendedAnInteger =
			anInteger.rawSignedIntegerAt(anIntegerSize) >> 31;
		for (int i = 1; i <= outputSize; i++)
		{
			final int objectWord = i > objectSize
				? extendedObject
				: object.rawSignedIntegerAt(i);
			final int anIntegerWord = i > anIntegerSize
				? extendedAnInteger
				: anInteger.rawSignedIntegerAt(i);
			final int result = objectWord | anIntegerWord;
			output.rawSignedIntegerAtPut(i, result);
		}
		output.trimExcessInts();
		return output;
	}

	@Override @AvailMethod
	@NotNull AvailObject o_BitwiseXor (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anInteger,
		final boolean canDestroy)
	{
		final int objectSize = object.integerSlotsCount();
		final int anIntegerSize = anInteger.integerSlotsCount();
		AvailObject output = null;
		if (canDestroy)
		{
			// Choose the most spacious one to destroy, but reject it if it's
			// immutable. Never choose the smaller one, even if it's the only
			// one that's mutable. If they're equal sized and mutable it doesn't
			// matter which we choose.
			if (objectSize == anIntegerSize)
			{
				output =
					isMutable
						? object
						: anInteger.descriptor().isMutable()
							? anInteger
							: null;
			}
			else if (objectSize > anIntegerSize)
			{
				output = isMutable ? object : null;
			}
			else
			{
				output = anInteger.descriptor().isMutable() ? anInteger : null;
			}
		}
		// Both integers are 32 bits. This is by far the most case case.
		if (objectSize == 1 && anIntegerSize == 1)
		{
			final int result = object.rawSignedIntegerAt(1)
				^ anInteger.rawSignedIntegerAt(1);
			if (output == null)
			{
				output = mutable().create(1);
			}
			output.rawSignedIntegerAtPut(1, result);
			return output;
		}
		// If neither of the inputs were suitable for destruction, then allocate
		// a new one whose size is that of the larger input.
		if (output == null)
		{
			output = mutable().create(max(objectSize, anIntegerSize));
		}
		// Handle larger integers.
		final int outputSize = output.integerSlotsCount();
		final int extendedObject = object.rawSignedIntegerAt(objectSize) >> 31;
		final int extendedAnInteger =
			anInteger.rawSignedIntegerAt(anIntegerSize) >> 31;
		for (int i = 1; i <= outputSize; i++)
		{
			final int objectWord = i > objectSize
				? extendedObject
				: object.rawSignedIntegerAt(i);
			final int anIntegerWord = i > anIntegerSize
				? extendedAnInteger
				: anInteger.rawSignedIntegerAt(i);
			final int result = objectWord ^ anIntegerWord;
			output.rawSignedIntegerAtPut(i, result);
		}
		output.trimExcessInts();
		return output;
	}

	@Override @AvailMethod
	SerializerOperation o_SerializerOperation (
		final @NotNull AvailObject object)
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
		final @NotNull AvailObject object,
		final Class<?> classHint)
	{
		// Force marshaling to java.math.BigInteger.
		if (BigInteger.class.equals(classHint))
		{
			return object.asBigInteger();
		}
		// Force marshaling to Java's primitive long type.
		else if (Long.TYPE.equals(classHint))
		{
			if (!object.isLong())
			{
				throw new MarshalingException();
			}
			return Long.valueOf(object.extractLong());
		}
		// Force marshaling to Java's primitive int type.
		else if (Integer.TYPE.equals(classHint))
		{
			if (!object.isInt())
			{
				throw new MarshalingException();
			}
			return Integer.valueOf(object.extractInt());
		}
		// Force marshaling to Java's primitive short type.
		else if (Short.TYPE.equals(classHint))
		{
			if (!object.isSignedShort())
			{
				throw new MarshalingException();
			}
			return Short.valueOf(object.extractSignedShort());
		}
		// Force marshaling to Java's primitive byte type.
		else if (Byte.TYPE.equals(classHint))
		{
			if (!object.isSignedByte())
			{
				throw new MarshalingException();
			}
			return Byte.valueOf(object.extractSignedByte());
		}
		// No useful hint was provided, so marshal to the smallest primitive
		// integral type able to express object's value.
		if (object.isLong())
		{
			if (object.isInt())
			{
				if (object.isSignedShort())
				{
					if (object.isSignedByte())
					{
						return Byte.valueOf(object.extractSignedByte());
					}
					return Short.valueOf(object.extractSignedShort());
				}
				return Integer.valueOf(object.extractInt());
			}
			return Long.valueOf(object.extractLong());
		}
		return object.asBigInteger();
	}

	/**
	 * Answer a {@link BigInteger} that is the numerical equivalent of the given
	 * object, which is an {@linkplain IntegerDescriptor Avail integer}.
	 */
	@Override @AvailMethod
	public @NotNull BigInteger o_AsBigInteger (
		final @NotNull AvailObject object)
	{
		final int integerCount = object.integerSlotsCount();
		if (integerCount <= 2)
		{
			return BigInteger.valueOf(object.extractLong());
		}
		final byte[] bytes = new byte[integerCount << 2];
		for (int i = integerCount, b = 0; i > 0; i--)
		{
			final int integer = object.slot(RAW_SIGNED_INT_AT_, i);
			bytes[b++] = (byte) (integer >> 24);
			bytes[b++] = (byte) (integer >> 16);
			bytes[b++] = (byte) (integer >> 8);
			bytes[b++] = (byte) integer;
		}
		return new BigInteger(bytes);
	}

	/**
	 * Create any instances of {@link AvailObject} that need to be present for
	 * basic Avail operations like arithmetic to work correctly.  In particular,
	 * generate the array of immutable Avail {@linkplain IntegerDescriptor
	 * integers} in the range 0..255, inclusive.
	 */
	static void createWellKnownObjects ()
	{
		immutableByteObjects = new AvailObject [256];
		for (int i = 0; i <= 255; i++)
		{
			final AvailObject object = mutable().create(1);
			object.rawSignedIntegerAtPut(1, i);
			object.makeImmutable();
			immutableByteObjects[i] = object;
		}
	}

	/**
	 * Clear any instances that are no longer essential when there is no longer
	 * a need for any {@link AvailObject}s.  Also create any simple tables of
	 * non-AvailObjects, such as the table of hash values for all unsigned
	 * bytes.
	 */
	static void clearWellKnownObjects ()
	{
		immutableByteObjects = null;
		hashesOfUnsignedBytes = new int [256];
		for (int i = 0; i <= 255; i++)
		{
			hashesOfUnsignedBytes[i] = computeHashOfInt(i);
		}
	}

	/**
	 * Convert the specified Java {@code long} into an Avail {@linkplain
	 * IntegerDescriptor integer}.
	 *
	 * @param aLong A Java {@code long}.
	 * @return An {@link AvailObject}.
	 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
	 */
	public static @NotNull AvailObject fromLong (final long aLong)
	{
		if (aLong == (aLong & 255))
		{
			return immutableByteObjects[(int) aLong];
		}
		if (aLong == (int)aLong)
		{
			final AvailObject result = mutable().create(1);
			result.rawSignedIntegerAtPut(1, (int) aLong);
			return result;
		}
		final AvailObject result = mutable().create(2);
		result.rawSignedIntegerAtPut(1, (int) aLong);
		result.rawSignedIntegerAtPut(2, (int) (aLong >> 32L));
		return result;
	}

	/**
	 * Create an {@linkplain IntegerDescriptor Avail integer} that is the
	 * numerical equivalent of the given Java {@link BigInteger}.
	 *
	 * @param bigInteger The BigInteger to convert.
	 * @return An Avail integer representing the same number as the argument.
	 */
	public static @NotNull AvailObject fromBigInteger (
		final @NotNull BigInteger bigInteger)
	{
		final byte[] bytes = bigInteger.toByteArray();
		if (bytes.length <= 8)
		{
			return fromLong(bigInteger.longValue());
		}
		final int intCount = (bytes.length + 3) >> 2;
		final AvailObject result = mutable().create(intCount);
		// Start with the least significant bits.
		int byteIndex = bytes.length - 1;
		for (int destIndex = 1; destIndex < intCount; destIndex++)
		{
			final int intValue =
				(bytes[byteIndex] & 255) +
				((bytes[byteIndex - 1] & 255) << 8) +
				((bytes[byteIndex - 2] & 255) << 16) +
				((bytes[byteIndex - 3] & 255) << 24);
			result.rawSignedIntegerAtPut(destIndex, intValue);
			byteIndex -= 4;
		}
		// Do the highest order int specially, low to high bytes.
		final int signByte = ((bytes[byteIndex] >> 7) & 1) * 255;
		int intValue = bytes[byteIndex] & 255;
		intValue += (byteIndex >= 1 ? bytes[byteIndex - 1] : signByte) << 8;
		intValue += (byteIndex >= 2 ? bytes[byteIndex - 2] : signByte) << 8;
		intValue += (byteIndex >= 3 ? bytes[byteIndex - 3] : signByte) << 8;
		result.rawSignedIntegerAtPut(intCount, intValue);
		result.trimExcessInts();
		return result;
	}


	/**
	 * Answer an Avail {@linkplain IntegerDescriptor integer} that holds the
	 * truncation of the {@code double} argument, rounded towards zero.
	 *
	 * @param aDouble
	 *            The object whose truncation should be encoded as an Avail
	 *            integer.
	 * @return An Avail integer.
	 */
	public static @NotNull AvailObject truncatedFromDouble (
		final double aDouble)
	{
		// Extract the top three 32-bit sections.  That guarantees 65 bits
		// of mantissa, which is more than a double actually captures.
		double truncated = aDouble;
		if (truncated >= Long.MIN_VALUE && truncated <= Long.MAX_VALUE)
		{
			// Common case -- it fits in a long.
			return IntegerDescriptor.fromLong((long)truncated);
		}
		final boolean neg = truncated < 0.0d;
		truncated = abs(truncated);
		final int exponent = getExponent(truncated);
		final int slots = exponent + 31 / 32;  // probably needs work
		AvailObject out = IntegerDescriptor.mutable().create(slots);
		truncated = scalb(truncated, (1 - slots) * 32);
		for (int i = slots; i >= 1; --i)
		{
			final long intSlice = (int) truncated;
			out.rawUnsignedIntegerAtPut(i, (int)intSlice);
			truncated -= intSlice;
			truncated = scalb(truncated, 32);
		}
		out.trimExcessInts();
		if (neg)
		{
			out = IntegerDescriptor.zero().noFailMinusCanDestroy(out, true);
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
	public static AvailObject fromInt (final int anInteger)
	{
		if (anInteger == (anInteger & 255))
		{
			return immutableByteObjects[anInteger];
		}
		final AvailObject result = mutable().create(1);
		result.rawSignedIntegerAtPut(1, anInteger);
		return result;
	}

	/**
	 * Convert the specified byte-valued Java {@code short} into an Avail
	 * {@linkplain IntegerDescriptor integer}.
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
		final @NotNull AvailObject object,
		final int exponentBias)
	{
		// Extract and scale the top three ints from anInteger, if available.
		// This guarantees at least 64 correct upper bits were extracted (if
		// available), which is better than a double can represent.
		final int slotsCount = object.integerSlotsCount();
		final long high = object.rawSignedIntegerAt(slotsCount);
		double d = Math.scalb(high, ((slotsCount - 1) << 5) - exponentBias);
		if (slotsCount > 1)
		{
			final long med = (high & ~0xFFFFFFFFL)
				+ object.rawUnsignedIntegerAt(slotsCount - 1);
			d += Math.scalb(med, ((slotsCount - 2) << 5) - exponentBias);
			if (slotsCount > 2)
			{
				final long low = (high & ~0xFFFFFFFFL)
					+ object.rawUnsignedIntegerAt(slotsCount - 2);
				d += Math.scalb(low, ((slotsCount - 3) << 5) - exponentBias);
			}
		}
		return d;

	}

	/**
	 * Answer an {@link AvailObject} representing the {@linkplain
	 * IntegerDescriptor integer} zero (0).
	 *
	 * @return The Avail integer zero.
	 */
	public static AvailObject zero ()
	{
		//  Note that here we can safely return by reference.
		return immutableByteObjects[0];
	}

	/**
	 * Answer an {@link AvailObject} representing the {@linkplain
	 * IntegerDescriptor integer} one (1).
	 *
	 * @return The Avail integer one.
	 */
	public static AvailObject one ()
	{
		//  Note that here we can safely return by reference.
		return immutableByteObjects[1];
	}

	/**
	 * Answer an {@link AvailObject} representing the {@linkplain
	 * IntegerDescriptor integer} ten (10).
	 *
	 * @return The Avail integer ten.
	 */
	public static AvailObject ten ()
	{
		//  Note that here we can safely return by reference.
		return immutableByteObjects[10];
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

	/**
	 * Hash the passed {@code int}.  Note that it must have the same value as
	 * what {@link #computeHashOfIntegerObject(AvailObject)} would return, given
	 * an encoding of the {@code int} as an Avail {@linkplain IntegerDescriptor
	 * integer}.
	 *
	 * @param anInt The {@code int} to hash.
	 * @return The hash of the given {@code int}.
	 */
	static int computeHashOfInt (final int anInt)
	{
		return (0x13592884 + anInt) * Multiplier ^ 0x95ffb59f;
	}

	/**
	 * Compute the hash of the given Avail {@linkplain IntegerDescriptor
	 * integer} object.  Note that if the input is within the range of an {@code
	 * int}, it should produce the same value as the equivalent invocation of
	 * {@link #computeHashOfInt(int)}.
	 *
	 * @param anIntegerObject
	 *        An Avail {@linkplain IntegerDescriptor integer} to be hashed.
	 * @return The hash of the given Avail {@linkplain IntegerDescriptor
	 *         integer}.
	 */
	static int computeHashOfIntegerObject (final AvailObject anIntegerObject)
	{
		int output = 0x13592884;
		for (int i = anIntegerObject.integerSlotsCount(); i > 0; i--)
		{
			output += anIntegerObject.rawSignedIntegerAt(i);
			output *= Multiplier;
			output ^= 0x95ffb59f;
		}
		return output;
	}

	/**
	 * An array of 256 {@code int}s, corresponding to the hashes of the values
	 * 0..255 inclusive.  Initialized via {@link #clearWellKnownObjects()}.
	 */
	static int hashesOfUnsignedBytes[] = null;

	/**
	 * An array of 256 immutable {@linkplain IntegerDescriptor integers},
	 * corresponding with the indices 0..255 inclusive.  These make many kinds
	 * of calculations much more efficient than naively constructing a fresh
	 * {@link AvailObject} unconditionally.
	 */
	static AvailObject [] immutableByteObjects = null;

	/**
	 * Construct a new {@link IntegerDescriptor}.
	 *
	 * @param isMutable
	 *        Does the {@linkplain Descriptor descriptor} represent a mutable
	 *        object?
	 */
	protected IntegerDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}

	/**
	 * The mutable {@link IntegerDescriptor}.
	 */
	private static final IntegerDescriptor mutable =
		new IntegerDescriptor(true);

	/**
	 * Answer the mutable {@link IntegerDescriptor}.
	 *
	 * @return The mutable {@link IntegerDescriptor}.
	 */
	public static IntegerDescriptor mutable ()
	{
		return mutable;
	}

	/**
	 * The immutable {@link IntegerDescriptor}.
	 */
	private static final IntegerDescriptor immutable =
		new IntegerDescriptor(false);

	/**
	 * Answer the immutable {@link IntegerDescriptor}.
	 *
	 * @return The immutable {@link IntegerDescriptor}.
	 */
	public static IntegerDescriptor immutable ()
	{
		return immutable;
	}
}
