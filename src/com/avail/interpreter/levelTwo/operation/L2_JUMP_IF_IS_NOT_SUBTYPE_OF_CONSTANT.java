/**
 * L2_JUMP_IF_IS_NOT_SUBTYPE_OF_CONSTANT.java
 * Copyright © 1993-2015, The Avail Foundation, LLC.
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
import com.avail.descriptor.A_Type;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.InstanceMetaDescriptor;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.levelTwo.*;
import com.avail.interpreter.levelTwo.operand.L2ConstantOperand;
import com.avail.interpreter.levelTwo.operand.L2ReadPointerOperand;
import com.avail.interpreter.levelTwo.register.L2ObjectRegister;
import com.avail.optimizer.L2Translator;
import com.avail.optimizer.L2Translator.L1NaiveTranslator;
import com.avail.optimizer.RegisterSet;

/**
 * Jump to the target if the object (a type) is not a subtype of the constant
 * type.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public class L2_JUMP_IF_IS_NOT_SUBTYPE_OF_CONSTANT extends L2Operation
{
	/**
	 * Initialize the sole instance.
	 */
	public final static L2Operation instance =
		new L2_JUMP_IF_IS_NOT_SUBTYPE_OF_CONSTANT().init(
			PC.is("target"),
			READ_POINTER.is("type to check"),
			CONSTANT.is("constant type"));

	@Override
	public void step (
		final L2Instruction instruction,
		final Interpreter interpreter)
	{
		final int target = instruction.pcAt(0);
		final L2ObjectRegister typeReg = instruction.readObjectRegisterAt(1);
		final A_Type constantType = instruction.constantAt(2);

		if (!typeReg.in(interpreter).isSubtypeOf(constantType))
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
		final L2ObjectRegister typeReg = instruction.readObjectRegisterAt(1);
		final A_Type constantType = instruction.constantAt(2);

		boolean canJump = false;
		boolean mustJump = false;
		A_Type intersection = null;
		if (registerSet.hasConstantAt(typeReg))
		{
			final AvailObject type = registerSet.constantAt(typeReg);
			mustJump = canJump = !type.isSubtypeOf(constantType);
			intersection = constantType.typeIntersection(type);
		}
		else
		{
			assert registerSet.hasTypeAt(typeReg);
			final A_Type knownMeta = registerSet.typeAt(typeReg);
			assert knownMeta != null;
			final A_Type knownType = knownMeta.instance();
			intersection = constantType.typeIntersection(knownType);
			if (intersection.isBottom())
			{
				mustJump = canJump = true;
			}
			else if (knownType.isSubtypeOf(constantType))
			{
				mustJump = canJump = false;
			}
			else
			{
				mustJump = false;
				canJump = true;
			}
		}
		if (mustJump)
		{
			// It can never be a subtype of the constantType.  Always jump.  The
			// instructions that follow the jump will become dead code and
			// be eliminated next pass.
			assert canJump;
			naiveTranslator.addInstruction(
				L2_JUMP.instance,
				instruction.operands[0]);
			return true;
		}
		assert !mustJump;
		if (!canJump)
		{
			// It is always a subtype of the constantType, so never jump.
			return true;
		}
		// Since it's already an X and we're testing for Y, we might be
		// better off testing for an X∩Y instead.  Let's just assume it's
		// quicker for now.  Eventually we can extend this idea to testing
		// things other than types, such as if we know we have a tuple but we
		// want to dispatch based on the tuple's size.
		if (!intersection.equals(constantType))
		{
			naiveTranslator.addInstruction(
				L2_JUMP_IF_IS_NOT_SUBTYPE_OF_CONSTANT.instance,
				instruction.operands[0],
				new L2ReadPointerOperand(typeReg),
				new L2ConstantOperand(intersection));
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
		final L2ObjectRegister typeReg = instruction.readObjectRegisterAt(1);
		final A_Type constantType = instruction.constantAt(2);

		assert registerSets.size() == 2;
		final RegisterSet fallThroughSet = registerSets.get(0);
//		final RegisterSet postJumpSet = registerSets.get(1);

		assert fallThroughSet.hasTypeAt(typeReg);
		if (fallThroughSet.hasConstantAt(typeReg))
		{
			// The *exact* type is already known.  Don't weaken it by recording
			// type information for it (a meta).
		}
		else
		{
			final A_Type existingMeta = fallThroughSet.typeAt(typeReg);
			final A_Type existingType = existingMeta.instance();
			final A_Type intersectionType =
				existingType.typeIntersection(constantType);
			final A_Type intersectionMeta =
				InstanceMetaDescriptor.on(intersectionType);
			fallThroughSet.strengthenTestedTypeAtPut(typeReg, intersectionMeta);
		}
	}

	@Override
	public boolean hasSideEffect ()
	{
		// It jumps, which counts as a side effect.
		return true;
	}
}
