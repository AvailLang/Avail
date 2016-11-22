/**
 * Expression.java
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
import com.avail.descriptor.A_Type;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.ParseNodeDescriptor;
import com.avail.descriptor.TupleTypeDescriptor;
import com.avail.exceptions.SignatureException;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * An {@code Expression} represents a structural view of part of the
 * message name.
 */
abstract class Expression
{
	/**
	 * The one-based explicit numbering for this argument.  To specify this
	 * in a message name, a circled number (0-50) immediately follows the
	 * underscore or ellipsis (or the close guillemet for permuting {@link
	 * Group}s).
	 */
	private int explicitOrdinal = -1;

	/**
	 * Answer whether or not this an {@linkplain Argument argument} or
	 * {@linkplain Group group}.
	 *
	 * @return {@code true} if and only if this is an argument or group,
	 *         {@code false} otherwise.
	 */
	boolean isArgumentOrGroup ()
	{
		return false;
	}

	/**
	 * Answer whether or not this a {@linkplain Group group}.
	 *
	 * @return {@code true} if and only if this is an argument or group,
	 *         {@code false} otherwise.
	 */
	boolean isGroup ()
	{
		return false;
	}

	/**
	 * If this isn't even a {@link Group} then it doesn't need
	 * double-wrapping.  Override in Group.
	 *
	 * @return {@code true} if this is a group which will generate a tuple
	 *         of fixed-length tuples, {@code false} if this group will
	 *         generate a tuple of individual arguments or subgroups (or if
	 *         this isn't a group).
	 */
	boolean needsDoubleWrapping ()
	{
		return false;
	}

	/**
	 * Answer the number of non-backquoted underscores/ellipses that occur
	 * in this section of the method name.
	 *
	 * @return The number of non-backquoted underscores/ellipses in the
	 *         receiver.
	 */
	int underscoreCount ()
	{
		return 0;
	}

	/**
	 * Extract all {@link SectionCheckpoint}s into the specified list.
	 *
	 * @param sectionCheckpoints
	 *        Where to add section checkpoints found within this expression.
	 */
	void extractSectionCheckpointsInto (
		final List<SectionCheckpoint> sectionCheckpoints)
	{
		// Do nothing by default.
	}

	/**
	 * Answer whether this expression recursively contains any section
	 * checkpoints.
	 *
	 * @return True if this expression recursively contains any section
	 *         checkpoints, otherwise false.
	 */
	boolean hasSectionCheckpoints ()
	{
		final List<SectionCheckpoint> any = new ArrayList<>();
		extractSectionCheckpointsInto(any);
		return !any.isEmpty();
	}

	/**
	 * Are all keywords of the expression comprised exclusively of lower
	 * case characters?
	 *
	 * @return {@code true} if all keywords of the expression are comprised
	 *         exclusively of lower case characters, {@code false}
	 *         otherwise.
	 */
	boolean isLowerCase ()
	{
		return true;
	}

	/**
	 * Check that the given type signature is appropriate for this message
	 * expression. If not, throw a {@link SignatureException}.
	 *
	 * <p>This is also called recursively on subcomponents, and it checks
	 * that {@linkplain Argument group arguments} have the correct structure
	 * for what will be parsed. The method may reject parses based on the
	 * number of repetitions of a {@linkplain Group group} at a call site,
	 * but not the number of arguments actually delivered by each
	 * repetition. For example, the message "«_:_‡,»" can limit the number
	 * of _:_ pairs to at most 5 by declaring the tuple type's size to be
	 * [5..5]. However, the message "«_:_‡[_]»" will always produce a tuple
	 * of 3-tuples followed by a 2-tuple (if any elements at all occur).
	 * Attempting to add a method implementation for this message that only
	 * accepted a tuple of 7-tuples would be inappropriate (and
	 * ineffective). Instead, it should be required to accept a tuple whose
	 * size is in the range [2..3].</p>
	 *
	 * <p>Note that the outermost (pseudo)group represents the entire
	 * message, so the caller should synthesize a fixed-length {@linkplain
	 * TupleTypeDescriptor tuple type} for the outermost check.</p>
	 *
	 * @param argumentType
	 *        A {@linkplain TupleTypeDescriptor tuple type} describing the
	 *        types of arguments that a method being added will accept.
	 * @param sectionNumber
	 *        Which {@linkplain SectionCheckpoint} section marker this list
	 *        of argument types are being validated against.  To validate
	 *        the final method or macro body rather than a prefix function,
	 *        use any value greater than the {@linkplain
	 *        MessageSplitter#numberOfSectionCheckpoints}.
	 * @throws SignatureException
	 *        If the argument type is inappropriate.
	 */
	abstract public void checkType (
		final A_Type argumentType,
		final int sectionNumber)
	throws SignatureException;

