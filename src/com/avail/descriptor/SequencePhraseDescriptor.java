/*
 * SequenceNodeDescriptor.java
 * Copyright © 1993-2018, The Avail Foundation, LLC.
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

import com.avail.annotations.AvailMethod;
import com.avail.compiler.AvailCodeGenerator;
import com.avail.descriptor.PhraseTypeDescriptor.PhraseKind;
import com.avail.serialization.SerializerOperation;
import com.avail.utility.evaluation.Continuation1NotNull;
import com.avail.utility.evaluation.Transformer1;
import com.avail.utility.json.JSONWriter;

import javax.annotation.Nullable;
import java.util.List;

import static com.avail.descriptor.PhraseTypeDescriptor.PhraseKind.SEQUENCE_PHRASE;
import static com.avail.descriptor.SequencePhraseDescriptor.ObjectSlots.STATEMENTS;
import static com.avail.descriptor.TupleDescriptor.emptyTuple;
import static com.avail.descriptor.TypeDescriptor.Types.TOP;

/**
 * My instances represent a sequence of {@linkplain PhraseDescriptor phrases} to
 * be treated as statements, except possibly the last one.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class SequencePhraseDescriptor
extends PhraseDescriptor
{
	/**
	 * My slots of type {@link AvailObject}.
	 */
	public enum ObjectSlots
	implements ObjectSlotsEnum
	{
		/**
		 * The {@linkplain PhraseDescriptor statements} that should be
		 * considered to execute sequentially, discarding each result except
		 * possibly for that of the last statement.
		 */
		STATEMENTS
	}

	@Override @AvailMethod
	void o_ChildrenDo (
		final AvailObject object,
		final Continuation1NotNull<A_Phrase> action)
	{
		for (final AvailObject statement : object.slot(STATEMENTS))
		{
			action.value(statement);
		}
	}

	@Override @AvailMethod
	void o_ChildrenMap (
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
	void o_EmitEffectOn (
		final AvailObject object,
		final AvailCodeGenerator codeGenerator)
	{
		for (final A_Phrase statement : object.slot(STATEMENTS))
		{
			statement.emitEffectOn(codeGenerator);
		}
	}

	@Override @AvailMethod
	void o_EmitValueOn (
		final AvailObject object,
		final AvailCodeGenerator codeGenerator)
	{
		final A_Tuple statements = object.slot(STATEMENTS);
		final int statementsCount = statements.tupleSize();
		if (statements.tupleSize() > 0)
		{
			for (int i = 1; i < statementsCount; i++)
			{
				statements.tupleAt(i).emitEffectOn(codeGenerator);
			}
			statements.tupleAt(statementsCount).emitValueOn(codeGenerator);
		}
	}

	@Override @AvailMethod
	boolean o_EqualsPhrase (
		final AvailObject object,
		final A_Phrase aPhrase)
	{
		return !aPhrase.isMacroSubstitutionNode()
			&& object.phraseKind().equals(aPhrase.phraseKind())
			&& object.slot(STATEMENTS).equals(aPhrase.statements());
	}

	@Override @AvailMethod
	A_Type o_ExpressionType (final AvailObject object)
	{
		final A_Tuple statements = object.slot(STATEMENTS);
		return statements.tupleSize() > 0
			? statements.tupleAt(statements.tupleSize()).expressionType()
			: TOP.o();
	}

	@Override @AvailMethod
	void o_FlattenStatementsInto (
		final AvailObject object,
		final List<A_Phrase> accumulatedStatements)
	{
		for (final A_Phrase statement : object.slot(STATEMENTS))
		{
			statement.flattenStatementsInto(accumulatedStatements);
		}
	}

	@Override @AvailMethod
	int o_Hash (final AvailObject object)
	{
		return object.slot(STATEMENTS).hash() + 0xE38140CA;
	}

	@Override
	PhraseKind o_PhraseKind (final AvailObject object)
	{
		return SEQUENCE_PHRASE;
	}

	@Override @AvailMethod
	A_Tuple o_Statements (final AvailObject object)
	{
		return object.slot(STATEMENTS);
	}

	@Override
	void o_StatementsDo (
		final AvailObject object,
		final Continuation1NotNull<A_Phrase> continuation)
	{
		for (final AvailObject statement : object.slot(STATEMENTS))
		{
			continuation.value(statement);
		}
	}

	@Override
	SerializerOperation o_SerializerOperation (final AvailObject object)
	{
		return SerializerOperation.SEQUENCE_PHRASE;
	}

	@Override
	A_Tuple o_Tokens (final AvailObject object)
	{
		return emptyTuple();
	}

	@Override @AvailMethod
	void o_ValidateLocally (
		final AvailObject object,
		final @Nullable A_Phrase parent)
	{
		// Do nothing.
	}

	@Override
	void o_WriteTo (final AvailObject object, final JSONWriter writer)
	{
		writer.startObject();
		writer.write("kind");
		writer.write("sequence phrase");
		writer.write("statements");
		object.slot(STATEMENTS).writeTo(writer);
		writer.endObject();
	}

	@Override
	void o_WriteSummaryTo (final AvailObject object, final JSONWriter writer)
	{
		writer.startObject();
		writer.write("kind");
		writer.write("sequence phrase");
		writer.write("statements");
		object.slot(STATEMENTS).writeSummaryTo(writer);
		writer.endObject();
	}

	/**
	 * Create a new {@linkplain SequencePhraseDescriptor sequence phrase} from the
	 * given {@linkplain TupleDescriptor tuple} of {@linkplain
	 * PhraseDescriptor statements}.
	 *
	 * @param statements
	 *        The expressions to assemble into a {@linkplain
	 *        SequencePhraseDescriptor sequence phrase}.
	 * @return The resulting sequence phrase.
	 */
	public static A_Phrase newSequence (final A_Tuple statements)
	{
		final AvailObject instance = mutable.create();
		instance.setSlot(STATEMENTS, statements);
		instance.makeShared();
		return instance;
	}

	/**
	 * Construct a new {@link SequencePhraseDescriptor}.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 */
	private SequencePhraseDescriptor (final Mutability mutability)
	{
		super(mutability, TypeTag.SEQUENCE_PHRASE_TAG, ObjectSlots.class, null);
	}

	/** The mutable {@link SequencePhraseDescriptor}. */
	private static final SequencePhraseDescriptor mutable =
		new SequencePhraseDescriptor(Mutability.MUTABLE);

	@Override
	SequencePhraseDescriptor mutable ()
	{
		return mutable;
	}

	/** The shared {@link SequencePhraseDescriptor}. */
	private static final SequencePhraseDescriptor shared =
		new SequencePhraseDescriptor(Mutability.SHARED);

	@Override
	SequencePhraseDescriptor shared ()
	{
		return shared;
	}
}
