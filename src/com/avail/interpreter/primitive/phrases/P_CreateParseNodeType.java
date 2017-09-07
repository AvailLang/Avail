/**
 * P_CreateParseNodeType.java
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
package com.avail.interpreter.primitive.phrases;

import com.avail.descriptor.A_Type;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.FunctionTypeDescriptor;
import com.avail.descriptor.InstanceMetaDescriptor;
import com.avail.descriptor.ParseNodeTypeDescriptor;
import com.avail.descriptor.ParseNodeTypeDescriptor.ParseNodeKind;
import com.avail.descriptor.TupleDescriptor;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;

import java.util.List;

import static com.avail.descriptor.FunctionTypeDescriptor.functionType;
import static com.avail.descriptor.InstanceMetaDescriptor.instanceMetaOn;
import static com.avail.descriptor.InstanceMetaDescriptor.topMeta;
import static com.avail.descriptor.InstanceTypeDescriptor.instanceTypeOn;
import static com.avail.descriptor.ParseNodeTypeDescriptor.ParseNodeKind
	.PARSE_NODE;
import static com.avail.descriptor.TupleDescriptor.tuple;
import static com.avail.exceptions.AvailErrorCode.E_BAD_YIELD_TYPE;
import static com.avail.interpreter.Primitive.Flag.CanFold;
import static com.avail.interpreter.Primitive.Flag.CanInline;

/**
 * <strong>Primitive:</strong> Create a variation of a {@linkplain
 * ParseNodeTypeDescriptor parse node type}.  In particular, create a parse
 * node type of the same {@linkplain ParseNodeKind kind} but with the specified
 * expression type.
 */
public final class P_CreateParseNodeType extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	public static final Primitive instance =
		new P_CreateParseNodeType().init(
			2, CanFold, CanInline);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter,
		final boolean skipReturnCheck)
	{
		assert args.size() == 2;
		final AvailObject baseType = args.get(0);
		final AvailObject expressionType = args.get(1);
		if (baseType.isBottom())
		{
			return interpreter.primitiveSuccess(baseType);
		}
		final ParseNodeKind kind = baseType.parseNodeKind();
		if (!expressionType.isSubtypeOf(kind.mostGeneralYieldType()))
		{
			return interpreter.primitiveFailure(E_BAD_YIELD_TYPE);
		}
		return interpreter.primitiveSuccess(kind.create(expressionType));
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return functionType(
			tuple(
				instanceMetaOn(PARSE_NODE.mostGeneralType()),
				topMeta()),
			instanceMetaOn(PARSE_NODE.mostGeneralType()));
	}

	@Override
	protected A_Type privateFailureVariableType ()
	{
		return instanceTypeOn(E_BAD_YIELD_TYPE.numericCode());
	}
}
