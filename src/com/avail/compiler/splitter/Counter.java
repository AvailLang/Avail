/**
 * Counter.java
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
import com.avail.compiler.splitter.InstructionGenerator.Label;
import com.avail.descriptor.A_Phrase;
import com.avail.descriptor.A_Type;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.IntegerRangeTypeDescriptor;
import com.avail.descriptor.ParseNodeTypeDescriptor.ParseNodeKind;
import com.avail.descriptor.StringDescriptor;
import com.avail.exceptions.SignatureException;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import static com.avail.compiler.ParsingConversionRule.LIST_TO_SIZE;
import static com.avail.compiler.ParsingOperation.*;
import static com.avail.exceptions.AvailErrorCode.E_INCORRECT_TYPE_FOR_COUNTING_GROUP;

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
 * the double dagger. The message "«very‡,»#good" accepts a single
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
	final Group group;

	/**
	 * Construct a new {@link Counter}.
	 *
	 * @param group
	 *        The {@linkplain Group group} whose occurrences should be
	 *        counted.
	 */
	Counter (final Group group)
	{
		this.group = group;
		explicitOrdinal(group.explicitOrdinal());
		group.explicitOrdinal(-1);
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
	void extractSectionCheckpointsInto (
		final List<SectionCheckpoint> sectionCheckpoints)
	{
		group.extractSectionCheckpointsInto(sectionCheckpoints);
	}

	@Override
	public void checkType (
		final A_Type argumentType,
		final int sectionNumber)
	throws SignatureException
	{
		if (!argumentType.isSubtypeOf(
			IntegerRangeTypeDescriptor.wholeNumbers()))
		{
			// The declared type for the subexpression must be a subtype of
			// whole number.
			MessageSplitter.throwSignatureException(E_INCORRECT_TYPE_FOR_COUNTING_GROUP);
		}
	}

	@Override
	void emitOn (
		final InstructionGenerator generator,
		final A_Type phraseType)
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
		final Label $loopStart = new Label();
		final Label $loopExit = new Label();
		final Label $loopSkip = new Label();
		generator.emit(this, SAVE_PARSE_POSITION);
		generator.emit(this, NEW_LIST);
		generator.emit(this, BRANCH, $loopSkip);
		generator.emit($loopStart);
		generator.emit(this, NEW_LIST);
		// Note that even though the Counter cannot contain anything that
		// would push data, the Counter region must not contain a section
		// checkpoint.  There's no point, since the iteration would not be
		// passed, in case it's confusing (number completed versus number
		// started).
		final int oldPartialListsCount = generator.partialListsCount;
		for (final Expression expression : group.beforeDagger.expressions)
		{
			assert !expression.isArgumentOrGroup();
			generator.partialListsCount = Integer.MIN_VALUE;
			expression.emitOn(
				generator,
				null); //TODO MvG - FIGURE OUT the types.
		}
		generator.emit(this, APPEND_ARGUMENT);
		generator.emit(this, BRANCH, $loopExit);
		for (final Expression expression : group.afterDagger.expressions)
		{
			assert !expression.isArgumentOrGroup();
			expression.emitOn(
				generator,
				null); //TODO MvG - FIGURE OUT the types.
		}
		generator.partialListsCount = oldPartialListsCount;
		generator.emit(this, ENSURE_PARSE_PROGRESS);
		generator.emit(this, JUMP, $loopStart);
		generator.emit($loopExit);
		generator.emit(this, ENSURE_PARSE_PROGRESS);
		generator.emit($loopSkip);
		generator.emit(this, DISCARD_SAVED_PARSE_POSITION);
		generator.emit(this, CONVERT, LIST_TO_SIZE.number());
	}

	@Override
	public String toString ()
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
		final @Nullable Iterator<AvailObject> argumentProvider,
		final StringBuilder builder,
		final int indent)
	{
		assert argumentProvider != null;
		final A_Phrase countLiteral = argumentProvider.next();
		assert countLiteral.isInstanceOf(
			ParseNodeKind.LITERAL_NODE.mostGeneralType());
		final int count = countLiteral.token().literal().extractInt();
		for (int i = 1; i <= count; i++)
		{
			if (i > 1)
			{
				builder.append(" ");
			}
			group.printGroupOccurrence(
				Collections.<AvailObject>emptyIterator(),
				builder,
				indent,
				isArgumentOrGroup());
		}
		builder.append("#");
	}

	@Override
	boolean shouldBeSeparatedOnLeft ()
	{
		// This Counter node should be separated on the left if the
		// contained group should be.
		return group.shouldBeSeparatedOnLeft();
	}

	@Override
	boolean shouldBeSeparatedOnRight ()
	{
		// This Counter node should be separated on the right to emphasize
		// the trailing "#".
		return true;
	}
}
