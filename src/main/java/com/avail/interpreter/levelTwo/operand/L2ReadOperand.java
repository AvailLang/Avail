/*
 * L2ReadOperand.java
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

import com.avail.descriptor.representation.AvailObject;
import com.avail.descriptor.representation.A_BasicObject;
import com.avail.descriptor.types.A_Type;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.operation.L2_MAKE_IMMUTABLE;
import com.avail.interpreter.levelTwo.operation.L2_MOVE;
import com.avail.interpreter.levelTwo.register.L2Register;
import com.avail.interpreter.levelTwo.register.L2Register.RegisterKind;
import com.avail.optimizer.L2BasicBlock;
import com.avail.optimizer.L2Synonym;
import com.avail.optimizer.L2ValueManifest;
import com.avail.optimizer.values.L2SemanticValue;
import com.avail.utility.Casts;
import com.avail.utility.Pair;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;

import static com.avail.interpreter.levelTwo.operation.L2_MAKE_IMMUTABLE.sourceOfImmutable;
import static com.avail.utility.Casts.cast;
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
	private L2SemanticValue semanticValue;

	/**
	 * A type restriction, certified by the VM, that this particular read of
	 * this register is guaranteed to satisfy.
	 */
	private final TypeRestriction restriction;

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
	 * @param register
	 *        The {@link L2Register} being read by this operand.
	 */
	protected L2ReadOperand (
		final L2SemanticValue semanticValue,
		final TypeRestriction restriction,
		final R register)
	{
		this.semanticValue = semanticValue;
		this.restriction = restriction;
		this.register = register;
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
	 * operand is reading.  The control flow graph must be in SSA form.
	 *
	 * @return The defining {@link L2WriteOperand}.
	 */
	public L2WriteOperand<R> definition ()
	{
		return Casts.<L2WriteOperand<?>, L2WriteOperand<R>>cast(
			register.definition());
	}

	@Override
	public void instructionWasAdded (
		final L2ValueManifest manifest)
	{
		super.instructionWasAdded(manifest);
		manifest.setRestriction(semanticValue, restriction);
		register().addUse(this);
	}

	@Override
	public L2ReadOperand<?> adjustedForReinsertion (
		final L2ValueManifest manifest)
	{
		if (manifest.hasSemanticValue(semanticValue))
		{
			return this;
		}
		// Be lenient.  This gets called after (or just before) placeholder
		// replacement, when reinserting instructions that preceded or followed
		// the placeholder in its original basic block.  However, some of the
		// instructions might reference valid registers via semantic values that
		// were removed in a prior dead code elimination.  That's because moves
		// that extend a synonym can disappear, even though subsequent code uses
		// a semantic value introduced by that move.
		//
		// The good news is that the register is still valid, so we can simply
		// rewrite this operation to use a semantic value that's still in the
		// manifest, or even add a new one.
		final List<L2Synonym> synonyms = manifest.synonymsForRegister(register);
		assert !synonyms.isEmpty();
		final L2SemanticValue newSemanticValue =
			synonyms.get(0).pickSemanticValue();
		return this.copyForSemanticValue(newSemanticValue);
	}

	/**
	 * Create an {@code L2ReadOperand} like this one, but with a different
	 * {@link #semanticValue}.
	 *
	 * @param newSemanticValue
	 *        The {@link L2SemanticValue} to use in the copy.
	 * @return A duplicate of the receiver, but with a different
	 *         {@link L2SemanticValue}.
	 */
	public abstract L2ReadOperand<R> copyForSemanticValue (
		final L2SemanticValue newSemanticValue);

	/**
	 * Create an {@code L2ReadOperand} like this one, but with a different
	 * {@link #register}.
	 *
	 * @param newRegister
	 *        The {@link L2Register} to use in the copy.
	 * @return A duplicate of the receiver, but with a different
	 *         {@link L2Register}.
	 */
	public abstract L2ReadOperand<R> copyForRegister (
		final L2Register newRegister);

	@Override
	public void instructionWasInserted (
		final L2Instruction newInstruction)
	{
		super.instructionWasInserted(newInstruction);
		register().addUse(this);
	}

	@Override
	public final void instructionWasRemoved ()
	{
		super.instructionWasRemoved();
		register().removeUse(this);
	}

	@Override
	public final void replaceRegisters (
		final Map<L2Register, L2Register> registerRemap,
		final L2Instruction theInstruction)
	{
		final @Nullable R replacement =
			nullableCast(registerRemap.get(register));
		if (replacement == null || replacement == register)
		{
			return;
		}
		register().removeUse(this);
		replacement.addUse(this);
		register = replacement;
	}

	@Override
	public final L2ReadOperand<?> transformEachRead (
		final UnaryOperator<L2ReadOperand<?>> transformer)
	{
		return transformer.apply(this);
	}

	@Override
	public void addReadsTo (final List<L2ReadOperand<?>> readOperands)
	{
		readOperands.add(this);
	}

	@Override
	public final void addSourceRegistersTo (
		final List<L2Register> sourceRegisters)
	{
		sourceRegisters.add(register);
	}

	@Override
	public void appendTo (final StringBuilder builder)
	{
		builder.append('@').append(registerString());
		if (restriction.constantOrNull == null)
		{
			// Don't redundantly print restriction information for constants.
			builder.append(restriction.suffixString());
		}
	}

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
				final L2_MOVE<?, ?, ?> operation = cast(other.operation());
				other = operation.sourceOf(other).definition().instruction();
				continue;
			}
			if (bypassImmutables
				&& other.operation() instanceof L2_MAKE_IMMUTABLE)
			{
				other = sourceOfImmutable(other).definition().instruction();
				continue;
			}
			return other;
		}
	}

	/**
	 * Find the set of {@link L2SemanticValue}s and {@link TypeRestriction}
	 * leading to this read operand.  The control flow graph is not necessarily
	 * in SSA form, so the underlying register may have multiple definitions to
	 * choose from, some of which are not in this read's history.
	 *
	 * <p>If there is a write of the register in the same block as the read,
	 * extract the information from that.</p>
	 *
	 * <p>Otherwise each incoming edge must carry this information in its
	 * manifest.  Note that there's no phi function to merge differing registers
	 * into this one, otherwise the phi itself would have been considered the
	 * nearest write.  We still have to take the union of the restrictions, and
	 * the intersection of the synonyms' sets of {@link L2SemanticValue}s.</p>
	 *
	 * @return A {@link Pair} consisting of a {@link Set} of synonymous
	 *         {@link L2SemanticValue}s, and the {@link TypeRestriction}
	 *         guaranteed at this read.
	 */
	public Pair<Set<L2SemanticValue>, TypeRestriction> findSourceInformation ()
	{
		// Either the write must happen inside the block we're moving from, or
		// it must have come in along the edges, and is therefore in each
		// incoming edge's manifest.
		final L2BasicBlock thisBlock = instruction().basicBlock();
		for (final L2WriteOperand<?> def : register.definitions())
		{
			if (def.instruction().basicBlock() == thisBlock)
			{
				// Ignore ghost instructions that haven't been fully removed
				// yet, during placeholder substitution.
				if (thisBlock.instructions().contains(def.instruction()))
				{
					return new Pair<>(def.semanticValues(), def.restriction());
				}
			}
		}

		// Integrate the information from the block's incoming manifests.
		final Iterator<L2PcOperand> incoming =
			thisBlock.predecessorEdgesIterator();
		assert incoming.hasNext();
		final L2ValueManifest firstManifest = incoming.next().manifest();
		final Set<L2SemanticValue> semanticValues = new HashSet<>();
		@Nullable TypeRestriction typeRestriction = null;
		for (final L2Synonym syn : firstManifest.synonymsForRegister(register))
		{
			semanticValues.addAll(syn.semanticValues());
			typeRestriction = typeRestriction == null
				? firstManifest.restrictionFor(syn.pickSemanticValue())
				: typeRestriction.union(
					firstManifest.restrictionFor(syn.pickSemanticValue()));
		}
		while (incoming.hasNext())
		{
			final L2ValueManifest nextManifest = incoming.next().manifest();
			final Set<L2SemanticValue> newSemanticValues = new HashSet<>();
			for (final L2Synonym syn :
				nextManifest.synonymsForRegister(register))
			{
				newSemanticValues.addAll(syn.semanticValues());
				typeRestriction = stripNull(typeRestriction).union(
					nextManifest.restrictionFor(syn.pickSemanticValue()));
			}
			// Intersect with the newSemanticValues.
			semanticValues.retainAll(newSemanticValues);
		}
		return new Pair<>(semanticValues, stripNull(typeRestriction));
	}

	/**
	 * Answer the {@link L2WriteBoxedOperand} which produces the value that will
	 * populate this register. Skip over move instructions. Also skip over
	 * boxing and unboxing operations that don't alter the value. The containing
	 * graph must be in SSA form.
	 *
	 * @param bypassImmutables
	 *        Whether to bypass instructions that force a value to become
	 *        immutable.
	 * @return The requested {@code L2Instruction}.
	 */
	public L2WriteBoxedOperand originalBoxedWriteSkippingMoves (
		final boolean bypassImmutables)
	{
		L2WriteOperand<?> def = definition();
		@Nullable L2WriteBoxedOperand earliestBoxed = null;
		while (true)
		{
			if (def instanceof L2WriteBoxedOperand)
			{
				earliestBoxed = (L2WriteBoxedOperand) def;
			}
			final L2Instruction instruction = def.instruction();
			if (instruction.operation().isMove())
			{
				final L2_MOVE<?, ?, ?> operation =
					cast(instruction.operation());
				def = operation.sourceOf(instruction).definition();
				continue;
			}
			//TODO: Trace back through L2_[BOX|UNBOX]_[INT|FLOAT], etc.
			if (bypassImmutables
				&& instruction.operation() == L2_MAKE_IMMUTABLE.instance)
			{
				def = sourceOfImmutable(instruction).definition();
				continue;
			}
			return stripNull(earliestBoxed);
		}
	}

	/**
	 * To accommodate code motion, deletion, and replacement, we sometimes have
	 * to adjust the {@link #semanticValue} to one we know is in scope.
	 *
	 * @param replacementSemanticValue
	 *        The {@link L2SemanticValue} that should replace the current
	 *        {@link #semanticValue}.
	 */
	public void updateSemanticValue (
		final L2SemanticValue replacementSemanticValue)
	{
		semanticValue = replacementSemanticValue;
	}
}
