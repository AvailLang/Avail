/*
 * L2ObjectRegister.java
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

import com.avail.descriptor.A_BasicObject;
import com.avail.descriptor.AvailObject;
import com.avail.interpreter.levelTwo.L2Operation;
import com.avail.interpreter.levelTwo.operand.L2ReadPointerOperand;
import com.avail.interpreter.levelTwo.operand.L2WritePointerOperand;
import com.avail.interpreter.levelTwo.operand.TypeRestriction;
import com.avail.interpreter.levelTwo.operation.L2_MOVE;
import com.avail.optimizer.L2Generator;
import com.avail.optimizer.L2Retranslator;

import static com.avail.descriptor.TypeDescriptor.Types.TOP;
import static com.avail.utility.Casts.cast;

/**
 * {@code L2ObjectRegister} models the conceptual usage of a register that can
 * store an {@link AvailObject}.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public class L2ObjectRegister
extends L2Register<A_BasicObject>
{
	@Override
	public RegisterKind registerKind ()
	{
		return RegisterKind.OBJECT;
	}

	/**
	 * Construct a new {@code L2ObjectRegister}.
	 *
	 * @param debugValue
	 *        A value used to distinguish the new instance visually during
	 *        debugging of L2 translations.
	 * @param restriction
	 * 	      The {@link TypeRestriction}.
	 */
	public L2ObjectRegister (
		final int debugValue,
		final TypeRestriction<A_BasicObject> restriction)
	{
		super(debugValue, restriction);
	}

	@Override
	public L2ReadPointerOperand read (
		final TypeRestriction<A_BasicObject> typeRestriction)
	{
		return new L2ReadPointerOperand(this, restriction);
	}

	@Override
	public L2WritePointerOperand write ()
	{
		return new L2WritePointerOperand(this);
	}

	@Override
	public <R extends L2Register<A_BasicObject>>
	R copyForTranslator (
		final L2Generator generator,
		final TypeRestriction<A_BasicObject> typeRestriction)
	{
		return cast(
			new L2ObjectRegister(generator.nextUnique(), typeRestriction));
	}

	@Override
	public L2ObjectRegister copyAfterColoring ()
	{
		final L2ObjectRegister result = new L2ObjectRegister(
			finalIndex(), TypeRestriction.restriction(TOP.o()));
		result.setFinalIndex(finalIndex());
		return result;
	}

	@Override
	public L2ObjectRegister copyForInliner (final L2Retranslator inliner)
	{
		return new L2ObjectRegister(
			inliner.targetGenerator.nextUnique(), restriction);
	}

	@Override
	public L2Operation phiMoveOperation ()
	{
		return L2_MOVE.instance;
	}

	@Override
	public String namePrefix ()
	{
		return "r";
	}
}
