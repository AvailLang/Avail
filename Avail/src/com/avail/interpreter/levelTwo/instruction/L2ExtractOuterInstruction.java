/**
 * interpreter/levelTwo/instruction/L2ExtractOuterInstruction.java
 * Copyright (c) 2010, Mark van Gulik.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this *   list of conditions and the following disclaimer.
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
import com.avail.interpreter.levelTwo.instruction.L2ExtractOuterInstruction;
import com.avail.interpreter.levelTwo.register.L2ObjectRegister;
import com.avail.interpreter.levelTwo.register.L2Register;
import java.util.ArrayList;
import static com.avail.interpreter.levelTwo.L2Operation.*;

public class L2ExtractOuterInstruction extends L2Instruction
{
	int _outerNumber;
	L2ObjectRegister _closureReg;
	L2ObjectRegister _dest;


	// accessing

	public ArrayList<L2Register> destinationRegisters ()
	{
		//  Answer a collection of registers written to by this instruction.

		ArrayList<L2Register> result = new ArrayList<L2Register>(1);
		result.add(_dest);
		return result;
	}

	public ArrayList<L2Register> sourceRegisters ()
	{
		//  Answer a collection of registers read by this instruction.

		ArrayList<L2Register> result = new ArrayList<L2Register>(1);
		result.add(_closureReg);
		return result;
	}



	// code generation

	public void emitOn (
			final L2CodeGenerator anL2CodeGenerator)
	{
		//  Emit this instruction to the code generator.

		anL2CodeGenerator.emitWord(L2_doMoveFromOuterVariable_ofClosureObject_destObject_.ordinal());
		anL2CodeGenerator.emitWord(_outerNumber);
		anL2CodeGenerator.emitObjectRegister(_closureReg);
		anL2CodeGenerator.emitObjectRegister(_dest);
	}



	// initialization

	public L2ExtractOuterInstruction closureRegisterOuterNumberDestination (
			final L2ObjectRegister closureRegister, 
			final int outerNum, 
			final L2ObjectRegister destRegister)
	{
		_closureReg = closureRegister;
		_outerNumber = outerNum;
		_dest = destRegister;
		return this;
	}



	// typing

	public void propagateTypeInfoFor (
			final L2Translator anL2Translator)
	{
		//  Propagate type information due to this instruction.
		//
		//  Outer variables are treated as untyped here, because the same code object may
		//  occur within multiple parent code objects.  That's because of the coalescing garbage
		//  collector, which can merge equal code objects.  In different parent code objects, the
		//  child code object may have different type expectations.  This isn't as bad as it seems,
		//  as closures are very profitable to inline anyhow, greatly reducing occurrences of this
		//  lack of type information.

		anL2Translator.removeTypeForRegister(_dest);
		anL2Translator.removeConstantForRegister(_dest);
	}





}
