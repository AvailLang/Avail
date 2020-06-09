/*
 * VariableTypeDescriptor.kt
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

import com.avail.descriptor.representation.A_BasicObject
import com.avail.descriptor.representation.AvailObject
import com.avail.descriptor.representation.Mutability
import com.avail.descriptor.representation.ObjectSlotsEnum
import com.avail.descriptor.types.BottomTypeDescriptor.Companion.bottom
import com.avail.descriptor.types.InstanceMetaDescriptor.Companion.instanceMeta
import com.avail.descriptor.types.VariableTypeDescriptor.ObjectSlots
import com.avail.descriptor.variables.VariableDescriptor
import com.avail.serialization.SerializerOperation
import com.avail.utility.json.JSONWriter
import java.util.*

/**
 * A `VariableTypeDescriptor variable type` is the [type][TypeDescriptor] of any
 * [variable][VariableDescriptor] that can only hold objects having the
 * specified [inner&amp;#32;type][ObjectSlots.INNER_TYPE]. The read and write
 * capabilities of the object instances are equivalent, therefore the inner type
 * is invariant.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @see ReadWriteVariableTypeDescriptor
 *
 * @constructor
 * Construct a new `VariableTypeDescriptor`.
 *
 * @param mutability
 *   The [mutability][Mutability] of the new descriptor.
 */
