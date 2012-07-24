/**
 * IntegerRangeTypeDescriptor.java
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

import static com.avail.descriptor.IntegerRangeTypeDescriptor.ObjectSlots.*;
import static com.avail.descriptor.AvailObject.error;
import java.math.BigInteger;
import java.util.List;
import com.avail.annotations.*;
import com.avail.serialization.SerializerOperation;

/**
 * My instances represent the types of one or more extended integers.  There are
 * lower and upper bounds, and flags to indicate whether those bounds are to be
 * treated as inclusive or exclusive of the bounds themselves.
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 */
public class IntegerRangeTypeDescriptor
extends TypeDescriptor
{
	/**
	 * The layout of object slots for my instances.
	 */
	public enum ObjectSlots
	implements ObjectSlotsEnum
	{
		/**
		 * The extended integer which is the lower bound of this range.  It is
		 * either inclusive or exclusive depending on the {@linkplain
		* IntegerRangeTypeDescriptor#o_LowerInclusive lowerInclusive} flag.
		 */
		LOWER_BOUND,

		/**
		 * The extended integer which is the upper bound of this range.  It is
		 * either inclusive or exclusive depending on the {@linkplain
		* IntegerRangeTypeDescriptor#o_UpperInclusive upperInclusive} flag.
		 */
		UPPER_BOUND
	}

	@Override @AvailMethod
	@NotNull AvailObject o_LowerBound (
		final @NotNull AvailObject object)
	{
		return object.slot(LOWER_BOUND);
	}

	@Override @AvailMethod
	@NotNull AvailObject o_UpperBound (
		final @NotNull AvailObject object)
	{
		return object.slot(UPPER_BOUND);
	}

	@Override
	public void printObjectOnAvoidingIndent (
		final @NotNull AvailObject object,
		final @NotNull StringBuilder aStream,
		final @NotNull List<AvailObject> recursionList,
		final int indent)
	{
		aStream.append(object.lowerInclusive() ? '[' : '(');
		object.slot(LOWER_BOUND).printOnAvoidingIndent(
			aStream,
			recursionList,
			indent + 1);
		aStream.append("..");
		object.slot(UPPER_BOUND).printOnAvoidingIndent(
			aStream,
			recursionList,
			indent + 1);
		aStream.append(object.upperInclusive() ? ']' : ')');
	}

	@Override @AvailMethod
	boolean o_Equals (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
	{
		return another.equalsIntegerRangeType(object);
	}

	@Override @AvailMethod
	boolean o_EqualsIntegerRangeType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
	{
		if (!object.slot(LOWER_BOUND).equals(another.lowerBound()))
		{
			return false;
		}
		if (!object.slot(UPPER_BOUND).equals(another.upperBound()))
		{
			return false;
		}
		if (object.lowerInclusive() != another.lowerInclusive())
		{
			return false;
		}
		if (object.upperInclusive() != another.upperInclusive())
		{
			return false;
		}
		return true;
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * Answer the object's hash value.  Be careful, as the range (10..20) is the
	 * same type as the range [11..19], so they should hash the same.  Actually,
	 * this is taken care of during instance creation - if an exclusive bound is
	 * finite, it is converted to its inclusive equivalent.  Otherwise asking
	 * for one of the bounds will yield a value which is either inside or
	 * outside depending on something that should not be observable (because it
	 * serves to distinguish two representations of equal objects).
	 */
	@Override @AvailMethod
	int o_Hash (
		final @NotNull AvailObject object)
	{
		return IntegerRangeTypeDescriptor.computeHash(
			object.slot(LOWER_BOUND).hash(),
			object.slot(UPPER_BOUND).hash(),
			object.lowerInclusive(),
			object.upperInclusive());
	}

	@Override @AvailMethod
	boolean o_LowerInclusive (
		final @NotNull AvailObject object)
	{
		return lowerInclusive;
	}

	@Override @AvailMethod
	boolean o_UpperInclusive (
		final @NotNull AvailObject object)
	{
		return upperInclusive;
	}

	@Override @AvailMethod
	boolean o_IsSubtypeOf (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aType)
	{
		// Check if object (a type) is a subtype of aType (should also be a type).
		return aType.isSupertypeOfIntegerRangeType(object);
	}

	/**
	 * Integer range types compare like the subsets they represent.  The only
	 * elements that matter in the comparisons are within one unit of the four
	 * boundary conditions (because these are the only places where the type
	 * memberships can change), so just use these.  In particular, use the value
	 * just inside and the value just outside each boundary.  If the subtype's
	 * constraints don't logically imply the supertype's constraints then the
	 * subtype is not actually a subtype.  Make use of the fact that integer
	 * range types have their bounds canonized into inclusive form, if finite,
	 * at range type creation time.
	 */
	@Override @AvailMethod
	boolean o_IsSupertypeOfIntegerRangeType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject possibleSub)
	{
		final AvailObject subMinObject = possibleSub.lowerBound();
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
		final AvailObject subMaxObject = possibleSub.upperBound();
		final AvailObject superMaxObject = object.slot(UPPER_BOUND);
		if (superMaxObject.lessThan(subMaxObject))
		{
			return false;
		}
		if (superMaxObject.equals(subMaxObject)
			&& possibleSub.upperInclusive()
			&& !object.upperInclusive())
		{
			return false;
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
		return another.typeIntersectionOfIntegerRangeType(object);
	}

	@Override @AvailMethod
	@NotNull AvailObject o_TypeIntersectionOfIntegerRangeType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
	{
		AvailObject minObject = object.slot(LOWER_BOUND);
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
		AvailObject maxObject = object.slot(UPPER_BOUND);
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
		return IntegerRangeTypeDescriptor.create(
			minObject.makeImmutable(),
			isMinInc,
			maxObject.makeImmutable(),
			isMaxInc);
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
		return another.typeUnionOfIntegerRangeType(object);
	}

	@Override @AvailMethod
	@NotNull AvailObject o_TypeUnionOfIntegerRangeType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
	{
		AvailObject minObject = object.slot(LOWER_BOUND);
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
		AvailObject maxObject = object.slot(UPPER_BOUND);
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
		return IntegerRangeTypeDescriptor.create(
			minObject,
			isMinInc,
			maxObject,
			isMaxInc);
	}

	@Override @AvailMethod
	boolean o_IsIntegerRangeType (
		final @NotNull AvailObject object)
	{
		return true;
	}

	@Override @AvailMethod @ThreadSafe
	@NotNull SerializerOperation o_SerializerOperation(
		final @NotNull AvailObject object)
	{
		return SerializerOperation.INTEGER_RANGE_TYPE;
	}

	@Override
	Object o_MarshalToJava (
		final @NotNull AvailObject object,
		final Class<?> ignoredClassHint)
	{
		if (object.isSubtypeOf(PojoTypeDescriptor.byteRange()))
		{
			return Byte.TYPE;
		}
		else if (object.isSubtypeOf(PojoTypeDescriptor.shortRange()))
		{
			return Short.TYPE;
		}
		else if (object.isSubtypeOf(PojoTypeDescriptor.intRange()))
		{
			return Integer.TYPE;
		}
		else if (object.isSubtypeOf(PojoTypeDescriptor.longRange()))
		{
			return Long.TYPE;
		}
		else if (object.isSubtypeOf(Integers))
		{
			return BigInteger.class;
		}
		// If the integer range type is something else, then treat the
		// type as opaque.
		return super.o_MarshalToJava(object, ignoredClassHint);
	}

	@Override
	boolean o_RangeIncludesInt (
		final @NotNull AvailObject object,
		final int anInt)
	{
		final AvailObject lower = object.slot(LOWER_BOUND);
		AvailObject asInteger = null;
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
			asInteger = IntegerDescriptor.fromInt(anInt);
			if (asInteger.lessThan(lower))
			{
				return false;
			}
		}

		final AvailObject upper = object.slot(UPPER_BOUND);
		if (upper.isInt())
		{
			if (anInt > upper.extractInt())
			{
				return false;
			}
		}
		else if (!upper.isFinite())
		{
			if (!upper.isPositive())
			{
				return false;
			}
		}
		else
		{
			if (asInteger == null)
			{
				asInteger = IntegerDescriptor.fromInt(anInt);
			}
			if (upper.lessThan(asInteger))
			{
				return false;
			}
		}
		return true;
	}

	/**
	 * The range [0..255].
	 */
	static AvailObject Bytes;

	/**
	 * The range of Unicode code points, [0..1114111].
	 */
	static AvailObject CharacterCodePoints;

	/**
	 * The range of integers including infinities, [-∞..∞].
	 */
	static AvailObject ExtendedIntegers;

	/**
	 * The range of integers not including infinities, (∞..∞).
	 */
	static AvailObject Integers;

	/**
	 * The range of natural numbers, [1..∞).
	 */
	static AvailObject NaturalNumbers;

	/**
	 * The range [0..15].
	 */
	static AvailObject Nybbles;

	/**
	 * The range [0..65535].
	 */
	static AvailObject UnsignedShorts;

	/**
	 * The range of whole numbers, [0..∞).
	 */
	static AvailObject WholeNumbers;

	/**
	 * The metatype for integers.  This is an {@linkplain InstanceTypeDescriptor
	 * instance type} whose base instance is {@linkplain #ExtendedIntegers
	 * extended integer}, and therefore has all integer range types as
	 * instances.
	 */
	static AvailObject Meta;

	static void createWellKnownObjects ()
	{
		Bytes = create(
			IntegerDescriptor.zero(),
			true,
			IntegerDescriptor.fromUnsignedByte((short)255),
			true).makeImmutable();
		CharacterCodePoints = create(
			IntegerDescriptor.zero(),
			true,
			IntegerDescriptor.fromInt(CharacterDescriptor.maxCodePointInt),
			true).makeImmutable();
		ExtendedIntegers = create(
			InfinityDescriptor.negativeInfinity(),
			true,
			InfinityDescriptor.positiveInfinity(),
			true).makeImmutable();
		Integers = create(
			InfinityDescriptor.negativeInfinity(),
			false,
			InfinityDescriptor.positiveInfinity(),
			false).makeImmutable();
		NaturalNumbers = create(
			IntegerDescriptor.one(),
			true,
			InfinityDescriptor.positiveInfinity(),
			false).makeImmutable();
		Nybbles = create(
			IntegerDescriptor.zero(),
			true,
			IntegerDescriptor.fromUnsignedByte((short)15),
			true).makeImmutable();
		UnsignedShorts = create(
			IntegerDescriptor.zero(),
			true,
			IntegerDescriptor.fromInt(65535),
			true).makeImmutable();
		WholeNumbers = create(
			IntegerDescriptor.zero(),
			true,
			InfinityDescriptor.positiveInfinity(),
			false).makeImmutable();

		Meta = InstanceMetaDescriptor.on(
			ExtendedIntegers).makeImmutable();
	}

	static void clearWellKnownObjects ()
	{
		Bytes = null;
		CharacterCodePoints = null;
		ExtendedIntegers = null;
		Integers = null;
		NaturalNumbers = null;
		Nybbles = null;
		UnsignedShorts = null;
		WholeNumbers = null;
		Meta = null;
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
	static int computeHash (
		final int lowerBoundHash,
		final int upperBoundHash,
		final boolean lowerInclusive,
		final boolean upperInclusive)
	{
		final int flagsHash =
			lowerInclusive
				? (upperInclusive ? 0x1503045E : 0x053A6C17)
				: upperInclusive ? 0x1DB2D751 : 0x1130427D;
		return lowerBoundHash * 29 ^ flagsHash ^ upperBoundHash;
	}

	/**
	 * Return the range [0..255].
	 *
	 * @return The unsigned byte range.
	 */
	public static AvailObject bytes ()
	{
		return Bytes;
	}

	/**
	 * Return the range of Unicode code points, [0..1114111].
	 *
	 * @return The range of Unicode code points.
	 */
	public static AvailObject characterCodePoints ()
	{
		return CharacterCodePoints;
	}

	/**
	 * Return the range of integers including infinities, [-∞..∞].
	 *
	 * @return The range of integers including infinities.
	 */
	public static AvailObject extendedIntegers ()
	{
		return ExtendedIntegers;
	}

	/**
	 * Return the range of integers not including infinities, (∞..∞).
	 *
	 * @return The range of finite integers.
	 */
	public static AvailObject integers ()
	{
		return Integers;
	}

	/**
	 * Return the range of natural numbers, [1..∞).
	 *
	 * @return The range of positive finite integers.
	 */
	public static AvailObject naturalNumbers ()
	{
		return NaturalNumbers;
	}

	/**
	 * Return the range [0..15].
	 *
	 * @return The non-negative integers that can be represented in 4 bits.
	 */
	public static AvailObject nybbles ()
	{
		return Nybbles;
	}

	/**
	 * Return the range [0..65535].
	 *
	 * @return The non-negative integers that can be represented in 16 bits.
	 */
	public static AvailObject unsignedShorts ()
	{
		return UnsignedShorts;
	}

	/**
	 * Return the range of whole numbers, [0..∞).
	 *
	 * @return The non-negative finite integers.
	 */
	public static AvailObject wholeNumbers ()
	{
		return WholeNumbers;
	}

	/**
	 * Return the metatype for all integer range types.
	 *
	 * @return The integer metatype.
	 */
	public static AvailObject meta ()
	{
		return Meta;
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
	public static AvailObject singleInteger (final AvailObject integerObject)
	{
		integerObject.makeImmutable();
		return IntegerRangeTypeDescriptor.create(
			integerObject, true, integerObject, true);
	}

	/**
	 * Return a range consisting of a single integer or infinity.
	 *
	 * @param anInt A Java <code>int</code>.
	 * @return A range containing a single value.
	 */
	public static AvailObject singleInt (final int anInt)
	{
		final AvailObject integerObject = IntegerDescriptor.fromInt(anInt);
		integerObject.makeImmutable();
		return IntegerRangeTypeDescriptor.create(
			integerObject, true, integerObject, true);
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
	public static AvailObject create (
		final @NotNull AvailObject lowerBound,
		final boolean lowerInclusive,
		final @NotNull AvailObject upperBound,
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
		AvailObject low = lowerBound;
		boolean lowInc = lowerInclusive;
		if (!lowInc)
		{
			// Try to rewrite (if possible) as inclusive boundary.
			if (low.isFinite())
			{
				low = low.noFailPlusCanDestroy(IntegerDescriptor.one(), false);
				lowInc = true;
			}
		}
		AvailObject high = upperBound;
		boolean highInc = upperInclusive;
		if (!highInc)
		{
			// Try to rewrite (if possible) as inclusive boundary.
			if (high.isFinite())
			{
				high = high.noFailMinusCanDestroy(
					IntegerDescriptor.one(), false);
				highInc = true;
			}
		}
		if (high.lessThan(low))
		{
			return BottomTypeDescriptor.bottom();
		}
		if (high.equals(low) && (!highInc || !lowInc))
		{
			// Unusual cases such as [INF..INF) give preference to exclusion
			// over inclusion.
			return BottomTypeDescriptor.bottom();
		}
		final IntegerRangeTypeDescriptor descriptor =
			lookupDescriptor(true, lowInc, highInc);
		final AvailObject result = descriptor.create();
		result.setSlot(LOWER_BOUND, low);
		result.setSlot(UPPER_BOUND, high);
		result.makeImmutable();
		return result;
	}

	/**
	 * Construct a new {@link IntegerRangeTypeDescriptor}.
	 *
	 * @param isMutable
	 *            Does the {@linkplain Descriptor descriptor} represent a
	 *            mutable object?
	 * @param lowerInclusive
	 *            Do my object instances include their lower bound?
	 * @param upperInclusive
	 *            Do my object instances include their upper bound?
	 */
	protected IntegerRangeTypeDescriptor (
		final boolean isMutable,
		final boolean lowerInclusive,
		final boolean upperInclusive)
	{
		super(isMutable);
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
	 * <li>Whether the descriptor is <em>immutable</em>,</li>
	 * <li>Whether the descriptor's instances include their lower bound,
	 * and</li>
	 * <li>Whether the descriptor's instances include their upper bound.</li>
	 * </ul>
	 * These occur in bit positions 0x01, 0x02, and 0x04 of the array
	 * subscripts, respectively.
	 */
	private final static IntegerRangeTypeDescriptor[] descriptors;

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
	 *            Whether the descriptor's objects are mutable.
	 * @param lowerInclusive
	 *            Whether the descriptor's objects include the lower bound.
	 * @param upperInclusive
	 *            Whether the descriptor's objects include the upper bound.
	 * @return
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
}
