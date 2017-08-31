/**
 * ModuleHeader.java
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

package com.avail.compiler;

import static com.avail.descriptor.AtomDescriptor.SpecialAtom.MESSAGE_BUNDLE_KEY;
import static com.avail.descriptor.TokenDescriptor.TokenType.LITERAL;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import com.avail.AvailRuntime;
import com.avail.compiler.splitter.MessageSplitter;
import com.avail.descriptor.MapDescriptor.Entry;
import org.jetbrains.annotations.Nullable;
import com.avail.builder.ModuleName;
import com.avail.builder.ModuleNameResolver;
import com.avail.builder.ResolvedModuleName;
import com.avail.builder.UnresolvedDependencyException;
import com.avail.descriptor.*;
import com.avail.exceptions.MalformedMessageException;
import com.avail.serialization.Deserializer;
import com.avail.serialization.MalformedSerialStreamException;
import com.avail.serialization.Serializer;

/**
 * A module's header information.
 */
public class ModuleHeader
{
	/**
	 * The {@link ModuleName} of the module undergoing compilation.
	 */
	public final ResolvedModuleName moduleName;

	/**
	 * The versions for which the module undergoing compilation guarantees
	 * support.
	 */
	public final List<A_String> versions = new ArrayList<>();

	/**
	 * The {@linkplain ModuleImport module imports} imported by the module
	 * undergoing compilation.  This includes both modules being extended
	 * and modules being simply used.
	 */
	public final List<ModuleImport> importedModules = new ArrayList<>();

	/**
	 * Answer the list of local module {@linkplain String names} imported by
	 * this module header, in the order they appear in the Uses and Extends
	 * clauses.
	 *
	 * @return The list of local module names.
	 */
	public final List<String> importedModuleNames ()
	{
		final List<String> localNames =
			new ArrayList<>(importedModules.size());
		for (final ModuleImport moduleImport : importedModules)
		{
			localNames.add(moduleImport.moduleName.asNativeString());
		}
		return localNames;
	}

	/**
	 * The {@linkplain StringDescriptor names} defined and exported by the
	 * {@linkplain ModuleDescriptor module} undergoing compilation.
	 */
	public final Set<A_String> exportedNames = new LinkedHashSet<>();

	/**
	 * The {@linkplain StringDescriptor names} of {@linkplain
	 * MethodDescriptor methods} that are {@linkplain ModuleDescriptor
	 * module} entry points.
	 */
	public final List<A_String> entryPoints = new ArrayList<>();

	/**
	 * Answer a {@link List} of {@link String}s which name entry points
	 * defined in this module header.
	 *
	 * @return The list of this module's entry point names.
	 */
	public final List<String> entryPointNames ()
	{
		final List<String> javaStrings =
			new ArrayList<>(entryPoints.size());
		for (final A_String entryPoint : entryPoints)
		{
			javaStrings.add(entryPoint.asNativeString());
		}
		return javaStrings;
	}

	/**
	 * The {@linkplain TokenDescriptor pragma tokens}, which are always
	 * string {@linkplain LiteralTokenDescriptor literals}.
	 */
	public final List<A_Token> pragmas = new ArrayList<>();

	/**
	 * The position in the file where the body starts (right after the "body"
	 * token).
	 */
	public int startOfBodyPosition;

	/**
	 * The line number in the file where the body starts (on the same line as
	 * the "body" token).
	 */
	public int startOfBodyLineNumber;

	/**
	 * Construct a new {@link ModuleHeader}.
	 *
	 * @param moduleName
	 *        The {@link ResolvedModuleName resolved name} of the module.
	 */
	public ModuleHeader (final ResolvedModuleName moduleName)
	{
		this.moduleName = moduleName;
	}

	/**
	 * Output the module header.
	 *
	 * @param serializer
	 *        The serializer on which to write the header information.
	 */
	public void serializeHeaderOn (final Serializer serializer)
	{
		serializer.serialize(StringDescriptor.from(moduleName.qualifiedName()));
		serializer.serialize(TupleDescriptor.fromList(versions));
		serializer.serialize(tuplesForSerializingModuleImports());
		serializer.serialize(
			TupleDescriptor.fromList(new ArrayList<>(exportedNames)));
		serializer.serialize(TupleDescriptor.fromList(entryPoints));
		serializer.serialize(TupleDescriptor.fromList(pragmas));
		serializer.serialize(IntegerDescriptor.fromInt(startOfBodyPosition));
		serializer.serialize(IntegerDescriptor.fromInt(startOfBodyLineNumber));
	}

