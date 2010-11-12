/**
 * interpreter/levelTwo/instruction/L2GetTypeInstruction.java
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
import com.avail.descriptor.TypeDescriptor.Types;
import com.avail.interpreter.levelTwo.L2CodeGenerator;
import com.avail.interpreter.levelTwo.L2Translator;
import com.avail.interpreter.levelTwo.instruction.L2GetTypeInstruction;
import com.avail.interpreter.levelTwo.register.L2ObjectRegister;
import com.avail.interpreter.levelTwo.register.L2Register;
import java.util.ArrayList;
import static com.avail.interpreter.levelTwo.L2Operation.*;

public class L2GetTypeInstruction extends L2Instruction
{
	L2ObjectRegister _source;
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
		result.add(_source);
		return result;
	}



	// code generation

	public void emitOn (
			final L2CodeGenerator anL2CodeGenerator)
	{
		//  Emit this instruction to the code generator.

		anL2CodeGenerator.emitWord(L2_doGetType_destObject_.ordinal());
		anL2CodeGenerator.emitObjectRegister(_source);
		anL2CodeGenerator.emitObjectRegister(_dest);
	}



	// initialization

	public L2GetTypeInstruction sourceDestination (
			final L2ObjectRegister sourceReg, 
			final L2ObjectRegister destinationReg)
	{
		_source = sourceReg;
		_dest = destinationReg;
		return this;
	}



	// typing

	public void propagateTypeInfoFor (
			final L2Translator anL2Translator)
	{
		//  Propagate type information due to this instruction.

		if (anL2Translator.registerHasTypeAt(_source))
		{
			final AvailObject type = anL2Translator.registerTypeAt(_source);
			//  Apply the rule of metacovariance.  It says that given types T1 and T2, T1 <= T2 implies T1 type <= T2 type.
			//  It is guaranteed true for all types in Avail.
			final AvailObject meta = type.type();
			anL2Translator.registerTypeAtPut(_dest, meta);
		}
		else
		{
			anL2Translator.registerTypeAtPut(_dest, Types.type.object());
		}
		if (anL2Translator.registerHasConstantAt(_source))
		{
			anL2Translator.registerConstantAtPut(_dest, anL2Translator.registerConstantAt(_source).type());
		}
		else
		{
			anL2Translator.removeConstantForRegister(_dest);
		}
	}





}
