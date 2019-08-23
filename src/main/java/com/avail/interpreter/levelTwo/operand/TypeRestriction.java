/*
 * TypeRestriction.java
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

package com.avail.interpreter.levelTwo.operand;

import com.avail.descriptor.*;
import com.avail.interpreter.levelTwo.operation.L2_JUMP_IF_EQUALS_CONSTANT;
import com.avail.interpreter.levelTwo.operation.L2_JUMP_IF_KIND_OF_CONSTANT;
import com.avail.interpreter.levelTwo.register.L2BoxedRegister;
import com.avail.interpreter.levelTwo.register.L2FloatRegister;
import com.avail.interpreter.levelTwo.register.L2IntRegister;

import javax.annotation.Nullable;
import java.util.*;
import java.util.stream.Collectors;

import static com.avail.descriptor.AbstractEnumerationTypeDescriptor.enumerationWith;
import static com.avail.descriptor.AbstractEnumerationTypeDescriptor.instanceTypeOrMetaOn;
import static com.avail.descriptor.BottomTypeDescriptor.bottom;
import static com.avail.descriptor.InstanceMetaDescriptor.instanceMeta;
import static com.avail.descriptor.IntegerDescriptor.fromInt;
import static com.avail.descriptor.NilDescriptor.nil;
import static com.avail.descriptor.SetDescriptor.*;
import static com.avail.descriptor.TypeDescriptor.Types.ANY;
import static com.avail.descriptor.TypeDescriptor.Types.TOP;
import static com.avail.descriptor.TypeDescriptor.isProperSubtype;
import static com.avail.interpreter.levelTwo.operand.TypeRestriction.RestrictionFlagEncoding.*;
import static java.util.Collections.singleton;
import static java.util.Collections.unmodifiableSet;

/**
 * This mechanism describes a restriction of a type without saying what it's to
 * be applied to.
 *
 * <p>We capture an Avail {@link A_Type}, and an optional exactly known value,
 * so that we can represent something that avoids the metacovariance weakness of
 * metatypes.</p>
 *
 * <p>We also capture negative type and negative instance information, to
 * leverage more advantage from the failure paths of type tests like {@link
 * L2_JUMP_IF_KIND_OF_CONSTANT} and {@link L2_JUMP_IF_EQUALS_CONSTANT}.</p>
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class TypeRestriction
{
	/**
	 * The type of value that known to be in this register if this control
	 * flow path is taken.
	 */
	public final A_Type type;

	/**
	 * The exact value that is known to be in this register if this control flow
	 * path is taken, or {@code null} if unknown.
	 */
	public final @Nullable AvailObject constantOrNull;

	/**
	 * The set of types that are specifically excluded.  A value that satisfies
	 * one of these types does not satisfy this type restriction.  For the
	 * purpose of canonicalization, these types are all proper subtypes of the
	 * restriction's {@link #type}.  The {@link #constantOrNull}, if non-null,
	 * must not be a member of any of these types.
	 */
	public final Set<A_Type> excludedTypes;

	/**
	 * The set of values that are specifically excluded.  A value in this set
	 * does not satisfy this type restriction.  For the purpose of
	 * canonicalization, these values must all be members of the restriction's
	 * {@link #type}, and must not contain the {@link #constantOrNull}, if
	 * non-null.
	 */
	public final Set<A_BasicObject> excludedValues;

	/**
	 * An enumeration used to interpret the {@link #flags} of a {@link
	 * TypeRestriction}.  The sense of the flags is such that a bit-wise and can
	 * be used
	 */
	public enum RestrictionFlagEncoding
	{
		/** Whether the value is known to be immutable. */
		IMMUTABLE,

		/**
		 * Whether the value is available in a boxed form in some {@link
		 * L2BoxedRegister}.
		 */
		BOXED,

		/**
		 * Whether the value is available in an unboxed form in some {@link
		 * L2IntRegister}.
		 */
		UNBOXED_INT,

		/**
		 * Whether the value is available in an unboxed form in some {@link
		 * L2FloatRegister}.
		 */
		UNBOXED_FLOAT;

		/** A pre-computed bit mask for this flag. */
		private final int mask;

		RestrictionFlagEncoding ()
		{
			this.mask = 1 << ordinal();
		}

		/** Answer the pre-computed bit mask for this flag. */
		public int mask ()
		{
			return mask;
		}
	}

	/**
	 * The flags that track intangible or supplemental properties of a value.
	 * These bits are indexed via the ordinals of elements of {@link
	 * RestrictionFlagEncoding}.
	 *
	 * <p>The semantics are chosen so that the intersection of two {@code
	 * TypeRestriction}s produces the bit-wise "and" (&) of the inputs' flags,
	 * and the union uses the bit-wise "or" (|).</p>
	 */
	public final int flags;

	/** Answer whether the restricted value is known to be immutable. */
	public boolean isImmutable ()
	{
		return (flags & IMMUTABLE.mask()) != 0;
	}

	/**
	 * Answer whether the restricted value is known to be boxed in an {@link
	 * L2BoxedRegister}.
	 */
	public boolean isBoxed ()
	{
		return (flags & BOXED.mask()) != 0;
	}

	/**
	 * Answer whether the restricted value is known to be unboxed in an {@link
	 * L2IntRegister}.
	 */
	public boolean isUnboxedInt ()
	{
		return (flags & UNBOXED_INT.mask()) != 0;
	}

	/**
	 * Answer whether the restricted value is known to be unboxed in an {@link
	 * L2FloatRegister}.
	 */
	public boolean isUnboxedFloat ()
	{
		return (flags & UNBOXED_FLOAT.mask()) != 0;
	}

	/**
	 * Create a {@code TypeRestriction} from the already-canonicalized
	 * arguments.
	 *
	 * @param type
	 *        The Avail type that constrains some value somewhere.
	 * @param constantOrNull
	 *        Either {@code null} or the exact value that some value somewhere
	 *        must equal.
	 * @param excludedTypes
	 *        A set of {@link A_Type}s to consider excluded.
	 * @param excludedValues
	 *        A set of values to consider excluded.
	 * @param flags
	 *        The encoded {@link #flags} {@code int}.
	 */
	private TypeRestriction (
		final A_Type type,
		final @Nullable A_BasicObject constantOrNull,
		final Set<A_Type> excludedTypes,
		final Set<A_BasicObject> excludedValues,
		final int flags)
	{
		// Make the Avail objects immutable.  They'll be made Shared if they
		// survive the L2 translation and end up in an L2Chunk.
		this.type = type.makeImmutable();
		if (constantOrNull != null)
		{
			this.constantOrNull = constantOrNull.makeImmutable();
		}
		else
		{
			this.constantOrNull = null;
		}

		final int typesSize = excludedTypes.size();
		this.excludedTypes =
			typesSize == 0
				? Collections.emptySet()
				: typesSize == 1
					? singleton(excludedTypes.iterator().next())
					: unmodifiableSet(new HashSet<>(excludedTypes));

		final int constantsSize = excludedValues.size();
		this.excludedValues =
			constantsSize == 0
				? Collections.emptySet()
				: constantsSize == 1
					? singleton(excludedValues.iterator().next())
					: unmodifiableSet(new HashSet<>(excludedValues));
		this.flags = flags;
	}
	/**
	 * Create a {@code TypeRestriction} from the already-canonicalized
	 * arguments.
	 *
	 * @param type
	 *        The Avail type that constrains some value somewhere.
	 * @param constantOrNull
	 *        Either {@code null} or the exact value that some value somewhere
	 *        must equal.
	 * @param excludedTypes
	 *        A set of {@link A_Type}s to consider excluded.
	 * @param excludedValues
	 *        A set of values to consider excluded.
	 * @param isImmutable
	 *        Whether the value is known to be immutable.
	 * @param isBoxed
	 *        Whether this value is known to already reside in an {@link
	 *        L2BoxedRegister}.
	 * @param isUnboxedInt
	 *        Whether this value is known to already reside in an {@link
	 *        L2IntRegister}.
	 * @param isUnboxedFloat
	 *        Whether this value is known to already reside in an {@link
	 *        L2FloatRegister}.
	 */
	private TypeRestriction (
		final A_Type type,
		final @Nullable A_BasicObject constantOrNull,
		final Set<A_Type> excludedTypes,
		final Set<A_BasicObject> excludedValues,
		final boolean isImmutable,
		final boolean isBoxed,
		final boolean isUnboxedInt,
		final boolean isUnboxedFloat)
	{
		this(
			type,
			constantOrNull,
			excludedTypes,
			excludedValues,
			(isImmutable ? IMMUTABLE.mask() : 0)
				+ (isBoxed ? BOXED.mask() : 0)
				+ (isUnboxedInt ? UNBOXED_INT.mask() : 0)
				+ (isUnboxedFloat ? UNBOXED_FLOAT.mask() : 0));
	}

	/**
	 * The {@link TypeRestriction} for a register that holds {@link
	 * NilDescriptor#nil}.
	 *
	 * <p>It's marked as immutable because there is no way to create another
	 * {@link AvailObject} with a {@link NilDescriptor} as its descriptor.</p>
	 */
	public static final TypeRestriction nilRestriction =
		new TypeRestriction(
			TOP.o(),
			nil,
			singleton(ANY.o()),
			Collections.emptySet(),
			true,
			true,
			false,
			false);

	/**
	 * The {@link TypeRestriction} for a register that has any value whatsoever,
	 * including {@link NilDescriptor#nil}, and is not known to be immutable.
	 */
	public static final TypeRestriction topRestriction =
		new TypeRestriction(
			TOP.o(),
			null,
			Collections.emptySet(),
			Collections.emptySet(),
			false,
			true,
			false,
			false);

	/**
	 * The {@link TypeRestriction} for a register that has any value whatsoever,
	 * including {@link NilDescriptor#nil}, but is known to be immutable.
	 */
	public static final TypeRestriction topRestrictionImmutable =
		new TypeRestriction(
			TOP.o(),
			null,
			Collections.emptySet(),
			Collections.emptySet(),
			true,
			true,
			false,
			false);

	/**
	 * The {@link TypeRestriction} for a register that has any value whatsoever,
	 * excluding {@link NilDescriptor#nil}, but it's not known to be immutable.
	 */
	public static final TypeRestriction anyRestriction =
		new TypeRestriction(
			ANY.o(),
			null,
			Collections.emptySet(),
			Collections.emptySet(),
			false,
			true,
			false,
			false);

	/**
	 * The {@link TypeRestriction} for a register that has any value whatsoever,
	 * excluding {@link NilDescriptor#nil}, but it's known to be immutable.
	 */
	public static final TypeRestriction anyRestrictionImmutable =
		new TypeRestriction(
			ANY.o(),
			null,
			Collections.emptySet(),
			Collections.emptySet(),
			true,
			true,
			false,
			false);

	/**
	 * The {@link TypeRestriction} for a register that cannot hold any value.
	 * This can be useful for cleanly dealing with unreachable code.
	 *
	 * <p>It's marked as immutable because nothing can read from a register with
	 * this restriction.</p>
	 */
	public static final TypeRestriction bottomRestriction =
		new TypeRestriction(
			bottom(),
			null,
			Collections.emptySet(),
			Collections.emptySet(),
			true,
			false,
			false,
			false);

	/**
	 * The {@link TypeRestriction} for a register that can only hold the value
	 * bottom (i.e., the restriction type is bottom's type).  This is a sticky
	 * point in the type system, in that multiple otherwise unrelated type
	 * hierarchies share the (uninstantiable) type bottom as a descendant.
	 *
	 * <p>Note that this restriction is marked as immutable because there is no
	 * way to create another {@link AvailObject} whose descriptor is a {@link
	 * BottomTypeDescriptor}.</p>
	 */
	public static final TypeRestriction bottomTypeRestriction =
		new TypeRestriction(
			instanceMeta(bottom()),
			bottom(),
			Collections.emptySet(),
			Collections.emptySet(),
			true,
			true,
			false,
			false);

	/**
	 * Create or reuse an immutable {@code TypeRestriction} from the already
	 * mutually consistent, canonical arguments.
	 *
	 * @param givenType
	 *        The Avail type that constrains some value somewhere.
	 * @param givenConstantOrNull
	 *        Either {@code null} or the exact value that some value somewhere
	 *        must equal.
	 * @param givenExcludedTypes
	 *        A set of {@link A_Type}s to consider excluded.
	 * @param givenExcludedValues
	 *        A set of values to consider excluded.
	 * @param flags
	 *        The encoded {@link #flags} {@code int}.
	 * @return The new or existing canonical TypeRestriction.
	 */
	private static TypeRestriction fromCanonical (
		final A_Type givenType,
		final @Nullable A_BasicObject givenConstantOrNull,
		final Set<A_Type> givenExcludedTypes,
		final Set<A_BasicObject> givenExcludedValues,
		final int flags)
	{
		assert !givenExcludedTypes.contains(bottom());
		givenExcludedTypes.forEach(A_Type::makeImmutable);
		givenExcludedValues.forEach(A_BasicObject::makeImmutable);
		if (givenConstantOrNull != null)
		{
			// A constant was specified.  Use it if it satisfies the main type
			// constraint and isn't specifically excluded, otherwise use the
			// bottomRestriction, which is the impossible restriction.
			if (givenConstantOrNull.equalsNil())
			{
				return nilRestriction;
			}
			assert givenConstantOrNull.isInstanceOf(givenType);
			assert !givenExcludedValues.contains(givenConstantOrNull);
			assert givenExcludedTypes.stream().noneMatch(
				givenConstantOrNull::isInstanceOf);
			// No reason to exclude it, so use the constant.  We can safely
			// omit the excluded types and values as part of canonicalization.
			return new TypeRestriction(
				instanceTypeOrMetaOn(givenConstantOrNull),
				givenConstantOrNull,
				Collections.emptySet(),
				Collections.emptySet(),
				flags);
		}

		// Not a known constant.
		if (givenExcludedTypes.isEmpty() && givenExcludedValues.isEmpty())
		{
			if (givenType.equals(TOP.o()))
			{
				return (flags & IMMUTABLE.mask()) != 0
					? topRestrictionImmutable
					: topRestriction;
			}
			if (givenType.equals(ANY.o()))
			{
				return (flags & IMMUTABLE.mask()) != 0
					? anyRestrictionImmutable
					: anyRestriction;
			}
			if (givenType.instanceCount().equalsInt(1)
				&& !givenType.isInstanceMeta())
			{
				// This is a non-meta instance type, which should be treated as
				// a constant restriction.
				final AvailObject instance = givenType.instance();
				if (instance.isBottom())
				{
					// Special case: bottom's type has one instance, bottom.
					return bottomTypeRestriction;
				}
				return new TypeRestriction(
					givenType,
					instance,
					Collections.emptySet(),
					Collections.emptySet(),
					flags);
			}
		}
		return new TypeRestriction(
			givenType,
			null,
			givenExcludedTypes,
			givenExcludedValues,
			flags);
	}

	/**
	 * Create or reuse an immutable {@code TypeRestriction}, canonicalizing the
	 * arguments.
	 *
	 * @param type
	 *        The Avail type that constrains some value somewhere.
	 * @param constantOrNull
	 *        Either {@code null} or the exact value that some value somewhere
	 *        must equal.
	 * @param givenExcludedTypes
	 *        A set of {@link A_Type}s to consider excluded.
	 * @param givenExcludedValues
	 *        A set of values to consider excluded.
	 * @param isImmutable
	 *        Whether the value is known to be immutable.
	 * @param isBoxed
	 *        Whether this value is known to already reside in an {@link
	 *        L2BoxedRegister}.
	 * @param isUnboxedInt
	 *        Whether this value is known to already reside in an {@link
	 *        L2IntRegister}.
	 * @param isUnboxedFloat
	 *        Whether this value is known to already reside in an {@link
	 *        L2FloatRegister}.
	 * @return The new or existing canonical TypeRestriction.
	 */
	public static TypeRestriction restriction (
		final A_Type type,
		final @Nullable A_BasicObject constantOrNull,
		final Set<A_Type> givenExcludedTypes,
		final Set<A_BasicObject> givenExcludedValues,
		final boolean isImmutable,
		final boolean isBoxed,
		final boolean isUnboxedInt,
		final boolean isUnboxedFloat)
	{
		final int flags =
			(isImmutable ? IMMUTABLE.mask() : 0)
				+ (isBoxed ? BOXED.mask() : 0)
				+ (isUnboxedInt ? UNBOXED_INT.mask() : 0)
				+ (isUnboxedFloat ? UNBOXED_FLOAT.mask() : 0);
		return restriction(
			type,
			constantOrNull,
			givenExcludedTypes,
			givenExcludedValues,
			flags);
	}

	/**
	 * Create or reuse an immutable {@code TypeRestriction}, canonicalizing the
	 * arguments.
	 *
	 * @param type
	 *        The Avail type that constrains some value somewhere.
	 * @param constantOrNull
	 *        Either {@code null} or the exact value that some value somewhere
	 *        must equal.
	 * @param givenExcludedTypes
	 *        A set of {@link A_Type}s to consider excluded.
	 * @param givenExcludedValues
	 *        A set of values to consider excluded.
	 * @param flags
	 *        The encoded {@link #flags} {@code int}.
	 * @return The new or existing canonical TypeRestriction.
	 */
	public static TypeRestriction restriction (
		final A_Type type,
		final @Nullable A_BasicObject constantOrNull,
		final Set<A_Type> givenExcludedTypes,
		final Set<A_BasicObject> givenExcludedValues,
		final int flags)
	{
		if (constantOrNull == null
			&& type.isEnumeration()
			&& (!type.isInstanceMeta() || type.instance().isBottom()))
		{
			// No constant was specified, but the type is a non-meta enumeration
			// (or bottom's type, which has only one instance, bottom).  See if
			// excluding disallowed types and values happens to leave exactly
			// zero or one possibility.
			final Set<A_BasicObject> instances = toSet(type.instances());
			instances.removeAll(givenExcludedValues);
			instances.removeIf(
				instance -> givenExcludedTypes.stream().anyMatch(
					instance::isInstanceOf));
			switch (instances.size())
			{
				case 0:
				{
					return bottomRestriction;
				}
				case 1:
				{
					final A_BasicObject instance = instances.iterator().next();
					return fromCanonical(
						instanceTypeOrMetaOn(instance),
						instance,
						Collections.emptySet(),
						Collections.emptySet(),
						flags);
				}
				default:
				{
					// We've already applied the full effect of the excluded
					// types and values to the given type.
					return new TypeRestriction(
						enumerationWith(setFromCollection(instances)),
						null,
						Collections.emptySet(),
						Collections.emptySet(),
						flags);
				}
			}
		}
		if (constantOrNull != null)
		{
			// A constant was specified.  Use it if it satisfies the main type
			// constraint and isn't specifically excluded, otherwise use the
			// bottomRestriction, which is the impossible restriction.
			if (constantOrNull.equalsNil())
			{
				return nilRestriction;
			}
			if (!constantOrNull.isInstanceOf(type)
				|| givenExcludedValues.contains(constantOrNull))
			{
				return bottomRestriction;
			}
			for (final A_Type excludedType : givenExcludedTypes)
			{
				if (constantOrNull.isInstanceOf(excludedType))
				{
					return bottomRestriction;
				}
			}
			// No reason to exclude it, so use the constant.  We can safely
			// omit the excluded types and values as part of canonicalization.
			// Note that even though we make the constant immutable here, and
			// the value passing through registers at runtime will be equal to
			// it, it might be a different Java AvailObject that's still
			// mutable.
			constantOrNull.makeImmutable();
			return new TypeRestriction(
				instanceTypeOrMetaOn(constantOrNull),
				constantOrNull,
				Collections.emptySet(),
				Collections.emptySet(),
				flags);
		}

		// Are we excluding the base type?
		if (givenExcludedTypes.stream().anyMatch(type::isSubtypeOf))
		{
			return bottomRestriction;
		}

		// Eliminate excluded types that are proper subtypes of other excluded
		// types.  Note: this reduction is O(n^2) in the number of excluded
		// types.  We could use a LookupTree to speed this up.
		final Set<A_BasicObject> excludedValues =
			new HashSet<>(givenExcludedValues);
		final Set<A_Type> excludedTypes = givenExcludedTypes.stream()
			.map(type::typeIntersection)
			.collect(Collectors.toCollection(HashSet::new));
		excludedTypes.remove(bottom());
		final Iterator<A_Type> iterator = excludedTypes.iterator();
		iterator.forEachRemaining(
			t ->
			{
				if (t.isEnumeration() && !t.isInstanceMeta())
				{
					// Convert an excluded enumeration into individual excluded
					// values.
					for (final AvailObject v : t.instances())
					{
						excludedValues.add(v);
					}
				}
				else if (excludedTypes.stream().anyMatch(
					t2 -> isProperSubtype(t, t2)))
				{
					iterator.remove();
				}
			});

		// Eliminate excluded values that are already under an excluded type, or
		// are not under the given type.
		excludedValues.removeIf(
			v -> !v.isInstanceOf(type)
				|| excludedTypes.stream().anyMatch(v::isInstanceOf));

		if (type.equals(TOP.o())
			&& excludedTypes.isEmpty()
			&& excludedValues.isEmpty())
		{
			return topRestriction;
		}

		return fromCanonical(
			type,
			null,
			excludedTypes,
			excludedValues,
			flags);
	}

	/**
	 * Create or reuse a {@code TypeRestriction}.
	 *
	 * @param type
	 *        The Avail type that constrains some value somewhere.
	 * @param constantOrNull
	 *        Either {@code null} or the exact value that some value somewhere
	 *        must equal.
	 * @return The new or existing canonical TypeRestriction.
	 */
	public static TypeRestriction restriction (
		final A_Type type,
		final @Nullable A_BasicObject constantOrNull)
	{
		return restriction(
			type,
			constantOrNull,
			Collections.emptySet(),
			Collections.emptySet(),
			false,
			true,
			false,
			false);
	}

	/**
	 * Create or reuse a {@code TypeRestriction}, for which no constant
	 * information is provided (but might be deduced from the type).
	 *
	 * @param type
	 *        The Avail type that constrains some value somewhere.
	 * @param encoding
	 *        A {@link RestrictionFlagEncoding} indicating the type of register
	 *        that will hold this value ({@link RestrictionFlagEncoding#BOXED},
	 *        {@link RestrictionFlagEncoding#UNBOXED_INT}, or {@link
	 *        RestrictionFlagEncoding#UNBOXED_FLOAT}).
	 * @return The new or existing canonical TypeRestriction.
	 */
	public static TypeRestriction restrictionForType (
		final A_Type type,
		final RestrictionFlagEncoding encoding)
	{
		return restriction(
			type,
			null,
			Collections.emptySet(),
			Collections.emptySet(),
			encoding.mask());
	}

	/**
	 * Create or reuse a {@code TypeRestriction}, for which no constant
	 * information is provided (but might be deduced from the type).
	 *
	 * <p>If the requested register encoding is {@link
	 * RestrictionFlagEncoding#BOXED}, also flag the restriction as {@link
	 * RestrictionFlagEncoding#IMMUTABLE}.</p>
	 *
	 * @param constant
	 *        The sole Avail value that this restriction permits.
	 * @param encoding
	 *        A {@link RestrictionFlagEncoding} indicating the type of register
	 *        that will hold this value ({@link RestrictionFlagEncoding#BOXED},
	 *        {@link RestrictionFlagEncoding#UNBOXED_INT}, or {@link
	 *        RestrictionFlagEncoding#UNBOXED_FLOAT}).
	 * @return The new or existing canonical TypeRestriction.
	 */
	public static TypeRestriction restrictionForConstant (
		final A_BasicObject constant,
		final RestrictionFlagEncoding encoding)
	{
		assert encoding == BOXED
			|| encoding == UNBOXED_INT
			|| encoding == UNBOXED_FLOAT;
		constant.makeImmutable();
		return restriction(
			constant.equalsNil() ? TOP.o() : instanceTypeOrMetaOn(constant),
			constant,
			Collections.emptySet(),
			Collections.emptySet(),
			encoding.mask()
				| (encoding == BOXED ? IMMUTABLE.mask() : 0));
	}

	/**
	 * Create the union of the receiver and the other TypeRestriction.  This is
	 * the restriction that a register would have if it were assigned from one
	 * of two sources, each having one of the restrictions.
	 *
	 * @param other
	 *        The other {@code TypeRestriction} to combine with the receiver to
	 *        produce the output restriction.
	 * @return The new type restriction.
	 */
	public TypeRestriction union (final TypeRestriction other)
	{
		if (constantOrNull != null
			&& other.constantOrNull != null
			&& constantOrNull.equals(other.constantOrNull))
		{
			// The two restrictions are for the same constant value.
			return constantOrNull.equalsNil()
				? nilRestriction
				: restriction(
					instanceTypeOrMetaOn(constantOrNull), constantOrNull);
		}
		// We can only exclude types that were excluded in both restrictions.
		// Therefore find each intersection of an excluded type from the first
		// restriction and an excluded type from the second restriction.
		final Set<A_Type> mutualTypeIntersections = new HashSet<>();
		for (final A_Type t1 : excludedTypes)
		{
			for (final A_Type t2 : other.excludedTypes)
			{
				final A_Type intersection = t1.typeIntersection(t2);
				if (!intersection.isBottom())
				{
					mutualTypeIntersections.add(intersection);
				}
			}
		}
		// Figure out which excluded constants are also excluded in the other
		// restriction.
		final Set<A_BasicObject> newExcludedValues = new HashSet<>();
		for (final A_BasicObject value : excludedValues)
		{
			if (other.excludedValues.contains(value)
				|| other.excludedTypes.stream().anyMatch(value::isInstanceOf))
			{
				newExcludedValues.add(value);
			}
		}
		for (final A_BasicObject value : other.excludedValues)
		{
			if (excludedTypes.stream().anyMatch(value::isInstanceOf))
			{
				newExcludedValues.add(value);
			}
		}
		return restriction(
			type.typeUnion(other.type),
			null,
			mutualTypeIntersections,
			newExcludedValues,
			flags & other.flags);
	}

	/**
	 * Create the intersection of the receiver and the other TypeRestriction.
	 * This is the restriction that a register would have if it were already
	 * known to have the first restriction, and has been tested positively
	 * against the second restriction.
	 *
	 * @param other
	 *        The other {@code TypeRestriction} to combine with the receiver to
	 *        produce the intersected restriction.
	 * @return The new type restriction.
	 */
	public TypeRestriction intersection (final TypeRestriction other)
	{
		if (constantOrNull != null
			&& other.constantOrNull != null
			&& !constantOrNull.equals(other.constantOrNull))
		{
			// The restrictions are both constant, but disagree, so the
			// intersection is empty.
			return bottomRestriction;
		}
		final Set<A_Type> unionOfExcludedTypes = new HashSet<>(excludedTypes);
		unionOfExcludedTypes.addAll(other.excludedTypes);
		final Set<A_BasicObject> unionOfExcludedValues =
			new HashSet<>(excludedValues);
		unionOfExcludedValues.addAll(other.excludedValues);
		return restriction(
			type.typeIntersection(other.type),
			constantOrNull != null ? constantOrNull : other.constantOrNull,
			unionOfExcludedTypes,
			unionOfExcludedValues,
			flags | other.flags);
	}

	/**
	 * Create the intersection of the receiver with the given A_Type.  This is
	 * the restriction that a register would have if it were already known to
	 * satisfy the receiver restriction, and has been tested positively against
	 * the given type.
	 *
	 * @param typeToIntersect
	 *        The {@link A_Type} to combine with the receiver to produce an
	 *        intersected restriction.
	 * @return The new type restriction.
	 */
	public TypeRestriction intersectionWithType (
		final A_Type typeToIntersect)
	{
		return restriction(
			type.typeIntersection(typeToIntersect),
			constantOrNull,
			excludedTypes,
			excludedValues,
			flags);
	}

	/**
	 * Create the asymmetric difference of the receiver and the given A_Type.
	 * This is the restriction that a register would have if it held a value
	 * that satisfied the receiver, but failed a test against the given type.
	 *
	 * @param typeToExclude
	 *        The type to exclude from the receiver to create a new {@code
	 *        TypeRestriction}.
	 * @return The new type restriction.
	 */
	public TypeRestriction minusType (final A_Type typeToExclude)
	{
		final Set<A_Type> augmentedExcludedTypes = new HashSet<>(excludedTypes);
		augmentedExcludedTypes.add(typeToExclude);
		return restriction(
			type,
			constantOrNull,
			augmentedExcludedTypes,
			excludedValues,
			flags);
	}

	/**
	 * Create the asymmetric difference of the receiver and the given exact
	 * value.  This is the restriction that a register would have if it held a
	 * value that satisfied the receiver, but failed a value comparison against
	 * the given value.
	 *
	 * @param valueToExclude
	 *        The value to exclude from the receiver to create a new {@code
	 *        TypeRestriction}.
	 * @return The new type restriction.
	 */
	public TypeRestriction minusValue (final A_BasicObject valueToExclude)
	{
		final Set<A_BasicObject> augmentedExcludedValues =
			new HashSet<>(excludedValues);
		augmentedExcludedValues.add(valueToExclude);
		return restriction(
			type,
			constantOrNull,
			excludedTypes,
			augmentedExcludedValues,
			flags);
	}

	/**
	 * Answer true if this {@code TypeRestriction} contains every possible
	 * element of the given type.
	 *
	 * @param testType
	 *        The type to test is subsumed by this {@code TypeRestriction}.
	 * @return True iff every instance of {@code testType} is a member of this
	 *         {@code TypeRestriction}.
	 */
	public boolean containsEntireType (final A_Type testType)
	{
		if (constantOrNull != null)
		{
			if (constantOrNull.isType() && !constantOrNull.isBottom())
			{
				// The value is known to be a type other than bottom, so there
				// is no possible testType that could contain just this
				// constant as a member.
				return false;
			}
			return testType.equals(instanceTypeOrMetaOn(constantOrNull));
		}
		if (!testType.isSubtypeOf(type))
		{
			return false;
		}
		for (final A_Type excludedType : excludedTypes)
		{
			if (!excludedType.typeIntersection(testType).isBottom())
			{
				return false;
			}
		}
		for (final A_BasicObject excludedValue : excludedValues)
		{
			if (excludedValue.isInstanceOf(testType))
			{
				return false;
			}
		}
		return true;
	}

	/**
	 * Answer true if this {@code TypeRestriction} only contains values that
	 * are within the given testType.
	 *
	 * @param testType
	 *        The type to check for complete coverage of this {@code
	 *        TypeRestriction}.
	 * @return True iff every instance of this {@code TypeRestriction} is also
	 *         a member of {@code testType}.
	 */
	public boolean containedByType (final A_Type testType)
	{
		return type.isSubtypeOf(testType);
	}

	/**
	 * Answer true if this {@code TypeRestriction} contains any values in common
	 * with the given type.  It uses the {@link A_Type#isVacuousType()} test to
	 * determine whether any instances exist in the intersection.
	 *
	 * @param testType
	 *        The {@link A_Type} to intersect with this {@code TypeRestriction}
	 * @return True iff there are any instances in common between the supplied
	 *         type and this {@code TypeRestriction}.
	 */
	public boolean intersectsType (final A_Type testType)
	{
		if (constantOrNull != null)
		{
			return constantOrNull.isInstanceOf(testType);
		}
		if (testType.typeIntersection(type).isVacuousType())
		{
			return false;
		}
		for (final A_Type excludedType : excludedTypes)
		{
			if (testType.isSubtypeOf(excludedType))
			{
				return false;
			}
		}
		//noinspection RedundantIfStatement
		if (!excludedValues.isEmpty()
			&& testType.isEnumeration()
			&& !testType.isInstanceMeta()
			&& testType.instances().isSubsetOf(
				setFromCollection(excludedValues)))
		{
			// Every element of the testType enumeration has been explicitly
			// excluded from the restriction, so there's no intersection.
			return false;
		}
		return true;
	}

	/**
	 * Answer a restriction like the receiver, but with an additional flag set.
	 * If the flag is already set, answer the receiver.
	 *
	 * @param flagEncoding
	 *        The {@link RestrictionFlagEncoding} to set.
	 * @return The new {@code TypeRestriction}, or the receiver.
	 */
	public TypeRestriction withFlag (final RestrictionFlagEncoding flagEncoding)
	{
		if ((flags & flagEncoding.mask()) != 0)
		{
			// Flag is already set.
			return this;
		}
		return restriction(
			type,
			constantOrNull,
			excludedTypes,
			excludedValues,
			flags | flagEncoding.mask());
	}

	/**
	 * Answer a restriction like the receiver, but with a flag cleared.
	 * If the flag is already clear, answer the receiver.
	 *
	 * @param flagEncoding
	 *        The {@link RestrictionFlagEncoding} to clear.
	 * @return The new {@code TypeRestriction}, or the receiver.
	 */
	public TypeRestriction withoutFlag (
		final RestrictionFlagEncoding flagEncoding)
	{
		if ((flags & flagEncoding.mask()) == 0)
		{
			// Flag is already set.
			return this;
		}
		return restriction(
			type,
			constantOrNull,
			excludedTypes,
			excludedValues,
			flags & ~flagEncoding.mask());
	}

	/**
	 * If this restriction has only a finite set of possible values, and the
	 * number of such values is no more than the given maximum, answer an {@link
	 * A_Set} of them, otherwise {@code null}.
	 *
	 * @param maximumCount
	 *        The threshold above which {@code null} should be answered, even if
	 *        there is a finite set of potential values.
	 * @return The {@link A_Set} of possible instances or {@code null}.
	 */
	public @Nullable A_Set enumerationValuesOrNull (
		final int maximumCount)
	{
		if (maximumCount >= 0 && this == bottomRestriction)
		{
			return SetDescriptor.emptySet();
		}
		if (maximumCount >= 1 && constantOrNull != null)
		{
			return set(constantOrNull);
		}
		if (type.isEnumeration()
			&& !type.isInstanceMeta()
			&& type.instanceCount().lessOrEqual(fromInt(maximumCount)))
		{
			return type.instances();
		}
		return null;
	}

	@Override
	public boolean equals (final @Nullable Object other)
	{
		if (!(other instanceof TypeRestriction))
		{
			return false;
		}
		final TypeRestriction strongOther = (TypeRestriction) other;
		return this == other
			|| (type.equals(strongOther.type)
				&& Objects.equals(constantOrNull, strongOther.constantOrNull)
				&& excludedTypes.equals(strongOther.excludedTypes)
				&& excludedValues.equals(strongOther.excludedValues)
				&& flags == strongOther.flags);
	}

	@Override
	public int hashCode ()
	{
		return Objects.hash(
			type, constantOrNull, excludedTypes, excludedValues, flags);
	}

	/**
	 * Answer whether this {@code TypeRestriction} is a specialization of the
	 * given one.  That means every value that satisfies the receiver will also
	 * satisfy the argument.
	 *
	 * @param other
	 *        The other type restriction.
	 * @return Whether the receiver is a specialization of the argument.
	 */
	public boolean isStrongerThan (final TypeRestriction other)
	{
		if ((~flags & other.flags) != 0)
		{
			return false;
		}
		if (!type.isSubtypeOf(other.type))
		{
			return false;
		}
		// I have to exclude at least every type excluded by the argument.
		for (final A_Type otherExcludedType : other.excludedTypes)
		{
			if (excludedTypes.stream().noneMatch(
				otherExcludedType::isSubtypeOf))
			{
				return false;
			}
		}
		// I also have to exclude every value excluded by the argument.
		for (final A_BasicObject otherExcludedValue : other.excludedValues)
		{
			if (!excludedValues.contains(otherExcludedValue)
				&& excludedTypes.stream().noneMatch(
					otherExcludedValue::isInstanceOf))
			{
				return false;
			}
		}
		// Any additional exclusions that the receiver has are irrelevant, as
		// they only act to strengthen the restriction.
		return true;
	}

	/**
	 * Answer a {@link String}, possibly empty, suitable for displaying after
	 * a register, after a read/write of a register, or after any other place
	 * that this restriction might be applied.
	 *
	 * @return The {@link String} describing this restriction, if interesting.
	 */
	public String suffixString ()
	{
		final @Nullable AvailObject constant = constantOrNull;
		if (constant != null)
		{
			//noinspection DynamicRegexReplaceableByCompiledPattern
			return "=" + constant.typeTag().name().replace(
				"_TAG", "");
		}
		if (!type.equals(TOP.o()))
		{
			//noinspection DynamicRegexReplaceableByCompiledPattern
			return ":" + ((AvailObject) type).typeTag().name()
				.replace("_TAG", "");
		}
		return "";
	}

	@Override
	public String toString ()
	{
		final StringBuilder builder = new StringBuilder();
		builder.append("restriction(");
		if (constantOrNull != null)
		{
			builder.append("c=");
			builder.append(constantOrNull);
		}
		else
		{
			builder.append("t=");
			builder.append(type);
			if (!excludedTypes.isEmpty())
			{
				builder.append(", ex.t=");
				builder.append(excludedTypes);
			}
			if (!excludedValues.isEmpty())
			{
				builder.append(", ex.v=");
				builder.append(excludedValues);
			}
		}
		if (isImmutable())
		{
			builder.append(", imm");
		}
		if (isBoxed())
		{
			builder.append(", box");
		}
		if (isUnboxedInt())
		{
			builder.append(", int");
		}
		if (isUnboxedFloat())
		{
			builder.append(", float");
		}
		builder.append(")");
		return builder.toString();
	}
}
