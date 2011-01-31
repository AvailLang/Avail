/**
 * interpreter/levelTwo/instruction/L2SuperCallInstruction.java
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

import com.avail.descriptor.AvailObject;
import com.avail.interpreter.levelTwo.L2CodeGenerator;
import com.avail.interpreter.levelTwo.L2Translator;
import com.avail.interpreter.levelTwo.instruction.L2SuperCallInstruction;
import com.avail.interpreter.levelTwo.register.L2Register;
import com.avail.interpreter.levelTwo.register.L2RegisterVector;
import java.util.ArrayList;
import java.util.List;
import static com.avail.interpreter.levelTwo.L2Operation.*;

public class L2SuperCallInstruction extends L2Instruction
{
	AvailObject _impSet;
	L2RegisterVector _argsVector;
	L2RegisterVector _argTypesVector;


	// accessing

	@Override
	public List<L2Register> destinationRegisters ()
	{
		//  Answer a collection of registers written to by this instruction.  Since a call can clear all registers,
		//  we could try to list all registers as destinations.  Instead, we treat calls as the ends of the basic
		//  blocks during flow analysis.

		return new ArrayList<L2Register>();
	}

	@Override
	public List<L2Register> sourceRegisters ()
	{
		//  Answer a collection of registers read by this instruction.

		List<L2Register> result = new ArrayList<L2Register>(_argsVector.registers().size() * 2);
		result.addAll(_argsVector.registers());
		result.addAll(_argTypesVector.registers());
		return result;
	}



	// code generation

	@Override
	public void emitOn (
			final L2CodeGenerator anL2CodeGenerator)
	{
		//  Emit this instruction to the code generator.

		anL2CodeGenerator.emitWord(L2_doSuperSend_argumentsVector_argumentTypesVector_.ordinal());
		anL2CodeGenerator.emitLiteral(_impSet);
		anL2CodeGenerator.emitVector(_argsVector);
		anL2CodeGenerator.emitVector(_argTypesVector);
	}



	// initialization

	public L2SuperCallInstruction selectorArgsVectorArgTypesVector (
			final AvailObject theImpSet,
			final L2RegisterVector args,
			final L2RegisterVector argTypes)
	{
		_impSet = theImpSet;
		_argsVector = args;
		_argTypesVector = argTypes;
		return this;
	}



	// typing

	@Override
	public void propagateTypeInfoFor (
			final L2Translator anL2Translator)
	{
		//  Propagate type information due to this instruction.

		anL2Translator.restrictPropagationInformationToArchitecturalRegisters();
	}
}
