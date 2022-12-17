/*
 * Group.kt
 * Copyright © 1993-2022, The Avail Foundation, LLC.
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

package avail.compiler.splitter

import avail.compiler.ParsingOperation.APPEND_ARGUMENT
import avail.compiler.ParsingOperation.CHECK_AT_LEAST
import avail.compiler.ParsingOperation.CHECK_AT_MOST
import avail.compiler.ParsingOperation.CONCATENATE
import avail.compiler.ParsingOperation.DISCARD_SAVED_PARSE_POSITION
import avail.compiler.ParsingOperation.EMPTY_LIST
import avail.compiler.ParsingOperation.ENSURE_PARSE_PROGRESS
import avail.compiler.ParsingOperation.PERMUTE_LIST
import avail.compiler.ParsingOperation.SAVE_PARSE_POSITION
import avail.compiler.splitter.InstructionGenerator.Label
import avail.compiler.splitter.MessageSplitter.Companion.circledNumberCodePoint
import avail.compiler.splitter.MessageSplitter.Companion.indexForPermutation
import avail.compiler.splitter.MessageSplitter.Companion.throwSignatureException
import avail.compiler.splitter.MessageSplitter.Metacharacter
import avail.compiler.splitter.MessageSplitter.Metacharacter.DOUBLE_DAGGER
import avail.compiler.splitter.WrapState.PUSHED_LIST
import avail.compiler.splitter.WrapState.SHOULD_NOT_HAVE_ARGUMENTS
import avail.compiler.splitter.WrapState.SHOULD_NOT_PUSH_LIST
import avail.descriptor.numbers.A_Number.Companion.equalsInt
import avail.descriptor.numbers.A_Number.Companion.extractInt
import avail.descriptor.numbers.A_Number.Companion.isInt
import avail.descriptor.numbers.InfinityDescriptor.Companion.positiveInfinity
import avail.descriptor.numbers.IntegerDescriptor.Companion.fromInt
import avail.descriptor.numbers.IntegerDescriptor.Companion.zero
import avail.descriptor.phrases.A_Phrase
import avail.descriptor.phrases.A_Phrase.Companion.expressionsTuple
import avail.descriptor.phrases.A_Phrase.Companion.permutation
import avail.descriptor.phrases.A_Phrase.Companion.phraseKindIsUnder
import avail.descriptor.phrases.A_Phrase.Companion.token
import avail.descriptor.phrases.ListPhraseDescriptor.Companion.newListNode
import avail.descriptor.phrases.LiteralPhraseDescriptor.Companion.syntheticLiteralNodeFor
import avail.descriptor.representation.AvailObject
import avail.descriptor.tuples.A_Tuple.Companion.tupleAt
import avail.descriptor.tuples.A_Tuple.Companion.tupleSize
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tupleFromList
import avail.descriptor.tuples.StringDescriptor.Companion.stringFrom
import avail.descriptor.tuples.TupleDescriptor
import avail.descriptor.tuples.TupleDescriptor.Companion.tupleFromIntegerList
import avail.descriptor.types.A_Type
import avail.descriptor.types.A_Type.Companion.defaultType
import avail.descriptor.types.A_Type.Companion.isSubtypeOf
import avail.descriptor.types.A_Type.Companion.lowerBound
import avail.descriptor.types.A_Type.Companion.phraseTypeExpressionType
import avail.descriptor.types.A_Type.Companion.sizeRange
import avail.descriptor.types.A_Type.Companion.subexpressionsTupleType
import avail.descriptor.types.A_Type.Companion.typeAtIndex
import avail.descriptor.types.A_Type.Companion.typeIntersection
import avail.descriptor.types.A_Type.Companion.typeTuple
import avail.descriptor.types.A_Type.Companion.upperBound
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.integerRangeType
import avail.descriptor.types.ListPhraseTypeDescriptor.Companion.createListPhraseType
import avail.descriptor.types.ListPhraseTypeDescriptor.Companion.emptyListPhraseType
import avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.EXPRESSION_PHRASE
import avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.LIST_PHRASE
import avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.LITERAL_PHRASE
import avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.PERMUTED_LIST_PHRASE
import avail.descriptor.types.TupleTypeDescriptor.Companion.tupleTypeForTypes
import avail.exceptions.AvailErrorCode.E_CASE_INSENSITIVE_EXPRESSION_CANONIZATION
import avail.exceptions.AvailErrorCode.E_INCORRECT_ARGUMENT_TYPE
import avail.exceptions.AvailErrorCode.E_INCORRECT_TYPE_FOR_COMPLEX_GROUP
import avail.exceptions.AvailErrorCode.E_INCORRECT_TYPE_FOR_GROUP
import avail.exceptions.MalformedMessageException
import avail.exceptions.SignatureException
import kotlin.math.max
import kotlin.math.min

/**
 * A [Group] is delimited by the
 * [open&#32;guillemet][Metacharacter.OPEN_GUILLEMET] («) and
 * [close&#32;guillemet][Metacharacter.CLOSE_GUILLEMET] (») characters, and may
 * contain subgroups and an occurrence of a
 * [double&#32;dagger][Metacharacter.DOUBLE_DAGGER] (‡). If no double dagger or
 * subgroup is present, the sequence of message parts between the guillemets are
 * allowed to occur zero or more times at a call site (i.e., a send of this
 * message). When the number of [underscore][Metacharacter.UNDERSCORE] (_) and
 * [ellipsis][Metacharacter.ELLIPSIS] (…) plus the number of subgroups is
 * exactly one, the argument (or subgroup) values are assembled into a
 * [tuple][TupleDescriptor]. Otherwise the leaf arguments and/or subgroups are
 * assembled into a tuple of fixed-sized tuples, each containing one entry for
 * each argument or subgroup.
 *
 * When a double dagger occurs in a group, the parts to the left of the double
 * dagger can occur zero or more times, but separated by the parts to the right.
 * For example, "«_‡,»" is how to specify a comma-separated tuple of arguments.
 * This pattern contains a single underscore and no subgroups, so parsing
 * "1,2,3" would simply produce the tuple <1,2,3>. The pattern "«_=_;»" will
 * parse "1=2;3=4;5=6;" into <<1,2>,<3,4>,<5,6>> because it has two underscores.
 *
 * The message "«A_‡x_»" parses zero or more occurrences in the text of the
 * keyword "A" followed by an argument, separated by the keyword "x" and an
 * argument.  "A 1 x 2 A 3 x 4 A 5" is such an expression (and "A 1 x 2" is
 * not). In this case, the arguments will be grouped in such a way that the
 * final element of the tuple, if any, is missing the post-double dagger
 * elements: <<1,2>,<3,4>,<5>>.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @constructor
 * Construct a new [Group], given a [Sequence] before the double-dagger, and a
 * [Sequence] after the double-dagger.  If the double-dagger did not occur,
 * the [afterDagger] should be an empty sequence, and [hasDagger] should be
 * false.
 *
 * @param startInName
 *   The position of the group in the message name, specifically just before the
 *   "«".
 * @param pastEndInName
 *   The position just past the end of the group, which should be just after the
 *   "»" or any suffix characters that don't just wrap the group.
 * @param beforeDagger
 *   The [Sequence] before the double-dagger, or in the entire subexpression if
 *   no double-dagger was present.
 * @param hasDagger
 *   True iff a [DOUBLE_DAGGER] ("‡") occurred.
 * @param afterDagger
 *   The [Sequence] after the double-dagger, or an empty sequence if there was
 *   no double-dagger.
 * @param maximumCardinality
 *   The maximum number of occurrences accepted for this group, or the default
 *   [Integer.MAX_VALUE] if it's unbounded.
 */
