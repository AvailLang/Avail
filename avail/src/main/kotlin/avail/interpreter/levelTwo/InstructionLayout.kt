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

package avail.interpreter.levelTwo

import avail.interpreter.levelTwo.L2NamedOperandType.Purpose
import avail.interpreter.levelTwo.L2OperandType.Companion.operandTypeForOperandClass
import avail.interpreter.levelTwo.operand.L2Operand
import avail.interpreter.levelTwo.operand.L2PcOperand
import avail.interpreter.levelTwo.operand.L2PcVectorOperand
import avail.interpreter.levelTwo.operand.L2ReadOperand
import avail.interpreter.levelTwo.operand.L2ReadVectorOperand
import avail.interpreter.levelTwo.operand.L2WriteBoxedVectorOperand
import avail.interpreter.levelTwo.operand.L2WriteOperand
import avail.utility.cast
import avail.utility.compareChained
import avail.utility.ifZero
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KProperty1
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.superclasses
import kotlin.reflect.jvm.javaField

/**
 * A helper class that an [L2Instruction] uses to access its operands in a
 * simple, mostly typesafe way.
 *
 * @property instructionClass
 *   The Kotlin [KClass] for the [L2Instruction] subclass that this layout
 *   describes.
 *
 * @constructor
 *   Create an [InstructionLayout] for navigating the [L2Operand]s of the given
 *   [KClass] (on some [L2Instruction]).
 */
