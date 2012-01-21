/**
 * L2NoFailPrimitiveInstruction.java
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

import static com.avail.interpreter.levelTwo.L2Operation.L2_doNoFailPrimitive_withArguments_result_;
import java.util.*;
import com.avail.annotations.NotNull;
import com.avail.interpreter.Primitive;
import com.avail.interpreter.levelTwo.*;
import com.avail.interpreter.levelTwo.register.*;

/**
 * {@code L2NoFailPrimitiveInstruction} executes the {@linkplain Primitive
 * primitive} with the specified number.  The primitive should not fail.
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 */
public final class L2NoFailPrimitiveInstruction
extends L2Instruction
{
	/** The {@linkplain Primitive primitive} number. */
	private final int primitiveNumber;

	/**
	 * The {@linkplain L2RegisterVector arguments} to the {@linkplain Primitive
	 * primitive}.
	 */
	private final @NotNull L2RegisterVector primitiveArguments;

	/**
	 * The {@linkplain L2ObjectRegister register} to which the result of the
	 * {@linkplain Primitive primitive} will be written in the event of success.
	 */
	private final @NotNull L2ObjectRegister destinationRegister;

	/**
	 * Construct a new {@link L2NoFailPrimitiveInstruction}.
	 *
	 * @param primitiveNumber
	 *            The {@linkplain Primitive primitive} number.
	 * @param primitiveArguments
	 *            The {@linkplain L2RegisterVector arguments} to the {@linkplain
	 *            Primitive primitive}.
	 * @param destinationRegister
	 *            The {@linkplain L2ObjectRegister register} to which the result
	 *            of the {@linkplain Primitive primitive} will be written in the
	 *            event of success.
	 */
	public L2NoFailPrimitiveInstruction (
			final int primitiveNumber,
			final L2RegisterVector primitiveArguments,
			final L2ObjectRegister destinationRegister)
	{
		this.primitiveNumber = primitiveNumber;
		this.primitiveArguments = primitiveArguments;
		this.destinationRegister = destinationRegister;
	}

	@Override
	public @NotNull List<L2Register> sourceRegisters ()
	{
		List<L2Register> result = new ArrayList<L2Register>(
			primitiveArguments.registers().size());
		result.addAll(primitiveArguments.registers());
		return result;
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>Since a call can clear all registers, we could try to list all
	 * registers as destinations. Instead, we treat calls as the ends of the
	 * basic blocks during flow analysis.</p>
	 */
	@Override
	public @NotNull List<L2Register> destinationRegisters ()
	{
		return Collections.<L2Register>singletonList(destinationRegister);
	}

	@Override
	public void emitOn (final @NotNull L2CodeGenerator codeGenerator)
	{
		codeGenerator.emitL2Operation(
			L2_doNoFailPrimitive_withArguments_result_);
		codeGenerator.emitPrimitiveNumber(primitiveNumber);
		codeGenerator.emitVector(primitiveArguments);
		codeGenerator.emitObjectRegister(destinationRegister);
	}

	@Override
	public void propagateTypeInfoFor (final @NotNull L2Translator translator)
	{
		translator.removeTypeForRegister(destinationRegister);
		translator.removeConstantForRegister(destinationRegister);
	}
}
