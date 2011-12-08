/**
 * com.avail.compiler/SuperCastNodeDescriptor.java
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

package com.avail.descriptor;

import static com.avail.descriptor.AvailObject.*;
import static com.avail.descriptor.ParseNodeTypeDescriptor.ParseNodeKind.*;
import java.util.List;
import com.avail.annotations.*;
import com.avail.compiler.AvailCodeGenerator;
import com.avail.interpreter.levelTwo.L2Interpreter;
import com.avail.utility.*;

/**
 * My instances represent a weakening of the type of an argument of a {@linkplain
 * SendNodeDescriptor message send}, in order to invoke a more general
 * implementation.  Multiple arguments of the same message send may be weakened
 * in this way.
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 */
public class SuperCastNodeDescriptor extends ParseNodeDescriptor
{
	/**
	 * My slots of type {@link AvailObject}.
	 *
	 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
	 */
	public enum ObjectSlots implements ObjectSlotsEnum
	{
		/**
		 * The expression that yields some multi-method argument.
		 */
		EXPRESSION,

		/**
		 * The {@linkplain TypeDescriptor type} to consider the expression to be for
		 * the purpose of multi-method lookup.
		 */
		SUPER_CAST_TYPE

	}


	/**
	 * Setter for field expression.
	 */
	@Override @AvailMethod
	void o_Expression (
		final @NotNull AvailObject object,
		final AvailObject expression)
	{
		object.objectSlotPut(ObjectSlots.EXPRESSION, expression);
	}

	/**
	 * Getter for field expression.
	 */
	@Override @AvailMethod
	AvailObject o_Expression (
		final @NotNull AvailObject object)
	{
		return object.objectSlot(ObjectSlots.EXPRESSION);
	}


	/**
	 * Setter for field superCastType.
	 */
	@Override @AvailMethod
	void o_SuperCastType (
		final @NotNull AvailObject object,
		final AvailObject superCastType)
	{
		object.objectSlotPut(ObjectSlots.SUPER_CAST_TYPE, superCastType);
	}

	/**
	 * Getter for field superCastType.
	 */
	@Override @AvailMethod
	AvailObject o_SuperCastType (
		final @NotNull AvailObject object)
	{
		return object.objectSlot(ObjectSlots.SUPER_CAST_TYPE);
	}


	@Override @AvailMethod
	AvailObject o_ExpressionType (final AvailObject object)
	{
		return object.objectSlot(ObjectSlots.SUPER_CAST_TYPE);
	}

	@Override @AvailMethod
	AvailObject o_Kind (final AvailObject object)
	{
		return SUPER_CAST_NODE.create(object.expressionType());
	}

	@Override @AvailMethod
	int o_Hash (final AvailObject object)
	{
		return
			object.expression().hash() * Multiplier
				+ object.superCastType().hash()
			^ 0xD8CCD162;
	}

	@Override @AvailMethod
	boolean o_Equals (
		final @NotNull AvailObject object,
		final AvailObject another)
	{
		return object.kind().equals(another.kind())
			&& object.expression().equals(another.expression())
			&& object.superCastType().equals(another.superCastType());
	}

	@Override @AvailMethod
	void o_EmitEffectOn (
		final @NotNull AvailObject object,
		final AvailCodeGenerator codeGenerator)
	{
		error("A superCast can only be done to an argument of a call.");
	}

	@Override @AvailMethod
	void o_EmitValueOn (
		final @NotNull AvailObject object,
		final AvailCodeGenerator codeGenerator)
	{
		// My parent (send) node deals with my altered semantics.
		object.expression().emitValueOn(codeGenerator);
	}

	@Override @AvailMethod
	void o_ChildrenMap (
		final @NotNull AvailObject object,
		final Transformer1<AvailObject, AvailObject> aBlock)
	{
		object.expression(aBlock.value(object.expression()));
	}


	@Override @AvailMethod
	void o_ChildrenDo (
		final @NotNull AvailObject object,
		final Continuation1<AvailObject> aBlock)
	{
		aBlock.value(object.expression());
	}


	@Override @AvailMethod
	void o_ValidateLocally (
		final @NotNull AvailObject object,
		final AvailObject parent,
		final List<AvailObject> outerBlocks,
		final L2Interpreter anAvailInterpreter)
	{
		if (!parent.isInstanceOfKind(SEND_NODE.mostGeneralType()))
		{
			error("Only use superCast notation as a message argument");
		}
	}


	/**
	 * Create a new {@linkplain SuperCastNodeDescriptor supercast node} representing
	 * the specified expression weakened to the specified type for the purpose
	 * of acting as an argument of a {@linkplain SendNodeDescriptor send node}.
	 *
	 * @param expression
	 *            The expression that will provide a value to use as an argument
	 *            to a send at runtime.
	 * @param superCastType
	 *            The {@linkplain TypeDescriptor type} to treat the argument as at
	 *            runtime, for the purpose of deciding which method to invoke.
	 * @return
	 *            The resulting {@linkplain SuperCastNodeDescriptor supercast node}
	 */
	public static AvailObject create (
		final AvailObject expression,
		final AvailObject superCastType)
	{
		final AvailObject cast = mutable().create();
		cast.expression(expression);
		cast.superCastType(superCastType);
		return cast;
	}


	/**
	 * Construct a new {@link SuperCastNodeDescriptor}.
	 *
	 * @param isMutable Whether my {@linkplain AvailObject instances} can
	 *                  change.
	 */
	public SuperCastNodeDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}

	/**
	 * The mutable {@link SuperCastNodeDescriptor}.
	 */
	private final static SuperCastNodeDescriptor mutable =
		new SuperCastNodeDescriptor(true);

	/**
	 * Answer the mutable {@link SuperCastNodeDescriptor}.
	 *
	 * @return The mutable {@link SuperCastNodeDescriptor}.
	 */
	public static SuperCastNodeDescriptor mutable ()
	{
		return mutable;
	}

	/**
	 * The immutable {@link SuperCastNodeDescriptor}.
	 */
	private final static SuperCastNodeDescriptor immutable =
		new SuperCastNodeDescriptor(false);

	/**
	 * Answer the immutable {@link SuperCastNodeDescriptor}.
	 *
	 * @return The immutable {@link SuperCastNodeDescriptor}.
	 */
	public static SuperCastNodeDescriptor immutable ()
	{
		return immutable;
	}
}
