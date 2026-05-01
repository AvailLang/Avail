/*
 * P_PrivateForceOptimizationForTests.kt
 * Copyright © 1993-2025, The Avail Foundation, LLC.
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

import avail.descriptor.functions.A_RawFunction
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.AvailObject
import avail.descriptor.representation.NilDescriptor.Companion.nil
import avail.descriptor.sets.SetDescriptor.Companion.set
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.types.A_Type
import avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import avail.descriptor.types.CompiledCodeTypeDescriptor.Companion.mostGeneralCompiledCodeType
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.PrimitiveTypeDescriptor.Types
import avail.exceptions.AvailErrorCode.E_ILLEGAL_TRACE_MODE
import avail.interpreter.execution.Interpreter
import avail.interpreter.levelTwo.HiddenVariable.GLOBAL_STATE
import avail.interpreter.levelTwo.WritesHiddenVariable
import avail.interpreter.primitive.Primitive.Flag.HasSideEffect
import avail.interpreter.primitive.Primitive.Flag.Private
import avail.interpreter.primitive.Primitive1
import avail.optimizer.L1Translator
import avail.optimizer.OptimizationLevel

/**
 * **Primitive:** This private primitive is used by tests to force L2
 * optimization of a supplied [A_RawFunction].
 */
@WritesHiddenVariable(GLOBAL_STATE::class)
object P_PrivateForceOptimizationForTests : Primitive1(Private, HasSideEffect)
{
	override fun attempt1(
		interpreter: Interpreter,
		arg1: AvailObject
	): A_BasicObject?
	{
		val code = arg1
		try
		{
			L1Translator.`🌼translateToLevelTwo`(
				code,
				OptimizationLevel.FIRST_JVM_TRANSLATION,
				interpreter)
		}
		catch (e: Throwable)
		{
			// Reuse an easily identified error code that isn't likely to be
			// encountered otherwise.
			return interpreter.fail(E_ILLEGAL_TRACE_MODE)
		}
		return nil
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(
				mostGeneralCompiledCodeType()),
			Types.TOP())

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(set(E_ILLEGAL_TRACE_MODE))

	override val canDestroyArguments get() = false
}
