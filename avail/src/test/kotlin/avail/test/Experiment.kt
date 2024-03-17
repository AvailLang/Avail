/*
 * Experiment.kt
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

package avail.test

import avail.descriptor.representation.NilDescriptor.Companion.nil
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.i32
import avail.interpreter.levelTwo.L2Instruction
import avail.interpreter.levelTwo.L2NamedOperandType.Purpose
import avail.interpreter.levelTwo.L2NamedOperandType.Purpose.FAILURE
import avail.interpreter.levelTwo.L2NamedOperandType.Purpose.SUCCESS
import avail.interpreter.levelTwo.operand.L2Operand
import avail.interpreter.levelTwo.operand.L2PcOperand
import avail.interpreter.levelTwo.operand.L2ReadIntOperand
import avail.interpreter.levelTwo.operand.L2ReadOperand
import avail.interpreter.levelTwo.operand.L2ReadVectorOperand
import avail.interpreter.levelTwo.operand.L2WriteIntOperand
import avail.interpreter.levelTwo.operand.L2WriteOperand
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.intRestrictionForType
import avail.optimizer.L2BasicBlock
import avail.optimizer.L2Generator
import avail.optimizer.OptimizationLevel
import avail.optimizer.jvm.JVMTranslator
import avail.optimizer.values.Frame
import avail.optimizer.values.L2SemanticUnboxedInt
import avail.utility.cast
import org.junit.jupiter.api.Test
import org.objectweb.asm.MethodVisitor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

// This is an experiment to rework L2Instruction to have its own hierarchy,
// rather than holding an L2Operation and array of L2Operands.  This allows both
// general access (e.g., the list of the instruction's operands) and access
// that's specific to the instruction subclass (an Add instruction's augend).
//
// It currently uses reflection to minimize boilerplate in subclasses, but it
// might be possible to reintroduce a small amount of per-subclass boilerplate
// to avoid reflection entirely.


abstract class Instruction
{
	// Current L2Instruction requires this.
	var basicBlock: L2BasicBlock? = null

	// Current L2Instruction requires this.
	var offset = -1

	// We'll use the layout at least once per instruction, probably more, so we
	// may as well look it up proactively.
	private val layout: InstructionLayout = layoutsByClass[javaClass]?:
		layoutsByClass.computeIfAbsent(javaClass, ::InstructionLayout)

	// Use the layout to extract the operands.
	val operands: List<L2Operand> get() = layout.operands(this)

	// Use the layout to extract just the read-operands, including the reads
	// inside vector-reads (these reads are *not* produced by [operands], above.
	val readOperands: List<L2ReadOperand<*>> get() = layout.readOperands(this)

	// Use the layout to extract just the write-operands.
	fun writeOperands() = javaClass.fields
		.filter { L2WriteOperand::class.java.isAssignableFrom(it.type) }
		.map { it.get(this) }

	// Use the layout's operandFields for the operand names and other
	// information shared by instances of the same instruction subclass.
	override fun toString() = buildString {
		append("${this@Instruction.javaClass.simpleName}:\n\t")
		layout.operandFields.joinTo(this, ",\n\t") {
			"${it.name} = ${it.get(this@Instruction)}"
		}
	}

	// Current L2Instruction has this.
	abstract fun translateToJVM(
		translator: JVMTranslator,
		method: MethodVisitor,
		instruction: L2Instruction)



	// An alternative approach to enumerating operands.
	abstract fun mapOperands(transformer: (L2Operand)->L2Operand)

	// Writes back the same values, leaving them unchanged.
	fun operandsDo(body: (L2Operand)->Unit)
	{
		mapOperands {
			body(it)
			it
		}
	}

	fun readOperandsDo(body: (L2ReadOperand<*>)->Unit)
	{
		mapOperands {
			when (it)
				{
					is L2ReadOperand<*> -> body(it)
				}
			it
		}
	}

	fun writeOperandsDo(body: (L2WriteOperand<*>)->Unit)
	{
		mapOperands {
			when (it)
			{
				is L2WriteOperand<*> -> body(it)
			}
			it
		}
	}



	companion object
	{
		// Access to an existing layout generally involves no locks or
		// modification of contended memory.
		val layoutsByClass =
			ConcurrentHashMap<Class<Instruction>, InstructionLayout>(100)
	}
}

// One per class.  See layoutsByClass a little bit above, and the
// Instruction.layout field.
class InstructionLayout
constructor(private val instructionClass: Class<Instruction>)
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

		private val setterMethod: Method = run {
			instructionClass.getDeclaredMethod(
				"set" + name.replaceFirstChar(Char::titlecase), type)
		}

		val purpose = javaField.getAnnotation(On::class.java)?.purpose

		fun get(instruction: Instruction): O =
			getterMethod.invoke(instruction).cast()

		fun set(instruction: Instruction, operand: O) {
			setterMethod.invoke(instruction, operand)
		}

		fun update(
			instruction: Instruction,
			transformation: (O)->O)
		{
			set(instruction, transformation(get(instruction)))
		}
	}


	val operandFields = instructionClass.declaredFields
		.filter { L2Operand::class.java.isAssignableFrom(it.type) }
		.map { OperandField<L2Operand>(it) }

	@Suppress("UNCHECKED_CAST")
	private val scalarReadOperandFields: List<OperandField<L2ReadOperand<*>>> =
		operandFields
			.filter { L2ReadOperand::class.java.isAssignableFrom(it.type) }
			as List<OperandField<L2ReadOperand<*>>>

	@Suppress("UNCHECKED_CAST")
	private val vectorReadOperandFields:
			List<OperandField<L2ReadVectorOperand<*>>> =
		operandFields
			.filter {
				L2ReadVectorOperand::class.java.isAssignableFrom(it.type)
			} as List<OperandField<L2ReadVectorOperand<*>>>

	@Suppress("UNCHECKED_CAST")
	private val writeOperandFields: List<OperandField<L2WriteOperand<*>>> =
		operandFields
			.filter { L2WriteOperand::class.java.isAssignableFrom(it.type) }
			as List<OperandField<L2WriteOperand<*>>>

	fun operands(instruction: Instruction) =
		operandFields.map { it.get(instruction) }

	fun readOperands(instruction: Instruction): List<L2ReadOperand<*>> = when
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

	fun writeOperands(instruction: Instruction): List<L2WriteOperand<*>> =
		writeOperandFields.map { it.get(instruction) }
}


@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class On constructor(val purpose: Purpose)



class L2_ADD_INTS constructor(
	var augend: L2ReadIntOperand,
	var addend: L2ReadIntOperand,
	@On(SUCCESS) var sum: L2WriteIntOperand,
	@On(FAILURE) var outOfRange: L2PcOperand,
	@On(SUCCESS) var inRange: L2PcOperand
) : Instruction()
{
	override fun translateToJVM(
		translator: JVMTranslator,
		method: MethodVisitor,
		instruction: L2Instruction)
	{
		// No more need to extract the augend operand from a clumsy array, and
		// to strengthen its type!
		translator.load(method, augend.register())
		//...
	}

	override fun mapOperands(transformer: (L2Operand)->L2Operand)
	{
		// This pattern occurs once per class.
		augend = transformer(augend).cast()
		addend = transformer(addend).cast()
		sum = transformer(sum).cast()
		outOfRange = transformer(outOfRange).cast()
		inRange = transformer(inRange).cast()
	}
}



class ExampleUsage
{
	@Test
	fun testPrintInstruction()
	{
		val generator = L2Generator(
			OptimizationLevel.FIRST_JVM_TRANSLATION,
			Frame(null, nil, "code name", "top frame"))
		val startBlock = generator.createBasicBlock(
			"START for ${generator.topFrame.codeName}")
		startBlock.makeIrremovable()
		generator.startBlock(startBlock)
		val semanticTemp = generator.newTemp()
		val write = generator.intWrite(
			setOf(L2SemanticUnboxedInt(semanticTemp)),
			intRestrictionForType(i32))
		val add = L2_ADD_INTS(
			augend = generator.unboxedIntConstant(10),
			addend = generator.unboxedIntConstant(20),
			sum = write,
			outOfRange = generator.unreachablePcOperand(),
			inRange = generator.unreachablePcOperand())
		//gen.addInstruction(instruction)
		// Just check that toString doesn't crash.
		add.toString()
	}
}
