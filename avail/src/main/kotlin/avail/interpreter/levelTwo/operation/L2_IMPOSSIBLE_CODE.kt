/*
 * L2_IMPOSSIBLE_CODE.kt
 * Copyright © 1993-2022, The Avail Foundation, LLC.
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
package avail.interpreter.levelTwo.operation

import avail.optimizer.jvm.CheckedMethod
import avail.optimizer.jvm.CheckedMethod.Companion.staticMethod
import avail.optimizer.jvm.JVMTranslator
import avail.optimizer.jvm.ReferencedInGeneratedCode
import org.objectweb.asm.Opcodes

/**
 * This instruction is inserted as a placeholder when an impossible condition is
 * detected while preparing an instruction to be added to the graph.  It can
 * be a result of intersecting an instruction's read operand's intrinsic
 * restriction with the restriction for the read's semantic value as found in
 * the current manifest.  This can be a consequence of latent chains of
 * restrictions that aren't fully present in the manifest, but appear further
 * down the graph.
 *
 * Any block that leads only to [L2_IMPOSSIBLE_CODE] can be replaced with a
 * block that holds just the [L2_IMPOSSIBLE_CODE].  A conditional two-way branch
 * to such a block along one of its edges can be replaced with an unconditional
 * jump to the other branch.  Applying these two reductions repeatedly will
 * cause all [L2_IMPOSSIBLE_CODE] instructions to be removed from the graph.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
class L2_IMPOSSIBLE_CODE
constructor(
): L2ControlFlowInstruction()
{
	override val isCold get() = true

	override val altersControlFlow: Boolean get() = true

	override val hasSideEffect get() = true

	/**
	 * [ImpossibleCodeException] is thrown only if code with an impossible
	 * state of registers is actually reached.
	 */
	class ImpossibleCodeException : RuntimeException(
		"L2_IMPOSSIBLE_CODE instructions should have been eliminated")

	override fun JVMTranslator.translateToJVM()
	{
		// :: throw throwImpossibleCodeExceptionMethod();
		generateCall(throwImpossibleCodeExceptionMethod)
		method.visitInsn(Opcodes.ATHROW)
	}

	companion object
	{
		/**
		 * Throw an [ImpossibleCodeException], but pretend to return one to
		 * make JVM data flow analysis happy (and keep instruction count low in
		 * the generated code for `L2_UNREACHABLE_CODE`).
		 *
		 * @return
		 *   Never actually returns, always throws `ImpossibleCodeException`.
		 */
		@ReferencedInGeneratedCode
		@JvmStatic
		fun throwImpossibleCodeException(): ImpossibleCodeException
		{
			throw ImpossibleCodeException()
		}

		/**
		 * The [CheckedMethod] for [throwImpossibleCodeException].
		 */
		val throwImpossibleCodeExceptionMethod = staticMethod(
			L2_IMPOSSIBLE_CODE::class.java,
			::throwImpossibleCodeException.name,
			ImpossibleCodeException::class.java)
	}
}
