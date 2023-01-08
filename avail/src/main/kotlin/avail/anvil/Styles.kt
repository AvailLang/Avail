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

package avail.anvil

import avail.anvil.AdaptiveColor.Companion.blend
import avail.anvil.BoundStyle.Companion.defaultStyle
import avail.anvil.RenderingContext.Companion.defaultAttributes
import avail.anvil.StyleFlag.Bold
import avail.anvil.StyleFlag.Italic
import avail.anvil.StyleFlag.StrikeThrough
import avail.anvil.StyleFlag.Subscript
import avail.anvil.StyleFlag.Superscript
import avail.anvil.StyleFlag.Underline
import avail.anvil.StylePatternCompiler.Companion.compile
import avail.anvil.StylePatternCompiler.ExactMatchToken
import avail.anvil.StylePatternCompiler.SubsequenceToken
import avail.anvil.StylePatternCompiler.SuccessionToken
import avail.anvil.StyleRuleContextState.ACCEPTED
import avail.anvil.StyleRuleContextState.PAUSED
import avail.anvil.StyleRuleContextState.REJECTED
import avail.anvil.StyleRuleContextState.RUNNING
import avail.anvil.StyleRuleExecutor.endOfSequenceLiteral
import avail.anvil.StyleRuleExecutor.run
import avail.anvil.StyleRuleInstructionCoder.Companion.decodeInstruction
import avail.anvil.streams.StreamStyle
import avail.descriptor.methods.StylerDescriptor.SystemStyle
import avail.descriptor.numbers.AbstractNumberDescriptor.Order
import avail.io.NybbleArray
import avail.io.NybbleInputStream
import avail.io.NybbleOutputStream
import avail.persistence.cache.StyleRun
import avail.utility.PrefixSharingList.Companion.append
import avail.utility.PrefixSharingList.Companion.withoutLast
import org.availlang.artifact.environment.project.AvailProject
import org.availlang.artifact.environment.project.Palette
import org.availlang.artifact.environment.project.StyleAttributes
import java.awt.Color
import javax.swing.SwingUtilities
import javax.swing.text.Style
import javax.swing.text.StyleConstants
import javax.swing.text.StyleConstants.CharacterConstants
import javax.swing.text.StyleConstants.setBackground
import javax.swing.text.StyleConstants.setBold
import javax.swing.text.StyleConstants.setFontFamily
import javax.swing.text.StyleConstants.setForeground
import javax.swing.text.StyleConstants.setItalic
import javax.swing.text.StyleConstants.setStrikeThrough
import javax.swing.text.StyleConstants.setSubscript
import javax.swing.text.StyleConstants.setSuperscript
import javax.swing.text.StyleConstants.setUnderline
import javax.swing.text.StyleContext
import javax.swing.text.StyleContext.getDefaultStyleContext
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
			getDefaultStyleContext()
				.getStyle(StyleContext.DEFAULT_STYLE)
		}
	}
}

