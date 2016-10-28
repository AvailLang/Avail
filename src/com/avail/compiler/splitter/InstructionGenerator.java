/**
 * InstructionGenerator.java
 * Copyright © 1993-2016, The Avail Foundation, LLC.
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

import static com.avail.compiler.ParsingOperation.JUMP;
import static com.avail.compiler.ParsingOperation.BRANCH;
import static com.avail.compiler.ParsingOperation.PARSE_PART;
import static com.avail.compiler.ParsingOperation.PARSE_PART_CASE_INSENSITIVELY;
import com.avail.descriptor.A_Tuple;
import com.avail.descriptor.TupleDescriptor;
import com.avail.utility.Pair;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;

/**
 * {@code InstructionGenerator} is used by {@code MessageSplitter} to
 * accumulate the sequence of {@linkplain ParsingOperation instructions} that
 * can be used directly for parsing.  The instructions are encoded as a tuple
 * of non-negative integers.
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
		@InnerAccess int position = -1;
		@InnerAccess List<Pair<Integer, ParsingOperation>> operationsToFix =
			new ArrayList<>();
	}

	/** The instructions generated so far. */
	private final List<Integer> instructions = new ArrayList<>();

	/**
	 * The innermost {@link Expression} that was active for the corresponding
	 * {@link #instructions}.
	 */
	private final List<Expression> expressionList = new ArrayList<>();

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
	 * Re-order the instructions so that {@link ParsingOperation#PARSE_PART} and
	 * {@link ParsingOperation#PARSE_PART_CASE_INSENSITIVELY} occur as early as
	 * possible.
	 */
	void optimizeInstructions ()
	{
		final int instructionsCount = instructions.size();
		final BitSet branchTargets = new BitSet(instructionsCount);
		// Add branch/jump targets, assuming a null entry means it's just a
		// fall-through from the previous instruction.  As a simplification,
		// assume jumps fall through, even though they don't.
		for (final int instruction : instructions)
		{
			final ParsingOperation operation =
				ParsingOperation.decode(instruction);
			if (operation == JUMP || operation == BRANCH)
			{
				// Adjust to zero-based.
				final int target = ParsingOperation.operand(instruction) - 1;
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
					final ParsingOperation operation =
						ParsingOperation.decode(instruction);
					if (operation == PARSE_PART
						|| operation == PARSE_PART_CASE_INSENSITIVELY)
					{
						// Swap it leftward if it commutes.
						final int priorInstruction = instructions.get(i - 1);
						final ParsingOperation priorOperation =
							ParsingOperation.decode(priorInstruction);
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
