/**
 * L1Disassembler.java
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

package com.avail.interpreter.levelOne;

import static com.avail.descriptor.AvailObject.error;
import java.util.List;
import com.avail.annotations.*;
import com.avail.descriptor.*;

/**
 * An instance of {@code L1Disassembler} converts a {@linkplain
 * CompiledCodeDescriptor compiled code object} into a textual representation
 * of its sequence of {@linkplain L1Instruction level one instructions}.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public class L1Disassembler
{
	/**
	 * The {@linkplain CompiledCodeDescriptor compiled code object} being
	 * disassembled.
	 */
	A_RawFunction code;

	/**
	 * The {@link StringBuilder} onto which to describe the level one
	 * instructions.
	 */
	StringBuilder builder;

	/**
	 * The (mutable) {@link List} of {@link AvailObject}s to avoid recursing
	 * into while printing the {@linkplain L1Instruction level one
	 * instructions}.
	 */
	List<A_BasicObject> recursionList;

	/**
	 * The number of tabs to output after each line break.
	 */
	int indent;

	/**
	 * The level one {@linkplain NybbleTupleDescriptor nybblecodes tuple},
	 * pre-extracted from the {@linkplain CompiledCodeDescriptor compiled code
	 * object}.
	 */
	A_Tuple nybbles;

	/**
	 * The current level one offset into the code.
	 */
	int pc;


	/**
	 * An {@link L1OperandTypeDispatcher} suitably specialized to decode and
	 * print the instruction operands.
	 */
	L1OperandTypeDispatcher operandTypePrinter = new L1OperandTypeDispatcher()
	{

		@Override
		public void doImmediate ()
		{
			builder.append("immediate=" + getInteger());
		}

		@Override
		public void doLiteral ()
		{
			final int index = getInteger();
			builder.append("literal#" + index + "=");
			code.literalAt(index).printOnAvoidingIndent(
				builder,
				recursionList,
				indent + 1);
		}

		@Override
		public void doLocal ()
		{
			final int index = getInteger();
			if (index <= code.numArgs())
			{
				builder.append("arg#" + index);
			}
			else
			{
				builder.append("local#" + (index - code.numArgs()));
			}
		}

		@Override
		public void doOuter ()
		{
			builder.append("outer#" + getInteger());
		}

		@Override
		public void doExtension ()
		{
			error("Extension nybblecode should be dealt with another way.");
		}
	};

	/**
	 * Parse the given compiled code object into a sequence of L1 instructions,
	 * printing them on the provided stream.
	 *
	 * @param code
	 *        The {@linkplain CompiledCodeDescriptor code} to decompile.
	 * @param builder
	 *        Where to write the decompilation.
	 * @param recursionList
	 *        Which objects are already being visited.
	 * @param indent
	 *        The indentation level.
	 */
	@SuppressWarnings("unused")
	public static void disassemble (
		final A_RawFunction code,
		final StringBuilder builder,
		final List<A_BasicObject> recursionList,
		final int indent)
	{
		// The constructor does all the work...
		new L1Disassembler(code, builder, recursionList, indent);
	}

	/**
	 * Parse the given compiled code object into a sequence of L1 instructions,
	 * printing them on the provided stream.
	 *
	 * @param code
	 *        The {@linkplain CompiledCodeDescriptor code} to decompile.
	 * @param builder
	 *        Where to write the decompilation.
	 * @param recursionList
	 *        Which objects are already being visited.
	 * @param indent
	 *        The indentation level.
	 */
	private L1Disassembler (
		final A_RawFunction code,
		final StringBuilder builder,
		final List<A_BasicObject> recursionList,
		final int indent)
	{
		this.code = code;
		this.builder = builder;
		this.recursionList = recursionList;
		this.indent = indent;
		this.nybbles = code.nybbles();
		this.pc = 1;
		boolean first = true;
		while (pc <= nybbles.tupleSize())
		{
			if (!first)
			{
				builder.append("\n");
			}
			first = false;
			for (int i = indent; i > 0; i--)
			{
				builder.append("\t");
			}
			builder.append(pc + ": ");
			int nybble = nybbles.extractNybbleFromTupleAt(pc++);
			if (nybble == L1Operation.L1_doExtension.ordinal())
			{
				nybble = 16 + nybbles.extractNybbleFromTupleAt(pc++);
			}
			final L1Operation operation = L1Operation.values()[nybble];
			final L1OperandType[] operandTypes = operation.operandTypes();
			builder.append(operation.name());
			if (operandTypes.length > 0)
			{
				builder.append("(");
				for (int i = 0; i < operandTypes.length; i++)
				{
					if (i > 0)
					{
						builder.append(", ");
					}
					operandTypes[i].dispatch(operandTypePrinter);
				}
				builder.append(")");
			}
		}
	}

	/**
	 * Extract an encoded integer from the nybblecode instruction stream.  The
	 * encoding uses only a nybble for very small operands, and can still
	 * represent up to {@link Integer#MAX_VALUE} if necessary. Adjust the
	 * {@link #pc program counter} to skip the integer.
	 *
	 * @return The integer extracted from the nybblecode stream.
	 */
	@InnerAccess int getInteger ()
	{
		final byte firstNybble = nybbles.extractNybbleFromTupleAt(pc);
		pc++;
		int value = 0;
		final byte[] counts =
		{
			0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 2, 4, 8
		};
		for (int count = counts[firstNybble]; count > 0; count--, pc++)
		{
			value = (value << 4) + nybbles.extractNybbleFromTupleAt(pc);
		}
		final byte[] offsets =
		{
			0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 26, 42, 58, 0, 0
		};
		value += offsets[firstNybble];
		return value;
	}
}
