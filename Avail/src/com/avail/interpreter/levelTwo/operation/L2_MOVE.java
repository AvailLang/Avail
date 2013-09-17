/**
 * L2_MOVE.java
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
import com.avail.descriptor.AvailObject;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.levelTwo.*;
import com.avail.interpreter.levelTwo.register.L2ObjectRegister;
import com.avail.optimizer.*;

/**
 * Move an {@link AvailObject} from the source to the destination.  The
 * {@link L2Translator} creates more moves than are strictly necessary, but
 * various mechanisms cooperate to remove redundant inter-register moves.
 *
 * <p>
 * The object being moved is not made immutable by this operation, as that
 * is the responsibility of the {@link L2_MAKE_IMMUTABLE} operation.
 * </p>
 */
public class L2_MOVE extends L2Operation
{
	/**
	 * Initialize the sole instance.
	 */
	public final static L2Operation instance =
		new L2_MOVE().init(
			READ_POINTER.is("source"),
			WRITE_POINTER.is("destination"));

	@Override
	public void step (
		final L2Instruction instruction,
		final Interpreter interpreter)
	{
		final L2ObjectRegister sourceReg = instruction.readObjectRegisterAt(0);
		final L2ObjectRegister destinationReg =
			instruction.writeObjectRegisterAt(1);

		destinationReg.set(sourceReg.in(interpreter), interpreter);
	}

	@Override
	protected void propagateTypes (
		final L2Instruction instruction,
		final RegisterSet registerSet,
		final L2Translator translator)
	{
		final L2ObjectRegister sourceReg = instruction.readObjectRegisterAt(0);
		final L2ObjectRegister destinationReg =
			instruction.writeObjectRegisterAt(1);

		assert sourceReg != destinationReg;
		registerSet.removeConstantAt(destinationReg);
		if (registerSet.hasTypeAt(sourceReg))
		{
			registerSet.typeAtPut(
				destinationReg,
				registerSet.typeAt(sourceReg),
				instruction);
		}
		else
		{
			registerSet.removeTypeAt(destinationReg);
		}

		if (registerSet.hasConstantAt(sourceReg))
		{
			registerSet.constantAtPut(
				destinationReg,
				registerSet.constantAt(sourceReg),
				instruction);
		}
		registerSet.propagateMove(sourceReg, destinationReg, instruction);
	}
}
