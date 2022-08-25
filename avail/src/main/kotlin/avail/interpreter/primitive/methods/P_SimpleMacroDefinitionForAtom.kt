/*
 * P_SimpleMacroDefinitionForAtom.kt
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
import avail.compiler.splitter.MessageSplitter.Metacharacter
import avail.descriptor.atoms.A_Atom.Companion.atomName
import avail.descriptor.atoms.A_Atom.Companion.bundleOrCreate
import avail.descriptor.bundles.A_Bundle.Companion.messageSplitter
import avail.descriptor.fiber.A_Fiber.Companion.availLoader
import avail.descriptor.functions.A_RawFunction.Companion.methodName
import avail.descriptor.functions.A_RawFunction.Companion.numArgs
import avail.descriptor.functions.FunctionDescriptor
import avail.descriptor.methods.A_Styler
import avail.descriptor.phrases.PhraseDescriptor
import avail.descriptor.representation.NilDescriptor.Companion.nil
import avail.descriptor.sets.A_Set.Companion.setUnionCanDestroy
import avail.descriptor.sets.SetDescriptor.Companion.set
import avail.descriptor.tuples.A_Tuple
import avail.descriptor.tuples.A_Tuple.Companion.tupleAt
import avail.descriptor.tuples.A_Tuple.Companion.tupleSize
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.tuples.StringDescriptor.Companion.stringFrom
import avail.descriptor.tuples.TupleDescriptor
import avail.descriptor.types.A_Type
import avail.descriptor.types.A_Type.Companion.argsTupleType
import avail.descriptor.types.A_Type.Companion.isSubtypeOf
import avail.descriptor.types.A_Type.Companion.returnType
import avail.descriptor.types.A_Type.Companion.typeAtIndex
import avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionTypeReturning
import avail.descriptor.types.FunctionTypeDescriptor.Companion.mostGeneralFunctionType
import avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.PARSE_PHRASE
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.ATOM
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.TOP
import avail.descriptor.types.TupleTypeDescriptor.Companion.zeroOrMoreOf
import avail.descriptor.types.TupleTypeDescriptor.Companion.zeroOrOneOf
import avail.exceptions.AvailErrorCode.E_CANNOT_DEFINE_DURING_COMPILATION
import avail.exceptions.AvailErrorCode.E_INCORRECT_NUMBER_OF_ARGUMENTS
import avail.exceptions.AvailErrorCode.E_LOADING_IS_OVER
import avail.exceptions.AvailErrorCode.E_MACRO_ARGUMENT_MUST_BE_A_PHRASE
import avail.exceptions.AvailErrorCode.E_MACRO_MUST_RETURN_A_PHRASE
import avail.exceptions.AvailErrorCode.E_MACRO_PREFIX_FUNCTIONS_MUST_RETURN_TOP
import avail.exceptions.AvailErrorCode.E_MACRO_PREFIX_FUNCTION_ARGUMENT_MUST_BE_A_PHRASE
import avail.exceptions.AvailErrorCode.E_MACRO_PREFIX_FUNCTION_INDEX_OUT_OF_BOUNDS
import avail.exceptions.AvailErrorCode.E_REDEFINED_WITH_SAME_ARGUMENT_TYPES
import avail.exceptions.AvailErrorCode.E_STYLER_ALREADY_SET_BY_THIS_MODULE
import avail.exceptions.AvailException
import avail.exceptions.MalformedMessageException
import avail.interpreter.Primitive
import avail.interpreter.Primitive.Flag.CanSuspend
import avail.interpreter.Primitive.Flag.Unknown
import avail.interpreter.execution.AvailLoader.Companion.addBootstrapStyler
import avail.interpreter.execution.Interpreter
import avail.interpreter.primitive.style.P_BootstrapDefinitionStyler

/**
 * **Primitive:** Simple macro definition.  The first argument is the macro
 * name, and the second argument is a [tuple][TupleDescriptor] of
 * [functions][FunctionDescriptor] returning ⊤, one for each occurrence of a
 * [section&#32;sign][Metacharacter.SECTION_SIGN] (§) in the macro name.  The
 * third argument is the function to invoke for the complete macro.  It is
 * constrained to answer a [phrase][PhraseDescriptor].
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
@Suppress("unused")
object P_SimpleMacroDefinitionForAtom : Primitive(4, CanSuspend, Unknown)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(4)
		val atom = interpreter.argument(0)
		val prefixFunctions = interpreter.argument(1)
		val function = interpreter.argument(2)
		val optionalStylerFunction: A_Tuple = interpreter.argument(3)

		val fiber = interpreter.fiber()
		val loader = fiber.availLoader ?:
			return interpreter.primitiveFailure(E_LOADING_IS_OVER)
		if (!loader.phase().isExecuting)
		{
			return interpreter.primitiveFailure(
				E_CANNOT_DEFINE_DURING_COMPILATION)
		}
		prefixFunctions.forEach { prefixFunction ->
			val numArgs = prefixFunction.code().numArgs()
			val kind = prefixFunction.kind()
			val argsKind = kind.argsTupleType
			(1 .. numArgs).forEach { argIndex ->
				if (!argsKind.typeAtIndex(argIndex)
						.isSubtypeOf(PARSE_PHRASE.mostGeneralType))
				{
					return interpreter.primitiveFailure(
						E_MACRO_PREFIX_FUNCTION_ARGUMENT_MUST_BE_A_PHRASE)
				}
			}
			if (!kind.returnType.isTop)
			{
				return interpreter.primitiveFailure(
					E_MACRO_PREFIX_FUNCTIONS_MUST_RETURN_TOP)
			}
		}
		try
		{
			val splitter = atom.bundleOrCreate().messageSplitter
			if (prefixFunctions.tupleSize !=
				splitter.numberOfSectionCheckpoints)
			{
				return interpreter.primitiveFailure(
					E_MACRO_PREFIX_FUNCTION_INDEX_OUT_OF_BOUNDS)
			}
		}
		catch (e: MalformedMessageException)
		{
			return interpreter.primitiveFailure(e.errorCode)
		}

		val numArgs = function.code().numArgs()
		val kind = function.kind()
		val argsKind = kind.argsTupleType
		for (argIndex in 1 .. numArgs)
		{
			if (!argsKind.typeAtIndex(argIndex).isSubtypeOf(
					PARSE_PHRASE.mostGeneralType))
			{
				return interpreter.primitiveFailure(
					E_MACRO_ARGUMENT_MUST_BE_A_PHRASE)
			}
		}
		if (!kind.returnType.isSubtypeOf(PARSE_PHRASE.mostGeneralType))
		{
			return interpreter.primitiveFailure(E_MACRO_MUST_RETURN_A_PHRASE)
		}

		return interpreter.suspendInSafePointThen {
			try
			{
				loader.addMacroBody(atom, function, prefixFunctions, false)
				val atomName = atom.atomName
				if (optionalStylerFunction.tupleSize == 1)
				{
					val stylerFunction = optionalStylerFunction.tupleAt(1)
					loader.addStyler(atom.bundleOrCreate(), stylerFunction)
				}
				// If a styler function was not specified, but the body was
				// a primitive that has a bootstrapStyler, use that just as
				// though it had been specified.
				addBootstrapStyler(function.code(), atom, loader.module)
				prefixFunctions.forEachIndexed { zeroIndex, prefixFunction ->
					prefixFunction.code().methodName = stringFrom(
						"Macro prefix #${zeroIndex + 1} of $atomName")
				}
				function.code().methodName = stringFrom(
					"Macro body of $atomName")
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
		functionType(
			tuple(
				ATOM.o,
				zeroOrMoreOf(mostGeneralFunctionType()),
				functionTypeReturning(PARSE_PHRASE.mostGeneralType),
				zeroOrOneOf(A_Styler.stylerFunctionType)),
			TOP.o)

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(
			set(
				E_LOADING_IS_OVER,
				E_CANNOT_DEFINE_DURING_COMPILATION,
				E_INCORRECT_NUMBER_OF_ARGUMENTS,
				E_REDEFINED_WITH_SAME_ARGUMENT_TYPES,
				E_MACRO_PREFIX_FUNCTION_ARGUMENT_MUST_BE_A_PHRASE,
				E_MACRO_PREFIX_FUNCTIONS_MUST_RETURN_TOP,
				E_MACRO_ARGUMENT_MUST_BE_A_PHRASE,
				E_MACRO_MUST_RETURN_A_PHRASE,
				E_MACRO_PREFIX_FUNCTION_INDEX_OUT_OF_BOUNDS,
				E_STYLER_ALREADY_SET_BY_THIS_MODULE
			).setUnionCanDestroy(possibleErrors, true))

	override fun bootstrapStyler() = P_BootstrapDefinitionStyler
}
