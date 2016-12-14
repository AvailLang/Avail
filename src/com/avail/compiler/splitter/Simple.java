/**
 * Simple.java
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
import com.avail.compiler.ParsingOperation;
import com.avail.descriptor.A_String;
import com.avail.descriptor.A_Type;
import com.avail.descriptor.AvailObject;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;

import static com.avail.compiler.ParsingOperation.PARSE_PART;
import static com.avail.compiler.ParsingOperation.PARSE_PART_CASE_INSENSITIVELY;

/**
 * A {@linkplain Simple} is an {@linkplain Expression expression} that
 * represents a single token, except for the double-dagger character.
 */
final class Simple
extends Expression
{
	/**
	 * The {@link MessageSplitter} in which this is a {@link Simple} expression.
	 */
	private MessageSplitter messageSplitter;

	/**
	 * The one-based index of this token within the {@link
	 * MessageSplitter#messagePartsList message parts}.
	 */
	final int tokenIndex;

	/**
	 * Construct a new {@linkplain Simple simple expression} representing a
	 * specific token expected in the input.
	 *
	 * @param tokenIndex
	 *        The one-based index of the token within the {@link
	 *        MessageSplitter#messagePartsList message parts}.
	 */
	Simple (final MessageSplitter messageSplitter, final int tokenIndex)
	{
		super(messageSplitter.messagePartPositions.get(tokenIndex - 1));
		this.messageSplitter = messageSplitter;
		this.tokenIndex = tokenIndex;
	}

	@Override
	boolean isLowerCase ()
	{
		final String token =
			messageSplitter.messagePartsList.get(tokenIndex - 1).asNativeString();
		return token.toLowerCase().equals(token);
	}

	@Override
	public void checkType (
		final A_Type argumentType,
		final int sectionNumber)
	{
		assert false : "checkType() should not be called for Simple" +
				" expressions";
	}

	@Override
	WrapState emitOn (
		final A_Type phraseType,
		final InstructionGenerator generator,
		final WrapState wrapState)
	{
		// Parse the specific keyword.
		final ParsingOperation op = generator.caseInsensitive
			? PARSE_PART_CASE_INSENSITIVELY
			: PARSE_PART;
		generator.emit(this, op, tokenIndex);
		return wrapState;
	}

	@Override
	public String toString ()
	{
		return getClass().getSimpleName() +
			"(" + messageSplitter.messagePartsList.get(tokenIndex - 1) + ")";
	}

	@Override
	public void printWithArguments (
		final @Nullable Iterator<AvailObject> arguments,
		final StringBuilder builder,
		final int indent)
	{
		final A_String token = messageSplitter.messagePartsList.get(tokenIndex - 1);
		builder.append(token.asNativeString());
	}

	/**
	 * Characters which, if they start a token, should vote for having a
	 * space before the token.  If the predecessor agrees, there will be a
	 * space.
	 */
	private final static String charactersThatLikeSpacesBefore = "(=+-×÷*/∧∨:?";

	/**
	 * Characters which, if they end a token, should vote for having a
	 * space after the token.  If the successor agrees, there will be a
	 * space.
	 */
	private final static String charactersThatLikeSpacesAfter = ")]=+-×÷*/∧∨→";

	@Override
	boolean shouldBeSeparatedOnLeft ()
	{
		final String string =
			messageSplitter.messagePartsList
				.get(tokenIndex - 1)
				.asNativeString();
		assert string.length() > 0;
		final int firstCharacter = string.codePointAt(0);
		return Character.isUnicodeIdentifierPart(firstCharacter)
			|| charactersThatLikeSpacesBefore.indexOf(firstCharacter) >= 0;
	}

	@Override
	boolean shouldBeSeparatedOnRight ()
	{
		final String token =
			messageSplitter.messagePartsList
				.get(tokenIndex - 1)
				.asNativeString();
		assert token.length() > 0;
		final int lastCharacter = token.codePointBefore(token.length());
		return Character.isUnicodeIdentifierPart(lastCharacter)
			|| charactersThatLikeSpacesAfter.indexOf(lastCharacter) >= 0;
	}
}
