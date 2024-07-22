/*
 * L2OperandType.kt
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
package avail.interpreter.levelTwo

import avail.descriptor.representation.AvailObject
import avail.interpreter.levelTwo.L2NamedOperandType.Purpose
import avail.interpreter.levelTwo.operand.L2ArbitraryConstantOperand
import avail.interpreter.levelTwo.operand.L2CommentOperand
import avail.interpreter.levelTwo.operand.L2ConstantOperand
import avail.interpreter.levelTwo.operand.L2FloatImmediateOperand
import avail.interpreter.levelTwo.operand.L2IntImmediateOperand
import avail.interpreter.levelTwo.operand.L2Operand
import avail.interpreter.levelTwo.operand.L2PcOperand
import avail.interpreter.levelTwo.operand.L2PcVectorOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedVectorOperand
import avail.interpreter.levelTwo.operand.L2ReadFloatOperand
import avail.interpreter.levelTwo.operand.L2ReadFloatVectorOperand
import avail.interpreter.levelTwo.operand.L2ReadIntOperand
import avail.interpreter.levelTwo.operand.L2ReadIntVectorOperand
import avail.interpreter.levelTwo.operand.L2ReadVectorOperand
import avail.interpreter.levelTwo.operand.L2WriteBoxedOperand
import avail.interpreter.levelTwo.operand.L2WriteBoxedVectorOperand
import avail.interpreter.levelTwo.operand.L2WriteFloatOperand
import avail.interpreter.levelTwo.operand.L2WriteIntOperand
import avail.interpreter.levelTwo.register.L2BoxedRegister
import avail.interpreter.levelTwo.register.L2FloatRegister
import avail.interpreter.levelTwo.register.L2IntRegister

/**
 * An `L2OperandType` specifies the nature of a level two operand.  It doesn't
 * fully specify how the operand is used, but it does say whether the associated
 * register is being read or written or both.
 *
 * @constructor
 *
 * @property canHavePurpose
 *   Whether this kind of operand can have a [Purpose].
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
sealed class L2OperandType
constructor(
	val operandClass: Class<out L2Operand>,
	val canHavePurpose: Boolean = false)
{
	/**
	 * The ordinals ascend in the order of definition of the instances.
	 */
	val ordinal = counter++

	/**
	 * Create an [L2NamedOperandType] from the receiver and a [String] naming
	 * its role within some [L2Instruction].
	 *
	 * @param roleName
	 *   The name of this operand.
	 * @return A named operand type.
	 */
	fun named(roleName: String): L2NamedOperandType
	{
		return L2NamedOperandType(this, roleName, null)
	}

	/**
	 * Create a [L2NamedOperandType] from the receiver, a [String] naming its
	 * role within some [L2Instruction], and a designator of its [Purpose].
	 * The purpose is used to designate branch edges, and correlate them to
	 * register writes that only happen if the corresponding edge is taken.
	 *
	 * @param roleName
	 *   The name of this operand.
	 * @param purpose
	 *   The [Purpose] that best describes the [L2NamedOperandType].
	 * @return A named operand type.
	 */
	fun named(roleName: String, purpose: Purpose): L2NamedOperandType
	{
		assert(canHavePurpose)
		return L2NamedOperandType(this, roleName, purpose)
	}

	companion object
	{
		// Note that these are ORDERED, so that fields with an earlier operand
		// type will be processed (by [L2Instruction.instructionWasAdded])
		// before ones with a later operand type, after taking into
		// consideration whether a field has a [Purpose].
		private var counter = 0

		/**
		 * The [L2CommentOperand] holds descriptive text that does not affect
		 * analysis or execution of level two code.  It is for diagnostic
		 * purposes only.
		 */
		object COMMENT : L2OperandType(L2CommentOperand::class.java)

		/**
		 * An [L2ConstantOperand] holds a specific [AvailObject].  The value is
		 * always made immutable for safety during L2 code generation.  And then
		 * made shared prior to installing the [L2Chunk].
		 */
		object CONSTANT : L2OperandType(L2ConstantOperand::class.java)

		/**
		 * An [L2ConstantOperand] holds a Java object of any type, except
		 * [AvailObject], which should be handled with a [CONSTANT] operand.
		 */
		object ARBITRARY_CONSTANT : L2OperandType(
			L2ArbitraryConstantOperand::class.java)

		/**
		 * An [L2IntImmediateOperand] holds an [Int] value.
		 */
		object INT_IMMEDIATE : L2OperandType(L2IntImmediateOperand::class.java)

		/**
		 * An [L2FloatImmediateOperand] holds a `double` value.
		 */
		object FLOAT_IMMEDIATE : L2OperandType(
			L2FloatImmediateOperand::class.java)

		/**
		 * The [L2ReadBoxedOperand] holds the [L2BoxedRegister] that will be
		 * read.
		 */
		object READ_BOXED : L2OperandType(L2ReadBoxedOperand::class.java)

		/**
		 * The [L2ReadIntOperand] holds the [L2IntRegister] that will be read.
		 */
		object READ_INT : L2OperandType(L2ReadIntOperand::class.java)

		/**
		 * The [L2WriteFloatOperand] holds the [L2FloatRegister] that will be
		 * read.
		 */
		object READ_FLOAT : L2OperandType(L2ReadFloatOperand::class.java)

		/**
		 * The [L2ReadVectorOperand] holds a [List] of [L2ReadBoxedOperand]s
		 * which will be read.
		 */
		object READ_BOXED_VECTOR : L2OperandType(
			L2ReadBoxedVectorOperand::class.java)

		/**
		 * The [L2ReadVectorOperand] holds a [List] of [L2ReadBoxedOperand]s
		 * which will be read.
		 */
		object READ_INT_VECTOR : L2OperandType(
			L2ReadIntVectorOperand::class.java)

		/**
		 * The [L2ReadVectorOperand] holds a [List] of [L2ReadBoxedOperand]s
		 * which will be read.
		 */
		object READ_FLOAT_VECTOR : L2OperandType(
			L2ReadFloatVectorOperand::class.java)

		/**
		 * The [L2WriteBoxedOperand] holds the [L2BoxedRegister] that will be
		 * written.
		 */
		object WRITE_BOXED : L2OperandType(
			L2WriteBoxedOperand::class.java,
			true)

		/**
		 * The [L2WriteIntOperand] holds the [L2IntRegister] that will be
		 * written.
		 */
		object WRITE_INT : L2OperandType(
			L2WriteIntOperand::class.java,
			true)

		/**
		 * The [L2WriteFloatOperand] holds the [L2FloatRegister] that will be
		 * written.
		 */
		object WRITE_FLOAT : L2OperandType(
			L2WriteFloatOperand::class.java,
			true)

		/**
		 * The [L2WriteBoxedVectorOperand] holds a [List] of
		 * [L2WriteBoxedOperand]s which will all be written.
		 */
		object WRITE_BOXED_VECTOR : L2OperandType(
			L2WriteBoxedVectorOperand::class.java)

		/**
		 * An [L2PcOperand] holds an offset into the chunk's instructions,
		 * presumably for the purpose of branching there at some time and under
		 * some condition.
		 */
		object PC : L2OperandType(
			L2PcOperand::class.java,
			true)

		/**
		 * The [L2PcVectorOperand] holds a [List] of [L2PcOperand]s which can be
		 * the targets of a multi-way jump.
		 */
		object PC_VECTOR : L2OperandType(L2PcVectorOperand::class.java, true)
	}
}

object OperandTypeMap
{
	/** The set of all [L2OperandType]s. */
	val allOperandTypes: Set<L2OperandType>

	/** A private map from [L2Operand] class to its [L2OperandType]. */
	private val privateOperandTypeByOperandClass:
		Map<Class<out L2Operand>, L2OperandType>

	init
	{
		// Force all singletons to be instantiated, and therefore added to
		// privateAllOperandTypes and privateOperandTypeByOperandClass.
		val operandClassToOperandType =
			mutableMapOf<Class<out L2Operand>, L2OperandType>()
		val allTypes = mutableSetOf<L2OperandType>()
		L2OperandType::class.sealedSubclasses.forEach { subclass ->
			val operandType = subclass.objectInstance!!
			val operandClass = operandType.operandClass
			operandClassToOperandType[operandClass] = operandType
			allTypes.add(operandType)
		}
		privateOperandTypeByOperandClass = operandClassToOperandType
		allOperandTypes = allTypes
	}

	/**
	 * Given an [L2Operand] class, get its [L2OperandType].
	 */
	fun operandTypeForOperandClass(
		operandClass: Class<out L2Operand>
	): L2OperandType = privateOperandTypeByOperandClass[operandClass]!!
}
