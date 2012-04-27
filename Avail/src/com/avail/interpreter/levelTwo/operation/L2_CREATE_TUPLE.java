/**
 * L2_CREATE_TUPLE.java
 * Copyright © 1993-2012, Mark van Gulik and Todd L Smith.
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
import com.avail.annotations.NotNull;
import com.avail.descriptor.*;
import com.avail.interpreter.levelTwo.*;
import com.avail.interpreter.levelTwo.operand.*;
import com.avail.interpreter.levelTwo.register.*;
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
	public final static L2Operation instance = new L2_CREATE_TUPLE();

	static
	{
		instance.init(
			READ_VECTOR.is("elements"),
			WRITE_POINTER.is("tuple"));
	}

	@Override
	public void step (final @NotNull L2Interpreter interpreter)
	{
		final int valuesIndex = interpreter.nextWord();
		final int destIndex = interpreter.nextWord();
		final AvailObject indices = interpreter.vectorAt(valuesIndex);
		final int size = indices.tupleSize();
		final AvailObject tuple =
			ObjectTupleDescriptor.mutable().create(size);
		for (int i = 1; i <= size; i++)
		{
			tuple.tupleAtPut(
				i,
				interpreter.pointerAt(indices.tupleIntAt(i)));
		}
		interpreter.pointerAtPut(destIndex, tuple);
	}

	@Override
	public void propagateTypesInFor (
		final L2Instruction instruction,
		final RegisterSet registers)
	{
		final L2ReadVectorOperand sourcesOperand =
			(L2ReadVectorOperand) instruction.operands[0];
		final L2WritePointerOperand destinationOperand =
			(L2WritePointerOperand) instruction.operands[1];

		final L2RegisterVector sourceVector = sourcesOperand.vector;
		final int size = sourceVector.registers().size();
		final AvailObject sizeRange =
			IntegerDescriptor.fromInt(size).kind();
		List<AvailObject> types;
		types = new ArrayList<AvailObject>(sourceVector.registers().size());
		for (final L2Register register : sourceVector.registers())
		{
			if (registers.hasTypeAt(register))
			{
				types.add(registers.typeAt(register));
			}
			else
			{
				types.add(ANY.o());
			}
		}
		final AvailObject tupleType =
			TupleTypeDescriptor.tupleTypeForSizesTypesDefaultType(
				sizeRange,
				TupleDescriptor.fromCollection(types),
				BottomTypeDescriptor.bottom());
		tupleType.makeImmutable();
		registers.typeAtPut(destinationOperand.register, tupleType);
		registers.propagateWriteTo(destinationOperand.register);
		if (sourceVector.allRegistersAreConstantsIn(registers))
		{
			final List<AvailObject> constants = new ArrayList<AvailObject>(
				sourceVector.registers().size());
			for (final L2Register register : sourceVector.registers())
			{
				constants.add(registers.constantAt(register));
			}
			final AvailObject tuple = TupleDescriptor.fromCollection(
				constants);
			tuple.makeImmutable();
			assert tuple.isInstanceOf(tupleType);
			registers.constantAtPut(
				destinationOperand.register,
				tuple);
		}
		else
		{
			registers.removeConstantAt(
				destinationOperand.register);
		}
	}
}