/**
 * A builder for [DefaultBoundSystemStyle].
 *
 * @property flags
 *   The [style&#32;flags][StyleFlag].
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
class DefaultBoundSystemStyleBuilder(private val flags: MutableSet<StyleFlag>)
{
	/** The font family. */
	var family: String = "Monospaced"

	/** The foreground [color][Color]. */
	var foreground: ((SystemColors)->Color) = SystemColors::baseCode

	/** The background [color][Color]. */
	var background: ((SystemColors)->Color) = SystemColors::codeBackground

	/** Whether to use bold font weight. */
	fun bold() { flags.add(Bold) }

	/** Whether to use italic font style. */
	fun italic() { flags.add(Italic) }

	/** Whether to use underline text decoration. */
	fun underline() { flags.add(Underline) }

	/** Whether to use superscript text position. */
	fun superscript() { flags.add(Superscript) }

	/** Whether to use subscript text position. */
	fun subscript() { flags.add(Subscript) }

	/** Whether to use strikethrough text decoration. */
	fun strikeThrough() { flags.add(StrikeThrough) }
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

	/** Default style for [SystemStyle.MODULE_HEADER_REGION]. */
	MODULE_HEADER_REGION(SystemStyle.MODULE_HEADER_REGION, {
		background = SystemColors::faintTransparentIndigo
	}),

	/** Default style for [SystemStyle.VERSION]. */
	VERSION(SystemStyle.VERSION, {
		background = SystemColors::faintTransparentRose
	}),

	/** Default style for [SystemStyle.IMPORT]. */
	IMPORT(SystemStyle.IMPORT, {
		background = SystemColors::faintTransparentRose
	}),

	/** Default style for [SystemStyle.EXPORT]. */
	EXPORT(SystemStyle.EXPORT, {
		background = SystemColors::faintTransparentRose
	}),

	/** Default style for [SystemStyle.ENTRY_POINT]. */
	ENTRY_POINT(SystemStyle.ENTRY_POINT, {
		background = SystemColors::faintTransparentRose
		bold()
	}),

	/** Default style for [SystemStyle.ENTRY_POINT]. */
	PRAGMA(SystemStyle.PRAGMA, {
		background = SystemColors::faintTransparentRose
		italic()
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

	/** Default style for [SystemStyle.OTHER_LITERAL]. */
	OTHER_LITERAL(SystemStyle.OTHER_LITERAL, {
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
		foreground = SystemColors::rose
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
		background = SystemColors::transparentMustard
	}),

	/** Default style for [SystemStyle.NONLOCAL_CONTROL]. */
	NONLOCAL_CONTROL(SystemStyle.NONLOCAL_CONTROL, {
		foreground = SystemColors::mustard
	}),

	/** Default style for [SystemStyle.MATH_EXPONENT]. */
	MATH_EXPONENT(SystemStyle.MATH_EXPONENT, {
		superscript()
	}),

	/** Default style for [SystemStyle.DEEMPHASIZE]. */
	DEEMPHASIZE(SystemStyle.DEEMPHASIZE, {
		foreground = SystemColors::deemphasize
	}),

	/** Default style for [SystemStyle.EXCLUDED]. */
	EXCLUDED(SystemStyle.EXCLUDED, {
		strikeThrough()
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
		setFontFamily(style, family)
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
		setFontFamily(style, family)
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
	 * @param replace
	 *   Indicates whether or not the previous attributes should be cleared
	 *   before the new attributes as set. If true, the operation will replace
	 *   the previous attributes entirely. If false, the new attributes will be
	 *   merged with the previous attributes.
	 */
	fun StyledDocument.applyStyleRuns(
		runs: List<StyleRun>,
		replace: Boolean = true)
	{
		assert(SwingUtilities.isEventDispatchThread())
		val compositeStyles = mutableMapOf<String, Style?>()
		runs.forEach { (range, compositeStyleName) ->
			val styleNames = compositeStyleName.split(",")
			val style = if (styleNames.size == 1)
			{
				// All simple styles should already have been added to the
				// document, so just ask the document for its registered style
				// and use it to style the range.
				val styleName = styleNames.first()
				getStyle(styleName)
			}
			else
			{
				// Compute the composite styles on demand, using a cache to
				// avoid redundant effort.
				val style =
					compositeStyles.computeIfAbsent(compositeStyleName) {
						val style = addStyle(it, defaultStyle)
						val styles = styleNames.mapNotNull(::getStyle)
						if (styles.isNotEmpty())
						{
							val combined = styles.drop(1).fold(
								StyleAspects(styles.first())
							) { aspect, nextStyle ->
								aspect + StyleAspects(nextStyle)
							}
							combined.applyTo(style)
							style
						}
						else
						{
							null
						}
					}
				style
			}
			style?.let {
				setCharacterAttributes(
					range.first - 1,
					range.last - range.first + 1,
					style,
					replace)
			}
		}
	}
}

/**
 * Styles that are on/off.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @constructor
 *
 * Wrap a [StyleFlag] around an appropriate
 * [Swing&#32;style&#32;constant][StyleConstants].
 *
 * @param styleConstantsObject
 *   A [Swing&#32;style&#32;constant][StyleConstants] (stupidly typed as
 *   [java.lang.Object] by the authors of Swing).
 */
enum class StyleFlag constructor(styleConstantsObject: Any)
{
	/** Whether to apply bold font weight. */
	Bold(StyleConstants.Bold),

	/** Whether to apply italic font style. */
	Italic(StyleConstants.Italic),

	/** Whether to apply underline text decoration. */
	Underline(StyleConstants.Underline),

	/** Whether to apply superscript text position. */
	Superscript(StyleConstants.Superscript),

	/** Whether to apply subscript text position. */
	Subscript(StyleConstants.Subscript),

	/** Whether to apply strikethrough text decoration. */
	StrikeThrough(StyleConstants.StrikeThrough);

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
 * @property flags
 *   The [style&#32;flags][StyleFlag].
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
 * @param flags
 *   The [style&#32;flags][StyleFlag].
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
	 * * If either the receiver or the argument is [bold][StyleFlag.Bold], then
	 *   use [bold][StyleFlag.Bold].
	 * * If either the receiver or the argument is [italic][StyleFlag.Italic],
	 *   then use [italic][StyleFlag.Italic].
	 * * If either the receiver or the argument is
	 *   [underline][StyleFlag.Underline], then use
	 *   [underline][StyleFlag.Underline].
	 * * If either the receiver or the argument is
	 *   [superscript][StyleFlag.Superscript], then use
	 *   [superscript][StyleFlag.Superscript].
	 * * If either the receiver or the argument is
	 *   [subscript][StyleFlag.Subscript], then use
	 *   [subscript][StyleFlag.Subscript].
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
		setFontFamily(style, fontFamily)
		StyleConstants.setForeground(style, foreground)
		StyleConstants.setBackground(style, background)
		flags.forEach { flag ->
			style.addAttribute(flag.styleConstants, true)
		}
	}
}

//////////////////////////// TODO: New stuff after. ////////////////////////////

////////////////////////////////////////////////////////////////////////////////
//                                Stylesheets.                                //
////////////////////////////////////////////////////////////////////////////////

/**
 * An [AvailProject] contains a [stylesheet][Stylesheet] that dictates how an
 * Avail source viewer/editor should render a source region tagged with one or
 * more style classifiers. A stylesheet comprises one or more
 * [patterns][StylePattern], each of which encodes _(1)_ _whether_ a region of
 * text should be rendered and _(2)_ _how_ it should be rendered. Patterns are
 * written using a simple domain-specific language (DSL); this DSL is designed
 * to be reminiscent of Cascading Stylesheets (CSS), but, for reasons of
 * simplicity, is not compatible with it. Each pattern
 * [compiles][StylePatternCompiler] down into a [rule][StyleRule]. A rule is a
 * program, comprising [StyleRuleInstruction]s, whose input is the sequence `S`
 * of style classifiers attached to some source region `R` and whose output is a
 * partial [rendering&#32;context][RenderingContext] that should be applied to
 * `R`. The complete collection of rules is organized into a global
 * [StyleRuleTree], such that every vertex contains the [StyleRuleContext]s for
 * those rules that are still live and every edge encodes a possible next style
 * classifier, either as _(1)_ a fixed style classifier mentioned by some active
 * rule in the source vertex or _(2)_ a wildcard that matches any style
 * classifier. The [RenderingEngine] iteratively feeds `S`, one style classifier
 * at a time, through the tree, starting at the root. At each vertex, it feeds
 * the current classifier `C` to each rule therein. Each rule that accepts `C`
 * generates one or more [StyleRuleContext]s, each of which is injected into the
 * lazy successor vertex along the edge labeled `C`; each rule that rejects `C`
 * is excluded from further consideration; each rule that completes adds itself
 * to the (ordered) solution set. After consuming `S` entirely, the rules of the
 * solution set are ranked according to specificity, and every rule that ties
 * for highest specificity contributes its rendering effects to the final
 * [RenderingContext]. Rendering conflicts are resolved by insertion order, with
 * later rules prevailing over earlier ones. For computational efficiency, the
 * final result is memoized (to the sequence `S`).
 *
 * @property styleRules
 *   The [style&#32;rules][StyleRule].
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
class Stylesheet constructor(val styleRules: Set<StyleRule>)
{
	/**
	 * Abstract the [Stylesheet] from the specified [project][AvailProject].
	 *
	 * @param project
	 *   The project from which to decode the stylesheet.
	 * @param errors
	 *   The accumulator for errors, as pairs of invalid
	 *   [patterns][UnvalidatedStylePattern] and
	 *   [exceptions][StylePatternException]. When non-`null`, patterns that
	 *   fail [compilation][compile] are excised from the result, and any
	 *   relevant [StylePatternException]s are appended here; when `null`,
	 *   the constructor will terminate abnormally (by raising a
	 *   [StylePatternException]) upon encountering an invalid source
	 *   pattern.
	 * @throws StylePatternException
	 *   If any of the source patterns fails [compilation][compile] for
	 *   any reason.
	 */
	constructor(
		project: AvailProject,
		errors: MutableList<Pair<
			UnvalidatedStylePattern, StylePatternException>>? = null
	): this(project.stylesheet, project.palette, errors)
	{
		// No implementation required.
	}

	/**
	 * Construct a [Stylesheet] from (1) the specified map of source patterns
	 * and [style&#32;attributes][StyleAttributes] and (2) the
	 * [palette][Palette] for final color selection.
	 *
	 * @param map
	 *   The map of source patterns to their desired style attributes.
	 * @param palette
	 *   The palette.
	 * @param errors
	 *   The accumulator for errors, as pairs of invalid
	 *   [patterns][UnvalidatedStylePattern] and
	 *   [exceptions][StylePatternException]. When non-`null`, patterns that
	 *   fail [compilation][compile] are excised from the result, and any
	 *   relevant [StylePatternException]s are appended here; when `null`, the
	 *   constructor will terminate abnormally (by raising a
	 *   [StylePatternException]) upon encountering an invalid source pattern.
	 * @throws StylePatternException
	 *   If any of the source patterns fails [compilation][compile] for
	 *   any reason.
	 */
	constructor(
		map: Map<String, StyleAttributes>,
		palette: Palette,
		errors: MutableList<Pair<
			UnvalidatedStylePattern, StylePatternException>>? = null
	): this(compileRules(map, palette, errors))
	{
		// No implementation required.
	}

	companion object
	{
		/**
		 * Compile the specified map from source patterns to
		 * [style&#32;attributes][StyleAttributes], using the supplied
		 * [palette][Palette] for final color selection.
		 *
		 * @param map
		 *   The map of source patterns to their desired style attributes.
		 * @param palette
		 *   The palette.
		 * @param errors
		 *   The accumulator for errors, as pairs of invalid
		 *   [patterns][UnvalidatedStylePattern] and
		 *   [exceptions][StylePatternException]. When non-`null`, patterns that
		 *   fail [compilation][compile] are excised from the result, and any
		 *   relevant [StylePatternException]s are appended here; when `null`,
		 *   the function will terminate abnormally (by raising a
		 *   [StylePatternException]) upon encountering an invalid source
		 *   pattern.
		 * @return
		 *   The compiled [rules][StyleRule].
		 * @throws StylePatternException
		 *   If [errors] is not `null` and any of the source patterns fails
		 *   [compilation][compile] for any reason.
		 */
		private fun compileRules(
			map: Map<String, StyleAttributes>,
			palette: Palette,
			errors: MutableList<Pair<
				UnvalidatedStylePattern, StylePatternException>>? = null
		): Set<StyleRule> =
			map.mapNotNullTo(mutableSetOf()) { (source, attrs) ->
				val context = UnvalidatedRenderingContext(attrs)
				val pattern = UnvalidatedStylePattern(source, context)
				try
				{
					compile(pattern, palette)
				}
				catch (e: StylePatternException)
				{
					errors?.add(pattern to e) ?: throw e
					null
				}
			}
	}
}

////////////////////////////////////////////////////////////////////////////////
//                                 Patterns.                                  //
////////////////////////////////////////////////////////////////////////////////

/**
 * A persistent [stylesheet][Stylesheet] maps unvalidated
 * [style&#32;patterns][StylePattern] onto unvalidated
 * [style&#32;attributes][StyleAttributes]. A [StylePatternCompiler] compiles a
 * valid [StylePattern] with valid [StyleAttributes] into a
 * [ValidatedStylePattern].
 *
 * @property source
 *   The source text of the style pattern.
 * @property renderingContext
 *   The [RenderingContext].
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
sealed class StylePattern constructor(
	val source: String,
	open val renderingContext: RenderingContext)
{
	/**
	 * Determine the relative specificity of the receiver and the argument.
	 * The relative specificity of two [patterns][StylePattern] is determined
	 * according to the following criteria:
	 *
	 * * If two patterns are equal, then they are
	 *   _[equally&#32;specific][Order.EQUAL]_.
	 * * All exact matches are mutually _[incomparable][Order.INCOMPARABLE]_.
	 * * Any exact match is _[more&#32;specific][Order.MORE]_ than any inexact
	 *   match.
	 * * For inexact matches, given two [patterns][StylePattern] `P` and `Q`,
	 *   the following rules are applied in order:
	 *    * If `P` contains `Q` and `|P| ≥ |Q|`, then `P` is
	 *      _[more&#32;specific][Order.MORE]_.
	 *    * If `|successions(P)| = `|successions(Q)|`, where `successions`
	 *      computes the successions of its argument, and every succession of
	 *      `P` is more specific than the corresponding succession of `Q`, then
	 *      `P` is _[more&#32;specific][Order.MORE]_.
	 *    * If `|successions(P)| = 1` and `|successions(Q)| > 1`, then `P` and
	 *      `Q` are _[incomparable][Order.INCOMPARABLE]_.
	 *    * If every succession of `P` occurs within `Q`, preserving order and
	 *      advancing monotonically, and `|P| ≤ |Q|`, then `Q` is
	 *      _[more&#32;specific][Order.MORE]_.
	 *    * Otherwise, `P` and `Q` are _[incomparable][Order.INCOMPARABLE]_.
	 *
	 * @param other
	 *   The pattern to check specificity against.
	 * @return
	 *   The [relation][Order] of the two patterns with respect to
	 *   specificity.
	 */
	fun compareSpecificityTo(other: StylePattern): Order
	{
		// Eliminate whitespace, for simplicity.
		val p = source.replace(findWhitespace, "")
		val q = other.source.replace(findWhitespace, "")
		// If the patterns are identical, then naturally they are equivalent.
		if (p == q)
		{
			// e.g., p: #a <=> q: #a | p: #a,#b<#c,#d <=> q: #a,#b<#c,#d
			return Order.EQUAL
		}
		// Now handle exact matches, because they follow special rules (which
		// are easily/cheaply resolved).
		when (val exactMatchOperator = ExactMatchToken.lexeme.first())
		{
			p.first() ->
				return (
					// e.g., p: =#a <=> q: =#b | p: =#a <=> q: =#a,#b
					if (q.first() == exactMatchOperator) Order.INCOMPARABLE
					// e.g., =#a <=> #a
					else Order.MORE)
			q.first() ->
				// e.g., p: #a <=> q: =#b
				return Order.LESS
		}
		// Both patterns are inexact. Now determine if one of the patterns
		// embeds the other entirely. If so, then the larger of the two is the
		// more specific.
		when
		{
			p.contains(q) ->
				// e.g., p: #a,#b,#b <=> q: #a,#b
				return Order.MORE
			q.contains(p) ->
				// e.g., p: #a,#b <=> q: #a,#b,#b
				return Order.LESS
		}
		// Neither pattern directly embeds the other. Decompose each pattern
		// into its successions. If the succession counts of the two patterns
		// are equal, then ascertain whether the successions are all comparable.
		val pSuccessions = p.split(SubsequenceToken.lexeme)
		val qSuccessions = q.split(SubsequenceToken.lexeme)
		if (pSuccessions.size == qSuccessions.size)
		{
			// e.g., p: #a <=> q: #b
			if (pSuccessions.size == 1) return Order.INCOMPARABLE
			val specificities = pSuccessions.zip(qSuccessions)
				.map { (a, b) -> a.compareSuccessionSpecificityTo(b) }
			return when
			{
				// Every succession of p is less than or equally specific to the
				// corresponding succession of q.
				// e.g., p: #a<#b <=> q: #a,#b<#b
				specificities.all { it.isLessOrEqual() } -> Order.LESS
				// Every succession of p is more than or equally specific to the
				// corresponding succession of q.
				// e.g., p: #a,#b<#c <=> q: #b<#c
				specificities.all { it.isMoreOrEqual() } -> Order.MORE
				// e.g., p: #a<#b <=> q: #c<#d
				// e.g., p: #a<#b <=> q: #a<#c
				// e.g., p: #a,#b<#c <=> q: #a<#c,#d
				else -> Order.INCOMPARABLE
			}
		}
		// The two patterns have unequal succession counts. If either contains
		// only a single succession, then the patterns are incomparable.
		val shorter =
			if (pSuccessions.size <= qSuccessions.size) pSuccessions
			else qSuccessions
		val longer = (
			if (shorter === pSuccessions) qSuccessions
			else pSuccessions
		).toMutableList()
		if (shorter.size == 1)
		{
			// e.g., p: #a,#b <=> q: #a<#b | p: #a,#b,#c <=> #a<#b<#c
			// e.g., p: #a<#b <=> q: #a,#b | p: #a<#b<#c <=> #a,#b,#c
			return Order.INCOMPARABLE
		}
		// If every succession of one pattern is comparable to one in the other
		// pattern, preserving order, then the longer pattern is more specific.
		// Otherwise, the patterns are incomparable. Choose the shorter of the
		// two patterns for the iteration, for a minor gain in efficiency.
		val specificities = mutableListOf<Order>()
		var index = 0
		val indices = shorter.map { a ->
			index = longer.subList(index, longer.size).indexOfFirst { b ->
				// Find the earliest comparable element. Remember the result of
				// the comparison, for use below.
				val order = a.compareSuccessionSpecificityTo(b)
				if (!order.isIncomparable())
				{
					specificities.add(order)
					return@indexOfFirst true
				}
				false
			}
			index
		}
		if (indices.contains(-1))
		{
			// One of the successions of the smaller pattern was incomparable to
			// any succession in the longer pattern. Therefore, the patterns are
			// incomparable.
			// e.g., p: #a<#b <=> q: #b<#a<#c
			return Order.INCOMPARABLE
		}
		return when
		{
			pSuccessions.size < qSuccessions.size ->
			{
				assert(shorter === pSuccessions)
				when
				{
					// Every succession of p is less specific or equally
					// specific to a corresponding succession of q, so p is
					// less specific than q.
					// e.g., p: #a<#b <=> q: #a<#c<#b
					// e.g., p: #a<#b <=> q: #a<#c,#d<#b
					// e.g., p: #a<#b <=> q: #a<#c<#b<#d
					// e.g., p: #a<#b <=> q: #a<#c<#d<#b
					// e.g., p: #a<#b <=> q: #c<#a<#d<#b
					// e.g., p: #a<#b <=> q: #c<#a<#d<#b<#e
					// e.g., p: #a<#b<#c <=> q: #a<#d<#b<#c
					// e.g., p: #a<#b<#c <=> q: #a<#c<#b<#c
					// e.g., p: #a<#a<#b <=> q: #a<#b<#a<#b
					// e.g., p: #a<#b<#a<#b <=> q: #a<#a<#b<#b<#a<#b
					specificities.all { it.isLessOrEqual() } -> Order.LESS
					// e.g., p: #a,b<#c <=> q: #a<#c<#d
					else -> Order.INCOMPARABLE
				}
			}
			specificities.all { it.isLessOrEqual() } ->
			{
				assert(pSuccessions.size > qSuccessions.size)
				assert(shorter === qSuccessions)
				// Every succession of q is less specific or equally
				// specific to a corresponding succession of p, so p is
				// more specific than q.
				// e.g., p: #a<#c<#b <=> q: #a<#b
				// e.g., p: #a<#c,#d<#b <=> q: #a<#b
				// e.g., p: #a<#c<#b<#d <=> q: #a<#b
				// e.g., p: #a<#c<#d<#b <=> q: #a<#b
				// e.g., p: #c<#a<#d<#b <=> q: #a<#b
				// e.g., p: #c<#a<#d<#b<#e <=> q: #a<#b
				// e.g., p: #a<#d<#b<#c <=> q: #a<#b<#c
				// e.g., p: #a<#b<#a<#b <=> q: #a<#a<#b
				// e.g., p: #a<#a<#b<#b<#a<#b <=> q: #a<#b<#a<#b
				Order.MORE
			}
			// e.g., p: #a,#b<#b#<#c <=> q: #a<#b,#c
			else -> Order.INCOMPARABLE
		}
	}

	override fun toString() = source

	companion object
	{
		/**
		 * A [regular&#32;expression][Regex] to find whitespace in a source
		 * pattern.
		 */
		private val findWhitespace by lazy { "\\s+".toRegex() }

		/**
		 * A [regular&#32;expression][Regex] to find operators in a source
		 * pattern.
		 */
		private val findOperator by lazy {
			"${SuccessionToken.lexeme}|${SubsequenceToken.lexeme}".toRegex()
		}

		/**
		 * Determine the relative specificity of the receiver and the argument,
		 * where each is treated as a succession within some pattern.
		 * The relative specificity of two successions is determined
		 * according to the following criteria:
		 *
		 * * If the receiver and argument are equal, then they are
		 *   _[equally&#32;specific][Order.EQUAL]_.
		 * * If the receiver contains the argument, then the receiver is
		 *   _[more&#32;specific][Order.MORE]_.
		 * * Otherwise, the receiver and argument are
		 *   _[equally&#32;specific][Order.EQUAL]_.
		 *
		 * @param other
		 *   The succession to check specificity against.
		 * @return
		 *   The [relation][Order] of the two patterns with respect to
		 *   specificity.
		 */
		private fun String.compareSuccessionSpecificityTo(other: String): Order
		{
			// If the patterns are identical, then they are equivalent.
			if (this == other)
			{
				// e.g., p: #a <=> q: #a
				return Order.EQUAL
			}
			// Determine if one of the successions embeds the other entirely. If
			// so, then the larger of the two is the more specific.
			when
			{
				this.contains(other) ->
					// e.g., p: #a,#b,#b <=> q: #a,#b
					return Order.MORE
				other.contains(this) ->
					// e.g., p: #a,#b <=> q: #a,#b,#b
					return Order.LESS
			}
			// The successions are incomparable.
			return Order.INCOMPARABLE
		}

		/**
		 * Determine the first index at which [list] occurs as a sublist of the
		 * receiver.
		 *
		 * @param list
		 *   The list to find.
		 * @return
		 *   The zero-based index of the first occurrence of [list] in the
		 *   receiver, or `-1` if [list] does not occur.
		 */
		private fun <T> List<T>.indexOfSublist(list: List<T>): Int
		{
			val listSize = list.size
			if (listSize > size) return -1
			for (index in 0 .. this.size - listSize)
			{
				val slice = subList(index, index + listSize)
				if (slice == list) return index
			}
			return -1
		}

		/**
		 * Compute the integral signum of the receiver.
		 *
		 * @return
		 *   * `-1` if the receiver is `<0`
		 *   * `0` if the receiver is `=0`
		 *   * `1` if the receiver is `>0`
		 */
		private val Int.signum get() =
			when
			{
				this < 0 -> -1
				this == 0 -> 0
				else -> 1
			}
	}
}

/**
 * An unvalidated [style&#32;pattern][StylePattern].
 *
 * @property renderingContext
 *   The [unvalidated&#32;rendering&#32;context][UnvalidatedRenderingContext].
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @constructor
 *
 * Construct a [ValidatedStylePattern] from the specified source text and
 * [unvalidated&#32;rendering&#32;context][UnvalidatedRenderingContext].
 *
 * @param source
 *   The source text of the style pattern.
 * @param renderingContext
 *   The [unvalidated&#32;rendering&#32;context][UnvalidatedRenderingContext].
 */
class UnvalidatedStylePattern constructor(
	source: String,
	override val renderingContext: UnvalidatedRenderingContext
): StylePattern(source, renderingContext)
{
	/**
	 * Validate the [receiver][UnvalidatedStylePattern] against the supplied
	 * [palette][Palette]. If validation succeeds, then answer a
	 * [ValidatedStylePattern] that includes the validated source and all
	 * attributes of the [rendering&#32;context][renderingContext].
	 *
	 * @param palette
	 *   The [Palette], for interpreting the
	 *   [foreground][StyleAttributes.foreground] and
	 *   [background][StyleAttributes.background] colors for text rendition.
	 * @return
	 *   The [ValidatedStylePattern].
	 * @throws RenderingContextValidationException
	 *   If the palette is missing any referenced colors.
	 */
	fun validate(palette: Palette) = ValidatedStylePattern(
		source,
		renderingContext.validate(palette))
}

/**
 * A [style&#32;pattern][StylePattern] that has been successfully validated by
 * a [StylePatternCompiler].
 *
 * @property renderingContext
 *   The [validated&#32;rendering&#32;context][ValidatedRenderingContext].
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @constructor
 *
 * Construct a [ValidatedStylePattern] from the specified source text and
 * [validated&#32;rendering&#32;context][ValidatedRenderingContext].
 *
 * @param source
 *   The source text of the style pattern.
 * @param renderingContext
 *   The [validated&#32;rendering&#32;context][ValidatedRenderingContext].
 */
class ValidatedStylePattern constructor(
	source: String,
	override val renderingContext: ValidatedRenderingContext
): StylePattern(source, renderingContext)

////////////////////////////////////////////////////////////////////////////////
//                                 Compiler.                                  //
////////////////////////////////////////////////////////////////////////////////

/**
 * A [StylePatternCompiler] parses a [pattern][StylePattern], resolves a
 * [rendering&#32;context][RenderingContext] against a [palette][Palette], and
 * generates a [rule][StyleRule] if all syntactic and semantic requirements are
 * met.
 *
 * @property pattern
 *   The [unvalidated&#32;pattern][UnvalidatedStylePattern] to compile.
 * @property palette
 *   The palette for resolution of symbolic colors.
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
class StylePatternCompiler private constructor(
	private val pattern: UnvalidatedStylePattern,
	private val palette: Palette)
{
	/** The source text of the pattern. */
	private val source get() = pattern.source

	/**
	 * A [Token] represents one of the lexical units of a [StylePattern].
	 *
	 * @author Todd L Smith &lt;todd@availlang.org&gt;
	 */
	internal sealed class Token
	{
		/**
		 * The one-based character position with the source pattern of the
		 * beginning of the [lexeme].
		 */
		abstract val position: Int

		/** The lexeme. */
		abstract val lexeme: String

		final override fun toString() = lexeme
	}

	/**
	 * A [EndOfPatternToken] represents the end of the source pattern.
	 *
	 * @author Todd L Smith &lt;todd@availlang.org&gt;
	 */
	internal data class EndOfPatternToken constructor(
		override val position: Int
	): Token()
	{
		override val lexeme get() = EndOfPatternToken.lexeme

		companion object
		{
			/** The fake lexeme to use for describing end-of-pattern. */
			const val lexeme = "end of pattern"
		}
	}

	/**
	 * A [StyleClassifierToken] represents a literal style classifier.
	 *
	 * @author Todd L Smith &lt;todd@availlang.org&gt;
	 */
	internal data class StyleClassifierToken constructor(
		override val position: Int,
		override val lexeme: String
	): Token()

	/**
	 * A [ExactMatchToken] serves as a leading pragma to force exact-match
	 * semantics on the [pattern][StylePattern]. This disables wildcard matching
	 * and the [subsequence][SubsequenceExpression]
	 * [operator][SubsequenceToken].
	 *
	 * @author Todd L Smith &lt;todd@availlang.org&gt;
	 */
	internal data class ExactMatchToken(override val position: Int = 0): Token()
	{
		override val lexeme get() = ExactMatchToken.lexeme

		companion object
		{
			/** The lexeme. */
			const val lexeme = "="
		}
	}

	/**
	 * A [SuccessionToken] represents immediate succession of two subpatterns of
	 * a [StylePattern]. Succession has higher precedence than subsequence.
	 *
	 * @author Todd L Smith &lt;todd@availlang.org&gt;
	 */
	internal data class SuccessionToken(override val position: Int = 0): Token()
	{
		override val lexeme get() = SuccessionToken.lexeme

		companion object
		{
			/** The lexeme. */
			const val lexeme = ","
		}
	}

	/**
	 * A [SubsequenceToken] represents eventual subsequence of the right-hand
	 * subpattern (after the left-hand subpattern). Subsequence has lower
	 * precedence than succession.
	 *
	 * @author Todd L Smith &lt;todd@availlang.org&gt;
	 */
	internal data class SubsequenceToken(override val position: Int = 0): Token()
	{
		override val lexeme get() = SubsequenceToken.lexeme

		companion object
		{
			/** The lexeme. */
			const val lexeme = "<"
		}
	}

	/**
	 * An [InvalidToken] represents unexpected input in an alleged
	 * [StylePattern], and guarantees that the producing pattern is not a
	 * [ValidatedStylePattern].
	 *
	 * @author Todd L Smith &lt;todd@availlang.org&gt;
	 */
	internal data class InvalidToken constructor(
		override val position: Int,
		override val lexeme: String
	): Token()

	/** The [Token]s scanned from the source text of the pattern. */
	private val tokens: List<Token> by lazy {
		val tokens = mutableListOf<Token>()
		val source = source
		var i = 0
		while (true)
		{
			if (i == source.length) break
			val start = i
			val c = source.codePointAt(i)
			i += Character.charCount(c)
			when
			{
				c == '='.code -> tokens.add(ExactMatchToken(start + 1))
				c == ','.code -> tokens.add(SuccessionToken(start + 1))
				c == '<'.code -> tokens.add(SubsequenceToken(start + 1))
				c == '#'.code ->
				{
					if (i == source.length)
					{
						tokens.add(InvalidToken(start + 1, c.toChar().toString()))
					}
					else
					{
						while (i < source.length)
						{
							val p = source.codePointAt(i)
							if (!p.isNonLeadingClassifierCharacter) break
							i += Character.charCount(p)
						}
						tokens.add(
							StyleClassifierToken(
								start + 1, source.substring(start, i)))
					}
				}
				c.toChar().isWhitespace() ->
				{
					// No action required.
				}
				else -> tokens.add(InvalidToken(
					start + 1, c.toChar().toString()))
			}
		}
		tokens.add(EndOfPatternToken(i + 1))
		tokens
	}

	/**
	 * A [ParseContext] tracks a parsing theory for a [StylePattern]. A
	 * [StylePatternCompiler] manages a small number of contexts in pursuit of
	 * an unambiguous interpretation of a [StylePattern].
	 *
	 * Note that the parsing algorithm is fully deterministic, so [ParseContext]
	 * is a convenient abstraction for bookkeeping, not an essential one for,
	 * e.g., ambiguity resolution, parallelism, etc.
	 *
	 * @property tokenIndex
	 *   The zero-based position of the next [Token] to consider.
	 * @property operands
	 *   The operand stack, containing fully parsed
	 *   [subexpressions][Expression].
	 * @author Todd L Smith &lt;todd@availlang.org&gt;
	 */
	private inner class ParseContext(
		val tokenIndex: Int = 0,
		val operands: List<Expression> = listOf())
	{
		/** The [token] under consideration. */
		val token get() = tokens[tokenIndex]

		/**
		 * The one-based position of the leading character of the [token]
		 * within the source pattern, for error reporting.
		 */
		val position get() = token.position

		/**
		 * Derive a successor [ParseContext] that focuses on the next
		 * [token][Token].
		 */
		val nextContext get() = ParseContext(tokenIndex + 1, operands)

		/**
		 * Derive a successor [ParseContext] that focuses on the next
		 * [token][Token] and includes the specified [subexpression][Expression]
		 * at the top of its [operand&#32;stack][operands].
		 *
		 * @param operand
		 *   The subexpression to push onto the operand stack.
		 * @return
		 *   The requested context.
		 */
		fun with(operand: Expression) = ParseContext(
			tokenIndex + 1,
			operands.append(operand))

		/**
		 * Derive a successor [ParseContext] with an appropriate
		 * [MatchExpression] or [ForkExpression] based on the specified
		 * [classifier]. A [ForkExpression] will be pushed only if the
		 * [classifier] matches the [leading&#32;classifier][leadingClassifier]
		 * in the current [succession][SuccessionExpression].
		 *
		 * @param classifier
		 *   The style classifier.
		 * @param leadingClassifier
		 *   The leading classifier of the current succession, or `null` if this
		 *   call is creating the first subexpression of a new succession.
		 * @return
		 *   The requested context.
		 */
		fun makeMatchExpression(
			classifier: String,
			leadingClassifier: String?
		): ParseContext
		{
			val match = MatchExpression(classifier)
			if (classifier == leadingClassifier)
			{
				return with(ForkExpression(match))
			}
			return with(match)
		}

		/**
		 * Derive a successor [ParseContext] that reduces the top of the
		 * [operand&#32;stack][operands] to an [ExactMatchExpression].
		 *
		 * @return
		 *   The requested context.
		 */
		fun makeExactMatchExpression(): ParseContext
		{
			val operand = operands.last()
			return ParseContext(
				tokenIndex,
				operands.withoutLast().append(ExactMatchExpression(operand)))
		}

		/**
		 * Derive a successor [ParseContext] that reduces the top of the
		 * [operand&#32;stack][operands] to a [SuccessionExpression].
		 *
		 * @return
		 *   The requested context.
		 */
		fun makeSuccessionExpression(): ParseContext
		{
			val (left, right) =
				operands.subList(operands.size - 2, operands.size)
			val operands = operands.subList(0, operands.size - 2)
			return ParseContext(
				tokenIndex,
				operands.append(SuccessionExpression(left, right)))
		}

		/**
		 * Derive a successor [ParseContext] that reduces the top of the
		 * [operand&#32;stack][operands] to a [SubsequenceExpression].
		 *
		 * @return
		 *   The requested context.
		 */
		fun makeSubsequenceExpression(): ParseContext
		{
			val (left, right) =
				operands.subList(operands.size - 2, operands.size)
			val operands = operands.subList(0, operands.size - 2)
			return ParseContext(
				tokenIndex,
				operands.append(SubsequenceExpression(left, right)))
		}

		override fun toString() = buildString {
			append(try { token } catch (e: Exception) { "«bad index»" })
			append('@')
			append(tokenIndex)
			operands.forEach {
				append(" :: ")
				append(it)
			}
		}
	}

	/**
	 * An [Expression] represents an expression within the [StylePattern]
	 * grammar. It serves as the unifying node type for the abstract syntax
	 * tree (AST) of a [StylePattern].
	 *
	 * @author Todd L Smith &lt;todd@availlang.org&gt;
	 */
	private sealed class Expression
	{
		/**
		 * Accept the specified [visitor], dispatching to its appropriate
		 * entry point based on the receiver's own type. Do not automatically
		 * visit any subexpressions; it is the visitor's responsibility to
		 * visit any subexpressions that it cares about.
		 *
		 * @param visitor
		 *   The [visitor][ExpressionVisitor].
		 */
		abstract fun accept(visitor: ExpressionVisitor)

		abstract override fun toString(): String
	}

	/**
	 * A [MatchExpression] represents the intent to match a literal style
	 * classifier.
	 *
	 * @author Todd L Smith &lt;todd@availlang.org&gt;
	 */
	private data class MatchExpression(
		val classifier: String
	): Expression()
	{
		override fun accept(visitor: ExpressionVisitor) = visitor.visit(this)
		override fun toString() = classifier
	}

	/**
	 * A [ExactMatchExpression] constrains its [subexpression][Expression] to
	 * exactly match the entire style classifier stream.
	 *
	 * @property child
	 *   The constrained [subexpression][Expression].
	 * @author Todd L Smith &lt;todd@availlang.org&gt;
	 */
	private data class ExactMatchExpression(val child: Expression): Expression()
	{
		override fun accept(visitor: ExpressionVisitor) = visitor.visit(this)
		override fun toString() = "${ExactMatchToken.lexeme}$child"
	}

	/**
	 * A [SuccessionExpression] constrains its subexpressions to match only if
	 * they are strictly adjacent in the style classifier stream.
	 *
	 * @property left
	 *   The left-hand [subexpression][Expression], which must match first.
	 * @property right
	 *   The right-hand [subexpression][Expression], which must match second,
	 *   without any interleaving positive-width matches.
	 * @author Todd L Smith &lt;todd@availlang.org&gt;
	 */
	private data class SuccessionExpression(
		val left: Expression,
		val right: Expression
	): Expression()
	{
		override fun accept(visitor: ExpressionVisitor) = visitor.visit(this)
		override fun toString() = "$left${SuccessionToken.lexeme}$right"
	}

	/**
	 * A [SubsequenceExpression] constrains its right-hand
	 * [subexpression][Expression] to match eventually, irrespective of the
	 * number of intermediate positive-width matches the occur after the
	 * left-hand subexpression.
	 *
	 * @property left
	 *   The left-hand [subexpression][Expression], which must match first.
	 * @property right
	 *   The right-hand [subexpression][Expression], which must match second,
	 *   after zero or more interleaving positive-width matches.
	 * @author Todd L Smith &lt;todd@availlang.org&gt;
	 */
	private data class SubsequenceExpression(
		val left: Expression,
		val right: Expression
	): Expression()
	{
		override fun accept(visitor: ExpressionVisitor) = visitor.visit(this)
		override fun toString() = "$left ${SubsequenceToken.lexeme} $right"
	}

	/**
	 * A [ForkExpression] mandates that that the [rule][StyleRule] needs to fork
	 * another [context][StyleRuleContext] in order to correctly match a
	 * self-similar pattern. For this purpose, a pattern is _self-similar_ if it
	 * begins with a repeated prefix.
	 *
	 * @property expression
	 *   The [Expression] that represents the remainder of the transitively
	 *   enclosing [Expression] that occurs after the leading occurrence of the
	 *   repeated prefix.
	 * @author Todd L Smith &lt;todd@availlang.org&gt;
	 */
	private data class ForkExpression(val expression: Expression): Expression()
	{
		override fun accept(visitor: ExpressionVisitor) = visitor.visit(this)
		override fun toString() = "fork ($expression)"
	}

	/**
	 * An [EmptyClassifierSequenceExpression] matches only an empty stream of
	 * classifiers. It can only serve as an outermost [expression][Expression].
	 *
	 * @author Todd L Smith &lt;todd@availlang.org&gt;
	 */
	private object EmptyClassifierSequenceExpression: Expression()
	{
		override fun accept(visitor: ExpressionVisitor) = visitor.visit(this)
		override fun toString() = ExactMatchToken.lexeme
	}

	/**
	 * An [ExpressionVisitor] visits the desired [subexpressions][Expression] of
	 * some [expression][Expression], in an order of its own choosing. Because
	 * [Expression.accept] does not automatically visit subexpressions,
	 * implementors of [ExpressionVisitor] must choose which subexpressions to
	 * visit, in what order, and what action to take upon visitation.
	 *
	 * @author Todd L Smith &lt;todd@availlang.org&gt;
	 */
	private interface ExpressionVisitor
	{
		/**
		 * Visit a [MatchExpression].
		 *
		 * @param expression
		 *   The [MatchExpression].
		 */
		fun visit(expression: MatchExpression)

		/**
		 * Visit a [ExactMatchExpression].
		 *
		 * @param expression
		 *   The [ExactMatchExpression].
		 */
		fun visit(expression: ExactMatchExpression)

		/**
		 * Visit a [SuccessionExpression].
		 *
		 * @param expression
		 *   The [SuccessionExpression].
		 */
		fun visit(expression: SuccessionExpression)

		/**
		 * Visit a [SubsequenceExpression].
		 *
		 * @param expression
		 *   The [SubsequenceExpression].
		 */
		fun visit(expression: SubsequenceExpression)

		/**
		 * Visit a [ForkExpression].
		 *
		 * @param expression
		 *   The [ForkExpression].
		 */
		fun visit(expression: ForkExpression)

		/**
		 * Visit a [EmptyClassifierSequenceExpression].
		 *
		 * @param expression
		 *   The [EmptyClassifierSequenceExpression].
		 */
		fun visit(expression: EmptyClassifierSequenceExpression)
	}

	/** The outermost [expression][Expression] parsed from the [source]. */
	private val expression: Expression by lazy {
		val context = parseOutermost()
		assert(context.tokenIndex == tokens.size) {
			"parsing did not reach the end of the token stream"
		}
		val operands = context.operands
		assert(operands.size == 1) { "expected stack depth = 1" }
		operands.first()
	}

	/**
	 * Parse an outermost [expression][Expression].
	 *
	 * @return
	 *   The final [ParseContext].
	 */
	private fun parseOutermost(): ParseContext
	{
		val context = ParseContext()
		val next = when (val token = context.token)
		{
			is ExactMatchToken ->
			{
				val next = context.nextContext
				when (next.token)
				{
					is EndOfPatternToken -> context.with(
						EmptyClassifierSequenceExpression)
					else -> parseSuccession(next, allowSubsequence = false)
						.makeExactMatchExpression()
				}
			}
			is StyleClassifierToken -> parseSubsequence(context)
			else -> throw StylePatternException(
				context.position,
				"expected exact match pragma (${ExactMatchToken().lexeme}) or "
					+ "classifier (#…), but found $token")
		}
		if (next.token !is EndOfPatternToken)
		{
			throw StylePatternException(
				next.position,
				"expected end of pattern")
		}
		// Consume the end-of-pattern token. The caller expects the token stream
		// to be fully exhausted.
		return next.nextContext
	}

	/**
	 * Parse a [SubsequenceExpression], [SuccessionExpression], or
	 * [MatchExpression] at the specified [ParseContext].
	 *
	 * @param context
	 *   The initial context for the parse.
	 */
	private fun parseSubsequence(context: ParseContext): ParseContext
	{
		val next = when (val token = context.token)
		{
			is StyleClassifierToken -> parseSuccession(context)
			else -> throw StylePatternException(
				context.position,
				"expected classifier (#…), but found $token")
		}
		return when (val token = next.token)
		{
			is EndOfPatternToken -> next
			is SubsequenceToken -> parseSubsequence(next.nextContext)
				.makeSubsequenceExpression()
			else -> throw StylePatternException(
				next.position,
				"expected subsequence operator (<) or end of pattern, "
					+ "but found $token")
		}
	}

	/**
	 * Parse a [SuccessionExpression] or a [MatchExpression] at the specified
	 * [ParseContext].
	 *
	 * @param context
	 *   The initial context for the parse.
	 * @param leadingClassifier
	 *   The leading classifier of the succession, or `null` if this is the
	 *   opening parse of a new succession.
	 * @param allowSubsequence
	 *   Whether to permit the appearance of a [SubsequenceToken] as an
	 *   expression delimiter. When `false`, no [ForkExpression]s will be
	 *   generated.
	 */
	private fun parseSuccession(
		context: ParseContext,
		leadingClassifier: String? = null,
		allowSubsequence: Boolean = true
	): ParseContext
	{
		val next = when (val token = context.token)
		{
			is StyleClassifierToken -> context.makeMatchExpression(
				token.lexeme,
				if (allowSubsequence) leadingClassifier else null)
			else -> throw StylePatternException(
				context.position,
				"expected classifier (#…), but found $token")
		}
		val leading = leadingClassifier ?: context.token.lexeme
		return when (val token = next.token)
		{
			is EndOfPatternToken -> next
			is SuccessionToken ->
				parseSuccession(next.nextContext, leading, allowSubsequence)
					.makeSuccessionExpression()
			is SubsequenceToken ->
			{
				if (allowSubsequence) next
				else throw StylePatternException(
					next.position,
					"expected succession operator (,) or end of pattern, "
						+ "but found $token")
			}
			else -> throw StylePatternException(
				next.position,
				"expected succession operator (,), subsequence operator (<), "
					+ "or end of pattern, but found $token")
		}
	}

	/**
	 * A [CodeGenerator] produces a [rule][StyleRule] from an outermost
	 * [expression][Expression].
	 *
	 * @author Todd L Smith &lt;todd@availlang.org&gt;
	 */
	private inner class CodeGenerator: ExpressionVisitor
	{
		/**
		 * Whether or not to generate code performs exact matching of complete
		 * style classifier streams.
		 */
		var matchExactly = false

		/**
		 * The zero-based indices of the literal style classifiers, keyed by
		 * the classifiers themselves. The key set is required to preserve
		 * insertion order.
		 */
		val literals = mutableMapOf<String, Int>()

		/**
		 * Obtain the index of specified literal style classifier, first
		 * allocating a new index if necessary.
		 *
		 * @param literal
		 *   The literal.
		 * @return
		 *   The index of the specified literal.
		 */
		private fun literalIndex(literal: String) =
			literals.computeIfAbsent(literal) {
				literals.size
			}

		/**
		 * The program counter at the start of the next
		 * [instruction][StyleRuleInstruction] to [accumulate][accumulator].
		 */
		private val programCounter get() = accumulator.size

		/**
		 * The dynamically scoped branch-back target to use for failed
		 * [MatchLiteralClassifierOrJumpN]s generated by the function passed to
		 * [withTarget].
		 */
		private var target: Int? = null

		/**
		 * Set up the branch-back [target] for failed
		 * [MatchLiteralClassifierOrJumpN]s, but only if currently unset. Then
		 * perform the specified [action]. Clear the branch-back target if it
		 * was clear when the call started.
		 *
		 * @param action
		 *   The action to perform, using the stored target.
		 */
		private fun withTarget(action: ()->Unit)
		{
			val shouldClearTarget = target === null
			if (shouldClearTarget) target = programCounter
			action()
			if (shouldClearTarget) target = null
		}

		/**
		 * The accumulator for coded [instructions][StyleRuleInstruction],
		 * populated by the various implementations of [visit].
		 */
		private val accumulator = NybbleOutputStream(16)

		/** The complete coded [instruction][StyleRuleInstruction] stream. */
		val instructions: NybbleArray by lazy {
			expression.accept(this)
			accumulator.toNybbleArray()
		}

		override fun visit(expression: MatchExpression)
		{
			if (matchExactly)
			{
				when (val literalIndex = literalIndex(expression.classifier))
				{
					0 -> MatchLiteralClassifier0.emitOn(accumulator)
					1 -> MatchLiteralClassifier1.emitOn(accumulator)
					2 -> MatchLiteralClassifier2.emitOn(accumulator)
					3 -> MatchLiteralClassifier3.emitOn(accumulator)
					else -> MatchLiteralClassifierN.emitOn(
						accumulator,
						literalIndex)
				}
			}
			else
			{
				withTarget {
					when (val literalIndex =
						literalIndex(expression.classifier))
					{
						0 -> MatchLiteralClassifierOrJump0.emitOn(
							accumulator,
							target!!)
						1 -> MatchLiteralClassifierOrJump1.emitOn(
							accumulator,
							target!!)
						2 -> MatchLiteralClassifierOrJump2.emitOn(
							accumulator,
							target!!)
						3 -> MatchLiteralClassifierOrJump3.emitOn(
							accumulator,
							target!!)
						else -> MatchLiteralClassifierOrJumpN.emitOn(
							accumulator,
							literalIndex,
							target!!)
					}
				}
			}
		}

		override fun visit(expression: ExactMatchExpression)
		{
			matchExactly = true
			expression.child.accept(this)
			// For correct operation, we need to explicitly match the
			// end-of-sequence classifier; otherwise, we will accept sequences
			// with acceptable prefixes that do not match in their totality.
			MatchEndOfSequence.emitOn(accumulator)
		}

		override fun visit(expression: SuccessionExpression)
		{
			withTarget {
				expression.left.accept(this)
				expression.right.accept(this)
			}
		}

		override fun visit(expression: SubsequenceExpression)
		{
			expression.left.accept(this)
			expression.right.accept(this)
		}

		override fun visit(expression: ForkExpression)
		{
			when (val target = target!!)
			{
				0 -> Fork.emitOn(accumulator)
				else -> ForkN.emitOn(accumulator, target)
			}
			expression.expression.accept(this)
		}

		override fun visit(expression: EmptyClassifierSequenceExpression)
		{
			// Don't emit any instructions. Only one rule can implement this
			// expression within an entire StyleRuleTree, and that rule is
			// stored and handled specially.
		}
	}

	/** The [rule][StyleRule] compiled from the [source]. */
	val rule: StyleRule by lazy {
		val codeGenerator = CodeGenerator()
		StyleRule(
			pattern.validate(palette),
			codeGenerator.instructions,
			codeGenerator.literals.keys.toList())
	}

	companion object
	{
		/**
		 * `true` iff the receiving code point is a valid style classifier
		 * character.
		 */
		private val Int.isNonLeadingClassifierCharacter: Boolean get() =
			when (this)
			{
				'-'.code -> true
				in '0'.code .. '9'.code -> true
				in 'A'.code .. 'Z'.code -> true
				in 'a'.code .. 'z'.code -> true
				else -> false
			}

		/**
		 * Compile the specified [pattern] into a [rule][StyleRule], using the
		 * supplied [palette][Palette] to resolve any symbolic colors to actual
		 * [colors][Color].
		 *
		 * @param pattern
		 *   The [unvalidated&#32;pattern][UnvalidatedStylePattern] to compile.
		 * @param palette
		 *   The palette for resolution of symbolic colors.
		 * @return
		 *   The compiled rule.
		 * @throws StylePatternException
		 *   If [pattern] could not be compiled for any reason.
		 * @throws RenderingContextValidationException
		 *   If [validation][UnvalidatedRenderingContext.validate] of
		 *   [pattern]'s [rendering&#32;context][RenderingContext] failed for
		 *   any reason.
		 */
		fun compile(pattern: UnvalidatedStylePattern, palette: Palette) =
			StylePatternCompiler(pattern, palette).rule
	}
}

/**
 * Raised when [compilation][StylePatternCompiler] of a [StylePattern] fails
 * for any reason.
 *
 * @property position
 *   The one-based character position at which the error was detected.
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @constructor
 *
 * Construct a [StylePatternException].
 *
 * @param position
 *   The one-based character position at which the error was detected.
 * @param problem
 *   A brief message about the error that occurred.
 * @param cause
 *   The causal exception, if any.
 */
class StylePatternException(
	val position: Int,
	problem: String,
	cause: Exception? = null
): Exception("pattern error @ character #$position: $problem", cause)

////////////////////////////////////////////////////////////////////////////////
//                                   Rules.                                   //
////////////////////////////////////////////////////////////////////////////////

/**
 * A [StyleRule] is a [pattern][ValidatedStylePattern]-matching program produced
 * by a [StylePatternCompiler]. A runtime [stylesheet][Stylesheet] aggregates
 * all rules that should be considered when determining how Avail source text
 * should be rendered.
 *
 * @property pattern
 *   The [validated&#32;pattern][ValidatedStylePattern].
 * @property instructions
 *   The [instructions][StyleRuleInstruction] that implement the
 *   [pattern][ValidatedStylePattern]-matching program.
 * @property literals
 *   The literal values recorded by the [compiler][StylePatternCompiler],
 *   corresponding to the fixed style classifiers embedded in the
 *   [pattern][ValidatedStylePattern]. Will be [interned][intern] prior to
 *   internal storage, to accelerate matching during
 *   [execution][StyleRuleExecutor.run].
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @constructor
 *
 * Construct a [StyleRule].
 *
 * @param pattern
 *   The [validated&#32;pattern][ValidatedStylePattern].
 * @param instructions
 *   The [instructions][StyleRuleInstruction] that implement the
 *   [pattern][ValidatedStylePattern]-matching program.
 * @param literals
 *   The literal values recorded by the [compiler][StylePatternCompiler],
 *   corresponding to the fixed style classifiers embedded in the
 *   [pattern][ValidatedStylePattern]. Will be [interned][intern] prior to
 *   internal storage, to accelerate matching during
 *   [execution][StyleRuleExecutor.run].
 */
class StyleRule constructor(
	val pattern: ValidatedStylePattern,
	val instructions: NybbleArray,
	literals: List<String>)
{
	/** The source text whence the rule was compiled. */
	val source get() = pattern.source

	/**
	 * The [validated&#32;rendering&#32;context][ValidatedRenderingContext] to
	 * apply when this rule is not obsoleted by a more specific rule during
	 * aggregate matching.
	 */
	val renderingContext get() = pattern.renderingContext

	/** The [interned][String.intern] literals. */
	private val literals = literals.map { it.intern() }

	/**
	 * Answer the literal value at the requested index. Should generally only be
	 * invoked by a [StyleRuleInstruction] during its
	 * [execution][StyleRuleExecutor.run].
	 *
	 * @param index
	 *   The index of the desired literal value.
	 * @return
	 *   The requested literal value.
	 * @throws IndexOutOfBoundsException
	 *   If [index] is out of bounds.
	 */
	fun literalAt(index: Int) = literals[index]

	/** The initial [StyleRuleContext] for running the receiver. */
	val initialContext get() = StyleRuleContext(this, 0)

	/**
	 * Determine the relative specificity of the receiver and the argument.
	 *
	 * @param other
	 *   The [rule][StyleRule] to check specificity against.
	 * @return
	 *   The relation of the two patterns with respect to specificity, as a
	 *   [lax&#32;signum][Math.signum] value, i.e., `<0` if the receiver is less
	 *   specific than the argument, `=0` if the receiver is equally specific as
	 *   the argument, and `>0` if the receiver is more specific than the
	 *   argument. The caller should not rely upon the exact values, only their
	 *   displacement from `0`.
	 * @see StylePattern.compareSpecificityTo
	 */
	fun compareSpecificityTo(other: StyleRule) =
		pattern.compareSpecificityTo(other.pattern)

	override fun toString() = buildString {
		append(source)
		append("\nnybblecodes:\n\t")
		if (instructions.size == 0)
		{
			append("[no instructions]")
		}
		else
		{
			append(instructions)
		}
		append("\ninstructions:")
		val reader = instructions.inputStream()
		if (reader.atEnd)
		{
			append("\n\t[no instructions]")
		}
		while (!reader.atEnd)
		{
			val programCounter = reader.position
			val instruction = decodeInstruction(reader)
			val assembly = instruction.toString()
			val annotated = assembly.replace(findLiteralIndex) {
				val literalIndex = it.groupValues[1].toInt()
				"#$literalIndex <${literalAt(literalIndex)}>"
			}
			append("\n\t@$programCounter: ")
			append(annotated)
		}
		append("\nrenderingContext:")
		val ugly = renderingContext.toString()
		val pretty = ugly
			.replace("StyleAttributes(", "\n\t")
			.replace("=", " = ")
			.replace(", ", "\n\t")
			.replace(")", "")
		append(pretty)
	}

	companion object
	{
		/**
		 * A [regular&#32;expression][Regex] to find a literal index in
		 * disassembly text.
		 */
		private val findLiteralIndex by lazy { "#(\\d+)".toRegex() }
	}
}

/**
 * A [StyleRuleInstructionCoder] implements a strategy for correlating
 * [instructions][StyleRuleInstruction] and their decoders. Subclasses
 * self-register simply by calling the superconstructor.
 *
 * @property opcode
 *   The opcode of the associated [instruction][StyleRuleInstruction].
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
@Suppress("LeakingThis")
sealed class StyleRuleInstructionCoder constructor(val opcode: Int)
{
	init
	{
		assert(!opcodes.contains(opcode)) {
			"opcode $opcode is already bound to ${opcodes[opcode]}"
		}
		opcodes[opcode] = this
	}

	/**
	 * Encode the associated [instruction][StyleRuleInstruction] onto the
	 * specified [NybbleOutputStream]. Subclasses should call the
	 * superimplementation to ensure that the [opcode] is correctly encoded.
	 * This protocol exists to permit the
	 * [code&#32;generator][StylePatternCompiler.CodeGenerator] to emit
	 * instructions without instantiating them.
	 *
	 * @param nybbles
	 *   The destination for the coded instruction.
	 * @param operands
	 *   The operands to emit.
	 */
	open fun emitOn(
		nybbles: NybbleOutputStream,
		vararg operands: Int)
	{
		nybbles.opcode(opcode)
		operands.forEach { nybbles.vlq(it) }
	}

	/**
	 * Decode the operands of the associated [instruction][StyleRuleInstruction]
	 * from the specified [NybbleInputStream]. Note that the [opcode] was just
	 * decoded from this same stream. This protocol exists primarily to support
	 * debugging, as [execution][StyleRuleExecutor] does not require reification
	 * of the instructions themselves.
	 *
	 * @param nybbles
	 *   The encoded instruction stream.
	 * @return
	 *   The decoded instruction.
	 */
	protected abstract fun decodeOperands(
		nybbles: NybbleInputStream
	): StyleRuleInstruction

	companion object
	{
		/**
		 * The registry of [opcodes][StyleRuleInstructionCoder], keyed by the
		 * opcode value.
		 */
		private val opcodes = mutableMapOf<Int, StyleRuleInstructionCoder>()

		/**
		 * Decode an [instruction][StyleRuleInstruction] from the specified
		 * [NybbleInputStream]. This protocol exists primarily to support
		 * debugging, as [execution][StyleRuleExecutor] does not require
		 * reification of the instructions themselves.
		 *
		 * @param nybbles
		 *   The encoded instruction stream.
		 * @return
		 *   The decoded instruction.
		 */
		fun decodeInstruction(nybbles: NybbleInputStream) =
			opcodes[nybbles.opcode()]!!.decodeOperands(nybbles)
	}
}

