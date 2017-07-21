/**
 * StacksExtendsModule.java
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

package com.avail.stacks;

import java.util.HashMap;
import java.util.Map;

import com.avail.descriptor.A_String;

/**
 * A grouping of all implementationGroups originating from the names section of
 * this module that this is being extended by another module.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
public class StacksExtendsModule extends StacksImportModule
{

	/**
	 * Construct a new {@link StacksExtendsModule}.
	 * @param moduleImportName The name of the module
	 * @param implementationGroups
	 * 		The a map of {@linkplain ImplementationGroup implementationGroups}
	 * 		keyed by the implementation name.
	 * @param moduleNameToExtendsList
	 * 		A map of module names to other {@linkplain StacksExtendsModule
	 * 		modules} extended by this module.
	 * @param methodLeafNameToModuleName
	 * 		A map keyed by a method name with no path to the qualified module
	 * 		path it is originally named from.
	 * @param moduleNameToUsesList
	 * 		A map of module names to other {@linkplain StacksUsesModule modules}
	 * 		used by this module.
	 * @param usesMethodLeafNameToModuleName
	 * 		A map keyed by visible (uses) method names with no path to the
	 * 		qualified module path it is originally named from.
	 */
	public StacksExtendsModule (final String moduleImportName,
		final HashMap<A_String,ImplementationGroup> implementationGroups,
		final HashMap<String,StacksExtendsModule> moduleNameToExtendsList,
		final HashMap<A_String, HashMap<String, ImplementationGroup>>
			methodLeafNameToModuleName,
		final HashMap<String,StacksUsesModule> moduleNameToUsesList,
		final HashMap<A_String, HashMap<String, ImplementationGroup>>
			usesMethodLeafNameToModuleName)
	{
		super(moduleImportName,implementationGroups,moduleNameToExtendsList,
			methodLeafNameToModuleName, moduleNameToUsesList,
			usesMethodLeafNameToModuleName);
	}

	/**
	 * @param moduleNameToSearch
	 * 		The implementation to search for
	 * @return
	 * 		The {@linkplain StacksExtendsModule} the implementation belongs to.
	 */
	public StacksExtendsModule getExtendsModule(
		final String moduleNameToSearch)
	{
		if (moduleName.equals(moduleNameToSearch))
		{
			return this;
		}
		if (!(moduleNameToExtendsList().isEmpty()))
		{
			if (moduleNameToExtendsList().containsKey(moduleNameToSearch))
			{
				return moduleNameToExtendsList().get(moduleNameToSearch);
			}

			for (final StacksExtendsModule extendsModule :
				moduleNameToExtendsList().values())
			{
				return extendsModule
					.getExtendsModule(moduleNameToSearch);
			}
		}
		return null;
	}

	@Override
	public void addMethodImplementation (final A_String key,
		final MethodCommentImplementation implementation)
	{
		if (implementations().containsKey(key))
		{
			implementations().get(key).addMethod(implementation);
		}

		//Should only have one group, anything else would be an error in Avail.
		for (final ImplementationGroup group :
			extendsMethodLeafNameToModuleName().get(key).values())
		{
			group.addMethod(implementation);
		}
	}

	@Override
	public void addMacroImplementation (final A_String key,
		final MacroCommentImplementation implementation)
	{
		if (implementations().containsKey(key))
		{
			implementations().get(key).addMacro(implementation);
		}

		//Should only have one group, anything else would be an error in Avail.
		for (final ImplementationGroup group :
			extendsMethodLeafNameToModuleName().get(key).values())
		{
			group.addMacro(implementation);
		}
	}

	@Override
	public void addSemanticImplementation (final A_String key,
		final SemanticRestrictionCommentImplementation implementation)
	{
		if (implementations().containsKey(key))
		{
			implementations().get(key)
				.addSemanticRestriction(implementation);
		}

		//Should only have one group, anything else would be an error in Avail.
		for (final ImplementationGroup group :
			extendsMethodLeafNameToModuleName().get(key).values())
		{
			group.addSemanticRestriction(implementation);
		}
	}

	@Override
	public void addGrammaticalImplementation (final A_String key,
		final GrammaticalRestrictionCommentImplementation implementation)
	{
		if (implementations().containsKey(key))
		{
			implementations().get(key)
				.addGrammaticalRestriction(implementation);
		}

		//Should only have one group, anything else would be an error in Avail.
		for (final ImplementationGroup group :
			extendsMethodLeafNameToModuleName().get(key).values())
		{
			group.addGrammaticalRestriction(implementation);
		}
	}

	@Override
	public void addClassImplementationGroup (final A_String key,
		final ImplementationGroup classImplementationGroup)
	{
		if (implementations().containsKey(key))
		{
			implementations().put(key, classImplementationGroup);
		}

		//Shouldn't really happen
	}

	@Override
	public void addGlobalImplementationGroup (
		final A_String key,
		final ImplementationGroup globalImplementationGroup)
	{
		if (implementations().containsKey(key))
		{
			implementations().put(key, globalImplementationGroup);
		}

		//Shouldn't really happen
	}

	@Override
	public void renameImplementation (final A_String key,
		final A_String newName, final StacksCommentsModule newlyDefinedModule,
		final StacksFilename newFileName,
		final boolean deleteOriginal)
	{
		final Map<String, ImplementationGroup> groupMap =
			extendsMethodLeafNameToModuleName().get(key);

		if (groupMap != (null))
		{

			ImplementationGroup group = new ImplementationGroup(newName,
				newlyDefinedModule.moduleName(), newFileName, false);

			for (final ImplementationGroup aGroup : groupMap.values())
			{
				group =
					new ImplementationGroup(aGroup, newFileName,
						newlyDefinedModule.moduleName(), newName);
			}
			if (newlyDefinedModule.extendsMethodLeafNameToModuleName()
				.containsKey(newName))
			{
				newlyDefinedModule.namedPublicCommentImplementations().put(
					newName,
					group);
				newlyDefinedModule
					.extendsMethodLeafNameToModuleName()
					.get(newName)
					.put(newlyDefinedModule.moduleName(), group);
			} else
			{
				final HashMap<String, ImplementationGroup> newMap =
					new HashMap<>();

				newMap.put(newlyDefinedModule.moduleName(), group);

				newlyDefinedModule.extendsMethodLeafNameToModuleName().put(
					newName,
					newMap);
			}
			if (deleteOriginal)
			{
				extendsMethodLeafNameToModuleName().remove(key);
			}
		}
	}

	/**
	 * Construct a new {@link StacksExtendsModule} from a
	 * {@linkplain StacksCommentsModule}
	 * @param module
	 * 		The {@linkplain StacksCommentsModule} to be transformed
	 */
	public StacksExtendsModule (
		final StacksCommentsModule module)
	{

		super (module.moduleName(),
			new HashMap<>(
				module.namedPublicCommentImplementations()),
			new HashMap<>(
				module.extendedNamesImplementations()),
			new HashMap<>(
				module.extendsMethodLeafNameToModuleName()),
			module.usesNamesImplementations(),
			module.usesMethodLeafNameToModuleName());
	}

	@Override
	public String toString()
	{
		final StringBuilder stringBuilder = new StringBuilder().append("<h4>")
			.append(moduleName).append("</h4>")
			.append("<ol>");
		for (final A_String key : implementations().keySet())
		{
			stringBuilder.append("<li>").append(key.asNativeString())
				.append("</li>");
		}
		stringBuilder.append("</ol>");

		for (final StacksExtendsModule extendsModule :
			moduleNameToExtendsList().values())
		{
			stringBuilder.append(extendsModule.toString());
		}

		return stringBuilder.toString();
	}
}
