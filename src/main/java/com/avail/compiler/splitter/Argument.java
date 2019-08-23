/*
 * Argument.java
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
import com.avail.compiler.splitter.MessageSplitter.Metacharacter;
import com.avail.descriptor.A_Phrase;
import com.avail.descriptor.A_Type;
import com.avail.descriptor.BottomTypeDescriptor;
import com.avail.exceptions.SignatureException;

import javax.annotation.Nullable;
import java.util.IdentityHashMap;
import java.util.Iterator;

import static com.avail.compiler.ParsingOperation.*;
import static com.avail.compiler.splitter.MessageSplitter.indexForConstant;
import static com.avail.compiler.splitter.MessageSplitter.throwSignatureException;
import static com.avail.exceptions.AvailErrorCode.E_INCORRECT_ARGUMENT_TYPE;
import static com.avail.utility.Nulls.stripNull;

/**
 * An {@code Argument} is an occurrence of {@linkplain
 * Metacharacter#UNDERSCORE underscore} (_) in a message name. It
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
	 * Construct an argument, given the one-based position of the token in the
	 * name, and the absolute index of this argument in the entire message name.
	 *
	 * @param positionInName
	 *        The one-based position of the start of the token in the message
	 *        name.
	 * @param absoluteUnderscoreIndex
	 *        The one-based index of this argument within the entire message
	 *        name's list of arguments.
	 */
	Argument (
		final int positionInName,
		final int absoluteUnderscoreIndex)
	{
		super(positionInName);
		this.absoluteUnderscoreIndex = absoluteUnderscoreIndex;
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

	/**
	 * A simple underscore/ellipsis can be arbitrarily restricted, other
	 * than when it is restricted to the uninstantiable type {@linkplain
	 * BottomTypeDescriptor#bottom() bottom}.
	 */
	@Override
	void checkType (
		final A_Type argumentType,
		final int sectionNumber)
	throws SignatureException
	{
		// Method argument type should not be bottom.
		if (argumentType.isBottom())
		{
			throwSignatureException(E_INCORRECT_ARGUMENT_TYPE);
		}
	}

	/**
	 * Parse an argument subexpression, then check that it has an acceptable
	 * form (i.e., does not violate a grammatical restriction for that
	 * argument position).
	 */
	@Override
	WrapState emitOn (
		final A_Type phraseType,
		final InstructionGenerator generator,
		final WrapState wrapState)
	{
		generator.flushDelayed();
		generator.emit(this, PARSE_ARGUMENT);
		generator.emitDelayed(this, CHECK_ARGUMENT, absoluteUnderscoreIndex);
		generator.emitDelayed(
			this,
			TYPE_CHECK_ARGUMENT,
			indexForConstant(phraseType));
		return wrapState.processAfterPushedArgument(this, generator);
	}

	@Override
	public void printWithArguments (
		final @Nullable Iterator<? extends A_Phrase> arguments,
		final StringBuilder builder,
		final int indent)
	{
		stripNull(arguments).next().printOnAvoidingIndent(
			builder,
			new IdentityHashMap<>(),
			indent + 1);
	}

	@Override
	boolean shouldBeSeparatedOnLeft ()
	{
		return true;
	}

	@Override
	boolean shouldBeSeparatedOnRight ()
	{
		return true;
	}
}
