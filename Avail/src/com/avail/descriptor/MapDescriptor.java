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

import static com.avail.descriptor.TypeDescriptor.Types.*;
import java.util.*;
import com.avail.annotations.*;
import com.avail.exceptions.MapException;
import com.avail.exceptions.AvailErrorCode;
import com.avail.serialization.SerializerOperation;

/**
 * An Avail {@linkplain MapDescriptor map} refers to the root of a Bagwell
 * Ideal Hash Tree.  The implementation is similar to that of {@linkplain
 * SetDescriptor sets}, but using map-specific bin descriptors instead of the
 * set-specific ones.
 *
 * <p>
 * Unlike the optimization for {@linkplain SetDescriptor sets} in which a
 * singleton set has the element itself as the root bin (since bins likewise are
 * not manipulated by Avail programs), that optimization is not available for
 * maps.  That's because a singleton map records both a key and a value.  Thus,
 * a map bin is allowed to be so small that it can contain one key and value.
 * In fact, there is even a single size zero linear map bin for use as the root
 * of the empty map.
 * </p>
 *
 * <p>
 * The presence of singular bins affects maps of all scales, due to the
 * recursive nature of the hash tree of bins, many of which contain sub-bins.
 * Since a sub-bin of size one for a set is just the element itself, small bins
 * lead to more expensive in space for maps than for sets.  To compensate for
 * this, maps are allowed to have larger linear bins before replacing them with
 * their hashed equivalents.
 * </p>
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 */
public class MapDescriptor extends Descriptor
{
	/**
	 * The layout of object slots for my instances.
	 */
	public enum ObjectSlots implements ObjectSlotsEnum
	{
		/**
		 * The topmost bin of this {@linkplain MapDescriptor map}.  Unlike the
		 * implementation for {@linkplain SetDescriptor sets}, all maps contain
		 * an actual map bin in this slot.
		 */
		ROOT_BIN
	}

	/**
	 * Extract the root {@linkplain MapBinDescriptor bin} from the {@linkplain
	 * MapDescriptor map}.
	 *
	 * @param object The map from which to extract the root bin.
	 * @return The map's bin.
	 */
	private static AvailObject rootBin (
		final AvailObject object)
	{
		return object.slot(ObjectSlots.ROOT_BIN);
	}

	/**
	 * Replace the {@linkplain MapDescriptor map}'s root {@linkplain
	 * MapBinDescriptor bin}.  The replacement may be the {@link
	 * NullDescriptor#nullObject() null object} to indicate an empty map.
	 *
	 * @param object
	 *            The map (must not be an indirection).
	 * @param bin
	 *            The root bin for the map, or the null object.
	 */
	private static void rootBin (
		final AvailObject object,
		final AvailObject bin)
	{
		object.setSlot(ObjectSlots.ROOT_BIN, bin);
	}

	@Override
	public void printObjectOnAvoidingIndent (
		final AvailObject object,
		final StringBuilder aStream,
		final List<AvailObject> recursionList,
		final int indent)
	{
		boolean multiline = false;
		aStream.append('{');
		final int startPosition = aStream.length();
		boolean first = true;
		for (final MapDescriptor.Entry entry : object.mapIterable())
		{
			aStream.append(first ? "" : ", ");
			final int entryStart = aStream.length();
			entry.key.printOnAvoidingIndent(
				aStream, recursionList, indent + 2);
			aStream.append("→");
			entry.value.printOnAvoidingIndent(
				aStream, recursionList, indent + 1);
			if (aStream.length() - startPosition > 100
				|| aStream.indexOf("\n", entryStart) != -1)
			{
				// Start over with multiple line formatting.
				aStream.setLength(startPosition);
				multiline = true;
				break;
			}
			first = false;
		}
		if (multiline)
		{
			first = true;
			for (final MapDescriptor.Entry entry : object.mapIterable())
			{
				aStream.append(first ? "\n" : ",\n");
				for (int i = indent; i > 0; i--)
				{
					aStream.append('\t');
				}
				final int entryStart = aStream.length();
				entry.key.printOnAvoidingIndent(
					aStream, recursionList, indent + 2);
				if (aStream.indexOf("\n", entryStart) != -1)
				{
					aStream.append("\n");
					for (int i = indent + 1; i > 0; i--)
					{
						aStream.append('\t');
					}
				}
				aStream.append("→");
				entry.value.printOnAvoidingIndent(
					aStream, recursionList, indent + 1);
				first = false;
			}
			aStream.append("\n");
			for (int i = indent; i > 0; i--)
			{
				aStream.append('\t');
			}
		}
		aStream.append('}');
	}

