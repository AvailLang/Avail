/*
 * EqualityRawPojoDescriptor.kt
 * Copyright © 1993-2020, The Avail Foundation, LLC.
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

import com.avail.descriptor.representation.A_BasicObject
import com.avail.descriptor.representation.AvailObject
import com.avail.descriptor.representation.Mutability
import java.util.*

/**
 * `EqualityRawPojoDescriptor` differs from [RawPojoDescriptor] in that equality
 * of wrapped pojos is based on semantic equivalence rather than referential
 * equality. It is used only for immutable reflective classes and exists only to
 * support certain recursive comparison operations.
 *
 * @constructor
 *
 * @param mutability
 *   The [mutability][Mutability] of the new descriptor.
 * @param javaObject
 *   The actual Java [Object] represented by the [AvailObject] that will use the
 *   new descriptor.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @see RawPojoDescriptor
 */
internal class EqualityRawPojoDescriptor(
	mutability: Mutability,
	javaObject: Any?
) : RawPojoDescriptor(mutability, javaObject)
{
	override fun o_Equals(self: AvailObject, another: A_BasicObject): Boolean =
		another.equalsEqualityRawPojoFor(self, javaObject)

	override fun o_EqualsEqualityRawPojo(
		self: AvailObject,
		otherEqualityRawPojo: AvailObject,
		otherJavaObject: Any?
	): Boolean
	{
		return when
		{
			javaObject == null -> otherJavaObject == null
			otherJavaObject == null -> false
			// Neither is null.
			javaObject != otherJavaObject -> false
			// They're equal.
			!self.sameAddressAs(otherEqualityRawPojo) ->
			{
				// They're equal, but distinct AvailObjects.
				when
				{
					!isShared -> self.becomeIndirectionTo(otherEqualityRawPojo)
					!otherEqualityRawPojo.descriptor().isShared ->
						otherEqualityRawPojo.becomeIndirectionTo(self)
				}
				true
			}
			else -> true
		}
	}

	override fun o_EqualsRawPojoFor(
		self: AvailObject,
		otherRawPojo: AvailObject,
		otherJavaObject: Any?
	) = false

	override fun o_Hash(self: AvailObject): Int =
		when (javaObject)
		{
			null -> -0x3bb1116b
			else -> javaObject.hashCode() xor 0x59EEE44C
		}

	/**
	 * Replace the descriptor with a newly synthesized one that has the same
	 * [javaObject] but is [immutable][Mutability.IMMUTABLE].
	 */
	override fun o_MakeImmutable(self: AvailObject): AvailObject
	{
		if (isMutable)
		{
			self.setDescriptor(
				EqualityRawPojoDescriptor(Mutability.IMMUTABLE, javaObject))
		}
		return self
	}

	/**
	 * Replace the descriptor with a newly synthesized one that has the same
	 * [javaObject] but is [shared][Mutability.SHARED].
	 */
	override fun o_MakeShared(self: AvailObject): AvailObject
	{
		if (!isShared)
		{
			self.setDescriptor(
				EqualityRawPojoDescriptor(Mutability.SHARED, javaObject))
		}
		return self
	}

	override fun printObjectOnAvoidingIndent(
		self: AvailObject,
		builder: StringBuilder,
		recursionMap: IdentityHashMap<A_BasicObject, Void>,
		indent: Int)
	{
		builder.append("equality raw pojo: ")
		builder.append(javaObject)
	}
}
