/**
 * interpreter/levelTwo/instruction/L2CreateSimpleContinuation.java
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

import static com.avail.interpreter.levelTwo.L2Operation.L2_doCreateSimpleContinuationIn_;
import java.util.*;
import com.avail.annotations.NotNull;
import com.avail.descriptor.*;
import com.avail.interpreter.levelTwo.*;
import com.avail.interpreter.levelTwo.register.*;

/**
 * {@code L2CreateSimpleContinuation} creates a real {@linkplain
 * ContinuationDescriptor continuation} using the contents of the {@linkplain
 * L2Interpreter interpreter}'s architectural registers, i.e. the calling
 * continuation, the current {@linkplain ClosureDescriptor closure}, and as many
 * {@linkplain AvailObject arguments} as the closure specifies. This instruction
 * also creates the local variables necessary to execute the continuation. The
 * resultant continuation always represents the state upon entry to the closure.
 * This instruction exists solely to allow an authentic level one instruction
 * simulator to be implemented in terms of level two instructions.
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
 */
public final class L2CreateSimpleContinuation
extends L2Instruction
{
	/**
	 * The {@linkplain L2ObjectRegister register} into which the new {@linkplain
	 * ContinuationDescriptor continuation} will be written.
	 */
	private final @NotNull L2ObjectRegister destinationRegister;

	/**
	 * Construct a new {@link L2CreateSimpleContinuation}.
	 *
	 * @param destinationRegister
	 *        The {@linkplain L2ObjectRegister register} into which the new
	 *        {@linkplain ContinuationDescriptor continuation} will be written.
	 */
	public L2CreateSimpleContinuation (
		final @NotNull L2ObjectRegister destinationRegister)
	{
		this.destinationRegister = destinationRegister;
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p> Since this instruction should only appear in the unoptimized default
	 * chunk, there is no need for this to be accurate. We're missing the
	 * caller, the closure, and the argument registers, which depend on what
	 * code this is being invoked for.</p>
	 */
	@Override
	public @NotNull List<L2Register> sourceRegisters ()
	{
		return Collections.emptyList();
	}

	@Override
	public @NotNull List<L2Register> destinationRegisters ()
	{
		return Collections.<L2Register>singletonList(destinationRegister);
	}

	@Override
	public void emitOn (final @NotNull L2CodeGenerator codeGenerator)
	{
		codeGenerator.emitWord(L2_doCreateSimpleContinuationIn_.ordinal());
		codeGenerator.emitObjectRegister(destinationRegister);
	}

	@Override
	public void propagateTypeInfoFor (final @NotNull L2Translator translator)
	{
		translator.registerTypeAtPut(
			destinationRegister,
			ContinuationTypeDescriptor.forClosureType(
				translator.code().closureType()));
		translator.removeConstantForRegister(destinationRegister);
	}
}
