/*
 * Styles.kt
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

package avail.environment

import avail.descriptor.methods.StylerDescriptor.SystemStyle
import avail.environment.BoundStyle.Companion.defaultStyle
import avail.environment.streams.StreamStyle
import avail.persistence.cache.StyleRun
import java.awt.Color
import javax.swing.SwingUtilities
import javax.swing.text.Style
import javax.swing.text.StyleConstants
import javax.swing.text.StyleContext
import javax.swing.text.StyledDocument

/**
 * An abstraction of the styles used by the [workbench][AvailWorkbench].
 * Represents the binding of an abstract style name to a
 * [textual&#32;style][Style] usable by a [styled&#32;document][StyledDocument].
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
interface BoundStyle
{
	/** The canonical name of the style. Should begin with octothorp (`#`). */
	val styleName: String

	/**
	 * Apply the light-themed variant of the receiving style to the specified
	 * [StyledDocument].
	 *
	 * Generally should be overridden, but not called directly. Most callers
	 * will want to use [setStyle], because it is theme-sensitive.
	 *
	 * @param doc
	 *   The document with which to register the receiver.
	 * @return
	 *   The applied [textual&#32;style][Style].
	 */
	fun lightStyle(doc: StyledDocument): Style

	/**
	 * Apply the dark-themed variant of the receiving style to the specified
	 * [StyledDocument].
	 *
	 * Generally should be overridden, but not called directly. Most callers
	 * will want to use [setStyle], because it is theme-sensitive.
	 *
	 * @param doc
	 *   The document with which to register the receiver.
	 * @return
	 *   The applied [textual&#32;style][Style].
	 */
	fun darkStyle(doc: StyledDocument): Style

	/**
	 * Apply the appropriate variant of the receiving style to the specified
	 * [StyledDocument] based on the
	 * [active&#32;theme][AvailWorkbench.darkMode].
	 *
	 * @param doc
	 *   The document with which to register the receiver.
	 * @return
	 *   The applied [textual&#32;style][Style].
	 */
	fun setStyle(doc: StyledDocument): Style =
		if (AvailWorkbench.darkMode) darkStyle(doc) else lightStyle(doc)

	/**
	 * Answer the active [style][Style] for the receiver and the specified
	 * [StyledDocument].
	 *
	 * @param doc
	 *   The styled document.
	 * @return
	 *   The applied [textual&#32;style][Style], if the receiver is registered
	 *   with the argument; otherwise, `null`.
	 */
	fun getStyle(doc: StyledDocument): Style? = doc.getStyle(styleName)

	companion object
	{
		/** The default style. */
		val defaultStyle: Style by lazy {
			StyleContext.getDefaultStyleContext()
				.getStyle(StyleContext.DEFAULT_STYLE)
		}
	}
}

/**
 * Default [style&#32;bindings][BoundStyle] for all
 * [system&#32;styles][SystemStyle]. Constructor capabilities are not intended
 * to cover Swing's styling capabilities comprehensively, only to provide those
 * options that will be exercised by one or more system styles.
 *
 * @property family
 *   The font family.
 * @property foreground
 *   How to obtain a [system&#32;color][SystemColors] for the foreground.
 * @property background
 *   How to obtain a [system&#32;color][SystemColors] for the background.
 * @property bold
 *   Whether to use bold font weight.
 * @property italic
 *   Whether to use italic font style.
 * @property italic
 *   Whether to use underline font decoration.
 * @author Leslie Schultz &lt;leslie@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * 
 * @constructor
 * Create a [DefaultBoundSystemStyle] for the specified [SystemStyle].
 * 
 * @param systemStyle
 *   The target system style.
 * @param family
 *   The font family. Defaults to `"Monospaced"`.
 * @param foreground
 *   How to obtain a [system&#32;color][SystemColors] for the foreground.
 *   Defaults to [SystemColors.baseCode].
 * @param background
 *   How to obtain a [system&#32;color][SystemColors] for the background.
 *   Defaults to [SystemColors.codeBackground].
 * @param bold
 *   Whether to use bold font weight. Defaults to `false`.
 * @param italic
 *   Whether to use italic font style. Defaults to `false`.
 * @param italic
 *   Whether to use underline font decoration. Defaults to `false`.
 */
