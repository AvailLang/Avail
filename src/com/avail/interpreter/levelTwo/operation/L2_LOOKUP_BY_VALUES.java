/**
 * L2_LOOKUP_BY_VALUES.java
 * Copyright © 1993-2017, The Avail Foundation, LLC.
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

import static com.avail.exceptions.AvailErrorCode.*;
import static com.avail.descriptor.TypeDescriptor.Types.ANY;
import static com.avail.interpreter.levelTwo.L2OperandType.*;
import java.util.*;
import java.util.logging.Level;
import com.avail.descriptor.*;
import com.avail.exceptions.MethodDefinitionException;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.levelTwo.*;
import com.avail.interpreter.levelTwo.register.L2ObjectRegister;
import com.avail.interpreter.levelTwo.register.L2RegisterVector;
import com.avail.optimizer.L2Translator;
import com.avail.optimizer.RegisterSet;

/**
 * Look up the method to invoke. Use the provided vector of arguments to
 * perform a polymorphic lookup. Write the resulting function into the
 * specified destination register. If the lookup fails, then branch to the
 * specified {@linkplain Interpreter#offset(int) offset}.
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
			WRITE_POINTER.is("looked up function"),
			PC.is("lookup succeeded"),
			WRITE_POINTER.is("error code"));

	@Override
	public void step (
		final L2Instruction instruction,
		final Interpreter interpreter)
	{
		final A_Bundle bundle = instruction.bundleAt(0);
		final L2RegisterVector argsVector = instruction.readVectorRegisterAt(1);
		final L2ObjectRegister functionReg =
			instruction.writeObjectRegisterAt(2);
		final int lookedSucceeded = instruction.pcAt(3);
		final L2ObjectRegister errorCodeReg =
			instruction.writeObjectRegisterAt(4);

		if (Interpreter.debugL2)
		{
			Interpreter.log(
				Level.FINER,
				"Lookup {0}",
				bundle.message().atomName());
		}
		interpreter.argsBuffer.clear();
		for (final L2ObjectRegister argumentReg : argsVector.registers())
		{
			interpreter.argsBuffer.add(argumentReg.in(interpreter));
		}
		final A_Method method = bundle.bundleMethod();
		final long before = System.nanoTime();
		final A_Definition definitionToCall;
		try
		{
			definitionToCall =
				method.lookupByValuesFromList(interpreter.argsBuffer);
		}
		catch (MethodDefinitionException e)
		{
			errorCodeReg.set(e.numericCode(), interpreter);
			// Fall through to the next instruction.
			return;
		}
		finally
		{
			final long after = System.nanoTime();
			interpreter.recordDynamicLookup(bundle, after - before);
		}
		if (definitionToCall.isAbstractDefinition())
		{
			errorCodeReg.set(
				E_ABSTRACT_METHOD_DEFINITION.numericCode(), interpreter);
			// Fall through to the next instruction.
			return;
		}
		if (definitionToCall.isForwardDefinition())
		{
			errorCodeReg.set(
				E_FORWARD_METHOD_DEFINITION.numericCode(), interpreter);
			// Fall through to the next instruction.
			return;
		}
		functionReg.set(definitionToCall.bodyBlock(), interpreter);
		interpreter.offset(lookedSucceeded);
	}

	@Override
	protected void propagateTypes (
		final L2Instruction instruction,
		final List<RegisterSet> registerSets,
		final L2Translator translator)
	{
		// Find all possible definitions (taking into account the types
		// of the argument registers).  Then build an enumeration type over
		// those functions.
		final A_Bundle bundle = instruction.bundleAt(0);
		final L2RegisterVector argsVector = instruction.readVectorRegisterAt(1);
		final L2ObjectRegister functionReg =
			instruction.writeObjectRegisterAt(2);
		final L2ObjectRegister errorCodeReg =
			instruction.writeObjectRegisterAt(4);
		// If the lookup fails, then only the error code register changes.
		registerSets.get(0).typeAtPut(
			errorCodeReg,
			AbstractEnumerationTypeDescriptor.withInstances(
				TupleDescriptor.from(
						E_NO_METHOD.numericCode(),
						E_NO_METHOD_DEFINITION.numericCode(),
						E_AMBIGUOUS_METHOD_DEFINITION.numericCode(),
						E_FORWARD_METHOD_DEFINITION.numericCode(),
						E_ABSTRACT_METHOD_DEFINITION.numericCode())
					.asSet()),
			instruction);
		// If the lookup succeeds, then the situation is more complex.
		final RegisterSet registerSet = registerSets.get(1);
		final List<L2ObjectRegister> argRegisters = argsVector.registers();
		final int numArgs = argRegisters.size();
		final List<A_Type> argTypeBounds = new ArrayList<>(numArgs);
		for (final L2ObjectRegister argRegister : argRegisters)
		{
			final A_Type type = registerSet.hasTypeAt(argRegister)
				? registerSet.typeAt(argRegister)
				: ANY.o();
			argTypeBounds.add(type);
		}
		// Figure out what could be invoked at runtime given these argument
		// type constraints.
		final List<A_Function> possibleFunctions = new ArrayList<>();
		final List<A_Definition> possibleDefinitions =
			bundle.bundleMethod().definitionsAtOrBelow(argTypeBounds);
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
				functionReg,
				possibleFunctions.get(0),
				instruction);
		}
		else
		{
			final A_Type enumType =
				AbstractEnumerationTypeDescriptor.withInstances(
					SetDescriptor.fromCollection(possibleFunctions));
			registerSet.typeAtPut(functionReg, enumType, instruction);
		}
	}
}
