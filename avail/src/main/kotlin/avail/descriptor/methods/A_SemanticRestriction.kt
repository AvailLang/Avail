/*
 * A_SemanticRestriction.kt
 * Copyright © 1993-2022, The Avail Foundation, LLC.
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
package avail.descriptor.methods

import avail.descriptor.functions.A_Continuation
import avail.descriptor.functions.A_Function
import avail.descriptor.module.A_Module
import avail.descriptor.module.ModuleDescriptor
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.AvailObject

/**
 * `A_SemanticRestriction` is an interface that specifies the operations
 * suitable for a [semantic&#32;restriction][SemanticRestrictionDescriptor].
 * It's a sub-interface of [A_BasicObject], the interface that defines the
 * behavior that all [AvailObject]s are required to support.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
interface A_SemanticRestriction : A_BasicObject {
	/**
	 * Answer the function to execute to determine the effect of this semantic
	 * restriction on a list of argument static types at a call site.
	 *
	 * Also defined in [A_Continuation].
	 *
	 * @return
	 *   The function which is the body of the semantic restriction.
	 */
	fun function(): A_Function

	/**
	 * Answer the [method][MethodDescriptor] for which this semantic restriction
	 * applies.
	 *
	 * Also defined in [A_Definition] and [A_GrammaticalRestriction].
	 *
	 * @return
	 *   The method to which this semantic restriction applies.
	 */
	fun definitionMethod(): A_Method

	/**
	 * Answer the [module][ModuleDescriptor] in which this semantic
	 * restriction was defined.
	 *
	 * Also defined in [A_Definition].
	 *
	 * @return
	 *   The method to which this semantic restriction applies.
	 */
	fun definitionModule(): A_Module
}
