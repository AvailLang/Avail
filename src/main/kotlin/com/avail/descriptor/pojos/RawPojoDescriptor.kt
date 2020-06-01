/*
 * RawPojoDescriptor.kt
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
import com.avail.descriptor.representation.AbstractDescriptor
import com.avail.descriptor.representation.AvailObject
import com.avail.descriptor.representation.AvailObjectFieldHelper
import com.avail.descriptor.representation.Descriptor
import com.avail.descriptor.representation.Mutability
import com.avail.descriptor.representation.ObjectSlotsEnum
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.PojoTypeDescriptor
import com.avail.descriptor.types.TypeDescriptor.Types
import com.avail.descriptor.types.TypeTag
import com.avail.serialization.SerializerOperation
import com.avail.utility.Casts.cast
import com.avail.utility.Casts.nullableCast
import java.lang.reflect.Constructor
import java.lang.reflect.Method
import java.util.*

/**
 * A `RawPojoDescriptor` is a thin veneer over a plain-old Java object (pojo).
 * Avail programs will use [typed&#32;pojos][PojoDescriptor] universally, but
 * the implementation mechanisms frequently require raw pojos (especially for
 * defining [pojo&#32;types][PojoTypeDescriptor]).
 *
 * @constructor
 * Create a [raw&#32;pojo][RawPojoDescriptor].  These structures are not usually
 * created by end users, but are created for internal uses, such as to reify
 * [Class] objects, or to have a private slot of an [AvailObject] hold onto a
 * [WeakHashMap] or other Java/Kotlin construct.
 *
 * @param mutability
 *   The [mutability][Mutability] of the new descriptor.
 * @param javaObject
 *   The actual Java [Object] represented by the sole [AvailObject] that will
 *   use the new descriptor.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @see EqualityRawPojoDescriptor
 */
