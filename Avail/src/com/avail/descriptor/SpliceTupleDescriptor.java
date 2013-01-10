/**
 * SpliceTupleDescriptor.java
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

package com.avail.descriptor;

import static com.avail.descriptor.AvailObject.error;
import static java.lang.Math.*;
import com.avail.annotations.*;

/**
 * TODO: [MvG] Document this type!
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public class SpliceTupleDescriptor
extends TupleDescriptor
{
	/**
	 * The layout of integer slots for my instances.
	 */
	public enum IntegerSlots
	implements IntegerSlotsEnum
	{
		/**
		 * The hash value of this splice tuple, or zero.  If the hash value
		 * happens to equal zero it will have to be recomputed each time it is
		 * requested.
		 */
		@HideFieldInDebugger
		HASH_OR_ZERO,

		/**
		 * TODO: [MvG] Document this.
		 */
		INTEGER_ZONE_DATA_AT_;

		static
		{
			assert TupleDescriptor.IntegerSlots.HASH_OR_ZERO.ordinal()
				== HASH_OR_ZERO.ordinal();
		}
	}

	/**
	 * The layout of object slots for my instances.
	 */
	public enum ObjectSlots
	implements ObjectSlotsEnum
	{
		/**
		 * TODO: [MvG] Document this.
		 */
		OBJECT_ZONE_DATA_AT_
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * Compare a subrange of this splice tuple and a subrange of the given
	 * tuple.
	 * </p>
	 */
	@Override @AvailMethod
	boolean o_CompareFromToWithStartingAt (
		final AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final AvailObject anotherObject,
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

	@Override @AvailMethod
	boolean o_CompareFromToWithByteStringStartingAt (
		final AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final AvailObject aByteString,
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

	@Override @AvailMethod
	boolean o_CompareFromToWithByteTupleStartingAt (
		final AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final AvailObject aByteTuple,
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

	@Override @AvailMethod
	boolean o_CompareFromToWithNybbleTupleStartingAt (
		final AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final AvailObject aNybbleTuple,
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
	@Override @AvailMethod
	boolean o_CompareFromToWithObjectTupleStartingAt (
		final AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final AvailObject anObjectTuple,
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

	@Override @AvailMethod
	boolean o_Equals (final AvailObject object, final AvailObject another)
	{
		return another.equalsAnyTuple(object);
	}

	@Override @AvailMethod
	boolean o_EqualsAnyTuple (
		final AvailObject object,
		final AvailObject anotherTuple)
	{
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
			if (!isShared())
			{
				anotherTuple.makeImmutable();
				object.becomeIndirectionTo(anotherTuple);
			}
		}
		else
		{
			if (!anotherTuple.descriptor.isShared())
			{
				object.makeImmutable();
				anotherTuple.becomeIndirectionTo(object);
			}
		}
		return true;
	}

	@Override @AvailMethod
	int o_EndOfZone (final AvailObject object, final int zone)
	{
		// Answer the ending index for the given zone.
		return object.slot(
			IntegerSlots.INTEGER_ZONE_DATA_AT_,
			zone * 2);
	}

	@Override @AvailMethod
	int o_EndSubtupleIndexInZone (final AvailObject object, final int zone)
	{
		// Answer the ending index into the subtuple for the given zone.
		return
			object.slot(
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
	public AvailObject
		o_ForZoneSetSubtupleStartSubtupleIndexEndOfZone (
			final AvailObject object,
			final int zone,
			final AvailObject newSubtuple,
			final int startSubtupleIndex,
			final int endOfZone)
	{
		assert isMutable();
		object.setSlot(
			ObjectSlots.OBJECT_ZONE_DATA_AT_,
			zone,
			newSubtuple);
		object.setSlot(
			IntegerSlots.INTEGER_ZONE_DATA_AT_,
			zone * 2 - 1,
			startSubtupleIndex);
		object.setSlot(
			IntegerSlots.INTEGER_ZONE_DATA_AT_,
			zone * 2,
			endOfZone);
		return object;
	}

	/**
	 * Modify the subtuple holding the elements for the given zone.  This is
	 * 'unclipped'.  Should only be valid if isMutable is true.
	 */
	@Override @AvailMethod
	void o_SetSubtupleForZoneTo (
		final AvailObject object,
		final int zoneIndex,
		final AvailObject newTuple)
	{
		assert isMutable();
		object.setSlot(
			ObjectSlots.OBJECT_ZONE_DATA_AT_,
			zoneIndex,
			newTuple);
	}

	/**
	 * Answer the size of the given zone.
	 */
	@Override @AvailMethod
	int o_SizeOfZone (final AvailObject object, final int zone)
	{
		if (zone == 1)
		{
			return object.slot(
				IntegerSlots.INTEGER_ZONE_DATA_AT_,
				2);
		}
		return
			object.slot(
				IntegerSlots.INTEGER_ZONE_DATA_AT_,
				zone * 2)
			- object.slot(
				IntegerSlots.INTEGER_ZONE_DATA_AT_,
				zone * 2 - 2);
	}

	/**
	 * Answer the starting index for the given zone.
	 */
	@Override @AvailMethod
	int o_StartOfZone (final AvailObject object, final int zone)
	{
		if (zone == 1)
		{
			return 1;
		}
		return
			object.slot(
				IntegerSlots.INTEGER_ZONE_DATA_AT_,
				zone * 2 - 2)
			+ 1;
	}

	/**
	 * Answer the starting index into the subtuple for the given zone.
	 */
	@Override @AvailMethod
	int o_StartSubtupleIndexInZone (final AvailObject object, final int zone)
	{
		return object.slot(
			IntegerSlots.INTEGER_ZONE_DATA_AT_,
			zone * 2 - 1);
	}

	/**
	 * Answer the subtuple holding the elements for the given zone.  This is
	 * 'unclipped'.
	 */
	@Override @AvailMethod
	AvailObject o_SubtupleForZone (final AvailObject object, final int zone)
	{
		return object.slot(
			ObjectSlots.OBJECT_ZONE_DATA_AT_,
			zone);
	}

	/**
	 * Convert the tuple index into an index into the (unclipped) subtuple for
	 * the given zone.
	 */
	@Override @AvailMethod
	int o_TranslateToZone (
		final AvailObject object,
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
	@Override @AvailMethod
	int o_ZoneForIndex (final AvailObject object, final int index)
	{
		int high = object.numberOfZones();
		int low = 1;
		int mid;
		while (high != low)
		{
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
	@Override @AvailMethod
	int o_NumberOfZones (final AvailObject object)
	{
		return object.variableObjectSlotsCount();
	}

	/**
	 * Make a tuple that only contains the given range of elements of the given
	 * tuple.  Optimized here to extract the applicable zones into a new splice
	 * tuple, preventing buildup of layers of splice tuples (to one level if
	 * other optimizations hold).
	 */
	@Override @AvailMethod
	AvailObject o_CopyTupleFromToCanDestroy (
		final AvailObject object,
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
				SpliceTupleDescriptor.mutable);
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
	@Override @AvailMethod
	AvailObject o_TupleAt (final AvailObject object, final int index)
	{
		if (index < 1 || index > object.tupleSize())
		{
			error("Out of bounds access to SpliceTuple", object);
			return NilDescriptor.nil();
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
	@Override @AvailMethod
	void o_TupleAtPut (
		final AvailObject object,
		final int index,
		final AvailObject anObject)
	{
		error("This message is not appropriate for a SpliceTuple", object);
		return;
	}

	/**
	 * Answer a tuple with all the elements of object except at the given index
	 * we should have newValueObject.  This may destroy the original tuple if
	 * canDestroy is true.
	 */
	@Override @AvailMethod
	AvailObject o_TupleAtPuttingCanDestroy (
		final AvailObject object,
		final int index,
		final AvailObject newValueObject,
		final boolean canDestroy)
	{
		assert index >= 1 && index <= object.tupleSize();
		if (!(canDestroy & isMutable()))
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
	@Override @AvailMethod
	int o_TupleIntAt (final AvailObject object, final int index)
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
	@Override @AvailMethod
	int o_TupleSize (final AvailObject object)
	{
		return object.endOfZone(object.numberOfZones());
	}

	/**
	 * Answer approximately how many bits per entry are taken up by this object.
	 *
	 * <p>
	 * Make this always seem a little worse than any of the other
	 * representations
	 * </p>
	 */
	@Override @AvailMethod
	int o_BitsPerEntry (final AvailObject object)
	{
		return 33;
	}


	@Override @AvailMethod
	boolean o_IsSplice (final AvailObject object)
	{
		return true;
	}

	/**
	 * Hash part of the tuple object.
	 */
	@Override @AvailMethod
	int o_ComputeHashFromTo (
		final AvailObject object,
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
	@Override @AvailMethod
	AvailObject o_CopyAsMutableSpliceTuple (final AvailObject object)
	{
		if (isMutable())
		{
			object.makeSubobjectsImmutable();
		}
		final int numberOfZones = object.numberOfZones();
		final AvailObject result =
			AvailObject.newObjectIndexedIntegerIndexedDescriptor(
				numberOfZones,
				numberOfZones * 2,
				SpliceTupleDescriptor.mutable);
		assert result.objectSlotsCount() == object.objectSlotsCount();
		assert result.integerSlotsCount() == object.integerSlotsCount();
		for (int subscript = 1; subscript <= numberOfZones; subscript++)
		{
			result.setSlot(
				ObjectSlots.OBJECT_ZONE_DATA_AT_,
				subscript,
				object.slot(
					ObjectSlots.OBJECT_ZONE_DATA_AT_,
					subscript));
		}
		for (int subscript = 1; subscript <= numberOfZones * 2; subscript++)
		{
			result.setSlot(
				IntegerSlots.INTEGER_ZONE_DATA_AT_,
				subscript,
				object.slot(
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
	@Override @AvailMethod
	void o_Verify (final AvailObject object)
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
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 */
	private SpliceTupleDescriptor (final Mutability mutability)
	{
		super(mutability);
	}

	/** The mutable {@link SpliceTupleDescriptor}. */
	static final SpliceTupleDescriptor mutable =
		new SpliceTupleDescriptor(Mutability.MUTABLE);

	@Override
	SpliceTupleDescriptor mutable ()
	{
		return mutable;
	}

	/** The immutable {@link SpliceTupleDescriptor}. */
	private static final SpliceTupleDescriptor immutable =
		new SpliceTupleDescriptor(Mutability.IMMUTABLE);

	@Override
	SpliceTupleDescriptor immutable ()
	{
		return immutable;
	}

	/** The shared {@link SpliceTupleDescriptor}. */
	private static final SpliceTupleDescriptor shared =
		new SpliceTupleDescriptor(Mutability.SHARED);

	@Override
	SpliceTupleDescriptor shared ()
	{
		return shared;
	}
}
