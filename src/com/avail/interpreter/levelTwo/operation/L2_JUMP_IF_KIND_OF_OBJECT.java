/**
 * L2_JUMP_IF_KIND_OF_OBJECT.java
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

import com.avail.descriptor.A_Type;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.levelTwo.*;
import com.avail.interpreter.levelTwo.operand.L2ConstantOperand;
import com.avail.interpreter.levelTwo.operand.L2PcOperand;
import com.avail.interpreter.levelTwo.operand.L2ReadPointerOperand;
import com.avail.interpreter.levelTwo.register.L2ObjectRegister;
import com.avail.optimizer.L2Translator;
import com.avail.optimizer.L2Translator.L1NaiveTranslator;
import com.avail.optimizer.RegisterSet;

import java.util.List;

/**
 * Jump to the target if the value is an instance of the type.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public class L2_JUMP_IF_KIND_OF_OBJECT extends L2Operation
{
	/**
	 * Initialize the sole instance.
	 */
	public static final L2Operation instance =
		new L2_JUMP_IF_KIND_OF_OBJECT().init(
			PC.is("target"),
			READ_POINTER.is("value"),
			READ_POINTER.is("type"));

	@Override
	public void step (
		final L2Instruction instruction,
		final Interpreter interpreter)
	{
		final int target = instruction.pcAt(0);
		final L2ObjectRegister valueReg = instruction.readObjectRegisterAt(1);
		final L2ObjectRegister typeReg = instruction.readObjectRegisterAt(2);

		if (valueReg.in(interpreter).isInstanceOf(typeReg.in(interpreter)))
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
		final L2PcOperand target = (L2PcOperand)(instruction.operands[0]);
		final L2ObjectRegister valueReg = instruction.readObjectRegisterAt(1);
		final L2ObjectRegister typeReg = instruction.readObjectRegisterAt(2);

		if (registerSet.hasConstantAt(typeReg))
		{
			// Replace this compare-and-branch with one that compares against
			// a constant.  This further opens the possibility of eliminating
			// one of the branches.
			final A_Type constantType = registerSet.constantAt(typeReg);
			naiveTranslator.addInstruction(
				L2_JUMP_IF_KIND_OF_OBJECT.instance,
				target,
				new L2ReadPointerOperand(valueReg),
				new L2ConstantOperand(constantType));
			return true;
		}
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
		final L2ObjectRegister typeReg = instruction.readObjectRegisterAt(2);

		assert registerSets.size() == 2;
//		final RegisterSet fallThroughSet = registerSets.get(0);
		final RegisterSet postJumpSet = registerSets.get(1);

		final A_Type type;
		if (postJumpSet.hasConstantAt(typeReg))
		{
			// The type is statically known.
			type = postJumpSet.constantAt(typeReg);
		}
		else if (postJumpSet.hasTypeAt(typeReg))
		{
			// The exact type being tested against isn't known, but the metatype
			// is.
			final A_Type meta = postJumpSet.typeAt(typeReg);
			assert meta.isInstanceMeta();
			type = meta.instance();
		}
		else
		{
			type = ANY.o();
		}
		postJumpSet.strengthenTestedTypeAtPut(
			typeReg,
			type.typeIntersection(postJumpSet.typeAt(objectReg)));
	}

	@Override
	public boolean hasSideEffect ()
	{
		// It jumps, which counts as a side effect.
		return true;
	}
}
