/*
 * StylingGroup.kt
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

@file:Suppress("DuplicatedCode", "MemberVisibilityCanBePrivate")

package org.availlang.artifact.environment.project

import org.availlang.artifact.environment.location.ProjectConfig
import org.availlang.json.JSONFriendly
import org.availlang.json.JSONObject
import org.availlang.json.JSONWriter
import org.availlang.json.jsonObject
import java.awt.Color
import java.io.File

/**
 * The palette of symbolic styles. Both registries contain the same keys, which
 * are the symbolic names of the styles.
 *
 * @property lightColors
 *   The registry of colors for light mode, as a map from symbolic names to
 *   RGBA colors.
 * @property darkColors
 *   The register of colors for dark mode, as a map from symbolic names to RGBA
 *   colors.
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
class Palette constructor(
	val lightColors: Map<String, Color> = mutableMapOf(),
	val darkColors: Map<String, Color> = mutableMapOf()
): JSONFriendly
{
	init
	{
		assert(lightColors.keys == darkColors.keys) {
			"light and dark maps have different keys"
		}
	}

	/**
	 * Whether the receiver is empty, i.e., binds no symbolic names to
	 * [colors][Color].
	 */
	val isEmpty get() = lightColors.isEmpty()

	/**
	 * Whether the receiver is not empty, i.e., binds at least one symbolic name
	 * to [colors][Color].
	 */
	@Suppress("unused")
	inline val isNotEmpty get() = !isEmpty

	override fun writeTo(writer: JSONWriter) = writer.writeObject {
		// Each map has an identical key set, so the choice of lightColors is
		// arbitrary.
		lightColors.keys.forEach {
			at(it) { write(hex(it)) }
		}
	}

	/**
	 * Create a new [Palette] by merging this [Palette] onto the provided
	 * [Palette] overriding the provided [Palette]'s colors with this
	 * [Palette]'s colors if duplicates exist.
	 */
	fun mergeOnto (palette: Palette): Palette =
		Palette(
			palette.lightColors + lightColors,
			palette.darkColors + darkColors)

	/**
	 * Construct a padded hexadecimal rendition of the light/dark color scheme,
	 * for the specified symbolic name.
	 *
	 * @param name
	 *   The symbolic name.
	 * @return
	 *   The requested encoding, as `#rrggbbaa/rrggbbaa`, where the first group
	 *   of hexadecimal digits represents the light mode color and the second
	 *   the dark mode color.
	 */
	@Suppress("SpellCheckingInspection")
	private fun hex(name: String) = buildString(18) {
		append('#')
		lightColors[name]!!.apply {
			append("%02X".format(red))
			append("%02X".format(green))
			append("%02X".format(blue))
			append("%02X".format(alpha))
		}
		append('/')
		darkColors[name]!!.apply {
			append("%02X".format(red))
			append("%02X".format(green))
			append("%02X".format(blue))
			append("%02X".format(alpha))
		}
	}

	companion object
	{
		/** The canonical empty [Palette]. */
		val empty get() = Palette()

		/**
		 * Deserialize a [Palette] from the specified [JSONObject].
		 *
		 * @param data
		 *   The serialized data.
		 * @return
		 *   The deserialized [Palette].
		 */
		fun from(data: JSONObject): Palette
		{
			val lightColors = mutableMapOf<String, Color>()
			val darkColors = mutableMapOf<String, Color>()
			data.forEach { (name, value) ->
				val colors = decodeColors(value.string)
				lightColors[name] = colors.first
				darkColors[name] = colors.second
			}
			return Palette(lightColors, darkColors)
		}

		/**
		 * Construct a light/dark [Color] pair from the specified hexadecimal
		 * string.
		 *
		 * @param hex
		 *   The hexadecimal string, encoded as
		 *   `#rrggbb(?:aa)(?:/rrggbb(?:aa))?`, where the first group of
		 *   hexadecimal digits represents the light mode color and the second
		 *   the dark mode color.
		 * @return
		 *   The decoded color pair.
		 */
		private fun decodeColors(hex: String): Pair<Color, Color>
		{
			// The first character is the octothorp, so ignore it. Split at the
			// solidus, if there is one. If no solidus occurs, then use the same
			// color for both light and dark mode.
			val values = hex.substring(1).split('/')
			val light = decodeColor(values[0])
			val dark = values.getOrNull(1)?.let { decodeColor(it) } ?: light
			return light to dark
		}

		/**
		 * Decode a [Color] from the specified hexadecimal string.
		 *
		 * @param hex
		 *   The hexadecimal string, without decoration, with an optional alpha
		 *   channel at the end.
		 * @return
		 *   The decoded color.
		 */
		private fun decodeColor(hex: String): Color
		{
			val r = hex.substring(0 .. 1).toInt(16)
			val g = hex.substring(2 .. 3).toInt(16)
			val b = hex.substring(4 .. 5).toInt(16)
			val a =
				if (hex.length == 8) hex.substring(6 .. 7).toInt(16)
				else 255
			return Color(r, g, b, a)
		}
	}
}

