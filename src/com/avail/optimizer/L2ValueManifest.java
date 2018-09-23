/*
 * L2ValueManifest.java
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
package com.avail.optimizer;

import com.avail.descriptor.A_BasicObject;
import com.avail.descriptor.A_Number;
import com.avail.interpreter.levelTwo.operand.L2ReadFloatOperand;
import com.avail.interpreter.levelTwo.operand.L2ReadIntOperand;
import com.avail.interpreter.levelTwo.operand.L2ReadOperand;
import com.avail.interpreter.levelTwo.operand.L2ReadPointerOperand;
import com.avail.interpreter.levelTwo.operand.L2ReadVectorOperand;
import com.avail.interpreter.levelTwo.operand.L2WritePhiOperand;
import com.avail.interpreter.levelTwo.operand.L2WritePointerOperand;
import com.avail.interpreter.levelTwo.operand.TypeRestriction;
import com.avail.interpreter.levelTwo.operation.L2_PHI_PSEUDO_OPERATION;
import com.avail.interpreter.levelTwo.register.L2FloatRegister;
import com.avail.interpreter.levelTwo.register.L2IntRegister;
import com.avail.interpreter.levelTwo.register.L2ObjectRegister;
import com.avail.interpreter.levelTwo.register.L2Register;
import com.avail.optimizer.values.Frame;
import com.avail.optimizer.values.L2SemanticValue;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Function;

import static com.avail.optimizer.L2Synonym.SynonymFlag.KNOWN_IMMUTABLE;
import static com.avail.utility.Casts.cast;
import static com.avail.utility.Nulls.stripNull;

/**
 * The {@code L1ValueManifest} maintains a bidirectional mapping between visible
 * registers and {@link L2SemanticValue}s.
 *
 * <p>The mapping is maintained bidirectionally (value → register and
 * register → value), so that registers can be efficiently removed.  This
 * happens at control flow merges, where only the intersection of the semantic
 * values available in each predecessor edge is kept, specifically via the
 * introduction of new registers defined by phi instructions.</p>
 *
 * <p>Note that in any manifest, any particular {@link L2SemanticValue} must
 * have <em>at most one</em> register mapped to it.</p>
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class L2ValueManifest
{
	/** The synonyms keyed by registers. */
	private final Map<L2Register<?>, L2Synonym<?, ?>> registerToSynonym;

	/** The synonyms keyed by semantic values */
	private final Map<L2SemanticValue, L2Synonym<?, ?>> semanticValueToSynonym;

	/** Create a new, empty manifest. */
	public L2ValueManifest ()
	{
		registerToSynonym = new HashMap<>();
		semanticValueToSynonym = new HashMap<>();
	}

	/**
	 * Copy an existing manifest.
	 *
	 * @param originalManifest
	 * 	      The original manifest.
	 */
	public L2ValueManifest (final L2ValueManifest originalManifest)
	{
		registerToSynonym = new HashMap<>();
		semanticValueToSynonym = new HashMap<>();
		final Map<L2Synonym<?, ?>, L2Synonym<?, ?>> synonymMap =
			new HashMap<>();
		for (final Entry<L2Register<?>, L2Synonym<?, ?>> entry :
			originalManifest.registerToSynonym.entrySet())
		{
			registerToSynonym.put(
				entry.getKey(),
				synonymMap.computeIfAbsent(entry.getValue(), L2Synonym::new));
		}
		for (final Entry<L2SemanticValue, L2Synonym<?, ?>> entry :
			originalManifest.semanticValueToSynonym.entrySet())
		{
			semanticValueToSynonym.put(
				entry.getKey(),
				synonymMap.computeIfAbsent(entry.getValue(), L2Synonym::new));
		}
		// Update links between boxed/unboxed versions.  Do not create any new
		// synonyms here.
		for (final L2Synonym<?, ?> newSynonym
			: new HashSet<>(synonymMap.values()))
		{
			newSynonym.boxedSynonym =
				cast(synonymMap.get(newSynonym.boxedSynonym));
			newSynonym.unboxedSynonym =
				cast(synonymMap.get(newSynonym.unboxedSynonym));
		}
	}

	/**
	 * Look up the given semantic value, answering an {@link L2Synonym} that's
	 * bound to it, if any, otherwise {@code null}.
	 *
	 * @param semanticValue
	 *        The semantic value to look up.
	 * @return The {@link L2Synonym} bound to that that semantic value.
	 */
	public @Nullable <R extends L2Register<T>, T extends A_BasicObject>
	L2Synonym<R, T> semanticValueToSynonym (final L2SemanticValue semanticValue)
	{
		return cast(semanticValueToSynonym.get(semanticValue));
	}

	/**
	 * Look up the given register, answering the {@link L2Synonym} that it's
	 * associated with, if any, otherwise {@code null}.
	 *
	 * @param register
	 *        The {@link L2Register} to look up.
	 * @return The {@link L2Synonym} that it is currently a member of.
	 */
	public @Nullable <R extends L2Register<T>, T extends A_BasicObject>
	L2Synonym<R, T> registerToSynonym (final R register)
	{
		return cast(registerToSynonym.get(register));
	}

	/**
	 * Record the fact that the given register now holds this given semantic
	 * value, in addition to any it may have already held.  Note that this may
	 * bring together two previously distinct {@link L2Synonym}s.
	 *
	 * @param semanticValue
	 *        The {@link L2SemanticValue} to associate with the register.
	 * @param register
	 *        The {@link L2Register} to associate with the semantic value.
	 */
	public <R extends L2Register<T>, T extends A_BasicObject>
	void addBinding (
		final L2SemanticValue semanticValue,
		final R register)
	{
		final @Nullable L2Synonym<R, T> synonym1 =
			semanticValueToSynonym(semanticValue);
		@Nullable L2Synonym<R, T> synonym2 = registerToSynonym(register);
		if (synonym1 == null)
		{
			if (synonym2 == null)
			{
				synonym2 = new L2Synonym<>();
				synonym2.addRegister(register);
				synonym2.setRestriction(register.restriction());
				registerToSynonym.put(register, synonym2);
				// Fall through and use the new synonym2.
			}
			synonym2.addSemanticValue(semanticValue);
			semanticValueToSynonym.put(semanticValue, synonym2);
		}
		else if (synonym2 == null)
		{
			synonym1.addRegister(register);
			registerToSynonym.put(register, synonym1);
		}
		else if (synonym1 != synonym2)
		{
			// Merge the two (distinct) synonyms.
			final L2Synonym<R, T> small, large;
			if (synonym1.biggerThan(synonym2))
			{
				small = synonym2;
				large = synonym1;
			}
			else
			{
				small = synonym1;
				large = synonym2;
			}
			mergeSynonymInto(small, large);
			// Now update all PrimitiveSemanticValues that referred to the small
			// synonym to use the large one instead.  Note that this has to be
			// applied recursively.

		}
	}

	/**
	 * An existing read should be associated with the newly created write, which
	 * has been ensured immutable.  If the source is not part of any synonym,
	 * the destination will not be either, and they will not be associated.
	 *
	 * @param sourceRead
	 *        The register read which should be associated with the write.
	 * @param destinationWrite
	 *        The register write of an immutable version of the same value that
	 *        is in the sourceRead.
	 */
	void introduceImmutable (
		final L2ReadPointerOperand sourceRead,
		final L2WritePointerOperand destinationWrite)
	{
		final @Nullable L2Synonym<L2ObjectRegister, A_BasicObject> synonym =
			registerToSynonym(sourceRead.register());
		if (synonym != null)
		{
			// Ensure attempts to look up any of the associated semantic values
			// will use the destination register, rather than one of the others.
			final L2ObjectRegister destinationRegister =
				destinationWrite.register();
			registerToSynonym.put(destinationRegister, synonym);
			synonym.addRegister(destinationRegister);
			synonym.replaceDefaultRegister(destinationRegister);
			synonym.setFlag(KNOWN_IMMUTABLE);
		}
	}

	/**
	 * Merge the registers and semantic values of the smaller synonym into the
	 * larger synonym.  Update the manifest to reflect the merge.
	 *
	 * @param small
	 *        The smaller {@link L2Synonym} to merge from.
	 * @param large
	 *        The larger {@link L2Synonym} to merge into.
	 * @param <R> The register class.
	 * @param <T> The base type of restrictions on the included registers.
	 */
	private <R extends L2Register<T>, T extends A_BasicObject>
	void mergeSynonymInto (
		final L2Synonym<R, T> small,
		final L2Synonym<R, T> large)
	{
		assert small != large;
		assert small.getClass() == large.getClass();  // Generics can't do this.
		for (
			final Iterator<R> iterator = small.registersIterator();
			iterator.hasNext();)
		{
			final R register = iterator.next();
			large.addRegister(register);
			assert registerToSynonym.get(register) == small;
			registerToSynonym.put(register, large);
		}
		for (
			final Iterator<L2SemanticValue> iterator =
				small.semanticValuesIterator();
			iterator.hasNext();)
		{
			final L2SemanticValue semanticValue = iterator.next();
			large.addSemanticValue(semanticValue);
			assert semanticValueToSynonym.get(semanticValue) == small;
			semanticValueToSynonym.put(semanticValue, large);
		}
		large.setRestriction(
			small.restriction().intersection(large.restriction()));
		// Now update uses of small with large.
		for (final Entry<L2SemanticValue, L2Synonym<?, ?>> entry :
			new ArrayList<>(semanticValueToSynonym.entrySet()))
		{
			final L2SemanticValue originalSemanticValue = entry.getKey();
			final L2SemanticValue transformedSemanticValue =
				originalSemanticValue.transformInnerSynonym(small, large);
			if (originalSemanticValue != transformedSemanticValue)
			{
				final L2Synonym<?, ?> synonym = entry.getValue();
				// Replace the semantic value inside the synonym.
				synonym.removeSemanticValue(originalSemanticValue);
				synonym.addSemanticValue(transformedSemanticValue);
				// Update the manifest's map from semantic values to synonym.
				semanticValueToSynonym.remove(originalSemanticValue);
				semanticValueToSynonym.put(transformedSemanticValue, large);
			}
		}

		if (small.hasFlag(KNOWN_IMMUTABLE) && !large.hasFlag(KNOWN_IMMUTABLE))
		{
			large.replaceDefaultRegister(
				small.defaultRegisterRead().register());
			large.setFlag(KNOWN_IMMUTABLE);
		}
		large.setRestriction(
			large.restriction().intersection(small.restriction()));

		if (small.boxedSynonym != null)
		{
			if (large.boxedSynonym != null
				&& large.unboxedSynonym != small.unboxedSynonym)
			{
				mergeSynonymInto(small.boxedSynonym, large.boxedSynonym);
			}
			else
			{
				large.boxedSynonym = small.boxedSynonym;
			}
		}
		if (small.unboxedSynonym != null)
		{
			if (large.unboxedSynonym != null
				&& large.unboxedSynonym != small.unboxedSynonym)
			{
				mergeSynonymInto(
					stripNull(small.unboxedSynonym()),
					stripNull(large.unboxedSynonym()));
			}
			else
			{
				large.unboxedSynonym = small.unboxedSynonym;
			}
		}
	}

	/**
	 * Does the given register contain an immutable value at this point?  If the
	 * register is not part of a synonym, assume it has not been made immutable.
	 *
	 * @param register
	 *        The {@link L2Register} to test.
	 * @return {@code true} if the register is known to contain an immutable
	 *         value here, {@code false} otherwise.
	 */
	public boolean isAlreadyImmutable (final L2Register<?> register)
	{
		final @Nullable L2Synonym<?, ?> synonym =
			registerToSynonym.get(register);
		return synonym != null && synonym.hasFlag(KNOWN_IMMUTABLE);
	}

	/**
	 * Answer the unboxed {@code int} variant of the specified {@link
	 * L2ReadPointerOperand}, if one already exists.
	 *
	 * @param registerRead
	 *        The {@code L2ReadPointerOperand} to test.
	 * @return The requested unboxed variant or {@code null}.
	 */
	public @Nullable L2ReadIntOperand alreadyUnboxedInt (
		final L2ReadPointerOperand registerRead)
	{
		final @Nullable L2Synonym<L2ObjectRegister, A_BasicObject>
			boxedSynonym = registerToSynonym(registerRead.register());
		if (boxedSynonym == null)
		{
			return null;
		}
		@Nullable L2Synonym<L2IntRegister, A_Number> unboxed =
			boxedSynonym.unboxedSynonym();
		if (unboxed != null)
		{
			return unboxed.defaultRegisterRead();
		}
		final Iterator<L2SemanticValue> iterator =
			boxedSynonym.semanticValuesIterator();
		while (iterator.hasNext())
		{
			final L2SemanticValue unboxedValue = iterator.next().unboxedAsInt();
			unboxed = semanticValueToSynonym(unboxedValue);
			if (unboxed != null)
			{
				// Connect the synonyms.
				boxedSynonym.unboxedSynonym = unboxed;
				unboxed.boxedSynonym = boxedSynonym;
				return unboxed.defaultRegisterRead();
			}
		}
		return null;
	}

	/**
	 * Answer the unboxed {@code double} variant of the specified {@link
	 * L2ReadPointerOperand}, if one already exists.
	 *
	 * @param registerRead
	 *        The {@code L2ReadPointerOperand} to test.
	 * @return The requested unboxed variant {@code null}.
	 */
	public @Nullable L2ReadFloatOperand alreadyUnboxedFloat (
		final L2ReadPointerOperand registerRead)
	{
		final @Nullable L2Synonym<L2ObjectRegister, A_BasicObject>
			boxedSynonym = registerToSynonym(registerRead.register());
		if (boxedSynonym == null)
		{
			return null;
		}
		@Nullable L2Synonym<L2FloatRegister, A_Number> unboxed =
			boxedSynonym.unboxedSynonym();
		if (unboxed != null)
		{
			return unboxed.defaultRegisterRead();
		}
		final Iterator<L2SemanticValue> iterator =
			boxedSynonym.semanticValuesIterator();
		while (iterator.hasNext())
		{
			final L2SemanticValue unboxedValue =
				iterator.next().unboxedAsFloat();
			unboxed = semanticValueToSynonym(unboxedValue);
			if (unboxed != null)
			{
				// Connect the synonyms.
				boxedSynonym.unboxedSynonym = unboxed;
				unboxed.boxedSynonym = boxedSynonym;
				return unboxed.defaultRegisterRead();
			}
		}
		return null;
	}

	/**
	 * Answer the boxed variant of the specified {@link L2ReadIntOperand}, if
	 * one already exists.
	 *
	 * @param registerRead
	 *        The {@code L2ReadIntOperand} to test.
	 * @return The requested boxed variant.
	 */
	public @Nullable L2ReadPointerOperand alreadyBoxed (
		final L2ReadIntOperand registerRead)
	{
		final @Nullable L2Synonym<L2IntRegister, A_Number> unboxedSynonym =
			registerToSynonym(registerRead.register());
		if (unboxedSynonym == null)
		{
			return null;
		}
		if (unboxedSynonym.boxedSynonym != null)
		{
			return unboxedSynonym.boxedSynonym.defaultRegisterRead();
		}
		final Iterator<L2SemanticValue> iterator =
			unboxedSynonym.semanticValuesIterator();
		while (iterator.hasNext())
		{
			final L2SemanticValue boxedSemanticValue = iterator.next().boxed();
			final @Nullable L2Synonym<L2ObjectRegister, A_BasicObject>
				boxedSynonym = semanticValueToSynonym(boxedSemanticValue);
			if (boxedSynonym != null)
			{
				// Connect the synonyms.
				unboxedSynonym.boxedSynonym = boxedSynonym;
				boxedSynonym.unboxedSynonym = unboxedSynonym;
				return boxedSynonym.defaultRegisterRead();
			}
		}
		return null;
	}

	/**
	 * Answer the boxed variant of the specified {@link L2ReadFloatOperand}, if
	 * one already exists.
	 *
	 * @param registerRead
	 *        The {@code L2ReadFloatOperand} to test.
	 * @return The requested boxed variant.
	 */
	public @Nullable L2ReadPointerOperand alreadyBoxed (
		final L2ReadFloatOperand registerRead)
	{
		final @Nullable L2Synonym<L2FloatRegister, A_Number> unboxedSynonym =
			registerToSynonym(registerRead.register());
		if (unboxedSynonym == null)
		{
			return null;
		}
		if (unboxedSynonym.boxedSynonym != null)
		{
			return unboxedSynonym.boxedSynonym.defaultRegisterRead();
		}
		final Iterator<L2SemanticValue> iterator =
			unboxedSynonym.semanticValuesIterator();
		while (iterator.hasNext())
		{
			final L2SemanticValue boxedSemanticValue = iterator.next().boxed();
			final @Nullable L2Synonym<L2ObjectRegister, A_BasicObject>
				boxedSynonym = semanticValueToSynonym(boxedSemanticValue);
			if (boxedSynonym != null)
			{
				// Connect the synonyms.
				unboxedSynonym.boxedSynonym = boxedSynonym;
				boxedSynonym.unboxedSynonym = unboxedSynonym;
				return boxedSynonym.defaultRegisterRead();
			}
		}
		return null;
	}

	/**
	 * Answer a copy of the set of {@link L2Synonym}s in this manifest.
	 *
	 * @return The indicated {@link Set}.
	 */
	public Set<L2Synonym<?, ?>> synonyms ()
	{
		return new HashSet<>(registerToSynonym.values());
	}

	/**
	 * Remove all information about registers and semantic values from this
	 * manifest.
	 */
	public void clear ()
	{
		registerToSynonym.clear();
		semanticValueToSynonym.clear();
	}

	/**
	 * Populate the empty receiver with bindings from the incoming manifests.
	 * Only keep the bindings for {@link L2SemanticValue}s that occur in all
	 * incoming manifests.  Generate phi functions as needed on the provided
	 * {@link L2Generator}.  The phi functions' source registers correspond
	 * positionally with the list of manifests.
	 *
	 * @param manifests
	 *        The list of manifests from which to populate the receiver.
	 * @param translator
	 *        The {@link L1Translator} on which to write any necessary phi
	 *        functions.
	 */
	void populateFromIntersection (
		final List<L2ValueManifest> manifests,
		final L1Translator translator)
	{
		assert registerToSynonym.isEmpty();
		assert semanticValueToSynonym.isEmpty();
		final int manifestsSize = manifests.size();
		if (manifestsSize == 0)
		{
			return;
		}
		if (manifestsSize == 1)
		{
			// Special case of a single incoming edge.  Copy each synonym.
			for (final L2Synonym<?, ?> synonym : manifests.get(0).synonyms())
			{
				final L2Synonym<?, ?> synonymCopy = new L2Synonym<>(synonym);
				final Iterator<? extends L2Register<?>> registers =
					synonymCopy.registersIterator();
				while (registers.hasNext())
				{
					registerToSynonym.put(registers.next(), synonymCopy);
				}
				final Iterator<L2SemanticValue> values =
					synonymCopy.semanticValuesIterator();
				while (values.hasNext())
				{
					semanticValueToSynonym.put(values.next(), synonymCopy);
				}
			}
			return;
		}
		final List<L2ValueManifest> otherManifests = new ArrayList<>(manifests);
		final L2ValueManifest firstManifest = otherManifests.remove(0);
		// Avoid producing multiple phi instructions for the same list of
		// incoming synonyms.  Explicitly track them.
		final Map<List<L2Synonym<?, ?>>, L2Register<?>> phiMap =
			new HashMap<>();

		// For each semantic value, Figure out commonalities for it in the
		// manifests' bindings.
		semanticValues:
		for (final Entry<L2SemanticValue, L2Synonym<?, ?>> entry
			: firstManifest.semanticValueToSynonym.entrySet())
		{
			final L2SemanticValue semanticValue = entry.getKey();
			final L2Synonym<?, ?> firstSynonym = entry.getValue();
			final Set<? extends L2Register<?>> commonRegisters =
				firstSynonym.registersCopy();
			final List<L2Synonym<?, ?>> sourceSynonyms =
				new ArrayList<>(manifestsSize);
			sourceSynonyms.add(firstSynonym);
			boolean allImmutable = firstSynonym.hasFlag(KNOWN_IMMUTABLE);
			for (final L2ValueManifest otherManifest : otherManifests)
			{
				final @Nullable L2Synonym<?, ?> otherSynonym =
					otherManifest.semanticValueToSynonym(semanticValue);
				if (otherSynonym == null)
				{
					// At least one incoming edge doesn't know about the
					// semantic value, so don't preserve it or make a phi.
					// Eventually we'll collect these into some sort of set of
					// semantic values that were known in at least some
					// ancestors, to support total redundancy elimination.
					continue semanticValues;
				}
				commonRegisters.retainAll(otherSynonym.registersCopy());
				sourceSynonyms.add(otherSynonym);
				allImmutable &= otherSynonym.hasFlag(KNOWN_IMMUTABLE);
			}
			// Every incoming manifest had at least one register providing the
			// semantic value.
			if (!commonRegisters.isEmpty())
			{
				// The manifests all had at least one register in common which
				// was mapped to the semantic value.  Make these common
				// registers available in the new manifest.
				for (final L2Register<?> register : commonRegisters)
				{
					addBinding(semanticValue, register);
				}
				// Try to preserve the first manifest's default register.
				final L2Register<?> firstDefaultRegister =
					firstSynonym.defaultRegisterRead().register();
				final @Nullable L2Synonym<?, ?> newSynonym =
					registerToSynonym.get(commonRegisters.iterator().next());
				assert newSynonym != null;
				if (commonRegisters.contains(firstDefaultRegister))
				{
					newSynonym.replaceDefaultRegister(firstDefaultRegister);
				}
				// Preserve knowledge of immutability.
				if (allImmutable)
				{
					newSynonym.setFlag(KNOWN_IMMUTABLE);
				}
			}
			else if (phiMap.containsKey(sourceSynonyms))
			{
				// The manifests all supplied semantic values, but in different
				// registers, and in a pattern of source synonyms that we've
				// already encountered for a previous semantic value.  Reuse the
				// phi variable.
				addBinding(semanticValue, phiMap.get(sourceSynonyms));
			}
			else
			{
				// The manifests all supplied the semantic value, but in
				// different registers, and we haven't seen that particular
				// pattern of source synonyms for previous semantic values.
				// Create a new register and a phi function to populate it, and
				// record the list of synonyms it came from so that we can avoid
				// producing extra phi functions.
				final List<L2ReadOperand<?, ?>> reads =
					new ArrayList<>(manifestsSize);
				reads.add(firstSynonym.defaultRegisterRead());
				TypeRestriction<A_BasicObject> newRestriction =
					cast(firstSynonym.restriction());
				for (final L2ValueManifest otherManifest : otherManifests)
				{
					final @Nullable L2Synonym<?, A_BasicObject> otherSynonym =
						otherManifest.semanticValueToSynonym(semanticValue);
					assert otherSynonym != null;
					reads.add(otherSynonym.defaultRegisterRead());
					final TypeRestriction<A_BasicObject> otherRestriction =
						otherSynonym.restriction();
					newRestriction = newRestriction.union(otherRestriction);
				}
				final L2Register<A_BasicObject> firstDefaultRegister =
					cast(firstSynonym.defaultRegisterRead().register());
				final L2Register<A_BasicObject> newRegister =
					firstDefaultRegister.copyForTranslator(
						translator, newRestriction);
				final L2WritePhiOperand<?, ?> newPhiWrite =
					translator.newPhiRegisterWriter(newRegister);
				translator.addInstruction(
					L2_PHI_PSEUDO_OPERATION.instance,
					new L2ReadVectorOperand<>(cast(reads)),
					newPhiWrite);
				final L2Register<?> newPhiRegister = newPhiWrite.register();
				addBinding(semanticValue, newPhiRegister);
				phiMap.put(sourceSynonyms, newPhiRegister);
			}
		}
		// Every semantic value present in all predecessors is now defined in
		// this new manifest.  Now connect any unboxed/boxed pairs of synonyms
		// that have related semantic values.  Be careful to copy collections to
		// avoid altering them while iterating over them.
		final Set<L2Synonym<?, ?>> unboxedSynonyms = new HashSet<>();
		for (final Entry<L2SemanticValue, L2Synonym<?, ?>> entry
			: semanticValueToSynonym.entrySet())
		{
			final L2SemanticValue semanticValue = entry.getKey();
			if (semanticValue.isUnboxedInt() || semanticValue.isUnboxedFloat())
			{
				unboxedSynonyms.add(entry.getValue());
			}
		}
		for (final L2Synonym<?, ?> unboxedSynonym : unboxedSynonyms)
		{
			final Set<L2SemanticValue> unboxedSet =
				unboxedSynonym.semanticValuesCopy();
			for (final L2SemanticValue unboxedSemanticValue : unboxedSet)
			{
				final L2SemanticValue boxedSemanticValue =
					unboxedSemanticValue.boxed();
				final @Nullable L2Synonym<L2ObjectRegister, A_BasicObject>
					boxedSynonym = semanticValueToSynonym(boxedSemanticValue);
				if (boxedSynonym != null)
				{
					// We have both unboxed and boxed versions of related
					// semantic values, so connect them.

					// First merge the immutability information.
					if (unboxedSynonym.hasFlag(KNOWN_IMMUTABLE))
					{
						boxedSynonym.setFlag(KNOWN_IMMUTABLE);
					}
					if (boxedSynonym.hasFlag(KNOWN_IMMUTABLE))
					{
						unboxedSynonym.setFlag(KNOWN_IMMUTABLE);
					}

					if (boxedSynonym.unboxedSynonym == null)
					{
						// It's the first connection.
						boxedSynonym.unboxedSynonym = cast(unboxedSynonym);
					}
					else if (boxedSynonym.unboxedSynonym != unboxedSynonym)
					{
						// Merge the two distinct unboxed synonyms.
						mergeSynonymInto(
							cast(unboxedSynonym),
							boxedSynonym.unboxedSynonym);
					}
					// And do the same going the other way.
					if (unboxedSynonym.boxedSynonym == null)
					{
						// First connection.
						unboxedSynonym.boxedSynonym = boxedSynonym;
					}
					else if (unboxedSynonym.boxedSynonym != boxedSynonym)
					{
						// Merge the two distinct boxed synonyms.
						mergeSynonymInto(
							boxedSynonym,
							unboxedSynonym.boxedSynonym);
					}
				}
			}
		}
	}

	public L2ValueManifest transform (
		final Function<L2SemanticValue, L2SemanticValue>
			semanticValueTransformer,
		final Function<Frame, Frame> frameTransformer)
	{
		final L2ValueManifest newManifest = new L2ValueManifest();
		final Set<L2Synonym<?, ?>> originalSynonyms =
			new HashSet<>(registerToSynonym.values());
		for (final L2Synonym<?, ?> originalSynonym : originalSynonyms)
		{
			final L2Synonym<?, ?> newSynonym =
				originalSynonym.transform(semanticValueTransformer);
			if (newSynonym != originalSynonym)
			{
				for (
					final Iterator<? extends L2Register<?>> iterator =
						newSynonym.registersIterator();
					iterator.hasNext();)
				{
					newManifest.registerToSynonym.put(
						iterator.next(), newSynonym);
				}
				for (
					final Iterator<L2SemanticValue> iterator =
						newSynonym.semanticValuesIterator();
					iterator.hasNext();)
				{
					newManifest.semanticValueToSynonym.put(
						iterator.next(), newSynonym);
				}
			}
		}
		return newManifest;
	}
}