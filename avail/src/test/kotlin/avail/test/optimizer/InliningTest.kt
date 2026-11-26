/*
 * InliningTest.kt
 * Copyright © 1993-2026, The Avail Foundation, LLC.
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

package avail.test.optimizer

import avail.AvailRuntime.HookType.READ_UNASSIGNED_VARIABLE
import avail.AvailRuntime.HookType.RESULT_DISAGREED_WITH_EXPECTED_TYPE
import avail.descriptor.fiber.FiberDescriptor
import avail.descriptor.fiber.FiberDescriptor.ExecutionState.PARKED
import avail.descriptor.functions.FunctionDescriptor.Companion.createFunction
import avail.descriptor.numbers.InfinityDescriptor.Companion.positiveInfinity
import avail.descriptor.numbers.IntegerDescriptor.Companion.fromInt
import avail.descriptor.numbers.IntegerDescriptor.Companion.fromLong
import avail.descriptor.representation.A_Bundle.Companion.bundleMethod
import avail.descriptor.representation.A_Continuation
import avail.descriptor.representation.A_Continuation.Companion.caller
import avail.descriptor.representation.A_Continuation.Companion.frameAt
import avail.descriptor.representation.A_Continuation.Companion.function
import avail.descriptor.representation.A_Continuation.Companion.levelTwoChunk
import avail.descriptor.representation.A_Fiber
import avail.descriptor.representation.A_Fiber.Companion.continuation
import avail.descriptor.representation.A_Fiber.Companion.executionState
import avail.descriptor.representation.A_Label
import avail.descriptor.representation.A_Label.Companion.caller
import avail.descriptor.representation.A_Method.Companion.definitionsTuple
import avail.descriptor.representation.A_Method.Companion.lookupByValuesFromList
import avail.descriptor.representation.A_Number.Companion.extractLong
import avail.descriptor.representation.A_Number.Companion.plusCanDestroy
import avail.descriptor.representation.A_RawFunction
import avail.descriptor.representation.A_RawFunction.Companion.methodName
import avail.descriptor.representation.A_RawFunction.Companion.numLocals
import avail.descriptor.representation.A_RawFunction.Companion.startingChunk
import avail.descriptor.representation.A_Sendable.Companion.bodyBlock
import avail.descriptor.representation.A_Set.Companion.hasElement
import avail.descriptor.representation.A_String.Companion.asNativeString
import avail.descriptor.representation.A_Variable
import avail.descriptor.representation.A_Variable.Companion.clearValue
import avail.descriptor.representation.A_Variable.Companion.getValue
import avail.descriptor.representation.A_Variable.Companion.hasValue
import avail.descriptor.representation.A_Variable.Companion.setValue
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.tuples.StringDescriptor.Companion.stringFrom
import avail.descriptor.tuples.TupleDescriptor.Companion.emptyTuple
import avail.descriptor.types.BottomTypeDescriptor.Companion.bottom
import avail.descriptor.types.FiberTypeDescriptor.Companion.mostGeneralFiberType
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.FunctionTypeDescriptor.Companion.mostGeneralFunctionType
import avail.descriptor.types.InstanceMetaDescriptor.Companion.topMeta
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.extendedIntegers
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.extendedIntegersMeta
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.inclusive
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.integers
import avail.descriptor.types.LabelTypeDescriptor.Companion.mostGeneralLabelType
import avail.descriptor.types.PrimitiveTypeDescriptor.Types
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.TOP
import avail.descriptor.types.TupleTypeDescriptor.Companion.mostGeneralTupleType
import avail.descriptor.types.TupleTypeDescriptor.Companion.tupleTypeForTypes
import avail.descriptor.types.VariableTypeDescriptor.Companion.variableTypeFor
import avail.descriptor.variables.VariableDescriptor.Companion.newVariableWithContentType
import avail.interpreter.levelOne.L1Operation.L1Ext_doGetLiteral
import avail.interpreter.levelOne.L1Operation.L1Ext_doPushLabel
import avail.interpreter.levelOne.L1Operation.L1Ext_doSetLiteral
import avail.interpreter.levelOne.L1Operation.L1_doClose
import avail.interpreter.levelOne.L1Operation.L1_doGetLocal
import avail.interpreter.levelOne.L1Operation.L1_doGetOuter
import avail.interpreter.levelOne.L1Operation.L1_doMakeTuple
import avail.interpreter.levelOne.L1Operation.L1_doPop
import avail.interpreter.levelOne.L1Operation.L1_doPushLocal
import avail.interpreter.levelOne.L1Operation.L1_doPushOuter
import avail.interpreter.levelOne.L1Operation.L1_doSetLocal
import avail.interpreter.levelTwo.L2Chunk
import avail.interpreter.levelTwo.L2Chunk.InvalidationReason.DEPENDENCY_CHANGED
import avail.interpreter.levelTwo.L2Instruction
import avail.interpreter.levelTwo.L2JVMChunk
import avail.interpreter.levelTwo.operation.L2_ADD_UNDER_FROZEN_FRAME
import avail.interpreter.levelTwo.operation.L2_ENTER_FREEZE
import avail.interpreter.levelTwo.operation.L2_FALL_BACK_TO_L1
import avail.interpreter.levelTwo.operation.L2_INLINED_FALL_BACK_TO_L1
import avail.interpreter.levelTwo.operation.L2_INVOKE
import avail.interpreter.levelTwo.operation.L2_INVOKE_CONSTANT_FUNCTION
import avail.interpreter.levelTwo.operation.L2_INVOKE_INVALID_MESSAGE_RESULT_FUNCTION
import avail.interpreter.levelTwo.operation.L2_MOVE_CONSTANT_BOXED
import avail.interpreter.levelTwo.operation.L2_PHI
import avail.interpreter.levelTwo.operation.L2_RUN_INFALLIBLE_PRIMITIVE
import avail.interpreter.levelTwo.operation.variables.L2_CREATE_VARIABLE
import avail.interpreter.primitive.controlflow.P_InvokeWithTuple
import avail.interpreter.primitive.fibers.P_CurrentFiber
import avail.interpreter.primitive.fibers.P_ParkCurrentFiber
import avail.interpreter.primitive.fibers.P_UnparkFiber
import avail.interpreter.primitive.functions.P_CloseFunction
import avail.interpreter.primitive.general.P_EmergencyExit
import avail.interpreter.primitive.integers.P_UpperBound
import avail.interpreter.primitive.numbers.P_Addition
import avail.interpreter.primitive.numbers.P_Subtraction
import avail.interpreter.primitive.rawfunctions.P_PrivateForceOptimizationForTests
import avail.interpreter.primitive.tuples.P_TupleAt
import avail.optimizer.L2BasicBlock
import avail.optimizer.reoptimizer.L2Inliner
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.withLock
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.api.assertThrows

/**
 * Tests of [L2Inliner], which embeds a suitable callee's graph at a call site
 * instead of invoking the callee.
 */
