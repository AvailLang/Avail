/**
 * interpreter/levelTwo/instruction/L2PrepareNewFrame.java
 * Copyright (c) 2011, Mark van Gulik.
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

import static com.avail.interpreter.levelTwo.L2Operation.L2_doPrepareNewFrame;
import java.util.*;
import com.avail.annotations.NotNull;
import com.avail.descriptor.*;
import com.avail.interpreter.levelTwo.*;
import com.avail.interpreter.levelTwo.register.L2Register;

/**
 * {@code L2CreateContinuationInstruction} creates a new {@linkplain
 * ContinuationDescriptor continuation} from a calling continuation, a
 * {@linkplain ClosureDescriptor closure}, a level one program counter and stack
 * pointer, some slot information, and a level two program counter.
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
 */
public final class L2PrepareNewFrame
extends L2Instruction
{
	/**
	 * Construct a new {@link L2PrepareNewFrame}.  The instance doesn't need to
	 * keep track of which registers are read or written, because it should only
	 * ever be invoked within the default {@linkplain L2ChunkDescriptor chunk}.
	 */
	public L2PrepareNewFrame ()
	{
		// No state to initialize.
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

	@Override
	public void emitOn (final @NotNull L2CodeGenerator codeGenerator)
	{
		codeGenerator.emitL2Operation(
			L2_doPrepareNewFrame);
	}

	@Override
	public void propagateTypeInfoFor (final @NotNull L2Translator translator)
	{
		// No real optimization should ever be done near this wordcode.
		// Do nothing.
	}
}
