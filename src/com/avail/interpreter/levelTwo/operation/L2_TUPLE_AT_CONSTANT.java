/*
 * L2_TUPLE_AT_CONSTANT.java
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

import com.avail.descriptor.A_Tuple;
import com.avail.descriptor.A_Type;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.TupleDescriptor;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2OperandType;
import com.avail.interpreter.levelTwo.L2Operation;
import com.avail.interpreter.levelTwo.operand.L2ReadPointerOperand;
import com.avail.interpreter.levelTwo.operand.L2WritePointerOperand;
import com.avail.interpreter.levelTwo.register.L2ObjectRegister;
import com.avail.optimizer.L2Translator;
import com.avail.optimizer.RegisterSet;
import com.avail.optimizer.jvm.JVMTranslator;
import org.objectweb.asm.MethodVisitor;

import java.util.Set;

import static com.avail.interpreter.levelTwo.L2OperandType.*;
import static org.objectweb.asm.Opcodes.INVOKEINTERFACE;
import static org.objectweb.asm.Type.*;

/**
 * Extract an element at a fixed subscript from a {@link TupleDescriptor tuple}
 * that is known to be long enough, writing the element into a register.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class L2_TUPLE_AT_CONSTANT
extends L2Operation
{
	/**
	 * Construct an {@code L2_TUPLE_AT_CONSTANT}.
	 */
	private L2_TUPLE_AT_CONSTANT ()
	{
		super(
			READ_POINTER.is("tuple"),
			INT_IMMEDIATE.is("immediate subscript"),
			WRITE_POINTER.is("destination"));
	}

	/**
	 * Initialize the sole instance.
	 */
	public static final L2_TUPLE_AT_CONSTANT instance =
		new L2_TUPLE_AT_CONSTANT();

	@Override
	protected void propagateTypes (
		final L2Instruction instruction,
		final RegisterSet registerSet,
		final L2Translator translator)
	{
		final L2ReadPointerOperand tupleReg =
			instruction.readObjectRegisterAt(0);
		final int subscript = instruction.intImmediateAt(1);
		final L2WritePointerOperand destinationReg =
			instruction.writeObjectRegisterAt(2);

		final A_Type tupleType = tupleReg.type();
		final int minSize = tupleType.sizeRange().lowerBound().extractInt();
		assert minSize >= subscript;
		registerSet.typeAtPut(
			destinationReg.register(),
			tupleType.typeAtIndex(subscript),
			instruction);
	}

	@Override
	public void toString (
		final L2Instruction instruction,
		final Set<L2OperandType> desiredTypes,
		final StringBuilder builder)
	{
		assert this == instruction.operation();
		final L2ObjectRegister tupleReg =
			instruction.readObjectRegisterAt(0).register();
		final int subscript = instruction.intImmediateAt(1);
		final L2ObjectRegister destinationReg =
			instruction.writeObjectRegisterAt(2).register();

		renderPreamble(instruction, builder);
		builder.append(' ');
		builder.append(destinationReg);
		builder.append(" ← ");
		builder.append(tupleReg);
		builder.append('[');
		builder.append(subscript);
		builder.append(']');
	}

	@Override
	public void translateToJVM (
		final JVMTranslator translator,
		final MethodVisitor method,
		final L2Instruction instruction)
	{
		final L2ObjectRegister tupleReg =
			instruction.readObjectRegisterAt(0).register();
		final int subscript = instruction.intImmediateAt(1);
		final L2ObjectRegister destinationReg =
			instruction.writeObjectRegisterAt(2).register();

		// :: destination = tuple.tupleAt(subscript);
		translator.load(method, tupleReg);
		translator.literal(method, subscript);
		method.visitMethodInsn(
			INVOKEINTERFACE,
			getInternalName(A_Tuple.class),
			"tupleAt",
			getMethodDescriptor(getType(AvailObject.class), INT_TYPE),
			true);
		translator.store(method, destinationReg);
	}
}