class InliningTest
{
	/** The [OptimizerTestHelper] that holds this test's runtime. */
	lateinit var helper: OptimizerTestHelper

	/**
	 * Create the runtime, with the primitives these tests call.
	 *
	 * @param testInfo
	 *   The JUnit description of the test about to run.
	 */
	@BeforeEach
	fun setUp(testInfo: TestInfo)
	{
		helper = OptimizerTestHelper(testInfo.displayName)
		helper.definePrimitives(
			"PrivateForceOptimizationForTests:_" to
				P_PrivateForceOptimizationForTests,
			"Crash:_" to P_EmergencyExit,
			"_+_" to P_Addition)
	}

	/** Shut down this test's runtime. */
	@AfterEach
	fun tearDown()
	{
		helper.runtime.destroy()
	}

	/**
	 * Answer the [A_RawFunction] of the sole definition of the named method.
	 *
	 * @param name
	 *   The method's name.
	 * @return
	 *   The body of its definition.
	 */
	private fun soleBodyOf(name: String): A_RawFunction =
		helper.lookup(name).bundleMethod.definitionsTuple.single()
			.bodyBlock().code()

	/** Answer the [L2Instruction]s of the receiver's optimized chunk. */
	private val A_RawFunction.l2Instructions: List<L2Instruction>
		get() = startingChunk.instructions.filterIsInstance<L2Instruction>()

	/** Answer whether the receiver's optimized chunk invokes any function. */
	private val A_RawFunction.invokesAFunction: Boolean
		get() = l2Instructions.any {
			it is L2_INVOKE || it is L2_INVOKE_CONSTANT_FUNCTION
		}

	/**
	 * Callees are embedded one inside another at most twenty deep.  Each of a
	 * chain of functions calls the next, and each is optimized after the one
	 * it calls, so each embeds the next, which already embeds the rest.  The
	 * function whose chunk is twenty deep is not embedded in its caller,
	 * which calls it instead.
	 */
	@Test
	fun testEmbeddingIsAtMostTwentyDeep()
	{
		val names = (0 .. 21).map { "level ${it}_" }
		// Define each level after the one it calls, which must exist first.
		names.indices.reversed().forEach { index ->
			helper.defineMethod(names[index], integers) {
				argumentTypes(integers)
				L1_doPushLocal(1)
				when (val next = names.getOrNull(index + 1))
				{
					null ->
					{
						pushLiteral(fromInt(1))
						call("_+_", integers)
					}
					else -> call(next, integers)
				}
			}
		}
		val bodies = names.map(::soleBodyOf)
		bodies.reversed().forEach(helper::testOptimize)
		val depths = bodies.map {
			(it.startingChunk as L2JVMChunk).embeddingDepth
		}
		assertEquals(
			listOf(0) + (20 downTo 1) + 0,
			depths,
			"Expected each level to embed the next, up to twenty deep")
		assertTrue(
			bodies[0].invokesAFunction,
			"Expected the outermost level to call the next, not embed it")
		assertEquals(
			fromInt(43),
			helper.executeRawFunction(bodies[0], listOf(fromInt(42))))
	}

	/**
	 * A call of an optimized function that calls only an infallible
	 * primitive is embedded in its caller.  The caller's chunk invokes
	 * nothing, notes where the callee was embedded, depends on everything
	 * the callee's chunk depends on, and computes the same results.
	 */
	@Test
	fun testCalleeWithoutCallsIsEmbedded()
	{
		helper.defineMethod("twice_", integers) {
			argumentTypes(integers)
			L1_doPushLocal(1)
			L1_doPushLocal(1)
			call("_+_", integers)
		}
		val callee = soleBodyOf("twice_")
		helper.testOptimize(callee)
		val calleeChunk = callee.startingChunk as L2JVMChunk
		assertNotNull(calleeChunk.inlinableGraph)
		assertTrue(
			!callee.invokesAFunction,
			"Expected the callee to call only an infallible primitive")
		val caller = helper.rawFunction(integers) {
			argumentTypes(integers)
			L1_doPushLocal(1)
			call("twice_", integers)
			pushLiteral(fromInt(1))
			call("_+_", integers)
		}
		helper.testOptimize(caller)
		assertTrue(
			!caller.invokesAFunction,
			"Expected the call of twice_ to be embedded")
		val callerChunk = caller.startingChunk as L2JVMChunk
		assertTrue(
			callerChunk.controlFlowGraph.basicBlockOrder.any {
				"INLINING: twice_" in it.debugNote
			},
			"Expected a block noting where twice_ was embedded")
		for (value in calleeChunk.contingentValues)
		{
			assertTrue(
				callerChunk.contingentValues.hasElement(value),
				"Expected the caller to depend on $value")
		}
		for (value in listOf(0, 5, -7, 1 shl 30))
		{
			val result = helper.executeRawFunction(
				caller, listOf(fromInt(value)))
			assertEquals(2L * value + 1, result.extractLong)
		}
	}

