/*
 * A_Definition.kt
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

import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.A_BasicObject.Companion.dispatch
import avail.descriptor.representation.AvailObject
import avail.descriptor.sets.A_Set
import avail.dispatch.LookupTree
import avail.interpreter.execution.AvailLoader

/**
 * `A_Definition` is an interface that specifies the operations specific
 * to [definitions][DefinitionDescriptor] (of a [method][MethodDescriptor]) in
 * Avail.  It's a sub-interface of [A_BasicObject], the interface that defines
 * the behavior that all [AvailObject]s are required to support.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
interface A_Definition : A_Sendable
{
	companion object
	{
		/**
		 * Answer the [method][MethodDescriptor] that this
		 * [definition][DefinitionDescriptor] is for.
		 *
		 * @return
		 *   The definition's method.
		 */
		val A_Definition.definitionMethod: A_Method
			get() = dispatch { o_DefinitionMethod(it) }

		/**
		 * This definition's [A_Set] of [A_Styler]s.  A module must not define
		 * more than one styler on the same [A_Definition].
		 *
		 * When styling a phrase, all applicable definitions are consulted,
		 * filtering by which definitions are visible to the current module, and
		 * which definitions contain at least one styler visible to the current
		 * module.  These are placed in a [LookupTree] (easily cached in the
		 * [AvailLoader]), and the phrase's argument phrases are used to
		 * identify the most specific relevant definition.  Once the definition
		 * is chosen, the visible modules that have a styler for that definition
		 * are selected (there must be at least one, otherwise the definition
		 * would have been excluded from the lookup tree).  If there is a styler
		 * from an ancestor module that dominates (i.e., all other applicable
		 * modules are themselves ancestors of it), then the styler from that
		 * module is used. Otherwise a special conflict styler will be used to
		 * bring attention to the collision.
		 */
		val A_Definition.definitionStylers: A_Set
			get() = dispatch { o_DefinitionStylers(it) }

		/**
		 * Atomically update this definition's set of stylers.
		 */
		fun A_Definition.updateStylers(updater: A_Set.() -> A_Set) =
			dispatch { o_UpdateStylers(it, updater) }
	}
}
