/**
 * L2ReadPointerOperand.java
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
import com.avail.descriptor.A_Set;
import com.avail.descriptor.A_Type;
import com.avail.descriptor.AvailObject;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2OperandDispatcher;
import com.avail.interpreter.levelTwo.L2OperandType;
import com.avail.interpreter.levelTwo.register.L2ObjectRegister;
import com.avail.interpreter.levelTwo.register.L2Register;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

import static com.avail.descriptor.AbstractEnumerationTypeDescriptor
	.enumerationWith;
import static com.avail.descriptor.AbstractEnumerationTypeDescriptor
	.instanceTypeOrMetaOn;
import static com.avail.descriptor.SetDescriptor.emptySet;

/**
 * An {@code L2ReadPointerOperand} is an operand of type {@link
 * L2OperandType#READ_POINTER}.  It holds the actual {@link L2ObjectRegister}
 * that is to be accessed.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public class L2ReadPointerOperand extends L2Operand
{
	/**
	 * The actual {@link L2ObjectRegister}.
	 */
	private L2ObjectRegister register;

	/**
	 * A type restriction, certified by the VM, that this particular read of
	 * this register is guaranteed to satisfy.  This supplements the more basic
	 * type restriction already present in the {@link L2ObjectRegister} itself.
	 */
	private final TypeRestriction restriction;

	/**
	 * Construct a new {@code L2ReadPointerOperand} for the specified {@link
	 * L2ObjectRegister} and optional restriction.
	 *
	 * @param register
	 *        The object register.
	 * @param restriction
	 *        The further {@link TypeRestriction} to apply to this particular
	 *        read.
	 */
	public L2ReadPointerOperand (
		final L2ObjectRegister register,
		final @Nullable TypeRestriction restriction)
	{
		this.register = register;
		this.restriction =
			restriction == null ? register.restriction() : restriction;
	}

	/**
	 * Answer the register that is to be read.
	 *
	 * @return An {@link L2ObjectRegister}.
	 */
	public final L2ObjectRegister register ()
	{
		return register;
	}

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
	 * Answer the type restriction for this register read.
	 *
	 * @return A {@link TypeRestriction}.
	 */
	public final TypeRestriction restriction ()
	{
		return restriction;
	}

	/**
	 * Answer this read's type restriction's basic type.
	 *
	 * @return An {@link A_Type}.
	 */
	public final A_Type type ()
	{
		return restriction.type;
	}

	/**
	 * Answer this read's type restriction's constant value (i.e., the exact
	 * value that this read is guaranteed to produce), or {@code null} if such
	 * a constraint is not available.
	 *
	 * @return The exact {@link A_BasicObject} that's known to be in this
	 *         register, or else {@code null}.
	 */
	public final @Nullable A_BasicObject constantOrNull ()
	{
		return restriction.constantOrNull;
	}

	@Override
	public L2OperandType operandType ()
	{
		return L2OperandType.READ_POINTER;
	}

	@Override
	public void dispatchOperand (final L2OperandDispatcher dispatcher)
	{
		dispatcher.doOperand(this);
	}

	@Override
	public void instructionWasAdded (final L2Instruction instruction)
	{
		register.addUse(instruction);
	}

	@Override
	public void instructionWasRemoved (final L2Instruction instruction)
	{
		register.removeUse(instruction);
	}

	@Override
	public void replaceRegisters (
		final Map<L2Register, L2Register> registerRemap,
		final L2Instruction instruction)
	{
		final @Nullable L2Register replacement = registerRemap.get(register);
		if (replacement == null || replacement == register)
		{
			return;
		}
		register.removeUse(instruction);
		replacement.addUse(instruction);
		register = L2ObjectRegister.class.cast(replacement);
	}

	@Override
	public void addSourceRegistersTo (final List<L2Register> sourceRegisters)
	{
		sourceRegisters.add(register);
	}

	@Override
	public String toString ()
	{
		//noinspection StringConcatenationMissingWhitespace
		return
			"Read("
				+ register
				+ register.restriction().suffixString()
				+ ")";
	}

	/**
	 * Create a {@code PhiRestriction}, which narrows a register's type
	 * information along a control flow branch.  The type and optional constant
	 * value are supplied.
	 *
	 * @param restrictedType
	 *        The type that the register will hold along this branch.
	 * @param restrictedConstantOrNull
	 *        Either {@code null} or the exact value that the register will hold
	 *        along this branch.
	 */
	public PhiRestriction restrictedTo (
		final A_Type restrictedType,
		final @Nullable A_BasicObject restrictedConstantOrNull)
	{
		return new PhiRestriction(
			register,
			restrictedType.typeIntersection(type()),
			restrictedConstantOrNull);
	}

	/**
	 * Create a {@code PhiRestriction}, which narrows a register's type
	 * information along a control flow branch.  The exact value is supplied.
	 *
	 * @param restrictedConstant
	 *        The exact value that the register will hold along this branch.
	 */
	public PhiRestriction restrictedToValue (
		final A_BasicObject restrictedConstant)
	{
		final @Nullable A_Type type = type();
		assert restrictedConstant.isInstanceOf(type)
			: "This register has no possible values.";
		return new PhiRestriction(
			register,
			instanceTypeOrMetaOn(restrictedConstant).typeIntersection(type),
			restrictedConstant);
	}

	/**
	 * Create a {@code PhiRestriction}, which narrows a register's type
	 * information along a control flow branch.  A value to exclude from the
	 * existing type is provided.
	 *
	 * @param excludedConstant
	 *        The value that the register <em>cannot</em> hold along this
	 *        branch.
	 */
	public PhiRestriction restrictedWithoutValue (
		final A_BasicObject excludedConstant)
	{
		final @Nullable A_Type type = type();
		final @Nullable A_BasicObject constantOrNull = constantOrNull();
		if (constantOrNull != null)
		{
			// It's unclear if this is necessarily a problem, or if it's
			// actually reasonable for code that will soon be marked dead.
			assert !excludedConstant.equals(constantOrNull)
				: "This register has no possible values.";
			// The excluded value is irrelevant.
			return new PhiRestriction(register, type, constantOrNull);
		}
		final A_Type restrictedType;
		if (type.instanceCount().isFinite() && !type.isInstanceMeta())
		{
			restrictedType = enumerationWith(
				type.instances().setWithoutElementCanDestroy(
					excludedConstant, false));
		}
		else
		{
			restrictedType = type;
		}
		return new PhiRestriction(
			register,
			restrictedType,
			restrictedType.instanceCount().equalsInt(1)
					&& !restrictedType.isInstanceMeta()
				? restrictedType.instance()
				: null);
	}

	/**
	 * Create a {@code PhiRestriction}, which narrows a register's type
	 * information along a control flow branch.  A type is provided to exclude,
	 * although we don't yet maintain precise negative type information.
	 *
	 * @param excludedType
	 *        The value that the register <em>cannot</em> hold along this
	 *        branch.
	 */
	public PhiRestriction restrictedWithoutType (
		final A_Type excludedType)
	{
		final @Nullable A_Type type = type();
		final @Nullable A_BasicObject constantOrNull = constantOrNull();
		if (constantOrNull != null)
		{
			// It's unclear if this is necessarily a problem, or if it's
			// actually reasonable for code that will soon be marked dead.
			assert !constantOrNull.isInstanceOf(excludedType)
				: "This register has no possible values.";
			// The excluded type is irrelevant.
			return new PhiRestriction(register, type, constantOrNull);
		}
		if (type.instanceCount().isFinite() && !type.isInstanceMeta())
		{
			A_Set elements = emptySet();
			for (final A_BasicObject element : type.instances())
			{
				if (!element.isInstanceOf(excludedType))
				{
					elements = elements.setWithElementCanDestroy(element, true);
				}
			}
			elements = elements.makeImmutable();
			return new PhiRestriction(
				register, enumerationWith(elements), null);
		}
		// Be conservative and ignore the type subtraction.  We could eventually
		// record this information.
		return new PhiRestriction(register, type, null);
	}

	/**
	 * Read the value of this register from the provided {@link Interpreter}.
	 *
	 * @param interpreter An Interpreter.
	 * @return An {@code AvailObject}, the value of this register.
	 */
	public final AvailObject in (final Interpreter interpreter)
	{
		return interpreter.pointerAt(finalIndex());
	}
}
