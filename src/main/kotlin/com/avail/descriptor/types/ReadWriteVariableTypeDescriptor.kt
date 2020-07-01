/*
 * ReadWriteVariableTypeDescriptor.kt
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
import com.avail.descriptor.types.ReadWriteVariableTypeDescriptor.ObjectSlots
import com.avail.descriptor.types.ReadWriteVariableTypeDescriptor.ObjectSlots.*
import com.avail.descriptor.types.VariableTypeDescriptor.Companion.variableReadWriteType
import com.avail.descriptor.types.VariableTypeDescriptor.Companion.variableTypeFor
import com.avail.descriptor.variables.VariableDescriptor
import com.avail.serialization.SerializerOperation
import com.avail.utility.json.JSONWriter
import java.util.*

/**
 * A `ReadWriteVariableTypeDescriptor read-write variable type` is parametric on
 * the types of values that may be [read][ObjectSlots.READ_TYPE] from and
 * [written][ObjectSlots.WRITE_TYPE] to object instance
 * [variables][VariableDescriptor]. Reading a variable is a covariant
 * capability, while writing a variable is a contravariant capability.
 *
 * When the read and write capabilities are equivalent, the static factory
 * methods normalize the representation to an invariant
 * [variable&#32;type&#32;descriptor][VariableTypeDescriptor].
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @see VariableTypeDescriptor
 *
 * @constructor
 * Construct a new [ReadWriteVariableTypeDescriptor].
 *
 * @param mutability
 *   The [mutability][Mutability] of the new descriptor.
 */
