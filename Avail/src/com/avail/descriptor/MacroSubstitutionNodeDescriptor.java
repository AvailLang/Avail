/**
 * MacroSubstitutionNodeDescriptor.java
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

import static com.avail.descriptor.AvailObject.multiplier;
import static com.avail.descriptor.MacroSubstitutionNodeDescriptor.ObjectSlots.*;
import java.util.List;
import com.avail.annotations.*;
import com.avail.compiler.AvailCodeGenerator;
import com.avail.descriptor.ParseNodeTypeDescriptor.ParseNodeKind;
import com.avail.utility.evaluation.*;
import com.avail.utility.json.JSONWriter;

/**
 * A {@linkplain MacroSubstitutionNodeDescriptor macro substitution node}
 * represents the result of applying a {@linkplain MacroDefinitionDescriptor
 * macro} to its argument {@linkplain ParseNodeDescriptor expressions} to
 * produce an {@linkplain ObjectSlots#OUTPUT_PARSE_NODE output parse node}.
 *
 * <p>
 * It's kept around specifically to allow grammatical restrictions to operate on
 * the actual occurring macro (and method) names, not what they've turned into.
 * As such, the macro substitution node should be {@linkplain
 * #o_StripMacro(AvailObject) stripped off} prior to being composed into a larger
 * parse tree, whether a send node, another macro invocation, or direct
 * embedding within an assignment statement, variable reference, or any other
 * hierarchical parsing structure.
 * </p>
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class MacroSubstitutionNodeDescriptor
extends ParseNodeDescriptor
{
	/**
	 * My slots of type {@link AvailObject}.
	 *
	 * @author Mark van Gulik &lt;mark@availlang.org&gt;
	 */
	public enum ObjectSlots
	implements ObjectSlotsEnum
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
		 * MacroDefinitionDescriptor macro} substitution.
		 */
		OUTPUT_PARSE_NODE
	}

	@Override
	public void printObjectOnAvoidingIndent (
		final AvailObject object,
		final StringBuilder builder,
		final List<A_BasicObject> recursionList,
		final int indent)
	{
		builder.append("MACRO TRANSFORMATION (");
		builder.append(object.slot(MACRO_NAME));
		builder.append(") = ");
		object.slot(OUTPUT_PARSE_NODE).printOnAvoidingIndent(
			builder,
			recursionList,
			indent);
	}

	@Override @AvailMethod
	A_Phrase o_OutputParseNode (final AvailObject object)
	{
		return object.slot(OUTPUT_PARSE_NODE);
	}

	@Override @AvailMethod
	A_Type o_ExpressionType (final AvailObject object)
	{
		return object.slot(OUTPUT_PARSE_NODE).expressionType();
	}

	@Override @AvailMethod
	int o_Hash (final AvailObject object)
	{
		return
			object.slot(MACRO_NAME).hash() * multiplier
			+ (object.slot(OUTPUT_PARSE_NODE).hash() ^ 0x1d50d7f9);
	}

	@Override @AvailMethod
	boolean o_EqualsParseNode (
		final AvailObject object,
		final A_Phrase aParseNode)
	{
		return aParseNode.isMacroSubstitutionNode()
			&& object.slot(MACRO_NAME).equals(aParseNode.apparentSendName())
			&& object.slot(OUTPUT_PARSE_NODE).equals(
				aParseNode.outputParseNode());
	}

	@Override @AvailMethod
	A_Atom o_ApparentSendName (final AvailObject object)
	{
		return object.slot(MACRO_NAME);
	}

	@Override @AvailMethod
	void o_EmitEffectOn (
		final AvailObject object,
		final AvailCodeGenerator codeGenerator)
	{
		object.slot(OUTPUT_PARSE_NODE).emitEffectOn(codeGenerator);
	}

	@Override @AvailMethod
	void o_EmitValueOn (
		final AvailObject object,
		final AvailCodeGenerator codeGenerator)
	{
		object.slot(OUTPUT_PARSE_NODE).emitValueOn(codeGenerator);
	}

	@Override @AvailMethod
	void o_ChildrenMap (
		final AvailObject object,
		final Transformer1<A_Phrase, A_Phrase> aBlock)
	{
		object.setSlot(
			OUTPUT_PARSE_NODE,
			aBlock.valueNotNull(object.slot(OUTPUT_PARSE_NODE)));
	}

	@Override @AvailMethod
	void o_ChildrenDo (
		final AvailObject object,
		final Continuation1<A_Phrase> aBlock)
	{
		aBlock.value(object.slot(OUTPUT_PARSE_NODE));
	}

	@Override @AvailMethod
	void o_ValidateLocally (
		final AvailObject object,
		final @Nullable A_Phrase parent)
	{
		// Do nothing.
	}

	@Override @AvailMethod
	void o_FlattenStatementsInto (
		final AvailObject object,
		final List<A_Phrase> accumulatedStatements)
	{
		object.slot(OUTPUT_PARSE_NODE).flattenStatementsInto(
			accumulatedStatements);
	}

	@Override
	ParseNodeKind o_ParseNodeKind (final AvailObject object)
	{
		return object.slot(OUTPUT_PARSE_NODE).parseNodeKind();
	}

	@Override
	A_Phrase o_StripMacro (final AvailObject object)
	{
		return object.slot(OUTPUT_PARSE_NODE);
	}

	@Override
	void o_WriteTo (final AvailObject object, final JSONWriter writer)
	{
		writer.startObject();
		writer.write("kind");
		writer.write("macro substitution phrase");
		writer.write("macro name");
		object.slot(MACRO_NAME).writeTo(writer);
		writer.write("output phrase");
		object.slot(OUTPUT_PARSE_NODE).writeTo(writer);
		writer.endObject();
	}

	@Override
	boolean o_IsMacroSubstitutionNode (final AvailObject object)
	{
		return true;
	}

	/**
	 * Construct a new {@linkplain MacroSubstitutionNodeDescriptor macro
	 * substitution node}.
	 *
	 * @param macroName
	 *        The name of the macro that produced this node.
	 * @param outputParseNode
	 *        The expression produced by the macro body.
	 * @return The new macro substitution node.
	 */
	public static AvailObject fromNameAndNode(
		final A_Atom macroName,
		final AvailObject outputParseNode)
	{
		final AvailObject newNode = mutable.create();
		newNode.setSlot(MACRO_NAME, macroName);
		newNode.setSlot(OUTPUT_PARSE_NODE, outputParseNode);
		newNode.makeShared();
		return newNode;
	}

	/**
	 * Construct a new {@link MacroSubstitutionNodeDescriptor}.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 */
	public MacroSubstitutionNodeDescriptor (final Mutability mutability)
	{
		super(mutability, ObjectSlots.class, null);
	}

	/** The mutable {@link MacroSubstitutionNodeDescriptor}. */
	private static final MacroSubstitutionNodeDescriptor mutable =
		new MacroSubstitutionNodeDescriptor(Mutability.MUTABLE);

	@Override
	MacroSubstitutionNodeDescriptor mutable ()
	{
		return mutable;
	}

	/** The immutable {@link MacroSubstitutionNodeDescriptor}. */
	private static final MacroSubstitutionNodeDescriptor shared =
		new MacroSubstitutionNodeDescriptor(Mutability.SHARED);

	@Override
	MacroSubstitutionNodeDescriptor shared ()
	{
		return shared;
	}
}
