/**
 * L2CreateFunctionInstruction.java
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

import static com.avail.interpreter.levelTwo.L2Operation.L2_doCreateFunctionFromCodeObject_outersVector_destObject_;
import java.util.*;
import com.avail.annotations.NotNull;
import com.avail.descriptor.*;
import com.avail.interpreter.levelTwo.*;
import com.avail.interpreter.levelTwo.register.*;

/**
 * {@code L2CreateFunctionInstruction} writes a new {@linkplain FunctionDescriptor
 * function} into the specified {@linkplain L2ObjectRegister register}. The
 * {@linkplain CompiledCodeDescriptor compiled code} literal and the captured
 * values are made available at construction time.
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
 */
public final class L2CreateFunctionInstruction
extends L2Instruction
{
	/**
	 * The {@linkplain CompiledCodeDescriptor compiled code} that defines the
	 * behavior of the {@linkplain FunctionDescriptor function}.
	 */
	private final @NotNull AvailObject code;

	/** The captured {@linkplain AvailObject values}. */
	private final @NotNull L2RegisterVector outersVector;

	/**
	 * The {@linkplain L2ObjectRegister register} into which the manufactured
	 * {@linkplain FunctionDescriptor function} will be written.
	 */
	private final @NotNull L2ObjectRegister destinationRegister;

	/**
	 * Construct a new {@link L2CreateFunctionInstruction}.
	 *
	 * @param code
	 *        The {@linkplain CompiledCodeDescriptor compiled code} that defines
	 *        the behavior of the {@linkplain FunctionDescriptor function}.
	 * @param outersVector
	 *        The captured {@linkplain AvailObject values}.
	 * @param destinationRegister
	 *        The {@linkplain L2ObjectRegister register} into which the
	 *        manufactured {@linkplain FunctionDescriptor function} will be
	 *        written.
	 */
	public L2CreateFunctionInstruction (
		final @NotNull AvailObject code,
		final @NotNull L2RegisterVector outersVector,
		final @NotNull L2ObjectRegister destinationRegister)
	{
		this.code = code;
		this.outersVector = outersVector;
		this.destinationRegister = destinationRegister;
	}

	@Override
	public @NotNull List<L2Register> sourceRegisters ()
	{
		final List<L2Register> result = new ArrayList<L2Register>(
			outersVector.registers().size());
		result.addAll(outersVector.registers());
		return result;
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
			L2_doCreateFunctionFromCodeObject_outersVector_destObject_);
		codeGenerator.emitLiteral(code);
		codeGenerator.emitVector(outersVector);
		codeGenerator.emitObjectRegister(destinationRegister);
	}

	@Override
	public void propagateTypeInfoFor (final @NotNull L2Translator translator)
	{
		translator.registerTypeAtPut(destinationRegister, code.functionType());
		if (outersVector.allRegistersAreConstantsIn(translator))
		{
			final AvailObject function = FunctionDescriptor.mutable().create(
				outersVector.registers().size());
			function.code(code);
			int index = 1;
			for (final L2ObjectRegister outer : outersVector)
			{
				function.outerVarAtPut(
					index++, translator.registerConstantAt(outer));
			}
		}
		else
		{
			translator.removeConstantForRegister(destinationRegister);
		}
	}
}