	@Override @AvailMethod
	boolean o_Equals (
		final AvailObject object,
		final AvailObject another)
	{
		return another.equalsMap(object);
	}

	@Override @AvailMethod
	boolean o_EqualsMap (
		final AvailObject object,
		final AvailObject aMap)
	{
		if (object.sameAddressAs(aMap))
		{
			return true;
		}
		if (object.mapSize() != aMap.mapSize())
		{
			return false;
		}
		if (object.hash() != aMap.hash())
		{
			return false;
		}
		final AvailObject aMapRootBin = rootBin(aMap);
		for (final MapDescriptor.Entry entry : object.mapIterable())
		{
			final AvailObject actualValue = aMapRootBin.mapBinAtHash(
				entry.key,
				entry.keyHash);
			if (!entry.value.equals(actualValue))
			{
				return false;
			}
		}
		// They're equal (but occupy disjoint storage).  Replace one with an
		// indirection to the other to reduce storage costs and the frequency
		// of entry-wise comparisons.
		object.becomeIndirectionTo(aMap);
		// Mark as immutable, now that there are at least two references to it.
		aMap.makeImmutable();
		return true;
	}

	@Override @AvailMethod
	boolean o_IsInstanceOfKind (
		final AvailObject object,
		final AvailObject aTypeObject)
	{
		if (aTypeObject.isSupertypeOfPrimitiveTypeEnum(ANY))
		{
			return true;
		}
		if (!aTypeObject.isMapType())
		{
			return false;
		}
		if (!aTypeObject.sizeRange().rangeIncludesInt(object.mapSize()))
		{
			return false;
		}
		final AvailObject keyType = aTypeObject.keyType();
		final AvailObject valueType = aTypeObject.valueType();
		final AvailObject rootBin = rootBin(object);
		final boolean keysMatch =
			keyType.equals(ANY.o())
			|| (!keyType.isEnumeration()
				&& rootBin.mapBinKeyUnionKind().isSubtypeOf(keyType));
		final boolean valuesMatch =
			valueType.equals(ANY.o())
			|| (!valueType.isEnumeration()
				&& rootBin.mapBinValueUnionKind().isSubtypeOf(valueType));
		if (keysMatch)
		{
			if (valuesMatch)
			{
				// assert keysMatch && valuesMatch;
				return true;
			}
			// assert keysMatch && !valuesMatch;
			for (final Entry entry : object.mapIterable())
			{
				if (!entry.value.isInstanceOf(valueType))
				{
					return false;
				}
			}
		}
		else
		{
			if (valuesMatch)
			{
				// assert !keysMatch && valuesMatch;
				for (final Entry entry : object.mapIterable())
				{
					if (!entry.key.isInstanceOf(keyType))
					{
						return false;
					}
				}
			}
			else
			{
				// assert !keysMatch && !valuesMatch;
				for (final Entry entry : object.mapIterable())
				{
					if (!entry.key.isInstanceOf(keyType)
						|| !entry.value.isInstanceOf(valueType))
					{
						return false;
					}
				}
			}
		}
		return true;
	}

	@Override @AvailMethod
	int o_Hash (
		final AvailObject object)
	{
		// A map's hash is a simple function of its rootBin's keysHash and
		// valuesHash.
		final AvailObject root = rootBin(object);
		int h = root.mapBinKeysHash();
		h ^= 0x45F78A7E;
		h += root.mapBinValuesHash();
		h ^= 0x57CE9F5E;
		return h;
	}

	@Override @AvailMethod
	boolean o_IsMap (
		final AvailObject object)
	{
		return true;
	}

