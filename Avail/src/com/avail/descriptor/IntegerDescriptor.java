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

import static com.avail.descriptor.AvailObject.error;
import static java.lang.Math.max;
import static java.lang.Math.scalb;
import java.math.BigInteger;
import java.util.List;
import com.avail.annotations.NotNull;
import com.avail.descriptor.TypeDescriptor.Types;

@IntegerSlots("rawSignedIntegerAt#")
public class IntegerDescriptor extends ExtendedNumberDescriptor
{


	// GENERATED accessors

	@Override
	public int ObjectRawSignedIntegerAt (
		final AvailObject object,
		final int index)
	{
		//  GENERATED getter method (indexed).

		return object.integerSlotAtByteIndex(((index * 4) + 0));
	}

	@Override
	public void ObjectRawSignedIntegerAtPut (
		final AvailObject object,
		final int index,
		final int value)
	{
		//  GENERATED setter method (indexed).

		object.integerSlotAtByteIndexPut(((index * 4) + 0), value);
	}



	// java printing

	@Override
	public void printObjectOnAvoidingIndent (
		final AvailObject object,
		final StringBuilder aStream,
		final List<AvailObject> recursionList,
		final int indent)
	{
		int integerCount = object.integerSlotsCount();
		if (integerCount == 1)
		{
			aStream.append(object.rawSignedIntegerAt(1));
		}
		else if (integerCount == 2)
		{
			long integer = object.rawSignedIntegerAt(2) << 32L;
			integer |= object.rawUnsignedIntegerAt(1);
			aStream.append(integer);
		}
		else
		{
			byte[] bytes = new byte[integerCount << 2];
			for (int i = integerCount, b = 0; i > 0; i--)
			{
				int integer = object.rawSignedIntegerAt(i);
				bytes[b++] = (byte) (integer >> 24);
				bytes[b++] = (byte) (integer >> 16);
				bytes[b++] = (byte) (integer >> 8);
				bytes[b++] = (byte) integer;
			}
			BigInteger bigInteger = new BigInteger(bytes);
			aStream.append(bigInteger);
		}
	}



	// operations

	@Override
	public boolean ObjectEquals (
		final AvailObject object,
		final AvailObject another)
	{
		return another.equalsInteger(object);
	}

	@Override
	public boolean ObjectEqualsInteger (
		final AvailObject object,
		final AvailObject anAvailInteger)
	{
		//  Compare two integers for equality.

		if (object.integerSlotsCount() != anAvailInteger.integerSlotsCount())
		{
			return false;
		}
		for (int i = 1, _end1 = object.integerSlotsCount(); i <= _end1; i++)
		{
			if (object.rawUnsignedIntegerAt(i) != anAvailInteger.rawUnsignedIntegerAt(i))
			{
				return false;
			}
		}
		return true;
	}

