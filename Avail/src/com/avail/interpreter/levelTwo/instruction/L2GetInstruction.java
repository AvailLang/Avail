/**
 * interpreter/levelTwo/instruction/L2GetInstruction.java
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

import static com.avail.interpreter.levelTwo.L2Operation.L2_doGetVariable_destObject_;
import java.util.*;
import com.avail.annotations.NotNull;
import com.avail.descriptor.*;
import com.avail.interpreter.levelTwo.*;
import com.avail.interpreter.levelTwo.register.*;

/**
 * {@code L2GetInstruction} reads the {@linkplain AvailObject value} of the
 * {@linkplain ContainerDescriptor container} specified in the source
 * {@linkplain L2ObjectRegister register}.
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
 */
public final class L2GetInstruction
extends L2Instruction
{
	/** The source {@linkplain L2ObjectRegister register}. */
	private final @NotNull L2ObjectRegister sourceRegister;

	/** The destination {@linkplain L2ObjectRegister register}. */
	private final @NotNull L2ObjectRegister destinationRegister;

	/**
	 * Construct a new {@link L2GetInstruction}.
	 *
	 * @param sourceRegister
	 *        The source {@linkplain L2ObjectRegister register}.
	 * @param destinationRegister
	 *        The destination {@linkplain L2ObjectRegister register}.
	 */
	public L2GetInstruction (
		final @NotNull L2ObjectRegister sourceRegister,
		final @NotNull L2ObjectRegister destinationRegister)
	{
		this.sourceRegister = sourceRegister;
		this.destinationRegister = destinationRegister;
	}

	@Override
	public @NotNull List<L2Register> sourceRegisters ()
	{
		return Collections.<L2Register>singletonList(sourceRegister);
	}

	@Override
	public @NotNull List<L2Register> destinationRegisters ()
	{
		return Collections.<L2Register>singletonList(destinationRegister);
	}

	@Override
	public void emitOn (final @NotNull L2CodeGenerator codeGenerator)
	{
		codeGenerator.emitL2Operation(
			L2_doGetVariable_destObject_);
		codeGenerator.emitObjectRegister(sourceRegister);
		codeGenerator.emitObjectRegister(destinationRegister);
	}

	@Override
	public void propagateTypeInfoFor (final @NotNull L2Translator translator)
	{
		if (translator.registerHasTypeAt(sourceRegister))
		{
			final AvailObject varType =
				translator.registerTypeAt(sourceRegister).typeIntersection(
					ContainerTypeDescriptor.mostGeneralType());
			translator.registerTypeAtPut(sourceRegister, varType);
			translator.registerTypeAtPut(
				destinationRegister, varType.readType());
		}
		else
		{
			translator.removeTypeForRegister(destinationRegister);
		}
		translator.removeConstantForRegister(destinationRegister);
	}
}
