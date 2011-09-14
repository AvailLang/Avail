/**
 * com.avail.oldcompiler/MessageSplitter.java
 * Copyright (c) 2010, Mark van Gulik.
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

import static com.avail.descriptor.AvailObject.error;
import java.util.*;
import com.avail.compiler.node.*;
import com.avail.compiler.scanning.AvailScanner;
import com.avail.descriptor.*;
import static com.avail.descriptor.TypeDescriptor.Types.*;

/**
 * This class is used to split Avail message names into a sequence of
 * pseudo-instructions that can be used directly for parsing.  The
 * pseudo-instructions are of the form:
 *
 * <p><ul>
 * <li>0     - parseArgument</li>
 * <li>1     - pushEmptyList</li>
 * <li>2     - append (pop A, append A to list on top of stack)</li>
 * <li>3     - push current parse position</li>
 * <li>4     - under-pop parse position (should be 2nd-to-top of stack)</li>
 * <li>5     - check that progress has been made relative to the parse position
 *             found at 2nd-to-top of stack.  Also replace with new parse
 *             position.</li>
 * <li>6     - parseRawToken</li>
 * <li>7     - (reserved)</li>
 * <li>8*N   - branch to instruction N (attempt to continue parsing at both
 *             the next instruction and N)</li>
 * <li>8*N+1 - jump to instruction N (do not attempt to continue at the next
 *             instruction)</li>
 * <li>8*N+2 - parseKeyword at part N</li>
 * <li>8*N+3 - copyArgumentForCheck for Nth leaf argument (position corresponds
 *             with negative precedence restrictions</li>
 * <li>8*N+4 - (reserved)</li>
 * <li>8*N+5 - (reserved)</li>
 * <li>8*N+6 - (reserved)</li>
 * <li>8*N+7 - (reserved)</li>
 * </ul></p>
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 */
public class MessageSplitter
{
	/**
	 * The Avail string to be parsed.
	 */
	final AvailObject messageName;

	/**
	 * The individual tokens (strings) constituting the message.  Alphanumerics
	 * are in runs, separated from other alphanumerics by a single space.
	 * Operator characters are never beside spaces, and are always parsed as
	 * individual tokens.  Open chevron («), double dagger (‡), and close
	 * chevron (») are used to indicate repeated/optional substructures.  The
	 * backquote (`) can precede any operator character to ensure it is not
	 * used in a special way like chevrons and double dagger.  A backquote may
	 * also escape another backquote.
	 */
	final List<AvailObject> messageParts = new ArrayList<AvailObject>(10);

	/**
	 * The current one-based parsing position in the list of tokens.
	 */
	int messagePartPosition;

	/**
	 * The number of non-backquoted underscores/ellipses encountered so far.
	 */
	int numberOfUnderscores;

	/**
	 * A list of integers representing parsing instructions.  These instructions
	 * can parse a specific keyword, recursively parse an argument, branch for
	 * backtracking, and manipulate a stack of parse nodes.
	 */
	final List<Integer> instructions = new ArrayList<Integer>(10);

	/**
	 * The top-most {@link Group}.
	 */
	private final Group rootGroup;


	/**
	 * An {@linkplain Expression} represents a structural view of part of the
	 * message name.
	 */
	abstract class Expression
	{

		/**
		 * Write instructions for parsing me to the given list.
		 * @param list The list of integers {@link MessageSplitter encoding}
		 * parsing instructions.
		 */
		abstract void emitOn (final List<Integer> list);


