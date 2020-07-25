/*
 * L2_LOOKUP_BY_VALUES.java
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
package com.avail.interpreter.levelTwo.operation

import com.avail.AvailRuntimeSupport
import com.avail.descriptor.atoms.A_Atom.Companion.atomName
import com.avail.descriptor.bundles.A_Bundle
import com.avail.descriptor.bundles.A_Bundle.Companion.bundleMethod
import com.avail.descriptor.bundles.A_Bundle.Companion.message
import com.avail.descriptor.functions.A_Function
import com.avail.descriptor.methods.A_Definition
import com.avail.descriptor.methods.A_Method
import com.avail.descriptor.representation.AvailObject
import com.avail.descriptor.sets.SetDescriptor.Companion.set
import com.avail.descriptor.sets.SetDescriptor.Companion.setFromCollection
import com.avail.descriptor.sets.SetDescriptor.Companion.toSet
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import com.avail.descriptor.types.BottomTypeDescriptor.Companion.bottom
import com.avail.descriptor.types.TypeDescriptor.Types
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
import com.avail.interpreter.levelTwo.L2NamedOperandType
import com.avail.interpreter.levelTwo.L2OperandType
import com.avail.interpreter.levelTwo.operand.L2PcOperand
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedVectorOperand
import com.avail.interpreter.levelTwo.operand.L2SelectorOperand
import com.avail.interpreter.levelTwo.operand.L2WriteBoxedOperand
import com.avail.interpreter.levelTwo.operand.TypeRestriction.Companion.restrictionForType
import com.avail.interpreter.levelTwo.operand.TypeRestriction.RestrictionFlagEncoding
import com.avail.optimizer.L2Generator
import com.avail.optimizer.L2ValueManifest
import com.avail.optimizer.RegisterSet
import com.avail.optimizer.jvm.CheckedMethod
import com.avail.optimizer.jvm.JVMTranslator
import com.avail.optimizer.jvm.ReferencedInGeneratedCode
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import java.util.*
import java.util.logging.Level

/**
 * Look up the method to invoke. Use the provided vector of arguments to
 * perform a polymorphic lookup. Write the resulting function into the
 * specified destination register. If the lookup fails, then branch to the
 * specified [offset][Interpreter.setOffset].
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object L2_LOOKUP_BY_VALUES : L2ControlFlowOperation(
	L2OperandType.SELECTOR.named("message bundle"),
	L2OperandType.READ_BOXED_VECTOR.named("arguments"),
	L2OperandType.WRITE_BOXED.named(
		"looked up function", L2NamedOperandType.Purpose.SUCCESS),
	L2OperandType.WRITE_BOXED.named(
		"error code", L2NamedOperandType.Purpose.FAILURE),
	L2OperandType.PC.named(
		"lookup succeeded", L2NamedOperandType.Purpose.SUCCESS),
	L2OperandType.PC.named(
		"lookup failed", L2NamedOperandType.Purpose.FAILURE))
{
	override fun instructionWasAdded(
		instruction: L2Instruction,
		manifest: L2ValueManifest)
	{
		assert(this == instruction.operation())
		//		final L2SelectorOperand bundle = instruction.operand(0);
		val argRegs =
			instruction.operand<L2ReadBoxedVectorOperand>(1)
		val functionReg =
			instruction.operand<L2WriteBoxedOperand>(2)
		val errorCodeReg =
			instruction.operand<L2WriteBoxedOperand>(3)
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
		// argument satisfied at least one of the functions' corresponding
		// argument types.
		val arguments = argRegs.elements()
		val functionType = functionReg.restriction().type
		if (functionType.isEnumeration)
		{
			val numArgs = arguments.size
			val functions: Set<A_Function> = toSet(functionType.instances())
			val argumentTupleUnionType =
				functions.fold(bottom()) { union, function ->
					union.typeUnion(
						function.code().functionType().argsTupleType())
				}
			for (i in 1 .. numArgs)
			{
				val argumentUnion = argumentTupleUnionType.typeAtIndex(i)
				lookupSucceeded.manifest().intersectType(
					arguments[i - 1].semanticValue(),
					argumentUnion)
			}
			// If only one argument wasn't strongly typed enough to prove
			// statically, we could subtract the functions' argument types from
			// that argument register's restriction.  We won't bother here,
			// since this is the failed slow lookup case.
		}
	}

	override fun propagateTypes(
		instruction: L2Instruction,
		registerSets: List<RegisterSet>,
		generator: L2Generator)
	{
		// Find all possible definitions (taking into account the types of the
		// argument registers).  Then build an enumeration type over those
		// functions.
		val bundleOperand =
			instruction.operand<L2SelectorOperand>(0)
		val argRegs =
			instruction.operand<L2ReadBoxedVectorOperand>(1)
		val functionReg =
			instruction.operand<L2WriteBoxedOperand>(2)
		val errorCodeReg =
			instruction.operand<L2WriteBoxedOperand>(3)
		//		final L2PcOperand lookupSucceeded = instruction.operand(4);
//		final L2PcOperand lookupFailed = instruction.operand(5);

		// If the lookup fails, then only the error code register changes.
		registerSets[0].typeAtPut(
			errorCodeReg.register(), lookupErrorsType, instruction)
		// If the lookup succeeds, then the situation is more complex.
		val registerSet = registerSets[1]
		val argRestrictions = argRegs.elements().map { argRegister ->
			val type = when
				{
					registerSet.hasTypeAt(argRegister.register()) ->
						registerSet.typeAt(argRegister.register())
					else -> Types.ANY.o()
				}
			restrictionForType(type, RestrictionFlagEncoding.BOXED)

		}
		// Figure out what could be invoked at runtime given these argument
		// type constraints.
		val possibleDefinitions: List<A_Definition> =
			bundleOperand.bundle.bundleMethod().definitionsAtOrBelow(
				argRestrictions)
		val possibleFunctions = possibleDefinitions
			.filter(A_Definition::isMethodDefinition)
			.map(A_Definition::bodyBlock)
		if (possibleFunctions.size == 1)
		{
			// Only one function could be looked up (it's monomorphic for this
			// call site).  Therefore we know strongly what the function is.
			registerSet.constantAtPut(
				functionReg.register(),
				possibleFunctions[0],
				instruction)
		}
		else
		{
			val enumType = enumerationWith(setFromCollection(possibleFunctions))
			registerSet.typeAtPut(functionReg.register(), enumType, instruction)
		}
	}

	override fun translateToJVM(
		translator: JVMTranslator,
		method: MethodVisitor,
		instruction: L2Instruction)
	{
		val bundleOperand =
			instruction.operand<L2SelectorOperand>(0)
		val argRegs =
			instruction.operand<L2ReadBoxedVectorOperand>(1)
		val functionReg =
			instruction.operand<L2WriteBoxedOperand>(2)
		val errorCodeReg =
			instruction.operand<L2WriteBoxedOperand>(3)
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
			method, argRegs.elements(), AvailObject::class.java)
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
	 * The error codes that can be produced by a failed lookup.
	 */
	@kotlin.jvm.JvmField
	val lookupErrorsType : A_Type =
		enumerationWith(set(
			E_NO_METHOD,
			E_NO_METHOD_DEFINITION,
			E_AMBIGUOUS_METHOD_DEFINITION,
			E_ABSTRACT_METHOD_DEFINITION,
			E_FORWARD_METHOD_DEFINITION))

	/**
	 * Perform the lookup.
	 *
	 * @param interpreter
	 *   The [Interpreter].
	 * @param bundle
	 *   The [A_Bundle].
	 * @param values
	 *   The [values][AvailObject] for the lookup.
	 * @return
	 *   The unique [function][A_Function].
	 * @throws MethodDefinitionException
	 *   If the lookup did not resolve to a unique executable function.
	 */
	@JvmStatic
	@ReferencedInGeneratedCode
	@Throws(MethodDefinitionException::class)
	fun lookup(
		interpreter: Interpreter,
		bundle: A_Bundle,
		values: Array<AvailObject>): A_Function
	{
		if (Interpreter.debugL2)
		{
			log(
				Interpreter.loggerDebugL2,
				Level.FINER,
				"{0}Lookup {1}",
				interpreter.debugModeString,
				bundle.message().atomName())
		}
		val valuesList: MutableList<AvailObject> = ArrayList(values.size)
		Collections.addAll(valuesList, *values)
		val method: A_Method = bundle.bundleMethod()
		val before = AvailRuntimeSupport.captureNanos()
		val definitionToCall: A_Definition
		definitionToCall = try
		{
			method.lookupByValuesFromList(valuesList)
		}
		finally
		{
			val after = AvailRuntimeSupport.captureNanos()
			interpreter.recordDynamicLookup(bundle, after - before.toDouble())
		}
		if (definitionToCall.isAbstractDefinition())
		{
			throw abstractMethod()
		}
		if (definitionToCall.isForwardDefinition())
		{
			throw forwardMethod()
		}
		return definitionToCall.bodyBlock()
	}

	/**
	 * The [CheckedMethod] for [lookup].
	 */
	private val lookupMethod = CheckedMethod.staticMethod(
		L2_LOOKUP_BY_VALUES::class.java,
		::lookup.name,
		A_Function::class.java,
		Interpreter::class.java,
		A_Bundle::class.java,
		Array<AvailObject>::class.java)
}
