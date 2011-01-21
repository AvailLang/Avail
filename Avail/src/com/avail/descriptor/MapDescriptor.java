/**
 * descriptor/MapDescriptor.java
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

import static com.avail.descriptor.AvailObject.*;
import static com.avail.descriptor.TypeDescriptor.Types.*;
import java.util.*;
import com.avail.utility.*;

/**
 * I represent a discrete function whose keys and values are arbitrary Avail
 * objects.  My type depends on the union of the types of my keys, as well as
 * the union of the types of my values.
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 */
public class MapDescriptor extends Descriptor
{

	/**
	 * The layout of integer slots for my instances.
	 */
	public enum IntegerSlots
	{
		INTERNAL_HASH,
		MAP_SIZE,
		NUM_BLANKS
	}

	/**
	 * The layout of object slots for my instances.
	 */
	public enum ObjectSlots
	{
		DATA_AT_INDEX_
	}


	@Override
	public AvailObject o_DataAtIndex (
		final AvailObject object,
		final int subscript)
	{
		return object.objectSlotAt(ObjectSlots.DATA_AT_INDEX_, subscript);
	}

	@Override
	public void o_DataAtIndexPut (
		final AvailObject object,
		final int subscript,
		final AvailObject value)
	{
		object.objectSlotAtPut(ObjectSlots.DATA_AT_INDEX_, subscript, value);
	}

	/**
	 * Setter for field internalHash.
	 */
	@Override
	public void o_InternalHash (
		final AvailObject object,
		final int value)
	{
		object.integerSlotPut(IntegerSlots.INTERNAL_HASH, value);
	}

	/**
	 * Setter for field mapSize.
	 */
	@Override
	public void o_MapSize (
		final AvailObject object,
		final int value)
	{
		object.integerSlotPut(IntegerSlots.MAP_SIZE, value);
	}

	/**
	 * Setter for field numBlanks.
	 */
	@Override
	public void o_NumBlanks (
		final AvailObject object,
		final int value)
	{
		object.integerSlotPut(IntegerSlots.NUM_BLANKS, value);
	}

	/**
	 * Getter for field internalHash.
	 */
	@Override
	public int o_InternalHash (
		final AvailObject object)
	{
		return object.integerSlot(IntegerSlots.INTERNAL_HASH);
	}

	/**
	 * Getter for field mapSize.
	 */
	@Override
	public int o_MapSize (
		final AvailObject object)
	{
		return object.integerSlot(IntegerSlots.MAP_SIZE);
	}

	/**
	 * Getter for field numBlanks.
	 */
	@Override
	public int o_NumBlanks (
		final AvailObject object)
	{
		return object.integerSlot(IntegerSlots.NUM_BLANKS);
	}



	// java printing

	@Override
	public void printObjectOnAvoidingIndent (
		final AvailObject object,
		final StringBuilder aStream,
		final List<AvailObject> recursionList,
		final int indent)
	{
		final int size = object.mapSize();
		if (size == 0)
		{
			aStream.append("[->]");
			return;
		}
		if (size > 1)
		{
			aStream.append("Map:");
		}
		object.mapDo(new Continuation2<AvailObject, AvailObject>()
			{
			@Override
			public void value (final AvailObject key, final AvailObject value)
			{
				if (size > 1)
				{
					aStream.append("\n");
					for (int t = 1; t <= indent; t++)
					{
						aStream.append("\t");
					}
				}
				aStream.append('[');
				key.printOnAvoidingIndent(aStream, recursionList, indent + 1);
				aStream.append("->");
				value.printOnAvoidingIndent(aStream, recursionList, indent + 1);
				aStream.append(']');
			}
			});
	}



	// operations

	@Override
	public boolean o_Equals (
		final AvailObject object,
		final AvailObject another)
	{
		return another.equalsMap(object);
	}

	@Override
	public boolean o_EqualsMap (
		final AvailObject object,
		final AvailObject aMap)
	{
		if (object.internalHash() != aMap.internalHash())
		{
			return false;
		}
		if (object.mapSize() != aMap.mapSize())
		{
			return false;
		}
		for (int i = 1, _end1 = object.capacity(); i <= _end1; i++)
		{
			final AvailObject keyObject = object.keyAtIndex(i);
			if (!keyObject.equalsVoidOrBlank())
			{
				if (!aMap.hasKey(keyObject))
				{
					return false;
				}
				if (!aMap.mapAt(keyObject).equals(object.valueAtIndex(i)))
				{
					return false;
				}
			}
		}
		return true;
	}

