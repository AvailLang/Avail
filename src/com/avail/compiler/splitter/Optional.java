/**
 * Optional.java
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
import com.avail.descriptor.AtomDescriptor;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.EnumerationTypeDescriptor;
import com.avail.descriptor.ListNodeTypeDescriptor;
import com.avail.descriptor.ParseNodeTypeDescriptor.ParseNodeKind;
import com.avail.descriptor.StringDescriptor;
import com.avail.exceptions.SignatureException;
import com.avail.utility.evaluation.Continuation1;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import static com.avail.compiler.ParsingOperation.*;
import static com.avail.exceptions.AvailErrorCode.E_INCORRECT_TYPE_FOR_BOOLEAN_GROUP;

/**
 * An {@code Optional} is a {@link Sequence} wrapped in guillemets («»), and
 * followed by a question mark (?).  It may not contain {@link Argument}s or
 * subgroups, and since it is not a group it may not contain a {@linkplain
 * StringDescriptor#doubleDagger() double dagger} (‡).
 *
 * <p>At a call site, an optional produces a {@linkplain
 * EnumerationTypeDescriptor#booleanObject() boolean} that indicates whether
 * there was an occurrence of the group.  For example, the message
 * "«very»?good" accepts a single argument: a boolean that is {@linkplain
 * AtomDescriptor#trueObject() true} if the token "very" occurred and
 * {@linkplain AtomDescriptor#falseObject() false} if it did not.</p>
 */
final class Optional
extends Expression
{
	/** The optional {@link Sequence}. */
	private final Sequence sequence;

	/**
	 * Construct a new {@link Optional}.
	 *
	 * @param positionInName
	 *        The position of the group or token in the message name.
	 * @param sequence
	 *        The governed {@linkplain Sequence sequence}.
	 */
	Optional (final int positionInName, final Sequence sequence)
	{
		super(positionInName);
		this.sequence = sequence;
		if (sequence.canBeReordered())
		{
			explicitOrdinal(sequence.explicitOrdinal());
			sequence.explicitOrdinal(-1);
		}
	}

	@Override
	boolean isArgumentOrGroup ()
	{
		return true;
	}

	@Override
	int underscoreCount ()
	{
		assert sequence.underscoreCount() == 0;
		return 0;
	}

	@Override
	boolean isLowerCase ()
	{
		return sequence.isLowerCase();
	}

	@Override
	void extractSectionCheckpointsInto (
		final List<SectionCheckpoint> sectionCheckpoints)
	{
		sequence.extractSectionCheckpointsInto(sectionCheckpoints);
	}

	@Override
	public void checkType (
		final A_Type argumentType,
		final int sectionNumber)
	throws SignatureException
	{
		if (!argumentType.isSubtypeOf(
			EnumerationTypeDescriptor.booleanObject()))
		{
			// The declared type of the subexpression must be a subtype of
			// boolean.
			MessageSplitter.throwSignatureException(
				E_INCORRECT_TYPE_FOR_BOOLEAN_GROUP);
		}
	}

	@Override
	void emitOn (
		final InstructionGenerator generator,
		final A_Type phraseType)
	{
		/* branch to @absent
		 * push the current parse position on the mark stack
		 * ...the sequence's expressions...
		 * check progress and update saved position or abort.
		 * discard the saved parse position from the mark stack.
		 * push literal true
		 * jump to @groupSkip
		 * @absent:
		 * push literal false
		 * @groupSkip:
		 */
		generator.flushDelayed();
		final boolean needsProgressCheck =
			sequence.mightBeEmpty(ListNodeTypeDescriptor.empty());
		final Label $absent = new Label();
		final Label $after = new Label();
		generator.emit(this, BRANCH, $absent);
		generator.emitIf(needsProgressCheck, this, SAVE_PARSE_POSITION);
		assert sequence.argumentsAreReordered != Boolean.TRUE;
		for (final Expression expression : sequence.expressions)
		{
			expression.emitOn(generator, ListNodeTypeDescriptor.empty());
		}
		generator.emitIf(needsProgressCheck, this, ENSURE_PARSE_PROGRESS);
		generator.emitIf(
			needsProgressCheck, this, DISCARD_SAVED_PARSE_POSITION);
		generator.emit(this, PUSH_LITERAL, MessageSplitter.indexForTrue());
		generator.emit(this, JUMP, $after);
		generator.emit($absent);
		generator.emit(this, PUSH_LITERAL, MessageSplitter.indexForFalse());
		generator.emit($after);
	}

	void emitWithSplitOn (
		final InstructionGenerator generator,
		Continuation1<Boolean> generateSplit)
	{
		/* branch to @absent
		 * push the current parse position on the mark stack
		 * ...the sequence's expressions...
		 * check progress and update saved position or abort.
		 * discard the saved parse position from the mark stack.
		 * ...generateSplit(true), which have the same effect as pushing the
		 *    literal true, appending it, and pushing and appending anything
		 *    that the generatorSplit produces...
		 * jump to @groupSkip
		 * @absent:
		 * ...generateSplit(false), which have the same effect as pushing the
		 *    literal false, appending it, and pushing and appending anything
		 *    that the generatorSplit produces...
		 * @groupSkip:
		 */
		generator.flushDelayed();
		final boolean needsProgressCheck =
			sequence.mightBeEmpty(ListNodeTypeDescriptor.empty());
		final Label $absent = new Label();
		final Label $after = new Label();
		generator.emit(this, BRANCH, $absent);
		generator.emitIf(needsProgressCheck, this, SAVE_PARSE_POSITION);
		assert sequence.argumentsAreReordered != Boolean.TRUE;
		for (final Expression expression : sequence.expressions)
		{
			expression.emitOn(generator, ListNodeTypeDescriptor.empty());
		}
		generator.emitIf(needsProgressCheck, this, ENSURE_PARSE_PROGRESS);
		generator.emitIf(
			needsProgressCheck, this, DISCARD_SAVED_PARSE_POSITION);
		generateSplit.value(true);
		generator.flushDelayed();
		generator.emit(this, JUMP, $after);
		generator.emit($absent);
		generateSplit.value(false);
		generator.flushDelayed();
		generator.emit($after);
	}

	@Override
	public String toString ()
	{
		return getClass().getSimpleName() + "(" + sequence + ")";
	}

	@Override
	public void printWithArguments (
		final @Nullable Iterator<AvailObject> argumentProvider,
		final StringBuilder builder,
		final int indent)
	{
		assert argumentProvider != null;
		final A_Phrase literal = argumentProvider.next();
		assert literal.isInstanceOf(
			ParseNodeKind.LITERAL_NODE.mostGeneralType());
		final boolean flag = literal.token().literal().extractBoolean();
		if (flag)
		{
			builder.append("«");
			sequence.printWithArguments(
				Collections.<AvailObject>emptyIterator(), builder, indent);
			builder.append("»?");
		}
	}

	@Override
	boolean shouldBeSeparatedOnLeft ()
	{
		// For now.  Eventually we could find out whether there were even
		// any tokens printed by passing an argument iterator.
		return true;
	}

	@Override
	boolean shouldBeSeparatedOnRight ()
	{
		// For now.  Eventually we could find out whether there were even
		// any tokens printed by passing an argument iterator.
		return true;
	}

	@Override
	boolean mightBeEmpty (final A_Type phraseType)
	{
		// Optional things can be absent.
		return true;
	}
}
