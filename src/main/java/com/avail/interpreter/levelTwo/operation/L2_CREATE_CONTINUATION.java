/*
 * L2_CREATE_CONTINUATION.java
 * Copyright © 1993-2019, The Avail Foundation, LLC.
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
package com.avail.interpreter.levelTwo.operation;

import com.avail.descriptor.objects.A_BasicObject;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2OperandType;
import com.avail.interpreter.levelTwo.L2Operation;
import com.avail.interpreter.levelTwo.operand.L2IntImmediateOperand;
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedOperand;
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedVectorOperand;
import com.avail.interpreter.levelTwo.operand.L2ReadIntOperand;
import com.avail.interpreter.levelTwo.operand.L2WriteBoxedOperand;
import com.avail.optimizer.jvm.JVMTranslator;
import org.objectweb.asm.MethodVisitor;

import javax.annotation.Nullable;
import java.util.Set;

import static com.avail.descriptor.AvailObject.argOrLocalOrStackAtPutMethod;
import static com.avail.descriptor.ContinuationDescriptor.createContinuationExceptFrameMethod;
import static com.avail.interpreter.Interpreter.chunkField;
import static com.avail.interpreter.levelTwo.L2OperandType.*;
import static org.objectweb.asm.Opcodes.DUP;

/**
 * Create a continuation from scratch, using the specified caller, function,
 * constant level one program counter, constant stack pointer, continuation
 * slot values, and level two program counter.  Write the new continuation
 * into the specified register.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class L2_CREATE_CONTINUATION
extends L2Operation
{
	/**
	 * Construct an {@code L2_CREATE_CONTINUATION}.
	 */
	private L2_CREATE_CONTINUATION ()
	{
		super(
			READ_BOXED.is("function"),
			READ_BOXED.is("caller"),
			INT_IMMEDIATE.is("level one pc"),
			INT_IMMEDIATE.is("stack pointer"),
			READ_BOXED_VECTOR.is("slot values"),
			WRITE_BOXED.is("destination"),
			READ_INT.is("label address"),
			READ_BOXED.is("register dump"),
			COMMENT.is("usage comment"));
	}

	/**
	 * Initialize the sole instance.
	 */
	public static final L2_CREATE_CONTINUATION instance =
		new L2_CREATE_CONTINUATION();

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
//		final L2ReadIntOperand labelIntReg = instruction.operand(6);
//		final L2ReadBoxedOperand registerDumpReg = instruction.operand(7);

		renderPreamble(instruction, builder);
		builder.append(' ');
		builder.append(destReg);
		builder.append(" ← $[");
		builder.append(function);
		builder.append("]\n\tpc=");
		builder.append(levelOnePC);
		builder.append("\n\tstack=[");
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
		builder.append("]\n\t[stackp=");
		builder.append(levelOneStackp);
		builder.append("]\n\tcaller=");
		builder.append(caller);
		renderOperandsStartingAt(instruction, 6, desiredTypes, builder);
	}

	@Override
	public void translateToJVM (
		final JVMTranslator translator,
		final MethodVisitor method,
		final L2Instruction instruction)
	{
		assert this == instruction.operation();
		final L2ReadBoxedOperand function = instruction.operand(0);
		final L2ReadBoxedOperand caller = instruction.operand(1);
		final L2IntImmediateOperand levelOnePC = instruction.operand(2);
		final L2IntImmediateOperand levelOneStackp = instruction.operand(3);
		final L2ReadBoxedVectorOperand slots = instruction.operand(4);
		final L2WriteBoxedOperand destReg = instruction.operand(5);
		final L2ReadIntOperand labelIntReg = instruction.operand(6);
		final L2ReadBoxedOperand registerDumpReg = instruction.operand(7);

		// :: continuation = createContinuationExceptFrame(
		// ::    function,
		// ::    caller,
		// ::    registerDump
		// ::    levelOnePC,
		// ::    levelOneStackp,
		// ::    interpreter.chunk,
		// ::    onRampOffset);
		translator.load(method, function.register());
		translator.load(method, caller.register());
		translator.load(method, registerDumpReg.register());
		translator.literal(method, levelOnePC.value);
		translator.literal(method, levelOneStackp.value);
		translator.loadInterpreter(method);
		chunkField.generateRead(method);
		translator.load(method, labelIntReg.register());
		createContinuationExceptFrameMethod.generateCall(method);
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
				argOrLocalOrStackAtPutMethod.generateCall(method);
			}
		}
		translator.store(method, destReg.register());
	}
}
