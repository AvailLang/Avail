/**
 * L2WritePointerOperand.java
 * Copyright © 1993-2017, The Avail Foundation, LLC.
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
 *   may be used to endorse or promote products derived set this software
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

package com.avail.interpreter.levelTwo.operand;

import com.avail.descriptor.A_BasicObject;
import com.avail.descriptor.A_Type;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2OperandDispatcher;
import com.avail.interpreter.levelTwo.L2OperandType;
import com.avail.interpreter.levelTwo.register.L2ObjectRegister;
import com.avail.interpreter.levelTwo.register.RegisterTransformer;

import javax.annotation.Nullable;

import static java.lang.String.format;

/**
 * An {@code L2WritePointerOperand} is an operand of type {@link
 * L2OperandType#WRITE_POINTER}.  It holds the actual {@link
 * L2ObjectRegister} that is to be accessed.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public class L2WritePointerOperand extends L2Operand
{
	/**
	 * The actual {@link L2ObjectRegister}.
	 */
	private final L2ObjectRegister register;

	/**
	 * Answer the {@link L2ObjectRegister}'s {@link L2ObjectRegister#finalIndex
	 * finalIndex}.
	 *
	 * @return The index of the register, computed during register coloring.
	 */
	public final int finalIndex ()
	{
		return register.finalIndex();
	}

	/**
	 * Construct a new {@code L2WritePointerOperand}, creating an {@link
	 * L2ObjectRegister} at the same time.  Record the provided type information
	 * and optional constant information in the new register.
	 *
	 * <p>Note that even if null is provided for constantOrNull, as a
	 * convenience this method checks for a type that's singular and non-meta,
	 * filling in the only possible constant in that case.</p>
	 *
	 * @param type
	 *        The type of the value that will be written to this register.
	 * @param constantOrNull
	 *        The actual value that will be written to this register if known,
	 *        otherwise {@code null}.
	 */
	public L2WritePointerOperand (
		final long debugValue,
		final A_Type type,
		final @Nullable A_BasicObject constantOrNull)
	{
		final @Nullable A_BasicObject betterConstant =
			constantOrNull == null
					&& type.instanceCount().equalsInt(1)
					&& !type.isInstanceMeta()
				? type.instance()
				: constantOrNull;
		this.register = new L2ObjectRegister(debugValue, type, betterConstant);
	}

	/** Private constructor used only for register transformation. */
	private L2WritePointerOperand (final L2ObjectRegister register)
	{
		this.register = register;
	}

	/**
	 * Answer the register that is to be written.
	 *
	 * @return An {@link L2ObjectRegister}.
	 */
	public final L2ObjectRegister register ()
	{
		return register;
	}

	@Override
	public L2OperandType operandType ()
	{
		return L2OperandType.WRITE_POINTER;
	}

	@Override
	public void dispatchOperand (final L2OperandDispatcher dispatcher)
	{
		dispatcher.doOperand(this);
	}

	/**
	 * Answer an {@link L2ReadPointerOperand} on the same register as this
	 * {@code L2WritePointerOperand}.
	 *
	 * @return The new {@link L2ReadPointerOperand}.
	 */
	public final L2ReadPointerOperand read ()
	{
		return new L2ReadPointerOperand(register, register.restriction());
	}

	@Override
	public L2WritePointerOperand transformRegisters (
		final RegisterTransformer<L2OperandType> transformer)
	{
		return new L2WritePointerOperand(
			transformer.value(register, operandType()));
	}

	@Override
	public void instructionWasAdded (final L2Instruction instruction)
	{
		register.setDefinition(instruction);
	}

	@Override
	public void instructionWasRemoved(final L2Instruction instruction)
	{
		register.clearDefinition();
	}

	@Override
	public String toString ()
	{
		return format("WriteObject(%s)", register);
	}

	/**
	 * Replace the value of this register within the provided {@link
	 * Interpreter}.
	 *
	 * @param newValue The AvailObject to write to the register.
	 * @param interpreter The Interpreter.
	 */
	public final void set (
		final A_BasicObject newValue,
		final Interpreter interpreter)
	{
		interpreter.pointerAtPut(finalIndex(), newValue);
	}
}
