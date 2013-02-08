/**
 * VariableUseNodeDescriptor.java
 * Copyright © 1993-2013, Mark van Gulik and Todd L Smith.
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

import static com.avail.descriptor.AvailObject.multiplier;
import static com.avail.descriptor.ParseNodeTypeDescriptor.ParseNodeKind.*;
import static com.avail.descriptor.TypeDescriptor.Types.*;
import static com.avail.descriptor.VariableUseNodeDescriptor.IntegerSlots.*;
import static com.avail.descriptor.VariableUseNodeDescriptor.ObjectSlots.*;
import java.util.List;
import com.avail.annotations.*;
import com.avail.compiler.AvailCodeGenerator;
import com.avail.descriptor.ParseNodeTypeDescriptor.ParseNodeKind;
import com.avail.utility.*;

/**
 * My instances represent the use of some {@linkplain DeclarationNodeDescriptor
 * declared entity}.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class VariableUseNodeDescriptor
extends ParseNodeDescriptor
{
	/**
	 * My slots of type {@linkplain Integer int}.
	 */
	public enum IntegerSlots implements IntegerSlotsEnum
	{
		/**
		 * A flag indicating (with 0/1) whether this is the last use of the
		 * mentioned entity.
		 */
		FLAGS;


		/**
		 * Whether this is the last use of the mentioned entity.
		 */
		static final BitField LAST_USE = bitField(
			FLAGS,
			0,
			1);

	}

	/**
	 * My slots of type {@link AvailObject}.
	 */
	public enum ObjectSlots
	implements ObjectSlotsEnum
	{
		/**
		 * The {@linkplain TokenDescriptor token} that is a mention of the
		 * entity in question.
		 */
		USE_TOKEN,

		/**
		 * The {@linkplain DeclarationNodeDescriptor declaration} of the entity
		 * that is being mentioned.
		 */
		DECLARATION
	}

	@Override
	boolean allowsImmutableToMutableReferenceInField (final AbstractSlotsEnum e)
	{
		return e == FLAGS;
	}

	@Override @AvailMethod
	AvailObject o_Token (final AvailObject object)
	{
		return object.slot(USE_TOKEN);
	}

	@Override @AvailMethod
	AvailObject o_Declaration (final AvailObject object)
	{
		return object.slot(DECLARATION);
	}

	@Override @AvailMethod
	void o_IsLastUse (
		final AvailObject object,
		final boolean isLastUse)
	{
		if (isShared())
		{
			synchronized (object)
			{
				object.setSlot(LAST_USE, isLastUse ? 1 : 0);
			}
		}
		else
		{
			object.setSlot(LAST_USE, isLastUse ? 1 : 0);
		}
	}

	@Override @AvailMethod
	boolean o_IsLastUse (final AvailObject object)
	{
		if (isShared())
		{
			synchronized (object)
			{
				return object.slot(LAST_USE) != 0;
			}
		}
		return object.slot(LAST_USE) != 0;
	}

	@Override @AvailMethod
	AvailObject o_ExpressionType (final AvailObject object)
	{
		return object.slot(DECLARATION).declaredType();
	}

	@Override @AvailMethod
	int o_Hash (final AvailObject object)
	{
		return
			(object.slot(USE_TOKEN).hash()) * multiplier
				+ object.slot(DECLARATION).hash()
			^ 0x62CE7BA2;
	}

	@Override @AvailMethod
	boolean o_EqualsParseNode (
		final AvailObject object,
		final AvailObject aParseNode)
	{
		return object.kind().equals(aParseNode.kind())
			&& object.slot(USE_TOKEN).equals(aParseNode.token())
			&& object.slot(DECLARATION).equals(aParseNode.declaration())
			&& object.isLastUse() == aParseNode.isLastUse();
	}

	@Override @AvailMethod
	void o_EmitValueOn (
		final AvailObject object,
		final AvailCodeGenerator codeGenerator)
	{
		final AvailObject declaration = object.slot(DECLARATION);
		declaration.declarationKind().emitVariableValueForOn(
			declaration,
			codeGenerator);
	}

	@Override @AvailMethod
	void o_ChildrenMap (
		final AvailObject object,
		final Transformer1<AvailObject, AvailObject> aBlock)
	{
		// Do nothing.
	}

	@Override @AvailMethod
	void o_ChildrenDo (
		final AvailObject object,
		final Continuation1<AvailObject> aBlock)
	{
		// Do nothing.
	}

	@Override @AvailMethod
	void o_ValidateLocally (
		final AvailObject object,
		final @Nullable AvailObject parent)
	{
		// Do nothing.
	}

	@Override
	ParseNodeKind o_ParseNodeKind (final AvailObject object)
	{
		return VARIABLE_USE_NODE;
	}

	@Override
	public void printObjectOnAvoidingIndent (
		final AvailObject object,
		final StringBuilder builder,
		final List<AvailObject> recursionList,
		final int indent)
	{
		builder.append(object.slot(USE_TOKEN).string().asNativeString());
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
		final AvailObject newUse = mutable.create();
		newUse.setSlot(USE_TOKEN, theToken);
		newUse.setSlot(DECLARATION, declaration);
		newUse.setSlot(FLAGS, 0);
		newUse.makeShared();
		return newUse;
	}

	/**
	 * Construct a new {@link VariableUseNodeDescriptor}.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 */
	private VariableUseNodeDescriptor (final Mutability mutability)
	{
		super(mutability);
	}

	/** The mutable {@link VariableUseNodeDescriptor}. */
	private static final VariableUseNodeDescriptor mutable =
		new VariableUseNodeDescriptor(Mutability.MUTABLE);

	@Override
	VariableUseNodeDescriptor mutable ()
	{
		return mutable;
	}

	/** The shared {@link VariableUseNodeDescriptor}. */
	private static final VariableUseNodeDescriptor shared =
		new VariableUseNodeDescriptor(Mutability.IMMUTABLE);

	@Override
	VariableUseNodeDescriptor shared ()
	{
		return shared;
	}
}
