/*
 * L2_SAVE_ALL_AND_PC_TO_INT.java
 * Copyright © 1993-2019, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice, this
 *     list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice, this
 *     list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
 *
 *  * Neither the name of the copyright holder nor the names of the contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
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
package com.avail.interpreter.levelTwo.operation;

import com.avail.descriptor.functions.ContinuationRegisterDumpDescriptor;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2OperandType;
import com.avail.interpreter.levelTwo.L2Operation;
import com.avail.interpreter.levelTwo.operand.L2PcOperand;
import com.avail.interpreter.levelTwo.operand.L2WriteBoxedOperand;
import com.avail.interpreter.levelTwo.operand.L2WriteIntOperand;
import com.avail.optimizer.jvm.JVMTranslator;
import org.objectweb.asm.MethodVisitor;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import static com.avail.descriptor.functions.ContinuationRegisterDumpDescriptor.createRegisterDumpMethod;
import static com.avail.interpreter.levelTwo.L2NamedOperandType.Purpose.REFERENCED_AS_INT;
import static com.avail.interpreter.levelTwo.L2NamedOperandType.Purpose.SUCCESS;
import static com.avail.interpreter.levelTwo.L2OperandType.*;

/**
 * Extract the given "reference" edge's target level two offset as an {@code
 * int}, then follow the fall-through edge.  The int value will be used in the
 * fall-through code to assemble a continuation, which, when returned into, will
 * start at the reference edge target.  Note that the L2 offset of the reference
 * edge is not known until just before JVM code generation.
 *
 * <p>This is a special operation, in that during final JVM code generation it
 * saves all objects in a register dump
 * ({@link ContinuationRegisterDumpDescriptor}), and the
 * {@link L2_ENTER_L2_CHUNK} at the reference target will restore them.</p>
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class L2_SAVE_ALL_AND_PC_TO_INT
extends L2Operation
{
	/**
	 * Construct an {@code L2_CREATE_CONTINUATION}.
	 */
	private L2_SAVE_ALL_AND_PC_TO_INT ()
	{
		super(
			PC.is("reference", REFERENCED_AS_INT),
			WRITE_INT.is("L2 address", SUCCESS),
			WRITE_BOXED.is("register dump", SUCCESS),
			PC.is("fall-through", SUCCESS));
	}

	/**
	 * Initialize the sole instance.
	 */
	public static final L2_SAVE_ALL_AND_PC_TO_INT instance =
		new L2_SAVE_ALL_AND_PC_TO_INT();

	/**
	 * From the given {@link L2Instruction}, extract the
	 * {@link L2PcOperand edge} that indicates the L2 offset to capture as an
	 * {@code int} in the second argument.  The conversion of the edge to an int
	 * occurs very late, in
	 * {@link #translateToJVM(JVMTranslator, MethodVisitor, L2Instruction)}, as
	 * does the decision about which registers should be captured in the
	 * register dump – and restored when the {@link L2_ENTER_L2_CHUNK} at
	 * the referenced edge's target is reached.
	 *
	 * @param instruction
	 *        The instruction from which to extract the reference edge.
	 * @return The referenced {@link L2PcOperand edge}.
	 */
	public static L2PcOperand referenceOf (final L2Instruction instruction)
	{
		assert instruction.operation() instanceof L2_SAVE_ALL_AND_PC_TO_INT;
		return instruction.operand(0);
	}

	@Override
	public List<L2PcOperand> targetEdges (final L2Instruction instruction)
	{
		assert this == instruction.operation();
		final List<L2PcOperand> edges = new ArrayList<>(2);
		edges.add(instruction.operand(0));
		edges.add(instruction.operand(3));
		return edges;
	}

	@Override
	public boolean hasSideEffect (
		final L2Instruction instruction)
	{
		// Don't let it be removed if either edge crosses a zone boundary.
		assert this == instruction.operation();
		final L2PcOperand target = instruction.operand(0);
//		final L2WriteIntOperand targetAsInt = instruction.operand(1);
//		final L2WriteBoxedOperand registerDump = instruction.operand(2);
		final L2PcOperand fallThrough = instruction.operand(3);

		return instruction.basicBlock().zone != fallThrough.targetBlock().zone
			|| instruction.basicBlock().zone != target.targetBlock(). zone;
	}

	@Override
	public boolean altersControlFlow ()
	{
		return true;
	}

	/**
	 * Answer true if this instruction leads to multiple targets, *multiple* of
	 * which can be reached.  This is not the same as a branch, in which only
	 * one will be reached for any circumstance of reaching this instruction.
	 * In particular, the {@code L2_SAVE_ALL_AND_PC_TO_INT} instruction jumps
	 * to its fall-through label, but after reification has saved the live
	 * register state, it gets restored again and winds up traversing the other
	 * edge.
	 *
	 * <p>This is an important distinction, in that this type of instruction
	 * should act as a barrier against redundancy elimination.  Otherwise an
	 * object with identity (i.e., a variable) created in the first branch won't
	 * be the same as the one produced again in the second branch.</p>
	 *
	 * <p>Also, we must treat as always-live-in to this instruction any values
	 * that are used in <em>either</em> branch, since they'll both be taken.</p>
	 *
	 * @return Whether multiple branches may be taken following the circumstance
	 *         of arriving at this instruction.
	 */
	@Override
	public boolean goesMultipleWays ()
	{
		return true;
	}

	@Override
	public L2Instruction optionalReplacementForDeadInstruction (
		final L2Instruction instruction)
	{
		// Nobody is using the targetAsInt, so nobody is synthesizing a
		// continuation that could ever resume, so nobody needs to capture the
		// live register state.  Turn the instruction into an unconditional jump
		// along the fallThrough edge.
		final L2PcOperand fallThroughEdge = instruction.operand(3);
		return new L2Instruction(
			instruction.basicBlock(),
			L2_JUMP.instance,
			fallThroughEdge);
	}

	@Override
	public void appendToWithWarnings (
		final L2Instruction instruction,
		final Set<L2OperandType> desiredTypes,
		final StringBuilder builder,
		final Consumer<Boolean> warningStyleChange)
	{
		assert this == instruction.operation();
		final L2PcOperand target = instruction.operand(0);
		final L2WriteIntOperand targetAsInt = instruction.operand(1);
		final L2WriteBoxedOperand registerDump = instruction.operand(2);
//		final L2PcOperand fallThrough = instruction.operand(3);

		renderPreamble(instruction, builder);
		builder.append(' ');
		builder.append(targetAsInt);
		builder.append(" ← address of label $[");
		builder.append(target.targetBlock().name());
		builder.append("]");
		if (target.offset() != -1)
		{
			builder.append("(=").append(target.offset()).append(")");
		}
		builder.append(",\n\tdump registers ");
		builder.append(registerDump);
	}

	@Override
	public void translateToJVM (
		final JVMTranslator translator,
		final MethodVisitor method,
		final L2Instruction instruction)
	{
		assert this == instruction.operation();
		final L2PcOperand target = instruction.operand(0);
		final L2WriteIntOperand targetAsInt = instruction.operand(1);
		final L2WriteBoxedOperand registerDump = instruction.operand(2);
		final L2PcOperand fallThrough = instruction.operand(3);

		target.createAndPushRegisterDumpArrays(translator, method);
		// :: [AvailObject[], long[]]
		createRegisterDumpMethod.generateCall(method);
		// :: [registerDump]
		translator.store(method, registerDump.register());
		// :: []

		translator.intConstant(method, target.offset());
		translator.store(method, targetAsInt.register());

		// Jump is usually elided.
		translator.jump(method, instruction, fallThrough);
	}
}
