/*
 * PojoDescriptor.kt
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
package com.avail.descriptor.pojos

import com.avail.descriptor.pojos.PojoDescriptor.ObjectSlots.KIND
import com.avail.descriptor.pojos.PojoDescriptor.ObjectSlots.RAW_POJO
import com.avail.descriptor.pojos.RawPojoDescriptor.Companion.rawNullPojo
import com.avail.descriptor.representation.A_BasicObject
import com.avail.descriptor.representation.AvailObject
import com.avail.descriptor.representation.Descriptor
import com.avail.descriptor.representation.Mutability
import com.avail.descriptor.representation.ObjectSlotsEnum
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.BottomPojoTypeDescriptor.Companion.pojoBottom
import com.avail.descriptor.types.PojoTypeDescriptor
import com.avail.descriptor.types.TypeTag
import com.avail.utility.json.JSONWriter
import java.util.IdentityHashMap

/**
 * A `PojoDescriptor` describes a plain-old Java object (pojo) that is
 * accessible to an Avail programmer as an [AvailObject]. An Avail pojo
 * comprises a [raw&#32;pojo][RawPojoDescriptor] and a
 * [pojo&#32;type][PojoTypeDescriptor] that describes the pojo contextually.
 *
 * @constructor
 * Create a new [AvailObject] that wraps the specified
 * [raw&#32;pojo][RawPojoDescriptor] and has the specified
 * [pojo&#32;type][PojoTypeDescriptor].
 *
 * @param mutability
 *   The [mutability][Mutability] of the new descriptor.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
class PojoDescriptor private constructor(
	mutability: Mutability
) : Descriptor(mutability, TypeTag.POJO_TAG, ObjectSlots::class.java, null)
{
	/** The layout of the object slots.  */
	enum class ObjectSlots : ObjectSlotsEnum
	{
		/** A [raw&#32;pojo][RawPojoDescriptor]. */
		RAW_POJO,

		/** The [kind][PojoTypeDescriptor] of the pojo. */
		KIND
	}

	override fun o_Equals(self: AvailObject, another: A_BasicObject): Boolean =
		another.equalsPojo(self)

	override fun o_EqualsPojo(self: AvailObject, aPojo: AvailObject): Boolean =
		when
		{
			self.sameAddressAs(aPojo) -> true
			!self.slot(RAW_POJO).equals(aPojo.slot(RAW_POJO)) -> false
			else ->
			{
				when
				{
					!isShared ->
						self.becomeIndirectionTo(aPojo.makeImmutable())
					!aPojo.descriptor().isShared ->
						aPojo.becomeIndirectionTo(self.makeImmutable())
				}
				true
			}
		}

	override fun o_Hash(self: AvailObject): Int
	{
		var hash = self.slot(RAW_POJO).hash() xor 0x749101DD
		hash *= AvailObject.multiplier
		hash += self.slot(KIND).hash()
		return hash
	}

	override fun o_IsPojo(self: AvailObject): Boolean = true

	override fun o_Kind(self: AvailObject): A_Type = self.slot(KIND)

	override fun o_MarshalToJava(
		self: AvailObject,
		classHint: Class<*>?
	): Any? = self.slot(RAW_POJO).javaObject()

	override fun o_RawPojo(self: AvailObject): AvailObject = self.slot(RAW_POJO)

	override fun <T : Any> o_JavaObject(self: AvailObject): T? =
		self.slot(RAW_POJO).javaObject()

	override fun o_ShowValueInNameForDebugger(self: AvailObject): Boolean =
		false

	override fun o_WriteTo(self: AvailObject, writer: JSONWriter) =
		writer.writeObject {
			at("kind") { write("pojo") }
			at("pojo type") { self.slot(KIND).writeTo(writer) }
			at("description") {
				write(self.slot(RAW_POJO).javaObject<String>())
			}
		}

	override fun o_WriteSummaryTo(self: AvailObject, writer: JSONWriter) =
		writer.writeObject {
			at("kind") { write("pojo") }
			at("pojo type") { self.slot(KIND).writeSummaryTo(writer) }
			at("description") {
				write(self.slot(RAW_POJO).javaObject<String>())
			}
		}

	override fun printObjectOnAvoidingIndent(
		self: AvailObject,
		builder: StringBuilder,
		recursionMap: IdentityHashMap<A_BasicObject, Void>,
		indent: Int)
	{
		builder.append(self.slot(RAW_POJO).javaObject<Any>())
		builder.append(" ∈ ")
		self.slot(KIND).printOnAvoidingIndent(builder, recursionMap, indent)
	}

	override fun mutable() = mutable

	override fun immutable() = immutable

	override fun shared() = shared

	companion object
	{
		/** The mutable [PojoDescriptor].  */
		private val mutable = PojoDescriptor(Mutability.MUTABLE)

		/** The immutable [PojoDescriptor].  */
		private val immutable = PojoDescriptor(Mutability.IMMUTABLE)

		/** The shared [PojoDescriptor].  */
		private val shared = PojoDescriptor(Mutability.SHARED)

		/**
		 * Create a new [AvailObject] that wraps the specified
		 * [raw&#32;pojo][RawPojoDescriptor] and has the specified
		 * [pojo&#32;type][PojoTypeDescriptor].
		 *
		 * @param rawPojo
		 *   A raw pojo.
		 * @param pojoType
		 *   A pojo type.
		 * @return
		 *   The new Avail [pojo][PojoDescriptor].
		 */
		@JvmStatic
		fun newPojo(
			rawPojo: AvailObject,
			pojoType: A_Type
		): AvailObject = mutable.createImmutable {
			setSlot(RAW_POJO, rawPojo)
			setSlot(KIND, pojoType)
		}

		/** The [pojo][PojoDescriptor] that wraps Java's `null`.  */
		private val nullObject =
			newPojo(rawNullPojo(), pojoBottom()).makeShared()

		/**
		 * Answer the [pojo][PojoDescriptor] that wraps Java's
		 * `null`.
		 *
		 * @return
		 *   The `null` pojo.
		 */
		@JvmStatic
		fun nullPojo(): AvailObject = nullObject
	}
}
