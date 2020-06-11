/*
 * P_SetCompiledCodeName.kt
 * Copyright © 1993-2019, The Avail Foundation, LLC.
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
package com.avail.interpreter.primitive.rawfunctions

import com.avail.descriptor.functions.CompiledCodeDescriptor
import com.avail.descriptor.methods.MethodDescriptor.SpecialMethodAtom
import com.avail.descriptor.representation.NilDescriptor.Companion.nil
import com.avail.descriptor.tuples.A_String
import com.avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.CompiledCodeTypeDescriptor.Companion.mostGeneralCompiledCodeType
import com.avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import com.avail.descriptor.types.TupleTypeDescriptor.Companion.nonemptyStringType
import com.avail.descriptor.types.TypeDescriptor.Types
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Flag.CanInline
import com.avail.interpreter.Primitive.Flag.CannotFail
import com.avail.interpreter.Primitive.Flag.WritesToHiddenGlobalState
import com.avail.interpreter.effects.LoadingEffectToRunPrimitive
import com.avail.interpreter.execution.Interpreter
import com.avail.interpreter.levelTwo.L2Operation.HiddenVariable.GLOBAL_STATE
import com.avail.interpreter.levelTwo.WritesHiddenVariable

/**
 * **Primitive:** Set a [compiled&#32;code][CompiledCodeDescriptor]'s symbolic
 * name to the given [A_String].  Also name its sub-functions in a systematic
 * way.
 */
@WritesHiddenVariable(GLOBAL_STATE::class)
object P_SetCompiledCodeName : Primitive(
	2, CannotFail, CanInline, WritesToHiddenGlobalState)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(2)
		val code = interpreter.argument(0)
		val name = interpreter.argument(1)
		code.setMethodName(name)
		interpreter.availLoaderOrNull()?.recordEffect(
			LoadingEffectToRunPrimitive(
				SpecialMethodAtom.SET_COMPILED_CODE_NAME.bundle,
				code,
				name))
		return interpreter.primitiveSuccess(nil)
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(
				mostGeneralCompiledCodeType(),
				nonemptyStringType()),
			Types.TOP.o())
}
