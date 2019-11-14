/*
 * MessageSplitter.kt
 * Copyright © 1993-2019, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * * Neither the name of the copyright holder nor the names of the contributors
 *   may be used to endorse or promote products derived from this software
 *   without specific prior written permission.
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

package com.avail.compiler.splitter

import com.avail.compiler.ParsingOperation
import com.avail.compiler.problems.CompilerDiagnostics
import com.avail.compiler.splitter.MessageSplitter.Metacharacter.*
import com.avail.compiler.splitter.MessageSplitter.Metacharacter.Companion.canBeBackQuoted
import com.avail.descriptor.*
import com.avail.descriptor.ObjectTupleDescriptor.tupleFromList
import com.avail.descriptor.SetDescriptor.set
import com.avail.descriptor.StringDescriptor.stringFrom
import com.avail.descriptor.TupleDescriptor.emptyTuple
import com.avail.descriptor.atoms.AtomDescriptor.*
import com.avail.descriptor.objects.A_BasicObject
import com.avail.descriptor.parsing.A_Phrase
import com.avail.descriptor.tuples.A_String
import com.avail.descriptor.tuples.A_Tuple
import com.avail.exceptions.AvailErrorCode
import com.avail.exceptions.AvailErrorCode.*
import com.avail.exceptions.MalformedMessageException
import com.avail.exceptions.SignatureException
import com.avail.utility.Casts.cast
import com.avail.utility.Locks.auto
import java.util.*
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantReadWriteLock

/**
 * `MessageSplitter` is used to split Avail message names into a sequence of
 * [instructions][ParsingOperation] that can be used directly for parsing.
 *
 * Message splitting occurs in two phases.  In the first setPhase, the message
 * is tokenized and parsed into an abstract [Expression] tree. In the second
 * setPhase, a [tuple type][TupleTypeDescriptor] of [phrase
 * types][PhraseTypeDescriptor] is supplied, and produces a tuple of
 * integer-encoded [ParsingOperation]s.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @constructor
 *
 * Construct a new `MessageSplitter`, parsing the provided message into token
 * strings and generating [parsing][ParsingOperation] for parsing occurrences of
 * this message.
 *
 * @param messageName
 *   An Avail [string][StringDescriptor] specifying the keywords and arguments
 *   of some message being defined.
 * @throws MalformedMessageException
 *         If the message name is malformed.
 */
