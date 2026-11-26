/*
 * L2CallSite.kt
 * Copyright © 1993-2026, The Avail Foundation, LLC.
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
package avail.optimizer

import avail.descriptor.representation.A_BasicObject
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.primitive.Primitive

/**
 * A call of a known function whose invocation is being generated, as seen by
 * a [Primitive] that generates code in place of the call.  The primitive
 * emits its code on the [generator], then ends every path that completes the
 * call either by handing the call's answer to [useAnswer], or by generating
 * the invocation of some other function whose answer is the call's, through
 * [generateGeneralFunctionInvocation].
 *
 * Where the call's answer goes, whether it must still be checked against the
 * type the call site expects, and where a call reifies are the call site's
 * business, so a primitive generates the same code whatever kind of call site
 * it is in.
 */
interface L2CallSite
{
	/** The generator on which to emit the call's code. */
	val generator: L2GeneratorInterface

	/**
	 * Record that the call has produced its answer in the given register, and
	 * end the current path.  The answer satisfies the VM's guarantees for the
	 * call, but may not yet satisfy the type the call site expects, which the
	 * call site checks if necessary.
	 *
	 * @param answer
	 *   The register holding the call's answer.
	 */
	fun useAnswer(answer: L2ReadBoxedOperand)

	/**
	 * Record that the call produces the given constant, and end the current
	 * path, if it is reachable.
	 *
	 * @param constantAnswer
	 *   The call's answer.
	 */
	fun useConstantAnswer(constantAnswer: A_BasicObject)
	{
		if (!generator.currentlyReachable()) return
		useAnswer(generator.boxedConstant(constantAnswer))
	}

	/**
	 * Generate the invocation of the given function with the given
	 * arguments, whose answer is the call's answer, and end the current path.
	 *
	 * @param functionToCall
	 *   The register holding the function to invoke.
	 * @param tryToGenerateSpecialPrimitiveInvocation
	 *   Whether the function's primitive, if it has one, may generate code in
	 *   place of the invocation.  A primitive that falls back to invoking its
	 *   own function passes `false`, so that it is not asked again.
	 * @param arguments
	 *   The registers supplying the arguments.
	 * @param willAlwaysFailPrimitive
	 *   Whether the function's primitive is known to fail, so that its
	 *   fallback code runs.
	 */
	fun generateGeneralFunctionInvocation(
		functionToCall: L2ReadBoxedOperand,
		tryToGenerateSpecialPrimitiveInvocation: Boolean,
		arguments: List<L2ReadBoxedOperand>,
		willAlwaysFailPrimitive: Boolean = false)

	/**
	 * Generate a restart of the frame making the call, with the given
	 * arguments, which ends the current path.
	 *
	 * @param restartArguments
	 *   The registers supplying the arguments with which to restart it.
	 */
	fun generateRestartContinuation(
		restartArguments: List<L2ReadBoxedOperand>)
}
