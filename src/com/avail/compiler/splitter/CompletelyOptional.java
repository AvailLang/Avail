/**
 * CompletelyOptional.java
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
import com.avail.descriptor.ListNodeDescriptor;
import com.avail.descriptor.StringDescriptor;
import com.avail.descriptor.TupleDescriptor;
import com.avail.exceptions.MalformedMessageException;
import com.avail.exceptions.SignatureException;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import static com.avail.compiler.ParsingOperation.*;
import static com.avail.exceptions.AvailErrorCode.E_INCONSISTENT_ARGUMENT_REORDERING;

/**
 * A {@code CompletelyOptional} is a special {@linkplain Expression
 * expression} indicated by a {@linkplain
 * StringDescriptor#doubleQuestionMark() double question mark} following a
 * {@linkplain Simple simple} or {@linkplain Group simple group}. It may not
 * contain {@linkplain Argument arguments} or non-simple subgroups and it
 * may not contain a {@linkplain StringDescriptor#doubleDagger() double
 * dagger}. The expression may appear zero or one times.
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
	final Expression expression;

	/**
	 * Construct a new {@link Counter}.
	 *
	 * @param expression
	 *        The governed {@linkplain Expression expression}.
	 * @throws MalformedMessageException
	 *         If the inner expression has an {@link #explicitOrdinal()}.
	 */
	CompletelyOptional (
		final MessageSplitter messageSplitter,
		final Expression expression)
		throws MalformedMessageException
	{
		this.expression = expression;
		if (expression.canBeReordered()
			&& expression.explicitOrdinal() != -1)
		{
			messageSplitter.throwMalformedMessageException(
				E_INCONSISTENT_ARGUMENT_REORDERING,
				"Completely optional phrase should not have a circled "
				+ "number to indicate reordering");
		}
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
	void extractSectionCheckpointsInto (
		final List<SectionCheckpoint> sectionCheckpoints)
	{
		expression.extractSectionCheckpointsInto(sectionCheckpoints);
	}

	@Override
	public void checkType (
		final A_Type argumentType,
		final int sectionNumber)
	throws SignatureException
	{
		assert false :
			"checkType() should not be called for CompletelyOptional" +
			" expressions";
	}

	@Override
	void emitOn (
		final InstructionGenerator generator,
		final A_Type phraseType)
	{
		/* branch to @expressionSkip.
		 * push current parse position on the mark stack.
		 * ...Simple or stuff before dagger (i.e., all expressions).
		 * check progress and update saved position, or abort.
		 * discard mark position
		 * @expressionSkip:
		 */
		final Label $expressionSkip = new Label();
		generator.emit(this, BRANCH, $expressionSkip);
		generator.emit(this, SAVE_PARSE_POSITION);
		final List<Expression> expressions;
		if (expression instanceof Simple)
		{
			expressions = Collections.singletonList(expression);
		}
		else
		{
			assert expression instanceof Group;
			final Group group = (Group) expression;
			assert group.afterDagger.expressions.isEmpty();
			assert group.underscoreCount() == 0;
			expressions = group.beforeDagger.expressions;
		}
		for (final Expression subexpression : expressions)
		{
			assert !subexpression.isArgumentOrGroup();
			// The partialListsCount stays the same, in case there's a
			// section checkpoint marker within this completely optional
			// region.  That's a reasonable way to indicate that a prefix
			// function should only run when the optional section actually
			// occurs.  Since no completely optional section can produce a
			// value (argument, counter, etc), there's no problem.
			subexpression.emitOn(
				generator,
				null); //TODO MvG - FIGURE OUT types
		}
		generator.emit(this, ENSURE_PARSE_PROGRESS);
		generator.emit(this, DISCARD_SAVED_PARSE_POSITION);
		generator.emit($expressionSkip);
	}

	@Override
	public String toString ()
	{
		return getClass().getSimpleName() + "(" + expression + ")";
	}

	@Override
	public void printWithArguments (
		final @Nullable Iterator<AvailObject> argumentProvider,
		final StringBuilder builder,
		final int indent)
	{
		// Don't consume any real arguments.  In case the expression is
		// itself a group, synthesize a dummy argument for it, containing
		// just an entry for that group.  The entry should itself contain a
		// single empty list of arguments for an occurrence.  That is, there
		// is one argument-position worth of arguments in the iterator, and
		// it holds one occurrence of the group (to make it print once), and
		// since it's really a CompletelyOptional group, the occurrence has
		// no values within it.
		final A_Phrase emptyListNode = ListNodeDescriptor.empty();
		final A_Phrase oneEmptyListNode = ListNodeDescriptor.newExpressions(
			TupleDescriptor.from(emptyListNode));
		expression.printWithArguments(
			TupleDescriptor.from(oneEmptyListNode).iterator(),
			builder,
			indent);
		builder.append("⁇");
	}

	@Override
	boolean shouldBeSeparatedOnLeft ()
	{
		return expression.isGroup() || expression.shouldBeSeparatedOnLeft();
	}

	@Override
	boolean shouldBeSeparatedOnRight ()
	{
		// Emphasize the double question mark that will always be printed
		// by ensuring a space follows it.
		return true;
	}
}
