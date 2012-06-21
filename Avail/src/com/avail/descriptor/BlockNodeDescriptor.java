/**
 * BlockNodeDescriptor.java
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

import static com.avail.descriptor.AvailObject.Multiplier;
import static com.avail.descriptor.BlockNodeDescriptor.ObjectSlots.*;
import static com.avail.descriptor.BlockNodeDescriptor.IntegerSlots.*;
import static com.avail.descriptor.DeclarationNodeDescriptor.DeclarationKind.*;
import static com.avail.descriptor.ParseNodeTypeDescriptor.ParseNodeKind.*;
import static com.avail.descriptor.TypeDescriptor.Types.*;
import java.util.*;
import com.avail.annotations.*;
import com.avail.compiler.AvailCodeGenerator;
import com.avail.descriptor.ParseNodeTypeDescriptor.ParseNodeKind;
import com.avail.interpreter.Primitive;
import com.avail.interpreter.Primitive.Flag;
import com.avail.utility.*;

/**
 * My instances represent occurrences of blocks (functions) encountered in code.
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
	public enum ObjectSlots implements ObjectSlotsEnum
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
		DECLARED_EXCEPTIONS
	}

	/**
	 * My slots of type {@linkplain Integer int}.
	 *
	 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
	 */
	public enum IntegerSlots implements IntegerSlotsEnum
	{
		/**
		 * The {@linkplain Primitive primitive} number to invoke for this block.
		 * This is not the {@link Enum#ordinal()} of the primitive, but rather
		 * its {@link Primitive#primitiveNumber}.
		 */
		@EnumField(
			describedBy=Primitive.class,
			lookupMethodName="byPrimitiveNumber")
		PRIMITIVE
	}

	/**
	 * Getter for field argumentsTuple.
	 */
	@Override @AvailMethod
	AvailObject o_ArgumentsTuple (
		final @NotNull AvailObject object)
	{
		return object.slot(ARGUMENTS_TUPLE);
	}

	/**
	 * Getter for field statementsTuple.
	 */
	@Override @AvailMethod
	AvailObject o_StatementsTuple (
		final @NotNull AvailObject object)
	{
		return object.slot(STATEMENTS_TUPLE);
	}

	/**
	 * Getter for field resultType.
	 */
	@Override @AvailMethod
	AvailObject o_ResultType (
		final @NotNull AvailObject object)
	{
		return object.slot(RESULT_TYPE);
	}

	/**
	 * Setter for field neededVariables.
	 */
	@Override @AvailMethod
	void o_NeededVariables (
		final @NotNull AvailObject object,
		final AvailObject neededVariables)
	{
		object.setSlot(NEEDED_VARIABLES, neededVariables);
	}

	/**
	 * Getter for field neededVariables.
	 */
	@Override @AvailMethod
	AvailObject o_NeededVariables (
		final @NotNull AvailObject object)
	{
		return object.slot(NEEDED_VARIABLES);
	}

	/**
	 * Getter for field checkedExceptions.
	 */
	@Override @AvailMethod
	@NotNull AvailObject o_DeclaredExceptions (
		final @NotNull AvailObject object)
	{
		return object.slot(DECLARED_EXCEPTIONS);
	}


	/**
	 * Getter for field primitive.
	 */
	@Override @AvailMethod
	int o_Primitive (
		final @NotNull AvailObject object)
	{
		return object.slot(PRIMITIVE);
	}


	@Override boolean allowsImmutableToMutableReferenceInField (
		final @NotNull AbstractSlotsEnum e)
	{
		return e == NEEDED_VARIABLES;
	}


	@Override @AvailMethod
	AvailObject o_ExpressionType (final @NotNull AvailObject object)
	{
		List<AvailObject> argumentTypes;
		argumentTypes = new ArrayList<AvailObject>(
				object.argumentsTuple().tupleSize());
		for (final AvailObject argDeclaration : object.argumentsTuple())
		{
			argumentTypes.add(argDeclaration.declaredType());
		}
		return FunctionTypeDescriptor.create(
			TupleDescriptor.fromCollection(argumentTypes),
			object.resultType());
	}

	/**
	 * The expression '[expr]' has no effect, only a value.
	 */
	@Override @AvailMethod
	void o_EmitEffectOn (
		final @NotNull AvailObject object,
		final AvailCodeGenerator codeGenerator)
	{
		return;
	}

	@Override @AvailMethod
	void o_EmitValueOn (
		final @NotNull AvailObject object,
		final AvailCodeGenerator codeGenerator)
	{
		final AvailCodeGenerator newGenerator = new AvailCodeGenerator();
		final AvailObject compiledBlock = object.generate(newGenerator);
		if (object.neededVariables().tupleSize() == 0)
		{
			final AvailObject function = FunctionDescriptor.create(
				compiledBlock,
				TupleDescriptor.empty());
			function.makeImmutable();
			codeGenerator.emitPushLiteral(function);
		}
		else
		{
			codeGenerator.emitCloseCode(
				compiledBlock,
				object.neededVariables());
		}
	}

	@Override @AvailMethod
	int o_Hash (final @NotNull AvailObject object)
	{
		return
			(((object.argumentsTuple().hash() * Multiplier
				+ object.statementsTuple().hash()) * Multiplier
				+ object.resultType().hash()) * Multiplier
				+ object.neededVariables().hash()) * Multiplier
				+ object.primitive()
			^ 0x05E6A04A;
	}

	@Override @AvailMethod
	boolean o_Equals (
		final @NotNull AvailObject object,
		final AvailObject another)
	{
		return object.kind().equals(another.kind())
			&& object.argumentsTuple().equals(another.argumentsTuple())
			&& object.statementsTuple().equals(another.statementsTuple())
			&& object.resultType().equals(another.resultType())
			&& object.neededVariables().equals(another.neededVariables())
			&& object.primitive() == another.primitive();
	}

	@Override
	ParseNodeKind o_ParseNodeKind (final @NotNull AvailObject object)
	{
		return ParseNodeKind.BLOCK_NODE;
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
		final @NotNull AvailObject object)
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
	private static List<AvailObject> labels (final @NotNull AvailObject object)
	{
		final List<AvailObject> labels = new ArrayList<AvailObject>(1);
		for (final AvailObject maybeLabel : object.statementsTuple())
		{
			if (maybeLabel.isInstanceOfKind(LABEL_NODE.mostGeneralType()))
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
	private static List<AvailObject> locals (final @NotNull AvailObject object)
	{
		final List<AvailObject> locals = new ArrayList<AvailObject>(5);
		for (final AvailObject maybeLocal : object.statementsTuple())
		{
			if (maybeLocal.isInstanceOfKind(DECLARATION_NODE.mostGeneralType())
				&& !maybeLocal.isInstanceOfKind(LABEL_NODE.mostGeneralType()))
			{
				locals.add(maybeLocal);
			}
		}
		return locals;
	}


	@Override @AvailMethod
	void o_ChildrenMap (
		final @NotNull AvailObject object,
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
		object.setSlot(ARGUMENTS_TUPLE, arguments);
		AvailObject statements = object.statementsTuple();
		for (int i = 1; i <= statements.tupleSize(); i++)
		{
			statements = statements.tupleAtPuttingCanDestroy(
				i,
				aBlock.value(statements.tupleAt(i)),
				true);
		}
		object.setSlot(STATEMENTS_TUPLE, statements);
	}


	@Override @AvailMethod
	void o_ChildrenDo (
		final @NotNull AvailObject object,
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


	@Override @AvailMethod
	void o_ValidateLocally (
		final @NotNull AvailObject object,
		final AvailObject parent)
	{
		// Make sure our neededVariables list has up-to-date information about
		// the outer variables that are accessed in me, because they have to be
		// captured when a function is made for me.

		collectNeededVariablesOfOuterBlocks(object);
	}


	/**
	 * Answer an Avail compiled block compiled from the given block node, using
	 * the given {@link AvailCodeGenerator}.
	 *
	 * @param object The {@linkplain BlockNodeDescriptor block node}.
	 * @param codeGenerator
	 *            A {@linkplain AvailCodeGenerator code generator}
	 * @return An {@link AvailObject} of type {@linkplain FunctionDescriptor function}.
	 */
	@Override @AvailMethod
	AvailObject o_Generate (
		final @NotNull AvailObject object,
		final AvailCodeGenerator codeGenerator)
	{
		codeGenerator.startBlock(
			object.argumentsTuple(),
			locals(object),
			labels(object),
			object.neededVariables(),
			object.resultType(),
			object.declaredExceptions());
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
			if (lastStatementType.parseNodeKindIsUnder(LABEL_NODE)
				|| (lastStatementType.parseNodeKindIsUnder(ASSIGNMENT_NODE)
					&& object.resultType().equals(TOP.o())))
			{
				// Either the block 1) ends with the label declaration or
				// 2) is top-valued and ends with an assignment. Push the top
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
	 *            The {@linkplain List list} of {@linkplain
	 *            DeclarationNodeDescriptor argument declarations}.
	 * @param primitive
	 *            The {@linkplain Primitive#primitiveNumber index} of the
	 *            primitive that the resulting block will invoke.
	 * @param statementsList
	 *            The {@linkplain List list} of statement
	 *            {@linkplain ParseNodeDescriptor nodes}.
	 * @param resultType
	 *            The {@linkplain TypeDescriptor type} that will be returned by
	 *            the block.
	 * @param declaredExceptions
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
		final AvailObject declaredExceptions)
	{
		return newBlockNode(
			TupleDescriptor.fromCollection(argumentsList),
			IntegerDescriptor.fromInt(primitive),
			TupleDescriptor.fromCollection(statementsList),
			resultType,
			declaredExceptions);
	}

	/**
	 * Construct a {@linkplain BlockNodeDescriptor block node}.
	 *
	 * @param arguments
	 *            The {@linkplain TupleDescriptor tuple} of {@linkplain
	 *            DeclarationNodeDescriptor argument declarations}.
	 * @param primitive
	 *            The index of the primitive that the resulting block will
	 *            invoke.
	 * @param statements
	 *            The {@linkplain TupleDescriptor tuple} of statement
	 *            {@linkplain ParseNodeDescriptor nodes}.
	 * @param resultType
	 *            The {@linkplain TypeDescriptor type} that will be returned by
	 *            the block.
	 * @param declaredExceptions
	 *            The {@linkplain SetDescriptor set} of exception types that may
	 *            be raised by this block.  <em>This is not yet normalized.</em>
	 * @return
	 *            A block node.
	 */
	public static AvailObject newBlockNode (
		final AvailObject arguments,
		final AvailObject primitive,
		final AvailObject statements,
		final AvailObject resultType,
		final AvailObject declaredExceptions)
	{
		final List<AvailObject> flattenedStatements =
			new ArrayList<AvailObject>(statements.tupleSize() + 3);
		for (final AvailObject statement : statements)
		{
			statement.flattenStatementsInto(flattenedStatements);
		}
		// Remove useless statements that are just top literals, other than the
		// final statement.  Actually remove any bare literals, not just top.
		for (int index = flattenedStatements.size() - 2; index >= 0; index--)
		{
			final AvailObject statement = flattenedStatements.get(index);
			if (statement.isInstanceOfKind(LITERAL_NODE.mostGeneralType()))
			{
				flattenedStatements.remove(index);
			}
		}
		final AvailObject block = mutable().create();
		block.setSlot(
			ARGUMENTS_TUPLE,
			arguments);
		block.setSlot(
			PRIMITIVE,
			primitive.extractInt());
		block.setSlot(
			STATEMENTS_TUPLE,
			TupleDescriptor.fromCollection(flattenedStatements));
		block.setSlot(
			RESULT_TYPE,
			resultType);
		block.setSlot(
			NEEDED_VARIABLES,
			NullDescriptor.nullObject());
		block.setSlot(
			DECLARED_EXCEPTIONS,
			declaredExceptions);
		block.makeImmutable();
		return block;
	}

	/**
	 * Ensure that the {@linkplain BlockNodeDescriptor block node} is valid.
	 * Throw an appropriate exception if it is not.
	 *
	 * @param blockNode
	 *            The {@linkplain BlockNodeDescriptor block node} to validate.
	 */
	public static void recursivelyValidate (
		final @NotNull AvailObject blockNode)
	{
		final List<AvailObject> blockStack = new ArrayList<AvailObject>(3);
		treeDoWithParent(
			blockNode,
			new Continuation3<AvailObject, AvailObject, List<AvailObject>>()
			{
				@Override
				public void value (
					final AvailObject node,
					final AvailObject parent,
					final List<AvailObject> blockNodes)
				{
					node.validateLocally(parent);
				}
			},
			null,
			blockStack);
		assert blockStack.isEmpty();
		assert blockNode.neededVariables().tupleSize() == 0;
	}

	/**
	 * Figure out what outer variables will need to be captured when a function
	 * for me is built.
	 *
	 * @param object The current {@linkplain BlockNodeDescriptor block node}.
	 */
	private void collectNeededVariablesOfOuterBlocks (
		final @NotNull AvailObject object)
	{
		final Set<AvailObject> neededDeclarations = new HashSet<AvailObject>();
		final Set<AvailObject> providedByMe = new HashSet<AvailObject>();
		providedByMe.addAll(allLocallyDefinedVariables(object));
		object.childrenDo(new Continuation1<AvailObject>()
		{
			@Override
			public void value (final AvailObject node)
			{
				assert !node.isInstanceOfKind(SEQUENCE_NODE.mostGeneralType())
				: "Sequence nodes should have been eliminated by this point";
				if (node.isInstanceOfKind(BLOCK_NODE.mostGeneralType()))
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
				if (node.isInstanceOfKind(VARIABLE_USE_NODE.mostGeneralType()))
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
			TupleDescriptor.fromCollection(
				new ArrayList<AvailObject>(neededDeclarations)));
	}

	@Override
	public void printObjectOnAvoidingIndent (
		final @NotNull AvailObject object,
		final StringBuilder builder,
		final List<AvailObject> recursionList,
		final int indent)
	{
		// Optimize for one-liners...
		final AvailObject argumentsTuple = object.argumentsTuple();
		final int argCount = argumentsTuple.tupleSize();
		final int primitive = object.primitive();
		final AvailObject statementsTuple = object.statementsTuple();
		final int statementsSize = statementsTuple.tupleSize();
		AvailObject explicitResultType = object.resultType();
		if (explicitResultType != null)
		{
			// Suppress redundant block type declaration.
			if (statementsSize >= 1
				&& statementsTuple.tupleAt(statementsSize).expressionType()
					.equals(explicitResultType))
			{
				explicitResultType = null;
			}
		}
		if (argCount == 0
				&& primitive == 0
				&& statementsSize == 1
				&& explicitResultType == null)
		{
			builder.append('[');
			statementsTuple.tupleAt(1).printOnAvoidingIndent(
				builder,
				recursionList,
				indent + 1);
			if (statementsTuple.tupleAt(1).expressionType().equals(TOP.o()))
			{
				builder.append(";");
			}
			builder.append("]");
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
		boolean skipFailureDeclaration = false;
		if (primitive != 0)
		{
			builder.append('\t');
			builder.append("Primitive ");
			builder.append(primitive);
			final Primitive primObject = Primitive.byPrimitiveNumber(primitive);
			if (!primObject.hasFlag(Flag.CannotFail))
			{
				builder.append(" (");
				statementsTuple.tupleAt(1).printOnAvoidingIndent(
					builder,
					recursionList,
					indent + 2);
				builder.append(")");
				skipFailureDeclaration = true;
			}
			builder.append(";");
			builder.append('\n');
			for (int i = 1; i <= indent; i++)
			{
				builder.append('\t');
			}
		}
		for (int index = 1; index <= statementsSize; index++)
		{
			final AvailObject statement = statementsTuple.tupleAt(index);
			if (skipFailureDeclaration)
			{
				assert statement.isInstanceOf(
					DECLARATION_NODE.mostGeneralType());
				skipFailureDeclaration = false;
			}
			else
			{
				builder.append('\t');
				statement.printOnAvoidingIndent(
					builder,
					recursionList,
					indent + 2);
				if (index < statementsSize
					|| statement.expressionType().equals(TOP.o()))
				{
					builder.append(';');
				}
				builder.append('\n');
				for (int i = 1; i <= indent; i++)
				{
					builder.append('\t');
				}
			}
		}
		builder.append(']');
		if (explicitResultType != null)
		{
			builder.append(" : ");
			builder.append(explicitResultType.toString());
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
	private static final BlockNodeDescriptor mutable =
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
	private static final BlockNodeDescriptor immutable =
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
