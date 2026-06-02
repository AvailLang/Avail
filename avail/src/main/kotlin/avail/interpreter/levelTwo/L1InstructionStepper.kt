/*
 * L1InstructionStepper.kt
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
package avail.interpreter.levelTwo

import avail.AvailDebuggerModel
import avail.AvailRuntime
import avail.AvailRuntime.HookType
import avail.AvailRuntimeSupport
import avail.descriptor.atoms.A_Atom.Companion.atomName
import avail.descriptor.bundles.A_Bundle
import avail.descriptor.bundles.A_Bundle.Companion.bundleMethod
import avail.descriptor.bundles.A_Bundle.Companion.message
import avail.descriptor.fiber.A_Fiber.Companion.continuation
import avail.descriptor.fiber.A_Fiber.Companion.executionState
import avail.descriptor.fiber.A_Fiber.Companion.fiberHelper
import avail.descriptor.fiber.A_Fiber.Companion.getAndSetSynchronizationFlag
import avail.descriptor.fiber.FiberDescriptor.ExecutionState.PAUSED
import avail.descriptor.fiber.FiberDescriptor.ExecutionState.RUNNING
import avail.descriptor.fiber.FiberDescriptor.SynchronizationFlag.BOUND
import avail.descriptor.functions.A_Continuation
import avail.descriptor.functions.A_Continuation.Companion.function
import avail.descriptor.functions.A_Continuation.Companion.replacingCaller
import avail.descriptor.functions.A_Function
import avail.descriptor.functions.A_Function.Companion.optionallyNilOuterVar
import avail.descriptor.functions.A_RawFunction.Companion.literalAt
import avail.descriptor.functions.A_RawFunction.Companion.lookupStat
import avail.descriptor.functions.A_RawFunction.Companion.methodName
import avail.descriptor.functions.A_RawFunction.Companion.numArgs
import avail.descriptor.functions.A_RawFunction.Companion.numSlots
import avail.descriptor.functions.A_RawFunction.Companion.returneeCheckStat
import avail.descriptor.functions.A_RawFunction.Companion.returnerCheckStat
import avail.descriptor.functions.CompiledCodeDescriptor.L1InstructionDecoder
import avail.descriptor.functions.ContinuationDescriptor.Companion.createContinuationWithFrame
import avail.descriptor.functions.ContinuationDescriptor.Companion.createLabelContinuation
import avail.descriptor.functions.FunctionDescriptor.Companion.createExceptOuters
import avail.descriptor.methods.A_Definition
import avail.descriptor.methods.A_Definition.Companion.definitionMethod
import avail.descriptor.methods.A_Method
import avail.descriptor.methods.A_Method.Companion.lookupByTypesFromTuple
import avail.descriptor.methods.A_Method.Companion.lookupByValuesFromList
import avail.descriptor.methods.A_Method.Companion.numArgs
import avail.descriptor.methods.A_Sendable.Companion.bodyBlock
import avail.descriptor.methods.A_Sendable.Companion.isAbstractDefinition
import avail.descriptor.methods.A_Sendable.Companion.isForwardDefinition
import avail.descriptor.methods.A_Sendable.Companion.isMethodDefinition
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.AvailObject
import avail.descriptor.representation.Mutability.IMMUTABLE
import avail.descriptor.representation.Mutability.MUTABLE
import avail.descriptor.representation.NilDescriptor.Companion.nil
import avail.descriptor.tuples.A_String.Companion.asNativeString
import avail.descriptor.tuples.A_Tuple
import avail.descriptor.tuples.A_Tuple.Companion.tupleIntAt
import avail.descriptor.tuples.A_Tuple.Companion.tupleSize
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.generateObjectTupleFrom
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.generateReversedFrom
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.optimizedTuple
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tupleFromList
import avail.descriptor.tuples.TupleDescriptor.Companion.emptyTuple
import avail.descriptor.types.A_Type
import avail.descriptor.types.A_Type.Companion.typeAtIndex
import avail.descriptor.types.A_Type.Companion.typeUnion
import avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.instanceTypeOrMetaOn
import avail.descriptor.types.PrimitiveTypeDescriptor.Types
import avail.descriptor.variables.A_Variable
import avail.descriptor.variables.A_Variable.Companion.getValue
import avail.descriptor.variables.A_Variable.Companion.getValueClearing
import avail.descriptor.variables.A_Variable.Companion.setValueNoCheck
import avail.descriptor.variables.VariableDescriptor.Companion.newVariableWithContentType
import avail.exceptions.AvailErrorCode
import avail.exceptions.AvailErrorCode.E_OBSERVED_VARIABLE_WRITTEN_WHILE_UNTRACED
import avail.exceptions.MethodDefinitionException
import avail.exceptions.VariableGetException
import avail.exceptions.VariableSetException
import avail.interpreter.execution.Interpreter
import avail.interpreter.execution.Interpreter.Companion.assignmentFunction
import avail.interpreter.execution.Interpreter.Companion.log
import avail.interpreter.levelOne.L1Operation
import avail.interpreter.levelOne.L1Operation.Ordinals.L1Ext_doDuplicate_ord
import avail.interpreter.levelOne.L1Operation.Ordinals.L1Ext_doGetLiteral_ord
import avail.interpreter.levelOne.L1Operation.Ordinals.L1Ext_doPermute_ord
import avail.interpreter.levelOne.L1Operation.Ordinals.L1Ext_doPushLabel_ord
import avail.interpreter.levelOne.L1Operation.Ordinals.L1Ext_doSetLiteral_ord
import avail.interpreter.levelOne.L1Operation.Ordinals.L1Ext_doSetLocalSlot_ord
import avail.interpreter.levelOne.L1Operation.Ordinals.L1Ext_doSuperCall_ord
import avail.interpreter.levelOne.L1Operation.Ordinals.L1_doCall_ord
import avail.interpreter.levelOne.L1Operation.Ordinals.L1_doClose_ord
import avail.interpreter.levelOne.L1Operation.Ordinals.L1_doExtension_ord
import avail.interpreter.levelOne.L1Operation.Ordinals.L1_doGetLastOuter_ord
import avail.interpreter.levelOne.L1Operation.Ordinals.L1_doGetLocalClearing_ord
import avail.interpreter.levelOne.L1Operation.Ordinals.L1_doGetLocal_ord
import avail.interpreter.levelOne.L1Operation.Ordinals.L1_doGetOuter_ord
import avail.interpreter.levelOne.L1Operation.Ordinals.L1_doMakeTuple_ord
import avail.interpreter.levelOne.L1Operation.Ordinals.L1_doPop_ord
import avail.interpreter.levelOne.L1Operation.Ordinals.L1_doPushLastLocal_ord
import avail.interpreter.levelOne.L1Operation.Ordinals.L1_doPushLastOuter_ord
import avail.interpreter.levelOne.L1Operation.Ordinals.L1_doPushLiteral_ord
import avail.interpreter.levelOne.L1Operation.Ordinals.L1_doPushLocal_ord
import avail.interpreter.levelOne.L1Operation.Ordinals.L1_doPushOuter_ord
import avail.interpreter.levelOne.L1Operation.Ordinals.L1_doSetLocal_ord
import avail.interpreter.levelOne.L1Operation.Ordinals.L1_doSetOuter_ord
import avail.optimizer.DefaultL1ExecutableChunk
import avail.optimizer.DefaultL1ExecutableChunk.DefaultEntryPoint
import avail.optimizer.DefaultL1ExecutableChunk.DefaultEntryPoint.AFTER_PRIMITIVE_FAILURE
import avail.optimizer.DefaultL1ExecutableChunk.DefaultEntryPoint.AFTER_REIFICATION_FOR_LABEL_CREATION
import avail.optimizer.DefaultL1ExecutableChunk.DefaultL1Chunk
import avail.optimizer.StackReifier
import avail.optimizer.StackReifier.AfterReification.CONTINUE_FIBER
import avail.optimizer.StackReifier.AfterReification.SWITCH_FROM_FIBER
import avail.optimizer.jvm.ReferencedInGeneratedCode
import avail.performance.Statistic
import avail.performance.StatisticReport.REIFICATIONS
import avail.utility.Strings.truncateTo
import avail.utility.cast
import java.util.logging.Level
import java.util.regex.Pattern

/**
 * This class is used to simulate the effect of level one nybblecodes during
 * execution of the [DefaultL1ExecutableChunk], on behalf of an [Interpreter].
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @property interpreter
 *   The [Interpreter] on whose behalf to step level one nybblecodes.
 * @constructor
 * Construct a new `L1InstructionStepper`.
 *
 * @param interpreter
 * The [Interpreter] on whose behalf to step through level one nybblecode
 * instructions.
 */
