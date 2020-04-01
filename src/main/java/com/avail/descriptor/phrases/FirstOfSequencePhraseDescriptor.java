/*
 * FirstOfSequencePhraseDescriptor.java
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
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.JavaCompatibility.ObjectSlotsEnumJava;
import com.avail.descriptor.NilDescriptor;
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
import java.util.ArrayList;
import java.util.List;

import static com.avail.descriptor.phrases.FirstOfSequencePhraseDescriptor.ObjectSlots.STATEMENTS;
import static com.avail.descriptor.tuples.ObjectTupleDescriptor.tupleFromList;
import static com.avail.descriptor.tuples.TupleDescriptor.emptyTuple;
import static com.avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.FIRST_OF_SEQUENCE_PHRASE;

/**
 * My instances represent a sequence of {@linkplain PhraseDescriptor phrases} to
 * be treated as statements, except possibly the <em>first</em> one. All phrases
 * are executed, and all results except the one from the first phrase are
 * discarded.  The {@linkplain FirstOfSequencePhraseDescriptor
 * first-of-sequence} phrase's effective value is the value produced by the
 * first phrase.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class FirstOfSequencePhraseDescriptor
extends PhraseDescriptor
{

	/**
	 * My slots of type {@link AvailObject}.
	 */
	public enum ObjectSlots implements ObjectSlotsEnumJava
	{
		/**
		 * The {@link A_Tuple} of {@linkplain PhraseDescriptor expressions}
		 * that should be considered to execute sequentially, discarding each
		 * result except for that of the <em>first</em> expression. There must
		 * be at least one expression.  All expressions but the first must be
		 * typed as ⊤.  The first one is also allowed to be typed as ⊤, but even
		 * if so, if the actual value produced is more specific (i.e., not
		 * {@linkplain NilDescriptor#nil nil}, then that is what the
		 * {@linkplain FirstOfSequencePhraseDescriptor first-of-sequence}
		 * phrase's effective value will be.
		 */
		STATEMENTS;
	}

	@Override @AvailMethod
	protected void o_ChildrenDo (
		final AvailObject object,
		final Continuation1NotNull<A_Phrase> action)
	{
		for (final AvailObject statement : object.slot(STATEMENTS))
		{
			action.value(statement);
		}
	}

	@Override @AvailMethod
	protected void o_ChildrenMap (
		final AvailObject object,
		final Transformer1<A_Phrase, A_Phrase> transformer)
	{
		A_Tuple statements = object.slot(STATEMENTS);
		for (int i = 1; i <= statements.tupleSize(); i++)
		{
			statements = statements.tupleAtPuttingCanDestroy(
				i,
				transformer.valueNotNull(statements.tupleAt(i)),
				true);
		}
		object.setSlot(STATEMENTS, statements);
	}

	@Override @AvailMethod
	protected void o_EmitEffectOn (
		final AvailObject object,
		final AvailCodeGenerator codeGenerator)
	{
		// It's unclear under what circumstances this construct would be asked
		// to emit itself only for effect.  Regardless, keep the first
		// expression's value on the stack until the other statements have all
		// executed... then pop it.  Even though there will be no significant
		// runtime difference, it will makes disassembly more faithful.
		final A_Tuple statements = object.slot(STATEMENTS);
		final int statementsCount = statements.tupleSize();
		assert statements.tupleSize() > 0;
		// Leave the first statement's value on the stack while evaluating the
		// subsequent statements.
		statements.tupleAt(1).emitValueOn(codeGenerator);
		for (int i = 2; i <= statementsCount; i++)
		{
			statements.tupleAt(i).emitEffectOn(codeGenerator);
		}
		// Finally, pop the first expression's value.
		codeGenerator.emitPop();
	}

	@Override @AvailMethod
	protected void o_EmitValueOn (
		final AvailObject object,
		final AvailCodeGenerator codeGenerator)
	{
		final A_Tuple statements = object.slot(STATEMENTS);
		final int statementsCount = statements.tupleSize();
		assert statements.tupleSize() > 0;
		// Leave the first statement's value on the stack while evaluating the
		// subsequent statements.
		statements.tupleAt(1).emitValueOn(codeGenerator);
		for (int i = 2; i <= statementsCount; i++)
		{
			statements.tupleAt(i).emitEffectOn(codeGenerator);
		}
	}

	@Override @AvailMethod
	protected boolean o_EqualsPhrase (
		final AvailObject object,
		final A_Phrase aPhrase)
	{
		return !aPhrase.isMacroSubstitutionNode()
			&& object.phraseKind().equals(aPhrase.phraseKind())
			&& object.slot(STATEMENTS).equals(aPhrase.statements());
	}

	@Override @AvailMethod
	protected A_Type o_ExpressionType (final AvailObject object)
	{
		final A_Tuple statements = object.slot(STATEMENTS);
		assert statements.tupleSize() > 0;
		return statements.tupleAt(1).expressionType();
	}

	@Override @AvailMethod
	protected void o_FlattenStatementsInto (
		final AvailObject object,
		final List<A_Phrase> accumulatedStatements)
	{
		final A_Tuple statements = object.slot(STATEMENTS);
		// Process the first expression, then grab the final value-producing
		// expression back *off* the list.
		statements.tupleAt(1).flattenStatementsInto(accumulatedStatements);
		final A_Phrase valueProducer = accumulatedStatements.remove(
			accumulatedStatements.size() - 1);
		final List<A_Phrase> myFlatStatements = new ArrayList<>();
		myFlatStatements.add(valueProducer);
		for (int i = 2, limit = statements.tupleSize(); i <= limit; i++)
		{
			statements.tupleAt(i).flattenStatementsInto(myFlatStatements);
		}
		if (myFlatStatements.size() == 1)
		{
			accumulatedStatements.add(myFlatStatements.get(0));
		}
		else
		{
			final A_Phrase newFirstOfSequence =
				newFirstOfSequenceNode(tupleFromList(myFlatStatements));
			accumulatedStatements.add(newFirstOfSequence);
		}
	}

	@Override @AvailMethod
	public int o_Hash (final AvailObject object)
	{
		return object.slot(STATEMENTS).hash() ^ 0x70EDD231;
	}

	@Override
	protected PhraseKind o_PhraseKind (final AvailObject object)
	{
		return FIRST_OF_SEQUENCE_PHRASE;
	}

	@Override
	protected SerializerOperation o_SerializerOperation (
		final AvailObject object)
	{
		return SerializerOperation.FIRST_OF_SEQUENCE_PHRASE;
	}

	@Override @AvailMethod
	protected A_Tuple o_Statements (final AvailObject object)
	{
		return object.slot(STATEMENTS);
	}

	@Override
	protected void o_StatementsDo (
		final AvailObject object,
		final Continuation1NotNull<A_Phrase> continuation)
	{
		for (final A_Phrase statement : object.slot(STATEMENTS))
		{
			statement.statementsDo(continuation);
		}
	}

	@Override
	protected A_Tuple o_Tokens (final AvailObject object)
	{
		return emptyTuple();
	}

	@Override @AvailMethod
	protected void o_ValidateLocally (
		final AvailObject object,
		final @Nullable A_Phrase parent)
	{
		// Do nothing.
	}

	@Override
	protected void o_WriteTo (final AvailObject object, final JSONWriter writer)
	{
		writer.startObject();
		writer.write("kind");
		writer.write("first-of-sequence phrase");
		writer.write("statements");
		object.slot(STATEMENTS).writeTo(writer);
		writer.endObject();
	}

	@Override
	protected void o_WriteSummaryTo (final AvailObject object, final JSONWriter writer)
	{
		writer.startObject();
		writer.write("kind");
		writer.write("first-of-sequence phrase");
		writer.write("statements");
		object.slot(STATEMENTS).writeSummaryTo(writer);
		writer.endObject();
	}

	/**
	 * Create a new first-of-sequence phrase from the given {@link A_Tuple} of
	 * statements.
	 *
	 * @param statements
	 *        The expressions to assemble into a first-of-sequence phrase, the
	 *        <em>first</em> of which provides the value.
	 * @return The resulting first-of-sequence phrase.
	 */
	public static A_Phrase newFirstOfSequenceNode (final A_Tuple statements)
	{
		final AvailObject instance = mutable.create();
		assert statements.tupleSize() > 1;
		instance.setSlot(STATEMENTS, statements);
		instance.makeShared();
		return instance;
	}

	/**
	 * Construct a new {@code FirstOfSequencePhraseDescriptor}.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 */
	private FirstOfSequencePhraseDescriptor (final Mutability mutability)
	{
		super(
			mutability,
			TypeTag.FIRST_OF_SEQUENCE_PHRASE_TAG,
			ObjectSlots.class,
			null);
	}

	/** The mutable {@link FirstOfSequencePhraseDescriptor}. */
	private static final FirstOfSequencePhraseDescriptor mutable =
		new FirstOfSequencePhraseDescriptor(Mutability.MUTABLE);

	@Override
	public FirstOfSequencePhraseDescriptor mutable ()
	{
		return mutable;
	}

	/** The shared {@link FirstOfSequencePhraseDescriptor}. */
	private static final FirstOfSequencePhraseDescriptor shared =
		new FirstOfSequencePhraseDescriptor(Mutability.SHARED);

	@Override
	public FirstOfSequencePhraseDescriptor shared ()
	{
		return shared;
	}
}
