/**
 * P_406_BootstrapConstantDeclarationMacro.java
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
import static com.avail.descriptor.TokenDescriptor.TokenType.*;
import static com.avail.interpreter.Primitive.Flag.*;
import java.util.*;
import com.avail.compiler.AvailRejectedParseException;
import com.avail.descriptor.*;
import com.avail.interpreter.*;

/**
 * The {@code P_406_BootstrapConstantDeclarationMacro} primitive is used
 * for bootstrapping declaration of a {@link #LOCAL_CONSTANT_NODE local
 * constant declaration}.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public class P_406_BootstrapConstantDeclarationMacro extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	public final static Primitive instance =
		new P_406_BootstrapConstantDeclarationMacro().init(
			2, CannotFail, Bootstrap);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter,
		final boolean skipReturnCheck)
	{
		assert args.size() == 2;
		final A_Phrase constantNameLiteralNode = args.get(0);
		final A_Phrase initializationExpression = args.get(1);

		assert constantNameLiteralNode.isInstanceOfKind(
			LITERAL_NODE.mostGeneralType());
		final A_Token syntheticLiteralNameToken =
			constantNameLiteralNode.token();
		assert syntheticLiteralNameToken.isLiteralToken();
		assert syntheticLiteralNameToken.tokenType() == SYNTHETIC_LITERAL;
		final A_Token innerNameToken = syntheticLiteralNameToken.literal();
		assert innerNameToken.isInstanceOfKind(TOKEN.o());

		if (innerNameToken.tokenType() != KEYWORD)
		{
			throw new AvailRejectedParseException(
				StringDescriptor.from(
					"Constant name to be alphanumeric"));
		}
		final A_Type initializationType =
			initializationExpression.expressionType();
		if (initializationType.equals(TOP.o()))
		{
			throw new AvailRejectedParseException(
				StringDescriptor.from(
					"constant initialization expression to have a type"
					+ " other than ⊤"));
		}
		if (initializationType.equals(BottomTypeDescriptor.bottom()))
		{
			throw new AvailRejectedParseException(
				StringDescriptor.from(
					"constant initialization expression to have a type"
					+ " other than ⊥"));
		}

		final A_Phrase constantDeclaration =
			DeclarationNodeDescriptor.newConstant(
				innerNameToken,
				initializationExpression);
		constantDeclaration.makeImmutable();
		return interpreter.primitiveSuccess(constantDeclaration);
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return FunctionTypeDescriptor.create(
			TupleDescriptor.from(
				/* Constant name token as a literal node */
				LITERAL_NODE.create(TOKEN.o()),
				/* Initialization expression */
				EXPRESSION_NODE.create(ANY.o())),
			LOCAL_CONSTANT_NODE.mostGeneralType());
	}
}