class InstructionLayout<I : L2Instruction>
internal constructor(
	private val instructionClass: KClass<out I>,
	parentLayout: InstructionLayout<*>?)
{
	/**
	 * An instance of [OperandField] is created for each field of an
	 * [L2Instruction] that contains an [L2Operand].
	 *
	 * @param O
	 *   The subclass of [L2Operand] that occurs in the corresponding field.
	 *
	 * @constructor
	 *   Create an [OperandField] for use in an [InstructionLayout] used by all
	 *   instances of ome [L2Instruction].
	 * @param property
	 *   The Kotlin `var` property of the [L2Instruction] in which the
	 *   [L2Operand] is stored.
	 */
	private inner class OperandField<O: L2Operand>
	constructor(
		private val property: KMutableProperty1<I, O>
	): Comparable<OperandField<*>>
	{
		/** The Java [Class] for the field's type, [O] (an [L2Operand]). */
		val type: Class<O> get() = property.javaField!!.type.cast()

		/** The name of this property. */
		val name: String = property.name

		/**
		 * The getter for reading the field from a provided [L2Instruction]
		 * of the appropriate subtype ([I]).
		 */
		private val getter = property.getter

		/**
		 * The setter for writing the field into a provided [L2Instruction]
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
		val namedOperandType: L2NamedOperandType = property.javaField!!.run {
			L2NamedOperandType(
				operandTypeForOperandClass(this@OperandField.type),
				name,
				getAnnotation(On::class.java)?.purpose,
				isAnnotationPresent(HideInSimpleVisualization::class.java),
				isAnnotationPresent(HideInAllVisualizations::class.java))
		}

		/**
		 * Read the field from an [L2Instruction] of the required type.
		 */
		fun get(instruction: L2Instruction): O = getter(instruction.cast())

		/**
		 * Write the field in an [L2Instruction] of the required type.
		 */
		fun set(instruction: L2Instruction, operand: O) =
			setter(instruction.cast(), operand)

		/**
		 * Write the field, but allowing the [operand]'s type to be checked at
		 * runtime.
		 */
		fun setUnchecked(instruction: L2Instruction, operand: L2Operand) =
			setter(instruction.cast(), operand.cast())

		/**
		 * Read this field from the [instruction], pass it through the
		 * [transformation], and write it back into the instruction.
		 */
		fun update(
			instruction: L2Instruction,
			transformation: (L2Operand)->L2Operand
		) = set(instruction, transformation(get(instruction)).cast())

		/**
		 * Sort fields as
		 *   1. no-[Purpose], ordered by the operand type, then
		 *   2. [Purpose]ful, ordered by operand type.
		 * Break ties by field name.
		 */
		override fun compareTo(other: OperandField<*>): Int
		{
			return (namedOperandType.purpose == null)
				.compareTo(other.namedOperandType.purpose == null)
				.ifZero {
					namedOperandType.operandType.ordinal
						.compareTo(other.namedOperandType.operandType.ordinal)
						.ifZero {
							name.compareTo(other.name)
						}
				}

		}

		override fun toString(): String = "${type.simpleName}.$name"
	}

	/**
	 * The name to present as the basic instruction name.
	 */
	val name = instructionClass.simpleName!!
		.removePrefix("L2_")
		.split("_")
		.joinToString("") { it.lowercase().replaceFirstChar(Char::uppercase) }

	/** The list of [OperandField]s, in declaration order. */
	private val operandFields: List<OperandField<out L2Operand>>

	init
	{
		// Make sure there aren't any accidental val fields, since that won't
		// work for the things we need to do to fields.  It's fine to have val
		// fields for non-L2Operands.
		val valFields = instructionClass.memberProperties
			.filterIsInstance<KProperty1<I, L2Operand>>()
			.filter { it !is KMutableProperty1<*, *> }
			.filter { it.javaField !== null }
			.filter {
				L2Operand::class.java.isAssignableFrom(it.javaField!!.type)
			}
		assert(valFields.isEmpty())
		{
			"Found val fields (${valFields.map { it.name }}) in " +
				"instruction class ($instructionClass).  They must be var."
		}

		// Require all val and var properties that have a backing field to be
		// L2Operands.
		val illegalFields = instructionClass.declaredMemberProperties
			.filterIsInstance<KProperty1<I, *>>()
			.filter { it.javaField != null }
			.filterNot {
				L2Operand::class.java.isAssignableFrom(it.javaField!!.type)
			}
		if (illegalFields.isNotEmpty())
		{
			val names = illegalFields.map {
				"${it.name} (${it.returnType})"
			}
			println(
				"${instructionClass.simpleName} " +
					"has non-L2Operand var fields: $names")
		}
		//assert(illegalFields.isEmpty()) {
		//	"${instructionClass.simpleName} " +
		//		"has non-L2Operand var fields: $illegalFields"
		//}

		// In Kotlin/JVM, `declaredFields` seems to produce the fields in
		// declaration order, so this is a handy sorting index for preserving
		// that when starting with the declared properties.
		val parentOperandFields = parentLayout?.operandFields ?: emptyList()
		val fieldNumbering = instructionClass.java.declaredFields
			.withIndex()
			.associate { (i, field) -> field to i }

		val localFields = instructionClass.declaredMemberProperties
			.filterIsInstance<KMutableProperty1<I, out L2Operand>>()
			.filter {
				L2Operand::class.java.isAssignableFrom(it.javaField!!.type)
			}
			.sortedBy { fieldNumbering[it.javaField!!] }
			.map { OperandField(it) }
		operandFields = (parentOperandFields + localFields)
			.sortedWith(operandComparator).cast()
	}

	/**
	 * Transform this instruction's operands, writing them back.  The
	 * transformer must preserve the type of each operand, otherwise the attempt
	 * to write it back will fail.
	 *
	 * @param instruction
	 *   The instruction to alter, which must be of a type suitable for this
	 *   layout to manipulate.
	 * @param transform
	 *   A function that maps an [L2Operand] into a replacement operand of the
	 *   same [Class].
	 */
	fun updateOperands(
		instruction: L2Instruction,
		transform: (L2Operand) -> L2Operand)
	{
		operandFields.forEach { field ->
			field.setUnchecked(instruction, transform(field.get(instruction)))
		}
	}

	/**
	 * Create a List of [OperandField]s metting the reified [L2Operand]
	 * subtype.
	 */
	private inline fun <reified OperandClass: L2Operand> filterOperands(
		operandType: KClass<OperandClass>
	): List<OperandField<OperandClass>>
	{
		return operandFields
			.filter { operandType.java.isAssignableFrom(it.type) }
			.cast()!!
	}

	/** Operands of type [L2ReadOperand]. */
	private val scalarReadOperandFields = filterOperands(L2ReadOperand::class)

	/** Operands of type [L2ReadVectorOperand]. */
	private val vectorReadOperandFields =
		filterOperands(L2ReadVectorOperand::class)

	/** Operands of type [L2WriteOperand]. */
	private val scalarWriteOperandFields = filterOperands(L2WriteOperand::class)

	/** Operands of type [L2WriteBoxedVectorOperand]. */
	private val vectorWriteOperandFields =
		filterOperands(L2WriteBoxedVectorOperand::class)

	/** Operands of type [L2PcOperand]. */
	private val scalarPcOperandFields = filterOperands(L2PcOperand::class)

	/** Operands of type [L2PcVectorOperand]. */
	private val vectorPcOperandFields = filterOperands(L2PcVectorOperand::class)

	/**
	 * Iterate over all operand fields, providing both the [L2Operand] in that
	 * field and the corresponding [L2NamedOperandType].
	 */
	fun operandsWithNamedTypesDo(
		instruction: L2Instruction,
		consumer: (L2Operand, L2NamedOperandType) -> Unit)
	{
		operandFields.forEach { field ->
			consumer(field.get(instruction), field.namedOperandType)
		}
	}

	/**
	 * Extract the [Array] of [L2Operand]s from the instruction.
	 */
	fun operands(instruction: L2Instruction): List<L2Operand> =
		operandFields.map { it.get(instruction) }

	/**
	 * Extract a list of all [L2ReadOperand]s, even those inside vectors.
	 */
	fun readOperands(
		instruction: L2Instruction
	): List<L2ReadOperand<*>> = when
	{
		vectorReadOperandFields.isEmpty() ->
			scalarReadOperandFields.map { it.get(instruction) }
		else -> mutableListOf<L2ReadOperand<*>>().also { list ->
			operandFields.forEach { operandField ->
				operandField.get(instruction).addReadsTo(list)
			}
		}
	}

	/**
	 * Extract a list of all [L2WriteOperand]s, even those inside vectors.
	 */
	fun writeOperands(
		instruction: L2Instruction
	): List<L2WriteOperand<*>> = when
	{
		vectorWriteOperandFields.isEmpty() ->
			scalarWriteOperandFields.map { it.get(instruction) }
		else -> mutableListOf<L2WriteOperand<*>>().also { list ->
			operandFields.forEach { operandField ->
				operandField.get(instruction).addWritesTo(list)
			}
		}
	}

	/**
	 * Extract a list of all [L2PcOperand]s, even those inside vectors.
	 */
	fun pcOperands(
		instruction: L2Instruction
	): List<L2PcOperand> = when
	{
		vectorPcOperandFields.isEmpty() ->
			scalarPcOperandFields.map { it.get(instruction) }
		else -> mutableListOf<L2PcOperand>().also { list ->
			operandFields.forEach { operandField ->
				operandField.get(instruction).addEdgesTo(list)
			}
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

	companion object
	{
		/** A map from [L2Instruction] subclass to its [InstructionLayout]. */
		private val layoutsByClass:
				MutableMap<KClass<out L2Instruction>, InstructionLayout<*>> =
			ConcurrentHashMap(100)

		/**
		 * Look up or create and cache the [InstructionLayout] for the given
		 * [KClass] of an [L2Instruction] subclass.
		 */
		fun layoutForClass(
			instructionClass: KClass<out L2Instruction>
		): InstructionLayout<*>?
		{
			if (instructionClass == L2Instruction::class) return null
			val parentLayout = layoutForClass(
				instructionClass.superclasses.single().cast())
			return layoutsByClass.computeIfAbsent(instructionClass) { cls ->
				InstructionLayout(cls, parentLayout)
			}
		}

		/**
		 * A [Comparator] suitable for ordering the [OperandField]s within an
		 * [InstructionLayout].
		 */
		private val operandComparator =
			compareChained<InstructionLayout<*>.OperandField<*>>(
				{ it.namedOperandType.purpose !== null },
				{ it.namedOperandType.operandType.ordinal })
	}
}
