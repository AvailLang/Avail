/**
 * A_Module.java
 * Copyright © 1993-2013, Mark van Gulik and Todd L Smith.
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
	 * @return
	 */
	boolean isSystemModule ();

	/**
	 * @param isSystemModule
	 */
	void isSystemModule (boolean isSystemModule);

	/**
	 * @return
	 */
	A_String moduleName ();

	/**
	 * @param value
	 */
	void versions (A_BasicObject value);

	/**
	 * @return
	 */
	A_Set versions ();

	/**
	 * Dispatch to the descriptor.
	 */
	A_Map newNames ();

	/**
	 * @param stringName
	 * @param trueName
	 */
	void addImportedName (
		A_String stringName,
		A_Atom trueName);

	/**
	 * @param stringName
	 * @param trueName
	 */
	void introduceNewName (
		A_String stringName,
		A_Atom trueName);

	/**
	 * @param stringName
	 * @param trueName
	 */
	void addPrivateName (
		A_String stringName,
		A_Atom trueName);

	/**
	 * Dispatch to the descriptor.
	 */
	A_Map importedNames ();

	/**
	 * Dispatch to the descriptor.
	 */
	A_Map privateNames ();

	/**
	 * Dispatch to the descriptor.
	 */
	A_Set visibleNames ();

	/**
	 * Dispatch to the descriptor.
	 */
	A_Set methodDefinitions ();

	/**
	 * Dispatch to the descriptor.
	 */
	A_Map variableBindings ();
	/**
	 * @param methodNameAtom
	 * @param typeRestrictionFunction
	 */
	void addTypeRestriction (
		A_Atom methodNameAtom,
		A_Function typeRestrictionFunction);

	/**
	 * @param name
	 * @param constantBinding
	 */
	void addConstantBinding (
		A_String name,
		A_BasicObject constantBinding);

	/**
	 * Dispatch to the descriptor.
	 */
	A_Map constantBindings ();

	/**
	 * @param methodName
	 * @param illegalArgMsgs
	 */
	void addGrammaticalRestrictions (
		A_Atom methodName,
		A_Tuple illegalArgMsgs);

	/**
	 * @param definition
	 */
	void moduleAddDefinition (A_BasicObject definition);

	/**
	 * @param methodName
	 * @param sealSignature
	 */
	void addSeal (
		A_Atom methodName,
		A_Tuple sealSignature);

	/**
	 * @param name
	 * @param variableBinding
	 */
	void addVariableBinding (
		A_String name,
		A_BasicObject variableBinding);

	/**
	 * @return
	 */
	int allocateFromCounter ();

	/**
	 * Dispatch to the descriptor.
	 */
	void cleanUpAfterCompile ();

	/**
	 * Dispatch to the descriptor.
	 */
	A_BundleTree buildFilteredBundleTree ();

	/**
	 * Dispatch to the descriptor.
	 */
	boolean nameVisible (A_Atom trueName);

	/**
	 * Dispatch to the descriptor.
	 */
	void resolveForward (A_BasicObject forwardDefinition);

	/**
	 * Dispatch to the descriptor.
	 */
	void removeFrom (AvailLoader aLoader);

	/**
	 * Dispatch to the descriptor.
	 */
	A_Set trueNamesForStringName (A_String stringName);

	/**
	 * Dispatch to the descriptor.
	 */
	A_Map entryPoints ();

	/**
	 * Dispatch to the descriptor.
	 */
	void addEntryPoint (A_String stringName, A_Atom trueName);
}
