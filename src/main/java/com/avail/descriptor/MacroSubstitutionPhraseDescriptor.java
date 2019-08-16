/*
 * MacroSubstitutionNodeDescriptor.java
 * Copyright © 1993-2018, The Avail Foundation, LLC.
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

import com.avail.annotations.AvailMethod;
import com.avail.compiler.AvailCodeGenerator;
import com.avail.descriptor.PhraseTypeDescriptor.PhraseKind;
import com.avail.interpreter.Primitive;
import com.avail.serialization.SerializerOperation;
import com.avail.utility.evaluation.Continuation1NotNull;
import com.avail.utility.evaluation.Transformer1;
import com.avail.utility.json.JSONWriter;

import javax.annotation.Nullable;
import java.util.IdentityHashMap;
import java.util.List;

import static com.avail.descriptor.AvailObject.multiplier;
import static com.avail.descriptor.MacroSubstitutionPhraseDescriptor.ObjectSlots.MACRO_ORIGINAL_SEND;
import static com.avail.descriptor.MacroSubstitutionPhraseDescriptor.ObjectSlots.OUTPUT_PARSE_NODE;

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
	public enum ObjectSlots
	implements ObjectSlotsEnum
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
	A_Atom o_ApparentSendName (final AvailObject object)
	{
		return object.slot(MACRO_ORIGINAL_SEND).apparentSendName();
	}

	@Override
	A_Phrase o_ArgumentsListNode (final AvailObject object)
	{
		return object.slot(OUTPUT_PARSE_NODE).argumentsListNode();
	}

	@Override
	A_Tuple o_ArgumentsTuple (final AvailObject object)
	{
		return object.slot(OUTPUT_PARSE_NODE).argumentsTuple();
	}

	@Override
	A_Bundle o_Bundle (final AvailObject object)
	{
		// Reach into the output phrase.  If you want the macro name, use the
		// apparentSendName instead.
		return object.slot(OUTPUT_PARSE_NODE).bundle();
	}

	@Override @AvailMethod
	void o_ChildrenDo (
		final AvailObject object,
		final Continuation1NotNull<A_Phrase> action)
	{
		action.value(object.slot(OUTPUT_PARSE_NODE));
	}

	@Override @AvailMethod
	void o_ChildrenMap (
		final AvailObject object,
		final Transformer1<A_Phrase, A_Phrase> transformer)
	{
		// Don't transform the original phrase, just the output phrase.
		object.setSlot(
			OUTPUT_PARSE_NODE,
			transformer.valueNotNull(object.slot(OUTPUT_PARSE_NODE)));
	}

	@Override
	A_Phrase o_CopyWith (
		final AvailObject object, final A_Phrase newPhrase)
	{
		// Create a copy the list, not this macro substitution.
		return object.slot(OUTPUT_PARSE_NODE).copyWith(newPhrase);
	}

	@Override
	A_Phrase o_Declaration (final AvailObject object)
	{
		return object.slot(OUTPUT_PARSE_NODE).declaration();
	}

	@Override
	A_Set o_DeclaredExceptions (final AvailObject object)
	{
		return object.slot(OUTPUT_PARSE_NODE).declaredExceptions();
	}

	@Override
	A_Type o_DeclaredType (final AvailObject object)
	{
		return object.slot(OUTPUT_PARSE_NODE).declaredType();
	}

	@Override
	void o_EmitAllValuesOn (
		final AvailObject object, final AvailCodeGenerator codeGenerator)
	{
		codeGenerator.setTokensWhile(
			object.slot(MACRO_ORIGINAL_SEND).tokens(),
			() ->
				object.slot(OUTPUT_PARSE_NODE).emitAllValuesOn(codeGenerator));
	}

	@Override @AvailMethod
	void o_EmitEffectOn (
		final AvailObject object,
		final AvailCodeGenerator codeGenerator)
	{
		codeGenerator.setTokensWhile(
			object.slot(MACRO_ORIGINAL_SEND).tokens(),
			() -> object.slot(OUTPUT_PARSE_NODE).emitEffectOn(codeGenerator));
	}

	@Override @AvailMethod
	void o_EmitValueOn (
		final AvailObject object,
		final AvailCodeGenerator codeGenerator)
	{
		codeGenerator.setTokensWhile(
			object.slot(MACRO_ORIGINAL_SEND).tokens(),
			() ->
				object.slot(OUTPUT_PARSE_NODE).emitValueOn(codeGenerator));
	}

	@Override @AvailMethod
	boolean o_EqualsPhrase (
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
	A_Phrase o_Expression (final AvailObject object)
	{
		return object.slot(OUTPUT_PARSE_NODE).expression();
	}

	@Override
	A_Phrase o_ExpressionAt (final AvailObject object, final int index)
	{
		return object.slot(OUTPUT_PARSE_NODE).expressionAt(index);
	}

	@Override
	int o_ExpressionsSize (final AvailObject object)
	{
		return object.slot(OUTPUT_PARSE_NODE).expressionsSize();
	}

	@Override
	A_Tuple o_ExpressionsTuple (final AvailObject object)
	{
		return object.slot(OUTPUT_PARSE_NODE).expressionsTuple();
	}

	@Override @AvailMethod
	A_Type o_ExpressionType (final AvailObject object)
	{
		return object.slot(OUTPUT_PARSE_NODE).expressionType();
	}

	@Override @AvailMethod
	void o_FlattenStatementsInto (
		final AvailObject object,
		final List<A_Phrase> accumulatedStatements)
	{
		object.slot(OUTPUT_PARSE_NODE).flattenStatementsInto(
			accumulatedStatements);
	}

	@Override
	A_RawFunction o_GenerateInModule (
		final AvailObject object, final A_Module module)
	{
		return object.slot(OUTPUT_PARSE_NODE).generateInModule(module);
	}

	@Override @AvailMethod
	int o_Hash (final AvailObject object)
	{
		return
			object.slot(MACRO_ORIGINAL_SEND).hash() * multiplier
				+ (object.slot(OUTPUT_PARSE_NODE).hash() ^ 0x1d50d7f9);
	}

	@Override
	boolean o_HasSuperCast (final AvailObject object)
	{
		return object.slot(OUTPUT_PARSE_NODE).hasSuperCast();
	}

	@Override
	AvailObject o_InitializationExpression (final AvailObject object)
	{
		return object.slot(OUTPUT_PARSE_NODE).initializationExpression();
	}

	@Override
	void o_IsLastUse (final AvailObject object, final boolean isLastUse)
	{
		object.slot(OUTPUT_PARSE_NODE).isLastUse(isLastUse);
	}

	@Override
	boolean o_IsLastUse (final AvailObject object)
	{
		return object.slot(OUTPUT_PARSE_NODE).isLastUse();
	}

	@Override
	boolean o_IsMacroSubstitutionNode (final AvailObject object)
	{
		return true;
	}

	@Override
	A_Phrase o_LastExpression (final AvailObject object)
	{
		return object.slot(OUTPUT_PARSE_NODE).lastExpression();
	}

	@Override
	A_Phrase o_List (final AvailObject object)
	{
		return object.slot(OUTPUT_PARSE_NODE).list();
	}

	@Override
	AvailObject o_LiteralObject (final AvailObject object)
	{
		return object.slot(OUTPUT_PARSE_NODE).literalObject();
	}

	@Override
	A_Phrase o_MacroOriginalSendNode (final AvailObject object)
	{
		return object.slot(MACRO_ORIGINAL_SEND);
	}

	@Override
	AvailObject o_MarkerValue (final AvailObject object)
	{
		return object.slot(OUTPUT_PARSE_NODE).markerValue();
	}

	@Override
	A_Tuple o_NeededVariables (final AvailObject object)
	{
		return object.slot(OUTPUT_PARSE_NODE).neededVariables();
	}

	@Override
	void o_NeededVariables (
		final AvailObject object, final A_Tuple neededVariables)
	{
		object.slot(OUTPUT_PARSE_NODE).neededVariables(neededVariables);
	}

	@Override @AvailMethod
	A_Phrase o_OutputPhrase (final AvailObject object)
	{
		return object.slot(OUTPUT_PARSE_NODE);
	}

	@Override
	PhraseKind o_PhraseKind (final AvailObject object)
	{
		// Answer the output phrase's kind, not this macro substitution's kind.
		return object.slot(OUTPUT_PARSE_NODE).phraseKind();
	}

	@Override
	boolean o_PhraseKindIsUnder (
		final AvailObject object, final PhraseKind expectedPhraseKind)
	{
		// Use the output phrase's kind, not this macro substitution's kind.
		return object.slot(OUTPUT_PARSE_NODE).phraseKindIsUnder(
			expectedPhraseKind);
	}

	@Override
	A_Tuple o_Permutation (final AvailObject object)
	{
		return object.slot(OUTPUT_PARSE_NODE).permutation();
	}

	@Override
	@Nullable Primitive o_Primitive (final AvailObject object)
	{
		return object.slot(OUTPUT_PARSE_NODE).primitive();
	}

	@Override
	SerializerOperation o_SerializerOperation (final AvailObject object)
	{
		return SerializerOperation.MACRO_SUBSTITUTION_PHRASE;
	}

	@Override
	int o_StartingLineNumber (final AvailObject object)
	{
		return object.slot(OUTPUT_PARSE_NODE).startingLineNumber();
	}

	@Override
	A_Tuple o_Statements (final AvailObject object)
	{
		return object.slot(OUTPUT_PARSE_NODE).statements();
	}

	@Override
	void o_StatementsDo (
		final AvailObject object,
		final Continuation1NotNull<A_Phrase> continuation)
	{
		object.slot(OUTPUT_PARSE_NODE).statementsDo(continuation);
	}

	@Override
	A_Tuple o_StatementsTuple (final AvailObject object)
	{
		return object.slot(OUTPUT_PARSE_NODE).statementsTuple();
	}

	@Override
	A_Phrase o_StripMacro (final AvailObject object)
	{
		return object.slot(OUTPUT_PARSE_NODE);
	}

	@Override
	A_Type o_SuperUnionType (final AvailObject object)
	{
		return object.slot(OUTPUT_PARSE_NODE).superUnionType();
	}

	@Override
	A_Token o_Token (final AvailObject object)
	{
		return object.slot(OUTPUT_PARSE_NODE).token();
	}

	@Override
	A_Tuple o_Tokens (final AvailObject object)
	{
		return object.slot(MACRO_ORIGINAL_SEND).tokens();
	}

	@Override
	A_Phrase o_TypeExpression (final AvailObject object)
	{
		return object.slot(OUTPUT_PARSE_NODE).typeExpression();
	}

	@Override @AvailMethod
	void o_ValidateLocally (
		final AvailObject object,
		final @Nullable A_Phrase parent)
	{
		object.slot(OUTPUT_PARSE_NODE).validateLocally(parent);
	}

	@Override
	A_Phrase o_Variable (final AvailObject object)
	{
		return object.slot(OUTPUT_PARSE_NODE).variable();
	}

	@Override
	void o_WriteTo (final AvailObject object, final JSONWriter writer)
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
	MacroSubstitutionPhraseDescriptor mutable ()
	{
		return mutable;
	}

	/** The immutable {@link MacroSubstitutionPhraseDescriptor}. */
	private static final MacroSubstitutionPhraseDescriptor shared =
		new MacroSubstitutionPhraseDescriptor(Mutability.SHARED);

	@Override
	MacroSubstitutionPhraseDescriptor shared ()
	{
		return shared;
	}
}
