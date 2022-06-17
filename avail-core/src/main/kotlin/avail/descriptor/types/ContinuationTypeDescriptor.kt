/*
 * ContinuationTypeDescriptor.kt
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

import avail.descriptor.functions.ContinuationDescriptor
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.AvailObject
import avail.descriptor.representation.AvailObject.Companion.combine2
import avail.descriptor.representation.Mutability
import avail.descriptor.representation.ObjectSlotsEnum
import avail.descriptor.sets.SetDescriptor.Companion.emptySet
import avail.descriptor.types.A_Type.Companion.argsTupleType
import avail.descriptor.types.A_Type.Companion.functionType
import avail.descriptor.types.A_Type.Companion.isSubtypeOf
import avail.descriptor.types.A_Type.Companion.isSupertypeOfContinuationType
import avail.descriptor.types.A_Type.Companion.returnType
import avail.descriptor.types.A_Type.Companion.typeIntersection
import avail.descriptor.types.A_Type.Companion.typeIntersectionOfContinuationType
import avail.descriptor.types.A_Type.Companion.typeUnion
import avail.descriptor.types.A_Type.Companion.typeUnionOfContinuationType
import avail.descriptor.types.BottomTypeDescriptor.Companion.bottom
import avail.descriptor.types.ContinuationTypeDescriptor.ObjectSlots.FUNCTION_TYPE
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionTypeFromArgumentTupleType
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionTypeReturning
import avail.descriptor.types.InstanceMetaDescriptor.Companion.instanceMeta
import avail.interpreter.primitive.controlflow.P_ExitContinuationWithResultIf
import avail.interpreter.primitive.controlflow.P_RestartContinuationWithArguments
import avail.serialization.SerializerOperation
import org.availlang.json.JSONWriter
import java.util.IdentityHashMap

/**
 * Continuation types are the types of [continuations][ContinuationDescriptor].
 * They contain information about the
 * [types&#32;of&#32;function][FunctionTypeDescriptor] that can appear
 * on the top stack frame for a continuation of this type.
 *
 * Continuations can be
 * [restarted&#32;with&#32;a&#32;new&#32;tuple&#32;of&#32;arguments][P_RestartContinuationWithArguments],
 * so continuation types are contravariant with respect to their function types'
 * argument types.  Surprisingly, continuation types are also contravariant with
 * respect to their function types' return types.  This is due to the capability
 * to [exit][P_ExitContinuationWithResultIf] a continuation with a specific
 * value.
 *
 * Note: If/when function types support checked exceptions we won't need to
 * mention them in continuation types, since invoking a continuation in any way
 * (restart, exit, resume) causes exception obligations/permissions to be
 * instantly voided.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @constructor
 *  Construct a new `ContinuationTypeDescriptor`.
 *
 * @param mutability
 *   The [mutability][Mutability] of the new descriptor.
 */
