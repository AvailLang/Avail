/**
 * ParsingOperation.java
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

package com.avail.compiler;

import static com.avail.compiler.ParsingConversionRule.*;
import java.util.*;
import com.avail.compiler.MessageSplitter.SectionCheckpoint;
import com.avail.descriptor.*;
import com.avail.descriptor.TokenDescriptor.TokenType;

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
	 * Arity zero entries:
	 */

	/**
	 * {@code 0} - Push a new {@linkplain ListNodeDescriptor list} that
	 * contains an {@linkplain TupleDescriptor#empty() empty tuple} of
	 * {@linkplain ParseNodeDescriptor phrases} onto the parse stack.
	 */
	NEW_LIST(0),

	/**
	 * {@code 1} - Pop an argument from the parse stack of the current
	 * potential message send. Pop a {@linkplain ListNodeDescriptor list} from
	 * the parse stack. Append the argument to the list. Push the resultant list
	 * onto the parse stack.
	 */
	APPEND_ARGUMENT(1),

	/**
	 * {@code 2} - Push a {@linkplain MarkerNodeDescriptor marker} representing
	 * the current parse position onto the mark stack.
	 */
	SAVE_PARSE_POSITION(2),

	/**
	 * {@code 3} - Pop the top marker off the mark stack.
	 */
	DISCARD_SAVED_PARSE_POSITION(3),

	/**
	 * {@code 4} - Pop the top marker off the mark stack and compare it to the
	 * current parse position.  If they're the same, abort the current parse,
	 * otherwise push the current parse position onto the mark stack in place of
	 * the old marker and continue parsing.
	 */
	ENSURE_PARSE_PROGRESS(4),

	/**
	 * {@code 5} - Push a {@link LiteralNodeDescriptor literal node}
	 * containing the {@linkplain AtomDescriptor Avail false boolean}.
	 */
	PUSH_FALSE(5),

	/**
	 * {@code 6} - Push a {@link LiteralNodeDescriptor literal node}
	 * containing the {@linkplain AtomDescriptor Avail true boolean}.
	 */
	PUSH_TRUE(6),

	/**
	 * {@code 7} - Parse an ordinary argument of a message send, pushing the
	 * expression onto the parse stack.
	 */
	PARSE_ARGUMENT(7),

	/**
	 * {@code 8} - Parse an expression, even one whose expressionType is ⊤,
	 * then push <em>a literal node wrapping this expression</em> onto the parse
	 * stack.
	 *
	 * <p>If we didn't wrap the parse node inside a literal node, we wouldn't be
	 * able to process sequences of statements in macros, since they would each
	 * have an expressionType of ⊤ (or if one was ⊥, the entire expressionType
	 * would also be ⊥).  Instead, they will have the expressionType phrase⇒⊤
	 * (or phrase⇒⊥), which is perfectly fine to put inside a list node during
	 * parsing.</p>
	 */
	PARSE_TOP_VALUED_ARGUMENT(8),

	/**
	 * {@code 9} - Parse a {@linkplain TokenDescriptor raw token}. It should
	 * correspond to a {@linkplain VariableDescriptor variable} that is
	 * in scope. Push a {@linkplain ReferenceNodeDescriptor variable reference
	 * phrase} onto the parse stack.
	 */
	PARSE_VARIABLE_REFERENCE(9),

	/**
	 * {@code 10} - Parse an argument of a message send, using the <em>outermost
	 * (module) scope</em>.  Leave it on the parse stack.
	 */
	PARSE_ARGUMENT_IN_MODULE_SCOPE(10),

	/**
	 * {@code 11} - Parse <em>any</em> {@linkplain TokenDescriptor raw token},
	 * leaving it on the parse stack.
	 *
	 * <p>This is used by the {@link MessageSplitter.RawTokenArgument} message
	 * expression, denoted by the characters "…!" in the message name.</p>
	 */
	PARSE_ANY_RAW_TOKEN(11),

	/**
	 * {@code 12} - Parse a raw <em>{@linkplain TokenType#KEYWORD keyword}</em>
	 * {@linkplain TokenDescriptor token}, leaving it on the parse stack.
	 *
	 * <p>This is used by the {@link MessageSplitter.RawKeywordTokenArgument}
	 * message expression, denoted by characters "…" in the message name.</p>
	 */
	PARSE_RAW_KEYWORD_TOKEN(12),

	/**
	 * {@code 13} - Parse a raw <em>{@linkplain TokenType#LITERAL literal}</em>
	 * {@linkplain TokenDescriptor token}, leaving it on the parse stack.
	 *
	 * <p>This is used by the {@link
	 * MessageSplitter.RawStringLiteralTokenArgument} message expression,
	 * denoted by characters "…$" in the message name.</p>
	 */
	PARSE_RAW_STRING_LITERAL_TOKEN(13),

	/**
	 * {@code 14} - Parse a raw <em>{@linkplain TokenType#LITERAL literal}</em>
	 * {@linkplain TokenDescriptor token}, leaving it on the parse stack.
	 *
	 * <p>This is used by the {@link
	 * MessageSplitter.RawWholeNumberLiteralTokenArgument} message expression,
	 * denoted by characters "…#" in the message name.</p>
	 */
	PARSE_RAW_WHOLE_NUMBER_LITERAL_TOKEN(14),

	/** Reserved for future parsing concepts. */
	RESERVED_15(15),

	/*
	 * Arity one entries:
	 */

	/**
	 * {@code 16*N+0} - Branch to instruction N. Attempt to continue parsing at
	 * both the next instruction and instruction N.
	 */
	BRANCH(0)
	{
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
	JUMP(1)
	{
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
	PARSE_PART(2)
	{
		@Override
		public int keywordIndex (final int instruction)
		{
			return operand(instruction);
		}
	},

	/**
	 * {@code 16*N+3} - Parse the Nth {@linkplain MessageSplitter#messageParts
	 * message part} of the current message. This will be a specific {@linkplain
	 * TokenDescriptor token}. It should be matched case insensitively against
	 * the source token.
	 */
	PARSE_PART_CASE_INSENSITIVELY(3)
	{
		@Override
		public int keywordIndex (final int instruction)
		{
			return operand(instruction);
		}
	},

	/**
	 * {@code 16*N+4} - Push a {@link LiteralNodeDescriptor literal node}
	 * containing an {@linkplain IntegerDescriptor Avail integer} based on the
	 * operand.
	 */
	PUSH_INTEGER_LITERAL(4)
	{
		@Override
		public int integerToPush (final int instruction)
		{
			return operand(instruction);
		}
	},

	/**
	 * {@code 16*N+5} - Apply grammatical restrictions to the Nth leaf argument
	 * (underscore/ellipsis) of the current message.
	 */
	CHECK_ARGUMENT(5)
	{
		@Override
		public int checkArgumentIndex (final int instruction)
		{
			return operand(instruction);
		}
	},

	/**
	 * {@code 16*N+6} - Pop an argument from the parse stack and apply the
	 * {@linkplain ParsingConversionRule conversion rule} specified by N.
	 */
	CONVERT(6)
	{
		@Override
		public ParsingConversionRule conversionRule (
			final int instruction)
		{
			return ruleNumber(operand(instruction));
		}
	},

	/**
	 * {@code 16*N+7} - A macro has been parsed up to a {@link
	 * SectionCheckpoint} (§).  Make a copy of the parse stack, then perform the
	 * equivalent of an {@link #APPEND_ARGUMENT} on the copy, the specified
	 * number of times minus one (because zero is not a legal operand).  Make it
	 * into a single {@linkplain ListNodeDescriptor list node} and push it onto
	 * the original parse stack.  It will be consumed by a subsequent {@link
	 * #RUN_PREFIX_FUNCTION}.
	 *
	 * <p>This instruction is detected specially by the {@linkplain
	 * MessageBundleTreeDescriptor message bundle tree}'s {@linkplain
	 * A_BundleTree#expand(A_Module)} operation.  Its successors are separated
	 * into distinct message bundle trees, one per message bundle.</p>
	 */
	PREPARE_TO_RUN_PREFIX_FUNCTION(7)
	{
		@Override
		public int fixupDepth (final int instruction)
		{
			return operand(instruction);
		}
	},

	/**
	 * {@code 16*N+8} - A macro has been parsed up to a {@link
	 * SectionCheckpoint} (§), and a copy of the cleaned up parse stack has been
	 * pushed, so invoke the Nth prefix function associated with the macro.
	 * Consume the previously pushed copy of the parse stack.  The current
	 * {@link AvailCompiler.ParserState}'s {@linkplain
	 * AvailCompiler.ParserState#clientDataMap} is stashed in the
	 * new {@link FiberDescriptor fiber}'s {@linkplain
	 * AvailObject#fiberGlobals()} and retrieved afterward, so the prefix
	 * function and macros can alter the scope or communicate with each other
	 * by manipulating this {@linkplain MapDescriptor map}.  This technique
	 * prevents chatter between separate fibers (i.e., parsing can still be
	 * done in parallel) and between separate linguistic abstractions (the keys
	 * are atoms and are therefore modular).
	 */
	RUN_PREFIX_FUNCTION(8)
	{
		@Override
		public int prefixFunctionSubscript (final int instruction)
		{
			return operand(instruction);
		}
	},

	/**
	 * {@code 16*N+9} - Permute the elements of the list node on the top of the
	 * stack via the permutation found via {@linkplain
	 * MessageSplitter#permutationAtIndex(int)}.  The list node must be the same
	 * size as the permutation.
	 */
	PERMUTE_LIST(9)
	{
		@Override
		public int permutationIndex (final int instruction)
		{
			return operand(instruction);
		}
	},

	/**
	 * {@code 16*N+10} - Check that the list node on the top of the stack has at
	 * least the specified size.  Proceed to the next instruction only if this
	 * is the case.
	 */
	CHECK_AT_LEAST(10)
	{
		@Override
		public int requiredMinimumSize (final int instruction)
		{
			return operand(instruction);
		}
	},

	/**
	 * {@code 16*N+11} - Check that the list node on the top of the stack has at
	 * most the specified size.  Proceed to the next instruction only if this
	 * is the case.
	 */
	CHECK_AT_MOST(11)
	{
		@Override
		public int requiredMaximumSize (final int instruction)
		{
			return operand(instruction);
		}
	};

	/**
	 * My array of values, since {@link Enum}.values() makes a copy every time.
	 */
	static final ParsingOperation[] all = values();

	/**
	 * The binary logarithm of the number of distinct instructions supported by
	 * the coding scheme.  It must be integral.
	 */
	static final int distinctInstructionsShift = 4;

	/**
	 * The number of distinct instructions supported by the coding scheme.  It
	 * must be a power of two.
	 */
	public static final int distinctInstructions =
		1 << distinctInstructionsShift;

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
	public final int encoding ()
	{
		if (ordinal() >= distinctInstructions)
		{
			throw new UnsupportedOperationException();
		}
		return modulus;
	}

	/**
	 * Answer the instruction coding of the receiver for the given operand. The
	 * receiver must be arity one ({@code 1}), which is equivalent to its
	 * ordinal being greater than or equal to {@code #distinctInstructions}.
	 *
	 * @param operand The operand.
	 * @return The instruction coding.
	 */
	public final int encoding (final int operand)
	{
		if (ordinal() < distinctInstructions)
		{
			throw new UnsupportedOperationException();
		}
		// The operand should be positive, but allow -1 to represent undefined
		// branch targets.  The generated code with a -1 operand will be wrong,
		// but the first pass of code emission calculates the correct branch
		// targets, and the second pass uses the correct targets.
		assert operand > 0 || operand == -1;
		final int result = (operand << distinctInstructionsShift) + modulus;
		assert operand(result) == operand : "Overflow detected";
		return result;
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
	 * operand.  Answer 0 if the operand does not represent a message part.
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
	 * @return The {@code int} value to push.
	 */
	public int integerToPush (final int instruction)
	{
		 throw new RuntimeException("Parsing instruction is inappropriate");
	}

	/**
	 * Assume that the instruction encodes an operand which is to be treated as
	 * a boolean to be passed as an argument at an Avail call site.  Answer the
	 * operand as a Java boolean.
	 *
	 * @param instruction
	 * @return The {@code boolean} value to push.
	 */
	public boolean booleanToPush (final int instruction)
	{
		 throw new RuntimeException("Parsing instruction is inappropriate");
	}

	/**
	 * @param instruction
	 * @return
	 */
	public int fixupDepth (final int instruction)
	{
		throw new RuntimeException("Parsing instruction is inappropriate");
	}

	/**
	 * Answer the subscript of the prefix function that should be invoked.
	 *
	 * @param instruction
	 * @return
	 */
	public int prefixFunctionSubscript (final int instruction)
	{
		throw new RuntimeException("Parsing instruction is inappropriate");
	}

	/**
	 * Given an instruction and program counter, answer the list of successor
	 * program counters that should be explored. For example, a {@link #BRANCH}
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
		throw new RuntimeException("Parsing instruction is inappropriate");
	}

	/**
	 * Extract the index of the permutation for a {@link #PERMUTE_LIST}
	 * parsing instruction.
	 *
	 * @param instruction A coded instruction.
	 * @return The index of the permutation.
	 */
	public int permutationIndex (final int instruction)
	{
		throw new RuntimeException("Parsing instruction is inappropriate");
	}

	/**
	 * Extract the minimum size that the list on the top of the stack must be
	 * to continue parsing.
	 *
	 * @param instruction A coded instruction.
	 * @return The minimum list size.
	 */
	public int requiredMinimumSize (final int instruction)
	{
		throw new RuntimeException("Parsing instruction is inappropriate");
	}

	/**
	 * Extract the maximum size that the list on the top of the stack may be
	 * to continue parsing.
	 *
	 * @param instruction A coded instruction.
	 * @return The maximum list size.
	 */
	public int requiredMaximumSize (final int instruction)
	{
		throw new RuntimeException("Parsing instruction is inappropriate");
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
			return all[instruction];
		}
		// It's parametric, so it resides in the next 'distinctInstructions'
		// region of enum values.  Mask it and add the offset.
		final int subscript = (instruction & (distinctInstructions - 1))
			+ distinctInstructions;
		return all[subscript];
	}
}