enum class DefaultBoundSystemStyle(
	systemStyle: SystemStyle,
	private val family: String = "Monospaced",
	private val foreground: ((SystemColors)->Color) = SystemColors::baseCode,
	private val background: ((SystemColors)->Color) =
		SystemColors::codeBackground,
	private val bold: Boolean = false,
	private val italic: Boolean = false,
	private val underline: Boolean = false
): BoundStyle
{
	/** Default style for [SystemStyle.BLOCK]. */
	BLOCK(
		SystemStyle.BLOCK,
		foreground = SystemColors::mustard
	),

	/** Default style for [SystemStyle.METHOD_DEFINITION]. */
	METHOD_DEFINITION(
		SystemStyle.METHOD_DEFINITION,
		foreground = SystemColors::rose
	),

	/** Default style for [SystemStyle.METHOD_NAME]. */
	METHOD_NAME(
		SystemStyle.METHOD_NAME,
		foreground = SystemColors::mango
	),

	/** Default style for [SystemStyle.PARAMETER_DEFINITION]. */
	PARAMETER_DEFINITION(
		SystemStyle.PARAMETER_DEFINITION,
		foreground = SystemColors::transparentMagenta,
		bold = true
	),

	/** Default style for [SystemStyle.PARAMETER_USE]. */
	PARAMETER_USE(
		SystemStyle.PARAMETER_USE,
		foreground = SystemColors::magenta,
		bold = true
	),

	/** Default style for [SystemStyle.METHOD_SEND]. */
	METHOD_SEND(SystemStyle.METHOD_SEND),

	/** Default style for [SystemStyle.MACRO_SEND]. */
	MACRO_SEND(SystemStyle.MACRO_SEND),

	/** Default style for [SystemStyle.STATEMENT]. */
	STATEMENT(SystemStyle.STATEMENT, bold = true),

	/** Default style for [SystemStyle.TYPE]. */
	TYPE(
		SystemStyle.TYPE,
		foreground = SystemColors::blue
	),

	/** Default style for [SystemStyle.METATYPE]. */
	METATYPE(
		SystemStyle.METATYPE,
		foreground = SystemColors::blue,
		italic = true
	),

	/** Default style for [SystemStyle.PHRASE_TYPE]. */
	PHRASE_TYPE(
		SystemStyle.PHRASE_TYPE,
		foreground = SystemColors::lilac,
		italic = true
	),

	/** Default style for [SystemStyle.MODULE_HEADER]. */
	MODULE_HEADER(
		SystemStyle.MODULE_HEADER,
		foreground = SystemColors::transparentRose
	),

	/** Default style for [SystemStyle.VERSION]. */
	VERSION(
		SystemStyle.VERSION,
		foreground = SystemColors::rose
	),

	/** Default style for [SystemStyle.IMPORT]. */
	IMPORT(
		SystemStyle.IMPORT,
		foreground = SystemColors::rose
	),

	/** Default style for [SystemStyle.EXPORT]. */
	EXPORT(
		SystemStyle.EXPORT,
		foreground = SystemColors::rose
	),

	/** Default style for [SystemStyle.ENTRY_POINT]. */
	ENTRY_POINT(
		SystemStyle.ENTRY_POINT,
		foreground = SystemColors::rose,
		bold = true
	),

	/** Default style for [SystemStyle.COMMENT]. */
	COMMENT(
		SystemStyle.COMMENT,
		foreground = SystemColors::weakGray
	),

	/** Default style for [SystemStyle.DOCUMENTATION]. */
	DOCUMENTATION(
		SystemStyle.DOCUMENTATION,
		foreground = SystemColors::strongGray
	),

	/** Default style for [SystemStyle.DOCUMENTATION_TAG]. */
	DOCUMENTATION_TAG(
		SystemStyle.DOCUMENTATION_TAG,
		foreground = SystemColors::strongGray,
		bold = true
	),

	/** Default style for [SystemStyle.MODULE_CONSTANT_DEFINITION]. */
	MODULE_CONSTANT_DEFINITION(
		SystemStyle.MODULE_CONSTANT_DEFINITION,
		foreground = SystemColors::transparentMagenta,
		bold = true
	),

	/** Default style for [SystemStyle.MODULE_CONSTANT_USE]. */
	MODULE_CONSTANT_USE(
		SystemStyle.MODULE_CONSTANT_USE,
		foreground = SystemColors::magenta,
		italic = true
	),

	/** Default style for [SystemStyle.MODULE_VARIABLE_DEFINITION]. */
	MODULE_VARIABLE_DEFINITION(
		SystemStyle.MODULE_VARIABLE_DEFINITION,
		foreground = SystemColors::transparentMagenta,
		bold = true
	),

	/** Default style for [SystemStyle.MODULE_VARIABLE_USE]. */
	MODULE_VARIABLE_USE(
		SystemStyle.MODULE_VARIABLE_USE,
		foreground = SystemColors::magenta,
		italic = true,
		underline = true
	),

	/** Default style for [SystemStyle.LOCAL_CONSTANT_DEFINITION]. */
	LOCAL_CONSTANT_DEFINITION(
		SystemStyle.LOCAL_CONSTANT_DEFINITION,
		foreground = SystemColors::transparentMagenta,
		bold = true
	),

	/** Default style for [SystemStyle.LOCAL_CONSTANT_USE]. */
	LOCAL_CONSTANT_USE(
		SystemStyle.LOCAL_CONSTANT_USE,
		foreground = SystemColors::magenta
	),

	/** Default style for [SystemStyle.LOCAL_VARIABLE_DEFINITION]. */
	LOCAL_VARIABLE_DEFINITION(
		SystemStyle.LOCAL_VARIABLE_DEFINITION,
		foreground = SystemColors::transparentMagenta,
		bold = true
	),

	/** Default style for [SystemStyle.LOCAL_VARIABLE_USE]. */
	LOCAL_VARIABLE_USE(
		SystemStyle.LOCAL_VARIABLE_USE,
		foreground = SystemColors::magenta,
		underline = true
	),

	/** Default style for [SystemStyle.LABEL_DEFINITION]. */
	LABEL_DEFINITION(
		SystemStyle.LABEL_DEFINITION,
		foreground = SystemColors::mustard,
		background = SystemColors::transparentMustard
	),

	/** Default style for [SystemStyle.LABEL_USE]. */
	LABEL_USE(
		SystemStyle.LABEL_USE,
		foreground = SystemColors::mustard
	),

	/** Default style for [SystemStyle.STRING_LITERAL]. */
	STRING_LITERAL(
		SystemStyle.STRING_LITERAL,
		foreground = SystemColors::green
	),

	/** Default style for [SystemStyle.STRING_ESCAPE_SEQUENCE]. */
	STRING_ESCAPE_SEQUENCE(
		SystemStyle.STRING_ESCAPE_SEQUENCE,
		foreground = SystemColors::transparentGreen
	),

	/** Default style for [SystemStyle.NUMERIC_LITERAL]. */
	NUMERIC_LITERAL(
		SystemStyle.NUMERIC_LITERAL,
		foreground = SystemColors::green
	),

	/** Default style for [SystemStyle.BOOLEAN_LITERAL]. */
	BOOLEAN_LITERAL(
		SystemStyle.BOOLEAN_LITERAL,
		foreground = SystemColors::green
	),

	/** Default style for [SystemStyle.TUPLE_CONSTRUCTOR]. */
	TUPLE_CONSTRUCTOR(
		SystemStyle.TUPLE_CONSTRUCTOR,
		foreground = SystemColors::green
	),

	/** Default style for [SystemStyle.SET_CONSTRUCTOR]. */
	SET_CONSTRUCTOR(
		SystemStyle.SET_CONSTRUCTOR,
		foreground = SystemColors::green
	),

	/** Default style for [SystemStyle.MAP_CONSTRUCTOR]. */
	MAP_CONSTRUCTOR(
		SystemStyle.MAP_CONSTRUCTOR,
		foreground = SystemColors::green
	),

	/** Default style for [SystemStyle.CHARACTER_LITERAL]. */
	CHARACTER_LITERAL(
		SystemStyle.CHARACTER_LITERAL,
		foreground = SystemColors::green
	),

	/** Default style for [SystemStyle.ATOM_LITERAL]. */
	ATOM_LITERAL(
		SystemStyle.ATOM_LITERAL,
		foreground = SystemColors::green
	),

	/** Default style for [SystemStyle.CONDITIONAL]. */
	CONDITIONAL(
		SystemStyle.CONDITIONAL,
		foreground = SystemColors::mustard
	),

	/** Default style for [SystemStyle.LOOP]. */
	LOOP(
		SystemStyle.LOOP,
		foreground = SystemColors::mustard
	),

	/** Default style for [SystemStyle.LEXER_DEFINITION]. */
	LEXER_DEFINITION(
		SystemStyle.LEXER_DEFINITION,
		foreground = SystemColors::rose
	),

	/** Default style for [SystemStyle.MACRO_DEFINITION]. */
	MACRO_DEFINITION(
		SystemStyle.MACRO_DEFINITION,
		foreground = SystemColors::rose
	),

	/** Default style for [SystemStyle.SEMANTIC_RESTRICTION_DEFINITION]. */
	SEMANTIC_RESTRICTION_DEFINITION(
		SystemStyle.SEMANTIC_RESTRICTION_DEFINITION,
		foreground = SystemColors::rose
	),

	/** Default style for [SystemStyle.GRAMMATICAL_RESTRICTION_DEFINITION]. */
	GRAMMATICAL_RESTRICTION_DEFINITION(
		SystemStyle.GRAMMATICAL_RESTRICTION_DEFINITION,
		foreground = SystemColors::rose
	),

	/** Default style for [SystemStyle.SEAL_DEFINITION]. */
	SEAL_DEFINITION(
		SystemStyle.SEAL_DEFINITION,
		foreground = SystemColors::rose
	),

	/** Default style for [SystemStyle.OBJECT_TYPE_DEFINITION]. */
	OBJECT_TYPE_DEFINITION(
		SystemStyle.OBJECT_TYPE_DEFINITION,
		foreground = SystemColors::blue
	),

	/** Default style for [SystemStyle.SPECIAL_OBJECT_DEFINITION]. */
	SPECIAL_OBJECT_DEFINITION(
		SystemStyle.SPECIAL_OBJECT_DEFINITION,
		foreground = SystemColors::rose
	),

	/** Default style for [SystemStyle.PRIMITIVE_NAME]. */
	PRIMITIVE_NAME(
		SystemStyle.PRIMITIVE_NAME,
		foreground = SystemColors::rose,
		bold = true
	),

	/** Default style for [SystemStyle.PHRASE]. */
	PHRASE(
		SystemStyle.PHRASE,
		foreground = SystemColors::lilac
	),

	/** Default style for [SystemStyle.RETURN_VALUE]. */
	RETURN_VALUE(
		SystemStyle.RETURN_VALUE,
		foreground = SystemColors::mustard,
		background = SystemColors::transparentMustard
	),

	/** Default style for [SystemStyle.NONLOCAL_CONTROL]. */
	NONLOCAL_CONTROL(
		SystemStyle.NONLOCAL_CONTROL,
		foreground = SystemColors::mustard,
		background = SystemColors::transparentMustard
	);

	override val styleName = systemStyle.kotlinString

	override fun lightStyle(doc: StyledDocument): Style
	{
		val style = doc.addStyle(styleName, defaultStyle)
		StyleConstants.setFontFamily(style, family)
		StyleConstants.setForeground(style, foreground(LightColors))
		StyleConstants.setBackground(style, background(LightColors))
		StyleConstants.setBold(style, bold)
		StyleConstants.setItalic(style, italic)
		StyleConstants.setUnderline(style, underline)
		return style
	}

	override fun darkStyle(doc: StyledDocument): Style
	{
		val style = doc.addStyle(styleName, defaultStyle)
		StyleConstants.setFontFamily(style, family)
		StyleConstants.setForeground(style, foreground(DarkColors))
		StyleConstants.setBackground(style, background(DarkColors))
		StyleConstants.setBold(style, bold)
		StyleConstants.setItalic(style, italic)
		StyleConstants.setUnderline(style, underline)
		return style
	}
}