	/**
	 * When the caller's check of an embedded callee's result fails, the
	 * failure is reported as coming from the callee, although the callee never
	 * returned through the interpreter.  The hook for a disagreeing result
	 * records the returning function it was given, then crashes the fiber.
	 */
	@Test
	fun testEmbeddedCalleeIsBlamedForWrongResult()
	{
		helper.defineMethod("loose_", Types.ANY()) {
			argumentTypes(Types.ANY())
			L1_doPushLocal(1)
		}
		val callee = soleBodyOf("loose_")
		helper.testOptimize(callee)
		assertNotNull((callee.startingChunk as L2JVMChunk).inlinableGraph)
		val blamed = newVariableWithContentType(mostGeneralFunctionType)
		val hook = helper.rawFunction(bottom) {
			argumentTypes(
				mostGeneralFunctionType,
				topMeta,
				variableTypeFor(Types.ANY()))
			L1_doPushLocal(1)
			L1Ext_doSetLiteral(addLiteral(blamed))
			pushLiteral(stringFrom("result disagreed"))
			call("Crash:_", bottom)
		}
		helper.runtime[RESULT_DISAGREED_WITH_EXPECTED_TYPE] =
			createFunction(hook, emptyTuple)
		val caller = helper.rawFunction(integers) {
			argumentTypes(Types.ANY())
			L1_doPushLocal(1)
			call("loose_", integers)
		}
		helper.testOptimize(caller)
		assertTrue(
			!caller.invokesAFunction,
			"Expected the call of loose_ to be embedded")
		assertEquals(
			fromInt(7),
			helper.executeRawFunction(caller, listOf(fromInt(7))))
		assertThrows<Throwable> {
			helper.executeRawFunction(caller, listOf(stringFrom("seven")))
		}
		assertTrue(blamed.hasValue(), "Expected the check to fail")
		assertTrue(
			blamed.getValue().code().equals(callee),
			"Expected the embedded callee to be blamed")
	}

	/**
	 * A callee that falls back to L1, here when it reads an unassigned
	 * variable, is still embedded, and its fallback continues it in L1.  If
	 * the L1 code reifies, the caller's frame is reified through its own
	 * reification path, beneath the callee's L1 frame.  The hook for reading
	 * an unassigned variable takes a label for its own frame, which reifies
	 * the whole stack, records it, then crashes the fiber.
	 */
	@Test
	fun testEmbeddedFallbackReifiesCallerBeneathCallee()
	{
		val global = newVariableWithContentType(integers)
		helper.defineMethod("plus global_", integers) {
			argumentTypes(integers)
			L1_doPushLocal(1)
			L1Ext_doGetLiteral(addLiteral(global))
			call("_+_", integers)
		}
		val callee = soleBodyOf("plus global_")
		helper.testOptimize(callee)
		assertTrue(
			callee.l2Instructions.any { it is L2_FALL_BACK_TO_L1 },
			"Expected the callee to fall back to L1 if the read fails")
		assertTrue(
			!callee.invokesAFunction,
			"Expected the callee to make no calls")
		val label = newVariableWithContentType(mostGeneralLabelType)
		val hook = helper.rawFunction(bottom) {
			L1Ext_doPushLabel()
			L1Ext_doSetLiteral(addLiteral(label))
			pushLiteral(stringFrom("unassigned"))
			call("Crash:_", bottom)
		}
		helper.runtime[READ_UNASSIGNED_VARIABLE] =
			createFunction(hook, emptyTuple)
		val caller = helper.rawFunction(integers) {
			argumentTypes(integers)
			L1_doPushLocal(1)
			call("plus global_", integers)
		}
		helper.testOptimize(caller)
		assertTrue(
			!caller.invokesAFunction,
			"Expected the call of plus global_ to be embedded")
		assertTrue(
			caller.l2Instructions.any { it is L2_INLINED_FALL_BACK_TO_L1 },
			"Expected the embedded fallback")
		global.setValue(fromInt(10))
		assertEquals(
			fromInt(15),
			helper.executeRawFunction(caller, listOf(fromInt(5))))
		global.clearValue()
		assertThrows<Throwable> {
			helper.executeRawFunction(caller, listOf(fromInt(5)))
		}
		assertTrue(label.hasValue(), "Expected the hook to take a label")
		val hookLabel: A_Label = label.getValue()
		val calleeFrame = hookLabel.caller
		assertTrue(
			calleeFrame.function.code().equals(callee),
			"Expected the hook to have been called from the callee")
		assertTrue(
			calleeFrame.caller.function.code().equals(caller),
			"Expected the callee's frame to have the caller's beneath it")
	}

