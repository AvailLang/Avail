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

import com.avail.interpreter.levelTwo.register.L2Register;
import com.avail.optimizer.values.Frame;
import com.avail.optimizer.values.L2SemanticValue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.UnaryOperator;

import static java.util.Collections.singleton;
import static java.util.Collections.unmodifiableSet;
import static java.util.stream.Collectors.toCollection;

/**
 * An {@code L2Synonym} is a set of {@link L2Register}s and {@link
 * L2SemanticValue}s.  The {@link L2ValueManifest} at each instruction includes
 * a set of synonyms which partition the potentially both the live registers and
 * semantic values.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class L2Synonym
{
	/**
	 * The {@link L2SemanticValue}s for which this synonym's registers hold the
	 * (same) value.
	 */
	private final Set<L2SemanticValue> semanticValues;

	/**
	 * Answer the immutable set of {@link L2SemanticValue}s of this synonym.
	 */
	public Set<L2SemanticValue> semanticValues ()
	{
		return semanticValues;
	}

	/**
	 * Create a synonym.
	 *
	 * @param semanticValues
	 *        The non-empty collection of {@link L2SemanticValue}s bound to this
	 *        synonym.
	 */
	public L2Synonym (
		final Collection<? extends L2SemanticValue> semanticValues)
	{
		final int size = semanticValues.size();
		assert size > 0;
		this.semanticValues = size == 1
			? singleton(semanticValues.iterator().next())
			: unmodifiableSet(new HashSet<>(semanticValues));
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
	public L2Synonym transformInnerSynonym (
		final L2Synonym original,
		final L2Synonym replacement)
	{
		if (this == original)
		{
			// Don't recurse inside, because it's impossible for it to contain
			// itself cyclically.
			return replacement;
		}
		// Expect few replacements, statistically.
		boolean anyChanged = false;
		final Set<L2SemanticValue> newSemanticValues =
			new HashSet<>(semanticValues.size());
		for (final L2SemanticValue semanticValue : semanticValues)
		{
			final L2SemanticValue transformedSemanticValue =
				semanticValue.transformInnerSynonym(original, replacement);
			if (semanticValue != transformedSemanticValue)
			{
				anyChanged = true;
			}
			newSemanticValues.add(transformedSemanticValue);
		}
		if (anyChanged)
		{
			return new L2Synonym(newSemanticValues);
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
	public L2Synonym transform (
		final UnaryOperator<L2SemanticValue> semanticValueTransformer)
	{
		final Set<L2SemanticValue> newSemanticValues = new HashSet<>();
		boolean changed = false;
		for (final L2SemanticValue semanticValue : semanticValues)
		{
			final L2SemanticValue newSemanticValue =
				semanticValueTransformer.apply(semanticValue);
			newSemanticValues.add(newSemanticValue);
			changed |= !newSemanticValue.equals(semanticValue);
		}
		return changed ? new L2Synonym(newSemanticValues) : this;
	}

	@Override
	public String toString ()
	{
		final List<String> sortedStrings = semanticValues.stream()
			.map(Object::toString)
			.sorted(String::compareTo)
			.collect(toCollection(ArrayList::new));
		final StringBuilder builder = new StringBuilder();
		builder.append('〖');
		boolean first = true;
		for (final String string : sortedStrings)
		{
			if (!first)
			{
				builder.append(" & ");
			}
			builder.append(string);
			first = false;
		}
		builder.append('〗');
		return builder.toString();
	}
}