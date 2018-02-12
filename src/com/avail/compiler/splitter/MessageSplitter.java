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

import com.avail.annotations.InnerAccess;
import com.avail.compiler.ParsingOperation;
import com.avail.compiler.problems.CompilerDiagnostics;
import com.avail.descriptor.*;
import com.avail.exceptions.AvailErrorCode;
import com.avail.exceptions.MalformedMessageException;
import com.avail.exceptions.SignatureException;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static com.avail.compiler.splitter.MessageSplitter.Metacharacter.*;
import static com.avail.descriptor.AtomDescriptor.falseObject;
import static com.avail.descriptor.AtomDescriptor.trueObject;
import static com.avail.descriptor.SetDescriptor.set;
import static com.avail.descriptor.StringDescriptor.stringFrom;
import static com.avail.descriptor.TupleDescriptor.emptyTuple;
import static com.avail.descriptor.TupleDescriptor.tupleFromList;
import static com.avail.exceptions.AvailErrorCode.*;

/**
 * {@code MessageSplitter} is used to split Avail message names into a sequence
 * of {@linkplain ParsingOperation instructions} that can be used directly for
 * parsing.
 *
 * <p>Message splitting occurs in two phases.  In the first setPhase, the
 * message is tokenized and parsed into an abstract {@link Expression} tree.
 * In the second setPhase, a {@linkplain TupleTypeDescriptor tuple type} of {@link
 * ParseNodeTypeDescriptor phrase types} is supplied, and produces a tuple of
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
		BACK_QUOTE("`"),
		CLOSE_GUILLEMET("»"),
		DOLLAR_SIGN("$"),
		DOUBLE_DAGGER("‡"),
		DOUBLE_QUESTION_MARK("⁇"),
		ELLIPSIS("…"),
		EXCLAMATION_MARK("!"),
		OCTOTHORP("#"),
		OPEN_GUILLEMET("«"),
		SECTION_SIGN("§"),
		SINGLE_DAGGER("†"),
		TILDE("~"),
		QUESTION_MARK("?"),
		UNDERSCORE("_"),
		UP_ARROW("↑"),
		VERTICAL_BAR("|");

		public final A_String string;

		Metacharacter (final String javaString)
		{
			string = stringFrom(javaString).makeShared();
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
		E_TILDE_MUST_NOT_FOLLOW_ARGUMENT,
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
	private static final Map<Integer, Integer> circledNumbersMap =
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
	@InnerAccess final A_String messageName;

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
	 * be evaluated in the static scope during compilation.  This is applicable
	 * to both methods and macros.  The expression must yield a type.</li>
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
	@InnerAccess final List<Integer> messagePartPositions = new ArrayList<>(10);

	/** The current one-based parsing position in the list of tokens. */
	@InnerAccess int messagePartPosition;

	/**
	 * A count of the number of unbackquoted underscores or ellipses in the
	 * message name.
	 */
	@InnerAccess int leafArgumentCount = 0;

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
	private static final Map<AvailObject, Integer> constantsMap = new HashMap<>(100);

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
	@InnerAccess
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
	@InnerAccess static int indexForConstant (final A_BasicObject constant)
	{
		final AvailObject strongConstant = constant.makeShared();
		constantsLock.readLock().lock();
		try
		{
			final Integer index = constantsMap.get(strongConstant);
			if (index != null)
			{
				return index;
			}
		}
		finally
		{
			constantsLock.readLock().unlock();
		}

		constantsLock.writeLock().lock();
		try
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
		finally
		{
			constantsLock.writeLock().unlock();
		}
	}

	/** The position at which true is stored in the {@link #constantsList}. */
	private static final int indexForTrue =
		indexForConstant(trueObject());

	/** The position at which true is stored in the {@link #constantsList}. */
	static int indexForTrue ()
	{
		return indexForTrue;
	}

	/** The position at which false is stored in the {@link #constantsList}. */
	private static final int indexForFalse =
		indexForConstant(falseObject());

	/** The position at which false is stored in the {@link #constantsList}. */
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
		constantsLock.readLock().lock();
		try
		{
			return constantsList.get(index - 1);
		}
		finally
		{
			constantsLock.readLock().unlock();
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
		this.messageName = messageName;
		messageName.makeImmutable();
		splitMessage();
		messagePartPosition = 1;
		rootSequence = parseSequence();
		if (!atEnd())
		{
			final A_String part = currentMessagePart();
			final String encountered;
			if (part.equals(CLOSE_GUILLEMET.string))
			{
				encountered =
					"close guillemet (») with no corresponding open guillemet";
			}
			else if (part.equals(DOUBLE_DAGGER.string))
			{
				encountered = "double-dagger (‡) outside of a group";
			}
			else
			{
				encountered = "unexpected token " + part;
			}
			throwMalformedMessageException(
				E_UNBALANCED_GUILLEMETS,
				"Encountered " + encountered);
		}
		messagePartsTuple = tupleFromList(messagePartsList).makeShared();
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
			builder.append("\t");
			builder.append(part.asNativeString());
			builder.append("\n");
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
	 */
	private int positionInName()
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
		return atEnd() ? null : currentMessagePart();
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
	 * Pretty-print a send of this message with given argument nodes.
	 *
	 * @param sendNode
	 *        The {@linkplain SendNodeDescriptor send node} that is being
	 *        printed.
	 * @param builder
	 *        A {@link StringBuilder} on which to pretty-print the send of my
	 *        message with the given arguments.
	 * @param indent
	 *        The current indentation level.
	 */
	public void printSendNodeOnIndent (
		final A_Phrase sendNode,
		final StringBuilder builder,
		final int indent)
	{
		builder.append("«");
		rootSequence.printWithArguments(
			sendNode.argumentsListNode().expressionsTuple().iterator(),
			builder,
			indent);
		builder.append("»");
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
		@SuppressWarnings("StringConcatenationMissingWhitespace")
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
	private void splitMessage () throws MalformedMessageException
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
						(A_String)messageName.copyTupleFromToCanDestroy(
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
							(A_String)messageName.copyTupleFromToCanDestroy(
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
						(A_String)(messageName.copyTupleFromToCanDestroy(
							position, position, false)));
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
						messagePartsList.add(
							stringFrom(builder.toString()));
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
							messagePartsList.add((A_String)
								(messageName.copyTupleFromToCanDestroy(
									i, i, false)));
							messagePartPositions.add(i);
						}
					}
				}
			}
			else if (isUnderscoreOrSpaceOrOperator(ch))
			{
				messagePartsList.add(
					(A_String)(messageName.copyTupleFromToCanDestroy(
						position, position, false)));
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
						(A_String)messageName.copyTupleFromToCanDestroy(
							start, position - 1, false));
				}
			}
		}
	}

	/**
	 * Create a {@linkplain Group group} from the series of tokens describing
	 * it. This is also used to construct the outermost sequence of {@linkplain
	 * Expression expressions}.  Expect the {@linkplain #messagePartPosition} to
	 * point (via a one-based offset) to the first token of the sequence, or
	 * just past the end if the sequence is empty. Leave the {@code
	 * messagePartPosition} pointing just past the last token of the group.
	 *
	 * <p>Stop parsing the sequence when we reach the end of the tokens, a close
	 * guillemet (»), or a double-dagger (‡).</p>
	 *
	 * @return A {@link Sequence} expression parsed from the {@link
	 *         #messagePartsList}.
	 * @throws MalformedMessageException If the method name is malformed.
	 */
	private Sequence parseSequence ()
		throws MalformedMessageException
	{
		List<Expression> alternatives = new ArrayList<>();
		boolean justParsedVerticalBar = false;
		final Sequence sequence = new Sequence(positionInName(), this);
		while (true)
		{
			assert !justParsedVerticalBar || !alternatives.isEmpty();
			A_String token = atEnd() ? null : currentMessagePart();
			if (token == null)
			{
				if (justParsedVerticalBar)
				{
					throwMalformedMessageException(
						E_VERTICAL_BAR_MUST_SEPARATE_TOKENS_OR_SIMPLE_GROUPS,
						"Expecting another token or simple group after the "
							+ "vertical bar (|)");
				}
				sequence.checkForConsistentOrdinals();
				return sequence;
			}
			if (token.equals(CLOSE_GUILLEMET.string) || token.equals(
				DOUBLE_DAGGER.string))
			{
				if (justParsedVerticalBar)
				{
					final String problem = token.equals(CLOSE_GUILLEMET.string)
						? "close guillemet (»)"
						: "double-dagger (‡)";
					throwMalformedMessageException(
						E_VERTICAL_BAR_MUST_SEPARATE_TOKENS_OR_SIMPLE_GROUPS,
						"Expecting another token or simple group after the "
						+ "vertical bar (|), not "
						+ problem);
				}
				sequence.checkForConsistentOrdinals();
				return sequence;
			}
			if (token.equals(UNDERSCORE.string))
			{
				// Capture the one-based index.
				final int argStart = messagePartPosition;
				if (alternatives.size() > 0)
				{
					// Alternations may not contain arguments.
					throwMalformedMessageException(
						E_ALTERNATIVE_MUST_NOT_CONTAIN_ARGUMENTS,
						"Alternations must not contain arguments");
				}
				messagePartPosition++;
				@Nullable A_String nextToken = currentMessagePartOrNull();
				int ordinal = -1;
				if (nextToken != null)
				{
					// Just ate the underscore, so immediately after is where
					// we expect an optional circled number to indicate argument
					// reordering.
					final int codePoint = nextToken.tupleCodePointAt(1);
					if (circledNumbersMap.containsKey(codePoint))
					{
						// In theory we could allow messages to go past ㊿ by
						// allowing a sequence of circled single digits (⓪-⑨)
						// that doesn't start with ⓪.  DEFINITELY not worth the
						// bother for now (2014.12.24).
						ordinal = circledNumbersMap.get(codePoint);
						messagePartPosition++;
						nextToken = currentMessagePartOrNull();
					}
				}
				Expression argument = null;
				if (nextToken != null)
				{
					if (nextToken.equals(SINGLE_DAGGER.string))
					{
						messagePartPosition++;
						argument = new ArgumentInModuleScope(this, argStart);
					}
					else if (nextToken.equals(UP_ARROW.string))
					{
						messagePartPosition++;
						argument = new VariableQuote(this, argStart);
					}
					else if (nextToken.equals(EXCLAMATION_MARK.string))
					{
						messagePartPosition++;
						argument = new ArgumentForMacroOnly(this, argStart);
					}
				}
				// If the argument wasn't set already (because it wasn't
				// followed by a modifier), then set it here.
				if (argument == null)
				{
					argument = new Argument(this, argStart);
				}
				argument.explicitOrdinal(ordinal);
				sequence.addExpression(argument);
			}
			else if (token.equals(ELLIPSIS.string))
			{
				final int ellipsisStart = messagePartPosition;
				if (alternatives.size() > 0)
				{
					// Alternations may not contain arguments.
					throwMalformedMessageException(
						E_ALTERNATIVE_MUST_NOT_CONTAIN_ARGUMENTS,
						"Alternations must not contain arguments");
				}
				messagePartPosition++;
				final @Nullable A_String nextToken = currentMessagePartOrNull();
				if (nextToken != null
					&& nextToken.equals(EXCLAMATION_MARK.string))
				{
					sequence.addExpression(
						new RawTokenArgument(this, ellipsisStart));
					messagePartPosition++;
				}
				else if (nextToken != null
					&& nextToken.equals(OCTOTHORP.string))
				{
					sequence.addExpression(
						new RawWholeNumberLiteralTokenArgument(
							this, ellipsisStart));
					messagePartPosition++;
				}
				else if (nextToken != null
					&& nextToken.equals(DOLLAR_SIGN.string))
				{
					sequence.addExpression(
						new RawStringLiteralTokenArgument(this, ellipsisStart));
					messagePartPosition++;
				}
				else
				{
					sequence.addExpression(
						new RawKeywordTokenArgument(this, ellipsisStart));
				}
			}
			else if (token.equals(OCTOTHORP.string))
			{
				throwMalformedMessageException(
					E_OCTOTHORP_MUST_FOLLOW_A_SIMPLE_GROUP_OR_ELLIPSIS,
					"An octothorp (#) may only follow a simple group («») "
					+ "or an ellipsis (…)");
			}
			else if (token.equals(DOLLAR_SIGN.string))
			{
				throwMalformedMessageException(
					E_DOLLAR_SIGN_MUST_FOLLOW_AN_ELLIPSIS,
					"A dollar sign ($) may only follow an ellipsis(…)");
			}
			else if (token.equals(QUESTION_MARK.string))
			{
				throwMalformedMessageException(
					E_QUESTION_MARK_MUST_FOLLOW_A_SIMPLE_GROUP,
					"A question mark (?) may only follow a simple group "
					+ "(optional) or a group with no double-dagger (‡)");
			}
			else if (token.equals(TILDE.string))
			{
				throwMalformedMessageException(
					E_TILDE_MUST_NOT_FOLLOW_ARGUMENT,
					"A tilde (~) must not follow an argument");
			}
			else if (token.equals(VERTICAL_BAR.string))
			{
				throwMalformedMessageException(
					E_VERTICAL_BAR_MUST_SEPARATE_TOKENS_OR_SIMPLE_GROUPS,
					"A vertical bar (|) may only separate tokens or simple "
					+ "groups");
			}
			else if (token.equals(EXCLAMATION_MARK.string))
			{
				throwMalformedMessageException(
					E_EXCLAMATION_MARK_MUST_FOLLOW_AN_ALTERNATION_GROUP,
					"An exclamation mark (!) may only follow an alternation "
					+ "group (or follow an underscore for macros)");
			}
			else if (token.equals(UP_ARROW.string))
			{
				throwMalformedMessageException(
					E_UP_ARROW_MUST_FOLLOW_ARGUMENT,
					"An up-arrow (↑) may only follow an argument");
			}
			else if (circledNumbersMap.containsKey(
				token.tupleCodePointAt(1)))
			{
				throwMalformedMessageException(
					E_INCONSISTENT_ARGUMENT_REORDERING,
					"Unquoted circled numbers (⓪-㊿) may only follow an "
					+ "argument, an ellipsis, or an argument group");
			}
			else if (token.equals(OPEN_GUILLEMET.string))
			{
				// Eat the open guillemet, parse a subgroup, eat the (mandatory)
				// close guillemet, and add the group.
				final int startOfGroup = positionInName();
				messagePartPosition++;
				final Group subgroup = parseGroup(startOfGroup);
				//justParsedVerticalBar = false;
				if (!atEnd())
				{
					token = currentMessagePart();
				}
				// Otherwise token stays an open guillemet, hence not a close...
				if (!token.equals(CLOSE_GUILLEMET.string))
				{
					// Expected matching close guillemet.
					throwMalformedMessageException(
						E_UNBALANCED_GUILLEMETS,
						"Expected close guillemet (») to end group");
				}
				messagePartPosition++;
				// Just ate the close guillemet, so immediately after is where
				// we expect an optional circled number to indicate argument
				// reordering.
				if (!atEnd())
				{
					token = currentMessagePart();
					final int codePoint = token.tupleCodePointAt(1);
					if (circledNumbersMap.containsKey(codePoint))
					{
						// In theory we could allow messages to go past ㊿ by
						// allowing a sequence of circled single digits (⓪-⑨)
						// that doesn't start with ⓪.  DEFINITELY not worth the
						// bother for now (2014.12.24).
						subgroup.explicitOrdinal(
							circledNumbersMap.get(codePoint));
						messagePartPosition++;
					}
				}
				// Try to parse a counter, optional, and/or case-insensitive.
				Expression subexpression = subgroup;
				if (!atEnd())
				{
					token = currentMessagePart();
					if (token.equals(OCTOTHORP.string))
					{
						if (subgroup.underscoreCount() > 0)
						{
							// Counting group may not contain arguments.
							throwMalformedMessageException(
								E_OCTOTHORP_MUST_FOLLOW_A_SIMPLE_GROUP_OR_ELLIPSIS,
								"An octothorp (#) may only follow a simple "
								+ "group or an ellipsis (…)");
						}
						subexpression = new Counter(startOfGroup, subgroup);
						messagePartPosition++;
					}
					else if (token.equals(QUESTION_MARK.string))
					{
						if (subgroup.hasDagger)
						{
							// A question mark after a group with underscores
							// means zero or one occurrence, so a double-dagger
							// would be pointless.
							throwMalformedMessageException(
								E_QUESTION_MARK_MUST_FOLLOW_A_SIMPLE_GROUP,
								"A question mark (?) may only follow a simple "
								+ "group (optional) or a group with arguments "
								+ "(0 or 1 occurrences), but not one with a "
								+ "double-dagger (‡), since that implies "
								+ "multiple occurrences to be separated");
						}
						if (subgroup.underscoreCount() > 0)
						{
							subgroup.maximumCardinality(1);
							subexpression = subgroup;
						}
						else
						{
							subexpression = new Optional(
								startOfGroup, subgroup.beforeDagger);
						}
						messagePartPosition++;
					}
					else if (token.equals(DOUBLE_QUESTION_MARK.string))
					{
						if (subgroup.underscoreCount() > 0
							|| subgroup.hasDagger)
						{
							// Completely optional group may not contain
							// arguments or double daggers.
							throwMalformedMessageException(
								E_DOUBLE_QUESTION_MARK_MUST_FOLLOW_A_TOKEN_OR_SIMPLE_GROUP,
								"A double question mark (⁇) may only follow "
								+ "a token or simple group, not one with a "
								+ "double-dagger (‡) or arguments");
						}
						subexpression = new CompletelyOptional(
							startOfGroup, this, subgroup.beforeDagger);
						messagePartPosition++;
					}
					else if (token.equals(EXCLAMATION_MARK.string))
					{
						if (subgroup.underscoreCount() > 0
							|| subgroup.hasDagger
							|| (subgroup.beforeDagger.expressions.size() != 1)
							|| !(subgroup.beforeDagger.expressions.get(0)
								instanceof Alternation))
						{
							// Numbered choice group may not contain
							// underscores.  The group must also consist of an
							// alternation.
							throwMalformedMessageException(
								E_EXCLAMATION_MARK_MUST_FOLLOW_AN_ALTERNATION_GROUP,
								"An exclamation mark (!) may only follow an "
								+ "alternation group or (for macros) an "
								+ "underscore");
						}
						final Expression alternation =
							subgroup.beforeDagger.expressions.get(0);
						subexpression =
							new NumberedChoice((Alternation) alternation);
						messagePartPosition++;
					}
				}
				if (!atEnd())
				{
					token = currentMessagePart();
					// Try to parse a case-insensitive modifier.
					if (token.equals(TILDE.string))
					{
						if (!subexpression.isLowerCase())
						{
							throwMalformedMessageException(
								E_CASE_INSENSITIVE_EXPRESSION_CANONIZATION,
								"Tilde (~) may only occur after a lowercase "
								+ "token or a group of lowercase tokens");
						}
						subexpression = new CaseInsensitive(
							startOfGroup, subexpression);
						messagePartPosition++;
					}
				}
				// Parse a vertical bar. If no vertical bar occurs, then either
				// complete an alternation already in progress (including this
				// most recent expression) or add the subexpression directly to
				// the group.
				if (atEnd()
					|| !currentMessagePart().equals(VERTICAL_BAR.string))
				{
					if (alternatives.size() > 0)
					{
						alternatives.add(subexpression);
						subexpression = new Alternation(
							startOfGroup, alternatives);
						alternatives = new ArrayList<>();
					}
					sequence.addExpression(subexpression);
					justParsedVerticalBar = false;
				}
				else
				{
					if (subexpression.underscoreCount() > 0)
					{
						// Alternations may not contain arguments.
						throwMalformedMessageException(
							E_ALTERNATIVE_MUST_NOT_CONTAIN_ARGUMENTS,
							"Alternatives must not contain arguments");
					}
					alternatives.add(subexpression);
					messagePartPosition++;
					justParsedVerticalBar = true;
				}
			}
			else if (token.equals(SECTION_SIGN.string))
			{
				if (alternatives.size() > 0)
				{
					throwMalformedMessageException(
						E_ALTERNATIVE_MUST_NOT_CONTAIN_ARGUMENTS,
						"Alternative must not contain a section "
						+ "checkpoint (§)");
				}
				assert !justParsedVerticalBar;
				sequence.addExpression(
					new SectionCheckpoint(positionInName(), this));
				messagePartPosition++;
			}
			else
			{
				// Parse a backquote.
				if (token.equals(BACK_QUOTE.string))
				{
					// Eat the backquote.
					messagePartPosition++;
					if (atEnd())
					{
						// Expected operator character after backquote, not end.
						throwMalformedMessageException(
							E_EXPECTED_OPERATOR_AFTER_BACKQUOTE,
							"Backquote (`) must be followed by an operator "
							+ "character");
					}
					token = currentMessagePart();
					if (token.tupleSize() != 1
						|| !isUnderscoreOrSpaceOrOperator(
							token.tupleCodePointAt(1)))
					{
						// Expected operator character after backquote.
						throwMalformedMessageException(
							E_EXPECTED_OPERATOR_AFTER_BACKQUOTE,
							"Backquote (`) must be followed by an operator "
							+ "character");
					}
				}
				// Parse a regular keyword or operator.
				//justParsedVerticalBar = false;
				Expression subexpression =
					new Simple(this, messagePartPosition);
				messagePartPosition++;
				// Parse a completely optional.
				if (!atEnd())
				{
					token = currentMessagePart();
					if (token.equals(DOUBLE_QUESTION_MARK.string))
					{
						final Sequence singleSequence =
							new Sequence(subexpression.positionInName, this);
						singleSequence.addExpression(subexpression);
						subexpression = new CompletelyOptional(
							subexpression.positionInName, this, singleSequence);
						messagePartPosition++;
					}
				}
				// Parse a case insensitive.
				if (!atEnd())
				{
					token = currentMessagePart();
					if (token.equals(TILDE.string))
					{
						if (!subexpression.isLowerCase())
						{
							throwMalformedMessageException(
								E_CASE_INSENSITIVE_EXPRESSION_CANONIZATION,
								"Tilde (~) may only occur after a lowercase "
								+ "token or a group of lowercase tokens");
						}
						subexpression = new CaseInsensitive(
							subexpression.positionInName, subexpression);
						messagePartPosition++;
					}
				}
				// Parse a vertical bar. If no vertical bar occurs, then either
				// complete an alternation already in progress (including this
				// most recent expression) or add the subexpression directly to
				// the group.
				if (atEnd()
					|| !currentMessagePart().equals(VERTICAL_BAR.string))
				{
					if (alternatives.size() > 0)
					{
						alternatives.add(subexpression);
						subexpression = new Alternation(
							alternatives.get(0).positionInName, alternatives);
						alternatives = new ArrayList<>();
					}
					sequence.addExpression(subexpression);
					justParsedVerticalBar = false;
				}
				else
				{
					alternatives.add(subexpression);
					messagePartPosition++;
					justParsedVerticalBar = true;
				}
			}
		}
	}

	/**
	 * Create a {@linkplain Group group} from the series of tokens describing
	 * it. This is also used to construct the outermost sequence of {@linkplain
	 * Expression expressions}, with the restriction that an occurrence of a
	 * {@linkplain Metacharacter#DOUBLE_DAGGER double dagger} in the
	 * outermost pseudo-group is an error. Expect the {@linkplain
	 * #messagePartPosition} to point (via a one-based offset) to the first
	 * token of the group, or just past the end if the group is empty. Leave the
	 * {@code messagePartPosition} pointing just past the last token of the
	 * group.
	 *
	 * <p>The caller is responsible for identifying and skipping an open
	 * guillemet prior to this group, and for consuming the close guillemet
	 * after parsing the group. The outermost caller is also responsible for
	 * ensuring the entire input was exactly consumed.</p>
	 *
	 * @param positionInName
	 *        The position of the group in the message name.
	 * @return A {@link Group} expression parsed from the {@link
	 *         #messagePartsList}.
	 * @throws MalformedMessageException If the method name is malformed.
	 */
	private Group parseGroup (final int positionInName)
		throws MalformedMessageException
	{
		final Sequence beforeDagger = parseSequence();
		if (!atEnd() && currentMessagePart().equals(DOUBLE_DAGGER.string))
		{
			messagePartPosition++;
			final Sequence afterDagger = parseSequence();
			if (!atEnd() && currentMessagePart().equals(DOUBLE_DAGGER.string))
			{
				// Two daggers were encountered in a group.
				throwMalformedMessageException(
					E_INCORRECT_USE_OF_DOUBLE_DAGGER,
					"A group must have at most one double-dagger (‡)");
			}
			return new Group(positionInName, beforeDagger, afterDagger);
		}
		return new Group(positionInName, this, beforeDagger);
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
	 *            A function type.
	 * @param sectionNumber
	 *            The {@link SectionCheckpoint}'s subscript if this is a check
	 *            of a {@linkplain MacroDefinitionDescriptor macro}'s,
	 *            {@linkplain A_Definition#prefixFunctions() prefix function},
	 *            otherwise any value past the total {@link
	 *            #numberOfSectionCheckpoints} for a method or macro body.
	 * @throws SignatureException
	 *            If the function type is inappropriate for the method name.
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
		rootSequence.checkType(
			functionType.argsTupleType(),
			sectionNumber);
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
	 */
	void throwMalformedMessageException (
			final AvailErrorCode errorCode,
			final String errorMessage)
		throws MalformedMessageException
	{
		throw new MalformedMessageException(
			errorCode,
			() ->
			{
				final StringBuilder builder = new StringBuilder();
				builder.append(errorMessage);
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
					(A_String)messageName.copyTupleFromToCanDestroy(
						1, characterIndex - 1, false);
				final A_String after =
					(A_String)messageName.copyTupleFromToCanDestroy(
						characterIndex, messageName.tupleSize(), false);
				builder.append(before.asNativeString());
				builder.append(CompilerDiagnostics.errorIndicatorSymbol);
				builder.append(after.asNativeString());
				builder.append("\"");
				return builder.toString();
			});
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
