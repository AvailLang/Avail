/*
 * A_BundleTree.java
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

package com.avail.descriptor;

import com.avail.compiler.ParsingOperation;
import com.avail.utility.Pair;

import java.util.Collection;

/**
 * {@code A_BundleTree} is an interface that specifies the {@linkplain
 * MessageBundleTreeDescriptor message-bundle-tree}-specific operations that an
 * {@link AvailObject} must implement.  It's a sub-interface of {@link
 * A_BasicObject}, the interface that defines the behavior that all AvailObjects
 * are required to support.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public interface A_BundleTree
extends A_BasicObject
{
	/**
	 * Answer the bundle tree's map of all plans.
	 *
	 * @return A map of type {bundle→{definition→plan|0..}|0..}.
	 */
	A_Map allParsingPlansInProgress ();

	/**
	 * Expand the bundle tree if there's anything currently unclassified in it.
	 * By postponing this until necessary, construction of the parsing rules for
	 * the grammar is postponed until actually necessary.
	 *
	 * @param module
	 *        The current module which this bundle tree is being used to parse.
	 */
	void expand (A_Module module);

	/**
	 * A grammatical restriction has been added.  Update this bundle tree to
	 * conform to the new restriction along any already-expanded paths for the
	 * given plan.  Updated the treesToVisit collection to include any new
	 * trees to visit as a consequence of visiting this tree.
	 *
	 * @param planInProgress
	 *        The {@link A_DefinitionParsingPlan} along which to update the
	 *        bundle tree (and extant successors).
	 * @param treesToVisit
	 *        A collection of {@link Pair}s to visit.  Updated to include
	 *        successors of this &lt;bundle, planInProgress&gt;.
	 */
	void updateForNewGrammaticalRestriction (
		final A_ParsingPlanInProgress planInProgress,
		final Collection<Pair<A_BundleTree, A_ParsingPlanInProgress>>
			treesToVisit);

	/**
	 * Answer the {@link A_Set set} of {@link A_Bundle bundles}, an invocation
	 * of which has been completely parsed when this bundle tree has been
	 * reached.
	 *
	 * <p>This is only an authoritative set if an {@link #expand(A_Module)} has
	 * been invoked since the last modification via methods like {@link
	 * #addPlanInProgress(A_ParsingPlanInProgress)}.</p>
	 *
	 * @return The bundles for which a send has been parsed at this point.
	 */
	A_Set lazyComplete ();

	/**
	 * Answer the bundle trees that are waiting for a specific token to be
	 * parsed.  These are organized as a map where each key is the string form
	 * of an expected token, and the corresponding value is the successor {@link
	 * A_BundleTree bundle tree} representing the situation where a token
	 * matching the key was consumed.
	 *
	 * <p>This is only an authoritative map if an {@link #expand(A_Module)} has
	 * been invoked since the last modification via methods like {@link
	 * #addPlanInProgress(A_ParsingPlanInProgress)}.</p>
	 *
	 * @return A map from strings to bundle trees.
	 */
	A_Map lazyIncomplete ();

	/**
	 * Answer the bundle trees that are waiting for a specific case-insensitive
	 * token to be parsed.  These are organized as a map where each key is the
	 * lower-case string form of an expected case-insensitive token, and the
	 * corresponding value is the successor bundle tree representing the
	 * situation where a token case-insensitively matching the key was consumed.
	 *
	 * <p>This is only an authoritative map if an {@link #expand(A_Module)} has
	 * been invoked since the last modification via methods like {@link
	 * #addPlanInProgress(A_ParsingPlanInProgress)}.</p>
	 *
	 * @return A map from lowercase strings to bundle trees.
	 */
	A_Map lazyIncompleteCaseInsensitive ();

	/**
	 * Answer the bundle trees that will be reached when specific parse
	 * instructions run.  During normal processing, all such instructions are
	 * attempted in parallel.  Certain instructions like {@link
	 * ParsingOperation#PARSE_PART} do not get added to this map, and are added
	 * to other structures such as {@link #lazyIncomplete()}.
	 *
	 * <p>Each key is an {@link IntegerDescriptor integer} that encodes a
	 * parsing instruction, and the value is a tuple of successor {@link
	 * A_BundleTree bundle trees} that are reached after executing that parsing
	 * instruction.  The tuples are typically of size one, but some instructions
	 * require the parsings to diverge, for example when running macro prefix
	 * functions.</p>
	 *
	 * <p>This is only an authoritative map if an {@link #expand(A_Module)} has
	 * been invoked since the last modification via methods like {@link
	 * #addPlanInProgress(A_ParsingPlanInProgress)}.</p>
	 *
	 * @return A map from integer-encoded instructions to tuples of successor
	 *         bundle trees.
	 */
	A_Map lazyActions ();

	/**
	 * Answer a map used by the {@link ParsingOperation#CHECK_ARGUMENT}
	 * instruction to quickly eliminate arguments that are forbidden by
	 * grammatical restrictions.  The map is from each restricted argument
	 * {@link A_Bundle bundle} to the successor bundle tree that includes every
	 * bundle that <em>is</em> allowed when an argument is an invocation of a
	 * restricted argument bundle.  Each argument bundle that is restricted by
	 * at least one parent bundle at this point (just after having parsed an
	 * argument) has an entry in this map.  Argument bundles that are not
	 * restricted do not occur in this map, and are instead dealt with by an
	 * entry in the {@link #lazyActions()} map.
	 *
	 * <p>This technique leads to an increase in the number of bundle trees, but
	 * is very fast at eliminating illegal parses, even when expressions are
	 * highly ambiguous (or highly ambiguous for their initial parts).</p>
	 *
	 * <p>This is only an authoritative map if an {@link #expand(A_Module)} has
	 * been invoked since the last modification via methods like {@link
	 * #addPlanInProgress(A_ParsingPlanInProgress)}.</p>
	 *
	 * @return A map from potential child bundle to the successor bundle tree
	 *         that should be visited if an invocation of that bundle has just
	 *         been parsed as an argument.
	 */
	A_Map lazyPrefilterMap ();

	/**
	 * If this message bundle tree has a type filter tree, return the raw pojo
	 * holding it, otherwise {@link NilDescriptor#nil}.
	 *
	 * <p>The type filter tree is used to quickly eliminate potential bundle
	 * invocations based on the type of an argument that has just been parsed.
	 * The argument's expression type is looked up in the tree, and the result
	 * is which bundle tree should be visited, having eliminated all parsing
	 * possibilities where the argument was of an unacceptable type.</p>
	 *
	 * <p>This is only authoritative if an {@link #expand(A_Module)} has been
	 * invoked since the last modification via methods like {@link
	 * #addPlanInProgress(A_ParsingPlanInProgress)}.</p>
	 *
	 * @return The type filter tree pojo or nil.
	 */
	A_BasicObject lazyTypeFilterTreePojo ();

	/**
	 * Add a {@link DefinitionParsingPlanDescriptor definition parsing plan} to
	 * this bundle tree.  The corresponding bundle must already be present.
	 *
	 * @param planInProgress
	 *        The definition parsing plan to add.
	 */
	void addPlanInProgress (A_ParsingPlanInProgress planInProgress);

	/**
	 * Remove information about this {@link A_DefinitionParsingPlan definition
	 * parsing plan} from this bundle tree.
	 *
	 * @param planInProgress
	 *        The parsing plan to exclude.
	 */
	void removePlanInProgress (A_ParsingPlanInProgress planInProgress);

	/**
	 * Answer the nearest ancestor bundle tree that contained a {@link
	 * ParsingOperation#JUMP_BACKWARD}.  There may be closer ancestor bundle
	 * trees with a backward jump, but that jump wasn't present in the bundle
	 * tree yet.
	 *
	 * @return The nearest ancestor backward-jump-containing bundle tree.
	 */
	A_BundleTree latestBackwardJump ();

	/**
	 * Answer whether there are any parsing-plans-in-progress which are at a
	 * backward jump.
	 *
	 * @return Whether there are any backward jumps.
	 */
	boolean hasBackwardJump ();

	/**
	 * Answer whether this bundle tree has been marked as the source of a cycle.
	 * If so, the {@link #latestBackwardJump()} is the bundle tree at which to
	 * continue processing.
	 *
	 * @return Whether the bundle tree is the source of a linkage to an
	 * equivalent ancestor bundle tree.
	 */
	boolean isSourceOfCycle ();

	/**
	 * Set whether this bundle tree is the source of a cycle.  If so, the {@link
	 * #latestBackwardJump()} must be the bundle tree at which to continue
	 * processing.
	 *
	 * @param isSourceOfCycle
	 *        Whether the bundle tree is the source of a linkage to an
	 *        equivalent ancestor bundle tree.
	 */
	void isSourceOfCycle (boolean isSourceOfCycle);
}
