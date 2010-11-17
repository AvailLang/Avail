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

import static com.avail.descriptor.AvailObject.CanAllocateObjects;
import static com.avail.descriptor.AvailObject.error;
import java.util.ArrayList;
import java.util.List;

import com.avail.descriptor.TypeDescriptor.Types;

@IntegerSlots({
	"internalHash",
	"mapSize",
	"numBlanks"
})
@ObjectSlots("dataAtIndex#")
public class MapDescriptor extends Descriptor
{
	// GENERATED accessors

	@Override
	AvailObject ObjectDataAtIndex (
		final AvailObject object,
		final int index)
	{
		//  GENERATED getter method (indexed).

		return object.objectSlotAtByteIndex(((index * -4) + 0));
	}

	@Override
	void ObjectDataAtIndexPut (
		final AvailObject object,
		final int index,
		final AvailObject value)
	{
		//  GENERATED setter method (indexed).

		object.objectSlotAtByteIndexPut(((index * -4) + 0), value);
	}

	@Override
	void ObjectInternalHash (
		final AvailObject object,
		final int value)
	{
		//  GENERATED setter method.

		object.integerSlotAtByteIndexPut(4, value);
	}

	@Override
	void ObjectMapSize (
		final AvailObject object,
		final int value)
	{
		//  GENERATED setter method.

		object.integerSlotAtByteIndexPut(8, value);
	}

	@Override
	void ObjectNumBlanks (
		final AvailObject object,
		final int value)
	{
		//  GENERATED setter method.

		object.integerSlotAtByteIndexPut(12, value);
	}

	@Override
	int ObjectInternalHash (
		final AvailObject object)
	{
		//  GENERATED getter method.

		return object.integerSlotAtByteIndex(4);
	}

	@Override
	int ObjectMapSize (
		final AvailObject object)
	{
		//  GENERATED getter method.

		return object.integerSlotAtByteIndex(8);
	}

	@Override
	int ObjectNumBlanks (
		final AvailObject object)
	{
		//  GENERATED getter method.

		return object.integerSlotAtByteIndex(12);
	}



	// java printing

	@Override
	void printObjectOnAvoidingIndent (
		final AvailObject object,
		final StringBuilder aStream,
		final List<AvailObject> recursionList,
		final int indent)
	{
		int size = object.mapSize();
		if (size == 0)
		{
			aStream.append("[->]");
			return;
		}
		boolean printedAny = false;
		for (int i = 1, limit = object.capacity(); i <= limit; i++)
		{
			AvailObject key = object.keyAtIndex(i);
			if (! key.equalsVoidOrBlank())
			{
				if (size > 3)
				{
					if (printedAny)
					{
						aStream.append("\n");
						for (int t = 1; t <= indent; t++)
						{
							aStream.append("\t");
						}
					}
					printedAny = true;
				}
				AvailObject value = object.valueAtIndex(i);
				aStream.append('[');
				key.printOnAvoidingIndent(aStream, recursionList, indent + 1);
				aStream.append("->");
				value.printOnAvoidingIndent(aStream, recursionList, indent + 1);
				aStream.append(']');
			}
		}
	}



	// operations

	@Override
	boolean ObjectEquals (
		final AvailObject object,
		final AvailObject another)
	{
		return another.equalsMap(object);
	}

	@Override
	boolean ObjectEqualsMap (
		final AvailObject object,
		final AvailObject aMap)
	{
		if (! (object.internalHash() == aMap.internalHash()))
		{
			return false;
		}
		if (! (object.mapSize() == aMap.mapSize()))
		{
			return false;
		}
		for (int i = 1, _end1 = object.capacity(); i <= _end1; i++)
		{
			final AvailObject keyObject = object.keyAtIndex(i);
			if (! keyObject.equalsVoidOrBlank())
			{
				if (! aMap.hasKey(keyObject))
				{
					return false;
				}
				if (! aMap.mapAt(keyObject).equals(object.valueAtIndex(i)))
				{
					return false;
				}
			}
		}
		return true;
	}