/**
 * A [StyleRuleInstruction] is an indivisible unit of behavior within a
 * [style&#32;rule][StyleRule]. Instructions are neither generated nor executed
 * in reified form; the class hierarchy exists only to support disassembly for
 * debugging and provide loci for documentation of behavior.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
sealed interface StyleRuleInstruction
{
	abstract override fun toString(): String
}

/**
 * Match a style classifier against literal `#0`. On success, fall through to
 * the next [instruction][StyleRuleInstruction] of the enclosing
 * [rule][StyleRule] and [pause][PAUSED]; on failure, [fail][REJECTED] the
 * enclosing [rule][StyleRule].
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object MatchLiteralClassifier0:
	StyleRuleInstructionCoder(0x0), StyleRuleInstruction
{
	override fun toString() = "match literal #0"

	override fun decodeOperands(nybbles: NybbleInputStream) =
		MatchLiteralClassifier0
}

/**
 * Match a style classifier against literal `#1`. On success, fall through to
 * the next [instruction][StyleRuleInstruction] of the enclosing
 * [rule][StyleRule] and [pause][PAUSED]; on failure, [fail][REJECTED] the
 * enclosing [rule][StyleRule].
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object MatchLiteralClassifier1:
	StyleRuleInstructionCoder(0x1), StyleRuleInstruction
{
	override fun toString() = "match literal #1"

	override fun decodeOperands(nybbles: NybbleInputStream) =
		MatchLiteralClassifier1
}

/**
 * Match a style classifier against literal `#2`. On success, fall through to
 * the next [instruction][StyleRuleInstruction] of the enclosing
 * [rule][StyleRule] and [pause][PAUSED]; on failure, [fail][REJECTED] the
 * enclosing [rule][StyleRule].
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object MatchLiteralClassifier2:
	StyleRuleInstructionCoder(0x2), StyleRuleInstruction
{
	override fun toString() = "match literal #2"

	override fun decodeOperands(nybbles: NybbleInputStream) =
		MatchLiteralClassifier2
}

/**
 * Match a style classifier against literal `#3`. On success, fall through to
 * the next [instruction][StyleRuleInstruction] of the enclosing
 * [rule][StyleRule] and [pause][PAUSED]; on failure, [fail][REJECTED] the
 * enclosing [rule][StyleRule].
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object MatchLiteralClassifier3:
	StyleRuleInstructionCoder(0x3), StyleRuleInstruction
{
	override fun toString() = "match literal #3"

	override fun decodeOperands(nybbles: NybbleInputStream) =
		MatchLiteralClassifier3
}

/**
 * Match a style classifier against literal `#N`, where `N` is supplied as an
 * immediate operand. On success, fall through to the next
 * [instruction][StyleRuleInstruction] of the enclosing [rule][StyleRule] and
 * [pause][PAUSED]; on failure, [fail][REJECTED] the enclosing
 * [rule][StyleRule]. Note that the encoding offsets the literal index by `-4`,
 * so this cannot be used to encode indices ≤ `4`.
 *
 * @property literalIndex
 *   The index of the literal style classifier to match.
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
class MatchLiteralClassifierN constructor(
	private val literalIndex: Int
): StyleRuleInstruction
{
	init
	{
		assert(literalIndex >= 4) { "literal index must be ≥ 4" }
	}

	override fun toString() = "match literal #$literalIndex"

	companion object : StyleRuleInstructionCoder(0x4)
	{
		override fun emitOn(nybbles: NybbleOutputStream, vararg operands: Int)
		{
			assert(operands[0] >= 4) { "literal index must be ≥ 4" }
			nybbles.opcode(opcode)
			nybbles.vlq(operands[0] - 4)
		}

		override fun decodeOperands(nybbles: NybbleInputStream) =
			MatchLiteralClassifierN(nybbles.unvlq() + 4)
	}
}

/**
 * Match a style classifier against literal `#0`. On success, fall through to
 * the next [instruction][StyleRuleInstruction] of the enclosing
 * [rule][StyleRule] and [pause][PAUSED]; on failure, jump to the target
 * instruction.
 *
 * @property jumpTarget
 *   The zero-based nybble offset of the target
 *   [instruction][StyleRuleInstruction]. The offset is relative to the start of
 *   the instruction stream.
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
class MatchLiteralClassifierOrJump0 constructor(
	private val jumpTarget: Int
): StyleRuleInstruction
{
	override fun toString() = "match literal #0 or jump to @$jumpTarget"

	companion object : StyleRuleInstructionCoder(0x5)
	{
		override fun decodeOperands(nybbles: NybbleInputStream) =
			MatchLiteralClassifierOrJump0(nybbles.unvlq())
	}
}

/**
 * Match a style classifier against literal `#1`. On success, fall through to
 * the next [instruction][StyleRuleInstruction] of the enclosing
 * [rule][StyleRule] and [pause][PAUSED]; on failure, jump to the target
 * instruction.
 *
 * @property jumpTarget
 *   The zero-based nybble offset of the target
 *   [instruction][StyleRuleInstruction]. The offset is relative to the start of
 *   the instruction stream.
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
class MatchLiteralClassifierOrJump1 constructor(
	private val jumpTarget: Int
): StyleRuleInstruction
{
	override fun toString() = "match literal #1 or jump to @$jumpTarget"

	companion object : StyleRuleInstructionCoder(0x6)
	{
		override fun decodeOperands(nybbles: NybbleInputStream) =
			MatchLiteralClassifierOrJump1(nybbles.unvlq())
	}
}

/**
 * Match a style classifier against literal `#2`. On success, fall through to
 * the next [instruction][StyleRuleInstruction] of the enclosing
 * [rule][StyleRule] and [pause][PAUSED]; on failure, jump to the target
 * instruction.
 *
 * @property jumpTarget
 *   The zero-based nybble offset of the target
 *   [instruction][StyleRuleInstruction]. The offset is relative to the start of
 *   the instruction stream.
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
class MatchLiteralClassifierOrJump2 constructor(
	private val jumpTarget: Int
): StyleRuleInstruction
{
	override fun toString() = "match literal #2 or jump to @$jumpTarget"

	companion object : StyleRuleInstructionCoder(0x7)
	{
		override fun decodeOperands(nybbles: NybbleInputStream) =
			MatchLiteralClassifierOrJump2(nybbles.unvlq())
	}
}

/**
 * Match a style classifier against literal `#3`. On success, fall through to
 * the next [instruction][StyleRuleInstruction] of the enclosing
 * [rule][StyleRule] and [pause][PAUSED]; on failure, jump to the target
 * instruction.
 *
 * @property jumpTarget
 *   The zero-based nybble offset of the target
 *   [instruction][StyleRuleInstruction]. The offset is relative to the start of
 *   the instruction stream.
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
class MatchLiteralClassifierOrJump3 constructor(
	private val jumpTarget: Int
): StyleRuleInstruction
{
	override fun toString() = "match literal #3 or jump to @$jumpTarget"

	companion object : StyleRuleInstructionCoder(0x8)
	{
		override fun decodeOperands(nybbles: NybbleInputStream) =
			MatchLiteralClassifierOrJump3(nybbles.unvlq())
	}
}

/**
 * Match a style classifier against literal `#N`, where `N` is supplied as an
 * immediate operand. On success, fall through to the next
 * [instruction][StyleRuleInstruction] of the enclosing [rule][StyleRule] and
 * [pause][PAUSED]; on failure, jump to the target instruction. Note that the
 * encoding offsets the literal index by `-4`, so this cannot be used to encode
 * indices ≤ `4`.
 *
 * @property literalIndex
 *   The index of the literal style classifier to match.
 * @property jumpTarget
 *   The zero-based nybble offset of the target
 *   [instruction][StyleRuleInstruction]. The offset is relative to the start of
 *   the instruction stream.
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
class MatchLiteralClassifierOrJumpN constructor(
	private val literalIndex: Int,
	private val jumpTarget: Int
): StyleRuleInstruction
{
	init
	{
		assert(literalIndex >= 4) { "literal index must be ≥ 4" }
	}

	override fun toString() =
		"match literal #$literalIndex or jump to @$jumpTarget"

	companion object : StyleRuleInstructionCoder(0x9)
	{
		override fun emitOn(nybbles: NybbleOutputStream, vararg operands: Int)
		{
			assert(operands[0] >= 4) { "literal index must be ≥ 4" }
			nybbles.opcode(opcode)
			nybbles.vlq(operands[0] - 4)
			nybbles.vlq(operands[1])
		}

		override fun decodeOperands(nybbles: NybbleInputStream) =
			MatchLiteralClassifierOrJumpN(nybbles.unvlq() + 4, nybbles.unvlq())
	}
}

/**
 * Unconditionally fork the current [context][StyleRuleContext], setting its
 * program counter to `0`. This supports matching of [rules][StyleRule] compiled
 * from [patterns][StylePattern] with repeated prefixes, and optimizes for the
 * case where the repetition occurs in the first succession. Always succeeds.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object Fork: StyleRuleInstructionCoder(0xA), StyleRuleInstruction
{
	override fun toString() = "fork to @0"
	override fun decodeOperands(nybbles: NybbleInputStream) = Fork
}

/**
 * Unconditionally fork the current [context][StyleRuleContext], setting its
 * program counter to `N`, where `N` is supplied as an immediate operand.. This
 * supports matching of [rules][StyleRule] compiled from
 * [patterns][StylePattern] with repeated prefixes, and optimizes for the case
 * where the repetition occurs in the first succession.
 *
 * @property forkTarget
 *   The zero-based nybble offset of the target
 *   [instruction][StyleRuleInstruction] for resumption by the new
 *   [context][StyleRuleContext]. The offset is relative to the start of the
 *   instruction stream.
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
class ForkN constructor(
	private val forkTarget: Int
): StyleRuleInstruction
{
	init
	{
		assert(forkTarget >= 1) { "fork target must be ≥ 1" }
	}

	override fun toString() = "fork to @$forkTarget"

	companion object : StyleRuleInstructionCoder(0xB)
	{
		override fun emitOn(nybbles: NybbleOutputStream, vararg operands: Int)
		{
			assert(operands[0] >= 1) { "fork target must be ≥ 1" }
			nybbles.opcode(opcode)
			nybbles.vlq(operands[0] - 1)
		}

		override fun decodeOperands(nybbles: NybbleInputStream) =
			ForkN(nybbles.unvlq() + 1)
	}
}

/**
 * Match a style classifier against the special end-of-sequence classifier,
 * represented by the empty string. On success, [succeed][ACCEPTED] the
 * enclosing rule; on failure, [fail][REJECTED] the enclosing [rule][StyleRule].
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object MatchEndOfSequence: StyleRuleInstructionCoder(0xC), StyleRuleInstruction
{
	override fun toString() = "match end of sequence"
	override fun decodeOperands(nybbles: NybbleInputStream) = MatchEndOfSequence
}

////////////////////////////////////////////////////////////////////////////////
//                                 Execution.                                 //
////////////////////////////////////////////////////////////////////////////////

/**
 * A [StyleRuleContext] represents the complete machine state of a running
 * [StyleRule].
 *
 * @property rule
 *   The [rule][StyleRule] that generated this context.
 * @property programCounter
 *   The zero-based nybble index of the next [instruction][StyleRuleInstruction]
 *   to execute from the [rule][StyleRule]. This is relative to the based of the
 *   coded instruction stream.
 * @property state
 *   The [execution&#32;state][StyleRuleContextState].
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
data class StyleRuleContext constructor(
	val rule: StyleRule,
	val programCounter: Int,
	val state: StyleRuleContextState = PAUSED)
{
	/**
	 * The literal style classifier that the [rule] will attempt to match given
	 * this [context][StyleRuleContext], or `null` if the
	 * [program&#32;counter][programCounter] is at the end of the
	 * [instruction][StyleRuleInstruction] [stream][StyleRule.instructions].
	 */
	val literal get(): String?
	{
		// Find the first instruction that matches a style classifier.
		val reader = rule.instructions.inputStream(programCounter)
		while (true)
		{
			when (val opcode = reader.read())
			{
				MatchLiteralClassifier0.opcode,
				MatchLiteralClassifier1.opcode,
				MatchLiteralClassifier2.opcode,
				MatchLiteralClassifier3.opcode ->
					return rule.literalAt(
						opcode - MatchLiteralClassifier0.opcode)
				MatchLiteralClassifierN.opcode ->
					return rule.literalAt(reader.unvlq() + 4)
				MatchLiteralClassifierOrJump0.opcode,
				MatchLiteralClassifierOrJump1.opcode,
				MatchLiteralClassifierOrJump2.opcode,
				MatchLiteralClassifierOrJump3.opcode ->
					return rule.literalAt(
						opcode - MatchLiteralClassifierOrJump0.opcode)
				MatchLiteralClassifierOrJumpN.opcode ->
					return rule.literalAt(reader.unvlq() + 4)
				Fork.opcode -> {}
				ForkN.opcode -> reader.unvlq()
				MatchEndOfSequence.opcode -> return endOfSequenceLiteral
				-1 -> return null
			}
		}
	}

	/**
	 * A transform of the receiver without any representational singularities.
	 * This simplifies detection of [successful][StyleRuleContextState.ACCEPTED]
	 * contexts.
	 */
	val normalized get() =
		when
		{
			state == REJECTED -> this
			programCounter == rule.instructions.size -> copy(state = ACCEPTED)
			else -> this
		}

	override fun toString() = "@{${rule.source} :: @$programCounter, $state}"
}

