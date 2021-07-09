/*
 * SelfPojoTypeDescriptor.kt
 * Copyright © 1993-2021, The Avail Foundation, LLC.
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
import com.avail.descriptor.maps.MapDescriptor.Companion.emptyMap
import com.avail.descriptor.pojos.PojoDescriptor
import com.avail.descriptor.pojos.RawPojoDescriptor
import com.avail.descriptor.pojos.RawPojoDescriptor.Companion.equalityPojo
import com.avail.descriptor.representation.A_BasicObject
import com.avail.descriptor.representation.AvailObject
import com.avail.descriptor.representation.Mutability
import com.avail.descriptor.representation.NilDescriptor
import com.avail.descriptor.representation.ObjectSlotsEnum
import com.avail.descriptor.sets.A_Set
import com.avail.descriptor.sets.A_Set.Companion.setIntersectionCanDestroy
import com.avail.descriptor.sets.A_Set.Companion.setUnionCanDestroy
import com.avail.descriptor.sets.A_Set.Companion.setWithElementCanDestroy
import com.avail.descriptor.sets.SetDescriptor
import com.avail.descriptor.sets.SetDescriptor.Companion.emptySet
import com.avail.descriptor.tuples.A_String
import com.avail.descriptor.tuples.A_Tuple
import com.avail.descriptor.tuples.A_Tuple.Companion.tupleAt
import com.avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import com.avail.descriptor.tuples.StringDescriptor.Companion.stringFrom
import com.avail.descriptor.tuples.TupleDescriptor
import com.avail.descriptor.types.ArrayPojoTypeDescriptor.PojoArray
import com.avail.descriptor.types.BottomPojoTypeDescriptor.Companion.pojoBottom
import com.avail.descriptor.types.SelfPojoTypeDescriptor.ObjectSlots.JAVA_ANCESTORS
import com.avail.descriptor.types.SelfPojoTypeDescriptor.ObjectSlots.JAVA_CLASS
import com.avail.serialization.SerializerOperation
import java.lang.reflect.Modifier
import java.util.IdentityHashMap

/**
 * `SelfPojoTypeDescriptor` describes the self type of a Java class or
 * interface. In the pojo implementation, any Java class or interface that
 * depends recursively on itself through type parameterization of self,
 * superclass, or superinterface uses a pojo self type. [java.lang.Enum][Enum]
 * is a famous example from the Java library: its type parameter, `E`, extends
 * `Enum`'s self type. A pojo self type is used to break the recursive
 * dependency.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @constructor
 * Construct a new `SelfPojoTypeDescriptor`.
 *
 * @param mutability
 *   The [mutability][Mutability] of the new descriptor.
 */
