/**
 * interpreter/levelTwo/instruction/L2GetTypeInstruction.java
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

import static com.avail.descriptor.TypeDescriptor.Types.TYPE;
import static com.avail.interpreter.levelTwo.L2Operation.L2_doGetType_destObject_;
import java.util.*;
import com.avail.annotations.NotNull;
import com.avail.descriptor.*;
import com.avail.interpreter.levelTwo.*;
import com.avail.interpreter.levelTwo.register.*;

/**
 * {@code L2GetTypeInstruction} stores the {@linkplain TypeDescriptor type} of
 * the {@linkplain AvailObject object} in the source {@linkplain
 * L2ObjectRegister register} into the destination register.
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
 */
public final class L2GetTypeInstruction
extends L2Instruction
{
	/** The source {@linkplain L2ObjectRegister register}. */
	private final @NotNull L2ObjectRegister sourceRegister;

	/** The destination {@linkplain L2ObjectRegister register}. */
	private final @NotNull L2ObjectRegister destinationRegister;

	/**
	 * Construct a new {@link L2GetTypeInstruction}.
	 *
	 * @param sourceRegister
	 *        The source {@linkplain L2ObjectRegister register}.
	 * @param destinationRegister
	 *        The destination {@linkplain L2ObjectRegister register}.
	 */
	public L2GetTypeInstruction (
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
		codeGenerator.emitWord(L2_doGetType_destObject_.ordinal());
		codeGenerator.emitObjectRegister(sourceRegister);
		codeGenerator.emitObjectRegister(destinationRegister);
	}

	@Override
	public void propagateTypeInfoFor (final @NotNull L2Translator translator)
	{
		if (translator.registerHasTypeAt(sourceRegister))
		{
			final AvailObject type = translator.registerTypeAt(sourceRegister);
			// Apply the rule of metacovariance. It says that given types T1
			// and T2, T1 <= T2 implies T1 type <= T2 type. It is guaranteed
			// true for all types in Avail.
			final AvailObject meta = type.type();
			translator.registerTypeAtPut(destinationRegister, meta);
		}
		else
		{
			translator.registerTypeAtPut(destinationRegister, TYPE.o());
		}
		if (translator.registerHasConstantAt(sourceRegister))
		{
			translator.registerConstantAtPut(
				destinationRegister,
				translator.registerConstantAt(sourceRegister).type());
		}
		else
		{
			translator.removeConstantForRegister(destinationRegister);
		}
	}
}
