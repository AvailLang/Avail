/*
 * MessageSplitter.kt
 * Copyright © 1993-2022, The Avail Foundation, LLC.
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

package avail.compiler.splitter

import avail.compiler.ParsingOperation
import avail.compiler.problems.CompilerDiagnostics
import avail.compiler.splitter.MessageSplitter.Metacharacter.BACK_QUOTE
import avail.compiler.splitter.MessageSplitter.Metacharacter.CLOSE_GUILLEMET
import avail.compiler.splitter.MessageSplitter.Metacharacter.Companion.canBeBackQuoted
import avail.compiler.splitter.MessageSplitter.Metacharacter.DOUBLE_DAGGER
import avail.compiler.splitter.MessageSplitter.Metacharacter.DOUBLE_QUESTION_MARK
import avail.compiler.splitter.MessageSplitter.Metacharacter.ELLIPSIS
import avail.compiler.splitter.MessageSplitter.Metacharacter.EXCLAMATION_MARK
import avail.compiler.splitter.MessageSplitter.Metacharacter.OCTOTHORP
import avail.compiler.splitter.MessageSplitter.Metacharacter.OPEN_GUILLEMET
import avail.compiler.splitter.MessageSplitter.Metacharacter.QUESTION_MARK
import avail.compiler.splitter.MessageSplitter.Metacharacter.SECTION_SIGN
import avail.compiler.splitter.MessageSplitter.Metacharacter.SINGLE_DAGGER
import avail.compiler.splitter.MessageSplitter.Metacharacter.TILDE
import avail.compiler.splitter.MessageSplitter.Metacharacter.UNDERSCORE
import avail.compiler.splitter.MessageSplitter.Metacharacter.UP_ARROW
import avail.compiler.splitter.MessageSplitter.Metacharacter.VERTICAL_BAR
import avail.descriptor.atoms.AtomDescriptor.Companion.falseObject
import avail.descriptor.atoms.AtomDescriptor.Companion.trueObject
import avail.descriptor.atoms.AtomDescriptor.SpecialAtom
import avail.descriptor.bundles.A_Bundle
import avail.descriptor.bundles.A_BundleTree
import avail.descriptor.methods.A_Macro
import avail.descriptor.methods.DefinitionDescriptor
import avail.descriptor.methods.MacroDescriptor
import avail.descriptor.methods.MethodDefinitionDescriptor
import avail.descriptor.numbers.A_Number.Companion.extractInt
import avail.descriptor.numbers.A_Number.Companion.isInt
import avail.descriptor.numbers.IntegerDescriptor
import avail.descriptor.parsing.A_Lexer
import avail.descriptor.phrases.A_Phrase
import avail.descriptor.phrases.A_Phrase.Companion.argumentsListNode
import avail.descriptor.phrases.A_Phrase.Companion.expressionsTuple
import avail.descriptor.phrases.ListPhraseDescriptor
import avail.descriptor.phrases.PermutedListPhraseDescriptor
import avail.descriptor.phrases.ReferencePhraseDescriptor
import avail.descriptor.phrases.SendPhraseDescriptor
import avail.descriptor.phrases.VariableUsePhraseDescriptor
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.AvailObject
import avail.descriptor.representation.Mutability.SHARED
import avail.descriptor.sets.A_Set
import avail.descriptor.sets.SetDescriptor.Companion.set
import avail.descriptor.tokens.LiteralTokenDescriptor
import avail.descriptor.tuples.A_String
import avail.descriptor.tuples.A_String.Companion.asNativeString
import avail.descriptor.tuples.A_String.Companion.copyStringFromToCanDestroy
import avail.descriptor.tuples.A_Tuple
import avail.descriptor.tuples.A_Tuple.Companion.appendCanDestroy
import avail.descriptor.tuples.A_Tuple.Companion.tupleAt
import avail.descriptor.tuples.A_Tuple.Companion.tupleCodePointAt
import avail.descriptor.tuples.A_Tuple.Companion.tupleSize
import avail.descriptor.tuples.StringDescriptor
import avail.descriptor.tuples.StringDescriptor.Companion.stringFrom
import avail.descriptor.tuples.TupleDescriptor
import avail.descriptor.tuples.TupleDescriptor.Companion.emptyTuple
import avail.descriptor.types.A_Type
import avail.descriptor.types.A_Type.Companion.argsTupleType
import avail.descriptor.types.A_Type.Companion.lowerBound
import avail.descriptor.types.A_Type.Companion.sizeRange
import avail.descriptor.types.A_Type.Companion.upperBound
import avail.descriptor.types.FunctionTypeDescriptor
import avail.descriptor.types.PhraseTypeDescriptor
import avail.descriptor.types.TupleTypeDescriptor
import avail.exceptions.AvailErrorCode
import avail.exceptions.AvailErrorCode.E_ALTERNATIVE_MUST_NOT_CONTAIN_ARGUMENTS
import avail.exceptions.AvailErrorCode.E_CASE_INSENSITIVE_EXPRESSION_CANONIZATION
import avail.exceptions.AvailErrorCode.E_DOUBLE_QUESTION_MARK_MUST_FOLLOW_A_TOKEN_OR_SIMPLE_GROUP
import avail.exceptions.AvailErrorCode.E_EXCLAMATION_MARK_MUST_FOLLOW_AN_ALTERNATION_GROUP
import avail.exceptions.AvailErrorCode.E_EXPECTED_OPERATOR_AFTER_BACKQUOTE
import avail.exceptions.AvailErrorCode.E_INCONSISTENT_ARGUMENT_REORDERING
import avail.exceptions.AvailErrorCode.E_INCORRECT_ARGUMENT_TYPE
import avail.exceptions.AvailErrorCode.E_INCORRECT_NUMBER_OF_ARGUMENTS
import avail.exceptions.AvailErrorCode.E_INCORRECT_TYPE_FOR_BOOLEAN_GROUP
import avail.exceptions.AvailErrorCode.E_INCORRECT_TYPE_FOR_COMPLEX_GROUP
import avail.exceptions.AvailErrorCode.E_INCORRECT_TYPE_FOR_COUNTING_GROUP
import avail.exceptions.AvailErrorCode.E_INCORRECT_TYPE_FOR_GROUP
import avail.exceptions.AvailErrorCode.E_INCORRECT_TYPE_FOR_NUMBERED_CHOICE
import avail.exceptions.AvailErrorCode.E_INCORRECT_USE_OF_DOUBLE_DAGGER
import avail.exceptions.AvailErrorCode.E_METHOD_NAME_IS_NOT_CANONICAL
import avail.exceptions.AvailErrorCode.E_OCTOTHORP_MUST_FOLLOW_A_SIMPLE_GROUP_OR_ELLIPSIS
import avail.exceptions.AvailErrorCode.E_QUESTION_MARK_MUST_FOLLOW_A_SIMPLE_GROUP
import avail.exceptions.AvailErrorCode.E_UNBALANCED_GUILLEMETS
import avail.exceptions.AvailErrorCode.E_UP_ARROW_MUST_FOLLOW_ARGUMENT
import avail.exceptions.AvailErrorCode.E_VERTICAL_BAR_MUST_SEPARATE_TOKENS_OR_SIMPLE_GROUPS
import avail.exceptions.MalformedMessageException
import avail.exceptions.SignatureException
import avail.utility.iterableWith
import avail.utility.safeWrite
import org.availlang.cache.LRUCache
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read

/**
 * `MessageSplitter` is used to split Avail message names into a sequence of
 * [instructions][ParsingOperation] that can be used directly for parsing.
 *
 * Message splitting occurs in two phases.  In the first setPhase, the message
 * is tokenized and parsed into an abstract [Expression] tree. In the second
 * setPhase, a [tuple&#32;type][TupleTypeDescriptor] of
 * [phrase&#32;types][PhraseTypeDescriptor] is supplied, and produces a tuple of
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
@Throws(MalformedMessageException::class)
private constructor(messageName: A_String)
{
	/**
	 * The Avail string to be parsed.  It *must* be [SHARED].
	 */
	val messageName: A_String

	/**
	 * The individual tokens ([strings][StringDescriptor])
	 * constituting the message.
	 *
	 *  * Alphanumerics are in runs, separated from other alphanumerics by a
	 *    single space.
	 *  * Operator characters are never beside spaces, and are always parsed as
	 *    individual tokens.
	 *  * [Open&#32;guillemet][Metacharacter.OPEN_GUILLEMET] («),
	 *    [double&#32;dagger][Metacharacter.DOUBLE_DAGGER] (‡), and
	 *    [close&#32;guillemet][Metacharacter.CLOSE_GUILLEMET] (») are used to
	 *    indicate repeated or optional substructures.
	 *  * The characters [octothorp][Metacharacter.OCTOTHORP]
	 *    (#) and [question&#32;mark][Metacharacter.QUESTION_MARK] (?) modify
	 *    the output of repeated substructures to produce either a count of the
	 *    repetitions or a boolean indicating whether an optional subexpression
	 *    (expecting no arguments) was present.
	 *  * Placing a [question&#32;mark][Metacharacter.QUESTION_MARK]
	 *    (?) after a group containing arguments but no
	 *    [double&#32;dagger][Metacharacter.DOUBLE_DAGGER] (‡) will limit the
	 *    repetitions of the group to at most one.  Although limiting the method
	 *    definitions to only accept 0..1 occurrences would accomplish the same
	 *    grammatical narrowing, the parser might still attempt to parse more
	 *    than one occurrence, leading to unnecessarily confusing diagnostics.
	 *  * An [exclamation&#32;mark][Metacharacter.EXCLAMATION_MARK] (!) can
	 *    follow a group of alternations to produce the 1-based index of the
	 *    alternative that actually occurred.
	 *  * An [underscore][Metacharacter.UNDERSCORE] (_) indicates where an
	 *    argument occurs.
	 *  * A [single&#32;dagger][Metacharacter.SINGLE_DAGGER] (†) may occur
	 *    immediately after an underscore to cause the argument expression to be
	 *    evaluated in the static scope during compilation, wrapping the value
	 *    in a [literal&#32;phrase][LiteralPhraseDescriptor].  This is
	 *    applicable to both methods and macros.  The expression is subject to
	 *    grammatical restrictions, and it must yield a value of suitable type.
	 *  * An [up-arrow][Metacharacter.UP_ARROW] (↑) after an underscore
	 *    indicates an in-scope variable name is to be parsed.  The
	 *    subexpression causes the variable itself to be provided, rather than
	 *    its value.
	 *  * An [exclamation&#32;mark][Metacharacter.EXCLAMATION_MARK] (!) may
	 *    occur after the underscore instead, to indicate the argument
	 *    expression may be ⊤-valued or ⊥-valued.  Since a function (and
	 *    therefore a method definition) cannot accept a ⊤-valued argument, this
	 *    mechanism only makes sense for macros, since macros bodies are passed
	 *    phrases, which may be typed as *yielding* a top-valued result.
	 *  * An [ellipsis][Metacharacter.ELLIPSIS] (…) matches a single
	 *    [keyword&#32;token][A_Token].
	 *  * An [exclamation&#32;mark][Metacharacter.EXCLAMATION_MARK] (!) after an
	 *    ELLIPSIS indicates *any* token will be accepted at that position.
	 *  * An [octothorp][Metacharacter.OCTOTHORP] (#) after an ellipsis
	 *    indicates only a *literal* token will be accepted.
	 *  * The N<sup>th</sup> [section][Metacharacter.SECTION_SIGN] (§) in a
	 *    message name indicates where a macro's N<sup>th</sup>
	 *    [prefix&#32;function][A_Definition.prefixFunctions] should be invoked
	 *    with the current parse stack up to that point.
	 *  * A [backquote][Metacharacter.BACK_QUOTE] (`) can precede any operator
	 *    character, such as guillemets or double dagger, to ensure it is not
	 *    used in a special way. A backquote may also operate on another
	 *    backquote (to indicate that an actual backquote token will appear in a
	 *    call).
	 */
	val messageParts: Array<A_String>

	/**
	 * A collection of one-based positions in the original string, corresponding
	 * to the [messageParts] array that have been extracted.
	 */
	private val messagePartPositions: IntArray

	/** The current one-based parsing position in the list of tokens. */
	private var messagePartPosition: Int = 0

	/**
	 * The number of underscores/ellipses present in the method name. This is
	 * not the same as the number of arguments that a method implementing this
	 * name would accept, as a top-level guillemet group with `N` recursively
	 * embedded underscores/ellipses is counted as `N`, not one.
	 *
	 * This count of underscores/ellipses is essential for expressing negative
	 * precedence rules (grammatical restrictions) in the presence of repeated
	 * arguments.  Also note that backquoted underscores are not counted, since
	 * they don't represent a position at which a subexpression must occur.
	 * Similarly, backquoted ellipses are not a place where an arbitrary input
	 * token can go.
	 */
	var leafArgumentCount = 0
		private set

	/**
	 * The number of [SectionCheckpoint]s encountered so far.
	 */
	var numberOfSectionCheckpoints: Int = 0

	/** The top-most [sequence][Sequence]. */
	private val rootSequence: Sequence

	/**
	 * The current effective position in the message name.  This is always the
	 * start of a token or the end of the entire name.
	 */
	private val currentPositionForStart: Int get() = when (messagePartPosition)
	{
		messageParts.size + 1 -> messageName.tupleSize + 1
		else -> messagePartPositions[messagePartPosition - 1]
	}

	/**
	 * The current effective position in the message name.  This is always the
	 * *end* of a token or the end of the name.
	 */
	private val currentPositionForEnd: Int get() = when (messagePartPosition)
	{
		messageParts.size + 1 -> messageName.tupleSize + 1
		1 -> 1
		else -> messagePartPositions[messagePartPosition - 2] +
			messageParts[messagePartPosition - 2].tupleSize
	}

	/**
	 * The metacharacters used in message names.
	 *
	 * @constructor
	 *
	 * Initialize an enum instance.
	 *
	 * @param javaString
	 *   The Java [String] denoting the metacharacter.
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
		 * the [circled&#32;numbers][circledNumbersString].
		 */
		BACK_QUOTE("`"),

		/**
		 * A close-guillemet (») indicates the end of a group or other structure
		 * that started with an [OPEN_GUILLEMET] open-guillemet («).
		 */
		CLOSE_GUILLEMET("»"),

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
		 *    [RawTokenArgument], which matches a single [A_Token] of kind
		 *    [TokenType.LITERAL].
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
		 *
		 * When an exclamation mark follows a guillemet group, that group must
		 * contain an alternation.  If no alternatives produce a value, the
		 * effect is to push a constant corresponding with the alternative's
		 * one-based index in the alternation.
		 *
		 * If the exclamation mark follows a guillemet group that contains an
		 * alternation where at least one alternative produces a value, it
		 * attempts to parse a series of alternative phrases.  Each parsed
		 * phrase is numbered with an ascending one-based subscript,
		 * representing the order in which the phrases occurred in the source.
		 * The final effect is to push a single tuple with N elements,
		 * corresponding to the N alternatives.  Each element is itself a tuple
		 * with an appropriate number of arguments for that alternative, but
		 * preceded by a natural number indicating the order of that phrase in
		 * the source.
		 */
		EXCLAMATION_MARK("!"),

		/**
		 * An octothorp (#) after an [ELLIPSIS] (…) indicates that a
		 * whole-number-valued literal token should be consumed from the Avail
		 * source code at this position.  This is accomplished through the use
		 * of a [RawLiteralTokenArgument].
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
		 * invocation, it should invoke one of its
		 * [prefix&#32;functions][A_Macro.prefixFunctions].  The order of
		 * section signs in the method name corresponds with the order of the
		 * prefix functions.
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
		 * A space character separates alphanumeric tokens in a message name.
		 */
		SPACE(" "),

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
		 *  * If followed by a [UP_ARROW] (↑), a
		 *    [variable&#32;use&#32;phrase][VariableUsePhraseDescriptor] must be
		 *    supplied, which is converted into a
		 *    [reference&#32;phrase][ReferencePhraseDescriptor].
		 */
		UNDERSCORE("_"),

		/**
		 * If an up-arrow (↑) follows an [UNDERSCORE], a [VariableQuote] is
		 * created, which expects a [VariableUsePhraseDescriptor], which will be
		 * automatically converted into a
		 * [reference&#32;phrase][ReferencePhraseDescriptor].
		 */
		UP_ARROW("↑"),

		/**
		 * The vertical bar (|) separates alternatives within an [Alternation].
		 * The alternatives may be [Simple] expressions, or simple [Group]s
		 * (having no double-dagger, and yielding no argument values).
		 */
		VERTICAL_BAR("|");

		/** The Avail [A_String] denoting this metacharacter. */
		val string: A_String = stringFrom(javaString).makeShared().apply {
			assert(tupleSize == 1)
		}

		/** The sole codepoint ([Int]) of this [Metacharacter] instance. */
		val codepoint: Int = string.tupleCodePointAt(1)

		companion object
		{
			/**
			 * This collects all metacharacters, including space and circled
			 * numbers.  These are the characters that can be [BACK_QUOTE]d.
			 */
			private val backquotableCodepoints = mutableSetOf<Int>()

			// Load the set with metacharacters and circled numbers.
			init
			{
				backquotableCodepoints.addAll(circledNumbersMap.keys)
				entries.forEach {
					backquotableCodepoints.add(it.codepoint)
				}
			}

			/**
			 * Answer whether the given Unicode codepoint may be
			 * [backquoted][BACK_QUOTE].
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
		// Since the messageName is used as a key in a cache, multiple threads
		// may compare these keys, so it must have been shared already for
		// safety.
		assert(messageName.descriptor().isShared)
		this.messageName = messageName
		val tokenizer = MessageSplitterTokenizer(this.messageName)
		this.messageParts = tokenizer.canonicalMessageParts()
		this.messagePartPositions = tokenizer.messagePartPositions()
		try
		{
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
		}
		catch (e: MalformedMessageException)
		{
			// Add contextual text and rethrow it.
			throw MalformedMessageException(e.errorCode)
			{
				buildString {
					append(e.describeProblem())
					append(". See arrow (")
					append(CompilerDiagnostics.errorIndicatorSymbol)
					append(") in: \"")
					val characterIndex = currentPositionForStart
					val before = messageName.copyStringFromToCanDestroy(
						1, characterIndex - 1, false)
					val after = messageName.copyStringFromToCanDestroy(
						characterIndex, messageName.tupleSize, false)
					append(before.asNativeString())
					append(CompilerDiagnostics.errorIndicatorSymbol)
					append(after.asNativeString())
					append('"')
				}
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
	private fun dumpForDebug(builder: StringBuilder) = with(builder)
	{
		append(messageName.asNativeString())
		append("\n------\n")
		messageParts.forEach {
			append("\t${it.asNativeString()}\n")
		}
	}

	/**
	 * Answer whether parsing has reached the end of the message parts.
	 *
	 * @return
	 *   `true` if the current position has consumed the last message part.
	 */
	private val atEnd get() = messagePartPosition > messageParts.size

	/**
	 * Answer the current message part, or `null` if we are [atEnd].  Do not
	 * consume the message part.
	 *
	 * @return
	 *   The current message part or `null`.
	 */
	private val currentMessagePartOrNull
		get() = if (atEnd) null else messageParts[messagePartPosition - 1]

	/**
	 * Answer the current message part.  We must not be [atEnd].  Do
	 * not consume the message part.
	 *
	 * @return
	 *   The current message part.
	 */
	private val currentMessagePart
		get(): A_String
		{
			assert(!atEnd)
			return messageParts[messagePartPosition - 1]
		}

	/**
	 * Pretty-print a send of this message with given argument phrases.
	 *
	 * @param sendPhrase
	 *   The [send&#32;phrase][SendPhraseDescriptor] that is being printed.
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
			sendPhrase.argumentsListNode.expressionsTuple.iterator(),
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
	 * @throws SignatureException
	 *   If a plan cannot be constructed for this name and phrase type.
	 */
	@Throws(SignatureException::class)
	fun instructionsTupleFor(phraseType: A_Type): A_Tuple
	{
		val generator = InstructionGenerator()
		rootSequence.emitOn(phraseType, generator, WrapState.PUSHED_LIST)
		generator.optimizeInstructions()
		return generator.instructionsTuple()
	}

	/**
	 * Answer a [List] of [Expression] objects that correlates with the
	 * [parsing&#32;instructions][instructionsTupleFor] generated for the
	 * message name and the provided signature tuple type. Note that the list is
	 * 0-based and the tuple is 1-based.
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
		assert(expressions.size == generator.instructionsTuple().tupleSize)
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
		val zeroBasedPosition =
			if (pc == expressions.size + 1) string.length
			else expressions[pc - 1].startInName - 1
		val annotatedString = string.replaceRange(
			zeroBasedPosition until zeroBasedPosition,
			CompilerDiagnostics.errorIndicatorSymbol)
		return stringFrom(annotatedString).toString()
	}

	/**
	 * Given a series of one-based indices that indicate how to traverse the
	 * nested lists of some call site to a single point of interest, follow the
	 * corresponding [Expression]s to identify the region of the name identified
	 * by that path.
	 *
	 * The returned range includes both the first and last character position of
	 * the range, using one-based indexing.
	 */
	fun highlightRangeForPath(
		indices: List<Int>
	): IntRange
	{
		var expression: Expression = rootSequence
		val indicesIterator = indices.iterator()
		while (indicesIterator.hasNext())
		{
			val index = indicesIterator.next()
			when (expression)
			{
				is Sequence ->
				{
					val yielders = expression.yielders
					if (index - 1 !in yielders.indices) break
					expression = yielders[index - 1]
				}
				is Group ->
				{
					// Ignore the current index, since it's just the group
					// repetition index, which doesn't affect the highlight.
					val leftYielders = expression.beforeDagger.yielders
					val rightYielders = expression.afterDagger.yielders
					if (leftYielders.size == 1 && rightYielders.isEmpty())
					{
						// It's a singly-wrapped group, so go immediately to the
						// sole left yielder without consuming another index.
						expression = leftYielders.single()
					}
					else
					{
						// It's a doubly-wrapped group.  Consume another index
						// and figure out which left or right yielder to visit.
						if (indicesIterator.hasNext())
						{
							val nextZeroIndex = indicesIterator.next() - 1
							val nextMinusLeft =
								nextZeroIndex - leftYielders.size
							expression = when
							{
								nextMinusLeft < 0 -> leftYielders[nextZeroIndex]
								nextMinusLeft < rightYielders.size ->
									rightYielders[nextMinusLeft]
								// Invalid index.
								else -> break
							}
						}
					}
				}
			}
		}
		return expression.startInName until expression.pastEndInName
	}

	/**
	 * Given a one-based [messagePartIndex] into this splitter's tokenized
	 * parts, answer the one-based (inclusive) range of the message covered by
	 * that message part.
	 *
	 * The returned range includes both the first and last character position of
	 * the range, using one-based indexing.
	 */
	fun rangeToHighlightForPartIndex(
		messagePartIndex: Int
	): IntRange
	{
		assert(messagePartIndex > 0)
		val start = messagePartPositions[messagePartIndex - 1]
		val size = messageParts[messagePartIndex - 1].tupleSize
		return start until start + size
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
		errorString: String
	): Boolean
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
	 *   A [Sequence] expression parsed from the [messageParts] array.
	 * @throws MalformedMessageException
	 *   If the method name is malformed.
	 */
	@Throws(MalformedMessageException::class)
	private fun parseSequence(): Sequence
	{
		val start = currentPositionForStart
		val elements = mutableListOf<Expression>()
		var isReordered: Boolean? = null
		var anyYielders = false
		parseElementOrAlternation()
			.iterableWith { parseElementOrAlternation() }
			.forEach { expression ->
				elements.add(expression)
				if (expression.yieldsValue)
				{
					assert(expression.canBeReordered)
					val alsoReordered = expression.explicitOrdinal != -1
					if (!anyYielders)
					{
						isReordered = alsoReordered
					}
					else
					{
						// Check that the new expression's reordering agrees
						// with the current consensus.
						if (isReordered != alsoReordered)
						{
							throwMalformedMessageException(
								E_INCONSISTENT_ARGUMENT_REORDERING,
								"The sequence of subexpressions before or " +
									"after a double-dagger (‡) in a group " +
									"must have either all or none of its " +
									"arguments or direct subgroups numbered " +
									"for reordering")
						}
					}
					anyYielders = true
				}
		}
		val sequence = Sequence(start, currentPositionForEnd, elements)
		sequence.isReordered = isReordered ?: false
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
		val alternatives = mutableListOf<Expression>()
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
		return Alternation(
			firstExpression.startInName,
			alternatives.last().pastEndInName,
			alternatives)
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
	private fun parseElement(): Expression? = when
	{
		atEnd -> null
		// Try to parse the kinds of things that deal with their own suffixes.
		peekAheadFor(DOUBLE_DAGGER) || peekAheadFor(CLOSE_GUILLEMET) -> null
		peekFor(UNDERSCORE) ->
			parseOptionalExplicitOrdinal(parseUnderscoreElement())
		peekFor(ELLIPSIS) ->
			parseOptionalExplicitOrdinal(parseEllipsisElement())
		peekFor(OPEN_GUILLEMET) ->
			parseOptionalExplicitOrdinal(parseGuillemetElement())
		peekFor(SECTION_SIGN) -> SectionCheckpoint(
			currentPositionForStart - 1,  // Include the §.
			currentPositionForEnd,   // Can't have adjacent spaces.
			++numberOfSectionCheckpoints)
		else -> parseSimple()
	}

	/**
	 * Parse a [Simple] at the current position.  Also look for suffixes
	 * indicating
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
				messagePartPosition >= messageParts.size,
				E_EXPECTED_OPERATOR_AFTER_BACKQUOTE,
				"Backquote (`) must be followed by a special " +
					"metacharacter, space, or circled number"))
		{
			val token = currentMessagePart
			// Expects metacharacter or space or circled number after backquote.
			throwMalformedIf(
				token.tupleSize != 1 || !canBeBackQuoted(
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
		val tokenIndex = messagePartPosition
		var expression: Expression = Simple(
			messagePartPositions[tokenIndex - 1],
			// Only include the actual token characters, not any space that may
			// be after it if that token and its successor are alphanumeric.
			messagePartPositions[tokenIndex - 1] +
				messageParts[tokenIndex - 1].tupleSize,
			currentMessagePart,
			tokenIndex)
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
			val sequence = Sequence(
				expression.startInName,
				expression.pastEndInName,
				listOf(expression))
			expression = Optional(
				expression.startInName,
				expression.pastEndInName,
				sequence)
		}
		else if (peekFor(DOUBLE_QUESTION_MARK))
		{
			val sequence = Sequence(
				expression.startInName,
				expression.pastEndInName,
				listOf(expression))
			expression = CompletelyOptional(
				expression.startInName, expression.pastEndInName, sequence)
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
		// We just parsed an open guillemet.  Parse the content, eat the
		// mandatory close guillemet, and apply any modifiers to the group.
		val startOfGroup = currentPositionForStart - 1  // Include the '«'.
		val beforeDagger = parseSequence()
		val hasDagger = peekFor(DOUBLE_DAGGER)
		val afterDagger: Sequence
		when
		{
			hasDagger ->
			{
				afterDagger = parseSequence()
				// Check for a second double-dagger.
				peekFor(
					DOUBLE_DAGGER,
					true,
					E_INCORRECT_USE_OF_DOUBLE_DAGGER,
					"A group must have at most one double-dagger (‡)")
			}
			else ->
			{
				afterDagger = Sequence(
					currentPositionForStart,
					currentPositionForStart,
					emptyList())
			}
		}
		if (!peekFor(CLOSE_GUILLEMET))
		{
			// Expected matching close guillemet.
			throwMalformedMessageException(
				E_UNBALANCED_GUILLEMETS,
				"Expected close guillemet (») to end group")
		}
		var group = Group(
			startOfGroup,
			currentPositionForEnd,
			beforeDagger,
			hasDagger,
			afterDagger)

		// Look for a case-sensitive, then look for a counter, optional, or
		// completely-optional.
		if (peekFor(
			TILDE,
			!group.isLowerCase,
			E_CASE_INSENSITIVE_EXPRESSION_CANONIZATION,
			"Tilde (~) may only occur after a lowercase " +
				"token or a group of lowercase tokens"))
		{
			group = group.applyCaseInsensitive()
			// Rebuild the group, but including the '~'.
			group = group.run {
				Group(
					startInName,
					currentPositionForEnd,  // Include the '~'.
					beforeDagger.applyCaseInsensitive(),
					hasDagger,
					afterDagger.applyCaseInsensitive(),
					maximumCardinality)
			}
		}

		return when
		{
			peekFor(
				OCTOTHORP,
				group.underscoreCount > 0,
				E_OCTOTHORP_MUST_FOLLOW_A_SIMPLE_GROUP_OR_ELLIPSIS,
				"An octothorp (#) may only follow a non-yielding " +
					"group or an ellipsis (…)"
			) -> Counter(startOfGroup, currentPositionForEnd, group)
			peekFor(
				QUESTION_MARK,
				group.hasDagger,
				E_QUESTION_MARK_MUST_FOLLOW_A_SIMPLE_GROUP,
				"A question mark (?) may only follow a simple "
					+ "group (optional) or a group with arguments "
					+ "(0 or 1 occurrences), but not one with a "
					+ "double-dagger (‡), since that suggests "
					+ "multiple occurrences to be separated"
			) -> when
			{
				group.underscoreCount > 0 ->
				{
					// A complex group just gets bounded to [0..1] occurrences.
					group = group.run {
						Group(
							startInName,
							currentPositionForEnd,  // Include the '?'.
							beforeDagger,
							hasDagger,
							afterDagger,
							maximumCardinality = 1)
					}
					group
				}
				else ->
					// A simple group turns into an Optional, which produces a
					// literal boolean indicating the presence of such a
					// subexpression.
					Optional(
						startOfGroup,
						currentPositionForEnd,
						group.beforeDagger)
			}
			peekFor(
				DOUBLE_QUESTION_MARK,
				group.underscoreCount > 0 || group.hasDagger,
				E_DOUBLE_QUESTION_MARK_MUST_FOLLOW_A_TOKEN_OR_SIMPLE_GROUP,
				"A double question mark (⁇) may only follow "
					+ "a token or simple group, not one with a "
					+ "double-dagger (‡) or arguments"
			) -> CompletelyOptional(
				startOfGroup, currentPositionForEnd, group.beforeDagger)
			peekFor(
				EXCLAMATION_MARK,
				group.underscoreCount > 0
					|| group.hasDagger
					|| group.beforeDagger.expressions.size != 1
					|| group.beforeDagger.expressions[0] !is Alternation,
				E_EXCLAMATION_MARK_MUST_FOLLOW_AN_ALTERNATION_GROUP,
				"An exclamation mark (!) may only follow an "
					+ "alternation group or (for macros) an "
					+ "underscore"
			) ->
			{
				// The guillemet group should have had a single element, an
				// alternation.
				val alternation =
					group.beforeDagger.expressions[0] as Alternation
				NumberedChoice(
					startOfGroup,
					currentPositionForEnd,
					alternation)
			}
			else -> group
		}
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
		val tokenStart = currentPositionForStart - 1  // Include the ellipsis.
		incrementLeafArgumentCount()
		return when
		{
			peekFor(EXCLAMATION_MARK) -> RawTokenArgument(
				tokenStart, currentPositionForEnd, leafArgumentCount)
			peekFor(OCTOTHORP) -> RawLiteralTokenArgument(
				tokenStart, currentPositionForEnd, leafArgumentCount)
			else -> RawKeywordTokenArgument(
				tokenStart, currentPositionForEnd, leafArgumentCount)
		}
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
		// Include the underscore itself.
		val startInName = currentPositionForStart - 1
		incrementLeafArgumentCount()
		return when
		{
			peekFor(SINGLE_DAGGER) -> ArgumentInModuleScope(
				startInName, currentPositionForEnd, leafArgumentCount)
			peekFor(UP_ARROW) -> VariableQuote(
				startInName, currentPositionForEnd, leafArgumentCount)
			peekFor(EXCLAMATION_MARK) -> ArgumentForMacroOnly(
				startInName, currentPositionForEnd, leafArgumentCount)
			else -> Argument(
				startInName, currentPositionForEnd, leafArgumentCount)
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
	 *   [macro][MacroDescriptor]'s,
	 *   [prefix&#32;function][A_Macro.prefixFunctions], otherwise any
	 *   value past the total [numberOfSectionCheckpoints] for a method or macro
	 *   body.
	 * @throws SignatureException
	 *         If the function type is inappropriate for the method name.
	 */
	@Throws(SignatureException::class)
	fun checkImplementationSignature(
		functionType: A_Type,
		sectionNumber: Int = Integer.MAX_VALUE)
	{
		val argsTupleType = functionType.argsTupleType
		val sizes = argsTupleType.sizeRange
		val lowerBound = sizes.lowerBound
		val upperBound = sizes.upperBound
		if (!lowerBound.equals(upperBound) || !lowerBound.isInt)
		{
			// Method definitions (and other definitions) should take a
			// definite number of arguments.
			throwSignatureException(E_INCORRECT_NUMBER_OF_ARGUMENTS)
		}
		val lowerBoundInt = lowerBound.extractInt
		if (lowerBoundInt != numberOfArguments)
		{
			throwSignatureException(E_INCORRECT_NUMBER_OF_ARGUMENTS)
		}
		rootSequence.checkRootType(functionType.argsTupleType, sectionNumber)
	}

	/**
	 * Answer whether the given list is correctly internally structured for a
	 * send of this message.  It must recursively match the expected number of
	 * arguments, as well as have the expected permutations in the corresponding
	 * recursive [Expression]s.
	 *
	 * @param list
	 *   The [list&#32;phrase][ListPhraseDescriptor] or
	 *   [permuted&#32;list&#32;phrase][PermutedListPhraseDescriptor] being
	 *   checked for conformance with the expected argument structure.
	 * @return
	 *   Whether the supplied list is recursively of the right shape to be used
	 *   as the argument list for a call of this splitter's [A_Bundle].
	 */
	fun checkListStructure(list: A_Phrase): Boolean =
		rootSequence.checkListStructure(list)

	/**
	 * Does the message contain any groups?
	 *
	 * @return
	 *   `true` if the message contains any groups, `false` otherwise.
	 */
	val containsGroups get() = rootSequence.expressions.any(Expression::isGroup)

	/**
	 * Answer whether there are any reordered expressions anywhere inside this
	 * method name.  Reordering is specified by placing circled numbers
	 * (e.g., ①, ②) after all argument-yielding [Expression]s occurring in some
	 * [Sequence].  Either none or all must be numbered within any sequence, and
	 * the numbers must be a non-identity permutation (not simply ①, ②, ③,
	 * etc.).
	 */
	val recursivelyContainsReorders
		get() = rootSequence.recursivelyContainsReorders

	/**
	 * The computed list of [Simple] expressions recursively inside the
	 * [rootSequence].
	 */
	val allSimpleLeafIndices: List<Int>
		get()
		{
			val indices = mutableListOf<Int>()
			val work = ArrayDeque<Expression>()
			work.add(rootSequence)
			while (work.isNotEmpty())
			{
				val item = work.removeLast()
				if (item is Simple) indices.add(item.tokenIndex)
				work.addAll(item.children().reversed())
			}
			return indices
		}

	override fun toString() = buildString { dumpForDebug(this) }

	companion object
	{
		/**
		 * The public factory for splitting message names.  The result may be
		 * cached in an [LRUCache].  Since the [messageName] is used as a key in
		 * the cache, we force it to be [SHARED] here.
		 *
		 * Note that any exception thrown during splitting is captured in the
		 * cache as well, and rethrown when subsequently looking up that same
		 * name.
		 */
		fun split(messageName: A_String): MessageSplitter
		{
			return cache[messageName.makeShared()]
		}

		/**
		 * The cache of recently split message names.  The keys are [A_String]s
		 * which have been [SHARED].
		 */
		private val cache = LRUCache(10000, 100, ::MessageSplitter)

		/**
		 * The [set][A_Set] of all [errors][AvailErrorCode] that can happen
		 * during [message&#32;splitting][MessageSplitter].
		 */
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
			E_QUESTION_MARK_MUST_FOLLOW_A_SIMPLE_GROUP,
			E_VERTICAL_BAR_MUST_SEPARATE_TOKENS_OR_SIMPLE_GROUPS,
			E_EXCLAMATION_MARK_MUST_FOLLOW_AN_ALTERNATION_GROUP,
			E_DOUBLE_QUESTION_MARK_MUST_FOLLOW_A_TOKEN_OR_SIMPLE_GROUP,
			E_CASE_INSENSITIVE_EXPRESSION_CANONIZATION,
			E_EXPECTED_OPERATOR_AFTER_BACKQUOTE,
			E_UP_ARROW_MUST_FOLLOW_ARGUMENT,
			E_INCONSISTENT_ARGUMENT_REORDERING).makeShared()

		/** A String containing all 51 circled numbers. */
		private const val circledNumbersString =
			"⓪①②③④⑤⑥⑦⑧⑨⑩⑪⑫⑬⑭⑮⑯" +
				"⑰⑱⑲⑳㉑㉒㉓㉔㉕㉖㉗㉘㉙㉚㉛㉜㉝" +
				"㉞㉟㊱㊲㊳㊴㊵㊶㊷㊸㊹㊺㊻㊼㊽㊾㊿"

		/** How many circled numbers are in Unicode. */
		private val circledNumbersCount =
			circledNumbersString.codePointCount(0, circledNumbersString.length)

		/** An array of the circled number code points. */
		private val circledNumberCodePoints: IntArray =
			circledNumbersString.codePoints().iterator().asSequence().toList().toIntArray()

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
		val circledNumbersMap =
			circledNumberCodePoints.withIndex().associate { (i, cp) -> cp to i }

		/**
		 * The tuple of all encountered permutations (tuples of integers) found
		 * in all message names.  Keeping a statically accessible tuple shared
		 * between message names allows [bundle&#32;trees][A_BundleTree] to
		 * easily get to the permutations they need without having a separate
		 * per-tree structure.
		 *
		 * The field is an [AtomicReference], and is accessed in a wait-free way
		 * with compare-and-set and retry.  The tuple itself should always be
		 * marked as shared.  This mechanism is thread-safe.
		 */
		private val permutations = AtomicReference<A_Tuple>(emptyTuple)

		/**
		 * A statically-scoped [List] of unique constants needed as operands of
		 * some [ParsingOperation]s.  The inverse [Map] (but containing
		 * one-based indices) is kept in [constantsMap].
		 */
		private val constantsList = mutableListOf<AvailObject>()

		/**
		 * A statically-scoped map from Avail object to one-based index (into
		 * [constantsList], after adjusting to a zero-based [List]), for which
		 * some [ParsingOperation] needed to hold that constant as an operand.
		 */
		private val constantsMap = mutableMapOf<AvailObject, Int>()

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
		fun permutationAtIndex(index: Int): AvailObject =
			permutations.get().tupleAt(index)

		/**
		 * Answer the index of the given permutation (tuple of integers), adding
		 * it to the global [permutations] tuple if necessary.
		 *
		 * @param permutation
		 *   The permutation whose globally unique one-based index should be
		 *   determined.
		 * @return The permutation's one-based index.
		 */
		fun indexForPermutation(permutation: A_Tuple): Int
		{
			var checkedLimit = 0
			while (true)
			{
				val before = permutations.get()
				val newLimit = before.tupleSize
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
					return after.tupleSize
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
		fun indexForConstant(constant: A_BasicObject): Int
		{
			val strongConstant = constant.makeShared()
			constantsLock.read {
				val index = constantsMap[strongConstant]
				index?.let { return it }
			}

			constantsLock.safeWrite {
				val index = constantsMap[strongConstant]
				index?.let { return it }
				assert(constantsMap.size == constantsList.size)
				val newIndex = constantsList.size + 1
				constantsList.add(strongConstant)
				constantsMap[strongConstant] = newIndex
				return newIndex
			}
		}

		/** The position at which true is stored in the [constantsList]. */
		val indexForTrue = indexForConstant(trueObject)

		/** The position at which false is stored in the [constantsList]. */
		val indexForFalse = indexForConstant(falseObject)

		/**
		 * Answer the [AvailObject] having the given one-based index in the
		 * static [constantsList] [List].
		 *
		 * @param index
		 *   The one-based index of the constant to retrieve.
		 * @return
		 *   The [AvailObject] at the given index.
		 */
		fun constantForIndex(index: Int): AvailObject =
			constantsLock.read { constantsList[index - 1] }

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
				expression.yieldsValue || expression.underscoreCount > 0,
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
		@Throws(MalformedMessageException::class)
		fun throwMalformedMessageException(
			errorCode: AvailErrorCode, errorMessage: String
		): Nothing
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
		fun isUnderscoreOrSpaceOrOperator(cp: Int) =
			cp == '_'.code
				|| cp == '…'.code
				|| cp == ' '.code
				|| cp == '/'.code
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
		fun isOperator(cp: Int) =
			!(Character.isDigit(cp)
				|| Character.isUnicodeIdentifierStart(cp)
				|| Character.isSpaceChar(cp)
				|| Character.isWhitespace(cp)
				|| cp < 32
				|| cp in 127 .. 159
				|| !Character.isDefined(cp)
				|| cp == '_'.code
				|| cp == '"'.code
				|| cp == '\uFEFF'.code)
	}
}
