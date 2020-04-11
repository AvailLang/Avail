/*
 * ReferencePhraseDescriptor.java
 * Copyright © 1993-2020, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice, this
 *     list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice, this
 *     list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
 *
 *  * Neither the name of the copyright holder nor the names of the contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
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

package com.avail.descriptor.phrases;

import com.avail.annotations.AvailMethod;
import com.avail.compiler.AvailCodeGenerator;
import com.avail.descriptor.representation.AvailObject;
import com.avail.descriptor.JavaCompatibility.ObjectSlotsEnumJava;
import com.avail.descriptor.phrases.DeclarationPhraseDescriptor.DeclarationKind;
import com.avail.descriptor.representation.A_BasicObject;
import com.avail.descriptor.representation.Mutability;
import com.avail.descriptor.tuples.A_Tuple;
import com.avail.descriptor.types.A_Type;
import com.avail.descriptor.types.PhraseTypeDescriptor.PhraseKind;
import com.avail.descriptor.types.TypeTag;
import com.avail.serialization.SerializerOperation;
import com.avail.utility.evaluation.Continuation1NotNull;
import com.avail.utility.evaluation.Transformer1;
import com.avail.utility.json.JSONWriter;

import javax.annotation.Nullable;
import java.util.IdentityHashMap;

import static com.avail.descriptor.representation.AvailObject.error;
import static com.avail.descriptor.phrases.ReferencePhraseDescriptor.ObjectSlots.VARIABLE;
import static com.avail.descriptor.types.InstanceTypeDescriptor.instanceType;
import static com.avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.REFERENCE_PHRASE;
import static com.avail.descriptor.types.VariableTypeDescriptor.variableTypeFor;

/**
 * My instances represent a reference-taking expression.  A variable itself is
 * to be pushed on the stack.  Note that this does not work for arguments or
 * constants or labels, as no actual variable object is created for those.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class ReferencePhraseDescriptor
extends PhraseDescriptor
{
	/**
	 * My slots of type {@link AvailObject}.
	 *
	 * @author Mark van Gulik &lt;mark@availlang.org&gt;
	 */
	public enum ObjectSlots implements ObjectSlotsEnumJava
	{
		/**
		 * The {@linkplain VariableUsePhraseDescriptor variable use phrase} for
		 * which the {@linkplain ReferencePhraseDescriptor reference} is being
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
	public A_Phrase o_Variable (final AvailObject object)
	{
		return object.slot(VARIABLE);
	}

	/**
	 * The value I represent is a variable itself.  Answer an appropriate
	 * variable type.
	 */
	@Override @AvailMethod
	public A_Type o_ExpressionType (final AvailObject object)
	{
		final A_Phrase variable = object.slot(VARIABLE);
		final A_Phrase declaration = variable.declaration();
		final DeclarationKind kind = declaration.declarationKind();
		if (kind == DeclarationKind.MODULE_VARIABLE)
		{
			return
				instanceType(declaration.literalObject());
		}
		return variableTypeFor(variable.expressionType());
	}

	@Override @AvailMethod
	public boolean o_EqualsPhrase (
		final AvailObject object,
		final A_Phrase aPhrase)
	{
		return !aPhrase.isMacroSubstitutionNode()
			&& object.phraseKind().equals(aPhrase.phraseKind())
			&& object.slot(VARIABLE).equals(aPhrase.variable());
	}

	@Override @AvailMethod
	public int o_Hash (final AvailObject object)
	{
		return object.variable().hash() ^ 0xE7FA9B3F;
	}

	@Override @AvailMethod
	public void o_EmitValueOn (
		final AvailObject object,
		final AvailCodeGenerator codeGenerator)
	{
		final A_Phrase declaration = object.slot(VARIABLE).declaration();
		declaration.declarationKind().emitVariableReferenceForOn(
			object.tokens(), declaration, codeGenerator);
	}

	@Override @AvailMethod
	public void o_ChildrenMap (
		final AvailObject object,
		final Transformer1<A_Phrase, A_Phrase> transformer)
	{
		object.setSlot(VARIABLE, transformer.valueNotNull(object.slot(VARIABLE)));
	}

	@Override @AvailMethod
	public void o_ChildrenDo (
		final AvailObject object,
		final Continuation1NotNull<A_Phrase> action)
	{
		action.value(object.slot(VARIABLE));
	}

	@Override
	public void o_StatementsDo (
		final AvailObject object,
		final Continuation1NotNull<A_Phrase> continuation)
	{
		throw unsupportedOperationException();
	}

	@Override @AvailMethod
	public void o_ValidateLocally (
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
	public PhraseKind o_PhraseKind (final AvailObject object)
	{
		return REFERENCE_PHRASE;
	}

	@Override
	public SerializerOperation o_SerializerOperation (
		final AvailObject object)
	{
		return SerializerOperation.REFERENCE_PHRASE;
	}

	@Override
	public A_Tuple o_Tokens (final AvailObject object)
	{
		return object.slot(VARIABLE).tokens();
	}

	@Override
	public void o_WriteTo (final AvailObject object, final JSONWriter writer)
	{
		writer.startObject();
		writer.write("kind");
		writer.write("variable reference phrase");
		writer.write("referent");
		object.slot(VARIABLE).writeTo(writer);
		writer.endObject();
	}

	@Override
	public void o_WriteSummaryTo (final AvailObject object, final JSONWriter writer)
	{
		writer.startObject();
		writer.write("kind");
		writer.write("variable reference phrase");
		writer.write("referent");
		object.slot(VARIABLE).writeSummaryTo(writer);
		writer.endObject();
	}

	/**
	 * Create a new {@linkplain ReferencePhraseDescriptor reference phrase} from
	 * the given {@linkplain VariableUsePhraseDescriptor variable use phrase}.
	 *
	 * @param variableUse
	 *        A variable use phrase for which to construct a reference phrase.
	 * @return The new reference phrase.
	 */
	public static A_Phrase referenceNodeFromUse (final A_Phrase variableUse)
	{
		final AvailObject newReferenceNode = mutable.create();
		newReferenceNode.setSlot(VARIABLE, variableUse);
		newReferenceNode.makeShared();
		return newReferenceNode;
	}

	/**
	 * Construct a new {@link ReferencePhraseDescriptor}.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 */
	public ReferencePhraseDescriptor (final Mutability mutability)
	{
		super(
			mutability, TypeTag.REFERENCE_PHRASE_TAG, ObjectSlots.class, null);
	}

	/** The mutable {@link ReferencePhraseDescriptor}. */
	private static final ReferencePhraseDescriptor mutable =
		new ReferencePhraseDescriptor(Mutability.MUTABLE);

	@Override
	public ReferencePhraseDescriptor mutable ()
	{
		return mutable;
	}

	/** The shared {@link ReferencePhraseDescriptor}. */
	private static final ReferencePhraseDescriptor shared =
		new ReferencePhraseDescriptor(Mutability.SHARED);

	@Override
	public ReferencePhraseDescriptor shared ()
	{
		return shared;
	}
}
