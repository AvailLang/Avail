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
	override val scaledHeight: Int
): IconKey
{
	override val resourceName: String
		get() = sideEffectKind.iconFileName
}

/**
 * Manages the structure view icons associated with the various
 * [SideEffectKind]s.
 *
 * @author Richard Arriaga
 */
object SideEffectIcons
{
	/**
	 * Return a suitable icon to display for this instance with the given line
	 * height.
	 *
	 * @param lineHeight
	 *   The desired icon height in pixels.
	 * @return The icon.
	 */
	fun icon(lineHeight: Int, sideEffectKind: SideEffectKind): ImageIcon =
		cachedScaledIcons[SideEffectIconKey(sideEffectKind, lineHeight)]

	/**
	 * A static cache of scaled icons, organized
	 * [SideEffectKind] and line height.
	 */
	private val cachedScaledIcons =
		ImageIconCache<SideEffectIconKey>(
			"/workbench/structure-icons/",
			SideEffectIcons::class.java)
}