	@Override
	public boolean o_IsInstanceOfSubtypeOf (
		final AvailObject object,
		final AvailObject aTypeObject)
	{
		//  Answer whether object is an instance of a subtype of aTypeObject.  Don't generate
		//  an approximate type and do the comparison, because the approximate type
		//  will just send this message recursively.

		if (aTypeObject.equals(VOID_TYPE.o()))
		{
			return true;
		}
		if (aTypeObject.equals(ALL.o()))
		{
			return true;
		}
		if (!aTypeObject.isMapType())
		{
			return false;
		}
		//  See if it's an acceptable size...
		final AvailObject size = IntegerDescriptor.objectFromInt(object.mapSize());
		if (!size.isInstanceOfSubtypeOf(aTypeObject.sizeRange()))
		{
			return false;
		}
		//  map's size is out of range.
		final AvailObject keyTypeObject = aTypeObject.keyType();
		final AvailObject valueTypeObject = aTypeObject.valueType();
		AvailObject key;
		AvailObject value;
		for (int i = 1, _end1 = object.capacity(); i <= _end1; i++)
		{
			key = object.keyAtIndex(i);
			if (!key.equalsVoidOrBlank())
			{
				if (!key.isInstanceOfSubtypeOf(keyTypeObject))
				{
					return false;
				}
				value = object.valueAtIndex(i);
				if (!value.equalsVoidOrBlank()
						&& !value.isInstanceOfSubtypeOf(valueTypeObject))
				{
					return false;
				}
			}
		}
		return true;
	}

	@Override
	public AvailObject o_ExactType (
		final AvailObject object)
	{
		//  Answer the object's type.

		final Mutable<AvailObject> keyType = new Mutable<AvailObject>(
				TERMINATES.o());
		final Mutable<AvailObject> valueType = new Mutable<AvailObject>(
				TERMINATES.o());
		object.mapDo(new Continuation2<AvailObject, AvailObject>()
			{
			@Override
			public void value (final AvailObject key, final AvailObject value)
			{
				keyType.value = keyType.value.typeUnion(key.type());
				valueType.value = valueType.value.typeUnion(value.type());
			}
			});
		return MapTypeDescriptor.mapTypeForSizesKeyTypeValueType(
			IntegerDescriptor.objectFromInt(object.mapSize()).type(),
			keyType.value,
			valueType.value);
	}

	@Override
	public int o_Hash (
		final AvailObject object)
	{
		//  Take the internal hash, and twiddle it (so nested maps won't cause unwanted correlation).

		return object.internalHash() + 0x1D79B13 ^ 0x1A9A22FE;
	}

	@Override
	public boolean o_IsHashAvailable (
		final AvailObject object)
	{
		//  Answer whether this object's hash value can be computed without creating
		//  new objects.  This method is used by the garbage collector to decide which
		//  objects to attempt to coalesce.  The garbage collector uses the hash values
		//  to find objects that it is likely can be coalesced together.
		//
		//  This is ONLY overridden here for the garbage collector's use.  It probably
		//  slows down the garbage collector in some cases.

		for (int i = 1, _end1 = object.capacity(); i <= _end1; i++)
		{
			if (!object.keyAtIndex(i).isHashAvailable())
			{
				return false;
			}
			if (!object.valueAtIndex(i).isHashAvailable())
			{
				return false;
			}
		}
		return true;
	}

	@Override
	public AvailObject o_Type (
		final AvailObject object)
	{
		//  Answer the object's type.

		return ApproximateTypeDescriptor.withInstance(object.makeImmutable());
	}



	// operations-map

	@Override
	public boolean o_HasKey (
		final AvailObject object,
		final AvailObject keyObject)
	{
		//  Answer whether the map has the given key.  Note that we don't stop searching
		//  when we reach a blank, only when we reach void or the target object.

		final int modulus = object.capacity();
		int h = (int)((keyObject.hash() & 0xFFFFFFFFL) % modulus + 1);
		while (true) {
			final AvailObject slotObject = object.keyAtIndex(h);
			if (slotObject.equalsVoid())
			{
				return false;
			}
			if (slotObject.equals(keyObject))
			{
				return true;
			}
			h = h == modulus ? 1 : h + 1;
		}
	}

