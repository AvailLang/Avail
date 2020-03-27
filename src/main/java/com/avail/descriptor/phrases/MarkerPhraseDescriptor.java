/*
 * MarkerPhraseDescriptor.java
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
import com.avail.descriptor.representation.Mutability;
import com.avail.descriptor.representation.ObjectSlotsEnum;
import com.avail.descriptor.tuples.A_Tuple;
import com.avail.descriptor.types.A_Type;
import com.avail.descriptor.types.PhraseTypeDescriptor.PhraseKind;
import com.avail.descriptor.types.TypeTag;
import com.avail.serialization.SerializerOperation;
import com.avail.utility.evaluation.Continuation1NotNull;
import com.avail.utility.evaluation.Transformer1;

import javax.annotation.Nullable;
import java.util.IdentityHashMap;

import static com.avail.descriptor.numbers.IntegerDescriptor.fromInt;
import static com.avail.descriptor.phrases.MarkerPhraseDescriptor.ObjectSlots.MARKER_VALUE;
import static com.avail.descriptor.tuples.TupleDescriptor.emptyTuple;
import static com.avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.MARKER_PHRASE;
import static com.avail.descriptor.types.TypeDescriptor.Types.TOP;

/**
 * My instances represent a parsing marker that can be pushed onto the parse
 * stack.  It should never occur as part of a composite {@linkplain
 * PhraseDescriptor phrase}, and is not capable of emitting code.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class MarkerPhraseDescriptor
extends PhraseDescriptor
{
	/**
	 * My slots of type {@link AvailObject}.
	 */
	public enum ObjectSlots
	implements ObjectSlotsEnum
	{
		/**
		 * The {@linkplain MarkerPhraseDescriptor marker} being wrapped in a form
		 * suitable for the parse stack.
		 */
		MARKER_VALUE
	}

	/**
	 * An {@link Enum} whose ordinals can be used as marker values in
	 * {@linkplain MarkerPhraseDescriptor marker phrases}.
	 */
	public enum MarkerTypes {
		/**
		 * A marker standing for a duplicate of some value that was on the
		 * stack.
		 */
		DUP,

		/**
		 * A marker indicating the value below it has been permuted, and should
		 * be checked by a subsequent call operation;
		 */
		PERMUTE;

		/**
		 * A pre-built marker for this enumeration value.
		 */
		public final A_Phrase marker = newMarkerNode(fromInt(ordinal()));
	}

	@Override
	public void printObjectOnAvoidingIndent (
		final AvailObject object,
		final StringBuilder builder,
		final IdentityHashMap<A_BasicObject, Void> recursionMap,
		final int indent)
	{
		builder.append("Marker(");
		builder.append(object.markerValue());
		builder.append(")");
	}

	@Override @AvailMethod
	protected void o_ChildrenDo (
		final AvailObject object,
		final Continuation1NotNull<A_Phrase> action)
	{
		throw unsupportedOperationException();
	}

	@Override @AvailMethod
	protected void o_ChildrenMap (
		final AvailObject object,
		final Transformer1<A_Phrase, A_Phrase> transformer)
	{
		throw unsupportedOperationException();
	}

	@Override @AvailMethod
	protected void o_EmitValueOn (
		final AvailObject object,
		final AvailCodeGenerator codeGenerator)
	{
		assert false : "A marker phrase can not generate code.";
	}

	@Override @AvailMethod
	protected boolean o_EqualsPhrase (
		final AvailObject object,
		final A_Phrase aPhrase)
	{
		return !aPhrase.isMacroSubstitutionNode()
			&& object.phraseKind().equals(aPhrase.phraseKind())
			&& object.markerValue().equals(aPhrase.markerValue());
	}

	@Override @AvailMethod
	protected A_Type o_ExpressionType (final AvailObject object)
	{
		// This shouldn't make a difference.
		return TOP.o();
	}

	@Override @AvailMethod
	public int o_Hash (final AvailObject object)
	{
		return object.markerValue().hash() ^ 0xCBCACACC;
	}

	@Override @AvailMethod
	protected AvailObject o_MarkerValue (final AvailObject object)
	{
		return object.slot(MARKER_VALUE);
	}

	@Override
	protected PhraseKind o_PhraseKind (final AvailObject object)
	{
		return MARKER_PHRASE;
	}

	@Override
	protected SerializerOperation o_SerializerOperation (
		final AvailObject object)
	{
		// There's currently no reason to serialize a marker phrase.  This may
		// change at some point.
		throw unsupportedOperationException();
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
		return emptyTuple();
	}

	@Override @AvailMethod
	protected void o_ValidateLocally (
		final AvailObject object,
		final @Nullable A_Phrase parent)
	{
		throw unsupportedOperationException();
	}

	/**
	 * Create a marker phrase wrapping the given {@link A_BasicObject}.
	 *
	 * @param markerValue The value to wrap.
	 * @return A new immutable marker phrase.
	 */
	public static AvailObject newMarkerNode (final A_BasicObject markerValue)
	{
		final AvailObject instance = mutable.create();
		instance.setSlot(MARKER_VALUE, markerValue);
		instance.makeShared();
		return instance;
	}

	/**
	 * Construct a new {@code MarkerPhraseDescriptor}.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 */
	private MarkerPhraseDescriptor (final Mutability mutability)
	{
		super(mutability, TypeTag.MARKER_PHRASE_TAG, ObjectSlots.class, null);
	}

	/** The mutable {@link MarkerPhraseDescriptor}. */
	private static final MarkerPhraseDescriptor mutable =
		new MarkerPhraseDescriptor(Mutability.MUTABLE);

	@Override
	public MarkerPhraseDescriptor mutable ()
	{
		return mutable;
	}

	/** The shared {@link MarkerPhraseDescriptor}. */
	private static final MarkerPhraseDescriptor shared =
		new MarkerPhraseDescriptor(Mutability.SHARED);

	@Override
	public MarkerPhraseDescriptor shared ()
	{
		return shared;
	}
}
