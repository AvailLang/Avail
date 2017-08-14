/**
 * L2_JUMP_IF_EQUALS_CONSTANT.java
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

import static com.avail.interpreter.levelTwo.L2OperandType.*;
import java.util.List;
import com.avail.descriptor.A_BasicObject;
import com.avail.descriptor.A_Type;
import com.avail.descriptor.AvailObject;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.levelTwo.*;
import com.avail.interpreter.levelTwo.register.L2ObjectRegister;
import com.avail.optimizer.L2Translator;
import com.avail.optimizer.L2Translator.L1NaiveTranslator;
import com.avail.optimizer.RegisterSet;

/**
 * Jump to the target if the object equals the constant.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public class L2_JUMP_IF_EQUALS_CONSTANT extends L2Operation
{
	/**
	 * Initialize the sole instance.
	 */
	public static final L2Operation instance =
		new L2_JUMP_IF_EQUALS_CONSTANT().init(
			PC.is("target"),
			READ_POINTER.is("value"),
			CONSTANT.is("constant"));

	@Override
	public void step (
		final L2Instruction instruction,
		final Interpreter interpreter)
	{
		final int target = instruction.pcAt(0);
		final L2ObjectRegister objectReg = instruction.readObjectRegisterAt(1);
		final A_BasicObject constant = instruction.constantAt(2);

		if (objectReg.in(interpreter).equals(constant))
		{
			interpreter.offset(target);
		}
	}

	@Override
	public boolean regenerate (
		final L2Instruction instruction,
		final RegisterSet registerSet,
		final L1NaiveTranslator naiveTranslator)
	{
		// Eliminate tests due to type propagation.
//		final int target = instruction.pcAt(0);
		final L2ObjectRegister objectReg = instruction.readObjectRegisterAt(1);
		final A_Type value = instruction.constantAt(2);

		final boolean canJump;
		final boolean mustJump;
		if (registerSet.hasConstantAt(objectReg))
		{
			final AvailObject constant = registerSet.constantAt(objectReg);
			mustJump = constant.equals(value);
			canJump = mustJump;
		}
		else
		{
			assert registerSet.hasTypeAt(objectReg);
			final A_Type knownType = registerSet.typeAt(objectReg);
			assert knownType != null;
			canJump = value.isInstanceOf(knownType);
			mustJump = false;
		}
		if (mustJump)
		{
			// It must be that value.  Always jump.  The instructions that
			// follow the jump will die and be eliminated next pass.
			naiveTranslator.addInstruction(
				L2_JUMP.instance,
				instruction.operands[0]);
			return true;
		}
		if (!canJump)
		{
			// It is never the specified value, so never jump.
			return true;
		}
		// The test could not be eliminated or improved.
		return super.regenerate(instruction, registerSet, naiveTranslator);
	}

	@Override
	protected void propagateTypes (
		final L2Instruction instruction,
		final List<RegisterSet> registerSets,
		final L2Translator translator)
	{
//		final int target = instruction.pcAt(0);
		final L2ObjectRegister objectReg = instruction.readObjectRegisterAt(1);
		final A_BasicObject value = instruction.constantAt(2);

		assert registerSets.size() == 2;
//		final RegisterSet fallThroughSet = registerSets.get(0);
		final RegisterSet postJumpSet = registerSets.get(1);

		postJumpSet.strengthenTestedValueAtPut(objectReg, value);
	}

	@Override
	public boolean hasSideEffect ()
	{
		// It jumps, which counts as a side effect.
		return true;
	}
}
