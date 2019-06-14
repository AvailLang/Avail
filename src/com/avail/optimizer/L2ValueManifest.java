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

import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.operand.*;
import com.avail.interpreter.levelTwo.operation.L2_MOVE;
import com.avail.interpreter.levelTwo.operation.L2_PHI_PSEUDO_OPERATION;
import com.avail.interpreter.levelTwo.register.L2Register;
import com.avail.interpreter.levelTwo.register.L2Register.RegisterKind;
import com.avail.optimizer.values.Frame;
import com.avail.optimizer.values.L2SemanticValue;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.IntStream;

import static com.avail.interpreter.levelTwo.register.L2Register.RegisterKind.*;
import static com.avail.utility.Casts.cast;
import static com.avail.utility.Nulls.stripNull;
import static java.util.Collections.singleton;
import static java.util.stream.Collectors.toList;

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
	/** The synonyms keyed by semantic values. */
	private final Map<L2SemanticValue, L2Synonym> semanticValueToSynonym =
		new HashMap<>();

	/**
	 * The {@link TypeRestriction}s currently in force on this manifest's {@link
	 * L2Synonym}s.
	 */
	private final Map<L2Synonym, TypeRestriction> synonymRestrictions =
		new HashMap<>();

	/**
	 * A map from each {@link L2Synonym} to a {@link List} of {@link
	 * L2WriteOperand}s that are operands of instructions that act as
	 * definitions of the synonym's semantic values.
	 */
	private final Map<L2Synonym, List<L2WriteOperand<?>>> definitions =
		new HashMap<>();

	/** Create a new, empty manifest. */
	public L2ValueManifest ()
	{
		// Nothing else to initialize.
	}

	/**
	 * Copy an existing manifest.  Also intersect the {@link TypeRestriction}s
	 * with the information in the given {@link PhiRestriction}s.
	 *
	 * <p>As a notational convenience, if a {@link PhiRestriction} was
	 * constructed with {@link L2ReadOperand#inaccessible()}, the corresponding
	 * semantic value should be *excluded* as invalid.</p>
	 *
	 * @param originalManifest
	 * 	      The original manifest.
	 * @param phiRestrictions
	 *        Additional register type and value restrictions to apply along
	 *        this control flow edge.
	 */
	public L2ValueManifest (
		final L2ValueManifest originalManifest,
		final PhiRestriction... phiRestrictions)
	{
		semanticValueToSynonym.putAll(originalManifest.semanticValueToSynonym);
		synonymRestrictions.putAll(originalManifest.synonymRestrictions);
		originalManifest.definitions.forEach(
			(synonym, list) -> definitions.put(synonym, new ArrayList<>(list)));
		Arrays.stream(phiRestrictions)
			.forEach(
				phiRestriction ->
				{
					if (phiRestriction.typeRestriction == null)
					{
						// Bottom was given explicitly as the phi restriction,
						// which is an indication to remove this register as
						// unassigned along this path.  Note that we should only
						// remove the semantic value, not the whole synonym.
						removeSemanticValue(phiRestriction.semanticValue);
					}
					else
					{
						synonymRestrictions.merge(
							semanticValueToSynonym.get(
								phiRestriction.semanticValue),
							phiRestriction.typeRestriction,
							TypeRestriction::intersection);
					}
				});
	}

	/**
	 * Exclude the given {@link L2SemanticValue} from this manifest.
	 *
	 * @param semanticValue
	 *        The {@link L2SemanticValue} that should no longer be mentioned by
	 *        this manifest.
	 */
	public void removeSemanticValue (
		final L2SemanticValue semanticValue)
	{
		if (!hasSemanticValue(semanticValue))
		{
			// It already isn't mentioned.
			return;
		}
		final L2Synonym oldSynonym = semanticValueToSynonym(semanticValue);
		final TypeRestriction oldRestriction =
			synonymRestrictions.get(oldSynonym);
		final List<L2WriteOperand<?>> oldDefinitions =
			definitions.get(oldSynonym);
		// Erase the old synonym.
		final Set<L2SemanticValue> values =
			new HashSet<>(oldSynonym.semanticValues());
		semanticValueToSynonym.keySet().removeAll(values);
		synonymRestrictions.remove(oldSynonym);
		definitions.remove(oldSynonym);

		values.remove(semanticValue);
		if (values.isEmpty())
		{
			// The synonym was a singleton.
			return;
		}
		final L2Synonym newSynonym = new L2Synonym(values);
		values.forEach(sv -> semanticValueToSynonym.put(sv, newSynonym));
		synonymRestrictions.put(newSynonym, oldRestriction);
		definitions.put(newSynonym, oldDefinitions);
	}

	/**
	 * Answer whether the {@link L2SemanticValue} is known to this manifest.
	 *
	 * @param semanticValue
	 *        The {@link L2SemanticValue}.
	 * @return Whether there is a register known to be holding this value.
	 */
	public boolean hasSemanticValue (
		final L2SemanticValue semanticValue)
	{
		return semanticValueToSynonym.containsKey(semanticValue);
	}

	/**
	 * Look up the given {@link L2SemanticValue}, answering the {@link
	 * L2Synonym} that's bound to it.  Fail if it's not found.
	 *
	 * @param semanticValue
	 *        The semantic value to look up.
	 * @return The {@link L2Synonym} bound to that semantic value.
	 */
	public L2Synonym semanticValueToSynonym (
		final L2SemanticValue semanticValue)
	{
		return stripNull(semanticValueToSynonym.get(semanticValue));
	}

	/**
	 * Capture information about a new {@link L2Synonym} and its {@link
	 * TypeRestriction}.  It's an error if any of the semantic values of the
	 * synonym are already bound to other synonyms in this manifest.
	 *
	 * @param freshSynonym
	 *        The new {@link L2Synonym} to record.
	 * @param restriction
	 *        The {@link TypeRestriction} to constrain the new synonym.
	 */
	public void introduceSynonym (
		final L2Synonym freshSynonym,
		final TypeRestriction restriction)
	{
		for (final L2SemanticValue sv : freshSynonym.semanticValues())
		{
			final @Nullable L2Synonym priorSynonym =
				semanticValueToSynonym.put(sv, freshSynonym);
			assert priorSynonym == null;
		}
		synonymRestrictions.put(freshSynonym, restriction);
		definitions.put(freshSynonym, new ArrayList<>());
	}

	/**
	 * Merge a new {@link L2SemanticValue} into an existing {@link L2Synonym}.
	 * Update the manifest to reflect the merge.
	 *
	 * @param existingSynonym
	 *        An {@link L2Synonym}.
	 * @param semanticValue
	 *        Another {@link L2SemanticValue} representing the same value.
	 * @return The newly merged {@link L2Synonym}.
	 */
	public L2Synonym extendSynonym (
		final L2Synonym existingSynonym,
		final L2SemanticValue semanticValue)
	{
		final Set<L2SemanticValue> semanticValues =
			new HashSet<>(existingSynonym.semanticValues());
		assert !semanticValues.contains(semanticValue);
		assert !hasSemanticValue(semanticValue);
		semanticValues.add(semanticValue);
		final L2Synonym merged = new L2Synonym(semanticValues);
		semanticValues.forEach(sv -> semanticValueToSynonym.put(sv, merged));
		final TypeRestriction restriction =
			synonymRestrictions.remove(existingSynonym);
		synonymRestrictions.put(merged, restriction);
		definitions.put(merged, definitions.remove(existingSynonym));

		// Now update composite L2SemanticValues that used the given synonyms,
		// to now use the merged synonym instead.
		final Map<L2SemanticValue, L2Synonym> mapCopy =
			new HashMap<>(semanticValueToSynonym);
		mapCopy.forEach((sv, synonym) ->
		{
			final L2SemanticValue transformedSemanticValue =
				sv.transformInnerSynonym(existingSynonym, merged);
			if (sv != transformedSemanticValue)
			{
				// Replace the semantic value inside the synonym.
				final Set<L2SemanticValue> oldSemanticValues =
					synonym.semanticValues();
				final Set<L2SemanticValue> newSemanticValues =
					new HashSet<>(oldSemanticValues);
				newSemanticValues.remove(sv);
				newSemanticValues.add(transformedSemanticValue);
				final L2Synonym transformedSynonym =
					new L2Synonym(newSemanticValues);
				// Update the manifest's map from semantic values to synonym.
				oldSemanticValues.forEach(semanticValueToSynonym::remove);
				newSemanticValues.forEach(
					sv2 -> semanticValueToSynonym.put(sv2, transformedSynonym));
				// And update the relevant type restrictions.
				final TypeRestriction oldRestriction =
					synonymRestrictions.remove(synonym);
				synonymRestrictions.put(transformedSynonym, oldRestriction);
				definitions.put(
					transformedSynonym, definitions.remove(synonym));
			}
		});
		return merged;
	}

	/**
	 * Merge two synonyms together.  Update the manifest to reflect the merge.
	 *
	 * @param synonym1 An {@code L2Synonym}.
	 * @param synonym2 Another {@code L2Synonym} representing the same value.
	 * @return The newly merged {@link L2Synonym}.
	 */
	public L2Synonym mergeSynonyms (
		final L2Synonym synonym1,
		final L2Synonym synonym2)
	{
		assert synonym1 != synonym2;
		final Set<L2SemanticValue> semanticValues =
			new HashSet<>(synonym1.semanticValues());
		semanticValues.addAll(synonym2.semanticValues());
		final L2Synonym merged = new L2Synonym(semanticValues);
		semanticValues.forEach(sv -> semanticValueToSynonym.put(sv, merged));
		final TypeRestriction restriction =
			synonymRestrictions.remove(synonym1).intersection(
				synonymRestrictions.remove(synonym2));
		synonymRestrictions.put(merged, restriction);

		// Update the definitions map.  Just concatenate the input synonyms'
		// lists, as this essentially preserves earliest definition order.
		final List<L2WriteOperand<?>> list = new ArrayList<>(
			definitions.remove(synonym1));
		list.addAll(definitions.remove(synonym2));
		definitions.put(merged, list);

		// Now update composite L2SemanticValues that used the given synonyms,
		// to now use the merged synonym instead.
		final Map<L2SemanticValue, L2Synonym> mapCopy =
			new HashMap<>(semanticValueToSynonym);
		mapCopy.forEach((semanticValue, synonym) ->
		{
			final L2SemanticValue transformedSemanticValue =
				semanticValue
					.transformInnerSynonym(synonym1, merged)
					.transformInnerSynonym(synonym2, merged);
			if (semanticValue != transformedSemanticValue)
			{
				// Replace the semantic value inside the synonym.
				final Set<L2SemanticValue> oldSemanticValues =
					synonym.semanticValues();
				final Set<L2SemanticValue> newSemanticValues =
					new HashSet<>(oldSemanticValues);
				newSemanticValues.remove(semanticValue);
				newSemanticValues.add(transformedSemanticValue);
				final L2Synonym transformedSynonym =
					new L2Synonym(newSemanticValues);
				// Update the manifest's map from semantic values to synonym.
				oldSemanticValues.forEach(semanticValueToSynonym::remove);
				newSemanticValues.forEach(
					sv -> semanticValueToSynonym.put(sv, transformedSynonym));
				// And update the relevant type restrictions.
				final TypeRestriction oldRestriction =
					synonymRestrictions.remove(synonym);
				synonymRestrictions.put(transformedSynonym, oldRestriction);
				definitions.put(
					transformedSynonym, definitions.remove(synonym));
			}
		});
		return merged;
	}

	/**
	 * Replace the {@link TypeRestriction} associated with the given {@link
	 * L2Synonym}, which must be a synonym known by this manifest.
	 *
	 * @param synonym
	 *        The given {@link L2Synonym}.
	 * @param restriction
	 *        The {@link TypeRestriction} to bound the synonym.
	 */
	public void setRestriction (
		final L2Synonym synonym,
		final TypeRestriction restriction)
	{
		assert synonymRestrictions.containsKey(synonym);
		synonymRestrictions.put(synonym, restriction);
	}

	/**
	 * Look up the {@link TypeRestriction} that currently bounds this {@link
	 * L2SemanticValue}.  Fail if there is none.
	 *
	 * @param semanticValue
	 *        The given {@link L2SemanticValue}.
	 * @return The {@link TypeRestriction} that bounds the synonym.
	 */
	public TypeRestriction restrictionFor (
		final L2SemanticValue semanticValue)
	{
		return restrictionFor(semanticValueToSynonym(semanticValue));
	}

	/**
	 * Look up the {@link TypeRestriction} that currently bounds this {@link
	 * L2Synonym}.  Fail if there is none.
	 *
	 * @param synonym
	 *        The given {@link L2Synonym}.
	 * @return The {@link TypeRestriction} that bounds the synonym.
	 */
	public TypeRestriction restrictionFor (
		final L2Synonym synonym)
	{
		return stripNull(synonymRestrictions.get(synonym));
	}

	/**
	 * Answer a copy of the set of {@link L2Synonym}s in this manifest.
	 *
	 * @return The indicated {@link Set}.
	 */
	public Set<L2Synonym> synonyms ()
	{
		return new HashSet<>(semanticValueToSynonym.values());
	}

	/**
	 * Remove all information about registers and semantic values from this
	 * manifest.
	 */
	public void clear ()
	{
		semanticValueToSynonym.clear();
		synonymRestrictions.clear();
		definitions.clear();
	}

	/**
	 * Record the fact that an {@link L2Instruction} has been emitted, which
	 * writes to the given {@link L2WriteOperand}.
	 *
	 * @param writer
	 *        The operand that received the value.
	 */
	public void recordDefinition (
		final L2WriteOperand<?> writer)
	{
		assert writer.instructionHasBeenEmitted();

		final L2SemanticValue semanticValue = writer.semanticValue();
		final L2Synonym synonym;
		if (hasSemanticValue(semanticValue))
		{
			synonym = semanticValueToSynonym(writer.semanticValue());
		}
		else
		{
			synonym = new L2Synonym(singleton(semanticValue));
			introduceSynonym(synonym, writer.restriction());
		}
		final List<L2WriteOperand<?>> list = definitions.computeIfAbsent(
			synonym, s -> new ArrayList<>());
		assert !list.contains(writer);
		list.add(writer);
	}

	/**
	 * Erase the information about all {@link L2SemanticValue}s that are part of
	 * the given {@link L2Synonym}.
	 *
	 * @param synonym
	 *        The {@link L2Synonym} to forget.
	 */
	public void forget (final L2Synonym synonym)
	{
		assert synonymRestrictions.containsKey(synonym);

		semanticValueToSynonym.keySet().removeAll(synonym.semanticValues());
		synonymRestrictions.remove(synonym);
		definitions.remove(synonym);
	}

	/**
	 * Given a synonym and a {@link RegisterKind}, answer a {@link
	 * L2SemanticValue} from within the synonym, such that it was given a value
	 * of the appropriate register kind as early as possible.
	 *
	 * @param synonym
	 *        The {@link L2Synonym} to read from.
	 * @param registerKind
	 *        The kind of register that should supply the value.
	 * @param <RR>
	 *        The {@link L2WriteOperand} type, which should agree with the
	 *        #registerKind.
	 * @param <R>
	 *        The {@link L2Register} type of the write.
	 * @return The earliest known {@link L2WriteOperand} of the appropriate
	 *         {@link RegisterKind}, for any {@link L2SemanticValue} of the
	 *         given {@link L2Synonym}.
	 */
	public <
		RR extends L2WriteOperand<R>,
		R extends L2Register>
	RR getDefinition (
		final L2Synonym synonym,
		final RegisterKind registerKind)
	{
		final List<L2WriteOperand<?>> list = definitions.get(synonym);
		for (final L2WriteOperand<?> writer : list)
		{
			if (writer.registerKind() == registerKind)
			{
				return cast(writer);
			}
		}
		throw new RuntimeException(
			"No write was found for the given register kind in a synonym");
	}

	/**
	 * Create an {@link L2ReadBoxedOperand} for the {@link L2SemanticValue} of
	 * the earliest known boxed write for any semantic values in the same {@link
	 * L2Synonym} as the given semantic value..
	 *
	 * @param semanticValue
	 *        The {@link L2SemanticValue} to read as a boxed value.
	 * @return An {@link L2ReadBoxedOperand} that reads the value.
	 */
	public L2ReadBoxedOperand readBoxed (final L2SemanticValue semanticValue)
	{
		return readBoxed(semanticValueToSynonym(semanticValue));
	}

	/**
	 * Create an {@link L2ReadIntOperand} for the {@link L2SemanticValue} of
	 * the earliest known unboxed int write for any semantic values in the
	 * same {@link L2Synonym} as the given semantic value..
	 *
	 * @param semanticValue
	 *        The {@link L2SemanticValue} to read as an unboxed int value.
	 * @return An {@link L2ReadIntOperand} that reads the value.
	 */
	public L2ReadIntOperand readInt (final L2SemanticValue semanticValue)
	{
		return readInt(semanticValueToSynonym(semanticValue));
	}

	/**
	 * Create an {@link L2ReadFloatOperand} for the {@link L2SemanticValue} of
	 * the earliest known unboxed float write for any semantic values in the
	 * same {@link L2Synonym} as the given semantic value..
	 *
	 * @param semanticValue
	 *        The {@link L2SemanticValue} to read as an unboxed int value.
	 * @return An {@link L2ReadBoxedOperand} that reads from the synonym.
	 */
	public L2ReadFloatOperand readFloat (final L2SemanticValue semanticValue)
	{
		return readFloat(semanticValueToSynonym(semanticValue));
	}

	/**
	 * Create an {@link L2ReadBoxedOperand} for the given {@link L2Synonym}.
	 *
	 * @param synonym
	 *        The {@link L2Synonym} to read as a boxed value.
	 * @return An {@link L2ReadBoxedOperand} that reads the value.
	 */
	public L2ReadBoxedOperand readBoxed (final L2Synonym synonym)
	{
		final TypeRestriction restriction = restrictionFor(synonym);
		assert restriction.isBoxed();
		final L2WriteOperand<?> definition = getDefinition(synonym, BOXED);
		assert definition.instructionHasBeenEmitted();
		return new L2ReadBoxedOperand(
			definition.semanticValue(), restriction, this);
	}

	/**
	 * Create an {@link L2ReadIntOperand} for the {@link L2SemanticValue} of
	 * the earliest known unboxed int write for any semantic values in the
	 * same {@link L2Synonym} as the given semantic value..
	 *
	 * @param synonym
	 *        The {@link L2Synonym} to read as an unboxed int value.
	 * @return An {@link L2ReadIntOperand} that reads the value.
	 */
	public L2ReadIntOperand readInt (final L2Synonym synonym)
	{
		final TypeRestriction restriction = restrictionFor(synonym);
		assert restriction.isUnboxedInt();
		final L2WriteOperand<?> definition = getDefinition(synonym, INTEGER);
		return new L2ReadIntOperand(
			definition.semanticValue(), restriction, this);
	}

	/**
	 * Create an {@link L2ReadFloatOperand} for the {@link L2SemanticValue} of
	 * the earliest known unboxed float write for any semantic values in the
	 * same {@link L2Synonym} as the given semantic value..
	 *
	 * @param synonym
	 *        The {@link L2Synonym} to read as an unboxed float value.
	 * @return An {@link L2ReadFloatOperand} that reads the value.
	 */
	public L2ReadFloatOperand readFloat (final L2Synonym synonym)
	{
		final TypeRestriction restriction = restrictionFor(synonym);
		assert restriction.isUnboxedFloat();
		final L2WriteOperand<?> definition = getDefinition(synonym, FLOAT);
		return new L2ReadFloatOperand(
			definition.semanticValue(), restriction, this);
	}

	/**
	 * Given an {@link L2SemanticValue}, produce an {@link L2ReadBoxedOperand}
	 * of the same value, but with the current manifest's {@link
	 * TypeRestriction} applied.
	 *
	 * @param semanticValue
	 *        The {@link L2SemanticValue} for which to generate a read.
	 * @return The {@link L2ReadBoxedOperand} that reads the value.
	 */
	public L2ReadBoxedOperand read (
		final L2SemanticValue semanticValue)
	{
		return new L2ReadBoxedOperand(
			semanticValue,
			restrictionFor(semanticValueToSynonym(semanticValue)),
			this);
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
		assert semanticValueToSynonym.isEmpty();
		assert synonymRestrictions.isEmpty();
		assert definitions.isEmpty();
		final int manifestsSize = manifests.size();
		if (manifestsSize == 0)
		{
			// Unreachable, or an entry point where no registers are set.
			return;
		}
		if (manifestsSize == 1)
		{
			final L2ValueManifest soleManifest = manifests.get(0);
			semanticValueToSynonym.putAll(soleManifest.semanticValueToSynonym);
			synonymRestrictions.putAll(soleManifest.synonymRestrictions);
			soleManifest.definitions.forEach(
				(syn, list) -> definitions.put(syn, new ArrayList<>(list)));
			return;
		}
		final List<L2ValueManifest> otherManifests = new ArrayList<>(manifests);
		final L2ValueManifest firstManifest = otherManifests.remove(0);
		// For each semantic value, figure out the list of synonyms in which it
		// occurs for each input manifest.  Build a map from the lists of input
		// synonyms to the set of semantic values that are represented by those
		// input synonyms.  That defines which phi instructions will be needed,
		// although we can eliminate some later when we know if there's an
		// actual register that's common to all incoming edges.
		final Set<L2SemanticValue> liveSemanticValues = new HashSet<>(
			firstManifest.semanticValueToSynonym.keySet());
		otherManifests.forEach(
			m -> liveSemanticValues.retainAll(
				m.semanticValueToSynonym.keySet()));
		final Map<List<L2Synonym>, Set<L2SemanticValue>> phiMap =
			new HashMap<>();
		liveSemanticValues.forEach(
			sv -> phiMap
				.computeIfAbsent(
					manifests.stream()
						.map(m -> m.semanticValueToSynonym(sv))
						.collect(toList()),
					k -> new HashSet<>())
				.add(sv));
		// The phiMap is now populated, but we still need to figure out the
		// appropriate TypeRestrictions, including the available register types.
		phiMap.forEach(
			(synList, relatedSemanticValues) ->
			{
				final TypeRestriction restriction =
					IntStream.range(0, manifestsSize)
						.mapToObj(
							i -> manifests.get(i).restrictionFor(
								synList.get(i)))
						.reduce(TypeRestriction::union)
						.orElseThrow(
							() -> new RuntimeException(
								"Impossible: no manifests"));
				// Implicitly discard it if there were no common register kinds
				// between all the inputs.
				if (restriction.isBoxed())
				{
					// Generate a boxed phi.
					final List<L2ReadBoxedOperand> sources =
						IntStream.range(0, manifestsSize)
							.mapToObj(
								i -> manifests.get(i).readBoxed(synList.get(i)))
							.collect(toList());
					generatePhi(
						generator,
						relatedSemanticValues,
						restriction,
						new L2ReadBoxedVectorOperand(sources),
						L2_PHI_PSEUDO_OPERATION.boxed,
						L2_MOVE.boxed,
						this::readBoxed,
						sv -> generator.boxedWrite(sv, restriction));
				}
				if (restriction.isUnboxedInt())
				{
					// Generate an unboxed int phi.
					final List<L2ReadIntOperand> sources =
						IntStream.range(0, manifestsSize)
							.mapToObj(
								i -> manifests.get(i).readInt(synList.get(i)))
							.collect(toList());
					generatePhi(
						generator,
						relatedSemanticValues,
						restriction,
						new L2ReadIntVectorOperand(sources),
						L2_PHI_PSEUDO_OPERATION.unboxedInt,
						L2_MOVE.unboxedInt,
						this::readInt,
						sv -> generator.intWrite(sv, restriction));
				}
				if (restriction.isUnboxedFloat())
				{
					// Generate an unboxed float phi.
					final List<L2ReadFloatOperand> sources =
						IntStream.range(0, manifestsSize)
							.mapToObj(
								i -> manifests.get(i).readFloat(synList.get(i)))
							.collect(toList());
					generatePhi(
						generator,
						relatedSemanticValues,
						restriction,
						new L2ReadFloatVectorOperand(sources),
						L2_PHI_PSEUDO_OPERATION.unboxedFloat,
						L2_MOVE.unboxedFloat,
						this::readFloat,
						sv -> generator.floatWrite(sv, restriction));
				}
			});
	}

	/**
	 * Generate an {@link L2_PHI_PSEUDO_OPERATION} and any additional moves to
	 * ensure the given set of related {@link L2SemanticValue}s are populated
	 * with values from the given sources.
	 *
	 * @param generator
	 *        The {@link L2Generator} on which to write instructions.
	 * @param relatedSemanticValues
	 *        The {@link L2SemanticValue}s that should constitute a synonym in
	 *        the current manifest, due to them being mutually connected to a
	 *        synonym in each predecessor manifest.  The synonyms may differ in
	 *        the predecessor manifests, but within each manifest there must be
	 *        a synonym for that manifest that contains all of these semantic
	 *        values.
	 * @param restriction
	 *        The {@link TypeRestriction} to bound the synonym.
	 * @param sources
	 *        An {@link L2ReadVectorOperand} that reads from each
	 * @param phiOperation
	 *        The {@link L2_PHI_PSEUDO_OPERATION} instruction to generate.
	 * @param moveOperation
	 *        Th2 {@link L2_MOVE} instructions to also generate as needed.
	 * @param <RR>
	 *        The {@link L2ReadOperand} type supplying values.
	 * @param <R>
	 *        The kind of {@link L2Register}s to merge.
	 */
	private <
		RR extends L2ReadOperand<R>,
		R extends L2Register>
	void generatePhi (
		final L2Generator generator,
		final Set<L2SemanticValue> relatedSemanticValues,
		final TypeRestriction restriction,
		final L2ReadVectorOperand<RR, R> sources,
		final L2_PHI_PSEUDO_OPERATION<R> phiOperation,
		final L2_MOVE<R> moveOperation,
		final Function<L2SemanticValue, RR> createReader,
		final Function<L2SemanticValue, L2WriteOperand<R>> createWriter)
	{
		final L2SemanticValue firstSemanticValue;
		final List<L2WriteOperand<R>> distinctDefs = sources.elements().stream()
			.map(L2ReadOperand::definition)
			.distinct()
			.collect(toList());
		if (distinctDefs.size() == 1
			&& relatedSemanticValues.contains(
				distinctDefs.get(0).semanticValue()))
		{
			// All paths get the value from a common definition, and that
			// definition is for one of the relatedSemanticValues.
			final L2WriteOperand<R> onlySource = distinctDefs.iterator().next();
			firstSemanticValue = onlySource.semanticValue();
			if (semanticValueToSynonym.containsKey(firstSemanticValue))
			{
				// Already present due to another RegisterKind.  Just make sure
				// the common register shows up as a definition.
				recordDefinition(onlySource);
				return;
			}
			// This is the first RegisterKind for this collection of related
			// semantic values.
			introduceSynonym(
				new L2Synonym(singleton(firstSemanticValue)), restriction);
			final List<L2WriteOperand<?>> list = definitions.computeIfAbsent(
				semanticValueToSynonym(firstSemanticValue),
				syn -> new ArrayList<>());
			list.add(onlySource);
		}
		else
		{
			firstSemanticValue = relatedSemanticValues.iterator().next();
			generator.addInstruction(
				phiOperation,
				sources,
				createWriter.apply(firstSemanticValue));
		}
		final List<L2SemanticValue> otherSemanticValues =
			new ArrayList<>(relatedSemanticValues);
		otherSemanticValues.remove(firstSemanticValue);
		otherSemanticValues.forEach(
			otherSemanticValue -> generator.moveRegister(
				moveOperation,
				createReader.apply(firstSemanticValue),
				createWriter.apply(otherSemanticValue)));
	}

	/**
	 * Transform this manifest by mapping its {@link L2SemanticValue}s and
	 * {@link Frame}s.
	 *
	 * @param semanticValueTransformer
	 *        The transformation for {@link L2SemanticValue}s.
	 * @param frameTransformer
	 *        The transformation for {@link Frame}s.
	 * @return The transformed manifest.
	 */
	public L2ValueManifest transform (
		final UnaryOperator<L2SemanticValue> semanticValueTransformer,
		final UnaryOperator<Frame> frameTransformer)
	{
		final L2ValueManifest newManifest = new L2ValueManifest();
		synonyms().forEach(
			oldSynonym -> newManifest.introduceSynonym(
				oldSynonym.transform(semanticValueTransformer),
				restrictionFor(oldSynonym)));
		return newManifest;
	}
}