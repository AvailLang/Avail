/**
 * interpreter/levelTwo/instruction/L2InterpretOneInstruction.java
 * Copyright (c) 2010, Mark van Gulik.
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

import static com.avail.interpreter.levelTwo.L2Operation.L2_doInterpretOneInstruction;
import java.util.*;
import com.avail.annotations.NotNull;
import com.avail.descriptor.L2ChunkDescriptor;
import com.avail.interpreter.levelOne.L1Instruction;
import com.avail.interpreter.levelTwo.*;
import com.avail.interpreter.levelTwo.register.L2Register;

/**
 * {@code L2InterpretOneInstruction} resides solely in the {@linkplain
 * L2Translator#createChunkForFirstInvocation() default} {@linkplain
 * L2ChunkDescriptor chunk} and exists to simulate a single {@linkplain
 * L1Instruction level one Avail instruction}.
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
 */
public final class L2InterpretOneInstruction
extends L2Instruction
{
	/**
	 * {@inheritDoc}
	 *
	 * <p>But this should never be produced by the {@linkplain L2Translator
	 * optimizer}, as its existence and purpose are restricted to the
	 * {@linkplain L2Translator#createChunkForFirstInvocation() default}
	 * {@linkplain L2ChunkDescriptor chunk}.</p>
	 */
	@Override
	public @NotNull List<L2Register> sourceRegisters ()
	{
		return Collections.emptyList();
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>Ignore the {@linkplain L2Interpreter#callerRegister() caller
	 * register}, as it is implicit in the {@linkplain L2InterpretOneInstruction
	 * instruction}.</p>
	 */
	@Override
	public List<L2Register> destinationRegisters ()
	{
		return Collections.emptyList();
	}

	@Override
	public void emitOn (final @NotNull L2CodeGenerator codeGenerator)
	{
		codeGenerator.emitWord(L2_doInterpretOneInstruction.ordinal());
	}

	@Override
	public void propagateTypeInfoFor (final @NotNull L2Translator translator)
	{
		// No real optimization should ever be done near this wordcode.
		// Do nothing.
	}
}
