/*
 * P_CastInto.java
 * Copyright © 1993-2019, The Avail Foundation, LLC.
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
package com.avail.interpreter.primitive.types

import com.avail.descriptor.A_Function
import com.avail.descriptor.A_RawFunction
import com.avail.descriptor.A_Type
import com.avail.interpreter.Interpreter
import com.avail.interpreter.Primitive
import com.avail.interpreter.levelTwo.operand.L2ConstantOperand
import com.avail.interpreter.levelTwo.operand.L2IntImmediateOperand
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import com.avail.interpreter.levelTwo.operation.L2_FUNCTION_PARAMETER_TYPE
import com.avail.interpreter.levelTwo.operation.L2_JUMP_IF_KIND_OF_CONSTANT
import com.avail.interpreter.levelTwo.operation.L2_JUMP_IF_KIND_OF_OBJECT
import com.avail.optimizer.L1Translator
import com.avail.optimizer.L1Translator.CallSiteHelper

import com.avail.descriptor.AbstractEnumerationTypeDescriptor.enumerationWith
import com.avail.descriptor.BottomTypeDescriptor.bottom
import com.avail.descriptor.FunctionTypeDescriptor.functionType
import com.avail.descriptor.InstanceMetaDescriptor.anyMeta
import com.avail.descriptor.ObjectTupleDescriptor.tuple
import com.avail.descriptor.SetDescriptor.set
import com.avail.descriptor.TypeDescriptor.Types.ANY
import com.avail.descriptor.TypeDescriptor.Types.TOP
import com.avail.exceptions.AvailErrorCode.E_INCORRECT_ARGUMENT_TYPE
import com.avail.interpreter.Primitive.Fallibility.CallSiteCanFail
import com.avail.interpreter.Primitive.Fallibility.CallSiteCannotFail
import com.avail.interpreter.Primitive.Fallibility.CallSiteMustFail
import com.avail.interpreter.Primitive.Flag.CanInline
import com.avail.interpreter.Primitive.Flag.Invokes
import com.avail.interpreter.levelTwo.operand.TypeRestriction.RestrictionFlagEncoding.BOXED
import com.avail.interpreter.levelTwo.operand.TypeRestriction.restrictionForType
import com.avail.optimizer.L2Generator.edgeTo

/**
 * **Primitive:** If the second argument, a [ function][A_Function], accepts the first argument as its parameter, do the invocation.
 * Otherwise fail the primitive.
 */
object P_CastInto : Primitive(2, Invokes, CanInline)
{

	override fun attempt(
		interpreter: Interpreter): Primitive.Result
	{
		interpreter.checkArgumentCount(2)
		val value = interpreter.argument(0)
		val castFunction = interpreter.argument(1)

		if (value.isInstanceOf(
				castFunction.code().functionType().argsTupleType().typeAtIndex(1)))
		{
			// "Jump" into the castFunction, to keep this frame from showing up.
			interpreter.argsBuffer.clear()
			interpreter.argsBuffer.add(value)
			interpreter.function = castFunction
			return Primitive.Result.READY_TO_INVOKE
		}
		// Fail the primitive.
		return interpreter.primitiveFailure(E_INCORRECT_ARGUMENT_TYPE)
	}

	override fun privateBlockTypeRestriction(): A_Type
	{
		return functionType(
			tuple(
				ANY.o(),
				functionType(
					tuple(
						bottom()),
					TOP.o())),
			TOP.o())
	}

	override fun privateFailureVariableType(): A_Type
	{
		return enumerationWith(set(E_INCORRECT_ARGUMENT_TYPE))
	}

	override fun returnTypeGuaranteedByVM(
		rawFunction: A_RawFunction,
		argumentTypes: List<A_Type>): A_Type
	{
		// Keep it simple.  In theory, if we could show that the cast would not
		// fail, and that the function was a primitive, we could ask the
		// primitive what it would produce.
		val castFunctionType = argumentTypes[1]
		return castFunctionType.returnType()
	}

	override fun fallibilityForArgumentTypes(
		argumentTypes: List<A_Type>): Primitive.Fallibility
	{
		val valueType = argumentTypes[0]
		val castFunctionType = argumentTypes[1]

		// Only deal with a constant castFunction for now, otherwise assume it
		// could either succeed or fail.
		if (castFunctionType.instanceCount().equalsInt(1))
		{
			val function = castFunctionType.instance()
			val code = function.code()
			val argType = code.functionType().argsTupleType().typeAtIndex(1)
			if (valueType.isSubtypeOf(argType))
			{
				return CallSiteCannotFail
			}
			if (valueType.typeIntersection(argType).isBottom)
			{
				return CallSiteMustFail
			}
		}
		return CallSiteCanFail
	}

