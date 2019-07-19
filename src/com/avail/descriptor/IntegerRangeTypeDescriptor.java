/*
 * IntegerRangeTypeDescriptor.java
 * Copyright © 1993-2018, The Avail Foundation, LLC.
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

import com.avail.annotations.AvailMethod;
import com.avail.annotations.ThreadSafe;
import com.avail.serialization.SerializerOperation;
import com.avail.utility.json.JSONWriter;

import javax.annotation.Nullable;
import java.math.BigInteger;
import java.util.IdentityHashMap;

import static com.avail.descriptor.AvailObject.error;
import static com.avail.descriptor.BottomTypeDescriptor.bottom;
import static com.avail.descriptor.InfinityDescriptor.negativeInfinity;
import static com.avail.descriptor.InfinityDescriptor.positiveInfinity;
import static com.avail.descriptor.InstanceMetaDescriptor.instanceMeta;
import static com.avail.descriptor.IntegerDescriptor.*;
import static com.avail.descriptor.IntegerRangeTypeDescriptor.ObjectSlots.LOWER_BOUND;
import static com.avail.descriptor.IntegerRangeTypeDescriptor.ObjectSlots.UPPER_BOUND;
import static com.avail.descriptor.PojoTypeDescriptor.*;
import static com.avail.descriptor.TypeDescriptor.Types.NUMBER;

/**
 * My instances represent the types of one or more extended integers. There are
 * lower and upper bounds, and flags to indicate whether those bounds are to be
 * treated as inclusive or exclusive of the bounds themselves.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class IntegerRangeTypeDescriptor
extends TypeDescriptor
{
	/**
	 * The layout of object slots for my instances.
	 */
	public enum ObjectSlots
	implements ObjectSlotsEnum
	{
		/**
		 * The extended integer which is the lower bound of this range. It is
		 * either inclusive or exclusive depending on the {@linkplain
		* IntegerRangeTypeDescriptor#o_LowerInclusive lowerInclusive} flag.
		 */
		LOWER_BOUND,

		/**
		 * The extended integer which is the upper bound of this range. It is
		 * either inclusive or exclusive depending on the {@linkplain
		* IntegerRangeTypeDescriptor#o_UpperInclusive upperInclusive} flag.
		 */
		UPPER_BOUND
	}

	@Override
	public void printObjectOnAvoidingIndent (
		final AvailObject object,
		final StringBuilder aStream,
		final IdentityHashMap<A_BasicObject, Void> recursionMap,
		final int indent)
	{
		aStream.append(object.lowerInclusive() ? '[' : '(');
		object.slot(LOWER_BOUND).printOnAvoidingIndent(
			aStream,
			recursionMap,
			indent + 1);
		aStream.append("..");
		object.slot(UPPER_BOUND).printOnAvoidingIndent(
			aStream,
			recursionMap,
			indent + 1);
		aStream.append(object.upperInclusive() ? ']' : ')');
	}

	@Override @AvailMethod
	boolean o_Equals (final AvailObject object, final A_BasicObject another)
	{
		return another.equalsIntegerRangeType(object);
	}

	@Override @AvailMethod
	boolean o_EqualsIntegerRangeType (
		final AvailObject object,
		final A_Type another)
	{
		return object.slot(LOWER_BOUND).equals(another.lowerBound())
			&& object.slot(UPPER_BOUND).equals(another.upperBound())
			&& object.lowerInclusive() == another.lowerInclusive()
			&& object.upperInclusive() == another.upperInclusive();
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * Answer the object's hash value.  Be careful, as the range (10..20) is the
	 * same type as the range [11..19], so they should hash the same.  Actually,
	 * this is taken care of during instance creation - if an exclusive bound is
	 * finite, it is converted to its inclusive equivalent.  Otherwise asking
	 * for one of the bounds would yield a value which is either inside or
	 * outside depending on something that should not be observable (because it
	 * serves to distinguish two representations of equal objects).
	 * </p>
	 */
	@Override @AvailMethod
	int o_Hash (final AvailObject object)
	{
		return computeHash(
			object.slot(LOWER_BOUND).hash(),
			object.slot(UPPER_BOUND).hash(),
			object.lowerInclusive(),
			object.upperInclusive());
	}

	@Override @AvailMethod
	boolean o_IsIntegerRangeType (final AvailObject object)
	{
		return true;
	}

	@Override @AvailMethod
	boolean o_IsSubtypeOf (final AvailObject object, final A_Type aType)
	{
		return aType.isSupertypeOfIntegerRangeType(object);
	}

	/**
	 * Integer range types compare like the subsets they represent. The only
	 * elements that matter in the comparisons are within one unit of the four
	 * boundary conditions (because these are the only places where the type
	 * memberships can change), so just use these. In particular, use the value
	 * just inside and the value just outside each boundary. If the subtype's
	 * constraints don't logically imply the supertype's constraints then the
	 * subtype is not actually a subtype. Make use of the fact that integer
	 * range types have their bounds canonized into inclusive form, if finite,
	 * at range type creation time.
	 */
	@Override @AvailMethod
	boolean o_IsSupertypeOfIntegerRangeType (
		final AvailObject object,
		final A_Type possibleSub)
	{
		final A_Number subMinObject = possibleSub.lowerBound();
		final AvailObject superMinObject = object.slot(LOWER_BOUND);
		if (subMinObject.lessThan(superMinObject))
		{
			return false;
		}
		if (subMinObject.equals(superMinObject)
			&& possibleSub.lowerInclusive()
			&& !object.lowerInclusive())
		{
			return false;
		}
		final A_Number subMaxObject = possibleSub.upperBound();
		final A_Number superMaxObject = object.slot(UPPER_BOUND);
		if (superMaxObject.lessThan(subMaxObject))
		{
			return false;
		}
		return !superMaxObject.equals(subMaxObject)
			|| !possibleSub.upperInclusive()
			|| object.upperInclusive();
	}

	@Override @AvailMethod
	A_Number o_LowerBound (final AvailObject object)
	{
		return object.slot(LOWER_BOUND);
	}

	@Override @AvailMethod
	boolean o_LowerInclusive (final AvailObject object)
	{
		return lowerInclusive;
	}

	@Override @AvailMethod
	AvailObject o_MakeImmutable (final AvailObject object)
	{
		if (isMutable())
		{
			// There are no immutable descriptors, so make the object shared.
			object.makeShared();
		}
		return object;
	}

	@Override
	@Nullable Object o_MarshalToJava (
		final AvailObject object,
		final @Nullable Class<?> ignoredClassHint)
	{
		if (object.isSubtypeOf(byteRange()))
		{
			return Byte.TYPE;
		}
		if (object.isSubtypeOf(charRange()))
		{
			return Character.TYPE;
		}
		if (object.isSubtypeOf(shortRange()))
		{
			return Short.TYPE;
		}
		if (object.isSubtypeOf(intRange()))
		{
			return Integer.TYPE;
		}
		if (object.isSubtypeOf(longRange()))
		{
			return Long.TYPE;
		}
		if (object.isSubtypeOf(integers()))
		{
			return BigInteger.class;
		}
		// If the integer range type is something else, then treat the
		// type as opaque.
		return super.o_MarshalToJava(object, ignoredClassHint);
	}

	@Override
	boolean o_RangeIncludesInt (
		final AvailObject object,
		final int anInt)
	{
		final A_Number lower = object.slot(LOWER_BOUND);
		@Nullable A_Number asInteger = null;
		if (lower.isInt())
		{
			if (anInt < lower.extractInt())
			{
				return false;
			}
		}
		else if (!lower.isFinite())
		{
			if (lower.isPositive())
			{
				return false;
			}
		}
		else
		{
			asInteger = fromInt(anInt);
			if (asInteger.lessThan(lower))
			{
				return false;
			}
		}

		final A_Number upper = object.slot(UPPER_BOUND);
		if (upper.isInt())
		{
			return anInt <= upper.extractInt();
		}
		else if (!upper.isFinite())
		{
			return upper.isPositive();
		}
		else
		{
			if (asInteger == null)
			{
				asInteger = fromInt(anInt);
			}
			return !upper.lessThan(asInteger);
		}
	}

	@Override @AvailMethod @ThreadSafe
	SerializerOperation o_SerializerOperation(final AvailObject object)
	{
		return SerializerOperation.INTEGER_RANGE_TYPE;
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
		return another.typeIntersectionOfIntegerRangeType(object);
	}

	@Override @AvailMethod
	A_Type o_TypeIntersectionOfIntegerRangeType (
		final AvailObject object,
		final A_Type another)
	{
		A_Number minObject = object.slot(LOWER_BOUND);
		boolean isMinInc = object.lowerInclusive();
		if (another.lowerBound().equals(minObject))
		{
			isMinInc = isMinInc && another.lowerInclusive();
		}
		else if (minObject.lessThan(another.lowerBound()))
		{
			minObject = another.lowerBound();
			isMinInc = another.lowerInclusive();
		}
		A_Number maxObject = object.slot(UPPER_BOUND);
		boolean isMaxInc = object.upperInclusive();
		if (another.upperBound().equals(maxObject))
		{
			isMaxInc = isMaxInc && another.upperInclusive();
		}
		else if (another.upperBound().lessThan(maxObject))
		{
			maxObject = another.upperBound();
			isMaxInc = another.upperInclusive();
		}
		// At least two references now.
		return integerRangeType(
			minObject.makeImmutable(),
			isMinInc,
			maxObject.makeImmutable(),
			isMaxInc);
	}

	@Override @AvailMethod
	A_Type o_TypeIntersectionOfPrimitiveTypeEnum (
		final AvailObject object,
		final Types primitiveTypeEnum)
	{
		return NUMBER.superTests[primitiveTypeEnum.ordinal()]
			? object
			: bottom();
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
		return another.typeUnionOfIntegerRangeType(object);
	}

	@Override @AvailMethod
	A_Type o_TypeUnionOfIntegerRangeType (
		final AvailObject object,
		final A_Type another)
	{
		A_Number minObject = object.slot(LOWER_BOUND);
		boolean isMinInc = object.lowerInclusive();
		if (another.lowerBound().equals(minObject))
		{
			isMinInc = isMinInc || another.lowerInclusive();
		}
		else if (another.lowerBound().lessThan(minObject))
		{
			minObject = another.lowerBound();
			isMinInc = another.lowerInclusive();
		}
		A_Number maxObject = object.slot(UPPER_BOUND);
		boolean isMaxInc = object.upperInclusive();
		if (another.upperBound().equals(maxObject))
		{
			isMaxInc = isMaxInc || another.upperInclusive();
		}
		else if (maxObject.lessThan(another.upperBound()))
		{
			maxObject = another.upperBound();
			isMaxInc = another.upperInclusive();
		}
		return integerRangeType(minObject, isMinInc, maxObject, isMaxInc);
	}

	@Override @AvailMethod
	A_Type o_TypeUnionOfPrimitiveTypeEnum (
		final AvailObject object,
		final Types primitiveTypeEnum)
	{
		return NUMBER.unionTypes[primitiveTypeEnum.ordinal()];
	}

	@Override @AvailMethod
	A_Number o_UpperBound (final AvailObject object)
	{
		return object.slot(UPPER_BOUND);
	}

	@Override @AvailMethod
	boolean o_UpperInclusive (final AvailObject object)
	{
		return upperInclusive;
	}

	@Override
	void o_WriteTo (final AvailObject object, final JSONWriter writer)
	{
		writer.startObject();
		writer.write("kind");
		writer.write("integer type");
		writer.write("lower bound");
		object.slot(LOWER_BOUND).writeTo(writer);
		writer.write("upper bound");
		object.slot(UPPER_BOUND).writeTo(writer);
		writer.endObject();
	}

	/**
	 * Compute the hash of the {@link IntegerRangeTypeDescriptor} that has the
	 * specified information.
	 *
	 * @param lowerBoundHash The hash of the lower bound.
	 * @param upperBoundHash The hash of the upper bound.
	 * @param lowerInclusive Whether the lower bound is inclusive.
	 * @param upperInclusive Whether the upper bound is inclusive.
	 * @return The hash value.
	 */
	private static int computeHash (
		final int lowerBoundHash,
		final int upperBoundHash,
		final boolean lowerInclusive,
		final boolean upperInclusive)
	{
		final int flagsHash =
			lowerInclusive
				? upperInclusive ? 0x1503045E : 0x753A6C17
				: upperInclusive ? 0x1DB2D751 : 0x1130427D;
		return lowerBoundHash * 29 ^ flagsHash ^ upperBoundHash;
	}

	/**
	 * Return a range consisting of a single {@linkplain IntegerDescriptor
	 * integer} or {@linkplain InfinityDescriptor infinity}.
	 *
	 * @param integerObject
	 *            An Avail integer or infinity.
	 * @return
	 *            A {@linkplain IntegerRangeTypeDescriptor range} containing a
	 *            single value.
	 */
	public static A_Type singleInteger (final A_Number integerObject)
	{
		integerObject.makeImmutable();
		return integerRangeType(integerObject, true, integerObject, true);
	}

	/**
	 * Return a range consisting of a single integer or infinity.
	 *
	 * @param anInt A Java {@code int}.
	 * @return A range containing a single value.
	 */
	public static A_Type singleInt (final int anInt)
	{
		final A_Number integerObject = fromInt(anInt).makeImmutable();
		return integerRangeType(integerObject, true, integerObject, true);
	}

	/**
	 * Create an integer range type.  Normalize it as necessary, converting
	 * exclusive finite bounds into equivalent inclusive bounds.  An empty range
	 * is always converted to {@linkplain BottomTypeDescriptor bottom}.
	 *
	 * @param lowerBound
	 *            The lowest value inside (or just outside) the range.
	 * @param lowerInclusive
	 *            Whether to include the lowerBound.
	 * @param upperBound
	 *            The highest value inside (or just outside) the range.
	 * @param upperInclusive
	 *            Whether to include the upperBound.
	 * @return
	 *            The new normalized integer range type.
	 */
	public static A_Type integerRangeType (
		final A_Number lowerBound,
		final boolean lowerInclusive,
		final A_Number upperBound,
		final boolean upperInclusive)
	{
		if (lowerBound.sameAddressAs(upperBound))
		{
			if (lowerBound.descriptor().isMutable())
			{
				error(
					"Don't plug in a mutable object as two distinct"
					+ " construction parameters");
			}
		}
		A_Number low = lowerBound;
		boolean lowInc = lowerInclusive;
		if (!lowInc)
		{
			// Try to rewrite (if possible) as inclusive boundary.
			if (low.isFinite())
			{
				low = low.noFailPlusCanDestroy(one(), false);
				lowInc = true;
			}
		}
		A_Number high = upperBound;
		boolean highInc = upperInclusive;
		if (!highInc)
		{
			// Try to rewrite (if possible) as inclusive boundary.
			if (high.isFinite())
			{
				high = high.noFailMinusCanDestroy(one(), false);
				highInc = true;
			}
		}
		if (high.lessThan(low))
		{
			return bottom();
		}
		if (high.equals(low) && (!highInc || !lowInc))
		{
			// Unusual cases such as [INF..INF) give preference to exclusion
			// over inclusion.
			return bottom();
		}
		if (low.isInt() && high.isInt())
		{
			assert lowInc && highInc;
			final int lowInt = low.extractInt();
			final int highInt = high.extractInt();
			if (0 <= lowInt && lowInt < smallRangeLimit
				&& 0 <= highInt && highInt < smallRangeLimit)
			{
				return smallRanges[highInt][lowInt];
			}
		}
		final IntegerRangeTypeDescriptor descriptor =
			lookupDescriptor(true, lowInc, highInc);
		final AvailObject result = descriptor.create();
		result.setSlot(LOWER_BOUND, low);
		result.setSlot(UPPER_BOUND, high);
		return result;
	}

	/**
	 * Create an inclusive-inclusive range with the given endpoints.
	 *
	 * @param lowerBound The low end, inclusive, of the range.
	 * @param upperBound The high end, inclusive, of the range.
	 * @return The integral type containing the bounds and all integers between.
	 */
	public static A_Type inclusive (
		final A_Number lowerBound,
		final A_Number upperBound)
	{
		return integerRangeType(lowerBound, true, upperBound, true);
	}

	/**
	 * Create an inclusive-inclusive range with the given endpoints.
	 *
	 * @param lowerBound The low end, inclusive, of the range.
	 * @param upperBound The high end, inclusive, of the range.
	 * @return The integral type containing the bounds and all integers between.
	 */
	public static A_Type inclusive (
		final long lowerBound,
		final long upperBound)
	{
		return integerRangeType(
			fromLong(lowerBound), true, fromLong(upperBound), true);
	}

	/**
	 * Construct a new {@link IntegerRangeTypeDescriptor}.
	 *
	 * @param isMutable
	 *        {@code true} if the descriptor is {@linkplain Mutability#MUTABLE
	 *        mutable}, {@code false} if it is {@linkplain Mutability#SHARED
	 *        shared}.
	 * @param lowerInclusive
	 *        Do my object instances include their lower bound?
	 * @param upperInclusive
	 *        Do my object instances include their upper bound?
	 */
	private IntegerRangeTypeDescriptor (
		final boolean isMutable,
		final boolean lowerInclusive,
		final boolean upperInclusive)
	{
		super(
			isMutable ? Mutability.MUTABLE : Mutability.SHARED,
			TypeTag.EXTENDED_INTEGER_TYPE_TAG,
			ObjectSlots.class,
			null);
		this.lowerInclusive = lowerInclusive;
		this.upperInclusive = upperInclusive;
	}

	/**
	 * When true, my object instances (i.e., instances of {@link AvailObject})
	 * are considered to include their lower bound.
	 */
	private final boolean lowerInclusive;

	/**
	 * When true, my object instances (i.e., instances of {@link AvailObject})
	 * are considered to include their upper bound.
	 */
	private final boolean upperInclusive;

	/**
	 * The array of descriptor instances of this class.  There are three boolean
	 * decisions to make when selecting a descriptor, namely:
	 * <ul>
	 * <li>Whether the descriptor is <em>{@linkplain Mutability#SHARED
	 * shared}</em>,</li>
	 * <li>Whether the descriptor's instances include their lower bound, and
	 * </li>
	 * <li>Whether the descriptor's instances include their upper bound.</li>
	 * </ul>
	 * These occur in bit positions 0x01, 0x02, and 0x04 of the array
	 * subscripts, respectively.
	 */
	private static final IntegerRangeTypeDescriptor[] descriptors;

	static
	{
		descriptors = new IntegerRangeTypeDescriptor[8];
		for (int i = 0; i < 8; i++)
		{
			descriptors[i] = new IntegerRangeTypeDescriptor(
				(i & 1) == 0,
				(i & 2) != 0,
				(i & 4) != 0);
		}
	}

	/**
	 * Answer the descriptor with the three specified boolean properties.
	 *
	 * @param isMutable
	 *        {@code true} if the descriptor's objects are {@linkplain
	 *        Mutability#MUTABLE mutable}, {@code false} if they are {@linkplain
	 *        Mutability#SHARED shared}.
	 * @param lowerInclusive
	 *        Whether the descriptor's objects include the lower bound.
	 * @param upperInclusive
	 *        Whether the descriptor's objects include the upper bound.
	 * @return The requested {@link IntegerRangeTypeDescriptor}.
	 */
	private static IntegerRangeTypeDescriptor lookupDescriptor (
		final boolean isMutable,
		final boolean lowerInclusive,
		final boolean upperInclusive)
	{
		final int subscript =
			(isMutable ? 0 : 1)
			| (lowerInclusive ? 2 : 0)
			| (upperInclusive ? 4 : 0);
		return descriptors[subscript];
	}

	@Override
	AbstractDescriptor mutable ()
	{
		return lookupDescriptor(true, lowerInclusive, upperInclusive);
	}

	@Override
	AbstractDescriptor immutable ()
	{
		// There are no immutable descriptors, only shared ones.
		return lookupDescriptor(false, lowerInclusive, upperInclusive);
	}

	@Override
	AbstractDescriptor shared ()
	{
		return lookupDescriptor(false, lowerInclusive, upperInclusive);
	}

	/** One past the maximum lower or upper bound of a pre-built range. */
	static final int smallRangeLimit = 10;

	/**
	 * An array of arrays of small inclusive-inclusive ranges.  The first index
	 * is the upper bound, and must be in [0..smallRangeLimit-1].  The second
	 * index is the lower bound, and must be in the range [0..upper bound].
	 * This scheme allows both indices to start at zero and not include any
	 * degenerate elements.
	 *
	 * <p>Use of these pre-built ranges is not mandatory, but is generally
	 * recommended for performance.  The {@link #create()} operation uses them
	 * whenever possible.</p>
	 */
	private static final A_Type[][] smallRanges = new A_Type[smallRangeLimit][];

	static
	{
		for (int upper = 0; upper < smallRangeLimit; upper++)
		{
			final A_Type[] byLower = new A_Type[upper + 1];
			for (int lower = 0; lower <= upper; lower++)
			{
				final IntegerRangeTypeDescriptor descriptor =
					lookupDescriptor(true, true, true);
				final AvailObject result = descriptor.create();
				result.setSlot(LOWER_BOUND, fromInt(lower));
				result.setSlot(UPPER_BOUND, fromInt(upper));
				byLower[lower] = result.makeShared();
			}
			smallRanges[upper] = byLower;
		}
	}

	/** The range [0..1]. */
	private static final A_Type zeroOrOne = smallRanges[1][0];

	/**
	 * Return the range [0..1].
	 *
	 * @return The integer range that includes just zero and one.
	 */
	public static A_Type zeroOrOne ()
	{
		return zeroOrOne;
	}

	/** The range [0..255]. */
	private static final A_Type bytes = inclusive(0, 255).makeShared();

	/**
	 * Return the range [0..255].
	 *
	 * @return The unsigned byte range.
	 */
	public static A_Type bytes ()
	{
		return bytes;
	}

	/** The range of Unicode code points, [0..1114111]. */
	private static final A_Type characterCodePoints =
		inclusive(0, CharacterDescriptor.maxCodePointInt).makeShared();

	/**
	 * Return the range of Unicode code points, [0..1114111].
	 *
	 * @return The range of Unicode code points.
	 */
	public static A_Type characterCodePoints ()
	{
		return characterCodePoints;
	}

	/** The range of integers including infinities, [-∞..∞]. */
	private static final A_Type extendedIntegers =
		inclusive(negativeInfinity(), positiveInfinity()).makeShared();

	/**
	 * Return the range of integers including infinities, [-∞..∞].
	 *
	 * @return The range of integers including infinities.
	 */
	public static A_Type extendedIntegers ()
	{
		return extendedIntegers;
	}

	/** The range of integers not including infinities, (∞..∞). */
	private static final A_Type integers =
		integerRangeType(
			negativeInfinity(), false, positiveInfinity(), false
		).makeShared();

	/**
	 * Return the range of integers not including infinities, (∞..∞).
	 *
	 * @return The range of finite integers.
	 */
	public static A_Type integers ()
	{
		return integers;
	}

	/** The range of natural numbers, [1..∞). */
	private static final A_Type naturalNumbers =
		integerRangeType(one(), true, positiveInfinity(), false).makeShared();

	/**
	 * Return the range of natural numbers, [1..∞).
	 *
	 * @return The range of positive finite integers.
	 */
	public static A_Type naturalNumbers ()
	{
		return naturalNumbers;
	}

	/** The range [0..15]. */
	private static final A_Type nybbles = inclusive(0, 15).makeShared();

	/**
	 * Return the range [0..15].
	 *
	 * @return The non-negative integers that can be represented in 4 bits.
	 */
	public static A_Type nybbles ()
	{
		return nybbles;
	}

	/** The range [0..65535]. */
	private static final A_Type unsignedShorts =
		inclusive(0, 65535).makeShared();

	/**
	 * Return the range [0..65535].
	 *
	 * @return The non-negative integers that can be represented in 16 bits.
	 */
	public static A_Type unsignedShorts ()
	{
		return unsignedShorts;
	}

	/** The range of whole numbers, [0..∞). */
	private static final A_Type wholeNumbers =
		integerRangeType(zero(), true, positiveInfinity(), false).makeShared();

	/**
	 * Return the range of whole numbers, [0..∞).
	 *
	 * @return The non-negative finite integers.
	 */
	public static A_Type wholeNumbers ()
	{
		return wholeNumbers;
	}

	/** The range of a signed 32-bit {@code int}, [-2^31..2^31). */
	private static final A_Type int32 =
		inclusive(Integer.MIN_VALUE, Integer.MAX_VALUE).makeShared();

	/**
	 * Return the range of 32-bit signed ints.
	 *
	 * @return [-0x80000000..0x7FFFFFFF].
	 */
	public static A_Type int32 ()
	{
		return int32;
	}

	/** The range of a signed 64-bit {@code long}, [-2^63..2^63). */
	private static final A_Type int64 =
		inclusive(Long.MIN_VALUE, Long.MAX_VALUE).makeShared();

	/**
	 * Return the range of 64-bit signed longs.
	 *
	 * @return [-0x8000_0000_0000_0000..0x7FFF_FFFF_FFFF_FFFF].
	 */
	public static A_Type int64 ()
	{
		return int64;
	}

	/**
	 * The metatype for integers. This is an {@linkplain InstanceTypeDescriptor
	 * instance type} whose base instance is {@linkplain #extendedIntegers
	 * extended integer}, and therefore has all integer range types as
	 * instances.
	 */
	private static final A_Type extendedIntegersMeta =
		instanceMeta(extendedIntegers).makeShared();

	/**
	 * Return the metatype for all integer range types.
	 *
	 * @return The integer metatype.
	 */
	public static A_Type extendedIntegersMeta ()
	{
		return extendedIntegersMeta;
	}
}
