/*
 * UsageTypeIcons.kt
 * Copyright © 1993-2023, The Avail Foundation, LLC.
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

import avail.persistence.cache.record.NamesIndex.UsageType
import javax.swing.ImageIcon

/**
 * The [Pair] of [String] [Int] used to retrieve a usage type icon in the
 * senders view.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @property usageType
 *   The associated [UsageType].
 * @property resourceName
 *   The associated file name of the resource.
 * @property scaledHeight
 *   The height to which to scale the image.
 */
data class UsageTypeIconKey constructor(
	val usageType: UsageType,
	override val scaledHeight: Int
): IconKey
{
	override val resourceName: String
		get() = usageType.iconFileName
}

/**
 * Manages the senders view icons associated with the various [UsageType]s.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object UsageTypeIcons
{
	/**
	 * Return a suitable icon to display for this instance with the given line
	 * height.
	 *
	 * @param lineHeight
	 *   The desired icon height in pixels.
	 * @param usageType
	 *   The [usage&#32;type][UsageType].
	 * @return
	 *   The icon.
	 */
	fun icon(lineHeight: Int, usageType: UsageType): ImageIcon =
		cachedScaledIcons[UsageTypeIconKey(usageType, lineHeight)]

	/**
	 * A static cache of scaled icons, organized [UsageType] and line height.
	 */
	private val cachedScaledIcons =
		ImageIconCache<UsageTypeIconKey>(
			"/workbench/usage-icons/",
			UsageTypeIcons::class.java)
}
