/**
 * com.avail.newcompiler/BlockNodeDescriptor.java
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

import java.util.*;
import com.avail.compiler.AvailCodeGenerator;
import com.avail.descriptor.*;
import com.avail.descriptor.TypeDescriptor.Types;
import com.avail.interpreter.Primitive;
import com.avail.utility.Continuation1;

/**
 * My instances represent occurrences of blocks (closures) encountered in code.
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 */
public class BlockNodeDescriptor extends ParseNodeDescriptor
{
	/**
	 * My slots of type {@link AvailObject}.
	 *
	 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
	 */
	public enum ObjectSlots
	{
		/**
		 * The block's tuple of argument declarations.
		 */
		ARGUMENTS_TUPLE,

		/**
		 * The tuple of statements contained in this block.
		 */
		STATEMENTS_TUPLE,

		/**
		 * The type this block is expected to return an instance of.
		 */
		RESULT_TYPE,

		/**
		 * A tuple of variables needed by this block.  This is set after the
		 * {@linkplain BlockNodeDescriptor block node} has already been
		 * created.
		 */
		NEEDED_VARIABLES
	}

	/**
	 * My slots of type {@link Integer int}.
	 *
	 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
	 */
	public enum IntegerSlots
	{
		/**
		 * The {@link Primitive primitive} number to invoke for this block.
		 */
		PRIMITIVE
	}

	/**
	 * Setter for field argumentsTuple.
	 */
	@Override
	public void o_ArgumentsTuple (
		final AvailObject object,
		final AvailObject argumentsTuple)
	{
		object.objectSlotPut(ObjectSlots.ARGUMENTS_TUPLE, argumentsTuple);
	}

	/**
	 * Getter for field argumentsTuple.
	 */
	@Override
	public AvailObject o_ArgumentsTuple (
		final AvailObject object)
	{
		return object.objectSlot(ObjectSlots.ARGUMENTS_TUPLE);
	}

	/**
	 * Setter for field statementsTuple.
	 */
	@Override
	public void o_StatementsTuple (
		final AvailObject object,
		final AvailObject statementsTuple)
	{
		object.objectSlotPut(ObjectSlots.STATEMENTS_TUPLE, statementsTuple);
	}

	/**
	 * Getter for field statementsTuple.
	 */
	@Override
	public AvailObject o_StatementsTuple (
		final AvailObject object)
	{
		return object.objectSlot(ObjectSlots.STATEMENTS_TUPLE);
	}

	/**
	 * Setter for field resultType.
	 */
	@Override
	public void o_ResultType (
		final AvailObject object,
		final AvailObject resultType)
	{
		object.objectSlotPut(ObjectSlots.RESULT_TYPE, resultType);
	}

	/**
	 * Getter for field resultType.
	 */
	@Override
	public AvailObject o_ResultType (
		final AvailObject object)
	{
		return object.objectSlot(ObjectSlots.RESULT_TYPE);
	}

	/**
	 * Setter for field neededVariables.
	 */
	@Override
	public void o_NeededVariables (
		final AvailObject object,
		final AvailObject neededVariables)
	{
		object.objectSlotPut(ObjectSlots.NEEDED_VARIABLES, neededVariables);
	}

	/**
	 * Getter for field neededVariables.
	 */
	@Override
	public AvailObject o_NeededVariables (
		final AvailObject object)
	{
		return object.objectSlot(ObjectSlots.NEEDED_VARIABLES);
	}

	/**
	 * Setter for field primitive.
	 */
	@Override
	public void o_Primitive (
		final AvailObject object,
		final int primitive)
	{
		object.integerSlotPut(IntegerSlots.PRIMITIVE, primitive);
	}

	/**
	 * Getter for field primitive.
	 */
	@Override
	public int o_Primitive (
		final AvailObject object)
	{
		return object.integerSlot(IntegerSlots.PRIMITIVE);
	}


	@Override
	public AvailObject o_Type (final AvailObject object)
	{
		return Types.blockNode.object();
	}


	@Override
	public AvailObject o_ExpressionType (final AvailObject object)
	{
		List<AvailObject> argumentTypes;
		argumentTypes = new ArrayList<AvailObject>(
				object.argumentsTuple().tupleSize());
		for (final AvailObject argDeclaration : object.argumentsTuple())
		{
			argumentTypes.add(argDeclaration.declaredType());
		}
		return ClosureTypeDescriptor.closureTypeForArgumentTypesReturnType(
			TupleDescriptor.mutableObjectFromArray(argumentTypes),
			object.resultType());
	}

	/**
	 * The expression '[expr]' has no effect, only a value.
	 */
	@Override
	public void o_EmitEffectOn (
		final AvailObject object,
		final AvailCodeGenerator codeGenerator)
	{
		return;
	}

	@Override
	public void o_EmitValueOn (
		final AvailObject object,
		final AvailCodeGenerator codeGenerator)
	{
		final AvailCodeGenerator newGenerator = new AvailCodeGenerator();
		final AvailObject compiledBlock = generate(object, newGenerator);
		if (object.neededVariables().tupleSize() == 0)
		{
			final AvailObject closure = ClosureDescriptor.create(
				compiledBlock,
				TupleDescriptor.empty());
			closure.makeImmutable();
			codeGenerator.emitPushLiteral(closure);
		}
		else
		{
			codeGenerator.emitCloseCode(
				compiledBlock,
				object.neededVariables());
		}
	}


