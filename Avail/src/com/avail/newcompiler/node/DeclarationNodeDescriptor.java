/**
 * com.avail.newcompiler/DeclarationNodeDescriptor.java
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

import com.avail.descriptor.*;

/**
 * My instances represent assignment statements.
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 */
public class DeclarationNodeDescriptor extends ParseNodeDescriptor
{
	/**
	 * My slots of type {@link AvailObject}.
	 *
	 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
	 */
	public enum ObjectSlots
	{
		/**
		 * The {@link ByteStringDescriptor name} of the variable being declared.
		 */
		NAME,

		/**
		 * The {@link TypeDescriptor type} of the variable being declared.
		 */
		DECLARED_TYPE,

		/**
		 * The optional {@link ParseNodeDescriptor initialization expression},
		 * or the {@link VoidDescriptor#voidObject() voidObject} otherwise.  Not
		 * applicable to all kinds of declarations.
		 */
		INITIALIZATION_EXPRESSION,

		/**
		 * The {@link AvailObject} held directly by this declaration.  It can be
		 * either a module constant value or a module variable.
		 */
		LITERAL_OBJECT
	}

	/**
	 * My slots of type {@link Integer int}.
	 *
	 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
	 */
	public enum IntegerSlots
	{
		/**
		 * Flags encoded as an {@code int}.
		 */
		DECLARATION_KIND
	}

	/**
	 * These are the kinds of arguments, variables, constants, and labels that
	 * can be declared.  There are also optional initializing expressions, fixed
	 * values (for module constants), and fixed variable objects (for module
	 * variables).
	 *
	 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
	 */
	public enum DeclarationKind
	{
		/**
		 * This is an argument to a block.
		 */
		ARGUMENT,

		/**
		 * This is a label declaration at the start of a block.
		 */
		LABEL,

		/**
		 * This is a local variable, declared within a block.
		 */
		LOCAL_VARIABLE,

		/**
		 * This is a local constant, declared within a block.
		 */
		LOCAL_CONSTANT,

		/**
		 * This is a variable declared at the outermost (module) scope.
		 */
		MODULE_VARIABLE,

		/**
		 * This is a constant declared at the outermost (module) scope.
		 */
		MODULE_CONSTANT

	}

	/**
	 * Setter for field name.
	 */
	@Override
	public void o_Name (
		final AvailObject object,
		final AvailObject name)
	{
		object.objectSlotPut(ObjectSlots.NAME, name);
	}

	/**
	 * Getter for field name.
	 */
	@Override
	public AvailObject o_Name (
		final AvailObject object)
	{
		return object.objectSlot(ObjectSlots.NAME);
	}

	/**
	 * Setter for field declaredType.
	 */
	@Override
	public void o_DeclaredType (
		final AvailObject object,
		final AvailObject declaredType)
	{
		object.objectSlotPut(ObjectSlots.DECLARED_TYPE, declaredType);
	}

	/**
	 * Getter for field declaredType.
	 */
	@Override
	public AvailObject o_DeclaredType (
		final AvailObject object)
	{
		return object.objectSlot(ObjectSlots.DECLARED_TYPE);
	}

	/**
	 * Setter for field initializationExpression.
	 */
	@Override
	public void o_InitializationExpression (
		final AvailObject object,
		final AvailObject initializationExpression)
	{
		object.objectSlotPut(
			ObjectSlots.INITIALIZATION_EXPRESSION,
			initializationExpression);
	}

	/**
	 * Getter for field initializationExpression.
	 */
	@Override
	public AvailObject o_InitializationExpression (
		final AvailObject object)
	{
		return object.objectSlot(ObjectSlots.INITIALIZATION_EXPRESSION);
	}

	/**
	 * Setter for field literalObject.
	 */
	@Override
	public void o_LiteralObject (
		final AvailObject object,
		final AvailObject literalObject)
	{
		object.objectSlotPut(ObjectSlots.LITERAL_OBJECT, literalObject);
	}

	/**
	 * Getter for field literalObject.
	 */
	@Override
	public AvailObject o_LiteralObject (
		final AvailObject object)
	{
		return object.objectSlot(ObjectSlots.LITERAL_OBJECT);
	}

	/**
	 * Setter for field declarationKind.
	 */
	@Override
	public void o_DeclarationKind (
		final AvailObject object,
		final DeclarationKind declarationKind)
	{
		object.integerSlotPut(
			IntegerSlots.DECLARATION_KIND,
			declarationKind.ordinal());
	}

	/**
	 * Getter for field declarationKind.
	 */
	@Override
	public DeclarationKind o_DeclarationKind (
		final AvailObject object)
	{
		return DeclarationKind.values()[object.integerSlot(
			IntegerSlots.DECLARATION_KIND)];
	}


	/**
	 * Construct a new {@link DeclarationNodeDescriptor}.
	 *
	 * @param isMutable Whether my {@linkplain AvailObject instances} can
	 *                  change.
	 */
	public DeclarationNodeDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}

	/**
	 * The mutable {@link DeclarationNodeDescriptor}.
	 */
	private final static DeclarationNodeDescriptor mutable =
		new DeclarationNodeDescriptor(true);

	/**
	 * Answer the mutable {@link DeclarationNodeDescriptor}.
	 *
	 * @return The mutable {@link DeclarationNodeDescriptor}.
	 */
	public static DeclarationNodeDescriptor mutable ()
	{
		return mutable;
	}

	/**
	 * The immutable {@link DeclarationNodeDescriptor}.
	 */
	private final static DeclarationNodeDescriptor immutable =
		new DeclarationNodeDescriptor(false);

	/**
	 * Answer the immutable {@link DeclarationNodeDescriptor}.
	 *
	 * @return The immutable {@link DeclarationNodeDescriptor}.
	 */
	public static DeclarationNodeDescriptor immutable ()
	{
		return immutable;
	}

}
