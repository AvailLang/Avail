/*
 * P_SimpleMethodStabilityHelper.kt
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
package avail.interpreter.primitive.methods

import avail.descriptor.fiber.A_Fiber
import avail.descriptor.fiber.A_Fiber.Companion.availLoader
import avail.descriptor.fiber.A_Fiber.Companion.priority
import avail.descriptor.fiber.A_Fiber.Companion.setSuccessAndFailure
import avail.descriptor.fiber.A_Fiber.Companion.textInterface
import avail.descriptor.fiber.FiberDescriptor.Companion.createFiber
import avail.descriptor.functions.A_Function
import avail.descriptor.functions.A_RawFunction
import avail.descriptor.functions.A_RawFunction.Companion.codeStartingLineNumber
import avail.descriptor.functions.A_RawFunction.Companion.methodName
import avail.descriptor.functions.A_RawFunction.Companion.module
import avail.descriptor.functions.A_RawFunction.Companion.numArgs
import avail.descriptor.functions.FunctionDescriptor.Companion.createFunction
import avail.descriptor.methods.MethodDescriptor.SpecialMethodAtom
import avail.descriptor.representation.NilDescriptor.Companion.nil
import avail.descriptor.sets.SetDescriptor.Companion.set
import avail.descriptor.sets.SetDescriptor.Companion.setFromCollection
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tupleFromList
import avail.descriptor.tuples.StringDescriptor.Companion.stringFrom
import avail.descriptor.tuples.TupleDescriptor.Companion.emptyTuple
import avail.descriptor.types.A_Type
import avail.descriptor.types.A_Type.Companion.argsTupleType
import avail.descriptor.types.A_Type.Companion.instances
import avail.descriptor.types.A_Type.Companion.returnType
import avail.descriptor.types.A_Type.Companion.tupleOfTypesFromTo
import avail.descriptor.types.AbstractEnumerationTypeDescriptor
import avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import avail.descriptor.types.BottomTypeDescriptor.Companion.bottom
import avail.descriptor.types.InstanceMetaDescriptor.Companion.instanceMeta
import avail.descriptor.types.InstanceMetaDescriptor.Companion.topMeta
import avail.descriptor.types.InstanceTypeDescriptor.Companion.instanceType
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.ANY
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.TOP
import avail.descriptor.types.VariableTypeDescriptor.Companion.variableTypeFor
import avail.exceptions.AvailErrorCode.E_LOADING_IS_OVER
import avail.interpreter.Primitive
import avail.interpreter.Primitive.Flag.CanFold
import avail.interpreter.Primitive.Flag.CanSuspend
import avail.interpreter.Primitive.Flag.Private
import avail.interpreter.execution.Interpreter
import avail.interpreter.levelOne.L1InstructionWriter
import avail.interpreter.levelOne.L1Operation
import avail.interpreter.primitive.numbers.P_Addition
import avail.utility.cartesianProductForEach
import avail.utility.parallelMapThen
import kotlin.math.min

/**
 * **Primitive:** This private primitive, which we will call the "restriction
 * primitive", acts as the body of a semantic restriction that gets generated
 * any time a method definition is added where the body function is some
 * primitive that can be [folded][CanFold] (which we'll call the "foldable
 * primitive" to distinguish it from the restriction primitive). The signature
 * of the semantic restriction body is arranged to accept subtypes of the
 * argument types of the foldable primitive function – which are already
 * constrained by the foldable primitive itself.
 *
 * For example, the addition primitive [P_Addition], which has the [CanFold]
 * flag, can be defined on `<number, number>`, or any two subtypes of `number`.
 * The function using this restriction primitive, created to be used as the body
 * of a semantic restriction, will receive the two subtypes of `number` at some
 * call site.  If they are both
 * [enumerations][AbstractEnumerationTypeDescriptor], but not metatypes, it will
 * proceed to launch a fiber to evaluate the [P_Addition] function body for each
 * combination of inputs, collecting the results into an enumeration that is
 * returned from the semantic restriction, thereby strengthening the call site.
 * The evaluation of each combination of inputs occurs in its own [A_Fiber],
 * launched by the restriction primitive.  That allows a primitive failure to be
 * caught easily (and leading to this semantic restriction to give up and
 * produce ⊤) by the fiber success/failure mechanism itself, without the need
 * for exceptions to have been bootstrapped.  In the event that not all of the
 * input types are non-meta enumerations, the restriction primitive immediately
 * answers ⊤, to indicate no restriction has taken place.
 *
 * When the restriction function is created, a corresponding function using the
 * foldable primitive is created, with a suitable failure case if needed.  This
 * function is embedded as the first outer variable of the function, and is
 * directly invoked by the forked fibers.  By avoiding a method lookup here, it
 * won't accidentally look up a more specific method body.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
@Suppress("unused")
object P_SimpleMethodStabilityHelper : Primitive(
	-1, Private, CanSuspend)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		val originalFiber = interpreter.fiber()
		val loader = originalFiber.availLoader
		if (loader === null || loader.module.isNil)
		{
			return interpreter.primitiveFailure(E_LOADING_IS_OVER)
		}
		val runtime = interpreter.runtime

		// The arguments are the types at a call site for some foldable
		// primitive.  Only strengthen the call site (by returning a type other
		// than ⊤) if each argument is a non-meta enumeration.
		val arguments = interpreter.argsBuffer.toList()
		if (arguments.any { !it.isEnumeration || it.isInstanceMeta })
		{
			return interpreter.primitiveSuccess(TOP.o)
		}

		val enumerations = arguments.map { it.instances.makeShared().toList() }
		val combinationCount = enumerations.fold(1L) { product, list ->
			min(product * list.size.toLong(), Int.MAX_VALUE.toLong())
		}.toInt()
		if (combinationCount == 0)
		{
			// At least one of the arguments to this semantic restriction body
			// was ⊥.  It's unclear what leads to this, but simply answer ⊥.
			return interpreter.primitiveSuccess(bottom)
		}
		assert(combinationCount > 0)
		// The resulting combination of input types seems too expensive to
		// compute here.
		if (combinationCount > MAXIMUM_ENUMERATION_COMBINATIONS)
			return interpreter.primitiveSuccess(TOP.o)

		// The sole outer is the function to invoke with each combination of
		// enumerated arguments.
		val thisFunction = interpreter.function!!
		val functionToInvoke: A_Function = thisFunction.outerVarAt(1)
		val allCombinations = mutableListOf<List<A_Type>>()
		enumerations.cartesianProductForEach(allCombinations::add)

		return interpreter.suspendThen {
			allCombinations.parallelMapThen(
				action = { combination, afterEach: (A_Type?)->Unit ->
					val fiber = createFiber(
						ANY.o,
						loader.runtime,
						loader,
						originalFiber.textInterface,
						originalFiber.priority)
					{
						val prim = functionToInvoke.code().codePrimitive()!!
						stringFrom(
							"Primitive stability evaluation " +
								"for $prim $combination")
					}
					fiber.setSuccessAndFailure(
						onSuccess = afterEach,
						onFailure = {
							afterEach(null)
						})
					runtime.runOutermostFunction(
						fiber, functionToInvoke, combination)
				},
				then = { list ->
					if (list.any { it === null })
					{
						// The primitive failed for at least one combination, so
						// play it safe and have the semantic restriction answer
						// ⊤.
						succeed(TOP.o)
					}
					else
					{
						succeed(
							enumerationWith(
								setFromCollection(list.filterNotNull())))
					}
				}
			)
		}
	}

	/**
	 * Create an [A_Function] to use as a semantic restriction body, based on
	 * the given method body function which must be a [foldable][CanFold]
	 * primitive function.  The semantic restriction function should invoke the
	 * receiver primitive ([P_SimpleMethodStabilityHelper]) to produce the
	 * narrowed return type at a call site.
	 *
	 * @param methodBodyFunction
	 *    The [foldable][CanFold] primitive [A_Function] for which a stability
	 *    semantic restriction is to be created.
	 * @return
	 *    An [A_Function] to use as a semantic restriction body.
	 */
	fun createRestrictionBody(
		methodBodyFunction: A_Function
	): A_Function
	{
		val methodBodyCode = methodBodyFunction.code()
		val methodBodyPrimitive = methodBodyCode.codePrimitive()
		assert(methodBodyPrimitive!!.hasFlag(CanFold))
		val numArgs = methodBodyCode.numArgs()
		val methodArgumentTypes = methodBodyCode.functionType().argsTupleType
			.tupleOfTypesFromTo(1, numArgs)
		val metaTypes = methodArgumentTypes.map(::instanceMeta)

		// Create an alternative function to invoke that cleanly terminates the
		// current fiber if the primitive fails.
		val evaluationCode = L1InstructionWriter(
			methodBodyCode.module,
			methodBodyCode.codeStartingLineNumber,
			nil
		).run {
			primitive = methodBodyPrimitive
			argumentTypesTuple(methodArgumentTypes)
			returnType = methodBodyCode.functionType().returnType
			returnTypeIfPrimitiveFails = bottom
			if (!methodBodyPrimitive.hasFlag(Flag.CannotFail))
			{
				// Produce failure code that simply terminates the fiber.
				createLocal(
					variableTypeFor(methodBodyPrimitive.failureVariableType))
				write(
					methodBodyCode.codeStartingLineNumber,
					L1Operation.L1_doCall,
					addLiteral(
						SpecialMethodAtom.TERMINATE_CURRENT_FIBER.bundle),
					addLiteral(bottom))
			}
			compiledCode()
		}
		evaluationCode.methodName = stringFrom(
			"«generated primitive for use by semantic restriction»")
		val evaluationFunction = createFunction(evaluationCode, emptyTuple)

		// Now create the primitive body function for the semantic restriction.
		val code = L1InstructionWriter(
			methodBodyCode.module,
			methodBodyCode.codeStartingLineNumber,
			nil
		).run {
			primitive = P_SimpleMethodStabilityHelper
			val outerIndex = createOuter(instanceType(evaluationFunction))
			assert(outerIndex == 1)
			argumentTypesTuple(tupleFromList(metaTypes))
			returnType = topMeta()
			returnTypeIfPrimitiveFails = topMeta()
			writeDefaultFailureCode(
				methodBodyCode.codeStartingLineNumber, this, numArgs)
			compiledCode()
		}
		code.methodName = stringFrom(
			"«generated stability semantic restriction»")
		return createFunction(code, tuple(evaluationFunction))
	}

	/**
	 * When the number of combinations of values taken from the input
	 * enumerations is less than or equal to this threshold, compute the return
	 * results for each combination at the call site.  Otherwise don't restrict
	 * it beyond what the primitive method indicates in its signature.
	 */
	private const val MAXIMUM_ENUMERATION_COMBINATIONS = 1000

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(
			set(E_LOADING_IS_OVER))

	override fun privateBlockTypeRestriction(): A_Type = bottom

	override fun returnTypeGuaranteedByVM(
		rawFunction: A_RawFunction,
		argumentTypes: List<A_Type>
	): A_Type = topMeta()
}