	/**
	 * Evaluate the {@link Continuation1 block} for each of the {@link
	 * BlockNodeDescriptor Avail block's} declared entities, which includes
	 * arguments, locals, and labels.
	 *
	 * @param object
	 *        The Avail block node to scan.
	 * @param aBlock
	 *        What to do with each variable that the Avail block node defines.
	 */
	private void allLocallyDefinedVariablesDo (
		final AvailObject object,
		final Continuation1<AvailObject> aBlock)
	{
		for (final AvailObject argumentDeclaration : object.argumentsTuple())
		{
			aBlock.value(argumentDeclaration);
		}
		for (final AvailObject localDeclaration : locals(object))
		{
			aBlock.value(localDeclaration);
		}
		for (final AvailObject labelDeclaration : labels(object))
		{
			aBlock.value(labelDeclaration);
		}
	}


	/**
	 * Answer the labels present in this block's list of statements. There is
	 * either zero or one label, and it must be the first statement.
	 *
	 * @param object The block node to examine.
	 * @return A list of between zero and one labels.
	 */
	private static List<AvailObject> labels (final AvailObject object)
	{
		final List<AvailObject> labels = new ArrayList<AvailObject>(1);
		for (final AvailObject maybeLabel : object.statementsTuple())
		{
			if (maybeLabel.isInstanceOfSubtypeOf(Types.labelNode.object()))
			{
				labels.add(maybeLabel);
			}
		}
		return labels;
	}

	/**
	 * Answer the declarations of this block's local variables.  Do not include
	 * the label declaration if present, nor argument declarations.
	 *
	 * @param object The block node to examine.
	 * @return This block's local variable declarations.
	 */
	private static List<AvailObject> locals (final AvailObject object)
	{
		final List<AvailObject> locals = new ArrayList<AvailObject>(5);
		for (final AvailObject maybeLocal : object.statementsTuple())
		{
			if (maybeLocal.type().isSubtypeOf(Types.declarationNode.object())
				&& !maybeLocal.type().isSubtypeOf(Types.labelNode.object()))
			{
				locals.add(maybeLocal);
			}
		}
		return locals;
	}


	/**
	 * Answer an Avail compiled block compiled from the given block node, using
	 * the given {@link AvailCodeGenerator}.
	 *
	 * @param object The {@link BlockNodeDescriptor block node}.
	 * @param codeGenerator
	 *            A {@link AvailCodeGenerator code generator}
	 * @return An {@link AvailObject} of type {@link ClosureDescriptor closure}.
	 */
	public static AvailObject generate (
		final AvailObject object,
		final AvailCodeGenerator codeGenerator)
	{
		codeGenerator.startBlockWithArgumentsLocalsLabelsOuterVarsResultType(
			object.argumentsTuple(),
			locals(object),
			labels(object),
			object.neededVariables(),
			object.resultType());
		codeGenerator.stackShouldBeEmpty();
		codeGenerator.primitive(object.primitive());
		codeGenerator.stackShouldBeEmpty();
		final AvailObject statementsTuple = object.statementsTuple();
		final int statementsCount = statementsTuple.tupleSize();
		if (statementsCount == 0)
		{
			codeGenerator.emitPushLiteral(VoidDescriptor.voidObject());
		}
		else
		{
			for (int index = 1; index < statementsCount; index++)
			{
				statementsTuple.tupleAt(index).emitEffectOn(codeGenerator);
				codeGenerator.stackShouldBeEmpty();
			}
			final AvailObject lastStatement =
				statementsTuple.tupleAt(statementsCount);
			final AvailObject lastStatementType = lastStatement.type();
			if (lastStatementType.isSubtypeOf(Types.labelNode.object())
				|| lastStatementType.isSubtypeOf(Types.assignmentNode.object()))
			{
				// The block ends with the label declaration or an assignment.
				// Push the void object as the return value.
				lastStatement.emitEffectOn(codeGenerator);
				codeGenerator.emitPushLiteral(VoidDescriptor.voidObject());
			}
			else
			{
				lastStatement.emitValueOn(codeGenerator);
			}
		}
		return codeGenerator.endBlock();
	}

	/**
	 * Construct a new {@link BlockNodeDescriptor}.
	 *
	 * @param isMutable Whether my {@linkplain AvailObject instances} can
	 *                  change.
	 */
	public BlockNodeDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}

	/**
	 * The mutable {@link BlockNodeDescriptor}.
	 */
	private final static BlockNodeDescriptor mutable =
		new BlockNodeDescriptor(true);

	/**
	 * Answer the mutable {@link BlockNodeDescriptor}.
	 *
	 * @return The mutable {@link BlockNodeDescriptor}.
	 */
	public static BlockNodeDescriptor mutable ()
	{
		return mutable;
	}

	/**
	 * The immutable {@link BlockNodeDescriptor}.
	 */
	private final static BlockNodeDescriptor immutable =
		new BlockNodeDescriptor(false);

	/**
	 * Answer the immutable {@link BlockNodeDescriptor}.
	 *
	 * @return The immutable {@link BlockNodeDescriptor}.
	 */
	public static BlockNodeDescriptor immutable ()
	{
		return immutable;
	}

}
