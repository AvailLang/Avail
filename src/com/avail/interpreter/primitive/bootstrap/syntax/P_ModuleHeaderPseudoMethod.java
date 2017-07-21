/**
 * P_ModuleHeaderPseudoMethod.java
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

import com.avail.descriptor.*;
import com.avail.descriptor.MethodDescriptor.SpecialMethodAtom;
import com.avail.descriptor.ParseNodeTypeDescriptor.ParseNodeKind;
import com.avail.exceptions.AvailErrorCode;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;

import java.util.List;

import static com.avail.interpreter.Primitive.Flag.Bootstrap;
import static com.avail.interpreter.Primitive.Flag.Private;
import static com.avail.descriptor.TupleTypeDescriptor.*;
import static com.avail.descriptor.EnumerationTypeDescriptor.*;

/**
 * The {@code P_ModuleHeaderPseudoMethod} primitive is used to parse module
 * headers.  This primitive should never actually be invoked, but it's
 * convenient to reuse the lexing/parsing machinery as though the module header
 * were an invocation of a complex macro, specifically this one.
 *
 * <p>The name of the module header method is given in {@link
 * SpecialMethodAtom#MODULE_HEADER_METHOD}.</p>
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class P_ModuleHeaderPseudoMethod extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	public final static Primitive instance =
		new P_ModuleHeaderPseudoMethod().init(
			6, Private, Bootstrap);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter,
		final boolean skipReturnCheck)
	{
		assert args.size() == 6;
//		final A_Phrase moduleNameLiteral = args.get(0);
//		final A_Phrase optionalVersions = args.get(1);
//		final A_Phrase allImports = args.get(2);
//		final A_Phrase optionalNames = args.get(3);
//		final A_Phrase optionalEntries = args.get(4);
//		final A_Phrase optionalPragmas = args.get(5);

		return interpreter.primitiveFailure(AvailErrorCode.E_REQUIRED_FAILURE);
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		final A_Type stringTokenType =
			ParseNodeKind.LITERAL_NODE.create(
				LiteralTokenTypeDescriptor.create(stringType()));
		return FunctionTypeDescriptor.create(
			TupleDescriptor.from(
				/* Module name */
				stringTokenType,
				/* Optional versions */
				zeroOrOneOf(
					zeroOrMoreOf(
						stringTokenType)),
				/* All imports */
				zeroOrMoreOf(
					forTypes(
						IntegerRangeTypeDescriptor.inclusive(1, 2),
						zeroOrMoreOf(
							forTypes(
								// Imported module name
								stringTokenType,
								// Imported module versions
								zeroOrOneOf(
									zeroOrMoreOf(
										stringTokenType)),
								// Imported names
								zeroOrOneOf(
									forTypes(
										zeroOrMoreOf(
											forTypes(
												// Negated import
												booleanObject(),
												// Name
												stringTokenType,
												// Replacement name
												zeroOrOneOf(
													stringTokenType))),
										// Final ellipsis (import all the rest)
										booleanObject())))))),
				/* Optional names */
				zeroOrOneOf(zeroOrMoreOf(stringTokenType)),
				/* Optional entries */
				zeroOrOneOf(zeroOrMoreOf(stringTokenType)),
				/* Optional pragma */
				zeroOrOneOf(zeroOrMoreOf(stringTokenType))),
			/* Shouldn't be invoked, so always fail. */
			BottomTypeDescriptor.bottom());
	}
}
