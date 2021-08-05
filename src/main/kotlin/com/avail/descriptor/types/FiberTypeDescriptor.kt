/*
 * FiberTypeDescriptor.kt
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
import com.avail.descriptor.fiber.FiberDescriptor
import com.avail.descriptor.functions.ContinuationDescriptor
import com.avail.descriptor.functions.FunctionDescriptor
import com.avail.descriptor.representation.A_BasicObject
import com.avail.descriptor.representation.AvailObject
import com.avail.descriptor.representation.AvailObject.Companion.combine2
import com.avail.descriptor.representation.Mutability
import com.avail.descriptor.representation.ObjectSlotsEnum
import com.avail.descriptor.types.A_Type.Companion.isSubtypeOf
import com.avail.descriptor.types.A_Type.Companion.isSupertypeOfFiberType
import com.avail.descriptor.types.A_Type.Companion.typeIntersection
import com.avail.descriptor.types.A_Type.Companion.typeIntersectionOfFiberType
import com.avail.descriptor.types.A_Type.Companion.typeUnion
import com.avail.descriptor.types.A_Type.Companion.typeUnionOfFiberType
import com.avail.descriptor.types.FiberTypeDescriptor.ObjectSlots.RESULT_TYPE
import com.avail.descriptor.types.InstanceMetaDescriptor.Companion.instanceMeta
import com.avail.descriptor.types.TypeDescriptor.Types.TOP
import com.avail.serialization.SerializerOperation
import com.avail.utility.json.JSONWriter
import java.util.IdentityHashMap

/**
 * `FiberTypeDescriptor` represents the type of a [fiber][FiberDescriptor]. A
 * fiber is typed by the return type of the base [function][FunctionDescriptor]
 * used to create it. Fiber types provide first-order type safety, but are not
 * perfectly sincerely. Switching a fiber's
 * [continuation][ContinuationDescriptor] to one whose base function's return
 * type is incompatible with the fiber's type is a runtime error, and will be
 * detected and reported when such a
 * ([terminated][FiberDescriptor.ExecutionState.TERMINATED]) fiber's result is
 * requested.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @constructor
 * Construct a new [FiberTypeDescriptor].
 *
 * @param mutability
	* The [mutability][Mutability] of the new descriptor.
 */
