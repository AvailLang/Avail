/**
 * descriptor/TupleDescriptor.java
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

import com.avail.descriptor.ApproximateTypeDescriptor;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.ByteStringDescriptor;
import com.avail.descriptor.ByteTupleDescriptor;
import com.avail.descriptor.IntegerDescriptor;
import com.avail.descriptor.NybbleTupleDescriptor;
import com.avail.descriptor.ObjectTupleDescriptor;
import com.avail.descriptor.SetDescriptor;
import com.avail.descriptor.SpliceTupleDescriptor;
import com.avail.descriptor.TupleDescriptor;
import com.avail.descriptor.TupleTypeDescriptor;
import com.avail.descriptor.TypeDescriptor;
import com.avail.descriptor.VoidDescriptor;
import java.util.List;
import static com.avail.descriptor.AvailObject.*;
import static java.lang.Math.*;
import static java.util.Collections.*;

@IntegerSlots("hashOrZero")
public abstract class TupleDescriptor extends Descriptor
{


	// GENERATED accessors

	void ObjectHashOrZero (
			final AvailObject object, 
			final int value)
	{
		//  GENERATED setter method.

		object.integerSlotAtByteIndexPut(4, value);
	}

	int ObjectHashOrZero (
			final AvailObject object)
	{
		//  GENERATED getter method.

		return object.integerSlotAtByteIndex(4);
	}



	// GENERATED special mutable slots

	boolean allowsImmutableToMutableReferenceAtByteIndex (
			final int index)
	{
		//  GENERATED special mutable slots method.

		if ((index == 4))
		{
			return true;
		}
		return false;
	}



	// java printing

	void printObjectOnAvoidingIndent (
			final AvailObject object, 
			final StringBuilder aStream, 
			final List<AvailObject> recursionList, 
			final int indent)
	{
		aStream.append('<');
		for (int i = 1, limit = object.tupleSize(); i <= limit; i++)
		{
			if (i > 1)
			{
				aStream.append(", ");
			}
			object.tupleAt(i).printOnAvoidingIndent(aStream, recursionList, indent + 1);
		}
		aStream.append('>');
	}



	// operations

	boolean ObjectEqualsAnyTuple (
			final AvailObject object, 
			final AvailObject aTuple)
	{
		//  Compare this arbitrary Tuple and the given arbitrary tuple.

		if (object.sameAddressAs(aTuple))
		{
			return true;
		}
		//  Compare sizes...
		final int size = object.tupleSize();
		if (! (size == aTuple.tupleSize()))
		{
			return false;
		}
		if (! (ObjectHash(object) == aTuple.hash()))
		{
			return false;
		}
		for (int i = 1; i <= size; i++)
		{
			if (! ObjectTupleAt(object, i).equals(aTuple.tupleAt(i)))
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
		//  Now that there are at least two references to it
		return true;
	}

	boolean ObjectEqualsByteString (
			final AvailObject object, 
			final AvailObject aByteString)
	{
		//  Default to generic tuple comparison.

		return ObjectEqualsAnyTuple(object, aByteString);
	}

	boolean ObjectEqualsByteTuple (
			final AvailObject object, 
			final AvailObject aTuple)
	{
		//  Default to generic tuple comparison.

		return ObjectEqualsAnyTuple(object, aTuple);
	}

	boolean ObjectEqualsNybbleTuple (
			final AvailObject object, 
			final AvailObject aTuple)
	{
		//  Default to generic comparison.

		return ObjectEqualsAnyTuple(object, aTuple);
	}

	boolean ObjectEqualsObjectTuple (
			final AvailObject object, 
			final AvailObject aTuple)
	{
		//  Default to generic comparison.

		return ObjectEqualsAnyTuple(object, aTuple);
	}

	boolean ObjectEqualsTwoByteString (
			final AvailObject object, 
			final AvailObject aTwoByteString)
	{
		//  Default to generic tuple comparison.

		return ObjectEqualsAnyTuple(object, aTwoByteString);
	}

	boolean ObjectIsBetterRepresentationThan (
			final AvailObject object, 
			final AvailObject anotherObject)
	{
		//  Given two objects that are known to be equal, is the first one in a better form (more
		//  compact, more efficient, older generation) than the second one?

		return (object.bitsPerEntry() < anotherObject.bitsPerEntry());
	}

	boolean ObjectIsInstanceOfSubtypeOf (
			final AvailObject object, 
			final AvailObject aTypeObject)
	{
		//  Answer whether object is an instance of a subtype of aTypeObject.  Don't generate
		//  an approximate type and do the comparison, because the approximate type
		//  will just send this message recursively.

		if (aTypeObject.equals(TypeDescriptor.voidType()))
		{
			return true;
		}
		if (aTypeObject.equals(TypeDescriptor.all()))
		{
			return true;
		}
		if (! aTypeObject.isTupleType())
		{
			return false;
		}
		//  See if it's an acceptable size...
		final AvailObject size = IntegerDescriptor.objectFromInt(object.tupleSize());
		if (! size.isInstanceOfSubtypeOf(aTypeObject.sizeRange()))
		{
			return false;
		}
		//  tuple's size is out of range.
		final AvailObject typeTuple = aTypeObject.typeTuple();
		final int breakIndex = min (object.tupleSize(), typeTuple.tupleSize());
		for (int i = 1; i <= breakIndex; i++)
		{
			if (! object.tupleAt(i).isInstanceOfSubtypeOf(aTypeObject.typeAtIndex(i)))
			{
				return false;
			}
		}
		final AvailObject defaultTypeObject = aTypeObject.defaultType();
		for (int i = (breakIndex + 1), _end1 = object.tupleSize(); i <= _end1; i++)
		{
			if (! object.tupleAt(i).isInstanceOfSubtypeOf(defaultTypeObject))
			{
				return false;
			}
		}
		return true;
	}

	AvailObject ObjectExactType (
			final AvailObject object)
	{
		//  Answer the object's type.  Not very efficient - should cache the type inside the object.

		final AvailObject tupleOfTypes = object.copyAsMutableObjectTuple();
		for (int i = 1, _end1 = object.tupleSize(); i <= _end1; i++)
		{
			tupleOfTypes.tupleAtPuttingCanDestroy(
				i,
				object.tupleAt(i).type(),
				true);
		}
		return TupleTypeDescriptor.tupleTypeForSizesTypesDefaultType(
			IntegerDescriptor.objectFromInt(object.tupleSize()).type(),
			tupleOfTypes,
			TypeDescriptor.terminates());
	}

	int ObjectHash (
			final AvailObject object)
	{
		//  The hash value is stored raw in the object's hashOrZero slot if it has been computed,
		//  otherwise that slot is zero.  If a zero is detected, compute the hash and store it in
		//  hashOrZero.  Note that the hash can (extremely rarely) be zero, in which case the
		//  hash must be computed on demand every time it is requested.

		int hash = object.hashOrZero();
		if ((hash == 0))
		{
			hash = computeHashForObject(object);
			object.hashOrZero(hash);
		}
		return hash;
	}

	AvailObject ObjectType (
			final AvailObject object)
	{
		//  Answer the object's type.

		return ApproximateTypeDescriptor.withInstance(object.makeImmutable());
	}



	// operations-tuples

	boolean ObjectCompareFromToWithStartingAt (
			final AvailObject object, 
			final int startIndex1, 
			final int endIndex1, 
			final AvailObject anotherObject, 
			final int startIndex2)
	{
		error("Subclass responsibility: Object:compareFrom:to:with:startingAt: in Avail.TupleDescriptor", object);
		return false;
	}

	boolean ObjectCompareFromToWithAnyTupleStartingAt (
			final AvailObject object, 
			final int startIndex1, 
			final int endIndex1, 
			final AvailObject aTuple, 
			final int startIndex2)
	{
		//  Compare sections of two tuples.  Default generic comparison.

		int index2 = startIndex2;
		for (int index1 = startIndex1; index1 <= endIndex1; index1++)
		{
			if (! object.tupleAt(index1).equals(aTuple.tupleAt(index2)))
			{
				return false;
			}
			++index2;
		}
		return true;
	}

	boolean ObjectCompareFromToWithByteStringStartingAt (
			final AvailObject object, 
			final int startIndex1, 
			final int endIndex1, 
			final AvailObject aByteString, 
			final int startIndex2)
	{
		//  Compare sections of two tuples.  Default to generic comparison.

		return ObjectCompareFromToWithAnyTupleStartingAt(
			object,
			startIndex1,
			endIndex1,
			aByteString,
			startIndex2);
	}

	boolean ObjectCompareFromToWithByteTupleStartingAt (
			final AvailObject object, 
			final int startIndex1, 
			final int endIndex1, 
			final AvailObject aByteTuple, 
			final int startIndex2)
	{
		//  Compare sections of two tuples.  Default to generic comparison.

		return ObjectCompareFromToWithAnyTupleStartingAt(
			object,
			startIndex1,
			endIndex1,
			aByteTuple,
			startIndex2);
	}

	boolean ObjectCompareFromToWithNybbleTupleStartingAt (
			final AvailObject object, 
			final int startIndex1, 
			final int endIndex1, 
			final AvailObject aNybbleTuple, 
			final int startIndex2)
	{
		//  Compare sections of two tuples.  Default to generic comparison.

		return ObjectCompareFromToWithAnyTupleStartingAt(
			object,
			startIndex1,
			endIndex1,
			aNybbleTuple,
			startIndex2);
	}

	boolean ObjectCompareFromToWithObjectTupleStartingAt (
			final AvailObject object, 
			final int startIndex1, 
			final int endIndex1, 
			final AvailObject anObjectTuple, 
			final int startIndex2)
	{
		//  Compare sections of two tuples.  Default to generic comparison.

		return ObjectCompareFromToWithAnyTupleStartingAt(
			object,
			startIndex1,
			endIndex1,
			anObjectTuple,
			startIndex2);
	}

	boolean ObjectCompareFromToWithTwoByteStringStartingAt (
			final AvailObject object, 
			final int startIndex1, 
			final int endIndex1, 
			final AvailObject aTwoByteString, 
			final int startIndex2)
	{
		//  Compare sections of two tuples.  Default to generic comparison.

		return ObjectCompareFromToWithAnyTupleStartingAt(
			object,
			startIndex1,
			endIndex1,
			aTwoByteString,
			startIndex2);
	}

	AvailObject ObjectConcatenateTuplesCanDestroy (
			final AvailObject object, 
			final boolean canDestroy)
	{
		//  Take a tuple of tuples and answer one big tuple constructed by concatenating the
		//  subtuples together.  Optimized so that the resulting splice tuple's zones are not
		//  themselves splice tuples.

		int zones = 0;
		int newSize = 0;
		for (int i = 1, _end1 = object.tupleSize(); i <= _end1; i++)
		{
			AvailObject sub = object.tupleAt(i);
			final int subZones = ((sub.tupleSize() == 0) ? 0 : (sub.traversed().isSplice() ? sub.numberOfZones() : 1));
			//  Empty zones are not allowed in splice tuples.
			zones += subZones;
			newSize += sub.tupleSize();
		}
		if ((newSize == 0))
		{
			return TupleDescriptor.empty();
		}
		//  Now we know how many zones will be in the final tuple.  We must
		//  allocate room for it.  In the Java translation, the garbage collector
		//  will never increase the number of zones, so  there will always be
		//  room in the allocated chunk (maybe more than enough) for all the
		//  zones.
		final AvailObject result = AvailObject.newObjectIndexedIntegerIndexedDescriptor(
			zones,
			(zones * 2),
			SpliceTupleDescriptor.mutableDescriptor());
		int majorIndex = 0;
		int zone = 1;
		for (int i = 1, _end2 = object.tupleSize(); i <= _end2; i++)
		{
			AvailObject sub = object.tupleAt(i).traversed();
			if (sub.isSplice())
			{
				assert (sub.tupleSize() > 0);
				for (int originalZone = 1, _end3 = sub.numberOfZones(); originalZone <= _end3; originalZone++)
				{
					majorIndex += sub.sizeOfZone(originalZone);
					result.forZoneSetSubtupleStartSubtupleIndexEndOfZone(
						zone,
						sub.subtupleForZone(originalZone),
						sub.startSubtupleIndexInZone(originalZone),
						majorIndex);
					if ((canDestroy && sub.descriptor().isMutable()))
					{
						sub.setSubtupleForZoneTo(originalZone, VoidDescriptor.voidObject());
					}
					++zone;
				}
			}
			else
			{
				if (! (sub.tupleSize() == 0))
				{
					majorIndex += sub.tupleSize();
					result.forZoneSetSubtupleStartSubtupleIndexEndOfZone(
						zone,
						sub,
						1,
						majorIndex);
					++zone;
				}
				if ((canDestroy && isMutable()))
				{
					object.tupleAtPut(i, VoidDescriptor.voidObject());
				}
			}
		}
		assert (zone == (zones + 1)) : "Wrong number of zones";
		assert (majorIndex == newSize) : "Wrong resulting tuple size";
		result.hashOrZero(result.computeHashFromTo(1, majorIndex));
		if ((canDestroy && _isMutable))
		{
			object.assertObjectUnreachableIfMutable();
		}
		result.verify();
		return result;
	}

	AvailObject ObjectCopyTupleFromToCanDestroy (
			final AvailObject object, 
			final int start, 
			final int end, 
			final boolean canDestroy)
	{
		//  Make a tuple that only contains the given range of elements of the given tuple.
		//  Overidden in ObjectTuple so that if isMutable and canDestroy are true then the
		//  parts of the tuple outside the subrange will have their refcounts decremented
		//  and those tuple slots will be nilled out.

		assert (1 <= start && start <= (end + 1));
		assert (0 <= end && end <= object.tupleSize());
		if (((start - 1) == end))
		{
			if ((_isMutable && canDestroy))
			{
				object.assertObjectUnreachableIfMutable();
			}
			return TupleDescriptor.empty();
		}
		if ((_isMutable && (canDestroy && ((start == 1) || ((end - start) < 20)))))
		{
			if (! (start == 1))
			{
				for (int i = 1, _end1 = ((end - start) + 1); i <= _end1; i++)
				{
					object.tupleAtPut(i, object.tupleAt(((start + i) - 1)));
				}
			}
			object.truncateTo(((end - start) + 1));
			return object;
		}
		final AvailObject result = AvailObject.newObjectIndexedIntegerIndexedDescriptor(
			1,
			2,
			SpliceTupleDescriptor.mutableDescriptor());
		result.hashOrZero(object.computeHashFromTo(start, end));
		result.forZoneSetSubtupleStartSubtupleIndexEndOfZone(
			1,
			object,
			start,
			((end - start) + 1));
		result.verify();
		return result;
	}

	byte ObjectExtractNybbleFromTupleAt (
			final AvailObject object, 
			final int index)
	{
		//  Get the element at the given index in the tuple object, and extract a nybble from it.
		//  Fail if it's not a nybble.  Obviously overidden for speed in NybbleTupleDescriptor
		//  and its subclasses.

		final int nyb = object.tupleIntAt(index);
		if (! ((nyb >= 0) && (nyb <= 15)))
		{
			error("nybble is out of range", object);
			return 0;
		}
		return ((byte)(nyb));
	}

	int ObjectHashFromTo (
			final AvailObject object, 
			final int startIndex, 
			final int endIndex)
	{
		//  Compute object's hash value over the given range.

		if (((startIndex == 1) && (endIndex == object.tupleSize())))
		{
			return object.hash();
		}
		return object.computeHashFromTo(startIndex, endIndex);
	}

	AvailObject ObjectTupleAt (
			final AvailObject object, 
			final int index)
	{
		//  Answer the element at the given index in the tuple object.

		error("Subclass responsibility: Object:tupleAt: in Avail.TupleDescriptor", object);
		return VoidDescriptor.voidObject();
	}

	AvailObject ObjectTupleAtPuttingCanDestroy (
			final AvailObject object, 
			final int index, 
			final AvailObject newValueObject, 
			final boolean canDestroy)
	{
		//  Answer a tuple with all the elements of object except at the given index we should
		//  have newValueObject.  This may destroy the original tuple if canDestroy is true.

		error("Subclass responsibility: Object:tupleAt:putting:canDestroy: in Avail.TupleDescriptor", object);
		return VoidDescriptor.voidObject();
	}

	int ObjectTupleIntAt (
			final AvailObject object, 
			final int index)
	{
		//  Answer the integer element at the given index in the tuple object.

		error("Subclass responsibility: Object:tupleIntAt: in Avail.TupleDescriptor", object);
		return 0;
	}

	AvailObject ObjectAsSet (
			final AvailObject object)
	{
		//  Convert object to a set.

		AvailObject result = SetDescriptor.empty();
		AvailObject.lock(object);
		for (int i = 1, _end1 = object.tupleSize(); i <= _end1; i++)
		{
			result = result.setWithElementCanDestroy(object.tupleAt(i), true);
		}
		AvailObject.unlock(object);
		return result;
	}

	boolean ObjectIsTuple (
			final AvailObject object)
	{
		return true;
	}

	int ObjectTupleSize (
			final AvailObject object)
	{
		//  Answer the number of elements in the object (as a Smalltalk Integer).

		error("Subclass responsibility: ObjectTupleSize: in Avail.TupleDescriptor", object);
		return 0;
	}



	// private-accessing

	boolean ObjectIsSplice (
			final AvailObject object)
	{
		return false;
	}



	// private-computation

	int computeHashForObject (
			final AvailObject object)
	{
		//  Compute object's hash value.

		return object.computeHashFromTo(1, object.tupleSize());
	}

	int ObjectComputeHashFromTo (
			final AvailObject object, 
			final int start, 
			final int end)
	{
		//  Compute the hash value from the object's data.  The result should be
		//  a Smalltalk Integer between 16r00000000 and 16rFFFFFFFF inclusive.
		//  To keep the rehashing cost down for concatenated tuples, we use a
		//  non-commutative hash function.  If the tuple has elements with hash
		//  values h[1] through h[n], we use the formula...
		//  H=h[1]a^1 + h[2]a^2 + h[3]a^3... + h[n]a^n
		//  This can be rewritten as sum(i=1..n)(a^i * h[i]).  The constant 'a' is
		//  chosen as a primitive element of (Z[2^32],*), specifically 1664525, as
		//  taken from Knuth, The Art of Computer Programming, Vol. 2, 2nd ed.,
		//  page 102, row 26.  See also pages 19, 20, theorems B and C.  The
		//  period of this cycle is 2^30.  The element hash values are xored with
		//  a random constant (16r9EE570A6) before being used, to help prevent
		//  similar nested tuples from producing equal hashes.

		int hash = 0;
		for (int index = end; index >= start; index--)
		{
			final int itemHash = (object.tupleAt(index).hash() ^ PreToggle);
			hash = (((hash * Multiplier) + itemHash) & HashMask);
		}
		return ((hash * Multiplier) & HashMask);
	}



	// private-copying

	String ObjectAsNativeString (
			final AvailObject object)
	{
		//  Only applicable to tuples that contain characters.

		StringBuilder builder = new StringBuilder(object.tupleSize());
		for (int i = 1, limit = object.tupleSize(); i <= limit; i++)
		{
			builder.appendCodePoint(object.tupleAt(i).codePoint());
		}
		return builder.toString();
	}

	AvailObject ObjectCopyAsMutableObjectTuple (
			final AvailObject object)
	{
		//  Answer a mutable copy of object that holds arbitrary objects.

		final AvailObject result = AvailObject.newIndexedDescriptor(object.tupleSize(), ObjectTupleDescriptor.mutableDescriptor());
		result.hashOrZero(object.hashOrZero());
		for (int i = 1, _end1 = object.tupleSize(); i <= _end1; i++)
		{
			result.tupleAtPut(i, object.tupleAt(i));
		}
		return result;
	}




	// Startup/shutdown

	static AvailObject EmptyTuple;


	static AvailObject UnderscoreTuple;

	static void createWellKnownObjects ()
	{
		EmptyTuple = AvailObject.newIndexedDescriptor(
			0,
			NybbleTupleDescriptor.isMutableSize(true, 0));
		EmptyTuple.hashOrZero(0);
		EmptyTuple.makeImmutable();

		UnderscoreTuple = AvailObject.newIndexedDescriptor(
			1,
			ByteStringDescriptor.isMutableSize(true, 1));
		UnderscoreTuple.rawByteForCharacterAtPut(1, (byte)'_');
		UnderscoreTuple.hashOrZero(0);
		UnderscoreTuple.makeImmutable();
	}

	static void clearWellKnownObjects ()
	{
		//  Clear my cached empty tuple and underscore tuple.

		EmptyTuple = null;
		UnderscoreTuple = null;
	}



	public static AvailObject mutableObjectFromArray (List<AvailObject> list)
	{
		AvailObject tuple;
		tuple = AvailObject.newIndexedDescriptor(
			list.size(),
			ObjectTupleDescriptor.mutableDescriptor());
		for (int i = 1; i <= list.size(); i++)
		{
			tuple.tupleAtPut(i, list.get(i - 1));
		}
		return tuple;
	}

	public static AvailObject mutableCompressedFromIntegerArray (List<Integer> list)
	{
		AvailObject tuple;
		int maxValue = list.size() == 0 ? 0 : max(list);
		if (maxValue <= 15)
		{
			tuple = AvailObject.newIndexedDescriptor(
				(list.size() + 7) / 8,
				NybbleTupleDescriptor.isMutableSize(true, list.size()));
			for (int i = 1; i <= list.size(); i++)
			{
				tuple.rawNybbleAtPut(i, list.get(i - 1).byteValue());
			}
		}
		else if (maxValue <= 255)
		{
			tuple = AvailObject.newIndexedDescriptor(
				(list.size() + 3) / 4,
				ByteTupleDescriptor.isMutableSize(true, list.size()));
			for (int i = 1; i <= list.size(); i++)
			{
				tuple.rawByteAtPut(i, list.get(i - 1).shortValue());
			}
		}
		else
		{
			tuple = AvailObject.newIndexedDescriptor(
				list.size(),
				ObjectTupleDescriptor.mutableDescriptor());
			for (int i = 1; i <= list.size(); i++)
			{
				tuple.tupleAtPut(i, IntegerDescriptor.objectFromInt(list.get(i - 1).intValue()));
			}
		}
		return tuple;
	}


	/* Value conversion... */
	static int multiplierRaisedTo (int anInteger)
	{
		int result = 1;
		int power = Multiplier;
		int residue = anInteger;
		while (residue != 0)
		{
			if ((residue & 1) != 0)
				result *= power;
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

	/* Value conversion... */
	static int multiplierTimes (int anInteger)
	{
		return (Multiplier * anInteger) & HashMask;
	};

	/* Hash scrambling... */
	static final int Multiplier = 1664525;
	static final int PreToggle = 0xE570A6;

}
