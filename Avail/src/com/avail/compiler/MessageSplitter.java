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
 * <ul>
 *    <li>0     - parseArgument
 *    <li>1     - pushEmptyList
 *    <li>2     - append (pop A, append A to list on top of stack)
 *    <li>3     - (reserved)
 *    <li>4*N   - branch to instruction N (attempt to continue parsing at both the
 *                next instruction and N)
 *    <li>4*N+1 - jump to instruction N (do not attempt to continue at the next
 *                instruction)
 *    <li>4*N+2 - parseKeyword at part N
 *    <li>4*N+3 - (reserved)
 * </ul>
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
	final List<Integer>     instructions = new ArrayList<Integer>(20);

	
	/**
	 * An {@linkplain Expression} represents a structural view of part of the
	 * message name.
	 */
	abstract class Expression
	{
		public int instructionStart = -1;
		void emitOn (List<Integer> list)
		{
			if (instructionStart == -1)
			{
				instructionStart = list.size() + 1;
			}
			assert instructionStart == list.size() + 1;
		}
		
		final boolean isArgumentOrGroup ()
		{
			return this instanceof Argument || this instanceof Group;
		}
		
		final boolean isDagger ()
		{
			return this instanceof Dagger;
		}
		
		@Override
		public String toString ()
		{
			return this.getClass().getSimpleName();
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
	}

	
	/**
	 * A {@linkplain Dagger} is an occurrence of "‡" in a message name.  It's
	 * only valid between chevrons, and indicates that the left side is
	 * repeated, but separated by occurrences of the right side.
	 */
	final class Dagger extends Expression
	{
		@Override
		void emitOn (List<Integer> list)
		{
			assert false : "A dagger should not occur outside chevrons.";
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
	 * The message "«A_‡B_»" parses zero or more occurrences in the text of the
	 * keyword "A" followed by an argument, separated by the keyword "B" and an
	 * argument.  "A 1 B 2 A 3 B 4 A 5" is such an expression (and "A 1 B 2" is
	 * not).  In this case, the arguments will be grouped in such a way that the
	 * final element of the list, if any, is missing the post-dagger elements:
	 * <<1,2>,<3,4>,<5>>.
	 */
	final class Group extends Expression
	{
		int argumentCount = 0;
		List<Expression> expressions = new ArrayList<Expression>();
		int loopExit = -1;

		@Override
		void emitOn (List<Integer> list)
		{
			super.emitOn(list);
			if (argumentCount == 1)
			{
				/* Special case -- one argument case produces a list of
				 * expressions rather than a list of fixed-length lists of
				 * expressions.  The generated instructions should look like:
				 * 
				 * push empty list
				 * branch to @loopExit
				 * @loopStart:
				 * ...Stuff before dagger.  The argument or subgroup must be
				 * ...followed by "append" instruction.
				 * branch to @loopExit
				 * ...Stuff after dagger, nothing if dagger is omitted.  Must
				 * ...follow argument or subgroup with "append" instruction.
				 * jump to @loopStart
				 * @loopExit: 
				 */
				list.add(1);  // push empty list
				list.add(4 * loopExit);  // branch to @loopExit
				int loopStart = list.size() + 1;
				boolean sawDagger = false;
				for (Expression expression : expressions)
				{
					if (expression.isArgumentOrGroup())
					{
						expression.emitOn(list);
						list.add(2);  // append
					}
					else if (expression.isDagger())
					{
						assert !sawDagger
							: "Multiple daggers encountered in group";
						sawDagger = true;
						list.add(4 * loopExit);  // branch to @loopExit
					}
					else
					{
						expression.emitOn(list);
					}
				}
				list.add(loopStart * 4 + 1);  // jump to @loopStart
				loopExit = list.size() + 1;
			}
			else
			{
				/* General case -- the individual arguments need to be wrapped
				 * with "append" as for the special case above, but the start
				 * of each loop has to push an empty tuple and the dagger has to
				 * branch to a special @loopExitFromDagger that closes the last
				 * partial group.  Also, the backward jump should be preceded
				 * by an append to capture a solution.  Here's the code:
				 * 
				 * push empty list
				 * branch to @loopSkip
				 * @loopStart:
				 * ...Stuff before dagger, where arguments and subgroups must
				 * ...be followed by "append" instruction.
				 * branch to @loopExitFromDagger
				 * ...Stuff after dagger, nothing if dagger is omitted.  Must
				 * ...follow argument or subgroup with "append" instruction.
				 * append  (to capture a complete pass)
				 * jump @loopStart
				 * @loopExitFromDagger:
				 * append  (final partial tuple, just up to the dagger)
				 * @loopExit:
				 */
			}
		}

		@Override
		public String toString ()
		{
			StringBuilder builder = new StringBuilder();
			builder.append("Group(");
			boolean first = true;
			for (Expression e : expressions)
			{
				if (first)
				{
					first = false;
				}
				else
				{
					builder.append(", ");
				}
				builder.append(e.toString());
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
		/* Emit it twice -- once to calculate offsets, and then again to
		 * generate correct code.  Also treat the outermost group as a plain old
		 * sequence of instructions for the purpose of generating code. 
		 */
		for (int i = 1; i <= 2; i++)
		{
			instructions.clear();
			for (Expression expression : rootGroup.expressions)
			{
				expression.emitOn(instructions);
			}
		}

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
				group.expressions.add(new Argument());
				group.argumentCount++;
				messagePartPosition++;
			}
			else if (token.equals(TupleDescriptor.doubleDaggerTuple()))
			{
				group.expressions.add(new Dagger());
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
				if (!token.equals(TupleDescriptor.closeChevronTuple()))
				{
					error(
						"Expected matching close chevron in method name",
						messageName);
				}
				messagePartPosition++;
				group.expressions.add(subgroup);
				group.argumentCount++;
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
				group.expressions.add(new Simple(messagePartPosition));
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
