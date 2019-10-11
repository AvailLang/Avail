/*
 * P_AddSemanticRestriction.java
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
package com.avail.interpreter.primitive.methods

import com.avail.descriptor.A_Atom
import com.avail.descriptor.A_Function
import com.avail.descriptor.A_Method
import com.avail.descriptor.A_SemanticRestriction
import com.avail.descriptor.A_String
import com.avail.descriptor.A_Type
import com.avail.exceptions.AmbiguousNameException
import com.avail.exceptions.MalformedMessageException
import com.avail.exceptions.SignatureException
import com.avail.interpreter.AvailLoader
import com.avail.interpreter.Interpreter
import com.avail.interpreter.Primitive
import com.avail.optimizer.jvm.ReferencedInGeneratedCode

import com.avail.compiler.splitter.MessageSplitter.Companion.possibleErrors
import com.avail.descriptor.AbstractEnumerationTypeDescriptor.enumerationWith
import com.avail.descriptor.FunctionTypeDescriptor.functionType
import com.avail.descriptor.FunctionTypeDescriptor.functionTypeReturning
import com.avail.descriptor.InstanceMetaDescriptor.topMeta
import com.avail.descriptor.NilDescriptor.nil
import com.avail.descriptor.ObjectTupleDescriptor.tuple
import com.avail.descriptor.SemanticRestrictionDescriptor.newSemanticRestriction
import com.avail.descriptor.SetDescriptor.set
import com.avail.descriptor.StringDescriptor.formatString
import com.avail.descriptor.TupleTypeDescriptor.stringType
import com.avail.descriptor.TypeDescriptor.Types.TOP
import com.avail.exceptions.AvailErrorCode.E_AMBIGUOUS_NAME
import com.avail.exceptions.AvailErrorCode.E_CANNOT_DEFINE_DURING_COMPILATION
import com.avail.exceptions.AvailErrorCode.E_INCORRECT_NUMBER_OF_ARGUMENTS
import com.avail.exceptions.AvailErrorCode.E_LOADING_IS_OVER
import com.avail.exceptions.AvailErrorCode.E_TYPE_RESTRICTION_MUST_ACCEPT_ONLY_TYPES
import com.avail.interpreter.Primitive.Flag.Unknown

/**
 * **Primitive:** Add a type restriction function.
 */
object P_AddSemanticRestriction : Primitive(2, Unknown)
{

	override fun attempt(
		interpreter: Interpreter): Primitive.Result
	{
		interpreter.checkArgumentCount(2)
		val string = interpreter.argument(0)
		val function = interpreter.argument(1)
		val functionType = function.kind()
		val tupleType = functionType.argsTupleType()
		val loader = interpreter.availLoaderOrNull()
		             ?: return interpreter.primitiveFailure(E_LOADING_IS_OVER)
		if (!loader.phase().isExecuting)
		{
			return interpreter.primitiveFailure(
				E_CANNOT_DEFINE_DURING_COMPILATION)
		}
		for (i in function.code().numArgs() downTo 1)
		{
			if (!tupleType.typeAtIndex(i).isInstanceMeta)
			{
				return interpreter.primitiveFailure(
					E_TYPE_RESTRICTION_MUST_ACCEPT_ONLY_TYPES)
			}
		}
		try
		{
			val atom = loader.lookupName(string)
			val method = atom.bundleOrCreate().bundleMethod()
			val restriction = newSemanticRestriction(function, method, interpreter.module())
			loader.addSemanticRestriction(restriction)
		}
		catch (e: MalformedMessageException)
		{
			return interpreter.primitiveFailure(e)
		}
		catch (e: AmbiguousNameException)
		{
			return interpreter.primitiveFailure(e)
		}
		catch (e: SignatureException)
		{
			return interpreter.primitiveFailure(e)
		}

		function.code().setMethodName(
			formatString("Semantic restriction of %s", string))
		return interpreter.primitiveSuccess(nil)
	}

	override fun privateBlockTypeRestriction(): A_Type
	{
		return functionType(
			tuple(
				stringType(),
				functionTypeReturning(topMeta())),
			TOP.o())
	}

	override fun privateFailureVariableType(): A_Type
	{
		return enumerationWith(
			set(
				E_LOADING_IS_OVER,
				E_CANNOT_DEFINE_DURING_COMPILATION,
				E_AMBIGUOUS_NAME,
				E_TYPE_RESTRICTION_MUST_ACCEPT_ONLY_TYPES,
				E_INCORRECT_NUMBER_OF_ARGUMENTS
			).setUnionCanDestroy(possibleErrors, true))
	}

}