/*
 * L2Register.kt
 * Copyright © 1993-2021, The Avail Foundation, LLC.
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
package com.avail.interpreter.levelTwo.register

import com.avail.descriptor.representation.AvailObject
import com.avail.interpreter.execution.Interpreter
import com.avail.interpreter.levelTwo.L2Instruction
import com.avail.interpreter.levelTwo.L2Operation
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import com.avail.interpreter.levelTwo.operand.L2ReadFloatOperand
import com.avail.interpreter.levelTwo.operand.L2ReadIntOperand
import com.avail.interpreter.levelTwo.operand.L2ReadOperand
import com.avail.interpreter.levelTwo.operand.L2ReadVectorOperand
import com.avail.interpreter.levelTwo.operand.L2WriteOperand
import com.avail.interpreter.levelTwo.operand.TypeRestriction
import com.avail.interpreter.levelTwo.operand.TypeRestriction.RestrictionFlagEncoding
import com.avail.interpreter.levelTwo.operand.TypeRestriction.RestrictionFlagEncoding.BOXED_FLAG
import com.avail.interpreter.levelTwo.operand.TypeRestriction.RestrictionFlagEncoding.UNBOXED_FLOAT_FLAG
import com.avail.interpreter.levelTwo.operand.TypeRestriction.RestrictionFlagEncoding.UNBOXED_INT_FLAG
import com.avail.interpreter.levelTwo.operation.L2_MOVE
import com.avail.optimizer.L2ControlFlowGraph
import com.avail.optimizer.L2Entity
import com.avail.optimizer.L2Generator
import com.avail.optimizer.reoptimizer.L2Regenerator
import com.avail.optimizer.values.L2SemanticValue
import com.avail.utility.cast
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type

/**
 * `L2Register` models the conceptual use of a register by a [level&#32;two Avail
 * operation][L2Operation] in the [L2Generator].
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @property uniqueValue
 *   A value used to distinguish distinct registers.
 *
 * @constructor
 * Construct a new `L2BoxedRegister`.
 *
 * @param uniqueValue
 *   A value used to distinguish distinct registers.
 */
