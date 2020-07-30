/*
 * L2_UNREACHABLE_CODE.java
 * Copyright © 1993-2020, The Avail Foundation, LLC.
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
package com.avail.interpreter.levelTwo.operation

import com.avail.interpreter.levelTwo.L2Instruction
import com.avail.optimizer.L2Generator
import com.avail.optimizer.RegisterSet
import com.avail.optimizer.jvm.CheckedMethod
import com.avail.optimizer.jvm.JVMTranslator
import com.avail.optimizer.jvm.ReferencedInGeneratedCode
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

/**
 * This instruction should never be reached.  Stop the VM if it is.  We need the
 * instruction for dealing with labels that should never be jumped to, but still
 * need to be provided for symmetry reasons.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object  L2_UNREACHABLE_CODE : L2ControlFlowOperation()
{
	override fun propagateTypes(
		instruction: L2Instruction,
		registerSets: List<RegisterSet>,
		generator: L2Generator)
	{
		assert(registerSets.isEmpty())
	}

	// Don't remove it.
	override fun hasSideEffect(): Boolean = true

	/**
	 * `UnreachableCodeException` is thrown only if unreachable code is
	 * actually reached.
	 */
	class UnreachableCodeException : RuntimeException()

	override fun translateToJVM(
		translator: JVMTranslator,
		method: MethodVisitor,
		instruction: L2Instruction)
	{
		// :: throw throwUnreachableCodeException();
		throwUnreachableCodeExceptionMethod.generateCall(method)
		method.visitInsn(Opcodes.ATHROW)
	}

	/**
	 * Throw an [UnreachableCodeException], but pretend to return one to
	 * make JVM data flow analysis happy (and keep instruction count low in the
	 * generated code for `L2_UNREACHABLE_CODE`).
	 *
	 * @return
	 * Never returns, always throws `UnreachableCodeException`.
	 */
	@JvmStatic
	@ReferencedInGeneratedCode
	fun throwUnreachableCodeException(): UnreachableCodeException
	{
		throw UnreachableCodeException()
	}

	val throwUnreachableCodeExceptionMethod = CheckedMethod.staticMethod(
		L2_UNREACHABLE_CODE::class.java,
		::throwUnreachableCodeException.name,
		UnreachableCodeException::class.java)
}
