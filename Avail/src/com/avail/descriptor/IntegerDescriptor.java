/**
 * descriptor/IntegerDescriptor.java
 * Copyright (c) 2010, Mark van Gulik.
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
import java.math.BigInteger;
import java.util.List;
import com.avail.annotations.NotNull;
import com.avail.exceptions.*;
import com.avail.exceptions.ArithmeticException;

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
extends ExtendedNumberDescriptor
{
	/**
	 * The layout of integer slots for my instances.
	 */
	public enum IntegerSlots
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

	@Override
	public int o_RawSignedIntegerAt (
		final @NotNull AvailObject object,
		final int subscript)
	{
		return object.integerSlotAt(
			IntegerSlots.RAW_SIGNED_INT_AT_,
			subscript);
	}

	@Override
	public void o_RawSignedIntegerAtPut (
		final @NotNull AvailObject object,
		final int subscript,
		final int value)
	{
		object.integerSlotAtPut(
			IntegerSlots.RAW_SIGNED_INT_AT_,
			subscript,
			value);
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
			final byte[] bytes = new byte[integerCount << 2];
			for (int i = integerCount, b = 0; i > 0; i--)
			{
				final int integer = object.integerSlotAt(
					IntegerSlots.RAW_SIGNED_INT_AT_,
					i);
				bytes[b++] = (byte) (integer >> 24);
				bytes[b++] = (byte) (integer >> 16);
				bytes[b++] = (byte) (integer >> 8);
				bytes[b++] = (byte) integer;
			}
			final BigInteger bigInteger = new BigInteger(bytes);
			aStream.append(bigInteger);
		}
	}

	@Override
	public boolean o_Equals (
		final @NotNull AvailObject object,
		final AvailObject another)
	{
		return another.equalsInteger(object);
	}

	/**
	 * Compare two integers for equality.
	 */
	@Override
	public boolean o_EqualsInteger (
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
			final int a = object.integerSlotAt(IntegerSlots.RAW_SIGNED_INT_AT_, i);
			final int b = anAvailInteger.integerSlotAt(
				IntegerSlots.RAW_SIGNED_INT_AT_,
				i);
			if (a != b)
			{
				return false;
			}
		}
		return true;
	}

	@Override
	public boolean o_GreaterThanInteger (
		final @NotNull AvailObject object,
		final AvailObject another)
	{
		final int size1 = object.integerSlotsCount();
		final int size2 = another.integerSlotsCount();
		final int high1 =
			object.integerSlotAt(IntegerSlots.RAW_SIGNED_INT_AT_, size1);
		final int high2 =
			another.integerSlotAt(IntegerSlots.RAW_SIGNED_INT_AT_, size2);
		if (high1 >= 0)
		{
			if (high2 >= 0)
			{
				if (size1 != size2)
				{
					return size1 > size2;
				}
			}
			else
			{
				return true;
			}
		}
		else
		{
			if (high2 >= 0)
			{
				return false;
			}
			if (size1 != size2)
			{
				return size1 < size2;
			}
		}
		assert size1 == size2;
		assert high1 >= 0 == high2 >= 0;
		if (high1 != high2)
		{
			return high1 > high2;
		}
		for (int i = size1 - 1; i >= 1; i--)
		{
			final int a =
				object.integerSlotAt(IntegerSlots.RAW_SIGNED_INT_AT_, i);
			final int b =
				another.integerSlotAt(IntegerSlots.RAW_SIGNED_INT_AT_, i);
			if (a != b)
			{
				return (a & 0xFFFFFFFFL) > (b & 0xFFFFFFFFL);
			}
		}
		return false;
	}

	@Override
	public boolean o_GreaterThanSignedInfinity (
		final @NotNull AvailObject object,
		final AvailObject another)
	{
		return !another.isPositive();
	}

	/**
	 * Answer whether object is an instance of a subtype of aType.  Don't
	 * generate an approximate type and do the comparison, because the
	 * approximate type will just send this message recursively.
	 */
	@Override
	public boolean o_IsInstanceOfKind (
		final @NotNull AvailObject object,
		final AvailObject aType)
	{
		if (aType.equals(TOP.o()) || aType.equals(ANY.o()))
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

	@Override
	public boolean o_LessThan (
		final @NotNull AvailObject object,
		final AvailObject another)
	{
		return another.greaterThanInteger(object);
	}

	@Override
	public boolean o_TypeEquals (
		final @NotNull AvailObject object,
		final AvailObject aType)
	{
		//  Answer whether object's type is equal to aType (known to be a type).
		//  Since my implementation of o_CanComputeHashOfType: answers
		//  true, I'm not allowed to allocate objects to figure this out.

		if (!aType.isIntegerRangeType())
		{
			return false;
		}
		if (!aType.lowerBound().equals(object))
		{
			return false;
		}
		if (!aType.lowerInclusive())
		{
			return false;
		}
		if (!aType.upperBound().equals(object))
		{
			return false;
		}
		if (!aType.upperInclusive())
		{
			return false;
		}

		return true;
	}

	@Override
	public int o_Hash (
		final @NotNull AvailObject object)
	{
		if (object.isByte())
		{
			return hashOfUnsignedByte(object.extractByte());
		}
		return computeHashOfIntegerObject(object);
	}

	@Override
	public int o_HashOfType (
		final @NotNull AvailObject object)
	{
		final int objectHash = object.hash();
		return IntegerRangeTypeDescriptor.computeHash(
			objectHash,
			objectHash,
			true,
			true);
	}

	@Override
	public boolean o_IsFinite (
		final @NotNull AvailObject object)
	{
		return true;
	}

	@Override
	public @NotNull AvailObject o_Kind (
		final @NotNull AvailObject object)
	{
		object.makeImmutable();
		return IntegerRangeTypeDescriptor.singleInteger(object);
	}

	@Override
	public @NotNull AvailObject o_DivideCanDestroy (
			final @NotNull AvailObject object,
			final AvailObject aNumber,
			final boolean canDestroy)
		throws ArithmeticException
	{
		return aNumber.divideIntoIntegerCanDestroy(object, canDestroy);
	}

	@Override
	public @NotNull AvailObject o_MinusCanDestroy (
		final @NotNull AvailObject object,
		final AvailObject aNumber,
		final boolean canDestroy)
	{
		return aNumber.subtractFromIntegerCanDestroy(object, canDestroy);
	}

	@Override
	public @NotNull AvailObject o_PlusCanDestroy (
			final @NotNull AvailObject object,
			final @NotNull AvailObject aNumber,
			final boolean canDestroy)
		throws ArithmeticException
	{
		return aNumber.addToIntegerCanDestroy(object, canDestroy);
	}

	@Override
	public @NotNull AvailObject o_TimesCanDestroy (
			final @NotNull AvailObject object,
			final @NotNull AvailObject aNumber,
			final boolean canDestroy)
		throws ArithmeticException
	{
		return aNumber.multiplyByIntegerCanDestroy(object, canDestroy);
	}

	@Override
	public boolean o_IsInt (
		final @NotNull AvailObject object)
	{
		return object.integerSlotsCount() == 1;
	}

	@Override
	public boolean o_IsLong (
		final @NotNull AvailObject object)
	{
		return object.integerSlotsCount() <= 2;
	}

	@Override
	public short o_ExtractByte (
		final @NotNull AvailObject object)
	{
		assert object.integerSlotsCount() == 1;
		final int value = object.rawSignedIntegerAt(1);
		assert value == (value & 255) : "Value is out of range for a byte";
		return (short)value;
	}

	@Override
	public int o_ExtractInt (
		final @NotNull AvailObject object)
	{
		assert object.integerSlotsCount() == 1 : "Integer value out of bounds";
		return object.rawSignedIntegerAt(1);
	}

	/**
	 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
	 */
	@Override
	public long o_ExtractLong (final @NotNull AvailObject object)
	{
		assert
			object.integerSlotsCount() >= 1 && object.integerSlotsCount() <= 2
			: "Integer value out of bounds";

		if (object.integerSlotsCount() == 1)
		{
			return object.rawSignedIntegerAt(1);
		}

		long value = object.rawSignedIntegerAt(1);
		value |= object.rawSignedIntegerAt(2) << 32L;
		return value;
	}

	@Override
	public byte o_ExtractNybble (
		final @NotNull AvailObject object)
	{
		assert object.integerSlotsCount() == 1;
		final int value = object.rawSignedIntegerAt(1);
		assert value >= 0 && value <= 15 : "Value is out of range for a nybble";
		return (byte)value;
	}

	@Override
	public boolean o_IsByte (
		final @NotNull AvailObject object)
	{
		if (object.integerSlotsCount() > 1)
		{
			return false;
		}
		final int value = object.extractInt();
		return value >= 0 && value <= 255;
	}

	@Override
	public boolean o_IsNybble (
		final @NotNull AvailObject object)
	{
		if (object.integerSlotsCount() > 1)
		{
			return false;
		}
		final int value = object.extractInt();
		return value >= 0 && value <= 15;
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
	@Override
	public long o_RawUnsignedIntegerAt (
		final @NotNull AvailObject object,
		final int subscript)
	{
		final int signedInt = object.integerSlotAt(
			IntegerSlots.RAW_SIGNED_INT_AT_,
			subscript);
		return signedInt & 0xFFFFFFFFL;
	}

	/**
	 * Manually constructed accessor method.  Overwrite the quad-byte using the
	 * native byte-ordering, but using little endian between quad-bytes (i.e.,
	 * least significant quad comes first).
	 */
	@Override
	public void o_RawUnsignedIntegerAtPut (
		final @NotNull AvailObject object,
		final int subscript,
		final int value)
	{
		object.integerSlotAtPut(
			IntegerSlots.RAW_SIGNED_INT_AT_,
			subscript,
			value);
	}

	@Override
	public void o_TrimExcessInts (
		final @NotNull AvailObject object)
	{
		// Remove any redundant longs from my end.  Since I'm stored in Little
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

	@Override
	public @NotNull AvailObject o_AddToInfinityCanDestroy (
		final @NotNull AvailObject object,
		final AvailObject anInfinity,
		final boolean canDestroy)
	{
		return anInfinity;
	}

	@Override
	public @NotNull AvailObject o_AddToIntegerCanDestroy (
		final @NotNull AvailObject object,
		final AvailObject anInteger,
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
			return objectFromLong(sum);
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
	public @NotNull AvailObject o_DivideIntoInfinityCanDestroy (
			final @NotNull AvailObject object,
			final @NotNull AvailObject anInfinity,
			final boolean canDestroy)
		throws ArithmeticException
	{
		if (object.equals(zero()))
		{
			throw new ArithmeticException(
				AvailErrorCode.E_CANNOT_DIVIDE_BY_ZERO);
		}
		return object.greaterThan(zero()) ^ anInfinity.isPositive()
			? InfinityDescriptor.negativeInfinity()
			: InfinityDescriptor.positiveInfinity();
	}

	@Override
	public @NotNull AvailObject o_DivideIntoIntegerCanDestroy (
			final @NotNull AvailObject object,
			final @NotNull AvailObject anInteger,
			final boolean canDestroy)
		throws ArithmeticException
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
				- ((long) partialQuotient.integerSlotsCount() - 1 << 5L);
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
	public @NotNull AvailObject o_MultiplyByInfinityCanDestroy (
			final @NotNull AvailObject object,
			final @NotNull AvailObject anInfinity,
			final boolean canDestroy)
		throws ArithmeticException
	{
		if (object.equals(zero()))
		{
			throw new ArithmeticException(
				AvailErrorCode.E_CANNOT_MULTIPLY_ZERO_AND_INFINITY);
		}
		return object.greaterThan(zero()) ^ anInfinity.isPositive()
			? InfinityDescriptor.negativeInfinity()
			: InfinityDescriptor.positiveInfinity();
	}

	@Override
	public @NotNull AvailObject o_MultiplyByIntegerCanDestroy (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anInteger,
		final boolean canDestroy)
	{
		AvailObject output = null;
		if (object.isInt() && anInteger.isInt())
		{
			// See if the (signed) product will fit in 32 bits, the most common
			// case by far.
			final long prod = object.extractInt() * anInteger.extractInt();
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
			return objectFromLong(prod);
		}
		final int size1 = object.integerSlotsCount();
		final int size2 = anInteger.integerSlotsCount();
		// The following is a safe upper bound.  See below.
		final int targetSize = size1 + size2;
		output = mutable().create(targetSize);
		final long extension1 = object.rawSignedIntegerAt(size1) >> 31;
		final long extension2 = anInteger.rawSignedIntegerAt(size2) >> 31;

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
				long prod = k > size1
					? extension1
					: object.rawUnsignedIntegerAt(k);
				prod *= m > size2
					? extension2
					: anInteger.rawUnsignedIntegerAt(m);
				low += prod;
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
	public @NotNull AvailObject o_SubtractFromInfinityCanDestroy (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anInfinity,
		final boolean canDestroy)
	{
		return anInfinity;
	}

	@Override
	public @NotNull AvailObject o_SubtractFromIntegerCanDestroy (
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
			object.rawSignedIntegerAt(objectSize) >> 31 & 0xFFFFFFFFL;
		final long extendedAnInteger =
			anInteger.rawSignedIntegerAt(anIntegerSize) >> 31 & 0xFFFFFFFFL;
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

	/**
	 * The maximum code point of a character, as an Avail {@linkplain
	 * IntegerDescriptor integer}.
	 */
	private static AvailObject maxCharacterCodePoint;

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
		maxCharacterCodePoint = fromInt(
			CharacterDescriptor.maxCodePointInt);
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
		maxCharacterCodePoint = null;
	}

	/**
	 * Convert the specified Java {@code long} into an Avail {@linkplain
	 * IntegerDescriptor integer}.
	 *
	 * @param anInteger A Java {@code long}.
	 * @return An {@link AvailObject}.
	 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
	 */
	public static @NotNull AvailObject objectFromLong (final long anInteger)
	{
		if (anInteger >= 0 && anInteger <= 255)
		{
			return immutableByteObjects[(int) anInteger];
		}
		if (anInteger >= Integer.MIN_VALUE && anInteger <= Integer.MAX_VALUE)
		{
			final AvailObject result = mutable().create(1);
			result.rawSignedIntegerAtPut(1, (int) anInteger);
			return result;
		}
		final AvailObject result = mutable().create(2);
		result.rawSignedIntegerAtPut(1, (int) anInteger);
		result.rawSignedIntegerAtPut(2, (int) (anInteger >> 32L));
		return result;
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
		if (anInteger >= 0 && anInteger <= 255)
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
	private final static IntegerDescriptor mutable =
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
	private final static IntegerDescriptor immutable =
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
