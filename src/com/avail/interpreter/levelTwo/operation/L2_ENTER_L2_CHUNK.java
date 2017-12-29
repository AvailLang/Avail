/**
 * L2_ENTER_L2_CHUNK.java
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

import com.avail.descriptor.AvailObject;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.levelTwo.L2Chunk;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2Operation;
import com.avail.optimizer.ExecutableChunk;
import com.avail.optimizer.StackReifier;
import com.avail.optimizer.jvm.JVMTranslator;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;

import javax.annotation.Nullable;

import static com.avail.interpreter.levelTwo.L2OperandType.IMMEDIATE;
import static com.avail.utility.Nulls.stripNull;
import static org.objectweb.asm.Opcodes.*;
import static org.objectweb.asm.Type.*;

/**
 * This marks the entry point into optimized (level two) code.  At entry, the
 * arguments are expected to be in the {@link Interpreter#argsBuffer}.  Set up
 * fresh registers for this chunk, but do not write to them yet.
 *
 * <p>This instruction also occurs at places that a reified continuation can be
 * re-entered, such as returning into it, restarting it, or continuing it after
 * an interrupt has been handled.</p>
 */
public class L2_ENTER_L2_CHUNK extends L2Operation
{
	/**
	 * Initialize the sole instance.
	 */
	public static final L2Operation instance =
		new L2_ENTER_L2_CHUNK().init(
			IMMEDIATE.is("entry point offset in default chunk"));

	@Override
	public @Nullable StackReifier step (
		final L2Instruction instruction,
		final Interpreter interpreter)
	{
		final L2Chunk chunk = stripNull(interpreter.chunk);
		if (chunk.isValid())
		{
			// Allocate the registers.
			interpreter.pointers = new AvailObject[chunk.numObjects()];
			interpreter.integers = new int[chunk.numIntegers()];
		}
		else
		{
			// Jump to the corresponding entry point of the default chunk
			// instead.
			interpreter.chunk = L2Chunk.unoptimizedChunk;
			interpreter.offset = instruction.immediateAt(0);
			// Safety.
			interpreter.pointers = Interpreter.emptyPointersArray;
			interpreter.integers = Interpreter.emptyIntArray;
		}
		return null;
	}

	@Override
	public boolean hasSideEffect ()
	{
		return true;
	}

	@Override
	public void translateToJVM (
		final JVMTranslator translator,
		final MethodVisitor method,
		final L2Instruction instruction)
	{
		if (JVMTranslator.debugRecordL2InstructionTimings)
		{
			translator.generateRecordTimingsPrologue(method, instruction);
		}
		translator.generateRunAction(method, instruction);
		method.visitInsn(POP);
		if (JVMTranslator.debugRecordL2InstructionTimings)
		{
			translator.generateRecordTimingsEpilogue(method, instruction);
		}
		// Check to see if the chunk has become the unoptimized chunk. If so,
		// then return into the interpreter. The interpreter should loop
		// immediately, calling into the unoptimized chunk.
		method.visitVarInsn(ALOAD, translator.interpreterLocal());
		method.visitFieldInsn(
			GETFIELD,
			getInternalName(Interpreter.class),
			"chunk",
			getDescriptor(L2Chunk.class));
		method.visitFieldInsn(
			GETSTATIC,
			getInternalName(L2Chunk.class),
			"unoptimizedChunk",
			getDescriptor(L2Chunk.class));
		final Label continueLabel = new Label();
		method.visitJumpInsn(IF_ACMPNE, continueLabel);
		method.visitInsn(ACONST_NULL);
		method.visitInsn(ARETURN);
		method.visitLabel(continueLabel);
	}
}
