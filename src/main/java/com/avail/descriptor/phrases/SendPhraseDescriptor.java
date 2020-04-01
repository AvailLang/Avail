/*
 * SendPhraseDescriptor.java
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
import com.avail.descriptor.representation.A_BasicObject;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.JavaCompatibility.ObjectSlotsEnumJava;
import com.avail.descriptor.atoms.A_Atom;
import com.avail.descriptor.bundles.A_Bundle;
import com.avail.descriptor.bundles.MessageBundleDescriptor;
import com.avail.descriptor.methods.MethodDescriptor;
import com.avail.descriptor.representation.Mutability;
import com.avail.descriptor.tokens.A_Token;
import com.avail.descriptor.tuples.A_Tuple;
import com.avail.descriptor.types.A_Type;
import com.avail.descriptor.types.PhraseTypeDescriptor.PhraseKind;
import com.avail.descriptor.types.TypeDescriptor;
import com.avail.descriptor.types.TypeDescriptor.Types;
import com.avail.descriptor.types.TypeTag;
import com.avail.serialization.SerializerOperation;
import com.avail.utility.evaluation.Continuation1NotNull;
import com.avail.utility.evaluation.Transformer1;
import com.avail.utility.json.JSONWriter;

import javax.annotation.Nullable;
import java.util.IdentityHashMap;

import static com.avail.descriptor.AvailObject.multiplier;
import static com.avail.descriptor.phrases.SendPhraseDescriptor.ObjectSlots.*;
import static com.avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.LIST_PHRASE;
import static com.avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.SEND_PHRASE;

/**
 * My instances represent invocations of multi-methods in Avail code.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class SendPhraseDescriptor
extends PhraseDescriptor
{
	/**
	 * My slots of type {@link AvailObject}.
	 */
	public enum ObjectSlots implements ObjectSlotsEnumJava
	{
		/**
		 * The {@linkplain A_Tuple tuple} of {@linkplain A_Token tokens} that
		 * comprise this {@linkplain SendPhraseDescriptor send}.
		 */
		TOKENS,

		/**
		 * A {@link ListPhraseDescriptor list phrase} containing the expressions
		 * that yield the arguments of the method invocation.
		 */
		ARGUMENTS_LIST_NODE,

		/**
		 * The {@linkplain MessageBundleDescriptor message bundle} that this
		 * send was intended to invoke.  Technically, it's the {@linkplain
		 * MethodDescriptor method} inside the bundle that will be invoked, so
		 * the bundle gets stripped off when generating a raw function from a
		 * {@linkplain BlockPhraseDescriptor block phrase} containing this send.
		 */
		BUNDLE,

		/**
		 * What {@linkplain TypeDescriptor type} of {@linkplain AvailObject
		 * object} this method invocation must return.
		 */
		RETURN_TYPE
	}

	@Override
	public void printObjectOnAvoidingIndent (
		final AvailObject object,
		final StringBuilder builder,
		final IdentityHashMap<A_BasicObject, Void> recursionMap,
		final int indent)
	{
		object.bundle().messageSplitter().printSendNodeOnIndent(
			object, builder, indent);
	}

	@Override @AvailMethod
	protected A_Atom o_ApparentSendName (final AvailObject object)
	{
		final A_Bundle bundle = object.slot(BUNDLE);
		return bundle.message();
	}

	@Override @AvailMethod
	protected A_Phrase o_ArgumentsListNode (final AvailObject object)
	{
		return object.slot(ARGUMENTS_LIST_NODE);
	}

	@Override @AvailMethod
	protected A_Bundle o_Bundle (final AvailObject object)
	{
		return object.slot(BUNDLE);
	}

	@Override @AvailMethod
	protected void o_ChildrenDo (
		final AvailObject object,
		final Continuation1NotNull<A_Phrase> action)
	{
		action.value(object.slot(ARGUMENTS_LIST_NODE));
	}

	@Override @AvailMethod
	protected void o_ChildrenMap (
		final AvailObject object,
		final Transformer1<A_Phrase, A_Phrase> transformer)
	{
		object.setSlot(
			ARGUMENTS_LIST_NODE,
			transformer.valueNotNull(object.slot(ARGUMENTS_LIST_NODE)));
	}

	@Override @AvailMethod
	protected void o_EmitValueOn (
		final AvailObject object,
		final AvailCodeGenerator codeGenerator)
	{
		final A_Bundle bundle = object.slot(BUNDLE);
		final int argCount = bundle.bundleMethod().numArgs();
		final A_Phrase arguments = object.slot(ARGUMENTS_LIST_NODE);
		arguments.emitAllValuesOn(codeGenerator);
		final A_Type superUnionType = arguments.superUnionType();
		if (superUnionType.isBottom())
		{
			codeGenerator.emitCall(
				object.tokens(), argCount, bundle, object.expressionType());
		}
		else
		{
			codeGenerator.emitSuperCall(
				object.tokens(),
				argCount,
				bundle,
				object.expressionType(),
				superUnionType);
		}
	}

	@Override @AvailMethod
	protected boolean o_EqualsPhrase (
		final AvailObject object,
		final A_Phrase aPhrase)
	{
		return !aPhrase.isMacroSubstitutionNode()
			&& object.phraseKind().equals(aPhrase.phraseKind())
			&& object.slot(BUNDLE).equals(aPhrase.bundle())
			&& object.slot(ARGUMENTS_LIST_NODE).equals(
				aPhrase.argumentsListNode())
			&& object.slot(RETURN_TYPE).equals(aPhrase.expressionType());
	}

	@Override @AvailMethod
	protected A_Type o_ExpressionType (final AvailObject object)
	{
		return object.slot(RETURN_TYPE);
	}

	@Override @AvailMethod
	public int o_Hash (final AvailObject object)
	{
		return
			(object.slot(ARGUMENTS_LIST_NODE).hash() * multiplier
				^ object.slot(BUNDLE).hash()) * multiplier
				- object.slot(RETURN_TYPE).hash()
			^ 0x90E39B4D;
	}

	@Override
	protected PhraseKind o_PhraseKind (final AvailObject object)
	{
		return SEND_PHRASE;
	}

	@Override
	protected SerializerOperation o_SerializerOperation (
		final AvailObject object)
	{
		return SerializerOperation.SEND_PHRASE;
	}

	@Override
	protected void o_StatementsDo (
		final AvailObject object,
		final Continuation1NotNull<A_Phrase> continuation)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Tuple o_Tokens (final AvailObject object)
	{
		return object.slot(TOKENS);
	}

	@Override @AvailMethod
	protected void o_ValidateLocally (
		final AvailObject object,
		final @Nullable A_Phrase parent)
	{
		// Do nothing.
	}

	@Override
	protected void o_WriteSummaryTo (final AvailObject object, final JSONWriter writer)
	{
		writer.startObject();
		writer.write("kind");
		writer.write("send phrase");
		writer.write("arguments");
		object.slot(ARGUMENTS_LIST_NODE).writeSummaryTo(writer);
		writer.write("bundle");
		object.slot(BUNDLE).writeSummaryTo(writer);
		writer.write("return type");
		object.slot(RETURN_TYPE).writeSummaryTo(writer);
		writer.endObject();
	}

	@Override
	protected void o_WriteTo (final AvailObject object, final JSONWriter writer)
	{
		writer.startObject();
		writer.write("kind");
		writer.write("send phrase");
		writer.write("tokens");
		object.slot(TOKENS).writeTo(writer);
		writer.write("arguments");
		object.slot(ARGUMENTS_LIST_NODE).writeTo(writer);
		writer.write("bundle");
		object.slot(BUNDLE).writeTo(writer);
		writer.write("return type");
		object.slot(RETURN_TYPE).writeTo(writer);
		writer.endObject();
	}

	/**
	 * Create a new {@linkplain SendPhraseDescriptor send phrase} from the specified
	 * {@linkplain MethodDescriptor method}, {@linkplain ListPhraseDescriptor
	 * list phrase} of argument expressions, and return {@linkplain TypeDescriptor
	 * type}.
	 *
	 * @param tokens
	 *        The {@linkplain A_Tuple tuple} of {@linkplain A_Token tokens} that
	 *        comprise the {@linkplain SendPhraseDescriptor send}.
	 * @param bundle
	 *        The method bundle for which this represents an invocation.
	 * @param argsListNode
	 *        A {@linkplain ListPhraseDescriptor list phrase} of argument
	 *        expressions.
	 * @param returnType
	 *        The target method's expected return type.
	 * @return A new send phrase.
	 */
	public static A_Phrase newSendNode (
		final A_Tuple tokens,
		final A_Bundle bundle,
		final A_Phrase argsListNode,
		final A_Type returnType)
	{
		assert bundle.isInstanceOfKind(Types.MESSAGE_BUNDLE.o());
		assert argsListNode.phraseKindIsUnder(LIST_PHRASE);
		final AvailObject newObject = mutable.create();
		newObject.setSlot(TOKENS, tokens);
		newObject.setSlot(ARGUMENTS_LIST_NODE, argsListNode);
		newObject.setSlot(BUNDLE, bundle);
		newObject.setSlot(RETURN_TYPE, returnType);
		return newObject.makeShared();
	}

	/**
	 * Construct a new {@link SendPhraseDescriptor}.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 */
	private SendPhraseDescriptor (final Mutability mutability)
	{
		super(mutability, TypeTag.SEND_PHRASE_TAG, ObjectSlots.class, null);
	}

	/** The mutable {@link SendPhraseDescriptor}. */
	private static final SendPhraseDescriptor mutable =
		new SendPhraseDescriptor(Mutability.MUTABLE);

	@Override
	public SendPhraseDescriptor mutable ()
	{
		return mutable;
	}

	/** The shared {@link SendPhraseDescriptor}. */
	private static final SendPhraseDescriptor shared =
		new SendPhraseDescriptor(Mutability.SHARED);

	@Override
	public SendPhraseDescriptor shared ()
	{
		return shared;
	}
}
