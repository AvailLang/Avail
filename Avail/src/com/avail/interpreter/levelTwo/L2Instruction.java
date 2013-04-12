/**
 * L2Instruction.java
 * Copyright © 1993-2013, Mark van Gulik and Todd L Smith.
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
import com.avail.annotations.*;
import com.avail.descriptor.*;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.levelTwo.operand.*;
import com.avail.interpreter.levelTwo.operation.L2_LABEL;
import com.avail.optimizer.L2Translator;
import com.avail.interpreter.levelTwo.register.L2Register;
import com.avail.utility.*;

/**
 * {@code L2Instruction} is the foundation for all instructions understood by
 * the {@linkplain Interpreter level two Avail interpreter}. These
 * instructions are model objects generated and manipulated by the {@linkplain
 * L2Translator translator} and the {@linkplain L2CodeGenerator code generator}.
 *
 * <p>It implements a mechanism for establishing and interrogating the position
 * of the instruction within its {@linkplain L2ChunkDescriptor chunk}'s
 * {@linkplain com.avail.descriptor.L2ChunkDescriptor.ObjectSlots#WORDCODES
 * wordcode stream}. It defines responsibilities for interrogating the source
 * and destination {@linkplain L2Register registers} used by the instruction and
 * emitting the instruction on a code generator. Lastly it specifies an entry
 * point for describing type and constant value propagation to a translator.</p>
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class L2Instruction
{
	/**
	 * The {@link L2Operation} whose execution this instruction represents.
	 */
	public final L2Operation operation;

	/**
	 * The {@link L2Operand}s to supply to the operation.
	 */
	public final L2Operand[] operands;

	/**
	 * The position of the {@linkplain L2Instruction instruction} in its
	 * {@linkplain com.avail.descriptor.L2ChunkDescriptor.ObjectSlots#WORDCODES
	 * wordcode stream}.
	 */
	private int offset = -1;

	/**
	 * Answer the position of the {@linkplain L2Instruction instruction} in its
	 * {@linkplain com.avail.descriptor.L2ChunkDescriptor.ObjectSlots#WORDCODES
	 * wordcode stream}.
	 *
	 * @return The position of the instruction in its wordcode stream.
	 */
	public int offset ()
	{
		return offset;
	}

	/**
	 * Set the final position of the {@linkplain L2Instruction instruction}
	 * within its {@linkplain
	 * com.avail.descriptor.L2ChunkDescriptor.ObjectSlots#WORDCODES wordcode
	 * stream}.
	 *
	 * @param offset
	 *        The final position of the instruction within its wordcode stream.
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
		final L2Operation operation,
		final L2Operand... operands)
	{
		final L2NamedOperandType[] operandTypes = operation.namedOperandTypes;
		assert operandTypes != null;
		assert operandTypes.length == operands.length;
		for (int i = 0; i < operands.length; i++)
		{
			assert operands[i].operandType() == operandTypes[i].operandType();
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
	public List<L2Register> sourceRegisters ()
	{
		final List<L2Register> sourceRegisters = new ArrayList<L2Register>();
		for (final L2Operand operand : operands)
		{
			if (operand.operandType().isSource)
			{
				operand.transformRegisters(
					new Transformer2<L2Register, L2OperandType, L2Register>()
					{
						@Override
						public L2Register value (
							final @Nullable L2Register register,
							final @Nullable L2OperandType operandType)
						{
							assert register != null;
							assert operandType != null;
							sourceRegisters.add(register);
							return register;
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
	public List<L2Register> destinationRegisters ()
	{
		final List<L2Register> destinationRegisters =
			new ArrayList<L2Register>();
		for (final L2Operand operand : operands)
		{
			if (operand.operandType().isDestination)
			{
				operand.transformRegisters(
					new Transformer2<L2Register, L2OperandType, L2Register>()
					{
						@Override
						public L2Register value (
							final @Nullable L2Register register,
							final @Nullable L2OperandType operandType)
						{
							assert register != null;
							assert operandType != null;
							destinationRegisters.add(register);
							return register;
						}
					});
			}
		}
		return destinationRegisters;
	}

	/**
	 * Answer the possible target instructions of this instruction.  These must
	 * be {@linkplain L2_LABEL labels}.  This list does not
	 * include the instruction immediately following the receiver in the stream
	 * of instructions, but its reachability can be determined separately via
	 * {@linkplain L2Operation#reachesNextInstruction()}.
	 *
	 * @return A {@link List} of label {@link L2Instruction instructions}.
	 */
	public List<L2Instruction> targetLabels ()
	{
		List<L2Instruction> labels = Collections.emptyList();
		for (final L2Operand operand : operands)
		{
			if (operand.operandType() == L2OperandType.PC)
			{
				if (labels.size() == 0)
				{
					labels = new ArrayList<L2Instruction>();
				}
				labels.add(((L2PcOperand)operand).targetLabel());
			}
		}
		return labels;
	}

	/**
	 * Emit this {@linkplain L2Instruction instruction} to the specified
	 * {@linkplain L2CodeGenerator code generator}.
	 *
	 * @param codeGenerator A {@linkplain L2CodeGenerator code generator}.
	 */
	public void emitOn (
		final L2CodeGenerator codeGenerator)
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
	 * Answer whether this instruction has any observable effect besides
	 * writing to its destination registers.
	 *
	 * @return Whether this instruction has side effects.
	 */
	public boolean hasSideEffect ()
	{
		return operation.hasSideEffect(this);
	}

	/**
	 * Propagate {@linkplain TypeDescriptor type} and constant value information
	 * from source {@linkplain L2Register registers} to the destination
	 * registers.
	 *
	 * @param translator The {@linkplain L2Translator translator}.
	 */
	public void propagateTypesFor (final L2Translator translator)
	{
		operation.propagateTypesInFor(this, translator.registers());
	}

	/**
	 * Normalize the registers that this instruction reads from, to ensure that
	 * redundant moves can be eliminated.  Answer an equivalent instruction,
	 * possibly the receiver itself.
	 *
	 * @param transformer
	 *            The {@link Transformer2 transformer} which can normalize any
	 *            register to its best available equivalent.
	 * @return
	 *            The resulting {@link L2Instruction}.
	 */
	public L2Instruction transformRegisters (
		final Transformer2<L2Register, L2OperandType, L2Register>
			transformer)
	{
		final L2Operand[] newOperands = new L2Operand[operands.length];
		for (int i = 0; i < newOperands.length; i++)
		{
			newOperands[i] = operands[i].transformRegisters(transformer);
		}
		return new L2Instruction(operation, newOperands);
	}

	@Override
	public String toString ()
	{
		final StringBuilder builder = new StringBuilder();
		builder.append(operation.name());
		final L2NamedOperandType[] types = operation.operandTypes();
		assert operands.length == types.length;
		for (int i = 0; i < operands.length; i++)
		{
			builder.append(i == 0 ? ": " : ", ");
			assert operands[i].operandType() ==
				types[i].operandType();
			builder.append(types[i].name());
			builder.append("=");
			builder.append(operands[i]);
		}
		return builder.toString();
	}
}
