/**
 * descriptor/SpliceTupleDescriptor.java
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

import com.avail.descriptor.AvailObject;
import com.avail.descriptor.SpliceTupleDescriptor;
import com.avail.descriptor.TupleDescriptor;
import com.avail.descriptor.VoidDescriptor;
import java.util.List;
import static com.avail.descriptor.AvailObject.*;
import static java.lang.Math.*;

@IntegerSlots({
	"hashOrZero", 
	"_DoNotGenerateIntegerAt#"
})
@ObjectSlots("_DoNotGenerateObjectAt#")
public class SpliceTupleDescriptor extends TupleDescriptor
{


	// java printing

	void printObjectOnAvoidingIndent (
			final AvailObject object, 
			final StringBuilder aStream, 
			final List<AvailObject> recursionList, 
			final int indent)
	{
		if ((object.tupleSize() == 0))
		{
			aStream.append("<>");
			return;
		}
		boolean allChars = true;
		for (int i = 1, _end1 = object.tupleSize(); i <= _end1; i++)
		{
			if (! object.tupleAt(i).isCharacter())
			{
				allChars = false;
			}
		}
		if (allChars)
		{
			if (isMutable())
			{
				aStream.append("(mut)");
			}
			aStream.append("SpliceTuple: \"");
			for (int i = 1, _end2 = object.tupleSize(); i <= _end2; i++)
			{
				final char c = ((char)(object.tupleAt(i).codePoint()));
				if (((c == '\"') || ((c == '\'') || (c == '\\'))))
				{
					aStream.append('\\');
					aStream.append(c);
				}
				else
				{
					aStream.append(c);
				}
			}
			aStream.append('\"');
		}
		else
		{
			super.printObjectOnAvoidingIndent(
				object,
				aStream,
				recursionList,
				indent);
		}
	}



	// operations

	boolean ObjectCompareFromToWithStartingAt (
			final AvailObject object, 
			final int startIndex1, 
			final int endIndex1, 
			final AvailObject anotherObject, 
			final int startIndex2)
	{
		//  Compare a subrange of this splice tuple and a subrange of the given tuple.
		//
		//  Compare identity...

		if ((object.sameAddressAs(anotherObject) && (startIndex1 == startIndex2)))
		{
			return true;
		}
		for (int zone = object.zoneForIndex(startIndex1), _end1 = object.zoneForIndex(endIndex1); zone <= _end1; zone++)
		{
			final int clipOffsetInZone = max ((object.translateToZone(startIndex1, zone) - object.startSubtupleIndexInZone(zone)), 0);
			if (! object.subtupleForZone(zone).compareFromToWithStartingAt(
				(object.startSubtupleIndexInZone(zone) + clipOffsetInZone),
				min (object.endSubtupleIndexInZone(zone), object.translateToZone(endIndex1, zone)),
				anotherObject,
				(((object.startOfZone(zone) - startIndex1) + startIndex2) + clipOffsetInZone)))
			{
				return false;
			}
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
		//  Compare a subrange of this splice tuple and a subrange of the given bytestring.
		//
		//  Verify the tuple entries are the same inside.

		for (int zone = object.zoneForIndex(startIndex1), _end1 = object.zoneForIndex(endIndex1); zone <= _end1; zone++)
		{
			final int clipOffsetInZone = max ((object.translateToZone(startIndex1, zone) - object.startSubtupleIndexInZone(zone)), 0);
			if (! object.subtupleForZone(zone).compareFromToWithByteStringStartingAt(
				(object.startSubtupleIndexInZone(zone) + clipOffsetInZone),
				min (object.endSubtupleIndexInZone(zone), object.translateToZone(endIndex1, zone)),
				aByteString,
				(((startIndex2 + object.startOfZone(zone)) + clipOffsetInZone) - startIndex1)))
			{
				return false;
			}
		}
		return true;
	}

	boolean ObjectCompareFromToWithByteTupleStartingAt (
			final AvailObject object, 
			final int startIndex1, 
			final int endIndex1, 
			final AvailObject aByteTuple, 
			final int startIndex2)
	{
		//  Compare a subrange of this splice tuple and a subrange of the given byte tuple.
		//
		//  Verify the tuple entries are the same inside.

		for (int zone = object.zoneForIndex(startIndex1), _end1 = object.zoneForIndex(endIndex1); zone <= _end1; zone++)
		{
			final int clipOffsetInZone = max ((object.translateToZone(startIndex1, zone) - object.startSubtupleIndexInZone(zone)), 0);
			if (! object.subtupleForZone(zone).compareFromToWithByteTupleStartingAt(
				(object.startSubtupleIndexInZone(zone) + clipOffsetInZone),
				min (object.endSubtupleIndexInZone(zone), object.translateToZone(endIndex1, zone)),
				aByteTuple,
				(((startIndex2 + object.startOfZone(zone)) + clipOffsetInZone) - startIndex1)))
			{
				return false;
			}
		}
		return true;
	}

	boolean ObjectCompareFromToWithNybbleTupleStartingAt (
			final AvailObject object, 
			final int startIndex1, 
			final int endIndex1, 
			final AvailObject aNybbleTuple, 
			final int startIndex2)
	{
		//  Compare a subrange of this splice tuple and a subrange of the given nybble tuple.
		//
		//  Compare identity...

		if ((object.sameAddressAs(aNybbleTuple) && (startIndex1 == startIndex2)))
		{
			return true;
		}
		for (int zone = object.zoneForIndex(startIndex1), _end1 = object.zoneForIndex(endIndex1); zone <= _end1; zone++)
		{
			final int clipOffsetInZone = max ((object.translateToZone(startIndex1, zone) - object.startSubtupleIndexInZone(zone)), 0);
			if (! object.subtupleForZone(zone).compareFromToWithNybbleTupleStartingAt(
				(object.startSubtupleIndexInZone(zone) + clipOffsetInZone),
				min (object.endSubtupleIndexInZone(zone), object.translateToZone(endIndex1, zone)),
				aNybbleTuple,
				(object.startOfZone(zone) + clipOffsetInZone)))
			{
				return false;
			}
		}
		return true;
	}

	boolean ObjectCompareFromToWithObjectTupleStartingAt (
			final AvailObject object, 
			final int startIndex1, 
			final int endIndex1, 
			final AvailObject anObjectTuple, 
			final int startIndex2)
	{
		//  Compare a subrange of this splice tuple and a subrange of the given object tuple.
		//
		//  Compare identity...

		if ((object.sameAddressAs(anObjectTuple) && (startIndex1 == startIndex2)))
		{
			return true;
		}
		for (int zone = object.zoneForIndex(startIndex1), _end1 = object.zoneForIndex(endIndex1); zone <= _end1; zone++)
		{
			final int clipOffsetInZone = max ((object.translateToZone(startIndex1, zone) - object.startSubtupleIndexInZone(zone)), 0);
			if (! object.subtupleForZone(zone).compareFromToWithObjectTupleStartingAt(
				(object.startSubtupleIndexInZone(zone) + clipOffsetInZone),
				min (object.endSubtupleIndexInZone(zone), object.translateToZone(endIndex1, zone)),
				anObjectTuple,
				(((object.startOfZone(zone) - startIndex1) + startIndex2) + clipOffsetInZone)))
			{
				return false;
			}
		}
		return true;
	}

	boolean ObjectEquals (
			final AvailObject object, 
			final AvailObject another)
	{
		//  Compare this splice tuple and the given tuple.

		return another.equalsAnyTuple(object);
	}

	boolean ObjectEqualsAnyTuple (
			final AvailObject object, 
			final AvailObject anotherTuple)
	{
		//  Compare this splice tuple and the given tuple.
		//
		//  Compare identity...

		if (object.sameAddressAs(anotherTuple))
		{
			return true;
		}
		if (! (object.tupleSize() == anotherTuple.tupleSize()))
		{
			return false;
		}
		if (! (object.hash() == anotherTuple.hash()))
		{
			return false;
		}
		for (int zone = 1, _end1 = object.numberOfZones(); zone <= _end1; zone++)
		{
			if (! object.subtupleForZone(zone).compareFromToWithStartingAt(
				object.startSubtupleIndexInZone(zone),
				object.endSubtupleIndexInZone(zone),
				anotherTuple,
				object.startOfZone(zone)))
			{
				return false;
			}
		}
		if (((! anotherTuple.isSplice()) || (anotherTuple.numberOfZones() < object.numberOfZones())))
		{
			object.becomeIndirectionTo(anotherTuple);
			anotherTuple.makeImmutable();
		}
		else
		{
			anotherTuple.becomeIndirectionTo(object);
			object.makeImmutable();
		}
		return true;
	}

	boolean ObjectIsHashAvailable (
			final AvailObject object)
	{
		//  Answer whether this object's hash value can be computed without creating
		//  new objects.  This method is used by the garbage collector to decide which
		//  objects to attempt to coalesce.  The garbage collector uses the hash values
		//  to find objects that it is likely can be coalesced together.

		if (! (object.hashOrZero() == 0))
		{
			return true;
		}
		for (int zone = 1, _end1 = object.numberOfZones(); zone <= _end1; zone++)
		{
			if (! object.subtupleForZone(zone).isHashAvailable())
			{
				return false;
			}
		}
		//  Conservative approximation
		return true;
	}



	// operations-splice tuples

	int ObjectEndOfZone (
			final AvailObject object, 
			final int zone)
	{
		//  Answer the ending index for the given zone.

		return object.integerSlotAtByteIndex((((zone * 2) + _numberOfFixedIntegerSlots) * 4));
	}

	int ObjectEndSubtupleIndexInZone (
			final AvailObject object, 
			final int zone)
	{
		//  Answer the ending index into the subtuple for the given zone.

		return ((object.integerSlotAtByteIndex(((((zone * 2) - 1) + _numberOfFixedIntegerSlots) * 4)) + object.sizeOfZone(zone)) - 1);
	}

	AvailObject ObjectForZoneSetSubtupleStartSubtupleIndexEndOfZone (
			final AvailObject object, 
			final int zone, 
			final AvailObject newSubtuple, 
			final int startSubtupleIndex, 
			final int endOfZone)
	{
		//  Replace the zone information with the given zone information.  This is fairly low-level
		//  and 'unclipped'.  Should only be legal if isMutable is true.

		assert _isMutable;
		object.objectSlotAtByteIndexPut(((zone + _numberOfFixedObjectSlots) * -4), newSubtuple);
		object.integerSlotAtByteIndexPut(((((zone * 2) - 1) + _numberOfFixedIntegerSlots) * 4), startSubtupleIndex);
		object.integerSlotAtByteIndexPut((((zone * 2) + _numberOfFixedIntegerSlots) * 4), endOfZone);
		return object;
	}

	void ObjectSetSubtupleForZoneTo (
			final AvailObject object, 
			final int zoneIndex, 
			final AvailObject newTuple)
	{
		//  Modify the subtuple holding the elements for the given zone.  This is 'unclipped'.  Should only
		//  be valid if isMutable is true.

		assert _isMutable;
		object.objectSlotAtByteIndexPut(((zoneIndex + _numberOfFixedObjectSlots) * -4), newTuple);
	}

	int ObjectSizeOfZone (
			final AvailObject object, 
			final int zone)
	{
		//  Answer the size of the given zone.

		if ((zone == 1))
		{
			return object.integerSlotAtByteIndex(((2 + _numberOfFixedIntegerSlots) * 4));
		}
		return (object.integerSlotAtByteIndex((((zone * 2) + _numberOfFixedIntegerSlots) * 4)) - object.integerSlotAtByteIndex(((((zone * 2) - 2) + _numberOfFixedIntegerSlots) * 4)));
	}

	int ObjectStartOfZone (
			final AvailObject object, 
			final int zone)
	{
		//  Answer the starting index for the given zone.

		if ((zone == 1))
		{
			return 1;
		}
		return (object.integerSlotAtByteIndex(((((zone * 2) - 2) + _numberOfFixedIntegerSlots) * 4)) + 1);
	}

	int ObjectStartSubtupleIndexInZone (
			final AvailObject object, 
			final int zone)
	{
		//  Answer the starting index into the subtuple for the given zone.

		return object.integerSlotAtByteIndex(((((zone * 2) - 1) + _numberOfFixedIntegerSlots) * 4));
	}

	AvailObject ObjectSubtupleForZone (
			final AvailObject object, 
			final int zone)
	{
		//  Answer the subtuple holding the elements for the given zone.  This is 'unclipped'.

		return object.objectSlotAtByteIndex(((zone + _numberOfFixedObjectSlots) * -4));
	}

	int ObjectTranslateToZone (
			final AvailObject object, 
			final int tupleIndex, 
			final int zoneIndex)
	{
		//  Convert the tuple index into an index into the (unclipped) subtuple for the given zone.

		return ((tupleIndex - object.startOfZone(zoneIndex)) + object.startSubtupleIndexInZone(zoneIndex));
	}

	int ObjectZoneForIndex (
			final AvailObject object, 
			final int index)
	{
		//  Answer the zone number that contains the given index.

		int high = object.numberOfZones();
		int low = 1;
		int mid;
		while (! ((high == low))) {
			mid = ((high + low) / 2);
			if ((index <= object.endOfZone(mid)))
			{
				high = mid;
			}
			else
			{
				low = (mid + 1);
			}
		}
		return high;
	}

	int ObjectNumberOfZones (
			final AvailObject object)
	{
		//  Answer the number of zones in the splice tuple.

		return (object.objectSlotsCount() - _numberOfFixedObjectSlots);
	}



	// operations-tuples

	AvailObject ObjectCopyTupleFromToCanDestroy (
			final AvailObject object, 
			final int start, 
			final int end, 
			final boolean canDestroy)
	{
		//  Make a tuple that only contains the given range of elements of the given tuple.
		//  Optimized here to extract the applicable zones into a new splice tuple, preventing
		//  buildup of layers of splice tuples (to one level if other optimizations hold).

		assert (1 <= start && start <= (end + 1));
		assert (0 <= end && end <= object.tupleSize());
		if (((start - 1) == end))
		{
			return TupleDescriptor.empty();
		}
		final int lowZone = object.zoneForIndex(start);
		final int highZone = object.zoneForIndex(end);
		final AvailObject result = AvailObject.newObjectIndexedIntegerIndexedDescriptor(
			((highZone - lowZone) + 1),
			(((highZone - lowZone) + 1) * 2),
			SpliceTupleDescriptor.mutableDescriptor());
		result.hashOrZero(object.computeHashFromTo(start, end));
		int mainIndex = 0;
		int destZone = 1;
		for (int zone = lowZone; zone <= highZone; zone++)
		{
			final int leftClippedFromZone = max ((object.translateToZone(start, zone) - object.startSubtupleIndexInZone(zone)), 0);
			//  ...only nonzero for first used zone, and only if start is part way through it.
			final int rightClippedFromZone = max ((object.endSubtupleIndexInZone(zone) - object.translateToZone(end, zone)), 0);
			//  ...only nonzero for last used zone, and only if end is part way through it.
			mainIndex = (((mainIndex + object.sizeOfZone(zone)) - leftClippedFromZone) - rightClippedFromZone);
			result.forZoneSetSubtupleStartSubtupleIndexEndOfZone(
				destZone,
				object.subtupleForZone(zone),
				(object.startSubtupleIndexInZone(zone) + leftClippedFromZone),
				mainIndex);
			++destZone;
		}
		assert (mainIndex == ((end - start) + 1)) : "Incorrect zone clipping for splice tuple";
		//  There should be no empty zones if the above algorithm is correct.
		result.verify();
		return result;
	}

	AvailObject ObjectTupleAt (
			final AvailObject object, 
			final int index)
	{
		//  Answer the element at the given index in the tuple object.

		if (((index < 1) || (index > object.tupleSize())))
		{
			error("Out of bounds access to SpliceTuple", object);
			return VoidDescriptor.voidObject();
		}
		final int zoneIndex = object.zoneForIndex(index);
		return object.subtupleForZone(zoneIndex).tupleAt(object.translateToZone(index, zoneIndex));
	}

	void ObjectTupleAtPut (
			final AvailObject object, 
			final int index, 
			final AvailObject anObject)
	{
		//  Error - tupleAt:put: is not supported by SpliceTuples.  The different tuple variants have
		//  different requirements of anObject, and there is no sensible variation for SpliceTuples.

		error("This message is not appropriate for a SpliceTuple", object);
		return;
	}

	AvailObject ObjectTupleAtPuttingCanDestroy (
			final AvailObject object, 
			final int index, 
			final AvailObject newValueObject, 
			final boolean canDestroy)
	{
		//  Answer a tuple with all the elements of object except at the given index we should
		//  have newValueObject.  This may destroy the original tuple if canDestroy is true.

		assert ((index >= 1) && (index <= object.tupleSize()));
		if (! (canDestroy & _isMutable))
		{
			return object.copyAsMutableSpliceTuple().tupleAtPuttingCanDestroy(
				index,
				newValueObject,
				true);
		}
		final int zoneIndex = object.zoneForIndex(index);
		object.setSubtupleForZoneTo(zoneIndex, object.subtupleForZone(zoneIndex).tupleAtPuttingCanDestroy(
			object.translateToZone(index, zoneIndex),
			newValueObject,
			canDestroy));
		object.hashOrZero(0);
		//  ...invalidate the hash value.
		return object;
	}

	int ObjectTupleIntAt (
			final AvailObject object, 
			final int index)
	{
		//  Answer the integer element at the given index in the tuple object.

		if (((index < 1) || (index > object.tupleSize())))
		{
			error("Out of bounds access to SpliceTuple", object);
			return 0;
		}
		final int zoneIndex = object.zoneForIndex(index);
		return object.subtupleForZone(zoneIndex).tupleIntAt(object.translateToZone(index, zoneIndex));
	}

	int ObjectTupleSize (
			final AvailObject object)
	{
		//  Answer the number of elements in the object (as a Smalltalk Integer).

		return object.endOfZone(object.numberOfZones());
	}



	// private-accessing

	int ObjectBitsPerEntry (
			final AvailObject object)
	{
		//  Answer approximately how many bits per entry are taken up by this object.

		//  Make this always seem a little worse than any of the other representations
		return 33;
	}

	boolean ObjectIsSplice (
			final AvailObject object)
	{
		return true;
	}



	// private-computation

	int ObjectComputeHashFromTo (
			final AvailObject object, 
			final int startIndex, 
			final int endIndex)
	{
		//  Hash part of the tuple object.

		int hash = 0;
		int pieceMultiplierPower = 0;
		for (int zone = object.zoneForIndex(startIndex), _end1 = object.zoneForIndex(endIndex); zone <= _end1; zone++)
		{
			final int clipOffsetInZone = max ((object.translateToZone(startIndex, zone) - object.startSubtupleIndexInZone(zone)), 0);
			//  Can only be nonzero for leftmost affected zone, and only if start > start of zone.
			final AvailObject piece = object.subtupleForZone(zone);
			final int startInPiece = (object.startSubtupleIndexInZone(zone) + clipOffsetInZone);
			final int endInPiece = min (object.endSubtupleIndexInZone(zone), object.translateToZone(endIndex, zone));
			int pieceHash = piece.hashFromTo(startInPiece, endInPiece);
			pieceHash = ((pieceHash * TupleDescriptor.multiplierRaisedTo(pieceMultiplierPower)) & HashMask);
			pieceMultiplierPower = (((pieceMultiplierPower + endInPiece) - startInPiece) + 1);
			hash += pieceHash;
		}
		return (hash & HashMask);
	}



	// private-copying

	AvailObject ObjectCopyAsMutableSpliceTuple (
			final AvailObject object)
	{
		//  Answer a mutable copy of object that also only holds bytes.

		if (_isMutable)
		{
			object.makeSubobjectsImmutable();
		}
		final AvailObject result = AvailObject.newObjectIndexedIntegerIndexedDescriptor(
			(object.objectSlotsCount() - _numberOfFixedObjectSlots),
			((object.objectSlotsCount() - _numberOfFixedObjectSlots) * 2),
			SpliceTupleDescriptor.mutableDescriptor());
		assert (result.objectSlotsCount() == object.objectSlotsCount());
		result.hashOrZero(object.hashOrZero());
		for (int byteIndex = ((_numberOfFixedObjectSlots + 1) * -4), _end1 = (object.objectSlotsCount() * -4); byteIndex >= _end1; byteIndex -= 4)
		{
			result.objectSlotAtByteIndexPut(byteIndex, object.objectSlotAtByteIndex(byteIndex));
		}
		for (int byteIndex = ((_numberOfFixedIntegerSlots + 1) * 4), _end2 = (object.integerSlotsCount() * 4); byteIndex <= _end2; byteIndex += 4)
		{
			result.integerSlotAtByteIndexPut(byteIndex, object.integerSlotAtByteIndex(byteIndex));
		}
		result.verify();
		return result;
	}



	// private-verification

	void ObjectVerify (
			final AvailObject object)
	{
		//  Make sure the object contains no empty zones.

		assert (object.tupleSize() > 0);
		for (int i = 1, _end1 = object.numberOfZones(); i <= _end1; i++)
		{
			assert (object.sizeOfZone(i) > 0);
		}
	}





	/* Descriptor lookup */
	public static SpliceTupleDescriptor mutableDescriptor()
	{
		return (SpliceTupleDescriptor) AllDescriptors [146];
	};
	public static SpliceTupleDescriptor immutableDescriptor()
	{
		return (SpliceTupleDescriptor) AllDescriptors [147];
	};

}
