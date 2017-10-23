/**
 * MapDescriptor.java
 * Copyright © 1993-2017, The Avail Foundation, LLC.
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

import static com.avail.descriptor.AvailObject.maxPrintSize;
import static com.avail.descriptor.MapDescriptor.ObjectSlots.*;
import static com.avail.descriptor.TypeDescriptor.Types.*;
import java.util.*;

import com.avail.annotations.AvailMethod;
import com.avail.annotations.ThreadSafe;
import com.avail.exceptions.MapException;
import com.avail.exceptions.AvailErrorCode;
import com.avail.serialization.SerializerOperation;
import com.avail.utility.Generator;
import com.avail.utility.Strings;
import com.avail.utility.json.JSONWriter;
import org.jetbrains.annotations.Nullable;

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
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class MapDescriptor
extends Descriptor
{
	/**
	 * The layout of object slots for my instances.
	 */
	public enum ObjectSlots
	implements ObjectSlotsEnum
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
	 * @param map The map from which to extract the root bin.
	 * @return The map's bin.
	 */
	private static AvailObject rootBin (final A_Map map)
	{
		return ((AvailObject) map).slot(ROOT_BIN);
	}

	/**
	 * Replace the {@linkplain MapDescriptor map}'s root {@linkplain
	 * MapBinDescriptor bin}. The replacement may be {@link
	 * NilDescriptor#nil() nil} to indicate an empty map.
	 *
	 * @param map The map (must not be an indirection).
	 * @param bin The root bin for the map, or nil.
	 */
	private static void setRootBin (
		final A_Map map,
		final A_BasicObject bin)
	{
		((AvailObject) map).setSlot(ROOT_BIN, bin);
	}

	@Override
	public void printObjectOnAvoidingIndent (
		final AvailObject object,
		final StringBuilder builder,
		final IdentityHashMap<A_BasicObject, Void> recursionMap,
		final int indent)
	{
		boolean multiline = false;
		builder.append('{');
		final int startPosition = builder.length();
		boolean first = true;
		for (final MapDescriptor.Entry entry : object.mapIterable())
		{
			if (!first)
			{
				builder.append(", ");
			}
			final int entryStart = builder.length();
			entry.key().printOnAvoidingIndent(
				builder, recursionMap, indent + 2);
			builder.append("→");
			entry.value().printOnAvoidingIndent(
				builder, recursionMap, indent + 1);
			if (builder.length() - startPosition > 100
				|| builder.indexOf("\n", entryStart) != -1)
			{
				// Start over with multiple line formatting.
				builder.setLength(startPosition);
				multiline = true;
				break;
			}
			if (builder.length() > maxPrintSize)
			{
				builder.append("...}");
				return;
			}
			first = false;
		}
		if (multiline)
		{
			first = true;
			for (final MapDescriptor.Entry entry : object.mapIterable())
			{
				if (!first)
				{
					builder.append(',');
				}
				Strings.newlineTab(builder, indent + 1);
				final int entryStart = builder.length();
				entry.key().printOnAvoidingIndent(
					builder, recursionMap, indent + 2);
				if (builder.indexOf("\n", entryStart) != -1)
				{
					Strings.newlineTab(builder, indent + 1);
				}
				builder.append("→");
				entry.value().printOnAvoidingIndent(
					builder, recursionMap, indent + 1);
				if (builder.length() > maxPrintSize)
				{
					builder.append("...}");
					return;
				}
				first = false;
			}
			Strings.newlineTab(builder, indent);
		}
		builder.append('}');
	}

	/**
	 * Synthetic slots to display.
	 */
	enum FakeMapSlots implements ObjectSlotsEnum
	{
		/**
		 * A fake slot to present in the debugging view for each key of the map.
		 * It is always followed by its corresponding {@link #VALUE} slot.
		 */
		KEY,

		/**
		 * A fake slot to present in the debugging view for each value in the
		 * map.  It is always preceded by its corresponding {@link #KEY} slot.
		 */
		VALUE
	}

	/**
	 * {@inheritDoc}
	 *
	 * Use the {@linkplain MapIterable map iterable} to build the list of keys
	 * and values to present.  Hide the bin structure.
	 */
	@Override
	AvailObjectFieldHelper[] o_DescribeForDebugger (
		final AvailObject object)
	{
		final AvailObjectFieldHelper[] fields =
			new AvailObjectFieldHelper[object.mapSize() * 2];
		int counter = 0;
		for (final Entry entry : object.mapIterable())
		{
			fields[counter * 2] = new AvailObjectFieldHelper(
				object, FakeMapSlots.KEY, counter + 1, entry.key());
			fields[counter * 2 + 1] = new AvailObjectFieldHelper(
				object, FakeMapSlots.VALUE, counter + 1, entry.value());
			counter++;
		}
		return fields;
	}

	@Override
	String o_NameForDebugger (final AvailObject object)
	{
		return super.o_NameForDebugger(object) + ": mapSize="
			+ object.mapSize();
	}

	@Override @AvailMethod
	boolean o_Equals (final AvailObject object, final A_BasicObject another)
	{
		return another.equalsMap(object);
	}

	@Override @AvailMethod
	boolean o_EqualsMap (final AvailObject object, final A_Map aMap)
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
		final A_BasicObject aMapRootBin = rootBin(aMap);
		for (final MapDescriptor.Entry entry : object.mapIterable())
		{
			final A_Map actualValue = aMapRootBin.mapBinAtHash(
				entry.key(),
				entry.keyHash());
			if (!entry.value().equals(actualValue))
			{
				return false;
			}
		}
		// They're equal, but occupy disjoint storage. If possible, then replace
		// one with an indirection to the other to reduce storage costs and the
		// frequency of entry-wise comparisons.
		if (!isShared())
		{
			aMap.makeImmutable();
			object.becomeIndirectionTo(aMap);
		}
		else if (!aMap.descriptor().isShared())
		{
			object.makeImmutable();
			aMap.becomeIndirectionTo(object);
		}
		return true;
	}

	@Override @AvailMethod
	boolean o_IsInstanceOfKind (
		final AvailObject object,
		final A_Type aTypeObject)
	{
		if (aTypeObject.isSupertypeOfPrimitiveTypeEnum(NONTYPE))
		{
			return true;
		}
		if (!aTypeObject.isMapType())
		{
			return false;
		}
		final int mapSize = object.mapSize();
		if (!aTypeObject.sizeRange().rangeIncludesInt(mapSize))
		{
			return false;
		}
		final A_Type keyType = aTypeObject.keyType();
		final A_Type valueType = aTypeObject.valueType();
		final AvailObject rootBin = rootBin(object);
		final boolean keyTypeIsEnumeration = keyType.isEnumeration();
		final boolean valueTypeIsEnumeration = valueType.isEnumeration();
		@Nullable A_Type keyUnionKind = null;
		@Nullable A_Type valueUnionKind = null;
		final boolean keysMatch =
			keyType.equals(ANY.o())
			|| (!keyTypeIsEnumeration
				&& (keyUnionKind = rootBin.mapBinKeyUnionKind())
					.isSubtypeOf(keyType));
		final boolean valuesMatch =
			valueType.equals(ANY.o())
			|| (!valueTypeIsEnumeration
				&& (valueUnionKind = rootBin.mapBinValueUnionKind())
					.isSubtypeOf(valueType));
		if (keysMatch)
		{
			if (valuesMatch)
			{
				assert keysMatch && valuesMatch;
				return true;
			}
			assert keysMatch && !valuesMatch;
			// If the valueUnionKind and the expected valueType don't intersect
			// then the actual map can't comply.  The empty map was already
			// special-cased.
			if (!valueTypeIsEnumeration)
			{
				assert valueUnionKind != null;
				if (valueUnionKind.typeIntersection(valueType).isBottom())
				{
					return false;
				}
			}
			for (final Entry entry : object.mapIterable())
			{
				if (!entry.value().isInstanceOf(valueType))
				{
					return false;
				}
			}
		}
		else
		{
			// If the keyUnionKind and the expected keyType don't intersect
			// then the actual map can't comply.  The empty map was already
			// special-cased.
			if (!keyTypeIsEnumeration)
			{
				assert keyUnionKind != null;
				if (keyUnionKind.typeIntersection(keyType).isBottom())
				{
					return false;
				}
			}
			if (valuesMatch)
			{
				assert !keysMatch && valuesMatch;
				for (final Entry entry : object.mapIterable())
				{
					if (!entry.key().isInstanceOf(keyType))
					{
						return false;
					}
				}
			}
			else
			{
				assert !keysMatch && !valuesMatch;
				// If the valueUnionKind and the expected valueType don't
				// intersect then the actual map can't comply.  The empty map
				// was already special-cased.
				if (!valueTypeIsEnumeration)
				{
					assert valueUnionKind != null;
					if (valueUnionKind.typeIntersection(valueType).isBottom())
					{
						return false;
					}
				}
				for (final Entry entry : object.mapIterable())
				{
					if (!entry.key().isInstanceOf(keyType)
						|| !entry.value().isInstanceOf(valueType))
					{
						return false;
					}
				}
			}
		}
		return true;
	}

	@Override @AvailMethod
	int o_Hash (final AvailObject object)
	{
		// A map's hash is a simple function of its rootBin's keysHash and
		// valuesHash.
		final A_Map root = rootBin(object);
		int h = root.mapBinKeysHash();
		h ^= 0x45F78A7E;
		h += root.mapBinValuesHash();
		h ^= 0x57CE9F5E;
		return h;
	}

	@Override @AvailMethod
	boolean o_IsMap (final AvailObject object)
	{
		return true;
	}

	@Override @AvailMethod
	A_Type o_Kind (final AvailObject object)
	{
		final int size = object.mapSize();
		final A_Type sizeRange = InstanceTypeDescriptor.on(
			IntegerDescriptor.fromInt(size));
		final A_Map root = rootBin(object);
		return MapTypeDescriptor.mapTypeForSizesKeyTypeValueType(
			sizeRange,
			root.mapBinKeyUnionKind(),
			root.mapBinValueUnionKind());
	}

	@Override @AvailMethod
	AvailObject o_MapAt (
		final AvailObject object,
		final A_BasicObject keyObject)
	{
		// Answer the value of the map at the specified key. Fail if the key is
		// not present.
		final AvailObject value = rootBin(object).mapBinAtHash(
			keyObject,
			keyObject.hash());
		if (value.equalsNil())
		{
			throw new MapException(AvailErrorCode.E_KEY_NOT_FOUND);
		}
		return value;
	}

	/**
	 * Answer a map like this one but with keyObject->newValueObject instead
	 * of any existing mapping for keyObject. The original map can be destroyed
	 * or recycled if canDestroy is true and it's mutable.
	 *
	 * @param object The map.
	 * @param keyObject The key to add or replace.
	 * @param newValueObject The new value to store under the provided key.
	 * @param canDestroy Whether the given map may be recycled (if mutable).
	 * @return The new map, possibly the given one if canDestroy is true.
	 */
	@Override @AvailMethod
	A_Map o_MapAtPuttingCanDestroy (
		final AvailObject object,
		final A_BasicObject keyObject,
		final A_BasicObject newValueObject,
		final boolean canDestroy)
	{
		final A_BasicObject oldRoot = rootBin(object);
		final A_BasicObject traversedKey = keyObject.traversed();
		final A_BasicObject newRoot = oldRoot.mapBinAtHashPutLevelCanDestroy(
			traversedKey,
			traversedKey.hash(),
			newValueObject,
			(byte)0,
			canDestroy);
		if (canDestroy && isMutable())
		{
			setRootBin(object, newRoot);
			return object;
		}
		if (isMutable())
		{
			object.makeImmutable();
		}
		return MapDescriptor.createFromBin(newRoot);
	}

	@Override @AvailMethod
	A_Set o_KeysAsSet (final AvailObject object)
	{
		// Answer a set with all my keys.  Mark the keys as immutable because
		// they'll be shared with the new set.
		A_Set result = SetDescriptor.empty();
		for (final Entry entry : object.mapIterable())
		{
			result = result.setWithElementCanDestroy(
				entry.key().makeImmutable(),
				true);
		}
		return result;
	}

	/**
	 * Answer a tuple with all my values.  Mark the values as immutable because
	 * they'll be shared with the new tuple.
	 */
	@Override @AvailMethod
	A_Tuple o_ValuesAsTuple (final AvailObject object)
	{
		final int size = object.mapSize();
		final MapIterable mapIterable = object.mapIterable();
		final AvailObject result = ObjectTupleDescriptor.generateFrom(
			size,
			new Generator<A_BasicObject>()
			{
				@Override
				public A_BasicObject value ()
				{
					return mapIterable.next().value().makeImmutable();
				}
			});
		return result;
	}

	@Override @AvailMethod
	A_Map o_MapWithoutKeyCanDestroy (
		final AvailObject object,
		final A_BasicObject keyObject,
		final boolean canDestroy)
	{
		// Answer a map like this one but with keyObject removed from it. The
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
		A_BasicObject root = rootBin(object);
		root = root.mapBinRemoveKeyHashCanDestroy(
			keyObject,
			keyObject.hash(),
			canDestroy);
		if (canDestroy && isMutable())
		{
			setRootBin(object, root);
			return object;
		}
		return createFromBin(root);
	}

	@Override @AvailMethod
	boolean o_HasKey (final AvailObject object, final A_BasicObject key)
	{
		// Answer whether the map has the given key.
		return !rootBin(object).mapBinAtHash(key, key.hash()).equalsNil();
	}

	@Override @AvailMethod
	int o_MapSize (final AvailObject object)
	{
		// Answer how many elements are in the set. Delegate to the rootBin.
		return rootBin(object).binSize();
	}

	@Override @AvailMethod
	MapDescriptor.MapIterable o_MapIterable (final AvailObject object)
	{
		return rootBin(object).mapBinIterable();
	}

	@Override @AvailMethod @ThreadSafe
	SerializerOperation o_SerializerOperation (final AvailObject object)
	{
		return SerializerOperation.MAP;
	}

	@Override
	public boolean o_ShowValueInNameForDebugger (final AvailObject object)
	{
		return false;
	}

	@Override
	void o_WriteTo (final AvailObject object, final JSONWriter writer)
	{
		writer.startObject();
		writer.write("kind");
		writer.write("map");
		if (object.kind().keyType().isSubtypeOf(
			TupleTypeDescriptor.stringType()))
		{
			writer.write("map");
			writer.startObject();
			for (final Entry entry : object.mapIterable())
			{
				entry.key().writeTo(writer);
				entry.value().writeTo(writer);
			}
			writer.endObject();
		}
		else
		{
			writer.write("bindings");
			writer.startArray();
			for (final Entry entry : object.mapIterable())
			{
				writer.startArray();
				entry.key().writeTo(writer);
				entry.value().writeTo(writer);
				writer.endArray();
			}
			writer.endArray();
		}
		writer.endObject();
	}

	@Override
	void o_WriteSummaryTo (final AvailObject object, final JSONWriter writer)
	{
		writer.startObject();
		writer.write("kind");
		writer.write("map");
		if (object.kind().keyType().isSubtypeOf(
			TupleTypeDescriptor.stringType()))
		{
			writer.write("map");
			writer.startObject();
			for (final Entry entry : object.mapIterable())
			{
				entry.key().writeTo(writer);
				entry.value().writeSummaryTo(writer);
			}
			writer.endObject();
		}
		else
		{
			writer.write("bindings");
			writer.startArray();
			for (final Entry entry : object.mapIterable())
			{
				writer.startArray();
				entry.key().writeSummaryTo(writer);
				entry.value().writeSummaryTo(writer);
				writer.endArray();
			}
			writer.endArray();
		}
		writer.endObject();
	}

	/**
	 * {@link MapDescriptor.Entry} exists solely to allow the "foreach" control
	 * structure to be used on a {@linkplain MapDescriptor map} by suitable use
	 * of {@linkplain MapDescriptor#o_MapIterable(AvailObject) mapIterable()}.
	 *
	 * @author Mark van Gulik &lt;mark@availlang.org&gt;
	 */
	public static final class Entry
	{
		/**
		 * The key at some {@link MapIterable}'s current position.
		 */
		private @Nullable AvailObject key;

		/**
		 * The hash of the key at some {@link MapIterable}'s current position.
		 */
		private int keyHash;

		/**
		 * The value associated with the key at some {@link MapIterable}'s
		 * current position.
		 */
		private @Nullable AvailObject value;

		/**
		 * Update my fields.
		 *
		 * @param newKey The key to set.
		 * @param newKeyHash the hash of the key.
		 * @param newValue The value to set.
		 */
		public void setKeyAndHashAndValue (
			final @Nullable AvailObject newKey,
			final int newKeyHash,
			final @Nullable AvailObject newValue)
		{
			key = newKey;
			keyHash = newKeyHash;
			value = newValue;
		}

		/**
		 * @return The entry's key.
		 */
		public AvailObject key ()
		{
			final AvailObject k = key;
			assert k != null;
			return k;
		}

		/**
		 * @return The entry's key's precomputed hash value.
		 */
		public int keyHash ()
		{
			return keyHash;
		}

		/**
		 * @return The entry's value.
		 */
		public AvailObject value ()
		{
			final AvailObject v = value;
			assert v != null;
			return v;
		}
	}

	/**
	 * {@link MapDescriptor.MapIterable} is returned by {@linkplain
	 * MapDescriptor#o_MapIterable(AvailObject) mapIterable()} to support use
	 * of Java's "foreach" control structure on {@linkplain MapDescriptor maps}.
	 *
	 * @author Mark van Gulik &lt;mark@availlang.org&gt;
	 */
	public abstract static class MapIterable
	implements Iterator<Entry>, Iterable<Entry>
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
	 * Construct a new {@link MapDescriptor}.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 */
	private MapDescriptor (final Mutability mutability)
	{
		super(mutability, TypeTag.MAP_TAG, ObjectSlots.class, null);
	}

	/** The mutable {@link MapDescriptor}. */
	private static final MapDescriptor mutable =
		new MapDescriptor(Mutability.MUTABLE);

	@Override
	MapDescriptor mutable ()
	{
		return mutable;
	}

	/** The immutable {@link MapDescriptor}. */
	private static final MapDescriptor immutable =
		new MapDescriptor(Mutability.IMMUTABLE);

	@Override
	MapDescriptor immutable ()
	{
		return immutable;
	}

	/** The shared {@link MapDescriptor}. */
	private static final MapDescriptor shared =
		new MapDescriptor(Mutability.SHARED);

	@Override
	MapDescriptor shared ()
	{
		return shared;
	}

	/**
	 * Create a new {@linkplain MapDescriptor map} whose contents correspond to
	 * the specified {@linkplain TupleDescriptor tuple} of key-value bindings.
	 *
	 * @param tupleOfBindings
	 *        A tuple of key-value bindings, i.e. 2-element tuples.
	 * @return A new map.
	 */
	public static A_Map newWithBindings (
		final A_Tuple tupleOfBindings)
	{
		assert tupleOfBindings.isTuple();
		A_Map newMap = empty();
		for (final A_Tuple binding : tupleOfBindings)
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
	public static A_Map createFromBin (final A_BasicObject rootBin)
	{
		final A_Map newMap = mutable.create();
		setRootBin(newMap, rootBin);
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
	public static A_BasicObject combineMapsCanDestroy (
		final A_Map destination,
		final A_Map source,
		final boolean canDestroy)
	{
		assert destination.isMap();
		assert source.isMap();

		if (!canDestroy)
		{
			destination.makeImmutable();
		}
		if (source.sameAddressAs(destination))
		{
			return destination;
		}
		if (source.mapSize() == 0)
		{
			return destination;
		}
		A_Map target = destination;
		for (final Entry entry : source.mapIterable())
		{
			target = target.mapAtPuttingCanDestroy(
				entry.key(),
				entry.value(),
				true);
		}
		return target;
	}

	/** The empty map. */
	private static final A_Map emptyMap;

	static
	{
		final A_Map map = createFromBin(NilDescriptor.nil());
		map.hash();
		emptyMap = map.makeShared();
	}

	/**
	 * Answer the empty map.
	 *
	 * @return The empty map.
	 */
	public static A_Map empty ()
	{
		return emptyMap;
	}
}
