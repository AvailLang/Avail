/**
 * MessageSplitter.java
 * Copyright © 1993-2012, Mark van Gulik and Todd L Smith.
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

package com.avail.compiler;

import static com.avail.compiler.ParsingOperation.*;
import static com.avail.compiler.ParsingConversionRule.*;
import static com.avail.descriptor.ParseNodeTypeDescriptor.ParseNodeKind.*;
import static com.avail.exceptions.AvailErrorCode.*;
import java.io.PrintStream;
import java.util.*;
import com.avail.AvailRuntime;
import com.avail.annotations.*;
import com.avail.compiler.scanning.AvailScanner;
import com.avail.descriptor.*;
import com.avail.descriptor.ParseNodeTypeDescriptor.ParseNodeKind;
import com.avail.exceptions.*;

/**
 * {@code MessageSplitter} is used to split Avail message names into a sequence
 * of {@linkplain ParsingOperation instructions} that can be used directly for
 * parsing.
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
 */
public class MessageSplitter
{
	/**
	 * The Avail string to be parsed.
	 */
	private final AvailObject messageName;

	/**
	 * The individual tokens ({@linkplain StringDescriptor strings})
	 * constituting the message. Alphanumerics are in runs, separated from other
	 * alphanumerics by a single space. Operator characters are never beside
	 * spaces, and are always parsed as individual tokens. {@linkplain
	 * StringDescriptor#openGuillemet() Open guillemet} («), {@linkplain
	 * StringDescriptor#doubleDagger() double dagger} (‡), and {@linkplain
	 * StringDescriptor#closeGuillemet() close guillemet} (») are used to
	 * indicate repeated substructures. The characters {@linkplain
	 * StringDescriptor#octothorp() octothorp} (#) and {@linkplain
	 * StringDescriptor#questionMark() question mark} (?) modify the output of
	 * repeated substructures. The {@linkplain StringDescriptor#backQuote()}
	 * backquote (`) can precede any operator character, like guillemets and
	 * double dagger, to ensure it is not used in a special way. A backquote
	 * may also escape another backquote.
	 */
	final @NotNull List<AvailObject> messageParts =
		new ArrayList<AvailObject>(10);

	/** The current one-based parsing position in the list of tokens. */
	private int messagePartPosition;

	/**
	 * The number of non-backquoted underscores/ellipses encountered so far.
	 */
	@InnerAccess int numberOfUnderscores;

	/**
	 * A list of integers representing parsing instructions. These instructions
	 * can parse a specific keyword, recursively parse an argument, branch for
	 * backtracking, and manipulate a stack of parse nodes.
	 */
	private final @NotNull List<Integer> instructions =
		new ArrayList<Integer>(10);

	/** The top-most {@linkplain Group group}. */
	final @NotNull Group rootGroup;

	/**
	 * An {@code Expression} represents a structural view of part of the
	 * message name.
	 */
	abstract class Expression
	{
		/**
		 * Write instructions for parsing me to the given list.
		 *
		 * @param list
		 *        The list of integers {@linkplain MessageSplitter encoding}
		 *        parsing instructions.
		 * @param caseInsensitive
		 *        Should keywords be matched case insensitively?
		 */
		abstract void emitOn (
			final @NotNull List<Integer> list,
			final boolean caseInsensitive);

		/**
		 * Answer whether or not this an {@linkplain Argument argument} or
		 * {@linkplain Group group}.
		 *
		 * @return {@code true} if and only if this is an argument or group,
		 *         {@code false} otherwise.
		 */
		boolean isArgumentOrGroup ()
		{
			return false;
		}

		/**
		 * Answer whether or not this a {@linkplain Group group}.
		 *
		 * @return {@code true} if and only if this is an argument or group,
		 *         {@code false} otherwise.
		 */
		boolean isGroup ()
		{
			return false;
		}

		/**
		 * If this isn't even a {@link Group} then it doesn't need
		 * double-wrapping.  Override in Group.
		 *
		 * @return {@code true} if this is a group which will generate a tuple
		 *         of fixed-length tuples, {@code false} if this group will
		 *         generate a tuple of individual arguments or subgroups (or if
		 *         this isn't a group).
		 */
		boolean needsDoubleWrapping ()
		{
			return false;
		}

		/**
		 * Answer the number of non-backquoted underscores/ellipses that occur
		 * in this section of the method name.
		 *
		 * @return The number of non-backquoted underscores/ellipses in the
		 *         receiver.
		 */
		int underscoreCount ()
		{
			return 0;
		}

		/**
		 * Are all keywords of the expression comprised exclusively of lower
		 * case characters?
		 *
		 * @return {@code true} if all keywords of the expression are comprised
		 *         exclusively of lower case characters, {@code false}
		 *         otherwise.
		 */
		boolean isLowerCase ()
		{
			return true;
		}

		/**
		 * Check that the given type signature is appropriate for this message
		 * expression. If not, throw a {@link SignatureException}.
		 *
		 * <p>This is also called recursively on subcomponents, and it checks
		 * that {@linkplain Argument group arguments} have the correct structure
		 * for what will be parsed. The method may reject parses based on the
		 * number of repetitions of a {@linkplain Group group} at a call site,
		 * but not the number of arguments actually delivered by each
		 * repetition. For example, the message "«_:_‡,»" can limit the number
		 * of _:_ pairs to at most 5 by declaring the tuple type's size to be
		 * [5..5]. However, the message "«_:_‡[_]»" will always produce a tuple
		 * of 3-tuples followed by a 2-tuple (if any elements at all occur).
		 * Attempting to add a method implementation for this message that only
		 * accepted a tuple of 7-tuples would be inappropriate (and
		 * ineffective). Instead, it should be required to accept a tuple whose
		 * size is in the range [2..3].</p>
		 *
		 * <p>Note that the outermost (pseudo)group represents the entire
		 * message, so the caller should synthesize a fixed-length {@linkplain
		 * TupleTypeDescriptor tuple type} for the outermost check.</p>
		 *
		 * @param argumentType
		 *        A {@linkplain TupleTypeDescriptor tuple type} describing the
		 *        types of arguments that a method being added will accept.
		 * @throws SignatureException
		 *        If the argument type is inappropriate.
		 */
		public abstract void checkType (final @NotNull AvailObject argumentType)
			throws SignatureException;

		@Override
		public @NotNull String toString ()
		{
			return getClass().getSimpleName();
		}

		/**
		 * Pretty-print this part of the message, using the provided argument
		 * {@linkplain ParseNodeDescriptor nodes}.
		 *
		 * @param arguments
		 *        An {@link Iterator} that provides parse nodes to fill in for
		 *        arguments and subgroups.
		 * @param aStream
		 *        The {@link StringBuilder} on which to print.
		 * @param indent
		 *        The indentation level.
		 */
		abstract public void printWithArguments (
			Iterator<AvailObject> arguments,
			StringBuilder aStream,
			int indent);
	}

	/**
	 * A {@linkplain Simple} is an {@linkplain Expression expression} that
	 * represents a single token, except for the double-dagger character.
	 */
	final class Simple
	extends Expression
	{
		/**
		 * The one-based index of this token within the {@link
		 * MessageSplitter#messageParts message parts}.
		 */
		final int tokenIndex;

		/**
		 * Construct a new {@linkplain Simple simple expression} representing a
		 * specific token expected in the input.
		 *
		 * @param tokenIndex
		 *        The one-based index of the token within the {@link
		 *        MessageSplitter#messageParts message parts}.
		 */
		Simple (final int tokenIndex)
		{
			this.tokenIndex = tokenIndex;
		}

		@Override
		void emitOn (
			final @NotNull List<Integer> list,
			final boolean caseInsensitive)
		{
			// Parse the specific keyword.
			final ParsingOperation op =
				caseInsensitive ? parsePartCaseInsensitive : parsePart;
			list.add(op.encodingForOperand(tokenIndex));
		}

		@Override
		final boolean isLowerCase ()
		{
			final String token =
				messageParts.get(tokenIndex - 1).asNativeString();
			return token.toLowerCase().equals(token);
		}

		@Override
		public @NotNull String toString ()
		{
			final StringBuilder builder = new StringBuilder();
			builder.append(getClass().getSimpleName());
			builder.append("(");
			builder.append(messageParts.get(tokenIndex - 1).asNativeString());
			builder.append(")");
			return builder.toString();
		}

		@Override
		public void checkType (final @NotNull AvailObject argumentType)
		{
			assert false : "checkType() should not be called for Simple" +
					" expressions";
		}

		@Override
		public void printWithArguments (
			final Iterator<AvailObject> arguments,
			final StringBuilder aStream,
			final int indent)
		{
			final AvailObject token = messageParts.get(tokenIndex - 1);
			aStream.append(token.asNativeString());
		}
	}

