/*
 * CompiledCodeTypeDescriptor.kt
 * Copyright © 1993-2021, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
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

import com.avail.descriptor.functions.CompiledCodeDescriptor
import com.avail.descriptor.representation.A_BasicObject
import com.avail.descriptor.representation.AvailObject
import com.avail.descriptor.representation.Mutability
import com.avail.descriptor.representation.ObjectSlotsEnum
import com.avail.descriptor.types.A_Type.Companion.functionType
import com.avail.descriptor.types.A_Type.Companion.isSubtypeOf
import com.avail.descriptor.types.A_Type.Companion.isSupertypeOfCompiledCodeType
import com.avail.descriptor.types.A_Type.Companion.typeIntersection
import com.avail.descriptor.types.A_Type.Companion.typeIntersectionOfCompiledCodeType
import com.avail.descriptor.types.A_Type.Companion.typeUnion
import com.avail.descriptor.types.A_Type.Companion.typeUnionOfCompiledCodeType
import com.avail.descriptor.types.CompiledCodeTypeDescriptor.ObjectSlots.FUNCTION_TYPE
import com.avail.serialization.SerializerOperation
import com.avail.utility.json.JSONWriter
import java.util.IdentityHashMap

/**
 * A [compiled&#32;code&#32;type][CompiledCodeTypeDescriptor] is the
 * type for a [compiled&#32;code&#32;object][CompiledCodeDescriptor].
 * It contains a [function&#32;type][FunctionTypeDescriptor] with which it
 * covaries. That is, a compiled code type is a subtype of another if and only
 * if the first's related function type is a subtype of another's function type.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @constructor
 * Construct a new `CompiledCodeTypeDescriptor`.
 *
 * @param mutability
 * 		The [mutability][Mutability] of the new descriptor.
 */
