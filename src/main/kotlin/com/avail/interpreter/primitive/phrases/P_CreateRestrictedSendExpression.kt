/*
 * P_CreateRestrictedSendExpression.java
 * Copyright © 1993-2018, The Avail Foundation, LLC.
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

package com.avail.interpreter.primitive.phrases

import com.avail.AvailRuntime.currentRuntime
import com.avail.compiler.AvailAcceptedParseException
import com.avail.compiler.AvailRejectedParseException
import com.avail.compiler.splitter.MessageSplitter.Companion.possibleErrors
import com.avail.descriptor.*
import com.avail.descriptor.AbstractEnumerationTypeDescriptor.enumerationWith
import com.avail.descriptor.FiberDescriptor.currentFiber
import com.avail.descriptor.FiberDescriptor.newFiber
import com.avail.descriptor.FunctionTypeDescriptor.functionType
import com.avail.descriptor.InstanceMetaDescriptor.topMeta
import com.avail.descriptor.ObjectTupleDescriptor.tuple
import com.avail.descriptor.PhraseTypeDescriptor.PhraseKind
import com.avail.descriptor.PhraseTypeDescriptor.PhraseKind.LIST_PHRASE
import com.avail.descriptor.PhraseTypeDescriptor.PhraseKind.SEND_PHRASE
import com.avail.descriptor.SendPhraseDescriptor.newSendNode
import com.avail.descriptor.SetDescriptor.set
import com.avail.descriptor.StringDescriptor.stringFrom
import com.avail.descriptor.TupleDescriptor.emptyTuple
import com.avail.descriptor.TupleDescriptor.toList
import com.avail.descriptor.TypeDescriptor.Types.ATOM
import com.avail.exceptions.AvailErrorCode.*
import com.avail.exceptions.MalformedMessageException
import com.avail.interpreter.Interpreter
import com.avail.interpreter.Interpreter.*
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Flag.CanSuspend
import com.avail.interpreter.Primitive.Flag.Unknown
import com.avail.interpreter.Primitive.Result.FIBER_SUSPENDED
import com.avail.utility.Mutable
import com.avail.utility.Nulls.stripNull
import com.avail.utility.Strings.increaseIndentation
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

/**
 * **Primitive CreateRestrictedSendExpression**: Create a [send
* phrase][SendPhraseDescriptor] from the specified [message bundle][A_Bundle],
* [list phrase][ListPhraseDescriptor] of [argument
* expressions][PhraseKind.EXPRESSION_PHRASE], and [return type][TypeDescriptor].
* In addition, run all semantic restrictions in separate fibers.  The resulting
* send phrase's return type will be the intersection of the supplied type, the
* return types produced by the semantic restrictions, and the applicable method
* definitions' return types.
 *
 *
 * In the event that one or more semantic restrictions should fail, their
 * failure reasons will be captured and combined into a suitable composite
 * string.  This primitive will then fail with the composite string as the
 * failure value.  It is expected that the Avail primitive failure code will
 * simply invoke [P_RejectParsing] with that string to report the
 * encountered problems within the original fiber.
 *
 *
 * The primitive may also fail (with a suitable string) if the number of
 * arguments is incorrect, but no further checking is performed.  If there are
 * no applicable method definitions for the supplied types, they will simply not
 * contribute to the strengthened return type.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
object P_CreateRestrictedSendExpression : Primitive(3, CanSuspend, Unknown)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(3)
		val messageName = interpreter.argument(0)
		val argsListNode = interpreter.argument(1)
		val returnType = interpreter.argument(2)

		val originalFiber = currentFiber()
		val loader = originalFiber.availLoader()
		             ?: return interpreter.primitiveFailure(E_LOADING_IS_OVER)
		val argExpressions = argsListNode.expressionsTuple()
		val argsCount = argExpressions.tupleSize()
		val bundle: A_Bundle
		try
		{
			bundle = messageName.bundleOrCreate()
			val splitter = bundle.messageSplitter()
			if (splitter.numberOfArguments != argsCount)
			{
				return interpreter.primitiveFailure(
					E_INCORRECT_NUMBER_OF_ARGUMENTS)
			}
		}
		catch (e: MalformedMessageException)
		{
			return interpreter.primitiveFailure(e)
		}

		val argsTupleType = argsListNode.expressionType()
		val argTypesTuple = argsTupleType.tupleOfTypesFromTo(1, argsCount)
		val argTypesList = toList<A_Type>(argTypesTuple)
		// Compute the intersection of the supplied type, applicable definition
		// return types, and semantic restriction types.  Start with the
		// supplied type.
		val currentModule = loader.module()
		val allVisibleModules = currentModule.allAncestors()
		val intersection = Mutable<A_Type>(returnType)
		// Merge in the applicable (and visible) definition return types.
		var anyDefinitionsApplicable = false
		for (definition in bundle.bundleMethod().filterByTypes(argTypesList))
		{
			val definitionModule = definition.definitionModule()
			if (definitionModule.equalsNil() || allVisibleModules.hasElement(definitionModule))
			{
				intersection.value = intersection.value.typeIntersection(
					definition.bodySignature().returnType())
				anyDefinitionsApplicable = true
			}
		}
		if (!anyDefinitionsApplicable)
		{
			return interpreter.primitiveFailure(E_NO_METHOD_DEFINITION)
		}
		val applicableRestrictions = ArrayList<A_SemanticRestriction>()
		for (restriction in bundle.bundleMethod().semanticRestrictions())
		{
			if (allVisibleModules.hasElement(restriction.definitionModule()))
			{
				// The semantic restriction takes the *types* as arguments.
				if (restriction.function().kind().acceptsListOfArgValues(
						argTypesList))
				{
					applicableRestrictions.add(restriction)
				}
			}
		}
		val restrictionsSize = applicableRestrictions.size
		if (restrictionsSize == 0)
		{
			// No semantic restrictions.  Trivial success.
			return interpreter.primitiveSuccess(
				newSendNode(
					emptyTuple(), bundle, argsListNode, intersection.value))
		}

		// Merge in the (non-empty list of) semantic restriction results.
		val runtime = currentRuntime()
		val primitiveFunction = stripNull(interpreter.function)
		assert(primitiveFunction.code().primitive() === this)
		interpreter.primitiveSuspend(primitiveFunction)
		val copiedArgs = ArrayList(interpreter.argsBuffer)
		val countdown = AtomicInteger(restrictionsSize)
		val problems = ArrayList<A_String>()
		val decrement = decrement@ {
			if (countdown.decrementAndGet() != 0)
			{
				// We're not last to decrement, so don't do the epilogue.
				return@decrement
			}
			// We're last to report.  Either succeed or fail the
			// primitive within the original fiber.
			if (problems.isEmpty())
			{
				// There were no problems.  Succeed the primitive with a
				// send phrase yielding the intersection type.
				resumeFromSuccessfulPrimitive(
					runtime,
					originalFiber,
					this,
					newSendNode(
						emptyTuple(),
						bundle,
						argsListNode,
						intersection.value))
			}
			else
			{
				// There were problems.  Fail the primitive with a string
				// describing them all.
				// assert problems.size() > 0;
				val builder = StringBuilder()
				if (problems.size == 1)
				{
					builder.append(problems[0].asNativeString())
				}
				else
				{
					builder.append(
						"send phrase creation primitive not to have "
						+ "encountered multiple problems in semantic "
						+ "restrictions:")
					for (problem in problems)
					{
						builder.append("\n\t")
						builder.append(
							increaseIndentation(problem.asNativeString(), 1))
					}
				}
				val problemReport = stringFrom(builder.toString())
				// TODO: Yeah, we went to the effort of assembling a pretty
				// report about what went wrong, but the bootstrap logic can't
				// deal with anything but numeric codes, so just report a basic
				// failure.
				resumeFromFailedPrimitive(
					runtime,
					originalFiber,
					//					problemReport,
					E_INCORRECT_ARGUMENT_TYPE.numericCode(), // Ew, yuck.
					primitiveFunction,
					copiedArgs)
			}
		}
		val success = { resultType: AvailObject ->
			if (resultType.isType)
			{
				synchronized(intersection) {
					intersection.value =
						intersection.value.typeIntersection(resultType)
				}
			}
			else
			{
				synchronized(problems) {
					problems.add(
						stringFrom(
							"Semantic restriction failed to produce "
							+ "a type, and instead produced: "
							+ resultType))
				}
			}
			decrement.invoke()
		}
		var fiberCount = 1
		for (restriction in applicableRestrictions)
		{
			val finalCount = fiberCount++
			val forkedFiber = newFiber(
				topMeta(),
				originalFiber.priority()
			) {
				stringFrom(
					"Semantic restriction checker (#"
					+ finalCount
					+ ") for primitive "
					+ this.javaClass.simpleName)
			}
			forkedFiber.availLoader(originalFiber.availLoader())
			forkedFiber.heritableFiberGlobals(
				originalFiber.heritableFiberGlobals())
			forkedFiber.textInterface(originalFiber.textInterface())
			forkedFiber.setSuccessAndFailureContinuations(
				success,
				{ throwable ->
					if (throwable is AvailRejectedParseException)
					{
						val string = throwable.rejectionString
						synchronized(problems) {
							problems.add(string)
						}
					}
					else if (throwable !is AvailAcceptedParseException)
					{
						synchronized(problems) {
							problems.add(stringFrom(
								"evaluation of macro body not to raise an "
								+ "unhandled exception:\n\t"
								+ throwable))
						}
					}
					// Success without type narrowing – do nothing.
					// Now that we've fully dealt with it,
				})
			runOutermostFunction(
				runtime, forkedFiber, restriction.function(), argTypesList)
		}
		return FIBER_SUSPENDED
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(
				ATOM.o(),
				LIST_PHRASE.mostGeneralType(),
				topMeta()),
			SEND_PHRASE.mostGeneralType())

	override fun returnTypeGuaranteedByVM(
		rawFunction: A_RawFunction,
		argumentTypes: List<A_Type>): A_Type
	{
		assert(argumentTypes.size == 3)
		//		final A_Type messageNameType = argumentTypes.get(0);
		//		final A_Type argsListNodeType = argumentTypes.get(1);
		val returnTypeType = argumentTypes[2]

		val returnType = returnTypeType.instance()
		return SEND_PHRASE.create(returnType)
	}

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(
			set(
				E_INCORRECT_NUMBER_OF_ARGUMENTS,
				E_NO_METHOD_DEFINITION,
				E_LOADING_IS_OVER
			).setUnionCanDestroy(possibleErrors, true))
}