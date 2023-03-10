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

import avail.anvil.AvailWorkbench
import java.awt.Frame
import java.awt.GraphicsDevice
import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import java.util.prefs.Preferences
import kotlin.math.max

/**
 * Information about a window layout.
 */
open class LayoutConfiguration protected constructor ()
{
	/** The preferred location and size of the window, if specified. */
	internal var placement: Rectangle? = null

	/**
	 * The platform-independent information about whether the window is
	 * maximized or minimized (iconified).
	 */
	internal var extendedState: Int = Frame.NORMAL

	open fun parseInput (input: String)
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

			extendedState = runCatching {
				Integer.parseInt(substrings[6])
			}.getOrDefault(Frame.NORMAL)
		}
	}

	fun saveWindowPosition()
	{
		// TODO this needs to change to write to the local preferences file
		val preferences =
			placementPreferencesNodeForScreenNames(allScreenNames())
		preferences.put(placementLeafKeyString, stringToStore())
	}

	/**
	 * Answer a string representation of this configuration that is suitable
	 * for being stored and restored via the [LayoutConfiguration]
	 * constructor that accepts a [String].
	 *
	 * The layout should be fairly stable to avoid treating older versions
	 * as malformed.  To that end, we use a simple list of strings, adding
	 * entries for new purposes to the end, and never removing or changing
	 * the meaning of existing entries.
	 *
	 * Do not remove or repurpose old entries:
	 *  * **0-3**: x,y,w,h of workbench window.
	 *  * **6-9**: Reserved for compatibility.  These must be blank when
	 *    writing.  They used to contain the rectangle for a module editor
	 *    window, but that capability no longer exists.
	 *
	 * @return A string.
	 */
	internal open fun stringToStore(): String
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
		strings[4] = extendedState.toString()

		return strings.joinToString(",")
	}

	companion object
	{

		/**
		 * The prefix string for resources related to the workbench.
		 */
		private const val resourcePrefix = "/workbench/"

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
		private val basePreferences: Preferences =
			Preferences.userNodeForPackage(AvailWorkbench::class.java)

		/** The key under which to organize all placement information. */
		private const val placementByMonitorNamesString =
			"placementByMonitorNames"

		/** The leaf key under which to store a single window placement. */
		const val placementLeafKeyString = "placement"

		/**
		 * Create a [LayoutConfiguration] from the provided input string.
		 *
		 * @param input
		 *   A string in some encoding compatible with that produced by
		 *   [stringToStore].
		 */
		fun from (input: String = ""): LayoutConfiguration =
			LayoutConfiguration().apply {
				parseInput(input)
			}

		/**
		 * Figure out how to initially lay out the frame, based on previously
		 * saved preference information.
		 *
		 * @return The initial [LayoutConfiguration].
		 */
		val initialConfiguration: LayoutConfiguration
			get() = LayoutConfiguration()

		/**
		 * Answer a [List] of [Rectangle]s corresponding with the physical
		 * monitors into which [Frame]s may be positioned.
		 *
		 * @return The list of rectangles to which physical screens are mapped.
		 */
		fun allScreenNames(): List<String> =
			GraphicsEnvironment
				.getLocalGraphicsEnvironment()
				.screenDevices.map(GraphicsDevice::getIDstring)

		/**
		 * Answer the [Preferences] node responsible for holding the default
		 * window position and size for the current monitor configuration.
		 *
		 * @param screenNames
		 *   The list of id [String]s of all physical screens.
		 * @return The `Preferences` node in which placement information for
		 *   the current monitor configuration can be stored and retrieved.
		 */
		fun placementPreferencesNodeForScreenNames(screenNames: List<String>)
			: Preferences
		{
			val allNamesString = StringBuilder()
			screenNames.forEach { name ->
				allNamesString.append(name)
				allNamesString.append(";")
			}
			return basePreferences.node(
				"$placementByMonitorNamesString/$allNamesString")
		}

	}
}
