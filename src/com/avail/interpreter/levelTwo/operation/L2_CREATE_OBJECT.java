/**
 * L2_CREATE_OBJECT.java
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

import com.avail.descriptor.A_Map;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.ObjectDescriptor;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2Operation;
import com.avail.interpreter.levelTwo.operand.L2ReadPointerOperand;
import com.avail.interpreter.levelTwo.operand.L2WritePointerOperand;
import com.avail.optimizer.StackReifier;

import javax.annotation.Nullable;
import java.util.List;

import static com.avail.descriptor.MapDescriptor.emptyMap;
import static com.avail.descriptor.ObjectDescriptor.objectFromMap;
import static com.avail.interpreter.levelTwo.L2OperandType.READ_VECTOR;
import static com.avail.interpreter.levelTwo.L2OperandType.WRITE_POINTER;

/**
 * Create a map from the specified key object registers and the corresponding
 * value object registers, then convert the map to an Avail {@link
 * ObjectDescriptor user-defined object} and write it into the specified object
 * register.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public class L2_CREATE_OBJECT extends L2Operation
{
	/**
	 * Initialize the sole instance.
	 */
	public static final L2Operation instance =
		new L2_CREATE_OBJECT().init(
			READ_VECTOR.is("field keys"),
			READ_VECTOR.is("field values"),
			WRITE_POINTER.is("new object"));

	@Override
	public @Nullable StackReifier step (
		final L2Instruction instruction,
		final Interpreter interpreter)
	{
		final List<L2ReadPointerOperand> keysVector =
			instruction.readVectorRegisterAt(0);
		final List<L2ReadPointerOperand> valuesVector =
			instruction.readVectorRegisterAt(1);
		final L2WritePointerOperand destinationObjectReg =
			instruction.writeObjectRegisterAt(2);

		final int size = keysVector.size();
		assert size == valuesVector.size();
		A_Map map = emptyMap();
		for (int i = 0; i < size; i++)
		{
			map = map.mapAtPuttingCanDestroy(
				keysVector.get(i).in(interpreter),
				valuesVector.get(i).in(interpreter),
				true);
		}
		final AvailObject object = objectFromMap(map);
		destinationObjectReg.set(object, interpreter);
		return null;
	}
}
