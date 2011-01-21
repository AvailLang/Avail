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
import com.avail.descriptor.*;
import com.avail.interpreter.levelTwo.*;
import com.avail.interpreter.levelTwo.register.*;

public class L2CreateTupleInstruction extends L2Instruction
{
	L2RegisterVector _sourceVector;
	L2ObjectRegister _dest;


	// accessing

	@Override
	public List<L2Register> destinationRegisters ()
	{
		//  Answer a collection of registers written to by this instruction.

		List<L2Register> result = new ArrayList<L2Register>(1);
		result.add(_dest);
		return result;
	}

	@Override
	public List<L2Register> sourceRegisters ()
	{
		//  Answer a collection of registers read by this instruction.

		List<L2Register> result = new ArrayList<L2Register>(_sourceVector.registers().size());
		result.addAll(_sourceVector.registers());
		return result;
	}



	// code generation

	@Override
	public void emitOn (
			final L2CodeGenerator anL2CodeGenerator)
	{
		//  Emit this instruction to the code generator.

		anL2CodeGenerator.emitWord(L2_doCreateTupleOfSizeImmediate_valuesVector_destObject_.ordinal());
		anL2CodeGenerator.emitWord(_sourceVector.registers().size());
		anL2CodeGenerator.emitVector(_sourceVector);
		anL2CodeGenerator.emitObjectRegister(_dest);
	}



	// initialization

	public L2CreateTupleInstruction sourceVectorDestination (
			final L2RegisterVector srcVect,
			final L2ObjectRegister destination)
	{
		_sourceVector = srcVect;
		_dest = destination;
		return this;
	}



	// typing

	@Override
	public void propagateTypeInfoFor (
			final L2Translator anL2Translator)
	{
		//  Propagate type information due to this instruction.

		final int size = _sourceVector.registers().size();
		final AvailObject sizeRange = IntegerDescriptor.objectFromInt(size).type();
		List<AvailObject> types;
		types = new ArrayList<AvailObject>(_sourceVector.registers().size());
		for (L2Register reg : _sourceVector.registers())
		{
			if (anL2Translator.registerHasTypeAt(reg))
			{
				types.add(anL2Translator.registerTypeAt(reg));
			}
			else
			{
				types.add(ALL.o());
			}
		}
		final AvailObject tupleType = TupleTypeDescriptor.tupleTypeForSizesTypesDefaultType(
			sizeRange,
			TupleDescriptor.fromList(types),
			TERMINATES.o());
		tupleType.makeImmutable();
		anL2Translator.registerTypeAtPut(_dest, tupleType);
		if (_sourceVector.allRegistersAreConstantsIn(anL2Translator))
		{
			List<AvailObject> constants = new ArrayList<AvailObject>(_sourceVector.registers().size());
			for (L2Register reg : _sourceVector.registers())
			{
				constants.add(anL2Translator.registerConstantAt(reg));
			}
			AvailObject tuple = TupleDescriptor.fromList(constants);
			tuple.makeImmutable();
			assert tuple.isInstanceOfSubtypeOf(tupleType);
			anL2Translator.registerConstantAtPut(_dest, tuple);
		}
		else
		{
			anL2Translator.removeConstantForRegister(_dest);
		}
	}





}
