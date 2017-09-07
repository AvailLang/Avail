/**
 * Group.java
 * Copyright © 1993-2017, The Avail Foundation, LLC.
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
import com.avail.compiler.splitter.MessageSplitter.Metacharacter;
import com.avail.descriptor.*;
import com.avail.dispatch.LookupTree;
import com.avail.exceptions.SignatureException;
import javax.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import static com.avail.compiler.ParsingOperation.*;
import static com.avail.compiler.splitter.WrapState.*;
import static com.avail.descriptor.ParseNodeTypeDescriptor.ParseNodeKind.EXPRESSION_NODE;
import static com.avail.descriptor.ParseNodeTypeDescriptor.ParseNodeKind.LIST_NODE;
import static com.avail.exceptions.AvailErrorCode.E_INCORRECT_ARGUMENT_TYPE;
import static com.avail.exceptions.AvailErrorCode.E_INCORRECT_TYPE_FOR_COMPLEX_GROUP;
import static com.avail.exceptions.AvailErrorCode.E_INCORRECT_TYPE_FOR_GROUP;

/**
 * A {@linkplain Group} is delimited by the {@linkplain
 * Metacharacter#OPEN_GUILLEMET open guillemet} («) and {@linkplain
 * Metacharacter#CLOSE_GUILLEMET close guillemet} (») characters, and
 * may contain subgroups and an occurrence of a {@linkplain
 * Metacharacter#DOUBLE_DAGGER double dagger} (‡). If no double dagger
 * or subgroup is present, the sequence of message parts between the
 * guillemets are allowed to occur zero or more times at a call site
 * (i.e., a send of this message). When the number of {@linkplain
 * Metacharacter#UNDERSCORE underscores} (_) and {@linkplain
 * Metacharacter#ELLIPSIS ellipses} (…) plus the number of subgroups is
 * exactly one, the argument (or subgroup) values are assembled into a
 * {@linkplain TupleDescriptor tuple}. Otherwise the leaf arguments and/or
 * subgroups are assembled into a tuple of fixed-sized tuples, each
 * containing one entry for each argument or subgroup.
 *
 * <p>When a double dagger occurs in a group, the parts to the left of the
 * double dagger can occur zero or more times, but separated by the parts to
 * the right. For example, "«_‡,»" is how to specify a comma-separated tuple
 * of arguments. This pattern contains a single underscore and no subgroups,
 * so parsing "1,2,3" would simply produce the tuple <1,2,3>. The pattern
 * "«_=_;»" will parse "1=2;3=4;5=6;" into <<1,2>,<3,4>,<5,6>> because it
 * has two underscores.</p>
 *
 * <p>The message "«A_‡x_»" parses zero or more occurrences in the text of
 * the keyword "A" followed by an argument, separated by the keyword "x" and
 * an argument.  "A 1 x 2 A 3 x 4 A 5" is such an expression (and "A 1 x 2"
 * is not). In this case, the arguments will be grouped in such a way that
 * the final element of the tuple, if any, is missing the post-double dagger
 * elements: <<1,2>,<3,4>,<5>>.</p>
 */
