/*
 * L2_INVOKE_CONSTANT_FUNCTION.kt
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
package avail.interpreter.levelTwo.operation

import avail.descriptor.representation.A_RawFunction.Companion.declarationNames
import avail.descriptor.representation.A_RawFunction.Companion.methodName
import avail.descriptor.representation.A_RawFunction.Companion.numArgs
import avail.descriptor.representation.A_String.Companion.asNativeString
import avail.descriptor.representation.A_Type.Companion.returnType
import avail.interpreter.execution.Interpreter
import avail.interpreter.levelTwo.HiddenVariable.CURRENT_FUNCTION
import avail.interpreter.levelTwo.HiddenVariable.LATEST_RETURN_VALUE
import avail.interpreter.levelTwo.L2NamedOperandType.Purpose.OFF_RAMP
import avail.interpreter.levelTwo.L2NamedOperandType.Purpose.SUCCESS
import avail.interpreter.levelTwo.L2OperandType
import avail.interpreter.levelTwo.On
import avail.interpreter.levelTwo.WritesHiddenVariable
import avail.interpreter.levelTwo.operand.L2ConstantOperand
import avail.interpreter.levelTwo.operand.L2PcOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedVectorOperand
import avail.interpreter.levelTwo.operand.L2WriteBoxedOperand
import avail.interpreter.primitive.Primitive.Fallibility.CallSiteCanFail
import avail.interpreter.primitive.Primitive.Fallibility.CallSiteCannotFail
import avail.interpreter.primitive.Primitive.Flag.CanInline
import avail.interpreter.primitive.Primitive.Flag.CanSwitchContinuations
import avail.interpreter.primitive.Primitive.Flag.Invokes
import avail.interpreter.primitive.Primitive.Flag.Unknown
import avail.optimizer.L2GeneratorInterface
import avail.optimizer.L2SplitCondition
import avail.optimizer.L2ValueManifest
import avail.optimizer.StackReifier
import avail.optimizer.jvm.JVMTranslator
import avail.optimizer.reoptimizer.L2Regenerator
import avail.utility.Strings.increaseIndentation

/**
 * The given (constant) function is invoked.  The function may be a primitive,
 * and the primitive may succeed, fail, or replace the current continuation
 * (after reifying the stack).  It may also trigger reification of this frame by
 * Java-returning a [StackReifier] instead of null.
 *
 * The return value can be picked up from
 * [latestResult][Interpreter.getLatestResult] in a subsequent
 * [L2_GET_LATEST_RETURN_VALUE] instruction. Note that the value that was
 * returned has not been dynamically type-checked yet, so if its validity can't
 * be proven statically by the VM, the calling function should check the type
 * against its expectation (prior to the value getting captured in any
 * continuation).
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
@WritesHiddenVariable(
	CURRENT_FUNCTION::class,
	LATEST_RETURN_VALUE::class)
class L2_INVOKE_CONSTANT_FUNCTION(
	var constantFunction: L2ConstantOperand,
	var arguments: L2ReadBoxedVectorOperand,
	@On(SUCCESS) var result: L2WriteBoxedOperand,
	@On(SUCCESS) var ifReturn: L2PcOperand,
	@On(OFF_RAMP) var ifReification: L2PcOperand
): L2ControlFlowInstruction()
{
	override val hasSideEffect get() = true

	/** If it's primitive, defer to it, otherwise assume the worst. */
	override fun mightMakeEscapedVariableShared(
		manifest: L2ValueManifest
	): Boolean =
		when (val prim = constantFunction.constant.code().codePrimitive())
		{
			null -> true
			else -> prim.mightMakeEscapedVariableShared(
				arguments.elements.map(L2ReadBoxedOperand::type))
		}

	override fun L2Regenerator.regenerateForPostponement()
	{
		forcePostponedWritesToLocals()
		basicRegenerateForPostponement()
	}

	/**
	 * If the function is bottom-valued, treat the block as cold, and don't
	 * bother splitting paths that lead only to it and other cold blocks. The
	 * called function will definitely have to raise an exception, exit or
	 * restart a continuation, loop forever, or terminate the fiber, so
	 * splitting the code is not likely to have a big impact.
	 */
	override val isCold: Boolean
		get() =
			constantFunction.constant.code().functionType().returnType.isBottom

	override fun StringBuilder.appendToWithWarnings(
		desiredOperandTypes: Set<L2OperandType>,
		ignoreMisconnections: Boolean,
		warningStyleChange: (Boolean)->Unit)
	{
		val function = constantFunction.constant
		val code = function.code()
		renderPreamble()
		append(' ')
		append(result.registerString())
		append(" ← /* ")
		append(function.code().methodName.asNativeString())
		append(" */\n")
		append(function)
		append("(")
		val argNames = code.declarationNames.take(code.numArgs())
		arguments.elements.zip(argNames).joinTo(this, ",") { (arg, name) ->
			val valueString = increaseIndentation(arg.registerString(), 2)
			"\n\t\t${name.asNativeString()} ← $valueString"
		}
		append(")")
		renderOperandsExcludingFields(
			desiredOperandTypes, ::constantFunction, ::arguments)
	}

	override fun interestingConditions(): List<L2SplitCondition?>
	{
		val rawFunction = constantFunction.constant.code()
		val argumentTypes = arguments.elements.map(L2ReadBoxedOperand::type)
		val primitive = rawFunction.codePrimitive()
		when
		{
			primitive === null -> { }
			!primitive.hasFlag(CanInline) -> { }
			primitive.hasFlag(CanSwitchContinuations) -> { }
			primitive.hasFlag(Invokes) -> { }
			primitive.hasFlag(Unknown) -> { }
			primitive.fallibilityForArgumentTypes(argumentTypes)
				== CallSiteCanFail ->
			{
				// The call site is *sometimes* fallible, so ask the primitive
				// to produce any interesting conditions that might make it
				// entirely infalllible along some split paths.
				return primitive.interestingSplitConditions(
					arguments.elements, rawFunction)
			}
		}
		return emptyList()
	}

	override fun L2GeneratorInterface.emitTransformedInstruction()
	{
		// See if the new situation has become specialized enough to invoke a
		// primitive that's infallible for these arguments.
		val rawFunction = constantFunction.constant.code()
		val argumentTypes = arguments.elements.map(L2ReadBoxedOperand::type)
		val primitive = rawFunction.codePrimitive()
		when
		{
			primitive === null -> { }
			!primitive.hasFlag(CanInline) -> { }
			primitive.hasFlag(CanSwitchContinuations) -> { }
			primitive.hasFlag(Invokes) -> { }
			primitive.hasFlag(Unknown) -> { }
			primitive.fallibilityForArgumentTypes(argumentTypes)
				== CallSiteCannotFail ->
			{
				val resultType = primitive.returnTypeGuaranteedByVM(
					rawFunction, argumentTypes)
				+L2_RUN_INFALLIBLE_PRIMITIVE.createInstruction(
					L2ConstantOperand(rawFunction),
					primitive,
					arguments,
					boxedWrite(
						result.semanticValues(),
						result.restriction().intersectionWithType(resultType)))
				jumpTo(ifReturn.targetBlock())
				return
			}
		}
		+this@L2_INVOKE_CONSTANT_FUNCTION
	}

	override fun JVMTranslator.translateToJVM()
	{
		loadInterpreter()
		// :: [interpreter]
		loadInterpreter()
		// :: [interpreter, interpreter]
		load(Interpreter.chunkField)
		// :: [interpreter, callingChunk]
		loadInterpreter()
		// :: [interpreter, callingChunk, interpreter]
		loadLiteralObject(constantFunction.constant)
		// :: [interpreter, callingChunk, interpreter, function]
		L2_INVOKE.run {
			generatePushArgumentsAndInvoke(
				arguments.elements,
				result,
				ifReturn,
				ifReification)
		}
	}
}
