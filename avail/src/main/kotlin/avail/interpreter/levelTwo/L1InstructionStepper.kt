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
import avail.descriptor.functions.A_Continuation.Companion.caller
import avail.descriptor.functions.A_Continuation.Companion.function
import avail.descriptor.functions.A_Continuation.Companion.replacingCaller
import avail.descriptor.functions.A_Function
import avail.descriptor.functions.A_Function.Companion.optionallyNilOuterVar
import avail.descriptor.functions.A_RawFunction.Companion.literalAt
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
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.AvailObject
import avail.descriptor.representation.Mutability.IMMUTABLE
import avail.descriptor.representation.Mutability.MUTABLE
import avail.descriptor.representation.NilDescriptor.Companion.nil
import avail.descriptor.tuples.A_Tuple
import avail.descriptor.tuples.A_Tuple.Companion.tupleIntAt
import avail.descriptor.tuples.A_Tuple.Companion.tupleSize
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.generateObjectTupleFrom
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.generateReversedFrom
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tupleFromList
import avail.descriptor.tuples.TupleDescriptor.Companion.emptyTuple
import avail.descriptor.types.A_Type
import avail.descriptor.types.A_Type.Companion.typeAtIndex
import avail.descriptor.types.A_Type.Companion.typeUnion
import avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.instanceTypeOrMetaOn
import avail.descriptor.types.PrimitiveTypeDescriptor.Types
import avail.descriptor.variables.A_Variable
import avail.descriptor.variables.VariableDescriptor.Companion.newVariableWithContentType
import avail.exceptions.AvailErrorCode
import avail.exceptions.MethodDefinitionException
import avail.exceptions.VariableGetException
import avail.exceptions.VariableSetException
import avail.interpreter.execution.Interpreter
import avail.interpreter.execution.Interpreter.Companion.assignmentFunction
import avail.interpreter.execution.Interpreter.Companion.log
import avail.interpreter.levelOne.L1Operation
import avail.interpreter.levelOne.L1Operation.L1Ext_doDuplicate
import avail.interpreter.levelOne.L1Operation.L1Ext_doGetLiteral
import avail.interpreter.levelOne.L1Operation.L1Ext_doPermute
import avail.interpreter.levelOne.L1Operation.L1Ext_doPushLabel
import avail.interpreter.levelOne.L1Operation.L1Ext_doSetLiteral
import avail.interpreter.levelOne.L1Operation.L1Ext_doSetLocalSlot
import avail.interpreter.levelOne.L1Operation.L1Ext_doSuperCall
import avail.interpreter.levelOne.L1Operation.L1_doCall
import avail.interpreter.levelOne.L1Operation.L1_doClose
import avail.interpreter.levelOne.L1Operation.L1_doExtension
import avail.interpreter.levelOne.L1Operation.L1_doGetLocal
import avail.interpreter.levelOne.L1Operation.L1_doGetLocalClearing
import avail.interpreter.levelOne.L1Operation.L1_doGetOuter
import avail.interpreter.levelOne.L1Operation.L1_doGetOuterClearing
import avail.interpreter.levelOne.L1Operation.L1_doMakeTuple
import avail.interpreter.levelOne.L1Operation.L1_doPop
import avail.interpreter.levelOne.L1Operation.L1_doPushLastLocal
import avail.interpreter.levelOne.L1Operation.L1_doPushLastOuter
import avail.interpreter.levelOne.L1Operation.L1_doPushLiteral
import avail.interpreter.levelOne.L1Operation.L1_doPushLocal
import avail.interpreter.levelOne.L1Operation.L1_doPushOuter
import avail.interpreter.levelOne.L1Operation.L1_doSetLocal
import avail.interpreter.levelOne.L1Operation.L1_doSetOuter
import avail.interpreter.levelTwo.L2Chunk.ChunkEntryPoint
import avail.interpreter.levelTwo.operation.L2_INTERPRET_LEVEL_ONE
import avail.optimizer.StackReifier
import avail.optimizer.jvm.CheckedMethod
import avail.optimizer.jvm.CheckedMethod.Companion.instanceMethod
import avail.optimizer.jvm.ReferencedInGeneratedCode
import avail.performance.Statistic
import avail.performance.StatisticReport.REIFICATIONS
import avail.utility.cast
import java.util.logging.Level
import java.util.regex.Pattern

