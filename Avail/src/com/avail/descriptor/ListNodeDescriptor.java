/**
 * ListNodeDescriptor.java
 * Copyright © 1993-2012, Mark van Gulik and Todd L Smith.
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

import static com.avail.descriptor.ParseNodeTypeDescriptor.ParseNodeKind.*;
import static com.avail.descriptor.ListNodeDescriptor.ObjectSlots.*;
import java.util.*;
import com.avail.annotations.*;
import com.avail.compiler.AvailCodeGenerator;
import com.avail.descriptor.ParseNodeTypeDescriptor.ParseNodeKind;
import com.avail.utility.*;

/**
 * My instances represent {@linkplain ParseNodeDescriptor parse nodes} which will
 * generate tuples directly at runtime.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public class ListNodeDescriptor extends ParseNodeDescriptor
{
	/**
	 * My slots of type {@link AvailObject}.
	 *
	 * @author Mark van Gulik &lt;mark@availlang.org&gt;
	 */
	public enum ObjectSlots implements ObjectSlotsEnum
	{
		/**
		 * The {@linkplain TupleDescriptor tuple} of {@linkplain ParseNodeDescriptor
		 * parse nodes} that produce the values that will be aggregated into a
		 * tuple at runtime.
		 */
		EXPRESSIONS_TUPLE,

		/**
		 * The static type of the tuple that will be generated.
		 */
		TUPLE_TYPE
	}

	@Override boolean allowsImmutableToMutableReferenceInField (
		final AbstractSlotsEnum e)
	{
		return e == TUPLE_TYPE;
	}

	/**
	 * Getter for field expressionsTuple.
	 */
	@Override @AvailMethod
	AvailObject o_ExpressionsTuple (final AvailObject object)
	{
		return object.slot(ObjectSlots.EXPRESSIONS_TUPLE);
	}

	@Override @AvailMethod
	AvailObject o_ExpressionType (final AvailObject object)
	{
		AvailObject tupleType = object.slot(TUPLE_TYPE);
		if (tupleType.equalsNull())
		{
			final AvailObject expressionsTuple = object.expressionsTuple();
			final List<AvailObject> types = new ArrayList<AvailObject>(
				expressionsTuple.tupleSize());
			for (final AvailObject expression : expressionsTuple)
			{
				final AvailObject expressionType = expression.expressionType();
				if (expressionType.equals(BottomTypeDescriptor.bottom()))
				{
					return BottomTypeDescriptor.bottom();
				}
				types.add(expressionType);
			}
			final AvailObject sizes = IntegerRangeTypeDescriptor.singleInt(
				types.size());
			tupleType = TupleTypeDescriptor.tupleTypeForSizesTypesDefaultType(
				sizes,
				TupleDescriptor.fromList(types),
				BottomTypeDescriptor.bottom());
			tupleType.makeImmutable();
			object.setSlot(TUPLE_TYPE, tupleType);
		}
		return tupleType;
	}

	@Override
	void printObjectOnAvoidingIndent (
		final AvailObject object,
		final StringBuilder builder,
		final List<AvailObject> recursionList,
		final int indent)
	{
		builder.append("List(");
		boolean first = true;
		for (final AvailObject element : object.expressionsTuple())
		{
			if (!first)
			{
				builder.append(", ");
			}
			builder.append(element);
			first = false;
		}
		builder.append(")");
	}

	@Override @AvailMethod
	int o_Hash (final AvailObject object)
	{
		return object.expressionsTuple().hash() ^ 0xC143E977;
	}

	@Override @AvailMethod
	boolean o_Equals (
		final AvailObject object,
		final AvailObject another)
	{
		return object.kind().equals(another.kind())
			&& object.expressionsTuple().equals(another.expressionsTuple());
	}

	@Override @AvailMethod
	void o_EmitValueOn (
		final AvailObject object,
		final AvailCodeGenerator codeGenerator)
	{
		final AvailObject childNodes = object.expressionsTuple();
		for (final AvailObject expr : childNodes)
		{
			expr.emitValueOn(codeGenerator);
		}
		codeGenerator.emitMakeList(childNodes.tupleSize());
	}

	@Override @AvailMethod
	void o_ChildrenMap (
		final AvailObject object,
		final Transformer1<AvailObject, AvailObject> aBlock)
	{
		AvailObject expressions = object.expressionsTuple();
		for (int i = 1; i <= expressions.tupleSize(); i++)
		{
			expressions = expressions.tupleAtPuttingCanDestroy(
				i,
				aBlock.value(expressions.tupleAt(i)),
				true);
		}
		object.setSlot(EXPRESSIONS_TUPLE, expressions);
	}


	@Override @AvailMethod
	void o_ChildrenDo (
		final AvailObject object,
		final Continuation1<AvailObject> aBlock)
	{
		for (final AvailObject expression : object.expressionsTuple())
		{
			aBlock.value(expression);
		}
	}


	@Override @AvailMethod
	void o_ValidateLocally (
		final AvailObject object,
		final @Nullable AvailObject parent)
	{
		// Do nothing.
	}

	/**
	 * Create a new {@code ListNodeDescriptor list node} with one more parse
	 * node added to the end of the list.
	 *
	 * @param object
	 *        The list node to extend.
	 * @param newParseNode
	 *        The parse node to append.
	 * @return
	 *         A new {@code ListNodeDescriptor list node} with the parse node
	 *         appended.
	 */
	@Override @AvailMethod
	AvailObject o_CopyWith (
		final AvailObject object,
		final AvailObject newParseNode)
	{
		final AvailObject oldTuple = object.slot(EXPRESSIONS_TUPLE);
		final AvailObject newTuple = oldTuple.appendCanDestroy(
			newParseNode,
			true);
		return ListNodeDescriptor.newExpressions(newTuple);
	}

	@Override
	ParseNodeKind o_ParseNodeKind (
		final AvailObject object)
	{
		return LIST_NODE;
	}


	/**
	 * The empty {@link ListNodeDescriptor list node}.
	 */
	private static AvailObject empty;


	/**
	 * Answer the empty {@link ListNodeDescriptor list node}.
	 *
	 * @return The empty list node.
	 */
	public static AvailObject empty ()
	{
		return empty;
	}

	/**
	 * Create the empty {@link ListNodeDescriptor list node}.
	 */
	static void createWellKnownObjects ()
	{
		empty = newExpressions(TupleDescriptor.empty());
		empty.makeImmutable();
	}

	/**
	 * Discard the empty {@link ListNodeDescriptor list node}.
	 */
	static void clearWellKnownObjects ()
	{
		empty = null;
	}



	/**
	 * Create a new {@linkplain ListNodeDescriptor list node} from the given
	 * {@linkplain TupleDescriptor tuple} of {@linkplain ParseNodeDescriptor
	 * expressions}.
	 *
	 * @param expressions
	 *        The expressions to assemble into a {@linkplain ListNodeDescriptor
	 *        list node}.
	 * @return The resulting list node.
	 */
	public static AvailObject newExpressions (final AvailObject expressions)
	{
		final AvailObject instance = mutable().create();
		instance.setSlot(EXPRESSIONS_TUPLE, expressions);
		instance.setSlot(TUPLE_TYPE, NullDescriptor.nullObject());
		return instance;
	}

	/**
	 * Construct a new {@link ListNodeDescriptor}.
	 *
	 * @param isMutable Whether my {@linkplain AvailObject instances} can
	 *                  change.
	 */
	public ListNodeDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}

	/**
	 * The mutable {@link ListNodeDescriptor}.
	 */
	private static final ListNodeDescriptor mutable =
		new ListNodeDescriptor(true);

	/**
	 * Answer the mutable {@link ListNodeDescriptor}.
	 *
	 * @return The mutable {@link ListNodeDescriptor}.
	 */
	public static ListNodeDescriptor mutable ()
	{
		return mutable;
	}

	/**
	 * The immutable {@link ListNodeDescriptor}.
	 */
	private static final ListNodeDescriptor immutable =
		new ListNodeDescriptor(false);

	/**
	 * Answer the immutable {@link ListNodeDescriptor}.
	 *
	 * @return The immutable {@link ListNodeDescriptor}.
	 */
	public static ListNodeDescriptor immutable ()
	{
		return immutable;
	}
}
