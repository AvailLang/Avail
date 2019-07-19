/*
 * L2ReadOperand.java
 * Copyright © 1993-2018, The Avail Foundation, LLC.
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

package com.avail.interpreter.levelTwo.operand;

import com.avail.descriptor.A_BasicObject;
import com.avail.descriptor.A_Type;
import com.avail.descriptor.AvailObject;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2Operation;
import com.avail.interpreter.levelTwo.operation.L2_MAKE_IMMUTABLE;
import com.avail.interpreter.levelTwo.operation.L2_MOVE;
import com.avail.interpreter.levelTwo.register.L2Register;
import com.avail.interpreter.levelTwo.register.L2Register.RegisterKind;
import com.avail.optimizer.L2ValueManifest;
import com.avail.optimizer.values.L2SemanticValue;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

import static com.avail.utility.Casts.nullableCast;
import static com.avail.utility.Nulls.stripNull;

/**
 * {@code L2ReadOperand} abstracts the capabilities of actual register read
 * operands.
 *
 * @param <R>
 *        The subclass of {@link L2Register}.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public abstract class L2ReadOperand<R extends L2Register>
extends L2Operand
{
	/**
	 * The {@link L2SemanticValue} that is being read when an {@link
	 * L2Instruction} uses this {@link L2Operand}.
	 */
	private final L2SemanticValue semanticValue;

	/**
	 * A type restriction, certified by the VM, that this particular read of
	 * this register is guaranteed to satisfy.
	 */
	private final TypeRestriction restriction;

	/**
	 * The {@link L2WriteOperand} that produced the value that this read is
	 * consuming.
	 */
	private L2WriteOperand<R> definition;

	/**
	 * The actual {@link L2Register}.  This is only set during late optimization
	 * of the control flow graph.
	 */
	private R register;

	/**
	 * Construct a new {@code L2ReadOperand} for the specified {@link
	 * L2SemanticValue} and {@link TypeRestriction}, using information from the
	 * given {@link L2ValueManifest}.
	 *
	 * @param semanticValue
	 *        The {@link L2SemanticValue} that is being read when an {@link
	 *        L2Instruction} uses this {@link L2Operand}.
	 * @param restriction
	 *        The {@link TypeRestriction} that bounds the value being read.
	 * @param definition
	 *        The earliest known defining {@link L2WriteOperand} of the {@link
	 *        L2SemanticValue}.
	 */
	protected L2ReadOperand (
		final L2SemanticValue semanticValue,
		final TypeRestriction restriction,
		final L2WriteOperand<R> definition)
	{
		this.semanticValue = semanticValue;
		this.restriction = restriction;
		this.definition = definition;
		this.register = definition.register;
	}

	/**
	 * Answer the {@link L2SemanticValue} being read.
	 *
	 * @return The {@link L2SemanticValue}.
	 */
	public final L2SemanticValue semanticValue ()
	{
		return semanticValue;
	}

	/**
	 * Answer this read's {@link L2Register}.
	 *
	 * @return The register.
	 */
	public final R register ()
	{
		return stripNull(register);
	}

	/**
	 * Answer a String that describes this operand for debugging.
	 *
	 * @return A {@link String}.
	 */
	public final String registerString ()
	{
		return register + "[" + semanticValue + "]";
	}

	/**
	 * Answer the {@link L2Register}'s {@link L2Register#finalIndex()
	 * finalIndex}.
	 *
	 * @return The index of the register, computed during register coloring.
	 */
	public final int finalIndex ()
	{
		return register().finalIndex();
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
	public final @Nullable AvailObject constantOrNull ()
	{
		return restriction.constantOrNull;
	}

	/**
	 * Answer the {@link RegisterKind} of register that is read by this {@code
	 * L2ReadOperand}.
	 *
	 * @return The {@link RegisterKind}.
	 */
	public abstract RegisterKind registerKind ();

	/**
	 * Answer the {@link L2WriteOperand} that provided the value that this
	 * operand is reading.
	 *
	 * @return The defining {@link L2WriteOperand}.
	 */
	public L2WriteOperand<R> definition ()
	{
		return stripNull(definition);
	}

	@Override
	public void instructionWasAdded (
		final L2Instruction instruction,
		final L2ValueManifest manifest)
	{
		definition = manifest.getDefinition(semanticValue, registerKind());
		manifest.setRestriction(semanticValue, restriction);
		register().addUse(instruction);
	}

	@Override
	public final void instructionWasRemoved (final L2Instruction instruction)
	{
		register().removeUse(instruction);
	}

	@Override
	public final void replaceRegisters (
		final Map<L2Register, L2Register> registerRemap,
		final L2Instruction instruction)
	{
		final @Nullable R replacement =
			nullableCast(registerRemap.get(register));
		if (replacement == null || replacement == register)
		{
			return;
		}
		register().removeUse(instruction);
		replacement.addUse(instruction);
		register = replacement;
	}

	@Override
	public final void addSourceRegistersTo (
		final List<L2Register> sourceRegisters)
	{
		sourceRegisters.add(register);
	}

	@Override
	public final String toString ()
	{
		final StringBuilder builder = new StringBuilder();
		builder.append('@');
		builder.append(registerString());
		if (restriction.constantOrNull == null)
		{
			// Don't redundantly print restriction information for constants.
			builder.append(restriction.suffixString());
		}
		return builder.toString();
	}

	/**
	 * Answer an {@link L2Operation} that implements a phi move for the
	 * receiver.
	 *
	 * @return The requested instruction.
	 */
	public abstract L2_MOVE<? extends L2Register> phiMoveOperation ();

	/**
	 * Answer the {@link L2Instruction} which generates the value that will
	 * populate this register. Skip over move instructions. The containing graph
	 * must be in SSA form.
	 *
	 * @param bypassImmutables
	 *        Whether to bypass instructions that force a value to become
	 *        immutable.
	 * @return The requested {@code L2Instruction}.
	 */
	public L2Instruction definitionSkippingMoves (
		final boolean bypassImmutables)
	{
		L2Instruction other = definition().instruction();
		while (true)
		{
			if (other.operation().isMove())
			{
				other = L2_MOVE.sourceOf(other).definition().instruction();
				continue;
			}
			if (bypassImmutables
				&& other.operation() instanceof L2_MAKE_IMMUTABLE)
			{
				other = L2_MAKE_IMMUTABLE.sourceOfImmutable(other).definition()
					.instruction();
				continue;
			}
			return other;
		}
	}
}