class L1InstructionStepper constructor(val interpreter: Interpreter)
{
	/** The current position in the nybblecodes. */
	val instructionDecoder = L1InstructionDecoder()

	var stashedFrameAtPushLabel: Array<AvailObject>? = null

	var stashedPcAtPushLabel: Int = Int.MIN_VALUE

	var stashedStackpAtPushLabel: Int = Int.MIN_VALUE

	/**
	 * Get the current program counter.
	 */
	fun pc(): Int = instructionDecoder.pc

	/**
	 * A simple delegation to keep the run() code a bit smaller.  Fetch an
	 * operand [Int] from the [instructionDecoder].
	 */
	fun getOperand() = instructionDecoder.getOperand()

	/**
	 * Run the current code until it reaches the end.  Individual instructions,
	 * such as calls, may be subject to [reification][StackReifier], which
	 * should cause a suitable [A_Continuation] to be reified.  In
	 * addition, inter-nybblecode interrupts may also trigger reification, but
	 * they'll handle their own reification prior to returning here with a
	 * suitable [StackReifier] (to update and return again from here).
	 *
	 * @param frame
	 *   The current frame, containing the operand stack, arguments, and locals
	 *   manipulated by the instruction stepper.
	 * @param startingPc
	 *   The L1 program counter at which the stepper should resume running.
	 * @param startingStackp
	 *   The stack pointer at the position where the stepper should resume
	 *   running.
	 * @return
	 *   The [AvailObject] produced by running the function, or `null` if a
	 *   reification is happening.
	 */
	@ReferencedInGeneratedCode
	fun run(
		frame: Array<AvailObject>,
		startingPc: Int,
		startingStackp: Int
	): AvailObject?
	{
		val function = interpreter.function!!
		val code = function.code()
		if (Interpreter.debugL1)
		{
			log(
				Interpreter.loggerDebugL1,
				Level.FINER,
				"{0}Started L1 run: {1}",
				interpreter.debugModeString,
				whitespaces.matcher(function.toString()).replaceAll(" ")
					.truncateTo(100, "......"))
		}
		val debugger = interpreter.debugger
		code.setUpInstructionDecoder(instructionDecoder, startingPc)
		var stackp = startingStackp
		while (true)
		{
			// Check the debugger *prior* to checking for running past the end
			// of the nybblecodes (an implicit return), since we want to be able
			// to pause before returning, say in a chain of returns, or when a
			// breakpoint occurs as a top-level module statement at a point
			// prior to installing the base frame hook.
			if (debugger !== null)
			{
				if (!interpreter.debuggerRunCondition!!(interpreter))
				{
					interpreter.currentReifier = reifyForDebugger(
						function, debugger, frame, pc(), stackp)
					return null
				}
			}

			if (instructionDecoder.atEnd())
			{
				// It ran off the end of the nybblecodes, which is how a
				// function returns in Level One. Pop the return result and
				// return to the Kotlin caller.
				val popped = frame[stackp]
				frame[stackp] = nil
				++stackp
				assert(stackp == frame.size)
				interpreter.returningFunction = function
				if (Interpreter.debugL1)
				{
					log(
						Interpreter.loggerDebugL1,
						Level.FINER,
						"{0}L1 return ({1})",
						interpreter.debugModeString,
						popped.typeTag.name)
				}
				return popped
			}

			val operationOrdinal = instructionDecoder.getOperationOrdinal()
			if (Interpreter.debugL1)
			{
				val savePc = pc()
				val operation = L1Operation.lookup(operationOrdinal)
				val operands = operation.operandTypes.map {
					getOperand()
				}
				log(
					Interpreter.loggerDebugL1,
					Level.FINER,
					"{0}L1 step: {1}",
					interpreter.debugModeString,
					if (operands.isEmpty()) operation
					else "$operation $operands")
				instructionDecoder.pc = savePc
			}
			when (operationOrdinal)
			{
				L1_doCall_ord ->
				{
					val bundle: A_Bundle = code.literalAt(getOperand())
					val expectedReturnType = code.literalAt(getOperand())
					val numArgs = bundle.bundleMethod.numArgs
					if (Interpreter.debugL1)
					{
						log(
							Interpreter.loggerDebugL1,
							Level.FINER,
							"{0}\tL1 call ({1})",
							interpreter.debugModeString,
							bundle.message.atomName)
					}
					interpreter.argsBuffer.run {
						clear()
						for (i in stackp + numArgs - 1 downTo stackp)
						{
							add(frame[i])
							frame[i] = nil
						}
					}
					stackp += numArgs
					// Push the expected type, which should be replaced on the
					// stack with the actual value when the call completes
					// (after ensuring it complies).
					frame[--stackp] = expectedReturnType
					val method: A_Method = bundle.bundleMethod
					val matching: A_Definition = try
					{
						method.lookupByValuesFromList(
							interpreter.argsBuffer,
							code.lookupStat)
					}
					catch (e: MethodDefinitionException)
					{
						interpreter.currentReifier = reifyAndReportFailedLookup(
							method, e.errorCode, frame, pc(), stackp)
						return null
					}
					val valueOrNull = callMethodAfterLookup(
						matching, frame, pc(), stackp
					) ?: return null
					// The call returned normally, without reifications, with
					// the resulting value in the interpreter's latestResult.
					if (Interpreter.debugL1)
					{
						log(
							Interpreter.loggerDebugL1,
							Level.FINER,
							"{0}Call returned: {1}",
							interpreter.debugModeString,
							valueOrNull.typeTag.name)
					}
					val returnCheckReifier = checkReturnType(
						valueOrNull,
						expectedReturnType,
						function,
						frame,
						pc(),
						stackp)
					if (returnCheckReifier !== null)
					{
						// Reification is happening within the handling of
						// the failed return type check.
						interpreter.currentReifier = returnCheckReifier
						return null
					}
					assert(stackp <= code.numSlots)
					// Replace the stack slot.
					frame[stackp] = valueOrNull
				}
				L1_doPushLiteral_ord ->
				{
					frame[--stackp] = code.literalAt(getOperand())
				}
				L1_doPushLastLocal_ord ->
				{
					val localIndex = getOperand()
					val local = frame[localIndex]
											assert(local.notNil)
					frame[localIndex] = nil
					frame[--stackp] = local
				}
				L1_doPushLocal_ord ->
				{
					val local = frame[getOperand()]
					assert(local.notNil)
					frame[--stackp] = local.makeImmutable()
				}
				L1_doPushLastOuter_ord ->
				{
					val outerIndex = getOperand()
					val outer = function.outerVarAt(outerIndex)
					assert(outer.notNil)
					when (function.optionallyNilOuterVar(outerIndex))
					{
						true -> frame[--stackp] = outer
						else -> frame[--stackp] = outer.makeImmutable()
					}
				}
				L1_doClose_ord ->
				{
					val numCopiedVars = getOperand()
					val codeToClose = code.literalAt(getOperand())
					val newFunction: A_Function =
						createExceptOuters(codeToClose, numCopiedVars)
					var i = numCopiedVars
					while (i >= 1)
					{
						// We don't assertObjectUnreachableIfMutable() on the
						// popped outer variables because each outer variable's
						// new reference from the function balances the lost
						// reference from the continuation's stack. Likewise, we
						// don't make them be immutable. The function itself
						// should remain mutable at this point, otherwise the
						// outer variables would have to makeImmutable() to be
						// referenced by an immutable function.
						val popped = frame[stackp]
						frame[stackp] = nil
						++stackp
						val value = popped
						assert(value.notNil)
						newFunction.outerVarAtPut(i, value)
						i--
					}
					frame[--stackp] = newFunction as AvailObject
				}
				L1_doSetLocal_ord ->
				{
					val popped = frame[stackp]
					frame[stackp] = nil
					++stackp
					val variable = frame[getOperand()]
					if (!setVariable(variable, popped, frame, pc(), stackp))
						return null
				}
				L1_doGetLocalClearing_ord ->
				{
					val localVariable: A_Variable = frame[getOperand()]
					val valueOrNull = getVariableClearingIfMutable(
						localVariable, frame, pc(), stackp)
					if (valueOrNull === null) return null
					frame[--stackp] = valueOrNull
				}
				L1_doPushOuter_ord ->
				{
					val outer = function.outerVarAt(getOperand())
					assert(outer.notNil)
					frame[--stackp] = outer.makeImmutable()
				}
				L1_doPop_ord ->
				{
					frame[stackp] = nil
					++stackp
				}
				L1_doGetLastOuter_ord ->
				{
					val outerVariable = function.outerVarAt(getOperand())
					val valueOrNull = getVariableClearingIfMutable(
						outerVariable, frame, pc(), stackp)
					if (valueOrNull === null) return null
					frame[--stackp] = valueOrNull.makeImmutable()
				}
				L1_doSetOuter_ord ->
				{
					val popped = frame[stackp]
					frame[stackp] = nil
					++stackp
					val variable = function.outerVarAt(getOperand())
					if (!setVariable(variable, popped, frame, pc(), stackp))
						return null
				}
				L1_doGetLocal_ord ->
				{
					val variable = frame[getOperand()]
					val valueOrNull = getVariable(variable, frame, pc(), stackp)
					if (valueOrNull === null) return null
					frame[--stackp] = valueOrNull
				}
				L1_doMakeTuple_ord ->
				{
					when (val size = getOperand())
					{
						0 -> frame[--stackp] = emptyTuple
						1 -> frame[stackp] =
							optimizedTuple(frame[stackp]) as AvailObject
						else ->
						{
							var s = stackp
							val newTuple = generateReversedFrom(size) {
								frame[s].also {
									frame[s] = nil
									++s
								}
							}
							stackp += size - 1
							frame[stackp] = newTuple
						}
					}
				}
				L1_doGetOuter_ord ->
				{
					val variable = function.outerVarAt(getOperand())
					val valueOrNull = getVariable(variable, frame, pc(), stackp)
					if (valueOrNull === null) return null
					frame[--stackp] = valueOrNull
				}
				L1_doExtension_ord ->
				{
					throw AssertionError("Illegal dispatch nybblecode")
				}
				L1Ext_doPushLabel_ord ->
				{
					val numArgs = code.numArgs()
					assert(code.codePrimitive() == null)
					val args = (1..numArgs).map {
						frame[it].apply { assert(notNil) }
					}
					// Note that the locals are not present in the new
					// continuation, just arguments.  New locals will be
					// created when the continuation is restarted.
					// Freeze all fields of the new object, including
					// its caller, function, and args.
					// ...always a fresh copy, always mutable (uniquely owned).
					// ...and continue running the chunk.
					if (interpreter.callerIsReified())
					{
						// The caller has already been reified, so we don't need
						// to force reification here.
						// Note that the locals are not present in the new
						// continuation, just arguments.
						val labelContinuation = createLabelContinuation(
							interpreter.function!!,
							interpreter.getReifiedContinuation()!!,
							DefaultL1Chunk,
							AFTER_PRIMITIVE_FAILURE.offset,
							args)
						// Freeze all fields of the new object, including its
						// caller, function, and args.
						labelContinuation.makeSubobjectsImmutable()
						frame[--stackp] = labelContinuation as AvailObject
					}
					else
					{
						// Unfortunately, the caller is not yet reified.
						// Stash the frame, pc-2, and stackp into fields of this
						// stepper, reify any outer calls, then continue running
						// at offset AFTER_REIFICATION_FOR_LABEL_CREATION.  This
						// will retrieve the stashed data and continue the
						// stepper at the same push-label instruction, but this
						// time the caller will have been reified already.
						stashedFrameAtPushLabel = frame
						// Note: push-label is an extended nybblecode, and takes
						// two nybbles.
						stashedPcAtPushLabel = pc() - 2
						stashedStackpAtPushLabel = stackp
						val savedFunction = interpreter.function!!
						interpreter.currentReifier = StackReifier(
							true,
							reificationBeforeLabelCreationStat
						) {
							// The Java stack has now been reified into Avail
							// continuations.  Run this before continuing the L2
							// interpreter.
							interpreter.function = savedFunction
							interpreter.chunk = DefaultL1Chunk
							interpreter.setOffset(
								AFTER_REIFICATION_FOR_LABEL_CREATION.offset)
							// The push-label instruction will be retried now
							// that the call chain has been reified.
							CONTINUE_FIBER
						}
						return null
					}
				}
				L1Ext_doGetLiteral_ord ->
				{
					var variable = code.literalAt(getOperand())
					val valueOrNull = getVariable(variable, frame, pc(), stackp)
					if (valueOrNull === null) return null
					frame[--stackp] = valueOrNull
				}
				L1Ext_doSetLiteral_ord ->
				{
					val popped = frame[stackp]
					frame[stackp] = nil
					++stackp
					val variable = code.literalAt(getOperand())
					if (!setVariable(variable, popped, frame, pc(), stackp))
						return null
				}
				L1Ext_doDuplicate_ord ->
				{
					val value = frame[stackp].makeImmutable()
					frame[--stackp] = value
				}
				L1Ext_doPermute_ord ->
				{
					val permutation: A_Tuple =
						code.literalAt(getOperand())
					val size = permutation.tupleSize
					val values = arrayOfNulls<AvailObject>(size)
					for (i in 1..size)
					{
						values[permutation.tupleIntAt(i) - 1] =
							frame[stackp + size - i]
					}
					for (i in 1..size)
					{
						frame[stackp + size - i] = values[i - 1]!!
					}
				}
				L1Ext_doSuperCall_ord ->
				{
					val bundle: A_Bundle = code.literalAt(getOperand())
					val expectedReturnType = code.literalAt(getOperand())
					val superUnionType: A_Type = code.literalAt(getOperand())
					val numArgs = bundle.bundleMethod.numArgs
					if (Interpreter.debugL1)
					{
						log(
							Interpreter.loggerDebugL1,
							Level.FINER,
							"{0}L1 supercall: {1}",
							interpreter.debugModeString,
							bundle.message.atomName)
					}
					val args = interpreter.argsBuffer
					args.clear()
					var reversedStackp = stackp + numArgs
					val typesTuple: A_Tuple =
						generateObjectTupleFrom(numArgs) { index: Int ->
							val arg = frame[--reversedStackp]
							args.add(arg)
							instanceTypeOrMetaOn(arg).typeUnion(
								superUnionType.typeAtIndex(index))
						}
					stackp += numArgs
					// Push the expected type, which should be replaced on the
					// stack with the actual value when the call completes
					// (after ensuring it complies).
					frame[--stackp] = expectedReturnType
					val method: A_Method = bundle.bundleMethod
					val matching: A_Definition = try
					{
						method.lookupByTypesFromTuple(typesTuple)
					}
					catch (e: MethodDefinitionException)
					{
						interpreter.currentReifier =
							reifyAndReportFailedLookup(
								method, e.errorCode, frame, pc(), stackp)
						return null
					}
					val result =
						callMethodAfterLookup(matching, frame, pc(), stackp) ?:
						return null
					// The call returned normally, without reifications, with
					// the resulting value in the interpreter's latestResult.
					if (Interpreter.debugL1)
					{
						log(
							Interpreter.loggerDebugL1,
							Level.FINER,
							"{0}Call returned: {1}",
							interpreter.debugModeString,
							result.typeTag.name)
					}
					val returnCheckReifier = checkReturnType(
						result,
						expectedReturnType,
						function,
						frame,
						pc(),
						stackp)
					if (returnCheckReifier !== null)
					{
						// Reification is happening within the handling of
						// the failed return type check.
						interpreter.currentReifier = returnCheckReifier
						return null
					}
					assert(stackp <= code.numSlots)
					// Replace the stack slot.
					frame[stackp] = result
				}
				L1Ext_doSetLocalSlot_ord ->
				{
					frame[getOperand()] = frame[stackp]
					frame[stackp] = nil
					++stackp
				}
			}
		}
	}

