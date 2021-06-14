/*
 * GrammaticalRestrictionDescriptor.kt
 * Copyright © 1993-2021, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
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

import com.avail.AvailRuntimeSupport
import com.avail.descriptor.bundles.A_Bundle
import com.avail.descriptor.bundles.MessageBundleDescriptor
import com.avail.descriptor.methods.GrammaticalRestrictionDescriptor.IntegerSlots.Companion.HASH
import com.avail.descriptor.methods.GrammaticalRestrictionDescriptor.ObjectSlots.ARGUMENT_RESTRICTION_SETS
import com.avail.descriptor.methods.GrammaticalRestrictionDescriptor.ObjectSlots.DEFINITION_MODULE
import com.avail.descriptor.methods.GrammaticalRestrictionDescriptor.ObjectSlots.RESTRICTED_BUNDLE
import com.avail.descriptor.module.A_Module
import com.avail.descriptor.module.ModuleDescriptor
import com.avail.descriptor.representation.A_BasicObject
import com.avail.descriptor.representation.AvailObject
import com.avail.descriptor.representation.BitField
import com.avail.descriptor.representation.Descriptor
import com.avail.descriptor.representation.IntegerSlotsEnum
import com.avail.descriptor.representation.Mutability
import com.avail.descriptor.representation.ObjectSlotsEnum
import com.avail.descriptor.sets.SetDescriptor
import com.avail.descriptor.tuples.A_Tuple
import com.avail.descriptor.tuples.TupleDescriptor
import com.avail.descriptor.types.TypeTag

/**
 * A `GrammaticalRestrictionDescriptor grammatical restriction` serves to
 * exclude specific parses of nested method sends, thereby defining the negative
 * space of a grammar.  As it happens, this negative space is significantly more
 * modular than traditional positive grammars, in which precedence between all
 * operations is specified explicitly.  Not only is the negative space scheme
 * more modular, but the class of grammars specifiable this way is much more
 * powerful.
 *
 * For example, one may specify that a call to `"_*_"` may not have a call to
 * `"_+_"` as either argument.  Thus, `1+2*3` can only be parsed as `1+(2*3)`.
 * Similarly, by forbidding a send of `"_-_"` to be the right argument of a send
 * of `"_-_"`, this forbids interpreting `1-2-3` as `1-(2-3)`, leaving only the
 * traditional interpretation `(1-2)-3`.  But `"_min_"` and `"_max_"` have no
 * natural or conventional precedence relationship between each other.
 * Therefore an expression like `1 min 2 max 3` should be marked as ambiguous.
 * However, we can still treat `1 min 2 min 3` as `(1 min 2) min 3` by saying
 * `"_min_"` can't have `"_min_"` as its right argument.  Similarly `"_max_"`
 * can't have `"_max_"` as its right argument.  As another example we can
 * disallow `"(_)"` from occurring as the sole argument of `"(_)"`.  This still
 * allows redundant parentheses for clarity in expressions like `1+(2*3)`, but
 * forbids `((1*2))+3`.  This rule is intended to catch manual rewriting errors
 * in which the open and close parenthesis counts happen to match in some
 * complex expression, but multiple parenthesis pairs could mislead a reader.
 *
 * Grammatical restrictions are syntactic only, and as such apply to
 * [message&#32;bundles][MessageBundleDescriptor] rather than
 * [methods][MethodDescriptor].
 *
 * A grammatical restriction only affects the grammar of expressions in the
 * current module, plus any modules that recursively use or extend that module.
 *
 * @constructor
 *
 * @param mutability
 *   The [mutability][Mutability] of the new descriptor.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
class GrammaticalRestrictionDescriptor private constructor(
	mutability: Mutability
) : Descriptor(
	mutability,
	TypeTag.GRAMMATICAL_RESTRICTION_TAG,
	ObjectSlots::class.java,
	IntegerSlots::class.java
) {
	/**
	 * The layout of integer slots for my instances.
	 */
	enum class IntegerSlots : IntegerSlotsEnum {
		/**
		 * The low 32 bits are used for the [HASH], but the upper 32 can be used
		 * by other [BitField]s in subclasses.
		 */
		HASH_AND_MORE;

		companion object {
			/**
			 * A slot to hold the hash value, a random value computed at
			 * construction time.
			 */
			val HASH = BitField(HASH_AND_MORE, 0, 32)
		}
	}

	/**
	 * The layout of object slots for my instances.
	 */
	enum class ObjectSlots : ObjectSlotsEnum {
		/**
		 * A [tuple][TupleDescriptor] of [sets][SetDescriptor] of
		 * [message&#32;bundles][MessageBundleDescriptor] which are restricted
		 * from occurring in the corresponding underscore positions.  Due to
		 * guillemet expressions («»), the underscore positions do not
		 * necessarily agree with the tuple of arguments passed in a method
		 * send.
		 */
		ARGUMENT_RESTRICTION_SETS,

		/**
		 * The [message&#32;bundle][MessageBundleDescriptor] for which this is a
		 * grammatical restriction.
		 */
		RESTRICTED_BUNDLE,

		/**
		 * The [module][ModuleDescriptor] in which this grammatical restriction
		 * was added.
		 */
		DEFINITION_MODULE
	}

	override fun o_Hash(self: AvailObject): Int = self.slot(HASH)

	override fun o_RestrictedBundle(self: AvailObject): A_Bundle =
		self.slot(RESTRICTED_BUNDLE)

	override fun o_ArgumentRestrictionSets(self: AvailObject): A_Tuple =
		self.slot(ARGUMENT_RESTRICTION_SETS)

	// Compare by identity.
	override fun o_Equals(self: AvailObject, another: A_BasicObject) =
		self.sameAddressAs(another)

	override fun o_DefinitionModule(self: AvailObject): A_Module =
		self.slot(DEFINITION_MODULE)

	override fun mutable() = mutable

	// There is no immutable variant; answer the shared descriptor.
	override fun immutable() = shared

	override fun shared() = shared

	companion object {
		/**
		 * Create a new
		 * [grammatical&#32;restriction][GrammaticalRestrictionDescriptor] with
		 * the specified information.  Make it [Mutability.SHARED].
		 *
		 * @param argumentRestrictionSets
		 *   The tuple of sets of message bundles that restrict some message
		 *   bundle.
		 * @param restrictedBundle
		 *   The message bundle restricted by this grammatical restriction.
		 * @param module
		 *   The [module][ModuleDescriptor] in which this grammatical
		 *   restriction was defined.
		 * @return
		 *   The new grammatical restriction.
		 */
		fun newGrammaticalRestriction(
			argumentRestrictionSets: A_Tuple,
			restrictedBundle: A_Bundle,
			module: A_Module
		): A_GrammaticalRestriction = mutable.createShared {
			setSlot(HASH, AvailRuntimeSupport.nextNonzeroHash())
			setSlot(ARGUMENT_RESTRICTION_SETS, argumentRestrictionSets)
			setSlot(RESTRICTED_BUNDLE, restrictedBundle)
			setSlot(DEFINITION_MODULE, module)
		}

		/** The mutable [GrammaticalRestrictionDescriptor].  */
		private val mutable =
			GrammaticalRestrictionDescriptor(Mutability.MUTABLE)

		/** The shared [GrammaticalRestrictionDescriptor].  */
		private val shared = GrammaticalRestrictionDescriptor(Mutability.SHARED)
	}
}
