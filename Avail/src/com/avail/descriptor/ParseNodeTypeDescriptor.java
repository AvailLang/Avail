/**
 * ParseNodeTypeDescriptor.java
 * Copyright © 1993-2013, Mark van Gulik and Todd L Smith.
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

import static com.avail.descriptor.AvailObject.multiplier;
import static com.avail.descriptor.ParseNodeTypeDescriptor.ParseNodeKind.*;
import static com.avail.descriptor.TypeDescriptor.Types.*;
import static com.avail.descriptor.ParseNodeTypeDescriptor.ObjectSlots.*;
import static com.avail.descriptor.ParseNodeTypeDescriptor.IntegerSlots.*;
import java.util.List;
import com.avail.annotations.*;
import com.avail.serialization.SerializerOperation;

/**
 * Define the structure and behavior of parse node types.  The parse node types
 * are all parameterized by expression type, but they also have a relationship
 * to each other based on a fiat hierarchy.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class ParseNodeTypeDescriptor
extends TypeDescriptor
{
	/**
	 * My slots of type {@link AvailObject}.
	 */
	public enum ObjectSlots
	implements ObjectSlotsEnum
	{
		/**
		 * The type of value that this expression would produce.
		 */
		EXPRESSION_TYPE
	}

	/**
	 * My slots of type {@linkplain Integer int}.
	 */
	public enum IntegerSlots
	implements IntegerSlotsEnum
	{
		/**
		 * The {@linkplain ParseNodeKind kind} of parse node, encoded as an
		 * {@code int}.
		 */
		@EnumField(describedBy=ParseNodeKind.class)
		KIND;
	}

	/**
	 * My hierarchy of kinds of parse nodes.
	 */
	public enum ParseNodeKind
	implements IntegerEnumSlotDescriptionEnum
	{
		/** The root parse node kind. */
		PARSE_NODE(null),

		/** The kind of a parse marker. */
		MARKER_NODE(PARSE_NODE),

		/** The abstract parent kind of all expression nodes. */
		EXPRESSION_NODE(PARSE_NODE),

		/**
		 * The kind of an {@linkplain AssignmentNodeDescriptor assignment node}.
		 */
		ASSIGNMENT_NODE(EXPRESSION_NODE),

		/** The kind of a {@linkplain BlockNodeDescriptor block node}. */
		BLOCK_NODE(EXPRESSION_NODE)
		{
			@Override
			A_Type mostGeneralInnerType ()
			{
				return FunctionTypeDescriptor.mostGeneralType();
			}
		},

		/** The kind of a {@linkplain LiteralNodeDescriptor literal node}. */
		LITERAL_NODE(EXPRESSION_NODE)
		{
			@Override
			A_Type mostGeneralInnerType ()
			{
				return Types.ANY.o();
			}
		},

		/**
		 * The kind of a {@linkplain ReferenceNodeDescriptor reference node}.
		 */
		REFERENCE_NODE(EXPRESSION_NODE)
		{
			@Override
			A_Type mostGeneralInnerType ()
			{
				return VariableTypeDescriptor.mostGeneralType();
			}
		},

		/** The kind of a {@linkplain SendNodeDescriptor send node}. */
		SEND_NODE(EXPRESSION_NODE),

		/** The kind of a {@linkplain SequenceNodeDescriptor sequence node}. */
		SEQUENCE_NODE(PARSE_NODE),

		/** The kind of a {@linkplain ListNodeDescriptor list node}. */
		LIST_NODE(EXPRESSION_NODE)
		{
			@Override
			A_Type mostGeneralInnerType ()
			{
				return TupleTypeDescriptor.mostGeneralType();
			}
		},

		/**
		 * The kind of a {@linkplain VariableUseNodeDescriptor variable use
		 * node}.
		 */
		VARIABLE_USE_NODE(EXPRESSION_NODE)
		{
			@Override
			A_Type mostGeneralInnerType ()
			{
				return Types.ANY.o();
			}
		},

		/**
		 * The kind of a {@linkplain DeclarationNodeDescriptor declaration
		 * node}.
		 */
		DECLARATION_NODE(PARSE_NODE),

		/** The kind of an argument declaration node. */
		ARGUMENT_NODE(DECLARATION_NODE),

		/** The kind of a label declaration node. */
		LABEL_NODE(DECLARATION_NODE),

		/** The kind of a local variable declaration node. */
		LOCAL_VARIABLE_NODE(DECLARATION_NODE),

		/** The kind of a local constant declaration node. */
		LOCAL_CONSTANT_NODE(DECLARATION_NODE),

		/** The kind of a module variable declaration node. */
		MODULE_VARIABLE_NODE(DECLARATION_NODE),

		/** The kind of a module constant declaration node. */
		MODULE_CONSTANT_NODE(DECLARATION_NODE),

		/** The kind of a primitive failure reason variable declaration. */
		PRIMITIVE_FAILURE_REASON_NODE(DECLARATION_NODE),

		/** The result of a macro substitution. */
		MACRO_SUBSTITUTION(PARSE_NODE);

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
		A_Type mostGeneralInnerType ()
		{
			return Types.TOP.o();
		}

		/**
		 * The depth of this object in the ParseNodeKind hierarchy.
		 */
		final int depth;

		/**
		 * The most general type for this kind of parse node.
		 */
		private final A_Type mostGeneralType =
			create(mostGeneralInnerType()).makeShared();

		/**
		 * Construct a new {@link ParseNodeKind}.
		 *
		 * @param parentKind
		 *        The kind of parse node of which this is the type.
		 */
		ParseNodeKind (final @Nullable ParseNodeKind parentKind)
		{
			this.parentKind = parentKind;
			if (parentKind == null)
			{
				depth = 0;
			}
			else
			{
				depth = parentKind.depth + 1;
			}
		}

		/**
		 * Create a {@linkplain ParseNodeTypeDescriptor parse node type} given
		 * the expression type (the type of object produced by the expression).
		 *
		 * @param expressionType
		 *        The type of object that will be produced by an expression
		 *        which is of the type being constructed.
		 * @return The new parse node type, whose kind is the receiver.
		 */
		final public AvailObject create (final A_Type expressionType)
		{
			A_Type boundedExpressionType = expressionType;
			final AvailObject type = mutable.create();
			boundedExpressionType = expressionType.typeIntersection(
				mostGeneralInnerType());
			boundedExpressionType.makeImmutable();
			type.setSlot(EXPRESSION_TYPE, boundedExpressionType);
			type.setSlot(KIND, ordinal());
			return type;
		}

		/**
		 * Answer a {@linkplain ParseNodeTypeDescriptor parse node type} whose
		 * kind is the receiver and whose expression type is {@linkplain
		 * TypeDescriptor.Types#TOP top}. This is the most general parse node
		 * type of that kind.
		 *
		 * @return The new parse node type, whose kind is the receiver and whose
		 *         expression type is {@linkplain TypeDescriptor.Types#TOP top}.
		 */
		public final A_Type mostGeneralType ()
		{
			return mostGeneralType;
		}

		/**
		 * Answer the {@link ParseNodeKind} that is the nearest common ancestor
		 * to both the receiver and the argument.
		 *
		 * @param another The other {@link ParseNodeKind}.
		 * @return The nearest common ancestor (a {@link ParseNodeKind}).
		 */
		final public ParseNodeKind commonAncestorWith (
			final ParseNodeKind another)
		{
			@NotNull ParseNodeKind a = this;
			@NotNull ParseNodeKind b = another;
			while (a != b)
			{
				final int diff = b.depth - a.depth;
				if (diff <= 0)
				{
					a = a.parentKind();
				}
				if (diff >= 0)
				{
					b = b.parentKind();
				}
			}
			return a;
		}

		/**
		 * An array where index (t1 * #values) + t2 indicates whether t1 is a
		 * subkind of t2.
		 */
		private static final boolean[] compatibility =
			new boolean [values().length * values().length];

		static
		{
			// Populate the entire compatibility matrix.
			for (final ParseNodeKind kind1 : values())
			{
				for (final ParseNodeKind kind2 : values())
				{
					final int index = kind1.ordinal() * values().length
						+ kind2.ordinal();
					final boolean compatible =
						kind1.commonAncestorWith(kind2) == kind2;
					compatibility[index] = compatible;
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
		final public boolean isSubkindOf (final ParseNodeKind purportedParent)
		{
			final int index =
				ordinal() * values().length + purportedParent.ordinal();
			return compatibility[index];
		}
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
	 * Return the {@linkplain ParseNodeKind parse node kind} that this parse
	 * node type implements.
	 *
	 * @return The {@linkplain ParseNodeKind kind} of parse node that the object
	 *         is.
	 */
	@Override @AvailMethod
	ParseNodeKind o_ParseNodeKind (final AvailObject object)
	{
		final int ordinal = object.slot(KIND);
		return ParseNodeKind.values()[ordinal];
	}

	/**
	 * {@linkplain ParseNodeTypeDescriptor parse nodes} must implement {@link
	* AbstractDescriptor#o_Hash(AvailObject) hash}.
	 */
	@Override @AvailMethod
	int o_Hash (final AvailObject object)
	{
		return object.slot(EXPRESSION_TYPE).hash()
			^ (object.slot(KIND) * multiplier);
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * {@linkplain ParseNodeTypeDescriptor parse node types} are equal when they are
	 * of the same kind and have the same expression type.
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
		return object.parseNodeKind() == aParseNodeType.parseNodeKind()
			&& object.slot(EXPRESSION_TYPE).equals(
				aParseNodeType.expressionType());
 	}

	@Override @AvailMethod
	boolean o_IsSubtypeOf (final AvailObject object, final A_Type aType)
	{
		return aType.isSupertypeOfParseNodeType(object);
	}

	@Override @AvailMethod
	boolean o_IsSupertypeOfParseNodeType (
		final AvailObject object,
		final AvailObject aParseNodeType)
	{
		final ParseNodeKind myKind = object.parseNodeKind();
		final ParseNodeKind otherKind = aParseNodeType.parseNodeKind();
		if (otherKind.isSubkindOf(myKind))
		{
			return aParseNodeType.slot(EXPRESSION_TYPE).isSubtypeOf(
				object.slot(EXPRESSION_TYPE));
		}
		return false;
	}

	@Override @AvailMethod
	A_Type o_TypeIntersection (
		final AvailObject object,
		final A_Type another)
	{
		if (object.isSubtypeOf(another))
		{
			return object;
		}
		if (another.isSubtypeOf(object))
		{
			return another;
		}
		return another.typeIntersectionOfParseNodeType(object);
	}

	@Override @AvailMethod
	A_Type o_TypeIntersectionOfParseNodeType (
		final AvailObject object,
		final A_Type aParseNodeType)
	{
		if (object.isSubtypeOf(aParseNodeType))
		{
			return object;
		}
		if (aParseNodeType.isSubtypeOf(object))
		{
			return aParseNodeType;
		}
		final ParseNodeKind myKind = object.parseNodeKind();
		final ParseNodeKind otherKind = aParseNodeType.parseNodeKind();
		final ParseNodeKind ancestor = myKind.commonAncestorWith(otherKind);
		if (ancestor == myKind || ancestor == otherKind)
		{
			// One kind is the ancestor of the other.  We can work with that.
			final A_Type innerIntersection =
				object.slot(EXPRESSION_TYPE).typeIntersection(
					aParseNodeType.expressionType());
			return (ancestor == myKind ? otherKind : myKind).create(
				innerIntersection);
		}
		// There may be a common ancestor, but it isn't one of the supplied
		// kinds.  Since the kinds form a tree, the intersection is impossible.
		return BottomTypeDescriptor.bottom();
	}

	@Override @AvailMethod
	A_Type o_TypeUnion (
		final AvailObject object,
		final A_Type another)
	{
		return another.typeUnionOfParseNodeType(object);
	}

	@Override @AvailMethod
	A_Type o_TypeUnionOfParseNodeType (
		final AvailObject object,
		final A_Type aParseNodeType)
	{
		if (object.isSubtypeOf(aParseNodeType))
		{
			return aParseNodeType;
		}
		if (aParseNodeType.isSubtypeOf(object))
		{
			return object;
		}
		final ParseNodeKind myKind = object.parseNodeKind();
		final ParseNodeKind otherKind = aParseNodeType.parseNodeKind();
		final ParseNodeKind ancestor = myKind.commonAncestorWith(otherKind);
		return ancestor.create(
			object.slot(EXPRESSION_TYPE).typeUnion(
				aParseNodeType.expressionType()));
	}

	@Override @AvailMethod
	boolean o_ParseNodeKindIsUnder (
		final AvailObject object,
		final ParseNodeKind expectedParseNodeKind)
	{
		return object.parseNodeKind().isSubkindOf(expectedParseNodeKind);
	}

	@Override
	SerializerOperation o_SerializerOperation (final AvailObject object)
	{
		return SerializerOperation.PARSE_NODE_TYPE;
	}

	@Override
	public void printObjectOnAvoidingIndent (
		final AvailObject object,
		final StringBuilder builder,
		final List<A_BasicObject> recursionList,
		final int indent)
	{
		final ParseNodeKind kind = object.parseNodeKind();
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
		builder.append("→");
		object.expressionType().printOnAvoidingIndent(
			builder,
			recursionList,
			indent + 1);
	}

	/**
	 * Construct a new {@link ParseNodeTypeDescriptor}.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 */
	public ParseNodeTypeDescriptor (final Mutability mutability)
	{
		super(mutability);
	}

	/** The mutable {@link ParseNodeTypeDescriptor}. */
	static final ParseNodeTypeDescriptor mutable =
		new ParseNodeTypeDescriptor(Mutability.MUTABLE);

	@Override
	ParseNodeTypeDescriptor mutable ()
	{
		return mutable;
	}

	/** The shared {@link ParseNodeTypeDescriptor}. */
	private static final ParseNodeTypeDescriptor shared =
		new ParseNodeTypeDescriptor(Mutability.SHARED);

	@Override
	ParseNodeTypeDescriptor immutable ()
	{
		// There is no immutable descriptor.
		return shared;
	}

	@Override
	ParseNodeTypeDescriptor shared ()
	{
		return shared;
	}

	/**
	 * Does the specified {@linkplain AvailObject#flattenStatementsInto(List)
	 * flat} {@linkplain List list} of {@linkplain ParseNodeDescriptor parse
	 * nodes} contain only statements?
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
			final A_Type kind = statement.kind();
			assert !kind.parseNodeKindIsUnder(SEQUENCE_NODE);
			final boolean valid;
			if (i + 1 < statementCount)
			{
				valid =
					(kind.parseNodeKindIsUnder(ASSIGNMENT_NODE)
						|| kind.parseNodeKindIsUnder(DECLARATION_NODE)
						|| kind.parseNodeKindIsUnder(LABEL_NODE)
						|| kind.parseNodeKindIsUnder(SEND_NODE))
					&& kind.expressionType().equals(TOP.o());
			}
			else
			{
				valid = kind.expressionType().isSubtypeOf(resultType);
			}
			if (!valid)
			{
				return false;
			}
		}
		return true;
	}
}
