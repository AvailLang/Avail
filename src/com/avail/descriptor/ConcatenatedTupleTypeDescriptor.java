/**
 * ConcatenatedTupleTypeDescriptor.java
 * Copyright © 1993-2015, The Avail Foundation, LLC.
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

package com.avail.descriptor;

import static com.avail.descriptor.ConcatenatedTupleTypeDescriptor.IntegerSlots.*;
import static com.avail.descriptor.ConcatenatedTupleTypeDescriptor.ObjectSlots.*;
import static java.lang.Math.*;
import com.avail.annotations.*;
import com.avail.serialization.SerializerOperation;
import com.avail.utility.Generator;
import com.avail.utility.json.JSONWriter;

/**
 * An object instance of {@code ConcatenatedTupleTypeDescriptor} is an
 * optimization that postpones (or ideally avoids) the creation of a {@linkplain
 * TupleTypeDescriptor tuple type} when computing the static type of the
 * concatenation of two tuples.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class ConcatenatedTupleTypeDescriptor
extends TypeDescriptor
{
	/**
	 * The layout of integer slots for my instances.
	 */
	public enum IntegerSlots
	implements IntegerSlotsEnum
	{
		/**
		 * {@link BitField}s holding the tuple type complexity and other fields
		 * if needed.
		 */
		TUPLE_TYPE_COMPLEXITY_AND_MORE;

		/**
		 * The number of layers of virtualized concatenation in this tuple type.
		 * This may become a conservatively large estimate due to my subobjects
		 * being coalesced with more direct representations.
		 */
		final static BitField TUPLE_TYPE_COMPLEXITY =
			bitField(TUPLE_TYPE_COMPLEXITY_AND_MORE, 0, 32);
	}

	/**
	 * The layout of object slots for my instances.
	 */
	public enum ObjectSlots
	implements ObjectSlotsEnum
	{
		/**
		 * The type of the left tuple being concatenated.
		 */
		FIRST_TUPLE_TYPE,

		/**
		 * The type of the right tuple being concatenated.
		 */
		SECOND_TUPLE_TYPE
	}

	/**
	 * The maximum depth of {@link ConcatenatedTupleTypeDescriptor concatenated
	 * tuple types} that may exist before converting to a fully reified {@link
	 * TupleTypeDescriptor tuple type}.
	 */
	private static final int maximumConcatenationDepth = 10;

	/**
	 * Answer the type that my last element must have, if any.
	 */
	@Override @AvailMethod
	A_Type o_DefaultType (final AvailObject object)
	{
		final A_Type a = object.slot(FIRST_TUPLE_TYPE);
		final A_Type b = object.slot(SECOND_TUPLE_TYPE);
		return defaultTypeOfConcatenation(a, b);
	}

	@Override @AvailMethod
	boolean o_Equals (final AvailObject object, final A_BasicObject another)
	{
		return another.equalsTupleType(object);
	}

	@Override @AvailMethod
	boolean o_EqualsTupleType (
		final AvailObject object,
		final A_Type aTupleType)
	{
		// Tuple types are equal iff their size range, leading type tuple, and
		// default type match.
		if (object.sameAddressAs(aTupleType))
		{
			return true;
		}
		if (!object.sizeRange().equals(aTupleType.sizeRange()))
		{
			return false;
		}
		if (!object.defaultType().equals(aTupleType.defaultType()))
		{
			return false;
		}
		if (!object.typeTuple().equals(aTupleType.typeTuple()))
		{
			return false;
		}
		// They're equal, but occupy disjoint storage. If possible, replace one
		// with an indirection to the other.
		if (object.representationCostOfTupleType()
			< aTupleType.representationCostOfTupleType())
		{
			if (!aTupleType.descriptor().isShared())
			{
				aTupleType.becomeIndirectionTo(object.makeImmutable());
			}
		}
		else
		{
			if (!isShared())
			{
				object.becomeIndirectionTo(aTupleType.makeImmutable());
			}
		}
		return true;
	}

	/**
	 *
	 * Answer a 32-bit long that is always the same for equal objects, but
	 * statistically different for different objects.  This requires an object
	 * creation, so don't call it from the garbage collector.
	 */
	@Override @AvailMethod
	int o_Hash (final AvailObject object)
	{
		becomeRealTupleType(object);
		return object.hash();
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * A {@link ConcatenatedTupleTypeDescriptor concatenated tuple type} isn't
	 * a very fast representation to use, even though it's easy to construct.
	 * </p>
	 */
	@Override @AvailMethod
	boolean o_IsBetterRepresentationThan (
		final AvailObject object,
		final A_BasicObject anotherObject)
	{
		return object.representationCostOfTupleType()
			< anotherObject.representationCostOfTupleType();
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * I'm not a very time-efficient representation of a tuple type.
	 * </p>
	 */
	@Override @AvailMethod
	int o_RepresentationCostOfTupleType (
		final AvailObject object)
	{
		return object.slot(TUPLE_TYPE_COMPLEXITY);
	}

	/**
	 * Answer what range of tuple sizes my instances could be. Note that this
	 * can not be asked during a garbage collection because it allocates space
	 * for its answer.
	 */
	@Override @AvailMethod
	A_Type o_SizeRange (final AvailObject object)
	{
		final A_Type sizeRange1 = object.slot(FIRST_TUPLE_TYPE).sizeRange();
		final A_Type sizeRange2 = object.slot(SECOND_TUPLE_TYPE).sizeRange();
		return sizeRangeOfConcatenation(sizeRange1, sizeRange2);
	}

	/**
	 * Check if object is a subtype of aType.  They should both be types.
	 */
	@Override @AvailMethod
	boolean o_IsSubtypeOf (final AvailObject object, final A_Type aType)
	{
		return aType.isSupertypeOfTupleType(object);
	}

	/**
	 * Tuple type A is a supertype of tuple type B iff all the <em>possible
	 * instances</em> of B would also be instances of A.  Types
	 * indistinguishable under these conditions are considered the same type.
	 */
	@Override @AvailMethod
	boolean o_IsSupertypeOfTupleType (
		final AvailObject object,
		final AvailObject aTupleType)
	{
		if (object.equals(aTupleType))
		{
			return true;
		}
		if (!aTupleType.sizeRange().isSubtypeOf(object.sizeRange()))
		{
			return false;
		}
		if (!aTupleType.defaultType().isSubtypeOf(object.defaultType()))
		{
			return false;
		}
		final A_Tuple subTuple = aTupleType.typeTuple();
		final A_Tuple superTuple = object.typeTuple();
		final int limit = max(subTuple.tupleSize(), superTuple.tupleSize());
		for (int i = 1; i <= limit; i++)
		{
			final A_Type subType = i <= subTuple.tupleSize()
				? subTuple.tupleAt(i)
				: aTupleType.defaultType();
			final A_Type superType = i <= superTuple.tupleSize()
				? superTuple.tupleAt(i)
				: object.defaultType();
			if (!subType.isSubtypeOf(superType))
			{
				return false;
			}
		}
		return true;
	}

	@Override @AvailMethod
	boolean o_IsTupleType (final AvailObject object)
	{
		return true;
	}

	@Override
	AvailObject o_MakeShared (final AvailObject object)
	{
		// Before an object using this descriptor can be shared, it must first
		// become (an indirection to) a proper tuple type.
		assert !isShared();
		becomeRealTupleType(object);
		object.makeShared();
		return object.traversed();
	}

	@Override
	SerializerOperation o_SerializerOperation (
		final AvailObject object)
	{
		becomeRealTupleType(object);
		return object.serializerOperation();
	}

	@Override
	A_Tuple o_TupleOfTypesFromTo (
		final AvailObject object,
		final int startIndex,
		final int endIndex)
	{
		becomeRealTupleType(object);
		return super.o_TupleOfTypesFromTo(object, startIndex, endIndex);
	}

	/**
	 * Answer what type the given index would have in an object instance of me.
	 * Answer bottom if the index is definitely out of bounds.
	 */
	@Override @AvailMethod
	A_Type o_TypeAtIndex (final AvailObject object, final int index)
	{
		final AvailObject firstTupleType = object.slot(FIRST_TUPLE_TYPE);
		final AvailObject secondTupleType = object.slot(SECOND_TUPLE_TYPE);
		return elementOfConcatenation(firstTupleType, secondTupleType, index);
	}

	@Override @AvailMethod
	A_Type o_TypeIntersection (
		final AvailObject object,
		final A_Type another)
	{
		if (object.isSubtypeOf(another))
		{
			return object;
		}
		if (another.isSubtypeOf(object))
		{
			return another;
		}
		return another.typeIntersectionOfTupleType(object);
	}

	@Override @AvailMethod
	A_Type o_TypeIntersectionOfTupleType (
		final AvailObject object,
		final A_Type aTupleType)
	{
		final A_Type newSizesObject =
			object.sizeRange().typeIntersection(aTupleType.sizeRange());
		final A_Tuple lead1 = object.typeTuple();
		final A_Tuple lead2 = aTupleType.typeTuple();
		A_Tuple newLeading;
		if (lead1.tupleSize() > lead2.tupleSize())
		{
			newLeading = lead1;
		}
		else
		{
			newLeading = lead2;
		}
		newLeading.makeImmutable();
		//  Ensure first write attempt will force copying.
		final int newLeadingSize = newLeading.tupleSize();
		for (int i = 1; i <= newLeadingSize; i++)
		{
			final A_Type intersectionObject =
				object.typeAtIndex(i).typeIntersection(
					aTupleType.typeAtIndex(i));
			if (intersectionObject.isBottom())
			{
				return BottomTypeDescriptor.bottom();
			}
			newLeading = newLeading.tupleAtPuttingCanDestroy(
				i,
				intersectionObject,
				true);
		}
		// Make sure entries in newLeading are immutable, as typeIntersection
		// can answer one of its arguments.
		newLeading.makeSubobjectsImmutable();
		final A_Type newDefault =
			object.typeAtIndex(newLeadingSize + 1).typeIntersection(
				aTupleType.typeAtIndex(newLeadingSize + 1));
		newDefault.makeImmutable();
		return TupleTypeDescriptor.tupleTypeForSizesTypesDefaultType(
			newSizesObject,
			newLeading,
			newDefault);
	}

	/**
	 * Since this is really tricky, just compute the TupleTypeDescriptor that
	 * this is shorthand for.  Answer that tupleType's typeTuple.  This is the
	 * leading types of the tupleType, up to but not including where they all
	 * have the same type.  Don't run this from within a garbage collection, as
	 * it allocates objects.
	 */
	@Override @AvailMethod
	A_Tuple o_TypeTuple (final AvailObject object)
	{
		becomeRealTupleType(object);
		return object.typeTuple();
	}

	@Override @AvailMethod
	A_Type o_TypeUnion (
		final AvailObject object,
		final A_Type another)
	{
		if (object.isSubtypeOf(another))
		{
			return another;
		}
		if (another.isSubtypeOf(object))
		{
			return object;
		}
		return another.typeUnionOfTupleType(object);
	}

	@Override @AvailMethod
	A_Type o_TypeUnionOfTupleType (
		final AvailObject object,
		final A_Type aTupleType)
	{
		final A_Type newSizesObject = object.sizeRange().typeUnion(
			aTupleType.sizeRange());
		final A_Tuple lead1 = object.typeTuple();
		final A_Tuple lead2 = aTupleType.typeTuple();
		A_Tuple newLeading;
		if (lead1.tupleSize() > lead2.tupleSize())
		{
			newLeading = lead1;
		}
		else
		{
			newLeading = lead2;
		}
		newLeading.makeImmutable();
		// Ensure first write attempt will force copying.
		final int newLeadingSize = newLeading.tupleSize();
		for (int i = 1; i <= newLeadingSize; i++)
		{
			final A_Type unionObject = object.typeAtIndex(i).typeUnion(
				aTupleType.typeAtIndex(i));
			newLeading = newLeading.tupleAtPuttingCanDestroy(
				i,
				unionObject,
				true);
		}
		// Make sure entries in newLeading are immutable, as typeUnion can
		// answer one of its arguments.
		newLeading.makeSubobjectsImmutable();
		final A_Type newDefault =
			object.typeAtIndex(newLeadingSize + 1).typeUnion(
				aTupleType.typeAtIndex(newLeadingSize + 1));
		newDefault.makeImmutable();
		return TupleTypeDescriptor.tupleTypeForSizesTypesDefaultType(
			newSizesObject,
			newLeading,
			newDefault);
	}

	@Override @AvailMethod
	A_Type o_UnionOfTypesAtThrough (
		final AvailObject object,
		final int startIndex,
		final int endIndex)
	{
		// Answer the union of the types that object's instances could have in
		// the given range of indices. Out-of-range indices are treated as
		// bottom, which don't affect the union (unless all indices are out
		// of range).

		assert startIndex <= endIndex;
		if (startIndex == endIndex)
		{
			return object.typeAtIndex(startIndex);
		}
		if (endIndex <= 0)
		{
			return BottomTypeDescriptor.bottom();
		}
		final A_Type firstTupleType = object.slot(FIRST_TUPLE_TYPE);
		final A_Type secondTupleType = object.slot(SECOND_TUPLE_TYPE);
		final A_Number firstUpper = firstTupleType.sizeRange().upperBound();
		final A_Number secondUpper = secondTupleType.sizeRange().upperBound();
		final A_Number totalUpper =
			firstUpper.noFailPlusCanDestroy(secondUpper, false);
		final A_Number startIndexObject = IntegerDescriptor.fromInt(startIndex);
		if (totalUpper.isFinite())
		{
			if (startIndexObject.greaterThan(totalUpper))
			{
				return BottomTypeDescriptor.bottom();
			}
		}
		A_Type typeUnion =
			firstTupleType.unionOfTypesAtThrough(startIndex, endIndex);
		final A_Number startInSecondObject =
			startIndexObject.minusCanDestroy(firstUpper, false);
		final int startInSecond =
			startInSecondObject.lessThan(IntegerDescriptor.one())
				? 1
				: startInSecondObject.extractInt();
		final A_Number endInSecondObject =
			IntegerDescriptor.fromInt(endIndex).minusCanDestroy(
				firstTupleType.sizeRange().lowerBound(),
				false);
		final int endInSecond =
			endInSecondObject.lessThan(IntegerDescriptor.one())
				? 1
				: endInSecondObject.isInt()
					? endInSecondObject.extractInt()
					: Integer.MAX_VALUE;
		typeUnion = typeUnion.typeUnion(
			secondTupleType.unionOfTypesAtThrough(startInSecond, endInSecond));
		return typeUnion;
	}

	@Override
	void o_WriteTo (final AvailObject object, final JSONWriter writer)
	{
		becomeRealTupleType(object);
		writer.startObject();
		writer.write("kind");
		writer.write("tuple type");
		writer.write("leading types");
		object.typeTuple().writeTo(writer);
		writer.write("default type");
		object.defaultType().writeTo(writer);
		writer.write("cardinality");
		object.sizeRange().writeTo(writer);
		writer.endObject();
	}

	/**
	 * Expand me into an actual TupleTypeDescriptor, converting my storage into
	 * an indirection object to the actual tupleType.
	 *
	 * @param object
	 *        The {@linkplain ConcatenatedTupleTypeDescriptor concatenated tuple
	 *        type} to transform.
	 */
	private void becomeRealTupleType (final AvailObject object)
	{
		// There isn't even a shared descriptor -- we reify the tuple type upon
		// sharing.
		assert !isShared();
		final A_Type newObject = reallyConcatenate(
			object.slot(FIRST_TUPLE_TYPE),
			object.slot(SECOND_TUPLE_TYPE));
		object.becomeIndirectionTo(newObject);
	}

	/**
	 * Answer what type the given index would have in a tuple whose type
	 * complies with the concatenation of the two tuple types.  Answer bottom if
	 * the index is definitely out of bounds.
	 *
	 * @param firstTupleType The first {@link TupleTypeDescriptor tuple type}.
	 * @param secondTupleType The second tuple type.
	 * @param index The element index.
	 * @return The type of the specified index within the concatenated tuple
	 *         type.
	 */
	@InnerAccess static A_Type elementOfConcatenation (
		final A_Type firstTupleType,
		final A_Type secondTupleType,
		final int index)
	{
		if (index <= 0)
		{
			return BottomTypeDescriptor.bottom();
		}
		final A_Type firstSizeRange = firstTupleType.sizeRange();
		final A_Number firstUpper = firstSizeRange.upperBound();
		final A_Number secondUpper = secondTupleType.sizeRange().upperBound();
		final A_Number totalUpper =
			firstUpper.noFailPlusCanDestroy(secondUpper, false);
		if (totalUpper.isFinite())
		{
			final A_Number indexObject = IntegerDescriptor.fromInt(index);
			if (indexObject.greaterThan(totalUpper))
			{
				return BottomTypeDescriptor.bottom();
			}
		}
		final A_Number firstLower = firstSizeRange.lowerBound();
		if (index <= firstLower.extractInt())
		{
			return firstTupleType.typeAtIndex(index);
		}
		// Besides possibly being at a fixed offset within the firstTupleType,
		// the index might represent a range of possible indices of the
		// secondTupleType, depending on the spread between the first tuple
		// type's lower and upper bounds. Compute the union of these types.
		final A_Type typeFromFirstTuple = firstTupleType.typeAtIndex(index);
		int startIndex;
		if (firstUpper.isFinite())
		{
			startIndex = max(index - firstUpper.extractInt(), 1);
		}
		else
		{
			startIndex = 1;
		}
		final int endIndex = index - firstLower.extractInt();
		assert endIndex >= startIndex;
		return typeFromFirstTuple.typeUnion(
			secondTupleType.unionOfTypesAtThrough(startIndex, endIndex));
	}

	/**
	 * Answer the {@linkplain A_Type#sizeRange() size range} of the
	 * concatenation of tuples having the given size ranges.
	 *
	 * @param sizeRange1 The first tuple's sizeRange.
	 * @param sizeRange2 The second tuple's sizeRange.
	 * @return The range of sizes of the concatenated tuple.
	 */
	private static A_Type sizeRangeOfConcatenation (
		final A_Type sizeRange1,
		final A_Type sizeRange2)
	{
		final A_Number lower = sizeRange1.lowerBound().noFailPlusCanDestroy(
			sizeRange2.lowerBound(), false);
		final A_Number upper = sizeRange1.upperBound().noFailPlusCanDestroy(
			sizeRange2.upperBound(), false);
		return IntegerRangeTypeDescriptor.create(
			lower,
			true,
			upper,
			upper.isFinite());
	}

	/**
	 * Given two tuple types, the second of which must not be always empty,
	 * determine what a complying tuple's last element's type must be.
	 *
	 * @param tupleType1 The first tuple type.
	 * @param tupleType2 The second tuple type.
	 * @return The type of the last element of the concatenation of the tuple
	 *         types.
	 */
	private static A_Type defaultTypeOfConcatenation (
		final A_Type tupleType1,
		final A_Type tupleType2)
	{
		final A_Type bRange = tupleType2.sizeRange();
		assert !bRange.upperBound().equalsInt(0);
		if (tupleType1.sizeRange().upperBound().isFinite())
		{
			return tupleType2.defaultType();
		}
		int highIndexInB;
		if (bRange.upperBound().isFinite())
		{
			highIndexInB = bRange.upperBound().extractInt();
		}
		else
		{
			highIndexInB = tupleType2.typeTuple().tupleSize() + 1;
		}
		final A_Type typeUnion = tupleType1.defaultType().typeUnion(
			tupleType2.unionOfTypesAtThrough(1, highIndexInB));
		return typeUnion;
	}

	/**
	 * Produce a fully reified concatenation (i.e., not a {@link
	 * ConcatenatedTupleTypeDescriptor instance} of the given pair of tuple
	 * types.
	 *
	 * @param part1 The left tuple type.
	 * @param part2 The right tuple type.
	 * @return
	 */
	private static A_Type reallyConcatenate (
		final A_Type part1,
		final A_Type part2)
	{
		final A_Type sizes1 = part1.sizeRange();
		final A_Number upper1 = sizes1.upperBound();
		final int limit1 = upper1.isFinite()
			? upper1.extractInt()
			: max(
				part1.typeTuple().tupleSize() + 1,
				sizes1.lowerBound().extractInt());
		final A_Type sizes2 = part2.sizeRange();
		final A_Number upper2 = sizes2.upperBound();
		final int limit2 = upper2.isFinite()
			? upper2.extractInt()
			: part2.typeTuple().tupleSize() + 1;
		final int total = limit1 + limit2;
		final int section1 = min(sizes1.lowerBound().extractInt(), limit1);
		final A_Tuple typeTuple = ObjectTupleDescriptor.generateFrom(
			total,
			new Generator<A_BasicObject>()
			{
				private int index = 1;

				@Override
				public A_BasicObject value ()
				{
					if (index <= section1)
					{
						return part1.typeAtIndex(index++);
					}
					return elementOfConcatenation(part1, part2, index++);
				}
			});
		final A_Type newObject =
			TupleTypeDescriptor.tupleTypeForSizesTypesDefaultType(
				sizeRangeOfConcatenation(sizes1, sizes2),
				typeTuple,
				defaultTypeOfConcatenation(part1, part2));
		return newObject;
	}

	/**
	 * Construct a lazy concatenated tuple type object to represent the type
	 * that is the concatenation of the two tuple types.  Make the objects be
	 * immutable, because the new type represents the concatenation of the
	 * objects <em>at the time it was built</em>.
	 *
	 * @param firstTupleType
	 *        The first tuple type to concatenate.
	 * @param secondTupleType
	 *        The second tuple type to concatenate.
	 * @return
	 *        A simple representation of the tuple type whose instances are all
	 *        the concatenations of instances of the two given tuple types.
	 */
	public static A_Type concatenatingAnd (
		final A_Type firstTupleType,
		final A_Type secondTupleType)
	{
		assert firstTupleType.isTupleType() && !firstTupleType.isBottom();
		assert secondTupleType.isTupleType() && !secondTupleType.isBottom();
		if (secondTupleType.sizeRange().upperBound().equalsInt(0))
		{
			return firstTupleType.makeImmutable();
		}
		if (firstTupleType.sizeRange().upperBound().equalsInt(0))
		{
			return secondTupleType.makeImmutable();
		}
		final int maxCost = max(
			firstTupleType.representationCostOfTupleType(),
			secondTupleType.representationCostOfTupleType());
		if (maxCost > maximumConcatenationDepth)
		{
			return reallyConcatenate(
				firstTupleType.makeImmutable(),
				secondTupleType.makeImmutable());
		}
		final AvailObject result = mutable.create();
		result.setSlot(TUPLE_TYPE_COMPLEXITY, maxCost + 1);
		result.setSlot(FIRST_TUPLE_TYPE, firstTupleType.makeImmutable());
		result.setSlot(SECOND_TUPLE_TYPE, secondTupleType.makeImmutable());
		return result;
	}

	/**
	 * Construct a new {@link ConcatenatedTupleTypeDescriptor}.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 */
	private ConcatenatedTupleTypeDescriptor (final Mutability mutability)
	{
		super(mutability, ObjectSlots.class, IntegerSlots.class);
	}

	/** The mutable {@link ConcatenatedTupleTypeDescriptor}. */
	private static final ConcatenatedTupleTypeDescriptor mutable =
		new ConcatenatedTupleTypeDescriptor(Mutability.MUTABLE);

	@Override
	ConcatenatedTupleTypeDescriptor mutable ()
	{
		return mutable;
	}

	/** The immutable {@link ConcatenatedTupleTypeDescriptor}. */
	private static final ConcatenatedTupleTypeDescriptor immutable =
		new ConcatenatedTupleTypeDescriptor(Mutability.IMMUTABLE);

	@Override
	ConcatenatedTupleTypeDescriptor immutable ()
	{
		return immutable;
	}

	@Override
	ConcatenatedTupleTypeDescriptor shared ()
	{
		throw unsupportedOperationException();
	}
}