class MessageSplitter
@Throws(MalformedMessageException::class) constructor(messageName: A_String)
{
	/**
	 * The Avail string to be parsed.
	 */
	val messageName: A_String

	/**
	 * The individual tokens ([strings][StringDescriptor])
	 * constituting the message.
	 *
	 *  * Alphanumerics are in runs, separated from other
	 *   alphanumerics by a single space.
	 *  * Operator characters are never beside spaces, and are always parsed as
	 *    individual tokens.
	 *  * [Open guillemet][Metacharacter.OPEN_GUILLEMET] («),
	 *    [double dagger][Metacharacter.DOUBLE_DAGGER] (‡), and [close
	 *    guillemet][Metacharacter.CLOSE_GUILLEMET] (») are used to indicate
	 *    repeated or optional substructures.
	 *  * The characters [octothorp][Metacharacter.OCTOTHORP]
	 *    (#) and [question mark][Metacharacter.QUESTION_MARK] (?) modify the
	 *    output of repeated substructures to produce either a count of the
	 *    repetitions or a boolean indicating whether an optional subexpression
	 *    (expecting no arguments) was present.
	 *  * Placing a [question mark][Metacharacter.QUESTION_MARK]
	 *    (?) after a group containing arguments but no [double
	 *    dagger][Metacharacter.DOUBLE_DAGGER] (‡) will limit the repetitions of
	 *    the group to at most one.  Although limiting the method definitions to
	 *    only accept 0..1 occurrences would accomplish the same grammatical
	 *    narrowing, the parser might still attempt to parse more than one
	 *    occurrence, leading to unnecessarily confusing diagnostics.
	 *  * An [exclamation mark][Metacharacter.EXCLAMATION_MARK] (!) can follow a
	 *    group of alternations to produce the 1-based index of the alternative
	 *    that actually occurred.
	 *  * An [underscore][Metacharacter.UNDERSCORE] (_) indicates where an
	 *    argument occurs.
	 *  * A [single dagger][Metacharacter.SINGLE_DAGGER] (†) may occur
	 *    immediately after an underscore to cause the argument expression to be
	 *    evaluated in the static scope during compilation, wrapping the value
	 *    in a [literal phrase][LiteralPhraseDescriptor].  This is applicable to
	 *    both methods and macros.  The expression is subject to grammatical
	 *    restrictions, and it must yield a value of suitable type.
	 *  * An [up-arrow][Metacharacter.UP_ARROW] (↑) after an underscore
	 *    indicates an in-scope variable name is to be parsed.  The
	 *    subexpression causes the variable itself to be provided, rather than
	 *    its value.
	 *  * An [exclamation mark][Metacharacter.EXCLAMATION_MARK] (!) may occur
	 *    after the underscore instead, to indicate the argument expression may
	 *    be ⊤-valued or ⊥-valued.  Since a function (and therefore a method
	 *    definition) cannot accept a ⊤-valued argument, this mechanism only
	 *    makes sense for macros, since macros bodies are passed phrases, which
	 *    may be typed as *yielding* a top-valued result.
	 *  * An [ELLIPSIS][Metacharacter.ELLIPSIS] (…) matches a single [keyword
	 *    token][TokenDescriptor].
	 *  * An [exclamation mark][Metacharacter.EXCLAMATION_MARK] (!) after an
	 *    ELLIPSIS indicates *any* token will be accepted at that position.
	 *  * An [OCTOTHORP][Metacharacter.OCTOTHORP] (#) after an ellipsis
	 *    indicates only a *literal* token will be accepted.
	 *  * The N<sup>th</sup> [section][Metacharacter.SECTION_SIGN] (§) in a
	 *    message name indicates where a macro's N<sup>th</sup> [prefix
	 *    function][A_Definition.prefixFunctions] should be invoked with the
	 *    current parse stack up to that point.
	 *  * A [backquote][Metacharacter.BACK_QUOTE] (`) can precede any operator
	 *    character, such as guillemets or double dagger, to ensure it is not
	 *    used in a special way. A backquote may also operate on another
	 *    backquote (to indicate that an actual backquote token will appear in a
	 *    call).
	 *
	 * @see .messagePartsTuple
	 */
	internal val messagePartsList: MutableList<A_String> = ArrayList(10)

	/**
	 * The sequence of strings constituting discrete tokens of the message name.
	 *
	 * @see .messagePartsList
	 */
	val messagePartsTuple: A_Tuple

	/**
	 * A collection of one-based positions in the original string, corresponding
	 * to the [messagePartsList] that have been extracted.
	 */
	private val messagePartPositions: MutableList<Int> = ArrayList(10)

	/** The current one-based parsing position in the list of tokens.  */
	private var messagePartPosition: Int = 0

	/**
	 * The number of underscores/ellipses present in the method name. This is
	 * not the same as the number of arguments that a method implementing this
	 * name would accept, as a top-level guillemet group with `N` recursively
	 * embedded underscores/ellipses is counted as `N`, not one.
	 *
	 * This count of underscores/ellipses is essential for expressing negative
	 * precedence rules in the presence of repeated arguments.  Also note that
	 * backquoted underscores are not counted, since they don't represent a
	 * position at which a subexpression must occur.  Similarly, backquoted
	 * ellipses are not a place where an arbitrary input token can go.
	 */
	var leafArgumentCount = 0
		private set

	/**
	 * The number of [SectionCheckpoint]s encountered so far.
	 */
	var numberOfSectionCheckpoints: Int = 0

	/** The top-most [sequence][Sequence].  */
	private val rootSequence: Sequence

	/**
	 * The metacharacters used in message names.
	 *
	 * @constructor
	 *
	 * Initialize an enum instance.
	 *
	 * @param javaString
	 *        The Java [String] denoting the metacharacter.
	 */
	enum class Metacharacter constructor(javaString: String)
	{
		/**
		 * A back-quote (`) precedes a metacharacter to cause it to be treated
		 * as an ordinary character.  Since no metacharacters are alphanumeric,
		 * and since non-alphanumeric characters are always tokenized
		 * individually, back-quote only operates on the single character
		 * following it.  Note that since back-quote is itself a metacharacter,
		 * two successive backquotes indicates a single back-quote should be
		 * parsed from the Avail source code.
		 *
		 * The only characters that can be backquoted (and must be to use
		 * them in a non-special way) are the `Metacharacter`s, space, and
		 * the [circled numbers][circledNumbersString].
		 */
		BACK_QUOTE("`"),

		/**
		 * A close-guillemet (») indicates the end of a group or other structure
		 * that started with an [OPEN_GUILLEMET] open-guillemet («).
		 */
		CLOSE_GUILLEMET("»"),

		/**
		 * A dollar sign ($) after an [ELLIPSIS] (…) indicates that a
		 * string-valued literal token should be consumed from the Avail source
		 * code at this position.  This is accomplished through the use of a
		 * [RawStringLiteralTokenArgument].
		 */
		DOLLAR_SIGN("$"),

		/**
		 * The double-dagger (‡) is used within a [Group] to delimit the
		 * left part, which repeats, from the right part, which separates
		 * occurrences of the left part.
		 *
		 * The actual method argument (or portion of a method argument
		 * associated with this subexpression) must be able to accept some
		 * number of occurrences of tuples whose size is the total number of
		 * value-yielding subexpressions in the group, plus one final tuple that
		 * only has values yielded from subexpressions left of the
		 * double-dagger.  In the special, most common case of the left side
		 * having exactly one yielding expression, and the right side yielding
		 * nothing, the inner one-element tuples are elided.
		 */
		DOUBLE_DAGGER("‡"),

		/**
		 * The double-question-mark (⁇) is a single Unicode character (U+2047).
		 * It can be placed after a token or after a simple group (a group that
		 * has no right side and does not yield anything from its left side).
		 * In the case of a token, it allows zero or one occurrences of the
		 * token to occur in the Avail source, and *discards* information
		 * about whether the token was encountered.  Similarly, when applied to
		 * a simple group it accepts zero or one occurrences of the group's left
		 * side, discarding the distinction.  In both cases, the
		 * double-question-mark causes a [CompletelyOptional] to be built.
		 *
		 * This can be useful to make expressions read more fluidly by
		 * allowing inessential words to be elided when they would be
		 * grammatically awkward.
		 */
		DOUBLE_QUESTION_MARK("⁇"),

		/**
		 * An ellipsis (…) indicates that a single token should be consumed from
		 * the Avail source code (tokens are produced from the actual file
		 * content via [A_Lexer]s).  There are a few variants:
		 *
		 *  * If left unadorned, it creates a [RawKeywordTokenArgument], which
		 *    matches a single [A_Token] of kind [TokenType.KEYWORD].
		 *  * If followed by an [OCTOTHORP] (#), it creates a
		 *    [RawWholeNumberLiteralTokenArgument], which matches a single
		 *    [A_Token] of kind [TokenType.LITERAL] which yields an integer in
		 *    the range [0..∞).
		 *  * If followed by a [DOLLAR_SIGN] ($), it creates a
		 *    [RawStringLiteralTokenArgument], which matches a single [A_Token]
		 *    of kind [TokenType.LITERAL] which yields a string.
		 *  * If followed by an [EXCLAMATION_MARK] (!), it creates a
		 *    [RawTokenArgument], which matches a single [A_Token] of any kind
		 *    except [TokenType.WHITESPACE] or [TokenType.COMMENT].
		 */
		ELLIPSIS("…"),

		/**
		 * The exclamation mark (!) can follow an [ELLIPSIS] (…) to cause
		 * creation of a [RawTokenArgument], which matches any token that
		 * isn't whitespace or a comment.
		 *
		 * When it follows an [UNDERSCORE] (_), it creates an
		 * [ArgumentForMacroOnly], which matches any [A_Phrase], even those that
		 * yield ⊤ or ⊥.  Since these argument types are forbidden for methods,
		 * this construct can only be used in macros, where what is passed as an
		 * argument is a *phrase* yielding that type.
		 */
		EXCLAMATION_MARK("!"),

		/**
		 * An octothorp (#) after an [ELLIPSIS] (…) indicates that a
		 * whole-number-valued literal token should be consumed from the Avail
		 * source code at this position.  This is accomplished through the use
		 * of a [RawWholeNumberLiteralTokenArgument].
		 */
		OCTOTHORP("#"),

		/**
		 * An open-guillemet («) indicates the start of a group or other
		 * structure that will eventually end with a [CLOSE_GUILLEMET] (»).
		 */
		OPEN_GUILLEMET("«"),

		/**
		 * A question mark (?) may follow a [Simple] expression, which creates
		 * an [Optional], which yields a boolean literal indicating whether the
		 * token was present or not.
		 *
		 * When a question mark follows a simple [Group], i.e., one that has no
		 * [DOUBLE_DAGGER] (‡) and yields no values, it creates an [Optional].
		 * This causes a boolean literal to be generated at a call site,
		 * indicating whether the sequence of tokens from that group actually
		 * occurred.
		 *
		 * When a question mark follows a [Group] which produces one or more
		 * values, it simply limits that group to [0..1] occurrences.  The
		 * [DOUBLE_DAGGER] (‡) is forbidden here as well, because the sequence
		 * after the double-dagger would only ever occur between repetitions,
		 * but there cannot be two of them.
		 */
		QUESTION_MARK("?"),

		/**
		 * A section sign (§) indicates where, in the parsing of a macro
		 * invocation, it should invoke one of its [prefix
		 * functions][A_Definition.prefixFunctions].  The order of section signs
		 * in the method name corresponds with the order of the prefix
		 * functions.
		 *
		 * Prefix functions are passed all arguments that have been parsed so
		 * far.  They can access a fiber-specific variable,
		 * [SpecialAtom.CLIENT_DATA_GLOBAL_KEY], which allows manipulation of
		 * the current variables that are in scope, and also early parse
		 * rejection based on what has been parsed so far.
		 */
		SECTION_SIGN("§"),

		/**
		 * A single-dagger (†) following an [UNDERSCORE] (_) causes creation of
		 * an [ArgumentInModuleScope].  This will parse an expression that does
		 * not use any variables in the local scope, then evaluate it and wrap
		 * it in a [literal][LiteralTokenDescriptor].
		 */
		SINGLE_DAGGER("†"),

		/**
		 * An [Expression] followed by a tilde (~) causes all [Simple]
		 * expressions anywhere within it to match case-insensitively to the
		 * tokens lexed from the Avail source code.  All of these simple
		 * expressions are required to be specified in lower case to break a
		 * pointless symmetry about how to name the method.
		 */
		TILDE("~"),

		/**
		 * An underscore indicates where a subexpression should be parsed.
		 *
		 *  * If unadorned, an [Argument] is created, which expects an
		 *    expression that yields something other than ⊤ or ⊥.
		 *  * If followed by an [EXCLAMATION_MARK] (!), an
		 *    [ArgumentForMacroOnly] is constructed.  This allows expressions
		 *    that yield ⊤ or ⊥, which are only permitted in macros.
		 *  * If followed by a [SINGLE_DAGGER] (†), the expression must not
		 *    attempt to use any variables in the current scope. The expression
		 *    will be evaluated at compile time, and wrapped in a literal
		 *    phrase.
		 *  * If followed by a [UP_ARROW] (↑), a [variable use
		 *    phrase][VariableUsePhraseDescriptor] must be supplied, which is
		 *    converted into a [reference phrase][ReferencePhraseDescriptor].
		 */
		UNDERSCORE("_"),

		/**
		 * If an up-arrow (↑) follows an [UNDERSCORE], a [VariableQuote] is
		 * created, which expects a [VariableUsePhraseDescriptor], which will be
		 * automatically converted into a [reference
		 * phrase][ReferencePhraseDescriptor].
		 */
		UP_ARROW("↑"),

		/**
		 * The vertical bar (|) separates alternatives within an [Alternation].
		 * The alternatives may be [Simple] expressions, or simple [Group]s
		 * (having no double-dagger, and yielding no argument values).
		 */
		VERTICAL_BAR("|");

		/** The Avail [A_String] denoting this metacharacter.  */
		val string: A_String

		init
		{
			string = stringFrom(javaString).makeShared()
			assert(string.tupleSize() == 1)
		}

		companion object
		{
			/**
			 * This collects all metacharacters, including space and circled
			 * numbers.  These are the characters that can be [BACK_QUOTE]d.
			 */
			private val backquotableCodepoints = HashSet<Int>()

			// Load the set with metacharacters, space, and circled numbers.
			init
			{
				backquotableCodepoints.add(' '.toInt())
				backquotableCodepoints.addAll(circledNumbersMap.keys)
				for (metacharacter in values())
				{
					backquotableCodepoints.add(
						metacharacter.string.tupleCodePointAt(1))
				}
			}

			/**
			 * Answer whether the given Unicode codepoint may be
			 * [backquoted][BACK_QUOTE]
			 *
			 * @param codePoint
			 *   The Unicode codepoint to check.
			 * @return
			 *   Whether the character can be backquoted in a method name.
			 */
			fun canBeBackQuoted(codePoint: Int) =
				backquotableCodepoints.contains(codePoint)
		}
	}

	init
	{
		this.messageName = messageName.makeShared()
		try
		{
			splitMessage()
			messagePartPosition = 1
			rootSequence = parseSequence()

			if (!atEnd)
			{
				peekFor(
					CLOSE_GUILLEMET,
					true,
					E_UNBALANCED_GUILLEMETS,
					"close guillemet (») with no corresponding " +
						"open guillemet («)")
				peekFor(
					DOUBLE_DAGGER,
					true,
					E_UNBALANCED_GUILLEMETS,
					"double-dagger (‡) outside of a group")
				throwMalformedMessageException(
					E_UNBALANCED_GUILLEMETS,
					"Encountered unexpected character: $currentMessagePart")
			}
			messagePartsTuple = tupleFromList(messagePartsList).makeShared()
		}
		catch (e: MalformedMessageException)
		{
			// Add contextual text and rethrow it.
			throw MalformedMessageException(e.errorCode)
			{
				val builder = StringBuilder()
				builder.append(e.describeProblem())
				builder.append(". See arrow (")
				builder.append(CompilerDiagnostics.errorIndicatorSymbol)
				builder.append(") in: \"")
				val characterIndex =
					if (messagePartPosition > 0)
						if (messagePartPosition <= messagePartPositions.size)
							messagePartPositions[messagePartPosition - 1]
						else
							messageName.tupleSize() + 1
					else
						0
				val before = messageName.copyStringFromToCanDestroy(
					1, characterIndex - 1, false)
				val after = messageName.copyStringFromToCanDestroy(
					characterIndex, messageName.tupleSize(), false)
				builder.append(before.asNativeString())
				builder.append(CompilerDiagnostics.errorIndicatorSymbol)
				builder.append(after.asNativeString())
				builder.append('"')
				builder.toString()
			}
		}

	}

	/**
	 * Dump debugging information about this `MessageSplitter` to the
	 * specified [builder][StringBuilder].
	 *
	 * @param builder
	 *   The accumulator.
	 */
	private fun dumpForDebug(builder: StringBuilder)
	{
		builder.append(messageName.asNativeString())
		builder.append("\n------\n")
		for (part in messagePartsList)
		{
			builder.append('\t')
			builder.append(part.asNativeString())
			builder.append('\n')
		}
	}

	/**
	 * Answer the 1-based position of the current message part in the message
	 * name.
	 *
	 * @return
	 *   The current 1-based position in the message name.
	 */
	private val positionInName get() =
		if (atEnd)
		{
			messageName.tupleSize()
		}
		else messagePartPositions[messagePartPosition - 1]

	/**
	 * Answer whether parsing has reached the end of the message parts.
	 *
	 * @return
	 *   `true` if the current position has consumed the last message part.
	 */
	private val atEnd get() = messagePartPosition > messagePartsList.size

	/**
	 * Answer the current message part, or `null` if we are [atEnd].  Do not
	 * consume the message part.
	 *
	 * @return
	 *   The current message part or `null`.
	 */
	private val currentMessagePartOrNull get() =
		if (atEnd) null else messagePartsList[messagePartPosition - 1]

	/**
	 * Answer the current message part.  We must not be [atEnd].  Do
	 * not consume the message part.
	 *
	 * @return
	 *   The current message part.
	 */
	private val currentMessagePart get(): A_String
	{
		assert(!atEnd)
		return messagePartsList[messagePartPosition - 1]
	}

	/**
	 * Pretty-print a send of this message with given argument phrases.
	 *
	 * @param sendPhrase
	 *   The [send phrase][SendPhraseDescriptor] that is being printed.
	 * @param builder
	 *   A [StringBuilder] on which to pretty-print the send of my message with
	 *   the given arguments.
	 * @param indent
	 *   The current indentation level.
	 */
	fun printSendNodeOnIndent(
		sendPhrase: A_Phrase,
		builder: StringBuilder,
		indent: Int)
	{
		builder.append('«')
		rootSequence.printWithArguments(
			sendPhrase.argumentsListNode().expressionsTuple().iterator(),
			builder,
			indent)
		builder.append('»')
	}

	/**
	 * Answer a [tuple][TupleDescriptor] of Avail [integers][IntegerDescriptor]
	 * describing how to parse this message. See `MessageSplitter` and
	 * [ParsingOperation] for an understanding of the parse instructions.
	 *
	 * @param phraseType
	 *   The phrase type (yielding a tuple type) for this signature.
	 * @return
	 *   The tuple of integers encoding parse instructions for this message and
	 *   argument types.
	 */
	fun instructionsTupleFor(phraseType: A_Type): A_Tuple
	{
		val generator = InstructionGenerator()
		rootSequence.emitOn(phraseType, generator, WrapState.PUSHED_LIST)
		generator.optimizeInstructions()
		return generator.instructionsTuple()
	}

	/**
	 * Answer a [List] of [Expression] objects that correlates with the [parsing
	 * instructions][instructionsTupleFor] generated for the message name and
	 * the provided signature tuple type. Note that the list is 0-based and the
	 * tuple is 1-based.
	 *
	 * @param phraseType
	 *   The phrase type (yielding a tuple type) for this signature.
	 * @return
	 *   A list that indicates the origin [Expression] of each
	 *   [ParsingOperation].
	 */
	private fun originExpressionsFor(phraseType: A_Type): List<Expression>
	{
		val generator = InstructionGenerator()
		rootSequence.emitOn(phraseType, generator, WrapState.PUSHED_LIST)
		generator.optimizeInstructions()
		val expressions = generator.expressionList()
		assert(expressions.size == generator.instructionsTuple().tupleSize())
		return expressions
	}

	/**
	 * Answer a [String] containing the message name with an indicator inserted
	 * to show which area of the message is being parsed by the given program
	 * counter.
	 *
	 * @param phraseType
	 *   The phrase type (yielding a tuple type) for this signature.
	 * @param pc
	 *   The program counter for which an appropriate annotation should be
	 *   inserted into the name.  It's between 1 and the number of instructions
	 *   generated plus one.
	 * @return The annotated message string.
	 */
	fun highlightedNameFor(phraseType: A_Type, pc: Int): String
	{
		val string = messageName.asNativeString()
		val expressions = originExpressionsFor(phraseType)
		val zeroBasedPosition: Int
		zeroBasedPosition =
			if (pc == expressions.size + 1)
			{
				string.length
			}
			else
			{
				val expression = expressions[pc - 1]
				expression.positionInName - 1
			}
		val annotatedString = (string.substring(0, zeroBasedPosition)
							   + CompilerDiagnostics.errorIndicatorSymbol
							   + string.substring(zeroBasedPosition))
		return stringFrom(annotatedString).toString()
	}

	/**
	 * Answer a variant of the [message name][messageName] with backquotes
	 * stripped.
	 *
	 * @param start
	 *   The start position, inclusive.
	 * @param limit
	 *   The end position, exclusive.
	 * @return
	 *   The variant.
	 */
	private fun stripBackquotes(start:Int, limit: Int): String
	{
		val builder = StringBuilder()
		for (i in start until limit)
		{
			val cp = messageName.tupleCodePointAt(i)
			if (cp != '`'.toInt())
			{
				builder.appendCodePoint(cp)
			}
		}
		return builder.toString()
	}

	/**
	 * Decompose the message name into its constituent token strings. These can
	 * be subsequently parsed to generate the actual parse instructions. Do not
	 * do any semantic analysis here, not even backquote processing – that would
	 * lead to confusion over whether an operator was supposed to be treated as
	 * a special token like open-guillemet («) rather than like a
	 * backquote-escaped open-guillemet token.
	 *
	 * @throws MalformedMessageException
	 *   If the signature is invalid.
	 */
	@Throws(MalformedMessageException::class)
	private fun splitMessage()
	{
		if (messageName.tupleSize() == 0)
		{
			return
		}
		var position = 1
		while (position <= messageName.tupleSize())
		{
			val ch = messageName.tupleCodePointAt(position)
			when
			{
				ch == ' '.toInt() ->
				{
					if (messagePartsList.size == 0 ||
							isUnderscoreOrSpaceOrOperator(
								messageName.tupleCodePointAt(position - 1)))
					{
						// Problem is before the space.  Stuff the rest of the
						// input in as a final token to make diagnostics look
						// right.
						messagePartsList.add(
							messageName.copyStringFromToCanDestroy(
								position, messageName.tupleSize(), false))
						messagePartPositions.add(position)
						messagePartPosition = messagePartsList.size - 1
						throwMalformedMessageException(
							E_METHOD_NAME_IS_NOT_CANONICAL,
							"Expected alphanumeric character before space")
					}
					//  Skip the space.
					position++
					if (position > messageName.tupleSize()
						|| isUnderscoreOrSpaceOrOperator(
							messageName.tupleCodePointAt(position)))
					{
						if (messageName.tupleCodePointAt(position).toChar()!='`'
							|| position == messageName.tupleSize()
							|| messageName.tupleCodePointAt(position + 1)
							!= '_'.toInt())
						{
							// Problem is after the space.
							messagePartsList.add(
								messageName.copyStringFromToCanDestroy(
									position, messageName.tupleSize(), false))
							messagePartPositions.add(position)
							messagePartPosition = messagePartsList.size
							throwMalformedMessageException(
								E_METHOD_NAME_IS_NOT_CANONICAL,
								"Expected alphanumeric character after space")
						}
						// This is legal; we want to be able to parse
						// expressions like "a _b".
					}
				}
				ch == '`'.toInt() ->
					// Despite what the method comment says, backquote needs to
					// be processed specially when followed by an underscore so
					// that identifiers containing (escaped) underscores can be
					// treated as a single token. Otherwise, they are
					// unparseable.
					if (position == messageName.tupleSize()
							|| messageName.tupleCodePointAt(
								position + 1) != '_'.toInt())
					{
						// We didn't find an underscore, so we need to deal with
						// the backquote in the usual way.
						messagePartsList.add(
							messageName.copyStringFromToCanDestroy(
								position, position, false))
						messagePartPositions.add(position)
						position++
					}
					else
					{
						var sawRegular = false
						val start = position
						loop@ while (position <= messageName.tupleSize())
						{
							when
							{
								!isUnderscoreOrSpaceOrOperator(
									messageName.tupleCodePointAt(position)) ->
								{
									sawRegular = true
									position++
								}
								messageName.tupleCodePointAt(position)
										== '`'.toInt()
									&& position + 1 <= messageName.tupleSize()
									&& messageName.tupleCodePointAt(
											position + 1)
										== '_'.toInt() -> position += 2
								else -> break@loop
							}
						}
						if (sawRegular)
						{
							// If we ever saw something other than `_ in the
							// sequence, then produce a single token that
							// includes the underscores (but not the
							// backquotes).
							messagePartsList.add(stringFrom(
								stripBackquotes(start, position)))
							messagePartPositions.add(position)
						}
						else
						{
							// If we never saw a regular character, then produce
							// a token for each character.
							var i = start
							val limit = position - 1
							while (i <= limit)
							{
								messagePartsList.add(
									messageName.copyStringFromToCanDestroy(
										i, i, false))
								messagePartPositions.add(i)
								i++
							}
						}
					}
				isUnderscoreOrSpaceOrOperator(ch) ->
				{
					messagePartsList.add(
						messageName.copyStringFromToCanDestroy(
							position, position, false))
					messagePartPositions.add(position)
					position++
				}
				else ->
				{
					messagePartPositions.add(position)
					var sawIdentifierUnderscore = false
					val start = position
					loop@ while (position <= messageName.tupleSize())
					{
						when
						{
							!isUnderscoreOrSpaceOrOperator(
									messageName.tupleCodePointAt(position))
								-> position++
							messageName.tupleCodePointAt(position)
									== '`'.toInt()
								&& position + 1 <= messageName.tupleSize()
								&& messageName.tupleCodePointAt(position + 1)
									== '_'.toInt() ->
							{
								sawIdentifierUnderscore = true
								position += 2
							}
							else -> break@loop
						}
					}
					if (sawIdentifierUnderscore)
					{
						messagePartsList.add(stringFrom(
							stripBackquotes(start, position)))
					}
					else
					{
						messagePartsList.add(
							messageName.copyStringFromToCanDestroy(
								start, position - 1, false))
					}
				}
			}
		}
	}

	/**
	 * Check if there are more parts and the next part is an occurrence of the
	 * given [Metacharacter].  If so, increment the [messagePartPosition] and
	 * answer `true`}`, otherwise answer `false`.
	 *
	 * @param metacharacter
	 *   The [Metacharacter] to look for.
	 * @return
	 *   Whether the given metacharacter was found and consumed.
	 */
	private fun peekFor(metacharacter: Metacharacter): Boolean
	{
		val token = currentMessagePartOrNull
		if (token !== null && token.equals(metacharacter.string))
		{
			messagePartPosition++
			return true
		}
		return false
	}

	/**
	 * Check if the next part, if any, is the indicated [Metacharacter].
	 * Do not advance the [messagePartPosition] in either case.
	 *
	 * @param metacharacter
	 *   The [Metacharacter] to look ahead for.
	 * @return
	 *   Whether the given metacharacter is the next token.
	 */
	private fun peekAheadFor(metacharacter: Metacharacter): Boolean
	{
		val token = currentMessagePartOrNull
		return token !== null && token.equals(metacharacter.string)
	}

	/**
	 * Check for an occurrence of a circled number, signifying an explicit
	 * renumbering of arguments within a [Sequence].  Answer the contained
	 * number, or -1 if not present.  If it was present, also advance past it.
	 *
	 * @return
	 *   The value of the parsed explicit ordinal, or -1 if there was none.
	 */
	private fun peekForExplicitOrdinal(): Int
	{
		if (!atEnd)
		{
			val codePoint = currentMessagePart.tupleCodePointAt(1)
			if (circledNumbersMap.containsKey(codePoint))
			{
				// In theory we could allow messages to go past ㊿ by
				// allowing a sequence of circled single digits (⓪-⑨)
				// that doesn't start with ⓪.  DEFINITELY not worth the
				// bother for now (2014.12.24).
				messagePartPosition++
				return circledNumbersMap[codePoint]!!
			}
		}
		return -1
	}

	/**
	 * Check if the next token is the given metacharacter.  If it is not, return
	 * `false`.  If it is, consume it and check the failureCondition.  If it's
	 * true, throw a [MalformedMessageException] with the given errorCode and
	 * errorString.  Otherwise return `true`.
	 *
	 * @param metacharacter
	 *   The [Metacharacter] to look for.
	 * @param failureCondition
	 *   The [Boolean] to test if the metacharacter was consumed.
	 * @param errorCode
	 *   The error code to use in the [MalformedMessageException] if the
	 *   metacharacter is found and the condition is true.
	 * @param errorString
	 *   The errorString to use in the [MalformedMessageException] if the
	 *   metacharacter is found and the condition is true.
	 * @return
	 *   Whether the metacharacter was found and consumed.
	 * @throws MalformedMessageException
	 *   If the [Metacharacter] was found and the `failureCondition` was true.
	 */
	@Throws(MalformedMessageException::class)
	private fun peekFor(
		metacharacter: Metacharacter,
		failureCondition: Boolean,
		errorCode: AvailErrorCode,
		errorString: String): Boolean
	{
		val token = currentMessagePartOrNull
		if (token === null || !token.equals(metacharacter.string))
		{
			return false
		}
		throwMalformedIf(failureCondition, errorCode, errorString)
		messagePartPosition++
		return true
	}

	/**
	 * Create a [Sequence] from the tokenized message name.  Stop parsing the
	 * sequence when we reach the end of the tokens, a close guillemet (»), or a
	 * double-dagger (‡).
	 *
	 * @return
	 *   A [Sequence] expression parsed from the [messagePartsList].
	 * @throws MalformedMessageException
	 *   If the method name is malformed.
	 */
	@Throws(MalformedMessageException::class)
	private fun parseSequence(): Sequence
	{
		val sequence = Sequence(messagePartPosition)
		var expression = parseElementOrAlternation()
		while (expression !== null)
		{
			sequence.addExpression(expression)
			expression = parseElementOrAlternation()
		}
		sequence.checkForConsistentOrdinals()
		return sequence
	}

	/**
	 * Parse an element of a [Sequence].  The element may be an [Alternation].
	 *
	 * @return
	 *   The parsed [Expression], or `null` if no expression can be found.
	 * @throws MalformedMessageException
	 *   If the start of an [Expression] is found, but it's malformed.
	 */
	@Throws(MalformedMessageException::class)
	private fun parseElementOrAlternation(): Expression?
	{
		val firstExpression = parseElement() ?: return null
		!peekAheadFor(VERTICAL_BAR) && return firstExpression
		// It must be an alternation.
		checkAlternative(firstExpression)
		val alternatives = ArrayList<Expression>()
		alternatives.add(firstExpression)
		while (peekFor(VERTICAL_BAR))
		{
			val nextExpression = parseElement()
				?: throwMalformedMessageException(
					E_VERTICAL_BAR_MUST_SEPARATE_TOKENS_OR_SIMPLE_GROUPS,
					"Expecting another token or simple group after the " +
						"vertical bar (|)")
			checkAlternative(nextExpression)
			alternatives.add(nextExpression)
		}
		return Alternation(firstExpression.positionInName, alternatives)
	}

	/**
	 * Parse a single element or return `null`.
	 *
	 * @return
	 *   A parsed [Expression], which is suitable for use within an
	 *   [Alternation] or [Sequence], or `null` if none is available at this
	 *   position.
	 * @throws MalformedMessageException
	 *   If there appeared to be an element [Expression] at this position, but
	 *   it was malformed.
	 */
	@Throws(MalformedMessageException::class)
	private fun parseElement(): Expression?
	{
		// Try to parse the kinds of things that deal with their own suffixes.
		if (atEnd
			|| peekAheadFor(DOUBLE_DAGGER)
			|| peekAheadFor(CLOSE_GUILLEMET))
		{
			return null
		}
		if (peekFor(UNDERSCORE))
		{
			return parseOptionalExplicitOrdinal(parseUnderscoreElement())
		}
		if (peekFor(ELLIPSIS))
		{
			return parseOptionalExplicitOrdinal(parseEllipsisElement())
		}
		if (peekFor(OPEN_GUILLEMET))
		{
			return parseOptionalExplicitOrdinal(parseGuillemetElement())
		}
		return if (peekFor(SECTION_SIGN))
		{
			SectionCheckpoint(positionInName, ++numberOfSectionCheckpoints)
		}
		else parseSimple()
	}

	/**
	 * Parse a [Simple] at the current position.  Don't look for any suffixes.
	 *
	 * @return
	 *   The [Simple].
	 * @throws MalformedMessageException
	 *   If the [Simple] expression is malformed.
	 */
	@Throws(MalformedMessageException::class)
	private fun parseSimple(): Expression
	{
		// First, parse the next token, then apply a double-question-mark,
		// tilde, and/or circled number.
		if (peekFor(
				BACK_QUOTE,
				atEnd,
				E_EXPECTED_OPERATOR_AFTER_BACKQUOTE,
				"Backquote (`) must be followed by a special " +
					"metacharacter, space, or circled number"))
		{
			val token = currentMessagePart
			// Expects metacharacter or space or circled number after backquote.
			throwMalformedIf(
				token.tupleSize() != 1 || !canBeBackQuoted(
					token.tupleCodePointAt(1)),
				E_EXPECTED_OPERATOR_AFTER_BACKQUOTE,
				"Backquote (`) must be followed by a special " +
					"metacharacter, space, or circled number, not ($token)")
		}
		else
		{
			// Parse a regular keyword or operator.
			checkSuffixCharactersNotInSuffix()
		}
		var expression: Expression = Simple(
			currentMessagePart,
			messagePartPosition,
			messagePartPositions[messagePartPosition - 1])
		messagePartPosition++
		if (peekFor(
				TILDE,
				!expression.isLowerCase,
				E_CASE_INSENSITIVE_EXPRESSION_CANONIZATION,
				"Tilde (~) may only occur after a lowercase token " +
					"or a group of lowercase tokens"))
		{
			expression = expression.applyCaseInsensitive()
		}
		if (peekFor(QUESTION_MARK))
		{
			val sequence = Sequence(expression.positionInName)
			sequence.addExpression(expression)
			expression = Optional(
				expression.positionInName,
				sequence)
		}
		else if (peekFor(DOUBLE_QUESTION_MARK))
		{
			val sequence = Sequence(expression.positionInName)
			sequence.addExpression(expression)
			expression = CompletelyOptional(
				expression.positionInName, sequence)
		}
		return expression
	}

	/**
	 * The provided sequence element or [Alternation] has just been parsed.
	 * Look for a suffixed ordinal indicator, marking the [Expression] with the
	 * explicit ordinal if indicated.
	 *
	 * @param expression
	 *   The [Expression] that was just parsed.
	 * @return
	 *   A replacement [Expression] with its explicit ordinal set if necessary.
	 *   This may be the original expression after mutation.
	 */
	private fun parseOptionalExplicitOrdinal(expression: Expression): Expression
	{
		val ordinal = peekForExplicitOrdinal()
		if (ordinal != -1)
		{
			expression.explicitOrdinal = ordinal
		}
		return expression
	}

	/**
	 * Check that the given token is not a special character that should only
	 * occur after an element or subgroup.  If it is such a special character,
	 * throw a [MalformedMessageException] with a suitable error code and
	 * message.
	 *
	 * @throws MalformedMessageException
	 *   If the token is special.
	 */
	@Throws(MalformedMessageException::class)
	private fun checkSuffixCharactersNotInSuffix()
	{
		val token = currentMessagePartOrNull ?: return
		if (circledNumbersMap.containsKey(token.tupleCodePointAt(1)))
		{
			throwMalformedMessageException(
				E_INCONSISTENT_ARGUMENT_REORDERING,
				"Unquoted circled numbers (⓪-㊿) may only follow an " +
					"argument, an ellipsis, or an argument group")
		}
		peekFor(
			OCTOTHORP,
			true,
			E_OCTOTHORP_MUST_FOLLOW_A_SIMPLE_GROUP_OR_ELLIPSIS,
			"An octothorp (#) may only follow a simple group («») " +
				"or an ellipsis (…)")
		peekFor(
			DOLLAR_SIGN,
			true,
			E_DOLLAR_SIGN_MUST_FOLLOW_AN_ELLIPSIS,
			"A dollar sign ($) may only follow an ellipsis(…)")
		peekFor(
			QUESTION_MARK,
			true,
			E_QUESTION_MARK_MUST_FOLLOW_A_SIMPLE_GROUP,
			"A question mark (?) may only follow a simple group " +
				"(optional) or a group with no double-dagger (‡)")
		peekFor(
			TILDE,
			true,
			E_CASE_INSENSITIVE_EXPRESSION_CANONIZATION,
			"Tilde (~) may only occur after a lowercase " +
				"token or a group of lowercase tokens")
		peekFor(
			VERTICAL_BAR,
			true,
			E_VERTICAL_BAR_MUST_SEPARATE_TOKENS_OR_SIMPLE_GROUPS,
			"A vertical bar (|) may only separate tokens or simple " +
				"groups")
		peekFor(
			EXCLAMATION_MARK,
			true,
			E_EXCLAMATION_MARK_MUST_FOLLOW_AN_ALTERNATION_GROUP,
			"An exclamation mark (!) may only follow an alternation " +
				"group (or follow an underscore for macros)")
		peekFor(
			UP_ARROW,
			true,
			E_UP_ARROW_MUST_FOLLOW_ARGUMENT,
			"An up-arrow (↑) may only follow an argument")
	}

	/**
	 * An open guillemet («) was detected in the input stream.  Parse a suitable
	 * group or alternation or any other kind of [Expression] that starts with
	 * an open guillemet.  Do not parse any trailing circled numbers, which are
	 * used to permute argument expressions within a group or the top level.
	 *
	 * @return
	 *   The [Expression] that started at the [Metacharacter.OPEN_GUILLEMET].
	 * @throws MalformedMessageException
	 *   If the subgroup is malformed.
	 */
	@Throws(MalformedMessageException::class)
	private fun parseGuillemetElement(): Expression
	{
		// We just parsed an open guillemet.  Parse a subgroup, eat the
		// mandatory close guillemet, and apply any modifiers to the group.
		val startOfGroup = positionInName
		val beforeDagger = parseSequence()
		val group: Group
		group =
			if (peekFor(DOUBLE_DAGGER))
			{
				val afterDagger = parseSequence()
				// Check for a second double-dagger.
				peekFor(
					DOUBLE_DAGGER,
					true,
					E_INCORRECT_USE_OF_DOUBLE_DAGGER,
					"A group must have at most one double-dagger (‡)")
				Group(startOfGroup, beforeDagger, afterDagger)
			}
			else
			{
				Group(startOfGroup, beforeDagger)
			}

		if (!peekFor(CLOSE_GUILLEMET))
		{
			// Expected matching close guillemet.
			throwMalformedMessageException(
				E_UNBALANCED_GUILLEMETS,
				"Expected close guillemet (») to end group")
		}
		var subexpression: Expression = group

		// Look for a case-sensitive, then look for a counter, optional, or
		// completely-optional.
		if (peekFor(
				TILDE,
				!subexpression.isLowerCase,
				E_CASE_INSENSITIVE_EXPRESSION_CANONIZATION,
				"Tilde (~) may only occur after a lowercase " +
					"token or a group of lowercase tokens"))
		{
			subexpression = CaseInsensitive(startOfGroup, subexpression)
		}

		if (peekFor(
				OCTOTHORP,
				group.underscoreCount > 0,
				E_OCTOTHORP_MUST_FOLLOW_A_SIMPLE_GROUP_OR_ELLIPSIS,
				"An octothorp (#) may only follow a non-yielding " +
					"group or an ellipsis (…)"))
		{
			subexpression = Counter(startOfGroup, group)
		}
		else if (peekFor(
				QUESTION_MARK,
				group.hasDagger,
				E_QUESTION_MARK_MUST_FOLLOW_A_SIMPLE_GROUP,
				"A question mark (?) may only follow a simple "
					+ "group (optional) or a group with arguments "
					+ "(0 or 1 occurrences), but not one with a "
					+ "double-dagger (‡), since that suggests "
					+ "multiple occurrences to be separated"))
		{
			if (group.underscoreCount > 0)
			{
				// A complex group just gets bounded to [0..1] occurrences.
				group.beOptional()
			}
			else
			{
				// A simple group turns into an Optional, which produces a
				// literal boolean indicating the presence of such a
				// subexpression.
				subexpression = Optional(startOfGroup, group.beforeDagger)
			}
		}
		else if (peekFor(
				DOUBLE_QUESTION_MARK,
				group.underscoreCount > 0 || group.hasDagger,
				E_DOUBLE_QUESTION_MARK_MUST_FOLLOW_A_TOKEN_OR_SIMPLE_GROUP,
				"A double question mark (⁇) may only follow "
					+ "a token or simple group, not one with a "
					+ "double-dagger (‡) or arguments"))
		{
			subexpression = CompletelyOptional(
				startOfGroup, group.beforeDagger)
		}
		else if (peekFor(
				EXCLAMATION_MARK,
				group.underscoreCount > 0
					|| group.hasDagger
					|| group.beforeDagger.expressions.size != 1
					|| group.beforeDagger.expressions[0] !is Alternation,
				E_EXCLAMATION_MARK_MUST_FOLLOW_AN_ALTERNATION_GROUP,
				"An exclamation mark (!) may only follow an "
					+ "alternation group or (for macros) an "
					+ "underscore"))
		{
			// The guillemet group should have had a single element, an
			// alternation.
			val alternation = cast<Expression, Alternation>(
				group.beforeDagger.expressions[0])
			subexpression = NumberedChoice(alternation)
		}
		return subexpression
	}

	/**
	 * Parse an ellipsis element, applying suffixed modifiers if present.  The
	 * ellipsis has already been consumed.
	 *
	 * @return
	 *   A newly parsed [RawTokenArgument] or subclass.
	 */
	private fun parseEllipsisElement(): Expression
	{
		val tokenStart = messagePartPositions[messagePartPosition - 2]
		incrementLeafArgumentCount()
		val absoluteUnderscoreIndex = leafArgumentCount
		if (peekFor(EXCLAMATION_MARK))
		{
			return RawTokenArgument(
				tokenStart, absoluteUnderscoreIndex)
		}
		if (peekFor(OCTOTHORP))
		{
			return RawWholeNumberLiteralTokenArgument(
				tokenStart, absoluteUnderscoreIndex)
		}
		return if (peekFor(DOLLAR_SIGN))
		{
			RawStringLiteralTokenArgument(
				tokenStart, absoluteUnderscoreIndex)
		}
		else RawKeywordTokenArgument(
			tokenStart,
			absoluteUnderscoreIndex)
	}

	/**
	 * Parse an underscore element, applying suffixed modifiers if present.  The
	 * underscore has already been consumed.
	 *
	 * @return
	 *   A newly parsed [Argument] or subclass.
	 */
	private fun parseUnderscoreElement(): Expression
	{
		// Capture the one-based index.
		val positionInName = messagePartPositions[messagePartPosition - 2]
		incrementLeafArgumentCount()
		val absoluteUnderscoreIndex = leafArgumentCount
		return when
		{
			peekFor(SINGLE_DAGGER) -> ArgumentInModuleScope(
				positionInName, absoluteUnderscoreIndex)
			peekFor(UP_ARROW) -> VariableQuote(
				positionInName, absoluteUnderscoreIndex)
			peekFor(EXCLAMATION_MARK) -> ArgumentForMacroOnly(
				positionInName, absoluteUnderscoreIndex)
			else -> Argument(positionInName, absoluteUnderscoreIndex)
		}
	}

	/**
	 * Increment the number of leaf arguments, which agrees with the number of
	 * non-backquoted underscores and ellipses.
	 */
	private fun incrementLeafArgumentCount()
	{
		leafArgumentCount++
	}

	/**
	 * Return the number of arguments a [MethodDefinitionDescriptor]
	 * implementing this name would accept.  Note that this is not necessarily
	 * the number of underscores and ellipses, as a guillemet group may contain
	 * zero or more underscores/ellipses (and other guillemet groups) but count
	 * as one top-level argument.
	 *
	 * @return
	 *   The number of arguments this message takes.
	 */
	val numberOfArguments get() = rootSequence.yielders.size

	/**
	 * Check that an [implementation][DefinitionDescriptor] with the given
	 * [signature][FunctionTypeDescriptor] is appropriate for a message like
	 * this.
	 *
	 * @param functionType
	 *   A function type.
	 * @param sectionNumber
	 *   The [SectionCheckpoint]'s subscript if this is a check of a
	 *   [macro][MacroDefinitionDescriptor]'s, [prefix
	 *   function][A_Definition.prefixFunctions], otherwise any value past the
	 *   total [numberOfSectionCheckpoints] for a method or macro body.
	 * @throws SignatureException
	 *         If the function type is inappropriate for the method name.
	 */
	@JvmOverloads
	@Throws(SignatureException::class)
	fun checkImplementationSignature(
		functionType: A_Type,
		sectionNumber: Int = Integer.MAX_VALUE)
	{
		val argsTupleType = functionType.argsTupleType()
		val sizes = argsTupleType.sizeRange()
		val lowerBound = sizes.lowerBound()
		val upperBound = sizes.upperBound()
		if (!lowerBound.equals(upperBound) || !lowerBound.isInt)
		{
			// Method definitions (and other definitions) should take a
			// definite number of arguments.
			throwSignatureException(E_INCORRECT_NUMBER_OF_ARGUMENTS)
		}
		val lowerBoundInt = lowerBound.extractInt()
		if (lowerBoundInt != numberOfArguments)
		{
			throwSignatureException(E_INCORRECT_NUMBER_OF_ARGUMENTS)
		}
		rootSequence.checkRootType(functionType.argsTupleType(), sectionNumber)
	}

	/**
	 * Does the message contain any groups?
	 *
	 * @return
	 *   `true` if the message contains any groups, `false` otherwise.
	 */
	val containsGroups get (): Boolean
	{
		for (expression in rootSequence.expressions)
		{
			if (expression.isGroup)
			{
				return true
			}
		}
		return false
	}

	override fun toString(): String
	{
		val builder = StringBuilder()
		dumpForDebug(builder)
		return builder.toString()
	}

	companion object
	{
		/**
		 * The [set][A_Set] of all [errors][AvailErrorCode] that can happen
		 * during [message splitting][MessageSplitter].
		 */
		@JvmStatic
		val possibleErrors: A_Set = set(
			E_INCORRECT_ARGUMENT_TYPE,
			E_INCORRECT_TYPE_FOR_GROUP,
			E_INCORRECT_TYPE_FOR_COMPLEX_GROUP,
			E_INCORRECT_TYPE_FOR_COUNTING_GROUP,
			E_INCORRECT_TYPE_FOR_BOOLEAN_GROUP,
			E_INCORRECT_TYPE_FOR_NUMBERED_CHOICE,
			E_INCORRECT_USE_OF_DOUBLE_DAGGER,
			E_UNBALANCED_GUILLEMETS,
			E_METHOD_NAME_IS_NOT_CANONICAL,
			E_ALTERNATIVE_MUST_NOT_CONTAIN_ARGUMENTS,
			E_OCTOTHORP_MUST_FOLLOW_A_SIMPLE_GROUP_OR_ELLIPSIS,
			E_DOLLAR_SIGN_MUST_FOLLOW_AN_ELLIPSIS,
			E_QUESTION_MARK_MUST_FOLLOW_A_SIMPLE_GROUP,
			E_VERTICAL_BAR_MUST_SEPARATE_TOKENS_OR_SIMPLE_GROUPS,
			E_EXCLAMATION_MARK_MUST_FOLLOW_AN_ALTERNATION_GROUP,
			E_DOUBLE_QUESTION_MARK_MUST_FOLLOW_A_TOKEN_OR_SIMPLE_GROUP,
			E_CASE_INSENSITIVE_EXPRESSION_CANONIZATION,
			E_EXPECTED_OPERATOR_AFTER_BACKQUOTE,
			E_UP_ARROW_MUST_FOLLOW_ARGUMENT,
			E_INCONSISTENT_ARGUMENT_REORDERING).makeShared()

		/** A String containing all 51 circled numbers.  */
		private const val circledNumbersString =
			"⓪①②③④⑤⑥⑦⑧⑨⑩⑪⑫⑬⑭⑮⑯" +
			"⑰⑱⑲⑳㉑㉒㉓㉔㉕㉖㉗㉘㉙㉚㉛㉜㉝" +
			"㉞㉟㊱㊲㊳㊴㊵㊶㊷㊸㊹㊺㊻㊼㊽㊾㊿"

		/** How many circled numbers are in Unicode.  */
		private val circledNumbersCount =
			circledNumbersString.codePointCount(0, circledNumbersString.length)

		/** An array of the circled number code points.  */
		private val circledNumberCodePoints = IntArray(circledNumbersCount)

		/**
		 * Answer the Unicode codepoint for a circled number with the given
		 * numeric value.  The values for which values are available in Unicode
		 * are 0..50.
		 *
		 * @param number
		 *   The number.
		 * @return
		 *   The codepoint which depicts that number inside a circle.
		 */
		@JvmStatic
		fun circledNumberCodePoint(number: Int): Int
		{
			assert(number in 0 until circledNumbersCount)
			return circledNumberCodePoints[number]
		}

		/**
		 * A map from the Unicode code points for the circled number characters
		 * found in various regions of the Unicode code space.
		 *
		 * @see [circledNumbersString]
		 */
		private val circledNumbersMap: MutableMap<Int, Int> =
			HashMap(circledNumbersCount)

		/* Initialize circledNumbersMap and circledNumberCodePoints */
		init
		{
			var positionInString = 0
			var number = 0
			while (positionInString < circledNumbersString.length)
			{
				val codePoint =
					circledNumbersString.codePointAt(positionInString)
				circledNumbersMap[codePoint] = number
				circledNumberCodePoints[number++] = codePoint
				positionInString += Character.charCount(codePoint)
			}
		}

		/**
		 * The tuple of all encountered permutations (tuples of integers) found
		 * in all message names.  Keeping a statically accessible tuple shared
		 * between message names allows [bundle trees][A_BundleTree] to easily
		 * get to the permutations they need without having a separate per-tree
		 * structure.
		 *
		 * The field is an [AtomicReference], and is accessed in a wait-free way
		 * with compare-and-set and retry.  The tuple itself should always be
		 * marked as shared.  This mechanism is thread-safe.
		 */
		private val permutations = AtomicReference<A_Tuple>(emptyTuple())

		/**
		 * A statically-scoped [List] of unique constants needed as operands of
		 * some [ParsingOperation]s.  The inverse [Map] (but containing
		 * one-based indices) is kept in #constantsMap}.
		 */
		private val constantsList = ArrayList<AvailObject>(100)

		/**
		 * A statically-scoped map from Avail object to one-based index (into
		 * [constantsList], after adjusting to a zero-based [List]), for which
		 * some [ParsingOperation] needed to hold that constant as an operand.
		 */
		private val constantsMap = HashMap<AvailObject, Int>(100)

		/**
		 * A lock to protect [constantsList] and [constantsMap].
		 */
		private val constantsLock = ReentrantReadWriteLock()

		/**
		 * Answer the permutation having the given one-based index.  We need a
		 * read barrier here, but no lock, since the tuple of tuples is only
		 * appended to, ensuring all extant indices will always be valid.
		 *
		 * @param index
		 *   The index of the permutation to retrieve.
		 * @return
		 *   The permutation (a [tuple][A_Tuple] of Avail integers).
		 */
		@JvmStatic
		fun permutationAtIndex(index: Int): AvailObject =
			permutations.get().tupleAt(index)

		/**
		 * Answer the index of the given permutation (tuple of integers), adding
		 * it to the global [MessageSplitter.constantsList] if necessary.
		 *
		 * @param permutation
		 *   The permutation whose globally unique one-based index should be
		 *   determined.
		 * @return The permutation's one-based index.
		 */
		@JvmStatic
		fun indexForPermutation(permutation: A_Tuple): Int
		{
			var checkedLimit = 0
			while (true)
			{
				val before = permutations.get()
				val newLimit = before.tupleSize()
				for (i in checkedLimit + 1..newLimit)
				{
					if (before.tupleAt(i).equals(permutation))
					{
						// Already exists.
						return i
					}
				}
				val after =
					before.appendCanDestroy(permutation, false).makeShared()
				if (permutations.compareAndSet(before, after))
				{
					// Added it successfully.
					return after.tupleSize()
				}
				checkedLimit = newLimit
			}
		}

		/**
		 * Answer the index of the given constant, adding it to the global
		 * [constantsList] and [constantsMap]} if necessary.
		 *
		 * @param constant
		 *   The type to look up or add to the global index.
		 * @return
		 *   The one-based index of the type, which can be retrieved later via
		 *   [constantForIndex].
		 */
		@JvmStatic
		fun indexForConstant(constant: A_BasicObject): Int
		{
			val strongConstant = constant.makeShared()
			auto(constantsLock.readLock()).use {
				val index = constantsMap[strongConstant]
				if (index !== null)
				{
					return index
				}
			}

			auto(constantsLock.writeLock()).use {
				val index = constantsMap[strongConstant]
				if (index !== null)
				{
					return index
				}
				assert(constantsMap.size == constantsList.size)
				val newIndex = constantsList.size + 1
				constantsList.add(strongConstant)
				constantsMap[strongConstant] = newIndex
				return newIndex
			}
		}

		/** The position at which true is stored in the [constantsList].  */
		@JvmStatic
		val indexForTrue = indexForConstant(trueObject())

		/** The position at which false is stored in the [constantsList].  */
		@JvmStatic
		val indexForFalse = indexForConstant(falseObject())

		/**
		 * Answer the [AvailObject] having the given one-based index in the
		 * static [constantsList] [List].
		 *
		 * @param index
		 *   The one-based index of the constant to retrieve.
		 * @return
		 *   The [AvailObject] at the given index.
		 */
		@JvmStatic
		fun constantForIndex(index: Int): AvailObject
		{
			auto(constantsLock.readLock())
				.use { return constantsList[index - 1] }
		}

		/**
		 * If the condition is true, throw a [MalformedMessageException] with
		 * the given errorCode and errorString.  Otherwise do nothing.
		 *
		 * @param condition
		 *   The [Boolean] to test.
		 * @param errorCode
		 *   The errorCode to use in the [MalformedMessageException] if the
		 *   condition was true.
		 * @param errorString
		 *   The errorString to use in the [MalformedMessageException] if the
		 *   condition was true.
		 * @throws MalformedMessageException
		 *   If the condition was true.
		 */
		@Throws(MalformedMessageException::class)
		private fun throwMalformedIf(
			condition: Boolean,
			errorCode: AvailErrorCode,
			errorString: String)
		{
			if (condition)
			{
				throwMalformedMessageException(errorCode, errorString)
			}
		}

		/**
		 * Check that an [Expression] to be used as an alternative in an
		 * [Alternation] is well-formed.  Note that alternations may not contain
		 * arguments.
		 *
		 * @param expression
		 *   The alternative to check.
		 * @throws MalformedMessageException
		 *   If the alternative contains an argument.
		 */
		@Throws(MalformedMessageException::class)
		private fun checkAlternative(expression: Expression)
		{
			throwMalformedIf(
				expression.yieldsValue
						|| expression.underscoreCount > 0,
				E_ALTERNATIVE_MUST_NOT_CONTAIN_ARGUMENTS,
				"Alternatives must not contain arguments")
		}

		/**
		 * Throw a [SignatureException] with the given error code.
		 *
		 * @param errorCode
		 *        The [AvailErrorCode] that indicates the problem.
		 * @throws SignatureException
		 *         Always, with the given error code.
		 */
		@JvmStatic
		@Throws(SignatureException::class)
		fun throwSignatureException(errorCode: AvailErrorCode): Nothing
		{
			throw SignatureException(errorCode)
		}

		/**
		 * Throw a [MalformedMessageException] with the given error code.
		 *
		 * @param errorCode
		 *   The [AvailErrorCode] that indicates the problem.
		 * @param errorMessage
		 *   A description of the problem.
		 * @throws MalformedMessageException
		 *   Always, with the given error code and diagnostic message.
		 * @return
		 *   Nothing, but pretend it's the [MalformedMessageException] that was
		 *   thrown so that the caller can pretend to throw it again to indicate
		 *   to Java that this call terminates the control flow unconditionally.
		 */
		@JvmStatic
		@Throws(MalformedMessageException::class)
		fun throwMalformedMessageException(
			errorCode: AvailErrorCode, errorMessage: String): Nothing
		{
			throw MalformedMessageException(errorCode) { errorMessage }
		}

		/**
		 * Answer whether the specified character is an operator character,
		 * space, underscore, or ellipsis.
		 *
		 * @param cp
		 *   A Unicode codepoint (an [Int]).
		 * @return
		 *   `true` if the specified character is an operator character, space,
		 *   underscore, or ellipsis; or `false` otherwise.
		 */
		private fun isUnderscoreOrSpaceOrOperator(cp: Int) =
			cp == '_'.toInt()
				|| cp == '…'.toInt()
				|| cp == ' '.toInt()
				|| cp == '/'.toInt()
				|| cp == '$'.toInt()
				|| isOperator(cp)

		/**
		 * Answer whether the given Unicode codePoint is an acceptable operator.
		 *
		 * @param cp
		 *   The codePoint to check.
		 * @return
		 *   Whether the codePoint can be used as an operator character in a
		 *   method name.
		 */
		@JvmStatic
		fun isOperator(cp: Int) =
			!(Character.isDigit(cp)
				|| Character.isUnicodeIdentifierStart(cp)
				|| Character.isSpaceChar(cp)
				|| Character.isWhitespace(cp)
				|| cp < 32
				|| !(cp >= 160 || cp <= 126)
				|| !Character.isDefined(cp)
				|| cp == '_'.toInt()
				|| cp == '"'.toInt()
				|| cp == '\uFEFF'.toInt())
	}
}
