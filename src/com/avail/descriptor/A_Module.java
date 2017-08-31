/**
 * A_Module.java
 * Copyright © 1993-2017, The Avail Foundation, LLC.
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

package com.avail.descriptor;

import com.avail.interpreter.AvailLoader;
import com.avail.interpreter.AvailLoader.LexicalScanner;
import com.avail.interpreter.primitive.modules.P_PublishName;
import com.avail.utility.evaluation.Continuation0;

/**
 * {@code A_Module} is an interface that specifies the {@linkplain
 * ModuleDescriptor module}-specific operations that an {@link AvailObject} must
 * implement.  It's a sub-interface of {@link A_BasicObject}, the interface that
 * defines the behavior that all AvailObjects are required to support.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public interface A_Module
extends A_BasicObject
{
	/**
	 * @param moreAncestors
	 */
	void addAncestors (A_Set moreAncestors);

	/**
	 * @param name
	 * @param constantBinding
	 */
	void addConstantBinding (
		A_String name,
		A_Variable constantBinding);

	/**
	 * Dispatch to the descriptor.
	 */
	void addEntryPoint (A_String stringName, A_Atom trueName);

	/**
	 * @param trueName
	 */
	void addImportedName (A_Atom trueName);

	/**
	 * @param trueNames
	 */
	void addImportedNames (A_Set trueNames);

	/**
	 * @param lexer
	 */
	void addLexer (A_Lexer lexer);

	/**
	 * @param trueName
	 */
	void addPrivateName (A_Atom trueName);

	/**
	 * @param trueNames
	 */
	void addPrivateNames (A_Set trueNames);

	/**
	 * @param methodName
	 * @param sealSignature
	 */
	void addSeal (
		A_Atom methodName,
		A_Tuple sealSignature);

	/**
	 * Add the specified {@linkplain A_Function function} to the {@linkplain
	 * A_Tuple tuple} of functions that should be applied when the {@linkplain
	 * A_Module module} is unloaded.
	 *
	 * @param unloadFunction
	 *        A function.
	 */
	void addUnloadFunction (A_Function unloadFunction);

	/**
	 * Add a module variable binding to this module.
	 *
	 * @param name The string naming the variable binding.
	 * @param variableBinding The {@link A_Variable variable} itself.
	 */
	void addVariableBinding (
		A_String name,
		A_Variable variableBinding);

	/**
	 * Return the set of all ancestor modules of this module.  Include this
	 * module in the set.
	 *
	 * @return The set of all ancestors of this module, including itself.
	 */
	A_Set allAncestors ();

	/**
	 * Dispatch to the descriptor.
	 */
	A_BundleTree buildFilteredBundleTree ();

	/**
	 * Dispatch to the descriptor.
	 */
	A_Map constantBindings ();

	/**
	 * Create and answer a {@link LexicalScanner} containing all lexers that are
	 * in scope for this module.
	 *
	 * @return The new {@link LexicalScanner}.
	 */
	LexicalScanner createLexicalScanner ();

	/**
	 * Dispatch to the descriptor.
	 */
	A_Map entryPoints ();

	/**
	 * Answer the {@linkplain A_Set set} of all {@linkplain A_Atom names}
	 * exported by this module.
	 *
	 * @return The set of exported names.
	 */
	A_Set exportedNames ();

	/**
	 * Dispatch to the descriptor.
	 */
	A_Map importedNames ();

	/**
	 * Introduce a new atom into this module.
	 *
	 * @param trueName The atom to introduce.
	 */
	void introduceNewName (A_Atom trueName);

	/**
	 * Dispatch to the descriptor.
	 */
	A_Set methodDefinitions ();

	/**
	 * Add a {@link A_Definition definition} to this module.
	 *
	 * @param definition The definition to add.
	 */
	void moduleAddDefinition (A_BasicObject definition);

	/**
	 * Add a grammatical restriction to this module.
	 *
	 * @param grammaticalRestriction The grammatical restriction to add.
	 */
	void moduleAddGrammaticalRestriction (
		A_GrammaticalRestriction grammaticalRestriction);

	/**
	 * Add a semantic restriction to this module.
	 *
	 * @param semanticRestriction The semantic restriction to add.
	 */
	void moduleAddSemanticRestriction (
		A_SemanticRestriction semanticRestriction);

	/**
	 * Answer the name of this module.
	 *
	 * @return A {@linkplain StringDescriptor string} naming this module.
	 */
	A_String moduleName ();

	/**
	 * Answer this module's {@linkplain A_Set} of {@link
	 * A_GrammaticalRestriction}s.
	 *
	 * @return The set of grammatical restrictions defined by this module.
	 */
	A_Set moduleGrammaticalRestrictions ();

	/**
	 * Answer this module's {@linkplain A_Set} of {@linkplain
	 * A_SemanticRestriction}s.
	 *
	 * @return The set of semantic restrictions defined by this module.
	 */
	A_Set moduleSemanticRestrictions ();

	/**
	 * Dispatch to the descriptor.
	 */
	boolean nameVisible (A_Atom trueName);

	/**
	 * Answer a {@linkplain A_Map map} from {@linkplain A_String strings} to
	 * {@linkplain A_Atom atoms}. These atoms prevent or at least clarify name
	 * conflicts. These names are those introduced by the module's {@code
	 * "Names"} section or {@link P_PublishName}.
	 *
	 * @return The map of new names.
	 */
	A_Map newNames ();

	/**
	 * Dispatch to the descriptor.
	 */
	A_Map privateNames ();

	/**
	 * Dispatch to the descriptor.
	 */
	void removeFrom (AvailLoader aLoader, Continuation0 afterRemoval);

	/**
	 * Dispatch to the descriptor.
	 */
	void resolveForward (A_BasicObject forwardDefinition);

	/**
	 * Dispatch to the descriptor.
	 */
	A_Set trueNamesForStringName (A_String stringName);

	/**
	 * Dispatch to the descriptor.
	 */
	A_Map variableBindings ();

	/**
	 * Answer the {@linkplain SetDescriptor set} of acceptable version
	 * {@linkplain StringDescriptor strings} for which this module claims
	 * compatibility.  An empty set indicates universal compatibility.
	 *
	 * @return This module's set of acceptable version strings.
	 */
	A_Set versions ();

	/**
	 * Set this module's {@linkplain SetDescriptor set} of acceptable version
	 * {@linkplain StringDescriptor strings}.  Use an empty set to indicate
	 * universal compatibility.
	 *
	 * @param versionStrings A set of version strings.
	 */
	void versions (A_Set versionStrings);

	/**
	 * Dispatch to the descriptor.
	 */
	A_Set visibleNames ();
}
