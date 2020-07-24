/*
 * TupleTypeDescriptor.kt
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
import com.avail.descriptor.numbers.A_Number
import com.avail.descriptor.numbers.IntegerDescriptor.Companion.fromInt
import com.avail.descriptor.representation.A_BasicObject
import com.avail.descriptor.representation.AvailObject
import com.avail.descriptor.representation.Mutability
import com.avail.descriptor.representation.ObjectSlotsEnum
import com.avail.descriptor.tuples.A_Tuple
import com.avail.descriptor.tuples.ObjectTupleDescriptor.Companion.generateObjectTupleFrom
import com.avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tupleFromArray
import com.avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tupleFromList
import com.avail.descriptor.tuples.TupleDescriptor
import com.avail.descriptor.tuples.TupleDescriptor.Companion.emptyTuple
import com.avail.descriptor.types.BottomTypeDescriptor.Companion.bottom
import com.avail.descriptor.types.InstanceMetaDescriptor.Companion.instanceMeta
import com.avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.integerRangeType
import com.avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.naturalNumbers
import com.avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.singleInt
import com.avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.wholeNumbers
import com.avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.zeroOrOne
import com.avail.descriptor.types.TupleTypeDescriptor.ObjectSlots
import com.avail.descriptor.types.TupleTypeDescriptor.ObjectSlots.DEFAULT_TYPE
import com.avail.descriptor.types.TupleTypeDescriptor.ObjectSlots.SIZE_RANGE
import com.avail.descriptor.types.TupleTypeDescriptor.ObjectSlots.TYPE_TUPLE
import com.avail.descriptor.types.TypeDescriptor.Types.ANY
import com.avail.descriptor.types.TypeDescriptor.Types.CHARACTER
import com.avail.optimizer.jvm.CheckedMethod.Companion.staticMethod
import com.avail.optimizer.jvm.ReferencedInGeneratedCode
import com.avail.serialization.SerializerOperation
import com.avail.utility.json.JSONWriter
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
class TupleTypeDescriptor private constructor(mutability: Mutability)
	: TypeDescriptor(
		mutability, TypeTag.TUPLE_TYPE_TAG, ObjectSlots::class.java, null)
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
		if (self.slot(TYPE_TUPLE).tupleSize() == 0)
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
				self.defaultType().printOnAvoidingIndent(
					builder,
					recursionMap,
					indent + 1)
				builder.append("…|>")
				return
			}
		}
		builder.append('<')
		val end = self.slot(TYPE_TUPLE).tupleSize()
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
		sizeRange.lowerBound().printOnAvoidingIndent(
			builder,
			recursionMap,
			indent + 1)
		if (!sizeRange.lowerBound().equals(sizeRange.upperBound()))
		{
			builder.append("..")
			sizeRange.upperBound().printOnAvoidingIndent(
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
		        || (self.slot(SIZE_RANGE)
		            .equals(aTupleType.sizeRange())
	            && self.slot(DEFAULT_TYPE)
		            .equals(aTupleType.defaultType())
	            && self.slot(TYPE_TUPLE)
		            .equals(aTupleType.typeTuple())))

	override fun o_IsBetterRepresentationThan(
		self: AvailObject,
		anotherObject: A_BasicObject): Boolean =
			(self.representationCostOfTupleType()
		        < anotherObject.representationCostOfTupleType())

	override fun o_RepresentationCostOfTupleType(self: AvailObject): Int = 1

	override fun o_Hash(self: AvailObject): Int
	{
		return hashOfTupleTypeWithSizesHashTypesHashDefaultTypeHash(
			self.slot(SIZE_RANGE).hash(),
			self.slot(TYPE_TUPLE).hash(),
			self.slot(DEFAULT_TYPE).hash())
	}

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
			return bottom()
		}
		if (startIndex == endIndex)
		{
			return self.typeAtIndex(startIndex)
		}
		val upper = self.sizeRange().upperBound()
		if (fromInt(startIndex).greaterThan(upper))
		{
			return bottom()
		}
		val leading = self.typeTuple()
		val interestingLimit = leading.tupleSize() + 1
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
		if (!aTupleType.sizeRange().isSubtypeOf(
				self.slot(SIZE_RANGE)))
		{
			return false
		}
		val subTuple = aTupleType.typeTuple()
		val superTuple: A_Tuple = self.slot(TYPE_TUPLE)
		var end = max(subTuple.tupleSize(), superTuple.tupleSize()) + 1
		val smallUpper = aTupleType.sizeRange().upperBound()
		if (smallUpper.isInt)
		{
			end = min(end, smallUpper.extractInt())
		}
		for (i in 1 .. end)
		{
			val subType: A_Type =
				if (i <= subTuple.tupleSize())
				{
					subTuple.tupleAt(i)
				}
				else
				{
					aTupleType.defaultType()
				}
			val superType: A_Type =
				if (i <= superTuple.tupleSize())
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
		val minSizeObject = self.sizeRange().lowerBound()
		if (!minSizeObject.isInt)
		{
			return false
		}
		// Only check the element types up to the minimum size.  It's ok to stop
		// after the variations (i.e., just after the leading typeTuple).
		val minSize = min(
			minSizeObject.extractInt(),
			self.slot(TYPE_TUPLE).tupleSize() + 1)
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
		self.isSubtypeOf(stringType()) -> String::class.java
		else -> super.o_MarshalToJava(self, classHint)
	}

	@ThreadSafe
	override fun o_SerializerOperation(self: AvailObject): SerializerOperation =
		SerializerOperation.TUPLE_TYPE

	override fun o_SizeRange(self: AvailObject): A_Type =
		self.slot(SIZE_RANGE)

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
				it > size -> bottom()
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
			return bottom()
		}
		val upper = self.slot(SIZE_RANGE).upperBound()
		if (upper.isInt)
		{
			if (upper.extractInt() < index)
			{
				return bottom()
			}
		}
		else if (upper.lessThan(fromInt(index)))
		{
			return bottom()
		}
		val leading: A_Tuple = self.slot(TYPE_TUPLE)
		return if (index <= leading.tupleSize())
		{
			leading.tupleAt(index)
		}
		else self.slot(DEFAULT_TYPE)
	}

	override fun o_TypeIntersection(self: AvailObject, another: A_Type): A_Type =
		when
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
			self.slot(SIZE_RANGE)
				.typeIntersection(aTupleType.sizeRange())
		val lead1: A_Tuple = self.slot(TYPE_TUPLE)
		val lead2 = aTupleType.typeTuple()
		var newLeading: A_Tuple
		newLeading =
			if (lead1.tupleSize() > lead2.tupleSize()) lead1
			else lead2
		newLeading.makeImmutable()
		//  Ensure first write attempt will force copying.
		val newLeadingSize = newLeading.tupleSize()
		for (i in 1 .. newLeadingSize)
		{
			val intersectionObject = self.typeAtIndex(i).typeIntersection(
				aTupleType.typeAtIndex(i))
			if (intersectionObject.isBottom)
			{
				return bottom()
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
			if (newLeadingSizeObject.lessThan(newSizesObject.lowerBound()))
			{
				return bottom()
			}
			if (newLeadingSizeObject.lessThan(newSizesObject.upperBound()))
			{
				newSizesObject = integerRangeType(
					newSizesObject.lowerBound(),
					newSizesObject.lowerInclusive(),
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
			self.slot(SIZE_RANGE).typeUnion(aTupleType.sizeRange())
		val lead1: A_Tuple = self.slot(TYPE_TUPLE)
		val lead2 = aTupleType.typeTuple()
		var newLeading: A_Tuple
		newLeading =
			if (lead1.tupleSize() > lead2.tupleSize()) lead1
			else lead2
		newLeading.makeImmutable()
		//  Ensure first write attempt will force copying.
		val newLeadingSize = newLeading.tupleSize()
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
		@JvmStatic
		fun tupleTypeForSizesTypesDefaultType(
			sizeRange: A_Type,
			typeTuple: A_Tuple,
			defaultType: A_Type): A_Type
		{
			if (sizeRange.isBottom)
			{
				return bottom()
			}
			assert(sizeRange.lowerBound().isFinite)
			assert(sizeRange.upperBound().isFinite
			       || !sizeRange.upperInclusive())
			if (sizeRange.upperBound().equalsInt(0)
			    && sizeRange.lowerBound().equalsInt(0))
			{
				return privateTupleTypeForSizesTypesDefaultType(
					sizeRange,
					emptyTuple,
					bottom())
			}
			val typeTupleSize = typeTuple.tupleSize()
			if (fromInt(typeTupleSize).greaterOrEqual(sizeRange.upperBound()))
			{
				// The (nonempty) tuple hits the end of the range – disregard
				// the passed defaultType and use the final element of the tuple
				// as the defaultType, while removing it from the tuple.
				// Recurse for further reductions.
				val upper = sizeRange.upperBound().extractInt()
				return tupleTypeForSizesTypesDefaultType(
					sizeRange,
					typeTuple.copyTupleFromToCanDestroy(
						1,
						upper - 1,
						false),
					typeTuple.tupleAt(upper).makeImmutable())
			}
			if (typeTupleSize > 0
			    && typeTuple.tupleAt(typeTupleSize).equals(defaultType))
			{
				//  See how many other redundant entries we can drop.
				var index = typeTupleSize - 1
				while (index > 0 && typeTuple.tupleAt(index).equals(defaultType))
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
		@JvmStatic
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
		@JvmStatic
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
		@JvmStatic
		@ReferencedInGeneratedCode
		fun tupleTypeForTypes(vararg types: A_Type): A_Type =
			tupleTypeForSizesTypesDefaultType(
				singleInt(types.size),
				tupleFromArray(*types),
				bottom())

		/** Access the method [tupleTypeForTypes].  */
		var tupleTypesForTypesArrayMethod = staticMethod(
			TupleTypeDescriptor::class.java,
			"tupleTypeForTypes",
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
		fun tupleTypeForTypes(types: List<A_Type>): A_Type =
			tupleTypeForSizesTypesDefaultType(
				singleInt(types.size),
				tupleFromList(types),
				bottom())

		/** Access the method [tupleTypeForTypes].  */
		@Suppress("unused")
		@JvmField
		val tupleTypesForTypesListMethod = staticMethod(
			TupleTypeDescriptor::class.java,
			"tupleTypeForTypes",
			A_Type::class.java,
			MutableList::class.java)

		/**
		 * Transform a tuple type into another tuple type by transforming each
		 * of the element types.  Assume the transformation is stable.  The
		 * resulting tuple type should have the same size range as the input
		 * tuple type, except if normalization produces
		 * [bottom][BottomTypeDescriptor.bottom].
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
		@JvmStatic
		fun tupleTypeFromTupleOfTypes(
			aTupleType: A_Type,
			elementTransformer: (A_Type) -> A_Type): A_Type
		{
			val sizeRange = aTupleType.sizeRange()
			val typeTuple = aTupleType.typeTuple()
			val defaultType = aTupleType.defaultType()
			val limit = typeTuple.tupleSize()
			val transformedTypeTuple: A_Tuple = generateObjectTupleFrom(limit)
				{ elementTransformer.invoke(typeTuple.tupleAt(it)) }
			val transformedDefaultType =
				elementTransformer.invoke(defaultType)
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
			assert(sizeRange.lowerBound().isFinite)
			assert(sizeRange.upperBound().isFinite || !sizeRange.upperInclusive())
			assert(sizeRange.lowerBound().extractInt() >= 0)
			val sizeRangeKind =
				if (sizeRange.isEnumeration) sizeRange.computeSuperkind()
				else sizeRange
			val limit = min(
				sizeRangeKind.lowerBound().extractInt(),
				typeTuple.tupleSize())
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

		/**
		 * Answer the hash of the tuple type whose canonized parameters have the
		 * specified hash values.
		 *
		 * @param sizesHash
		 *   The hash of the
		 *   [integer&#32;range&#32;type][IntegerRangeTypeDescriptor] that is
		 *   the size range for some [tuple&#32;type][TupleTypeDescriptor] being
		 *   hashed.
		 * @param typeTupleHash
		 *   The hash of the tuple of types of the leading arguments of tuples
		 *   that conform to some `TupleTypeDescriptor tuple&#32;type` being
		 *   hashed.
		 * @param defaultTypeHash
		 *   The hash of the type that remaining elements of conforming types
		 *   must have.
		 * @return
		 *   The hash of the `TupleTypeDescriptor tuple&#32;type` whose
		 *   component hash values were provided.
		 */
		private fun hashOfTupleTypeWithSizesHashTypesHashDefaultTypeHash(
			sizesHash: Int,
			typeTupleHash: Int,
			defaultTypeHash: Int): Int =
				sizesHash * 13 + defaultTypeHash * 11 + typeTupleHash * 7

		/** The mutable `TupleTypeDescriptor`.  */
		private val mutable = TupleTypeDescriptor(Mutability.MUTABLE)

		/** The immutable `TupleTypeDescriptor`.  */
		private val immutable = TupleTypeDescriptor(Mutability.IMMUTABLE)

		/** The shared `TupleTypeDescriptor`.  */
		private val shared = TupleTypeDescriptor(Mutability.SHARED)

		/** The most general tuple type.  */
		private val mostGeneralType: A_Type =
			zeroOrMoreOf(ANY.o).makeShared()

		/**
		 * Answer the most general tuple type.  This is the supertype of all
		 * other tuple types.
		 *
		 * @return
		 *   The most general tuple type.
		 */
		@JvmStatic
		fun mostGeneralTupleType(): A_Type = mostGeneralType

		/** The most general string type (i.e., tuples of characters).  */
		private val stringType: A_Type =
			zeroOrMoreOf(CHARACTER.o).makeShared()

		/**
		 * Answer the most general string type.  This type subsumes strings of
		 * any size.
		 *
		 * @return
		 *   The string type.
		 */
		@JvmStatic
		fun stringType(): A_Type = stringType

		/** The most general string type (i.e., tuples of characters).  */
		private val nonemptyStringType: A_Type =
			oneOrMoreOf(CHARACTER.o).makeShared()

		/**
		 * Answer the non-empty string type.  This type subsumes strings of any
		 * size ≥ 1.
		 *
		 * @return
		 *   The non-empty string type.
		 */
		@JvmStatic
		fun nonemptyStringType(): A_Type = nonemptyStringType

		/** The metatype for all tuple types.  */
		private val meta: A_Type = instanceMeta(mostGeneralType).makeShared()

		/**
		 * Answer the metatype for all tuple types.
		 *
		 * @return
		 *   The statically referenced metatype.
		 */
		@JvmStatic
		fun tupleMeta(): A_Type = meta
	}
}
