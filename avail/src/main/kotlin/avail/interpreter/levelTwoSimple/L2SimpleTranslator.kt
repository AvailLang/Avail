/*
 * L2SimpleTranslator.kt
 * Copyright © 1993-2022, The Avail Foundation, LLC.
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
package avail.interpreter.levelTwoSimple

import avail.AvailRuntime.HookType.INVALID_MESSAGE_SEND
import avail.AvailRuntime.HookType.RESULT_DISAGREED_WITH_EXPECTED_TYPE
import avail.descriptor.bundles.A_Bundle
import avail.descriptor.bundles.A_Bundle.Companion.bundleMethod
import avail.descriptor.bundles.A_Bundle.Companion.numArgs
import avail.descriptor.functions.A_Function
import avail.descriptor.functions.A_RawFunction
import avail.descriptor.functions.A_RawFunction.Companion.literalAt
import avail.descriptor.functions.A_RawFunction.Companion.localTypeAt
import avail.descriptor.functions.A_RawFunction.Companion.numArgs
import avail.descriptor.functions.A_RawFunction.Companion.numLocals
import avail.descriptor.functions.A_RawFunction.Companion.numOuters
import avail.descriptor.functions.A_RawFunction.Companion.numSlots
import avail.descriptor.functions.A_RawFunction.Companion.outerTypeAt
import avail.descriptor.functions.A_RawFunction.Companion.setStartingChunkAndReoptimizationCountdown
import avail.descriptor.functions.CompiledCodeDescriptor.L1InstructionDecoder
import avail.descriptor.functions.FunctionDescriptor.Companion.createFunction
import avail.descriptor.methods.A_ChunkDependable
import avail.descriptor.methods.A_Method.Companion.definitionsAtOrBelow
import avail.descriptor.methods.A_Method.Companion.numArgs
import avail.descriptor.methods.A_Sendable.Companion.bodyBlock
import avail.descriptor.methods.A_Sendable.Companion.bodySignature
import avail.descriptor.methods.A_Sendable.Companion.isMethodDefinition
import avail.descriptor.representation.AvailObject
import avail.descriptor.sets.SetDescriptor.Companion.setFromCollection
import avail.descriptor.tuples.A_Tuple.Companion.tupleIntAt
import avail.descriptor.tuples.A_Tuple.Companion.tupleSize
import avail.descriptor.tuples.TupleDescriptor.Companion.emptyTuple
import avail.descriptor.types.A_Type
import avail.descriptor.types.A_Type.Companion.acceptsListOfArgTypes
import avail.descriptor.types.A_Type.Companion.argsTupleType
import avail.descriptor.types.A_Type.Companion.functionType
import avail.descriptor.types.A_Type.Companion.isSubtypeOf
import avail.descriptor.types.A_Type.Companion.readType
import avail.descriptor.types.A_Type.Companion.returnType
import avail.descriptor.types.A_Type.Companion.typeAtIndex
import avail.descriptor.types.A_Type.Companion.typeIntersection
import avail.descriptor.types.A_Type.Companion.typeUnion
import avail.descriptor.types.BottomTypeDescriptor.Companion.bottom
import avail.descriptor.types.ContinuationTypeDescriptor.Companion.continuationTypeForFunctionType
import avail.descriptor.types.TupleTypeDescriptor.Companion.tupleTypeForTypesList
import avail.interpreter.execution.Interpreter
import avail.interpreter.levelOne.L1OperationDispatcher
import avail.interpreter.levelTwo.L2JVMChunk.Companion.unoptimizedChunk
import avail.interpreter.levelTwo.L2SimpleChunk
import avail.interpreter.levelTwo.operand.TypeRestriction
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.nilRestriction
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.restrictionForConstant
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.restrictionForType
import avail.interpreter.levelTwo.operand.TypeRestriction.RestrictionFlagEncoding.BOXED_FLAG
import avail.interpreter.levelTwo.operand.TypeRestriction.RestrictionFlagEncoding.IMMUTABLE_FLAG
import avail.optimizer.OptimizationLevel
import avail.performance.Statistic
import avail.performance.StatisticReport.L2_OPTIMIZATION_TIME
import avail.utility.notNullAnd

/**
 * An [L2SimpleTranslator] produces a fast translation from L1 nybblecodes into
 * a sequence of [L2SimpleInstruction]s within an [L2SimpleExecutableChunk].
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
class L2SimpleTranslator
constructor(
	val code: A_RawFunction,
	private val nextOptimizationLevel: OptimizationLevel,
	val interpreter: Interpreter,
) : L1OperationDispatcher
{
	/** The types of the registers at this point. */
	val restrictions = MutableList(code.numSlots + 1) { nilRestriction }

	/** The list of [L2SimpleInstruction]s being generated. */
	val instructions = mutableListOf<L2SimpleInstruction>()

	/** The current operand stack pointer during code generation. */
	var stackp = code.numSlots + 1

	/** The [L1InstructionDecoder] that provides L1 operations and operands. */
	val instructionDecoder = L1InstructionDecoder()

	/** The current program counter, taken from the [instructionDecoder]. */
	val pc: Int get() = instructionDecoder.pc()

	/**
	 * The [A_ChunkDependable]s which, if changed, should invalidate the chunk
	 * being constructed.
	 */
	private val contingentValues = mutableSetOf<A_ChunkDependable>()

	/**
	 * Create an L2Chunk.  This must only be performed while interpreters are
	 * able to run.  Invalidation happens within safe-points, because that's
	 * when method definitions can be added and removed.
	 */
	fun createChunk(): L2SimpleChunk
	{
		val funType = code.functionType()
		// Set up the restriction for r[0], the current function.
		restrictions[0] = restrictionForType(funType, BOXED_FLAG)
		// Set up the restrictions for the arguments r[1]..r[n].
		val paramTypes = funType.argsTupleType
		val numArgs = code.numArgs()
		for (i in 1.. numArgs)
		{
			restrictions[i] = restrictionForType(
				paramTypes.typeAtIndex(i), BOXED_FLAG)
		}
		// Also set up the local variables (but not constants).
		for (i in 1..code.numLocals)
		{
			restrictions[i + numArgs] = restrictionForType(
				code.localTypeAt(i), BOXED_FLAG)
		}
		code.setUpInstructionDecoder(instructionDecoder)
		instructionDecoder.pc(1)
		add(
			L2Simple_CheckForInterrupt(
				stackp,
				pc,
				instructions.size + 1,
				liveIndices()))
		while (!instructionDecoder.atEnd())
		{
			instructionDecoder.getOperation().dispatch(this)
		}
		assert(stackp == code.numSlots) {
			"One value should have been left on stack"
		}
		val chunk = L2SimpleChunk.allocate(
			code,
			// See L2SimpleExecutableChunk.runChunk()
			if (code.codePrimitive() == null) 0 else -1,
			instructions,
			setFromCollection(contingentValues),
			nextOptimizationLevel)
		code.setStartingChunkAndReoptimizationCountdown(
			chunk, nextOptimizationLevel.countdown)
		return chunk
	}

	private fun add(instruction: L2SimpleInstruction)
	{
		instructions.add(instruction)
	}

	/**
	 * Generate a dispatched call of this bundle's method.  The arguments are
	 * already on the stack, and should be popped.  After a successful lookup,
	 * proceed with an invocation as per [generateGeneralInvocation].  If the
	 * lookup is unsuccessful, invoke the [INVALID_MESSAGE_SEND] hook function.
	 * When the invocation of the hook function invariably returns, requesting
	 * reification (because the hook function is ⊥-valued), synthesize a
	 * continuation with the arguments popped and the expected value pushed,
	 * using the [unoptimizedChunk] (although it won't be resumable).
	 */
	private fun generateGeneralCall(
		bundle: A_Bundle,
		expectedType: A_Type,
		superUnionType: A_Type)
	{
		val numArgs = bundle.numArgs
		val registerIndices = liveIndices(
			stackp - numArgs + 1 until stackp)
		// In the event that this is a *zero-argument call*, we must ensure the
		// stack preserves the expectedType that will be pushed.
		registerIndices[stackp - 1] = stackp
		val argTypes = (stackp downTo stackp - numArgs + 1).map {
			restrictions[it]
		}
		// Now, figure out which actual method definitions might be called, take
		// the union of their return types, then intersect it with the expected
		// type.  This strengthened type might make subsequent calls, using the
		// returned value as an argument, more restrictive, ideally monomorphic.
		val possible = bundle.bundleMethod.definitionsAtOrBelow(argTypes)
			.filter { it.isMethodDefinition() }
		val possibleType = possible.fold(bottom) { typeUnion, def ->
			typeUnion.typeUnion(def.bodySignature().returnType)
		}
		if (superUnionType.isBottom)
		{
			instructions.add(
				L2Simple_GeneralCall(
					stackp,
					pc,
					instructions.size + 1,
					registerIndices,
					expectedType,
					!possibleType.isSubtypeOf(expectedType),
					bundle))
		}
		else
		{
			instructions.add(
				L2Simple_SuperCall(
					stackp,
					pc,
					instructions.size + 1,
					registerIndices,
					expectedType,
					!possibleType.isSubtypeOf(expectedType),
					bundle,
					superUnionType))
		}
		for (i in stackp - numArgs + 1 until stackp)
		{
			restrictions[i] = nilRestriction
		}
		restrictions[stackp] = restrictionForType(
			possibleType.typeIntersection(expectedType), BOXED_FLAG)
	}

	/**
	 * Generate an invocation of the given function.  The arguments are already
	 * on the stack, and known to conform with the function's argument types.
	 *
	 * After the completed invocation, if the call's result meets the expected
	 * type, the result should be on the stack.  If during the invocation a
	 * reification happens, a continuation using the [unoptimizedChunk] should
	 * be created, with the arguments popped and the expected *type* pushed on
	 * the stack. If after an unreified call, the returned value does not
	 * conform to the [expectedType], the [RESULT_DISAGREED_WITH_EXPECTED_TYPE]
	 * hook function should be invoked, with suitable arguments.  That
	 * Kotlin-level call can only return for reification, since the Avail
	 * function must have a return type of ⊥. When the reification happens, a
	 * continuation is created which is identical to what would be created had
	 * the original invocation itself requested reification.
	 *
	 * Answer the [TypeRestriction] that is guaranteed to hold for the return
	 * value *after* it has been checked successfully against the expectedType.
	 */
	private fun generateGeneralInvocation(
		calledFunction: A_Function,
		expectedType: A_Type
	): TypeRestriction
	{
		val calledCode = calledFunction.code()
		val numArgs = calledCode.numArgs()
		val registerIndices = liveIndices(
			stackp - numArgs + 1 until stackp)
		// In the event that this is a *zero-argument call*, we must ensure the
		// stack preserves the expectedType that will be pushed.
		registerIndices[stackp - 1] = stackp
		val prim = calledCode.codePrimitive()
		val guaranteedReturnType = when
		{
			prim != null ->
				prim.returnTypeGuaranteedByVM(
					calledCode,
					(stackp downTo stackp - numArgs + 1).map { i ->
						restrictions[i].type
					}
				).typeIntersection(calledCode.functionType().returnType)
			else -> calledCode.functionType().returnType
		}
		instructions.add(
			L2Simple_Invoke(
				stackp,
				pc,
				instructions.size + 1,
				registerIndices,
				expectedType,
				!guaranteedReturnType.isSubtypeOf(expectedType),
				calledFunction))
		return restrictionForType(
			guaranteedReturnType.typeIntersection(expectedType), BOXED_FLAG)
	}

	/**
	 * Answer an [Array] of register indices which should constitute a reified
	 * continuation at this position in the code.  For slots that are known to
	 * be nil, rather than go to the effort of actually clearing them, we've set
	 * their restriction to the [nilRestriction], so a '0' is used to indicate
	 * we want to store a nil in the corresponding slot (since `registers[0]` is
	 * reserved for the current function).
	 */
	private fun liveIndices(
		rangeToNil: IntRange? = null
	): Array<Int>
	{
		val array = Array(restrictions.size - 1) { zeroIndex ->
			val index = zeroIndex + 1
			when
			{
				restrictions[index].constantOrNull.notNullAnd { isNil } -> 0
				rangeToNil.notNullAnd { contains(index) } -> 0
				else -> index
			}
		}
		return interpreter.arraysForL2Simple
			.computeIfAbsent(array.asList()) { array }
	}


	override fun L1_doCall()
	{
		val bundle = code.literalAt(instructionDecoder.getOperand())
		val expectedType = code.literalAt(instructionDecoder.getOperand())
		val method = bundle.bundleMethod
		contingentValues.add(method)
		val numArgs = method.numArgs
		stackp += numArgs -1
		val argRestrictions = (stackp downTo stackp - numArgs + 1)
			.map { restrictions[it] }
		val argTypes = argRestrictions.map(TypeRestriction::type)
		val possible = method.definitionsAtOrBelow(argRestrictions)
		val only = possible.singleOrNull()
		if (only === null
			|| !only.isMethodDefinition()
			|| !only.bodySignature().acceptsListOfArgTypes(argTypes))
		{
			// Fall back to dynamic dispatch.
			generateGeneralCall(bundle, expectedType, bottom)
			return
		}
		val calledFunction = only.bodyBlock()
		val calledCode = calledFunction.code()
		// We now know the exact method definition that will be invoked.
		val primitive = calledCode.codePrimitive()
		var outputRestriction: TypeRestriction? = null
		if (primitive != null)
		{
			outputRestriction = primitive.attemptToGenerateSimpleInvocation(
				this, calledFunction, argRestrictions, expectedType)
		}
		if (outputRestriction == null)
		{
			// Nothing was generated, so fall back.
			outputRestriction =
				generateGeneralInvocation(calledFunction, expectedType)
		}
		for (i in stackp - numArgs + 1 until stackp)
		{
			restrictions[i] = nilRestriction
		}
		restrictions[stackp] =
			outputRestriction.intersectionWithType(expectedType)
	}

	override fun L1_doPushLiteral()
	{
		val value = code.literalAt(instructionDecoder.getOperand())
		add(L2Simple_MoveConstant(value, --stackp))
		restrictions[stackp] =
			restrictionForConstant(value, BOXED_FLAG).withFlag(IMMUTABLE_FLAG)
	}

	override fun L1_doPushLastLocal()
	{
		val local = instructionDecoder.getOperand()
		add(L2Simple_Move(local, --stackp))
		restrictions[stackp] = restrictions[local]
		restrictions[local] = nilRestriction
	}

	override fun L1_doPushLocal()
	{
		val local = instructionDecoder.getOperand()
		--stackp
		val oldRestriction = restrictions[local]
		when (oldRestriction.hasFlag(IMMUTABLE_FLAG))
		{
			// No need to make the value immutable – it already is.
			true -> add(L2Simple_Move(local, stackp))
			// Ensure the value is made immutable.
			else -> add(L2Simple_MoveAndMakeImmutable(local, stackp))
		}
		val newRestriction = oldRestriction.withFlag(IMMUTABLE_FLAG)
		restrictions[stackp] = newRestriction
		restrictions[local] = newRestriction
	}

	override fun L1_doPushLastOuter()
	{
		val outer = instructionDecoder.getOperand()
		add(L2Simple_PushLastOuter(outer, --stackp))
		restrictions[stackp] =
			restrictionForType(code.outerTypeAt(outer), BOXED_FLAG)
	}

	override fun L1_doClose()
	{
		val numOuters = instructionDecoder.getOperand()
		val rawFunction = code.literalAt(instructionDecoder.getOperand())
		assert(rawFunction.numOuters == numOuters)
		val oldStackp = stackp
		stackp += numOuters - 1
		add(
			when (numOuters)
			{
				0 -> L2Simple_MoveConstant(
					createFunction(code, emptyTuple) as AvailObject, stackp)
				1 -> L2Simple_CloseFunction1(rawFunction, stackp)
				2 -> L2Simple_CloseFunction2(rawFunction, stackp)
				3 -> L2Simple_CloseFunction3(rawFunction, stackp)
				4 -> L2Simple_CloseFunction4(rawFunction, stackp)
				else -> L2Simple_CloseFunctionN(rawFunction, stackp)
			})
		for (i in oldStackp until stackp)
			restrictions[i] = nilRestriction
		restrictions[stackp] =
			restrictionForType(rawFunction.functionType, BOXED_FLAG)
	}

	override fun L1_doSetLocal()
	{
		val variable = instructionDecoder.getOperand()
		add(
			L2Simple_SetVariable(
				stackp,
				pc,
				instructions.size + 1,
				liveIndices(),
				variable))
		restrictions[stackp++] = nilRestriction
	}

	override fun L1_doGetLocalClearing()
	{
		val local = instructionDecoder.getOperand()
		add(
			L2Simple_GetVariableClearing(
				--stackp,
				pc,
				instructions.size + 1,
				liveIndices(),
				local))
		restrictions[stackp] =
			restrictionForType(restrictions[local].type.readType, BOXED_FLAG)
	}

	override fun L1_doPushOuter()
	{
		val outer = instructionDecoder.getOperand()
		add(L2Simple_PushOuter(outer, --stackp))
		restrictions[stackp] =
			restrictionForType(code.outerTypeAt(outer), BOXED_FLAG)
	}

	override fun L1_doPop()
	{
		stackp++
		restrictions[stackp - 1] = nilRestriction
	}

	override fun L1_doGetOuterClearing()
	{
		val outer = instructionDecoder.getOperand()
		add(
			L2Simple_GetOuterClearing(
				--stackp,
				pc,
				instructions.size + 1,
				liveIndices(),
				outer))
		restrictions[stackp] = restrictionForType(
			code.outerTypeAt(outer).readType, BOXED_FLAG)
	}

	override fun L1_doSetOuter()
	{
		val outer = instructionDecoder.getOperand()
		add(
			L2Simple_SetOuter(
				stackp,
				pc,
				instructions.size + 1,
				liveIndices(),
				outer))
		restrictions[stackp++] = nilRestriction
	}

	override fun L1_doGetLocal()
	{
		val local = instructionDecoder.getOperand()
		add(
			L2Simple_GetVariable(
				--stackp,
				pc,
				instructions.size + 1,
				liveIndices(),
				local))
		restrictions[stackp] = restrictionForType(
			restrictions[local].type.readType, BOXED_FLAG)
	}

	override fun L1_doMakeTuple()
	{
		val size = instructionDecoder.getOperand()
		val oldStackp = stackp
		stackp += size - 1
		add(
			when (size)
			{
				0 -> L2Simple_MoveConstant(emptyTuple, stackp)
				1 -> L2Simple_MakeTuple1(stackp)
				2 -> L2Simple_MakeTuple2(stackp)
				3 -> L2Simple_MakeTuple3(stackp)
				else -> L2Simple_MakeTupleN(size, stackp)
			})
		val types = (oldStackp .. stackp).map {
			restrictions[it].type
		}
		for (i in oldStackp until stackp)
			restrictions[i] = nilRestriction
		restrictions[stackp] =
			restrictionForType(tupleTypeForTypesList(types), BOXED_FLAG)
	}

	override fun L1_doGetOuter()
	{
		val outer = instructionDecoder.getOperand()
		add(
			L2Simple_GetOuter(
				--stackp,
				pc,
				instructions.size + 1,
				liveIndices(),
				outer))
		restrictions[stackp] = restrictionForType(
			code.outerTypeAt(outer).readType, BOXED_FLAG)
	}

	override fun L1_doExtension()
	{
		assert(false) { "Illegal dispatch nybblecode" }
	}

	override fun L1Ext_doPushLabel()
	{
		// Update the restriction (to include the pushed label) prior to
		// emitting the instruction, so that if reification of the stack has to
		// happen in order to construct the label, the continuation that
		// continues running (right after the push) will see that the pushed
		// label has indeed been preserved across the reification and reentry.
		--stackp
		restrictions[stackp] = restrictionForType(
			continuationTypeForFunctionType(code.functionType()), BOXED_FLAG)
		add(
			L2Simple_PushLabel(
				stackp,
				pc,
				instructions.size + 1,
				liveIndices()))
	}

	override fun L1Ext_doGetLiteral()
	{
		val variable = code.literalAt(instructionDecoder.getOperand())
		add(
			L2Simple_GetConstant(
				--stackp,
				pc,
				instructions.size + 1,
				liveIndices(),
				variable))
		restrictions[stackp] = restrictionForType(
			variable.kind().readType, BOXED_FLAG)
	}

	override fun L1Ext_doSetLiteral()
	{
		val variable = code.literalAt(instructionDecoder.getOperand())
		add(
			L2Simple_SetConstant(
				stackp,
				pc,
				instructions.size + 1,
				liveIndices(),
				variable))
		restrictions[stackp++] = nilRestriction
	}

	override fun L1Ext_doDuplicate()
	{
		val oldRestriction = restrictions[stackp]
		when (oldRestriction.hasFlag(IMMUTABLE_FLAG))
		{
			// No need to make the value immutable – it already is.
			true -> add(L2Simple_Move(stackp, stackp - 1))
			// Ensure the value is made immutable.
			else -> add(L2Simple_MoveAndMakeImmutable(stackp, stackp - 1))
		}
		val newRestriction = oldRestriction.withFlag(IMMUTABLE_FLAG)
		restrictions[stackp] = newRestriction
		restrictions[--stackp] = newRestriction
	}

	override fun L1Ext_doPermute()
	{
		val permutation = code.literalAt(instructionDecoder.getOperand())
		val size = permutation.tupleSize
		val reads = Array(size) { -1 }
		val earliestStackp = stackp + size - 1
		for (i in 1..size)
		{
			reads[permutation.tupleIntAt(i) - 1] = earliestStackp + 1 - i
		}
		val readRestrictions = reads.map { restrictions[it] }
		val writes = Array(size) { earliestStackp - it }
		add(L2Simple_Permute(reads, writes))
		// Permute the restrictions as well.
		readRestrictions.zip(writes).forEach { (restriction, writeIndex) ->
			restrictions[writeIndex] = restriction
		}
	}

	override fun L1Ext_doSuperCall()
	{
		val bundle = code.literalAt(instructionDecoder.getOperand())
		val expectedType = code.literalAt(instructionDecoder.getOperand())
		val superUnionType = code.literalAt(instructionDecoder.getOperand())
		// Fall back to dynamic dispatch for now.
		stackp += bundle.bundleMethod.numArgs - 1
		generateGeneralCall(bundle, expectedType, superUnionType)
		return
	}

	override fun L1Ext_doSetLocalSlot()
	{
		val localSlot = instructionDecoder.getOperand()
		add(L2Simple_Move(stackp, localSlot))
		restrictions[localSlot] = restrictions[stackp]
		restrictions[stackp++] = nilRestriction
	}

	companion object {
		/** Translate the code into an [L2SimpleChunk]. */
		fun translateToLevelTwoSimple(
			code: A_RawFunction,
			nextOptimizationLevel: OptimizationLevel,
			interpreter: Interpreter): L2SimpleChunk
		{
			return simpleTranslationStat.record(interpreter.interpreterIndex) {
				val translator = L2SimpleTranslator(
					code, nextOptimizationLevel, interpreter)
				translator.createChunk()
			}
		}

		/** Statistics for timing the translation per L1Operation. */
		private val simpleTranslationStat =
			Statistic(L2_OPTIMIZATION_TIME, "L2Simple translation")
	}
}
