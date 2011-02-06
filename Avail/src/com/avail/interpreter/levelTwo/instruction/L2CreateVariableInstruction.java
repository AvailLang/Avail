/**
 * interpreter/levelTwo/instruction/L2CreateVariableInstruction.java
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

import static com.avail.interpreter.levelTwo.L2Operation.L2_doCreateVariableTypeConstant_destObject_;
import java.util.*;
import com.avail.annotations.NotNull;
import com.avail.descriptor.*;
import com.avail.interpreter.levelTwo.*;
import com.avail.interpreter.levelTwo.register.*;

/**
 * {@code L2CreateVariableInstruction} creates a new {@linkplain
 * ContainerDescriptor container} given a statically determined {@linkplain
 * TypeDescriptor type}.
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
 */
public final class L2CreateVariableInstruction
extends L2Instruction
{
	/**
	 * The constant {@linkplain TypeDescriptor type} with which the {@linkplain
	 * ContainerDescriptor container} should be created.
	 */
	private final @NotNull AvailObject constantType;

	/**
	 * The {@linkplain L2ObjectRegister register} into which the new {@linkplain
	 * ContainerDescriptor container} will be written.
	 */
	private final @NotNull L2ObjectRegister destinationRegister;

	/**
	 * Construct a new {@link L2CreateVariableInstruction}.
	 *
	 * @param constantType
	 *        The constant {@linkplain TypeDescriptor type} with which the
	 *        {@linkplain ContainerDescriptor variable} should be created.
	 * @param destinationRegister
	 *        The {@linkplain L2ObjectRegister register} into which the new
	 *        {@linkplain ContainerDescriptor variable} will be written.
	 */
	public L2CreateVariableInstruction (
		final @NotNull AvailObject constantType,
		final @NotNull L2ObjectRegister destinationRegister)
	{
		this.constantType = constantType;
		this.destinationRegister = destinationRegister;
	}

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
		codeGenerator.emitWord(
			L2_doCreateVariableTypeConstant_destObject_.ordinal());
		codeGenerator.emitLiteral(constantType);
		codeGenerator.emitObjectRegister(destinationRegister);
	}

	@Override
	public void propagateTypeInfoFor (final @NotNull L2Translator translator)
	{
		//  We know the exact type...
		translator.registerTypeAtPut(destinationRegister, constantType);
		//  ...but the instance is new so it can't be a constant.
		translator.removeConstantForRegister(destinationRegister);
	}
}
