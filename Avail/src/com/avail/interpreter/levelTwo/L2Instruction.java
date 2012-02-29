/**
 * L2Instruction.java
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

package com.avail.interpreter.levelTwo;

import java.util.*;
import com.avail.annotations.NotNull;
import com.avail.descriptor.*;
import com.avail.interpreter.levelTwo.operand.L2Operand;
import com.avail.interpreter.levelTwo.register.L2Register;
import com.avail.utility.Transformer1;

/**
 * {@code L2Instruction} is the foundation for all instructions understood by
 * the {@linkplain L2Interpreter level two Avail interpreter}. These
 * instructions are model objects generated and manipulated by the {@linkplain
 * L2Translator translator} and the {@linkplain L2CodeGenerator code generator}.
 *
 * <p>It implements a mechanism for establishing and interrogating the position
 * of the instruction within its {@linkplain L2ChunkDescriptor chunk}'s
 * {@linkplain L2ChunkDescriptor.ObjectSlots#WORDCODES wordcode stream}. It
 * defines responsibilities for interrogating the source and destination
 * {@linkplain L2Register registers} used by the instruction and emitting the
 * instruction on a code generator. Lastly it specifies an entry point for
 * describing type and constant value propagation to a translator.</p>
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
 */
public final class L2Instruction
{
	/**
	 * The {@link L2Operation} whose execution this instruction represents.
	 */
	public final @NotNull L2Operation operation;

	/**
	 * The {@link L2Operand}s to supply to the operation.
	 */
	public final @NotNull L2Operand[] operands;

	/**
	 * The position of the {@linkplain L2Instruction instruction} in its
	 * {@linkplain L2ChunkDescriptor.ObjectSlots#WORDCODES wordcode stream}.
	 */
	private int offset = -1;

	/**
	 * Answer the position of the {@linkplain L2Instruction instruction} in its
	 * {@linkplain L2ChunkDescriptor.ObjectSlots#WORDCODES wordcode stream}.
	 *
	 * @return The position of the {@linkplain L2Instruction instruction} in its
	 *         {@linkplain L2ChunkDescriptor.ObjectSlots#WORDCODES wordcode
	 *         stream}.
	 */
	public int offset ()
	{
		return offset;
	}

	/**
	 * Set the final position of the {@linkplain L2Instruction instruction}
	 * within its {@linkplain L2ChunkDescriptor.ObjectSlots#WORDCODES wordcode
	 * stream}.
	 *
	 * @param offset
	 *        The final position of the {@linkplain L2Instruction instruction}
	 *        within its {@linkplain L2ChunkDescriptor.ObjectSlots#WORDCODES
	 *        wordcode stream}.
	 */
	public void setOffset (final int offset)
	{
		this.offset = offset;
	}

	/**
	 * Construct a new {@link L2Instruction}.
	 *
	 * @param operation
	 *            The {@link L2Operation} that this instruction performs.
	 * @param operands
	 *            The array of {@link L2Operand}s on which this instruction
	 *            operates.  These must agree with the operation's array of
	 *            {@link L2OperandType}s.
	 */
	public L2Instruction (
		final @NotNull L2Operation operation,
		final @NotNull L2Operand... operands)
	{
		final L2OperandType[] operandTypes = operation.operandTypes();
		assert operandTypes.length == operands.length;
		for (int i = 0; i < operands.length; i++)
		{
			assert operands[i].operandType() == operandTypes[i];
		}
		this.operation = operation;
		this.operands = operands;
	}

	/**
	 * Answer the {@linkplain List list} of {@linkplain L2Register registers}
	 * read by this {@linkplain L2Instruction instruction}.
	 *
	 * @return The source {@linkplain L2Register registers}.
	 */
	public @NotNull List<L2Register> sourceRegisters ()
	{
		final List<L2Register> sourceRegisters = new ArrayList<L2Register>();
		for (final L2Operand operand : operands)
		{
			if (operand.operandType().isSource)
			{
				operand.transformRegisters(
					new Transformer1<L2Register, L2Register>()
					{
						@Override
						public L2Register value (final L2Register arg)
						{
							sourceRegisters.add(arg);
							return arg;
						}
					});
			}
		}
		return sourceRegisters;
	}

	/**
	 * Answer the {@linkplain List list} of {@linkplain L2Register registers}
	 * modified by this {@linkplain L2Instruction instruction}.
	 *
	 * @return The source {@linkplain L2Register registers}.
	 */
	public @NotNull List<L2Register> destinationRegisters ()
	{
		final List<L2Register> destinationRegisters =
			new ArrayList<L2Register>();
		for (final L2Operand operand : operands)
		{
			if (operand.operandType().isDestination)
			{
				operand.transformRegisters(
					new Transformer1<L2Register, L2Register>()
					{
						@Override
						public L2Register value (final L2Register arg)
						{
							destinationRegisters.add(arg);
							return arg;
						}
					});
			}
		}
		return destinationRegisters;
	}

	/**
	 * Emit this {@linkplain L2Instruction instruction} to the specified
	 * {@linkplain L2CodeGenerator code generator}.
	 *
	 * @param codeGenerator A {@linkplain L2CodeGenerator code generator}.
	 */
	public void emitOn (
		final @NotNull L2CodeGenerator codeGenerator)
	{
		if (operation.shouldEmit())
		{
			codeGenerator.emitL2Operation(operation);
			for (final L2Operand operand : operands)
			{
				operand.emitOn(codeGenerator);
			}
		}
	}

	/**
	 * Propagate {@linkplain TypeDescriptor type} and constant value information
	 * from source {@linkplain L2Register registers} to the destination
	 * registers.
	 *
	 * @param translator The {@linkplain L2Translator translator}.
	 */
	public void propagateTypesFor (final @NotNull L2Translator translator)
	{
		operation.propagateTypesInFor(this, translator);
	}

	/**
	 * Normalize the registers that this instruction reads from, to ensure that
	 * redundant moves can be eliminated.  Answer an equivalent instruction,
	 * possibly the receiver itself.
	 *
	 * @param translator
	 *            The {@link L2Translator} holding the register equivalence
	 *            state at this point in the translation.
	 * @return
	 *            The resulting {@link L2Instruction}.
	 */
	public @NotNull L2Instruction normalizeRegisters (
		final @NotNull L2Translator translator)
	{
		final L2Operand[] newOperands = operands.clone();
		for (int i = 0; i < newOperands.length; i++)
		{
			newOperands[i] = newOperands[i].transformRegisters(
				new Transformer1<L2Register, L2Register>()
				{
					@Override
					public L2Register value (final L2Register arg)
					{
						return translator.normalize(arg);
					}
				});
		}
		return new L2Instruction(operation, newOperands);
	}

	@Override
	public String toString ()
	{
		final StringBuilder builder = new StringBuilder();
		builder.append(operation.name());
		builder.append(" (");
		boolean first = true;
		for (final L2Operand operand : operands)
		{
			if (!first)
			{
				builder.append(", ");
			}
			builder.append(operand);
			first = false;
		}
		builder.append(")");
		return builder.toString();
	}
}
