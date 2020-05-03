/*
 * A_ParsingPlanInProgress.kt
 * Copyright © 1993-2020, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice, this
 *    list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 *  * Neither the name of the copyright holder nor the names of the contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
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
package com.avail.descriptor.parsing

import com.avail.descriptor.bundles.A_BundleTree
import com.avail.descriptor.representation.A_BasicObject
import com.avail.descriptor.representation.A_BasicObject.Companion.dispatch
import com.avail.descriptor.representation.AvailObject

/**
 * `A_ParsingPlanInProgress` is an interface that specifies the operations that
 * must be implemented by a
 * [parsing-plan-in-progress][ParsingPlanInProgressDescriptor].  It's a
 * sub-interface of [A_BasicObject], the interface that defines the behavior
 * that all [AvailObject]s are required to support.
 *
 * A plan-in-progress is the combination of a parsing plan and a current
 * program counter within the plan's instructions.  The parsing plan was already
 * specialized to a specific method definition's type signature, to allow early
 * type-based filtering during parsing.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
interface A_ParsingPlanInProgress : A_BasicObject {
	companion object {
		/**
		 * Answer whether this plan-in-progress is at a backward jump
		 * instruction.
		 *
		 * An [A_BundleTree] is expanded and kept in memory during parsing of a
		 * module.  When a method with a large number of repetitions is parsed,
		 * this would normally cause the bundle tree to expand and occupy more
		 * memory than is really needed.  Instead, we notice when a bundle tree
		 * node has reached the same plan-in-progress state as one of its
		 * ancestor bundle tree nodes, and simply link it back to the existing
		 * node, saving the memory space and the time to expand the bundle
		 * 'tree' repeatedly.
		 *
		 * @return
		 *   Whether it jumps backward from here.
		 */
		fun A_ParsingPlanInProgress.isBackwardJump(): Boolean =
			dispatch { o_IsBackwardJump(it) }

		/**
		 * Answer a Java [String] representing this message name being parsed at
		 * its position within the plan's parsing instructions.
		 *
		 * @return
		 *   A string describing the parsing plan with an indicator at the
		 *   specified parsing instruction.
		 */
		fun A_ParsingPlanInProgress.nameHighlightingPc(): String =
			dispatch { o_NameHighlightingPc(it) }

		/**
		 * Answer the program counter that this plan-in-progress represents.
		 *
		 * @return
		 *   The index into the plan's parsing instructions.
		 */
		fun A_ParsingPlanInProgress.parsingPc(): Int =
			dispatch { o_ParsingPc(it) }

		/**
		 * Answer this [plan-in-progress][ParsingPlanInProgressDescriptor]'s
		 * [A_DefinitionParsingPlan].
		 *
		 * @return
		 *   The parsing plan.
		 */
		fun A_ParsingPlanInProgress.parsingPlan(): A_DefinitionParsingPlan =
			dispatch { o_ParsingPlan(it) }
	}
}