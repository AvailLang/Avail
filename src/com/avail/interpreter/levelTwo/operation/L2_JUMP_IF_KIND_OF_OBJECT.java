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

import com.avail.descriptor.A_Type;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2Operation;
import com.avail.interpreter.levelTwo.operand.L2ConstantOperand;
import com.avail.interpreter.levelTwo.operand.L2PcOperand;
import com.avail.interpreter.levelTwo.operand.L2ReadPointerOperand;
import com.avail.optimizer.L1Translator;
import com.avail.optimizer.L2Translator;
import com.avail.optimizer.RegisterSet;
import com.avail.optimizer.StackReifier;

import javax.annotation.Nullable;
import java.util.List;

import static com.avail.interpreter.levelTwo.L2OperandType.PC;
import static com.avail.interpreter.levelTwo.L2OperandType.READ_POINTER;

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
			READ_POINTER.is("value"),
			READ_POINTER.is("type"),
			PC.is("is kind"),
			PC.is("if not kind"));

	@Override
	public @Nullable StackReifier step (
		final L2Instruction instruction,
		final Interpreter interpreter)
	{
		final L2ReadPointerOperand valueReg =
			instruction.readObjectRegisterAt(0);
		final L2ReadPointerOperand typeReg =
			instruction.readObjectRegisterAt(1);
		final int isKindOffset = instruction.pcOffsetAt(2);
		final int isNotKindOffset = instruction.pcOffsetAt(3);

		interpreter.offset(
			valueReg.in(interpreter).isInstanceOf(typeReg.in(interpreter))
				? isKindOffset
				: isNotKindOffset);
		return null;
	}

	@Override
	public boolean regenerate (
		final L2Instruction instruction,
		final RegisterSet registerSet,
		final L1Translator translator)
	{
		final L2ReadPointerOperand valueReg =
			instruction.readObjectRegisterAt(0);
		final L2ReadPointerOperand typeReg =
			instruction.readObjectRegisterAt(1);
		final L2PcOperand isKind = instruction.pcAt(2);
		final L2PcOperand isNotKind = instruction.pcAt(3);

		if (registerSet.hasConstantAt(typeReg.register()))
		{
			// Replace this compare-and-branch with one that compares against
			// a constant.  This further opens the possibility of eliminating
			// one of the branches.
			final A_Type constantType =
				registerSet.constantAt(typeReg.register());
			translator.addInstruction(
				L2_JUMP_IF_KIND_OF_CONSTANT.instance,
				valueReg,
				new L2ConstantOperand(constantType),
				isKind,
				isNotKind);
			return true;
		}
		return super.regenerate(instruction, registerSet, translator);
	}

	@Override
	protected void propagateTypes (
		final L2Instruction instruction,
		final List<RegisterSet> registerSets,
		final L2Translator translator)
	{
		final L2ReadPointerOperand valueReg =
			instruction.readObjectRegisterAt(0);
		final L2ReadPointerOperand typeReg =
			instruction.readObjectRegisterAt(1);
//		final L2PcOperand isKind = instruction.pcAt(2);
//		final L2PcOperand isNotKind = instruction.pcAt(3);

		assert registerSets.size() == 2;
		final RegisterSet isKindSet = registerSets.get(0);
//		final RegisterSet isNotKindSet = registerSets.get(1);

		final A_Type type;
		if (isKindSet.hasConstantAt(typeReg.register()))
		{
			// The type is statically known.
			type = isKindSet.constantAt(typeReg.register());
		}
		else
		{
			// The exact type being tested against isn't known, but the metatype
			// is.
			assert isKindSet.hasTypeAt(typeReg.register());
			final A_Type meta = isKindSet.typeAt(typeReg.register());
			assert meta.isInstanceMeta();
			type = meta.instance();
		}
		isKindSet.strengthenTestedTypeAtPut(
			valueReg.register(),
			type.typeIntersection(isKindSet.typeAt(valueReg.register())));
	}

	@Override
	public boolean hasSideEffect ()
	{
		// It jumps, which counts as a side effect.
		return true;
	}
}