	/**
	 * A callee that checks the result of a primitive it runs in place, and
	 * reports a wrong result, is still embedded.  The report stacks the
	 * callee's frame for reification, and the caller's frame is stacked
	 * beneath it through the call site's reification path.  The hook for a
	 * wrong result takes a label for its own frame, which reifies the whole
	 * stack, records it, then crashes the fiber.
	 */
	@Test
	fun testEmbeddedWrongResultReifiesCallerBeneathCallee()
	{
		helper.definePrimitives("_[_]" to P_TupleAt)
		val singleton = tupleTypeForTypes(Types.ANY())
		helper.defineMethod("first of_", integers) {
			argumentTypes(singleton)
			L1_doPushLocal(1)
			pushLiteral(fromInt(1))
			call("_[_]", integers)
		}
		val callee = soleBodyOf("first of_")
		helper.testOptimize(callee)
		assertTrue(
			callee.l2Instructions.any {
				it is L2_INVOKE_INVALID_MESSAGE_RESULT_FUNCTION
			},
			"Expected the callee to check the element")
		assertTrue(
			!callee.invokesAFunction,
			"Expected the callee to make no calls")
		val label = newVariableWithContentType(mostGeneralLabelType)
		val hook = helper.rawFunction(bottom) {
			argumentTypes(
				mostGeneralFunctionType,
				topMeta,
				variableTypeFor(Types.ANY()))
			L1Ext_doPushLabel()
			L1Ext_doSetLiteral(addLiteral(label))
			pushLiteral(stringFrom("result disagreed"))
			call("Crash:_", bottom)
		}
		helper.runtime[RESULT_DISAGREED_WITH_EXPECTED_TYPE] =
			createFunction(hook, emptyTuple)
		val caller = helper.rawFunction(integers) {
			argumentTypes(singleton)
			L1_doPushLocal(1)
			call("first of_", integers)
		}
		helper.testOptimize(caller)
		assertTrue(
			!caller.invokesAFunction,
			"Expected the call of first of_ to be embedded")
		assertEquals(
			fromInt(3),
			helper.executeRawFunction(caller, listOf(tuple(fromInt(3)))))
		assertThrows<Throwable> {
			helper.executeRawFunction(
				caller, listOf(tuple(stringFrom("three"))))
		}
		assertTrue(label.hasValue(), "Expected the hook to take a label")
		val hookLabel: A_Label = label.getValue()
		val calleeFrame = hookLabel.caller
		assertTrue(
			calleeFrame.function.code().equals(callee),
			"Expected the hook to have been called for the callee")
		assertTrue(
			calleeFrame.caller.function.code().equals(caller),
			"Expected the callee's frame to have the caller's beneath it")
	}

	/**
	 * A callee that makes a call of its own is embedded, keeping its call,
	 * and the caller computes the right result.
	 */
	@Test
	fun testCalleeThatCallsIsEmbedded()
	{
		helper.defineMethod("identity_", integers) {
			argumentTypes(integers)
			L1_doPushLocal(1)
		}
		helper.defineMethod("twice identity_", integers) {
			argumentTypes(integers)
			L1_doPushLocal(1)
			call("identity_", integers)
			L1_doPushLocal(1)
			call("_+_", integers)
		}
		val callee = soleBodyOf("twice identity_")
		helper.testOptimize(callee)
		assertNotNull((callee.startingChunk as L2JVMChunk).inlinableGraph)
		assertTrue(
			callee.invokesAFunction,
			"Expected the callee to invoke identity_")
		val caller = helper.rawFunction(integers) {
			argumentTypes(integers)
			L1_doPushLocal(1)
			call("twice identity_", integers)
		}
		helper.testOptimize(caller)
		assertEmbedded(caller, "twice identity_")
		val result = helper.executeRawFunction(caller, listOf(fromInt(21)))
		assertEquals(42L, result.extractLong)
	}

	/**
	 * Two embeddings of one callee, applied to the same argument, compute
	 * congruent primitive invocations, which the manifest must treat as one
	 * value.  The first embedding's result is consumed at once, so its
	 * invocation is emitted; the second's is only gathered into a tuple, and
	 * reaches the manifest when the embedding replays the callee's post-phi
	 * data.  The addition that follows splits on whether its operand fits in
	 * a machine integer, and the merge after it sees both invocations.  If
	 * they were not made one value as the second arrived, that merge would
	 * find each already holding a register.
	 *
	 * The caller is run on a bounded range and on an unbounded one with an
	 * addend that overflows a machine integer, so that both arms of the split
	 * are taken.
	 */
	@Test
	fun testCongruentEmbeddedInvocationsAreOneValue()
	{
		helper.definePrimitives(
			"_-_" to P_Subtraction,
			"_'s⁇genuine upper bound" to P_UpperBound)
		helper.defineMethod("upper_", extendedIntegers) {
			argumentTypes(extendedIntegersMeta)
			L1_doPushLocal(1)
			call("_'s⁇genuine upper bound", extendedIntegers)
		}
		val callee = soleBodyOf("upper_")
		helper.testOptimize(callee)
		assertNotNull((callee.startingChunk as L2JVMChunk).inlinableGraph)
		val caller = helper.rawFunction(mostGeneralTupleType) {
			argumentTypes(extendedIntegersMeta, extendedIntegers)
			// :: <upper base - 1, upper base>
			L1_doPushLocal(1)
			call("upper_", extendedIntegers)
			pushLiteral(fromInt(1))
			call("_-_", extendedIntegers)
			L1_doPushLocal(1)
			call("upper_", extendedIntegers)
			L1_doMakeTuple(2)
			// :: n + 1
			L1_doPushLocal(2)
			pushLiteral(fromInt(1))
			call("_+_", extendedIntegers)
			L1_doMakeTuple(2)
		}
		helper.testOptimize(caller)
		assertEmbedded(caller, "upper_")
		val bounded = helper.executeRawFunction(
			caller, listOf(inclusive(1, 10), fromInt(5)))
		assertTrue(
			bounded.equals(tuple(tuple(fromInt(9), fromInt(10)), fromInt(6))),
			"Expected <<9, 10>, 6>, not $bounded")
		val unbounded = helper.executeRawFunction(
			caller, listOf(integers, fromLong(Long.MAX_VALUE)))
		val expected = tuple(
			tuple(positiveInfinity, positiveInfinity),
			fromLong(Long.MAX_VALUE).plusCanDestroy(fromInt(1), false))
		assertTrue(
			unbounded.equals(expected),
			"Expected $expected, not $unbounded")
	}

	/**
	 * A caller that dispatches on an object's variant, then calls an embedded
	 * callee that dispatches on the same object's variant, tests the variant
	 * only once.  The embedded dispatch wishes the variant were known, and
	 * code splitting in the caller keeps the arms of the caller's own dispatch
	 * apart through the call.
	 */
	@Test
	fun testVariantDispatchedByCallerIsKnownInCallee()
	{
		val dispatch = helper.defineVariantDispatch()
		helper.defineMethod("second of_", integers) {
			argumentTypes(dispatch.commonType)
			L1_doPushLocal(1)
			call("second_", integers)
		}
		val callee = soleBodyOf("second of_")
		helper.testOptimize(callee)
		assertNotNull((callee.startingChunk as L2JVMChunk).inlinableGraph)
		val caller = helper.rawFunction(mostGeneralTupleType) {
			argumentTypes(dispatch.commonType)
			// :: <first x, second of x>
			L1_doPushLocal(1)
			call("first_", integers)
			L1_doPushLocal(1)
			call("second of_", integers)
			L1_doMakeTuple(2)
		}
		helper.testOptimize(caller)
		assertEmbedded(caller, "second of_")
		dispatch.assertSecondFollowsFirstDirectly(caller)
		dispatch.assertComputesPairs(caller)
	}

