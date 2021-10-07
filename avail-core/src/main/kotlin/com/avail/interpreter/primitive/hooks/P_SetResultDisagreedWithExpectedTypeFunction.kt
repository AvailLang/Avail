/*
 * P_SetResultDisagreedWithExpectedTypeFunction.kt
 * Copyright © 1993-2021, The Avail Foundation, LLC.
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

package com.avail.interpreter.primitive.hooks

import com.avail.AvailRuntime.HookType.RESULT_DISAGREED_WITH_EXPECTED_TYPE
import com.avail.descriptor.functions.FunctionDescriptor
import com.avail.descriptor.methods.MethodDescriptor
import com.avail.descriptor.representation.NilDescriptor.Companion.nil
import com.avail.descriptor.tuples.ObjectTupleDescriptor
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.BottomTypeDescriptor.Companion.bottom
import com.avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import com.avail.descriptor.types.FunctionTypeDescriptor.Companion.mostGeneralFunctionType
import com.avail.descriptor.types.InstanceMetaDescriptor.Companion.topMeta
import com.avail.descriptor.types.PrimitiveTypeDescriptor.Types.ANY
import com.avail.descriptor.types.PrimitiveTypeDescriptor.Types.TOP
import com.avail.descriptor.types.VariableTypeDescriptor.Companion.variableTypeFor
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Flag.CannotFail
import com.avail.interpreter.Primitive.Flag.HasSideEffect
import com.avail.interpreter.Primitive.Flag.WritesToHiddenGlobalState
import com.avail.interpreter.execution.Interpreter

/**
 * **Primitive:** Set the [function][FunctionDescriptor] to invoke whenever the
 * result produced by a [method&#32;invocation][MethodDescriptor] disagrees with
 * the type decreed by the applicable semantic restrictions at the call site.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
@Suppress("unused")
object P_SetResultDisagreedWithExpectedTypeFunction : Primitive(
	1, CannotFail, HasSideEffect, WritesToHiddenGlobalState)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(1)
		val function = interpreter.argument(0)
		RESULT_DISAGREED_WITH_EXPECTED_TYPE[interpreter.runtime] = function
		interpreter.availLoaderOrNull()?.statementCanBeSummarized(false)
		return interpreter.primitiveSuccess(nil)
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			ObjectTupleDescriptor.tuple(
				functionType(
					ObjectTupleDescriptor.tuple(
						mostGeneralFunctionType(),
						topMeta(),
						variableTypeFor(ANY.o)),
					bottom)),
			TOP.o)
}
