/*
 * P_SimpleMethodDeclaration.kt
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
package com.avail.interpreter.primitive.methods

import com.avail.compiler.splitter.MessageSplitter.Companion.possibleErrors
import com.avail.descriptor.atoms.AtomDescriptor
import com.avail.descriptor.functions.FunctionDescriptor
import com.avail.descriptor.module.ModuleDescriptor
import com.avail.descriptor.representation.NilDescriptor.Companion.nil
import com.avail.descriptor.sets.A_Set.Companion.setUnionCanDestroy
import com.avail.descriptor.sets.SetDescriptor.Companion.set
import com.avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import com.avail.descriptor.tuples.StringDescriptor.Companion.stringFrom
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import com.avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import com.avail.descriptor.types.FunctionTypeDescriptor.Companion.mostGeneralFunctionType
import com.avail.descriptor.types.TupleTypeDescriptor.Companion.stringType
import com.avail.descriptor.types.TypeDescriptor.Types.TOP
import com.avail.exceptions.AvailErrorCode.E_AMBIGUOUS_NAME
import com.avail.exceptions.AvailErrorCode.E_CANNOT_DEFINE_DURING_COMPILATION
import com.avail.exceptions.AvailErrorCode.E_LOADING_IS_OVER
import com.avail.exceptions.AvailErrorCode.E_METHOD_IS_SEALED
import com.avail.exceptions.AvailErrorCode.E_METHOD_RETURN_TYPE_NOT_AS_FORWARD_DECLARED
import com.avail.exceptions.AvailErrorCode.E_REDEFINED_WITH_SAME_ARGUMENT_TYPES
import com.avail.exceptions.AvailErrorCode.E_RESULT_TYPE_SHOULD_COVARY_WITH_ARGUMENTS
import com.avail.exceptions.AvailException
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Flag.Bootstrap
import com.avail.interpreter.Primitive.Flag.CanSuspend
import com.avail.interpreter.Primitive.Flag.Unknown
import com.avail.interpreter.execution.Interpreter

/**
 * **Primitive:** Add a method definition, given a string for which to look up
 * the corresponding [atom][AtomDescriptor] in the current
 * [module][ModuleDescriptor] and the [function][FunctionDescriptor] which will
 * act as the body of the method definition.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
@Suppress("unused")
object P_SimpleMethodDeclaration : Primitive(2, Bootstrap, CanSuspend, Unknown)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(2)
		val string = interpreter.argument(0)
		val function = interpreter.argument(1)
		val fiber = interpreter.fiber()
		val loader = fiber.availLoader()
		if (loader === null || loader.module().isNil)
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
				val atom = loader.lookupName(string)
				loader.addMethodBody(atom, function)
				// Quote the string to make the method name.
				function.code().setMethodName(
					stringFrom(string.toString()))
				succeed(nil)
			}
			catch (e: AvailException)
			{
				// MalformedMessageException
				// SignatureException
				// AmbiguousNameException
				fail(e.errorCode)
			}
		}
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(tuple(stringType(), mostGeneralFunctionType()), TOP.o)

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(
			set(
					E_LOADING_IS_OVER,
					E_CANNOT_DEFINE_DURING_COMPILATION,
					E_AMBIGUOUS_NAME,
					E_METHOD_RETURN_TYPE_NOT_AS_FORWARD_DECLARED,
					E_REDEFINED_WITH_SAME_ARGUMENT_TYPES,
					E_RESULT_TYPE_SHOULD_COVARY_WITH_ARGUMENTS,
					E_METHOD_IS_SEALED)
				.setUnionCanDestroy(possibleErrors, true))
}
