/**
 * L2_CREATE_TUPLE.java
 * Copyright © 1993-2014, The Avail Foundation, LLC.
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

import static com.avail.descriptor.TypeDescriptor.Types.ANY;
import static com.avail.interpreter.levelTwo.L2OperandType.*;
import java.util.*;
import com.avail.descriptor.*;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.levelTwo.*;
import com.avail.interpreter.levelTwo.register.*;
import com.avail.optimizer.L2Translator;
import com.avail.optimizer.RegisterSet;

/**
 * Create a {@link TupleDescriptor tuple} from the {@linkplain AvailObject
 * objects} in the specified registers.
 */
public class L2_CREATE_TUPLE extends L2Operation
{
	/**
	 * Initialize the sole instance.
	 */
	public final static L2Operation instance =
		new L2_CREATE_TUPLE().init(
			READ_VECTOR.is("elements"),
			WRITE_POINTER.is("tuple"));

	@Override
	public void step (
		final L2Instruction instruction,
		final Interpreter interpreter)
	{
		final L2RegisterVector elementsVector =
			instruction.readVectorRegisterAt(0);
		final L2ObjectRegister destinationReg =
			instruction.writeObjectRegisterAt(1);

		final List<L2ObjectRegister> registers = elementsVector.registers();
		final int size = registers.size();
		final A_Tuple tuple = ObjectTupleDescriptor.createUninitialized(size);
		for (int i = 1; i <= size; i++)
		{
			tuple.objectTupleAtPut(i, registers.get(i - 1).in(interpreter));
		}
		destinationReg.set(tuple, interpreter);
	}

	@Override
	protected void propagateTypes (
		final L2Instruction instruction,
		final RegisterSet registerSet,
		final L2Translator translator)
	{
		final L2RegisterVector elementsVector =
			instruction.readVectorRegisterAt(0);
		final L2ObjectRegister destinationReg =
			instruction.writeObjectRegisterAt(1);

		final List<L2ObjectRegister> registers = elementsVector.registers();
		final int size = registers.size();
		final A_Type sizeRange = IntegerDescriptor.fromInt(size).kind();
		final List<A_Type> types = new ArrayList<>(size);
		for (final L2ObjectRegister register : registers)
		{
			if (registerSet.hasTypeAt(register))
			{
				types.add(registerSet.typeAt(register));
			}
			else
			{
				types.add(ANY.o());
			}
		}
		final A_Type tupleType =
			TupleTypeDescriptor.tupleTypeForSizesTypesDefaultType(
				sizeRange,
				TupleDescriptor.fromList(types),
				BottomTypeDescriptor.bottom());
		tupleType.makeImmutable();
		registerSet.removeConstantAt(destinationReg);
		registerSet.typeAtPut(
			destinationReg,
			tupleType,
			instruction);
		if (elementsVector.allRegistersAreConstantsIn(registerSet))
		{
			final List<AvailObject> constants = new ArrayList<>(size);
			for (final L2ObjectRegister register : registers)
			{
				constants.add(registerSet.constantAt(register));
			}
			final A_Tuple tuple = TupleDescriptor.fromList(constants);
			tuple.makeImmutable();
			assert tuple.isInstanceOf(tupleType);
			registerSet.typeAtPut(
				destinationReg,
				InstanceTypeDescriptor.on(tuple),
				instruction);
			registerSet.constantAtPut(destinationReg, tuple, instruction);
		}
	}
}
