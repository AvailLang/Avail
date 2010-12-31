/**
 * com.avail.newcompiler/SendNodeDescriptor.java
 * Copyright (c) 2010, Mark van Gulik.
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

package com.avail.newcompiler.node;

import com.avail.compiler.AvailCodeGenerator;
import com.avail.descriptor.*;
import com.avail.descriptor.TypeDescriptor.Types;

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
	public enum ObjectSlots
	{
		/**
		 * The expressions yielding the arguments of the multi-method
		 * invocation.
		 */
		ARGUMENTS,

		/**
		 * The {@link ImplementationSetDescriptor implementation set} containing
		 * the multi-methods to be invoked.
		 */
		IMPLEMENTATION_SET,

		/**
		 * What {@link TypeDescriptor type} of {@link AvailObject object}
		 * this multi-method invocation must return.
		 */
		RETURN_TYPE

	}


	/**
	 * Setter for field arguments.
	 */
	@Override
	public void o_Arguments (
		final AvailObject object,
		final AvailObject arguments)
	{
		object.objectSlotPut(ObjectSlots.ARGUMENTS, arguments);
	}

	/**
	 * Getter for field arguments.
	 */
	@Override
	public AvailObject o_Arguments (
		final AvailObject object)
	{
		return object.objectSlot(ObjectSlots.ARGUMENTS);
	}


	/**
	 * Setter for field implementationSet.
	 */
	@Override
	public void o_ImplementationSet (
		final AvailObject object,
		final AvailObject implementationSet)
	{
		object.objectSlotPut(ObjectSlots.IMPLEMENTATION_SET, implementationSet);
	}

	/**
	 * Getter for field implementationSet.
	 */
	@Override
	public AvailObject o_ImplementationSet (
		final AvailObject object)
	{
		return object.objectSlot(ObjectSlots.IMPLEMENTATION_SET);
	}


	/**
	 * Setter for field returnType.
	 */
	@Override
	public void o_ReturnType (
		final AvailObject object,
		final AvailObject returnType)
	{
		object.objectSlotPut(ObjectSlots.RETURN_TYPE, returnType);
	}

	/**
	 * Getter for field arguments.
	 */
	@Override
	public AvailObject o_ReturnType (
		final AvailObject object)
	{
		return object.objectSlot(ObjectSlots.RETURN_TYPE);
	}


	@Override
	public AvailObject o_ExpressionType (final AvailObject object)
	{
		return object.returnType();
	}

	@Override
	public AvailObject o_Type (
			final AvailObject object)
	{
		return Types.sendNode.object();
	}

	@Override
	public void o_EmitValueOn (
		final AvailObject object,
		final AvailCodeGenerator codeGenerator)
	{
		boolean anyCasts;
		anyCasts = false;
		final AvailObject arguments = object.arguments();
		for (final AvailObject argNode : arguments)
		{
			argNode.emitValueOn(codeGenerator);
			if (argNode.type().isSubtypeOf(Types.superCastNode.object()))
			{
				anyCasts = true;
			}
		}
		final AvailObject implementationSet = object.implementationSet();
		implementationSet.makeImmutable();
		if (anyCasts)
		{
			for (final AvailObject argNode : arguments)
			{
				if (argNode.type().isSubtypeOf(Types.superCastNode.object()))
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
				implementationSet,
				object.returnType());
		}
		else
		{
			codeGenerator.emitCall(
				arguments.tupleSize(),
				implementationSet,
				object.returnType());
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
	private final static SendNodeDescriptor mutable =
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
	private final static SendNodeDescriptor immutable =
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
