/**
 * L2_EXPLODE_TUPLE.java
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
import com.avail.descriptor.TupleDescriptor;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2Operation;
import com.avail.interpreter.levelTwo.operand.L2ReadPointerOperand;
import com.avail.interpreter.levelTwo.operand.L2WritePointerOperand;
import com.avail.optimizer.L2Translator;
import com.avail.optimizer.RegisterSet;

import java.util.List;

import static com.avail.interpreter.levelTwo.L2OperandType.READ_POINTER;
import static com.avail.interpreter.levelTwo.L2OperandType.WRITE_VECTOR;

/**
 * Extract all elements from a known-length {@link TupleDescriptor tuple} into
 * a vector of registers.
 */
public class L2_EXPLODE_TUPLE extends L2Operation
{
	/**
	 * Initialize the sole instance.
	 */
	public static final L2Operation instance =
		new L2_EXPLODE_TUPLE().init(
			READ_POINTER.is("tuple"),
			WRITE_VECTOR.is("elements"));

	@Override
	public void step (
		final L2Instruction instruction,
		final Interpreter interpreter)
	{
		final L2ReadPointerOperand tupleReg =
			instruction.readObjectRegisterAt(0);
		final List<L2WritePointerOperand> elementsVector =
			instruction.writeVectorRegisterAt(1);

		final A_Tuple tuple = tupleReg.in(interpreter);
		// Make it immutable, at least until we have a framework for tracking
		// reference flow through registers and L2 operations more precisely.
		tuple.makeImmutable();
		final int tupleSize = tuple.tupleSize();
		assert tupleSize == elementsVector.size();
		for (int i = 1; i <= tupleSize; i++)
		{
			elementsVector.get(i - 1).set(tuple.tupleAt(i), interpreter);
		}
	}

	@Override
	protected void propagateTypes (
		final L2Instruction instruction,
		final RegisterSet registerSet,
		final L2Translator translator)
	{
		final L2ReadPointerOperand tupleReg =
			instruction.readObjectRegisterAt(0);
		final List<L2WritePointerOperand> elements =
			instruction.writeVectorRegisterAt(1);

		final A_Type tupleType = tupleReg.type();
		// Make all the contained types immutable.
		tupleType.makeImmutable();
		final int tupleSize = tupleType.sizeRange().lowerBound().extractInt();
		assert tupleSize == elements.size();
		for (int i = 1; i <= tupleSize; i++)
		{
			registerSet.typeAtPut(
				elements.get(i - 1).register(),
				tupleType.typeAtIndex(i),
				instruction);
		}
	}
}
