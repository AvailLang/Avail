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
import com.avail.descriptor.*;

/**
 * {@code ParsingOperation} describes the operations available for parsing Avail
 * message names.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
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
		public int encoding (final int operand)
		{
			throw new UnsupportedOperationException();
		}
	},

	/**
	 * {@code 1} - Push a new {@linkplain ListNodeDescriptor list} that
	 * contains an {@linkplain TupleDescriptor#empty() empty tuple} of
	 * {@linkplain ParseNodeDescriptor phrases} onto the parse stack.
	 */
	newList(1)
	{
		@Override
		public int encoding (final int operand)
		{
			throw new UnsupportedOperationException();
		}
	},

	/**
	 * {@code 2} - Pop an argument from the parse stack of the current
	 * potential message send. Pop a {@linkplain ListNodeDescriptor list} from
	 * the parse stack. Append the argument to the list. Push the resultant list
	 * onto the parse stack.
	 */
	appendArgument(2)
	{
		@Override
		public int encoding (final int operand)
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
		public int encoding (final int operand)
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
		public int encoding (final int operand)
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
		public int encoding (final int operand)
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
		public int encoding (final int operand)
		{
			throw new UnsupportedOperationException();
		}
	},

	/**
	 * {@code 7} - Pop the parse stack (and discard the result).
	 */
	pop(7)
	{
		@Override
		public int encoding (final int operand)
		{
			throw new UnsupportedOperationException();
		}
	},

	/**
	 * {@code 8} - Checkpoint the arguments parsed up to this point.  The
	 * arguments can be subsequently read by a primitive macro.
	 */
	argumentsCheckpoint(8)
	{
		@Override
		public int encoding (final int operand)
		{
			throw new UnsupportedOperationException();
		}
	},

	/** Reserved for future parsing concepts. */
	reserved9(9),

	/** Reserved for future parsing concepts. */
	reserved10(10),

	/** Reserved for future parsing concepts. */
	reserved11(11),

	/** Reserved for future parsing concepts. */
	reserved12(12),

	/** Reserved for future parsing concepts. */
	reserved13(13),

	/** Reserved for future parsing concepts. */
	reserved14(14),

	/** Reserved for future parsing concepts. */
	reserved15(15),

	/*
	 * Arity one (1).
	 */

	/**
	 * {@code 16*N+0} - Branch to instruction N. Attempt to continue parsing at
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
		public List<Integer> successorPcs (
			final int instruction,
			final int currentPc)
		{
			return Arrays.asList(operand(instruction), currentPc + 1);
		}
	},

	/**
	 * {@code 16*N+1} - Jump to instruction N. Attempt to continue parsing only
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
		public List<Integer> successorPcs (
			final int instruction,
			final int currentPc)
		{
			return Collections.singletonList(operand(instruction));
		}
	},

	/**
	 * {@code 16*N+2} - Parse the Nth {@linkplain MessageSplitter#messageParts
	 * message part} of the current message. This will be a specific {@linkplain
	 * TokenDescriptor token}. It should be matched case sensitively against the
	 * source token.
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
	 * {@code 16*N+3} - Apply grammatical restrictions to the Nth leaf argument
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
	 * {@code 16*N+4} - Pop an argument from the parse stack and apply the
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
		public ParsingConversionRule conversionRule (
			final int instruction)
		{
			return ruleNumber(operand(instruction));
		}
	},

	/**
	 * {@code 16*N+5} - Parse the Nth {@linkplain MessageSplitter#messageParts
	 * message part} of the current message. This will be a specific {@linkplain
	 * TokenDescriptor token}. It should be matched case insensitively against
	 * the source token.
	 */
	parsePartCaseInsensitive(5)
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
	 * {@code 16*N+6} - Push a {@link LiteralNodeDescriptor literal node}
	 * containing an {@linkplain IntegerDescriptor Avail integer} based on the
	 * operand.
	 */
	pushIntegerLiteral(6)
	{
		@Override
		public int encoding ()
		{
			throw new UnsupportedOperationException();
		}

		@Override
		public int integerToPush (final int instruction)
		{
			return operand(instruction);
		}
	};

	/**
	 * The binary logarithm of the number of distinct instructions supported by
	 * the coding scheme.  It must be integral.
	 */
	static final int distinctInstructionsShift = 4;

	/**
	 * The number of distinct instructions supported by the coding scheme.  It
	 * must be a power of two.
	 */
	static final int distinctInstructions = 1 << distinctInstructionsShift;

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
	public int encoding (final int operand)
	{
		return (operand << distinctInstructionsShift) + modulus;
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
		return instruction >> distinctInstructionsShift;
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
	 * Assume that the instruction encodes an operand which is to be treated as
	 * an integer to be passed as an argument at an Avail call site.  Answer
	 * the operand.
	 *
	 * @param instruction
	 * @return
	 */
	public int integerToPush (final int instruction)
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
	public List<Integer> successorPcs (
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
	public ParsingConversionRule conversionRule (final int instruction)
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
	public static ParsingOperation decode (final int instruction)
	{
		if (instruction < distinctInstructions)
		{
			return values()[instruction];
		}
		// It's parametric, so it resides in the next 'distinctInstructions'
		// region of enum values.  Mask it and add the offset.
		final int subscript = (instruction & (distinctInstructions - 1))
			+ distinctInstructions;
		return values()[subscript];
	}
}