		/**
		 * Answer whether or not this an argument or group.
		 * @return True if and only if this is an argument or group.
		 */
		boolean isArgumentOrGroup ()
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
		 * Check that the given type signature is appropriate for this message
		 * expression.  If not, throw a suitable exception.
		 *
		 * <p>This is also called recursively on subcomponents, and it checks
		 * that group arguments have the correct structure for what will be
		 * parsed.  The method may reject parses based on the number of
		 * repetitions of a group at a call site, but not the number of
		 * arguments actually delivered by each repetition.  For example, the
		 * message "«_:_‡,»" can limit the number of _:_ pairs to at most 5 by
		 * declaring the tuple type's size to be [5..5].  However, the message
		 * "«_:_‡[_]»" will always produce a tuple of 3-tuples followed by a
		 * 2-tuple (if any elements at all occur).  Attempting to add a method
		 * implementation for this message that only accepted a tuple of
		 * 7-tuples would be inappropriate (and ineffective).  Instead, it
		 * should be required to accept a tuple whose size is in the range
		 * [2..3].</p>
		 *
		 * <p>Note that the outermost (pseudo)group represents the entire
		 * message, so the caller should synthesize a fixed-length {@link
		 * TupleTypeDescriptor tuple type} for the outermost check.</p>
		 *
		 * @param argumentType
		 *        A {@link TupleTypeDescriptor tuple type} describing the types
		 *        of arguments that a method being added will accept.
		 */
		public abstract void checkType (final AvailObject argumentType);


		@Override
		public String toString ()
		{
			return getClass().getSimpleName();
		}
	}


	/**
	 * A {@linkplain Simple} is an {@linkplain Expression} that represents a
	 * single token, except for the double-dagger character.
	 */
	final class Simple extends Expression
	{
		/**
		 * The one-based index of this token within the {@link
		 * MessageSplitter#messageParts message parts}.
		 */
		final int tokenIndex;


		/**
		 * Construct a new {@link Simple} expression representing a specific
		 * token expected in the input.
		 *
		 * @param tokenIndex The one-based index of the token within the {@link
		 * MessageSplitter#messageParts message parts}.
		 */
		Simple (final int tokenIndex)
		{
			this.tokenIndex = tokenIndex;
		}


		@Override
		void emitOn (final List<Integer> list)
		{
			// Parse the specific keyword.
			list.add(tokenIndex * 8 + 2);
		}


		@Override
		public String toString ()
		{
			final StringBuilder builder = new StringBuilder();
			builder.append(getClass().getSimpleName());
			builder.append("(");
			builder.append(messageParts.get(tokenIndex - 1).asNativeString());
			builder.append(")");
			return builder.toString();
		}


		@Override
		public void checkType (final AvailObject argumentType)
		{
			assert false : "checkType() should not be called for Simple" +
					" expressions";
		}
	}


	/**
	 * An {@linkplain Argument} is an occurrence of "_" in a message name.  It
	 * indicates where an argument is expected.
	 */
	class Argument extends Expression
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
		void emitOn (final List<Integer> list)
		{
			// First, parse an argument subexpression.  Next, record it in a
			// list of lists that will later be used to check negative
			// precedence restrictions.  In particular, it will be recorded at
			// position N if this argument is for the Nth non-backquoted
			// underscore/ellipsis of this message.  This is done with two
			// instructions to simplify processing of the recursive expression
			// parse, as well as to make a completely non-recursive
			// parallel-shift-reduce parsing engine easier to build eventually.
			list.add(0);
			list.add(absoluteUnderscoreIndex * 8 + 3);
		}


