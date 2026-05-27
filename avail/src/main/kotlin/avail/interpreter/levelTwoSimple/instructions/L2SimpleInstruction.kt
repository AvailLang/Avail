/*
 * L2SimpleInstruction.kt
 * Copyright © 1993-2026, The Avail Foundation, LLC.
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
@file:Suppress("MemberVisibilityCanBePrivate")

package avail.interpreter.levelTwoSimple.instructions

import avail.descriptor.functions.A_RawFunction.Companion.methodName
import avail.descriptor.representation.AvailObject
import avail.descriptor.types.CompiledCodeTypeDescriptor.Companion.mostGeneralCompiledCodeType
import avail.descriptor.types.FunctionTypeDescriptor.Companion.mostGeneralFunctionType
import avail.exceptions.unsupported
import avail.interpreter.execution.Interpreter
import avail.interpreter.levelTwo.L1InstructionStepper
import avail.interpreter.levelTwo.L2AbstractInstruction
import avail.interpreter.levelTwo.L2Chunk
import avail.interpreter.levelTwo.L2SimpleChunk
import avail.interpreter.levelTwoSimple.L2SimpleInstructionTransformer
import avail.interpreter.levelTwoSimple.StateOfL1
import avail.interpreter.levelTwoSimple.instructions.registers.Offset
import avail.interpreter.levelTwoSimple.instructions.registers.Read
import avail.interpreter.levelTwoSimple.instructions.registers.RegisterSet
import avail.interpreter.levelTwoSimple.instructions.registers.Write
import avail.interpreter.levelTwoSimple.instructions.registers.WriteArray
import avail.interpreter.primitive.Primitive
import avail.interpreter.primitive.Primitive.Flag
import avail.interpreter.primitive.controlflow.P_InvokeWithTuple
import avail.optimizer.DefaultL1ExecutableChunk
import avail.optimizer.DefaultL1ExecutableChunk.DefaultEntryPoint
import avail.optimizer.jvm.JVMChunk
import avail.utility.mapToSet
import kotlin.reflect.KVisibility
import kotlin.reflect.full.memberProperties

/**
 * [L2SimpleInstruction] is the abstract class for a simplified level two
 * instruction set.  It acts as a very lightweight translation of level one
 * nybblecodes.  Its invalidation machinery is managed by [L2Chunk], so it
 * subscribes to method dependencies the same way as for full chunks that are
 * translated to [JVMChunk]s.
 *
 * Very little optimization is performed at this level.  Type deduction helps
 * eliminate spurious checks that would be necessary if method definitions could
 * be added or removed, but the invalidation mechanism handles that.  Calls can
 * often be statically transformed to simple monomorphic invocation, avoiding
 * the dispatch trees.  When the target is proven to be monomorphic, some
 * primitives can be directly embedded (e.g., [P_InvokeWithTuple]), if they can
 * be proven not to fail, skipping unnecessary type safety checks.  Similarly,
 * unnecessary return type checks can often be omitted as well.
 *
 * There is no register coloring, dead code elimination, special rewriting of
 * most primitives, or reworking into unboxed integer or floating point
 * operations.  Folding is attempted, however, if a monomorphic call indicates
 * it would be a [Primitive] function with the [Flag.CanFold] flag set.
 *
 * Flow is linear, and each instruction is responsible for handling reification,
 * if it can happen.  Boxed register values are maintained in an array, in the
 * same manner as for [L1InstructionStepper].  The current function occupies
 * `register[0]`, then the frame slots.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
sealed class L2SimpleInstruction
constructor(
	open val nextOffset: Offset
): L2AbstractInstruction
{
	/**
	 * Perform this instruction, a single step of an [L2SimpleChunk].  The
	 * mutable [Array] of [AvailObject]s acts as a simple set of registers.
	 * Element 0 is the current function, and the remaining elements correspond
	 * with the continuation slots, should one need to be constructed.
	 *
	 * @param registers
	 *   The current set of registers representing the stack fraame's state,
	 *   which may be modified.
	 * @param interpreter
	 *   The [Interpreter] that is executing this instruction.
	 * @return
	 *   `true` if execution should continue to the next instruction, or `false`
	 *   if reification or an Avail return is needed.
	 */
	abstract fun step(
		registers: RegisterSet,
		interpreter: Interpreter
	): Offset

	/**
	 * For instructions that can reenter, and only for those instructions, it's
	 * possible that the containing chunk has become invalid due to
	 * deoptimization (because of method definition changes, etc).  In that
	 * case, this method will provide the fallback [DefaultEntryPoint] within
	 * the [DefaultL1ExecutableChunk] for that kind of instruction.
	 */
	open fun defaultL1EntryPointIfInvalid(): DefaultEntryPoint =
		unsupported

	/**
	 * A previously constructed continuation is being resumed in some way, and
	 * the instruction *just before* the continuation's L2 offset has been asked
	 * to do anything specific to reentering the continuation.  For example, a
	 * method call might be forced to reify the stack, but at resumption time
	 * (i.e., when "returning" into it), it will still need to check the type of
	 * the "returned" value against the expected type.
	 *
	 * Most instructions are not suitable places for reentry.
	 *
	 * @param registers
	 *   The current set of registers representing the stack fraame's state,
	 *   which may be modified.
	 * @param interpreter
	 *   The [Interpreter] that is executing this instruction.
	 * @return
	 *   Answer `true` if execution should continue to the next instruction, or
	 * ` false` if reification or an Avail return is needed.
	 */
	open fun reenter(
		registers: RegisterSet,
		interpreter: Interpreter
	): Boolean = throw RuntimeException("Should not reenter here")

	/**
	 * Transform this instruction's reads, writes, and jump offsets to produce
	 * another one of the same type.
	 */
	abstract fun L2SimpleInstructionTransformer.transformed(
	): L2SimpleInstruction

	/**
	 * Answer whether this instruction can be safely postponed, assuming nobody
	 * needs its output yet.
	 */
	open val canBePostponed: Boolean get() = true

	open fun forEachWrite(withWrite: (Write) -> Unit)
	{
		// Big hack, but not too big.  Ask for a copy and listen for
		// transformation of writes.
		val transformer = object : L2SimpleInstructionTransformer() {
			override fun write(write: Write): Write
			{
				withWrite(write)
				return write
			}
		}
		// Ignore the returned copy.
		transformer.transformed()
	}

	open fun forEachRead(withRead: (Read) -> Unit)
	{
		// Big hack, but not too big.  Ask for a copy and listen for
		// transformation of reads.
		val transformer = object : L2SimpleInstructionTransformer() {
			override fun read(read: Read): Read
			{
				withRead(read)
				return read
			}
		}
		// Ignore the returned copy.
		transformer.transformed()
	}

	open val allWrites: List<Write> get() = buildList { forEachWrite(::add) }

	open val allReads: List<Read> get() = buildList { forEachRead(::add) }

	override fun toString(): String = toString(-1)

	fun toString(currentOffset: Int) = buildString {
		val cls = this@L2SimpleInstruction::class

		val pairs = cls.memberProperties
			.filter { it.name !in excludedFieldNames }
			.filter { it.visibility == KVisibility.PUBLIC }
			.mapNotNull {
				val value = it.getter.call(this@L2SimpleInstruction)
				if (it.name != L2SimpleInstruction::nextOffset.name
					|| value !is Offset
					|| (value != Offset.NEXT
						&& value != Offset(currentOffset + 1)))
				{
					it.name to value
				}
				else null
			}
		val (writes, nonwrites) = pairs
			.partition { it.second is Write || it.second is WriteArray }
		when (writes.size)
		{
			0 -> {  }
			1 ->
			{
				append(fieldValueToString(writes.single().second))
				append(" ::= ")
			}
			else ->
			{
				writes.joinTo(
					this,
					", ",
					"(",
					") ::= ")
				{ (name, value) ->
					name + ":" + fieldValueToString(value)
				}
			}
		}
		append(cls.simpleName!!.removePrefix("L2Simple_"))
		val nonwriteStrings = nonwrites
			.map { (name, value) -> name to fieldValueToString(value) }
		if (nonwriteStrings.any { (_, value) -> '\n' in value }
			|| nonwriteStrings.sumOf { (_, value) -> value.length } > 50)
		{
			nonwriteStrings
				.joinTo(this, ",\n\t", "(\n\t", ")") { (a,b) -> "$a=$b" }
		}
		else
		{
			nonwriteStrings.joinTo(this, ", ", "(", ")") { (a,b) -> "$a=$b" }
		}
	}

	/**
	 * Generate a suitable print representation of the value.
	 */
	fun fieldValueToString(value: Any?): String = when (value)
	{
		null -> "null"
		is Array<*> ->
			value.joinToString(",", "[", "]") { fieldValueToString(it) }
		is IntArray -> value.joinToString(",", "[", "]") {
			fieldValueToString(it.toString())
		}
		is LongArray -> value.joinToString(",", "[", "]") {
			fieldValueToString(it.toString())
		}
		is ByteArray -> value.joinToString(",", "[", "]") {
			fieldValueToString(it.toString())
		}
		is StateOfL1 -> value.run {
			"(pc=$pc,stackp=$stackp,slots=$liveSlots),all=$allLiveRegisters"
		}
		is AvailObject -> when
		{
			value.isInstanceOf(mostGeneralCompiledCodeType()) ->
				value.methodName.toString()
			value.isInstanceOf(mostGeneralFunctionType) ->
				value.code().methodName.toString()
			else -> value.toString()
		}
		is Offset -> value.toString()
		else -> value.toString()
	}

	companion object
	{
		val excludedFieldNames: Set<String> =
			L2SimpleInstruction::class.memberProperties.mapToSet { it.name } -
				L2SimpleInstruction::nextOffset.name

	}
}
