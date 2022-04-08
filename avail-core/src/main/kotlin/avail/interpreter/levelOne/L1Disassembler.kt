/*
 * L1Disassembler.kt
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

package avail.interpreter.levelOne

import avail.descriptor.atoms.A_Atom.Companion.atomName
import avail.descriptor.bundles.A_Bundle.Companion.message
import avail.descriptor.character.A_Character.Companion.isCharacter
import avail.descriptor.functions.A_RawFunction
import avail.descriptor.functions.A_RawFunction.Companion.codeStartingLineNumber
import avail.descriptor.functions.A_RawFunction.Companion.declarationNames
import avail.descriptor.functions.A_RawFunction.Companion.lineNumberEncodedDeltas
import avail.descriptor.functions.A_RawFunction.Companion.literalAt
import avail.descriptor.functions.A_RawFunction.Companion.numArgs
import avail.descriptor.functions.A_RawFunction.Companion.numOuters
import avail.descriptor.functions.CompiledCodeDescriptor
import avail.descriptor.functions.CompiledCodeDescriptor.L1InstructionDecoder
import avail.descriptor.numbers.A_Number.Companion.equalsInt
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.AbstractDescriptor.DebuggerObjectSlots.DUMMY_DEBUGGER_SLOT
import avail.descriptor.representation.AvailObject
import avail.descriptor.representation.AvailObjectFieldHelper
import avail.descriptor.tuples.A_Tuple.Companion.tupleIntAt
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tupleFromList
import avail.descriptor.types.A_Type.Companion.instance
import avail.descriptor.types.A_Type.Companion.instanceCount
import avail.descriptor.types.PrimitiveTypeDescriptor
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.MESSAGE_BUNDLE
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.METHOD
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.NUMBER
import avail.descriptor.types.TupleTypeDescriptor.Companion.mostGeneralTupleType
import avail.descriptor.types.TupleTypeDescriptor.Companion.stringType
import avail.descriptor.types.VariableTypeDescriptor.Companion.mostGeneralVariableType
import avail.utility.Strings
import java.util.IdentityHashMap

/**
 * An instance of `L1Disassembler` converts a
 * [compiled&#32;code&#32;object][CompiledCodeDescriptor] into a textual
 * representation of its sequence of [level&#32;one&#32;operations][L1Operation]
 * and their [operands][L1OperandType].
 *
 * @property code
 *   The [compiled&#32;code object][CompiledCodeDescriptor] being disassembled.
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @constructor
 *
 * @param code
 *   The [code][CompiledCodeDescriptor] to decompile.
 */
