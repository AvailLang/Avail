/*
 * SideEffectKind.kt
 * Copyright © 1993-2022, The Avail Foundation, LLC.
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

package avail.compiler

/**
 * The kinds of manifest entries that can be recorded.  If this changes in a
 * way other than adding enum values at the end, you must rebuild your
 * repository files.
 */
enum class SideEffectKind
{
	/** An atom has been defined.  The summaryText will be the atom name. */
	ATOM_DEFINITION_KIND,

	/** The summaryText will be the method name. */
	METHOD_DEFINITION_KIND,

	/** The summaryText will be the method name. */
	ABSTRACT_METHOD_DEFINITION_KIND,

	/** The summaryText will be the method name. */
	FORWARD_METHOD_DEFINITION_KIND,

	/** The summaryText will be the macro name. */
	MACRO_DEFINITION_KIND,

	/** The summaryText will be the method name being restricted. */
	SEMANTIC_RESTRICTION_KIND,

	/**
	 * The summaryText will be the method name being restricted.
	 */
	GRAMMATICAL_RESTRICTION_KIND,

	/**
	 * The summaryText will be the method name being sealed.
	 */
	SEAL_KIND,

	/** The summaryText will be the lexer's name. */
	LEXER_KIND,

	/** The summaryText will be the module constant's name. */
	MODULE_CONSTANT_KIND,

	/** The summaryText will be the module variable's name. */
	MODULE_VARIABLE_KIND;

	companion object
	{
		/** All of the [Kind]s. */
		val all = values().toList()
	}
}
