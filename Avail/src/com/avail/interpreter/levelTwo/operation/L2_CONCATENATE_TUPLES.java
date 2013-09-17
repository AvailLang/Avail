/**
 * L2_CONCATENATE_TUPLES.java
 * Copyright © 1993-2013, Mark van Gulik and Todd L Smith.
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

import static com.avail.interpreter.levelTwo.L2OperandType.*;
import java.util.List;
import com.avail.descriptor.*;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.levelTwo.*;
import com.avail.interpreter.levelTwo.register.L2ObjectRegister;
import com.avail.interpreter.levelTwo.register.L2RegisterVector;
import com.avail.optimizer.L2Translator;
import com.avail.optimizer.RegisterSet;

/**
 * Concatenate the tuples in the vector of object registers to produce a single
 * tuple in an output register.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public class L2_CONCATENATE_TUPLES extends L2Operation
{
	/**
	 * Initialize the sole instance.
	 */
	public final static L2Operation instance =
		new L2_CONCATENATE_TUPLES().init(
			READ_VECTOR.is("tuples to concatenate"),
			WRITE_POINTER.is("concatenated tuple"));

	@Override
	public void step (
		final L2Instruction instruction,
		final Interpreter interpreter)
	{
		final L2RegisterVector vector = instruction.readVectorRegisterAt(0);
		final L2ObjectRegister targetTupleReg =
			instruction.readObjectRegisterAt(1);

		final List<L2ObjectRegister> registers = vector.registers();
		final A_Tuple tuples =
			ObjectTupleDescriptor.createUninitialized(registers.size());
		for (int i = 1; i <= registers.size(); i++)
		{
			tuples.objectTupleAtPut(i, registers.get(i - 1).in(interpreter));
		}
		final A_Tuple concatenated = tuples.concatenateTuplesCanDestroy(true);
		targetTupleReg.set(concatenated, interpreter);
	}

	@Override
	protected void propagateTypes (
		final L2Instruction instruction,
		final RegisterSet registerSet,
		final L2Translator translator)
	{
		// Approximate it for now.  If testing the return type dynamically
		// becomes a bottleneck, we can improve this bound.
		final L2RegisterVector vector = instruction.readVectorRegisterAt(0);
		final L2ObjectRegister targetTupleReg =
			instruction.readObjectRegisterAt(1);

		final List<L2ObjectRegister> registers = vector.registers();
		if (registers.isEmpty())
		{
			registerSet.constantAtPut(
				targetTupleReg,
				TupleDescriptor.empty(),
				instruction);
			return;
		}
		int index = registers.size() - 1;
		A_Type resultType = registerSet.typeAt(registers.get(index));
		while (--index >= 0)
		{
			resultType = ConcatenatedTupleTypeDescriptor.concatenatingAnd(
				registerSet.typeAt(registers.get(index)),
				resultType);
		}
		registerSet.constantAtPut(targetTupleReg, resultType, instruction);
	}
}
