/*
 * CaseInsensitive.java
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
import com.avail.compiler.ParsingOperation;
import com.avail.compiler.splitter.MessageSplitter.Metacharacter;
import com.avail.descriptor.A_Phrase;
import com.avail.descriptor.A_Type;
import com.avail.exceptions.SignatureException;

import javax.annotation.Nullable;
import java.util.Iterator;
import java.util.List;

/**
 * {@code CaseInsensitive} is a special decorator {@linkplain Expression
 * expression} that causes the decorated expression's keywords to generate
 * {@linkplain ParsingOperation parse instructions} that cause case
 * insensitive parsing. It is indicated by a trailing {@linkplain
 * Metacharacter#TILDE tilde} ("~").
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
	 * Construct a new {@code CaseInsensitive}.
	 *
	 * @param positionInName
	 *        The position of this expression in the message name.
	 * @param expression
	 *        The {@linkplain Expression expression} whose keywords should
	 *        be matched case-insensitively.
	 */
	CaseInsensitive (final int positionInName, final Expression expression)
	{
		super(positionInName);
		this.expression = expression;
		if (expression.canBeReordered())
		{
			explicitOrdinal(expression.explicitOrdinal());
			expression.explicitOrdinal(-1);
		}
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
	void extractSectionCheckpointsInto (
		final List<SectionCheckpoint> sectionCheckpoints)
	{
		expression.extractSectionCheckpointsInto(sectionCheckpoints);
	}

	@Override
	void checkType (
		final A_Type argumentType,
		final int sectionNumber)
	throws SignatureException
	{
		expression.checkType(argumentType, sectionNumber);
	}

	@Override
	WrapState emitOn (
		final A_Type phraseType,
		final InstructionGenerator generator,
		final WrapState wrapState)
	{
		final boolean oldInsensitive = generator.caseInsensitive;
		generator.caseInsensitive = true;
		final WrapState newWrapState =
			expression.emitOn(phraseType, generator, wrapState);
		generator.caseInsensitive = oldInsensitive;
		return newWrapState;
	}

	@Override
	public String toString ()
	{
		return getClass().getSimpleName() + '(' + expression + ')';
	}

	@Override
	public void printWithArguments (
		final @Nullable Iterator<? extends A_Phrase> argumentProvider,
		final StringBuilder builder,
		final int indent)
	{
		expression.printWithArguments(
			argumentProvider,
			builder,
			indent);
		builder.append('~');
	}

	@Override
	boolean shouldBeSeparatedOnLeft ()
	{
		return expression.shouldBeSeparatedOnLeft();
	}

	@Override
	boolean shouldBeSeparatedOnRight ()
	{
		// Since we show the tilde (~) after the subexpression, and since
		// case insensitivity is most likely to apply to textual tokens, we
		// visually emphasize the tilde by ensuring a space follows it.
		return true;
	}

	@Override
	boolean mightBeEmpty (
		final A_Type phraseType)
	{
		return expression.mightBeEmpty(phraseType);
	}
}
