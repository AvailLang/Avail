/*
 * L2_LOOKUP_BY_TYPES.kt
 * Copyright © 1993-2021, The Avail Foundation, LLC.
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
package com.avail.interpreter.levelTwo.operation

import com.avail.AvailRuntimeSupport
import com.avail.descriptor.atoms.A_Atom.Companion.atomName
import com.avail.descriptor.bundles.A_Bundle
import com.avail.descriptor.bundles.A_Bundle.Companion.bundleMethod
import com.avail.descriptor.bundles.A_Bundle.Companion.message
import com.avail.descriptor.functions.A_Function
import com.avail.descriptor.methods.A_Definition
import com.avail.descriptor.methods.A_Method
import com.avail.descriptor.methods.A_Method.Companion.lookupByTypesFromTuple
import com.avail.descriptor.representation.AvailObject
import com.avail.descriptor.sets.SetDescriptor.Companion.set
import com.avail.descriptor.sets.SetDescriptor.Companion.toSet
import com.avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tupleFromList
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.A_Type.Companion.argsTupleType
import com.avail.descriptor.types.A_Type.Companion.instances
import com.avail.descriptor.types.A_Type.Companion.typeAtIndex
import com.avail.descriptor.types.A_Type.Companion.typeUnion
import com.avail.descriptor.types.AbstractEnumerationTypeDescriptor
import com.avail.descriptor.types.BottomTypeDescriptor.Companion.bottom
import com.avail.exceptions.AvailErrorCode.E_ABSTRACT_METHOD_DEFINITION
import com.avail.exceptions.AvailErrorCode.E_AMBIGUOUS_METHOD_DEFINITION
import com.avail.exceptions.AvailErrorCode.E_FORWARD_METHOD_DEFINITION
import com.avail.exceptions.AvailErrorCode.E_NO_METHOD
import com.avail.exceptions.AvailErrorCode.E_NO_METHOD_DEFINITION
import com.avail.exceptions.AvailException.Companion.numericCodeMethod
import com.avail.exceptions.MethodDefinitionException
import com.avail.exceptions.MethodDefinitionException.Companion.abstractMethod
import com.avail.exceptions.MethodDefinitionException.Companion.forwardMethod
import com.avail.interpreter.execution.Interpreter
import com.avail.interpreter.execution.Interpreter.Companion.log
import com.avail.interpreter.levelTwo.L2Instruction
import com.avail.interpreter.levelTwo.L2NamedOperandType.Purpose.FAILURE
import com.avail.interpreter.levelTwo.L2NamedOperandType.Purpose.SUCCESS
import com.avail.interpreter.levelTwo.L2OperandType.PC
import com.avail.interpreter.levelTwo.L2OperandType.READ_BOXED_VECTOR
import com.avail.interpreter.levelTwo.L2OperandType.SELECTOR
import com.avail.interpreter.levelTwo.L2OperandType.WRITE_BOXED
import com.avail.interpreter.levelTwo.operand.L2PcOperand
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedVectorOperand
import com.avail.interpreter.levelTwo.operand.L2SelectorOperand
import com.avail.interpreter.levelTwo.operand.L2WriteBoxedOperand
import com.avail.interpreter.levelTwo.operand.TypeRestriction.RestrictionFlagEncoding.*
import com.avail.optimizer.L2ValueManifest
import com.avail.optimizer.jvm.CheckedMethod
import com.avail.optimizer.jvm.JVMTranslator
import com.avail.optimizer.jvm.ReferencedInGeneratedCode
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import java.util.logging.Level

/**
 * Look up the method to invoke. Use the provided vector of argument types to
 * perform a polymorphic lookup. Write the resulting function into the
 * specified destination register. If the lookup fails, then branch to the
 * specified [offset][Interpreter.setOffset].
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object L2_LOOKUP_BY_TYPES : L2ControlFlowOperation(
	SELECTOR.named("message bundle"),
	READ_BOXED_VECTOR.named("argument types"),
	WRITE_BOXED.named("looked up function", SUCCESS),
	WRITE_BOXED.named("error code", FAILURE),
	PC.named("lookup succeeded", SUCCESS),
	PC.named("lookup failed", FAILURE))
{
	/** The type of failure codes that a failed lookup can produce.  */
	private val failureCodesType =
		AbstractEnumerationTypeDescriptor.enumerationWith(set(
			E_NO_METHOD,
			E_NO_METHOD_DEFINITION,
			E_AMBIGUOUS_METHOD_DEFINITION,
			E_FORWARD_METHOD_DEFINITION,
			E_ABSTRACT_METHOD_DEFINITION))

	override fun instructionWasAdded(
		instruction: L2Instruction,
		manifest: L2ValueManifest)
	{
		assert(this == instruction.operation())
		//		final L2SelectorOperand bundle = instruction.operand(0);
		val argTypeRegs = instruction.operand<L2ReadBoxedVectorOperand>(1)
		val functionReg = instruction.operand<L2WriteBoxedOperand>(2)
		val errorCodeReg = instruction.operand<L2WriteBoxedOperand>(3)
		val lookupSucceeded = instruction.operand<L2PcOperand>(4)
		val lookupFailed = instruction.operand<L2PcOperand>(5)
		super.instructionWasAdded(instruction, manifest)

		// If the lookup failed, it supplies the reason to the errorCodeReg.
		lookupFailed.manifest().setRestriction(
			errorCodeReg.pickSemanticValue(),
			errorCodeReg.restriction())

		// If the lookup succeeds, the functionReg will be set, and we can also
		// conclude that the arguments satisfied at least one of the found
		// function types.
		lookupSucceeded.manifest().setRestriction(
			functionReg.pickSemanticValue(),
			functionReg.restriction())
		// The function type should be an enumeration, so we know that each
		// argument type satisfied at least one of the functions' corresponding
		// argument types.
		val argumentTypeRegs = argTypeRegs.elements()
		val functionType = functionReg.restriction().type
		if (functionType.isEnumeration)
		{
			val numArgs = argumentTypeRegs.size
			val functions: Set<A_Function> = toSet(functionType.instances)
			val argumentTupleUnionType =
				functions.fold(bottom) { union, function ->
					union.typeUnion(
						function.code().functionType().argsTupleType)
				}
			for (i in 1 .. numArgs)
			{
				val argumentUnion = argumentTupleUnionType.typeAtIndex(i)
				lookupSucceeded.manifest().intersectType(
					argumentTypeRegs[i - 1].semanticValue(),
					AbstractEnumerationTypeDescriptor.instanceTypeOrMetaOn(
						argumentUnion))
			}
		}
	}

	override fun hasSideEffect() = true

	override fun translateToJVM(
		translator: JVMTranslator,
		method: MethodVisitor,
		instruction: L2Instruction)
	{
		val bundleOperand = instruction.operand<L2SelectorOperand>(0)
		val argTypeRegs = instruction.operand<L2ReadBoxedVectorOperand>(1)
		val functionReg = instruction.operand<L2WriteBoxedOperand>(2)
		val errorCodeReg = instruction.operand<L2WriteBoxedOperand>(3)
		val lookupSucceeded = instruction.operand<L2PcOperand>(4)
		val lookupFailed = instruction.operand<L2PcOperand>(5)

		// :: try {
		val tryStart = Label()
		val catchStart = Label()
		method.visitTryCatchBlock(
			tryStart,
			catchStart,
			catchStart,
			Type.getInternalName(MethodDefinitionException::class.java))
		method.visitLabel(tryStart)
		// ::    function = lookup(interpreter, bundle, types);
		translator.loadInterpreter(method)
		translator.literal(method, bundleOperand.bundle)
		translator.objectArray(
			method, argTypeRegs.elements(), AvailObject::class.java)
		lookupMethod.generateCall(method)
		translator.store(method, functionReg.register())
		// ::    goto lookupSucceeded;
		// Note that we cannot potentially eliminate this branch with a
		// fall through, because the next instruction expects a
		// MethodDefinitionException to be pushed onto the stack. So always do
		// the jump.
		translator.jump(method, lookupSucceeded)
		// :: } catch (MethodDefinitionException e) {
		method.visitLabel(catchStart)
		// ::    errorCode = e.numericCode();
		numericCodeMethod.generateCall(method)
		method.visitTypeInsn(
			Opcodes.CHECKCAST,
			Type.getInternalName(AvailObject::class.java))
		translator.store(method, errorCodeReg.register())
		// ::    goto lookupFailed;
		translator.jump(method, instruction, lookupFailed)
		// :: }
	}

	/**
	 * Perform the lookup.
	 *
	 * @param interpreter
	 *   The [Interpreter].
	 * @param bundle
	 *   The [A_Bundle].
	 * @param types
	 *   The [types][A_Type] for the lookup.
	 * @return
	 *   The unique [function][A_Function].
	 * @throws MethodDefinitionException
	 *   If the lookup did not resolve to a unique executable function.
	 */
	@ReferencedInGeneratedCode
	@JvmStatic
	@Throws(MethodDefinitionException::class)
	fun lookup(
		interpreter: Interpreter,
		bundle: A_Bundle,
		types: Array<AvailObject>): A_Function
	{
		if (Interpreter.debugL2)
		{
			log(
				Interpreter.loggerDebugL2,
				Level.FINER,
				"{0}Lookup-by-types {1}",
				interpreter.debugModeString,
				bundle.message.atomName)
		}
		val typesList = mutableListOf(*types)
		val method: A_Method = bundle.bundleMethod
		val before = AvailRuntimeSupport.captureNanos()
		val definitionToCall: A_Definition = try
		{
			method.lookupByTypesFromTuple(tupleFromList(typesList))
		}
		finally
		{
			val after = AvailRuntimeSupport.captureNanos()
			interpreter.recordDynamicLookup(bundle, after - before.toDouble())
		}
		when
		{
			definitionToCall.isAbstractDefinition() -> throw abstractMethod()
			definitionToCall.isForwardDefinition() -> throw forwardMethod()
			else -> return definitionToCall.bodyBlock()
		}
	}

	/**
	 * The [CheckedMethod] for [lookup].
	 */
	private val lookupMethod = CheckedMethod.staticMethod(
		L2_LOOKUP_BY_TYPES::class.java,
		::lookup.name,
		A_Function::class.java,
		Interpreter::class.java,
		A_Bundle::class.java,
		Array<AvailObject>::class.java)
}
