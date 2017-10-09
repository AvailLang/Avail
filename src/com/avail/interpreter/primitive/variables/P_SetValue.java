/**
 * P_SetValue.java
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
package com.avail.interpreter.primitive.variables;

import com.avail.descriptor.A_Function;
import com.avail.descriptor.A_Type;
import com.avail.descriptor.A_Variable;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.VariableDescriptor;
import com.avail.exceptions.VariableSetException;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;
import com.avail.interpreter.levelTwo.operand.L2PcOperand;
import com.avail.interpreter.levelTwo.operand.L2ReadPointerOperand;
import com.avail.interpreter.levelTwo.operand.L2ReadVectorOperand;
import com.avail.interpreter.levelTwo.operand.L2WritePointerOperand;
import com.avail.interpreter.levelTwo.operation.L2_SET_VARIABLE;
import com.avail.interpreter.levelTwo.operation.L2_SET_VARIABLE_NO_CHECK;
import com.avail.interpreter.levelTwo.register.L2ObjectRegister;
import com.avail.optimizer.L1NaiveTranslator;
import com.avail.optimizer.L2BasicBlock;

import java.util.List;

import static com.avail.descriptor.AbstractEnumerationTypeDescriptor
	.enumerationWith;
import static com.avail.descriptor.FunctionTypeDescriptor.functionType;
import static com.avail.descriptor.NilDescriptor.nil;
import static com.avail.descriptor.SetDescriptor.set;
import static com.avail.descriptor.TupleDescriptor.tuple;
import static com.avail.descriptor.TypeDescriptor.Types.ANY;
import static com.avail.descriptor.TypeDescriptor.Types.TOP;
import static com.avail.descriptor.VariableTypeDescriptor
	.mostGeneralVariableType;
import static com.avail.exceptions.AvailErrorCode.*;
import static com.avail.interpreter.Primitive.Flag.CanInline;
import static com.avail.interpreter.Primitive.Flag.HasSideEffect;

/**
 * <strong>Primitive:</strong> Assign the {@linkplain AvailObject value}
 * to the {@linkplain VariableDescriptor variable}.
 */
public final class P_SetValue
extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	public static final Primitive instance =
		new P_SetValue().init(
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
		return interpreter.primitiveSuccess(nil);
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return functionType(
			tuple(
				mostGeneralVariableType(),
				ANY.o()),
			TOP.o());
	}

	/**
	 * Use {@link L2_SET_VARIABLE_NO_CHECK} if possible, otherwise fall back on
	 * {@link L2_SET_VARIABLE}.
	 */
	@Override
	public void generateL2UnfoldableInlinePrimitive (
		final L1NaiveTranslator translator,
		final A_Function primitiveFunction,
		final L2ReadVectorOperand args,
		final L2WritePointerOperand resultWrite,
		final L2ReadVectorOperand preserved,
		final A_Type expectedType,
		final L2WritePointerOperand failureValueWrite,
		final L2BasicBlock successBlock,
		final boolean canFailPrimitive,
		final boolean skipReturnCheck)
	{
		final L2ReadPointerOperand varReg = args.elements().get(0);
		final L2ReadPointerOperand valueReg = args.elements().get(1);

		final A_Type varType = varReg.type();
		final A_Type valueType = valueReg.type();
		final A_Type varInnerType = varType.writeType();
		if (valueType.isSubtypeOf(varInnerType))
		{
			// It's a statically type-safe assignment.
			translator.addInstruction(
				L2_SET_VARIABLE_NO_CHECK.instance,
				varReg,
				valueReg,
				new L2PcOperand(successBlock));
		}
		else
		{
			// It's not statically type-safe.
			translator.addInstruction(
				L2_SET_VARIABLE.instance,
				varReg,
				valueReg,
				new L2PcOperand(successBlock));
		}
		// Either way, deal with a failed write by having the primitive inlined
		// in the fail case.
		super.generateL2UnfoldableInlinePrimitive(
			translator,
			primitiveFunction,
			args,
			resultWrite,
			preserved,
			expectedType,
			failureValueWrite,
			successBlock,
			canFailPrimitive,
			skipReturnCheck);
	}

	@Override
	protected A_Type privateFailureVariableType ()
	{
		return enumerationWith(
			set(
				E_CANNOT_STORE_INCORRECTLY_TYPED_VALUE,
				E_CANNOT_MODIFY_FINAL_JAVA_FIELD,
				E_JAVA_MARSHALING_FAILED,
				E_CANNOT_OVERWRITE_WRITE_ONCE_VARIABLE,
				E_OBSERVED_VARIABLE_WRITTEN_WHILE_UNTRACED));
	}
}