	/**
	 * An {@linkplain Argument} is an occurrence of {@linkplain
	 * StringDescriptor#underscore() underscore} (_) in a message name. It
	 * indicates where an argument is expected.
	 */
	class Argument
	extends Expression
	{
		/**
		 * The one-based index for this argument.  In particular, it's one plus
		 * the number of non-backquoted underscores/ellipses that occur anywhere
		 * to the left of this one in the message name.
		 */
		final int absoluteUnderscoreIndex;

		/**
		 * Construct an argument.
		 */
		Argument ()
		{
			numberOfUnderscores++;
			absoluteUnderscoreIndex = numberOfUnderscores;
		}

		@Override
		boolean isArgumentOrGroup ()
		{
			return true;
		}

		@Override
		int underscoreCount ()
		{
			return 1;
		}

		@Override
		void emitOn (
			final @NotNull List<Integer> list,
			final boolean caseInsensitive)
		{
			// First, parse an argument subexpression. Next, record it in a list
			// of lists that will later be used to check negative precedence
			// restrictions. In particular, it will be recorded at position N if
			// this argument is for the Nth non-backquoted underscore/ellipsis
			// of this message. This is done with two instructions to simplify
			// processing of the recursive expression parse, as well as to make
			// a completely non-recursive parallel-shift-reduce parsing engine
			// easier to build eventually.
			list.add(parseArgument.encoding());
			list.add(checkArgument.encodingForOperand(
				absoluteUnderscoreIndex));
		}

		/**
		 * A simple underscore/ellipsis can be arbitrarily restricted, other
		 * than when it is restricted to the uninstantiable type {@linkplain
		 * BottomTypeDescriptor#bottom() bottom}.
		 */
		@Override
		public void checkType (final @NotNull AvailObject argumentType)
			throws SignatureException
		{
			if (argumentType.equals(BottomTypeDescriptor.bottom()))
			{
				// Method argument type should not be bottom.
				throwSignatureException(E_INCORRECT_ARGUMENT_TYPE);
			}
			return;
		}

		@Override
		public void printWithArguments (
			final Iterator<AvailObject> arguments,
			final StringBuilder aStream,
			final int indent)
		{
			arguments.next().printOnAvoidingIndent(
				aStream,
				new ArrayList<AvailObject>(),
				indent + 1);
		}

	}

	/**
	 * A {@linkplain RawTokenArgument} is an occurrence of {@linkplain
	 * StringDescriptor#ellipsis() ellipsis} (…) in a message name. It indicates
	 * where a raw token argument is expected. This is an unusual kind of
	 * argument, in that the next token in the input stream is captured and
	 * passed as a literal argument to the macro.
	 */
	final class RawTokenArgument
	extends Argument
	{
		/**
		 * Construct a RawTokenArgument.
		 */
		RawTokenArgument ()
		{
			super();
		}

		@Override
		void emitOn (
			final @NotNull List<Integer> list,
			final boolean caseInsensitive)
		{
			// First, parse a raw token. Next, record it in a list of lists
			// that will later be used to check negative precedence
			// restrictions. In particular, it will be recorded at position N if
			// this argument is for the Nth non-backquoted underscore or
			// ellipsis of this message. This is done with two instructions to
			// simplify processing of the recursive expression parse, especially
			// as it relates to the non-recursive parallel shift-reduce parsing
			// engine.
			list.add(parseRawToken.encoding());
			list.add(checkArgument.encodingForOperand(
				absoluteUnderscoreIndex));
		}

		@Override
		public void printWithArguments (
			final Iterator<AvailObject> arguments,
			final StringBuilder aStream,
			final int indent)
		{
			// Describe the token that was parsed as this raw token argument.
			arguments.next().printOnAvoidingIndent(
				aStream,
				Collections.<AvailObject>emptyList(),
				indent + 1);
		}
	}

