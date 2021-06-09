/*
 * AvailCodeGenerator.kt
 * Copyright © 1993-2021, The Avail Foundation, LLC.
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

package com.avail.compiler

import com.avail.compiler.instruction.AvailCall
import com.avail.compiler.instruction.AvailCloseCode
import com.avail.compiler.instruction.AvailDuplicate
import com.avail.compiler.instruction.AvailGetLiteralVariable
import com.avail.compiler.instruction.AvailGetLocalVariable
import com.avail.compiler.instruction.AvailGetOuterVariable
import com.avail.compiler.instruction.AvailInstruction
import com.avail.compiler.instruction.AvailInstructionWithIndex
import com.avail.compiler.instruction.AvailLabel
import com.avail.compiler.instruction.AvailMakeTuple
import com.avail.compiler.instruction.AvailPermute
import com.avail.compiler.instruction.AvailPop
import com.avail.compiler.instruction.AvailPushLabel
import com.avail.compiler.instruction.AvailPushLiteral
import com.avail.compiler.instruction.AvailPushLocalVariable
import com.avail.compiler.instruction.AvailPushOuterVariable
import com.avail.compiler.instruction.AvailSetLiteralVariable
import com.avail.compiler.instruction.AvailSetLocalConstant
import com.avail.compiler.instruction.AvailSetLocalVariable
import com.avail.compiler.instruction.AvailSetOuterVariable
import com.avail.compiler.instruction.AvailSuperCall
import com.avail.compiler.instruction.AvailVariableAccessNote
import com.avail.descriptor.bundles.A_Bundle
import com.avail.descriptor.functions.A_RawFunction
import com.avail.descriptor.functions.CompiledCodeDescriptor
import com.avail.descriptor.functions.CompiledCodeDescriptor.Companion.newCompiledCode
import com.avail.descriptor.functions.ContinuationDescriptor
import com.avail.descriptor.module.A_Module
import com.avail.descriptor.objects.ObjectTypeDescriptor
import com.avail.descriptor.phrases.A_Phrase
import com.avail.descriptor.phrases.A_Phrase.Companion.argumentsTuple
import com.avail.descriptor.phrases.A_Phrase.Companion.declaredExceptions
import com.avail.descriptor.phrases.A_Phrase.Companion.declaredType
import com.avail.descriptor.phrases.A_Phrase.Companion.emitEffectOn
import com.avail.descriptor.phrases.A_Phrase.Companion.emitValueOn
import com.avail.descriptor.phrases.A_Phrase.Companion.neededVariables
import com.avail.descriptor.phrases.A_Phrase.Companion.phraseExpressionType
import com.avail.descriptor.phrases.A_Phrase.Companion.phraseKindIsUnder
import com.avail.descriptor.phrases.A_Phrase.Companion.primitive
import com.avail.descriptor.phrases.A_Phrase.Companion.startingLineNumber
import com.avail.descriptor.phrases.A_Phrase.Companion.statementsTuple
import com.avail.descriptor.phrases.A_Phrase.Companion.token
import com.avail.descriptor.phrases.A_Phrase.Companion.tokens
import com.avail.descriptor.phrases.BlockPhraseDescriptor.Companion.constants
import com.avail.descriptor.phrases.BlockPhraseDescriptor.Companion.labels
import com.avail.descriptor.phrases.BlockPhraseDescriptor.Companion.locals
import com.avail.descriptor.phrases.DeclarationPhraseDescriptor
import com.avail.descriptor.phrases.DeclarationPhraseDescriptor.DeclarationKind
import com.avail.descriptor.phrases.DeclarationPhraseDescriptor.DeclarationKind.LOCAL_CONSTANT
import com.avail.descriptor.phrases.PhraseDescriptor
import com.avail.descriptor.representation.A_BasicObject
import com.avail.descriptor.representation.AvailObject
import com.avail.descriptor.representation.NilDescriptor.Companion.nil
import com.avail.descriptor.sets.A_Set
import com.avail.descriptor.sets.SetDescriptor
import com.avail.descriptor.tokens.A_Token
import com.avail.descriptor.tuples.A_Tuple
import com.avail.descriptor.tuples.A_Tuple.Companion.tupleAt
import com.avail.descriptor.tuples.A_Tuple.Companion.tupleSize
import com.avail.descriptor.tuples.NybbleTupleDescriptor.Companion.generateNybbleTupleFrom
import com.avail.descriptor.tuples.ObjectTupleDescriptor.Companion.generateObjectTupleFrom
import com.avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tupleFromList
import com.avail.descriptor.tuples.StringDescriptor.Companion.stringFrom
import com.avail.descriptor.tuples.TupleDescriptor.Companion.emptyTuple
import com.avail.descriptor.tuples.TupleDescriptor.Companion.toList
import com.avail.descriptor.tuples.TupleDescriptor.Companion.tupleFromIntegerList
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.BottomTypeDescriptor.Companion.bottom
import com.avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import com.avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.ASSIGNMENT_PHRASE
import com.avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.LABEL_PHRASE
import com.avail.descriptor.types.TypeDescriptor.Types
import com.avail.descriptor.types.VariableTypeDescriptor.Companion.variableTypeFor
import com.avail.descriptor.variables.VariableDescriptor
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Flag
import com.avail.interpreter.primitive.privatehelpers.P_GetGlobalVariableValue
import com.avail.interpreter.primitive.privatehelpers.P_PushArgument1
import com.avail.interpreter.primitive.privatehelpers.P_PushArgument2
import com.avail.interpreter.primitive.privatehelpers.P_PushArgument3
import com.avail.interpreter.primitive.privatehelpers.P_PushConstant
import com.avail.interpreter.primitive.privatehelpers.P_PushLastOuter
import com.avail.io.NybbleOutputStream
import java.util.ArrayDeque
import java.util.BitSet

/**
 * An [AvailCodeGenerator] is used to convert a [phrase][PhraseDescriptor] into
 * the corresponding [raw&#32;function][CompiledCodeDescriptor].
 *
 * @property module
 *   The module in which this code occurs.
 * @property args
 *   The [List] of argument [declarations][A_Phrase] that correspond with actual
 *   arguments with which the resulting [raw&#32;function][A_RawFunction] will
 *   be invoked.
 * @property locals
 *   The [List] of declarations of local variables that this
 *   [raw&#32;function][A_RawFunction] will use.
 * @property constants
 *   The [List] of declarations of local constants that this [raw&#32;function]
 *   will use.
 * @property outers
 *   The [List] of the lexically captured declarations of arguments, variables,
 *   locals, and labels from enclosing scopes which are used by this block.
 * @property resultType
 *   The type of result that should be generated by running the code.
 * @property exceptionSet
 *   The [set][SetDescriptor] of [exceptions][ObjectTypeDescriptor] that this
 *   code may raise.
 * @property startingLineNumber
 *   The line number on which this code starts.
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @constructor
 *
 * Set up code generation of a raw function.
 *
 * @param module
 *   The module in which the function is defined.
 * @param args
 *   The tuple of argument declarations.
 * @param primitive
 *   The [Primitive] or `null`.
 * @param locals
 *   The list of local variable declarations.
 * @param constants
 *   The list of local constant declarations.
 * @param labels
 *   The list of (zero or one) label declarations.
 * @param outers
 *   Any needed outer variable/constant declarations.
 * @param resultType
 *   The return type of the function.
 * @param resultTypeIfPrimitiveFails
 *   The return type of the function, in the event that the primitive fails, or
 *   there is no primitive.
 * @param exceptionSet
 *   The declared exception set of the function.
 * @param startingLineNumber
 *   The line number of the module at which the function is purported to begin.
 */