class CompiledCodeTypeDescriptor private constructor(mutability: Mutability)
	: TypeDescriptor(
		mutability,
		TypeTag.RAW_FUNCTION_TYPE_TAG,
		ObjectSlots::class.java,
		null)
{
	/**
	 * The layout of object slots for my instances.
	 */
	enum class ObjectSlots : ObjectSlotsEnum
	{
		/**
		 * The type of function that this
		 * [compiled&#32;code&#32;type][CompiledCodeTypeDescriptor]
		 * supports.  Compiled code types are contravariant with respect to the
		 * function type's argument types and covariant with respect to the
		 * function type's return type.
		 */
		FUNCTION_TYPE
	}

	override fun printObjectOnAvoidingIndent(
		self: AvailObject,
		builder: StringBuilder,
		recursionMap: IdentityHashMap<A_BasicObject, Void>,
		indent: Int)
	{
		builder.append('¢')
		self.functionType().printOnAvoidingIndent(
			builder,
			recursionMap,
			indent + 1)
	}

	override fun o_FunctionType(self: AvailObject): A_Type =
		self.slot(FUNCTION_TYPE)

	override fun o_Equals(self: AvailObject, another: A_BasicObject): Boolean =
		another.equalsCompiledCodeType(self)

	/**
	 * {@inheritDoc}
	 *
	 * Compiled code types compare for equality by comparing their function
	 * types.
	 */
	override fun o_EqualsCompiledCodeType(
		self: AvailObject,
		aCompiledCodeType: A_Type): Boolean =
			if (self.sameAddressAs(aCompiledCodeType))
			{
				true
			}
			else
			{
				aCompiledCodeType.functionType().equals(self.functionType())
			}

	override fun o_Hash(self: AvailObject): Int =
		self.functionType().hash() * 71 xor -0x5874fe3d

	override fun o_IsSubtypeOf(self: AvailObject, aType: A_Type): Boolean =
		aType.isSupertypeOfCompiledCodeType(self)

	/**
	 * {@inheritDoc}
	 *
	 * Compiled code types exactly covary with their function types.
	 */
	override fun o_IsSupertypeOfCompiledCodeType(
		self: AvailObject,
		aCompiledCodeType: A_Type): Boolean
	{
		val subFunctionType = aCompiledCodeType.functionType()
		val superFunctionType = self.functionType()
		return subFunctionType.isSubtypeOf(superFunctionType)
	}

	override fun o_IsVacuousType(self: AvailObject): Boolean =
		self.slot(FUNCTION_TYPE).isVacuousType

	override fun o_TypeIntersection(self: AvailObject, another: A_Type): A_Type =
		when
		{
			self.isSubtypeOf(another) ->
			{
				self
			}
			else -> if (another.isSubtypeOf(self))
			{
				another
			}
			else another.typeIntersectionOfCompiledCodeType(self)
		}

	override fun o_TypeIntersectionOfCompiledCodeType(
		self: AvailObject,
		aCompiledCodeType: A_Type): A_Type
	{
		val functionType1 = self.functionType()
		val functionType2 = aCompiledCodeType.functionType()
		return if (functionType1.equals(functionType2))
		{
			self
		}
		else
		{
			compiledCodeTypeForFunctionType(
				functionType1.typeIntersection(functionType2))
		}
	}

	override fun o_TypeUnion(
		self: AvailObject,
		another: A_Type): A_Type =
		when
		{
			self.isSubtypeOf(another) ->
			{
				another
			}
			else -> if (another.isSubtypeOf(self))
			{
				self
			}
			else another.typeUnionOfCompiledCodeType(self)
		}

	override fun o_TypeUnionOfCompiledCodeType(
		self: AvailObject,
		aCompiledCodeType: A_Type): A_Type
	{
		val functionType1 = self.functionType()
		val functionType2 = aCompiledCodeType.functionType()
		return if (functionType1.equals(functionType2))
		{
			// Optimization only
			self
		}
		else
		{
			compiledCodeTypeForFunctionType(
				functionType1.typeUnion(functionType2))
		}
	}

	override fun o_SerializerOperation(self: AvailObject): SerializerOperation =
		SerializerOperation.COMPILED_CODE_TYPE

	override fun o_MakeImmutable(self: AvailObject): AvailObject =
		if (isMutable)
		{
			// Make the object shared.
			self.makeShared()
		}
		else self

	override fun o_WriteTo(self: AvailObject, writer: JSONWriter)
	{
		writer.startObject()
		writer.write("kind")
		writer.write("function implementation type")
		writer.write("function type")
		self.slot(FUNCTION_TYPE).writeTo(writer)
		writer.endObject()
	}

	override fun mutable(): TypeDescriptor = mutable

	// There is only a shared descriptor, not an immutable one.
	override fun immutable(): TypeDescriptor = shared

	override fun shared(): TypeDescriptor = shared

	companion object
	{
		/**
		 * Create a compiled code type based on the passed
		 * [function&#32;type][FunctionTypeDescriptor]. Ignore the function
		 * type's exception set.
		 *
		 * @param functionType
		 *   A [function type][FunctionTypeDescriptor] on which to base the new
		 *   compiled code type.
		 * @return
		 *   A new compiled code type.
		 */
		fun compiledCodeTypeForFunctionType(
			functionType: A_BasicObject
		): AvailObject =
			mutable.createImmutable {
				setSlot(FUNCTION_TYPE, functionType.makeImmutable())
			}

		/** The mutable [CompiledCodeTypeDescriptor].  */
		private val mutable: TypeDescriptor =
			CompiledCodeTypeDescriptor(Mutability.MUTABLE)

		/** The shared [CompiledCodeTypeDescriptor].  */
		private val shared: TypeDescriptor =
			CompiledCodeTypeDescriptor(Mutability.SHARED)

		/**
		 * The most general compiled code type. Since compiled code types are
		 * contravariant by argument types and contravariant by return type, the
		 * most general type is the one taking bottom as the arguments list
		 * (i.e., not specific enough to be able to call it), and having the
		 * return type bottom.
		 */
		private val mostGeneralType: A_Type =
			compiledCodeTypeForFunctionType(
				FunctionTypeDescriptor.mostGeneralFunctionType()).makeShared()

		/**
		 * Answer the most general compiled code type.
		 *
		 * @return
		 *   A compiled code type which has no supertypes that are themselves
		 *   compiled code types.
		 */
		@JvmStatic
		fun mostGeneralCompiledCodeType(): A_Type =  mostGeneralType

		/**
		 * The metatype for all compiled code types. In particular, it's just
		 * the [instance type][InstanceTypeDescriptor] for the
		 * [most&#32;general&#32;compiled&#32;code&#32;type][mostGeneralType].
		 */
		private val meta: A_Type =
			InstanceMetaDescriptor.instanceMeta(mostGeneralType).makeShared()

		/**
		 * Answer the metatype for all compiled code types.
		 *
		 * @return
		 *   The statically referenced metatype.
		 */
		fun meta(): A_Type = meta
	}
}
