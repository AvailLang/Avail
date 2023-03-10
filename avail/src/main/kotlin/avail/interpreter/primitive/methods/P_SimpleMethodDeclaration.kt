/*
 * P_SimpleMethodDeclaration.kt
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
import avail.descriptor.atoms.A_Atom.Companion.atomName
import avail.descriptor.atoms.A_Atom.Companion.bundleOrCreate
import avail.descriptor.atoms.A_Atom.Companion.bundleOrNil
import avail.descriptor.atoms.AtomDescriptor
import avail.descriptor.bundles.A_Bundle.Companion.bundleMethod
import avail.descriptor.fiber.A_Fiber.Companion.availLoader
import avail.descriptor.functions.A_Function
import avail.descriptor.functions.A_RawFunction.Companion.methodName
import avail.descriptor.functions.FunctionDescriptor
import avail.descriptor.methods.A_Styler.Companion.stylerFunctionType
import avail.descriptor.methods.SemanticRestrictionDescriptor.Companion.newSemanticRestriction
import avail.descriptor.module.ModuleDescriptor
import avail.descriptor.representation.NilDescriptor.Companion.nil
import avail.descriptor.sets.A_Set.Companion.setUnionCanDestroy
import avail.descriptor.sets.SetDescriptor.Companion.set
import avail.descriptor.tuples.A_String
import avail.descriptor.tuples.A_Tuple
import avail.descriptor.tuples.A_Tuple.Companion.tupleAt
import avail.descriptor.tuples.A_Tuple.Companion.tupleSize
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.tuples.StringDescriptor.Companion.stringFrom
import avail.descriptor.types.A_Type
import avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.FunctionTypeDescriptor.Companion.mostGeneralFunctionType
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.TOP
import avail.descriptor.types.TupleTypeDescriptor.Companion.stringType
import avail.descriptor.types.TupleTypeDescriptor.Companion.zeroOrOneOf
import avail.exceptions.AvailErrorCode.E_AMBIGUOUS_NAME
import avail.exceptions.AvailErrorCode.E_CANNOT_DEFINE_DURING_COMPILATION
import avail.exceptions.AvailErrorCode.E_INCORRECT_NUMBER_OF_ARGUMENTS
import avail.exceptions.AvailErrorCode.E_LOADING_IS_OVER
import avail.exceptions.AvailErrorCode.E_METHOD_IS_SEALED
import avail.exceptions.AvailErrorCode.E_METHOD_RETURN_TYPE_NOT_AS_FORWARD_DECLARED
import avail.exceptions.AvailErrorCode.E_REDEFINED_WITH_SAME_ARGUMENT_TYPES
import avail.exceptions.AvailErrorCode.E_RESULT_TYPE_SHOULD_COVARY_WITH_ARGUMENTS
import avail.exceptions.AvailErrorCode.E_STYLER_ALREADY_SET_BY_THIS_MODULE
import avail.exceptions.AvailException
import avail.interpreter.Primitive
import avail.interpreter.Primitive.Flag.Bootstrap
import avail.interpreter.Primitive.Flag.CanFold
import avail.interpreter.Primitive.Flag.CanSuspend
import avail.interpreter.Primitive.Flag.Unknown
import avail.interpreter.execution.AvailLoader.Companion.addBootstrapStyler
import avail.interpreter.execution.AvailLoader.Phase.EXECUTING_FOR_COMPILE
import avail.interpreter.execution.Interpreter
import avail.interpreter.primitive.style.P_BootstrapDefinitionStyler
import avail.utility.notNullAnd

/**
 * **Primitive:** Add a method definition, given a string for which to look up
 * the corresponding [atom][AtomDescriptor] in the current
 * [module][ModuleDescriptor] and the [function][FunctionDescriptor] which will
 * act as the body of the method definition.  An optional (tuple of size 0 or 1)
 * styling function can also be provided.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
@Suppress("unused")
object P_SimpleMethodDeclaration : Primitive(3, Bootstrap, CanSuspend, Unknown)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(3)
		val string: A_String = interpreter.argument(0)
		val function: A_Function = interpreter.argument(1)
		val optionalStylerFunction: A_Tuple = interpreter.argument(2)

		val fiber = interpreter.fiber()
		val loader = fiber.availLoader
		if (loader === null || loader.module.isNil)
		{
			return interpreter.primitiveFailure(E_LOADING_IS_OVER)
		}
		if (!loader.phase.isExecuting)
		{
			return interpreter.primitiveFailure(
				E_CANNOT_DEFINE_DURING_COMPILATION)
		}
		return interpreter.suspendInSafePointThen {
			try
			{
				val atom = loader.lookupName(string)
				loader.addMethodBody(atom, function)
				// Quote the string to make the method name.
				val atomName = atom.atomName
				val code = function.code()
				code.methodName = stringFrom(atomName.toString())
				// Only explicitly define the stability helper during
				// compilation.  Fast-loader will just deserialize it.
				if (loader.phase == EXECUTING_FOR_COMPILE)
				{
					if (code.codePrimitive().notNullAnd { hasFlag(CanFold) })
					{
						val restrictionBody =
							P_SimpleMethodStabilityHelper.createRestrictionBody(
								function)
						val restriction = newSemanticRestriction(
							restrictionBody,
							atom.bundleOrNil.bundleMethod,
							loader.module)
						loader.addSemanticRestriction(restriction)
					}
				}
				if (optionalStylerFunction.tupleSize == 1)
				{
					val stylerFunction = optionalStylerFunction.tupleAt(1)
					loader.addStyler(atom.bundleOrCreate(), stylerFunction)
				}
				else
				{
					// If a styler function was not specified, but the body was
					// a primitive that has a bootstrapStyler, use that just as
					// though it had been specified.
					addBootstrapStyler(code, atom, loader.module)
				}
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

	override fun privateBlockTypeRestriction(): A_Type = functionType(
		tuple(
			stringType,
			mostGeneralFunctionType(),
			zeroOrOneOf(stylerFunctionType)),
		TOP.o)

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(
			set(
				E_LOADING_IS_OVER,
				E_CANNOT_DEFINE_DURING_COMPILATION,
				E_AMBIGUOUS_NAME,
				E_INCORRECT_NUMBER_OF_ARGUMENTS,
				E_METHOD_RETURN_TYPE_NOT_AS_FORWARD_DECLARED,
				E_REDEFINED_WITH_SAME_ARGUMENT_TYPES,
				E_RESULT_TYPE_SHOULD_COVARY_WITH_ARGUMENTS,
				E_METHOD_IS_SEALED,
				E_STYLER_ALREADY_SET_BY_THIS_MODULE
			).setUnionCanDestroy(possibleErrors, true))

	override fun bootstrapStyler() = P_BootstrapDefinitionStyler
}
