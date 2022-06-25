/*
 * StylerDescriptor.kt
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

import avail.descriptor.functions.A_Function
import avail.descriptor.methods.StylerDescriptor.ObjectSlots.DEFINITION
import avail.descriptor.methods.StylerDescriptor.ObjectSlots.FUNCTION
import avail.descriptor.methods.StylerDescriptor.ObjectSlots.MODULE
import avail.descriptor.module.A_Module
import avail.descriptor.module.ModuleDescriptor
import avail.descriptor.objects.ObjectTypeDescriptor.Companion.Styles.styleType
import avail.descriptor.objects.ObjectTypeDescriptor.Companion.Styles.stylerFunctionType
import avail.descriptor.phrases.A_Phrase
import avail.descriptor.phrases.MacroSubstitutionPhraseDescriptor
import avail.descriptor.phrases.SendPhraseDescriptor
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.AvailObject
import avail.descriptor.representation.AvailObject.Companion.combine4
import avail.descriptor.representation.Descriptor
import avail.descriptor.representation.Mutability
import avail.descriptor.representation.ObjectSlotsEnum
import avail.descriptor.tokens.A_Token
import avail.descriptor.tuples.StringDescriptor.Companion.stringFrom
import avail.descriptor.types.TypeTag

/**
 * An [A_Styler] is the mechanism by which abstract styles are associated with a
 * module's phrases and tokens.  Stylers are created within the scope of a
 * module, and attached to a single [A_Definition] (typically a method or macro
 * definition).
 *
 * At compilation time, after a top-level phrase has been compiled and executed,
 * the [send][SendPhraseDescriptor] phrase (which may be the original of a
 * macro [substitution][MacroSubstitutionPhraseDescriptor] phrase) is traversed
 * bottom-up to populate some maps from phrases and tokens to styles.  As it
 * moves up the phrase tree, parent phrases may override the base style settings
 * established by the child phrases, such as to highlight constant strings used
 * as a method name in a method definition, versus arbitrary constant strings.
 *
 * The definitions of the method referenced by the send are filtered to include
 * only those for which:
 *   1. the styler is non-nil,
 *   2. the styler was defined in an ancestor of the current module, and
 *   3. the styler's definition was also defined in an ancestor module.
 * Note that this list cannot change due to other modules being loaded
 * concurrently, but it can change when a styler is added in the current module.
 *
 * Given these definitions, a lookup tree is constructed for the [A_Method].
 * The tree is cached for the duration of the current module's loading activity,
 * and is invalidated when a new styler is added for this [A_Method].  Other
 * modules are unaffected by new stylers.
 *
 * The static types of the method arguments are used to look up the most
 * specific styler in this tree.  If none is applicable, the default styler is
 * used.  If multiple most-specific stylers apply (i.e., there are at least two
 * which have no more specific definition in the tree), a conflict is reported,
 * probably by the use of a special conflict style.  Otherwise only one styler
 * applies, and its function is evaluated to update the maps from phrases and
 * tokens to styles.
 *
 * @constructor
 *
 * @param mutability
 *   The [mutability][Mutability] of the new descriptor.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
class StylerDescriptor private constructor(mutability: Mutability) : Descriptor(
	mutability,
	TypeTag.UNKNOWN_TAG,
	ObjectSlots::class.java,
	null)
{
	/**
	 * The layout of object slots for my instances.
	 */
	enum class ObjectSlots : ObjectSlotsEnum {
		/**
		 * The [function][stylerFunctionType] to invoke for styling.  It should
		 * accept the phrase being styled, a variable holding a map from
		 * [A_Phrase] to [style][styleType], and a variable holding a map from
		 * [A_Token] to style.
		 */
		FUNCTION,

		/**
		 * The [A_Definition] for which this is a styler.
		 */
		DEFINITION,

		/**
		 * The [module][ModuleDescriptor] in which this styler was added.
		 */
		MODULE
	}

	enum class BaseStyle(kotlinString: String)
	{
		DEFINITION("Definition"),

		DEFINITION_NAME("DefinitionName"),

		STRING_LITERAL("StringLiteral"),

		INTEGER_LITERAL("IntegerLiteral"),

		FLOAT_LITERAL("FloatLiteral"),

		DOUBLE_LITERAL("DoubleLiteral");

		val string = stringFrom(kotlinString).makeShared()
	}

	override fun o_Hash(self: AvailObject): Int = combine4(
		self.slot(FUNCTION).hash(),
		self.slot(DEFINITION).hash(),
		self.slot(MODULE).hash(),
		0x443046b4)

	override fun o_Function(self: AvailObject): A_Function =
		self.slot(FUNCTION)

	override fun o_Definition(self: AvailObject): A_Definition =
		self.slot(DEFINITION)

	override fun o_Module(self: AvailObject): A_Module =
		self.slot(MODULE)

	override fun o_Equals(self: AvailObject, another: A_BasicObject) =
		self.sameAddressAs(another)

	override fun mutable() = mutable

	// There is no immutable variant; answer the shared descriptor.
	override fun immutable() = shared

	override fun shared() = shared

	companion object {
		/** The mutable [StylerDescriptor]. */
		private val mutable = StylerDescriptor(Mutability.MUTABLE)

		/** The shared [StylerDescriptor]. */
		private val shared = StylerDescriptor(Mutability.SHARED)

		/**
		 * Create a new [styler][StylerDescriptor] with the specified
		 * information.  Make it [Mutability.SHARED].
		 *
		 * @param function
		 *   The [function][A_Function] to run against a call site's phrase to
		 *   generate styles.
		 * @param definition
		 *   The [definition][A_Definition] that will hold this styler.
		 * @param module
		 *   The [module][ModuleDescriptor] in which this styler was defined.
		 * @return
		 *   The new styler, not yet installed.
		 */
		fun newStyler(
			function: A_Function,
			definition: A_Definition,
			module: A_Module
		): A_Styler = mutable.createShared {
			setSlot(FUNCTION, function)
			setSlot(DEFINITION, definition)
			setSlot(MODULE, module)
		}
	}
}
