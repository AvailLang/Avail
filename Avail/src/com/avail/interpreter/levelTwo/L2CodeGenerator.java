/**
 * interpreter/levelTwo/L2CodeGenerator.java
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

package com.avail.interpreter.levelTwo;

import com.avail.descriptor.AvailObject;
import com.avail.descriptor.L2ChunkDescriptor;
import com.avail.descriptor.SetDescriptor;
import com.avail.interpreter.levelTwo.instruction.L2Instruction;
import com.avail.interpreter.levelTwo.register.L2FloatRegister;
import com.avail.interpreter.levelTwo.register.L2IntegerRegister;
import com.avail.interpreter.levelTwo.register.L2ObjectRegister;
import com.avail.interpreter.levelTwo.register.L2RegisterVector;
import java.util.ArrayList;
import static java.lang.Math.*;

public class L2CodeGenerator
{
	ArrayList<AvailObject> _literals;
	ArrayList<ArrayList<Integer>> _vectors;
	int _numObjects;
	int _numIntegers;
	int _numFloats;
	ArrayList<Integer> _wordcodes;
	AvailObject _contingentImpSets;


	// accessing

	public ArrayList<Integer> wordcodes ()
	{
		return _wordcodes;
	}



	// code generation

	public void addContingentImplementationSets (
			final AvailObject aSetOfImpSets)
	{
		_contingentImpSets = _contingentImpSets.setUnionCanDestroy(aSetOfImpSets, true);
	}

	public AvailObject createChunkFor (
			final AvailObject code)
	{
		//  The instructions have all been emitted at this point.  Create the actual chunk.

		return L2ChunkDescriptor.allocateIndexCodeLiteralsVectorsNumObjectsNumIntegersNumFloatsWordcodesContingentImpSets(
			true,
			code,
			_literals,
			_vectors,
			_numObjects,
			_numIntegers,
			_numFloats,
			_wordcodes,
			_contingentImpSets);
	}

	public void emitCallerRegister ()
	{
		//  This is a fixed architectural register.
		emitWord(1);
	}

	public void emitFloatRegister (
			final L2FloatRegister floatRegister)
	{

		final int index = floatRegister.finalIndex();
		if (index != -1)
		{
			_numFloats = max(_numFloats, index);
		}
		emitWord(index);
	}

	public void emitIntegerRegister (
			final L2IntegerRegister integerRegister)
	{

		final int index = integerRegister.finalIndex();
		if (index != -1)
		{
			_numIntegers = max(_numIntegers, index);
		}
		emitWord(index);
	}

	public void emitLiteral (
			final AvailObject aLiteral)
	{
		aLiteral.readBarrierFault();
		assert !aLiteral.descriptor().isMutable();
		int index = _literals.indexOf(aLiteral) + 1;
		if (index == 0)
		{
			_literals.add(aLiteral);
			index = _literals.size();
		}
		emitWord(index);
	}

	public void emitObjectRegister (
			final L2ObjectRegister objectRegister)
	{

		final int index = objectRegister.finalIndex();
		if (index != -1)
		{
			_numObjects = max(_numObjects, index);
		}
		emitWord(index);
	}

	public void emitVector (
			final L2RegisterVector registerVector)
	{
		ArrayList<L2ObjectRegister> registersList = registerVector.registers();
		ArrayList<Integer> registerIndices = new ArrayList<Integer>(registersList.size());
		for (int i = 0; i < registersList.size(); i++)
		{
			registerIndices.add(registersList.get(i).finalIndex());
		}
		int vectorIndex = _vectors.indexOf(registerIndices) + 1;
		if (vectorIndex == 0)
		{
			_vectors.add(registerIndices);
			vectorIndex = _vectors.size();
		}
		emitWord(vectorIndex);
	}

	public void emitWord (
			final int word)
	{
		assert ((word >= -0x8000) && (word <= 0x7FFF)) : "Word is out of range";
		_wordcodes.add(word);
	}

	public void forceArgumentCountToAtLeast (
			final int minArgCount)
	{
		//  Take steps to ensure there will be at least minArgCount object registers available at runtime.

		_numObjects = max(_numObjects, minArgCount);
	}

	public void setInstructions (
			final ArrayList<L2Instruction> instrs)
	{
		//  Set my collection of instructions.  Generate the wordcodes at this time.

		// Generate the instructions, but be prepared to discard the generated wordcodes.  The
		// wordcodes are generated as useless side-effect on this pass, as the main intent is to
		// measure instruction lengths to set their offsets (so references to labels will be correct).
		assert _wordcodes.size() == 0;
		for (L2Instruction instruction : instrs)
		{
			instruction.offset(_wordcodes.size() + 1);
			instruction.emitOn(this);
		}
		// Now that the instruction positions are known, discard the scratch code generator and
		// generate on a real code generator.  This is a trivial two-pass scheme to calculate jumps.
		_wordcodes = new ArrayList<Integer>(_wordcodes.size());
		for (L2Instruction instruction : instrs)
		{
			assert instruction.offset() == _wordcodes.size() + 1 : "Instruction offset is not right";
			instruction.emitOn(this);
		}
	}





	/* Constructor */

	L2CodeGenerator ()
	{
		_literals = new ArrayList<AvailObject>(20);
		_vectors = new ArrayList<ArrayList<Integer>>(20);
		_numObjects = 0;
		_numIntegers = 0;
		_numFloats = 0;
		_wordcodes = new ArrayList<Integer>(20);
		_contingentImpSets = SetDescriptor.empty();
	}

	void emitOpcode(int opcode)
	{
		emitWord(opcode);
	};

}
