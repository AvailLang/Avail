/*
 * EnumerationTypeDescriptor.kt
 * Copyright © 1993-2022, The Avail Foundation, LLC.
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
package avail.descriptor.types

import avail.descriptor.atoms.A_Atom
import avail.descriptor.atoms.AtomDescriptor
import avail.descriptor.atoms.AtomDescriptor.Companion.falseObject
import avail.descriptor.atoms.AtomDescriptor.Companion.trueObject
import avail.descriptor.maps.A_Map
import avail.descriptor.maps.A_Map.Companion.keysAsSet
import avail.descriptor.maps.A_Map.Companion.valuesAsTuple
import avail.descriptor.numbers.A_Number
import avail.descriptor.numbers.A_Number.Companion.extractLong
import avail.descriptor.numbers.A_Number.Companion.isLong
import avail.descriptor.numbers.IntegerDescriptor.Companion.fromInt
import avail.descriptor.objects.ObjectDescriptor
import avail.descriptor.phrases.A_Phrase.Companion.phraseExpressionType
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.AbstractSlotsEnum
import avail.descriptor.representation.AvailObject
import avail.descriptor.representation.AvailObject.Companion.combine2
import avail.descriptor.representation.Mutability
import avail.descriptor.representation.NilDescriptor.Companion.nil
import avail.descriptor.representation.ObjectSlotsEnum
import avail.descriptor.sets.A_Set
import avail.descriptor.sets.A_Set.Companion.hasElement
import avail.descriptor.sets.A_Set.Companion.isSet
import avail.descriptor.sets.A_Set.Companion.setElementsAreAllInstancesOfKind
import avail.descriptor.sets.A_Set.Companion.setSize
import avail.descriptor.sets.A_Set.Companion.setUnionCanDestroy
import avail.descriptor.sets.A_Set.Companion.setWithElementCanDestroy
import avail.descriptor.sets.SetDescriptor
import avail.descriptor.sets.SetDescriptor.Companion.emptySet
import avail.descriptor.sets.SetDescriptor.Companion.set
import avail.descriptor.sets.SetDescriptor.Companion.setFromCollection
import avail.descriptor.tuples.A_Tuple
import avail.descriptor.tuples.A_Tuple.Companion.asSet
import avail.descriptor.types.A_Type.Companion.acceptsArgTypesFromFunctionType
import avail.descriptor.types.A_Type.Companion.acceptsListOfArgTypes
import avail.descriptor.types.A_Type.Companion.acceptsListOfArgValues
import avail.descriptor.types.A_Type.Companion.acceptsTupleOfArgTypes
import avail.descriptor.types.A_Type.Companion.acceptsTupleOfArguments
import avail.descriptor.types.A_Type.Companion.argsTupleType
import avail.descriptor.types.A_Type.Companion.contentType
import avail.descriptor.types.A_Type.Companion.couldEverBeInvokedWith
import avail.descriptor.types.A_Type.Companion.declaredExceptions
import avail.descriptor.types.A_Type.Companion.defaultType
import avail.descriptor.types.A_Type.Companion.fieldTypeMap
import avail.descriptor.types.A_Type.Companion.fieldTypeTuple
import avail.descriptor.types.A_Type.Companion.functionType
import avail.descriptor.types.A_Type.Companion.instance
import avail.descriptor.types.A_Type.Companion.instances
import avail.descriptor.types.A_Type.Companion.isSubtypeOf
import avail.descriptor.types.A_Type.Companion.isSupertypeOfPrimitiveTypeEnum
import avail.descriptor.types.A_Type.Companion.lowerBound
import avail.descriptor.types.A_Type.Companion.lowerInclusive
import avail.descriptor.types.A_Type.Companion.parent
import avail.descriptor.types.A_Type.Companion.readType
import avail.descriptor.types.A_Type.Companion.returnType
import avail.descriptor.types.A_Type.Companion.sizeRange
import avail.descriptor.types.A_Type.Companion.tupleOfTypesFromTo
import avail.descriptor.types.A_Type.Companion.typeAtIndex
import avail.descriptor.types.A_Type.Companion.typeIntersection
import avail.descriptor.types.A_Type.Companion.typeTuple
import avail.descriptor.types.A_Type.Companion.typeUnion
import avail.descriptor.types.A_Type.Companion.unionOfTypesAtThrough
import avail.descriptor.types.A_Type.Companion.upperBound
import avail.descriptor.types.A_Type.Companion.upperInclusive
import avail.descriptor.types.A_Type.Companion.writeType
import avail.descriptor.types.BottomTypeDescriptor.Companion.bottom
import avail.descriptor.types.BottomTypeDescriptor.Companion.bottomMeta
import avail.descriptor.types.EnumerationTypeDescriptor.Companion.booleanType
import avail.descriptor.types.EnumerationTypeDescriptor.ObjectSlots.CACHED_SUPERKIND
import avail.descriptor.types.EnumerationTypeDescriptor.ObjectSlots.INSTANCES
import avail.descriptor.types.InstanceMetaDescriptor.Companion.topMeta
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.ANY
import avail.exceptions.unsupported
import avail.interpreter.levelTwo.operand.TypeRestriction
import avail.serialization.SerializerOperation
import org.availlang.json.JSONWriter
import java.util.IdentityHashMap

/**
 * My instances are called *enumerations*. This descriptor family is
 * used for enumerations with two or more instances (i.e., enumerations for
 * which two or more elements survive canonicalization). For the case of one
 * instance, see [InstanceTypeDescriptor], and for the case of zero
 * instances, see [BottomTypeDescriptor].
 *
 * An enumeration is created from a set of objects that are considered instances
 * of the resulting type.  For example, Avail's
 * [boolean&#32;type][booleanType] is simply an enumeration whose instances
 * are [atoms][AtomDescriptor] representing [true][AtomDescriptor.trueObject]
 * and [false][AtomDescriptor.falseObject].  This flexibility allows an
 * enumeration mechanism simply not available in other programming languages. In
 * particular, it allows one to define enumerations whose memberships overlap.
 * The subtype relationship mimics the subset relationship of the enumerations'
 * membership sets.
 *
 * Because of metacovariance and the useful properties it bestows, enumerations
 * that contain a type as a member (i.e., that type is an instance of the union)
 * also automatically include all subtypes as members.  Thus, an enumeration
 * whose instances are {5, "cheese",
 * [tuple][TupleTypeDescriptor.mostGeneralTupleType]} also has the type
 * [string][TupleTypeDescriptor.stringType] as a member (string being one of the
 * many subtypes of tuple).  This condition ensures that enumerations satisfy
 * metacovariance, which states that types' types vary the same way as the
 * types: <span style="border-width:thin; border-style:solid; white-space:
 * nowrap"><sub>x,yT</sub>(xy  T(x)T(y))</span>.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @constructor
 * Construct a new `EnumerationTypeDescriptor`.
 *
 * @param mutability
 *   The [mutability][Mutability] of the new descriptor.
 */
