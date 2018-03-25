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
import com.avail.descriptor.A_BasicObject;
import com.avail.descriptor.A_Type;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.NilDescriptor;
import com.avail.interpreter.levelTwo.operation.L2_JUMP_IF_EQUALS_CONSTANT;
import com.avail.interpreter.levelTwo.operation.L2_JUMP_IF_KIND_OF_CONSTANT;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.stream.Collectors;

import static com.avail.descriptor.AbstractEnumerationTypeDescriptor.enumerationWith;
import static com.avail.descriptor.AbstractEnumerationTypeDescriptor.instanceTypeOrMetaOn;
import static com.avail.descriptor.BottomTypeDescriptor.bottom;
import static com.avail.descriptor.InstanceMetaDescriptor.instanceMeta;
import static com.avail.descriptor.NilDescriptor.nil;
import static com.avail.descriptor.SetDescriptor.setFromCollection;
import static com.avail.descriptor.SetDescriptor.toSet;
import static com.avail.descriptor.TypeDescriptor.Types.TOP;
import static com.avail.descriptor.TypeDescriptor.isProperSubtype;
import static java.util.Collections.emptySet;

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
 * @param <T>
 *        The best type for exact values.
 */
public final class TypeRestriction<T extends A_BasicObject>
{
	/**
	 * The type of value that known to be in this register if this control
	 * flow path is taken.
	 */
	public final A_Type type;

	/**
	 * The exact value that is known to be in this register if this control
	 * flow path is taken, or {@code null} if unknown.
	 */
	public final @Nullable T constantOrNull;

	/**
	 * The set of types that are specifically excluded.  A value that satisfies
	 * one of these types does not satisfy this type restriction.  For the
	 * purpose of canonicalization, these types are all subtypes of the
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
	public final Set<T> excludedValues;

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
	 */
	@SuppressWarnings("unchecked")
	private TypeRestriction (
		final A_Type type,
		final @Nullable T constantOrNull,
		final Set<A_Type> excludedTypes,
		final Set<T> excludedValues)
	{
		// Make the Avail objects immutable.  They'll be made Shared if they
		// survive the L2 translation and end up in an L2Chunk.
		this.type = type.makeImmutable();
		this.constantOrNull = (constantOrNull != null)
			? (T) constantOrNull.makeImmutable()
			: null;

		final int typesSize = excludedTypes.size();
		this.excludedTypes =
			typesSize == 0
				? emptySet()
				: typesSize == 1
					? Collections.singleton(excludedTypes.iterator().next())
					: Collections.unmodifiableSet(
						new HashSet<>(excludedTypes));

		final int constantsSize = excludedTypes.size();
		this.excludedValues =
			constantsSize == 0
				? emptySet()
				: constantsSize == 1
					? Collections.singleton(excludedValues.iterator().next())
					: Collections.unmodifiableSet(
						new HashSet<>(excludedValues));
	}

	/**
	 * The {@link TypeRestriction} for a register that holds {@link
	 * NilDescriptor#nil}.
	 */
	private static final TypeRestriction<A_BasicObject> nilRestriction =
		new TypeRestriction<>(
			TOP.o(), nil, emptySet(), emptySet());

	/**
	 * The {@link TypeRestriction} for a register that has any value whatsoever,
	 * including {@link NilDescriptor#nil}.
	 */
	private static final TypeRestriction<A_BasicObject> topRestriction =
		new TypeRestriction<>(
			TOP.o(),
			null,
			emptySet(),
			emptySet());

	/**
	 * The {@link TypeRestriction} for a register that cannot hold any value.
	 * This can be useful for cleanly dealing with unreachable code.
	 */
	private static final TypeRestriction<A_BasicObject> bottomRestriction =
		new TypeRestriction<>(
			bottom(),
			null,
			emptySet(),
			emptySet());

	/**
	 * The {@link TypeRestriction} for a register that can only hold the value
	 * bottom (i.e., the restriction type is bottom's type).  This is a sticky
	 * point in the type system, in that multiple otherwise unrelated type
	 * hierarchies share the (uninstantiable) type bottom as a descendant.
	 */
	private static final TypeRestriction<A_BasicObject> bottomTypeRestriction =
		new TypeRestriction<>(
			instanceMeta(bottom()),
			bottom(),
			emptySet(),
			emptySet());