	/**
	 * Answer a [StackReifier] for reifying the current instruction step for
	 * debugging purposes.
	 *
	 * @param function
	 *   The [A_Function] being interpreted.
	 * @param debugger
	 *   The [AvailDebuggerModel] that is controlling the debugging session.
	 * @param frame
	 *   The current stack frame.
	 * @param pc
	 *   The program counter within the function.
	 * @param stackp
	 *   The stack pointer within the frame
	 * @return
	 * A [StackReifier] for reifying the current execution state.
	 */
	private fun reifyForDebugger(
		function: A_Function,
		debugger: AvailDebuggerModel,
		frame: Array<AvailObject>,
		pc: Int,
		stackp: Int,
	): StackReifier
	{
		// The debuggerRunCondition said we should pause now.
		val mutableContinuation = createContinuationWithFrame(
			function = function,
			caller = nil,
			registerDump = nil,
			pc = pc,
			stackp = stackp,
			levelTwoChunk = DefaultL1Chunk,
			levelTwoOffset = DefaultEntryPoint.RESUME.offset,
			frameValues = listOf(*frame),
			zeroBasedStartIndex = 1)
		return StackReifier(true, AvailDebuggerModel.reificationForDebuggerStat)
		{
			// Push the new continuation onto the reified stack.
			interpreter.run {
				val f = fiber()
				f.continuation = mutableContinuation.replacingCaller(
					getReifiedContinuation()!!)
				setReifiedContinuation(null)
				offset = Int.MAX_VALUE
				clearLatestResult()
				f.lock {
					synchronized(f) {
						assert(f.executionState === RUNNING)
						f.executionState = PAUSED
						val bound = f.getAndSetSynchronizationFlag(BOUND, false)
						f.fiberHelper.stopCountingCPU()
						assert(bound)
						fiber(null, "debug pause")
					}
				}
				postExitContinuation = {
					debugger.justPaused(f)
				}
				SWITCH_FROM_FIBER
			}
		}
	}