	/**
	 * Write instructions for parsing me to the given list.
	 *
	 * @param generator
	 *        The {@link InstructionGenerator} that accumulates the parsing
	 *        instructions.
	 * @param phraseType
	 *        The type of the phrase being parsed at and inside this parse
	 *        point.  Note that when this is for a list phrase type, it's
	 *        used for unrolling leading iterations of loops up to the end
	 *        of the variation (typically just past the list phrase's tuple
	 *        type's {@link A_Type#typeTuple()}).
	 */
	abstract void emitOn (
		final InstructionGenerator generator,
		final A_Type phraseType);

	@Override
	public String toString ()
	{
		return getClass().getSimpleName();
	}

	/**
	 * Pretty-print this part of the message, using the provided argument
	 * {@linkplain ParseNodeDescriptor nodes}.
	 *
	 * @param arguments
	 *        An {@link Iterator} that provides parse nodes to fill in for
	 *        arguments and subgroups.
	 * @param builder
	 *        The {@link StringBuilder} on which to print.
	 * @param indent
	 *        The indentation level.
	 */
	abstract public void printWithArguments (
		@Nullable Iterator<AvailObject> arguments,
		StringBuilder builder,
		int indent);

	/**
	 * Answer whether the pretty-printed representation of this {@link
	 * Expression} should be separated from its left sibling with a space.
	 *
	 * @return Whether this expression should be preceded by a space if it
	 *         has a left sibling.
	 */
	abstract boolean shouldBeSeparatedOnLeft ();

	/**
	 * Answer whether the pretty-printed representation of this {@link
	 * Expression} should be separated from its right sibling with a space.
	 *
	 * @return Whether this expression should be followed by a space if it
	 *         has a right sibling.
	 */
	abstract boolean shouldBeSeparatedOnRight ();

	/**
	 * Answer whether reordering with respect to siblings is applicable to
	 * this kind of expression.
	 *
	 * @return Whether the expression can in theory be reordered.
	 */
	final boolean canBeReordered ()
	{
		return isArgumentOrGroup();
	}

	/**
	 * Answer my explicitOrdinal, which indicates how to reorder me with my
	 * siblings.  This may only be requested for types of {@link Expression}
	 * that {@link #canBeReordered()}.
	 *
	 * @return My explicitOrdinal or -1.
	 */
	final int explicitOrdinal ()
	{
		assert canBeReordered();
		return explicitOrdinal;
	}

	/**
	 * Set my explicitOrdinal, which indicates how to reorder me with my
	 * siblings.  This may only be set for types of {@link Expression}
	 * that {@link #canBeReordered()}.
	 *
	 * @param ordinal My explicitOrdinal or -1.
	 */
	final void explicitOrdinal (final int ordinal)
	{
		assert canBeReordered();
		explicitOrdinal = ordinal;
	}

	/**
	 * Answer whether this expression might match an empty sequence of tokens.
	 *
	 * @return Whether what this expression matches could be empty.
	 * @param phraseType
	 */
	boolean mightBeEmpty (
		final A_Type phraseType)
	{
		// Most expressions can't match an empty sequence of tokens.
		return false;
	};
}