	/**
	 * Create or reuse an immutable {@code TypeRestriction} from the already
	 * mutually consistent, canonical arguments.
	 *
	 * @param <T>
	 *        The best type for exact values.
	 * @param givenType
	 *        The Avail type that constrains some value somewhere.
	 * @param givenConstantOrNull
	 *        Either {@code null} or the exact value that some value somewhere
	 *        must equal.
	 * @param givenExcludedTypes
	 *        A set of {@link A_Type}s to consider excluded.
	 * @param givenExcludedValues
	 *        A set of values to consider excluded.
	 * @return The new or existing canonical TypeRestriction.
	 */
	@SuppressWarnings("unchecked")
	private static <T extends A_BasicObject> TypeRestriction<T> fromCanonical (
			final A_Type givenType,
			final @Nullable T givenConstantOrNull,
			final Set<A_Type> givenExcludedTypes,
			final Set<T> givenExcludedValues)
	{
		if (givenConstantOrNull != null)
		{
			// A constant was specified.  Use it if it satisfies the main type
			// constraint and isn't specifically excluded, otherwise use the
			// bottomRestriction, which is the impossible restriction.
			if (givenConstantOrNull.equalsNil())
			{
				return (TypeRestriction<T>) nilRestriction;
			}
			assert givenConstantOrNull.isInstanceOf(givenType);
			assert !givenExcludedValues.contains(givenConstantOrNull);
			assert givenExcludedTypes.stream().noneMatch(
				givenConstantOrNull::isInstanceOf);
			// No reason to exclude it, so use the constant.  We can safely
			// omit the excluded types and values as part of canonicalization.
			return new TypeRestriction<>(
				instanceTypeOrMetaOn(givenConstantOrNull),
				givenConstantOrNull,
				emptySet(),
				emptySet());
		}

		// Not a known constant.
		if (givenType.equals(TOP.o()))
		{
			return (TypeRestriction<T>) topRestriction;
		}
		if (givenType.instanceCount().equalsInt(1))
		{
			final A_BasicObject instance = givenType.instance();
			if (instance.equals(bottom()))
			{
				// Special case: bottom's type has one instance, bottom.
				return (TypeRestriction<T>) bottomTypeRestriction;
			}
			// Ensure it's a meta, otherwise it should have been canonicalized
			// to provide a known constant.
			assert givenType.isInstanceMeta();
		}
		return new TypeRestriction<>(
			givenType,
			null,
			emptySet(),
			emptySet());
	}

	/**
	 * Create or reuse an immutable {@code TypeRestriction}, canonicalizing the
	 * arguments.
	 *
	 * @param <T>
	 *        The best type for exact values.
	 * @param type
	 *        The Avail type that constrains some value somewhere.
	 * @param constantOrNull
	 *        Either {@code null} or the exact value that some value somewhere
	 *        must equal.
	 * @param givenExcludedTypes
	 *        A set of {@link A_Type}s to consider excluded.
	 * @param givenExcludedValues
	 *        A set of values to consider excluded.
	 * @return The new or existing canonical TypeRestriction.
	 */
	@SuppressWarnings("unchecked")
	public static <T extends A_BasicObject> TypeRestriction<T> restriction (
		final A_Type type,
		final @Nullable T constantOrNull,
		final Set<A_Type> givenExcludedTypes,
		final Set<T> givenExcludedValues)
	{
		if (constantOrNull == null
			&& type.isEnumeration()
			&& (!type.isInstanceMeta() || type.instance().isBottom()))
		{
			// No constant was specified, but the type is a non-meta enumeration
			// (or bottom's type, which has only one instance, bottom).  See if
			// excluding disallowed types and values happens to leave exactly
			// zero or one possibility.
			final Set<T> instances = toSet(type.instances());
			instances.removeAll(givenExcludedValues);
			instances.removeIf(
				instance -> givenExcludedTypes.stream().anyMatch(
					instance::isInstanceOf));
			switch (instances.size())
			{
				case 0:
				{
					return (TypeRestriction<T>) bottomRestriction;
				}
				case 1:
				{
					final T instance = instances.iterator().next();
					return fromCanonical(
						instanceTypeOrMetaOn(instance),
						instance,
						emptySet(),
						emptySet());
				}
				default:
				{
					// We've already applied the full effect of the excluded
					// types and values to the given type.
					return new TypeRestriction<>(
						enumerationWith(setFromCollection(instances)),
						null,
						emptySet(),
						emptySet());
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
				return (TypeRestriction<T>) nilRestriction;
			}
			if (!constantOrNull.isInstanceOf(type)
				|| givenExcludedValues.contains(constantOrNull))
			{
				return (TypeRestriction<T>) bottomRestriction;
			}
			for (final A_Type excludedType : givenExcludedTypes)
			{
				if (constantOrNull.isInstanceOf(excludedType))
				{
					return (TypeRestriction<T>) bottomRestriction;
				}
			}
			// No reason to exclude it, so use the constant.  We can safely
			// omit the excluded types and values as part of canonicalization.
			return new TypeRestriction<>(
				instanceTypeOrMetaOn(constantOrNull),
				constantOrNull,
				emptySet(),
				emptySet());
		}

		// Are we excluding the base type?
		if (givenExcludedTypes.stream().anyMatch(type::isSubtypeOf))
		{
			return (TypeRestriction<T>) bottomRestriction;
		}

		// Eliminate excluded types that are proper subtypes of other excluded
		// types.  Note: this reduction is O(n^2) in the number of excluded
		// types.  We could use a LookupTree to speed this up.
		final Set<T> excludedValues = new HashSet<>(givenExcludedValues);
		final Set<A_Type> excludedTypes = givenExcludedTypes.stream()
			.map(type::typeIntersection)
			.collect(Collectors.toCollection(HashSet::new));
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
						excludedValues.add((T) v);
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
			return (TypeRestriction<T>) topRestriction;
		}

		return fromCanonical(type, null, excludedTypes, excludedValues);
	}

