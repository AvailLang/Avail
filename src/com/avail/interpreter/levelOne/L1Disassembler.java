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

import com.avail.descriptor.A_BasicObject;
import com.avail.descriptor.A_RawFunction;
import com.avail.descriptor.CompiledCodeDescriptor;
import com.avail.descriptor.NybbleTupleDescriptor;
import com.avail.utility.MutableInt;

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
	 * The current level one offset into the code.
	 */
	final MutableInt pc = new MutableInt(1);

	/**
	 * The level one {@linkplain NybbleTupleDescriptor nybblecodes tuple},
	 * pre-extracted from the {@linkplain CompiledCodeDescriptor compiled code
	 * object}.
	 */
	final int numNybbles;

	/**
	 * The number of tabs to output after each line break.
	 */
	final int indent;


	/**
	 * An {@link L1OperandTypeDispatcher} suitably specialized to decode and
	 * print the instruction operands.
	 */
	final L1OperandTypeDispatcher operandTypePrinter =
		new L1OperandTypeDispatcher()
	{

		@Override
		public void doImmediate ()
		{
			builder.append("immediate=").append(code.nextNybblecodeOperand(pc));
		}

		@Override
		public void doLiteral ()
		{
			final int index = code.nextNybblecodeOperand(pc);
			builder.append("literal#").append(index).append("=");
			code.literalAt(index).printOnAvoidingIndent(
				builder,
				recursionMap,
				indent + 1);
		}

		@Override
		public void doLocal ()
		{
			final int index = code.nextNybblecodeOperand(pc);
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
			builder.append("outer#").append(code.nextNybblecodeOperand(pc));
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
		this.numNybbles = code.numNybbles();
		assert pc.value == 1;
		boolean first = true;
		while (pc.value <= numNybbles)
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
			builder.append(pc.value).append(": ");

			final L1Operation operation = code.nextNybblecodeOperation(pc);
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
}
