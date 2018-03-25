/*
 * L2Synonym.java
 * Copyright © 1993-2018, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 *  Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 *  Neither the name of the copyright holder nor the names of the contributors
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
package com.avail.optimizer;

import com.avail.descriptor.A_BasicObject;
import com.avail.descriptor.A_Number;
import com.avail.interpreter.levelTwo.operand.L2ReadOperand;
import com.avail.interpreter.levelTwo.operand.TypeRestriction;
import com.avail.interpreter.levelTwo.operation.L2_MAKE_IMMUTABLE;
import com.avail.interpreter.levelTwo.register.L2ObjectRegister;
import com.avail.interpreter.levelTwo.register.L2Register;
import com.avail.optimizer.values.Frame;
import com.avail.optimizer.values.L2SemanticValue;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.function.Function;

import static com.avail.descriptor.TypeDescriptor.Types.TOP;
import static com.avail.utility.Casts.cast;
import static com.avail.utility.Nulls.stripNull;

/**
 * An {@code L2Synonym} is a set of {@link L2Register}s and {@link
 * L2SemanticValue}s.  The {@link L2ValueManifest} at each instruction includes
 * a set of synonyms which partition the potentially live registers and semantic
 * values.
 *
 * @param <R>
 *        The {@link L2Register} subclass for this synonym.
 * @param <T>
 *        The {@link A_BasicObject} subclass that bounds {@link
 *        TypeRestriction}s for this synonym's registers.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class L2Synonym<R extends L2Register<T>, T extends A_BasicObject>
{
	/** The set of equivalent {@link L2Register}s. */
	private final Set<R> registers;

	/** The {@link L2SemanticValue}s. */
	private final Set<L2SemanticValue> semanticValues;

	/**
	 * One of the registers, chosen to increase stability and perhaps reduce
	 * register pressure.
	 */
	private @Nullable R defaultRegister;

	/**
	 * The current type restriction, which applies to all the included
	 * registers, since they hold the same value.
	 */
	private TypeRestriction<T> restriction;

	/**
	 * The {@link L2Synonym}, if any, that holds the boxed version of this
	 * unboxed (int or float) synonym.
	 */
	public @Nullable L2Synonym<L2ObjectRegister, A_BasicObject> boxedSynonym =
		null;

	/**
	 * The {@link L2Synonym}, if any, that holds the unboxed (int or float)
	 * version of this boxed synonym.
	 */
	public @Nullable L2Synonym<? extends L2Register<A_Number>, A_Number>
		unboxedSynonym = null;

	/** Flags that can be set or cleared for this synonym. */
	public enum SynonymFlag
	{
		/**
		 * Whether the common value held by this synonym's registers is known to
		 * be immutable already.
		 */
		KNOWN_IMMUTABLE;
	}

	/** The flags that are set for this synonym. */
	public EnumSet<SynonymFlag> flags = EnumSet.noneOf(SynonymFlag.class);

	/**
	 * Add a register to this synonym, meaning that at the point where the
	 * manifest contains this synonym, the indicated register has the same value
	 * as all the other registers in this synonym, and fulfills each of its
	 * semantic values.
	 *
	 * @param register The {@link L2Register} to add.
	 */
	public void addRegister (final R register)
	{
		registers.add(register);
		if (defaultRegister == null)
		{
			defaultRegister = register;
		}
	}

	/**
	 * Add an {@link L2SemanticValue} to this synonym, meaning that each of its
	 * registers now holds the value for this semantic value.
	 *
	 * @param semanticValue The {@link L2SemanticValue} to add.
	 */
	public void addSemanticValue (final L2SemanticValue semanticValue)
	{
		semanticValues.add(semanticValue);
	}

	/**
	 * Remove a {@link L2SemanticValue} from this synonym, meaning that that
	 * semantic value is no longer associated with its registers.
	 *
	 * @param semanticValue The {@link L2SemanticValue} to remove.
	 */
	public void removeSemanticValue (final L2SemanticValue semanticValue)
	{
		semanticValues.remove(semanticValue);
	}

	/**
	 * Answer an iterator over the {@link L2Register}s of this synonym.  Do not
	 * alter the underlying collection via the iterator, nor alter the synonym
	 * while the iterator is still in use.
	 */
	public Iterator<R> registersIterator ()
	{
		return registers.iterator();
	}

	/**
	 * Answer a copy of the set of {@link L2Register}s of this synonym.
	 */
	public Set<R> registersCopy ()
	{
		return new HashSet<>(registers);
	}

	/**
	 * Answer an iterator over the {@link L2SemanticValue}s of this synonym.  Do
	 * not alter the underlying collection via the iterator, nor alter the
	 * synonym while the iterator is still in use.
	 */
	public Iterator<L2SemanticValue> semanticValuesIterator ()
	{
		return semanticValues.iterator();
	}

	/**
	 * Answer a copy of the set of {@link L2SemanticValue}s of this synonym.
	 */
	public Set<L2SemanticValue> semanticValuesCopy ()
	{
		return new HashSet<>(semanticValues);
	}

	/**
	 * Answer an {@link L2ReadOperand} for this synonym, which is required to
	 * contain at least one register.
	 */
	public <RR extends L2ReadOperand<R, T>> RR defaultRegisterRead ()
	{
		return cast(stripNull(defaultRegister).read(restriction));
	}

	/**
	 * Change which register should be used by default for this synonym.  This
	 * affects code generation, and can for force a particular register to be
	 * used, say, to avoid eliminating an intervening {@link L2_MAKE_IMMUTABLE}.
	 *
	 * @param register The new default register for this synonym.
	 */
	public void replaceDefaultRegister (final L2Register<?> register)
	{
		defaultRegister = cast(register);
	}

	/**
	 * Answer the {@link TypeRestriction} of this synonym.
	 *
	 * @return The {@link TypeRestriction}.
	 */
	public TypeRestriction<T> restriction ()
	{
		return restriction;
	}

	/**
	 * Strengthen the {@link TypeRestriction} of this synonym.  The
	 * strengthening can be a consequence of successful or unsuccessful type
	 * testing.
	 *
	 * @param newRestriction
	 *        The new {@link TypeRestriction} for this synonym.
	 */
	public void setRestriction (final TypeRestriction<T> newRestriction)
	{
		restriction = newRestriction;
	}

	/**
	 * Set this synonym's indicated flag.
	 *
	 * @param flag The flag to set.
	 */
	public void setFlag (final SynonymFlag flag)
	{
		flags.add(flag);
	}

	/**
	 * Clear this synonym's indicated flag.
	 *
	 * @param flag The flag to clear.
	 */
	public void clearFlag (final SynonymFlag flag)
	{
		flags.remove(flag);
	}

	/**
	 * Answer whether this synonym's value is known to be immutable already.
	 *
	 * @return The value of the indicated flag for this synonym.
	 */
	public boolean hasFlag (final SynonymFlag flag)
	{
		return flags.contains(flag);
	}

	/**
	 * Answer whether the number registers and semantic values within the
	 * receiver outnumbers the number in the argument.  This is used to minimize
	 * the cost of merging synonyms.
	 *
	 * @param other Another synonym to compare.
	 * @return Whether the receiver is strictly bigger.
	 */
	public boolean biggerThan (final L2Synonym<R, T> other)
	{
		return registers.size() + semanticValues.size()
			> other.registers.size() + other.semanticValues.size();
	}

	/**
	 * Recursively transform all {@link L2SemanticValue}s within the receiver,
	 * producing a new synonym in which the original synonym has been replaced
	 * by the replacement.  If nothing changed, answer the receiver.
	 *
	 * @param original
	 *        The original synonym to locate.
	 * @param replacement
	 *        The replacement synonym for each occurrence of the original.
	 * @return The replacement synonym, or the receiver if unchanged.
	 */
	public <R2 extends L2Register<T2>, T2 extends A_BasicObject>
	L2Synonym<R, T> transformInnerSynonym (
		final L2Synonym<R2, T2> original,
		final L2Synonym<R2, T2> replacement)
	{
		if (this == original)
		{
			// Don't recurse inside, because it's impossible for it to contain
			// itself cyclically.
			return cast(replacement);
		}
		// Expect few replacements, statistically.
		for (final L2SemanticValue semanticValue : semanticValues)
		{
			final L2SemanticValue transformedSemanticValue =
				semanticValue.transformInnerSynonym(original, replacement);
			if (semanticValue != transformedSemanticValue)
			{
				// At least one semantic value needs to be rewritten.
				final L2Synonym<R, T> newSynonym = new L2Synonym<>();
				newSynonym.registers.addAll(registers);
				for (final L2SemanticValue value : semanticValues)
				{
					// Avoid re-transforming the one we found, although we can't
					// easily avoid revisiting semantic values that we already
					// determined didn't need transformation.
					newSynonym.semanticValues.add(
						value == semanticValue
							? transformedSemanticValue
							: value.transformInnerSynonym(
								original, replacement));
				}
				newSynonym.defaultRegister = defaultRegister;
				newSynonym.restriction = restriction;
				return newSynonym;
			}
		}
		// Nothing needed to be transformed.
		return this;
	}

	/**
	 * Transform the {@link Frame}s and {@link L2SemanticValue}s within this
	 * synonym to produce a new synonym.
	 *
	 * @param semanticValueTransformer
	 *        How to transform each {@link L2SemanticValue}.
	 * @return The transformed synonym, or the original if there was no change.
	 */
	public L2Synonym<R, T> transform (
		final Function<L2SemanticValue, L2SemanticValue>
			semanticValueTransformer)
	{
		final L2Synonym<R, T> newSynonym = new L2Synonym<>();
		newSynonym.registers.addAll(registers);
		boolean changed = false;
		for (final L2SemanticValue semanticValue : semanticValues)
		{
			final L2SemanticValue newSemanticValue =
				semanticValueTransformer.apply(semanticValue);
			newSynonym.semanticValues.add(newSemanticValue);
			changed |= !newSemanticValue.equals(semanticValue);
		}
		return changed ? newSynonym : this;
	}

	/**
	 * Extract the {@link #unboxedSynonym}, strengthening its type as needed.
	 *
	 * @param <R2>
	 *        The {@link L2Register} type of the resulting synonym.
	 * @param <T2>
	 *        The content class of the resulting synonym.
	 * @return The unboxed synonym or {@code null}.
	 */
	public @Nullable <R2 extends L2Register<T2>, T2 extends A_BasicObject>
	L2Synonym<R2, T2> unboxedSynonym ()
	{
		return cast(unboxedSynonym);
	}

	/**
	 * Create an empty synonym.
	 */
	public L2Synonym ()
	{
		registers = new HashSet<>();
		semanticValues = new HashSet<>();
		defaultRegister = null;
		restriction = TypeRestriction.restriction(TOP.o());
	}

	/**
	 * Copy a synonym.
	 */
	public L2Synonym (final L2Synonym<R, T> original)
	{
		registers = new HashSet<>(original.registers);
		semanticValues = new HashSet<>(original.semanticValues);
		defaultRegister = original.defaultRegister;
		restriction = original.restriction;
		flags.addAll(original.flags);
	}
}