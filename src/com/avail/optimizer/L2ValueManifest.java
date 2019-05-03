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
import com.avail.interpreter.levelTwo.operand.L2ReadOperand;
import com.avail.interpreter.levelTwo.operand.L2ReadPointerOperand;
import com.avail.interpreter.levelTwo.operand.L2ReadVectorOperand;
import com.avail.interpreter.levelTwo.operand.L2WritePhiOperand;
import com.avail.interpreter.levelTwo.operand.L2WritePointerOperand;
import com.avail.interpreter.levelTwo.operand.TypeRestriction;
import com.avail.interpreter.levelTwo.operation.L2_PHI_PSEUDO_OPERATION;
import com.avail.interpreter.levelTwo.register.L2ObjectRegister;
import com.avail.interpreter.levelTwo.register.L2Register;
import com.avail.interpreter.levelTwo.register.L2Register.RegisterKind;
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

import static com.avail.interpreter.levelTwo.register.L2Register.RegisterKind.OBJECT;
import static com.avail.optimizer.L2Synonym.SynonymFlag.KNOWN_IMMUTABLE;
import static com.avail.utility.Casts.cast;
import static com.avail.utility.Nulls.stripNull;

/**
 * The {@code L2ValueManifest} maintains information about which {@link
 * L2Register}s hold values representing which {@link L2SemanticValue}s,
 * specifically using {@link L2Synonym}s as the binding mechanism.
 *
 * <p>The mapping is keyed both ways (semantic value → synonym, and register →
 * synonym), so that registers can be efficiently removed.  This happens at
 * control flow merges, where only the intersection of the semantic values
 * available in each predecessor edge is kept, specifically via the introduction
 * of new registers defined by phi instructions.</p>
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class L2ValueManifest
{
	/** The synonyms keyed by registers. */
	private final Map<L2Register, L2Synonym> registerToSynonym;

	/** The synonyms keyed by semantic values */
	private final Map<L2SemanticValue, L2Synonym> semanticValueToSynonym;

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
		final Map<L2Synonym, L2Synonym> synonymMap =
			new HashMap<>();
		for (final Entry<L2Register, L2Synonym> entry :
			originalManifest.registerToSynonym.entrySet())
		{
			registerToSynonym.put(
				entry.getKey(),
				synonymMap.computeIfAbsent(entry.getValue(), L2Synonym::new));
		}
		for (final Entry<L2SemanticValue, L2Synonym> entry :
			originalManifest.semanticValueToSynonym.entrySet())
		{
			semanticValueToSynonym.put(
				entry.getKey(),
				synonymMap.computeIfAbsent(entry.getValue(), L2Synonym::new));
		}
	}

	/**
	 * Look up the given semantic value, answering an {@link L2Synonym} that's
	 * bound to it, if any, otherwise {@code null}.
	 *
	 * @param semanticValue
	 *        The semantic value to look up.
	 * @return The {@link L2Synonym} bound to that semantic value.
	 */
	public @Nullable <R extends L2Register, T extends A_BasicObject>
	L2Synonym semanticValueToSynonym (final L2SemanticValue semanticValue)
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
	public @Nullable <R extends L2Register, T extends A_BasicObject>
	L2Synonym registerToSynonym (final R register)
	{
		return cast(registerToSynonym.get(register));
	}

	/**
	 * Answer the {@link L2Synonym} associated with the given {@link
	 * L2Register}, creating and storing one if necessary.
	 *
	 * @param register
	 *        An {@link L2Register} that may or may not already have been
	 *        registered with this manifest.
	 * @return The {@link L2Synonym} that is now associated with the given
	 *         register.
	 */
	public L2Synonym forceSynonymForRegister (final L2Register register)
	{
		@Nullable L2Synonym synonym = registerToSynonym.get(register);
		if (synonym == null)
		{
			synonym = new L2Synonym(register.restriction());
			synonym.addRegister(register);
			registerToSynonym.put(register, synonym);
		}
		return synonym;
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
	public void addBinding (
		final L2SemanticValue semanticValue,
		final L2Register register)
	{
		final @Nullable L2Synonym synonym1 =
			semanticValueToSynonym(semanticValue);
		final @Nullable L2Synonym synonym2 = registerToSynonym(register);
		if (synonym1 == null)
		{
			if (synonym2 == null)
			{
				final L2Synonym newSynonym =
					new L2Synonym(register.restriction());
				newSynonym.addRegister(register);
				registerToSynonym.put(register, newSynonym);
				newSynonym.addSemanticValue(semanticValue);
				semanticValueToSynonym.put(semanticValue, newSynonym);
			}
			else
			{
				synonym2.addSemanticValue(semanticValue);
				semanticValueToSynonym.put(semanticValue, synonym2);
			}
		}
		else if (synonym2 == null)
		{
			synonym1.addRegister(register);
			registerToSynonym.put(register, synonym1);
		}
		else if (synonym1 != synonym2)
		{
			// Merge the two (distinct) synonyms.
			mergeSynonyms(synonym1, synonym2);
		}
	}

	/**
	 * Record the fact that the given registers now hold the same value.  Note
	 * that this may bring together two previously distinct {@link L2Synonym}s,
	 * or create a new {@code L2Synonym} that does not yet include any {@link
	 * L2SemanticValue}s.
	 *
	 * @param register1
	 *        An {@link L2Register}.
	 * @param register2
	 *        A second {@link L2Register} that holds the same value.
	 */
	public void linkEqualRegisters (
		final L2Register register1,
		final L2Register register2)
	{
		final @Nullable L2Synonym synonym1 = registerToSynonym(register1);
		final @Nullable L2Synonym synonym2 = registerToSynonym(register2);
		if (synonym1 == null)
		{
			if (synonym2 == null)
			{
				final L2Synonym newSynonym = new L2Synonym(
					register1.restriction().intersection(
						register2.restriction()));
				newSynonym.addRegister(register1);
				newSynonym.addRegister(register2);
				registerToSynonym.put(register1, newSynonym);
				registerToSynonym.put(register2, newSynonym);
			}
			else
			{
				synonym2.addRegister(register1);
				registerToSynonym.put(register1, synonym2);
				synonym2.setRestriction(
					synonym2.restriction().intersection(
						register1.restriction()));
			}
		}
		else if (synonym2 == null)
		{
			synonym1.addRegister(register2);
			registerToSynonym.put(register2, synonym1);
			synonym1.setRestriction(
				synonym1.restriction().intersection(
					register2.restriction()));
		}
		else if (synonym1 != synonym2)
		{
			mergeSynonyms(synonym1, synonym2);
		}
	}

	/**
	 * Merge the given {@link L2Synonym}s, since they're now known to contain
	 * the same value.
	 *
	 * @param synonym1 An {@code L2Synonym}.
	 * @param synonym2 Another {@code L2Synonym} representing the same value.
	 */
	private void mergeSynonyms (
		final L2Synonym synonym1,
		final L2Synonym synonym2)
	{
		// Merge the smaller synonym into the larger.
		final L2Synonym small, large;
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
		// TODO MvG - Now update all PrimitiveSemanticValues that referred
		// to the small synonym to use the large one instead.  Note that
		// this has to be applied recursively.

		// Now merge the small synonym into the larger.
		mergeSynonymInto(small, large);
	}

	/**
	 * Merge the registers and semantic values of the smaller synonym into the
	 * larger synonym.  Update the manifest to reflect the merge.
	 *
	 * @param small
	 *        The smaller {@link L2Synonym} to merge from.
	 * @param large
	 *        The larger {@link L2Synonym} to merge into.
	 */
	void mergeSynonymInto (
		final L2Synonym small,
		final L2Synonym large)
	{
		assert small != large;
		for (
			final Iterator<L2Register> iterator = small.registersIterator();
			iterator.hasNext();)
		{
			final L2Register register = iterator.next();
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
		for (final Entry<L2SemanticValue, L2Synonym> entry :
			new ArrayList<>(semanticValueToSynonym.entrySet()))
		{
			final L2SemanticValue originalSemanticValue = entry.getKey();
			final L2SemanticValue transformedSemanticValue =
				originalSemanticValue.transformInnerSynonym(small, large);
			if (originalSemanticValue != transformedSemanticValue)
			{
				final L2Synonym synonym = entry.getValue();
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
			// Ensure the default object register in large is replaced by the
			// one from small, otherwise some later uses might use a register
			// that bypasses the makeImmutable().  However, for the unboxed
			// registers it doesn't matter which one gets used.  Note that since
			// small is known to be immutable, it *must* already have a default
			// boxed register.
			final L2ObjectRegister immutableRegister =
				stripNull(small.defaultObjectRead()).register();
			large.replaceDefaultObjectRegister(immutableRegister);
			large.setFlag(KNOWN_IMMUTABLE);
		}
		large.setRestriction(
			large.restriction().intersection(small.restriction()));
	}

	/**
	 * An existing read should be associated with the newly created write, which
	 * has been ensured immutable.
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
		final L2ObjectRegister destinationRegister =
			destinationWrite.register();
		linkEqualRegisters(sourceRead.register(), destinationRegister);
		final L2Synonym synonym =
			stripNull(registerToSynonym(sourceRead.register()));
		// Ensure attempts to look up any of the associated semantic values
		// will use the destination register, rather than one of the others.
		registerToSynonym.put(destinationRegister, synonym);
		synonym.addRegister(destinationRegister);
		synonym.replaceDefaultObjectRegister(destinationRegister);
		synonym.setFlag(KNOWN_IMMUTABLE);
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
	public boolean isAlreadyImmutable (final L2Register register)
	{
		final @Nullable L2Synonym synonym = registerToSynonym.get(register);
		return synonym != null && synonym.hasFlag(KNOWN_IMMUTABLE);
	}

	/**
	 * Answer a copy of the set of {@link L2Synonym}s in this manifest.
	 *
	 * @return The indicated {@link Set}.
	 */
	public Set<L2Synonym> synonyms ()
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
	 * @param generator
	 *        The {@link L2Generator} on which to write any necessary phi
	 *        functions.
	 */
	void populateFromIntersection (
		final List<L2ValueManifest> manifests,
		final L2Generator generator)
	{
		assert registerToSynonym.isEmpty();
		assert semanticValueToSynonym.isEmpty();
		final int manifestsSize = manifests.size();
		if (manifestsSize == 0)
		{
			// Unreachable, or an entry point where no registers are set.
			return;
		}
		if (manifestsSize == 1)
		{
			// Special case of a single incoming edge.  Copy each synonym.
			for (final L2Synonym synonym : manifests.get(0).synonyms())
			{
				final L2Synonym synonymCopy = new L2Synonym(synonym);
				final Iterator<L2Register> registers =
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

		// Treat each kind (boxed, int, float) of register separately, since
		// we don't take responsibility for boxing/unboxing here if values
		// of some kind are only available in a subset of source edges.
		for (final RegisterKind kind : RegisterKind.all)
		{
			// Avoid producing multiple phi instructions for the same list of
			// incoming synonyms (for each RegisterKind). Explicitly track them.
			final Map<List<L2Synonym>, L2Register> phiMap = new HashMap<>();

			// For each semantic value, figure out commonalities for it in the
			// manifests' bindings.
			semanticValues:
			for (final Entry<L2SemanticValue, L2Synonym> entry
				: firstManifest.semanticValueToSynonym.entrySet())
			{
				final L2SemanticValue semanticValue = entry.getKey();
				final L2Synonym firstSynonym = entry.getValue();
				if (kind.getDefaultRegister(firstSynonym) == null)
				{
					// The synonym doesn't have the right kind of register.
					//noinspection UnnecessaryLabelOnContinueStatement
					continue semanticValues;
				}
				final Set<? extends L2Register> commonRegisters =
					kind.getRegistersCopy(firstSynonym);
				final List<L2Synonym> sourceSynonyms =
					new ArrayList<>(manifestsSize);
				sourceSynonyms.add(firstSynonym);
				boolean allImmutable = firstSynonym.hasFlag(KNOWN_IMMUTABLE);
				for (final L2ValueManifest otherManifest : otherManifests)
				{
					final @Nullable L2Synonym otherSynonym =
						otherManifest.semanticValueToSynonym(semanticValue);
					if (otherSynonym == null
						|| kind.getDefaultRegister(otherSynonym) == null)
					{
						// At least one incoming edge doesn't know about the
						// semantic value (or at least in the right kind of
						// register), so we can't preserve it in a mutually
						// common register or make a phi.
						// TODO: Eventually we'll collect these into some sort
						// of set of semantic values that were known in at least
						// some ancestors, to support total redundancy
						// elimination.
						continue semanticValues;
					}
					//noinspection SuspiciousMethodCalls
					commonRegisters.retainAll(otherSynonym.registersCopy());
					sourceSynonyms.add(otherSynonym);
					// TODO: Similarly to above, tracking the fact that some
					// ancestors are known to be immutable could be used for
					// total redundancy elimination – in this case, duplicating
					// code to avoid having to make a value immutable if it
					// already was known to be immutable along some code paths.
					allImmutable &= otherSynonym.hasFlag(KNOWN_IMMUTABLE);
				}
				// Every incoming manifest had at least one register (of this
				// RegisterKind) providing the semantic value.
				if (!commonRegisters.isEmpty())
				{
					// The manifests all had at least one register in common
					// which was mapped to the semantic value.  Make these
					// common registers available in the new manifest.
					if (kind == OBJECT)
					{
						// Start with the first synonym's default register, so
						// that it will be used as the default register of the
						// new synonym, for stability and minimization of moves,
						// and to ensure immutability if needed.
						final L2Register firstDefaultRegister =
							stripNull(firstSynonym.defaultObjectRead())
								.register();
						if (commonRegisters.contains(firstDefaultRegister))
						{
							// The first incoming edge's default register of
							// this kind for this semantic value should ideally
							// be the one that acts as the default register in
							// the new manifest.
							addBinding(semanticValue, firstDefaultRegister);
						}
					}
					for (final L2Register register : commonRegisters)
					{
						addBinding(semanticValue, register);
					}
					// Try to preserve the first manifest's default register.

					// Preserve knowledge of immutability.
					if (allImmutable)
					{
						final L2Synonym synonym =
							stripNull(semanticValueToSynonym(semanticValue));
						synonym.setFlag(KNOWN_IMMUTABLE);
					}
				}
				else if (phiMap.containsKey(sourceSynonyms))
				{
					// The manifests all supplied semantic values, but in
					// different registers, and in a pattern of source synonyms
					// that we've already encountered for a previous semantic
					// value.  Reuse the phi variable.
					addBinding(semanticValue, phiMap.get(sourceSynonyms));
				}
				else
				{
					// The manifests all supplied the semantic value, but in
					// different registers, and we haven't seen that particular
					// pattern of source synonyms for previous semantic values.
					// Create a new register and a phi function to populate it,
					// and record the list of synonyms it came from so that we
					// can avoid producing extra phi functions.
					final List<L2ReadOperand<?>> reads =
						new ArrayList<>(manifestsSize);
					reads.add(kind.getDefaultRegister(firstSynonym));
					TypeRestriction newRestriction =
						cast(firstSynonym.restriction());
					for (final L2ValueManifest otherManifest : otherManifests)
					{
						final L2Synonym otherSynonym = stripNull(
							otherManifest.semanticValueToSynonym(
								semanticValue));
						reads.add(kind.getDefaultRegister(otherSynonym));
						newRestriction = newRestriction.union(
							otherSynonym.restriction());
					}
					final L2Register firstDefaultRegister =
						stripNull(firstSynonym.defaultObjectRead()).register();
					final L2Register newRegister =
						firstDefaultRegister.copyForTranslator(
							generator, newRestriction);
					final L2WritePhiOperand<?> newPhiWrite =
						generator.newPhiRegisterWriter(newRegister);
					generator.addInstruction(
						L2_PHI_PSEUDO_OPERATION.instance,
						new L2ReadVectorOperand<>(cast(reads)),
						newPhiWrite);
					final L2Register newPhiRegister = newPhiWrite.register();
					addBinding(semanticValue, newPhiRegister);
					phiMap.put(sourceSynonyms, newPhiRegister);
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
		final Set<L2Synonym> originalSynonyms =
			new HashSet<>(registerToSynonym.values());
		for (final L2Synonym originalSynonym : originalSynonyms)
		{
			final L2Synonym newSynonym =
				originalSynonym.transform(semanticValueTransformer);
			if (newSynonym != originalSynonym)
			{
				for (
					final Iterator<? extends L2Register> iterator =
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