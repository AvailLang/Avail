/**
 * SendNodeDescriptor.java
 * Copyright © 1993-2013, Mark van Gulik and Todd L Smith.
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

package com.avail.descriptor;

import static com.avail.descriptor.AvailObject.multiplier;
import static com.avail.descriptor.ParseNodeTypeDescriptor.ParseNodeKind.*;
import static com.avail.descriptor.SendNodeDescriptor.ObjectSlots.*;
import java.util.List;
import com.avail.annotations.*;
import com.avail.compiler.*;
import com.avail.descriptor.ParseNodeTypeDescriptor.ParseNodeKind;
import com.avail.descriptor.TypeDescriptor.Types;
import com.avail.exceptions.SignatureException;
import com.avail.utility.*;

/**
 * My instances represent invocations of multi-methods in Avail code.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class SendNodeDescriptor
extends ParseNodeDescriptor
{
	/**
	 * My slots of type {@link AvailObject}.
	 */
	public enum ObjectSlots
	implements ObjectSlotsEnum
	{
		/**
		 * A {@link ListNodeDescriptor list node} containing the expressions
		 * that yield the arguments of the method invocation.
		 */
		ARGUMENTS_LIST_NODE,

		/**
		 * The {@linkplain MessageBundleDescriptor message bundle} that this
		 * send was intended to invoke.  Technically, it's the {@linkplain
		 * MethodDescriptor method} inside the bundle that will be invoked, so
		 * the bundle gets stripped off when generating a raw function from a
		 * {@linkplain BlockNodeDescriptor block node} containing this send.
		 */
		BUNDLE,

		/**
		 * What {@linkplain TypeDescriptor type} of {@linkplain AvailObject
		 * object} this method invocation must return.
		 */
		RETURN_TYPE
	}

	@Override
	public void printObjectOnAvoidingIndent (
		final AvailObject object,
		final StringBuilder builder,
		final List<A_BasicObject> recursionList,
		final int indent)
	{
		final MessageSplitter splitter;
		try
		{
			splitter = new MessageSplitter(
				object.bundle().message().name());
		}
		catch (final SignatureException e)
		{
			builder.append("*** Malformed selector: ");
			builder.append(e.errorCode().name());
			builder.append("***");
			return;
		}
		splitter.printSendNodeOnIndent(
			object,
			builder,
			indent);
	}

	@Override @AvailMethod
	A_Phrase o_ArgumentsListNode (final AvailObject object)
	{
		return object.slot(ARGUMENTS_LIST_NODE);
	}

	@Override @AvailMethod
	A_Bundle o_Bundle (final AvailObject object)
	{
		return object.slot(BUNDLE);
	}

	@Override @AvailMethod
	A_Type o_ReturnType (final AvailObject object)
	{
		return object.slot(RETURN_TYPE);
	}


	@Override @AvailMethod
	A_Type o_ExpressionType (final AvailObject object)
	{
		return object.slot(RETURN_TYPE);
	}

	@Override @AvailMethod
	int o_Hash (final AvailObject object)
	{
		return
			(object.slot(ARGUMENTS_LIST_NODE).hash() * multiplier
				^ object.slot(BUNDLE).hash()) * multiplier
				- object.slot(RETURN_TYPE).hash()
			^ 0x90E39B4D;
	}

	@Override @AvailMethod
	boolean o_EqualsParseNode (
		final AvailObject object,
		final A_Phrase aParseNode)
	{
		return object.kind().equals(aParseNode.kind())
			&& object.slot(BUNDLE).equals(aParseNode.bundle())
			&& object.slot(ARGUMENTS_LIST_NODE).equals(
				aParseNode.argumentsListNode())
			&& object.slot(RETURN_TYPE).equals(aParseNode.returnType());
	}

	@Override @AvailMethod
	A_Atom o_ApparentSendName (final AvailObject object)
	{
		return object.slot(BUNDLE).name();
	}

	@Override @AvailMethod
	void o_EmitValueOn (
		final AvailObject object,
		final AvailCodeGenerator codeGenerator)
	{
		final A_Phrase arguments = object.slot(ARGUMENTS_LIST_NODE);
		final A_Tuple tuple = arguments.expressionsTuple();
		for (final A_Phrase argNode : tuple)
		{
			argNode.emitValueOn(codeGenerator);
		}
		final A_Bundle bundle = object.slot(BUNDLE);
		codeGenerator.emitCall(
			tuple.tupleSize(),
			bundle,
			object.returnType());
	}

	@Override @AvailMethod
	void o_ChildrenMap (
		final AvailObject object,
		final Transformer1<A_Phrase, A_Phrase> aBlock)
	{
		object.setSlot(
			ARGUMENTS_LIST_NODE,
			aBlock.value(object.slot(ARGUMENTS_LIST_NODE)));
	}

	@Override @AvailMethod
	void o_ChildrenDo (
		final AvailObject object,
		final Continuation1<A_Phrase> aBlock)
	{
		aBlock.value(object.slot(ARGUMENTS_LIST_NODE));
	}

	@Override @AvailMethod
	void o_ValidateLocally (
		final AvailObject object,
		final @Nullable A_Phrase parent)
	{
		// Do nothing.
	}

	@Override
	ParseNodeKind o_ParseNodeKind (final AvailObject object)
	{
		return SEND_NODE;
	}

	/**
	 * Create a new {@linkplain SendNodeDescriptor send node} from the specified
	 * {@linkplain MethodDescriptor method}, {@linkplain ListNodeDescriptor
	 * list node} of argument expressions, and return {@linkplain TypeDescriptor
	 * type}.
	 *
	 * @param bundle
	 *        The method bundle for which this represents an invocation.
	 * @param argsListNode
	 *        A {@linkplain ListNodeDescriptor list node} of argument
	 *        expressions.
	 * @param returnType
	 *        The target method's expected return type.
	 * @return A new send node.
	 */
	public static A_Phrase from (
		final A_Bundle bundle,
		final A_Phrase argsListNode,
		final A_Type returnType)
	{
		assert bundle.isInstanceOfKind(Types.MESSAGE_BUNDLE.o());
		final AvailObject newObject = mutable.create();
		newObject.setSlot(ARGUMENTS_LIST_NODE, argsListNode);
		newObject.setSlot(BUNDLE, bundle);
		newObject.setSlot(RETURN_TYPE, returnType);
		newObject.makeShared();
		return newObject;
	}

	/**
	 * Construct a new {@link SendNodeDescriptor}.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 */
	private SendNodeDescriptor (final Mutability mutability)
	{
		super(mutability);
	}

	/** The mutable {@link SendNodeDescriptor}. */
	private static final SendNodeDescriptor mutable =
		new SendNodeDescriptor(Mutability.MUTABLE);

	@Override
	SendNodeDescriptor mutable ()
	{
		return mutable;
	}

	/** The shared {@link SendNodeDescriptor}. */
	private static final SendNodeDescriptor shared =
		new SendNodeDescriptor(Mutability.SHARED);

	@Override
	SendNodeDescriptor shared ()
	{
		return shared;
	}
}
