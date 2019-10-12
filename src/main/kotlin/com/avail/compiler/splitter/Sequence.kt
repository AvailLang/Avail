/*
 * Sequence.kt
 * Copyright © 1993-2019, The Avail Foundation, LLC.
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
package com.avail.compiler.splitter

import com.avail.compiler.ParsingOperation.*
import com.avail.compiler.splitter.MessageSplitter.Companion.circledNumberCodePoint
import com.avail.compiler.splitter.MessageSplitter.Companion.indexForPermutation
import com.avail.compiler.splitter.MessageSplitter.Companion.throwMalformedMessageException
import com.avail.compiler.splitter.MessageSplitter.Companion.throwSignatureException
import com.avail.compiler.splitter.WrapState.*
import com.avail.descriptor.A_Phrase
import com.avail.descriptor.A_Type
import com.avail.descriptor.ListPhraseTypeDescriptor.emptyListPhraseType
import com.avail.descriptor.TupleDescriptor.tupleFromIntegerList
import com.avail.descriptor.TupleTypeDescriptor
import com.avail.exceptions.AvailErrorCode
import com.avail.exceptions.AvailErrorCode.*
import com.avail.exceptions.MalformedMessageException
import com.avail.exceptions.SignatureException
import com.avail.utility.Pair
import java.util.*

/**
 * A `Sequence` is the juxtaposition of any number of other [Expression]s.  It
 * is not itself a repetition, but it can be the left or right half of a [Group]
 * (bounded by the double-dagger (‡)).
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @constructor
 *
 * Create a `Sequence` with no elements.
 *
 * @param positionInName
 *   The position that this `Sequence` begins within the method name.
 */
