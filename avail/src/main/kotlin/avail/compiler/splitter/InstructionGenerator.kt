/*
 * InstructionGenerator.kt
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

import avail.compiler.ParsingOperation
import avail.compiler.ParsingOperation.APPEND_ARGUMENT
import avail.compiler.ParsingOperation.BRANCH_FORWARD
import avail.compiler.ParsingOperation.Companion.decode
import avail.compiler.ParsingOperation.Companion.operand
import avail.compiler.ParsingOperation.EMPTY_LIST
import avail.compiler.ParsingOperation.JUMP_BACKWARD
import avail.compiler.ParsingOperation.JUMP_FORWARD
import avail.compiler.ParsingOperation.PARSE_PART
import avail.compiler.ParsingOperation.PARSE_PART_CASE_INSENSITIVELY
import avail.compiler.ParsingOperation.PERMUTE_LIST
import avail.compiler.ParsingOperation.WRAP_IN_LIST
import avail.descriptor.tuples.A_Tuple
import avail.descriptor.tuples.TupleDescriptor.Companion.tupleFromIntegerList
import java.util.BitSet
import java.util.Collections

/**
 * `InstructionGenerator` is used by `MessageSplitter` to accumulate the
 * sequence of [instructions][ParsingOperation] that can be used directly for
 * parsing.  The instructions are encoded as a tuple of non-negative integers.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
internal class InstructionGenerator
{
	/** The instructions generated so far. */
	private val instructions = mutableListOf<Int>()

	/** The [Expression] that produced the corresponding [instructions]. */
	private val expressionList = mutableListOf<Expression>()

	/**
	 * Holds a sequence of (relocatable) instructions that will perform grammar
	 * and type checks, and sometimes a [ParsingOperation.APPEND_ARGUMENT] on an
	 * argument that has been parsed but not yet processed.  This allows faster
	 * checks (like token matching) to filter out incorrect matches, avoiding
	 * expensive type tests.
	 */
	private val delayedArgumentInstructions = mutableListOf<Int>()

	/**
	 * A [List] parallel to [delayedArgumentInstructions], which indicates the
	 * expression that produced each delayed instruction.
	 */
	private val delayedExpressionList = mutableListOf<Expression>()

	/** Whether to emit case-insensitive keyword matches at the moment. */
	var caseInsensitive = false
		private set

	/**
	 * Switch the generator to case-insensitive mode while executing the given
	 * [action].  Answer the result of running the action, if any.
	 */
	fun <O> beCaseInsensitiveWhile(action: ()->O): O
	{
		val old = caseInsensitive
		caseInsensitive = true
		return try
		{
			action()
		}
		finally
		{
			caseInsensitive = old
		}
	}

	/**
	 * The number of layers of lists that have been partially assembled at this
	 * point in the generated code.
	 */
	var partialListsCount = 1

	/**
	 * A `Label` can be created via the default public constructor. It can be
	 * [emitted][emit] to the [InstructionGenerator], and it can be the argument
	 * of another instruction [ emitted][emit] as an operand of an instruction,
	 * both before and after the Label itself has been emitted.  The Label must
	 * be emitted exactly once if it is used as an operand.
	 *
	 * Besides the default constructor, there are no public methods.
	 */
	internal class Label
	{
		/**
		 * The one-based index of the label, where -1 indicates the label has
		 * not yet been emitted.
		 */
		var position = -1

		/**
		 * The operations that need to be fixed up when this label is emitted.
		 * Each operation is encoded as a pair consisting of the index of the
		 * instruction to be fixed, and the ParsingOperation to emit at that
		 * location after combining with this label's position to form a parsing
		 * instruction.
		 */
		val operationsToFix = mutableListOf<Pair<Int, ParsingOperation>>()

		/**  Has an instruction using this label as an operand been emitted? */
		val isUsed: Boolean
			get() = position != -1 || operationsToFix.isNotEmpty()
	}

	/**
	 * Emit a [ParsingOperation] that takes no operand.
	 *
	 * @param operation
	 *   The operandless [ParsingOperation] to emit.
	 */
	fun emit(
		expression: Expression,
		operation: ParsingOperation)
	{
		assert(
			!(operation === APPEND_ARGUMENT
				|| operation === PERMUTE_LIST)
				|| delayedArgumentInstructions.isEmpty())
		expressionList.add(expression)
		instructions.add(operation.encoding)
	}

	/**
	 * Emit a [ParsingOperation] that takes an integer operand.
	 *
	 * @param operation
	 *   The [ParsingOperation] to emit with its operand.
	 */
	fun emit(
		expression: Expression,
		operation: ParsingOperation,
		operand: Int)
	{
		expressionList.add(expression)
		instructions.add(operation.encoding(operand))
	}

	/**
	 * Emit a [ParsingOperation] that takes no operand, but only if the
	 * condition is true.
	 *
	 * @param operation
	 *   The operandless [ParsingOperation] to emit.
	 */
	fun emitIf(
		condition: Boolean,
		expression: Expression,
		operation: ParsingOperation)
	{
		if (condition)
		{
			emit(expression, operation)
		}
	}

	/**
	 * Emit a [ParsingOperation] that takes a [Label] operand.
	 *
	 * @param operation
	 *   The [ParsingOperation] to emit with its operand.
	 */
	fun emit(
		expression: Expression,
		operation: ParsingOperation,
		label: Label)
	{
		assert(
			operation !== BRANCH_FORWARD
				&& operation !== JUMP_FORWARD
				&& operation !== JUMP_BACKWARD) {
			"Use emitJumpForward() etc. to emit jumps and branches"
		}
		expressionList.add(expression)
		if (label.position == -1)
		{
			// Label is still unresolved.  Promise to resolve this when the
			// label is emitted.
			label.operationsToFix.add(instructions.size + 1 to operation)
			instructions.add(placeholderInstruction)
		}
		else
		{
			// Label is already resolved.
			instructions.add(operation.encoding(label.position))
		}
	}

	/**
	 * Emit a label, pinning it to the current location in the instruction list.
	 *
	 * @param label
	 *   The label to emit.
	 */
	fun emit(label: Label)
	{
		assert(label.position == -1) { "Label was already emitted" }
		label.position = instructions.size + 1
		for (pair in label.operationsToFix)
		{
			assert(instructions[pair.first - 1] == placeholderInstruction)
			if (pair.first + 1 == label.position)
			{
				println("DEBUG: Operation target falls through.")
			}
			instructions[pair.first - 1] =
				pair.second.encoding(label.position)
		}
		label.operationsToFix.clear()
	}

	/**
	 * Emit a [jump-forward&#32;instruction][ParsingOperation.JUMP_FORWARD]. The
	 * target label must not have been emitted yet.
	 *
	 * @param label
	 *   The label to jump forward to.
	 */
	fun emitJumpForward(expression: Expression, label: Label)
	{
		assert(label.position == -1) {
			"Forward jumps must actually be forward"
		}
		expressionList.add(expression)
		// Promise to resolve this when the label is emitted.
		label.operationsToFix.add(instructions.size + 1 to JUMP_FORWARD)
		instructions.add(placeholderInstruction)
	}

	/**
	 * Emit a [jump-backward&#32;instruction][ParsingOperation.JUMP_BACKWARD].
	 * The target label must have been emitted already.
	 *
	 * @param label
	 *   The label to jump backward to.
	 */
	fun emitJumpBackward(
		expression: Expression,
		label: Label)
	{
		assert(label.position != -1) {
			"Backward jumps must actually be backward"
		}
		expressionList.add(expression)
		instructions.add(JUMP_BACKWARD.encoding(label.position))
	}

	/**
	 * Emit a [branch-forward][ParsingOperation.BRANCH_FORWARD].  The target
	 * label must not have been emitted yet.
	 *
	 * @param label
	 *   The label to branch forward to.
	 */
	fun emitBranchForward(expression: Expression, label: Label)
	{
		assert(label.position == -1) { "Branches must be forward" }
		expressionList.add(expression)
		// Promise to resolve this when the label is emitted.
		label.operationsToFix.add(instructions.size + 1 to BRANCH_FORWARD)
		instructions.add(placeholderInstruction)
	}

	/**
	 * Record an argument post-processing instruction.  It won't actually be
	 * emitted into the instruction stream until as late as possible.
	 *
	 * The instruction must be relocatable.
	 *
	 * @param expression
	 *   The expression that is emitting the instruction.
	 * @param operation
	 *   The operation of the instruction to delay.
	 */
	fun emitDelayed(expression: Expression, operation: ParsingOperation)
	{
		delayedExpressionList.add(expression)
		delayedArgumentInstructions.add(operation.encoding)
	}

	/**
	 * Record an argument post-processing instruction.  It won't actually be
	 * emitted into the instruction stream until as late as possible.
	 *
	 * The instruction must be relocatable.
	 *
	 * @param expression
	 *   The expression that is emitting the instruction.
	 * @param operation
	 *   The operation of the instruction to delay.
	 * @param operand
	 *   The operand of the instruction to delay.
	 */
	fun emitDelayed(
		expression: Expression,
		operation: ParsingOperation,
		operand: Int)
	{
		delayedExpressionList.add(expression)
		delayedArgumentInstructions.add(operation.encoding(operand))
	}

	/**
	 * Flush any delayed instructions to the main instruction list.
	 */
	fun flushDelayed()
	{
		if (delayedArgumentInstructions.isNotEmpty())
		{
			expressionList.addAll(delayedExpressionList)
			instructions.addAll(delayedArgumentInstructions)
			delayedExpressionList.clear()
			delayedArgumentInstructions.clear()
		}
	}

	/**
	 * Emit instructions to create a list from the N most recently pushed
	 * phrases.  N may be zero.
	 *
	 * @param expression
	 *   The expression that is emitting the instruction.
	 * @param listSize
	 *   The number of phrases to pop from the parseStack and assemble into a
	 *   list to be pushed.
	 */
	fun emitWrapped(expression: Expression, listSize: Int)
	{
		assert(delayedArgumentInstructions.isEmpty())
		assert(listSize >= 0)
		if (listSize == 0)
		{
			emit(expression, EMPTY_LIST)
		}
		else
		{
			emit(expression, WRAP_IN_LIST, listSize)
		}
	}

	/**
	 * Perform optimizations on the sequence of [ParsingOperation]s.
	 */
	fun optimizeInstructions() = hoistTokenParsing()

	/**
	 * Re-order the instructions so that [ParsingOperation.PARSE_PART] and
	 * [ParsingOperation.PARSE_PART_CASE_INSENSITIVELY] occur as early as
	 * possible.
	 */
	private fun hoistTokenParsing()
	{
		val instructionsCount = instructions.size
		val branchTargets = BitSet(instructionsCount)
		// Add branch/jump targets, assuming a null entry means it's just a
		// fall-through from the previous instruction.  As a simplification,
		// assume jumps fall through, even though they don't.
		for (instruction in instructions)
		{
			val operation = decode(instruction)
			if (operation === JUMP_FORWARD
				|| operation === JUMP_BACKWARD
				|| operation === BRANCH_FORWARD)
			{
				// Adjust to zero-based.
				val target = operand(instruction) - 1
				branchTargets.set(target)
			}
		}
		// Scan backward to allow backward bubbling PARSE_PARTs to travel as far
		// as possible without any looping.  We repeat the loop to allow
		// *sequences* of PARSE_PARTs to propagate backward.
		var changed = true
		while (changed)
		{
			changed = false
			// Don't visit i=0, as it has no predecessors.
			for (i in instructionsCount - 1 downTo 1)
			{
				if (!branchTargets.get(i))
				{
					// It's not a branch target.
					val instruction = instructions[i]
					val operation = decode(instruction)
					if (operation === PARSE_PART
						|| operation === PARSE_PART_CASE_INSENSITIVELY)
					{
						// Swap it leftward if it commutes.
						val priorInstruction = instructions[i - 1]
						val priorOperation = decode(priorInstruction)
						if (priorOperation.commutesWithParsePart)
						{
							instructions[i] = priorInstruction
							instructions[i - 1] = instruction
							val temp = expressionList[i]
							expressionList[i] = expressionList[i - 1]
							expressionList[i - 1] = temp
							changed = true
						}
					}
				}
			}
		}
	}

	/**
	 * Answer the [tuple][A_Tuple] of generated instructions.
	 *
	 * @return
	 *   An avail tuple of integers.
	 */
	fun instructionsTuple(): A_Tuple
	{
		assert(!instructions.contains(placeholderInstruction)) {
			"A placeholder instruction using a label was not resolved"
		}
		assert(instructions.size == expressionList.size)
		assert(delayedExpressionList.isEmpty())
		return tupleFromIntegerList(instructions).makeShared()
	}

	/**
	 * Answer the [expressions][expressionList] that correspond with the
	 * [instructions] list.
	 *
	 * @return
	 *   An immutable list of [Expression]s.
	 */
	fun expressionList(): List<Expression>
	{
		assert(instructions.size == expressionList.size)
		return Collections.unmodifiableList(expressionList)
	}

	companion object
	{
		private const val placeholderInstruction = Integer.MIN_VALUE
	}
}
