/*
 * ExpressionAsStatementNodeDescriptor.java
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
import com.avail.descriptor.ParseNodeTypeDescriptor.ParseNodeKind;
import com.avail.descriptor.TypeDescriptor.Types;
import com.avail.serialization.SerializerOperation;
import com.avail.utility.evaluation.Continuation1NotNull;
import com.avail.utility.evaluation.Transformer1;
import com.avail.utility.json.JSONWriter;

import javax.annotation.Nullable;
import java.util.IdentityHashMap;

import static com.avail.descriptor.ExpressionAsStatementNodeDescriptor.ObjectSlots.EXPRESSION;
import static com.avail.descriptor.ParseNodeTypeDescriptor.ParseNodeKind.EXPRESSION_AS_STATEMENT_NODE;

/**
 * My instances adapt expressions to be statements.  The two currently supported
 * examples are ⊤-value message sends and assignments.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class ExpressionAsStatementNodeDescriptor
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
		/** The expression being wrapped to be a statement. */
		EXPRESSION
	}

	@Override
	void printObjectOnAvoidingIndent (
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
	void o_ChildrenDo (
		final AvailObject object,
		final Continuation1NotNull<A_Phrase> action)
	{
		action.value(object.slot(EXPRESSION));
	}

	@Override @AvailMethod
	void o_ChildrenMap (
		final AvailObject object,
		final Transformer1<A_Phrase, A_Phrase> aBlock)
	{
		object.setSlot(
			EXPRESSION,
			aBlock.valueNotNull(object.slot(EXPRESSION)));
	}

	@Override @AvailMethod
	void o_EmitEffectOn (
		final AvailObject object,
		final AvailCodeGenerator codeGenerator)
	{
		final A_Phrase expression = object.slot(EXPRESSION);
		expression.emitEffectOn(codeGenerator);
	}

	@Override @AvailMethod
	void o_EmitValueOn (
		final AvailObject object,
		final AvailCodeGenerator codeGenerator)
	{
		final A_Phrase expression = object.slot(EXPRESSION);
		expression.emitValueOn(codeGenerator);
	}

	@Override @AvailMethod
	boolean o_EqualsParseNode (
		final AvailObject object,
		final A_Phrase aParseNode)
	{
		return !aParseNode.isMacroSubstitutionNode()
			&& object.parseNodeKind().equals(aParseNode.parseNodeKind())
			&& object.slot(EXPRESSION).equals(aParseNode.expression());
	}

	@Override @AvailMethod
	A_Phrase o_Expression (final AvailObject object)
	{
		return object.slot(EXPRESSION);
	}

	@Override @AvailMethod
	A_Type o_ExpressionType (final AvailObject object)
	{
		// Statements are always ⊤-valued.
		return Types.TOP.o();
	}

	@Override @AvailMethod
	int o_Hash (final AvailObject object)
	{
		return object.slot(EXPRESSION).hash() + 0x9088CDD8;
	}

	@Override @AvailMethod
	ParseNodeKind o_ParseNodeKind (final AvailObject object)
	{
		return EXPRESSION_AS_STATEMENT_NODE;
	}

	@Override
	SerializerOperation o_SerializerOperation (final AvailObject object)
	{
		return SerializerOperation.EXPRESSION_AS_STATEMENT_PHRASE;
	}

	@Override
	void o_StatementsDo (
		final AvailObject object,
		final Continuation1NotNull<A_Phrase> continuation)
	{
		continuation.value(object);
	}

	@Override
	A_Tuple o_Tokens (final AvailObject object)
	{
		return object.slot(EXPRESSION).tokens();
	}

	@Override @AvailMethod
	void o_ValidateLocally (
		final AvailObject object,
		final @Nullable A_Phrase parent)
	{
		// Do nothing.
	}

	@Override
	void o_WriteSummaryTo (final AvailObject object, final JSONWriter writer)
	{
		writer.startObject();
		writer.write("kind");
		writer.write("expression as statement phrase");
		writer.write("expression");
		object.slot(EXPRESSION).writeSummaryTo(writer);
		writer.endObject();
	}

	@Override
	void o_WriteTo (final AvailObject object, final JSONWriter writer)
	{
		writer.startObject();
		writer.write("kind");
		writer.write("expression as statement phrase");
		writer.write("expression");
		object.slot(EXPRESSION).writeTo(writer);
		writer.endObject();
	}

	/**
	 * Create a new {@linkplain ExpressionAsStatementNodeDescriptor
	 * expression-as-statement phrase} from the given expression phrase.
	 *
	 * @param expression
	 *        An expression (see {@link ParseNodeKind#EXPRESSION_NODE}).
	 * @return The new expression-as-statement phrase (see {@link
	 *         ParseNodeKind#EXPRESSION_AS_STATEMENT_NODE}).
	 */
	public static A_Phrase newExpressionAsStatement (final A_Phrase expression)
	{
		final AvailObject newExpressionAsStatement = mutable.create();
		newExpressionAsStatement.setSlot(EXPRESSION, expression);
		newExpressionAsStatement.makeShared();
		return newExpressionAsStatement;
	}

	/**
	 * Construct a new {@link ExpressionAsStatementNodeDescriptor}.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 */
	public ExpressionAsStatementNodeDescriptor (final Mutability mutability)
	{
		super(
			mutability,
			TypeTag.EXPRESSION_AS_STATEMENT_PHRASE_TAG,
			ObjectSlots.class,
			null);
	}

	/** The mutable {@link ExpressionAsStatementNodeDescriptor}. */
	private static final ExpressionAsStatementNodeDescriptor mutable =
		new ExpressionAsStatementNodeDescriptor(Mutability.MUTABLE);

	@Override
	ExpressionAsStatementNodeDescriptor mutable ()
	{
		return mutable;
	}

	/** The shared {@link ExpressionAsStatementNodeDescriptor}. */
	private static final ExpressionAsStatementNodeDescriptor shared =
		new ExpressionAsStatementNodeDescriptor(Mutability.SHARED);

	@Override
	ExpressionAsStatementNodeDescriptor shared ()
	{
		return shared;
	}
}
