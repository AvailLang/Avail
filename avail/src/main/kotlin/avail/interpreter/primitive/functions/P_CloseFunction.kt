/*
 * P_CloseFunction.kt
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
package avail.interpreter.primitive.functions

import avail.descriptor.functions.FunctionDescriptor
import avail.descriptor.functions.FunctionDescriptor.Companion.createFunction
import avail.descriptor.phrases.LiteralPhraseDescriptor
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.A_Number.Companion.equalsInt
import avail.descriptor.representation.A_RawFunction
import avail.descriptor.representation.A_RawFunction.Companion.declarationNames
import avail.descriptor.representation.A_RawFunction.Companion.numOuters
import avail.descriptor.representation.A_String.Companion.asNativeString
import avail.descriptor.representation.A_Tuple.Companion.copyTupleFromToCanDestroy
import avail.descriptor.representation.A_Tuple.Companion.tupleSize
import avail.descriptor.representation.A_Type
import avail.descriptor.representation.A_Type.Companion.instance
import avail.descriptor.representation.A_Type.Companion.instanceCount
import avail.descriptor.representation.AvailObject
import avail.descriptor.representation.NilDescriptor.Companion.nil
import avail.descriptor.sets.SetDescriptor.Companion.emptySet
import avail.descriptor.tokens.LiteralTokenDescriptor.Companion.literalToken
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tupleFromList
import avail.descriptor.tuples.StringDescriptor.Companion.stringFrom
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionTypeFromArgumentTupleType
import avail.descriptor.types.FunctionTypeDescriptor.Companion.mostGeneralFunctionType
import avail.descriptor.types.TupleTypeDescriptor.Companion.mostGeneralTupleType
import avail.interpreter.levelOne.L1Decompiler
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedVectorOperand
import avail.interpreter.levelTwo.operand.L2WriteBoxedOperand
import avail.interpreter.levelTwo.operand.TypeRestriction
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.restrictionForType
import avail.interpreter.levelTwo.operation.L2_RUN_INFALLIBLE_PRIMITIVE
import avail.interpreter.primitive.Foldable
import avail.interpreter.primitive.Primitive.Flag.CanInline
import avail.interpreter.primitive.Primitive.Flag.CannotFail
import avail.interpreter.primitive.Primitive.Flag.Private
import avail.interpreter.primitive.PrimitiveContext
import avail.interpreter.primitive.PrimitiveIsolatedN
import avail.optimizer.jvm.JVMTranslator
import avail.optimizer.manifest.L2ValueManifest
import avail.optimizer.values.L2SemanticPrimitiveInvocation
import avail.optimizer.values.L2SemanticValue
import avail.utility.Strings.increaseIndentation
import avail.utility.Strings.newlineTab
import org.objectweb.asm.Opcodes

/**
 * **Primitive:** Close a [raw&#32;function][A_RawFunction] over the given
 * outer values, answering a new [function][FunctionDescriptor].  The first
 * argument is the raw function, and each remaining argument is the value of
 * the corresponding outer, so the number of arguments is one more than the raw
 * function's [numOuters].  It cannot fail.
 *
 * This is how the optimizer represents closing a function, both as the
 * [L2_RUN_INFALLIBLE_PRIMITIVE] that creates it and as the
 * [L2SemanticPrimitiveInvocation] that names the result.  Because the result
 * is named by its raw function and outers, the raw function of any closure is
 * known wherever the closure is, and two closings of the same raw function
 * over the same outers are the same value, as function equality says they
 * are.  When every argument is constant, the closing folds to a constant
 * function.
 *
 * Unlike [P_CreateFunction], which takes the outers as a tuple and checks
 * them, it is not available to Avail code: only the optimizer produces it,
 * from an L1 close instruction whose outers the compiler has already checked.
 *
 * It keeps the default [canDestroyArguments], since the function it makes
 * holds its outers.  A mutable outer must not be altered or recycled once it
 * is captured, so the closing counts as consuming each of them.
 */
