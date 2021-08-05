/*
 * InstanceMetaDescriptor.kt
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

import com.avail.descriptor.atoms.A_Atom
import com.avail.descriptor.maps.A_Map
import com.avail.descriptor.numbers.A_Number
import com.avail.descriptor.numbers.IntegerDescriptor.Companion.one
import com.avail.descriptor.representation.A_BasicObject
import com.avail.descriptor.representation.AvailObject
import com.avail.descriptor.representation.AvailObject.Companion.combine2
import com.avail.descriptor.representation.IndirectionDescriptor
import com.avail.descriptor.representation.Mutability
import com.avail.descriptor.representation.ObjectSlotsEnum
import com.avail.descriptor.sets.A_Set
import com.avail.descriptor.sets.SetDescriptor.Companion.singletonSet
import com.avail.descriptor.tuples.A_Tuple
import com.avail.descriptor.types.A_Type.Companion.instance
import com.avail.descriptor.types.A_Type.Companion.instances
import com.avail.descriptor.types.A_Type.Companion.isSubtypeOf
import com.avail.descriptor.types.A_Type.Companion.isSupertypeOfPrimitiveTypeEnum
import com.avail.descriptor.types.A_Type.Companion.typeIntersection
import com.avail.descriptor.types.A_Type.Companion.typeUnion
import com.avail.descriptor.types.BottomTypeDescriptor.Companion.bottom
import com.avail.descriptor.types.InstanceMetaDescriptor.ObjectSlots.INSTANCE
import com.avail.descriptor.types.TypeDescriptor.Types
import com.avail.descriptor.types.TypeDescriptor.Types.ANY
import com.avail.interpreter.levelTwo.operand.TypeRestriction
import com.avail.optimizer.jvm.CheckedMethod
import com.avail.optimizer.jvm.CheckedMethod.Companion.staticMethod
import com.avail.optimizer.jvm.ReferencedInGeneratedCode
import com.avail.serialization.SerializerOperation
import com.avail.utility.json.JSONWriter
import java.util.IdentityHashMap

/**
 * My instances are called *instance metas*, the types of types.  These are the
 * only representation (modulo [indirection&#32;objects][IndirectionDescriptor])
 * of metatypes in Avail, as attempting to carry enumeration types up the
 * instance-of hierarchy leads to an unsound type theory.
 *
 * An `instance meta` behaves much like an
 * [instance&#32;type][InstanceTypeDescriptor] but always has a type as its
 * instance (which normal instance types are forbidden to have).
 *
 * Instance metas preserve metacovariance:
 * <span style="border-width:thin; border-style:solid; white-space:nowrap">
 * <sub>x,yT</sub>(xy
 * T(x)T(y))</span>.
 *
 * The uniform use of instance types trivially ensures the additional, stronger
 * property we call *metavariance*, which states that every type has a
 * unique type of its own:
 * <span style="border-width:thin; border-style:solid; white-space: nowrap">
 * <sub>x,yT</sub>(xy
 * T(x)T(y))</span>.
 * Note that metavariance requires this to hold for all types, but instance
 * types ensure this condition holds for all objects.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @constructor
 * Construct a new `InstanceMetaDescriptor`.
 *
 * @param mutability
 *   The [mutability][Mutability] of the new descriptor.
 */