	/**
	 * Reify the current frame into the specified [StackReifier].
	 *
	 * @param reifier
	 *   A `StackReifier`.
	 * @param frame
	 *   The current frame, corresponding to continuation slots.
	 * @param entryPoint
	 *   The [DefaultEntryPoint] at which to resume L1 interpretation.
	 * @param logMessage
	 *   The log message. Expects two template parameters, one for the
	 *   [debug&#32;string][Interpreter.debugModeString], one for the method
	 *   name, respectively.
	 */
	private fun reifyCurrentFrame(
		reifier: StackReifier,
		frame: Array<AvailObject>,
		pc: Int,
		stackp: Int,
		entryPoint: DefaultEntryPoint,
		logMessage: String)
	{
		val function = interpreter.function!!
		val continuation: A_Continuation = createContinuationWithFrame(
			function,
			nil,
			nil,
			pc,  // Right after the set-variable.
			stackp,
			DefaultL1Chunk,
			entryPoint.offset(),
			listOf(*frame),
			1)
		if (Interpreter.debugL2)
		{
			log(
				Interpreter.loggerDebugL2,
				Level.FINER,
				logMessage,
				interpreter.debugModeString,
				continuation.function.code().methodName.asNativeString())
		}
		reifier.pushAction {
			continuation.replacingCaller(it)
		}
	}

