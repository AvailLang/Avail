/*
 * CovariantParameterization.kt
 * Copyright © 1993-2020, The Avail Foundation, LLC.
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
package com.avail.descriptor.types

/**
 * A dimension along which a [phrase type][PhraseTypeDescriptor] can be
 * covariantly specialized.
 *
 * @property name
 *   The name to print when describing this parameterization of some phrase
 *   type.
 * @property mostGeneralType
 *   The most general type this parameterization can have. If a phrase type has
 *   this type for this parameterization, the parameterization won't be shown at
 *   all in the print representation of the type.
 *
 * @constructor
 * Construct a new [CovariantParameterization], but don't set its index yet.
 *
 * @param name
 *   The name of the covariant specialization.
 * @param mostGeneralType
 *   The the type that any specific phrase type's covariant specialization type
 *   is constrained to.
 */
@Suppress("unused")
class CovariantParameterization internal constructor(
	val name: String,
	val mostGeneralType: A_Type)
{
	/**
	 * The one-based index of this parameterization within
	 */
	var index = -1

	companion object
	{
		/**
		 * Static factory method – Create a covariant parameterization.  Don't
		 * set its index yet.
		 *
		 * @param name
		 *   The name of the covariant specialization.
		 * @param mostGeneralType
		 *   The the type that any specific phrase type's covariant
		 *   specialization type is constrained to.
		 * @return
		 *   The new covariant specialization.
		 */
		fun co(
			name: String,
			mostGeneralType: A_Type): CovariantParameterization =
				CovariantParameterization(name, mostGeneralType)

		/**
		 * Assemble an array of [CovariantParameterization]s.  This is a
		 * convenience method for assembling an array using varargs.
		 *
		 * @param array
		 *   An array of covariant specializations.
		 * @return
		 *   The same array.
		 */
		fun array(vararg array: CovariantParameterization)
			: Array<CovariantParameterization> = arrayOf(*array)
	}
}
