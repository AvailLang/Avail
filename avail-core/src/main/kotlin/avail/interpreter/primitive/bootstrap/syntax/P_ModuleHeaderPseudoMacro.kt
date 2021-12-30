/*
 * P_ModuleHeaderPseudoMacro.kt
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

package avail.interpreter.primitive.bootstrap.syntax

import avail.descriptor.methods.MethodDescriptor.SpecialMethodAtom
import avail.descriptor.methods.MethodDescriptor.SpecialMethodAtom.MODULE_HEADER
import avail.descriptor.phrases.ExpressionAsStatementPhraseDescriptor.Companion.newExpressionAsStatement
import avail.descriptor.phrases.ListPhraseDescriptor.Companion.newListNode
import avail.descriptor.phrases.SendPhraseDescriptor.Companion.newSendNode
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tupleFromArray
import avail.descriptor.tuples.TupleDescriptor.Companion.emptyTuple
import avail.descriptor.types.A_Type
import avail.descriptor.types.EnumerationTypeDescriptor.Companion.booleanType
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.inclusive
import avail.descriptor.types.ListPhraseTypeDescriptor.Companion.list
import avail.descriptor.types.ListPhraseTypeDescriptor.Companion.zeroOrMoreList
import avail.descriptor.types.ListPhraseTypeDescriptor.Companion.zeroOrOneList
import avail.descriptor.types.PhraseTypeDescriptor.Constants.stringLiteralType
import avail.descriptor.types.PhraseTypeDescriptor.PhraseKind
import avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.LITERAL_PHRASE
import avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.STATEMENT_PHRASE
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.TOP
import avail.interpreter.Primitive
import avail.interpreter.Primitive.Flag.Bootstrap
import avail.interpreter.Primitive.Flag.CanInline
import avail.interpreter.Primitive.Flag.CannotFail
import avail.interpreter.Primitive.Flag.Private
import avail.interpreter.execution.Interpreter

/**
 * The `P_ModuleHeaderPseudoMacro` primitive is used to parse module headers.
 * When this primitive is invoked, it should yield a
 * [PhraseKind.STATEMENT_PHRASE].  The method is private, and used to parse the
 * headers of modules with the same machinery used for the bodies.
 *
 *
 * The name of the module header method is given in
 * [SpecialMethodAtom.MODULE_HEADER].
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
@Suppress("unused")
object P_ModuleHeaderPseudoMacro
	: Primitive(6, Private, Bootstrap, CannotFail, CanInline)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(6)
		val moduleNameLiteral = interpreter.argument(0)
		val optionalVersions = interpreter.argument(1)
		val allImports = interpreter.argument(2)
		val optionalNames = interpreter.argument(3)
		val optionalEntries = interpreter.argument(4)
		val optionalPragmas = interpreter.argument(5)

		return interpreter.primitiveSuccess(
			newExpressionAsStatement(
				newSendNode(
					// Don't bother collecting tokens in header.
					emptyTuple,
					MODULE_HEADER.bundle,
					newListNode(
						tupleFromArray(
							moduleNameLiteral,
							optionalVersions,
							allImports,
							optionalNames,
							optionalEntries,
							optionalPragmas)),
					TOP.o)))
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tupleFromArray(
				// Module name.
				stringLiteralType,
				// Optional versions.
				zeroOrOneList(zeroOrMoreList(stringLiteralType)),
				// All imports.
				zeroOrMoreList(
					list(
						// 1 = Extends, 2 = Uses.
						LITERAL_PHRASE.create(inclusive(1, 2)),
						zeroOrMoreList(
							list(
								// Imported module name.
								stringLiteralType,
								// Imported module versions.
								zeroOrOneList(
									zeroOrMoreList(stringLiteralType)),
								// Imported names.
								zeroOrOneList(list(
									zeroOrMoreList(list(
										// Negated import.
										LITERAL_PHRASE.create(booleanType),
										// Original name.
										stringLiteralType,
										// Replacement name.
										zeroOrOneList(stringLiteralType))),
									// Final ellipsis (import all the rest).
									LITERAL_PHRASE.create(booleanType))))))),
				// Optional names.
				zeroOrOneList(zeroOrMoreList(stringLiteralType)),
				// Optional entries.
				zeroOrOneList(zeroOrMoreList(stringLiteralType)),
				// Optional pragma.
				zeroOrOneList(zeroOrMoreList(stringLiteralType))),
			/* Shouldn't be invoked, so always fail. */
			STATEMENT_PHRASE.mostGeneralType)
}
