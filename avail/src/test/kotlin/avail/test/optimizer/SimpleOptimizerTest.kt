/*
 * SimpleOptimizerTest.kt
 * Copyright © 1993-2025, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
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

package avail.test.optimizer

import avail.descriptor.atoms.AtomDescriptor
import avail.descriptor.atoms.AtomDescriptor.Companion.createAtom
import avail.descriptor.atoms.AtomDescriptor.Companion.falseObject
import avail.descriptor.atoms.AtomDescriptor.Companion.trueObject
import avail.descriptor.functions.FunctionDescriptor.Companion.createFunction
import avail.descriptor.numbers.InfinityDescriptor.Companion.positiveInfinity
import avail.descriptor.numbers.IntegerDescriptor.Companion.fromInt
import avail.descriptor.numbers.IntegerDescriptor.Companion.fromLong
import avail.descriptor.numbers.IntegerDescriptor.Companion.negativeOne
import avail.descriptor.numbers.IntegerDescriptor.Companion.one
import avail.descriptor.numbers.IntegerDescriptor.Companion.two
import avail.descriptor.numbers.IntegerDescriptor.Companion.zero
import avail.descriptor.representation.A_Bundle.Companion.bundleMethod
import avail.descriptor.representation.A_Method.Companion.lookupByValuesFromList
import avail.descriptor.representation.A_Number.Companion.bitShift
import avail.descriptor.representation.A_Number.Companion.extractInt
import avail.descriptor.representation.A_Number.Companion.minusCanDestroy
import avail.descriptor.representation.A_Number.Companion.noFailMinusCanDestroy
import avail.descriptor.representation.A_Number.Companion.plusCanDestroy
import avail.descriptor.representation.A_RawFunction.Companion.methodName
import avail.descriptor.representation.A_RawFunction.Companion.startingChunk
import avail.descriptor.representation.NilDescriptor.Companion.nil
import avail.descriptor.sets.SetDescriptor.Companion.emptySet
import avail.descriptor.sets.SetDescriptor.Companion.set
import avail.descriptor.sets.SetDescriptor.Companion.setFromCollection
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.tuples.StringDescriptor.Companion.stringFrom
import avail.descriptor.tuples.TupleDescriptor.Companion.emptyTuple
import avail.descriptor.tuples.TupleDescriptor.Companion.tupleFromIntegerList
import avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import avail.descriptor.types.BottomTypeDescriptor.Companion.bottom
import avail.descriptor.types.BottomTypeDescriptor.Companion.bottomMeta
import avail.descriptor.types.EnumerationTypeDescriptor.Companion.booleanType
import avail.descriptor.types.EnumerationTypeDescriptor.Companion.falseType
import avail.descriptor.types.EnumerationTypeDescriptor.Companion.trueType
import avail.descriptor.types.FiberTypeDescriptor.Companion.mostGeneralFiberType
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionMeta
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.InstanceMetaDescriptor.Companion.anyMeta
import avail.descriptor.types.InstanceMetaDescriptor.Companion.instanceMeta
import avail.descriptor.types.InstanceTypeDescriptor.Companion.instanceType
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.extendedIntegers
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.extendedIntegersMeta
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.inclusive
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.integerRangeType
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.integers
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.naturalNumbers
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.u32
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.u8
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.wholeNumbers
import avail.descriptor.types.MapTypeDescriptor.Companion.mapMeta
import avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.PARSE_PHRASE
import avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.SEND_PHRASE
import avail.descriptor.types.PrimitiveTypeDescriptor.Types
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.NUMBER
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.TOP
import avail.descriptor.types.ReadWriteVariableTypeDescriptor.Companion.fromReadAndWriteTypes
import avail.descriptor.types.SetTypeDescriptor.Companion.mostGeneralSetType
import avail.descriptor.types.SetTypeDescriptor.Companion.setMeta
import avail.descriptor.types.SetTypeDescriptor.Companion.setTypeForSizesContentType
import avail.descriptor.types.TupleTypeDescriptor.Companion.mostGeneralTupleType
import avail.descriptor.types.TupleTypeDescriptor.Companion.nonemptyStringType
import avail.descriptor.types.TupleTypeDescriptor.Companion.stringType
import avail.descriptor.types.TupleTypeDescriptor.Companion.tupleMeta
import avail.descriptor.types.TupleTypeDescriptor.Companion.tupleTypeForSizesTypesDefaultType
import avail.descriptor.types.TupleTypeDescriptor.Companion.tupleTypeForTypes
import avail.descriptor.types.TupleTypeDescriptor.Companion.zeroOrMoreOf
import avail.descriptor.types.TupleTypeDescriptor.Companion.zeroOrOneOf
import avail.descriptor.types.TypeTag
import avail.descriptor.types.VariableTypeDescriptor.Companion.variableReadWriteType
import avail.descriptor.types.VariableTypeDescriptor.Companion.variableTypeFor
import avail.interpreter.levelOne.L1Operation.L1Ext_doPermute
import avail.interpreter.levelOne.L1Operation.L1Ext_doPushLabel
import avail.interpreter.levelOne.L1Operation.L1Ext_doSetLocalSlot
import avail.interpreter.levelOne.L1Operation.L1_doGetLastOuter
import avail.interpreter.levelOne.L1Operation.L1_doGetLocal
import avail.interpreter.levelOne.L1Operation.L1_doGetLocalClearing
import avail.interpreter.levelOne.L1Operation.L1_doGetOuter
import avail.interpreter.levelOne.L1Operation.L1_doMakeTuple
import avail.interpreter.levelOne.L1Operation.L1_doPop
import avail.interpreter.levelOne.L1Operation.L1_doPushLastLocal
import avail.interpreter.levelOne.L1Operation.L1_doPushLastOuter
import avail.interpreter.levelOne.L1Operation.L1_doPushLiteral
import avail.interpreter.levelOne.L1Operation.L1_doPushLocal
import avail.interpreter.levelOne.L1Operation.L1_doPushOuter
import avail.interpreter.levelOne.L1Operation.L1_doSetLocal
import avail.interpreter.levelOne.L1Operation.L1_doSetOuter
import avail.interpreter.levelTwo.L2Instruction
import avail.interpreter.levelTwo.operation.L2_IMPOSSIBLE_CODE
import avail.interpreter.levelTwo.operation.L2_JUMP_BACK
import avail.interpreter.levelTwo.operation.NumericComparator
import avail.interpreter.levelTwo.operation.numbers.L2_BOX_INT
import avail.interpreter.levelTwo.operation.numbers.L2_JUMP_IF_COMPARE_INT
import avail.interpreter.levelTwo.operation.numbers.L2_MULTIPLY_INT_BY_INT
import avail.interpreter.levelTwo.operation.numbers.L2_UNBOX_INT
import avail.interpreter.levelTwo.operation.tuples.L2_TUPLE_SUBRANGE_NO_FAIL
import avail.interpreter.primitive.controlflow.P_ExitContinuationWithResultIf
import avail.interpreter.primitive.controlflow.P_IfFalseThenElse
import avail.interpreter.primitive.controlflow.P_IfTrueThenElse
import avail.interpreter.primitive.controlflow.P_InvokeWithTuple
import avail.interpreter.primitive.controlflow.P_RestartContinuationWithArguments
import avail.interpreter.primitive.controlflow.P_ShortCircuitHelper
import avail.interpreter.primitive.doubles.P_DoubleFloor
import avail.interpreter.primitive.fibers.P_CurrentFiber
import avail.interpreter.primitive.floats.P_FloatFloor
import avail.interpreter.primitive.functions.P_ParamTypeAt
import avail.interpreter.primitive.general.P_EmergencyExit
import avail.interpreter.primitive.general.P_Equality
import avail.interpreter.primitive.integers.P_BitShiftLeft
import avail.interpreter.primitive.integers.P_BitShiftRight
import avail.interpreter.primitive.integers.P_BitwiseAnd
import avail.interpreter.primitive.integers.P_BitwiseXor
import avail.interpreter.primitive.integers.P_LowerBound
import avail.interpreter.primitive.maps.P_KeyInMap
import avail.interpreter.primitive.maps.P_MapAtKey
import avail.interpreter.primitive.maps.P_MapSize
import avail.interpreter.primitive.numbers.P_Addition
import avail.interpreter.primitive.numbers.P_Division
import avail.interpreter.primitive.numbers.P_LessOrEqual
import avail.interpreter.primitive.numbers.P_LessThan
import avail.interpreter.primitive.numbers.P_Multiplication
import avail.interpreter.primitive.numbers.P_Subtraction
import avail.interpreter.primitive.privatehelpers.P_PushArgument1
import avail.interpreter.primitive.privatehelpers.P_PushConstant
import avail.interpreter.primitive.rawfunctions.P_PrivateForceOptimizationForTests
import avail.interpreter.primitive.sets.P_ElementInSet
import avail.interpreter.primitive.sets.P_SetIsSubset
import avail.interpreter.primitive.sets.P_SetSize
import avail.interpreter.primitive.sets.P_SetToTuple
import avail.interpreter.primitive.sets.P_TupleToSet
import avail.interpreter.primitive.tuples.P_ExtractSubtuple
import avail.interpreter.primitive.tuples.P_IntegerIntervalTuple
import avail.interpreter.primitive.tuples.P_TupleAt
import avail.interpreter.primitive.tuples.P_TupleReplaceAt
import avail.interpreter.primitive.tuples.P_TupleSize
import avail.interpreter.primitive.types.P_CastIntoElse
import avail.interpreter.primitive.types.P_CreateEnumeration
import avail.interpreter.primitive.types.P_InstanceCount
import avail.interpreter.primitive.types.P_Instances
import avail.interpreter.primitive.types.P_IsInstanceOf
import avail.interpreter.primitive.types.P_IsSubtypeOf
import avail.interpreter.primitive.types.P_Type
import avail.interpreter.primitive.variables.P_GetClearing
import avail.interpreter.primitive.variables.P_GetValue
import avail.interpreter.primitive.variables.P_SetValue
import avail.optimizer.CallSiteHelper
import avail.optimizer.L2Generator
import avail.optimizer.L2GeneratorInterface
import avail.optimizer.L2Optimizer.GenerationMode.WithFixedRegisterMap
import avail.optimizer.L2ValueManifest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo

class SimpleOptimizerTest
{
	lateinit var helper: OptimizerTestHelper

	@BeforeEach
	fun setUp(testInfo: TestInfo)
	{
		helper = OptimizerTestHelper(testInfo.displayName)
		helper.defineAbstractMethods(
			"⌊_⌋" to listOf(NUMBER()) to NUMBER()
		)
		helper.definePrimitives(
			"PrivateForceOptimizationForTests:_" to
				P_PrivateForceOptimizationForTests,
			"Crash:_" to P_EmergencyExit,
			"_+_" to P_Addition,
			"_-_" to P_Subtraction,
			"_×_" to P_Multiplication,
			"_÷_" to P_Division,
			"_<<_" to P_BitShiftLeft,
			"_>>_" to P_BitShiftRight,
			"_bit∧_" to P_BitwiseAnd,
			"_bit⊕_" to P_BitwiseXor,
			"⌊_⌋" to P_DoubleFloor,
			"⌊_⌋" to P_FloatFloor,
			"_'s⁇genuine lower bound" to P_LowerBound,
			"_=_" to P_Equality,
			"_<_" to P_LessThan,
			"_≤_" to P_LessOrEqual,
			"`|_`|" to P_TupleSize,
			"`|_`|" to P_SetSize,
			"`|_`|" to P_MapSize,
			"`|_`|" to P_InstanceCount,
			"_[_]" to P_TupleAt,
			"_[_]" to P_MapAtKey,
			"_[_]" to P_ParamTypeAt,
			"_[_.._]" to P_ExtractSubtuple,
			"_[_]→_" to P_TupleReplaceAt,
			"_→tuple" to P_SetToTuple,
			"_→set" to P_TupleToSet,
			"enumeration of_" to P_CreateEnumeration,
			"_⊆_" to P_IsSubtypeOf,
			"_⊆_" to P_SetIsSubset,
			"_∈_" to P_KeyInMap,
			"_∈_" to P_ElementInSet,
			"_∈_" to P_IsInstanceOf,
			"_'s⁇type" to P_Type,
			"_'s⁇instances" to P_Instances,
			"_to_by_" to P_IntegerIntervalTuple,
			"Invoke_with tuple_" to P_InvokeWithTuple,
			"Cast|cast_into_else_" to P_CastIntoElse,
			"Exit_with_if_" to P_ExitContinuationWithResultIf,
			"Restart_with_" to P_RestartContinuationWithArguments,
			"eject_↑" to P_GetClearing,
			"↓_" to P_GetValue,
			"_`?=_" to P_SetValue,
			"current fiber" to P_CurrentFiber)
		helper.defineMethod("-_", extendedIntegers) {
			argumentTypes(extendedIntegers)
			pushLiteral(stringFrom("-_ is a stub"))
			call("Crash:_", bottom)
		}
		helper.defineMethod("_to_by_", mostGeneralTupleType) {
			argumentTypes(integers, integers, instanceType(zero))
			pushLiteral(stringFrom("_to_by_ with zero delta is a stub"))
			call("Crash:_", bottom)
		}
		helper.defineMethod("If_then_", TOP()) {
			argumentTypes(booleanType, functionType(emptyTuple, TOP()))
			primitive = P_ShortCircuitHelper
		}
		helper.defineMethod("If_then_", TOP()) {
			argumentTypes(falseType, functionType(emptyTuple, TOP()))
			pushLiteral(nil)
		}
		helper.defineMethod("Unless_then_", TOP()) {
			argumentTypes(booleanType, functionType(emptyTuple, TOP()))
			primitive = P_ShortCircuitHelper
		}
		helper.defineMethod("Unless_then_", TOP()) {
			argumentTypes(trueType, functionType(emptyTuple, TOP()))
			pushLiteral(nil)
		}
		helper.addAlias("_<_", "_②>_①")
		val nullFunctionReturningString = functionType(emptyTuple, stringType)
		helper.defineMethod("Assert:_with function_", TOP()) {
			argumentTypes(booleanType, nullFunctionReturningString)
			write(0, L1_doPushLiteral, addLiteral(nil))
		}
		helper.defineMethod("Assert:_with function_", bottom) {
			argumentTypes(falseType, nullFunctionReturningString)
			pushLiteral(stringFrom("Assertion failed"))
			call("Crash:_", bottom)
		}
		helper.defineMethod("Require:_", TOP()) {
			argumentTypes(booleanType)
			pushLiteral(nil)
		}
		helper.defineMethod("Require:_", TOP()) {
			argumentTypes(falseType)
			pushLiteral(stringFrom("Require:_ is a stub."))
			call("Crash:_", bottom)
		}
		helper.defineMethod("_`?→_†", Types.ANY()) {
			argumentTypes(Types.ANY(), anyMeta)
			L1_doPushLastLocal(1)
		}
		helper.defineMethod("If_then_else_", TOP()) {
			argumentTypes(
				booleanType,
				functionType(emptyTuple, TOP()),
				functionType(emptyTuple, TOP()))
			primitive = P_IfFalseThenElse
		}
		helper.defineMethod("If_then_else_", TOP()) {
			argumentTypes(
				trueType,
				functionType(emptyTuple, TOP()),
				functionType(emptyTuple, TOP()))
			primitive = P_IfTrueThenElse
		}
		helper.defineMethod("Do_while_", TOP()) {
			argumentTypes(
				functionType(emptyTuple, TOP()),
				functionType(emptyTuple, booleanType))
			pushLiteral(stringFrom("Do_while_ is a stub."))
			call("Crash:_", bottom)
		}
		helper.defineMethod("While_do_", TOP()) {
			argumentTypes(
				functionType(emptyTuple, booleanType),
				functionType(emptyTuple, TOP()))
			pushLiteral(stringFrom("While_do_ is a stub."))
			call("Crash:_", bottom)
		}
		helper.defineMethod("Until_do_", TOP()) {
			argumentTypes(
				functionType(emptyTuple, booleanType),
				functionType(emptyTuple, TOP()))
			pushLiteral(stringFrom("Until_do_ is a stub."))
			call("Crash:_", bottom)
		}
		helper.defineMethod("map_through_", mostGeneralTupleType) {
			argumentTypes(
				mostGeneralTupleType,
				functionType(tuple(bottom), Types.ANY()))
			pushLiteral(stringFrom("map_through_ is a stub."))
			call("Crash:_", bottom)
		}
		helper.defineMethod("_↑+=_", TOP()) {
			argumentTypes(
				fromReadAndWriteTypes(mostGeneralTupleType, bottom),
				Types.ANY())
			pushLiteral(stringFrom("\"_↑+=_\" is a stub."))
			call("Crash:_", bottom)
		}
		helper.defineMethod("⌊_⌋", extendedIntegers) {
			argumentTypes(extendedIntegers)
			primitive = P_PushArgument1
			L1_doPushLocal(1)
		}
		helper.defineMethod("⌊_⌋", extendedIntegers) {
			argumentTypes(extendedIntegersMeta)
			L1_doPushLocal(1)
			call("_'s⁇genuine lower bound", extendedIntegers)
		}
		helper.defineMethod("⌊_⌋", bottom) {
			argumentTypes(bottomMeta)
			L1_doPushLocal(1)
			call("Crash:_", bottom)
		}

		helper.defineMethod("⌊_⌋is inclusive", booleanType) {
			argumentTypes(extendedIntegersMeta)
			pushLiteral(stringFrom("\"⌊_⌋is inclusive\" is a stub."))
			call("Crash:_", bottom)
		}
		helper.defineMethod("⌈_⌉", extendedIntegers) {
			argumentTypes(extendedIntegersMeta)
			pushLiteral(stringFrom("\"⌈_⌉\" is a stub."))
			call("Crash:_", bottom)
		}
		helper.defineMethod("⌈_⌉is inclusive", booleanType) {
			argumentTypes(extendedIntegersMeta)
			pushLiteral(stringFrom("\"⌈_⌉is inclusive\" is a stub."))
			call("Crash:_", bottom)
		}
		// Note that the result type is top, to satisfy primitive function
		// construction, but calls will use the stronger type, 'boolean'.
		helper.defineMethod("_∨_", TOP()) {
			argumentTypes(
				booleanType,
				functionType(emptyTuple, booleanType))
			primitive = P_ShortCircuitHelper
		}
		helper.defineMethod("_∨_", trueType) {
			argumentTypes(
				trueType,
				functionType(emptyTuple, booleanType))
			primitive = P_PushConstant
			pushLiteral(trueObject)
		}
		helper.defineMethod("{«_‡,»}ᵀ", instanceMeta(TOP())) {
			argumentTypes(
				tupleTypeForSizesTypesDefaultType(
					wholeNumbers,
					emptyTuple,
					Types.NONTYPE()))
			val instances = declareName("instances")

			L1_doPushLastLocal(instances)
			call("_→set", mostGeneralSetType())
			call("enumeration of_", anyMeta)
		}
		helper.defineMethod(
			"attempt extract_or_then_",
			TOP(),
			suppressLookup = true)
		{
			argumentTypes(
				zeroOrOneOf(SEND_PHRASE.mostGeneralType),
				PARSE_PHRASE.mostGeneralType,
				functionType(
					tuple(SEND_PHRASE.mostGeneralType),
					TOP()))
			declareName("optionalSend")
			declareName("transformedPhrase")
			declareName("action")

			pushLiteral(nil)
		}
		helper.defineMethod("attempt extract_or_then_", TOP()) {
			argumentTypes(
				tupleTypeForTypes(SEND_PHRASE.mostGeneralType),
				PARSE_PHRASE.mostGeneralType,
				functionType(
					tuple(SEND_PHRASE.mostGeneralType),
					TOP()))
			declareName("optionalSend")
			declareName("transformedPhrase")
			declareName("action")

			pushLiteral(stringFrom("Dummy non-trivial implementation 1"))
			call("Crash:_", bottom)
		}
		helper.defineMethod("attempt extract_or_then_", TOP()) {
			argumentTypes(
				tupleTypeForTypes(),
				SEND_PHRASE.mostGeneralType,
				functionType(
					tuple(SEND_PHRASE.mostGeneralType),
					TOP()))
			declareName("optionalSend")
			declareName("transformedPhrase")
			declareName("action")

			pushLiteral(stringFrom("Dummy non-trivial implementation 2"))
			call("Crash:_", bottom)
		}
		// Necessary for shortcutting the lookup in testFunctionTypePrint_2_2.
		helper.defineMethod("_[_]", bottomMeta) {
			argumentTypes(bottomMeta, naturalNumbers)
			pushLiteral(bottom)
		}
		helper.defineMethod("“_”", stringType) {
			argumentTypes(bottomMeta)
			declareName("arg")
			pushLiteral(stringFrom("⊥"))
		}
		helper.defineMethod("“_”", stringType) {
			argumentTypes(setMeta())
			declareName("aSetType")
			pushLiteral(stringFrom("some set type"))
		}
		helper.defineMethod("“_”", stringType) {
			argumentTypes(mapMeta())
			declareName("aMapType")
			pushLiteral(stringFrom("some map type"))
		}
		helper.defineMethod("“_”", stringType) {
			argumentTypes(instanceType(emptySet))
			declareName("theEmptySet")
			pushLiteral(stringFrom("∅"))
		}
		helper.defineMethod("“_”", stringType) {
			argumentTypes(instanceType(one))
			declareName("one")
			pushLiteral(stringFrom("1"))
		}
		helper.defineMethod("“_”", stringType) {
			argumentTypes(instanceType(two))
			declareName("two")
			pushLiteral(stringFrom("2"))
		}
		helper.defineMethod("(++_↑)", NUMBER()) {
			argumentTypes(fromReadAndWriteTypes(NUMBER(), bottom))
			val varArgument = declareName("var")
			// :: var ?= eject var + 1;
			L1_doPushLocal(varArgument)
			L1_doPushLocal(varArgument)
			call("eject_↑", NUMBER())
			pushLiteral(one)
			call("_+_", NUMBER())
			call("_`?=_", NUMBER())
			L1_doPop()
			// :: ↓var
			L1_doPushLastLocal(varArgument)
			call("↓_", NUMBER())
		}


		// Force lookup by value, otherwise only the bypassForTypeLookup will
		// get warmed up.  Specifically, we want the tree to dispatch by
		// constant hash first (looking for ⊥), then in the noMatch branch using
		// the tag.
		helper.lookup("“_”").bundleMethod
			.lookupByValuesFromList(listOf(bottom), null)
		helper.lookup("“_”").bundleMethod
			.lookupByValuesFromList(listOf(mostGeneralSetType()), null)

	}

	@AfterEach
	fun tearDown()
	{
		helper.runtime.destroy()
	}

	/** Check that we can create a function that adds 3 and 4 to produce 7. */
	@Test
	fun callAdditionTest()
	{
		val three = fromInt(3)
		val four = fromInt(4)
		val seven = fromInt(7)
		val rawFunction = helper.rawFunction(instanceType(seven)) {
			pushLiteral(three)
			pushLiteral(four)
			call("_+_", returnType!!)
		}
		val result = helper.executeRawFunction(rawFunction)
		assertEquals(seven, result)

		helper.testOptimize(rawFunction)

		val result2 = helper.executeRawFunction(rawFunction)
		assertEquals(seven, result2)
	}

	/**
	 * Test optimization of a function that asserts that the argument has only
	 * one instance, then extracts it.  This is a regression test.
	 */
	@Test
	fun optimizeInstanceTest()
	{
		val errorString = stringFrom("Expected single instance type")
		val constantErrorStringRawFunction = helper.rawFunction(
			instanceType(errorString))
		{
			pushLiteral(errorString)
		}
		constantErrorStringRawFunction.methodName = stringFrom("error string")
		val rawFunction = helper.rawFunction(Types.ANY()) {
			argumentTypes(instanceMeta(Types.ANY()))
			val t = declareName("t")

			L1_doPushLocal(t)
			call("`|_`|", inclusive(zero, positiveInfinity))
			pushLiteral(fromInt(1))
			call("_=_", booleanType)
			pushLiteral(
				createFunction(constantErrorStringRawFunction, emptyTuple))
			call("Assert:_with function_", TOP())
			L1_doPop()
			L1_doPushLastLocal(t)
			call("_'s⁇instances", mostGeneralSetType())
			call("_→tuple", mostGeneralTupleType)
			pushLiteral(fromInt(1))
			call("_[_]", Types.ANY())
		}

		helper.testOptimize(rawFunction)
	}

	/**
	 * Test optimization of a function that maps a tuple's elements through a
	 * function (same pattern as "map_through_".  This is a regression test.
	 */
	@Test
	fun optimizeTupleMapThrough()
	{
		val rawFunction = helper.rawFunction(mostGeneralTupleType) {
			argumentTypes(
				mostGeneralTupleType,
				functionType(
					tuple(bottom),
					Types.ANY()))
			createConstant(wholeNumbers) // tupleSize

			val aTuple = declareName("aTuple")
			val transformer = declareName("transformer")
			val tupleSize = declareName("tupleSize")

			// :: tupleSize := |aTuple|
			L1_doPushLocal(aTuple)
			call("`|_`|", wholeNumbers)
			L1Ext_doSetLocalSlot(tupleSize)

			// :: Exit outer with <> if tupleSize = 0
			L1Ext_doPushLabel()
			pushLiteral(emptyTuple)
			L1_doPushLocal(tupleSize)
			pushLiteral(zero)
			call("_=_", booleanType)
			call("Exit_with_if_", TOP())
			L1_doPop()

			// :: [...](1, tupleSize /→ n31,  aTuple, transformer)
			pushLiteral(
				createFunction(
					helper.createDummyRawFunction(
						arguments = listOf(
							"index" to inclusive(1, Int.MAX_VALUE),
							"end" to inclusive(1, Int.MAX_VALUE),
							"accumulator" to mostGeneralTupleType,
							"innerTuple" to mostGeneralTupleType,
							"innerTransformer" to
								functionType(tuple(bottom), Types.ANY())),
						returnType = mostGeneralTupleType),
					emptyTuple))
			pushLiteral(one)
			L1_doPushLastLocal(tupleSize)
			pushLiteral(inclusive(1, Int.MAX_VALUE))
			call("_`?→_†", inclusive(1, Int.MAX_VALUE))
			pushLiteral(emptyTuple)
			L1_doPushLastLocal(aTuple)
			L1_doPushLastLocal(transformer)
			L1_doMakeTuple (5)
			call("Invoke_with tuple_", mostGeneralTupleType)
		}

		helper.testOptimize(rawFunction)
	}

	/**
	 * Test optimization of a function that maps a tuple's elements through a
	 * function (same pattern as "any of_satisfies_[#1]", in Tuples, taking a
	 * one-argument predicate).  This is a regression test.
	 */
	@Test
	fun optimizeAnyOfSatisfies_1()
	{
		val rawFunction = helper.rawFunction(mostGeneralTupleType) {
			argumentTypes(
				naturalNumbers,
				wholeNumbers,
				mostGeneralTupleType,
				functionType(
					tuple(bottom),
					booleanType))
			createConstant(booleanType) // pastEnd
			createConstant(booleanType) // pass

			val index = declareName("index")
			val end = declareName("end")
			val innerTuple = declareName("innerTuple")
			val innerPredicate = declareName("innerPredicate")
			val pastEnd = declareName("pastEnd")
			val pass = declareName("pass")

			// :: $loop : boolean;
			// :: pastEnd ::= index > end;
			L1_doPushLocal(index)
			L1_doPushLocal(end)
			L1Ext_doPermute(addLiteral(tupleFromIntegerList(listOf(2, 1))))
			call("_②>_①", booleanType)
			L1Ext_doSetLocalSlot(pastEnd)

			// :: Exit outer with false if pastEnd;
			L1Ext_doPushLabel()
			pushLiteral(falseObject)
			L1_doPushLastLocal(pastEnd)
			call("Exit_with_if_", TOP())
			L1_doPop()

			// pass ::= innerPredicate(innerTuple[index]);
			L1_doPushLocal(innerPredicate)
			L1_doPushLocal(innerTuple)
			L1_doPushLocal(index)
			call("_[_]", Types.ANY())
			L1_doMakeTuple(1)
			call("Invoke_with tuple_", booleanType)
			L1Ext_doSetLocalSlot(pass)

			// :: Exit outer with true if pass;
			L1Ext_doPushLabel()
			pushLiteral(AtomDescriptor.trueObject)
			L1_doPushLastLocal(pass)
			call("Exit_with_if_", TOP())
			L1_doPop()

			// :: Restart loop
			//      with <index + 1, end, innerTuple, innerPredicate>;
			L1Ext_doPushLabel()
			L1_doPushLastLocal(index)
			pushLiteral(one)
			call("_+_", integerRangeType(two, true, positiveInfinity, false))
			L1_doPushLastLocal(end)
			L1_doPushLastLocal(innerTuple)
			L1_doPushLastLocal(innerPredicate)
			L1_doMakeTuple(4)
			call("Restart_with_", bottom)
		}

		helper.testOptimize(rawFunction)
	}

	/**
	 * Test optimization of a function that finds one more format site for
	 * pattern substitution.  This is equivalent to the addSite block in the
	 * Format module's "format sites for_" method.  This is a regression test.
	 */
	@Test
	fun optimizeFormatSitesFor_1()
	{
		val rawFunction = helper.rawFunction(mostGeneralTupleType) {
			val (description, text, escape) =
				listOf("description", "text", "escape").map {
					createAtom(stringFrom(it), nil)
				}
			val interpolationMode = enumerationWith(
				set(description, text, escape)
			)
			val formatSiteType = tupleTypeForTypes(
				stringType,
				interpolationMode,
				naturalNumbers,
				naturalNumbers)
			argumentTypes(
				Types.CHARACTER(),
				interpolationMode)
			// Arguments
			val closeDelimiter = 1
			val interpType = 2
			// Locals
			val start = createConstant(naturalNumbers)
			val varName = createConstant(stringType)
			// Outers
			val index = createOuter(variableTypeFor(naturalNumbers))
			val template = createOuter(stringType)
			val sites =
				createOuter(variableTypeFor(zeroOrMoreOf(formatSiteType)))

			assertEquals(closeDelimiter, declareName("closeDelimiter"))
			assertEquals(interpType, declareName("interpType"))
			assertEquals(start, declareName("start"))
			assertEquals(varName, declareName("varName"))
			declareName("index")
			declareName("template")
			declareName("sites")

			// :: start ::= index;
			L1_doGetOuter(index)
			L1Ext_doSetLocalSlot(start)
			// :: Do [...index...] while [...index,template,closeDelimiter...];
			L1_doPushOuter(index)
			close(
				outers = listOf(
					"index" to variableTypeFor(naturalNumbers)),
				returnType = TOP())
			L1_doPushOuter(index)
			L1_doPushOuter(template)
			L1_doPushLastLocal(closeDelimiter)
			close(
				outers = listOf(
					"index" to naturalNumbers,
					"template" to naturalNumbers,
					"closeDelimiter" to naturalNumbers),
				returnType = booleanType)
			call("Do_while_", TOP())
			L1_doPop()

			// :: If index - start = 1 then [...]
			L1_doGetOuter(index)
			L1_doPushLocal(start)
			call("_-_", integers)
			pushLiteral(one)
			call("_=_", booleanType)
			pushLiteral(
				createFunction(
					helper.createDummyRawFunction(
						returnType = TOP()),
					emptyTuple))
			call("If_then_", TOP())
			L1_doPop()

			// :: varName ::= template[start + 1..index - 1];
			L1_doPushLastOuter(template)
			L1_doPushLocal(start)
			pushLiteral(one)
			call("_+_", integerRangeType(two, true, positiveInfinity, false))
			L1_doGetOuter(index)
			pushLiteral(one)
			call("_-_", wholeNumbers)
			call("_[_.._]", stringType)
			L1Ext_doSetLocalSlot(varName)

			// sites += <varName, interpType, start, index>;
			L1_doPushLastOuter(sites)
			L1_doPushLastLocal(varName)
			L1_doPushLastLocal(interpType)
			L1_doPushLastLocal(start)
			L1_doGetLastOuter(index)
			L1_doMakeTuple(4)
			call("_↑+=_", TOP())
		}

		helper.testOptimize(rawFunction)
	}


	/**
	 * Somehow, the union of specific TypeRestrictions
	 * Test optimization of a function that finds one more format site for
	 * pattern substitution.  This is equivalent to the addSite block in the
	 * Format module's "format sites for_" method.  This is a regression test.
	 */
	@Test
	fun divisionSemanticRestriction_3_2()
	{
		val rawFunction = helper.rawFunction(mostGeneralTupleType) {
			argumentTypes()
			// Local constants:
			val numeratorMin = createConstant(extendedIntegers)
			val lowerInclusive = createConstant(booleanType)
			val numeratorMax = createConstant(extendedIntegers)
			val upperInclusive = createConstant(booleanType)
			// Outers:
			val numeratorRangeOuter = createOuter(extendedIntegersMeta)
			val denominatorOuter =
				createOuter(inclusive(zero, positiveInfinity))

			assertEquals(declareName("numeratorMin"), numeratorMin)
			assertEquals(declareName("lowerInclusive"), lowerInclusive)
			assertEquals(declareName("numeratorMax"), numeratorMax)
			assertEquals(declareName("upperInclusive"), upperInclusive)
			declareName("numeratorRangeOuter")
			declareName("denominatorOuter")

			// :: numeratorMin ::= ⌊numeratorRange⌋ ÷ denominator;
			L1_doPushOuter(numeratorRangeOuter)
			call("⌊_⌋", extendedIntegers)
			L1_doPushOuter(denominatorOuter)
			call("_÷_", extendedIntegers)
			L1Ext_doSetLocalSlot(numeratorMin)

			// :: lowerInclusive ::= ⌊numeratorRange⌋ is inclusive;
			L1_doPushOuter(numeratorRangeOuter)
			call("⌊_⌋is inclusive", booleanType)
			L1Ext_doSetLocalSlot(lowerInclusive)

			// :: numeratorMax ::= ⌈numeratorRange⌉ ÷ denominator;
			L1_doPushOuter(numeratorRangeOuter)
			call("⌈_⌉", extendedIntegers)
			L1_doPushOuter(denominatorOuter)
			call("_÷_", extendedIntegers)
			L1Ext_doSetLocalSlot(numeratorMax)

			// :: upperInclusive ::= ⌈numeratorRange⌉ is inclusive;
			L1_doPushLastOuter(numeratorRangeOuter)
			call("⌈_⌉is inclusive", booleanType)
			L1Ext_doSetLocalSlot(upperInclusive)

			// :: If denominator < 0 then [...] else [...];
			L1_doPushLastOuter(denominatorOuter)
			pushLiteral(zero)
			call("_<_", booleanType)
			pushLiteral(
				createFunction(
					helper.createDummyRawFunction(
						emptyList(), emptyList(), TOP()),
					emptyTuple))
			pushLiteral(
				createFunction(
					helper.createDummyRawFunction(
						emptyList(), emptyList(), TOP()),
					emptyTuple))
			call("If_then_else_", TOP())
		}

		helper.testOptimize(rawFunction)
	}

	/**
	 * The "_mod_" operation (modulus) produces the remainder from a division.
	 * This regression test is for an optimizer problem in Math.avail, with
	 * "_mod_" for [number, number]→number, specifically a dead code analysis
	 * that found no origin for Int(divisor-1).
	 */
	@Test
	fun modTest()
	{
		val rawFunction = helper.rawFunction(mostGeneralTupleType) {
			argumentTypes(NUMBER(), NUMBER())
			val dividend = declareName("dividend")
			val divisor = declareName("divisor")

			L1_doPushLocal(dividend)
			L1_doPushLocal(divisor)
			L1_doPushLastLocal(dividend)
			L1_doPushLastLocal(divisor)
			call("_÷_", NUMBER())
			call("⌊_⌋", NUMBER())
			call("_×_", NUMBER())
			call("_-_", NUMBER())
		}

		helper.testOptimize(rawFunction)
	}

	/**
	 * Test optimization of a function that constructs an integer interval tuple
	 * *or* fails if the delta is zero.  This is a regression test, taken from
	 * Tuple Tests.avail, "integer interval tuple".
	 *
	 * ```
	 * [
	 *     start : [-5..5],
	 *     end : [-5..5],
	 *     delta : [-5..5]
	 * |
	 *     expected : tuple := <>;
	 * ...
	 *     [
	 *         actual ::= start to end by delta;
	 *         // "Require:_=_" introduces temps tempActual1 and tempExpected1:
	 *         Require: actual = expected;
	 *         Require: 0 ≠ delta;
	 *     ]
	 * ...
	 * ]
	 * ```
	 *
	 * The actual problem was that during code splitting, the negative, zero,
	 * and positive cases for delta were split out as part of method dispatch,
	 * due to the presence of a delta=0 method definition.  Afterward, the
	 * control flow merged from the negative and positive cases, but led to a
	 * conservative type union for delta that included zero, causing the
	 * optimizer to believe that the final "Require:_" could fail.  Along that
	 * path, the semantic value propagation logic led to an impossible
	 * constraint for delta of both zero and not zero.  That happened because
	 * the *tag* included only {[TypeTag.NATURAL_NUMBER_TAG],
	 * [TypeTag.INTEGER_TAG]}, but not [TypeTag.WHOLE_NUMBER_TAG], so it deduced
	 * that zero should be excluded.  It did this after the control flow merge,
	 * at the point where delta was once again being compared to 0, and only
	 * along the ifTrue (Equal) branch.
	 *
	 * The general fix is to exclude branches where all but one edge lead to
	 * impossible cases (potentially hiding other problems that may exist during
	 * the big 2024-2025 L2 rework).
	 *
	 * The specific fix is to ensure that either the union of the delta ranges
	 * doesn't re-introduce zero (i.e., the `[-5..-1]` and `[1..5]` should
	 * produce a restriction that continues to exclude 0), and ensuring that the
	 * fact that the associated tag information excludes
	 * [TypeTag.WHOLE_NUMBER_TAG] should caause delta's restriction to likewise
	 * exclude 0.  Both are useful improvements.
	 */
	@Test
	fun optimizeIntegerIntervalTupleTest()
	{
		val rawFunction = helper.rawFunction(TOP()) {
			val minusToPlusFive = inclusive(-5, 5)
			val upToElevenValues = tupleTypeForSizesTypesDefaultType(
				inclusive(0, 11),
				emptyTuple,
				minusToPlusFive)
			argumentTypes()
			// Arguments:
			// Local constants:
			val actual = createConstant(upToElevenValues)
			declareName("actual")
			val tempActual1 = createConstant(upToElevenValues)
			declareName("tempActual1")
			val tempExpected1 = createConstant(mostGeneralTupleType)
			declareName("tempExpected1")
			val start = createOuter(minusToPlusFive)
			declareName("start")
			val end = createOuter(minusToPlusFive)
			declareName("end")
			val delta = createOuter(minusToPlusFive)
			declareName("delta")
			val expected = createOuter(variableTypeFor(mostGeneralTupleType))
			declareName("expected")

			// :: actual ::= start to end by delta;
			L1_doPushLastOuter(start)
			L1_doPushLastOuter(end)
			L1_doPushOuter(delta)
			call("_to_by_", upToElevenValues)
			L1Ext_doSetLocalSlot(actual)

			// :: tempActual1 := actual;
			L1_doPushLastLocal(actual)
			L1Ext_doSetLocalSlot(tempActual1)
			// :: tempExpected1 := expected;
			L1_doGetLastOuter(expected)
			L1Ext_doSetLocalSlot(tempExpected1)

			// :: Unless tempActual1 = tempExpected1 then [...];
			L1_doPushLocal(tempActual1)
			L1_doPushLocal(tempExpected1)
			call("_=_", booleanType)
			L1_doPushLastLocal(tempExpected1)
			L1_doPushLastLocal(tempActual1)
			close(
				outers = listOf(
					"tempExpected1" to minusToPlusFive,
					"tempActaul1" to minusToPlusFive),
				returnType = TOP())
			call("Unless_then_", TOP())
			L1_doPop()

			// :: Require: 0 ≠ delta;
			pushLiteral(zero)
			L1_doPushLastOuter(delta)
			call("_=_", booleanType)
			pushLiteral(falseObject)
			call("_=_", booleanType)
			call("Require:_", TOP())
		}

		helper.testOptimize(rawFunction)
	}

	/**
	 * This is a regression test for the Math Tests.avail, "bit∧" test, reduced
	 * to a smaller form that still exhibited the problem.
	 *
	 * It performs bitwise AND operations on powers of two, both positive and
	 * negative, to verify expected identities.
	 *
	 * The optimizer previously had trouble with this due to a pair of postponed
	 * [L2_BOX_INT] instructions that target two distinct semantic values that
	 * were already in the same synonym (because they were tested for equality
	 * earlier).  The fix was to make [L2Generator.forceTranslationForRead] more
	 * robust about existing synonyms and other postponed instructions that
	 * would populate them.
	 */
	@Test
	fun bitAndTestReduced()
	{
		val rawFunction = helper.rawFunction(TOP()) {
			// Arguments:
			val maxScale = 40
			argumentTypes(inclusive(1, maxScale))
			val i = declareName("i")

			// Local constants:
			val twoToMax = one.bitShift(fromInt(maxScale), false).makeShared()
			val twoToMaxM1 =
				twoToMax.noFailMinusCanDestroy(one, false).makeShared()
			val negTwoToMax =
				zero.noFailMinusCanDestroy(twoToMax, false).makeShared()
			val negTwo = fromInt(-2)

			val power = createConstant(inclusive(two, twoToMax))
			declareName("power")
			val tempActual4 = createConstant(inclusive(zero, twoToMaxM1))
			declareName("tempActual4")
			val tempExpected4 = createConstant(inclusive(one, twoToMaxM1))
			declareName("tempExpected4")
			val neg = createConstant(inclusive(negTwoToMax, negTwo))
			declareName("neg")
			val tempActual9 = createConstant(inclusive(zero, twoToMax))
			declareName("tempActual9")
			val tempExpected9 = createConstant(inclusive(two, twoToMax))
			declareName("tempExpected9")

			// ::  power ::= 1 << i;
			pushLiteral(one)
			L1_doPushLastLocal(i)
			call("_<<_", inclusive(two, twoToMax))
			L1Ext_doSetLocalSlot(power)

			// ::  tempActual4 ::= (power - 1) bit∧ (power - 1);
			L1_doPushLocal(power)
			pushLiteral(one)
			call("_-_", inclusive(one, twoToMaxM1))
			L1_doPushLocal(power)
			pushLiteral(one)
			call("_-_", inclusive(one, twoToMaxM1))
			call("_bit∧_", inclusive(zero, twoToMaxM1))
			L1Ext_doSetLocalSlot(tempActual4)
			// ::  tempExpected4 ::= power - 1;
			L1_doPushLocal(power)
			pushLiteral(one)
			call("_-_", inclusive(one, twoToMaxM1))
			L1Ext_doSetLocalSlot(tempExpected4)
			// ::  Unless tempActual4 = tempExpected4 then [ ... ]
			L1_doPushLocal(tempActual4)
			L1_doPushLocal(tempExpected4)
			call("_=_", booleanType)
			L1_doPushLastLocal(tempExpected4)
			L1_doPushLastLocal(tempActual4)
			close(
				outers = listOf(
					"tempExpected4" to inclusive(one, twoToMaxM1),
					"tempOctual4" to inclusive(zero, twoToMaxM1)),
				returnType = bottom)
			call("Unless_then_", TOP())
			L1_doPop()

			// ::  neg ::= -power;
			L1_doPushLocal(power)
			call("-_", inclusive(negTwoToMax, negTwo))
			L1Ext_doSetLocalSlot(neg)

			// ::  tempActual9 ::= neg bit ∧ power;
			L1_doPushLastLocal(neg)
			L1_doPushLocal(power)
			call("_bit∧_", inclusive(zero, twoToMax))
			L1Ext_doSetLocalSlot(tempActual9)
			// ::  tempExpeected9 ::= power;
			L1_doPushLastLocal(power)
			L1Ext_doSetLocalSlot(tempExpected9)
			// ::  Unless tempActual9 = tempExpected9 then [ ... ];
			L1_doPushLocal(tempActual9)
			L1_doPushLocal(tempExpected9)
			call("_=_", booleanType)
			L1_doPushLastLocal(tempExpected9)
			L1_doPushLastLocal(tempActual9)
			close(
				outers = listOf(
					"tempExpected9" to inclusive(two, twoToMax),
					"tempActual9" to inclusive(zero, twoToMax)),
				returnType = bottom)
			call("Unless_then_", TOP())
		}

		helper.testOptimize(rawFunction)
	}

	/**
	 *
	 * This is a regression test for the Multiplication Tests.avail, an inner
	 * function of the "Multiplication ok" test.
	 *
	 * An outer `i` has been captured, and `j` is an argument, each in the range
	 * `[1..10]`.  The function checks `Require: i × j = j × i;`.
	 */
	@Test
	fun multiplicationCommutativityTest()
	{
		val rawFunction = helper.rawFunction(TOP()) {
			val inputRange = inclusive(1, 10)
			val productRange = inclusive(1, 100)

			// Arguments:
			argumentTypes(inputRange)
			val argJ = declareName("j")

			// Local constants:
			val tempActual = createConstant(productRange)
			declareName("tempActual")
			val tempExpected = createConstant(productRange)
			declareName("tempExpected")

			// Outers:
			val outerI = createOuter(inputRange)
			declareName("i")

			L1_doPushOuter(outerI)
			L1_doPushLocal(argJ)
			call("_×_", productRange)
			L1Ext_doSetLocalSlot(tempActual)
			L1_doPushLastLocal(argJ)
			L1_doPushLastOuter(outerI)
			call("_×_", productRange)
			L1Ext_doSetLocalSlot(tempExpected)
			L1_doPushLocal(tempActual)
			L1_doPushLocal(tempExpected)
			call("_=_", booleanType)
			L1_doPushLastLocal(tempExpected)
			L1_doPushLastLocal(tempActual)
			close(
				outers = listOf(
					"tempExpected" to productRange,
					"tempActual" to productRange),
				returnType = bottom)
			call("Unless_then_", TOP())
		}

		helper.testOptimize(rawFunction)
	}

	/**
	 * Regression test for the statement:
	 *   `Require: {1,2,"foo"}ᵀ ⊆ {1,2,3,4,"foo","bar"}ᵀ;`
	 *
	 * At the original site in the test `type algebra` of the `type test suite`,
	 * this line failed during initial code generaation while generating the
	 * polymorphic call to `"_⊆_"`.
	 */
	@Test
	fun isolatedLineFromTypeAlgebraTest()
	{
		val rawFunction = helper.rawFunction(booleanType) {
			pushLiteral(fromInt(1))
			pushLiteral(fromInt(2))
			pushLiteral(stringFrom("foo"))
			L1_doMakeTuple(3)
			call(
				"{«_‡,»}ᵀ",
				instanceMeta(
					enumerationWith(
						setFromCollection(
							listOf(
								fromInt(2),
								fromInt(1),
								stringFrom("foo"))))))
			pushLiteral(fromInt(1))
			pushLiteral(fromInt(2))
			pushLiteral(fromInt(3))
			pushLiteral(fromInt(4))
			pushLiteral(stringFrom("foo"))
			pushLiteral(stringFrom("bar"))
			L1_doMakeTuple(6)
			call(
				"{«_‡,»}ᵀ",
				instanceMeta(
					enumerationWith(
						setFromCollection(
							listOf(
								fromInt(2),
								fromInt(1),
								fromInt(3),
								fromInt(4),
								stringFrom("foo"),
								stringFrom("bar"))))))
			call("_⊆_", booleanType)
		}

		helper.testOptimize(rawFunction)
	}

	/**
	 * Taken from the test `"two intercepts (value-producing)"`.  If we push the
	 * empty tuple, push 1, then call ```"_[_]"```, it works, but if the tuple
	 * is stored in a local variable first, the discovery of the stronger type
	 * (`<>'s type`) is discovered during a postponement phase, while also
	 * postponing creation of the local, still unescaped, variable.
	 */
	@Test
	fun testTupleAtAlwaysFails()
	{
		val rawFunction = helper.rawFunction(Types.ANY()) {
			createLocal(variableTypeFor(mostGeneralTupleType))
			val t = declareName("t")

			pushLiteral(emptyTuple)
			L1_doSetLocal(t)
			L1_doGetLocalClearing(t)
			pushLiteral(fromInt(1))
			call("_[_]", Types.ANY())
		}

		helper.testOptimize(rawFunction)
	}

	/**
	 * Taken from the semantic restriction of "_bit∧_"#1 in Math.avail.
	 *
	 * TODO Verify problem after resolution.
	 *
	 * During some rework, the synonym building mechanism during postponement
	 * was failing to emit a move to populate the destination semantic values,
	 * so there were no definitions that included those semantic values.
	 */
	@Test
	fun testBitAndSemanticRestriction()
	{
		val nonemptySetOfExtendedInteger =
			setTypeForSizesContentType(naturalNumbers, extendedIntegers)
		val setOfOneOrTmoExtendedIntegers =
			setTypeForSizesContentType(inclusive(1, 2), extendedIntegers)
		val rawFunction = helper.rawFunction(nonemptySetOfExtendedInteger) {
			argumentTypes(extendedIntegers, extendedIntegers)
			val x = declareName("x")
			val y = declareName("y")

			// :: x
			L1_doPushLocal(x)
			// :: [ xi : integer | ... y ... x ... ]
			L1_doPushLocal(y)
			L1_doPushLocal(x)
			close(
				arguments = listOf("xi" to integers),
				outers = listOf(
					"y" to extendedIntegers,
					"x" to extendedIntegers),
				returnType = setOfOneOrTmoExtendedIntegers)
			// [ ... y ... x ... ]
			L1_doPushLastLocal(y)
			L1_doPushLastLocal(x)
			close(
				outers = listOf(
					"y" to extendedIntegers,
					"x" to extendedIntegers),
				returnType = setOfOneOrTmoExtendedIntegers)
			// :: cast x into [xi : integer | ...] else [...]
			call("Cast|cast_into_else_", setOfOneOrTmoExtendedIntegers)
		}

		helper.testOptimize(rawFunction)
	}

	/**
	 * Regression test for conditionalStyler in Very Early Definers.avail.
	 *
	 * The problem was simply that the fallback lookup code
	 * (see [CallSiteHelper.JunctionType.FallBackToSlowLookup]) was calling
	 * [L2ValueManifest]`.readBoxed()`, which has since been removed to avoid
	 * confusion, instead of [L2GeneratorInterface.readBoxed].
	 */
	@Test
	fun testConditionalStyler()
	{
		val sendType = SEND_PHRASE.mostGeneralType
		val optionalSendType = zeroOrOneOf(sendType)

		val rawFunction = helper.rawFunction(TOP()) {
			argumentTypes(optionalSendType, PARSE_PHRASE.mostGeneralType)
			val optionalOriginal = declareName("optionalOriginal")
			val transformed = declareName("transformed")

			// :: attempt extract optionalOriginal
			//    or transformed
			//    then [original : send phrase | ...]
			L1_doPushLastLocal(optionalOriginal)
			L1_doPushLastLocal(transformed)
			pushLiteral(
				createFunction(
					helper.createDummyRawFunction(
						arguments = listOf(
							"original" to sendType),
						returnType = TOP()),
					emptyTuple))
			call("attempt extract_or_then_", TOP())
		}

		helper.testOptimize(rawFunction)
	}

	/**
	 * Regression test for code generated for calls to [P_ExtractSubtuple],
	 * originally encountered in the IPv4 address lexer, extracting the numeric
	 * string for the second octet.
	 *
	 * The encountered problem only showed up during the second round of code
	 * splitting, where a split path along which the start of the subrange was
	 * beyond i32, but the fallback instruction had a restriction
	 *
	 * The problem was simply that the fallback lookup code
	 * (see [CallSiteHelper.JunctionType.FallBackToSlowLookup]) was calling
	 * [L2ValueManifest]`.readBoxed()`, which has since been removed to avoid
	 * confusion, instead of [L2GeneratorInterface.readBoxed].
	 */
	@Test
	fun testTupleSubrange()
	{
		val rawFunction = helper.rawFunction(mostGeneralSetType()) {
			argumentTypes(mostGeneralTupleType, naturalNumbers, naturalNumbers)
			val source = declareName("source")
			val firstOctetPosition = declareName("firstOctetPosition")
			val line = declareName("line")

			val iVariableType = variableTypeFor(naturalNumbers)
			val twoOrMore = integerRangeType(two, true, positiveInfinity, false)


			createLocal(iVariableType)
			val i = declareName("i")
			createConstant(wholeNumbers)
			val size = declareName("size")
			createConstant(stringType)
			val firstOctetText = declareName("firstOctetText")
			createConstant(naturalNumbers)
			val secondOctetPosition = declareName("secondOctetPosition")
			createConstant(stringType)
			val secondOctetText = declareName("secondOctetText")
			createConstant(naturalNumbers)
			val thirdOctetPosition = declareName("thirdOctetPosition")
			createConstant(stringType)
			val thirdOctetText = declareName("thirdOctetText")
			createConstant(naturalNumbers)
			val fourthOctetPosition = declareName("fourthOctetPosition")
			createConstant(stringType)
			val fourthOctetText = declareName("fourthOctetText")

			// Label
			/*val body =*/ declareName("body")

			// :: $body : {token+|};
			// :: i : natural number := firstOctetPosition;
			L1_doPushLocal(firstOctetPosition)
			L1_doSetLocal(i)

			// :: size ::= |source|;
			L1_doPushLocal(source)
			call("`|_`|", wholeNumbers)
			L1Ext_doSetLocalSlot(size)

			// :: While i ≤ size ∧ source[i] is an Arabic numeral do [i++;];
			L1_doPushLocal(i)
			L1_doPushLocal(size)
			L1_doPushLocal(source)
			close(
				outers = listOf(
					"i" to iVariableType,
					"size" to wholeNumbers,
					"source" to stringType),
				returnType = booleanType)

			L1_doPushLocal(i)
			close(
				outers = listOf(
					"i" to iVariableType),
				returnType = TOP())
			call("While_do_", TOP())
			L1_doPop()

			// :: Exit body with ∅ if i > size ∨ source[i] ≠ ¢.;
			L1Ext_doPushLabel()
			pushLiteral(emptySet)
			L1_doGetLocal(i)
			L1_doPushLocal(size)
			L1Ext_doPermute(addLiteral(tupleFromIntegerList(listOf(2, 1))))
			call("_②>_①", booleanType)
			L1_doPushLocal(source)
			L1_doPushLocal(i)
			close(
				outers = listOf(
					"source" to stringType,
					"i" to iVariableType),
				returnType = booleanType)
			call("_∨_", booleanType)
			call("Exit_with_if_", TOP())
			L1_doPop()

			// :: firstOctetText ::= source[firstOctetPosition..i-1];
			L1_doPushLocal(source)
			L1_doPushLocal(firstOctetPosition)
			L1_doGetLocal(i)
			pushLiteral(one)
			call("_-_", wholeNumbers)
			call("_[_.._]", stringType)
			L1Ext_doSetLocalSlot(firstOctetText)

			// :: secondOctetPosition ::= i + 1;
			L1_doGetLocal(i)
			pushLiteral(one)
			call("_+_", twoOrMore)
			L1Ext_doSetLocalSlot(secondOctetPosition)

			// :: Do [i++;] while i ≤ size ∧ source[i] is an Arabic numeral;
			L1_doPushLocal(i)
			close(
				outers = listOf(
					"i" to iVariableType),
				returnType = TOP())
			L1_doPushLocal(i)
			L1_doPushLocal(size)
			L1_doPushLocal(source)
			close(
				outers = listOf(
					"i" to iVariableType,
					"size" to wholeNumbers,
					"source" to stringType),
				returnType = booleanType)
			call("Do_while_", TOP())
			L1_doPop()

			// :: Exit body with ∅ if i > size ∨ source[i] ≠ ¢.;
			L1Ext_doPushLabel()
			pushLiteral(emptySet)
			L1_doGetLocal(i)
			L1_doPushLocal(size)
			L1Ext_doPermute(addLiteral(tupleFromIntegerList(listOf(2, 1))))
			call("_②>_①", booleanType)
			L1_doPushLocal(source)
			L1_doPushLocal(i)
			close(
				outers = listOf(
					"source" to stringType,
					"i" to iVariableType),
				returnType = booleanType)
			call("_∨_", booleanType)
			call("Exit_with_if_", TOP())
			L1_doPop()

			// :: secondOctetText ::= source[secondOctetPosition..i-1];
			L1_doPushLocal(source)
			L1_doPushLastLocal(secondOctetPosition)
			L1_doGetLocal(i)
			pushLiteral(one)
			call("_-_", wholeNumbers)
			call("_[_.._]", stringType)
			L1Ext_doSetLocalSlot(secondOctetText)

			// :: thirdOctetPosition ::= i + 1;
			L1_doGetLocal(i)
			pushLiteral(one)
			call("_+_", twoOrMore)
			L1Ext_doSetLocalSlot(thirdOctetPosition)

			// :: Do [i++;] while i ≤ size ∧ source[i] is an Arabic numeral;
			L1_doPushLocal(i)
			close(
				outers = listOf(
					"i" to iVariableType),
				returnType = TOP())
			L1_doPushLocal(i)
			L1_doPushLocal(size)
			L1_doPushLocal(source)
			close(
				outers = listOf(
					"i" to iVariableType,
					"size" to wholeNumbers,
					"source" to stringType),
				returnType = booleanType)
			call("Do_while_", TOP())
			L1_doPop()

			// :: Exit body with ∅ if i > size ∨ source[i] ≠ ¢.;
			L1Ext_doPushLabel()
			pushLiteral(emptySet)
			L1_doGetLocal(i)
			L1_doPushLocal(size)
			L1Ext_doPermute(addLiteral(tupleFromIntegerList(listOf(2, 1))))
			call("_②>_①", booleanType)
			L1_doPushLocal(source)
			L1_doPushLocal(i)
			close(
				outers = listOf(
					"source" to stringType,
					"i" to iVariableType),
				returnType = booleanType)
			call("_∨_", booleanType)
			call("Exit_with_if_", TOP())
			L1_doPop()

			// :: thirdOctetText ::= source[thirdOctetPosition..i-1];
			L1_doPushLocal(source)
			L1_doPushLastLocal(thirdOctetPosition)
			L1_doGetLocal(i)
			pushLiteral(one)
			call("_-_", wholeNumbers)
			call("_[_.._]", stringType)
			L1Ext_doSetLocalSlot(thirdOctetText)

			// :: fourthOctetPosition ::= i + 1;
			L1_doGetLocal(i)
			pushLiteral(one)
			call("_+_", twoOrMore)
			L1Ext_doSetLocalSlot(fourthOctetPosition)

			// :: Do [i++;] while i ≤ size ∧ source[i] is an Arabic numeral;
			L1_doPushLocal(i)
			close(
				outers = listOf(
					"i" to iVariableType),
				returnType = TOP())
			L1_doPushLocal(i)
			L1_doPushLocal(size)
			L1_doPushLocal(source)
			close(
				outers = listOf(
					"i" to iVariableType,
					"size" to wholeNumbers,
					"source" to stringType),
				returnType = booleanType)
			call("Do_while_", TOP())
			L1_doPop()

			// :: If i = fourthOctetPosition then [Reject parse...];
			L1_doGetLocal(i)
			L1_doPushLocal(fourthOctetPosition)
			call("_=_", booleanType)
			pushLiteral(
				createFunction(
					helper.createDummyRawFunction(returnType = bottom),
					emptyTuple))
			call("If_then_", TOP())
			L1_doPop()

			// :: fourthOctetText ::= source[fourthOctetPosition..i-1];
			L1_doPushLocal(source)
			L1_doPushLastLocal(fourthOctetPosition)
			L1_doGetLocal(i)
			pushLiteral(one)
			call("_-_", wholeNumbers)
			call("_[_.._]", stringType)
			L1Ext_doSetLocalSlot(fourthOctetText)


			// :: cast
			// :: 	map each x of
			// :: 		<
			// :: 			firstOctetText,
			// :: 			secondOctetText,
			// :: 			thirdOctetText,
			// :: 			fourthOctetText
			// :: 		>
			// :: 	through [x (base 10)]
			// :: into
			// :: [
			// :: 	octets : <byte…|4>
			// :: |
			// :: 	{...}
			// :: ]
			// :: else
			// :: [ Reject parse... ]
			L1_doPushLastLocal(firstOctetText)
			L1_doPushLastLocal(secondOctetText)
			L1_doPushLastLocal(thirdOctetText)
			L1_doPushLastLocal(fourthOctetText)
			L1_doMakeTuple(4)
			pushLiteral(
				createFunction(
					helper.createDummyRawFunction(
						arguments = listOf(
							"x" to stringType),
						returnType = u8),
					emptyTuple))
			call("map_through_", tupleTypeForTypes(u8, u8, u8, u8))

			L1_doPushLastLocal(source)
			L1_doPushLastLocal(firstOctetPosition)
			L1_doPushLastLocal(i)
			L1_doPushLastLocal(line)
			close(
				arguments = listOf(
					"octets" to tupleTypeForTypes(u8, u8, u8, u8)),
				outers = listOf(
					"source" to stringType,
					"firstOctetPosition" to naturalNumbers,
					"i" to iVariableType,
					"line" to naturalNumbers),
				returnType = mostGeneralSetType())
			pushLiteral(
				createFunction(
					helper.createDummyRawFunction(returnType = bottom),
					emptyTuple))
			call("Cast|cast_into_else_", mostGeneralSetType())
		}

		helper.testOptimize(rawFunction)
	}

	/**
	 * Regression test for the inner block of "Cast|cast_into«_‡,»«else_»" (from
	 * Casts.avail), which failed with an NPE due to [P_ParamTypeAt] not
	 * accounting correctly for postponed instructions producing the function
	 * type.  This appeared when the postponement mechanism was converted from
	 * being a special pass to being allowed whenever semantic values are used
	 * to tie together writes and reads (versus just registers in later phases).
	 */
	@Test
	fun testCastIntoElseInnerBlock()
	{
		val twoOrMore = integerRangeType(two, true, positiveInfinity, false)
		val elseType = zeroOrOneOf(functionType(emptyTuple, TOP()))
		val resultType = functionType(tuple(bottom), TOP())
		val caseEntryType = functionType(tuple(bottom), TOP())

		val rawFunction = helper.rawFunction(resultType) {
			argumentTypes(naturalNumbers)
			val index = declareName("index")

			// Local constants
			createConstant(booleanType)
			val pastEnd = declareName("pastEnd")
			createConstant(booleanType)
			val typeMatches = declareName("typeMatches")

			// Label
			/*val body =*/ declareName("body")

			// Outers
			val outerCaseTuple = createOuter(
				tupleTypeForSizesTypesDefaultType(
					twoOrMore, emptyTuple, caseEntryType))
			declareName("caseTuple")
			val outerElse = createOuter(elseType)
			declareName("else")
			val outerValue = createOuter(Types.ANY())
			declareName("value")

			// :: $body : [⊥]→⊤;
			// :: pastEnd ::= |caseTuple| < index;
			L1_doPushOuter(outerCaseTuple)
			call("`|_`|", twoOrMore)
			L1_doPushLocal(index)
			call("_<_", booleanType)
			L1Ext_doSetLocalSlot(pastEnd)

			// :: Exit body with [v : any | ...] if pastEnd;
			L1Ext_doPushLabel()
			L1_doPushOuter(outerElse)
			close(
				arguments = listOf("v" to Types.ANY()),
				outers = listOf("outerElse" to elseType),
				returnType = resultType)
			L1_doPushLastLocal(pastEnd)
			call("Exit_with_if_",TOP())
			L1_doPop()

			// :: typeMatches ::= value ∈ caseTuple[index]'s type[1];
			L1_doPushOuter(outerValue)
			L1_doPushOuter(outerCaseTuple)
			L1_doPushLocal(index)
			call("_[_]", caseEntryType)
			call("_'s⁇type", instanceMeta(caseEntryType))
			pushLiteral(one)
			call("_[_]", anyMeta)
			call("_∈_", booleanType)
			L1Ext_doSetLocalSlot(typeMatches)

			// :: Exit body with caseTuple[index] if typeMatches;
			L1Ext_doPushLabel()
			L1_doPushOuter(outerCaseTuple)
			L1_doPushLocal(index)
			call("_[_]", caseEntryType)
			L1_doPushLastLocal(typeMatches)
			call("Exit_with_if_", TOP())
			L1_doPop()

			// :: Restart body with <index + 1>
			L1Ext_doPushLabel()
			L1_doPushLastLocal(index)
			pushLiteral(one)
			call("_+_", naturalNumbers)
			L1_doMakeTuple(1)
			call("Restart_with_", bottom)
		}

		helper.testOptimize(rawFunction)
	}

	/**
	 * Regression test for the macro expansion of the "≤" chain in the first
	 * line of the repeated block of "next integer within range", in the
	 * "pRNG test suite (pseudo-random number generation).
	 *
	 * The first and third input arguments are constant-valued (1 and 6,
	 * respectively), which led to reads of them being replaced with constants.
	 * This caused the registers to have infinitesimal lifetimes, and not be
	 * considered to interfere with each other. They were thus mapped to the
	 * same garbage register.  But the combined definition list then had a
	 * restriction to 1 and a subsequent (intersected) restriction to 6, which
	 * led to an impossible restriction (⊥), which propagated backward to make
	 * the whole graph raise an unconditional impossible restriction exception.
	 *
	 * The resolution *wasn't* to update the coloring algorithm to add an
	 * interference edge between garbage outpet registers of an instruction,
	 * although that probably would have worked.  Instead, when the generator is
	 * [WithFixedRegisterMap], it avoids generating [L2_IMPOSSIBLE_CODE] when a
	 * bottom-restricted synonym is detected.
	 */
	@Test
	fun testLessOrEqualChainInNextIntegerWithinRange()
	{
		val onesType = instanceType(one)
		val oneToSix = inclusive(1, 6)
		val sixesType = instanceType(fromInt(6))

		val rawFunction = helper.rawFunction(booleanType) {
			argumentTypes(onesType, oneToSix, sixesType)
			val arg1 = declareName("arg1")
			val arg2 = declareName("arg2")
			val arg3 = declareName("arg3")

			// Label
			/*val exit =*/ declareName("exit")

			// :: Exit exit with false if ¬(arg1 ≤ arg2);
			L1Ext_doPushLabel()
			pushLiteral(falseObject)
			L1_doPushLocal(arg1)
			L1_doPushLocal(arg2)
			call("_≤_", booleanType)
			pushLiteral(falseObject)
			call("_=_", booleanType)
			call("Exit_with_if_", TOP())
			L1_doPop()

			// :: Exit exit with false if ¬(arg2 ≤ arg3);
			L1Ext_doPushLabel()
			pushLiteral(falseObject)
			L1_doPushLastLocal(arg2)
			L1_doPushLastLocal(arg3)
			call("_≤_", booleanType)
			pushLiteral(falseObject)
			call("_=_", booleanType)
			call("Exit_with_if_", TOP())
			L1_doPop()

			// :: true
			pushLiteral(trueObject)
		}

		helper.testOptimize(rawFunction)
		assert(rawFunction.startingChunk.instructions
			.none { it is L2_IMPOSSIBLE_CODE })
	}

	/**
	 * Synchronization's "Signal_" has a block that handles a non-empty queue.
	 * When it
	 * invokes `queue[2..]`, a macro that resolves to `queue[2..|queue|]`, the
	 * primitive [P_ExtractSubtuple] attempts to perform range checks before
	 * reaching, on the happy path, an [L2_TUPLE_SUBRANGE_NO_FAIL].  During the
	 * check, it performs a subtraction `2 - 1`, which produces 1, which was
	 * not being placed in the same synonym as the 1 that was input to the
	 * subtraction.
	 *
	 * This was fixed generally at the point where a postponable instruction is
	 * added, detecting production of a constant value and ensuring that the
	 * semantic constant gets included and agglomerated with any existing
	 * synonym containing an equal semantic constant, otherwise postponing a
	 * constant move in place of the original instruction.
	 *
	 * NOTE: This test exercises object type creation code, and should be kept
	 * as an example usage of it until some other unit test uses it.  At that
	 * point this test should be removed, since it has already had its problem
	 * distilled into the far more succinct [testSubtupleFrom2].
	 */
	@Test
	fun testSignalBlock3()
	{
		// Create the atoms and object types used in the code.
		val synchronizationDeviceType =
			helper.objectType("synchronization device", true) { }
		val parkingQueueType = zeroOrMoreOf(mostGeneralFiberType())
		val nonreentrantMutexType =
			helper.objectType(
				"nonreentrant mutex",
				explicit = true,
				supertype = synchronizationDeviceType
			) {
				"mutex name"(nonemptyStringType)
				"owner"(variableTypeFor(mostGeneralFiberType()))
				"parked fibers"(variableTypeFor(parkingQueueType))
			}
		val reentrantMutexType = helper.objectType(
			"reentrant mutex",
			explicit = true,
			supertype = nonreentrantMutexType
		) { }
		val conditionType = helper.objectType(
			"condition",
			explicit = true
		) {
			"originating monitor"(reentrantMutexType)
			"condition predicate"(functionType(emptyTuple, booleanType))
			"parked fibers"(parkingQueueType)
		}
		val monitorType = helper.objectType(
			"monitor",
			explicit = false,
			reentrantMutexType
		) {
			"signaler fibers"(variableTypeFor(parkingQueueType))
		}

		val rawFunction = helper.rawFunction(TOP()) {
			argumentTypes()

			// Local constants
			val mutex = createConstant(monitorType)
			declareName("mutex")
			val head = createConstant(mostGeneralFiberType())
			declareName("head")
			val tupleTemp = createConstant(parkingQueueType)
			declareName("tupleTemp")

			// Outers
			val aCondition = createOuter(conditionType)
			declareName("aCondition")
			val queue = createOuter(variableTypeFor(parkingQueueType))
			declareName("queue")

			// :: mutex ::= aCondition's originating monitor;
			L1_doPushLastOuter(aCondition)
			callNewDummy(
				"_'s⁇originating monitor",
				listOf(conditionType),
				monitorType)
			L1Ext_doSetLocalSlot(mutex)

			// :: Ignore: priority enqueue current fiber on
			// ::     ↑mutex's signaler fibers;
			call("current fiber", mostGeneralFiberType())
			L1_doPushLocal(mutex)
			callNewDummy(
				"`↑_'s⁇signaler fibers",
				listOf(monitorType),
				variableTypeFor(parkingQueueType))
			callNewDummy(
				"priority enqueue_on_",
				listOf(
					mostGeneralFiberType(),
					variableTypeFor(parkingQueueType)),
				booleanType)
			callNewDummy(
				"Ignore:_",
				listOf(Types.ANY()),
				TOP())
			L1_doPop()

			// :: head ::= ↓queue[1];
			L1_doPushOuter(queue)
			call("↓_", parkingQueueType)
			pushLiteral(one)
			call("_[_]", mostGeneralFiberType())
			L1Ext_doSetLocalSlot(head)

			// :: queue ?= ↓queue[2..];
			L1_doPushOuter(queue)
			L1_doPushLastOuter(queue)
			call("↓_", parkingQueueType)
			L1Ext_doSetLocalSlot(tupleTemp)
			L1_doPushLocal(tupleTemp)
			pushLiteral(two)
			L1_doPushLastLocal(tupleTemp)
			call("`|_`|", wholeNumbers)
			call("_[_.._]", parkingQueueType)
			call("_`?=_", TOP())
			L1_doPop()

			// :: Ignore: priority enqueue head on ↑mutex's parked fibers;
			L1_doPushLocal(head)
			L1_doPushLocal(mutex)
			callNewDummy(
				"`↑_'s⁇parked fibers",
				listOf(monitorType),
				variableTypeFor(parkingQueueType))
			call("priority enqueue_on_", booleanType)
			call("Ignore:_", TOP())
			L1_doPop()

			// :: mutex's owner := head;
			L1_doPushLocal(mutex)
			L1_doPushLocal(head)
			callNewDummy(
				"_'s⁇owner:=_",
				listOf(monitorType, mostGeneralFiberType()),
				TOP())
			L1_doPop()

			// :: Unpark head;
			L1_doPushLastLocal(head)
			callNewDummy(
				"Unpark_",
				listOf(mostGeneralFiberType()),
				TOP())
			L1_doPop()

			// :: Until mutex's owner or zero = current fiber do
			// ::    [Park current fiber, then honor a termination request;];
			L1_doPushLastLocal(mutex)
			close(
				arguments = emptyList(),
				outers = listOf("mutex" to variableTypeFor(reentrantMutexType)),
				returnType = booleanType)
			pushLiteral(
				createFunction(
					helper.createDummyRawFunction(
						emptyList(), emptyList(), TOP()),
					emptyTuple))
			call("Until_do_", TOP())
		}

		helper.testOptimize(rawFunction)
	}

	/**
	 * Compute `aTuple[2..|aTuple|]`, which was failing during range checks that
	 * first computed the low index minus one, which in this case, equaled one,
	 * which conflicted with the existing synonym for the constant one being
	 * subtracted.  Two distinct synonyms are not allowed to be constrained to
	 * the same constant without being merged.
	 *
	 * This is a reduced test for the 3rd block of "Signal_" in Synchronization.
	 *
	 * This was fixed generally at the point where a postponable instruction is
	 * added, detecting production of a constant value and ensuring that the
	 * semantic constant gets included and agglomerated with any existing
	 * synonym containing an equal semantic constant, otherwise postponing a
	 * constant move in place of the original instruction.
	 */
	@Test
	fun testSubtupleFrom2()
	{
		val rawFunction = helper.rawFunction(mostGeneralTupleType) {
			argumentTypes(mostGeneralTupleType)
			val aTuple = declareName("aTuple")

			// :: aTuple[2..|aTuple|]
			L1_doPushLocal(aTuple)
			pushLiteral(two)
			L1_doPushLastLocal(aTuple)
			call("`|_`|", wholeNumbers)
			call("_[_.._]", mostGeneralTupleType)
		}

		helper.testOptimize(rawFunction)
	}

	/**
	 * This was taken from the implementation of `"_^_"` for *integer range
	 * types*, to produce a bounding range for a semantic restriction at a call
	 * site of `"_^_"` applied to *integers*.  In particular, this is five
	 * block levels deep inside that method, where an interesting base and an
	 * interesting power produce a value that's either inside or just outside
	 * the range being produced.
	 *
	 * The problem was related to local variable elision.
	 */
	@Test
	fun testRangePower_1_1_1_1_1()
	{
		var extendedWholeNumbers = inclusive(zero, positiveInfinity)
		val rawFunction = helper.rawFunction(TOP()) {
			argumentTypes()

			// Locals
			val value = createLocal(variableTypeFor(extendedIntegers))
			declareName("value")

			// Outers
			val interestingBaseOuter = createOuter(extendedIntegers)
			declareName("interestingBase")
			val interestingPowerOuter = createOuter(extendedWholeNumbers)
			declareName("interestingPower")
			val baseInclusiveOuter = createOuter(booleanType)
			declareName("baseInclusive")
			val powerOuter = createOuter(instanceMeta(wholeNumbers))
			declareName("power")
			val rangeOuter =
				createOuter(variableTypeFor(instanceMeta(extendedIntegers)))
			declareName("range")
			val openLimitsOuter =
				createOuter(variableTypeFor(instanceMeta(extendedIntegers)))
			declareName("openLimits")

			// :: value : extended integer :=
			//       interestingBase ^ interestingPower;
			L1_doPushLastOuter(interestingBaseOuter)
			L1_doPushOuter(interestingPowerOuter)
			callNewDummy(
				"_^_",
				listOf(extendedIntegers, extendedIntegers),
				extendedIntegers)
			L1_doSetLocal(value)

			// :: If baseInclusive ∧ interestingPower ∈ power
			//    then [...range...value...]
			//    else [...openLimits...value...];
			L1_doPushLastOuter(baseInclusiveOuter)
			L1_doPushLastOuter(interestingPowerOuter)
			L1_doPushLastOuter(powerOuter)
			close(
				outers = listOf(
					"interestingPower" to booleanType,
					"power" to instanceMeta(extendedWholeNumbers)),
				returnType = booleanType)
			callNewDummy(
				"_∧_",
				listOf(booleanType, functionType(emptyTuple, booleanType)),
				booleanType)
			L1_doPushLastOuter(rangeOuter)
			L1_doPushLocal(value)
			close(
				outers = listOf(
					"range" to variableTypeFor(instanceMeta(extendedIntegers)),
					"value" to variableTypeFor(extendedIntegers)),
				returnType = TOP())
			L1_doPushLastOuter(openLimitsOuter)
			L1_doPushLastLocal(value)
			close(
				outers = listOf(
					"openLimits" to
						variableTypeFor(instanceMeta(extendedIntegers)),
					"value" to variableTypeFor(extendedIntegers)),
				returnType = TOP())
			call("If_then_else_", TOP())
		}

		helper.testOptimize(rawFunction)
	}

	/**
	 * This was a nice short example from Function's [“_”#2#2].  After reduction
	 * of applicable dispatch branches, it had a hashed test for the function
	 * type's parameter type, specifically looking for "⊥", but using just the
	 * low bit (not optimal, but correct).  After code splitting, there was an
	 * impossible path related to the paramater extraction from the function
	 * type "⊥" producing "⊥" in every case, so the case where the low bit was
	 * *not* the same as "⊥"'s hash led to a contradiction.  During the explicit
	 * conditional postponement pass that followed, it noticed this
	 * contradiction at a time when it shouldn't have, and failed an assertion
	 * in [L2Instruction.transformedByRegenerator], where the instruction was an
	 * [L2_JUMP_IF_COMPARE_INT].
	 *
	 * The resolution was to remove an assertion in transformedByRegenerator, to
	 * allow the previous pass to leave the graph with [L2_IMPOSSIBLE_CODE]
	 * instructions.
	 */
	@Test
	fun testFunctionTypePrint_2_2()
	{
		val rawFunction = helper.rawFunction(TOP()) {
			argumentTypes()

			// Outers
			val s = createOuter(variableTypeFor(stringType))
			declareName("s")
			val t = createOuter(functionMeta())
			declareName("t")
			val index = createOuter(variableTypeFor(naturalNumbers))
			declareName("index")

			// :: s ++= “t[index]”;
			L1_doPushLastOuter(s)
			L1_doPushLastOuter(t)
			L1_doGetOuter(index)
			call("_[_]", anyMeta)
			call("“_”", stringType)
			callNewDummy(
				"_↑++=_",
				listOf(
					variableReadWriteType(mostGeneralTupleType, bottom),
					mostGeneralTupleType),
				TOP())
//			L1_doPop()
//
//			// :: index := eject index + 1;
//			L1_doPushOuter(index)
//			call("eject_↑", naturalNumbers)
//			pushLiteral(one)
//			call("_+_", integerRangeType(two, true, positiveInfinity, false))
//			L1_doSetOuter(index)
//			pushLiteral(nil)
		}

		helper.testOptimize(rawFunction)
	}

	/**
	 * This excerpt is from the trial division factorization algorithm, in
	 * particular the part where it scans for i32 factors in blocks of 30, only
	 * checking the multiple of 30 plus 1, 7, 11, 13, 17, 19, 23, and 29 (which
	 * arce the offsets that are coprime to 30).
	 *
	 * This test is a reduction to only offsets 1 and 7.  TODO Comelete
	 *
	 * TODO resolution (seems to be in multiplication instruction)
	 */
	@Test
	fun testTrialDivisionFactorization_5_1()
	{
		val rawFunction = helper.rawFunction(TOP()) {
			argumentTypes()

			// Outers
			val s = createOuter(variableTypeFor(stringType))
			declareName("s")
			val t = createOuter(functionMeta())
			declareName("t")
			val index = createOuter(variableTypeFor(naturalNumbers))
			declareName("index")

			// :: s ++= “t[index]”;
			L1_doPushLastOuter(s)
			L1_doPushLastOuter(t)
			L1_doGetOuter(index)
			call("_[_]", anyMeta)
			call("“_”", stringType)
			callNewDummy(
				"_↑++=_",
				listOf(
					variableReadWriteType(mostGeneralTupleType, bottom),
					mostGeneralTupleType),
				TOP())
//			L1_doPop()
//
//			// :: index := eject index + 1;
//			L1_doPushOuter(index)
//			call("eject_↑", naturalNumbers)
//			pushLiteral(one)
//			call("_+_", integerRangeType(two, true, positiveInfinity, false))
//			L1_doSetOuter(index)
//			pushLiteral(nil)
		}

		helper.testOptimize(rawFunction)
	}

	/**
	 * Regreession test for "a Mersenne Twister stream from_#2" in Mersenne
	 * Twister.  This particular raw function optimized to completion, but
	 * contained a flaw whereby at runtime an [L2_UNBOX_INT] instruction's
	 * JVM instructions sent [extractInt] was applied to an integer that was
	 * 63 bits long, the result of a multiplication.
	 *
	 * The cause was an incorrect merging of equivalent postponed instructions,
	 * failing to take into account the union of the restrictions on a value
	 * consumed by the instructions.  This forced the wrong restriction on the
	 * merged postponed instruction's read operand, which led to an impossible
	 * restriction later, including the removal of a branch based on the type.
	 *
	 * The operands of the merged postponed instruction are now set up correctly
	 * with the union of the incoming restrictions.
	 */
	@Test
	fun testAMersenneTwisterStreamFrom_2()
	{
		val vectorType = tupleTypeForSizesTypesDefaultType(
			inclusive(624, 624), emptyTuple, u32)
		val twoOrMore = integerRangeType(two, true, positiveInfinity, false)
		val threeOrMore =
			integerRangeType(fromInt(3), true, positiveInfinity, false)
		val rawFunction = helper.rawFunction(TOP()) {
			argumentTypes()

			// Constants
			val prevLocal = createConstant(u32)
			declareName("prev")
			val thisLocal = createConstant(u32)
			declareName("this")
			val valueLocal = createConstant(wholeNumbers)
			declareName("value")

			// Outers
			val vectorOuter = createOuter(variableTypeFor(vectorType))
			declareName("vector")
			val iOuter = createOuter(variableTypeFor(twoOrMore))
			declareName("i")

			// :: prev ::= vector[i - 1];
			L1_doGetOuter(vectorOuter)
			L1_doGetOuter(iOuter)
			pushLiteral(one)
			call("_-_", naturalNumbers)
			call("_[_]", u32)
			L1Ext_doSetLocalSlot(prevLocal)

			// :: this ::= vector[i];
			L1_doGetOuter(vectorOuter)
			L1_doGetOuter(iOuter)
			call("_[_]", u32)
			L1Ext_doSetLocalSlot(thisLocal)

			// :: value ::= (this bit⊕ ((prev bit⊕ (prev >> 30)) × 1566083941))
			//      - (i - 1);
			L1_doPushLastLocal(thisLocal)
			L1_doPushLocal(prevLocal)
			L1_doPushLastLocal(prevLocal)
			pushLiteral(fromInt(30))
			call("_>>_", inclusive(0, 3))
			call("_bit⊕_", wholeNumbers)
			pushLiteral(fromInt(1566083941))
			call("_×_", wholeNumbers)
			call("_bit⊕_", wholeNumbers)
			L1_doGetOuter(iOuter)
			pushLiteral(one)
			call("_-_", naturalNumbers)
			call("_-_", integers)
			L1Ext_doSetLocalSlot(valueLocal)

			// :: vector := vector[i]→value bit∧ ((1<<32)-1);
			L1_doGetLastOuter(vectorOuter)
			L1_doGetOuter(iOuter)
			L1_doPushLastLocal(valueLocal)
			pushLiteral(fromLong(0xFFFF_FFFFL))
			call("_bit∧_", u32)
			call("_[_]→_", vectorType)
			L1_doSetOuter(vectorOuter)

			// :: If (++i) > 624 then [...vector...i...];
			L1_doPushOuter(iOuter)
			call("(++_↑)", threeOrMore)
			pushLiteral(fromInt(624))
			L1Ext_doPermute(addLiteral(tuple(two, one)))
			call("_②>_①", booleanType)
			L1_doPushLastOuter(vectorOuter)
			L1_doPushLastOuter(iOuter)
			close(
				outers = listOf(
					"vector" to variableTypeFor(vectorType),
					"i" to variableTypeFor(twoOrMore)),
				returnType = TOP())
			call("If_then_", TOP())
		}

		helper.testOptimize(rawFunction)
	}

	/**
	 * Regreession test for the For-each block in the test "bit∧" in Math Tests,
	 * currently lines 327-339.
	 *
	 * Code splitting fails when attempting to translate an [L2_BOX_INT] from
	 * `Int(24-323 Mul 23-326)` to `24-323 Mul 23-326 & 24-331`.  In particular,
	 * the source semantic value does not exist in the manifest.
	 *
	 * The problem was ultimately found in
	 * [L2_MULTIPLY_INT_BY_INT.emitTransformedInstruction], where it reduced
	 * into a constant form.  It was failing to jump to the appropriate
	 * inRange or outOfRange target after setting up an int constant or a
	 * non-int constant, respectively.
	 */
	@Test
	fun testMathTestBitAnd_12()
	{
		val iType = inclusive(1, 100)
		// 2^100.
		val max = one.bitShift(fromInt(100), false).makeImmutable()
		assert(max.toString() == "1267650600228229401496703205376")
		// 2^100 - 1.
		val maxM1 = max.minusCanDestroy(one, false).makeImmutable()
		// -(2^100).
		val negMax = zero.minusCanDestroy(max, false).makeImmutable()
		// -(2^100) - 1.
		val negMaxM1 = negMax.minusCanDestroy(one, false).makeImmutable()
		// -(2^101).
		val neg2Max = negMax.bitShift(fromInt(-1), false).makeImmutable()
		val negTwo = fromInt(-2)
		val negThree = fromInt(-3)
		val negFour = fromInt(-4)
		val rawFunction = helper.rawFunction(TOP()) {
			argumentTypes(iType)
			val i = declareName("i")

			// Constants
			val power = createConstant(inclusive(two, max))
			declareName("power")
			val tempActual2 = createConstant(inclusive(zero, max))
			declareName("tempActual2")
			val tempExpected3 = createConstant(inclusive(two, max))
			declareName("tempExpected3")
			val tempActual4 = createConstant(inclusive(zero, maxM1))
			declareName("tempActual4")
			val tempExpected5 = createConstant(instanceType(zero))
			declareName("tempExpected5")
			val tempActual6 = createConstant(inclusive(zero, max))
			declareName("tempActual6")
			val tempExpected7 = createConstant(inclusive(two, max))
			declareName("tempExpected7")
			val tempActual8 = createConstant(inclusive(zero, maxM1))
			declareName("tempActual8")
			val tempExpected9 = createConstant(inclusive(one, maxM1))
			declareName("tempExpected9")
			// const#10:
			val neg = createConstant(inclusive(negMax, negTwo))
			declareName("neg")
			val tempActual11 = createConstant(inclusive(negMax, negTwo))
			declareName("tempActual11")
			val tempExpected12 = createConstant(inclusive(negMax, negTwo))
			declareName("tempExpected12")
			val tempActual13 = createConstant(inclusive(negMax, negTwo))
			declareName("tempActual13")
			val tempExpected14 = createConstant(inclusive(negMax, negTwo))
			declareName("tempExpected14")
			val tempActual15 = createConstant(inclusive(negMax, negThree))
			declareName("tempActual15")
			val tempExpected16 = createConstant(inclusive(neg2Max, negFour))
			declareName("tempExpected16")
			val tempActual17 = createConstant(inclusive(neg2Max, negThree))
			declareName("tempActual17")
			val tempExpected18 = createConstant(inclusive(negMaxM1, negThree))
			declareName("tempExpected18")
			val tempActual19 = createConstant(inclusive(zero, max))
			declareName("tempActual19")
			val tempExpected20 = createConstant(inclusive(two, max))
			declareName("tempExpected20")

			// :: power ::= 1 << i;
			pushLiteral(one)
			L1_doPushLastLocal(i)
			call("_<<_", inclusive(two, max))
			L1Ext_doSetLocalSlot(power)

			// :: Require: power bit∧ power = power;
			L1_doPushLocal(power)
			L1_doPushLocal(power)
			call("_bit∧_", inclusive(zero, max))
			L1Ext_doSetLocalSlot(tempActual2)
			L1_doPushLocal(power)
			L1Ext_doSetLocalSlot(tempExpected3)
			L1_doPushLocal(tempActual2)
			L1_doPushLocal(tempExpected3)
			call("_=_", booleanType)
			L1_doPushLastLocal(tempExpected3)
			L1_doPushLastLocal(tempActual2)
			close(
				outers = listOf(
					"tempExpected3" to inclusive(two, max),
					"tempActual2" to inclusive(zero, max)),
				returnType = bottom)
			call("Unless_then_", TOP())
			L1_doPop()

			// :: Require: power bit∧ (power - 1) = 0;
			L1_doPushLocal(power)
			L1_doPushLocal(power)
			pushLiteral(one)
			call("_-_", inclusive(one, maxM1))
			call("_bit∧_", inclusive(zero, maxM1))
			L1Ext_doSetLocalSlot(tempActual4)
			pushLiteral(zero)
			L1Ext_doSetLocalSlot(tempExpected5)
			L1_doPushLocal(tempActual4)
			L1_doPushLocal(tempExpected5)
			call("_=_", booleanType)
			L1_doPushLastLocal(tempExpected5)
			L1_doPushLastLocal(tempActual4)
			close(
				outers = listOf(
					"tempExpected5" to instanceType(zero),
					"tempActual4" to inclusive(zero, maxM1)),
				returnType = bottom)
			call("Unless_then_", TOP())
			L1_doPop()

			// :: Require: power bit∧ (power + 1) = power;
			L1_doPushLocal(power)
			L1_doPushLocal(power)
			pushLiteral(one)
			call("_+_", inclusive(fromInt(3), max.plusCanDestroy(one, false)))
			call("_bit∧_", inclusive(zero, max))
			L1Ext_doSetLocalSlot(tempActual6)
			L1_doPushLocal(power)
			L1Ext_doSetLocalSlot(tempExpected7)
			L1_doPushLocal(tempActual6)
			L1_doPushLocal(tempExpected7)
			call("_=_", booleanType)
			L1_doPushLastLocal(tempExpected7)
			L1_doPushLastLocal(tempActual6)
			close(
				outers = listOf(
					"tempExpected7" to inclusive(two, max),
					"tempActual6" to inclusive(zero, max)),
				returnType = bottom)
			call("Unless_then_", TOP())
			L1_doPop()

			// :: Require: (power - 1) bit∧ (power - 1) = (power - 1);
			L1_doPushLocal(power)
			pushLiteral(one)
			call("_-_", inclusive(one, maxM1))
			L1_doPushLocal(power)
			pushLiteral(one)
			call("_-_", inclusive(one, maxM1))
			call("_bit∧_", inclusive(zero, maxM1))
			L1Ext_doSetLocalSlot(tempActual8)
			L1_doPushLocal(power)
			pushLiteral(one)
			call("_-_", inclusive(one, maxM1))
			L1Ext_doSetLocalSlot(tempExpected9)
			L1_doPushLocal(tempActual8)
			L1_doPushLocal(tempExpected9)
			call("_=_", booleanType)
			L1_doPushLastLocal(tempExpected9)
			L1_doPushLastLocal(tempActual8)
			close(
				outers = listOf(
					"tempExpected9" to inclusive(one, maxM1),
					"tempActual8" to inclusive(zero, maxM1)),
				returnType = bottom)
			call("Unless_then_", TOP())
			L1_doPop()

			// :: neg ::= -power;
			L1_doPushLocal(power)
			call("-_", inclusive(negMax, negTwo))
			L1Ext_doSetLocalSlot(neg)

			// :: Require: neg bit∧ neg = neg;
			L1_doPushLocal(neg)
			L1_doPushLocal(neg)
			call("_bit∧_", inclusive(negMax, negTwo))
			L1Ext_doSetLocalSlot(tempActual11)
			L1_doPushLocal(neg)
			L1Ext_doSetLocalSlot(tempExpected12)
			L1_doPushLocal(tempActual11)
			L1_doPushLocal(tempExpected12)
			call("_=_", booleanType)
			L1_doPushLastLocal(tempExpected12)
			L1_doPushLastLocal(tempActual11)
			close(
				outers = listOf(
					"tempExpected12" to inclusive(negMax, negTwo),
					"tempActual11" to inclusive(negMax, negTwo)),
				returnType = bottom)
			call("Unless_then_", TOP())
			L1_doPop()

			// :: Require: neg bit∧ (neg + 1) = neg;
			L1_doPushLocal(neg)
			L1_doPushLocal(neg)
			pushLiteral(one)
			call(
				"_+_",
				inclusive(negMax.plusCanDestroy(one, false), negativeOne))
			call("_bit∧_", inclusive(negMax, negTwo))
			L1Ext_doSetLocalSlot(tempActual13)
			L1_doPushLocal(neg)
			L1Ext_doSetLocalSlot(tempExpected14)
			L1_doPushLocal(tempActual13)
			L1_doPushLocal(tempExpected14)
			call("_=_", booleanType)
			L1_doPushLastLocal(tempExpected14)
			L1_doPushLastLocal(tempActual13)
			close(
				outers = listOf(
					"tempExpected14" to inclusive(negMax, negTwo),
					"tempActual13" to inclusive(negMax, negTwo)),
				returnType = bottom)
			call("Unless_then_", TOP())
			L1_doPop()

			// :: Require: neg bit∧ (neg - 1) = neg × 2;
			L1_doPushLocal(neg)
			L1_doPushLocal(neg)
			pushLiteral(one)
			call("_-_", inclusive(negMaxM1, negThree))
			call("_bit∧_", inclusive(neg2Max, negThree))
			L1Ext_doSetLocalSlot(tempActual15)
			L1_doPushLocal(neg)
			pushLiteral(two)
			call("_×_", inclusive(neg2Max, negFour))
			L1Ext_doSetLocalSlot(tempExpected16)
			L1_doPushLocal(tempActual15)
			L1_doPushLocal(tempExpected16)
			call("_=_", booleanType)
			L1_doPushLastLocal(tempExpected16)
			L1_doPushLastLocal(tempActual15)
			close(
				outers = listOf(
					"tempExpected16" to inclusive(neg2Max, negFour),
					"tempActual15" to inclusive(neg2Max, negThree)),
				returnType = bottom)
			call("Unless_then_", TOP())
			L1_doPop()

			// :: Require: (neg - 1) bit∧ (neg - 1) = neg - 1;
			L1_doPushLocal(neg)
			pushLiteral(one)
			call("_-_", inclusive(negMaxM1, negThree))
			L1_doPushLocal(neg)
			pushLiteral(one)
			call("_-_", inclusive(negMaxM1, negThree))
			call("_bit∧_", inclusive(neg2Max, negThree))
			L1Ext_doSetLocalSlot(tempActual17)
			L1_doPushLocal(neg)
			pushLiteral(one)
			call("_-_", inclusive(negMaxM1, negThree))
			L1Ext_doSetLocalSlot(tempExpected18)
			L1_doPushLocal(tempActual17)
			L1_doPushLocal(tempExpected18)
			call("_=_", booleanType)
			L1_doPushLastLocal(tempExpected18)
			L1_doPushLastLocal(tempActual17)
			close(
				outers = listOf(
					"tempExpected18" to inclusive(negMaxM1, negThree),
					"tempActual17" to inclusive(neg2Max, negThree)),
				returnType = bottom)
			call("Unless_then_", TOP())
			L1_doPop()

			// :: Require: neg bit∧ power = power;
			L1_doPushLastLocal(neg)
			L1_doPushLocal(power)
			call("_bit∧_", inclusive(zero, max))
			L1Ext_doSetLocalSlot(tempActual19)
			L1_doPushLastLocal(power)
			L1Ext_doSetLocalSlot(tempExpected20)
			L1_doPushLocal(tempActual19)
			L1_doPushLocal(tempExpected20)
			call("_=_", booleanType)
			L1_doPushLastLocal(tempExpected20)
			L1_doPushLastLocal(tempActual19)
			close(
				outers = listOf(
					"tempExpected20" to inclusive(two, max),
					"tempActual19" to inclusive(zero, max)),
				returnType = bottom)
			call("Unless_then_", TOP())
		}

		helper.testOptimize(rawFunction)
	}

	/**
	 * Regreession test for the For-each block in the test "bit∧" in Math Tests,
	 * currently lines 327-339.  Reduced to just the definition of power and
	 * neg, and the source line containing `neg × 2`.
	 *
	 * Code splitting fails when attempting to translate an [L2_BOX_INT] from
	 * `Int(24-323 Mul 23-326)` to `24-323 Mul 23-326 & 24-331`.  In particular,
	 * the source semantic value does not exist in the manifest.
	 *
	 * The problem was ultimately found in
	 * [L2_MULTIPLY_INT_BY_INT.emitTransformedInstruction], where it reduced
	 * into a constant form.  It was failing to jump to the appropriate
	 * inRange or outOfRange target after setting up an int constant or a
	 * non-int constant, respectively.
	 */
	@Test
	fun testMathTestBitAnd_12_Reduced()
	{
		val iType = inclusive(1, 100)
		// 2^100.
		val max = one.bitShift(fromInt(100), false).makeImmutable()
		assert(max.toString() == "1267650600228229401496703205376")
		// -(2^100).
		val negMax = zero.minusCanDestroy(max, false).makeImmutable()
		// -(2^100) - 1.
		val negMaxM1 = negMax.minusCanDestroy(one, false).makeImmutable()
		// -(2^101).
		val neg2Max = negMax.bitShift(fromInt(-1), false).makeImmutable()
		val negTwo = fromInt(-2)
		val negThree = fromInt(-3)
		val negFour = fromInt(-4)
		val rawFunction = helper.rawFunction(TOP()) {
			argumentTypes(iType)
			val i = declareName("i")

			// Constants
			val power = createConstant(inclusive(two, max))
			declareName("power")
			val neg = createConstant(inclusive(negMax, negTwo))
			declareName("neg")
			val tempActual15 = createConstant(inclusive(negMax, negThree))
			declareName("tempActual15")
			val tempExpected16 = createConstant(inclusive(neg2Max, negFour))
			declareName("tempExpected16")

			// :: power ::= 1 << i;
			pushLiteral(one)
			L1_doPushLastLocal(i)
			call("_<<_", inclusive(two, max))
			L1Ext_doSetLocalSlot(power)

			// :: neg ::= -power;
			L1_doPushLocal(power)
			call("-_", inclusive(negMax, negTwo))
			L1Ext_doSetLocalSlot(neg)


			// :: /*Require:*/ /*neg bit∧*/ (neg - 1) = neg × 2;
			L1_doPushLocal(neg)
			pushLiteral(one)
			call("_-_", inclusive(negMaxM1, negThree))
			L1Ext_doSetLocalSlot(tempActual15)
			L1_doPushLocal(neg)
			pushLiteral(two)
			call("_×_", inclusive(neg2Max, negFour))
			L1Ext_doSetLocalSlot(tempExpected16)
			L1_doPushLocal(tempActual15)
			L1_doPushLocal(tempExpected16)
			call("_=_", booleanType)
		}

		helper.testOptimize(rawFunction)
	}


	/**
	 * Regreession test for the body of `"“_”"` in Tuples.avail, specifically
	 * the method for printing tuple types.
	 *
	 * During code splitting, one specialized path determined that the provided
	 * tuple type was ⊥, so its instance count ([P_InstanceCount]) was zero.
	 * This was compared against ∞, but the reduction logic in
	 * [NumericComparator.generateCompareAndBranchBoxed] skipped it because of
	 * the infinity (it wasn't in [integers]).  So it kept both branches, one
	 * of which contained a latent contradiction, which was exposed when the
	 * ifFalse edge and the ifFalse edge from another code split replica had to
	 * merge, at which time retroactive generation of t (the tuple type) caused
	 * an [L2_IMPOSSIBLE_CODE] to be emitted in the broken ifFalse edge.
	 * This would have been detected earlier if the arguments to the comparison
	 * had become properly restricted, but the graph showed that the original
	 * restrictions (from before code splitting) were still in use.
	 *
	 * The comparison input restriction computations now support extended
	 * integers, causing the replica for t=⊥ to have its comparison eliminated,
	 * replaced by an unconditional jump to the ifTrue edge.
	 *
	 * TODO – provide a secondary mechanism that handles an [L2_IMPOSSIBLE_CODE]
	 *  being generated retroactively before a merge point.  I don't know how to
	 *  make that survivable.  Perhaps re-split the edge, with the first edge
	 *  leading to a terminal [L2_IMPOSSIBLE_CODE], and the second edge that
	 *  leads to the merge point providing bogus data and having no predecessor,
	 *  causing it to be dropped in the next pass.
	 */
	@Test
	fun testTupleTypePrinting()
	{
		val rawFunction = helper.rawFunction(stringType) {
			argumentTypes(tupleMeta)
			val t = declareName("t")

			// :: if |t| < ∞ then [“(t :: nontype's type)”]
			// :: else if t = tuple then ["tuple"]
			// :: else if t = string then ["string"]
			// :: else
			// :: [
			// ::    s : nonempty string := "<";
			// ::    ...
			// ::    s ++= ">";
			// ::    s
			// :: ]
			L1_doPushLocal(t)
			call("`|_`|", inclusive(zero, positiveInfinity))
			pushLiteral(positiveInfinity)
			call("_<_", booleanType)
			L1_doPushLocal(t)
			close(
				outers = listOf(
					"t" to tupleMeta),
				returnType = stringType)
			L1_doPushLastLocal(t)
			close(
				outers = listOf(
					"t" to tupleMeta),
				returnType = TOP())
			call("If_then_else_", stringType)
		}

		helper.testOptimize(rawFunction)
	}

	/**
	 * Regreession test for the body of the 8th block in the 'continuations'
	 * test in Control Structure Tests (currently at line 86).
	 *
	 * During phi move insertion, a synonym was found to be constrained to a
	 * constant, without a corresponding semantic constant in the synonym.
	 * The constant restriction was the (boxed) integer `2`, for the semantic
	 * value `checkValue-1`.  The phi move just prior to the backward jump was
	 * correctly inserted (and for the forward jump at the start of the graph).
	 * However, [L2ValueManifest.retainSemanticValues] was then invoked for the
	 * regenerated [L2_JUMP_BACK], which stripped off the semantic constant,
	 * leaving the manifest along that back edge in an invalid state – every
	 * constant constraint has to have a semantic constant in the corresponding
	 * synonym.
	 *
	 * The fix is to have [L2ValueManifest.retainSemanticValues] (via its
	 * helper) also keep semantic constants that are synonymous with semantic
	 * values that are supposed to be retained, even if the semantic value
	 * itself is not explicitly named as retained.
	 */
	@Test
	fun testControlStructuretests_Continuations_8()
	{
		val rawFunction = helper.rawFunction(booleanType) {
			argumentTypes(integers)
			val checkValue = declareName("checkValue")

			// Label
			// :: $check : boolean;
			/*val check =*/ declareName("check")

			// :: Exit check with true if checkValue = 2;
			L1Ext_doPushLabel()
			pushLiteral(trueObject)
			L1_doPushLocal(checkValue)
			pushLiteral(two)
			call("_=_", booleanType)
			call("Exit_with_if_", TOP())
			L1_doPop()

			// :: Restart check with <2>
			L1Ext_doPushLabel()
			pushLiteral(tuple(two))
			call("Restart_with_", bottom)
		}

		helper.testOptimize(rawFunction)
	}

	/**
	 * The initial translation of Method "_×_" in Math.avail (line ~504) had a
	 * lookup of the floor function (⌊a⌋) that failed translation to L2.  This
	 * is a greatly reduced version of just the initial instructions.
	 *
	 */
	@Test
	fun testMultiplyIntegralTypes()
	{
		val rawFunction = helper.rawFunction(extendedIntegers) {
			argumentTypes(extendedIntegersMeta)
			val a = declareName("a")
			L1_doPushLastLocal(a)
			call("⌊_⌋", extendedIntegers)
		}

		helper.testOptimize(rawFunction)
	}
}
