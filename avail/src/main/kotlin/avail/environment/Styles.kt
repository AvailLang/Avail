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
import avail.environment.AdaptiveColor.Companion.blend
import avail.environment.BoundStyle.Companion.defaultStyle
import avail.environment.StyleFlag.*
import avail.environment.streams.StreamStyle
import avail.persistence.cache.StyleRun
import java.awt.Color
import javax.swing.SwingUtilities
import javax.swing.text.Style
import javax.swing.text.StyleConstants
import javax.swing.text.StyleConstants.CharacterConstants
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

/** A builder for DefaultBoundSystemStyle. */
class DefaultBoundSystemStyleBuilder(private val flags: MutableSet<StyleFlag>)
{
	var family: String = "Monospaced"
	var foreground: ((SystemColors)->Color) = SystemColors::baseCode
	var background: ((SystemColors)->Color) = SystemColors::codeBackground
	fun bold() { flags.add(Bold) }
	fun italic() { flags.add(Italic) }
	fun underline() { flags.add(Underline) }
	fun superscript() { flags.add(Superscript) }
	fun subscript() { flags.add(Subscript) }
}

/**
 * Default [style&#32;bindings][BoundStyle] for all
 * [system&#32;styles][SystemStyle]. Constructor capabilities are not intended
 * to cover Swing's styling capabilities comprehensively, only to provide those
 * options that will be exercised by one or more system styles.
 *
 * @author Leslie Schultz &lt;leslie@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
enum class DefaultBoundSystemStyle: BoundStyle
{
	/** Default style for [SystemStyle.BLOCK]. */
	BLOCK(SystemStyle.BLOCK, { foreground = SystemColors::mustard }),

	/** Default style for [SystemStyle.METHOD_DEFINITION]. */
	METHOD_DEFINITION(SystemStyle.METHOD_DEFINITION, {
		foreground = SystemColors::rose
	}),

	/** Default style for [SystemStyle.METHOD_NAME]. */
	METHOD_NAME(SystemStyle.METHOD_NAME, {
		foreground = SystemColors::mango
		bold()
	}),

	/** Default style for [SystemStyle.PARAMETER_DEFINITION]. */
	PARAMETER_DEFINITION(SystemStyle.PARAMETER_DEFINITION, {
		foreground = SystemColors::transparentMagenta
		bold()
	}),

	/** Default style for [SystemStyle.PARAMETER_USE]. */
	PARAMETER_USE(SystemStyle.PARAMETER_USE, {
		foreground = SystemColors::magenta
		bold()
	}),

	/** Default style for [SystemStyle.METHOD_SEND]. */
	METHOD_SEND(SystemStyle.METHOD_SEND, {}),

	/** Default style for [SystemStyle.MACRO_SEND]. */
	MACRO_SEND(SystemStyle.MACRO_SEND, {}),

	/** Default style for [SystemStyle.STATEMENT]. */
	STATEMENT(SystemStyle.STATEMENT, { bold() }),

	/** Default style for [SystemStyle.TYPE]. */
	TYPE(SystemStyle.TYPE, {
		foreground = SystemColors::blue}),

	/** Default style for [SystemStyle.METATYPE]. */
	METATYPE(SystemStyle.METATYPE, {
		foreground = SystemColors::blue
		italic()
	}),

	/** Default style for [SystemStyle.PHRASE_TYPE]. */
	PHRASE_TYPE(SystemStyle.PHRASE_TYPE, {
		foreground = SystemColors::lilac
		italic()
	}),

	/** Default style for [SystemStyle.MODULE_HEADER]. */
	MODULE_HEADER(SystemStyle.MODULE_HEADER, {
		foreground = SystemColors::transparentRose
	}),

	/** Default style for [SystemStyle.VERSION]. */
	VERSION(SystemStyle.VERSION, {
		foreground = SystemColors::rose
	}),

	/** Default style for [SystemStyle.IMPORT]. */
	IMPORT(SystemStyle.IMPORT, {
		foreground = SystemColors::rose
	}),

	/** Default style for [SystemStyle.EXPORT]. */
	EXPORT(SystemStyle.EXPORT, {
		foreground = SystemColors::rose
	}),

	/** Default style for [SystemStyle.ENTRY_POINT]. */
	ENTRY_POINT(SystemStyle.ENTRY_POINT, {
		foreground = SystemColors::rose
		bold()
	}),

	/** Default style for [SystemStyle.COMMENT]. */
	COMMENT(SystemStyle.COMMENT, {
		foreground = SystemColors::weakGray
	}),

	/** Default style for [SystemStyle.DOCUMENTATION]. */
	DOCUMENTATION(SystemStyle.DOCUMENTATION, {
		foreground = SystemColors::strongGray
	}),

	/** Default style for [SystemStyle.DOCUMENTATION_TAG]. */
	DOCUMENTATION_TAG(SystemStyle.DOCUMENTATION_TAG, {
		foreground = SystemColors::strongGray
		bold()
	}),

	/** Default style for [SystemStyle.MODULE_CONSTANT_DEFINITION]. */
	MODULE_CONSTANT_DEFINITION(SystemStyle.MODULE_CONSTANT_DEFINITION, {
		foreground = SystemColors::transparentMagenta
		bold()
	}),

	/** Default style for [SystemStyle.MODULE_CONSTANT_USE]. */
	MODULE_CONSTANT_USE(SystemStyle.MODULE_CONSTANT_USE, {
		foreground = SystemColors::magenta
		italic()
	}),

	/** Default style for [SystemStyle.MODULE_VARIABLE_DEFINITION]. */
	MODULE_VARIABLE_DEFINITION(SystemStyle.MODULE_VARIABLE_DEFINITION, {
		foreground = SystemColors::transparentMagenta
		bold()
	}),

	/** Default style for [SystemStyle.MODULE_VARIABLE_USE]. */
	MODULE_VARIABLE_USE(SystemStyle.MODULE_VARIABLE_USE, {
		foreground = SystemColors::magenta
		italic()
		underline()
	}),

	/** Default style for [SystemStyle.PRIMITIVE_FAILURE_REASON_DEFINITION]. */
	PRIMITIVE_FAILURE_REASON_DEFINITION(
		SystemStyle.PRIMITIVE_FAILURE_REASON_DEFINITION,
		{
			foreground = SystemColors::transparentMagenta
			bold()
		}),

	/** Default style for [SystemStyle.PRIMITIVE_FAILURE_REASON_USE]. */
	PRIMITIVE_FAILURE_REASON_USE(SystemStyle.PRIMITIVE_FAILURE_REASON_USE, {
		foreground = SystemColors::transparentMagenta
	}),

	/** Default style for [SystemStyle.LOCAL_CONSTANT_DEFINITION]. */
	LOCAL_CONSTANT_DEFINITION(SystemStyle.LOCAL_CONSTANT_DEFINITION, {
		foreground = SystemColors::transparentMagenta
		bold()
	}),

	/** Default style for [SystemStyle.LOCAL_CONSTANT_USE]. */
	LOCAL_CONSTANT_USE(SystemStyle.LOCAL_CONSTANT_USE, {
		foreground = SystemColors::magenta
	}),

	/** Default style for [SystemStyle.LOCAL_VARIABLE_DEFINITION]. */
	LOCAL_VARIABLE_DEFINITION(SystemStyle.LOCAL_VARIABLE_DEFINITION, {
		foreground = SystemColors::transparentMagenta
		bold()
	}),

	/** Default style for [SystemStyle.LOCAL_VARIABLE_USE]. */
	LOCAL_VARIABLE_USE(SystemStyle.LOCAL_VARIABLE_USE, {
		foreground = SystemColors::magenta
		underline()
	}),

	/** Default style for [SystemStyle.LABEL_DEFINITION]. */
	LABEL_DEFINITION(SystemStyle.LABEL_DEFINITION, {
		foreground = SystemColors::mustard
		background = SystemColors::transparentMustard
	}),

	/** Default style for [SystemStyle.LABEL_USE]. */
	LABEL_USE(SystemStyle.LABEL_USE, {
		foreground = SystemColors::mustard
	}),

	/** Default style for [SystemStyle.STRING_LITERAL]. */
	STRING_LITERAL(SystemStyle.STRING_LITERAL, {
		foreground = SystemColors::green
	}),

	/** Default style for [SystemStyle.STRING_ESCAPE_SEQUENCE]. */
	STRING_ESCAPE_SEQUENCE(SystemStyle.STRING_ESCAPE_SEQUENCE, {
		foreground = SystemColors::transparentGreen
		bold()
	}),

	/** Default style for [SystemStyle.NUMERIC_LITERAL]. */
	NUMERIC_LITERAL(SystemStyle.NUMERIC_LITERAL, {
		foreground = SystemColors::green
	}),

	/** Default style for [SystemStyle.BOOLEAN_LITERAL]. */
	BOOLEAN_LITERAL(SystemStyle.BOOLEAN_LITERAL, {
		foreground = SystemColors::green
	}),

	/** Default style for [SystemStyle.TUPLE_CONSTRUCTOR]. */
	TUPLE_CONSTRUCTOR(SystemStyle.TUPLE_CONSTRUCTOR, {
		foreground = SystemColors::green
	}),

	/** Default style for [SystemStyle.SET_CONSTRUCTOR]. */
	SET_CONSTRUCTOR(SystemStyle.SET_CONSTRUCTOR, {
		foreground = SystemColors::green
	}),

	/** Default style for [SystemStyle.MAP_CONSTRUCTOR]. */
	MAP_CONSTRUCTOR(SystemStyle.MAP_CONSTRUCTOR, {
		foreground = SystemColors::green
	}),

	/** Default style for [SystemStyle.CHARACTER_LITERAL]. */
	CHARACTER_LITERAL(SystemStyle.CHARACTER_LITERAL, {
		foreground = SystemColors::green
	}),

	/** Default style for [SystemStyle.ATOM_LITERAL]. */
	ATOM_LITERAL(SystemStyle.ATOM_LITERAL, {
		foreground = SystemColors::green
	}),

	/** Default style for [SystemStyle.CONDITIONAL]. */
	CONDITIONAL(SystemStyle.CONDITIONAL, {
		foreground = SystemColors::mustard
	}),

	/** Default style for [SystemStyle.LOOP]. */
	LOOP(SystemStyle.LOOP, {
		foreground = SystemColors::mustard
	}),

	/** Default style for [SystemStyle.LEXER_DEFINITION]. */
	LEXER_DEFINITION(SystemStyle.LEXER_DEFINITION, {
		foreground = SystemColors::rose
	}),

	/** Default style for [SystemStyle.MACRO_DEFINITION]. */
	MACRO_DEFINITION(SystemStyle.MACRO_DEFINITION, {
		foreground = SystemColors::rose
	}),

	/** Default style for [SystemStyle.SEMANTIC_RESTRICTION_DEFINITION]. */
	SEMANTIC_RESTRICTION_DEFINITION(
		SystemStyle.SEMANTIC_RESTRICTION_DEFINITION,
		{
			foreground = SystemColors::rose
		}),

	/** Default style for [SystemStyle.GRAMMATICAL_RESTRICTION_DEFINITION]. */
	GRAMMATICAL_RESTRICTION_DEFINITION(
		SystemStyle.GRAMMATICAL_RESTRICTION_DEFINITION,
		{
			foreground = SystemColors::rose
		}),

	/** Default style for [SystemStyle.SEAL_DEFINITION]. */
	SEAL_DEFINITION(SystemStyle.SEAL_DEFINITION, {
		foreground = SystemColors::rose
	}),

	/** Default style for [SystemStyle.OBJECT_TYPE_DEFINITION]. */
	OBJECT_TYPE_DEFINITION(SystemStyle.OBJECT_TYPE_DEFINITION, {
		foreground = SystemColors::blue
	}),

	/** Default style for [SystemStyle.SPECIAL_OBJECT]. */
	SPECIAL_OBJECT(SystemStyle.SPECIAL_OBJECT, {
		foreground = SystemColors::rose
	}),

	/** Default style for [SystemStyle.PRIMITIVE_NAME]. */
	PRIMITIVE_NAME(SystemStyle.PRIMITIVE_NAME, {
		foreground = SystemColors::rose
		bold()
	}),

	/** Default style for [SystemStyle.PHRASE]. */
	PHRASE(SystemStyle.PHRASE, {
		foreground = SystemColors::lilac
	}),

	/** Default style for [SystemStyle.RETURN_VALUE]. */
	RETURN_VALUE(SystemStyle.RETURN_VALUE, {
		foreground = SystemColors::mustard
		background = SystemColors::transparentMustard
	}),

	/** Default style for [SystemStyle.NONLOCAL_CONTROL]. */
	NONLOCAL_CONTROL(SystemStyle.NONLOCAL_CONTROL, {
		foreground = SystemColors::mustard
		background = SystemColors::transparentMustard
	}),

	MATH_EXPONENT(SystemStyle.MATH_EXPONENT, {
		superscript()
	}),

	DEEMPHASIZE(SystemStyle.DEEMPHASIZE, {
		foreground = SystemColors::deemphasize
	});

	/**
	 * Create a [DefaultBoundSystemStyle] for the specified [SystemStyle].  The
	 * [setup] function is given a [DefaultBoundSystemStyleBuilder] as an
	 * implicit receiver.
	 */
	@Suppress("ConvertSecondaryConstructorToPrimary")
	constructor(
		systemStyle: SystemStyle,
		setup: DefaultBoundSystemStyleBuilder.()->Unit)
	{
		this.systemStyle = systemStyle
		styleName = systemStyle.kotlinString
		booleanFlags = mutableSetOf()
		val builder = DefaultBoundSystemStyleBuilder(booleanFlags)
		builder.setup()
		family = builder.family
		foreground = builder.foreground
		background = builder.background
	}

	/**
	 * The [SystemStyle] that should have this [DefaultBoundSystemStyle] applied
	 * to it.
	 */
	private val systemStyle: SystemStyle

	/** The name of this style.  Should begin with '#'. */
	override val styleName: String

	/** The font family name.  Defaults to `"Monospaced"`. */
	private val family: String

	/**
	 * How to obtain a [system&#32;color][SystemColors] for the foreground. May
	 * be `null`, to use the default foreground. Defaults to `null`.
	 */
	private val foreground: ((SystemColors)->Color)

	/**
	 * How to obtain a [system&#32;color][SystemColors] for the background. May
	 * be `null`, to use the default background. Defaults to `null`.
	 */
	private val background: ((SystemColors)->Color)

	/**
	 * Extract the [CharacterConstants] provided as varargs in the constructor.
	 * If any argument is not a [CharacterConstants], fail right away.  The
	 * idiotic API of Swing *goes out of its way* to throw away all of the
	 * useful type information for no reason at all.
	 */
	private val booleanFlags: Set<StyleFlag>

	override fun lightStyle(doc: StyledDocument): Style
	{
		val style = doc.addStyle(styleName, defaultStyle)
		StyleConstants.setFontFamily(style, family)
		StyleConstants.setForeground(style, foreground(LightColors))
		StyleConstants.setBackground(style, background(LightColors))
		booleanFlags.forEach {
			style.addAttribute(it.styleConstants, true)
		}
		return style
	}

	override fun darkStyle(doc: StyledDocument): Style
	{
		val style = doc.addStyle(styleName, defaultStyle)
		StyleConstants.setFontFamily(style, family)
		StyleConstants.setForeground(style, foreground(DarkColors))
		StyleConstants.setBackground(style, background(DarkColors))
		booleanFlags.forEach {
			style.addAttribute(it.styleConstants, true)
		}
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
	@Suppress("unused")
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
 * @author Todd L Smith &lt;todd@availlang.org&gt;
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
		val compositeStyles = mutableMapOf<String, Style>()
		runs.forEach { (range, compositeStyleName) ->
			val styleNames = compositeStyleName.split(",")
			if (styleNames.size == 1)
			{
				// All simple styles should already have been added to the
				// document, so just ask the document for its registered style
				// and use it to style the range.
				val styleName = styleNames.first()
				getStyle(styleName)?.let { style ->
					setCharacterAttributes(
						range.first - 1,
						range.last - range.first + 1,
						style,
						false)
				}
			}
			else
			{
				// Compute the composite styles on demand, using a cache to
				// avoid redundant effort.
				val style =
					compositeStyles.computeIfAbsent(compositeStyleName) {
						val style = addStyle(it, defaultStyle)
						val combined = styleNames.drop(1).fold(
							StyleAspects(getStyle(styleNames.first()))
						) { aspect, nextStyleName ->
							aspect + StyleAspects(getStyle(nextStyleName))
						}
						combined.applyTo(style)
						style
					}
				setCharacterAttributes(
					range.first - 1,
					range.last - range.first + 1,
					style,
					false)
			}
		}
	}
}