	/**
	 * Convert the information about the imported modules into a {@linkplain
	 * TupleDescriptor tuple} of tuples suitable for serialization.
	 *
	 * @return A tuple encoding the module imports of this module header.
	 */
	private A_Tuple tuplesForSerializingModuleImports ()
	{
		final List<A_Tuple> list = new ArrayList<>();
		for (final ModuleImport moduleImport : importedModules)
		{
			list.add(moduleImport.tupleForSerialization());
		}
		return TupleDescriptor.fromList(list);
	}

	/**
	 * Convert the information encoded in a tuple into a {@link List} of {@link
	 * ModuleImport}s.
	 *
	 * @param serializedTuple An encoding of a list of ModuleImports.
	 * @return The list of ModuleImports.
	 * @throws MalformedSerialStreamException
	 *         If the module import specification is invalid.
	 */
	private static List<ModuleImport> moduleImportsFromTuple (
		final A_Tuple serializedTuple)
		throws MalformedSerialStreamException
	{
		final List<ModuleImport> list = new ArrayList<>();
		for (final A_Tuple importTuple : serializedTuple)
		{
			list.add(ModuleImport.fromSerializedTuple(importTuple));
		}
		return list;
	}

	/**
	 * Extract the module's header information from the {@link
	 * Deserializer}.
	 *
	 * @param deserializer The source of the header information.
	 * @throws MalformedSerialStreamException if malformed.
	 */
	public void deserializeHeaderFrom (final Deserializer deserializer)
		throws MalformedSerialStreamException
	{
		final A_String name = deserializer.deserialize();
		assert name != null;
		if (!name.asNativeString().equals(moduleName.qualifiedName()))
		{
			throw new RuntimeException("Incorrect module name");
		}
		final A_Tuple theVersions = deserializer.deserialize();
		assert theVersions != null;
		versions.clear();
		versions.addAll(TupleDescriptor.toList(theVersions));
		final A_Tuple theExtended = deserializer.deserialize();
		assert theExtended != null;
		importedModules.clear();
		importedModules.addAll(moduleImportsFromTuple(theExtended));
		final A_Tuple theExported = deserializer.deserialize();
		assert theExported != null;
		exportedNames.clear();
		exportedNames.addAll(TupleDescriptor.toList(theExported));
		final A_Tuple theEntryPoints = deserializer.deserialize();
		assert theEntryPoints != null;
		entryPoints.clear();
		entryPoints.addAll(
			TupleDescriptor.toList(theEntryPoints));
		final A_Tuple thePragmas = deserializer.deserialize();
		assert thePragmas != null;
		pragmas.clear();
		// Synthesize fake tokens for the pragma strings.
		for (final A_String pragmaString : thePragmas)
		{
			pragmas.add(LiteralTokenDescriptor.create(
				pragmaString,
				TupleDescriptor.empty(),
				TupleDescriptor.empty(),
				0,
				0,
				LITERAL,
				pragmaString));
		}
		final A_Number positionInteger = deserializer.deserialize();
		assert positionInteger != null;
		startOfBodyPosition = positionInteger.extractInt();
		final A_Number lineNumberInteger = deserializer.deserialize();
		assert lineNumberInteger != null;
		startOfBodyLineNumber = lineNumberInteger.extractInt();
	}