	/**
	 * Get the value from the given variable, reifying and invoking the
	 * [HookType.READ_UNASSIGNED_VARIABLE] hook if the variable has no value.
	 *
	 * @param variable
	 *   The variable to read.
	 * @param frame
	 *   The current frame of execution, corresponding to continuation slots.
	 * @param pc
	 *   The current program counter within the function.
	 * @param stackp
	 *   The current stack pointer, indicating the top of the stack.
	 * @return
	 *   Either `null` to indicate a reifier has been set up during a failed
	 *   read of the variable, or the [AvailObject] that's the current value of
	 *   the variable.
	 */
	private fun getVariable(
		variable: A_Variable,
		frame: Array<AvailObject>,
		pc: Int,
		stackp: Int
	): AvailObject?
	{
		return try
		{
			variable.getValue()
		}
		catch (e: VariableGetException)
		{
			assert(e.numericCode.equals(
				AvailErrorCode.E_CANNOT_READ_UNASSIGNED_VARIABLE.numericCode()))
			val savedFunction = interpreter.function!!
			val savedOffset = interpreter.offset
			val unassignedVariableFunction =
				interpreter.runtime[HookType.READ_UNASSIGNED_VARIABLE]
			interpreter.argsBuffer.clear()
			val valueOrNull =
				interpreter.invokeFunction(unassignedVariableFunction)
			// The hook is ⊥-typed, so it can't return normally.
			assert(valueOrNull === null)
			val reifier = interpreter.currentReifier!!
			interpreter.chunk = DefaultL1Chunk
			interpreter.setOffset(savedOffset)
			interpreter.function = savedFunction
			savedFunction.code().setUpInstructionDecoder(instructionDecoder, pc)
			if (reifier.actuallyReify)
			{
				reifyCurrentFrame(
					reifier,
					frame,
					pc,
					stackp,
					DefaultEntryPoint.UNREACHABLE_ENTRY,
					"{0}Push reified continuation for L1 getVar "
						+ "failure: {1}")
			}
			interpreter.currentReifier = reifier
			return null
		}
	}

