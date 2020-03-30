/*
 * L1Disassembler.kt
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

package com.avail.interpreter.levelOne

import com.avail.descriptor.A_BasicObject
import com.avail.descriptor.AvailObject.Companion.error
import com.avail.descriptor.functions.A_RawFunction
import com.avail.descriptor.functions.CompiledCodeDescriptor
import com.avail.descriptor.functions.CompiledCodeDescriptor.L1InstructionDecoder
import java.util.*

/**
 * An instance of `L1Disassembler` converts a [compiled code
 * object][CompiledCodeDescriptor] into a textual representation of its sequence
 * of [level one operations][L1Operation] and their [operands][L1OperandType].
 *
 * @property code
 *   The [compiled code object][CompiledCodeDescriptor] being disassembled.
 * @property builder
 *   The [StringBuilder] onto which to describe the level one instructions.
 * @property recursionMap
 *   The (mutable) [IdentityHashMap] of [A_BasicObject]s to avoid recursing into
 *   while printing the [level one][L1Operation].
 * @property indent
 *   The number of tabs to output after each line break.
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @constructor
 *
 * Parse the given compiled code object into a sequence of L1 instructions,
 * printing them on the provided stream.
 *
 * @param code
 *   The [code][CompiledCodeDescriptor] to decompile.
 * @param builder
 *   Where to write the decompilation.
 * @param recursionMap
 *   Which objects are already being visited.
 * @param indent
 *   The indentation level.
 */
class L1Disassembler private constructor(
	internal val code: A_RawFunction,
	internal val builder: StringBuilder,
	internal val recursionMap: IdentityHashMap<A_BasicObject, Void>,
	internal val indent: Int)
{
	/** The current position in the code. */
	internal val instructionDecoder = L1InstructionDecoder()

	/**
	 * An [L1OperandTypeDispatcher] suitably specialized to decode and print the
	 * instruction operands.
	 */
	private val operandTypePrinter: L1OperandTypeDispatcher =
		object : L1OperandTypeDispatcher
		{
			override fun doImmediate()
			{
				builder.append("immediate=").append(instructionDecoder.operand)
			}

			override fun doLiteral()
			{
				val index = instructionDecoder.operand
				builder.append("literal#").append(index).append("=")
				code.literalAt(index).printOnAvoidingIndent(
					builder,
					recursionMap,
					indent + 1)
			}

			override fun doLocal()
			{
				val index = instructionDecoder.operand
				if (index <= code.numArgs())
				{
					builder.append("arg#").append(index)
				}
				else
				{
					builder.append("local#").append(index - code.numArgs())
				}
			}

			override fun doOuter()
			{
				builder.append("outer#").append(instructionDecoder.operand)
			}

			override fun doExtension()
			{
				error("Extension nybblecode should be dealt with another way.")
			}
		}

	init
	{
		code.setUpInstructionDecoder(instructionDecoder)
		instructionDecoder.pc(1)
		var first = true
		while (!instructionDecoder.atEnd())
		{
			if (!first)
			{
				builder.append("\n")
			}
			first = false
			(indent downTo 1).forEach { _ -> builder.append("\t") }
			builder.append(instructionDecoder.pc()).append(": ")

			val operation = instructionDecoder.operation
			val operandTypes = operation.operandTypes
			builder.append(operation.name)
			if (operandTypes.isNotEmpty())
			{
				builder.append("(")
				operandTypes.indices.forEach { i ->
					if (i > 0)
					{
						builder.append(", ")
					}
					operandTypes[i].dispatch(operandTypePrinter)
				}
				builder.append(")")
			}
		}
	}

	companion object
	{
		/**
		 * Parse the given compiled code object into a sequence of L1
		 * instructions, printing them on the provided stream.
		 *
		 * @param code
		 *   The [code][CompiledCodeDescriptor] to decompile.
		 * @param builder
		 *   Where to write the decompilation.
		 * @param recursionMap
		 *   Which objects are already being visited.
		 * @param indent
		 *   The indentation level.
		 */
		@JvmStatic
		fun disassemble(
			code: A_RawFunction,
			builder: StringBuilder,
			recursionMap: IdentityHashMap<A_BasicObject, Void>,
			indent: Int)
		{
			// The constructor does all the work...
			L1Disassembler(code, builder, recursionMap, indent)
		}
	}
}