	/**
	 * Update the given module to correspond with information that has been
	 * accumulated in this {@link ModuleHeader}.
	 *
	 * @param module The module to update.
	 * @param runtime The current {@link AvailRuntime}.
	 * @return An error message {@link String} if there was a problem, or
	 *         {@code null} if no problems were encountered.
	 */
	public @Nullable String applyToModule (
		final A_Module module,
		final AvailRuntime runtime)
	{
		final ModuleNameResolver resolver = runtime.moduleNameResolver();
		module.versions(SetDescriptor.fromCollection(versions));

		for (final A_String name : exportedNames)
		{
			assert name.isString();
			final A_Atom trueName =
				AtomWithPropertiesDescriptor.create(name, module);
			module.introduceNewName(trueName);
			module.addImportedName(trueName);
		}

		for (final ModuleImport moduleImport : importedModules)
		{
			final ResolvedModuleName ref;
			try
			{
				ref = resolver.resolve(
					moduleName.asSibling(
						moduleImport.moduleName.asNativeString()),
					null);
			}
			catch (final UnresolvedDependencyException e)
			{
				assert false : "This never happens";
				throw new RuntimeException(e);
			}
			final A_String availRef = StringDescriptor.from(
				ref.qualifiedName());
			if (!runtime.includesModuleNamed(availRef))
			{
				return
					"module \"" + ref.qualifiedName()
					+ "\" to be loaded already";
			}

			final A_Module mod = runtime.moduleAt(availRef);
			final A_Set reqVersions = moduleImport.acceptableVersions;
			if (reqVersions.setSize() > 0)
			{
				final A_Set modVersions = mod.versions();
				if (!modVersions.setIntersects(reqVersions))
				{
					return
						"version compatibility; module \"" + ref.localName()
						+ "\" guarantees versions " + modVersions
						+ " but the current module requires " + reqVersions;
				}
			}
			module.addAncestors(mod.allAncestors());

			// Figure out which strings to make available.
			A_Set stringsToImport;
			final A_Map importedNamesMultimap = mod.importedNames();
			if (moduleImport.wildcard)
			{
				final A_Set renameSourceNames =
					moduleImport.renames.valuesAsTuple().asSet();
				stringsToImport = importedNamesMultimap.keysAsSet();
				stringsToImport = stringsToImport.setMinusCanDestroy(
					renameSourceNames, true);
				stringsToImport = stringsToImport.setUnionCanDestroy(
					moduleImport.names, true);
				stringsToImport = stringsToImport.setMinusCanDestroy(
					moduleImport.excludes, true);
			}
			else
			{
				stringsToImport = moduleImport.names;
			}

			// Look up the strings to get existing atoms.  Don't complain
			// about ambiguity, just export all that match.
			A_Set atomsToImport = SetDescriptor.empty();
			for (final A_String string : stringsToImport)
			{
				if (!importedNamesMultimap.hasKey(string))
				{
					return
						"module \"" + ref.qualifiedName()
						+ "\" to export " + string;
				}
				atomsToImport = atomsToImport.setUnionCanDestroy(
					importedNamesMultimap.mapAt(string), true);
			}

			// Perform renames.
			for (final Entry entry
				: moduleImport.renames.mapIterable())
			{
				final A_String newString = entry.key();
				final A_String oldString = entry.value();
				// Find the old atom.
				if (!importedNamesMultimap.hasKey(oldString))
				{
					return
						"module \"" + ref.qualifiedName()
						+ "\" to export " + oldString
						+ " for renaming to " + newString;
				}
				final A_Set oldCandidates =
					importedNamesMultimap.mapAt(oldString);
				if (oldCandidates.setSize() != 1)
				{
					return
						"module \"" + ref.qualifiedName()
						+ "\" to export a unique name " + oldString
						+ " for renaming to " + newString;
				}
				final A_Atom oldAtom = oldCandidates.iterator().next();
				// Find or create the new atom.
				final A_Atom newAtom;
				final A_Map newNames = module.newNames();
				if (newNames.hasKey(newString))
				{
					// Use it.  It must have been declared in the
					// "Names" clause.
					newAtom = module.newNames().mapAt(newString);
				}
				else
				{
					// Create it.
					newAtom = AtomWithPropertiesDescriptor.create(
						newString, module);
					module.introduceNewName(newAtom);
				}
				// Now tie the bundles together.
				assert newAtom.bundleOrNil().equalsNil();
				final A_Bundle newBundle;
				try
				{
					final A_Bundle oldBundle = oldAtom.bundleOrCreate();
					final A_Method method = oldBundle.bundleMethod();
					newBundle = MessageBundleDescriptor.newBundle(
						newAtom, method, new MessageSplitter(newString));
				}
				catch (final MalformedMessageException e)
				{
					return
						"well-formed signature for " + newString
						+ ", a rename of " + oldString
						+ " from \"" + ref.qualifiedName()
						+ "\"";
				}
				newAtom.setAtomProperty(
					MESSAGE_BUNDLE_KEY.atom, newBundle);
				atomsToImport = atomsToImport.setWithElementCanDestroy(
					newAtom, true);
			}

			// Actually make the atoms available in this module.
			if (moduleImport.isExtension)
			{
				module.addImportedNames(atomsToImport);
			}
			else
			{
				module.addPrivateNames(atomsToImport);
			}
		}

		for (final A_String name : entryPoints)
		{
			assert name.isString();
			try
			{
				final A_Set trueNames = module.trueNamesForStringName(name);
				final int size = trueNames.setSize();
				final AvailObject trueName;
				if (size == 0)
				{
					trueName = AtomWithPropertiesDescriptor.create(
						name, module);
					module.addPrivateName(trueName);
				}
				else if (size == 1)
				{
					// Just validate the name.
					@SuppressWarnings("unused")
					final MessageSplitter splitter =
						new MessageSplitter(name);
					trueName = trueNames.iterator().next();
				}
				else
				{
					return
						"entry point \"" + name.asNativeString()
						+ "\" to be unambiguous";
				}
				module.addEntryPoint(name, trueName);
			}
			catch (final MalformedMessageException e)
			{
				return
					"entry point \"" + name.asNativeString()
					+ "\" to be a valid name";
			}
		}

		return null;
	}
}
