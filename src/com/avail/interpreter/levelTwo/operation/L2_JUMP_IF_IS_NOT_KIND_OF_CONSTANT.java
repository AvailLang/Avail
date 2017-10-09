/**
 * L2_JUMP_IF_IS_NOT_KIND_OF_CONSTANT.java
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
import com.avail.descriptor.AvailObject;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2Operation;
import com.avail.interpreter.levelTwo.operand.L2ConstantOperand;
import com.avail.interpreter.levelTwo.operand.L2ReadPointerOperand;
import com.avail.interpreter.levelTwo.register.L2ObjectRegister;
import com.avail.optimizer.L1NaiveTranslator;
import com.avail.optimizer.L2Translator;
import com.avail.optimizer.RegisterSet;

import java.util.List;

import static com.avail.descriptor.AbstractEnumerationTypeDescriptor
	.instanceTypeOrMetaOn;
import static com.avail.interpreter.levelTwo.L2OperandType.*;
import static com.avail.utility.Nulls.stripNull;

/**
 * Jump to the target if the object is not an instance of the constant type.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public class L2_JUMP_IF_IS_NOT_KIND_OF_CONSTANT extends L2Operation
{
	/**
	 * Initialize the sole instance.
	 */
	public static final L2Operation instance =
		new L2_JUMP_IF_IS_NOT_KIND_OF_CONSTANT().init(
			READ_POINTER.is("object"),
			CONSTANT.is("constant type"),
			PC.is("is not kind"),
			PC.is("is kind"));

	@Override
	public void step (
		final L2Instruction instruction,
		final Interpreter interpreter)
	{
		final L2ObjectRegister objectReg = instruction.readObjectRegisterAt(0);
		final A_Type type = instruction.constantAt(1);
		final int notKindIndex = instruction.pcOffsetAt(2);
		final int isKindIndex = instruction.pcOffsetAt(3);

		interpreter.offset(
			objectReg.in(interpreter).isInstanceOf(type)
				? isKindIndex
				: notKindIndex);
	}

	@Override
	public boolean regenerate (
		final L2Instruction instruction,
		final RegisterSet registerSet,
		final L1NaiveTranslator naiveTranslator)
	{
		// TODO Rewrite this type-narrowing code.
		if (true) { throw new UnsupportedOperationException(); }

		// Eliminate tests due to type propagation.
		final L2ObjectRegister objectReg = instruction.readObjectRegisterAt(0);
		final A_Type type = instruction.constantAt(1);
//		final int notEqualIndex = instruction.pcAt(2);
//		final int equalIndex = instruction.pcAt(3);

		final boolean canJump;
		final boolean mustJump;
		final A_Type intersection;
		if (registerSet.hasConstantAt(objectReg))
		{
			final AvailObject constant = registerSet.constantAt(objectReg);
			mustJump = !constant.isInstanceOf(type);
			canJump = mustJump;
			final A_Type knownType = instanceTypeOrMetaOn(constant);
			intersection = type.typeIntersection(knownType);
		}
		else
		{
			assert registerSet.hasTypeAt(objectReg);
			final A_Type knownType = stripNull(registerSet.typeAt(objectReg));
			intersection = type.typeIntersection(knownType);
			if (intersection.isBottom())
			{
				mustJump = true;
				canJump = true;
			}
			else if (knownType.isSubtypeOf(type))
			{
				mustJump = false;
				canJump = false;
			}
			else
			{
				mustJump = false;
				canJump = true;
			}
		}
		if (mustJump)
		{
			// It can never be that kind of object.  Always jump.  The
			// instructions that follow the jump will become dead code and
			// be eliminated next pass.
			// assert canJump;
			naiveTranslator.addInstruction(
				L2_JUMP.instance,
				instruction.operands[0]);
			return true;
		}
		// assert !mustJump;
		if (!canJump)
		{
			// It is always of the specified type, so never jump.
			return true;
		}
		// Since it's already an X and we're testing for Y, we might be
		// better off testing for an X∩Y instead.  Let's just assume it's
		// quicker for now.  Eventually we can extend this idea to testing
		// things other than types, such as if we know we have a tuple but we
		// want to dispatch based on the tuple's size.
		if (!intersection.equals(type))
		{
			naiveTranslator.addInstruction(
				L2_JUMP_IF_IS_NOT_KIND_OF_CONSTANT.instance,
				new L2ReadPointerOperand(objectReg),
				new L2ConstantOperand(intersection),
				instruction.operands[2],
				instruction.operands[3]);
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
		final L2ObjectRegister objectReg = instruction.readObjectRegisterAt(0);
		final A_Type type = instruction.constantAt(1);
//		final int notEqualIndex = instruction.pcAt(2);
//		final int equalIndex = instruction.pcAt(3);

		assert registerSets.size() == 2;
		final RegisterSet fallThroughSet = registerSets.get(0);
//		final RegisterSet postJumpSet = registerSets.get(1);

		assert fallThroughSet.hasTypeAt(objectReg);
		final A_Type existingType = fallThroughSet.typeAt(objectReg);
		final A_Type intersection = existingType.typeIntersection(type);
		fallThroughSet.strengthenTestedTypeAtPut(objectReg, intersection);
	}

	@Override
	public boolean hasSideEffect ()
	{
		// It jumps, which counts as a side effect.
		return true;
	}
}