class EnumerationTypeDescriptor
private constructor(
	mutability: Mutability
) : AbstractEnumerationTypeDescriptor(
	mutability,
	TypeTag.UNKNOWN_TAG,
	ObjectSlots::class.java,
	null)
{
	/** The layout of object slots for my instances. */
	enum class ObjectSlots : ObjectSlotsEnum
	{
		/**
		 * The set of [objects][AvailObject] for which I am the
		 * [enumeration][EnumerationTypeDescriptor]. If any of the
		 * objects are [types][TypeDescriptor], then their subtypes
		 * are also automatically members of this enumeration.
		 */
		INSTANCES,

		/**
		 * Either [nil] or this enumeration's nearest superkind (i.e., the
		 * nearest type that isn't a union}.
		 */
		CACHED_SUPERKIND
	}

	/**
	 * Answer my nearest superkind (the most specific supertype of me that isn't
	 * also an [enumeration][AbstractEnumerationTypeDescriptor]). Do not acquire
	 * the argument's monitor.
	 *
	 * @param self
	 *   An enumeration.
	 * @return
	 *   The kind closest to the given enumeration.
	 */
	private fun rawGetSuperkind(self: AvailObject): A_Type
	{
		var cached: A_Type = self[CACHED_SUPERKIND]
		if (cached.isNil)
		{
			cached = bottom
			for (instance in getInstances(self))
			{
				cached = cached.typeUnion(instance.kind())
				if (cached.equals(ANY.o))
				{
					break
				}
			}
			if (isShared)
			{
				cached = cached.traversed().makeShared()
			}
			self[CACHED_SUPERKIND] = cached
		}
		return cached
	}

	/**
	 * Answer my nearest superkind (the most specific supertype of me that isn't
	 * also an [enumeration][AbstractEnumerationTypeDescriptor]).
	 *
	 * @param self
	 *   An enumeration.
	 * @return
	 *   The kind closest to the given enumeration.
	 */
	private fun getSuperkind(self: AvailObject): A_Type
	{
		if (isShared)
		{
			synchronized(self) { return rawGetSuperkind(self) }
		}
		return rawGetSuperkind(self)
	}

	override fun allowsImmutableToMutableReferenceInField(
		e: AbstractSlotsEnum): Boolean = e === CACHED_SUPERKIND

	override fun o_ComputeSuperkind(self: AvailObject): A_Type =
		getSuperkind(self)

	override fun o_InstanceCount(self: AvailObject): A_Number =
		fromInt(getInstances(self).setSize)

	override fun o_Instances(self: AvailObject): A_Set = getInstances(self)

	override fun printObjectOnAvoidingIndent(
		self: AvailObject,
		builder: StringBuilder,
		recursionMap: IdentityHashMap<A_BasicObject, Void>,
		indent: Int)
	{
		// Print boolean specially.
		if (self.equals(booleanType))
		{
			builder.append("boolean")
			return
		}
		// Default printing.
		getInstances(self).printOnAvoidingIndent(
			builder,
			recursionMap,
			indent + 1)
		builder.append("ᵀ")
	}

	/**
	 * {@inheritDoc}
	 *
	 * An instance type is only equal to another instance type, and only when
	 * they refer to equal instances.
	 */
	override fun o_Equals(self: AvailObject, another: A_BasicObject): Boolean
	{
		val equal = another.equalsEnumerationWithSet(getInstances(self))
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

	override fun o_EqualsEnumerationWithSet(
		self: AvailObject,
		aSet: A_Set
	): Boolean = getInstances(self).equals(aSet)

	/**
	 * The potentialInstance is a [user-defined&#32;object][ObjectDescriptor].
	 * See if it is an instance of the object. It is an instance precisely when
	 * it is in object's set of [instances][ObjectSlots.INSTANCES], or if it is
	 * a subtype of any type that occurs in the set of instances.
	 */
	override fun o_HasObjectInstance(
		self: AvailObject,
		potentialInstance: AvailObject
	): Boolean = getInstances(self).hasElement(potentialInstance)

	override fun o_Hash(self: AvailObject): Int =
		combine2(getInstances(self).hash(), 0x15b5b059)

	override fun o_IsInstanceOf(self: AvailObject, aType: A_Type): Boolean
	{
		if (aType.isInstanceMeta)
		{
			// I'm an enumeration of non-types, and aType is an instance meta
			// (the only sort of metas that exist these days -- 2012.07.17).
			// See if my instances comply with aType's instance (a type).
			val aTypeInstance = aType.instance
			val instanceSet = getInstances(self)
			if (aTypeInstance.isEnumeration)
			{
				// Check the complete membership.
				return instanceSet.all(
					aTypeInstance::enumerationIncludesInstance)
			}
			return instanceSet.setElementsAreAllInstancesOfKind(aTypeInstance)
		}
		// I'm an enumeration of non-types, so I could only be an instance of a
		// meta (already excluded), or of ANY or TOP.
		return aType.isSupertypeOfPrimitiveTypeEnum(ANY)
	}

	/**
	 * Compute the type intersection of the object, which is an
	 * [enumeration][EnumerationTypeDescriptor], and the argument, which may or
	 * may not be an enumeration (but must be a [ype][TypeDescriptor]).
	 *
	 * @param self
	 *   An enumeration.
	 * @param another
	 *   Another type.
	 * @return
	 *   The most general type that is a subtype of both self and `another`.
	 */
	override fun computeIntersectionWith(
		self: AvailObject,
		another: A_Type): A_Type
	{
		assert(another.isType)
		var set = emptySet
		val elements = getInstances(self)
		if (another.isEnumeration)
		{
			// Create a new enumeration containing all non-type elements that
			// are simultaneously present in object and another, plus the type
			// intersections of all pairs of types in the product of the sets.
			// This should even correctly deal with bottom as an element.
			val otherElements = another.instances
			var myTypes = emptySet
			for (element in elements)
			{
				if (element.isType)
				{
					myTypes = myTypes.setWithElementCanDestroy(element, true)
				}
				else if (otherElements.hasElement(element))
				{
					set = set.setWithElementCanDestroy(element, true)
				}
			}
			// We have the non-types now, so add the pair-wise intersection of
			// the types.
			if (myTypes.setSize > 0)
			{
				for (anotherElement in otherElements)
				{
					if (anotherElement.isType)
					{
						for (myType in myTypes)
						{
							set = set.setWithElementCanDestroy(
								anotherElement.typeIntersection(myType),
								true)
						}
					}
				}
			}
		}
		else
		{
			// Keep the instances that comply with another, which is not a union
			// type.
			for (element in getInstances(self))
			{
				if (element.isInstanceOfKind(another))
				{
					set = set.setWithElementCanDestroy(element, true)
				}
			}
		}
		if (set.setSize == 0)
		{
			// Decide whether this should be bottom or bottom's type
			// based on whether object and another are both metas.  Note that
			// object is a meta precisely when one of its instances is a type.
			// One more thing:  The special case of another being bottom should
			// not be treated as being a meta for our purposes, even though
			// bottom technically is a meta.
			if (self.isSubtypeOf(topMeta)
				&& another.isSubtypeOf(topMeta)
				&& !another.isBottom)
			{
				return bottomMeta
			}
		}
		return enumerationWith(set)
	}

	/**
	 * Compute the type union of the object, which is an
	 * [enumeration][EnumerationTypeDescriptor], and the argument, which may or
	 * may not be an enumeration (but must be a [type][TypeDescriptor]).
	 *
	 * @param self
	 *   An enumeration.
	 * @param another
	 *   Another type.
	 * @return
	 *   The most general type that is a subtype of both self and `another`.
	 */
	override fun computeUnionWith(
		self: AvailObject,
		another: A_Type
	): A_Type = when (another.isEnumeration)
	{
		// Create a new enumeration containing all elements from both
		// enumerations.
		true -> enumerationWith(getInstances(self).setUnionCanDestroy(
			another.instances, false))
		// Go up to my nearest kind, then compute the union with the given kind.
		else -> getInstances(self).fold(another) {
			type, instance -> type.typeUnion(instance.kind())
		}
	}

	override fun o_FieldTypeAt(self: AvailObject, field: A_Atom): A_Type =
		getSuperkind(self).fieldTypeAt(field)

	override fun o_FieldTypeAtOrNull(
		self: AvailObject,
		field: A_Atom
	): A_Type? = getSuperkind(self).fieldTypeAtOrNull(field)

	override fun o_FieldTypeTuple(self: AvailObject): A_Tuple =
		getSuperkind(self).fieldTypeTuple

	override fun o_FieldTypeMap(self: AvailObject): A_Map =
		getSuperkind(self).fieldTypeMap

	override fun o_LowerBound(self: AvailObject): A_Number =
		getSuperkind(self).lowerBound

	override fun o_LowerInclusive(self: AvailObject): Boolean =
		getSuperkind(self).lowerInclusive

	override fun o_UpperBound(self: AvailObject): A_Number =
		getSuperkind(self).upperBound

	override fun o_UpperInclusive(self: AvailObject): Boolean =
		getSuperkind(self).upperInclusive

	override fun o_EnumerationIncludesInstance(
		self: AvailObject,
		potentialInstance: AvailObject
	): Boolean = getInstances(self).hasElement(potentialInstance)


	override fun o_TypeAtIndex(
		self: AvailObject,
		index: Int
	): A_Type
	{
		// This is only intended for a TupleType stand-in. Answer what type the
		// given index would have in an object instance of me. Answer
		// bottom if the index is out of bounds.
		assert(self.isTupleType)
		return getSuperkind(self).typeAtIndex(index)
	}

	override fun o_UnionOfTypesAtThrough(
		self: AvailObject,
		startIndex: Int,
		endIndex: Int
	): A_Type
	{
		// Answer the union of the types that object's instances could have in
		// the given range of indices. Out-of-range indices are treated as
		// bottom, which don't affect the union (unless all indices are out
		// of range).
		assert(self.isTupleType)
		return getSuperkind(self).unionOfTypesAtThrough(startIndex, endIndex)
	}

	override fun o_DefaultType(self: AvailObject): A_Type
	{
		assert(self.isTupleType)
		return getSuperkind(self).defaultType
	}

	override fun o_SizeRange(self: AvailObject): A_Type =
		getSuperkind(self).sizeRange

	override fun o_TypeTuple(self: AvailObject): A_Tuple =
		getSuperkind(self).typeTuple

	/**
	 * Check if object (an enumeration) is a subtype of aType (should also be a
	 * type).  All members of me must also be instances of aType.
	 */
	override fun o_IsSubtypeOf(self: AvailObject, aType: A_Type): Boolean =
		getInstances(self).all { it.isInstanceOf(aType) }

	override fun o_IsIntegerRangeType(self: AvailObject): Boolean =
		getInstances(self).all(AvailObject::isExtendedInteger)

	override fun o_IsLiteralTokenType(self: AvailObject): Boolean =
		getInstances(self).all(AvailObject::isLiteralToken)

	override fun o_IsMapType(self: AvailObject): Boolean =
		getInstances(self).all(AvailObject::isMap)

	override fun o_IsSetType(self: AvailObject): Boolean =
		getInstances(self).all { it.isSet }

	override fun o_IsTupleType(self: AvailObject): Boolean =
		getInstances(self).all(AvailObject::isTuple)

	override fun o_AcceptsArgTypesFromFunctionType(
		self: AvailObject,
		functionType: A_Type): Boolean =
			getSuperkind(self).acceptsArgTypesFromFunctionType(functionType)

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

	override fun o_ContentType(self: AvailObject): A_Type =
		getSuperkind(self).contentType

	override fun o_CouldEverBeInvokedWith(
		self: AvailObject,
		argRestrictions: List<TypeRestriction>): Boolean =
			getSuperkind(self).couldEverBeInvokedWith(argRestrictions)

	// An enumeration with a cached superkind is pretty good.
	override fun o_IsBetterRepresentationThan(
		self: AvailObject,
		anotherObject: A_BasicObject): Boolean =
			self.mutableSlot(CACHED_SUPERKIND).notNil

	override fun o_KeyType(self: AvailObject): A_Type
	{
		val possibleMaps = self.instances
		val possibleKeys = possibleMaps.fold(emptySet) { union, instance ->
			union.setUnionCanDestroy(instance.keysAsSet, false)
		}
		return enumerationWith(possibleKeys)
	}

	override fun o_ValueType(self: AvailObject): A_Type
	{
		val possibleMaps = self.instances
		val possibleKeys = possibleMaps.fold(emptySet) { union, instance ->
			union.setUnionCanDestroy(instance.valuesAsTuple.asSet, false)
		}
		return enumerationWith(possibleKeys)
	}

	override fun o_Parent(self: AvailObject): A_BasicObject =
		getSuperkind(self).parent

	override fun o_ReturnType(self: AvailObject): A_Type =
		getSuperkind(self).returnType

	override fun o_MarshalToJava(self: AvailObject, classHint: Class<*>?): Any?
	{
		return if (self.isSubtypeOf(booleanType))
		{
			Boolean::class.javaPrimitiveType
		}
		else super.o_MarshalToJava(self, classHint)
	}

	override fun o_ReadType(self: AvailObject): A_Type =
		getSuperkind(self).readType

	override fun o_WriteType(self: AvailObject): A_Type =
		getSuperkind(self).writeType

	override fun o_PhraseTypeExpressionType(self: AvailObject): A_Type
	{
		var unionType = bottom
		for (instance in getInstances(self))
		{
			unionType = unionType.typeUnion(instance.phraseExpressionType)
		}
		return unionType
	}

	override fun o_RangeIncludesLong(self: AvailObject, aLong: Long): Boolean =
		getInstances(self).any { it.isLong && it.extractLong == aLong }

	override fun o_SerializerOperation(self: AvailObject): SerializerOperation =
		SerializerOperation.ENUMERATION_TYPE

	override fun o_TrimType(self: AvailObject, typeToRemove: A_Type): A_Type
	{
		val values = getInstances(self).filterNot {
			it.isInstanceOf(typeToRemove)
		}
		return enumerationWith(setFromCollection(values))
	}

	override fun o_TupleOfTypesFromTo(
		self: AvailObject,
		startIndex: Int,
		endIndex: Int
	): A_Tuple =
		getSuperkind(self).tupleOfTypesFromTo(startIndex, endIndex)

	override fun o_WriteTo(self: AvailObject, writer: JSONWriter)
	{
		writer.startObject()
		writer.write("kind")
		getSuperkind(self).writeTo(writer)
		writer.write("instances")
		self[INSTANCES].writeTo(writer)
		writer.endObject()
	}

	override fun o_WriteSummaryTo(self: AvailObject, writer: JSONWriter)
	{
		writer.startObject()
		writer.write("kind")
		getSuperkind(self).writeSummaryTo(writer)
		writer.write("instances")
		self[INSTANCES].writeSummaryTo(writer)
		writer.endObject()
	}

	override fun o_ComputeTypeTag(self: AvailObject): TypeTag =
		getInstances(self)
			.map(AvailObject::typeTag)
			.reduce(TypeTag::commonAncestorWith)
			.metaTag!!

	override fun o_InstanceTag(self: AvailObject): TypeTag =
		getInstances(self)
			.map(AvailObject::typeTag)
			.reduce(TypeTag::commonAncestorWith)

	override fun o_ComputeInstanceTag(self: AvailObject): TypeTag = unsupported

	override fun mutable(): AbstractEnumerationTypeDescriptor = mutable

	override fun immutable(): AbstractEnumerationTypeDescriptor = immutable

	override fun shared(): AbstractEnumerationTypeDescriptor = shared

	companion object
	{
		/**
		 * Extract my set of instances. If any object is itself a type then all
		 * of its subtypes are automatically instances, but they're not returned
		 * by this method. Also, any object that's a type and has a supertype in
		 * this set will have been removed during creation of this enumeration.
		 *
		 * @param self
		 *   The enumeration for which to extract the instances.
		 * @return
		 *   The instances of this enumeration.
		 */
		fun getInstances(self: AvailObject): A_Set = self[INSTANCES]

		/**
		 * Construct an enumeration type from a [set][SetDescriptor] with at
		 * least two instances. The set must have already been normalized, such
		 * that at most one of the elements is itself a [type][TypeDescriptor].
		 *
		 * @param normalizedSet
		 *   The set of instances.
		 * @return
		 *   The resulting enumeration.
		 */
		fun fromNormalizedSet(normalizedSet: A_Set): A_Type
		{
			assert(normalizedSet.setSize > 1)
			return mutable.create {
				setSlot(INSTANCES, normalizedSet.makeImmutable())
				setSlot(CACHED_SUPERKIND, nil)
			}
		}

		/** The mutable [EnumerationTypeDescriptor]. */
		private val mutable: AbstractEnumerationTypeDescriptor =
			EnumerationTypeDescriptor(Mutability.MUTABLE)

		/** The immutable [EnumerationTypeDescriptor]. */
		private val immutable: AbstractEnumerationTypeDescriptor =
			EnumerationTypeDescriptor(Mutability.IMMUTABLE)

		/** The shared [EnumerationTypeDescriptor]. */
		private val shared: AbstractEnumerationTypeDescriptor =
			EnumerationTypeDescriptor(Mutability.SHARED)

		/**
		 * Avail's boolean type, the equivalent of Java's primitive `boolean`
		 * pseudo-type, similar to Java's boxed [Boolean] class.
		 */
		val booleanType: A_Type

		/**
		 * The type whose only instance is the value
		 * [true][AtomDescriptor.trueObject].
		 */
		val trueType: A_Type

		/**
		 * The type whose only instance is the value
		 * [false][AtomDescriptor.falseObject].
		 */
		val falseType: A_Type

		init
		{
			booleanType =
				enumerationWith(set(trueObject, falseObject)).makeShared()
			trueType = instanceTypeOrMetaOn(trueObject).makeShared()
			falseType = instanceTypeOrMetaOn(falseObject).makeShared()
		}
	}
}
