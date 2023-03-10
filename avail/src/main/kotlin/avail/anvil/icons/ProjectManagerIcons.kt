/*
 * ProjectManagerIcons.kt
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

package avail.anvil.icons

import avail.compiler.SideEffectKind
import javax.swing.ImageIcon

/**
 * Manages the structure view icons associated with the various
 * [SideEffectKind]s.
 *
 * @author Richard Arriaga
 */
object ProjectManagerIcons
{
	/**
	 * Return a suitable icon to display a filled yellow star.
	 *
	 * @param lineHeight
	 *   The desired icon height in pixels.
	 * @return The icon.
	 */
	fun yellowStarFilled (lineHeight: Int): ImageIcon =
		icon(lineHeight, "yellow-star-filled.png")

	/**
	 * Return a suitable icon to display an unfilled yellow star.
	 *
	 * @param lineHeight
	 *   The desired icon height in pixels.
	 * @return The icon.
	 */
	fun yellowStarUnfilled (lineHeight: Int): ImageIcon =
		icon(lineHeight, "yellow-star-empty.png")

	/**
	 * Return a suitable icon to display a circle with an x in it.
	 *
	 * @param lineHeight
	 *   The desired icon height in pixels.
	 * @return The icon.
	 */
	fun cancelFilled (lineHeight: Int): ImageIcon =
		icon(lineHeight, "cancel-filled.png")

	/**
	 * Return a suitable icon to a refresh symbol.
	 *
	 * @param lineHeight
	 *   The desired icon height in pixels.
	 * @return The icon.
	 */
	fun refresh (lineHeight: Int): ImageIcon =
		icon(lineHeight, "refresh.png")

	/**
	 * Return a suitable icon to a down arrow.
	 *
	 * @param lineHeight
	 *   The desired icon height in pixels.
	 * @return The icon.
	 */
	fun arrowDropDown (lineHeight: Int): ImageIcon =
		icon(lineHeight, "arrow-drop-down.png")

	/**
	 * Return a suitable icon to an up arrow.
	 *
	 * @param lineHeight
	 *   The desired icon height in pixels.
	 * @return The icon.
	 */
	fun arrowDropUp (lineHeight: Int): ImageIcon =
		icon(lineHeight, "arrow-drop-up.png")

	/**
	 * Return a suitable icon to display for this instance with the given line
	 * height.
	 *
	 * @param lineHeight
	 *   The desired icon height in pixels.
	 * @return The icon.
	 */
	fun icon(lineHeight: Int, resourceName: String): ImageIcon =
		cachedScaledIcons[BasicIconKey(resourceName, lineHeight)]

	/**
	 * A static cache of scaled icons.
	 */
	private val cachedScaledIcons =
		ImageIconCache<BasicIconKey>(
			"/project-manager/",
			ProjectManagerIcons::class.java)
}
