/*
 * L2IntegerRegister.java
 * Copyright © 1993-2019, The Avail Foundation, LLC.
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

import com.avail.optimizer.L2Generator;
import com.avail.optimizer.reoptimizer.L2Inliner;

/**
 * {@code L2IntRegister} models the conceptual usage of a register that can
 * store a machine integer.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class L2IntRegister
extends L2Register
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
	 */
	public L2IntRegister (
		final int debugValue)
	{
		super(debugValue);
	}

	@Override
	public L2IntRegister copyForTranslator (
		final L2Generator generator)
	{
		return new L2IntRegister(generator.nextUnique());
	}

	@Override
	public L2IntRegister copyAfterColoring ()
	{
		final L2IntRegister result = new L2IntRegister(finalIndex());
		result.setFinalIndex(finalIndex());
		return result;
	}

	@Override
	public L2IntRegister copyForInliner (final L2Inliner inliner)
	{
		return new L2IntRegister(inliner.nextUnique());
	}

	@Override
	public String namePrefix ()
	{
		return "i";
	}
}