	/**
	 * A {@linkplain Group} is delimited by the {@linkplain
	 * StringDescriptor#openGuillemet() open guillemet} («) and {@linkplain
	 * StringDescriptor#closeGuillemet() close guillemet} (») characters, and
	 * may contain subgroups and an occurrence of a {@linkplain
	 * StringDescriptor#doubleDagger() double dagger} (‡). If no double dagger
	 * or subgroup is present, the sequence of message parts between the
	 * guillemets are allowed to occur zero or more times at a call site
	 * (i.e., a send of this message). When the number of {@linkplain
	 * StringDescriptor#underscore() underscores} (_) and {@linkplain
	 * StringDescriptor#ellipsis() ellipses} (…) plus the number of subgroups is
	 * exactly one, the argument (or subgroup) values are assembled into a
	 * {@linkplain TupleDescriptor tuple}. Otherwise the leaf arguments and/or
	 * subgroups are assembled into a tuple of fixed-sized tuples, each
	 * containing one entry for each argument or subgroup.
	 *
	 * <p>When a double dagger occurs in a group, the parts to the left of the
	 * double dagger can occur zero or more times, but separated by the parts to
	 * the right. For example, "«_‡,»" is how to specify a comma-separated tuple
	 * of arguments. This pattern contains a single underscore and no subgroups,
	 * so parsing "1,2,3" would simply produce the tuple <1,2,3>. The pattern
	 * "«_=_;»" will parse "1=2;3=4;5=6;" into <<1,2>,<3,4>,<5,6>> because it
	 * has two underscores.</p>
	 *
	 * <p>The message "«A_‡x_»" parses zero or more occurrences in the text of
	 * the keyword "A" followed by an argument, separated by the keyword "x" and
	 * an argument.  "A 1 x 2 A 3 x 4 A 5" is such an expression (and "A 1 x 2"
	 * is not). In this case, the arguments will be grouped in such a way that
	 * the final element of the tuple, if any, is missing the post-double dagger
	 * elements: <<1,2>,<3,4>,<5>>.</p>
	 */
	final class Group
	extends Expression
	{
		/**
		 * Whether a {@linkplain StringDescriptor#doubleDagger() double dagger}
		 * (‡) has been encountered in the tokens for this group.
		 */
		boolean hasDagger = false;

		/**
		 * How many {@linkplain StringDescriptor#underscore() argument tokens}
		 * (_) or {@linkplain StringDescriptor#ellipsis() ellipses} (…) were
		 * specified prior to the {@linkplain StringDescriptor#doubleDagger()
		 * double dagger} (or the end of the group if no double dagger is
		 * present).
		 */
		int argumentsBeforeDagger = 0;

		/**
		 * How many {@linkplain StringDescriptor#underscore() argument tokens}
		 * (_) or {@linkplain StringDescriptor#ellipsis() ellipses} (…) appeared
		 * after the {@linkplain StringDescriptor#doubleDagger() double dagger},
		 * or zero if there was no double dagger.
		 */
		int argumentsAfterDagger = 0;

		/**
		 * The expressions that appeared before the {@linkplain
		 * StringDescriptor#doubleDagger() double dagger}, or in the entire
		 * subexpression if no double dagger is present.
		 */
		final @NotNull List<Expression> expressionsBeforeDagger =
			new ArrayList<Expression>();

		/**
		 * The expressions that appeared after the {@linkplain
		 * StringDescriptor#doubleDagger() double dagger}, or an empty list if
		 * no double dagger is present.
		 */
		final @NotNull List<Expression> expressionsAfterDagger =
			new ArrayList<Expression>();

		/**
		 * The one-based position in the instruction stream to branch to in
		 * order to parse zero occurrences of this group. Set during the first
		 * pass of code generation.
		 */
		int loopSkip = -1;

		/**
		 * The one-based position in the instruction stream to branch to from
		 * the dagger's position within the loop for this group. Depending on
		 * the number of arguments and subgroups specified within this group,
		 * this may or may not equal {@link #loopSkip}.
		 */
		int loopExit = -1;

		/**
		 * Add an {@linkplain Expression expression} to the {@linkplain Group
		 * group}, either before or after the {@linkplain
		 * StringDescriptor#doubleDagger() double dagger}, depending on whether
		 * {@link #hasDagger} has been set.
		 *
		 * @param e The expression to add.
		 */
		void addExpression (final @NotNull Expression e)
		{
			if (!hasDagger)
			{
				expressionsBeforeDagger.add(e);
				if (e.isArgumentOrGroup())
				{
					argumentsBeforeDagger++;
				}
			}
			else
			{
				expressionsAfterDagger.add(e);
				if (e.isArgumentOrGroup())
				{
					argumentsAfterDagger++;
				}
			}
		}

		@Override
		boolean isArgumentOrGroup ()
		{
			return true;
		}

		@Override
		boolean isGroup ()
		{
			return true;
		}

		@Override
		int underscoreCount ()
		{
			int count = 0;
			for (final Expression expr : expressionsBeforeDagger)
			{
				count += expr.underscoreCount();
			}
			for (final Expression expr : expressionsAfterDagger)
			{
				count += expr.underscoreCount();
			}
			return count;
		}

		@Override
		boolean isLowerCase ()
		{
			for (final Expression expression : expressionsBeforeDagger)
			{
				if (!expression.isLowerCase())
				{
					return false;
				}
			}
			for (final Expression expression : expressionsAfterDagger)
			{
				if (!expression.isLowerCase())
				{
					return false;
				}
			}
			return true;
		}

		/**
		 * Determine if this group should generate a {@linkplain TupleDescriptor
		 * tuple} of plain arguments or a tuple of fixed-length tuples of plain
		 * arguments.
		 *
		 * @return {@code true} if this group will generate a tuple of
		 *         fixed-length tuples, {@code false} if this group will
		 *         generate a tuple of individual arguments or subgroups.
		 */
		@Override
		boolean needsDoubleWrapping ()
		{
			return argumentsBeforeDagger != 1 || argumentsAfterDagger != 0;
		}

		@Override
		void emitOn (
			final @NotNull List<Integer> list,
			final boolean caseInsensitive)
		{
			if (!needsDoubleWrapping())
			{
				/* Special case -- one argument case produces a list of
				 * expressions rather than a list of fixed-length lists of
				 * expressions.  The generated instructions should look like:
				 *
				 * push current parse position
				 * push empty list
				 * branch to @loopSkip
				 * @loopStart:
				 * ...Stuff before dagger.
				 * append  (add solution)
				 * branch to @loopExit (even if no dagger)
				 * ...Stuff after dagger, nothing if dagger is omitted.  Must
				 * ...follow argument or subgroup with "append" instruction.
				 * check progress and update saved position, or abort.
				 * jump to @loopStart
				 * @loopExit:
				 * check progress and update saved position, or abort.
				 * @loopSkip:
				 * under-pop parse position (remove 2nd from top of stack)
				 */
				list.add(saveParsePosition.encoding());
				list.add(newList.encoding());
				list.add(branch.encodingForOperand(loopSkip));
				final int loopStart = list.size() + 1;
				for (final Expression expression : expressionsBeforeDagger)
				{
					expression.emitOn(list, caseInsensitive);
				}
				list.add(appendArgument.encoding());
				list.add(branch.encodingForOperand(loopExit));
				for (final Expression expression : expressionsAfterDagger)
				{
					assert !expression.isArgumentOrGroup();
					expression.emitOn(list, caseInsensitive);
				}
				list.add(ensureParseProgress.encoding());
				list.add(jump.encodingForOperand(loopStart));
				loopExit = list.size() + 1;
				list.add(ensureParseProgress.encoding());
				loopSkip = list.size() + 1;
				list.add(discardSavedParsePosition.encoding());
			}
			else
			{
				/* General case -- the individual arguments need to be wrapped
				 * with "append" as for the special case above, but the start
				 * of each loop has to push an empty tuple, the dagger has to
				 * branch to a special @loopExit that closes the last (partial)
				 * group, and the backward jump should be preceded by an append
				 * to capture a solution.  Here's the code:
				 *
				 * push current parse position
				 * push empty list (the list of solutions)
				 * branch to @loopSkip
				 * @loopStart:
				 * push empty list (a compound solution)
				 * ...Stuff before dagger, where arguments and subgroups must
				 * ...be followed by "append" instruction.
				 * branch to @loopExit
				 * ...Stuff after dagger, nothing if dagger is omitted.  Must
				 * ...follow argument or subgroup with "append" instruction.
				 * append  (add complete solution)
				 * check progress and update saved position, or abort.
				 * jump @loopStart
				 * @loopExit:
				 * append  (add partial solution up to dagger)
				 * check progress and update saved position, or abort.
				 * @loopSkip:
				 * under-pop parse position (remove 2nd from top of stack)
				 */
				list.add(saveParsePosition.encoding());
				list.add(newList.encoding());
				list.add(branch.encodingForOperand(loopSkip));
				final int loopStart = list.size() + 1;
				list.add(newList.encoding());
				for (final Expression expression : expressionsBeforeDagger)
				{
					expression.emitOn(list, caseInsensitive);
					if (expression.isArgumentOrGroup())
					{
						list.add(appendArgument.encoding());
					}
				}
				list.add(branch.encodingForOperand(loopExit));
				for (final Expression expression : expressionsAfterDagger)
				{
					expression.emitOn(list, caseInsensitive);
					if (expression.isArgumentOrGroup())
					{
						list.add(appendArgument.encoding());
					}
				}
				list.add(appendArgument.encoding());
				list.add(ensureParseProgress.encoding());
				list.add(jump.encodingForOperand(loopStart));
				loopExit = list.size() + 1;
				list.add(appendArgument.encoding());
				list.add(ensureParseProgress.encoding());
				loopSkip = list.size() + 1;
				list.add(discardSavedParsePosition.encoding());
			}
		}

		@Override
		public @NotNull String toString ()
		{
			final List<String> strings = new ArrayList<String>();
			for (final Expression e : expressionsBeforeDagger)
			{
				strings.add(e.toString());
			}
			if (hasDagger)
			{
				strings.add("‡");
				for (final Expression e : expressionsAfterDagger)
				{
					strings.add(e.toString());
				}
			}

			final StringBuilder builder = new StringBuilder();
			builder.append("Group(");
			boolean first = true;
			for (final String s : strings)
			{
				if (!first)
				{
					builder.append(", ");
				}
				builder.append(s);
				first = false;
			}
			builder.append(")");
			return builder.toString();
		}

		/**
		 * Check if the given type is suitable for holding values generated by
		 * this group.
		 */
		@Override
		public void checkType (final @NotNull AvailObject argumentType)
			throws SignatureException
		{
			// Always expect a tuple of solutions here.
			if (argumentType.equals(BottomTypeDescriptor.bottom()))
			{
				// Method argument type should not be bottom.
				throwSignatureException(E_INCORRECT_ARGUMENT_TYPE);
			}

			if (!argumentType.isTupleType())
			{
				// The group produces a tuple.
				throwSignatureException(E_INCORRECT_TYPE_FOR_GROUP);
			}

			if (this != rootGroup && !needsDoubleWrapping())
			{
				// Expect a tuple of individual values.  No further checks are
				// needed.
			}
			else
			{
				// Expect a tuple of tuples of values, where the inner tuple
				// size ranges from the number of arguments left of the dagger
				// up to that plus the number of arguments right of the dagger.
				assert argumentType.isTupleType();
				final AvailObject expectedLower = IntegerDescriptor.fromInt(
					argumentsBeforeDagger);
				final AvailObject expectedUpper = IntegerDescriptor.fromInt(
					argumentsBeforeDagger + argumentsAfterDagger);
				final AvailObject typeTuple = argumentType.typeTuple();
				final int limit = typeTuple.tupleSize() + 1;
				for (int i = 1; i <= limit; i++)
				{
					final AvailObject solutionType =
						argumentType.typeAtIndex(i);
					if (solutionType.equals(BottomTypeDescriptor.bottom()))
					{
						// It was the empty tuple type.
						break;
					}
					if (!solutionType.isTupleType())
					{
						// The argument should be a tuple of tuples.
						throwSignatureException(E_INCORRECT_TYPE_FOR_GROUP);
					}
					// Check that the solution that will reside at the current
					// index accepts either a full group or a group up to the
					// dagger.
					final AvailObject solutionTypeSizes =
						solutionType.sizeRange();
					final AvailObject lower = solutionTypeSizes.lowerBound();
					final AvailObject upper = solutionTypeSizes.upperBound();
					if (!(lower.equals(expectedLower)
							|| lower.equals(expectedUpper))
						&& (!(upper.equals(expectedLower)
							|| upper.equals(expectedUpper))))
					{
						// This complex group should have elements whose types
						// are tuples restricted to have sizes ranging from the
						// number of argument subexpressions before the double
						// dagger up to the total number of argument
						// subexpressions in this group.
						throwSignatureException(
							E_INCORRECT_TYPE_FOR_COMPLEX_GROUP);
					}
					int j = 1;
					for (final Expression e : expressionsBeforeDagger)
					{
						if (e.isArgumentOrGroup())
						{
							e.checkType(solutionType.typeAtIndex(j));
							j++;
						}
					}
					for (final Expression e : expressionsAfterDagger)
					{
						if (e.isArgumentOrGroup())
						{
							e.checkType(solutionType.typeAtIndex(j));
							j++;
						}
					}
				}
			}
		}

		@Override
		public void printWithArguments (
			final Iterator<AvailObject> argumentProvider,
			final @NotNull StringBuilder aStream,
			final int indent)
		{
			final boolean needsDouble = needsDoubleWrapping();
			final AvailObject groupArguments = argumentProvider.next();
			final Iterator<AvailObject> occurrenceProvider =
				groupArguments.expressionsTuple().iterator();
			while (occurrenceProvider.hasNext())
			{
				final AvailObject occurrence = occurrenceProvider.next();
				final Iterator<AvailObject> innerIterator;
				if (needsDouble)
				{
					// The occurrence is itself a list node containing the
					// parse nodes to fill in to this group's arguments and
					// subgroups.
					assert occurrence.isInstanceOfKind(
						LIST_NODE.mostGeneralType());
					innerIterator = occurrence.expressionsTuple().iterator();
				}
				else
				{
					// The argumentObject is a listNode of parse nodes.
					// Each parse node is for the single argument or subgroup
					// which is left of the double-dagger (and there are no
					// arguments or subgroups to the right).
					assert occurrence.isInstanceOfKind(
						EXPRESSION_NODE.mostGeneralType());
					final List<AvailObject> argumentNodes =
						Collections.<AvailObject>singletonList(occurrence);
					innerIterator = argumentNodes.iterator();
				}
				printGroupOccurrence(
					innerIterator,
					aStream,
					indent,
					occurrenceProvider.hasNext());
				assert !innerIterator.hasNext();
			}
		}

		/**
		 * Pretty-print this part of the message, using the provided iterator
		 * to supply arguments.  This prints a single occurrence of a repeated
		 * group.  The completeGroup flag indicates if the double-dagger and
		 * subsequent subexpressions should also be printed.
		 *
		 * @param argumentProvider
		 *        An iterator to provide parse nodes for this group occurrence's
		 *        arguments and subgroups.
		 * @param aStream
		 *        The {@link StringBuilder} on which to print.
		 * @param indent
		 *        The indentation level.
		 * @param completeGroup
		 *        Whether to produce a complete group or just up to the
		 *        double-dagger. The last repetition of a subgroup uses false
		 *        for this flag.
		 */
		public void printGroupOccurrence (
			final @NotNull Iterator<AvailObject> argumentProvider,
			final @NotNull StringBuilder aStream,
			final int indent,
			final boolean completeGroup)
		{
			aStream.append("«");
			final List<Expression> expressionsToVisit;
			if (completeGroup && !expressionsAfterDagger.isEmpty())
			{
				expressionsToVisit = new ArrayList<Expression>(
					expressionsBeforeDagger.size()
					+ expressionsAfterDagger.size());
				expressionsToVisit.addAll(expressionsBeforeDagger);
				expressionsToVisit.add(null);  // Represents the dagger
				expressionsToVisit.addAll(expressionsAfterDagger);
			}
			else
			{
				expressionsToVisit = expressionsBeforeDagger;
			}
			boolean isFirst = true;
			for (final Expression expr : expressionsToVisit)
			{
				if (!isFirst)
				{
					aStream.append(" ");
				}
				if (expr == null)
				{
					// Place-holder for the double-dagger.
					aStream.append("‡");
				}
				else
				{
					expr.printWithArguments(
						argumentProvider,
						aStream,
						indent);
				}
				isFirst = false;
			}
			assert !argumentProvider.hasNext();
			aStream.append("»");
		}
	}

