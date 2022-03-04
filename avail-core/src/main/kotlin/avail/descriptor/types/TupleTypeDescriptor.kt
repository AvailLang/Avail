/*
 * TupleTypeDescriptor.kt
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
package avail.descriptor.types

import avail.annotations.ThreadSafe
import avail.descriptor.numbers.A_Number
import avail.descriptor.numbers.A_Number.Companion.equalsInt
import avail.descriptor.numbers.A_Number.Companion.extractInt
import avail.descriptor.numbers.A_Number.Companion.extractLong
import avail.descriptor.numbers.A_Number.Companion.greaterOrEqual
import avail.descriptor.numbers.A_Number.Companion.greaterThan
import avail.descriptor.numbers.A_Number.Companion.isInt
import avail.descriptor.numbers.A_Number.Companion.lessThan
import avail.descriptor.numbers.IntegerDescriptor.Companion.fromInt
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.AvailObject
import avail.descriptor.representation.AvailObject.Companion.combine4
import avail.descriptor.representation.Mutability
import avail.descriptor.representation.ObjectSlotsEnum
import avail.descriptor.tuples.A_Tuple
import avail.descriptor.tuples.A_Tuple.Companion.copyTupleFromToCanDestroy
import avail.descriptor.tuples.A_Tuple.Companion.tupleAt
import avail.descriptor.tuples.A_Tuple.Companion.tupleAtPuttingCanDestroy
import avail.descriptor.tuples.A_Tuple.Companion.tupleSize
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.generateObjectTupleFrom
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tupleFromArray
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tupleFromList
import avail.descriptor.tuples.TupleDescriptor
import avail.descriptor.tuples.TupleDescriptor.Companion.emptyTuple
import avail.descriptor.types.A_Type.Companion.computeSuperkind
import avail.descriptor.types.A_Type.Companion.defaultType
import avail.descriptor.types.A_Type.Companion.isSubtypeOf
import avail.descriptor.types.A_Type.Companion.isSupertypeOfTupleType
import avail.descriptor.types.A_Type.Companion.lowerBound
import avail.descriptor.types.A_Type.Companion.lowerInclusive
import avail.descriptor.types.A_Type.Companion.sizeRange
import avail.descriptor.types.A_Type.Companion.trimType
import avail.descriptor.types.A_Type.Companion.typeAtIndex
import avail.descriptor.types.A_Type.Companion.typeIntersection
import avail.descriptor.types.A_Type.Companion.typeIntersectionOfTupleType
import avail.descriptor.types.A_Type.Companion.typeTuple
import avail.descriptor.types.A_Type.Companion.typeUnion
import avail.descriptor.types.A_Type.Companion.typeUnionOfTupleType
import avail.descriptor.types.A_Type.Companion.upperBound
import avail.descriptor.types.A_Type.Companion.upperInclusive
import avail.descriptor.types.BottomTypeDescriptor.Companion.bottom
import avail.descriptor.types.InstanceMetaDescriptor.Companion.instanceMeta
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.inclusive
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.int32
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.integerRangeType
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.naturalNumbers
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.singleInt
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.wholeNumbers
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.zeroOrOne
import avail.descriptor.types.TupleTypeDescriptor.ObjectSlots
import avail.descriptor.types.TupleTypeDescriptor.ObjectSlots.DEFAULT_TYPE
import avail.descriptor.types.TupleTypeDescriptor.ObjectSlots.SIZE_RANGE
import avail.descriptor.types.TupleTypeDescriptor.ObjectSlots.TYPE_TUPLE
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.ANY
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.CHARACTER
import avail.optimizer.jvm.CheckedMethod.Companion.staticMethod
import avail.optimizer.jvm.ReferencedInGeneratedCode
import avail.serialization.SerializerOperation
import org.availlang.json.JSONWriter
import java.util.IdentityHashMap
import kotlin.math.max
import kotlin.math.min

/**
 * A tuple type can be the [type][AvailObject.kind] of a
 * [tuple][TupleDescriptor], or something more general. It has a canonical form
 * consisting of three pieces of information:
 *
 * * the [size range][ObjectSlots.SIZE_RANGE], an
 *   [integer&#32;range&#32;type][IntegerRangeTypeDescriptor] that a conforming
 *   tuple's size must be an instance of,
 * * a [tuple][TupleDescriptor] of [types][TypeDescriptor] corresponding with
 *   the initial elements of the tuple, and
 * * a [default type][ObjectSlots.DEFAULT_TYPE] for all elements beyond the
 *   tuple of types to conform to.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @constructor
 * Construct a new `TupleTypeDescriptor`.
 *
 * @param mutability
 *   The [mutability][Mutability] of the new descriptor.
 */
