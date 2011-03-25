/**
 * descriptor/TupleDescriptor.java
 * Copyright (c) 2010, Mark van Gulik.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * * Neither the name of the copyright holder nor the names of the contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission.
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

import static com.avail.descriptor.AvailObject.*;
import static com.avail.descriptor.TypeDescriptor.Types.*;
import static java.lang.Math.min;
import static java.util.Collections.max;
import java.util.*;
import com.avail.annotations.NotNull;

public abstract class TupleDescriptor
extends Descriptor
{
	/**
	 * The layout of integer slots for my instances.
	 */
	public enum IntegerSlots
	{
		HASH_OR_ZERO
	}

	@Override
	public void o_HashOrZero (final AvailObject object, final int value)
	{
		object.integerSlotPut(IntegerSlots.HASH_OR_ZERO, value);
	}

	@Override
	public int o_HashOrZero (final AvailObject object)
	{
		return object.integerSlot(IntegerSlots.HASH_OR_ZERO);
	}

	@Override
	public boolean allowsImmutableToMutableReferenceInField (final Enum<?> e)
	{
		return e == IntegerSlots.HASH_OR_ZERO;
	}

	@Override
	public void printObjectOnAvoidingIndent (
		final AvailObject object,
		final StringBuilder aStream,
		final List<AvailObject> recursionList,
		final int indent)
	{
		final List<String> strings = new ArrayList<String>(object.tupleSize());
		int totalChars = 0;
		boolean anyBreaks = false;
		for (final AvailObject element : object)
		{
			final StringBuilder localBuilder = new StringBuilder();
			element.printOnAvoidingIndent(
				localBuilder,
				recursionList,
				indent + 1);
			totalChars += localBuilder.length();
			if (!anyBreaks)
			{
				anyBreaks = localBuilder.indexOf("\n") >= 0;
			}
			strings.add(localBuilder.toString());
		}
		aStream.append('<');
		final boolean breakElements = strings.size() > 1
				&& (anyBreaks || totalChars > 60);
		for (int i = 0; i < strings.size(); i++)
		{
			if (i > 0)
			{
				aStream.append(",");
				if (!breakElements)
				{
					aStream.append(" ");
				}
			}
			if (breakElements)
			{
				aStream.append("\n");
				for (int j = indent; j > 0; j--)
				{
					aStream.append("\t");
				}
			}
			aStream.append(strings.get(i));
		}
		aStream.append('>');
	}

	@Override
	public boolean o_EqualsAnyTuple (
		final AvailObject object,
		final AvailObject aTuple)
	{
		// Compare this arbitrary Tuple and the given arbitrary tuple.

		if (object.sameAddressAs(aTuple))
		{
			return true;
		}
		// Compare sizes...
		final int size = object.tupleSize();
		if (size != aTuple.tupleSize())
		{
			return false;
		}
		if (o_Hash(object) != aTuple.hash())
		{
			return false;
		}
		for (int i = 1; i <= size; i++)
		{
			if (!o_TupleAt(object, i).equals(aTuple.tupleAt(i)))
			{
				return false;
			}
		}
		if (object.isBetterRepresentationThan(aTuple))
		{
			aTuple.becomeIndirectionTo(object);
			object.makeImmutable();
		}
		else
		{
			object.becomeIndirectionTo(aTuple);
			aTuple.makeImmutable();
		}
		// Now that there are at least two references to it
		return true;
	}

	@Override
	public boolean o_EqualsByteString (
		final AvailObject object,
		final AvailObject aByteString)
	{
		// Default to generic tuple comparison.

		return o_EqualsAnyTuple(object, aByteString);
	}

	@Override
	public boolean o_EqualsByteTuple (
		final AvailObject object,
		final AvailObject aTuple)
	{
		// Default to generic tuple comparison.

		return o_EqualsAnyTuple(object, aTuple);
	}

	@Override
	public boolean o_EqualsNybbleTuple (
		final AvailObject object,
		final AvailObject aTuple)
	{
		// Default to generic comparison.

		return o_EqualsAnyTuple(object, aTuple);
	}

	@Override
	public boolean o_EqualsObjectTuple (
		final AvailObject object,
		final AvailObject aTuple)
	{
		// Default to generic comparison.

		return o_EqualsAnyTuple(object, aTuple);
	}

	@Override
	public boolean o_EqualsTwoByteString (
		final AvailObject object,
		final AvailObject aTwoByteString)
	{
		// Default to generic tuple comparison.

		return o_EqualsAnyTuple(object, aTwoByteString);
	}

	@Override
	public boolean o_IsBetterRepresentationThan (
		final AvailObject object,
		final AvailObject anotherObject)
	{
		// Given two objects that are known to be equal, is the first one in a
		// better form (more
			return object.bitsPerEntry() < anotherObject.bitsPerEntry();
	}

	@Override
	public boolean o_IsInstanceOfSubtypeOf (
		final AvailObject object,
		final AvailObject aTypeObject)
	{
		// Answer whether object is an instance of a subtype of aTypeObject.
		// Don't generate
		// an approximate type and do the comparison, because the approximate
		// type
			if (aTypeObject.equals(VOID_TYPE.o()))
		{
			return true;
		}
		if (aTypeObject.equals(ALL.o()))
		{
			return true;
		}
		if (!aTypeObject.isTupleType())
		{
			return false;
		}
		// See if it's an acceptable size...
		final AvailObject size = IntegerDescriptor.fromInt(object.tupleSize());
		if (!size.isInstanceOfSubtypeOf(aTypeObject.sizeRange()))
		{
			return false;
		}
		// tuple's size is out of range.
		final AvailObject typeTuple = aTypeObject.typeTuple();
		final int breakIndex = min(object.tupleSize(), typeTuple.tupleSize());
		for (int i = 1; i <= breakIndex; i++)
		{
			if (!object.tupleAt(i).isInstanceOfSubtypeOf(
				aTypeObject.typeAtIndex(i)))
			{
				return false;
			}
		}
		final AvailObject defaultTypeObject = aTypeObject.defaultType();
		for (int i = breakIndex + 1, _end1 = object.tupleSize(); i <= _end1; i++)
		{
			if (!object.tupleAt(i).isInstanceOfSubtypeOf(defaultTypeObject))
			{
				return false;
			}
		}
		return true;
	}

	@Override
	public @NotNull AvailObject o_ExactType (final AvailObject object)
	{
		// Answer the object's type. Not very efficient - should cache the type
			final AvailObject tupleOfTypes = object.copyAsMutableObjectTuple();
		for (int i = 1, _end1 = object.tupleSize(); i <= _end1; i++)
		{
			tupleOfTypes.tupleAtPuttingCanDestroy(
				i,
				object.tupleAt(i).type(),
				true);
		}
		return TupleTypeDescriptor.tupleTypeForSizesTypesDefaultType(
			IntegerDescriptor.fromInt(object.tupleSize()).type(),
			tupleOfTypes,
			TERMINATES.o());
	}

	@Override
	public int o_Hash (final AvailObject object)
	{
		// The hash value is stored raw in the object's hashOrZero slot if it
		// has been computed,
		// otherwise that slot is zero. If a zero is detected, compute the hash
		// and store it in
		// hashOrZero. Note that the hash can (extremely rarely) be zero, in
		// which case the
			int hash = object.hashOrZero();
		if (hash == 0)
		{
			hash = computeHashForObject(object);
			object.hashOrZero(hash);
		}
		return hash;
	}

	@Override
	public @NotNull AvailObject o_Type (final AvailObject object)
	{
		// Answer the object's type.

		return ApproximateTypeDescriptor.withInstance(object.makeImmutable());
	}

	@Override
	public boolean o_CompareFromToWithStartingAt (
		final AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final AvailObject anotherObject,
		final int startIndex2)
	{
		error(
			"Subclass responsibility: Object:compareFrom:to:with:startingAt: in Avail.TupleDescriptor",
			object);
		return false;
	}

	@Override
	public boolean o_CompareFromToWithAnyTupleStartingAt (
		final AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final AvailObject aTuple,
		final int startIndex2)
	{
		// Compare sections of two tuples. Default generic comparison.

		int index2 = startIndex2;
		for (int index1 = startIndex1; index1 <= endIndex1; index1++)
		{
			if (!object.tupleAt(index1).equals(aTuple.tupleAt(index2)))
			{
				return false;
			}
			index2++;
		}
		return true;
	}

	@Override
	public boolean o_CompareFromToWithByteStringStartingAt (
		final AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final AvailObject aByteString,
		final int startIndex2)
	{
		// Compare sections of two tuples. Default to generic comparison.

		return o_CompareFromToWithAnyTupleStartingAt(
			object,
			startIndex1,
			endIndex1,
			aByteString,
			startIndex2);
	}

	@Override
	public boolean o_CompareFromToWithByteTupleStartingAt (
		final AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final AvailObject aByteTuple,
		final int startIndex2)
	{
		// Compare sections of two tuples. Default to generic comparison.

		return o_CompareFromToWithAnyTupleStartingAt(
			object,
			startIndex1,
			endIndex1,
			aByteTuple,
			startIndex2);
	}

	@Override
	public boolean o_CompareFromToWithNybbleTupleStartingAt (
		final AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final AvailObject aNybbleTuple,
		final int startIndex2)
	{
		// Compare sections of two tuples. Default to generic comparison.

		return o_CompareFromToWithAnyTupleStartingAt(
			object,
			startIndex1,
			endIndex1,
			aNybbleTuple,
			startIndex2);
	}

	@Override
	public boolean o_CompareFromToWithObjectTupleStartingAt (
		final AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final AvailObject anObjectTuple,
		final int startIndex2)
	{
		// Compare sections of two tuples. Default to generic comparison.

		return o_CompareFromToWithAnyTupleStartingAt(
			object,
			startIndex1,
			endIndex1,
			anObjectTuple,
			startIndex2);
	}

	@Override
	public boolean o_CompareFromToWithTwoByteStringStartingAt (
		final AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final AvailObject aTwoByteString,
		final int startIndex2)
	{
		// Compare sections of two tuples. Default to generic comparison.

		return o_CompareFromToWithAnyTupleStartingAt(
			object,
			startIndex1,
			endIndex1,
			aTwoByteString,
			startIndex2);
	}

	@Override
	public @NotNull AvailObject o_ConcatenateTuplesCanDestroy (
		final AvailObject object,
		final boolean canDestroy)
	{
		// Take a tuple of tuples and answer one big tuple constructed by
		// concatenating the
		// subtuples together. Optimized so that the resulting splice tuple's
		// zones are not
			int zones = 0;
		int newSize = 0;
		for (int i = 1, _end1 = object.tupleSize(); i <= _end1; i++)
		{
			final AvailObject sub = object.tupleAt(i);
			final int subZones = sub.tupleSize() == 0 ? 0 : sub.traversed()
					.isSplice() ? sub.numberOfZones() : 1;
			// Empty zones are not allowed in splice tuples.
			zones += subZones;
			newSize += sub.tupleSize();
		}
		if (newSize == 0)
		{
			return TupleDescriptor.empty();
		}
		// Now we know how many zones will be in the final tuple. We must
		// allocate room for it. In the Java translation, the garbage collector
		// will never increase the number of zones, so there will always be
		// room in the allocated chunk (maybe more than enough) for all the
		// zones.
		final AvailObject result = AvailObject
				.newObjectIndexedIntegerIndexedDescriptor(
					zones,
					(zones * 2),
					SpliceTupleDescriptor.mutable());
		int majorIndex = 0;
		int zone = 1;
		for (int i = 1, _end2 = object.tupleSize(); i <= _end2; i++)
		{
			final AvailObject sub = object.tupleAt(i).traversed();
			if (sub.isSplice())
			{
				assert sub.tupleSize() > 0;
				for (int originalZone = 1, _end3 = sub.numberOfZones(); originalZone <= _end3; originalZone++)
				{
					majorIndex += sub.sizeOfZone(originalZone);
					result.forZoneSetSubtupleStartSubtupleIndexEndOfZone(
						zone,
						sub.subtupleForZone(originalZone),
						sub.startSubtupleIndexInZone(originalZone),
						majorIndex);
					if (canDestroy && sub.descriptor().isMutable())
					{
						sub.setSubtupleForZoneTo(
							originalZone,
							VoidDescriptor.voidObject());
					}
					zone++;
				}
			}
			else
			{
				if (sub.tupleSize() != 0)
				{
					majorIndex += sub.tupleSize();
					result.forZoneSetSubtupleStartSubtupleIndexEndOfZone(
						zone,
						sub,
						1,
						majorIndex);
					zone++;
				}
				if (canDestroy && isMutable())
				{
					object.tupleAtPut(i, VoidDescriptor.voidObject());
				}
			}
		}
		assert zone == zones + 1 : "Wrong number of zones";
		assert majorIndex == newSize : "Wrong resulting tuple size";
		result.hashOrZero(result.computeHashFromTo(1, majorIndex));
		if (canDestroy && isMutable)
		{
			object.assertObjectUnreachableIfMutable();
		}
		result.verify();
		return result;
	}

	@Override
	public @NotNull AvailObject o_CopyTupleFromToCanDestroy (
		final AvailObject object,
		final int start,
		final int end,
		final boolean canDestroy)
	{
		// Make a tuple that only contains the given range of elements of the
		// given tuple.
		// Overidden in o_Tuple so that if isMutable and canDestroy are true
		// then the
		// parts of the tuple outside the subrange will have their refcounts
		// decremented
			assert 1 <= start && start <= end + 1;
		assert 0 <= end && end <= object.tupleSize();
		if (start - 1 == end)
		{
			if (isMutable && canDestroy)
			{
				object.assertObjectUnreachableIfMutable();
			}
			return TupleDescriptor.empty();
		}
		if (isMutable && canDestroy && (start == 1 || end - start < 20))
		{
			if (start != 1)
			{
				for (int i = 1, _end1 = end - start + 1; i <= _end1; i++)
				{
					object.tupleAtPut(i, object.tupleAt(start + i - 1));
				}
			}
			object.truncateTo(end - start + 1);
			return object;
		}
		final AvailObject result = AvailObject
				.newObjectIndexedIntegerIndexedDescriptor(
					1,
					2,
					SpliceTupleDescriptor.mutable());
		result.hashOrZero(object.computeHashFromTo(start, end));
		result.forZoneSetSubtupleStartSubtupleIndexEndOfZone(
			1,
			object,
			start,
			(end - start + 1));
		result.verify();
		return result;
	}

	@Override
	public byte o_ExtractNybbleFromTupleAt (
		final AvailObject object,
		final int index)
	{
		// Get the element at the given index in the tuple object, and extract a
		// nybble from it.
		// Fail if it's not a nybble. Obviously overidden for speed in
		// NybbleTupleDescriptor
			final int nyb = object.tupleIntAt(index);
		if (!(nyb >= 0 && nyb <= 15))
		{
			error("nybble is out of range", object);
			return 0;
		}
		return (byte) nyb;
	}

	@Override
	public int o_HashFromTo (
		final AvailObject object,
		final int startIndex,
		final int endIndex)
	{
		// Compute object's hash value over the given range.

		if (startIndex == 1 && endIndex == object.tupleSize())
		{
			return object.hash();
		}
		return object.computeHashFromTo(startIndex, endIndex);
	}

	@Override
	public @NotNull AvailObject o_TupleAt (final AvailObject object, final int index)
	{
		// Answer the element at the given index in the tuple object.

		error(
			"Subclass responsibility: Object:tupleAt: in Avail.TupleDescriptor",
			object);
		return VoidDescriptor.voidObject();
	}

	@Override
	public @NotNull AvailObject o_TupleAtPuttingCanDestroy (
		final AvailObject object,
		final int index,
		final AvailObject newValueObject,
		final boolean canDestroy)
	{
		// Answer a tuple with all the elements of object except at the given
		// index we should
		// have newValueObject. This may destroy the original tuple if
			error(
			"Subclass responsibility: Object:tupleAt:putting:canDestroy: in Avail.TupleDescriptor",
			object);
		return VoidDescriptor.voidObject();
	}

	@Override
	public int o_TupleIntAt (final AvailObject object, final int index)
	{
		// Answer the integer element at the given index in the tuple object.

		error(
			"Subclass responsibility: Object:tupleIntAt: in Avail.TupleDescriptor",
			object);
		return 0;
	}

	@Override
	public @NotNull AvailObject o_AsSet (final AvailObject object)
	{
		// Convert object to a set.

		AvailObject result = SetDescriptor.empty();
		AvailObject.lock(object);
		for (int i = 1, _end1 = object.tupleSize(); i <= _end1; i++)
		{
			result = result.setWithElementCanDestroy(object.tupleAt(i), true);
		}
		AvailObject.unlock(object);
		return result;
	}

	@Override
	public boolean o_IsTuple (final AvailObject object)
	{
		return true;
	}

	@Override
	public boolean o_IsByteTuple (final AvailObject object)
	{
		for (int i = 1, end = object.tupleSize(); i <= end; i++)
		{
			if (!object.tupleAt(i).isByte())
			{
				return false;
			}
		}

		return true;
	}

	/**
	 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
	 */
	@Override
	public boolean o_IsString (final @NotNull AvailObject object)
	{
		for (int i = 1, end = object.tupleSize(); i <= end; i++)
		{
			if (!object.tupleAt(i).isCharacter())
			{
				return false;
			}
		}

		return true;
	}

	@Override
	public int o_TupleSize (final AvailObject object)
	{
		// Answer the number of elements in the object (as a Smalltalk Integer).

		error(
			"Subclass responsibility: o_TupleSize: in Avail.TupleDescriptor",
			object);
		return 0;
	}

	@Override
	public boolean o_IsSplice (final AvailObject object)
	{
		return false;
	}

	/**
	 * Compute the object's hash value.
	 *
	 * @param object The object to hash.
	 * @return The hash value.
	 */
	int computeHashForObject (final AvailObject object)
	{
		return object.computeHashFromTo(1, object.tupleSize());
	}

	@Override
	public int o_ComputeHashFromTo (
		final AvailObject object,
		final int start,
		final int end)
	{
		// Compute the hash value from the object's data. The result should be
		// a Smalltalk Integer between 16r00000000 and 16rFFFFFFFF inclusive.
		// To keep the rehashing cost down for concatenated tuples, we use a
		// non-commutative hash function. If the tuple has elements with hash
		// values h[1] through h[n], we use the formula...
		// H=h[1]a^1 + h[2]a^2 + h[3]a^3... + h[n]a^n
		// This can be rewritten as sum(i=1..n)(a^i * h[i]). The constant 'a' is
		// chosen as a primitive element of (Z[2^32],*), specifically 1664525,
		// as taken from Knuth, The Art of Computer Programming, Vol. 2, 2nd
		// ed., page 102, row 26. See also pages 19, 20, theorems B and C. The
		// period of this cycle is 2^30. The element hash values are xored with
		// a random constant (16r9EE570A6) before being used, to help prevent
			int hash = 0;
		for (int index = end; index >= start; index--)
		{
		final int itemHash = object.tupleAt(index).hash() ^ PreToggle;
			hash = hash * Multiplier + itemHash;
		}
		return hash * Multiplier;
	}

	@Override
	public String o_AsNativeString (
		final AvailObject object)
	{
		// Only applicable to tuples that contain characters.

		final int size = object.tupleSize();
		final StringBuilder builder = new StringBuilder(size);
		for (int i = 1; i <= size; i++)
		{
			builder.appendCodePoint(object.tupleAt(i).codePoint());
		}
		return builder.toString();
	}

	/**
	 * Answer a mutable copy of object that holds arbitrary objects.
	 */
	@Override
	public @NotNull AvailObject o_CopyAsMutableObjectTuple (
		final AvailObject object)
	{
		final int size = object.tupleSize();
		final AvailObject result = ObjectTupleDescriptor.mutable().create(size);
		result.hashOrZero(object.hashOrZero());
		for (int i = 1; i <= size; i++)
		{
			result.tupleAtPut(i, object.tupleAt(i));
		}
		return result;
	}

	/**
	 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
	 */
	@Override
	public @NotNull
	Iterator<AvailObject> o_Iterator (
		final @NotNull AvailObject object)
	{
		final AvailObject selfSnapshot = object.copyAsMutableObjectTuple();
		final int size = selfSnapshot.tupleSize();
		return new Iterator<AvailObject>()
		{
			/**
			 * The index of the next {@linkplain AvailObject element}.
			 */
			int index = 1;

			@Override
			public boolean hasNext ()
			{
				return index <= size;
			}

			@Override
			public @NotNull AvailObject next ()
			{
				if (index > size)
				{
					throw new NoSuchElementException();
				}

				return selfSnapshot.tupleAt(index++);
			}

			@Override
			public void remove ()
			{
				throw new UnsupportedOperationException();
			}
		};
	}

	// Startup/shutdown

	static AvailObject EmptyTuple;
	static AvailObject UnderscoreTuple;
	static AvailObject OpenChevronTuple;
	static AvailObject CloseChevronTuple;
	static AvailObject DoubleDaggerTuple;
	static AvailObject BackQuoteTuple;

	static void createWellKnownObjects ()
	{
		EmptyTuple = NybbleTupleDescriptor.isMutableSize(true, 0).create();
		EmptyTuple.hashOrZero(0);
		EmptyTuple.makeImmutable();

		UnderscoreTuple = ByteStringDescriptor.from("_");
		UnderscoreTuple.makeImmutable();

		OpenChevronTuple = ByteStringDescriptor.from("«");
		OpenChevronTuple.makeImmutable();

		CloseChevronTuple = ByteStringDescriptor.from("»");
		CloseChevronTuple.makeImmutable();

		DoubleDaggerTuple = ByteStringDescriptor.from("‡");
		DoubleDaggerTuple.makeImmutable();

		BackQuoteTuple = ByteStringDescriptor.from("`");
		BackQuoteTuple.makeImmutable();
	}

	static void clearWellKnownObjects ()
	{
		// Clear my cached empty tuple and underscore tuple.

		EmptyTuple = null;
		UnderscoreTuple = null;
		OpenChevronTuple = null;
		CloseChevronTuple = null;
		DoubleDaggerTuple = null;
		BackQuoteTuple = null;
	}

	public static AvailObject fromList (
		final List<AvailObject> list)
	{
		AvailObject tuple;
		tuple = ObjectTupleDescriptor.mutable().create(list.size());
		for (int i = 1; i <= list.size(); i++)
		{
			tuple.tupleAtPut(i, list.get(i - 1));
		}
		return tuple;
	}

	public static AvailObject fromIntegerList (
		final List<Integer> list)
	{
		AvailObject tuple;
		final int maxValue = list.size() == 0 ? 0 : max(list);
		if (maxValue <= 15)
		{
			tuple = NybbleTupleDescriptor.isMutableSize(true, list.size())
				.create((list.size() + 7) / 8);
			for (int i = 1; i <= list.size(); i++)
			{
				tuple.rawNybbleAtPut(i, list.get(i - 1).byteValue());
			}
		}
		else if (maxValue <= 255)
		{
			tuple = ByteTupleDescriptor.isMutableSize(true, list.size())
				.create((list.size() + 3) / 4);
			for (int i = 1; i <= list.size(); i++)
			{
				tuple.rawByteAtPut(i, list.get(i - 1).shortValue());
			}
		}
		else
		{
			tuple = ObjectTupleDescriptor.mutable().create(list.size());
			for (int i = 1; i <= list.size(); i++)
			{
				tuple.tupleAtPut(
					i,
					IntegerDescriptor.fromInt(list.get(i - 1).intValue()));
			}
		}
		return tuple;
	}

	/* Value conversion... */
	static int multiplierRaisedTo (final int anInteger)
	{
		int result = 1;
		int power = Multiplier;
		int residue = anInteger;
		while (residue != 0)
		{
			if ((residue & 1) != 0)
			{
				result *= power;
			}
			power *= power;
			residue >>>= 1;
		}
		return result;
	};

	/* Special object access */
	public static AvailObject empty ()
	{
		return EmptyTuple;
	};

	public static AvailObject underscoreTuple ()
	{
		return UnderscoreTuple;
	};

	public static AvailObject openChevronTuple ()
	{
		return OpenChevronTuple;
	};

	public static AvailObject closeChevronTuple ()
	{
		return CloseChevronTuple;
	};

	public static AvailObject doubleDaggerTuple ()
	{
		return DoubleDaggerTuple;
	};

	public static AvailObject backQuoteTuple ()
	{
		return BackQuoteTuple;
	};

	/* Hash scrambling... */
	static final int PreToggle = 0xE570A6;

	/**
	 * Construct a new {@link TupleDescriptor}.
	 *
	 * @param isMutable
	 *            Does the {@linkplain Descriptor descriptor} represent a
	 *            mutable object?
	 */
	protected TupleDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}
}