class FiberTypeDescriptor constructor (mutability: Mutability)
	: TypeDescriptor(
		mutability, TypeTag.FIBER_TYPE_TAG, ObjectSlots::class.java, null)
{
	/**
	 * The layout of object slots for my instances.
	 */
	enum class ObjectSlots : ObjectSlotsEnum
	{
		/**
		 * The [fiber type][FiberTypeDescriptor]'s result type.
		 */
		RESULT_TYPE
	}

	override fun o_ResultType(self: AvailObject): A_Type =
		self.slot(RESULT_TYPE)

	override fun o_Equals(self: AvailObject, another: A_BasicObject): Boolean =
		another.equalsFiberType(self)

	override fun o_EqualsFiberType(
		self: AvailObject,
		aFiberType: A_Type): Boolean =
			(self.sameAddressAs(aFiberType)
				|| aFiberType.resultType().equals(
					self.slot(RESULT_TYPE)))

	override fun o_Hash(self: AvailObject): Int =
		combine2(self.slot(RESULT_TYPE).hash(), -0x43f7b58f)

	override fun o_IsSubtypeOf(self: AvailObject, aType: A_Type): Boolean =
		aType.isSupertypeOfFiberType(self)

	override fun o_IsSupertypeOfFiberType(
		self: AvailObject,
		aType: A_Type): Boolean =
			aType.resultType().isSubtypeOf(self.slot(RESULT_TYPE))

	override fun o_TypeIntersection(self: AvailObject, another: A_Type): A_Type =
		when
		{
			self.isSubtypeOf(another) -> self
			else ->
			{
				if (another.isSubtypeOf(self)) another
				else another.typeIntersectionOfFiberType(self)
			}
		}

	override fun o_TypeIntersectionOfFiberType(
		self: AvailObject,
		aFiberType: A_Type): A_Type =
			fiberType(
				self.slot(RESULT_TYPE)
					.typeIntersection(aFiberType.resultType()))

	override fun o_TypeUnion(self: AvailObject, another: A_Type): A_Type =
		when
		{
			self.isSubtypeOf(another) ->
			{
				another
			}
			else ->
			{
				if (another.isSubtypeOf(self)) self
				else another.typeUnionOfFiberType(self)
			}
		}

	override fun o_TypeUnionOfFiberType(
		self: AvailObject,
		aFiberType: A_Type): A_Type =
			fiberType(
				self.slot(RESULT_TYPE)
					.typeUnion(aFiberType.resultType()))

	override fun o_SerializerOperation(self: AvailObject): SerializerOperation =
		SerializerOperation.FIBER_TYPE

	override fun o_MakeImmutable(self: AvailObject): AvailObject
	{
		return if (isMutable)
		{
			// Make the object shared.
			self.makeShared()
		}
		else self
	}

	override fun o_WriteTo(self: AvailObject, writer: JSONWriter)
	{
		writer.startObject()
		writer.write("kind")
		writer.write("fiber type")
		writer.write("result type")
		self.slot(RESULT_TYPE).writeTo(writer)
		writer.endObject()
	}

	override fun o_WriteSummaryTo(self: AvailObject, writer: JSONWriter)
	{
		writer.startObject()
		writer.write("kind")
		writer.write("fiber type")
		writer.write("result type")
		self.slot(RESULT_TYPE).writeSummaryTo(writer)
		writer.endObject()
	}

	@ThreadSafe
	override fun printObjectOnAvoidingIndent(
		self: AvailObject,
		builder: StringBuilder,
		recursionMap: IdentityHashMap<A_BasicObject, Void>,
		indent: Int)
	{
		builder.append("fiber→")
		self.slot(RESULT_TYPE).printOnAvoidingIndent(
			builder, recursionMap, indent)
	}

	override fun mutable(): FiberTypeDescriptor = mutable

	// There is no immutable variation.
	override fun immutable(): FiberTypeDescriptor = shared

	override fun shared(): FiberTypeDescriptor = shared

	companion object
	{
		/** The mutable [FiberTypeDescriptor]. */
		val mutable = FiberTypeDescriptor(Mutability.MUTABLE)

		/** The shared [FiberTypeDescriptor]. */
		private val shared = FiberTypeDescriptor(Mutability.SHARED)

		/**
		 * Create a [fiber&#32;type][FiberTypeDescriptor] with the specified
		 * [result type][AvailObject.resultType].
		 *
		 * @param resultType
		 *   The result type.
		 * @return
		 *   A new fiber type.
		 */
		fun fiberType(resultType: A_Type): AvailObject = mutable.createShared {
			setSlot(RESULT_TYPE, resultType.makeImmutable())
		}

		/**
		 * The most general [fiber type][FiberTypeDescriptor].
		 */
		private val mostGeneralFiberType: A_Type =
			fiberType(TOP.o).makeShared()

		/**
		 * Answer the most general [fiber&#32;type][FiberDescriptor].
		 *
		 * @return
		 *   The most general fiber type.
		 */
		fun mostGeneralFiberType(): A_Type = mostGeneralFiberType

		/**
		 * The metatype for all [fiber&#32;types][FiberTypeDescriptor].
		 */
		private val meta: A_Type =
			instanceMeta(mostGeneralFiberType).makeShared()

		/**
		 * Answer the metatype for all [fiber&#32;types][FiberTypeDescriptor].
		 *
		 * @return
		 *   The metatype for all fiber types.
		 */
		fun fiberMeta(): A_Type = meta
	}
}
