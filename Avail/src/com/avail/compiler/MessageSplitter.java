/**
 * com.avail.compiler/MessageSplitter.java
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
import java.util.ArrayList;
import java.util.List;
import com.avail.compiler.scanner.AvailScanner;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.TupleDescriptor;

/**
 * This class is used to split Avail message names into a sequence of
 * pseudo-instructions that can be used directly for parsing.  The
 * pseudo-instructions are of the form:
 * 
 * <p><ul>
 * <li>0     - parseArgument</li>
 * <li>1     - pushEmptyList</li>
 * <li>2     - append (pop A, append A to list on top of stack)</li>
 * <li>3     - (reserved)</li>
 * <li>4*N   - branch to instruction N (attempt to continue parsing at both
 *             the next instruction and N)</li>
 * <li>4*N+1 - jump to instruction N (do not attempt to continue at the next
 *             instruction)</li>
 * <li>4*N+2 - parseKeyword at part N</li>
 * <li>4*N+3 - (reserved)</li>
 * </ul></p>
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 */
public class MessageSplitter
{
	/**
	 * The Avail string to be parsed. 
	 */
	final AvailObject       messageName;

	final List<AvailObject> messageParts = new ArrayList<AvailObject>(10);
	int                     messagePartPosition;
	final List<Integer>     instructions = new ArrayList<Integer>(10);

	
	/**
	 * An {@linkplain Expression} represents a structural view of part of the
	 * message name.
	 */
	abstract class Expression
	{
		public int instructionStart = -1;
		void emitOn (List<Integer> list)
		{
			instructionStart = list.size() + 1;
		}
		
		final boolean isArgumentOrGroup ()
		{
			return this instanceof Argument || this instanceof Group;
		}
		
		@Override
		public String toString ()
		{
			return getClass().getSimpleName();
		}
	};

	
	/**
	 * A {@linkplain Simple} is an {@linkplain Expression} that represents a
	 * single token, except for the double-dagger character.
	 */
	final class Simple extends Expression
	{
		final int tokenIndex;

		Simple (int tokenIndex)
		{
			this.tokenIndex = tokenIndex;
		}

		@Override
		void emitOn (List<Integer> list)
		{
			super.emitOn(list);
			list.add(tokenIndex * 4 + 2);  // parseKeyword
		}
		
		@Override
		public String toString ()
		{
			StringBuilder builder = new StringBuilder();
			builder.append(getClass().getSimpleName());
			builder.append("(");
			builder.append(messageParts.get(tokenIndex - 1).asNativeString());
			return builder.toString();
		}

	}

	
	/**
	 * An {@linkplain Argument} is an occurrence of "_" in a message name.  It
	 * indicates where an argument is expected.
	 */
	final class Argument extends Expression
	{
		@Override
		void emitOn (List<Integer> list)
		{
			super.emitOn(list);
			list.add(0);  // parseArgument
		}
	}


