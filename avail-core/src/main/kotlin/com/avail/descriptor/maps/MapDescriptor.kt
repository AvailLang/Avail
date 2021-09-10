/*
 * MapDescriptor.kt
 * Copyright © 1993-2021, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 *  * Neither the name of the copyright holder nor the names of the contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
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
package com.avail.descriptor.maps

import com.avail.annotations.ThreadSafe
import com.avail.descriptor.maps.A_Map.Companion.forEach
import com.avail.descriptor.maps.A_Map.Companion.hasKey
import com.avail.descriptor.maps.A_Map.Companion.mapAtPuttingCanDestroy
import com.avail.descriptor.maps.A_Map.Companion.mapIterable
import com.avail.descriptor.maps.A_Map.Companion.mapSize
import com.avail.descriptor.maps.A_MapBin.Companion.forEachInMapBin
import com.avail.descriptor.maps.A_MapBin.Companion.mapBinAtHash
import com.avail.descriptor.maps.A_MapBin.Companion.mapBinAtHashPutLevelCanDestroy
import com.avail.descriptor.maps.A_MapBin.Companion.mapBinAtHashReplacingLevelCanDestroy
import com.avail.descriptor.maps.A_MapBin.Companion.mapBinIterable
import com.avail.descriptor.maps.A_MapBin.Companion.mapBinKeyUnionKind
import com.avail.descriptor.maps.A_MapBin.Companion.mapBinKeysHash
import com.avail.descriptor.maps.A_MapBin.Companion.mapBinRemoveKeyHashCanDestroy
import com.avail.descriptor.maps.A_MapBin.Companion.mapBinSize
import com.avail.descriptor.maps.A_MapBin.Companion.mapBinValueUnionKind
import com.avail.descriptor.maps.A_MapBin.Companion.mapBinValuesHash
import com.avail.descriptor.maps.LinearMapBinDescriptor.Companion.emptyLinearMapBin
import com.avail.descriptor.maps.MapDescriptor.ObjectSlots.ROOT_BIN
import com.avail.descriptor.numbers.IntegerDescriptor.Companion.fromInt
import com.avail.descriptor.representation.A_BasicObject
import com.avail.descriptor.representation.AvailObject
import com.avail.descriptor.representation.AvailObject.Companion.combine3
import com.avail.descriptor.representation.AvailObjectFieldHelper
import com.avail.descriptor.representation.Descriptor
import com.avail.descriptor.representation.Mutability
import com.avail.descriptor.representation.Mutability.IMMUTABLE
import com.avail.descriptor.representation.Mutability.MUTABLE
import com.avail.descriptor.representation.Mutability.SHARED
import com.avail.descriptor.representation.ObjectSlotsEnum
import com.avail.descriptor.sets.A_Set
import com.avail.descriptor.sets.SetDescriptor
import com.avail.descriptor.sets.SetDescriptor.Companion.generateSetFrom
import com.avail.descriptor.tuples.A_Tuple
import com.avail.descriptor.tuples.A_Tuple.Companion.component1
import com.avail.descriptor.tuples.A_Tuple.Companion.component2
import com.avail.descriptor.tuples.A_Tuple.Companion.concatenateTuplesCanDestroy
import com.avail.descriptor.tuples.A_Tuple.Companion.copyTupleFromToCanDestroy
import com.avail.descriptor.tuples.A_Tuple.Companion.tupleSize
import com.avail.descriptor.tuples.ObjectTupleDescriptor.Companion.generateObjectTupleFrom
import com.avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import com.avail.descriptor.tuples.StringDescriptor.Companion.stringFrom
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.A_Type.Companion.isSubtypeOf
import com.avail.descriptor.types.A_Type.Companion.isSupertypeOfPrimitiveTypeEnum
import com.avail.descriptor.types.A_Type.Companion.keyType
import com.avail.descriptor.types.A_Type.Companion.rangeIncludesLong
import com.avail.descriptor.types.A_Type.Companion.sizeRange
import com.avail.descriptor.types.A_Type.Companion.typeIntersection
import com.avail.descriptor.types.A_Type.Companion.valueType
import com.avail.descriptor.types.InstanceTypeDescriptor.Companion.instanceType
import com.avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.wholeNumbers
import com.avail.descriptor.types.MapTypeDescriptor.Companion.mapTypeForSizesKeyTypeValueType
import com.avail.descriptor.types.TupleTypeDescriptor.Companion.stringType
import com.avail.descriptor.types.TypeDescriptor.Types.ANY
import com.avail.descriptor.types.TypeDescriptor.Types.NONTYPE
import com.avail.descriptor.types.TypeTag
import com.avail.exceptions.AvailErrorCode
import com.avail.exceptions.MapException
import com.avail.optimizer.jvm.CheckedMethod
import com.avail.optimizer.jvm.CheckedMethod.Companion.staticMethod
import com.avail.optimizer.jvm.ReferencedInGeneratedCode
import com.avail.serialization.SerializerOperation
import com.avail.utility.Strings.newlineTab
import com.avail.utility.cast
import com.avail.utility.json.JSONWriter
import java.util.IdentityHashMap

/**
 * An Avail [map][MapDescriptor] refers to the root of a Bagwell Ideal Hash
 * Tree.  The implementation is similar to that of [sets][SetDescriptor], but
 * using map-specific bin descriptors instead of the set-specific ones.
 *
 * Unlike the optimization for [sets][SetDescriptor] in which a singleton set
 * has the element itself as the root bin (since bins likewise are not
 * manipulated by Avail programs), that optimization is not available for maps.
 * That's because a singleton map records both a key and a value.  Thus, a map
 * bin is allowed to be so small that it can contain one key and value. In fact,
 * there is also a single size zero linear map bin for use as the root of the
 * empty map.
 *
 * The presence of singular bins affects maps of all scales, due to the
 * recursive nature of the hash tree of bins, many of which contain sub-bins.
 * Since a sub-bin of size one for a set is just the element itself, small bins
 * lead to more expense in space for maps than for sets.  To compensate for
 * this, maps are allowed to have larger linear bins before replacing them with
 * their hashed equivalents.
 *
 * @constructor
 *
 * @param mutability
 *   The [mutability][Mutability] of the new descriptor.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
class MapDescriptor private constructor(
	mutability: Mutability
) : Descriptor(mutability, TypeTag.MAP_TAG, ObjectSlots::class.java, null
) {
	/**
	 * The layout of object slots for my instances.
	 */
	enum class ObjectSlots : ObjectSlotsEnum {
		/**
		 * The topmost bin of this [map][MapDescriptor].  Unlike the
		 * implementation for [sets][SetDescriptor], all maps contain an actual
		 * map bin in this slot.
		 */
		ROOT_BIN
	}

	override fun printObjectOnAvoidingIndent(
		self: AvailObject,
		builder: StringBuilder,
		recursionMap: IdentityHashMap<A_BasicObject, Void>,
		indent: Int
	) {
		builder.append('{')
		val startPosition = builder.length
		var first = true
		var multiline = false
		for ((key, value) in self.mapIterable) {
			if (!first) {
				builder.append(", ")
			}
			val entryStart = builder.length
			key.printOnAvoidingIndent(builder, recursionMap, indent + 2)
			builder.append('→')
			value.printOnAvoidingIndent(builder, recursionMap, indent + 1)
			if (builder.length - startPosition > 100
				|| builder.indexOf("\n", entryStart) != -1
			) {
				// Start over with multiple line formatting.
				builder.setLength(startPosition)
				multiline = true
				break
			}
			first = false
		}
		if (multiline) {
			first = true
			self.forEach { key, value ->
				if (!first) {
					builder.append(',')
				}
				newlineTab(builder, indent + 1)
				val entryStart = builder.length
				key.printOnAvoidingIndent(builder, recursionMap, indent + 2)
				if (builder.indexOf("\n", entryStart) != -1) {
					newlineTab(builder, indent + 1)
				}
				builder.append('→')
				value.printOnAvoidingIndent(builder, recursionMap, indent + 1)
				first = false
			}
			newlineTab(builder, indent)
		}
		builder.append('}')
	}

	/**
	 * Synthetic slots to display.
	 */
	internal enum class FakeMapSlots : ObjectSlotsEnum {
		/**
		 * A fake slot to present in the debugging view for each key of the map.
		 * It is always followed by its corresponding [VALUE_] slot.
		 */
		KEY_,

		/**
		 * A fake slot to present in the debugging view for each value in the
		 * map.  It is always preceded by its corresponding [KEY_] slot.
		 */
		VALUE_
	}

	/**
	 * Use the [map&#32;iterable][MapIterable] to build the list of keys and
	 * values to present.  Hide the bin structure.
	 */
	override fun o_DescribeForDebugger(
		self: AvailObject
	): Array<AvailObjectFieldHelper> {
		if (self.isInstanceOfKind(
				mapTypeForSizesKeyTypeValueType(
					wholeNumbers, stringType, ANY.o))
		) {
			// The keys are all strings.
			val mapIterable = self.mapIterable
			return Array(self.mapSize) { counter ->
				val (key, value) = mapIterable.next()
				AvailObjectFieldHelper(
					self,
					object : ObjectSlotsEnum {
						/** The cached entry name. */
						private var name: String? = null

						override fun fieldName(): String {
							name?.run { return this }
							// Truncate large key strings.
							val keyStringSize = key.tupleSize
							val keyString = if (keyStringSize > 50) {
								tuple(
									key.copyTupleFromToCanDestroy(1, 25, false),
									stringFrom(" … "),
									key.copyTupleFromToCanDestroy(
										keyStringSize - 24,
										keyStringSize,
										false)
								).concatenateTuplesCanDestroy(false)
							}
							else
							{
								key
							}
							name = ("Key#$counter $keyString")
							return name!!
						}

						override fun fieldOrdinal() = counter
					},
					-1,
					value)
			}
		}
		val fields = arrayOfNulls<AvailObjectFieldHelper>(self.mapSize shl 1)
		var arrayIndex = 0
		self.mapIterable.forEachIndexed { entryCount, (key, value) ->
			fields[arrayIndex++] = AvailObjectFieldHelper(
				self, FakeMapSlots.KEY_, entryCount + 1, key)
			fields[arrayIndex++] = AvailObjectFieldHelper(
				self, FakeMapSlots.VALUE_, entryCount + 1, value)
		}
		return fields.cast()!!
	}

	override fun o_NameForDebugger(self: AvailObject) =
		super.o_NameForDebugger(self) + ": mapSize=${self.mapSize}"

	override fun o_Equals(self: AvailObject, another: A_BasicObject) =
		another.equalsMap(self)

	override fun o_EqualsMap(self: AvailObject, aMap: A_Map): Boolean = when {
		self.sameAddressAs(aMap) -> true
		rootBin(self).sameAddressAs(rootBin(aMap)) -> true
		self.mapSize != aMap.mapSize -> false
		self.hash() != aMap.hash() -> false
		rootBin(aMap).let { root ->
			self.mapIterable.any { (k, v, h) ->
				root.mapBinAtHash(k, h).let { it === null || !it.equals(v) }
			}
		} -> false
		!isShared -> {
			self.becomeIndirectionTo(aMap.makeImmutable())
			true
		}
		!aMap.descriptor().isShared -> {
			aMap.becomeIndirectionTo(self.makeImmutable())
			true
		}
		else -> {
			// Both are shared.  Substitute one of the bins for the other to
			// speed up subsequent equality checks.
			self.writeBackSlot(ROOT_BIN, 1, (rootBin(aMap) as AvailObject))
			true
		}
	}

	override fun o_ForEach(
		self: AvailObject,
		action: (AvailObject, AvailObject) -> Unit
	) = rootBin(self).forEachInMapBin(action)

	override fun o_IsInstanceOfKind(
		self: AvailObject,
		aType: A_Type
	): Boolean {
		when {
			aType.isSupertypeOfPrimitiveTypeEnum(NONTYPE) -> return true
			!aType.isMapType -> return false
			!aType.sizeRange.rangeIncludesLong(self.mapSize.toLong()) ->
				return false
		}
		val keyType = aType.keyType
		val valueType = aType.valueType
		val rootBin = rootBin(self)
		val keyTypeIsEnumeration = keyType.isEnumeration
		val valueTypeIsEnumeration = valueType.isEnumeration
		var keyUnionKind: A_Type? = null
		val keysMatch: Boolean
		when {
			keyType.equals(ANY.o) -> keysMatch = true
			keyTypeIsEnumeration -> keysMatch = false
			else -> {
				keyUnionKind = rootBin.mapBinKeyUnionKind
				keysMatch = keyUnionKind.isSubtypeOf(keyType)
			}
		}
		var valueUnionKind: A_Type? = null
		val valuesMatch: Boolean
		when {
			valueType.equals(ANY.o) -> valuesMatch = true
			valueTypeIsEnumeration -> valuesMatch = false
			else -> {
				valueUnionKind = rootBin.mapBinValueUnionKind
				valuesMatch = valueUnionKind.isSubtypeOf(valueType)
			}
		}
		return when {
			keysMatch && valuesMatch -> true
			// If the valueUnionKind and the expected valueType don't intersect
			// then the actual map can't comply.  The empty map was already
			// special-cased.
			keysMatch
					&& !valueTypeIsEnumeration
					&& valueUnionKind!!.typeIntersection(valueType).isBottom ->
				false
			keysMatch ->
				self.mapIterable.all { (_, v) -> v.isInstanceOf(valueType) }
			// If the keyUnionKind and the expected keyType don't intersect
			// then the actual map can't comply.  The empty map was already
			// special-cased.
			!keyTypeIsEnumeration
					&& keyUnionKind!!.typeIntersection(keyType).isBottom ->
				false
			valuesMatch ->
				self.mapIterable.all { (k) -> k.isInstanceOf(keyType) }
			!valueTypeIsEnumeration
					&& valueUnionKind!!.typeIntersection(valueType).isBottom ->
				false
			else -> self.mapIterable.none { (k, v) ->
				(!k.isInstanceOf(keyType) || !v.isInstanceOf(valueType))
			}
		}
	}

	// A map's hash is a simple function of its rootBin's keysHash and
	// valuesHash.
	override fun o_Hash(self: AvailObject): Int =
		rootBin(self).run {
			combine3(mapBinKeysHash, mapBinValuesHash, 0x57CE9F5E)
		}

	override fun o_IsMap(self: AvailObject) = true

	override fun o_Kind(self: AvailObject): A_Type {
		val root = rootBin(self)
		return mapTypeForSizesKeyTypeValueType(
			instanceType(fromInt(self.mapSize)),
			root.mapBinKeyUnionKind,
			root.mapBinValueUnionKind)
	}

	/**
	 * Answer the value of the map at the specified key. Fail if the key is not
	 * present.
	 */
	override fun o_MapAt(
		self: AvailObject,
		keyObject: A_BasicObject
	) = rootBin(self).mapBinAtHash(keyObject, keyObject.hash())
		?: throw MapException(AvailErrorCode.E_KEY_NOT_FOUND)

	/**
	 * Answer a map like this one but with [keyObject] associated with
	 * [newValueObject] instead of any existing mapping for keyObject. The
	 * original map can be destroyed or recycled if [canDestroy] is true and
	 * it's mutable.
	 *
	 * @param self
	 *   The map.
	 * @param keyObject
	 *   The key to add or replace.
	 * @param newValueObject
	 *   The new value to store under the provided key.
	 * @param canDestroy
	 *   Whether the given map may be recycled (if mutable).
	 * @return
	 *   The new map, possibly the given one if [canDestroy] is true.
	 */
	override fun o_MapAtPuttingCanDestroy(
		self: AvailObject,
		keyObject: A_BasicObject,
		newValueObject: A_BasicObject,
		canDestroy: Boolean
	): A_Map {
		val oldRoot = rootBin(self)
		val traversedKey: A_BasicObject = keyObject.traversed()
		val newRoot = oldRoot.mapBinAtHashPutLevelCanDestroy(
			traversedKey,
			traversedKey.hash(),
			newValueObject,
			0,
			canDestroy)
		if (canDestroy && isMutable) {
			setRootBin(self, newRoot)
			return self
		}
		if (isMutable) {
			self.makeImmutable()
		}
		return createFromBin(newRoot)
	}

	override fun o_MapAtReplacingCanDestroy(
		self: AvailObject,
		key: A_BasicObject,
		notFoundValue: A_BasicObject,
		canDestroy: Boolean,
		transformer: (AvailObject, AvailObject) -> A_BasicObject
	): A_Map {
		val oldRoot = rootBin(self)
		val traversedKey: A_BasicObject = key.traversed()
		val newRoot = oldRoot.mapBinAtHashReplacingLevelCanDestroy(
			traversedKey.cast(),
			traversedKey.hash(),
			notFoundValue.cast(),
			0,
			canDestroy,
			transformer)
		if (canDestroy && isMutable) {
			setRootBin(self, newRoot)
			return self
		}
		if (isMutable) {
			self.makeImmutable()
		}
		return createFromBin(newRoot)
	}

	/**
	 * Answer a set with all my keys.  Mark the keys as immutable because
	 * they'll be shared with the new set.
	 */
	override fun o_KeysAsSet(self: AvailObject): A_Set
	{
		return generateSetFrom(self.mapSize, self.mapIterable) {
			(key, _) -> key.makeImmutable()
		}
	}

	/**
	 * Answer a tuple with all my values.  Mark the values as immutable because
	 * they'll be shared with the new tuple.
	 */
	override fun o_ValuesAsTuple(self: AvailObject): A_Tuple
	{
		val mapIterable = self.mapIterable
		return generateObjectTupleFrom(self.mapSize) {
			mapIterable.next().value().makeImmutable() }
	}

	override fun o_MapWithoutKeyCanDestroy(
		self: AvailObject,
		keyObject: A_BasicObject,
		canDestroy: Boolean
	): A_Map {
		// Answer a map like this one but with keyObject removed from it. The
		// original map can be destroyed if canDestroy is true and it's mutable.
		if (!self.hasKey(keyObject)) {
			if (!canDestroy) {
				// Existing reference will be kept around.
				self.makeImmutable()
			}
			return self
		}
		val root = rootBin(self).mapBinRemoveKeyHashCanDestroy(
			keyObject, keyObject.hash(), canDestroy)
		if (canDestroy && isMutable) {
			setRootBin(self, root)
			return self
		}
		return createFromBin(root)
	}

	override fun o_HasKey(self: AvailObject, keyObject: A_BasicObject) =
		rootBin(self).mapBinAtHash(keyObject, keyObject.hash()) !== null

	override fun o_MapSize(self: AvailObject) = rootBin(self).mapBinSize

	override fun o_MapIterable(self: AvailObject): MapIterable =
		rootBin(self).mapBinIterable

	@ThreadSafe
	override fun o_SerializerOperation(self: AvailObject) =
		SerializerOperation.MAP

	override fun o_ShowValueInNameForDebugger(self: AvailObject) = false

	override fun o_WriteTo(self: AvailObject, writer: JSONWriter) =
		writer.writeObject {
			at("kind") { write("map") }
			if (self.kind().keyType.isSubtypeOf(stringType))
			{
				at("map") {
					writeObject {
						self.forEach { key, value ->
							key.writeTo(writer)
							value.writeTo(writer)
						}
					}
				}
			}
			else
			{
				at("bindings") {
					writeArray {
						self.forEach { key, value ->
							writeArray {
								key.writeTo(writer)
								value.writeTo(writer)
							}
						}
					}
				}
			}
		}

	override fun o_WriteSummaryTo(self: AvailObject, writer: JSONWriter) =
		writer.writeObject {
			at("kind") { write("map") }
			if (self.kind().keyType.isSubtypeOf(stringType))
			{
				at("map") {
					writeObject {
						self.forEach { key, value ->
							key.writeTo(writer)
							value.writeSummaryTo(writer)
						}
					}
				}
			}
			else
			{
				at("bindings") {
					writeArray {
						self.forEach { key, value ->
							writeArray {
								key.writeSummaryTo(writer)
								value.writeSummaryTo(writer)
							}
						}
					}
				}
			}
		}

	/**
	 * [Entry] exists solely to allow the "foreach" control structure to be used
	 * on a [map][MapDescriptor] by suitable use of [A_Map.mapIterable].
	 *
	 * @author Mark van Gulik &lt;mark@availlang.org&gt;
	 */
	class Entry
	{
		/**
		 * The key at some [MapIterable]'s current position.
		 */
		private var key: AvailObject? = null

		/**
		 * The hash of the key at some [MapIterable]'s current position.
		 */
		private var keyHash = 0

		/**
		 * The value associated with the key at some [MapIterable]'s
		 * current position.
		 */
		private var value: AvailObject? = null

		/**
		 * Update my fields.
		 *
		 * @param newKey The key to set.
		 * @param newKeyHash the hash of the key.
		 * @param newValue The value to set.
		 */
		fun setKeyAndHashAndValue(
			newKey: AvailObject?,
			newKeyHash: Int,
			newValue: AvailObject?
		) {
			key = newKey
			keyHash = newKeyHash
			value = newValue
		}

		/**
		 * Answer the [Entry]'s key.
		 *
		 * @return
		 *   The entry's key.
		 */
		fun key() = key!!

		/**
		 * Answer the [Entry]'s value.
		 *
		 * @return
		 *   The entry's value.
		 */
		fun value() = value!!

		/**
		 * Temporary Kotlin compatibility.
		 *
		 * @return The key.
		 */
		operator fun component1(): AvailObject {
			return key()
		}

		/**
		 * Temporary Kotlin compatibility.
		 *
		 * @return The value.
		 */
		operator fun component2(): AvailObject {
			return value()
		}

		/**
		 * Temporary Kotlin compatibility.
		 *
		 * @return The key's precomputed hash value.
		 */
		operator fun component3(): Int = keyHash
	}

	/**
	 * [MapIterable] is returned by [A_Map.mapIterable] to
	 * support use of Java's "foreach" control structure on [maps][A_Map].
	 *
	 * @constructor
	 *   Construct a new `MapIterable`.
	 *
	 * @author Mark van Gulik &lt;mark@availlang.org&gt;
	 */
	abstract class MapIterable
		protected constructor() : MutableIterator<Entry>, Iterable<Entry>
	{
		/**
		 * The [Entry] to be reused for each <key, value> pair while iterating
		 * over this [map][MapDescriptor].
		 */
		protected val entry = Entry()

		/**
		 * Convert trivially between an Iterable and an Iterator, since this
		 * class supports both protocols.
		 */
		override fun iterator(): MapIterable = this

		override fun remove() = throw UnsupportedOperationException()
	}

	override fun mutable() = mutable

	override fun immutable() = immutable

	override fun shared() = shared

	companion object
	{
		/**
		 * Extract the root [bin][MapBinDescriptor] from the
		 * [map][MapDescriptor].
		 *
		 * @param map
		 *   The map from which to extract the root bin.
		 * @return
		 *   The map's bin.
		 */
		private fun rootBin(map: A_Map): A_MapBin =
			(map as AvailObject).slot(ROOT_BIN)

		/**
		 * Replace the [map][A_Map]'s root [bin][MapBinDescriptor].
		 *
		 * @param map
		 *   The map (must not be an indirection).
		 * @param bin
		 *   The root bin for the map.
		 */
		private fun setRootBin(map: A_Map, bin: A_MapBin) =
			(map as AvailObject).setSlot(ROOT_BIN, bin)

		/** The mutable [MapDescriptor]. */
		private val mutable = MapDescriptor(MUTABLE)

		/** The immutable [MapDescriptor]. */
		private val immutable = MapDescriptor(IMMUTABLE)

		/** The shared [MapDescriptor]. */
		private val shared = MapDescriptor(SHARED)

		/**
		 * Create a new [map][A_Map] whose contents correspond to the specified
		 * [tuple][A_Tuple] of key-value bindings.
		 *
		 * @param tupleOfBindings
		 *   A tuple of key-value bindings, i.e. 2-element tuples.
		 * @return
		 *   A new map.
		 */
		fun mapWithBindings(tupleOfBindings: A_Tuple): A_Map = createFromBin(
			tupleOfBindings.fold(emptyLinearMapBin(0)) { root: A_MapBin, pair ->
				assert(pair.tupleSize == 2)
				val (key, value) = pair
				root.mapBinAtHashPutLevelCanDestroy(
					key.traversed(),
					key.hash(),
					value,
					0,
					true)
			})

		/**
		 * Create a new [map][A_Map] whose contents correspond to the specified
		 * vararg array of alternating keys and values.
		 *
		 * @param keysAndValues
		 *   A vararg [Array] of alternating keys and values.
		 * @return
		 *   A new map.
		 */
		fun mapFromPairs(vararg keysAndValues: A_BasicObject): A_Map {
			assert(keysAndValues.size and 1 == 0)
			return createFromBin(
				(keysAndValues.indices step 2).fold(emptyLinearMapBin(0)) {
					root: A_MapBin, i ->
					val key = keysAndValues[i].traversed()
					val value = keysAndValues[i + 1]
					root.mapBinAtHashPutLevelCanDestroy(
						key,
						key.hash(),
						value,
						0,
						true)
				})
		}

		/**
		 * Create a new [map][A_Map] based on the given
		 * [root&#32;bin][MapBinDescriptor].
		 *
		 * @param rootBin
		 *   The root bin to use in the new map.
		 * @return
		 *   A new mutable map.
		 */
		private fun createFromBin(rootBin: A_MapBin): A_Map =
			mutable.create { setRootBin(this, rootBin) }

		/**
		 * Combine the two [maps][A_Map] into a single map, destroying the
		 * destination if possible and appropriate.
		 *
		 * @param destination
		 *   The destination map.
		 * @param source
		 *   The source map.
		 * @param canDestroy
		 *   `true` if the operation is permitted to modify the destination map
		 *   in situ (if it is mutable), `false` otherwise.
		 * @return
		 *   The resultant map.
		 */
		@Suppress("unused")
		fun combineMapsCanDestroy(
			destination: A_Map,
			source: A_Map,
			canDestroy: Boolean
		): A_BasicObject {
			assert(destination.isMap)
			assert(source.isMap)
			if (!canDestroy)
			{
				destination.makeImmutable()
			}
			return when
			{
				source.sameAddressAs(destination) -> destination
				source.mapSize == 0 -> destination
				else -> source.mapIterable.fold(destination) { map, (k, v) ->
					map.mapAtPuttingCanDestroy(k, v, true)
				}
			}
		}

		/** The empty map. */
		val emptyMap: A_Map =
			createFromBin(emptyLinearMapBin(0)).let {
				it.hash()
				it.makeShared()
			}

		/**
		 * Answer the empty map.
		 *
		 * @return
		 *   The empty map.
		 */
		@ReferencedInGeneratedCode
		@JvmStatic
		fun emptyAvailMap() = emptyMap

		/** The [CheckedMethod] for [emptyMap]. */
		val emptyMapMethod = staticMethod(
			MapDescriptor::class.java,
			::emptyAvailMap.name,
			A_Map::class.java)
	}
}

