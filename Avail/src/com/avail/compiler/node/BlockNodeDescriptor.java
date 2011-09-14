/**
 * com.avail.compiler/BlockNodeDescriptor.java
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

import static com.avail.compiler.node.DeclarationNodeDescriptor.DeclarationKind.*;
import static com.avail.descriptor.AvailObject.Multiplier;
import static com.avail.descriptor.TypeDescriptor.Types.*;
import java.util.*;
import com.avail.annotations.NotNull;
import com.avail.compiler.AvailCodeGenerator;
import com.avail.descriptor.*;
import com.avail.interpreter.*;
import com.avail.interpreter.levelTwo.L2Interpreter;
import com.avail.utility.*;

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
		NEEDED_VARIABLES,

		/**
		 * The block's set of exception types that may be raised.  This set
		 * <em>has not yet been normalized</em> (e.g., removing types that are
		 * subtypes of types that are also present in the set).
		 */
		CHECKED_EXCEPTIONS
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
		 * This is not the {@link Enum#ordinal()} of the primitive, but rather
		 * its {@link Primitive#primitiveNumber}.
		 */
		PRIMITIVE
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
	 * Getter for field statementsTuple.
	 */
	@Override
	public AvailObject o_StatementsTuple (
		final AvailObject object)
	{
		return object.objectSlot(ObjectSlots.STATEMENTS_TUPLE);
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
	 * Getter for field checkedExceptions.
	 */
	@Override
	public @NotNull AvailObject o_CheckedExceptions (
		final @NotNull AvailObject object)
	{
		return object.objectSlot(ObjectSlots.CHECKED_EXCEPTIONS);
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
	public boolean allowsImmutableToMutableReferenceInField (
		final @NotNull Enum<?> e)
	{
		return e == ObjectSlots.NEEDED_VARIABLES;
	}


	@Override
	public AvailObject o_Kind (final AvailObject object)
	{
		return BLOCK_NODE.o();
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
		return ClosureTypeDescriptor.create(
			TupleDescriptor.fromList(argumentTypes),
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
		final AvailObject compiledBlock = object.generate(newGenerator);
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

	@Override
	public int o_Hash (final AvailObject object)
	{
		return
			(((object.argumentsTuple().hash() * Multiplier
				+ object.statementsTuple().hash()) * Multiplier
				+ object.resultType().hash()) * Multiplier
				+ object.neededVariables().hash()) * Multiplier
				+ object.primitive()
			^ 0x05E6A04A;
	}

	@Override
	public boolean o_Equals (
		final AvailObject object,
		final AvailObject another)
	{
		return object.kind().equals(another.kind())
			&& object.argumentsTuple().equals(another.argumentsTuple())
			&& object.statementsTuple().equals(another.statementsTuple())
			&& object.resultType().equals(another.resultType())
			&& object.neededVariables().equals(another.neededVariables())
			&& object.primitive() == another.primitive();
	}


	/**
	 * Return a {@linkplain List list} of all {@linkplain
	 * DeclarationNodeDescriptor declaration nodes} defined by this block.
	 * This includes arguments, locals, and labels.
	 *
	 * @param object The Avail block node to scan.
	 * @return The list of declarations.
	 */
	private static List<AvailObject> allLocallyDefinedVariables (
		final AvailObject object)
	{
		final List<AvailObject> declarations = new ArrayList<AvailObject>(10);
		for (final AvailObject argumentDeclaration : object.argumentsTuple())
		{
			declarations.add(argumentDeclaration);
		}
		declarations.addAll(locals(object));
		declarations.addAll(labels(object));
		return declarations;
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
			if (maybeLabel.isInstanceOfKind(LABEL_NODE.o()))
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
			if (maybeLocal.isInstanceOfKind(DECLARATION_NODE.o())
				&& !maybeLocal.isInstanceOfKind(LABEL_NODE.o()))
			{
				locals.add(maybeLocal);
			}
		}
		return locals;
	}


	@Override
	public void o_ChildrenMap (
		final AvailObject object,
		final Transformer1<AvailObject, AvailObject> aBlock)
	{
		AvailObject arguments = object.argumentsTuple();
		for (int i = 1; i <= arguments.tupleSize(); i++)
		{
			arguments = arguments.tupleAtPuttingCanDestroy(
				i,
				aBlock.value(arguments.tupleAt(i)),
				true);
		}
		object.objectSlotPut(ObjectSlots.ARGUMENTS_TUPLE, arguments);
		AvailObject statements = object.statementsTuple();
		for (int i = 1; i <= statements.tupleSize(); i++)
		{
			statements = statements.tupleAtPuttingCanDestroy(
				i,
				aBlock.value(statements.tupleAt(i)),
				true);
		}
		object.objectSlotPut(ObjectSlots.STATEMENTS_TUPLE, statements);
	}


	@Override
	public void o_ChildrenDo (
		final AvailObject object,
		final Continuation1<AvailObject> aBlock)
	{
		for (final AvailObject argument : object.argumentsTuple())
		{
			aBlock.value(argument);
		}
		for (final AvailObject statement : object.statementsTuple())
		{
			aBlock.value(statement);
		}
	}


	@Override
	public void o_ValidateLocally (
		final AvailObject object,
		final AvailObject parent,
		final List<AvailObject> outerBlocks,
		final L2Interpreter anAvailInterpreter)
	{
		// Make sure our neededVariables list has up-to-date information about
		// the outer variables that are accessed in me, because they have to be
		// captured when a closure is made for me.

		collectNeededVariablesOfOuterBlocks(object);
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
	@Override
	public AvailObject o_Generate (
		final AvailObject object,
		final AvailCodeGenerator codeGenerator)
	{
		codeGenerator.startBlock(
			object.argumentsTuple(),
			locals(object),
			labels(object),
			object.neededVariables(),
			object.resultType(),
			object.checkedExceptions());
		codeGenerator.stackShouldBeEmpty();
		codeGenerator.primitive(object.primitive());
		codeGenerator.stackShouldBeEmpty();
		final AvailObject statementsTuple = object.statementsTuple();
		final int statementsCount = statementsTuple.tupleSize();
		if (statementsCount == 0)
		{
			codeGenerator.emitPushLiteral(NullDescriptor.nullObject());
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
			final AvailObject lastStatementType = lastStatement.kind();
			if (lastStatementType.isSubtypeOf(LABEL_NODE.o())
				|| lastStatementType.isSubtypeOf(ASSIGNMENT_NODE.o())
						&& object.resultType().equals(TOP.o()))
			{
				// Either the block 1) ends with the label declaration or
				// 2) is void-valued and ends with an assignment. Push the void
				// object as the return value.
				lastStatement.emitEffectOn(codeGenerator);
				codeGenerator.emitPushLiteral(NullDescriptor.nullObject());
			}
			else
			{
				lastStatement.emitValueOn(codeGenerator);
			}
		}
		return codeGenerator.endBlock();
	}

	/**
	 * Construct a {@linkplain BlockNodeDescriptor block node}.
	 *
	 * @param argumentsList
	 *            The {@linkplain TupleDescriptor tuple} of {@linkplain
	 *            DeclarationNodeDescriptor argument declarations}.
	 * @param primitive
	 *            The index of the primitive that the resulting block will
	 *            invoke.
	 * @param statementsList
	 *            The {@linkplain TupleDescriptor tuple} of statement
	 *            {@linkplain ParseNodeDescriptor nodes}.
	 * @param resultType
	 *            The {@linkplain TypeDescriptor type} that will be returned by
	 *            the block.
	 * @param checkedExceptions
	 *            The {@linkplain SetDescriptor set} of exception types that may
	 *            be raised by this block.  <em>This is not yet normalized.</em>
	 * @return
	 *            A block node.
	 */
	public static AvailObject newBlockNode (
		final List<AvailObject> argumentsList,
		final int primitive,
		final List<AvailObject> statementsList,
		final AvailObject resultType,
		final AvailObject checkedExceptions)
	{
		final List<AvailObject> flattenedStatements =
			new ArrayList<AvailObject>(statementsList.size() + 3);
		for (final AvailObject statement : statementsList)
		{
			statement.flattenStatementsInto(flattenedStatements);
		}
		// Remove useless statements that are just void literals, other than the
		// final statement.  Actually remove any bare literals, not just void.
		for (int index = flattenedStatements.size() - 2; index >= 0; index--)
		{
			final AvailObject statement = flattenedStatements.get(index);
			if (statement.isInstanceOfKind(LITERAL_NODE.o()))
			{
				flattenedStatements.remove(index);
			}
		}
		final AvailObject block = mutable().create();
		block.objectSlotPut(
			ObjectSlots.ARGUMENTS_TUPLE,
			TupleDescriptor.fromList(argumentsList));
		block.integerSlotPut(
			IntegerSlots.PRIMITIVE,
			primitive);
		block.objectSlotPut(
			ObjectSlots.STATEMENTS_TUPLE,
			TupleDescriptor.fromList(flattenedStatements));
		block.objectSlotPut(
			ObjectSlots.RESULT_TYPE,
			resultType);
		block.objectSlotPut(
			ObjectSlots.NEEDED_VARIABLES,
			NullDescriptor.nullObject());
		block.objectSlotPut(
			ObjectSlots.CHECKED_EXCEPTIONS,
			checkedExceptions);
		block.makeImmutable();
		return block;

	}

	/**
	 * Figure out what outer variables will need to be captured when a closure
	 * for me is built.
	 *
	 * @param object The current {@linkplain BlockNodeDescriptor block node}.
	 */
	private void collectNeededVariablesOfOuterBlocks (
		final AvailObject object)
	{
		final Set<AvailObject> neededDeclarations = new HashSet<AvailObject>();
		final Set<AvailObject> providedByMe = new HashSet<AvailObject>();
		providedByMe.addAll(allLocallyDefinedVariables(object));
		object.childrenDo(new Continuation1<AvailObject>()
		{
			@Override
			public void value (final AvailObject node)
			{
				assert !node.isInstanceOfKind(SEQUENCE_NODE.o())
				: "Sequence nodes should have been eliminated by this point";
				if (node.kind().equals(BLOCK_NODE.o()))
				{
					for (final AvailObject declaration : node.neededVariables())
					{
						if (!providedByMe.contains(declaration))
						{
							neededDeclarations.add(declaration);
						}
					}
					return;
				}
				node.childrenDo(this);
				if (node.kind().equals(VARIABLE_USE_NODE.o()))
				{
					final AvailObject declaration = node.declaration();
					if (!providedByMe.contains(declaration)
						&& declaration.declarationKind() != MODULE_VARIABLE
						&& declaration.declarationKind() != MODULE_CONSTANT)
					{
						neededDeclarations.add(declaration);
					}
				}
			}
		});
		object.neededVariables(
			TupleDescriptor.fromList(
				new ArrayList<AvailObject>(neededDeclarations)));
	}

	@Override
	public void printObjectOnAvoidingIndent (
		final AvailObject object,
		final StringBuilder builder,
		final List<AvailObject> recursionList,
		final int indent)
	{
		// Optimize for one-liners...
		final AvailObject argumentsTuple = object.argumentsTuple();
		final int argCount = argumentsTuple.tupleSize();
		final int primitive = object.primitive();
		final AvailObject statementsTuple = object.statementsTuple();
		if (argCount == 0
				&& primitive == 0
				&& statementsTuple.tupleSize() == 1)
		{
			builder.append('[');
			statementsTuple.tupleAt(1).printOnAvoidingIndent(
				builder,
				recursionList,
				indent + 1);
			builder.append(";]");
			return;
		}

		// Use multiple lines instead...
		builder.append('[');
		if (argCount > 0)
		{
			argumentsTuple.tupleAt(1).printOnAvoidingIndent(
				builder,
				recursionList,
				indent + 2);
			for (int argIndex = 2; argIndex <= argCount; argIndex++)
			{
				builder.append(", ");
				argumentsTuple.tupleAt(argIndex).printOnAvoidingIndent(
					builder,
					recursionList,
					indent + 2);
			}
			builder.append(" |");
		}
		builder.append('\n');
		for (int i = 1; i <= indent; i++)
		{
			builder.append('\t');
		}
		if (primitive != 0)
		{
			builder.append('\t');
			builder.append("Primitive ");
			builder.append(primitive);
			builder.append(";");
			builder.append('\n');
			for (int _count3 = 1; _count3 <= indent; _count3++)
			{
				builder.append('\t');
			}
		}
		for (final AvailObject statement : statementsTuple)
		{
			builder.append('\t');
			statement.printOnAvoidingIndent(builder, recursionList, indent + 2);
			builder.append(';');
			builder.append('\n');
			for (int _count5 = 1; _count5 <= indent; _count5++)
			{
				builder.append('\t');
			}
		}
		builder.append(']');
		if (object.resultType() != null)
		{
			builder.append(" : ");
			builder.append(object.resultType().toString());
		}
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