final class Group
extends Expression
{
	/**
	 * Whether a {@linkplain Metacharacter#DOUBLE_DAGGER double dagger}
	 * (‡) has been encountered in the tokens for this group.
	 */
	final boolean hasDagger;

	/**
	 * The {@link Sequence} of {@link Expression}s that appeared before the
	 * {@linkplain Metacharacter#DOUBLE_DAGGER double dagger}, or in the
	 * entire subexpression if no double dagger is present.
	 */
	final Sequence beforeDagger;

	/**
	 * The {@link Sequence} of {@link Expression}s that appear after the
	 * {@linkplain Metacharacter#DOUBLE_DAGGER double dagger}, or an
	 * empty sequence if no double dagger is present.
	 */
	final Sequence afterDagger;

	/**
	 * The maximum number of occurrences accepted for this group.
	 */
	private int maximumCardinality = Integer.MAX_VALUE;

	/**
	 * Construct a new {@link Group} having a double-dagger (‡).
	 *
	 * @param positionInName
	 *        The position of the group in the message name.
	 * @param beforeDagger
	 *        The {@link Sequence} before the double-dagger.
	 * @param afterDagger
	 *        The {@link Sequence} after the double-dagger.
	 */
	public Group (
		final int positionInName,
		final Sequence beforeDagger,
		final Sequence afterDagger)
	{
		super(positionInName);
		this.beforeDagger = beforeDagger;
		this.hasDagger = true;
		this.afterDagger = afterDagger;
	}

	/**
	 * Construct a new {@link Group} that does not contain a double-dagger
	 * (‡).
	 *
	 * @param positionInName
	 *        The position of the group in the message name.
	 * @param beforeDagger
	 *        The {@link Sequence} of {@link Expression}s in the group.
	 */
	public Group (
		final int positionInName,
		final MessageSplitter messageSplitter,
		final Sequence beforeDagger)
	{
		super(positionInName);
		this.beforeDagger = beforeDagger;
		this.hasDagger = false;
		this.afterDagger = new Sequence(positionInName + 1, messageSplitter);
	}

	@Override
	boolean isArgumentOrGroup ()
	{
		return true;
	}

	@Override
	boolean isGroup ()
	{
		return true;
	}

	@Override
	int underscoreCount ()
	{
		return beforeDagger.underscoreCount()
			+ afterDagger.underscoreCount();
	}

	@Override
	boolean isLowerCase ()
	{
		return beforeDagger.isLowerCase() && afterDagger.isLowerCase();
	}

	/**
	 * Set the maximum number of times this group may occur.
	 *
	 * @param max
	 *        My new maximum cardinality, or {@link Integer#MAX_VALUE} to
	 *        stand for {@link InfinityDescriptor#positiveInfinity()}.
	 */
	void maximumCardinality (final int max)
	{
		maximumCardinality = max;
	}

	/**
	 * Determine if this group should generate a {@linkplain TupleDescriptor
	 * tuple} of plain arguments or a tuple of fixed-length tuples of plain
	 * arguments.
	 *
	 * @return {@code true} if this group will generate a tuple of
	 *         fixed-length tuples, {@code false} if this group will
	 *         generate a tuple of individual arguments or subgroups.
	 */
	@Override
	boolean needsDoubleWrapping ()
	{
		return beforeDagger.arguments.size() != 1
			|| afterDagger.arguments.size() != 0;
	}

	@Override
	void extractSectionCheckpointsInto (
		final List<SectionCheckpoint> sectionCheckpoints)
	{
		beforeDagger.extractSectionCheckpointsInto(sectionCheckpoints);
		afterDagger.extractSectionCheckpointsInto(sectionCheckpoints);
	}

	/**
	 * Check if the given type is suitable for holding values generated by
	 * this group.
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
			// The group produces a tuple.
			MessageSplitter.throwSignatureException(E_INCORRECT_TYPE_FOR_GROUP);
		}

		final A_Type requiredRange = IntegerRangeTypeDescriptor.integerRangeType(
			IntegerDescriptor.zero(),
			true,
			maximumCardinality == Integer.MAX_VALUE
				? InfinityDescriptor.positiveInfinity()
				: IntegerDescriptor.fromInt(maximumCardinality + 1),
			false);

		if (!argumentType.sizeRange().isSubtypeOf(requiredRange))
		{
			// The method's parameter should have a cardinality that's a
			// subtype of what the message name requires.
			MessageSplitter.throwSignatureException(E_INCORRECT_TYPE_FOR_GROUP);
		}

		if (needsDoubleWrapping())
		{
			// Expect a tuple of tuples of values, where the inner tuple
			// size ranges from the number of arguments left of the dagger
			// up to that plus the number of arguments right of the dagger.
			assert argumentType.isTupleType();
			final int argsBeforeDagger = beforeDagger.arguments.size();
			final int argsAfterDagger = afterDagger.arguments.size();
			final A_Number expectedLower = IntegerDescriptor.fromInt(
				argsBeforeDagger);
			final A_Number expectedUpper = IntegerDescriptor.fromInt(
				argsBeforeDagger + argsAfterDagger);
			final A_Tuple typeTuple = argumentType.typeTuple();
			final int limit = typeTuple.tupleSize() + 1;
			for (int i = 1; i <= limit; i++)
			{
				final A_Type solutionType = argumentType.typeAtIndex(i);
				if (solutionType.isBottom())
				{
					// It was the empty tuple type.
					break;
				}
				if (!solutionType.isTupleType())
				{
					// The argument should be a tuple of tuples.
					MessageSplitter.throwSignatureException(
						E_INCORRECT_TYPE_FOR_GROUP);
				}
				// Check that the solution that will reside at the current
				// index accepts either a full group or a group up to the
				// dagger.
				final A_Type solutionTypeSizes = solutionType.sizeRange();
				final A_Number lower = solutionTypeSizes.lowerBound();
				final A_Number upper = solutionTypeSizes.upperBound();
				if (!lower.equals(expectedLower)
					|| !upper.equals(expectedUpper))
				{
					// This complex group should have elements whose types
					// are tuples restricted to have sizes ranging from the
					// number of argument subexpressions before the double
					// dagger up to the total number of argument
					// subexpressions in this group.
					MessageSplitter.throwSignatureException(
						E_INCORRECT_TYPE_FOR_COMPLEX_GROUP);
				}
				int j = 1;
				for (final Expression e : beforeDagger.arguments)
				{
					e.checkType(
						solutionType.typeAtIndex(j),
						sectionNumber);
					j++;
				}
				for (final Expression e : afterDagger.arguments)
				{
					e.checkType(
						solutionType.typeAtIndex(j),
						sectionNumber);
					j++;
				}
			}
		}
	}

	@Override
	WrapState emitOn (
		final A_Type phraseType,
		final InstructionGenerator generator,
		final WrapState wrapState)
	{
		final A_Type subexpressionsTupleType =
			phraseType.subexpressionsTupleType();
		final A_Type sizeRange = subexpressionsTupleType.sizeRange();
		final A_Number minInteger = sizeRange.lowerBound();
		final int minSize = minInteger.isInt()
			? minInteger.extractInt() : Integer.MAX_VALUE;
		final A_Number maxInteger = sizeRange.upperBound();
		final int maxSize = maxInteger.isInt()
			? maxInteger.extractInt() : Integer.MAX_VALUE;
		final int endOfVariation =
			Math.min(
				Math.max(
					subexpressionsTupleType.typeTuple().tupleSize() + 2,
					Math.min(minSize, 3)),
				maxSize);
		final boolean needsProgressCheck =
			beforeDagger.mightBeEmpty(phraseType);
		generator.flushDelayed();
		if (maxSize == 0)
		{
			// The type signature requires an empty list, so that's what we get.
			generator.emit(this, EMPTY_LIST);
		}
		else if (!needsDoubleWrapping())
		{
			/* Special case -- one argument case produces a list of
			 * expressions rather than a list of fixed-length lists of
			 * expressions.  The case of maxSize = 0 was already handled.
			 * The generated instructions should look like:
			 *
			 * push empty list of solutions (emitted above)
			 * branch to $skip (if minSize = 0)
			 * push current parse position on the mark stack
			 * A repetition for each N=1..endOfVariation-1:
			 *     ...Stuff before dagger, appending sole argument.
			 *     branch to $exit (if N ≥ minSize)
			 *     ...Stuff after dagger, nothing if dagger is omitted.
			 *     ...Must not contain an argument or subgroup.
			 *     check progress and update saved position, or abort.
			 * And a final loop:
			 *     $loopStart:
			 *     ...Stuff before dagger, appending sole argument.
			 *     if (endOfVariation < maxSize) then:
			 *         EITHER branch to $exit (if endOfVariation ≥ minSize)
			 *         OR to $exitCheckMin (if endOfVariation < minSize)
			 *         check that the size is still < maxSize.
			 *         ...Stuff after dagger, nothing if dagger is omitted.
			 *         ...Must not contain an argument or subgroup.
			 *         check progress and update saved position, or abort.
			 *         jump to $loopStart.
			 *         if (endOfVariation < minSize) then:
			 *             $exitCheckMin:
			 *             check at least minSize.
			 * $exit:
			 * check progress and update saved position, or abort.
			 * discard the saved position from the mark stack.
			 * $skip:
			 */
			generator.partialListsCount++;
			final Label $skip = new Label();
			final Label $exit = new Label();
			final Label $exitCheckMin = new Label();
			final Label $loopStart = new Label();
			assert beforeDagger.arguments.size() == 1;
			assert afterDagger.arguments.size() == 0;
			boolean hasWrapped = false;
			if (minSize == 0)
			{
				// If size zero is valid, branch to the special $skip label that
				// avoids the progress check.  The case maxSize==0 was already
				// handled above.
				assert maxSize > 0;
				generator.emit(this, EMPTY_LIST);
				hasWrapped = true;
				generator.emit(this, BRANCH, $skip);
			}
			if (!hasWrapped && beforeDagger.hasSectionCheckpoints())
			{
				generator.emit(this, EMPTY_LIST);
				hasWrapped = true;
			}
			generator.emitIf(needsProgressCheck, this, SAVE_PARSE_POSITION);
			for (int index = 1; index < endOfVariation; index++)
			{
				final A_Type innerPhraseType =
					subexpressionsTupleType.typeAtIndex(index);
				final A_Type singularListType =
					ListNodeTypeDescriptor.createListNodeType(
						LIST_NODE,
						TupleTypeDescriptor.tupleTypeForTypes(
							innerPhraseType.expressionType()),
						TupleTypeDescriptor.tupleTypeForTypes(innerPhraseType));
				beforeDagger.emitOn(
					singularListType,
					generator,
					hasWrapped ? PUSHED_LIST : SHOULD_NOT_PUSH_LIST);
				if (index >= minSize)
				{
					generator.flushDelayed();
					if (!hasWrapped && index == minSize)
					{
						generator.emitWrapped(this, index);
						hasWrapped = true;
					}
					generator.emit(this, BRANCH, $exit);
				}
				if (!hasWrapped
					&& index == 1
					&& afterDagger.hasSectionCheckpoints())
				{
					generator.flushDelayed();
					generator.emitWrapped(this, 1);
					hasWrapped = true;
				}
				afterDagger.emitOn(
					ListNodeTypeDescriptor.empty(), generator, PUSHED_LIST);
				generator.emitIf(
					needsProgressCheck, this, ENSURE_PARSE_PROGRESS);
			}
			// The homogenous part of the tuple, one or more iterations.
			generator.flushDelayed();
			if (!hasWrapped)
			{
				generator.emitWrapped(this, endOfVariation - 1);
			}
			generator.emit($loopStart);
			final A_Type innerPhraseType =
				subexpressionsTupleType.defaultType();
			final A_Type singularListType =
				ListNodeTypeDescriptor.createListNodeType(
					LIST_NODE,
					TupleTypeDescriptor.tupleTypeForTypes(
						innerPhraseType.expressionType()),
					TupleTypeDescriptor.tupleTypeForTypes(innerPhraseType));
			beforeDagger.emitOn(singularListType, generator, PUSHED_LIST);
			if (endOfVariation < maxSize)
			{
				generator.flushDelayed();
				generator.emit(
					this,
					BRANCH,
					endOfVariation >= minSize ? $exit : $exitCheckMin);
				if (maxInteger.isFinite())
				{
					generator.emit(this, CHECK_AT_MOST, maxSize - 1);
				}
				afterDagger.emitOn(
					ListNodeTypeDescriptor.empty(), generator, PUSHED_LIST);
				generator.flushDelayed();
				generator.emitIf(
					needsProgressCheck, this, ENSURE_PARSE_PROGRESS);
				generator.emit(this, JUMP, $loopStart);
				if ($exitCheckMin.isUsed())
				{
					generator.emit($exitCheckMin);
					generator.emit(this, CHECK_AT_LEAST, minSize);
				}
			}
			generator.flushDelayed();
			generator.emit($exit);
			generator.emitIf(needsProgressCheck, this, ENSURE_PARSE_PROGRESS);
			generator.emitIf(
				needsProgressCheck, this, DISCARD_SAVED_PARSE_POSITION);
			generator.emit($skip);
			generator.partialListsCount--;
		}
		else
		{
			/* General case -- the individual arguments need to be wrapped
			 * with "append" as for the special case above, but the start
			 * of each loop has to push an empty tuple, the dagger has to
			 * branch to a special $exit that closes the last (partial)
			 * group, and the backward jump should be preceded by an append
			 * to capture a solution.  Note that the cae of maxSize = 0 was
			 * already handled.  Here's the code:
			 *
			 * push empty list (the list of solutions, emitted above)
			 * branch to $skip (if minSize = 0)
			 * push current parse position on the mark stack
			 * A repetition for each N=1..endOfVariation-1:
			 *     push empty list (a compound solution)
			 *     ...Stuff before dagger, where arguments and subgroups are
			 *     ...followed by "append" instructions.
			 *     permute left-half arguments tuple if needed
			 *     branch to $exit (if N ≥ minSize)
			 *     ...Stuff after dagger, nothing if dagger is omitted.
			 *     ...Must follow each argument or subgroup with "append"
			 *     ...instruction.
			 *     permute *only* right half of solution tuple if needed
			 *     append  (add complete solution)
			 *     check progress and update saved position, or abort.
			 * And a final loop:
			 *     $loopStart:
			 *     push empty list (a compound solution)
			 *     ...Stuff before dagger, where arguments and subgroups are
			 *     ...followed by "append" instructions.
			 *     permute left-half arguments tuple if needed
			 *     if (endOfVariation < maxSize) then:
			 *         EITHER branch to $exit (if endOfVariation ≥ minSize)
			 *         OR to $exitCheckMin (if endOfVariation < minSize)
			 *         check that the size is still < maxSize.
			 *         ...Stuff after dagger, nothing if dagger is omitted.
			 *         ...Must follow each arg or subgroup with "append"
			 *         ...instruction.
			 *         permute *only* right half of solution tuple if needed
			 *         append  (add complete solution)
			 *         check progress and update saved position, or abort.
			 *         jump to $loopStart.
			 *         if (endOfVariation < minSize) then:
			 *             $exitCheckMin:
			 *             append.
			 *             check at least minSize.
			 *             jump $mergedExit.
			 * $exit:
			 * append  (add partial solution up to dagger)
			 * $mergedExit:
			 * check progress and update saved position, or abort.
			 * discard the saved position from mark stack.
			 * $skip:
			 */
			final Label $skip = new Label();
			final Label $exit = new Label();
			final Label $exitCheckMin = new Label();
			final Label $mergedExit = new Label();
			final Label $loopStart = new Label();
			generator.flushDelayed();
			boolean hasWrapped = false;
			if (minSize == 0)
			{
				// If size zero is valid, branch to the special $skip label that
				// avoids the progress check.  The case maxSize==0 was already
				// handled above.
				assert maxSize > 0;
				generator.emit(this, EMPTY_LIST);
				hasWrapped = true;
				generator.emit(this, BRANCH, $skip);
			}
			if (!hasWrapped
				&& (beforeDagger.hasSectionCheckpoints()
					|| afterDagger.hasSectionCheckpoints()))
			{
				generator.emit(this, EMPTY_LIST);
				hasWrapped = true;
			}
			generator.emitIf(needsProgressCheck, this, SAVE_PARSE_POSITION);
			for (int index = 1; index < endOfVariation; index++)
			{
				if (index >= minSize)
				{
					if (!hasWrapped && index == minSize)
					{
						generator.flushDelayed();
						generator.emitWrapped(this, index - 1);
						hasWrapped = true;
					}
				}
				final A_Type sublistPhraseType =
					subexpressionsTupleType.typeAtIndex(index);
				emitDoubleWrappedBeforeDaggerOn(generator, sublistPhraseType);
				if (index >= minSize)
				{
					generator.flushDelayed();
					generator.emit(this, BRANCH, $exit);
				}
				emitDoubleWrappedAfterDaggerOn(generator, sublistPhraseType);
				generator.flushDelayed();
				if (hasWrapped)
				{
					generator.emit(this, APPEND_ARGUMENT);
				}
				generator.emitIf(
					needsProgressCheck, this, ENSURE_PARSE_PROGRESS);
			}
			generator.flushDelayed();
			if (!hasWrapped)
			{
				generator.emitWrapped(this, endOfVariation - 1);
			}
			// The homogenous part of the tuple, one or more iterations.
			generator.emit($loopStart);
			final A_Type sublistPhraseType =
				subexpressionsTupleType.typeAtIndex(endOfVariation);
			emitDoubleWrappedBeforeDaggerOn(generator, sublistPhraseType);
			generator.flushDelayed();
			if (endOfVariation < maxSize)
			{
				generator.emit(
					this,
					BRANCH,
					endOfVariation >= minSize ? $exit : $exitCheckMin);
				if (maxInteger.isFinite())
				{
					generator.emit(this, CHECK_AT_MOST, maxSize - 1);
				}
				emitDoubleWrappedAfterDaggerOn(generator, sublistPhraseType);
				generator.flushDelayed();
				generator.emit(this, APPEND_ARGUMENT);
				generator.emitIf(
					needsProgressCheck, this, ENSURE_PARSE_PROGRESS);
				generator.emit(this, JUMP, $loopStart);
				if ($exitCheckMin.isUsed())
				{
					generator.emit($exitCheckMin);
					generator.emit(this, APPEND_ARGUMENT);
					generator.emit(this, CHECK_AT_LEAST, minSize);
					generator.emit(this, JUMP, $mergedExit);
				}
			}
			generator.emit($exit);
			generator.emit(this, APPEND_ARGUMENT);
			generator.emit($mergedExit);
			generator.emitIf(needsProgressCheck, this, ENSURE_PARSE_PROGRESS);
			generator.emitIf(
				needsProgressCheck, this, DISCARD_SAVED_PARSE_POSITION);
			generator.emit($skip);
		}
		return wrapState.processAfterPushedArgument(this, generator);
	}

	/**
	 * Emit instructions to parse one occurrence of the portion of this
	 * group before the double-dagger.  Ensure the arguments and subgroups are
	 * assembled into a new list and pushed.
	 *
	 * <p>Permute this left-half list as needed.</p>
	 *
	 * @param generator
	 *        Where to generate parsing instructions.
	 * @param phraseType
	 *        The phrase type of the particular repetition of this group
	 *        whose before-dagger sequence is to be parsed.
	 */
	private void emitDoubleWrappedBeforeDaggerOn (
		final InstructionGenerator generator,
		final A_Type phraseType)
	{
		final A_Type subexpressionsTupleType =
			phraseType.subexpressionsTupleType();
		generator.partialListsCount += 2;
		int argIndex = 0;
		int ungroupedArgCount = 0;
		boolean listIsPushed = false;
		for (final Expression expression : beforeDagger.expressions)
		{
			// In order to ensure section checkpoints see a reasonable view of
			// the parse stack, form a list with what has been pushed before any
			// subexpression that has a section checkpoint, and use appends from
			// that point onward.
			if (expression.hasSectionCheckpoints())
			{
				tidyPushedList(generator, ungroupedArgCount, listIsPushed);
				ungroupedArgCount = 0;
				listIsPushed = true;
			}
			if (expression.isArgumentOrGroup())
			{
				argIndex++;
				final int realTypeIndex =
					beforeDagger.argumentsAreReordered == Boolean.TRUE
						? beforeDagger.permutedArguments.get(argIndex - 1)
						: argIndex;
				final A_Type entryType =
					subexpressionsTupleType.typeAtIndex(realTypeIndex);
				generator.flushDelayed();
				expression.emitOn(entryType, generator, SHOULD_NOT_PUSH_LIST);
				ungroupedArgCount++;
			}
			else
			{
				expression.emitOn(
					ListNodeTypeDescriptor.empty(),
					generator,
					SHOULD_NOT_HAVE_ARGUMENTS);
			}
		}
		assert argIndex == beforeDagger.arguments.size();
		tidyPushedList(generator, ungroupedArgCount, listIsPushed);
		generator.partialListsCount -= 2;
		if (beforeDagger.argumentsAreReordered == Boolean.TRUE)
		{
			// Permute the list on top of stack.
			final A_Tuple permutationTuple =
				TupleDescriptor.tupleFromIntegerList(
					beforeDagger.permutedArguments);
			final int permutationIndex =
				LookupTree.indexForPermutation(permutationTuple);
			generator.flushDelayed();
			generator.emit(this, PERMUTE_LIST, permutationIndex);
		}
	}

	/**
	 * Emit instructions to parse one occurrence of the portion of this
	 * group after the double-dagger.  Append each argument or subgroup.
	 * Permute just the right half of this list as needed.
	 *
	 * @param generator
	 *        Where to generate parsing instructions.
	 * @param phraseType
	 *        The phrase type of the particular repetition of this group
	 *        whose after-dagger sequence is to be parsed.
	 */
	private void emitDoubleWrappedAfterDaggerOn (
		final InstructionGenerator generator,
		final A_Type phraseType)
	{
		final A_Type subexpressionsTupleType =
			phraseType.subexpressionsTupleType();
		generator.partialListsCount += 2;
		int argIndex = beforeDagger.arguments.size();
		int ungroupedArgCount = 0;
		for (final Expression expression : afterDagger.expressions)
		{
			if (expression.hasSectionCheckpoints())
			{
				tidyPushedList(generator, ungroupedArgCount, true);
				ungroupedArgCount = 0;
			}
			if (expression.isArgumentOrGroup())
			{
				argIndex++;
				final int realTypeIndex =
					afterDagger.argumentsAreReordered == Boolean.TRUE
						? afterDagger.permutedArguments.get(argIndex - 1)
						: argIndex;
				final A_Type entryType =
					subexpressionsTupleType.typeAtIndex(realTypeIndex);
				generator.flushDelayed();
				expression.emitOn(entryType, generator, PUSHED_LIST);
				ungroupedArgCount = 0;
			}
			else
			{
				expression.emitOn(
					ListNodeTypeDescriptor.empty(),
					generator,
					SHOULD_NOT_HAVE_ARGUMENTS);
			}
		}
		tidyPushedList(generator, ungroupedArgCount, true);
		generator.partialListsCount -= 2;
		if (afterDagger.argumentsAreReordered == Boolean.TRUE)
		{
			// Permute just the right portion of the list on top of
			// stack.  The left portion was already adjusted in case it
			// was the last iteration and didn't have a right side.
			final int leftArgCount = beforeDagger.arguments.size();
			final int rightArgCount = afterDagger.arguments.size();
			final int adjustedPermutationSize =
				leftArgCount + rightArgCount;
			final ArrayList<Integer> adjustedPermutationList =
				new ArrayList<>(adjustedPermutationSize);
			for (int i = 1; i <= leftArgCount; i++)
			{
				// The left portion is the identity permutation, since
				// the actual left permutation was already applied.
				adjustedPermutationList.add(i);
			}
			for (int i = 0; i < rightArgCount; i++)
			{
				// Adjust the right permutation indices by the size of the left
				// part.
				adjustedPermutationList.add(
					afterDagger.arguments.get(i).explicitOrdinal()
						+ leftArgCount);
			}
			final A_Tuple permutationTuple =
				TupleDescriptor.tupleFromIntegerList(
					adjustedPermutationList);
			final int permutationIndex =
				LookupTree.indexForPermutation(permutationTuple);
			generator.flushDelayed();
			generator.emit(this, PERMUTE_LIST, permutationIndex);
		}
		// Ensure the tuple type was consumed up to its upperBound.
		assert subexpressionsTupleType.sizeRange().upperBound().equalsInt(
			argIndex);
	}

	/**
	 * Tidy up the stack, given information about whether a list has already
	 * been pushed, and how many values have been pushed instead of or in
	 * addition to that list.  After this call, a list definitely will have been
	 * pushed, and no additional arguments will be after it on the stack.
	 *
	 * @param listIsPushed
	 *        Whether a list has already been pushed.
	 * @param ungroupedArgCount
	 *        The number of arguments that have pushed since the list, if any.
	 * @param generator
	 *        Where to generate instructions.
	 */
	private void tidyPushedList (
		final InstructionGenerator generator,
		final int ungroupedArgCount,
		final boolean listIsPushed)
	{
		generator.flushDelayed();
		if (!listIsPushed)
		{
			generator.emitWrapped(this, ungroupedArgCount);
		}
		else if (ungroupedArgCount == 1)
		{
			generator.emit(this, APPEND_ARGUMENT);
		}
		else if (ungroupedArgCount > 1)
		{
			generator.emitWrapped(this, ungroupedArgCount);
			generator.emit(this, CONCATENATE);
		}
	}

	@Override
	public String toString ()
	{
		final List<String> strings = new ArrayList<>();
		for (final Expression e : beforeDagger.expressions)
		{
			final StringBuilder builder = new StringBuilder();
			builder.append(e);
			if (e.canBeReordered() && e.explicitOrdinal() != -1)
			{
				builder.appendCodePoint(
					MessageSplitter.circledNumberCodePoints[e.explicitOrdinal()]);
			}
			strings.add(builder.toString());
		}
		if (hasDagger)
		{
			strings.add("‡");
			for (final Expression e : afterDagger.expressions)
			{
				strings.add(e.toString());
			}
		}

		final StringBuilder builder = new StringBuilder();
		builder.append("Group(");
		boolean first = true;
		for (final String s : strings)
		{
			if (!first)
			{
				builder.append(", ");
			}
			builder.append(s);
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
		final boolean needsDouble = needsDoubleWrapping();
		final A_Phrase groupArguments = argumentProvider.next();
		final Iterator<AvailObject> occurrenceProvider =
			groupArguments.expressionsTuple().iterator();
		while (occurrenceProvider.hasNext())
		{
			final AvailObject occurrence = occurrenceProvider.next();
			final Iterator<AvailObject> innerIterator;
			if (needsDouble)
			{
				// The occurrence is itself a list node containing the
				// parse nodes to fill in to this group's arguments and
				// subgroups.
				assert occurrence.isInstanceOfKind(
					LIST_NODE.mostGeneralType());
				innerIterator = occurrence.expressionsTuple().iterator();
			}
			else
			{
				// The argumentObject is a listNode of parse nodes.
				// Each parse node is for the single argument or subgroup
				// which is left of the double-dagger (and there are no
				// arguments or subgroups to the right).
				assert occurrence.isInstanceOfKind(
					EXPRESSION_NODE.mostGeneralType());
				final List<AvailObject> argumentNodes =
					Collections.singletonList(occurrence);
				innerIterator = argumentNodes.iterator();
			}
			printGroupOccurrence(
				innerIterator,
				builder,
				indent,
				occurrenceProvider.hasNext());
			assert !innerIterator.hasNext();
		}
	}

	/**
	 * Pretty-print this part of the message, using the provided iterator
	 * to supply arguments.  This prints a single occurrence of a repeated
	 * group.  The completeGroup flag indicates if the double-dagger and
	 * subsequent subexpressions should also be printed.
	 *
	 * @param argumentProvider
	 *        An iterator to provide parse nodes for this group occurrence's
	 *        arguments and subgroups.
	 * @param builder
	 *        The {@link StringBuilder} on which to print.
	 * @param indent
	 *        The indentation level.
	 * @param completeGroup
	 *        Whether to produce a complete group or just up to the
	 *        double-dagger. The last repetition of a subgroup uses false
	 *        for this flag.
	 */
	void printGroupOccurrence (
		final Iterator<AvailObject> argumentProvider,
		final StringBuilder builder,
		final int indent,
		final boolean completeGroup)
	{
		builder.append("«");
		final List<Expression> expressionsToVisit;
		if (completeGroup && !afterDagger.expressions.isEmpty())
		{
			expressionsToVisit = new ArrayList<>(
				beforeDagger.expressions.size()
				+ 1
				+ afterDagger.expressions.size());
			expressionsToVisit.addAll(beforeDagger.expressions);
			expressionsToVisit.add(null);  // Represents the dagger
			expressionsToVisit.addAll(afterDagger.expressions);
		}
		else
		{
			expressionsToVisit = beforeDagger.expressions;
		}
		boolean needsSpace = false;
		for (final Expression expr : expressionsToVisit)
		{
			if (expr == null)
			{
				// Place-holder for the double-dagger.
				builder.append("‡");
				needsSpace = false;
			}
			else
			{
				if (needsSpace && expr.shouldBeSeparatedOnLeft())
				{
					builder.append(" ");
				}
				final int oldLength = builder.length();
				expr.printWithArguments(argumentProvider, builder, indent);
				needsSpace = expr.shouldBeSeparatedOnRight()
					&& builder.length() != oldLength;
			}
		}
		assert !argumentProvider.hasNext();
		builder.append("»");
	}

	@Override
	boolean shouldBeSeparatedOnLeft ()
	{
		return false;
	}

	@Override
	boolean shouldBeSeparatedOnRight ()
	{
		return false;
	}

	@Override
	boolean mightBeEmpty (final A_Type phraseType)
	{
		// This group can consume no tokens iff it can have zero repetitions.
		final A_Type tupleType = phraseType.expressionType();
		assert tupleType.isTupleType();
		return tupleType.sizeRange().lowerBound().equalsInt(0);
	}
}
