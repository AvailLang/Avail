/*
 * MacroSubstitutionPhraseDescriptor.java
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
import com.avail.descriptor.A_Module;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.JavaCompatibility.ObjectSlotsEnumJava;
import com.avail.descriptor.atoms.A_Atom;
import com.avail.descriptor.bundles.A_Bundle;
import com.avail.descriptor.functions.A_RawFunction;
import com.avail.descriptor.methods.MacroDefinitionDescriptor;
import com.avail.descriptor.representation.A_BasicObject;
import com.avail.descriptor.representation.Mutability;
import com.avail.descriptor.sets.A_Set;
import com.avail.descriptor.tokens.A_Token;
import com.avail.descriptor.tuples.A_Tuple;
import com.avail.descriptor.types.A_Type;
import com.avail.descriptor.types.PhraseTypeDescriptor.PhraseKind;
import com.avail.descriptor.types.TypeTag;
import com.avail.interpreter.Primitive;
import com.avail.serialization.SerializerOperation;
import com.avail.utility.evaluation.Continuation1NotNull;
import com.avail.utility.evaluation.Transformer1;
import com.avail.utility.json.JSONWriter;
import kotlin.Unit;

import javax.annotation.Nullable;
import java.util.IdentityHashMap;
import java.util.List;

import static com.avail.descriptor.AvailObject.multiplier;
import static com.avail.descriptor.phrases.MacroSubstitutionPhraseDescriptor.ObjectSlots.MACRO_ORIGINAL_SEND;
import static com.avail.descriptor.phrases.MacroSubstitutionPhraseDescriptor.ObjectSlots.OUTPUT_PARSE_NODE;

/**
 * A {@code MacroSubstitutionPhraseDescriptor macro substitution phrase}
 * represents the result of applying a {@linkplain MacroDefinitionDescriptor
 * macro} to its argument {@linkplain PhraseDescriptor expressions} to produce
 * an {@linkplain ObjectSlots#OUTPUT_PARSE_NODE output phrase}.
 *
 * <p> It's kept around specifically to allow grammatical restrictions to
 * operate on the actual occurring macro (and method) names, not what they've
 * turned into. As such, the macro substitution phrase should be {@linkplain
 * #o_StripMacro(AvailObject) stripped off} prior to being composed into a
 * larger parse tree, whether a send phrase, another macro invocation, or direct
 * embedding within an assignment statement, variable reference, or any other
 * hierarchical parsing structure. </p>
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class MacroSubstitutionPhraseDescriptor
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
		 * The {@linkplain SendPhraseDescriptor send phrase} prior to its
		 * transformation into the {@link #OUTPUT_PARSE_NODE}.
		 */
		MACRO_ORIGINAL_SEND,

		/**
		 * The {@linkplain PhraseDescriptor phrase} that is the result of
		 * transforming the input phrase through a {@linkplain
		 * MacroDefinitionDescriptor macro} substitution.
		 */
		OUTPUT_PARSE_NODE
	}

	@Override
	public void printObjectOnAvoidingIndent (
		final AvailObject object,
		final StringBuilder builder,
		final IdentityHashMap<A_BasicObject, Void> recursionMap,
		final int indent)
	{
		final A_Phrase outputPhrase = object.slot(OUTPUT_PARSE_NODE);
		outputPhrase.printOnAvoidingIndent(builder, recursionMap, indent);
	}

	@Override @AvailMethod
	protected A_Atom o_ApparentSendName (final AvailObject object)
	{
		return object.slot(MACRO_ORIGINAL_SEND).apparentSendName();
	}

	@Override
	protected A_Phrase o_ArgumentsListNode (final AvailObject object)
	{
		return object.slot(OUTPUT_PARSE_NODE).argumentsListNode();
	}

	@Override
	protected A_Tuple o_ArgumentsTuple (final AvailObject object)
	{
		return object.slot(OUTPUT_PARSE_NODE).argumentsTuple();
	}

	@Override
	protected A_Bundle o_Bundle (final AvailObject object)
	{
		// Reach into the output phrase.  If you want the macro name, use the
		// apparentSendName instead.
		return object.slot(OUTPUT_PARSE_NODE).bundle();
	}

	@Override @AvailMethod
	protected void o_ChildrenDo (
		final AvailObject object,
		final Continuation1NotNull<A_Phrase> action)
	{
		action.value(object.slot(OUTPUT_PARSE_NODE));
	}

	@Override @AvailMethod
	protected void o_ChildrenMap (
		final AvailObject object,
		final Transformer1<A_Phrase, A_Phrase> transformer)
	{
		// Don't transform the original phrase, just the output phrase.
		object.setSlot(
			OUTPUT_PARSE_NODE,
			transformer.valueNotNull(object.slot(OUTPUT_PARSE_NODE)));
	}

	@Override
	protected A_Phrase o_CopyWith (
		final AvailObject object, final A_Phrase newPhrase)
	{
		// Create a copy the list, not this macro substitution.
		return object.slot(OUTPUT_PARSE_NODE).copyWith(newPhrase);
	}

	@Override @AvailMethod
	protected A_Phrase o_CopyConcatenating (
		final AvailObject object, final A_Phrase newListPhrase)
	{
		// Create a copy the list, not this macro substitution.
		return object.slot(OUTPUT_PARSE_NODE).copyConcatenating(newListPhrase);
	}

	@Override
	protected A_Phrase o_Declaration (final AvailObject object)
	{
		return object.slot(OUTPUT_PARSE_NODE).declaration();
	}

	@Override
	protected A_Set o_DeclaredExceptions (final AvailObject object)
	{
		return object.slot(OUTPUT_PARSE_NODE).declaredExceptions();
	}

	@Override
	protected A_Type o_DeclaredType (final AvailObject object)
	{
		return object.slot(OUTPUT_PARSE_NODE).declaredType();
	}

	@Override
	protected void o_EmitAllValuesOn (
		final AvailObject object, final AvailCodeGenerator codeGenerator)
	{
		codeGenerator.setTokensWhile(
			object.slot(MACRO_ORIGINAL_SEND).tokens(),
			() ->
			{
				object.slot(OUTPUT_PARSE_NODE).emitAllValuesOn(codeGenerator);
				return Unit.INSTANCE;
			});
	}

	@Override @AvailMethod
	protected void o_EmitEffectOn (
		final AvailObject object,
		final AvailCodeGenerator codeGenerator)
	{
		codeGenerator.setTokensWhile(
			object.slot(MACRO_ORIGINAL_SEND).tokens(),
			() ->
			{
				object.slot(OUTPUT_PARSE_NODE).emitEffectOn(codeGenerator);
				return Unit.INSTANCE;
			});
	}

	@Override @AvailMethod
	protected void o_EmitValueOn (
		final AvailObject object,
		final AvailCodeGenerator codeGenerator)
	{
		codeGenerator.setTokensWhile(
			object.slot(MACRO_ORIGINAL_SEND).tokens(),
			() ->
			{
				object.slot(OUTPUT_PARSE_NODE).emitValueOn(codeGenerator);
				return Unit.INSTANCE;
			});
	}

	@Override @AvailMethod
	protected boolean o_EqualsPhrase (
		final AvailObject object,
		final A_Phrase aPhrase)
	{
		return aPhrase.isMacroSubstitutionNode()
			&& object.slot(MACRO_ORIGINAL_SEND).equals(
				aPhrase.macroOriginalSendNode())
			&& object.slot(OUTPUT_PARSE_NODE).equals(
				aPhrase.outputPhrase());
	}

	@Override
	protected A_Phrase o_Expression (final AvailObject object)
	{
		return object.slot(OUTPUT_PARSE_NODE).expression();
	}

	@Override
	protected A_Phrase o_ExpressionAt (final AvailObject object, final int index)
	{
		return object.slot(OUTPUT_PARSE_NODE).expressionAt(index);
	}

	@Override
	protected int o_ExpressionsSize (final AvailObject object)
	{
		return object.slot(OUTPUT_PARSE_NODE).expressionsSize();
	}

	@Override
	protected A_Tuple o_ExpressionsTuple (final AvailObject object)
	{
		return object.slot(OUTPUT_PARSE_NODE).expressionsTuple();
	}

	@Override @AvailMethod
	protected A_Type o_ExpressionType (final AvailObject object)
	{
		return object.slot(OUTPUT_PARSE_NODE).expressionType();
	}

	@Override @AvailMethod
	protected void o_FlattenStatementsInto (
		final AvailObject object,
		final List<A_Phrase> accumulatedStatements)
	{
		object.slot(OUTPUT_PARSE_NODE).flattenStatementsInto(
			accumulatedStatements);
	}

	@Override
	protected A_RawFunction o_GenerateInModule (
		final AvailObject object, final A_Module module)
	{
		return object.slot(OUTPUT_PARSE_NODE).generateInModule(module);
	}

	@Override @AvailMethod
	public int o_Hash (final AvailObject object)
	{
		return
			object.slot(MACRO_ORIGINAL_SEND).hash() * multiplier
				+ (object.slot(OUTPUT_PARSE_NODE).hash() ^ 0x1d50d7f9);
	}

	@Override
	protected boolean o_HasSuperCast (final AvailObject object)
	{
		return object.slot(OUTPUT_PARSE_NODE).hasSuperCast();
	}

	@Override
	protected AvailObject o_InitializationExpression (final AvailObject object)
	{
		return object.slot(OUTPUT_PARSE_NODE).initializationExpression();
	}

	@Override
	protected void o_IsLastUse (final AvailObject object, final boolean isLastUse)
	{
		object.slot(OUTPUT_PARSE_NODE).isLastUse(isLastUse);
	}

	@Override
	protected boolean o_IsLastUse (final AvailObject object)
	{
		return object.slot(OUTPUT_PARSE_NODE).isLastUse();
	}

	@Override
	protected boolean o_IsMacroSubstitutionNode (final AvailObject object)
	{
		return true;
	}

	@Override
	protected A_Phrase o_LastExpression (final AvailObject object)
	{
		return object.slot(OUTPUT_PARSE_NODE).lastExpression();
	}

	@Override
	protected A_Phrase o_List (final AvailObject object)
	{
		return object.slot(OUTPUT_PARSE_NODE).list();
	}

	@Override
	protected AvailObject o_LiteralObject (final AvailObject object)
	{
		return object.slot(OUTPUT_PARSE_NODE).literalObject();
	}

	@Override
	protected A_Phrase o_MacroOriginalSendNode (final AvailObject object)
	{
		return object.slot(MACRO_ORIGINAL_SEND);
	}

	@Override
	protected AvailObject o_MarkerValue (final AvailObject object)
	{
		return object.slot(OUTPUT_PARSE_NODE).markerValue();
	}

	@Override
	protected A_Tuple o_NeededVariables (final AvailObject object)
	{
		return object.slot(OUTPUT_PARSE_NODE).neededVariables();
	}

	@Override
	protected void o_NeededVariables (
		final AvailObject object, final A_Tuple neededVariables)
	{
		object.slot(OUTPUT_PARSE_NODE).neededVariables(neededVariables);
	}

	@Override @AvailMethod
	protected A_Phrase o_OutputPhrase (final AvailObject object)
	{
		return object.slot(OUTPUT_PARSE_NODE);
	}

	@Override
	protected PhraseKind o_PhraseKind (final AvailObject object)
	{
		// Answer the output phrase's kind, not this macro substitution's kind.
		return object.slot(OUTPUT_PARSE_NODE).phraseKind();
	}

	@Override
	protected boolean o_PhraseKindIsUnder (
		final AvailObject object, final PhraseKind expectedPhraseKind)
	{
		// Use the output phrase's kind, not this macro substitution's kind.
		return object.slot(OUTPUT_PARSE_NODE).phraseKindIsUnder(
			expectedPhraseKind);
	}

	@Override
	protected A_Tuple o_Permutation (final AvailObject object)
	{
		return object.slot(OUTPUT_PARSE_NODE).permutation();
	}

	@Override
	protected @Nullable Primitive o_Primitive (final AvailObject object)
	{
		return object.slot(OUTPUT_PARSE_NODE).primitive();
	}

	@Override
	protected SerializerOperation o_SerializerOperation (
		final AvailObject object)
	{
		return SerializerOperation.MACRO_SUBSTITUTION_PHRASE;
	}

	@Override
	protected int o_StartingLineNumber (final AvailObject object)
	{
		return object.slot(OUTPUT_PARSE_NODE).startingLineNumber();
	}

	@Override
	protected A_Tuple o_Statements (final AvailObject object)
	{
		return object.slot(OUTPUT_PARSE_NODE).statements();
	}

	@Override
	protected void o_StatementsDo (
		final AvailObject object,
		final Continuation1NotNull<A_Phrase> continuation)
	{
		object.slot(OUTPUT_PARSE_NODE).statementsDo(continuation);
	}

	@Override
	protected A_Tuple o_StatementsTuple (final AvailObject object)
	{
		return object.slot(OUTPUT_PARSE_NODE).statementsTuple();
	}

	@Override
	protected A_Phrase o_StripMacro (final AvailObject object)
	{
		return object.slot(OUTPUT_PARSE_NODE);
	}

	@Override
	protected A_Type o_SuperUnionType (final AvailObject object)
	{
		return object.slot(OUTPUT_PARSE_NODE).superUnionType();
	}

	@Override
	protected A_Token o_Token (final AvailObject object)
	{
		return object.slot(OUTPUT_PARSE_NODE).token();
	}

	@Override
	protected A_Tuple o_Tokens (final AvailObject object)
	{
		return object.slot(MACRO_ORIGINAL_SEND).tokens();
	}

	@Override
	protected A_Phrase o_TypeExpression (final AvailObject object)
	{
		return object.slot(OUTPUT_PARSE_NODE).typeExpression();
	}

	@Override @AvailMethod
	protected void o_ValidateLocally (
		final AvailObject object,
		final @Nullable A_Phrase parent)
	{
		object.slot(OUTPUT_PARSE_NODE).validateLocally(parent);
	}

	@Override
	protected A_Phrase o_Variable (final AvailObject object)
	{
		return object.slot(OUTPUT_PARSE_NODE).variable();
	}

	@Override
	protected void o_WriteTo (final AvailObject object, final JSONWriter writer)
	{
		writer.startObject();
		writer.write("kind");
		writer.write("macro substitution phrase");
		writer.write("macro send");
		object.slot(MACRO_ORIGINAL_SEND).writeTo(writer);
		writer.write("output phrase");
		object.slot(OUTPUT_PARSE_NODE).writeTo(writer);
		writer.endObject();
	}

	/**
	 * Construct a new macro substitution phrase.
	 *
	 * @param macroSend
	 *        The send of the macro that produced this phrase.
	 * @param outputPhrase
	 *        The expression produced by the macro body.
	 * @return The new macro substitution phrase.
	 */
	public static AvailObject newMacroSubstitution (
		final A_Phrase macroSend,
		final A_Phrase outputPhrase)
	{
		final AvailObject newNode = mutable.create();
		newNode.setSlot(MACRO_ORIGINAL_SEND, macroSend);
		newNode.setSlot(OUTPUT_PARSE_NODE, outputPhrase);
		newNode.makeShared();
		return newNode;
	}

	/**
	 * Construct a new {@code MacroSubstitutionPhraseDescriptor}.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 */
	public MacroSubstitutionPhraseDescriptor (final Mutability mutability)
	{
		super(
			mutability,
			TypeTag.MACRO_SUBSTITUTION_PHRASE_TAG,
			ObjectSlots.class,
			null);
	}

	/** The mutable {@link MacroSubstitutionPhraseDescriptor}. */
	private static final MacroSubstitutionPhraseDescriptor mutable =
		new MacroSubstitutionPhraseDescriptor(Mutability.MUTABLE);

	@Override
	public MacroSubstitutionPhraseDescriptor mutable ()
	{
		return mutable;
	}

	/** The immutable {@link MacroSubstitutionPhraseDescriptor}. */
	private static final MacroSubstitutionPhraseDescriptor shared =
		new MacroSubstitutionPhraseDescriptor(Mutability.SHARED);

	@Override
	public MacroSubstitutionPhraseDescriptor shared ()
	{
		return shared;
	}
}