	@Override @AvailMethod
	AvailObject o_Kind (
		final AvailObject object)
	{
		final int size = object.mapSize();
		final AvailObject sizeRange = InstanceTypeDescriptor.on(
			IntegerDescriptor.fromInt(size));
		final AvailObject root = rootBin(object);
		return MapTypeDescriptor.mapTypeForSizesKeyTypeValueType(
			sizeRange,
			root.mapBinKeyUnionKind(),
			root.mapBinValueUnionKind());
	}

	@Override @AvailMethod
	AvailObject o_MapAt (
		final AvailObject object,
		final AvailObject keyObject)
	{
		// Answer the value of the map at the specified key.  Fail if the key is
		// not present.
		final AvailObject value = rootBin(object).mapBinAtHash(
			keyObject,
			keyObject.hash());
		if (value.equalsNull())
		{
			throw new MapException(AvailErrorCode.E_KEY_NOT_FOUND);
		}
		return value;
	}

	@Override @AvailMethod
	AvailObject o_MapAtPuttingCanDestroy (
		final AvailObject object,
		final AvailObject keyObject,
		final AvailObject newValueObject,
		final boolean canDestroy)
	{
		// Answer a map like this one but with keyObject->newValueObject instead
		// of any existing mapping for keyObject.  The original map can be
		// destroyed or recycled if canDestroy is true and it's mutable.
		assert !newValueObject.equalsNull();
		final AvailObject oldRoot = rootBin(object);
		final AvailObject newRoot = oldRoot.mapBinAtHashPutLevelCanDestroy(
			 keyObject,
			 keyObject.hash(),
			 newValueObject,
			 (byte)0,
			 canDestroy);
		if (canDestroy & isMutable())
		{
			rootBin(object, newRoot);
			return object;
		}
		else if (isMutable())
		{
			object.makeImmutable();
		}
		return MapDescriptor.createFromBin(newRoot);
	}

	@Override @AvailMethod
	AvailObject o_KeysAsSet (
		final AvailObject object)
	{
		// Answer a set with all my keys.  Mark the keys as immutable because
		// they'll be shared with the new set.
		AvailObject result = SetDescriptor.empty();
		for (final Entry entry : object.mapIterable())
		{
			result = result.setWithElementCanDestroy(
				entry.key.makeImmutable(),
				true);
		}
		return result;
	}

	/**
	 * Answer a tuple with all my values.  Mark the values as immutable because
	 * they'll be shared with the new tuple.
	 */
	@Override @AvailMethod
	AvailObject o_ValuesAsTuple (
		final AvailObject object)
	{
		final int size = object.mapSize();
		final AvailObject result = ObjectTupleDescriptor.mutable().create(size);
		for (int i = 1; i <= size; i++)
		{
			// Initialize it for when we have our own garbage collector again.
			result.tupleAtPut(i, NullDescriptor.nullObject());
		}
		result.hashOrZero(0);
		int index = 1;
		for (final Entry entry : object.mapIterable())
		{
			result.tupleAtPut(index, entry.value.makeImmutable());
			index++;
		}
		assert index == size + 1;
		return result;
	}

	@Override @AvailMethod
	AvailObject o_MapWithoutKeyCanDestroy (
		final AvailObject object,
		final AvailObject keyObject,
		final boolean canDestroy)
	{
		// Answer a map like this one but with keyObject removed from it.  The
		// original map can be destroyed if canDestroy is true and it's mutable.
		if (!object.hasKey(keyObject))
		{
			if (!canDestroy)
			{
				// Existing reference will be kept around.
				object.makeImmutable();
			}
			return object;
		}
		AvailObject root = rootBin(object);
		root = root.mapBinRemoveKeyHashCanDestroy(
			keyObject,
			keyObject.hash(),
			canDestroy);
		if (canDestroy && isMutable)
		{
			rootBin(object, root);
			return object;
		}
		return createFromBin(root);
	}

	@Override @AvailMethod
	boolean o_HasKey (
		final AvailObject object,
		final AvailObject key)
	{
		// Answer whether the map has the given key.
		return !rootBin(object).mapBinAtHash(key, key.hash()).equalsNull();
	}

