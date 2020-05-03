/*
 * A_DefinitionParsingPlan.kt
 * Copyright © 1993-2020, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice, this
 *     list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice, this
 *     list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
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

import com.avail.compiler.ParsingOperation
import com.avail.descriptor.bundles.A_Bundle
import com.avail.descriptor.methods.A_Definition
import com.avail.descriptor.numbers.IntegerDescriptor
import com.avail.descriptor.representation.A_BasicObject
import com.avail.descriptor.representation.A_BasicObject.Companion.dispatch
import com.avail.descriptor.representation.AvailObject
import com.avail.descriptor.tuples.A_Tuple
import com.avail.descriptor.tuples.TupleDescriptor

/**
 * `A_DefinitionParsingPlan` is an interface that specifies the operations that
 * must be implemented by a
 * [definition&#32;parsing&#32;plan][DefinitionParsingPlanDescriptor].  It's a
 * sub-interface of [A_BasicObject], the interface that defines the behavior
 * that all [AvailObject]s are required to support.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
interface A_DefinitionParsingPlan : A_BasicObject {
	companion object {
		/**
		 * Answer the [message bundle][A_Bundle] that this definition parsing
		 * plan names.
		 *
		 * @return
		 *   The bundle to be parsed.
		 */
		fun A_DefinitionParsingPlan.bundle(): A_Bundle =
			dispatch { o_Bundle(it) }

		/**
		 * Answer the [definition][A_Definition] that this plan has been
		 * specialized for.
		 *
		 * @return
		 *   The definition whose argument types restrict parsing of the bundle.
		 */
		fun A_DefinitionParsingPlan.definition(): A_Definition =
			dispatch { o_Definition(it) }

		/**
		 * Answer a [tuple][TupleDescriptor] of [integers][IntegerDescriptor]
		 * encoding the [ParsingOperation]s and operands required to parse a
		 * call to this parsing plan.
		 *
		 * Matching parsing instructions for multiple messages can (usually) be
		 * executed in aggregate, avoiding the separate cost of attempting to
		 * parse each possible message at each place where a call may occur.
		 *
		 * @return
		 *   A tuple of integers encoding this plan's parsing instructions.
		 */
		fun A_DefinitionParsingPlan.parsingInstructions(): A_Tuple =
			dispatch { o_ParsingInstructions(it) }
	}
}