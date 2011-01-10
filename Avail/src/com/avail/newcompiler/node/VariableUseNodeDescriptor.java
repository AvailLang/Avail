/**
 * com.avail.newcompiler/VariableUseNodeDescriptor.java
 * Copyright (c) 2010, Mark van Gulik.
 * All rights reserved.
 *
 * modification, are permitted provided that the following conditions are met:
 * Redistribution and use in source and binary forms, with or without
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

import static com.avail.descriptor.AvailObject.Multiplier;
import java.util.List;
import com.avail.compiler.AvailCodeGenerator;
import com.avail.descriptor.*;
import com.avail.descriptor.TypeDescriptor.Types;
import com.avail.interpreter.levelTwo.L2Interpreter;
import com.avail.newcompiler.scanner.TokenDescriptor;
import com.avail.utility.Transformer1;

/**
 * My instances represent the use of some {@link DeclarationNodeDescriptor
 * declared entity}.
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 */
public class VariableUseNodeDescriptor extends ParseNodeDescriptor
{
	/**
	 * My slots of type {@link AvailObject}.
	 *
	 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
	 */
	public enum ObjectSlots
	{
		/**
		 * The {@link TokenDescriptor token} that is a mention of the entity
		 * in question.
		 */
		TOKEN,

		/**
		 * The {@link DeclarationNodeDescriptor declaration} of the entity that
		 * is being mentioned.
		 */
		DECLARATION
	}

	/**
	 * My slots of type {@link Integer int}.
	 *
	 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
	 */
	public enum IntegerSlots
	{
		/**
		 * A flag indicating (with 0/1) whether this is the last use of the
		 * mentioned entity.
		 */
		FLAGS
	}

	/**
	 * Setter for field token.
	 */
	@Override
	public void o_Token (
		final AvailObject object,
		final AvailObject token)
	{
		object.objectSlotPut(ObjectSlots.TOKEN, token);
	}

	/**
	 * Getter for field token.
	 */
	@Override
	public AvailObject o_Token (
		final AvailObject object)
	{
		return object.objectSlot(ObjectSlots.TOKEN);
	}

	/**
	 * Setter for field declaration.
	 */
	@Override
	public void o_Declaration (
		final AvailObject object,
		final AvailObject declaration)
	{
		object.objectSlotPut(ObjectSlots.DECLARATION, declaration);
	}

	/**
	 * Getter for field declaration.
	 */
	@Override
	public AvailObject o_Declaration (
		final AvailObject object)
	{
		return object.objectSlot(ObjectSlots.DECLARATION);
	}


	/**
	 * Setter for field declaration.
	 */
	@Override
	public void o_IsLastUse (
		final AvailObject object,
		final boolean isLastUse)
	{
		int flags = object.integerSlot(IntegerSlots.FLAGS);
		flags = flags & ~1 + (isLastUse ? 1 : 0);
		object.integerSlotPut(
			IntegerSlots.FLAGS,
			flags & ~1 + (isLastUse ? 1 : 0));
	}

	/**
	 * Getter for field declaration.
	 */
	@Override
	public boolean o_IsLastUse (
		final AvailObject object)
	{
		return (object.integerSlot(IntegerSlots.FLAGS) & 1) != 0;
	}


	@Override
	public AvailObject o_ExpressionType (final AvailObject object)
	{
		return object.declaration().declaredType();
	}

	@Override
	public AvailObject o_Type (final AvailObject object)
	{
		return Types.variableUseNode.object();
	}

	@Override
	public AvailObject o_ExactType (final AvailObject object)
	{
		return Types.variableUseNode.object();
	}

	@Override
	public int o_Hash (final AvailObject object)
	{
		return
			((object.isLastUse() ? 1 : 0) * Multiplier
				+ object.token().hash()) * Multiplier
				+ object.declaration().hash()
			^ 0x62CE7BA2;
	}

	@Override
	public boolean o_Equals (
		final AvailObject object,
		final AvailObject another)
	{
		return object.type().equals(another.type())
			&& object.token().equals(another.token())
			&& object.declaration().equals(another.declaration())
			&& object.isLastUse() == another.isLastUse();
	}

	@Override
	public void o_EmitValueOn (
		final AvailObject object,
		final AvailCodeGenerator codeGenerator)
	{
		final AvailObject declaration = object.declaration();
		declaration.declarationKind().emitVariableValueForOn(
			declaration,
			codeGenerator);
	}

	@Override
	public void printObjectOnAvoidingIndent (
		final AvailObject object,
		final StringBuilder builder,
		final List<AvailObject> recursionList,
		final int indent)
	{
		builder.append(
			object.token().string().asNativeString());
	}



	/**
	 * Construct a new {@link VariableUseNodeDescriptor variable use node}.
	 *
	 * @param token The token which is the use of the variable in the source.
	 * @param declaration The declaration which is being used.
	 * @return A new variable use node.
	 */
	public static AvailObject newUse (
		final AvailObject token,
		final AvailObject declaration)
	{
		assert token.isInstanceOfSubtypeOf(Types.token.object());
		assert declaration.isInstanceOfSubtypeOf(
			Types.declarationNode.object());

		final AvailObject newUse = mutable().create();
		newUse.token(token);
		newUse.declaration(declaration);
		newUse.isLastUse(false);
		return newUse;
	}



	/**
	 * Construct a new {@link VariableUseNodeDescriptor}.
	 *
	 * @param isMutable Whether my {@linkplain AvailObject instances} can
	 *                  change.
	 */
	public VariableUseNodeDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}

	/**
	 * The mutable {@link VariableUseNodeDescriptor}.
	 */
	private final static VariableUseNodeDescriptor mutable =
		new VariableUseNodeDescriptor(true);

	/**
	 * Answer the mutable {@link VariableUseNodeDescriptor}.
	 *
	 * @return The mutable {@link VariableUseNodeDescriptor}.
	 */
	public static VariableUseNodeDescriptor mutable ()
	{
		return mutable;
	}

	/**
	 * The immutable {@link VariableUseNodeDescriptor}.
	 */
	private final static VariableUseNodeDescriptor immutable =
		new VariableUseNodeDescriptor(false);

	/**
	 * Answer the immutable {@link VariableUseNodeDescriptor}.
	 *
	 * @return The immutable {@link VariableUseNodeDescriptor}.
	 */
	public static VariableUseNodeDescriptor immutable ()
	{
		return immutable;
	}

	@Override
	public void o_ChildrenMap (
		final AvailObject object,
		final Transformer1<AvailObject, AvailObject> aBlock)
	{
		// Do nothing.
	}

	@Override
	public void o_ValidateLocally (
		final AvailObject object,
		final AvailObject parent,
		final List<AvailObject> outerBlocks,
		final L2Interpreter anAvailInterpreter)
	{
		// Do nothing.
	}

}
