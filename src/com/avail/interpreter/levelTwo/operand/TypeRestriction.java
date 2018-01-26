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
import com.avail.descriptor.A_Set;
import com.avail.descriptor.A_Type;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.NilDescriptor;

import javax.annotation.Nullable;

import static com.avail.descriptor.AbstractEnumerationTypeDescriptor.enumerationWith;
import static com.avail.descriptor.AbstractEnumerationTypeDescriptor.instanceTypeOrMetaOn;
import static com.avail.descriptor.BottomTypeDescriptor.bottom;
import static com.avail.descriptor.NilDescriptor.nil;
import static com.avail.descriptor.SetDescriptor.emptySet;
import static com.avail.descriptor.TypeDescriptor.Types.TOP;

/**
 * This mechanism describes a restriction of a type without saying what it's to
 * be applied to.
 *
 * <p>We capture an Avail {@link A_Type}, and an optional exactly known value,
 * so that we can represent something that avoids the metacovariance weakness of
 * metatypes.</p>
 *
 * <p>Eventually we may also capture negative type information (e.g., "x isn't
 * an integer or a tuple").</p>
 */
public final class TypeRestriction
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
	public final @Nullable A_BasicObject constantOrNull;

	/**
	 * Create a {@code TypeRestriction}.
	 *
	 * @param type
	 *        The Avail type that constrains some value somewhere.
	 * @param constantOrNull
	 *        Either {@code null} or the exact value that some value somewhere
	 *        must equal.
	 */
	private TypeRestriction (
		final A_Type type,
		final @Nullable A_BasicObject constantOrNull)
	{
		// Make the Avail objects immutable.  They'll be made Shared if they
		// survive the L2 translation and end up in an L2Chunk.
		this.type = type.makeImmutable();
		this.constantOrNull = constantOrNull == null
			? null
			: constantOrNull.makeImmutable();
	}

	/**
	 * The {@link TypeRestriction} for a register that holds {@link
	 * NilDescriptor#nil}.
	 */
	private static final TypeRestriction nilRestriction =
		new TypeRestriction(TOP.o(), nil);

	/**
	 * The {@link TypeRestriction} for a register that any value whatsoever,
	 * including {@link NilDescriptor#nil}.
	 */
	private static final TypeRestriction topRestriction =
		new TypeRestriction(TOP.o(), null);

	/**
	 * The {@link TypeRestriction} for a register that cannot hold any value.
	 * This can be useful for cleanly dealing with unreachable code.
	 */
	private static final TypeRestriction bottomRestriction =
		new TypeRestriction(bottom(), null);

	/**
	 * The {@link TypeRestriction} for a register that can only hold the value
	 * bottom (i.e., the restriction type is bottom's type).  This is a sticky
	 * point in the type system, in that multiple otherwise unrelated type
	 * hierarchies share the (uninstantiable) type bottom as a descendant.
	 */
	private static final TypeRestriction bottomTypeRestriction =
		new TypeRestriction(instanceTypeOrMetaOn(bottom()), bottom());

	/**
	 * Create or reuse an immutable {@code TypeRestriction}.
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
		if (constantOrNull != null)
		{
			if (constantOrNull.equalsNil())
			{
				return nilRestriction;
			}
			if (!constantOrNull.isInstanceOf(type))
			{
				return bottomRestriction;
			}
			return new TypeRestriction(
				instanceTypeOrMetaOn(constantOrNull), constantOrNull);
		}
		if (type.instanceCount().equalsInt(1))
		{
			final A_BasicObject instance = type.instance();
			if (instance.equals(bottom()))
			{
				// Special case: bottom's type has one instance, bottom.
				return bottomTypeRestriction;
			}
			if (!type.isInstanceMeta())
			{
				// Its instance is a non-type, so it's exact.
				return new TypeRestriction(type, instance);
			}
		}
		if (type.equals(TOP.o()))
		{
			return topRestriction;
		}
		return new TypeRestriction(type, null);
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
		else
		{
			return restriction(type.typeUnion(other.type), null);
		}
	}

	/**
	 * Create the intersection of the receiver and the other TypeRestriction.
	 * This is the restriction that a register would have if it were already
	 * known to have the first type, and has been tested positively against the
	 * second type.
	 *
	 * @param other
	 *        The other {@code TypeRestriction} to combine with the receiver to
	 *        produce the output restriction.
	 * @return The new type restriction.
	 */
	public TypeRestriction intersection (final TypeRestriction other)
	{
		if (constantOrNull != null)
		{
			if (other.constantOrNull != null
				&& !constantOrNull.equals(other.constantOrNull))
			{
				// The two constants conflict.  Code using this register should
				// not be reachable, but allow it to be generated for now.
				return bottomRestriction;
			}
			// Rely on normalization to reject the constant if the type
			// intersection is vacuous (bottom).
			return restriction(
				type.typeIntersection(other.type),
				constantOrNull);
		}
		// The receiver wasn't a single constant.  Just do an intersection and
		// let normalization deal with the special cases.
		return restriction(
			type.typeIntersection(other.type),
			other.constantOrNull);
	}

	/**
	 * Create the asymmetric difference of the receiver and the other
	 * TypeRestriction.  This is the restriction that a register would have if
	 * it held a value typed by the receiver, but failed a test against the
	 * other type (and/or constant).
	 *
	 * @param other
	 *        The other {@code TypeRestriction} to combine with the receiver to
	 *        produce the output restriction.
	 * @return The new type restriction.
	 */
	public TypeRestriction minus (final TypeRestriction other)
	{
		if (type.isSubtypeOf(other.type))
		{
			// Exclude everything.
			return bottomRestriction;
		}
		if (constantOrNull != null)
		{
			// The constant was not excluded by the subtype test above.
			return restriction(type, constantOrNull);
		}
		if (type.isEnumeration() && !type.isInstanceMeta())
		{
			// Filter the enumeration set to exclude elements satisfying the
			// other type.
			A_Set filteredSet = emptySet();
			for (final AvailObject instance : type.instances())
			{
				if (!instance.isInstanceOf(other.type))
				{
					// This instance isn't excluded by the other type.
					filteredSet = filteredSet.setWithElementCanDestroy(
						instance, true);
				}
			}
			// If it reduced to one element, let normalization pull out the
			// constant.  It's known here not to be a meta.
			return restriction(enumerationWith(filteredSet), null);
		}
		// Ignore the type subtraction, at least until we've implemented
		// negative type restrictions.
		return restriction(type, null);
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
			return "=" + constant.typeTag().name().replace("_TAG", "");
		}
		if (!type.equals(TOP.o()))
		{
			return ":" + AvailObject.class.cast(type).typeTag().name()
				.replace("_TAG", "");
		}
		return "";
	}
}
