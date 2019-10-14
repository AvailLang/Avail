/*
 * P_SimpleLexerDefinitionForAtom.java
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

import com.avail.AvailRuntime.currentRuntime
import com.avail.AvailTask
import com.avail.compiler.splitter.MessageSplitter.Companion.possibleErrors
import com.avail.descriptor.A_Bundle
import com.avail.descriptor.A_Type
import com.avail.descriptor.AbstractEnumerationTypeDescriptor.enumerationWith
import com.avail.descriptor.FunctionTypeDescriptor.functionType
import com.avail.descriptor.LexerDescriptor.*
import com.avail.descriptor.MethodDescriptor.SpecialMethodAtom
import com.avail.descriptor.NilDescriptor.nil
import com.avail.descriptor.ObjectTupleDescriptor.tuple
import com.avail.descriptor.SetDescriptor.set
import com.avail.descriptor.StringDescriptor.formatString
import com.avail.descriptor.TypeDescriptor.Types.ATOM
import com.avail.descriptor.TypeDescriptor.Types.TOP
import com.avail.exceptions.AvailErrorCode.E_CANNOT_DEFINE_DURING_COMPILATION
import com.avail.exceptions.AvailErrorCode.E_LOADING_IS_OVER
import com.avail.exceptions.MalformedMessageException
import com.avail.interpreter.AvailLoader.Phase.EXECUTING_FOR_COMPILE
import com.avail.interpreter.Interpreter
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Flag.CanSuspend
import com.avail.interpreter.Primitive.Flag.Unknown
import com.avail.interpreter.Primitive.Result.FIBER_SUSPENDED
import com.avail.interpreter.effects.LoadingEffectToRunPrimitive
import com.avail.utility.Nulls.stripNull

/**
 * **Primitive:** Simple lexer definition.  The first argument
 * is the lexer name (an atom).  The second argument is a (stable, pure) filter
 * function which takes a character and answers true if the lexer should run
 * when that character is encountered in the input, otherwise false.  The third
 * argument is the body function, which takes a character, the source string,
 * and the current (one-based) index into it.  It may invoke a primitive to
 * accept zero or more lexings and/or a primitive to reject the lexing with a
 * diagnostic message.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
object P_SimpleLexerDefinitionForAtom : Primitive(3, CanSuspend, Unknown)
{

	override fun attempt(
		interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(3)
		val atom = interpreter.argument(0)
		val filterFunction = interpreter.argument(1)
		val bodyFunction = interpreter.argument(2)

		val fiber = interpreter.fiber()
		val loader = fiber.availLoader()
		             ?: return interpreter.primitiveFailure(E_LOADING_IS_OVER)
		if (!loader.phase().isExecuting)
		{
			return interpreter.primitiveFailure(
				E_CANNOT_DEFINE_DURING_COMPILATION)
		}
		val bundle: A_Bundle
		try
		{
			bundle = atom.bundleOrCreate()
		}
		catch (e: MalformedMessageException)
		{
			return interpreter.primitiveFailure(e.errorCode)
		}

		val method = bundle.bundleMethod()
		val lexer = newLexer(
			filterFunction, bodyFunction, method, loader.module())
		val primitiveFunction = stripNull(interpreter.function)
		interpreter.primitiveSuspend(primitiveFunction)
		interpreter.runtime().whenLevelOneSafeDo(
			fiber.priority(),
			AvailTask.forUnboundFiber(
				fiber
			) {
				filterFunction.code().setMethodName(
					formatString("Filter for lexer %s",
					             atom.atomName()))
				bodyFunction.code().setMethodName(
					formatString("Body for lexer %s", atom.atomName()))
				// Only update the loader's lexical scanner if we're
				// actually compiling, NOT if we're loading.  The loader
				// doesn't even have a lexical scanner during loading.
				if (loader.phase() == EXECUTING_FOR_COMPILE)
				{
					loader.lexicalScanner().addLexer(lexer)
				}
				loader.recordEffect(
					LoadingEffectToRunPrimitive(
						SpecialMethodAtom.LEXER_DEFINER.bundle,
						atom,
						filterFunction,
						bodyFunction))
				Interpreter.resumeFromSuccessfulPrimitive(
					currentRuntime(), fiber, this, nil)
			})
		return FIBER_SUSPENDED
	}

	override fun privateBlockTypeRestriction(): A_Type
	{
		return functionType(
			tuple(
				ATOM.o(),
				lexerFilterFunctionType(),
				lexerBodyFunctionType()),
			TOP.o())
	}

	override fun privateFailureVariableType(): A_Type
	{
		return enumerationWith(
			set(
				E_LOADING_IS_OVER,
				E_CANNOT_DEFINE_DURING_COMPILATION
			).setUnionCanDestroy(possibleErrors, true))
	}

}