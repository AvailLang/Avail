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
	private static @NotNull AvailObject rootBin (
		final @NotNull AvailObject object)
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
		final @NotNull AvailObject object,
		final @NotNull AvailObject bin)
	{
		object.setSlot(ObjectSlots.ROOT_BIN, bin);
	}

	@Override
	public void printObjectOnAvoidingIndent (
		final @NotNull AvailObject object,
		final @NotNull StringBuilder aStream,
		final @NotNull List<AvailObject> recursionList,
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
				for (int i = indent + 1; i > 0; i--)
				{
					aStream.append('\t');
				}
				entry.key.printOnAvoidingIndent(
					aStream, recursionList, indent + 2);
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
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
	{
		return another.equalsMap(object);
	}

	@Override @AvailMethod
	boolean o_EqualsMap (
		final @NotNull AvailObject object,
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
		final @NotNull AvailObject object,
		final AvailObject aTypeObject)
	{
		if (ANY.o().isSubtypeOf(aTypeObject))
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
		final AvailObject keyType = aTypeObject.keyType();
		final AvailObject valueType = aTypeObject.valueType();
		final AvailObject rootBin = rootBin(object);
		final boolean keysMatch =
			rootBin.mapBinKeyUnionKind().isSubtypeOf(keyType);
		final boolean valuesMatch =
			rootBin.mapBinValueUnionKind().isSubtypeOf(valueType);
		if (keysMatch && valuesMatch)
		{
			return true;
		}
		// We could produce separate loops for the cases where one or the other
		// matched, but we'll leave it up to the HotSpot compiler to hoist the
		// final booleans out.  Or at least rely on hardware branch prediction.
		for (final Entry entry : object.mapIterable())
		{
			if (!keysMatch && !entry.key.isInstanceOf(keyType))
			{
				return false;
			}
			if (!valuesMatch && !entry.value.isInstanceOf(valueType))
			{
				return false;
			}
		}
		return true;
	}

	@Override @AvailMethod
	int o_Hash (
		final @NotNull AvailObject object)
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
		final @NotNull AvailObject object)
	{
		return true;
	}

	@Override @AvailMethod
	@NotNull AvailObject o_Kind (
		final @NotNull AvailObject object)
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
	@NotNull AvailObject o_MapAt (
		final @NotNull AvailObject object,
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
	@NotNull AvailObject o_MapAtPuttingCanDestroy (
		final @NotNull AvailObject object,
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
	@NotNull AvailObject o_KeysAsSet (
		final @NotNull AvailObject object)
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
	@NotNull AvailObject o_ValuesAsTuple (
		final @NotNull AvailObject object)
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
		final @NotNull AvailObject object,
		final AvailObject key)
	{
		// Answer whether the map has the given key.
		return !rootBin(object).mapBinAtHash(key, key.hash()).equalsNull();
	}

	@Override @AvailMethod
	int o_MapSize (
		final @NotNull AvailObject object)
	{
		// Answer how many elements are in the set.  Delegate to the rootBin.
		return rootBin(object).binSize();
	}

	@Override @AvailMethod
	MapDescriptor.MapIterable o_MapIterable (
		final @NotNull AvailObject object)
	{
		return new MapIterable(rootBin(object));
	}

	@Override
	@AvailMethod @ThreadSafe
	@NotNull SerializerOperation o_SerializerOperation (
		final @NotNull AvailObject object)
	{
		return SerializerOperation.MAP;
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
	 * of the "foreach" control structure on {@linkplain MapDescriptor maps}.
	 *
	 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
	 */
	public static class MapIterable
	implements
		Iterator<Entry>,
		Iterable<Entry>
	{
		/**
		 * The {@link Entry} to be reused for each <key, value> pair while
		 * iterating over this {@link MapDescriptor map}.
		 */
		private final Entry entry = new Entry();

		/**
		 * The path through map bins, including the current linear bin.
		 */
		final Deque<AvailObject> binStack = new ArrayDeque<AvailObject>();

		/**
		 * The current position in each bin on the binStack, including the
		 * linear bin.  It should be the same size as the binStack.  When
		 * they're both empty it indicates {@code !hasNext()}.
		 */
		final Deque<Integer> subscriptStack = new ArrayDeque<Integer>();

		/**
		 * Construct a new {@link MapIterable} over the keys and values
		 * recursively contained in the given root bin / null.
		 *
		 * @see ObjectSlots#ROOT_BIN
		 * @param root The root bin over which to iterate.
		 */
		MapIterable (final AvailObject root)
		{
			followLeftmost(root);
		}

		/**
		 * Visit this bin or {@link NullDescriptor#nullObject() null object}.
		 * In particular, travel down its left spine so that it's positioned at
		 * the leftmost descendant.
		 *
		 * @param bin The bin or null object at which to begin enumerating.
		 */
		private void followLeftmost (
			final @NotNull AvailObject bin)
		{
			if (bin.equalsNull())
			{
				// The null object may only occur at the top of the bin tree.
				assert binStack.isEmpty();
				assert subscriptStack.isEmpty();
				//entry.keyHash = 0;
				entry.key = null;
				entry.value = null;
			}
			else
			{
				AvailObject currentBin = bin;
				while (currentBin.isHashedMapBin())
				{
					binStack.addLast(currentBin);
					subscriptStack.addLast(1);
					currentBin = currentBin.binElementAt(1);
				}
				binStack.addLast(currentBin);
				subscriptStack.addLast(1);
				assert binStack.size() == subscriptStack.size();
			}
		}

		@Override
		public Entry next ()
		{
			assert !binStack.isEmpty();
			final AvailObject linearBin = binStack.getLast().traversed();
			final Integer linearIndex = subscriptStack.getLast();
			entry.keyHash = linearBin.slot(
				LinearMapBinDescriptor.IntegerSlots.KEY_HASHES_,
				linearIndex);
			entry.key = linearBin.binElementAt(linearIndex * 2 - 1);
			entry.value = linearBin.binElementAt(linearIndex * 2);
			// Got the result.  Now advance the state...
			if (linearIndex < linearBin.variableIntegerSlotsCount())
			{
				// Continue in same leaf bin.
				subscriptStack.removeLast();
				subscriptStack.addLast(linearIndex + 1);
				return entry;
			}

			binStack.removeLast();
			subscriptStack.removeLast();
			assert binStack.size() == subscriptStack.size();
			while (true)
			{
				if (subscriptStack.isEmpty())
				{
					// This was the last entry in the map.
					return entry;
				}
				final AvailObject internalBin = binStack.getLast().traversed();
				final int internalSubscript = subscriptStack.getLast();
				final int maxSubscript = internalBin.variableObjectSlotsCount();
				if (internalSubscript != maxSubscript)
				{
					// Continue in current internal (hashed) bin.
					subscriptStack.addLast(subscriptStack.removeLast() + 1);
					assert binStack.size() == subscriptStack.size();
					followLeftmost(
						binStack.getLast().binElementAt(internalSubscript + 1));
					assert binStack.size() == subscriptStack.size();
					return entry;
				}
				subscriptStack.removeLast();
				binStack.removeLast();
				assert binStack.size() == subscriptStack.size();
			}
		}

		@Override
		public boolean hasNext ()
		{
			return !binStack.isEmpty();
		}

		@Override
		public void remove ()
		{
			throw new UnsupportedOperationException();
		}

		@Override
		public MapIterable iterator ()
		{
			// This is what Java *should* have provided all along.
			return this;
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
	public static @NotNull AvailObject newWithBindings (
		final @NotNull AvailObject tupleOfBindings)
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
	public static @NotNull AvailObject createFromBin (
		final @NotNull AvailObject rootBin)
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
	public static @NotNull AvailObject combineMapsCanDestroy (
		final @NotNull AvailObject destination,
		final @NotNull AvailObject source,
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
