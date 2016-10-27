/**
 * Sequence.java
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
import com.avail.annotations.InnerAccess;
import com.avail.descriptor.A_Tuple;
import com.avail.descriptor.A_Type;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.ListNodeTypeDescriptor;
import com.avail.descriptor.TupleDescriptor;
import com.avail.descriptor.TupleTypeDescriptor;
import com.avail.dispatch.LookupTree;
import com.avail.exceptions.MalformedMessageException;
import com.avail.exceptions.SignatureException;
import com.avail.utility.evaluation.Transformer1;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import static com.avail.compiler.ParsingOperation.APPEND_ARGUMENT;
import static com.avail.compiler.ParsingOperation.NEW_LIST;
import static com.avail.compiler.ParsingOperation.PERMUTE_LIST;
import static com.avail.descriptor.ParseNodeTypeDescriptor.ParseNodeKind.LIST_NODE;
import static com.avail.descriptor.ParseNodeTypeDescriptor.ParseNodeKind.PARSE_NODE;
import static com.avail.exceptions.AvailErrorCode.*;

/**
 * A {@link Sequence} is the juxtaposition of any number of other {@link
 * Expression}s.  It is not itself a repetition, but it can be the left or
 * right half of a {@link Group} (bounded by the double-dagger (‡)).
 */
