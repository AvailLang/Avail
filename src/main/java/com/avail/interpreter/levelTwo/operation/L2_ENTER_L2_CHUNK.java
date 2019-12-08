/*
 * L2_ENTER_L2_CHUNK.java
 * Copyright © 1993-2019, The Avail Foundation, LLC.
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

import com.avail.interpreter.Interpreter;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2OperandType;
import com.avail.interpreter.levelTwo.L2Operation;
import com.avail.interpreter.levelTwo.L2Operation.HiddenVariable.CURRENT_CONTINUATION;
import com.avail.interpreter.levelTwo.ReadsHiddenVariable;
import com.avail.interpreter.levelTwo.WritesHiddenVariable;
import com.avail.interpreter.levelTwo.operand.L2IntImmediateOperand;
import com.avail.interpreter.levelTwo.register.L2Register.RegisterKind;
import com.avail.optimizer.jvm.JVMTranslator;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;

import javax.annotation.Nullable;
import java.util.EnumMap;
import java.util.List;
import java.util.Set;

import static com.avail.descriptor.AvailObject.registerDumpMethod;
import static com.avail.descriptor.ContinuationRegisterDumpDescriptor.extractLongAtMethod;
import static com.avail.descriptor.ContinuationRegisterDumpDescriptor.extractObjectAtMethod;
import static com.avail.interpreter.Interpreter.getReifiedContinuationMethod;
import static com.avail.interpreter.Interpreter.popContinuationMethod;
import static com.avail.interpreter.levelTwo.L2OperandType.COMMENT;
import static com.avail.interpreter.levelTwo.L2OperandType.INT_IMMEDIATE;
import static org.objectweb.asm.Opcodes.*;

/**
 * This marks the entry point into optimized (level two) code.  At entry, the
 * arguments are expected to be in the {@link Interpreter#argsBuffer}.  Set up
 * fresh registers for this chunk, but do not write to them yet.
 *
 * <p>This instruction also occurs at places that a reified continuation can be
 * re-entered, such as returning into it, restarting it, or continuing it after
 * an interrupt has been handled.</p>
 */
@ReadsHiddenVariable(
	CURRENT_CONTINUATION.class)
@WritesHiddenVariable(
	CURRENT_CONTINUATION.class)
public final class L2_ENTER_L2_CHUNK
extends L2Operation
{
	/**
	 * Construct an {@code L2_ENTER_L2_CHUNK}.
	 */
	private L2_ENTER_L2_CHUNK ()
	{
		super(
			INT_IMMEDIATE.is("entry point offset in default chunk"),
			COMMENT.is("chunk entry point name"));
	}

	/**
	 * Initialize the sole instance.
	 */
	public static final L2_ENTER_L2_CHUNK instance = new L2_ENTER_L2_CHUNK();

	@Override
	public boolean isEntryPoint (final L2Instruction instruction)
	{
		return true;
	}

	@Override
	public boolean hasSideEffect ()
	{
		return true;
	}

	@Override
	public void toString (
		final L2Instruction instruction,
		final Set<L2OperandType> desiredTypes,
		final StringBuilder builder)
	{
		assert this == instruction.operation();
//		final L2IntImmediateOperand offsetInDefaultChunk =
//			instruction.operand(0);
//		final L2CommentOperand comment = instruction.operand(1);

		renderPreamble(instruction, builder);
	}

	@Override
	public void translateToJVM (
		final JVMTranslator translator,
		final MethodVisitor method,
		final L2Instruction instruction)
	{
		final L2IntImmediateOperand offsetInDefaultChunk =
			instruction.operand(0);
//		final L2CommentOperand comment = instruction.operand(1);

		// :: if (!checkValidity()) {
		translator.loadInterpreter(method);
		translator.literal(method, offsetInDefaultChunk.value);
		Interpreter.checkValidityMethod.generateCall(method);
		final Label isValidLabel = new Label();
		method.visitJumpInsn(IFNE, isValidLabel);
		// ::    return null;
		method.visitInsn(ACONST_NULL);
		method.visitInsn(ARETURN);
		// :: }
		method.visitLabel(isValidLabel);

		// Extract register values that were saved in a register dump by the
		// corresponding L2_SAVE_ALL_AND_PC_TO_INT instruction, which nicely set
		// up for us the lists of registers that were saved.  The interpreter
		// should have extracted the registerDump for us already.
		final @Nullable EnumMap<RegisterKind, List<Integer>>
			localNumberLists =
				translator.liveLocalNumbersByKindPerEntryPoint.get(instruction);
		if (localNumberLists != null)
		{
			final List<Integer> boxedList =
				localNumberLists.get(RegisterKind.BOXED);
			final List<Integer> intsList =
				localNumberLists.get(RegisterKind.INTEGER);
			final List<Integer> floatsList =
				localNumberLists.get(RegisterKind.FLOAT);
			final int boxedCount = boxedList.size();
			final int intsCount = intsList.size();
			final int floatsCount = floatsList.size();

			int countdown = boxedCount + intsCount + floatsCount;
			if (countdown > 0)
			{
				// Extract the register dump from the current continuation.
				translator.loadInterpreter(method);
				getReifiedContinuationMethod.generateCall(method);
				registerDumpMethod.generateCall(method);
				// Stack now has the registerDump.
				for (int i = 0; i < boxedCount; i++)
				{
					if (--countdown > 0)
					{
						method.visitInsn(DUP);
						// Stack has two registerDumps if needed.
					}
					translator.intConstant(method, i + 1);  //one-based
					extractObjectAtMethod.generateCall(method);
					method.visitVarInsn(
						RegisterKind.BOXED.storeInstruction,
						boxedList.get(i));
				}
				int i;
				for (i = 0; i < intsCount; i++)
				{
					if (--countdown > 0)
					{
						method.visitInsn(DUP);
						// Stack has two registerDumps if needed.
					}
					translator.intConstant(method, i + 1);  //one-based
					extractLongAtMethod.generateCall(method);
					method.visitInsn(L2I);
					method.visitVarInsn(
						RegisterKind.INTEGER.storeInstruction,
						intsList.get(i));
				}
				for (int j = 0; j < floatsCount; j++, i++)
				{
					if (--countdown > 0)
					{
						method.visitInsn(DUP);
						// Stack has two registerDumps if needed.
					}
					translator.intConstant(method, i + 1);  //one-based
					extractLongAtMethod.generateCall(method);
					method.visitInsn(L2D);
					method.visitVarInsn(
						RegisterKind.FLOAT.storeInstruction,
						floatsList.get(j));
				}
				assert countdown == 0;
				// The last copy of registerDumps was popped.
			}

			// :: interpreter.popContinuation();
			translator.loadInterpreter(method);
			popContinuationMethod.generateCall(method);
		}
	}
}