	/**
	 * A {@code Counter} is a special subgroup (i.e., not a root group)
	 * indicated by an {@linkplain StringDescriptor#octothorp() octothorp}
	 * following a {@linkplain Group group}. It may not contain {@linkplain
	 * Argument arguments} or subgroups, though it may contain a {@linkplain
	 * StringDescriptor#doubleDagger() double dagger}.
	 *
	 * <p>When a double dagger appears in a counter, the counter produces a
	 * {@linkplain IntegerRangeTypeDescriptor#wholeNumbers() whole number} that
	 * indicates the number of occurrences of the subexpression to the left of
	 * the double dagger. The message "«very‡,»# good" accepts a single
	 * argument: the count of occurrences of "very".</p>
	 *
	 * <p>When no double dagger appears in a counter, then the counter produces
	 * a whole number that indicates the number of occurrences of the entire
	 * group. The message "«very»#good" accepts a single argument: the count of
	 * occurrences of "very".</p>
	 */
	final class Counter
	extends Expression
	{
		/** The {@linkplain Group group} whose occurrences should be counted. */
		final @NotNull Group group;

		/**
		 * The one-based position in the instruction stream to branch to in
		 * order to parse zero occurrences of this group. Set during the first
		 * pass of code generation.
		 */
		int loopSkip = -1;

		/**
		 * The one-based position in the instruction stream to branch to from
		 * the dagger's position within the loop for this group. Depending on
		 * the number of arguments and subgroups specified within this group,
		 * this may or may not equal {@link #loopSkip}.
		 */
		int loopExit = -1;

		/**
		 * Construct a new {@link Counter}.
		 *
		 * @param group
		 *        The {@linkplain Group group} whose occurrences should be
		 *        counted.
		 */
		Counter (final @NotNull Group group)
		{
			this.group = group;
		}

		@Override
		void emitOn (
			final @NotNull List<Integer> list,
			final boolean caseInsensitive)
		{
			/* push current parse position
			 * push empty list
			 * branch to @loopSkip
			 * @loopStart:
			 * push empty list (represents group presence)
			 * ...Stuff before dagger.
			 * append (add solution)
			 * branch to @loopExit (even if no dagger)
			 * ...Stuff after dagger, nothing if dagger is omitted.  Must
			 * ...follow argument or subgroup with "append" instruction.
			 * check progress and update saved position, or abort.
			 * jump to @loopStart
			 * @loopExit:
			 * check progress and update saved position, or abort.
			 * @loopSkip:
			 * under-pop parse position (remove 2nd from top of stack)
			 */
			list.add(saveParsePosition.encoding());
			list.add(newList.encoding());
			list.add(branch.encodingForOperand(loopSkip));
			final int loopStart = list.size() + 1;
			list.add(newList.encoding());
			for (final Expression expression : group.expressionsBeforeDagger)
			{
				assert !expression.isArgumentOrGroup();
				expression.emitOn(list, caseInsensitive);
			}
			list.add(appendArgument.encoding());
			list.add(branch.encodingForOperand(loopExit));
			for (final Expression expression : group.expressionsAfterDagger)
			{
				assert !expression.isArgumentOrGroup();
				expression.emitOn(list, caseInsensitive);
			}
			list.add(ensureParseProgress.encoding());
			list.add(jump.encodingForOperand(loopStart));
			loopExit = list.size() + 1;
			list.add(ensureParseProgress.encoding());
			loopSkip = list.size() + 1;
			list.add(discardSavedParsePosition.encoding());
			list.add(convert.encodingForOperand(listToSize.number()));
		}

		@Override
		boolean isArgumentOrGroup ()
		{
			return true;
		}

		@Override
		int underscoreCount ()
		{
			assert group.underscoreCount() == 0;
			return 0;
		}

		@Override
		boolean isLowerCase ()
		{
			return group.isLowerCase();
		}

		@Override
		public void checkType (final @NotNull AvailObject argumentType)
			throws SignatureException
		{
			if (!argumentType.isSubtypeOf(
				IntegerRangeTypeDescriptor.wholeNumbers()))
			{
				// The declared type for the subexpression must be a subtype of
				// whole number.
				throwSignatureException(E_INCORRECT_TYPE_FOR_COUNTING_GROUP);
			}
		}

		@Override
		public @NotNull String toString ()
		{
			final StringBuilder builder = new StringBuilder();
			builder.append(getClass().getSimpleName());
			builder.append("(");
			builder.append(group);
			builder.append(")");
			return builder.toString();
		}

		@Override
		public void printWithArguments (
			final @NotNull Iterator<AvailObject> argumentProvider,
			final StringBuilder aStream,
			final int indent)
		{
			final AvailObject countLiteral = argumentProvider.next();
			assert countLiteral.isInstanceOf(
				ParseNodeKind.LITERAL_NODE.mostGeneralType());
			final int count = countLiteral.token().literal().extractInt();
			final Iterator<AvailObject> emptyProvider =
				Collections.<AvailObject>emptyList().iterator();
			for (int i = 1; i <= count; i++)
			{
				if (i > 1)
				{
					aStream.append(" ");
				}
				group.printGroupOccurrence(
					emptyProvider,
					aStream,
					indent,
					isArgumentOrGroup());
			}
			aStream.append("#");
		}
	}

