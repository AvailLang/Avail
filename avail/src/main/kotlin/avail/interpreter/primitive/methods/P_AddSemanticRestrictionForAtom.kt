/*
 * P_AddSemanticRestrictionForAtom.kt
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

package avail.interpreter.primitive.methods

import avail.compiler.splitter.MessageSplitter.Companion.possibleErrors
import avail.descriptor.methods.SemanticRestrictionDescriptor.Companion.newSemanticRestriction
import avail.descriptor.representation.A_Atom.Companion.atomName
import avail.descriptor.representation.A_Atom.Companion.bundleOrCreate
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.A_Bundle.Companion.bundleMethod
import avail.descriptor.representation.A_RawFunction.Companion.methodName
import avail.descriptor.representation.A_RawFunction.Companion.numArgs
import avail.descriptor.representation.A_Set.Companion.setUnionCanDestroy
import avail.descriptor.representation.A_Type
import avail.descriptor.representation.A_Type.Companion.argsTupleType
import avail.descriptor.representation.A_Type.Companion.typeAtIndex
import avail.descriptor.representation.AvailObject
import avail.descriptor.representation.NilDescriptor.Companion.nil
import avail.descriptor.sets.SetDescriptor.Companion.set
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.tuples.StringDescriptor.Companion.stringFrom
import avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionTypeReturning
import avail.descriptor.types.InstanceMetaDescriptor.Companion.topMeta
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.ATOM
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.TOP
import avail.exceptions.AvailErrorCode.E_CANNOT_DEFINE_DURING_COMPILATION
import avail.exceptions.AvailErrorCode.E_INCORRECT_NUMBER_OF_ARGUMENTS
import avail.exceptions.AvailErrorCode.E_LOADING_IS_OVER
import avail.exceptions.AvailErrorCode.E_TYPE_RESTRICTION_MUST_ACCEPT_ONLY_TYPES
import avail.exceptions.MalformedMessageException
import avail.exceptions.SignatureException
import avail.interpreter.execution.Interpreter
import avail.interpreter.primitive.Primitive.Flag.Unknown
import avail.interpreter.primitive.Primitive2

/**
 * **Primitive:** Add a type restriction function.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
@Suppress("unused")
object P_AddSemanticRestrictionForAtom : Primitive2(Unknown)
{
	override fun Interpreter.attempt2(
		arg1: AvailObject,
		arg2: AvailObject
	): A_BasicObject?
	{
		val atom = arg1
		val function = arg2
		val functionType = function.kind()
		val tupleType = functionType.argsTupleType
		val loader = availLoaderOrNull() ?: return fail(E_LOADING_IS_OVER)
		if (!loader.phase.isExecuting)
		{
			return fail(E_CANNOT_DEFINE_DURING_COMPILATION)
		}
		for (i in function.code().numArgs() downTo 1)
		{
			if (!tupleType.typeAtIndex(i).isInstanceMeta)
			{
				return fail(
					E_TYPE_RESTRICTION_MUST_ACCEPT_ONLY_TYPES)
			}
		}
		try
		{
			val method = atom.bundleOrCreate().bundleMethod
			val restriction = newSemanticRestriction(function, method, module())
			loader.addSemanticRestriction(restriction)
		}
		catch (e: MalformedMessageException)
		{
			return fail(e.errorCode)
		}
		catch (e: SignatureException)
		{
			return fail(e.errorCode)
		}

		function.code().methodName =
			stringFrom("Semantic restriction of ${atom.atomName}")
		return nil
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(ATOM(), functionTypeReturning(topMeta)),
			TOP())

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(
			set(
					E_LOADING_IS_OVER,
					E_CANNOT_DEFINE_DURING_COMPILATION,
					E_TYPE_RESTRICTION_MUST_ACCEPT_ONLY_TYPES,
					E_INCORRECT_NUMBER_OF_ARGUMENTS)
				.setUnionCanDestroy(possibleErrors, true))
}
