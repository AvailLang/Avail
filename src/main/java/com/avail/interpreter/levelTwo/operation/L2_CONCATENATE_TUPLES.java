/*
 * L2_CONCATENATE_TUPLES.java
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

import com.avail.descriptor.A_Type;
import com.avail.descriptor.TupleDescriptor;
import com.avail.descriptor.tuples.A_Tuple;
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

import java.util.List;
import java.util.Set;

import static com.avail.descriptor.ConcatenatedTupleTypeDescriptor.concatenatingAnd;
import static com.avail.descriptor.TupleDescriptor.emptyTuple;
import static com.avail.interpreter.levelTwo.L2OperandType.READ_BOXED_VECTOR;
import static com.avail.interpreter.levelTwo.L2OperandType.WRITE_BOXED;
import static org.objectweb.asm.Opcodes.INVOKEINTERFACE;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;
import static org.objectweb.asm.Type.*;

/**
 * Concatenate the tuples in the vector of object registers to produce a single
 * tuple in an output register.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class L2_CONCATENATE_TUPLES
extends L2Operation
{
	/**
	 * Construct an {@code L2_CONCATENATE_TUPLES}.
	 */
	private L2_CONCATENATE_TUPLES ()
	{
		super(
			READ_BOXED_VECTOR.is("tuples to concatenate"),
			WRITE_BOXED.is("concatenated tuple"));
	}

	/**
	 * Initialize the sole instance.
	 */
	public static final L2_CONCATENATE_TUPLES instance =
		new L2_CONCATENATE_TUPLES();

	@Override
	protected void propagateTypes (
		final L2Instruction instruction,
		final RegisterSet registerSet,
		final L2Generator generator)
	{
		// Approximate it for now.  If testing the return type dynamically
		// becomes a bottleneck, we can improve this bound.
		final L2ReadBoxedVectorOperand tuples = instruction.operand(0);
		final L2WriteBoxedOperand output = instruction.operand(1);

		if (tuples.elements().isEmpty())
		{
			registerSet.constantAtPut(
				output.register(),
				emptyTuple(),
				instruction);
			return;
		}
		int index = tuples.elements().size() - 1;
		A_Type resultType = tuples.elements().get(index).type();
		while (--index >= 0)
		{
			resultType = concatenatingAnd(
				tuples.elements().get(index).type(), resultType);
		}
		registerSet.constantAtPut(
			output.register(), resultType, instruction);
	}

	@Override
	public void toString (
		final L2Instruction instruction,
		final Set<L2OperandType> desiredTypes,
		final StringBuilder builder)
	{
		assert this == instruction.operation();
		final L2ReadBoxedVectorOperand tuples = instruction.operand(0);
		final L2WriteBoxedOperand output = instruction.operand(1);

		renderPreamble(instruction, builder);
		builder.append(' ');
		builder.append(output.registerString());
		builder.append(" ← ");
		for (int i = 0, limit = tuples.elements().size(); i < limit; i++)
		{
			if (i > 0)
			{
				builder.append(" ++ ");
			}
			final L2ReadBoxedOperand element = tuples.elements().get(i);
			builder.append(element.registerString());
		}
	}

	@Override
	public void translateToJVM (
		final JVMTranslator translator,
		final MethodVisitor method,
		final L2Instruction instruction)
	{
		final L2ReadBoxedVectorOperand tuples = instruction.operand(0);
		final L2WriteBoxedOperand output = instruction.operand(1);

		final List<L2ReadBoxedOperand> elements = tuples.elements();
		final int tupleCount = elements.size();
		if (tupleCount == 0)
		{
			method.visitMethodInsn(
				INVOKESTATIC,
				getInternalName(TupleDescriptor.class),
				"emptyTuple",
				getMethodDescriptor(getType(A_Tuple.class)),
				false);
		}
		else
		{
			translator.load(method, elements.get(0).register());
			for (int i = 1; i < tupleCount; i++)
			{
				translator.load(method, elements.get(i).register());
				translator.intConstant(method, 1);
				method.visitMethodInsn(
					INVOKEINTERFACE,
					getInternalName(A_Tuple.class),
					"concatenateWith",
					getMethodDescriptor(
						getType(A_Tuple.class),
						getType(A_Tuple.class),
						BOOLEAN_TYPE),
				true);
			}
		}
		translator.store(method, output.register());
	}
}
