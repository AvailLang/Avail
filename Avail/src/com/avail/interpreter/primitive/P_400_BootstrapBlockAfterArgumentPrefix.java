/**
 * P_400_BootstrapBlockAfterArgumentPrefix.java
 * Copyright © 1993-2012, Mark van Gulik and Todd L Smith.
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
import static com.avail.exceptions.AvailErrorCode.*;
import static com.avail.interpreter.Primitive.Flag.*;
import java.util.*;
import com.avail.descriptor.*;
import com.avail.exceptions.AvailErrorCode;
import com.avail.interpreter.*;

/**
 * The {@code P_400_BootstrapBlockAfterArgumentPrefix} primitive is used as a
 * prefix function for bootstrapping argument declarations within a {@link
 * P_404_BootstrapBlockMacro block}.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class P_400_BootstrapBlockAfterArgumentPrefix extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	public final static Primitive instance =
		new P_400_BootstrapBlockAfterArgumentPrefix().init(
			1, CannotFail, Bootstrap);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter,
		final boolean skipReturnCheck)
	{
		assert args.size() == 1;
		final A_Tuple allBlockArguments = args.get(0);
		final AvailLoader loader = interpreter.fiber().availLoader();
		if (loader == null)
		{
			return interpreter.primitiveFailure(E_LOADING_IS_OVER);
		}

		final A_Tuple latestBlockArgument =
			allBlockArguments.tupleAt(allBlockArguments.tupleSize());
		assert latestBlockArgument.tupleSize() == 2;
		final A_Phrase labelPhrase = latestBlockArgument.tupleAt(1);
		final A_Phrase typePhrase = latestBlockArgument.tupleAt(2);

		assert labelPhrase.isInstanceOfKind(LITERAL_NODE.create(TOKEN.o()));
		assert typePhrase.isInstanceOfKind(
			LITERAL_NODE.create(InstanceMetaDescriptor.anyMeta()));
		final A_Token argToken = labelPhrase.token();
		final A_Type argType = typePhrase.token().literal();
		assert argType.isType();

		if (argType.isTop() || argType.isBottom())
		{
			return interpreter.primitiveFailure(
				E_DECLARATION_TYPE_MUST_NOT_BE_TOP_OR_BOTTOM);
		}

		final A_Phrase argDeclaration =
			DeclarationNodeDescriptor.newArgument(argToken, argType);

		// Add the binding and we're done.
		final AvailErrorCode error = loader.addDeclaration(argDeclaration);
		if (error != null)
		{
			return interpreter.primitiveFailure(error);
		}
		return interpreter.primitiveSuccess(NilDescriptor.nil());
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return FunctionTypeDescriptor.create(
			TupleDescriptor.from(
				/* Optional arguments section */
				TupleTypeDescriptor.zeroOrOneOf(
					/* Arguments are present */
					TupleTypeDescriptor.oneOrMoreOf(
						/* An argument */
						TupleTypeDescriptor.forTypes(
							/* Argument name */
							LITERAL_NODE.create(TOKEN.o()),
							/* Argument type */
							LITERAL_NODE.create(
								InstanceMetaDescriptor.anyMeta()))))),
			TOP.o());
	}
}
