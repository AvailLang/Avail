/*
 * P_SetCompiledCodeName.kt
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
package avail.interpreter.primitive.rawfunctions

import avail.descriptor.functions.A_RawFunction.Companion.methodName
import avail.descriptor.functions.CompiledCodeDescriptor
import avail.descriptor.methods.MethodDescriptor.SpecialMethodAtom
import avail.descriptor.representation.NilDescriptor.Companion.nil
import avail.descriptor.tuples.A_String
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.types.A_Type
import avail.descriptor.types.CompiledCodeTypeDescriptor.Companion.mostGeneralCompiledCodeType
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.TupleTypeDescriptor.Companion.nonemptyStringType
import avail.descriptor.types.PrimitiveTypeDescriptor.Types
import avail.interpreter.Primitive
import avail.interpreter.Primitive.Flag.CanInline
import avail.interpreter.Primitive.Flag.CannotFail
import avail.interpreter.Primitive.Flag.WritesToHiddenGlobalState
import avail.interpreter.effects.LoadingEffectToRunPrimitive
import avail.interpreter.execution.Interpreter
import avail.interpreter.levelTwo.L2Operation.HiddenVariable.GLOBAL_STATE
import avail.interpreter.levelTwo.WritesHiddenVariable

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
		code.methodName = name
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
				nonemptyStringType),
			Types.TOP.o)
}