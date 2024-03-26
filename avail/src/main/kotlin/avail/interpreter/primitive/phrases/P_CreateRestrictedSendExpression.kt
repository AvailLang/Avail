/*
 * P_CreateRestrictedSendExpression.kt
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

package avail.interpreter.primitive.phrases

import avail.AvailRuntime.Companion.currentRuntime
import avail.compiler.AvailAcceptedParseException
import avail.compiler.AvailRejectedParseException
import avail.compiler.splitter.MessageSplitter.Companion.possibleErrors
import avail.descriptor.atoms.A_Atom.Companion.bundleOrCreate
import avail.descriptor.bundles.A_Bundle
import avail.descriptor.bundles.A_Bundle.Companion.bundleMethod
import avail.descriptor.bundles.A_Bundle.Companion.messageSplitter
import avail.descriptor.fiber.A_Fiber.Companion.availLoader
import avail.descriptor.fiber.A_Fiber.Companion.heritableFiberGlobals
import avail.descriptor.fiber.A_Fiber.Companion.priority
import avail.descriptor.fiber.A_Fiber.Companion.setGeneralFlag
import avail.descriptor.fiber.A_Fiber.Companion.setSuccessAndFailure
import avail.descriptor.fiber.A_Fiber.Companion.textInterface
import avail.descriptor.fiber.FiberDescriptor.Companion.createFiber
import avail.descriptor.fiber.FiberDescriptor.Companion.currentFiber
import avail.descriptor.fiber.FiberDescriptor.GeneralFlag.CAN_REJECT_PARSE
import avail.descriptor.fiber.FiberDescriptor.GeneralFlag.IS_SEMANTIC_RESTRICTION
import avail.descriptor.functions.A_RawFunction
import avail.descriptor.methods.A_Method.Companion.filterByTypes
import avail.descriptor.methods.A_Method.Companion.semanticRestrictions
import avail.descriptor.methods.A_Sendable.Companion.bodySignature
import avail.descriptor.methods.A_Sendable.Companion.definitionModule
import avail.descriptor.module.A_Module.Companion.hasAncestor
import avail.descriptor.phrases.A_Phrase.Companion.expressionsTuple
import avail.descriptor.phrases.A_Phrase.Companion.phraseExpressionType
import avail.descriptor.phrases.ListPhraseDescriptor
import avail.descriptor.phrases.SendPhraseDescriptor
import avail.descriptor.phrases.SendPhraseDescriptor.Companion.newSendNode
import avail.descriptor.sets.A_Set.Companion.setUnionCanDestroy
import avail.descriptor.sets.SetDescriptor.Companion.set
import avail.descriptor.tuples.A_String
import avail.descriptor.tuples.A_String.Companion.asNativeString
import avail.descriptor.tuples.A_Tuple.Companion.tupleSize
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.tuples.StringDescriptor.Companion.stringFrom
import avail.descriptor.tuples.TupleDescriptor.Companion.emptyTuple
import avail.descriptor.types.A_Type
import avail.descriptor.types.A_Type.Companion.acceptsListOfArgValues
import avail.descriptor.types.A_Type.Companion.instance
import avail.descriptor.types.A_Type.Companion.returnType
import avail.descriptor.types.A_Type.Companion.typeAtIndex
import avail.descriptor.types.A_Type.Companion.typeIntersection
import avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.InstanceMetaDescriptor.Companion.topMeta
import avail.descriptor.types.PhraseTypeDescriptor.PhraseKind
import avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.LIST_PHRASE
import avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.SEND_PHRASE
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.ATOM
import avail.descriptor.types.TupleTypeDescriptor.Companion.stringType
import avail.descriptor.types.TupleTypeDescriptor.Companion.tupleTypeForTypes
import avail.descriptor.types.TupleTypeDescriptor.Companion.zeroOrOneOf
import avail.descriptor.types.TypeDescriptor
import avail.exceptions.AvailErrorCode
import avail.exceptions.AvailErrorCode.E_INCORRECT_NUMBER_OF_ARGUMENTS
import avail.exceptions.AvailErrorCode.E_LOADING_IS_OVER
import avail.exceptions.AvailErrorCode.E_NO_METHOD_DEFINITION
import avail.exceptions.MalformedMessageException
import avail.interpreter.Primitive
import avail.interpreter.Primitive.Flag.CanSuspend
import avail.interpreter.Primitive.Flag.Unknown
import avail.interpreter.execution.Interpreter
import avail.utility.Strings.increaseIndentation
import avail.utility.parallelDoThen
import avail.utility.safeWrite
import java.util.Collections.synchronizedList
import java.util.concurrent.locks.ReentrantReadWriteLock

/**
 * **Primitive CreateRestrictedSendExpression**: Create a
 * [send&#32;phrase][SendPhraseDescriptor] from the specified
 * [message&#32;bundle][A_Bundle], [list&#32;phrase][ListPhraseDescriptor] of
 * argument [expressions][PhraseKind.EXPRESSION_PHRASE], and
 * [return&#32;type][TypeDescriptor]. In addition, run all semantic restrictions
 * in separate fibers.
 *
 * The primitive succeeds with a tuple of size two containing:
 * 1. An empty tuple for failure, or a tuple of size one containing the phrase
 *    for success, with a return type restricted by the semantic restrictions,
 * 2. A string report organizing the output from the semantic restrictions that
 *    rejected the phrase.
 *
 * The primitive fails if the bundle name is malformed, or if the number of
 * arguments is incorrect, or if there are no method definitions applicable for
 * the supplied types.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
@Suppress("unused")
object P_CreateRestrictedSendExpression : Primitive(3, CanSuspend, Unknown)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(3)
		val messageName = interpreter.argument(0)
		val argsListPhrase = interpreter.argument(1)
		val returnType = interpreter.argument(2)

		val originalFiber = currentFiber()
		val loader = originalFiber.availLoader ?:
			return interpreter.primitiveFailure(E_LOADING_IS_OVER)
		val argExpressions = argsListPhrase.expressionsTuple
		val argsCount = argExpressions.tupleSize
		val bundle: A_Bundle
		try
		{
			bundle = messageName.bundleOrCreate()
			val splitter = bundle.messageSplitter
			if (splitter.numberOfArguments != argsCount)
			{
				return interpreter.primitiveFailure(
					E_INCORRECT_NUMBER_OF_ARGUMENTS)
			}
			if (!splitter.checkListStructure(argsListPhrase))
			{
				return interpreter.primitiveFailure(
					AvailErrorCode.E_INCONSISTENT_ARGUMENT_REORDERING)
			}
		}
		catch (e: MalformedMessageException)
		{
			return interpreter.primitiveFailure(e)
		}

		val argsTupleType = argsListPhrase.phraseExpressionType.makeShared()
		val argTypesList = (1 .. argsCount).map { index ->
			val argType = argsTupleType.typeAtIndex(index)
			argType
		}
		// Compute the intersection of the supplied type, applicable definition
		// return types, and semantic restriction types.  Start with the
		// supplied type.
		val module = loader.module
		var intersection: A_Type = returnType
		val intersectionLock = ReentrantReadWriteLock()
		// Merge in the applicable (and visible) definition return types.
		var anyDefinitionsApplicable = false
		for (definition in bundle.bundleMethod.filterByTypes(argTypesList))
		{
			val definitionModule = definition.definitionModule()
			if (definitionModule.isNil
				|| module.hasAncestor(definitionModule))
			{
				intersection = intersection.typeIntersection(
					definition.bodySignature().returnType)
				anyDefinitionsApplicable = true
			}
		}
		if (!anyDefinitionsApplicable)
		{
			return interpreter.primitiveFailure(E_NO_METHOD_DEFINITION)
		}
		// Note, the semantic restriction takes the *types* as arguments.
		val applicableRestrictions =
			bundle.bundleMethod.semanticRestrictions.filter {
				module.hasAncestor(it.definitionModule())
					&& it.function().kind().acceptsListOfArgValues(argTypesList)
			}
		val restrictionsSize = applicableRestrictions.size
		if (restrictionsSize == 0)
		{
			// No semantic restrictions.  Trivial success.
			return interpreter.primitiveSuccess(
				tuple(
					tuple(
						newSendNode(
							emptyTuple,
							emptyTuple,
							bundle,
							argsListPhrase,
							intersection)),
					emptyTuple))
		}

		// Merge in the (non-empty list of) semantic restriction results.
		val runtime = currentRuntime()
		return interpreter.suspendThen {
			val problems = synchronizedList(mutableListOf<A_String>())
			// Now launch the fibers.
			var fiberCount = 1
			applicableRestrictions.parallelDoThen(
				action = { restriction, after ->
					val finalCount = fiberCount++
					val forkedFiber = createFiber(
						topMeta,
						runtime,
						loader,
						originalFiber.textInterface,
						originalFiber.priority)
					{
						stringFrom(
							"Semantic restriction checker (#$finalCount/" +
								applicableRestrictions.size.toString() +
								") for primitive " +
								P_CreateRestrictedSendExpression.simpleName)
					}
					forkedFiber.setGeneralFlag(CAN_REJECT_PARSE)
					forkedFiber.setGeneralFlag(IS_SEMANTIC_RESTRICTION)
					forkedFiber.heritableFiberGlobals =
						originalFiber.heritableFiberGlobals
					forkedFiber.setSuccessAndFailure(
						onSuccess = {
							when
							{
								it.isType -> intersectionLock.safeWrite {
									intersection =
										intersection.typeIntersection(it)
								}
								else -> problems.add(
									stringFrom(
										"Semantic restriction failed to " +
											"produce a type, and instead " +
											"produced: $it"))
							}
							after()
						},
						onFailure = { throwable ->
							when (throwable)
							{
								is AvailRejectedParseException ->
								{
									problems.add(throwable.rejectionString)
								}
								is AvailAcceptedParseException ->
								{
									// Success, but no type narrowing.  Do
									// nothing.
								}
								else -> problems.add(
									stringFrom(
										"evaluation of macro body not to " +
											"raise an unhandled " +
											"exception:\n\t$throwable"))
							}
							after()
						})
					runtime.runOutermostFunction(
						forkedFiber,
						restriction.function(),
						argTypesList,
						false)
				},
				then = {
					when
					{
						problems.isEmpty() ->
							// We're last to report, and there were no errors.
							succeed(
								tuple(
									tuple(
										newSendNode(
											emptyTuple,
											emptyTuple,
											bundle,
											argsListPhrase,
											intersection)),
									emptyTuple))
						else ->
							// There were problems.
							// Succeed with <<>, "report...">.
							succeed(
								tuple(
									emptyTuple,
									collectProblemReport(problems)))
					}
				})
		}
	}

	/**
	 * Assemble a report of these problems in a single string.
	 */
	private fun collectProblemReport(problems: List<A_String>): A_String =
		when (problems.size)
		{
			1 -> problems[0]
			else -> stringFrom(
				buildString {
					append(
						"send phrase creation primitive not to have " +
							"encountered multiple problems in semantic " +
							"restrictions:")
					for (problem in problems)
					{
						append("\n\t")
						append(increaseIndentation(problem.asNativeString(), 1))
					}
				})
		}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(
				ATOM.o,
				LIST_PHRASE.mostGeneralType,
				topMeta),
			tupleTypeForTypes(
				zeroOrOneOf(SEND_PHRASE.mostGeneralType),
				stringType))

	override fun returnTypeGuaranteedByVM(
		rawFunction: A_RawFunction,
		argumentTypes: List<A_Type>): A_Type
	{
		assert(argumentTypes.size == 3)
//		val messageNameType: A_Type = argumentTypes[0]
//		val argsListNodeType: A_Type = argumentTypes[1]
		val returnTypeType = argumentTypes[2]

		val returnType = returnTypeType.instance
		return tupleTypeForTypes(
			zeroOrOneOf(SEND_PHRASE.create(returnType)),
			stringType)
	}

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(
			set(
				E_INCORRECT_NUMBER_OF_ARGUMENTS,
				E_NO_METHOD_DEFINITION,
				E_LOADING_IS_OVER
			).setUnionCanDestroy(possibleErrors, true))
}
