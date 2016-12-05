/**
 * Argument.java
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
import com.avail.compiler.splitter.MessageSplitter.Metacharacter;
import com.avail.descriptor.A_BasicObject;
import com.avail.descriptor.A_Type;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.BottomTypeDescriptor;
import com.avail.exceptions.SignatureException;
import org.jetbrains.annotations.Nullable;

import java.util.IdentityHashMap;
import java.util.Iterator;

import static com.avail.compiler.ParsingOperation.CHECK_ARGUMENT;
import static com.avail.compiler.ParsingOperation.PARSE_ARGUMENT;
import static com.avail.compiler.ParsingOperation.TYPE_CHECK_ARGUMENT;
import static com.avail.exceptions.AvailErrorCode.E_INCORRECT_ARGUMENT_TYPE;

/**
 * An {@linkplain Argument} is an occurrence of {@linkplain
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
	 * Construct an argument.
	 *
	 * @param startTokenIndex The one-based index of the underscore token.
	 */
	Argument (
		final MessageSplitter messageSplitter,
		final int startTokenIndex)
	{
		super(messageSplitter.messagePartPositions.get(startTokenIndex - 1));
		messageSplitter.incrementLeafArgumentCount();
		absoluteUnderscoreIndex = messageSplitter.numberOfLeafArguments();
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
	public void checkType (
		final A_Type argumentType,
		final int sectionNumber)
	throws SignatureException
	{
		if (argumentType.isBottom())
		{
			// Method argument type should not be bottom.
			MessageSplitter.throwSignatureException(E_INCORRECT_ARGUMENT_TYPE);
		}
	}

	/**
	 * Parse an argument subexpression, then check that it has an acceptable
	 * form (i.e., does not violate a grammatical restriction for that
	 * argument position).
	 */
	@Override
	void emitOn (
		final InstructionGenerator generator,
		final A_Type phraseType)
	{
		generator.emit(this, PARSE_ARGUMENT);
		generator.emitDelayed(this, CHECK_ARGUMENT, absoluteUnderscoreIndex);
		generator.emitDelayed(
			this,
			TYPE_CHECK_ARGUMENT,
			MessageSplitter.indexForConstant(phraseType));
	}

	@Override
	public void printWithArguments (
		final @Nullable Iterator<AvailObject> arguments,
		final StringBuilder builder,
		final int indent)
	{
		assert arguments != null;
		arguments.next().printOnAvoidingIndent(
			builder,
			new IdentityHashMap<A_BasicObject, Void>(),
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
