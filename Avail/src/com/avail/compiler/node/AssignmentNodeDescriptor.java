/**
 * com.avail.newcompiler/AssignmentNodeDescriptor.java
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

package com.avail.compiler.node;

import static com.avail.descriptor.AvailObject.*;
import java.util.List;
import com.avail.compiler.AvailCodeGenerator;
import com.avail.descriptor.*;
import com.avail.descriptor.TypeDescriptor.Types;
import com.avail.interpreter.levelTwo.L2Interpreter;
import com.avail.compiler.node.DeclarationNodeDescriptor.DeclarationKind;
import com.avail.utility.Transformer1;

/**
 * My instances represent assignment statements.
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 */
public class AssignmentNodeDescriptor extends ParseNodeDescriptor
{
	/**
	 * My slots of type {@link AvailObject}.
	 *
	 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
	 */
	public enum ObjectSlots
	{
		/**
		 * The {@link VariableUseNodeDescriptor variable} being assigned.
		 */
		VARIABLE,

		/**
		 * The actual {@link ParseNodeDescriptor expression} providing the value to
		 * assign.
		 */
		EXPRESSION
	}


	/**
	 * Setter for field variable.
	 */
	@Override
	public void o_Variable (
		final AvailObject object,
		final AvailObject value)
	{
		object.objectSlotPut(ObjectSlots.VARIABLE, value);
	}

	/**
	 * Getter for field variable.
	 */
	@Override
	public AvailObject o_Variable (
		final AvailObject object)
	{
		return object.objectSlot(ObjectSlots.VARIABLE);
	}

	/**
	 * Setter for field expression.
	 */
	@Override
	public void o_Expression (
		final AvailObject object,
		final AvailObject value)
	{
		object.objectSlotPut(ObjectSlots.EXPRESSION, value);
	}

	/**
	 * Getter for field expression.
	 */
	@Override
	public AvailObject o_Expression (
		final AvailObject object)
	{
		return object.objectSlot(ObjectSlots.EXPRESSION);
	}


	@Override
	public AvailObject o_ExpressionType (final AvailObject object)
	{
		return Types.voidType.object();
	}

	@Override
	public AvailObject o_Type (final AvailObject object)
	{
		return Types.assignmentNode.object();
	}

	@Override
	public AvailObject o_ExactType (final AvailObject object)
	{
		return Types.assignmentNode.object();
	}

	@Override
	public int o_Hash (final AvailObject object)
	{
		return
			object.variable().hash() * Multiplier
				+ object.expression().hash()
			^ 0xA71EA854;
	}

	@Override
	public boolean o_Equals (
		final AvailObject object,
		final AvailObject another)
	{
		return object.type().equals(another.type())
			&& object.variable().equals(another.variable())
			&& object.expression().equals(another.expression());
	}

	@Override
	public void o_EmitEffectOn (
		final AvailObject object,
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

	@Override
	public void o_EmitValueOn (
		final AvailObject object,
		final AvailCodeGenerator codeGenerator)
	{
		// Assignments are only allowed to be top-level statements.
		error("Pass-through (embedded) assignments are not supported");
	}

	@Override
	public void o_ChildrenMap (
		final AvailObject object,
		final Transformer1<AvailObject, AvailObject> aBlock)
	{
		object.expression(aBlock.value(object.expression()));
		object.variable(aBlock.value(object.variable()));
	}

	@Override
	public void o_ValidateLocally (
		final AvailObject object,
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
	public void printObjectOnAvoidingIndent (
		final AvailObject object,
		final StringBuilder builder,
		final List<AvailObject> recursionList,
		final int indent)
	{
		builder.append(
			object.variable().declaration().token().string().asNativeString());
		builder.append(" := ");
		object.expression().printOnAvoidingIndent(
			builder,
			recursionList,
			indent + 1);
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
