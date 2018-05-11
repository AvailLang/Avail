/*
 * A_DefinitionParsingPlan.java
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

package com.avail.descriptor;

import com.avail.compiler.ParsingOperation;

/**
 * {@code A_DefinitionParsingPlan} is an interface that specifies the operations
 * that must be implemented by a {@linkplain DefinitionParsingPlanDescriptor
 * definition parsing plan}.  It's a sub-interface of {@link A_BasicObject},
 * the interface that defines the behavior that all AvailObjects are required to
 * support.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public interface A_DefinitionParsingPlan
extends A_BasicObject
{
	/**
	 * Answer the {@linkplain A_Bundle message bundle} that this definition
	 * parsing plan names.
	 *
	 * @return The bundle to be parsed.
	 */
	A_Bundle bundle ();

	/**
	 * Answer the {@linkplain A_Definition definition} that this plan has been
	 * specialized for.
	 *
	 * @return The definition whose argument types restrict parsing of the
	 *         bundle.
	 */
	A_Definition definition ();

	/**
	 * Answer a {@linkplain TupleDescriptor tuple} of {@linkplain
	 * IntegerDescriptor integers} encoding the {@linkplain ParsingOperation}s
	 * and operands required to parse a call to this parsing plan.
	 *
	 * <p>Matching parsing instructions for multiple messages can (usually) be
	 * executed in aggregate, avoiding the separate cost of attempting to parse
	 * each possible message at each place where a call may occur.</p>
	 *
	 * @return A tuple of integers encoding this plan's parsing instructions.
	 */
	A_Tuple parsingInstructions ();
}
