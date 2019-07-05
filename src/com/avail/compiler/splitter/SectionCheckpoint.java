/*
 * SectionCheckpoint.java
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
import com.avail.compiler.ParserState;
import com.avail.compiler.splitter.MessageSplitter.Metacharacter;
import com.avail.descriptor.A_Phrase;
import com.avail.descriptor.A_Type;

import javax.annotation.Nullable;
import java.util.Iterator;
import java.util.List;

import static com.avail.compiler.ParsingOperation.PREPARE_TO_RUN_PREFIX_FUNCTION;
import static com.avail.compiler.ParsingOperation.RUN_PREFIX_FUNCTION;

/**
 * An {@code SectionCheckpoint} expression is an occurrence of the
 * {@linkplain Metacharacter#SECTION_SIGN section sign} (§) in a message
 * name.  It indicates a position at which to save the argument expressions
 * for the message <em>up to this point</em>.  This value is captured in the
 * {@link ParserState} for subsequent use by primitive macros that need to
 * know an outer message send's initial argument expressions while parsing
 * a subsequent argument expression of the same message.
 *
 * <p>In particular, the block definition macro has to capture its
 * (optional) argument declarations before parsing the (optional) label,
 * declaration, since the latter has to be created with a suitable
 * continuation type that includes the argument types.</p>
 */
final class SectionCheckpoint
extends Expression
{
	/**
	 * The occurrence number of this SectionCheckpoint.  The section
	 * checkpoints are one-based and are numbered consecutively in the order
	 * in which they occur in the whole method name.
	 */
	private final int subscript;

	/**
	 * Construct a SectionCheckpoint.
	 *
	 * @param positionInName
	 *        The section checkpoint's position in the message name.
	 * @param subscript
	 *        This section checkpoint's one-based index.
	 */
	SectionCheckpoint (
		final int positionInName,
		final int subscript)
	{
		super(positionInName);
		this.subscript = subscript;
	}

	@Override
	void extractSectionCheckpointsInto (
		final List<SectionCheckpoint> sectionCheckpoints)
	{
		sectionCheckpoints.add(this);
	}

	@Override
	void checkType (
		final A_Type argumentType,
		final int sectionNumber)
	{
		assert false : "checkType() should not be called for " +
			"SectionCheckpoint expressions";
	}

	@Override
	WrapState emitOn (
		final A_Type phraseType,
		final InstructionGenerator generator,
		final WrapState wrapState)
	{
		// Tidy up any partially-constructed groups and invoke the
		// appropriate prefix function.  Note that the partialListsCount is
		// constrained to always be at least one here.
		generator.flushDelayed();
		generator.emit(
			this,
			PREPARE_TO_RUN_PREFIX_FUNCTION,
			generator.partialListsCount);
		generator.emit(this, RUN_PREFIX_FUNCTION, subscript);
		return wrapState;
	}

	@Override
	public void printWithArguments (
		final @Nullable Iterator<? extends A_Phrase> arguments,
		final StringBuilder builder,
		final int indent)
	{
		builder.append('§');
	}

	@Override
	boolean shouldBeSeparatedOnLeft ()
	{
		// The section symbol should always stand out.
		return true;
	}

	@Override
	boolean shouldBeSeparatedOnRight ()
	{
		// The section symbol should always stand out.
		return true;
	}

	@Override
	boolean mightBeEmpty (final A_Type phraseType)
	{
		return true;
	}
}
