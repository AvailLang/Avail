/**
 * L2_CREATE_TUPLE_TYPE.java
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
 * Create a fixed sized {@link TupleTypeDescriptor tuple type} from the
 * {@linkplain A_Type types} in the specified registers.
 */
public class L2_CREATE_TUPLE_TYPE extends L2Operation
{
	/**
	 * Initialize the sole instance.
	 */
	public static final L2Operation instance =
		new L2_CREATE_TUPLE_TYPE().init(
			READ_VECTOR.is("element types"),
			WRITE_POINTER.is("tuple type"));

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
		final A_Type[] types = new A_Type[size];
		for (int i = 0; i < size; i++)
		{
			types[i] = registers.get(i).in(interpreter);
		}
		final A_Type tupleType = TupleTypeDescriptor.forTypes(types);
		destinationReg.set(tupleType, interpreter);
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
		if (elementsVector.allRegistersAreConstantsIn(registerSet))
		{
			// The types are all constants, so create the tuple type statically.
			final List<A_Type> constants = new ArrayList<>(size);
			for (final L2ObjectRegister register : registers)
			{
				constants.add(registerSet.constantAt(register));
			}
			final A_Type tupleType = TupleTypeDescriptor.forTypes(
				constants.toArray(new A_Type[size]));
			tupleType.makeImmutable();
			registerSet.constantAtPut(destinationReg, tupleType, instruction);
		}
		else
		{
			final List<A_Type> types = new ArrayList<>(size);
			for (final L2ObjectRegister register : registers)
			{
				if (registerSet.hasTypeAt(register))
				{
					final A_Type meta = registerSet.typeAt(register);
					types.add(
						meta.isInstanceMeta() ? meta.instance() : ANY.o());
				}
				else
				{
					types.add(ANY.o());
				}
			}
			final A_Type tupleType = TupleTypeDescriptor.forTypes(
				types.toArray(new A_Type[size]));
			final A_Type tupleMeta = InstanceMetaDescriptor.on(tupleType);
			tupleMeta.makeImmutable();
			registerSet.removeConstantAt(destinationReg);
			registerSet.typeAtPut(destinationReg, tupleMeta, instruction);
		}
	}
}
