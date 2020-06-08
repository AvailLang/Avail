/*
 * P_MethodDeclarationFromAtom.kt
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
import com.avail.descriptor.atoms.A_Atom.Companion.atomName
import com.avail.descriptor.representation.NilDescriptor.Companion.nil
import com.avail.descriptor.sets.SetDescriptor.Companion.set
import com.avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import com.avail.descriptor.tuples.StringDescriptor.Companion.stringFrom
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import com.avail.descriptor.types.FunctionTypeDescriptor.functionType
import com.avail.descriptor.types.FunctionTypeDescriptor.mostGeneralFunctionType
import com.avail.descriptor.types.TypeDescriptor.Types.ATOM
import com.avail.descriptor.types.TypeDescriptor.Types.TOP
import com.avail.exceptions.AvailErrorCode.E_CANNOT_DEFINE_DURING_COMPILATION
import com.avail.exceptions.AvailErrorCode.E_LOADING_IS_OVER
import com.avail.exceptions.AvailErrorCode.E_METHOD_IS_SEALED
import com.avail.exceptions.AvailErrorCode.E_METHOD_RETURN_TYPE_NOT_AS_FORWARD_DECLARED
import com.avail.exceptions.AvailErrorCode.E_REDEFINED_WITH_SAME_ARGUMENT_TYPES
import com.avail.exceptions.AvailErrorCode.E_RESULT_TYPE_SHOULD_COVARY_WITH_ARGUMENTS
import com.avail.exceptions.AvailException
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Flag.CanSuspend
import com.avail.interpreter.Primitive.Flag.Unknown
import com.avail.interpreter.execution.Interpreter

/**
 * **Primitive:** Define a concrete method implementation.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object P_MethodDeclarationFromAtom : Primitive(2, CanSuspend, Unknown)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(2)
		val atom = interpreter.argument(0)
		val function = interpreter.argument(1)
		val fiber = interpreter.fiber()
		val loader = fiber.availLoader()
		if (loader === null || loader.module().equalsNil())
		{
			return interpreter.primitiveFailure(E_LOADING_IS_OVER)
		}
		if (!loader.phase().isExecuting)
		{
			return interpreter.primitiveFailure(
				E_CANNOT_DEFINE_DURING_COMPILATION)
		}

		return interpreter.suspendInLevelOneSafeThen {
			try
			{
				loader.addMethodBody(atom, function)
				// Quote the string to make the method name.
				function.code().setMethodName(
					stringFrom(atom.atomName().toString()))
				succeed(nil)
			}
			catch (e: AvailException)
			{
				// MalformedMessageException
				// SignatureException
				fail(e.errorCode)
			}
		}
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(tuple(ATOM.o(), mostGeneralFunctionType()), TOP.o())

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(
			set(
					E_LOADING_IS_OVER,
					E_CANNOT_DEFINE_DURING_COMPILATION,
					E_METHOD_RETURN_TYPE_NOT_AS_FORWARD_DECLARED,
					E_REDEFINED_WITH_SAME_ARGUMENT_TYPES,
					E_RESULT_TYPE_SHOULD_COVARY_WITH_ARGUMENTS,
					E_METHOD_IS_SEALED)
				.setUnionCanDestroy(possibleErrors, true))
}
