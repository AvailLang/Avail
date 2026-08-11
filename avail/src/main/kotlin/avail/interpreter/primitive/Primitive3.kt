/*
 * Primitive3.kt
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

package avail.interpreter.primitive

import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.AvailObject
import avail.interpreter.execution.Interpreter
import avail.optimizer.L2Generator
import avail.optimizer.StackReifier
import avail.optimizer.jvm.CheckedMethod.Companion.instanceMethod
import avail.optimizer.jvm.ReferencedInGeneratedCode

/**
 * A [Primitive] taking exactly three arguments.
 *
 * @param flags
 *   The flags that describe how the [Interpreter] and [L2Generator] should deal
 *   with this primitive.
 */
abstract class Primitive3
constructor(
	vararg flags: Flag
): Primitive(3, *flags)
{
	/**
	 * Attempt to run a primitive taking three arguments, answering either an
	 * AvailObject if successful, or null if the primitive could not complete
	 * for some reason.  If the primitive reifies, the
	 * [Interpreter.currentReifier] will capture the [StackReifier].  If the
	 * primitive fails, its failure code will be stored in the
	 * [Interpreter.latestResult].
	 *
	 * @receiver
	 *   The [Interpreter] performing the primitive attempt.
	 * @param arg1
	 *   The first argument to the primitive.
	 * @param arg2
	 *   The second argument to the primitive.
	 * @param arg3
	 *   The third argument to the primitive.
	 * @return
	 *   The result of the primitive attempt, or null if it failed or reified.
	 */
	@ReferencedInGeneratedCode
	abstract fun Interpreter.attempt3(
		arg1: AvailObject,
		arg2: AvailObject,
		arg3: AvailObject
	): A_BasicObject?

	final override fun attempt(interpreter: Interpreter): A_BasicObject?
	{
		val args = interpreter.argsBuffer
		assert(args.size == 3)
		return interpreter.attempt3(args[0], args[1], args[2])
	}

	companion object
	{
		/** The method [attempt3]. */
		val attempt3Method = instanceMethod(
			Primitive3::class.java,
			"attempt3",
			A_BasicObject::class.java,
			Interpreter::class.java,
			AvailObject::class.java,
			AvailObject::class.java,
			AvailObject::class.java)
	}
}