	@Override
	public AvailObject o_MapAt (
		final AvailObject object,
		final AvailObject keyObject)
	{
		//  Answer the value of the map at the specified key.  Fail if the key is not present.

		final int modulus = object.capacity();
		int h = (int)((keyObject.hash() & 0xFFFFFFFFL) % modulus + 1);
		while (true) {
			final AvailObject slotObject = object.keyAtIndex(h);
			if (slotObject.equalsVoid())
			{
				error("Key not found in map", object);
				return VoidDescriptor.voidObject();
			}
			if (slotObject.equals(keyObject))
			{
				return object.valueAtIndex(h);
			}
			h = h == modulus ? 1 : h + 1;
		}
	}

	@Override
	public AvailObject o_MapAtPuttingCanDestroy (
		final AvailObject object,
		final AvailObject keyObject,
		final AvailObject newValueObject,
		final boolean canDestroy)
	{
		//  Answer a map like this one but with keyObject->newValueObject instead of any existing
		//  mapping for keyObject.  The original map can be destroyed if canDestroy is true and it's mutable.

		keyObject.hash();
		newValueObject.hash();
		//  Forces hash value to be available so GC can't happen during internalHash update.
		final int neededCapacity = (object.mapSize() + 1 + object.numBlanks()) * 4 / 3 + 1;
		if (canDestroy && isMutable &&
				(object.hasKey(keyObject)
						|| object.capacity() >= neededCapacity))
		{
			return object.privateMapAtPut(keyObject, newValueObject);
		}
		final AvailObject result = MapDescriptor.newWithCapacity(object.mapSize() * 2 + 5);
		//  Start new map just over 50% free (with no blanks).
		CanAllocateObjects(false);
		object.mapDo(new Continuation2<AvailObject, AvailObject>()
			{
			@Override
			public void value (final AvailObject key, final AvailObject value)
			{
				result.privateMapAtPut(key, value);
			}
			});
		result.privateMapAtPut(keyObject, newValueObject);
		CanAllocateObjects(true);
		return result;
	}

	@Override
	public AvailObject o_MapWithoutKeyCanDestroy (
		final AvailObject object,
		final AvailObject keyObject,
		final boolean canDestroy)
	{
		//  Answer a map like this one but with keyObject removed from it.
		//  The original map can be destroyed if canDestroy is true and it's mutable.

		if (!object.hasKey(keyObject))
		{
			if (!canDestroy)
			{
				object.makeImmutable();
			}
			//  Existing reference will be kept around.
			return object;
		}
		if (canDestroy && isMutable)
		{
			return object.privateExcludeKey(keyObject);
		}
		final AvailObject result = MapDescriptor.newWithCapacity(object.capacity());
		CanAllocateObjects(false);
		object.mapDo(new Continuation2<AvailObject, AvailObject>()
			{
			@Override
			public void value (final AvailObject key, final AvailObject value)
			{
				if (!key.equals(keyObject))
				{
					result.privateMapAtPut(key, value);
				}
			}
			});
		CanAllocateObjects(true);
		return result;
	}

	@Override
	public AvailObject o_AsObject (
		final AvailObject object)
	{
		//  Convert the receiver into an object.

		return ObjectDescriptor.objectFromMap(object);
	}

	@Override
	public int o_Capacity (
		final AvailObject object)
	{
		//  Answer the total number of slots reserved for holding keys.

		return object.objectSlotsCount() - numberOfFixedObjectSlots() >>> 1;
	}

	@Override
	public boolean o_IsMap (
		final AvailObject object)
	{
		return true;
	}

	@Override
	public AvailObject o_KeysAsSet (
		final AvailObject object)
	{
		//  Answer a set with all my keys.  Mark the keys as immutable because they'll be shared with the new set.

		AvailObject.lock(object);
		AvailObject result = SetDescriptor.empty();
		for (int i = 1, _end1 = object.capacity(); i <= _end1; i++)
		{
			final AvailObject eachKeyObject = object.keyAtIndex(i);
			if (!eachKeyObject.equalsVoidOrBlank())
			{
				result = result.setWithElementCanDestroy(eachKeyObject.makeImmutable(), true);
			}
		}
		AvailObject.unlock(object);
		return result;
	}

