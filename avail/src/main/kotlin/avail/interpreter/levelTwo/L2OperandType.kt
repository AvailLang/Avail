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

import avail.descriptor.bundles.A_Bundle
import avail.descriptor.methods.DefinitionDescriptor
import avail.descriptor.representation.AvailObject
import avail.interpreter.Primitive
import avail.interpreter.levelTwo.L2NamedOperandType.Purpose
import avail.interpreter.levelTwo.operand.L2CommentOperand
import avail.interpreter.levelTwo.operand.L2ConstantOperand
import avail.interpreter.levelTwo.operand.L2FloatImmediateOperand
import avail.interpreter.levelTwo.operand.L2IntImmediateOperand
import avail.interpreter.levelTwo.operand.L2PcOperand
import avail.interpreter.levelTwo.operand.L2PcVectorOperand
import avail.interpreter.levelTwo.operand.L2PrimitiveOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operand.L2ReadIntOperand
import avail.interpreter.levelTwo.operand.L2ReadVectorOperand
import avail.interpreter.levelTwo.operand.L2SelectorOperand
import avail.interpreter.levelTwo.operand.L2WriteBoxedOperand
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
enum class L2OperandType
constructor(
	val canHavePurpose: Boolean = false)
{
	/**
	 * An [L2ConstantOperand] holds a Java object of any type, except
	 * [AvailObject], which should be handled with a [CONSTANT] operand.
	 */
	ARBITRARY_CONSTANT,

	/**
	 * An [L2ConstantOperand] holds a specific [AvailObject].  The value is
	 * always made immutable for safety during L2 code generation.  And then
	 * made shared prior to installing the [L2Chunk].
	 */
	CONSTANT,

	/**
	 * An [L2IntImmediateOperand] holds an [Int] value.
	 */
	INT_IMMEDIATE,

	/**
	 * An [L2FloatImmediateOperand] holds a `double` value.
	 */
	FLOAT_IMMEDIATE,

	/**
	 * An [L2PcOperand] holds an offset into the chunk's instructions,
	 * presumably for the purpose of branching there at some time and under some
	 * condition.
	 */
	PC(true),

	/**
	 * An [L2PrimitiveOperand] holds a [Primitive] to be invoked.
	 */
	PRIMITIVE,

	/**
	 * Like a [CONSTANT], the [L2SelectorOperand] holds the actual AvailObject,
	 * but it is known to be an [A_Bundle].  The [L2Chunk] depends on this
	 * bundle, invalidating itself if its [definitions][DefinitionDescriptor]
	 * change.
	 */
	SELECTOR,

	/**
	 * The [L2ReadBoxedOperand] holds the [L2BoxedRegister] that
	 * will be read.
	 */
	READ_BOXED,

	/**
	 * The [L2WriteBoxedOperand] holds the [L2BoxedRegister] that
	 * will be written.
	 */
	WRITE_BOXED(true),

	/**
	 * The [L2ReadIntOperand] holds the [L2IntRegister] that
	 * will be read.
	 */
	READ_INT,

	/**
	 * The [L2WriteIntOperand] holds the [L2IntRegister] that
	 * will be written.
	 */
	WRITE_INT(true),

	/**
	 * The [L2WriteFloatOperand] holds the [L2FloatRegister] that
	 * will be read.
	 */
	READ_FLOAT,

	/**
	 * The [L2WriteFloatOperand] holds the [L2FloatRegister] that
	 * will be written.
	 */
	WRITE_FLOAT(true),

	/**
	 * The [L2ReadVectorOperand] holds a [List] of [L2ReadBoxedOperand]s which
	 * will be read.
	 */
	READ_BOXED_VECTOR,

	/**
	 * The [L2ReadVectorOperand] holds a [List] of [L2ReadBoxedOperand]s which
	 * will be read.
	 */
	READ_INT_VECTOR,

	/**
	 * The [L2ReadVectorOperand] holds a [List] of [L2ReadBoxedOperand]s which
	 * will be read.
	 */
	READ_FLOAT_VECTOR,

	/**
	 * The [L2PcVectorOperand] holds a [List] of [L2PcOperand]s which can be
	 * the targets of a multi-way jump.
	 */
	PC_VECTOR(true),

	/**
	 * The [L2CommentOperand] holds descriptive text that does not affect
	 * analysis or execution of level two code.  It is for diagnostic purposes
	 * only.
	 */
	COMMENT;

	/**
	 * Create a [L2NamedOperandType] from the receiver and a [String] naming its
	 * role within some [L2Operation].
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
	 * role within some [L2Operation], and a designator of its
	 * [purpose][Purpose].  The purpose is used to designate branch edges, and
	 * correlate them to register writes that only happen if the corresponding
	 * edge is taken.
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
}