class L1Disassembler constructor(
	internal val code: A_RawFunction)
{
	interface L1DisassemblyVisitor : L1OperandTypeDispatcher
	{
		/**
		 * The given L1Operation was just encountered, and its operands will be
		 * visited before the corresponding [endOperation] is called.
		 *
		 * @param operation
		 *   The [L1Operation] that was encountered.
		 * @param pc
		 *   The nybblecode index at which the operation occurs.
		 * @param line
		 *   The source code line number associated with this operation.
		 */
		fun startOperation(operation: L1Operation, pc: Int, line: Int)

		/**
		 * We're between processing operands of an operation.
		 */
		fun betweenOperands()

		/**
		 * The given [L1Operation] has now been completely processed.
		 *
		 * @param operation
		 *   The operation that we're ending.
		 */
		fun endOperation(operation: L1Operation)
	}

	/**
	 * Extract the names of arg/local/constant/label/outer declarations from the
	 * raw function.
	 */
	val allDeclarationNames = code.declarationNames.map { it.asNativeString() }

	/**
	 * Visit the given [L1DisassemblyVisitor] with my [L1Operation]s and
	 * [Int]-valued [L1OperandType].
	 *
	 * @param visitor
	 *   The [L1DisassemblyVisitor] to tell about the operations and operands.
	 */
	fun visit(visitor: L1DisassemblyVisitor) = with (L1InstructionDecoder()) {
		val encodedDeltas = code.lineNumberEncodedDeltas
		var lineNumber = code.codeStartingLineNumber
		var instructionCounter = 1
		code.setUpInstructionDecoder(this@with)
		pc(1)
		while (!atEnd())
		{
			// Track the line number change from this operation.
			val encodedDelta = encodedDeltas.tupleIntAt(instructionCounter++)
			val decodedDelta =
				if (encodedDelta and 1 == 0) encodedDelta shr 1
				else -(encodedDelta shr 1)
			lineNumber += decodedDelta

			val pc = pc()
			val operation = getOperation()
			visitor.startOperation(operation, pc, lineNumber)
			operation.operandTypes.forEachIndexed { i, operandType ->
				if (i > 0) visitor.betweenOperands()
				operandType.dispatch(visitor, getOperand())
			}
			visitor.endOperation(operation)
		}
	}

	/**
	 * Output the disassembly to the given StringBuilder.
	 *
	 * @param builder
	 *   The [StringBuilder] onto which to describe the level one instructions.
	 * @param recursionMap
	 *   The (mutable) [IdentityHashMap] of [A_BasicObject]s to avoid recursing
	 *   into while printing the [level&#32;one][L1Operation].
	 * @param indent
	 *   The number of tabs to output after each line break.
	 * @param highlightPc
	 *   The optional pc at which to show a highlight.
	 */
	fun print(
		builder: StringBuilder,
		recursionMap: IdentityHashMap<A_BasicObject, Void>,
		indent: Int,
		highlightPc: Int = -1)
	{
		val tabs = Strings.repeated("\t", indent)
		printInstructions(recursionMap, indent) { pc, line, string ->
			if (pc != 1)
			{
				builder.append("\n")
			}
			if (pc == highlightPc)
			{
				builder.append(" \uD83D\uDD35==> ")
			}
			builder.append("$tabs$pc. [:$line] $string")
		}
	}

	/**
	 * Output the disassembly, one instruction at a time, to the provided
	 * function that takes the instruction pc and print representation.
	 *
	 * @param recursionMap
	 *   The (mutable) [IdentityHashMap] of [A_BasicObject]s to avoid recursing
	 *   into while printing the [level&#32;one][L1Operation].
	 * @param indent
	 *   The number of tabs to output after each line break.
	 * @param action
	 *   What to do with each pc, source line number, and corresponding
	 *   disassembled instruction.
	 */
	fun printInstructions(
		recursionMap: IdentityHashMap<A_BasicObject, Void>,
		indent: Int,
		action: (Int, Int, String)->Unit)
	{
		val tempBuilder = StringBuilder()
		var instructionPc = -1
		var instructionLine = -1
		val visitor = object : L1DisassemblyVisitor
		{
			override fun startOperation(
				operation: L1Operation,
				pc: Int,
				line: Int)
			{
				instructionPc = pc
				instructionLine = line
				tempBuilder.append(operation.shortName())
				if (operation.operandTypes.isNotEmpty()) {
					tempBuilder.append(" ")
				}
			}

			override fun betweenOperands()
			{
				tempBuilder.append(", ")
			}

			override fun endOperation(operation: L1Operation)
			{
				action(instructionPc, instructionLine, tempBuilder.toString())
				tempBuilder.clear()
			}

			override fun doImmediate(index: Int)
			{
				tempBuilder.append("#$index")
			}

			override fun doLiteral(index: Int)
			{
				val value = code.literalAt(index)
				val (print, _) = simplePrintable(value)
				when (print)
				{
					null -> value.printOnAvoidingIndent(
						tempBuilder, recursionMap, indent + 1)
					else -> tempBuilder.append(print)
				}
			}

			override fun doLocal(index: Int)
			{
				val name = allDeclarationNames[index - 1]
				tempBuilder.append(name)
			}

			override fun doOuter(index: Int)
			{
				val i = index + (allDeclarationNames.size - code.numOuters)
				val name = allDeclarationNames[i - 1]
				tempBuilder.append(name)
			}
		}
		visit(visitor)
	}

	/**
	 * Output the disassembly to the given {@link List} of
	 * {@link AvailObjectFieldHelper}s.
	 *
	 * @return
	 *   A [List] of [AvailObjectFieldHelper]s, one per disassembled
	 *   instruction.
	 */
	fun disassembledAsSlots(
		highlightPc: Int = -1
	): List<AvailObjectFieldHelper>
	{
		val slots = mutableListOf<AvailObjectFieldHelper>()
		var currentOperationPc: Int = Int.MIN_VALUE
		val operandValues = mutableListOf<AvailObject>()
		val nameBuilder = StringBuilder()

		val visitor = object : L1DisassemblyVisitor
		{
			override fun startOperation(
				operation: L1Operation,
				pc: Int,
				line: Int)
			{
				currentOperationPc = pc
				operandValues.clear()
				nameBuilder.clear()
				if (pc == highlightPc)
				{
					nameBuilder.append(" \uD83D\uDD35==> ")
				}
				nameBuilder.append("$pc. [:$line] ${operation.shortName()}")
				if (operation.operandTypes.isNotEmpty())
				{
					nameBuilder.append(" (")
				}
			}

			override fun betweenOperands()
			{
				nameBuilder.append(", ")
			}

			override fun endOperation(operation: L1Operation)
			{
				if (operation.operandTypes.isNotEmpty())
				{
					nameBuilder.append(")")
				}
				slots.add(
					AvailObjectFieldHelper(
						code,
						DUMMY_DEBUGGER_SLOT,
						currentOperationPc,
						tupleFromList(operandValues),
						slotName = "Instruction",
						forcedName = nameBuilder.toString(),
						forcedChildren = operandValues.toTypedArray()
					)
				)
			}

			override fun doImmediate(index: Int)
			{
				nameBuilder.append("immediate=$index")
			}

			override fun doLiteral(index: Int)
			{
				nameBuilder.append("literal#$index")
				val value = code.literalAt(index)
				printIfSimple(value, nameBuilder, operandValues)
			}

			override fun doLocal(index: Int)
			{
				val name = allDeclarationNames[index - 1]
				nameBuilder.append(
					when
					{
						index <= code.numArgs() -> "arg#$index = $name"
						else -> "local#${index - code.numArgs()} = $name"
					}
				)
			}

			override fun doOuter(index: Int)
			{
				val i = index + (allDeclarationNames.size - code.numOuters)
				val name = allDeclarationNames[i - 1]
				nameBuilder.append("outer#$index = $name")
			}
		}
		visit(visitor)
		return slots
	}

	private fun printIfSimple(
		value: AvailObject,
		builder: StringBuilder,
		operandValues: MutableList<AvailObject>? = null,
		depth: Int = 0)
	{
		if (depth > 3)
		{
			builder.append("***depth***")
			return
		}
		val (print, expand) = simplePrintable(value)
		if (depth == 0 && expand && operandValues != null)
		{
			operandValues.add(value)
		}
		if (print !== null)
		{
			builder.append(" = $print")
			return
		}
		if (value.isInstanceOf(mostGeneralVariableType))
		{
			// Allow
			builder.append("var(")
			val variableValue = value.value()
			printIfSimple(variableValue, builder, null, depth + 1)
			builder.append(")")
			return
		}
		if (!value.isType || !value.instanceCount.equalsInt(1)) return
		// It's an instance type or instance meta.
		val instance = value.instance
		val (print2, _) = simplePrintable(instance)
		if (print2 !== null)
		{
			builder.append(" = $print2's type")
			return
		}
		if (!instance.isType || !instance.instanceCount.equalsInt(1)) return
		// It's an instance meta.
		val instanceInstance = instance.instance
		val (print3, _) = simplePrintable(instanceInstance)
		if (print3 !== null)
		{
			builder.append(" = $print3's type's type")
		}
		else
		{
			builder.append(" = (some metatype)")
		}
	}

	companion object
	{
		/**
		 * Answer two things: (1) What object to print directly after the
		 * operand index, or null if none; (2) Whether to present the object
		 * as a sub-object for navigating in the debugger.
		 *
		 * When invoked on the instance of an instanceMeta (or the
		 * instance's instance of a meta-metatype) the nullity of the first
		 * value determines whether the original value should be printed,
		 * and the second value is ignored.
		 *
		 * @param value
		 *        The value to check for simple printability.
		 * @return Whether to print the value instead of deconstructing it.
		 */
		fun simplePrintable(value: AvailObject) =
			when {
				// Show some things textually.
				value.isNil -> value to false
				value.isString -> value to false
				value.isInstanceOf(NUMBER.o) -> value to false
				value.isInstanceOf(MESSAGE_BUNDLE.o) ->
					value.message.atomName to true
				value.isInstanceOf(METHOD.o) -> value to true
				value.isAtom -> value.atomName to true
				value.isCharacter -> value to false
				value.equals(mostGeneralTupleType) -> value to false
				value.equals(stringType) -> value to false
				value.isInstanceOf(mostGeneralVariableType) -> null to true
				!value.isType -> value to true
				value.isTop -> value to false
				value.traversed().descriptor() is PrimitiveTypeDescriptor ->
					value to false
				value.isBottom -> value to false
				else -> null to true
			}
	}
}
