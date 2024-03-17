/*
 * RegisterKind.kt
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
package avail.interpreter.levelTwo.register

import avail.descriptor.representation.AvailObject
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operand.L2ReadFloatOperand
import avail.interpreter.levelTwo.operand.L2ReadIntOperand
import avail.interpreter.levelTwo.operand.L2ReadOperand
import avail.interpreter.levelTwo.operand.TypeRestriction
import avail.interpreter.levelTwo.operand.TypeRestriction.RestrictionFlagEncoding
import avail.interpreter.levelTwo.operand.TypeRestriction.RestrictionFlagEncoding.BOXED_FLAG
import avail.interpreter.levelTwo.operand.TypeRestriction.RestrictionFlagEncoding.UNBOXED_FLOAT_FLAG
import avail.interpreter.levelTwo.operand.TypeRestriction.RestrictionFlagEncoding.UNBOXED_INT_FLAG
import avail.interpreter.levelTwo.operation.L2_MOVE
import avail.optimizer.values.L2SemanticBoxedValue
import avail.optimizer.values.L2SemanticUnboxedFloat
import avail.optimizer.values.L2SemanticUnboxedInt
import avail.optimizer.values.L2SemanticValue
import avail.utility.cast
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type

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
 * @property Self
 *   The receiver's statically determinable type.
 *
 * @constructor
 * Create an instance of the enum.
 *
 * @param ordinal
 *   A unique [Int] for each instance.
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
sealed class RegisterKind<Self : RegisterKind<Self>>
constructor (
	val ordinal: Int,
	val kindName: String,
	val prefix: String,
	val jvmTypeString: String,
	val loadInstruction: Int,
	val storeInstruction: Int,
	val restrictionFlag: RestrictionFlagEncoding)
{
	/**
	 * Answer a suitable [L2ReadOperand] for extracting the indicated
	 * [L2SemanticValue] of this kind.
	 *
	 * @param semanticValue
	 *   The [L2SemanticValue] to consume via an [L2ReadOperand].
	 * @param restriction
	 *   The [TypeRestriction] relevant to this read.
	 * @param register
	 *   The earliest known defining [RegisterKind] of the [L2SemanticValue].
	 * @return
	 *   The new [L2ReadOperand].
	 */
	abstract fun readOperand(
		semanticValue: L2SemanticValue<Self>,
		restriction: TypeRestriction,
		register: L2Register<Self>
	): L2ReadOperand<Self>

	/**
	 * Answer a suitable [L2_MOVE] operation for transferring values of this
	 * kind.
	 */
	fun move(): L2_MOVE<Self> =
		L2_MOVE.movesByKind[this]!!.cast()

	companion object
	{
		/** Don't modify this array. */
		val all: Array<RegisterKind<*>> = arrayOf(
			BOXED_KIND,
			INTEGER_KIND,
			FLOAT_KIND)
	}
}


/**
 * The kind of register that holds an [AvailObject].
 */
object BOXED_KIND : RegisterKind<BOXED_KIND>(
	ordinal = 0,
	kindName = "boxed",
	prefix = "r",
	jvmTypeString = Type.getDescriptor(AvailObject::class.java),
	loadInstruction = Opcodes.ALOAD,
	storeInstruction = Opcodes.ASTORE,
	restrictionFlag = BOXED_FLAG)
{
	override fun readOperand(
		semanticValue: L2SemanticValue<BOXED_KIND>,
		restriction: TypeRestriction,
		register: L2Register<BOXED_KIND>
	): L2ReadBoxedOperand
	{
		return L2ReadBoxedOperand(
			semanticValue as L2SemanticBoxedValue,
			restriction,
			register as L2BoxedRegister)
	}
}

/**
 * The kind of register that holds an [Int].
 */
object INTEGER_KIND : RegisterKind<INTEGER_KIND>(
	ordinal = 1,
	kindName = "int",
	prefix = "i",
	jvmTypeString = Type.INT_TYPE.descriptor,
	loadInstruction = Opcodes.ILOAD,
	storeInstruction = Opcodes.ISTORE,
	restrictionFlag = UNBOXED_INT_FLAG)
{
	override fun readOperand(
		semanticValue: L2SemanticValue<INTEGER_KIND>,
		restriction: TypeRestriction,
		register: L2Register<INTEGER_KIND>
	): L2ReadIntOperand
	{
		return L2ReadIntOperand(
			semanticValue as L2SemanticUnboxedInt,
			restriction,
			register as L2IntRegister)
	}
}

/**
 * The kind of register that holds a `double`.
 */
object FLOAT_KIND : RegisterKind<FLOAT_KIND>(
	ordinal = 2,
	kindName = "float",
	prefix = "f",
	jvmTypeString = Type.DOUBLE_TYPE.descriptor,
	loadInstruction = Opcodes.DLOAD,
	storeInstruction = Opcodes.DSTORE,
	restrictionFlag = UNBOXED_FLOAT_FLAG)
{
	override fun readOperand(
		semanticValue: L2SemanticValue<FLOAT_KIND>,
		restriction: TypeRestriction,
		register: L2Register<FLOAT_KIND>
	): L2ReadFloatOperand
	{
		return L2ReadFloatOperand(
			semanticValue as L2SemanticUnboxedFloat,
			restriction,
			register as L2FloatRegister)
	}
}

//		/**
//		 * The kind of register that holds the value of some variable prior to
//		 * the variable having escaped, if ever.  TODO Implement this.
//		 */
//		UNESCAPED_VARIABLE_VALUE
