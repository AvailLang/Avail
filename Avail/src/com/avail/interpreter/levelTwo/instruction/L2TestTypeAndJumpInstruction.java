/**
 * interpreter/levelTwo/instruction/L2TestTypeAndJumpInstruction.java
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

import static com.avail.interpreter.levelTwo.L2Operation.L2_doJump_ifObject_isKindOfConstant_;
import java.util.*;
import com.avail.annotations.NotNull;
import com.avail.descriptor.*;
import com.avail.interpreter.levelTwo.*;
import com.avail.interpreter.levelTwo.register.*;

/**
 * If the {@linkplain L2Register source register} contains an {@linkplain
 * AvailObject object} compatible with the immediate {@linkplain TypeDescriptor
 * type} when the {@linkplain L2Interpreter interpreter} encounters an {@code
 * L2TestTypeAndJumpInstruction}, then jump to the {@linkplain
 * L2LabelInstruction target}; otherwise do nothing.
 *
 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
 */
public final class L2TestTypeAndJumpInstruction
extends L2AbstractJumpInstruction
{
	/** The source {@linkplain L2Register register}. */
	private final @NotNull L2ObjectRegister sourceRegister;

	/**
	 * The immediate {@linkplain TypeDescriptor type} against which the contents
	 * of the {@linkplain #sourceRegister source register} will be compared.
	 */
	private final @NotNull AvailObject constantType;

	/**
	 * Construct a new {@link L2TestTypeAndJumpInstruction}.
	 *
	 * @param target
	 *        The jump {@linkplain L2LabelInstruction target}.
	 * @param sourceRegister
	 *        The source {@linkplain L2Register register}.
	 * @param constantType
	 *        The immediate {@linkplain TypeDescriptor type} against which the
	 *        contents of the {@linkplain #sourceRegister source register} will
	 *        be compared.
	 */
	public L2TestTypeAndJumpInstruction (
		final @NotNull L2LabelInstruction target,
		final @NotNull L2ObjectRegister sourceRegister,
		final @NotNull AvailObject constantType)
	{
		super(target);
		this.sourceRegister = sourceRegister;
		this.constantType = constantType;
	}

	@Override
	public @NotNull List<L2Register> sourceRegisters ()
	{
		return Collections.<L2Register>singletonList(sourceRegister);
	}

	@Override
	public void emitOn (final @NotNull L2CodeGenerator codeGenerator)
	{
		codeGenerator.emitWord(L2_doJump_ifObject_isKindOfConstant_.ordinal());
		codeGenerator.emitWord(target().offset());
		codeGenerator.emitObjectRegister(sourceRegister);
		codeGenerator.emitLiteral(constantType);
	}
}
