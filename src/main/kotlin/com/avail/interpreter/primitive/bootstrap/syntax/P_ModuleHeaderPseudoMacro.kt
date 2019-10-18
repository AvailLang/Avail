/*
 * P_ModuleHeaderPseudoMacro.kt
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

package com.avail.interpreter.primitive.bootstrap.syntax

import com.avail.descriptor.A_Type
import com.avail.descriptor.EnumerationTypeDescriptor.booleanType
import com.avail.descriptor.ExpressionAsStatementPhraseDescriptor.newExpressionAsStatement
import com.avail.descriptor.FunctionTypeDescriptor.functionType
import com.avail.descriptor.IntegerRangeTypeDescriptor.inclusive
import com.avail.descriptor.ListPhraseDescriptor.newListNode
import com.avail.descriptor.ListPhraseTypeDescriptor.*
import com.avail.descriptor.MethodDescriptor.SpecialMethodAtom
import com.avail.descriptor.MethodDescriptor.SpecialMethodAtom.MODULE_HEADER
import com.avail.descriptor.ObjectTupleDescriptor.tupleFromArray
import com.avail.descriptor.PhraseTypeDescriptor.Constants.stringLiteralType
import com.avail.descriptor.PhraseTypeDescriptor.PhraseKind
import com.avail.descriptor.PhraseTypeDescriptor.PhraseKind.LITERAL_PHRASE
import com.avail.descriptor.PhraseTypeDescriptor.PhraseKind.STATEMENT_PHRASE
import com.avail.descriptor.SendPhraseDescriptor.newSendNode
import com.avail.descriptor.TupleDescriptor.emptyTuple
import com.avail.descriptor.TypeDescriptor.Types.TOP
import com.avail.interpreter.Interpreter
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Flag.*

/**
 * The `P_ModuleHeaderPseudoMacro` primitive is used to parse module
 * headers.  When this primitive is invoked, it should yield a [ ][PhraseKind.STATEMENT_PHRASE].  The method is private, and used to parse the
 * headers of modules with the same machinery used for the bodies.
 *
 *
 * The name of the module header method is given in [ ][SpecialMethodAtom.MODULE_HEADER].
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
object P_ModuleHeaderPseudoMacro : Primitive(6, Private, Bootstrap, CannotFail, CanInline)
{

	override fun attempt(
		interpreter: Interpreter): Result
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
					emptyTuple(),
					MODULE_HEADER.bundle,
					newListNode(
						tupleFromArray(
							moduleNameLiteral,
							optionalVersions,
							allImports,
							optionalNames,
							optionalEntries,
							optionalPragmas)),
					TOP.o())))
	}

	override fun privateBlockTypeRestriction(): A_Type
	{
		return functionType(
			tupleFromArray(
				/* Module name */
				stringLiteralType,
				/* Optional versions */
				zeroOrOneList(zeroOrMoreList(stringLiteralType)),
				/* All imports */
				zeroOrMoreList(
					list(
						LITERAL_PHRASE.create(
							inclusive(1, 2)),
						zeroOrMoreList(
							list(
								// Imported module name
								stringLiteralType,
								// Imported module versions
								zeroOrOneList(
									zeroOrMoreList(stringLiteralType)),
								// Imported names
								zeroOrOneList(
									list(
										zeroOrMoreList(
											list(
												// Negated import
												LITERAL_PHRASE.create(
													booleanType()),
												// Name
												stringLiteralType,
												// Replacement name
												zeroOrOneList(
													stringLiteralType))),
										// Final ellipsis (import all the rest)
										LITERAL_PHRASE.create(
											booleanType()))))))),
				/* Optional names */
				zeroOrOneList(zeroOrMoreList(stringLiteralType)),
				/* Optional entries */
				zeroOrOneList(zeroOrMoreList(stringLiteralType)),
				/* Optional pragma */
				zeroOrOneList(zeroOrMoreList(stringLiteralType))),
			/* Shouldn't be invoked, so always fail. */
			STATEMENT_PHRASE.mostGeneralType())
	}

}