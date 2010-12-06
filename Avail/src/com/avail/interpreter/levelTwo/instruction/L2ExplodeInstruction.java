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

import com.avail.interpreter.levelTwo.L2CodeGenerator;
import com.avail.interpreter.levelTwo.L2Translator;
import com.avail.interpreter.levelTwo.instruction.L2ExplodeInstruction;
import com.avail.interpreter.levelTwo.register.L2ObjectRegister;
import com.avail.interpreter.levelTwo.register.L2Register;
import com.avail.interpreter.levelTwo.register.L2RegisterVector;
import java.util.ArrayList;
import java.util.List;
import static com.avail.interpreter.levelTwo.L2Operation.*;

public class L2ExplodeInstruction extends L2Instruction
{
	L2ObjectRegister _toExplode;
	L2ObjectRegister _destSender;
	L2ObjectRegister _destClosure;
	L2RegisterVector _destVector;


	// accessing

	@Override
	public List<L2Register> destinationRegisters ()
	{
		//  Answer a collection of registers written to by this instruction.

		List<L2Register> result = new ArrayList<L2Register>(2 + _destVector.registers().size());
		result.add(_destSender);
		result.add(_destClosure);
		result.addAll(_destVector.registers());
		return result;
	}

	@Override
	public List<L2Register> sourceRegisters ()
	{
		//  Answer a collection of registers read by this instruction.

		List<L2Register> result = new ArrayList<L2Register>(1);
		result.add(_toExplode);
		return result;
	}



	// code generation

	@Override
	public void emitOn (
			final L2CodeGenerator anL2CodeGenerator)
	{
		//  Emit this instruction to the code generator.

		anL2CodeGenerator.emitWord(L2_doExplodeContinuationObject_senderDestObject_closureDestObject_slotsDestVector_.ordinal());
		anL2CodeGenerator.emitObjectRegister(_toExplode);
		anL2CodeGenerator.emitObjectRegister(_destSender);
		anL2CodeGenerator.emitObjectRegister(_destClosure);
		anL2CodeGenerator.emitVector(_destVector);
	}



	// initialization

	public L2ExplodeInstruction toExplodeDestSenderDestClosureDestVector (
			final L2ObjectRegister givenContinuationRegister,
			final L2ObjectRegister senderReg,
			final L2ObjectRegister closureReg,
			final L2RegisterVector regVector)
	{
		_toExplode = givenContinuationRegister;
		_destSender = senderReg;
		_destClosure = closureReg;
		_destVector = regVector;
		return this;
	}



	// typing

	@Override
	public void propagateTypeInfoFor (
			final L2Translator anL2Translator)
	{
		//  Propagate type information due to this instruction.
		//
		//  Hm.  This explode instruction should have captured type information for its
		//  continuation slots.  For now just clear them all (yuck).  Later, we will capture
		//  this in the call instruction, suitably adjusted with an expectation of a return
		//  value (of the appropriate type) on the stack.
		//
		//  Clear all types, then set the sender and closure types (the slot types will be set by L2Translator>>doCall)...

		anL2Translator.clearRegisterTypes();
		//  anL2Translator registerTypes at: destSender identity put: TypeDescriptor continuation.
		//
		//  unused information
		anL2Translator.registerTypeAtPut(_destClosure, anL2Translator.code().closureType());
		anL2Translator.clearRegisterConstants();
	}





}
