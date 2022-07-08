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

import avail.descriptor.character.CharacterDescriptor
import avail.descriptor.functions.A_Function
import avail.descriptor.methods.A_Styler.Companion.stylerFunctionType
import avail.descriptor.methods.StylerDescriptor.ObjectSlots.FUNCTION
import avail.descriptor.methods.StylerDescriptor.ObjectSlots.METHOD
import avail.descriptor.methods.StylerDescriptor.ObjectSlots.MODULE
import avail.descriptor.module.A_Module
import avail.descriptor.module.ModuleDescriptor
import avail.descriptor.numbers.DoubleDescriptor
import avail.descriptor.numbers.FloatDescriptor
import avail.descriptor.phrases.A_Phrase
import avail.descriptor.phrases.DeclarationPhraseDescriptor
import avail.descriptor.phrases.MacroSubstitutionPhraseDescriptor
import avail.descriptor.phrases.SendPhraseDescriptor
import avail.descriptor.phrases.VariableUsePhraseDescriptor
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.AvailObject
import avail.descriptor.representation.AvailObject.Companion.combine4
import avail.descriptor.representation.Descriptor
import avail.descriptor.representation.Mutability
import avail.descriptor.representation.ObjectSlotsEnum
import avail.descriptor.tokens.A_Token
import avail.descriptor.tuples.StringDescriptor.Companion.stringFrom
import avail.descriptor.types.TupleTypeDescriptor.Companion.stringType
import avail.descriptor.types.TypeTag
import java.awt.Color

/**
 * An [A_Styler] is the mechanism by which abstract styles are associated with a
 * module's phrases and tokens.  Stylers are created within the scope of a
 * module, and attached to a single [A_Method].
 *
 * At compilation time, after a top-level phrase has been compiled and executed,
 * the [send][SendPhraseDescriptor] phrase (which may be the original of a
 * macro [substitution][MacroSubstitutionPhraseDescriptor] phrase) is traversed
 * bottom-up to populate some maps from phrases and tokens to styles.  As it
 * moves up the phrase tree, parent phrases may override the base style settings
 * established by the child phrases, such as to highlight constant strings used
 * as a method name in a method definition, versus arbitrary constant strings.
 *
 * The stylers of the method referenced by the send are filtered to include only
 * those for which the defining module is an ancestor, and the most specific
 * (one that has all the others as ancestor) is chosen to style the send.  If
 * there is no such most specific module, a special conflict style is used.
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
		 * [A_Phrase] to style [name][stringType], a variable holding a map
		 * from [A_Token] to style [name][stringType], and a variable holding a
		 * map from each [variable-use][VariableUsePhraseDescriptor] to its
		 * [declaration][DeclarationPhraseDescriptor].
		 */
		FUNCTION,

		/**
		 * The [A_Method] for which this is a styler.
		 */
		METHOD,

		/**
		 * The [module][ModuleDescriptor] in which this styler was added.
		 */
		MODULE
	}

	enum class BaseStyle(
		val kotlinString: String,
		rgbInt: Int)
	{
		/** Default coloring of keywords. */
		KEYWORD("Keyword", 0xC0C0FF),

		/** Default coloring of operator tokens. */
		OPERATOR("Operator", 0xFFB0B0),


		/** The declaration of a variable/constant. */
		VARIABLE_DECLARATION("VariableDeclaration", 0xB0FFB0),

		/** The declaration of a block argument. */
		ARGUMENT_DECLARATION("ArgumentDeclaration", 0xB0FFF0),

		/** The declaration of a label in a block. */
		LABEL_DECLARATION("LabelDeclaration", 0x90E080),

		/** The use of a variable/constant. */
		VARIABLE_USE("VariableUse", 0x90FF90),


		/** The definition of some method, restriction, atom, etc. */
		DEFINITION("Definition", 0xE0E050),

		/** The name of the thing being defined. */
		DEFINITION_NAME("DefinitionName", 0xF0F080),

		/** The optional primitive name in a block. */
		PRIMITIVE_NAME("PrimitiveName", 0xF0FF50),


		/** An (extended) integer literal token. */
		INTEGER_LITERAL("IntegerLiteral", 0xC0F080),

		/** A [stringType] literal token. */
		STRING_LITERAL("StringLiteral", 0xF0C080),

		/** A [character][CharacterDescriptor] literal token. */
		CHARACTER_LITERAL("CharacterLiteral", 0x8090FF),

		/** A [float][FloatDescriptor] literal token. */
		FLOAT_LITERAL("FloatLiteral", 0xC0F0A0),

		/** A [double][DoubleDescriptor] literal token. */
		DOUBLE_LITERAL("DoubleLiteral", 0xC0F0FF);

		val string = stringFrom(kotlinString).makeShared()

		val color = Color(rgbInt)

		companion object
		{
			/** The map from each style name to its [BaseStyle]. */
			val stylesMap = values().associateByTo(
				mutableMapOf(), BaseStyle::string)
		}
	}

	override fun o_Hash(self: AvailObject): Int = combine4(
		self.slot(FUNCTION).hash(),
		self.slot(METHOD).hash(),
		self.slot(MODULE).hash(),
		0x443046b4)

	override fun o_Function(self: AvailObject): A_Function =
		self.slot(FUNCTION)

	override fun o_StylerMethod(self: AvailObject): A_Method =
		self.slot(METHOD)

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
		 * @param method
		 *   The [A_Method] that will hold this styler.
		 * @param module
		 *   The [module][ModuleDescriptor] in which this styler was defined.
		 * @return
		 *   The new styler, not yet installed.
		 */
		fun newStyler(
			function: A_Function,
			method: A_Method,
			module: A_Module
		): A_Styler = mutable.createShared {
			setSlot(FUNCTION, function)
			setSlot(METHOD, method)
			setSlot(MODULE, module)
		}
	}
}