	/**
	 * Get the value from the given variable, reifying and invoking the
	 * [HookType.READ_UNASSIGNED_VARIABLE] hook if the variable has no value.
	 * Clear the variable as well, but only if the variable is [MUTABLE].  If
	 * the variable was not mutable, make the value [IMMUTABLE].
	 *
	 * @param variable
	 *   The variable to read (and possibly clear).
	 * @param frame
	 *   The current frame of execution, corresponding to continuation slots.
	 * @param pc
	 *   The current program counter within the function.
	 * @param stackp
	 *   The current stack pointer, indicating the top of the stack.
	 * @return
	 *   Either `null` to indicate a reifier has been set up during a failed
	 *   read of the variable, or the [AvailObject] that's the current value of
	 *   the variable.
	 */
	private fun getVariableClearingIfMutable(
		variable: A_Variable,
		frame: Array<AvailObject>,
		pc: Int,
		stackp: Int
	): AvailObject?
	{
		return try
		{
			if (variable.traversed().descriptor.isMutable)
			{
				variable.getValueClearing()
			}
			else
			{
				// Automatically makes the value immutable.
				variable.getValue()
			}
		}
		catch (e: VariableGetException)
		{
			assert(e.numericCode.equals(
				AvailErrorCode.E_CANNOT_READ_UNASSIGNED_VARIABLE.numericCode()))
			val savedFunction = interpreter.function!!
			val savedOffset = interpreter.offset
			val unassignedVariableFunction =
				interpreter.runtime[HookType.READ_UNASSIGNED_VARIABLE]
			interpreter.argsBuffer.clear()
			val valueOrNull =
				interpreter.invokeFunction(unassignedVariableFunction)
			// The hook is ⊥-typed, so it can't return normally.
			assert(valueOrNull === null)
			val reifier = interpreter.currentReifier!!
			interpreter.chunk = DefaultL1Chunk
			interpreter.setOffset(savedOffset)
			interpreter.function = savedFunction
			savedFunction.code().setUpInstructionDecoder(instructionDecoder, pc)
			if (reifier.actuallyReify)
			{
				reifyCurrentFrame(
					reifier,
					frame,
					pc,
					stackp,
					DefaultEntryPoint.UNREACHABLE_ENTRY,
					"{0}Push reified continuation for L1 getVarClearing "
						+ "failure: {1}")
			}
			interpreter.currentReifier = reifier
			return null
		}
	}