/**
 * This class is used to simulate the effect of level one nybblecodes during
 * execution of the [L2_INTERPRET_LEVEL_ONE] instruction, on behalf
 * of an [Interpreter].
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

	/** The current stack position as would be seen in a continuation. */
	var stackp = 0

	/**
	 * The registers that hold [Avail&#32;objects][AvailObject].
	 */
	var pointers: Array<AvailObject> = emptyPointersArray

	/**
	 * Get the current program counter.
	 */
	fun pc(): Int = instructionDecoder.pc()

	/**
	 * Read from the specified object register.
	 *
	 * @param index
	 *   Which object register to read.
	 * @return
	 *   The value from that register.
	 */
	fun pointerAt(index: Int): AvailObject = pointers[index]

	/**
	 * Write to the specified object register.
	 *
	 * @param index
	 *   Which object register to write.
	 * @param value
	 *   The value to write to that register.
	 */
	fun pointerAtPut(index: Int, value: A_BasicObject)
	{
		pointers[index] = value as AvailObject
	}

	/**
	 * Wipe out the existing register set for safety.
	 */
	fun wipeRegisters()
	{
		pointers = emptyPointersArray
	}

	/**
	 * Push a value onto the current virtualized continuation's stack (which
	 * is just some consecutively-numbered pointer registers and an integer
	 * register that maintains the position).
	 *
	 * @param value
	 * The value to push on the virtualized stack.
	 */
	private fun push(value: A_BasicObject)
	{
		pointerAtPut(--stackp, value)
	}

	/**
	 * Pop a value off the current virtualized continuation's stack (which
	 * is just some consecutively-numbered pointer registers and an integer
	 * register that maintains the position).
	 *
	 * @return
	 * The value popped off the virtualized stack.
	 */
	private fun pop(): AvailObject
	{
		val popped = pointerAt(stackp)
		pointerAtPut(stackp++, nil)
		return popped
	}

	/**
	 * Run the current code until it reaches the end.  Individual instructions,
	 * such as calls, may be subject to [reification][StackReifier], which
	 * should cause a suitable [A_Continuation] to be reified.  In
	 * addition, inter-nybblecode interrupts may also trigger reification, but
	 * they'll handle their own reification prior to returning here with a
	 * suitable [StackReifier] (to update and return again from here).
	 *
	 * @return
	 *   `null` if the current function returns normally, otherwise a
	 *   [StackReifier] with which to reify the stack.
	 */
	@ReferencedInGeneratedCode
	fun run(): StackReifier?
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
				object {
					override fun toString(): String =
						whitespaces.matcher(function.toString()).replaceAll(" ")
				})
		}
		val debugger = interpreter.debugger
		code.setUpInstructionDecoder(instructionDecoder)
		while (!instructionDecoder.atEnd())
		{
			if (debugger !== null)
			{
				val f = interpreter.fiber()
				if (!interpreter.debuggerRunCondition!!(f))
				{
					// The debuggerRunCondition said we should pause now.
					val mutableContinuation = createContinuationWithFrame(
						function = function,
						caller = nil,
						registerDump = nil,
						pc = pc(),
						stackp = stackp,
						levelTwoChunk = L2Chunk.unoptimizedChunk,
						levelTwoOffset =
							ChunkEntryPoint.TO_RESUME.offsetInDefaultChunk,
						frameValues = listOf(*pointers),
						zeroBasedStartIndex = 1)
					interpreter.isReifying = true
					return StackReifier(
						true,
						AvailDebuggerModel.reificationForDebuggerStat)
					{
						// Push the new continuation onto the reified stack.
						interpreter.apply {
							returnNow = false
							f.continuation =
								mutableContinuation.replacingCaller(
									getReifiedContinuation()!!)
							interpreter.setReifiedContinuation(null)
							isReifying = false
							returnNow = false
							exitNow = true
							offset = Int.MAX_VALUE
							setLatestResult(null)
							levelOneStepper.wipeRegisters()
							f.lock {
								synchronized(f) {
									assert(f.executionState === RUNNING)
									f.executionState = PAUSED
									val bound = f.getAndSetSynchronizationFlag(
										BOUND, false)
									f.fiberHelper.stopCountingCPU()
									assert(bound)
									fiber(null, "debug pause")
								}
							}
							postExitContinuation {
								debugger.justPaused(f)
							}
						}
					}
				}
			}
			val operationOrdinal = instructionDecoder.getOperationOrdinal()
			if (Interpreter.debugL1)
			{
				val savePc = pc()
				val operation = L1Operation.lookup(operationOrdinal)
				val operands = operation.operandTypes.map {
					instructionDecoder.getOperand()
				}
				log(
					Interpreter.loggerDebugL1,
					Level.FINER,
					"{0}L1 step: {1}",
					interpreter.debugModeString,
					if (operands.isEmpty()) operation
					else "$operation $operands")
				instructionDecoder.pc(savePc)
			}
			when (operationOrdinal)
			{
				L1_doCall.ordinal ->
				{
					val bundle: A_Bundle =
						code.literalAt(instructionDecoder.getOperand())
					val expectedReturnType: A_Type =
						code.literalAt(instructionDecoder.getOperand())
					val numArgs: Int = bundle.bundleMethod.numArgs
					if (Interpreter.debugL1)
					{
						log(
							Interpreter.loggerDebugL1,
							Level.FINER,
							"{0}         L1 call ({1})",
							interpreter.debugModeString,
							bundle.message.atomName)
					}
					interpreter.argsBuffer.clear()
					for (i in stackp + numArgs - 1 downTo stackp)
					{
						interpreter.argsBuffer.add(pointerAt(i))
						pointerAtPut(i, nil)
					}
					stackp += numArgs
					// Push the expected type, which should be replaced on the
					// stack with the actual value when the call completes
					// (after ensuring it complies).
					push(expectedReturnType)
					val method: A_Method = bundle.bundleMethod
					val matching: A_Definition = try
					{
						method.lookupByValuesFromList(interpreter.argsBuffer)
					}
					catch (e: MethodDefinitionException)
					{
						return reifyAndReportFailedLookup(method, e.errorCode)
					}
					callMethodAfterLookup(matching)?.let { reifier ->
						return reifier
					}

					// The call returned normally, without reifications, with
					// the resulting value in the interpreter's latestResult.
					val result = interpreter.getLatestResult()
					if (Interpreter.debugL1)
					{
						log(
							Interpreter.loggerDebugL1,
							Level.FINER,
							"{0}Call returned: {1}",
							interpreter.debugModeString,
							result.typeTag.name)
					}
					val returnCheckReifier =
						checkReturnType(result, expectedReturnType, function)
					if (returnCheckReifier !== null)
					{
						// Reification is happening within the handling of
						// the failed return type check.
						return returnCheckReifier
					}
					assert(stackp <= code.numSlots)
					// Replace the stack slot.
					pointerAtPut(stackp, result)
				}
				L1_doPushLiteral.ordinal ->
				{
					push(code.literalAt(instructionDecoder.getOperand()))
				}
				L1_doPushLastLocal.ordinal ->
				{
					val localIndex = instructionDecoder.getOperand()
					val local = pointerAt(localIndex)
					assert(local.notNil)
					pointerAtPut(localIndex, nil)
					push(local)
				}
				L1_doPushLocal.ordinal ->
				{
					val local = pointerAt(instructionDecoder.getOperand())
					assert(local.notNil)
					push(local.makeImmutable())
				}
				L1_doPushLastOuter.ordinal ->
				{
					val outerIndex = instructionDecoder.getOperand()
					val outer: A_BasicObject = function.outerVarAt(outerIndex)
					assert(outer.notNil)
					if (function.optionallyNilOuterVar(outerIndex))
					{
						push(outer)
					}
					else
					{
						push(outer.makeImmutable())
					}
				}
				L1_doClose.ordinal ->
				{
					val numCopiedVars = instructionDecoder.getOperand()
					val codeToClose =
						code.literalAt(instructionDecoder.getOperand())
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
						val value = pop()
						assert(value.notNil)
						newFunction.outerVarAtPut(i, value)
						i--
					}
					push(newFunction)
				}
				L1_doSetLocal.ordinal ->
				{
					val reifier = setVariable(
						pointerAt(instructionDecoder.getOperand()), pop())
					if (reifier !== null)
					{
						return reifier
					}
				}
				L1_doGetLocalClearing.ordinal ->
				{
					val localVariable: A_Variable =
						pointerAt(instructionDecoder.getOperand())
					val valueOrReifier =
						getVariableClearingIfMutable(localVariable)
					if (valueOrReifier is StackReifier)
					{
						return valueOrReifier
					}
					val value = valueOrReifier as AvailObject
					push(value)
				}
				L1_doPushOuter.ordinal ->
				{
					val outer =
						function.outerVarAt(instructionDecoder.getOperand())
					assert(outer.notNil)
					push(outer.makeImmutable())
				}
				L1_doPop.ordinal ->
				{
					pop()
				}
				L1_doGetOuterClearing.ordinal ->
				{
					val outerVariable: A_Variable =
						function.outerVarAt(instructionDecoder.getOperand())
					val valueOrReifier =
						getVariableClearingIfMutable(outerVariable)
					if (valueOrReifier is StackReifier)
					{
						return valueOrReifier
					}
					val value = valueOrReifier as AvailObject
					push(value.makeImmutable())
				}
				L1_doSetOuter.ordinal ->
				{
					val reifier = setVariable(
						function.outerVarAt(instructionDecoder.getOperand()),
						pop())
					if (reifier !== null)
					{
						return reifier
					}
				}
				L1_doGetLocal.ordinal ->
				{
					val valueOrReifier =
						getVariable(pointerAt(instructionDecoder.getOperand()))
					if (valueOrReifier is StackReifier)
					{
						return valueOrReifier
					}
					push(valueOrReifier as AvailObject)
				}
				L1_doMakeTuple.ordinal ->
				{
					when (val size = instructionDecoder.getOperand())
					{
						0 -> push(emptyTuple)
						1 -> push(tuple(pop()))
						else -> push(generateReversedFrom(size) { pop() })
					}
				}
				L1_doGetOuter.ordinal ->
				{
					val valueOrReifier = getVariable(
						function.outerVarAt(instructionDecoder.getOperand()))
					if (valueOrReifier is StackReifier)
					{
						return valueOrReifier
					}
					push(valueOrReifier as AvailObject)
				}
				L1_doExtension.ordinal ->
				{
					assert(false) { "Illegal dispatch nybblecode" }
				}
				L1Ext_doPushLabel.ordinal ->
				{
					val numArgs = code.numArgs()
					assert(code.codePrimitive() === null)
					val args = (1..numArgs).map {
						val arg = pointerAt(it)
						assert(arg.notNil)
						arg
					}
					assert(interpreter.chunk == L2Chunk.unoptimizedChunk)
					val savedFunction = interpreter.function!!
					val savedPointers = pointers
					val savedPc = pc()
					val savedStackp = stackp

					// Note that the locals are not present in the new
					// continuation, just arguments.  The locals will be
					// created by offsetToRestartUnoptimizedChunk()
					// when the continuation is restarted.
					// Freeze all fields of the new object, including
					// its caller, function, and args.
					// ...always a fresh copy, always mutable (uniquely
					// owned).
					// ...and continue running the chunk.
					interpreter.isReifying = true
					return StackReifier(
						true,
						reificationBeforeLabelCreationStat
					) {
						// The Java stack has been reified into Avail
						// continuations.  Run this before continuing the L2
						// interpreter.
						interpreter.function = savedFunction
						interpreter.chunk = L2Chunk.unoptimizedChunk
						interpreter.setOffset(
							ChunkEntryPoint.AFTER_REIFICATION
								.offsetInDefaultChunk)
						pointers = savedPointers
						savedFunction.code().setUpInstructionDecoder(
							instructionDecoder)
						instructionDecoder.pc(savedPc)
						stackp = savedStackp

						// Note that the locals are not present in the new
						// continuation, just arguments.  The locals will be
						// created by offsetToRestartUnoptimizedChunk()
						// when the continuation is restarted.
						val newContinuation =
							createLabelContinuation(
								savedFunction,
								interpreter.getReifiedContinuation()!!,
								L2Chunk.unoptimizedChunk,
								ChunkEntryPoint.TO_RESTART.offsetInDefaultChunk,
								args)

						// Freeze all fields of the new object, including
						// its caller, function, and args.
						newContinuation.makeSubobjectsImmutable()
						assert(newContinuation.caller().isNil
								|| !newContinuation.caller().descriptor()
							.isMutable) {
							("Caller should freeze because two "
								+ "continuations can see it")
						}
						push(newContinuation)
						interpreter.returnNow = false
						// ...and continue running the chunk.
						interpreter.isReifying = false
					}
				}
				L1Ext_doGetLiteral.ordinal ->
				{
					val valueOrReifier = getVariable(
						code.literalAt(instructionDecoder.getOperand()))
					if (valueOrReifier is StackReifier)
					{
						return valueOrReifier
					}
					push(valueOrReifier as AvailObject)
				}
				L1Ext_doSetLiteral.ordinal ->
				{
					setVariable(
						code.literalAt(instructionDecoder.getOperand()), pop())
				}
				L1Ext_doDuplicate.ordinal ->
				{
					push(pointerAt(stackp).makeImmutable())
				}
				L1Ext_doPermute.ordinal ->
				{
					val permutation: A_Tuple =
						code.literalAt(instructionDecoder.getOperand())
					val size = permutation.tupleSize
					val values = arrayOfNulls<AvailObject>(size)
					for (i in 1..size)
					{
						values[permutation.tupleIntAt(i) - 1] =
							pointerAt(stackp + size - i)
					}
					for (i in 1..size)
					{
						pointerAtPut(stackp + size - i, values[i - 1]!!)
					}
				}
				L1Ext_doSuperCall.ordinal ->
				{
					val bundle: A_Bundle =
						code.literalAt(instructionDecoder.getOperand())
					val expectedReturnType: A_Type =
						code.literalAt(instructionDecoder.getOperand())
					val superUnionType: A_Type =
						code.literalAt(instructionDecoder.getOperand())
					val numArgs: Int = bundle.bundleMethod.numArgs
					if (Interpreter.debugL1)
					{
						log(
							Interpreter.loggerDebugL1,
							Level.FINER,
							"{0}L1 supercall: {1}",
							interpreter.debugModeString,
							bundle.message.atomName)
					}
					interpreter.argsBuffer.clear()
					var reversedStackp = stackp + numArgs
					val typesTuple: A_Tuple =
						generateObjectTupleFrom(numArgs)
						{ index: Int ->
							val arg = pointerAt(--reversedStackp)
							interpreter.argsBuffer.add(arg)
							instanceTypeOrMetaOn(arg).typeUnion(
								superUnionType.typeAtIndex(index))
						}
					stackp += numArgs
					// Push the expected type, which should be replaced on the
					// stack with the actual value when the call completes
					// (after ensuring it complies).
					push(expectedReturnType)
					val method: A_Method = bundle.bundleMethod
					val matching: A_Definition = try
					{
						method.lookupByTypesFromTuple(typesTuple)
					}
					catch (e: MethodDefinitionException)
					{
						return reifyAndReportFailedLookup(method, e.errorCode)
					}
					callMethodAfterLookup(matching)?.let { reifier ->
						return reifier
					}

					// The call returned normally, without reifications, with
					// the resulting value in the interpreter's latestResult.
					val result = interpreter.getLatestResult()
					if (Interpreter.debugL1)
					{
						log(
							Interpreter.loggerDebugL1,
							Level.FINER,
							"{0}Call returned: {1}",
							interpreter.debugModeString,
							result.typeTag.name)
					}
					val returnCheckReifier =
						checkReturnType(result, expectedReturnType, function)
					if (returnCheckReifier !== null)
					{
						// Reification is happening within the handling of
						// the failed return type check.
						return returnCheckReifier
					}
					assert(stackp <= code.numSlots)
					// Replace the stack slot.
					pointerAtPut(stackp, result)
				}
				L1Ext_doSetLocalSlot.ordinal ->
				{
					pointerAtPut(instructionDecoder.getOperand(), pop())
				}
			}
		}
		// It ran off the end of the nybblecodes, which is how a function
		// returns in Level One.  Capture the result and return to the Java
		// caller.
		interpreter.setLatestResult(pop())
		assert(stackp == pointers.size)
		interpreter.returnNow = true
		interpreter.returningFunction = function
		if (Interpreter.debugL1)
		{
			log(
				Interpreter.loggerDebugL1,
				Level.FINER,
				"{0}L1 return",
				interpreter.debugModeString)
		}
		return null
	}

	/**
	 * Reify the current frame into the specified [StackReifier].
	 *
	 * @param reifier
	 *   A `StackReifier`.
	 * @param entryPoint
	 *   The [ChunkEntryPoint] at which to resume L1 interpretation.
	 * @param logMessage
	 *   The log message. Expects two template parameters, one for the
	 *   [debug&#32;string][Interpreter.debugModeString], one for the method
	 *   name, respectively.
	 */
	private fun reifyCurrentFrame(
		reifier: StackReifier,
		entryPoint: ChunkEntryPoint,
		logMessage: String)
	{
		val function = interpreter.function!!
		val continuation: A_Continuation = createContinuationWithFrame(
			function,
			nil,
			nil,
			pc(),  // Right after the set-variable.
			stackp,
			L2Chunk.unoptimizedChunk,
			entryPoint.offsetInDefaultChunk,
			listOf(*pointers),
			1)
		if (Interpreter.debugL2)
		{
			log(
				Interpreter.loggerDebugL2,
				Level.FINER,
				logMessage,
				interpreter.debugModeString,
				continuation.function().code().methodName)
		}
		reifier.pushAction { theInterpreter: Interpreter ->
			theInterpreter.setReifiedContinuation(
				continuation.replacingCaller(
					theInterpreter.getReifiedContinuation()!!))
		}
	}

	/**
	 * Get the value from the given variable, reifying and invoking the
	 * [HookType.READ_UNASSIGNED_VARIABLE] hook if the variable has no value.
	 *
	 * @param variable
	 *   The variable to read.
	 * @return
	 *   A [StackReifier] if the variable was unassigned, otherwise the
	 *   [AvailObject] that's the current value of the variable.
	 */
	private fun getVariable(variable: A_Variable): Any
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
			val savedPointers = pointers
			val savedOffset = interpreter.offset
			val savedPc = pc()
			val savedStackp = stackp
			val implicitObserveFunction =
				HookType.READ_UNASSIGNED_VARIABLE[interpreter.runtime]
			interpreter.argsBuffer.clear()
			val reifier =
				interpreter.invokeFunction(implicitObserveFunction)!!
			pointers = savedPointers
			interpreter.chunk = L2Chunk.unoptimizedChunk
			interpreter.setOffset(savedOffset)
			interpreter.function = savedFunction
			savedFunction.code().setUpInstructionDecoder(instructionDecoder)
			instructionDecoder.pc(savedPc)
			stackp = savedStackp
			if (reifier.actuallyReify())
			{
				reifyCurrentFrame(
					reifier, ChunkEntryPoint.UNREACHABLE,
					"{0}Push reified continuation for L1 getVar "
						+ "failure: {1}")
			}
			reifier
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
	 * @return
	 *   A [StackReifier] if the variable was unassigned, otherwise the
	 *   [AvailObject] that's the current value of the variable.
	 */
	private fun getVariableClearingIfMutable(variable: A_Variable): Any
	{
		return try
		{
			if (variable.traversed().descriptor().isMutable)
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
			val savedPointers = pointers
			val savedOffset = interpreter.offset
			val savedPc = pc()
			val savedStackp = stackp
			val implicitObserveFunction =
				HookType.READ_UNASSIGNED_VARIABLE[interpreter.runtime]
			interpreter.argsBuffer.clear()
			val reifier =
				interpreter.invokeFunction(implicitObserveFunction)!!
			pointers = savedPointers
			interpreter.chunk = L2Chunk.unoptimizedChunk
			interpreter.setOffset(savedOffset)
			interpreter.function = savedFunction
			savedFunction.code().setUpInstructionDecoder(instructionDecoder)
			instructionDecoder.pc(savedPc)
			stackp = savedStackp
			if (reifier.actuallyReify())
			{
				reifyCurrentFrame(
					reifier, ChunkEntryPoint.UNREACHABLE,
					"{0}Push reified continuation for L1 getVarClearing "
						+ "failure: {1}")
			}
			reifier
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
	 * @return
	 *   A [StackReifier] to reify the stack if an observed variable is assigned
	 *   while tracing is off, otherwise null.
	 */
	private fun setVariable(
		variable: A_Variable,
		value: AvailObject): StackReifier?
	{
		try
		{
			// The value's reference from the stack is now from the variable.
			variable.setValueNoCheck(value)
		}
		catch (e: VariableSetException)
		{
			assert(e.numericCode.equals(
				AvailErrorCode.E_OBSERVED_VARIABLE_WRITTEN_WHILE_UNTRACED
					.numericCode()))
			val savedFunction = interpreter.function!!
			val savedPointers = pointers
			val savedOffset = interpreter.offset
			val savedPc = pc()
			val savedStackp = stackp
			val implicitObserveFunction =
				interpreter.runtime.implicitObserveFunction()
			interpreter.argsBuffer.clear()
			interpreter.argsBuffer.add((assignmentFunction() as AvailObject))
			interpreter.argsBuffer.add(
				(tuple(variable, value) as AvailObject))
			val reifier =
				interpreter.invokeFunction(implicitObserveFunction)
			pointers = savedPointers
			interpreter.chunk = L2Chunk.unoptimizedChunk
			interpreter.setOffset(savedOffset)
			interpreter.function = savedFunction
			savedFunction.code().setUpInstructionDecoder(instructionDecoder)
			instructionDecoder.pc(savedPc)
			stackp = savedStackp
			if (reifier !== null)
			{
				if (reifier.actuallyReify())
				{
					reifyCurrentFrame(
						reifier, ChunkEntryPoint.TO_RESUME,
						"{0}Push reified continuation for L1 setVar "
							+ "failure: {1}")
				}
				return reifier
			}
		}
		return null
	}

	/**
	 * Check that the matching definition is a method definition, then invoke
	 * its body function.  If reification is requested, construct a suitable
	 * continuation for the current frame on the way out.
	 *
	 * @param matching
	 *   The [A_Definition] that was already looked up.
	 * @return
	 *   Either `null` to indicate successful return from the called function,
	 *   or a [StackReifier] to indicate reification is in progress.
	 */
	private fun callMethodAfterLookup(matching: A_Definition): StackReifier?
	{
		// At this point, the frame information is still the same, but we've set
		// up argsBuffer.
		if (matching.isForwardDefinition())
		{
			return reifyAndReportFailedLookup(
				matching.definitionMethod,
				AvailErrorCode.E_FORWARD_METHOD_DEFINITION)
		}
		if (matching.isAbstractDefinition())
		{
			return reifyAndReportFailedLookup(
				matching.definitionMethod,
				AvailErrorCode.E_ABSTRACT_METHOD_DEFINITION)
		}
		val savedFunction = interpreter.function!!
		assert(interpreter.chunk == L2Chunk.unoptimizedChunk)
		val savedOffset = interpreter.offset
		val savedPointers = pointers
		val savedPc = pc()
		val savedStackp = stackp
		val functionToInvoke = matching.bodyBlock()
		val reifier = interpreter.invokeFunction(functionToInvoke)
		pointers = savedPointers
		interpreter.chunk = L2Chunk.unoptimizedChunk
		interpreter.setOffset(savedOffset)
		interpreter.function = savedFunction
		savedFunction.code().setUpInstructionDecoder(instructionDecoder)
		instructionDecoder.pc(savedPc)
		stackp = savedStackp
		if (reifier !== null)
		{
			if (Interpreter.debugL2)
			{
				log(
					Interpreter.loggerDebugL2,
					Level.FINER,
					"{0}Reifying call from L1 ({1})",
					interpreter.debugModeString,
					reifier.actuallyReify())
			}
			if (reifier.actuallyReify())
			{
				reifyCurrentFrame(
					reifier, ChunkEntryPoint.TO_RETURN_INTO,
					"{0}Push reified continuation for L1 call: {1}")
			}
		}
		return reifier
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
	 * @return
	 *   A [StackReifier] if reification is needed, otherwise `null`.
	 */
	internal fun checkReturnType(
		result: AvailObject,
		expectedReturnType: A_Type,
		returnee: A_Function): StackReifier?
	{
		val before = AvailRuntimeSupport.captureNanos()
		val checkOk = result.isInstanceOf(expectedReturnType)
		val after = AvailRuntimeSupport.captureNanos()
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
			assert(interpreter.chunk == L2Chunk.unoptimizedChunk)
			val savedOffset = interpreter.offset
			val savedPointers = pointers
			val savedPc = pc()
			val savedStackp = stackp
			val reportedResult = newVariableWithContentType(Types.ANY.o)
			reportedResult.setValueNoCheck(result)
			val argsBuffer = interpreter.argsBuffer
			argsBuffer.clear()
			argsBuffer.add(returner as AvailObject)
			argsBuffer.add(expectedReturnType as AvailObject)
			argsBuffer.add(reportedResult)
			val reifier = interpreter.invokeFunction(
				interpreter.runtime.resultDisagreedWithExpectedTypeFunction()
			)!!
			pointers = savedPointers
			interpreter.chunk = L2Chunk.unoptimizedChunk
			interpreter.setOffset(savedOffset)
			interpreter.function = savedFunction
			savedFunction.code().setUpInstructionDecoder(instructionDecoder)
			instructionDecoder.pc(savedPc)
			stackp = savedStackp
			if (reifier.actuallyReify())
			{
				reifyCurrentFrame(
					reifier,
					ChunkEntryPoint.UNREACHABLE,
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
	 * @return
	 *   A [StackReifier] to cause reification.
	 */
	private fun reifyAndReportFailedLookup(
		method: A_Method,
		errorCode: AvailErrorCode): StackReifier
	{
		val arguments = tupleFromList(interpreter.argsBuffer)

		val savedFunction = interpreter.function!!
		assert(interpreter.chunk == L2Chunk.unoptimizedChunk)
		val savedOffset = interpreter.offset
		val savedPointers = pointers
		val savedPc = pc()
		val savedStackp = stackp
		interpreter.argsBuffer.run {
			clear()
			add(errorCode.numericCode().cast())
			add(method.cast())
			add(arguments.cast())
		}
		val reifier = interpreter.invokeFunction(
			interpreter.runtime.invalidMessageSendFunction())!!
		// The function cannot return, so we got a StackReifier back.

		pointers = savedPointers
		interpreter.chunk = L2Chunk.unoptimizedChunk
		interpreter.setOffset(savedOffset)
		interpreter.function = savedFunction
		savedFunction.code().setUpInstructionDecoder(instructionDecoder)
		instructionDecoder.pc(savedPc)
		stackp = savedStackp
		if (reifier.actuallyReify())
		{
			reifyCurrentFrame(
				reifier,
				ChunkEntryPoint.UNREACHABLE,
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

		/** The [CheckedMethod] for [run]. */
		val runMethod = instanceMethod(
			L1InstructionStepper::class.java,
			L1InstructionStepper::run.name,
			StackReifier::class.java)
	}
}
