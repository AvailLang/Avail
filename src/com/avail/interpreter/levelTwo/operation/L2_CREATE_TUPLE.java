/**
 * L2_CREATE_TUPLE.java
 * Copyright © 1993-2017, The Avail Foundation, LLC.
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
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2Operation;
import com.avail.interpreter.levelTwo.operand.L2ReadPointerOperand;
import com.avail.interpreter.levelTwo.operand.L2WritePointerOperand;
import com.avail.optimizer.L2Translator;
import com.avail.optimizer.RegisterSet;

import java.util.ArrayList;
import java.util.List;

import static com.avail.descriptor.BottomTypeDescriptor.bottom;
import static com.avail.descriptor.InstanceTypeDescriptor.instanceType;
import static com.avail.descriptor.IntegerDescriptor.fromInt;
import static com.avail.descriptor.ObjectTupleDescriptor
	.generateObjectTupleFrom;
import static com.avail.descriptor.TupleDescriptor.tupleFromList;
import static com.avail.descriptor.TupleTypeDescriptor
	.tupleTypeForSizesTypesDefaultType;
import static com.avail.descriptor.TypeDescriptor.Types.ANY;
import static com.avail.interpreter.levelTwo.L2OperandType.READ_VECTOR;
import static com.avail.interpreter.levelTwo.L2OperandType.WRITE_POINTER;

/**
 * Create a {@link TupleDescriptor tuple} from the {@linkplain AvailObject
 * objects} in the specified registers.
 */
public class L2_CREATE_TUPLE extends L2Operation
{
	/**
	 * Initialize the sole instance.
	 */
	public static final L2Operation instance =
		new L2_CREATE_TUPLE().init(
			READ_VECTOR.is("elements"),
			WRITE_POINTER.is("tuple"));

	@Override
	public void step (
		final L2Instruction instruction,
		final Interpreter interpreter)
	{
		final List<L2ReadPointerOperand> elements =
			instruction.readVectorRegisterAt(0);
		final L2WritePointerOperand destinationReg =
			instruction.writeObjectRegisterAt(1);

		final A_Tuple tuple = generateObjectTupleFrom(
			elements.size(), i -> elements.get(i - 1).in(interpreter));
		destinationReg.set(tuple, interpreter);
	}

	@Override
	protected void propagateTypes (
		final L2Instruction instruction,
		final RegisterSet registerSet,
		final L2Translator translator)
	{
		final List<L2ReadPointerOperand> elements =
			instruction.readVectorRegisterAt(0);
		final L2WritePointerOperand destinationReg =
			instruction.writeObjectRegisterAt(1);

		final int size = elements.size();
		final A_Type sizeRange = fromInt(size).kind();
		final List<A_Type> types = new ArrayList<>(size);
		for (final L2ReadPointerOperand element: elements)
		{
			if (registerSet.hasTypeAt(element.register()))
			{
				types.add(registerSet.typeAt(element.register()));
			}
			else
			{
				types.add(ANY.o());
			}
		}
		final A_Type tupleType =
			tupleTypeForSizesTypesDefaultType(sizeRange,
				tupleFromList(types), bottom());
		tupleType.makeImmutable();
		registerSet.removeConstantAt(destinationReg.register());
		registerSet.typeAtPut(
			destinationReg.register(),
			tupleType,
			instruction);
		if (registerSet.allRegistersAreConstant(elements))
		{
			final List<AvailObject> constants = new ArrayList<>(size);
			for (final L2ReadPointerOperand element : elements)
			{
				constants.add(registerSet.constantAt(element.register()));
			}
			final A_Tuple tuple = tupleFromList(constants);
			tuple.makeImmutable();
			assert tuple.isInstanceOf(tupleType);
			registerSet.typeAtPut(
				destinationReg.register(),
				instanceType(tuple),
				instruction);
			registerSet.constantAtPut(
				destinationReg.register(), tuple, instruction);
		}
	}

	/**
	 * Given an {@link L2Instruction} using this operation, extract the list of
	 * registers that supply the elements of the tuple.
	 *
	 * @param instruction
	 *        The tuple creation instruction to examine.
	 * @return The instruction's {@link List} of {@link L2ReadPointerOperand}s
	 *         that supply the tuple elements.
	 */
	public static List<L2ReadPointerOperand> tupleSourceRegistersOf (
		final L2Instruction instruction)
	{
		assert instruction.operation == instance;
		return instruction.readVectorRegisterAt(0);
	}
}