class VariableTypeDescriptor private constructor(mutability: Mutability)
	: TypeDescriptor(
		mutability, TypeTag.VARIABLE_TYPE_TAG, ObjectSlots::class.java, null)
{
	/**
	 * The layout of object slots for my instances.
	 */
	enum class ObjectSlots : ObjectSlotsEnum
	{
		/**
		 * The type of values that my object instances can contain.
		 */
		INNER_TYPE
	}

	override fun printObjectOnAvoidingIndent(
		self: AvailObject,
		builder: StringBuilder,
		recursionMap: IdentityHashMap<A_BasicObject, Void>,
		indent: Int)
	{
		builder.append("↑")
		self.slot(ObjectSlots.INNER_TYPE).printOnAvoidingIndent(
			builder,
			recursionMap,
			indent + 1)
	}

	override fun o_ReadType(self: AvailObject): A_Type =
		self.slot(ObjectSlots.INNER_TYPE)

	override fun o_WriteType(self: AvailObject): A_Type =
		self.slot(ObjectSlots.INNER_TYPE)

	override fun o_Equals(
		self: AvailObject,
		another: A_BasicObject): Boolean = another.equalsVariableType(self)

	override fun o_EqualsVariableType(
		self: AvailObject,
		aType: A_Type): Boolean
	{
		if (self.sameAddressAs(aType))
		{
			return true
		}
		val same =
			(aType.readType().equals(self.slot(ObjectSlots.INNER_TYPE))
	            && aType.writeType().equals(self.slot(ObjectSlots.INNER_TYPE)))
		if (same)
		{
			if (!isShared)
			{
				aType.makeImmutable()
				self.becomeIndirectionTo(aType)
			}
			else if (!aType.descriptor().isShared)
			{
				self.makeImmutable()
				aType.becomeIndirectionTo(self)
			}
		}
		return same
	}

	override fun o_Hash(self: AvailObject): Int =
		(self.slot(ObjectSlots.INNER_TYPE).hash() xor 0x7613E420) + 0x024E3167

	override fun o_IsSubtypeOf(self: AvailObject, aType: A_Type): Boolean =
		aType.isSupertypeOfVariableType(self)

	override fun o_IsSupertypeOfVariableType(
		self: AvailObject,
		aVariableType: A_Type): Boolean
	{
		val innerType = self.slot(ObjectSlots.INNER_TYPE)

		// Variable types are covariant by read capability and contravariant by
		// write capability.
		return (aVariableType.readType().isSubtypeOf(innerType)
	        && innerType.isSubtypeOf(aVariableType.writeType()))
	}

	override fun o_TypeIntersection(self: AvailObject, another: A_Type): A_Type =
		when
		{
			self.isSubtypeOf(another) -> self
			another.isSubtypeOf(self) -> another
			else -> another.typeIntersectionOfVariableType(self)
		}

	override fun o_TypeIntersectionOfVariableType(
		self: AvailObject,
		aVariableType: A_Type): A_Type
	{
		val innerType: A_Type = self.slot(ObjectSlots.INNER_TYPE)
		// The intersection of two variable types is a variable type whose
		// read type is the type intersection of the two incoming read types and
		// whose write type is the type union of the two incoming write types.
		return variableReadWriteType(
			innerType.typeIntersection(aVariableType.readType()),
			innerType.typeUnion(aVariableType.writeType()))
	}

	override fun o_TypeUnion(
		self: AvailObject,
		another: A_Type): A_Type =
			when
			{
				self.isSubtypeOf(another) -> another
				another.isSubtypeOf(self) -> self
				else -> another.typeUnionOfVariableType(self)
			}

	override fun o_TypeUnionOfVariableType(
		self: AvailObject,
		aVariableType: A_Type): A_Type
	{
		val innerType: A_Type = self.slot(ObjectSlots.INNER_TYPE)

		// The union of two variable types is a variable type whose
		// read type is the type union of the two incoming read types and whose
		// write type is the type intersection of the two incoming write types.
		return variableReadWriteType(
			innerType.typeUnion(aVariableType.readType()),
			innerType.typeIntersection(aVariableType.writeType()))
	}

	override fun o_SerializerOperation(self: AvailObject): SerializerOperation =
		SerializerOperation.SIMPLE_VARIABLE_TYPE

	override fun o_MakeImmutable(self: AvailObject): AvailObject =
		if (isMutable)
		{
			// Since there isn't an immutable variant, make the object shared.
			self.makeShared()
		}
		else self

	override fun o_WriteTo(self: AvailObject, writer: JSONWriter)
	{
		writer.startObject()
		writer.write("kind")
		writer.write("variable type")
		writer.write("write type")
		val innerType = self.slot(ObjectSlots.INNER_TYPE)
		innerType.writeTo(writer)
		writer.write("read type")
		innerType.writeTo(writer)
		writer.endObject()
	}

	override fun o_WriteSummaryTo(self: AvailObject, writer: JSONWriter)
	{
		writer.startObject()
		writer.write("kind")
		writer.write("variable type")
		writer.write("write type")
		val innerType = self.slot(ObjectSlots.INNER_TYPE)
		innerType.writeSummaryTo(writer)
		writer.write("read type")
		innerType.writeSummaryTo(writer)
		writer.endObject()
	}

	override fun mutable(): VariableTypeDescriptor = mutable

	// There is only a shared variant, not an immutable one.
	override fun immutable(): VariableTypeDescriptor = shared

	override fun shared(): VariableTypeDescriptor = shared

	companion object
	{
		/**
		 * Create a variable type based on the given content
		 * [type][TypeDescriptor].
		 *
		 * @param innerType
		 *   The content type on which to base the variable type.
		 * @return
		 *   The new variable type.
		 */
		@JvmStatic
		fun variableTypeFor(innerType: A_Type): A_Type
		{
			val result = mutable.create()
			result.setSlot(ObjectSlots.INNER_TYPE, innerType.makeImmutable())
			return result
		}

		/**
		 * Create a variable type based on the given read and write
		 * [types][TypeDescriptor].
		 *
		 * @param readType
		 *   The read type.
		 * @param writeType
		 *   The write type.
		 * @return
		 *   The new variable type.
		 */
		@JvmStatic
		fun variableReadWriteType(readType: A_Type, writeType: A_Type): A_Type =
			if (readType.equals(writeType))
			{
				variableTypeFor(readType)
			}
			else
			{
				ReadWriteVariableTypeDescriptor.fromReadAndWriteTypes(
					readType, writeType)
			}

		/** The mutable [VariableTypeDescriptor].  */
		private val mutable = VariableTypeDescriptor(Mutability.MUTABLE)

		/** The shared [VariableTypeDescriptor].  */
		private val shared = VariableTypeDescriptor(Mutability.SHARED)

		/**
		 * The most general [variable][ReadWriteVariableTypeDescriptor].
		 */
		private val mostGeneralType: A_Type =
			variableReadWriteType(Types.TOP.o(), bottom()).makeShared()

		/**
		 * Answer the most general
		 * [variable&amp;#32;type][ReadWriteVariableTypeDescriptor].
		 *
		 * @return
		 *   The most general
		 *   [variable&amp;#32;type][ReadWriteVariableTypeDescriptor].
		 */
		@JvmStatic
		fun mostGeneralVariableType(): A_Type = mostGeneralType

		/**
		 * The (instance) type of the most general [ ] metatype.
		 */
		private val variableMeta: A_Type =
			instanceMeta(mostGeneralType).makeShared()

		/**
		 * Answer the (instance) type of the most general
		 * [variable][ReadWriteVariableTypeDescriptor] metatype.
		 *
		 * @return
		 *   The instance type containing the most general
		 *   [variable][ReadWriteVariableTypeDescriptor] metatype.
		 */
		@JvmStatic
		fun variableMeta(): A_Type = variableMeta
	}
}