/** Styles that are on/off. */
enum class StyleFlag
constructor(styleConstantsObject: Any)
{
	Bold(StyleConstants.Bold),
	Italic(StyleConstants.Italic),
	Underline(StyleConstants.Underline),
	Superscript(StyleConstants.Superscript),
	Subscript(StyleConstants.Subscript);

	/** Undo the idiotic type-erasure to Object. */
	val styleConstants = styleConstantsObject as StyleConstants
}

/**
 * Concentrate the renderable aspects of a [BoundStyle].
 *
 * @property fontFamily
 *   The font family for text rendition.
 * @property foreground
 *   The foreground color, i.e., the color of rendered text.
 * @property background
 *   The background color.
 * @property bold
 *   Whether the font weight is **bold**.
 * @property italic
 *   Whether the font style is _italic_.
 * @property underline
 *   Whether the font decoration is
 *   <span style="text-decoration: underline">underline</span>.
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @constructor
 * Construct an immutable representation of the complete set of supported style
 * attributes available for a [BoundStyle].
 *
 * @param fontFamily
 *   The font family for text rendition.
 * @param foreground
 *   The foreground color, i.e., the color of rendered text.
 * @param background
 *   The background color.
 * @param bold
 *   Whether the font weight is **bold**.
 * @param italic
 *   Whether the font style is _italic_.
 * @param underline
 *   Whether the font decoration is
 *   <span style="text-decoration: underline">underline</span>.
 */