		/**
		 * A simple underscore/ellipsis can be arbitrarily restricted, other
		 * than when it is restricted to the uninstantiable type {@link
		 * TypeDescriptor.Types#TERMINATES terminates}.
		 */
		@Override
		public void checkType (final AvailObject argumentType)
		{
			if (argumentType.equals(TerminatesTypeDescriptor.terminates()))
			{
				error("Method argument type should not be \"terminates\".");
			}
			return;
		}
	}


	/**
	 * A {@linkplain RawTokenArgument} is an occurrence of "…" in a message
	 * name.  It indicates where a raw token argument is expected.  This is an
	 * unusual kind of argument, in that the next token in the input stream is
	 * captured and passed as a literal argument to the macro.
	 */
	final class RawTokenArgument extends Argument
	{
		/**
		 * Construct a RawTokenArgument.
		 */
		RawTokenArgument ()
		{
			super();
		}

		@Override
		void emitOn (final List<Integer> list)
		{
			// First, parse a raw token.  Next, record it in a list of lists
			// that will later be used to check negative precedence
			// restrictions.  In particular, it will be recorded at position N
			// if this argument is for the Nth non-backquoted underscore or
			// ellipsis of this message.  This is done with two instructions
			// to simplify processing of the recursive expression parse, as well
			// as to make a completely non-recursive parallel-shift-reduce
			// parsing engine easier to build eventually.
			list.add(6);
			list.add(absoluteUnderscoreIndex * 8 + 3);
		}
	}


	/**
	 * A {@linkplain Group} is delimited by the open chevron ("«") and close
	 * chevron ("»") characters, and may contain subgroups and an occurrence of
	 * a double dagger ("‡").  If no dagger or subgroup is present, the sequence
	 * of message parts between the chevrons are allowed to occur zero or more
	 * times at a call site (i.e., a send of this message).  When the number of
	 * underscores ("_") and ellipses ("…") plus the number of subgroups is
	 * exactly one, the argument (or subgroup) values are assembled into a list.
	 * Otherwise the leaf arguments and/or subgroups are assembled into a list
	 * of fixed-sized lists, each containing one entry for each argument or
	 * subgroup.
	 * <p>
	 * When a dagger occurs in a group, the parts to the left of the dagger
	 * can occur zero or more times, but separated by the parts to the right.
	 * For example, "«_‡,»" is how to specify a comma-separated list of
	 * arguments.  This pattern contains a single underscore and no subgroups,
	 * so parsing "1,2,3" would simply produce the list <1,2,3>.  The pattern
	 * "«_=_;»" will parse "1=2;3=4;5=6;" into <<1,2>,<3,4>,<5,6>> because it
	 * has two underscores.
	 * <p>
	 * The message "«A_‡x_»" parses zero or more occurrences in the text of the
	 * keyword "A" followed by an argument, separated by the keyword "x" and an
	 * argument.  "A 1 x 2 A 3 x 4 A 5" is such an expression (and "A 1 x 2" is
	 * not).  In this case, the arguments will be grouped in such a way that the
	 * final element of the list, if any, is missing the post-dagger elements:
	 * <<1,2>,<3,4>,<5>>.
	 */
	final class Group extends Expression
	{
		/**
		 * Whether a double dagger (‡) has been encountered in the tokens for
		 * this group.
		 */
		boolean hasDagger = false;

		/**
		 * How many argument tokens ("_") or ellipses ("…") were specified prior
		 * to the double dagger (or the end of the group if no double dagger is
		 * present).
		 */
		int argumentsBeforeDagger = 0;

		/**
		 * How many argument tokens ("_") or ellipses ("…") appeared after the
		 * double dagger, or zero if there was no double dagger.
		 */
		int argumentsAfterDagger = 0;

		/**
		 * The expressions that appeared before the double dagger, or in the
		 * entire subexpression if no double dagger is present.
		 */
		List<Expression> expressionsBeforeDagger = new ArrayList<Expression>();

		/**
		 * The expressions that appeared after the double dagger, or an empty
		 * list if no double dagger is present.
		 */
		List<Expression> expressionsAfterDagger = new ArrayList<Expression>();

		/**
		 * The one-based position in the instruction stream to branch to in
		 * order to parse zero occurrences of this group.  Set during the first
		 * pass of code generation.
		 */
		int loopSkip = -1;

		/**
		 * The one-based position in the instruction stream to branch to from
		 * the dagger's position within the loop for this group.  Depending on
		 * the number of arguments and subgroups specified within this group,
		 * this may or may not equal {@link #loopSkip}.  If not equal, the only
		 * intervening instruction will be an append to add the final partial
		 * group to the list.
		 */
		int loopExit = -1;

		/**
		 * Add an expression to the group, either before or after the dagger,
		 * depending on whether hasDagger has been set.
		 *
		 * @param e The expression to add.
		 */
		final void addExpression (final Expression e)
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


		/**
		 * Determine if this group should generate a tuple of plain arguments
		 * or a tuple of fixed-length tuples of plain arguments.
		 *
		 * @return True if this group will generate a tuple of fixed-length
		 *         tuples, otherwise false (and the group will generate a tuple
		 *         of individual arguments or subgroups).
		 */
		boolean needsDoubleWrapping ()
		{
			return argumentsBeforeDagger != 1 || argumentsAfterDagger != 0;
		}


		@Override
		void emitOn (final List<Integer> list)
		{
			if (!needsDoubleWrapping())
			{
				/* Special case -- one argument case produces a list of
				 * expressions rather than a list of fixed-length lists of
				 * expressions.  The generated instructions should look like:
				 *
				 * **push current parse position
				 * push empty list
				 * branch to @loopSkip
				 * @loopStart:
				 * ...Stuff before dagger.
				 * append  (add solution)
				 * branch to @loopExit (even if no dagger)
				 * ...Stuff after dagger, nothing if dagger is omitted.  Must
				 * ...follow argument or subgroup with "append" instruction.
				 * ** check progress and update saved position, or abort.
				 * jump to @loopStart
				 * @loopExit:
				 * ** check progress and update saved position, or abort.
				 * @loopSkip:
				 * **under-pop parse position (remove 2nd from top of stack)
				 */
				list.add(3);  // push parse position
				list.add(1);  // push empty list
				list.add(8 * loopSkip);  // branch to @loopSkip
				final int loopStart = list.size() + 1;
				for (final Expression expression : expressionsBeforeDagger)
				{
					expression.emitOn(list);
				}
				list.add(2);  // append
				list.add(8 * loopExit);  // branch to @loopExit
				for (final Expression expression : expressionsAfterDagger)
				{
					assert !expression.isArgumentOrGroup();
					expression.emitOn(list);
				}
				list.add(5);  // check progress
				list.add(loopStart * 8 + 1);  // jump to @loopStart
				loopExit = list.size() + 1;
				list.add(5);  // check progress
				loopSkip = list.size() + 1;
				list.add(4);  // underpop parse position
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
				 * **push current parse position
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
				 * ** check progress and update saved position, or abort.
				 * jump @loopStart
				 * @loopExit:
				 * append  (add partial solution up to dagger)
				 * ** check progress and update saved position, or abort.
				 * @loopSkip:
				 * **under-pop parse position (remove 2nd from top of stack)
				 */
				list.add(3);  // push parse position
				list.add(1);  // push empty list
				list.add(8 * loopSkip);  // branch to @loopSkip
				final int loopStart = list.size() + 1;
				list.add(1);  // inner list
				for (final Expression expression : expressionsBeforeDagger)
				{
					expression.emitOn(list);
					if (expression.isArgumentOrGroup())
					{
						list.add(2);  // append
					}
				}
				list.add(8 * loopExit);  // branch to @loopExit
				for (final Expression expression : expressionsAfterDagger)
				{
					expression.emitOn(list);
					if (expression.isArgumentOrGroup())
					{
						list.add(2);  // append
					}
				}
				list.add(2);  // add inner list to outer
				list.add(5);  // check progress
				list.add(loopStart * 8 + 1);  // jump to @loopStart
				loopExit = list.size() + 1;
				list.add(2);  // append partial tuple, up to dagger
				list.add(5);  // check progress
				loopSkip = list.size() + 1;
				list.add(4);  // underpop parse position
			}
		}

		@Override
		public String toString ()
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
		public void checkType (final AvailObject argumentType)
		{
			// Always expect a tuple of solutions here.
			if (argumentType.equals(TerminatesTypeDescriptor.terminates()))
			{
				error("Method argument type should not be \"terminates\".");
			}

			if (!argumentType.isTupleType())
			{
				error(
					"The repeated group \"" + toString() +
					"\" must accept a tuple, not \"" +
					argumentType.toString() + "\".");
			}

			if (!needsDoubleWrapping())
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
				for (int i = 1, limit = argumentType.typeTuple().tupleSize();
					i <= limit;
					i++)
				{
					final AvailObject solutionType = argumentType.typeAtIndex(i);
					if (!solutionType.isTupleType())
					{
						error(
							"The declared type for the subexpression \"" +
							toString() +
							"\" is expected to be a tuple type whose " +
							Integer.toString(i) +
							"-th element accepts a tuple.");
					}
					// Check that the solution that will reside at the current
					// index accepts either a full group or a group up to the
					// dagger.
					final AvailObject solutionTypeSizes = solutionType.sizeRange();
					final AvailObject lower = solutionTypeSizes.lowerBound();
					final AvailObject upper = solutionTypeSizes.upperBound();
					if (lower != expectedLower && lower != expectedUpper
						|| upper != expectedLower && upper != expectedUpper)
					{
						error(
							"The complex group \"" + toString() +
							"\" should have elements whose types are " +
							"tuples restricted to have lower and upper " +
							"bounds equal to the pre-dagger cardinality (" +
							expectedLower.toString() +
							") or the total cardinality (" +
							expectedUpper.toString() +
							").  Instead, the " + Integer.toString(i) +
							"-th element of the outer list has type " +
							solutionType.toString() + ".");
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


		/**
		 * Pretty-print this part of the message, using the provided argument
		 * {@link ParseNodeDescriptor nodes}.
		 *
		 * @param availObject
		 *        The arguments to this {@link Group}, organized as either a
		 *        single {@link ParseNodeDescriptor parse node} or a {@link
		 *        TupleNodeDescriptor tuple node} of parse nodes, depending on
		 *        {@code doubleWrap}.  The root group always has doubleWrap
		 *        true, and passes a synthetic {@linkplain TupleNodeDescriptor
		 *        tuple node}.
		 * @param aStream
		 *        The {@link StringBuilder} on which to print.
		 * @param indent
		 *        The indentation level.
		 * @param completeGroup
		 *        Whether to produce a complete group or just up to the
		 *        double-dagger.  The last repetition of a subgroup uses false
		 *        for this flag.
		 * @param doubleWrap
		 *        Whether to treat the arguments list as a tuple of values or a
		 *        single value.  True if the group's {@code
		 *        needsDoubleWrapping()} is true.  Forced to true for the root
		 *        group.
		 */
		public void printWithArguments (
			final AvailObject availObject,
			final StringBuilder aStream,
			final int indent,
			final boolean completeGroup,
			final boolean doubleWrap)
		{
			aStream.append("«");
			final Iterator<AvailObject> argumentsIterator;
			if (doubleWrap)
			{
				assert availObject.isInstanceOfKind(TUPLE_NODE.o());
				argumentsIterator = availObject.expressionsTuple().iterator();
			}
			else
			{
				assert availObject.isInstanceOfKind(EXPRESSION_NODE.o());
				final List<AvailObject> argumentNodes =
					Collections.singletonList(availObject);
				argumentsIterator = argumentNodes.iterator();
			}
			boolean isFirst = true;
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
			for (final Expression expr : expressionsToVisit)
			{
				if (!isFirst)
				{
					aStream.append(" ");
				}

				if (expr == null)
				{
					aStream.append("‡");
				}
				else if (expr instanceof Simple)
				{
					final AvailObject token =
						messageParts.get(((Simple)expr).tokenIndex - 1);
					aStream.append(token.asNativeString());
				}
				else if (expr instanceof Argument)
				{
					final AvailObject argument = argumentsIterator.next();
					argument.printOnAvoidingIndent(
						aStream,
						new ArrayList<AvailObject>(),
						indent);
				}
				else
				{
					assert expr instanceof Group;
					final Group subgroup = (Group)expr;
					final AvailObject argument = argumentsIterator.next();
					final AvailObject occurrences = argument.expressionsTuple();
					for (int i = 1; i <= occurrences.tupleSize(); i++)
					{
						if (i > 1)
						{
							aStream.append(" ");
						}
						subgroup.printWithArguments(
							occurrences.tupleAt(i),
							aStream,
							indent,
							i != occurrences.tupleSize(),
							subgroup.needsDoubleWrapping());
					}
				}
				isFirst = false;
			}
			assert !argumentsIterator.hasNext();
			aStream.append("»");
		}
	}


	/**
	 * Construct a new {@link MessageSplitter}, parsing the provided message
	 * into token strings and generating parsing instructions for parsing
	 * occurrences of this message.
	 *
	 * @param messageName An Avail {@link ByteStringDescriptor string}
	 *                    specifying the keywords and arguments of some message
	 *                    being defined.
	 */
	public MessageSplitter (final AvailObject messageName)
	{
		this.messageName = messageName;
		messageName.makeImmutable();
		splitMessage();
		messagePartPosition = 1;
		rootGroup = parseGroup();
		if (rootGroup.hasDagger)
		{
			error("Dagger is not allowed outside chevrons");
		}
		if (messagePartPosition != messageParts.size() + 1)
		{
			error("Imbalanced chevrons in message: "
				+ messageName.asNativeString());
		}
		// Emit it twice -- once to calculate the branch positions, and then
		// again to output using the correct branches.
		for (int i = 1; i <= 2; i++)
		{
			instructions.clear();
			for (final Expression expression : rootGroup.expressionsBeforeDagger)
			{
				expression.emitOn(instructions);
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
		final List<String> partsList = new ArrayList<String>(messageParts.size());
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
		System.out.printf(
			"%s  ->  %s  ->  %s%n",
			messageName.asNativeString(),
			partsList.toString(),
			instructionsList.toString());
	}


	/**
	 * Answer a {@linkplain TupleDescriptor tuple} of Avail {@linkplain
	 * ByteStringDescriptor strings} comprising this message.
	 *
	 * @return A tuple of strings.
	 */
	public AvailObject messageParts ()
	{
		final AvailObject tuple = TupleDescriptor.fromList(messageParts);
		tuple.makeImmutable();
		return tuple;
	}


	/**
	 * Pretty-print a send of this message with given argument nodes.
	 *
	 * @param sendNode
	 *        The {@link SendNodeDescriptor send node} that is being printed.
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
		rootGroup.printWithArguments(
			TupleNodeDescriptor.newExpressions(sendNode.arguments()),
			aStream,
			indent,
			true,
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
	 * Decompose the message name into its constituent token strings.  These
	 * can be subsequently parsed to generate the actual parse instructions.
	 * Do not do any semantic analysis here, not even backquote processing --
	 * that would lead to confusion over whether an operator was supposed to be
	 * treated as a special token like open-chevron («) rather than like a
	 * backquote-escaped token).
	 */
	private void splitMessage ()
	{
		if (messageName.tupleSize() == 0)
		{
			return;
		}
		int position = 1;
		while (position <= messageName.tupleSize()) {
			final char ch = (char) messageName.tupleAt(position).codePoint();
			if (ch == ' ')
			{
				if (messageParts.size() == 0
					|| isCharacterAnUnderscoreOrSpaceOrOperator(
						(char) messageName.tupleAt(position - 1).codePoint()))
				{
					error(
						"Illegally canonized method name"
						+ " (problem before space)");
				}
				//  Skip the space.
				position++;
				if (position > messageName.tupleSize()
						|| isCharacterAnUnderscoreOrSpaceOrOperator(
							(char) messageName.tupleAt(position).codePoint()))
				{
					error(
						"Illegally canonized method name"
						+ " (problem after space)");
				}
			}
			else if (ch == '_'
				|| ch == '…'
				|| AvailScanner.isOperatorCharacter(ch))
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
	 * Create a group from the series of tokens describing it.  This is also
	 * used to construct the outermost sequence of expressions, with the
	 * restriction that an occurrence of a double-dagger in the outermost
	 * pseudo-group is an error.  Expect the {@linkplain #messagePartPosition}
	 * to point (via a one-based offset) to the first token of the group, or
	 * just past the end if the group is empty.  Leave the messagePartPosition
	 * pointing just past the last token of the group.
	 * <p>
	 * The caller is responsible for identifying and skipping an open chevron
	 * prior to this group, and for consuming the close chevron after parsing
	 * the group.  The outermost caller is also responsible for ensuring the
	 * entire input was exactly consumed.
	 *
	 * @return A {@link Group} expression parsed from the {@link #messageParts}.
	 */
	Group parseGroup ()
	{
		final Group group = new Group();
		while (true)
		{
			if (messagePartPosition > messageParts.size())
			{
				return group;
			}
			AvailObject token = messageParts.get(messagePartPosition - 1);
			if (token.equals(TupleDescriptor.closeChevronTuple()))
			{
				return group;
			}
			else if (token.equals(TupleDescriptor.underscoreTuple()))
			{
				group.addExpression(new Argument());
				messagePartPosition++;
			}
			else if (token.equals(TupleDescriptor.ellipsisTuple()))
			{
				group.addExpression(new RawTokenArgument());
				messagePartPosition++;
			}
			else if (token.equals(TupleDescriptor.doubleDaggerTuple()))
			{
				if (group.hasDagger)
				{
					error("Two daggers encountered in group");
				}
				group.hasDagger = true;
				messagePartPosition++;
			}
			else if (token.equals(TupleDescriptor.openChevronTuple()))
			{
				// Eat the open chevron, parse a subgroup, eat the (mandatory)
				// close chevron, and add the group.
				messagePartPosition++;
				final Group subgroup = parseGroup();
				if (messagePartPosition <= messageParts.size())
				{
					token = messageParts.get(messagePartPosition - 1);
				}
				// Otherwise token stays an open chevron, hence not a close...
				if (!token.equals(TupleDescriptor.closeChevronTuple()))
				{
					error(
						"Expected matching close chevron in method name",
						messageName);
				}
				messagePartPosition++;
				group.addExpression(subgroup);
			}
			else
			{
				// Parse a backquote or regular keyword or operator
				if (token.equals(TupleDescriptor.backQuoteTuple()))
				{
					messagePartPosition++;  // eat the backquote
					if (messagePartPosition > messageParts.size())
					{
						error(
							"Expected operator character after backquote, "
								+ "not end of message name",
							messageName);
					}
					token = messageParts.get(messagePartPosition - 1);
					if (token.tupleSize() != 1
						|| !isCharacterAnUnderscoreOrSpaceOrOperator(
							(char)token.tupleAt(1).codePoint()))
					{
						error(
							"Expecting operator character after backquote",
							messageName);
					}
				}
				// Parse a regular keyword or operator
				group.addExpression(new Simple(messagePartPosition));
				messagePartPosition++;
			}
		}
	}


	/**
	 * Return the number of arguments a {@link MethodSignatureDescriptor method}
	 * implementing this name would accept.  Note that this is not necessarily
	 * the number of underscores and ellipses, as a chevron group may contain
	 * zero or more underscores/ellipses (and other chevron groups) but count as
	 * one top-level argument.
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
	 * implementing this name would accept, as a top-level chevron group with N
	 * recursively embedded underscores/ellipses is counted as N, not one.
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
			|| AvailScanner.isOperatorCharacter(aCharacter);
	}


	/**
	 * If the argument is an encoding of a parseKeyword instruction, answer the
	 * index of the keyword into the message parts, otherwise answer zero.
	 *
	 * @param instruction The encoded parsing instruction.
	 * @return The keyword index or zero.
	 */
	public static int keywordIndexFromInstruction (final int instruction)
	{
		if (instruction >= 8 && instruction % 8 == 2)
		{
			return instruction >> 3;
		}
		return 0;
	}


	/**
	 * Given an instruction and program counter, answer the list of successor
	 * program counters that should be explored.  For example, a branch
	 * instruction will need to visit both the program counter plus one
	 * <em>and</em> the branch target.
	 *
	 * @param instruction The encoded parsing instruction at the specified
	 *                    program counter.
	 * @param currentPc The current program counter.
	 * @return The list of successor program counters.
	 */
	public static List<Integer> successorPcs (
		final int instruction,
		final int currentPc)
	{
		if (instruction >= 8)
		{
			if ((instruction & 7) == 0)
			{
				// Branch -- return the next pc *and* the branch target.
				return Arrays.asList(instruction >> 3, currentPc + 1);
			}
			if ((instruction & 7) == 1)
			{
				return Collections.singletonList(instruction >> 3);
			}
		}
		return Collections.singletonList(currentPc + 1);
	}
}