	/**
	 * Set a variable, triggering reification and invocation of the
	 * [AvailRuntime.implicitObserveFunction] if necessary.
	 *
	 * @param variable
	 *   The variable to update.
	 * @param value
	 *   The type-safe value to write to the variable.
	 * @param frame
	 *   The current frame of execution, corresponding to continuation slots.
	 * @param pc
	 *   The current program counter within the function.
	 * @param stackp
	 *   The current stack pointer, indicating the top of the stack.
	 * @return
	 *   `true` if the variable was successfully updated, or `false` if it
	 *   had to reify while handling a failed write, in which case the reifier
	 *   will have been set up in the [interpreter].
	 */
	private fun setVariable(
		variable: A_Variable,
		value: AvailObject,
		frame: Array<AvailObject>,
		pc: Int,
		stackp: Int
	): Boolean
	{
		try
		{
			// The value's reference from the stack is now from the variable.
			variable.setValueNoCheck(value)
		}
		catch (e: VariableSetException)
		{
			assert(e.numericCode.equals(
				E_OBSERVED_VARIABLE_WRITTEN_WHILE_UNTRACED.numericCode()))
			val savedFunction = interpreter.function!!
			val savedOffset = interpreter.offset
			val implicitObserveFunction =
				interpreter.runtime.implicitObserveFunction()
			interpreter.argsBuffer.clear()
			interpreter.argsBuffer.add(assignmentFunction() as AvailObject)
			interpreter.argsBuffer.add(
				tuple(variable, value) as AvailObject)
			val valueOrNull =
				interpreter.invokeFunction(implicitObserveFunction)
			interpreter.chunk = DefaultL1Chunk
			interpreter.setOffset(savedOffset)
			interpreter.function = savedFunction
			savedFunction.code().setUpInstructionDecoder(instructionDecoder, pc)
			if (valueOrNull === null)
			{
				val reifier = interpreter.currentReifier!!
				if (reifier.actuallyReify)
				{
					reifyCurrentFrame(
						reifier,
						frame,
						pc,
						stackp,
						DefaultEntryPoint.RESUME,
						"{0}Push reified continuation for L1 setVar "
							+ "failure: {1}")
				}
				interpreter.currentReifier = reifier
				return false
			}
		}
		return true
	}

	/**
	 * Check that the matching definition is a method definition, then invoke
	 * its body function.  If reification is requested, construct a suitable
	 * continuation for the current frame on the way out.
	 *
	 * @param matching
	 *   The [A_Definition] that was already looked up.
	 * @param frame
	 *   The current frame, corresponding to continuation slots.
	 * @param pc
	 *   The current program counter within the function.
	 * @param stackp
	 *   The current stack pointer, indicating the top of the stack.
	 * @return
	 *   Either an [A_BasicObject] to indicate successful return from the called
	 *   function, or `null` to indicate reification is in progress.
	 */
	private fun callMethodAfterLookup(
		matching: A_Definition,
		frame: Array<AvailObject>,
		pc: Int,
		stackp: Int
	): AvailObject?
	{
		// At this point, the frame information is still the same, but we've set
		// up argsBuffer.
		if (!matching.isMethodDefinition())
		{
			val errorCode = when
			{
				matching.isForwardDefinition() ->
					AvailErrorCode.E_FORWARD_METHOD_DEFINITION
				matching.isAbstractDefinition() ->
					AvailErrorCode.E_ABSTRACT_METHOD_DEFINITION
				else -> error("Unknown definition type")
			}
			interpreter.currentReifier = reifyAndReportFailedLookup(
				matching.definitionMethod,
				errorCode,
				frame,
				pc,
				stackp)
			return null
		}
		val savedFunction = interpreter.function!!
		assert(interpreter.chunk === DefaultL1Chunk)
		val savedOffset = interpreter.offset
		val functionToInvoke = matching.bodyBlock()
		val valueOrNull = interpreter.invokeFunction(functionToInvoke)
		interpreter.chunk = DefaultL1Chunk
		interpreter.setOffset(savedOffset)
		interpreter.function = savedFunction
		savedFunction.code().setUpInstructionDecoder(instructionDecoder, pc)
		valueOrNull?.let { return it as AvailObject }
		val reifier = interpreter.currentReifier!!
		if (Interpreter.debugL2)
		{
			log(
				Interpreter.loggerDebugL2,
				Level.FINER,
				"{0}Reifying call from L1 ({1})",
				interpreter.debugModeString,
				reifier.actuallyReify)
		}
		if (reifier.actuallyReify)
		{
			reifyCurrentFrame(
				reifier,
				frame,
				pc,
				stackp,
				DefaultEntryPoint.REENTRY_FROM_REIFIED_CALL,
				"{0}Push reified continuation for L1 call: {1}")
		}
		return null
	}