private data class StyleAspects constructor(
	val fontFamily: String,
	val foreground: Color,
	val background: Color,
	val flags: Set<StyleFlag>)
{
	/**
	 * Construct an instance by summarizing the supported style attributes of
	 * the specified [Style].
	 *
	 * @param style
	 *   The style to summarize.
	 */
	constructor(style: Style) : this(
		StyleConstants.getFontFamily(style),
		StyleConstants.getForeground(style),
		StyleConstants.getBackground(style),
		StyleFlag.values().filterTo(mutableSetOf()) {
			@Suppress("IMPLICIT_BOXING_IN_IDENTITY_EQUALS")
			style.getAttribute(it.styleConstants) === java.lang.Boolean.TRUE
		})

	/**
	 * Derive an instance by compositing the receiver and the argument,
	 * according to the following rules:
	 *
	 * * Use the argument's [fontFamily] unconditionally.
	 * * If either the receiver's or the argument's [foreground] is
	 *   [SystemColors.baseCode], then use the other color.
	 * * If neither the receiver's nor the argument's [foreground] is
	 *   [SystemColors.baseCode], then [blend] both colors, giving 15% to the
	 *   receiver and %85 to the argument.
	 * * If either the receiver's or the argument's [background] is
	 *   [SystemColors.codeBackground], then use the other color.
	 * * If neither the receiver's nor the argument's [foreground] is
	 *   [SystemColors.codeBackground], then [blend] both colors, giving 15% to
	 *   the receiver and %85 to the argument.
	 * * If either the receiver or the argument is [bold], then use [bold].
	 * * If either the receiver or the argument is [italic], then use [italic].
	 * * If either the receiver or the argument is [underline], then use
	 *   [underline].
	 *
	 * @param other
	 *   The aspects to merge.
	 * @return
	 *   The merged instance.
	 */
	operator fun plus(other: StyleAspects) = StyleAspects(
		other.fontFamily,
		when
		{
			foreground == SystemColors.active.baseCode -> other.foreground
			other.foreground == SystemColors.active.baseCode -> foreground
			else -> blend(foreground, other.foreground, 0.15f)
		},
		when
		{
			background == SystemColors.active.codeBackground -> other.background
			other.background == SystemColors.active.codeBackground -> background
			else -> blend(background, other.background, 0.15f)
		},
		flags.union(other.flags))

	/**
	 * Apply the consolidated aspects to the specified [style], i.e., for
	 * subsequent use in a [StyledDocument].
	 *
	 * @param style
	 *   The style to modify.
	 */
	fun applyTo(style: Style)
	{
		StyleConstants.setFontFamily(style, fontFamily)
		StyleConstants.setForeground(style, foreground)
		StyleConstants.setBackground(style, background)
		flags.forEach { flag ->
			style.addAttribute(flag.styleConstants, true)
		}
	}
}
