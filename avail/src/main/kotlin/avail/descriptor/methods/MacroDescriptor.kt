/*
 * MacroDescriptor.kt
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

import avail.annotations.HideFieldJustForPrinting
import avail.descriptor.atoms.A_Atom.Companion.atomName
import avail.descriptor.atoms.AtomDescriptor.SpecialAtom
import avail.descriptor.bundles.A_Bundle
import avail.descriptor.bundles.A_Bundle.Companion.message
import avail.descriptor.bundles.A_Bundle.Companion.numArgs
import avail.descriptor.functions.A_Function
import avail.descriptor.functions.FunctionDescriptor
import avail.descriptor.methods.A_Sendable.Companion.bodyBlock
import avail.descriptor.methods.A_Sendable.Companion.definitionModuleName
import avail.descriptor.methods.DefinitionDescriptor.Companion.builtInNoModuleName
import avail.descriptor.methods.MacroDescriptor.ObjectSlots.BODY_BLOCK
import avail.descriptor.methods.MacroDescriptor.ObjectSlots.BUNDLE
import avail.descriptor.methods.MacroDescriptor.ObjectSlots.MACRO_PREFIX_FUNCTIONS
import avail.descriptor.methods.MacroDescriptor.ObjectSlots.MODULE
import avail.descriptor.module.A_Module
import avail.descriptor.module.A_Module.Companion.moduleName
import avail.descriptor.module.ModuleDescriptor
import avail.descriptor.numbers.A_Number.Companion.extractInt
import avail.descriptor.phrases.ListPhraseDescriptor
import avail.descriptor.phrases.PhraseDescriptor
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.AvailObject
import avail.descriptor.representation.AvailObject.Companion.combine2
import avail.descriptor.representation.Descriptor
import avail.descriptor.representation.Mutability
import avail.descriptor.representation.ObjectSlotsEnum
import avail.descriptor.tuples.A_String
import avail.descriptor.tuples.A_Tuple
import avail.descriptor.types.A_Type
import avail.descriptor.types.A_Type.Companion.argsTupleType
import avail.descriptor.types.A_Type.Companion.lowerBound
import avail.descriptor.types.A_Type.Companion.phraseTypeExpressionType
import avail.descriptor.types.A_Type.Companion.sizeRange
import avail.descriptor.types.A_Type.Companion.upperBound
import avail.descriptor.types.ListPhraseTypeDescriptor
import avail.descriptor.types.PhraseTypeDescriptor.PhraseKind
import avail.descriptor.types.PrimitiveTypeDescriptor.Types
import avail.descriptor.types.TupleTypeDescriptor.Companion.mappingElementTypes
import avail.descriptor.types.TypeTag
import avail.serialization.SerializerOperation
import org.availlang.json.JSONWriter

/**
 * Macros are extremely hygienic in Avail.  They are defined almost exactly like
 * ordinary multimethods.  The first difference is which primitive is used to
 * define a macro versus a method.  The other difference is that instead of
 * generating code at an occurrence to call a method (a call site), the macro
 * body is immediately invoked, passing the [phrases][PhraseDescriptor] that
 * occupy the corresponding argument positions in the method/macro name. The
 * macro body will then do what it does and return a suitable replacement
 * phrase.
 *
 * Instead of returning a new phrase, a macro body may instead reject parsing,
 * the same way a [semantic&#32;restriction][SemanticRestrictionDescriptor] may.
 * As you might expect, the diagnostic message provided to the parse rejection
 * primitive will be presented to the user.
 *
 * As with methods, repeated arguments of macros are indicated with guillemets
 * (`«»`) and the double-dagger (`‡`).  The type of such an argument for a
 * method is a tuple of tuples whose elements correspond to the underscores
 * (`_`) and guillemet groups contained therein.  When exactly one underscore or
 * guillemet group occurs within a group, then a simple tuple of values is
 * expected (rather than a tuple of size-one tuples).  Macros expect tuples in
 * an analogous way, but (1) the bottom-level pieces are always phrases, and (2)
 * the grouping is actually via [list&#32;phrases][ListPhraseDescriptor] rather
 * than tuples.  Thus, a macro always operates on phrases.
 *
 * @constructor
 *
 * @param mutability
 *   The [mutability][Mutability] of the new descriptor.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
class MacroDescriptor private constructor(
	mutability: Mutability
) : Descriptor(mutability, TypeTag.MACRO_TAG, ObjectSlots::class.java, null) {
	/**
	 * The layout of object slots for my instances.
	 */
	enum class ObjectSlots : ObjectSlotsEnum {
		/**
		 * The [A_Bundle] for which this macro is defined.
		 */
		BUNDLE,

		/**
		 * The [module][ModuleDescriptor] in which this definition occurs.
		 */
		@HideFieldJustForPrinting
		MODULE,

		/**
		 * The [function][FunctionDescriptor] to invoke to transform the
		 * (complete) argument phrases into a suitable replacement phrase.
		 */
		BODY_BLOCK,

		/**
		 * A [tuple][A_Tuple] of [functions][A_Function] corresponding with
		 * occurrences of section checkpoints (`"§"`) in the message name.  Each
		 * function takes the collected argument phrases thus far, and has the
		 * opportunity to reject the parse or read/write parse-specific
		 * information in a fiber-specific variable with the key
		 * [SpecialAtom.CLIENT_DATA_GLOBAL_KEY].
		 */
		@HideFieldJustForPrinting
		MACRO_PREFIX_FUNCTIONS;
	}

	override fun o_DefinitionModule(self: AvailObject): A_Module =
		self[MODULE]

	override fun o_DefinitionModuleName(self: AvailObject): A_String
	{
		val module: A_Module = self[MODULE]
		return if (module.isNil) {
			builtInNoModuleName
		}
		else
		{
			module.moduleName
		}
	}

	override fun o_Equals(self: AvailObject, another: A_BasicObject) =
		another.traversed().sameAddressAs(self)

	override fun o_BodyBlock(self: AvailObject): A_Function =
		self[BODY_BLOCK]

	override fun o_BodySignature(self: AvailObject): A_Type =
		self[BODY_BLOCK].kind()

	override fun o_DefinitionBundle(self: AvailObject): A_Bundle =
		self[BUNDLE]

	override fun o_Hash(self: AvailObject): Int =
		combine2(self.bodyBlock().hash(), 0x67f6ec56)

	override fun o_IsAbstractDefinition(self: AvailObject): Boolean = false

	override fun o_IsForwardDefinition(self: AvailObject): Boolean = false

	override fun o_IsMethodDefinition(self: AvailObject): Boolean = false

	override fun o_Kind(self: AvailObject): A_Type = Types.MACRO_DEFINITION.o

	override fun o_ParsingSignature(self: AvailObject): A_Type
	{
		// A macro definition's parsing signature is a list phrase type whose
		// covariant subexpressions type is the body block's kind's arguments
		// type.
		val argsTupleType = self[BODY_BLOCK].kind().argsTupleType
		val sizes = argsTupleType.sizeRange
		assert(sizes.lowerBound.extractInt
			== sizes.upperBound.extractInt)
		assert(sizes.lowerBound.extractInt == self[BUNDLE].numArgs)
		return ListPhraseTypeDescriptor.createListPhraseType(
			PhraseKind.LIST_PHRASE,
			mappingElementTypes(argsTupleType) {
				it.phraseTypeExpressionType
			},
			argsTupleType)
	}

	override fun o_PrefixFunctions(self: AvailObject): A_Tuple =
		self[MACRO_PREFIX_FUNCTIONS]

	override fun o_SerializerOperation(self: AvailObject) =
		SerializerOperation.MACRO_DEFINITION

	override fun o_WriteTo(self: AvailObject, writer: JSONWriter) =
		writer.writeObject {
			at("kind") { write("macro definition") }
			at("definition method") {
				self.definitionBundle().message.atomName.writeTo(writer)
			}
			at("definition module") {
				self.definitionModuleName().writeTo(writer)
			}
			at("body block") { self[BODY_BLOCK].writeTo(writer) }
			at("macro prefix functions") {
				self[MACRO_PREFIX_FUNCTIONS].writeTo(writer)
			}
		}

	override fun o_WriteSummaryTo(self: AvailObject, writer: JSONWriter) =
		writer.writeObject {
			at("kind") { write("macro definition") }
			at("definition method") {
				self.definitionBundle().message.atomName.writeTo(writer)
			}
			at("definition module") {
				self.definitionModuleName().writeTo(writer)
			}
			at("body block") { self[BODY_BLOCK].writeSummaryTo(writer) }
			at("macro prefix functions") {
				self[MACRO_PREFIX_FUNCTIONS].writeSummaryTo(writer)
			}
		}

	override fun mutable() = mutable

	// There is no immutable variant.
	override fun immutable() = shared

	override fun shared() = shared

	companion object {
		/**
		 * Create a new macro signature from the provided argument.
		 *
		 * @param bundle
		 *   The [A_Bundle] that names the macro.
		 * @param definitionModule
		 *   The module in which this macro is added.
		 * @param bodyBlock
		 *   The body of the signature.  This will be invoked when a call site
		 *   is compiled, passing the subexpressions
		 *   ([phrases][PhraseDescriptor]) as arguments.
		 * @param prefixFunctions
		 *   The tuple of prefix functions that correspond with the section
		 *   checkpoints (`"§"`) in the macro's name.
		 * @return
		 *   A macro definition.
		 */
		fun newMacroDefinition(
			bundle: A_Bundle,
			definitionModule: A_Module,
			bodyBlock: A_Function,
			prefixFunctions: A_Tuple
		): A_Macro = mutable.createShared {
			setSlot(BUNDLE, bundle)
			setSlot(MODULE, definitionModule)
			setSlot(BODY_BLOCK, bodyBlock)
			setSlot(MACRO_PREFIX_FUNCTIONS, prefixFunctions)
		}

		/** The mutable [MacroDescriptor]. */
		private val mutable = MacroDescriptor(Mutability.MUTABLE)

		/** The shared [MacroDescriptor]. */
		private val shared = MacroDescriptor(Mutability.SHARED)
	}
}