	@Override
	public boolean ObjectGreaterThanInteger (
		final AvailObject object,
		final AvailObject another)
	{
		//  Answer true if and only if object is strictly greater than another.

		final int size1 = object.integerSlotsCount();
		final int size2 = another.integerSlotsCount();
		final int high1 = object.rawSignedIntegerAt(size1);
		final int high2 = another.rawSignedIntegerAt(size2);
		if (high1 >= 0)
		{
			if (high2 >= 0)
			{
				if (size1 > size2)
				{
					return true;
				}
				if (size1 < size2)
				{
					return false;
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
			if (size1 > size2)
			{
				return false;
			}
			if (size1 < size2)
			{
				return true;
			}
		}
		if (size1 != size2)
		{
			error("Sizes should match", object);
			return false;
		}
		if ((high1 >= 0) != (high2 >= 0))
		{
			error("Signs should match", object);
			return false;
		}
		if (high1 != high2)
		{
			return (high1 > high2);
		}
		for (int i = size1 - 1; i >= 1; i--)
		{
			long a = object.rawUnsignedIntegerAt(i);
			long b = another.rawUnsignedIntegerAt(i);
			if (a != b) return (a & 0xFFFFFFFFL) > (b & 0xFFFFFFFFL);
		}
		//  They're equal.
		return false;
	}

	@Override
	public boolean ObjectGreaterThanSignedInfinity (
		final AvailObject object,
		final AvailObject another)
	{
		return !another.isPositive();
	}

	@Override
	public boolean ObjectIsInstanceOfSubtypeOf (
		final AvailObject object,
		final AvailObject aType)
	{
		//  Answer whether object is an instance of a subtype of aType.  Don't generate
		//  an approximate type and do the comparison, because the approximate type
		//  will just send this message recursively.

		if (aType.equals(Types.voidType.object()))
		{
			return true;
		}
		if (aType.equals(Types.all.object()))
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
	public boolean ObjectLessThan (
		final AvailObject object,
		final AvailObject another)
	{
		return another.greaterThanInteger(object);
	}

	@Override
	public boolean ObjectTypeEquals (
		final AvailObject object,
		final AvailObject aType)
	{
		//  Answer whether object's type is equal to aType (known to be a type).
		//  Since my implementation of ObjectCanComputeHashOfType: answers
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
		//  ...(inclusive).
		return true;
	}

	@Override
	public boolean ObjectCanComputeHashOfType (
		final AvailObject object)
	{
		//  Answer whether object supports the #hashOfType protocol.

		return true;
	}

	@Override
	public AvailObject ObjectExactType (
		final AvailObject object)
	{
		//  Answer the object's type.

		object.makeImmutable();
		return IntegerRangeTypeDescriptor.singleInteger(object);
	}

	@Override
	public int ObjectHash (
		final AvailObject object)
	{
		//  Answer the object's hash value.

		if (object.isByte())
		{
			return IntegerDescriptor.hashOfByte(object.extractByte());
		}
		return IntegerDescriptor.computeHashOfIntegerObject(object);
	}

	@Override
	public int ObjectHashOfType (
		final AvailObject object)
	{
		//  Answer my type's hash value (without creating any objects).

		final int objectHash = object.hash();
		return IntegerRangeTypeDescriptor.computeHashFromLowerBoundHashUpperBoundHashLowerInclusiveUpperInclusive(
			objectHash,
			objectHash,
			true,
			true);
	}

	@Override
	public boolean ObjectIsFinite (
		final AvailObject object)
	{
		return true;
	}

	@Override
	public AvailObject ObjectType (
		final AvailObject object)
	{
		//  Answer the object's type.

		return ApproximateTypeDescriptor.withInstance(object.makeImmutable());
	}



	// operations-numbers

	@Override
	public AvailObject ObjectDivideCanDestroy (
		final AvailObject object,
		final AvailObject aNumber,
		final boolean canDestroy)
	{
		//  Double-dispatch it.

		return aNumber.divideIntoIntegerCanDestroy(object, canDestroy);
	}

	@Override
	public AvailObject ObjectMinusCanDestroy (
		final AvailObject object,
		final AvailObject aNumber,
		final boolean canDestroy)
	{
		//  Double-dispatch it.

		return aNumber.subtractFromIntegerCanDestroy(object, canDestroy);
	}

	@Override
	public AvailObject ObjectPlusCanDestroy (
		final AvailObject object,
		final AvailObject aNumber,
		final boolean canDestroy)
	{
		//  Double-dispatch it.

		return aNumber.addToIntegerCanDestroy(object, canDestroy);
	}

	@Override
	public AvailObject ObjectTimesCanDestroy (
		final AvailObject object,
		final AvailObject aNumber,
		final boolean canDestroy)
	{
		//  Double-dispatch it.

		return aNumber.multiplyByIntegerCanDestroy(object, canDestroy);
	}

	@Override
	public short ObjectExtractByte (
		final AvailObject object)
	{
		assert (object.integerSlotsCount() == 1);
		final int value = object.rawSignedIntegerAt(1);
		assert ((value >= 0) && (value <= 255)) : "Value is out of range for a byte";
		return ((short)(value));
	}

	@Override
	public int ObjectExtractInt (
		final AvailObject object)
	{
		assert (object.integerSlotsCount() == 1) : "Integer value out of bounds";
		return object.rawSignedIntegerAt(1);
	}

	/**
	 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
	 */
	@Override
	public long ObjectExtractLong (final @NotNull AvailObject object)
	{
		assert
			(object.integerSlotsCount() >= 1 && object.integerSlotsCount() <= 2)
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
	public byte ObjectExtractNybble (
		final AvailObject object)
	{
		assert (object.integerSlotsCount() == 1);
		final int value = object.rawSignedIntegerAt(1);
		assert ((value >= 0) && (value <= 15)) : "Value is out of range for a nybble";
		return ((byte)(value));
	}

	@Override
	public boolean ObjectIsByte (
		final AvailObject object)
	{
		if ((object.integerSlotsCount() > 1))
		{
			return false;
		}
		final int value = object.extractInt();
		return ((value >= 0) && (value <= 255));
	}

	@Override
	public boolean ObjectIsNybble (
		final AvailObject object)
	{
		if ((object.integerSlotsCount() > 1))
		{
			return false;
		}
		final int value = object.extractInt();
		return ((value >= 0) && (value <= 15));
	}



	// private-accessing

	@Override
	public long ObjectRawUnsignedIntegerAt (
		final AvailObject object,
		final int index)
	{
		//  Manually constructed accessor method.  Access the quad-byte using the native byte-ordering,
		//  but using little endian between quad-bytes (i.e., least significant quad comes first).

		return (object.integerSlotAtByteIndex(index * 4) & 0xFFFFFFFFL);
	}

	@Override
	public void ObjectRawUnsignedIntegerAtPut (
		final AvailObject object,
		final int index,
		final int value)
	{
		//  Manually constructed accessor method.  Overwrite the quad-byte using the native byte-ordering,
		//  but using little endian between quad-bytes (i.e., least significant quad comes first).

		object.integerSlotAtByteIndexPut(index * 4, value);
	}

	@Override
	public void ObjectTrimExcessLongs (
		final AvailObject object)
	{
		//  Remove any redundant longs from my end.  Since I'm stored in little endian order, just
		//  shorten the object.

		assert isMutable;
		int size = object.integerSlotsCount();
		if (size > 1)
		{
			if (object.rawSignedIntegerAt(size) >= 0)
			{
				while (size > 1 && object.rawSignedIntegerAt(size) == 0 && object.rawSignedIntegerAt(size - 1) >= 0) {
					size--;
					object.truncateWithFillerForNewIntegerSlotsCount(size);
				}
			}
			else
			{
				while (size > 1 && object.rawSignedIntegerAt(size) == -1 && object.rawSignedIntegerAt(size - 1) < 0) {
					size--;
					object.truncateWithFillerForNewIntegerSlotsCount(size);
				}
			}
		}
	}



	// private-arithmetic

	@Override
	public AvailObject ObjectAddToInfinityCanDestroy (
		final AvailObject object,
		final AvailObject anInfinity,
		final boolean canDestroy)
	{
		return anInfinity;
	}

	@Override
	public AvailObject ObjectAddToIntegerCanDestroy (
		final AvailObject object,
		final AvailObject anInteger,
		final boolean canDestroy)
	{
		//  Add the two Avail integers to produce another, destroying one if both allowed and useful.

		// This routine would be much quicker with access to machine carry flags,
		// but Java doesn't let us actually go down to the metal (nor do C and C++).
		// Our best recourse without reverting to assembly language is to use 64-bit
		// arithmetic over 32-bit words.

		int objectSize = object.integerSlotsCount();
		int anIntegerSize = anInteger.integerSlotsCount();
		AvailObject output = null;
		if (canDestroy)
		{
			// Choose the most spacious one to destroy, but reject it if it's immutable.
			// Never choose the smaller one, even if it's the only one that's mutable.
			// If they're equal sized and mutable it doesn't matter which we choose.
			if (objectSize == anIntegerSize)
			{
				output = isMutable ? object : (anInteger.descriptor().isMutable() ? anInteger : null);
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
			// See if the (signed) sum will fit in 32 bits, the most common case by far.
			long sum = ((long)object.rawSignedIntegerAt(1)) + ((long)anInteger.rawSignedIntegerAt(1));
			if (sum == (int)sum)
			{
				// Yes, it fits.  Clobber one of the inputs, or create a new object if they were both immutable...
				if (output == null)
				{
					output = AvailObject.newIndexedDescriptor(1, IntegerDescriptor.mutableDescriptor());
				}
				assert output.integerSlotsCount() == 1;
				output.rawSignedIntegerAtPut(1, (int)sum);
				return output;
			}
			// Doesn't fit in 32 bits; use two 32-bit words.
			output = AvailObject.newIndexedDescriptor(2, IntegerDescriptor.mutableDescriptor());
			output.rawSignedIntegerAtPut(1, (int)sum);
			output.rawSignedIntegerAtPut(2, (int)(sum>>32));
			return output;
		}
		//  Set estimatedSize to the max of the input sizes.  There will only rarely be an overflow
		//  and at most by one cell.  Underflows should also be pretty rare, and they're handled
		//  by output.trimExcessLongs().
		if (output == null)
		{
			output = AvailObject.newIndexedDescriptor(
				max(objectSize, anIntegerSize),
				IntegerDescriptor.mutableDescriptor());
		}
		int outputSize = output.integerSlotsCount();
		long extendedObject = object.rawSignedIntegerAt(objectSize) >> 31 & 0xFFFFFFFFL;
		long extendedAnInteger = anInteger.rawSignedIntegerAt(anIntegerSize) >> 31 & 0xFFFFFFFFL;
		long partial = 0;
		int lastInt = 0;
		// The object is always big enough to store the max of the number of quads from each
		// input, so after the loop we can check partial and the upper bit of the result to see
		// if another quad needs to be appended.
		for (int i = 1; i <= outputSize; i++)
		{
			partial += i > objectSize ? extendedObject : object.rawUnsignedIntegerAt(i);
			partial += i > anIntegerSize ? extendedAnInteger : anInteger.rawUnsignedIntegerAt(i);
			lastInt = (int)partial;
			output.rawSignedIntegerAtPut(i, lastInt);
			partial >>>= 32;
		}
		partial += extendedObject + extendedAnInteger;
		if (lastInt >> 31 != (int)partial)
		{
			// Top bit of last word no longer agrees with sign of result.  Extend it.
			AvailObject newOutput = AvailObject.newIndexedDescriptor(
				outputSize + 1,
				IntegerDescriptor.mutableDescriptor());
			for (int i = 1; i <= outputSize; i++)
			{
				newOutput.rawSignedIntegerAtPut(i, output.rawSignedIntegerAt(i));
			}
			newOutput.rawSignedIntegerAtPut(outputSize + 1, (int)partial);
			// No need to truncate it in this case.
			return newOutput;
		}
		output.trimExcessLongs();
		return output;
	}

	@Override
	public AvailObject ObjectDivideIntoInfinityCanDestroy (
		final AvailObject object,
		final AvailObject anInfinity,
		final boolean canDestroy)
	{
		if (object.equals(IntegerDescriptor.zero()))
		{
			error("Can't divide infinity (or anything else) by zero", object);
			return VoidDescriptor.voidObject();
		}
		return ((object.greaterThan(IntegerDescriptor.zero()) ^ anInfinity.isPositive()) ? InfinityDescriptor.negativeInfinity() : InfinityDescriptor.positiveInfinity());
	}

	@Override
	public AvailObject ObjectDivideIntoIntegerCanDestroy (
		final AvailObject object,
		final AvailObject anInteger,
		final boolean canDestroy)
	{
		//  Divide anInteger / object.  Round the result towards zero.

		//  Compute anInteger / object.  Round towards ZERO.  Expect the
		//  division to take a lot of time, as I haven't optimized it much.
		if (object.equals(IntegerDescriptor.zero()))
		{
			error("Division by zero");
			return VoidDescriptor.voidObject();
		}
		if (anInteger.equals(IntegerDescriptor.zero()))
		{
			return anInteger;
		}
		if (object.lessThan(IntegerDescriptor.zero()))
		{
			return object.subtractFromIntegerCanDestroy(IntegerDescriptor.zero(), canDestroy)
			.divideIntoIntegerCanDestroy(anInteger, canDestroy)
			.subtractFromIntegerCanDestroy(IntegerDescriptor.zero(), canDestroy);
		}
		if (anInteger.lessThan(IntegerDescriptor.zero()))
		{
			return object.divideIntoIntegerCanDestroy(
				anInteger.subtractFromIntegerCanDestroy(IntegerDescriptor.zero(), canDestroy),
				canDestroy)
				.subtractFromIntegerCanDestroy(IntegerDescriptor.zero(), canDestroy);
		}
		//  Both integers are now positive, and the divisor is not zero.  That simplifies things
		//  quite a bit.  Ok, we need to keep estimating the quotient and reverse multiplying
		//  until our remainder is in [0..divisor - 1].  Each pass through the loop we estimate
		//  partialQuotient = remainder / object.  This should be accurate to about 50 bits.
		//  We then set remainder = remainder - (partialQuotient * object).  If remainder goes
		//  negative (due to overestimation), we toggle a flag (saying whether it represents a
		//  positive or negative quantity), then negate it to make it a positive integer.  Also,
		//  we either add partialQuotient to the fullQuotient or subtract it, depending on the
		//  setting of this flag.  At the end, we adjust the remainder (in case it represents
		//  a negative value) and quotient (accordingly).  Note that we're using double
		//  precision floating point math to do the estimation.  Not all processors will do
		//  this well, so a 32-bit fixed-point division estimation might be a more 'portable'
		//  way to do this.  The rest of the algorithm would stay the same, however.

		AvailObject remainder = anInteger;
		boolean remainderIsReallyNegative = false;
		AvailObject fullQuotient = IntegerDescriptor.zero();
		AvailObject partialQuotient = IntegerDescriptor.zero();

		int divisorSlotsCount = object.integerSlotsCount();
		long divisorScale = (divisorSlotsCount - 1) * 32L;   // Power of two by which to scale doubleDivisor to get actual value
		long divisorHigh = object.rawUnsignedIntegerAt(divisorSlotsCount);
		long divisorMedium = divisorSlotsCount > 1 ? object.rawUnsignedIntegerAt(divisorSlotsCount - 1) : 0;
		long divisorLow = divisorSlotsCount > 2 ? object.rawUnsignedIntegerAt(divisorSlotsCount - 2) : 0;
		double doubleDivisor =
			scalb((double)divisorLow, -64) +
			scalb((double)divisorMedium, -32) +
			divisorHigh;

		while (remainder.greaterOrEqual(object))
		{
			//  Estimate partialQuotient = remainder / object, using the uppermost 3 words of each.
			//  Allow a slightly negative remainder due to rounding.  Compensate at the bottom of the loop.
			int dividendSlotsCount = remainder.integerSlotsCount();
			long dividendScale = (dividendSlotsCount - 1) * 32L;   // Power of two by which to scale doubleDividend to get actual value
			long dividendHigh = remainder.rawUnsignedIntegerAt(dividendSlotsCount);
			long dividendMedium = dividendSlotsCount > 1 ? remainder.rawUnsignedIntegerAt(dividendSlotsCount - 1) : 0;
			long dividendLow = dividendSlotsCount > 2 ? remainder.rawUnsignedIntegerAt(dividendSlotsCount - 2) : 0;
			double doubleDividend =
				scalb((double)dividendLow, -64) +
				scalb((double)dividendMedium, -32) +
				dividendHigh;

			// Divide the doubles to estimate remainder / object.  The estimate should be very good since
			// we extracted 96 bits of data, only about 33 of which could be leading zero bits.  The mantissas
			// are basically filled with as many bits as they can hold, so the division should produce about as
			// many bits of useful output.  After suitable truncation and conversion to an integer, this quotient
			// should produce about 50 bits of the final result.
			//
			// It's not obvious that it always converges, but here's my reasoning.  The first pass produces 50
			// accurate bits of quotient (or completes by producing a small enough remainder).  The remainder
			// from this pass is used as the dividend in the next pass, and this is always at least 50 bits smaller
			// than the previous dividend.  Eventually this leads to a remainder within a factor of two of the
			// dividend.
			//
			// Now say we have a very large divisor and that the first 50+ bits of the divisor and (remaining)
			// dividend agree exactly.  In that case the estimated division will still make progress, because it
			// will produce exactly 1.0d as the quotient, which causes the remainder to decrease to <= the
			// divisor (because we already got it to within a factor of two above), thus terminating the loop.
			// Note that the quotient can't be <1.0d (when quotientScale is also zero), since that can only
			// happen when the remainder is truly less than the divisor, which would have caused an exit
			// after the previous iteration.  If it's >1.0d (or =1.0d) then we are making progress each step,
			// eliminating 50 actual bits, except on the final iteration which must converge in at most one
			// more step.  Note that we could have used just a few bits in the floating point division and still
			// always converged in time proportional to the difference in bit lengths divided by the number
			// of bits being used (i.e., more slowly for fewer bits).

			double doubleQuotient = doubleDividend / doubleDivisor;
			long quotientScale = dividendScale - divisorScale;
			assert quotientScale >= 0L;

			partialQuotient = AvailObject.newIndexedDescriptor(
				(int)(((quotientScale + 2) >> 5) + 1),       // sign bit plus safety margin
				IntegerDescriptor.mutableDescriptor());

			long bitShift = quotientScale - (((long) partialQuotient.integerSlotsCount() - 1) << 5L);
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
			partialQuotient.trimExcessLongs();

			if (remainderIsReallyNegative)
			{
				fullQuotient = partialQuotient.subtractFromIntegerCanDestroy(fullQuotient, false);
			}
			else
			{
				fullQuotient = partialQuotient.addToIntegerCanDestroy(fullQuotient, false);
			}
			remainder = remainder.minusCanDestroy(object.multiplyByIntegerCanDestroy(partialQuotient, false), false);
			if (remainder.lessThan(IntegerDescriptor.zero()))
			{
				//  Oops, we overestimated the partial quotient by a little bit.  I would guess this never
				//  gets much more than one part in 2^50.  Because of the way we've done the math,
				//  when the problem is small enough the estimated division is always correct.  So we
				//  don't need to worry about this case near the end, just when there are plenty of
				//  digits of accuracy produced by the estimated division.  So instead of correcting the
				//  partial quotient and remainder, we simply toggle a flag indicating whether the
				//  remainder we're actually dealing with is positive or negative, and then negate the
				//  remainder to keep it positive.
				remainder = remainder.subtractFromIntegerCanDestroy(IntegerDescriptor.zero(), false);
				remainderIsReallyNegative = !remainderIsReallyNegative;
			}
		}
		//  At this point, we really have a remainder in [-object+1..object-1].  If the remainder is less than zero,
		//  adjust it to make it positive.  This just involves adding the divisor to it while decrementing the quotient
		//  because the divisor doesn't quite go into the dividend as many times as we thought.
		if (remainderIsReallyNegative && (remainder.greaterThan(IntegerDescriptor.zero())))
		{
			//  We fix the sign of remainder then add object all in one fell swoop.
			remainder = remainder.subtractFromIntegerCanDestroy(object, false);
			fullQuotient = IntegerDescriptor.one().subtractFromIntegerCanDestroy(fullQuotient, false);
		}
		assert remainder.greaterOrEqual(IntegerDescriptor.zero()) && (remainder.lessThan(object));
		return fullQuotient;
	}

	@Override
	public AvailObject ObjectMultiplyByInfinityCanDestroy (
		final AvailObject object,
		final AvailObject anInfinity,
		final boolean canDestroy)
	{
		if (object.equals(IntegerDescriptor.zero()))
		{
			error("Can't multiply infinity by zero", object);
			return VoidDescriptor.voidObject();
		}
		return ((object.greaterThan(IntegerDescriptor.zero()) ^ anInfinity.isPositive()) ? InfinityDescriptor.negativeInfinity() : InfinityDescriptor.positiveInfinity());
	}

	@Override
	public AvailObject ObjectMultiplyByIntegerCanDestroy (
		final AvailObject object,
		final AvailObject anInteger,
		final boolean canDestroy)
	{
		//  Assume there are no floats (for now).  Also assume no overflow.

		//  This routine makes heavy use of 64-bit integers to extract carries from 32-bit words.

		int objectSize = object.integerSlotsCount();
		int anIntegerSize = anInteger.integerSlotsCount();
		AvailObject output = null;
		if (objectSize == 1 && anIntegerSize == 1)
		{
			// See if the (signed) product will fit in 32 bits, the most common case by far.
			long prod = ((long)object.rawSignedIntegerAt(1)) * ((long)anInteger.rawSignedIntegerAt(1));
			if (prod == (int)prod)
			{
				// Yes, it fits.  Clobber one of the inputs, or create a new object if they were both immutable...
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
					output = AvailObject.newIndexedDescriptor(1, IntegerDescriptor.mutableDescriptor());
				}
				assert output.integerSlotsCount() == 1;
				output.rawSignedIntegerAtPut(1, (int)prod);
				return output;
			}
			// Doesn't fit.  Worst case: -2^31 * -2^31 = 2^62, which fits in 64 bits even with a sign.
			output = AvailObject.newIndexedDescriptor(2, IntegerDescriptor.mutableDescriptor());
			output.rawSignedIntegerAtPut(1, (int)prod);
			output.rawSignedIntegerAtPut(2, (int)(prod>>32));
			return output;
		}
		int targetSize = objectSize + anIntegerSize;      // This is a safe upper bound.  See below.
		output = AvailObject.newIndexedDescriptor(
			targetSize,
			IntegerDescriptor.mutableDescriptor());
		long extendedObject = ((object.rawSignedIntegerAt(objectSize)) >> 31);
		long extendedAnInteger = ((anInteger.rawSignedIntegerAt(anIntegerSize)) >> 31);

		//  We can't recycle storage quite as easily here as we did for addition and
		//  subtraction, because the intermediate sum would be clobbering one of the
		//  multiplicands that we (may) need to scan again.  So always allocate the
		//  new object.  The product will always fit in N+M cells if the multiplicands fit
		//  in sizes N and M cells.  For proof, consider the worst case (using bytes for
		//  the example).  In hex, -80 * -80 = +4000, which fits.  Also, 7F*7F = 3F01,
		//  and 7F*-80 = -3F80.  So no additional padding is necessary.  The scheme
		//  we will use is to compute each word of the result, low to high, using a carry
		//  of two words.  All quantities are treated as unsigned, but the multiplicands
		//  are sign-extended as needed.  Multiplying two one-word multiplicands yields
		//  a two word result, so we need to use three words to properly hold the carry (it
		//  would take more than four billion words to overflow this, and only one billion
		//  words are addressable on a 32-bit machine).  The three-word intermediate
		//  value is handled as two two-word accumulators, A and B.  B is considered
		//  shifted by a word (to the left).  The high word of A is added to the low word
		//  of B (with carry to the high word of B), and then the high word of A is cleared.
		//  This "shifts the load" to B for holding big numbers without affecting their sum.
		//  When a new two-word value needs to be added in, this trick is employed,
		//  followed by directly adding the two-word value to A, as long as we can ensure
		//  the *addition* won't overflow, which is the case if the two-word value is an
		//  unsigned product of two one-word values.  Since FF*FF=FE01, we can safely
		//  add this to 00FF (getting FF00) without overflow.  Pretty slick, huh?

		long lowPartial = 0;
		long highPartial = 0;
		for (int i = 1; i <= targetSize; ++i)
		{
			for (int k = 1; k <= i; ++k)
			{
				long prod = k > objectSize ? extendedObject : object.rawUnsignedIntegerAt(k);
				prod *= (i - k + 1) > anIntegerSize ? extendedAnInteger : anInteger.rawUnsignedIntegerAt(i - k + 1);
				lowPartial += prod;
				highPartial += lowPartial >>> 32;		// Add upper of low to lower of high (carrying to upper of high)
				lowPartial = lowPartial & 0xFFFFFFFFL;   // Subtract that same amount from low (clearing its upper word)
			}
			output.rawSignedIntegerAtPut(i, (int)lowPartial);
			lowPartial = highPartial & 0xFFFFFFFFL;
			highPartial >>>= 32;
		}
		//  We can safely ignore any remaining bits from the partial products.
		output.trimExcessLongs();
		return output;
	}

	@Override
	public AvailObject ObjectSubtractFromInfinityCanDestroy (
		final AvailObject object,
		final AvailObject anInfinity,
		final boolean canDestroy)
	{
		return anInfinity;
	}

	@Override
	public AvailObject ObjectSubtractFromIntegerCanDestroy (
		final AvailObject object,
		final AvailObject anInteger,
		final boolean canDestroy)
	{
		//  Subtract object from anInteger to produce another, destroying one of them if allowed and useful.

		// This routine would be much quicker with access to machine carry flags,
		// but Java doesn't let us actually go down to the metal (nor do C and C++).
		// Our best recourse without reverting to assembly language is to use 64-bit
		// arithmetic over 32-bit words.

		int objectSize = object.integerSlotsCount();
		int anIntegerSize = anInteger.integerSlotsCount();
		AvailObject output = null;
		if (canDestroy)
		{
			// Choose the most spacious one to destroy, but reject it if it's immutable.
			// Never choose the smaller one, even if it's the only one that's mutable.
			// If they're equal sized and mutable it doesn't matter which we choose.
			if (objectSize == anIntegerSize)
			{
				output = isMutable ? object : (anInteger.descriptor().isMutable() ? anInteger : null);
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
			// See if the (signed) difference will fit in 32 bits, the most common case by far.
			long diff = (long)anInteger.rawSignedIntegerAt(1) - (long)object.rawSignedIntegerAt(1);
			if (diff == (int)diff)
			{
				// Yes, it fits.  Clobber one of the inputs, or create a new object if they were both immutable...
				if (output == null)
				{
					output = AvailObject.newIndexedDescriptor(1, IntegerDescriptor.mutableDescriptor());
				}
				assert output.integerSlotsCount() == 1;
				output.rawSignedIntegerAtPut(1, (int)diff);
				return output;
			}
			// Doesn't fit in 32 bits; use two 32-bit words.
			output = AvailObject.newIndexedDescriptor(2, IntegerDescriptor.mutableDescriptor());
			output.rawSignedIntegerAtPut(1, (int)diff);
			output.rawSignedIntegerAtPut(2, (int)(diff>>32));
			return output;
		}
		//  Set estimatedSize to the max of the input sizes.  There will only rarely be an overflow
		//  and at most by one cell.  Underflows should also be pretty rare, and they're handled
		//  by output.trimExcessLongs().
		if (output == null)
		{
			output = AvailObject.newIndexedDescriptor(
				max(objectSize, anIntegerSize),
				IntegerDescriptor.mutableDescriptor());
		}
		int outputSize = output.integerSlotsCount();
		long extendedObject = object.rawSignedIntegerAt(objectSize) >> 31 & 0xFFFFFFFFL;
		long extendedAnInteger = anInteger.rawSignedIntegerAt(anIntegerSize) >> 31 & 0xFFFFFFFFL;
		long partial = 1;
		int lastInt = 0;
		// The object is always big enough to store the max of the number of quads from each
		// input, so after the loop we can check partial and the upper bit of the result to see
		// if another quad needs to be appended.
		for (int i = 1; i <= outputSize; i++)
		{
			partial += i > anIntegerSize ? extendedAnInteger : anInteger.rawUnsignedIntegerAt(i);
			partial += (i > objectSize ? extendedObject : object.rawUnsignedIntegerAt(i)) ^ 0xFFFFFFFFL;
			lastInt = (int)partial;
			output.rawSignedIntegerAtPut(i, lastInt);
			partial >>>= 32;
		}
		partial += extendedAnInteger + (extendedObject ^ 0xFFFFFFFFL);
		if (lastInt >> 31 != (int)partial)
		{
			// Top bit of last word no longer agrees with sign of result.  Extend it.
			AvailObject newOutput = AvailObject.newIndexedDescriptor(
				outputSize + 1,
				IntegerDescriptor.mutableDescriptor());
			for (int i = 1; i <= outputSize; i++)
			{
				newOutput.rawSignedIntegerAtPut(i, output.rawSignedIntegerAt(i));
			}
			newOutput.rawSignedIntegerAtPut(outputSize + 1, (int)partial);
			// No need to truncate it in this case.
			return newOutput;
		}
		output.trimExcessLongs();
		return output;
	}




	// Startup/shutdown
	static void createWellKnownObjects ()
	{
		immutableByteObjects = new AvailObject [256];
		for (int i = 0; i <= 255; i++)
		{
			AvailObject object = AvailObject.newIndexedDescriptor(
				1,
				IntegerDescriptor.mutableDescriptor());
			object.rawSignedIntegerAtPut(1, i);
			object.makeImmutable();
			immutableByteObjects[i] = object;
		}
	}

	static void clearWellKnownObjects ()
	{
		//  IntegerDescriptor initializeClassInstanceVariables

		immutableByteObjects = null;
		HashesOfBytes = new int [256];
		for (int i = 0; i <= 255; i++)
		{
			HashesOfBytes[i] = computeHashOfInt(i);
		}
	}



	/* Value conversion... */

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
			AvailObject result = AvailObject.newIndexedDescriptor(
				1, IntegerDescriptor.mutableDescriptor());
			result.rawSignedIntegerAtPut(1, (int) anInteger);
			return result;
		}
		AvailObject result = AvailObject.newIndexedDescriptor(
			2, IntegerDescriptor.mutableDescriptor());
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
	public static AvailObject objectFromInt (int anInteger)
	{
		if (anInteger >= 0 && anInteger <= 255)
		{
			return immutableByteObjects[anInteger];
		}
		AvailObject result = AvailObject.newIndexedDescriptor(1, IntegerDescriptor.mutableDescriptor());
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
	public static AvailObject objectFromByte (short anInteger)
	{
		assert anInteger >= 0 && anInteger <= 255;
		return immutableByteObjects[anInteger];
	}

	public static AvailObject zero ()
	{
		//  Note that here we can safely return by reference.
		return immutableByteObjects[0];
	}

	public static AvailObject one ()
	{
		//  Note that here we can safely return by reference.
		return immutableByteObjects[1];
	}

	static int hashOfByte (short anInteger)
	{
		return HashesOfBytes[anInteger];
	}


	/* Hashing */
	static int computeHashOfInt (int anInteger)
	{
		int [] map1 = ByteToWordMappings[3];
		int [] map2 = ByteToWordMappings[6];
		int [] map3 = ByteToWordMappings[2];
		int [] map4 = ByteToWordMappings[4];
		int high = 0x821E;
		int low = 0x4903;
		int residue = anInteger;
		for (int k = 3; k >=  0; --k)
		{
			high = (high + map1[residue&255]) & 0xFFFF;
			low = (low + map2[residue&255]) & 0xFFFF;
			high ^= map3[high&255];
			low ^= map4[low&255];
			residue = residue >> 8;
		}
		return ((high << 16L) + (low & 0xFFFF));
	}

	static int computeHashOfIntegerObject (AvailObject anIntegerObject)
	{
		int [] map1 = ByteToWordMappings[3];
		int [] map2 = ByteToWordMappings[6];
		int [] map3 = ByteToWordMappings[2];
		int [] map4 = ByteToWordMappings[4];
		int high = 0x821E;
		int low = 0x4903;
		int count = anIntegerObject.integerSlotsCount();
		for (int i = 1; i <= count; ++i)
		{
			int residue = anIntegerObject.rawSignedIntegerAt(i);
			for (int k = 3; k >=  0; --k)
			{
				high = (high + map1[residue&255]) & 0xFFFF;
				low = (low + map2[residue&255]) & 0xFFFF;
				high ^= map3[high&255];
				low ^= map4[low&255];
				residue = residue >>> 8;
			}
		}
		return ((high << 16L) + (low & 0xFFFF));
	}

	/* Various permutation tables for hashing... */
	final static int ByteToWordMappings[][] = {
		{
			0x1381, 0x7AA6, 0x8B20, 0x4E4A, 0x563A, 0x7707, 0x7199, 0x82F7, 0xA48E, 0xB145, 0xC1A5, 0xC867, 0xC59D, 0x65CF, 0x2454, 0x6788,
			0x302A, 0x2286, 0xC7B3, 0x1680, 0x3332, 0x7049, 0x4577, 0x84A4, 0x5EDE, 0x4792, 0x1CA3, 0x18F6, 0xDFB2, 0x75B9, 0x9227, 0x6197,
			0x2510, 0x3B61, 0x7FA9, 0x5CB7, 0xBDBF, 0xF9C5, 0xFA71, 0x0D2C, 0x81D7, 0x0960, 0x357F, 0x2C1D, 0xCEBD, 0x57A0, 0x988A, 0x207E,
			0x388B, 0xDB14, 0xCFCB, 0x7E95, 0xDE2F, 0xA61E, 0x5BC6, 0x6822, 0x7BA8, 0xF58D, 0x1EC0, 0xA568, 0xCA82, 0x506B, 0x93EA, 0x194F,
			0x48D3, 0xE5D5, 0xB2F5, 0xB339, 0xB758, 0x6DAF, 0xC931, 0x720B, 0x4D79, 0x5906, 0x9033, 0xF32D, 0x5896, 0xC3CA, 0x5F84, 0xBA15,
			0x6CA7, 0x5563, 0x6E02, 0x0376, 0x1528, 0xF15D, 0x791A, 0xD5BC, 0xBE65, 0xF7F1, 0xAFD2, 0x0F13, 0x74E0, 0xA0FB, 0x0A57, 0x0248,
			0xF46C, 0x2E72, 0x4985, 0x3AF9, 0x9918, 0x1D59, 0xDA5C, 0x5340, 0x51FC, 0xE211, 0x7C4C, 0x3C29, 0x0BD8, 0xC224, 0x8312, 0xAA8F,
			0x8A56, 0x40C3, 0x8F3B, 0xEEFA, 0x9F69, 0x109C, 0xFD66, 0x2DBA, 0x6443, 0x9693, 0x9E7B, 0x129A, 0xABDA, 0x2F19, 0xD026, 0x9791,
			0x4698, 0x62AC, 0x323F, 0x6A4D, 0x5D73, 0x526A, 0x8930, 0x3736, 0xDC94, 0xAE83, 0x63DB, 0xEB1B, 0xC0F8, 0xE942, 0x435F, 0x047D,
			0x422E, 0x80B5, 0xE33D, 0x955A, 0x2875, 0x4B23, 0x78ED, 0x0716, 0xEDAD, 0x149F, 0xFB9E, 0xE852, 0x41D1, 0x7DEB, 0xD81C, 0x4AAA,
			0xCD6F, 0xB02B, 0xB5CC, 0xC6F3, 0x3FDF, 0x2301, 0xDDB4, 0x69B8, 0x940C, 0x44C8, 0x91F0, 0xD337, 0x1B08, 0x9D35, 0xFCEC, 0x76E7,
			0xEF5B, 0x2A78, 0xB8FD, 0xE4EE, 0x8C89, 0xACC9, 0xBF53, 0x2BA1, 0x9B09, 0x0EB6, 0xBBE1, 0x5AC4, 0x069B, 0xF834, 0xB9B0, 0xA287,
			0xD90F, 0xA33E, 0xFF55, 0xF6F4, 0xE6B1, 0xB6FE, 0xCC5E, 0x4C04, 0xFE6D, 0x36FF, 0x8D8C, 0xF2DC, 0xD7C1, 0x27E3, 0xF044, 0x31E6,
			0xECE4, 0x86CD, 0xA7D6, 0x113C, 0xA8BB, 0x2905, 0xE790, 0xD14E, 0x8EF2, 0x08EF, 0x0070, 0x667C, 0xBCE2, 0x6BA2, 0xD625, 0x21C7,
			0x0521, 0x1A17, 0x0C6E, 0x1764, 0x1F47, 0xE00D, 0x39BE, 0x54CE, 0xAD74, 0x0141, 0xA900, 0x9C50, 0x85C2, 0xE1D4, 0xA14B, 0x3EE5,
			0x73E8, 0xEA0A, 0xD4AB, 0x9A7A, 0x8846, 0x4F1F, 0x6FDD, 0x2603, 0xB4AE, 0x8738, 0xD2D9, 0xC462, 0x3D0E, 0xCBE9, 0x34D0, 0x6051
		},

		{
			0xE2FF, 0xE4FC, 0xF64D, 0xD3B1, 0xAACB, 0xAD00, 0xDCEF, 0xCC88, 0xAFD2, 0xD94B, 0x0828, 0xA8AB, 0x2E2E, 0xDA04, 0xABCA, 0x72FE,
			0xBDD5, 0xF021, 0x7176, 0x3AC3, 0x7F16, 0xA08E, 0x05F0, 0x70DC, 0x83EB, 0x37BD, 0x798F, 0x0140, 0x2831, 0x9A0F, 0xFC0D, 0xF790,
			0x1EE0, 0xEAAA, 0xA152, 0xC5D6, 0xFFC7, 0x6A17, 0x5861, 0x0F68, 0xE9AC, 0x4913, 0xC3D3, 0x6181, 0x8DD7, 0xCA2F, 0x3F27, 0x8975,
			0xB250, 0xB997, 0x0393, 0xB7EE, 0x003D, 0xED07, 0xDEF5, 0xCF26, 0x1D2B, 0x2A47, 0xEE56, 0x9C6D, 0xEBED, 0x4625, 0xBCA0, 0xCDAE,
			0xC69B, 0x775A, 0x80A6, 0x8703, 0xE85E, 0x31A9, 0x3E3E, 0xE718, 0x95FB, 0xB3CD, 0x86CE, 0xEC41, 0x8296, 0xB0C4, 0xE6E6, 0x930E,
			0xC08B, 0x3CFA, 0x10D4, 0x6924, 0xB694, 0xFBE5, 0x8CE9, 0xC936, 0x9439, 0xD6C8, 0x223F, 0xA330, 0x6758, 0x25E2, 0xBB01, 0x6F6A,
			0x1763, 0x42DE, 0x754F, 0x04B3, 0x1ADA, 0xEFC6, 0x6B42, 0x334E, 0x2083, 0x2999, 0x8432, 0x48DB, 0x4DAF, 0xC85C, 0x27F8, 0xDF0A,
			0x6457, 0xB446, 0x13C1, 0x9F3A, 0x8F86, 0xB11F, 0x8BB5, 0x1F70, 0xD154, 0x851E, 0x65BA, 0x9E66, 0xB533, 0xD77E, 0x0A34, 0x5215,
			0xD2B9, 0xA7CC, 0x09F4, 0x5E09, 0x0B6C, 0x1C08, 0x8E9D, 0x608A, 0x6CE4, 0xDB05, 0xAC8C, 0xD548, 0xB810, 0x0E7D, 0x2B1C, 0x810C,
			0x0DF3, 0x210B, 0xC2A1, 0xC74C, 0x7CE3, 0x6D65, 0x4F23, 0xC47C, 0x6285, 0x02F6, 0x24F1, 0x23BB, 0xFD45, 0x45B4, 0x8ACF, 0x14F9,
			0x55EA, 0xF8D0, 0x32A5, 0x156F, 0x5FF7, 0x3D37, 0x5BD1, 0xBFAD, 0xCB73, 0xC1B2, 0x30A3, 0x985F, 0xF949, 0xBE95, 0x9282, 0x34A7,
			0xA962, 0xF112, 0x4387, 0x57E7, 0xAEE1, 0x0678, 0x5079, 0x5D2C, 0x7E59, 0x9614, 0x116E, 0xA460, 0x68B6, 0xF543, 0xFA1B, 0x4A92,
			0x5C2A, 0x9938, 0xD8E8, 0xF3BE, 0xE55D, 0xF48D, 0x919E, 0xE14A, 0xBA77, 0x191A, 0x53D9, 0x41A2, 0xA5B8, 0x1674, 0x473B, 0x3551,
			0x633C, 0x40C5, 0xE084, 0x6E1D, 0x7D19, 0x3944, 0x78D8, 0x2F02, 0x3664, 0x9D9A, 0x38C9, 0x51A8, 0xA291, 0xE306, 0x9B11, 0x5A35,
			0x669C, 0xF2FD, 0x4BA4, 0x9798, 0xD020, 0x59DF, 0x7355, 0xA669, 0x0722, 0x4EBC, 0x74B7, 0x9071, 0xDD7B, 0x0C72, 0x54C2, 0x262D,
			0x4CBF, 0xD45B, 0x2C89, 0xCE29, 0x1B80, 0x569F, 0x7667, 0x2DB0, 0xFEC0, 0x447A, 0x7B53, 0x3BDD, 0x18F2, 0x12EC, 0x887F, 0x7A6B
		},

		{
			0x12B5, 0x7CF9, 0xBB53, 0x3DB3, 0x6349, 0x0B2B, 0xB24F, 0x1D65, 0xF350, 0xC207, 0xFF9C, 0xA756, 0x93C7, 0x46A0, 0x2FFE, 0xA09D,
			0x6864, 0xBD21, 0x2891, 0x649A, 0x7E33, 0xA6E5, 0xC57F, 0x54F4, 0xB01F, 0x49BD, 0x3783, 0x030B, 0x4076, 0x8EA8, 0x580E, 0xF02F,
			0xEF2E, 0x0694, 0xC71A, 0x5F43, 0x3C02, 0x45D2, 0xB168, 0x6BDA, 0x4E57, 0x0DCC, 0x6123, 0xDEA9, 0x39DC, 0x70D1, 0x3670, 0xA399,
			0x946F, 0xF84E, 0x6596, 0x5059, 0xD6AF, 0x4AD4, 0x51D9, 0xFCAC, 0x9F93, 0x6F32, 0xD941, 0xDB87, 0xDAEE, 0xD786, 0x4D84, 0xBE36,
			0x97CD, 0xE305, 0x4C40, 0xE94A, 0x57F5, 0x4298, 0x0A9E, 0xC47E, 0xFDD3, 0xAC74, 0x8627, 0xDFAB, 0x29A5, 0x2B82, 0x1181, 0x6611,
			0x6DA3, 0x47C3, 0xF72A, 0xFA78, 0x598D, 0x8B1B, 0x821E, 0x2018, 0x2E3B, 0x1508, 0xDDBA, 0x8D39, 0x96C1, 0x02EA, 0xC08A, 0x7AE2,
			0xCFBB, 0x92E7, 0x6928, 0xE88E, 0x6280, 0x73F1, 0xD185, 0xFB0A, 0x07B8, 0xD025, 0x7B30, 0xEEF8, 0xADFB, 0xC692, 0xFE16, 0xB7FC,
			0x6A3D, 0x0960, 0x25C4, 0xBC75, 0x2762, 0x3152, 0xEBDE, 0xEABF, 0xF4F7, 0x3BC5, 0xB573, 0x0F47, 0x8F44, 0x7922, 0x198C, 0x90E1,
			0x742D, 0xF5FD, 0x8CD7, 0x6CB9, 0x4BA4, 0xE442, 0x7FB1, 0x83CA, 0x2C01, 0xF1E3, 0x164B, 0x7700, 0xA12C, 0xE18B, 0x99A7, 0xAE61,
			0x80C2, 0x437C, 0xD212, 0x3A6D, 0xCDE9, 0x1EA6, 0x7169, 0xB45A, 0x9E03, 0x179B, 0xD417, 0x0014, 0xAA1D, 0x72D6, 0xD879, 0x843C,
			0x1489, 0x7635, 0x4415, 0xE5A1, 0x9855, 0x223A, 0x78ED, 0x5AA2, 0xC8AE, 0x5C90, 0xA29F, 0xE758, 0xB3AA, 0x8166, 0xDC46, 0x2A7A,
			0x53F0, 0xD3F2, 0xA513, 0xF204, 0x3F10, 0xD588, 0x6771, 0x38CB, 0x32EC, 0x348F, 0xCEDD, 0x0E67, 0x186A, 0x9AB0, 0x0520, 0x2648,
			0x9131, 0xECE6, 0x5E3F, 0xA4EF, 0x8A29, 0x88BC, 0x2DB6, 0x9DFA, 0x600F, 0xC324, 0x4FB4, 0x1C5E, 0xE2F6, 0xB806, 0x4134, 0x9CC6,
			0x23BE, 0xB619, 0x554C, 0xAB09, 0x3354, 0x303E, 0xA877, 0xE637, 0x52B7, 0xCA5F, 0xC95D, 0x04D8, 0xA9AD, 0x1FD0, 0x9597, 0x5D72,
			0x8763, 0x0C0D, 0x4826, 0x9BF3, 0xEDB2, 0x751C, 0x56C0, 0xBAE4, 0x3E6C, 0xB9C8, 0x5B45, 0xAFC9, 0x895C, 0x1BCF, 0x1338, 0x10E0,
			0x247D, 0x1AD5, 0xC10C, 0x0151, 0xF6CE, 0xCCEB, 0x217B, 0x8595, 0x08E8, 0x356B, 0x7DDF, 0xE05B, 0xF9FF, 0xBF4D, 0xCBDB, 0x6E6E
		},

		{
			0x8DC1, 0x5B22, 0x6680, 0x01D7, 0xD81C, 0x5F7D, 0xC00A, 0x2B01, 0x309F, 0x1B11, 0x8828, 0x1570, 0xBE4D, 0x0CD2, 0xB5A0, 0xE7D6,
			0xA6BA, 0x8B93, 0x38C3, 0xF094, 0x3562, 0x1D4B, 0x049B, 0x59CF, 0x6C27, 0x225F, 0x9102, 0x4B33, 0x7675, 0x2354, 0x4279, 0x653C,
			0xB988, 0x0281, 0xFF95, 0xBA19, 0x9A07, 0xD0FB, 0x48BF, 0xB86E, 0x7BCD, 0xC516, 0xB252, 0xB112, 0x2D50, 0xD946, 0xD7C4, 0x39CA,
			0x5709, 0x8706, 0x0915, 0xEB48, 0x6226, 0x07B3, 0x3D18, 0xDC84, 0x9B5B, 0x2AC2, 0x70F1, 0x60F6, 0x7C61, 0x0EF4, 0x8E42, 0x138B,
			0xA54C, 0xE2FC, 0x4AFE, 0x2CFD, 0x7347, 0x3C87, 0xCC6A, 0xA721, 0x1E9E, 0x25A5, 0x6A13, 0xA2C0, 0x6F66, 0x342B, 0xA940, 0xBFA4,
			0xCEEC, 0x8A3E, 0xCA53, 0x5C35, 0x3FF7, 0x6860, 0x4FC6, 0x9EFF, 0x7A1F, 0x454E, 0x7214, 0x378A, 0xB04F, 0xE8E9, 0xC8F5, 0x460B,
			0x4E10, 0x1AFA, 0xF777, 0xB6F3, 0x19E3, 0x4751, 0xFB3D, 0x187B, 0x2F39, 0x545A, 0xCFBB, 0xE385, 0x101B, 0x5A44, 0xF40D, 0xBB1E,
			0x5DA9, 0x1C1D, 0xCBB7, 0x9DC8, 0x85DC, 0x7E3F, 0xE086, 0x8C9A, 0x64EE, 0x966C, 0x89E4, 0x4364, 0x6D4A, 0xDA03, 0xA356, 0xC198,
			0x3EA8, 0x7717, 0xA1E8, 0xD2AA, 0x40F0, 0xD655, 0xF2E7, 0x7437, 0xBD36, 0x533B, 0x1404, 0x6125, 0xAA0F, 0xB459, 0x20F9, 0x2732,
			0x3265, 0xF529, 0xA0E2, 0xF369, 0xE923, 0x6373, 0x9543, 0x5E99, 0x6E6D, 0x9045, 0xAE31, 0x212D, 0xA87F, 0x08BE, 0x75DF, 0x16CE,
			0x242E, 0x12DE, 0xD538, 0xD3C7, 0xF8EA, 0x3171, 0xF9AB, 0x92B8, 0x7DA3, 0x9F8D, 0x0FBC, 0x50AD, 0x3649, 0x865C, 0x0558, 0x179C,
			0x003A, 0x2882, 0x83CB, 0xF697, 0xDF0E, 0x9996, 0xE1AE, 0x9474, 0x0AF2, 0x0DB0, 0x787E, 0x2ED3, 0x8F9D, 0xDE20, 0xC26F, 0x6BBD,
			0xC3D1, 0xE5E1, 0x842C, 0x6708, 0xA4AC, 0x93ED, 0x97AF, 0x81D9, 0x9834, 0x4124, 0xDD41, 0xEE0C, 0xBC68, 0x33B6, 0xB72F, 0x4C5D,
			0xB300, 0xC9F8, 0xFE5E, 0x29C5, 0xEAB5, 0x49E6, 0xAB30, 0xF1E0, 0xED57, 0x44B4, 0xCDC9, 0xFC8E, 0x79B9, 0x1FA6, 0xD192, 0xD478,
			0x06DB, 0xADDD, 0xC46B, 0x7172, 0x52B1, 0xEFDA, 0x518F, 0x56E5, 0x58D8, 0xECD4, 0xFA91, 0xFD8C, 0x3BA2, 0x0B89, 0x03EB, 0x6976,
			0x11A7, 0x3AEF, 0xAC63, 0xC77A, 0xE605, 0xDB83, 0x82CC, 0x7FB2, 0x2690, 0x55D5, 0x801A, 0x9C7C, 0xE4A1, 0x4DD0, 0xAF2A, 0xC667
		},

		{
			0x0E75, 0x188A, 0x8F52, 0x5A07, 0x1D32, 0x1E0E, 0xDC7E, 0x4980, 0xE10F, 0xB508, 0x8E6A, 0xAAB4, 0x87D2, 0x01F2, 0x957D, 0xFE3F,
			0x6F3A, 0xB8BA, 0xDBE4, 0x00A1, 0xFCE8, 0x6457, 0xD560, 0x8B06, 0x4669, 0x974C, 0x24C2, 0x3FE9, 0x5E99, 0x5D6B, 0x9D4A, 0xBCD6,
			0xFB62, 0x4E82, 0xD92A, 0x0C39, 0x7823, 0x5915, 0xEE1A, 0x4AD5, 0xF2E7, 0x8C45, 0x2103, 0x1638, 0x0A37, 0x42BD, 0x1079, 0xE7EF,
			0x7593, 0x5491, 0x2C90, 0xEF68, 0xC184, 0x602C, 0x9B63, 0x19AF, 0xCF18, 0xCEA6, 0xE961, 0x89C3, 0x9647, 0x4492, 0x325A, 0xD056,
			0x85F7, 0xA1FF, 0xA5B7, 0xCD73, 0xF81E, 0xE8E6, 0xB08C, 0x9C87, 0xC8EA, 0x2016, 0xAD1B, 0xABD4, 0x0997, 0xA2BC, 0x13E3, 0xBA02,
			0x8033, 0x8A71, 0xD883, 0x302F, 0xC7BB, 0xE4E0, 0x29A5, 0x1FF5, 0xAEB1, 0xD6D3, 0x3EAD, 0x77A2, 0xDD86, 0x992B, 0x73DE, 0xA676,
			0x39CB, 0x2DFB, 0x92FE, 0x5000, 0x7D7B, 0x66DF, 0x059C, 0xEC0C, 0xB2F4, 0x03CD, 0x2E17, 0xB424, 0xE0E2, 0xCA35, 0xEA5F, 0xD126,
			0xA7BE, 0xA928, 0xC5A3, 0x3878, 0x33F0, 0x9459, 0xC444, 0xBD1F, 0xC9F3, 0x58CF, 0xCC55, 0x70A8, 0x9096, 0x0B01, 0xB1AE, 0xE211,
			0xD358, 0x8DED, 0x350D, 0xA4AB, 0x7209, 0xA336, 0x6A12, 0xEB64, 0x719B, 0x4BB0, 0x6D51, 0x93DB, 0x2681, 0xC35C, 0x8395, 0x349F,
			0xCB20, 0x0FFD, 0x4FD9, 0x553D, 0x2F05, 0x98C9, 0x1BB8, 0x0D3C, 0xED94, 0x1ACA, 0x7EB3, 0x4C4B, 0x6BC5, 0x5146, 0x844D, 0x14CE,
			0xE6C6, 0x91A7, 0x8229, 0x3AAA, 0x3D10, 0x2BD8, 0x9F22, 0x08E5, 0xF4EC, 0xB6A0, 0x480B, 0x1C67, 0x115D, 0x3714, 0x4521, 0x8840,
			0x818D, 0xF35E, 0x1243, 0x362D, 0x52F9, 0xF7D0, 0x9E1D, 0x400A, 0x41EB, 0xFA70, 0xC077, 0x5FFA, 0x7AF8, 0x17AC, 0x02C7, 0x7685,
			0x2588, 0x7B65, 0xB953, 0x6549, 0x278B, 0xE5D1, 0xBEB2, 0x227C, 0x06F1, 0x5698, 0x31A4, 0x699E, 0x7CC1, 0x15DD, 0xA83B, 0xF6B5,
			0xDA54, 0x79B9, 0x4D6C, 0x6172, 0x9ADC, 0x53B6, 0x4750, 0x2A5B, 0xB7A9, 0x8625, 0xC227, 0x3B8E, 0x5C3E, 0xBFEE, 0x04E1, 0xD489,
			0x236F, 0x6C9D, 0x3CC4, 0x6E30, 0xD748, 0x67C8, 0xD22E, 0xF1BF, 0xC67A, 0xF9DA, 0x2874, 0xDE04, 0xE34E, 0x624F, 0x63FC, 0xFF66,
			0x6819, 0x0734, 0x436E, 0xF0CC, 0xAF31, 0x7F9A, 0x5713, 0xA07F, 0xFD41, 0xACF6, 0x741C, 0xBB8F, 0xF5C0, 0xB3D7, 0xDF6D, 0x5B42
		},

		{
			0x4A13, 0xF50D, 0x2D8F, 0x08DA, 0xAF03, 0x09EE, 0x3390, 0xCAC2, 0x53BB, 0xBD50, 0x9317, 0xBECD, 0xD70A, 0xA193, 0x7A4D, 0x7D41,
			0x5CC1, 0x9BDF, 0x6439, 0x0E07, 0x7CAB, 0x0564, 0x8EF9, 0x36CE, 0x1A8A, 0x043E, 0x1F2B, 0xAD9A, 0x1672, 0xF610, 0xB7E7, 0x6E86,
			0x6835, 0x439E, 0x5EF0, 0x5A33, 0x8361, 0xE1EF, 0x038E, 0x9214, 0x6124, 0xD288, 0xA632, 0x5D1B, 0x505C, 0x4D92, 0x9AD8, 0xE968,
			0xB122, 0x0B67, 0x3163, 0x1E08, 0x2CA5, 0xBAA4, 0x572F, 0xEF7A, 0x5B95, 0xF3B0, 0x7882, 0x21A9, 0x7411, 0xD9A1, 0x664A, 0x98A6,
			0x15E0, 0x0D48, 0x1471, 0x85AC, 0xE596, 0x8C46, 0x6230, 0x69C3, 0xC6FA, 0x9D7D, 0xE618, 0x8A98, 0x2637, 0x634B, 0x511E, 0xFBF6,
			0x54B7, 0x2070, 0xAE00, 0x67D5, 0xEB76, 0xAB9C, 0xB642, 0x9579, 0xF2FB, 0xF08B, 0xA731, 0x70BA, 0x6F85, 0x9FB9, 0xC76A, 0x258D,
			0x9487, 0x1BCA, 0xA3E3, 0x3525, 0xB5BD, 0x425D, 0xD8C4, 0x4149, 0x7220, 0xF799, 0xED55, 0x2B5F, 0xF8FC, 0x2FA3, 0x56D1, 0x13D6,
			0x86EA, 0x2ECB, 0xC8C7, 0x24F5, 0x3926, 0x8F43, 0x841D, 0x0F84, 0x6CD3, 0xA5D2, 0xDAA2, 0x810F, 0x7FD4, 0xB22D, 0xDF3B, 0x1904,
			0x971C, 0x0706, 0x906E, 0xCE77, 0x4B97, 0x3805, 0x225A, 0x99F1, 0xA8F3, 0x59BE, 0xCC59, 0xA91F, 0xDD51, 0xB460, 0xDC2C, 0x12FF,
			0xD415, 0xC40E, 0x711A, 0x9C83, 0xF96C, 0xB8DB, 0x47B2, 0xBBAF, 0x8B7B, 0x6558, 0x02B1, 0xE374, 0x3FE4, 0x0047, 0xA052, 0xE0C8,
			0x3CE1, 0xFFA7, 0xF4FE, 0xC975, 0x44B5, 0xBCF4, 0xB944, 0xA43F, 0xFD29, 0x583C, 0xD0C9, 0x106B, 0xE70C, 0xE42A, 0x48C5, 0x8954,
			0x799D, 0xCD23, 0x5FEC, 0x3709, 0xD1DD, 0x6BBC, 0x7B89, 0x60A0, 0xAA38, 0x3D9B, 0x3E6D, 0x8756, 0xEC02, 0x01FD, 0xA25B, 0x3A16,
			0x6D91, 0xB0E5, 0x2728, 0x17ED, 0x1C12, 0x764F, 0x77B6, 0x7362, 0x064C, 0x4E27, 0x7594, 0xC3D0, 0x4FE2, 0xFA65, 0xC5F7, 0xEE0B,
			0xEAF2, 0x9EC6, 0x82B4, 0x293D, 0xE27E, 0xC2AD, 0x0A2E, 0xDE19, 0xBF78, 0x454E, 0x235E, 0x6A57, 0x4CB3, 0xACCF, 0xCF45, 0xCBDE,
			0xD336, 0x3421, 0x969F, 0xFEAE, 0x91C0, 0xC17F, 0xC0DC, 0xD581, 0x55D9, 0x327C, 0x2ACC, 0x4969, 0x30F8, 0xFC73, 0x1853, 0xF1E8,
			0x3BEB, 0x1D34, 0xB3AA, 0x1180, 0x466F, 0x8D40, 0x0CD7, 0xE801, 0x40E6, 0x88A8, 0xDBE9, 0x528C, 0x80B8, 0x7E66, 0x28BF, 0xD63A
		},

		{
			0x85EC, 0x3A70, 0x37FF, 0xC132, 0x1CE9, 0xBD0E, 0x5697, 0x2634, 0x8D01, 0xAFEB, 0x9FD6, 0x5AC4, 0x42D5, 0xDE50, 0xD223, 0xE6C9,
			0x2A21, 0x7C25, 0x5ECD, 0x6E9D, 0xAC59, 0x09AA, 0xF512, 0x8E42, 0xD494, 0x53BF, 0x7140, 0xE8CF, 0xCEB7, 0xDD3A, 0xB12B, 0x25F4,
			0xF4BB, 0x03EE, 0x1238, 0x1D20, 0x3E87, 0x34C1, 0xC983, 0x2E64, 0x5081, 0x55F0, 0x139B, 0x5826, 0x7916, 0xBC98, 0x2CD7, 0x911C,
			0x5133, 0x769C, 0x1841, 0xCD7B, 0x61C5, 0xAA10, 0xC5AF, 0x753B, 0x2952, 0xAD35, 0xF3CA, 0xE518, 0x204F, 0x16B1, 0x3FB0, 0xE40B,
			0x77A8, 0x6FF6, 0x7082, 0xCCB9, 0x9D2F, 0xB68E, 0x49CE, 0x416B, 0xA4D2, 0xD543, 0xF01B, 0x9A1D, 0x4D24, 0x6B6F, 0xB7E3, 0x2B86,
			0x0E9A, 0xC0FA, 0xEDAC, 0x088B, 0x9531, 0xE9A0, 0xA6F3, 0xFFC3, 0x04C6, 0x40AD, 0xAB47, 0xC828, 0x871A, 0x4492, 0x5702, 0x6089,
			0xB3B3, 0x694E, 0xE0B5, 0xE272, 0xB0E5, 0xFA99, 0x0790, 0xFB4B, 0x8471, 0xD35F, 0xBEBA, 0x7F7A, 0x99AB, 0x9348, 0xA8F9, 0x66F7,
			0xFEA9, 0x9BB8, 0xBA37, 0xCB69, 0x8067, 0x0A39, 0x5B49, 0xA554, 0x94D3, 0xF160, 0x6209, 0x5F46, 0x2863, 0x43DC, 0xCFC0, 0xF85A,
			0x8A00, 0x0130, 0x8CE4, 0x388F, 0xC619, 0xBB2A, 0x98DA, 0x7EA7, 0x5C78, 0x2305, 0x4596, 0x025E, 0x7473, 0xA1BC, 0x926D, 0x7B14,
			0x6D44, 0xE39F, 0xC277, 0xD9B4, 0x5DD1, 0x723E, 0xEFF8, 0x0CBE, 0x4E62, 0xEAD9, 0x479E, 0xA258, 0xEC0D, 0x22F2, 0xF253, 0xD184,
			0x88E0, 0xA968, 0x0545, 0x154D, 0xEB91, 0x817D, 0x5215, 0x243D, 0xD695, 0x1E56, 0xB92E, 0x35C8, 0x7A07, 0x30E8, 0xB579, 0x3688,
			0x59CB, 0xB265, 0x33C2, 0x6874, 0x2DD0, 0x8BEF, 0x3108, 0xAED4, 0xDC76, 0xC3FB, 0x145B, 0x7D66, 0xF6A2, 0xFC29, 0xE1F1, 0xDB0C,
			0x9EEA, 0x2F3F, 0x104A, 0xBF5C, 0x111E, 0x21C7, 0xFDD8, 0x906C, 0xD0E2, 0xCA8C, 0x822D, 0x78DB, 0x1961, 0x4C55, 0x3C6E, 0x6AED,
			0x97A4, 0x9C36, 0x4AFE, 0x830F, 0xF7CC, 0x6CBD, 0xD8B2, 0x675D, 0xEE11, 0x3DE1, 0x3B8A, 0x48AE, 0xD7DD, 0xA793, 0x7351, 0x1BA6,
			0x89DE, 0xA3FD, 0xE7E6, 0xDA8D, 0x6306, 0x397C, 0x6485, 0xF917, 0x547E, 0xC475, 0xB8DF, 0xDFA1, 0xB4A5, 0x1F04, 0x86B6, 0x0D7F,
			0x464C, 0x1A13, 0xC757, 0x0627, 0x00F5, 0x0B03, 0x8FE7, 0x961F, 0x4B6A, 0x0F80, 0xA0FC, 0x4F2C, 0x653C, 0x3222, 0x17A3, 0x270A
		},

		{
			0xF667, 0x1D85, 0x5C4A, 0xDEAF, 0xCB8E, 0x6B88, 0x2EC7, 0x3EB4, 0xF5AE, 0x4FF5, 0x838B, 0xFED2, 0xE14E, 0x9C45, 0x89ED, 0x01FB,
			0x111B, 0xAD94, 0x192F, 0x5518, 0x99FD, 0x5ED8, 0xBB3D, 0xDC1A, 0x5FDC, 0x6F89, 0x7CAA, 0xB99F, 0x4E3E, 0xD433, 0x4C99, 0x2D32,
			0xB47C, 0x6C53, 0xF9DE, 0xEF2C, 0xBF08, 0x6AA1, 0x50C8, 0xC0CA, 0xEAF8, 0x7096, 0x3319, 0xAF2A, 0x0257, 0x941E, 0xEE92, 0x7F5C,
			0x43F1, 0x9D76, 0xE21D, 0xF380, 0xC6C1, 0x3A00, 0x15F7, 0xA0C6, 0xF2EB, 0x53BC, 0xA89A, 0x7946, 0x07FA, 0x61CC, 0x47C9, 0xD795,
			0xB3DF, 0xD33A, 0xE0E1, 0x2920, 0x2CCB, 0xBE5A, 0x8534, 0x698A, 0x9325, 0x5840, 0x390D, 0x51AD, 0xB2E9, 0xA547, 0xDD44, 0x2848,
			0xDBEC, 0x71A6, 0xCFFE, 0xA661, 0xB0A0, 0x3F73, 0x8D54, 0x8109, 0x97AB, 0x05B3, 0x7660, 0x0F7E, 0xFF22, 0xD8FF, 0x1091, 0xE572,
			0xFCE8, 0x0986, 0x235B, 0xA311, 0x417F, 0x6643, 0x54A5, 0x865D, 0x8C9D, 0xABA7, 0x8ED0, 0xE6E4, 0x3842, 0xB89E, 0x16F4, 0xDF64,
			0xD956, 0xA437, 0x7E28, 0x4479, 0x5939, 0x6482, 0x4915, 0xA2EF, 0xA70E, 0xEDA8, 0x75A4, 0xD0B5, 0x4A2E, 0xF462, 0xEC27, 0xCC90,
			0x5712, 0x1BC0, 0x4801, 0x311F, 0x95C3, 0x368D, 0x06A2, 0x7D0B, 0xCECD, 0x569B, 0x98BE, 0x6D04, 0x2AF3, 0xE3D6, 0x8775, 0x1EE0,
			0xBD24, 0x8F3B, 0xC52B, 0xB1BD, 0x37E5, 0x1C35, 0xEB6B, 0x257B, 0x22CE, 0xD6B9, 0x5AB7, 0x0E93, 0xC9F2, 0x35E6, 0x4583, 0x466D,
			0xBC14, 0x3B13, 0xC205, 0x2487, 0x2B65, 0xC823, 0xB73C, 0xCD26, 0x6577, 0x9A9C, 0x9B58, 0xC336, 0xE721, 0x1766, 0x0CC2, 0x9F4B,
			0x63D7, 0x0BE7, 0x84C5, 0x88BF, 0xAE98, 0xC4F9, 0x7355, 0x2638, 0xDADB, 0xD2D5, 0xE4B1, 0xCAD1, 0xE852, 0xB569, 0x5DA9, 0x7ACF,
			0xF8DD, 0xAC2D, 0x081C, 0xBA71, 0x8006, 0x5210, 0xFD30, 0xFB07, 0x0A16, 0xE9BA, 0x686A, 0x14B8, 0x1F7D, 0x606E, 0x787A, 0x96EA,
			0xF76C, 0x8B5E, 0xD570, 0x32AC, 0x0402, 0x188F, 0x2F4C, 0x7BEE, 0x774F, 0x6E68, 0xAA8C, 0x40F0, 0x345F, 0x1250, 0xFAF6, 0xB663,
			0xC1E2, 0x3DA3, 0xA1DA, 0x4B4D, 0x92C4, 0x3097, 0x4DB2, 0x2103, 0xC784, 0x3C78, 0x4274, 0x91D9, 0x030F, 0x2729, 0xF0BB, 0x2081,
			0x8AFC, 0x5B0C, 0xF141, 0x00D3, 0x9EE3, 0xD1D4, 0x1A3F, 0x900A, 0xA917, 0x72B6, 0x1359, 0x7451, 0x6231, 0x8249, 0x0DB0, 0x676F
		},

		{
			0x14FA, 0xBFD1, 0x95E1, 0x38DA, 0x8B78, 0xBD09, 0x5833, 0x6C32, 0xAFD6, 0x0C4F, 0x0DF4, 0xE89A, 0xE6CD, 0xF592, 0x1B03, 0x9ADF,
			0xFC07, 0x2FF8, 0x154D, 0x610E, 0xDC70, 0x9899, 0x0ABB, 0x59BD, 0x35F9, 0xB951, 0xE088, 0xB549, 0x6E43, 0x472E, 0x37CA, 0x3C5C,
			0x6F3A, 0xCF11, 0x9F74, 0x40C0, 0x8EBC, 0x756B, 0x801E, 0x3AA1, 0x3945, 0x5D67, 0x5410, 0xE904, 0x4DB3, 0x068A, 0xD5E3, 0xDA98,
			0x905F, 0x0E6C, 0xE40D, 0x5EEC, 0x3E12, 0x05D7, 0xA357, 0x5247, 0x4966, 0x03E5, 0x2193, 0x967E, 0x4441, 0x4A85, 0xAD9E, 0xCA40,
			0xB7CE, 0x5061, 0x26AD, 0xA7D8, 0x73A3, 0x020C, 0xFDC9, 0x9BB5, 0x1F8E, 0x1295, 0xCD8D, 0xFFC3, 0x48ED, 0x8490, 0xB024, 0x1927,
			0x70AB, 0x45EA, 0xB3B8, 0x5676, 0x2D5E, 0xBAB4, 0xCE01, 0xA8A4, 0x86A8, 0x1A97, 0x66DC, 0x776A, 0xC2A9, 0xACB0, 0xF935, 0x819C,
			0xEAD2, 0x1C75, 0xEDB2, 0xE5E7, 0xA1C6, 0xC90B, 0x2CE6, 0xBCE9, 0xEF05, 0x4FF0, 0x87DE, 0xD8AC, 0x999D, 0x9E26, 0x9D13, 0x0894,
			0x7C0F, 0xDBF7, 0x4E2C, 0xF8EB, 0x7D18, 0xA46D, 0xDDB6, 0xE34A, 0x881F, 0xEC19, 0x9328, 0x42BA, 0x8D4C, 0x3B7B, 0x0B9B, 0x2AA2,
			0xF783, 0x6A14, 0xD481, 0xD0DD, 0x0073, 0x6538, 0x7416, 0x6055, 0xC623, 0x763F, 0x2884, 0xB217, 0x01AF, 0x185D, 0xD75B, 0x3096,
			0x04D4, 0x8F1C, 0x3606, 0x55A5, 0x4358, 0xC363, 0x923B, 0xB6D5, 0x5C20, 0xE11A, 0x1E6F, 0xC5B9, 0x2221, 0xABC8, 0xD1BE, 0x1D00,
			0xF37C, 0x9C62, 0x6D69, 0x781B, 0x2B82, 0x4BD0, 0x63E4, 0x2034, 0x94C1, 0xBB8F, 0xF236, 0xF0FF, 0xEEAE, 0x712A, 0xB45A, 0x347A,
			0xD272, 0x16CF, 0xDE50, 0x5FBF, 0x85AA, 0xCCA6, 0x62FD, 0xA056, 0x7229, 0xC8B1, 0x7E54, 0x53EE, 0xD637, 0x8244, 0x8331, 0x4C9F,
			0x6830, 0x7AA0, 0x6B80, 0x67E0, 0x89C5, 0xCB8C, 0x1387, 0xC03D, 0x46F2, 0xFEA7, 0x9722, 0x3FD3, 0x6968, 0x7F77, 0xC7E8, 0xF459,
			0x3371, 0x7BE2, 0x07F1, 0xD92F, 0x2E48, 0x3142, 0x1102, 0xEB79, 0xA9C7, 0x2946, 0x8A2B, 0xE24E, 0xB17F, 0xC14B, 0x2552, 0x171D,
			0xFA53, 0x5B91, 0x0F0A, 0xDF25, 0x64F3, 0xA2EF, 0xBEC2, 0x3D86, 0x8C08, 0xF6CC, 0xC42D, 0x106E, 0xE7FB, 0x57FC, 0x79FE, 0x248B,
			0xB815, 0x51F5, 0x5ACB, 0x277D, 0xAAF6, 0xA53E, 0x3289, 0xD3DB, 0x2365, 0xAEC4, 0x0964, 0xA639, 0x9160, 0xFB3C, 0xF1D9, 0x41B7
		},

		{
			0x1FD8, 0xE8A0, 0x12AE, 0xD3FB, 0x3ED7, 0x1DBD, 0x555C, 0xC77D, 0x6DBA, 0xB498, 0xA011, 0xABF0, 0xDE93, 0xC349, 0xEAAF, 0x5EA8,
			0xF599, 0x336B, 0xD421, 0x2CB9, 0x0D58, 0x8467, 0xB088, 0x3FED, 0xC45B, 0x899E, 0xA7A7, 0x411D, 0x2426, 0x83F6, 0x34D1, 0x08EF,
			0x75FF, 0x792A, 0xF9B4, 0x634C, 0x6B2F, 0xCB1E, 0x7FE0, 0x49A3, 0x99E8, 0x8208, 0x3C59, 0x4744, 0x3003, 0xC87E, 0x42FD, 0x712C,
			0x170C, 0x4E53, 0x9EEA, 0xF892, 0x7629, 0x19EC, 0xD202, 0x206D, 0x52DE, 0x29A4, 0x2B7A, 0x004A, 0xE2F9, 0x7EFC, 0x09D9, 0x62C7,
			0x8D00, 0xBB09, 0x98DA, 0x538A, 0x2F54, 0x58B7, 0xB7DF, 0xD69F, 0xE537, 0x7CC2, 0x440E, 0x1BD0, 0x9C20, 0x319C, 0xCF31, 0xDB96,
			0xCD3D, 0xCE55, 0x5918, 0x6CA2, 0x64B5, 0x2541, 0x4A97, 0xDA3A, 0xA245, 0x5BBF, 0xFA06, 0x9377, 0x4601, 0x5C75, 0xC0B3, 0xE06F,
			0x7DBE, 0xE4CC, 0xC27B, 0x4579, 0x609B, 0x5187, 0x06A1, 0x4F2E, 0xF7E4, 0xB5E2, 0x7707, 0xD76C, 0x6774, 0xC55F, 0x3513, 0x9539,
			0x0C1A, 0x6184, 0xA8CF, 0x21EB, 0xE348, 0x5F17, 0xCA5E, 0x2840, 0x5085, 0x7270, 0x3A0D, 0x381B, 0x0FB6, 0x14C9, 0xACAC, 0xE9B2,
			0x9482, 0xA1C1, 0x7B30, 0x36D6, 0x1822, 0xEC4D, 0xF33B, 0xBE76, 0xE736, 0x560B, 0xA5F4, 0xFDBC, 0xAEAD, 0xADDC, 0x86B8, 0x0EBB,
			0xF21F, 0x90AB, 0xAAEE, 0xBA2B, 0xF168, 0x5A61, 0xA4A6, 0x39E6, 0xB38E, 0xB665, 0x8832, 0x2EAA, 0xDCC8, 0x9FFE, 0xB962, 0xE614,
			0x2242, 0xFFE1, 0x9BF7, 0xC943, 0x138B, 0x0B7F, 0xB281, 0xCCDB, 0x2795, 0x873F, 0x66A9, 0x1A19, 0x9D64, 0x548D, 0x0273, 0x6F1C,
			0x9650, 0xFBFA, 0x688F, 0xC1C4, 0x4DCD, 0xD0CA, 0x2605, 0xF638, 0xA604, 0x2A71, 0xFE8C, 0x1515, 0x92E5, 0x914E, 0xD9B1, 0x48F8,
			0x0523, 0x6E63, 0x8A72, 0x0A9A, 0x1057, 0x8047, 0xDF33, 0xA946, 0x01E3, 0xE1B0, 0x699D, 0x8527, 0xDDC3, 0x32D4, 0xED0F, 0x1124,
			0xD5D3, 0xB852, 0xEE7C, 0x40D5, 0xFC28, 0x6525, 0x97C6, 0xD1E9, 0x8B90, 0xD84F, 0x73C5, 0xEF2D, 0x5769, 0x3735, 0xC6A5, 0x1C89,
			0x7412, 0xF051, 0x43F2, 0x8E80, 0x9A34, 0x23F3, 0x3B3C, 0xEB0A, 0x8FD2, 0xA383, 0x1EF5, 0x4C10, 0x043E, 0xB16E, 0x3DDD, 0x8186,
			0x7A78, 0x8C5A, 0x70C0, 0xAF16, 0x4B60, 0x16E7, 0x036A, 0x7894, 0xBD5D, 0x2DF1, 0xBCCE, 0xBF4B, 0x6ACB, 0x5D91, 0xF456, 0x0766
		}
	};
	static int HashesOfBytes[] = {
		0x003AEC76, 0x00897CCE, 0x0025F5A2, 0x000B6A84, 0x00F7DA0B, 0x009445AB, 0x0052D230, 0x0067508B,
		0x004BDBEF, 0x0082D3F7, 0x0022F544, 0x00F0C630, 0x00CF8813, 0x008912BD, 0x00B79490, 0x0039F1AB,
		0x00092EBD, 0x004320EA, 0x00868AC9, 0x0010B42A, 0x003A38AB, 0x000B00B0, 0x00B51F85, 0x006AFBCF,
		0x003DBFAB, 0x007E6B13, 0x0009EEA3, 0x00118E11, 0x00F0EA20, 0x0082A3A3, 0x004D0A59, 0x009210CD,
		0x00869BCE, 0x00DBB8BE, 0x00A480B9, 0x0033FFBF, 0x00434657, 0x0092530C, 0x004B93F8, 0x00258487,
		0x006A0E68, 0x00337409, 0x004D34CF, 0x007ECC4D, 0x00A00DD1, 0x0081E010, 0x006D8B65, 0x000636DE,
		0x00F2A9D1, 0x00E84744, 0x007E3CAB, 0x004A1A10, 0x006A9C59, 0x0009953C, 0x00365265, 0x00A4E69F,
		0x0009A613, 0x00C82BF7, 0x0011C641, 0x00E7CFBD, 0x00B74976, 0x00C8607B, 0x002C1B42, 0x00031057,
		0x006A1BB5, 0x004E1AAB, 0x007E48D0, 0x0082423C, 0x002BAE0B, 0x0002294D, 0x00302CBE, 0x007E2813,
		0x00AF4350, 0x00C8CEAB, 0x0052EB6F, 0x00740185, 0x00A024BD, 0x00038976, 0x00A07750, 0x00F2AB59,
		0x00E1D257, 0x003A7E13, 0x00F262A1, 0x009E9488, 0x00F07610, 0x00F3F22D, 0x00A1AB55, 0x00AD41AB,
		0x002BD744, 0x0003FF63, 0x00928944, 0x00260F6F, 0x004AA0BD, 0x00A8A987, 0x003047CE, 0x00A035AB,
		0x00F9E1AB, 0x006D2AF7, 0x00863706, 0x0067049C, 0x006073A3, 0x00CF883C, 0x00A8B557, 0x0010C7B5,
		0x00B715C9, 0x00948109, 0x00266D9C, 0x00102EBD, 0x0086F53C, 0x00C8046B, 0x00096590, 0x005443D1,
		0x00032C65, 0x0009D886, 0x00255633, 0x00E88D3F, 0x00EDAE59, 0x00A43B1B, 0x005D3557, 0x004D810B,
		0x0039B22A, 0x00093750, 0x00B6D3D3, 0x007EA87B, 0x00394C9F, 0x00AD419C, 0x004ED3B7, 0x005EF3BD,
		0x00CFFF4D, 0x00F30D33, 0x007470AB, 0x005E7FB6, 0x00F04610, 0x0026EED1, 0x00B62520, 0x00B0632A,
		0x005211CF, 0x00B658BD, 0x00B0AB06, 0x007E424F, 0x00DCC909, 0x00097941, 0x004ACF65, 0x004E2C9C,
		0x0039A2C9, 0x00037555, 0x00AD9413, 0x004D55F9, 0x006EB655, 0x00252411, 0x00264CAB, 0x00921CA3,
		0x008D8FCD, 0x0025A79C, 0x00F9E4BD, 0x009243BE, 0x0086DE13, 0x004E0D55, 0x00BD917C, 0x00BD5AC8,
		0x00CF2FCF, 0x00394F0C, 0x007EDA0B, 0x008DA350, 0x004E7913, 0x00F1EAAC, 0x0025E913, 0x00105BF9,
		0x00E7C2A4, 0x004EEFB7, 0x00D4E374, 0x0009D3D1, 0x0024C042, 0x007E8BA1, 0x00789511, 0x00CF13A2,
		0x00EDC713, 0x00F02809, 0x00892A94, 0x0003C7BE, 0x00F0CB2A, 0x00928609, 0x0039C769, 0x000FD4EF,
		0x008F87EF, 0x00CB6BD3, 0x0022A3F2, 0x007F1BA3, 0x00A03159, 0x00113F88, 0x00266ABE, 0x00391BA3,
		0x00C8F50B, 0x003ADC13, 0x00240965, 0x00B08130, 0x0009BECF, 0x006AE73A, 0x003925D1, 0x00A84F88,
		0x00A076A1, 0x0010EC41, 0x008F74CF, 0x007E8909, 0x006A4E53, 0x000FE69F, 0x006DAC3C, 0x009E469F,
		0x00821185, 0x0009286B, 0x00B04355, 0x0082BE26, 0x00A4A14D, 0x004D54A3, 0x006D506E, 0x00ED06AB,
		0x00E74844, 0x00B7E37D, 0x0039A776, 0x002636A1, 0x0092613C, 0x00250BF8, 0x0062AFA1, 0x004A382D,
		0x0011C9BD, 0x0025F5D8, 0x007E9E20, 0x005EDD10, 0x007ECE87, 0x0082C03A, 0x0039846F, 0x00BD89AC,
		0x0089C94D, 0x00F07B55, 0x00A1F3F6, 0x00527611, 0x005DCC3C, 0x008D3984, 0x000959DE, 0x00B0013C,
		0x00FE787B, 0x003A91BD, 0x0092723C, 0x00334130, 0x004CD1B0, 0x002BC5CC, 0x00430142, 0x006AE455,
		0x002606AB, 0x0039F9D0, 0x002685F9, 0x008917C9, 0x00433F9C, 0x00AFB3A3, 0x003314C9, 0x005EBBBE
	};
	static AvailObject [] immutableByteObjects = null;

	/**
	 * Construct a new {@link IntegerDescriptor}.
	 *
	 * @param myId The id of the {@linkplain Descriptor descriptor}.
	 * @param isMutable
	 *        Does the {@linkplain Descriptor descriptor} represent a mutable
	 *        object?
	 */
	protected IntegerDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}

	final static IntegerDescriptor mutableDescriptor = new IntegerDescriptor(true);

	public static IntegerDescriptor mutableDescriptor ()
	{
		return mutableDescriptor;
	}

	final static IntegerDescriptor immutableDescriptor = new IntegerDescriptor(false);

	public static IntegerDescriptor immutableDescriptor ()
	{
		return immutableDescriptor;
	}
}