	/**
	 * An {@code Optional} is a special subgroup (i.e., not a root group)
	 * indicated by a {@linkplain StringDescriptor#questionMark() question mark}
	 * following a {@linkplain Group group}. It may not contain {@linkplain
	 * Argument arguments} or subgroups and it may not contain a {@linkplain
	 * StringDescriptor#doubleDagger() double dagger}. The group may appear only
	 * once.
	 *
	 * <p>An optional produces a {@linkplain
	 * EnumerationTypeDescriptor#booleanObject() boolean} that indicates whether
	 * there was an occurrence of the group. The message "«very»?good"
	 * accepts a single argument: a boolean that is {@linkplain
	 * AtomDescriptor#trueObject() true} if the token "very" occurred and
	 * {@linkplain AtomDescriptor#falseObject() false} if it did not.</p>
	 */
	final class Optional
	extends Expression
	{
		/** The governed {@linkplain Group group}. */
		final @NotNull Group group;

		/**
		 * The one-based position in the instruction stream to branch to in
		 * order to parse zero occurrences of this group. Set during the first
		 * pass of code generation.
		 */
		int groupSkip = -1;

		/**
		 * Construct a new {@link Optional}.
		 *
		 * @param group
		 *        The governed {@linkplain Group group}.
		 */
		Optional (final @NotNull Group group)
		{
			this.group = group;
		}

		@Override
		void emitOn (
			final @NotNull List<Integer> list,
			final boolean caseInsensitive)
		{
			/* push current parse position
			 * push empty list
			 * branch to @groupSkip
			 * push empty list (represents group presence)
			 * ...Stuff before dagger (i.e., all expressions).
			 * append (add solution)
			 * check progress and update saved position, or abort.
			 * @groupSkip:
			 * under-pop parse position (remove 2nd from top of stack)
			 * convert (list->boolean)
			 */
			list.add(saveParsePosition.encoding());
			list.add(newList.encoding());
			list.add(branch.encodingForOperand(groupSkip));
			list.add(newList.encoding());
			for (final Expression expression : group.expressionsBeforeDagger)
			{
				assert !expression.isArgumentOrGroup();
				expression.emitOn(list, caseInsensitive);
			}
			list.add(appendArgument.encoding());
			list.add(ensureParseProgress.encoding());
			groupSkip = list.size() + 1;
			list.add(discardSavedParsePosition.encoding());
			list.add(convert.encodingForOperand(listToNonemptiness.number()));
		}

		@Override
		boolean isArgumentOrGroup ()
		{
			return true;
		}

		@Override
		int underscoreCount ()
		{
			assert group.underscoreCount() == 0;
			return 0;
		}

		@Override
		boolean isLowerCase ()
		{
			return group.isLowerCase();
		}

		@Override
		public void checkType (final @NotNull AvailObject argumentType)
			throws SignatureException
		{
			if (!argumentType.isSubtypeOf(
				EnumerationTypeDescriptor.booleanObject()))
			{
				// The declared type of the subexpression must be a subtype of
				// boolean.
				throwSignatureException(E_INCORRECT_TYPE_FOR_BOOLEAN_GROUP);
			}
		}

		@Override
		public @NotNull String toString ()
		{
			final StringBuilder builder = new StringBuilder();
			builder.append(getClass().getSimpleName());
			builder.append("(");
			builder.append(group);
			builder.append(")");
			return builder.toString();
		}

		@Override
		public void printWithArguments (
			final Iterator<AvailObject> argumentProvider,
			final StringBuilder aStream,
			final int indent)
		{
			final AvailObject literal = argumentProvider.next();
			assert literal.isInstanceOf(
				ParseNodeKind.LITERAL_NODE.mostGeneralType());
			final boolean flag = literal.token().literal().extractBoolean();
			if (flag)
			{
				group.printGroupOccurrence(
					Collections.<AvailObject>emptyList().iterator(),
					aStream,
					indent,
					true);
			}
		}
	}