	/**
	 * Answer a tuple with all my values.  Mark the values as immutable because
	 * they'll be shared with the new tuple.
	 */
	@Override
	public AvailObject o_ValuesAsTuple (
		final AvailObject object)
	{
		final AvailObject result = ObjectTupleDescriptor.mutable().create(
			object.mapSize());
		AvailObject.lock(result);
		CanAllocateObjects(false);
		for (int i = 1, _end1 = object.mapSize(); i <= _end1; i++)
		{
			result.tupleAtPut(i, VoidDescriptor.voidObject());
		}
		result.hashOrZero(0);
		final Mutable<Integer> targetIndex = new Mutable<Integer>(1);
		object.mapDo(new Continuation2<AvailObject, AvailObject>()
			{
			@Override
			public void value (final AvailObject key, final AvailObject value)
			{
				value.makeImmutable();
				result.tupleAtPut(targetIndex.value, value);
				targetIndex.value++;
			}
			});
		assert targetIndex.value == object.mapSize() + 1;
		CanAllocateObjects(true);
		AvailObject.unlock(result);
		return result;
	}



	// operations-private

	@Override
	public AvailObject o_KeyAtIndex (
		final AvailObject object,
		final int index)
	{
		//  Answer the map's indexth key.

		return object.dataAtIndex(index + index - 1);
	}

	@Override
	public void o_KeyAtIndexPut (
		final AvailObject object,
		final int index,
		final AvailObject keyObject)
	{
		//  Set the map's indexth key.

		object.dataAtIndexPut((index * 2 - 1), keyObject);
	}

	@Override
	public AvailObject o_PrivateExcludeKey (
		final AvailObject object,
		final AvailObject keyObject)
	{
		//  Remove keyObject from the map's keys if it's present.  The map must be mutable.
		//  Also, computing the key's hash value should not cause an allocation.

		assert keyObject.isHashAvailable() & !keyObject.equalsVoidOrBlank() & isMutable;
		final int h0 = keyObject.hash();
		final int modulus = object.capacity();
		int probe = (int)((h0 & 0xFFFFFFFFL) % modulus + 1);
		AvailObject.lock(object);
		while (true) {
			final AvailObject slotValue = object.keyAtIndex(probe);
			if (slotValue.equalsVoid())
			{
				AvailObject.unlock(object);
				return object;
			}
			if (slotValue.equals(keyObject))
			{
				object.internalHash(
					object.internalHash()
					^ h0 + object.valueAtIndex(probe).hash() * 23);
				object.keyAtIndexPut(probe, BlankDescriptor.blank());
				object.valueAtIndexPut(probe, VoidDescriptor.voidObject());
				object.mapSize((object.mapSize() - 1));
				object.numBlanks((object.numBlanks() + 1));
				AvailObject.unlock(object);
				return object;
			}
			if (probe == modulus)
			{
				probe = 1;
			}
			else
			{
				probe++;
			}
		}
	}

	@Override
	public AvailObject o_PrivateMapAtPut (
		final AvailObject object,
		final AvailObject keyObject,
		final AvailObject valueObject)
	{
		//  Make keyObject go to valueObject in the map.  The object must be mutable and have
		//  room for the new element.  Also, computing the key's hash value should not cause
		//  an allocation.

		assert keyObject.isHashAvailable() & !keyObject.equalsVoidOrBlank() & isMutable;
		assert (object.mapSize() + object.numBlanks()) * 4 <= object.capacity() * 3;
		final int h0 = keyObject.hash();
		final int modulus = object.capacity();
		int probe = (int)((h0 & 0xFFFFFFFFL) % modulus + 1);
		AvailObject.lock(object);
		int tempHash;
		while (true) {
			final AvailObject slotValue = object.keyAtIndex(probe);
			if (slotValue.equals(keyObject))
			{
				tempHash = object.internalHash()
				^ h0 + object.valueAtIndex(probe).hash() * 23;
				tempHash ^= h0 + valueObject.hash() * 23;
				object.internalHash(tempHash);
				object.valueAtIndexPut(probe, valueObject);
				AvailObject.unlock(object);
				return object;
			}
			if (slotValue.equalsVoidOrBlank())
			{
				object.keyAtIndexPut(probe, keyObject);
				object.valueAtIndexPut(probe, valueObject);
				object.mapSize((object.mapSize() + 1));
				object.internalHash(
					object.internalHash() ^ h0 + valueObject.hash() * 23);
				if (slotValue.equalsBlank())
				{
					object.numBlanks(object.numBlanks() - 1);
				}
				AvailObject.unlock(object);
				return object;
			}
			probe = probe == modulus ? 1 : probe + 1;
		}
	}

