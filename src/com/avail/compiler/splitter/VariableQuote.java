/**
 * VariableQuote.java
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
import com.avail.descriptor.A_BasicObject;
import com.avail.descriptor.A_Type;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.ParseNodeTypeDescriptor.ParseNodeKind;
import com.avail.descriptor.ReferenceNodeDescriptor;
import com.avail.descriptor.StringDescriptor;
import com.avail.descriptor.VariableDescriptor;
import org.jetbrains.annotations.Nullable;

import java.util.IdentityHashMap;
import java.util.Iterator;

import static com.avail.compiler.ParsingOperation.CHECK_ARGUMENT;
import static com.avail.compiler.ParsingOperation.PARSE_VARIABLE_REFERENCE;
import static com.avail.compiler.ParsingOperation.TYPE_CHECK_ARGUMENT;

/**
 * A {@linkplain VariableQuote} is an occurrence of {@linkplain
 * StringDescriptor#upArrow() up arrow} (↑) after an underscore in a
 * message name. It indicates that the expression must be the name of a
 * {@linkplain VariableDescriptor variable} that is currently in-scope. It
 * produces a {@linkplain ReferenceNodeDescriptor reference} to the
 * variable, rather than extracting its value.
 */
final class VariableQuote
extends Argument
{
	/**
	 * Construct a new {@link VariableQuote}.
	 *
	 * @param startTokenIndex The one-based token index of this argument.
	 */
	VariableQuote (
		final MessageSplitter splitter,
		final int startTokenIndex)
	{
		super(splitter, startTokenIndex);
	}

	@Override
	void emitOn (
		final InstructionGenerator generator,
		final A_Type phraseType)
	{
		generator.flushDelayed();
		generator.emit(this, PARSE_VARIABLE_REFERENCE);
		generator.emitDelayed(this, CHECK_ARGUMENT, absoluteUnderscoreIndex);
		if (!ParseNodeKind.REFERENCE_NODE.mostGeneralType().isSubtypeOf(
			phraseType))
		{
			generator.emitDelayed(
				this,
				TYPE_CHECK_ARGUMENT,
				MessageSplitter.indexForConstant(phraseType));
		}
	}

	@Override
	public void printWithArguments (
		final @Nullable Iterator<AvailObject> arguments,
		final StringBuilder builder,
		final int indent)
	{
		assert arguments != null;
		// Describe the variable reference that was parsed as this argument.
		arguments.next().printOnAvoidingIndent(
			builder,
			new IdentityHashMap<A_BasicObject, Void>(),
			indent + 1);
		builder.append("↑");
	}
}
