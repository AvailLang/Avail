/**
 * P_410_BootstrapVariableUseMacro.java
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
import static com.avail.interpreter.Primitive.Flag.*;
import java.util.*;
import com.avail.compiler.AvailRejectedParseException;
import com.avail.descriptor.*;
import com.avail.descriptor.TokenDescriptor.TokenType;
import com.avail.interpreter.*;

/**
 * The {@code P_410_BootstrapVariableUseMacro} primitive is used to create
 * {@link VariableUseNodeDescriptor variable use} phrases.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public class P_410_BootstrapVariableUseMacro extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	public final static Primitive instance =
		new P_410_BootstrapVariableUseMacro().init(
			1, CannotFail, Bootstrap);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter)
	{
		assert args.size() == 1;
		final AvailObject variableNameLiteral = args.get(0);
		assert variableNameLiteral.isInstanceOf(LITERAL_NODE.mostGeneralType());
		final AvailObject literalToken = variableNameLiteral.token();
		assert literalToken.tokenType() == TokenType.SYNTHETIC_LITERAL;
		final AvailObject actualToken = literalToken.literal();
		assert actualToken.isInstanceOf(TOKEN.o());

		if (actualToken.tokenType() != TokenType.KEYWORD)
		{
			throw new AvailRejectedParseException(
				StringDescriptor.from(
					"variable name to be a keyword token"));
		}
		final AvailObject variableNameString = actualToken.string();
		final AvailObject clientData =
			interpreter.currentParserState.clientDataMap;
		final AvailObject scopeMap =
			clientData.mapAt(AtomDescriptor.compilerScopeMapKey());
		if (scopeMap.hasKey(variableNameString))
		{
			final AvailObject variableUse = VariableUseNodeDescriptor.newUse(
				actualToken,
				scopeMap.mapAt(variableNameString));
			variableUse.makeImmutable();
			return interpreter.primitiveSuccess(variableUse);
		}
		// Not in a block scope. See if it's a module variable or module
		// constant...
		final AvailObject module =
			interpreter.currentParserState.currentModule();
		if (module.variableBindings().hasKey(variableNameString))
		{
			final AvailObject variableObject = module.variableBindings().mapAt(
				variableNameString);
			final AvailObject moduleVarDecl =
				DeclarationNodeDescriptor.newModuleVariable(
					actualToken,
					variableObject,
					NilDescriptor.nil());
			final AvailObject variableUse = VariableUseNodeDescriptor.newUse(
				actualToken,
				moduleVarDecl);
			variableUse.makeImmutable();
			return interpreter.primitiveSuccess(variableUse);
		}
		if (!module.constantBindings().hasKey(variableNameString))
		{
			throw new AvailRejectedParseException(
				StringDescriptor.from(
					"variable "
					+ variableNameString.toString()
					+ " to be in scope"));
		}
		final AvailObject variableObject = module.constantBindings().mapAt(
			variableNameString);
		final AvailObject moduleConstDecl =
			DeclarationNodeDescriptor.newModuleConstant(
				actualToken,
				variableObject,
				NilDescriptor.nil());
		final AvailObject variableUse = VariableUseNodeDescriptor.newUse(
			actualToken,
			moduleConstDecl);
		return interpreter.primitiveSuccess(variableUse);
	}

	@Override
	protected AvailObject privateBlockTypeRestriction ()
	{
		return FunctionTypeDescriptor.create(
			TupleDescriptor.from(
				/* Variable name */
				LITERAL_NODE.create(TOKEN.o())),
			VARIABLE_USE_NODE.mostGeneralType());
	}
}
