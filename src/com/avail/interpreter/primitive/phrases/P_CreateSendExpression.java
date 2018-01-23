/**
 * P_CreateSendExpression.java
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

import com.avail.compiler.splitter.MessageSplitter;
import com.avail.descriptor.*;
import com.avail.descriptor.ParseNodeTypeDescriptor.ParseNodeKind;
import com.avail.exceptions.MalformedMessageException;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;
import com.avail.optimizer.jvm.ReferencedInGeneratedCode;
import java.util.List;

import static com.avail.compiler.splitter.MessageSplitter.possibleErrors;
import static com.avail.descriptor.AbstractEnumerationTypeDescriptor
	.enumerationWith;
import static com.avail.descriptor.FunctionTypeDescriptor.functionType;
import static com.avail.descriptor.InstanceMetaDescriptor.topMeta;
import static com.avail.descriptor.ParseNodeTypeDescriptor.ParseNodeKind
	.LIST_NODE;
import static com.avail.descriptor.ParseNodeTypeDescriptor.ParseNodeKind
	.SEND_NODE;
import static com.avail.descriptor.SendNodeDescriptor.newSendNode;
import static com.avail.descriptor.SetDescriptor.set;
import static com.avail.descriptor.TupleDescriptor.emptyTuple;
import static com.avail.descriptor.TupleDescriptor.tuple;
import static com.avail.descriptor.TypeDescriptor.Types.ATOM;
import static com.avail.exceptions.AvailErrorCode
	.E_INCORRECT_NUMBER_OF_ARGUMENTS;
import static com.avail.interpreter.Primitive.Flag.CanFold;
import static com.avail.interpreter.Primitive.Flag.CanInline;

/**
 * <strong>Primitive:</strong> Create a {@linkplain SendNodeDescriptor send
 * expression} from the specified {@linkplain MessageBundleDescriptor message
 * bundle}, {@linkplain ListNodeDescriptor list node} of {@linkplain
 * ParseNodeKind#EXPRESSION_NODE argument expressions}, and {@linkplain
 * TypeDescriptor return type}.  Do not apply semantic restrictions.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class P_CreateSendExpression
extends Primitive
{
	/**
	 * The sole instance of this primitive class. Accessed through reflection.
	 */
	@ReferencedInGeneratedCode
	public static final Primitive instance =
		new P_CreateSendExpression().init(
			3, CanFold, CanInline);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter)
	{
		assert args.size() == 3;
		final A_Atom messageName = args.get(0);
		final A_Phrase argsListNode = args.get(1);
		final A_Type returnType = args.get(2);

		final A_Tuple argExpressions = argsListNode.expressionsTuple();
		final int argsCount = argExpressions.tupleSize();
		final A_Bundle bundle;
		try
		{
			bundle = messageName.bundleOrCreate();
			final MessageSplitter splitter = bundle.messageSplitter();
			if (splitter.numberOfArguments() != argsCount)
			{
				return interpreter.primitiveFailure(
					E_INCORRECT_NUMBER_OF_ARGUMENTS);
			}
		}
		catch (final MalformedMessageException e)
		{
			return interpreter.primitiveFailure(e.errorCode());
		}
		return interpreter.primitiveSuccess(
			newSendNode(emptyTuple(), bundle, argsListNode, returnType));
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return functionType(
			tuple(
				ATOM.o(),
				LIST_NODE.mostGeneralType(),
				topMeta()),
			SEND_NODE.mostGeneralType());
	}

	@Override
	public A_Type returnTypeGuaranteedByVM (
		final A_RawFunction rawFunction,
		final List<? extends A_Type> argumentTypes)
	{
		assert argumentTypes.size() == 3;
//		final A_Type messageNameType = argumentTypes.get(0);
//		final A_Type argsListNodeType = argumentTypes.get(1);
		final A_Type returnTypeType = argumentTypes.get(2);

		final A_Type returnType = returnTypeType.instance();
		return SEND_NODE.create(returnType);
	}

	@Override
	protected A_Type privateFailureVariableType ()
	{
		return enumerationWith(
			set(
				E_INCORRECT_NUMBER_OF_ARGUMENTS
			).setUnionCanDestroy(possibleErrors, true));
	}
}
