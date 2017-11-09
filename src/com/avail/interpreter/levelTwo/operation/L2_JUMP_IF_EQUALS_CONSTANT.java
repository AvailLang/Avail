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

import com.avail.descriptor.A_BasicObject;
import com.avail.descriptor.AvailObject;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2Operation;
import com.avail.interpreter.levelTwo.operand.L2PcOperand;
import com.avail.interpreter.levelTwo.operand.L2ReadPointerOperand;
import com.avail.optimizer.L1Translator;
import com.avail.optimizer.L2Translator;
import com.avail.optimizer.RegisterSet;
import com.avail.optimizer.StackReifier;
import com.avail.utility.evaluation.Transformer1NotNullArg;

import javax.annotation.Nullable;
import java.util.List;

import static com.avail.interpreter.levelTwo.L2OperandType.*;

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
			READ_POINTER.is("value"),
			CONSTANT.is("constant"),
			PC.is("if equal"),
			PC.is("if unequal"));

	@Override
	public Transformer1NotNullArg<Interpreter, StackReifier> actionFor (
		final L2Instruction instruction)
	{
		final int valueRegisterIndex =
			instruction.readObjectRegisterAt(0).finalIndex();
		final A_BasicObject constant = instruction.constantAt(1);
		final int ifEqual = instruction.pcOffsetAt(2);
		final int ifUnequal = instruction.pcOffsetAt(3);

		return interpreter ->
		{
			interpreter.offset(
				interpreter.pointerAt(valueRegisterIndex).equals(constant)
					? ifEqual
					: ifUnequal);
			return null;
		};
	}

	@Override
	public boolean regenerate (
		final L2Instruction instruction,
		final RegisterSet registerSet,
		final L1Translator translator)
	{
		// Eliminate tests due to type propagation.
		final L2ReadPointerOperand valueReg =
			instruction.readObjectRegisterAt(0);
		final A_BasicObject constant = instruction.constantAt(1);
		final L2PcOperand ifEqual = instruction.pcAt(2);
		final L2PcOperand ifUnequal = instruction.pcAt(3);

		final @Nullable A_BasicObject valueOrNull = valueReg.constantOrNull();
		if (valueOrNull != null)
		{
			// Compare them right now.
			translator.addInstruction(
				L2_JUMP.instance,
				valueOrNull.equals(constant) ? ifEqual : ifUnequal);
			return true;
		}
		if (!constant.isInstanceOf(valueReg.type()))
		{
			// They can't be equal.
			translator.addInstruction(L2_JUMP.instance, ifUnequal);
			return true;
		}
		// Otherwise it's still contingent.
		return super.regenerate(instruction, registerSet, translator);
	}

	@Override
	protected void propagateTypes (
		final L2Instruction instruction,
		final List<RegisterSet> registerSets,
		final L2Translator translator)
	{
		throw new UnsupportedOperationException();
//		final int ifEqual = instruction.pcAt(0);
//		final int ifUnequal = instruction.pcAt(1);
//		final L2ObjectRegister objectReg = instruction.readObjectRegisterAt(2);
//		final A_BasicObject value = instruction.constantAt(3);
//
//		assert registerSets.size() == 2;
//		final RegisterSet ifEqualSet = registerSets.get(0);
//		final RegisterSet ifUnequalSet = registerSets.get(1);

//TODO MvG - We need to be able to strengthen the variable by introducing a new
// version somehow.  Likewise, we can capture an is-not-a set of types along the
// other path.
//		postJumpSet.strengthenTestedValueAtPut(objectReg, value);
	}

	@Override
	public boolean hasSideEffect ()
	{
		// It jumps, which counts as a side effect.
		return true;
	}

	@Override
	public String debugNameIn (
		final L2Instruction instruction)
	{
		final AvailObject constant = instruction.constantAt(1);
		return name() + "(const=" + constant.typeTag() + ")";
	}
}
