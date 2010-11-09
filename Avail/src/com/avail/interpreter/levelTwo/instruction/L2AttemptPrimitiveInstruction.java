/**
 * interpreter/levelTwo/instruction/L2AttemptPrimitiveInstruction.java
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

import com.avail.interpreter.levelTwo.L2CodeGenerator;
import com.avail.interpreter.levelTwo.L2Translator;
import com.avail.interpreter.levelTwo.instruction.L2AttemptPrimitiveInstruction;
import com.avail.interpreter.levelTwo.instruction.L2LabelInstruction;
import com.avail.interpreter.levelTwo.register.L2ObjectRegister;
import com.avail.interpreter.levelTwo.register.L2Register;
import com.avail.interpreter.levelTwo.register.L2RegisterVector;
import java.util.ArrayList;
import static com.avail.interpreter.levelTwo.L2Operation.*;

public class L2AttemptPrimitiveInstruction extends L2Instruction
{
	int _primNum;
	L2RegisterVector _arguments;
	L2ObjectRegister _dest;
	L2LabelInstruction _failureLabel;


	// accessing

	public ArrayList<L2Register> destinationRegisters ()
	{
		//  Answer a collection of registers written to by this instruction.  Since a call can clear all registers,
		//  we could try to list all registers as destinations.  Instead, we treat calls as the ends of the basic
		//  blocks during flow analysis.

		ArrayList<L2Register> result = new ArrayList<L2Register>(1);
		result.add(_dest);
		return result;
	}

	public ArrayList<L2Register> sourceRegisters ()
	{
		//  Answer a collection of registers read by this instruction.

		ArrayList<L2Register> result = new ArrayList<L2Register>(_arguments.registers().size());
		result.addAll(_arguments.registers());
		return result;
	}



	// code generation

	public void emitOn (
			final L2CodeGenerator anL2CodeGenerator)
	{
		//  Emit this instruction to the code generator.

		anL2CodeGenerator.emitWord(L2_doAttemptPrimitive_withArguments_result_ifFail_.ordinal());
		anL2CodeGenerator.emitWord(_primNum);
		anL2CodeGenerator.emitVector(_arguments);
		anL2CodeGenerator.emitObjectRegister(_dest);
		anL2CodeGenerator.emitWord(_failureLabel.offset());
	}



	// initialization

	public L2AttemptPrimitiveInstruction primitiveArgumentsDestinationIfFail (
			final int prim, 
			final L2RegisterVector args, 
			final L2ObjectRegister destination, 
			final L2LabelInstruction ifFail)
	{
		_primNum = prim;
		_arguments = args;
		_dest = destination;
		_failureLabel = ifFail;
		return this;
	}



	// typing

	public void propagateTypeInfoFor (
			final L2Translator anL2Translator)
	{
		//  Propagate type information due to this instruction.

		anL2Translator.removeTypeForRegister(_dest);
		anL2Translator.removeConstantForRegister(_dest);
	}





}