class InstanceMetaDescriptor private constructor(mutability: Mutability)
	: AbstractEnumerationTypeDescriptor(
		mutability, TypeTag.UNKNOWN_TAG, ObjectSlots::class.java, null)
{
	/**
	 * The layout of object slots for my instances.
	 */
	enum class ObjectSlots : ObjectSlotsEnum
	{
		/**
		 * The [type][TypeDescriptor] for which I am the
		 * [instance&#32;meta][InstanceTypeDescriptor].
		 */
		INSTANCE
	}

	override fun printObjectOnAvoidingIndent(
		self: AvailObject,
		builder: StringBuilder,
		recursionMap: IdentityHashMap<A_BasicObject, Void>,
		indent: Int)
	{
		builder.append("(")
		getInstance(self).printOnAvoidingIndent(
			builder,
			recursionMap,
			indent)
		builder.append(")'s type")
	}

	/**
	 * Compute the type intersection of the object which is an instance meta,
	 * and the argument, which is some type (it may be an
	 * [enumeration][AbstractEnumerationTypeDescriptor]).
	 *
	 * @param self
	 *   An instance meta.
	 * @param another
	 *   Another type.
	 * @return
	 *   The most general type that is a subtype of both object and another.
	 */
	override fun computeIntersectionWith(
		self: AvailObject,
		another: A_Type
	): A_Type = when
	{
		another.isBottom -> another
		another.isInstanceMeta ->
			instanceMeta(
				getInstance(self).typeIntersection(another.instance))
		another.isSupertypeOfPrimitiveTypeEnum(ANY) -> self
		else -> bottom
	}

	/**
	 * Compute the type union of the object, which is an [instance
	 * meta][InstanceMetaDescriptor], and the argument, which may or may not be
	 * an [enumeration][AbstractEnumerationTypeDescriptor] (but must be a
	 * [type][TypeDescriptor]).
	 *
	 * @param self
	 *   An instance meta.
	 * @param another
	 *   Another type.
	 * @return
	 *   The most specific type that is a supertype of both self and `another`.
	 */
	public override fun computeUnionWith(
		self: AvailObject,
		another: A_Type
	): A_Type = when
	{
		another.isBottom -> self
		another.isInstanceMeta ->
			instanceMeta(getInstance(self).typeUnion(another.instance))
		// Unless another is top, then the answer will be any.
		else -> ANY.o.typeUnion(another)
	}

	override fun o_Instance(self: AvailObject): AvailObject = getInstance(self)

	override fun o_IsInstanceMeta(self: AvailObject): Boolean = true

	override fun o_ComputeSuperkind(self: AvailObject): A_Type = ANY.o

	/**
	 * {@inheritDoc}
	 *
	 * An instance meta is only equal to another instance meta, and only when
	 * they refer to equal instances.
	 */
	override fun o_Equals(self: AvailObject, another: A_BasicObject): Boolean
	{
		val equal = (another.isInstanceMeta
			 && getInstance(self).equals((another as A_Type).instance))
		when
		{
			!equal -> return false
			!isShared ->
			{
				another.makeImmutable()
				self.becomeIndirectionTo(another)
			}
			!another.descriptor().isShared ->
			{
				self.makeImmutable()
				another.becomeIndirectionTo(self)
			}
		}
		return equal
	}

	/**
	 * {@inheritDoc}
	 *
	 * An instance meta is never equal to an instance type.
	 */
	override fun o_EqualsInstanceTypeFor(
		self: AvailObject,
		anObject: AvailObject): Boolean = false

	override fun o_Hash(self: AvailObject): Int =
		combine2(getInstance(self).hash(), 0x361b5d51)

	override fun o_IsSubtypeOf(self: AvailObject, aType: A_Type): Boolean =
		getInstance(self).isInstanceOf(aType)

	// Technically my instance is the instance I specify, which is a type,
	// *plus* all subtypes of it.  However, to distinguish metas from kinds
	// we need it to answer one here.
	override fun o_InstanceCount(self: AvailObject): A_Number = one

	override fun o_Instances(self: AvailObject): A_Set =
		singletonSet(getInstance(self))

	override fun o_EnumerationIncludesInstance(
		self: AvailObject,
		potentialInstance: AvailObject): Boolean =
			(potentialInstance.isType
				&& potentialInstance.isSubtypeOf(getInstance(self)))

	override fun o_IsInstanceOf(self: AvailObject, aType: A_Type): Boolean =
		if (aType.isInstanceMeta)
		{
			// I'm an instance meta on some type, and aType is (also) an
			// instance meta (the only sort of meta that exists these
			// days -- 2012.07.17).  See if my instance (a type) is an
			// instance of aType's instance (also a type, but maybe a meta).
			getInstance(self).isInstanceOf(aType.instance)
		}
		else
		{
			// I'm a meta, a singular enumeration of a type, so I could only be
			// an instance of a meta meta (already excluded), or of ANY or TOP.
			aType.isSupertypeOfPrimitiveTypeEnum(ANY)
		}

	// A metatype can't have an integer as an instance.
	override fun o_RangeIncludesLong(self: AvailObject, aLong: Long): Boolean =
		false

	override fun o_FieldTypeAt(self: AvailObject, field: A_Atom): A_Type =
		unsupported

	override fun o_FieldTypeAtOrNull(
		self: AvailObject,
		field: A_Atom
	): A_Type = unsupported

	override fun o_FieldTypeMap(self: AvailObject): A_Map = unsupported

	override fun o_LowerBound(self: AvailObject): A_Number = unsupported

	override fun o_LowerInclusive(self: AvailObject): Boolean = unsupported

	override fun o_UpperBound(self: AvailObject): A_Number = unsupported

	override fun o_UpperInclusive(self: AvailObject): Boolean = unsupported

	override fun o_TypeAtIndex(self: AvailObject, index: Int): A_Type =
		unsupported

	override fun o_UnionOfTypesAtThrough(
		self: AvailObject,
		startIndex: Int,
		endIndex: Int): A_Type = unsupported

	override fun o_DefaultType(self: AvailObject): A_Type = unsupported

	override fun o_SizeRange(self: AvailObject): A_Type = unsupported

	override fun o_TupleOfTypesFromTo(
		self: AvailObject,
		startIndex: Int,
		endIndex: Int): A_Tuple = unsupported

	override fun o_TypeTuple(self: AvailObject): A_Tuple = unsupported

	// A metatype can't be an integer range type.
	override fun o_IsIntegerRangeType(self: AvailObject): Boolean = false

	// A metatype can't be a literal token type.
	override fun o_IsLiteralTokenType(self: AvailObject): Boolean = false

	// A metatype can't be a map type.
	override fun o_IsMapType(self: AvailObject): Boolean = false

	// A metatype can't be a set type.
	override fun o_IsSetType(self: AvailObject): Boolean = false

	// A metatype can't be a tuple type.
	override fun o_IsTupleType(self: AvailObject): Boolean = false

	override fun o_AcceptsArgTypesFromFunctionType(
		self: AvailObject,
		functionType: A_Type): Boolean = unsupported

	override fun o_AcceptsListOfArgTypes(
		self: AvailObject,
		argTypes: List<A_Type>): Boolean = unsupported

	override fun o_AcceptsListOfArgValues(
		self: AvailObject,
		argValues: List<A_BasicObject>): Boolean = unsupported

	override fun o_AcceptsTupleOfArgTypes(
		self: AvailObject,
		argTypes: A_Tuple): Boolean = unsupported

	override fun o_AcceptsTupleOfArguments(
		self: AvailObject,
		arguments: A_Tuple): Boolean = unsupported

	override fun o_ArgsTupleType(self: AvailObject): A_Type = unsupported

	override fun o_DeclaredExceptions(self: AvailObject): A_Set = unsupported

	override fun o_FunctionType(self: AvailObject): A_Type = unsupported

	override fun o_ContentType(self: AvailObject): A_Type = unsupported

	override fun o_CouldEverBeInvokedWith(
		self: AvailObject,
		argRestrictions: List<TypeRestriction>): Boolean = unsupported

	override fun o_KeyType(self: AvailObject): A_Type = unsupported

	override fun o_ValueType(self: AvailObject): A_Type = unsupported

	override fun o_Parent(self: AvailObject): A_BasicObject = unsupported

	override fun o_ReturnType(self: AvailObject): A_Type = unsupported

	override fun o_ReadType(self: AvailObject): A_Type = unsupported

	override fun o_WriteType(self: AvailObject): A_Type = unsupported

	override fun o_PhraseTypeExpressionType(self: AvailObject): A_Type =
		unsupported

	override fun o_HasObjectInstance(
		self: AvailObject,
		potentialInstance: AvailObject): Boolean = unsupported

	override fun o_SerializerOperation(self: AvailObject): SerializerOperation =
		SerializerOperation.INSTANCE_META

	override fun o_WriteTo(self: AvailObject, writer: JSONWriter) =
		writer.writeObject {
			at("kind") { ANY.o.writeTo(writer) }
			at("instances") { self.instances.writeTo(writer) }
		}

	override fun o_WriteSummaryTo(self: AvailObject, writer: JSONWriter) =
		writer.writeObject {
			at("kind") { ANY.o.writeSummaryTo(writer) }
			at("instances") { self.instances.writeSummaryTo(writer) }
		}

	override fun o_ComputeTypeTag(self: AvailObject): TypeTag =
		getInstance(self).typeTag().metaTag()

	override fun o_TrimType(self: AvailObject, typeToRemove: A_Type): A_Type
	{
		if (self.isSubtypeOf(typeToRemove)) return bottom
		return self
	}

	override fun mutable(): AbstractEnumerationTypeDescriptor = mutable

	override fun immutable(): AbstractEnumerationTypeDescriptor = immutable

	override fun shared(): AbstractEnumerationTypeDescriptor = shared

	companion object
	{
		/**
		 * Answer the instance (a type) that the provided instance meta
		 * contains.
		 *
		 * @param self
		 *   An instance type.
		 * @return
		 *   The instance represented by the given instance type.
		 */
		private fun getInstance(self: AvailObject): AvailObject =
			self.slot(INSTANCE)

		/** The mutable [InstanceMetaDescriptor]. */
		private val mutable: AbstractEnumerationTypeDescriptor =
			InstanceMetaDescriptor(Mutability.MUTABLE)

		/** The immutable [InstanceMetaDescriptor]. */
		private val immutable: AbstractEnumerationTypeDescriptor =
			InstanceMetaDescriptor(Mutability.IMMUTABLE)

		/** The shared [InstanceMetaDescriptor]. */
		private val shared: AbstractEnumerationTypeDescriptor =
			InstanceMetaDescriptor(Mutability.SHARED)

		/**
		 * `⊤`'s type, cached statically for convenience.
		 */
		private val topMeta: A_Type = instanceMeta(Types.TOP.o).makeShared()

		/**
		 * Answer ⊤'s type, the most general metatype.
		 *
		 * @return
		 *   `⊤`'s type.
		 */
		fun topMeta(): A_Type = topMeta

		/**
		 * Any's type, cached statically for convenience.
		 */
		private val anyMeta: A_Type = instanceMeta(ANY.o).makeShared()

		/**
		 * Answer any's type, a metatype.
		 *
		 * @return
		 *   `any`'s type.
		 */
		fun anyMeta(): A_Type = anyMeta

		/**
		 * Answer a new instance of this descriptor based on some object whose
		 * type it will represent.
		 *
		 * @param instance
		 *   The object whose type to represent.
		 * @return
		 *   An [AvailObject] representing the type of the argument.
		 */
		@ReferencedInGeneratedCode
		@JvmStatic
		fun instanceMeta(instance: A_Type): A_Type = mutable.create {
			assert(instance.isType)
			setSlot(INSTANCE, instance.makeImmutable())
		}

		/**
		 * The [CheckedMethod] for [instanceMeta].
		 */
		val instanceMetaMethod = staticMethod(
			InstanceMetaDescriptor::class.java,
			::instanceMeta.name,
			A_Type::class.java,
			A_Type::class.java)
	}
}
