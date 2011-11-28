/**
 * interpreter/levelTwo/instruction/L2CreateTupleInstruction.java
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

import static com.avail.descriptor.TypeDescriptor.Types.*;
import static com.avail.interpreter.levelTwo.L2Operation.L2_doCreateTupleOfSizeImmediate_valuesVector_destObject_;
import java.util.*;
import com.avail.annotations.NotNull;
import com.avail.descriptor.*;
import com.avail.interpreter.levelTwo.*;
import com.avail.interpreter.levelTwo.register.*;

/**
 * {@code L2CreateTupleInstruction} creates a {@linkplain TupleDescriptor tuple}
 * from the aggregate contents of a {@linkplain L2RegisterVector register
 * vector}.
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
 */
public final class L2CreateTupleInstruction
extends L2Instruction
{
	/**
	 * The {@linkplain L2ObjectRegister registers} containing the {@linkplain
	 * AvailObject objects} that should be aggregated into a {@linkplain
	 * TupleDescriptor tuple}.
	 */
	private final @NotNull L2RegisterVector sourceVector;

	/**
	 * The {@linkplain L2ObjectRegister register} into which the new {@linkplain
	 * TupleDescriptor tuple} will be written.
	 */
	private final @NotNull L2ObjectRegister destinationRegister;

	/**
	 * Construct a new {@link L2CreateTupleInstruction}.
	 *
	 * @param sourceVector
	 *        The {@linkplain L2ObjectRegister registers} containing the
	 *        {@linkplain AvailObject objects} that should be aggregated into a
	 *        {@linkplain TupleDescriptor tuple}.
	 * @param destinationRegister
	 *        The {@linkplain L2ObjectRegister register} into which the new
	 *        {@linkplain TupleDescriptor tuple} will be written.
	 */
	public L2CreateTupleInstruction (
		final @NotNull L2RegisterVector sourceVector,
		final @NotNull L2ObjectRegister destinationRegister)
	{
		this.sourceVector = sourceVector;
		this.destinationRegister = destinationRegister;
	}

	@Override
	public @NotNull List<L2Register> sourceRegisters ()
	{
		final List<L2Register> result = new ArrayList<L2Register>(
			sourceVector.registers().size());
		result.addAll(sourceVector.registers());
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
			L2_doCreateTupleOfSizeImmediate_valuesVector_destObject_);
		codeGenerator.emitImmediate(sourceVector.registers().size());
		codeGenerator.emitVector(sourceVector);
		codeGenerator.emitObjectRegister(destinationRegister);
	}

	@Override
	public void propagateTypeInfoFor (final @NotNull L2Translator translator)
	{
		final int size = sourceVector.registers().size();
		final AvailObject sizeRange = IntegerDescriptor.fromInt(size).kind();
		List<AvailObject> types;
		types = new ArrayList<AvailObject>(sourceVector.registers().size());
		for (final L2Register register : sourceVector.registers())
		{
			if (translator.registerHasTypeAt(register))
			{
				types.add(translator.registerTypeAt(register));
			}
			else
			{
				types.add(ANY.o());
			}
		}
		final AvailObject tupleType =
			TupleTypeDescriptor.tupleTypeForSizesTypesDefaultType(
				sizeRange,
				TupleDescriptor.fromCollection(types),
				BottomTypeDescriptor.bottom());
		tupleType.makeImmutable();
		translator.registerTypeAtPut(destinationRegister, tupleType);
		if (sourceVector.allRegistersAreConstantsIn(translator))
		{
			final List<AvailObject> constants = new ArrayList<AvailObject>(
				sourceVector.registers().size());
			for (final L2Register register : sourceVector.registers())
			{
				constants.add(translator.registerConstantAt(register));
			}
			final AvailObject tuple = TupleDescriptor.fromCollection(constants);
			tuple.makeImmutable();
			assert tuple.isInstanceOf(tupleType);
			translator.registerConstantAtPut(destinationRegister, tuple);
		}
		else
		{
			translator.removeConstantForRegister(destinationRegister);
		}
	}
}
