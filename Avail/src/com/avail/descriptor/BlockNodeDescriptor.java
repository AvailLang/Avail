/**
 * BlockNodeDescriptor.java
 * Copyright © 1993-2013, Mark van Gulik and Todd L Smith.
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
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class BlockNodeDescriptor
extends ParseNodeDescriptor
{
	/**
	 * My slots of type {@linkplain Integer int}.
	 */
	public enum IntegerSlots
	implements IntegerSlotsEnum
	{
		/**
		 * The {@linkplain Primitive primitive} number to invoke for this block.
		 * This is not the {@link Enum#ordinal()} of the primitive, but rather
		 * its {@link Primitive#primitiveNumber}.
		 */
		@EnumField(
			describedBy=Primitive.class,
			lookupMethodName="byPrimitiveNumberOrNull")
		PRIMITIVE,

		/**
		 * The line number on which this block starts.
		 */
		STARTING_LINE_NUMBER;
	}

	/**
	 * My slots of type {@link AvailObject}.
	 */
	public enum ObjectSlots
	implements ObjectSlotsEnum
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

	@Override boolean allowsImmutableToMutableReferenceInField (
		final AbstractSlotsEnum e)
	{
		return e == NEEDED_VARIABLES;
	}

	@Override @AvailMethod
	A_Tuple o_ArgumentsTuple (final AvailObject object)
	{
		return object.slot(ARGUMENTS_TUPLE);
	}

	@Override @AvailMethod
	A_Tuple o_StatementsTuple (final AvailObject object)
	{
		return object.slot(STATEMENTS_TUPLE);
	}

	@Override @AvailMethod
	AvailObject o_ResultType (final AvailObject object)
	{
		return object.slot(RESULT_TYPE);
	}

	@Override @AvailMethod
	AvailObject o_NeededVariables (final AvailObject object)
	{
		return object.mutableSlot(NEEDED_VARIABLES);
	}

	@Override @AvailMethod
	void o_NeededVariables (
		final AvailObject object,
		final A_Tuple neededVariables)
	{
		object.setMutableSlot(NEEDED_VARIABLES, neededVariables);
	}

	@Override @AvailMethod
	A_Set o_DeclaredExceptions (final AvailObject object)
	{
		return object.slot(DECLARED_EXCEPTIONS);
	}

	@Override @AvailMethod
	int o_Primitive (final AvailObject object)
	{
		return object.slot(PRIMITIVE);
	}

	@Override @AvailMethod
	int o_StartingLineNumber (final AvailObject object)
	{
		return object.slot(STARTING_LINE_NUMBER);
	}

	@Override @AvailMethod
	A_Type o_ExpressionType (final AvailObject object)
	{
		List<AvailObject> argumentTypes;
		argumentTypes = new ArrayList<AvailObject>(
				object.argumentsTuple().tupleSize());
		for (final A_BasicObject argDeclaration : object.argumentsTuple())
		{
			argumentTypes.add(argDeclaration.declaredType());
		}
		return FunctionTypeDescriptor.create(
			TupleDescriptor.fromList(argumentTypes),
			object.resultType());
	}

	/**
	 * The expression "[expr]" has no effect, only a value.
	 */
	@Override @AvailMethod
	void o_EmitEffectOn (
		final AvailObject object,
		final AvailCodeGenerator codeGenerator)
	{
		return;
	}

	@Override @AvailMethod
	void o_EmitValueOn (
		final AvailObject object,
		final AvailCodeGenerator codeGenerator)
	{
		final AvailCodeGenerator newGenerator = new AvailCodeGenerator(
			codeGenerator.module());
		final AvailObject compiledBlock = object.generate(newGenerator);
		if (object.neededVariables().tupleSize() == 0)
		{
			final A_Function function = FunctionDescriptor.create(
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
	int o_Hash (final AvailObject object)
	{
		return
			(((object.argumentsTuple().hash() * multiplier
				+ object.statementsTuple().hash()) * multiplier
				+ object.resultType().hash()) * multiplier
				+ object.neededVariables().hash()) * multiplier
				+ object.primitive()
			^ 0x05E6A04A;
	}

	@Override @AvailMethod
	boolean o_EqualsParseNode (
		final AvailObject object,
		final A_BasicObject aParseNode)
	{
		return object.kind().equals(aParseNode.kind())
			&& object.argumentsTuple().equals(aParseNode.argumentsTuple())
			&& object.statementsTuple().equals(aParseNode.statementsTuple())
			&& object.resultType().equals(aParseNode.resultType())
			&& object.neededVariables().equals(aParseNode.neededVariables())
			&& object.primitive() == aParseNode.primitive();
	}

	@Override
	ParseNodeKind o_ParseNodeKind (final AvailObject object)
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
	private static List<AvailObject> labels (final A_BasicObject object)
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
	private static List<AvailObject> locals (final A_BasicObject object)
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
		final AvailObject object,
		final Transformer1<AvailObject, AvailObject> aBlock)
	{
		A_Tuple arguments = object.argumentsTuple();
		for (int i = 1; i <= arguments.tupleSize(); i++)
		{
			arguments = arguments.tupleAtPuttingCanDestroy(
				i,
				aBlock.value(arguments.tupleAt(i)),
				true);
		}
		object.setSlot(ARGUMENTS_TUPLE, arguments);
		A_Tuple statements = object.statementsTuple();
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


	@Override @AvailMethod
	void o_ValidateLocally (
		final AvailObject object,
		final @Nullable A_BasicObject parent)
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
	 * @param object
	 *            The {@linkplain BlockNodeDescriptor block node}.
	 * @param codeGenerator
	 *            A {@linkplain AvailCodeGenerator code generator}
	 * @return
	 *            An {@link AvailObject} of type {@linkplain FunctionDescriptor
	 *            function}.
	 */
	@Override @AvailMethod
	AvailObject o_Generate (
		final AvailObject object,
		final AvailCodeGenerator codeGenerator)
	{
		codeGenerator.startBlock(
			object.argumentsTuple(),
			locals(object),
			labels(object),
			object.neededVariables(),
			object.resultType(),
			object.declaredExceptions(),
			object.slot(STARTING_LINE_NUMBER));
		codeGenerator.stackShouldBeEmpty();
		codeGenerator.primitive(object.primitive());
		codeGenerator.stackShouldBeEmpty();
		final A_Tuple statementsTuple = object.statementsTuple();
		final int statementsCount = statementsTuple.tupleSize();
		if (statementsCount == 0)
		{
			codeGenerator.emitPushLiteral(NilDescriptor.nil());
		}
		else
		{
			for (int index = 1; index < statementsCount; index++)
			{
				statementsTuple.tupleAt(index).emitEffectOn(codeGenerator);
				codeGenerator.stackShouldBeEmpty();
			}
			final A_BasicObject lastStatement =
				statementsTuple.tupleAt(statementsCount);
			final A_BasicObject lastStatementType = lastStatement.kind();
			if (lastStatementType.parseNodeKindIsUnder(LABEL_NODE)
				|| (lastStatementType.parseNodeKindIsUnder(ASSIGNMENT_NODE)
					&& object.resultType().equals(TOP.o())))
			{
				// Either the block 1) ends with the label declaration or
				// 2) is top-valued and ends with an assignment. Push the top
				// object as the return value.
				lastStatement.emitEffectOn(codeGenerator);
				codeGenerator.emitPushLiteral(NilDescriptor.nil());
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
	 * @param lineNumber
	 *            The line number on which the block starts.
	 * @return
	 *            A block node.
	 */
	public static AvailObject newBlockNode (
		final List<AvailObject> argumentsList,
		final int primitive,
		final List<AvailObject> statementsList,
		final A_Type resultType,
		final A_Set declaredExceptions,
		final int lineNumber)
	{
		return newBlockNode(
			TupleDescriptor.fromList(argumentsList),
			IntegerDescriptor.fromInt(primitive),
			TupleDescriptor.fromList(statementsList),
			resultType,
			declaredExceptions,
			lineNumber);
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
	 * @param lineNumber
	 *            The line number of the current module at which this block
	 *            begins.
	 * @return
	 *            A block node.
	 */
	public static AvailObject newBlockNode (
		final A_Tuple arguments,
		final A_Number primitive,
		final A_Tuple statements,
		final A_Type resultType,
		final A_Set declaredExceptions,
		final int lineNumber)
	{
		final List<AvailObject> flattenedStatements =
			new ArrayList<AvailObject>(statements.tupleSize() + 3);
		for (final A_BasicObject statement : statements)
		{
			statement.flattenStatementsInto(flattenedStatements);
		}
		// Remove useless statements that are just top literals, other than the
		// final statement.  Actually remove any bare literals, not just top.
		for (int index = flattenedStatements.size() - 2; index >= 0; index--)
		{
			final A_BasicObject statement = flattenedStatements.get(index);
			if (statement.isInstanceOfKind(LITERAL_NODE.mostGeneralType()))
			{
				flattenedStatements.remove(index);
			}
		}
		final AvailObject block = mutable.create();
		block.setSlot(ARGUMENTS_TUPLE, arguments);
		block.setSlot(PRIMITIVE, primitive.extractInt());
		block.setSlot(
			STATEMENTS_TUPLE,
			TupleDescriptor.fromList(flattenedStatements));
		block.setSlot(RESULT_TYPE, resultType);
		block.setSlot(NEEDED_VARIABLES, NilDescriptor.nil());
		block.setSlot(DECLARED_EXCEPTIONS, declaredExceptions);
		block.setSlot(STARTING_LINE_NUMBER, lineNumber);
		block.makeShared();
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
		final AvailObject blockNode)
	{
		treeDoWithParent(
			blockNode,
			new Continuation2<AvailObject, AvailObject>()
			{
				@Override
				public void value (
					final @Nullable AvailObject node,
					final @Nullable AvailObject parent)
				{
					assert node != null;
					node.validateLocally(parent);
				}
			},
			null);
		assert blockNode.neededVariables().tupleSize() == 0;
	}

	/**
	 * Figure out what outer variables will need to be captured when a function
	 * for me is built.
	 *
	 * @param object The current {@linkplain BlockNodeDescriptor block node}.
	 */
	private void collectNeededVariablesOfOuterBlocks (final AvailObject object)
	{
		final Set<AvailObject> neededDeclarations = new HashSet<AvailObject>();
		final Set<AvailObject> providedByMe = new HashSet<AvailObject>();
		providedByMe.addAll(allLocallyDefinedVariables(object));
		object.childrenDo(new Continuation1<AvailObject>()
		{
			@Override
			public void value (final @Nullable AvailObject node)
			{
				assert node != null;
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
		final A_Tuple argumentsTuple = object.argumentsTuple();
		final int argCount = argumentsTuple.tupleSize();
		final int primitive = object.primitive();
		final A_Tuple statementsTuple = object.statementsTuple();
		final int statementsSize = statementsTuple.tupleSize();
		@Nullable AvailObject explicitResultType = object.resultType();
		if (statementsSize >= 1
			&& statementsTuple.tupleAt(statementsSize).expressionType()
				.equals(explicitResultType))
		{
			explicitResultType = null;
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
		final Primitive prim = Primitive.byPrimitiveNumberOrNull(primitive);
		if (prim != null
			&& !prim.hasFlag(Flag.SpecialReturnConstant)
			&& !prim.hasFlag(Flag.SpecialReturnSoleArgument))
		{
			builder.append('\t');
			builder.append("Primitive ");
			builder.append(primitive);
			if (!prim.hasFlag(Flag.CannotFail))
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
			final A_BasicObject statement = statementsTuple.tupleAt(index);
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
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 */
	private BlockNodeDescriptor (final Mutability mutability)
	{
		super(mutability);
	}

	/** The mutable {@link BlockNodeDescriptor}. */
	private static final BlockNodeDescriptor mutable =
		new BlockNodeDescriptor(Mutability.MUTABLE);

	@Override
	BlockNodeDescriptor mutable ()
	{
		return mutable;
	}

	/** The shared {@link BlockNodeDescriptor}. */
	private static final BlockNodeDescriptor shared =
		new BlockNodeDescriptor(Mutability.SHARED);

	@Override
	BlockNodeDescriptor shared ()
	{
		return shared;
	}
}
