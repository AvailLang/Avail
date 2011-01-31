/**
 * com.avail.newcompiler/AssignmentNodeDescriptor.java
 * Copyright (c) 2010, Mark van Gulik.
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

package com.avail.compiler.node;

import java.util.*;
import com.avail.compiler.AvailCodeGenerator;
import com.avail.descriptor.*;
import com.avail.descriptor.TypeDescriptor.Types;
import com.avail.interpreter.levelTwo.L2Interpreter;
import com.avail.utility.Transformer1;

/**
 * My instances represent {@link ParseNodeDescriptor parse nodes} which will
 * generate tuples directly at runtime.
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 */
public class TupleNodeDescriptor extends ParseNodeDescriptor
{
	/**
	 * My slots of type {@link AvailObject}.
	 *
	 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
	 */
	public enum ObjectSlots
	{
		/**
		 * The {@link TupleDescriptor tuple} of {@link ParseNodeDescriptor
		 * parse nodes} that produce the values that will be aggregated into a
		 * tuple at runtime.
		 */
		EXPRESSIONS_TUPLE,

		/**
		 * The static type of the tuple that will be generated.
		 */
		TUPLE_TYPE
	}


	/**
	 * Setter for field expressionsTuple.
	 */
	@Override
	public void o_ExpressionsTuple (
		final AvailObject object,
		final AvailObject expressionsTuple)
	{
		object.objectSlotPut(ObjectSlots.EXPRESSIONS_TUPLE, expressionsTuple);
	}

	/**
	 * Getter for field expressionsTuple.
	 */
	@Override
	public AvailObject o_ExpressionsTuple (
		final AvailObject object)
	{
		return object.objectSlot(ObjectSlots.EXPRESSIONS_TUPLE);
	}

	/**
	 * Setter for field tupleType.
	 */
	@Override
	public void o_TupleType (
		final AvailObject object,
		final AvailObject tupleType)
	{
		object.objectSlotPut(ObjectSlots.TUPLE_TYPE, tupleType);
	}

	/**
	 * Getter for field tupleType.
	 */
	@Override
	public AvailObject o_TupleType (
		final AvailObject object)
	{
		return object.objectSlot(ObjectSlots.TUPLE_TYPE);
	}


	@Override
	public AvailObject o_ExpressionType (final AvailObject object)
	{
		AvailObject tupleType = object.tupleType();
		if (tupleType.equalsVoid())
		{
			final AvailObject expressionsTuple = object.expressionsTuple();
			final List<AvailObject> types = new ArrayList<AvailObject>(
				expressionsTuple.tupleSize());
			for (final AvailObject expression : expressionsTuple)
			{
				AvailObject expressionType = expression.expressionType();
				if (expressionType.equals(Types.TERMINATES.o()))
				{
					return Types.TERMINATES.o();
				}
				types.add(expressionType);
			}
			final AvailObject sizes = IntegerRangeTypeDescriptor.singleInteger(
				IntegerDescriptor.fromInt(types.size()));
			tupleType = TupleTypeDescriptor.tupleTypeForSizesTypesDefaultType(
				sizes,
				TupleDescriptor.fromList(types),
				Types.TERMINATES.o());
			tupleType.makeImmutable();
			object.tupleType(tupleType);
		}
		return tupleType;
	}

	@Override
	public AvailObject o_Type (final AvailObject object)
	{
		//  Answer the object's type.

		return Types.TUPLE_NODE.o();
	}

	@Override
	public AvailObject o_ExactType (final AvailObject object)
	{
		//  Answer the object's type.

		return Types.TUPLE_NODE.o();
	}

	@Override
	public int o_Hash (final AvailObject object)
	{
		return object.expressionsTuple().hash() ^ 0xC143E977;
	}

	@Override
	public boolean o_Equals (
		final AvailObject object,
		final AvailObject another)
	{
		return object.type().equals(another.type())
			&& object.expressionsTuple().equals(another.expressionsTuple());
	}

	@Override
	public void o_EmitValueOn (
		final AvailObject object,
		final AvailCodeGenerator codeGenerator)
	{
		final AvailObject childNodes = object.expressionsTuple();
		for (final AvailObject expr : childNodes)
		{
			expr.emitValueOn(codeGenerator);
		}
		codeGenerator.emitMakeTuple(childNodes.tupleSize());
	}

	@Override
	public void o_ChildrenMap (
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
		object.expressionsTuple(expressions);
	}


	@Override
	public void o_ValidateLocally (
		final AvailObject object,
		final AvailObject parent,
		final List<AvailObject> outerBlocks,
		final L2Interpreter anAvailInterpreter)
	{
		// Do nothing.
	}


	/**
	 * Create a new {@code TupleNodeDescriptor tuple node} with one more parse
	 * node added to the end of the tuple.
	 *
	 * @param object
	 *        The tuple node to extend.
	 * @param newParseNode
	 *        The parse node to append.
	 * @return
	 *         A new {@code TupleNodeDescriptor tuple node} with the parse node
	 *         appended.
	 */
	@Override
	public AvailObject o_CopyWith (
		final AvailObject object,
		final AvailObject newParseNode)
	{
		final List<AvailObject> newNodes = new ArrayList<AvailObject>(
			object.expressionsTuple().tupleSize() + 1);
		for (final AvailObject expression : object.expressionsTuple())
		{
			newNodes.add(expression);
		}
		newNodes.add(newParseNode);
		final AvailObject newTupleNode = TupleNodeDescriptor.mutable().create();
		newTupleNode.expressionsTuple(TupleDescriptor.fromList(newNodes));
		newTupleNode.tupleType(VoidDescriptor.voidObject());
		return newTupleNode;
	}


	/**
	 * Create a new {@link TupleNodeDescriptor tuple node} from the given {@link
	 * TupleDescriptor tuple} of {@link ParseNodeDescriptor expressions}.
	 *
	 * @param arguments
	 *        The expressions to assemble into a {@link TupleNodeDescriptor
	 *        tuple node}.
	 * @return The resulting tuple node.
	 */
	public static AvailObject newExpressions (final AvailObject arguments)
	{
		final AvailObject instance = mutable().create();
		instance.expressionsTuple(arguments);
		instance.tupleType(VoidDescriptor.voidObject());
		return instance;
	}

	/**
	 * Construct a new {@link TupleNodeDescriptor}.
	 *
	 * @param isMutable Whether my {@linkplain AvailObject instances} can
	 *                  change.
	 */
	public TupleNodeDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}

	/**
	 * The mutable {@link TupleNodeDescriptor}.
	 */
	private final static TupleNodeDescriptor mutable =
		new TupleNodeDescriptor(true);

	/**
	 * Answer the mutable {@link TupleNodeDescriptor}.
	 *
	 * @return The mutable {@link TupleNodeDescriptor}.
	 */
	public static TupleNodeDescriptor mutable ()
	{
		return mutable;
	}

	/**
	 * The immutable {@link TupleNodeDescriptor}.
	 */
	private final static TupleNodeDescriptor immutable =
		new TupleNodeDescriptor(false);

	/**
	 * Answer the immutable {@link TupleNodeDescriptor}.
	 *
	 * @return The immutable {@link TupleNodeDescriptor}.
	 */
	public static TupleNodeDescriptor immutable ()
	{
		return immutable;
	}
}
