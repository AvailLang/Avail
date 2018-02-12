/*
 * Alternation.java
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
import com.avail.compiler.splitter.InstructionGenerator.Label;
import com.avail.compiler.splitter.MessageSplitter.Metacharacter;
import com.avail.descriptor.A_Type;
import com.avail.descriptor.AvailObject;

import javax.annotation.Nullable;
import java.util.Iterator;
import java.util.List;

import static com.avail.compiler.ParsingOperation.*;
import static com.avail.descriptor.BottomTypeDescriptor.bottom;
import static com.avail.descriptor.ListNodeTypeDescriptor.emptyListNodeType;

/**
 * An {@code Alternation} is a special {@linkplain Expression expression}
 * indicated by interleaved {@linkplain Metacharacter#VERTICAL_BAR
 * vertical bars} between {@linkplain Simple simples} and {@linkplain
 * Group simple groups}. It may not contain {@linkplain Argument arguments}.
 *
 * <p>An alternation specifies several alternative parses but does not
 * produce any information. No facility is provided to determine which
 * alternative occurred during a parse. The message "a|an_" may be parsed as
 * either "a_" or "an_".</p>
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
final class Alternation
extends Expression
{
	/** The alternative {@linkplain Expression expressions}. */
	private final List<Expression> alternatives;

	/**
	 * Answer my {@link List} of {@linkplain #alternatives}.
	 *
	 * @return My alternative {@linkplain Expression expressions}.
	 */
	List<Expression> alternatives ()
	{
		return alternatives;
	}

	/**
	 * Construct a new {@link Alternation}.
	 *
	 * @param positionInName
	 *        The position of this expression in the message name.
	 * @param alternatives
	 *        The alternative {@linkplain Expression expressions}.
	 */
	Alternation (final int positionInName, final List<Expression> alternatives)
	{
		super(positionInName);
		this.alternatives = alternatives;
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
	void extractSectionCheckpointsInto (
		final List<SectionCheckpoint> sectionCheckpoints)
	{
		for (final Expression alternative : alternatives)
		{
			alternative.extractSectionCheckpointsInto(sectionCheckpoints);
		}
	}

	@Override
	public void checkType (
		final A_Type argumentType,
		final int sectionNumber)
	{
		assert false :
			"checkType() should not be called for Alternation expressions";
	}

	@Override
	WrapState emitOn (
		final A_Type phraseType,
		final InstructionGenerator generator,
		final WrapState wrapState)
	{
		/* push current parse position on the mark stack
		 * branch to @branches[0]
		 * ...First alternative.
		 * jump to @branches[N-1] (the last branch label)
		 * @branches[0]:
		 * ...Repeat for each alternative, omitting the branch and jump for
		 * ...the last alternative.
		 * @branches[N-1]:
		 * check progress and update saved position, or abort.
		 * pop the parse position.
		 */
		final Label $after = new Label();
		boolean needsProgressCheck = false;
		for (final Expression alternative : alternatives)
		{
			needsProgressCheck |= alternative.mightBeEmpty(
				bottom());
		}
		generator.flushDelayed();
		generator.emitIf(needsProgressCheck, this, SAVE_PARSE_POSITION);
		for (int i = 0; i < alternatives.size(); i++)
		{
			// Generate a branch to the next alternative unless this is the
			// last alternative.
			final Label $nextAlternative = new Label();
			if (i < alternatives.size() - 1)
			{
				generator.emitBranchForward(this, $nextAlternative);
			}
			// The partialListsCount stays the same, in case there's a
			// section checkpoint marker in one of the alternatives.  That's
			// a reasonable way to indicate that a prefix function should
			// only run when that alternative occurs.  Since no alternative
			// can produce a value (argument, counter, etc), there's no
			// problem.
			final WrapState newWrapState = alternatives.get(i).emitOn(
				emptyListNodeType(), generator, wrapState);
			assert newWrapState == wrapState;
			// Generate a jump to the last label unless this is the last
			// alternative.
			if (i < alternatives.size() - 1)
			{
				generator.emitJumpForward(this, $after);
			}
			generator.emit($nextAlternative);
		}
		generator.emit($after);
		generator.emitIf(needsProgressCheck, this, ENSURE_PARSE_PROGRESS);
		generator.emitIf(
			needsProgressCheck, this, DISCARD_SAVED_PARSE_POSITION);
		return wrapState;
	}

	@Override
	public String toString ()
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
		final @Nullable Iterator<AvailObject> argumentProvider,
		final StringBuilder builder,
		final int indent)
	{
		boolean isFirst = true;
		for (final Expression alternative : alternatives)
		{
			if (!isFirst)
			{
				builder.append("|");
			}
			alternative.printWithArguments(
				null,
				builder,
				indent);
			isFirst = false;
		}
	}

	@Override
	boolean shouldBeSeparatedOnLeft ()
	{
		return alternatives.get(0).shouldBeSeparatedOnLeft();
	}

	@Override
	boolean shouldBeSeparatedOnRight ()
	{
		final Expression last = alternatives.get(alternatives.size() - 1);
		return last.shouldBeSeparatedOnRight();
	}

	@Override
	boolean mightBeEmpty (
		final A_Type phraseType)
	{
		for (final Expression alternative : alternatives)
		{
			if (alternative.mightBeEmpty(bottom()))
			{
				return true;
			}
		}
		return false;
	}
}
