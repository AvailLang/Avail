/*
 * FusedPojoTypeDescriptor.kt
 * Copyright © 1993-2020, The Avail Foundation, LLC.
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
package com.avail.descriptor.types

import com.avail.annotations.ThreadSafe
import com.avail.descriptor.maps.A_Map
import com.avail.descriptor.maps.MapDescriptor
import com.avail.descriptor.maps.MapDescriptor.Companion.emptyMap
import com.avail.descriptor.pojos.PojoDescriptor
import com.avail.descriptor.pojos.RawPojoDescriptor.Companion.rawObjectClass
import com.avail.descriptor.representation.A_BasicObject
import com.avail.descriptor.representation.AbstractSlotsEnum
import com.avail.descriptor.representation.AvailObject
import com.avail.descriptor.representation.BitField
import com.avail.descriptor.representation.IntegerSlotsEnum
import com.avail.descriptor.representation.Mutability
import com.avail.descriptor.representation.NilDescriptor.Companion.nil
import com.avail.descriptor.representation.ObjectSlotsEnum
import com.avail.descriptor.sets.SetDescriptor
import com.avail.descriptor.tuples.A_Tuple
import com.avail.descriptor.tuples.A_Tuple.Companion.tupleAt
import com.avail.descriptor.tuples.A_Tuple.Companion.tupleSize
import com.avail.descriptor.tuples.StringDescriptor
import com.avail.descriptor.tuples.StringDescriptor.Companion.stringFrom
import com.avail.descriptor.tuples.TupleDescriptor
import com.avail.descriptor.tuples.TupleDescriptor.Companion.emptyTuple
import com.avail.descriptor.types.BottomPojoTypeDescriptor.Companion.pojoBottom
import com.avail.descriptor.types.FusedPojoTypeDescriptor.IntegerSlots.Companion.HASH_OR_ZERO
import com.avail.descriptor.types.FusedPojoTypeDescriptor.IntegerSlots.HASH_AND_MORE
import com.avail.descriptor.types.FusedPojoTypeDescriptor.ObjectSlots.JAVA_ANCESTORS
import com.avail.descriptor.types.FusedPojoTypeDescriptor.ObjectSlots.SELF_TYPE
import com.avail.descriptor.types.FusedPojoTypeDescriptor.ObjectSlots.TYPE_VARIABLES
import com.avail.serialization.SerializerOperation
import com.avail.utility.json.JSONWriter
import java.lang.reflect.Modifier
import java.lang.reflect.TypeVariable
import java.util.Comparator
import java.util.IdentityHashMap

/**
 * `FusedPojoTypeDescriptor` describes synthetic points in Avail's pojo type
 * hierarchy. This is a superset of Java's own reference type hierarchy. In
 * particular, the pojo type hierarchy includes type unions and type
 * intersections that may still conform to actual (but unspecified) Java classes
 * and interfaces. For instance, the type intersection of [Cloneable] and
 * [Serializable] describes **1)** any interface that extends both and **2)**
 * any class that implements both.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @constructor
 * Construct a new [FusedPojoTypeDescriptor].
 *
 * @param mutability
 *   The [mutability][Mutability] of the new descriptor.
 */