/**
 * The execution state of a [style&#32;rule][StyleRule].
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
enum class StyleRuleContextState
{
	/**
	 * The enclosing [context][StyleRuleContext] is paused, waiting for another
	 * style classifier to become available.
	 */
	PAUSED,

	/**
	 * The enclosing [context][StyleRuleContext] is currently running.
	 */
	RUNNING,

	/**
	 * The enclosing [context][StyleRuleContext] has accepted a style classifier
	 * sequence.
	 */
	ACCEPTED,

	/**
	 * The enclosing [context][StyleRuleContext] has rejected a style classifier
	 * sequence.
	 */
	REJECTED
}

/**
 * The [StyleRuleExecutor] is a stateless virtual machine that accepts a
 * [context][StyleRuleContext] and [executes][run] one or more instructions of
 * its [rule][StyleRule], returning control immediately upon detecting that a
 * derivative of the initial context has left the
 * [RUNNING]&nbsp;[state][StyleRuleContextState]. The executor uses a supplied
 * injector to feed [forked][ForkN] contexts back into the pool of pending
 * contexts. It should be run iteratively against a lineage of contexts and a
 * sequence of classifiers. The special [end-of-sequence][endOfSequenceLiteral]
 * classifier should terminate a sequence of classifiers (unless the rule
 * [rejects][REJECTED] the sequence preemptively).
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object StyleRuleExecutor
{
	/**
	 * Using the machine state recorded in the
	 * [initial&#32;context][initialContext], run the associated
	 * [rule][StyleRule] until leaves the [RUNNING]&nbsp;
	 * [state][StyleRuleContextState].
	 *
	 * @param initialContext
	 *   The initial [context][StyleRuleContext]. Must be in the [RUNNING]
	 *   state.
	 * @param classifier
	 *   The _[interned][String.intern]_ style classifier, which is possibly the
	 *   special [end-of-sequence&#32;classifier][endOfSequenceLiteral].
	 *   **Non-interned classifiers will not match literals correctly, because
	 *   identity checks, not value checks, are used for efficiency!**
	 * @param injector
	 *   How to inject a forked [context][StyleRuleContext] into the pool of
	 *   pending contexts.
	 * @return
	 *   The context after leaving the [RUNNING] state.
	 */
	fun run(
		initialContext: StyleRuleContext,
		classifier: String,
		injector: (StyleRuleContext) -> Unit
	): StyleRuleContext
	{
		assert(initialContext.state == RUNNING)
		val rule = initialContext.rule
		val reader = rule.instructions.inputStream(0)
		var context = initialContext
		while (context.state == RUNNING)
		{
			reader.goTo(context.programCounter)
			context = when (val opcode = reader.opcode())
			{
				MatchLiteralClassifier0.opcode,
						MatchLiteralClassifier1.opcode,
						MatchLiteralClassifier2.opcode,
						MatchLiteralClassifier3.opcode ->
					executeMatchLiteralClassifier(
						context,
						classifier,
						rule.literalAt(opcode - MatchLiteralClassifier0.opcode),
						reader.position)
				MatchLiteralClassifierN.opcode ->
					executeMatchLiteralClassifier(
						context,
						classifier,
						rule.literalAt(reader.unvlq() + 4),
						reader.position)
				MatchLiteralClassifierOrJump0.opcode,
						MatchLiteralClassifierOrJump1.opcode,
						MatchLiteralClassifierOrJump2.opcode,
						MatchLiteralClassifierOrJump3.opcode ->
					executeMatchLiteralClassifierOrJump(
						context,
						classifier,
						rule.literalAt(
							opcode - MatchLiteralClassifierOrJump0.opcode),
						reader.unvlq(),
						reader.position)
				MatchLiteralClassifierOrJumpN.opcode ->
					executeMatchLiteralClassifierOrJump(
						context,
						classifier,
						rule.literalAt(reader.unvlq() + 4),
						reader.unvlq(),
						reader.position)
				Fork.opcode ->
					executeFork(
						context,
						0,
						injector,
						reader.position)
				ForkN.opcode ->
					executeFork(
						context,
						reader.unvlq() + 1,
						injector,
						reader.position)
				MatchEndOfSequence.opcode ->
					executeMatchLiteralClassifier(
						context,
						classifier,
						endOfSequenceLiteral,
						reader.position)
				else -> throw IllegalStateException("invalid opcode: $opcode")
			}
		}
		return context
	}

	/**
	 * The special end-of-sequence literal, represented by the
	 * [interned][String.intern] empty string.
	 */
	val endOfSequenceLiteral = "".intern()

	/**
	 * Execute one of the [MatchLiteralClassifierN] family of instructions.
	 *
	 * @param context
	 *   The [context][StyleRuleContext] just prior to execution of the
	 *   instruction.
	 * @param classifier
	 *   The style classifier.
	 * @param literal
	 *   The literal style classifier to match against [classifier].
	 * @param programCounter
	 *   The program counter just after decoding the instruction.
	 * @return
	 *   The context just after execution of the instruction.
	 */
	private fun executeMatchLiteralClassifier(
		context: StyleRuleContext,
		classifier: String,
		literal: String,
		programCounter: Int
	) = context
		.copy(
			programCounter = programCounter,
			state = when (literal === classifier)
			{
				true -> PAUSED
				false -> REJECTED
			})
		.normalized

	/**
	 * Execute one of the [MatchLiteralClassifierOrJumpN] family of
	 * instructions. Note that failure leaves the returned context in the
	 * [RUNNING]&nbsp;[state][StyleRuleContextState], which is imperative for
	 * correctly matching certain rules and inputs, specifically when the
	 * rejected classifier happens to coincide with the rule's starting
	 * classifier.
	 *
	 * @param context
	 *   The [context][StyleRuleContext] just prior to execution of the
	 *   instruction.
	 * @param classifier
	 *   The style classifier.
	 * @param literal
	 *   The literal style classifier to match against [classifier].
	 * @param jumpTarget
	 *   The program counter of the jump target. The jump occurs only if the
	 *   match fails.
	 * @param programCounter
	 *   The program counter just after decoding the instruction.
	 * @return
	 *   The context just after execution of the instruction.
	 */
	private fun executeMatchLiteralClassifierOrJump(
		context: StyleRuleContext,
		classifier: String,
		literal: String,
		jumpTarget: Int,
		programCounter: Int
	) =
		when (literal === classifier)
		{
			true -> context.copy(
				programCounter = programCounter,
				state = PAUSED
			).normalized
			false -> context.copy(
				programCounter = jumpTarget,
				state = when (jumpTarget)
				{
					context.programCounter -> PAUSED
					else -> RUNNING
				}
			).normalized
		}

	/**
	 * Execute one of the [ForkN] family of instructions.
	 *
	 * @param context
	 *   The [context][StyleRuleContext] just prior to execution of the
	 *   instruction.
	 * @param forkTarget
	 *   The zero-based nybble offset of the target
	 *   [instruction][StyleRuleInstruction] for resumption by the forked
	 *   [context][StyleRuleContext]. The offset is relative to the start of the
	 *   instruction stream.
	 * @param programCounter
	 *   The program counter just after decoding the instruction.
	 * @return
	 *   The context just after execution of the instruction.
	 */
	private fun executeFork(
		context : StyleRuleContext,
		forkTarget: Int,
		injector: (StyleRuleContext) -> Unit,
		programCounter: Int
	): StyleRuleContext
	{
		injector(context.copy(programCounter = forkTarget).normalized)
		return context.copy(programCounter = programCounter).normalized
	}
}

