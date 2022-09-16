/*
 * LayoutConfiguration.kt
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

package avail.anvil.window

import avail.builder.ModuleRoots
import avail.anvil.AvailWorkbench
import java.awt.Frame
import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import java.util.prefs.Preferences
import kotlin.math.max
import kotlin.math.min

/**
 * The [LayoutConfiguration] for the [AvailWorkbench].
 */
class AvailWorkbenchLayoutConfiguration private constructor ()
	: LayoutConfiguration()
{
	/**
	 * The width of the left region of the builder frame in pixels, if
	 * specified
	 */
	internal var leftSectionWidth: Int? = null

	/**
	 * The proportion, if specified, as a float between `0.0` and `1.0` of
	 * the height of the top left module region in relative proportional to
	 * the height of the entire builder frame.
	 */
	internal var moduleVerticalProportion: Double? = null

	override fun parseInput(input: String)
	{
		if (input.isNotEmpty())
		{
			val substrings = input.split(',')
			kotlin.runCatching {
				val (x, y, w, h) =
					substrings.slice(0 .. 3).map(Integer::parseInt)
				val rectangle = Rectangle(x, y, max(50, w), max(50, h))
				// Ignore placement if it's entirely off-screen or
				// zero-thickness.
				val intersectsAny = GraphicsEnvironment
					.getLocalGraphicsEnvironment()
					.screenDevices.any { device ->
						device.configurations.any {config ->
							config.bounds.intersects(rectangle)}}
				if (intersectsAny) placement = rectangle
			}

			leftSectionWidth = runCatching {
				max(0, Integer.parseInt(substrings[4]))
			}.getOrDefault(200)

			moduleVerticalProportion = runCatching {
				java.lang.Double.parseDouble(substrings[5])
			}.getOrDefault(0.5)

			extendedState = runCatching {
				Integer.parseInt(substrings[6])
			}.getOrDefault(Frame.NORMAL)
		}
	}

	/**
	 * Answer this configuration's recommended width in pixels for the left
	 * region of the window, supplying a suitable default if necessary.
	 *
	 * @return The recommended width of the left part.
	 */
	internal fun leftSectionWidth(): Int
	{
		val w = leftSectionWidth
		return w ?: 200
	}

	/**
	 * Add this configuration's recommended proportion of height of the
	 * modules list versus the entire frame's height, supplying a default
	 * if necessary.  It must be between 0.0 and 1.0 inclusive.
	 *
	 * @return The vertical proportion of the modules area.
	 */
	internal fun moduleVerticalProportion(): Double
	{
		val h = moduleVerticalProportion
		return if (h !== null) max(0.0, min(1.0, h)) else 0.5
	}

	/**
	 * Answer a string representation of this configuration that is suitable
	 * for being stored and restored via the [AvailWorkbenchLayoutConfiguration]
	 * constructor that accepts a [String].
	 *
	 * The layout should be fairly stable to avoid treating older versions
	 * as malformed.  To that end, we use a simple list of strings, adding
	 * entries for new purposes to the end, and never removing or changing
	 * the meaning of existing entries.
	 *
	 * Do not remove or repurpose old entries:
	 *  * **0-3**: x,y,w,h of workbench window.
	 *  * **4**: width in pixels of left section of workbench (modules and
	 *    entry points.
	 *  * **5**: vertical proportion between modules area and entry points
	 *    area.
	 *  * **6-9**: Reserved for compatibility.  These must be blank when
	 *    writing.  They used to contain the rectangle for a module editor
	 *    window, but that capability no longer exists.
	 *
	 * @return A string.
	 */
	override fun stringToStore(): String
	{
		val strings = Array(10) { "" }
		val p = placement
		if (p !== null)
		{
			strings[0] = p.x.toString()
			strings[1] = p.y.toString()
			strings[2] = p.width.toString()
			strings[3] = p.height.toString()
		}
		strings[4] = (leftSectionWidth ?: 200).toString()
		strings[5] = (moduleVerticalProportion ?: 0.5).toString()
		strings[6] = extendedState.toString()

		return strings.joinToString(",")
	}

	companion object {

		/**
		 * The prefix string for resources related to the workbench.
		 */
		private const val resourcePrefix = "/resources/workbench/"

		/**
		 * Answer a properly prefixed [String] for accessing the resource having
		 * the given local name.
		 *
		 * @param localResourceName
		 *   The unqualified resource name.
		 * @return The fully qualified resource name.
		 */
		fun resource(localResourceName: String): String =
			resourcePrefix + localResourceName

		/** The user-specific [Preferences] for this application to use. */
		val basePreferences: Preferences =
			Preferences.userNodeForPackage(AvailWorkbench::class.java)

		/** The key under which to store the [ModuleRoots]. */
		const val moduleRootsKeyString = "module roots"

		/** The subkey that holds a root's source directory name. */
		const val moduleRootsSourceSubkeyString = "source"

		/** The key under which to store the module rename rules. */
		const val moduleRenamesKeyString = "module renames"

		/** The subkey that holds a rename rule's source module name. */
		const val moduleRenameSourceSubkeyString = "source"

		/** The subkey that holds a rename rule's replacement module name. */
		const val moduleRenameTargetSubkeyString = "target"

		/**
		 * Create a [AvailWorkbenchLayoutConfiguration] from the provided input
		 * string.
		 *
		 * @param input
		 *   A string in some encoding compatible with that produced by
		 *   [stringToStore].
		 */
		fun from (input: String = ""): AvailWorkbenchLayoutConfiguration =
			AvailWorkbenchLayoutConfiguration().apply {
				parseInput(input)
			}
	}
}
