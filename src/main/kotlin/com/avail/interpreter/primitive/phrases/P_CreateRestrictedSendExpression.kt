/*
 * P_CreateRestrictedSendExpression.kt
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

package com.avail.interpreter.primitive.phrases

import com.avail.AvailRuntime.currentRuntime
import com.avail.compiler.AvailAcceptedParseException
import com.avail.compiler.AvailRejectedParseException
import com.avail.compiler.splitter.MessageSplitter.Companion.possibleErrors
import com.avail.descriptor.atoms.A_Atom.Companion.bundleOrCreate
import com.avail.descriptor.bundles.A_Bundle
import com.avail.descriptor.bundles.A_Bundle.Companion.bundleMethod
import com.avail.descriptor.bundles.A_Bundle.Companion.messageSplitter
import com.avail.descriptor.fiber.FiberDescriptor.Companion.currentFiber
import com.avail.descriptor.fiber.FiberDescriptor.Companion.newFiber
import com.avail.descriptor.fiber.FiberDescriptor.Companion.setSuccessAndFailure
import com.avail.descriptor.functions.A_RawFunction
import com.avail.descriptor.phrases.A_Phrase.Companion.expressionsTuple
import com.avail.descriptor.phrases.ListPhraseDescriptor
import com.avail.descriptor.phrases.SendPhraseDescriptor
import com.avail.descriptor.phrases.SendPhraseDescriptor.Companion.newSendNode
import com.avail.descriptor.representation.AvailObject
import com.avail.descriptor.sets.SetDescriptor.Companion.set
import com.avail.descriptor.tuples.A_String
import com.avail.descriptor.tuples.ObjectTupleDescriptor.tuple
import com.avail.descriptor.tuples.StringDescriptor.Companion.stringFrom
import com.avail.descriptor.tuples.TupleDescriptor.emptyTuple
import com.avail.descriptor.tuples.TupleDescriptor.toList
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.AbstractEnumerationTypeDescriptor.enumerationWith
import com.avail.descriptor.types.FunctionTypeDescriptor.functionType
import com.avail.descriptor.types.InstanceMetaDescriptor.topMeta
import com.avail.descriptor.types.PhraseTypeDescriptor.PhraseKind
import com.avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.LIST_PHRASE
import com.avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.SEND_PHRASE
import com.avail.descriptor.types.TypeDescriptor
import com.avail.descriptor.types.TypeDescriptor.Types.ATOM
import com.avail.exceptions.AvailErrorCode.E_INCORRECT_ARGUMENT_TYPE
import com.avail.exceptions.AvailErrorCode.E_INCORRECT_NUMBER_OF_ARGUMENTS
import com.avail.exceptions.AvailErrorCode.E_LOADING_IS_OVER
import com.avail.exceptions.AvailErrorCode.E_NO_METHOD_DEFINITION
import com.avail.exceptions.MalformedMessageException
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Flag.CanSuspend
import com.avail.interpreter.Primitive.Flag.Unknown
import com.avail.interpreter.execution.Interpreter
import com.avail.interpreter.execution.Interpreter.Companion.runOutermostFunction
import com.avail.utility.Strings.increaseIndentation
import java.util.concurrent.atomic.AtomicInteger

/**
 * **Primitive CreateRestrictedSendExpression**: Create a
 * [send&#32;phrase][SendPhraseDescriptor] from the specified
 * [message&#32;bundle][A_Bundle], [list&#32;phrase][ListPhraseDescriptor] of
 * argument [expressions][PhraseKind.EXPRESSION_PHRASE], and
 * [return&#32;type][TypeDescriptor]. In addition, run all semantic restrictions
 * in separate fibers.  The resulting send phrase's return type will be the
 * intersection of the supplied type, the return types produced by the semantic
 * restrictions, and the applicable method definitions' return types.
 *
 * In the event that one or more semantic restrictions should fail, their
 * failure reasons will be captured and combined into a suitable composite
 * string.  This primitive will then fail with the composite string as the
 * failure value.  It is expected that the Avail primitive failure code will
 * simply invoke [P_RejectParsing] with that string to report the encountered
 * problems within the original fiber.
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
		val loader = originalFiber.availLoader() ?:
			return interpreter.primitiveFailure(E_LOADING_IS_OVER)
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
		val allVisibleModules = loader.module().allAncestors()
		var intersection: A_Type = returnType
		val intersectionMonitor = Object()
		// Merge in the applicable (and visible) definition return types.
		var anyDefinitionsApplicable = false
		for (definition in bundle.bundleMethod().filterByTypes(argTypesList))
		{
			val definitionModule = definition.definitionModule()
			if (definitionModule.equalsNil()
				|| allVisibleModules.hasElement(definitionModule))
			{
				intersection = intersection.typeIntersection(
					definition.bodySignature().returnType())
				anyDefinitionsApplicable = true
			}
		}
		if (!anyDefinitionsApplicable)
		{
			return interpreter.primitiveFailure(E_NO_METHOD_DEFINITION)
		}
		// Note, the semantic restriction takes the *types* as arguments.
		val applicableRestrictions =
			bundle.bundleMethod().semanticRestrictions().asSequence()
				.filter {
					allVisibleModules.hasElement(it.definitionModule()) &&
						it.function().kind().acceptsListOfArgValues(
							argTypesList)
				}
				.toList()
		val restrictionsSize = applicableRestrictions.size
		if (restrictionsSize == 0)
		{
			// No semantic restrictions.  Trivial success.
			return interpreter.primitiveSuccess(
				newSendNode(emptyTuple(), bundle, argsListNode, intersection))
		}

		// Merge in the (non-empty list of) semantic restriction results.
		val runtime = currentRuntime()
		val primitiveFunction = interpreter.function!!
		assert(primitiveFunction.code().primitive() === this)
		return interpreter.suspendThen {
			val countdown = AtomicInteger(restrictionsSize)
			val problems = mutableListOf<A_String>()
			val decrement = decrement@{
				when {
					countdown.decrementAndGet() != 0 ->
						// We're not last to decrement.
						return@decrement
					problems.isEmpty() ->
						// We're last to report.  Either succeed or fail the
						// primitive within the original fiber.
						succeed(
							newSendNode(
								emptyTuple(),
								bundle,
								argsListNode,
								intersection))
					else -> {
						// There were problems.  Fail the primitive with a
						// string describing them all.
						@Suppress("UNUSED_VARIABLE")
						val problemReport: A_String = when (problems.size) {
							1 -> problems[0]
							else -> stringFrom(
								buildString {
									append(
										"send phrase creation primitive not " +
											"to have encountered multiple " +
											"problems in semantic " +
											"restrictions:")
									for (problem in problems) {
										append("\n\t")
										append(
											increaseIndentation(
												problem.asNativeString(), 1))
									}
								})
						}
						// TODO: Yeah, we went to the effort of assembling a
						// pretty report about what went wrong, but the
						// bootstrap logic can't deal with anything but numeric
						// codes, so just report a basic failure.
						fail(E_INCORRECT_ARGUMENT_TYPE)
					}
				}
			}
			val success: (AvailObject) -> Unit = {
				when {
					it.isType -> synchronized(intersectionMonitor) {
						intersection = intersection.typeIntersection(it)
					}
					else -> synchronized(problems) {
						problems.add(
							stringFrom(
								"Semantic restriction failed to produce "
									+ "a type, and instead produced: $it"))
					}
				}
				decrement.invoke()
			}
			// Now launch the fibers.
			var fiberCount = 1
			for (restriction in applicableRestrictions) {
				val finalCount = fiberCount++
				val forkedFiber = newFiber(topMeta(), originalFiber.priority()) {
					stringFrom(
						"Semantic restriction checker (#$finalCount) " +
							"for primitive ${this.javaClass.simpleName}")
				}
				forkedFiber.setAvailLoader(loader)
				forkedFiber.setHeritableFiberGlobals(
					originalFiber.heritableFiberGlobals())
				forkedFiber.setTextInterface(originalFiber.textInterface())
				forkedFiber.setSuccessAndFailure(
					success,
					{ throwable ->
						when (throwable) {
							is AvailRejectedParseException -> {
								// Compute the rejectionString outside the mutex.
								val string = throwable.rejectionString
								synchronized(problems) {
									problems.add(string)
								}
							}
							is AvailAcceptedParseException -> {
								// Success without type narrowing – do nothing.
							}
							else ->
								synchronized(problems) {
									problems.add(stringFrom(
										"evaluation of macro body not to " +
											"raise an unhandled " +
											"exception:\n\t$throwable"))
								}
						}
					})
				runOutermostFunction(
					runtime, forkedFiber, restriction.function(), argTypesList)
			}
		}
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
		// final A_Type messageNameType = argumentTypes.get(0);
		// final A_Type argsListNodeType = argumentTypes.get(1);
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