	/**
	 * A {@code CompletelyOptional} is a special {@linkplain Expression
	 * expression} indicated by two {@linkplain StringDescriptor#questionMark()
	 * question marks} following a {@linkplain Simple simple} or {@linkplain
	 * Group simple group}. It may not contain {@linkplain Argument arguments}
	 * or non-simple subgroups and it may not contain a {@linkplain
	 * StringDescriptor#doubleDagger() double dagger}. The expression may appear
	 * zero or one times.
	 *
	 * <p>A completely optional does not produce any information. No facility is
	 * provided to determine whether there was an occurrence of the expression.
	 * The message "very??good" accepts no arguments, but may be parsed as
	 * either "very good" or "good".</p>
	 */
	final class CompletelyOptional
	extends Expression
	{
		/** The governed {@linkplain Expression expression}. */
		final @NotNull Expression expression;

		/**
		 * The one-based position in the instruction stream to branch to in
		 * order to parse zero occurrences of this expression. Set during the
		 * first pass of code generation.
		 */
		int expressionSkip = -1;

		/**
		 * Construct a new {@link Counter}.
		 *
		 * @param expression
		 *        The governed {@linkplain Expression expression}.
		 */
		CompletelyOptional (final @NotNull Expression expression)
		{
			this.expression = expression;
		}

		@Override
		void emitOn (
			final @NotNull List<Integer> list,
			final boolean caseInsensitive)
		{
			/* push current parse position
			 * push empty list (just to discard)
			 * branch to @groupSkip
			 * ...Simple or stuff before dagger (i.e., all expressions).
			 * check progress and update saved position, or abort.
			 * @groupSkip:
			 * under-pop parse position (remove 2nd from top of stack)
			 * pop (empty list)
			 */
			list.add(saveParsePosition.encoding());
			list.add(newList.encoding());
			list.add(branch.encodingForOperand(expressionSkip));
			final List<Expression> expressions;
			if (expression instanceof Simple)
			{
				expressions = Collections.singletonList(expression);
			}
			else
			{
				assert expression instanceof Group;
				final Group group = (Group) expression;
				assert group.expressionsAfterDagger.isEmpty();
				assert group.underscoreCount() == 0;
				expressions = group.expressionsBeforeDagger;
			}
			for (final Expression subexpression : expressions)
			{
				assert !subexpression.isArgumentOrGroup();
				subexpression.emitOn(list, caseInsensitive);
			}
			list.add(ensureParseProgress.encoding());
			expressionSkip = list.size() + 1;
			list.add(discardSavedParsePosition.encoding());
			list.add(pop.encoding());
		}

		@Override
		int underscoreCount ()
		{
			assert expression.underscoreCount() == 0;
			return 0;
		}

		@Override
		boolean isLowerCase ()
		{
			return expression.isLowerCase();
		}

		@Override
		public void checkType (final @NotNull AvailObject argumentType)
			throws SignatureException
		{
			assert false :
				"checkType() should not be called for CompletelyOptional" +
				" expressions";
		}

		@Override
		public @NotNull String toString ()
		{
			final StringBuilder builder = new StringBuilder();
			builder.append(getClass().getSimpleName());
			builder.append("(");
			builder.append(expression);
			builder.append(")");
			return builder.toString();
		}

		@Override
		public void printWithArguments (
			final Iterator<AvailObject> argumentProvider,
			final StringBuilder aStream,
			final int indent)
		{
			expression.printWithArguments(
				argumentProvider,
				aStream,
				indent);
			aStream.append("⁇");
		}
	}

	/**
	 * {@code CaseInsensitive} is a special decorator {@linkplain Expression
	 * expression} that causes the decorated expression's keywords to generate
	 * {@linkplain ParsingOperation parse instructions} that cause case
	 * insensitive parsing. It is indicated by a trailing {@linkplain
	 * StringDescriptor#tilde() tilde} ("~").
	 */
	final class CaseInsensitive
	extends Expression
	{
		/**
		 * The {@linkplain Expression expression} whose keywords should be
		 * matched case-insensitively.
		 */
		final Expression expression;

		/**
		 * Construct a new {@link CaseInsensitive}.
		 *
		 * @param expression
		 *        The {@linkplain Expression expression} whose keywords should
		 *        be matched case-insensitively.
		 */
		CaseInsensitive (final Expression expression)
		{
			this.expression = expression;
		}

		@Override
		void emitOn (
			final @NotNull List<Integer> list,
			final boolean caseInsensitive)
		{
			expression.emitOn(list, true);
		}

		@Override
		boolean isArgumentOrGroup ()
		{
			return expression.isArgumentOrGroup();
		}

		@Override
		boolean isGroup ()
		{
			return expression.isGroup();
		}

		@Override
		int underscoreCount ()
		{
			return expression.underscoreCount();
		}

		@Override
		boolean isLowerCase ()
		{
			assert expression.isLowerCase();
			return true;
		}

		@Override
		public void checkType (final @NotNull AvailObject argumentType)
			throws SignatureException
		{
			expression.checkType(argumentType);
		}

		@Override
		public @NotNull String toString ()
		{
			final StringBuilder builder = new StringBuilder();
			builder.append(getClass().getSimpleName());
			builder.append("(");
			builder.append(expression);
			builder.append(")");
			return builder.toString();
		}

		@Override
		public void printWithArguments (
			final Iterator<AvailObject> argumentProvider,
			final StringBuilder aStream,
			final int indent)
		{
			expression.printWithArguments(
				argumentProvider,
				aStream,
				indent);
			aStream.append("~");
		}
	}