/**
 * The global registry of all styles, initially populated with the
 * [system&#32;styles][SystemStyle] and their
 * [default&#32;bindings][DefaultBoundSystemStyle].
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object StyleRegistry: Iterable<BoundStyle>
{
	/**
	 * The registry of all styles, as a map from style names to
	 * [concrete&#32;style&#32;bindings][BoundStyle], initially populated from
	 * [DefaultBoundSystemStyle] and [StreamStyle].
	 */
	private val allStyles = DefaultBoundSystemStyle.values()
 		.associateByTo(mutableMapOf<String, BoundStyle>()) { it.styleName }
		.apply {
			putAll(StreamStyle.values().map { it.styleName to it })
		}

	/**
	 * Lookup the [style&#32;binding][BoundStyle] for the specified
	 * [SystemStyle].
	 *
	 * @param systemStyle
	 *   The system style of interest.
	 * @return
	 *   The requested binding. Will never be `null`.
	 */
	operator fun get(systemStyle: SystemStyle) =
		allStyles[systemStyle.kotlinString]!!

	/**
	 * Lookup the [style&#32;binding][BoundStyle] for the specified style.
	 *
	 * @param styleName
	 *   The name of the style of interest.
	 * @return
	 *   The requested binding, if any.
	 */
	operator fun get(styleName: String) = allStyles[styleName]

	/**
	 * Apply the appropriate variant of every style enclosed by the specified
	 * `enum` to the specified [StyledDocument].
	 *
	 * @param T
	 *   The `enum` of interest.
	 * @param doc
	 *   The styled document.
	 */
	inline fun <reified T> addStyles(doc: StyledDocument)
		where T: Enum<T>, T: BoundStyle
	{
		T::class.java.enumConstants!!.forEach { it.setStyle(doc) }
	}

	/**
	 * Apply the appropriate variant of every registered style to the specified
	 * [StyledDocument].
	 *
	 * @param doc
	 *   The styled document.
	 */
	fun addAllStyles(doc: StyledDocument) = allStyles.values.forEach {
		it.setStyle(doc)
	}

	override fun iterator() = allStyles.values.iterator()
}

/**
 * Utility for applying [bound&#32;styles][BoundStyle] to [StyledDocument]s.
 *
 * @author Todd Smith &lt;todd@availlang.org&gt;
 */
object StyleApplicator
{
	/**
	 * Apply all [style&#32;runs] to the receiver. Each style name is treated as
	 * a comma-separated composite. Rendered styles compose rather than replace.
	 * **Must be invoked on the Swing UI thread.**
	 *
	 * @param runs
	 *   The style runs to apply to the [document][StyledDocument].
	 */
	fun StyledDocument.applyStyleRuns(runs: List<StyleRun>)
	{
		assert(SwingUtilities.isEventDispatchThread())
		runs.forEach { (range, compositeStyleName) ->
			val styleNames = compositeStyleName.split(",")
			styleNames.forEach { styleName ->
				getStyle(styleName)?.let { style ->
					setCharacterAttributes(
						range.first - 1,
						range.last - range.first + 1,
						style,
						false)
				}
			}
		}
	}
}
