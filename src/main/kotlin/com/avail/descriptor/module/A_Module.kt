/*
 * A_Module.kt
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
package com.avail.descriptor.module

import com.avail.compiler.ModuleHeader
import com.avail.descriptor.atoms.A_Atom
import com.avail.descriptor.bundles.A_Bundle
import com.avail.descriptor.bundles.A_BundleTree
import com.avail.descriptor.functions.A_Function
import com.avail.descriptor.maps.A_Map
import com.avail.descriptor.methods.A_Definition
import com.avail.descriptor.methods.A_GrammaticalRestriction
import com.avail.descriptor.methods.A_Macro
import com.avail.descriptor.methods.A_Method
import com.avail.descriptor.methods.A_SemanticRestriction
import com.avail.descriptor.numbers.A_Number
import com.avail.descriptor.parsing.A_Lexer
import com.avail.descriptor.phrases.A_Phrase
import com.avail.descriptor.phrases.BlockPhraseDescriptor
import com.avail.descriptor.representation.A_BasicObject
import com.avail.descriptor.representation.A_BasicObject.Companion.dispatch
import com.avail.descriptor.representation.AvailObject
import com.avail.descriptor.representation.NilDescriptor.Companion.nil
import com.avail.descriptor.sets.A_Set
import com.avail.descriptor.sets.SetDescriptor
import com.avail.descriptor.tuples.A_String
import com.avail.descriptor.tuples.A_Tuple
import com.avail.descriptor.tuples.StringDescriptor
import com.avail.descriptor.variables.A_Variable
import com.avail.exceptions.AvailRuntimeException
import com.avail.interpreter.execution.AvailLoader
import com.avail.interpreter.execution.AvailLoader.LexicalScanner
import com.avail.interpreter.primitive.modules.P_PublishName

/**
 * `A_Module` is an interface that specifies the
 * [module][ModuleDescriptor]-specific operations that an [AvailObject] must
 * implement.  It's a sub-interface of [A_BasicObject], the interface that
 * defines the behavior that all AvailObjects are required to support.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
interface A_Module : A_BasicObject
{
	companion object
	{
		/**
		 * Update this module with information that has been accumulated in the
		 * given [ModuleHeader].
		 *
		 * @param loader
		 *   The [AvailLoader] responsible for loading this module.
		 * @param moduleHeader
		 *   The [ModuleHeader] containing information to transfer to this
		 *   module.
		 * @return
		 *   An error message [String] if there was a problem, or `null` if no
		 *   problems were encountered.
		 */
		fun A_Module.applyModuleHeader(
			loader: AvailLoader,
			moduleHeader: ModuleHeader
		): String? = dispatch { o_ApplyModuleHeader(it, loader, moduleHeader) }

		/**
		 * TODO MvG Comment Me!
		 * @param name
		 * @param constantBinding
		 * @throws AvailRuntimeException
		 *   If the [module][A_Module] is already closed.
		 */
		@Throws(AvailRuntimeException::class)
		fun A_Module.addConstantBinding(
			name: A_String,
			constantBinding: A_Variable
		) = dispatch { o_AddConstantBinding(it, name, constantBinding) }

		/**
		 * TODO MvG Comment Me!
		 * @param trueName
		 * @throws AvailRuntimeException
		 *   If the [module][A_Module] is already closed.
		 */
		@Throws(AvailRuntimeException::class)
		fun A_Module.addImportedName(trueName: A_Atom) =
			dispatch { o_AddImportedName(it, trueName) }

		/**
		 * TODO MvG Comment Me!
		 * @param trueNames
		 * @throws AvailRuntimeException
		 *   If the [module][A_Module] is already closed.
		 */
		@Throws(AvailRuntimeException::class)
		fun A_Module.addImportedNames(trueNames: A_Set) =
			dispatch { o_AddImportedNames(it, trueNames) }

		/**
		 * TODO MvG Comment Me!
		 * @param lexer
		 * @throws AvailRuntimeException
		 *   If the [module][A_Module] is already closed.
		 */
		@Throws(AvailRuntimeException::class)
		fun A_Module.addLexer(lexer: A_Lexer) =
			dispatch { o_AddLexer(it, lexer) }

		/**
		 * TODO MvG Comment Me!
		 * @param trueName
		 * @throws AvailRuntimeException
		 *   If the [module][A_Module] is already closed.
		 */
		@Throws(AvailRuntimeException::class)
		fun A_Module.addPrivateName(trueName: A_Atom) =
			dispatch { o_AddPrivateName(it, trueName) }

		/**
		 * TODO MvG Comment Me!
		 * @param trueNames
		 * @throws AvailRuntimeException
		 *   If the [module][A_Module] is already closed.
		 */
		@Throws(AvailRuntimeException::class)
		fun A_Module.addPrivateNames(trueNames: A_Set) =
			dispatch { o_AddPrivateNames(it, trueNames) }

		/**
		 * TODO MvG Comment Me!
		 * @param methodName
		 * @param sealSignature
		 * @throws AvailRuntimeException
		 *   If the [module][A_Module] is already closed.
		 */
		@Throws(AvailRuntimeException::class)
		fun A_Module.addSeal(
			methodName: A_Atom,
			sealSignature: A_Tuple
		) = dispatch { o_AddSeal(it, methodName, sealSignature) }

		/**
		 * Add the specified [function][A_Function] to the [tuple][A_Tuple] of
		 * functions that should be applied when the [module][A_Module] is unloaded.
		 *
		 * @param unloadFunction
		 *   A function.
		 * @throws AvailRuntimeException
		 *   If the [module][A_Module] is already closed.
		 */
		@Throws(AvailRuntimeException::class)
		fun A_Module.addUnloadFunction(unloadFunction: A_Function) =
			dispatch { o_AddUnloadFunction(it, unloadFunction) }

		/**
		 * Add a module variable binding to this module.
		 *
		 * @param name
		 *   The string naming the variable binding.
		 * @param variableBinding
		 *   The [variable][A_Variable] itself.
		 * @throws AvailRuntimeException
		 *   If the [module][A_Module] is already closed.
		 */
		@Throws(AvailRuntimeException::class)
		fun A_Module.addVariableBinding(
			name: A_String,
			variableBinding: A_Variable
		) = dispatch { o_AddVariableBinding(it, name, variableBinding) }

		/**
		 * Return the set of all ancestor modules of this module. Exclude this
		 * module from the set.
		 *
		 * @return
		 *   The set of all ancestors of this module, including itself.
		 */
		fun A_Module.allAncestors(): A_Set = dispatch { o_AllAncestors(it) }

		/**
		 * Determine if the given module is equal to or an ancestor of the
		 * receiver.
		 *
		 * @param potentialAncestor
		 *   The [A_Module] to test for membership in the receiver's ancestry.
		 * @return
		 *   `true` if [potentialAncestor] equals or is an ancestor of `this`,
		 *   otherwise `false`.
		 */
		fun A_Module.hasAncestor(potentialAncestor: A_Module): Boolean =
			dispatch { o_HasAncestor(it, potentialAncestor) }

		/**
		 * Dispatch to the descriptor.
		 */
		fun A_Module.buildFilteredBundleTree(): A_BundleTree =
			dispatch { o_BuildFilteredBundleTree(it) }

		/**
		 * Dispatch to the descriptor.
		 */
		fun A_Module.constantBindings(): A_Map =
			dispatch { o_ConstantBindings(it) }

		/**
		 * Create and answer a [LexicalScanner] containing all lexers that are in
		 * scope for this module.
		 *
		 * @return
		 *   The new [LexicalScanner].
		 */
		fun A_Module.createLexicalScanner(): LexicalScanner =
			dispatch { o_CreateLexicalScanner(it) }

		/**
		 * Dispatch to the descriptor.
		 */
		fun A_Module.entryPoints(): A_Map =
			dispatch { o_EntryPoints(it) }

		/**
		 * Answer the [set][A_Set] of all [names][A_Atom] exported by this module.
		 *
		 * @return
		 *   The set of exported names.
		 */
		fun A_Module.exportedNames(): A_Set = dispatch { o_ExportedNames(it) }

		/**
		 * Dispatch to the descriptor.
		 */
		fun A_Module.importedNames(): A_Map = dispatch { o_ImportedNames(it) }

		/**
		 * Introduce a new atom into this module.
		 *
		 * @param trueName
		 *   The atom to introduce.
		 * @throws AvailRuntimeException
		 *   If the [module][A_Module] is already closed.
		 */
		@Throws(AvailRuntimeException::class)
		fun A_Module.introduceNewName(trueName: A_Atom) =
			dispatch { o_IntroduceNewName(it, trueName) }

		/**
		 * Dispatch to the descriptor.
		 */
		fun A_Module.methodDefinitions(): A_Set =
			dispatch { o_MethodDefinitions(it) }

		/**
		 * Add a [definition][A_Definition] to this module.
		 *
		 * @param definition
		 *   The definition to add.
		 * @throws AvailRuntimeException
		 *   If the [module][A_Module] is already closed.
		 */
		@Throws(AvailRuntimeException::class)
		fun A_Module.moduleAddDefinition(definition: A_Definition) =
			dispatch { o_ModuleAddDefinition(it, definition) }

		/**
		 * Add a grammatical restriction to this module.
		 *
		 * @param grammaticalRestriction
		 *   The grammatical restriction to add.
		 * @throws AvailRuntimeException
		 *   If the [module][A_Module] is already closed.
		 */
		@Throws(AvailRuntimeException::class)
		fun A_Module.moduleAddGrammaticalRestriction(
			grammaticalRestriction: A_GrammaticalRestriction
		) = dispatch {
			o_ModuleAddGrammaticalRestriction(it, grammaticalRestriction)
		}

		/**
		 * Add an [A_Macro] to this module.
		 *
		 * @param macro
		 *   The [A_Macro] to add.
		 */
		fun A_Module.moduleAddMacro(macro: A_Macro) =
			dispatch { o_ModuleAddMacro(it, macro) }

		/**
		 * Add a semantic restriction to this module.
		 *
		 * @param semanticRestriction
		 *   The semantic restriction to add.
		 * @throws AvailRuntimeException
		 *   If the [module][A_Module] is already closed.
		 */
		@Throws(AvailRuntimeException::class)
		fun A_Module.moduleAddSemanticRestriction(
			semanticRestriction: A_SemanticRestriction
		) = dispatch { o_ModuleAddSemanticRestriction(it, semanticRestriction) }

		/**
		 * Answer the name of this module.
		 *
		 * @return
		 *   A [string][StringDescriptor] naming this module.
		 */
		fun A_Module.moduleName(): A_String = dispatch { o_ModuleName(it) }

		/**
		 * Answer a [map][A_Map] from [strings][A_String] to [atoms][A_Atom].
		 * These atoms prevent or at least clarify name conflicts. These names
		 * are those introduced by the module's `"Names"` section or
		 * [P_PublishName].
		 *
		 * @return
		 *   The map of new names.
		 */
		fun A_Module.newNames(): A_Map = dispatch { o_NewNames(it) }

		/**
		 * Dispatch to the descriptor.
		 */
		fun A_Module.privateNames(): A_Map = dispatch { o_PrivateNames(it) }

		/**
		 * Dispatch to the descriptor.
		 */
		fun A_Module.removeFrom(loader: AvailLoader, afterRemoval: ()->Unit) =
			dispatch { o_RemoveFrom(it, loader, afterRemoval) }

		/**
		 * Dispatch to the descriptor.
		 */
		fun A_Module.resolveForward(forwardDefinition: A_BasicObject) =
			dispatch { o_ResolveForward(it, forwardDefinition) }

		/**
		 * Dispatch to the descriptor.
		 */
		fun A_Module.trueNamesForStringName(stringName: A_String): A_Set =
			dispatch { o_TrueNamesForStringName(it, stringName) }

		/**
		 * Dispatch to the descriptor.
		 */
		fun A_Module.variableBindings(): A_Map =
			dispatch { o_VariableBindings(it) }

		/**
		 * Answer the [set][SetDescriptor] of acceptable version
		 * [strings][StringDescriptor] for which this module claims
		 * compatibility. An empty set indicates universal compatibility.
		 *
		 * @return
		 *   This module's set of acceptable version strings.
		 */
		fun A_Module.versions(): A_Set = dispatch { o_Versions(it) }

		/**
		 * Dispatch to the descriptor.
		 */
		fun A_Module.visibleNames(): A_Set = dispatch { o_VisibleNames(it) }

		/**
		 * Add the given [A_Bundle] to this module.  It will be removed from its
		 * connected [A_Method] when this module is unloaded.
		 */
		fun A_Module.addBundle(bundle: A_Bundle) =
			dispatch { o_AddBundle(it, bundle) }

		/**
		 * Look up a one-based index in this module's tuple of block phrases.
		 * If the tuple is [nil], first fetch it from the repository and
		 * overwrite the field.
		 */
		fun A_Module.originatingPhraseAtIndex(index: Int): A_Phrase =
			dispatch { o_OriginatingPhraseAtIndex(it, index) }

		/**
		 * Record a [block&#32;phrase][BlockPhraseDescriptor] in this module,
		 * answering the unique one-based Avail integer index at which it can
		 * later be retrieved.
		 */
		fun A_Module.recordBlockPhrase(blockPhrase: A_Phrase): A_Number =
			dispatch { o_RecordBlockPhrase(it, blockPhrase) }

		/**
		 * Extract the module's tuple of block phrases that it accumulated
		 * during compilation.  Also set the field to nil.
		 */
		fun A_Module.getAndSetTupleOfBlockPhrases(
			newValue: AvailObject
		): AvailObject = dispatch {
			o_GetAndSetTupleOfBlockPhrases(it, newValue)
		}

		/**
		 * Deserialization has completed, and this is the [A_Tuple] of objects
		 * that were deserialized.  This tuple can be used for pumping
		 * serializers and deserializers of subsequent modules, as well as the
		 * separate repository record for block phrases, stacks comments, and
		 * any styling, navigation, or indexing information stored separately
		 * from the body record of the module.
		 */
		fun A_Module.serializedObjects(serializedObjects: A_Tuple) =
			dispatch { o_SerializedObjects(it, serializedObjects) }

		/**
		 * Serialization has completed, and this is the [A_Map] from the newly
		 * serialized objects to their local one-based index.  This map can be
		 * inverted to form a [tuple][A_Tuple], if needed, or vice-versa.
		 * Either can be used to populate the [filter]
		 */
		fun A_Module.serializedObjectsMap(serializedObjects: A_Map) =
			dispatch { o_SerializedObjectsMap(it, serializedObjects) }

		/**
		 * Read the current [state][ModuleDescriptor.State] of the module, which
		 * indicates whether the module is loading, unloading, or in a stable
		 * state.
		 */
		fun A_Module.moduleState() = dispatch { o_ModuleState(it) }

		/**
		 * Set the [state][ModuleDescriptor.State] of the module.  State changes
		 * are restricted by the state enum's directed graph of successors.
		 */
		fun A_Module.setModuleState(newState: ModuleDescriptor.State) =
			dispatch { o_SetModuleState(it, newState) }
	}
}
