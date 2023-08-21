 /*
 * ImageIconCache.kt
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

import org.availlang.cache.LRUCache
import java.awt.Component
import java.awt.Graphics
import java.awt.Image
import javax.swing.Icon
import javax.swing.ImageIcon
import kotlin.math.max

 /**
 * The interface for the minimum required state for an [ImageIconCache] key.
 *
 * @author Richard Arriaga
 */
interface IconKey
{
	/**
	 * The base name of the resource; e.g. "my-image.png".
	 */
	val resourceName: String

	/**
	 * The height to scale the image.
	 */
	val scaledHeight: Int
}

/**
 * The basic [IconKey].
 */
data class BasicIconKey constructor(
	override val resourceName: String,
	override val scaledHeight: Int
): IconKey

/**
 * A wrapper of an [LRUCache] keyed by an [IconKey] for storing scaled
 * [ImageIcon]s organized by scaled height.
 *
 * @author Richard Arriaga
 *
 * @param Key
 *   The type of [IconKey] that represents the cache key.
 *
 * @constructor
 * Construct an [ImageIconCache].
 *
 * @param resourcePath
 *   The path to the resource location
 */
class ImageIconCache<Key: IconKey> constructor(
	resourcePath: String,
	resourceClass: Class<*>,
	softCapacity: Int = 100,
	strongCapacity: Int = 20)
{
	/**
	 * The backing [LRUCache].
	 */
	private val cache = LRUCache<Key, ImageIcon>(
		softCapacity,
		strongCapacity,
		{ key ->
			val path = "$resourcePath${key.resourceName}"
			val resource = resourceClass.getResource(path)
			val originalIcon = ImageIcon(resource)
			val scaled = originalIcon.image.getScaledInstance(
				-1, key.scaledHeight, Image.SCALE_SMOOTH)
			ImageIcon(scaled, key.resourceName)
		})

	/**
	 * Answer the [ImageIcon] for the associated key.
	 *
	 * @param k
	 *   The [Key] to look up.
	 * @return
	 *   The requested [ImageIcon].
	 */
	operator fun get(k: Key): ImageIcon =
		try
		{
			val icon = cache[k]
			// Yeah, as if. I have seen this return null, recently even
			// (2023.01.26).
			@Suppress("SENSELESS_COMPARISON")
			check(icon !== null)
			icon
		}
		catch (e: Throwable)
		{
			throw RuntimeException("Could not locate ${k.resourceName}", e)
		}
}

class CompoundIcon constructor(
	private val icons: List<Icon>,
	private val xGap: Int
): Icon
{
	val height: Int = icons.maxOfOrNull { it.iconHeight } ?: 0

	val width: Int = max(0, icons.sumOf { it.iconWidth + xGap } - xGap)

	override fun getIconWidth(): Int = width

	override fun getIconHeight(): Int = height

	override fun paintIcon(c: Component, g: Graphics, x: Int, y: Int)
	{
		var dx = 0
		icons.forEach { icon ->
			// Center the icons vertically.
			val dy = (height - icon.iconHeight) shr 1
			icon.paintIcon(c, g, x + dx, y + dy)
			dx += icon.iconWidth + xGap
		}
	 }
 }
