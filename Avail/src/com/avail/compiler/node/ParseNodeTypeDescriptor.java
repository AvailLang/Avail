/**
 * com.avail.descriptor.parser/ParseNodeTypeDescriptor.java
 * Copyright (c) 2011, Mark van Gulik.
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

package com.avail.compiler.node;

import static com.avail.descriptor.AvailObject.Multiplier;
import static com.avail.descriptor.TypeDescriptor.Types.TYPE;
import java.util.List;
import com.avail.annotations.*;
import com.avail.descriptor.*;

/**
 * Define the structure and behavior of parse node types.  The parse node types
 * are all parameterized by expression type, but they also have a relationship
 * to each other based on a fiat hierarchy.
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 */
public class ParseNodeTypeDescriptor extends TypeDescriptor
{
	/**
	 * My slots of type {@link AvailObject}.
	 *
	 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
	 */
	public enum ObjectSlots
	{
		/**
		 * The type of value that this expression would produce.
		 */
		EXPRESSION_TYPE
	}

	/**
	 * My slots of type {@link Integer int}.
	 *
	 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
	 */
	public enum IntegerSlots
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
	 *
	 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
	 */
	public enum ParseNodeKind
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
		BLOCK_NODE(EXPRESSION_NODE),

		/** The kind of a {@linkplain LiteralNodeDescriptor literal node}. */
		LITERAL_NODE(EXPRESSION_NODE),

		/**
		 * The kind of a {@linkplain ReferenceNodeDescriptor reference node}.
		 */
		REFERENCE_NODE(EXPRESSION_NODE),

		/** The kind of a {@linkplain SendNodeDescriptor send node}. */
		SEND_NODE(EXPRESSION_NODE),

		/** The kind of a {@linkplain SequenceNodeDescriptor sequence node}. */
		SEQUENCE_NODE(EXPRESSION_NODE),

		/**
		 * The kind of a {@linkplain SuperCastNodeDescriptor super cast node}.
		 */
		SUPER_CAST_NODE(EXPRESSION_NODE),

		/** The kind of a {@linkplain TupleNodeDescriptor tuple node}. */
		TUPLE_NODE(EXPRESSION_NODE),

		/**
		 * The kind of a {@linkplain VariableUseNodeDescriptor variable use
		 * node}.
		 */
		VARIABLE_USE_NODE(EXPRESSION_NODE),

		/**
		 * The kind of a {@linkplain DeclarationNodeDescriptor declaration
		 * node}.
		 */
		DECLARATION_NODE(EXPRESSION_NODE),

		/** The kind of an argument node. */
		ARGUMENT_NODE(DECLARATION_NODE),

		/** The kind of a label node. */
		LABEL_NODE(DECLARATION_NODE),

		/** The kind of a local variable node. */
		LOCAL_VARIABLE_NODE(DECLARATION_NODE),

		/** The kind of a local constant node. */
		LOCAL_CONSTANT_NODE(DECLARATION_NODE),

		/** The kind of a module variable node. */
		MODULE_VARIABLE_NODE(DECLARATION_NODE),

		/** The kind of a module constant node. */
		MODULE_CONSTANT_NODE(DECLARATION_NODE);

		/**
		 * The kind of parse node that this kind is a child of.
		 */
		final ParseNodeKind parentKind;

		/**
		 * Answer the kind of parse node of which this object is the type.
		 *
		 * @return My parent parse node kind.
		 */
		public final ParseNodeKind parentKind ()
		{
			return parentKind;
		}

		/**
		 * The depth of this object in the ParseNodeKind hierarchy.
		 */
		final int depth;

		/**
		 * The most general type for this kind of parse node.
		 */
		AvailObject mostGeneralType;

		/**
		 * Construct a new {@link ParseNodeKind}.
		 *
		 * @param parentKind The kind of parse node of which this is the type.
		 */
		ParseNodeKind(final ParseNodeKind parentKind)
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
		 * Create a {@link ParseNodeTypeDescriptor parse node type} given the
		 * expression type (the type of object produced by the expression).
		 *
		 * @param expressionType
		 *            The type of object that will be produced by an expression
		 *            which is of the type being constructed.
		 * @return The new parse node type, whose kind is the receiver.
		 */
		final public @NotNull AvailObject create (
			final @NotNull AvailObject expressionType)
		{
			expressionType.makeImmutable();
			final AvailObject type = mutable().create();
			type.objectSlotPut(ObjectSlots.EXPRESSION_TYPE, expressionType);
			type.integerSlotPut(IntegerSlots.KIND, ordinal());
			return type;
		}

