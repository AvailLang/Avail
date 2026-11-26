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

import avail.descriptor.representation.A_Function
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
import avail.interpreter.levelTwo.operand.L2WriteBoxedVectorOperand
import avail.interpreter.primitive.Primitive.Fallibility.CallSiteCanFail
import avail.interpreter.primitive.Primitive.Flag.CanInline
import avail.interpreter.primitive.Primitive.Flag.CanSwitchContinuations
import avail.interpreter.primitive.Primitive.Flag.Invokes
import avail.interpreter.primitive.Primitive.Flag.Unknown
import avail.optimizer.L2CallSite
import avail.optimizer.L2Generator.Companion.edgeTo
import avail.optimizer.L2GeneratorInterface
import avail.optimizer.L2SplitCondition
import avail.optimizer.PostPhiData
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
 *
 * @property constantFunction
 *   The function to invoke.
 * @property arguments
 *   The arguments to pass to it.
 * @property result
 *   Where to write the value it returns.
 * @property returningFunction
 *   Either empty, or a single write of the function that returned the
 *   [result], which is where a failed check of the result's type puts the
 *   blame.  It is written only when the result must be checked (see
 *   [L2_INVOKE.returningFunction]).  When this invocation is replaced by code
 *   that produces the result in place, the invoked function is the one that
 *   produced it.
 * @property ifReturn
 *   Where to continue after a normal return.
 * @property ifReification
 *   Where to continue if the stack is reified during the call.
 */
@WritesHiddenVariable(
	CURRENT_FUNCTION::class,
	LATEST_RETURN_VALUE::class)
