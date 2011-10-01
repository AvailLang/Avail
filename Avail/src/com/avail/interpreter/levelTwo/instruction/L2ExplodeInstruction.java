/**
 * interpreter/levelTwo/instruction/L2ExplodeInstruction.java
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

import static com.avail.interpreter.levelTwo.L2Operation.L2_doExplodeContinuationObject;
import java.util.*;
import com.avail.annotations.NotNull;
import com.avail.descriptor.*;
import com.avail.interpreter.levelTwo.*;
import com.avail.interpreter.levelTwo.register.*;

/**
 * {@code L2ExplodeInstruction} disassembles the source {@linkplain
 * ContinuationDescriptor continuation} by extracting its caller, {@linkplain
 * FunctionDescriptor function}, and {@linkplain AvailObject slots} into the
 * specified {@linkplain L2RegisterVector register vector}.
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
 */
public final class L2ExplodeInstruction
extends L2Instruction
{
	/**
	 * The {@linkplain L2ObjectRegister register} containing the {@linkplain
	 * ContinuationDescriptor continuation} that should be disassembled into the
	 * destination registers.
	 */
	private final @NotNull L2ObjectRegister sourceRegister;

	/**
	 * The destination {@linkplain L2ObjectRegister register} for the
	 * {@linkplain ContinuationDescriptor caller}.
	 */
	private final @NotNull L2ObjectRegister caller;

	/**
	 * The destination {@linkplain L2ObjectRegister register} for the
	 * {@linkplain FunctionDescriptor function}.
	 */
	private final @NotNull L2ObjectRegister function;

	/**
	 * The destination {@linkplain L2RegisterVector vector} for the {@linkplain
	 * AvailObject slots} of the {@linkplain ContinuationDescriptor
	 * continuation}.
	 */
	private final @NotNull L2RegisterVector slotsVector;

	/**
	 * Construct a new {@link L2ExplodeInstruction}.
	 *
	 * @param sourceRegister
	 *        The {@linkplain L2ObjectRegister register} containing the
	 *        {@linkplain ContinuationDescriptor continuation} that should be
	 *        disassembled into the destination registers.
	 * @param caller
	 *        The destination {@linkplain L2ObjectRegister register} for the
	 *        {@linkplain ContinuationDescriptor caller}.
	 * @param function
	 *        The destination {@linkplain L2ObjectRegister register} for the
	 *        {@linkplain FunctionDescriptor function}.
	 * @param slotsVector
	 *        The destination {@linkplain L2RegisterVector vector} for the
	 *        {@linkplain AvailObject slots} of the {@linkplain
	 *        ContinuationDescriptor continuation}.
	 */
	public L2ExplodeInstruction (
		final @NotNull L2ObjectRegister sourceRegister,
		final @NotNull L2ObjectRegister caller,
		final @NotNull L2ObjectRegister function,
		final @NotNull L2RegisterVector slotsVector)
	{
		this.sourceRegister = sourceRegister;
		this.caller = caller;
		this.function = function;
		this.slotsVector = slotsVector;
	}

	@Override
	public @NotNull List<L2Register> sourceRegisters ()
	{
		return Collections.<L2Register>singletonList(sourceRegister);
	}

	@Override
	public List<L2Register> destinationRegisters ()
	{
		List<L2Register> result = new ArrayList<L2Register>(
			2 + slotsVector.registers().size());
		result.add(caller);
		result.add(function);
		result.addAll(slotsVector.registers());
		return result;
	}

	@Override
	public void emitOn (final @NotNull L2CodeGenerator codeGenerator)
	{
		codeGenerator.emitL2Operation(
			L2_doExplodeContinuationObject);
		codeGenerator.emitObjectRegister(sourceRegister);
		codeGenerator.emitObjectRegister(caller);
		codeGenerator.emitObjectRegister(function);
		codeGenerator.emitVector(slotsVector);
	}

	@Override
	public void propagateTypeInfoFor (final @NotNull L2Translator translator)
	{
		// TODO: [MvG] How seriously should the comments below be taken?

		// Hm.  This explode instruction should have captured type information
		// for its continuation slots.  For now just clear them all (yuck).
		// Later, we will capture this in the call instruction, suitably
		// adjusted with an expectation of a return value (of the appropriate
		// type) on the stack.
		//
		// Clear all types, then set the sender and function types (the slot
		// types will be set by L2Translator>>doCall)...

		translator.clearRegisterTypes();
		//  anL2Translator registerTypes
		//    at: destSender identity put: TypeDescriptor continuation.
		//
		//  unused information
		translator.registerTypeAtPut(function, translator.code().functionType());
		translator.clearRegisterConstants();
	}
}
