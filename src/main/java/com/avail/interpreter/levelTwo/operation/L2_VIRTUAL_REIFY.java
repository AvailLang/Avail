/*
 * L2_VIRTUAL_REIFY.java
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

import com.avail.descriptor.A_Continuation;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.ContinuationDescriptor;
import com.avail.descriptor.objects.A_BasicObject;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2OperandType;
import com.avail.interpreter.levelTwo.L2Operation;
import com.avail.interpreter.levelTwo.operand.L2IntImmediateOperand;
import com.avail.interpreter.levelTwo.operand.L2PcOperand;
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedOperand;
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedVectorOperand;
import com.avail.interpreter.levelTwo.operand.L2WriteBoxedOperand;
import com.avail.optimizer.jvm.JVMTranslator;
import org.objectweb.asm.MethodVisitor;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Set;

import static com.avail.descriptor.ContinuationDescriptor.createContinuationExceptFrameMethod;
import static com.avail.interpreter.Interpreter.chunkField;
import static com.avail.interpreter.levelTwo.L2OperandType.COMMENT;
import static org.objectweb.asm.Opcodes.*;
import static org.objectweb.asm.Type.*;

/**
 * This is a placeholder instruction, which is replaced after data flow
 * optimizations by:
 *
 * <ol>
 *    <li>{@link L2_REIFY},</li>
 *    <li>{@link L2_SAVE_ALL_AND_PC_TO_INT},</li>
 *    <li>{@link L2_CREATE_CONTINUATION},</li>
 *    <li>{@link L2_RETURN_FROM_REIFICATION_HANDLER}, and</li>
 *    <li>{@link L2_ENTER_L2_CHUNK}.</li>
 * </ol>
 *
 * <p>The {@link L2_CREATE_CONTINUATION}'s level one pc is set to an arbitrary
 * value, since it isn't used or ever exposed to level one code.</p>
 *
 * <p>This instruction is allowed to commute past any other instructions except
 * {@link L2_SAVE_ALL_AND_PC_TO_INT}.  Since these usually end up in off-ramps,
 * where reification is already happening, TODO We have to be careful.
 * </p>
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class L2_VIRTUAL_REIFY
extends L2Operation
{
	/**
	 * Construct an {@code L2_CREATE_CONTINUATION}.
	 */
	private L2_VIRTUAL_REIFY ()
	{
		super(
			COMMENT.is("usage comment"));
	}

	/**
	 * Initialize the sole instance.
	 */
	public static final L2_VIRTUAL_REIFY instance =
		new L2_VIRTUAL_REIFY();

	/**
	 * Extract the {@link List} of slot registers ({@link
	 * L2ReadBoxedOperand}s) that fed the given {@link L2Instruction} whose
	 * {@link L2Operation} is an {@code L2_CREATE_CONTINUATION}.
	 *
	 * @param instruction
	 *        The create-continuation instruction.
	 * @return The slots that were provided to the instruction for populating an
	 *         {@link ContinuationDescriptor continuation}.
	 */
	public static List<L2ReadBoxedOperand> slotRegistersFor (
		final L2Instruction instruction)
	{
		assert instruction.operation() == instance;
		final L2ReadBoxedVectorOperand vector = instruction.operand(5);
		return vector.elements();
	}

	/**
	 * This kind of instruction can be declared dead if its output is never
	 * used, but we have to convert it to an {@link L2_JUMP} to the fall-through
	 * PC for correctness.
	 *
	 * @param instruction The instruction about to be replaced.
	 * @return A replacement {@link L2_JUMP} instruction.
	 */
	@Override
	public L2Instruction optionalReplacementForDeadInstruction (
		final L2Instruction instruction)
	{
		// The fall-through PC.
		final L2PcOperand fallthrough = instruction.operand(7);
		return new L2Instruction(
			instruction.basicBlock,
			L2_JUMP.instance,
			new L2PcOperand(
				fallthrough.targetBlock(),
				false,
				fallthrough.manifest()));
	}

	@Override
	public void toString (
		final L2Instruction instruction,
		final Set<L2OperandType> desiredTypes,
		final StringBuilder builder)
	{
		assert this == instruction.operation();
		final L2ReadBoxedOperand function = instruction.operand(0);
		final L2ReadBoxedOperand caller = instruction.operand(1);
		final L2IntImmediateOperand levelOnePC = instruction.operand(2);
		final L2IntImmediateOperand levelOneStackp = instruction.operand(3);
		final L2ReadBoxedVectorOperand slots = instruction.operand(4);
		final L2WriteBoxedOperand destReg = instruction.operand(5);
//		final L2PcOperand onRamp = instruction.operand(6);
//		final L2PcOperand fallThrough = instruction.operand(7);

		renderPreamble(instruction, builder);
		builder.append(' ');
		builder.append(destReg);
		builder.append(" ← $[");
		builder.append(function);
		builder.append("]:pc=");
		builder.append(levelOnePC);
		builder.append(" stack=[");
		boolean first = true;
		for (final L2ReadBoxedOperand slot : slots.elements())
		{
			if (!first)
			{
				builder.append(",");
			}
			first = false;
			builder.append("\n\t\t");
			builder.append(slot);
		}
		builder.append("]\n\t[");
		builder.append(levelOneStackp);
		builder.append("] caller=");
		builder.append(caller);
		renderOperandsStartingAt(instruction, 6, desiredTypes, builder);
	}

	@Override
	public void translateToJVM (
		final JVMTranslator translator,
		final MethodVisitor method,
		final L2Instruction instruction)
	{
		final L2ReadBoxedOperand function = instruction.operand(0);
		final L2ReadBoxedOperand caller = instruction.operand(1);
		final L2IntImmediateOperand levelOnePC = instruction.operand(2);
		final L2IntImmediateOperand levelOneStackp = instruction.operand(3);
		final L2ReadBoxedVectorOperand slots = instruction.operand(4);
		final L2WriteBoxedOperand destReg = instruction.operand(5);
		final L2PcOperand onRamp = instruction.operand(6);
		final L2PcOperand fallThrough = instruction.operand(7);

		// :: continuation = createContinuationExceptFrame(
		// ::    function,
		// ::    caller,
		// ::    levelOnePC,
		// ::    levelOneStackp,
		// ::    interpreter.chunk,
		// ::    onRampOffset);
		translator.load(method, function.register());
		translator.load(method, caller.register());
		translator.literal(method, levelOnePC.value);
		translator.literal(method, levelOneStackp.value);
		translator.loadInterpreter(method);
		chunkField.generateRead(method);
		translator.intConstant(method, onRamp.offset());
		createContinuationExceptFrameMethod.generateCall(method);
		method.visitTypeInsn(CHECKCAST, getInternalName(AvailObject.class));
		final int slotCount = slots.elements().size();
		for (int i = 0; i < slotCount; i++)
		{
			final L2ReadBoxedOperand regRead = slots.elements().get(i);
			final @Nullable A_BasicObject constant = regRead.constantOrNull();
			// Skip if it's always nil, since the continuation was already
			// initialized with nils.
			if (constant == null || !constant.equalsNil())
			{
				// :: continuation.argOrLocalOrStackAtPut(«i + 1», «slots[i]»);
				method.visitInsn(DUP);
				translator.intConstant(method, i + 1);
				translator.load(method, slots.elements().get(i).register());
				method.visitMethodInsn(
					INVOKEINTERFACE,
					getInternalName(A_Continuation.class),
					"argOrLocalOrStackAtPut",
					getMethodDescriptor(
						VOID_TYPE,
						INT_TYPE,
						getType(AvailObject.class)),
					true);
			}
		}
		translator.store(method, destReg.register());
		// :: goto fallThrough;
		translator.jump(method, instruction, fallThrough);
	}
}
