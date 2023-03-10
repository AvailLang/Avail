/*
 * SideEffectIcons.kt
 * Copyright © 1993-2022, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 *  * Neither the name of the copyright holder nor the names of the contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
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

package avail.anvil.icons.structure

import avail.anvil.icons.IconKey
import avail.anvil.icons.ImageIconCache
import avail.compiler.SideEffectKind
import javax.swing.ImageIcon

/**
 * The [Pair] of [String] [Int] used to retrieve a side effect icon in the
 * structure view.
 *
 * @author Richard Arriaga
 *
 * @property sideEffectKind
 *   The associated [SideEffectKind].
 * @property resourceName
 *   The associated file name of the resource.
 * @property scaledHeight
 *   The height to which to scale the image.
 */
data class SideEffectIconKey constructor(
	val sideEffectKind: SideEffectKind,
	override val resourceName: String,
	override val scaledHeight: Int
): IconKey

/**
 * Manages the structure view icons associated with the various
 * [SideEffectKind]s.
 *
 * @author Richard Arriaga
 */
object SideEffectIcons
{
	/**
	 * The resource name for [SideEffectKind.ABSTRACT_METHOD_DEFINITION_KIND].
	 */
	private const val ABSTRACT_METHOD ="AbstractMethod-Dark.png"

	/**
	 * The resource name for  [SideEffectKind.ATOM_DEFINITION_KIND].
	 */
	private const val ATOM = "Atom-Dark.png"

	/**
	 * The resource name for  [SideEffectKind.MODULE_CONSTANT_KIND].
	 */
	private const val MODULE_CONSTANT = "Constant-Dark.png"

	/**
	 * The resource name for  [SideEffectKind.FORWARD_METHOD_DEFINITION_KIND].
	 */
	private const val FORWARD_METHOD = "ForwardMethod-Dark.png"

	/**
	 * The resource name for  [SideEffectKind.GRAMMATICAL_RESTRICTION_KIND].
	 */
	private const val GRAMMATICAL_RESTRICTION = "GrammaticalRestriction-DarkLight.png"

	/**
	 * The resource name for  [SideEffectKind.LEXER_KIND].
	 */
	private const val LEXER = "Lexer-Dark.png"

	/**
	 * The resource name for  [SideEffectKind.MACRO_DEFINITION_KIND].
	 */
	private const val MACRO = "Macro-Dark.png"

	/**
	 * The resource name for  [SideEffectKind.METHOD_DEFINITION_KIND].
	 */
	private const val METHOD = "Method-Dark.png"

	/**
	 * The resource name for  [SideEffectKind.METHOD_DEFINITION_KIND].
	 */
	private const val SEALED_METHOD = "Seal-DarkLight.png"

	/**
	 * The resource name for  [SideEffectKind.SEMANTIC_RESTRICTION_KIND].
	 */
	private const val SEMANTIC_RESTRICTION ="SemanticRestriction-Dark.png"

	/**
	 * The resource name for  [SideEffectKind.MODULE_VARIABLE_KIND].
	 */
	private const val MODULE_VARIABLE = "Variable-Dark.png"

	/**
	 * Return a suitable icon to display for this instance with the given line
	 * height.
	 *
	 * @param lineHeight
	 *   The desired icon height in pixels.
	 * @return The icon.
	 */
	fun icon(lineHeight: Int, sideEffectKind: SideEffectKind): ImageIcon =
		cachedScaledIcons[SideEffectIconKey(
			sideEffectKind, fileName(sideEffectKind), lineHeight)]

	/**
	 * Answer the [String] file name for the icon for the associated
	 * [SideEffectKind].
	 *
	 * @param sideEffectKind
	 *   The [SideEffectKind] to retrieve the file name for.
	 * @return
	 *   The associated file name.
	 */
	private fun fileName (sideEffectKind: SideEffectKind): String =
		when (sideEffectKind)
		{
			SideEffectKind.ATOM_DEFINITION_KIND -> ATOM
			SideEffectKind.METHOD_DEFINITION_KIND -> METHOD
			SideEffectKind.ABSTRACT_METHOD_DEFINITION_KIND -> ABSTRACT_METHOD
			SideEffectKind.FORWARD_METHOD_DEFINITION_KIND -> FORWARD_METHOD
			SideEffectKind.MACRO_DEFINITION_KIND -> MACRO
			SideEffectKind.SEMANTIC_RESTRICTION_KIND -> SEMANTIC_RESTRICTION
			SideEffectKind.GRAMMATICAL_RESTRICTION_KIND ->
				GRAMMATICAL_RESTRICTION
			SideEffectKind.SEAL_KIND -> SEALED_METHOD
			SideEffectKind.LEXER_KIND -> LEXER
			SideEffectKind.MODULE_CONSTANT_KIND -> MODULE_CONSTANT
			SideEffectKind.MODULE_VARIABLE_KIND -> MODULE_VARIABLE
		}

	/**
	 * A static cache of scaled icons, organized
	 * [SideEffectKind] and line height.
	 */
	private val cachedScaledIcons =
		ImageIconCache<SideEffectIconKey>(
			"/workbench/structure-icons/",
			SideEffectIcons::class.java)
}