@Suppress("unused")
object P_CloseFunction
	: PrimitiveIsolatedN(-1, Private, CannotFail, CanInline), Foldable
{
	override fun attemptIsolated(
		context: PrimitiveContext,
		args: Array<AvailObject>
	): A_BasicObject?
	{
		val rawFunction: A_RawFunction = args[0]
		assert(args.size == rawFunction.numOuters + 1)
		return createFunction(
			rawFunction, tupleFromList(args.asList().subList(1, args.size)))
	}

	/**
	 * A function type cannot say how many arguments a variadic primitive
	 * takes, so the argument types are left fully general.  The first must be
	 * a raw function, and the rest must suit its outers.
	 */
	override fun privateBlockTypeRestriction(): A_Type =
		functionTypeFromArgumentTupleType(
			mostGeneralTupleType, mostGeneralFunctionType, emptySet)

	/**
	 * The function will have exactly the raw function's own function type,
	 * when the raw function is known.
	 */
	context(_: Protected)
	override fun restrictionGuaranteedByVM(
		rawFunction: A_RawFunction?,
		argumentTypes: List<A_Type>
	): TypeRestriction
	{
		val rawFunctionType = argumentTypes[0]
		return when
		{
			rawFunctionType.instanceCount.equalsInt(1)
				&& !rawFunctionType.isInstanceMeta ->
				restrictionForType(rawFunctionType.instance.functionType())
			else -> super.restrictionGuaranteedByVM(rawFunction, argumentTypes)
		}
	}

	/**
	 * Answer the closing that names the function held by the given
	 * [L2SemanticValue] in the given manifest, or `null` if the function is
	 * not known to have been closed by one.  Its first argument is the raw
	 * function's semantic value, and the rest are its outers'.
	 *
	 * @param manifest
	 *   The [L2ValueManifest] in which to look.
	 * @param function
	 *   The [L2SemanticValue] of the function.
	 * @return
	 *   The [L2SemanticPrimitiveInvocation] of this primitive among the
	 *   function's synonyms, or `null`.
	 */
	fun closingIn(
		manifest: L2ValueManifest,
		function: L2SemanticValue
	): L2SemanticPrimitiveInvocation? =
		manifest.semanticValueToSynonymOrNull(function)
			?.semanticValues()
			?.firstNotNullOfOrNull { value ->
				(value as? L2SemanticPrimitiveInvocation)
					?.takeIf { it.foldable === this }
			}

	/**
	 * Answer the [A_RawFunction] that the function held by the given
	 * [L2SemanticValue] is known to run in the given manifest, or `null` if it
	 * is not known.  It is known if the function is a constant, or if it was
	 * closed by this primitive from a constant raw function (see
	 * [closingIn]).
	 *
	 * @param manifest
	 *   The [L2ValueManifest] in which to look.
	 * @param function
	 *   The [L2SemanticValue] of the function.
	 * @return
	 *   The function's raw function, or `null`.
	 */
	fun rawFunctionIn(
		manifest: L2ValueManifest,
		function: L2SemanticValue
	): A_RawFunction?
	{
		manifest.constantOrNullFor(function)?.let { return it.code() }
		val closing = closingIn(manifest, function) ?: return null
		return manifest.constantOrNullFor(closing.argumentSemanticValues[0])
	}

	/**
	 * Show the closed block decompiled, with each use of an outer replaced by
	 * the semantic value captured for it, then list the outers.
	 */
	override fun StringBuilder.describeL2Invocation(
		arguments: List<L2ReadBoxedOperand>)
	{
		val rawFunction: A_RawFunction? = arguments[0].semanticValue().constant
		if (rawFunction === null)
		{
			append(this@P_CloseFunction)
			append(arguments)
			return
		}
		val allNames = rawFunction.declarationNames
		val outerPairs = allNames
			.copyTupleFromToCanDestroy(
				allNames.tupleSize - rawFunction.numOuters + 1,
				allNames.tupleSize,
				false)
			.toList()
			.zip(arguments.drop(1))
		val outerPhrases = outerPairs
			.map { (baseName, read) ->
				val base = baseName.asNativeString()
				val name = stringFrom("$base(=${read.semanticValue()})")
				val token = literalToken(name, 0, 0, name, nil)
				LiteralPhraseDescriptor.fromTokenForDecompiler(token)
			}
			.toTypedArray()
		val counters = mutableMapOf<String, Int>()
		val decompiler = L1Decompiler(rawFunction, outerPhrases) {
			val newCount = (counters[it] ?: 0) + 1
			counters[it] = newCount
			it + newCount
		}
		append(increaseIndentation(decompiler.block.toString(), 1))
		outerPairs.forEach { (baseName, read) ->
			newlineTab(1)
			append(baseName.asNativeString())
			append(" = ")
			append(read)
		}
	}

	/**
	 * Build the function directly from the registers holding the outers,
	 * without first gathering them into a tuple.
	 */
	override fun JVMTranslator.generateJvmCodeWithoutTracking(
		arguments: L2ReadBoxedVectorOperand,
		result: L2WriteBoxedOperand)
	{
		val rawFunctionRead = arguments.elements[0]
		val outerReads = arguments.elements.drop(1)
		val numOuters = outerReads.size
		load(rawFunctionRead)
		// :: code
		if (numOuters <= 5)
		{
			outerReads.forEach<L2ReadBoxedOperand>(::load)
		}
		when (numOuters)
		{
			0 ->
			{
				intConstant(0)
				generateCall(FunctionDescriptor.createExceptOutersMethod)
			}
			1 -> generateCall(FunctionDescriptor.createWithOuters1Method)
			2 -> generateCall(FunctionDescriptor.createWithOuters2Method)
			3 -> generateCall(FunctionDescriptor.createWithOuters3Method)
			4 -> generateCall(FunctionDescriptor.createWithOuters4Method)
			5 -> generateCall(FunctionDescriptor.createWithOuters5Method)
			else ->
			{
				// :: function = createExceptOuters(code, numOuters);
				intConstant(numOuters)
				generateCall(FunctionDescriptor.createExceptOutersMethod)
				outerReads.forEachIndexed { i, outerRead ->
					// :: function.outerVarAtPut(«i + 1», «outerRegs[i]»);
					method.visitInsn(Opcodes.DUP)
					intConstant(i + 1)
					load(outerRead)
					generateCall(FunctionDescriptor.outerVarAtPutMethod)
				}
			}
		}
		// :: newFunction = function;
		store(result.register())
	}
}