	/**
	 * A caller that calls an embedded callee that dispatches on an object's
	 * variant, then dispatches on the same object's variant itself, tests the
	 * variant only once.  The caller's dispatch wishes the variant were known,
	 * and code splitting in the caller keeps the arms of the embedded dispatch
	 * apart through the return from the callee.
	 */
	@Test
	fun testVariantDispatchedByCalleeIsKnownInCaller()
	{
		val dispatch = helper.defineVariantDispatch()
		helper.defineMethod("first of_", integers) {
			argumentTypes(dispatch.commonType)
			L1_doPushLocal(1)
			call("first_", integers)
		}
		val callee = soleBodyOf("first of_")
		helper.testOptimize(callee)
		assertNotNull((callee.startingChunk as L2JVMChunk).inlinableGraph)
		val caller = helper.rawFunction(mostGeneralTupleType) {
			argumentTypes(dispatch.commonType)
			// :: <first of x, second x>
			L1_doPushLocal(1)
			call("first of_", integers)
			L1_doPushLocal(1)
			call("second_", integers)
			L1_doMakeTuple(2)
		}
		helper.testOptimize(caller)
		assertEmbedded(caller, "first of_")
		dispatch.assertSecondFollowsFirstDirectly(caller)
		dispatch.assertComputesPairs(caller)
	}

	/**
	 * A caller that invokes a function closed by one of two embedded
	 * definitions, each closing a different block over the argument, invokes
	 * each closure apart from the other.  The invocation wishes the closure's
	 * raw function were known, so code splitting keeps the arms of the
	 * embedded dispatch apart through the merge, and each invocation is
	 * reached only by its own closing.
	 */
	@Test
	fun testClosingsOfDifferentBlocksAreInvokedApart()
	{
		helper.definePrimitives("Invoke_with tuple_" to P_InvokeWithTuple)
		val dispatch = helper.defineVariantDispatch()
		val blocks = listOf(1, 2).map { answer ->
			helper.rawFunction(integers) {
				val x = createOuter(dispatch.commonType)
				// Touch the outer, so that the block is not just a constant.
				L1_doPushOuter(x)
				L1_doPop()
				pushLiteral(fromInt(answer))
			}
		}
		assertNotEquals(blocks[0], blocks[1])
		// The type says the blocks take no arguments, so invoking one with an
		// empty tuple is a call of the function, not of the primitive.
		val blockType = functionType(emptyTuple, integers)
		listOf(dispatch.left, dispatch.right).zip(blocks).forEach {
				(value, block) ->
			helper.defineMethod("block for_", blockType) {
				argumentTypes(value.kind())
				L1_doPushLocal(1)
				L1_doClose(1, addLiteral(block))
			}
		}
		val method = helper.lookup("block for_").bundleMethod
		listOf(dispatch.left, dispatch.right).forEach { value ->
			method.lookupByValuesFromList(listOf(value), null)
		}
		method.definitionsTuple.forEach { definition ->
			val body = definition.bodyBlock().code()
			helper.testOptimize(body)
			assertNotNull((body.startingChunk as L2JVMChunk).inlinableGraph)
		}
		val caller = helper.rawFunction(integers) {
			argumentTypes(dispatch.commonType)
			// :: invoke block for x with <>
			L1_doPushLocal(1)
			call("block for_", blockType)
			pushLiteral(emptyTuple)
			call("Invoke_with tuple_", integers)
		}
		helper.testOptimize(caller)
		assertEmbedded(caller, "block for_")
		// Examine the graph while it is still in SSA form, before registers
		// that never interfere are merged.
		val graph = (caller.startingChunk as L2JVMChunk).inlinableGraph
		assertNotNull(graph)
		val invocations = graph!!.basicBlockOrder
			.filterNot(L2BasicBlock::isCold)
			.flatMap(L2BasicBlock::instructions)
			.filterIsInstance<L2_INVOKE>()
		// The fallback for other kinds of shape invokes whatever function its
		// lookup finds, and then whatever that answers, so only the closings
		// are examined, but no invocation may be of a merge of closings.
		val origins = invocations.map {
			it.calledFunction.definitionSkippingMoves()
		}
		val mergedClosings = origins.filterIsInstance<L2_PHI<*>>().filter {
			it.sources.elements.any { source ->
				closedRawFunctionOrNull(source.definitionSkippingMoves()) !==
					null
			}
		}
		assertTrue(
			mergedClosings.isEmpty(),
			"Expected no invocation of merged closings: $mergedClosings")
		assertEquals(
			blocks.toSet(),
			origins.mapNotNullTo(mutableSetOf(), ::closedRawFunctionOrNull),
			"Expected each block to be invoked only after its own closing")
		listOf(dispatch.left, dispatch.right).zip(listOf(1, 2)).forEach {
				(value, answer) ->
			assertEquals(
				fromInt(answer),
				helper.executeRawFunction(caller, listOf(value)))
		}
	}

