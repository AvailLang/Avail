/**
 * P_011_SetValue.java
 * Copyright © 1993-2014, The Avail Foundation, LLC.
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
package com.avail.interpreter.primitive;

import static com.avail.descriptor.TypeDescriptor.Types.*;
import static com.avail.interpreter.Primitive.Flag.*;
import static com.avail.exceptions.AvailErrorCode.*;
import java.util.Arrays;
import java.util.List;
import com.avail.descriptor.*;
import com.avail.exceptions.VariableSetException;
import com.avail.interpreter.*;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.operand.L2PcOperand;
import com.avail.interpreter.levelTwo.operand.L2ReadPointerOperand;
import com.avail.interpreter.levelTwo.operation.L2_SET_VARIABLE;
import com.avail.interpreter.levelTwo.operation.L2_SET_VARIABLE_NO_CHECK;
import com.avail.interpreter.levelTwo.register.L2ObjectRegister;
import com.avail.interpreter.levelTwo.register.L2RegisterVector;
import com.avail.optimizer.RegisterSet;
import com.avail.optimizer.L2Translator.L1NaiveTranslator;

/**
 * <strong>Primitive 11:</strong> Assign the {@linkplain AvailObject value}
 * to the {@linkplain VariableDescriptor variable}.
 */
public final class P_011_SetValue
extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	public final static Primitive instance =
		new P_011_SetValue().init(
			2, CanInline, HasSideEffect);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter,
		final boolean skipReturnCheck)
	{
		assert args.size() == 2;
		final A_Variable var = args.get(0);
		final AvailObject value = args.get(1);
		try
		{
			var.setValue(value);
		}
		catch (final VariableSetException e)
		{
			return interpreter.primitiveFailure(e);
		}
		return interpreter.primitiveSuccess(NilDescriptor.nil());
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return FunctionTypeDescriptor.create(
			TupleDescriptor.from(
				VariableTypeDescriptor.mostGeneralType(),
				ANY.o()),
			TOP.o());
	}

	/**
	 * Use {@link L2_SET_VARIABLE_NO_CHECK} if possible, otherwise fall back on
	 * {@link L2_SET_VARIABLE}.
	 */
	@Override
	public void generateL2UnfoldableInlinePrimitive (
		final L1NaiveTranslator levelOneNaiveTranslator,
		final A_Function primitiveFunction,
		final L2RegisterVector args,
		final L2ObjectRegister resultRegister,
		final L2RegisterVector preserved,
		final A_Type expectedType,
		final L2ObjectRegister failureValueRegister,
		final L2Instruction successLabel,
		final boolean canFailPrimitive,
		final boolean skipReturnCheck)
	{
		final L2ObjectRegister varReg = args.registers().get(0);
		final L2ObjectRegister valueReg = args.registers().get(1);

		final RegisterSet registerSet =
			levelOneNaiveTranslator.naiveRegisters();
		final A_Type varType = registerSet.typeAt(varReg);
		final A_Type valueType = registerSet.typeAt(valueReg);
		final A_Type varInnerType = varType.writeType();
		if (valueType.isSubtypeOf(varInnerType))
		{
			// It's a statically type-safe assignment.
			levelOneNaiveTranslator.addInstruction(
				L2_SET_VARIABLE_NO_CHECK.instance,
				new L2ReadPointerOperand(varReg),
				new L2ReadPointerOperand(valueReg),
				new L2PcOperand(successLabel));
		}
		else
		{
			// It's not statically type-safe.
			levelOneNaiveTranslator.addInstruction(
				L2_SET_VARIABLE.instance,
				new L2ReadPointerOperand(varReg),
				new L2ReadPointerOperand(valueReg),
				new L2PcOperand(successLabel));
		}
		// Either way, deal with a failed write by having the primitive
		// inlined in the fail case.
		super.generateL2UnfoldableInlinePrimitive(
			levelOneNaiveTranslator,
			primitiveFunction,
			args,
			resultRegister,
			preserved,
			expectedType,
			failureValueRegister,
			successLabel,
			canFailPrimitive,
			skipReturnCheck);
	}

	@Override
	protected A_Type privateFailureVariableType ()
	{
		return AbstractEnumerationTypeDescriptor.withInstances(
			SetDescriptor.fromCollection(Arrays.asList(
				E_CANNOT_STORE_INCORRECTLY_TYPED_VALUE.numericCode(),
				E_CANNOT_MODIFY_FINAL_JAVA_FIELD.numericCode(),
				E_JAVA_MARSHALING_FAILED.numericCode(),
				E_CANNOT_OVERWRITE_WRITE_ONCE_VARIABLE.numericCode(),
				E_OBSERVED_VARIABLE_WRITTEN_WHILE_UNTRACED.numericCode())));
	}
}