internal class Sequence constructor(positionInName: Int)
: Expression(positionInName)
{
	/** The sequence of expressions that I comprise. */
	val expressions: MutableList<Expression> = ArrayList()

	/**
	 * Which of my [expressions] is an argument, ellipsis, or group? These are
	 * in the order they occur in the `expressions` list.
	 */
	val yielders: MutableList<Expression> = ArrayList()

	/**
	 * My one-based permutation that takes argument expressions from the order
	 * in which they occur to the order in which they are bound to arguments at
	 * a call site.
	 */
	val permutedYielders: MutableList<Int> = ArrayList()

	/**
	 * A three-state indicator of whether my argument components should be
	 * reordered.  If `null`, a decision has not yet been made, either during
	 * parsing (because an argument/group has not yet been encountered), or
	 * because this `Sequence` has no arguments or subgroups that act as
	 * arguments.  If [java.lang.Boolean.TRUE], then all argument positions so
	 * far have specified reordering (by using circled numbers), and if
	 * [java.lang.Boolean.FALSE], then no arguments so far have specified
	 * reordering.
	 */
	var yieldersAreReordered: Boolean? = null

	override val isLowerCase: Boolean
		get() = expressions.stream().allMatch { it.isLowerCase }

	/** A cache of places at which code splitting can take place.  */
	@Volatile
	private var cachedRunsForCodeSplitting: List<List<Pair<Expression, Int>>>? =
		null

	/**
	 * Add an [expression][Expression] to the `Sequence`.
	 *
	 * @param e
	 *   The expression to add.
	 * @throws MalformedMessageException
	 *   If the absence or presence of argument numbering would be inconsistent
	 *   within this `Sequence`.
	 */
	@Throws(MalformedMessageException::class)
	fun addExpression(e: Expression)
	{
		expressions.add(e)
		if (e.yieldsValue)
		{
			yielders.add(e)
		}
		if (e.canBeReordered)
		{
			if (yieldersAreReordered !== null
				&& yieldersAreReordered == (e.explicitOrdinal == -1))
			{
				throwMalformedMessageException(
					E_INCONSISTENT_ARGUMENT_REORDERING,
					"The sequence of subexpressions before or after a "
						+ "double-dagger (‡) in a group must have either all "
						+ "or none of its arguments or direct subgroups numbered "
						+ "for reordering")
			}
			yieldersAreReordered = e.explicitOrdinal != -1
		}
	}

	@Deprecated("Not applicable to Sequence")
	override val yieldsValue: Boolean
		get()
		{
			assert(false) { "Should not ask sequence if it yields a value" }
			return false
		}

	override val underscoreCount: Int
		get() = expressions.stream().mapToInt { it!!.underscoreCount }.sum()

	override fun extractSectionCheckpointsInto(
		sectionCheckpoints: MutableList<SectionCheckpoint>)
	{
		expressions.forEach { expression ->
			expression.extractSectionCheckpointsInto(sectionCheckpoints)
		}
	}

	/**
	 * Check that the given type signature is appropriate for this top-level
	 * sequence. If not, throw a [SignatureException].
	 *
	 * @param argumentType
	 *   A [tuple type][TupleTypeDescriptor] describing the types of arguments
	 *   that a method being added will accept.
	 * @param sectionNumber
	 *   Which [SectionCheckpoint] section marker this list of argument types
	 *   are being validated against.  To validate the final method or macro
	 *   body rather than a prefix function, use any value greater than
	 *   [MessageSplitter.numberOfSectionCheckpoints].
	 * @throws SignatureException
	 *   If the argument type is inappropriate.
	 */
	@Throws(SignatureException::class)
	fun checkRootType(argumentType: A_Type, sectionNumber: Int) =
		checkTypeWithErrorCode(
			argumentType, sectionNumber, E_INCORRECT_NUMBER_OF_ARGUMENTS)

	@Throws(SignatureException::class)
	override fun checkType(argumentType: A_Type, sectionNumber: Int) =
		checkTypeWithErrorCode(
			argumentType, sectionNumber, E_INCORRECT_TYPE_FOR_GROUP)

	/**
	 * Check if the given type is suitable for holding values generated by this
	 * sequence.
	 *
	 * @param argumentType
	 *   The [tuple type][TupleTypeDescriptor] describing the types of arguments
	 *   expected for this `Sequence`.
	 * @param sectionNumber
	 *   Which [SectionCheckpoint] section marker this list of argument types
	 *   are being validated against.  To validate the final method or macro
	 *   body rather than a prefix function, use any value greater than
	 *   [MessageSplitter.numberOfSectionCheckpoints].
	 * @param errorCode
	 *   The [AvailErrorCode] to include in a [SignatureException] if the
	 *   argument count is wrong.
	 * @throws SignatureException
	 *   If the signature is not appropriate.
	 */
	@Throws(SignatureException::class)
	fun checkTypeWithErrorCode(
		argumentType: A_Type,
		sectionNumber: Int,
		errorCode: AvailErrorCode)
	{
		// Always expect a tuple of solutions here.
		if (argumentType.isBottom)
		{
			// Method argument type should not be bottom.
			throwSignatureException(E_INCORRECT_ARGUMENT_TYPE)
		}

		if (!argumentType.isTupleType)
		{
			// The sequence produces a tuple.
			throwSignatureException(E_INCORRECT_TYPE_FOR_GROUP)
		}

		// Make sure the tuple of argument types are suitable for the
		// argument positions that I comprise.  Take the argument reordering
		// permutation into account if present.
		val expected = yielders.size
		val sizes = argumentType.sizeRange()
		if (!sizes.lowerBound().equalsInt(expected)
			|| !sizes.upperBound().equalsInt(expected))
		{
			throwSignatureException(errorCode)
		}
		if (yieldersAreReordered === java.lang.Boolean.TRUE)
		{
			for (i in 1..expected)
			{
				val argumentOrGroup = yielders[i - 1]
				val providedType =
					argumentType.typeAtIndex(permutedYielders[i - 1])
				assert(!providedType.isBottom)
				argumentOrGroup.checkType(providedType, sectionNumber)
			}
		}
		else
		{
			for (i in 1..expected)
			{
				val argumentOrGroup = yielders[i - 1]
				val providedType = argumentType.typeAtIndex(i)
				assert(!providedType.isBottom)
				argumentOrGroup.checkType(providedType, sectionNumber)
			}
		}
	}

	/**
	 * Analyze the sequence to find the appropriate ranges within which code
	 * splitting should be performed.  Code splitting allows chains of Optional
	 * expressions to turn into binary trees, with the non-optional that follows
	 * at the leaves.  Since the endpoints are unique, we can postpone pushing
	 * the constant boolean values (that indicate whether on Optional was
	 * present or not along that path) until just before merging control flow.
	 *
	 * Also capture the corresponding indices into the tuple type with each
	 * expression in each run.  A zero indicates that no type is consumed for
	 * that expression.
	 *
	 * @return
	 *   The runs of expressions within which to perform code splitting,
	 *   expressed as a list of lists of &lt;expression, typeIndex> pairs.
	 */
	private fun runsForCodeSplitting(): List<List<Pair<Expression, Int>>>
	{
		val cached = cachedRunsForCodeSplitting
		if (cached !== null)
		{
			return cached
		}
		val result = ArrayList<List<Pair<Expression, Int>>>()
		val currentRun = ArrayList<Pair<Expression, Int>>()
		var typeIndex = 0
		for (expression in expressions)
		{
			// Put the subexpression into one of the runs.
			if (expression.hasSectionCheckpoints)
			{
				if (currentRun.isNotEmpty())
				{
					result.add(ArrayList(currentRun))
					currentRun.clear()
				}
				result.add(
					listOf(
						Pair(
							expression,
							if (expression.yieldsValue) ++typeIndex else 0)))
			}
			else
			{
				currentRun.add(
					Pair(
						expression,
						if (expression.yieldsValue) ++typeIndex else 0))
				if (expression !is Optional)
				{
					result.add(ArrayList(currentRun))
					currentRun.clear()
				}
			}
		}
		if (currentRun.isNotEmpty())
		{
			result.add(currentRun)
		}
		cachedRunsForCodeSplitting = result
		return result
	}

	/**
	 * Emit code to cause the given run of <expression, tuple-type-index> pairs
	 * to be emitted, starting at positionInRun.  Note that the arguments will
	 * initially be written in reverse order, but the outermost call of this
	 * method will reverse them.
	 *
	 * @param run
	 *   A list of <Expression, type-index> pairs to process together, defining
	 *   the boundary of code-splitting.
	 * @param positionInRun
	 *   Where in the run to start generating code.  Useful for recursive code
	 *   splitting.  It may be just past the end of the run.
	 * @param generator
	 *   Where to emit instructions.
	 * @param subexpressionsTupleType
	 *   A tuple type containing the expected phrase types for this entire
	 *   sequence.  Indexed by the second()s of the run pairs.
	 */
	private fun emitRunOn(
		run: List<Pair<Expression, Int>>,
		positionInRun: Int,
		generator: InstructionGenerator,
		subexpressionsTupleType: A_Type)
	{
		val runSize = run.size
		val pair = run[positionInRun]
		val expression = pair.first()
		val typeIndex = pair.second()
		val realTypeIndex =
			if (typeIndex != 0
					&& yieldersAreReordered === java.lang.Boolean.TRUE)
				permutedYielders[typeIndex - 1]
			else
				typeIndex
		val subexpressionType =
			if (typeIndex == 0)
				emptyListPhraseType()
			else
				subexpressionsTupleType.typeAtIndex(realTypeIndex)
		if (positionInRun == runSize - 1)
		{
			// We're on the last element of the run, or it's a singleton run.
			// Either way, just emit it (ending the recursion).
			expression.emitOn(
				subexpressionType, generator, SHOULD_NOT_PUSH_LIST)
		}
		else
		{
			(expression as Optional).emitInRunThen(generator) {
				emitRunOn(
					run,
					positionInRun + 1,
					generator,
					subexpressionsTupleType)
			}
			if (positionInRun == 0)
			{
				// Do the argument reversal at the outermost recursion.
				val lastElementPushed =
					run[runSize - 1].first().yieldsValue
				val permutationSize = runSize + if (lastElementPushed) 0 else -1
				if (permutationSize > 1)
				{
					generator.emit(this, REVERSE_STACK, permutationSize)
				}
			}
		}
	}

	/**
	 * Generate code to parse the sequence.  Use the passed [WrapState] to
	 * control whether the arguments/groups should be left on the stack
	 * ([WrapState.SHOULD_NOT_PUSH_LIST]), assembled into a list
	 * ([WrapState.NEEDS_TO_PUSH_LIST]), or concatenated onto an existing list
	 * ([WrapState.PUSHED_LIST]).
	 */
	override fun emitOn(
		phraseType: A_Type,
		generator: InstructionGenerator,
		wrapState: WrapState): WrapState
	{
		val subexpressionsTupleType = phraseType.subexpressionsTupleType()
		var argIndex = 0
		var ungroupedArguments = 0
		var listIsPushed = wrapState === PUSHED_LIST
		val allRuns = runsForCodeSplitting()
		for (run in allRuns)
		{
			val runSize = run.size
			val lastInRun = run[runSize - 1].first()
			if (lastInRun.hasSectionCheckpoints)
			{
				assert(runSize == 1)
				generator.flushDelayed()
				if (listIsPushed)
				{
					if (ungroupedArguments == 1)
					{
						generator.emit(this, APPEND_ARGUMENT)
					}
					else if (ungroupedArguments > 1)
					{
						generator.emitWrapped(this, ungroupedArguments)
						generator.emit(this, CONCATENATE)
					}
					ungroupedArguments = 0
				}
				else if (wrapState === NEEDS_TO_PUSH_LIST)
				{
					generator.emitWrapped(this, ungroupedArguments)
					listIsPushed = true
					ungroupedArguments = 0
				}
			}
			emitRunOn(run, 0, generator, subexpressionsTupleType)
			val argsInRun = if (lastInRun.yieldsValue)
				runSize
			else
				runSize - 1
			ungroupedArguments += argsInRun
			argIndex += argsInRun
		}
		generator.flushDelayed()
		if (listIsPushed)
		{
			if (ungroupedArguments == 1)
			{
				generator.emit(this, APPEND_ARGUMENT)
			}
			else if (ungroupedArguments > 1)
			{
				generator.emitWrapped(this, ungroupedArguments)
				generator.emit(this, CONCATENATE)
			}
		}
		else if (wrapState === NEEDS_TO_PUSH_LIST)
		{
			generator.emitWrapped(this, ungroupedArguments)
			listIsPushed = true
		}
		assert(
			listIsPushed
				|| wrapState === SHOULD_NOT_PUSH_LIST
				|| wrapState === SHOULD_NOT_HAVE_ARGUMENTS)
		assert(yielders.size == argIndex)
		assert(
			subexpressionsTupleType.sizeRange().lowerBound().equalsInt(
				argIndex))
		assert(
			subexpressionsTupleType.sizeRange().upperBound().equalsInt(
				argIndex))
		if (yieldersAreReordered === java.lang.Boolean.TRUE)
		{
			assert(listIsPushed)
			val permutationTuple = tupleFromIntegerList(permutedYielders)
			val permutationIndex = indexForPermutation(permutationTuple)
			// This sequence was already collected into a list phrase as the
			// arguments/groups were parsed.  Permute the list.
			generator.flushDelayed()
			generator.emit(this, PERMUTE_LIST, permutationIndex)
		}
		return if (wrapState === NEEDS_TO_PUSH_LIST) PUSHED_LIST else wrapState
	}

	override fun toString(): String
	{
		val builder = StringBuilder()
		builder.append("Sequence(")
		var first = true
		for (e in expressions)
		{
			if (!first)
			{
				builder.append(", ")
			}
			builder.append(e)
			if (e.canBeReordered && e.explicitOrdinal != -1)
			{
				builder.appendCodePoint(
					circledNumberCodePoint(e.explicitOrdinal))
			}
			first = false
		}
		builder.append(')')
		return builder.toString()
	}

	override fun printWithArguments(
		arguments: Iterator<A_Phrase>?,
		builder: StringBuilder,
		indent: Int)
	{
		assert(arguments !== null)
		var needsSpace = false
		for (expression in expressions)
		{
			if (expression.shouldBeSeparatedOnLeft && needsSpace)
			{
				builder.append(' ')
			}
			val oldLength = builder.length
			expression.printWithArguments(
				arguments, builder, indent)
			needsSpace =
				expression.shouldBeSeparatedOnRight
					&& builder.length != oldLength
		}
		assert(!arguments!!.hasNext())
	}

	override val shouldBeSeparatedOnLeft: Boolean
		get() =
			expressions.isNotEmpty() && expressions[0].shouldBeSeparatedOnLeft

	override val shouldBeSeparatedOnRight: Boolean
		get() =
			expressions.isNotEmpty() && expressions[expressions.size - 1]
				.shouldBeSeparatedOnRight

	/**
	 * Check that if ordinals were specified for my N argument positions, that
	 * they are all present and constitute a permutation of [1..N]. If not,
	 * throw a [MalformedMessageException].
	 *
	 * @throws MalformedMessageException
	 *   If the arguments have reordering numerals (circled numbers), but they
	 *   don't form a non-trivial permutation of [1..N].
	 */
	@Throws(MalformedMessageException::class)
	fun checkForConsistentOrdinals()
	{
		if (yieldersAreReordered !== java.lang.Boolean.TRUE)
		{
			return
		}
		val usedOrdinalsList = expressions
			.asSequence()
			.filter { it.canBeReordered }
			.map { it.explicitOrdinal }
			.toList()
		val size = usedOrdinalsList.size
		val sortedOrdinalsList = ArrayList(usedOrdinalsList)
		sortedOrdinalsList.sort()
		val usedOrdinalsSet = HashSet(usedOrdinalsList)
		if (usedOrdinalsSet.size < usedOrdinalsList.size
			|| sortedOrdinalsList[0] != 1
			|| sortedOrdinalsList[size - 1] != size
			|| usedOrdinalsList == sortedOrdinalsList)
		{
			// There may have been a duplicate, a lowest value other
			// than 1, a highest value other than the number of values,
			// or the permutation might be the identity permutation (not
			// allowed).  Note that if one of the arguments somehow
			// still had an ordinal of -1 then it will trigger (at
			// least) the lowest value condition.
			throwMalformedMessageException(
				E_INCONSISTENT_ARGUMENT_REORDERING,
				"The circled numbers for this clause must range from 1 to " +
					"the number of arguments/groups, but must not be in " +
					"ascending order (got $usedOrdinalsList)")
		}
		assert(permutedYielders.isEmpty())
		permutedYielders.addAll(usedOrdinalsList)
	}

	override fun mightBeEmpty(phraseType: A_Type): Boolean
	{
		val subexpressionsTupleType = phraseType.subexpressionsTupleType()
		var index = 0
		for (expression in expressions)
		{
			if (expression.yieldsValue)
			{
				index++
				val realTypeIndex =
					if (yieldersAreReordered === java.lang.Boolean.TRUE)
						permutedYielders[index - 1]
					else
						index
				val entryType =
					subexpressionsTupleType.typeAtIndex(realTypeIndex)
				if (!expression.mightBeEmpty(entryType))
				{
					return false
				}
			}
			else
			{
				if (!expression.mightBeEmpty(emptyListPhraseType()))
				{
					return false
				}
			}
		}
		return true
	}
}
