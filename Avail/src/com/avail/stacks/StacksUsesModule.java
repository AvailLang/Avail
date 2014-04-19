/**
 * StacksUsesModule.java
 * Copyright © 1993-2014, The Avail Foundation, LLC.
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
import com.avail.descriptor.A_Map;
import com.avail.descriptor.A_String;
import com.avail.descriptor.MapDescriptor;
import com.avail.descriptor.StringDescriptor;

/**
 * A grouping of all implementationGroups originating from the names section of
 * this module that this is being used by another module.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
public class StacksUsesModule extends StacksImportModule
{

	/**
	 * The {@linkplain MapDescriptor map} of renames ({@linkplain
	 * StringDescriptor string} → string) explicitly specified in this
	 * import declaration.  The keys are the newly introduced names and the
	 * values are the names provided by the predecessor module.
	 */
	public final A_Map renames;

	/**
	 * Construct a new {@link StacksUsesModule}.
	 *
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
	 * @param renames
	 * 		The {@linkplain MapDescriptor map} of renames ({@linkplain
	 * 		StringDescriptor string} → string) explicitly specified in this
	 * 		import declaration.  The keys are the newly introduced names and
	 * 		the values are the names provided by the predecessor module.
	 * 	 * @param usesMethodLeafNameToModuleName
	 * 		A map keyed by visible (uses) method names with no path to the
	 * 		qualified module path it is originally named from.
	 */
	public StacksUsesModule (final String moduleImportName,
		final HashMap<A_String,ImplementationGroup> implementationGroups,
		final HashMap<String, StacksExtendsModule> moduleNameToExtendsList,
		final HashMap<A_String, HashMap<String, ImplementationGroup>>
			methodLeafNameToModuleName,
		final HashMap<String,StacksUsesModule> moduleNameToUsesList,
		final HashMap<A_String, HashMap<String, ImplementationGroup>>
			usesMethodLeafNameToModuleName,
		final A_Map renames)
	{
		super(moduleImportName,implementationGroups,moduleNameToExtendsList,
			methodLeafNameToModuleName, moduleNameToUsesList,
			usesMethodLeafNameToModuleName);

		this.renames = renames;
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
		final StacksFilename newFileName, final boolean deleteOriginal)
	{
		ImplementationGroup group = new ImplementationGroup(key,
			newlyDefinedModule.moduleName(),newFileName);
		for (final ImplementationGroup aGroup :
			extendsMethodLeafNameToModuleName().get(key).values())
		{
			group =
				new ImplementationGroup(aGroup,newFileName,
					newlyDefinedModule.moduleName(), newName);
		}
		if (newlyDefinedModule.usesMethodLeafNameToModuleName()
			.containsKey(newName))
		{
			newlyDefinedModule.usesMethodLeafNameToModuleName().get(newName)
				.put(newlyDefinedModule.moduleName(), group);
		}
		else
		{
			final HashMap<String, ImplementationGroup> newMap =
				new HashMap<String, ImplementationGroup>();

			newMap.put(newlyDefinedModule.moduleName(), group);

			newlyDefinedModule.usesMethodLeafNameToModuleName().put(newName,
				newMap);
		}
		if (deleteOriginal)
		{
			extendsMethodLeafNameToModuleName().remove(key);
		}

	}

	/**
	 * Construct a new {@link StacksUsesModule} from a
	 * {@linkplain StacksCommentsModule}
	 * @param module
	 * 		The {@linkplain StacksCommentsModule} to be transformed
	 * @param renamesMap
	 * 		The renames {@linkplain A_Map map}
	 */
	public StacksUsesModule(
		final StacksCommentsModule module, final A_Map renamesMap)
	{
		super(module.moduleName(),
			new HashMap<A_String,ImplementationGroup>(
				module.namedPublicCommentImplementations()),
			new HashMap<String,StacksExtendsModule>(
				module.extendedNamesImplementations()),
			new HashMap<A_String, HashMap<String, ImplementationGroup>>(
				module.extendsMethodLeafNameToModuleName()),
			module.usesNamesImplementations(),
			module.usesMethodLeafNameToModuleName());
			this.renames = renamesMap;
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

		for (final StacksUsesModule usesModule :
			moduleNameToUsesList().values())
		{
			stringBuilder.append(usesModule.toString());
		}

		return stringBuilder.toString();
	}
}

