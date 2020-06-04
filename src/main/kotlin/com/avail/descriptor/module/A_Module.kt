/*
 * A_Module.java
 * Copyright © 1993-2020, The Avail Foundation, LLC.
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

import com.avail.descriptor.atoms.A_Atom
import com.avail.descriptor.bundles.A_BundleTree
import com.avail.descriptor.functions.A_Function
import com.avail.descriptor.maps.A_Map
import com.avail.descriptor.methods.A_Definition
import com.avail.descriptor.methods.A_GrammaticalRestriction
import com.avail.descriptor.methods.A_SemanticRestriction
import com.avail.descriptor.parsing.A_Lexer
import com.avail.descriptor.representation.A_BasicObject
import com.avail.descriptor.representation.AvailObject
import com.avail.descriptor.sets.A_Set
import com.avail.descriptor.sets.SetDescriptor
import com.avail.descriptor.tuples.A_String
import com.avail.descriptor.tuples.A_Tuple
import com.avail.descriptor.tuples.StringDescriptor
import com.avail.descriptor.variables.A_Variable
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
	/**
	 * TODO MvG Comment Me!
	 * @param moreAncestors
	 */
	fun addAncestors(moreAncestors: A_Set)

	/**
	 * TODO MvG Comment Me!
	 * @param name
	 * @param constantBinding
	 */
	fun addConstantBinding(
		name: A_String,
		constantBinding: A_Variable)

	/**
	 * Dispatch to the descriptor.
	 */
	fun addEntryPoint(stringName: A_String, trueName: A_Atom)

	/**
	 * TODO MvG Comment Me!
	 * @param trueName
	 */
	fun addImportedName(trueName: A_Atom)

	/**
	 * TODO MvG Comment Me!
	 * @param trueNames
	 */
	fun addImportedNames(trueNames: A_Set)

	/**
	 * TODO MvG Comment Me!
	 * @param lexer
	 */
	fun addLexer(lexer: A_Lexer)

	/**
	 * TODO MvG Comment Me!
	 * @param trueName
	 */
	fun addPrivateName(trueName: A_Atom)

	/**
	 * TODO MvG Comment Me!
	 * @param trueNames
	 */
	fun addPrivateNames(trueNames: A_Set)

	/**
	 * TODO MvG Comment Me!
	 * @param methodName
	 * @param sealSignature
	 */
	fun addSeal(
		methodName: A_Atom,
		sealSignature: A_Tuple)

	/**
	 * Add the specified [function][A_Function] to the [tuple][A_Tuple] of
	 * functions that should be applied when the [module][A_Module] is unloaded.
	 *
	 * @param unloadFunction
	 *   A function.
	 */
	fun addUnloadFunction(unloadFunction: A_Function)

	/**
	 * Add a module variable binding to this module.
	 *
	 * @param name
	 *   The string naming the variable binding.
	 * @param variableBinding
	 *   The [variable][A_Variable] itself.
	 */
	fun addVariableBinding(
		name: A_String,
		variableBinding: A_Variable)

	/**
	 * Return the set of all ancestor modules of this module. Include this
	 * module in the set.
	 *
	 * @return
	 *   The set of all ancestors of this module, including itself.
	 */
	fun allAncestors(): A_Set

	/**
	 * Dispatch to the descriptor.
	 */
	fun buildFilteredBundleTree(): A_BundleTree

	/**
	 * Dispatch to the descriptor.
	 */
	fun constantBindings(): A_Map

	/**
	 * Create and answer a [LexicalScanner] containing all lexers that are in
	 * scope for this module.
	 *
	 * @return
	 *   The new [LexicalScanner].
	 */
	fun createLexicalScanner(): LexicalScanner

	/**
	 * Dispatch to the descriptor.
	 */
	fun entryPoints(): A_Map

	/**
	 * Answer the [set][A_Set] of all [names][A_Atom] exported by this module.
	 *
	 * @return
	 *   The set of exported names.
	 */
	fun exportedNames(): A_Set

	/**
	 * Dispatch to the descriptor.
	 */
	fun importedNames(): A_Map

	/**
	 * Introduce a new atom into this module.
	 *
	 * @param trueName
	 *   The atom to introduce.
	 */
	fun introduceNewName(trueName: A_Atom)

	/**
	 * Dispatch to the descriptor.
	 */
	fun methodDefinitions(): A_Set

	/**
	 * Add a [definition][A_Definition] to this module.
	 *
	 * @param definition
	 *   The definition to add.
	 */
	fun moduleAddDefinition(definition: A_Definition)

	/**
	 * Add a grammatical restriction to this module.
	 *
	 * @param grammaticalRestriction
	 *   The grammatical restriction to add.
	 */
	fun moduleAddGrammaticalRestriction(
		grammaticalRestriction: A_GrammaticalRestriction)

	/**
	 * Add a semantic restriction to this module.
	 *
	 * @param semanticRestriction
	 *   The semantic restriction to add.
	 */
	fun moduleAddSemanticRestriction(
		semanticRestriction: A_SemanticRestriction)

	/**
	 * Answer the name of this module.
	 *
	 * @return
	 *   A [string][StringDescriptor] naming this module.
	 */
	fun moduleName(): A_String

	/**
	 * Answer this module's [A_Set] of [A_GrammaticalRestriction]s.
	 *
	 * @return
	 *   The set of grammatical restrictions defined by this module.
	 */
	fun moduleGrammaticalRestrictions(): A_Set

	/**
	 * Answer this module's [A_Set] of [A_SemanticRestriction]s.
	 *
	 * @return
	 *   The set of semantic restrictions defined by this module.
	 */
	fun moduleSemanticRestrictions(): A_Set

	/**
	 * Dispatch to the descriptor.
	 */
	fun nameVisible(trueName: A_Atom): Boolean

	/**
	 * Answer a [map][A_Map] from [strings][A_String] to [atoms][A_Atom]. These
	 * atoms prevent or at least clarify name conflicts. These names are those
	 * introduced by the module's `"Names"` section or [P_PublishName].
	 *
	 * @return
	 *   The map of new names.
	 */
	fun newNames(): A_Map

	/**
	 * Dispatch to the descriptor.
	 */
	fun privateNames(): A_Map

	/**
	 * Dispatch to the descriptor.
	 */
	fun removeFrom(loader: AvailLoader, afterRemoval: () -> Unit)

	/**
	 * Dispatch to the descriptor.
	 */
	fun resolveForward(forwardDefinition: A_BasicObject)

	/**
	 * Dispatch to the descriptor.
	 */
	fun trueNamesForStringName(stringName: A_String): A_Set

	/**
	 * Dispatch to the descriptor.
	 */
	fun variableBindings(): A_Map

	/**
	 * Answer the [set][SetDescriptor] of acceptable version
	 * [strings][StringDescriptor] for which this module claims compatibility.
	 * An empty set indicates universal compatibility.
	 *
	 * @return
	 *   This module's set of acceptable version strings.
	 */
	fun versions(): A_Set

	/**
	 * Set this module's [set][SetDescriptor] of acceptable version
	 * [strings][StringDescriptor].  Use an empty set to indicate universal
	 * compatibility.
	 *
	 * @param versionStrings A set of version strings.
	 */
	fun setVersions(versionStrings: A_Set)

	/**
	 * Dispatch to the descriptor.
	 */
	fun visibleNames(): A_Set
}
