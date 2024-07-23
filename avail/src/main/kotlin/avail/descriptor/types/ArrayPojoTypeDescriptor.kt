/*
 * ArrayPojoTypeDescriptor.kt
 * Copyright © 1993-2022, The Avail Foundation, LLC.
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
package avail.descriptor.types

import avail.annotations.ThreadSafe
import avail.descriptor.maps.A_Map
import avail.descriptor.maps.A_Map.Companion.keysAsSet
import avail.descriptor.maps.A_Map.Companion.mapAtPuttingCanDestroy
import avail.descriptor.maps.MapDescriptor
import avail.descriptor.maps.MapDescriptor.Companion.emptyMap
import avail.descriptor.pojos.PojoDescriptor
import avail.descriptor.pojos.RawPojoDescriptor.Companion.equalityPojo
import avail.descriptor.pojos.RawPojoDescriptor.Companion.rawObjectClass
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.A_BasicObject.Companion.synchronizeIf
import avail.descriptor.representation.AbstractSlotsEnum
import avail.descriptor.representation.AvailObject
import avail.descriptor.representation.AvailObject.Companion.combine2
import avail.descriptor.representation.BitField
import avail.descriptor.representation.IntegerSlotsEnum
import avail.descriptor.representation.Mutability
import avail.descriptor.representation.ObjectSlotsEnum
import avail.descriptor.sets.SetDescriptor
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.tuples.TupleDescriptor
import avail.descriptor.tuples.TupleDescriptor.Companion.emptyTuple
import avail.descriptor.types.A_Type.Companion.contentType
import avail.descriptor.types.A_Type.Companion.isSubtypeOf
import avail.descriptor.types.A_Type.Companion.lowerBound
import avail.descriptor.types.A_Type.Companion.sizeRange
import avail.descriptor.types.A_Type.Companion.typeIntersection
import avail.descriptor.types.A_Type.Companion.typeIntersectionOfPojoType
import avail.descriptor.types.A_Type.Companion.typeUnionOfPojoFusedType
import avail.descriptor.types.A_Type.Companion.typeUnionOfPojoType
import avail.descriptor.types.A_Type.Companion.typeUnionOfPojoUnfusedType
import avail.descriptor.types.A_Type.Companion.upperBound
import avail.descriptor.types.ArrayPojoTypeDescriptor.IntegerSlots.Companion.HASH_OR_ZERO
import avail.descriptor.types.ArrayPojoTypeDescriptor.IntegerSlots.HASH_AND_MORE
import avail.descriptor.types.ArrayPojoTypeDescriptor.ObjectSlots.CONTENT_TYPE
import avail.descriptor.types.ArrayPojoTypeDescriptor.ObjectSlots.JAVA_ANCESTORS
import avail.descriptor.types.ArrayPojoTypeDescriptor.ObjectSlots.SIZE_RANGE
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.wholeNumbers
import avail.descriptor.types.PrimitiveTypeDescriptor.Types
import avail.exceptions.unsupported
import avail.serialization.SerializerOperation
import org.availlang.json.JSONWriter
import java.io.Serializable
import java.lang.reflect.Array
import java.util.IdentityHashMap

/**
 * `ArrayPojoTypeDescriptor` describes Java array types. A Java array type
 * extends [java.lang.Object][Object] and implements
 * [Cloneable][java.lang.Cloneable] and [Serializable]. It has an element type
 * and a fixed size.
 *
 * Avail expands upon these features in two ways. First, a pojo array type may
 * have any [Avail type][TypeDescriptor] as its element type; this is, of
 * course, a superset of pojo types. Second, it may express a range of sizes,
 * not just a single fixed size; this is analogous to the size ranges supported
 * by [tuple types][TupleTypeDescriptor], [set types][SetTypeDescriptor], and
 * [map&#32;types][MapTypeDescriptor].
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @constructor
 *   Construct a new `ArrayPojoTypeDescriptor`.
 *
 * @param mutability
 *   The [mutability][Mutability] of the new descriptor.
 */
