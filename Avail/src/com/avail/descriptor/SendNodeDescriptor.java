/**
 * SendNodeDescriptor.java
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

package com.avail.descriptor;

import static com.avail.descriptor.AvailObject.Multiplier;
import static com.avail.descriptor.ParseNodeTypeDescriptor.ParseNodeKind.*;
import static com.avail.descriptor.SendNodeDescriptor.ObjectSlots.*;
import java.util.List;
import com.avail.annotations.*;
import com.avail.compiler.*;
import com.avail.descriptor.ParseNodeTypeDescriptor.ParseNodeKind;
import com.avail.exceptions.SignatureException;
import com.avail.utility.*;

/**
 * My instances represent invocations of multi-methods in Avail code.
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 */
public class SendNodeDescriptor extends ParseNodeDescriptor
{
	/**
	 * My slots of type {@link AvailObject}.
	 *
	 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
	 */
	public enum ObjectSlots implements ObjectSlotsEnum
	{
		/**
		 * A {@link ListNodeDescriptor list node} containing the expressions
		 * that yield the arguments of the method invocation.
		 */
		ARGUMENTS_LIST_NODE,

		/**
		 * The {@linkplain MethodDescriptor method} to be invoked.
		 */
		METHOD,

		/**
		 * What {@linkplain TypeDescriptor type} of {@linkplain AvailObject
		 * object} this method invocation must return.
		 */
		RETURN_TYPE
	}


	/**
	 * Getter for field arguments.
	 */
	@Override @AvailMethod
	AvailObject o_ArgumentsListNode (
		final @NotNull AvailObject object)
	{
		return object.slot(ObjectSlots.ARGUMENTS_LIST_NODE);
	}

	/**
	 * Getter for field method.
	 */
	@Override @AvailMethod
	AvailObject o_Method (
		final @NotNull AvailObject object)
	{
		return object.slot(ObjectSlots.METHOD);
	}

	/**
	 * Getter for field arguments.
	 */
	@Override @AvailMethod
	AvailObject o_ReturnType (
		final @NotNull AvailObject object)
	{
		return object.slot(ObjectSlots.RETURN_TYPE);
	}


	@Override @AvailMethod
	AvailObject o_ExpressionType (final @NotNull AvailObject object)
	{
		return object.returnType();
	}

	@Override @AvailMethod
	int o_Hash (final @NotNull AvailObject object)
	{
		return
			(object.argumentsListNode().hash() * Multiplier
				^ object.method().hash()) * Multiplier
				- object.returnType().hash()
			^ 0x90E39B4D;
	}

	@Override @AvailMethod
	boolean o_Equals (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
	{
		return object.kind().equals(another.kind())
			&& object.argumentsListNode().equals(another.argumentsListNode())
			&& object.method().equals(another.method())
			&& object.returnType().equals(another.returnType());
	}

	@Override @AvailMethod
	AvailObject o_ApparentSendName (final @NotNull AvailObject object)
	{
		return object.method().name();
	}

	@Override @AvailMethod
	void o_EmitValueOn (
		final @NotNull AvailObject object,
		final @NotNull AvailCodeGenerator codeGenerator)
	{
		final AvailObject arguments = object.argumentsListNode();
		final AvailObject tuple = arguments.expressionsTuple();
		for (final AvailObject argNode : tuple)
		{
			argNode.emitValueOn(codeGenerator);
		}
		final AvailObject method = object.method();
		method.makeImmutable();
		codeGenerator.emitCall(
			tuple.tupleSize(),
			method,
			object.returnType());
	}

	@Override @AvailMethod
	void o_ChildrenMap (
		final @NotNull AvailObject object,
		final @NotNull Transformer1<AvailObject, AvailObject> aBlock)
	{
		object.setSlot(
			ARGUMENTS_LIST_NODE,
			aBlock.value(
				object.slot(ARGUMENTS_LIST_NODE)));
	}

	@Override @AvailMethod
	void o_ChildrenDo (
		final @NotNull AvailObject object,
		final @NotNull Continuation1<AvailObject> aBlock)
	{
		aBlock.value(object.slot(ARGUMENTS_LIST_NODE));
	}

	@Override @AvailMethod
	void o_ValidateLocally (
		final @NotNull AvailObject object,
		final @NotNull AvailObject parent)
	{
		// Do nothing.
	}

	@Override
	@NotNull ParseNodeKind o_ParseNodeKind (
		final @NotNull AvailObject object)
	{
		return SEND_NODE;
	}


	/**
	 * If set to true, print send nodes with extra notation to help visually
	 * sort out ambiguous parses.
	 */
	static final boolean nicePrinting = true;

	@Override
	public void printObjectOnAvoidingIndent (
		final @NotNull AvailObject object,
		final StringBuilder builder,
		final List<AvailObject> recursionList,
		final int indent)
	{
		if (nicePrinting)
		{
			final MessageSplitter splitter;
			try
			{
				splitter = new MessageSplitter(
					object.method().name().name());
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
		else
		{
			builder.append("SendNode[");
			builder.append(object.method().name().name().asNativeString());
			builder.append("](");
			boolean isFirst = true;
			for (final AvailObject arg
				: object.argumentsListNode().expressionsTuple())
			{
				if (!isFirst)
				{
					builder.append(",");
				}
				builder.append("\n");
				for (int i = indent; i >= 0; i--)
				{
					builder.append("\t");
				}
				arg.printOnAvoidingIndent(builder, recursionList, indent + 1);
				isFirst = false;
			}
			builder.append(")");
		}
	}


	/**
	 * Create a new {@linkplain SendNodeDescriptor send node} from the specified
	 * {@linkplain MethodDescriptor method}, {@linkplain ListNodeDescriptor
	 * list node} of argument expressions, and return {@linkplain TypeDescriptor
	 * type}.
	 *
	 * @param method
	 *        The target method.
	 * @param argsListNode
	 *        A {@linkplain ListNodeDescriptor list node} of argument
	 *        expressions.
	 * @param returnType
	 *        The target method's expected return type.
	 * @return A new send node.
	 */
	public static @NotNull AvailObject from (
		final @NotNull AvailObject method,
		final @NotNull AvailObject argsListNode,
		final @NotNull AvailObject returnType)
	{
		final AvailObject newObject = mutable.create();
		newObject.setSlot(ARGUMENTS_LIST_NODE, argsListNode);
		newObject.setSlot(METHOD, method);
		newObject.setSlot(RETURN_TYPE, returnType);
		return newObject;
	}

	/**
	 * Construct a new {@link SendNodeDescriptor}.
	 *
	 * @param isMutable Whether my {@linkplain AvailObject instances} can
	 *                  change.
	 */
	public SendNodeDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}

	/**
	 * The mutable {@link SendNodeDescriptor}.
	 */
	private static final SendNodeDescriptor mutable =
		new SendNodeDescriptor(true);

	/**
	 * Answer the mutable {@link SendNodeDescriptor}.
	 *
	 * @return The mutable {@link SendNodeDescriptor}.
	 */
	public static SendNodeDescriptor mutable ()
	{
		return mutable;
	}

	/**
	 * The immutable {@link SendNodeDescriptor}.
	 */
	private static final SendNodeDescriptor immutable =
		new SendNodeDescriptor(false);

	/**
	 * Answer the immutable {@link SendNodeDescriptor}.
	 *
	 * @return The immutable {@link SendNodeDescriptor}.
	 */
	public static SendNodeDescriptor immutable ()
	{
		return immutable;
	}
}