	@Override
	boolean ObjectIsInstanceOfSubtypeOf (
		final AvailObject object,
		final AvailObject aTypeObject)
	{
		//  Answer whether object is an instance of a subtype of aTypeObject.  Don't generate
		//  an approximate type and do the comparison, because the approximate type
		//  will just send this message recursively.

		if (aTypeObject.equals(Types.voidType.object()))
		{
			return true;
		}
		if (aTypeObject.equals(Types.all.object()))
		{
			return true;
		}
		if (! aTypeObject.isMapType())
		{
			return false;
		}
		//  See if it's an acceptable size...
		final AvailObject size = IntegerDescriptor.objectFromInt(object.mapSize());
		if (! size.isInstanceOfSubtypeOf(aTypeObject.sizeRange()))
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
			if (! key.equalsVoidOrBlank())
			{
				if (! key.isInstanceOfSubtypeOf(keyTypeObject))
				{
					return false;
				}
				value = object.valueAtIndex(i);
				if (! (value.equalsVoidOrBlank() || value.isInstanceOfSubtypeOf(valueTypeObject)))
				{
					return false;
				}
			}
		}
		return true;
	}

	@Override
	AvailObject ObjectExactType (
		final AvailObject object)
	{
		//  Answer the object's type.

		AvailObject keyType = Types.terminates.object();
		AvailObject valueType = Types.terminates.object();
		for (int i = 1, _end1 = object.capacity(); i <= _end1; i++)
		{
			final AvailObject keyObject = object.keyAtIndex(i);
			if (! keyObject.equalsVoidOrBlank())
			{
				keyType = keyType.typeUnion(keyObject.type());
				valueType = valueType.typeUnion(object.valueAtIndex(i).type());
			}
		}
		return MapTypeDescriptor.mapTypeForSizesKeyTypeValueType(
			IntegerDescriptor.objectFromInt(object.mapSize()).type(),
			keyType,
			valueType);
	}

	@Override
	int ObjectHash (
		final AvailObject object)
	{
		//  Take the internal hash, and twiddle it (so nested maps won't cause unwanted correlation).

		return (((object.internalHash() + 0x1D79B13) ^ 0x1A9A22FE) & HashMask);
	}

	@Override
	boolean ObjectIsHashAvailable (
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
			if (! object.keyAtIndex(i).isHashAvailable())
			{
				return false;
			}
			if (! object.valueAtIndex(i).isHashAvailable())
			{
				return false;
			}
		}
		return true;
	}

	@Override
	AvailObject ObjectType (
		final AvailObject object)
	{
		//  Answer the object's type.

		return ApproximateTypeDescriptor.withInstance(object.makeImmutable());
	}



	// operations-map

	@Override
	boolean ObjectHasKey (
		final AvailObject object,
		final AvailObject keyObject)
	{
		//  Answer whether the map has the given key.  Note that we don't stop searching
		//  when we reach a blank, only when we reach void or the target object.

		final int modulus = object.capacity();
		int h = (int)(((keyObject.hash() & 0xFFFFFFFFL) % modulus) + 1);
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
			h = ((h == modulus) ? 1 : (h + 1));
		}
	}

	@Override
	AvailObject ObjectMapAt (
		final AvailObject object,
		final AvailObject keyObject)
	{
		//  Answer the value of the map at the specified key.  Fail if the key is not present.

		final int modulus = object.capacity();
		int h = (int)(((keyObject.hash() & 0xFFFFFFFFL) % modulus) + 1);
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
			h = ((h == modulus) ? 1 : (h + 1));
		}
	}

	@Override
	AvailObject ObjectMapAtPuttingCanDestroy (
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
		final int neededCapacity = (((((object.mapSize() + 1) + object.numBlanks()) * 4) / 3) + 1);
		if ((canDestroy && (isMutable && (object.hasKey(keyObject) || (object.capacity() >= neededCapacity)))))
		{
			return object.privateMapAtPut(keyObject, newValueObject);
		}
		final AvailObject result = MapDescriptor.newWithCapacity(((object.mapSize() * 2) + 5));
		//  Start new map just over 50% free (with no blanks).
		CanAllocateObjects(false);
		for (int i = 1, _end1 = object.capacity(); i <= _end1; i++)
		{
			final AvailObject eachKeyObject = object.keyAtIndex(i);
			if (! eachKeyObject.equalsVoidOrBlank())
			{
				result.privateMapAtPut(eachKeyObject, object.valueAtIndex(i));
			}
		}
		result.privateMapAtPut(keyObject, newValueObject);
		CanAllocateObjects(true);
		return result;
	}

	@Override
	AvailObject ObjectMapWithoutKeyCanDestroy (
		final AvailObject object,
		final AvailObject keyObject,
		final boolean canDestroy)
	{
		//  Answer a map like this one but with keyObject removed from it.
		//  The original map can be destroyed if canDestroy is true and it's mutable.

		if (! object.hasKey(keyObject))
		{
			if (! canDestroy)
			{
				object.makeImmutable();
			}
			//  Existing reference will be kept around.
			return object;
		}
		if ((canDestroy && isMutable))
		{
			return object.privateExcludeKey(keyObject);
		}
		final AvailObject result = MapDescriptor.newWithCapacity(object.capacity());
		CanAllocateObjects(false);
		for (int i = 1, _end1 = object.capacity(); i <= _end1; i++)
		{
			final AvailObject eachKeyObject = object.keyAtIndex(i);
			if (! eachKeyObject.equalsVoidOrBlank())
			{
				if (! eachKeyObject.equals(keyObject))
				{
					result.privateMapAtPut(eachKeyObject, object.valueAtIndex(i));
				}
			}
		}
		CanAllocateObjects(true);
		return result;
	}

	@Override
	AvailObject ObjectAsObject (
		final AvailObject object)
	{
		//  Convert the receiver into an object.

		return ObjectDescriptor.objectFromMap(object);
	}

	@Override
	int ObjectCapacity (
		final AvailObject object)
	{
		//  Answer the total number of slots reserved for holding keys.

		return ((object.objectSlotsCount() - numberOfFixedObjectSlots()) >>> 1);
	}

	@Override
	boolean ObjectIsMap (
		final AvailObject object)
	{
		return true;
	}

	@Override
	AvailObject ObjectKeysAsSet (
		final AvailObject object)
	{
		//  Answer a set with all my keys.  Mark the keys as immutable because they'll be shared with the new set.

		AvailObject.lock(object);
		AvailObject result = SetDescriptor.empty();
		for (int i = 1, _end1 = object.capacity(); i <= _end1; i++)
		{
			final AvailObject eachKeyObject = object.keyAtIndex(i);
			if (! eachKeyObject.equalsVoidOrBlank())
			{
				result = result.setWithElementCanDestroy(eachKeyObject.makeImmutable(), true);
			}
		}
		AvailObject.unlock(object);
		return result;
	}

	@Override
	AvailObject ObjectValuesAsTuple (
		final AvailObject object)
	{
		//  Answer a tuple with all my values.  Mark the values as immutable because they'll be shared with the new
		//  tuple.

		final AvailObject result = AvailObject.newIndexedDescriptor(object.size(), ObjectTupleDescriptor.mutableDescriptor());
		AvailObject.lock(result);
		CanAllocateObjects(false);
		for (int i = 1, _end1 = object.size(); i <= _end1; i++)
		{
			result.tupleAtPut(i, VoidDescriptor.voidObject());
		}
		result.hashOrZero(0);
		int targetIndex = 1;
		for (int i = 1, _end2 = object.capacity(); i <= _end2; i++)
		{
			final AvailObject eachKeyObject = object.keyAtIndex(i);
			if (! eachKeyObject.equalsVoidOrBlank())
			{
				result.tupleAtPut(targetIndex, object.valueAtIndex(i).makeImmutable());
				++targetIndex;
			}
		}
		assert (targetIndex == (object.size() + 1));
		CanAllocateObjects(true);
		AvailObject.unlock(result);
		return result;
	}



	// operations-private

	@Override
	AvailObject ObjectKeyAtIndex (
		final AvailObject object,
		final int index)
	{
		//  Answer the map's indexth key.

		return object.dataAtIndex(((index + index) - 1));
	}

	@Override
	void ObjectKeyAtIndexPut (
		final AvailObject object,
		final int index,
		final AvailObject keyObject)
	{
		//  Set the map's indexth key.

		object.dataAtIndexPut(((index * 2) - 1), keyObject);
	}

	@Override
	AvailObject ObjectPrivateExcludeKey (
		final AvailObject object,
		final AvailObject keyObject)
	{
		//  Remove keyObject from the map's keys if it's present.  The map must be mutable.
		//  Also, computing the key's hash value should not cause an allocation.

		assert ((keyObject.isHashAvailable() & (! keyObject.equalsVoidOrBlank())) & isMutable);
		final int h0 = keyObject.hash();
		final int modulus = object.capacity();
		int probe = (int)(((h0 & 0xFFFFFFFFL) % modulus) + 1);
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
				object.internalHash(((object.internalHash() ^ (h0 + (object.valueAtIndex(probe).hash() * 23))) & HashMask));
				object.keyAtIndexPut(probe, BlankDescriptor.blank());
				object.valueAtIndexPut(probe, VoidDescriptor.voidObject());
				object.mapSize((object.mapSize() - 1));
				object.numBlanks((object.numBlanks() + 1));
				AvailObject.unlock(object);
				return object;
			}
			if ((probe == modulus))
			{
				probe = 1;
			}
			else
			{
				++probe;
			}
		}
	}

	@Override
	AvailObject ObjectPrivateMapAtPut (
		final AvailObject object,
		final AvailObject keyObject,
		final AvailObject valueObject)
	{
		//  Make keyObject go to valueObject in the map.  The object must be mutable and have
		//  room for the new element.  Also, computing the key's hash value should not cause
		//  an allocation.

		assert ((keyObject.isHashAvailable() & (! keyObject.equalsVoidOrBlank())) & isMutable);
		assert (((object.mapSize() + object.numBlanks()) * 4) <= (object.capacity() * 3));
		final int h0 = keyObject.hash();
		final int modulus = object.capacity();
		int probe = (int)(((h0 & 0xFFFFFFFFL) % modulus) + 1);
		AvailObject.lock(object);
		int tempHash;
		while (true) {
			final AvailObject slotValue = object.keyAtIndex(probe);
			if (slotValue.equals(keyObject))
			{
				tempHash = (object.internalHash() ^ (h0 + (object.valueAtIndex(probe).hash() * 23)));
				tempHash ^= (h0 + (valueObject.hash() * 23));
				object.internalHash((tempHash & HashMask));
				object.valueAtIndexPut(probe, valueObject);
				AvailObject.unlock(object);
				return object;
			}
			if (slotValue.equalsVoidOrBlank())
			{
				object.keyAtIndexPut(probe, keyObject);
				object.valueAtIndexPut(probe, valueObject);
				object.mapSize((object.mapSize() + 1));
				object.internalHash(((object.internalHash() ^ (h0 + (valueObject.hash() * 23))) & HashMask));
				if (slotValue.equalsBlank())
				{
					object.numBlanks((object.numBlanks() - 1));
				}
				AvailObject.unlock(object);
				return object;
			}
			probe = ((probe == modulus) ? 1 : (probe + 1));
		}
	}

	@Override
	AvailObject ObjectValueAtIndex (
		final AvailObject object,
		final int index)
	{
		//  Answer the map's indexth value.

		return object.dataAtIndex((index * 2));
	}

	@Override
	void ObjectValueAtIndexPut (
		final AvailObject object,
		final int index,
		final AvailObject valueObject)
	{
		//  Set the map's indexth value.

		object.dataAtIndexPut((index * 2), valueObject);
	}



	// private-copying

	@Override
	List<AvailObject> ObjectKeysAsArray (
		final AvailObject object)
		{
		//  Utility method - collect the object's keys into a Smalltalk Array.

		AvailObject.lock(object);
		List<AvailObject> result;
		result = new ArrayList<AvailObject>(object.mapSize());
		for (int i = 1, _end1 = object.capacity(); i <= _end1; i++)
		{
			final AvailObject eachKeyObject = object.keyAtIndex(i);
			if (! eachKeyObject.equalsVoidOrBlank())
			{
				result.add(eachKeyObject.makeImmutable());
			}
		}
		assert (result.size() == object.mapSize());
		AvailObject.unlock(object);
		return result;
		}




	// Startup/shutdown

	static AvailObject EmptyMap;

	static void createWellKnownObjects ()
	{
		//  Initialize my EmptyMap class variable in addition to the usual classInstVars.

		EmptyMap = newWithCapacity(3);
		EmptyMap.makeImmutable();
	}

	static void clearWellKnownObjects ()
	{
		//  Initialize my EmptyMap class variable in addition to the usual classInstVars.

		EmptyMap = null;
	}



	/* Object creation */
	public static AvailObject newWithCapacity (int capacity)
	{
		AvailObject result = AvailObject.newIndexedDescriptor(capacity * 2, MapDescriptor.mutableDescriptor());
		result.internalHash(0);
		result.mapSize(0);
		result.numBlanks(0);
		for (int i = 1; i <= capacity * 2; i++)
		{
			result.dataAtIndexPut(i, VoidDescriptor.voidObject());
		};
		return result;
	};

	public static AvailObject empty ()
	{
		return EmptyMap;
	};

	/**
	 * Construct a new {@link MapDescriptor}.
	 *
	 * @param myId The id of the {@linkplain Descriptor descriptor}.
	 * @param isMutable
	 *        Does the {@linkplain Descriptor descriptor} represent a mutable
	 *        object?
	 * @param numberOfFixedObjectSlots
	 *        The number of fixed {@linkplain AvailObject object} slots.
	 * @param numberOfFixedIntegerSlots The number of fixed integer slots.
	 * @param hasVariableObjectSlots
	 *        Does an {@linkplain AvailObject object} using this {@linkplain
	 *        Descriptor} have any variable object slots?
	 * @param hasVariableIntegerSlots
	 *        Does an {@linkplain AvailObject object} using this {@linkplain
	 *        Descriptor} have any variable integer slots?
	 */
	protected MapDescriptor (
		final int myId,
		final boolean isMutable,
		final int numberOfFixedObjectSlots,
		final int numberOfFixedIntegerSlots,
		final boolean hasVariableObjectSlots,
		final boolean hasVariableIntegerSlots)
	{
		super(
			myId,
			isMutable,
			numberOfFixedObjectSlots,
			numberOfFixedIntegerSlots,
			hasVariableObjectSlots,
			hasVariableIntegerSlots);
	}
	
	public static MapDescriptor mutableDescriptor()
	{
		return (MapDescriptor) allDescriptors [104];
	}
	
	public static MapDescriptor immutableDescriptor()
	{
		return (MapDescriptor) allDescriptors [105];
	}
}
