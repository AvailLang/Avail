/*
 * P_SimpleLexerDefinitionForAtom.kt
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

package avail.interpreter.primitive.methods

import avail.compiler.ModuleManifestEntry
import avail.compiler.ModuleManifestEntry.Kind
import avail.compiler.splitter.MessageSplitter.Companion.possibleErrors
import avail.descriptor.atoms.A_Atom.Companion.atomName
import avail.descriptor.atoms.A_Atom.Companion.bundleOrCreate
import avail.descriptor.bundles.A_Bundle.Companion.bundleMethod
import avail.descriptor.functions.A_RawFunction.Companion.codeStartingLineNumber
import avail.descriptor.functions.A_RawFunction.Companion.methodName
import avail.descriptor.methods.MethodDescriptor.SpecialMethodAtom
import avail.descriptor.parsing.LexerDescriptor.Companion.lexerBodyFunctionType
import avail.descriptor.parsing.LexerDescriptor.Companion.lexerFilterFunctionType
import avail.descriptor.parsing.LexerDescriptor.Companion.newLexer
import avail.descriptor.phrases.A_Phrase.Companion.startingLineNumber
import avail.descriptor.representation.NilDescriptor.Companion.nil
import avail.descriptor.sets.A_Set.Companion.setUnionCanDestroy
import avail.descriptor.sets.SetDescriptor.Companion.set
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.tuples.StringDescriptor.Companion.stringFrom
import avail.descriptor.types.A_Type
import avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.ATOM
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.TOP
import avail.exceptions.AvailErrorCode.E_CANNOT_DEFINE_DURING_COMPILATION
import avail.exceptions.AvailErrorCode.E_LOADING_IS_OVER
import avail.exceptions.MalformedMessageException
import avail.interpreter.Primitive
import avail.interpreter.Primitive.Flag.CanSuspend
import avail.interpreter.Primitive.Flag.Unknown
import avail.interpreter.effects.LoadingEffectToRunPrimitive
import avail.interpreter.execution.AvailLoader.Phase.EXECUTING_FOR_COMPILE
import avail.interpreter.execution.Interpreter

/**
 * **Primitive:** Simple lexer definition.  The first argument is the lexer name
 * (an atom).  The second argument is a (stable, pure) filter function which
 * takes a character and answers true if the lexer should run when that
 * character is encountered in the input, otherwise false.  The third argument
 * is the body function, which takes a character, the source string, and the
 * current (one-based) index into it.  It may invoke a primitive to accept zero
 * or more lexings and/or a primitive to reject the lexing with a diagnostic
 * message.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
@Suppress("unused")
object P_SimpleLexerDefinitionForAtom : Primitive(3, CanSuspend, Unknown)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(3)
		val atom = interpreter.argument(0)
		val filterFunction = interpreter.argument(1)
		val bodyFunction = interpreter.argument(2)

		val fiber = interpreter.fiber()
		val loader = fiber.availLoader() ?:
			return interpreter.primitiveFailure(E_LOADING_IS_OVER)
		if (!loader.phase().isExecuting)
		{
			return interpreter.primitiveFailure(
				E_CANNOT_DEFINE_DURING_COMPILATION)
		}
		val bundle = try
		{
			atom.bundleOrCreate()
		}
		catch (e: MalformedMessageException)
		{
			return interpreter.primitiveFailure(e.errorCode)
		}
		val method = bundle.bundleMethod
		val lexer = newLexer(
			filterFunction, bodyFunction, method, loader.module)

		return interpreter.suspendInLevelOneSafeThen {
			filterFunction.code().methodName =
				stringFrom("Filter for lexer ${atom.atomName}")
			bodyFunction.code().methodName =
				stringFrom("Body for lexer ${atom.atomName}")
			// Updating the lexical scanner is cheap enough that we do it even
			// when we're loading, not just compiling.  This allows us to parse
			// and run entry points in the scope of the module, without having
			// to generate the lexicalScanner for a module that has already been
			// closed (ModuleDescriptor.State.Loaded).
			if (loader.phase() == EXECUTING_FOR_COMPILE)
			{
				loader.lexicalScanner().addLexer(lexer)
				loader.manifestEntries!!.add(
					ModuleManifestEntry(
						Kind.LEXER_KIND,
						atom.atomName.asNativeString(),
						loader.topLevelStatementBeingCompiled!!
							.startingLineNumber,
						bodyFunction.code().codeStartingLineNumber,
						bodyFunction))
			}
			loader.recordEffect(
				LoadingEffectToRunPrimitive(
					SpecialMethodAtom.LEXER_DEFINER.bundle,
					atom,
					filterFunction,
					bodyFunction))
			succeed(nil)
		}
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(
				ATOM.o, lexerFilterFunctionType(), lexerBodyFunctionType()),
			TOP.o)

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(set(
				E_LOADING_IS_OVER,
				E_CANNOT_DEFINE_DURING_COMPILATION)
			.setUnionCanDestroy(possibleErrors, true))
}
