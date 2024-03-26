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
import avail.interpreter.levelTwo.L2NamedOperandType.Purpose
import avail.interpreter.levelTwo.L2OperandType
import avail.interpreter.levelTwo.L2Operation.HiddenVariable
import avail.interpreter.levelTwo.ReadsHiddenVariable
import avail.interpreter.levelTwo.WritesHiddenVariable
import avail.interpreter.levelTwo.operand.L2Operand
import avail.interpreter.levelTwo.operand.L2PcOperand
import avail.interpreter.levelTwo.operand.L2PcVectorOperand
import avail.interpreter.levelTwo.operand.L2ReadOperand
import avail.interpreter.levelTwo.operand.L2ReadVectorOperand
import avail.interpreter.levelTwo.operand.L2WriteBoxedVectorOperand
import avail.interpreter.levelTwo.operand.L2WriteOperand
import avail.utility.cast
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.jvm.javaField

/**
 * A helper class that an [L2NewInstruction] uses to access its operands in a
 * simple, mostly typesafe way.
 *
 * @property instructionClass
 *   The Kotlin [KClass] for the [L2NewInstruction] subclass that this layout
 *   describes.
 *
 * @constructor
 *   Create an [InstructionLayout] for navigating the [L2Operand]s of the given
 *   [KClass] (on some [L2NewInstruction]).
 */
