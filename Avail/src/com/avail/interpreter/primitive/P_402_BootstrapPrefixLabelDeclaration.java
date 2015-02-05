/**
 * P_402_BootstrapPrefixLabelDeclaration.java
 * Copyright © 1993-2014, The Avail Foundation, LLC.
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

package com.avail.interpreter.primitive;

import static com.avail.descriptor.TypeDescriptor.Types.*;
import static com.avail.descriptor.ParseNodeTypeDescriptor.ParseNodeKind.*;
import static com.avail.interpreter.Primitive.Flag.*;
import java.util.*;
import com.avail.descriptor.*;
import com.avail.interpreter.*;

/**
 * The {@code P_402_BootstrapPrefixLabelDeclaration} primitive is used
 * for bootstrapping declaration of a {@link #PRIMITIVE_FAILURE_REASON_NODE
 * primitive failure variable} which holds the reason for a primitive's failure.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class P_402_BootstrapPrefixLabelDeclaration
extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	public final static Primitive instance =
		new P_402_BootstrapPrefixLabelDeclaration().init(
			3, CannotFail, Bootstrap);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter,
		final boolean skipReturnCheck)
	{
		assert args.size() == 3;
		final A_Token failureVariableName = args.get(0);
		final A_Type type = args.get(1);

		final A_Phrase failureDeclaration =
			DeclarationNodeDescriptor.newPrimitiveFailureVariable(
				failureVariableName,
				type);
		failureDeclaration.makeImmutable();
		return interpreter.primitiveSuccess(failureDeclaration);
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return FunctionTypeDescriptor.create(
			TupleDescriptor.from(
				/* Macro argument is a parse node. */
				LIST_NODE.create(
					/* Optional arguments section. */
					TupleTypeDescriptor.zeroOrOneOf(
						/* Arguments are present. */
						TupleTypeDescriptor.oneOrMoreOf(
							/* An argument. */
							TupleTypeDescriptor.forTypes(
								/* Argument name, a literal node holding a
								 * synthetic token holding the real token.
								 */
								LITERAL_NODE.create(
									/* The outer synthetic literal token. */
									LiteralTokenTypeDescriptor.create(
										/* Inner original token. */
										TOKEN.o())),
								/* Argument type. */
								LITERAL_NODE.create(
									/* The synthetic literal token. */
									LiteralTokenTypeDescriptor.create(
										/* Holding the type. */
										InstanceMetaDescriptor.anyMeta())))))),
				/* Macro argument is a parse node. */
				LIST_NODE.create(
					/* Optional primitive failure variable declaration. */
					TupleTypeDescriptor.zeroOrOneOf(
						/* Primitive failure variable parts. */
						TupleTypeDescriptor.forTypes(
							/* Primitive failure variable name token */
							TOKEN.o(),
							/* Primitive failure variable type */
							LITERAL_NODE.create(
								/* The synthetic literal token. */
								LiteralTokenTypeDescriptor.create(
									/* Holding the failure var's type. */
									InstanceMetaDescriptor.anyMeta()))))),
				/* Macro argument is a parse node. */
				LIST_NODE.create(
					/* Optional label declaration. */
					TupleTypeDescriptor.zeroOrOneOf(
						/* Primitive label type. */
						LITERAL_NODE.create(
							/* The synthetic literal token. */
							LiteralTokenTypeDescriptor.create(
								/* Holding the label's return type. */
								InstanceMetaDescriptor.anyMeta()))))),
			TOP.o());
	}
}
