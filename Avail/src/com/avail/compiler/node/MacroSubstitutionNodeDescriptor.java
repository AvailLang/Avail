/**
 * com.avail.compiler/MacroSubstitutionNodeDescriptor.java
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
import java.util.List;
import com.avail.annotations.NotNull;
import com.avail.compiler.AvailCodeGenerator;
import com.avail.descriptor.*;
import com.avail.interpreter.levelTwo.L2Interpreter;
import com.avail.utility.*;

/**
 * A {@linkplain MacroSubstitutionNodeDescriptor macro substitution node}
 * represents the result of applying a {@linkplain MacroSignatureDescriptor
 * macro} to its argument {@linkplain ParseNodeDescriptor expressions} to
 * produce an {@linkplain ObjectSlots#OUTPUT_PARSE_NODE output parse node}.
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 */
public class MacroSubstitutionNodeDescriptor extends ParseNodeDescriptor
{
	/**
	 * My slots of type {@link AvailObject}.
	 *
	 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
	 */
	public enum ObjectSlots
	{
		/**
		 * The {@linkplain AtomDescriptor true name} of the macro that was
		 * invoked to produce this {@linkplain MacroSubstitutionNodeDescriptor
		 * macro substitution node}.
		 */
		MACRO_NAME,

		/**
		 * The {@linkplain ParseNodeDescriptor parse node} that is the result of
		 * transforming the input parse node through a {@linkplain
		 * MacroSignatureDescriptor macro substitution}.
		 */
		OUTPUT_PARSE_NODE
	}

	/**
	 * Setter for field macroName.
	 */
	@Override
	public void o_MacroName (
		final AvailObject object,
		final AvailObject value)
	{
		object.objectSlotPut(ObjectSlots.MACRO_NAME, value);
	}

	/**
	 * Getter for field macroName.
	 */
	@Override
	public AvailObject o_MacroName (
		final AvailObject object)
	{
		return object.objectSlot(ObjectSlots.MACRO_NAME);
	}

	/**
	 * Setter for field outputParseNode.
	 */
	@Override
	public void o_OutputParseNode (
		final AvailObject object,
		final AvailObject value)
	{
		object.objectSlotPut(ObjectSlots.OUTPUT_PARSE_NODE, value);
	}

	/**
	 * Getter for field outputParseNode.
	 */
	@Override
	public AvailObject o_OutputParseNode (
		final AvailObject object)
	{
		return object.objectSlot(ObjectSlots.OUTPUT_PARSE_NODE);
	}


	@Override
	public AvailObject o_ExpressionType (final AvailObject object)
	{
		return object.outputParseNode().expressionType();
	}


	@Override
	public AvailObject o_Kind (final AvailObject object)
	{
		return object.outputParseNode().kind();
	}


	@Override
	public int o_Hash (final AvailObject object)
	{
		return
			object.macroName().hash() * Multiplier
				+ object.outputParseNode().hash()
			^ 0x1d50d7f9;
	}


	@Override
	public boolean o_Equals (
		final AvailObject object,
		final AvailObject another)
	{
		return object.macroName().equals(another.macroName())
			&& object.outputParseNode().equals(another.outputParseNode());
	}


	@Override
	public AvailObject o_ApparentSendName (final AvailObject object)
	{
		return object.macroName();
	}


	@Override
	public void o_EmitEffectOn (
		final AvailObject object,
		final AvailCodeGenerator codeGenerator)
	{
		object.outputParseNode().emitEffectOn(codeGenerator);
	}


	@Override
	public void o_EmitValueOn (
		final AvailObject object,
		final AvailCodeGenerator codeGenerator)
	{
		object.outputParseNode().emitValueOn(codeGenerator);
	}


	@Override
	public void o_ChildrenMap (
		final AvailObject object,
		final Transformer1<AvailObject, AvailObject> aBlock)
	{
		object.outputParseNode(aBlock.value(object.outputParseNode()));
	}


	@Override
	public void o_ChildrenDo (
		final AvailObject object,
		final Continuation1<AvailObject> aBlock)
	{
		aBlock.value(object.outputParseNode());
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


	@Override
	public void o_FlattenStatementsInto (
		final AvailObject object,
		final List<AvailObject> accumulatedStatements)
	{
		object.outputParseNode().flattenStatementsInto(accumulatedStatements);
	}


	@Override
	public void printObjectOnAvoidingIndent (
		final AvailObject object,
		final StringBuilder builder,
		final List<AvailObject> recursionList,
		final int indent)
	{
		builder.append("MACRO TRANSFORMATION (");
		builder.append(object.macroName());
		builder.append(") = ");
		object.outputParseNode().printOnAvoidingIndent(
			builder,
			recursionList,
			indent);
	}


	/**
	 * Construct a new {@link MacroSubstitutionNodeDescriptor macro substitution
	 * node}.
	 *
	 * @param macroName
	 *            The name of the macro that produced this node.
	 * @param outputParseNode
	 *            The expression produced by the macro body.
	 * @return The new macro substitution node.
	 */
	public AvailObject fromNameAndNode(
		final @NotNull AvailObject macroName,
		final @NotNull AvailObject outputParseNode)
	{
		final AvailObject newNode = mutable().create();
		newNode.macroName(macroName);
		newNode.outputParseNode(outputParseNode);
		newNode.makeImmutable();
		return newNode;
	}

	/**
	 * Construct a new {@link MacroSubstitutionNodeDescriptor}.
	 *
	 * @param isMutable Whether my {@linkplain AvailObject instances} can
	 *                  change.
	 */
	public MacroSubstitutionNodeDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}

	/**
	 * The mutable {@link MacroSubstitutionNodeDescriptor}.
	 */
	private final static MacroSubstitutionNodeDescriptor mutable =
		new MacroSubstitutionNodeDescriptor(true);

	/**
	 * Answer the mutable {@link MacroSubstitutionNodeDescriptor}.
	 *
	 * @return The mutable {@link MacroSubstitutionNodeDescriptor}.
	 */
	public static MacroSubstitutionNodeDescriptor mutable ()
	{
		return mutable;
	}

	/**
	 * The immutable {@link MacroSubstitutionNodeDescriptor}.
	 */
	private final static MacroSubstitutionNodeDescriptor immutable =
		new MacroSubstitutionNodeDescriptor(false);

	/**
	 * Answer the immutable {@link MacroSubstitutionNodeDescriptor}.
	 *
	 * @return The immutable {@link MacroSubstitutionNodeDescriptor}.
	 */
	public static MacroSubstitutionNodeDescriptor immutable ()
	{
		return immutable;
	}
}
