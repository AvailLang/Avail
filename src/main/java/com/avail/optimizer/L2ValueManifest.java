/*
 * L2ValueManifest.java
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
package com.avail.optimizer;

import com.avail.descriptor.A_Type;
import com.avail.interpreter.Primitive;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2Operation;
import com.avail.interpreter.levelTwo.operand.*;
import com.avail.interpreter.levelTwo.operand.TypeRestriction.RestrictionFlagEncoding;
import com.avail.interpreter.levelTwo.operation.L2_PHI_PSEUDO_OPERATION;
import com.avail.interpreter.levelTwo.register.L2Register;
import com.avail.interpreter.levelTwo.register.L2Register.RegisterKind;
import com.avail.optimizer.values.Frame;
import com.avail.optimizer.values.L2SemanticPrimitiveInvocation;
import com.avail.optimizer.values.L2SemanticValue;
import com.avail.utility.Casts;
import com.avail.utility.Pair;

import javax.annotation.Nullable;
import java.util.*;
import java.util.Map.Entry;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import static com.avail.interpreter.levelTwo.operand.TypeRestriction.bottomRestriction;
import static com.avail.interpreter.levelTwo.register.L2Register.RegisterKind.*;
import static com.avail.utility.Casts.cast;
import static com.avail.utility.Nulls.stripNull;
import static java.util.Collections.singleton;
import static java.util.stream.Collectors.toList;

/**
 * The {@code L2ValueManifest} maintains information about which {@link
 * L2SemanticValue}s hold equivalent values at this point, the {@link
 * TypeRestriction}s for those semantic values, and the list of {@link
 * L2WriteOperand}s that are visible definitions of those values.
 *
 * <p>In order to avoid reevaluating primitives with the same values, a manifest
 * also tracks </p>
 * {@link
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
	 * Copy an existing manifest.
	 *
	 * @param originalManifest
	 * 	      The original manifest.
	 */
	public L2ValueManifest (
		final L2ValueManifest originalManifest)
	{
		semanticValueToSynonym.putAll(originalManifest.semanticValueToSynonym);
		synonymRestrictions.putAll(originalManifest.synonymRestrictions);
		originalManifest.definitions.forEach(
			(synonym, list) -> definitions.put(synonym, new ArrayList<>(list)));
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
	 * Given an {@link L2SemanticValue}, see if there's already an equivalent
	 * one in this manifest.  If an {@link L2SemanticPrimitiveInvocation} is
	 * supplied, look for a recursively synonymous one.
	 *
	 * <p>Answer the extant {@link L2SemanticValue} if found, otherwise answer
	 * {@code null}.  Note that there may be multiple {@link
	 * L2SemanticPrimitiveInvocation}s that are equivalent, in which case an
	 * arbitrary (and not necessarily stable) one is chosen.</p>
	 *
	 * @param semanticValue
	 *        The {@link L2SemanticValue} to look up.
	 * @return An {@link L2SemanticValue} from this manifest which is equivalent
	 *         to the given one, or {@code null} if no such value is in the
	 *         manifest.
	 */
	public @Nullable L2SemanticValue equivalentSemanticValue (
		final L2SemanticValue semanticValue)
	{
		if (semanticValueToSynonym.containsKey(semanticValue))
		{
			// It already exists in exactly the form given.
			return semanticValue;
		}
		if (!(semanticValue instanceof L2SemanticPrimitiveInvocation))
		{
			// It's not present and it's not a primitive.
			return null;
		}
		final L2SemanticPrimitiveInvocation semanticPrimitive =
			cast(semanticValue);
		final List<L2SemanticValue> newArgs =
			semanticPrimitive.argumentSemanticValues;
		final int numArgs = newArgs.size();
		nextEntry: for (final Entry<L2SemanticValue, L2Synonym> entry
			: semanticValueToSynonym.entrySet())
		{
			final L2SemanticValue sv = entry.getKey();
			if (!(sv instanceof L2SemanticPrimitiveInvocation))
			{
				continue;
			}
			final L2SemanticPrimitiveInvocation existingPrimitive = cast(sv);
			if (existingPrimitive.primitive != semanticPrimitive.primitive)
			{
				continue;
			}
			// The actual primitives match here.
			final List<L2SemanticValue> existingArgs =
				existingPrimitive.argumentSemanticValues;
			assert existingArgs.size() == numArgs;
			for (int i = 0; i < numArgs; i++)
			{
				final L2SemanticValue newArg = newArgs.get(i);
				final L2SemanticValue existingArg = existingArgs.get(i);
				if (newArg.equals(existingArg))
				{
					// The corresponding arguments at this position are equal.
					continue;
				}
				// Check if the arguments are still visible and match.
				if (!hasSemanticValue(newArg) || !hasSemanticValue(existingArg))
				{
					// One of the arguments is no longer extant in the manifest,
					// and also wasn't equal to its counterpart.
					continue nextEntry;
				}
				final L2Synonym existingSynonym =
					semanticValueToSynonym(existingArg);
				if (semanticValueToSynonym(newArg).equals(existingSynonym))
				{
					// They're known to be synonymous.
					continue;
				}
				if (!(newArg instanceof L2SemanticPrimitiveInvocation))
				{
					// They're not synonyms, and the new arg isn't primitive.
					continue nextEntry;
				}
				final @Nullable L2SemanticValue newArgEquivalent =
					equivalentSemanticValue(newArg);
				if (newArgEquivalent == null)
				{
					// No equivalent was found in the manifest.
					continue nextEntry;
				}
				// An equivalent was found, so check if the equivalent is
				// synonymous with the corresponding argument.
				if (!semanticValueToSynonym(newArgEquivalent).equals(
					existingSynonym))
				{
					continue nextEntry;
				}
			}
			// The arguments of sv matched.
			return sv;
		}
		// No extant semantic values matched.
		return null;
	}

	/**
	 * Merge a new {@link L2SemanticValue} into an existing {@link L2Synonym}.
	 * Update the manifest to reflect the merge.
	 *
	 * <p>Note that because the {@link L2SemanticValue} is new, we don't have to
	 * check for existing {@link L2SemanticPrimitiveInvocation}s becoming
	 * synonyms of each other, which is much faster than the general case in
	 * {@link #mergeExistingSemanticValues(L2SemanticValue,
	 * L2SemanticValue)}.</p>
	 *
	 * @param existingSynonym
	 *        An {@link L2Synonym}.
	 * @param semanticValue
	 *        Another {@link L2SemanticValue} representing the same value.
	 */
	public void extendSynonym (
		final L2Synonym existingSynonym,
		final L2SemanticValue semanticValue)
	{
		final Set<L2SemanticValue> semanticValues =
			new HashSet<>(existingSynonym.semanticValues());
		assert !hasSemanticValue(semanticValue);
		semanticValues.add(semanticValue);
		final L2Synonym merged = new L2Synonym(semanticValues);
		semanticValues.forEach(sv -> semanticValueToSynonym.put(sv, merged));
		synonymRestrictions.put(
			merged, synonymRestrictions.remove(existingSynonym));
		definitions.put(merged, definitions.remove(existingSynonym));
	}

	/**
	 * Given two {@link L2SemanticValue}s, merge their {@link L2Synonym}s
	 * together, if they're not already.  Update the manifest to reflect the
	 * merged synonyms.
	 *
	 * @param semanticValue1
	 *        An {@link L2SemanticValue}.
	 * @param semanticValue2
	 *        Another {@link L2SemanticValue} representing what has just been
	 *        shown to be the same value.  It may already be a synonym of the
	 *        first semantic value.
	 */
	public void mergeExistingSemanticValues (
		final L2SemanticValue semanticValue1,
		final L2SemanticValue semanticValue2)
	{
		final L2Synonym synonym1 = semanticValueToSynonym(semanticValue1);
		final L2Synonym synonym2 = semanticValueToSynonym(semanticValue2);
		if (!privateMergeSynonyms(synonym1, synonym2))
		{
			return;
		}

		// Figure out which L2SemanticPrimitiveInvocations have become
		// equivalent due to their arguments being merged into the same
		// synonyms.  Repeat as necessary, alternating collection of newly
		// matched pairs of synonyms with merging them.
		final Map<Primitive, List<L2SemanticPrimitiveInvocation>>
			allSemanticPrimitives = new HashMap<>();
		semanticValueToSynonym.keySet().forEach(
			sv -> {
				if (sv instanceof L2SemanticPrimitiveInvocation)
				{
					final L2SemanticPrimitiveInvocation invocation = cast(sv);
					final List<L2SemanticPrimitiveInvocation> list =
						allSemanticPrimitives.computeIfAbsent(
							invocation.primitive,
							p -> new ArrayList<>());
					list.add(invocation);
				}
			});
		if (allSemanticPrimitives.size() == 0)
		{
			// There are no primitive invocations visible.
			return;
		}
		while (true)
		{
			final List<Pair<L2SemanticValue, L2SemanticValue>> followupMerges =
				new ArrayList<>();
			allSemanticPrimitives.values().forEach(
				invocations ->
				{
					// It takes at least two primitive invocations (of the
					// same primitive) for there to be a potential merge.
					if (invocations.size() > 1)
					{
						// Create a map from each distinct input list of
						// synonyms to the set of invocation synonyms.
						final Map<List<L2Synonym>, Set<L2Synonym>> map =
							new HashMap<>();
						invocations.forEach(invocation ->
						{
							final List<L2Synonym> argumentSynonyms =
								invocation.argumentSemanticValues.stream()
									.map(this::semanticValueToSynonym)
									.collect(toList());
							final Set<L2Synonym> primitiveSynonyms =
								map.computeIfAbsent(
									argumentSynonyms, p -> new HashSet<>());
							final L2Synonym invocationSynonym =
								semanticValueToSynonym.get(invocation);
							if (!primitiveSynonyms.isEmpty()
								&& !primitiveSynonyms.contains(
								invocationSynonym))
							{
								final L2Synonym sampleSynonym =
									primitiveSynonyms.iterator().next();
								final L2SemanticValue sampleInvocation =
									sampleSynonym.pickSemanticValue();
								followupMerges.add(
									new Pair<>(
										invocation,
										sampleInvocation));
							}
							primitiveSynonyms.add(invocationSynonym);
						});
					}
				});
			if (followupMerges.isEmpty())
			{
				break;
			}
			followupMerges.forEach(
				pair -> privateMergeSynonyms(
					semanticValueToSynonym(pair.first()),
					semanticValueToSynonym(pair.second())));
		}
	}

	/**
	 * Given two {@link L2SemanticValue}s, merge their {@link L2Synonym}s
	 * together, if they're not already.  Update the manifest to reflect the
	 * merged synonyms.  Do not yet merge synonyms of {@link
	 * L2SemanticPrimitiveInvocation}s whose arguments have just become
	 * equivalent.
	 *
	 * @param synonym1
	 *        An {@link L2Synonym}.
	 * @param synonym2
	 *        Another {@link L2Synonym} representing what has just been shown to
	 *        be the same value.  It may already be equal to the first synonym.
	 * @return Whether any change was made to the manifest.
	 */
	private boolean privateMergeSynonyms (
		final L2Synonym synonym1,
		final L2Synonym synonym2)
	{
		if (synonym1 == synonym2)
		{
			return false;
		}
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
		return true;
	}

	/**
	 * Retrieve the oldest definition of the given {@link L2SemanticValue} or an
	 * equivalent, but having the given {@link RegisterKind}.
	 *
	 * @param semanticValue
	 *        The {@link L2SemanticValue} being examined.
	 * @param registerKind
	 *        The {@link RegisterKind} of the desired register.
	 * @param <RW>
	 *        The kind of {@link L2WriteOperand} to look for.
	 * @param <R>
	 *        The kind of {@link L2Register} within the write operand.
	 * @return The requested {@link L2WriteOperand}.
	 */
	public <
		RW extends L2WriteOperand<R>,
		R extends L2Register>
	RW getDefinition (
		final L2SemanticValue semanticValue,
		final RegisterKind registerKind)
	{
		return definitions.get(semanticValueToSynonym(semanticValue)).stream()
			.filter(writer -> writer.registerKind() == registerKind)
			.findFirst()
			.<RW>map(Casts::cast)
			.orElseThrow(
				() -> new AssertionError(
					"Appropriate register for kind not found"));
	}

	/**
	 * Replace the {@link TypeRestriction} associated with the given {@link
	 * L2SemanticValue}, which must be known by this manifest.  Note that this
	 * also restricts any synonymous semantic values.
	 *
	 * @param semanticValue
	 *        The given {@link L2SemanticValue}.
	 * @param restriction
	 *        The {@link TypeRestriction} to bound the synonym.
	 */
	public void setRestriction (
		final L2SemanticValue semanticValue,
		final TypeRestriction restriction)
	{
		synonymRestrictions.put(
			semanticValueToSynonym(semanticValue), restriction);
	}

	/**
	 * Replace the {@link TypeRestriction} associated with the given {@link
	 * L2SemanticValue}, which must be known by this manifest, with the
	 * intersection of its current restriction and the given restriction.  Note
	 * that this also restricts any synonymous semantic values.
	 *
	 * @param semanticValue
	 *        The given {@link L2SemanticValue}.
	 * @param type
	 *        The {@link A_Type} to intersect with the synonym.
	 */
	public void intersectType (
		final L2SemanticValue semanticValue,
		final A_Type type)
	{
		final L2Synonym synonym = semanticValueToSynonym(semanticValue);
		synonymRestrictions.put(
			synonym,
			synonymRestrictions.get(synonym).intersectionWithType(type));
	}

	/**
	 * Replace the {@link TypeRestriction} associated with the given {@link
	 * L2SemanticValue}, which must be known by this manifest, with the
	 * difference between its current restriction and the given restriction.
	 * Note that this also restricts any synonymous semantic values.
	 *
	 * @param semanticValue
	 *        The given {@link L2SemanticValue}.
	 * @param type
	 *        The {@link A_Type} to exclude from the synonym's restriction.
	 */
	public void subtractType (
		final L2SemanticValue semanticValue,
		final A_Type type)
	{
		final L2Synonym synonym = semanticValueToSynonym(semanticValue);
		synonymRestrictions.put(
			synonym,
			synonymRestrictions.get(synonym).minusType(type));
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
		return synonymRestrictions.get(semanticValueToSynonym(semanticValue));
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
	 * writes to the given {@link L2WriteOperand}.  Since this is the
	 * introduction of a new {@link L2SemanticValue}, it must not yet be in this
	 * manifest.
	 *
	 * <p>{@link L2Operation}s that move values between semantic values should
	 * customize their {@link L2Operation#instructionWasAdded(L2Instruction,
	 * L2ValueManifest)} method to use {@link
	 * #recordDefinitionForMove(L2WriteOperand, L2SemanticValue)}.</p>
	 *
	 * @param writer
	 *        The operand that received the value.
	 */
	public void recordDefinition (
		final L2WriteOperand<?> writer)
	{
		assert writer.instructionHasBeenEmitted();

		final L2SemanticValue semanticValue = writer.semanticValue();
		if (!hasSemanticValue(semanticValue))
		{
			// This is a new semantic value.
			final L2Synonym synonym = new L2Synonym(singleton(semanticValue));
			semanticValueToSynonym.put(semanticValue, synonym);
			synonymRestrictions.put(synonym, writer.restriction());
			definitions.put(synonym, new ArrayList<>(singleton(writer)));
		}
		else
		{
			// This is a new RegisterKind for an existing semantic value.
			final L2Synonym synonym = semanticValueToSynonym(semanticValue);
			final TypeRestriction existingRestriction =
				restrictionFor(semanticValue);
			final RestrictionFlagEncoding writerRestrictionFlag =
				writer.registerKind().restrictionFlag;
			// The restriction *might* know about this kind, if there were
			// multiple kinds that led to multiple phi instructions for the same
			// synonym.
			synonymRestrictions.put(
				synonym, existingRestriction.withFlag(writerRestrictionFlag));
			definitions.get(synonym).add(writer);
		}
	}

	/**
	 * Record the fact that an {@link L2Instruction} has been emitted, which
	 * writes to the given {@link L2WriteOperand}.
	 *
	 * @param writer
	 *        The operand that received the value.
	 * @param sourceSemanticValue
	 *        The {@link L2SemanticValue} that already holds the value.
	 */
	public void recordDefinitionForMove (
		final L2WriteOperand<?> writer,
		final L2SemanticValue sourceSemanticValue)
	{
		assert writer.instructionHasBeenEmitted();

		final L2SemanticValue semanticValue = writer.semanticValue();
		if (semanticValue.equals(sourceSemanticValue))
		{
			// Introduce a definition of a new kind for a semantic value that
			// already has a value of a different kind.
			assert hasSemanticValue(semanticValue);
			setRestriction(
				semanticValue,
				restrictionFor(sourceSemanticValue)
					.intersection(writer.restriction()));
		}
		else if (!hasSemanticValue(semanticValue))
		{
			// Introduce the newly written semantic value, synonymous to the
			// given one.
			final L2Synonym oldSynonym =
				semanticValueToSynonym(sourceSemanticValue);
			extendSynonym(oldSynonym, semanticValue);
			setRestriction(
				semanticValue,
				restrictionFor(sourceSemanticValue)
					.intersection(writer.restriction()));
		}
		else
		{
			// The write to an existing semantic value must be a consequence of
			// post-phi duplication into registers for synonymous semantic
			// values, but where there were multiple kinds leading to multiple
			// phis for the same target synonym.
			final L2Synonym oldSynonym =
				semanticValueToSynonym(sourceSemanticValue);
			assert definitions.get(oldSynonym).stream().anyMatch(
				instr -> instr.semanticValue() == semanticValue);
		}
		definitions.get(semanticValueToSynonym(semanticValue)).add(writer);
	}

	/**
	 * Record the fact that an {@link L2Instruction} has been emitted, which
	 * writes to the given {@link L2WriteOperand}.  The write is for a {@link
	 * RegisterKind} which has not been written yet for this {@link
	 * L2SemanticValue}, although there are definitions for other kinds for the
	 * semantic value..
	 *
	 * @param writer
	 *        The operand that received the value.
	 */
	public void recordDefinitionForNewKind (
		final L2WriteOperand<?> writer)
	{
		assert writer.instructionHasBeenEmitted();

		final L2SemanticValue semanticValue = writer.semanticValue();
		assert hasSemanticValue(semanticValue);
		final L2Synonym oldSynonym = semanticValueToSynonym(semanticValue);
		definitions.get(oldSynonym).add(writer);
		setRestriction(
			semanticValue,
			restrictionFor(semanticValue).intersection(writer.restriction()));
	}

	/**
	 * Record the fact that an {@link L2Instruction} that writes to this {@link
	 * L2WriteOperand} has been inserted as part of an optimization pass.  Since
	 * this is an insertion, the {@link L2SemanticValue} may or may not be in
	 * this manifest already.
	 *
	 * @param writer
	 *        The operand that received the value.
	 */
	public void recordDefinitionForInsertion (
		final L2WriteOperand<?> writer)
	{
		assert writer.instructionHasBeenEmitted();

		final L2SemanticValue semanticValue = writer.semanticValue();
		if (hasSemanticValue(semanticValue))
		{
			definitions.get(semanticValueToSynonym(semanticValue)).add(writer);
		}
		else
		{
			final L2Synonym synonym = new L2Synonym(singleton(semanticValue));
			semanticValueToSynonym.put(semanticValue, synonym);
			synonymRestrictions.put(synonym, writer.restriction());
			definitions.put(synonym, new ArrayList<>(singleton(writer)));
		}
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
		final TypeRestriction restriction = restrictionFor(semanticValue);
		assert restriction.isBoxed();
		final L2WriteBoxedOperand definition =
			getDefinition(semanticValue, BOXED);
		assert definition.instructionHasBeenEmitted();
		return new L2ReadBoxedOperand(
			definition.semanticValue(), restriction, this);
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
		final TypeRestriction restriction = restrictionFor(semanticValue);
		assert restriction.isUnboxedInt();
		final L2WriteIntOperand definition =
			getDefinition(semanticValue, INTEGER);
		return new L2ReadIntOperand(
			definition.semanticValue(), restriction, this);
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
		final TypeRestriction restriction = restrictionFor(semanticValue);
		assert restriction.isUnboxedFloat();
		final L2WriteFloatOperand definition =
			getDefinition(semanticValue, FLOAT);
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
			semanticValue, restrictionFor(semanticValue), this);
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
		// Find the semantic values which are present in all manifests.  Build
		// phi instructions to move from the old definitions in each input edge
		// to a new definition (write) within the phi instruction.  We expect to
		// eliminate most of these by collapsing moves during register coloring.
		final Set<L2SemanticValue> liveSemanticValues = new HashSet<>(
			firstManifest.semanticValueToSynonym.keySet());
		otherManifests.forEach(
			m -> liveSemanticValues.retainAll(
				m.semanticValueToSynonym.keySet()));
		final Map<List<L2Synonym>, List<L2SemanticValue>> phiMap =
			new HashMap<>();
		liveSemanticValues.forEach(
			sv -> phiMap
				.computeIfAbsent(
					manifests.stream()
						.map(m -> m.semanticValueToSynonym(sv))
						.collect(toList()),
					k -> new ArrayList<>())
				.add(sv));
		// The phiMap is now populated, but we still need to figure out the
		// appropriate TypeRestrictions, including the available register types.
		phiMap.values().forEach(
			relatedSemanticValues ->
			{
				// Consume the first related semantic value to construct a phi
				// instruction, and populate the others via moves.
				final L2SemanticValue firstSemanticValue =
					relatedSemanticValues.get(0);
				final TypeRestriction restriction =
					manifests.stream()
						.map(m -> m.restrictionFor(firstSemanticValue))
						.reduce(TypeRestriction::union)
						.orElse(bottomRestriction); // impossible
				// Implicitly discard it if there were no common register kinds
				// between all the inputs.
				if (restriction.isBoxed())
				{
					// Generate a boxed phi.
					final List<L2ReadBoxedOperand> sources =
						manifests.stream()
							.map(m -> m.readBoxed(firstSemanticValue))
							.collect(toList());
					generatePhi(
						generator,
						relatedSemanticValues,
						restriction,
						new L2ReadBoxedVectorOperand(sources),
						L2_PHI_PSEUDO_OPERATION.boxed,
						this::readBoxed,
						generator::boxedWrite);
				}
				if (restriction.isUnboxedInt())
				{
					// Generate an unboxed int phi.
					final List<L2ReadIntOperand> sources =
						manifests.stream()
							.map(m -> m.readInt(firstSemanticValue))
							.collect(toList());
					generatePhi(
						generator,
						relatedSemanticValues,
						restriction,
						new L2ReadIntVectorOperand(sources),
						L2_PHI_PSEUDO_OPERATION.unboxedInt,
						this::readInt,
						generator::intWrite);
				}
				if (restriction.isUnboxedFloat())
				{
					// Generate an unboxed float phi.
					final List<L2ReadFloatOperand> sources =
						manifests.stream()
							.map(m -> m.readFloat(firstSemanticValue))
							.collect(toList());
					generatePhi(
						generator,
						relatedSemanticValues,
						restriction,
						new L2ReadFloatVectorOperand(sources),
						L2_PHI_PSEUDO_OPERATION.unboxedFloat,
						this::readFloat,
						generator::floatWrite);
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
	 * @param createReader
	 *        A {@link Function} taking an {@link L2SemanticValue} and producing
	 *        a suitable {@link L2ReadOperand}.
	 * @param createWriter
	 *        A {@link BiFunction} taking an {@link L2SemanticValue} and a
	 *        {@link TypeRestriction}, and producing a suitable {@link
	 *        L2WriteOperand}.
	 * @param <RV>
	 *        The {@link L2ReadVectorOperand} supplying values.
	 * @param <RR>
	 *        The {@link L2ReadOperand} type supplying each value.
	 * @param <R>
	 *        The kind of {@link L2Register}s to merge.
	 */
	private <
		RV extends L2ReadVectorOperand<RR, R>,
		RR extends L2ReadOperand<R>,
		R extends L2Register>
	void generatePhi (
		final L2Generator generator,
		final Collection<L2SemanticValue> relatedSemanticValues,
		final TypeRestriction restriction,
		final RV sources,
		final L2_PHI_PSEUDO_OPERATION<RR, R> phiOperation,
		final Function<L2SemanticValue, RR> createReader,
		final BiFunction<L2SemanticValue, TypeRestriction, L2WriteOperand<R>>
			createWriter)
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
				recordDefinitionForNewKind(onlySource);
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
				createWriter.apply(firstSemanticValue, restriction));
		}
		final List<L2SemanticValue> otherSemanticValues =
			new ArrayList<>(relatedSemanticValues);
		otherSemanticValues.remove(firstSemanticValue);
		otherSemanticValues.forEach(
			otherSemanticValue ->
				generator.addInstruction(
					phiOperation.moveOperation,
					createReader.apply(firstSemanticValue),
					createWriter.apply(otherSemanticValue, restriction)));
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
			oldSynonym ->
			{
				final TypeRestriction restriction =
					restrictionFor(oldSynonym.pickSemanticValue());
				newManifest.introduceSynonym(
					oldSynonym.transform(semanticValueTransformer),
					restriction);
			});
		return newManifest;
	}
}
