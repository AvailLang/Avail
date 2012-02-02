/**
 * MapDescriptor.java
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

import static com.avail.descriptor.AvailObject.*;
import static com.avail.descriptor.TypeDescriptor.Types.*;
import java.util.*;
import com.avail.annotations.*;
import com.avail.serialization.SerializerOperation;

/**
 * I represent a discrete function whose keys and values are arbitrary Avail
 * objects.  My type depends on the union of the types of my keys, as well as
 * the union of the types of my values.
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 */
public class MapDescriptor
extends Descriptor
{
	/**
	 * The layout of integer slots for my instances.
	 */
	public enum IntegerSlots implements IntegerSlotsEnum
	{
		/**
		 * The basic hash value of the map, computed as some commutative
		 * combination of the hashes of the map's <key,value> pairs.  This basic
		 * hash is twiddled before being used as the map's formal hash, to
		 * reduce the propagation of bad hashes (e.g., 0 for the empty map), and
		 * to ensure various combinations of nested maps have uncorrelated
		 * hash values.
		 */
		INTERNAL_HASH,

		/**
		 * The number of valid <key,value> pairs in the map.
		 */
		MAP_SIZE,

		/**
		 * The number of key slots that currently contain a {@linkplain
		 * BlankDescriptor blank}.  Overwriting with blanks allows rehashing the
		 * map to be significantly postponed for a reasonable cost in space.
		 * The blanks disappear after the rehash.
		 */
		NUM_BLANKS
	}

	/**
	 * The layout of object slots for my instances.
	 */
	public enum ObjectSlots implements ObjectSlotsEnum
	{
		/**
		 * The raw data, organized as key1, value1, key2, value2, etc.  Hashing
		 * locates the key slot.  If it contains the expected key, great.  If
		 * it contains a {@linkplain BlankDescriptor blank}, continue the search
		 * with the next pair of slots, wrapping if necessary.  If it contains
		 * the {@linkplain NullDescriptor#nullObject() null object}, the key is
		 * not present.
		 */
		DATA_AT_INDEX_
	}

	@Override @AvailMethod
	@NotNull AvailObject o_DataAtIndex (
		final @NotNull AvailObject object,
		final int subscript)
	{
		return object.slot(ObjectSlots.DATA_AT_INDEX_, subscript);
	}

	@Override @AvailMethod
	void o_DataAtIndexPut (
		final @NotNull AvailObject object,
		final int subscript,
		final AvailObject value)
	{
		object.setSlot(ObjectSlots.DATA_AT_INDEX_, subscript, value);
	}

	@Override @AvailMethod
	void o_InternalHash (
		final @NotNull AvailObject object,
		final int value)
	{
		object.setSlot(IntegerSlots.INTERNAL_HASH, value);
	}

	@Override @AvailMethod
	void o_MapSize (
		final @NotNull AvailObject object,
		final int value)
	{
		object.setSlot(IntegerSlots.MAP_SIZE, value);
	}

	@Override @AvailMethod
	void o_NumBlanks (
		final @NotNull AvailObject object,
		final int value)
	{
		object.setSlot(IntegerSlots.NUM_BLANKS, value);
	}

	@Override @AvailMethod
	int o_InternalHash (
		final @NotNull AvailObject object)
	{
		return object.slot(IntegerSlots.INTERNAL_HASH);
	}

	@Override @AvailMethod
	int o_MapSize (
		final @NotNull AvailObject object)
	{
		return object.slot(IntegerSlots.MAP_SIZE);
	}

	@Override @AvailMethod
	int o_NumBlanks (
		final @NotNull AvailObject object)
	{
		return object.slot(IntegerSlots.NUM_BLANKS);
	}


	/**
	 * {@link MapDescriptor.Entry} exists solely to allow the "foreach" control
	 * structure to be used on a {@linkplain MapDescriptor map} by suitable use
	* of {@linkplain MapDescriptor#o_MapIterable(AvailObject) mapIterable()}.
	 *
	 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
	 */
	public static class Entry
	{
		/**
		 * The key at some {@link MapIterable}'s current position.
		 */
		public final AvailObject key;

		/**
		 * The value associated with the key at some {@link MapIterable}'s
		 * current position.
		 */
		public final AvailObject value;

		/**
		 * Construct a new {@link Entry}.
		 *
		 * @param key The key of the {@linkplain MapDescriptor map} entry.
		 * @param value The value of the {@linkplain MapDescriptor map} entry.
		 */
		Entry(final AvailObject key, final AvailObject value)
		{
			this.key = key;
			this.value = value;
		}
	}

	/**
	 * {@link MapDescriptor.MapIterable} is returned by {@linkplain
	* MapDescriptor#o_MapIterable(AvailObject) mapIterable()} to support use of
	 * the"foreach" control structure on {@linkplain MapDescriptor maps}.
	 *
	 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
	 */
	public static class MapIterable
	implements
		Iterator<Entry>,
		Iterable<Entry>
	{
		/**
		 * The {@linkplain MapDescriptor map} being iterated.
		 */
		private final AvailObject object;

		/**
		 * The subscript used by {@link
		* MapDescriptor#o_DataAtIndex(AvailObject, int) dataAtIndex(int)} to
		 * extract the current key.  The value associated with this key is at
		 * {@code dataAtIndex(dataSubscript + 1)}.
		 */
		private int dataSubscript;

		/**
		 * The size of the {@linkplain MapDescriptor map} being iterated over.
		 */
		private final int dataCapacity;

		/**
		 * Construct a new {@link MapIterable}.
		 *
		 * @param object The {@linkplain MapDescriptor map} to iterate over.
		 */
		MapIterable (final AvailObject object)
		{
			this.object = object;
			dataSubscript = -1;  // 1 - 2
			dataCapacity = object.capacity() * 2;
			advance();
		}

		/**
		 * Advance this iterator to the next position at which an actual key
		 * exists, or just beyond the end of the map.
		 */
		private void advance ()
		{
			do
			{
				dataSubscript += 2;
			}
			while (dataSubscript <= dataCapacity
				&& object.dataAtIndex(dataSubscript).equalsNullOrBlank());
		}

		@Override
		public void remove ()
		{
			throw new UnsupportedOperationException();
		}

		@Override
		public Entry next ()
		{
			// Recycle the same Entry repeatedly.
			assert hasNext();
			final AvailObject key = object.dataAtIndex(dataSubscript);
			final AvailObject value = object.dataAtIndex(dataSubscript + 1);
			assert !key.equalsNullOrBlank();
			advance();
			return new Entry(key, value);
		}

		@Override
		public boolean hasNext ()
		{
			return dataSubscript <= dataCapacity;
		}

		@Override
		public Iterator<Entry> iterator ()
		{
			// This is what Java *should* have provided all along.
			return this;
		}
	}

	@Override
	public void printObjectOnAvoidingIndent (
		final @NotNull AvailObject object,
		final StringBuilder aStream,
		final List<AvailObject> recursionList,
		final int indent)
	{
		final int size = object.mapSize();
		if (size == 0)
		{
			aStream.append("{}");
			return;
		}
		aStream.append('{');
		boolean first = true;
		for (final Entry entry : object.mapIterable())
		{
			if (!first)
			{
				aStream.append(", ");
			}
			entry.key.printOnAvoidingIndent(
				aStream,
				recursionList,
				indent + 1);
			aStream.append("→");
			entry.value.printOnAvoidingIndent(
				aStream,
				recursionList,
				indent + 1);
			first = false;
		}
		aStream.append('}');
	}

	@Override @AvailMethod
	boolean o_Equals (
		final @NotNull AvailObject object,
		final AvailObject another)
	{
		return another.equalsMap(object);
	}

	@Override @AvailMethod
	boolean o_EqualsMap (
		final @NotNull AvailObject object,
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
		for (int i = 1, end = object.capacity(); i <= end; i++)
		{
			final AvailObject keyObject = object.keyAtIndex(i);
			if (!keyObject.equalsNullOrBlank())
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

	@Override @AvailMethod
	boolean o_IsInstanceOfKind (
		final @NotNull AvailObject object,
		final AvailObject aTypeObject)
	{
		if (aTypeObject.equals(TOP.o()))
		{
			return true;
		}
		if (aTypeObject.equals(ANY.o()))
		{
			return true;
		}
		if (!aTypeObject.isMapType())
		{
			return false;
		}
		//  See if it's an acceptable size...
		final AvailObject size = IntegerDescriptor.fromInt(object.mapSize());
		if (!size.isInstanceOf(aTypeObject.sizeRange()))
		{
			return false;
		}
		//  map's size is out of range.
		final AvailObject keyTypeObject = aTypeObject.keyType();
		final AvailObject valueTypeObject = aTypeObject.valueType();
		AvailObject key;
		AvailObject value;
		for (int i = 1, end = object.capacity(); i <= end; i++)
		{
			key = object.keyAtIndex(i);
			if (!key.equalsNullOrBlank())
			{
				if (!key.isInstanceOf(keyTypeObject))
				{
					return false;
				}
				value = object.valueAtIndex(i);
				if (!value.equalsNullOrBlank()
						&& !value.isInstanceOf(valueTypeObject))
				{
					return false;
				}
			}
		}
		return true;
	}

	@Override @AvailMethod
	int o_Hash (
		final @NotNull AvailObject object)
	{
		//  Take the internal hash, and twiddle it (so nested maps won't cause unwanted correlation).

		return object.internalHash() + 0x1D79B13 ^ 0x1A9A22FE;
	}

	@Override @AvailMethod
	@NotNull AvailObject o_Kind (
		final @NotNull AvailObject object)
	{
		AvailObject keyType = BottomTypeDescriptor.bottom();
		AvailObject valueType = BottomTypeDescriptor.bottom();
		for (final Entry entry : object.mapIterable())
		{
			// TODO: [TLS] Need to visit and fix all noninstanceType() sends --
			// including this one!
			keyType = keyType.typeUnion(entry.key.kind());
			valueType = valueType.typeUnion(entry.value.kind());
		}
		return MapTypeDescriptor.mapTypeForSizesKeyTypeValueType(
			IntegerDescriptor.fromInt(object.mapSize()).kind(),
			keyType,
			valueType);
	}

	@Override @AvailMethod
	boolean o_HasKey (
		final @NotNull AvailObject object,
		final AvailObject keyObject)
	{
		//  Answer whether the map has the given key.  Note that we don't stop searching
		//  when we reach a blank, only when we reach top or the target object.

		final int modulus = object.capacity();
		int h = (int)((keyObject.hash() & 0xFFFFFFFFL) % modulus + 1);
		while (true)
		{
			final AvailObject slotObject = object.keyAtIndex(h);
			if (slotObject.equalsNull())
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

	@Override @AvailMethod
	@NotNull AvailObject o_MapAt (
		final @NotNull AvailObject object,
		final AvailObject keyObject)
	{
		//  Answer the value of the map at the specified key.  Fail if the key is not present.

		final int modulus = object.capacity();
		int h = (int)((keyObject.hash() & 0xFFFFFFFFL) % modulus + 1);
		while (true)
		{
			final AvailObject slotObject = object.keyAtIndex(h);
			if (slotObject.equalsNull())
			{
				error("Key not found in map", object);
				return NullDescriptor.nullObject();
			}
			if (slotObject.equals(keyObject))
			{
				return object.valueAtIndex(h);
			}
			h = h == modulus ? 1 : h + 1;
		}
	}

	@Override @AvailMethod
	@NotNull AvailObject o_MapAtPuttingCanDestroy (
		final @NotNull AvailObject object,
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
//		canAllocateObjects(false);
		for (final Entry entry : object.mapIterable())
		{
			result.privateMapAtPut(entry.key, entry.value);
		}
		result.privateMapAtPut(keyObject, newValueObject);
//		canAllocateObjects(true);
		return result;
	}

	@Override @AvailMethod
	@NotNull AvailObject o_MapWithoutKeyCanDestroy (
		final @NotNull AvailObject object,
		final AvailObject keyObject,
		final boolean canDestroy)
	{
		// Answer a map like this one but with keyObject removed from it.  The
		// original map can be destroyed if canDestroy is true and it's mutable.

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
		final AvailObject result =
			MapDescriptor.newWithCapacity(object.capacity());
//		canAllocateObjects(false);
		for (final Entry entry : object.mapIterable())
		{
			if (!entry.key.equals(keyObject))
			{
				result.privateMapAtPut(entry.key, entry.value);
			}
		}
//		canAllocateObjects(true);
		return result;
	}

	@Override @AvailMethod
	@NotNull AvailObject o_AsObject (
		final @NotNull AvailObject object)
	{
		//  Convert the receiver into an object.

		return ObjectDescriptor.objectFromMap(object);
	}

	@Override @AvailMethod
	int o_Capacity (
		final @NotNull AvailObject object)
	{
		//  Answer the total number of slots reserved for holding keys.

		return object.variableObjectSlotsCount() >>> 1;
	}

	@Override @AvailMethod
	boolean o_IsMap (
		final @NotNull AvailObject object)
	{
		return true;
	}

	@Override @AvailMethod
	@NotNull AvailObject o_KeysAsSet (
		final @NotNull AvailObject object)
	{
		//  Answer a set with all my keys.  Mark the keys as immutable because they'll be shared with the new set.

//		AvailObject.lock(object);
		AvailObject result = SetDescriptor.empty();
		for (int i = 1, end = object.capacity(); i <= end; i++)
		{
			final AvailObject eachKeyObject = object.keyAtIndex(i);
			if (!eachKeyObject.equalsNullOrBlank())
			{
				result = result.setWithElementCanDestroy(eachKeyObject.makeImmutable(), true);
			}
		}
//		AvailObject.unlock(object);
		return result;
	}

	/**
	 * Answer a tuple with all my values.  Mark the values as immutable because
	 * they'll be shared with the new tuple.
	 */
	@Override @AvailMethod
	@NotNull AvailObject o_ValuesAsTuple (
		final @NotNull AvailObject object)
	{
		final AvailObject result = ObjectTupleDescriptor.mutable().create(
			object.mapSize());
//		AvailObject.lock(result);
//		canAllocateObjects(false);
		for (int i = 1, end = object.mapSize(); i <= end; i++)
		{
			result.tupleAtPut(i, NullDescriptor.nullObject());
		}
		result.hashOrZero(0);
		int targetIndex = 1;
		for (final Entry entry : object.mapIterable())
		{
			entry.value.makeImmutable();
			result.tupleAtPut(targetIndex, entry.value);
			targetIndex++;
		}
		assert targetIndex == object.mapSize() + 1;
//		canAllocateObjects(true);
//		AvailObject.unlock(result);
		return result;
	}

	@Override @AvailMethod
	@NotNull AvailObject o_KeyAtIndex (
		final @NotNull AvailObject object,
		final int index)
	{
		//  Answer the map's indexth key.

		return object.dataAtIndex(index * 2 - 1);
	}

	@Override @AvailMethod
	void o_KeyAtIndexPut (
		final @NotNull AvailObject object,
		final int index,
		final AvailObject keyObject)
	{
		//  Set the map's indexth key.

		object.dataAtIndexPut(index * 2 - 1, keyObject);
	}

	@Override @AvailMethod
	@NotNull AvailObject o_PrivateExcludeKey (
		final @NotNull AvailObject object,
		final AvailObject keyObject)
	{
		//  Remove keyObject from the map's keys if it's present.  The map must be mutable.
		//  Also, computing the key's hash value should not cause an allocation.

		assert !keyObject.equalsNullOrBlank() & isMutable;
		final int h0 = keyObject.hash();
		final int modulus = object.capacity();
		int probe = (int)((h0 & 0xFFFFFFFFL) % modulus + 1);
//		AvailObject.lock(object);
		while (true)
		{
			final AvailObject slotValue = object.keyAtIndex(probe);
			if (slotValue.equalsNull())
			{
//				AvailObject.unlock(object);
				return object;
			}
			if (slotValue.equals(keyObject))
			{
				object.internalHash(
					object.internalHash()
					^ h0 + object.valueAtIndex(probe).hash() * 23);
				object.keyAtIndexPut(probe, BlankDescriptor.blank());
				object.valueAtIndexPut(probe, NullDescriptor.nullObject());
				object.mapSize(object.mapSize() - 1);
				object.numBlanks(object.numBlanks() + 1);
//				AvailObject.unlock(object);
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

	@Override @AvailMethod
	@NotNull AvailObject o_PrivateMapAtPut (
		final @NotNull AvailObject object,
		final AvailObject keyObject,
		final AvailObject valueObject)
	{
		//  Make keyObject go to valueObject in the map.  The object must be mutable and have
		//  room for the new element.  Also, computing the key's hash value should not cause
		//  an allocation.

		assert !keyObject.equalsNullOrBlank() & isMutable;
		assert (object.mapSize() + object.numBlanks()) * 4 <= object.capacity() * 3;
		final int h0 = keyObject.hash();
		final int modulus = object.capacity();
		int probe = (int)((h0 & 0xFFFFFFFFL) % modulus + 1);
//		AvailObject.lock(object);
		int tempHash;
		while (true)
		{
			final AvailObject slotValue = object.keyAtIndex(probe);
			if (slotValue.equals(keyObject))
			{
				tempHash = object.internalHash()
				^ h0 + object.valueAtIndex(probe).hash() * 23;
				tempHash ^= h0 + valueObject.hash() * 23;
				object.internalHash(tempHash);
				object.valueAtIndexPut(probe, valueObject);
//				AvailObject.unlock(object);
				return object;
			}
			if (slotValue.equalsNullOrBlank())
			{
				object.keyAtIndexPut(probe, keyObject);
				object.valueAtIndexPut(probe, valueObject);
				object.mapSize(object.mapSize() + 1);
				object.internalHash(
					object.internalHash() ^ h0 + valueObject.hash() * 23);
				if (slotValue.equalsBlank())
				{
					object.numBlanks(object.numBlanks() - 1);
				}
//				AvailObject.unlock(object);
				return object;
			}
			probe = probe == modulus ? 1 : probe + 1;
		}
	}

	@Override @AvailMethod
	@NotNull AvailObject o_ValueAtIndex (
		final @NotNull AvailObject object,
		final int index)
	{
		//  Answer the map's indexth value.

		return object.dataAtIndex(index * 2);
	}

	@Override @AvailMethod
	void o_ValueAtIndexPut (
		final @NotNull AvailObject object,
		final int index,
		final AvailObject valueObject)
	{
		//  Set the map's indexth value.

		object.dataAtIndexPut(index * 2, valueObject);
	}

	@Override @AvailMethod
	List<AvailObject> o_KeysAsArray (
		final @NotNull AvailObject object)
	{
//		AvailObject.lock(object);
		List<AvailObject> result;
		result = new ArrayList<AvailObject>(object.mapSize());
		for (int i = 1, end = object.capacity(); i <= end; i++)
		{
			final AvailObject eachKeyObject = object.keyAtIndex(i);
			if (!eachKeyObject.equalsNullOrBlank())
			{
				result.add(eachKeyObject.makeImmutable());
			}
		}
		assert result.size() == object.mapSize();
//		AvailObject.unlock(object);
		return result;
	}

	@Override @AvailMethod
	MapDescriptor.MapIterable o_MapIterable (
		final @NotNull AvailObject object)
	{
		return new MapIterable(object);
	}

	@Override
	@AvailMethod @ThreadSafe
	@NotNull SerializerOperation o_SerializerOperation (
		final @NotNull AvailObject object)
	{
		return SerializerOperation.MAP;
	}


	/**
	 * An immutable empty map.
	 */
	static AvailObject emptyMap;

	/**
	 * Initialize my emptyMap static field.
	 */
	static void createWellKnownObjects ()
	{
		emptyMap = newWithCapacity(3);
		emptyMap.makeImmutable();
	}

	/**
	 * Clear my emptyMap static field.
	 */
	static void clearWellKnownObjects ()
	{
		emptyMap = null;
	}

	/**
	 * Return the (immutable) empty map.
	 *
	 * @return An empty, immutable map.
	 */
	public static AvailObject empty ()
	{
		return emptyMap;
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
	public static @NotNull AvailObject newWithCapacity (final int capacity)
	{
		final AvailObject result = mutable().create(capacity * 2);
		result.internalHash(0);
		result.mapSize(0);
		result.numBlanks(0);
		for (int i = 1; i <= capacity * 2; i++)
		{
			result.dataAtIndexPut(i, NullDescriptor.nullObject());
		}
		return result;
	}

	/**
	 * Create a new {@linkplain MapDescriptor map} whose contents correspond to
	 * the specified {@linkplain TupleDescriptor tuple} of key-value bindings.
	 *
	 * @param tupleOfBindings
	 *        A tuple of key-value bindings, i.e. 2-element tuples.
	 * @return A new map.
	 */
	public static @NotNull AvailObject newWithBindings (
		final @NotNull AvailObject tupleOfBindings)
	{
		assert tupleOfBindings.isTuple();
		// The adjustment supports empty maps.
		AvailObject newMap = newWithCapacity(
			tupleOfBindings.tupleSize() * 2 + 1);
		 for (final AvailObject binding : tupleOfBindings)
		 {
			 assert binding.isTuple();
			 assert binding.tupleSize() == 2;
			 newMap = newMap.mapAtPuttingCanDestroy(
				 binding.tupleAt(1),
				 binding.tupleAt(2),
				 true);
		 }
		 return newMap;
	}

	/**
	 * Combine the two {@linkplain MapDescriptor maps} into a single map,
	 * destroying the destination if possible and appropriate.
	 *
	 * @param destination
	 *        The destination map.
	 * @param source
	 *        The source map.
	 * @param canDestroy
	 *        {@code true} if the operation is permitted to modify the
	 *        destination map in situ (if it is mutable), {@code false}
	 *        otherwise.
	 * @return The resultant map.
	 */
	public static @NotNull AvailObject combineMapsCanDestroy (
		final @NotNull AvailObject destination,
		final @NotNull AvailObject source,
		final boolean canDestroy)
	{
		assert destination.isMap();
		assert source.isMap();

		AvailObject target =
			canDestroy && destination.descriptor.isMutable()
			? destination
			: empty();
		if (target != destination)
		{
			for (final AvailObject key : destination.keysAsSet())
			{
				target = target.mapAtPuttingCanDestroy(
					key, destination.mapAt(key), true);
			}
		}
		for (final AvailObject key : source.keysAsSet())
		{
			target = target.mapAtPuttingCanDestroy(
				key, source.mapAt(key), canDestroy);
		}
		return target;
	}

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
	private static final MapDescriptor mutable = new MapDescriptor(true);

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
	private static final MapDescriptor immutable = new MapDescriptor(false);

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
