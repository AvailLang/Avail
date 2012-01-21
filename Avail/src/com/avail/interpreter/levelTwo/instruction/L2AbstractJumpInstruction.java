/**
 * L2AbstractJumpInstruction.java
 * Copyright © 1993-2012, Mark van Gulik and Todd L Smith.
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

package com.avail.interpreter.levelTwo.instruction;

import java.util.*;
import com.avail.annotations.NotNull;
import com.avail.interpreter.levelTwo.L2Interpreter;
import com.avail.interpreter.levelTwo.register.L2Register;

/**
 * {@code L2AbstractJumpInstruction} is the foundation of all jump instructions
 * understood by the {@linkplain L2Interpreter level two Avail interpreter}. It
 * implements a read-only jump {@linkplain L2Instruction target} that is set at
 * construction time.
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
 */
public abstract class L2AbstractJumpInstruction
extends L2Instruction
{
	/**
	 * The {@linkplain L2LabelInstruction target} of the {@linkplain
	 * L2AbstractJumpInstruction jump}.
	 */
	private final @NotNull L2LabelInstruction target;

	/**
	 * Answer the {@linkplain L2LabelInstruction target} of the {@linkplain
	 * L2AbstractJumpInstruction jump}.
	 *
	 * @return The jump {@linkplain L2LabelInstruction target}.
	 */
	protected @NotNull L2LabelInstruction target ()
	{
		return target;
	}

	/**
	 * Construct a new {@link L2AbstractJumpInstruction}.
	 *
	 * @param target The jump {@linkplain L2LabelInstruction target}.
	 */
	protected L2AbstractJumpInstruction (
		final @NotNull L2LabelInstruction target)
	{
		this.target = target;
	}

	@Override
	public @NotNull List<L2Register> sourceRegisters ()
	{
		return Collections.emptyList();
	}

	@Override
	public @NotNull List<L2Register> destinationRegisters ()
	{
		return Collections.emptyList();
	}
}
