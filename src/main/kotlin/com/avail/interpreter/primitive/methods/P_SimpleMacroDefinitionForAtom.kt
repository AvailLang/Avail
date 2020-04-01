/*
 * P_SimpleMacroDefinitionForAtom.kt
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
import com.avail.compiler.splitter.MessageSplitter.Metacharacter
import com.avail.descriptor.NilDescriptor.nil
import com.avail.descriptor.functions.FunctionDescriptor
import com.avail.descriptor.phrases.PhraseDescriptor
import com.avail.descriptor.sets.SetDescriptor.set
import com.avail.descriptor.tuples.ObjectTupleDescriptor.tuple
import com.avail.descriptor.tuples.StringDescriptor.stringFrom
import com.avail.descriptor.tuples.TupleDescriptor
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.AbstractEnumerationTypeDescriptor.enumerationWith
import com.avail.descriptor.types.FunctionTypeDescriptor.*
import com.avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.PARSE_PHRASE
import com.avail.descriptor.types.TupleTypeDescriptor.zeroOrMoreOf
import com.avail.descriptor.types.TypeDescriptor.Types.ATOM
import com.avail.descriptor.types.TypeDescriptor.Types.TOP
import com.avail.exceptions.AvailErrorCode.*
import com.avail.exceptions.AvailException
import com.avail.exceptions.MalformedMessageException
import com.avail.interpreter.Interpreter
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Flag.CanSuspend
import com.avail.interpreter.Primitive.Flag.Unknown

/**
 * **Primitive:** Simple macro definition.  The first argument is the macro
 * name, and the second argument is a [tuple][TupleDescriptor] of
 * [functions][FunctionDescriptor] returning ⊤, one for each occurrence of a
 * [section sign][Metacharacter.SECTION_SIGN] (§) in the macro name.  The third
 * argument is the function to invoke for the complete macro.  It is constrained
 * to answer a [phrase][PhraseDescriptor].
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object P_SimpleMacroDefinitionForAtom : Primitive(3, CanSuspend, Unknown)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(3)
		val atom = interpreter.argument(0)
		val prefixFunctions = interpreter.argument(1)
		val function = interpreter.argument(2)

		val fiber = interpreter.fiber()
		val loader = fiber.availLoader() ?:
			return interpreter.primitiveFailure(E_LOADING_IS_OVER)
		if (!loader.phase().isExecuting)
		{
			return interpreter.primitiveFailure(
				E_CANNOT_DEFINE_DURING_COMPILATION)
		}
		for (prefixFunction in prefixFunctions)
		{
			val numArgs = prefixFunction.code().numArgs()
			val kind = prefixFunction.kind()
			val argsKind = kind.argsTupleType()
			for (argIndex in 1 .. numArgs)
			{
				if (!argsKind.typeAtIndex(argIndex)
						.isSubtypeOf(PARSE_PHRASE.mostGeneralType()))
				{
					return interpreter.primitiveFailure(
						E_MACRO_PREFIX_FUNCTION_ARGUMENT_MUST_BE_A_PARSE_NODE)
				}
			}
			if (!kind.returnType().isTop)
			{
				return interpreter.primitiveFailure(
					E_MACRO_PREFIX_FUNCTIONS_MUST_RETURN_TOP)
			}
		}
		try
		{
			val splitter = atom.bundleOrCreate().messageSplitter()
			if (prefixFunctions.tupleSize() !=
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
		val argsKind = kind.argsTupleType()
		for (argIndex in 1 .. numArgs)
		{
			if (!argsKind.typeAtIndex(argIndex).isSubtypeOf(
					PARSE_PHRASE.mostGeneralType()))
			{
				return interpreter.primitiveFailure(
					E_MACRO_ARGUMENT_MUST_BE_A_PARSE_NODE)
			}
		}
		if (!kind.returnType().isSubtypeOf(PARSE_PHRASE.mostGeneralType()))
		{
			return interpreter.primitiveFailure(
				E_MACRO_MUST_RETURN_A_PARSE_NODE)
		}

		return interpreter.suspendAndDoInLevelOneSafe {
			toSucceed, toFail ->
			try
			{
				loader.addMacroBody(atom, function, prefixFunctions)
				val atomName = atom.atomName()
				for ((zeroIndex, prefixFunction) in prefixFunctions.withIndex())
				{
					prefixFunction.code().setMethodName(
						stringFrom("Macro prefix #${zeroIndex+1} of $atomName"))
				}
				function.code().setMethodName(
					stringFrom("Macro body of $atomName"))
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
		functionType(
			tuple(
				ATOM.o(),
				zeroOrMoreOf(mostGeneralFunctionType()),
				functionTypeReturning(PARSE_PHRASE.mostGeneralType())),
			TOP.o())

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(set(
				E_LOADING_IS_OVER, E_CANNOT_DEFINE_DURING_COMPILATION,
				E_INCORRECT_NUMBER_OF_ARGUMENTS,
				E_REDEFINED_WITH_SAME_ARGUMENT_TYPES,
				E_MACRO_PREFIX_FUNCTION_ARGUMENT_MUST_BE_A_PARSE_NODE,
				E_MACRO_PREFIX_FUNCTIONS_MUST_RETURN_TOP,
				E_MACRO_ARGUMENT_MUST_BE_A_PARSE_NODE,
				E_MACRO_MUST_RETURN_A_PARSE_NODE,
				E_MACRO_PREFIX_FUNCTION_INDEX_OUT_OF_BOUNDS)
            .setUnionCanDestroy(possibleErrors, true))
}