		/**
		 * Populate any necessary references to {@link AvailObject}s held by
		 * this {@link ParseNodeKind}.
		 */
		void createWellKnownObjects ()
		{
			mostGeneralType = create(Types.TOP.o());
		}

		/**
		 * Release all references to {@link AvailObject}s held by this {@link
		 * ParseNodeKind}.
		 */
		void clearWellKnownObjects ()
		{
			mostGeneralType = null;
		}

		/**
		 * Answer a {@link ParseNodeTypeDescriptor parse node type} whose kind
		 * is the receiver and whose expression type is {@linkplain
		 * TypeDescriptor.Types#TOP top}.  This is the most general parse node
		 * type of that kind.
		 *
		 * @return The new parse node type, whose kind is the receiver and whose
		 *         expression type is {@linkplain TypeDescriptor.Types#TOP top}.
		 */
		final public @NotNull AvailObject mostGeneralType ()
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
		final public @NotNull ParseNodeKind commonAncestorWith (
			final @NotNull ParseNodeKind another)
		{
			ParseNodeKind a = this;
			ParseNodeKind b = another;
			while (a != b)
			{
				final int diff = b.depth - a.depth;
				if (diff <= 0)
				{
					a = a.parentKind;
				}
				if (diff >= 0)
				{
					b = b.parentKind;
				}
			}
			return a;
		}
	}

	/**
	 * Return the type of object that would be produced by a parse node of this
	 * type.
	 *
	 * @return The {@link TypeDescriptor type} of the {@link AvailObject} that
	 *         will be produced by a parse node of this type.
	 */
	@Override
	public @NotNull AvailObject o_ExpressionType (
		final @NotNull AvailObject object)
	{
		return object.objectSlot(ObjectSlots.EXPRESSION_TYPE);
	}

	/**
	 * Return the {@linkplain ParseNodeKind parse node kind} that this parse
	 * node type implements.
	 *
	 * @return The {@link ParseNodeKind kind} of parse node that the object is.
	 */
	@Override
	public @NotNull ParseNodeKind o_ParseNodeKind (
		final @NotNull AvailObject object)
	{
		final int ordinal = object.integerSlot(IntegerSlots.KIND);
		return ParseNodeKind.values()[ordinal];
	}

	/**
	 * {@link ParseNodeTypeDescriptor parse nodes} must implement {@link
	 * AbstractDescriptor#o_Hash(AvailObject) hash}.
	 */
	@Override
	public int o_Hash (final @NotNull AvailObject object)
	{
		return object.objectSlot(ObjectSlots.EXPRESSION_TYPE).hash()
			^ (object.integerSlot(IntegerSlots.KIND) * Multiplier);
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * {@link ParseNodeTypeDescriptor parse node types} are equal when they are
	 * of the same kind and have the same expression type.
	 * </p>
	 */
	@Override
	public boolean o_Equals (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
	{
		return another.equalsParseNodeType(object);
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * {@link ParseNodeTypeDescriptor parse node types} are equal when they are
	 * of the same kind and have the same expression type.
	 * </p>
	 */
	@Override
	public boolean o_EqualsParseNodeType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aParseNodeType)
	{
		return object.integerSlot(IntegerSlots.KIND)
				== aParseNodeType.integerSlot(IntegerSlots.KIND)
			&& object.expressionType().equals(
				aParseNodeType.expressionType());
 	}

	@Override
	public @NotNull AvailObject o_Kind (
		final @NotNull AvailObject object)
	{
		return TYPE.o();
	}

	@Override
	public boolean o_IsSubtypeOf (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aType)
	{
		return aType.isSupertypeOfParseNodeType(object);
	}

	@Override
	public @NotNull boolean o_IsSupertypeOfParseNodeType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aParseNodeType)
	{
		final ParseNodeKind myKind =
			ParseNodeKind.values()[object.integerSlot(IntegerSlots.KIND)];
		final ParseNodeKind otherKind =
			ParseNodeKind.values()[aParseNodeType.integerSlot(IntegerSlots.KIND)];
		final ParseNodeKind ancestor = myKind.commonAncestorWith(otherKind);
		if (ancestor == myKind)
		{
			return aParseNodeType.expressionType().isSubtypeOf(
				object.expressionType());
		}
		return false;
	}

	@Override
	public @NotNull AvailObject o_TypeIntersection (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
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

	@Override
	public @NotNull AvailObject o_TypeIntersectionOfParseNodeType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aParseNodeType)
	{
		if (object.isSubtypeOf(aParseNodeType))
		{
			return object;
		}
		if (aParseNodeType.isSubtypeOf(object))
		{
			return aParseNodeType;
		}
		final ParseNodeKind myKind = ParseNodeKind
			.values()[object.integerSlot(IntegerSlots.KIND)];
		final ParseNodeKind otherKind = ParseNodeKind
			.values()[aParseNodeType.integerSlot(IntegerSlots.KIND)];
		final ParseNodeKind ancestor = myKind.commonAncestorWith(otherKind);
		if (ancestor == myKind || ancestor == otherKind)
		{
			// One kind is the ancestor of the other.  We can work with that.
			final AvailObject innerIntersection =
				object.expressionType().typeIntersection(
					aParseNodeType.expressionType());
			return (ancestor == myKind ? otherKind : myKind).create(
				innerIntersection);
		}
		// There may be a common ancestor, but it isn't one of the supplied
		// kinds.  Since the kinds form a tree, the intersection is impossible.
		return BottomTypeDescriptor.bottom();
	}

	@Override
	public @NotNull AvailObject o_TypeUnion (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
	{
		return another.typeUnionOfParseNodeType(object);
	}

	@Override
	public @NotNull AvailObject o_TypeUnionOfParseNodeType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aParseNodeType)
	{
		if (object.isSubtypeOf(aParseNodeType))
		{
			return aParseNodeType;
		}
		if (aParseNodeType.isSubtypeOf(object))
		{
			return object;
		}
		final ParseNodeKind myKind = ParseNodeKind
			.values()[object.integerSlot(IntegerSlots.KIND)];
		final ParseNodeKind otherKind = ParseNodeKind
			.values()[aParseNodeType.integerSlot(IntegerSlots.KIND)];
		final ParseNodeKind ancestor = myKind.commonAncestorWith(otherKind);
		return ancestor.create(
			object.expressionType().typeUnion(aParseNodeType.expressionType()));
	}

	@Override
	public boolean o_ParseNodeKindIsUnder (
		final @NotNull AvailObject object,
		final @NotNull ParseNodeKind expectedParseNodeKind)
	{
		final int ordinal = object.integerSlot(IntegerSlots.KIND);
		final ParseNodeKind myKind = ParseNodeKind.values()[ordinal];
		final ParseNodeKind commonAncestor =
			myKind.commonAncestorWith(expectedParseNodeKind);
		return commonAncestor == expectedParseNodeKind;
	}

	@Override
	public void printObjectOnAvoidingIndent (
		final @NotNull AvailObject object,
		final @NotNull StringBuilder builder,
		final @NotNull List<AvailObject> recursionList,
		final int indent)
	{
		final int kindOrdinal = object.integerSlot(IntegerSlots.KIND);
		final ParseNodeKind kind = ParseNodeKind.values()[kindOrdinal];
		builder.append(kind.name());
		builder.append("(");
		object.expressionType().printOnAvoidingIndent(
			builder,
			recursionList,
			indent + 1);
		builder.append(")");
	}

	/**
	 * Populate any necessary references to {@link AvailObject}s held by this
	 * class.
	 */
	public static void createWellKnownObjects ()
	{
		for (final ParseNodeKind kind : ParseNodeKind.values())
		{
			kind.createWellKnownObjects();
		}
	}

	/**
	 * Release all references to {@link AvailObject}s held by this class.
	 */
	public static void clearWellKnownObjects ()
	{
		for (final ParseNodeKind kind : ParseNodeKind.values())
		{
			kind.clearWellKnownObjects();
		}
	}

	/**
	 * Construct a new {@link ParseNodeTypeDescriptor}.
	 *
	 * @param isMutable Whether my {@linkplain AvailObject instances} can
	 *                  change.
	 */
	public ParseNodeTypeDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}

	/**
	 * The mutable {@link ParseNodeTypeDescriptor}.
	 */
	private final static ParseNodeTypeDescriptor mutable =
		new ParseNodeTypeDescriptor(true);

	/**
	 * Answer the mutable {@link ParseNodeTypeDescriptor}.
	 *
	 * @return The mutable {@link ParseNodeTypeDescriptor}.
	 */
	public static ParseNodeTypeDescriptor mutable ()
	{
		return mutable;
	}

	/**
	 * The immutable {@link ParseNodeTypeDescriptor}.
	 */
	private final static ParseNodeTypeDescriptor immutable =
		new ParseNodeTypeDescriptor(false);

	/**
	 * Answer the immutable {@link ParseNodeTypeDescriptor}.
	 *
	 * @return The immutable {@link ParseNodeTypeDescriptor}.
	 */
	public static ParseNodeTypeDescriptor immutable ()
	{
		return immutable;
	}

}
