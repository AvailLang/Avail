/*
 * ParseNodeTypeDescriptor.java
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
import com.avail.annotations.HideFieldInDebugger;
import com.avail.annotations.InnerAccess;
import com.avail.serialization.SerializerOperation;
import com.avail.utility.json.JSONWriter;

import javax.annotation.Nullable;
import java.util.IdentityHashMap;
import java.util.List;

import static com.avail.descriptor.AvailObject.multiplier;
import static com.avail.descriptor.BottomTypeDescriptor.bottom;
import static com.avail.descriptor.FunctionTypeDescriptor.mostGeneralFunctionType;
import static com.avail.descriptor.ListNodeTypeDescriptor.createListNodeType;
import static com.avail.descriptor.ListNodeTypeDescriptor.createListNodeTypeNoCheck;
import static com.avail.descriptor.ParseNodeTypeDescriptor.IntegerSlots.HASH_AND_MORE;
import static com.avail.descriptor.ParseNodeTypeDescriptor.IntegerSlots.HASH_OR_ZERO;
import static com.avail.descriptor.ParseNodeTypeDescriptor.ObjectSlots.EXPRESSION_TYPE;
import static com.avail.descriptor.ParseNodeTypeDescriptor.ParseNodeKind.*;
import static com.avail.descriptor.TupleTypeDescriptor.mostGeneralTupleType;
import static com.avail.descriptor.TupleTypeDescriptor.tupleTypeFromTupleOfTypes;
import static com.avail.descriptor.TypeDescriptor.Types.ANY;
import static com.avail.descriptor.TypeDescriptor.Types.TOP;
import static com.avail.descriptor.VariableTypeDescriptor.mostGeneralVariableType;

/**
 * Define the structure and behavior of parse node types.  The parse node types
 * are all parameterized by expression type, but they also have a relationship
 * to each other based on a fiat hierarchy.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public class ParseNodeTypeDescriptor
extends TypeDescriptor
{
	/**
	 * My slots of type {@linkplain Integer int}.
	 */
	public enum IntegerSlots
	implements IntegerSlotsEnum
	{
		/**
		 * The low 32 bits are used for caching the hash.
		 */
		@HideFieldInDebugger
		HASH_AND_MORE;

		/**
		 * The hash, or zero ({@code 0}) if the hash has not yet been computed.
		 */
		static final BitField HASH_OR_ZERO = bitField(HASH_AND_MORE, 0, 32);
	}

	/**
	 * My slots of type {@link AvailObject}.
	 */
	public enum ObjectSlots
	implements ObjectSlotsEnum
	{
		/**
		 * The type of value that this expression would produce.
		 */
		EXPRESSION_TYPE;
	}

	/**
	 * My hierarchy of kinds of parse nodes.
	 */
	public enum ParseNodeKind
	implements IntegerEnumSlotDescriptionEnum
	{
		/** The root parse node kind. */
		PARSE_NODE("phrase type", null, TypeTag.PHRASE_TAG),

		/** The kind of a parse marker. */
		MARKER_NODE(
			"marker phrase type", PARSE_NODE, TypeTag.MARKER_PHRASE_TAG),

		/** The abstract parent kind of all expression nodes. */
		EXPRESSION_NODE(
			"expression phrase type",
			PARSE_NODE,
			TypeTag.EXPRESSION_PHRASE_TAG),

		/**
		 * The kind of an {@linkplain AssignmentNodeDescriptor assignment node}.
		 */
		ASSIGNMENT_NODE(
			"assignment phrase type",
			EXPRESSION_NODE,
			TypeTag.ASSIGNMENT_PHRASE_TAG),

		/** The kind of a {@linkplain BlockNodeDescriptor block node}. */
		BLOCK_NODE(
			"block phrase type",
			EXPRESSION_NODE,
			TypeTag.BLOCK_PHRASE_TAG)
		{
			@Override
			public A_Type mostGeneralYieldType ()
			{
				return mostGeneralFunctionType();
			}
		},

		/** The kind of a {@linkplain LiteralNodeDescriptor literal node}. */
		LITERAL_NODE(
			"literal node type",
			EXPRESSION_NODE,
			TypeTag.LITERAL_PHRASE_TAG)
		{
			@Override
			public A_Type mostGeneralYieldType ()
			{
				return ANY.o();
			}
		},

		/**
		 * The kind of a {@linkplain ReferenceNodeDescriptor reference node}.
		 */
		REFERENCE_NODE(
			"variable reference phrase type",
			EXPRESSION_NODE,
			TypeTag.REFERENCE_PHRASE_TAG)
		{
			@Override
			public A_Type mostGeneralYieldType ()
			{
				return mostGeneralVariableType();
			}
		},

		/**
		 * The kind of a {@linkplain SuperCastNodeDescriptor super cast node}.
		 */
		SUPER_CAST_NODE(
			"super cast phrase", EXPRESSION_NODE, TypeTag.SUPER_CAST_PHRASE_TAG)
		{
			@Override
			public A_Type mostGeneralYieldType ()
			{
				return ANY.o();
			}
		},

		/** The kind of a {@linkplain SendNodeDescriptor send node}. */
		SEND_NODE("send phrase type", EXPRESSION_NODE, TypeTag.SEND_PHRASE_TAG),

		/** The kind of a {@linkplain ListNodeDescriptor list node}. */
		LIST_NODE("list phrase type", EXPRESSION_NODE, TypeTag.LIST_PHRASE_TAG)
		{
			/** Create a descriptor for this kind. */
			@Override
			ParseNodeTypeDescriptor createDescriptor (
				final Mutability mutability)
			{
				return new ListNodeTypeDescriptor(mutability, this);
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
				final ParseNodeKind listNodeKind = this;
				final A_Type subexpressionsTupleType =
					tupleTypeFromTupleOfTypes(yieldType, PARSE_NODE::create);
				return createListNodeTypeNoCheck(
					listNodeKind, yieldType, subexpressionsTupleType);
			}
		},

		/**
		 * The kind of a {@linkplain PermutedListNodeDescriptor permuted list
		 * node}. */
		PERMUTED_LIST_NODE(
			"permuted list phrase type",
			LIST_NODE,
			TypeTag.PERMUTED_LIST_PHRASE_TAG)
		{
			/** Create a descriptor for this kind. */
			@Override
			ParseNodeTypeDescriptor createDescriptor (
				final Mutability mutability)
			{
				return new ListNodeTypeDescriptor(mutability, this);
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
				final ParseNodeKind listNodeKind = this;
				final A_Type subexpressionsTupleType =
					tupleTypeFromTupleOfTypes(yieldType, PARSE_NODE::create);
				return createListNodeTypeNoCheck(
					listNodeKind, yieldType, subexpressionsTupleType);
			}
		},

		/**
		 * The kind of a {@linkplain VariableUseNodeDescriptor variable use
		 * node}.
		 */
		VARIABLE_USE_NODE(
			"variable use phrase type", EXPRESSION_NODE, TypeTag.VARIABLE_TAG)
		{
			@Override
			public A_Type mostGeneralYieldType ()
			{
				return ANY.o();
			}
		},

		/** A phrase that does not produce a result. */
		STATEMENT_NODE(
			"statement phrase type", PARSE_NODE, TypeTag.STATEMENT_PHRASE_TAG),

		/** The kind of a {@linkplain SequenceNodeDescriptor sequence node}. */
		SEQUENCE_NODE(
			"sequence phrase type",
			STATEMENT_NODE,
			TypeTag.SEQUENCE_PHRASE_TAG),

		/**
		 * The kind of a {@linkplain FirstOfSequenceNodeDescriptor
		 * first-of-sequence node}.
		 */
		FIRST_OF_SEQUENCE_NODE(
			"first-of-sequence phrase type",
			STATEMENT_NODE,
			TypeTag.FIRST_OF_SEQUENCE_PHRASE_TAG),

		/**
		 * The kind of a {@linkplain DeclarationNodeDescriptor declaration
		 * node}.
		 */
		DECLARATION_NODE(
			"declaration phrase type",
			STATEMENT_NODE,
			TypeTag.DECLARATION_PHRASE_TAG),

		/** The kind of an argument declaration node. */
		ARGUMENT_NODE(
			"argument phrase type",
			DECLARATION_NODE,
			TypeTag.ARGUMENT_PHRASE_TAG),

		/** The kind of a label declaration node. */
		LABEL_NODE(
			"label phrase type", DECLARATION_NODE, TypeTag.LABEL_PHRASE_TAG),

		/** The kind of a local variable declaration node. */
		LOCAL_VARIABLE_NODE(
			"local variable phrase type",
			DECLARATION_NODE,
			TypeTag.LOCAL_VARIABLE_PHRASE_TAG),

		/** The kind of a local constant declaration node. */
		LOCAL_CONSTANT_NODE(
			"local constant phrase type",
			DECLARATION_NODE,
			TypeTag.LOCAL_CONSTANT_PHRASE_TAG),

		/** The kind of a module variable declaration node. */
		MODULE_VARIABLE_NODE(
			"module variable phrase type",
			DECLARATION_NODE,
			TypeTag.MODULE_VARIABLE_PHRASE_TAG),

		/** The kind of a module constant declaration node. */
		MODULE_CONSTANT_NODE(
			"module constant phrase type",
			DECLARATION_NODE,
			TypeTag.MODULE_CONSTANT_PHRASE_TAG),

		/** The kind of a primitive failure reason variable declaration. */
		PRIMITIVE_FAILURE_REASON_NODE(
			"primitive failure reason phrase type",
			DECLARATION_NODE,
			TypeTag.PRIMITIVE_FAILURE_REASON_PHRASE_TAG),

		/**
		 * A statement phrase built from an expression.  At the moment, only
		 * assignments and sends can be expression-as-statement phrases.
		 */
		EXPRESSION_AS_STATEMENT_NODE(
			"expression as statement phrase type",
			STATEMENT_NODE,
			TypeTag.EXPRESSION_AS_STATEMENT_PHRASE_TAG),

		/** The result of a macro substitution. */
		MACRO_SUBSTITUTION(
			"macro substitution phrase type",
			PARSE_NODE,
			TypeTag.MACRO_SUBSTITUTION_PHRASE_TAG);

		/**
		 * The kind of parse node that this kind is a child of.
		 */
		final @Nullable ParseNodeKind parentKind;

		/**
		 * Answer the kind of parse node of which this object is the type.
		 *
		 * @return My parent parse node kind.
		 */
		public final @Nullable ParseNodeKind parentKind ()
		{
			return parentKind;
		}

		/**
		 * The most general inner type for this kind of parse node.
		 *
		 * @return The most general inner type for this kind of parse node.
		 */
		public A_Type mostGeneralYieldType ()
		{
			return TOP.o();
		}

		/**
		 * The depth of this object in the ParseNodeKind hierarchy.
		 */
		final int depth;

		/** The JSON name of this type. */
		final String jsonName;

		/** The type tag associated with phrases of this kind. */
		final TypeTag typeTag;

		/**
		 * Construct a new {@link ParseNodeKind}.
		 *
		 * @param jsonName
		 *        The JSON name of this type.
		 * @param parentKind
		 *        The kind of parse node for which this is a subkind.
		 * @param typeTag
		 *        The type tag associated with phrases of this kind.
		 */
		ParseNodeKind (
			final String jsonName,
			final @Nullable ParseNodeKind parentKind,
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

		/** Create a descriptor for this kind. */
		ParseNodeTypeDescriptor createDescriptor (final Mutability mutability)
		{
			return new ParseNodeTypeDescriptor(
				mutability, this, ObjectSlots.class, IntegerSlots.class);
		}

		/**
		 * Create a {@linkplain ParseNodeTypeDescriptor parse node type} given
		 * the yield type (the type of object produced by the expression).
		 *
		 * @param yieldType
		 *        The type of object that will be produced by an expression
		 *        which is of the type being constructed.
		 * @return The new parse node type, whose kind is the receiver.
		 */
		public A_Type create (
			final A_Type yieldType)
		{
			assert yieldType.isSubtypeOf(mostGeneralYieldType());
			return createNoCheck(yieldType);
		}

		/**
		 * Create a {@linkplain ParseNodeTypeDescriptor parse node type} given
		 * the yield type (the type of object produced by the expression).
		 *
		 * @param yieldType
		 *        The type of object that will be produced by an expression
		 *        which is of the type being constructed.
		 * @return The new parse node type, whose kind is the receiver.
		 */
		public A_Type createNoCheck (
			final A_Type yieldType)
		{
			final AvailObject type = mutableDescriptor.create();
			type.setSlot(EXPRESSION_TYPE, yieldType.makeImmutable());
			return type;
		}

		/** The descriptor for mutable instances of this kind. */
		@InnerAccess final ParseNodeTypeDescriptor mutableDescriptor;

		/** The descriptor for shared instances of this kind. */
		@InnerAccess final ParseNodeTypeDescriptor sharedDescriptor;

		/**
		 * The most general type for this kind of parse node.
		 */
		private final A_Type mostGeneralType;

		/**
		 * Answer a {@linkplain ParseNodeTypeDescriptor parse node type} whose
		 * kind is the receiver and whose expression type is {@linkplain
		 * Types#TOP top}. This is the most general parse node type of that
		 * kind.
		 *
		 * @return The new parse node type, whose kind is the receiver and whose
		 *         expression type is {@linkplain Types#TOP top}.
		 */
		public final A_Type mostGeneralType ()
		{
			return mostGeneralType;
		}

		/**
		 * Answer the {@link ParseNodeKind} that is the nearest common ancestor
		 * to both the receiver and the argument.  Compute it rather than look
		 * it up, since this is used to populate the lookup table.
		 *
		 * @param other The other {@link ParseNodeKind}.
		 * @return The nearest common ancestor (a {@link ParseNodeKind}).
		 */
		private final ParseNodeKind computeCommonAncestorWith (
			final ParseNodeKind other)
		{
			ParseNodeKind a = this;
			ParseNodeKind b = other;
			while (a != b)
			{
				final int diff = b.depth - a.depth;
				if (diff <= 0)
				{
					a = a.parentKind();
					assert a != null;
				}
				if (diff >= 0)
				{
					b = b.parentKind();
					assert b != null;
				}
			}
			return a;
		}

		/**
		 * Answer the {@link ParseNodeKind} that is the nearest common ancestor
		 * to both the receiver and the argument.  Only use this after static
		 * initialization has completed.
		 *
		 * @param other The other {@link ParseNodeKind}.
		 * @return The nearest common ancestor (a {@link ParseNodeKind}).
		 */
		public final ParseNodeKind commonAncestorWith (
			final ParseNodeKind other)
		{
			return commonAncestors[ordinal() * all.length + other.ordinal()];
		}

		/**
		 * Answer the {@link ParseNodeKind} that is the nearest common
		 * descendant to both the receiver and the argument.  Only use this
		 * after static initialization has completed.
		 *
		 * @param other The other {@link ParseNodeKind}.
		 * @return The nearest common descendant (a {@link ParseNodeKind}), or
		 *         {@code null} if there are no common descendants.
		 */
		public final @Nullable ParseNodeKind commonDescendantWith (
			final ParseNodeKind other)
		{
			return commonDescendants[ordinal() * all.length + other.ordinal()];
		}

		/** An array of all {@link ParseNodeKind} enumeration values. */
		private static final ParseNodeKind[] all = values();

		/**
		 * Answer an array of all {@link ParseNodeKind} enumeration values.
		 *
		 * @return An array of all {@link ParseNodeKind} enum values.  Do not
		 *         modify the array.
		 */
		public static ParseNodeKind[] all ()
		{
			return all;
		}

		/**
		 * Answer the {@link ParseNodeKind} enumeration value having the given
		 * ordinal {@code int}.  The supplied ordinal must be valid.
		 *
		 * @param ordinal The ordinal to look up.
		 * @return The indicated {@link ParseNodeKind}.
		 */
		public static ParseNodeKind lookup (final int ordinal)
		{
			return all[ordinal];
		}

		/**
		 * An array where the value at [(t1 * #values) + t2] indicates the
		 * nearest common ancestor of the kinds with ordinals t1 and t2.  Note
		 * that this matrix is symmetric about its diagonal (i.e., it equals its
		 * transpose).
		 */
		private static final ParseNodeKind[] commonAncestors =
			new ParseNodeKind [all.length * all.length];

		static
		{
			// Populate the entire commonAncestors matrix.
			for (final ParseNodeKind kind1 : all)
			{
				for (final ParseNodeKind kind2 : all)
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
		private static final ParseNodeKind[] commonDescendants =
			new ParseNodeKind [all.length * all.length];

		static
		{
			// Populate the entire commonDescendants matrix.
			for (final ParseNodeKind kind1 : all)
			{
				for (final ParseNodeKind kind2 : all)
				{
					// The kinds form a tree, so either kind1 is an ancestor of
					// kind2, kind2 is an ancestor of kind1, or they have no
					// common descent.
					final int index = kind1.ordinal() * all.length
						+ kind2.ordinal();
					final ParseNodeKind ancestor = commonAncestors[index];
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
		 * {@link ParseNodeKind}.
		 *
		 * @param purportedParent The kind that may be the ancestor.
		 * @return Whether the receiver descends from the argument.
		 */
		public final boolean isSubkindOf (final ParseNodeKind purportedParent)
		{
			final int index =
				ordinal() * all.length + purportedParent.ordinal();
			return commonAncestors[index] == purportedParent;
		}
	}

	@Override
	boolean allowsImmutableToMutableReferenceInField (
		final AbstractSlotsEnum e)
	{
		// Only the hash part may change (be set lazily), not the kind.
		return e == HASH_AND_MORE;
	}

	/**
	 * Return the type of object that would be produced by a parse node of this
	 * type.
	 *
	 * @return The {@linkplain TypeDescriptor type} of the {@link AvailObject}
	 *         that will be produced by a parse node of this type.
	 */
	@Override @AvailMethod
	A_Type o_ExpressionType (final AvailObject object)
	{
		return object.slot(EXPRESSION_TYPE);
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * {@linkplain ParseNodeTypeDescriptor parse node types} are equal when they
	 * are of the same kind and have the same expression type.
	 * </p>
	 */
	@Override @AvailMethod
	boolean o_Equals (final AvailObject object, final A_BasicObject another)
	{
		return another.equalsParseNodeType(object);
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * {@linkplain ParseNodeTypeDescriptor parse node types} are equal when they
	 * are of the same kind and have the same expression type.
	 * </p>
	 */
	@Override @AvailMethod
	boolean o_EqualsParseNodeType (
		final AvailObject object,
		final A_Type aParseNodeType)
	{
		return kind == aParseNodeType.parseNodeKind()
			&& object.slot(EXPRESSION_TYPE).equals(
				aParseNodeType.expressionType());
 	}

	/**
	 * {@linkplain ParseNodeTypeDescriptor parse nodes} must implement {@link
	 * AbstractDescriptor#o_Hash(AvailObject) hash}.
	 */
	@Override @AvailMethod
	int o_Hash (final AvailObject object)
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
	boolean o_IsSubtypeOf (final AvailObject object, final A_Type aType)
	{
		return aType.isSupertypeOfParseNodeType(object);
	}

	@Override
	@AvailMethod
	boolean o_IsSupertypeOfListNodeType (
		final AvailObject object,
		final A_Type aListNodeType)
	{
		return LIST_NODE.isSubkindOf(kind)
			&& aListNodeType.expressionType().isSubtypeOf(
				object.expressionType());
	}

	@Override
	@AvailMethod
	boolean o_IsSupertypeOfParseNodeType (
		final AvailObject object,
		final A_Type aParseNodeType)
	{
		final ParseNodeKind otherKind = aParseNodeType.parseNodeKind();
		return otherKind.isSubkindOf(kind)
			&& aParseNodeType.expressionType().isSubtypeOf(
				object.expressionType());
	}

	/**
	 * Return the {@linkplain ParseNodeKind parse node kind} that this parse
	 * node type implements.
	 *
	 * @return The {@linkplain ParseNodeKind kind} of parse node that the object
	 *         is.
	 */
	@Override @AvailMethod
	ParseNodeKind o_ParseNodeKind (final AvailObject object)
	{
		return kind;
	}

	@Override @AvailMethod
	boolean o_ParseNodeKindIsUnder (
		final AvailObject object,
		final ParseNodeKind expectedParseNodeKind)
	{
		return kind.isSubkindOf(expectedParseNodeKind);
	}

	@Override
	SerializerOperation o_SerializerOperation (final AvailObject object)
	{
		return SerializerOperation.PARSE_NODE_TYPE;
	}

	@Override
	A_Type o_SubexpressionsTupleType (final AvailObject object)
	{
		// Only applicable if the expression type is a tuple type.
		return tupleTypeFromTupleOfTypes(
			object.slot(EXPRESSION_TYPE), PARSE_NODE::create);
	}

	@Override @AvailMethod
	A_Type o_TypeIntersection (
		final AvailObject object,
		final A_Type another)
	{
		return another.typeIntersectionOfParseNodeType(object);
	}

	@Override
	A_Type o_TypeIntersectionOfListNodeType (
		final AvailObject object,
		final A_Type aListNodeType)
	{
		// Intersection of two list node types.
		final @Nullable ParseNodeKind intersectionKind =
			kind.commonDescendantWith(
				aListNodeType.parseNodeKind());
		if (intersectionKind == null)
		{
			return bottom();
		}
		assert intersectionKind.isSubkindOf(LIST_NODE);
		return createListNodeType(
			intersectionKind,
			object.expressionType().typeIntersection(
				aListNodeType.expressionType()),
			aListNodeType.subexpressionsTupleType());
	}

	@Override @AvailMethod
	A_Type o_TypeIntersectionOfParseNodeType (
		final AvailObject object,
		final A_Type aParseNodeType)
	{
		final @Nullable ParseNodeKind intersectionKind =
			kind.commonDescendantWith(aParseNodeType.parseNodeKind());
		if (intersectionKind == null)
		{
			return bottom();
		}
		assert !intersectionKind.isSubkindOf(LIST_NODE);
		// It should be safe to assume the mostGeneralType() of a subkind is
		// always a subtype of the mostGeneralType() of a superkind.
		return intersectionKind.createNoCheck(
			object.slot(EXPRESSION_TYPE).typeIntersection(
				aParseNodeType.expressionType()));
	}

	@Override @AvailMethod
	A_Type o_TypeUnion (
		final AvailObject object,
		final A_Type another)
	{
		return another.typeUnionOfParseNodeType(object);
	}

	@Override
	A_Type o_TypeUnionOfListNodeType (
		final AvailObject object,
		final A_Type aListNodeType)
	{
		// Union of a non-list parse node type and a list node type is a
		// non-list parse node type.
		final ParseNodeKind otherKind = aListNodeType.parseNodeKind();
		assert otherKind.isSubkindOf(LIST_NODE);
		final ParseNodeKind unionKind = kind.commonAncestorWith(otherKind);
		assert !unionKind.isSubkindOf(LIST_NODE);
		return unionKind.create(
			object.expressionType().typeUnion(aListNodeType.expressionType()));
	}

	@Override @AvailMethod
	A_Type o_TypeUnionOfParseNodeType (
		final AvailObject object,
		final A_Type aParseNodeType)
	{
		final ParseNodeKind unionKind =
			kind.commonAncestorWith(
				aParseNodeType.parseNodeKind());
		return unionKind.createNoCheck(
			object.slot(EXPRESSION_TYPE).typeUnion(
				aParseNodeType.expressionType()));
	}

	@Override
	void o_WriteTo (final AvailObject object, final JSONWriter writer)
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
		if (kind == PARSE_NODE)
		{
			builder.append("phrase");
		}
		else
		{
			final String name = kind.name().toLowerCase()
				.replace("node", "phrase")
				.replace('_', ' ');
			builder.append(name);
		}
		builder.append("⇒");
		object.expressionType().printOnAvoidingIndent(
			builder, recursionMap, indent + 1);
	}

	/**
	 * Does the specified {@linkplain AvailObject#flattenStatementsInto(List)
	 * flat} {@linkplain List list} of {@linkplain ParseNodeDescriptor parse
	 * nodes} contain only statements?
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
	public static boolean containsOnlyStatements (
		final List<A_Phrase> flat,
		final A_Type resultType)
	{
		final int statementCount = flat.size();
		for (int i = 0; i < statementCount; i++)
		{
			final A_Phrase statement = flat.get(i);
			assert !statement.parseNodeKindIsUnder(SEQUENCE_NODE);
			final boolean valid;
			if (i + 1 < statementCount)
			{
				valid =
					(statement.parseNodeKindIsUnder(STATEMENT_NODE)
						|| statement.parseNodeKindIsUnder(ASSIGNMENT_NODE)
						|| statement.parseNodeKindIsUnder(SEND_NODE))
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

	/** The {@link ParseNodeKind} of instances that use this descriptor. */
	protected final ParseNodeKind kind;

	/**
	 * Construct a new descriptor for this kind of phrase type.
	 *
	 * @param mutability
	 *            The {@linkplain Mutability mutability} of the new descriptor.
	 * @param kind
	 *            The {@link ParseNodeKind} of the new descriptor.
	 * @param objectSlotsEnumClass
	 *            The Java {@link Class} which is a subclass of {@link
	 *            ObjectSlotsEnum} and defines this object's object slots
	 *            layout, or null if there are no object slots.
	 * @param integerSlotsEnumClass
	 *            The Java {@link Class} which is a subclass of {@link
	 *            IntegerSlotsEnum} and defines this object's object slots
	 *            layout, or null if there are no integer slots.
	 */
	protected ParseNodeTypeDescriptor (
		final Mutability mutability,
		final ParseNodeKind kind,
		final @Nullable Class<? extends ObjectSlotsEnum> objectSlotsEnumClass,
		final @Nullable Class<? extends IntegerSlotsEnum> integerSlotsEnumClass)
	{
		super(mutability, kind.typeTag, objectSlotsEnumClass, integerSlotsEnumClass);
		this.kind = kind;
	}

	@Override
	ParseNodeTypeDescriptor mutable ()
	{
		return kind.mutableDescriptor;
	}

	@Override
	ParseNodeTypeDescriptor immutable ()
	{
		// There are no immutable descriptors.
		return kind.sharedDescriptor;
	}

	@Override
	ParseNodeTypeDescriptor shared ()
	{
		return kind.sharedDescriptor;
	}
}