class InstructionLayout<I : L2NewInstruction>
internal constructor(private val instructionClass: KClass<out I>)
{
	/**
	 * An instance of [OperandField] is created for each field of an
	 * [L2NewInstruction] that contains an [L2Operand].
	 *
	 * @param O
	 *   The subclass of [L2Operand] that occurs in the corresponding field.
	 *
	 * @constructor
	 *   Create an [OperandField] for use in an [InstructionLayout] used by all
	 *   instances of ome [L2NewInstruction].
	 * @param property
	 *   The Kotlin `var` property of the [L2NewInstruction] in which the
	 *   [L2Operand] is stored.
	 */
	private inner class OperandField<O: L2Operand>
	constructor(private val property: KMutableProperty1<I, O>)
	{
		/** The Java [Class] for the field's type, [O] (an [L2Operand]). */
		val type: Class<O> get() = property.javaField!!.type.cast()

		/** The name of this property. */
		val name: String = property.name

		/**
		 * The getter for reading the field from a provided [L2NewInstruction]
		 * of the appropriate subtype ([I]).
		 */
		private val getter = property.getter

		/**
		 * The setter for writing the field into a provided [L2NewInstruction]
		 * of the appropriate subtype ([I]).
		 */
		private val setter = property.setter

		/**
		 * The [L2NamedOperandType] created for this field.  Note that if the
		 * field definition has an [On] annotation, its [Purpose] is extracted
		 * and captured in this [L2NamedOperandType].  [L2WriteOperand]s that
		 * have a [Purpose] will only be considered written to if the
		 * [L2PcOperand] edge leaving the instruction is labeled with the same
		 * [Purpose].
		 */
		val namedOperandType: L2NamedOperandType = L2NamedOperandType(
			L2OperandType.operandTypeForOperandClass(type),
			name,
			property.javaField!!.getAnnotation(On::class.java)?.purpose)

		/**
		 * Read the field from an [L2NewInstruction] of the required type.
		 */
		fun get(instruction: L2NewInstruction): O = getter(instruction.cast())

		/**
		 * Write the field in an [L2NewInstruction] of the required type.
		 */
		fun set(instruction: L2NewInstruction, operand: O) =
			setter(instruction.cast(), operand)

		/**
		 * Write the field, but allowing the [operand]'s type to be checked at
		 * runtime.
		 */
		fun setUnchecked(instruction: L2NewInstruction, operand: L2Operand) =
			setter(instruction.cast(), operand.cast())

		/**
		 * Read this field from the [instruction], pass it through the
		 * [transformation], and write it back into the instruction.
		 */
		fun update(
			instruction: L2NewInstruction,
			transformation: (L2Operand)->L2Operand
		) = set(instruction, transformation(get(instruction)).cast())
	}

	/**
	 * The name to present as the basic instruction name.
	 */
	val name = instructionClass.simpleName!!

	private val operandFields = instructionClass.declaredMemberProperties
		.filterIsInstance<KMutableProperty1<I, out L2Operand>>()
		.filter { L2Operand::class.java.isAssignableFrom(it.javaField!!.type) }
		.map { OperandField(it) }

	fun updateOperands(
		instruction: L2NewInstruction,
		transform: (L2Operand) -> L2Operand)
	{
		operandFields.forEach { field ->
			val old = field.get(instruction)
			val new = transform(old)
			field.setUnchecked(instruction, new)
		}
	}

	/** Operands of type [L2ReadOperand]. */
	private val scalarReadOperandFields:
			List<OperandField<L2ReadOperand<*>>> =
		operandFields
			.filter { L2ReadOperand::class.java.isAssignableFrom(it.type) }
			.cast()

	/** Operands of type [L2ReadVectorOperand]. */
	private val vectorReadOperandFields:
			List<OperandField<L2ReadVectorOperand<*>>> =
		operandFields
			.filter {
				L2ReadVectorOperand::class.java.isAssignableFrom(it.type)
			}.cast()

	/** Operands of type [L2WriteOperand]. */
	private val scalarWriteOperandFields:
			List<OperandField<L2WriteOperand<*>>> =
		operandFields
			.filter { L2WriteOperand::class.java.isAssignableFrom(it.type) }
			.cast()

	/** Operands of type [L2WriteBoxedVectorOperand]. */
	private val vectorWriteOperandFields:
			List<OperandField<L2WriteBoxedVectorOperand>> =
		operandFields
			.filter {
				L2WriteBoxedVectorOperand::class.java.isAssignableFrom(it.type)
			}.cast()

	/** Operands of type [L2PcOperand]. */
	private val scalarPcOperandFields: List<OperandField<L2PcOperand>> =
		operandFields
			.filter { L2PcOperand::class.java.isAssignableFrom(it.type) }
			.cast()

	/** Operands of type [L2PcVectorOperand]. */
	private val vectorPcOperandFields: List<OperandField<L2PcVectorOperand>> =
		operandFields
			.filter { L2PcVectorOperand::class.java.isAssignableFrom(it.type) }
			.cast()

	/**
	 * Iterate over all operand fields, providing both the [L2Operand] in that
	 * field and the corresponding [L2NamedOperandType].
	 */
	fun operandsWithNamedTypesDo(
		instruction: L2NewInstruction,
		consumer: (L2Operand, L2NamedOperandType) -> Unit)
	{
		operandFields.forEach { field ->
    		consumer(field.get(instruction.cast()), field.namedOperandType)
    	}
	}

	/**
	 * Extract the [Array] of [L2Operand]s from the instruction.
	 */
	fun operands(instruction: L2NewInstruction): Array<L2Operand> =
		operandFields.map { it.get(instruction.cast()) }.toTypedArray()

	/**
	 * Extract a list of all [L2ReadOperand]s, even those inside vectors.
	 */
	fun readOperands(
		instruction: L2NewInstruction
	): List<L2ReadOperand<*>> = when
	{
		vectorReadOperandFields.isEmpty() ->
			scalarReadOperandFields.map { it.get(instruction.cast()) }
		else -> mutableListOf<L2ReadOperand<*>>().let { list ->
			scalarReadOperandFields.mapTo(list) { read ->
				read.get(instruction.cast())
			}
			vectorReadOperandFields.flatMapTo(list) { vector ->
				vector.get(instruction.cast()).elements
			}
		}
	}

	/**
	 * Extract a list of all [L2WriteOperand]s, even those inside vectors.
	 */
	fun writeOperands(
		instruction: L2NewInstruction
	): List<L2WriteOperand<*>> = when
	{
		vectorWriteOperandFields.isEmpty() ->
			scalarWriteOperandFields.map { it.get(instruction.cast()) }
		else -> mutableListOf<L2WriteOperand<*>>().let { list ->
			scalarWriteOperandFields.mapTo(list) { write ->
				write.get(instruction.cast())
			}
			vectorWriteOperandFields.flatMapTo(list) { vector ->
				vector.get(instruction.cast()).elements
			}
		}
	}

	/**
	 * Extract a list of all [L2PcOperand]s, even those inside vectors.
	 */
	fun pcOperands(
		instruction: L2NewInstruction
	): List<L2PcOperand> = when
	{
		vectorPcOperandFields.isEmpty() ->
			scalarPcOperandFields.map { it.get(instruction.cast()) }
		else -> mutableListOf<L2PcOperand>().let { list ->
			scalarPcOperandFields.mapTo(list) { edgeField ->
				edgeField.get(instruction.cast())
			}
			vectorPcOperandFields.flatMapTo(list) { edgeVectorField ->
				edgeVectorField.get(instruction.cast()).edges
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
		instruction: I,
		transform: (L2Operand) -> L2Operand)
	{
		operandFields.forEach { operandField ->
			operandField.update(instruction, transform.cast())
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
			instructionClass.findAnnotation<ReadsHiddenVariable>()
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
			instructionClass.findAnnotation<WritesHiddenVariable>()
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