/**
 * The style attributes to apply to a styling pattern.
 *
 * @property fontFamily
 *   The font family for text rendition.
 * @property foreground
 *   The symbolic name of the foreground color, relative to some [Palette].
 * @property background
 *   The symbolic name of the background color, relative to some [Palette].
 * @property bold
 *   Whether to give bold weight to the rendered text.
 * @property italic
 *   Whether to give italic style to the rendered text.
 * @property underline
 *   Whether to give underline decoration to the rendered text.
 * @property superscript
 *   Whether to give superscript position to the rendered text.
 * @property subscript
 *   Whether to give subscript position to the rendered text.
 * @property strikethrough
 *   Whether to give strikethrough decoration to the rendered text.
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
data class StyleAttributes constructor(
	val fontFamily: String? = null,
	val foreground: String? = null,
	val background: String? = null,
	val bold: Boolean? = null,
	val italic: Boolean? = null,
	val underline: Boolean? = null,
	val superscript: Boolean? = null,
	val subscript: Boolean? = null,
	val strikethrough: Boolean? = null
): JSONFriendly
{
	constructor(data: JSONObject): this(
		fontFamily = data.getStringOrNull(StyleAttributes::fontFamily.name),
		foreground = data.getStringOrNull(StyleAttributes::foreground.name),
		background = data.getStringOrNull(StyleAttributes::background.name),
		bold = data.getBooleanOrNull(StyleAttributes::bold.name),
		italic = data.getBooleanOrNull(StyleAttributes::italic.name),
		underline = data.getBooleanOrNull(StyleAttributes::underline.name),
		superscript = data.getBooleanOrNull(StyleAttributes::superscript.name),
		subscript = data.getBooleanOrNull(StyleAttributes::subscript.name),
		strikethrough = data.getBooleanOrNull(
			StyleAttributes::strikethrough.name)
	)
	{
		// No implementation required.
	}

	override fun writeTo(writer: JSONWriter)
	{
		writer.writeObject {
			fontFamily?.let { at(::fontFamily.name) { write(fontFamily) } }
			foreground?.let { at(::foreground.name) { write(foreground) } }
			background?.let { at(::background.name) { write(background) } }
			bold?.let { at(::bold.name) { write(bold) } }
			italic?.let { at(::italic.name) { write(italic) } }
			underline?.let { at(::underline.name) { write(underline) } }
			superscript?.let { at(::superscript.name) { write(superscript) } }
			subscript?.let { at(::subscript.name) { write(subscript) } }
			strikethrough?.let {
				at(::strikethrough.name) { write(strikethrough) }
			}
		}
	}
}

/**
 * A pairing of a stylesheet and its associated [Palette].
 *
 * @author Richard Arriaga
 */
class StylingSelection constructor(
	var palette: Palette = Palette.empty,
	val stylesheet: MutableMap<String, StyleAttributes> = mutableMapOf())
{
	/**
	 * Apply this [StylingSelection] onto the provided [StylingSelection].
	 *
	 * @param selection
	 *   The [StylingSelection] to merge onto.
	 * @return
	 *   The merged [StylingSelection].
	 */
	fun mergeOnto (selection: StylingSelection): StylingSelection =
		selection.apply {
			palette = this@StylingSelection.palette.mergeOnto(palette)
			stylesheet.putAll(this@StylingSelection.stylesheet)
		}

	companion object
	{
		/**
		 * A new empty [StylingSelection] with each call.
		 */
		val empty get() = StylingSelection(Palette.empty, mutableMapOf())

		/**
		 * Compose together the ordered list of [StylingSelection]s,
		 * [merging][mergeOnto] from left to write with the last element in the
		 * list having the final override capability.
		 *
		 * @param orderedStyles
		 *   The list of [StylingSelection]s to compose together.
		 * @return
		 *   The resulting composed [StylingSelection].
		 */
		@Suppress("unused")
		fun compose (orderedStyles: List<StylingSelection>): StylingSelection =
			orderedStyles.fold(empty) { merged, next -> next.mergeOnto(merged) }
	}
}