internal class ArrayPojoTypeDescriptor
private constructor(
	mutability: Mutability
) : PojoTypeDescriptor(
	mutability, ObjectSlots::class.java, IntegerSlots::class.java)
{
	/**
	 * `PojoArray` mimics the type properties of Java array types. It extends
	 * [Object] and implements [Cloneable][java.lang.Cloneable] and
	 * [Serializable], as required by the Java language specification. The type
	 * parameter is used to specify the element type.
	 *
	 * @author Todd L Smith &lt;todd@availlang.org&gt;
	 * @param T
	 *   The element type.
	 */
	internal abstract class PojoArray<T> : Cloneable, Serializable

	/** The layout of the integer slots. */
	internal enum class IntegerSlots : IntegerSlotsEnum
	{
		/**
		 * The low 32 bits are used for the [HASH_OR_ZERO], but the upper 32 can
		 * be used by other [BitField]s in subclasses.
		 */
		HASH_AND_MORE;

		companion object
		{
			/**
			 * A slot to hold the hash value, or zero if it has not been
			 * computed. The hash of an atom is a random number, computed once.
			 */
			val HASH_OR_ZERO = BitField(HASH_AND_MORE, 0, 32) { null }
		}
	}

	/** The layout of the object slots. */
	internal enum class ObjectSlots : ObjectSlotsEnum
	{
		/**
		 * A lazy [map][MapDescriptor] from [pojos][PojoDescriptor] that wrap
		 * [Java&#32;classes&#32;and&#32;interfaces][Class] to their [type
		 * parameterizations][TupleDescriptor]. The [keys][A_Map.keysAsSet]
		 * constitute this type's complete [ancestry][SetDescriptor] of Java
		 * types.
		 */
		JAVA_ANCESTORS,

		/**
		 * The [type][TypeDescriptor] of elements that may be read from
		 * instances of this type. (We say "read" because Java incorrectly
		 * treats arrays as though they are covariant data types.)
		 */
		CONTENT_TYPE,

		/**
		 * An [integer range][IntegerRangeTypeDescriptor] that specifies all
		 * allowed array sizes for instances of this type.
		 */
		SIZE_RANGE
	}

	override fun allowsImmutableToMutableReferenceInField(
		e: AbstractSlotsEnum
	): Boolean = e === HASH_AND_MORE

	override fun o_ContentType(self: AvailObject): A_Type =
		self[CONTENT_TYPE]

	override fun o_EqualsPojoType(
		self: AvailObject,
		aPojoType: AvailObject): Boolean
	{
		when
		{
			aPojoType.equalsPojoBottomType() -> return false
			aPojoType.isPojoSelfType ->
				return self.pojoSelfType().equalsPojoType(aPojoType)
			!self[SIZE_RANGE].equals(aPojoType.sizeRange)
				|| !self[CONTENT_TYPE]
					.equals(aPojoType.contentType) -> return false
			// The objects are known to be equal and not reference identical
			// (checked by a caller), so coalesce them if possible.
			!isShared ->
			{
				aPojoType.makeImmutable()
				self.becomeIndirectionTo(aPojoType)
			}
			!aPojoType.descriptor().isShared ->
			{
				self.makeImmutable()
				aPojoType.becomeIndirectionTo(self)
			}
		}
		return true
	}

	override fun o_Hash(self: AvailObject): Int =
		self.synchronizeIf(isShared) { hash(self) }

	override fun o_IsAbstract(self: AvailObject): Boolean = false

	override fun o_IsPojoArrayType(self: AvailObject): Boolean = true

	override fun o_IsPojoFusedType(self: AvailObject): Boolean = false

	override fun o_JavaAncestors(self: AvailObject): AvailObject =
		self[JAVA_ANCESTORS]

	override fun o_JavaClass(self: AvailObject): AvailObject =
		equalityPojo(PojoArray::class.java)

	override fun o_MarshalToJava(
		self: AvailObject,
		classHint: Class<*>?): Any
	{
		val elementType: A_BasicObject = self[CONTENT_TYPE]
		return Array.newInstance(
			elementType.marshalToJava(classHint) as Class<*>?, 0)
				.javaClass
	}

	override fun o_PojoSelfType(self: AvailObject): A_Type =
		SelfPojoTypeDescriptor.newSelfPojoType(
			equalityPojo(PojoArray::class.java),
			self[JAVA_ANCESTORS])

	override fun o_SizeRange(self: AvailObject): A_Type =
		self[SIZE_RANGE]

	@ThreadSafe
	override fun o_SerializerOperation(self: AvailObject): SerializerOperation =
		SerializerOperation.ARRAY_POJO_TYPE

	override fun o_TypeIntersectionOfPojoType(
		self: AvailObject,
		aPojoType: A_Type
	): A_Type = when
	{
		aPojoType.isPojoSelfType ->
			self.pojoSelfType().typeIntersectionOfPojoType(aPojoType)
		// Compute the type intersection of the two pojo array types.
		aPojoType.isPojoArrayType -> arrayPojoType(
			self[CONTENT_TYPE].typeIntersection(aPojoType.contentType),
			self[SIZE_RANGE].typeIntersection(aPojoType.sizeRange))
		// A Java array type is effectively final, so the type intersection of a
		// pojo array type and a singleton pojo type is pojo bottom.
		else -> BottomPojoTypeDescriptor.pojoBottom()
	}

	override fun o_TypeIntersectionOfPojoFusedType(
		self: AvailObject,
		aFusedPojoType: A_Type
	): A_Type = unsupported

	override fun o_TypeIntersectionOfPojoUnfusedType(
		self: AvailObject,
		anUnfusedPojoType: A_Type
	): A_Type = unsupported

	override fun o_TypeUnionOfPojoType(
		self: AvailObject,
		aPojoType: A_Type): A_Type
	{
		return if (aPojoType.isPojoSelfType)
		{
			self.pojoSelfType().typeUnionOfPojoType(aPojoType)
		}
		else canonicalPojoType(
			aPojoType.typeUnionOfPojoFusedType(self),
			false)
	}

	override fun o_TypeUnionOfPojoFusedType(
		self: AvailObject,
		aFusedPojoType: A_Type): A_Type
	{
		val intersectionAncestors = computeUnion(
			self, aFusedPojoType)
		val javaClass = mostSpecificOf(
			intersectionAncestors.keysAsSet)
		// If the intersection contains a most specific type, then the answer is
		// not a fused pojo type; otherwise it is.
		return if (javaClass.notNil)
		{
			UnfusedPojoTypeDescriptor.createUnfusedPojoType(
				javaClass, intersectionAncestors)
		}
		else
		{
			FusedPojoTypeDescriptor.createFusedPojoType(intersectionAncestors)
		}
	}

	override fun o_TypeUnionOfPojoUnfusedType(
		self: AvailObject,
		anUnfusedPojoType: A_Type): A_Type
	{
		if (anUnfusedPojoType.isPojoSelfType)
		{
			return self.pojoSelfType().typeUnionOfPojoUnfusedType(
				anUnfusedPojoType)
		}
		val intersectionAncestors = computeUnion(
			self, anUnfusedPojoType)
		val javaClass = mostSpecificOf(
			intersectionAncestors.keysAsSet)
		// If the intersection contains a most specific type, then the answer is
		// not a fused pojo type; otherwise it is.
		return if (javaClass.notNil)
		{
			UnfusedPojoTypeDescriptor.createUnfusedPojoType(
				javaClass, intersectionAncestors)
		}
		else
		{
			FusedPojoTypeDescriptor.createFusedPojoType(intersectionAncestors)
		}
	}

	override fun o_TypeVariables(self: AvailObject): A_Map = emptyMap

	override fun printObjectOnAvoidingIndent(
		self: AvailObject,
		builder: StringBuilder,
		recursionMap: IdentityHashMap<A_BasicObject, Void>,
		indent: Int)
	{
		self[CONTENT_TYPE].printOnAvoidingIndent(
			builder, recursionMap, indent)
		builder.append('[')
		val range = self[SIZE_RANGE]
		when
		{
			range.lowerBound.equals(range.upperBound) ->
			{
				range.lowerBound.printOnAvoidingIndent(
					builder, recursionMap, indent)
			}
			wholeNumbers.isSubtypeOf(range) ->
			{
				// This is the most common range, as it corresponds with all real
				// Java array types.
			}
			else ->
			{
				range.lowerBound.printOnAvoidingIndent(
					builder, recursionMap, indent)
				builder.append("..")
				range.upperBound.printOnAvoidingIndent(
					builder, recursionMap, indent)
			}
		}
		builder.append(']')
	}

	override fun o_WriteTo(self: AvailObject, writer: JSONWriter)
	{
		writer.startObject()
		writer.write("kind")
		writer.write("array pojo type")
		writer.write("content type")
		self[CONTENT_TYPE].writeTo(writer)
		writer.write("size range")
		self[SIZE_RANGE].writeTo(writer)
		writer.endObject()
	}

	override fun mutable(): ArrayPojoTypeDescriptor = mutable

	override fun immutable(): ArrayPojoTypeDescriptor = immutable

	override fun shared(): ArrayPojoTypeDescriptor = shared

	companion object
	{
		/**
		 * Lazily compute and install the hash of the
		 * [object][ArrayPojoTypeDescriptor].
		 *
		 * @param self
		 *   An object.
		 * @return
		 *   The hash.
		 */
		private fun hash(self: AvailObject): Int
		{
			var hash = self[HASH_OR_ZERO]
			if (hash == 0)
			{
				// Note that this definition produces a value compatible with a
				// pojo self type; this is necessary to permit comparison
				// between an unfused pojo type and its self type.
				hash = combine2(
					self[JAVA_ANCESTORS].keysAsSet.hash(), -0x5fea43bc)
				self[HASH_OR_ZERO] = hash
			}
			return hash
		}

		/** The mutable [ArrayPojoTypeDescriptor]. */
		private val mutable = ArrayPojoTypeDescriptor(Mutability.MUTABLE)

		/** The immutable [ArrayPojoTypeDescriptor]. */
		private val immutable = ArrayPojoTypeDescriptor(Mutability.IMMUTABLE)

		/** The shared [ArrayPojoTypeDescriptor]. */
		private val shared = ArrayPojoTypeDescriptor(Mutability.SHARED)

		/**
		 * The [map][MapDescriptor] used by
		 * [array&#32;pojo&#32;types][ArrayPojoTypeDescriptor].  Note that this
		 * map does not contain the entry for [PojoArray], as this has to be
		 * specialized per pojo array type.
		 */
		private val arrayBaseAncestorMap: A_Map

		/**
		 * Create a new [AvailObject] that represents a
		 * [pojo&#32;array&#32;type][ArrayPojoTypeDescriptor].
		 *
		 * @param elementType
		 *   The [type][TypeDescriptor] of elements that may be read from
		 *   instances of this type. (We say "read" because Java incorrectly
		 *   treats arrays as though they are covariant data types.)
		 * @param sizeRange
		 *   An [integer range][IntegerRangeTypeDescriptor] that specifies all
		 *   allowed array sizes for instances of this type. This must be a
		 *   subtype of [whole number][IntegerRangeTypeDescriptor.wholeNumbers].
		 * @return
		 *   The requested pojo array type.
		 */
		fun arrayPojoType(
			elementType: A_Type,
			sizeRange: A_Type): AvailObject
		{
			var javaAncestors = arrayBaseAncestorMap
			javaAncestors = javaAncestors.mapAtPuttingCanDestroy(
				equalityPojo(PojoArray::class.java),
				tuple(elementType),
				false)
			return mutable.createImmutable {
				setSlot(JAVA_ANCESTORS, javaAncestors)
				setSlot(CONTENT_TYPE, elementType)
				setSlot(SIZE_RANGE, sizeRange)
			}
		}

		init
		{
			var javaAncestors = emptyMap
			javaAncestors = javaAncestors.mapAtPuttingCanDestroy(
				rawObjectClass(),
				emptyTuple,
				true)
			javaAncestors = javaAncestors.mapAtPuttingCanDestroy(
				equalityPojo(Cloneable::class.java),
				emptyTuple,
				true)
			javaAncestors = javaAncestors.mapAtPuttingCanDestroy(
				equalityPojo(Serializable::class.java),
				emptyTuple,
				true)
			arrayBaseAncestorMap = javaAncestors.makeShared()
		}

		/** The most general [pojo&#32;array&#32;type][PojoTypeDescriptor]. */
		val mostGeneralType: A_Type =
			pojoArrayType(Types.ANY.o, wholeNumbers).makeShared()
	}
}
