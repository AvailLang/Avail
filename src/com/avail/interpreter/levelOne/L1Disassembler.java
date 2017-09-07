/**
 * L1Disassembler.java
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

package com.avail.interpreter.levelOne;

import com.avail.annotations.InnerAccess;
import com.avail.descriptor.A_BasicObject;
import com.avail.descriptor.A_RawFunction;
import com.avail.descriptor.A_Tuple;
import com.avail.descriptor.CompiledCodeDescriptor;
import com.avail.descriptor.NybbleTupleDescriptor;

import java.util.IdentityHashMap;

import static com.avail.descriptor.AvailObject.error;

/**
 * An instance of {@code L1Disassembler} converts a {@linkplain
 * CompiledCodeDescriptor compiled code object} into a textual representation
 * of its sequence of {@linkplain L1Operation level one operations} and their
 * {@linkplain L1OperandType operands}.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class L1Disassembler
{
	/**
	 * The {@linkplain CompiledCodeDescriptor compiled code object} being
	 * disassembled.
	 */
	final A_RawFunction code;

	/**
	 * The {@link StringBuilder} onto which to describe the level one
	 * instructions.
	 */
	final StringBuilder builder;

	/**
	 * The (mutable) {@link IdentityHashMap} of {@link A_BasicObject}s to avoid
	 * recursing into while printing the {@linkplain L1Operation level one
	 * operations}.
	 */
	final IdentityHashMap<A_BasicObject, Void> recursionMap;

	/**
	 * The number of tabs to output after each line break.
	 */
	final int indent;

	/**
	 * The level one {@linkplain NybbleTupleDescriptor nybblecodes tuple},
	 * pre-extracted from the {@linkplain CompiledCodeDescriptor compiled code
	 * object}.
	 */
	final A_Tuple nybbles;

	/**
	 * The current level one offset into the code.
	 */
	int pc;


	/**
	 * An {@link L1OperandTypeDispatcher} suitably specialized to decode and
	 * print the instruction operands.
	 */
	final L1OperandTypeDispatcher operandTypePrinter = new L1OperandTypeDispatcher()
	{

		@Override
		public void doImmediate ()
		{
			builder.append("immediate=").append(getInteger());
		}

		@Override
		public void doLiteral ()
		{
			final int index = getInteger();
			builder.append("literal#").append(index).append("=");
			code.literalAt(index).printOnAvoidingIndent(
				builder,
				recursionMap,
				indent + 1);
		}

		@Override
		public void doLocal ()
		{
			final int index = getInteger();
			if (index <= code.numArgs())
			{
				builder.append("arg#").append(index);
			}
			else
			{
				builder.append("local#").append(index - code.numArgs());
			}
		}

		@Override
		public void doOuter ()
		{
			builder.append("outer#").append(getInteger());
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
	 * @param recursionMap
	 *        Which objects are already being visited.
	 * @param indent
	 *        The indentation level.
	 */
	public static void disassemble (
		final A_RawFunction code,
		final StringBuilder builder,
		final IdentityHashMap<A_BasicObject, Void> recursionMap,
		final int indent)
	{
		// The constructor does all the work...
		//noinspection ResultOfObjectAllocationIgnored
		new L1Disassembler(code, builder, recursionMap, indent);
	}

	/**
	 * Parse the given compiled code object into a sequence of L1 instructions,
	 * printing them on the provided stream.
	 *
	 * @param code
	 *        The {@linkplain CompiledCodeDescriptor code} to decompile.
	 * @param builder
	 *        Where to write the decompilation.
	 * @param recursionMap
	 *        Which objects are already being visited.
	 * @param indent
	 *        The indentation level.
	 */
	private L1Disassembler (
		final A_RawFunction code,
		final StringBuilder builder,
		final IdentityHashMap<A_BasicObject, Void> recursionMap,
		final int indent)
	{
		this.code = code;
		this.builder = builder;
		this.recursionMap = recursionMap;
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
			builder.append(pc).append(": ");
			int nybble = nybbles.extractNybbleFromTupleAt(pc++);
			if (nybble == L1Operation.L1_doExtension.ordinal())
			{
				nybble = 16 + nybbles.extractNybbleFromTupleAt(pc++);
			}
			final L1Operation operation = L1Operation.all()[nybble];
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
