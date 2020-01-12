/*
 * L2NamedOperandType.java
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

import com.avail.interpreter.levelTwo.operation.L2_CREATE_CONTINUATION;

import javax.annotation.Nullable;

/**
 * An {@code L2NamedOperandType} is used to specify both an {@link
 * L2OperandType} and a {@link String} naming its purpose with respect to some
 * {@link L2Operation}.  This effectively allows operations to declare named
 * operands, increasing the descriptiveness of the level two instruction set.
 * The names are not used in any way at runtime.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public class L2NamedOperandType
{
	/**
	 * The {@link L2OperandType} that the receiver decorates.
	 */
	private final L2OperandType operandType;

	/**
	 * Answer the {@link L2OperandType} that this decorates.
	 *
	 * @return The L2OperandType.
	 */
	public L2OperandType operandType ()
	{
		return operandType;
	}

	/**
	 * The {@link String} that names the receiver within an {@link L2Operation}.
	 */
	private final String name;

	/**
	 * Answer the {@link String} that names the receiver.
	 *
	 * @return The receiver's name.
	 */
	public String name ()
	{
		return name;
	}

	/**
	 * A {@code Purpose} specifies additional semantic meaning for an
	 * {@link L2NamedOperandType}.
	 *
	 * @author Todd L Smith &lt;todd@availlang.org&gt;
	 */
	public enum Purpose
	{
		/**
		 * Indicates that a {@link L2OperandType#PC PC} codes for a successful
		 * operation.
		 */
		SUCCESS,

		/**
		 * Indicates that a {@link L2OperandType#PC PC} codes for a failed
		 * operation. This represents ordinary failure, not "catastrophic"
		 * failure which requires an off-ramp.
		 */
		FAILURE,

		/**
		 * Indicates that a {@link L2OperandType#PC PC} codes for an off-ramp.
		 */
		OFF_RAMP,

		/**
		 * Indicates that a {@link L2OperandType PC} codes for an on-ramp.
		 */
		ON_RAMP,

		/**
		 * This target address is not directly used here, but is converted to an
		 * integer constant so it can be passed elsewhere, typically to an
		 * {@link L2_CREATE_CONTINUATION}.
		 */
		REFERENCED_AS_INT;
	}

	/**
	 * The {@link Purpose} that best describes the {@link L2NamedOperandType},
	 * if any.
	 */
	private final @Nullable Purpose purpose;

	/**
	 * Answer the {@link Purpose} that best describes the {@link
	 * L2NamedOperandType}, if any.
	 *
	 * @return The receiver's purpose, or {@code null} if nothing additional is
	 *         known about its purpose.
	 */
	public @Nullable Purpose purpose ()
	{
		return purpose;
	}

	@Override
	public String toString()
	{
		return operandType.name() + "(" + name + ")";
	}

	/**
	 * Construct a new {@code L2NamedOperandType}.
	 *
	 * @param operandType
	 *        The {@link L2OperandType} to wrap.
	 * @param name
	 *        The name of this operand.
	 * @param purpose
	 *        The {@link Purpose} that best describes the {@link
	 *        L2NamedOperandType}, if any.
	 */
	L2NamedOperandType (
		final L2OperandType operandType,
		final String name,
		final @Nullable Purpose purpose)
	{
		this.operandType = operandType;
		this.name = name;
		this.purpose = purpose;
		assert purpose == null || operandType.canHavePurpose;
	}
}
