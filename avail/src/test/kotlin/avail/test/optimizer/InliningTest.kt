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
import avail.descriptor.functions.FunctionDescriptor.Companion.createFunction
import avail.descriptor.numbers.IntegerDescriptor.Companion.fromInt
import avail.descriptor.representation.A_Bundle.Companion.bundleMethod
import avail.descriptor.representation.A_Continuation.Companion.caller
import avail.descriptor.representation.A_Continuation.Companion.function
import avail.descriptor.representation.A_Label
import avail.descriptor.representation.A_Label.Companion.caller
import avail.descriptor.representation.A_Method.Companion.definitionsTuple
import avail.descriptor.representation.A_Number.Companion.extractLong
import avail.descriptor.representation.A_RawFunction
import avail.descriptor.representation.A_RawFunction.Companion.startingChunk
import avail.descriptor.representation.A_Sendable.Companion.bodyBlock
import avail.descriptor.representation.A_Set.Companion.hasElement
import avail.descriptor.representation.A_Variable.Companion.clearValue
import avail.descriptor.representation.A_Variable.Companion.getValue
import avail.descriptor.representation.A_Variable.Companion.hasValue
import avail.descriptor.representation.A_Variable.Companion.setValue
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.tuples.StringDescriptor.Companion.stringFrom
import avail.descriptor.tuples.TupleDescriptor.Companion.emptyTuple
import avail.descriptor.types.BottomTypeDescriptor.Companion.bottom
import avail.descriptor.types.FunctionTypeDescriptor.Companion.mostGeneralFunctionType
import avail.descriptor.types.InstanceMetaDescriptor.Companion.topMeta
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.integers
import avail.descriptor.types.LabelTypeDescriptor.Companion.mostGeneralLabelType
import avail.descriptor.types.PrimitiveTypeDescriptor.Types
import avail.descriptor.types.TupleTypeDescriptor.Companion.tupleTypeForTypes
import avail.descriptor.types.VariableTypeDescriptor.Companion.variableTypeFor
import avail.descriptor.variables.VariableDescriptor.Companion.newVariableWithContentType
import avail.interpreter.levelOne.L1Operation.L1Ext_doGetLiteral
import avail.interpreter.levelOne.L1Operation.L1Ext_doPushLabel
import avail.interpreter.levelOne.L1Operation.L1Ext_doSetLiteral
import avail.interpreter.levelOne.L1Operation.L1_doPushLocal
import avail.interpreter.levelTwo.L2Instruction
import avail.interpreter.levelTwo.L2JVMChunk
import avail.interpreter.levelTwo.operation.L2_FALL_BACK_TO_L1
import avail.interpreter.levelTwo.operation.L2_INLINED_FALL_BACK_TO_L1
import avail.interpreter.levelTwo.operation.L2_INVOKE
import avail.interpreter.levelTwo.operation.L2_INVOKE_CONSTANT_FUNCTION
import avail.interpreter.levelTwo.operation.L2_INVOKE_INVALID_MESSAGE_RESULT_FUNCTION
import avail.interpreter.primitive.general.P_EmergencyExit
import avail.interpreter.primitive.numbers.P_Addition
import avail.interpreter.primitive.rawfunctions.P_PrivateForceOptimizationForTests
import avail.interpreter.primitive.tuples.P_TupleAt
import avail.optimizer.reoptimizer.L2Inliner
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
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
	 * A callee that makes a call of its own might reify, so it is invoked
	 * rather than embedded, and the caller still computes the right result.
	 */
	@Test
	fun testCalleeThatCallsIsInvoked()
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
		assertTrue(
			callee.invokesAFunction,
			"Expected the callee to invoke identity_")
		val caller = helper.rawFunction(integers) {
			argumentTypes(integers)
			L1_doPushLocal(1)
			call("twice identity_", integers)
		}
		helper.testOptimize(caller)
		assertTrue(
			caller.invokesAFunction,
			"Expected the call of twice identity_ to remain a call")
		val result = helper.executeRawFunction(caller, listOf(fromInt(21)))
		assertEquals(42L, result.extractLong)
	}
}
