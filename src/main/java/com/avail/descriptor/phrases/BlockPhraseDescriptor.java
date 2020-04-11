/*
 * BlockPhraseDescriptor.java
 * Copyright © 1993-2020, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice, this
 *     list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice, this
 *     list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
 *
 *  * Neither the name of the copyright holder nor the names of the contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
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

package com.avail.descriptor.phrases;

import com.avail.annotations.AvailMethod;
import com.avail.annotations.EnumField;
import com.avail.compiler.AvailCodeGenerator;
import com.avail.descriptor.A_Module;
import com.avail.descriptor.representation.AvailObject;
import com.avail.descriptor.JavaCompatibility.IntegerSlotsEnumJava;
import com.avail.descriptor.JavaCompatibility.ObjectSlotsEnumJava;
import com.avail.descriptor.ModuleDescriptor;
import com.avail.descriptor.functions.A_Function;
import com.avail.descriptor.functions.A_RawFunction;
import com.avail.descriptor.functions.FunctionDescriptor;
import com.avail.descriptor.phrases.DeclarationPhraseDescriptor.DeclarationKind;
import com.avail.descriptor.representation.A_BasicObject;
import com.avail.descriptor.representation.AbstractSlotsEnum;
import com.avail.descriptor.representation.BitField;
import com.avail.descriptor.representation.Mutability;
import com.avail.descriptor.sets.A_Set;
import com.avail.descriptor.tokens.A_Token;
import com.avail.descriptor.tuples.A_Tuple;
import com.avail.descriptor.tuples.TupleDescriptor;
import com.avail.descriptor.types.A_Type;
import com.avail.descriptor.types.PhraseTypeDescriptor.PhraseKind;
import com.avail.descriptor.types.TypeTag;
import com.avail.exceptions.AvailRuntimeException;
import com.avail.interpreter.Primitive;
import com.avail.interpreter.Primitive.Flag;
import com.avail.serialization.SerializerOperation;
import com.avail.utility.evaluation.Continuation1NotNull;
import com.avail.utility.evaluation.Transformer1;
import com.avail.utility.json.JSONWriter;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import static com.avail.descriptor.representation.AvailObject.multiplier;
import static com.avail.descriptor.NilDescriptor.nil;
import static com.avail.descriptor.functions.FunctionDescriptor.createFunction;
import static com.avail.descriptor.phrases.BlockPhraseDescriptor.IntegerSlots.PRIMITIVE;
import static com.avail.descriptor.phrases.BlockPhraseDescriptor.IntegerSlots.STARTING_LINE_NUMBER;
import static com.avail.descriptor.phrases.BlockPhraseDescriptor.ObjectSlots.*;
import static com.avail.descriptor.phrases.DeclarationPhraseDescriptor.DeclarationKind.MODULE_CONSTANT;
import static com.avail.descriptor.phrases.DeclarationPhraseDescriptor.DeclarationKind.MODULE_VARIABLE;
import static com.avail.descriptor.tuples.ObjectTupleDescriptor.tupleFromList;
import static com.avail.descriptor.tuples.TupleDescriptor.emptyTuple;
import static com.avail.descriptor.types.FunctionTypeDescriptor.functionType;
import static com.avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.*;
import static com.avail.exceptions.AvailErrorCode.E_BLOCK_MUST_NOT_CONTAIN_OUTERS;
import static com.avail.utility.Strings.newlineTab;
import static com.avail.utility.evaluation.Combinator.recurse;

/**
 * My instances represent occurrences of blocks (functions) encountered in code.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class BlockPhraseDescriptor
extends PhraseDescriptor
{
	/**
	 * My slots of type {@linkplain Integer int}.
	 */
	public enum IntegerSlots implements IntegerSlotsEnumJava
	{
		/**
		 * A slot containing multiple {@link BitField}s.
		 */
		PRIMITIVE_AND_STARTING_LINE_NUMBER;

		/**
		 * The {@linkplain Primitive primitive} number to invoke for this block.
		 * This is not the {@link Enum#ordinal()} of the primitive, but rather
		 * its {@link Primitive#getPrimitiveNumber()}. The numbering is
		 * ephemeral, and is not serialized or accessible within Avail code.
		 */
		@EnumField(
			describedBy=Primitive.class,
			lookupMethodName="byPrimitiveNumberOrNull")
		public static final BitField PRIMITIVE = new BitField(
			PRIMITIVE_AND_STARTING_LINE_NUMBER,
			0,
			32);

		/**
		 * The line number on which this block starts.
		 */
		public static final BitField STARTING_LINE_NUMBER = new BitField(
			PRIMITIVE_AND_STARTING_LINE_NUMBER,
			32,
			32);
	}

	/**
	 * My slots of type {@link AvailObject}.
	 */
	public enum ObjectSlots implements ObjectSlotsEnumJava
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
		 * {@linkplain BlockPhraseDescriptor block phrase} has already been
		 * created.
		 */
		NEEDED_VARIABLES,

		/**
		 * The block's set of exception types that may be raised.  This set
		 * <em>has not yet been normalized</em> (e.g., removing types that are
		 * subtypes of types that are also present in the set).
		 */
		DECLARED_EXCEPTIONS,

		/**
		 * The tuple of tokens forming this block phrase, if any.
		 */
		TOKENS;
	}

	@Override
	public void printObjectOnAvoidingIndent (
		final AvailObject object,
		final StringBuilder builder,
		final IdentityHashMap<A_BasicObject, Void> recursionMap,
		final int indent)
	{
		// Optimize for one-liners...
		final A_Tuple argumentsTuple = object.argumentsTuple();
		final int argCount = argumentsTuple.tupleSize();
		final @Nullable Primitive primitive = object.primitive();
		final A_Tuple statementsTuple = object.statementsTuple();
		final int statementsSize = statementsTuple.tupleSize();
		@Nullable A_Type explicitResultType = object.resultType();
		if (statementsSize >= 1
			&& statementsTuple.tupleAt(statementsSize).expressionType()
				.equals(explicitResultType))
		{
			explicitResultType = null;
		}
		@Nullable A_Set declaredExceptions = object.declaredExceptions();
		if (declaredExceptions.setSize() == 0)
		{
			declaredExceptions = null;
		}
		final boolean endsWithStatement = statementsSize < 1
			|| statementsTuple.tupleAt(statementsSize).expressionType().isTop();
		if (argCount == 0
			&& primitive == null
			&& statementsSize == 1
			&& explicitResultType == null
			&& declaredExceptions == null)
		{
			// See if the lone statement fits on a line.
			final StringBuilder tempBuilder = new StringBuilder();
			statementsTuple.tupleAt(1).printOnAvoidingIndent(
				tempBuilder,
				recursionMap,
				indent);
			if (tempBuilder.indexOf("\n") == -1
				&& tempBuilder.length() < 100)
			{
				builder.append('[');
				builder.append(tempBuilder);
				if (endsWithStatement)
				{
					builder.append(';');
				}
				builder.append(']');
				return;
			}
		}

		// Use multiple lines instead...
		builder.append('[');
		boolean wroteAnything = false;
		if (argCount > 0)
		{
			wroteAnything = true;
			for (int argIndex = 1; argIndex <= argCount; argIndex++)
			{
				newlineTab(builder, indent);
				argumentsTuple.tupleAt(argIndex).printOnAvoidingIndent(
					builder, recursionMap, indent);
				if (argIndex < argCount)
				{
					builder.append(',');
				}
			}
			newlineTab(builder, indent - 1);
			builder.append('|');
		}
		boolean skipFailureDeclaration = false;
		if (primitive != null && !primitive.hasFlag(Flag.SpecialForm))
		{
			wroteAnything = true;
			newlineTab(builder, indent);
			builder.append("Primitive ");
			builder.append(primitive.name());
			if (!primitive.hasFlag(Flag.CannotFail))
			{
				builder.append(" (");
				statementsTuple.tupleAt(1).printOnAvoidingIndent(
					builder, recursionMap, indent);
				builder.append(')');
				skipFailureDeclaration = true;
			}
			builder.append(';');
		}
		for (int index = 1; index <= statementsSize; index++)
		{
			final A_Phrase statement = statementsTuple.tupleAt(index);
			if (skipFailureDeclaration)
			{
				assert statement.isInstanceOf(
					DECLARATION_PHRASE.mostGeneralType());
				skipFailureDeclaration = false;
			}
			else
			{
				wroteAnything = true;
				newlineTab(builder, indent);
				statement.printOnAvoidingIndent(
					builder, recursionMap, indent);
				if (index < statementsSize || endsWithStatement)
				{
					builder.append(';');
				}
			}
		}
		if (wroteAnything)
		{
			newlineTab(builder, indent - 1);
		}
		builder.append(']');
		if (explicitResultType != null)
		{
			builder.append(" : ");
			builder.append(explicitResultType);
		}
		if (declaredExceptions != null)
		{
			builder.append(" ^ ");
			builder.append(declaredExceptions);
		}
	}

	@Override
	protected boolean allowsImmutableToMutableReferenceInField (
		final AbstractSlotsEnum e)
	{
		return e == NEEDED_VARIABLES;
	}

	@Override @AvailMethod
	public A_Tuple o_ArgumentsTuple (final AvailObject object)
	{
		return object.slot(ARGUMENTS_TUPLE);
	}

	@Override @AvailMethod
	public void o_ChildrenDo (
		final AvailObject object,
		final Continuation1NotNull<A_Phrase> action)
	{
		for (final AvailObject argument : object.argumentsTuple())
		{
			action.value(argument);
		}
		for (final AvailObject statement : object.statementsTuple())
		{
			action.value(statement);
		}
	}

	@Override @AvailMethod
	public void o_ChildrenMap (
		final AvailObject object,
		final Transformer1<A_Phrase, A_Phrase> transformer)
	{
		A_Tuple arguments = object.argumentsTuple();
		for (int i = 1; i <= arguments.tupleSize(); i++)
		{
			arguments = arguments.tupleAtPuttingCanDestroy(
				i, transformer.valueNotNull(arguments.tupleAt(i)), true);
		}
		object.setSlot(ARGUMENTS_TUPLE, arguments);
		A_Tuple statements = object.statementsTuple();
		for (int i = 1; i <= statements.tupleSize(); i++)
		{
			statements = statements.tupleAtPuttingCanDestroy(
				i, transformer.valueNotNull(statements.tupleAt(i)), true);
		}
		object.setSlot(STATEMENTS_TUPLE, statements);
	}

	@Override @AvailMethod
	public A_Set o_DeclaredExceptions (final AvailObject object)
	{
		return object.slot(DECLARED_EXCEPTIONS);
	}

	/**
	 * The expression "[expr]" has no effect, only a value.
	 */
	@Override @AvailMethod
	public void o_EmitEffectOn (
		final AvailObject object,
		final AvailCodeGenerator codeGenerator)
	{
		// No effect.
	}

	@Override @AvailMethod
	public void o_EmitValueOn (
		final AvailObject object,
		final AvailCodeGenerator codeGenerator)
	{
		final A_RawFunction compiledBlock =
			object.generateInModule(codeGenerator.getModule());
		if (object.neededVariables().tupleSize() == 0)
		{
			final A_Function function =
				createFunction(compiledBlock, emptyTuple());
			function.makeImmutable();
			codeGenerator.emitPushLiteral(object.tokens(), function);
		}
		else
		{
			codeGenerator.emitCloseCode(
				object.tokens(),
				compiledBlock,
				object.neededVariables());
		}
	}

	@Override @AvailMethod
	public boolean o_EqualsPhrase (
		final AvailObject object,
		final A_Phrase aPhrase)
	{
		return !aPhrase.isMacroSubstitutionNode()
			&& object.phraseKind().equals(aPhrase.phraseKind())
			&& object.argumentsTuple().equals(aPhrase.argumentsTuple())
			&& object.statementsTuple().equals(aPhrase.statementsTuple())
			&& object.resultType().equals(aPhrase.resultType())
			&& object.neededVariables().equals(aPhrase.neededVariables())
			&& object.primitive() == aPhrase.primitive();
	}

	@Override @AvailMethod
	public A_Type o_ExpressionType (final AvailObject object)
	{
		final List<A_Type> argumentTypes =
			new ArrayList<>(object.argumentsTuple().tupleSize());
		for (final A_Phrase argDeclaration : object.argumentsTuple())
		{
			argumentTypes.add(argDeclaration.declaredType());
		}
		return
			functionType(tupleFromList(argumentTypes), object.resultType());
	}

	/**
	 * Answer an Avail compiled block compiled from the given block phrase,
	 * using the given {@link AvailCodeGenerator}.
	 *
	 * @param object
	 *        The block phrase.
	 * @param module
	 *        The {@linkplain ModuleDescriptor module} which is intended to hold
	 *        the resulting code.
	 * @return An {@link AvailObject} of type {@linkplain FunctionDescriptor
	 *         function}.
	 */
	@Override @AvailMethod
	public A_RawFunction o_GenerateInModule (
		final AvailObject object,
		final A_Module module)
	{
		return AvailCodeGenerator.Companion.generateFunction(module, object);
	}

	@Override @AvailMethod
	public int o_Hash (final AvailObject object)
	{
		final @Nullable Primitive prim = object.primitive();
		int h = object.argumentsTuple().hash();
		h = h * multiplier + object.argumentsTuple().hash();
		h = h * multiplier + object.statementsTuple().hash();
		h = h * multiplier + object.resultType().hash();
		h = h * multiplier + object.neededVariables().hash();
		h = h * multiplier + (prim == null ? 0 : prim.name().hashCode());
		h = h * multiplier ^ 0x05E6A04A;
		return h;
	}

	@Override @AvailMethod
	public A_Tuple o_NeededVariables (final AvailObject object)
	{
		return object.mutableSlot(NEEDED_VARIABLES);
	}

	@Override @AvailMethod
	public void o_NeededVariables (
		final AvailObject object,
		final A_Tuple neededVariables)
	{
		object.setMutableSlot(NEEDED_VARIABLES, neededVariables);
	}

	@Override
	public PhraseKind o_PhraseKind (final AvailObject object)
	{
		return BLOCK_PHRASE;
	}

	@Override @AvailMethod
	public @Nullable Primitive o_Primitive (final AvailObject object)
	{
		return Primitive.Companion.byNumber(object.slot(PRIMITIVE));
	}

	@Override @AvailMethod
	public A_Type o_ResultType (final AvailObject object)
	{
		return object.slot(RESULT_TYPE);
	}

	@Override
	public SerializerOperation o_SerializerOperation (
		final AvailObject object)
	{
		return SerializerOperation.BLOCK_PHRASE;
	}

	@Override @AvailMethod
	public int o_StartingLineNumber (final AvailObject object)
	{
		return object.slot(STARTING_LINE_NUMBER);
	}

	@Override @AvailMethod
	public A_Tuple o_StatementsTuple (final AvailObject object)
	{
		return object.slot(STATEMENTS_TUPLE);
	}

	@Override
	public void o_StatementsDo (
		final AvailObject object,
		final Continuation1NotNull<A_Phrase> continuation)
	{
		throw unsupportedOperationException();
	}

	@Override
	public A_Tuple o_Tokens (final AvailObject object)
	{
		return object.slot(TOKENS);
	}

	@Override @AvailMethod
	public void o_ValidateLocally (
		final AvailObject object,
		final @Nullable A_Phrase parent)
	{
		// Make sure our neededVariables list has up-to-date information about
		// the outer variables that are accessed in me, because they have to be
		// captured when a function is made for me.

		collectNeededVariablesOfOuterBlocks(object);
	}

	@Override
	public void o_WriteSummaryTo (
		final AvailObject object, final JSONWriter writer)
	{
		writer.startObject();
		writer.write("kind");
		writer.write("block phrase");
		final @Nullable Primitive primitive = object.primitive();
		writer.write("primitive");
		writer.write(primitive == null ? "" : primitive.name());
		writer.write("starting line");
		writer.write(object.slot(STARTING_LINE_NUMBER));
		writer.write("arguments");
		object.slot(ARGUMENTS_TUPLE).writeSummaryTo(writer);
		writer.write("statements");
		object.slot(STATEMENTS_TUPLE).writeSummaryTo(writer);
		writer.write("result type");
		object.slot(RESULT_TYPE).writeSummaryTo(writer);
		writer.write("needed variables");
		object.slot(NEEDED_VARIABLES).writeSummaryTo(writer);
		writer.write("declared exceptions");
		object.slot(DECLARED_EXCEPTIONS).writeSummaryTo(writer);
		writer.endObject();
	}

	@Override
	public void o_WriteTo (final AvailObject object, final JSONWriter writer)
	{
		writer.startObject();
		writer.write("kind");
		writer.write("block phrase");
		final @Nullable Primitive primitive = object.primitive();
		writer.write("primitive");
		writer.write(primitive == null ? "" : primitive.name());
		writer.write("starting line");
		writer.write(object.slot(STARTING_LINE_NUMBER));
		writer.write("arguments");
		object.slot(ARGUMENTS_TUPLE).writeTo(writer);
		writer.write("statements");
		object.slot(STATEMENTS_TUPLE).writeTo(writer);
		writer.write("result type");
		object.slot(RESULT_TYPE).writeTo(writer);
		writer.write("needed variables");
		object.slot(NEEDED_VARIABLES).writeTo(writer);
		writer.write("declared exceptions");
		object.slot(DECLARED_EXCEPTIONS).writeTo(writer);
		writer.write("tokens");
		object.slot(TOKENS).writeTo(writer);
		writer.endObject();
	}

	/**
	 * Return a {@link List} of all {@linkplain DeclarationPhraseDescriptor
	 * declaration phrases} defined by this block. This includes arguments,
	 * locals, constants, and labels.
	 *
	 * @param object The Avail block phrase to scan.
	 * @return The list of declarations.
	 */
	private static List<A_Phrase> allLocallyDefinedVariables (
		final A_Phrase object)
	{
		final List<A_Phrase> declarations = new ArrayList<>(10);
		for (final A_Phrase argumentDeclaration : object.argumentsTuple())
		{
			declarations.add(argumentDeclaration);
		}
		declarations.addAll(locals(object));
		declarations.addAll(constants(object));
		declarations.addAll(labels(object));
		return declarations;
	}

	/**
	 * Answer the labels present in this block's list of statements. There is
	 * either zero or one label, and it must be the first statement.
	 *
	 * @param object The block phrase to examine.
	 * @return A list of between zero and one labels.
	 */
	public static List<A_Phrase> labels (final A_Phrase object)
	{
		final List<A_Phrase> labels = new ArrayList<>(1);
		for (final AvailObject phrase : object.statementsTuple())
		{
			if (phrase.isInstanceOfKind(LABEL_PHRASE.mostGeneralType()))
			{
				assert phrase.declarationKind() == DeclarationKind.LABEL;
				labels.add(phrase);
			}
		}
		return labels;
	}

	/**
	 * Answer the declarations of this block's local variables.  Do not include
	 * the label declaration if present, nor argument declarations, nor local
	 * constants.
	 *
	 * <p>Include the primitive failure reason variable, if present.</p>
	 *
	 * @param object The block phrase to examine.
	 * @return This block's local variable declarations.
	 */
	public static List<A_Phrase> locals (final A_Phrase object)
	{
		final List<A_Phrase> locals = new ArrayList<>(5);
		for (final A_Phrase phrase : object.statementsTuple())
		{
			if (phrase.isInstanceOfKind(DECLARATION_PHRASE.mostGeneralType()))
			{
				final DeclarationKind kind = phrase.declarationKind();
				if (kind == DeclarationKind.LOCAL_VARIABLE
					|| kind == DeclarationKind.PRIMITIVE_FAILURE_REASON)
				{
					locals.add(phrase);
				}
			}
		}
		return locals;
	}

	/**
	 * Answer the declarations of this block's local constants.  Do not include
	 * the label declaration if present, nor argument declarations, nor local
	 * variables.
	 *
	 * @param object The block phrase to examine.
	 * @return This block's local constant declarations.
	 */
	public static List<A_Phrase> constants (final A_Phrase object)
	{
		final List<A_Phrase> constants = new ArrayList<>(5);
		for (final A_Phrase phrase : object.statementsTuple())
		{
			if (phrase.isInstanceOfKind(DECLARATION_PHRASE.mostGeneralType())
				&& phrase.declarationKind()
					== DeclarationKind.LOCAL_CONSTANT)
			{
				constants.add(phrase);
			}
		}
		return constants;
	}

	/**
	 * Construct a block phrase.
	 *
	 * @param arguments
	 *        The {@linkplain TupleDescriptor tuple} of {@linkplain
	 *        DeclarationPhraseDescriptor argument declarations}.
	 * @param primitive
	 *        The index of the primitive that the resulting block will invoke.
	 * @param statements
	 *        The {@link A_Tuple tuple} of statement {@linkplain
	 *        PhraseDescriptor phrases}.
	 * @param resultType
	 *        The {@link A_Type type} that will be returned by the block.
	 * @param declaredExceptions
	 *        The {@link A_Set set} of exception types that may be raised by
	 *        this block.  <em>This is not yet normalized.</em>
	 * @param lineNumber
	 *        The line number in the current module at which this block begins.
	 * @param tokens
	 *        The {@link A_Tuple} of {@link A_Token}s contributing to this block
	 *        phrase.
	 * @return A block phrase.
	 */
	public static AvailObject newBlockNode (
		final A_Tuple arguments,
		final int primitive,
		final A_Tuple statements,
		final A_Type resultType,
		final A_Set declaredExceptions,
		final int lineNumber,
		final A_Tuple tokens)
	{
		final List<A_Phrase> flattenedStatements =
			new ArrayList<>(statements.tupleSize() + 3);
		for (final A_Phrase statement : statements)
		{
			statement.flattenStatementsInto(flattenedStatements);
		}
		// Remove useless statements that are just top literals, other than the
		// final statement.  Actually remove any bare literals, not just top.
		for (int index = flattenedStatements.size() - 2; index >= 0; index--)
		{
			final A_BasicObject statement = flattenedStatements.get(index);
			if (statement.isInstanceOfKind(LITERAL_PHRASE.mostGeneralType()))
			{
				flattenedStatements.remove(index);
			}
		}
		final AvailObject block = mutable.create();
		block.setSlot(ARGUMENTS_TUPLE, arguments);
		block.setSlot(PRIMITIVE, primitive);
		block.setSlot(STATEMENTS_TUPLE, tupleFromList(flattenedStatements));
		block.setSlot(RESULT_TYPE, resultType);
		block.setSlot(NEEDED_VARIABLES, nil);
		block.setSlot(DECLARED_EXCEPTIONS, declaredExceptions);
		block.setSlot(TOKENS, tokens);
		block.setSlot(STARTING_LINE_NUMBER, lineNumber);
		block.makeShared();
		return block;
	}

	/**
	 * Ensure that the block phrase is valid.  Throw an appropriate exception if
	 * it is not.
	 *
	 * @param blockNode
	 *        The block phrase to validate.
	 */
	public static void recursivelyValidate (
		final A_Phrase blockNode)
	{
		treeDoWithParent(blockNode, A_Phrase::validateLocally, null);
		if (blockNode.neededVariables().tupleSize() != 0)
		{
			throw new AvailRuntimeException(E_BLOCK_MUST_NOT_CONTAIN_OUTERS);
		}
	}

	/**
	 * Figure out what outer variables will need to be captured when a function
	 * for me is built.
	 *
	 * @param object The block phrase to analyze.
	 */
	private static void collectNeededVariablesOfOuterBlocks (
		final A_Phrase object)
	{
		final Set<A_Phrase> providedByMe =
			new HashSet<>(allLocallyDefinedVariables(object));
		final Set<A_Phrase> neededDeclarationsSet = new HashSet<>();
		final List<A_Phrase> neededDeclarations = new ArrayList<>();
		recurse(
			object,
			(parent, again) ->
				parent.childrenDo(child -> {
					if (child.phraseKindIsUnder(BLOCK_PHRASE))
					{
						for (final A_Phrase declaration :
							child.neededVariables())
						{
							if (!providedByMe.contains(declaration)
								&& !neededDeclarationsSet.contains(declaration))
							{
								neededDeclarationsSet.add(declaration);
								neededDeclarations.add(declaration);
							}
						}
					}
					else if (child.phraseKindIsUnder(VARIABLE_USE_PHRASE))
					{
						final A_Phrase declaration = child.declaration();
						if (!providedByMe.contains(declaration)
							&& declaration.declarationKind() != MODULE_VARIABLE
							&& declaration.declarationKind() != MODULE_CONSTANT
							&& !neededDeclarationsSet.contains(declaration))
						{
							neededDeclarationsSet.add(declaration);
							neededDeclarations.add(declaration);
						}
						// Avoid visiting the declaration explicitly, otherwise
						// uses of declarations that have initializations will
						// cause variables used in those initializations to
						// accidentally be captured as well.
					}
					else
					{
						again.value(child);
					}
				}));
		object.neededVariables(tupleFromList(neededDeclarations));
	}

	/**
	 * Construct a new {@code BlockPhraseDescriptor}.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 */
	private BlockPhraseDescriptor (final Mutability mutability)
	{
		super(
			mutability,
			TypeTag.BLOCK_PHRASE_TAG,
			ObjectSlots.class,
			IntegerSlots.class);
	}

	/** The mutable {@link BlockPhraseDescriptor}. */
	private static final BlockPhraseDescriptor mutable =
		new BlockPhraseDescriptor(Mutability.MUTABLE);

	@Override
	public BlockPhraseDescriptor mutable ()
	{
		return mutable;
	}

	/** The shared {@link BlockPhraseDescriptor}. */
	private static final BlockPhraseDescriptor shared =
		new BlockPhraseDescriptor(Mutability.SHARED);

	@Override
	public BlockPhraseDescriptor shared ()
	{
		return shared;
	}
}