////////////////////////////////////////////////////////////////////////////////
//                                 Rendering.                                 //
////////////////////////////////////////////////////////////////////////////////

/**
 * A [RenderingContext] expresses how to render a complete set of character
 * attributes to a contiguous region of text within an arbitrary
 * [StyledDocument].
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @constructor
 *
 * Construct a [RenderingContext] from the specified [StyleAttributes].
 *
 * @param attrs
 *   The partial [StyleAttributes]. Any missing aspects will be sensibly
 *   [defaulted][defaultAttributes].
 */
sealed class RenderingContext constructor(attrs: StyleAttributes)
{
	/**
	 * The complete [StyleAttributes] to use when rendering to a
	 * [StyledDocument]. Any attributes bound to `null` in the argument will be
	 * set to [default&#32;values][defaultAttributes].
	 */
	protected val attributes = StyleAttributes(
		fontFamily = attrs.fontFamily ?: defaultAttributes.fontFamily,
		foreground = attrs.foreground ?: defaultAttributes.foreground,
		background = attrs.background ?: defaultAttributes.background,
		bold = attrs.bold ?: defaultAttributes.bold,
		italic = attrs.italic ?: defaultAttributes.italic,
		underline = attrs.underline ?: defaultAttributes.underline,
		superscript = attrs.superscript ?: defaultAttributes.superscript,
		subscript = attrs.subscript ?: defaultAttributes.subscript,
		strikethrough =attrs.strikethrough ?: defaultAttributes.strikethrough)