	/**
	 * An {@code Alternation} is a special {@linkplain Expression expression}
	 * indicated by interleaved {@linkplain StringDescriptor#verticalBar()
	 * vertical bars} between {@linkplain Simple simples} and {@linkplain
	 * Group simple groups}. It may not contain {@linkplain Argument arguments}.
	 *
	 * <p>An alternation specifies several alternative parses but does not
	 * produce any information. No facility is provided to determine which
	 * alternative occurred during a parse. The message "a|an_" may be parsed as
	 * either "a_" or "an_".</p>
	 *
	 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
	 */
	final class Alternation
	extends Expression
	{
		/** The alternative {@linkplain Expression expressions}. */
		private final @NotNull List<Expression> alternatives;

		/**
		 * The one-based positions in the instruction stream of the labels. All
		 * but the last correspond to the beginnings of the alternatives. The
		 * last corresponds to the first instruction beyond the code segment
		 * associated with the last alternative.
		 */
		private final @NotNull int[] branches;

		/**
		 * Construct a new {@link Alternation}.
		 *
		 * @param alternatives
		 *        The alternative {@linkplain Expression expressions}.
		 */
		Alternation (final @NotNull List<Expression> alternatives)
		{
			this.alternatives = alternatives;
			this.branches = new int[alternatives.size()];
			Arrays.fill(branches, -1);
		}

		@Override
		void emitOn (
			final @NotNull List<Integer> list,
			final @NotNull boolean caseInsensitive)
		{
			/* push current parse position
			 * push empty list (just to discard)
			 * branch to @branches[0]
			 * ...First alternative.
			 * jump to @branches[N-1] (the last branch label)
			 * @branches[0]:
			 * ...Repeat for each alternative, omitting the branch and jump for
			 * ...the last alternative.
			 * @branches[N-1]:
			 * check progress and update saved position, or abort.
			 * under-pop parse position (remove 2nd from top of stack)
			 * pop (empty list)
			 */
			list.add(saveParsePosition.encoding());
			list.add(newList.encoding());
			for (int i = 0; i < alternatives.size(); i++)
			{
				// Generate a branch to the next alternative unless this is the
				// last alternative.
				if (i < alternatives.size() - 1)
				{
					list.add(branch.encodingForOperand(branches[i]));
				}
				alternatives.get(i).emitOn(list, caseInsensitive);
				// Generate a jump to the last label unless this is the last
				// alternative.
				if (i < alternatives.size() - 1)
				{
					list.add(jump.encodingForOperand(
						branches[branches.length - 1]));
				}
				branches[i] = list.size() + 1;
			}
			list.add(ensureParseProgress.encoding());
			list.add(discardSavedParsePosition.encoding());
			list.add(pop.encoding());
		}

		@Override
		int underscoreCount ()
		{
			return 0;
		}

		@Override
		boolean isLowerCase ()
		{
			for (final Expression expression : alternatives)
			{
				if (!expression.isLowerCase())
				{
					return false;
				}
			}
			return true;
		}

		@Override
		public void checkType (final AvailObject argumentType)
			throws SignatureException
		{
			assert false :
				"checkType() should not be called for Alternation expressions";
		}

		@Override
		public @NotNull String toString ()
		{
			final StringBuilder builder = new StringBuilder();
			builder.append(getClass().getSimpleName());
			builder.append("(");
			boolean first = true;
			for (final Expression expression : alternatives)
			{
				if (!first)
				{
					builder.append(',');
				}
				builder.append(expression);
				first = false;
			}
			builder.append(")");
			return builder.toString();
		}

		@Override
		public void printWithArguments (
			final Iterator<AvailObject> argumentProvider,
			final StringBuilder aStream,
			final int indent)
		{
			boolean isFirst = true;
			for (final Expression alternative : alternatives)
			{
				if (!isFirst)
				{
					aStream.append("|");
				}
				alternative.printWithArguments(
					null,
					aStream,
					indent);
				isFirst = false;
			}
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
	 * @throws SignatureException
	 *         If the message name is malformed.
	 */
	public MessageSplitter (final @NotNull AvailObject messageName)
		throws SignatureException
	{
		this.messageName = messageName;
		messageName.makeImmutable();
		splitMessage();
		messagePartPosition = 1;
		rootGroup = parseGroup();
		if (rootGroup.hasDagger)
		{
			throwSignatureException(E_INCORRECT_USE_OF_DOUBLE_DAGGER);
		}
		if (messagePartPosition != messageParts.size() + 1)
		{
			throwSignatureException(E_UNBALANCED_GUILLEMETS);
		}
		// Emit it twice -- once to calculate the branch positions, and then
		// again to output using the correct branches.
		for (int i = 1; i <= 2; i++)
		{
			instructions.clear();
			for (final Expression expression
				: rootGroup.expressionsBeforeDagger)
			{
				expression.emitOn(instructions, false);
			}
		}
		assert rootGroup.expressionsAfterDagger.isEmpty();
		// dumpForDebug();
	}

	/**
	 * Dump debugging information about this {@linkplain MessageSplitter} to
	 * System.out.
	 */
	public void dumpForDebug ()
	{
		final List<String> partsList =
			new ArrayList<String>(messageParts.size());
		for (final AvailObject part : messageParts)
		{
			partsList.add(part.asNativeString());
		}
		final AvailObject instructionsTuple = instructionsTuple();
		final List<Integer> instructionsList = new ArrayList<Integer>();
		for (final AvailObject instruction : instructionsTuple)
		{
			instructionsList.add(instruction.extractInt());
		}
		final PrintStream out = AvailRuntime.current().standardOutputStream();
		out.printf(
			"%s  ->  %s  ->  %s%n",
			messageName.asNativeString(),
			partsList.toString(),
			instructionsList.toString());
	}

	/**
	 * Answer a {@linkplain TupleDescriptor tuple} of Avail {@linkplain
	 * StringDescriptor strings} comprising this message.
	 *
	 * @return A tuple of strings.
	 */
	public AvailObject messageParts ()
	{
		final AvailObject tuple = TupleDescriptor.fromCollection(messageParts);
		tuple.makeImmutable();
		return tuple;
	}

	/**
	 * Pretty-print a send of this message with given argument nodes.
	 *
	 * @param sendNode
	 *        The {@linkplain SendNodeDescriptor send node} that is being
	 *        printed.
	 * @param aStream
	 *        A {@link StringBuilder} on which to pretty-print the send of my
	 *        message with the given arguments.
	 * @param indent
	 *        The current indentation level.
	 */
	public void printSendNodeOnIndent(
		final AvailObject sendNode,
		final StringBuilder aStream,
		final int indent)
	{
		rootGroup.printGroupOccurrence(
			sendNode.argumentsListNode().expressionsTuple().iterator(),
			aStream,
			indent,
			true);
	}

	/**
	 * Answer a {@linkplain TupleDescriptor tuple} of Avail {@linkplain
	 * IntegerDescriptor integers} describing how to parse this message.
	 * See {@link MessageSplitter} for a description of the parse instructions.
	 *
	 * @return The tuple of integers encoding parse instructions for this
	 *         message.
	 */
	public AvailObject instructionsTuple ()
	{
		final AvailObject tuple = TupleDescriptor.fromIntegerList(
			instructions);
		tuple.makeImmutable();
		return tuple;
	}

	/**
	 * Decompose the message name into its constituent token strings. These
	 * can be subsequently parsed to generate the actual parse instructions.
	 * Do not do any semantic analysis here, not even backquote processing –
	 * that would lead to confusion over whether an operator was supposed to be
	 * treated as a special token like open-guillemet («) rather than like a
	 * backquote-escaped token).
	 *
	 * @throws SignatureException If the signature is invalid.
	 */
	private void splitMessage () throws SignatureException
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
				if (messageParts.size() == 0
					|| isCharacterAnUnderscoreOrSpaceOrOperator(
						(char) messageName.tupleAt(position - 1).codePoint()))
				{
					// Problem is before the space.
					throwSignatureException(E_METHOD_NAME_IS_NOT_CANONICAL);
				}
				//  Skip the space.
				position++;
				if (position > messageName.tupleSize()
						|| isCharacterAnUnderscoreOrSpaceOrOperator(
							(char) messageName.tupleAt(position).codePoint()))
				{
					// Problem is after the space.
					throwSignatureException(E_METHOD_NAME_IS_NOT_CANONICAL);
				}
			}
			else if (isCharacterAnUnderscoreOrSpaceOrOperator(ch))
			{
				messageParts.add(messageName.copyTupleFromToCanDestroy(
					position,
					position,
					false));
				position++;
			}
			else
			{
				final int start = position;
				while (position <= messageName.tupleSize()
						&& !isCharacterAnUnderscoreOrSpaceOrOperator(
							(char) messageName.tupleAt(position).codePoint()))
				{
					position++;
				}
				messageParts.add(messageName.copyTupleFromToCanDestroy(
					start,
					position - 1,
					false));
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
	 * @return A {@link Group} expression parsed from the {@link #messageParts}.
	 *
	 * @throws SignatureException If the method name is malformed.
	 */
	Group parseGroup () throws SignatureException
	{
		List<Expression> alternatives = new ArrayList<Expression>();
		final Group group = new Group();
		while (true)
		{
			if (messagePartPosition > messageParts.size())
			{
				return group;
			}
			AvailObject token = messageParts.get(messagePartPosition - 1);
			if (token.equals(StringDescriptor.closeGuillemet()))
			{
				return group;
			}
			else if (token.equals(StringDescriptor.underscore()))
			{
				if (alternatives.size() > 0)
				{
					// Alternations may not contain arguments.
					throwSignatureException(
						E_ALTERNATIVE_MUST_NOT_CONTAIN_ARGUMENTS);
				}
				group.addExpression(new Argument());
				messagePartPosition++;
			}
			else if (token.equals(StringDescriptor.ellipsis()))
			{
				if (alternatives.size() > 0)
				{
					// Alternations may not contain arguments.
					throwSignatureException(
						E_ALTERNATIVE_MUST_NOT_CONTAIN_ARGUMENTS);
				}
				group.addExpression(new RawTokenArgument());
				messagePartPosition++;
			}
			else if (token.equals(StringDescriptor.doubleDagger()))
			{
				if (group.hasDagger)
				{
					// Two daggers were encountered in a group.
					throwSignatureException(E_INCORRECT_USE_OF_DOUBLE_DAGGER);
				}
				if (alternatives.size() > 0)
				{
					// Alternations may not contain arguments.
					throwSignatureException(
						E_ALTERNATIVE_MUST_NOT_CONTAIN_ARGUMENTS);
				}
				group.hasDagger = true;
				messagePartPosition++;
			}
			else if (token.equals(StringDescriptor.octothorp()))
			{
				throwSignatureException(
					E_OCTOTHORP_MUST_FOLLOW_A_SIMPLE_GROUP);
			}
			else if (token.equals(StringDescriptor.questionMark()))
			{
				throwSignatureException(
					E_QUESTION_MARK_MUST_FOLLOW_A_SIMPLE_GROUP);
			}
			else if (token.equals(StringDescriptor.tilde()))
			{
				throwSignatureException(E_TILDE_MUST_NOT_FOLLOW_ARGUMENT);
			}
			else if (token.equals(StringDescriptor.verticalBar()))
			{
				throwSignatureException(
					E_VERTICAL_BAR_MUST_FOLLOW_A_SIMPLE_OR_SIMPLE_GROUP);
			}
			else if (token.equals(StringDescriptor.openGuillemet()))
			{
				// Eat the open guillemet, parse a subgroup, eat the (mandatory)
				// close guillemet, and add the group.
				messagePartPosition++;
				final Group subgroup = parseGroup();
				if (messagePartPosition <= messageParts.size())
				{
					token = messageParts.get(messagePartPosition - 1);
				}
				// Otherwise token stays an open guillemet, hence not a close...
				if (!token.equals(StringDescriptor.closeGuillemet()))
				{
					// Expected matching close guillemet.
					throwSignatureException(E_UNBALANCED_GUILLEMETS);
				}
				messagePartPosition++;
				// Try to parse a counter, optional, and/or case-insensitive.
				Expression subexpression = subgroup;
				if (messagePartPosition <= messageParts.size())
				{
					token = messageParts.get(messagePartPosition - 1);
					if (token.equals(StringDescriptor.octothorp()))
					{
						if (subgroup.underscoreCount() > 0)
						{
							// Counting group may not contain arguments.
							throwSignatureException(
								E_OCTOTHORP_MUST_FOLLOW_A_SIMPLE_GROUP);
						}
						subexpression = new Counter(subgroup);
						messagePartPosition++;
					}
					else if (token.equals(StringDescriptor.questionMark()))
					{
						if (subgroup.underscoreCount() > 0)
						{
							// Optional group may not contain arguments.
							throwSignatureException(
								E_QUESTION_MARK_MUST_FOLLOW_A_SIMPLE_GROUP);
						}
						if (subgroup.hasDagger)
						{
							// Optional group may not contain double dagger.
							throwSignatureException(
								E_QUESTION_MARK_MUST_FOLLOW_A_SIMPLE_GROUP);
						}
						subexpression = new Optional(subgroup);
						messagePartPosition++;
					}
					else if (token.equals(
						StringDescriptor.doubleQuestionMark()))
					{
						if (subgroup.underscoreCount() > 0)
						{
							// Completely optional group may not contain
							// arguments.
							throwSignatureException(
								E_DOUBLE_QUESTION_MARK_MUST_FOLLOW_A_SIMPLE_OR_SIMPLE_GROUP);
						}
						if (subgroup.hasDagger)
						{
							// Completely optional group may not contain double
							// dagger.
							throwSignatureException(
								E_DOUBLE_QUESTION_MARK_MUST_FOLLOW_A_SIMPLE_OR_SIMPLE_GROUP);
						}
						subexpression = new CompletelyOptional(subgroup);
						messagePartPosition++;
					}
				}
				if (messagePartPosition <= messageParts.size())
				{
					token = messageParts.get(messagePartPosition - 1);
					// Try to parse a case-insensitive modifier.
					if (token.equals(StringDescriptor.tilde()))
					{
						if (!subexpression.isLowerCase())
						{
							throwSignatureException(
								E_CASE_INSENSITIVE_EXPRESSION_CANONIZATION);
						}
						subexpression = new CaseInsensitive(subexpression);
						messagePartPosition++;
					}
				}
				// Parse a vertical bar. If no vertical bar occurs, then either
				// complete an alternation already in progress (including this
				// most recent expression) or add the subexpression directly to
				// the group.
				if (messagePartPosition > messageParts.size()
					|| !messageParts.get(messagePartPosition - 1)
						.equals(StringDescriptor.verticalBar()))
				{
					if (alternatives.size() > 0)
					{
						alternatives.add(subexpression);
						subexpression = new Alternation(alternatives);
						alternatives = new ArrayList<Expression>();
					}
					group.addExpression(subexpression);
				}
				else
				{
					if (subexpression.underscoreCount() > 0)
					{
						// Alternations may not contain arguments.
						throwSignatureException(
							E_ALTERNATIVE_MUST_NOT_CONTAIN_ARGUMENTS);
					}
					alternatives.add(subexpression);
					messagePartPosition++;
				}
			}
			else
			{
				// Parse a backquote.
				if (token.equals(StringDescriptor.backQuote()))
				{
					messagePartPosition++;  // eat the backquote
					if (messagePartPosition > messageParts.size())
					{
						// Expected operator character after backquote, not end.
						throwSignatureException(
							E_EXPECTED_OPERATOR_AFTER_BACKQUOTE);
					}
					token = messageParts.get(messagePartPosition - 1);
					if (token.tupleSize() != 1
						|| !isCharacterAnUnderscoreOrSpaceOrOperator(
							(char)token.tupleAt(1).codePoint()))
					{
						// Expected operator character after backquote.
						throwSignatureException(
							E_EXPECTED_OPERATOR_AFTER_BACKQUOTE);
					}
				}
				// Parse a regular keyword or operator.
				Expression subexpression = new Simple(messagePartPosition);
				messagePartPosition++;
				// Parse a completely optional.
				if (messagePartPosition <= messageParts.size())
				{
					token = messageParts.get(messagePartPosition - 1);
					if (token.equals(StringDescriptor.doubleQuestionMark()))
					{
						subexpression = new CompletelyOptional(subexpression);
						messagePartPosition++;
					}
				}
				// Parse a case insensitive.
				if (messagePartPosition <= messageParts.size())
				{
					token = messageParts.get(messagePartPosition - 1);
					if (token.equals(StringDescriptor.tilde()))
					{
						if (!subexpression.isLowerCase())
						{
							throwSignatureException(
								E_CASE_INSENSITIVE_EXPRESSION_CANONIZATION);
						}
						subexpression = new CaseInsensitive(subexpression);
						messagePartPosition++;
					}
				}
				// Parse a vertical bar. If no vertical bar occurs, then either
				// complete an alternation already in progress (including this
				// most recent expression) or add the subexpression directly to
				// the group.
				if (messagePartPosition > messageParts.size()
					|| !messageParts.get(messagePartPosition - 1)
						.equals(StringDescriptor.verticalBar()))
				{
					if (alternatives.size() > 0)
					{
						alternatives.add(subexpression);
						subexpression = new Alternation(alternatives);
						alternatives = new ArrayList<Expression>();
					}
					group.addExpression(subexpression);
				}
				else
				{
					alternatives.add(subexpression);
					messagePartPosition++;
				}
			}
		}
	}

	/**
	 * Return the number of arguments a {@linkplain
	 * MethodImplementationDescriptor method} implementing this name would
	 * accept.  Note that this is not necessarily the number of underscores and
	 * ellipses, as a guillemet group may contain zero or more
	 * underscores/ellipses (and other guillemet groups) but count as one
	 * top-level argument.
	 *
	 * @return The number of arguments this message takes.
	 */
	public int numberOfArguments ()
	{
		return rootGroup.argumentsBeforeDagger + rootGroup.argumentsAfterDagger;
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
		return numberOfUnderscores;
	}

	/**
	 * Check that an {@linkplain ImplementationDescriptor implementation} with
	 * the given {@linkplain FunctionTypeDescriptor signature} is appropriate
	 * for a message like this.
	 *
	 * @param functionType
	 *            A function type.
	 * @throws SignatureException
	 *            If the function type is inappropriate for the method name.
	 */
	public void checkImplementationSignature (
			final @NotNull AvailObject functionType)
		throws SignatureException
	{
		final AvailObject argsTupleType = functionType.argsTupleType();
		final AvailObject sizes = argsTupleType.sizeRange();
		final AvailObject lowerBound = sizes.lowerBound();
		final AvailObject upperBound = sizes.upperBound();
		if (!lowerBound.equals(upperBound) || !lowerBound.isInt())
		{
			// Method implementations (and other implementations) should take a
			// definite number of arguments.
			throwSignatureException(E_INCORRECT_NUMBER_OF_ARGUMENTS);
		}
		final int lowerBoundInt = lowerBound.extractInt();
		if (lowerBoundInt != numberOfArguments())
		{
			throwSignatureException(E_INCORRECT_NUMBER_OF_ARGUMENTS);
		}
		// The checker treats the outer group as needing double-wrapping, so
		// wrap it.
		rootGroup.checkType(
			TupleTypeDescriptor.tupleTypeForSizesTypesDefaultType(
				sizes,
				TupleDescriptor.empty(),
				functionType.argsTupleType()));
	}

	/**
	 * Does the message contain any groups?
	 *
	 * @return {@code true} if the message contains any groups, {@code false}
	 *         otherwise.
	 */
	public boolean containsGroups ()
	{
		for (final Expression expression : rootGroup.expressionsBeforeDagger)
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
	void throwSignatureException (
			final @NotNull AvailErrorCode errorCode)
		throws SignatureException
	{
		throw new SignatureException(errorCode);
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
			|| AvailScanner.isOperatorCharacter(aCharacter);
	}
}
