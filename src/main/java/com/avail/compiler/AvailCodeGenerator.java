/*
 * AvailCodeGenerator.java
 * Copyright © 1993-2018, The Avail Foundation, LLC.
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

package com.avail.compiler;

import com.avail.compiler.instruction.*;
import com.avail.descriptor.*;
import com.avail.descriptor.DeclarationPhraseDescriptor.DeclarationKind;
import com.avail.interpreter.Primitive;
import com.avail.interpreter.Primitive.Flag;
import com.avail.interpreter.primitive.privatehelpers.*;
import com.avail.utility.evaluation.Continuation0;

import javax.annotation.Nullable;
import java.io.ByteArrayOutputStream;
import java.util.*;
import java.util.Map.Entry;

import static com.avail.descriptor.CompiledCodeDescriptor.newCompiledCode;
import static com.avail.descriptor.DeclarationPhraseDescriptor.DeclarationKind.LOCAL_CONSTANT;
import static com.avail.descriptor.FunctionTypeDescriptor.functionType;
import static com.avail.descriptor.NilDescriptor.nil;
import static com.avail.descriptor.NybbleTupleDescriptor.generateNybbleTupleFrom;
import static com.avail.descriptor.ObjectTupleDescriptor.generateObjectTupleFrom;
import static com.avail.descriptor.ObjectTupleDescriptor.tupleFromList;
import static com.avail.descriptor.PhraseTypeDescriptor.PhraseKind.ASSIGNMENT_PHRASE;
import static com.avail.descriptor.PhraseTypeDescriptor.PhraseKind.LABEL_PHRASE;
import static com.avail.descriptor.TupleDescriptor.*;
import static com.avail.descriptor.VariableTypeDescriptor.variableTypeFor;
import static java.util.Arrays.asList;

/**
 * An {@link AvailCodeGenerator} is used to convert a {@linkplain
 * PhraseDescriptor parse tree} into the corresponding {@linkplain
 * CompiledCodeDescriptor compiled code}.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class AvailCodeGenerator
{
	/**
	 * The {@linkplain List list} of {@linkplain AvailInstruction instructions}
	 * generated so far.
	 */
	private final List<AvailInstruction> instructions = new ArrayList<>(10);

	/**
	 * A stack of {@link A_Tuple}s of {@link A_Token}s, representing successive
	 * refinement while traversing downwards through the phrase tree.
	 */
	private final Deque<A_Tuple> tokensStack = new ArrayDeque<>();

	/**
	 * The {@link List} of argument {@linkplain A_Phrase declarations} that
	 * correspond with actual arguments with which the resulting {@linkplain
	 * A_RawFunction raw function} will be invoked.
	 */
	private final List<A_Phrase> args;

	/**
	 * The {@link List} of declarations of local variables that this {@linkplain
	 * A_RawFunction raw function} will use.
	 */
	private final List<A_Phrase> locals;

	/**
	 * The {@link List} of declarations of local constants that this {@linkplain
	 * A_RawFunction raw function} will use.
	 */
	private final List<A_Phrase> constants;

	/**
	 * The {@link List} of the lexically captured declarations of arguments,
	 * variables, locals, and labels from enclosing scopes which are used by
	 * this block.
	 */
	private final List<A_Phrase> outers;

	/**
	 * A mapping from local variable/constant/argument/label declarations to
	 * index.
	 */
	private final Map<A_Phrase, Integer> varMap = new HashMap<>();

	/**
	 * A mapping from lexically captured variable/constant/argument/label
	 * declarations to the index within the list of outer variables that must
	 * be provided when creating a function from the compiled code.
	 */
	private final Map<A_Phrase, Integer> outerMap = new HashMap<>();

	/**
	 * The list of literal objects that have been encountered so far.
	 */
	private final List<A_BasicObject> literals = new ArrayList<>(10);

	/**
	 * The current stack depth, which is the number of objects that have been
	 * pushed but not yet been popped at this point in the list of
	 * instructions.
	 */
	private int depth = 0;

	/**
	 * The maximum stack depth that has been encountered so far.
	 */
	private int maxDepth = 0;

	/**
	 * A mapping from {@link DeclarationKind#LABEL label} to {@link AvailLabel},
	 * a pseudo-instruction.
	 */
	private final Map<A_Phrase, AvailLabel> labelInstructions = new HashMap<>();

	/**
	 * The type of result that should be generated by running the code.
	 */
	private final A_Type resultType;

	/**
	 * The {@linkplain SetDescriptor set} of {@linkplain ObjectTypeDescriptor
	 * exceptions} that this code may raise.
	 */
	private final A_Set exceptionSet;

	/**
	 * Which {@linkplain Primitive primitive VM operation} should be
	 * invoked, or
	 * zero if none.
	 */
	private @Nullable Primitive primitive;

	/**
	 * The module in which this code occurs.
	 */
	private final A_Module module;

	/**
	 * The line number on which this code starts.
	 */
	private final int startingLineNumber;

	/**
	 * Answer the index of the literal, adding it if not already present.
	 *
	 * @param aLiteral
	 *        The literal to look up.
	 * @return The index of the literal.
	 */
	private int indexOfLiteral (
		final A_BasicObject aLiteral)
	{
		int index = literals.indexOf(aLiteral) + 1;
		if (index == 0)
		{
			literals.add(aLiteral);
			index = literals.size();
		}
		return index;
	}

	/**
	 * Answer the number of arguments that the code under construction accepts.
	 *
	 * @return The code's number of arguments.
	 */
	public int numArgs ()
	{
		return args.size();
	}

	/**
	 * @return The module in which code generation is deemed to take place.
	 */
	public A_Module module ()
	{
		return module;
	}

	/**
	 * Generate a {@linkplain FunctionDescriptor function} with the supplied
	 * properties.
	 *
	 * @param module
	 *        The module in which the code occurs.
	 * @param blockPhrase
	 *        The block phrase from which the raw function is being generated.
	 * @return A raw function.
	 */
	public static A_RawFunction generateFunction (
		final A_Module module,
		final A_Phrase blockPhrase)
	{
		final @Nullable Primitive primitive = blockPhrase.primitive();
		final AvailCodeGenerator generator = new AvailCodeGenerator(
			module,
			toList(blockPhrase.argumentsTuple()),
			primitive,
			BlockPhraseDescriptor.locals(blockPhrase),
			BlockPhraseDescriptor.constants(blockPhrase),
			BlockPhraseDescriptor.labels(blockPhrase),
			toList(blockPhrase.neededVariables()),
			blockPhrase.resultType(),
			blockPhrase.declaredExceptions(),
			blockPhrase.startingLineNumber());
		generator.stackShouldBeEmpty();
		final A_Tuple statementsTuple = blockPhrase.statementsTuple();
		final int statementsCount = statementsTuple.tupleSize();
		if (statementsCount == 0
			&& (primitive == null || primitive.canHaveNybblecodes()))
		{
			// Ideally, we could capture just the close-square-bracket here.
			generator.emitPushLiteral(emptyTuple(), nil);
		}
		else
		{
			for (int index = 1; index < statementsCount; index++)
			{
				statementsTuple.tupleAt(index).emitEffectOn(generator);
				generator.stackShouldBeEmpty();
			}
			if (statementsCount > 0)
			{
				final A_Phrase lastStatement =
					statementsTuple.tupleAt(statementsCount);
				if (lastStatement.phraseKindIsUnder(LABEL_PHRASE)
					|| (lastStatement.phraseKindIsUnder(ASSIGNMENT_PHRASE)
						    && lastStatement.expressionType().isTop()))
				{
					// Either the block 1) ends with the label declaration or
					// 2) is top-valued and ends with an assignment. Push the
					// nil object as the return value.
					// Ideally, we could capture just the close-square-bracket
					// token here.
					lastStatement.emitEffectOn(generator);
					generator.emitPushLiteral(emptyTuple(), nil);
				}
				else
				{
					lastStatement.emitValueOn(generator);
				}
			}
		}
		return generator.endBlock(blockPhrase);
	}

	/**
	 * Set up code generation of a raw function.
	 *
	 * @param module
	 *        The module in which the function is defined.
	 * @param argumentsTuple
	 *        The tuple of argument declarations.
	 * @param thePrimitive
	 *        The {@link Primitive} or {@code null}.
	 * @param locals
	 *        The list of local variable declarations.
	 * @param constants
	 *        The list of local constant declarations.
	 * @param labels
	 *        The list of (zero or one) label declarations.
	 * @param outers
	 *        Any needed outer variable/constant declarations.
	 * @param resultType
	 *        The return type of the function.
	 * @param declaredException
	 *        The declared exception set of the function.
	 * @param startingLineNumber
	 *        The line number of the module at which the function is purported
	 *        to begin.
	 */
	private AvailCodeGenerator (
		final A_Module module,
		final List<A_Phrase> argumentsTuple,
		final @Nullable Primitive thePrimitive,
		final List<A_Phrase> locals,
		final List<A_Phrase> constants,
		final List<A_Phrase> labels,
		final List<A_Phrase> outers,
		final A_Type resultType,
		final A_Set declaredException,
		final int startingLineNumber)
	{
		this.module = module;
		this.args = argumentsTuple;
		this.locals = locals;
		this.constants = constants;
		this.outers = outers;
		for (final A_Phrase argumentDeclaration : argumentsTuple)
		{
			varMap.put(argumentDeclaration, varMap.size() + 1);
		}
		primitive = thePrimitive;
		for (final A_Phrase local : locals)
		{
			varMap.put(local, varMap.size() + 1);
		}
		for (final A_Phrase constant : constants)
		{
			varMap.put(constant, varMap.size() + 1);
		}
		for (final A_Phrase outerVar : outers)
		{
			outerMap.put(outerVar, outerMap.size() + 1);
		}
		for (final A_Phrase label : labels)
		{
			labelInstructions.put(label, new AvailLabel(label.tokens()));
		}
		this.resultType = resultType;
		this.exceptionSet = declaredException;
		this.startingLineNumber = startingLineNumber;
	}

	/**
	 * Answer the type that should be captured in the literal frame for the
	 * given declaration.  In the case that it's a variable declaration, we need
	 * to wrap the declared type in a variable type.
	 *
	 * @param declaration
	 *        The {@link DeclarationPhraseDescriptor declaration} to examine.
	 * @return The type for the corresponding {@link ContinuationDescriptor
	 *         continuation} slot.
	 */
	private static A_Type outerType (final A_Phrase declaration)
	{
		return declaration.declarationKind().isVariable()
			? variableTypeFor(declaration.declaredType())
			: declaration.declaredType();
	}

	/**
	 * Finish compilation of the block, answering the resulting compiledCode
	 * object.
	 *
	 * @param originatingBlockPhrase
	 *        The block phrase from which the raw function is created.
	 * @return A {@linkplain CompiledCodeDescriptor compiled code} object.
	 */
	private A_RawFunction endBlock (final A_Phrase originatingBlockPhrase)
	{
		fixFinalUses();
		// Detect blocks that immediately return something and mark them with a
		// special primitive number.
		if (primitive == null && instructions.size() == 1)
		{
			final AvailInstruction onlyInstruction = instructions.get(0);
			if (onlyInstruction instanceof AvailPushLiteral
				&& ((AvailPushLiteral) onlyInstruction).index() == 1)
			{
				// The block immediately answers a constant.
				primitive(P_PushConstant.instance);
			}
			else if (numArgs() >= 1
				&& onlyInstruction instanceof AvailPushLocalVariable)
			{
				// The block immediately answers the specified argument.
				final int argumentIndex =
					((AvailPushLocalVariable) onlyInstruction).index();
				switch (argumentIndex)
				{
					case 1:
						primitive(P_PushArgument1.instance);
						break;
					case 2:
						primitive(P_PushArgument2.instance);
						break;
					case 3:
						primitive(P_PushArgument3.instance);
						break;
				}
			}
			else if (onlyInstruction instanceof AvailPushOuterVariable)
			{
				// The block immediately answers the sole captured outer
				// variable or constant.  There can only be one such outer since
				// we only capture what's needed, and there are no other
				// instructions that might use another outer.
				final AvailPushOuterVariable pushOuter =
					(AvailPushOuterVariable) onlyInstruction;
				assert pushOuter.index() == 1;
				assert pushOuter.isLastAccess();
				primitive(P_PushLastOuter.instance);
			}
			// Only optimize module constants, not module variables.  Module
			// variables can be unassigned, and reading an unassigned module
			// variable must fail appropriately.
			if (onlyInstruction instanceof AvailGetLiteralVariable
				&& ((AvailGetLiteralVariable) onlyInstruction).index() == 1
				&& literals.get(0).isInitializedWriteOnceVariable())
			{
				primitive(P_GetGlobalVariableValue.instance);
			}
		}
		// Make sure we're not closing over variables that don't get used.
		final BitSet unusedOuters = new BitSet(outerMap.size());
		unusedOuters.flip(0, outerMap.size());
		final ByteArrayOutputStream nybbles = new ByteArrayOutputStream(50);
		final List<Integer> encodedLineNumberDeltas = new ArrayList<>(50);
		int currentLineNumber = startingLineNumber;
		for (final AvailInstruction instruction : instructions)
		{
			if (instruction.isOuterUse())
			{
				final int i = ((AvailInstructionWithIndex) instruction).index();
				unusedOuters.clear(i - 1);
			}
			instruction.writeNybblesOn(nybbles);
			final int nextLineNumber = instruction.lineNumber();
			if (nextLineNumber == -1)
			{
				// Indicate no change.
				encodedLineNumberDeltas.add(0);
			}
			else
			{
				final int delta = nextLineNumber - currentLineNumber;
				final int encodedDelta = delta < 0
					? -delta << 1 | 1
					: delta << 1;
				encodedLineNumberDeltas.add(encodedDelta);
				currentLineNumber = nextLineNumber;
			}
		}
		if (!unusedOuters.isEmpty())
		{
			final Set<A_Phrase> unusedOuterDeclarations = new HashSet<>();
			for (final Entry<A_Phrase, Integer> entry : outerMap.entrySet())
			{
				if (unusedOuters.get(entry.getValue() - 1))
				{
					unusedOuterDeclarations.add(entry.getKey());
				}
			}
			assert false
				: "Some outers were unused: " + unusedOuterDeclarations;
		}
		final byte[] nybblesArray = nybbles.toByteArray();
		final A_Tuple nybbleTuple = generateNybbleTupleFrom(
			nybblesArray.length, i -> nybblesArray[i - 1]);
		assert resultType.isType();

		final A_Tuple argTypes = generateObjectTupleFrom(
			args.size(), i -> args.get(i - 1).declaredType());
		final A_Tuple localTypes = generateObjectTupleFrom(
			locals.size(),
			i -> variableTypeFor(locals.get(i - 1).declaredType()));
		final A_Tuple constantTypes = generateObjectTupleFrom(
			constants.size(), i -> constants.get(i - 1).declaredType());

		final A_Tuple outerTypes = generateObjectTupleFrom(
			outers.size(), i -> outerType(outers.get(i - 1)));

		final A_Type functionType =
			functionType(argTypes, resultType, exceptionSet);
		final A_RawFunction code = newCompiledCode(
			nybbleTuple,
			maxDepth,
			functionType,
			primitive,
			tupleFromList(literals),
			localTypes,
			constantTypes,
			outerTypes,
			module,
			startingLineNumber,
			tupleFromIntegerList(encodedLineNumberDeltas),
			originatingBlockPhrase);
		return code.makeShared();
	}

	/**
	 * Decrease the tracked stack depth by the given amount.
	 *
	 * @param delta
	 *        The number of things popped off the stack.
	 */
	private void decreaseDepth (
		final int delta)
	{
		depth -= delta;
		assert depth >= 0
			: "Inconsistency - Generated code would pop too much.";
	}

	/**
	 * Increase the tracked stack depth by one.
	 */
	private void increaseDepth ()
	{
		depth ++;
		if (depth > maxDepth)
		{
			maxDepth = depth;
		}
	}

	/**
	 * Verify that the stack is empty at this point.
	 */
	private void stackShouldBeEmpty ()
	{
		assert depth == 0 : "The stack should be empty here";
	}

	/**
	 * Capture an {@link A_Tuple} of {@link A_Token}s for the duration of
	 * execution of the {@link Continuation0 action}.  If the tuple of tokens is
	 * empty, just evaluate the action.
	 */
	public void setTokensWhile (
		final A_Tuple tokens,
		final Continuation0 action)
	{
		if (tokens.tupleSize() == 0)
		{
			action.value();
			return;
		}
		tokensStack.addLast(tokens);
		try
		{
			action.value();
		}
		finally
		{
			tokensStack.removeLast();
		}
	}

	/**
	 * Write a regular multimethod call.  I expect my arguments to have been
	 * pushed already.
	 *
	 * @param tokens
	 *        The {@link A_Tuple} of {@link A_Token}s associated with this
	 *        call.
	 * @param nArgs
	 *        The number of arguments that the method accepts.
	 * @param bundle
	 *        The message bundle for the method in which to look up the
	 *        method definition being invoked.
	 * @param returnType
	 *        The expected return type of the call.
	 */
	public void emitCall (
		final A_Tuple tokens,
		final int nArgs,
		final A_Bundle bundle,
		final A_Type returnType)
	{
		final int messageIndex = indexOfLiteral(bundle);
		final int returnIndex = indexOfLiteral(returnType);
		addInstruction(new AvailCall(tokens, messageIndex, returnIndex));
		// Pops off arguments.
		decreaseDepth(nArgs);
		// Pushes expected return type, to be overwritten by return value.
		increaseDepth();
	}

	/**
	 * Write a super-call.  I expect my arguments and their types to have been
	 * pushed already (interleaved).
	 *
	 * @param tokens
	 *        The {@link A_Tuple} of {@link A_Token}s associated with this
	 *        call.
	 * @param nArgs
	 *        The number of arguments that the method accepts.
	 * @param bundle
	 *        The message bundle for the method in which to look up the method
	 *        definition being invoked.
	 * @param returnType
	 *        The expected return type of the call.
	 * @param superUnionType
	 *        The tuple type used to direct method lookup.
	 */
	public void emitSuperCall (
		final A_Tuple tokens,
		final int nArgs,
		final A_Bundle bundle,
		final A_Type returnType,
		final A_Type superUnionType)
	{
		final int messageIndex = indexOfLiteral(bundle);
		final int returnIndex = indexOfLiteral(returnType);
		final int superUnionIndex = indexOfLiteral(superUnionType);
		addInstruction(new AvailSuperCall(
			tokens, messageIndex, returnIndex, superUnionIndex));
		// Pops all arguments.
		decreaseDepth(nArgs);
		// Pushes expected return type, to be overwritten by return value.
		increaseDepth();
	}

	/**
	 * Create a function from {@code CompiledCodeDescriptor compiled code} and
	 * the pushed outer (lexically bound) variables.
	 *
	 * @param tokens
	 *        The {@link A_Tuple} of {@link A_Token}s associated with this
	 *        function closing operation.
	 * @param compiledCode
	 *        The code from which to make a function.
	 * @param neededVariables
	 *        A {@linkplain TupleDescriptor tuple} of {@linkplain
	 *        DeclarationPhraseDescriptor declarations} of variables that the
	 *        code needs to access.
	 */
	public void emitCloseCode (
		final A_Tuple tokens,
		final A_RawFunction compiledCode,
		final A_Tuple neededVariables)
	{
		for (final A_Phrase variableDeclaration : neededVariables)
		{
			emitPushLocalOrOuter(tokens, variableDeclaration);
		}
		final int codeIndex = indexOfLiteral(compiledCode);
		addInstruction(new AvailCloseCode(
			tokens,
			neededVariables.tupleSize(),
			codeIndex));
		// Copied variables are popped.
		decreaseDepth(neededVariables.tupleSize());
		// Function is pushed.
		increaseDepth();
	}

	/**
	 * Emit code to duplicate the element at the top of the stack.
	 */
	public void emitDuplicate ()
	{
		increaseDepth();
		addInstruction(new AvailDuplicate(emptyTuple()));
	}

	/**
	 * Emit code to get the value of a literal variable.
	 *
	 * @param tokens
	 *        The {@link A_Tuple} of {@link A_Token}s associated with this
	 *        literal.
	 * @param aLiteral
	 *        The {@linkplain VariableDescriptor variable} that should have
	 *        its value extracted.
	 */
	public void emitGetLiteral (
		final A_Tuple tokens,
		final A_BasicObject aLiteral)
	{
		increaseDepth();
		final int index = indexOfLiteral(aLiteral);
		addInstruction(new AvailGetLiteralVariable(tokens, index));
	}

	/**
	 * Emit code to get the value of a local or outer (captured) variable.
	 *
	 * @param tokens
	 *        The {@link A_Tuple} of {@link A_Token}s associated with this
	 *        call.
	 * @param localOrOuter
	 *        The {@linkplain DeclarationPhraseDescriptor declaration} of the
	 *        variable that should have its value extracted.
	 */
	public void emitGetLocalOrOuter (
		final A_Tuple tokens,
		final A_Phrase localOrOuter)
	{
		increaseDepth();
		if (varMap.containsKey(localOrOuter))
		{
			addInstruction(
				new AvailGetLocalVariable(tokens, varMap.get(localOrOuter)));
			return;
		}
		if (outerMap.containsKey(localOrOuter))
		{
			addInstruction(
				new AvailGetOuterVariable(tokens, outerMap.get(localOrOuter)));
			return;
		}
		assert !labelInstructions.containsKey(localOrOuter)
			: "This case should have been handled a different way!";
		assert false : "Consistency error - unknown variable.";
	}

	/**
	 * Emit a {@linkplain DeclarationPhraseDescriptor declaration} of a {@link
	 * DeclarationKind#LABEL label} for the current block.
	 *
	 * @param labelNode
	 *        The label declaration.
	 */
	public void emitLabelDeclaration (
		final A_Phrase labelNode)
	{
		assert instructions.isEmpty()
			: "Label must be first statement in block";
		// stack is unaffected.
		addInstruction(labelInstructions.get(labelNode));
	}

	/**
	 * Emit code to create a {@linkplain TupleDescriptor tuple} from the top N
	 * items on the stack.
	 *
	 * @param tokens
	 *        The {@link A_Tuple} of {@link A_Token}s associated with this
	 *        call.
	 * @param count
	 *        How many pushed items to pop for the new tuple.
	 */
	public void emitMakeTuple (
		final A_Tuple tokens,
		final int count)
	{
		addInstruction(new AvailMakeTuple(tokens, count));
		decreaseDepth(count);
		increaseDepth();
	}

	/**
	 * Emit code to permute the top N items on the stack with the given
	 * N-element permutation.
	 *
	 * @param tokens
	 *        The {@link A_Tuple} of {@link A_Token}s associated with this
	 *        call.
	 * @param permutation
	 *        A tuple of one-based integers that forms a permutation.
	 */
	public void emitPermute (
		final A_Tuple tokens,
		final A_Tuple permutation)
	{
		final int index = indexOfLiteral(permutation);
		addInstruction(new AvailPermute(tokens, index));
	}

	/**
	 * Emit code to pop the top value from the stack.
	 */
	public void emitPop ()
	{
		addInstruction(new AvailPop(emptyTuple()));
		decreaseDepth(1);
	}

	/**
	 * Emit code to push a literal object onto the stack.
	 *
	 * @param tokens
	 *        The {@link A_Tuple} of {@link A_Token}s associated with this
	 *        call.
	 * @param aLiteral
	 *        The object to push.
	 */
	public void emitPushLiteral (
		final A_Tuple tokens,
		final A_BasicObject aLiteral)
	{
		increaseDepth();
		final int index = indexOfLiteral(aLiteral);
		addInstruction(new AvailPushLiteral(tokens, index));
	}

	/**
	 * Push a variable.  It can be local to the current block or defined in an
	 * outer scope.
	 *
	 * @param tokens
	 *        The {@link A_Tuple} of {@link A_Token}s associated with this
	 *        call.
	 * @param variableDeclaration
	 *        The variable declaration.
	 */
	public void emitPushLocalOrOuter (
		final A_Tuple tokens,
		final A_Phrase variableDeclaration)
	{
		increaseDepth();
		if (varMap.containsKey(variableDeclaration))
		{
			addInstruction(new AvailPushLocalVariable(
				tokens, varMap.get(variableDeclaration)));
			return;
		}
		if (outerMap.containsKey(variableDeclaration))
		{
			addInstruction(new AvailPushOuterVariable(
				tokens, outerMap.get(variableDeclaration)));
			return;
		}
		assert labelInstructions.containsKey(variableDeclaration)
			: "Consistency error - unknown variable.";
		addInstruction(new AvailPushLabel(tokens));
	}

	/**
	 * Emit code to pop the stack and write the popped value into a literal
	 * variable.
	 *
	 * @param tokens
	 *        The {@link A_Tuple} of {@link A_Token}s associated with this
	 *        call.
	 * @param aLiteral
	 *        The variable in which to write.
	 */
	public void emitSetLiteral (
		final A_Tuple tokens,
		final A_BasicObject aLiteral)
	{
		final int index = indexOfLiteral(aLiteral);
		addInstruction(new AvailSetLiteralVariable(tokens, index));
		//  Value to assign has been popped.
		decreaseDepth(1);
	}

	/**
	 * Emit code to pop the stack and write into a local or outer variable.
	 *
	 final A_Tuple tokens,
	 * @param localOrOuter
	 *        The {@linkplain DeclarationPhraseDescriptor declaration} of the
	 *        {@link DeclarationKind#LOCAL_VARIABLE local} or outer variable in
	 *        which to write.
	 */
	public void emitSetLocalOrOuter (
		final A_Tuple tokens,
		final A_Phrase localOrOuter)
	{
		decreaseDepth(1);
		if (varMap.containsKey(localOrOuter))
		{
			addInstruction(
				new AvailSetLocalVariable(tokens, varMap.get(localOrOuter)));
			return;
		}
		if (outerMap.containsKey(localOrOuter))
		{
			addInstruction(
				new AvailSetOuterVariable(tokens, outerMap.get(localOrOuter)));
			return;
		}
		assert !labelInstructions.containsKey(localOrOuter)
			: "You can't assign to a label!";
		assert false : "Consistency error - unknown variable.";
	}

	/**
	 *  Emit code to pop the stack and write it directly into a local slot of
	 * the continuation.  This is how local constants become initialized.
	 *
	 * @param tokens
	 *        The {@link A_Tuple} of {@link A_Token}s associated with this
	 *        call.
	 * @param localConstant
	 *        The local constant declaration.
	 */
	public void emitSetLocalFrameSlot (
		final A_Tuple tokens,
		final A_Phrase localConstant)
	{
		assert localConstant.declarationKind() == LOCAL_CONSTANT;
		assert varMap.containsKey(localConstant)
			: "Local constants can only be initialized at their definition.";
		decreaseDepth(1);
		addInstruction(
			new AvailSetLocalConstant(tokens, varMap.get(localConstant)));
	}

	/**
	 * Add the {@link AvailInstruction} to the generated sequence.  Use the
	 * instruction's tuple of tokens if non-empty, otherwise examine the
	 * {@link #tokensStack} for a better candidate.
	 */
	private void addInstruction (final AvailInstruction instruction)
	{
		if (instruction.relevantTokens().tupleSize() == 0)
		{
			// Replace it with the nearest tuple from the stack, which should
			// relate to the most specific position in the phrase tree for which
			// tokens are known.
			if (!tokensStack.isEmpty())
			{
				instruction.setRelevantTokens(tokensStack.getLast());
			}
		}
		instructions.add(instruction);
	}

	/**
	 * Set the primitive number to write in the generated code.  A failed
	 * attempt at running the primitive will be followed by running the level
	 * one code (nybblecodes) that this class generates.
	 *
	 * @param thePrimitive The {@link Primitive} or {@code null}.
	 */
	public void primitive (
		final Primitive thePrimitive)
	{
		assert primitive == null : "Primitive was already set";
		primitive = thePrimitive;
	}

	/**
	 * Figure out which uses of local and outer variables are final uses.  This
	 * interferes with the concept of labels in the following way.  We now only
	 * allow labels as the first statement in a block, so you can only restart
	 * or exit a continuation (not counting the debugger usage, which shouldn't
	 * affect us).  Restarting requires only the arguments and outer variables
	 * to be preserved, as all local variables are recreated (unassigned) on
	 * restart.  Exiting doesn't require anything of any non-argument locals, so
	 * no problem there.  Note that after the last place in the code where the
	 * continuation is constructed we don't even need any arguments or outer
	 * variables unless the code after this point actually uses the arguments.
	 * We'll be a bit conservative here and simply clean up those arguments and
	 * outer variables which are used after the last continuation construction
	 * point, at their final use points, while always cleaning up final uses of
	 * local non-argument variables.
	 */
	private void fixFinalUses ()
	{
		final List<AvailVariableAccessNote> localData =
			asList(new AvailVariableAccessNote[varMap.size()]);
		final List<AvailVariableAccessNote> outerData =
			asList(new AvailVariableAccessNote[outerMap.size()]);
		for (final AvailInstruction instruction : instructions)
		{
			instruction.fixUsageFlags(localData, outerData, this);
		}
		final @Nullable Primitive p = primitive;
		if (p != null)
		{
			// If necessary, prevent clearing of the primitive failure variable
			// after its last usage.
			if (p.hasFlag(Flag.PreserveFailureVariable))
			{
				assert !p.hasFlag(Flag.CannotFail);
				final AvailInstruction fakeFailureVariableUse =
					new AvailGetLocalVariable(emptyTuple(), numArgs() + 1);
				fakeFailureVariableUse.fixUsageFlags(
					localData, outerData, this);
			}
			// If necessary, prevent clearing of the primitive arguments after
			// their last usage.
			if (p.hasFlag(Flag.PreserveArguments))
			{
				for (int index = 1; index <= numArgs(); index++)
				{
					final AvailInstruction fakeArgumentUse =
						new AvailPushLocalVariable(emptyTuple(), index);
					fakeArgumentUse.fixUsageFlags(localData, outerData, this);
				}
			}
		}
	}
}
