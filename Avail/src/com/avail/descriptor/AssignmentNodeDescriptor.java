/**
 * com.avail.compiler/AssignmentNodeDescriptor.java
 * Copyright (c) 2010, Mark van Gulik.
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
import java.util.List;
import com.avail.annotations.*;
import com.avail.compiler.AvailCodeGenerator;
import com.avail.descriptor.DeclarationNodeDescriptor.DeclarationKind;
import com.avail.interpreter.levelTwo.L2Interpreter;
import com.avail.utility.*;

/**
 * My instances represent assignment statements.
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 */
public class AssignmentNodeDescriptor
extends ParseNodeDescriptor
{
	/**
	 * My slots of type {@link AvailObject}.
	 *
	 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
	 */
	public enum ObjectSlots implements ObjectSlotsEnum
	{
		/**
		 * The {@linkplain VariableUseNodeDescriptor variable} being assigned.
		 */
		VARIABLE,

		/**
		 * The actual {@linkplain ParseNodeDescriptor expression} providing the value
		 * to assign.
		 */
		EXPRESSION
	}


	/**
	 * Setter for field variable.
	 */
	@Override @AvailMethod
	void o_Variable (
		final @NotNull AvailObject object,
		final AvailObject value)
	{
		object.objectSlotPut(ObjectSlots.VARIABLE, value);
	}

	/**
	 * Getter for field variable.
	 */
	@Override @AvailMethod
	AvailObject o_Variable (
		final @NotNull AvailObject object)
	{
		return object.objectSlot(ObjectSlots.VARIABLE);
	}

	/**
	 * Setter for field expression.
	 */
	@Override @AvailMethod
	void o_Expression (
		final @NotNull AvailObject object,
		final AvailObject value)
	{
		object.objectSlotPut(ObjectSlots.EXPRESSION, value);
	}

	/**
	 * Getter for field expression.
	 */
	@Override @AvailMethod
	AvailObject o_Expression (
		final @NotNull AvailObject object)
	{
		return object.objectSlot(ObjectSlots.EXPRESSION);
	}


	@Override @AvailMethod
	AvailObject o_ExpressionType (final AvailObject object)
	{
		return object.expression().expressionType();
	}

	@Override @AvailMethod
	AvailObject o_Kind (final AvailObject object)
	{
		return ParseNodeTypeDescriptor.ParseNodeKind.ASSIGNMENT_NODE.create(
			object.expressionType());
	}

	@Override @AvailMethod
	int o_Hash (final AvailObject object)
	{
		return
			object.variable().hash() * Multiplier
				+ object.expression().hash()
			^ 0xA71EA854;
	}

	@Override @AvailMethod
	boolean o_Equals (
		final @NotNull AvailObject object,
		final AvailObject another)
	{
		return object.kind().equals(another.kind())
			&& object.variable().equals(another.variable())
			&& object.expression().equals(another.expression());
	}

	@Override @AvailMethod
	void o_EmitEffectOn (
		final @NotNull AvailObject object,
		final AvailCodeGenerator codeGenerator)
	{
		final AvailObject declaration = object.variable().declaration();
		final DeclarationKind declarationKind = declaration.declarationKind();
		assert declarationKind.isVariable();
		object.expression().emitValueOn(codeGenerator);
		declarationKind.emitVariableAssignmentForOn(
			declaration,
			codeGenerator);
	}

	@Override @AvailMethod
	void o_EmitValueOn (
		final @NotNull AvailObject object,
		final AvailCodeGenerator codeGenerator)
	{
		final AvailObject declaration = object.variable().declaration();
		final DeclarationKind declarationKind = declaration.declarationKind();
		assert declarationKind.isVariable();
		object.expression().emitValueOn(codeGenerator);
		codeGenerator.emitDuplicate();
		declarationKind.emitVariableAssignmentForOn(
			declaration,
			codeGenerator);
	}

	@Override @AvailMethod
	void o_ChildrenMap (
		final @NotNull AvailObject object,
		final Transformer1<AvailObject, AvailObject> aBlock)
	{
		object.expression(aBlock.value(object.expression()));
		object.variable(aBlock.value(object.variable()));
	}

	@Override @AvailMethod
	void o_ChildrenDo (
		final @NotNull AvailObject object,
		final Continuation1<AvailObject> aBlock)
	{
		aBlock.value(object.expression());
		aBlock.value(object.variable());
	}

	@Override @AvailMethod
	void o_ValidateLocally (
		final @NotNull AvailObject object,
		final AvailObject parent,
		final List<AvailObject> outerBlocks,
		final L2Interpreter anAvailInterpreter)
	{
		final AvailObject variable = object.variable();
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
				error("Can't assign to constant");
				break;
			case LOCAL_VARIABLE:
			case MODULE_VARIABLE:
				break;
		}
	}

	@Override
	void printObjectOnAvoidingIndent (
		final @NotNull AvailObject object,
		final StringBuilder builder,
		final List<AvailObject> recursionList,
		final int indent)
	{
		builder.append(
			object.variable().token().string().asNativeString());
		builder.append(" := ");
		object.expression().printOnAvoidingIndent(
			builder,
			recursionList,
			indent + 1);
	}

	/**
	 * Create a new {@linkplain AssignmentNodeDescriptor assignment node} using the
	 * given {@linkplain VariableUseNodeDescriptor variable use} and {@linkplain
	 * ParseNodeDescriptor expression}.
	 *
	 * @param variableUse
	 *            A use of the variable into which to assign.
	 * @param expression
	 *            The expression whose value should be assigned to the variable.
	 * @return The new assignment node.
	 */
	public static AvailObject from (
		final AvailObject variableUse,
		final AvailObject expression)
	{
		final AvailObject assignment = mutable().create();
		assignment.objectSlotPut(ObjectSlots.VARIABLE, variableUse);
		assignment.objectSlotPut(ObjectSlots.EXPRESSION, expression);
		assignment.makeImmutable();
		return assignment;
	}


	/**
	 * Construct a new {@link AssignmentNodeDescriptor}.
	 *
	 * @param isMutable Whether my {@linkplain AvailObject instances} can
	 *                  change.
	 */
	public AssignmentNodeDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}

	/**
	 * The mutable {@link AssignmentNodeDescriptor}.
	 */
	private final static AssignmentNodeDescriptor mutable =
		new AssignmentNodeDescriptor(true);

	/**
	 * Answer the mutable {@link AssignmentNodeDescriptor}.
	 *
	 * @return The mutable {@link AssignmentNodeDescriptor}.
	 */
	public static AssignmentNodeDescriptor mutable ()
	{
		return mutable;
	}

	/**
	 * The immutable {@link AssignmentNodeDescriptor}.
	 */
	private final static AssignmentNodeDescriptor immutable =
		new AssignmentNodeDescriptor(false);

	/**
	 * Answer the immutable {@link AssignmentNodeDescriptor}.
	 *
	 * @return The immutable {@link AssignmentNodeDescriptor}.
	 */
	public static AssignmentNodeDescriptor immutable ()
	{
		return immutable;
	}
}