	/**
	 * Answer the raw function that the given instruction closes, if it is an
	 * [L2_RUN_INFALLIBLE_PRIMITIVE] of [P_CloseFunction], or `null` if it is
	 * not a closing.
	 *
	 * @param instruction
	 *   The [L2Instruction] that produced an invoked function.
	 * @return
	 *   The [A_RawFunction] it closes, or `null`.
	 */
	private fun closedRawFunctionOrNull(
		instruction: L2Instruction
	): A_RawFunction?
	{
		if (instruction !is L2_RUN_INFALLIBLE_PRIMITIVE
			|| instruction.primitive.constant !== P_CloseFunction)
		{
			return null
		}
		val constant =
			instruction.arguments.elements[0].definitionSkippingMoves()
		assertTrue(constant is L2_MOVE_CONSTANT_BOXED, "$constant")
		return (constant as L2_MOVE_CONSTANT_BOXED).constant().constant
	}

	/**
	 * Freezing the continuation of an embedded callee also builds its
	 * caller's frame, as an L1 continuation beneath the callee's, even though
	 * the callee has no local variables.  The callee calls "share caller",
	 * which shares a label whose caller is the callee's frame, which shares
	 * (and so freezes) the whole stack.  The callee then resumes in L1 and
	 * returns into the caller's new frame, which runs the rest of the caller
	 * exactly once.
	 */
	@Test
	fun testFreezeBuildsCallerBeneathEmbeddedCallee()
	{
		val shared = newVariableWithContentType(mostGeneralLabelType)
			.makeShared()
		defineShareCaller(shared)
		defineShareThenAddOne()
		val callee = soleBodyOf("share then add one to_")
		helper.testOptimize(callee)
		assertNotNull((callee.startingChunk as L2JVMChunk).inlinableGraph)
		assertEquals(0, callee.numLocals, "Expected a callee with no locals")
		val counter = defineCounted()
		val caller = helper.rawFunction(integers) {
			argumentTypes(integers)
			L1_doPushLocal(1)
			call("share then add one to_", integers)
			call("counted_", integers)
		}
		helper.testOptimize(caller)
		assertEmbedded(caller, "share then add one to_")
		assertTrue(
			caller.l2Instructions.any { it is L2_ADD_UNDER_FROZEN_FRAME },
			"Expected the callee's freeze to build the caller's frame")
		counter.setValue(fromInt(0))
		assertEquals(
			fromInt(6),
			helper.executeRawFunction(caller, listOf(fromInt(5))))
		assertEquals(fromInt(1), counter.getValue())
		assertTrue(shared.hasValue(), "Expected the callee to share a label")
		val frames = framesBeneath(shared.getValue())
		assertTrue(
			frames[0].function.code().equals(callee),
			"Expected the label's caller to be the callee's frame")
		assertTrue(
			frames[1].function.code().equals(caller),
			"Expected the caller's frame beneath the callee's")
	}

	/**
	 * Freezing an embedded callee's continuation creates the variables whose
	 * creation was postponed in both the callee and its caller, each in its
	 * own frame, holding the values assigned before the call.
	 */
	@Test
	fun testFreezeCreatesElidedLocalsOfCalleeAndCaller()
	{
		val shared = newVariableWithContentType(mostGeneralLabelType)
			.makeShared()
		defineShareCaller(shared)
		helper.defineMethod("plus one around share_", integers) {
			argumentTypes(integers)
			val y = createLocal(variableTypeFor(integers))
			// :: y := n + 1;
			L1_doPushLocal(1)
			pushLiteral(fromInt(1))
			call("_+_", integers)
			L1_doSetLocal(y)
			// :: share caller;
			call("share caller", TOP())
			L1_doPop()
			// :: y
			L1_doGetLocal(y)
		}
		val callee = soleBodyOf("plus one around share_")
		helper.testOptimize(callee)
		assertNotNull((callee.startingChunk as L2JVMChunk).inlinableGraph)
		assertTrue(
			callee.l2Instructions.any { it is L2_ENTER_FREEZE },
			"Expected the callee's local to be elided across its call")
		val counter = defineCounted()
		val caller = helper.rawFunction(integers) {
			argumentTypes(integers)
			val z = createLocal(variableTypeFor(integers))
			// :: z := x + 2;
			L1_doPushLocal(1)
			pushLiteral(fromInt(2))
			call("_+_", integers)
			L1_doSetLocal(z)
			// :: plus one around share x + z
			L1_doPushLocal(1)
			call("plus one around share_", integers)
			L1_doGetLocal(z)
			call("_+_", integers)
			call("counted_", integers)
		}
		helper.testOptimize(caller)
		assertEmbedded(caller, "plus one around share_")
		assertNoVariableCreatedWhenWarm(caller)
		counter.setValue(fromInt(0))
		assertEquals(
			fromInt(13),
			helper.executeRawFunction(caller, listOf(fromInt(5))))
		assertEquals(fromInt(1), counter.getValue())
		val frames = framesBeneath(shared.getValue())
		assertTrue(frames[0].function.code().equals(callee))
		assertEquals(fromInt(6), frames[0].frameAt(2).getValue())
		assertTrue(frames[1].function.code().equals(caller))
		assertEquals(fromInt(7), frames[1].frameAt(2).getValue())
	}

	/**
	 * A callee that embeds a callee of its own can itself be embedded.
	 * Freezing the innermost callee's continuation builds the frames of both
	 * enclosing calls, innermost first.
	 */
	@Test
	fun testFreezeBuildsFramesOfNestedEmbeddings()
	{
		val shared = newVariableWithContentType(mostGeneralLabelType)
			.makeShared()
		defineShareCaller(shared)
		defineShareThenAddOne()
		val innermost = soleBodyOf("share then add one to_")
		helper.testOptimize(innermost)
		helper.defineMethod("add one via share_", integers) {
			argumentTypes(integers)
			L1_doPushLocal(1)
			call("share then add one to_", integers)
			pushLiteral(fromInt(1))
			call("_+_", integers)
		}
		val middle = soleBodyOf("add one via share_")
		helper.testOptimize(middle)
		assertEmbedded(middle, "share then add one to_")
		assertNotNull((middle.startingChunk as L2JVMChunk).inlinableGraph)
		val counter = defineCounted()
		val caller = helper.rawFunction(integers) {
			argumentTypes(integers)
			L1_doPushLocal(1)
			call("add one via share_", integers)
			call("counted_", integers)
		}
		helper.testOptimize(caller)
		assertEmbedded(caller, "add one via share_")
		assertTrue(
			caller.l2Instructions.count { it is L2_ADD_UNDER_FROZEN_FRAME }
				>= 2,
			"Expected freezes to build both enclosing frames")
		counter.setValue(fromInt(0))
		assertEquals(
			fromInt(7),
			helper.executeRawFunction(caller, listOf(fromInt(5))))
		assertEquals(fromInt(1), counter.getValue())
		val frames = framesBeneath(shared.getValue())
		assertTrue(frames[0].function.code().equals(innermost))
		assertTrue(frames[1].function.code().equals(middle))
		assertTrue(frames[2].function.code().equals(caller))
	}