	override fun tryToGenerateSpecialPrimitiveInvocation(
		functionToCallReg: L2ReadBoxedOperand,
		rawFunction: A_RawFunction,
		arguments: List<L2ReadBoxedOperand>,
		argumentTypes: List<A_Type>,
		translator: L1Translator,
		callSiteHelper: CallSiteHelper): Boolean
	{
		// Inline the invocation of this P_CastInto primitive, such that it
		// does a type test for the type being cast to, then either invokes the
		// first block with the value being cast or the second block with no
		// arguments.
		val valueRead = arguments[0]
		val castFunctionRead = arguments[1]

		val castBlock = translator.generator.createBasicBlock("cast type matched")
		val elseBlock = translator.generator.createBasicBlock("cast type did not match")

		val typeTest = castFunctionRead.exactSoleArgumentType()
		if (typeTest != null)
		{
			// By tracing where the castBlock came from, we were able to
			// determine the exact type to compare the value against.  This is
			// the usual case for casts, typically where the castBlock phrase is
			// simply a block phrase.  First see if we can eliminate the runtime
			// test entirely.
			val constant = valueRead.constantOrNull()
			val passedTest: Boolean?
			if (constant != null)
			{
				passedTest = constant.isInstanceOf(typeTest)
			}
			else if (valueRead.type().isSubtypeOf(typeTest))
			{
				passedTest = true
			}
			else if (valueRead.type().typeIntersection(typeTest).isBottom)
			{
				passedTest = false
			}
			else
			{
				passedTest = null
			}
			if (passedTest != null)
			{
				// Go to the castBlock or elseBlock without having to do the
				// runtime type test (since we just did it).  Don't do a type
				// check on the result, because the client will deal with it.
				if (passedTest)
				{
					translator.generateGeneralFunctionInvocation(
						castFunctionRead,
						listOf(valueRead),
						true,
						callSiteHelper)
					return true
				}
				// In theory we could skip the check, but for simplicity just
				// generate a regular invocation.  We expect the primitive to
				// always fail, however.
				super.tryToGenerateSpecialPrimitiveInvocation(
					functionToCallReg,
					rawFunction,
					arguments,
					argumentTypes,
					translator,
					callSiteHelper)
				return true
			}

			// We know the exact type to compare the value against, but we
			// couldn't statically eliminate the type test.  Emit a branch.
			translator.addInstruction(
				L2_JUMP_IF_KIND_OF_CONSTANT.instance,
				valueRead,
				L2ConstantOperand(typeTest),
				edgeTo(castBlock),
				edgeTo(elseBlock))
		}
		else
		{
			// We don't statically know the type to compare the value against,
			// but we can get it at runtime by extracting the actual
			// castFunction's argument type.  Note that we can't phi-strengthen
			// the valueRead along the branches, since we don't statically know
			// the type that it was compared to.
			val parameterTypeWrite = translator.generator.boxedWriteTemp(
				restrictionForType(anyMeta(), BOXED))
			translator.addInstruction(
				L2_FUNCTION_PARAMETER_TYPE.instance,
				castFunctionRead,
				L2IntImmediateOperand(1),
				parameterTypeWrite)
			translator.addInstruction(
				L2_JUMP_IF_KIND_OF_OBJECT.instance,
				valueRead,
				translator.readBoxed(parameterTypeWrite),
				edgeTo(castBlock),
				edgeTo(elseBlock))
		}

		// We couldn't skip the runtime type check, which takes us to either
		// castBlock or elseBlock, after which we merge the control flow back.
		// Start by generating the invocation of castFunction.
		translator.generator.startBlock(castBlock)
		translator.generateGeneralFunctionInvocation(
			castFunctionRead, listOf(valueRead), true, callSiteHelper)

		// Now deal with invoking the elseBlock instead.  For simplicity, just
		// invoke this primitive function, and the redundant type test will
		// always fail.
		translator.generator.startBlock(elseBlock)
		translator.generateGeneralFunctionInvocation(
			functionToCallReg, arguments, false, callSiteHelper)
		return true

	}

}