final class Sequence
extends Expression
{
	private MessageSplitter messageSplitter;
	/**
	 * The sequence of expressions that I comprise.
	 */
	final List<Expression> expressions = new ArrayList<>();

	/**
	 * Which of my {@link #expressions} is an argument, ellipsis, or group?
	 * These are in the order they occur in the {@code expressions} list.
	 */
	final List<Expression> arguments = new ArrayList<>();

	/**
	 * My one-based permutation that takes argument expressions from the
	 * order in which they occur to the order in which they are bound to
	 * arguments at a call site.
	 */
	final List<Integer> permutedArguments = new ArrayList<>();

	/**
	 * A three-state indicator of whether my argument components should be
	 * reordered.  If null, a decision has not yet been made, either during
	 * parsing (because an argument/group has not yet been encountered), or
	 * because this {@code Sequence} has no arguments or subgroups that act
	 * as arguments.  If {@link Boolean#TRUE}, then all argument positions
	 * so far have specified reordering (by using circled numbers), and if
	 * {@link Boolean#FALSE}, then no arguments so far have specified
	 * reordering.
	 */
	@Nullable Boolean argumentsAreReordered = null;

	public Sequence (final MessageSplitter messageSplitter)
	{
		this.messageSplitter = messageSplitter;
	}

	/**
	 * Add an {@linkplain Expression expression} to the {@link Sequence}.
	 *
	 * @param e
	 *        The expression to add.
	 * @throws MalformedMessageException
	 *         If the absence or presence of argument numbering would be
	 *         inconsistent within this {@link Sequence}.
	 */
	void addExpression (final Expression e)
		throws MalformedMessageException
	{
		expressions.add(e);
		if (e.isArgumentOrGroup())
		{
			arguments.add(e);
		}
		if (e.canBeReordered())
		{
			if (argumentsAreReordered != null
				&& argumentsAreReordered != (e.explicitOrdinal() != -1))
			{
				messageSplitter.throwMalformedMessageException(
					E_INCONSISTENT_ARGUMENT_REORDERING,
					"The sequence of subexpressions before or after a "
					+ "double-dagger (‡) in a group must have either all "
					+ "or none of its arguments/subgroups numbered for "
					+ "reordering");
			}
			argumentsAreReordered = e.explicitOrdinal() != -1;
		}
	}

	@Override
	boolean isArgumentOrGroup ()
	{
		return false;
	}

	@Override
	boolean isGroup ()
	{
		return false;
	}

	@Override
	int underscoreCount ()
	{
		int count = 0;
		for (final Expression expr : expressions)
		{
			count += expr.underscoreCount();
		}
		return count;
	}

	@Override
	boolean isLowerCase ()
	{
		for (final Expression expression : expressions)
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
		for (final Expression expression : expressions)
		{
			expression.extractSectionCheckpointsInto(sectionCheckpoints);
		}
	}

	/**
	 * Check if the given type is suitable for holding values generated by
	 * this sequence.
	 */
	@Override
	public void checkType (
		final A_Type argumentType,
		final int sectionNumber)
	throws SignatureException
	{
		// Always expect a tuple of solutions here.
		if (argumentType.isBottom())
		{
			// Method argument type should not be bottom.
			MessageSplitter.throwSignatureException(E_INCORRECT_ARGUMENT_TYPE);
		}

		if (!argumentType.isTupleType())
		{
			// The sequence produces a tuple.
			MessageSplitter.throwSignatureException(E_INCORRECT_TYPE_FOR_GROUP);
		}

		// Make sure the tuple of argument types are suitable for the
		// argument positions that I comprise.  Take the argument reordering
		// permutation into account if present.
		final int expected = arguments.size();
		final A_Type sizes = argumentType.sizeRange();
		if (!sizes.lowerBound().equalsInt(expected)
			|| !sizes.upperBound().equalsInt(expected))
		{
			MessageSplitter.throwSignatureException(
				this == messageSplitter.rootSequence
					? E_INCORRECT_NUMBER_OF_ARGUMENTS
					: E_INCORRECT_TYPE_FOR_GROUP);
		}
		for (int i = 1; i <= arguments.size(); i++)
		{
			final Expression argumentOrGroup =
				argumentsAreReordered == Boolean.TRUE
					? arguments.get(permutedArguments.get(i - 1) - 1)
					: arguments.get(i - 1);
			final A_Type providedType = argumentType.typeAtIndex(i);
			assert !providedType.isBottom();
			argumentOrGroup.checkType(providedType, sectionNumber);
		}
	}

	@Override
	void emitOn (
		final InstructionGenerator generator,
		final A_Type phraseType)
	{
		/*
		 * Generate code to parse the sequence.  After parsing, the stack
		 * contains a new list of parsed expressions for all arguments,
		 * ellipses, and subgroups that were encountered.
		 */
		generator.emit(this, NEW_LIST);
		emitWithoutInitialNewListPushOn(generator, phraseType);
	}

	/**
	 * Emit parsing instructions that assume that there has already been an
	 * empty list pushed, onto which to accumulate arguments.
	 *
	 * @param generator
	 *        The instruction generator with which to emit.
	 * @param phraseType
	 *        The {@link A_Type phrase type} for a definition's signature.
	 */
	@InnerAccess
	void emitWithoutInitialNewListPushOn (
		final InstructionGenerator generator,
		final A_Type phraseType)
	{
		/* After parsing, the list that's already on the stack will contain
		 * all arguments, ellipses, and subgroups that were encountered.
		 */
		assert phraseType.isSubtypeOf(PARSE_NODE.mostGeneralType());
		final A_Type tupleType;
		if (phraseType.isSubtypeOf(LIST_NODE.mostGeneralType()))
		{
			tupleType = phraseType.subexpressionsTupleType();
		}
		else
		{
			tupleType = TupleTypeDescriptor.mappingElementTypes(
				phraseType.expressionType(),
				new Transformer1<A_Type, A_Type>()
				{
					@Override
					public A_Type value (@Nullable final A_Type arg)
					{
						assert arg != null;
						return PARSE_NODE.create(arg);
					}
				});
		}
		int index = 0;
		for (final Expression expression : expressions)
		{
			if (expression.isArgumentOrGroup())
			{
				index++;
				final int realTypeIndex =
					argumentsAreReordered == Boolean.TRUE
						? permutedArguments.get(index - 1)
						: index;
				final A_Type entryType =
					tupleType.typeAtIndex(realTypeIndex);
				expression.emitOn(generator, entryType);
				generator.emit(this, APPEND_ARGUMENT);
			}
			else
			{
				expression.emitOn(
					generator, ListNodeTypeDescriptor.empty());
			}
		}
		assert tupleType.sizeRange().lowerBound().equalsInt(index);
		assert tupleType.sizeRange().upperBound().equalsInt(index);
		if (argumentsAreReordered == Boolean.TRUE)
		{
			final A_Tuple permutationTuple =
				TupleDescriptor.fromIntegerList(permutedArguments);
			final int permutationIndex =
				LookupTree.indexForPermutation(permutationTuple);
			// This sequence was already collected into a list node as the
			// arguments/groups were parsed.  Permute the list.
			generator.emit(this, PERMUTE_LIST, permutationIndex);
		}
	}

	@Override
	public String toString ()
	{
		final StringBuilder builder = new StringBuilder();
		builder.append("Sequence(");
		boolean first = true;
		for (final Expression e : expressions)
		{
			if (!first)
			{
				builder.append(", ");
			}
			builder.append(e.toString());
			if (e.canBeReordered() && e.explicitOrdinal() != -1)
			{
				builder.appendCodePoint(
					MessageSplitter.circledNumberCodePoints[e.explicitOrdinal()]);
			}
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
		assert argumentProvider != null;
		boolean needsSpace = false;
		for (final Expression expression : expressions)
		{
			if (needsSpace && expression.shouldBeSeparatedOnLeft())
			{
				builder.append(" ");
			}
			final int oldLength = builder.length();
			expression.printWithArguments(
				argumentProvider,
				builder,
				indent);
			needsSpace = expression.shouldBeSeparatedOnRight()
				&& builder.length() != oldLength;
		}
		assert !argumentProvider.hasNext();
	}

	@Override
	boolean shouldBeSeparatedOnLeft ()
	{
		return !expressions.isEmpty()
			&& expressions.get(0).shouldBeSeparatedOnLeft();
	}

	@Override
	boolean shouldBeSeparatedOnRight ()
	{
		return !expressions.isEmpty()
			&& expressions.get(expressions.size() - 1)
				.shouldBeSeparatedOnRight();
	}

	/**
	 * Check that if ordinals were specified for my N argument positions,
	 * that they are all present and constitute a permutation of [1..N].
	 * If not, throw a {@link MalformedMessageException}.
	 *
	 * @throws MalformedMessageException
	 *         If the arguments have reordering numerals (circled numbers),
	 *         but they don't form a non-trivial permutation of [1..N].
	 */
	public void checkForConsistentOrdinals ()
		throws MalformedMessageException
	{
		if (argumentsAreReordered == Boolean.TRUE)
		{
			checkForConsistentOrdinals(expressions);
		}
	}

	/**
	 * Check whether the provided list of expressions has specified a
	 * reordering that is a non-trivial permutation of [1..N].
	 *
	 * @param subexpressions
	 *        The expressions, N of which are argument subexpressions, which
	 *        we check are distinct values in [1..N] and not all ascending.
	 * @throws MalformedMessageException
	 *         If the reordering numbers of the argument subexpressions are
	 *         not a non-trivial permutation of [1..N].
	 */
	private void checkForConsistentOrdinals (
			final List<? extends Expression> subexpressions)
		throws MalformedMessageException
	{
		final List<Integer> usedOrdinalsList = new ArrayList<>();
		for (final Expression e : subexpressions)
		{
			if (e.canBeReordered())
			{
				usedOrdinalsList.add(e.explicitOrdinal());
			}
		}
		final int size = usedOrdinalsList.size();
		final Set<Integer> usedOrdinalsSet =
			new HashSet<>(usedOrdinalsList);
		final List<Integer> sortedOrdinalsList =
			new ArrayList<>(usedOrdinalsList);
		Collections.sort(sortedOrdinalsList);
		if (usedOrdinalsSet.size() < usedOrdinalsList.size()
			|| sortedOrdinalsList.get(0) != 1
			|| sortedOrdinalsList.get(size - 1) != size
			|| usedOrdinalsList.equals(sortedOrdinalsList))
		{
			// There may have been a duplicate, a lowest value other
			// than 1, a highest value other than the number of values,
			// or the permutation might be the identity permutation (not
			// allowed).  Note that if one of the arguments somehow
			// still had an ordinal of -1 then it will trigger (at
			// least) the lowest value condition.
			messageSplitter.throwMalformedMessageException(
				E_INCONSISTENT_ARGUMENT_REORDERING,
				"The circled numbers for this clause must range from 1 "
				+ "to the number of arguments/groups, but must not be "
				+ "in ascending order (got " + usedOrdinalsList + ")");
		}
		assert permutedArguments.isEmpty();
		permutedArguments.addAll(usedOrdinalsList);
	}
}