	/**
	 * When the caller's chunk is invalidated while an embedded callee's
	 * continuation is suspended, returning into that continuation freezes
	 * it, which builds the caller's frame beneath it, and both resume in L1,
	 * each with its own function.  The callee calls "park here", which
	 * records its fiber and parks it.  The test then invalidates the caller's
	 * chunk at a safe point and unparks the fiber.
	 */
	@Test
	fun testInvalidReentryResumesEmbeddedCalleeInL1()
	{
		helper.definePrimitives(
			"current fiber" to P_CurrentFiber,
			"park" to P_ParkCurrentFiber,
			"Unpark_" to P_UnparkFiber)
		val fiberVariable = newVariableWithContentType(mostGeneralFiberType())
			.makeShared()
		helper.defineMethod("park here", TOP()) {
			// :: recorded fiber := current fiber;
			call("current fiber", mostGeneralFiberType())
			L1Ext_doSetLiteral(addLiteral(fiberVariable))
			// :: park;  A new fiber holds a permit, which this consumes.
			call("park", TOP())
			L1_doPop()
			// :: park
			call("park", TOP())
		}
		helper.defineMethod("park then add one to_", integers) {
			argumentTypes(integers)
			call("park here", TOP())
			L1_doPop()
			L1_doPushLocal(1)
			pushLiteral(fromInt(1))
			call("_+_", integers)
		}
		val callee = soleBodyOf("park then add one to_")
		helper.testOptimize(callee)
		assertNotNull((callee.startingChunk as L2JVMChunk).inlinableGraph)
		val counter = defineCounted()
		val caller = helper.rawFunction(integers) {
			argumentTypes(integers)
			L1_doPushLocal(1)
			call("park then add one to_", integers)
			call("counted_", integers)
		}
		helper.testOptimize(caller)
		assertEmbedded(caller, "park then add one to_")
		val callerChunk = caller.startingChunk as L2JVMChunk
		counter.setValue(fromInt(0))
		val result = CompletableFuture.supplyAsync {
			helper.executeRawFunction(caller, listOf(fromInt(5)))
		}
		val deadline = System.nanoTime() + 30_000_000_000L
		while (!fiberVariable.hasValue()
			|| fiberVariable.getValue().executionState !== PARKED)
		{
			assertTrue(System.nanoTime() < deadline && !result.isDone) {
				"Expected the fiber to park, but recorded=" +
					"${fiberVariable.hasValue()}, finished=${result.isDone}" +
					", state=" +
					fiberVariable.takeIf { it.hasValue() }
						?.getValue()?.executionState
			}
			Thread.sleep(1)
		}
		val fiber: A_Fiber = fiberVariable.getValue()
		val calleeFrame = fiber.continuation.caller
		assertTrue(
			calleeFrame.function.code().equals(callee)
				&& calleeFrame.levelTwoChunk === callerChunk
				&& calleeFrame.descriptor.isMutable,
			"Expected the callee's chunk-wide continuation to be suspended")
		val invalidated = CountDownLatch(1)
		helper.runtime.whenSafePointDo(FiberDescriptor.commandPriority) {
			L2Chunk.invalidationLock.withLock {
				callerChunk.invalidate(DEPENDENCY_CHANGED)
			}
			invalidated.countDown()
		}
		invalidated.await()
		assertTrue(!callerChunk.isValid, "Expected the chunk to be invalid")
		val unparker = helper.rawFunction(TOP()) {
			argumentTypes(mostGeneralFiberType())
			L1_doPushLocal(1)
			call("Unpark_", TOP())
		}
		helper.executeRawFunction(unparker, listOf(fiber))
		assertEquals(fromInt(6), result.get(30, TimeUnit.SECONDS))
		assertEquals(fromInt(1), counter.getValue())
	}

	/**
	 * A block that captures one of its creator's local variables, and is
	 * invoked by its creator, is embedded.  When the stack freezes, the
	 * creator's frame and the block's function hold one and the same
	 * variable, holding the value the creator assigned.
	 */
	@Test
	fun testCapturedLocalIsSharedWithEmbeddedBlock()
	{
		val shared = newVariableWithContentType(mostGeneralLabelType)
			.makeShared()
		val counter = defineCounted()
		val (block, caller) = defineCapturingCaller(shared)
		helper.testOptimize(caller)
		// Building the caller names the block after it.
		assertEmbedded(caller, block.methodName.asNativeString())
		counter.setValue(fromInt(0))
		assertEquals(
			fromInt(5),
			helper.executeRawFunction(caller, listOf(fromInt(5))))
		assertEquals(fromInt(1), counter.getValue())
		val frames = framesBeneath(shared.getValue())
		assertTrue(frames[0].function.code().equals(block))
		assertTrue(frames[1].function.code().equals(caller))
		val captured = frames[0].function.outerVarAt(1)
		val local = frames[1].frameAt(2)
		assertTrue(
			captured.equals(local),
			"Expected the block to capture the caller's own variable")
		assertEquals(fromInt(5), local.getValue())
	}