	/**
	 * A {@linkplain Group} is delimited by the open chevron ("«") and close
	 * chevron ("»") characters, and may contain subgroups and an occurrence of
	 * a double dagger ("‡").  If no dagger or subgroup is present, the sequence
	 * of message parts between the chevrons are allowed to occur zero or more
	 * times at a call site (i.e., a send of this message).  When the number of
	 * underscores ("_") plus the number of subgroups is exactly one, the
	 * argument (or subgroup) values are assembled into a list.  Otherwise the
	 * arguments and/or subgroups are assembled into a list of fixed-sized
	 * lists, each containing one entry for each argument or subgroup.
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
		boolean hasDagger = false;
		int argumentsBeforeDagger = 0;
		int argumentsAfterDagger = 0;
		List<Expression> expressionsBeforeDagger = new ArrayList<Expression>();
		List<Expression> expressionsAfterDagger = new ArrayList<Expression>();
		int loopSkip = -1;
		int loopExit = -1;

		final void addExpression (Expression e)
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
		void emitOn (List<Integer> list)
		{
			super.emitOn(list);
			if (argumentsBeforeDagger == 1 && argumentsAfterDagger == 0)
			{
				/* Special case -- one argument case produces a list of
				 * expressions rather than a list of fixed-length lists of
				 * expressions.  The generated instructions should look like:
				 * 
				 * push empty list
				 * branch to @loopSkip
				 * @loopStart:
				 * ...Stuff before dagger.
				 * append  (add solution)
				 * branch to @loopExit (even if no dagger)
				 * ...Stuff after dagger, nothing if dagger is omitted.  Must
				 * ...follow argument or subgroup with "append" instruction.
				 * jump to @loopStart
				 * @loopExit:
				 * @loopSkip:
				 */
				list.add(1);  // push empty list
				list.add(4 * loopSkip);  // branch to @loopSkip
				int loopStart = list.size() + 1;
				for (Expression expression : expressionsBeforeDagger)
				{
					expression.emitOn(list);
				}
				list.add(2);  // append
				list.add(4 * loopExit);  // branch to @loopExit
				for (Expression expression : expressionsAfterDagger)
				{
					assert !expression.isArgumentOrGroup();
					expression.emitOn(list);
				}
				list.add(loopStart * 4 + 1);  // jump to @loopStart
				loopExit = list.size() + 1;
				loopSkip = list.size() + 1;
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
				 * jump @loopStart
				 * @loopExit:
				 * append  (add partial solution up to dagger)
				 * @loopSkip:
				 */
				list.add(1);  // push empty list
				list.add(4 * loopSkip);  // branch to @loopSkip
				int loopStart = list.size() + 1;
				list.add(1);  // inner list
				for (Expression expression : expressionsBeforeDagger)
				{
					expression.emitOn(list);
					if (expression.isArgumentOrGroup())
					{
						list.add(2);  // append
					}
				}
				list.add(4 * loopExit);  // branch to @loopExit
				for (Expression expression : expressionsAfterDagger)
				{
					expression.emitOn(list);
					if (expression.isArgumentOrGroup())
					{
						list.add(2);  // append
					}
				}
				list.add(2);  // add inner list to outer
				list.add(loopStart * 4 + 1);  // jump to @loopStart
				loopExit = list.size() + 1;
				list.add(2);  // append partial tuple, up to dagger
				loopSkip = list.size() + 1;
			}
		}

		@Override
		public String toString ()
		{
			List<String> strings = new ArrayList<String>();
			for (Expression e : expressionsBeforeDagger)
			{
				strings.add(e.toString());
			}
			if (hasDagger)
			{
				strings.add("‡");
				for (Expression e : expressionsAfterDagger)
				{
					strings.add(e.toString());
				}
			}

			StringBuilder builder = new StringBuilder();
			builder.append("Group(");
			boolean first = true;
			for (String s : strings)
			{
				if (first)
				{
					first = false;
				}
				else
				{
					builder.append(", ");
				}
				builder.append(s);
			}
			builder.append(")");
			return builder.toString();
		}
	}
	
	
	public MessageSplitter (AvailObject messageName)
	{
		this.messageName = messageName;
		messageName.makeImmutable();
		splitMessage();
		messagePartPosition = 1;
		Group rootGroup = parseGroup();
		/* Emit it twice -- once to calculate the branch positions, and then
		 * again to output using the correct branches.
		 */
		if (rootGroup.hasDagger)
		{
			error("Dagger is not allowed outside chevrons");
		}
		if (messagePartPosition != messageParts.size() + 1)
		{
			error("Imbalanced chevrons in message: "
				+ messageName.asNativeString());
		}
		for (int i = 1; i <= 3; i++)
		{
			instructions.clear();
			for (Expression expression : rootGroup.expressionsBeforeDagger)
			{
				expression.emitOn(instructions);
			}
		}
		assert rootGroup.expressionsAfterDagger.isEmpty();

		// Debug info...
		List<String> partsList = new ArrayList<String>(messageParts.size());
		for (AvailObject part : messageParts)
		{
			partsList.add(part.asNativeString());
		}
		AvailObject instructionsTuple = instructionsTuple();
		List<Integer> instructionsList = new ArrayList<Integer>();
		for (AvailObject instruction : instructionsTuple)
		{
			instructionsList.add(instruction.extractInt());
		}
		System.out.printf(
			"%s  ->  %s  ->  %s%n",
			messageName.asNativeString(),
			partsList.toString(),
			instructionsList.toString());
	}
	
	public AvailObject messageParts ()
	{
		AvailObject tuple = TupleDescriptor.mutableObjectFromArray(
			messageParts);
		tuple.makeImmutable();
		return tuple;
	}
	
	public AvailObject instructionsTuple ()
	{
		AvailObject tuple = TupleDescriptor.mutableCompressedFromIntegerArray(
			instructions);
		tuple.makeImmutable();
		return tuple;
	}

	AvailObject parserSteps ()
	{
		AvailObject tuple = TupleDescriptor.mutableObjectFromArray(
			messageParts);
		tuple.makeImmutable();
		return tuple;
	}

	
	void splitMessage ()
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
					|| isCharacterUnderscoreOrSpaceOrOperator(
						(char) messageName.tupleAt(position - 1).codePoint()))
				{
					error(
						"Illegally canonized method name"
						+ " (problem before space)");
				}
				//  Skip the space.
				position++;
				if (position > messageName.tupleSize()
						|| isCharacterUnderscoreOrSpaceOrOperator(
							(char) messageName.tupleAt(position).codePoint()))
				{
					error(
						"Illegally canonized method name"
						+ " (problem after space)");
				}
			}
			else if (ch == '_' || AvailScanner.isOperatorCharacter(ch))
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
						&& !isCharacterUnderscoreOrSpaceOrOperator(
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
	
	Group parseGroup ()
	{
		Group group = new Group();
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
				Group subgroup = parseGroup();
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
						|| token.tupleAt(1).codePoint() == ' '
						|| !isCharacterUnderscoreOrSpaceOrOperator(
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
	 * Answer whether the specified character is an operator character, space,
	 * or underscore.
	 *
	 * @param aCharacter A Java {@code char}.
	 * @return {@code true} if the specified character is an operator character,
	 *          space, or underscore; or {@code false} otherwise.
	 */
	private static boolean isCharacterUnderscoreOrSpaceOrOperator (
		final char aCharacter)
	{
		return aCharacter == '_'
			|| aCharacter == ' '
			|| AvailScanner.isOperatorCharacter(aCharacter);
	}

}
