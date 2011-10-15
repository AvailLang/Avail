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

import static com.avail.descriptor.AvailObject.error;
import static java.lang.Math.*;
import java.util.List;
import com.avail.annotations.NotNull;

/**
 * TODO: Document this type!
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 */
public class SpliceTupleDescriptor
extends TupleDescriptor
{
	/**
	 * The layout of integer slots for my instances.
	 */
	public enum IntegerSlots
	{
		/**
		 * The hash value of this splice tuple, or zero.  If the hash value
		 * happens to equal zero it will have to be recomputed each time it is
		 * requested.
		 */
		HASH_OR_ZERO,

		/**
		 * TODO
		 */
		INTEGER_ZONE_DATA_AT_
	}

	/**
	 * The layout of object slots for my instances.
	 */
	public enum ObjectSlots
	{
		/**
		 * TODO
		 */
		OBJECT_ZONE_DATA_AT_
	}

	@Override
	public void printObjectOnAvoidingIndent (
		final @NotNull AvailObject object,
		final @NotNull StringBuilder aStream,
		final @NotNull List<AvailObject> recursionList,
		final int indent)
	{
		if (object.tupleSize() == 0)
		{
			aStream.append("<>");
			return;
		}
		if (object.isString())
		{
			if (isMutable())
			{
				aStream.append("(mut)");
			}
			aStream.append("SpliceTuple: \"");
			for (int i = 1, end = object.tupleSize(); i <= end; i++)
			{
				final char c = (char) object.tupleAt(i).codePoint();
				if (c == '\"' || c == '\\')
				{
					aStream.append('\\');
				}
				aStream.append(c);
			}
			aStream.append('"');
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

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * Compare a subrange of this splice tuple and a subrange of the given
	 * tuple.
	 * </p>
	 */
	@Override
	public boolean o_CompareFromToWithStartingAt (
		final @NotNull AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final @NotNull AvailObject anotherObject,
		final int startIndex2)
	{
		if (object.sameAddressAs(anotherObject) && startIndex1 == startIndex2)
		{
			return true;
		}
		final int startZone = object.zoneForIndex(startIndex1);
		final int endZone = object.zoneForIndex(endIndex1);
		for (int zone = startZone; zone <= endZone; zone++)
		{
			final int clipOffsetInZone =
				max(
					object.translateToZone(startIndex1, zone)
						- object.startSubtupleIndexInZone(zone),
					0);
			if (!object.subtupleForZone(zone).compareFromToWithStartingAt(
				object.startSubtupleIndexInZone(zone) + clipOffsetInZone,
				min(
					object.endSubtupleIndexInZone(zone),
					object.translateToZone(endIndex1, zone)),
				anotherObject,
				object.startOfZone(zone)
					- startIndex1
					+ startIndex2
					+ clipOffsetInZone))
			{
				return false;
			}
		}
		return true;
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * Compare a subrange of this splice tuple and a subrange of the given
	 * byte string.
	 * </p>
	 */
	@Override
	public boolean o_CompareFromToWithByteStringStartingAt (
		final @NotNull AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final @NotNull AvailObject aByteString,
		final int startIndex2)
	{
		for (
			int
				zone = object.zoneForIndex(startIndex1),
				end = object.zoneForIndex(endIndex1);
			zone <= end;
			zone++)
		{
			final int clipOffsetInZone = max(
				object.translateToZone(startIndex1, zone)
					- object.startSubtupleIndexInZone(zone),
				0);
			if (!object.subtupleForZone(zone)
				.compareFromToWithByteStringStartingAt(
					object.startSubtupleIndexInZone(zone) + clipOffsetInZone,
					min(
						object.endSubtupleIndexInZone(zone),
						object.translateToZone(endIndex1, zone)),
					aByteString,
					startIndex2
						+ object.startOfZone(zone)
						+ clipOffsetInZone
						- startIndex1))
			{
				return false;
			}
		}
		return true;
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * Compare a subrange of this splice tuple and a subrange of the given
	 * byte tuple.
	 * </p>
	 */
	@Override
	public boolean o_CompareFromToWithByteTupleStartingAt (
		final @NotNull AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final @NotNull AvailObject aByteTuple,
		final int startIndex2)
	{
		for (
			int
				zone = object.zoneForIndex(startIndex1),
				end = object.zoneForIndex(endIndex1);
			zone <= end;
			zone++)
		{
			final int clipOffsetInZone = max(
				object.translateToZone(startIndex1, zone)
					- object.startSubtupleIndexInZone(zone),
				0);
			if (!object.subtupleForZone(zone)
				.compareFromToWithByteTupleStartingAt(
					object.startSubtupleIndexInZone(zone) + clipOffsetInZone,
					min(
						object.endSubtupleIndexInZone(zone),
						object.translateToZone(endIndex1, zone)),
					aByteTuple,
					startIndex2
						+ object.startOfZone(zone)
						+ clipOffsetInZone
						- startIndex1))
			{
				return false;
			}
		}
		return true;
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * Compare a subrange of this splice tuple and a subrange of the given
	 * nybble tuple.
	 * </p>
	 */
	@Override
	public boolean o_CompareFromToWithNybbleTupleStartingAt (
		final @NotNull AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final @NotNull AvailObject aNybbleTuple,
		final int startIndex2)
	{
		if (object.sameAddressAs(aNybbleTuple) && startIndex1 == startIndex2)
		{
			return true;
		}
		for (
			int
				zone = object.zoneForIndex(startIndex1),
				end = object.zoneForIndex(endIndex1);
			zone <= end;
			zone++)
		{
			final int clipOffsetInZone = max(
				object.translateToZone(startIndex1, zone)
					- object.startSubtupleIndexInZone(zone),
				0);
			if (!object.subtupleForZone(zone)
				.compareFromToWithNybbleTupleStartingAt(
					object.startSubtupleIndexInZone(zone) + clipOffsetInZone,
					min(
						object.endSubtupleIndexInZone(zone),
						object.translateToZone(endIndex1, zone)),
					aNybbleTuple,
					object.startOfZone(zone) + clipOffsetInZone))
			{
				return false;
			}
		}
		return true;
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * Compare a subrange of this splice tuple and a subrange of the given
	 * object tuple.
	 * </p>
	 */
	@Override
	public boolean o_CompareFromToWithObjectTupleStartingAt (
		final @NotNull AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final @NotNull AvailObject anObjectTuple,
		final int startIndex2)
	{
		if (object.sameAddressAs(anObjectTuple) && startIndex1 == startIndex2)
		{
			return true;
		}
		for (
			int
				zone = object.zoneForIndex(startIndex1),
				end = object.zoneForIndex(endIndex1);
			zone <= end;
			zone++)
		{
			final int clipOffsetInZone = max(
				object.translateToZone(startIndex1, zone)
					- object.startSubtupleIndexInZone(zone),
				0);
			if (!object.subtupleForZone(zone)
				.compareFromToWithObjectTupleStartingAt(
					object.startSubtupleIndexInZone(zone) + clipOffsetInZone,
					min(
						object.endSubtupleIndexInZone(zone),
						object.translateToZone(endIndex1, zone)),
					anObjectTuple,
					object.startOfZone(zone)
						- startIndex1
						+ startIndex2
						+ clipOffsetInZone))
			{
				return false;
			}
		}
		return true;
	}

	@Override
	public boolean o_Equals (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
	{
		//  Compare this splice tuple and the given tuple.

		return another.equalsAnyTuple(object);
	}

	@Override
	public boolean o_EqualsAnyTuple (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anotherTuple)
	{
		//  Compare this splice tuple and the given tuple.
		//
		//  Compare identity...

		if (object.sameAddressAs(anotherTuple))
		{
			return true;
		}
		if (object.tupleSize() != anotherTuple.tupleSize())
		{
			return false;
		}
		if (object.hash() != anotherTuple.hash())
		{
			return false;
		}
		for (int zone = 1, end = object.numberOfZones(); zone <= end; zone++)
		{
			if (!object.subtupleForZone(zone).compareFromToWithStartingAt(
				object.startSubtupleIndexInZone(zone),
				object.endSubtupleIndexInZone(zone),
				anotherTuple,
				object.startOfZone(zone)))
			{
				return false;
			}
		}
		if (!anotherTuple.isSplice()
			|| anotherTuple.numberOfZones() < object.numberOfZones())
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

	@Override
	public int o_EndOfZone (
		final @NotNull AvailObject object,
		final int zone)
	{
		// Answer the ending index for the given zone.

		return object.integerSlotAt(
			IntegerSlots.INTEGER_ZONE_DATA_AT_,
			zone * 2);
	}

	@Override
	public int o_EndSubtupleIndexInZone (
		final @NotNull AvailObject object,
		final int zone)
	{
		// Answer the ending index into the subtuple for the given zone.

		return
			object.integerSlotAt(
				IntegerSlots.INTEGER_ZONE_DATA_AT_,
				zone * 2 - 1)
			+ object.sizeOfZone(zone)
			- 1;
	}

	/**
	 * Replace the zone information with the given zone information.  This is
	 * fairly low-level and 'unclipped'.  Should only be legal if isMutable is
	 * true.
	 */
	@Override
	public @NotNull AvailObject
		o_ForZoneSetSubtupleStartSubtupleIndexEndOfZone (
			final @NotNull AvailObject object,
			final int zone,
			final @NotNull AvailObject newSubtuple,
			final int startSubtupleIndex,
			final int endOfZone)
	{
		assert isMutable;
		object.objectSlotAtPut(
			ObjectSlots.OBJECT_ZONE_DATA_AT_,
			zone,
			newSubtuple);
		object.integerSlotAtPut(
			IntegerSlots.INTEGER_ZONE_DATA_AT_,
			zone * 2 - 1,
			startSubtupleIndex);
		object.integerSlotAtPut(
			IntegerSlots.INTEGER_ZONE_DATA_AT_,
			zone * 2,
			endOfZone);
		return object;
	}

	/**
	 * Modify the subtuple holding the elements for the given zone.  This is
	 * 'unclipped'.  Should only be valid if isMutable is true.
	 */
	@Override
	public void o_SetSubtupleForZoneTo (
		final @NotNull AvailObject object,
		final int zoneIndex,
		final @NotNull AvailObject newTuple)
	{
		assert isMutable;
		object.objectSlotAtPut(
			ObjectSlots.OBJECT_ZONE_DATA_AT_,
			zoneIndex,
			newTuple);
	}

	/**
	 * Answer the size of the given zone.
	 */
	@Override
	public int o_SizeOfZone (
		final @NotNull AvailObject object,
		final int zone)
	{
		if (zone == 1)
		{
			return object.integerSlotAt(
				IntegerSlots.INTEGER_ZONE_DATA_AT_,
				2);
		}
		return
			object.integerSlotAt(
				IntegerSlots.INTEGER_ZONE_DATA_AT_,
				zone * 2)
			- object.integerSlotAt(
				IntegerSlots.INTEGER_ZONE_DATA_AT_,
				zone * 2 - 2);
	}

	/**
	 * Answer the starting index for the given zone.
	 */
	@Override
	public int o_StartOfZone (
		final @NotNull AvailObject object,
		final int zone)
	{
		if (zone == 1)
		{
			return 1;
		}
		return
			object.integerSlotAt(
				IntegerSlots.INTEGER_ZONE_DATA_AT_,
				zone * 2 - 2)
			+ 1;
	}

	/**
	 * Answer the starting index into the subtuple for the given zone.
	 */
	@Override
	public int o_StartSubtupleIndexInZone (
		final @NotNull AvailObject object,
		final int zone)
	{
		return object.integerSlotAt(
			IntegerSlots.INTEGER_ZONE_DATA_AT_,
			zone * 2 - 1);
	}

	/**
	 * Answer the subtuple holding the elements for the given zone.  This is
	 * 'unclipped'.
	 */
	@Override
	public @NotNull AvailObject o_SubtupleForZone (
		final @NotNull AvailObject object,
		final int zone)
	{
		return object.objectSlotAt(
			ObjectSlots.OBJECT_ZONE_DATA_AT_,
			zone);
	}

	/**
	 * Convert the tuple index into an index into the (unclipped) subtuple for
	 * the given zone.
	 */
	@Override
	public int o_TranslateToZone (
		final @NotNull AvailObject object,
		final int tupleIndex,
		final int zoneIndex)
	{
		return
			tupleIndex
			- object.startOfZone(zoneIndex)
			+ object.startSubtupleIndexInZone(zoneIndex);
	}

	/**
	 * Answer the zone number that contains the given index.
	 */
	@Override
	public int o_ZoneForIndex (
		final @NotNull AvailObject object,
		final int index)
	{
		int high = object.numberOfZones();
		int low = 1;
		int mid;
		while (high != low) {
			mid = (high + low) / 2;
			if (index <= object.endOfZone(mid))
			{
				high = mid;
			}
			else
			{
				low = mid + 1;
			}
		}
		return high;
	}

	/**
	 * Answer the number of zones in the splice tuple.
	 */
	@Override
	public int o_NumberOfZones (
		final @NotNull AvailObject object)
	{
		return object.variableObjectSlotsCount();
	}

	/**
	 * Make a tuple that only contains the given range of elements of the given
	 * tuple.  Optimized here to extract the applicable zones into a new splice
	 * tuple, preventing buildup of layers of splice tuples (to one level if
	 * other optimizations hold).
	 */
	@Override
	public @NotNull AvailObject o_CopyTupleFromToCanDestroy (
		final @NotNull AvailObject object,
		final int start,
		final int end,
		final boolean canDestroy)
	{

		assert 1 <= start && start <= end + 1;
		assert 0 <= end && end <= object.tupleSize();
		if (start - 1 == end)
		{
			return TupleDescriptor.empty();
		}
		final int lowZone = object.zoneForIndex(start);
		final int highZone = object.zoneForIndex(end);
		final AvailObject result =
			AvailObject.newObjectIndexedIntegerIndexedDescriptor(
				(highZone - lowZone + 1),
				((highZone - lowZone + 1) * 2),
				SpliceTupleDescriptor.mutable());
		result.hashOrZero(object.computeHashFromTo(start, end));
		int mainIndex = 0;
		int destZone = 1;
		for (int zone = lowZone; zone <= highZone; zone++)
		{
			final int leftClippedFromZone = max(
				object.translateToZone(start, zone)
					- object.startSubtupleIndexInZone(zone),
				0);
			// ...only nonzero for first used zone, and only if start is part
			// way through it.
			final int rightClippedFromZone = max(
				(object.endSubtupleIndexInZone(zone)
					- object.translateToZone(end, zone)),
				0);
			// ...only nonzero for last used zone, and only if end is part way
			// through it.
			mainIndex +=
				object.sizeOfZone(zone)
				- leftClippedFromZone
				- rightClippedFromZone;
			result.forZoneSetSubtupleStartSubtupleIndexEndOfZone(
				destZone,
				object.subtupleForZone(zone),
				object.startSubtupleIndexInZone(zone) + leftClippedFromZone,
				mainIndex);
			destZone++;
		}
		assert mainIndex == end - start + 1
			: "Incorrect zone clipping for splice tuple";
		//  There should be no empty zones if the above algorithm is correct.
		result.verify();
		return result;
	}

	/**
	 * Answer the element at the given index in the tuple object.
	 */
	@Override
	public @NotNull AvailObject o_TupleAt (
		final @NotNull AvailObject object,
		final int index)
	{
		if (index < 1 || index > object.tupleSize())
		{
			error("Out of bounds access to SpliceTuple", object);
			return NullDescriptor.nullObject();
		}
		final int zoneIndex = object.zoneForIndex(index);
		final AvailObject subtuple = object.subtupleForZone(zoneIndex);
		return subtuple.tupleAt(object.translateToZone(index, zoneIndex));
	}

	/**
	 * Error - tupleAt:put: is not supported by SpliceTuples.  The different
	 * tuple variants have different requirements of anObject, and there is no
	 * sensible variation for SpliceTuples.
	 */
	@Override
	public void o_TupleAtPut (
		final @NotNull AvailObject object,
		final int index,
		final @NotNull AvailObject anObject)
	{
		error("This message is not appropriate for a SpliceTuple", object);
		return;
	}

	/**
	 * Answer a tuple with all the elements of object except at the given index
	 * we should have newValueObject.  This may destroy the original tuple if
	 * canDestroy is true.
	 */
	@Override
	public @NotNull AvailObject o_TupleAtPuttingCanDestroy (
		final @NotNull AvailObject object,
		final int index,
		final @NotNull AvailObject newValueObject,
		final boolean canDestroy)
	{
		assert index >= 1 && index <= object.tupleSize();
		if (!(canDestroy & isMutable))
		{
			return object.copyAsMutableSpliceTuple().tupleAtPuttingCanDestroy(
				index,
				newValueObject,
				true);
		}
		final int zoneIndex = object.zoneForIndex(index);
		final AvailObject oldSubtuple = object.subtupleForZone(zoneIndex);
		final AvailObject newSubtuple = oldSubtuple.tupleAtPuttingCanDestroy(
			object.translateToZone(index, zoneIndex),
			newValueObject,
			canDestroy);
		object.setSubtupleForZoneTo(
			zoneIndex,
			newSubtuple);
		object.hashOrZero(0);
		return object;
	}

	/**
	 * Answer the integer element at the given index in the tuple object.
	 */
	@Override
	public int o_TupleIntAt (
		final @NotNull AvailObject object,
		final int index)
	{
		if (index < 1 || index > object.tupleSize())
		{
			error("Out of bounds access to SpliceTuple", object);
			return 0;
		}
		final int zoneIndex = object.zoneForIndex(index);
		final AvailObject subtuple = object.subtupleForZone(zoneIndex);
		return subtuple.tupleIntAt(object.translateToZone(index, zoneIndex));
	}

	/**
	 * Answer the number of elements in the object as an int.
	 */
	@Override
	public int o_TupleSize (
		final @NotNull AvailObject object)
	{
		return object.endOfZone(object.numberOfZones());
	}

	/**
	 * Answer approximately how many bits per entry are taken up by this object.
	 * <p>
	 * Make this always seem a little worse than any of the other
	 * representations
	 */
	@Override
	public int o_BitsPerEntry (
		final @NotNull AvailObject object)
	{
		return 33;
	}


	@Override
	public boolean o_IsSplice (
		final @NotNull AvailObject object)
	{
		return true;
	}

	/**
	 * Hash part of the tuple object.
	 */
	@Override
	public int o_ComputeHashFromTo (
		final @NotNull AvailObject object,
		final int startIndex,
		final int endIndex)
	{
		int hash = 0;
		int pieceMultiplierPower = 0;
		for (
				int
					zone = object.zoneForIndex(startIndex),
					end = object.zoneForIndex(endIndex);
				zone <= end;
				zone++)
		{
			final int clipOffsetInZone = max(
				object.translateToZone(startIndex, zone)
					- object.startSubtupleIndexInZone(zone),
				0);
			// Can only be nonzero for leftmost affected zone, and only if
			// start > start of zone.
			final AvailObject piece = object.subtupleForZone(zone);
			final int startInPiece = object.startSubtupleIndexInZone(zone)
				+ clipOffsetInZone;
			final int endInPiece = min(
				object.endSubtupleIndexInZone(zone),
				object.translateToZone(endIndex, zone));
			int pieceHash = piece.hashFromTo(startInPiece, endInPiece);
			pieceHash *= TupleDescriptor.multiplierRaisedTo(
				pieceMultiplierPower);
			pieceMultiplierPower += endInPiece - startInPiece + 1;
			hash += pieceHash;
		}
		return hash;
	}

	/**
	 * Answer a mutable copy of object that is also a splice tuple.
	 */
	@Override
	public @NotNull AvailObject o_CopyAsMutableSpliceTuple (
		final @NotNull AvailObject object)
	{
		if (isMutable)
		{
			object.makeSubobjectsImmutable();
		}
		final int numberOfZones = object.numberOfZones();
		final AvailObject result =
			AvailObject.newObjectIndexedIntegerIndexedDescriptor(
				numberOfZones,
				numberOfZones * 2,
				SpliceTupleDescriptor.mutable());
		assert result.objectSlotsCount() == object.objectSlotsCount();
		assert result.integerSlotsCount() == object.integerSlotsCount();
		for (int subscript = 1; subscript <= numberOfZones; subscript++)
		{
			result.objectSlotAtPut(
				ObjectSlots.OBJECT_ZONE_DATA_AT_,
				subscript,
				object.objectSlotAt(
					ObjectSlots.OBJECT_ZONE_DATA_AT_,
					subscript));
		}
		for (int subscript = 1; subscript <= numberOfZones * 2; subscript++)
		{
			result.integerSlotAtPut(
				IntegerSlots.INTEGER_ZONE_DATA_AT_,
				subscript,
				object.integerSlotAt(
					IntegerSlots.INTEGER_ZONE_DATA_AT_,
					subscript));
		}
		result.hashOrZero(object.hashOrZero());
		result.verify();
		return result;
	}

	/**
	 * Make sure the object contains no empty zones.
	 */
	@Override
	public void o_Verify (
		final @NotNull AvailObject object)
	{
		assert object.tupleSize() > 0;
		for (int i = object.numberOfZones(); i >= 1; i--)
		{
			assert object.sizeOfZone(i) > 0;
		}
	}

	/**
	 * Construct a new {@link SpliceTupleDescriptor}.
	 *
	 * @param isMutable
	 *        Does the {@linkplain Descriptor descriptor} represent a mutable
	 *        object?
	 */
	protected SpliceTupleDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}


	/**
	 * The mutable {@link SpliceTupleDescriptor}.
	 */
	private final static SpliceTupleDescriptor mutable =
		new SpliceTupleDescriptor(true);


	/**
	 * Answer the mutable {@link SpliceTupleDescriptor}.
	 *
	 * @return The mutable {@link SpliceTupleDescriptor}.
	 */
	public static SpliceTupleDescriptor mutable ()
	{
		return mutable;
	}


	/**
	 * The immutable {@link SpliceTupleDescriptor}.
	 */
	private final static SpliceTupleDescriptor immutable =
		new SpliceTupleDescriptor(false);


	/**
	 * Answer the immutable {@link SpliceTupleDescriptor}.
	 *
	 * @return The immutable {@link SpliceTupleDescriptor}.
	 */
	public static SpliceTupleDescriptor immutable ()
	{
		return immutable;
	}
}
