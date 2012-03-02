/**
 * TupleTypeDescriptor.java
 * Copyright © 1993-2012, Mark van Gulik and Todd L Smith.
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

import static com.avail.descriptor.TypeDescriptor.Types.*;
import static java.lang.Math.*;
import java.util.List;
import com.avail.annotations.*;
import com.avail.serialization.SerializerOperation;

/**
 * A tuple type can be the {@linkplain AvailObject#kind() type} of a {@linkplain
 * TupleDescriptor tuple}, or something more general.  It has a canonical form
 * consisting of three pieces of information:
 * <ul>
 * <li>the {@linkplain ObjectSlots#SIZE_RANGE size range}, an {@linkplain
 * IntegerRangeTypeDescriptor integer range type} that a conforming tuple's
 * size must be an instance of,</li>
 * <li>a {@linkplain TupleDescriptor tuple} of {@linkplain TypeDescriptor types}
 * corresponding with the initial elements of the tuple, and</li>
 * <li>a {@linkplain ObjectSlots#DEFAULT_TYPE default type} for all elements
 * beyond the tuple of types to conform to.</li>
 * </ul>
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 */
public class TupleTypeDescriptor
extends TypeDescriptor
{
	/**
	 * The layout of object slots for my instances.
	 */
	public enum ObjectSlots implements ObjectSlotsEnum
	{
		/**
		 * An {@linkplain IntegerRangeTypeDescriptor integer range type} that
		 * contains all allowed {@linkplain
		* TupleDescriptor#o_TupleSize(AvailObject) tuple sizes} for instances
		 * of this type.
		 */
		SIZE_RANGE,

		/**
		 * The types of the leading elements of tuples that conform to this
		 * type.  This is reduced at construction time to the minimum size of
		 * tuple that covers the same range.  For example, if the last element
		 * of this tuple equals the {@link #DEFAULT_TYPE} then this tuple will
		 * be shortened by one.
		 *
		 */
		TYPE_TUPLE,

		/**
		 * The type for all subsequent elements of the tuples that conform to
		 * this type.
		 */
		DEFAULT_TYPE
	}

	@Override @AvailMethod
	@NotNull AvailObject o_DefaultType (
		final @NotNull AvailObject object)
	{
		return object.slot(ObjectSlots.DEFAULT_TYPE);
	}

	@Override @AvailMethod
	@NotNull AvailObject o_SizeRange (
		final @NotNull AvailObject object)
	{
		return object.slot(ObjectSlots.SIZE_RANGE);
	}

	@Override @AvailMethod
	@NotNull AvailObject o_TypeTuple (
		final @NotNull AvailObject object)
	{
		return object.slot(ObjectSlots.TYPE_TUPLE);
	}

	@Override
	public void printObjectOnAvoidingIndent (
		final @NotNull AvailObject object,
		final @NotNull StringBuilder aStream,
		final @NotNull List<AvailObject> recursionList,
		final int indent)
	{
		if (object.typeTuple().tupleSize() == 0)
		{
			if (object.sizeRange().equals(
				IntegerRangeTypeDescriptor.wholeNumbers()))
			{
				if (object.defaultType().equals(ANY.o()))
				{
					aStream.append("tuple");
					return;
				}
				if (object.defaultType().equals(CHARACTER.o()))
				{
					aStream.append("string");
					return;
				}
				//  Okay, it's homogeneous and of arbitrary size...
				aStream.append('<');
				object.defaultType().printOnAvoidingIndent(
					aStream,
					recursionList,
					(indent + 1));
				aStream.append("…|>");
				return;
			}
		}
		aStream.append('<');
		final int end = object.typeTuple().tupleSize();
		for (int i = 1; i <= end; i++)
		{
			object.typeAtIndex(i).printOnAvoidingIndent(
				aStream,
				recursionList,
				indent + 1);
			aStream.append(", ");
		}
		object.defaultType().printOnAvoidingIndent(
			aStream,
			recursionList,
			indent + 1);
		aStream.append("…|");
		final AvailObject sizeRange = object.sizeRange();
		sizeRange.lowerBound().printOnAvoidingIndent(
			aStream,
			recursionList,
			indent + 1);
		if (!sizeRange.lowerBound().equals(sizeRange.upperBound()))
		{
			aStream.append("..");
			sizeRange.upperBound().printOnAvoidingIndent(
				aStream,
				recursionList,
				indent + 1);
		}
		aStream.append('>');
	}

	@Override @AvailMethod
	boolean o_Equals (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
	{
		return another.equalsTupleType(object);
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * Tuple types are equal if and only if their sizeRange, typeTuple, and
	 * defaultType match.
	 * </p>
	 */
	@Override @AvailMethod
	boolean o_EqualsTupleType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aTupleType)
	{
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

	@Override @AvailMethod
	boolean o_IsBetterRepresentationThan (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anotherObject)
	{
		return !anotherObject.isBetterRepresentationThanTupleType(object);
	}

	@Override @AvailMethod
	boolean o_IsBetterRepresentationThanTupleType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aTupleType)
	{
		return true;
	}

	@Override @AvailMethod
	int o_Hash (
		final @NotNull AvailObject object)
	{
		return TupleTypeDescriptor
			.hashOfTupleTypeWithSizesHashTypesHashDefaultTypeHash(
				object.sizeRange().hash(),
				object.typeTuple().hash(),
				object.defaultType().hash());
	}

	@Override @AvailMethod
	@NotNull AvailObject o_Kind (
		final @NotNull AvailObject object)
	{
		return TYPE.o();
	}

	@Override @AvailMethod
	@NotNull AvailObject o_TypeAtIndex (
		final @NotNull AvailObject object,
		final int index)
	{
		// Answer what type the given index would have in an object instance of
		// me.  Answer bottom if the index is out of bounds.
		if (index <= 0)
		{
			return BottomTypeDescriptor.bottom();
		}
		final AvailObject upper = object.sizeRange().upperBound();
		if (upper.lessThan(IntegerDescriptor.fromInt(index)))
		{
			return BottomTypeDescriptor.bottom();
		}
		final AvailObject leading = object.typeTuple();
		if (index <= leading.tupleSize())
		{
			return leading.tupleAt(index);
		}
		return object.defaultType();
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * Answer the union of the types that object's instances could have in the
	 * given range of indices.  Out-of-range indices are treated as bottom,
	 * which don't affect the union (unless all indices are out of range).
	 * </p>
	 */
	@Override @AvailMethod
	@NotNull AvailObject o_UnionOfTypesAtThrough (
		final @NotNull AvailObject object,
		final int startIndex,
		final int endIndex)
	{
		assert startIndex <= endIndex;
		if (startIndex == endIndex)
		{
			return object.typeAtIndex(startIndex);
		}
		if (endIndex <= 0)
		{
			return BottomTypeDescriptor.bottom();
		}
		final AvailObject upper = object.sizeRange().upperBound();
		if (upper.isFinite() && startIndex > upper.extractInt())
		{
			return BottomTypeDescriptor.bottom();
		}
		final AvailObject leading = object.typeTuple();
		final int interestingLimit = leading.tupleSize() + 1;
		final int clipStart = max(min(startIndex, interestingLimit), 1);
		final int clipEnd = max(min(endIndex, interestingLimit), 1);
		AvailObject typeUnion = object.typeAtIndex(clipStart);
		for (int i = clipStart + 1; i <= clipEnd; i++)
		{
			typeUnion = typeUnion.typeUnion(object.typeAtIndex(i));
		}
		return typeUnion;
	}

	@Override @AvailMethod
	boolean o_IsSubtypeOf (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aType)
	{
		return aType.isSupertypeOfTupleType(object);
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * Tuple type A is a supertype of tuple type B iff all the <em>possible
	 * instances</em> of B would also be instances of A.  Types that are
	 * indistinguishable under this condition are considered the same type.
	 * </p>
	 */
	@Override @AvailMethod
	boolean o_IsSupertypeOfTupleType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aTupleType)
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
		final AvailObject subTuple = aTupleType.typeTuple();
		final AvailObject superTuple = object.typeTuple();
		for (
			int i = 1, end = max(subTuple.tupleSize(), superTuple.tupleSize());
			i <= end;
			i++)
		{
			AvailObject subType;
			if (i <= subTuple.tupleSize())
			{
				subType = subTuple.tupleAt(i);
			}
			else
			{
				subType = aTupleType.defaultType();
			}
			AvailObject superType;
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

	@Override @AvailMethod
	@NotNull AvailObject o_TypeIntersection (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
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
	@NotNull AvailObject o_TypeIntersectionOfTupleType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aTupleType)
	{
		AvailObject newSizesObject =
			object.sizeRange().typeIntersection(aTupleType.sizeRange());
		final AvailObject lead1 = object.typeTuple();
		final AvailObject lead2 = aTupleType.typeTuple();
		AvailObject newLeading;
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
			final AvailObject intersectionObject =
				object.typeAtIndex(i).typeIntersection(
					aTupleType.typeAtIndex(i));
			if (intersectionObject.equals(BottomTypeDescriptor.bottom()))
			{
				return BottomTypeDescriptor.bottom();
			}
			newLeading = newLeading.tupleAtPuttingCanDestroy(
				i,
				intersectionObject,
				true);
		}
		// Make sure entries in newLeading are immutable, as
		// typeIntersection(...)can answer one of its arguments.
		newLeading.makeSubobjectsImmutable();
		final AvailObject newDefault =
			object.typeAtIndex(newLeadingSize + 1).typeIntersection(
				aTupleType.typeAtIndex(newLeadingSize + 1));
		if (newDefault.equals(BottomTypeDescriptor.bottom()))
		{
			final AvailObject newLeadingSizeObject =
				IntegerDescriptor.fromInt(newLeadingSize);
			if (newLeadingSizeObject.lessThan(newSizesObject.lowerBound()))
			{
				return BottomTypeDescriptor.bottom();
			}
			if (newLeadingSizeObject.lessThan(newSizesObject.upperBound()))
			{
				newSizesObject = IntegerRangeTypeDescriptor.create(
					newSizesObject.lowerBound(),
					newSizesObject.lowerInclusive(),
					newLeadingSizeObject,
					true);
			}
		}
		//  safety until all primitives are destructive
		return TupleTypeDescriptor.tupleTypeForSizesTypesDefaultType(
			newSizesObject,
			newLeading,
			newDefault.makeImmutable());
	}

	@Override @AvailMethod
	@NotNull AvailObject o_TypeUnion (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
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
	@NotNull AvailObject o_TypeUnionOfTupleType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aTupleType)
	{
		final AvailObject newSizesObject =
			object.sizeRange().typeUnion(aTupleType.sizeRange());
		final AvailObject lead1 = object.typeTuple();
		final AvailObject lead2 = aTupleType.typeTuple();
		AvailObject newLeading;
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
			final AvailObject unionObject =
				object.typeAtIndex(i).typeUnion(aTupleType.typeAtIndex(i));
			newLeading = newLeading.tupleAtPuttingCanDestroy(
				i,
				unionObject,
				true);
		}
		// Make sure entries in newLeading are immutable, as typeUnion(...) can
		// answer one of its arguments.
		newLeading.makeSubobjectsImmutable();
		final AvailObject newDefault =
			object.typeAtIndex(newLeadingSize + 1).typeUnion(
				aTupleType.typeAtIndex(newLeadingSize + 1));
		// Safety until all primitives are destructive
		newDefault.makeImmutable();
		return TupleTypeDescriptor.tupleTypeForSizesTypesDefaultType(
			newSizesObject,
			newLeading,
			newDefault);
	}

	@Override @AvailMethod
	boolean o_IsTupleType (
		final @NotNull AvailObject object)
	{
		// I am a tupleType, so answer true.
		return true;
	}

	@Override @AvailMethod @ThreadSafe
	@NotNull SerializerOperation o_SerializerOperation (
		final @NotNull AvailObject object)
	{
		return SerializerOperation.TUPLE_TYPE;
	}

	@Override
	Object o_MarshalToJava (
		final @NotNull AvailObject object,
		final Class<?> ignoredClassHint)
	{
		if (object.isSubtypeOf(StringType))
		{
			return String.class;
		}
		return super.o_MarshalToJava(object, ignoredClassHint);
	}

	/**
	 * The most general tuple type.
	 */
	private static AvailObject MostGeneralType;

	/**
	 * Answer the most general tuple type.  This is the supertype of all other
	 * tuple types.
	 *
	 * @return The most general tuple type.
	 */
	public static AvailObject mostGeneralType ()
	{
		return MostGeneralType;
	}


	/**
	 * The most general string type (i.e., tuples of characters).
	 */
	private static AvailObject StringType;

	/**
	 * Answer the most general string type.  This type subsumes strings of any
	 * size.
	 *
	 * @return The string type.
	 */
	public static AvailObject stringTupleType ()
	{
		return StringType;
	}


	/**
	 * The metatype for all tuple types.
	 */
	private static AvailObject Meta;

	/**
	 * Answer the metatype for all tuple types.
	 *
	 * @return The statically referenced metatype.
	 */
	public static AvailObject meta ()
	{
		return Meta;
	}

	public static void clearWellKnownObjects ()
	{
		MostGeneralType = null;
		StringType = null;
		Meta = null;
	}

	public static void createWellKnownObjects ()
	{
		MostGeneralType = tupleTypeForSizesTypesDefaultType(
			IntegerRangeTypeDescriptor.wholeNumbers(),
			TupleDescriptor.empty(),
			ANY.o());
		MostGeneralType.makeImmutable();
		StringType = tupleTypeForSizesTypesDefaultType(
			IntegerRangeTypeDescriptor.wholeNumbers(),
			TupleDescriptor.empty(),
			CHARACTER.o());
		StringType.makeImmutable();
		Meta = InstanceTypeDescriptor.on(MostGeneralType);
		Meta.makeImmutable();
	}


	/**
	 * Create the tuple type specified by the arguments.  The size range
	 * indicates the allowable tuple sizes for conforming tuples, the type tuple
	 * indicates the types of leading elements (if a conforming tuple is long
	 * enough to include those indices), and the default type is the type of the
	 * remaining elements.  Canonize the tuple type to the simplest
	 * representation that includes exactly those tuples that a naive tuple type
	 * constructed from these parameters would include.  In particular, if no
	 * tuples are possible then answer bottom, otherwise remove any final
	 * occurrences of the default from the end of the type tuple, trimming it to
	 * no more than the maximum size in the range.
	 *
	 * @param sizeRange The allowed sizes of conforming tuples.
	 * @param typeTuple The types of the initial elements of conforming tuples.
	 * @param defaultType The types of remaining elements of conforming tuples.
	 * @return A canonized tuple type with the specified properties.
	 */
	public static @NotNull AvailObject tupleTypeForSizesTypesDefaultType (
		final @NotNull AvailObject sizeRange,
		final @NotNull AvailObject typeTuple,
		final @NotNull AvailObject defaultType)
	{
		if (sizeRange.equals(BottomTypeDescriptor.bottom()))
		{
			return BottomTypeDescriptor.bottom();
		}
		assert sizeRange.lowerBound().isFinite();
		assert sizeRange.upperBound().isFinite() || !sizeRange.upperInclusive();
		assert IntegerDescriptor.fromInt(typeTuple.tupleSize())
			.lessOrEqual(sizeRange.upperBound());
		if (sizeRange.lowerBound().equals(IntegerDescriptor.zero())
				&& sizeRange.upperBound().equals(IntegerDescriptor.zero()))
		{
			assert typeTuple.tupleSize() == 0;
			return privateTupleTypeForSizesTypesDefaultType(
				sizeRange, typeTuple, BottomTypeDescriptor.bottom());
		}
		if (sizeRange.upperInclusive()
				&& sizeRange.upperBound().extractInt() == typeTuple.tupleSize())
		{
			// The (nonempty) tuple hits the end of the range – disregard the
			// passed defaultType and use the final element of the tuple as the
			// defaultType, while removing it from the tuple.  Recurse for
			// further reductions.
			return tupleTypeForSizesTypesDefaultType(
				sizeRange,
				typeTuple.copyTupleFromToCanDestroy(
					1,
					typeTuple.tupleSize() - 1,
					false),
				typeTuple.tupleAt(typeTuple.tupleSize()).makeImmutable());
		}
		if (typeTuple.tupleSize() > 0
				&& typeTuple.tupleAt(typeTuple.tupleSize()).equals(defaultType))
		{
			//  See how many other redundant entries we can drop.
			int index = typeTuple.tupleSize() - 1;
			while (index > 0 && typeTuple.tupleAt(index).equals(defaultType))
			{
				index--;
			}
			return tupleTypeForSizesTypesDefaultType(
				sizeRange,
				typeTuple.copyTupleFromToCanDestroy(1, index, false),
				defaultType);
		}
		return privateTupleTypeForSizesTypesDefaultType(
			sizeRange, typeTuple, defaultType);
	}

	/**
	 * Create a {@linkplain TupleTypeDescriptor tuple type} with the specified
	 * parameters.  These must already have been canonized by the caller.
	 *
	 * @param sizeRange The allowed sizes of conforming tuples.
	 * @param typeTuple The types of the initial elements of conforming tuples.
	 * @param defaultType The types of remaining elements of conforming tuples.
	 * @return A tuple type with the specified properties.
	 */
	private static AvailObject privateTupleTypeForSizesTypesDefaultType (
		final @NotNull AvailObject sizeRange,
		final @NotNull AvailObject typeTuple,
		final @NotNull AvailObject defaultType)
	{
		assert sizeRange.lowerBound().isFinite();
		assert sizeRange.upperBound().isFinite() || !sizeRange.upperInclusive();
		assert sizeRange.lowerBound().extractInt() >= 0;

		final AvailObject sizeRangeKind = sizeRange.isEnumeration()
			? sizeRange.computeSuperkind()
			: sizeRange;
		final int limit = min(
			sizeRangeKind.lowerBound().extractInt(),
			typeTuple.tupleSize());
		for (int i = 1; i <= limit; i++)
		{
			assert typeTuple.tupleAt(i).isInstanceOfKind(TYPE.o());
		}
		final AvailObject result = mutable().create();
		result.setSlot(ObjectSlots.SIZE_RANGE, sizeRangeKind);
		result.setSlot(ObjectSlots.TYPE_TUPLE, typeTuple);
		result.setSlot(ObjectSlots.DEFAULT_TYPE, defaultType);
		return result;
	}

	/**
	 * Answer the hash of the tuple type whose canonized parameters have the
	 * specified hash values.
	 *
	 * @param sizesHash
	 *            The hash of the {@linkplain IntegerRangeTypeDescriptor integer
	 *            range type} that is the size range for some {@linkplain
	 *            TupleTypeDescriptor tuple type} being hashed.
	 * @param typeTupleHash
	 *            The hash of the tuple of types of the leading arguments of
	 *            tuples that conform to some {@linkplain TupleTypeDescriptor
	 *            tuple type} being hashed.
	 * @param defaultTypeHash
	 *            The hash of the type that remaining elements of conforming
	 *            types must have.
	 * @return
	 *            The hash of the {@linkplain TupleTypeDescriptor tuple type}
	 *            whose component hash values were provided.
	 */
	static int hashOfTupleTypeWithSizesHashTypesHashDefaultTypeHash (
		final int sizesHash,
		final int typeTupleHash,
		final int defaultTypeHash)
	{
		return sizesHash * 13 + defaultTypeHash * 11 + typeTupleHash * 7;
	}

	/**
	 * Construct a new {@link TupleTypeDescriptor}.
	 *
	 * @param isMutable
	 *        Does the {@linkplain Descriptor descriptor} represent a mutable
	 *        object?
	 */
	protected TupleTypeDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}

	/**
	 * The mutable {@link TupleTypeDescriptor}.
	 */
	private static final TupleTypeDescriptor mutable =
		new TupleTypeDescriptor(true);

	/**
	 * Answer the mutable {@link TupleTypeDescriptor}.
	 *
	 * @return The mutable {@link TupleTypeDescriptor}.
	 */
	public static TupleTypeDescriptor mutable ()
	{
		return mutable;
	}

	/**
	 * The immutable {@link TupleTypeDescriptor}.
	 */
	private static final TupleTypeDescriptor immutable =
		new TupleTypeDescriptor(false);

	/**
	 * Answer the immutable {@link TupleTypeDescriptor}.
	 *
	 * @return The immutable {@link TupleTypeDescriptor}.
	 */
	public static TupleTypeDescriptor immutable ()
	{
		return immutable;
	}
}
