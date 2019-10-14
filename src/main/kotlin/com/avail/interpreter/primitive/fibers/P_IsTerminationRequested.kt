/*
 * P_IsTerminationRequested.java
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
package com.avail.interpreter.primitive.fibers

import com.avail.descriptor.A_Type
import com.avail.descriptor.AtomDescriptor.objectFromBoolean
import com.avail.descriptor.EnumerationTypeDescriptor.booleanType
import com.avail.descriptor.FiberDescriptor
import com.avail.descriptor.FiberDescriptor.InterruptRequestFlag.TERMINATION_REQUESTED
import com.avail.descriptor.FunctionTypeDescriptor.functionType
import com.avail.descriptor.TupleDescriptor.emptyTuple
import com.avail.interpreter.Interpreter
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Flag.*

/**
 * **Primitive:** Has termination been requested for the
 * current [fiber][FiberDescriptor]? Answer the current value of the
 * appropriate interrupt flag and simultaneously clear it.
 */
object P_IsTerminationRequested : Primitive(0, CannotFail, CanInline, HasSideEffect)
{

	override fun attempt(
		interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(0)
		return interpreter.primitiveSuccess(
			objectFromBoolean(
				interpreter.fiber().getAndClearInterruptRequestFlag(
					TERMINATION_REQUESTED)))
	}

	override fun privateBlockTypeRestriction(): A_Type
	{
		return functionType(emptyTuple(), booleanType())
	}

}