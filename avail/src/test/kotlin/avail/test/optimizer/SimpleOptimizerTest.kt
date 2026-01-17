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
import avail.descriptor.functions.A_RawFunction.Companion.methodName
import avail.descriptor.functions.FunctionDescriptor.Companion.createFunction
import avail.descriptor.numbers.A_Number.Companion.bitShift
import avail.descriptor.numbers.A_Number.Companion.noFailMinusCanDestroy
import avail.descriptor.numbers.InfinityDescriptor.Companion.positiveInfinity
import avail.descriptor.numbers.IntegerDescriptor.Companion.fromInt
import avail.descriptor.numbers.IntegerDescriptor.Companion.one
import avail.descriptor.numbers.IntegerDescriptor.Companion.two
import avail.descriptor.numbers.IntegerDescriptor.Companion.zero
import avail.descriptor.representation.NilDescriptor.Companion.nil
import avail.descriptor.sets.SetDescriptor.Companion.set
import avail.descriptor.sets.SetDescriptor.Companion.setFromCollection
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.tuples.StringDescriptor.Companion.stringFrom
import avail.descriptor.tuples.TupleDescriptor.Companion.emptyTuple
import avail.descriptor.tuples.TupleDescriptor.Companion.tupleFromIntegerList
import avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import avail.descriptor.types.BottomTypeDescriptor.Companion.bottom
import avail.descriptor.types.EnumerationTypeDescriptor.Companion.booleanType
import avail.descriptor.types.EnumerationTypeDescriptor.Companion.falseType
import avail.descriptor.types.EnumerationTypeDescriptor.Companion.trueType
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
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.wholeNumbers
import avail.descriptor.types.PrimitiveTypeDescriptor.Types
import avail.descriptor.types.ReadWriteVariableTypeDescriptor.Companion.fromReadAndWriteTypes
import avail.descriptor.types.SetTypeDescriptor.Companion.mostGeneralSetType
import avail.descriptor.types.SetTypeDescriptor.Companion.setTypeForSizesContentType
import avail.descriptor.types.TupleTypeDescriptor.Companion.mostGeneralTupleType
import avail.descriptor.types.TupleTypeDescriptor.Companion.stringType
import avail.descriptor.types.TupleTypeDescriptor.Companion.tupleTypeForSizesTypesDefaultType
import avail.descriptor.types.TupleTypeDescriptor.Companion.tupleTypeForTypes
import avail.descriptor.types.TupleTypeDescriptor.Companion.zeroOrMoreOf
import avail.descriptor.types.TypeTag
import avail.descriptor.types.VariableTypeDescriptor.Companion.variableTypeFor
import avail.interpreter.levelOne.L1Operation.L1Ext_doPermute
import avail.interpreter.levelOne.L1Operation.L1Ext_doPushLabel
import avail.interpreter.levelOne.L1Operation.L1Ext_doSetLocalSlot
import avail.interpreter.levelOne.L1Operation.L1_doGetLastOuter
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
import avail.interpreter.levelTwo.operation.numbers.L2_BOX_INT
import avail.interpreter.primitive.controlflow.P_ExitContinuationWithResultIf
import avail.interpreter.primitive.controlflow.P_InvokeWithTuple
import avail.interpreter.primitive.controlflow.P_RestartContinuationWithArguments
import avail.interpreter.primitive.controlflow.P_ShortCircuitHelper
import avail.interpreter.primitive.doubles.P_DoubleFloor
import avail.interpreter.primitive.floats.P_FloatFloor
import avail.interpreter.primitive.general.P_EmergencyExit
import avail.interpreter.primitive.general.P_Equality
import avail.interpreter.primitive.integers.P_BitShiftLeft
import avail.interpreter.primitive.integers.P_BitwiseAnd
import avail.interpreter.primitive.maps.P_MapAtKey
import avail.interpreter.primitive.maps.P_MapSize
import avail.interpreter.primitive.numbers.P_Addition
import avail.interpreter.primitive.numbers.P_Division
import avail.interpreter.primitive.numbers.P_LessThan
import avail.interpreter.primitive.numbers.P_Multiplication
import avail.interpreter.primitive.numbers.P_Subtraction
import avail.interpreter.primitive.rawfunctions.P_PrivateForceOptimizationForTests
import avail.interpreter.primitive.sets.P_SetIsSubset
import avail.interpreter.primitive.sets.P_SetSize
import avail.interpreter.primitive.sets.P_SetToTuple
import avail.interpreter.primitive.sets.P_TupleToSet
import avail.interpreter.primitive.tuples.P_ExtractSubtuple
import avail.interpreter.primitive.tuples.P_IntegerIntervalTuple
import avail.interpreter.primitive.tuples.P_TupleAt
import avail.interpreter.primitive.tuples.P_TupleSize
import avail.interpreter.primitive.types.P_CastIntoElse
import avail.interpreter.primitive.types.P_CreateEnumeration
import avail.interpreter.primitive.types.P_InstanceCount
import avail.interpreter.primitive.types.P_Instances
import avail.interpreter.primitive.types.P_IsSubtypeOf
import avail.optimizer.L2Generator
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
			"⌊_⌋" to listOf(Types.NUMBER()) to Types.NUMBER()
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
			"_bit∧_" to P_BitwiseAnd,
			"⌊_⌋" to P_DoubleFloor,
			"⌊_⌋" to P_FloatFloor,
			"_=_" to P_Equality,
			"_<_" to P_LessThan,
			"`|_`|" to P_TupleSize,
			"`|_`|" to P_SetSize,
			"`|_`|" to P_MapSize,
			"`|_`|" to P_InstanceCount,
			"_[_]" to P_TupleAt,
			"_[_]" to P_MapAtKey,
			"_[_.._]" to P_ExtractSubtuple,
			"_→tuple" to P_SetToTuple,
			"_→set" to P_TupleToSet,
			"enumeration of_" to P_CreateEnumeration,
			"_⊆_" to P_IsSubtypeOf,
			"_⊆_" to P_SetIsSubset,
			"_'s⁇instances" to P_Instances,
			"_to_by_" to P_IntegerIntervalTuple,
			"Invoke_with tuple_" to P_InvokeWithTuple,
			"Cast|cast_into_else_" to P_CastIntoElse,
			"Exit_with_if_" to P_ExitContinuationWithResultIf,
			"Restart_with_" to P_RestartContinuationWithArguments)
		helper.defineMethod("-_", extendedIntegers) {
			argumentTypes(extendedIntegers)
			L1_doPushLiteral(
				addLiteral(stringFrom("-_ is a stub")))
			call("Crash:_", bottom)
		}
		helper.defineMethod("_to_by_", mostGeneralTupleType) {
			argumentTypes(integers, integers, instanceType(zero))
			L1_doPushLiteral(
				addLiteral(stringFrom("_to_do_ with zero delta is a stub")))
			call("Crash:_", bottom)
		}
		helper.defineMethod("If_then_", Types.TOP()) {
			argumentTypes(booleanType, functionType(emptyTuple, Types.TOP()))
			primitive = P_ShortCircuitHelper
		}
		helper.defineMethod("If_then_", Types.TOP()) {
			argumentTypes(falseType, functionType(emptyTuple, Types.TOP()))
			pushLiteral(nil)
		}
		helper.defineMethod("Unless_then_", Types.TOP()) {
			argumentTypes(booleanType, functionType(emptyTuple, Types.TOP()))
			primitive = P_ShortCircuitHelper
		}
		helper.defineMethod("Unless_then_", Types.TOP()) {
			argumentTypes(trueType, functionType(emptyTuple, Types.TOP()))
			pushLiteral(nil)
		}
		helper.addAlias("_<_", "_②>_①")
		val nullFunctionReturningString = functionType(emptyTuple, stringType)
		helper.defineMethod("Assert:_with function_", Types.TOP()) {
			argumentTypes(booleanType, nullFunctionReturningString)
			write(0, L1_doPushLiteral, addLiteral(nil))
		}
		helper.defineMethod("Assert:_with function_", bottom) {
			argumentTypes(falseType, nullFunctionReturningString)
			L1_doPushLiteral(addLiteral(stringFrom("Assertion failed")))
			call("Crash:_", bottom)
		}
		helper.defineMethod("Require:_", Types.TOP()) {
			argumentTypes(booleanType)
			pushLiteral(nil)
		}
		helper.defineMethod("Require:_", Types.TOP()) {
			argumentTypes(falseType)
			L1_doPushLiteral(addLiteral(stringFrom("Require:_ is a stub.")))
			call("Crash:_", bottom)
		}
		helper.defineMethod("_`?→_†", Types.ANY()) {
			argumentTypes(Types.ANY(), anyMeta)
			L1_doPushLastLocal(1)
		}
		helper.defineMethod("Do_while_", Types.TOP()) {
			argumentTypes(
				functionType(emptyTuple, Types.TOP()),
				functionType(emptyTuple, booleanType))
			L1_doPushLiteral(addLiteral(stringFrom("Do_while_ is a stub.")))
			call("Crash:_", bottom)
		}
		helper.defineMethod("_↑+=_", Types.TOP()) {
			argumentTypes(
				fromReadAndWriteTypes(mostGeneralTupleType, bottom),
				Types.ANY())
			L1_doPushLiteral(addLiteral(stringFrom("\"_↑+=_\" is a stub.")))
			call("Crash:_", bottom)
		}
		helper.defineMethod("⌊_⌋", extendedIntegers) {
			argumentTypes(extendedIntegersMeta)
			L1_doPushLocal(1)
		}
		helper.defineMethod("⌊_⌋is inclusive", booleanType) {
			argumentTypes(extendedIntegersMeta)
			L1_doPushLiteral(addLiteral(stringFrom("\"⌊_⌋is inclusive\" is a stub.")))
			call("Crash:_", bottom)
		}
		helper.defineMethod("⌈_⌉", extendedIntegers) {
			argumentTypes(extendedIntegersMeta)
			L1_doPushLiteral(addLiteral(stringFrom("\"⌈_⌉\" is a stub.")))
			call("Crash:_", bottom)
		}
		helper.defineMethod("⌈_⌉is inclusive", booleanType) {
			argumentTypes(extendedIntegersMeta)
			L1_doPushLiteral(addLiteral(stringFrom("\"⌈_⌉is inclusive\" is a stub.")))
			call("Crash:_", bottom)
		}
		helper.defineMethod("If_then_else_", Types.TOP()) {
			argumentTypes(
				booleanType,
				functionType(emptyTuple, Types.TOP()),
				functionType(emptyTuple, Types.TOP()))
			L1_doPushLiteral(addLiteral(stringFrom("\"If_then_else\" is a stub.")))
			call("Crash:_", bottom)
		}

		// TODO: Remove if the two primitive calls is sufficient to produce the
		//  encountered problem in [isolatedLineFromTypeAlgebraTest].
		helper.defineMethod("{«_‡,»}ᵀ", instanceMeta(Types.TOP())) {
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
			call("Assert:_with function_", Types.TOP())
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
			call("Exit_with_if_", Types.TOP())
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
			call("Exit_with_if_", Types.TOP())
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
			call("Exit_with_if_", Types.TOP())
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

			assertEquals(declareName("closeDelimiter"), closeDelimiter)
			assertEquals(declareName("interpType"), interpType)
			assertEquals(declareName("start"), start)
			assertEquals(declareName("varName"), varName)
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
				returnType = Types.TOP())
			L1_doPushOuter(index)
			L1_doPushOuter(template)
			L1_doPushLastLocal(closeDelimiter)
			close(
				outers = listOf(
					"index" to naturalNumbers,
					"template" to naturalNumbers,
					"closeDelimiter" to naturalNumbers),
				returnType = booleanType)
			call("Do_while_", Types.TOP())
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
						returnType = Types.TOP()),
					emptyTuple))
			call("If_then_", Types.TOP())
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
			call("_↑+=_", Types.TOP())
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
						emptyList(), emptyList(), Types.TOP()),
					emptyTuple))
			pushLiteral(
				createFunction(
					helper.createDummyRawFunction(
						emptyList(), emptyList(), Types.TOP()),
					emptyTuple))
			call("If_then_else_", Types.TOP())
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
			argumentTypes(Types.NUMBER(), Types.NUMBER())
			val dividend = declareName("dividend")
			val divisor = declareName("divisor")

			L1_doPushLocal(dividend)
			L1_doPushLocal(divisor)
			L1_doPushLastLocal(dividend)
			L1_doPushLastLocal(divisor)
			call("_÷_", Types.NUMBER())
			call("⌊_⌋", Types.NUMBER())
			call("_×_", Types.NUMBER())
			call("_-_", Types.NUMBER())
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
		val rawFunction = helper.rawFunction(Types.TOP()) {
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
				returnType = Types.TOP())
			call("Unless_then_", Types.TOP())
			L1_doPop()

			// :: Require: 0 ≠ delta;
			pushLiteral(zero)
			L1_doPushLastOuter(delta)
			call("_=_", booleanType)
			pushLiteral(falseObject)
			call("_=_", booleanType)
			call("Require:_", Types.TOP())
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
		val rawFunction = helper.rawFunction(Types.TOP()) {
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
			call("Unless_then_", Types.TOP())
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
			call("Unless_then_", Types.TOP())
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
		val rawFunction = helper.rawFunction(Types.TOP()) {
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
			call("Unless_then_", Types.TOP())
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

			// :; x
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
}
