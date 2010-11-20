/**
 * interpreter/levelTwo/instruction/L2CreateContinuationInstruction.java
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

import com.avail.descriptor.ContinuationTypeDescriptor;
import com.avail.interpreter.levelTwo.L2CodeGenerator;
import com.avail.interpreter.levelTwo.L2Translator;
import com.avail.interpreter.levelTwo.instruction.L2CreateContinuationInstruction;
import com.avail.interpreter.levelTwo.instruction.L2LabelInstruction;
import com.avail.interpreter.levelTwo.register.L2ObjectRegister;
import com.avail.interpreter.levelTwo.register.L2Register;
import com.avail.interpreter.levelTwo.register.L2RegisterVector;
import java.util.ArrayList;
import static com.avail.interpreter.levelTwo.L2Operation.*;

public class L2CreateContinuationInstruction extends L2Instruction
{
	L2ObjectRegister _caller;
	L2ObjectRegister _closure;
	int _pc;
	int _stackp;
	int _size;
	L2RegisterVector _slotsVector;
	L2LabelInstruction _continuationLabel;
	L2ObjectRegister _dest;


	// accessing

	@Override
	public ArrayList<L2Register> destinationRegisters ()
	{
		//  Answer a collection of registers written to by this instruction.

		ArrayList<L2Register> result = new ArrayList<L2Register>(1);
		result.add(_dest);
		return result;
	}

	@Override
	public ArrayList<L2Register> sourceRegisters ()
	{
		//  Answer a collection of registers read by this instruction.

		ArrayList<L2Register> result = new ArrayList<L2Register>(2 + _slotsVector.registers().size());
		result.add(_caller);
		result.add(_closure);
		result.addAll(_slotsVector.registers());
		return result;
	}



	// code generation

	@Override
	public void emitOn (
			final L2CodeGenerator anL2CodeGenerator)
	{
		//  Emit this instruction to the code generator.

		anL2CodeGenerator.emitWord(L2_doCreateContinuationWithSenderObject_closureObject_pcInteger_stackpInteger_sizeImmediate_slotsVector_wordcodeOffset_destObject_.ordinal());
		anL2CodeGenerator.emitObjectRegister(_caller);
		anL2CodeGenerator.emitObjectRegister(_closure);
		anL2CodeGenerator.emitWord(_pc);
		anL2CodeGenerator.emitWord(_stackp);
		anL2CodeGenerator.emitWord(_size);
		anL2CodeGenerator.emitVector(_slotsVector);
		anL2CodeGenerator.emitWord(_continuationLabel.offset());
		anL2CodeGenerator.emitObjectRegister(_dest);
	}



	// initialization

	public L2CreateContinuationInstruction callerClosurePcStackpSizeSlotsVectorLabelDestination (
			final L2ObjectRegister callerReg, 
			final L2ObjectRegister closureReg, 
			final int pcInt, 
			final int spInt, 
			final int sizeInt, 
			final L2RegisterVector slots, 
			final L2LabelInstruction label, 
			final L2ObjectRegister destination)
	{
		_caller = callerReg;
		_closure = closureReg;
		_pc = pcInt;
		_stackp = spInt;
		_size = sizeInt;
		_slotsVector = slots;
		_continuationLabel = label;
		_dest = destination;
		return this;
	}



	// typing

	@Override
	public void propagateTypeInfoFor (
			final L2Translator anL2Translator)
	{
		//  Propagate type information due to this instruction.

		anL2Translator.registerTypeAtPut(_dest, ContinuationTypeDescriptor.continuationTypeForClosureType(anL2Translator.code().closureType()));
		anL2Translator.removeConstantForRegister(_dest);
	}





}