	@Override @AvailMethod
	int o_MapSize (
		final AvailObject object)
	{
		// Answer how many elements are in the set.  Delegate to the rootBin.
		return rootBin(object).binSize();
	}

	@Override @AvailMethod
	MapDescriptor.MapIterable o_MapIterable (
		final AvailObject object)
	{
		return rootBin(object).mapBinIterable();
		// return new MapIterable(rootBin(object));
	}

	@Override @AvailMethod @ThreadSafe
	SerializerOperation o_SerializerOperation (
		final AvailObject object)
	{
		return SerializerOperation.MAP;
	}

	@Override
	public boolean o_ShowValueInNameForDebugger (
		final AvailObject object)
	{
		return false;
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
		public AvailObject key;

		/**
		 * The hash of the key at some {@link MapIterable}'s current position.
		 */
		public int keyHash;

		/**
		 * The value associated with the key at some {@link MapIterable}'s
		 * current position.
		 */
		public AvailObject value;
	}

	/**
	 * {@link MapDescriptor.MapIterable} is returned by {@linkplain
	 * MapDescriptor#o_MapIterable(AvailObject) mapIterable()} to support use
	 * of Java's "foreach" control structure on {@linkplain MapDescriptor maps}.
	 *
	 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
	 */
	public abstract static class MapIterable
	implements
		Iterator<Entry>,
		Iterable<Entry>
	{
		/**
		 * The {@link Entry} to be reused for each <key, value> pair while
		 * iterating over this {@link MapDescriptor map}.
		 */
		protected final Entry entry = new Entry();

		/**
		 * Construct a new {@link MapDescriptor.MapIterable}.
		 */
		protected MapIterable ()
		{
			// Nothing
		}

		/**
		 * Convert trivially between an Iterable and an Iterator, since this
		 * class supports both protocols.
		 */
		@Override
		public MapIterable iterator ()
		{
			return this;
		}

		@Override
		public void remove ()
		{
			throw new UnsupportedOperationException();
		}
	}

	/**
	 * The empty map (immutable).
	 */
	static AvailObject EmptyMap;

	/**
	 * Initialize the {@link #EmptyMap} static in addition to the usual statics.
	 */
	static void createWellKnownObjects ()
	{
		final AvailObject empty = mutable().create();
		rootBin(empty, NullDescriptor.nullObject());
		empty.makeImmutable();
		EmptyMap = empty;
	}

	/**
	 * Clear the {@link #EmptyMap} static in addition to the usual statics.
	 */
	static void clearWellKnownObjects ()
	{
		EmptyMap = null;
	}

	/**
	 * Answer the (immutable) empty map.
	 *
	 * @return The empty map.
	 */
	public static AvailObject empty ()
	{
		return EmptyMap;
	}


	/**
	 * Create a new {@linkplain MapDescriptor map} whose contents correspond to
	 * the specified {@linkplain TupleDescriptor tuple} of key-value bindings.
	 *
	 * @param tupleOfBindings
	 *        A tuple of key-value bindings, i.e. 2-element tuples.
	 * @return A new map.
	 */
	public static AvailObject newWithBindings (
		final AvailObject tupleOfBindings)
	{
		assert tupleOfBindings.isTuple();
		AvailObject newMap = EmptyMap;
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
	 * Create a new {@linkplain MapDescriptor map} based on the given
	 * {@linkplain MapBinDescriptor root bin}.
	 *
	 * @param rootBin The rootBin to use in the new map.
	 * @return A new mutable map.
	 */
	public static AvailObject createFromBin (
		final AvailObject rootBin)
	{
		final AvailObject newMap = MapDescriptor.mutable().create();
		rootBin(newMap, rootBin);
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
	public static AvailObject combineMapsCanDestroy (
		final AvailObject destination,
		final AvailObject source,
		final boolean canDestroy)
	{
		assert destination.isMap();
		assert source.isMap();

		if (!canDestroy)
		{
			destination.makeImmutable();
		}
		if (source.mapSize() == 0)
		{
			return destination;
		}
		AvailObject target = destination;
		for (final Entry entry : source.mapIterable())
		{
			target = target.mapAtPuttingCanDestroy(
				entry.key,
				entry.value,
				true);
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
