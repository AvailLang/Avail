/*
 * L2_TRY_OPTIONAL_PRIMITIVE.java
 * Copyright © 1993-2018, The Avail Foundation, LLC.
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

import com.avail.descriptor.A_Function;
import com.avail.descriptor.A_RawFunction;
import com.avail.descriptor.AvailObject;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;
import com.avail.interpreter.Primitive.Flag;
import com.avail.interpreter.levelTwo.L2Chunk;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2Operation;
import com.avail.optimizer.L2Generator;
import com.avail.optimizer.RegisterSet;
import com.avail.optimizer.StackReifier;
import com.avail.optimizer.jvm.JVMTranslator;
import com.avail.optimizer.jvm.ReferencedInGeneratedCode;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;

import javax.annotation.Nullable;
import java.util.List;

import static com.avail.interpreter.Primitive.Flag.CanInline;
import static com.avail.interpreter.levelTwo.operation.L2_TRY_PRIMITIVE.attemptInlinePrimitive;
import static com.avail.interpreter.levelTwo.operation.L2_TRY_PRIMITIVE.attemptNonInlinePrimitive;
import static org.objectweb.asm.Opcodes.*;
import static org.objectweb.asm.Type.*;

/**
 * Expect the {@link AvailObject} (pointers) array and int array to still
 * reflect the caller.  Expect {@link Interpreter#argsBuffer} to have been
 * loaded with the arguments to this possible primitive function, and expect the
 * code/function/chunk to have been updated for this primitive function.
 * Try to execute a potential primitive, setting the {@link
 * Interpreter#returnNow} flag and {@link Interpreter#latestResult()
 * latestResult} if successful.  The caller always has the responsibility of
 * checking the return value, if applicable at that call site.  Used only by the
 * {@linkplain L2Chunk#unoptimizedChunk unoptimized chunk}.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class L2_TRY_OPTIONAL_PRIMITIVE
extends L2Operation
{
	/**
	 * Construct an {@code L2_TRY_OPTIONAL_PRIMITIVE}.
	 */
	private L2_TRY_OPTIONAL_PRIMITIVE ()
	{
		// Prevent accidental construction due to code cloning.
	}

	/**
	 * Initialize the sole instance.
	 */
	public static final L2_TRY_OPTIONAL_PRIMITIVE instance =
		new L2_TRY_OPTIONAL_PRIMITIVE();

	@Override
	public boolean isEntryPoint (final L2Instruction instruction)
	{
		return true;
	}

	@Override
	protected void propagateTypes (
		final L2Instruction instruction,
		final List<RegisterSet> registerSets,
		final L2Generator translator)
	{
		// This instruction should only be used in the L1 interpreter loop.
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean hasSideEffect ()
	{
		// It could fail and jump.
		return true;
	}

	/**
	 * Attempt the {@linkplain Primitive primitive}, dynamically checking
	 * whether it is an {@linkplain Flag#CanInline inlineable} primitive.
	 *
	 * @param interpreter
	 *        The {@link Interpreter}.
	 * @param function
	 *        The {@link A_Function}.
	 * @param primitive
	 *        The {@link Primitive}.
	 * @return The {@link StackReifier}, if any.
	 */
	@SuppressWarnings("unused")
	@ReferencedInGeneratedCode
	public static @Nullable StackReifier attemptPrimitive (
		final Interpreter interpreter,
		final A_Function function,
		final Primitive primitive)
	{
		return primitive.hasFlag(CanInline)
			? attemptInlinePrimitive(interpreter, function, primitive)
			: attemptNonInlinePrimitive(interpreter, function, primitive);
	}

	@Override
	public void translateToJVM (
		final JVMTranslator translator,
		final MethodVisitor method,
		final L2Instruction instruction)
	{
		// :: if (interpreter.function.code().primitive() == null)
		// ::    goto noPrimitive;
		translator.loadInterpreter(method);
		method.visitInsn(DUP);
		method.visitFieldInsn(
			GETFIELD,
			getInternalName(Interpreter.class),
			"function",
			getDescriptor(A_Function.class));
		method.visitInsn(DUP);
		method.visitMethodInsn(
			INVOKEINTERFACE,
			getInternalName(A_Function.class),
			"code",
			getMethodDescriptor(getType(A_RawFunction.class)),
			true);
		method.visitMethodInsn(
			INVOKEINTERFACE,
			getInternalName(A_RawFunction.class),
			"primitive",
			getMethodDescriptor(getType(Primitive.class)),
			true);
		method.visitInsn(DUP);
		final Label noPrimitive = new Label();
		method.visitJumpInsn(IFNULL, noPrimitive);
		// :: return L2_TRY_OPTIONAL_PRIMITIVE.attemptPrimitive(
		// ::    interpreter, function, primitive);
		method.visitMethodInsn(
			INVOKESTATIC,
			getInternalName(L2_TRY_OPTIONAL_PRIMITIVE.class),
			"attemptPrimitive",
			getMethodDescriptor(
				getType(StackReifier.class),
				getType(Interpreter.class),
				getType(A_Function.class),
				getType(Primitive.class)),
			false);
		method.visitInsn(ARETURN);
		method.visitLabel(noPrimitive);
		// Pop the three Category-1 arguments that were waiting for
		// attemptPrimitive().
		method.visitInsn(POP2);
		method.visitInsn(POP);
	}
}
