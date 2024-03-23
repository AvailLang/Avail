/*
 * InstructionLayout.kt
 * Copyright © 1993-2024, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 *  * Neither the name of the copyright holder nor the names of the contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
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

package avail.interpreter.levelTwo.new

import avail.interpreter.levelTwo.HiddenVariableShift
import avail.interpreter.levelTwo.L2Instruction
import avail.interpreter.levelTwo.L2NamedOperandType
import avail.interpreter.levelTwo.L2OperandType
import avail.interpreter.levelTwo.L2Operation.HiddenVariable
import avail.interpreter.levelTwo.ReadsHiddenVariable
import avail.interpreter.levelTwo.WritesHiddenVariable
import avail.interpreter.levelTwo.operand.L2Operand
import avail.interpreter.levelTwo.operand.L2ReadOperand
import avail.interpreter.levelTwo.operand.L2ReadVectorOperand
import avail.interpreter.levelTwo.operand.L2WriteBoxedVectorOperand
import avail.interpreter.levelTwo.operand.L2WriteOperand
import avail.utility.cast
import java.lang.reflect.Field
import java.lang.reflect.Method

// One per class.  See layoutsByClass a little bit above, and the
// Instruction.layout field.
class InstructionLayout
constructor(private val instructionClass: Class<out L2NewInstruction>)
{
	// One per lexical field definition (e.g., L2_ADD_INTS' augend field).
	inner class OperandField<O: L2Operand>
	constructor(private val javaField: Field)
	{
		val type: Class<O> get() = javaField.type.cast()

		val name: String = javaField.name

		private val getterMethod: Method = run {
			instructionClass.getDeclaredMethod(
				"get" + name.replaceFirstChar(Char::titlecase))
		}

		private val setterMethod: Method =
			instructionClass.getDeclaredMethod(
				"set" + name.replaceFirstChar(Char::titlecase), type)

		val purpose = javaField.getAnnotation(On::class.java)?.purpose

		val namedOperandType: L2NamedOperandType = L2NamedOperandType(
			L2OperandType.operandTypeForOperandClass(type), name, purpose)

		fun get(instruction: L2NewInstruction): O =
			getterMethod.invoke(instruction).cast()

		fun set(instruction: L2NewInstruction, operand: O) =
			setterMethod.invoke(instruction, operand)

		fun update(
			instruction: L2NewInstruction,
			transformation: (O)->O)
		{
			set(instruction, transformation(get(instruction)))
		}
	}

	/**
	 * The name of the instruction subclass.
	 */
	val name = instructionClass.simpleName

	val operandFields = instructionClass.declaredFields
		.filter { L2Operand::class.java.isAssignableFrom(it.type) }
		.map { OperandField<L2Operand>(it) }

	@Suppress("UNCHECKED_CAST")
	private val scalarReadOperandFields:
			List<OperandField<L2ReadOperand<*>>> =
		operandFields
			.filter { L2ReadOperand::class.java.isAssignableFrom(it.type) }
			as List<OperandField<L2ReadOperand<*>>>

	private val vectorReadOperandFields:
			List<OperandField<L2ReadVectorOperand<*>>> =
		operandFields
			.filter {
				L2ReadVectorOperand::class.java.isAssignableFrom(it.type)
			}.cast()

	private val scalarWriteOperandFields:
			List<OperandField<L2WriteOperand<*>>> =
		operandFields
			.filter {
				L2WriteOperand::class.java.isAssignableFrom(it.type)
			}.cast()

	private val vectorWriteOperandFields:
			List<OperandField<L2WriteBoxedVectorOperand>> =
		operandFields
			.filter {
				L2WriteBoxedVectorOperand::class.java.isAssignableFrom(it.type)
			}.cast()

	fun operandsWithNamedTypesDo(
		instruction: L2NewInstruction,
		consumer: (L2Operand, L2NamedOperandType) -> Unit)
	{
		operandFields.forEach { field ->
    		consumer(field.get(instruction), field.namedOperandType)
    	}
	}

	fun operands(instruction: L2NewInstruction): Array<L2Operand> =
		operandFields.map { it.get(instruction) }.toTypedArray()

	fun readOperands(
		instruction: L2NewInstruction
	): List<L2ReadOperand<*>> = when
	{
		vectorReadOperandFields.isEmpty() ->
			scalarReadOperandFields.map { it.get(instruction) }
		else -> mutableListOf<L2ReadOperand<*>>().let { list ->
			scalarReadOperandFields.mapTo(list) { read ->
				read.get(instruction)
			}
			vectorReadOperandFields.flatMapTo(list) { vector ->
				vector.get(instruction).elements
			}
		}
	}

	fun writeOperands(
		instruction: L2NewInstruction
	): List<L2WriteOperand<*>> = when
	{
		vectorWriteOperandFields.isEmpty() ->
			scalarWriteOperandFields.map { it.get(instruction) }
		else -> mutableListOf<L2WriteOperand<*>>().let { list ->
			scalarWriteOperandFields.mapTo(list) { write ->
				write.get(instruction)
			}
			vectorWriteOperandFields.flatMapTo(list) { vector ->
				vector.get(instruction).elements
			}
		}
	}

	/**
	 * Transform this instruction's operands, writing them back.  The
	 * transformer must preserve the type of each operand, otherwise the attempt
	 * to write it back will fail.
	 *
	 * @param instruction
	 *   The instruction to alter, which must be of a type ssuitable for this
	 *   layout to manipulate.
	 * @param transform
	 *   A function that maps an [L2Operand] into a replacement operand of the
	 *   same [Class].
	 */
	fun transformOperands(
		instruction: L2NewInstruction,
		transform: (L2Operand) -> L2Operand)
	{
		operandFields.forEach { operandField ->
			val old = operandField.get(instruction)
			val new = transform(old)
			operandField.set(instruction, new.cast())
		}
	}

	/**
	 * The bitwise-or of the masks of [HiddenVariable]s that are read by
	 * [L2Instruction]s using this operation.  Note that all reads are
	 * considered to happen before all writes.
	 */
	val readsHiddenVariablesMask: Int

	/**
	 * The bitwise-or of the masks of [HiddenVariable]s that are overwritten by
	 * [L2Instruction]s using this operation.  Note that all reads are
	 * considered to happen before all writes.
	 */
	val writesHiddenVariablesMask: Int

	// Do some more initialization for the primary constructor.
	init
	{
		val readsAnnotation =
			instructionClass.getAnnotation(ReadsHiddenVariable::class.java)
		var readMask = 0
		if (readsAnnotation !== null)
		{
			for (hiddenVariableSubclass in readsAnnotation.value)
			{
				val shiftAnnotation =
					hiddenVariableSubclass.java.getAnnotation(
						HiddenVariableShift::class.java)
				readMask = readMask or (1 shl shiftAnnotation.value)
			}
		}
		readsHiddenVariablesMask = readMask
		val writesAnnotation =
			instructionClass.getAnnotation(WritesHiddenVariable::class.java)
		var writeMask = 0
		if (writesAnnotation !== null)
		{
			for (hiddenVariableSubclass in writesAnnotation.value)
			{
				val shiftAnnotation =
					hiddenVariableSubclass.java.getAnnotation(
						HiddenVariableShift::class.java)
				writeMask = writeMask or (1 shl shiftAnnotation.value)
			}
		}
		writesHiddenVariablesMask = writeMask
	}
}
