/**
 * ParseNodeDescriptor.java
 * Copyright © 1993-2012, Mark van Gulik and Todd L Smith.
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

import static com.avail.descriptor.ParseNodeTypeDescriptor.ParseNodeKind.*;
import java.util.List;
import com.avail.annotations.*;
import com.avail.compiler.AvailCodeGenerator;
import com.avail.descriptor.ParseNodeTypeDescriptor.*;
import com.avail.utility.*;

/**
 * I'm used to implement the abstract notion of parse nodes.  All concrete parse
 * nodes are below me in the hierarchy.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public abstract class ParseNodeDescriptor extends Descriptor
{

	/**
	 * Construct a new {@link ParseNodeDescriptor}.
	 *
	 * @param isMutable Whether the descriptor being constructed represents
	 *                  mutable objects or not.
	 */
	protected ParseNodeDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}

	/**
	 * Return the parse node's expression type, which is the type of object that
	 * will be produced by this parse node.
	 *
	 * @return The {@linkplain TypeDescriptor type} of the {@link AvailObject}
	 *         that will be produced by this parse node.
	 */
	@Override @AvailMethod
	abstract AvailObject o_ExpressionType (
		final AvailObject object);

	@Override @AvailMethod
	final AvailObject o_Kind (
		final AvailObject object)
	{
		return object.parseNodeKind().create(object.expressionType());
	}

	@Override @AvailMethod
	boolean o_IsInstanceOfKind (
		final AvailObject object,
		final AvailObject aType)
	{
		if (PARSE_NODE.mostGeneralType().isSubtypeOf(aType))
		{
			return true;
		}
		if (aType.isSubtypeOf(PARSE_NODE.mostGeneralType()))
		{
			return object.parseNodeKind().isSubkindOf(aType.parseNodeKind())
				&& object.expressionType().isSubtypeOf(aType.expressionType());
		}
		return false;
	}

	/**
	* The {@link #o_ApparentSendName(AvailObject) apparentSendName} of
	 * something that isn't a {@linkplain SendNodeDescriptor send node} or
	 * {@linkplain MacroSubstitutionNodeDescriptor macro substitution node} is
	 * always the {@link NullDescriptor#nullObject() void} object.
	 */
	@Override @AvailMethod
	AvailObject o_ApparentSendName (
		final AvailObject object)
	{
		return NullDescriptor.nullObject();
	}

	/**
	 * {@linkplain ParseNodeDescriptor parse nodes} must implement {@link
	* AbstractDescriptor#o_Hash(AvailObject) hash}.
	 */
	@Override @AvailMethod
	abstract int o_Hash (final AvailObject object);

	/**
	 * {@linkplain ParseNodeDescriptor parse nodes} must implement {@link
	* AbstractDescriptor#o_Equals(AvailObject, AvailObject) equals}.
	 */
	@Override @AvailMethod
	abstract boolean o_Equals (
		final AvailObject object,
		final AvailObject another);

	/**
	 * Emit the effect of this node.  By default that means to emit the value of
	 * the node, then to pop the unwanted value from the stack.
	 *
	 * @param object The parse node.
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
	 * Emit the value of this node.  That means emit a sequence of instructions
	 * that will cause this node's value to end up on the stack.
	 *
	 * @param object The parse node.
	 * @param codeGenerator Where to emit the code.
	 */
	@Override @AvailMethod
	abstract void o_EmitValueOn (
		final AvailObject object,
		final AvailCodeGenerator codeGenerator);


	/**
	 * A special enumeration used to visit all object slots for copying.
	 */
	enum FakeObjectSlots implements ObjectSlotsEnum
	{
		/**
		 * An indexed object slot that makes it easy to visit all object slots.
		 */
		ALL_OBJECT_SLOTS_
	}

	/**
	 * A special enumeration used to visit all integer slots for copying.
	 */
	enum FakeIntegerSlots implements IntegerSlotsEnum
	{
		/**
		 * An indexed integer slot that makes it easy to visit all integer
		 * slots.
		 */
		ALL_INTEGER_SLOTS_
	}

	/**
	 * If the receiver is immutable, make an equivalent mutable copy of that
	 * parse node.  Otherwise, answer the receiver itself.
	 *
	 * @param object The {@linkplain ParseNodeDescriptor parse node} of which to
	 *               create a mutable copy.
	 * @return A mutable {@linkplain ParseNodeDescriptor parse node} equivalent
	 *         to the passed parse node, possibly the same object.
	 */
	@Override @AvailMethod
	AvailObject o_CopyMutableParseNode (
		final AvailObject object)
	{
		if (isMutable())
		{
			return object;
		}
		final int objectCount = object.objectSlotsCount();
		final int integerCount = object.integerSlotsCount();

		final short descriptorId = (short)(object.descriptorId() & ~1);
		final AbstractDescriptor mutableDescriptor =
			allDescriptors.get(descriptorId);
		assert mutableDescriptor.getClass() == object.descriptor().getClass();

		final AvailObject copy =
			AvailObject.newObjectIndexedIntegerIndexedDescriptor(
				objectCount - numberOfFixedObjectSlots,
				integerCount - numberOfFixedIntegerSlots,
				mutableDescriptor);
		for (int i = 1; i <= objectCount; i++)
		{
			final AvailObject slotValue = object.slot(
				FakeObjectSlots.ALL_OBJECT_SLOTS_,
				i);
			// Potentially share the object.
			slotValue.makeImmutable();
			copy.setSlot(
				FakeObjectSlots.ALL_OBJECT_SLOTS_,
				i,
				slotValue);
		}
		for (int i = 1; i <= integerCount; i++)
		{
			copy.setSlot(
				FakeIntegerSlots.ALL_INTEGER_SLOTS_,
				i,
				object.slot(FakeIntegerSlots.ALL_INTEGER_SLOTS_, i));
		}
		return copy;
	}

	/**
	 * Visit and transform the direct descendants of this parse node.  Map this
	 * {@linkplain ParseNodeDescriptor parse node}'s children through the
	 * (destructive) transformation specified by aBlock, assigning them back
	 * into my slots.
	 *
	 * @param object The {@linkplain ParseNodeDescriptor parse node} to
	 *               transform.
	 * @param aBlock The {@linkplain Transformer1 transformation} through which
	 *               to map this parse node's children.
	 */
	@Override @AvailMethod
	abstract void o_ChildrenMap (
		final AvailObject object,
		final Transformer1<AvailObject, AvailObject> aBlock);

	/**
	 * Visit every node constituting this parse tree, invoking the passed {@link
	 * Continuation1} with each.
	 *
	 * @param object
	 *            The {@linkplain ParseNodeDescriptor parse node} to traverse.
	 * @param aBlock
	 *            The {@linkplain Continuation1 action} to perform with each of
	 *            this parse node's children.
	 */
	@Override @AvailMethod
	abstract void o_ChildrenDo (
		final AvailObject object,
		final Continuation1<AvailObject> aBlock);

	/**
	 * Validate this node, throwing an exception if there is a problem.
	 *
	 * @param object
	 *        The {@linkplain ParseNodeDescriptor parse node} to validate.
	 * @param parent
	 *        The {@linkplain ParseNodeDescriptor parse node} which contains the
	 *        parse node to validate.
	 */
	@Override @AvailMethod
	abstract void o_ValidateLocally (
		final AvailObject object,
		final @Nullable AvailObject parent);

	@Override @AvailMethod
	void o_FlattenStatementsInto (
		final AvailObject object,
		final List<AvailObject> accumulatedStatements)
	{
		accumulatedStatements.add(object);
	}

	/**
	 * Return the {@linkplain ParseNodeKind parse node kind} that this parse
	 * node's type implements.
	 *
	 * @return The {@linkplain ParseNodeKind kind} of parse node that the
	 *         object's type would be.
	 */
	@Override @AvailMethod
	abstract ParseNodeKind o_ParseNodeKind (
		final AvailObject object);

	@Override int maximumIndent ()
	{
		return Integer.MAX_VALUE;
	}

	@Override
	public boolean o_ShowValueInNameForDebugger (
		final AvailObject object)
	{
		return false;
	}

	/**
	 * Visit the entire tree with the given {@linkplain Continuation3 block},
	 * children before parents.  The block takes three arguments: the node, its
	 * parent, and the list of enclosing block nodes.
	 *
	 * @param object
	 *        The current {@linkplain ParseNodeDescriptor parse node}.
	 * @param aBlock
	 *        What to do with each descendant.
	 * @param parentNode
	 *        This node's parent, or {@code null}.
	 * @param outerNodes
	 *        The list of {@linkplain BlockNodeDescriptor blocks} surrounding
	 *        this node, from outermost to innermost.
	 */
	public static void treeDoWithParent (
		final AvailObject object,
		final Continuation3<AvailObject, AvailObject, List<AvailObject>> aBlock,
		final @Nullable AvailObject parentNode,
		final List<AvailObject> outerNodes)
	{
		object.childrenDo(
			new Continuation1<AvailObject>()
			{
				@Override
				public void value (final @Nullable AvailObject child)
				{
					assert child != null;
					assert child.isInstanceOfKind(PARSE_NODE.mostGeneralType());
					treeDoWithParent(
						child,
						aBlock,
						object,
						outerNodes);
				}
			});
		aBlock.value(
			object,
			parentNode,
			outerNodes);
	}
}
