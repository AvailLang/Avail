/*
 * InstanceTypeDescriptor.kt
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

import avail.descriptor.atoms.A_Atom
import avail.descriptor.maps.A_Map
import avail.descriptor.maps.A_Map.Companion.keysAsSet
import avail.descriptor.maps.A_Map.Companion.mapSize
import avail.descriptor.maps.A_Map.Companion.valuesAsTuple
import avail.descriptor.numbers.A_Number
import avail.descriptor.numbers.A_Number.Companion.extractLong
import avail.descriptor.numbers.A_Number.Companion.isLong
import avail.descriptor.numbers.IntegerDescriptor.Companion.one
import avail.descriptor.objects.ObjectDescriptor
import avail.descriptor.phrases.A_Phrase.Companion.phraseExpressionType
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.AvailObject
import avail.descriptor.representation.AvailObject.Companion.combine2
import avail.descriptor.representation.Mutability
import avail.descriptor.representation.ObjectSlotsEnum
import avail.descriptor.sets.A_Set
import avail.descriptor.sets.A_Set.Companion.hasElement
import avail.descriptor.sets.A_Set.Companion.isSet
import avail.descriptor.sets.A_Set.Companion.setSize
import avail.descriptor.sets.A_Set.Companion.setWithElementCanDestroy
import avail.descriptor.sets.SetDescriptor.Companion.generateSetFrom
import avail.descriptor.sets.SetDescriptor.Companion.singletonSet
import avail.descriptor.tuples.A_Tuple
import avail.descriptor.tuples.A_Tuple.Companion.asSet
import avail.descriptor.tuples.A_Tuple.Companion.tupleAt
import avail.descriptor.tuples.A_Tuple.Companion.tupleSize
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.generateObjectTupleFrom
import avail.descriptor.types.A_Type.Companion.acceptsArgTypesFromFunctionType
import avail.descriptor.types.A_Type.Companion.acceptsListOfArgTypes
import avail.descriptor.types.A_Type.Companion.acceptsListOfArgValues
import avail.descriptor.types.A_Type.Companion.acceptsTupleOfArgTypes
import avail.descriptor.types.A_Type.Companion.acceptsTupleOfArguments
import avail.descriptor.types.A_Type.Companion.argsTupleType
import avail.descriptor.types.A_Type.Companion.couldEverBeInvokedWith
import avail.descriptor.types.A_Type.Companion.declaredExceptions
import avail.descriptor.types.A_Type.Companion.fieldTypeMap
import avail.descriptor.types.A_Type.Companion.fieldTypeTuple
import avail.descriptor.types.A_Type.Companion.functionType
import avail.descriptor.types.A_Type.Companion.instance
import avail.descriptor.types.A_Type.Companion.instances
import avail.descriptor.types.A_Type.Companion.isSubtypeOf
import avail.descriptor.types.A_Type.Companion.isSupertypeOfPrimitiveTypeEnum
import avail.descriptor.types.A_Type.Companion.readType
import avail.descriptor.types.A_Type.Companion.returnType
import avail.descriptor.types.A_Type.Companion.typeTuple
import avail.descriptor.types.A_Type.Companion.typeUnion
import avail.descriptor.types.A_Type.Companion.writeType
import avail.descriptor.types.BottomTypeDescriptor.Companion.bottom
import avail.descriptor.types.InstanceTypeDescriptor.ObjectSlots.INSTANCE
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.singleInt
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.ANY
import avail.interpreter.levelTwo.operand.TypeRestriction
import avail.optimizer.jvm.CheckedMethod
import avail.optimizer.jvm.CheckedMethod.Companion.staticMethod
import avail.optimizer.jvm.ReferencedInGeneratedCode
import avail.serialization.SerializerOperation
import org.availlang.json.JSONWriter
import java.util.IdentityHashMap
import kotlin.math.max
import kotlin.math.min

/**
 * My instances are called *instance types*, the types of individual objects.
 * In particular, whenever an object is asked for its
 * [type][A_BasicObject.kind], it creates an
 * [instance&#32;type][InstanceTypeDescriptor] that wraps that object.  Only
 * that object is a member of that instance type, except in the case that the
 * object is itself a type, in which case subtypes of that object are also
 * considered instances of the instance type.
 *
 * This last provision is to support the property called
 * *metacovariance*, which states that types' types vary the same way as
 * the types:
 * <span style="border-width:thin; border-style:solid; white-space:nowrap">
 * <sub>x,yT</sub>(xy
 * T(x)T(y))</span>.
 *
 * The uniform use of instance types trivially ensures the additional property
 * we call *metavariance*, which states that every type has a unique
 * type of its own:
 * <span style="border-width:thin; border-style:solid; white-space:nowrap">
 * <sub>x,yT</sub>(xy
 * T(x)T(y))</span>.
 * Note that metavariance requires this to hold for all types, but instance
 * types ensure this condition holds for all objects.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @constructor
 * Construct a new `InstanceTypeDescriptor`.
 *
 * @param mutability
 *   The [mutability][Mutability] of the new descriptor.
 */
