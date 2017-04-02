/**
 * InstructionGenerator.java
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
import com.avail.annotations.InnerAccess;
import com.avail.compiler.ParsingOperation;

import com.avail.descriptor.A_Tuple;
import com.avail.descriptor.TupleDescriptor;
import com.avail.utility.Pair;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;

import static com.avail.compiler.ParsingOperation.*;

/**
 * {@code InstructionGenerator} is used by {@code MessageSplitter} to accumulate
 * the sequence of {@linkplain ParsingOperation instructions} that can be used
 * directly for parsing.  The instructions are encoded as a tuple of
 * non-negative integers.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
class InstructionGenerator
{

	/**
	 * A {@code Label} can be created via the default public constructor.
	 * It can be {@linkplain #emit(Label) emitted} to the
	 * {@link InstructionGenerator}, and it can be the argument of another
	 * instruction {@linkplain #emit(Expression, ParsingOperation, Label)
	 * emitted} as an operand of an instruction, both before and after the Label
	 * itself has been emitted.  The Label must be emitted exactly once if it is
	 * used as an operand.
	 *
	 * <p>Besides the default constructor, there are no public methods.</p>
	 */
	static class Label
	{
		/**
		 * The one-based index of the label, where -1 indicates the label has
		 * not yet been emitted.
		 */
		@InnerAccess int position = -1;

		/**
		 * The operations that need to be fixed up when this label is emitted.
		 * Each operation is encoded as a pair consisting of the index of the
		 * instruction to be fixed, and the ParsingOperation to emit at that
		 * location after combining with this label's position to form a parsing
		 * instruction.
		 */
		@InnerAccess List<Pair<Integer, ParsingOperation>> operationsToFix =
			new ArrayList<>();

		/**
		 * Answer whether an instruction using this label as an operand has been
		 * emitted.
		 *
		 * @return Whether this label has been used.
		 */
		public boolean isUsed ()
		{
			return position != -1 || !operationsToFix.isEmpty();
		}
	}

	/** The instructions generated so far. */
	private final List<Integer> instructions = new ArrayList<>();

	/**
	 * The {@link Expression} that produced the corresponding {@link
	 * #instructions}.
	 */
	private final List<Expression> expressionList = new ArrayList<>();

	/**
	 * Holds a sequence of (relocatable) instructions that will perform grammar
	 * and type checks, and sometimes a {@link ParsingOperation#APPEND_ARGUMENT}
	 * on an argument that has been parsed but not yet processed.  This allows
	 * faster checks (like token matching) to filter out incorrect matches,
	 * avoiding expensive type tests.
	 */
	private final List<Integer> delayedArgumentInstructions = new ArrayList<>();

	/**
	 * A {@link List} parallel to {@link #delayedArgumentInstructions}, which
	 * indicates the expression that produced each delayed instruction.
	 */
	private final List<Expression> delayedExpressionList = new ArrayList<>();

	private static final Integer placeholderInstruction = Integer.MIN_VALUE;

	/** Whether to emit case-insensitive keyword matches at the moment. */
	boolean caseInsensitive = false;

	/**
	 * The number of layers of lists that have been partially assembled at this
	 * point in the generated code.
	 */
	int partialListsCount = 1;

	/**
	 * Emit a {@link ParsingOperation} that takes no operand.
	 *
	 * @param operation The operandless {@link ParsingOperation} to emit.
	 */
	final void emit (
		final Expression expression,
		final ParsingOperation operation)
	{
		assert !(operation == APPEND_ARGUMENT
				|| operation == PERMUTE_LIST)
			|| delayedArgumentInstructions.isEmpty();
		expressionList.add(expression);
		instructions.add(operation.encoding());
	}

	/**
	 * Emit a {@link ParsingOperation} that takes an integer operand.
	 *
	 * @param operation
	 *        The {@link ParsingOperation} to emit with its operand.
	 */
	final void emit (
		final Expression expression,
		final ParsingOperation operation,
		final int operand)
	{
		expressionList.add(expression);
		instructions.add(operation.encoding(operand));
	}


	/**
	 * Emit a {@link ParsingOperation} that takes no operand, but only if the
	 * condition is true.
	 *
	 * @param operation The operandless {@link ParsingOperation} to emit.
	 */
	final void emitIf (
		final boolean condition,
		final Expression expression,
		final ParsingOperation operation)
	{
		if (condition)
		{
			emit(expression, operation);
		}
	}

	/**
	 * Emit a {@link ParsingOperation} that takes an integer operand, but only
	 * if the condition is true.
	 *
	 * @param operation
	 *        The {@link ParsingOperation} to emit with its operand.
	 */
	final void emitIf (
		final boolean condition,
		final Expression expression,
		final ParsingOperation operation,
		final int operand)
	{
		if (condition)
		{
			emit(expression, operation, operand);
		}
	}

	/**
	 * Emit a {@link ParsingOperation} that takes a {@link Label} operand.
	 *
	 * @param operation
	 *        The {@link ParsingOperation} to emit with its operand.
	 */
	final void emit (
		final Expression expression,
		final ParsingOperation operation,
		final Label label)
	{
		expressionList.add(expression);
		if (label.position == -1)
		{
			// Label is still unresolved.  Promise to resolve this when the
			// label is emitted.
			label.operationsToFix.add(
				new Pair<>(instructions.size() + 1, operation));
			instructions.add(placeholderInstruction);
		}
		else
		{
			// Label is already resolved.
			instructions.add(operation.encoding(label.position));
		}
	}

	/**
	 * Emit a label, pinning it to the current location in the instruction list.
	 *
	 * @param label The label to emit.
	 */
	final void emit (final Label label)
	{
		assert label.position == -1 : "Label was already emitted";
		label.position = instructions.size() + 1;
		for (final Pair<Integer, ParsingOperation> pair : label.operationsToFix)
		{
			assert instructions.get(pair.first() - 1)
				.equals(placeholderInstruction);
			if (pair.first() + 1 == label.position)
			{
				System.out.println("DEBUG: Operation target falls through.");
			}
			instructions.set(
				pair.first() - 1, pair.second().encoding(label.position));
		}
		label.operationsToFix.clear();
	}

	/**
	 * Record an argument post-processing instruction.  It won't actually be
	 * emitted into the instruction stream until as late as possible.
	 *
	 * <p>The instruction must be relocatable.</p>
	 *
	 * @param expression The expression that is emitting the instruction.
	 * @param operation The operation of the instruction to delay.
	 */
	final void emitDelayed (
		final Expression expression,
		final ParsingOperation operation)
	{
		delayedExpressionList.add(expression);
		delayedArgumentInstructions.add(operation.encoding());
	}

	/**
	 * Record an argument post-processing instruction.  It won't actually be
	 * emitted into the instruction stream until as late as possible.
	 *
	 * <p>The instruction must be relocatable.</p>
	 *
	 * @param expression The expression that is emitting the instruction.
	 * @param operation The operation of the instruction to delay.
	 * @param operand The operand of the instruction to delay.
	 */
	final void emitDelayed (
		final Expression expression,
		final ParsingOperation operation,
		final int operand)
	{
		delayedExpressionList.add(expression);
		delayedArgumentInstructions.add(operation.encoding(operand));
	}


	/**
	 * Flush any delayed instructions to the main instruction list.
	 */
	final void flushDelayed ()
	{
		if (!delayedArgumentInstructions.isEmpty())
		{
			expressionList.addAll(delayedExpressionList);
			instructions.addAll(delayedArgumentInstructions);
			delayedExpressionList.clear();
			delayedArgumentInstructions.clear();
		}
	}

	/**
	 * Emit instructions to create a list from the N most recently pushed
	 * phrases.  N may be zero.
	 *
	 * @param expression
	 *        The expression that is emitting the instruction.
	 * @param listSize
	 *        The number of phrases to pop from the parseStack and assemble into
	 *        a list to be pushed.
	 */
	final void emitWrapped (
		final Expression expression,
		final int listSize)
	{
		assert delayedArgumentInstructions.isEmpty();
		assert listSize >= 0;
		if (listSize == 0)
		{
			emit(expression, EMPTY_LIST);
		}
		else
		{
			emit(expression, WRAP_IN_LIST, listSize);
		}
	}

	/**
	 * Perform optimizations on the sequence of {@link ParsingOperation}s.
	 */
	void optimizeInstructions ()
	{
		hoistTokenParsing();
	}

	/**
	 * Re-order the instructions so that {@link ParsingOperation#PARSE_PART} and
	 * {@link ParsingOperation#PARSE_PART_CASE_INSENSITIVELY} occur as early as
	 * possible.
	 */
	private void hoistTokenParsing ()
	{
		final int instructionsCount = instructions.size();
		final BitSet branchTargets = new BitSet(instructionsCount);
		// Add branch/jump targets, assuming a null entry means it's just a
		// fall-through from the previous instruction.  As a simplification,
		// assume jumps fall through, even though they don't.
		for (final int instruction : instructions)
		{
			final ParsingOperation operation = decode(instruction);
			if (operation == JUMP || operation == BRANCH)
			{
				// Adjust to zero-based.
				final int target = operand(instruction) - 1;
				branchTargets.set(target);
			}
		}
		// Scan backward to allow backward bubbling PARSE_PARTs to travel as far
		// as possible without any looping.  We repeat the loop to allow
		// *sequences* of PARSE_PARTs to propagate backward.
		boolean changed = true;
		while (changed)
		{
			changed = false;
			// Don't visit i=0, as it has no predecessors.
			for (int i = instructionsCount - 1; i >= 1; i--)
			{
				if (!branchTargets.get(i))
				{
					// It's not a branch target.
					final int instruction = instructions.get(i);
					final ParsingOperation operation = decode(instruction);
					if (operation == PARSE_PART
						|| operation == PARSE_PART_CASE_INSENSITIVELY)
					{
						// Swap it leftward if it commutes.
						final int priorInstruction = instructions.get(i - 1);
						final ParsingOperation priorOperation =
							decode(priorInstruction);
						if (priorOperation.commutesWithParsePart())
						{
							instructions.set(i, priorInstruction);
							instructions.set(i - 1, instruction);
							final Expression temp = expressionList.get(i);
							expressionList.set(i, expressionList.get(i - 1));
							expressionList.set(i - 1, temp);
							changed = true;
						}
					}
				}
			}
		}
	}

	/**
	 * Answer the {@link A_Tuple tuple} of generated instructions.
	 *
	 * @return An avail tuple of integers.
	 */
	final A_Tuple instructionsTuple ()
	{
		assert !instructions.contains(placeholderInstruction)
			: "A placeholder instruction using a label was not resolved";
		assert instructions.size() == expressionList.size();
		assert delayedExpressionList.isEmpty();
		return TupleDescriptor.fromIntegerList(instructions).makeShared();
	}

	/**
	 * Answer the {@linkplain #expressionList expressions} that correspond with
	 * the {@linkplain #instructions} list.
	 *
	 * @return An immutable list of {@link Expression}s.
	 */
	final List<Expression> expressionList ()
	{
		assert instructions.size() == expressionList.size();
		return Collections.unmodifiableList(expressionList);
	}
}