	override fun equals(other: Any?): Boolean
	{
		// Note that this implementation suffices for all subclasses, since they
		// do not introduce additional state.
		if (this === other) return true
		if (javaClass != other?.javaClass) return false
		other as RenderingContext
		if (attributes != other.attributes) return false
		return true
	}

	override fun hashCode() = 13 + attributes.hashCode()

	override fun toString() = attributes.toString()

	companion object
	{
		/** The default [RenderingContext]. */
		private val defaultAttributes = StyleAttributes(
			fontFamily = "Monospaced",
			foreground = "baseCode",
			background = "codeBackground",
			bold = false,
			italic = false,
			underline = false,
			superscript = false,
			subscript = false,
			strikethrough = false
		)
	}
}

/**
 * An [UnvalidatedRenderingContext] not has yet been [validated][validate].
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @constructor
 *
 * Construct an [UnvalidatedRenderingContext] from the specified
 * [StyleAttributes].
 *
 * @param attrs
 *   The complete [StyleAttributes].
 */
@Suppress("EqualsOrHashCode")
class UnvalidatedRenderingContext constructor(
	attrs: StyleAttributes
): RenderingContext(attrs)
{
	/**
	 * Validate the [receiver][UnvalidatedRenderingContext] against the supplied
	 * [palette][Palette]. If validation succeeds, then answer a
	 * [ValidatedRenderingContext] that includes all attributes of the receiver.
	 *
	 * @param palette
	 *   The [Palette], for interpreting the
	 *   [foreground][StyleAttributes.foreground] and
	 *   [background][StyleAttributes.background] colors for text rendition.
	 * @return
	 *   The [ValidatedRenderingContext].
	 * @throws RenderingContextValidationException
	 *   If the palette is missing any referenced colors.
	 */
	fun validate(palette: Palette): ValidatedRenderingContext
	{
		palette.colors[attributes.foreground!!]
			?: throw RenderingContextValidationException(
				"palette missing foreground color: ${attributes.foreground}")
		palette.colors[attributes.background!!]
			?: throw RenderingContextValidationException(
				"palette missing background color: ${attributes.background}")
		return ValidatedRenderingContext(attributes, palette)
	}

	override fun hashCode() = 41 * super.hashCode()
}