class TupleTypeDescriptor
private constructor(
	mutability: Mutability
) : TypeDescriptor(
	mutability,
	TypeTag.TUPLE_TYPE_TAG,
	TypeTag.TUPLE_TAG,
	ObjectSlots::class.java,
	null)
{
	/**
	 * The layout of object slots for my instances.
	 */
	enum class ObjectSlots : ObjectSlotsEnum
	{
		/**
		 * An [integer&#32;range&#32;type][IntegerRangeTypeDescriptor] that
		 * contains all allowed [tuple&#32;sizes][A_Tuple.tupleSize] for
		 * instances of this type.
		 */
		SIZE_RANGE,

		/**
		 * The types of the leading elements of tuples that conform to this
		 * type. This is reduced at construction time to the minimum size of
		 * tuple that covers the same range. For example, if the last element of
		 * this tuple equals the [DEFAULT_TYPE] then this tuple will be
		 * shortened by one.
		 */
		TYPE_TUPLE,

		/**
		 * The type for all subsequent elements of the tuples that conform to
		 * this type.
		 */
		DEFAULT_TYPE
	}

	override fun printObjectOnAvoidingIndent(
		self: AvailObject,
		builder: StringBuilder,
		recursionMap: IdentityHashMap<A_BasicObject, Void>,
		indent: Int)
	{
		if (self.slot(TYPE_TUPLE).tupleSize == 0)
		{
			if (self.slot(SIZE_RANGE).equals(wholeNumbers))
			{
				if (self.slot(DEFAULT_TYPE).equals(ANY.o))
				{
					builder.append("tuple")
					return
				}
				if (self.slot(DEFAULT_TYPE).equals(CHARACTER.o))
				{
					builder.append("string")
					return
				}
				//  Okay, it's homogeneous and of arbitrary size...
				builder.append('<')
				self.defaultType.printOnAvoidingIndent(
					builder,
					recursionMap,
					indent + 1)
				builder.append("…|>")
				return
			}
		}
		builder.append('<')
		val end = self.slot(TYPE_TUPLE).tupleSize
		for (i in 1 .. end)
		{
			self.typeAtIndex(i).printOnAvoidingIndent(
				builder,
				recursionMap,
				indent + 1)
			builder.append(", ")
		}
		self.slot(DEFAULT_TYPE).printOnAvoidingIndent(
			builder,
			recursionMap,
			indent + 1)
		builder.append("…|")
		val sizeRange: A_Type = self.slot(SIZE_RANGE)
		sizeRange.lowerBound.printOnAvoidingIndent(
			builder,
			recursionMap,
			indent + 1)
		if (!sizeRange.lowerBound.equals(sizeRange.upperBound))
		{
			builder.append("..")
			sizeRange.upperBound.printOnAvoidingIndent(
				builder,
				recursionMap,
				indent + 1)
		}
		builder.append('>')
	}

	override fun o_DefaultType(self: AvailObject): A_Type =
		self.slot(DEFAULT_TYPE)

	override fun o_Equals(self: AvailObject, another: A_BasicObject): Boolean =
		another.equalsTupleType(self)

	/**
	 * {@inheritDoc}
	 *
	 * Tuple types are equal if and only if their sizeRange, typeTuple, and
	 * defaultType match.
	 */
	override fun o_EqualsTupleType(
		self: AvailObject,
		aTupleType: A_Type): Boolean =
			(self.sameAddressAs(aTupleType)
				|| (self.slot(SIZE_RANGE).equals(aTupleType.sizeRange)
				&& self.slot(DEFAULT_TYPE).equals(aTupleType.defaultType)
				&& self.slot(TYPE_TUPLE).equals(aTupleType.typeTuple)))

	override fun o_IsBetterRepresentationThan(
		self: AvailObject,
		anotherObject: A_BasicObject): Boolean =
			(self.representationCostOfTupleType()
				< anotherObject.representationCostOfTupleType())

	override fun o_RepresentationCostOfTupleType(self: AvailObject): Int = 1

	override fun o_Hash(self: AvailObject): Int = combine4(
		self.slot(SIZE_RANGE).hash(),
		self.slot(TYPE_TUPLE).hash(),
		self.slot(DEFAULT_TYPE).hash(),
		-0x749f6826)

	/**
	 * {@inheritDoc}
	 *
	 * Answer the union of the types that object's instances could have in the
	 * given range of indices.  Out-of-range indices are treated as bottom,
	 * which don't affect the union (unless all indices are out of range).
	 */
	override fun o_UnionOfTypesAtThrough(
		self: AvailObject,
		startIndex: Int,
		endIndex: Int): A_Type
	{
		if (startIndex > endIndex)
		{
			return bottom
		}
		if (startIndex == endIndex)
		{
			return self.typeAtIndex(startIndex)
		}
		val upper = self.sizeRange.upperBound
		if (fromInt(startIndex).greaterThan(upper))
		{
			return bottom
		}
		val leading = self.typeTuple
		val interestingLimit = leading.tupleSize + 1
		val clipStart = max(min(startIndex, interestingLimit), 1)
		val clipEnd = max(min(endIndex, interestingLimit), 1)
		var typeUnion = self.typeAtIndex(clipStart)
		for (i in clipStart + 1 .. clipEnd)
		{
			typeUnion = typeUnion.typeUnion(self.typeAtIndex(i))
		}
		return typeUnion
	}

	override fun o_IsSubtypeOf(self: AvailObject, aType: A_Type): Boolean =
		aType.isSupertypeOfTupleType(self)

	/**
	 * {@inheritDoc}
	 *
	 * Tuple type A is a supertype of tuple type B iff all the *possible
	 * instances* of B would also be instances of A.  Types that are
	 * indistinguishable under this condition are considered the same type.
	 */
	override fun o_IsSupertypeOfTupleType(
		self: AvailObject,
		aTupleType: A_Type): Boolean
	{
		if (self.equals(aTupleType))
		{
			return true
		}
		if (!aTupleType.sizeRange.isSubtypeOf(
				self.slot(SIZE_RANGE)))
		{
			return false
		}
		val subTuple = aTupleType.typeTuple
		val superTuple: A_Tuple = self.slot(TYPE_TUPLE)
		var end = max(subTuple.tupleSize, superTuple.tupleSize) + 1
		val smallUpper = aTupleType.sizeRange.upperBound
		if (smallUpper.isInt)
		{
			end = min(end, smallUpper.extractInt)
		}
		for (i in 1 .. end)
		{
			val subType: A_Type =
				if (i <= subTuple.tupleSize)
				{
					subTuple.tupleAt(i)
				}
				else
				{
					aTupleType.defaultType
				}
			val superType: A_Type =
				if (i <= superTuple.tupleSize)
				{
					superTuple.tupleAt(i)
				}
				else
				{
					self.slot(DEFAULT_TYPE)
				}
			if (!subType.isSubtypeOf(superType))
			{
				return false
			}
		}
		return true
	}

	// I am a tupleType, so answer true.
	override fun o_IsTupleType(self: AvailObject): Boolean = true

	override fun o_IsVacuousType(self: AvailObject): Boolean
	{
		val minSizeObject = self.sizeRange.lowerBound
		if (!minSizeObject.isInt)
		{
			return false
		}
		// Only check the element types up to the minimum size.  It's ok to stop
		// after the variations (i.e., just after the leading typeTuple).
		val minSize = min(
			minSizeObject.extractInt,
			self.slot(TYPE_TUPLE).tupleSize + 1)
		for (i in 1 .. minSize)
		{
			if (self.typeAtIndex(i).isVacuousType)
			{
				return true
			}
		}
		return false
	}

	override fun o_MarshalToJava(
		self: AvailObject,
		classHint: Class<*>?
	): Any? = when
	{
		self.isSubtypeOf(stringType) -> String::class.java
		else -> super.o_MarshalToJava(self, classHint)
	}

	@ThreadSafe
	override fun o_SerializerOperation(self: AvailObject): SerializerOperation =
		SerializerOperation.TUPLE_TYPE

	override fun o_SizeRange(self: AvailObject): A_Type =
		self.slot(SIZE_RANGE)

	override fun o_TrimType(self: AvailObject, typeToRemove: A_Type): A_Type
	{
		if (!self.sizeRange.isSubtypeOf(int32))
		{
			// Trim the type to only those that are physically possible.
			self.makeImmutable()
			return tupleTypeForSizesTypesDefaultType(
					self.sizeRange.typeIntersection(int32),
					self.typeTuple,
					self.defaultType
				).trimType(typeToRemove)
		}
		if (self.isSubtypeOf(typeToRemove)) return bottom
		if (!typeToRemove.isTupleType) return self
		if (typeToRemove.isEnumeration) return self
		self.makeImmutable()
		typeToRemove.makeImmutable()
		if (!typeToRemove.sizeRange.isSubtypeOf(int32))
		{
			// Trim the type to only those that are physically possible.
			return self.trimType(
				tupleTypeForSizesTypesDefaultType(
					typeToRemove.sizeRange.typeIntersection(int32),
					typeToRemove.typeTuple,
					typeToRemove.defaultType))
		}
		val sizeRange = self.sizeRange
		val leadingTypes = self.typeTuple
		val defaultType = self.defaultType
		val removedLeadingTypes = typeToRemove.typeTuple
		val variationLimit =
			max(leadingTypes.tupleSize, removedLeadingTypes.tupleSize) + 1
		val firstVariation = (1..variationLimit).indexOfFirst { i ->
			// Test whether the element at that position would always be
			// excluded by the removed type.
			!self.typeAtIndex(i).isSubtypeOf(typeToRemove.typeAtIndex(i))
		} + 1  // from Kotlin index -> one-based
		val removedSizeRange = typeToRemove.sizeRange
		if (firstVariation == 0)
		{
			// At every element position, the type in the receiver is subsumed
			// by the removed type.  Therefore, the only actual tuples that
			// could be in the receiver but not in the removed type are those
			// with a size that the removed type doesn't have.
			return tupleTypeForSizesTypesDefaultType(
				sizeRange.trimType(removedSizeRange), leadingTypes, defaultType)
		}
		// All elements from 1 to firstVariation were subsumed by the removed
		// type, so all tuples of size 1..firstVariation that match self would
		// also match typeToRemove.
		val permittedSizes = sizeRange.trimType(
			inclusive(
				removedSizeRange.lowerBound.extractLong,
				(firstVariation - 1).toLong()))
		if (!permittedSizes.equals(sizeRange))
		{
			// Some of the sizes were excluded.  Good enough – use this
			// restricted type.
			return tupleTypeForSizesTypesDefaultType(
				permittedSizes, leadingTypes, defaultType)
		}
		// None of the tuple sizes were excluded.  Even if both tuple types are
		// completely homogenous, with self having element type A∪B and the
		// excluded element type B, we can't say the result is A, because only
		// tuples where *all* elements are B are excluded.  For example, the
		// tuple <b, a, b> would continue to be included.  The remaining case we
		// can make progress on is tuples of size 0 or 1, since there are no
		// "other" elements that could save the tuple from exclusion.
		if (sizeRange.upperBound.equalsInt(1))
		{
			assert (leadingTypes.tupleSize == 0)
			return tupleTypeForSizesTypesDefaultType(
				if (removedSizeRange.lowerBound.equalsInt(0)) singleInt(1)
				else sizeRange,
				emptyTuple,
				defaultType.trimType(typeToRemove.typeAtIndex(1)))
		}
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
		return generateObjectTupleFrom(size) {
			when
			{
				it > size -> bottom
				else -> self.typeAtIndex(it + startIndex - 1).makeImmutable()
			}
		}
	}

	override fun o_TypeAtIndex(self: AvailObject, index: Int): A_Type
	{
		// Answer what type the given index would have in an object instance of
		// me.  Answer bottom if the index is out of bounds.
		if (index <= 0)
		{
			return bottom
		}
		val upper = self.slot(SIZE_RANGE).upperBound
		if (upper.isInt)
		{
			if (upper.extractInt < index)
			{
				return bottom
			}
		}
		else if (upper.lessThan(fromInt(index)))
		{
			return bottom
		}
		val leading: A_Tuple = self.slot(TYPE_TUPLE)
		return if (index <= leading.tupleSize)
		{
			leading.tupleAt(index)
		}
		else self.slot(DEFAULT_TYPE)
	}

	override fun o_TypeIntersection(
		self: AvailObject,
		another: A_Type
	): A_Type = when
	{
		self.isSubtypeOf(another) -> self
		another.isSubtypeOf(self) -> another
		else -> another.typeIntersectionOfTupleType(self)
	}

	override fun o_TypeIntersectionOfTupleType(
		self: AvailObject,
		aTupleType: A_Type): A_Type
	{
		var newSizesObject =
			self.slot(SIZE_RANGE).typeIntersection(aTupleType.sizeRange)
		val lead1: A_Tuple = self.slot(TYPE_TUPLE)
		val lead2 = aTupleType.typeTuple
		var newLeading: A_Tuple
		newLeading =
			if (lead1.tupleSize > lead2.tupleSize) lead1
			else lead2
		newLeading.makeImmutable()
		//  Ensure first write attempt will force copying.
		val newLeadingSize = newLeading.tupleSize
		for (i in 1 .. newLeadingSize)
		{
			val intersectionObject = self.typeAtIndex(i).typeIntersection(
				aTupleType.typeAtIndex(i))
			if (intersectionObject.isBottom)
			{
				return bottom
			}
			newLeading =
				newLeading.tupleAtPuttingCanDestroy(i, intersectionObject, true)
		}
		// Make sure entries in newLeading are immutable, as
		// typeIntersection(...)can answer one of its arguments.
		newLeading.makeSubobjectsImmutable()
		val newDefault =
			self.typeAtIndex(newLeadingSize + 1).typeIntersection(
				aTupleType.typeAtIndex(newLeadingSize + 1))
		if (newDefault.isBottom)
		{
			val newLeadingSizeObject: A_Number = fromInt(newLeadingSize)
			if (newLeadingSizeObject.lessThan(newSizesObject.lowerBound))
			{
				return bottom
			}
			if (newLeadingSizeObject.lessThan(newSizesObject.upperBound))
			{
				newSizesObject = integerRangeType(
					newSizesObject.lowerBound,
					newSizesObject.lowerInclusive,
					newLeadingSizeObject,
					true)
			}
		}
		//  safety until all primitives are destructive
		return tupleTypeForSizesTypesDefaultType(
			newSizesObject, newLeading, newDefault.makeImmutable())
	}

	override fun o_TypeTuple(self: AvailObject): A_Tuple =
		self.slot(TYPE_TUPLE)

	override fun o_TypeUnion(self: AvailObject, another: A_Type): A_Type =
		when
		{
			self.isSubtypeOf(another) -> another
			another.isSubtypeOf(self) -> self
			else -> another.typeUnionOfTupleType(self)
		}

	override fun o_TypeUnionOfTupleType(
		self: AvailObject,
		aTupleType: A_Type): A_Type
	{
		val newSizesObject =
			self.slot(SIZE_RANGE).typeUnion(aTupleType.sizeRange)
		val lead1: A_Tuple = self.slot(TYPE_TUPLE)
		val lead2 = aTupleType.typeTuple
		var newLeading: A_Tuple
		newLeading =
			if (lead1.tupleSize > lead2.tupleSize) lead1
			else lead2
		newLeading.makeImmutable()
		//  Ensure first write attempt will force copying.
		val newLeadingSize = newLeading.tupleSize
		for (i in 1 .. newLeadingSize)
		{
			val unionObject =
				self.typeAtIndex(i).typeUnion(aTupleType.typeAtIndex(i))
			newLeading = newLeading.tupleAtPuttingCanDestroy(
				i, unionObject, true)
		}
		// Make sure entries in newLeading are immutable, as typeUnion(...) can
		// answer one of its arguments.
		newLeading.makeSubobjectsImmutable()
		val newDefault =
			self.typeAtIndex(newLeadingSize + 1).typeUnion(
				aTupleType.typeAtIndex(newLeadingSize + 1))
		// Safety until all primitives are destructive
		newDefault.makeImmutable()
		return tupleTypeForSizesTypesDefaultType(
			newSizesObject, newLeading, newDefault)
	}

	override fun o_WriteTo(self: AvailObject, writer: JSONWriter)
	{
		writer.startObject()
		writer.write("kind")
		writer.write("tuple type")
		writer.write("leading types")
		self.slot(TYPE_TUPLE).writeTo(writer)
		writer.write("default type")
		self.slot(DEFAULT_TYPE).writeTo(writer)
		writer.write("cardinality")
		self.slot(SIZE_RANGE).writeTo(writer)
		writer.endObject()
	}

	override fun o_WriteSummaryTo(self: AvailObject, writer: JSONWriter)
	{
		writer.startObject()
		writer.write("kind")
		writer.write("tuple type")
		writer.write("leading types")
		self.slot(TYPE_TUPLE).writeSummaryTo(writer)
		writer.write("default type")
		self.slot(DEFAULT_TYPE).writeSummaryTo(writer)
		writer.write("cardinality")
		self.slot(SIZE_RANGE).writeSummaryTo(writer)
		writer.endObject()
	}

	override fun mutable(): TupleTypeDescriptor = mutable

	override fun immutable(): TupleTypeDescriptor = immutable

	override fun shared(): TupleTypeDescriptor = shared

	companion object
	{
		/**
		 * Create the tuple type specified by the arguments. The size range
		 * indicates the allowable tuple sizes for conforming tuples, the type
		 * tuple indicates the types of leading elements (if a conforming tuple
		 * is long enough to include those indices), and the default type is the
		 * type of the remaining elements. Canonize the tuple type to the
		 * simplest representation that includes exactly those tuples that a
		 * naive tuple type constructed from these parameters would include. In
		 * particular, if no tuples are possible then answer bottom, otherwise
		 * remove any final occurrences of the default from the end of the type
		 * tuple, trimming it to no more than the maximum size in the range.
		 *
		 * @param sizeRange
		 *   The allowed sizes of conforming tuples.
		 * @param typeTuple
		 *   The types of the initial elements of conforming tuples.
		 * @param defaultType
		 *   The type of remaining elements of conforming tuples.
		 * @return
		 *   A canonized tuple type with the specified properties.
		 */
		fun tupleTypeForSizesTypesDefaultType(
			sizeRange: A_Type,
			typeTuple: A_Tuple,
			defaultType: A_Type): A_Type
		{
			if (sizeRange.isBottom)
			{
				return bottom
			}
			assert(sizeRange.lowerBound.isFinite)
			assert(sizeRange.upperBound.isFinite || !sizeRange.upperInclusive)
			if (sizeRange.upperBound.equalsInt(0)
				&& sizeRange.lowerBound.equalsInt(0))
			{
				return privateTupleTypeForSizesTypesDefaultType(
					sizeRange, emptyTuple, bottom)
			}
			val typeTupleSize = typeTuple.tupleSize
			if (fromInt(typeTupleSize).greaterOrEqual(sizeRange.upperBound))
			{
				// The (nonempty) tuple hits the end of the range – disregard
				// the passed defaultType and use the final element of the tuple
				// as the defaultType, while removing it from the tuple.
				// Recurse for further reductions.
				val upper = sizeRange.upperBound.extractInt
				return tupleTypeForSizesTypesDefaultType(
					sizeRange,
					typeTuple.copyTupleFromToCanDestroy(1, upper - 1, false),
					typeTuple.tupleAt(upper).makeImmutable())
			}
			if (typeTupleSize > 0
				&& typeTuple.tupleAt(typeTupleSize).equals(defaultType))
			{
				//  See how many other redundant entries we can drop.
				var index = typeTupleSize - 1
				while (index > 0
					&& typeTuple.tupleAt(index).equals(defaultType))
				{
					index--
				}
				return tupleTypeForSizesTypesDefaultType(
					sizeRange,
					typeTuple.copyTupleFromToCanDestroy(1, index, false),
					defaultType)
			}
			return privateTupleTypeForSizesTypesDefaultType(
				sizeRange, typeTuple, defaultType)
		}

		/**
		 * Answer a tuple type consisting of either zero or one occurrences of the
		 * given element type.
		 *
		 * @param aType
		 *   A [type][TypeDescriptor].
		 * @return
		 *   A size [0..1] tuple type whose element has the given type.
		 */
		fun zeroOrOneOf(aType: A_Type): A_Type =
			tupleTypeForSizesTypesDefaultType(zeroOrOne, emptyTuple, aType)

		/**
		 * Answer a tuple type consisting of zero or more of the given element
		 * type.
		 *
		 * @param aType
		 *   A [type][TypeDescriptor].
		 * @return
		 *   A size [0..∞) tuple type whose elements have the given type.
		 */
		fun zeroOrMoreOf(aType: A_Type): A_Type =
			tupleTypeForSizesTypesDefaultType(
				wholeNumbers,
				emptyTuple,
				aType)

		/**
		 * Answer a tuple type consisting of one or more of the given element
		 * type.
		 *
		 * @param aType
		 *   A [type][TypeDescriptor].
		 * @return
		 *   A size [1..∞) tuple type whose elements have the given type.
		 */
		fun oneOrMoreOf(aType: A_Type): A_Type =
			tupleTypeForSizesTypesDefaultType(
				naturalNumbers,
				emptyTuple,
				aType)

		/**
		 * Answer a fixed size tuple type consisting of the given element types.
		 *
		 * @param types
		 *   A variable number of types corresponding to the elements of the
		 *   resulting tuple type.
		 * @return
		 *   A fixed-size tuple type.
		 */
		@ReferencedInGeneratedCode
		@JvmStatic
		fun tupleTypeForTypes(vararg types: A_Type): A_Type =
			tupleTypeForSizesTypesDefaultType(
				singleInt(types.size),
				tupleFromArray(*types),
				bottom)

		/** Access the method [tupleTypeForTypes]. */
		var tupleTypesForTypesArrayMethod = staticMethod(
			TupleTypeDescriptor::class.java,
			::tupleTypeForTypes.name,
			A_Type::class.java,
			Array<A_Type>::class.java)

		/**
		 * Answer a fixed size tuple type consisting of the given element types.
		 *
		 * @param types
		 *   A [List] of [A_Type]s corresponding to the elements of the
		 *   resulting fixed-size tuple type.
		 * @return
		 *   A fixed-size tuple type.
		 */
		@ReferencedInGeneratedCode
		@JvmStatic
		fun tupleTypeForTypesList(types: List<A_Type>): A_Type =
			tupleTypeForSizesTypesDefaultType(
				singleInt(types.size),
				tupleFromList(types),
				bottom)

		/** Access the method [tupleTypeForTypes]. */
		@Suppress("unused")
		val tupleTypesForTypesListMethod = staticMethod(
			TupleTypeDescriptor::class.java,
			::tupleTypeForTypesList.name,
			A_Type::class.java,
			MutableList::class.java)

		/**
		 * Transform a tuple type into another tuple type by transforming each
		 * of the element types.  Assume the transformation is stable.  The
		 * resulting tuple type should have the same size range as the input
		 * tuple type, except if normalization produces [bottom].
		 *
		 * @param aTupleType
		 *   A tuple type whose element types should be transformed.
		 * @param elementTransformer
		 *   A transformation to perform on each element type, to produce the
		 *   corresponding element types of the resulting tuple type.
		 * @return
		 *   A tuple type resulting from applying the transformation to each
		 *   element type of the supplied tuple type.
		 */
		fun mappingElementTypes(
			aTupleType: A_Type,
			elementTransformer: (A_Type) -> A_Type): A_Type
		{
			val sizeRange = aTupleType.sizeRange
			val typeTuple = aTupleType.typeTuple
			val defaultType = aTupleType.defaultType
			val limit = typeTuple.tupleSize
			val transformedTypeTuple: A_Tuple = generateObjectTupleFrom(limit)
				{ elementTransformer(typeTuple.tupleAt(it)) }
			val transformedDefaultType = elementTransformer(defaultType)
			return tupleTypeForSizesTypesDefaultType(
				sizeRange, transformedTypeTuple, transformedDefaultType)
		}

		/**
		 * Create a tuple type with the specified parameters. These must already
		 * have been canonized by the caller.
		 *
		 * @param sizeRange
		 *   The allowed sizes of conforming tuples.
		 * @param typeTuple
		 *   The types of the initial elements of conforming tuples.
		 * @param defaultType
		 *   The types of remaining elements of conforming tuples.
		 * @return
		 *   A tuple type with the specified properties.
		 */
		private fun privateTupleTypeForSizesTypesDefaultType(
			sizeRange: A_Type,
			typeTuple: A_Tuple,
			defaultType: A_Type): A_Type
		{
			assert(sizeRange.lowerBound.isFinite)
			assert(sizeRange.upperBound.isFinite || !sizeRange.upperInclusive)
			assert(sizeRange.lowerBound.extractInt >= 0)
			val sizeRangeKind =
				if (sizeRange.isEnumeration) sizeRange.computeSuperkind()
				else sizeRange
			val limit = min(
				sizeRangeKind.lowerBound.extractInt,
				typeTuple.tupleSize)
			for (i in 1 .. limit)
			{
				assert(typeTuple.tupleAt(i).isType)
			}
			return mutable.create {
				setSlot(SIZE_RANGE, sizeRangeKind)
				setSlot(TYPE_TUPLE, typeTuple)
				setSlot(DEFAULT_TYPE, defaultType)
			}
		}

		/** The mutable `TupleTypeDescriptor`. */
		private val mutable = TupleTypeDescriptor(Mutability.MUTABLE)

		/** The immutable `TupleTypeDescriptor`. */
		private val immutable = TupleTypeDescriptor(Mutability.IMMUTABLE)

		/** The shared `TupleTypeDescriptor`. */
		private val shared = TupleTypeDescriptor(Mutability.SHARED)

		/** The most general tuple type. */
		val mostGeneralTupleType: A_Type = zeroOrMoreOf(ANY.o).makeShared()

		/** The most general string type (i.e., tuples of characters). */
		val stringType: A_Type = zeroOrMoreOf(CHARACTER.o).makeShared()

		/** The most general string type (i.e., tuples of characters). */
		val nonemptyStringType: A_Type = oneOrMoreOf(CHARACTER.o).makeShared()

		/** The metatype for all tuple types. */
		val tupleMeta: A_Type = instanceMeta(mostGeneralTupleType).makeShared()
	}
}