	/**
	 * Create or reuse an immutable {@code TypeRestriction}.
	 *
	 * @param <T>
	 *        The best type for exact values.
	 * @param type
	 *        The Avail type that constrains some value somewhere.
	 * @param constantOrNull
	 *        Either {@code null} or the exact value that some value somewhere
	 *        must equal.
	 * @return The new or existing canonical TypeRestriction.
	 */
	public static <T extends A_BasicObject> TypeRestriction<T> restriction (
		final A_Type type,
		final @Nullable T constantOrNull)
	{
		return restriction(type, constantOrNull, emptySet(), emptySet());
	}

	/**
	 * Create or reuse an immutable {@code TypeRestriction}, for which no
	 * constant information is provided (but might be deduced from the type).
	 *
	 * @param <T>
	 *        The best type for exact values.
	 * @param type
	 *        The Avail type that constrains some value somewhere.
	 * @return The new or existing canonical TypeRestriction.
	 */
	public static <T extends A_BasicObject> TypeRestriction<T> restriction (
		final A_Type type)
	{
		return restriction(type, null, emptySet(), emptySet());
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
	@SuppressWarnings("unchecked")
	public TypeRestriction<T> union (final TypeRestriction<T> other)
	{
		if (constantOrNull != null
			&& other.constantOrNull != null
			&& constantOrNull.equals(other.constantOrNull))
		{
			// The two restrictions are for the same constant value.
			return constantOrNull.equalsNil()
				? (TypeRestriction<T>) nilRestriction
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
		final Set<T> newExcludedValues = new HashSet<>();
		for (final T value : excludedValues)
		{
			if (other.excludedValues.contains(value)
				|| other.excludedTypes.stream().anyMatch(value::isInstanceOf))
			{
				newExcludedValues.add(value);
			}
		}
		for (final T value : other.excludedValues)
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
			newExcludedValues);
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
	@SuppressWarnings("unchecked")
	public TypeRestriction<T> intersection (final TypeRestriction<T> other)
	{
		if (constantOrNull != null
			&& other.constantOrNull != null
			&& !constantOrNull.equals(other.constantOrNull))
		{
			// The restrictions are both constant, but disagree, so the
			// intersection is empty.
			return (TypeRestriction<T>) bottomRestriction;
		}
		final Set<A_Type> unionOfExcludedTypes = new HashSet<>(excludedTypes);
		unionOfExcludedTypes.addAll(other.excludedTypes);
		final Set<T> unionOfExcludedValues = new HashSet<>(excludedValues);
		unionOfExcludedValues.addAll(other.excludedValues);
		return restriction(
			type.typeIntersection(other.type),
			constantOrNull != null ? constantOrNull : other.constantOrNull,
			unionOfExcludedTypes,
			unionOfExcludedValues);
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
	@SuppressWarnings("unchecked")
	public TypeRestriction<T> intersectionWithType (
		final A_Type typeToIntersect)
	{
		return restriction(
			type.typeIntersection(typeToIntersect),
			constantOrNull,
			excludedTypes,
			excludedValues);
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
	public TypeRestriction<T> minusType (final A_Type typeToExclude)
	{
		final Set<A_Type> augmentedExcludedTypes = new HashSet<>(excludedTypes);
		augmentedExcludedTypes.add(typeToExclude);
		return restriction(
			type,
			constantOrNull,
			augmentedExcludedTypes,
			excludedValues);
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
	public TypeRestriction<T> minusValue (final T valueToExclude)
	{
		final Set<T> augmentedExcludedValues = new HashSet<>(excludedValues);
		augmentedExcludedValues.add(valueToExclude);
		return restriction(
			type,
			constantOrNull,
			excludedTypes,
			augmentedExcludedValues);
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
	public boolean isStrongerThan (final TypeRestriction<T> other)
	{
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
		for (final T otherExcludedValue : other.excludedValues)
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
		final @Nullable AvailObject constant =
			AvailObject.class.cast(constantOrNull);
		if (constant != null)
		{
			//noinspection DynamicRegexReplaceableByCompiledPattern
			return "=" + constant.typeTag().name().replace(
				"_TAG", "");
		}
		if (!type.equals(TOP.o()))
		{
			//noinspection DynamicRegexReplaceableByCompiledPattern
			return ":" + AvailObject.class.cast(type).typeTag().name()
				.replace("_TAG", "");
		}
		return "";
	}
}
