/**
 * L2_LOOKUP_BY_VALUES.java
 * Copyright © 1993-2013, Mark van Gulik and Todd L Smith.
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
package com.avail.interpreter.levelTwo.operation;

import static com.avail.descriptor.AvailObject.error;
import static com.avail.descriptor.TypeDescriptor.Types.TOP;
import static com.avail.interpreter.Interpreter.debugL1;
import static com.avail.interpreter.levelTwo.L2OperandType.*;
import java.util.*;
import com.avail.descriptor.*;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.levelTwo.*;
import com.avail.interpreter.levelTwo.operand.*;
import com.avail.interpreter.levelTwo.register.L2ObjectRegister;
import com.avail.optimizer.RegisterSet;

/**
 * Look up the method to invoke.  Use the provided vector of arguments to
 * perform a polymorphic lookup.  Write the resulting function into the
 * specified destination register.
 */
public class L2_LOOKUP_BY_VALUES extends L2Operation
{
	/**
	 * Initialize the sole instance.
	 */
	public final static L2Operation instance =
		new L2_LOOKUP_BY_VALUES().init(
			SELECTOR.is("message bundle"),
			READ_VECTOR.is("arguments"),
			WRITE_POINTER.is("looked up function"));

	@Override
	public void step (final Interpreter interpreter)
	{
		final int selectorIndex = interpreter.nextWord();
		final int argumentsIndex = interpreter.nextWord();
		final int resultingFunctionIndex = interpreter.nextWord();
		final A_Tuple vect = interpreter.vectorAt(argumentsIndex);
		interpreter.argsBuffer.clear();
		final int numArgs = vect.tupleSize();
		for (int i = 1; i <= numArgs; i++)
		{
			interpreter.argsBuffer.add(
				interpreter.pointerAt(vect.tupleIntAt(i)));
		}
		final A_Bundle bundle = interpreter.chunk().literalAt(selectorIndex);
		final A_Method method = bundle.bundleMethod();
		if (debugL1)
		{
			System.out.printf(
				"  --- looking up: %s%n",
				bundle.message().atomName());
		}
		final A_Definition definitionToCall =
			method.lookupByValuesFromList(interpreter.argsBuffer);
		if (definitionToCall.equalsNil())
		{
			error("Unable to find unique definition for call");
			return;
		}
		if (!definitionToCall.isMethodDefinition())
		{
			error("Attempted to call a non-method definition");
			return;
		}
		interpreter.pointerAtPut(
			resultingFunctionIndex,
			definitionToCall.bodyBlock());
	}

	@Override
	public void propagateTypes (
		final L2Instruction instruction,
		final RegisterSet registerSet)
	{
		// Find all possible definitions (taking into account the types
		// of the argument registers).  Then build an enumeration type over
		// those functions.
		final L2SelectorOperand selectorOperand =
			(L2SelectorOperand) instruction.operands[0];
		final L2ReadVectorOperand argsOperand =
			(L2ReadVectorOperand) instruction.operands[1];
		final L2WritePointerOperand destinationOperand =
			(L2WritePointerOperand) instruction.operands[2];
		final List<L2ObjectRegister> argRegisters =
			argsOperand.vector.registers();
		final int numArgs = argRegisters.size();
		final List<A_Type> argTypeBounds =
			new ArrayList<A_Type>(numArgs);
		for (final L2ObjectRegister argRegister : argRegisters)
		{
			final A_Type type = registerSet.hasTypeAt(argRegister)
				? registerSet.typeAt(argRegister)
				: TOP.o();
			argTypeBounds.add(type);
		}
		// Figure out what could be invoked at runtime given these argument
		// type constraints.
		final List<A_Function> possibleFunctions =
			new ArrayList<A_Function>();
		final List<A_Definition> possibleDefinitions =
			selectorOperand.bundle.bundleMethod().definitionsAtOrBelow(
				argTypeBounds);
		for (final A_Definition definition : possibleDefinitions)
		{
			if (definition.isMethodDefinition())
			{
				possibleFunctions.add(definition.bodyBlock());
			}
		}
		if (possibleFunctions.size() == 1)
		{
			// Only one function could be looked up (it's monomorphic for
			// this call site).  Therefore we know strongly what the
			// function is.
			registerSet.constantAtPut(
				destinationOperand.register,
				possibleFunctions.get(0),
				instruction);
		}
		else
		{
			final A_Type enumType =
				AbstractEnumerationTypeDescriptor.withInstances(
					SetDescriptor.fromCollection(possibleFunctions));
			registerSet.typeAtPut(
				destinationOperand.register,
				enumType,
				instruction);
		}
	}
}
