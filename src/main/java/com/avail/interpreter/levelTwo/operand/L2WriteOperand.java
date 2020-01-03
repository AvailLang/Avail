/*
 * L2WriteOperand.java
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

package com.avail.interpreter.levelTwo.operand;

import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.register.L2IntRegister;
import com.avail.interpreter.levelTwo.register.L2Register;
import com.avail.interpreter.levelTwo.register.L2Register.RegisterKind;
import com.avail.optimizer.L2ValueManifest;
import com.avail.optimizer.values.L2SemanticValue;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.avail.utility.Casts.cast;
import static com.avail.utility.Nulls.stripNull;
import static java.util.Collections.unmodifiableSet;

/**
 * {@code L2WriteOperand} abstracts the capabilities of actual register write
 * operands.
 *
 * @param <R>
 *        The subclass of {@link L2Register} that this writes to.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public abstract class L2WriteOperand<R extends L2Register>
extends L2Operand
{
	/**
	 * The {@link L2SemanticValue}s being written when an {@link L2Instruction}
	 * uses this {@link L2Operand}.
	 */
	private final Set<L2SemanticValue> semanticValues;

	/**
	 * The {@link TypeRestriction} that indicates what values may be written to
	 * the destination register.
	 */
	private final TypeRestriction restriction;

	/**
	 * The actual {@link L2Register}.  This is only set during late optimization
	 * of the control flow graph.
	 */
	protected R register;

	/**
	 * Construct a new {@code L2WriteOperand} for the specified {@link
	 * L2SemanticValue}.
	 *
	 * @param semanticValues
	 *        The {@link Set} of {@link L2SemanticValue} that this operand is
	 *        effectively producing.
	 * @param restriction
	 *        The {@link TypeRestriction} that indicates what values are allowed
	 *        to be written into the register.
	 * @param register
	 *        The {@link L2Register} to write.
	 */
	public L2WriteOperand (
		final Set<L2SemanticValue> semanticValues,
		final TypeRestriction restriction,
		final R register)
	{
		this.semanticValues = new HashSet<>(semanticValues);
		this.restriction = restriction;
		this.register = register;
	}

	/**
	 * Answer this write's immutable set of {@link L2SemanticValue}s.
	 *
	 * @return The semantic value being written.
	 */
	public final Set<L2SemanticValue> semanticValues ()
	{
		return unmodifiableSet(semanticValues);
	}

	/**
	 * Answer this write's sole {@link L2SemanticValue}, failing if there isn't
	 * exactly one.
	 *
	 * @return The write operand's {@link L2SemanticValue}.
	 */
	public L2SemanticValue onlySemanticValue ()
	{
		assert semanticValues.size() == 1;
		return semanticValues.iterator().next();
	}

	/**
	 * Choose an arbitrary one of the {@link L2SemanticValue}s that this operand
	 * writes.
	 *
	 * @return The write operand's {@link L2SemanticValue}.
	 */
	public L2SemanticValue pickSemanticValue ()
	{
		return semanticValues.iterator().next();
	}

	/**
	 * Answer this write's {@link TypeRestriction}.
	 *
	 * @return The {@link TypeRestriction} that constrains what's being written.
	 */
	public final TypeRestriction restriction ()
	{
		return restriction;
	}

	/**
	 * Answer the {@link RegisterKind} of register that is written by this
	 * {@code L2WriteOperand}.
	 *
	 * @return The {@link RegisterKind}.
	 */
	public abstract RegisterKind registerKind ();

	/**
	 * Answer the {@link L2Register}'s {@link L2Register#finalIndex()
	 * finalIndex}.
	 *
	 * @return The index of the register, computed during register coloring.
	 */
	public final int finalIndex ()
	{
		return stripNull(register).finalIndex();
	}

	/**
	 * Answer the register that is to be written.
	 *
	 * @return An {@link L2IntRegister}.
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
		return register.toString() + semanticValues;
	}

	@Override
	public final void instructionWasAdded (
		final L2ValueManifest manifest)
	{
		super.instructionWasAdded(manifest);
		register.addDefinition(this);
		manifest.recordDefinition(this);
	}

	@Override
	public final void instructionWasInserted (
		final L2Instruction newInstruction)
	{
		super.instructionWasInserted(newInstruction);
		register.addDefinition(this);
	}

	/**
	 * This operand is a write of a move-like operation.  Make the semantic
	 * value a synonym of the given {@link L2ReadOperand}'s semantic value.
	 *
	 * @param sourceSemanticValue
	 *        The {@link L2SemanticValue} that already holds the value.
	 * @param manifest
	 *        The {@link L2ValueManifest} in which to capture the synonymy of
	 *        the source and destination.
	 */
	public final void instructionWasAddedForMove (
		final L2SemanticValue sourceSemanticValue,
		final L2ValueManifest manifest)
	{
		super.instructionWasAdded(manifest);
		register.addDefinition(this);
		manifest.recordDefinitionForMove(this, sourceSemanticValue);
	}

	@Override
	public final void instructionWasRemoved ()
	{
		super.instructionWasRemoved();
		register().removeDefinition(this);
	}

	/**
	 * Add the given {@link L2SemanticValue} to this write operand's set of
	 * semantic values.  DO NOT update any other structures to reflect this
	 * change, as this is the caller's responsibility.
	 *
	 * @param newSemanticValue
	 *        The new {@link L2SemanticValue} to add to the write operand's set
	 *        of semantic values.
	 */
	public void retroactivelyIncludeSemanticValue (
		final L2SemanticValue newSemanticValue)
	{
		semanticValues.add(newSemanticValue);
	}

	@Override
	public final void replaceRegisters (
		final Map<L2Register, L2Register> registerRemap,
		final L2Instruction theInstruction)
	{
		final @Nullable L2Register replacement = registerRemap.get(register);
		if (replacement == null || replacement == register)
		{
			return;
		}
		register().removeDefinition(this);
		replacement.addDefinition(this);
		register = cast(replacement);
	}

	@Override
	public final void addDestinationRegistersTo (
		final List<L2Register> destinationRegisters)
	{
		destinationRegisters.add(register);
	}

	@Override
	public void appendTo (final StringBuilder builder)
	{
		builder.append("→").append(registerString());
	}
}