	/**
	 * A local variable captured only by a block that is embedded in its
	 * creator never escapes, so the creator's chunk creates it only in cold
	 * code, when the stack is reified or frozen.
	 */
	// TODO: A local that the L1 code pushes is aliased, and the translator
	//  creates it eagerly, before any embedding can show that it does not
	//  escape.  Eliding it needs escape to be found from the graph instead.
	@Disabled("an embedded block's captured local is not elided yet")
	@Test
	fun testCapturedLocalIsElidedWhenWarm()
	{
		val shared = newVariableWithContentType(mostGeneralLabelType)
			.makeShared()
		defineCounted()
		val (block, caller) = defineCapturingCaller(shared)
		helper.testOptimize(caller)
		assertEmbedded(caller, block.methodName.asNativeString())
		assertNoVariableCreatedWhenWarm(caller)
	}

	/**
	 * Define a block that calls "share caller" (see [defineShareCaller]) and
	 * answers the value of the variable it captures, and optimize it.  Then
	 * create a caller that assigns its argument to a local variable, closes
	 * the block over that variable, invokes it with "Invoke_with tuple_", and
	 * passes the block's answer through "counted_" (see [defineCounted]),
	 * which must already be defined.
	 *
	 * @param sharedVariable
	 *   The shared variable to receive the label that "share caller" makes.
	 * @return
	 *   The block's raw function, and the caller's.
	 */
	private fun defineCapturingCaller(
		sharedVariable: A_Variable
	): Pair<A_RawFunction, A_RawFunction>
	{
		helper.definePrimitives("Invoke_with tuple_" to P_InvokeWithTuple)
		defineShareCaller(sharedVariable)
		val block = helper.rawFunction(integers) {
			val v = createOuter(variableTypeFor(integers))
			// :: share caller;
			call("share caller", TOP())
			L1_doPop()
			// :: v
			L1_doGetOuter(v)
		}
		helper.testOptimize(block)
		assertNotNull((block.startingChunk as L2JVMChunk).inlinableGraph)
		val caller = helper.rawFunction(integers) {
			argumentTypes(integers)
			val v = createLocal(variableTypeFor(integers))
			// :: v := x;
			L1_doPushLocal(1)
			L1_doSetLocal(v)
			// :: counted invoke [share caller; v] with <>
			L1_doPushLocal(v)
			L1_doClose(1, addLiteral(block))
			pushLiteral(emptyTuple)
			call("Invoke_with tuple_", integers)
			call("counted_", integers)
		}
		return block to caller
	}

	/**
	 * Assert that the given function's optimized chunk embedded a call of the
	 * named function, as shown by a basic block whose debug note records
	 * where the callee was embedded.
	 *
	 * @param function
	 *   The [A_RawFunction] whose chunk to examine.
	 * @param name
	 *   The [methodName][A_RawFunction.methodName] of the embedded callee.
	 */
	private fun assertEmbedded(function: A_RawFunction, name: String)
	{
		val chunk = function.startingChunk as L2JVMChunk
		assertTrue(
			chunk.controlFlowGraph.basicBlockOrder.any {
				"INLINING: $name" in it.debugNote
			},
			"Expected a block noting where $name was embedded")
	}

	/**
	 * Assert that the given function's optimized chunk creates variables
	 * only in cold code, which runs only when a frame is reified or frozen.
	 *
	 * @param function
	 *   The [A_RawFunction] whose chunk to examine.
	 */
	private fun assertNoVariableCreatedWhenWarm(function: A_RawFunction)
	{
		val warmCreations = function.l2Instructions.filter {
			it is L2_CREATE_VARIABLE && !it.basicBlock().isCold
		}
		assertTrue(
			warmCreations.isEmpty(),
			"Expected no variable to be created outside cold code: " +
				warmCreations)
	}

	/**
	 * Define "share caller", which pushes a label for its own frame and
	 * writes it into the given shared variable.  That shares the label's
	 * caller, the frame that called "share caller", along with every frame
	 * beneath it, which runs the freeze subgraph of each that has one.
	 *
	 * @param sharedVariable
	 *   The shared variable to receive the label.
	 */
	private fun defineShareCaller(sharedVariable: A_Variable)
	{
		helper.defineMethod("share caller", TOP()) {
			L1Ext_doPushLabel()
			L1Ext_doSetLiteral(addLiteral(sharedVariable))
			pushLiteral(fromInt(0))
		}
	}

	/**
	 * Define "share then add one to_", which calls "share caller" (see
	 * [defineShareCaller]), then answers its argument plus one.  It has no
	 * local variables.
	 */
	private fun defineShareThenAddOne()
	{
		helper.defineMethod("share then add one to_", integers) {
			argumentTypes(integers)
			call("share caller", TOP())
			L1_doPop()
			L1_doPushLocal(1)
			pushLiteral(fromInt(1))
			call("_+_", integers)
		}
	}

	/**
	 * Define "counted_", which adds one to a new counter, then answers its
	 * argument.  A caller that passes a call's result through it shows that
	 * its code after the call ran exactly once.
	 *
	 * @return
	 *   The variable holding the count, initially unassigned.
	 */
	private fun defineCounted(): A_Variable
	{
		val counter = newVariableWithContentType(integers)
		helper.defineMethod("counted_", integers) {
			argumentTypes(integers)
			// :: counter := counter + 1;
			L1Ext_doGetLiteral(addLiteral(counter))
			pushLiteral(fromInt(1))
			call("_+_", integers)
			L1Ext_doSetLiteral(addLiteral(counter))
			// :: value
			L1_doPushLocal(1)
		}
		return counter
	}

	/**
	 * Answer the frames beneath the given label: its caller, then that
	 * frame's caller, and so on.
	 *
	 * @param label
	 *   The [A_Label] whose caller chain to answer.
	 * @return
	 *   The [A_Continuation]s, innermost first.
	 */
	private fun framesBeneath(label: A_Label): List<A_Continuation> =
		generateSequence(label.caller) { frame ->
			frame.caller.takeIf { it.notNil }
		}.toList()
}
