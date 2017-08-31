/**
 * Counter.java
 * Copyright © 1993-2017, The Avail Foundation, LLC.
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
import com.avail.compiler.splitter.MessageSplitter.Metacharacter;
import com.avail.descriptor.A_Phrase;
import com.avail.descriptor.A_Type;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.IntegerRangeTypeDescriptor;
import com.avail.descriptor.ListNodeTypeDescriptor;
import com.avail.descriptor.ParseNodeTypeDescriptor.ParseNodeKind;
import com.avail.exceptions.SignatureException;
import javax.annotation.Nullable;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import static com.avail.compiler.splitter.WrapState.*;
import static com.avail.compiler.ParsingConversionRule.LIST_TO_SIZE;
import static com.avail.compiler.ParsingOperation.*;
import static com.avail.exceptions.AvailErrorCode.E_INCORRECT_TYPE_FOR_COUNTING_GROUP;

/**
 * A {@code Counter} is a special subgroup (i.e., not a root group)
 * indicated by an {@linkplain Metacharacter#OCTOTHORP octothorp}
 * following a {@linkplain Group group}. It may not contain {@linkplain
 * Argument arguments} or subgroups, though it may contain a {@linkplain
 * Metacharacter#DOUBLE_DAGGER double dagger}.
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
	private final Group group;

	/**
	 * Construct a new {@link Counter}.
	 *
	 * @param positionInName
	 *        The position of the start of the group in the message name.
	 * @param group
	 *        The {@linkplain Group group} whose occurrences should be counted.
	 */
	Counter (final int positionInName, final Group group)
	{
		super(positionInName);
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
			MessageSplitter.throwSignatureException(
				E_INCORRECT_TYPE_FOR_COUNTING_GROUP);
		}
	}

	@Override
	WrapState emitOn (
		final A_Type phraseType,
		final InstructionGenerator generator,
		final WrapState wrapState)
	{
		/* push current parse position
		 * push empty list
		 * branch to $loopSkip
		 * $loopStart:
		 * ...Stuff before dagger.  Must not have arguments or subgroups.
		 * push empty list (represents group presence)
		 * append (add solution)
		 * branch to $loopExit (even if no dagger)
		 * ...Stuff after dagger, nothing if dagger is omitted.  Must not have
		 * ...arguments or subgroups.
		 * check progress and update saved position, or abort.
		 * jump to $loopStart
		 * $loopExit:
		 * check progress and update saved position, or abort.
		 * $loopSkip:
		 * under-pop parse position (remove 2nd from top of stack)
		 */
		generator.flushDelayed();
		final boolean needsProgressCheck =
			group.beforeDagger.mightBeEmpty(phraseType);
		final Label $loopStart = new Label();
		final Label $loopExit = new Label();
		final Label $loopSkip = new Label();
		generator.emitIf(needsProgressCheck, this, SAVE_PARSE_POSITION);
		generator.emit(this, EMPTY_LIST);
		generator.emit(this, BRANCH, $loopSkip);
		generator.emit($loopStart);
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
				ListNodeTypeDescriptor.empty(),
				generator,
				SHOULD_NOT_HAVE_ARGUMENTS);
		}
		generator.emit(this, EMPTY_LIST);
		generator.emit(this, APPEND_ARGUMENT);
		generator.emit(this, BRANCH, $loopExit);
		for (final Expression expression : group.afterDagger.expressions)
		{
			assert !expression.isArgumentOrGroup();
			expression.emitOn(
				ListNodeTypeDescriptor.empty(),
				generator,
				SHOULD_NOT_HAVE_ARGUMENTS);
		}
		generator.partialListsCount = oldPartialListsCount;
		generator.emitIf(needsProgressCheck, this, ENSURE_PARSE_PROGRESS);
		generator.emit(this, JUMP, $loopStart);
		generator.emit($loopExit);
		generator.emitIf(needsProgressCheck, this, ENSURE_PARSE_PROGRESS);
		generator.emit($loopSkip);
		generator.emitIf(
			needsProgressCheck, this, DISCARD_SAVED_PARSE_POSITION);
		generator.emit(this, CONVERT, LIST_TO_SIZE.number());
		return wrapState.processAfterPushedArgument(this, generator);
	}

	@Override
	public String toString ()
	{
		return getClass().getSimpleName() + "(" + group + ")";
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
				Collections.emptyIterator(),
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

	@Override
	boolean mightBeEmpty (final A_Type phraseType)
	{
		final A_Type integerRangeType = phraseType.expressionType();
		assert integerRangeType.isIntegerRangeType();
		return integerRangeType.lowerBound().equalsInt(0);
	}
}
