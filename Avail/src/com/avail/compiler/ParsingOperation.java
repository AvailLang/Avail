/**
 * ParsingOperation.java
 * Copyright © 1993-2012, Mark van Gulik and Todd L Smith.
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

package com.avail.compiler;

import static com.avail.compiler.ParsingConversionRule.*;
import java.util.*;
import com.avail.annotations.NotNull;
import com.avail.descriptor.*;

/**
 * {@code ParsingOperation} describes the operations available for parsing Avail
 * message names.
 *
 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 */
public enum ParsingOperation
{
	/*
	 * Arity zero (0).
	 */

	/**
	 * {@code 0} - Parse an argument of a message send.
	 */
	parseArgument(0)
	{
		@Override
		public int encodingForOperand (final int operand)
		{
			throw new UnsupportedOperationException();
		}
	},

	/**
	 * {@code 1} - Push a new {@linkplain TupleNodeDescriptor list} that
	 * contains an {@linkplain TupleDescriptor#empty() empty tuple} of
	 * {@linkplain ParseNodeDescriptor phrases} onto the parse stack.
	 */
	newList(1)
	{
		@Override
		public int encodingForOperand (final int operand)
		{
			throw new UnsupportedOperationException();
		}
	},

	/**
	 * {@code 2} - Pop an argument from the parse stack of the current
	 * potential message send. Pop a {@linkplain TupleNodeDescriptor list} from
	 * the parse stack. Append the argument to the list. Push the resultant list
	 * onto the parse stack.
	 */
	appendArgument(2)
	{
		@Override
		public int encodingForOperand (final int operand)
		{
			throw new UnsupportedOperationException();
		}
	},

	/**
	 * {@code 3} - Push a {@linkplain MarkerNodeDescriptor marker} representing
	 * the current parse position onto the parse stack.
	 */
	saveParsePosition(3)
	{
		@Override
		public int encodingForOperand (final int operand)
		{
			throw new UnsupportedOperationException();
		}
	},

	/**
	 * {@code 4} - Underpop(1) a {@linkplain MarkerNodeDescriptor marker}
	 * representing a saved parse position from the parse stack.
	 */
	discardSavedParsePosition(4)
	{
		@Override
		public int encodingForOperand (final int operand)
		{
			throw new UnsupportedOperationException();
		}
	},

	/**
	 * {@code 5} - Underpop(1) the parse stack for a {@linkplain
	 * MarkerNodeDescriptor marker} representing a saved parse position. Compare
	 * the saved parse position against the current parse position. Abort the
	 * parse if no progress has been made. Otherwise underpush(1) a marker that
	 * represents the current parse position.
	 */
	ensureParseProgress(5)
	{
		@Override
		public int encodingForOperand (final int operand)
		{
			throw new UnsupportedOperationException();
		}
	},

	/**
	 * {@code 6} - Parse a {@linkplain TokenDescriptor raw token}.
	 */
	parseRawToken(6)
	{
		@Override
		public int encodingForOperand (final int operand)
		{
			throw new UnsupportedOperationException();
		}
	},

	/*
	 * Arity one (1).
	 */

	/**
	 * {@code 8*N+0} - Branch to instruction N. Attempt to continue parsing at
	 * each of the next instruction and instruction N.
	 */
	branch(0)
	{
		@Override
		public int encoding ()
		{
			throw new UnsupportedOperationException();
		}

		@Override
		public @NotNull List<Integer> successorPcs (
			final int instruction,
			final int currentPc)
		{
			return Arrays.asList(operand(instruction), currentPc + 1);
		}
	},

	/**
	 * {@code 8*N+1} - Jump to instruction N. Attempt to continue parsing only
	 * at instruction N.
	 */
	jump(1)
	{
		@Override
		public int encoding ()
		{
			throw new UnsupportedOperationException();
		}

		@Override
		public @NotNull List<Integer> successorPcs (
			final int instruction,
			final int currentPc)
		{
			return Collections.singletonList(operand(instruction));
		}
	},

	/**
	 * {@code 8*N+2} - Parse the Nth {@linkplain MessageSplitter#messageParts
	 * message part} of the current message. This will be a specific {@linkplain
	 * TokenDescriptor token}.
	 */
	parsePart(2)
	{
		@Override
		public int encoding ()
		{
			throw new UnsupportedOperationException();
		}

		@Override
		public int keywordIndex (final int instruction)
		{
			return operand(instruction);
		}
	},

