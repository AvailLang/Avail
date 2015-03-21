/**
 * ParseNodeDescriptor.java
 * Copyright © 1993-2015, The Avail Foundation, LLC.
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
import com.avail.descriptor.ParseNodeTypeDescriptor.ParseNodeKind;
import com.avail.utility.evaluation.*;

/**
 * I'm used to implement the abstract notion of parse nodes. All concrete parse
 * nodes are below me in the hierarchy.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public abstract class ParseNodeDescriptor
extends Descriptor
{
	/**
	 * A special enumeration used to visit all object slots for copying.
	 */
	enum FakeObjectSlots
	implements ObjectSlotsEnum
	{
		/**
		 * An indexed object slot that makes it easy to visit all object slots.
		 */
		ALL_OBJECT_SLOTS_
	}

	/**
	 * A special enumeration used to visit all integer slots for copying.
	 */
	enum FakeIntegerSlots
	implements IntegerSlotsEnum
	{
		/**
		 * An indexed integer slot that makes it easy to visit all integer
		 * slots.
		 */
		ALL_INTEGER_SLOTS_
	}

	@Override int maximumIndent ()
	{
		return Integer.MAX_VALUE;
	}

	/**
	 * The {@link #o_ApparentSendName(AvailObject) apparentSendName} of
	 * something that isn't a {@linkplain SendNodeDescriptor send node} or
	 * {@linkplain MacroSubstitutionNodeDescriptor macro substitution node} is
	 * always the {@link NilDescriptor#nil() nil} object.
	 */
	@Override @AvailMethod
	A_Atom o_ApparentSendName (final AvailObject object)
	{
		return NilDescriptor.nil();
	}

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
		final Continuation1<A_Phrase> aBlock);

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
		final Transformer1<A_Phrase, A_Phrase> aBlock);

	/**
	 * If the receiver is immutable, make an equivalent mutable copy of that
	 * parse node.  Otherwise, answer the receiver itself.
	 *
	 * @param object
	 *        The {@linkplain ParseNodeDescriptor parse node} of which to
	 *        create a mutable copy.
	 * @return A mutable {@linkplain ParseNodeDescriptor parse node} equivalent
	 *         to the passed parse node, possibly the same object.
	 */
	@Override @AvailMethod
	A_Phrase o_CopyMutableParseNode (final AvailObject object)
	{
		object.makeSubobjectsImmutable();
		if (isMutable())
		{
			return object;
		}
		return AvailObjectRepresentation.newLike(mutable(), object, 0, 0);
	}

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

	@Override
	void o_EmitForSuperSendOn (
		final AvailObject object,
		final AvailCodeGenerator codeGenerator)
	{
		// This is a normal argument.  Push it, then push its type.
		object.emitValueOn(codeGenerator);
		codeGenerator.emitGetType();
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
	 * {@linkplain ParseNodeDescriptor parse nodes} must implement {@link
	 * ParseNodeDescriptor#o_EqualsParseNode(AvailObject, A_Phrase)}.
	 */
	@Override @AvailMethod
	final boolean o_Equals (
		final AvailObject object,
		final A_BasicObject another)
	{
		return another.equalsParseNode(object);
	}

	/**
	 * Compare this parse node to the given parse node.
	 */
	@Override @AvailMethod
	abstract boolean o_EqualsParseNode (
		final AvailObject object,
		final A_Phrase aParseNode);

	/**
	 * Return the parse node's expression type, which is the type of object that
	 * will be produced by this parse node.
	 *
	 * @return The {@linkplain TypeDescriptor type} of the {@link AvailObject}
	 *         that will be produced by this parse node.
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
	 * {@linkplain ParseNodeDescriptor parse nodes} must implement {@link
	 * AbstractDescriptor#o_Hash(AvailObject) hash}.
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
		if (PARSE_NODE.mostGeneralType().isSubtypeOf(aType))
		{
			return true;
		}
		if (aType.isSubtypeOf(PARSE_NODE.mostGeneralType()))
		{
			return object.parseNodeKindIsUnder(aType.parseNodeKind())
				&& object.expressionType().isSubtypeOf(aType.expressionType());
		}
		return false;
	}

	@Override
	boolean o_IsMacroSubstitutionNode (final AvailObject object)
	{
		return false;
	}

	@Override @AvailMethod
	final A_Type o_Kind (final AvailObject object)
	{
		return object.parseNodeKind().create(object.expressionType());
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
	 * Return the {@linkplain ParseNodeKind parse node kind} that this parse
	 * node's type implements.
	 *
	 * @return The {@linkplain ParseNodeKind kind} of parse node that the
	 *         object's type would be.
	 */
	@Override @AvailMethod
	abstract ParseNodeKind o_ParseNodeKind (final AvailObject object);

	@Override @AvailMethod
	boolean o_ParseNodeKindIsUnder (
		final AvailObject object,
		final ParseNodeKind expectedParseNodeKind)
	{
		return object.parseNodeKind().isSubkindOf(expectedParseNodeKind);
	}

	@Override
	public boolean o_ShowValueInNameForDebugger (final AvailObject object)
	{
		return false;
	}

	@Override @AvailMethod
	abstract void o_StatementsDo (
		final AvailObject object,
		final Continuation1<A_Phrase> continuation);

	@Override @AvailMethod
	A_Phrase o_StripMacro (final AvailObject object)
	{
		return object;
	}

	@Override @AvailMethod
	A_Type o_TypeForLookup (final AvailObject object)
	{
		return object.expressionType();
	}

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
		final @Nullable A_Phrase parent);

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
	 */
	public static void treeDoWithParent (
		final A_Phrase object,
		final Continuation2<A_Phrase, A_Phrase> aBlock,
		final @Nullable A_Phrase parentNode)
	{
		object.childrenDo(
			new Continuation1<A_Phrase>()
			{
				@Override
				public void value (final @Nullable A_Phrase child)
				{
					assert child != null;
					assert child.isInstanceOfKind(PARSE_NODE.mostGeneralType());
					treeDoWithParent(
						child,
						aBlock,
						object);
				}
			});
		aBlock.value(object, parentNode);
	}

	/**
	 * Construct a new {@link ParseNodeDescriptor}.
	 *
	 * @param mutability
	 *            The {@linkplain Mutability mutability} of the new descriptor.
	 * @param objectSlotsEnumClass
	 *            The Java {@link Class} which is a subclass of {@link
	 *            ObjectSlotsEnum} and defines this object's object slots
	 *            layout, or null if there are no object slots.
	 * @param integerSlotsEnumClass
	 *            The Java {@link Class} which is a subclass of {@link
	 *            IntegerSlotsEnum} and defines this object's object slots
	 *            layout, or null if there are no integer slots.
	 */
	protected ParseNodeDescriptor (
		final Mutability mutability,
		final @Nullable Class<? extends ObjectSlotsEnum> objectSlotsEnumClass,
		final @Nullable Class<? extends IntegerSlotsEnum> integerSlotsEnumClass)
	{
		super(mutability, objectSlotsEnumClass, integerSlotsEnumClass);
	}

	@Override
	final ParseNodeDescriptor immutable ()
	{
		// Subclasses do not have an immutable descriptor, so use the shared one
		// instead.
		return shared();
	}

	@Override
	abstract ParseNodeDescriptor shared ();
}
