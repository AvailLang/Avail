/*
 * SemanticRestrictionDescriptor.kt
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
package com.avail.descriptor.methods

import com.avail.annotations.AvailMethod
import com.avail.descriptor.module.A_Module
import com.avail.descriptor.Descriptor
import com.avail.descriptor.module.ModuleDescriptor
import com.avail.descriptor.functions.A_Function
import com.avail.descriptor.functions.FunctionDescriptor
import com.avail.descriptor.representation.A_BasicObject
import com.avail.descriptor.representation.AvailObject
import com.avail.descriptor.representation.Mutability
import com.avail.descriptor.representation.ObjectSlotsEnum
import com.avail.descriptor.types.TypeTag
import com.avail.interpreter.primitive.phrases.P_RejectParsing

/**
 * A [semantic&#32;restriction][A_SemanticRestriction] holds a function to
 * invoke when *compiling* a potential call site of a method.  The arguments'
 * static types at the call site are passed to the function, and if successful
 * it produces a result type used to further restrict the expected type at that
 * call site.  Instead, it may invoke [P_RejectParsing] to cause that call site
 * to be rejected as a possible parse, supplying a message describing the nature
 * of the rejection.  The message will be shown to the user if no significant
 * parsing beyond this point in the source file was possible.  Raising an
 * unhandled exception during execution of the semantic restriction will
 * similarly be wrapped with a suitable error string and possibly a stack trace,
 * leading to a parse rejection just as with the explicit rejection primitive.
 *
 * If a semantic restriction's function parameters are not general enough to
 * accept the actual arguments (the static types of the arguments at the call
 * site), then that semantic restriction is simply not run.
 *
 * @constructor
 *
 * @param mutability
 *   The [mutability][Mutability] of the new descriptor.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
class SemanticRestrictionDescriptor
/**
 * Construct a new [SemanticRestrictionDescriptor].
 *
 */
private constructor(mutability: Mutability) : Descriptor(
	mutability,
	TypeTag.SEMANTIC_RESTRICTION_TAG,
	ObjectSlots::class.java,
	null
) {
	/**
	 * The layout of object slots for my instances.
	 */
	enum class ObjectSlots : ObjectSlotsEnum {
		/**
		 * The [function][FunctionDescriptor] to invoke to determine the static
		 * suitability of the arguments at this call site, and to compute a
		 * stronger type bound for the call site's result type.
		 */
		FUNCTION,

		/**
		 * The [method][MethodDescriptor] for which this is a semantic
		 * restriction.
		 */
		DEFINITION_METHOD,

		/**
		 * The [module][ModuleDescriptor] in which this semantic restriction was
		 * added.
		 */
		DEFINITION_MODULE
	}

	@AvailMethod
	override fun o_Hash(self: AvailObject) =
		((self.slot(ObjectSlots.FUNCTION).hash() xor 0x0E0D9C10)
			+ self.slot(ObjectSlots.DEFINITION_METHOD).hash())

	@AvailMethod
	override fun o_Function(self: AvailObject): A_Function =
		self.slot(ObjectSlots.FUNCTION)

	override fun o_DefinitionMethod(self: AvailObject): A_Method =
		self.slot(ObjectSlots.DEFINITION_METHOD)

	@AvailMethod
	override fun o_DefinitionModule(self: AvailObject): A_Module =
		self.slot(ObjectSlots.DEFINITION_MODULE)

	/** Compare by identity. */
	@AvailMethod
	override fun o_Equals(self: AvailObject, another: A_BasicObject) =
		self.sameAddressAs(another)

	override fun mutable() = mutable

	// There is no immutable variant; answer the shared descriptor.
	override fun immutable() = shared

	override fun shared() = shared

	companion object {
		/** The mutable [SemanticRestrictionDescriptor].  */
		private val mutable = SemanticRestrictionDescriptor(Mutability.MUTABLE)

		/** The shared [SemanticRestrictionDescriptor].  */
		private val shared = SemanticRestrictionDescriptor(Mutability.SHARED)

		/**
		 * Create a new
		 * [semantic&#32;restriction][SemanticRestrictionDescriptor] with the
		 * specified information.  Make it [Mutability.SHARED].
		 *
		 * @param function
		 *   The [function][FunctionDescriptor] to run against a call site's
		 *   argument types.
		 * @param method
		 *   The [method][MethodDescriptor] for which this semantic restriction
		 *   applies.
		 * @param module
		 *   The [module][ModuleDescriptor] in which this semantic restriction
		 *   was defined.
		 * @return
		 *   The new semantic restriction.
		 */
		fun newSemanticRestriction(
			function: A_Function,
			method: A_Method,
			module: A_Module
		): A_SemanticRestriction = with(mutable.create()) {
			setSlot(ObjectSlots.FUNCTION, function)
			setSlot(ObjectSlots.DEFINITION_METHOD, method)
			setSlot(ObjectSlots.DEFINITION_MODULE, module)
			makeShared()
		}
	}
}