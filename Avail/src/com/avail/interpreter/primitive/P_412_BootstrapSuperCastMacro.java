/**
 * P_412_BootstrapSuperCastMacro.java
 * Copyright © 1993-2015, The Avail Foundation, LLC.
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
import com.avail.interpreter.*;

/**
 * The {@code P_412_BootstrapSuperCastMacro} primitive is used to create a
 * {@linkplain SuperCastNodeDescriptor super-cast phrase}.  This is used to
 * control method lookup, and is a generalization of the concept of {@code
 * super} found in some object-oriented languages.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class P_412_BootstrapSuperCastMacro extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	public final static Primitive instance =
		new P_412_BootstrapSuperCastMacro().init(
			1, CannotFail, Bootstrap);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter,
		final boolean skipReturnCheck)
	{
		assert args.size() == 2;
		final A_Phrase expressionNode = args.get(0);
		final A_Phrase typeLiteral = args.get(1);

		final A_Type type = typeLiteral.token().literal();
		if (type.isTop() || type.isBottom())
		{
			throw new AvailRejectedParseException(
				"supercast type to be something other than " + type);
		}
		final A_Type expressionType = expressionNode.expressionType();
		if (!expressionType.isSubtypeOf(type) || expressionType.equals(type))
		{
			throw new AvailRejectedParseException(
				"supercast type ("
				+ type
				+ ") to be a (strict) supertype of the expression's type ("
				+ expressionType
				+ ")");
		}
		final A_Phrase superCast =
			SuperCastNodeDescriptor.create(expressionNode, type);
		superCast.makeImmutable();
		return interpreter.primitiveSuccess(superCast);
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return FunctionTypeDescriptor.create(
			TupleDescriptor.from(
				EXPRESSION_NODE.create(ANY.o()),
				LITERAL_NODE.create(InstanceMetaDescriptor.anyMeta())),
			SUPER_CAST_NODE.mostGeneralType());
	}
}
