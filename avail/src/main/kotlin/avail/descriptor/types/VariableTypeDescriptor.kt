/*
 * VariableTypeDescriptor.kt
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

import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.AvailObject
import avail.descriptor.representation.AvailObject.Companion.combine2
import avail.descriptor.representation.Mutability
import avail.descriptor.representation.ObjectSlotsEnum
import avail.descriptor.types.A_Type.Companion.isSubtypeOf
import avail.descriptor.types.A_Type.Companion.isSupertypeOfVariableType
import avail.descriptor.types.A_Type.Companion.readType
import avail.descriptor.types.A_Type.Companion.typeIntersection
import avail.descriptor.types.A_Type.Companion.typeIntersectionOfVariableType
import avail.descriptor.types.A_Type.Companion.typeUnion
import avail.descriptor.types.A_Type.Companion.typeUnionOfVariableType
import avail.descriptor.types.A_Type.Companion.writeType
import avail.descriptor.types.BottomTypeDescriptor.Companion.bottom
import avail.descriptor.types.InstanceMetaDescriptor.Companion.instanceMeta
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.TOP
import avail.descriptor.types.VariableTypeDescriptor.ObjectSlots
import avail.descriptor.types.VariableTypeDescriptor.ObjectSlots.INNER_TYPE
import avail.descriptor.variables.VariableDescriptor
import avail.serialization.SerializerOperation
import org.availlang.json.JSONWriter
import java.util.IdentityHashMap

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
class VariableTypeDescriptor
private constructor(
	mutability: Mutability
) : TypeDescriptor(
	mutability,
	TypeTag.VARIABLE_TYPE_TAG,
	TypeTag.VARIABLE_TAG,
	ObjectSlots::class.java,
	null)
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
		self.slot(INNER_TYPE).printOnAvoidingIndent(
			builder,
			recursionMap,
			indent + 1)
	}

	override fun o_ReadType(self: AvailObject): A_Type =
		self.slot(INNER_TYPE)

	override fun o_WriteType(self: AvailObject): A_Type =
		self.slot(INNER_TYPE)

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
			(aType.readType.equals(self.slot(INNER_TYPE))
				&& aType.writeType.equals(self.slot(INNER_TYPE)))
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
		combine2(self.slot(INNER_TYPE).hash(), 0x7613E420)

	override fun o_IsSubtypeOf(self: AvailObject, aType: A_Type): Boolean =
		aType.isSupertypeOfVariableType(self)

	override fun o_IsSupertypeOfVariableType(
		self: AvailObject,
		aVariableType: A_Type): Boolean
	{
		val innerType = self.slot(INNER_TYPE)

		// Variable types are covariant by read capability and contravariant by
		// write capability.
		return (aVariableType.readType.isSubtypeOf(innerType)
			&& innerType.isSubtypeOf(aVariableType.writeType))
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
		val innerType: A_Type = self.slot(INNER_TYPE)
		// The intersection of two variable types is a variable type whose
		// read type is the type intersection of the two incoming read types and
		// whose write type is the type union of the two incoming write types.
		return variableReadWriteType(
			innerType.typeIntersection(aVariableType.readType),
			innerType.typeUnion(aVariableType.writeType))
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
		val innerType: A_Type = self.slot(INNER_TYPE)

		// The union of two variable types is a variable type whose
		// read type is the type union of the two incoming read types and whose
		// write type is the type intersection of the two incoming write types.
		return variableReadWriteType(
			innerType.typeUnion(aVariableType.readType),
			innerType.typeIntersection(aVariableType.writeType))
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
		val innerType = self.slot(INNER_TYPE)
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
		val innerType = self.slot(INNER_TYPE)
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
		fun variableTypeFor(innerType: A_Type): A_Type = mutable.create {
			setSlot(INNER_TYPE, innerType.makeImmutable())
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

		/** The mutable [VariableTypeDescriptor]. */
		private val mutable = VariableTypeDescriptor(Mutability.MUTABLE)

		/** The shared [VariableTypeDescriptor]. */
		private val shared = VariableTypeDescriptor(Mutability.SHARED)

		/**
		 * The most general [variable][ReadWriteVariableTypeDescriptor] type.
		 */
		val mostGeneralVariableType: A_Type =
			variableReadWriteType(TOP.o, bottom).makeShared()

		/**
		 * The (instance) type of the most general variable metatype.
		 */
		val mostGeneralVariableMeta: A_Type =
			instanceMeta(mostGeneralVariableType).makeShared()
	}
}
