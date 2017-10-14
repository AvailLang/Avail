/**
 * TypeRestriction.java
 * Copyright © 1993-2017, The Avail Foundation, LLC.
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

import javax.annotation.Nullable;

import static com.avail.descriptor.AbstractEnumerationTypeDescriptor
	.instanceTypeOrMetaOn;

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
	public TypeRestriction (
		final A_Type type,
		final @Nullable A_BasicObject constantOrNull)
	{
		if (constantOrNull != null)
		{
			// Narrow the type to be as strong as possible.
			assert constantOrNull.isInstanceOf(type)
				: "TypeRestriction is vacuous";
			this.type = instanceTypeOrMetaOn(constantOrNull);
			this.constantOrNull = constantOrNull;
		}
		else if (type.instanceCount().equalsInt(1) && type.isInstanceMeta())
		{
			// Extract the sole possible value.
			this.type = type;
			this.constantOrNull = type.instance();
		}
		else
		{
			this.type = type;
			this.constantOrNull = null;
		}

		// Make the Avail objects immutable.  They'll be made Shared if they
		// survive the L2 translation and end up in an L2Chunk.
		this.type.makeImmutable();
		if (this.constantOrNull != null)
		{
			this.constantOrNull.makeImmutable();
		}
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
		final @Nullable A_BasicObject newConstant =
			(constantOrNull == null || other.constantOrNull == null)
				? null
				: constantOrNull.equals(other.constantOrNull)
					? constantOrNull
					: null;
		return new TypeRestriction(
			type.typeUnion(other.type),
			newConstant);
	}
}
