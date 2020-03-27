/*
 * ExpressionAsStatementPhraseDescriptor.java
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
import com.avail.descriptor.A_BasicObject;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.JavaCompatibility.ObjectSlotsEnumJava;
import com.avail.descriptor.representation.Mutability;
import com.avail.descriptor.tuples.A_Tuple;
import com.avail.descriptor.types.A_Type;
import com.avail.descriptor.types.PhraseTypeDescriptor.PhraseKind;
import com.avail.descriptor.types.TypeDescriptor.Types;
import com.avail.descriptor.types.TypeTag;
import com.avail.serialization.SerializerOperation;
import com.avail.utility.evaluation.Continuation1NotNull;
import com.avail.utility.evaluation.Transformer1;
import com.avail.utility.json.JSONWriter;

import javax.annotation.Nullable;
import java.util.IdentityHashMap;

import static com.avail.descriptor.phrases.ExpressionAsStatementPhraseDescriptor.ObjectSlots.EXPRESSION;
import static com.avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.EXPRESSION_AS_STATEMENT_PHRASE;

/**
 * My instances adapt expressions to be statements.  The two currently supported
 * examples are ⊤-value message sends and assignments.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class ExpressionAsStatementPhraseDescriptor
extends PhraseDescriptor
{
	/**
	 * My slots of type {@link AvailObject}.
	 *
	 * @author Mark van Gulik &lt;mark@availlang.org&gt;
	 */
	public enum ObjectSlots implements ObjectSlotsEnumJava
	{
		/** The expression being wrapped to be a statement. */
		EXPRESSION
	}

	@Override
	public void printObjectOnAvoidingIndent (
		final AvailObject object,
		final StringBuilder builder,
		final IdentityHashMap<A_BasicObject, Void> recursionMap,
		final int indent)
	{
		object.slot(EXPRESSION).printOnAvoidingIndent(
			builder,
			recursionMap,
			indent);
	}

	@Override @AvailMethod
	protected void o_ChildrenDo (
		final AvailObject object,
		final Continuation1NotNull<A_Phrase> action)
	{
		action.value(object.slot(EXPRESSION));
	}

	@Override @AvailMethod
	protected void o_ChildrenMap (
		final AvailObject object,
		final Transformer1<A_Phrase, A_Phrase> transformer)
	{
		object.setSlot(
			EXPRESSION,
			transformer.valueNotNull(object.slot(EXPRESSION)));
	}

	@Override @AvailMethod
	protected void o_EmitEffectOn (
		final AvailObject object,
		final AvailCodeGenerator codeGenerator)
	{
		final A_Phrase expression = object.slot(EXPRESSION);
		expression.emitEffectOn(codeGenerator);
	}

	@Override @AvailMethod
	protected void o_EmitValueOn (
		final AvailObject object,
		final AvailCodeGenerator codeGenerator)
	{
		final A_Phrase expression = object.slot(EXPRESSION);
		expression.emitValueOn(codeGenerator);
	}

	@Override @AvailMethod
	protected boolean o_EqualsPhrase (
		final AvailObject object,
		final A_Phrase aPhrase)
	{
		return !aPhrase.isMacroSubstitutionNode()
			&& object.phraseKind().equals(aPhrase.phraseKind())
			&& object.slot(EXPRESSION).equals(aPhrase.expression());
	}

	@Override @AvailMethod
	protected A_Phrase o_Expression (final AvailObject object)
	{
		return object.slot(EXPRESSION);
	}

	@Override @AvailMethod
	protected A_Type o_ExpressionType (final AvailObject object)
	{
		// Statements are always ⊤-valued.
		return Types.TOP.o();
	}

	@Override @AvailMethod
	public int o_Hash (final AvailObject object)
	{
		return object.slot(EXPRESSION).hash() + 0x9088CDD8;
	}

	@Override @AvailMethod
	protected PhraseKind o_PhraseKind (final AvailObject object)
	{
		return EXPRESSION_AS_STATEMENT_PHRASE;
	}

	@Override
	protected SerializerOperation o_SerializerOperation (
		final AvailObject object)
	{
		return SerializerOperation.EXPRESSION_AS_STATEMENT_PHRASE;
	}

	@Override
	protected void o_StatementsDo (
		final AvailObject object,
		final Continuation1NotNull<A_Phrase> continuation)
	{
		continuation.value(object);
	}

	@Override
	protected A_Tuple o_Tokens (final AvailObject object)
	{
		return object.slot(EXPRESSION).tokens();
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
		writer.write("expression as statement phrase");
		writer.write("expression");
		object.slot(EXPRESSION).writeSummaryTo(writer);
		writer.endObject();
	}

	@Override
	protected void o_WriteTo (final AvailObject object, final JSONWriter writer)
	{
		writer.startObject();
		writer.write("kind");
		writer.write("expression as statement phrase");
		writer.write("expression");
		object.slot(EXPRESSION).writeTo(writer);
		writer.endObject();
	}

	/**
	 * Create a new expression-as-statement phrase from the given expression
	 * phrase.
	 *
	 * @param expression
	 *        An expression (see {@link PhraseKind#EXPRESSION_PHRASE}).
	 * @return The new expression-as-statement phrase (see {@link
	 *         PhraseKind#EXPRESSION_AS_STATEMENT_PHRASE}).
	 */
	public static A_Phrase newExpressionAsStatement (final A_Phrase expression)
	{
		final AvailObject newExpressionAsStatement = mutable.create();
		newExpressionAsStatement.setSlot(EXPRESSION, expression);
		newExpressionAsStatement.makeShared();
		return newExpressionAsStatement;
	}

	/**
	 * Construct a new {@code ExpressionAsStatementPhraseDescriptor}.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 */
	public ExpressionAsStatementPhraseDescriptor (final Mutability mutability)
	{
		super(
			mutability,
			TypeTag.EXPRESSION_AS_STATEMENT_PHRASE_TAG,
			ObjectSlots.class,
			null);
	}

	/** The mutable {@link ExpressionAsStatementPhraseDescriptor}. */
	private static final ExpressionAsStatementPhraseDescriptor mutable =
		new ExpressionAsStatementPhraseDescriptor(Mutability.MUTABLE);

	@Override
	public ExpressionAsStatementPhraseDescriptor mutable ()
	{
		return mutable;
	}

	/** The shared {@link ExpressionAsStatementPhraseDescriptor}. */
	private static final ExpressionAsStatementPhraseDescriptor shared =
		new ExpressionAsStatementPhraseDescriptor(Mutability.SHARED);

	@Override
	public ExpressionAsStatementPhraseDescriptor shared ()
	{
		return shared;
	}
}
