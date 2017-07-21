/**
 * AssignmentNodeDescriptor.java
 * Copyright © 1993-2017, The Avail Foundation, LLC.
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

import static com.avail.descriptor.AvailObject.*;
import static com.avail.descriptor.AssignmentNodeDescriptor.IntegerSlots.*;
import static com.avail.descriptor.AssignmentNodeDescriptor.ObjectSlots.*;
import java.util.IdentityHashMap;

import com.avail.annotations.AvailMethod;
import com.avail.compiler.AvailCodeGenerator;
import com.avail.descriptor.DeclarationNodeDescriptor.DeclarationKind;
import com.avail.descriptor.ParseNodeTypeDescriptor.ParseNodeKind;
import com.avail.descriptor.TypeDescriptor.Types;
import com.avail.serialization.SerializerOperation;
import com.avail.utility.evaluation.*;
import com.avail.utility.json.JSONWriter;
import org.jetbrains.annotations.Nullable;

/**
 * My instances represent assignment statements.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class AssignmentNodeDescriptor
extends ParseNodeDescriptor
{
	/**
	 * My integer slots.
	 */
	public enum IntegerSlots
	implements IntegerSlotsEnum
	{
		/**
		 * The {@linkplain AssignmentNodeDescriptor assignment node}'s flags.
		 */
		FLAGS;

		/**
		 * Is this an inline {@linkplain AssignmentNodeDescriptor assignment}?
		 */
		static final BitField IS_INLINE = bitField(FLAGS, 0, 1);
	}

	/**
	 * My slots of type {@link AvailObject}.
	 */
	public enum ObjectSlots
	implements ObjectSlotsEnum
	{
		/**
		 * The {@linkplain VariableUseNodeDescriptor variable} being assigned.
		 */
		VARIABLE,

		/**
		 * The actual {@linkplain ParseNodeDescriptor expression} providing the
		 * value to assign.
		 */
		EXPRESSION
	}

	@Override
	void printObjectOnAvoidingIndent (
		final AvailObject object,
		final StringBuilder builder,
		final IdentityHashMap<A_BasicObject, Void> recursionMap,
		final int indent)
	{
		builder.append(object.slot(VARIABLE).token().string().asNativeString());
		builder.append(" := ");
		object.slot(EXPRESSION).printOnAvoidingIndent(
			builder,
			recursionMap,
			indent + 1);
	}

	@Override @AvailMethod
	A_Phrase o_Variable (final AvailObject object)
	{
		return object.slot(VARIABLE);
	}

	@Override @AvailMethod
	A_Phrase o_Expression (final AvailObject object)
	{
		return object.slot(EXPRESSION);
	}

	/**
	 * Does the {@linkplain AvailObject object} represent an inline assignment?
	 *
	 * @param object An object.
	 * @return {@code true} if the object represents an inline assignment,
	 *         {@code false} otherwise.
	 */
	public static boolean isInline (final AvailObject object)
	{
		return object.slot(IS_INLINE) != 0;
	}

	@Override @AvailMethod
	A_Type o_ExpressionType (final AvailObject object)
	{
		if (!isInline(object))
		{
			return Types.TOP.o();
		}
		return object.slot(EXPRESSION).expressionType();
	}

	@Override @AvailMethod
	int o_Hash (final AvailObject object)
	{
		return
			object.variable().hash() * multiplier
				+ object.expression().hash()
			^ 0xA71EA854;
	}

	@Override @AvailMethod
	boolean o_EqualsParseNode (
		final AvailObject object,
		final A_Phrase aParseNode)
	{
		return !aParseNode.isMacroSubstitutionNode()
			&& object.parseNodeKind().equals(aParseNode.parseNodeKind())
			&& object.slot(VARIABLE).equals(aParseNode.variable())
			&& object.slot(EXPRESSION).equals(aParseNode.expression());
	}

	@Override @AvailMethod
	void o_EmitEffectOn (
		final AvailObject object,
		final AvailCodeGenerator codeGenerator)
	{
		final A_Phrase declaration = object.slot(VARIABLE).declaration();
		final DeclarationKind declarationKind = declaration.declarationKind();
		assert declarationKind.isVariable();
		object.slot(EXPRESSION).emitValueOn(codeGenerator);
		declarationKind.emitVariableAssignmentForOn(declaration, codeGenerator);
	}

	@Override @AvailMethod
	void o_EmitValueOn (
		final AvailObject object,
		final AvailCodeGenerator codeGenerator)
	{
		final A_Phrase declaration = object.slot(VARIABLE).declaration();
		final DeclarationKind declarationKind = declaration.declarationKind();
		assert declarationKind.isVariable();
		object.slot(EXPRESSION).emitValueOn(codeGenerator);
		if (isInline(object))
		{
			codeGenerator.emitDuplicate();
			declarationKind.emitVariableAssignmentForOn(
				declaration, codeGenerator);
		}
		else
		{
			// This assignment is the last statement in a sequence.  Don't leak
			// the assigned value, since it's *not* an inlined assignment.
			declarationKind.emitVariableAssignmentForOn(
				declaration, codeGenerator);
			codeGenerator.emitPushLiteral(NilDescriptor.nil());
		}
	}

	@Override @AvailMethod
	void o_ChildrenMap (
		final AvailObject object,
		final Transformer1<A_Phrase, A_Phrase> aBlock)
	{
		object.setSlot(EXPRESSION,
			aBlock.valueNotNull(object.slot(EXPRESSION)));
		object.setSlot(VARIABLE, aBlock.valueNotNull(object.slot(VARIABLE)));
	}

	@Override @AvailMethod
	void o_ChildrenDo (
		final AvailObject object,
		final Continuation1<A_Phrase> aBlock)
	{
		aBlock.value(object.slot(EXPRESSION));
		aBlock.value(object.slot(VARIABLE));
	}

	@Override
	void o_StatementsDo (
		final AvailObject object,
		final Continuation1<A_Phrase> continuation)
	{
		continuation.value(object);
	}

	@Override @AvailMethod
	void o_ValidateLocally (
		final AvailObject object,
		final @Nullable A_Phrase parent)
	{
		final A_Phrase variable = object.slot(VARIABLE);
		final DeclarationKind kind = variable.declaration().declarationKind();
		switch (kind)
		{
			case ARGUMENT:
				error("Can't assign to argument");
				break;
			case LABEL:
				error("Can't assign to label");
				break;
			case LOCAL_CONSTANT:
			case MODULE_CONSTANT:
			case PRIMITIVE_FAILURE_REASON:
				error("Can't assign to constant");
				break;
			case LOCAL_VARIABLE:
			case MODULE_VARIABLE:
				break;
		}
	}

	@Override
	ParseNodeKind o_ParseNodeKind (final AvailObject object)
	{
		return ParseNodeKind.ASSIGNMENT_NODE;
	}

	@Override
	SerializerOperation o_SerializerOperation (final AvailObject object)
	{
		return SerializerOperation.ASSIGNMENT_PHRASE;
	}

	@Override
	void o_WriteTo (final AvailObject object, final JSONWriter writer)
	{
		writer.startObject();
		writer.write("kind");
		writer.write("assignment phrase");
		writer.write("target");
		object.slot(VARIABLE).writeTo(writer);
		writer.write("expression");
		object.slot(EXPRESSION).writeTo(writer);
		writer.endObject();
	}

	@Override
	void o_WriteSummaryTo (final AvailObject object, final JSONWriter writer)
	{
		writer.startObject();
		writer.write("kind");
		writer.write("assignment phrase");
		writer.write("target");
		object.slot(VARIABLE).writeSummaryTo(writer);
		writer.write("expression");
		object.slot(EXPRESSION).writeSummaryTo(writer);
		writer.endObject();
	}

	/**
	 * Create a new {@linkplain AssignmentNodeDescriptor assignment node} using
	 * the given {@linkplain VariableUseNodeDescriptor variable use} and
	 * {@linkplain ParseNodeDescriptor expression}.  Also indicate whether the
	 * assignment is inline (produces a value) or not (must be a statement).
	 *
	 * @param variableUse
	 *        A use of the variable into which to assign.
	 * @param expression
	 *        The expression whose value should be assigned to the variable.
	 * @param isInline
	 *        {@code true} to create an inline assignment, {@code false}
	 *        otherwise.
	 * @return The new assignment node.
	 */
	public static A_Phrase from (
		final A_Phrase variableUse,
		final A_Phrase expression,
		final boolean isInline)
	{
		final AvailObject assignment = mutable.create();
		assignment.setSlot(VARIABLE, variableUse);
		assignment.setSlot(EXPRESSION, expression);
		assignment.setSlot(IS_INLINE, isInline ? 1 : 0);
		assignment.makeShared();
		return assignment;
	}

	/**
	 * Construct a new {@link AssignmentNodeDescriptor}.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 */
	private AssignmentNodeDescriptor (final Mutability mutability)
	{
		super(
			mutability,
			TypeTag.ASSIGNMENT_PHRASE_TAG,
			ObjectSlots.class,
			IntegerSlots.class);
	}

	/** The mutable {@link AssignmentNodeDescriptor}. */
	private static final AssignmentNodeDescriptor mutable =
		new AssignmentNodeDescriptor(Mutability.MUTABLE);

	@Override
	AssignmentNodeDescriptor mutable ()
	{
		return mutable;
	}

	/** The shared {@link AssignmentNodeDescriptor}. */
	private static final AssignmentNodeDescriptor shared =
		new AssignmentNodeDescriptor(Mutability.SHARED);

	@Override
	AssignmentNodeDescriptor shared ()
	{
		return shared;
	}
}
