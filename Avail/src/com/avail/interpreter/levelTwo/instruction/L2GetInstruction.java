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

import static com.avail.descriptor.TypeDescriptor.Types.*;
import static com.avail.interpreter.levelTwo.L2Operation.L2_doGetVariable_destObject_;
import java.util.*;
import com.avail.descriptor.AvailObject;
import com.avail.interpreter.levelTwo.*;
import com.avail.interpreter.levelTwo.register.*;

public class L2GetInstruction extends L2Instruction
{
	L2ObjectRegister _sourceVar;
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

		List<L2Register> result = new ArrayList<L2Register>(1);
		result.add(_sourceVar);
		return result;
	}



	// code generation

	@Override
	public void emitOn (
			final L2CodeGenerator anL2CodeGenerator)
	{
		//  Emit this instruction to the code generator.

		anL2CodeGenerator.emitWord(L2_doGetVariable_destObject_.ordinal());
		anL2CodeGenerator.emitObjectRegister(_sourceVar);
		anL2CodeGenerator.emitObjectRegister(_dest);
	}



	// initialization

	public L2GetInstruction sourceVariableDestination (
			final L2ObjectRegister sourceVariable,
			final L2ObjectRegister destination)
	{
		_sourceVar = sourceVariable;
		_dest = destination;
		return this;
	}



	// typing

	@Override
	public void propagateTypeInfoFor (
			final L2Translator anL2Translator)
	{
		//  Propagate type information due to this instruction.

		if (anL2Translator.registerHasTypeAt(_sourceVar))
		{
			AvailObject varType = anL2Translator.registerTypeAt(_sourceVar);
			varType = varType.typeIntersection(CONTAINER.o());
			anL2Translator.registerTypeAtPut(_sourceVar, varType);
			if (varType.isSubtypeOf(CONTAINER.o())
				&& !varType.equals(CONTAINER.o()))
			{
				anL2Translator.registerTypeAtPut(_dest, varType.innerType());
			}
			else
			{
				anL2Translator.removeTypeForRegister(_dest);
			}
		}
		else
		{
			anL2Translator.removeTypeForRegister(_dest);
		}
		anL2Translator.removeConstantForRegister(_dest);
	}
}
