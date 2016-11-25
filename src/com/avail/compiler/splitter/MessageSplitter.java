/**
 * MessageSplitter.java
 * Copyright © 1993-2015, The Avail Foundation, LLC.
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

import static com.avail.descriptor.StringDescriptor.*;
import static com.avail.exceptions.AvailErrorCode.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import com.avail.annotations.InnerAccess;
import com.avail.compiler.AvailCompiler;
import com.avail.compiler.ParsingOperation;
import com.avail.compiler.scanning.AvailScanner;
import com.avail.descriptor.*;
import com.avail.exceptions.*;
import com.avail.utility.Generator;
import org.jetbrains.annotations.Nullable;

/**
 * {@code MessageSplitter} is used to split Avail message names into a sequence
 * of {@linkplain ParsingOperation instructions} that can be used directly for
 * parsing.
 *
 * <p>Message splitting occurs in two phases.  In the first phase, the
 message is tokenized and parsed into an abstract {@link Expression}
 * tree.  In the second phase, a {@linkplain TupleTypeDescriptor tuple type} of
 * {@link com.avail.descriptor.ParseNodeTypeDescriptor phrase types} is
 * supplied, and produces a tuple of integer-encoded {@link ParsingOperation}s.
 * </p>
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class MessageSplitter
{
	/**
	 * The {@linkplain A_Set set} of all {@linkplain AvailErrorCode errors} that
	 * can happen during {@linkplain MessageSplitter message splitting}.
	 */
	public static final A_Set possibleErrors = SetDescriptor.from(
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
	static final int [] circledNumberCodePoints = new int[circledNumbersCount];

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
		assert circledNumbersCount == 51;
		int pointer = 0;
		int arrayPointer = 0;
		while (pointer < circledNumbersString.length())
		{
			final int codePoint = circledNumbersString.codePointAt(pointer);
			circledNumbersMap.put(codePoint, pointer);
			circledNumberCodePoints[arrayPointer++] = codePoint;
			pointer += Character.charCount(codePoint);
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
	 * <p><ul>
	 * <li>Alphanumerics are in runs, separated from other
	 * alphanumerics by a single space.</li>
	 * <li>Operator characters are never beside spaces, and are always parsed as
	 * individual tokens.</li>
	 * <li>{@linkplain StringDescriptor#openGuillemet() Open guillemet} («),
	 * {@linkplain StringDescriptor#doubleDagger() double dagger} (‡), and
	 * {@linkplain StringDescriptor#closeGuillemet() close guillemet} (») are
	 * used to indicate repeated or optional substructures.</li>
	 * <li>The characters {@linkplain StringDescriptor#octothorp() octothorp}
	 * (#) and {@linkplain StringDescriptor#questionMark() question mark} (?)
	 * modify the output of repeated substructures to produce either a count
	 * of the repetitions or a boolean indicating whether an optional
	 * subexpression (expecting no arguments) was present.</li>
	 * <li>Placing a {@linkplain StringDescriptor#questionMark() question mark}
	 * (?) after a group containing arguments but no {@linkplain
	 * StringDescriptor#doubleDagger() double-dagger} (‡) will limit the
	 * repetitions of the group to at most one.  Although limiting the method
	 * definitions to only accept 0..1 occurrences would accomplish the same
	 * grammatical narrowing, the parser might still attempt to parse more than
	 * one occurrence, leading to unnecessarily confusing diagnostics.</li>
	 * <li>An {@linkplain StringDescriptor#exclamationMark() exclamation mark}
	 * (!) can follow a group of alternations to produce the 1-based index of
	 * the alternative that actually occurred.</li>
	 * <li>An {@linkplain StringDescriptor#underscore() underscore} (_)
	 * indicates where an argument occurs.</li>
	 * <li>A {@linkplain StringDescriptor#singleDagger() single dagger} (†) may
	 * occur immediately after an underscore to cause the argument expression to
	 * be evaluated in the static scope during compilation.  This is applicable
	 * to both methods and macros.  The expression must yield a type.</li>
	 * <li>An {@linkplain StringDescriptor#upArrow() up-arrow} (↑) after an
	 * underscore indicates an in-scope variable name is to be parsed.  The
	 * subexpression causes the variable itself to be provided, rather than its
	 * value.</li>
	 * <li>An {@linkplain StringDescriptor#exclamationMark() exclamation mark}
	 * (!) may occur after the underscore instead, to indicate the argument
	 * expression may be ⊤-valued or ⊥-valued.  Since a function (and therefore
	 * a method definition) cannot accept a ⊤-valued argument, this mechanism
	 * only makes sense for macros, since macros bodies are passed phrases,
	 * which may be typed as <em>yielding</em> a top-valued result.</li>
	 * <li>An {@linkplain StringDescriptor#ellipsis() ellipsis} (…) matches a
	 * single {@linkplain TokenDescriptor keyword token}.</li>
	 * <li>An {@linkplain StringDescriptor#exclamationMark() exclamation mark}
	 * (!) after an ellipsis indicates <em>any</em> token will be accepted at
	 * that position.</li>
	 * <li>An {@linkplain StringDescriptor#octothorp() octothorp} (#) after an
	 * ellipsis indicates only a <em>literal</em> token will be accepted.</li>
	 * <li>The N<sup>th</sup> {@linkplain StringDescriptor#sectionSign() section
	 * sign} (§) in a message name indicates where a macro's N<sup>th</sup>
	 * {@linkplain A_Definition#prefixFunctions() prefix function} should be
	 * invoked with the current parse stack up to that point.</li>
	 * <li>A {@linkplain StringDescriptor#backQuote() backquote} (`) can
	 * precede any operator character, such as guillemets or double dagger, to
	 * ensure it is not used in a special way. A backquote may also operate on
	 * another backquote (to indicate that an actual backquote token will appear
	 * in a call).</li>
	 * </ul></p>
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
	 * A record of where each "underscore" occurred in the list of {@link
	 * #messagePartsList}.
	 */
	@InnerAccess List<Integer> underscorePartNumbers = new ArrayList<>();

	/**
	 * The number of {@link SectionCheckpoint}s encountered so far.
	 */
	@InnerAccess int numberOfSectionCheckpoints;

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
	public static AtomicReference<A_Tuple> permutations =
		new AtomicReference<A_Tuple>(TupleDescriptor.empty());

	/**
	 * A statically-scoped {@link List} of unique constants needed as operands
	 * of some {@link ParsingOperation}s.  The inverse {@link Map} (but
	 * containing one-based indices) is kept in #constantsMap}.
	 */
	private static List<AvailObject> constantsList = new ArrayList<>(100);

	/**
	 * A statically-scoped map from Avail object to one-based index (into
	 * {@link #constantsList}, after adjusting to a zero-based {@link List}),
	 * for which some {@link ParsingOperation} needed to hold that constant as
	 * an operand.
	 */
	private static Map<AvailObject, Integer> constantsMap = new HashMap<>(100);

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
		final AvailObject strongConstant = (AvailObject) constant.makeShared();
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
	private final static int indexForTrue =
		indexForConstant(AtomDescriptor.trueObject());

	/** The position at which true is stored in the {@link #constantsList}. */
	static int indexForTrue ()
	{
		return indexForTrue;
	}

	/** The position at which false is stored in the {@link #constantsList}. */
	private final static int indexForFalse =
		indexForConstant(AtomDescriptor.falseObject());

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
	 * Construct a new {@link MessageSplitter}, parsing the provided message
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
			String encountered;
			if (part.equals(closeGuillemet()))
			{
				encountered =
					"close guillemet (») with no corresponding open guillemet";
			}
			else if (part.equals(doubleDagger()))
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
		messagePartsTuple =
			TupleDescriptor.fromList(messagePartsList).makeShared();
	}

	/**
	* Dump debugging information about this {@linkplain MessageSplitter} to
	* the specified {@linkplain StringBuilder builder}.
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
	 * Answer the list of one-based positions in the original string
	 * corresponding to the {@link #messagePartsList} that have been extracted.
	 *
	 * @return A {@link List} of {@link Integer}s.
	 */
	public List<Integer> messagePartPositions ()
	{
		return messagePartPositions;
	}

	/**
	 * Answer a record of where each "underscore" occurred in the list of {@link
	 * #messagePartsList}.
	 *
	 * @return A {@link List} of one-based {@link Integer}s.
	 */
	public List<Integer> underscorePartNumbers ()
	{
		return underscorePartNumbers;
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
	 * See {@link MessageSplitter} and {@link ParsingOperation} for an
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
		rootSequence.emitAppendingOn(generator, phraseType);
		generator.optimizeInstructions();
		return generator.instructionsTuple();
	}

	/**
	 * Answer a {@link List} of {@link Expression} objects that correlates with
	 * the {@linkplain #instructionsTupleFor(A_Type) parsing instructions}
	 * generated for the give message name and provided signature tuple type.
	 * Note that the list is 0-based and the tuple is 1-based.
	 *
	 * @param tupleType The tuple of phrase types for this signature.
	 * @return A list that indicates the origin Expression of each {@link
	 *         ParsingOperation}.
	 */
	private List<Expression> originExpressionsFor (final A_Type tupleType)
	{
		final InstructionGenerator generator = new InstructionGenerator();
		rootSequence.emitOn(generator, tupleType);
		generator.optimizeInstructions();
		return generator.expressionList();
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
			final char ch = (char) messageName.tupleAt(position).codePoint();
			if (ch == ' ')
			{
				if (messagePartsList.size() == 0
					|| isCharacterAnUnderscoreOrSpaceOrOperator(
						(char) messageName.tupleAt(position - 1).codePoint()))
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
						|| isCharacterAnUnderscoreOrSpaceOrOperator(
							(char) messageName.tupleAt(position).codePoint()))
				{
					if ((char) messageName.tupleAt(position).codePoint() == '`'
						&& position != messageName.tupleSize()
						&& (char) messageName.tupleAt(position + 1).codePoint()
							== '_')
					{
						// This is legal; we want to be able to parse
						// expressions like "a _b".
					}
					else
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
				}
			}
			else if (ch == '`')
			{
				// Despite what the method comment says, backquote needs to be
				// processed specially when followed by an underscore so that
				// identifiers containing (escaped) underscores can be treated
				// as a single token. Otherwise, they are unparseable.
				if (position == messageName.tupleSize()
					|| messageName.tupleAt(position + 1).codePoint() != '_')
				{
					// We didn't find an underscore, so we need to deal with the
					// backquote in the usual way.
					messagePartsList.add(
						(A_String)(messageName.copyTupleFromToCanDestroy(
							position,
							position,
							false)));
					messagePartPositions.add(position);
					position++;
				}
				else
				{
					boolean sawRegular = false;
					final int start = position;
					while (position <= messageName.tupleSize())
					{
						if (!isCharacterAnUnderscoreOrSpaceOrOperator(
							(char) messageName.tupleAt(position).codePoint()))
						{
							sawRegular = true;
							position++;
						}
						else if (
							messageName.tupleAt(position).codePoint() == '`'
							&& position + 1 <= messageName.tupleSize()
							&& messageName.tupleAt(position + 1).codePoint()
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
							final int cp = messageName.tupleAt(i).codePoint();
							if (cp != '`')
							{
								builder.appendCodePoint(cp);
							}
						}
						messagePartsList.add(
							StringDescriptor.from(builder.toString()));
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
									i,
									i,
									false)));
							messagePartPositions.add(i);
						}
					}
				}
			}
			else if (isCharacterAnUnderscoreOrSpaceOrOperator(ch))
			{
				messagePartsList.add(
					(A_String)(messageName.copyTupleFromToCanDestroy(
						position,
						position,
						false)));
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
					if (!isCharacterAnUnderscoreOrSpaceOrOperator(
						(char) messageName.tupleAt(position).codePoint()))
					{
						position++;
					}
					else if (messageName.tupleAt(position).codePoint() == '`'
						&& position + 1 <= messageName.tupleSize()
						&& messageName.tupleAt(position + 1).codePoint() == '_')
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
						final int cp = messageName.tupleAt(i).codePoint();
						if (cp != '`')
						{
							builder.appendCodePoint(cp);
						}
					}
					messagePartsList.add(StringDescriptor.from(builder.toString()));
				}
				else
				{
					messagePartsList.add(
						(A_String)messageName.copyTupleFromToCanDestroy(
							start,
							position - 1,
							false));
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
		List<Expression> alternatives = new ArrayList<Expression>();
		boolean justParsedVerticalBar = false;
		final Sequence sequence = new Sequence(this);
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
			if (token.equals(closeGuillemet()) || token.equals(doubleDagger()))
			{
				if (justParsedVerticalBar)
				{
					final String problem = token.equals(closeGuillemet())
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
			if (token.equals(underscore()))
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
				Expression argument = null;
				@Nullable A_String nextToken = currentMessagePartOrNull();
				int ordinal = -1;
				if (nextToken != null)
				{
					// Just ate the underscore, so immediately after is where
					// we expect an optional circled number to indicate argument
					// reordering.
					final int codePoint = nextToken.tupleAt(1).codePoint();
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
				if (nextToken != null)
				{
					if (nextToken.equals(singleDagger()))
					{
						messagePartPosition++;
						argument = new ArgumentInModuleScope(this, argStart);
					}
					else if (nextToken.equals(upArrow()))
					{
						messagePartPosition++;
						argument = new VariableQuote(this, argStart);
					}
					else if (nextToken.equals(exclamationMark()))
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
			else if (token.equals(ellipsis()))
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
				@Nullable
				final A_String nextToken = currentMessagePartOrNull();
				if (nextToken != null && nextToken.equals(exclamationMark()))
				{
					sequence.addExpression(
						new RawTokenArgument(this, ellipsisStart));
					messagePartPosition++;
				}
				else if (nextToken != null && nextToken.equals(octothorp()))
				{
					sequence.addExpression(
						new RawWholeNumberLiteralTokenArgument(
							this, ellipsisStart));
					messagePartPosition++;
				}
				else if (nextToken != null && nextToken.equals(dollarSign()))
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
			else if (token.equals(octothorp()))
			{
				throwMalformedMessageException(
					E_OCTOTHORP_MUST_FOLLOW_A_SIMPLE_GROUP_OR_ELLIPSIS,
					"An octothorp (#) may only follow a simple group («») "
					+ "or an ellipsis (…)");
			}
			else if (token.equals(dollarSign()))
			{
				throwMalformedMessageException(
					E_DOLLAR_SIGN_MUST_FOLLOW_AN_ELLIPSIS,
					"A dollar sign ($) may only follow an ellipsis(…)");
			}
			else if (token.equals(questionMark()))
			{
				throwMalformedMessageException(
					E_QUESTION_MARK_MUST_FOLLOW_A_SIMPLE_GROUP,
					"A question mark (?) may only follow a simple group "
					+ "(optional) or a group with no double-dagger (‡)");
			}
			else if (token.equals(tilde()))
			{
				throwMalformedMessageException(
					E_TILDE_MUST_NOT_FOLLOW_ARGUMENT,
					"A tilde (~) must not follow an argument");
			}
			else if (token.equals(verticalBar()))
			{
				throwMalformedMessageException(
					E_VERTICAL_BAR_MUST_SEPARATE_TOKENS_OR_SIMPLE_GROUPS,
					"A vertical bar (|) may only separate tokens or simple "
					+ "groups");
			}
			else if (token.equals(exclamationMark()))
			{
				throwMalformedMessageException(
					E_EXCLAMATION_MARK_MUST_FOLLOW_AN_ALTERNATION_GROUP,
					"An exclamation mark (!) may only follow an alternation "
					+ "group (or follow an underscore for macros)");
			}
			else if (token.equals(upArrow()))
			{
				throwMalformedMessageException(
					E_UP_ARROW_MUST_FOLLOW_ARGUMENT,
					"An up-arrow (↑) may only follow an argument");
			}
			else if (circledNumbersMap.containsKey(
				token.tupleAt(1).codePoint()))
			{
				throwMalformedMessageException(
					E_INCONSISTENT_ARGUMENT_REORDERING,
					"Unquoted circled numbers (⓪-㊿) may only follow an "
					+ "argument, an ellipsis, or an argument group");
			}
			else if (token.equals(openGuillemet()))
			{
				// Eat the open guillemet, parse a subgroup, eat the (mandatory)
				// close guillemet, and add the group.
				messagePartPosition++;
				final Group subgroup = parseGroup();
				justParsedVerticalBar = false;
				if (!atEnd())
				{
					token = currentMessagePart();
				}
				// Otherwise token stays an open guillemet, hence not a close...
				if (!token.equals(closeGuillemet()))
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
					final int codePoint = token.tupleAt(1).codePoint();
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
					if (token.equals(octothorp()))
					{
						if (subgroup.underscoreCount() > 0)
						{
							// Counting group may not contain arguments.
							throwMalformedMessageException(
								E_OCTOTHORP_MUST_FOLLOW_A_SIMPLE_GROUP_OR_ELLIPSIS,
								"An octothorp (#) may only follow a simple "
								+ "group or an ellipsis (…)");
						}
						subexpression = new Counter(subgroup);
						messagePartPosition++;
					}
					else if (token.equals(questionMark()))
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
							subexpression = new Optional(subgroup.beforeDagger);
						}
						messagePartPosition++;
					}
					else if (token.equals(doubleQuestionMark()))
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
						subexpression =
							new CompletelyOptional(this, subgroup.beforeDagger);
						messagePartPosition++;
					}
					else if (token.equals(exclamationMark()))
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
							new NumberedChoice((Alternation)alternation);
						messagePartPosition++;
					}
				}
				if (!atEnd())
				{
					token = currentMessagePart();
					// Try to parse a case-insensitive modifier.
					if (token.equals(tilde()))
					{
						if (!subexpression.isLowerCase())
						{
							throwMalformedMessageException(
								E_CASE_INSENSITIVE_EXPRESSION_CANONIZATION,
								"Tilde (~) may only occur after a lowercase "
								+ "token or a group of lowercase tokens");
						}
						subexpression = new CaseInsensitive(subexpression);
						messagePartPosition++;
					}
				}
				// Parse a vertical bar. If no vertical bar occurs, then either
				// complete an alternation already in progress (including this
				// most recent expression) or add the subexpression directly to
				// the group.
				if (atEnd() || !currentMessagePart().equals(verticalBar()))
				{
					if (alternatives.size() > 0)
					{
						alternatives.add(subexpression);
						subexpression = new Alternation(alternatives);
						alternatives = new ArrayList<Expression>();
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
			else if (token.equals(sectionSign()))
			{
				if (alternatives.size() > 0)
				{
					throwMalformedMessageException(
						E_ALTERNATIVE_MUST_NOT_CONTAIN_ARGUMENTS,
						"Alternative must not contain a section "
						+ "checkpoint (§)");
				}
				assert !justParsedVerticalBar;
				sequence.addExpression(new SectionCheckpoint(this));
				messagePartPosition++;
			}
			else
			{
				// Parse a backquote.
				if (token.equals(backQuote()))
				{
					// Eat the backquote.
					justParsedVerticalBar = false;
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
						|| !isCharacterAnUnderscoreOrSpaceOrOperator(
							(char)token.tupleAt(1).codePoint()))
					{
						// Expected operator character after backquote.
						throwMalformedMessageException(
							E_EXPECTED_OPERATOR_AFTER_BACKQUOTE,
							"Backquote (`) must be followed by an operator "
							+ "character");
					}
				}
				// Parse a regular keyword or operator.
				justParsedVerticalBar = false;
				Expression subexpression =
					new Simple(this, messagePartPosition);
				messagePartPosition++;
				// Parse a completely optional.
				if (!atEnd())
				{
					token = currentMessagePart();
					if (token.equals(doubleQuestionMark()))
					{
						final Sequence singleSequence = new Sequence(this);
						singleSequence.addExpression(subexpression);
						subexpression =
							new CompletelyOptional(this, singleSequence);
						messagePartPosition++;
					}
				}
				// Parse a case insensitive.
				if (!atEnd())
				{
					token = currentMessagePart();
					if (token.equals(tilde()))
					{
						if (!subexpression.isLowerCase())
						{
							throwMalformedMessageException(
								E_CASE_INSENSITIVE_EXPRESSION_CANONIZATION,
								"Tilde (~) may only occur after a lowercase "
								+ "token or a group of lowercase tokens");
						}
						subexpression = new CaseInsensitive(subexpression);
						messagePartPosition++;
					}
				}
				// Parse a vertical bar. If no vertical bar occurs, then either
				// complete an alternation already in progress (including this
				// most recent expression) or add the subexpression directly to
				// the group.
				if (atEnd() || !currentMessagePart().equals(verticalBar()))
				{
					if (alternatives.size() > 0)
					{
						alternatives.add(subexpression);
						subexpression = new Alternation(alternatives);
						alternatives = new ArrayList<Expression>();
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
	 * {@linkplain StringDescriptor#doubleDagger() double dagger} in the
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
	 * @return A {@link Group} expression parsed from the {@link #messagePartsList}.
	 * @throws MalformedMessageException If the method name is malformed.
	 */
	private Group parseGroup ()
		throws MalformedMessageException
	{
		final Sequence beforeDagger = parseSequence();
		if (!atEnd() && currentMessagePart().equals(doubleDagger()))
		{
			messagePartPosition++;
			final Sequence afterDagger = parseSequence();
			if (!atEnd() && currentMessagePart().equals(doubleDagger()))
			{
				// Two daggers were encountered in a group.
				throwMalformedMessageException(
					E_INCORRECT_USE_OF_DOUBLE_DAGGER,
					"A group must have at most one double-dagger (‡)");
			}
			return new Group(beforeDagger, afterDagger);
		}
		return new Group(this, beforeDagger);
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
	public int numberOfUnderscores ()
	{
		return underscorePartNumbers.size();
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
			new Generator<String>()
			{
				@Override
				public String value ()
				{
					final StringBuilder builder = new StringBuilder();
					builder.append(errorMessage);
					final String errorIndicator =
						AvailCompiler.errorIndicatorSymbol;
					builder.append(". See arrow (");
					builder.append(errorIndicator);
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
					builder.append(errorIndicator);
					builder.append(after.asNativeString());
					builder.append("\"");
					return builder.toString();
				}
			});
	}

	/**
	 * Answer whether the specified character is an operator character, space,
	 * underscore, or ellipsis.
	 *
	 * @param aCharacter A Java {@code char}.
	 * @return {@code true} if the specified character is an operator character,
	 *          space, underscore, or ellipsis; or {@code false} otherwise.
	 */
	private static boolean isCharacterAnUnderscoreOrSpaceOrOperator (
		final char aCharacter)
	{
		return aCharacter == '_'
			|| aCharacter == '…'
			|| aCharacter == ' '
			|| aCharacter == '/'
			|| aCharacter == '$'
			|| AvailScanner.isOperatorCharacter(aCharacter);
	}

	@Override
	public String toString ()
	{
		final StringBuilder builder = new StringBuilder();
		dumpForDebug(builder);
		return builder.toString();
	}
}
