/*
 * BottomPojoTypeDescriptor.kt
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
import avail.descriptor.pojos.RawPojoDescriptor.Companion.rawNullPojo
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.AvailObject
import avail.descriptor.representation.Mutability
import avail.descriptor.representation.NilDescriptor.Companion.nil
import avail.descriptor.types.A_Type.Companion.isSupertypeOfPojoBottomType
import avail.descriptor.types.BottomTypeDescriptor.Companion.bottom
import avail.exceptions.unsupported
import avail.serialization.SerializerOperation
import org.availlang.json.JSONWriter
import java.util.IdentityHashMap

/**
 * `BottomPojoTypeDescriptor` describes the type of Java `null`, which is
 * implicitly an instance of every Java reference type. It therefore describes
 * the most specific Java reference type. Its only proper subtype is Avail's own
 * [bottom type][BottomTypeDescriptor].
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @constructor
 * Construct a new [BottomPojoTypeDescriptor].
 *
 * @param mutability
 *   The [mutability][Mutability] of the new descriptor.
 */
class BottomPojoTypeDescriptor
constructor(mutability: Mutability) : PojoTypeDescriptor(mutability, null, null)
{
	override fun o_EqualsPojoBottomType(self: AvailObject): Boolean = true

	override fun o_EqualsPojoType(
		self: AvailObject,
		aPojoType: AvailObject): Boolean = aPojoType.equalsPojoBottomType()

	override fun o_Hash(self: AvailObject): Int = 0x496FFE01

	// Pojo bottom has an instance: null.
	override fun o_IsAbstract(self: AvailObject): Boolean = false

	// Pojo bottom is the most specific pojo type, so it is also a pojo
	// array type.
	override fun o_IsPojoArrayType(self: AvailObject): Boolean = true

	// Pojo bottom is the intersection of any two unrelated pojo types, so
	// it is a pojo fusion type.
	override fun o_IsPojoFusedType(self: AvailObject): Boolean = true

	override fun o_IsSubtypeOf(
		self: AvailObject,
		aType: A_Type): Boolean = aType.isSupertypeOfPojoBottomType(self)

	override fun o_IsSupertypeOfPojoType(
		self: AvailObject,
		aPojoType: A_Type): Boolean = aPojoType.equalsPojoBottomType()

	override fun o_JavaAncestors(self: AvailObject): AvailObject = nil

	override fun o_JavaClass(self: AvailObject): AvailObject = rawNullPojo()

	// The pojo bottom type is its own self type.
	override fun o_PojoSelfType(self: AvailObject): A_Type = self

	override fun o_MarshalToJava(
		self: AvailObject,
		classHint: Class<*>?) = Any::class.java

	override fun o_TypeIntersectionOfPojoType(
		self: AvailObject,
		aPojoType: A_Type): A_Type = self

	override fun o_TypeIntersectionOfPojoFusedType(
		self: AvailObject,
		aFusedPojoType: A_Type
	): A_Type = unsupported

	override fun o_TypeIntersectionOfPojoUnfusedType(
		self: AvailObject,
		anUnfusedPojoType: A_Type): A_Type
	{
		unsupported
	}

	override fun o_TypeUnionOfPojoType(
		self: AvailObject,
		aPojoType: A_Type): A_Type = aPojoType

	override fun o_TypeUnionOfPojoFusedType(
		self: AvailObject,
		aFusedPojoType: A_Type
	): A_Type = unsupported

	override fun o_TypeUnionOfPojoUnfusedType(
		self: AvailObject,
		anUnfusedPojoType: A_Type
	): A_Type = unsupported

	override fun o_TypeVariables(self: AvailObject): A_Map = unsupported

	@ThreadSafe
	override fun o_SerializerOperation(
		self: AvailObject): SerializerOperation =
			SerializerOperation.BOTTOM_POJO_TYPE

	override fun printObjectOnAvoidingIndent(
		self: AvailObject,
		builder: StringBuilder,
		recursionMap: IdentityHashMap<A_BasicObject, Void>,
		indent: Int)
	{
		builder.append("pojo ⊥")
	}

	override fun o_WriteTo(self: AvailObject, writer: JSONWriter)
	{
		writer.startObject()
		writer.write("kind")
		writer.write("pojo bottom type")
		writer.endObject()
	}

	override fun mutable(): BottomPojoTypeDescriptor = mutable

	override fun immutable(): BottomPojoTypeDescriptor = immutable

	override fun shared(): BottomPojoTypeDescriptor = shared

	companion object
	{
		/** The mutable [BottomPojoTypeDescriptor]. */
		val mutable = BottomPojoTypeDescriptor(Mutability.MUTABLE)

		/** The immutable [BottomPojoTypeDescriptor]. */
		private val immutable = BottomPojoTypeDescriptor(Mutability.IMMUTABLE)

		/** The shared [BottomPojoTypeDescriptor]. */
		private val shared = BottomPojoTypeDescriptor(Mutability.SHARED)

		/**
		 * The most specific [pojo type][PojoTypeDescriptor], other than
		 * [bottom].
		 */
		private val pojoBottom: A_Type = mutable.createShared { }

		/**
		 * Answer the most specific [pojo&#32;type][PojoTypeDescriptor], other
		 * than [bottom].
		 *
		 * @return
		 *   The most specific pojo type.
		 */
		fun pojoBottom(): A_Type = pojoBottom
	}
}