/**
 * A [ValidatedRenderingContext] has complete [StyleAttributes] and has been
 * successfully validated against the [palette][Palette] used to construct it.
 * It is therefore ready to [render][renderTo] itself onto [StyledDocument]s.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @constructor
 *
 * Construct a [ValidatedRenderingContext] from the specified [StyleAttributes]
 * and [Palette]. The context remains valid so long as the system
 * [color&#32;mode][AvailWorkbench.darkMode] does not change.
 *
 * @param attrs
 *   The complete [StyleAttributes].
 * @param palette
 *   The [Palette], for interpreting the
 *   [foreground][StyleAttributes.foreground] and
 *   [background][StyleAttributes.background] colors for text rendition.
 */
@Suppress("EqualsOrHashCode")
class ValidatedRenderingContext constructor(
	attrs: StyleAttributes,
	palette: Palette
): RenderingContext(attrs)
{
	/**
	 * The [document&#32;style][StyledDocument] to use when rendering to a
	 * [StyledDocument]. its attributes are sourced from [attributes], which has
	 * been fully resolved along all rendering dimensions.
	 */
	private val documentStyle: Style by lazy {
		getDefaultStyleContext().NamedStyle(defaultDocumentStyle).apply {
			setFontFamily(this, attributes.fontFamily!!)
			setForeground(this, palette.colors[attributes.foreground])
			setBackground(this, palette.colors[attributes.background])
			setBold(this, attributes.bold!!)
			setItalic(this, attributes.italic!!)
			setUnderline(this, attributes.underline!!)
			setSuperscript(this, attributes.superscript!!)
			setSubscript(this, attributes.subscript!!)
			setStrikeThrough(this, attributes.strikethrough!!)
		}
	}

	/**
	 * Apply the [receiver][RenderingContext] to the specified [range] of the
	 * target [StyledDocument].
	 *
	 * @param document
	 *   The target [StyledDocument].
	 * @param range
	 *   The target one-based [range][IntRange] of characters within the
	 *   [document].
	 * @param replace
	 *   Indicates whether or not the previous attributes should be cleared
	 *   before the new attributes are set. If `true`, the operation will
	 *   replace the previous attributes entirely. If `false`, the new
	 *   attributes will be merged with the previous attributes.
	 */
	fun renderTo(
		document: StyledDocument,
		range: IntRange,
		replace: Boolean = true)
	{
		assert(SwingUtilities.isEventDispatchThread())
		document.setCharacterAttributes(
			range.first - 1,
			range.last - range.first + 1,
			documentStyle,
			replace)
	}

	override fun hashCode() = 31 * super.hashCode()

	companion object
	{
		/**
		 * The default [document&#32;style][Style], to serve as the parent for
		 * new document styles.
		 */
		private val defaultDocumentStyle: Style by lazy {
			getDefaultStyleContext().getStyle(StyleContext.DEFAULT_STYLE)
		}
	}
}

