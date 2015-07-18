/**
 * L2Instruction.java
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

package com.avail.interpreter.levelTwo;

import java.util.*;
import com.avail.annotations.*;
import com.avail.descriptor.*;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;
import com.avail.interpreter.levelTwo.operand.*;
import com.avail.interpreter.levelTwo.operation.L2_LABEL;
import com.avail.optimizer.L2Translator;
import com.avail.optimizer.RegisterSet;
import com.avail.interpreter.levelTwo.register.L2IntegerRegister;
import com.avail.interpreter.levelTwo.register.L2ObjectRegister;
import com.avail.interpreter.levelTwo.register.L2Register;
import com.avail.interpreter.levelTwo.register.L2RegisterVector;
import com.avail.utility.evaluation.*;
/**
 * {@code L2Instruction} is the foundation for all instructions understood by
 * the {@linkplain Interpreter level two Avail interpreter}. These
 * instructions are model objects generated and manipulated by the {@linkplain
 * L2Translator translator}.
 *
 * <p>It used to be the case that the instructions were flattened into a stream
 * of integers, operation followed by operands.  That is no longer the case, as
 * of 2013-05-01 [MvG].  Instead, the L2Instructions themselves are kept around.
 * To execute an L2Instruction, its L2Operation is extracted and asked to
 * {@linkplain L2Operation#step(L2Instruction, Interpreter) step}.</p>
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
	 * The position of this {@link L2Instruction} within its array of
	 * instructions.
	 */
	private int offset = -1;

	/**
	 * Answer the position of this {@link L2Instruction} within its array of
	 * instructions.
	 *
	 * @return The position of the instruction in its chunk's instruction array.
	 */
	public int offset ()
	{
		return offset;
	}

	/**
	 * Set the final position of the {@link L2Instruction} within its {@link
	 * L2Chunk}'s array of instructions.
	 *
	 * @param offset
	 *        The final position of the instruction within the array.
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
		final List<L2Register> sourceRegisters = new ArrayList<>();
		for (final L2Operand operand : operands)
		{
			if (operand.operandType().isSource)
			{
				operand.transformRegisters(
					new Transformer2<
						L2Register,
						L2OperandType,
						L2Register>()
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
			new ArrayList<>();
		for (final L2Operand operand : operands)
		{
			if (operand.operandType().isDestination)
			{
				operand.transformRegisters(
					new Transformer2<
						L2Register,
						L2OperandType,
						L2Register>()
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
	 * be {@linkplain L2_LABEL labels}.  This list does not include the
	 * instruction immediately following the receiver in the stream of
	 * instructions, but its reachability can be determined separately via
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
	 * from source {@link L2Register}s to destination registers within the
	 * provided {@link RegisterSet}s.  There is one RegisterSet for each target
	 * L2Instruction, including the instruction that follows this one.  They
	 * occur in the same order as the {@link #targetLabels()}, with the
	 * successor instruction's RegisterSet prepended if it {@link
	 * L2Operation#reachesNextInstruction()}.
	 *
	 * @param registerSets
	 *            A list of RegisterSets in the above-specified order.
	 * @param translator
	 *            The L2Translator on behalf of which to propagate types.
	 */
	public void propagateTypes (
		final List<RegisterSet> registerSets,
		final L2Translator translator)
	{
		final int count = (operation.reachesNextInstruction() ? 1 : 0)
			+ targetLabels().size();
		assert registerSets.size() == count;
		if (count == 1)
		{
			operation.propagateTypes(
				this,
				registerSets.get(0),
				translator);
		}
		else
		{
			operation.propagateTypes(
				this,
				registerSets,
				translator);
		}
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
		final Transformer2<L2Register, L2OperandType, L2Register> transformer)
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
		builder.append(offset);
		builder.append(". ");
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

	/**
	 * Extract the {@link String} from the {@link L2CommentOperand} having the
	 * specified position in my array of operands.
	 *
	 * @param operandIndex Which operand holds the comment.
	 * @return The String from the comment.
	 */
	public final String commentAt (final int operandIndex)
	{
		return ((L2CommentOperand)operands[operandIndex]).comment;
	}

	/**
	 * Extract the constant {@link AvailObject} from the {@link
	 * L2ConstantOperand} having the specified position in my array of operands.
	 *
	 * @param operandIndex Which operand holds the constant.
	 * @return The constant value.
	 */
	public final AvailObject constantAt (final int operandIndex)
	{
		return ((L2ConstantOperand)operands[operandIndex]).object;
	}

	/**
	 * Extract the {@link A_Bundle} from the {@link L2SelectorOperand} having
	 * the specified position in my array of operands.
	 *
	 * @param operandIndex Which operand holds the message bundle.
	 * @return The message bundle.
	 */
	public final A_Bundle bundleAt (final int operandIndex)
	{
		return ((L2SelectorOperand)operands[operandIndex]).bundle;
	}

	/**
	 * Extract the immediate {@code int}  from the {@link L2ImmediateOperand}
	 * having the specified position in my array of operands.
	 *
	 * @param operandIndex Which operand holds the immediate value.
	 * @return The immediate value.
	 */
	public final int immediateAt (final int operandIndex)
	{
		return ((L2ImmediateOperand)operands[operandIndex]).value;
	}

	/**
	 * Extract the program counter {@code int} from the {@link L2PcOperand}
	 * having the specified position in my array of operands.
	 *
	 * @param operandIndex Which operand holds the program counter value.
	 * @return An int representing a target offset into a chunk's instructions.
	 */
	public final int pcAt (final int operandIndex)
	{
		return ((L2PcOperand)operands[operandIndex]).targetLabel().offset;
	}

	/**
	 * Extract the {@link Primitive} from the {@link L2PrimitiveOperand} having
	 * the specified position in my array of operands.
	 *
	 * @param operandIndex Which operand holds a primitive.
	 * @return The specified {@link Primitive}.
	 */
	public final Primitive primitiveAt (final int operandIndex)
	{
		return ((L2PrimitiveOperand)operands[operandIndex]).primitive;
	}

	/**
	 * Extract the {@link L2IntegerRegister} from the {@link L2ReadIntOperand}
	 * having the specified position in my array of operands.
	 *
	 * @param operandIndex Which operand holds a read of an integer register.
	 * @return The specified {@link L2IntegerRegister} to read.
	 */
	public final L2IntegerRegister readIntRegisterAt (final int operandIndex)
	{
		return ((L2ReadIntOperand)operands[operandIndex]).register;
	}

	/**
	 * Extract the {@link L2IntegerRegister} from the {@link L2WriteIntOperand}
	 * having the specified position in my array of operands.
	 *
	 * @param operandIndex Which operand holds a write of an integer register.
	 * @return The specified {@link L2IntegerRegister} to write.
	 */
	public final L2IntegerRegister writeIntRegisterAt (final int operandIndex)
	{
		return ((L2WriteIntOperand)operands[operandIndex]).register;
	}

	/**
	 * Extract the {@link L2IntegerRegister} from the {@link
	 * L2ReadWriteIntOperand} having the specified position in my array of
	 * operands.
	 *
	 * @param operandIndex
	 *            Which operand holds a read&write of an integer register.
	 * @return
	 *            The specified {@link L2IntegerRegister} to read and write.
	 */
	public final L2IntegerRegister readWriteIntRegisterAt (
		final int operandIndex)
	{
		return ((L2ReadWriteIntOperand)operands[operandIndex]).register;
	}

	/**
	 * Extract the {@link L2ObjectRegister} from the {@link
	 * L2ReadPointerOperand} having the specified position in my array of
	 * operands.
	 *
	 * @param operandIndex Which operand holds a read of an object register.
	 * @return The specified {@link L2ObjectRegister} to read.
	 */
	public final L2ObjectRegister readObjectRegisterAt (final int operandIndex)
	{
		return ((L2ReadPointerOperand)operands[operandIndex]).register;
	}

	/**
	 * Extract the {@link L2ObjectRegister} from the {@link
	 * L2WritePointerOperand} having the specified position in my array of
	 * operands.
	 *
	 * @param operandIndex Which operand holds a write of an object register.
	 * @return The specified {@link L2ObjectRegister} to write.
	 */
	public final L2ObjectRegister writeObjectRegisterAt (final int operandIndex)
	{
		return ((L2WritePointerOperand)operands[operandIndex]).register;
	}

	/**
	 * Extract the {@link L2ObjectRegister} from the {@link
	 * L2ReadWritePointerOperand} having the specified position in my array of
	 * operands.
	 *
	 * @param operandIndex
	 *            Which operand holds a read&write of an integer register.
	 * @return
	 *            The specified {@link L2IntegerRegister} to read and write.
	 */
	public final L2ObjectRegister readWriteObjectRegisterAt (
		final int operandIndex)
	{
		return ((L2ReadWritePointerOperand)operands[operandIndex]).register;
	}

	/**
	 * Extract the {@link L2RegisterVector} from the {@link L2ReadVectorOperand}
	 * having the specified position in my array of operands.
	 *
	 * @param operandIndex Which operand holds a read of a register vector.
	 * @return The specified {@link L2RegisterVector} to read.
	 */
	public final L2RegisterVector readVectorRegisterAt (final int operandIndex)
	{
		return ((L2ReadVectorOperand)operands[operandIndex]).vector;
	}

	/**
	 * Extract the {@link L2RegisterVector} from the {@link
	 * L2WriteVectorOperand} having the specified position in my array of
	 * operands.
	 *
	 * @param operandIndex Which operand holds a write of a register vector.
	 * @return The specified {@link L2RegisterVector} to write.
	 */
	public final L2RegisterVector writeVectorRegisterAt (final int operandIndex)
	{
		return ((L2WriteVectorOperand)operands[operandIndex]).vector;
	}

	/**
	 * Extract the {@link L2RegisterVector} from the {@link
	 * L2ReadWriteVectorOperand} having the specified position in my array of
	 * operands.
	 *
	 * @param operandIndex
	 *            Which operand holds a read&write of a register vector.
	 * @return
	 *            The specified {@link L2RegisterVector} to read and write.
	 */
	public final L2RegisterVector readWriteVectorRegisterAt (
		final int operandIndex)
	{
		return ((L2ReadWriteVectorOperand)operands[operandIndex]).vector;
	}
}