	/**
	 * Check that the result is an instance of the expected type.  If it is,
	 * return.  If not, invoke the resultDisagreedWithExpectedTypeFunction.
	 * Also accumulate statistics related to the return type check.  The
	 * [Interpreter.returningFunction] must have been set by the client.
	 *
	 * @param result
	 *   The value that was just returned.
	 * @param expectedReturnType
	 *   The expected type to check the value against.
	 * @param returnee
	 *   The [A_Function] that we're returning into.
	 * @param frame
	 *   The current frame of execution, corresponding to continuation slots.
	 * @param pc
	 *   The current program counter within the function.
	 * @param stackp
	 *   The current stack pointer, indicating the top of the stack.
	 * @return
	 *   A [StackReifier] if reification is needed, otherwise `null`.
	 */
	internal fun checkReturnType(
		result: AvailObject,
		expectedReturnType: A_Type,
		returnee: A_Function,
		frame: Array<AvailObject>,
		pc: Int,
		stackp: Int
	): StackReifier?
	{
		val before = AvailRuntimeSupport.captureNanos(interpreter)
		val checkOk = result.isInstanceOf(expectedReturnType)
		val after = AvailRuntimeSupport.captureNanos(interpreter)
		val returner = interpreter.returningFunction!!
		val calledPrimitive = returner.code().codePrimitive()
		if (calledPrimitive !== null)
		{
			calledPrimitive.addNanosecondsCheckingResultType(
				after - before, interpreter.interpreterIndex)
		}
		else
		{
			returner.code().returnerCheckStat.record(
				after - before, interpreter.interpreterIndex)
			returnee.code().returneeCheckStat.record(
				after - before, interpreter.interpreterIndex)
		}
		if (!checkOk)
		{
			val savedFunction = interpreter.function!!
			assert(interpreter.chunk === DefaultL1Chunk)
			val savedOffset = interpreter.offset
			val reportedResult = newVariableWithContentType(Types.ANY(), result)
			val argsBuffer = interpreter.argsBuffer
			argsBuffer.clear()
			argsBuffer.add(returner as AvailObject)
			argsBuffer.add(expectedReturnType as AvailObject)
			argsBuffer.add(reportedResult)
			val valueOrNull = interpreter.invokeFunction(
				interpreter.runtime.resultDisagreedWithExpectedTypeFunction())
			// Handler is ⊥-typed, so it can't return normally.
			assert(valueOrNull === null)
			val reifier = interpreter.currentReifier!!
			interpreter.chunk = DefaultL1Chunk
			interpreter.setOffset(savedOffset)
			interpreter.function = savedFunction
			savedFunction.code().setUpInstructionDecoder(instructionDecoder, pc)
			if (reifier.actuallyReify)
			{
				reifyCurrentFrame(
					reifier,
					frame,
					pc,
					stackp,
					DefaultEntryPoint.UNREACHABLE_ENTRY,
					"{0}Push reified continuation for L1 check "
						+ "return type failure: {1}")
			}
			return reifier
		}
		// Check was ok.
		return null
	}

	/**
	 * Return a [StackReifier] to reify the Java stack into [A_Continuation]s,
	 * then invoke the [AvailRuntime.invalidMessageSendFunction] with
	 * appropriate arguments. An [AvailErrorCode] is also provided to indicate
	 * what the lookup problem was.
	 *
	 * @param method
	 *   The method that failed lookup.
	 * @param errorCode
	 *   The [AvailErrorCode] indicating the lookup problem.
	 * @param frame
	 *   The current frame of execution, corresponding to continuation slots.
	 * @param pc
	 *   The current program counter within the function.
	 * @param stackp
	 *   The current stack pointer, indicating the top of the stack.
	 * @return
	 *   A [StackReifier] to cause reification.
	 */
	private fun reifyAndReportFailedLookup(
		method: A_Method,
		errorCode: AvailErrorCode,
		frame: Array<AvailObject>,
		pc: Int,
		stackp: Int
	): StackReifier
	{
		val arguments = tupleFromList(interpreter.argsBuffer)

		val savedFunction = interpreter.function!!
		assert(interpreter.chunk === DefaultL1Chunk)
		val savedOffset = interpreter.offset
		interpreter.argsBuffer.run {
			clear()
			add(errorCode.numericCode().cast())
			add(method.cast())
			add(arguments.cast())
		}
		val valueOrNull = interpreter.invokeFunction(
			interpreter.runtime.invalidMessageSendFunction())
		// The handle is ⊥-typed, so it can't return normally.
		assert(valueOrNull === null)
		interpreter.chunk = DefaultL1Chunk
		interpreter.setOffset(savedOffset)
		interpreter.function = savedFunction
		savedFunction.code().setUpInstructionDecoder(instructionDecoder, pc)
		val reifier = interpreter.currentReifier!!
		if (reifier.actuallyReify)
		{
			reifyCurrentFrame(
				reifier,
				frame,
				pc,
				stackp,
				DefaultEntryPoint.UNREACHABLE_ENTRY,
				"{0}Push reified continuation for failed lookup handler: {1}")
		}
		return reifier
	}

	companion object
	{
		/** The [Statistic] for reifications prior to label creation in L1. */
		private val reificationBeforeLabelCreationStat = Statistic(
			REIFICATIONS, "Reification before label creation in L1")

		/** An empty array used for clearing the pointers quickly. */
		private val emptyPointersArray = arrayOf<AvailObject>()

		/**
		 * A pre-compilable regex that matches one or more whitespace
		 * characters.
		 */
		private val whitespaces = Pattern.compile("\\s+")
	}
}
