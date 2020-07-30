/*
 * A_BundleTree.java
 * Copyright © 1993-2019, The Avail Foundation, LLC.
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
package com.avail.descriptor.bundles

import com.avail.compiler.ParsingOperation
import com.avail.descriptor.maps.A_Map
import com.avail.descriptor.module.A_Module
import com.avail.descriptor.numbers.IntegerDescriptor
import com.avail.descriptor.parsing.A_DefinitionParsingPlan
import com.avail.descriptor.parsing.A_ParsingPlanInProgress
import com.avail.descriptor.parsing.DefinitionParsingPlanDescriptor
import com.avail.descriptor.representation.A_BasicObject
import com.avail.descriptor.representation.A_BasicObject.Companion.dispatch
import com.avail.descriptor.representation.AvailObject
import com.avail.descriptor.representation.NilDescriptor
import com.avail.descriptor.representation.NilDescriptor.Companion.nil
import com.avail.descriptor.sets.A_Set
import com.avail.descriptor.tuples.A_String
import com.avail.descriptor.tuples.A_Tuple
import java.util.Deque

/**
 * `A_BundleTree` is an interface that specifies the operations specific to a
 * [message&32;bundle&32;tree][MessageBundleTreeDescriptor] that an
 * [AvailObject] must implement.  It's a sub-interface of [A_BasicObject], the
 * interface that defines the behavior that all [AvailObject]s are required to
 * support.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
interface A_BundleTree : A_BasicObject {

	companion object {
		/**
		 * Answer the bundle tree's map of all plans.
		 *
		 * @return
		 *   A map of type `{bundle→{definition→plan|0..}|0..}`.
		 */
		fun A_BundleTree.allParsingPlansInProgress() =
			dispatch { o_AllParsingPlansInProgress(it) }

		/**
		 * Expand the bundle tree if there's anything currently unclassified in
		 * it. By postponing this until necessary, construction of the parsing
		 * rules for the grammar is postponed until actually necessary.
		 *
		 * @param module
		 *   The current module which this bundle tree is being used to parse.
		 */
		fun A_BundleTree.expand(module: A_Module) =
			dispatch { o_Expand(it, module) }

		/**
		 * A grammatical restriction has been added.  Update this bundle tree to
		 * conform to the new restriction along any already-expanded paths for
		 * the given plan.  Updated the treesToVisit collection to include any
		 * new trees to visit as a consequence of visiting this tree.
		 *
		 * @param planInProgress
		 *   The [A_DefinitionParsingPlan] along which to update the bundle tree
		 *   (and extant successors).
		 * @param treesToVisit
		 *   A collection of [Pair]s to visit.  Updated to include successors of
		 *   this `<bundle, planInProgress>`.
		 */
		fun A_BundleTree.updateForNewGrammaticalRestriction(
			planInProgress: A_ParsingPlanInProgress,
			treesToVisit: Deque<Pair<A_BundleTree, A_ParsingPlanInProgress>>
		) = dispatch {
			o_UpdateForNewGrammaticalRestriction(
				it, planInProgress, treesToVisit)
		}

		/**
		 * Answer the [set][A_Set] of [bundles][A_Bundle], an invocation of
		 * which has been completely parsed when this bundle tree has been
		 * reached.
		 *
		 * This is only an authoritative set if [expand] has been invoked since
		 * the last modification via methods like [addPlanInProgress].
		 *
		 * @return
		 *   The bundles for which a send has been parsed at this point.
		 */
		fun A_BundleTree.lazyComplete() = dispatch { o_LazyComplete(it) }

		/**
		 * Answer the bundle trees that are waiting for a specific token to be
		 * parsed.  These are organized as a map where each key is the
		 * [A_String] form of an expected token, and the corresponding value is
		 * the successor [A_BundleTree] representing the situation where a token
		 * matching the key was consumed.
		 *
		 * This is only an authoritative map if an [expand] has been invoked
		 * since the last modification via methods like [addPlanInProgress].
		 *
		 * @return
		 *   A map from strings to bundle trees.
		 */
		fun A_BundleTree.lazyIncomplete() = dispatch { o_LazyIncomplete(it) }

		/**
		 * Answer the bundle trees that are waiting for a specific
		 * case-insensitive token to be parsed.  These are organized as a map
		 * where each key is the lower-case string form of an expected
		 * case-insensitive token, and the corresponding value is the successor
		 * bundle tree representing the situation where a token
		 * case-insensitively matching the key was consumed.
		 *
		 * This is only an authoritative map if an [expand] has been invoked
		 * since the last modification via methods like [addPlanInProgress].
		 *
		 * @return
		 *   A map from lowercase strings to bundle trees.
		 */
		fun A_BundleTree.lazyIncompleteCaseInsensitive() =
			dispatch { o_LazyIncompleteCaseInsensitive(it) }

		/**
		 * Answer the bundle trees that will be reached when specific parse
		 * instructions run.  During normal processing, all such instructions
		 * are attempted in parallel.  Certain instructions like
		 * [ParsingOperation.PARSE_PART] do not get added to this map, and are
		 * instead added to other structures such as [lazyIncomplete].
		 *
		 * Each key is an [integer][IntegerDescriptor] that encodes a parsing
		 * instruction, and the value is a tuple of successor
		 * [bundle&#32;trees][A_BundleTree] that are reached after executing
		 * that parsing instruction.  The tuples are typically of size one, but
		 * some instructions require the parsings to diverge, for example when
		 * running macro prefix functions.
		 *
		 * This is only an authoritative map if an [expand] has been invoked
		 * since the last modification via methods like [addPlanInProgress].
		 *
		 * @return
		 *   An [A_Map] from integer-encoded instructions to [A_Tuple]s of
		 *   successor [A_BundleTree]s.
		 */
		fun A_BundleTree.lazyActions() = dispatch { o_LazyActions(it) }

		/**
		 * Answer a map used by the [ParsingOperation.CHECK_ARGUMENT]
		 * instruction to quickly eliminate arguments that are forbidden by
		 * grammatical restrictions.  The map is from each restricted argument
		 * [bundle][A_Bundle] to the successor bundle tree that includes every
		 * bundle that *is* allowed when an argument is an invocation of a
		 * restricted argument bundle.  Each argument bundle that is restricted
		 * by at least one parent bundle at this point (just after having parsed
		 * an argument) has an entry in this map.  Argument bundles that are not
		 * restricted do not occur in this map, and are instead dealt with by an
		 * entry in the [lazyActions] map.
		 *
		 * This technique leads to an increase in the number of bundle trees,
		 * but is very fast at eliminating illegal parses, even when expressions
		 * are highly ambiguous (or highly ambiguous for their initial parts).
		 *
		 * This is only an authoritative map if an [expand] has been invoked
		 * since the last modification via methods like [addPlanInProgress].
		 *
		 * @return
		 *   An [A_Map] from potential child [A_Bundle] to the successor
		 *   [A_BundleTree] that should be visited if an invocation of that
		 *   bundle has just been parsed as an argument.
		 */
		fun A_BundleTree.lazyPrefilterMap(): A_Map =
			dispatch { o_LazyPrefilterMap(it) }

		/**
		 * If this message bundle tree has a type filter tree, return the raw
		 * pojo holding it, otherwise [NilDescriptor.nil].
		 *
		 * The type filter tree is used to quickly eliminate potential bundle
		 * invocations based on the type of an argument that has just been
		 * parsed. The argument's expression type is looked up in the tree, and
		 * the result is which bundle tree should be visited, having eliminated
		 * all parsing possibilities where the argument was of an unacceptable
		 * type.
		 *
		 * This is only authoritative if an [expand] has been invoked since the
		 * last modification via methods like [addPlanInProgress].
		 *
		 * @return
		 *   The type filter tree pojo or [nil].
		 */
		fun A_BundleTree.lazyTypeFilterTreePojo() =
			dispatch { o_LazyTypeFilterTreePojo(it) }

		/**
		 * Add a
		 * [definition&#32;parsing&#32;plan][DefinitionParsingPlanDescriptor] to
		 * this bundle tree.  The corresponding bundle must already be present.
		 *
		 * @param planInProgress
		 *   The [A_DefinitionParsingPlan] to add.
		 */
		fun A_BundleTree.addPlanInProgress(
			planInProgress: A_ParsingPlanInProgress
		) = dispatch { o_AddPlanInProgress(it, planInProgress) }

		/**
		 * Remove information about this [definition][A_DefinitionParsingPlan]
		 * from this bundle tree.
		 *
		 * @param planInProgress
		 *   The [A_ParsingPlanInProgress] to exclude.
		 */
		fun A_BundleTree.removePlanInProgress(
			planInProgress: A_ParsingPlanInProgress
		) = dispatch { o_RemovePlanInProgress(it, planInProgress) }

		/**
		 * Answer the nearest ancestor bundle tree that contained a
		 * [ParsingOperation.JUMP_BACKWARD].  There may be closer ancestor
		 * [A_BundleTree]s with a backward jump, but that jump wasn't present in
		 * the bundle tree yet.
		 *
		 * @return
		 *   The nearest ancestor backward-jump-containing bundle tree.
		 */
		fun A_BundleTree.latestBackwardJump() =
			dispatch { o_LatestBackwardJump(it) }

		/**
		 * Answer whether there are any parsing-plans-in-progress which are at a
		 * [backward&#32;jump][ParsingOperation.JUMP_BACKWARD]].
		 *
		 * @return
		 *   Whether there are any backward jumps.
		 */
		fun A_BundleTree.hasBackwardJump() = dispatch { o_HasBackwardJump(it) }

		/**
		 * Answer whether this bundle tree has been marked as the source of a
		 * cycle. If so, the [latestBackwardJump] is the bundle tree at which
		 * to continue processing.
		 *
		 * @return
		 *   Whether the bundle tree is the source of a linkage to an equivalent
		 *   ancestor bundle tree.
		 */
		fun A_BundleTree.isSourceOfCycle() = dispatch { o_IsSourceOfCycle(it) }

		/**
		 * Set whether this bundle tree is the source of a cycle.  If so, the
		 * [latestBackwardJump] must be the bundle tree at which to continue
		 * processing.
		 *
		 * @param isSourceOfCycle
		 *   Whether the bundle tree is the source of a linkage to an equivalent
		 *   ancestor bundle tree.
		 */
		fun A_BundleTree.isSourceOfCycle(isSourceOfCycle: Boolean) =
			dispatch { o_IsSourceOfCycle(it, isSourceOfCycle) }
	}
}