class L2_INVOKE_CONSTANT_FUNCTION(
	var constantFunction: L2ConstantOperand,
	var arguments: L2ReadBoxedVectorOperand,
	@On(SUCCESS) var result: L2WriteBoxedOperand,
	@On(SUCCESS) var returningFunction: L2WriteBoxedVectorOperand,
	@On(SUCCESS) var ifReturn: L2PcOperand,
	@On(OFF_RAMP) var ifReification: L2PcOperand
): L2ControlFlowInstruction()
{
	override val hasSideEffect get() = true

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

	override fun PostPhiData.interestingConditions(): List<L2SplitCondition?>
	{
		val rawFunction = constantFunction.constant.code()
		val argumentTypes = arguments.elements.map { it.type }
		rawFunction.codePrimitive()?.run {
			when
			{
				!hasFlag(CanInline) -> { }
				hasFlag(CanSwitchContinuations) -> { }
				hasFlag(Invokes) -> { }
				hasFlag(Unknown) -> { }
				fallibilityForArgumentTypes(argumentTypes) == CallSiteCanFail ->
				{
					// The call site is *sometimes* fallible, so ask the
					// primitive to produce any interesting conditions that
					// might make it entirely infallible along some split
					// paths.
					return interestingSplitConditions(arguments.elements)
				}
			}
		}
		return emptyList()
	}

	/**
	 * If the function has a primitive, let it generate code in place of this
	 * call, through a [RewrittenCallSite].  This is asked again whenever the
	 * graph is regenerated, since the arguments' types may have narrowed
	 * enough to reveal a new opportunity.  Otherwise keep the call.
	 */
	override fun L2GeneratorInterface.emitTransformedInstruction()
	{
		val function = constantFunction.constant
		val rawFunction = function.code()
		val primitive = rawFunction.codePrimitive()
		if (primitive !== null
			&& tryToGeneratePrimitiveInPlace(
				boxedConstant(function),
				rawFunction,
				primitive,
				arguments.elements,
				RewrittenCallSite(this, this@L2_INVOKE_CONSTANT_FUNCTION)))
		{
			return
		}
		+this@L2_INVOKE_CONSTANT_FUNCTION
	}

	/**
	 * The [L2CallSite] of an [L2_INVOKE_CONSTANT_FUNCTION] whose primitive is
	 * generating code in place of the call during regeneration.  The call's
	 * own operands say where each path goes: an answer produced in place is
	 * written to the call's [result], with the invoked function named as the
	 * one that produced it, and continues at its [ifReturn] target.  A
	 * function invoked in the primitive's place is called with the same
	 * targets, writing the same values.
	 *
	 * Each nested call's reification edge goes to the same target as the
	 * call's.  That target begins with an entry point, which must be reached
	 * by a single edge, but [L2Regenerator] gives each incoming edge its own
	 * copy of such a block.
	 *
	 * @property generator
	 *   The generator regenerating the graph.
	 * @property invoke
	 *   The call whose primitive is generating code in its place.
	 * @constructor
	 * Create the call site.
	 */
	private class RewrittenCallSite(
		override val generator: L2GeneratorInterface,
		val invoke: L2_INVOKE_CONSTANT_FUNCTION
	) : L2CallSite
	{
		override fun useAnswer(answer: L2ReadBoxedOperand) = generator.run {
			if (answer.type.isBottom)
			{
				// The VM says the answer cannot actually be produced.
				addUnreachableCode()
				return@run
			}
			move(answer.semanticValue(), invoke.result.semanticValues())
			invoke.returningFunction.elements.forEach { write ->
				moveConstant(
					invoke.constantFunction.constant, write.semanticValues())
			}
			jumpTo(invoke.ifReturn.targetBlock())
		}

		override fun generateGeneralFunctionInvocation(
			functionToCall: L2ReadBoxedOperand,
			tryToGenerateSpecialPrimitiveInvocation: Boolean,
			arguments: List<L2ReadBoxedOperand>,
			willAlwaysFailPrimitive: Boolean
		): Unit = generator.run {
			// The nested function's answer is the call's answer, so it must be
			// at least as strong as what the call promised.
			val returnType = functionToCall.type.returnType
			val promised = invoke.result.restriction()
			val restriction = when
			{
				returnType.isBottom -> promised
				else -> promised.intersectionWithType(returnType)
			}
			val result = boxedWrite(
				invoke.result.semanticValues(), restriction)
			val returningFunction = L2WriteBoxedVectorOperand(
				invoke.returningFunction.elements.map { write ->
					boxedWrite(write.semanticValues(), write.restriction())
				})
			val ifReturn = edgeTo(invoke.ifReturn.targetBlock())
			val ifReification = edgeTo(invoke.ifReification.targetBlock())
			val constant: A_Function? = functionToCall.constantOrNull
			val nested = when (constant)
			{
				null -> L2_INVOKE(
					functionToCall,
					L2ReadBoxedVectorOperand(arguments),
					result,
					returningFunction,
					ifReturn,
					ifReification)
				else -> L2_INVOKE_CONSTANT_FUNCTION(
					L2ConstantOperand(constant),
					L2ReadBoxedVectorOperand(arguments),
					result,
					returningFunction,
					ifReturn,
					ifReification)
			}
			when
			{
				tryToGenerateSpecialPrimitiveInvocation ->
					nested.run { emitTransformedInstruction() }
				else -> +nested
			}
		}

		override fun generateRestartContinuation(
			restartArguments: List<L2ReadBoxedOperand>
		): Unit = generator.generateRestartOfTopFrame(restartArguments)
	}


	override fun JVMTranslator.translateToJVM()
	{
		loadInterpreter()
		// :: interpreter
		loadInterpreter()
		// :: interpreter, interpreter
		load(Interpreter.chunkField)
		// :: interpreter, callingChunk
		loadInterpreter()
		// :: interpreter, callingChunk, interpreter
		load(Interpreter.interpreterFunctionField)
		// :: interpreter, callingChunk, callingFn
		loadInterpreter()
		// :: [interpreter, callingChunk, callingFn, interpreter
		loadLiteralObject(constantFunction.constant)
		// :: [interpreter, callingChunk, callingFn, interpreter, calledFn
		L2_INVOKE.run {
			generatePushArgumentsAndInvoke(
				arguments.elements,
				result,
				returningFunction,
				ifReturn,
				ifReification)
		}
	}
}