	/**
	 * {@code 8*N+3} - Apply grammatical restrictions to the Nth leaf argument
	 * (underscore/ellipsis) of the current message.
	 */
	checkArgument(3)
	{
		@Override
		public int encoding ()
		{
			throw new UnsupportedOperationException();
		}

		@Override
		public int checkArgumentIndex (final int instruction)
		{
			return operand(instruction);
		}
	},

	/**
	 * {@code 8*N+4} - Pop an argument from the parse stack and apply the
	 * {@linkplain ParsingConversionRule conversion rule} specified by N.
	 */
	convert(4)
	{
		@Override
		public int encoding ()
		{
			throw new UnsupportedOperationException();
		}

		@Override
		public @NotNull ParsingConversionRule conversionRule (
			final int instruction)
		{
			return ruleNumber(operand(instruction));
		}
	};

	/** The number of distinct instructions supported by the coding scheme. */
	static final int distinctInstructions = 8;

	/** The modulus that represents the operation uniquely for its arity. */
	private final int modulus;

	/**
	 * Construct a new ({@code 0}) {@link ParsingOperation}.
	 *
	 * @param modulus
	 *        The modulus that represents the operation uniquely for its arity.
	 */
	private ParsingOperation (final int modulus)
	{
		this.modulus = modulus;
	}

	/**
	 * Answer the instruction coding of the receiver.
	 *
	 * @return The instruction coding.
	 */
	public int encoding ()
	{
		return modulus;
	}

	/**
	 * Answer the instruction coding of the receiver for the given operand. The
	 * receiver must be arity one ({@code 1}).
	 *
	 * @param operand The operand.
	 * @return The instruction coding.
	 */
	public int encodingForOperand (final int operand)
	{
		return distinctInstructions * operand + modulus;
	}

	/**
	 * Answer the operand given a coded instruction (that represents the same
	 * operation as the receiver).
	 *
	 * @param instruction A coded instruction.
	 * @return The operand.
	 */
	public int operand (final int instruction)
	{
		return instruction >> 3;
	}

	/**
	 * Assume that the instruction encodes an operand that represents a
	 * {@linkplain MessageSplitter#messageParts message part} index: answer the
	 * operand.
	 *
	 * @param instruction A coded instruction.
	 * @return The message part index, or {@code 0} if the assumption was false.
	 */
	public int keywordIndex (final int instruction)
	{
		return 0;
	}

	/**
	 * Given an instruction and program counter, answer the list of successor
	 * program counters that should be explored. For example, a {@link #branch}
	 * instruction will need to visit both the next program counter <em>and</em>
	 * the branch target.
	 *
	 * @param instruction The encoded parsing instruction at the specified
	 *                    program counter.
	 * @param currentPc The current program counter.
	 * @return The list of successor program counters.
	 */
	public @NotNull List<Integer> successorPcs (
		final int instruction,
		final int currentPc)
	{
		return Collections.singletonList(currentPc + 1);
	}

	/**
	 * Assume that the instruction encodes an operand that represents the index
	 * of an argument to be checked (for grammatical permissiveness): answer the
	 * operand.
	 *
	 * @param instruction A coded instruction.
	 * @return The argument index, or {@code 0} if the assumption was false.
	 */
	public int checkArgumentIndex (final int instruction)
	{
		return 0;
	}

	/**
	 * Assume that the instruction encodes an operand that represents an
	 * argument {@linkplain ParsingConversionRule conversion rule} to be
	 * performed: answer the operand.
	 *
	 * @param instruction A coded instruction.
	 * @return The conversion rule, or {@code 0} if the assumption was false.
	 */
	public @NotNull ParsingConversionRule conversionRule (final int instruction)
	{
		return noConversion;
	}

	/**
	 * Decode the specified instruction into an {@linkplain ParsingOperation
	 * operation}.
	 *
	 * @param instruction A coded instruction.
	 * @return The decoded operation.
	 */
	public static @NotNull ParsingOperation decode (final int instruction)
	{
		final int selector = instruction & (distinctInstructions - 1);
		if (instruction > distinctInstructions)
		{
			switch (selector)
			{
				case 0: return branch;
				case 1: return jump;
				case 2: return parsePart;
				case 3: return checkArgument;
				case 4: return convert;
			}
		}
		else
		{
			switch (selector)
			{
				case 0: return parseArgument;
				case 1: return newList;
				case 2: return appendArgument;
				case 3: return saveParsePosition;
				case 4: return discardSavedParsePosition;
				case 5: return ensureParseProgress;
				case 6: return parseRawToken;
			}
		}
		throw new RuntimeException("reserved opcode");
	}
}
