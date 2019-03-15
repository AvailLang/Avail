/*
 * Sequence.java
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
import com.avail.annotations.InnerAccess;
import com.avail.descriptor.A_Phrase;
import com.avail.descriptor.A_Tuple;
import com.avail.descriptor.A_Type;
import com.avail.exceptions.MalformedMessageException;
import com.avail.exceptions.SignatureException;
import com.avail.utility.Pair;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import static com.avail.compiler.ParsingOperation.APPEND_ARGUMENT;
import static com.avail.compiler.ParsingOperation.CONCATENATE;
import static com.avail.compiler.ParsingOperation.PERMUTE_LIST;
import static com.avail.compiler.ParsingOperation.REVERSE_STACK;
import static com.avail.compiler.splitter.MessageSplitter.circledNumberCodePoint;
import static com.avail.compiler.splitter.MessageSplitter.indexForPermutation;
import static com.avail.compiler.splitter.WrapState.NEEDS_TO_PUSH_LIST;
import static com.avail.compiler.splitter.WrapState.PUSHED_LIST;
import static com.avail.compiler.splitter.WrapState.SHOULD_NOT_HAVE_ARGUMENTS;
import static com.avail.compiler.splitter.WrapState.SHOULD_NOT_PUSH_LIST;
import static com.avail.descriptor.ListPhraseTypeDescriptor.emptyListPhraseType;
import static com.avail.descriptor.TupleDescriptor.tupleFromIntegerList;
import static com.avail.exceptions.AvailErrorCode.E_INCONSISTENT_ARGUMENT_REORDERING;
import static com.avail.exceptions.AvailErrorCode.E_INCORRECT_ARGUMENT_TYPE;
import static com.avail.exceptions.AvailErrorCode.E_INCORRECT_NUMBER_OF_ARGUMENTS;
import static com.avail.exceptions.AvailErrorCode.E_INCORRECT_TYPE_FOR_GROUP;
import static java.util.Collections.singletonList;

/**
 * A {@code Sequence} is the juxtaposition of any number of other {@link
 * Expression}s.  It is not itself a repetition, but it can be the left or
 * right half of a {@link Group} (bounded by the double-dagger (‡)).
 */
