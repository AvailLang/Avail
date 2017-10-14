/**
 * L2_LOOKUP_BY_TYPES.java
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

import com.avail.AvailRuntime;
import com.avail.descriptor.A_Bundle;
import com.avail.descriptor.A_Definition;
import com.avail.descriptor.A_Function;
import com.avail.descriptor.A_Method;
import com.avail.descriptor.A_Type;
import com.avail.exceptions.MethodDefinitionException;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2Operation;
import com.avail.interpreter.levelTwo.operand.L2ReadPointerOperand;
import com.avail.interpreter.levelTwo.operand.L2WritePointerOperand;
import com.avail.interpreter.levelTwo.register.L2ObjectRegister;
import com.avail.optimizer.L2Translator;
import com.avail.optimizer.RegisterSet;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

import static com.avail.descriptor.AbstractEnumerationTypeDescriptor
	.enumerationWith;
import static com.avail.descriptor.SetDescriptor.set;
import static com.avail.descriptor.SetDescriptor.setFromCollection;
import static com.avail.descriptor.TupleDescriptor.tupleFromList;
import static com.avail.descriptor.TypeDescriptor.Types.ANY;
import static com.avail.exceptions.AvailErrorCode.*;
import static com.avail.interpreter.levelTwo.L2OperandType.*;

/**
 * Look up the method to invoke. Use the provided vector of argument types to
 * perform a polymorphic lookup. Write the resulting function into the
 * specified destination register. If the lookup fails, then branch to the
 * specified {@linkplain Interpreter#offset(int) offset}.
 */
public class L2_LOOKUP_BY_TYPES extends L2Operation
{
	/**
	 * Initialize the sole instance.
	 */
	public static final L2Operation instance =
		new L2_LOOKUP_BY_VALUES().init(
			SELECTOR.is("message bundle"),
			READ_VECTOR.is("argument types"),
			WRITE_POINTER.is("looked up function"),
			WRITE_POINTER.is("error code"),
			PC.is("lookup succeeded"),
			PC.is("lookup failed"));

	@Override
	public void step (
		final L2Instruction instruction,
		final Interpreter interpreter)
	{
		final A_Bundle bundle = instruction.bundleAt(0);
		final List<L2ReadPointerOperand> argTypeRegs =
			instruction.readVectorRegisterAt(1);
		final L2WritePointerOperand functionReg =
			instruction.writeObjectRegisterAt(2);
		final L2WritePointerOperand errorCodeReg =
			instruction.writeObjectRegisterAt(3);
		final int lookupSucceeded = instruction.pcOffsetAt(4);
		final int lookupFailed = instruction.pcOffsetAt(5);

		if (Interpreter.debugL2)
		{
			Interpreter.log(
				Level.FINER,
				"Lookup-by-types {0}",
				bundle.message().atomName());
		}
		interpreter.argsBuffer.clear();
		for (final L2ReadPointerOperand argTypeReg : argTypeRegs)
		{
			interpreter.argsBuffer.add(argTypeReg.in(interpreter));
		}
		final A_Method method = bundle.bundleMethod();
		final long before = AvailRuntime.captureNanos();
		final A_Definition definitionToCall;
		try
		{
			definitionToCall = method.lookupByTypesFromTuple(
				tupleFromList(interpreter.argsBuffer));
		}
		catch (final MethodDefinitionException e)
		{
			errorCodeReg.set(e.numericCode(), interpreter);
			// Fall through to the next instruction.
			interpreter.offset(lookupFailed);
			return;
		}
		finally
		{
			final long after = AvailRuntime.captureNanos();
			interpreter.recordDynamicLookup(bundle, after - before);
		}
		if (definitionToCall.isAbstractDefinition())
		{
			errorCodeReg.set(
				E_ABSTRACT_METHOD_DEFINITION.numericCode(), interpreter);
			// Fall through to the next instruction.
			interpreter.offset(lookupFailed);
			return;
		}
		if (definitionToCall.isForwardDefinition())
		{
			errorCodeReg.set(
				E_FORWARD_METHOD_DEFINITION.numericCode(), interpreter);
			// Fall through to the next instruction.
			interpreter.offset(lookupFailed);
			return;
		}
		functionReg.set(definitionToCall.bodyBlock(), interpreter);
		interpreter.offset(lookupSucceeded);
	}

	/** The type of failure codes that a failed lookup can produce. */
	private final A_Type failureCodesType = enumerationWith(
		set(
			E_NO_METHOD,
			E_NO_METHOD_DEFINITION,
			E_AMBIGUOUS_METHOD_DEFINITION,
			E_FORWARD_METHOD_DEFINITION,
			E_ABSTRACT_METHOD_DEFINITION));

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
		final List<L2ReadPointerOperand> argTypeRegs =
			instruction.readVectorRegisterAt(1);
		final L2WritePointerOperand functionReg =
			instruction.writeObjectRegisterAt(2);
		final L2WritePointerOperand errorCodeReg =
			instruction.writeObjectRegisterAt(3);
//		final int lookupSucceeded = instruction.pcOffsetAt(4);
//		final int lookupFailed = instruction.pcOffsetAt(5);

		// If the lookup fails, then only the error code register changes.
		registerSets.get(0).typeAtPut(
			errorCodeReg.register(), failureCodesType, instruction);
		// If the lookup succeeds, then the situation is more complex.
		final RegisterSet registerSet = registerSets.get(1);
		final int numArgs = argTypeRegs.size();
		final List<A_Type> argTypeBounds = new ArrayList<>(numArgs);
		for (final L2ReadPointerOperand argRegister : argTypeRegs)
		{
			final A_Type type = registerSet.hasTypeAt(argRegister.register())
				? registerSet.typeAt(argRegister.register())
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
				functionReg.register(),
				possibleFunctions.get(0),
				instruction);
		}
		else
		{
			final A_Type enumType =
				enumerationWith(setFromCollection(possibleFunctions));
			registerSet.typeAtPut(
				functionReg.register(), enumType, instruction);
		}
	}
}
