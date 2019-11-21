/*
 * P_ForwardMethodDeclarationForAtom.kt
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
package com.avail.interpreter.primitive.methods

import com.avail.compiler.splitter.MessageSplitter.Companion.possibleErrors
import com.avail.descriptor.A_Type
import com.avail.descriptor.AbstractEnumerationTypeDescriptor.enumerationWith
import com.avail.descriptor.FunctionTypeDescriptor.functionMeta
import com.avail.descriptor.FunctionTypeDescriptor.functionType
import com.avail.descriptor.NilDescriptor.nil
import com.avail.descriptor.ObjectTupleDescriptor.tuple
import com.avail.descriptor.SetDescriptor.set
import com.avail.descriptor.TypeDescriptor.Types.ATOM
import com.avail.descriptor.TypeDescriptor.Types.TOP
import com.avail.exceptions.AvailErrorCode.*
import com.avail.exceptions.AvailException
import com.avail.interpreter.Interpreter
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Flag.CanSuspend
import com.avail.interpreter.Primitive.Flag.Unknown

/**
 * **Primitive:** Forward declare a method (for recursion or mutual recursion).
 */
object P_ForwardMethodDeclarationForAtom : Primitive(2, CanSuspend, Unknown)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(2)
		val atom = interpreter.argument(0)
		val blockSignature = interpreter.argument(1)
		val fiber = interpreter.fiber()
		val loader = fiber.availLoader()
		             ?: return interpreter.primitiveFailure(E_LOADING_IS_OVER)
		if (!loader.phase().isExecuting)
		{
			return interpreter.primitiveFailure(
				E_CANNOT_DEFINE_DURING_COMPILATION)
		}

		return interpreter.suspendAndDoInLevelOneSafe {
			toSucceed, toFail ->
			try
			{
				loader.addForwardStub(atom, blockSignature)
				toSucceed.value(nil)
			}
			catch (e: AvailException)
			{
				// MalformedMessageException
				// SignatureException
				toFail.value(e.errorCode)
			}
		}
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(tuple(ATOM.o(), functionMeta()), TOP.o())

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(set(
				E_LOADING_IS_OVER,
				E_CANNOT_DEFINE_DURING_COMPILATION,
			    E_REDEFINED_WITH_SAME_ARGUMENT_TYPES,
			    E_RESULT_TYPE_SHOULD_COVARY_WITH_ARGUMENTS,
			    E_METHOD_IS_SEALED)
			.setUnionCanDestroy(possibleErrors, true))
}
