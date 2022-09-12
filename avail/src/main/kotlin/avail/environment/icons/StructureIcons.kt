/*
 * WorkbenchIcons.kt
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

package avail.environment.icons

import avail.compiler.SideEffectKind
import org.availlang.cache.LRUCache
import java.awt.Image
import javax.swing.ImageIcon

/**
 * The [Pair] of [String] [Int] used to retrieve a structure icon.
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
data class StructureIconKey constructor(
	val sideEffectKind: SideEffectKind,
	val resourceName: String,
	val scaledHeight: Int)

/**
 * Manages the structure view icons associated with the various
 * [SideEffectKind]s.
 *
 * @author Richard Arriaga
 */
object StructureIcons
{
	/**
	 * The resource name for [SideEffectKind.ABSTRACT_METHOD_DEFINITION_KIND].
	 */
	private const val ABSTRACT_METHOD ="abstract-method.png"

	/**
	 * The resource name for  [SideEffectKind.ATOM_DEFINITION_KIND].
	 */
	private const val ATOM = "atom.png"

	/**
	 * The resource name for  [SideEffectKind.MODULE_CONSTANT_KIND].
	 */
	private const val MODULE_CONSTANT = "constant.png"

	/**
	 * The resource name for  [SideEffectKind.FORWARD_METHOD_DEFINITION_KIND].
	 */
	private const val FORWARD_METHOD = "forward-method.png"

	/**
	 * The resource name for  [SideEffectKind.GRAMMATICAL_RESTRICTION_KIND].
	 */
	private const val GRAMMATICAL_RESTRICTION = "grammatical-restriction.png"

	/**
	 * The resource name for  [SideEffectKind.LEXER_KIND].
	 */
	private const val LEXER = "lexer.png"

	/**
	 * The resource name for  [SideEffectKind.MACRO_DEFINITION_KIND].
	 */
	private const val MACRO = "macro.png"

	/**
	 * The resource name for  [SideEffectKind.METHOD_DEFINITION_KIND].
	 */
	private const val METHOD = "method.png"

	/**
	 * The resource name for  [SideEffectKind.METHOD_DEFINITION_KIND].
	 */
	private const val SEALED_METHOD = "method-sealed.png"

	/**
	 * The resource name for  [SideEffectKind.SEMANTIC_RESTRICTION_KIND].
	 */
	private const val SEMANTIC_RESTRICTION ="semantic-restriction.png"

	/**
	 * The resource name for  [SideEffectKind.MODULE_VARIABLE_KIND].
	 */
	private const val MODULE_VARIABLE = "variable.png"

	/**
	 * Return a suitable icon to display for this instance with the given line
	 * height.
	 *
	 * @param lineHeight
	 *   The desired icon height in pixels.
	 * @return The icon.
	 */
	fun icon(lineHeight: Int, sideEffectKind: SideEffectKind): ImageIcon =
		cachedScaledIcons[StructureIconKey(
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
	 * A static cache of scaled icons, organized by node class and line
	 * height.
	 */
	private val cachedScaledIcons = LRUCache<StructureIconKey, ImageIcon>(
		100, 20,
		{ key ->
			val path = "/resources/workbench/structure-icons/${key.resourceName}"
			val thisClass = StructureIcons::class.java
			val resource = thisClass.getResource(path)
			val originalIcon = ImageIcon(resource)
			val scaled = originalIcon.image.getScaledInstance(
				-1, key.scaledHeight, Image.SCALE_SMOOTH)
			ImageIcon(scaled, key.resourceName)
		})
}
