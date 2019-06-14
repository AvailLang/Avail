/*
 * L2_TUPLE_AT_NO_FAIL.java
 * Copyright © 1993-2019, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 *  Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 *  Neither the name of the copyright holder nor the names of the contributors
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
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedOperand;
import com.avail.interpreter.levelTwo.operand.L2ReadIntOperand;
import com.avail.interpreter.levelTwo.operand.L2WriteBoxedOperand;
import com.avail.interpreter.levelTwo.register.L2BoxedRegister;
import com.avail.interpreter.levelTwo.register.L2IntRegister;
import com.avail.optimizer.L2Generator;
import com.avail.optimizer.RegisterSet;
import com.avail.optimizer.jvm.JVMTranslator;
import org.objectweb.asm.MethodVisitor;

import java.util.Set;

import static com.avail.descriptor.IntegerRangeTypeDescriptor.int32;
import static com.avail.interpreter.levelTwo.L2OperandType.*;
import static org.objectweb.asm.Opcodes.INVOKEINTERFACE;
import static org.objectweb.asm.Type.*;

/**
 * Extract an element at a subscript from a {@link TupleDescriptor tuple} that
 * is known to be within bounds, writing the element into a register.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class L2_TUPLE_AT_NO_FAIL
extends L2Operation
{
	/**
	 * Construct an {@code L2_TUPLE_AT_NO_FAIL}.
	 */
	private L2_TUPLE_AT_NO_FAIL ()
	{
		super(
			READ_BOXED.is("tuple"),
			READ_INT.is("int subscript"),
			WRITE_BOXED.is("destination"));
	}

	/**
	 * Initialize the sole instance.
	 */
	public static final L2_TUPLE_AT_NO_FAIL instance =
		new L2_TUPLE_AT_NO_FAIL();

	@Override
	protected void propagateTypes (
		final L2Instruction instruction,
		final RegisterSet registerSet,
		final L2Generator generator)
	{
		final L2ReadBoxedOperand tupleReg =
			instruction.readBoxedRegisterAt(0);
		final L2ReadIntOperand subscript = instruction.readIntRegisterAt(1);
		final L2WriteBoxedOperand destinationReg =
			instruction.writeBoxedRegisterAt(2);

		final A_Type tupleType = tupleReg.type();
		final A_Type bounded = subscript.type().typeIntersection(int32());
		final int minInt = bounded.lowerBound().extractInt();
		final int maxInt = bounded.upperBound().extractInt();
		registerSet.typeAtPut(
			destinationReg.register(),
			tupleType.unionOfTypesAtThrough(minInt, maxInt),
			instruction);
	}

	@Override
	public void toString (
		final L2Instruction instruction,
		final Set<L2OperandType> desiredTypes,
		final StringBuilder builder)
	{
		assert this == instruction.operation();
		final String tupleReg =
			instruction.readBoxedRegisterAt(0).registerString();
		final String subscript =
			instruction.readIntRegisterAt(1).registerString();
		final String destinationReg =
			instruction.writeBoxedRegisterAt(2).registerString();

		renderPreamble(instruction, builder);
		builder.append(' ');
		builder.append(destinationReg);
		builder.append(" ← ");
		builder.append(tupleReg);
		builder.append("(no fail)[");
		builder.append(subscript);
		builder.append(']');
	}

	@Override
	public void translateToJVM (
		final JVMTranslator translator,
		final MethodVisitor method,
		final L2Instruction instruction)
	{
		final L2BoxedRegister tupleReg =
			instruction.readBoxedRegisterAt(0).register();
		final L2IntRegister subscriptReg =
			instruction.readIntRegisterAt(1).register();
		final L2BoxedRegister destinationReg =
			instruction.writeBoxedRegisterAt(2).register();

		// :: destination = tuple.tupleAt(subscript);
		translator.load(method, tupleReg);
		translator.load(method, subscriptReg);
		method.visitMethodInsn(
			INVOKEINTERFACE,
			getInternalName(A_Tuple.class),
			"tupleAt",
			getMethodDescriptor(getType(AvailObject.class), INT_TYPE),
			true);
		translator.store(method, destinationReg);
	}
}