class SelfPojoTypeDescriptor constructor(mutability: Mutability)
	: PojoTypeDescriptor(mutability, ObjectSlots::class.java, null)
{
	/** The layout of the object slots.  */
	internal enum class ObjectSlots : ObjectSlotsEnum
	{
		/**
		 * A [raw pojo][RawPojoDescriptor] that wraps the
		 * [Java&#32;class&#32;or&#32;interface][Class] represented by this
		 * [pojo&#32;type][UnfusedPojoTypeDescriptor].
		 */
		JAVA_CLASS,

		/**
		 * A [set][SetDescriptor] of [pojos][PojoDescriptor] that wrap [Java
		 * classes and interfaces][Class]. This constitutes this type's complete
		 * ancestry of Java types. There are no [type
		 * parameterization][TypeDescriptor] [tuples][TupleDescriptor] because
		 * no Java type may appear multiply in the ancestry of any other Java
		 * type with different type parameterizations, thereby permitting pojo
		 * self types to omit type parameterization information.
		 */
		JAVA_ANCESTORS
	}

	override fun o_JavaAncestors(self: AvailObject): AvailObject =
		self.slot(JAVA_ANCESTORS)

	override fun o_JavaClass(self: AvailObject): AvailObject =
		self.slot(JAVA_CLASS)

	override fun o_EqualsPojoType(
		self: AvailObject,
		aPojoType: AvailObject): Boolean
	{
		// Callers have ensured that aPojoType is either an unfused pojo type
		// or a self type.
		val other: A_BasicObject = aPojoType.pojoSelfType()
		return (self.slot(JAVA_CLASS).equals(other.javaClass())
		        && self.slot(JAVA_ANCESTORS).equals(other.javaAncestors()))
	}

	// Note that this definition produces a value compatible with an unfused
	// pojo type; this is necessary to permit comparison between an unfused
	// pojo type and its self type.
	override fun o_Hash(self: AvailObject): Int =
		self.slot(JAVA_ANCESTORS).hash() xor -0x5fea43bc

	override fun o_IsAbstract(self: AvailObject): Boolean
	{
		val javaClass: A_BasicObject = self.slot(JAVA_CLASS)
		return (javaClass.isNil
			|| Modifier.isAbstract(
			javaClass.javaObjectNotNull<Class<*>>().modifiers))
	}

	override fun o_IsPojoArrayType(self: AvailObject): Boolean =
		self.slot(JAVA_CLASS)
			.equals(equalityPojo(PojoArray::class.java))

	override fun o_IsPojoFusedType(self: AvailObject): Boolean =
		self.slot(JAVA_CLASS).isNil

	override fun o_IsPojoSelfType(self: AvailObject): Boolean = true

	override fun o_IsSupertypeOfPojoType(
		self: AvailObject,
		aPojoType: A_Type): Boolean
	{
		// Check type compatibility by computing the set intersection of the
		// ancestry of the arguments. If the result is not equal to the
		// ancestry of object, then object is not a supertype of aPojoType.
		val ancestors: A_Set = self.slot(JAVA_ANCESTORS)
		val otherAncestors: A_Set = aPojoType.pojoSelfType().javaAncestors()
		val intersection =
			ancestors.setIntersectionCanDestroy(otherAncestors, false)
		return ancestors.equals(intersection)
	}

	override fun o_PojoSelfType(self: AvailObject): A_Type = self

	override fun o_MakeImmutable(self: AvailObject): AvailObject =
		if (isMutable)
		{
			// Make the object shared, since there's not an immutable variant.
			self.makeShared()
		}
		else self

	override fun o_MarshalToJava(self: AvailObject, classHint: Class<*>?): Any?
	{
		val javaClass: A_BasicObject = self.slot(JAVA_CLASS)
		return if (javaClass.isNil)
		{
			// TODO: [TLS] Answer the nearest mutual parent of the leaf types.
			Any::class.java
		}
		else javaClass.javaObject<Any>()
	}

	@ThreadSafe
	override fun o_SerializerOperation(self: AvailObject): SerializerOperation =
		SerializerOperation.SELF_POJO_TYPE_REPRESENTATIVE

	override fun o_TypeIntersectionOfPojoType(
		self: AvailObject,
		aPojoType: A_Type): A_Type
	{
		val other = aPojoType.pojoSelfType()
		val ancestors: A_Set = self.slot(JAVA_ANCESTORS)
		val otherAncestors: A_Set = other.javaAncestors()
		for (ancestor in ancestors)
		{
			val javaClass = ancestor.javaObjectNotNull<Class<*>>()
			val modifiers = javaClass.modifiers
			if (Modifier.isFinal(modifiers))
			{
				return pojoBottom()
			}
		}
		for (ancestor in otherAncestors)
		{
			val javaClass = ancestor.javaObjectNotNull<Class<*>>()
			val modifiers = javaClass.modifiers
			if (Modifier.isFinal(modifiers))
			{
				return pojoBottom()
			}
		}
		return newSelfPojoType(
			NilDescriptor.nil,
			ancestors.setUnionCanDestroy(otherAncestors, false))
	}

	override fun o_TypeIntersectionOfPojoFusedType(
		self: AvailObject,
		aFusedPojoType: A_Type): A_Type
	{
		unsupportedOperation()
	}

	override fun o_TypeIntersectionOfPojoUnfusedType(
		self: AvailObject,
		anUnfusedPojoType: A_Type): A_Type
	{
		unsupportedOperation()
	}

	override fun o_TypeUnionOfPojoType(
		self: AvailObject,
		aPojoType: A_Type): A_Type
	{
		val intersection =
			self.slot(JAVA_ANCESTORS).setIntersectionCanDestroy(
				aPojoType.pojoSelfType().javaAncestors(), false)
		return newSelfPojoType(mostSpecificOf(intersection), intersection)
	}

	override fun o_TypeUnionOfPojoFusedType(
		self: AvailObject,
		aFusedPojoType: A_Type): A_Type
	{
		unsupportedOperation()
	}

	override fun o_TypeUnionOfPojoUnfusedType(
		self: AvailObject,
		anUnfusedPojoType: A_Type): A_Type
	{
		unsupportedOperation()
	}

	override fun o_TypeVariables(self: AvailObject): A_Map = emptyMap

	override fun printObjectOnAvoidingIndent(
		self: AvailObject,
		builder: StringBuilder,
		recursionMap: IdentityHashMap<A_BasicObject, Void>,
		indent: Int)
	{
		val javaClass: A_BasicObject = self.slot(JAVA_CLASS)
		if (javaClass.notNil)
		{
			builder.append(javaClass.javaObjectNotNull<Class<*>>().name)
		}
		else
		{
			val ancestors: A_Set = self.slot(JAVA_ANCESTORS)
			val childless = childlessAmong(ancestors).sortedBy {
				it.javaObjectNotNull<Class<*>>().name
			}
			builder.append('(')
			var first = true
			for (aClass in childless)
			{
				if (!first)
				{
					builder.append(" ∩ ")
				}
				first = false
				builder.append(aClass.javaObjectNotNull<Class<*>>().name)
			}
			builder.append(')')
		}
		builder.append("'s self type")
	}

	override fun mutable(): SelfPojoTypeDescriptor = mutable

	// There is no immutable descriptor.
	override fun immutable(): SelfPojoTypeDescriptor = shared

	override fun shared(): SelfPojoTypeDescriptor = shared

	companion object
	{
		/** The mutable [SelfPojoTypeDescriptor].  */
		private val mutable = SelfPojoTypeDescriptor(Mutability.MUTABLE)

		/** The shared [SelfPojoTypeDescriptor].  */
		private val shared = SelfPojoTypeDescriptor(Mutability.SHARED)

		/**
		 * Create a new [AvailObject] that represents a
		 * [pojo&#32;self&#32;type][SelfPojoTypeDescriptor].
		 *
		 * @param javaClass
		 *   A [raw&#32;pojo][RawPojoDescriptor] that wraps the
		 *   [Java&#32;class&#32;or&#32;interface][Class] represented by this
		 *   pojo self type.
		 * @param javaAncestors
		 *   A [set][SetDescriptor] of [pojos][PojoDescriptor] that wrap
		 *   [Java&#32;classes&#32;and&#32;interfaces][Class]. This constitutes
		 *   this type's complete ancestry of Java types. There are no
		 *   [type&#32;parameterization][TypeDescriptor]
		 *   [tuples][TupleDescriptor] because no Java type may appear multiply
		 *   in the ancestry of any other Java type with different type
		 *   parameterizations, thereby permitting pojo self types to omit type
		 *   parameterization information.
		 * @return
		 *   The requested pojo type.
		 */
		fun newSelfPojoType(
			javaClass: AvailObject?,
			javaAncestors: A_Set?
		): AvailObject = mutable.createImmutable {
			setSlot(JAVA_CLASS, javaClass!!)
			setSlot(JAVA_ANCESTORS, javaAncestors!!)
		}

		/**
		 * Convert a self pojo type to a 2-tuple holding the main class name (or
		 * `null`) and a set of ancestor class names.
		 *
		 * @param selfPojo
		 *   The self pojo to convert.
		 * @return
		 *   A 2-tuple suitable for serialization.
		 */
		fun pojoSerializationProxy(
			selfPojo: A_BasicObject): A_Tuple
		{
			assert(selfPojo.isPojoSelfType)
			val pojoClass = selfPojo.javaClass()
			val mainClassName = if (pojoClass.isNil)
			{
				NilDescriptor.nil
			}
			else
			{
				val javaClass = pojoClass.javaObjectNotNull<Class<*>>()
				stringFrom(javaClass.name)
			}
			var ancestorNames = emptySet
			for (ancestor in selfPojo.javaAncestors())
			{
				val javaClass = ancestor.javaObjectNotNull<Class<*>>()
				ancestorNames = ancestorNames.setWithElementCanDestroy(
					stringFrom(javaClass.name), true)
			}
			return tuple(mainClassName, ancestorNames)
		}

		/**
		 * Convert a proxy previously created by [pojoSerializationProxy] back
		 * into a self pojo type.
		 *
		 * @param selfPojoProxy
		 *   A 2-tuple with the class name (or null) and a set of ancestor class
		 *   names.
		 * @param classLoader
		 *   The [ClassLoader] used to load any mentioned classes.
		 * @return
		 *   A self pojo type.
		 * @throws ClassNotFoundException
		 *   If a class can't be loaded.
		 */
		@Throws(ClassNotFoundException::class)
		fun pojoFromSerializationProxy(
			selfPojoProxy: A_Tuple,
			classLoader: ClassLoader): AvailObject
		{
			val className: A_String = selfPojoProxy.tupleAt(1)
			val mainRawType = if (className.isNil)
			{
				NilDescriptor.nil
			}
			else
			{
				val mainClass = Class.forName(
					className.asNativeString(), true, classLoader)
				equalityPojo(mainClass)
			}
			var ancestorTypes = emptySet
			for (ancestorClassName in selfPojoProxy.tupleAt(2))
			{
				val ancestorClass = Class.forName(
					ancestorClassName.asNativeString(), true, classLoader)
				ancestorTypes = ancestorTypes.setWithElementCanDestroy(
					equalityPojo(ancestorClass), true)
			}
			return newSelfPojoType(mainRawType, ancestorTypes)
		}
	}
}
