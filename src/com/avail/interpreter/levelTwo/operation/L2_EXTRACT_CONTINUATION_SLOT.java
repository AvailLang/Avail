/*
 * L2_EXTRACT_CONTINUATION_SLOT.java
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
import com.avail.descriptor.AvailObject;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2Operation;
import com.avail.interpreter.levelTwo.register.L2ObjectRegister;
import com.avail.optimizer.L2Translator;
import com.avail.optimizer.RegisterSet;
import com.avail.optimizer.jvm.JVMTranslator;
import org.objectweb.asm.MethodVisitor;

import static com.avail.interpreter.levelTwo.L2OperandType.*;
import static org.objectweb.asm.Opcodes.INVOKEINTERFACE;
import static org.objectweb.asm.Type.*;

/**
 * Extract a single slot from a continuation.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public class L2_EXTRACT_CONTINUATION_SLOT
extends L2Operation
{
	/**
	 * Initialize the sole instance.
	 */
	public static final L2Operation instance =
		new L2_EXTRACT_CONTINUATION_SLOT().init(
			READ_POINTER.is("continuation"),
			IMMEDIATE.is("slot index"),
			WRITE_POINTER.is("extracted slot"));

	@Override
	protected void propagateTypes (
		final L2Instruction instruction,
		final RegisterSet registerSet,
		final L2Translator translator)
	{
//		final L2ReadPointerOperand continuationReg =
//			instruction.readObjectRegisterAt(0);
//		final int slotIndex = instruction.immediateAt(1);
//		final L2WritePointerOperand explodedSlot =
//			instruction.writeObjectRegisterAt(2);

		// Update the type and value information to agree with the type and
		// value known to be in that continuation slot.  Note that there are no
		// exposed mechanisms for tampering with a continuation without setting
		// its chunk to the default chunk, so we can be assured that the same
		// values written to slots in an off-ramp are present in the
		// continuation when it reaches the subsequent on-ramp.

		//TODO MvG - This is deprecated anyhow.
	}

	@Override
	public void translateToJVM (
		final JVMTranslator translator,
		final MethodVisitor method,
		final L2Instruction instruction)
	{
		// Extract a single slot from the given continuation.
		final L2ObjectRegister continuationReg =
			instruction.readObjectRegisterAt(0).register();
		final int slotIndex = instruction.immediateAt(1);
		final L2ObjectRegister explodedSlotReg =
			instruction.writeObjectRegisterAt(2).register();

		// :: «slot[i]» = continuation.argOrLocalOrStackAt(«slotIndex»);
		translator.load(method, continuationReg);
		translator.intConstant(method, slotIndex);
		method.visitMethodInsn(
			INVOKEINTERFACE,
			getInternalName(A_Continuation.class),
			"argOrLocalOrStackAt",
			getMethodDescriptor(getType(AvailObject.class), INT_TYPE),
			true);
		translator.store(method, explodedSlotReg);
	}
}
