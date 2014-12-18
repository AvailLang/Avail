/**
 * ConcatenatedTupleTypeDescriptor.java
 * Copyright © 1993-2014, The Avail Foundation, LLC.
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

import static com.avail.descriptor.ConcatenatedTupleTypeDescriptor.ObjectSlots.*;
import static java.lang.Math.*;
import com.avail.annotations.*;
import com.avail.serialization.SerializerOperation;
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
		return object.typeTuple().equals(aTupleType.typeTuple());
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
		return false;
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * I'm not a very time-efficient representation of a tuple type.
	 * </p>
	 */
	@Override @AvailMethod
	boolean o_IsBetterRepresentationThanTupleType (
		final AvailObject object,
		final A_Type aTupleType)
	{
		return false;
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
		final A_Type part1 = object.slot(FIRST_TUPLE_TYPE);
		final A_Number size1 = part1.sizeRange().upperBound();
		int limit1;
		if (size1.isFinite())
		{
			limit1 = size1.extractInt();
		}
		else
		{
			limit1 = max(
				part1.typeTuple().tupleSize() + 1,
				part1.sizeRange().lowerBound().extractInt());
		}
		final A_Type part2 = object.slot(SECOND_TUPLE_TYPE);
		final A_Number size2 = part2.sizeRange().upperBound();
		int limit2;
		if (size2.isFinite())
		{
			limit2 = size2.extractInt();
		}
		else
		{
			limit2 = part2.typeTuple().tupleSize() + 1;
		}
		final int total = limit1 + limit2;
		final A_Tuple typeTuple =
			ObjectTupleDescriptor.createUninitialized(total);
		// Make it pointer-safe first.
		for (int i = 1; i <= total; i++)
		{
			typeTuple.objectTupleAtPut(i, NilDescriptor.nil());
		}
		final int section1 = min(
			part1.sizeRange().lowerBound().extractInt(),
			limit1);
		for (int i = 1; i <= section1; i++)
		{
			typeTuple.objectTupleAtPut(i, part1.typeAtIndex(i));
		}
		for (int i = section1 + 1; i <= total; i++)
		{
			typeTuple.objectTupleAtPut(i, object.typeAtIndex(i));
		}
		final A_Type newObject =
			TupleTypeDescriptor.tupleTypeForSizesTypesDefaultType(
				object.sizeRange(),
				typeTuple,
				object.defaultType());
		object.becomeIndirectionTo(newObject);
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

	/**
	 * Answer the type that my last element must have, if any.  Do not call this
	 * from within a garbage collection, as it may need to allocate space for
	 * computing a type union.
	 */
	@Override @AvailMethod
	A_Type o_DefaultType (final AvailObject object)
	{
		final A_Type a = object.slot(FIRST_TUPLE_TYPE);
		final A_Type b = object.slot(SECOND_TUPLE_TYPE);
		final A_Type bRange = b.sizeRange();
		if (bRange.upperBound().equals(IntegerDescriptor.zero()))
		{
			return a.defaultType();
		}
		if (a.sizeRange().upperBound().isFinite())
		{
			return b.defaultType();
		}
		int highIndexInB;
		if (bRange.upperBound().isFinite())
		{
			highIndexInB = bRange.upperBound().extractInt();
		}
		else
		{
			highIndexInB = b.typeTuple().tupleSize() + 1;
		}
		final A_Type typeUnion = a.defaultType().typeUnion(
			b.unionOfTypesAtThrough(1, highIndexInB));
		return typeUnion;
	}

	/**
	 * Answer what range of tuple sizes my instances could be. Note that this
	 * can not be asked during a garbage collection because it allocates space
	 * for its answer.
	 */
	@Override @AvailMethod
	A_Type o_SizeRange (final AvailObject object)
	{
		final A_Type a = object.slot(FIRST_TUPLE_TYPE).sizeRange();
		final A_Type b = object.slot(SECOND_TUPLE_TYPE).sizeRange();
		final A_Number upper = a.upperBound().noFailPlusCanDestroy(
			b.upperBound(), false);
		return IntegerRangeTypeDescriptor.create(
			a.lowerBound().noFailPlusCanDestroy(b.lowerBound(), false),
			true,
			upper,
			upper.isFinite());
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
		for (
			int i = 1, end = max(subTuple.tupleSize(), superTuple.tupleSize());
			i <= end;
			i++)
		{
			A_Type subType;
			if (i <= subTuple.tupleSize())
			{
				subType = subTuple.tupleAt(i);
			}
			else
			{
				subType = aTupleType.defaultType();
			}
			A_Type superType;
			if (i <= superTuple.tupleSize())
			{
				superType = superTuple.tupleAt(i);
			}
			else
			{
				superType = object.defaultType();
			}
			if (!subType.isSubtypeOf(superType))
			{
				return false;
			}
		}
		return true;
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
		if (index <= 0)
		{
			return BottomTypeDescriptor.bottom();
		}
	
		final A_Number firstUpper =
			object.slot(FIRST_TUPLE_TYPE).sizeRange().upperBound();
		final A_Number secondUpper =
			object.slot(SECOND_TUPLE_TYPE).sizeRange().upperBound();
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
		final A_Number firstLower =
			object.slot(FIRST_TUPLE_TYPE).sizeRange().lowerBound();
		if (index <= firstLower.extractInt())
		{
			return object.slot(FIRST_TUPLE_TYPE).typeAtIndex(index);
		}
		// Besides possibly being at a fixed offset within the firstTupleType,
		// the index might represent a range of possible indices of the
		// secondTupleType, depending on the spread between the first tuple
		// type's lower and upper bounds. Compute the union of these types.
		final A_Type typeUnion =
			object.slot(FIRST_TUPLE_TYPE).typeAtIndex(index);
		int startIndex;
		if (firstUpper.isFinite())
		{
			startIndex = max((index - firstUpper.extractInt()), 1);
		}
		else
		{
			startIndex = 1;
		}
		final int endIndex = index - firstLower.extractInt();
		assert endIndex >= startIndex;
		return typeUnion.typeUnion(
			object.slot(SECOND_TUPLE_TYPE).unionOfTypesAtThrough(
				startIndex, endIndex));
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
	boolean o_IsTupleType (final AvailObject object)
	{
		return true;
	}

	@Override
	SerializerOperation o_SerializerOperation (
		final AvailObject object)
	{
		becomeRealTupleType(object);
		return object.serializerOperation();
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
	 * Construct a lazy concatenated tuple type object to represent the type
	 * that is the concatenation of the two tuple types.  Make the objects be
	 * immutable, because the new type represents the concatenation of the
	 * objects <em>at the time it was built</em>.
	 *
	 * @param firstObject
	 *        The first tuple type to concatenate.
	 * @param secondObject
	 *        The second tuple type to concatenate.
	 * @return
	 *        A simple representation of the tuple type whose instances are all
	 *        the concatenations of instances of the two given tupletypes.
	 */
	public static AvailObject concatenatingAnd (
		final A_BasicObject firstObject,
		final A_BasicObject secondObject)
	{
		assert firstObject.isTupleType() && secondObject.isTupleType();
		final AvailObject result = mutable.create();
		result.setSlot(FIRST_TUPLE_TYPE, firstObject.makeImmutable());
		result.setSlot(SECOND_TUPLE_TYPE, secondObject.makeImmutable());
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
		super(mutability, ObjectSlots.class, null);
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
