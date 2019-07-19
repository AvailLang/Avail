/*
 * ParseNodeDescriptor.java
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
import com.avail.annotations.InnerAccess;
import com.avail.compiler.AvailCodeGenerator;
import com.avail.descriptor.PhraseTypeDescriptor.PhraseKind;
import com.avail.serialization.SerializerOperation;
import com.avail.utility.evaluation.Continuation1;
import com.avail.utility.evaluation.Continuation1NotNull;
import com.avail.utility.evaluation.Continuation2;
import com.avail.utility.evaluation.Transformer1;

import javax.annotation.Nullable;
import java.util.List;

import static com.avail.descriptor.AvailObjectRepresentation.newLike;
import static com.avail.descriptor.BottomTypeDescriptor.bottom;
import static com.avail.descriptor.NilDescriptor.nil;
import static com.avail.descriptor.PhraseTypeDescriptor.PhraseKind.PARSE_PHRASE;

/**
 * I'm used to implement the abstract notion of phrases.  All concrete phrase
 * kinds are below me in the hierarchy.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public abstract class PhraseDescriptor
extends Descriptor
{
	@Override int maximumIndent ()
	{
		return Integer.MAX_VALUE;
	}

	/**
	 * The {@code apparentSendName} of something that isn't a {@linkplain
	 * SendPhraseDescriptor send phrase} or {@linkplain
	 * MacroSubstitutionPhraseDescriptor macro substitution phrase} is always
	 * {@link NilDescriptor#nil nil}.
	 */
	@Override @AvailMethod
	A_Atom o_ApparentSendName (final AvailObject object)
	{
		return nil;
	}

	/**
	 * Visit every phrase constituting this parse tree, invoking the passed
	 * {@link Continuation1} with each.
	 *
	 * @param object
	 *        The {@linkplain A_Phrase phrase} to traverse.
	 * @param action
	 *        The {@linkplain Continuation1 action} to perform with each of
	 */
	@Override @AvailMethod
	abstract void o_ChildrenDo (
		final AvailObject object,
		final Continuation1NotNull<A_Phrase> action);

	/**
	 * Visit and transform the direct descendants of this phrase.  Map this
	 * phrase's children through the (destructive) transformation specified by
	 * aBlock, assigning them back into my slots.
	 *
	 * @param object
	 *        The phrase to transform.
	 * @param transformer
	 *        The {@linkplain Transformer1 transformation} through which
	 *        to recursively map the phrase.
	 */
	@Override @AvailMethod
	abstract void o_ChildrenMap (
		final AvailObject object,
		final Transformer1<A_Phrase, A_Phrase> transformer);

	/**
	 * If the receiver is immutable, make an equivalent mutable copy of that
	 * phrase.  Otherwise, answer the receiver itself.
	 *
	 * @param object
	 *        The {@link A_Phrase} of which to create a mutable copy.
	 * @return A mutable {@link A_Phrase} equivalent to the passed phrase,
	 *         possibly the same object.
	 */
	@Override @AvailMethod
	A_Phrase o_CopyMutablePhrase (final AvailObject object)
	{
		object.makeSubobjectsImmutable();
		if (isMutable())
		{
			return object;
		}
		return newLike(mutable(), object, 0, 0);
	}

	/**
	 * Emit the effect of this phrase.  By default that means to emit the value
	 * of the phrase, then to pop the unwanted value from the stack.
	 *
	 * @param object The phrase.
	 * @param codeGenerator Where to emit the code.
	 */
	@Override @AvailMethod
	void o_EmitEffectOn (
		final AvailObject object,
		final AvailCodeGenerator codeGenerator)
	{
		object.emitValueOn(codeGenerator);
		codeGenerator.emitPop();
	}

	/**
	 * Emit the value of this phrase.  That means emit a sequence of
	 * instructions that will cause this phrase's value to end up on the stack.
	 *
	 * @param object The phrase.
	 * @param codeGenerator Where to emit the code.
	 */
	@Override @AvailMethod
	abstract void o_EmitValueOn (
		final AvailObject object,
		final AvailCodeGenerator codeGenerator);

	/**
	 * {@linkplain A_Phrase phrases} must implement {@link
	 * PhraseDescriptor#o_EqualsPhrase(AvailObject, A_Phrase)}.
	 */
	@Override @AvailMethod
	final boolean o_Equals (
		final AvailObject object,
		final A_BasicObject another)
	{
		return another.equalsPhrase(object);
	}

	@Override @AvailMethod
	abstract boolean o_EqualsPhrase (
		final AvailObject object,
		final A_Phrase aPhrase);

	/**
	 * Return the phrase's expression type, which is the type of object that
	 * will be produced by this phrase.
	 *
	 * @return The {@linkplain TypeDescriptor type} of the {@link AvailObject}
	 *         that will be produced by evaluating this phrase.
	 */
	@Override @AvailMethod
	abstract A_Type o_ExpressionType (final AvailObject object);

	@Override @AvailMethod
	void o_FlattenStatementsInto (
		final AvailObject object,
		final List<A_Phrase> accumulatedStatements)
	{
		accumulatedStatements.add(object);
	}

	/**
	 * {@linkplain A_Phrase phrases} must implement {@link
	 * AbstractDescriptor#o_Hash(AvailObject)}.
	 */
	@Override @AvailMethod
	abstract int o_Hash (final AvailObject object);

	@Override
	boolean o_HasSuperCast (final AvailObject object)
	{
		// Terminate the recursion through the recursive list structure.  If
		// this isn't overridden in a subclass then it must be a bottom-level
		// argument to a send.
		return false;
	}

	@Override @AvailMethod
	boolean o_IsInstanceOfKind (
		final AvailObject object,
		final A_Type aType)
	{
		if (PARSE_PHRASE.mostGeneralType().isSubtypeOf(aType))
		{
			return true;
		}
		return aType.isSubtypeOf(PARSE_PHRASE.mostGeneralType())
			&& object.phraseKindIsUnder(aType.phraseKind())
			&& object.expressionType().isSubtypeOf(aType.expressionType());
	}

	@Override
	boolean o_IsMacroSubstitutionNode (final AvailObject object)
	{
		return false;
	}

	@Override @AvailMethod
	final A_Type o_Kind (final AvailObject object)
	{
		return object.phraseKind().create(object.expressionType());
	}

	@Override
	final AvailObject o_MakeImmutable (final AvailObject object)
	{
		if (isMutable())
		{
			// None of the subclasses define an immutable descriptor, so make
			// the argument shared instead.
			return object.makeShared();
		}
		return object;
	}

	/**
	 * Return the {@linkplain PhraseKind phrase kind} that this phrase's type
	 * implements.
	 *
	 * @return The {@linkplain PhraseKind kind} of phrase that the object's type
	 *         would be.
	 */
	@Override @AvailMethod
	abstract PhraseKind o_PhraseKind (final AvailObject object);

	@Override @AvailMethod
	boolean o_PhraseKindIsUnder (
		final AvailObject object,
		final PhraseKind expectedPhraseKind)
	{
		return object.phraseKind().isSubkindOf(expectedPhraseKind);
	}

	@Override
	abstract SerializerOperation o_SerializerOperation (
		final AvailObject object);

	@Override
	public boolean o_ShowValueInNameForDebugger (final AvailObject object)
	{
		return false;
	}

	@Override @AvailMethod
	abstract void o_StatementsDo (
		final AvailObject object,
		final Continuation1NotNull<A_Phrase> continuation);

	@Override @AvailMethod
	A_Phrase o_StripMacro (final AvailObject object)
	{
		return object;
	}

	@Override @AvailMethod
	A_Type o_SuperUnionType (final AvailObject object)
	{
		return bottom();
	}

	@Override
	abstract A_Tuple o_Tokens (final AvailObject object);

	/**
	 * Validate this phrase, throwing an exception if there is a problem.
	 *
	 * @param object
	 *        The {@link A_Phrase} to validate.
	 * @param parent
	 *        The {@link A_Phrase} which contains the phrase to validate as a
	 *        subphrase.
	 */
	@Override @AvailMethod
	abstract void o_ValidateLocally (
		final AvailObject object,
		final @Nullable A_Phrase parent);

	/**
	 * Visit the entire tree with the given {@linkplain Continuation2 block},
	 * children before parents.  The block takes two arguments: the phrase and
	 * its parent.
	 *
	 * @param object
	 *        The current {@link A_Phrase}.
	 * @param aBlock
	 *        What to do with each descendant.
	 * @param parentNode
	 *        This phrase's parent, or {@code null}.
	 */
	@InnerAccess static void treeDoWithParent (
		final A_Phrase object,
		final Continuation2<A_Phrase, A_Phrase> aBlock,
		final @Nullable A_Phrase parentNode)
	{
		object.childrenDo(child -> treeDoWithParent(child, aBlock, object));
		aBlock.value(object, parentNode);
	}

	/**
	 * Construct a new {@code PhraseDescriptor}.
	 *
	 * @param mutability
	 *            The {@linkplain Mutability mutability} of the new descriptor.
	 * @param typeTag
	 *            The {@link TypeTag} to embed in the new descriptor.
	 * @param objectSlotsEnumClass
	 *            The Java {@link Class} which is a subclass of {@link
	 *            ObjectSlotsEnum} and defines this object's object slots
	 *            layout, or null if there are no object slots.
	 * @param integerSlotsEnumClass
	 *            The Java {@link Class} which is a subclass of {@link
	 *            IntegerSlotsEnum} and defines this object's object slots
	 *            layout, or null if there are no integer slots.
	 */
	protected PhraseDescriptor (
		final Mutability mutability,
		final TypeTag typeTag,
		final @Nullable Class<? extends ObjectSlotsEnum> objectSlotsEnumClass,
		final @Nullable Class<? extends IntegerSlotsEnum> integerSlotsEnumClass)
	{
		super(mutability, typeTag, objectSlotsEnumClass, integerSlotsEnumClass);
	}

	@Override
	final PhraseDescriptor immutable ()
	{
		// Subclasses do not have an immutable descriptor, so use the shared one
		// instead.
		return shared();
	}

	@Override
	abstract PhraseDescriptor shared ();
}