/**
 * The appropriate colors to select from the [receiver][Palette], depending on
 * whether the application is using [dark&#32;mode][AvailWorkbench.darkMode].
 */
val Palette.colors get() =
	if (AvailWorkbench.darkMode) darkColors else lightColors

/**
 * Raised when [rendering&#32;context][RenderingContext]
 * [validation][UnvalidatedRenderingContext.validate] fails for any reason.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
class RenderingContextValidationException(message: String): Exception(message)

////////////////////////////////////////////////////////////////////////////////
//                                  Coding.                                   //
////////////////////////////////////////////////////////////////////////////////

/**
 * Apply a variable-length universal coding strategy to the supplied value,
 * encoding it onto the receiver. The coding is not efficient for large
 * [instruction&#32;sets][StyleRuleInstruction], but is quite efficient for a
 * very small instruction set, i.e., fewer than 32 instructions.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @param value
 *   The value to encode.
 */
fun NybbleOutputStream.opcode(value: Int)
{
	assert(value >= 0)
	// This encoding is not efficient at all if the instruction set ever
	// grows large, but is quite efficient for small instruction sets.
	var residue = value
	while (residue >= 15)
	{
		write(15)
		residue -= 15
	}
	write(residue)
}

/**
 * Unapply the variable-length universal coding strategy applied by
 * [`opcode`][NybbleOutputStream.opcode] to decode a nonnegative integer from
 * the receiver. If the stored encoding does not denote a valid value, the
 * result is undefined, and the number of bytes consumed is also undefined.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @return
 *   The requested integer.
 */
fun NybbleInputStream.opcode(): Int
{
	var value = 0
	while (true)
	{
		val nybble = read()
		if (nybble == 15)
		{
			value += 15
		}
		else
		{
			value += nybble
			return value
		}
	}
}

/**
 * Apply a variable-length universal coding strategy to the supplied value,
 * encoding it onto the receiver using a nybble-based variant of MIDI VLQ. The
 * value must be non-negative.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @param value
 *   The value to encode.
 * @see <a href="https://en.wikipedia.org/wiki/Variable-length_quantity">
 *   Variable-length quantity</a>
 */
fun NybbleOutputStream.vlq(value: Int)
{
	assert (value >= 0)
	var residue = value
	while (residue >= 8)
	{
		val nybble = (residue and 0x07) or 0x08
		write(nybble)
		residue = residue ushr 3
	}
	write(residue)
}

/**
 * Unapply the variable-length universal coding strategy applied by
 * [`vlq`][NybbleOutputStream.vlq] to decode a nonnegative integer from the
 * receiver. If the stored encoding does not denote a valid value, the result is
 * undefined, and the number of bytes consumed is also undefined.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @return
 *   The requested integer.
 * @see <a href="https://en.wikipedia.org/wiki/Variable-length_quantity">
 *   Variable-length quantity</a>
 */
fun NybbleInputStream.unvlq(): Int
{
	var n = 0
	var k = 0
	while (true)
	{
		val nybble = read()
		when
		{
			nybble and 0x08 == 0x08 ->
			{
				n = n or ((nybble and 0x07) shl k)
				k += 3
			}
			else ->
			{
				// The MSB is clear, so we're done decoding.
				return n or (nybble shl k)
			}
		}
	}
}
