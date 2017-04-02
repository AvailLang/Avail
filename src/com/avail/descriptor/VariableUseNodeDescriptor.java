/**
 * VariableUseNodeDescriptor.java
 * Copyright © 1993-2017, The Avail Foundation, LLC.
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
import java.util.IdentityHashMap;

import com.avail.annotations.AvailMethod;
import com.avail.compiler.AvailCodeGenerator;
import com.avail.descriptor.ParseNodeTypeDescriptor.ParseNodeKind;
import com.avail.serialization.SerializerOperation;
import com.avail.utility.evaluation.*;
import com.avail.utility.json.JSONWriter;
import org.jetbrains.annotations.Nullable;

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
	A_Token o_Token (final AvailObject object)
	{
		return object.slot(USE_TOKEN);
	}

	@Override @AvailMethod
	A_Phrase o_Declaration (final AvailObject object)
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
	A_Type o_ExpressionType (final AvailObject object)
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
		final A_Phrase aParseNode)
	{
		return !aParseNode.isMacroSubstitutionNode()
			&& object.parseNodeKind().equals(aParseNode.parseNodeKind())
			&& object.slot(USE_TOKEN).equals(aParseNode.token())
			&& object.slot(DECLARATION).equals(aParseNode.declaration())
			&& object.isLastUse() == aParseNode.isLastUse();
	}

	@Override @AvailMethod
	void o_EmitValueOn (
		final AvailObject object,
		final AvailCodeGenerator codeGenerator)
	{
		final A_Phrase declaration = object.slot(DECLARATION);
		declaration.declarationKind().emitVariableValueForOn(
			declaration,
			codeGenerator);
	}

	@Override @AvailMethod
	void o_ChildrenDo (
		final AvailObject object,
		final Continuation1<A_Phrase> aBlock)
	{
		aBlock.value(object.slot(DECLARATION));
	}

	@Override @AvailMethod
	void o_ChildrenMap (
		final AvailObject object,
		final Transformer1<A_Phrase, A_Phrase> aBlock)
	{
		object.setSlot(
			DECLARATION, aBlock.valueNotNull(object.slot(DECLARATION)));
	}

	@Override
	void o_StatementsDo (
		final AvailObject object,
		final Continuation1<A_Phrase> continuation)
	{
		throw unsupportedOperationException();
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
		return VARIABLE_USE_NODE;
	}

	@Override
	SerializerOperation o_SerializerOperation (final AvailObject object)
	{
		return SerializerOperation.VARIABLE_USE_PHRASE;
	}

	@Override
	void o_WriteTo (final AvailObject object, final JSONWriter writer)
	{
		writer.startObject();
		writer.write("kind");
		writer.write("variable use phrase");
		writer.write("token");
		object.slot(USE_TOKEN).writeTo(writer);
		writer.write("declaration");
		object.slot(DECLARATION).writeTo(writer);
		writer.endObject();
	}

	@Override
	void o_WriteSummaryTo (final AvailObject object, final JSONWriter writer)
	{
		writer.startObject();
		writer.write("kind");
		writer.write("variable use phrase");
		writer.write("token");
		object.slot(USE_TOKEN).writeSummaryTo(writer);
		writer.write("declaration");
		object.slot(DECLARATION).writeSummaryTo(writer);
		writer.endObject();
	}

	@Override
	public void printObjectOnAvoidingIndent (
		final AvailObject object,
		final StringBuilder builder,
		final IdentityHashMap<A_BasicObject, Void> recursionMap,
		final int indent)
	{
		final A_Token useToken = object.slot(USE_TOKEN);
		builder.append(useToken.string().asNativeString());
	}

	/**
	 * Construct a new {@linkplain VariableUseNodeDescriptor variable use node}.
	 *
	 * @param theToken The token which is the use of the variable in the source.
	 * @param declaration The declaration which is being used.
	 * @return A new variable use node.
	 */
	public static AvailObject newUse (
		final A_Token theToken,
		final A_Phrase declaration)
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
		super(
			mutability,
			TypeTag.VARIABLE_USE_PHRASE_TAG,
			ObjectSlots.class,
			IntegerSlots.class);
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