class ReadWriteVariableTypeDescriptor private constructor(
	mutability: Mutability) : TypeDescriptor(
		mutability, TypeTag.VARIABLE_TYPE_TAG, ObjectSlots::class.java, null)
{
	/**
	 * The layout of object slots for my instances.
	 */
	enum class ObjectSlots : ObjectSlotsEnum
	{
		/** The type of values that can be read from my object instances.  */
		READ_TYPE,

		/** The type of values that can be written to my object instances.  */
		WRITE_TYPE
	}

	override fun printObjectOnAvoidingIndent(
		self: AvailObject,
		builder: StringBuilder,
		recursionMap: IdentityHashMap<A_BasicObject, Void>,
		indent: Int)
	{
		builder.append("read ")
		self.slot(READ_TYPE).printOnAvoidingIndent(
			builder,
			recursionMap,
			indent + 1)
		builder.append("/write ")
		self.slot(WRITE_TYPE).printOnAvoidingIndent(
			builder,
			recursionMap,
			indent + 1)
	}

	override fun o_ReadType(self: AvailObject): A_Type =
		self.slot(READ_TYPE)

	override fun o_WriteType(self: AvailObject): A_Type =
		self.slot(WRITE_TYPE)

	override fun o_Equals(self: AvailObject, another: A_BasicObject): Boolean =
		another.equalsVariableType(self)

	override fun o_EqualsVariableType(self: AvailObject, aType: A_Type): Boolean =
		when
		{
			self.sameAddressAs(aType) -> true
			aType.readType().equals(self.slot(READ_TYPE))
				&& aType.writeType().equals(self.slot(WRITE_TYPE)) ->
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
				true
			}
			else -> false
		}

	override fun o_Hash(self: AvailObject): Int =
		self.slot(READ_TYPE).hash() xor
			(0x0F40149E + self.slot(WRITE_TYPE).hash()) xor
				0x05469E1A

	override fun o_IsSubtypeOf(self: AvailObject, aType: A_Type): Boolean =
		aType.isSupertypeOfVariableType(self)

	// Variable types are covariant by read capability and contravariant by
	// write capability.
	override fun o_IsSupertypeOfVariableType(
		self: AvailObject,
		aVariableType: A_Type): Boolean =
			(aVariableType.readType().isSubtypeOf(
					self.slot(READ_TYPE))
		        && self.slot(WRITE_TYPE)
				    .isSubtypeOf(aVariableType.writeType()))

	override fun o_TypeIntersection(self: AvailObject, another: A_Type): A_Type =
		when
		{
			self.isSubtypeOf(another) -> self
			another.isSubtypeOf(self) -> another
			else -> another.typeIntersectionOfVariableType(self)
		}

	// The intersection of two variable types is variable type whose
	// read type is the type intersection of the two incoming read types and
	// whose write type is the type union of the two incoming write types.
	override fun o_TypeIntersectionOfVariableType(
		self: AvailObject,
		aVariableType: A_Type): A_Type =
			variableReadWriteType(
				self.slot(READ_TYPE)
					.typeIntersection(aVariableType.readType()),
				self.slot(WRITE_TYPE)
					.typeUnion(aVariableType.writeType()))

	override fun o_TypeUnion(self: AvailObject, another: A_Type): A_Type =
		when
		{
			self.isSubtypeOf(another) -> another
			another.isSubtypeOf(self) -> self
			else -> another.typeUnionOfVariableType(self)
		}

	// The union of two variable types is a variable type whose
	// read type is the type union of the two incoming read types and whose
	// write type is the type intersection of the two incoming write types.
	override fun o_TypeUnionOfVariableType(
		self: AvailObject,
		aVariableType: A_Type): A_Type =
			variableReadWriteType(
				self.slot(READ_TYPE)
					.typeUnion(aVariableType.readType()),
				self.slot(WRITE_TYPE)
					.typeIntersection(aVariableType.writeType()))

	override fun o_SerializerOperation(self: AvailObject): SerializerOperation =
		if (self.readType().equals(self.writeType()))
		{
			SerializerOperation.SIMPLE_VARIABLE_TYPE
		}
		else
		{
			SerializerOperation.READ_WRITE_VARIABLE_TYPE
		}

	override fun o_MakeImmutable(self: AvailObject): AvailObject =
		if (isMutable)
		{
			// Make the object shared rather than immutable (since there isn't
			// actually an immutable descriptor).
			self.makeShared()
		}
		else self

	override fun o_WriteTo(self: AvailObject, writer: JSONWriter)
	{
		writer.startObject()
		writer.write("kind")
		writer.write("variable type")
		writer.write("write type")
		self.slot(WRITE_TYPE).writeTo(writer)
		writer.write("read type")
		self.slot(READ_TYPE).writeTo(writer)
		writer.endObject()
	}

	override fun o_WriteSummaryTo(self: AvailObject, writer: JSONWriter)
	{
		writer.startObject()
		writer.write("kind")
		writer.write("variable type")
		writer.write("write type")
		self.slot(WRITE_TYPE).writeSummaryTo(writer)
		writer.write("read type")
		self.slot(READ_TYPE).writeSummaryTo(writer)
		writer.endObject()
	}

	override fun mutable(): ReadWriteVariableTypeDescriptor = mutable

	// There isn't an immutable variant.
	override fun immutable(): ReadWriteVariableTypeDescriptor = shared

	override fun shared(): ReadWriteVariableTypeDescriptor = shared

	companion object
	{
		/** The mutable [ReadWriteVariableTypeDescriptor].  */
		private val mutable =
			ReadWriteVariableTypeDescriptor(Mutability.MUTABLE)

		/** The shared [ReadWriteVariableTypeDescriptor].  */
		private val shared =
			ReadWriteVariableTypeDescriptor(Mutability.SHARED)

		/**
		 * Create a [variable&#32;type][VariableTypeDescriptor] based on the
		 * given read and write [types][TypeDescriptor].
		 *
		 * @param readType
		 *   The read type.
		 * @param writeType
		 *   The write type.
		 * @return
		 *   The new variable type.
		 */
		fun fromReadAndWriteTypes(
			readType: A_Type,
			writeType: A_Type): A_Type
		{
			if (readType.equals(writeType))
			{
				return variableTypeFor(readType)
			}
			return mutable.create {
				setSlot(READ_TYPE, readType)
				setSlot(WRITE_TYPE, writeType)
			}
		}
	}
}
