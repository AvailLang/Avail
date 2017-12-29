/**
 * L2_RETURN.java
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

import com.avail.descriptor.A_BasicObject;
import com.avail.descriptor.A_Function;
import com.avail.descriptor.AvailObject;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.levelTwo.L2Chunk;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2Operation;
import com.avail.optimizer.L2Translator;
import com.avail.optimizer.RegisterSet;
import com.avail.optimizer.StackReifier;
import com.avail.optimizer.jvm.JVMTranslator;
import com.avail.utility.Nulls;
import com.avail.utility.evaluation.Transformer1NotNullArg;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;

import java.util.List;

import static com.avail.interpreter.levelTwo.L2OperandType.READ_INT;
import static com.avail.interpreter.levelTwo.L2OperandType.READ_POINTER;
import static com.avail.optimizer.jvm.JVMCodeGenerationUtility.emitIntConstant;
import static com.avail.utility.Nulls.stripNull;
import static org.objectweb.asm.Opcodes.*;
import static org.objectweb.asm.Type.*;

/**
 * Return from the current {@link L2Chunk} with the given return value.  The
 * value to return will be stored in {@link Interpreter#latestResult(
 * A_BasicObject)}, so the caller will need to look there.
 */
public class L2_RETURN extends L2Operation
{
	/**
	 * Initialize the sole instance.
	 */
	public static final L2Operation instance =
		new L2_RETURN().init(
			READ_POINTER.is("return value"),
			READ_INT.is("skip return check"));

	@Override
	public Transformer1NotNullArg<Interpreter, StackReifier> actionFor (
		final L2Instruction instruction)
	{
		// Return to the calling continuation with the given value.
		final int valueRegIndex =
			instruction.readObjectRegisterAt(0).finalIndex();
		final int skipCheckIndex =
			instruction.readIntRegisterAt(1).finalIndex();

		return interpreter ->
		{
			final AvailObject value = interpreter.pointerAt(valueRegIndex);
			interpreter.latestResult(value);
			interpreter.skipReturnCheck =
				interpreter.integerAt(skipCheckIndex) != 0;
			interpreter.returnNow = true;
			interpreter.returningFunction = stripNull(interpreter.function);
			return null;
		};
	}

	@Override
	protected void propagateTypes (
		final L2Instruction instruction,
		final List<RegisterSet> registerSets,
		final L2Translator translator)
	{
		// A return instruction doesn't mention where it might end up.
		assert registerSets.size() == 0;
	}

	@Override
	public boolean hasSideEffect ()
	{
		// Never remove this.
		return true;
	}

	@Override
	public boolean reachesNextInstruction ()
	{
		return false;
	}

	@Override
	public void translateToJVM (
		final JVMTranslator translator,
		final MethodVisitor method,
		final L2Instruction instruction)
	{
		final int valueRegIndex =
			instruction.readObjectRegisterAt(0).finalIndex();
		final int skipCheckIndex =
			instruction.readIntRegisterAt(1).finalIndex();

		// interpreter.latestResult(interpreter.pointerAt(valueRegIndex))
		method.visitVarInsn(ALOAD, translator.interpreterLocal());
		method.visitInsn(DUP);
		emitIntConstant(method, valueRegIndex);
		method.visitMethodInsn(
			INVOKEVIRTUAL,
			getInternalName(Interpreter.class),
			"pointerAt",
			getMethodDescriptor(getType(AvailObject.class), INT_TYPE),
			false);
		method.visitMethodInsn(
			INVOKEVIRTUAL,
			getInternalName(Interpreter.class),
			"latestResult",
			getMethodDescriptor(VOID_TYPE, getType(A_BasicObject.class)),
			false);
		// interpreter.skipReturnCheck =
		//    interpreter.integerAt(skipCheckIndex) != 0
		method.visitVarInsn(ALOAD, translator.interpreterLocal());
		method.visitInsn(DUP);
		emitIntConstant(method, skipCheckIndex);
		method.visitMethodInsn(
			INVOKEVIRTUAL,
			getInternalName(Interpreter.class),
			"integerAt",
			getMethodDescriptor(INT_TYPE, INT_TYPE),
			false);
		final Label skipReturnCheckTrueLabel = new Label();
		method.visitJumpInsn(IFNE, skipReturnCheckTrueLabel);
		emitIntConstant(method, 0);
		method.visitFieldInsn(
			PUTFIELD,
			getInternalName(Interpreter.class),
			"skipReturnCheck",
			BOOLEAN_TYPE.getDescriptor());
		final Label setReturnNowLabel = new Label();
		method.visitJumpInsn(GOTO, setReturnNowLabel);
		method.visitLabel(skipReturnCheckTrueLabel);
		emitIntConstant(method, 1);
		method.visitFieldInsn(
			PUTFIELD,
			getInternalName(Interpreter.class),
			"skipReturnCheck",
			BOOLEAN_TYPE.getDescriptor());
		method.visitLabel(setReturnNowLabel);
		// interpreter.returnNow = true
		method.visitVarInsn(ALOAD, translator.interpreterLocal());
		emitIntConstant(method, 1);
		method.visitFieldInsn(
			PUTFIELD,
			getInternalName(Interpreter.class),
			"returnNow",
			BOOLEAN_TYPE.getDescriptor());
		// interpreter.returningFunction = stripNull(interpreter.function)
		method.visitVarInsn(ALOAD, translator.interpreterLocal());
		method.visitInsn(DUP);
		method.visitFieldInsn(
			GETFIELD,
			getInternalName(Interpreter.class),
			"function",
			getDescriptor(A_Function.class));
		method.visitMethodInsn(
			INVOKESTATIC,
			getInternalName(Nulls.class),
			"stripNull",
			getMethodDescriptor(getType(Object.class), getType(Object.class)),
			false);
		method.visitFieldInsn(
			PUTFIELD,
			getInternalName(Interpreter.class),
			"returningFunction",
			getDescriptor(A_Function.class));
		method.visitInsn(ACONST_NULL);
		method.visitInsn(ARETURN);
	}
}
