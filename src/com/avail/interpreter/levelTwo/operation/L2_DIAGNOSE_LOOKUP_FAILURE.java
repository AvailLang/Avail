/*
 * L2_DIAGNOSE_LOOKUP_FAILURE.java
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

import com.avail.descriptor.A_Definition;
import com.avail.descriptor.A_Number;
import com.avail.descriptor.A_Set;
import com.avail.descriptor.AvailObject;
import com.avail.exceptions.AvailErrorCode;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2Operation;
import com.avail.interpreter.levelTwo.operand.L2WritePointerOperand;
import com.avail.interpreter.levelTwo.register.L2ObjectRegister;
import com.avail.optimizer.L2Translator;
import com.avail.optimizer.RegisterSet;
import com.avail.optimizer.jvm.JVMTranslator;
import com.avail.utility.IteratorNotNull;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;

import static com.avail.descriptor.AbstractEnumerationTypeDescriptor.enumerationWith;
import static com.avail.descriptor.SetDescriptor.set;
import static com.avail.exceptions.AvailErrorCode.*;
import static com.avail.interpreter.levelTwo.L2OperandType.CONSTANT;
import static com.avail.interpreter.levelTwo.L2OperandType.WRITE_POINTER;
import static org.objectweb.asm.Opcodes.*;
import static org.objectweb.asm.Type.*;

/**
 * A method lookup failed. Write the appropriate error code into the supplied
 * {@linkplain L2ObjectRegister object register}.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
@Deprecated
public class L2_DIAGNOSE_LOOKUP_FAILURE
extends L2Operation
{
	/**
	 * Initialize the sole instance.
	 */
	public static final L2Operation instance =
		new L2_DIAGNOSE_LOOKUP_FAILURE().init(
			CONSTANT.is("matching definitions"),
			WRITE_POINTER.is("error code"));

	@Override
	protected void propagateTypes (
		@NotNull final L2Instruction instruction,
		@NotNull final RegisterSet registerSet,
		final L2Translator translator)
	{
//		final A_Set definitions = instruction.constantAt(0);
		final L2WritePointerOperand errorCodeReg =
			instruction.writeObjectRegisterAt(1);
		registerSet.typeAtPut(
			errorCodeReg.register(),
			enumerationWith(
				set(
					E_NO_METHOD,
					E_NO_METHOD_DEFINITION,
					E_AMBIGUOUS_METHOD_DEFINITION,
					E_FORWARD_METHOD_DEFINITION,
					E_ABSTRACT_METHOD_DEFINITION)),
			instruction);
	}

	@Override
	public void translateToJVM (
		final JVMTranslator translator,
		final MethodVisitor method,
		final L2Instruction instruction)
	{
		final A_Set definitions = instruction.constantAt(0);
		final L2ObjectRegister errorCodeReg =
			instruction.writeObjectRegisterAt(1).register();

		final Label diagnosed = new Label();
		// :: if (definitions.setSize() == 0) {
		// ::    error = E_NO_METHOD_DEFINITION;
		// ::    goto diagnosed;
		// :: }
		final Label setSizeNotZero = new Label();
		translator.literal(method, definitions);
		method.visitMethodInsn(
			INVOKEINTERFACE,
			getInternalName(A_Set.class),
			"setSize",
			getMethodDescriptor(INT_TYPE),
			true);
		method.visitJumpInsn(IFNE, setSizeNotZero);
		method.visitFieldInsn(
			GETSTATIC,
			getInternalName(AvailErrorCode.class),
			"E_NO_METHOD_DEFINITION",
			getDescriptor(AvailErrorCode.class));
		method.visitJumpInsn(GOTO, diagnosed);
		method.visitLabel(setSizeNotZero);

		// :: if (definitions.setSize() > 1) {
		// ::    error = E_AMBIGUOUS_METHOD_DEFINITION;
		// ::    goto diagnosed;
		// :: }
		final Label setSizeIsOne = new Label();
		translator.literal(method, definitions);
		method.visitMethodInsn(
			INVOKEINTERFACE,
			getInternalName(A_Set.class),
			"setSize",
			getMethodDescriptor(INT_TYPE),
			true);
		translator.intConstant(method,1);
		method.visitJumpInsn(IF_ICMPLE, setSizeIsOne);
		method.visitFieldInsn(
			GETSTATIC,
			getInternalName(AvailErrorCode.class),
			"E_AMBIGUOUS_METHOD_DEFINITION",
			getDescriptor(AvailErrorCode.class));
		method.visitJumpInsn(GOTO, diagnosed);
		method.visitLabel(setSizeIsOne);

		// :: definition = definitions.iterator().next();
		translator.literal(method, definitions);
		method.visitMethodInsn(
			INVOKEVIRTUAL,
			getInternalName(AvailObject.class),
			"iterator",
			getMethodDescriptor(getType(IteratorNotNull.class)),
			false);
		method.visitMethodInsn(
			INVOKEINTERFACE,
			getInternalName(IteratorNotNull.class),
			"next",
			getMethodDescriptor(getType(Object.class)),
			true);
		method.visitTypeInsn(CHECKCAST, getInternalName(A_Definition.class));
		final Label startDefinition = new Label();
		final int definition = translator.nextLocal(
			getType(A_Definition.class));
		method.visitLabel(startDefinition);
		method.visitVarInsn(ASTORE, definition);

		// :: if (definition.isAbstractDefinition()) {
		// ::    error = E_ABSTRACT_METHOD_DEFINITION;
		// ::    goto diagnosed;
		// :: }
		final Label notAbstract = new Label();
		method.visitVarInsn(ALOAD, definition);
		method.visitMethodInsn(
			INVOKEINTERFACE,
			getInternalName(A_Definition.class),
			"isAbstractDefinition",
			getMethodDescriptor(BOOLEAN_TYPE),
			true);
		method.visitJumpInsn(IFEQ, notAbstract);
		method.visitFieldInsn(
			GETSTATIC,
			getInternalName(AvailErrorCode.class),
			"E_ABSTRACT_METHOD_DEFINITION",
			getDescriptor(AvailErrorCode.class));
		method.visitJumpInsn(GOTO, diagnosed);
		method.visitLabel(notAbstract);

		// :: if (definition.isForwardDefinition()) {
		// ::    error = E_FORWARD_METHOD_DEFINITION;
		// ::    goto diagnosed;
		// :: }
		final Label notForward = new Label();
		method.visitVarInsn(ALOAD, definition);
		method.visitMethodInsn(
			INVOKEINTERFACE,
			getInternalName(A_Definition.class),
			"isForwardDefinition",
			getMethodDescriptor(BOOLEAN_TYPE),
			true);
		method.visitJumpInsn(IFEQ, notForward);
		method.visitFieldInsn(
			GETSTATIC,
			getInternalName(AvailErrorCode.class),
			"E_FORWARD_METHOD_DEFINITION",
			getDescriptor(AvailErrorCode.class));
		method.visitJumpInsn(GOTO, diagnosed);
		method.visitLabel(notForward);

		// :: throw new RuntimeException("unknown lookup failure");
		method.visitTypeInsn(NEW, getInternalName(RuntimeException.class));
		method.visitInsn(DUP);
		method.visitLdcInsn("unknown lookup failure");
		method.visitMethodInsn(
			INVOKESPECIAL,
			getInternalName(RuntimeException.class),
			"<init>",
			getMethodDescriptor(VOID_TYPE, getType(String.class)),
			false);
		method.visitInsn(ATHROW);

		// :: errorCode = error.numericCode();
		method.visitLabel(diagnosed);
		method.visitLocalVariable(
			"definition",
			getDescriptor(A_Definition.class),
			null,
			startDefinition,
			diagnosed,
			definition);
		method.visitMethodInsn(
			INVOKEVIRTUAL,
			getInternalName(AvailErrorCode.class),
			"numericCode",
			getMethodDescriptor(getType(A_Number.class)),
			false);
		// N *
		translator.store(method, errorCodeReg);
	}
}