internal class FusedPojoTypeDescriptor constructor (mutability: Mutability)
	: PojoTypeDescriptor(
		mutability, ObjectSlots::class.java, IntegerSlots::class.java)
{
	/** The layout of the integer slots.  */
	internal enum class IntegerSlots : IntegerSlotsEnum
	{
		/**
		 * The low 32 bits are used for the [HASH_OR_ZERO], but the upper
		 * 32 can be used by other [BitField]s in subclasses.
		 */
		HASH_AND_MORE;

		companion object
		{
			/**
			 * A slot to hold the hash value, or zero if it has not been
			 * computed. The hash of an atom is a random number, computed once.
			 */
			@JvmField
			val HASH_OR_ZERO = BitField(HASH_AND_MORE, 0, 32)
		}
	}

	/** The layout of the object slots.  */
	internal enum class ObjectSlots : ObjectSlotsEnum
	{
		/**
		 * A [map][MapDescriptor] from [pojos][PojoDescriptor] that wrap
		 * [Java&#32;classes&#32;and&#32;interfaces][Class] to their
		 * [type&#32;parameterizations][TupleDescriptor]. The
		 * [keys][AvailObject.keysAsSet] constitute this type's complete
		 * [ancestry][SetDescriptor] of Java types.
		 */
		JAVA_ANCESTORS,

		/**
		 * A [map][MapDescriptor] from fully-qualified
		 * [type&#32;variable][TypeVariable] [names][StringDescriptor] to their
		 * [values][TypeDescriptor] in this [type][UnfusedPojoTypeDescriptor].
		 */
		TYPE_VARIABLES,

		/**
		 * The cached [self&#32;type][SelfPojoTypeDescriptor] of this
		 * [pojo&#32;type][UnfusedPojoTypeDescriptor].
		 */
		SELF_TYPE
	}

	override fun allowsImmutableToMutableReferenceInField(
		e: AbstractSlotsEnum): Boolean =
			e === HASH_AND_MORE
				|| e === TYPE_VARIABLES
				|| e === SELF_TYPE

	override fun o_EqualsPojoType(
		self: AvailObject,
		aPojoType: AvailObject): Boolean
	{
		if (aPojoType.isPojoSelfType)
		{
			return self.pojoSelfType().equalsPojoType(aPojoType)
		}
		if (!aPojoType.javaClass().equalsNil())
		{
			return false
		}
		val ancestors: A_Map = self.slot(JAVA_ANCESTORS)
		val otherAncestors: A_Map = aPojoType.javaAncestors()
		if (ancestors.mapSize() != otherAncestors.mapSize())
		{
			return false
		}
		for (ancestor in ancestors.keysAsSet())
		{
			if (!otherAncestors.hasKey(ancestor))
			{
				return false
			}
			val params: A_Tuple = ancestors.mapAt(ancestor)
			val otherParams: A_Tuple = otherAncestors.mapAt(ancestor)
			val limit = params.tupleSize()
			assert(limit == otherParams.tupleSize())
			for (i in 1 .. limit)
			{
				if (!params.tupleAt(i).equals(otherParams.tupleAt(i)))
				{
					return false
				}
			}
		}
		// The objects are known to be equal and not reference identical
		// (checked by a caller), so coalesce them if possible.
		if (!isShared)
		{
			aPojoType.makeImmutable()
			self.becomeIndirectionTo(aPojoType)
		}
		else if (!aPojoType.descriptor().isShared)
		{
			self.makeImmutable()
			aPojoType.becomeIndirectionTo(self)
		}
		return true
	}

	override fun o_Hash(self: AvailObject): Int
	{
		if (isShared)
		{
			synchronized(self) { return hash(self) }
		}
		return hash(self)
	}

	override fun o_IsAbstract(self: AvailObject): Boolean = true

	override fun o_IsPojoArrayType(self: AvailObject): Boolean = false

	override fun o_IsPojoFusedType(self: AvailObject): Boolean = true

	override fun o_JavaAncestors(self: AvailObject): AvailObject =
		self.slot(JAVA_ANCESTORS)

	override fun o_JavaClass(self: AvailObject): AvailObject = nil

	// TODO: [TLS] Answer the nearest mutual parent of the leaf types.
	override fun o_MarshalToJava(
		self: AvailObject,
		classHint: Class<*>?): Any? = Any::class.java

	/**
	 * Lazily compute the self type of the specified
	 * [object][FusedPojoTypeDescriptor].
	 *
	 * @param self
	 *   An object.
	 * @return
	 *   The self type.
	 */
	private fun pojoSelfType(self: AvailObject): AvailObject
	{
		var selfType = self.slot(SELF_TYPE)
		if (selfType.equalsNil())
		{
			selfType = SelfPojoTypeDescriptor.newSelfPojoType(
				nil, self.slot(
					JAVA_ANCESTORS).keysAsSet())
			if (isShared)
			{
				selfType = selfType.traversed().makeShared()
			}
			self.setSlot(SELF_TYPE, selfType)
		}
		return selfType
	}

	override fun o_PojoSelfType(self: AvailObject): A_Type
	{
		if (isShared)
		{
			synchronized(self) { return pojoSelfType(self) }
		}
		return pojoSelfType(self)
	}

	@ThreadSafe
	override fun o_SerializerOperation(self: AvailObject): SerializerOperation =
		SerializerOperation.FUSED_POJO_TYPE

	override fun o_TypeIntersectionOfPojoType(
		self: AvailObject,
		aPojoType: A_Type): A_Type
	{
		if (aPojoType.isPojoSelfType)
		{
			return self.pojoSelfType().typeIntersectionOfPojoType(aPojoType)
		}
		// A Java array type is effectively final, so the type intersection with
		// of a pojo array type and a singleton pojo type is pojo bottom.
		return if (aPojoType.isPojoArrayType)
		{
			pojoBottom()
		}
		else canonicalPojoType(
			aPojoType.typeIntersectionOfPojoFusedType(self),
			false)
	}

	override fun o_TypeIntersectionOfPojoFusedType(
		self: AvailObject,
		aFusedPojoType: A_Type): A_Type
	{
		val intersection = computeIntersection(self, aFusedPojoType)
		return if (intersection.equalsPojoBottomType())
		{
			pojoBottom()
		}
		else createFusedPojoType(intersection as A_Map)
		// The result will be a pojo fused type. Find the union of the key sets
		// and the intersection of their parameterizations.
	}

	override fun o_TypeIntersectionOfPojoUnfusedType(
		self: AvailObject,
		anUnfusedPojoType: A_Type): A_Type
	{
		val otherJavaClass =
			anUnfusedPojoType.javaClass().javaObjectNotNull<Class<*>>()
		val otherModifiers = otherJavaClass.modifiers
		// If the unfused pojo type's class is final, then the intersection is
		// pojo bottom.
		if (Modifier.isFinal(otherModifiers))
		{
			return pojoBottom()
		}
		// If the unfused pojo type is a class, then check that none of the
		// fused pojo type's ancestors are classes.
		if (!Modifier.isInterface(otherModifiers))
		{
			// If any of the fused pojo type's ancestors are Java classes, then
			// the intersection is pojo bottom.
			for (ancestor in
				self.slot(JAVA_ANCESTORS).keysAsSet())
			{
				// Ignore java.lang.Object.
				if (!ancestor.equals(rawObjectClass()))
				{
					val javaClass = ancestor.javaObjectNotNull<Class<*>>()
					val modifiers = javaClass.modifiers
					if (Modifier.isFinal(modifiers)
						|| !Modifier.isInterface(modifiers))
					{
						return pojoBottom()
					}
				}
			}
		}
		val intersection = computeIntersection(self, anUnfusedPojoType)
		return if (intersection.equalsPojoBottomType())
		{
			pojoBottom()
		}
		else createFusedPojoType(intersection as A_Map)
		// The result will be a pojo fused type. Find the union of the key sets
		// and the intersection of their parameterizations.
	}

	override fun o_TypeUnionOfPojoType(
		self: AvailObject,
		aPojoType: A_Type): A_Type =
			if (aPojoType.isPojoSelfType)
			{
				self.pojoSelfType().typeUnionOfPojoType(aPojoType)
			}
			else
			{
				canonicalPojoType(
					aPojoType.typeUnionOfPojoType(self),
					false)
			}

	override fun o_TypeUnionOfPojoFusedType(
		self: AvailObject,
		aFusedPojoType: A_Type): A_Type
	{
		val intersectionAncestors = computeUnion(
			self, aFusedPojoType)
		val javaClass = mostSpecificOf(
			intersectionAncestors.keysAsSet())
		// If the intersection contains a most specific type, then the answer is
		// not a fused pojo type; otherwise it is.
		return if (!javaClass.equalsNil())
		{
			UnfusedPojoTypeDescriptor
				.createUnfusedPojoType(javaClass, intersectionAncestors)
		}
		else
		{
			createFusedPojoType(intersectionAncestors)
		}
	}

	override fun o_TypeUnionOfPojoUnfusedType(
		self: AvailObject,
		anUnfusedPojoType: A_Type): A_Type
	{
		val intersectionAncestors = computeUnion(self, anUnfusedPojoType)
		val javaClass = mostSpecificOf(
			intersectionAncestors.keysAsSet())
		// If the intersection contains a most specific type, then the answer is
		// not a fused pojo type; otherwise it is.
		return if (!javaClass.equalsNil())
		{
			UnfusedPojoTypeDescriptor
				.createUnfusedPojoType(javaClass, intersectionAncestors)
		}
		else
		{
			createFusedPojoType(intersectionAncestors)
		}
	}

	/**
	 * Lazily compute the type variables of the specified
	 * [object][FusedPojoTypeDescriptor].
	 *
	 * @param self
	 *   An object.
	 * @return
	 *   The type variables.
	 */
	private fun typeVariables(self: AvailObject): A_Map
	{
		var typeVars: A_Map = self.slot(TYPE_VARIABLES)
		if (typeVars.equalsNil())
		{
			typeVars = emptyMap()
			for (entry in
				self.slot(JAVA_ANCESTORS).mapIterable())
			{
				val ancestor = entry.key().javaObjectNotNull<Class<*>>()
				val vars = ancestor.typeParameters
//				val vars: Array<TypeVariable<*>> = ancestor.typeParameters
				val typeArgs: A_Tuple = entry.value()
				assert(vars.size == typeArgs.tupleSize())
				for (i in vars.indices)
				{
					typeVars = typeVars.mapAtPuttingCanDestroy(
						stringFrom(
							ancestor.name + "." + vars[i].name),
						typeArgs.tupleAt(i + 1),
						true)
				}
				if (isShared)
				{
					typeVars = typeVars.traversed().makeShared()
				}
			}
			self.setSlot(TYPE_VARIABLES, typeVars)
		}
		return typeVars
	}

	override fun o_TypeVariables(self: AvailObject): A_Map
	{
		if (isShared)
		{
			synchronized(self) { return typeVariables(self) }
		}
		return typeVariables(self)
	}

	override fun printObjectOnAvoidingIndent(
		self: AvailObject,
		builder: StringBuilder,
		recursionMap: IdentityHashMap<A_BasicObject, Void>,
		indent: Int)
	{
		val ancestors: A_Map = self.slot(JAVA_ANCESTORS)
		val childless = mutableListOf<AvailObject>()
		childless.addAll(childlessAmong(ancestors.keysAsSet()))
		childless.sortWith(Comparator { o1: AvailObject, o2: AvailObject ->
			val c1 = o1.javaObjectNotNull<Class<*>>()
			val c2 = o2.javaObjectNotNull<Class<*>>()
			c1.name.compareTo(c2.name)
		})
		var firstChildless = true
		for (javaClass in childless)
		{
			if (!firstChildless)
			{
				builder.append(" ∩ ")
			}
			firstChildless = false
			builder.append(javaClass.javaObjectNotNull<Class<*>>().name)
			val params: A_Tuple =
				if (ancestors.hasKey(javaClass)) ancestors.mapAt(javaClass)
				else emptyTuple()
			if (params.tupleSize() != 0)
			{
				builder.append('<')
				var firstParam = true
				for (param in params)
				{
					if (!firstParam)
					{
						builder.append(", ")
					}
					firstParam = false
					param.printOnAvoidingIndent(builder, recursionMap, indent)
				}
				builder.append('>')
			}
		}
	}

	override fun o_WriteTo(self: AvailObject, writer: JSONWriter)
	{
		writer.startObject()
		writer.write("kind")
		writer.write("pojo type")
		writer.write("class")
		writer.write(self.toString())
		writer.endObject()
	}

	override fun mutable(): FusedPojoTypeDescriptor = mutable

	override fun immutable(): FusedPojoTypeDescriptor = immutable

	override fun shared(): FusedPojoTypeDescriptor = shared

	companion object
	{
		/**
		 * Lazily compute the hash of the specified
		 * [object][FusedPojoTypeDescriptor].
		 *
		 * @param self
		 *   An object.
		 * @return
		 *   The hash.
		 */
		private fun hash(self: AvailObject): Int
		{
			var hash = self.slot(HASH_OR_ZERO)
			if (hash == 0)
			{
				// Note that this definition produces a value compatible with a
				// pojo self type; this is necessary to permit comparison
				// between an unfused pojo type and its self type.
				hash =
					self.slot(JAVA_ANCESTORS).keysAsSet().hash() xor
						-0x5fea43bc
				self.setSlot(HASH_OR_ZERO, hash)
			}
			return hash
		}

		/** The mutable [FusedPojoTypeDescriptor].  */
		private val mutable = FusedPojoTypeDescriptor(Mutability.MUTABLE)

		/** The immutable [FusedPojoTypeDescriptor].  */
		private val immutable = FusedPojoTypeDescriptor(Mutability.IMMUTABLE)

		/** The shared [FusedPojoTypeDescriptor].  */
		private val shared = FusedPojoTypeDescriptor(Mutability.SHARED)

		/**
		 * Create a new [AvailObject] that represents an [unparameterized pojo
		 * type][FusedPojoTypeDescriptor].
		 *
		 * @param javaAncestors
		 *   A [map][MapDescriptor] from [pojos][PojoDescriptor] that wrap
		 *   [Java&#32;classes&#32;and&#32;interfaces][Class] to their
		 *   [type&#32;parameterizations][TupleDescriptor]. The
		 *   [keys][AvailObject.keysAsSet] constitute this type's complete
		 *   [ancestry][SetDescriptor] of Java types.
		 * @return
		 *   The requested pojo type.
		 */
		@JvmStatic
		fun createFusedPojoType(javaAncestors: A_Map?): AvailObject =
			mutable.createImmutable {
				setSlot(HASH_OR_ZERO, 0)
				setSlot(JAVA_ANCESTORS, javaAncestors!!)
				setSlot(TYPE_VARIABLES, nil)
				setSlot(SELF_TYPE, nil)
			}
	}
}
