/*
 * L2OperandType.java
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

package com.avail.interpreter.levelTwo;

import com.avail.descriptor.DefinitionDescriptor;
import com.avail.descriptor.bundles.A_Bundle;
import com.avail.interpreter.Primitive;
import com.avail.interpreter.levelTwo.L2NamedOperandType.Purpose;
import com.avail.interpreter.levelTwo.operand.*;
import com.avail.interpreter.levelTwo.register.L2BoxedRegister;
import com.avail.interpreter.levelTwo.register.L2FloatRegister;
import com.avail.interpreter.levelTwo.register.L2IntRegister;

import java.util.List;
import java.util.concurrent.atomic.LongAdder;


/**
 * An {@code L2OperandType} specifies the nature of a level two operand.  It
 * doesn't fully specify how the operand is used, but it does say whether the
 * associated register is being read or written or both.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public enum L2OperandType
{
	/**
	 * An {@link L2ConstantOperand} holds a specific AvailObject.  The value is
	 * always made immutable for safety during L2 code generation.  And then
	 * made shared prior to installing the {@link L2Chunk}.
	 */
	CONSTANT,

	/**
	 * An {@link L2IntImmediateOperand} holds an {@code int} value.
	 */
	INT_IMMEDIATE,

	/**
	 * An {@link L2FloatImmediateOperand} holds a {@code double} value.
	 */
	FLOAT_IMMEDIATE,

	/**
	 * An {@link L2PcOperand} holds an offset into the chunk's instructions,
	 * presumably for the purpose of branching there at some time and under some
	 * condition.
	 */
	PC,

	/**
	 * An {@link L2PrimitiveOperand} holds a {@link Primitive} to be invoked.
	 */
	PRIMITIVE,

	/**
	 * Like a {@link #CONSTANT}, the {@link L2SelectorOperand} holds the actual
	 * AvailObject, but it is known to be an {@link A_Bundle}.  The {@link
	 * L2Chunk} depends on this bundle, invalidating itself if its {@link
	 * DefinitionDescriptor definitions} change.
	 */
	SELECTOR,

	/**
	 * The {@link L2ReadBoxedOperand} holds the {@link L2BoxedRegister} that
	 * will be read.
	 */
	READ_BOXED,

	/**
	 * The {@link L2WriteBoxedOperand} holds the {@link L2BoxedRegister} that
	 * will be written.
	 */
	WRITE_BOXED,

	/**
	 * The {@link L2ReadIntOperand} holds the {@link L2IntRegister} that
	 * will be read.
	 */
	READ_INT,

	/**
	 * The {@link L2WriteIntOperand} holds the {@link L2IntRegister} that
	 * will be written.
	 */
	WRITE_INT,

	/**
	 * The {@link L2WriteFloatOperand} holds the {@link L2FloatRegister} that
	 * will be read.
	 */
	READ_FLOAT,

	/**
	 * The {@link L2WriteFloatOperand} holds the {@link L2FloatRegister} that
	 * will be written.
	 */
	WRITE_FLOAT,

	/**
	 * The {@link L2ReadVectorOperand} holds a {@link List} of {@link
	 * L2ReadBoxedOperand}s which will be read.
	 */
	READ_BOXED_VECTOR,

	/**
	 * The {@link L2ReadVectorOperand} holds a {@link List} of {@link
	 * L2ReadBoxedOperand}s which will be read.
	 */
	READ_INT_VECTOR,

	/**
	 * The {@link L2ReadVectorOperand} holds a {@link List} of {@link
	 * L2ReadBoxedOperand}s which will be read.
	 */
	READ_FLOAT_VECTOR,

	/**
	 * The {@link L2InternalCounterOperand} holds a {@link LongAdder} that will
	 * be incremented when a specific condition happens, such as taking or not
	 * taking a branch.
	 */
	INTERNAL_COUNTER,

	/**
	 * The {@link L2CommentOperand} holds descriptive text that does not affect
	 * analysis or execution of level two code.  It is for diagnostic purposes
	 * only.
	 */
	COMMENT;

	/**
	 * Create a {@link L2NamedOperandType} from the receiver and a {@link
	 * String} naming its role within some {@link L2Operation}.
	 *
	 * @param roleName
	 *        The name of this operand.
	 * @return A named operand type.
	 */
	public L2NamedOperandType is (final String roleName)
	{
		return new L2NamedOperandType(this, roleName, null);
	}

	/**
	 * Create a {@link L2NamedOperandType} from the receiver, a {@link
	 * String} naming its role within some {@link L2Operation}, and a designator
	 * of its {@linkplain Purpose purpose}.
	 *
	 * @param roleName
	 *        The name of this operand.
	 * @param purpose
	 *        The {@link Purpose} that best describes the {@link
	 *        L2NamedOperandType}.
	 * @return A named operand type.
	 */
	public L2NamedOperandType is (final String roleName, final Purpose purpose)
	{
		return new L2NamedOperandType(this, roleName, purpose);
	}
}