	@Override
	public AvailObject o_ValueAtIndex (
		final AvailObject object,
		final int index)
	{
		//  Answer the map's indexth value.

		return object.dataAtIndex(index * 2);
	}

	@Override
	public void o_ValueAtIndexPut (
		final AvailObject object,
		final int index,
		final AvailObject valueObject)
	{
		//  Set the map's indexth value.

		object.dataAtIndexPut(index * 2, valueObject);
	}



	// private-copying

	@Override
	public List<AvailObject> o_KeysAsArray (
		final AvailObject object)
		{
		//  Utility method - collect the object's keys into a Smalltalk Array.

		AvailObject.lock(object);
		List<AvailObject> result;
		result = new ArrayList<AvailObject>(object.mapSize());
		for (int i = 1, _end1 = object.capacity(); i <= _end1; i++)
		{
			final AvailObject eachKeyObject = object.keyAtIndex(i);
			if (!eachKeyObject.equalsVoidOrBlank())
			{
				result.add(eachKeyObject.makeImmutable());
			}
		}
		assert result.size() == object.mapSize();
		AvailObject.unlock(object);
		return result;
		}


	@Override
	public void o_mapDo (
		final AvailObject object,
		final Continuation2<AvailObject, AvailObject> continuation)
	{
		// Iterate over the keys and values of the map, invoking the
		// continuation for each pair.
		AvailObject.lock(object);
		for (int i = 1, _end1 = object.capacity(); i <= _end1; i++)
		{
			final AvailObject key = object.keyAtIndex(i);
			if (!key.equalsVoidOrBlank())
			{
				continuation.value(key, object.valueAtIndex(i));
			}
		}
		AvailObject.unlock(object);
	}


	/**
	 * An immutable empty map.
	 */
	static AvailObject EmptyMap;

	/**
	 * Initialize my EmptyMap static field.
	 */
	static void createWellKnownObjects ()
	{
		EmptyMap = newWithCapacity(3);
		EmptyMap.makeImmutable();
	}

	/**
	 * Clear my EmptyMap static field.
	 */
	static void clearWellKnownObjects ()
	{
		EmptyMap = null;
	}



	/**
	 * Create a new map with the given initial capacity.  The capacity is a
	 * measure of how many slot pairs a map contains, and as such is always
	 * somewhat larger than the maximum number of keys the map may actually
	 * contain.
	 *
	 * @param capacity The number of key/value slot pairs to reserve.
	 * @return A new map.
	 */
	public static AvailObject newWithCapacity (final int capacity)
	{
		final AvailObject result = mutable().create(capacity * 2);
		result.internalHash(0);
		result.mapSize(0);
		result.numBlanks(0);
		for (int i = 1; i <= capacity * 2; i++)
		{
			result.dataAtIndexPut(i, VoidDescriptor.voidObject());
		};
		return result;
	};


	/**
	 * Return the (immutable) empty map.
	 *
	 * @return An empty, immutable map.
	 */
	public static AvailObject empty ()
	{
		return EmptyMap;
	};

	/**
	 * Construct a new {@link MapDescriptor}.
	 *
	 * @param isMutable
	 *        Does the {@linkplain Descriptor descriptor} represent a mutable
	 *        object?
	 */
	protected MapDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}

	/**
	 * The mutable {@link MapDescriptor}.
	 */
	private final static MapDescriptor mutable = new MapDescriptor(true);

	/**
	 * Answer the mutable {@link MapDescriptor}.
	 *
	 * @return The mutable {@link MapDescriptor}.
	 */
	public static MapDescriptor mutable ()
	{
		return mutable;
	}

	/**
	 * The immutable {@link MapDescriptor}.
	 */
	private final static MapDescriptor immutable = new MapDescriptor(false);

	/**
	 * Answer the immutable {@link MapDescriptor}.
	 *
	 * @return The immutable {@link MapDescriptor}.
	 */
	public static MapDescriptor immutable ()
	{
		return immutable;
	}
}
