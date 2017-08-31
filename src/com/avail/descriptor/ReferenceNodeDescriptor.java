/**
 * ReferenceNodeDescriptor.java
 * Copyright © 1993-2017, The Avail Foundation, LLC.
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

import static com.avail.descriptor.AvailObject.error;
import static com.avail.descriptor.ParseNodeTypeDescriptor.ParseNodeKind.*;
import static com.avail.descriptor.ReferenceNodeDescriptor.ObjectSlots.*;
import java.util.IdentityHashMap;

import com.avail.annotations.AvailMethod;
import com.avail.compiler.AvailCodeGenerator;
import com.avail.descriptor.DeclarationNodeDescriptor.DeclarationKind;
import com.avail.descriptor.ParseNodeTypeDescriptor.ParseNodeKind;
import com.avail.serialization.SerializerOperation;
import com.avail.utility.evaluation.*;
import com.avail.utility.json.JSONWriter;
import javax.annotation.Nullable;

/**
 * My instances represent a reference-taking expression.  A variable itself is
 * to be pushed on the stack.  Note that this does not work for arguments or
 * constants or labels, as no actual variable object is created for those.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class ReferenceNodeDescriptor
extends ParseNodeDescriptor
{
	/**
	 * My slots of type {@link AvailObject}.
	 *
	 * @author Mark van Gulik &lt;mark@availlang.org&gt;
	 */
	public enum ObjectSlots
	implements ObjectSlotsEnum
	{
		/**
		 * The {@linkplain VariableUseNodeDescriptor variable use node} for
		 * which the {@linkplain ReferenceNodeDescriptor reference} is being
		 * taken.
		 */
		VARIABLE
	}

	@Override
	public void printObjectOnAvoidingIndent (
		final AvailObject object,
		final StringBuilder builder,
		final IdentityHashMap<A_BasicObject, Void> recursionMap,
		final int indent)
	{
		builder.append("↑");
		builder.append(object.slot(VARIABLE).token().string().asNativeString());
	}

	@Override @AvailMethod
	A_Phrase o_Variable (final AvailObject object)
	{
		return object.slot(VARIABLE);
	}

	/**
	 * The value I represent is a variable itself.  Answer an appropriate
	 * variable type.
	 */
	@Override @AvailMethod
	A_Type o_ExpressionType (final AvailObject object)
	{
		final A_Phrase variable = object.slot(VARIABLE);
		final A_Phrase declaration = variable.declaration();
		final DeclarationKind kind = declaration.declarationKind();
		if (kind == DeclarationKind.MODULE_VARIABLE)
		{
			return InstanceTypeDescriptor.on(declaration.literalObject());
		}
		assert kind == DeclarationKind.LOCAL_VARIABLE;
		return VariableTypeDescriptor.wrapInnerType(variable.expressionType());
	}

	@Override @AvailMethod
	boolean o_EqualsParseNode (
		final AvailObject object,
		final A_Phrase aParseNode)
	{
		return !aParseNode.isMacroSubstitutionNode()
			&& object.parseNodeKind().equals(aParseNode.parseNodeKind())
			&& object.slot(VARIABLE).equals(aParseNode.variable());
	}

	@Override @AvailMethod
	int o_Hash (final AvailObject object)
	{
		return object.variable().hash() ^ 0xE7FA9B3F;
	}

	@Override @AvailMethod
	void o_EmitValueOn (
		final AvailObject object,
		final AvailCodeGenerator codeGenerator)
	{
		final A_Phrase declaration = object.slot(VARIABLE).declaration();
		declaration.declarationKind().emitVariableReferenceForOn(
			declaration,
			codeGenerator);
	}

	@Override @AvailMethod
	void o_ChildrenMap (
		final AvailObject object,
		final Transformer1<A_Phrase, A_Phrase> aBlock)
	{
		object.setSlot(VARIABLE, aBlock.valueNotNull(object.slot(VARIABLE)));
	}

	@Override @AvailMethod
	void o_ChildrenDo (
		final AvailObject object,
		final Continuation1<A_Phrase> aBlock)
	{
		aBlock.value(object.slot(VARIABLE));
	}

	@Override
	void o_StatementsDo (
		final AvailObject object,
		final Continuation1NotNull<A_Phrase> continuation)
	{
		throw unsupportedOperationException();
	}

	@Override @AvailMethod
	void o_ValidateLocally (
		final AvailObject object,
		final @Nullable A_Phrase parent)
	{
		final A_BasicObject decl = object.slot(VARIABLE).declaration();
		switch (decl.declarationKind())
		{
			case ARGUMENT:
				error("You can't take the reference of an argument");
				break;
			case LABEL:
				error("You can't take the reference of a label");
				break;
			case LOCAL_CONSTANT:
			case MODULE_CONSTANT:
			case PRIMITIVE_FAILURE_REASON:
				error("You can't take the reference of a constant");
				break;
			case LOCAL_VARIABLE:
			case MODULE_VARIABLE:
				// Do nothing.
				break;
		}
	}

	@Override @AvailMethod
	ParseNodeKind o_ParseNodeKind (final AvailObject object)
	{
		return REFERENCE_NODE;
	}

	@Override
	SerializerOperation o_SerializerOperation (final AvailObject object)
	{
		return SerializerOperation.REFERENCE_PHRASE;
	}

	@Override
	void o_WriteTo (final AvailObject object, final JSONWriter writer)
	{
		writer.startObject();
		writer.write("kind");
		writer.write("variable reference phrase");
		writer.write("referent");
		object.slot(VARIABLE).writeTo(writer);
		writer.endObject();
	}

	@Override
	void o_WriteSummaryTo (final AvailObject object, final JSONWriter writer)
	{
		writer.startObject();
		writer.write("kind");
		writer.write("variable reference phrase");
		writer.write("referent");
		object.slot(VARIABLE).writeSummaryTo(writer);
		writer.endObject();
	}

	/**
	 * Create a new {@linkplain ReferenceNodeDescriptor reference node} from the
	 * given {@linkplain VariableUseNodeDescriptor variable use node}.
	 *
	 * @param variableUse
	 *        A variable use node for which to construct a reference node.
	 * @return The new reference node.
	 */
	public static A_Phrase fromUse (final A_Phrase variableUse)
	{
		final AvailObject newReferenceNode = mutable.create();
		newReferenceNode.setSlot(VARIABLE, variableUse);
		newReferenceNode.makeShared();
		return newReferenceNode;
	}

	/**
	 * Construct a new {@link ReferenceNodeDescriptor}.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 */
	public ReferenceNodeDescriptor (final Mutability mutability)
	{
		super(
			mutability, TypeTag.REFERENCE_PHRASE_TAG, ObjectSlots.class, null);
	}

	/** The mutable {@link ReferenceNodeDescriptor}. */
	private static final ReferenceNodeDescriptor mutable =
		new ReferenceNodeDescriptor(Mutability.MUTABLE);

	@Override
	ReferenceNodeDescriptor mutable ()
	{
		return mutable;
	}

	/** The shared {@link ReferenceNodeDescriptor}. */
	private static final ReferenceNodeDescriptor shared =
		new ReferenceNodeDescriptor(Mutability.SHARED);

	@Override
	ReferenceNodeDescriptor shared ()
	{
		return shared;
	}
}