internal class Group
constructor(
	startInName: Int,
	pastEndInName: Int,
	val beforeDagger: Sequence,
	val hasDagger: Boolean,
	val afterDagger: Sequence,
	var maximumCardinality: Int = Integer.MAX_VALUE
) : Expression(startInName, pastEndInName)
{
	override val recursivelyContainsReorders: Boolean
		get() = beforeDagger.recursivelyContainsReorders ||
			afterDagger.recursivelyContainsReorders

	override val yieldsValue: Boolean
		get() = true

	override val isGroup: Boolean
		get() = true

	override val isLowerCase: Boolean
		get() = beforeDagger.isLowerCase && afterDagger.isLowerCase

	@Throws(MalformedMessageException::class)
	override fun applyCaseInsensitive(): Group
	{
		if (!isLowerCase)
		{
			// Fail due to the group containing a non-lowercase token.
			MessageSplitter.throwMalformedMessageException(
				E_CASE_INSENSITIVE_EXPRESSION_CANONIZATION,
				"Tilde (~) may only occur after a lowercase token or a group " +
					"of lowercase tokens")
		}
		return Group(
			startInName,
			pastEndInName,
			beforeDagger.applyCaseInsensitive(),
			hasDagger,
			afterDagger.applyCaseInsensitive(),
			maximumCardinality)
	}

	override val underscoreCount: Int
		get() = beforeDagger.underscoreCount + afterDagger.underscoreCount

	/**
	 * Determine if this group should generate a [tuple][TupleDescriptor] of
	 * plain arguments or a tuple of fixed-length tuples of plain arguments.
	 *
	 * @return
	 *   `true` if this group will generate a tuple of fixed-length tuples,
	 *   `false` if this group will generate a tuple of individual arguments or
	 *   subgroups.
	 */
	private val needsDoubleWrapping: Boolean
		get() =
			beforeDagger.yielders.size != 1 || afterDagger.yielders.isNotEmpty()

	override fun extractSectionCheckpointsInto(
		sectionCheckpoints: MutableList<SectionCheckpoint>)
	{
		beforeDagger.extractSectionCheckpointsInto(sectionCheckpoints)
		afterDagger.extractSectionCheckpointsInto(sectionCheckpoints)
	}

	/**
	 * Check if the given type is suitable for holding values generated by
	 * this group.
	 */
	@Throws(SignatureException::class)
	override fun checkType(argumentType: A_Type, sectionNumber: Int)
	{
		// Always expect a tuple of solutions here.
		if (argumentType.isBottom)
		{
			// Method argument type should not be bottom.
			throwSignatureException(E_INCORRECT_ARGUMENT_TYPE)
		}

		if (!argumentType.isTupleType)
		{
			// The group produces a tuple.
			throwSignatureException(E_INCORRECT_TYPE_FOR_GROUP)
		}

		val requiredRange = integerRangeType(
			zero,
			true,
			if (maximumCardinality == Integer.MAX_VALUE) positiveInfinity
			else fromInt(maximumCardinality + 1),
			false)

		if (!argumentType.sizeRange.isSubtypeOf(requiredRange))
		{
			// The method's parameter should have a cardinality that's a
			// subtype of what the message name requires.
			throwSignatureException(E_INCORRECT_TYPE_FOR_GROUP)
		}

		if (needsDoubleWrapping)
		{
			// Expect a tuple of tuples of values, where the inner tuple
			// size ranges from the number of arguments left of the dagger
			// up to that plus the number of arguments right of the dagger.
			assert(argumentType.isTupleType)
			val argsBeforeDagger = beforeDagger.yielders.size
			val argsAfterDagger = afterDagger.yielders.size
			val expectedLower = fromInt(argsBeforeDagger)
			val expectedUpper = fromInt(argsBeforeDagger + argsAfterDagger)
			val typeTuple = argumentType.typeTuple
			val limit = typeTuple.tupleSize + 1
			for (i in 1..limit)
			{
				val solutionType = argumentType.typeAtIndex(i)
				if (solutionType.isBottom)
				{
					// It was the empty tuple type.
					break
				}
				if (!solutionType.isTupleType)
				{
					// The argument should be a tuple of tuples.
					throwSignatureException(E_INCORRECT_TYPE_FOR_GROUP)
				}
				// Check that the solution that will reside at the current
				// index accepts either a full group or a group up to the
				// dagger.
				val solutionTypeSizes = solutionType.sizeRange
				val lower = solutionTypeSizes.lowerBound
				val upper = solutionTypeSizes.upperBound
				if (!lower.equals(expectedLower)
					|| !upper.equals(expectedUpper))
				{
					// This complex group should have elements whose types are
					// tuples restricted to have sizes ranging from the number
					// of argument subexpressions before the double dagger up to
					// the total number of argument subexpressions in this
					// group.
					throwSignatureException(E_INCORRECT_TYPE_FOR_COMPLEX_GROUP)
				}
				var j = 1
				for (e in beforeDagger.yielders)
				{
					e.checkType(solutionType.typeAtIndex(j++), sectionNumber)
				}
				for (e in afterDagger.yielders)
				{
					e.checkType(solutionType.typeAtIndex(j++), sectionNumber)
				}
			}
		}
	}

	@Suppress("LocalVariableName")
	@Throws(SignatureException::class)
	override fun emitOn(
		phraseType: A_Type,
		generator: InstructionGenerator,
		wrapState: WrapState): WrapState
	{
		// The phrase type might not be guaranteed to be a list type at this
		// point.
		val intersected = phraseType
			.makeImmutable()
			.typeIntersection(LIST_PHRASE.mostGeneralType)
			.makeShared()
		if (intersected.isBottom)
		{
			// If a list is supplied, it won't match the type, and if a non-list
			// is supplied, it can't be parsed.  Note that this doesn't affect
			// the ability to invoke such a method/macro body with, say, a
			// literal phrase yielding a suitable tuple.
			throwSignatureException(E_INCORRECT_TYPE_FOR_GROUP)
		}
		val subexpressionsTupleType = intersected.subexpressionsTupleType
		val sizeRange = subexpressionsTupleType.sizeRange
		val minInteger = sizeRange.lowerBound
		val minSize =
			if (minInteger.isInt) minInteger.extractInt else Integer.MAX_VALUE
		val maxInteger = sizeRange.upperBound
		val maxSize =
			if (maxInteger.isInt) maxInteger.extractInt else Integer.MAX_VALUE
		val endOfVariation = min(
			max(
				subexpressionsTupleType.typeTuple.tupleSize + 2,
				min(minSize, 3)),
			maxSize)
		val needsProgressCheck = beforeDagger.mightBeEmpty(intersected)
		generator.flushDelayed()
		if (maxSize == 0)
		{
			// The type signature requires an empty list, so that's what we get.
			generator.emit(this, EMPTY_LIST)
		}
		else if (!needsDoubleWrapping)
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
			generator.partialListsCount++
			assert(beforeDagger.yielders.size == 1)
			assert(afterDagger.yielders.isEmpty())
			var hasWrapped = false
			val `$skip` = Label()
			if (minSize == 0)
			{
				// If size zero is valid, branch to the special $skip label that
				// avoids the progress check.  The case maxSize==0 was already
				// handled above.
				assert(maxSize > 0)
				generator.emit(this, EMPTY_LIST)
				hasWrapped = true
				generator.emitBranchForward(this, `$skip`)
			}
			if (!hasWrapped && beforeDagger.hasSectionCheckpoints)
			{
				generator.emit(this, EMPTY_LIST)
				hasWrapped = true
			}
			generator.emitIf(needsProgressCheck, this, SAVE_PARSE_POSITION)
			val `$exit` = Label()
			for (index in 1 until endOfVariation)
			{
				val innerPhraseType = subexpressionsTupleType.typeAtIndex(index)
				val singularListType = createListPhraseType(
					LIST_PHRASE,
					tupleTypeForTypes(
						innerPhraseType.phraseTypeExpressionType),
					tupleTypeForTypes(innerPhraseType))
				beforeDagger.emitOn(
					singularListType,
					generator,
					if (hasWrapped) PUSHED_LIST else SHOULD_NOT_PUSH_LIST)
				if (index >= minSize)
				{
					generator.flushDelayed()
					if (!hasWrapped && index == minSize)
					{
						generator.emitWrapped(this, index)
						hasWrapped = true
					}
					generator.emitBranchForward(this, `$exit`)
				}
				if (!hasWrapped
					&& index == 1
					&& afterDagger.hasSectionCheckpoints)
				{
					generator.flushDelayed()
					generator.emitWrapped(this, 1)
					hasWrapped = true
				}
				afterDagger.emitOn(
					emptyListPhraseType(), generator, PUSHED_LIST)
				generator.emitIf(
					needsProgressCheck, this, ENSURE_PARSE_PROGRESS)
			}
			// The homogenous part of the tuple, one or more iterations.
			generator.flushDelayed()
			if (!hasWrapped)
			{
				generator.emitWrapped(this, endOfVariation - 1)
			}
			val `$loopStart` = Label()
			generator.emit(`$loopStart`)
			val innerPhraseType = subexpressionsTupleType.defaultType
			val singularListType = createListPhraseType(
				LIST_PHRASE,
				tupleTypeForTypes(innerPhraseType.phraseTypeExpressionType),
				tupleTypeForTypes(innerPhraseType))
			beforeDagger.emitOn(singularListType, generator, PUSHED_LIST)
			if (endOfVariation < maxSize)
			{
				generator.flushDelayed()
				val `$exitCheckMin` = Label()
				generator.emitBranchForward(
					this,
					if (endOfVariation >= minSize) `$exit` else `$exitCheckMin`)
				if (maxInteger.isFinite)
				{
					generator.emit(this, CHECK_AT_MOST, maxSize - 1)
				}
				afterDagger.emitOn(
					emptyListPhraseType(), generator, PUSHED_LIST)
				generator.flushDelayed()
				generator.emitIf(
					needsProgressCheck, this, ENSURE_PARSE_PROGRESS)
				generator.emitJumpBackward(this, `$loopStart`)
				if (`$exitCheckMin`.isUsed)
				{
					generator.emit(`$exitCheckMin`)
					generator.emit(this, CHECK_AT_LEAST, minSize)
				}
			}
			generator.flushDelayed()
			generator.emit(`$exit`)
			generator.emitIf(needsProgressCheck, this, ENSURE_PARSE_PROGRESS)
			generator.emitIf(
				needsProgressCheck, this, DISCARD_SAVED_PARSE_POSITION)
			generator.emit(`$skip`)
			generator.partialListsCount--
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
			generator.flushDelayed()
			var hasWrapped = false
			val `$skip` = Label()
			if (minSize == 0)
			{
				// If size zero is valid, branch to the special $skip label that
				// avoids the progress check.  The case maxSize==0 was already
				// handled above.
				assert(maxSize > 0)
				generator.emit(this, EMPTY_LIST)
				hasWrapped = true
				generator.emitBranchForward(this, `$skip`)
			}
			if (!hasWrapped
				&& (beforeDagger.hasSectionCheckpoints
					|| afterDagger.hasSectionCheckpoints))
			{
				generator.emit(this, EMPTY_LIST)
				hasWrapped = true
			}
			generator.emitIf(needsProgressCheck, this, SAVE_PARSE_POSITION)
			val `$exit` = Label()
			for (index in 1 until endOfVariation)
			{
				if (index >= minSize)
				{
					if (!hasWrapped && index == minSize)
					{
						generator.flushDelayed()
						generator.emitWrapped(this, index - 1)
						hasWrapped = true
					}
				}
				val sublistPhraseType =
					subexpressionsTupleType.typeAtIndex(index)
				emitDoubleWrappedBeforeDaggerOn(generator, sublistPhraseType)
				if (index >= minSize)
				{
					generator.flushDelayed()
					generator.emitBranchForward(this, `$exit`)
				}
				emitDoubleWrappedAfterDaggerOn(generator, sublistPhraseType)
				generator.flushDelayed()
				if (hasWrapped)
				{
					generator.emit(this, APPEND_ARGUMENT)
				}
				generator.emitIf(
					needsProgressCheck, this, ENSURE_PARSE_PROGRESS)
			}
			generator.flushDelayed()
			if (!hasWrapped)
			{
				generator.emitWrapped(this, endOfVariation - 1)
			}
			// The homogenous part of the tuple, one or more iterations.
			val `$loopStart` = Label()
			generator.emit(`$loopStart`)
			val sublistPhraseType =
				subexpressionsTupleType.typeAtIndex(endOfVariation)
			emitDoubleWrappedBeforeDaggerOn(generator, sublistPhraseType)
			generator.flushDelayed()
			val `$mergedExit` = Label()
			if (endOfVariation < maxSize)
			{
				val `$exitCheckMin` = Label()
				generator.emitBranchForward(
					this,
					if (endOfVariation >= minSize) `$exit` else `$exitCheckMin`)
				if (maxInteger.isFinite)
				{
					generator.emit(this, CHECK_AT_MOST, maxSize - 1)
				}
				emitDoubleWrappedAfterDaggerOn(generator, sublistPhraseType)
				generator.flushDelayed()
				generator.emit(this, APPEND_ARGUMENT)
				generator.emitIf(
					needsProgressCheck, this, ENSURE_PARSE_PROGRESS)
				generator.emitJumpBackward(this, `$loopStart`)
				if (`$exitCheckMin`.isUsed)
				{
					generator.emit(`$exitCheckMin`)
					generator.emit(this, APPEND_ARGUMENT)
					generator.emit(this, CHECK_AT_LEAST, minSize)
					generator.emitJumpForward(this, `$mergedExit`)
				}
			}
			generator.emit(`$exit`)
			generator.emit(this, APPEND_ARGUMENT)
			generator.emit(`$mergedExit`)
			generator.emitIf(needsProgressCheck, this, ENSURE_PARSE_PROGRESS)
			generator.emitIf(
				needsProgressCheck, this, DISCARD_SAVED_PARSE_POSITION)
			generator.emit(`$skip`)
		}
		return wrapState.processAfterPushedArgument(this, generator)
	}

	/**
	 * Emit instructions to parse one occurrence of the portion of this
	 * group before the double-dagger.  Ensure the arguments and subgroups are
	 * assembled into a new list and pushed.
	 *
	 * Permute this left-half list as needed.
	 *
	 * @param generator
	 *   Where to generate parsing instructions.
	 * @param phraseType
	 *   The phrase type of the particular repetition of this group whose
	 *   before-dagger sequence is to be parsed.
	 * @throws SignatureException
	 *   If the signature and phrase type are inconsistent.
	 */
	@Throws(SignatureException::class)
	private fun emitDoubleWrappedBeforeDaggerOn(
		generator: InstructionGenerator,
		phraseType: A_Type)
	{
		// The phrase type might not be guaranteed to be a list type at this
		// point.
		val intersected = phraseType
			.makeImmutable()
			.typeIntersection(LIST_PHRASE.mostGeneralType)
			.makeShared()
		if (intersected.isBottom)
		{
			// If a list is supplied, it won't match the type, and if a non-list
			// is supplied, it can't be parsed.  Note that this doesn't affect
			// the ability to invoke such a method/macro body with, say, a
			// literal phrase yielding a suitable tuple.
			throwSignatureException(E_INCORRECT_TYPE_FOR_GROUP)
		}
		val subexpressionsTupleType = intersected.subexpressionsTupleType
		generator.partialListsCount += 2
		var argIndex = 0
		var ungroupedArgCount = 0
		var listIsPushed = false
		for (expression in beforeDagger.expressions)
		{
			// In order to ensure section checkpoints see a reasonable view of
			// the parse stack, form a list with what has been pushed before any
			// subexpression that has a section checkpoint, and use appends from
			// that point onward.
			if (expression.hasSectionCheckpoints)
			{
				tidyPushedList(generator, ungroupedArgCount, listIsPushed)
				ungroupedArgCount = 0
				listIsPushed = true
			}
			if (expression.yieldsValue)
			{
				argIndex++
				val realTypeIndex =
					if (beforeDagger.isReordered)
						beforeDagger.permutation[argIndex - 1]
					else
						argIndex
				val entryType =
					subexpressionsTupleType.typeAtIndex(realTypeIndex)
				generator.flushDelayed()
				expression.emitOn(entryType, generator, SHOULD_NOT_PUSH_LIST)
				ungroupedArgCount++
			}
			else
			{
				expression.emitOn(
					emptyListPhraseType(),
					generator,
					SHOULD_NOT_HAVE_ARGUMENTS)
			}
		}
		assert(argIndex == beforeDagger.yielders.size)
		tidyPushedList(generator, ungroupedArgCount, listIsPushed)
		generator.partialListsCount -= 2
		if (beforeDagger.isReordered)
		{
			// Permute the list on top of stack.
			val permutationTuple =
				tupleFromIntegerList(beforeDagger.permutation)
			val permutationIndex = indexForPermutation(permutationTuple)
			generator.flushDelayed()
			generator.emit(this, PERMUTE_LIST, permutationIndex)
		}
	}

	/**
	 * Emit instructions to parse one occurrence of the portion of this group
	 * after the double-dagger.  Append each argument or subgroup. Permute just
	 * the right half of this list as needed.
	 *
	 * @param generator
	 *   Where to generate parsing instructions.
	 * @param phraseType
	 *   The phrase type of the particular repetition of this group whose
	 *   after-dagger sequence is to be parsed.
	 * @throws SignatureException
	 *   If the signature and phrase type are inconsistent.
	 */
	private fun emitDoubleWrappedAfterDaggerOn(
		generator: InstructionGenerator,
		phraseType: A_Type)
	{
		// The phrase type might not be guaranteed to be a list type at this
		// point.
		val intersected = phraseType
			.makeImmutable()
			.typeIntersection(LIST_PHRASE.mostGeneralType)
			.makeShared()
		if (intersected.isBottom)
		{
			// If a list is supplied, it won't match the type, and if a non-list
			// is supplied, it can't be parsed.  Note that this doesn't affect
			// the ability to invoke such a method/macro body with, say, a
			// literal phrase yielding a suitable tuple.
			throwSignatureException(E_INCORRECT_TYPE_FOR_GROUP)
		}
		val subexpressionsTupleType = intersected.subexpressionsTupleType
		generator.partialListsCount += 2
		var argIndex = beforeDagger.yielders.size
		var ungroupedArgCount = 0
		for (expression in afterDagger.expressions)
		{
			if (expression.hasSectionCheckpoints)
			{
				tidyPushedList(generator, ungroupedArgCount, true)
				ungroupedArgCount = 0
			}
			if (expression.yieldsValue)
			{
				argIndex++
				val realTypeIndex =
					if (afterDagger.isReordered)
						afterDagger.permutation[argIndex - 1]
					else
						argIndex
				val entryType =
					subexpressionsTupleType.typeAtIndex(realTypeIndex)
				generator.flushDelayed()
				expression.emitOn(entryType, generator, PUSHED_LIST)
				ungroupedArgCount = 0
			}
			else
			{
				expression.emitOn(
					emptyListPhraseType(),
					generator,
					SHOULD_NOT_HAVE_ARGUMENTS)
			}
		}
		tidyPushedList(generator, ungroupedArgCount, true)
		generator.partialListsCount -= 2
		if (afterDagger.isReordered)
		{
			// Permute just the right portion of the list on top of
			// stack.  The left portion was already adjusted in case it
			// was the last iteration and didn't have a right side.
			val leftArgCount = beforeDagger.yielders.size
			val rightArgCount = afterDagger.yielders.size
			val adjustedPermutationList = mutableListOf<Int>()
			for (i in 1..leftArgCount)
			{
				// The left portion is the identity permutation, since
				// the actual left permutation was already applied.
				adjustedPermutationList.add(i)
			}
			for (i in 0 until rightArgCount)
			{
				// Adjust the right permutation indices by the size of the left
				// part.
				adjustedPermutationList.add(
					afterDagger.yielders[i].explicitOrdinal + leftArgCount)
			}
			val permutationTuple = tupleFromIntegerList(adjustedPermutationList)
			val permutationIndex = indexForPermutation(permutationTuple)
			generator.flushDelayed()
			generator.emit(this, PERMUTE_LIST, permutationIndex)
		}
		// Ensure the tuple type was consumed up to its upperBound.
		assert(
			subexpressionsTupleType.sizeRange.upperBound.equalsInt(
				argIndex))
	}

	/**
	 * Tidy up the stack, given information about whether a list has already
	 * been pushed, and how many values have been pushed instead of or in
	 * addition to that list.  After this call, a list definitely will have been
	 * pushed, and no additional arguments will be after it on the stack.
	 *
	 * @param listIsPushed
	 *   Whether a list has already been pushed.
	 * @param ungroupedArgCount
	 *   The number of arguments that have pushed since the list, if any.
	 * @param generator
	 *   Where to generate instructions.
	 */
	private fun tidyPushedList(
		generator: InstructionGenerator,
		ungroupedArgCount: Int,
		listIsPushed: Boolean)
	{
		generator.flushDelayed()
		if (!listIsPushed)
		{
			generator.emitWrapped(this, ungroupedArgCount)
		}
		else if (ungroupedArgCount == 1)
		{
			generator.emit(this, APPEND_ARGUMENT)
		}
		else if (ungroupedArgCount > 1)
		{
			generator.emitWrapped(this, ungroupedArgCount)
			generator.emit(this, CONCATENATE)
		}
	}

	override fun toString(): String
	{
		val strings = mutableListOf<String>()
		for (e in beforeDagger.expressions)
		{
			val string = buildString {
				append(e)
				if (e.canBeReordered && e.explicitOrdinal != -1)
				{
					appendCodePoint(circledNumberCodePoint(e.explicitOrdinal))
				}
			}
			strings.add(string)
		}
		if (hasDagger)
		{
			strings.add("‡")
			for (e in afterDagger.expressions)
			{
				strings.add(e.toString())
			}
		}
		return buildString {
			append("Group(")
			var first = true
			for (s in strings)
			{
				if (!first) append(", ")
				append(s)
				first = false
			}
			append(')')
		}
	}

	override fun printWithArguments(
		arguments: Iterator<A_Phrase>?,
		builder: StringBuilder,
		indent: Int)
	{
		val needsDouble = needsDoubleWrapping
		var groupArguments = arguments!!.next()
		if (groupArguments.phraseKindIsUnder(LITERAL_PHRASE))
		{
			// Decompose a literal tuple as though it was a list phrase of
			// its elements.
			groupArguments = newListNode(
				tupleFromList(
					groupArguments.token.literal().map {
						syntheticLiteralNodeFor(
							it, stringFrom(it.toString()))
					}))
		}
		val occurrenceProvider = groupArguments.expressionsTuple.iterator()
		while (occurrenceProvider.hasNext())
		{
			val occurrence = occurrenceProvider.next()
			val innerIterator: Iterator<AvailObject>
			if (needsDouble)
			{
				// The occurrence is itself a list phrase containing the phrases
				// to fill in to this group's arguments and subgroups.
				assert(
					occurrence.isInstanceOfKind(
						LIST_PHRASE.mostGeneralType))
				innerIterator = occurrence.expressionsTuple.iterator()
			}
			else
			{
				// The argumentObject is a listNode of phrases. Each phrase is
				// for the single argument or subgroup which is left of the
				// double-dagger (and there are no arguments or subgroups to the
				// right).
				assert(
					occurrence.isInstanceOfKind(
						EXPRESSION_PHRASE.mostGeneralType))
				val argumentNodes = listOf(occurrence)
				innerIterator = argumentNodes.iterator()
			}
			printGroupOccurrence(
				innerIterator,
				builder,
				indent,
				occurrenceProvider.hasNext())
			assert(!innerIterator.hasNext())
		}
	}

	/**
	 * Pretty-print this part of the message, using the provided iterator to
	 * supply arguments.  This prints a single occurrence of a repeated group.
	 * The completeGroup flag indicates if the double-dagger and subsequent
	 * subexpressions should also be printed.
	 *
	 * @param argumentProvider
	 *   An iterator to provide phrases for this group occurrence's arguments
	 *   and subgroups.
	 * @param builder
	 *   The [StringBuilder] on which to print.
	 * @param indent
	 *   The indentation level.
	 * @param completeGroup
	 *   Whether to produce a complete group or just up to the double-dagger.
	 *   The last repetition of a subgroup uses false for this flag.
	 */
	fun printGroupOccurrence(
		argumentProvider: Iterator<AvailObject>,
		builder: StringBuilder,
		indent: Int,
		completeGroup: Boolean)
	{
		builder.append('«')
		val expressionsToVisit: List<Expression?> =
			mutableListOf<Expression?>().apply {
				addAll(beforeDagger.expressions)
				if (completeGroup && afterDagger.expressions.isNotEmpty())
				{
					add(null)  // Represents the dagger
					addAll(afterDagger.expressions)
				}
		}
		var needsSpace = false
		for (expr in expressionsToVisit)
		{
			if (expr === null)
			{
				// Place-holder for the double-dagger.
				builder.append('‡')
				needsSpace = false
			}
			else
			{
				if (needsSpace && expr.shouldBeSeparatedOnLeft)
				{
					builder.append(' ')
				}
				val oldLength = builder.length
				expr.printWithArguments(argumentProvider, builder, indent)
				needsSpace =
					expr.shouldBeSeparatedOnRight && builder.length != oldLength
			}
		}
		assert(!argumentProvider.hasNext())
		builder.append('»')
	}

	override val shouldBeSeparatedOnLeft: Boolean get() = false

	override val shouldBeSeparatedOnRight: Boolean get() = false

	override fun mightBeEmpty(phraseType: A_Type): Boolean
	{
		// This group can consume no tokens iff it can have zero repetitions.
		val tupleType = phraseType.phraseTypeExpressionType
		assert(tupleType.isTupleType)
		return tupleType.sizeRange.lowerBound.equalsInt(0)
	}

	override fun checkListStructure(phrase: A_Phrase): Boolean
	{
		// Don't check inside literals.  That's the job of type checking.
		if (phrase.phraseKindIsUnder(LITERAL_PHRASE)) return true
		// Must not permute the guillemet group repetition itself.
		if (phrase.phraseKindIsUnder(PERMUTED_LIST_PHRASE)) return false
		if (!phrase.phraseKindIsUnder(LIST_PHRASE)) return false
		return phrase.expressionsTuple.all { subphrase ->
			checkOneRepeatedSublistStructure(subphrase)
		}
	}

	/**
	 * Recursively check one occurrence of a [Group] repetition for validly
	 * formed and permuted lists.
	 *
	 * @param phrase
	 *   The phrase being supplied for one repetition of this group.
	 */
	private fun checkOneRepeatedSublistStructure(phrase: A_Phrase): Boolean
	{
		// Don't check inside literals.  That's the job of type checking.
		if (!needsDoubleWrapping)
		{
			// Simple case of 1 arg before and 0 args after '‡'.
			return beforeDagger.yielders[0].checkListStructure(phrase)
		}
		// It's double-wrapped (i.e., this repetition is itself a list).
		if (phrase.phraseKindIsUnder(LITERAL_PHRASE)) return true
		if (!phrase.phraseKindIsUnder(LIST_PHRASE)) return false
		val subphrases = phrase.expressionsTuple
		val subphrasesSize = subphrases.tupleSize
		if (subphrasesSize == beforeDagger.yielders.size)
		{
			// Only check the left side.  Note - it might require a permutation.
			return beforeDagger.checkListStructure(phrase)
		}
		// Both sides should be present.
		if (subphrasesSize !=
			beforeDagger.yielders.size + afterDagger.yielders.size)
		{
			// Wrong size.
			return false
		}
		val leftSize = beforeDagger.yielders.size
		if (beforeDagger.isReordered || afterDagger.isReordered)
		{
			// It must be permuted.
			if (!phrase.phraseKindIsUnder(PERMUTED_LIST_PHRASE)) return false
			val actualPermutation = phrase.permutation
			val expectedPermutation = beforeDagger.permutation.toMutableList()
			afterDagger.permutation.mapTo(expectedPermutation) { it + leftSize }
			if (!tupleFromIntegerList(expectedPermutation)
					.equals(actualPermutation))
			{
				// The permutation isn't correct.
				return false
			}
		}
		else
		{
			// It must not be permuted.
			if (phrase.phraseKindIsUnder(PERMUTED_LIST_PHRASE)) return false
		}
		// Check everything on the left.
		for (i in 1..leftSize)
		{
			if (!beforeDagger.yielders[i - 1]
					.checkListStructure(subphrases.tupleAt(i)))
			{
				return false
			}
		}
		// Check everything on the right.
		for (i in 1..afterDagger.yielders.size)
		{
			if (!afterDagger.yielders[i - 1]
					.checkListStructure(subphrases.tupleAt(i + leftSize)))
			{
				return false
			}
		}
		// It all checked out structurally.
		return true
	}
}
