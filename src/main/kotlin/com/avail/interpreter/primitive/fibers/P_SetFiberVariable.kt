/*
 * P_SetFiberVariable.java
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
import com.avail.descriptor.AtomDescriptor
import com.avail.descriptor.AtomDescriptor.SpecialAtom.HERITABLE_KEY
import com.avail.descriptor.FiberDescriptor
import com.avail.descriptor.FunctionTypeDescriptor.functionType
import com.avail.descriptor.NilDescriptor.nil
import com.avail.descriptor.ObjectTupleDescriptor.tuple
import com.avail.descriptor.TypeDescriptor.Types.*
import com.avail.interpreter.Interpreter
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Flag.*

/**
 * **Primitive:** Associate the given value with the given
 * [name][AtomDescriptor] (key) in the variables of the
 * current [fiber][FiberDescriptor].
 */
object P_SetFiberVariable : Primitive(2, CannotFail, CanInline, HasSideEffect)
{

	override fun attempt(
		interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(2)
		val key = interpreter.argument(0)
		val value = interpreter.argument(1)
		val fiber = interpreter.fiber()
		if (key.getAtomProperty(HERITABLE_KEY.atom).equalsNil())
		{
			fiber.fiberGlobals(
				fiber.fiberGlobals().mapAtPuttingCanDestroy(
					key.makeImmutable(),
					value.makeImmutable(),
					true))
		}
		else
		{
			fiber.heritableFiberGlobals(
				fiber.heritableFiberGlobals().mapAtPuttingCanDestroy(
					key.makeImmutable(),
					value.makeImmutable(),
					true))
		}
		return interpreter.primitiveSuccess(nil)
	}

	override fun privateBlockTypeRestriction(): A_Type
	{
		return functionType(tuple(ATOM.o(), ANY.o()), TOP.o())
	}

}