/**
 * The stylesheet and accompanying [Palette] options.
 *
 * @author Richard Arriaga
 */
class StylingGroup constructor(): JSONFriendly
{
	/**
	 * The default stylesheet for this root. Symbolic names are resolved against
	 * the accompanying [Palette]s.
	 */
	val stylesheet: Map<String, StyleAttributes> = mutableMapOf()

	/**
	 * The map from the palette name to a [Palette] for the accompanying
	 * [stylesheet].
	 */
	val palettes = mutableMapOf<String, Palette>()

	/**
	 * Update this [StylingGroup] using the provided [StylingGroup]. The
	 * provided [StylingGroup] will overwrite duplicates in this [StylingGroup].
	 *
	 * @param other
	 *   The [StylingGroup] to update from.
	 */
	fun updateFrom (other: StylingGroup)
	{
		(stylesheet as MutableMap<String, StyleAttributes>)
			.putAll(other.stylesheet)
		palettes.putAll(other.palettes)
	}

	/**
	 * Answer the [StylingSelection] for the given [Palette] name.
	 *
	 * @param paletteName
	 *   The name of the [Palette] to choose from [palettes] to use as the
	 *   [StylingSelection.palette].
	 * @return
	 *   The composed [StylingSelection].
	 */
	@Suppress("unused")
	fun selection (paletteName: String): StylingSelection =
		when
		{
			palettes.isEmpty() -> StylingSelection()
			paletteName.isBlank() ->
				StylingSelection(
					palettes.values.first().let {
						Palette(
							it.lightColors.toMutableMap(),
							it.darkColors.toMutableMap())
					},
					stylesheet.toMutableMap())

			else ->
				StylingSelection(
					palettes[paletteName] ?: palettes.values.first().let {
						Palette(
							it.lightColors.toMutableMap(),
							it.darkColors.toMutableMap())
					},
					stylesheet.toMutableMap())
		}

	override fun writeTo(writer: JSONWriter)
	{
		writer.writeObject {
			at(::stylesheet.name) {
				writeObject {
					stylesheet.forEach { (rule, attributes) ->
						at(rule) { write(attributes) }
					}
				}
			}
			at(::palettes.name)
			{
				writeObject {
					palettes.forEach { (name, palette) ->
						at(name) { write(palette) }
					}
				}
			}
		}
	}

	/**
	 * Save this [StylingGroup] to the file indicated by the provided path.
	 *
	 * @param path
	 *   The path to the file where this [StylingGroup] is to be saved.
	 */
	@Suppress("unused")
	fun saveToDisk (path: String)
	{
		File(path).apply {
			if (!isDirectory)
			{
				writeText(jsonPrettyPrintedFormattedString)
			}
		}
	}

	/**
	 * Construct a [StylingGroup].
	 *
	 * @param obj
	 *   The [JSONObject] to extract the [StylingGroup] from.
	 */
	constructor(obj: JSONObject): this()
	{
		obj.getObjectOrNull(::stylesheet.name)?.let {
			it.associateTo(stylesheet as MutableMap<String, StyleAttributes>)
			{ (rule, attributes) ->
				rule to StyleAttributes(attributes as JSONObject)
			}
		}
		obj.getObjectOrNull(::palettes.name)?.let {
			it.associateTo(palettes)
			{ (name, palette) ->
				name to Palette.from(palette as JSONObject)
			}
		}
	}

	companion object
	{
		/**
		 * Extract the [StylingGroup] from the configuration file at the provided
		 * [ProjectConfig] location.
		 *
		 * @param config
		 *   The [ProjectConfig] location of the file that contains the
		 *   [StylingGroup].
		 */
		fun from (config: ProjectConfig): StylingGroup =
			StylingGroup(jsonObject(File(config.fullPathNoPrefix).readText()))
	}
}
