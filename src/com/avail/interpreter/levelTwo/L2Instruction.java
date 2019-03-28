/*
 * L2Instruction.java
 * Copyright © 1993-2018, The Avail Foundation, LLC.
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

import com.avail.annotations.InnerAccess;
import com.avail.descriptor.A_BasicObject;
import com.avail.descriptor.A_Bundle;
import com.avail.descriptor.A_Method;
import com.avail.descriptor.AvailObject;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;
import com.avail.interpreter.levelTwo.operand.L2ConstantOperand;
import com.avail.interpreter.levelTwo.operand.L2FloatImmediateOperand;
import com.avail.interpreter.levelTwo.operand.L2IntImmediateOperand;
import com.avail.interpreter.levelTwo.operand.L2Operand;
import com.avail.interpreter.levelTwo.operand.L2PcOperand;
import com.avail.interpreter.levelTwo.operand.L2PrimitiveOperand;
import com.avail.interpreter.levelTwo.operand.L2ReadFloatOperand;
import com.avail.interpreter.levelTwo.operand.L2ReadIntOperand;
import com.avail.interpreter.levelTwo.operand.L2ReadOperand;
import com.avail.interpreter.levelTwo.operand.L2ReadPointerOperand;
import com.avail.interpreter.levelTwo.operand.L2ReadVectorOperand;
import com.avail.interpreter.levelTwo.operand.L2SelectorOperand;
import com.avail.interpreter.levelTwo.operand.L2WriteFloatOperand;
import com.avail.interpreter.levelTwo.operand.L2WriteIntOperand;
import com.avail.interpreter.levelTwo.operand.L2WritePhiOperand;
import com.avail.interpreter.levelTwo.operand.L2WritePointerOperand;
import com.avail.interpreter.levelTwo.operand.PhiRestriction;
import com.avail.interpreter.levelTwo.register.L2ObjectRegister;
import com.avail.interpreter.levelTwo.register.L2Register;
import com.avail.optimizer.L2BasicBlock;
import com.avail.optimizer.L2ControlFlowGraph;
import com.avail.optimizer.L2Generator;
import com.avail.optimizer.L2Retranslator;
import com.avail.optimizer.jvm.JVMTranslator;
import org.objectweb.asm.MethodVisitor;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static com.avail.utility.Nulls.stripNull;

/**
 * {@code L2Instruction} is the foundation for all instructions understood by
 * the {@linkplain Interpreter level two Avail interpreter}. These instructions
 * are model objects generated and manipulated by the {@link L2Generator}.
 *
 * <p>It used to be the case that the instructions were flattened into a stream
 * of integers, operation followed by operands.  That is no longer the case, as
 * of 2013-05-01 [MvG].  Instead, the L2Instructions themselves are kept around
 * for reoptimization and {@linkplain #translateToJVM(JVMTranslator,
 * MethodVisitor) JVM code generation}.</p>
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class L2Instruction
{
	/**
	 * The {@link L2Operation} whose execution this instruction represents.
	 */
	private final L2Operation operation;

	/**
	 * The {@link L2Operand}s to supply to the operation.
	 */
	private final L2Operand[] operands;

	/**
	 * The position of this instruction within its array of instructions.
	 * Only valid near the end of translation.
	 */
	private int offset = -1;

	/**
	 * The {@link L2BasicBlock} to which the instruction belongs.
	 */
	public final L2BasicBlock basicBlock;

	/**
	 * The source {@link L2Register}s.
	 */
	@InnerAccess final List<L2Register<?>> sourceRegisters = new ArrayList<>();

	/**
	 * The destination {@link L2Register}s.
	 */
	@InnerAccess final List<L2Register<?>> destinationRegisters =
		new ArrayList<>();

	/**
	 * The {@link L2Operation} whose execution this instruction represents.
	 */
	public L2Operation operation ()
	{
		return operation;
	}

	/**
	 * Answer the {@link L2Operand}s to supply to the operation.
	 */
	public L2Operand[] operands ()
	{
		return operands;
	}

	/**
	 * Evaluate the given function with each operand.
	 *
	 * @param consumer The {@link Consumer} to evaluate.
	 */
	public void operandsDo (final Consumer<L2Operand> consumer)
	{
		for (final L2Operand operand : operands)
		{
			consumer.accept(operand);
		}
	}

	/**
	 * Answer the Nth {@link L2Operand} to supply to the operation.
	 */
	public L2Operand operand (final int index)
	{
		return operands[index];
	}

	/**
	 * Answer the position of this instruction within its array of instructions.
	 *
	 * @return The position of the instruction in its chunk's instruction array.
	 */
	public int offset ()
	{
		return offset;
	}

	/**
	 * Set the final position of this instruction within its {@link L2Chunk}'s
	 * array of instructions.
	 *
	 * @param offset
	 *        The final position of the instruction within the array.
	 */
	public void setOffset (final int offset)
	{
		this.offset = offset;
	}

	/**
	 * Construct a new {@code L2Instruction}.
	 *
	 * @param operation
	 *        The {@link L2Operation} that this instruction performs.
	 * @param theOperands
	 *        The array of {@link L2Operand}s on which this instruction
	 *        operates.  These must agree with the operation's array of {@link
	 *        L2NamedOperandType}s.  The operation is given an opportunity to
	 *        augment this array, just as it may be given the opportunity to
	 *        augment the named operand types.
	 * @param basicBlock
	 *        The {@link L2BasicBlock} which will contain this instruction.
	 */
	public L2Instruction (
		final L2BasicBlock basicBlock,
		final L2Operation operation,
		final L2Operand... theOperands)
	{
		final L2Operand[] augmentedOperands = operation.augment(theOperands);
		final L2NamedOperandType[] operandTypes =
			stripNull(operation.namedOperandTypes);
		assert operandTypes.length == augmentedOperands.length;
		for (int i = 0; i < augmentedOperands.length; i++)
		{
			assert augmentedOperands[i].operandType()
				== operandTypes[i].operandType();
		}
		this.operation = operation;
		this.operands = new L2Operand[augmentedOperands.length];
		this.basicBlock = basicBlock;
		for (int i = 0; i < operands.length; i++)
		{
			final L2Operand operand = augmentedOperands[i].clone();
			this.operands[i] = operand;
			operand.addSourceRegistersTo(sourceRegisters);
			operand.addDestinationRegistersTo(destinationRegisters);
		}
	}

	/**
	 * Answer the {@linkplain List list} of {@linkplain L2Register registers}
	 * read by this {@code L2Instruction instruction}.
	 *
	 * @return The source {@linkplain L2Register registers}.
	 */
	public List<L2Register<?>> sourceRegisters ()
	{
		//noinspection AssignmentOrReturnOfFieldWithMutableType
		return sourceRegisters;
	}

	/**
	 * Answer the {@link List list} of {@link L2Register} modified by this
	 * {@code L2Instruction instruction}.
	 *
	 * @return The source {@linkplain L2Register}s.
	 */
	public List<L2Register<?>> destinationRegisters ()
	{
		//noinspection AssignmentOrReturnOfFieldWithMutableType
		return destinationRegisters;
	}

	/**
	 * Answer all possible {@link L2PcOperand}s of this instruction.  These
	 * edges lead to other {@link L2BasicBlock}s, and carry both the basic slot
	 * register information and additional {@link PhiRestriction}s for other
	 * registers that are strengthened along that edge.
	 *
	 * <p>This is empty for instructions that don't alter control flow and just
	 * fall through to the next instruction of the same basic block.</p>
	 *
	 * @return A {@link List} of {@link L2PcOperand}s leading to the successor
	 *         {@link L2BasicBlock}s.
	 */
	public List<L2PcOperand> targetEdges ()
	{
		return operation().targetEdges(this);
	}

	/**
	 * Answer whether this instruction can alter control flow.  That's true for
	 * any kind of instruction that has more than one successor (e.g., a branch)
	 * or no successors at all (e.g., a return).
	 *
	 * <p>An instruction for which this is true must occur at the end of each
	 * {@link L2BasicBlock}, but never before the end.</p>
	 *
	 * @return Whether this instruction can do something other than fall through
	 *         to the next instruction of its basic block.
	 */
	public boolean altersControlFlow ()
	{
		return operation().altersControlFlow();
	}

	/**
	 * Answer whether this instruction has any observable effect besides
	 * writing to its destination registers.
	 *
	 * @return Whether this instruction has side effects.
	 */
	public boolean hasSideEffect ()
	{
		return operation().hasSideEffect(this);
	}

	/**
	 * Replace all registers in this instruction using the registerRemap.  If a
	 * register is not present as a key of that map, leave it alone.  Do not
	 * assume SSA form.
	 *
	 * @param registerRemap
	 *        A mapping from existing {@link L2Register}s to replacement {@link
	 *        L2Register}s having the same {@link L2Register#registerKind()}.
	 */
	public void replaceRegisters (
		final Map<L2Register<?>, L2Register<?>> registerRemap)
	{
		final List<L2Register<?>> sourcesBefore =
			new ArrayList<>(sourceRegisters);
		final List<L2Register<?>> destinationsBefore =
			new ArrayList<>(destinationRegisters);
		operandsDo(operand -> operand.replaceRegisters(registerRemap, this));
		sourceRegisters.replaceAll(r -> registerRemap.getOrDefault(r, r));
		destinationRegisters.replaceAll(r -> registerRemap.getOrDefault(r, r));
		assert sourceRegisters.size() == sourcesBefore.size();
		assert destinationRegisters.size() == destinationsBefore.size();
	}

	/**
	 * This instruction was just added to its {@link L2BasicBlock}.
	 */
	public void justAdded ()
	{
		operation().instructionWasAdded(this);
		operandsDo(operand -> operand.instructionWasAdded(this));
	}

	/**
	 * This instruction was just removed from its {@link L2BasicBlock}.
	 */
	public void justRemoved ()
	{
		operandsDo(operand -> operand.instructionWasRemoved(this));
	}

	/**
	 * Answer whether this instruction should be emitted during final code
	 * generation (from the non-SSA {@link L2ControlFlowGraph} into a flat
	 * sequence of {@code L2Instruction}s.  Allow the operation to decide.
	 *
	 * @return Whether to preserve this instruction during final code
	 *         generation.
	 */
	public boolean shouldEmit ()
	{
		return operation().shouldEmit(this);
	}

	@Override
	public String toString ()
	{
		final StringBuilder builder = new StringBuilder();
		operation()
			.toString(this, EnumSet.allOf(L2OperandType.class), builder);
		return builder.toString();
	}

	/**
	 * Extract the constant {@link AvailObject} from the {@link
	 * L2ConstantOperand} having the specified position in my array of operands.
	 *
	 * @param operandIndex
	 *        Which operand holds the constant.
	 * @return The constant value.
	 */
	public AvailObject constantAt (final int operandIndex)
	{
		return ((L2ConstantOperand) operand(operandIndex)).object;
	}

	/**
	 * Extract the {@link A_Bundle} from the {@link L2SelectorOperand} having
	 * the specified position in my array of operands.  Should only be used if
	 * it's known that the constant is in fact an {@link A_Bundle}, and the
	 * resulting {@link L2Chunk} should be dependent upon changes to its {@link
	 * A_Method}.
	 *
	 * @param operandIndex
	 *        Which operand holds the message bundle.
	 * @return The message bundle.
	 */
	public A_Bundle bundleAt (final int operandIndex)
	{
		return ((L2SelectorOperand) operand(operandIndex)).bundle;
	}

	/**
	 * Extract the immediate {@code int} from the {@link L2IntImmediateOperand}
	 * having the specified position in my array of operands.
	 *
	 * @param operandIndex
	 *        Which operand holds the immediate value.
	 * @return The immediate value.
	 */
	public int intImmediateAt (final int operandIndex)
	{
		return ((L2IntImmediateOperand) operand(operandIndex)).value;
	}

	/**
	 * Extract the immediate {@code double} from the {@link
	 * L2FloatImmediateOperand} having the specified position in my array of operands.
	 *
	 * @param operandIndex
	 *        Which operand holds the immediate value.
	 * @return The immediate value.
	 */
	public double floatImmediateAt (final int operandIndex)
	{
		return ((L2FloatImmediateOperand) operand(operandIndex)).value;
	}

	/**
	 * Extract the {@link L2PcOperand} having the specified position in my array
	 * of operands.
	 *
	 * @param operandIndex
	 *        Which operand holds the {@link L2PcOperand}.
	 * @return The {@link L2PcOperand} representing the destination {@link
	 *         L2BasicBlock} that will be reached if this branch direction is
	 *         taken.
	 */
	public L2PcOperand pcAt (final int operandIndex)
	{
		return ((L2PcOperand) operand(operandIndex));
	}

	/**
	 * Extract the program counter {@code int} from the {@link L2PcOperand}
	 * having the specified position in my array of operands.
	 *
	 * @param operandIndex
	 *        Which operand holds the program counter value.
	 * @return An int representing a target offset into a chunk's instructions.
	 */
	public int pcOffsetAt (final int operandIndex)
	{
		return pcAt(operandIndex).targetBlock().offset();
	}

	/**
	 * Extract the {@link Primitive} from the {@link L2PrimitiveOperand} having
	 * the specified position in my array of operands.
	 *
	 * @param operandIndex
	 *        Which operand holds a primitive.
	 * @return The specified {@link Primitive}.
	 */
	public Primitive primitiveAt (final int operandIndex)
	{
		return ((L2PrimitiveOperand) operand(operandIndex)).primitive;
	}

	/**
	 * Extract the {@link L2ReadIntOperand} having the specified position in my
	 * array of operands.
	 *
	 * @param operandIndex
	 *        Which operand holds a read of an integer register.
	 * @return The specified {@link L2ReadIntOperand} to read.
	 */
	public L2ReadIntOperand readIntRegisterAt (final int operandIndex)
	{
		return (L2ReadIntOperand) operand(operandIndex);
	}

	/**
	 * Extract the {@link L2WriteIntOperand} having the specified position in my
	 * array of operands.
	 *
	 * @param operandIndex
	 *        Which operand holds a write of an integer register.
	 * @return The specified {@link L2WriteIntOperand} to write.
	 */
	public L2WriteIntOperand writeIntRegisterAt (final int operandIndex)
	{
		return (L2WriteIntOperand) operand(operandIndex);
	}

	/**
	 * Extract the {@link L2ReadFloatOperand} having the specified position in
	 * my array of operands.
	 *
	 * @param operandIndex
	 *        Which operand holds a read of a double register.
	 * @return The specified {@link L2ReadFloatOperand} to read.
	 */
	public L2ReadFloatOperand readFloatRegisterAt (final int operandIndex)
	{
		return (L2ReadFloatOperand) operand(operandIndex);
	}

	/**
	 * Extract the {@link L2WriteFloatOperand} having the specified position in my
	 * array of operands.
	 *
	 * @param operandIndex
	 *        Which operand holds a write of a double register.
	 * @return The specified {@link L2WriteFloatOperand} to write.
	 */
	public L2WriteFloatOperand writeFloatRegisterAt (final int operandIndex)
	{
		return (L2WriteFloatOperand) operand(operandIndex);
	}

	/**
	 * Extract the {@link L2ReadPointerOperand} having the specified position in
	 * my array of operands.
	 *
	 * @param operandIndex
	 *        Which operand holds a read of an object register.
	 * @return The specified {@link L2ObjectRegister} to read.
	 */
	public L2ReadPointerOperand readObjectRegisterAt (final int operandIndex)
	{
		return (L2ReadPointerOperand) operand(operandIndex);
	}

	/**
	 * Extract the {@link L2WritePointerOperand} having the specified position
	 * in my array of operands.
	 *
	 * @param operandIndex
	 *        Which operand holds a write of an object register.
	 * @return The specified {@code L2WritePointerOperand}.
	 */
	public L2WritePointerOperand writeObjectRegisterAt (final int operandIndex)
	{
		return (L2WritePointerOperand) operand(operandIndex);
	}

	/**
	 * Extract the {@link List} of {@link L2ReadOperand}s from the {@link
	 * L2ReadVectorOperand} having the specified position in my array of
	 * operands.
	 *
	 * @param <RR>
	 *        The type of {@link L2ReadOperand}s in this vector.
	 * @param <R>
	 *        The type of {@link L2Register}s in this vector.
	 * @param <T>
	 *        The type of values in the registers.
	 * @param operandIndex
	 *        Which operand holds a read of a register vector.
	 * @return The list of {@link L2ReadPointerOperand}s.
	 */
	@SuppressWarnings("unchecked")
	public <
		RR extends L2ReadOperand<R, T>,
		R extends L2Register<T>,
		T extends A_BasicObject>
	List<RR> readVectorRegisterAt (final int operandIndex)
	{
		return (
			(L2ReadVectorOperand<RR, R, T>) operand(operandIndex)).elements();
	}

	/**
	 * Extract the {@link L2WritePhiOperand} having the specified position
	 * in my array of operands.
	 *
	 * @param operandIndex
	 *        Which operand holds a phi write.
	 * @return The specified {@code L2WritePhiOperand}.
	 */
	@SuppressWarnings("unchecked")
	public <U extends L2WritePhiOperand<?, ?>> U writePhiRegisterAt (
		final int operandIndex)
	{
		return (U) operand(operandIndex);
	}

	/**
	 * Transform this instruction's operands for the given {@link
	 * L2Retranslator}.
	 *
	 * @param inliner
	 *        The {@link L2Retranslator} through which to write this
	 *        instruction's equivalent effect.
	 */
	public L2Operand[] transformOperands (final L2Retranslator inliner)
	{
		final L2Operand[] newOperands = new L2Operand[operands().length];
		for (int i = 0; i < newOperands.length; i++)
		{
			newOperands[i] = inliner.transformOperand(operand(i));
		}
		return newOperands;
	}

	/**
	 * Write the equivalent of this instruction through the given {@link
	 * L2Retranslator}.  Certain types of {@link L2Operation}s are transformed
	 * in ways specific to inlining.
	 *
	 * @param inliner
	 *        The {@link L2Retranslator} through which to write this
	 *        instruction's equivalent effect.
	 */
	public void transformAndEmitOn (final L2Retranslator inliner)
	{
		operation().emitTransformedInstruction(
			this, transformOperands(inliner), inliner);
	}

	/**
	 * Translate the {@code L2Instruction} into corresponding JVM instructions.
	 *
	 * @param translator
	 *        The {@link JVMTranslator} responsible for the translation.
	 * @param method
	 *        The {@linkplain MethodVisitor method} into which the generated JVM
	 *        instructions will be written.
	 */
	public void translateToJVM (
		final JVMTranslator translator,
		final MethodVisitor method)
	{
		operation().translateToJVM(translator, method, this);
	}
}
