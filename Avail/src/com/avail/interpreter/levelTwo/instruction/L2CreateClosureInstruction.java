/**
 * interpreter/levelTwo/instruction/L2CreateClosureInstruction.java
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

import static com.avail.interpreter.levelTwo.L2Operation.L2_doCreateClosureFromCodeObject_outersVector_destObject_;
import java.util.*;
import com.avail.descriptor.*;
import com.avail.interpreter.levelTwo.*;
import com.avail.interpreter.levelTwo.register.*;

public class L2CreateClosureInstruction extends L2Instruction
{
	AvailObject _code;
	L2RegisterVector _outersVector;
	L2ObjectRegister _dest;


	// accessing

	@Override
	public List<L2Register> destinationRegisters ()
	{
		//  Answer a collection of registers written to by this instruction.

		final List<L2Register> result = new ArrayList<L2Register>(1);
		result.add(_dest);
		return result;
	}

	@Override
	public List<L2Register> sourceRegisters ()
	{
		//  Answer a collection of registers read by this instruction.

		final List<L2Register> result = new ArrayList<L2Register>(_outersVector.registers().size());
		result.addAll(_outersVector.registers());
		return result;
	}



	// code generation

	@Override
	public void emitOn (
			final L2CodeGenerator anL2CodeGenerator)
	{
		//  Emit this instruction to the code generator.

		anL2CodeGenerator.emitWord(L2_doCreateClosureFromCodeObject_outersVector_destObject_.ordinal());
		anL2CodeGenerator.emitLiteral(_code);
		anL2CodeGenerator.emitVector(_outersVector);
		anL2CodeGenerator.emitObjectRegister(_dest);
	}



	// initialization

	public L2CreateClosureInstruction codeOutersVectorDestObject (
			final AvailObject codeLiteral,
			final L2RegisterVector outerVect,
			final L2ObjectRegister destClosure)
	{
		_code = codeLiteral;
		_outersVector = outerVect;
		_dest = destClosure;
		return this;
	}



	// typing

	@Override
	public void propagateTypeInfoFor (
			final L2Translator anL2Translator)
	{
		//  Propagate type information due to this instruction.

		anL2Translator.registerTypeAtPut(_dest, _code.closureType());
		if (_outersVector.allRegistersAreConstantsIn(anL2Translator))
		{
			final AvailObject closure = ClosureDescriptor.mutable().create(
				_outersVector.registers().size());
			closure.code(_code);
			for (int i = 1, _end1 = _outersVector.registers().size(); i <= _end1; i++)
			{
				closure.outerVarAtPut(i, anL2Translator.registerConstantAt(_outersVector.registers().get(i - 1)));
			}
		}
		else
		{
			anL2Translator.removeConstantForRegister(_dest);
		}
	}





}