class ContinuationTypeDescriptor private constructor(mutability: Mutability)
	: TypeDescriptor(
		mutability,
		TypeTag.CONTINUATION_TYPE_TAG,
		TypeTag.CONTINUATION_TAG,
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
		 * [continuation&#32;type][ContinuationTypeDescriptor] supports.
		 * Continuation types are contravariant with respect to the function
		 * type's argument types, and, surprisingly, they are also contravariant
		 * with respect to the function type's return type.
		 */
		FUNCTION_TYPE
	}

	override fun printObjectOnAvoidingIndent(
		self: AvailObject,
		builder: StringBuilder,
		recursionMap: IdentityHashMap<A_BasicObject, Void>,
		indent: Int)
	{
		builder.append('$')
		self.functionType.printOnAvoidingIndent(
			builder,
			recursionMap,
			indent + 1)
	}

	override fun o_Equals(self: AvailObject, another: A_BasicObject): Boolean =
		another.equalsContinuationType(self)

	/**
	 * {@inheritDoc}
	 *
	 * Continuation types compare for equality by comparing their function
	 * types.
	 */
	override fun o_EqualsContinuationType(
		self: AvailObject,
		aContinuationType: A_Type
	): Boolean =
		(self.sameAddressAs(aContinuationType)
			|| aContinuationType.functionType.equals(self.functionType))

	override fun o_FunctionType(self: AvailObject): A_Type =
		self.slot(FUNCTION_TYPE)

	override fun o_Hash(self: AvailObject): Int =
		combine2(self.functionType.hash(), 0x3E20409)

	override fun o_IsSubtypeOf(self: AvailObject, aType: A_Type): Boolean =
		aType.isSupertypeOfContinuationType(self)

	/**
	 * {@inheritDoc}
	 *
	 * Since the only things that can be done with continuations are to restart
	 * them or to exit them, continuation subtypes must accept any values that
	 * could be passed as arguments or as the return value to the supertype.
	 * Therefore, continuation types must be contravariant with respect to the
	 * contained functionType's arguments, and also contravariant with respect
	 * to the contained functionType's result type.
	 */
	override fun o_IsSupertypeOfContinuationType(
		self: AvailObject,
		aContinuationType: A_Type): Boolean
	{
		val subFunctionType = aContinuationType.functionType
		val superFunctionType = self.functionType
		return (superFunctionType.returnType.isSubtypeOf(
				subFunctionType.returnType)
			&& superFunctionType.argsTupleType.isSubtypeOf(
				subFunctionType.argsTupleType))
	}

	override fun o_IsVacuousType(self: AvailObject): Boolean =
		self.slot(FUNCTION_TYPE).isVacuousType

	override fun o_TypeIntersection(
		self: AvailObject,
		another: A_Type
	): A_Type = when
	{
		self.isSubtypeOf(another) -> self
		another.isSubtypeOf(self) -> another
		else -> another.typeIntersectionOfContinuationType(self)
	}

	override fun o_TypeIntersectionOfContinuationType(
		self: AvailObject,
		aContinuationType: A_Type): A_Type
	{
		val functionType1 = self.functionType
		val functionType2 = aContinuationType.functionType
		if (functionType1.equals(functionType2))
		{
			return self
		}
		val argsTupleType = functionType1.argsTupleType.typeUnion(
			functionType2.argsTupleType)
		val returnType = functionType1.returnType.typeUnion(
			functionType2.returnType)
		val intersection = functionTypeFromArgumentTupleType(
			argsTupleType, returnType, emptySet)
		return continuationTypeForFunctionType(intersection)
	}

	override fun o_TypeUnion(self: AvailObject, another: A_Type): A_Type =
		when
		{
			self.isSubtypeOf(another) -> another
			else ->
			{
				if (another.isSubtypeOf(self)) self
				else another.typeUnionOfContinuationType(self)
			}
		}

	override fun o_TypeUnionOfContinuationType(
		self: AvailObject,
		aContinuationType: A_Type): A_Type
	{
		val functionType1 = self.functionType
		val functionType2 = aContinuationType.functionType
		if (functionType1.equals(functionType2))
		{
			// Optimization only
			return self
		}
		val union = functionTypeFromArgumentTupleType(
			functionType1.argsTupleType.typeIntersection(
				functionType2.argsTupleType),
			functionType1.returnType.typeIntersection(
				functionType2.returnType),
			emptySet)
		return continuationTypeForFunctionType(union)
	}

	override fun o_SerializerOperation(self: AvailObject): SerializerOperation =
		SerializerOperation.CONTINUATION_TYPE

	override fun o_WriteTo(self: AvailObject, writer: JSONWriter)
	{
		writer.startObject()
		writer.write("kind")
		writer.write("continuation type")
		writer.write("function type")
		self.slot(FUNCTION_TYPE).writeTo(writer)
		writer.endObject()
	}

	override fun mutable(): ContinuationTypeDescriptor = mutable

	override fun immutable(): ContinuationTypeDescriptor = immutable

	override fun shared(): ContinuationTypeDescriptor = shared

	companion object
	{
		/**
		 * Create a continuation type based on the passed
		 * [function&#32;type][FunctionTypeDescriptor]. Ignore the function
		 * type's exception set.
		 *
		 * @param functionType
		 *   A [function&#32;type][FunctionTypeDescriptor] on which to base
		 *   the new continuation type.
		 * @return
		 *   A new continuation type.
		 */
		fun continuationTypeForFunctionType(
			functionType: A_Type
		): A_Type = mutable.createImmutable {
			setSlot(FUNCTION_TYPE, functionType.makeImmutable())
		}

		/** The mutable [ContinuationTypeDescriptor]. */
		private val mutable = ContinuationTypeDescriptor(Mutability.MUTABLE)

		/** The immutable [ContinuationTypeDescriptor]. */
		private val immutable = ContinuationTypeDescriptor(Mutability.IMMUTABLE)

		/** The shared [ContinuationTypeDescriptor]. */
		private val shared = ContinuationTypeDescriptor(Mutability.SHARED)

		/**
		 * The most general continuation type.  Since continuation types are
		 * contravariant by argument types and contravariant by return type, the
		 * most general type is the one taking bottom as the arguments list
		 * (i.e., not specific enough to be able to call it), and having the
		 * return type bottom.
		 */
		val mostGeneralContinuationType: A_Type =
			continuationTypeForFunctionType(functionTypeReturning(bottom))
				.makeShared()

		/**
		 * The metatype for all continuation types.  In particular, it's just
		 * the [instanceMeta] for the [mostGeneralContinuationType].
		 */
		val continuationMeta: A_Type =
			instanceMeta(mostGeneralContinuationType).makeShared()
	}
}
