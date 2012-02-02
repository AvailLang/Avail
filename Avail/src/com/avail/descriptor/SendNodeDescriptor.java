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
import java.util.List;
import com.avail.annotations.*;
import com.avail.compiler.*;
import com.avail.interpreter.levelTwo.L2Interpreter;
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
		 * The expressions yielding the arguments of the multi-method
		 * invocation.
		 */
		ARGUMENTS,

		/**
		 * The {@linkplain MethodDescriptor method} containing
		 * the multimethods to be invoked.
		 */
		METHOD,

		/**
		 * What {@linkplain TypeDescriptor type} of {@linkplain AvailObject object}
		 * this multi-method invocation must return.
		 */
		RETURN_TYPE
	}


	/**
	 * Setter for field arguments.
	 */
	@Override @AvailMethod
	void o_Arguments (
		final @NotNull AvailObject object,
		final AvailObject arguments)
	{
		object.setSlot(ObjectSlots.ARGUMENTS, arguments);
	}

	/**
	 * Getter for field arguments.
	 */
	@Override @AvailMethod
	AvailObject o_Arguments (
		final @NotNull AvailObject object)
	{
		return object.slot(ObjectSlots.ARGUMENTS);
	}


	/**
	 * Setter for field method.
	 */
	@Override @AvailMethod
	void o_Method (
		final @NotNull AvailObject object,
		final AvailObject method)
	{
		object.setSlot(ObjectSlots.METHOD, method);
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
	 * Setter for field returnType.
	 */
	@Override @AvailMethod
	void o_ReturnType (
		final @NotNull AvailObject object,
		final AvailObject returnType)
	{
		assert returnType.isType();
		object.setSlot(ObjectSlots.RETURN_TYPE, returnType);
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
	AvailObject o_ExpressionType (final AvailObject object)
	{
		return object.returnType();
	}

	@Override @AvailMethod
	AvailObject o_Kind (final AvailObject object)
	{
		return SEND_NODE.create(object.expressionType());
	}

	@Override @AvailMethod
	int o_Hash (final AvailObject object)
	{
		return
			(object.arguments().hash() * Multiplier
				+ object.method().hash()) * Multiplier
				+ object.returnType().hash()
			^ 0x90E39B4D;
	}

	@Override @AvailMethod
	boolean o_Equals (
		final @NotNull AvailObject object,
		final AvailObject another)
	{
		return object.kind().equals(another.kind())
			&& object.arguments().equals(another.arguments())
			&& object.method().equals(another.method())
			&& object.returnType().equals(another.returnType());
	}

	@Override @AvailMethod
	AvailObject o_ApparentSendName (final AvailObject object)
	{
		return object.method().name();
	}

	@Override @AvailMethod
	void o_EmitValueOn (
		final @NotNull AvailObject object,
		final AvailCodeGenerator codeGenerator)
	{
		boolean anyCasts;
		anyCasts = false;
		final AvailObject arguments = object.arguments();
		for (final AvailObject argNode : arguments)
		{
			argNode.emitValueOn(codeGenerator);
			if (argNode.isInstanceOfKind(SUPER_CAST_NODE.mostGeneralType()))
			{
				anyCasts = true;
			}
		}
		final AvailObject method = object.method();
		method.makeImmutable();
		if (anyCasts)
		{
			for (final AvailObject argNode : arguments)
			{
				if (argNode.isInstanceOfKind(SUPER_CAST_NODE.mostGeneralType()))
				{
					codeGenerator.emitPushLiteral(argNode.expressionType());
				}
				else
				{
					codeGenerator.emitGetType(arguments.tupleSize() - 1);
				}
			}
			// We've pushed all argument values and all arguments types onto the
			// stack.
			codeGenerator.emitSuperCall(
				arguments.tupleSize(),
				method,
				object.returnType());
		}
		else
		{
			codeGenerator.emitCall(
				arguments.tupleSize(),
				method,
				object.returnType());
		}
	}

	@Override @AvailMethod
	void o_ChildrenMap (
		final @NotNull AvailObject object,
		final Transformer1<AvailObject, AvailObject> aBlock)
	{
		AvailObject arguments = object.arguments();
		for (int i = 1; i <= arguments.tupleSize(); i++)
		{
			arguments = arguments.tupleAtPuttingCanDestroy(
				i,
				aBlock.value(arguments.tupleAt(i)),
				true);
		}
		object.arguments(arguments);
	}

	@Override @AvailMethod
	void o_ChildrenDo (
		final @NotNull AvailObject object,
		final Continuation1<AvailObject> aBlock)
	{
		for (final AvailObject argument : object.arguments())
		{
			aBlock.value(argument);
		}
	}

	@Override @AvailMethod
	void o_ValidateLocally (
		final @NotNull AvailObject object,
		final AvailObject parent,
		final List<AvailObject> outerBlocks,
		final L2Interpreter anAvailInterpreter)
	{
		// Do nothing.
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
			final MessageSplitter splitter = new MessageSplitter(
				object.method().name().name());
			splitter.printSendNodeOnIndent(
				object,
				builder,
				indent);
		}
		else
		{
			builder.append("SendNode[");
			builder.append(object.method()
				.name().name().asNativeString());
			builder.append("](");
			boolean isFirst = true;
			for (final AvailObject arg : object.argumentsTuple())
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
