/**
 * L2_SET_VARIABLE_NO_CHECK.java
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

import com.avail.descriptor.A_Type;
import com.avail.descriptor.A_Variable;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.VariableDescriptor;
import com.avail.exceptions.VariableSetException;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2Operation;
import com.avail.interpreter.levelTwo.operand.L2ReadPointerOperand;
import com.avail.optimizer.L2Translator;
import com.avail.optimizer.RegisterSet;
import com.avail.optimizer.StackReifier;
import com.avail.optimizer.jvm.JVMTranslator;
import org.objectweb.asm.MethodVisitor;

import javax.annotation.Nullable;
import java.util.List;

import static com.avail.descriptor.VariableTypeDescriptor
	.mostGeneralVariableType;
import static com.avail.interpreter.levelTwo.L2OperandType.PC;
import static com.avail.interpreter.levelTwo.L2OperandType.READ_POINTER;
import static com.avail.optimizer.jvm.JVMCodeGenerationUtility.emitIntConstant;
import static com.avail.utility.Nulls.stripNull;
import static org.objectweb.asm.Opcodes.*;
import static org.objectweb.asm.Type.INT_TYPE;
import static org.objectweb.asm.Type.getInternalName;

/**
 * Assign a value to a {@linkplain VariableDescriptor variable} <em>without</em>
 * checking that it's of the correct type.
 */
public class L2_SET_VARIABLE_NO_CHECK
extends L2Operation
{
	/**
	 * Initialize the sole instance.
	 */
	public static final L2Operation instance =
		new L2_SET_VARIABLE_NO_CHECK().init(
			READ_POINTER.is("variable"),
			READ_POINTER.is("value to write"),
			PC.is("write succeeded"),
			PC.is("write failed"));

	@Override
	public @Nullable StackReifier step (
		final L2Instruction instruction,
		final Interpreter interpreter)
	{
		final L2ReadPointerOperand variableReg =
			instruction.readObjectRegisterAt(0);
		final L2ReadPointerOperand valueReg = instruction.readObjectRegisterAt(1);
		final int succeeded = instruction.pcOffsetAt(2);
		final int failed = instruction.pcOffsetAt(3);

		final AvailObject value = valueReg.in(interpreter);
		final A_Variable variable = variableReg.in(interpreter);
		try
		{
			variable.setValueNoCheck(value);
			// Jump to the success offset.
			interpreter.offset(succeeded);
		}
		catch (final VariableSetException e)
		{
			// Jump to the failure offset.
			interpreter.offset(failed);
		}
		return null;
	}

	@Override
	protected void propagateTypes (
		final L2Instruction instruction,
		final List<RegisterSet> registerSets,
		final L2Translator translator)
	{
		final L2ReadPointerOperand variableReg =
			instruction.readObjectRegisterAt(0);
		final L2ReadPointerOperand valueReg =
			instruction.readObjectRegisterAt(1);
//		final int succeeded = instruction.pcAt(2);
//		final int failed = instruction.pcAt(3);

		// The two register sets are clones, so only cross-check one of them.
		final RegisterSet registerSet = registerSets.get(0);
		assert registerSet.hasTypeAt(variableReg.register());
		final A_Type varType = registerSet.typeAt(variableReg.register());
		assert varType.isSubtypeOf(mostGeneralVariableType());
		assert registerSet.hasTypeAt(valueReg.register());
		final A_Type valueType = registerSet.typeAt(valueReg.register());
		assert valueType.isSubtypeOf(varType.writeType());
	}

	@Override
	public boolean hasSideEffect ()
	{
		return true;
	}

	@Override
	public boolean isVariableSet ()
	{
		return true;
	}

	@Override
	public void translateToJVM (
		final JVMTranslator translator,
		final MethodVisitor method,
		final L2Instruction instruction)
	{
//		final L2ReadPointerOperand variableReg =
//			instruction.readObjectRegisterAt(0);
//		final L2ReadPointerOperand valueReg =
//			instruction.readObjectRegisterAt(1);
		final int succeeded = instruction.pcOffsetAt(2);
		final int failed = instruction.pcOffsetAt(3);

		super.translateToJVM(translator, method, instruction);
		method.visitVarInsn(ALOAD, translator.interpreterLocal());
		method.visitFieldInsn(
			GETFIELD,
			getInternalName(Interpreter.class),
			"offset",
			INT_TYPE.getDescriptor());
		emitIntConstant(method, succeeded);
		method.visitJumpInsn(
			IF_ICMPNE,
			stripNull(translator.instructionLabels)[failed]);
		method.visitJumpInsn(
			GOTO,
			stripNull(translator.instructionLabels)[succeeded]);
	}
}
