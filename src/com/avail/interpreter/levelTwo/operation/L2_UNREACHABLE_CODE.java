/*
 * L2_UNREACHABLE_CODE.java
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

import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2Operation;
import com.avail.optimizer.L2Translator;
import com.avail.optimizer.RegisterSet;
import com.avail.optimizer.jvm.JVMTranslator;
import com.avail.optimizer.jvm.ReferencedInGeneratedCode;
import org.objectweb.asm.MethodVisitor;

import javax.annotation.Nullable;
import java.util.List;

import static org.objectweb.asm.Opcodes.ATHROW;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;
import static org.objectweb.asm.Type.*;

/**
 * This instruction should never be reached.  Stop the VM if it is.  We need the
 * instruction for dealing with labels that should never be jumped to, but still
 * need to be provided for symmetry reasons.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public class L2_UNREACHABLE_CODE
extends L2Operation
{
	/**
	 * Initialize the sole instance.
	 */
	public static final L2Operation instance = new L2_UNREACHABLE_CODE().init();

	@Override
	protected void propagateTypes (
		final L2Instruction instruction,
		final List<RegisterSet> registerSets,
		final L2Translator translator)
	{
		assert registerSets.isEmpty();
	}

	@Override
	public boolean hasSideEffect ()
	{
		// Don't remove it.
		return true;
	}

	@Override
	public boolean reachesNextInstruction ()
	{
		return false;
	}

	/**
	 * {@code UnreachableCodeException} is thrown only if unreachable code is
	 * actually reached.
	 */
	public static class UnreachableCodeException
	extends RuntimeException
	{
		// No implementation required.
	}

	/**
	 * Throw an {@link UnreachableCodeException}, but pretend to return one to
	 * make JVM data flow analysis happy (and keep instruction count low in the
	 * generated code for {@code L2_UNREACHABLE_CODE}).
	 *
	 * @return Never returns, always throws {@code UnreachableCodeException}.
	 */
	@SuppressWarnings("unused")
	@ReferencedInGeneratedCode
	public static @Nullable UnreachableCodeException
	throwUnreachableCodeException ()
	{
		throw new UnreachableCodeException();
	}

	@Override
	public void translateToJVM (
		final JVMTranslator translator,
		final MethodVisitor method,
		final L2Instruction instruction)
	{
		// :: throw throwUnreachableCodeException();
		method.visitMethodInsn(
			INVOKESTATIC,
			getInternalName(L2_UNREACHABLE_CODE.class),
			"throwUnreachableCodeException",
			getMethodDescriptor(getType(UnreachableCodeException.class)),
			false);
		method.visitInsn(ATHROW);
	}
}
