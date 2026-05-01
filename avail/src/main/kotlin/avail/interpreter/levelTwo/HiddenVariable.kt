/*
 * HiddenVariable.kt
 * Copyright © 1993-2024, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 *  * Neither the name of the copyright holder nor the names of the contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
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

package avail.interpreter.levelTwo

import avail.interpreter.primitive.Primitive
import avail.interpreter.primitive.Primitive.Flag

/**
 * A brief hierarchy of classes for sensibly parameterizing the
 * [ReadsHiddenVariable] and [WritesHiddenVariable] annotations on an
 * [L2Instruction] subclass.
 */
sealed class HiddenVariable
{
	/** How the current continuation field is affected. */
	@HiddenVariableShift(0)
	class CURRENT_CONTINUATION : HiddenVariable()

	/** How the current function field is affected. */
	@HiddenVariableShift(1)
	class CURRENT_FUNCTION : HiddenVariable()

	/** How the latest return value field is affected. */
	@HiddenVariableShift(2)
	class LATEST_RETURN_VALUE : HiddenVariable()

	/** How the current stack reifier field is affected. */
	@HiddenVariableShift(3)
	class STACK_REIFIER : HiddenVariable()

	/**
	 * How any other global variables are affected.  This includes things
	 * like the global exception reporter, the stringification function,
	 * observerless setup, etc.
	 *
	 * [Primitive]s are annotated with the [Flag.ReadsFromHiddenGlobalState]
	 * and [Flag.WritesToHiddenGlobalState] flags in their constructors to
	 * indicate that `GLOBAL_STATE` is affected.
	 */
	@HiddenVariableShift(4)
	class GLOBAL_STATE : HiddenVariable()
}
