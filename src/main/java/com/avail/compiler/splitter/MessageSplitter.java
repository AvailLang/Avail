/*
 * MessageSplitter.java
 * Copyright © 1993-2018, The Avail Foundation, LLC.
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

package com.avail.compiler.splitter;

import com.avail.compiler.ParsingOperation;
import com.avail.compiler.problems.CompilerDiagnostics;
import com.avail.descriptor.*;
import com.avail.descriptor.AtomDescriptor.SpecialAtom;
import com.avail.descriptor.TokenDescriptor.TokenType;
import com.avail.exceptions.AvailErrorCode;
import com.avail.exceptions.MalformedMessageException;
import com.avail.exceptions.SignatureException;
import com.avail.utility.Locks.Auto;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static com.avail.compiler.splitter.MessageSplitter.Metacharacter.*;
import static com.avail.descriptor.AtomDescriptor.falseObject;
import static com.avail.descriptor.AtomDescriptor.trueObject;
import static com.avail.descriptor.ObjectTupleDescriptor.tupleFromList;
import static com.avail.descriptor.SetDescriptor.set;
import static com.avail.descriptor.StringDescriptor.stringFrom;
import static com.avail.descriptor.TupleDescriptor.emptyTuple;
import static com.avail.exceptions.AvailErrorCode.*;
import static com.avail.utility.Casts.cast;
import static com.avail.utility.Locks.auto;

/**
 * {@code MessageSplitter} is used to split Avail message names into a sequence
 * of {@linkplain ParsingOperation instructions} that can be used directly for
 * parsing.
 *
 * <p>Message splitting occurs in two phases.  In the first setPhase, the
 * message is tokenized and parsed into an abstract {@link Expression} tree. In
 * the second setPhase, a {@linkplain TupleTypeDescriptor tuple type} of {@link
 * PhraseTypeDescriptor phrase types} is supplied, and produces a tuple of
 * integer-encoded {@link ParsingOperation}s.</p>
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class MessageSplitter
{
	/** The metacharacters used in message names. */
	public enum Metacharacter
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
		 * <p>The only characters that can be backquoted (and must be to use
		 * them in a non-special way) are the {@code Metacharacter}s, space, and
		 * the {@linkplain #circledNumbersString circled numbers}.</p>
		 */
		BACK_QUOTE("`"),

		/**
		 * A close-guillemet (») indicates the end of a group or other structure
		 * that started with an {@link #OPEN_GUILLEMET} open-guillemet («).
		 */
		CLOSE_GUILLEMET("»"),

		/**
		 * A dollar sign ($) after an {@link #ELLIPSIS} (…) indicates that a
		 * string-valued literal token should be consumed from the Avail source
		 * code at this position.  This is accomplished through the use of a
		 * {@link RawStringLiteralTokenArgument}.
		 */
		DOLLAR_SIGN("$"),

		/**
		 * The double-dagger (‡) is used within a {@link Group} to delimit the
		 * left part, which repeats, from the right part, which separates
		 * occurrences of the left part.
		 *
		 * <p>The actual method argument (or portion of a method argument
		 * associated with this subexpression) must be able to accept some
		 * number of occurrences of tuples whose size is the total number of
		 * value-yielding subexpressions in the group, plus one final tuple that
		 * only has values yielded from subexpressions left of the
		 * double-dagger.  In the special, most common case of the left side
		 * having exactly one yielding expression, and the right side yielding
		 * nothing, the inner one-element tuples are elided.</p>
		 */
		DOUBLE_DAGGER("‡"),

		/**
		 * The double-question-mark (⁇) is a single Unicode character (U+2047).
		 * It can be placed after a token or after a simple group (a group that
		 * has no right side and does not yield anything from its left side).
		 * In the case of a token, it allows zero or one occurrences of the
		 * token to occur in the Avail source, and <em>discards</em> information
		 * about whether the token was encountered.  Similarly, when applied to
		 * a simple group it accepts zero or one occurrences of the group's left
		 * side, discarding the distinction.  In both cases, the
		 * double-question-mark causes a {@link CompletelyOptional} to be built.
		 *
		 * <p>This can be useful to make expressions read more fluidly by
		 * allowing inessential words to be elided when they would be
		 * grammatically awkward.</p>
		 */
		DOUBLE_QUESTION_MARK("⁇"),

		/**
		 * An ellipsis (…) indicates that a single token should be consumed from
		 * the Avail source code (tokens are produced from the actual file
		 * content via {@link A_Lexer}s).  There are a few variants:
		 *
		 * <ul>
		 *     <li>If left unadorned, it creates a {@link
		 *         RawKeywordTokenArgument}, which matches a single {@link
		 *         A_Token} of kind {@link TokenType#KEYWORD}.</li>
		 *     <li>If followed by an {@link #OCTOTHORP} (#), it creates a {@link
		 *         RawWholeNumberLiteralTokenArgument}, which matches a single
		 *         {@link A_Token} of kind {@link TokenType#LITERAL} which
		 *         yields an integer in the range [0..∞).</li>
		 *     <li>If followed by a {@link #DOLLAR_SIGN} ($), it creates a
		 *         {@link RawStringLiteralTokenArgument}, which matches a
		 *         single {@link A_Token} of kind {@link TokenType#LITERAL}
		 *         which yields a string.</li>
		 *     <li>If followed by an {@link #EXCLAMATION_MARK} (!), it creates a
		 *         {@link RawTokenArgument}, which matches a single {@link
		 *         A_Token} of any kind except {@link TokenType#WHITESPACE} or
		 *         {@link TokenType#COMMENT}.</li>
		 * </ul>
		 */
		ELLIPSIS("…"),

		/**
		 * The exclamation mark (!) can follow an {@link #ELLIPSIS} (…) to cause
		 * creation of a {@link RawTokenArgument}, which matches any token that
		 * isn't whitespace or a comment.
		 *
		 * <p>When it follows an {@link #UNDERSCORE} (_), it creates an {@link
		 * ArgumentForMacroOnly}, which matches any {@link A_Phrase}, even those
		 * that yield ⊤ or ⊥.  Since these argument types are forbidden for
		 * methods, this construct can only be used in macros, where what is
		 * passed as an argument is a <em>phrase</em> yielding that type.</p>
		 */
		EXCLAMATION_MARK("!"),

		/**
		 * An octothorp (#) after an {@link #ELLIPSIS} (…) indicates that a
		 * whole-number-valued literal token should be consumed from the Avail
		 * source code at this position.  This is accomplished through the use
		 * of a {@link RawWholeNumberLiteralTokenArgument}.
		 */
		OCTOTHORP("#"),

		/**
		 * An open-guillemet («) indicates the start of a group or other
		 * structure that will eventually end with a {@link #CLOSE_GUILLEMET}
		 * (»).
		 */
		OPEN_GUILLEMET("«"),

		/**
		 * A question mark (?) may follow a {@link Simple} expression, which
		 * creates an {@link Optional}, which yields a boolean literal
		 * indicating whether the token was present or not.
		 *
		 * <p>When a question mark follows a simple {@link Group}, i.e., one
		 * that has no {@link #DOUBLE_DAGGER} (‡) and yields no values, it
		 * creates an {@link Optional}.  This causes a boolean literal to be
		 * generated at a call site, indicating whether the sequence of tokens
		 * from that group actually occurred.</p>
		 *
		 * <p>When a question mark follows a {@link Group} which produces one or
		 * more values, it simply limits that group to [0..1] occurrences.  The
		 * {@link #DOUBLE_DAGGER} (‡) is forbidden here as well, because the
		 * sequence after the double-dagger would only ever occur between
		 * repetitions, but there cannot be two of them.</p>
		 */
		QUESTION_MARK("?"),

		/**
		 * A section sign (§) indicates where, in the parsing of a macro
		 * invocation, it should invoke one of its {@link
		 * A_Definition#prefixFunctions()}.  The order of section signs in the
		 * method name corresponds with the order of the prefix functions.
		 *
		 * <p>Prefix functions are passed all arguments that have been parsed so
		 * far.  They can access a fiber-specific variable, {@link
		 * SpecialAtom#CLIENT_DATA_GLOBAL_KEY}, which allows manipulation of the
		 * current variables that are in scope, and also early parse rejection
		 * based on what has been parsed so far.</p>
		 */
		SECTION_SIGN("§"),

		/**
		 * A single-dagger (†) following an {@link #UNDERSCORE} (_) causes
		 * creation of an {@link ArgumentInModuleScope}.  This will parse an
		 * expression that does not use any variables in the local scope, then
		 * evaluate it and wrap it in a {@link LiteralTokenDescriptor literal
		 * token}.
		 */
		SINGLE_DAGGER("†"),

		/**
		 * An {@link Expression} followed by a tilde (~) causes all {@link
		 * Simple} expressions anywhere within it to match case-insensitively
		 * to the tokens lexed from the Avail source code.  All of these simple
		 * expressions are required to be specified in lower case to break a
		 * pointless symmetry about how to name the method.
		 */
		TILDE("~"),

		/**
		 * An underscore indicates where a subexpression should be parsed.
		 *
		 * <ul>
		 *     <li>If unadorned, an {@link Argument} is created, which expects
		 *         an expression that yields something other than ⊤ or ⊥.</li>
		 *     <li>If followed by an {@link #EXCLAMATION_MARK} (!), an {@link
		 *         ArgumentForMacroOnly} is constructed.  This allows
		 *         expressions that yield ⊤ or ⊥, which are only permitted in
		 *         macros.</li>
		 *     <li>If followed by a {@link #SINGLE_DAGGER} (†), the expression
		 *         must not attempt to use any variables in the current scope.
		 *         The expression will be evaluated at compile time, and wrapped
		 *         in a literal phrase.</li>
		 *     <li>If followed by a {@link #UP_ARROW} (↑), a {@linkplain
		 *         VariableUsePhraseDescriptor variable use phrase} must be
		 *         supplied, which is converted into a {@linkplain
		 *         ReferencePhraseDescriptor reference phrase}.</li>
		 * </ul>
		 */
		UNDERSCORE("_"),

		/**
		 * If an up-arrow (↑) follows an {@link #UNDERSCORE}, a {@link
		 * VariableQuote} is created, which expects a {@linkplain
		 * VariableUsePhraseDescriptor variable use phrase}, which will be
		 * automatically converted into a {@linkplain ReferencePhraseDescriptor
		 * reference phrase}.
		 */
		UP_ARROW("↑"),

		/**
		 * The vertical bar (|) separates alternatives within an {@link
		 * Alternation}.  The alternatives may be {@link Simple} expressions, or
		 * simple {@link Group}s (having no double-dagger, and yielding no
		 * argument values).
		 */
		VERTICAL_BAR("|");

		/** The Avail {@link A_String} denoting this metacharacter. */
		public final A_String string;

		/**
		 * This collects all metacharacters, including space and circled
		 * numbers.  These are the characters that can be {@link #BACK_QUOTE}d.
		 */
		private static final Set<Integer> backquotableCodepoints =
			new HashSet<>();

		// Load the set with metacharacters, space, and circled numbers.
		static
		{
			backquotableCodepoints.add((int)' ');
			backquotableCodepoints.addAll(circledNumbersMap.keySet());
			for (final Metacharacter metacharacter : values())
			{
				backquotableCodepoints.add(metacharacter.string.tupleCodePointAt(1));
			}
		}

		/**
		 * Answer whether the given Unicode codepoint may be {@link
		 * #BACK_QUOTE}d.
		 *
		 * @param codePoint
		 *        The Unicode codepoint to check.
		 * @return Whether the character can be backquoted in a method name.
		 */
		public static boolean canBeBackQuoted (final int codePoint)
		{
			return backquotableCodepoints.contains(codePoint);
		}

		/**
		 * Initialize an enum instance.
		 *
		 * @param javaString The Java {@link String} denoting the metacharacter.
		 */
		Metacharacter (final String javaString)
		{
			string = stringFrom(javaString).makeShared();
			assert string.tupleSize() == 1;
		}
	}

	/**
	 * The {@linkplain A_Set set} of all {@linkplain AvailErrorCode errors} that
	 * can happen during {@linkplain MessageSplitter message splitting}.
	 */
	public static final A_Set possibleErrors = set(
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
		E_INCONSISTENT_ARGUMENT_REORDERING).makeShared();

	/** A String containing all 51 circled numbers. */
	static final String circledNumbersString =
		"⓪①②③④⑤⑥⑦⑧⑨⑩⑪⑫⑬⑭⑮⑯" +
		"⑰⑱⑲⑳㉑㉒㉓㉔㉕㉖㉗㉘㉙㉚㉛㉜㉝" +
		"㉞㉟㊱㊲㊳㊴㊵㊶㊷㊸㊹㊺㊻㊼㊽㊾㊿";

	/** How many circled numbers are in Unicode. */
	static final int circledNumbersCount =
		circledNumbersString.codePointCount(0, circledNumbersString.length());

	/** An array of the circled number code points. */
	private static final int [] circledNumberCodePoints =
		new int[circledNumbersCount];

	/**
	 * Answer the Unicode codepoint for a circled number with the given numeric
	 * value.  The values for which values are available in Unicode are 0..50.
	 *
	 * @param number The number.
	 * @return The codepoint which depicts that number inside a circle.
	 */
	static int circledNumberCodePoint (final int number)
	{
		assert number >= 0 && number < circledNumbersCount;
		return circledNumberCodePoints[number];
	}

	/**
	 * A map from the Unicode code points for the circled number characters
	 * found in various regions of the Unicode code space.  See {@link
	 * #circledNumbersString}.
	 */
	static final Map<Integer, Integer> circledNumbersMap =
		new HashMap<>(circledNumbersCount);

	/* Initialize circledNumbersMap and circledNumberCodePoints */
	static
	{
		int positionInString = 0;
		int number = 0;
		while (positionInString < circledNumbersString.length())
		{
			final int codePoint =
				circledNumbersString.codePointAt(positionInString);
			circledNumbersMap.put(codePoint, number);
			circledNumberCodePoints[number++] = codePoint;
			positionInString += Character.charCount(codePoint);
		}
	}

	/**
	 * The Avail string to be parsed.
	 */
	final A_String messageName;

	/**
	 * The individual tokens ({@linkplain StringDescriptor strings})
	 * constituting the message.
	 *
	 * <ul>
	 * <li>Alphanumerics are in runs, separated from other
	 * alphanumerics by a single space.</li>
	 * <li>Operator characters are never beside spaces, and are always parsed as
	 * individual tokens.</li>
	 * <li>{@linkplain Metacharacter#OPEN_GUILLEMET Open guillemet} («),
	 * {@linkplain Metacharacter#DOUBLE_DAGGER double dagger} (‡), and
	 * {@linkplain Metacharacter#CLOSE_GUILLEMET close guillemet} (») are
	 * used to indicate repeated or optional substructures.</li>
	 * <li>The characters {@linkplain Metacharacter#OCTOTHORP octothorp}
	 * (#) and {@linkplain Metacharacter#QUESTION_MARK question mark} (?)
	 * modify the output of repeated substructures to produce either a count
	 * of the repetitions or a boolean indicating whether an optional
	 * subexpression (expecting no arguments) was present.</li>
	 * <li>Placing a {@linkplain Metacharacter#QUESTION_MARK question mark}
	 * (?) after a group containing arguments but no {@linkplain
	 * Metacharacter#DOUBLE_DAGGER double-dagger} (‡) will limit the
	 * repetitions of the group to at most one.  Although limiting the method
	 * definitions to only accept 0..1 occurrences would accomplish the same
	 * grammatical narrowing, the parser might still attempt to parse more than
	 * one occurrence, leading to unnecessarily confusing diagnostics.</li>
	 * <li>An {@linkplain Metacharacter#EXCLAMATION_MARK exclamation mark}
	 * (!) can follow a group of alternations to produce the 1-based index of
	 * the alternative that actually occurred.</li>
	 * <li>An {@linkplain Metacharacter#UNDERSCORE underscore} (_)
	 * indicates where an argument occurs.</li>
	 * <li>A {@linkplain Metacharacter#SINGLE_DAGGER single dagger} (†) may
	 * occur immediately after an underscore to cause the argument expression to
	 * be evaluated in the static scope during compilation, wrapping the value
	 * in a {@linkplain LiteralPhraseDescriptor literal phrase}.  This is
	 * applicable to both methods and macros.  The expression is subject to
	 * grammatical restrictions, and it must yield a value of suitable
	 * type.</li>
	 * <li>An {@linkplain Metacharacter#UP_ARROW up-arrow} (↑) after an
	 * underscore indicates an in-scope variable name is to be parsed.  The
	 * subexpression causes the variable itself to be provided, rather than its
	 * value.</li>
	 * <li>An {@linkplain Metacharacter#EXCLAMATION_MARK exclamation mark}
	 * (!) may occur after the underscore instead, to indicate the argument
	 * expression may be ⊤-valued or ⊥-valued.  Since a function (and therefore
	 * a method definition) cannot accept a ⊤-valued argument, this mechanism
	 * only makes sense for macros, since macros bodies are passed phrases,
	 * which may be typed as <em>yielding</em> a top-valued result.</li>
	 * <li>An {@linkplain Metacharacter#ELLIPSIS ELLIPSIS} (…) matches a
	 * single {@linkplain TokenDescriptor keyword token}.</li>
	 * <li>An {@linkplain Metacharacter#EXCLAMATION_MARK exclamation mark}
	 * (!) after an ELLIPSIS indicates <em>any</em> token will be accepted at
	 * that position.</li>
	 * <li>An {@linkplain Metacharacter#OCTOTHORP OCTOTHORP} (#) after an
	 * ellipsis indicates only a <em>literal</em> token will be accepted.</li>
	 * <li>The N<sup>th</sup> {@linkplain Metacharacter#SECTION_SIGN section
	 * sign} (§) in a message name indicates where a macro's N<sup>th</sup>
	 * {@linkplain A_Definition#prefixFunctions() prefix function} should be
	 * invoked with the current parse stack up to that point.</li>
	 * <li>A {@linkplain Metacharacter#BACK_QUOTE backquote} (`) can
	 * precede any operator character, such as guillemets or double dagger, to
	 * ensure it is not used in a special way. A backquote may also operate on
	 * another backquote (to indicate that an actual backquote token will appear
	 * in a call).</li>
	 * </ul>
	 * @see #messagePartsTuple
	 */
	final List<A_String> messagePartsList = new ArrayList<>(10);

	/**
	 * The sequence of strings constituting discrete tokens of the message name.
	 * @see #messagePartsList
	 */
	private final A_Tuple messagePartsTuple;

	/**
	 * A collection of one-based positions in the original string, corresponding
	 * to the {@link #messagePartsList} that have been extracted.
	 */
	final List<Integer> messagePartPositions = new ArrayList<>(10);

	/** The current one-based parsing position in the list of tokens. */
	int messagePartPosition;

	/**
	 * A count of the number of unbackquoted underscores or ellipses in the
	 * message name.
	 */
	int leafArgumentCount = 0;

	/**
	 * The number of {@link SectionCheckpoint}s encountered so far.
	 */
	public int numberOfSectionCheckpoints;

	/** The top-most {@linkplain Sequence sequence}. */
	final Sequence rootSequence;

	/**
	 * The tuple of all encountered permutations (tuples of integers) found in
	 * all message names.  Keeping a statically accessible tuple shared between
	 * message names allows {@link A_BundleTree bundle trees} to easily get to
	 * the permutations they need without having a separate per-tree structure.
	 *
	 * <p>The field is an {@link AtomicReference}, and is accessed in a
	 * wait-free way with compare-and-set and retry.  The tuple itself should
	 * always be marked as shared.  This mechanism is thread-safe.</p>
	 */
	public static final AtomicReference<A_Tuple> permutations =
		new AtomicReference<>(emptyTuple());

	/**
	 * A statically-scoped {@link List} of unique constants needed as operands
	 * of some {@link ParsingOperation}s.  The inverse {@link Map} (but
	 * containing one-based indices) is kept in #constantsMap}.
	 */
	private static final List<AvailObject> constantsList = new ArrayList<>(100);

	/**
	 * A statically-scoped map from Avail object to one-based index (into
	 * {@link #constantsList}, after adjusting to a zero-based {@link List}),
	 * for which some {@link ParsingOperation} needed to hold that constant as
	 * an operand.
	 */
	private static final Map<AvailObject, Integer> constantsMap =
		new HashMap<>(100);

	/**
	 * A lock to protect {@link #constantsList} and {@link #constantsMap}.
	 */
	private static final ReadWriteLock constantsLock =
		new ReentrantReadWriteLock();

	/**
	 * Answer the permutation having the given one-based index.  We need a read
	 * barrier here, but no lock, since the tuple of tuples is only appended to,
	 * ensuring all extant indices will always be valid.
	 *
	 * @param index The index of the permutation to retrieve.
	 * @return The permutation (a {@linkplain A_Tuple tuple} of Avail integers).
	 */
	public static A_Tuple permutationAtIndex (final int index)
	{
		return permutations.get().tupleAt(index);
	}

	/**
	 * Answer the index of the given permutation (tuple of integers), adding it
	 * to the global {@link MessageSplitter#constantsList} if necessary.
	 *
	 * @param permutation
	 *        The permutation whose globally unique one-based index should be
	 *        determined.
	 * @return The permutation's one-based index.
	 */
	public static int indexForPermutation (final A_Tuple permutation)
	{
		int checkedLimit = 0;
		while (true)
		{
			final A_Tuple before = permutations.get();
			final int newLimit = before.tupleSize();
			for (int i = checkedLimit + 1; i <= newLimit; i++)
			{
				if (before.tupleAt(i).equals(permutation))
				{
					// Already exists.
					return i;
				}
			}
			final A_Tuple after =
				before.appendCanDestroy(permutation, false).makeShared();
			if (permutations.compareAndSet(before, after))
			{
				// Added it successfully.
				return after.tupleSize();
			}
			checkedLimit = newLimit;
		}
	}

	/**
	 * Answer the index of the given constant, adding it to the global {@link
	 * #constantsList} and {@link #constantsMap}} if necessary.
	 *
	 * @param constant
	 *        The type to look up or add to the global index.
	 * @return The one-based index of the type, which can be retrieved later via
	 *         {@link #constantForIndex(int)}.
	 */
	public static int indexForConstant (final A_BasicObject constant)
	{
		final AvailObject strongConstant = constant.makeShared();
		try (final Auto ignored = auto(constantsLock.readLock()))
		{
			final Integer index = constantsMap.get(strongConstant);
			if (index != null)
			{
				return index;
			}
		}

		try (final Auto ignored = auto(constantsLock.writeLock()))
		{
			final Integer index = constantsMap.get(strongConstant);
			if (index != null)
			{
				return index;
			}
			assert constantsMap.size() == constantsList.size();
			final int newIndex = constantsList.size() + 1;
			constantsList.add(strongConstant);
			constantsMap.put(strongConstant, newIndex);
			return newIndex;
		}
	}

	/** The position at which true is stored in the {@link #constantsList}. */
	private static final int indexForTrue = indexForConstant(trueObject());

	/**
	 * The position at which true is stored in the {@link #constantsList}.
	 *
	 * @return {@code true}'s index in the {@link #constantsList}.
	 */
	static int indexForTrue ()
	{
		return indexForTrue;
	}

	/** The position at which false is stored in the {@link #constantsList}. */
	private static final int indexForFalse = indexForConstant(falseObject());

	/**
	 * The position at which false is stored in the {@link #constantsList}.
	 *
	 * @return {@code false}'s index in the {@link #constantsList}.
	 */
	static int indexForFalse ()
	{
		return indexForFalse;
	}

	/**
	 * Answer the {@link AvailObject} having the given one-based index in the
	 * static {@link #constantsList} {@link List}.
	 *
	 * @param index The one-based index of the constant to retrieve.
	 * @return The {@link AvailObject} at the given index.
	 */
	public static AvailObject constantForIndex (final int index)
	{
		try (final Auto ignored = auto(constantsLock.readLock()))
		{
			return constantsList.get(index - 1);
		}
	}

	/**
	 * Construct a new {@code MessageSplitter}, parsing the provided message
	 * into token strings and generating {@linkplain ParsingOperation parsing
	 * instructions} for parsing occurrences of this message.
	 *
	 * @param messageName
	 *        An Avail {@linkplain StringDescriptor string} specifying the
	 *        keywords and arguments of some message being defined.
	 * @throws MalformedMessageException
	 *         If the message name is malformed.
	 */
	public MessageSplitter (final A_String messageName)
	throws MalformedMessageException
	{
		this.messageName = messageName.makeShared();
		try
		{
			splitMessage();
			messagePartPosition = 1;
			rootSequence = parseSequence();

			if (!atEnd())
			{
				peekFor(
					CLOSE_GUILLEMET,
					true,
					E_UNBALANCED_GUILLEMETS,
					"close guillemet (») with no corresponding open "
						+ "guillemet («)");
				peekFor(
					DOUBLE_DAGGER,
					true,
					E_UNBALANCED_GUILLEMETS,
					"double-dagger (‡) outside of a group");
				throwMalformedMessageException(
					E_UNBALANCED_GUILLEMETS,
					"Encountered unexpected character: "
						+ currentMessagePart());
			}
			messagePartsTuple = tupleFromList(messagePartsList).makeShared();
		}
		catch (final MalformedMessageException e)
		{
			// Add contextual text and rethrow it.
			throw new MalformedMessageException(
				e.errorCode(),
				() ->
				{
					final StringBuilder builder = new StringBuilder();
					builder.append(e.describeProblem());
					builder.append(". See arrow (");
					builder.append(CompilerDiagnostics.errorIndicatorSymbol);
					builder.append(") in: \"");
					final int characterIndex =
						messagePartPosition > 0
							? messagePartPosition <= messagePartPositions.size()
							? messagePartPositions.get(
							messagePartPosition - 1)
							: messageName.tupleSize() + 1
							: 0;
					final A_String before =
						messageName.copyStringFromToCanDestroy(
							1, characterIndex - 1, false);
					final A_String after =
						messageName.copyStringFromToCanDestroy(
							characterIndex, messageName.tupleSize(), false);
					builder.append(before.asNativeString());
					builder.append(CompilerDiagnostics.errorIndicatorSymbol);
					builder.append(after.asNativeString());
					builder.append('"');
					return builder.toString();
				});
		}
	}

	/**
	* Dump debugging information about this {@code MessageSplitter} to the
	 * specified {@linkplain StringBuilder builder}.
	*
	* @param builder
	*        The accumulator.
	*/
	public void dumpForDebug (final StringBuilder builder)
	{
		builder.append(messageName.asNativeString());
		builder.append("\n------\n");
		for (final A_String part : messagePartsList)
		{
			builder.append('\t');
			builder.append(part.asNativeString());
			builder.append('\n');
		}
	}

	/**
	 * Answer the {@link A_String} that is being decomposed as a message name.
	 *
	 * @return The name of the message being split.
	 */
	public A_String messageName ()
	{
		return messageName;
	}

	/**
	 * Answer a {@linkplain TupleDescriptor tuple} of Avail {@linkplain
	 * StringDescriptor strings} comprising this message.
	 *
	 * @return A tuple of strings.
	 */
	public A_Tuple messageParts ()
	{
		return messagePartsTuple;
	}

	/**
	 * Answer the 1-based position of the current message part in the message
	 * name.
	 *
	 * @return The current 1-based position in the message name.
	 */
	private int positionInName ()
	{
		if (atEnd())
		{
			return messageName().tupleSize();
		}
		return messagePartPositions.get(messagePartPosition - 1);
	}

	/**
	 * Answer whether parsing has reached the end of the message parts.
	 *
	 * @return True if the current position has consumed the last message part.
	 */
	private boolean atEnd ()
	{
		return messagePartPosition > messagePartsList.size();
	}

	/**
	 * Answer the current message part, or {@code null} if we are {@link
	 * #atEnd()}.  Do not consume the message part.
	 *
	 * @return The current message part or null.
	 */
	private @Nullable A_String currentMessagePartOrNull ()
	{
		return atEnd() ? null : messagePartsList.get(messagePartPosition - 1);
	}

	/**
	 * Answer the current message part.  We must not be {@link #atEnd()}.  Do
	 * not consume the message part.
	 *
	 * @return The current message part.
	 */
	private A_String currentMessagePart ()
	{
		assert !atEnd();
		return messagePartsList.get(messagePartPosition - 1);
	}

	/**
	 * Pretty-print a send of this message with given argument phrases.
	 *
	 * @param sendPhrase
	 *        The {@linkplain SendPhraseDescriptor send phrase} that is being
	 *        printed.
	 * @param builder
	 *        A {@link StringBuilder} on which to pretty-print the send of my
	 *        message with the given arguments.
	 * @param indent
	 *        The current indentation level.
	 */
	public void printSendNodeOnIndent (
		final A_Phrase sendPhrase,
		final StringBuilder builder,
		final int indent)
	{
		builder.append('«');
		rootSequence.printWithArguments(
			sendPhrase.argumentsListNode().expressionsTuple().iterator(),
			builder,
			indent);
		builder.append('»');
	}

	/**
	 * Answer a {@linkplain TupleDescriptor tuple} of Avail {@linkplain
	 * IntegerDescriptor integers} describing how to parse this message.
	 * See {@code MessageSplitter} and {@link ParsingOperation} for an
	 * understanding of the parse instructions.
	 *
	 * @param phraseType
	 *        The phrase type (yielding a tuple type) for this signature.
	 * @return The tuple of integers encoding parse instructions for this
	 *         message and argument types.
	 */
	public A_Tuple instructionsTupleFor (final A_Type phraseType)
	{
		final InstructionGenerator generator = new InstructionGenerator();
		rootSequence.emitOn(phraseType, generator, WrapState.PUSHED_LIST);
		generator.optimizeInstructions();
		return generator.instructionsTuple();
	}

	/**
	 * Answer a {@link List} of {@link Expression} objects that correlates with
	 * the {@linkplain #instructionsTupleFor(A_Type) parsing instructions}
	 * generated for the message name and the provided signature tuple type.
	 * Note that the list is 0-based and the tuple is 1-based.
	 *
	 * @param phraseType
	 *        The phrase type (yielding a tuple type) for this signature.
	 * @return A list that indicates the origin Expression of each {@link
	 *         ParsingOperation}.
	 */
	private List<Expression> originExpressionsFor (final A_Type phraseType)
	{
		final InstructionGenerator generator = new InstructionGenerator();
		rootSequence.emitOn(phraseType, generator, WrapState.PUSHED_LIST);
		generator.optimizeInstructions();
		final List<Expression> expressions = generator.expressionList();
		assert expressions.size() == generator.instructionsTuple().tupleSize();
		return expressions;
	}

	/**
	 * Answer a String containing the message name with an indicator inserted to
	 * show which area of the message is being parsed by the given program
	 * counter.
	 *
	 * @param phraseType
	 *        The phrase type (yielding a tuple type) for this signature.
	 * @param pc
	 *        The program counter for which an appropriate annotation should be
	 *        inserted into the name.  It's between 1 and the number of
	 *        instructions generated plus one.
	 * @return The annotated message string.
	 */
	public String highlightedNameFor(final A_Type phraseType, final int pc)
	{
		final String string = messageName().asNativeString();
		final List<Expression> expressions = originExpressionsFor(phraseType);
		final int zeroBasedPosition;
		if (pc == expressions.size() + 1)
		{
			zeroBasedPosition = string.length();
		}
		else
		{
			final Expression expression = expressions.get(pc - 1);
			zeroBasedPosition = expression.positionInName - 1;
		}
		final String annotatedString =
			string.substring(0, zeroBasedPosition)
				+ CompilerDiagnostics.errorIndicatorSymbol
				+ string.substring(zeroBasedPosition);
		return stringFrom(annotatedString).toString();
	}

	/**
	 * Decompose the message name into its constituent token strings. These
	 * can be subsequently parsed to generate the actual parse instructions.
	 * Do not do any semantic analysis here, not even backquote processing –
	 * that would lead to confusion over whether an operator was supposed to be
	 * treated as a special token like open-guillemet («) rather than like a
	 * backquote-escaped open-guillemet token.
	 *
	 * @throws MalformedMessageException If the signature is invalid.
	 */
	private void splitMessage ()
	throws MalformedMessageException
	{
		if (messageName.tupleSize() == 0)
		{
			return;
		}
		int position = 1;
		while (position <= messageName.tupleSize())
		{
			final int ch = messageName.tupleCodePointAt(position);
			if (ch == ' ')
			{
				if (messagePartsList.size() == 0
					|| isUnderscoreOrSpaceOrOperator(
						messageName.tupleCodePointAt(position - 1)))
				{
					// Problem is before the space.  Stuff the rest of the input
					// in as a final token to make diagnostics look right.
					messagePartsList.add(
						messageName.copyStringFromToCanDestroy(
							position, messageName.tupleSize(), false));
					messagePartPositions.add(position);
					messagePartPosition = messagePartsList.size() - 1;
					throwMalformedMessageException(
						E_METHOD_NAME_IS_NOT_CANONICAL,
						"Expected alphanumeric character before space");
				}
				//  Skip the space.
				position++;
				if (position > messageName.tupleSize()
						|| isUnderscoreOrSpaceOrOperator(
							messageName.tupleCodePointAt(position)))
				{
					if ((char) messageName.tupleCodePointAt(position) != '`'
						|| position == messageName.tupleSize()
						|| messageName.tupleCodePointAt(position + 1) != '_')
					{
						// Problem is after the space.
						messagePartsList.add(
							messageName.copyStringFromToCanDestroy(
								position, messageName.tupleSize(), false));
						messagePartPositions.add(position);
						messagePartPosition = messagePartsList.size();
						throwMalformedMessageException(
							E_METHOD_NAME_IS_NOT_CANONICAL,
							"Expected alphanumeric character after space");
					}
					// This is legal; we want to be able to parse
					// expressions like "a _b".
				}
			}
			else if (ch == '`')
			{
				// Despite what the method comment says, backquote needs to be
				// processed specially when followed by an underscore so that
				// identifiers containing (escaped) underscores can be treated
				// as a single token. Otherwise, they are unparseable.
				if (position == messageName.tupleSize()
					|| messageName.tupleCodePointAt(position + 1) != '_')
				{
					// We didn't find an underscore, so we need to deal with the
					// backquote in the usual way.
					messagePartsList.add(
						messageName.copyStringFromToCanDestroy(
							position, position, false));
					messagePartPositions.add(position);
					position++;
				}
				else
				{
					boolean sawRegular = false;
					final int start = position;
					while (position <= messageName.tupleSize())
					{
						if (!isUnderscoreOrSpaceOrOperator(
							messageName.tupleCodePointAt(position)))
						{
							sawRegular = true;
							position++;
						}
						else if (
							messageName.tupleCodePointAt(position) == '`'
							&& position + 1 <= messageName.tupleSize()
							&& messageName.tupleCodePointAt(position + 1)
								== '_')
						{
							position += 2;
						}
						else
						{
							break;
						}
					}
					if (sawRegular)
					{
						// If we ever saw something other than `_ in the
						// sequence, then produce a single token that includes
						// the underscores (but not the backquotes).
						final StringBuilder builder = new StringBuilder();
						for (int i = start, limit = position - 1;
							i <= limit;
							i++)
						{
							final int cp = messageName.tupleCodePointAt(i);
							if (cp != '`')
							{
								builder.appendCodePoint(cp);
							}
						}
						messagePartsList.add(stringFrom(builder.toString()));
						messagePartPositions.add(position);
					}
					else
					{
						// If we never saw a regular character, then produce a
						// token for each character.
						for (int i = start, limit = position - 1;
							i <= limit;
							i++)
						{
							messagePartsList.add(
								messageName.copyStringFromToCanDestroy(
									i, i, false));
							messagePartPositions.add(i);
						}
					}
				}
			}
			else if (isUnderscoreOrSpaceOrOperator(ch))
			{
				messagePartsList.add(
					messageName.copyStringFromToCanDestroy(
						position, position, false));
				messagePartPositions.add(position);
				position++;
			}
			else
			{
				messagePartPositions.add(position);
				boolean sawIdentifierUnderscore = false;
				final int start = position;
				while (position <= messageName.tupleSize())
				{
					if (!isUnderscoreOrSpaceOrOperator(
						messageName.tupleCodePointAt(position)))
					{
						position++;
					}
					else if (messageName.tupleCodePointAt(position) == '`'
						&& position + 1 <= messageName.tupleSize()
						&& messageName.tupleCodePointAt(position + 1) == '_')
					{
						sawIdentifierUnderscore = true;
						position += 2;
					}
					else
					{
						break;
					}
				}
				if (sawIdentifierUnderscore)
				{
					final StringBuilder builder = new StringBuilder();
					for (int i = start, limit = position - 1; i <= limit; i++)
					{
						final int cp = messageName.tupleCodePointAt(i);
						if (cp != '`')
						{
							builder.appendCodePoint(cp);
						}
					}
					messagePartsList.add(stringFrom(builder.toString()));
				}
				else
				{
					messagePartsList.add(
						messageName.copyStringFromToCanDestroy(
							start, position - 1, false));
				}
			}
		}
	}

	/**
	 * Check if there are more parts and the next part is an occurrence of the
	 * given {@link Metacharacter}.  If so, increment the {@link
	 * #messagePartPosition} and answer {code true}, otherwise answer {@code
	 * false}.
	 *
	 * @param metacharacter
	 *        The {@link Metacharacter} to look for.
	 * @return Whether the given metacharacter was found and consumed.
	 */
	private boolean peekFor (
		final Metacharacter metacharacter)
	{
		final @Nullable A_String token = currentMessagePartOrNull();
		if (token != null && token.equals(metacharacter.string))
		{
			messagePartPosition++;
			return true;
		}
		return false;
	}

	/**
	 * Check if the next part, if any, is the indicated {@link Metacharacter}.
	 * Do not advance the {@link #messagePartPosition} in either case.
	 *
	 * @param metacharacter
	 *        The {@link Metacharacter} to look ahead for.
	 * @return Whether the given metacharacter is the next token.
	 */
	private boolean peekAheadFor (
		final Metacharacter metacharacter)
	{
		final @Nullable A_String token = currentMessagePartOrNull();
		return token != null && token.equals(metacharacter.string);
	}

	/**
	 * Check for an occurrence of a circled number, signifying an explicit
	 * renumbering of arguments within a {@link Sequence}.  Answer the contained
	 * number, or -1 if not present.  If it was present, also advance past it.
	 *
	 * @return The value of the parsed explicit ordinal, or -1 if there was
	 *         none.
	 */
	private int peekForExplicitOrdinal ()
	{
		if (!atEnd())
		{
			final int codePoint = currentMessagePart().tupleCodePointAt(1);
			if (circledNumbersMap.containsKey(codePoint))
			{
				// In theory we could allow messages to go past ㊿ by
				// allowing a sequence of circled single digits (⓪-⑨)
				// that doesn't start with ⓪.  DEFINITELY not worth the
				// bother for now (2014.12.24).
				messagePartPosition++;
				return circledNumbersMap.get(codePoint);
			}
		}
		return -1;
	}

	/**
	 * Check if the next token is the given metacharacter.  If it is not, return
	 * {@code false}.  If it is, consume it and check the failureCondition.  If
	 * it's true, throw a {@link MalformedMessageException} with the given
	 * errorCode and errorString.  Otherwise return {@code true}.
	 *
	 * @param metacharacter
	 *        The {@link Metacharacter} to look for.
	 * @param failureCondition
	 *        The {@code boolean} to test if the metacharacter was consumed.
	 * @param errorCode
	 *        The errorCode to use in the {@link MalformedMessageException} if
	 *        the metacharacter is found and the condition is true.
	 * @param errorString
	 *        The errorString to use in the {@link MalformedMessageException} if
	 *        the metacharacter is found and the condition is true.
	 * @return Whether the metacharacter was found and consumed.
	 * @throws MalformedMessageException
	 *         If the {@link Metacharacter} was found and the failureCondition
	 *         was true.
	 */
	private boolean peekFor (
		final Metacharacter metacharacter,
		final boolean failureCondition,
		final AvailErrorCode errorCode,
		final String errorString)
	throws MalformedMessageException
	{
		final @Nullable A_String token = currentMessagePartOrNull();
		if (token == null || !token.equals(metacharacter.string))
		{
			return false;
		}
		throwMalformedIf(failureCondition, errorCode, errorString);
		messagePartPosition++;
		return true;
	}

	/**
	 * If the condition is true, throw a {@link MalformedMessageException} with
	 * the given errorCode and errorString.  Otherwise do nothing.
	 *
	 * @param condition
	 *        The {@code boolean} to test.
	 * @param errorCode
	 *        The errorCode to use in the {@link MalformedMessageException} if
	 *        the condition was true.
	 * @param errorString
	 *        The errorString to use in the {@link MalformedMessageException} if
	 *        the condition was true.
	 * @throws MalformedMessageException
	 *         If the condition was true.
	 */
	private static void throwMalformedIf (
		final boolean condition,
		final AvailErrorCode errorCode,
		final String errorString)
	throws MalformedMessageException
	{
		if (condition)
		{
			throwMalformedMessageException(errorCode, errorString);
		}
	}

	/**
	 * Create a {@link Sequence} from the tokenized message name.  Stop parsing
	 * the sequence when we reach the end of the tokens, a close guillemet (»),
	 * or a double-dagger (‡).</p>
	 *
	 * @return A {@link Sequence} expression parsed from the {@link
	 *         #messagePartsList}.
	 * @throws MalformedMessageException If the method name is malformed.
	 */
	private Sequence parseSequence ()
	throws MalformedMessageException
	{
		final Sequence sequence = new Sequence(messagePartPosition);
		@Nullable Expression expression = parseElementOrAlternation();
		while (expression != null)
		{
			sequence.addExpression(expression);
			expression = parseElementOrAlternation();
		}
		sequence.checkForConsistentOrdinals();
		return sequence;
	}

	/**
	 * Parse an element of a {@link Sequence}.  The element may be an {@link
	 * Alternation}.
	 *
	 * @return The parsed {@link Expression}, or {@code null} if no expression
	 *         can be found
	 * @throws MalformedMessageException
	 *         If the start of an {@link Expression} is found, but it's
	 *         malformed.
	 */
	private @Nullable Expression parseElementOrAlternation ()
	throws MalformedMessageException
	{
		final Expression firstExpression = parseElement();
		if (firstExpression == null)
		{
			return null;
		}
		if (!peekAheadFor(VERTICAL_BAR))
		{
			// Not an alternation.
			return firstExpression;
		}
		// It must be an alternation.
		checkAlternative(firstExpression);
		final List<Expression> alternatives = new ArrayList<>();
		alternatives.add(firstExpression);
		while (peekFor(VERTICAL_BAR))
		{
			final @Nullable Expression nextExpression = parseElement();
			if (nextExpression == null)
			{
				throwMalformedMessageException(
					E_VERTICAL_BAR_MUST_SEPARATE_TOKENS_OR_SIMPLE_GROUPS,
					"Expecting another token or simple group after the "
						+ "vertical bar (|)");
			}
			checkAlternative(nextExpression);
			alternatives.add(nextExpression);
		}
		return new Alternation(firstExpression.positionInName, alternatives);
	}

	/**
	 * Check that an {@link Expression} to be used as an alternative in an
	 * {@link Alternation} is well-formed.  Note that alternations may not
	 * contain arguments.
	 *
	 * @param expression
	 *        The alternative to check.
	 * @throws MalformedMessageException
	 *         If the alternative contains an argument.
	 */
	private static void checkAlternative (final Expression expression)
	throws MalformedMessageException
	{
		throwMalformedIf(
			expression.isArgumentOrGroup()
				|| expression.underscoreCount() > 0,
			E_ALTERNATIVE_MUST_NOT_CONTAIN_ARGUMENTS,
			"Alternatives must not contain arguments");
	}

	/**
	 * Parse a single element or return {@code null}.
	 *
	 * @return A parsed {@link Expression}, which is suitable for use within an
	 *         {@link Alternation} or {@link Sequence}, or {@code null} if none
	 *         is available at this position.
	 * @throws MalformedMessageException
	 *         If there appeared to be an element {@link Expression} at this
	 *         position, but it was malformed.
	 */
	private @Nullable Expression parseElement ()
	throws MalformedMessageException
	{
		// Try to parse the kinds of things that deal with their own suffixes.
		if (atEnd()
			|| peekAheadFor(DOUBLE_DAGGER)
			|| peekAheadFor(CLOSE_GUILLEMET))
		{
			return null;
		}
		if (peekFor(UNDERSCORE))
		{
			return parseOptionalExplicitOrdinal(parseUnderscoreElement());
		}
		if (peekFor(ELLIPSIS))
		{
			return parseOptionalExplicitOrdinal(parseEllipsisElement());
		}
		if (peekFor(OPEN_GUILLEMET))
		{
			return parseOptionalExplicitOrdinal(parseGuillemetElement());
		}
		if (peekFor(SECTION_SIGN))
		{
			return new SectionCheckpoint(
				positionInName(), ++numberOfSectionCheckpoints);
		}
		return parseSimple();
	}

	/**
	 * Parse a {@link Simple} at the current position.  Don't look for any
	 * suffixes.
	 *
	 * @return The {@link Simple}.
	 * @throws MalformedMessageException
	 *         If the {@link Simple} expression is malformed.
	 */
	private Expression parseSimple ()
	throws MalformedMessageException
	{
		// First, parse the next token, then apply a double-question-mark,
		// tilde, and/or circled number.
		if (peekFor(
			BACK_QUOTE,
			atEnd(),
			E_EXPECTED_OPERATOR_AFTER_BACKQUOTE,
			"Backquote (`) must be followed by a special metacharacter, "
				+ "space, or circled number"))
		{
			final A_String token = currentMessagePart();
			// Expects metacharacter or space or circled number after backquote.
			throwMalformedIf(
				token.tupleSize() != 1
					|| !canBeBackQuoted(token.tupleCodePointAt(1)),
				E_EXPECTED_OPERATOR_AFTER_BACKQUOTE,
				"Backquote (`) must be followed by a special metacharacter, "
					+ "space, or circled number, not ("
					+ token
					+ ")");
		}
		else
		{
			// Parse a regular keyword or operator.
			checkSuffixCharactersNotInSuffix();
		}
		Expression expression = new Simple(
			currentMessagePart(),
			messagePartPosition,
			messagePartPositions.get(messagePartPosition - 1));
		messagePartPosition++;

		if (peekFor(
			TILDE,
			!expression.isLowerCase(),
			E_CASE_INSENSITIVE_EXPRESSION_CANONIZATION,
			"Tilde (~) may only occur after a lowercase "
				+ "token or a group of lowercase tokens"))
		{
			expression = expression.applyCaseInsensitive();
		}

		if (peekFor(QUESTION_MARK))
		{
			final Sequence sequence = new Sequence(expression.positionInName);
			sequence.addExpression(expression);
			expression = new Optional(
				expression.positionInName,
				sequence);
		}
		else if (peekFor(DOUBLE_QUESTION_MARK))
		{
			final Sequence sequence = new Sequence(expression.positionInName);
			sequence.addExpression(expression);
			expression = new CompletelyOptional(
				expression.positionInName, sequence);
		}

		return expression;
	}

	/**
	 * The provided sequence element or {@link Alternation} has just been
	 * parsed.  Look for a suffixed ordinal indicator, marking the {@link
	 * Expression} with the explicit ordinal if indicated.
	 *
	 * @param expression
	 *        The {@link Expression} that was just parsed.
	 * @return A replacement {@link Expression} with its explicit ordinal set
	 *         if necessary.  This may be the original expression after
	 *         mutation.
	 */
	private Expression parseOptionalExplicitOrdinal (
		final Expression expression)
	{
		final int ordinal = peekForExplicitOrdinal();
		if (ordinal != -1)
		{
			expression.explicitOrdinal(ordinal);
		}
		return expression;
	}

	/**
	 * Check that the given token is not a special character that should only
	 * occur after an element or subgroup.  If it is such a special character,
	 * throw a {@link MalformedMessageException} with a suitable error code and
	 * message.
	 *
	 * @throws MalformedMessageException
	 *         If the token is special.
	 */
	private void checkSuffixCharactersNotInSuffix ()
	throws MalformedMessageException
	{
		final @Nullable A_String token = currentMessagePartOrNull();
		if (token == null)
		{
			return;
		}
		if (circledNumbersMap.containsKey(token.tupleCodePointAt(1)))
		{
			throwMalformedMessageException(
				E_INCONSISTENT_ARGUMENT_REORDERING,
				"Unquoted circled numbers (⓪-㊿) may only follow an "
					+ "argument, an ellipsis, or an argument group");
		}
		peekFor(
			OCTOTHORP,
			true,
			E_OCTOTHORP_MUST_FOLLOW_A_SIMPLE_GROUP_OR_ELLIPSIS,
			"An octothorp (#) may only follow a simple group («») "
				+ "or an ellipsis (…)");
		peekFor(
			DOLLAR_SIGN,
			true,
			E_DOLLAR_SIGN_MUST_FOLLOW_AN_ELLIPSIS,
			"A dollar sign ($) may only follow an ellipsis(…)");
		peekFor(
			QUESTION_MARK,
			true,
			E_QUESTION_MARK_MUST_FOLLOW_A_SIMPLE_GROUP,
			"A question mark (?) may only follow a simple group "
				+ "(optional) or a group with no double-dagger (‡)");
		peekFor(
			TILDE,
			true,
			E_CASE_INSENSITIVE_EXPRESSION_CANONIZATION,
			"Tilde (~) may only occur after a lowercase "
				+ "token or a group of lowercase tokens");
		peekFor(
			VERTICAL_BAR,
			true,
			E_VERTICAL_BAR_MUST_SEPARATE_TOKENS_OR_SIMPLE_GROUPS,
			"A vertical bar (|) may only separate tokens or simple "
				+ "groups");
		peekFor(
			EXCLAMATION_MARK,
			true,
			E_EXCLAMATION_MARK_MUST_FOLLOW_AN_ALTERNATION_GROUP,
			"An exclamation mark (!) may only follow an alternation "
				+ "group (or follow an underscore for macros)");
		peekFor(
			UP_ARROW,
			true,
			E_UP_ARROW_MUST_FOLLOW_ARGUMENT,
			"An up-arrow (↑) may only follow an argument");
	}

	/**
	 * An open guillemet («) was detected in the input stream.  Parse a suitable
	 * group or alternation or any other kind of {@link Expression} that starts
	 * with an open guillemet.  Do not parse any trailing circled numbers, which
	 * are used to permute argument expressions within a group or the top level.
	 *
	 * @return The {@link Expression} that started at the {@link
	 *         Metacharacter#OPEN_GUILLEMET}.
	 * @throws MalformedMessageException
	 *         If the subgroup is malformed.
	 */
	private Expression parseGuillemetElement ()
	throws MalformedMessageException
	{
		// We just parsed an open guillemet.  Parse a subgroup, eat the
		// mandatory close guillemet, and apply any modifiers to the group.
		final int startOfGroup = positionInName();
		final Sequence beforeDagger = parseSequence();
		final Group group;
		if (peekFor(DOUBLE_DAGGER))
		{
			final Sequence afterDagger = parseSequence();
			// Check for a second double-dagger.
			peekFor(
				DOUBLE_DAGGER,
				true,
				E_INCORRECT_USE_OF_DOUBLE_DAGGER,
				"A group must have at most one double-dagger (‡)");
			group = new Group(startOfGroup, beforeDagger, afterDagger);
		}
		else
		{
			group = new Group(startOfGroup, beforeDagger);
		}

		if (!peekFor(CLOSE_GUILLEMET))
		{
			// Expected matching close guillemet.
			throwMalformedMessageException(
				E_UNBALANCED_GUILLEMETS,
				"Expected close guillemet (») to end group");
		}
		Expression subexpression = group;

		// Look for a case-sensitive, then look for a counter, optional, or
		// completely-optional.
		if (peekFor(
			TILDE,
			!subexpression.isLowerCase(),
			E_CASE_INSENSITIVE_EXPRESSION_CANONIZATION,
			"Tilde (~) may only occur after a lowercase "
				+ "token or a group of lowercase tokens"))
		{
			subexpression = new CaseInsensitive(startOfGroup, subexpression);
		}

		if (peekFor(
			OCTOTHORP,
			group.underscoreCount() > 0,
			E_OCTOTHORP_MUST_FOLLOW_A_SIMPLE_GROUP_OR_ELLIPSIS,
			"An octothorp (#) may only follow a non-yielding "
			+ "group or an ellipsis (…)"))
		{
			subexpression = new Counter(startOfGroup, group);
		}
		else if (peekFor(
			QUESTION_MARK,
			group.hasDagger,
			E_QUESTION_MARK_MUST_FOLLOW_A_SIMPLE_GROUP,
			"A question mark (?) may only follow a simple "
			+ "group (optional) or a group with arguments "
			+ "(0 or 1 occurrences), but not one with a "
			+ "double-dagger (‡), since that implies "
			+ "multiple occurrences to be separated"))
		{
			if (group.underscoreCount() > 0)
			{
				// A complex group just gets bounded to [0..1] occurrences.
				group.beOptional();
			}
			else
			{
				// A simple group turns into an Optional, which produces a
				// literal boolean indicating the presence of such a
				// subexpression.
				subexpression = new Optional(startOfGroup, group.beforeDagger);
			}
		}
		else if (peekFor(
			DOUBLE_QUESTION_MARK,
			group.underscoreCount() > 0 || group.hasDagger,
			E_DOUBLE_QUESTION_MARK_MUST_FOLLOW_A_TOKEN_OR_SIMPLE_GROUP,
			"A double question mark (⁇) may only follow "
			+ "a token or simple group, not one with a "
			+ "double-dagger (‡) or arguments"))
		{
			subexpression = new CompletelyOptional(
				startOfGroup, group.beforeDagger);
		}
		else if (peekFor(
			EXCLAMATION_MARK,
			group.underscoreCount() > 0
				|| group.hasDagger
				|| (group.beforeDagger.expressions.size() != 1)
				|| !(group.beforeDagger.expressions.get(0)
					instanceof Alternation),
			E_EXCLAMATION_MARK_MUST_FOLLOW_AN_ALTERNATION_GROUP,
			"An exclamation mark (!) may only follow an "
			+ "alternation group or (for macros) an "
			+ "underscore"))
		{
			// The guillemet group should have had a single element, an
			// alternation.
			final Alternation alternation =
				cast(group.beforeDagger.expressions.get(0));
			subexpression = new NumberedChoice(alternation);
		}
		return subexpression;
	}

	/**
	 * Parse an ellipsis element, applying suffixed modifiers if present.  The
	 * ellipsis has already been consumed.
	 *
	 * @return A newly parsed {@link RawTokenArgument} or subclass.
	 */
	private Expression parseEllipsisElement ()
	{
		final int tokenStart =
			messagePartPositions.get(messagePartPosition - 2);
		incrementLeafArgumentCount();
		final int absoluteUnderscoreIndex = numberOfLeafArguments();
		if (peekFor(EXCLAMATION_MARK))
		{
			return new RawTokenArgument(
				tokenStart, absoluteUnderscoreIndex);
		}
		if (peekFor(OCTOTHORP))
		{
			return new RawWholeNumberLiteralTokenArgument(
				tokenStart, absoluteUnderscoreIndex);
		}
		if (peekFor(DOLLAR_SIGN))
		{
			return new RawStringLiteralTokenArgument(
				tokenStart, absoluteUnderscoreIndex);
		}
		return new RawKeywordTokenArgument(tokenStart, absoluteUnderscoreIndex);
	}

	/**
	 * Parse an underscore element, applying suffixed modifiers if present.  The
	 * underscore has already been consumed.
	 *
	 * @return A newly parsed {@link Argument} or subclass.
	 */
	private Expression parseUnderscoreElement ()
	{
		// Capture the one-based index.
		final int positionInName =
			messagePartPositions.get(messagePartPosition - 2);
		incrementLeafArgumentCount();
		final int absoluteUnderscoreIndex = numberOfLeafArguments();
		if (peekFor(SINGLE_DAGGER))
		{
			return new ArgumentInModuleScope(
				positionInName, absoluteUnderscoreIndex);
		}
		else if (peekFor(UP_ARROW))
		{
			return new VariableQuote(positionInName, absoluteUnderscoreIndex);
		}
		else if (peekFor(EXCLAMATION_MARK))
		{
			return new ArgumentForMacroOnly(
				positionInName, absoluteUnderscoreIndex);
		}
		else
		{
			return new Argument(positionInName, absoluteUnderscoreIndex);
		}
	}

	/**
	 * Increment the number of leaf arguments, which agrees with the number of
	 * non-backquoted underscores and ellipses.
	 */
	void incrementLeafArgumentCount ()
	{
		leafArgumentCount++;
	}

	/**
	 * Return the number of arguments a {@linkplain
	 * MethodDefinitionDescriptor method} implementing this name would
	 * accept.  Note that this is not necessarily the number of underscores and
	 * ellipses, as a guillemet group may contain zero or more
	 * underscores/ellipses (and other guillemet groups) but count as one
	 * top-level argument.
	 *
	 * @return The number of arguments this message takes.
	 */
	public int numberOfArguments ()
	{
		return rootSequence.arguments.size();
	}

	/**
	 * Return the number of underscores/ellipses present in the method name.
	 * This is not the same as the number of arguments that a method
	 * implementing this name would accept, as a top-level guillemet group with
	 * N recursively embedded underscores/ellipses is counted as N, not one.
	 *
	 * <p>
	 * This count of underscores/ellipses is essential for expressing negative
	 * precedence rules in the presence of repeated arguments.  Also note that
	 * backquoted underscores are not counted, since they don't represent a
	 * position at which a subexpression must occur.  Similarly, backquoted
	 * ellipses are not a place where an arbitrary input token can go.
	 * </p>
	 *
	 * @return The number of non-backquoted underscores/ellipses within this
	 *         method name.
	 */
	public int numberOfLeafArguments ()
	{
		return leafArgumentCount;
	}

	/**
	 * Check that an {@linkplain DefinitionDescriptor implementation} with
	 * the given {@linkplain FunctionTypeDescriptor signature} is appropriate
	 * for a message like this.
	 *
	 * @param functionType
	 *        A function type.
	 * @param sectionNumber
	 *        The {@link SectionCheckpoint}'s subscript if this is a check of a
	 *        {@linkplain MacroDefinitionDescriptor macro}'s, {@linkplain
	 *        A_Definition#prefixFunctions() prefix function}, otherwise any
	 *        value past the total {@link #numberOfSectionCheckpoints} for a
	 *        method or macro body.
	 * @throws SignatureException
	 *         If the function type is inappropriate for the method name.
	 */
	private void checkImplementationSignature (
		final A_Type functionType,
		final int sectionNumber)
	throws SignatureException
	{
		final A_Type argsTupleType = functionType.argsTupleType();
		final A_Type sizes = argsTupleType.sizeRange();
		final A_Number lowerBound = sizes.lowerBound();
		final A_Number upperBound = sizes.upperBound();
		if (!lowerBound.equals(upperBound) || !lowerBound.isInt())
		{
			// Method definitions (and other definitions) should take a
			// definite number of arguments.
			throwSignatureException(E_INCORRECT_NUMBER_OF_ARGUMENTS);
		}
		final int lowerBoundInt = lowerBound.extractInt();
		if (lowerBoundInt != numberOfArguments())
		{
			throwSignatureException(E_INCORRECT_NUMBER_OF_ARGUMENTS);
		}
		rootSequence.checkRootType(functionType.argsTupleType(), sectionNumber);
	}

	/**
	 * Check that an {@linkplain DefinitionDescriptor implementation} with
	 * the given {@linkplain FunctionTypeDescriptor signature} is appropriate
	 * for a message like this.
	 *
	 * @param functionType
	 *            A function type.
	 * @throws SignatureException
	 *            If the function type is inappropriate for the method name.
	 */
	public void checkImplementationSignature (
		final A_Type functionType)
	throws SignatureException
	{
		checkImplementationSignature(functionType, Integer.MAX_VALUE);
	}

	/**
	 * Does the message contain any groups?
	 *
	 * @return {@code true} if the message contains any groups, {@code false}
	 *         otherwise.
	 */
	public boolean containsGroups ()
	{
		for (final Expression expression : rootSequence.expressions)
		{
			if (expression.isGroup())
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * Throw a {@link SignatureException} with the given error code.
	 *
	 * @param errorCode The {@link AvailErrorCode} that indicates the problem.
	 * @throws SignatureException Always, with the given error code.
	 */
	static void throwSignatureException (
			final AvailErrorCode errorCode)
		throws SignatureException
	{
		throw new SignatureException(errorCode);
	}

	/**
	 * Throw a {@link MalformedMessageException} with the given error code.
	 *
	 * @param errorCode
	 *        The {@link AvailErrorCode} that indicates the problem.
	 * @param errorMessage
	 *        A description of the problem.
	 * @throws MalformedMessageException
	 *         Always, with the given error code and diagnostic message.
	 * @return Nothing, but pretend it's the {@link MalformedMessageException}
	 *         that was thrown so that the caller can pretend to throw it
	 *         again to indicate to Java that this call terminates the control
	 *         flow unconditionally.
	 */
	static MalformedMessageException throwMalformedMessageException (
		final AvailErrorCode errorCode,
		final String errorMessage)
	throws MalformedMessageException
	{
		throw new MalformedMessageException(errorCode, () -> errorMessage);
	}

	/**
	 * Answer whether the specified character is an operator character, space,
	 * underscore, or ellipsis.
	 *
	 * @param cp A Unicode codepoint (an {@code int}).
	 * @return {@code true} if the specified character is an operator character,
	 *          space, underscore, or ellipsis; or {@code false} otherwise.
	 */
	private static boolean isUnderscoreOrSpaceOrOperator (
		final int cp)
	{
		return cp == '_'
			|| cp == '…'
			|| cp == ' '
			|| cp == '/'
			|| cp == '$'
			|| isOperator(cp);
	}

	/**
	 * Answer whether the given Unicode codePoint is an acceptable operator.
	 *
	 * @param cp The codePoint to check.
	 * @return Whether the codePoint can be used as an operator character in a
	 *         method name.
	 */
	public static boolean isOperator (final int cp)
	{
		return
			!(Character.isDigit(cp)
				|| Character.isUnicodeIdentifierStart(cp)
				|| Character.isSpaceChar(cp)
				|| Character.isWhitespace(cp)
				|| cp < 32
				|| !(cp >= 160 || cp <= 126)
				|| !Character.isDefined(cp)
				|| cp == '_'
				|| cp == '"'
				|| cp == '\uFEFF');
	}

	@Override
	public String toString ()
	{
		final StringBuilder builder = new StringBuilder();
		dumpForDebug(builder);
		return builder.toString();
	}
}
