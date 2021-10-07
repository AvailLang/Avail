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

package com.avail.interpreter.levelOne

import com.avail.descriptor.atoms.A_Atom.Companion.atomName
import com.avail.descriptor.bundles.A_Bundle.Companion.message
import com.avail.descriptor.character.A_Character.Companion.isCharacter
import com.avail.descriptor.functions.A_RawFunction
import com.avail.descriptor.functions.A_RawFunction.Companion.declarationNames
import com.avail.descriptor.functions.A_RawFunction.Companion.lineNumberEncodedDeltas
import com.avail.descriptor.functions.A_RawFunction.Companion.literalAt
import com.avail.descriptor.functions.A_RawFunction.Companion.numArgs
import com.avail.descriptor.functions.A_RawFunction.Companion.codeStartingLineNumber
import com.avail.descriptor.functions.CompiledCodeDescriptor
import com.avail.descriptor.functions.CompiledCodeDescriptor.L1InstructionDecoder
import com.avail.descriptor.representation.A_BasicObject
import com.avail.descriptor.representation.AbstractDescriptor.DebuggerObjectSlots
import com.avail.descriptor.representation.AvailObject
import com.avail.descriptor.representation.AvailObjectFieldHelper
import com.avail.descriptor.tuples.A_Tuple.Companion.tupleIntAt
import com.avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tupleFromList
import com.avail.descriptor.types.A_Type.Companion.instance
import com.avail.descriptor.types.PrimitiveTypeDescriptor
import com.avail.descriptor.types.PrimitiveTypeDescriptor.Types.MESSAGE_BUNDLE
import com.avail.descriptor.types.PrimitiveTypeDescriptor.Types.METHOD
import com.avail.descriptor.types.PrimitiveTypeDescriptor.Types.NUMBER
import com.avail.utility.Strings
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
	 * Extract the names of arg/local/constant declarations from the raw
	 * function.
	 */
	val declarationNames = code.declarationNames.map { it.asNativeString() }

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
	 * @property builder
	 *   The [StringBuilder] onto which to describe the level one instructions.
	 * @property recursionMap
	 *   The (mutable) [IdentityHashMap] of [A_BasicObject]s to avoid recursing
	 *   into while printing the [level&#32;one][L1Operation].
	 * @property indent
	 *   The number of tabs to output after each line break.
	 */
	fun print(
		builder: StringBuilder,
		recursionMap: IdentityHashMap<A_BasicObject, Void>,
		indent: Int,
		highlightPc: Int = -1)
	{
		val tabs = Strings.repeated("\t", indent)
		val visitor = object : L1DisassemblyVisitor
		{
			override fun startOperation(
				operation: L1Operation,
				pc: Int,
				line: Int)
			{
				if (pc != 1)
				{
					builder.append("\n")
				}
				if (pc == highlightPc) {
					builder.append(" \uD83D\uDD35==> ")
				}
				builder.append("$tabs$pc. [:$line] ")
				builder.append(operation.name)
				if (operation.operandTypes.isNotEmpty()) {
					builder.append("(")
				}
			}

			override fun betweenOperands()
			{
				builder.append(", ")
			}

			override fun endOperation(operation: L1Operation)
			{
				if (operation.operandTypes.isNotEmpty())
				{
					builder.append(")")
				}
			}

			override fun doImmediate(index: Int)
			{
				builder.append("immediate=$index")
			}

			override fun doLiteral(index: Int)
			{
				builder.append("literal#$index")
				val literal = code.literalAt(index)
				builder.append(" = ")
				literal.printOnAvoidingIndent(builder, recursionMap, indent + 1)
			}

			override fun doLocal(index: Int)
			{
				when {
					index <= code.numArgs() -> builder.append("arg#$index")
					else -> builder.append("local#${index - code.numArgs()}")
				}
			}

			override fun doOuter(index: Int)
			{
				builder.append("outer#$index")
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
				if (pc == highlightPc) {
					nameBuilder.append(" \uD83D\uDD35==> ")
				}
				nameBuilder.append("$pc. [:$line] ${operation.shortName()}")
				if (operation.operandTypes.isNotEmpty()) {
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
				slots.add(AvailObjectFieldHelper(
					code,
					DebuggerObjectSlots("Instruction"),
					currentOperationPc,
					tupleFromList(operandValues),
					forcedName = nameBuilder.toString(),
					forcedChildren = operandValues.toTypedArray()))
			}

			override fun doImmediate(index: Int)
			{
				nameBuilder.append("immediate=$index")
			}

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
			private fun simplePrintable(value: AvailObject) =
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
					!value.isType -> value to true
					value.isTop -> value to false
					value.traversed().descriptor() is PrimitiveTypeDescriptor ->
						value to false
					value.isBottom -> value to false
					else -> null to true
				}

			override fun doLiteral(index: Int)
			{
				nameBuilder.append("literal#$index")
				val value = code.literalAt(index)
				val (print, expand) = simplePrintable(value)
				if (expand) operandValues.add(value)
				if (print !== null)
				{
					nameBuilder.append(" = $print")
				}
				else if (value.isInstanceMeta)
				{
					val instance = value.instance
					val (print2, _) = simplePrintable(instance)
					if (print2 !== null)
					{
						nameBuilder.append(" = $print2")
					}
					else if (instance.isInstanceMeta)
					{
						val instanceInstance = instance.instance
						val (print3, _) = simplePrintable(instanceInstance)
						if (print3 !== null)
						{
							nameBuilder.append(" = $print3")
						}
					}
				}
			}

			override fun doLocal(index: Int)
			{
				val name = declarationNames[index - 1]
				nameBuilder.append(
					when {
						index <= code.numArgs() -> "arg#$index = $name"
						else -> "local#${index - code.numArgs()} = $name"
					})
			}

			override fun doOuter(index: Int)
			{
				nameBuilder.append("outer#$index")
			}
		}
		visit(visitor)
		return slots
	}
}
