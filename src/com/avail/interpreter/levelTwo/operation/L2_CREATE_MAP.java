/*
 * L2_CREATE_MAP.java
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

import com.avail.descriptor.A_BasicObject;
import com.avail.descriptor.A_Map;
import com.avail.descriptor.MapDescriptor;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2OperandType;
import com.avail.interpreter.levelTwo.L2Operation;
import com.avail.interpreter.levelTwo.operand.L2ReadPointerOperand;
import com.avail.interpreter.levelTwo.register.L2ObjectRegister;
import com.avail.optimizer.jvm.JVMTranslator;
import org.objectweb.asm.MethodVisitor;

import java.util.List;
import java.util.Set;

import static com.avail.interpreter.levelTwo.L2OperandType.READ_VECTOR;
import static com.avail.interpreter.levelTwo.L2OperandType.WRITE_POINTER;
import static org.objectweb.asm.Opcodes.INVOKEINTERFACE;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;
import static org.objectweb.asm.Type.*;

/**
 * Create a map from the specified key object registers and the corresponding
 * value object registers (writing the map into a specified object register).
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class L2_CREATE_MAP
extends L2Operation
{
	/**
	 * Construct an {@code L2_CREATE_MAP}.
	 */
	private L2_CREATE_MAP ()
	{
		super(
			READ_VECTOR.is("keys"),
			READ_VECTOR.is("values"),
			WRITE_POINTER.is("new map"));
	}

	/**
	 * Initialize the sole instance.
	 */
	public static final L2_CREATE_MAP instance = new L2_CREATE_MAP();

	@Override
	public void toString (
		final L2Instruction instruction,
		final Set<L2OperandType> desiredTypes,
		final StringBuilder builder)
	{
		assert this == instruction.operation;
		final List<L2ReadPointerOperand> keysVector =
			instruction.readVectorRegisterAt(0);
		final List<L2ReadPointerOperand> valuesVector =
			instruction.readVectorRegisterAt(1);
		final L2ObjectRegister destinationMapReg =
			instruction.writeObjectRegisterAt(2).register();

		renderPreamble(instruction, builder);
		builder.append(' ');
		builder.append(destinationMapReg);
		builder.append(" ← {");
		for (int i = 0, limit = keysVector.size(); i < limit; i++)
		{
			if (i > 0)
			{
				builder.append(", ");
			}
			final L2ReadPointerOperand key = keysVector.get(i);
			final L2ReadPointerOperand value = valuesVector.get(i);
			builder.append(key);
			builder.append("→");
			builder.append(value);
		}
		builder.append('}');
	}

	@Override
	public void translateToJVM (
		final JVMTranslator translator,
		final MethodVisitor method,
		final L2Instruction instruction)
	{
		final List<L2ReadPointerOperand> keysVector =
			instruction.readVectorRegisterAt(0);
		final List<L2ReadPointerOperand> valuesVector =
			instruction.readVectorRegisterAt(1);
		final L2ObjectRegister destinationMapReg =
			instruction.writeObjectRegisterAt(2).register();

		// :: map = MapDescriptor.emptyMap();
		method.visitMethodInsn(
			INVOKESTATIC,
			getInternalName(MapDescriptor.class),
			"emptyMap",
			getMethodDescriptor(getType(A_Map.class)),
			false);
		final int limit = keysVector.size();
		assert limit == valuesVector.size();
		for (int i = 0; i < limit; i++)
		{
			// :: map = map.mapAtPuttingCanDestroy(
			// ::    «keysVector[i]», «valuesVector[i]», true);
			translator.load(method, keysVector.get(i).register());
			translator.load(method, valuesVector.get(i).register());
			translator.intConstant(method, 1);
			method.visitMethodInsn(
				INVOKEINTERFACE,
				getInternalName(A_Map.class),
				"mapAtPuttingCanDestroy",
				getMethodDescriptor(
					getType(A_Map.class),
					getType(A_BasicObject.class),
					getType(A_BasicObject.class),
					BOOLEAN_TYPE),
				true);
		}
		// :: destinationMap = map;
		translator.store(method, destinationMapReg);
	}
}
