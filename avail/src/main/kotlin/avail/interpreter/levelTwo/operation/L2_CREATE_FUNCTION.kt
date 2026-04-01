/*
 * L2_CREATE_FUNCTION.kt
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

import avail.descriptor.functions.A_RawFunction
import avail.descriptor.functions.A_RawFunction.Companion.declarationNames
import avail.descriptor.functions.A_RawFunction.Companion.numOuters
import avail.descriptor.functions.A_RawFunction.Companion.outerTypeAt
import avail.descriptor.functions.FunctionDescriptor
import avail.descriptor.functions.FunctionDescriptor.Companion.createFunction
import avail.descriptor.phrases.LiteralPhraseDescriptor
import avail.descriptor.representation.NilDescriptor.Companion.nil
import avail.descriptor.tokens.LiteralTokenDescriptor.Companion.literalToken
import avail.descriptor.tuples.A_String.Companion.asNativeString
import avail.descriptor.tuples.A_Tuple.Companion.copyTupleFromToCanDestroy
import avail.descriptor.tuples.A_Tuple.Companion.tupleAt
import avail.descriptor.tuples.A_Tuple.Companion.tupleSize
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tupleFromList
import avail.descriptor.tuples.StringDescriptor.Companion.stringFrom
import avail.descriptor.types.A_Type
import avail.descriptor.types.A_Type.Companion.typeIntersection
import avail.interpreter.levelOne.L1Decompiler
import avail.interpreter.levelTwo.L2Instruction
import avail.interpreter.levelTwo.L2OperandType
import avail.interpreter.levelTwo.operand.L2CommentOperand
import avail.interpreter.levelTwo.operand.L2ConstantOperand
import avail.interpreter.levelTwo.operand.L2IntImmediateOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedVectorOperand
import avail.interpreter.levelTwo.operand.L2WriteBoxedOperand
import avail.interpreter.levelTwo.operand.TypeRestriction.RestrictionFlagEncoding.IMMUTABLE_FLAG
import avail.optimizer.L2GeneratorInterface
import avail.optimizer.L2ValueManifest
import avail.optimizer.jvm.JVMTranslator
import avail.utility.Strings.increaseIndentation
import avail.utility.Strings.newlineTab
import org.objectweb.asm.Opcodes

/**
 * Synthesize a new [function][FunctionDescriptor] from the provided
 * constant compiled code and the vector of captured ("outer") variables.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
class L2_CREATE_FUNCTION(
	var code: L2ConstantOperand,
	var capturedVariables: L2ReadBoxedVectorOperand,
	var newFunction: L2WriteBoxedOperand
) : L2Instruction()
{
	override fun StringBuilder.appendToWithWarnings(
		desiredOperandTypes: Set<L2OperandType>,
		ignoreMisconnections: Boolean,
		warningStyleChange: (Boolean)->Unit)
	{
		renderPreamble()
		append(' ')
		append(newFunction.registerString())
		append(" ← ")

		// Decompile it to a phrase, but substituting the supplied semantic
		// values in place of each use of an outer variable.
		val rawFunction: A_RawFunction = code.constant
		val allNames = rawFunction.declarationNames
		val outerPairs = allNames
			.copyTupleFromToCanDestroy(
				allNames.tupleSize - rawFunction.numOuters + 1,
				allNames.tupleSize,
				false)
			.toList()
			.zip(capturedVariables.elements)
		val outerPhrases = outerPairs
			.map { (baseName, read) ->
				val base = baseName.asNativeString()
;				val name = stringFrom("$base(=${read.semanticValue()})")
				val token = literalToken(name, 0, 0, name, nil)
				LiteralPhraseDescriptor.fromTokenForDecompiler(token)
			}
			.toTypedArray()
		val counters = mutableMapOf<String, Int>()
		val decompiler = L1Decompiler(rawFunction, outerPhrases) {
			var newCount: Int? = counters[it]
			newCount = if (newCount === null) 1 else newCount + 1
			counters[it] = newCount
			it + newCount
		}
		val decompiled = decompiler.block.toString()
		append(increaseIndentation(decompiled, 1))

		// Also list the outers separately after the block.
		outerPairs.forEach { (baseName, read) ->
			newlineTab(1)
			append(baseName.asNativeString())
			append(" = ")
			append(read)
		}
	}

	override fun L2GeneratorInterface.extractFunctionOuter(
		functionRegister: L2ReadBoxedOperand,
		outerIndex: Int,
		outerType: A_Type
	): L2ReadBoxedOperand
	{
		val originalRead = capturedVariables.elements[outerIndex - 1]
		val rawCode: A_RawFunction = code.constant
		// Intersect the read's restriction, the given type, and the type that
		// the code says the outer must have.
		var intersection = originalRead.restriction().intersectionWithType(
			outerType.typeIntersection(rawCode.outerTypeAt(outerIndex)))
		assert(!intersection.type.isBottom)
		val semanticValue = originalRead.semanticValue()
		if (currentManifest.hasSemanticValue(semanticValue))
		{
			// This semantic value is still live.  Use it directly.
			val restriction = currentManifest.restrictionFor(semanticValue)
			if (restriction.isBoxed)
			{
				// It's still live *and* boxed.
				return readBoxed(semanticValue)
			}
		}
		// The registers that supplied the value are no longer live.  Extract
		// the value from the actual function.  Note that it's still guaranteed
		// to have the strengthened type.
		if (functionRegister.restriction().isImmutable)
		{
			// An immutable function has immutable captured outers.
			intersection = intersection.withFlag(IMMUTABLE_FLAG)
		}
		val tempWrite = boxedWriteTemp("outer #$outerIndex", intersection)
		val allNames = rawCode.declarationNames
		val nameIndex = allNames.tupleSize - rawCode.numOuters + outerIndex
		val outerName = when (nameIndex <= allNames.tupleSize)
		{
			true -> allNames.tupleAt(nameIndex).asNativeString()
			else -> ""
		}
		+L2_MOVE_OUTER_VARIABLE(
			L2CommentOperand(outerName),
			L2IntImmediateOperand(outerIndex),
			functionRegister,
			tempWrite)
		return readBoxed(tempWrite)
	}

	/**
	 * Extract the constant [A_RawFunction] from the given [L2Instruction],
	 * which must have `L2_CREATE_FUNCTION` as its operation.
	 *
	 * @return
	 *   The constant [A_RawFunction] extracted from the instruction.
	 */
	override fun getConstantCode(manifest: L2ValueManifest): A_RawFunction = code.constant

	override fun L2GeneratorInterface.emitTransformedInstruction()
	{
		// See if the outers are all constant, perhaps due to code splitting.
		val constantOuters = capturedVariables.elements.map { outer ->
			val constant = outer.constantOrNull
			if (constant == null)
			{
				+this@L2_CREATE_FUNCTION
				return
			}
			constant
		}
		val staticFunction = createFunction(
			code.constant, tupleFromList(constantOuters))
		moveBoxedRegister(
			boxedConstant(staticFunction).semanticValue(),
			newFunction.semanticValues())
	}

	override fun JVMTranslator.translateToJVM()
	{
		val numOuters = capturedVariables.elements.size
		val theCode = this@L2_CREATE_FUNCTION.code.constant
		assert(numOuters == theCode.numOuters)
		loadLiteralObject(theCode)
		assert(numOuters != 0)
		if (numOuters <= 5)
		{
			capturedVariables.registers().forEach {
				loadRegister(it)
			}
		}
		when (numOuters)
		{
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
				for (i in 0 until numOuters)
				{
					// :: function.outerVarAtPut(«i + 1», «outerRegs[i]»);
					method.visitInsn(Opcodes.DUP)
					intConstant(i + 1)
					load(capturedVariables.elements[i])
					generateCall(FunctionDescriptor.outerVarAtPutMethod)
				}
			}
		}
		// :: newFunction = function;
		store(newFunction.register())
	}
}
