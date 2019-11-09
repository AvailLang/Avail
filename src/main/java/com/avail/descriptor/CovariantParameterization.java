/*
 * CovariantParameterization.java
 * Copyright © 1993-2019, The Avail Foundation, LLC.
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

/**
 * A dimension along which a {@link PhraseTypeDescriptor phrase type} can
 * be covariantly specialized.
 */
public final class CovariantParameterization
{
	/**
	 * The name to print when describing this parameterization of some
	 * phrase type.
	 */
	final String name;

	/**
	 * The most general type this parameterization can have.
	 *
	 * <p>If a phrase type has this type for this parameterization, the
	 * parameterization won't be shown at all in the print representation
	 * of the type.
	 */
	final A_Type mostGeneralType;

	/**
	 * The one-based index of this parameterization within
	 */
	int index = -1;

	/**
	 * Construct a new {@link CovariantParameterization}, but don't set its
	 * index yet.
	 *
	 * @param name
	 *        The name of the covariant specialization.
	 * @param mostGeneralType
	 *        The the type that any specific phrase type's covariant
	 *        specialization type is constrained to.
	 */
	CovariantParameterization (
		final String name,
		final A_Type mostGeneralType)
	{
		this.name = name;
		this.mostGeneralType = mostGeneralType;
	}

	/**
	 * Static factory method – Create a covariant parameterization.  Don't
	 * set its index yet.
	 *
	 * @param name
	 *        The name of the covariant specialization.
	 * @param mostGeneralType
	 *        The the type that any specific phrase type's covariant
	 *        specialization type is constrained to.
	 * @return The new covariant specialization.
	 */
	static CovariantParameterization co(
		final String name,
		final A_Type mostGeneralType)
	{
		return new CovariantParameterization(name, mostGeneralType);
	}

	/**
	 * Assemble an array of {@link CovariantParameterization}s.  This is a
	 * convenience method for assembling an array using varargs.
	 *
	 * @param array An array of covariant specializations.
	 * @return The same array.
	 */
	static CovariantParameterization[] array(
		final CovariantParameterization... array)
	{
		return array;
	}
}