class AvailCodeGenerator private constructor(
	val module: A_Module,
	private val args: List<A_Phrase>,
	primitive: Primitive?,
	private val locals: List<A_Phrase>,
	private val constants: List<A_Phrase>,
	labels: List<A_Phrase>,
	private val outers: List<A_Phrase>,
	private val resultType: A_Type,
	private val resultTypeIfPrimitiveFails: A_Type,
	private val exceptionSet: A_Set,
	private val startingLineNumber: Int)
{
	/**
	 * Which [primitive&#32;VM&#32;operation][Primitive] should be invoked, or
	 * `null` if none.
	 */
	private var primitive: Primitive? = primitive
		set(thePrimitive)
		{
			assert(field === null) { "Primitive was already set" }
			field = thePrimitive
		}

	/**
	 * The [list][List] of [instructions][AvailInstruction] generated so far.
	 */
	private val instructions = mutableListOf<AvailInstruction>()

	/**
	 * A stack of [A_Tuple]s of [A_Token]s, representing successive refinement
	 * while traversing downwards through the phrase tree.
	 */
	private val tokensStack = ArrayDeque<A_Tuple>()

	/**
	 * A mapping from local variable/constant/argument/label declarations to
	 * index.
	 */
	private val varMap = mutableMapOf<A_Phrase, Int>()

	/**
	 * A mapping from lexically captured variable/constant/argument/label
	 * declarations to the index within the list of outer variables that must be
	 * provided when creating a function from the compiled code.
	 */
	private val outerMap = mutableMapOf<A_Phrase, Int>()

	/**
	 * The list of literal objects that have been encountered so far.
	 */
	private val literals = mutableListOf<A_BasicObject>()

	/**
	 * The current stack depth, which is the number of objects that have been
	 * pushed but not yet been popped at this point in the list of instructions.
	 */
	private var depth = 0

	/**
	 * The maximum stack depth that has been encountered so far.
	 */
	private var maxDepth = 0

	/**
	 * A mapping from [label][DeclarationKind.LABEL] to [AvailLabel], a
	 * pseudo-instruction.
	 */
	private val labelInstructions = mutableMapOf<A_Phrase, AvailLabel>()

	/**
	 * Answer the index of the literal, adding it if not already present.
	 *
	 * @param aLiteral
	 *   The literal to look up.
	 * @return
	 *   The index of the literal.
	 */
	private fun indexOfLiteral(aLiteral: A_BasicObject): Int
	{
		var index = literals.indexOf(aLiteral) + 1
		if (index == 0)
		{
			literals.add(aLiteral)
			index = literals.size
		}
		return index
	}

	/**
	 * The number of arguments that the code under construction accepts.
	 */
	val numArgs get() = args.size

	init
	{
		for (argumentDeclaration in args)
		{
			varMap[argumentDeclaration] = varMap.size + 1
		}
		for (local in locals)
		{
			varMap[local] = varMap.size + 1
		}
		for (constant in constants)
		{
			varMap[constant] = varMap.size + 1
		}
		for (outerVar in outers)
		{
			outerMap[outerVar] = outerMap.size + 1
		}
		for (label in labels)
		{
			labelInstructions[label] = AvailLabel(label.tokens())
		}
	}

	/**
	 * Finish compilation of the block, answering the resulting raw function.
	 *
	 * @param originatingBlockPhrase
	 *   The block phrase from which the raw function is created.
	 * @return
	 *   A [raw&#32;function][A_RawFunction] object.
	 */
	private fun endBlock(originatingBlockPhrase: A_Phrase): A_RawFunction
	{
		fixFinalUses()
		// Detect blocks that immediately return something and mark them with a
		// special primitive number.
		if (primitive === null && instructions.size == 1)
		{
			val onlyInstruction = instructions[0]
			if (onlyInstruction is AvailPushLiteral
				&& onlyInstruction.index == 1)
			{
				// The block immediately answers a constant.
				primitive = P_PushConstant
			}
			else if (numArgs >= 1 && onlyInstruction is AvailPushLocalVariable)
			{
				// The block immediately answers the specified argument.
				when (onlyInstruction.index)
				{
					1 -> primitive = P_PushArgument1
					2 -> primitive = P_PushArgument2
					3 -> primitive = P_PushArgument3
				}
			}
			else if (onlyInstruction is AvailPushOuterVariable)
			{
				// The block immediately answers the sole captured outer
				// variable or constant.  There can only be one such outer since
				// we only capture what's needed, and there are no other
				// instructions that might use another outer.
				assert(onlyInstruction.index == 1)
				assert(onlyInstruction.isLastAccess)
				primitive = P_PushLastOuter
			}
			// Only optimize module constants, not module variables.  Module
			// variables can be unassigned, and reading an unassigned module
			// variable must fail appropriately.
			if (onlyInstruction is AvailGetLiteralVariable
				&& onlyInstruction.index == 1
				&& literals[0].isInitializedWriteOnceVariable)
			{
				primitive = P_GetGlobalVariableValue
			}
		}
		// Make sure we're not closing over variables that don't get used.
		val unusedOuters = BitSet(outerMap.size)
		unusedOuters.flip(0, outerMap.size)
		val nybbles = NybbleOutputStream(50)
		val encodedLineNumberDeltas = mutableListOf<Int>()
		var currentLineNumber = startingLineNumber
		for (instruction in instructions)
		{
			if (instruction.isOuterUse)
			{
				val i = (instruction as AvailInstructionWithIndex).index
				unusedOuters.clear(i - 1)
			}
			instruction.writeNybblesOn(nybbles)
			val nextLineNumber = instruction.lineNumber
			if (nextLineNumber == -1)
			{
				// Indicate no change.
				encodedLineNumberDeltas.add(0)
			}
			else
			{
				val delta = nextLineNumber - currentLineNumber
				val encodedDelta =
					if (delta < 0)
						-delta shl 1 or 1
					else
						delta shl 1
				encodedLineNumberDeltas.add(encodedDelta)
				currentLineNumber = nextLineNumber
			}
		}
		if (!unusedOuters.isEmpty)
		{
			val unusedOuterDeclarations = mutableSetOf<A_Phrase>()
			for ((key, value) in outerMap)
			{
				if (unusedOuters.get(value - 1))
				{
					unusedOuterDeclarations.add(key)
				}
			}
			assert(false) {
				"Some outers were unused: $unusedOuterDeclarations"
			}
		}
		val nybblesArray = nybbles.toByteArray()
		val nybbleTuple = generateNybbleTupleFrom(nybblesArray.size) {
			i -> nybblesArray[i - 1].toInt()
		}
		assert(resultType.isType)

		val argTypes = generateObjectTupleFrom(args.size) {
			i -> args[i - 1].declaredType()
		}
		val localTypes = generateObjectTupleFrom(locals.size) {
			i -> variableTypeFor(locals[i - 1].declaredType())
		}
		val constantTypes = generateObjectTupleFrom(constants.size) {
			i -> constants[i - 1].declaredType()
		}

		val outerTypes = generateObjectTupleFrom(outers.size) {
			i -> outerType(outers[i - 1])
		}

		val declarations = mutableListOf<A_Phrase>()
		declarations.addAll(originatingBlockPhrase.argumentsTuple())
		declarations.addAll(locals(originatingBlockPhrase))
		declarations.addAll(constants(originatingBlockPhrase))
		val packedDeclarationNames = declarations.joinToString(",") {
			// Quote-escape any name that contains a comma (,) or quote (").
			val name = it.token().string().asNativeString()
			if (name.contains(',') || name.contains('\"'))
				stringFrom(name).toString()
			else name
		}

		val functionType = functionType(argTypes, resultType, exceptionSet)
		val code = newCompiledCode(
			nybbleTuple,
			maxDepth,
			functionType,
			primitive,
			resultTypeIfPrimitiveFails,
			tupleFromList(literals),
			localTypes,
			constantTypes,
			outerTypes,
			module,
			startingLineNumber,
			tupleFromIntegerList(encodedLineNumberDeltas),
			originatingBlockPhrase as AvailObject,
			stringFrom(packedDeclarationNames))
		return code.makeShared()
	}

	/**
	 * Decrease the tracked stack depth by the given amount.
	 *
	 * @param delta
	 *   The number of things popped off the stack.
	 */
	private fun decreaseDepth(delta: Int)
	{
		depth -= delta
		assert(depth >= 0) {
			"Inconsistency - Generated code would pop too much."
		}
	}

	/**
	 * Increase the tracked stack depth by one.
	 */
	private fun increaseDepth()
	{
		depth++
		if (depth > maxDepth)
		{
			maxDepth = depth
		}
	}

	/**
	 * Verify that the stack is empty at this point.
	 */
	private fun stackShouldBeEmpty()
	{
		assert(depth == 0) { "The stack should be empty here" }
	}

	/**
	 * Capture an [A_Tuple] of [A_Token]s for the duration of execution of the
	 * action].  If the tuple of tokens is empty, just evaluate the action.
	 *
	 * @param tokens
	 *   The tokens to make available during the application of the supplied
	 *   action.
	 * @param action
	 *   An arbitrary action.
	 */
	fun setTokensWhile(tokens: A_Tuple, action: () -> Unit)
	{
		if (tokens.tupleSize() == 0)
		{
			action()
			return
		}
		tokensStack.addLast(tokens)
		try
		{
			action()
		}
		finally
		{
			tokensStack.removeLast()
		}
	}

	/**
	 * Write a regular multimethod call.  I expect my arguments to have been
	 * pushed already.
	 *
	 * @param tokens
	 *   The [A_Tuple] of [A_Token]s associated with this call.
	 * @param nArgs
	 *   The number of arguments that the method accepts.
	 * @param bundle
	 *   The message bundle for the method in which to look up the method
	 *   definition being invoked.
	 * @param returnType
	 *   The expected return type of the call.
	 */
	fun emitCall(
		tokens: A_Tuple,
		nArgs: Int,
		bundle: A_Bundle,
		returnType: A_Type)
	{
		val messageIndex = indexOfLiteral(bundle)
		val returnIndex = indexOfLiteral(returnType)
		addInstruction(AvailCall(tokens, messageIndex, returnIndex))
		// Pops off arguments.
		decreaseDepth(nArgs)
		// Pushes expected return type, to be overwritten by return value.
		increaseDepth()
	}

	/**
	 * Write a super-call.  I expect my arguments and their types to have been
	 * pushed already (interleaved).
	 *
	 * @param tokens
	 *   The [A_Tuple] of [A_Token]s associated with this call.
	 * @param nArgs
	 *   The number of arguments that the method accepts.
	 * @param bundle
	 *   The message bundle for the method in which to look up the method
	 *   definition being invoked.
	 * @param returnType
	 *   The expected return type of the call.
	 * @param superUnionType
	 *   The tuple type used to direct method lookup.
	 */
	fun emitSuperCall(
		tokens: A_Tuple,
		nArgs: Int,
		bundle: A_Bundle,
		returnType: A_Type,
		superUnionType: A_Type)
	{
		val messageIndex = indexOfLiteral(bundle)
		val returnIndex = indexOfLiteral(returnType)
		val superUnionIndex = indexOfLiteral(superUnionType)
		addInstruction(AvailSuperCall(
			tokens, messageIndex, returnIndex, superUnionIndex))
		// Pops all arguments.
		decreaseDepth(nArgs)
		// Pushes expected return type, to be overwritten by return value.
		increaseDepth()
	}

	/**
	 * Create a function from `CompiledCodeDescriptor compiled code` and the
	 * pushed outer (lexically bound) variables.
	 *
	 * @param tokens
	 *   The [A_Tuple] of [A_Token]s associated with this function closing
	 *   operation.
	 * @param compiledCode
	 *   The code from which to make a function.
	 * @param neededVariables
	 *   A [tuple][A_Tuple] of [declarations][DeclarationPhraseDescriptor] of
	 *   variables that the code needs to access.
	 */
	fun emitCloseCode(
		tokens: A_Tuple,
		compiledCode: A_RawFunction,
		neededVariables: A_Tuple)
	{
		for (variableDeclaration in neededVariables)
		{
			emitPushLocalOrOuter(tokens, variableDeclaration)
		}
		val codeIndex = indexOfLiteral(compiledCode)
		addInstruction(AvailCloseCode(
			tokens,
			neededVariables.tupleSize(),
			codeIndex))
		// Copied variables are popped.
		decreaseDepth(neededVariables.tupleSize())
		// Function is pushed.
		increaseDepth()
	}

	/**
	 * Emit code to duplicate the element at the top of the stack.
	 */
	fun emitDuplicate()
	{
		increaseDepth()
		addInstruction(AvailDuplicate(emptyTuple))
	}

	/**
	 * Emit code to get the value of a literal variable.
	 *
	 * @param tokens
	 *   The [A_Tuple] of [A_Token]s associated with this literal.
	 * @param aLiteral
	 *   The [variable][VariableDescriptor] that should have its value
	 *   extracted.
	 */
	fun emitGetLiteral(tokens: A_Tuple, aLiteral: A_BasicObject)
	{
		increaseDepth()
		val index = indexOfLiteral(aLiteral)
		addInstruction(AvailGetLiteralVariable(tokens, index))
	}

	/**
	 * Emit code to get the value of a local or outer (captured) variable.
	 *
	 * @param tokens
	 *   The [A_Tuple] of [A_Token]s associated with this call.
	 * @param localOrOuter
	 *   The [declaration][DeclarationPhraseDescriptor] of the variable that
	 *   should have its value extracted.
	 */
	fun emitGetLocalOrOuter(tokens: A_Tuple, localOrOuter: A_Phrase)
	{
		increaseDepth()
		if (varMap.containsKey(localOrOuter))
		{
			addInstruction(
				AvailGetLocalVariable(tokens, varMap[localOrOuter]!!))
			return
		}
		if (outerMap.containsKey(localOrOuter))
		{
			addInstruction(
				AvailGetOuterVariable(tokens, outerMap[localOrOuter]!!))
			return
		}
		assert(!labelInstructions.containsKey(localOrOuter)) {
			"This case should have been handled a different way!"
		}
		assert(false) { "Consistency error - unknown variable." }
	}

	/**
	 * Emit a [declaration][DeclarationPhraseDescriptor] of a
	 * [label][DeclarationKind.LABEL] for the current block.
	 *
	 * @param labelNode
	 *   The label declaration.
	 */
	fun emitLabelDeclaration(labelNode: A_Phrase)
	{
		assert(instructions.isEmpty()) {
			"Label must be first statement in block"
		}
		// stack is unaffected.
		addInstruction(labelInstructions[labelNode]!!)
	}

	/**
	 * Emit code to create a [tuple][A_Tuple] from the top `N` items on the
	 * stack.
	 *
	 * @param tokens
	 *   The [A_Tuple] of [A_Token]s associated with this call.
	 * @param count
	 *   How many pushed items to pop for the new tuple.
	 */
	fun emitMakeTuple(tokens: A_Tuple, count: Int)
	{
		addInstruction(AvailMakeTuple(tokens, count))
		decreaseDepth(count)
		increaseDepth()
	}

	/**
	 * Emit code to permute the top N items on the stack with the given
	 * `N`-element permutation.
	 *
	 * @param tokens
	 *   The [A_Tuple] of [A_Token]s associated with this call.
	 * @param permutation
	 *   A tuple of one-based integers that forms a permutation.
	 */
	fun emitPermute(tokens: A_Tuple, permutation: A_Tuple)
	{
		val index = indexOfLiteral(permutation)
		addInstruction(AvailPermute(tokens, index))
	}

	/**
	 * Emit code to pop the top value from the stack.
	 */
	fun emitPop()
	{
		addInstruction(AvailPop(emptyTuple))
		decreaseDepth(1)
	}

	/**
	 * Emit code to push a literal object onto the stack.
	 *
	 * @param tokens
	 *   The [A_Tuple] of [A_Token]s associated with this call.
	 * @param aLiteral
	 *   The object to push.
	 */
	fun emitPushLiteral(tokens: A_Tuple, aLiteral: A_BasicObject)
	{
		increaseDepth()
		val index = indexOfLiteral(aLiteral)
		addInstruction(AvailPushLiteral(tokens, index))
	}

	/**
	 * Push a variable.  It can be local to the current block or defined in an
	 * outer scope.
	 *
	 * @param tokens
	 *   The [A_Tuple] of [A_Token]s associated with this call.
	 * @param variableDeclaration
	 *   The variable declaration.
	 */
	fun emitPushLocalOrOuter(tokens: A_Tuple, variableDeclaration: A_Phrase)
	{
		increaseDepth()
		if (varMap.containsKey(variableDeclaration))
		{
			addInstruction(AvailPushLocalVariable(
				tokens, varMap[variableDeclaration]!!))
			return
		}
		if (outerMap.containsKey(variableDeclaration))
		{
			addInstruction(AvailPushOuterVariable(
				tokens, outerMap[variableDeclaration]!!))
			return
		}
		assert(labelInstructions.containsKey(variableDeclaration)) {
			"Consistency error - unknown variable."
		}
		addInstruction(AvailPushLabel(tokens))
	}

	/**
	 * Emit code to pop the stack and write the popped value into a literal
	 * variable.
	 *
	 * @param tokens
	 *   The [A_Tuple] of [A_Token]s associated with this call.
	 * @param aLiteral
	 *   The variable in which to write.
	 */
	fun emitSetLiteral(tokens: A_Tuple, aLiteral: A_BasicObject)
	{
		val index = indexOfLiteral(aLiteral)
		addInstruction(AvailSetLiteralVariable(tokens, index))
		//  Value to assign has been popped.
		decreaseDepth(1)
	}

	/**
	 * Emit code to pop the stack and write into a local or outer variable.
	 *
	 * @param tokens
	 *   The [tuple][A_Tuple] of [tokens][A_Token] that are associated with this
	 *   instruction.
	 * @param localOrOuter
	 *   The [declaration][DeclarationPhraseDescriptor] of the
	 *   [local][DeclarationKind.LOCAL_VARIABLE] or outer variable in which to
	 *   write.
	 */
	fun emitSetLocalOrOuter(tokens: A_Tuple, localOrOuter: A_Phrase)
	{
		decreaseDepth(1)
		if (varMap.containsKey(localOrOuter))
		{
			addInstruction(
				AvailSetLocalVariable(tokens, varMap[localOrOuter]!!))
			return
		}
		if (outerMap.containsKey(localOrOuter))
		{
			addInstruction(
				AvailSetOuterVariable(tokens, outerMap[localOrOuter]!!))
			return
		}
		assert(!labelInstructions.containsKey(localOrOuter)) {
			"You can't assign to a label!"
		}
		assert(false) { "Consistency error - unknown variable." }
	}

	/**
	 * Emit code to pop the stack and write it directly into a local slot of the
	 * continuation.  This is how local constants become initialized.
	 *
	 * @param tokens
	 *   The [A_Tuple] of [A_Token]s associated with this call.
	 * @param localConstant
	 *   The local constant declaration.
	 */
	fun emitSetLocalFrameSlot(
		tokens: A_Tuple,
		localConstant: A_Phrase)
	{
		assert(localConstant.declarationKind() === LOCAL_CONSTANT)
		assert(varMap.containsKey(localConstant)) {
			"Local constants can only be initialized at their definition."
		}
		decreaseDepth(1)
		addInstruction(AvailSetLocalConstant(tokens, varMap[localConstant]!!))
	}

	/**
	 * Add the [AvailInstruction] to the generated sequence.  Use the
	 * instruction's tuple of tokens if non-empty, otherwise examine the
	 * [tokensStack] for a better candidate.
	 *
	 * @param instruction
	 *   An instruction.
	 */
	private fun addInstruction(instruction: AvailInstruction)
	{
		if (instruction.relevantTokens.tupleSize() == 0)
		{
			// Replace it with the nearest tuple from the stack, which should
			// relate to the most specific position in the phrase tree for which
			// tokens are known.
			if (!tokensStack.isEmpty())
			{
				instruction.relevantTokens = tokensStack.last
			}
		}
		instructions.add(instruction)
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
	private fun fixFinalUses()
	{
		val localData = arrayOfNulls<AvailVariableAccessNote>(
			varMap.size).toMutableList()
		val outerData = arrayOfNulls<AvailVariableAccessNote>(
			outerMap.size).toMutableList()
		for (instruction in instructions)
		{
			instruction.fixUsageFlags(localData, outerData, this)
		}
		val p = primitive
		if (p !== null)
		{
			// If necessary, prevent clearing of the primitive failure variable
			// after its last usage.
			if (p.hasFlag(Flag.PreserveFailureVariable))
			{
				assert(!p.hasFlag(Flag.CannotFail))
				val fakeFailureVariableUse = AvailGetLocalVariable(
					emptyTuple, numArgs + 1)
				fakeFailureVariableUse.fixUsageFlags(
					localData, outerData, this)
			}
			// If necessary, prevent clearing of the primitive arguments after
			// their last usage.
			if (p.hasFlag(Flag.PreserveArguments))
			{
				for (index in 1 .. numArgs)
				{
					val fakeArgumentUse =
						AvailPushLocalVariable(emptyTuple, index)
					fakeArgumentUse.fixUsageFlags(localData, outerData, this)
				}
			}
		}
	}

	companion object
	{
		/**
		 * Generate a [raw&#32;function][A_RawFunction] with the supplied
		 * properties.
		 *
		 * @param module
		 *   The module in which the code occurs.
		 * @param blockPhrase
		 *   The block phrase from which the raw function is being generated.
		 * @return
		 *   A raw function.
		 */
		fun generateFunction(
			module: A_Module,
			blockPhrase: A_Phrase): A_RawFunction
		{
			val primitive = blockPhrase.primitive()
			val resultTypeIfPrimitiveFails =
				blockPhrase.statementsTuple().run {
					when
					{
						primitive == null -> blockPhrase.resultType()
						primitive.hasFlag(Flag.CannotFail) -> bottom
						tupleSize() == 0 -> Types.TOP.o
						else -> tupleAt(tupleSize()).phraseExpressionType()
					}
				}
			val generator = AvailCodeGenerator(
				module,
				toList(blockPhrase.argumentsTuple()),
				primitive,
				locals(blockPhrase),
				constants(blockPhrase),
				labels(blockPhrase),
				toList(blockPhrase.neededVariables()),
				blockPhrase.resultType(),
				resultTypeIfPrimitiveFails,
				blockPhrase.declaredExceptions(),
				blockPhrase.startingLineNumber())
			generator.stackShouldBeEmpty()
			val statementsTuple = blockPhrase.statementsTuple()
			val statementsCount = statementsTuple.tupleSize()
			if (statementsCount == 0
				&& (primitive === null || primitive.canHaveNybblecodes()))
			{
				// Ideally, we could capture just the close-square-bracket here.
				generator.emitPushLiteral(emptyTuple, nil)
			}
			else
			{
				for (index in 1 until statementsCount)
				{
					statementsTuple.tupleAt(index).emitEffectOn(generator)
					generator.stackShouldBeEmpty()
				}
				if (statementsCount > 0)
				{
					val lastStatement = statementsTuple.tupleAt(statementsCount)
					if (lastStatement.phraseKindIsUnder(LABEL_PHRASE)
						|| (lastStatement.phraseKindIsUnder(ASSIGNMENT_PHRASE)
							&& lastStatement.phraseExpressionType().isTop))
					{
						// Either the block 1) ends with the label declaration
						// or 2) is top-valued and ends with an assignment. Push
						// the nil object as the return value. Ideally, we could
						// capture just the close-square-bracket token here.
						lastStatement.emitEffectOn(generator)
						generator.emitPushLiteral(emptyTuple, nil)
					}
					else
					{
						lastStatement.emitValueOn(generator)
					}
				}
			}
			return generator.endBlock(blockPhrase)
		}

		/**
		 * Answer the type that should be captured in the literal frame for the
		 * given declaration.  In the case that it's a variable declaration, we
		 * need to wrap the declared type in a variable type.
		 *
		 * @param declaration
		 *   The [declaration][DeclarationPhraseDescriptor] to examine.
		 * @return
		 *   The type for the corresponding
		 *   [continuation][ContinuationDescriptor] slot.
		 */
		private fun outerType(declaration: A_Phrase): A_Type
		{
			return if (declaration.declarationKind().isVariable)
				variableTypeFor(declaration.declaredType())
			else
				declaration.declaredType()
		}
	}
}
