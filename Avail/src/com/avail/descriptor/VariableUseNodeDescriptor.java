/**
 * VariableUseNodeDescriptor.java
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

package com.avail.descriptor;

import static com.avail.descriptor.AvailObject.Multiplier;
import static com.avail.descriptor.ParseNodeTypeDescriptor.ParseNodeKind.*;
import static com.avail.descriptor.TypeDescriptor.Types.*;
import java.util.List;
import com.avail.annotations.*;
import com.avail.compiler.AvailCodeGenerator;
import com.avail.interpreter.levelTwo.L2Interpreter;
import com.avail.utility.*;

/**
 * My instances represent the use of some {@linkplain DeclarationNodeDescriptor
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
	public enum ObjectSlots implements ObjectSlotsEnum
	{
		/**
		 * The {@linkplain TokenDescriptor token} that is a mention of the entity
		 * in question.
		 */
		USE_TOKEN,

		/**
		 * The {@linkplain DeclarationNodeDescriptor declaration} of the entity that
		 * is being mentioned.
		 */
		DECLARATION
	}

	/**
	 * My slots of type {@linkplain Integer int}.
	 *
	 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
	 */
	public enum IntegerSlots implements IntegerSlotsEnum
	{
		/**
		 * A flag indicating (with 0/1) whether this is the last use of the
		 * mentioned entity.
		 */
		@BitFields(describedBy=Flags.class)
		FLAGS
	}

	/**
	 * Bit fields for the {@link IntegerSlots#FLAGS} slot.
	 */
	public static class Flags
	{
		/**
		 * Whether this is the last use of the mentioned entity.
		 */
		@BitField(shift=0, bits=1)
		static final BitField LAST_USE =
			bitField(Flags.class, "LAST_USE");
	}

	/**
	 * Getter for field token.
	 */
	@Override @AvailMethod
	AvailObject o_Token (
		final @NotNull AvailObject object)
	{
		return object.slot(ObjectSlots.USE_TOKEN);
	}

	/**
	 * Getter for field declaration.
	 */
	@Override @AvailMethod
	AvailObject o_Declaration (
		final @NotNull AvailObject object)
	{
		return object.slot(ObjectSlots.DECLARATION);
	}


	@Override @AvailMethod
	void o_IsLastUse (
		final @NotNull AvailObject object,
		final boolean isLastUse)
	{
		object.bitSlotPut(
			IntegerSlots.FLAGS,
			Flags.LAST_USE,
			isLastUse ? 1 : 0);
	}

	@Override @AvailMethod
	boolean o_IsLastUse (
		final @NotNull AvailObject object)
	{
		return object.bitSlot(
			IntegerSlots.FLAGS,
			Flags.LAST_USE) != 0;
	}


	@Override @AvailMethod
	AvailObject o_ExpressionType (final AvailObject object)
	{
		return object.declaration().declaredType();
	}

	@Override @AvailMethod
	AvailObject o_Kind (final AvailObject object)
	{
		return VARIABLE_USE_NODE.create(object.expressionType());
	}

	@Override @AvailMethod
	int o_Hash (final AvailObject object)
	{
		return
			((object.isLastUse() ? 1 : 0) * Multiplier
				+ object.token().hash()) * Multiplier
				+ object.declaration().hash()
			^ 0x62CE7BA2;
	}

	@Override @AvailMethod
	boolean o_Equals (
		final @NotNull AvailObject object,
		final AvailObject another)
	{
		return object.kind().equals(another.kind())
			&& object.token().equals(another.token())
			&& object.declaration().equals(another.declaration())
			&& object.isLastUse() == another.isLastUse();
	}

	@Override @AvailMethod
	void o_EmitValueOn (
		final @NotNull AvailObject object,
		final AvailCodeGenerator codeGenerator)
	{
		final AvailObject declaration = object.declaration();
		declaration.declarationKind().emitVariableValueForOn(
			declaration,
			codeGenerator);
	}

	@Override
	public void printObjectOnAvoidingIndent (
		final @NotNull AvailObject object,
		final StringBuilder builder,
		final List<AvailObject> recursionList,
		final int indent)
	{
		builder.append(object.token().string().asNativeString());
	}



	/**
	 * Construct a new {@linkplain VariableUseNodeDescriptor variable use node}.
	 *
	 * @param theToken The token which is the use of the variable in the source.
	 * @param declaration The declaration which is being used.
	 * @return A new variable use node.
	 */
	public static AvailObject newUse (
		final AvailObject theToken,
		final AvailObject declaration)
	{
		assert theToken.isInstanceOfKind(TOKEN.o());
		assert declaration.isInstanceOfKind(DECLARATION_NODE.mostGeneralType());

		final AvailObject newUse = mutable().create();
		newUse.setSlot(ObjectSlots.USE_TOKEN, theToken);
		newUse.setSlot(ObjectSlots.DECLARATION, declaration);
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

	@Override @AvailMethod
	void o_ChildrenMap (
		final @NotNull AvailObject object,
		final Transformer1<AvailObject, AvailObject> aBlock)
	{
		// Do nothing.
	}

	@Override @AvailMethod
	void o_ChildrenDo (
		final @NotNull AvailObject object,
		final Continuation1<AvailObject> aBlock)
	{
		// Do nothing.
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
}