final class Sequence
extends Expression
{
	private final MessageSplitter messageSplitter;
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

	Sequence (
		final int positionInName,
		final MessageSplitter messageSplitter)
	{
		super(positionInName);
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
				&& argumentsAreReordered == (e.explicitOrdinal() == -1))
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

	private volatile @Nullable List<List<Pair<Expression, Integer>>>
		cachedRunsForCodeSplitting = null;

	/**
	 * Analyze the sequence to find the appropriate ranges within which code
	 * splitting should be performed.  Code splitting allows chains of Optional
	 * expressions to turn into binary trees, with the non-optional that follows
	 * at the leaves.  Since the endpoints are unique, we can postpone pushing
	 * the constant boolean values (that indicate whether on Optional was
	 * present or not along that path) until just before merging control flow.
	 *
	 * <p>Also capture the corresponding indices into the tuple type with each
	 * expression in each run.  A zero indicates that no type is consumed for
	 * that expression.</p>
	 *
	 * @return The runs of expressions within which to perform code splitting,
	 *         expressed as a list of lists of &lt;expression, typeIndex> pairs.
	 */
	private List<List<Pair<Expression, Integer>>> runsForCodeSplitting ()
	{
		if (cachedRunsForCodeSplitting != null)
		{
			return cachedRunsForCodeSplitting;
		}
		final List<List<Pair<Expression, Integer>>> result = new ArrayList<>();
		final List<Pair<Expression, Integer>> currentRun = new ArrayList<>();
		int typeIndex = 0;
		for (final Expression expression : expressions)
		{
			// Put the subexpression into one of the runs.
			if (expression.hasSectionCheckpoints())
			{
				if (!currentRun.isEmpty())
				{
					result.add(new ArrayList<>(currentRun));
					currentRun.clear();
				}
				result.add(
					singletonList(
						new Pair<>(
							expression,
							expression.isArgumentOrGroup() ? ++typeIndex : 0)));
			}
			else
			{
				currentRun.add(
					new Pair<>(
						expression,
						expression.isArgumentOrGroup() ? ++typeIndex : 0));
				if (!(expression instanceof Optional))
				{
					result.add(new ArrayList<>(currentRun));
					currentRun.clear();
				}
			}
		}
		if (!currentRun.isEmpty())
		{
			result.add(currentRun);
		}
		cachedRunsForCodeSplitting = result;
		return result;
	}

	/**
	 * Emit code to cause the given run of &lt;expression, tuple-type-index>
	 * pairs to be emitted, starting at positionInRun.  Note that the arguments
	 * will initially be written in reverse order, but the outermost call of
	 * this method will reverse them.
	 *
	 * @param run
	 *        A list of &lt;Expression, type-index> pairs to process together,
	 *        defining the boundary of code-splitting.
	 * @param positionInRun
	 *        Where in the run to start generating code.  Useful for recursive
	 *        code splitting.  It may be just past the end of the run.
	 * @param generator
	 *        Where to emit instructions.
	 * @param subexpressionsTupleType
	 *        A tuple type containing the expected phrase types for this entire
	 *        sequence.  Indexed by the second()s of the run pairs.
	 */
	@InnerAccess
	private void emitRunOn (
		final List<Pair<Expression, Integer>> run,
		final int positionInRun,
		final InstructionGenerator generator,
		final A_Type subexpressionsTupleType)
	{
		final int runSize = run.size();
		final Pair<Expression, Integer> pair = run.get(positionInRun);
		final Expression expression = pair.first();
		final int typeIndex = pair.second();
		final int realTypeIndex =
			typeIndex != 0 && argumentsAreReordered == Boolean.TRUE
				? permutedArguments.get(typeIndex - 1)
				: typeIndex;
		final A_Type subexpressionType = typeIndex == 0
			? emptyListPhraseType()
			: subexpressionsTupleType.typeAtIndex(realTypeIndex);
		if (positionInRun == runSize - 1)
		{
			// We're on the last element of the run, or it's a singleton run.
			// Either way, just emit it (ending the recursion).
			expression.emitOn(
				subexpressionType, generator, SHOULD_NOT_PUSH_LIST);
		}
		else
		{
			((Optional) expression).emitInRunThen(
				generator,
				() -> emitRunOn(
					run,
					positionInRun + 1,
					generator,
					subexpressionsTupleType));
			if (positionInRun == 0)
			{
				// Do the argument reversal at the outermost recursion.
				final boolean lastElementPushed =
					run.get(runSize - 1).first().isArgumentOrGroup();
				final int permutationSize =
					runSize + (lastElementPushed ? 0 : -1);
				generator.emitIf(
					permutationSize > 1, this, REVERSE_STACK, permutationSize);
			}
		}
	}

	/**
	 * Generate code to parse the sequence.  Use the passed {@link WrapState} to
	 * control whether the arguments/groups should be left on the stack
	 * ({@link WrapState#SHOULD_NOT_PUSH_LIST}), assembled into a list ({@link
	 * WrapState#NEEDS_TO_PUSH_LIST}), or concatenated onto an existing list
	 * ({@link WrapState#PUSHED_LIST}).
	 */
	@Override
	WrapState emitOn (
		final A_Type phraseType,
		final InstructionGenerator generator,
		final WrapState wrapState)
	{
		final A_Type subexpressionsTupleType =
			phraseType.subexpressionsTupleType();
		int argIndex = 0;
		int ungroupedArguments = 0;
		boolean listIsPushed = wrapState == PUSHED_LIST;
		final List<List<Pair<Expression, Integer>>> allRuns =
			runsForCodeSplitting();
		for (final List<Pair<Expression, Integer>> run : allRuns)
		{
			final int runSize = run.size();
			final Expression lastInRun = run.get(runSize - 1).first();
			if (lastInRun.hasSectionCheckpoints())
			{
				assert runSize == 1;
				generator.flushDelayed();
				if (listIsPushed)
				{
					if (ungroupedArguments == 1)
					{
						generator.emit(this, APPEND_ARGUMENT);
					}
					else if (ungroupedArguments > 1)
					{
						generator.emitWrapped(this, ungroupedArguments);
						generator.emit(this, CONCATENATE);
					}
					ungroupedArguments = 0;
				}
				else if (wrapState == NEEDS_TO_PUSH_LIST)
				{
					generator.emitWrapped(this, ungroupedArguments);
					listIsPushed = true;
					ungroupedArguments = 0;
				}
			}
			emitRunOn(run, 0, generator, subexpressionsTupleType);
			final int argsInRun = lastInRun.isArgumentOrGroup()
				? runSize : runSize - 1;
			ungroupedArguments += argsInRun;
			argIndex += argsInRun;
		}
		generator.flushDelayed();
		if (listIsPushed)
		{
			if (ungroupedArguments == 1)
			{
				generator.emit(this, APPEND_ARGUMENT);
			}
			else if (ungroupedArguments > 1)
			{
				generator.emitWrapped(this, ungroupedArguments);
				generator.emit(this, CONCATENATE);
			}
		}
		else if (wrapState == NEEDS_TO_PUSH_LIST)
		{
			generator.emitWrapped(this, ungroupedArguments);
			listIsPushed = true;
		}
		assert listIsPushed
			|| wrapState == SHOULD_NOT_PUSH_LIST
			|| wrapState == SHOULD_NOT_HAVE_ARGUMENTS;
		assert arguments.size() == argIndex;
		assert subexpressionsTupleType.sizeRange().lowerBound().equalsInt(
			argIndex);
		assert subexpressionsTupleType.sizeRange().upperBound().equalsInt(
			argIndex);
		if (argumentsAreReordered == Boolean.TRUE)
		{
			assert listIsPushed;
			final A_Tuple permutationTuple =
				tupleFromIntegerList(permutedArguments);
			final int permutationIndex = indexForPermutation(permutationTuple);
			// This sequence was already collected into a list phrase as the
			// arguments/groups were parsed.  Permute the list.
			generator.flushDelayed();
			generator.emit(this, PERMUTE_LIST, permutationIndex);
		}
		return wrapState == NEEDS_TO_PUSH_LIST ? PUSHED_LIST : wrapState;
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
			builder.append(e);
			if (e.canBeReordered() && e.explicitOrdinal() != -1)
			{
				builder.appendCodePoint(
					circledNumberCodePoint(e.explicitOrdinal()));
			}
			first = false;
		}
		builder.append(')');
		return builder.toString();
	}

	@Override
	public void printWithArguments (
		final @Nullable Iterator<? extends A_Phrase> argumentProvider,
		final StringBuilder builder,
		final int indent)
	{
		assert argumentProvider != null;
		boolean needsSpace = false;
		for (final Expression expression : expressions)
		{
			if (needsSpace && expression.shouldBeSeparatedOnLeft())
			{
				builder.append(' ');
			}
			final int oldLength = builder.length();
			expression.printWithArguments(
				argumentProvider, builder, indent);
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
	void checkForConsistentOrdinals ()
		throws MalformedMessageException
	{
		if (argumentsAreReordered != Boolean.TRUE)
		{
			return;
		}
		final List<Integer> usedOrdinalsList = new ArrayList<>();
		for (final Expression e : expressions)
		{
			if (e.canBeReordered())
			{
				usedOrdinalsList.add(e.explicitOrdinal());
			}
		}
		final int size = usedOrdinalsList.size();
		final List<Integer> sortedOrdinalsList =
			new ArrayList<>(usedOrdinalsList);
		Collections.sort(sortedOrdinalsList);
		final Set<Integer> usedOrdinalsSet = new HashSet<>(usedOrdinalsList);
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
				+ "in ascending order (got " + usedOrdinalsList + ')');
		}
		assert permutedArguments.isEmpty();
		permutedArguments.addAll(usedOrdinalsList);
	}

	@Override
	boolean mightBeEmpty (
		final A_Type phraseType)
	{
		final A_Type subexpressionsTupleType =
			phraseType.subexpressionsTupleType();
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
					subexpressionsTupleType.typeAtIndex(realTypeIndex);
				if (!expression.mightBeEmpty(entryType))
				{
					return false;
				}
			}
			else
			{
				if (!expression.mightBeEmpty(emptyListPhraseType()))
				{
					return false;
				}
			}
		}
		return true;
	}
}
