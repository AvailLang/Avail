/*
 * L2_CREATE_CONTINUATION.java
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
package com.avail.interpreter.levelTwo.operation;

import com.avail.descriptor.A_Continuation;
import com.avail.descriptor.A_Function;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.ContinuationDescriptor;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.levelTwo.L2Chunk;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2OperandType;
import com.avail.interpreter.levelTwo.L2Operation;
import com.avail.interpreter.levelTwo.operand.L2Operand;
import com.avail.interpreter.levelTwo.operand.L2PcOperand;
import com.avail.interpreter.levelTwo.operand.L2ReadPointerOperand;
import com.avail.interpreter.levelTwo.register.L2ObjectRegister;
import com.avail.optimizer.jvm.JVMTranslator;
import org.objectweb.asm.MethodVisitor;

import java.util.List;
import java.util.Set;

import static com.avail.interpreter.levelTwo.L2NamedOperandType.Purpose.OFF_RAMP;
import static com.avail.interpreter.levelTwo.L2NamedOperandType.Purpose.ON_RAMP;
import static com.avail.interpreter.levelTwo.L2OperandType.*;
import static org.objectweb.asm.Opcodes.*;
import static org.objectweb.asm.Type.*;

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
			READ_POINTER.is("function"),
			READ_POINTER.is("caller"),
			INT_IMMEDIATE.is("level one pc"),
			INT_IMMEDIATE.is("stack pointer"),
			READ_VECTOR.is("slot values"),
			WRITE_POINTER.is("destination"),
			PC.is("on-ramp", ON_RAMP),
			PC.is("fall through after creation", OFF_RAMP),
			COMMENT.is("usage comment"));
	}

	/**
	 * Initialize the sole instance.
	 */
	public static final L2_CREATE_CONTINUATION instance =
		new L2_CREATE_CONTINUATION();

	/**
	 * Extract the {@link List} of slot registers ({@link
	 * L2ReadPointerOperand}s) that fed the given {@link L2Instruction} whose
	 * {@link L2Operation} is an {@code L2_CREATE_CONTINUATION}.
	 *
	 * @param instruction
	 *        The create-continuation instruction.
	 * @return The slots that were provided to the instruction for populating an
	 *         {@link ContinuationDescriptor continuation}.
	 */
	public static List<L2ReadPointerOperand> slotRegistersFor (
		final L2Instruction instruction)
	{
		assert instruction.operation == instance;
		return instruction.readVectorRegisterAt(5);
	}

	@Override
	public void toString (
		final L2Instruction instruction,
		final Set<L2OperandType> desiredTypes,
		final StringBuilder builder)
	{
		assert this == instruction.operation;
		final L2Operand function = instruction.readObjectRegisterAt(0);
		final L2Operand caller = instruction.readObjectRegisterAt(1);
		final int levelOnePC = instruction.intImmediateAt(2);
		final int levelOneStackp = instruction.intImmediateAt(3);
		final L2Operand slots = instruction.operands[4];
		final L2ObjectRegister destReg =
			instruction.writeObjectRegisterAt(5).register();
//		final int onRampOffset = instruction.pcOffsetAt(6);
//		final L2PcOperand fallThrough = instruction.pcAt(7);

		renderPreamble(instruction, builder);
		builder.append(' ');
		builder.append(destReg);
		builder.append(" ← $[");
		builder.append(function);
		builder.append("]:pc=");
		builder.append(levelOnePC);
		builder.append(" stack=");
		builder.append(slots);
		builder.append('[');
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
		final L2ObjectRegister functionReg =
			instruction.readObjectRegisterAt(0).register();
		final L2ObjectRegister callerReg =
			instruction.readObjectRegisterAt(1).register();
		final int levelOnePC = instruction.intImmediateAt(2);
		final int levelOneStackp = instruction.intImmediateAt(3);
		final List<L2ReadPointerOperand> slots =
			instruction.readVectorRegisterAt(4);
		final L2ObjectRegister destReg =
			instruction.writeObjectRegisterAt(5).register();
		final int onRampOffset = instruction.pcOffsetAt(6);
		final L2PcOperand fallThrough = instruction.pcAt(7);

		// :: continuation = createContinuationExceptFrame(
		// ::    function,
		// ::    caller,
		// ::    levelOnePC,
		// ::    levelOneStackp,
		// ::    interpreter.chunk,
		// ::    onRampOffset);
		translator.load(method, functionReg);
		translator.load(method, callerReg);
		translator.literal(method, levelOnePC);
		translator.literal(method, levelOneStackp);
		translator.loadInterpreter(method);
		method.visitFieldInsn(
			GETFIELD,
			getInternalName(Interpreter.class),
			"chunk",
			getDescriptor(L2Chunk.class));
		translator.intConstant(method, onRampOffset);
		method.visitMethodInsn(
			INVOKESTATIC,
			getInternalName(ContinuationDescriptor.class),
			"createContinuationExceptFrame",
			getMethodDescriptor(
				getType(A_Continuation.class),
				getType(A_Function.class),
				getType(A_Continuation.class),
				INT_TYPE,
				INT_TYPE,
				getType(L2Chunk.class),
				INT_TYPE),
			false);
		for (int i = 0, limit = slots.size(); i < limit; i++)
		{
			// :: continuation.argOrLocalOrStackAtPut(«i + 1», «slots[i]»);
			method.visitInsn(DUP);
			translator.intConstant(method, i + 1);
			translator.load(method, slots.get(i).register());
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
		translator.store(method, destReg);
		// :: goto fallThrough;
		translator.branch(method, instruction, fallThrough);
	}
}