abstract class L2Register constructor (val uniqueValue: Int) : L2Entity
{
	/**
	 * One of the kinds of registers that Level Two supports.
	 *
	 * @property kindName
	 *   The descriptive name of this register kind.
	 * @property prefix
	 *   The prefix to use for registers of this kind.
	 * @property jvmTypeString
	 *    The JVM [Type] string.
	 * @property loadInstruction
	 *   The JVM instruction that loads a register of this kind.
	 * @property storeInstruction
	 *   The JVM instruction for storing.
	 * @property restrictionFlag
	 *   The [RestrictionFlagEncoding] used to indicate a [TypeRestriction] has
	 *   an available register of this kind.
	 *
	 * @constructor
	 * Create an instance of the enum.
	 *
	 * @param kindName
	 *   A descriptive name for this kind of register.
	 * @param prefix
	 *   The prefix to use when naming registers of this kind.
	 * @param jvmTypeString
	 *   The canonical [String] used to identify this [Type] of register to the
	 *   JVM.
	 * @param loadInstruction
	 *   The JVM instruction for loading.
	 * @param storeInstruction
	 *   The JVM instruction for storing.
	 * @param restrictionFlag
	 *   The corresponding [RestrictionFlagEncoding].
	 */
	enum class RegisterKind constructor (
		val kindName: String,
		val prefix: String,
		val jvmTypeString: String,
		val loadInstruction: Int,
		val storeInstruction: Int,
		val restrictionFlag: RestrictionFlagEncoding)
	{
		/**
		 * The kind of register that holds an [AvailObject].
		 */
		BOXED_KIND(
			"boxed",
			"r",
			Type.getDescriptor(AvailObject::class.java),
			Opcodes.ALOAD,
			Opcodes.ASTORE,
			BOXED_FLAG)
		{
			override fun <R : L2Register, RR : L2ReadOperand<R>> readOperand(
				semanticValue: L2SemanticValue,
				restriction: TypeRestriction,
				register: R): RR
			{
				return L2ReadBoxedOperand(
					semanticValue,
					restriction,
					register as L2BoxedRegister).cast<L2ReadOperand<*>?, RR>()
			}
		},

		/**
		 * The kind of register that holds an [Int].
		 */
		INTEGER_KIND(
			"int",
			"i",
			Type.INT_TYPE.descriptor,
			Opcodes.ILOAD,
			Opcodes.ISTORE,
			UNBOXED_INT_FLAG)
		{
			override fun <R : L2Register, RR : L2ReadOperand<R>> readOperand(
				semanticValue: L2SemanticValue,
				restriction: TypeRestriction,
				register: R): RR
			{
				return L2ReadIntOperand(
					semanticValue,
					restriction,
					register as L2IntRegister).cast<L2ReadOperand<*>?, RR>()
			}
		},

		/**
		 * The kind of register that holds a `double`.
		 */
		FLOAT_KIND(
			"float",
			"f",
			Type.DOUBLE_TYPE.descriptor,
			Opcodes.DLOAD,
			Opcodes.DSTORE,
			UNBOXED_FLOAT_FLAG)
		{
			override fun <R : L2Register, RR : L2ReadOperand<R>> readOperand(
				semanticValue: L2SemanticValue,
				restriction: TypeRestriction,
				register: R): RR
			{
				return L2ReadFloatOperand(
					semanticValue,
					restriction,
					register as L2FloatRegister).cast<L2ReadOperand<*>?, RR>()
			}
		};

		//		/**
		//		 * The kind of register that holds the value of some variable prior to
		//		 * the variable having escaped, if ever.  TODO Implement this.
		//		 */
		//		UNESCAPED_VARIABLE_VALUE

		/**
		 * Answer a suitable [L2ReadOperand] for extracting the indicated
		 * [L2SemanticValue] of this kind.
		 *
		 * @param semanticValue
		 *   The [L2SemanticValue] to consume via an [L2ReadOperand].
		 * @param restriction
		 *   The [TypeRestriction] relevant to this read.
		 * @param register
		 *   The earliest known defining [L2Register] of the [L2SemanticValue].
		 * @return
		 *   The new [L2ReadOperand].
		 * @param R
		 *   The [L2Register] subclass.
		 * @param RR
		 *   The [L2ReadOperand] subclass.
		 */
		abstract fun <R : L2Register, RR : L2ReadOperand<R>> readOperand(
			semanticValue: L2SemanticValue,
			restriction: TypeRestriction,
			register: R): RR

		/**
		 * Answer a suitable [L2_MOVE] operation for transferring values of this
		 * kind.
		 *
		 * @param R
		 *   The [L2Register] subclass.
		 * @param RR
		 *   The [L2ReadOperand] subclass.
		 * @param WR
		 *   The [L2WriteOperand] subclass.
		 * @param RV
		 *   The [L2ReadVectorOperand] subclass.
		 * @return
		 *   The new [L2ReadOperand].
		 */
		fun <
			R : L2Register,
			RR : L2ReadOperand<R>,
			WR : L2WriteOperand<R>,
			RV : L2ReadVectorOperand<R, RR>>
		move(): L2_MOVE<R, RR, WR, RV> = L2_MOVE.moveByKind(this)

		companion object
		{
			/** Don't modify this array. */
			val all = values()
		}
	}

	/**
	 * Answer the kind of register this is. Different register kinds are
	 * allocated from different virtual banks, and do not interfere in terms of
	 * register liveness computation.
	 *
	 * @return The [RegisterKind].
	 */
	abstract fun registerKind(): RegisterKind

	/**
	 * A coloring number to be used by the [interpreter][Interpreter] at runtime
	 * to identify the storage location of a [register][L2Register].
	 */
	private var finalIndex = -1

	/**
	 * Answer the coloring number to be used by the [interpreter][Interpreter]
	 * at runtime to identify the storage location of a [register][L2Register].
	 *
	 * @return
	 * An `L2Register` coloring number.
	 */
	fun finalIndex(): Int = finalIndex

	/**
	 * Set the coloring number to be used by the [interpreter][Interpreter] at
	 * runtime to identify the storage location of an `L2Register`.
	 *
	 * @param theFinalIndex
	 *   An `L2Register` coloring number.
	 */
	fun setFinalIndex(theFinalIndex: Int)
	{
		assert(finalIndex == -1)
		{ "Only set the finalIndex of an L2RegisterIdentity once" }
		finalIndex = theFinalIndex
	}

