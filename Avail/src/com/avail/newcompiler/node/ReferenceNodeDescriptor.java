/**
 * com.avail.newcompiler/ReferenceNodeDescriptor.java
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

package com.avail.newcompiler.node;

import static com.avail.descriptor.AvailObject.error;
import java.util.List;
import com.avail.compiler.AvailCodeGenerator;
import com.avail.descriptor.*;
import com.avail.descriptor.TypeDescriptor.Types;
import com.avail.interpreter.levelTwo.L2Interpreter;
import com.avail.utility.Transformer1;

/**
 * My instances represent a reference-taking expression.  A variable itself is
 * to be pushed on the stack.  Note that this does not work for arguments or
 * constants or labels, as no actual variable object is created for those.
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 */
public class ReferenceNodeDescriptor extends ParseNodeDescriptor
{
	/**
	 * My slots of type {@link AvailObject}.
	 *
	 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
	 */
	public enum ObjectSlots
	{
		/**
		 * The {@link VariableUseNodeDescriptor variable use node} for which
		 * the reference is being taken.
		 */
		VARIABLE
	}


	/**
	 * Setter for field variable.
	 */
	@Override
	public void o_Variable (
		final AvailObject object,
		final AvailObject variable)
	{
		object.objectSlotPut(ObjectSlots.VARIABLE, variable);
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
	 * The value I represent is a variable itself.  Answer an appropriate
	 * variable type.
	 */
	@Override
	public AvailObject o_ExpressionType (final AvailObject object)
	{
		return ContainerTypeDescriptor.wrapInnerType(
			object.variable().expressionType());
	}

	@Override
	public AvailObject o_Type (final AvailObject object)
	{
		return Types.referenceNode.object();
	}

	@Override
	public AvailObject o_ExactType (final AvailObject object)
	{
		return Types.referenceNode.object();
	}

	@Override
	public int o_Hash (final AvailObject object)
	{
		return
			object.variable().hash() ^ 0xE7FA9B3F;
	}

	@Override
	public boolean o_Equals (
		final AvailObject object,
		final AvailObject another)
	{
		return object.type().equals(another.type())
			&& object.variable().equals(another.variable());
	}

	@Override
	public void o_EmitValueOn (
		final AvailObject object,
		final AvailCodeGenerator codeGenerator)
	{
		final AvailObject declaration = object.variable().declaration();
		declaration.declarationKind().emitVariableReferenceForOn(
			declaration,
			codeGenerator);
	}

	@Override
	public void o_ChildrenMap (
		final AvailObject object,
		final Transformer1<AvailObject, AvailObject> aBlock)
	{
		object.variable(aBlock.value(object.variable()));
	}


	@Override
	public void printObjectOnAvoidingIndent (
		final AvailObject object,
		final StringBuilder builder,
		final List<AvailObject> recursionList,
		final int indent)
	{
		builder.append("&");
		builder.append(
			object.variable().token().string().asNativeString());
	}



	/**
	 * Construct a new {@link ReferenceNodeDescriptor}.
	 *
	 * @param isMutable Whether my {@linkplain AvailObject instances} can
	 *                  change.
	 */
	public ReferenceNodeDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}

	/**
	 * The mutable {@link ReferenceNodeDescriptor}.
	 */
	private final static ReferenceNodeDescriptor mutable =
		new ReferenceNodeDescriptor(true);

	/**
	 * Answer the mutable {@link ReferenceNodeDescriptor}.
	 *
	 * @return The mutable {@link ReferenceNodeDescriptor}.
	 */
	public static ReferenceNodeDescriptor mutable ()
	{
		return mutable;
	}

	/**
	 * The immutable {@link ReferenceNodeDescriptor}.
	 */
	private final static ReferenceNodeDescriptor immutable =
		new ReferenceNodeDescriptor(false);

	/**
	 * Answer the immutable {@link ReferenceNodeDescriptor}.
	 *
	 * @return The immutable {@link ReferenceNodeDescriptor}.
	 */
	public static ReferenceNodeDescriptor immutable ()
	{
		return immutable;
	}

	@Override
	public void o_ValidateLocally (
		final AvailObject object,
		final AvailObject parent,
		final List<AvailObject> outerBlocks,
		final L2Interpreter anAvailInterpreter)
	{
		final AvailObject decl = object.variable().declaration();
		switch (decl.declarationKind())
		{
			case ARGUMENT:
				error("You can't take the reference of an argument");
				break;
			case LABEL:
				error("You can't take the reference of a label");
				break;
			case LOCAL_CONSTANT:
			case MODULE_CONSTANT:
				error("You can't take the reference of a constant");
				break;
			case LOCAL_VARIABLE:
			case MODULE_VARIABLE:
				// Do nothing.
				break;
		}
	}

}
