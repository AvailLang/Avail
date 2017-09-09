/**
 * P_ModuleHeaderPseudoMacro.java
 * Copyright © 1993-2017, The Avail Foundation, LLC.
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

package com.avail.interpreter.primitive.bootstrap.syntax;

import com.avail.descriptor.A_Phrase;
import com.avail.descriptor.A_Type;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.MethodDescriptor.SpecialMethodAtom;
import com.avail.descriptor.ParseNodeTypeDescriptor.ParseNodeKind;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;

import java.util.List;

import static com.avail.descriptor.EnumerationTypeDescriptor.booleanType;
import static com.avail.descriptor.ExpressionAsStatementNodeDescriptor
	.newExpressionAsStatement;
import static com.avail.descriptor.FunctionTypeDescriptor.functionType;
import static com.avail.descriptor.IntegerRangeTypeDescriptor.inclusive;
import static com.avail.descriptor.ListNodeDescriptor.newListNode;
import static com.avail.descriptor.ListNodeTypeDescriptor.createListNodeType;
import static com.avail.descriptor.LiteralTokenTypeDescriptor.literalTokenType;
import static com.avail.descriptor.MethodDescriptor.SpecialMethodAtom
	.MODULE_HEADER_METHOD;
import static com.avail.descriptor.ParseNodeTypeDescriptor.ParseNodeKind.*;
import static com.avail.descriptor.SendNodeDescriptor.newSendNode;
import static com.avail.descriptor.TupleDescriptor.emptyTuple;
import static com.avail.descriptor.TupleDescriptor.tuple;
import static com.avail.descriptor.TupleTypeDescriptor.*;
import static com.avail.descriptor.TypeDescriptor.Types.TOP;
import static com.avail.interpreter.Primitive.Flag.*;

/**
 * The {@code P_ModuleHeaderPseudoMacro} primitive is used to parse module
 * headers.  When this primitive is invoked, it should yield a {@link
 * ParseNodeKind#STATEMENT_NODE}.  The method is private, and used to parse the
 * headers of modules with the same machinery used for the bodies.
 *
 * <p>The name of the module header method is given in {@link
 * SpecialMethodAtom#MODULE_HEADER_METHOD}.</p>
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class P_ModuleHeaderPseudoMacro extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	public static final Primitive instance =
		new P_ModuleHeaderPseudoMacro().init(
			6, Private, Bootstrap, CannotFail, CanInline);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter,
		final boolean skipReturnCheck)
	{
		assert args.size() == 6;
		final A_Phrase moduleNameLiteral = args.get(0);
		final A_Phrase optionalVersions = args.get(1);
		final A_Phrase allImports = args.get(2);
		final A_Phrase optionalNames = args.get(3);
		final A_Phrase optionalEntries = args.get(4);
		final A_Phrase optionalPragmas = args.get(5);

		return interpreter.primitiveSuccess(
			newExpressionAsStatement(
				newSendNode(
					// Don't bother collecting tokens in header.
					emptyTuple(),
					MODULE_HEADER_METHOD.bundle,
					newListNode(
						tuple(moduleNameLiteral, optionalVersions,
							allImports, optionalNames, optionalEntries,
							optionalPragmas)),
					TOP.o())));
	}

	static A_Type zeroOrMoreList (final A_Type type)
	{
		return createListNodeType(LIST_NODE, zeroOrMoreOf(type));
	}

	static A_Type zeroOrOneList (final A_Type type)
	{
		return createListNodeType(LIST_NODE, zeroOrOneOf(type));
	}

	static A_Type list (final A_Type... types)
	{
		return createListNodeType(LIST_NODE, tupleTypeForTypes(types));
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		final A_Type stringTokenType =
			LITERAL_NODE.create(
				literalTokenType(
					stringType()));
		return functionType(
			tuple(
				/* Module name */
				stringTokenType,
				/* Optional versions */
				zeroOrOneList(zeroOrMoreList(stringTokenType)),
				/* All imports */
				zeroOrMoreList(
					list(
						LITERAL_NODE.create(
							inclusive(1, 2)),
						zeroOrMoreList(
							list(
								// Imported module name
								stringTokenType,
								// Imported module versions
								zeroOrOneList(zeroOrMoreList(stringTokenType)),
								// Imported names
								zeroOrOneList(
									list(
										zeroOrMoreList(
											list(
												// Negated import
												LITERAL_NODE.create(
													booleanType()),
												// Name
												stringTokenType,
												// Replacement name
												zeroOrOneList(
													stringTokenType))),
										// Final ellipsis (import all the rest)
										LITERAL_NODE.create(
											booleanType()))))))),
				/* Optional names */
				zeroOrOneList(zeroOrMoreList(stringTokenType)),
				/* Optional entries */
				zeroOrOneList(zeroOrMoreList(stringTokenType)),
				/* Optional pragma */
				zeroOrOneList(zeroOrMoreList(stringTokenType))),
			/* Shouldn't be invoked, so always fail. */
			STATEMENT_NODE.mostGeneralType());
	}
}