class InstanceTypeDescriptor
private constructor(
	mutability: Mutability
) : AbstractEnumerationTypeDescriptor(
	mutability,
	TypeTag.UNKNOWN_TAG,
	ObjectSlots::class.java,
	null)
{
	/**
	 * The layout of object slots for my instances.
	 */
	enum class ObjectSlots : ObjectSlotsEnum
	{
		/**
		 * The [object][AvailObject] for which I am the
		 * [instance&#32;type][InstanceTypeDescriptor].
		 */
		INSTANCE
	}

	override fun printObjectOnAvoidingIndent(
		self: AvailObject,
		builder: StringBuilder,
		recursionMap: IdentityHashMap<A_BasicObject, Void>,
		indent: Int)
	{
		builder.append("{")
		getInstance(self).printOnAvoidingIndent(
			builder,
			recursionMap,
			indent)
		builder.append("}ᵀ")
	}

	/**
	 * Compute the type intersection of the object which is an instance type,
	 * and the argument, which may or may not be an instance type (but must be a
	 * type).
	 *
	 * @param self
	 *   An instance type.
	 * @param another
	 *   Another type.
	 * @return
	 *   The most general type that is a subtype of both object and another.
	 */
	override fun computeIntersectionWith(
		self: AvailObject,
		another: A_Type): A_Type
	{
		if (another.isEnumeration)
		{
			if (another.isInstanceMeta)
			{
				// Intersection of an instance type and an instance meta is
				// always bottom.
				return bottom
			}
			// Create a new enumeration containing all elements that are
			// simultaneously present in object and another.
			return if (another.instances.hasElement(getInstance(self)))
			{
				self
			}
			else bottom
		}
		// Keep the instance if it complies with another, which is not an
		// enumeration.
		return if (getInstance(self).isInstanceOfKind(another))
		{
			self
		}
		else bottom
	}

	/**
	 * Compute the type union of the object, which is an
	 * [instance&#32;type][InstanceTypeDescriptor], and the argument, which may
	 * or may not be an [enumeration][AbstractEnumerationTypeDescriptor] (but
	 * must be a [type][TypeDescriptor]).
	 *
	 * @param self
	 *   An instance type.
	 * @param another
	 *   Another type.
	 * @return
	 *   The most specific type that is a supertype of both self and `another`.
	 */
	override fun computeUnionWith(self: AvailObject, another: A_Type): A_Type =
		when
		{
			another.isEnumeration && another.isInstanceMeta ->
				// Union of an instance type and an instance meta is any.
				ANY.o
			another.isEnumeration ->
				// Create a new enumeration containing all elements from both
				// enumerations.
				enumerationWith(
					another.instances.setWithElementCanDestroy(
						getInstance(self), false))
			// Another is a kind.
			getInstance(self).isInstanceOfKind(another) -> another
			else -> getSuperkind(self).typeUnion(another)
		}

	override fun o_Instance(self: AvailObject): AvailObject = getInstance(self)

	override fun o_InstanceTag(self: AvailObject): TypeTag =
		getInstance(self).typeTag

	override fun o_ComputeSuperkind(self: AvailObject): A_Type =
		getSuperkind(self)

	/**
	 * {@inheritDoc}
	 *
	 * An instance type is only equal to another instance type, and only when
	 * they refer to equal instances.
	 */
	override fun o_Equals(self: AvailObject, another: A_BasicObject): Boolean
	{
		val equal = another.equalsInstanceTypeFor(
			getInstance(self))
		if (equal)
		{
			if (!isShared)
			{
				another.makeImmutable()
				self.becomeIndirectionTo(another)
			}
			else if (!another.descriptor().isShared)
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
	 * An instance type is only equal to another instance type, and only when
	 * they refer to equal instances.
	 */
	override fun o_EqualsInstanceTypeFor(
		self: AvailObject,
		anObject: AvailObject): Boolean = getInstance(self).equals(anObject)

	/**
	 * The potentialInstance is a [user-defined&#32;object][ObjectDescriptor].
	 * See if it is an instance of the object.
	 */
	override fun o_HasObjectInstance(
		self: AvailObject,
		potentialInstance: AvailObject): Boolean =
		getInstance(self).equals(potentialInstance)

	override fun o_Hash(self: AvailObject): Int =
		combine2(getInstance(self).hash(), 0x15d5b163)

	override fun o_IsInstanceOf(self: AvailObject, aType: A_Type): Boolean =
		if (aType.isInstanceMeta)
		{
			// I'm a singular enumeration of a non-type, and aType is an
			// instance meta (the only sort of meta that exists these
			// days -- 2012.07.17).  See if my instance (a non-type) is an
			// instance of aType's instance (a type).
			getInstance(self).isInstanceOf(aType.instance)
		}
		else
		{
			// I'm a singular enumeration of a non-type, so I could only be an
			// instance of a meta (already excluded), or of ANY or TOP.
			aType.isSupertypeOfPrimitiveTypeEnum(ANY)
		}

	override fun o_FieldTypeAt(self: AvailObject, field: A_Atom): A_Type =
		getSuperkind(self).fieldTypeAt(field)

	override fun o_FieldTypeAtOrNull(
		self: AvailObject,
		field: A_Atom
	): A_Type? =
		getSuperkind(self).fieldTypeAtOrNull(field)

	override fun o_FieldTypeMap(self: AvailObject): A_Map =
		getSuperkind(self).fieldTypeMap

	override fun o_FieldTypeTuple(self: AvailObject): A_Tuple =
		getSuperkind(self).fieldTypeTuple

	override fun o_LowerBound(self: AvailObject): A_Number
	{
		val instance = getInstance(self)
		assert(instance.isExtendedInteger)
		return instance
	}

	override fun o_LowerInclusive(self: AvailObject): Boolean
	{
		assert(getInstance(self).isExtendedInteger)
		return true
	}

	override fun o_UpperBound(self: AvailObject): A_Number
	{
		val instance = getInstance(self)
		assert(instance.isExtendedInteger)
		return instance
	}

	override fun o_UpperInclusive(self: AvailObject): Boolean
	{
		assert(getInstance(self).isExtendedInteger)
		return true
	}

	override fun o_TypeAtIndex(self: AvailObject, index: Int): A_Type
	{
		// This is only intended for a TupleType stand-in. Answer what type the
		// given index would have in an object instance of me. Answer ⊥ if the
		// index is out of bounds.
		val tuple: A_Tuple = getInstance(self)
		assert(tuple.isTuple)
		return if (1 <= index && index <= tuple.tupleSize)
		{
			instanceTypeOrMetaOn(tuple.tupleAt(index))
		}
		else bottom
	}

	override fun o_UnionOfTypesAtThrough(
		self: AvailObject,
		startIndex: Int,
		endIndex: Int): A_Type
	{
		// Answer the union of the types that object's instances could have in
		// the given range of indices. Out-of-range indices are treated as ⊥,
		// which don't affect the union (unless all indices are out of range).
		val tuple: A_Tuple = getInstance(self)
		assert(tuple.isTuple)
		if (startIndex > endIndex)
		{
			return bottom
		}
		val upperIndex = tuple.tupleSize
		if (startIndex > upperIndex)
		{
			return bottom
		}
		if (startIndex == endIndex)
		{
			return instanceTypeOrMetaOn(tuple.tupleAt(startIndex))
		}
		val offset = max(startIndex, 1) - 1
		val size = min(endIndex, upperIndex) - offset
		val set = generateSetFrom(size) { tuple.tupleAt(it + offset) }
		return enumerationWith(set)
	}

	override fun o_DefaultType(self: AvailObject): A_Type
	{
		val tuple: A_Tuple = getInstance(self)
		assert(tuple.isTuple)
		val tupleSize = tuple.tupleSize
		return if (tupleSize == 0)
		{
			bottom
		}
		else instanceTypeOrMetaOn(tuple.tupleAt(tupleSize))
	}

	override fun o_SizeRange(self: AvailObject): A_Type =
		getInstance(self).run {
			when
			{
				isTuple -> singleInt(tupleSize)
				isSet -> singleInt(setSize)
				isMap -> singleInt(mapSize)
				else ->
					throw AssertionError("Unexpected instance for sizeRange")
			}
		}

	override fun o_TypeTuple(self: AvailObject): A_Tuple
	{
		assert(getInstance(self).isTuple)
		return getSuperkind(self).typeTuple
	}

	override fun o_IsSubtypeOf(self: AvailObject, aType: A_Type): Boolean =
		getInstance(self).isInstanceOf(aType)

	override fun o_IsIntegerRangeType(self: AvailObject): Boolean =
		getInstance(self).isExtendedInteger

	override fun o_IsLiteralTokenType(self: AvailObject): Boolean =
		getInstance(self).isLiteralToken()

	override fun o_IsMapType(self: AvailObject): Boolean =
		getInstance(self).isMap

	override fun o_IsSetType(self: AvailObject): Boolean =
		getInstance(self).isSet

	override fun o_IsTupleType(self: AvailObject): Boolean =
		getInstance(self).isTuple

	override fun o_InstanceCount(self: AvailObject): A_Number = one

	override fun o_Instances(self: AvailObject): A_Set =
		singletonSet(getInstance(self))

	override fun o_EnumerationIncludesInstance(
		self: AvailObject,
		potentialInstance: AvailObject): Boolean =
			potentialInstance.equals(getInstance(self))

	override fun o_AcceptsArgTypesFromFunctionType(
		self: AvailObject,
		functionType: A_Type): Boolean =
			getSuperkind(self).acceptsArgTypesFromFunctionType(
				functionType)

	override fun o_AcceptsListOfArgTypes(
		self: AvailObject,
		argTypes: List<A_Type>): Boolean =
			getSuperkind(self).acceptsListOfArgTypes(argTypes)

	override fun o_AcceptsListOfArgValues(
		self: AvailObject,
		argValues: List<A_BasicObject>): Boolean =
			getSuperkind(self).acceptsListOfArgValues(argValues)

	override fun o_AcceptsTupleOfArgTypes(
		self: AvailObject,
		argTypes: A_Tuple): Boolean =
			getSuperkind(self).acceptsTupleOfArgTypes(argTypes)

	override fun o_AcceptsTupleOfArguments(
		self: AvailObject,
		arguments: A_Tuple): Boolean =
			getSuperkind(self).acceptsTupleOfArguments(arguments)

	override fun o_ArgsTupleType(self: AvailObject): A_Type =
		getSuperkind(self).argsTupleType

	override fun o_DeclaredExceptions(self: AvailObject): A_Set =
		getSuperkind(self).declaredExceptions

	override fun o_FunctionType(self: AvailObject): A_Type =
		getSuperkind(self).functionType

	override fun o_ContentType(self: AvailObject): A_Type
	{
		/*
		 * Wow, this is weird. Ask a set for its type and you get an instance
		 * type that refers back to the set.  Ask that type for its content type
		 * (since it's technically a set type) and it reports an enumeration
		 * whose sole instance is this set again.
		 */
		val set: A_Set = getInstance(self)
		assert(set.isSet)
		return enumerationWith(set)
	}

	override fun o_CouldEverBeInvokedWith(
		self: AvailObject,
		argRestrictions: List<TypeRestriction>): Boolean =
			getSuperkind(self).couldEverBeInvokedWith(argRestrictions)

	override fun o_KeyType(self: AvailObject): A_Type =
		enumerationWith(getInstance(self).keysAsSet)

	override fun o_ValueType(self: AvailObject): A_Type =
		enumerationWith(getInstance(self).valuesAsTuple.asSet)

	override fun o_Parent(self: AvailObject): A_BasicObject
	{
		unsupportedOperation()
	}

	override fun o_ReturnType(self: AvailObject): A_Type =
		getSuperkind(self).returnType

	override fun o_ReadType(self: AvailObject): A_Type =
		getSuperkind(self).readType

	override fun o_WriteType(self: AvailObject): A_Type =
		getSuperkind(self).writeType

	override fun o_PhraseTypeExpressionType(self: AvailObject): A_Type =
		getInstance(self).phraseExpressionType

	override fun o_RangeIncludesLong(self: AvailObject, aLong: Long): Boolean =
		getInstance(self).run { isLong && extractLong == aLong }

	override fun o_SerializerOperation(self: AvailObject): SerializerOperation =
		SerializerOperation.INSTANCE_TYPE

	override fun o_TrimType(self: AvailObject, typeToRemove: A_Type): A_Type
	{
		if (self.isSubtypeOf(typeToRemove)) return bottom
		return self
	}

	override fun o_TupleOfTypesFromTo(
		self: AvailObject,
		startIndex: Int,
		endIndex: Int): A_Tuple
	{
		// Answer the tuple of types over the given range of indices.  Any
		// indices out of range for this tuple type will be ⊥.
		assert(startIndex >= 1)
		val size = endIndex - startIndex + 1
		assert(size >= 0)
		val tuple: A_Tuple = getInstance(self)
		val tupleSize = tuple.tupleSize
		return generateObjectTupleFrom(size) {
			if (it <= tupleSize)
			{
				instanceTypeOrMetaOn(tuple.tupleAt(it)).makeImmutable()
			}
			else
			{
				bottom
			}
		}
	}

	override fun o_LiteralType(self: AvailObject): A_Type
	{
		val token = getInstance(self)
		val literal = token.literal()
		return instanceTypeOrMetaOn(literal)
	}

	override fun o_WriteTo(self: AvailObject, writer: JSONWriter)
	{
		writer.startObject()
		writer.write("kind")
		getSuperkind(self).writeTo(writer)
		writer.write("instances")
		self.instances.writeTo(writer)
		writer.endObject()
	}

	override fun o_WriteSummaryTo(self: AvailObject, writer: JSONWriter)
	{
		writer.startObject()
		writer.write("kind")
		getSuperkind(self).writeSummaryTo(writer)
		writer.write("instances")
		self.instances.writeSummaryTo(writer)
		writer.endObject()
	}

	override fun o_ComputeTypeTag(self: AvailObject): TypeTag =
		getInstance(self).typeTag.metaTag!!

	override fun o_ComputeInstanceTag(self: AvailObject): TypeTag =
		getInstance(self).typeTag

	override fun mutable(): AbstractEnumerationTypeDescriptor = mutable

	override fun immutable(): AbstractEnumerationTypeDescriptor = immutable

	override fun shared(): AbstractEnumerationTypeDescriptor = shared

	companion object
	{
		/**
		 * Answer the instance that the provided instance type contains.
		 *
		 * @param self
		 *   An instance type.
		 * @return
		 *   The instance represented by the given instance type.
		 */
		private fun getInstance(self: AvailObject): AvailObject =
			self[INSTANCE]

		/**
		 * Answer the kind that is nearest to the given object, an
		 * [instance&#32;type][InstanceTypeDescriptor].
		 *
		 * @param self
		 *   An instance type.
		 * @return
		 *   The kind (a [type][TypeDescriptor] but not an
		 *   [enumeration][AbstractEnumerationTypeDescriptor]) that is nearest
		 *   the specified instance type.
		 */
		private fun getSuperkind(self: AvailObject): A_Type =
			getInstance(self).kind()

		/** The mutable [InstanceTypeDescriptor]. */
		private val mutable: AbstractEnumerationTypeDescriptor =
			InstanceTypeDescriptor(Mutability.MUTABLE)

		/** The immutable [InstanceTypeDescriptor]. */
		private val immutable: AbstractEnumerationTypeDescriptor =
			InstanceTypeDescriptor(Mutability.IMMUTABLE)

		/** The shared [InstanceTypeDescriptor]. */
		private val shared: AbstractEnumerationTypeDescriptor =
			InstanceTypeDescriptor(Mutability.SHARED)

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
		fun instanceType(instance: A_BasicObject): AvailObject =
			mutable.create {
				assert(!instance.isType)
				assert(instance.notNil)
				setSlot(INSTANCE, instance.makeImmutable())
			}

		/**
		 * The [CheckedMethod] for [instanceType].
		 */
		val instanceTypeMethod = staticMethod(
			InstanceTypeDescriptor::class.java,
			::instanceType.name,
			AvailObject::class.java,
			A_BasicObject::class.java)
	}
}
