/*
 * PhraseTypeDescriptor.java
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

package com.avail.descriptor.types;

import com.avail.annotations.AvailMethod;
import com.avail.annotations.HideFieldInDebugger;
import com.avail.descriptor.JavaCompatibility.IntegerEnumSlotDescriptionEnumJava;
import com.avail.descriptor.representation.AvailObject;
import com.avail.descriptor.JavaCompatibility.IntegerSlotsEnumJava;
import com.avail.descriptor.JavaCompatibility.ObjectSlotsEnumJava;
import com.avail.descriptor.phrases.*;
import com.avail.descriptor.representation.*;
import com.avail.serialization.SerializerOperation;
import com.avail.utility.json.JSONWriter;

import javax.annotation.Nullable;
import java.util.IdentityHashMap;
import java.util.List;

import static com.avail.descriptor.representation.AvailObject.multiplier;
import static com.avail.descriptor.types.BottomTypeDescriptor.bottom;
import static com.avail.descriptor.types.FunctionTypeDescriptor.mostGeneralFunctionType;
import static com.avail.descriptor.types.ListPhraseTypeDescriptor.createListNodeType;
import static com.avail.descriptor.types.ListPhraseTypeDescriptor.createListNodeTypeNoCheck;
import static com.avail.descriptor.types.LiteralTokenTypeDescriptor.literalTokenType;
import static com.avail.descriptor.types.PhraseTypeDescriptor.IntegerSlots.HASH_AND_MORE;
import static com.avail.descriptor.types.PhraseTypeDescriptor.IntegerSlots.HASH_OR_ZERO;
import static com.avail.descriptor.types.PhraseTypeDescriptor.ObjectSlots.EXPRESSION_TYPE;
import static com.avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.*;
import static com.avail.descriptor.types.TupleTypeDescriptor.*;
import static com.avail.descriptor.types.TypeDescriptor.Types.ANY;
import static com.avail.descriptor.types.TypeDescriptor.Types.TOP;
import static com.avail.descriptor.types.VariableTypeDescriptor.mostGeneralVariableType;
import static com.avail.utility.Nulls.stripNull;

/**
 * Define the structure and behavior of phrase types.  The phrase types
 * are all parameterized by expression type, but they also have a relationship
 * to each other based on a fiat hierarchy.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public class PhraseTypeDescriptor
extends TypeDescriptor
{
	/**
	 * My slots of type {@linkplain Integer int}.
	 */
	public enum IntegerSlots implements IntegerSlotsEnumJava
	{
		/**
		 * The low 32 bits are used for caching the hash.
		 */
		@HideFieldInDebugger
		HASH_AND_MORE;

		/**
		 * The hash, or zero ({@code 0}) if the hash has not yet been computed.
		 */
		public static final BitField HASH_OR_ZERO =
			new BitField(HASH_AND_MORE, 0, 32);
	}

	/**
	 * My slots of type {@link AvailObject}.
	 */
	public enum ObjectSlots implements ObjectSlotsEnumJava
	{
		/**
		 * The type of value that this expression would produce.
		 */
		EXPRESSION_TYPE;
	}

	/**
	 * My hierarchy of kinds of phrases.
	 */
	public enum PhraseKind
	implements IntegerEnumSlotDescriptionEnumJava
	{
		/** The root phrase kind. */
		PARSE_PHRASE("phrase type", null, TypeTag.PHRASE_TAG),

		/** The kind of a parse marker. */
		MARKER_PHRASE(
			"marker phrase type", PARSE_PHRASE, TypeTag.MARKER_PHRASE_TAG),

		/** The abstract parent kind of all expression phrases. */
		EXPRESSION_PHRASE(
			"expression phrase type",
			PARSE_PHRASE,
			TypeTag.EXPRESSION_PHRASE_TAG),

		/**
		 * The kind of an {@linkplain AssignmentPhraseDescriptor assignment
		 * phrase}.
		 */
		ASSIGNMENT_PHRASE(
			"assignment phrase type",
			EXPRESSION_PHRASE,
			TypeTag.ASSIGNMENT_PHRASE_TAG),

		/** The kind of a {@linkplain BlockPhraseDescriptor block phrase}. */
		BLOCK_PHRASE(
			"block phrase type",
			EXPRESSION_PHRASE,
			TypeTag.BLOCK_PHRASE_TAG)
		{
			@Override
			public A_Type mostGeneralYieldType ()
			{
				return mostGeneralFunctionType();
			}
		},

		/**
		 * The kind of a {@linkplain LiteralPhraseDescriptor literal phrase}.
		 */
		LITERAL_PHRASE(
			"literal phrase type",
			EXPRESSION_PHRASE,
			TypeTag.LITERAL_PHRASE_TAG)
		{
			@Override
			public A_Type mostGeneralYieldType ()
			{
				return ANY.o();
			}
		},

		/**
		 * The kind of a {@linkplain ReferencePhraseDescriptor reference
		 * phrase}.
		 */
		REFERENCE_PHRASE(
			"variable reference phrase type",
			EXPRESSION_PHRASE,
			TypeTag.REFERENCE_PHRASE_TAG)
		{
			@Override
			public A_Type mostGeneralYieldType ()
			{
				return mostGeneralVariableType();
			}
		},

		/**
		 * The kind of a {@linkplain SuperCastPhraseDescriptor super cast
		 * phrase}.
		 */
		SUPER_CAST_PHRASE(
			"super cast phrase",
			EXPRESSION_PHRASE,
			TypeTag.SUPER_CAST_PHRASE_TAG)
		{
			@Override
			public A_Type mostGeneralYieldType ()
			{
				return ANY.o();
			}
		},

		/** The kind of a {@linkplain SendPhraseDescriptor send phrase}. */
		SEND_PHRASE(
			"send phrase type", EXPRESSION_PHRASE, TypeTag.SEND_PHRASE_TAG),

		/** The kind of a {@linkplain ListPhraseDescriptor list phrase}. */
		LIST_PHRASE(
			"list phrase type", EXPRESSION_PHRASE, TypeTag.LIST_PHRASE_TAG)
		{
			/** Create a descriptor for this kind. */
			@Override
			PhraseTypeDescriptor createDescriptor (
				final Mutability mutability)
			{
				return new ListPhraseTypeDescriptor(mutability, this);
			}

			@Override
			public A_Type mostGeneralYieldType ()
			{
				return mostGeneralTupleType();
			}

			@Override
			public final A_Type createNoCheck (
				final A_Type yieldType)
			{
				final PhraseKind listNodeKind = this;
				final A_Type subexpressionsTupleType =
					tupleTypeFromTupleOfTypes(yieldType, PARSE_PHRASE::create);
				return createListNodeTypeNoCheck(
					listNodeKind, yieldType, subexpressionsTupleType);
			}
		},

		/**
		 * The kind of a {@linkplain PermutedListPhraseDescriptor permuted list
		 * phrase}.
		 */
		PERMUTED_LIST_PHRASE(
			"permuted list phrase type",
			LIST_PHRASE,
			TypeTag.PERMUTED_LIST_PHRASE_TAG)
		{
			/** Create a descriptor for this kind. */
			@Override
			PhraseTypeDescriptor createDescriptor (
				final Mutability mutability)
			{
				return new ListPhraseTypeDescriptor(mutability, this);
			}

			@Override
			public A_Type mostGeneralYieldType ()
			{
				return mostGeneralTupleType();
			}

			@Override
			public final A_Type createNoCheck (
				final A_Type yieldType)
			{
				final PhraseKind listNodeKind = this;
				final A_Type subexpressionsTupleType =
					tupleTypeFromTupleOfTypes(yieldType, PARSE_PHRASE::create);
				return createListNodeTypeNoCheck(
					listNodeKind, yieldType, subexpressionsTupleType);
			}
		},

		/**
		 * The kind of a {@linkplain VariableUsePhraseDescriptor variable use
		 * phrase}.
		 */
		VARIABLE_USE_PHRASE(
			"variable use phrase type", EXPRESSION_PHRASE, TypeTag.VARIABLE_TAG)
		{
			@Override
			public A_Type mostGeneralYieldType ()
			{
				return ANY.o();
			}
		},

		/** A phrase that does not produce a result. */
		STATEMENT_PHRASE(
			"statement phrase type",
			PARSE_PHRASE,
			TypeTag.STATEMENT_PHRASE_TAG),

		/**
		 * The kind of a {@linkplain SequencePhraseDescriptor sequence phrase}.
		 */
		SEQUENCE_PHRASE(
			"sequence phrase type",
			STATEMENT_PHRASE,
			TypeTag.SEQUENCE_PHRASE_TAG),

		/**
		 * The kind of a {@linkplain FirstOfSequencePhraseDescriptor
		 * first-of-sequence phrase}.
		 */
		FIRST_OF_SEQUENCE_PHRASE(
			"first-of-sequence phrase type",
			STATEMENT_PHRASE,
			TypeTag.FIRST_OF_SEQUENCE_PHRASE_TAG),

		/**
		 * The kind of a {@linkplain DeclarationPhraseDescriptor declaration
		 * phrase}.
		 */
		DECLARATION_PHRASE(
			"declaration phrase type",
			STATEMENT_PHRASE,
			TypeTag.DECLARATION_PHRASE_TAG),

		/** The kind of an argument declaration phrase. */
		ARGUMENT_PHRASE(
			"argument phrase type",
			DECLARATION_PHRASE,
			TypeTag.ARGUMENT_PHRASE_TAG),

		/** The kind of a label declaration phrase. */
		LABEL_PHRASE(
			"label phrase type",
			DECLARATION_PHRASE,
			TypeTag.LABEL_PHRASE_TAG),

		/** The kind of a local variable declaration phrase. */
		LOCAL_VARIABLE_PHRASE(
			"local variable phrase type",
			DECLARATION_PHRASE,
			TypeTag.LOCAL_VARIABLE_PHRASE_TAG),

		/** The kind of a local constant declaration phrase. */
		LOCAL_CONSTANT_PHRASE(
			"local constant phrase type",
			DECLARATION_PHRASE,
			TypeTag.LOCAL_CONSTANT_PHRASE_TAG),

		/** The kind of a module variable declaration phrase. */
		MODULE_VARIABLE_PHRASE(
			"module variable phrase type",
			DECLARATION_PHRASE,
			TypeTag.MODULE_VARIABLE_PHRASE_TAG),

		/** The kind of a module constant declaration phrase. */
		MODULE_CONSTANT_PHRASE(
			"module constant phrase type",
			DECLARATION_PHRASE,
			TypeTag.MODULE_CONSTANT_PHRASE_TAG),

		/** The kind of a primitive failure reason variable declaration. */
		PRIMITIVE_FAILURE_REASON_PHRASE(
			"primitive failure reason phrase type",
			DECLARATION_PHRASE,
			TypeTag.PRIMITIVE_FAILURE_REASON_PHRASE_TAG),

		/**
		 * A statement phrase built from an expression.  At the moment, only
		 * assignments and sends can be expression-as-statement phrases.
		 */
		EXPRESSION_AS_STATEMENT_PHRASE(
			"expression as statement phrase type",
			STATEMENT_PHRASE,
			TypeTag.EXPRESSION_AS_STATEMENT_PHRASE_TAG),

		/** The result of a macro substitution. */
		MACRO_SUBSTITUTION_PHRASE(
			"macro substitution phrase type",
			PARSE_PHRASE,
			TypeTag.MACRO_SUBSTITUTION_PHRASE_TAG);

		/**
		 * The kind of phrase that this kind is a child of.
		 */
		final @Nullable
		PhraseKind parentKind;

		/**
		 * Answer the kind of phrase of which this object is the type.
		 *
		 * @return My parent phrase kind.
		 */
		public final @Nullable
		PhraseKind parentKind ()
		{
			return parentKind;
		}

		/**
		 * The most general inner type for this kind of phrase.
		 *
		 * @return The most general inner type for this kind of phrase.
		 */
		public A_Type mostGeneralYieldType ()
		{
			return TOP.o();
		}

		/**
		 * The depth of this object in the PhraseKind hierarchy.
		 */
		final int depth;

		/** The JSON name of this type. */
		final String jsonName;

		/** The type tag associated with phrases of this kind. */
		public final TypeTag typeTag;

		/**
		 * Construct a new {@code PhraseKind}.
		 *
		 * @param jsonName
		 *        The JSON name of this type.
		 * @param parentKind
		 *        The kind of phrase for which this is a subkind.
		 * @param typeTag
		 *        The type tag associated with phrases of this kind.
		 */
		@SuppressWarnings({
			"OverridableMethodCallDuringObjectConstruction",
			"OverriddenMethodCallDuringObjectConstruction"
		})
		PhraseKind (
			final String jsonName,
			final @Nullable PhraseKind parentKind,
			final TypeTag typeTag)
		{
			this.jsonName = jsonName;
			this.parentKind = parentKind;
			this.typeTag = typeTag;
			if (parentKind == null)
			{
				depth = 0;
			}
			else
			{
				depth = parentKind.depth + 1;
			}
			mutableDescriptor = createDescriptor(Mutability.MUTABLE);
			sharedDescriptor = createDescriptor(Mutability.SHARED);
			mostGeneralType = createNoCheck(mostGeneralYieldType());
		}

		/**
		 * Create a descriptor for this kind.
		 *
		 * @param mutability The {@link Mutability} of the descriptor.
		 * @return The new descriptor.
		 */
		PhraseTypeDescriptor createDescriptor (final Mutability mutability)
		{
			return new PhraseTypeDescriptor(
				mutability, this, ObjectSlots.class, IntegerSlots.class);
		}

		/**
		 * Create a {@linkplain PhraseTypeDescriptor phrase type} given
		 * the yield type (the type of object produced by the expression).
		 *
		 * @param yieldType
		 *        The type of object that will be produced by an expression
		 *        which is of the type being constructed.
		 * @return The new phrase type, whose kind is the receiver.
		 */
		public A_Type create (
			final A_Type yieldType)
		{
			assert yieldType.isSubtypeOf(mostGeneralYieldType());
			return createNoCheck(yieldType);
		}

		/**
		 * Create a {@linkplain PhraseTypeDescriptor phrase type} given
		 * the yield type (the type of object produced by the expression).
		 *
		 * @param yieldType
		 *        The type of object that will be produced by an expression
		 *        which is of the type being constructed.
		 * @return The new phrase type, whose kind is the receiver.
		 */
		public A_Type createNoCheck (
			final A_Type yieldType)
		{
			final AvailObject type = mutableDescriptor.create();
			type.setSlot(EXPRESSION_TYPE, yieldType.makeImmutable());
			return type;
		}

		/** The descriptor for mutable instances of this kind. */
		final PhraseTypeDescriptor mutableDescriptor;

		/** The descriptor for shared instances of this kind. */
		final PhraseTypeDescriptor sharedDescriptor;

		/**
		 * The most general type for this kind of phrase.
		 */
		private final A_Type mostGeneralType;

		/**
		 * Answer a {@linkplain PhraseTypeDescriptor phrase type} whose
		 * kind is the receiver and whose expression type is {@linkplain
		 * Types#TOP top}. This is the most general phrase type of that
		 * kind.
		 *
		 * @return The new phrase type, whose kind is the receiver and whose
		 *         expression type is {@linkplain Types#TOP top}.
		 */
		public final A_Type mostGeneralType ()
		{
			return mostGeneralType;
		}

		/**
		 * Answer the {@code PhraseKind} that is the nearest common ancestor
		 * to both the receiver and the argument.  Compute it rather than look
		 * it up, since this is used to populate the lookup table.
		 *
		 * @param other The other {@code PhraseKind}.
		 * @return The nearest common ancestor (a {@code PhraseKind}).
		 */
		private PhraseKind computeCommonAncestorWith (
			final PhraseKind other)
		{
			PhraseKind a = this;
			PhraseKind b = other;
			while (a != b)
			{
				final int diff = b.depth - a.depth;
				if (diff <= 0)
				{
					a = stripNull(a.parentKind());
				}
				if (diff >= 0)
				{
					b = stripNull(b.parentKind());
				}
			}
			return a;
		}

		/**
		 * Answer the {@code PhraseKind} that is the nearest common ancestor
		 * to both the receiver and the argument.  Only use this after static
		 * initialization has completed.
		 *
		 * @param other The other {@code PhraseKind}.
		 * @return The nearest common ancestor (a {@code PhraseKind}).
		 */
		public final PhraseKind commonAncestorWith (
			final PhraseKind other)
		{
			return commonAncestors[ordinal() * all.length + other.ordinal()];
		}

		/**
		 * Answer the {@code PhraseKind} that is the nearest common descendant
		 * to both the receiver and the argument.  Only use this after static
		 * initialization has completed.
		 *
		 * @param other
		 *        The other {@code PhraseKind}.
		 * @return The nearest common descendant (a {@code PhraseKind}), or
		 *         {@code null} if there are no common descendants.
		 */
		public final @Nullable PhraseKind commonDescendantWith (
			final PhraseKind other)
		{
			return commonDescendants[ordinal() * all.length + other.ordinal()];
		}

		/** An array of all {@code PhraseKind} enumeration values. */
		private static final PhraseKind[] all = values();

		/**
		 * Answer an array of all {@code PhraseKind} enumeration values.
		 *
		 * @return An array of all {@code PhraseKind} enum values.  Do not
		 *         modify the array.
		 */
		public static PhraseKind[] all ()
		{
			return all.clone();
		}

		/**
		 * Answer the {@code PhraseKind} enumeration value having the given
		 * ordinal {@code int}.  The supplied ordinal must be valid.
		 *
		 * @param ordinal The ordinal to look up.
		 * @return The indicated {@code PhraseKind}.
		 */
		public static PhraseKind lookup (final int ordinal)
		{
			return all[ordinal];
		}

		/**
		 * An array where the value at [(t1 * #values) + t2] indicates the
		 * nearest common ancestor of the kinds with ordinals t1 and t2.  Note
		 * that this matrix is symmetric about its diagonal (i.e., it equals its
		 * transpose).
		 */
		private static final PhraseKind[] commonAncestors =
			new PhraseKind[all.length * all.length];

		static
		{
			// Populate the entire commonAncestors matrix.
			for (final PhraseKind kind1 : all)
			{
				for (final PhraseKind kind2 : all)
				{
					final int index = kind1.ordinal() * all.length
						+ kind2.ordinal();
					commonAncestors[index] =
						kind1.computeCommonAncestorWith(kind2);
				}
			}
		}

		/**
		 * An array where the value at [(t1 * #values) + t2] indicates the
		 * nearest common descendant of the kinds with ordinals t1 and t2, or
		 * {@code null} if the kinds have no common descendant.  Note that this
		 * matrix is symmetric about its diagonal (i.e., it equals its
		 * transpose).
		 */
		private static final PhraseKind[] commonDescendants =
			new PhraseKind[all.length * all.length];

		static
		{
			// Populate the entire commonDescendants matrix.
			for (final PhraseKind kind1 : all)
			{
				for (final PhraseKind kind2 : all)
				{
					// The kinds form a tree, so either kind1 is an ancestor of
					// kind2, kind2 is an ancestor of kind1, or they have no
					// common descent.
					final int index = kind1.ordinal() * all.length
						+ kind2.ordinal();
					final PhraseKind ancestor = commonAncestors[index];
					commonDescendants[index] =
						ancestor == kind1
							? kind2
							: ancestor == kind2
								? kind1
								: null;
				}
			}
		}

		/**
		 * Answer whether this is a subkind of (or equal to) the specified
		 * {@code PhraseKind}.
		 *
		 * @param purportedParent The kind that may be the ancestor.
		 * @return Whether the receiver descends from the argument.
		 */
		public final boolean isSubkindOf (final PhraseKind purportedParent)
		{
			final int index =
				ordinal() * all.length + purportedParent.ordinal();
			return commonAncestors[index] == purportedParent;
		}
	}

	@Override
	protected boolean allowsImmutableToMutableReferenceInField (
		final AbstractSlotsEnum e)
	{
		// Only the hash part may change (be set lazily), not the kind.
		return e == HASH_AND_MORE;
	}

	/**
	 * Return the type of object that would be produced by a phrase of this
	 * type.
	 *
	 * @return The {@linkplain TypeDescriptor type} of the {@link AvailObject}
	 *         that will be produced by a phrase of this type.
	 */
	@Override @AvailMethod
	public A_Type o_ExpressionType (final AvailObject object)
	{
		return object.slot(EXPRESSION_TYPE);
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>Phrase types are equal when they are of the same kind and have the
	 * same expression type.</p>
	 */
	@Override @AvailMethod
	public boolean o_Equals (final AvailObject object, final A_BasicObject another)
	{
		return another.equalsPhraseType(object);
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>Phrase types are equal when they are of the same kind and have the
	 * same expression type.</p>
	 */
	@Override @AvailMethod
	public boolean o_EqualsPhraseType (
		final AvailObject object,
		final A_Type aPhraseType)
	{
		return kind == aPhraseType.phraseKind()
			&& object.slot(EXPRESSION_TYPE).equals(
				aPhraseType.expressionType());
 	}

	/**
	 * Subclasses of {@code PhraseTypeDescriptor} must implement {@linkplain
	 * A_Phrase phrases} must implement {@link A_BasicObject#hash()}.
	 */
	@Override @AvailMethod
	public int o_Hash (final AvailObject object)
	{
		int hash = object.slot(HASH_OR_ZERO);
		if (hash == 0)
		{
			hash = object.slot(EXPRESSION_TYPE).hash()
				^ (kind.ordinal() * multiplier);
			object.setSlot(HASH_OR_ZERO, hash);
		}
		return hash;
	}

	@Override @AvailMethod
	public boolean o_IsSubtypeOf (final AvailObject object, final A_Type aType)
	{
		return aType.isSupertypeOfPhraseType(object);
	}

	@Override
	@AvailMethod
	public boolean o_IsSupertypeOfListNodeType (
		final AvailObject object,
		final A_Type aListNodeType)
	{
		return LIST_PHRASE.isSubkindOf(kind)
			&& aListNodeType.expressionType().isSubtypeOf(
				object.expressionType());
	}

	@Override
	@AvailMethod
	public boolean o_IsSupertypeOfPhraseType (
		final AvailObject object,
		final A_Type aPhraseType)
	{
		final PhraseKind otherKind = aPhraseType.phraseKind();
		return otherKind.isSubkindOf(kind)
			&& aPhraseType.expressionType().isSubtypeOf(
				object.expressionType());
	}

	/**
	 * Return the {@linkplain PhraseKind phrase kind} that this phrase type
	 * implements.
	 *
	 * @return The {@linkplain PhraseKind kind} of phrase that the object is.
	 */
	@Override @AvailMethod
	public PhraseKind o_PhraseKind (final AvailObject object)
	{
		return kind;
	}

	@Override @AvailMethod
	public boolean o_PhraseKindIsUnder (
		final AvailObject object,
		final PhraseKind expectedPhraseKind)
	{
		return kind.isSubkindOf(expectedPhraseKind);
	}

	@Override
	public SerializerOperation o_SerializerOperation (
		final AvailObject object)
	{
		return SerializerOperation.PARSE_NODE_TYPE;
	}

	@Override
	public A_Type o_SubexpressionsTupleType (final AvailObject object)
	{
		// Only applicable if the expression type is a tuple type.
		return tupleTypeFromTupleOfTypes(
			object.slot(EXPRESSION_TYPE), PARSE_PHRASE::create);
	}

	@Override @AvailMethod
	public A_Type o_TypeIntersection (
		final AvailObject object,
		final A_Type another)
	{
		return another.typeIntersectionOfPhraseType(object);
	}

	@Override
	public A_Type o_TypeIntersectionOfListNodeType (
		final AvailObject object,
		final A_Type aListNodeType)
	{
		// Intersection of two list phrase types.
		final @Nullable PhraseKind intersectionKind =
			kind.commonDescendantWith(
				aListNodeType.phraseKind());
		if (intersectionKind == null)
		{
			return bottom();
		}
		assert intersectionKind.isSubkindOf(LIST_PHRASE);
		return createListNodeType(
			intersectionKind,
			object.expressionType().typeIntersection(
				aListNodeType.expressionType()),
			aListNodeType.subexpressionsTupleType());
	}

	@Override @AvailMethod
	public A_Type o_TypeIntersectionOfPhraseType (
		final AvailObject object,
		final A_Type aPhraseType)
	{
		final @Nullable PhraseKind intersectionKind =
			kind.commonDescendantWith(aPhraseType.phraseKind());
		if (intersectionKind == null)
		{
			return bottom();
		}
		assert !intersectionKind.isSubkindOf(LIST_PHRASE);
		// It should be safe to assume the mostGeneralType() of a subkind is
		// always a subtype of the mostGeneralType() of a superkind.
		return intersectionKind.createNoCheck(
			object.slot(EXPRESSION_TYPE).typeIntersection(
				aPhraseType.expressionType()));
	}

	@Override @AvailMethod
	public A_Type o_TypeUnion (
		final AvailObject object,
		final A_Type another)
	{
		return another.typeUnionOfPhraseType(object);
	}

	@Override
	public A_Type o_TypeUnionOfListNodeType (
		final AvailObject object,
		final A_Type aListNodeType)
	{
		// Union of a non-list phrase type and a list phrase type is a non-list
		// phrase type.
		final PhraseKind otherKind = aListNodeType.phraseKind();
		assert otherKind.isSubkindOf(LIST_PHRASE);
		final PhraseKind unionKind = kind.commonAncestorWith(otherKind);
		assert !unionKind.isSubkindOf(LIST_PHRASE);
		return unionKind.create(
			object.expressionType().typeUnion(aListNodeType.expressionType()));
	}

	@Override @AvailMethod
	public A_Type o_TypeUnionOfPhraseType (
		final AvailObject object,
		final A_Type aPhraseType)
	{
		final PhraseKind unionKind =
			kind.commonAncestorWith(
				aPhraseType.phraseKind());
		return unionKind.createNoCheck(
			object.slot(EXPRESSION_TYPE).typeUnion(
				aPhraseType.expressionType()));
	}

	@Override
	public void o_WriteTo (final AvailObject object, final JSONWriter writer)
	{
		writer.startObject();
		writer.write("kind");
		writer.write(kind.jsonName);
		writer.write("expression type");
		object.slot(EXPRESSION_TYPE).writeTo(writer);
		writer.endObject();
	}

	@Override
	public void printObjectOnAvoidingIndent (
		final AvailObject object,
		final StringBuilder builder,
		final IdentityHashMap<A_BasicObject, Void> recursionMap,
		final int indent)
	{
		if (kind == PARSE_PHRASE)
		{
			builder.append("phrase");
		}
		else
		{
			final String name = kind.name().toLowerCase().replace('_', ' ');
			builder.append(name);
		}
		builder.append('⇒');
		object.expressionType().printOnAvoidingIndent(
			builder, recursionMap, indent + 1);
	}

	/**
	 * Does the specified {@linkplain AvailObject#flattenStatementsInto(List)
	 * flat} {@linkplain List list} of {@linkplain PhraseDescriptor phrases}
	 * contain only statements?
	 *
	 * TODO MvG - REVISIT to make this work sensibly.  Probably only allow
	 *      statements in a sequence/first-of-sequence, and have blocks hold an
	 *      optional final <em>expression</em>.
	 *
	 * @param flat
	 *        A flattened list of statements.
	 * @param resultType
	 *        The result type of the sequence. Use {@linkplain Types#TOP top}
	 *        if unconcerned about result type.
	 * @return {@code true} if the list contains only statements, {@code false}
	 *         otherwise.
	 */
	@SuppressWarnings("BooleanMethodIsAlwaysInverted")
	public static boolean containsOnlyStatements (
		final List<A_Phrase> flat,
		final A_Type resultType)
	{
		final int statementCount = flat.size();
		for (int i = 0; i < statementCount; i++)
		{
			final A_Phrase statement = flat.get(i);
			assert !statement.phraseKindIsUnder(SEQUENCE_PHRASE);
			final boolean valid;
			if (i + 1 < statementCount)
			{
				valid =
					(statement.phraseKindIsUnder(STATEMENT_PHRASE)
						|| statement.phraseKindIsUnder(ASSIGNMENT_PHRASE)
						|| statement.phraseKindIsUnder(SEND_PHRASE))
					&& statement.expressionType().isTop();
			}
			else
			{
				valid = statement.expressionType().isSubtypeOf(resultType);
			}
			if (!valid)
			{
				return false;
			}
		}
		return true;
	}

	/** The {@code PhraseKind} of instances that use this descriptor. */
	protected final PhraseKind kind;

	/**
	 * Constants that are phrase types.  These must be initialized only after
	 * the {@code PhraseTypeDescriptor}s have been created.
	 */
	public static final class Constants
	{
		/**
		 * Forbid instantiation.
		 */
		private Constants ()
		{
			// No implementation required.
		}

		/** The phrase type for string literals. */
		public static final A_Type stringLiteralType =
			LITERAL_PHRASE.create(
				literalTokenType(
					stringType())
			).makeShared();
	}

	/**
	 * Construct a new descriptor for this kind of phrase type.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 * @param kind
	 *        The {@code PhraseKind} of the new descriptor.
	 * @param objectSlotsEnumClass
	 *        The Java {@link Class} which is a subclass of {@link
	 *        ObjectSlotsEnum} and defines this object's object slots layout, or
	 *        null if there are no object slots.
	 * @param integerSlotsEnumClass
	 *        The Java {@link Class} which is a subclass of {@link
	 *        IntegerSlotsEnum} and defines this object's object slots layout,
	 *        or null if there are no integer slots.
	 */
	protected PhraseTypeDescriptor (
		final Mutability mutability,
		final PhraseKind kind,
		final @Nullable Class<? extends ObjectSlotsEnum> objectSlotsEnumClass,
		final @Nullable Class<? extends IntegerSlotsEnum> integerSlotsEnumClass)
	{
		super(mutability, kind.typeTag, objectSlotsEnumClass, integerSlotsEnumClass);
		this.kind = kind;
	}

	@Override
	public PhraseTypeDescriptor mutable ()
	{
		return kind.mutableDescriptor;
	}

	@Override
	public PhraseTypeDescriptor immutable ()
	{
		// There are no immutable descriptors.
		return kind.sharedDescriptor;
	}

	@Override
	public PhraseTypeDescriptor shared ()
	{
		return kind.sharedDescriptor;
	}
}
