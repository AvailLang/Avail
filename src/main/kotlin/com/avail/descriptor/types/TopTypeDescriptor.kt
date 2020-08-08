/*
 * TopTypeDescriptor.kt
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
import com.avail.descriptor.representation.AvailObject
import com.avail.descriptor.representation.BitField
import com.avail.descriptor.representation.IntegerSlotsEnum
import com.avail.descriptor.representation.Mutability
import com.avail.descriptor.representation.NilDescriptor
import com.avail.descriptor.representation.ObjectSlotsEnum
import com.avail.descriptor.tuples.StringDescriptor
import com.avail.utility.json.JSONWriter

/**
 * `TopTypeDescriptor` implements the type of [nil][NilDescriptor.nil].
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @constructor
 * Construct a new [shared][Mutability.SHARED] [PrimitiveTypeDescriptor].
 *
 * @param typeTag
 *   The [TypeTag] to embed in the new descriptor.
 * @param primitiveType
 *   The [primitive type][TypeDescriptor.Types] represented by this descriptor.
 */
class TopTypeDescriptor internal constructor(
	typeTag: TypeTag,
	primitiveType: Types) : PrimitiveTypeDescriptor(
		typeTag,
		primitiveType,
		ObjectSlots::class.java,
		IntegerSlots::class.java)
{
	/**
	 * The layout of integer slots for my instances.
	 */
	enum class IntegerSlots : IntegerSlotsEnum
	{
		/**
		 * The low 32 bits are used for caching the hash.
		 */
		HASH_AND_MORE;

		companion object
		{
			/**
			 * The hash, populated during construction.
			 */
			@JvmField
			val HASH = BitField(HASH_AND_MORE, 0, 32)

			init
			{
				assert(PrimitiveTypeDescriptor.IntegerSlots.HASH_AND_MORE.ordinal
						   == HASH_AND_MORE.ordinal)
				assert(PrimitiveTypeDescriptor.IntegerSlots.HASH
						   .isSamePlaceAs(HASH))
			}
		}
	}

	/**
	 * The layout of object slots for my instances.
	 */
	enum class ObjectSlots : ObjectSlotsEnum
	{
		/**
		 * The [name][StringDescriptor] of this primitive type.
		 */
		NAME,

		/**
		 * The parent type of this primitive type.
		 */
		PARENT;

		companion object
		{
			init
			{
				assert(PrimitiveTypeDescriptor.ObjectSlots.NAME.ordinal
						   == NAME.ordinal)
				assert(PrimitiveTypeDescriptor.ObjectSlots.PARENT.ordinal
						   == PARENT.ordinal)
			}
		}
	}

	@ThreadSafe
	override fun o_IsSubtypeOf(self: AvailObject, aType: A_Type): Boolean
	{
		// Check if object (the type top) is a subtype of aType (may also be
		// top).
		assert(aType.isType)
		return aType.traversed().sameAddressAs(self)
	}

	// Check if object (the type top) is a supertype of aPrimitiveType (a
	// primitive type). Always true.
	@ThreadSafe
	override fun o_IsSupertypeOfPrimitiveTypeEnum(
		self: AvailObject,
		primitiveTypeEnum: Types): Boolean = true

	@ThreadSafe
	override fun o_IsTop(self: AvailObject): Boolean = true

	override fun o_WriteTo(self: AvailObject, writer: JSONWriter)
	{
		writer.startObject()
		writer.write("kind")
		writer.write("top type")
		writer.endObject()
	}
}