open class RawPojoDescriptor protected constructor(
	mutability: Mutability,
	val javaObject: Any?
) : Descriptor(mutability, TypeTag.POJO_TAG, null, null)
{
	override fun o_Equals(self: AvailObject, another: A_BasicObject): Boolean =
		another.equalsRawPojoFor(self, javaObject)

	override fun o_EqualsEqualityRawPojo(
		self: AvailObject,
		otherEqualityRawPojo: AvailObject,
		otherJavaObject: Any?
	) = false

	override fun o_EqualsRawPojoFor(
		self: AvailObject,
		otherRawPojo: AvailObject,
		otherJavaObject: Any?
	): Boolean
	{
		when
		{
			javaObject !== otherJavaObject -> return false
			self.sameAddressAs(otherRawPojo) -> return true
			!isShared -> self.becomeIndirectionTo(otherRawPojo)
			!otherRawPojo.descriptor().isShared ->
				otherRawPojo.becomeIndirectionTo(self)
		}
		return true
	}

	override fun o_Hash(self: AvailObject): Int
	{
		// This ensures that mutations of the wrapped pojo do not corrupt hashed
		// Avail data structures.
		return System.identityHashCode(javaObject) xor 0x277AB9C3
	}

	override fun o_IsRawPojo(self: AvailObject): Boolean = true

	override fun <T> o_JavaObject(self: AvailObject): T? =
		nullableCast<Any?, T?>(javaObject)

	override fun o_Kind(self: AvailObject): A_Type = Types.RAW_POJO.o()

	/**
	 * Replace the descriptor with a newly synthesized one that has the same
	 * [javaObject] but is [immutable][Mutability.IMMUTABLE].
	 */
	override fun o_MakeImmutable(self: AvailObject): AvailObject
	{
		if (isMutable)
		{
			self.setDescriptor(
				RawPojoDescriptor(Mutability.IMMUTABLE, javaObject))
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
				RawPojoDescriptor(Mutability.SHARED, javaObject))
		}
		return self
	}

	override fun o_MarshalToJava(
		self: AvailObject,
		classHint: Class<*>?
	): Any? = javaObject

	override fun o_SerializerOperation(self: AvailObject): SerializerOperation =
		when (javaObject)
		{
			null -> SerializerOperation.RAW_POJO_NULL
			is Class<*> ->
				when (cast<Any, Class<*>>(javaObject).isPrimitive)
				{
					true -> SerializerOperation.RAW_PRIMITIVE_JAVA_CLASS
					else -> SerializerOperation.RAW_NONPRIMITIVE_JAVA_CLASS
				}
			is Method -> SerializerOperation.RAW_POJO_METHOD
			is Constructor<*> -> SerializerOperation.RAW_POJO_CONSTRUCTOR
			else -> super.o_SerializerOperation(self)
		}

	override fun printObjectOnAvoidingIndent(
		self: AvailObject,
		builder: StringBuilder,
		recursionMap: IdentityHashMap<A_BasicObject, Void>,
		indent: Int)
	{
		// This is not a thread-safe read of the slot, but this method is just
		// for debugging anyway, so don't bother acquiring the lock. Coherence
		// isn't important here.
		builder.append("raw pojo: ")
		builder.append(javaObject)
	}

	/**
	 * A fake enumeration of slots for a nice description of this pojo.
	 */
	internal enum class FakeSlots : ObjectSlotsEnum
	{
		/** The sole (pseudo-)slot, the java object itself.  */
		JAVA_OBJECT
	}

	/**
	 * Show the actual [javaObject][A_BasicObject.javaObject], rather
	 * than just its index.  This is *much* nicer to have available in
	 * the Eclipse/IntelliJ Java debugger.
	 */
	override fun o_DescribeForDebugger(
		self: AvailObject
	): Array<AvailObjectFieldHelper>
	{
		val fields: MutableList<AvailObjectFieldHelper> = ArrayList()
		fields.add(
			AvailObjectFieldHelper(
				self,
				FakeSlots.JAVA_OBJECT,
				-1,
				javaObject))
		return fields.toTypedArray()
	}

	@Deprecated(
		"Not applicable to pojos",
		replaceWith = ReplaceWith("Create a new pojo object instead"))
	override fun mutable() =
		throw unsupportedOperationException()

	@Deprecated(
		"Not applicable to pojos",
		replaceWith = ReplaceWith("Create a new pojo object instead"))
	override fun immutable() =
		throw unsupportedOperationException()

	@Deprecated(
		"Not applicable to pojos",
		replaceWith = ReplaceWith("Create a new pojo object instead"))
	override fun shared() =
		throw unsupportedOperationException()

	companion object
	{
		/**
		 * A [raw&#32;pojo][RawPojoDescriptor] for [Object]'s [class][Class].
		 */
		private val rawObjectClass = equalityPojo(Any::class.java).makeShared()

		/**
		 * Answer a raw pojo for [Object]'s [class][Class].
		 *
		 * @return
		 *   A raw pojo that represents `Object`.
		 */
		@JvmStatic
		fun rawObjectClass(): AvailObject = rawObjectClass

		/** The `null` [pojo][PojoDescriptor].  */
		private val rawNullObject = identityPojo(null).makeShared()

		/**
		 * Answer the `null` raw pojo.
		 *
		 * @return
		 *   The `null` pojo.
		 */
		@JvmStatic
		fun rawNullPojo(): AvailObject = rawNullObject

		/**
		 * Create a new [AvailObject] that wraps the specified Java [Object] for
		 * identity-based comparison semantics.
		 *
		 * @param javaObject
		 *   A Java Object, possibly `null`.
		 * @return
		 *   The new Avail [pojo][PojoDescriptor].
		 */
		@JvmStatic
		fun identityPojo(javaObject: Any?): AvailObject =
			RawPojoDescriptor(Mutability.MUTABLE, javaObject).create()

		/**
		 * Create a new [AvailObject] that wraps the specified Java [Object] for
		 * equality-based comparison semantics.
		 *
		 * @param javaObject
		 *   A Java Object, never `null`.
		 * @return
		 *   The new [Avail&#32;pojo][PojoDescriptor].
		 */
		@JvmStatic
		fun equalityPojo(javaObject: Any): AvailObject =
			EqualityRawPojoDescriptor(Mutability.MUTABLE, javaObject).create()
	}
}
