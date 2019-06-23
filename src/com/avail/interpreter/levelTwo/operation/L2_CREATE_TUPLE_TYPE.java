/*
 * L2_CREATE_TUPLE_TYPE.java
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

import com.avail.descriptor.A_Type;
import com.avail.descriptor.TupleTypeDescriptor;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2OperandType;
import com.avail.interpreter.levelTwo.L2Operation;
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedOperand;
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedVectorOperand;
import com.avail.interpreter.levelTwo.operand.L2WriteBoxedOperand;
import com.avail.optimizer.L2Generator;
import com.avail.optimizer.RegisterSet;
import com.avail.optimizer.jvm.JVMTranslator;
import org.objectweb.asm.MethodVisitor;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.avail.descriptor.InstanceMetaDescriptor.instanceMeta;
import static com.avail.descriptor.TupleTypeDescriptor.tupleTypeForTypes;
import static com.avail.descriptor.TypeDescriptor.Types.ANY;
import static com.avail.interpreter.levelTwo.L2OperandType.READ_BOXED_VECTOR;
import static com.avail.interpreter.levelTwo.L2OperandType.WRITE_BOXED;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;
import static org.objectweb.asm.Type.*;

/**
 * Create a fixed sized {@link TupleTypeDescriptor tuple type} from the
 * {@linkplain A_Type types} in the specified registers.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class L2_CREATE_TUPLE_TYPE
extends L2Operation
{
	/**
	 * Construct an {@code L2_CREATE_TUPLE_TYPE}.
	 */
	private L2_CREATE_TUPLE_TYPE ()
	{
		super(
			READ_BOXED_VECTOR.is("element types"),
			WRITE_BOXED.is("tuple type"));
	}

	/**
	 * Initialize the sole instance.
	 */
	public static final L2_CREATE_TUPLE_TYPE instance =
		new L2_CREATE_TUPLE_TYPE();

	@Override
	protected void propagateTypes (
		final L2Instruction instruction,
		final RegisterSet registerSet,
		final L2Generator generator)
	{
		final L2ReadBoxedVectorOperand types = instruction.operand(0);
		final L2WriteBoxedOperand tupleType = instruction.operand(1);

		final List<L2ReadBoxedOperand> elements = types.elements();

		final int size = elements.size();
		if (registerSet.allRegistersAreConstant(elements))
		{
			// The types are all constants, so create the tuple type statically.
			final List<A_Type> constants = new ArrayList<>(size);
			for (final L2ReadBoxedOperand element : elements)
			{
				constants.add(registerSet.constantAt(element.register()));
			}
			final A_Type newTupleType = tupleTypeForTypes(constants);
			newTupleType.makeImmutable();
			registerSet.constantAtPut(
				tupleType.register(), newTupleType, instruction);
		}
		else
		{
			final List<A_Type> newTypes = new ArrayList<>(size);
			for (final L2ReadBoxedOperand element : elements)
			{
				if (registerSet.hasTypeAt(element.register()))
				{
					final A_Type meta = registerSet.typeAt(element.register());
					newTypes.add(
						meta.isInstanceMeta() ? meta.instance() : ANY.o());
				}
				else
				{
					newTypes.add(ANY.o());
				}
			}
			final A_Type newTupleType = tupleTypeForTypes(newTypes);
			final A_Type newTupleMeta = instanceMeta(newTupleType);
			newTupleMeta.makeImmutable();
			registerSet.removeConstantAt(tupleType.register());
			registerSet.typeAtPut(
				tupleType.register(), newTupleMeta, instruction);
		}
	}

	@Override
	public void toString (
		final L2Instruction instruction,
		final Set<L2OperandType> desiredTypes,
		final StringBuilder builder)
	{
		assert this == instruction.operation();
		final L2ReadBoxedVectorOperand types = instruction.operand(0);
		final L2WriteBoxedOperand tupleType = instruction.operand(1);

		renderPreamble(instruction, builder);
		builder.append(' ');
		builder.append(tupleType.registerString());
		builder.append(" ← ");
		builder.append(types.elements());
	}

	@Override
	public void translateToJVM (
		final JVMTranslator translator,
		final MethodVisitor method,
		final L2Instruction instruction)
	{
		final L2ReadBoxedVectorOperand types = instruction.operand(0);
		final L2WriteBoxedOperand tupleType = instruction.operand(1);

		// :: tupleType = TupleTypeDescriptor.tupleTypeForTypes(types);
		translator.objectArray(method, types.elements(), A_Type.class);
		method.visitMethodInsn(
			INVOKESTATIC,
			getInternalName(TupleTypeDescriptor.class),
			"tupleTypeForTypes",
			getMethodDescriptor(getType(A_Type.class), getType(A_Type[].class)),
			false);
		translator.store(method, tupleType.register());
	}
}