	/**
	 * The [L2WriteOperand]s that assign to this register.  While the
	 * [L2ControlFlowGraph] is in SSA form, there should be exactly one.
	 */
	private val definitions = mutableSetOf<L2WriteOperand<*>>()

	/**
	 * Record this [L2WriteOperand] in my set of defining write operands.
	 *
	 * @param write
	 *   An [L2WriteOperand] that's an operand of an instruction that writes to
	 *   this register in the control flow graph of basic blocks.
	 */
	fun addDefinition(write: L2WriteOperand<*>)
	{
		definitions.add(write)
	}

	/**
	 * Remove the given [L2WriteOperand] as one of the writers to this register.
	 *
	 * @param write
	 *   The [L2WriteOperand] to remove from my set of defining write operands.
	 */
	fun removeDefinition(write: L2WriteOperand<*>)
	{
		definitions.remove(write)
	}

	/**
	 * Answer the [L2WriteOperand] of an [L2Instruction] which assigns this
	 * register in the SSA control flow graph. It must have been assigned
	 * already, and there must be exactly one (when the control flow graph is in
	 * SSA form).
	 *
	 * @return
	 *   The requested `L2WriteOperand`.
	 */
	fun definition(): L2WriteOperand<*>
	{
		assert(definitions.size == 1)
		return definitions.single()
	}

	/**
	 * Answer the [L2WriteOperand]s which assign this register in the control
	 * flow graph, which is not necessarily in SSA form. It must be non-empty.
	 *
	 * @return
	 *   This register's defining [L2WriteOperand]s.
	 */
	fun definitions(): Collection<L2WriteOperand<*>> = definitions

	/**
	 * The [L2ReadOperand]s of emitted [L2Instruction]s that read from this
	 * register.
	 */
	private val uses = mutableSetOf<L2ReadOperand<*>>()

	/**
	 * Capture another [L2ReadOperand] of an emitted [L2Instruction] that uses
	 * this register.
	 *
	 * @param read
	 *   The [L2ReadOperand] that reads from this register.
	 */
	fun addUse(read: L2ReadOperand<*>)
	{
		uses.add(read)
	}

	/**
	 * Drop a use of this register by an [L2ReadOperand] of an [L2Instruction]
	 * that is now dead.
	 *
	 * @param read
	 *   An [L2ReadOperand] that no longer reads from this register because its
	 *   instruction has been removed.
	 */
	fun removeUse(read: L2ReadOperand<*>)
	{
		uses.remove(read)
	}

	/**
	 * Answer the [Set] of [L2ReadOperand]s that read from this register.
	 * Callers must not modify the returned collection.
	 *
	 * @return
	 *   A [Set] of [L2ReadOperand]s.
	 */
	fun uses(): Set<L2ReadOperand<*>> = uses

	/**
	 * Answer a new register like this one.
	 *
	 * @param generator
	 *   The [L2Generator] for which copying is requested.
	 * @return
	 *   The new `L2Register`.
	 */
	abstract fun copyForTranslator(generator: L2Generator): L2Register

	/**
	 * Answer a new register like this one, but where the uniqueValue has been
	 * set to the finalIndex.
	 *
	 * @return
	 *   The new `L2Register`.
	 */
	abstract fun copyAfterColoring(): L2Register

	/**
	 * Answer a copy of the receiver. Subclasses can be covariantly stronger in
	 * the return type.
	 *
	 * @param regenerator
	 *   The [L2Regenerator] for which copying is requested.
	 * @return
	 *   A copy of the receiver.
	 */
	abstract fun copyForRegenerator(regenerator: L2Regenerator): L2Register

	/**
	 * Answer the prefix for non-constant registers. This is used only for
	 * register printing.
	 *
	 * @return
	 *   The prefix.
	 */
	fun namePrefix(): String = registerKind().prefix

	override fun toString(): String
	{
		val builder = StringBuilder()
		builder.append(namePrefix())
		if (finalIndex() != -1)
		{
			builder.append(finalIndex())
		}
		else
		{
			builder.append(uniqueValue)
		}
		return builder.toString()
	}
}
