/*
 * L2IntegerRegister.java
 * Copyright © 1993-2018, The Avail Foundation, LLC.
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

package com.avail.interpreter.levelTwo.register;

import com.avail.descriptor.A_Number;
import com.avail.interpreter.levelTwo.L2Operation;
import com.avail.interpreter.levelTwo.operand.L2ReadIntOperand;
import com.avail.interpreter.levelTwo.operand.L2WriteIntOperand;
import com.avail.interpreter.levelTwo.operand.TypeRestriction;
import com.avail.interpreter.levelTwo.operation.L2_MOVE_INT;
import com.avail.optimizer.L1Translator;
import com.avail.optimizer.L2Inliner;

import static com.avail.descriptor.IntegerRangeTypeDescriptor.int32;
import static com.avail.utility.Casts.cast;

/**
 * {@code L2IntRegister} models the conceptual usage of a register that can
 * store a machine integer.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class L2IntRegister
extends L2Register<A_Number>
{
	@Override
	public RegisterKind registerKind ()
	{
		return RegisterKind.INTEGER;
	}

	/**
	 * Construct a new {@code L2IntRegister}.
	 *
	 * @param debugValue
	 *        A value used to distinguish the new instance visually during
	 *        debugging of L2 translations.
	 * @param restriction
	 * 	      The {@link TypeRestriction}.
	 */
	public L2IntRegister (
		final int debugValue,
		final TypeRestriction<A_Number> restriction)
	{
		super(debugValue, restriction);
	}

	@Override
	public L2ReadIntOperand read (
		final TypeRestriction<A_Number> typeRestriction)
	{
		return new L2ReadIntOperand(this, typeRestriction);
	}

	@Override
	public L2WriteIntOperand write ()
	{
		return new L2WriteIntOperand(this);
	}

	@Override
	public <R extends L2Register<A_Number>> R copyForTranslator (
		final L1Translator translator,
		final TypeRestriction<A_Number> typeRestriction)
	{
		return
			cast(new L2IntRegister(translator.nextUnique(), typeRestriction));
	}

	@Override
	public L2IntRegister copyAfterColoring ()
	{
		final L2IntRegister result = new L2IntRegister(
			finalIndex(), TypeRestriction.restriction(int32()));
		result.setFinalIndex(finalIndex());
		return result;
	}

	@Override
	public L2IntRegister copyForInliner (final L2Inliner inliner)
	{
		return new L2IntRegister(
			inliner.targetTranslator.nextUnique(),
			restriction);
	}

	@Override
	public L2Operation phiMoveOperation ()
	{
		return L2_MOVE_INT.instance;
	}

	@Override
	public String namePrefix ()
	{
		return "i";
	}
}
