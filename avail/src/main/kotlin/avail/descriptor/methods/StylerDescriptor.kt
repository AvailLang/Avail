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
import avail.descriptor.methods.A_Styler.Companion.stylerFunctionType
import avail.descriptor.methods.StylerDescriptor.ObjectSlots.FUNCTION
import avail.descriptor.methods.StylerDescriptor.ObjectSlots.METHOD
import avail.descriptor.methods.StylerDescriptor.ObjectSlots.MODULE
import avail.descriptor.module.A_Module
import avail.descriptor.module.ModuleDescriptor
import avail.descriptor.phrases.MacroSubstitutionPhraseDescriptor
import avail.descriptor.phrases.SendPhraseDescriptor
import avail.descriptor.phrases.VariableUsePhraseDescriptor
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.AvailObject
import avail.descriptor.representation.AvailObject.Companion.combine4
import avail.descriptor.representation.Descriptor
import avail.descriptor.representation.Mutability
import avail.descriptor.representation.ObjectSlotsEnum
import avail.descriptor.tuples.A_String
import avail.descriptor.tuples.StringDescriptor.Companion.stringFrom
import avail.descriptor.types.TypeTag

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
		 * accept the original send phrase being styled, plus the phrase that
		 * was produced by a macro, if one was applied, otherwise the original
		 * send phrase again.  Most functions are only interested in the first
		 * argument, but some, like the variable-use macro's styler, require
		 * access to the resulting [variable-use][VariableUsePhraseDescriptor]
		 * phrase, to get to the declaration that it's a use of, so that it can
		 * determine if it should be styled as a use of a constant, variable,
		 * function argument, etc.
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

	/**
	 * The bootstrap styles, applied directly by the virtual machine or endorsed
	 * standard library.
	 *
	 * @author Leslie Schultz &lt;leslie@availlang.org&gt;
	 * @author Todd L Smith &lt;todd@availlang.org&gt;
	 */
	enum class SystemStyle(val kotlinString: String)
	{
		/**
		 * The characters and whitespace that define the boundaries and sections
		 * of an Avail code block. Note that Avail block delimiters ([ and ])
		 * are not usually part of an enveloping expression; this contrasts with
		 * other programming languages that require parentheses around an `if`
		 * condition and/or braces around the `then` block. In these languages,
		 * the delimiters are part of the expression itself. In Avail, certain
		 * contexts may accept either a single compact expression or an entire
		 * delimited block to evaluate to a single value, so the delimiters are
		 * always interpreted as part of the contents, and not part of the
		 * context.
		 */
		BLOCK("#block"),

		/**
		 * The characters and whitespace that signal a method is being defined,
		 * excluding the method name and definition block.
		 */
		METHOD_DEFINITION("#method-definition"),

		/**
		 * The code establishing a method name, or identifying the structure of
		 * a method call within a string literal used as part of a method name
		 * (i.e. method name metacharacters). In Avail code, methods may be
		 * named using any string- or atom-valued expression. In practice, it
		 * would make little sense to style an expression or block used to
		 * calculate a method name in a special way just because it is used
		 * inline in a method definition, so for the purposes of styling, only
		 * string literals, atom literals, atom creation sends that embed a
		 * string literal, and uses of single string or atom variables or
		 * constants will be classified this way. When a lexical element is
		 * classified as a METHOD_NAME, that style is applied to all characters
		 * that are outside the string literal AND all method name
		 * metacharacters that are inside the string literal, including the
		 * meta-escape-character ` but not the characters being escaped.
		 */
		METHOD_NAME("#method-name"),

		/**
		 * The name of a parameter being declared. Does not include its type or
		 * value.
		 */
		PARAMETER_DEFINITION("#parameter-definition"),

		/**
		 * The usage of a parameter.
		 */
		PARAMETER_USE("#parameter-use"),

		/**
		 * The elements of a method name that indicate that method is being
		 * called, but not any arguments being passed.
		 */
		METHOD_SEND("#method-send"),

		/**
		 * The elements of a macro name that indicate that macro is being
		 * called, but not any arguments being passed.
		 */
		MACRO_SEND("#macro-send"),

		/**
		 * The terminator that indicates the immediately preceding code was a
		 * statement.
		 */
		STATEMENT("#statement"),

		/**
		 * The type annotation within a definition.
		 */
		TYPE("#type"),

		/**
		 * The type annotation within a definition, whenever the defined
		 * element is type-valued.
		 */
		METATYPE("#metatype"),

		/**
		 * The type annotation within a definition, whenever the defined
		 * element is phrase-valued.
		 */
		PHRASE_TYPE("#phrase-type"),

		/**
		 * The header keywords that appear in the header of a module.
		 */
		MODULE_HEADER("#module-header"),

		/**
		 * The entire module header, including whitespace and comments.
		 */
		MODULE_HEADER_REGION("#module-header-region"),

		/**
		 * A module version, i.e., a string literal within a module header
		 * `Version` section.
		 */
		VERSION("#version"),

		/**
		 * A module import, i.e., a string literal within a module header `Uses`
		 * section.
		 */
		IMPORT("#import"),

		/**
		 * A module export, i.e., a string literal within a module header
		 * `Extends` or `Names` section.
		 */
		EXPORT("#export"),

		/**
		 * A module entry point, i.e., a string literal within a module header
		 * `Entries` section.
		 */
		ENTRY_POINT("#entry-point"),

		/**
		 * A pragma, i.e., a string literal within the module header `Pragma`
		 * section.
		 */
		PRAGMA("#pragma"),

		/**
		 * Line and block comments, including delimiters.
		 */
		COMMENT("#comment"),

		/**
		 * Documentation blocks, including delimiters and documentation tag
		 * contents.
		 */
		DOCUMENTATION("#documentation"),

		/**
		 * Tags used inside documentation blocks, but not their contents.
		 */
		DOCUMENTATION_TAG("#documentation-tag"),

		/**
		 * The name of a module constant at its definition site.
		 */
		MODULE_CONSTANT_DEFINITION("#module-constant-definition"),

		/**
		 * A use of a module constant.
		 */
		MODULE_CONSTANT_USE("#module-constant-use"),

		/**
		 * The name of a module variable at its definition site.
		 */
		MODULE_VARIABLE_DEFINITION("#module-variable-definition"),

		/**
		 * A use of a module variable.
		 */
		MODULE_VARIABLE_USE("#module-variable-use"),

		/**
		 * The name of a primitive failure reason constant at its definition
		 * site.
		 */
		PRIMITIVE_FAILURE_REASON_DEFINITION(
			"#primitive-failure-reason-definition"),

		/**
		 * A use of a primitive failure reason constant.
		 */
		PRIMITIVE_FAILURE_REASON_USE("#primitive-failure-reason-definition"),

		/**
		 * The name of a local constant at its definition site.
		 */
		LOCAL_CONSTANT_DEFINITION("#local-constant-definition"),

		/**
		 * A use of a local constant.
		 */
		LOCAL_CONSTANT_USE("#local-constant-use"),

		/**
		 * The name of a local variable at its definition site.
		 */
		LOCAL_VARIABLE_DEFINITION("#local-variable-definition"),

		/**
		 * A use of a local variable.
		 */
		LOCAL_VARIABLE_USE("#local-variable-use"),

		/**
		 * The label that identifies the enclosing continuation.
		 */
		LABEL_DEFINITION("#label-definition"),

		/**
		 * A use of a label.
		 */
		LABEL_USE("#label-use"),

		/**
		 * A string literal, excluding quotation marks around it. Does not
		 * include escape sequences.
		 */
		STRING_LITERAL("#string-literal"),

		/**
		 * A string escape sequence, including both the escape character \ and
		 * the character(s) that follow it that are being escaped. *All* escape
		 * sequences are styled similarly, even \\ and \", which serve to mark
		 * the escape character \ and the delimiter " as actual characters
		 * within the string. Also used for delimiting quotes.
		 */
		STRING_ESCAPE_SEQUENCE("#string-escape-sequence"),

		/**
		 * A numeric literal, including digits, punctuation, and numeric control
		 * characters that indicate base or format (such as 0x, f, e, etc.).
		 */
		NUMERIC_LITERAL("#numeric-literal"),

		/**
		 * A boolean literal (`true` or `false`).
		 */
		BOOLEAN_LITERAL("#boolean-literal"),

		/**
		 * The fixed syntax used to construct a tuple, but not its elements.
		 */
		TUPLE_CONSTRUCTOR("#tuple-literal"),

		/**
		 * The fixed syntax used to construct a set, but not its elements.
		 */
		SET_CONSTRUCTOR("#set-literal"),

		/**
		 * The fixed syntax used to construct a map, but not its keys or values.
		 */
		MAP_CONSTRUCTOR("#set-literal"),

		/**
		 * A character literal, including delimiters.
		 */
		CHARACTER_LITERAL("#character-literal"),

		/**
		 * An atom literal, including delimiters. May be overridden if more
		 * contextual information is available about the atom's function, e.g.,
		 * if it serves as a method name, object type field, etc.
		 */
		ATOM_LITERAL("#atom-literal"),

		/**
		 * A literal of some other Avail datatype than string, number, boolean,
		 * character, or atom.
		 */
		OTHER_LITERAL("#other-literal"),

		/**
		 * The fixed syntax indicating a conditional expression, but not its
		 * arguments, i.e., predicates or governed blocks.
		 */
		CONDITIONAL("#conditional"),

		/**
		 * The fixed syntax indicating a loop expression, but not its arguments,
		 * i.e., predicates or governed blocks.
		 */
		LOOP("#loop"),

		/**
		 * The characters and whitespace that signal a lexer is being defined,
		 * excluding the lexer name and definition block.
		 */
		LEXER_DEFINITION("#lexer-definition"),

		/**
		 * The characters and whitespace that signal a macro is being defined,
		 * excluding the macro name and definition block.
		 */
		MACRO_DEFINITION("#macro-definition"),

		/**
		 * The characters and whitespace that signal a semantic restriction is
		 * being defined for a method, excluding the method name and definition
		 * block.
		 */
		SEMANTIC_RESTRICTION_DEFINITION("#semantic-restriction-definition"),

		/**
		 * The characters and whitespace that signal a grammatical restriction
		 * is being defined for a method or macro, excluding the name and
		 * definition block.
		 */
		GRAMMATICAL_RESTRICTION_DEFINITION(
			"#grammatical-restriction-definition"),

		/**
		 * The characters and whitespace that signal a seal is being placed,
		 * excluding the method name and signature types that the seal envelops.
		 */
		SEAL_DEFINITION("#seal-definition"),

		/**
		 * The characters and whitespace that signal an object type is being
		 * defined, excluding any defined names.
		 */
		OBJECT_TYPE_DEFINITION("#object-type-definition"),

		/**
		 * The characters and whitespace that signal a special object is being
		 * bound or accessed, excluding any literals (like name or ordinal).
		 */
		SPECIAL_OBJECT("#special-object-definition"),

		/** The optional primitive name in a block. */
		PRIMITIVE_NAME("#primitive-name"),

		/**
		 * The fixed syntax used to construct a phrase.
		 */
		PHRASE("#phrase"),

		/**
		 * The return value of a block.
		 */
		RETURN_VALUE("#return-value"),

		/**
		 * The fixed syntax used to express a nonlocal control action, e.g.,
		 * raising an exception, restarting or exiting a continuation, etc.
		 */
		NONLOCAL_CONTROL("#nonlocal-control"),

		/**
		 * The exponent in an exponential expression like `b` in `a^b`.
		 */
		MATH_EXPONENT("#math-exponent"),

		/** The token should be visually ignored. */
		DEEMPHASIZE("#deemphasize"),

		/** The token should indicate it is being excluded from something. */
		EXCLUDED("#excluded");

		/** The Avail [A_String] version of the [kotlinString]. */
		val string = stringFrom(kotlinString).makeShared()
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
		 *   The [function][A_Function] to run against a call site's send phrase
		 *   and its transformed phrase, to generate styles.
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
