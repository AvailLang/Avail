/**
 * AbstractAvailCompiler.java
 * Copyright © 1993-2013, Mark van Gulik and Todd L Smith. All rights reserved.
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

import static com.avail.compiler.AbstractAvailCompiler.ExpectedToken.*;
import static com.avail.compiler.ParsingOperation.*;
import static com.avail.descriptor.ParseNodeTypeDescriptor.ParseNodeKind.*;
import static com.avail.descriptor.TokenDescriptor.TokenType.*;
import static com.avail.descriptor.TupleDescriptor.toList;
import static com.avail.descriptor.TypeDescriptor.Types.*;
import static com.avail.utility.PrefixSharingList.*;
import static java.lang.Math.min;
import static java.util.Arrays.asList;
import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.*;
import com.avail.AvailRuntime;
import com.avail.AvailTask;
import com.avail.annotations.Nullable;
import com.avail.annotations.InnerAccess;
import com.avail.builder.*;
import com.avail.compiler.scanning.*;
import com.avail.descriptor.*;
import com.avail.descriptor.FiberDescriptor.GeneralFlag;
import com.avail.descriptor.TokenDescriptor.TokenType;
import com.avail.exceptions.SignatureException;
import com.avail.interpreter.*;
import com.avail.interpreter.primitive.P_352_RejectParsing;
import com.avail.serialization.*;
import com.avail.utility.*;

/**
 * The abstract compiler for Avail code.  Subclasses may wish to implement, oh,
 * say, a system version with a hard-coded basic syntax and a non-system version
 * with no hard-coded syntax but macro capability.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public abstract class AbstractAvailCompiler
{
	/**
	 * The {@link AvailRuntime} for the compiler. Since a compiler cannot
	 * migrate between two runtime environments, it is safe to cache it for
	 * efficient access.
	 */
	final AvailRuntime runtime = AvailRuntime.current();

	/**
	 * An {@code ImportValidationException} is raised by the constructor of
	 * {@link ModuleImport} when it is supplied with arguments that constitute
	 * an invalid import specification.
	 */
	private static final class ImportValidationException
	extends Exception
	{
		/** The serial version identifier. */
		private static final long serialVersionUID = 4925679877429696123L;

		/**
		 * Construct a new {@link ImportValidationException}.
		 *
		 * @param message
		 *        A message suitable for use as a parse rejection.
		 */
		public ImportValidationException (final String message)
		{
			super(message);
		}
	}

	/**
	 * Information that a {@link ModuleHeader} uses to keep track of a module
	 * import, whether from an {@linkplain ExpectedToken#EXTENDS Extends} or a
	 * {@linkplain ExpectedToken#USES Uses} clause.
	 */
	public static class ModuleImport
	{
		/**
		 * The name of the module being imported.
		 */
		public final A_String moduleName;

		/**
		 * A {@linkplain SetDescriptor set} of {@linkplain StringDescriptor
		 * strings} which, when intersected with the declared version strings
		 * for the actual module being imported, must be nonempty.
		 */
		public final A_Set acceptableVersions;

		/**
		 * Whether this {@link ModuleImport} is due to a {@linkplain
		 * ExpectedToken#EXTENDS Extends} clause rather than a {@linkplain
		 * ExpectedToken#USES Uses} clause.
		 */
		public final boolean isExtension;

		/**
		 * The {@linkplain SetDescriptor set} of names ({@linkplain
		 * StringDescriptor strings}) explicitly imported through this import
		 * declaration.  If no names or renames were specified, then this is
		 * {@linkplain NilDescriptor#nil() nil} instead.
		 */
		public final A_Set names;

		/**
		 * The {@linkplain MapDescriptor map} of renames ({@linkplain
		 * StringDescriptor string} → string) explicitly specified in this
		 * import declaration.  The keys are the newly introduced names and the
		 * values are the names provided by the predecessor module.  If no names
		 * or renames were specified, then this is {@linkplain
		 * NilDescriptor#nil() nil} instead.
		 */
		public final A_Map renames;

		/**
		 * The {@linkplain SetDescriptor set} of names to specifically exclude
		 * from being imported from the predecessor module.
		 */
		public final A_Set excludes;

		/**
		 * Whether to include all names exported by the predecessor module that
		 * are not otherwise excluded by this import.
		 */
		public final boolean wildcard;

		/**
		 * Construct a new {@link ModuleImport}.
		 *
		 * @param moduleName
		 *            The non-resolved {@linkplain StringDescriptor name} of the
		 *            module to import.
		 * @param acceptableVersions
		 *            The {@linkplain SetDescriptor set} of version {@linkplain
		 *            StringDescriptor strings} from which to look for a match
		 *            in the actual imported module's list of compatible
		 *            versions.
		 * @param isExtension
		 *            True if these imported declarations are supposed to be
		 *            re-exported from the current module.
		 * @param names
		 *            The {@linkplain SetDescriptor set} of names ({@linkplain
		 *            StringDescriptor strings}) imported from the module.  They
		 *            will be cause atoms to be looked up within the predecessor
		 *            module, and will be re-exported verbatim if isExtension is
		 *            true.  {@linkplain NilDescriptor#nil() Nil} indicates that
		 *            neither names nor renames were specified.
		 * @param renames
		 *            The {@linkplain MapDescriptor map} from new names to old
		 *            names (both {@linkplain StringDescriptor strings}) that
		 *            are imported from the module.  The new names will become
		 *            new atoms in the importing module, and exported if
		 *            isExtension is true.  {@linkplain NilDescriptor#nil() Nil}
		 *            indicates that neither names nor renames were specified.
		 * @param excludes
		 *            The {@linkplain SetDescriptor set} of names ({@linkplain
		 *            StringDescriptor strings}) to exclude from being imported.
		 * @param wildcard
		 *            Whether to import any published names not explicitly
		 *            excluded.
		 * @throws ImportValidationException
		 *         If the specification is invalid.
		 */
		ModuleImport (
				final A_String moduleName,
				final A_Set acceptableVersions,
				final boolean isExtension,
				final A_Set names,
				final A_Map renames,
				final A_Set excludes,
				final boolean wildcard)
			throws ImportValidationException
		{
			this.moduleName = moduleName.makeShared();
			this.acceptableVersions = acceptableVersions.makeShared();
			this.isExtension = isExtension;
			this.names = names.makeShared();
			this.renames = renames.makeShared();
			this.excludes = excludes.makeShared();
			this.wildcard = wildcard;
			validate();
		}

		/**
		 * Validate the module import specification.
		 *
		 * @throws ImportValidationException
		 *         If the specification is invalid.
		 */
		private void validate () throws ImportValidationException
		{
			final A_Set renameOriginals = renames.valuesAsTuple().asSet();
			if (wildcard)
			{
				if (!names.isSubsetOf(renameOriginals))
				{
					throw new ImportValidationException(
						"wildcard import not to be specified or "
						+ "explicit positive imports only to be used to force "
						+ "inclusion of source names of renames");
				}
			}
			else
			{
				if (excludes.setSize() != 0)
				{
					throw new ImportValidationException(
						"wildcard import to be specified or "
						+ "explicit negative imports not to be specified");
				}
			}
			final A_Set redundantExclusions =
				renameOriginals.setIntersectionCanDestroy(
					excludes,
					false);
			if (redundantExclusions.setSize() != 0)
			{
				final StringBuilder builder = new StringBuilder(100);
				builder.append(
					"source names of renames not to overlap explicit "
					+ "negative imports (the redundant name");
				if (redundantExclusions.setSize() == 1)
				{
					builder.append(" is ");
				}
				else
				{
					builder.append("s are ");
				}
				boolean first = true;
				for (final A_String redundant : redundantExclusions)
				{
					if (first)
					{
						first = !first;
					}
					else
					{
						builder.append(", ");
					}
					// This will quote the string.
					builder.append(redundant);
				}
				builder.append(")");
				throw new ImportValidationException(builder.toString());
			}
		}

		/**
		 * Answer a tuple suitable for serializing this import information.
		 *
		 * <p>
		 * This currently consists of exactly 7 elements:
		 * <ol>
		 * <li>The unresolved module name.</li>
		 * <li>The tuple of acceptable version strings.</li>
		 * <li>True if this is an "Extends" import, false if it's a "Uses".</li>
		 * <li>The set of names (strings) to explicitly import.</li>
		 * <li>The map from new names to old names (all strings) to explicitly
		 * import and rename.</li>
		 * <li>The set of names (strings) to explicitly exclude from
		 * importing.</li>
		 * <li>True to include all names not explicitly excluded, otherwise
		 * false</li>
		 * </ol>
		 *
		 * @see #fromSerializedTuple(A_Tuple)
		 * @return The tuple to serialize.
		 */
		A_Tuple tupleForSerialization ()
		{
			return TupleDescriptor.from(
				moduleName,
				acceptableVersions,
				AtomDescriptor.objectFromBoolean(isExtension),
				names,
				renames,
				excludes,
				AtomDescriptor.objectFromBoolean(wildcard));
		}

		/**
		 * Convert the provided {@linkplain TupleDescriptor tuple} into a
		 * {@link ModuleImport}.  This is the reverse of the transformation
		 * provided by {@link #tupleForSerialization()}.
		 *
		 * @param serializedTuple The tuple from which to build a ModuleImport.
		 * @return The ModuleImport.
		 * @throws MalformedSerialStreamException
		 *         If the module import specification is invalid.
		 */
		public static ModuleImport fromSerializedTuple (
				final A_Tuple serializedTuple)
			throws MalformedSerialStreamException
		{
			final int tupleSize = serializedTuple.tupleSize();
			assert tupleSize == 7;
			try
			{
				return new ModuleImport(
					serializedTuple.tupleAt(1), // moduleName
					serializedTuple.tupleAt(2), // acceptableVersions
					serializedTuple.tupleAt(3).extractBoolean(), // isExtension
					serializedTuple.tupleAt(4), // names
					serializedTuple.tupleAt(5), // renames
					serializedTuple.tupleAt(6), // excludes
					serializedTuple.tupleAt(7).extractBoolean() // wildcard
				);
			}
			catch (final ImportValidationException e)
			{
				throw new MalformedSerialStreamException(e);
			}
		}
	}

	/**
	 * A module's header information.
	 */
	public static class ModuleHeader
	{
		/**
		 * The {@link ModuleName} of the module undergoing compilation.
		 */
		public final ResolvedModuleName moduleName;

		/**
		 * Whether this is the header of a system module.
		 */
		public boolean isSystemModule;

		/**
		 * The versions for which the module undergoing compilation guarantees
		 * support.
		 */
		public final List<A_String> versions = new ArrayList<A_String>();

		/**
		 * The {@linkplain ModuleImport module imports} imported by the module
		 * undergoing compilation.  This includes both modules being extended
		 * and modules being simply used.
		 */
		public final List<ModuleImport> importedModules =
			new ArrayList<ModuleImport>();

		/**
		 * The {@linkplain StringDescriptor names} defined and exported by the
		 * {@linkplain ModuleDescriptor module} undergoing compilation.
		 */
		public final List<A_String> exportedNames = new ArrayList<A_String>();

		/**
		 * The {@linkplain StringDescriptor names} of {@linkplain
		 * MethodDescriptor methods} that are {@linkplain ModuleDescriptor
		 * module} entry points.
		 */
		public final List<A_String> entryPoints = new ArrayList<A_String>();

		/**
		 * The {@linkplain String pragma strings}.
		 */
		public final List<A_String> pragmas = new ArrayList<A_String>();

		/**
		 * Construct a new {@link AbstractAvailCompiler.ModuleHeader}.
		 *
		 * @param moduleName
		 *        The {@link ResolvedModuleName resolved name} of the module.
		 */
		public ModuleHeader (final ResolvedModuleName moduleName)
		{
			this.moduleName = moduleName;
		}

		/**
		 * @param serializer
		 */
		public void serializeHeaderOn (final Serializer serializer)
		{
			serializer.serialize(
				StringDescriptor.from(moduleName.qualifiedName()));
			serializer.serialize(
				AtomDescriptor.objectFromBoolean(isSystemModule));
			serializer.serialize(TupleDescriptor.fromList(versions));
			serializer.serialize(tuplesForSerializingModuleImports());
			serializer.serialize(TupleDescriptor.fromList(exportedNames));
			serializer.serialize(TupleDescriptor.fromList(entryPoints));
			serializer.serialize(TupleDescriptor.fromList(pragmas));
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
		 * Convert the information encoded in a tuple into a {@link List} of
		 * {@link ModuleImport}s.
		 *
		 * @param serializedTuple An encoding of a list of ModuleImports.
		 * @return The list of ModuleImports.
		 * @throws MalformedSerialStreamException
		 *         If the module import specification is invalid.
		 */
		private List<ModuleImport> moduleImportsFromTuple (
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
			final A_Atom theSystemFlag = deserializer.deserialize();
			assert theSystemFlag != null;
			isSystemModule = theSystemFlag.extractBoolean();
			final A_Tuple theVersions = deserializer.deserialize();
			assert theVersions != null;
			versions.clear();
			versions.addAll(toList(theVersions));
			final A_Tuple theExtended = deserializer.deserialize();
			assert theExtended != null;
			importedModules.clear();
			importedModules.addAll(moduleImportsFromTuple(theExtended));
			final A_Tuple theExported = deserializer.deserialize();
			assert theExported != null;
			exportedNames.clear();
			exportedNames.addAll(toList(theExported));
			final A_Tuple theEntryPoints = deserializer.deserialize();
			assert theEntryPoints != null;
			entryPoints.clear();
			entryPoints.addAll(toList(theEntryPoints));
			final A_Tuple thePragmas = deserializer.deserialize();
			assert thePragmas != null;
			pragmas.clear();
			pragmas.addAll(toList(thePragmas));
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
				final A_Atom trueName = AtomDescriptor.create(name, module);
				module.introduceNewName(trueName);
				module.addImportedName(trueName);
			}

			for (final ModuleImport moduleImport : importedModules)
			{
				final ResolvedModuleName ref = resolver.resolve(
					moduleName.asSibling(
						moduleImport.moduleName.asNativeString()));
				assert ref != null;
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
					final A_Set intersection =
						modVersions.setIntersectionCanDestroy(
							reqVersions, false);
					if (intersection.setSize() == 0)
					{
						return
							"version compatibility; module \"" + ref.localName()
							+ "\" guarantees versions " + modVersions
							+ " but current module requires " + reqVersions;
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
				for (final MapDescriptor.Entry entry
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
					A_Atom newAtom;
					if (module.newNames().hasKey(newString))
					{
						// Use it.  It must have been declared in the
						// "Names" clause.
						newAtom = module.newNames().mapAt(newString);
					}
					else
					{
						// Create it.
						newAtom = AtomDescriptor.create(newString, module);
					}
					// Now tie the bundles together.
					assert newAtom.bundleOrNil().equalsNil();
					final A_Bundle oldBundle = oldAtom.bundleOrCreate();
					final A_Method method = oldBundle.bundleMethod();
					final A_Bundle newBundle;
					try
					{
						newBundle = MessageBundleDescriptor.newBundle(
							newAtom, method);
					}
					catch (final SignatureException e)
					{
						return
							"well-formed signature for " + newString
							+ ", a rename of " + oldString
							+ " from \"" + ref.qualifiedName()
							+ "\"";
					}
					newAtom.setAtomProperty(
						AtomDescriptor.messageBundleKey(),
						newBundle);
					atomsToImport = atomsToImport.setWithElementCanDestroy(
						newAtom, true);
				}

				// Actually make the atoms available in this module.
				for (final A_Atom trueName : atomsToImport)
				{
					if (moduleImport.isExtension)
					{
						module.addImportedName(trueName);
					}
					else
					{
						module.addPrivateName(trueName);
					}
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
						trueName = AtomDescriptor.create(name, module);
						module.addPrivateName(trueName);
					}
					else if (size == 1)
					{
						final MessageSplitter splitter =
							new MessageSplitter(name);
						if (splitter.numberOfArguments() > 0)
						{
							return
								"entry point \"" + name.asNativeString()
								+ "\" to be private to the current module "
								+ "(because its arity is not zero)";
						}
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
				catch (final SignatureException e)
				{
					return
						"entry point \"" + name.asNativeString()
						+ "\" to be a valid name";
				}
			}

			return null;
		}
	}

	/**
	 * The header information for the current module being parsed.
	 */
	public final ModuleHeader moduleHeader;

	/**
	 * The Avail {@linkplain ModuleDescriptor module} undergoing compilation.
	 */
	final A_Module module;

	/**
	 * The {@linkplain AvailLoader loader} create and operated by this
	 * {@linkplain AbstractAvailCompiler compiler} to facilitate the loading of
	 * {@linkplain ModuleDescriptor modules}.
	 */
	public @Nullable AvailLoader loader;

	/**
	 * Answer the {@linkplain AvailLoader loader} create and operated by this
	 * {@linkplain AbstractAvailCompiler compiler} to facilitate the loading of
	 * {@linkplain ModuleDescriptor modules}.
	 *
	 * @return A loader.
	 */
	public AvailLoader loader ()
	{
		final AvailLoader theLoader = loader;
		assert theLoader != null;
		return theLoader;
	}

	/**
	 * The source text of the Avail {@linkplain ModuleDescriptor module}
	 * undergoing compilation.
	 */
	@InnerAccess final String source;

	/**
	 * The complete {@linkplain List list} of {@linkplain TokenDescriptor
	 * tokens} parsed from the source text.
	 */
	@InnerAccess List<A_Token> tokens;

	/**
	 * The position of the rightmost {@linkplain TokenDescriptor token} reached
	 * by any parsing attempt.
	 */
	@InnerAccess int greatestGuess;

	/**
	 * The {@linkplain List list} of {@linkplain String} {@linkplain Generator
	 * generators} that describe what was expected (but not found) at the
	 * {@linkplain #greatestGuess rightmost reached position}.
	 */
	@InnerAccess final List<Describer> greatExpectations = new ArrayList<>();

	/** The memoization of results of previous parsing attempts. */
	final @InnerAccess AvailCompilerFragmentCache fragmentCache =
		new AvailCompilerFragmentCache();

	/**
	 * The special {@linkplain AtomDescriptor atom} used to locate the current
	 * parsing information in a fiber's globals.
	 */
	@InnerAccess final A_Atom clientDataGlobalKey =
		AtomDescriptor.clientDataGlobalKey();

	/**
	 * The special {@linkplain AtomDescriptor atom} used to locate the map of
	 * declarations that are in scope, within the {@linkplain
	 * #clientDataGlobalKey current parsing information} stashed within a
	 * fiber's globals.
	 */
	@InnerAccess final A_Atom compilerScopeMapKey =
		AtomDescriptor.compilerScopeMapKey();

	/**
	 * Answer whether this is a {@linkplain AvailSystemCompiler system
	 * compiler}.  A system compiler is used for modules that start with the
	 * keyword "{@linkplain ExpectedToken#SYSTEM System}".  Such modules use a
	 * predefined syntax.
	 *
	 * @return Whether this is a system compiler.
	 */
	boolean isSystemCompiler ()
	{
		return false;
	}

	/**
	 * These are the tokens that are understood by the Avail compilers. Most of
	 * these tokens exist to support the {@linkplain AvailSystemCompiler system
	 * compiler}, though a few (related to module headers) are needed also by
	 * the {@linkplain AvailCompiler standard compiler}.
	 */
	public enum ExpectedToken
	{
		/** Module header token. Must be the first token of a system module. */
		SYSTEM("System", KEYWORD),

		/** Module header token: Precedes the name of the defined module. */
		MODULE("Module", KEYWORD),

		/**
		 * Module header token: Precedes the list of versions for which the
		 * defined module guarantees compatibility.
		 */
		VERSIONS("Versions", KEYWORD),

		/** Module header token: Precedes the list of pragma strings. */
		PRAGMA("Pragma", KEYWORD),

		/**
		 * Module header token: Occurs in pragma strings to define bootstrap
		 * method.
		 */
		PRAGMA_METHOD("method", KEYWORD),

		/**
		 * Module header token: Occurs in pragma strings to define bootstrap
		 * macros.
		 */
		PRAGMA_MACRO("macro", KEYWORD),

		/**
		 * Module header token: Occurs in a pragma string to define the
		 * stringification method.
		 */
		PRAGMA_STRINGIFY("stringify", KEYWORD),

		/**
		 * Module header token: Precedes the list of imported modules whose
		 * (filtered) names should be re-exported to clients of the defined
		 * module.
		 */
		EXTENDS("Extends", KEYWORD),

		/**
		 * Module header token: Precedes the list of imported modules whose
		 * (filtered) names are imported only for the private use of the
		 * defined module.
		 */
		USES("Uses", KEYWORD),

		/**
		 * Module header token: Precedes the list of names exported for use by
		 * clients of the defined module.
		 */
		NAMES("Names", KEYWORD),

		/**
		 * Module header token: Precedes the list of entry points.
		 */
		ENTRIES("Entries", KEYWORD),

		/** Module header token: Precedes the contents of the defined module. */
		BODY("Body", KEYWORD),

		/** Leads a primitive binding. */
		PRIMITIVE("Primitive", KEYWORD),

		/** Leads a label. */
		DOLLAR_SIGN("$", OPERATOR),

		/** Leads a reference. */
		UP_ARROW("↑", OPERATOR),

		/** Module header token: Separates string literals. */
		COMMA(",", OPERATOR),

		/** Module header token: Separates string literals for renames. */
		RIGHT_ARROW("→", OPERATOR),

		/** Module header token: Prefix to indicate exclusion. */
		MINUS("-", OPERATOR),

		/** Module header token: Indicates wildcard import */
		ELLIPSIS("…", OPERATOR),

		/** Uses related to declaration and assignment. */
		COLON(":", OPERATOR),

		/** Uses related to declaration and assignment. */
		EQUALS("=", OPERATOR),

		/** Leads a lexical block. */
		OPEN_SQUARE("[", OPERATOR),

		/** Ends a lexical block. */
		CLOSE_SQUARE("]", OPERATOR),

		/** Leads a function body. */
		VERTICAL_BAR("|", OPERATOR),

		/** Leads an exception set. */
		CARET("^", OPERATOR),

		/** Module header token: Uses related to grouping. */
		OPEN_PARENTHESIS("(", OPERATOR),

		/** Module header token: Uses related to grouping. */
		CLOSE_PARENTHESIS(")", OPERATOR),

		/** End of statement. */
		SEMICOLON(";", OPERATOR);

		/** The Java {@link String} form of the lexeme. */
		public final String lexemeJavaString;

		/**
		 * The {@linkplain StringDescriptor Avail string} form of the lexeme.
		 */
		private final A_String lexeme;

		/** The {@linkplain TokenType token type}. */
		private final TokenType tokenType;

		/**
		 * Answer the {@linkplain StringDescriptor lexeme}.
		 *
		 * @return The lexeme as an Avail string.
		 */
		public A_String lexeme ()
		{
			return lexeme;
		}

		/**
		 * Answer the {@linkplain TokenType token type}.
		 *
		 * @return The token type.
		 */
		TokenType tokenType ()
		{
			return tokenType;
		}

		/**
		 * Construct a new {@link ExpectedToken}.
		 *
		 * @param lexemeJavaString
		 *        The Java {@linkplain String lexeme string}, i.e. the text
		 *        of the token.
		 * @param tokenType
		 *        The {@linkplain TokenType token type}.
		 */
		ExpectedToken (
			final String lexemeJavaString,
			final TokenType tokenType)
		{
			this.tokenType = tokenType;
			this.lexemeJavaString = lexemeJavaString;
			this.lexeme = StringDescriptor.from(lexemeJavaString).makeShared();
		}
	}

	/**
	 * Asynchronously construct a suitable {@linkplain AbstractAvailCompiler
	 * compiler} to parse the specified {@linkplain ModuleName module name}.
	 *
	 * @param resolvedName
	 *        The {@linkplain ResolvedModuleName resolved name} of the
	 *        {@linkplain ModuleDescriptor module} to compile.
	 * @param stopAfterBodyToken
	 *        Whether to stop parsing at the occurrence of the BODY token. This
	 *        is an optimization for faster build analysis.
	 * @param succeed
	 *        What to do with the resultant compiler in the event of success.
	 *        This is a continuation that accepts the new compiler.
	 * @param fail
	 *        What to do in the event of failure. This is a continuation that
	 *        accepts the {@linkplain Throwable throwable} responsible for
	 *        abnormal termination.
	 * @throws IOException
	 *         If the source module cannot be opened or read.
	 */
	public static void create (
			final ResolvedModuleName resolvedName,
			final boolean stopAfterBodyToken,
			final Continuation1<AbstractAvailCompiler> succeed,
			final Continuation1<Throwable> fail)
		throws IOException
	{
		extractSourceThen(
			resolvedName,
			new Continuation1<String>()
			{
				@Override
				public void value (final @Nullable String sourceText)
				{
					try
					{
						assert sourceText != null;
						final List<A_Token> tokens = tokenize(
							sourceText,
							stopAfterBodyToken);
						AbstractAvailCompiler compiler;
						if (!tokens.isEmpty()
							&& tokens.get(0).string().equals(SYSTEM.lexeme()))
						{
							compiler = new AvailSystemCompiler(
								resolvedName,
								sourceText,
								tokens);
						}
						else
						{
							compiler = new AvailCompiler(
								resolvedName,
								sourceText,
								tokens);
						}
						succeed.value(compiler);
					}
					catch (final Throwable e)
					{
						fail.value(e);
					}
				}
			},
			fail);
	}

	/**
	 * Construct a new {@link AbstractAvailCompiler} which will use the given
	 * {@link Interpreter} to evaluate expressions.
	 *
	 * @param moduleName
	 *        The {@link ResolvedModuleName resolved name} of the module to
	 *        compile.
	 * @param source
	 *        The source code {@linkplain StringDescriptor string}.
	 * @param tokens
	 *        The list of {@linkplain TokenDescriptor tokens}.
	 */
	public AbstractAvailCompiler (
		final ResolvedModuleName moduleName,
		final String source,
		final List<A_Token> tokens)
	{
		this.moduleHeader = new ModuleHeader(moduleName);
		this.source = source;
		this.tokens = tokens;

		this.module = ModuleDescriptor.newModule(
			StringDescriptor.from(moduleHeader.moduleName.qualifiedName()));
		module.isSystemModule(isSystemCompiler());
	}

	/**
	 * This is actually a two-argument continuation, but it has only a single
	 * type parameter because the first one is always the {@linkplain
	 * ParserState parser state} that indicates where the continuation should
	 * continue parsing.
	 *
	 * @param <AnswerType>
	 *        The type of the second parameter of the {@linkplain
	 *        Con#value(ParserState, Object)} method.
	 * @author Mark van Gulik &lt;mark@availlang.org&gt;
	 */
	abstract class Con<AnswerType>
	implements Continuation2<ParserState, AnswerType>
	{
		/**
		 * A debugging description of this continuation.
		 */
		final String description;

		/**
		 * Construct a new {@link AvailCompiler.Con} with the provided
		 * description.
		 *
		 * @param description
		 *            The provided description.
		 */
		Con (final String description)
		{
			this.description = description;
		}

		@Override
		public String toString ()
		{
			return "Con(" + description + ")";
		}

		@Override
		public abstract void value (
			@Nullable ParserState state,
			@Nullable AnswerType answer);
	}


	/**
	 * A {@link Runnable} which supports a natural ordering (via the {@link
	 * Comparable} interface) which will favor processing of the leftmost
	 * available tasks first.
	 *
	 * @author Mark van Gulik &lt;mark@availlang.org&gt;
	 */
	private static class ParsingTask
	extends AvailTask
	{
		/**
		 * The description associated with this task. Only used for debugging.
		 */
		final String description;

		/** The parsing state for this task will operate. */
		final ParserState state;

		/**
		 * Construct a new {@link AbstractAvailCompiler.ParsingTask}.
		 *
		 * @param description What this task will do.
		 * @param state The {@linkplain ParserState parser state} for this task.
		 * @param continuation What to do.
		 */
		public ParsingTask (
			final String description,
			final ParserState state,
			final Continuation0 continuation)
		{
			super(FiberDescriptor.compilerPriority, continuation);
			this.description = description;
			this.state = state;
		}

		@Override
		public String toString()
		{
			return description + "@pos(" + state.position + ")";
		}

		@Override
		public int compareTo (final @Nullable AvailTask o)
		{
			assert o != null;
			final int priorityDelta = priority - o.priority;
			if (priorityDelta != 0)
			{
				return priorityDelta;
			}
			if (o instanceof ParsingTask)
			{
				final ParsingTask task = (ParsingTask) o;
				return state.position - task.state.position;
			}
			return priorityDelta;
		}
	}

	/** The number of work units that have been queued. */
	long workUnitsQueued = 0;

	/** The number of work units that have been completed. */
	long workUnitsCompleted = 0;

	/**
	 * The {@linkplain Throwable throwable} (if any) responsible for an
	 * abnormal termination of compilation.
	 */
	@InnerAccess volatile @Nullable Throwable terminator;

	/**
	 * Announce that compilation has failed because of the specified {@linkplain
	 * Throwable throwable}. Pending compiler tasks should exit immediately upon
	 * running, and no new compiler tasks should be queued. Notify all
	 * {@linkplain Thread threads} waiting on the {@linkplain
	 * AbstractAvailCompiler receiver}'s monitor.
	 *
	 * <p>
	 * Only the first call of this method has any effect.
	 * </p>
	 *
	 * @param throwable
	 *        The throwable responsible for termination of compilation.
	 */
	synchronized @InnerAccess void compilationFailed (final Throwable throwable)
	{
		if (terminator == null)
		{
			terminator = throwable;
			final Continuation0 reporter = failureReporter;
			assert reporter != null;
			reporter.value();
		}
	}

	/** The output stream on which the serializer writes. */
	public final ByteArrayOutputStream serializerOutputStream =
		new ByteArrayOutputStream(1000);

	/**
	 * The serializer that captures the sequence of bytes representing the
	 * module during compilation.
	 */
	final Serializer serializer = new Serializer(serializerOutputStream);

	/**
	 * What to do when there are no more work units.
	 */
	@InnerAccess volatile @Nullable Continuation0 noMoreWorkUnits = null;

	/**
	 * Execute {@code #tryBlock}, passing a {@linkplain
	 * AbstractAvailCompiler.Con continuation} that it should run upon finding
	 * exactly one local {@linkplain ParseNodeDescriptor solution}. Report
	 * ambiguity as an error.
	 *
	 * @param start
	 *        Where to start parsing.
	 * @param tryBlock
	 *        What to try. This is a continuation that accepts a continuation
	 *        that tracks completion of parsing.
	 * @param supplyAnswer
	 *        What to do if exactly one result was produced. This is a
	 *        continuation that accepts a solution.
	 */
	void tryIfUnambiguousThen (
		final ParserState start,
		final Con<Con<A_Phrase>> tryBlock,
		final Con<A_Phrase> supplyAnswer)
	{
		assert noMoreWorkUnits == null;
		// Augment the start position with a variant that incorporates the
		// solution-accepting continuation.
		final Mutable<Integer> count = new Mutable<Integer>(0);
		final MutableOrNull<A_Phrase> solution =
			new MutableOrNull<A_Phrase>();
		final MutableOrNull<ParserState> afterStatement =
			new MutableOrNull<ParserState>();
		noMoreWorkUnits = new Continuation0()
		{
			@Override
			public void value ()
			{
				synchronized (AbstractAvailCompiler.this)
				{
					assert workUnitsQueued == workUnitsCompleted;
				}
				// Ambiguity is detected and prevented during the parse, and
				// should never be identified here.
				assert count.value < 2;
				// If no solutions were found, then report an error.
				if (count.value == 0)
				{
					reportError();
					return;
				}
				// If a simple unambiguous solution was found, then answer
				// it forward to the continuation.
				if (count.value == 1)
				{
					assert solution.value != null;
					supplyAnswer.value(
						afterStatement.value, solution.value);
				}
			}
		};
		final ParserState realStart = new ParserState(
			start.position,
			start.clientDataMap);
		attempt(
			realStart,
			tryBlock,
			new Con<A_Phrase>("Record solution")
			{
				@Override
				public void value (
					final @Nullable ParserState afterSolution,
					final @Nullable A_Phrase aSolution)
				{
					assert afterSolution != null;
					assert aSolution != null;
					if (count.value == 0)
					{
						afterStatement.value = afterSolution;
						solution.value = aSolution;
						count.value++;
					}
					else
					{
						// Indicate the problem at the last token of the
						// ambiguous expression.
						reportAmbiguousInterpretations(
							new ParserState(
								afterSolution.position - 1,
								afterSolution.clientDataMap),
							solution.value(),
							aSolution);
						return;
					}
				}
			});
	}

	/**
	 * {@link ParserState} instances are immutable and keep track of a current
	 * {@link #position} and {@link #clientDataMap} during parsing.
	 *
	 * @author Mark van Gulik &lt;mark@availlang.org&gt;
	 */
	public class ParserState
	{
		/**
		 * The position represented by this {@link ParserState}. In particular,
		 * it's the (zero-based) index of the current token.
		 */
		final int position;

		/**
		 * A {@linkplain MapDescriptor map} of interesting information used by
		 * the compiler.
		 */
		public final A_Map clientDataMap;

		/**
		 * Construct a new immutable {@link ParserState}.
		 *
		 * @param position
		 *            The index of the current token.
		 * @param clientDataMap
		 *            The {@link MapDescriptor map} of data used by macros while
		 *            parsing Avail code.
		 */
		ParserState (
			final int position,
			final A_Map clientDataMap)
		{
			this.position = position;
			// Note that this map *must* be marked as shared, since parsing
			// proceeds in parallel.
			this.clientDataMap = clientDataMap.makeShared();
		}

		@Override
		public int hashCode ()
		{
			return position * 473897843 ^ clientDataMap.hashCode();
		}

		@Override
		public boolean equals (final @Nullable Object another)
		{
			if (!(another instanceof ParserState))
			{
				return false;
			}
			final ParserState anotherState = (ParserState) another;
			return position == anotherState.position
				&& clientDataMap.equals(anotherState.clientDataMap);
		}

		@Override
		public String toString ()
		{
			return String.format(
				"%s%n\tPOSITION = %d%n\tTOKENS = %s ☞ %s%n\tCLIENT_DATA = %s",
				getClass().getSimpleName(),
				position,
				(position > 0
					? tokens.get(position - 1).string()
					: "(start)"),
				(position < tokens.size()
					? tokens.get(position).string()
					: "(end)"),
				clientDataMap);
		}

		/**
		 * Determine if this state represents the end of the file. If so, one
		 * should not invoke {@link #peekToken()} or {@link #afterToken()}
		 * again.
		 *
		 * @return Whether this state represents the end of the file.
		 */
		boolean atEnd ()
		{
			return this.position == tokens.size() - 1;
		}

		/**
		 * Answer the {@linkplain TokenDescriptor token} at the current
		 * position.
		 *
		 * @return The token.
		 */
		A_Token peekToken ()
		{
			return tokens.get(position);
		}

		/**
		 * Answer whether the current token has the specified type and content.
		 *
		 * @param expectedToken
		 *        The {@linkplain ExpectedToken expected token} to look for.
		 * @return Whether the specified token was found.
		 */
		boolean peekToken (final ExpectedToken expectedToken)
		{
			if (atEnd())
			{
				return false;
			}
			final A_Token token = peekToken();
			return token.tokenType() == expectedToken.tokenType()
				&& token.string().equals(expectedToken.lexeme());
		}

		/**
		 * Answer whether the current token has the specified type and content.
		 *
		 * @param expectedToken
		 *        The {@linkplain ExpectedToken expected token} to look for.
		 * @param expected
		 *        A {@linkplain Describer describer} of a message to record if
		 *        the specified token is not present.
		 * @return Whether the specified token is present.
		 */
		boolean peekToken (
			final ExpectedToken expectedToken,
			final Describer expected)
		{
			final A_Token token = peekToken();
			final boolean found = token.tokenType() == expectedToken.tokenType()
					&& token.string().equals(expectedToken.lexeme());
			if (!found)
			{
				expected(expected);
			}
			return found;
		}

		/**
		 * Answer whether the current token has the specified type and content.
		 *
		 * @param expectedToken
		 *        The {@linkplain ExpectedToken expected token} to look for.
		 * @param expected
		 *        A message to record if the specified token is not present.
		 * @return Whether the specified token is present.
		 */
		boolean peekToken (
			final ExpectedToken expectedToken,
			final String expected)
		{
			return peekToken(expectedToken, generate(expected));
		}

		/**
		 * Return a new {@link ParserState} like this one, but advanced by one
		 * token.
		 *
		 * @return A new parser state.
		 */
		ParserState afterToken ()
		{
			assert !atEnd();
			return new ParserState(
				position + 1,
				clientDataMap);
		}

		/**
		 * Parse a string literal. Answer the {@linkplain LiteralTokenDescriptor
		 * string literal token} if found, otherwise answer {@code null}.
		 *
		 * @return The actual {@linkplain LiteralTokenDescriptor literal token}
		 *         or {@code null}.
		 */
		@Nullable A_Token peekStringLiteral ()
		{
			final A_Token token = peekToken();
			if (token.isInstanceOfKind(
				LiteralTokenTypeDescriptor.create(
					TupleTypeDescriptor.stringTupleType())))
			{
				return token;
			}
			return null;
		}

		/**
		 * Return a new {@linkplain ParserState parser state} like this one, but
		 * with the given declaration added.
		 *
		 * @param declaration
		 *        The {@linkplain DeclarationNodeDescriptor declaration} to add
		 *        to the map of visible bindings.
		 * @return The new parser state including the declaration.
		 */
		ParserState withDeclaration (final A_Phrase declaration)
		{
			final A_String name = declaration.token().string();
			A_Map scopeMap = clientDataMap.mapAt(compilerScopeMapKey);
			assert !scopeMap.hasKey(name);
			scopeMap = scopeMap.mapAtPuttingCanDestroy(name, declaration, true);
			final A_Map newClientDataMap = clientDataMap.mapAtPuttingCanDestroy(
				compilerScopeMapKey, scopeMap, true);
			return new ParserState(
				position,
				newClientDataMap);
		}

		/**
		 * Record an expectation at the current parse position. The expectations
		 * captured at the rightmost parse position constitute the error message
		 * in case the parse fails.
		 *
		 * <p>
		 * The expectation is a {@linkplain Describer}, in case constructing a
		 * {@link String} frivolously would be prohibitive. There is also {@link
		 * #expected(String) another} version of this method that accepts a
		 * String directly.
		 * </p>
		 *
		 * @param describer
		 *        The {@code describer} to capture.
		 */
		void expected (final Describer describer)
		{
			synchronized (AbstractAvailCompiler.this)
			{
				if (position == greatestGuess)
				{
					greatExpectations.add(describer);
				}
				if (position > greatestGuess)
				{
					greatestGuess = position;
					greatExpectations.clear();
					greatExpectations.add(describer);
				}
			}
		}

		/**
		 * Record an expectation at the current parse position. The expectations
		 * captured at the rightmost parse position constitute the error message
		 * in case the parse fails.
		 *
		 * @param values
		 *        A list of arbitrary {@linkplain AvailObject Avail values} that
		 *        should be stringified.
		 * @param transformer
		 *        A {@linkplain Transformer1 transformer} that accepts the
		 *        stringified values and answers an expectation message.
		 */
		void expected (
			final List<? extends A_BasicObject> values,
			final Transformer1<List<String>, String> transformer)
		{
			expected(new Describer()
			{
				@Override
				public void describeThen (
					final Continuation1<String> continuation)
				{
					Interpreter.stringifyThen(
						runtime,
						values,
						new Continuation1<List<String>>()
						{
							@Override
							public void value (
								final @Nullable List<String> list)
							{
								continuation.value(transformer.value(list));
							}
						});
				}
			});
		}

		/**
		 * Record an indication of what was expected at this parse position.
		 *
		 * @param aString
		 *        The string to look up.
		 */
		void expected (final String aString)
		{
			expected(generate(aString));
		}

		/**
		 * Return the {@linkplain ModuleDescriptor module} under compilation for
		 * this {@linkplain ParserState}.
		 *
		 * @return The current module being compiled.
		 */
		public A_BasicObject currentModule ()
		{
			return AbstractAvailCompiler.this.module;
		}

		/**
		 * Evaluate the given parse node.  Pass the result to the continuation,
		 * or if it fails pass the exception to the failure continuation.
		 *
		 * @param expression The parse node to evaluate.
		 * @param continuation What to do with the result of evaluation.
		 * @param onFailure What to do instead if there was a problem.
		 */
		void evaluatePhraseThen (
			final A_Phrase expression,
			final Continuation1<AvailObject> continuation,
			final Continuation1<Throwable> onFailure)
		{
			AbstractAvailCompiler.this.evaluatePhraseThen(
				expression,
				position,
				false,
				continuation,
				onFailure);
		}
	}

	/**
	 * Parse one or more string literals separated by commas. This parse isn't
	 * backtracking like the rest of the grammar - it's greedy. It considers a
	 * comma followed by something other than a string literal to be an
	 * unrecoverable parsing error (not a backtrack).
	 *
	 * <p>
	 * Return the {@link ParserState} after the strings if successful, otherwise
	 * null. Populate the passed {@link List} with the {@linkplain
	 * StringDescriptor strings}.
	 * </p>
	 *
	 * @param start
	 *        Where to start parsing.
	 * @param strings
	 *        The initially empty list of strings to populate.
	 * @return The parser state after the list of strings, or {@code null} if
	 *         the list of strings is malformed.
	 */
	private static @Nullable ParserState parseStringLiterals (
		final ParserState start,
		final List<A_String> strings)
	{
		assert strings.isEmpty();

		ParserState state = start.afterToken();
		A_Token token = start.peekStringLiteral();
		if (token == null)
		{
			return start;
		}
		while (true)
		{
			// We just read a string literal.
			strings.add(token.literal());
			if (!state.peekToken(COMMA))
			{
				return state;
			}
			state = state.afterToken();
			token = state.peekStringLiteral();
			if (token == null)
			{
				state.expected("another string literal after comma");
				return null;
			}
			state = state.afterToken();
		}
	}

	/**
	 * Parse one or more string literals separated by commas. This parse isn't
	 * backtracking like the rest of the grammar - it's greedy. It considers a
	 * comma followed by something other than a string literal or final
	 * ellipsis to be an unrecoverable parsing error (not a backtrack).
	 * A {@linkplain #RIGHT_ARROW right arrow} between two strings indicates a
	 * rename.  An ellipsis at the end (the comma before it is optional)
	 * indicates that all names not explicitly mentioned should be imported.
	 *
	 * <p>
	 * Return the {@link ParserState} after the strings if successful, otherwise
	 * null. Populate the passed {@link Mutable} structures with strings,
	 * string → string entries producing a <em>reverse</em> map for renaming,
	 * negated strings, i.e., with a '-' character prefixed to indicate
	 * exclusion, and an optional trailing ellipsis character (the comma before
	 * it is optional).
	 * </p>
	 *
	 * @param start
	 *        Where to start parsing.
	 * @param names
	 *        The names that are mentioned explicitly, like "x".
	 * @param renames
	 *        The renames that are provided explicitly in the form "x"→"y".  In
	 *        such a case the new key is "y" and its associated value is "x".
	 * @param excludes
	 *        Names to exclude explicitly, like -"x".
	 * @param wildcard
	 *        Whether a trailing ellipsis was present.
	 * @return The parser state after the list of strings, or {@code null} if
	 *         the list of strings is malformed.
	 */
	private static @Nullable ParserState parseExplicitImportNames (
		final ParserState start,
		final Mutable<A_Set> names,
		final Mutable<A_Map> renames,
		final Mutable<A_Set> excludes,
		final Mutable<Boolean> wildcard)
	{
		// An explicit list of imports was provided, so it's not a wildcard
		// unless an ellipsis also explicitly occurs.
		boolean anything = false;
		wildcard.value = false;
		ParserState state = start;
		while (true)
		{
			A_Token token;
			if (state.peekToken(ELLIPSIS))
			{
				state = state.afterToken();
				wildcard.value = true;
				return state;
			}
			else if (state.peekToken(MINUS))
			{
				state = state.afterToken();
				final A_Token negatedToken = state.peekStringLiteral();
				if (negatedToken == null)
				{
					state.expected("string literal after negation");
					return null;
				}
				state = state.afterToken();
				excludes.value = excludes.value.setWithElementCanDestroy(
					negatedToken.literal(), false);
			}
			else if ((token = state.peekStringLiteral()) != null)
			{
				state = state.afterToken();
				if (state.peekToken(RIGHT_ARROW))
				{
					state = state.afterToken();
					final A_Token token2 = state.peekStringLiteral();
					if (token2 == null)
					{
						state.expected(
							"string literal token after right arrow");
						return null;
					}
					state = state.afterToken();
					if (renames.value.hasKey(token2.literal()))
					{
						state.expected(
							"renames to specify distinct target names");
						return null;
					}
					renames.value = renames.value.mapAtPuttingCanDestroy(
						token2.literal(), token.literal(), true);
				}
				else
				{
					names.value = names.value.setWithElementCanDestroy(
						token.literal(), true);
				}
			}
			else
			{
				if (anything)
				{
					state.expected(
						"a string literal, minus sign, or ellipsis"
						+ " after dangling comma");
					return null;
				}
				state.expected(
					"another string literal, minus sign, ellipsis, or"
					+ " end of import");
				return state;
			}
			anything = true;

			if (state.peekToken(ELLIPSIS))
			{
				// Allow ellipsis with no preceding comma: Fall through without
				// consuming it and let the start of the loop handle it.
			}
			else if (state.peekToken(COMMA))
			{
				// Eat the comma.
				state = state.afterToken();
			}
			else
			{
				state.expected("comma or ellipsis or end of import");
				return state;
			}
		}
	}

	/**
	 * Parse one or more {@linkplain ModuleDescriptor module} imports separated
	 * by commas. This parse isn't backtracking like the rest of the grammar -
	 * it's greedy. It considers any parse error to be unrecoverable (not a
	 * backtrack).
	 *
	 * <p>
	 * Return the {@link ParserState} after the imports if successful, otherwise
	 * {@code null}. Populate the passed {@linkplain List list} with {@linkplain
	 * TupleDescriptor 2-tuples}. Each tuple's first element is a module
	 * {@linkplain StringDescriptor name} and second element is the
	 * collection of {@linkplain MethodDefinitionDescriptor method} names to
	 * import.
	 * </p>
	 *
	 * @param start
	 *        Where to start parsing.
	 * @param imports
	 *        The initially empty list of module imports to populate.
	 * @param isExtension
	 *        Whether this is an Extends clause rather than Uses.  This controls
	 *        whether imported names are re-exported.
	 * @return The parser state after the list of imports, or {@code null} if
	 *         the list of imports is malformed.
	 */
	private static @Nullable ParserState parseModuleImports (
		final ParserState start,
		final List<ModuleImport> imports,
		final boolean isExtension)
	{
		boolean anyEntries = false;
		ParserState state = start;
		do
		{
			final A_Token token = state.peekStringLiteral();
			if (token == null)
			{
				if (anyEntries)
				{
					state.expected("another module name after comma");
					return null;
				}
				state.expected("a comma-separated list of module names");
				// It's legal to have no strings.
				return state;
			}
			anyEntries = true;

			final A_String moduleName = token.literal();
			state = state.afterToken();

			final List<A_String> versions = new ArrayList<>();
			if (state.peekToken(OPEN_PARENTHESIS))
			{
				state = state.afterToken();
				state = parseStringLiterals(state, versions);
				if (state == null)
				{
					return null;
				}
				if (!state.peekToken(
					CLOSE_PARENTHESIS,
					"a close parenthesis following acceptable versions"))
				{
					return null;
				}
				state = state.afterToken();
			}

			final Mutable<A_Set> names = new Mutable<>(SetDescriptor.empty());
			final Mutable<A_Map> renames = new Mutable<>(MapDescriptor.empty());
			final Mutable<A_Set> excludes =
				new Mutable<>(SetDescriptor.empty());
			final Mutable<Boolean> wildcard = new Mutable<>(false);

			if (state.peekToken(EQUALS))
			{
				state = state.afterToken();
				if (!state.peekToken(
					OPEN_PARENTHESIS,
					"an open parenthesis preceding import list"))
				{
					return null;
				}
				state = state.afterToken();
				state = parseExplicitImportNames(
					state,
					names,
					renames,
					excludes,
					wildcard);
				if (state == null)
				{
					return null;
				}
				if (!state.peekToken(
					CLOSE_PARENTHESIS,
					"a close parenthesis following import list"))
				{
					return null;
				}
				state = state.afterToken();
			}
			else
			{
				wildcard.value = true;
			}

			try
			{
				imports.add(
					new ModuleImport(
						moduleName,
						SetDescriptor.fromCollection(versions),
						isExtension,
						names.value,
						renames.value,
						excludes.value,
						wildcard.value));
			}
			catch (final ImportValidationException e)
			{
				state.expected(e.getMessage());
				return null;
			}
			if (state.peekToken(COMMA))
			{
				state = state.afterToken();
			}
			else
			{
				return state;
			}
		}
		while (true);
	}

	/**
	 * Read the source string for the {@linkplain ModuleDescriptor module}
	 * specified by the fully-qualified {@linkplain ModuleName module name}.
	 *
	 * @param resolvedName
	 *        The {@linkplain ResolvedModuleName resolved name} of the module.
	 * @param continuation
	 *        What to do after the source module has been completely read.
	 *        Accepts the source text of the module.
	 * @param fail
	 *        What to do in the event of failure. This is a continuation that
	 *        accepts the {@linkplain Throwable throwable} responsible for
	 *        abnormal termination.
	 * @throws IOException
	 *         If the source module could not be opened or read for any reason.
	 */
	private static void extractSourceThen (
			final ResolvedModuleName resolvedName,
			final Continuation1<String> continuation,
			final Continuation1<Throwable> fail)
		throws IOException
	{
		final AvailRuntime runtime = AvailRuntime.current();
		final File ref = resolvedName.sourceReference();
		assert ref != null;
		final CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder();
		final ByteBuffer input = ByteBuffer.allocateDirect(4096);
		final CharBuffer output = CharBuffer.allocate(4096);
		final StringBuilder sourceBuilder = new StringBuilder(4096);
		final Mutable<Long> filePosition = new Mutable<Long>(0L);
		final AsynchronousFileChannel file;
		try
		{
			file = runtime.openFile(ref.toPath(), StandardOpenOption.READ);
		}
		catch (final IOException e)
		{
			fail.value(e);
			return;
		}
		final MutableOrNull<CompletionHandler<Integer, Void>> handler =
			new MutableOrNull<CompletionHandler<Integer,Void>>();
		handler.value =
			new CompletionHandler<Integer, Void>()
			{
				@Override
				public void completed (
					@Nullable final Integer bytesRead,
					@Nullable final Void nothing)
				{
					try
					{
						assert bytesRead != null;
						boolean moreInput = true;
						if (bytesRead == -1)
						{
							moreInput = false;
						}
						else
						{
							filePosition.value += bytesRead;
						}
						input.flip();
						final CoderResult result = decoder.decode(
							input, output, !moreInput);
						// If the decoder didn't consume all of the bytes, then
						// preserve the unconsumed bytes in the next buffer (for
						// decoding).
						if (input.hasRemaining())
						{
							final int delta = input.limit() - input.position();
							for (int i = 0; i < delta; i++)
							{
								final byte b = input.get(input.position() + i);
								input.put(i, b);
							}
							input.limit(input.capacity());
							input.position(delta);
						}
						else
						{
							input.clear();
						}
						// UTF-8 never compresses data, so the number of
						// characters encoded can be no greater than the number
						// of bytes encoded. The input buffer and the output
						// buffer are equally sized (in units), so an overflow
						// cannot occur.
						assert !result.isOverflow();
						if (result.isError())
						{
							result.throwException();
						}
						output.flip();
						sourceBuilder.append(output.toString());
						// If more input remains, then queue another read.
						if (moreInput)
						{
							output.clear();
							file.read(
								input,
								filePosition.value,
								null,
								handler.value);
						}
						// Otherwise, close the file channel and queue the
						// original continuation.
						else
						{
							decoder.flush(output);
							sourceBuilder.append(output.toString());
							file.close();
							runtime.execute(
								new AvailTask(
									FiberDescriptor.compilerPriority,
									new Continuation0()
									{
										@Override
										public void value ()
										{
											continuation.value(
												sourceBuilder.toString());
										}
									}));
						}
					}
					catch (final Throwable e)
					{
						fail.value(e);
					}
				}

				@Override
				public void failed (
					@Nullable final Throwable throwable,
					@Nullable final Void attachment)
				{
					fail.value(throwable);
				}
			};
		// Kick off the asynchronous read.
		file.read(input, 0L, null, handler.value);
	}

	/**
	 * Tokenize the {@linkplain ModuleDescriptor module} specified by the
	 * fully-qualified {@linkplain ModuleName module name}.
	 *
	 * @param source
	 *        The {@linkplain String string} containing the module's source
	 *        code.
	 * @param stopAfterBodyToken
	 *        Stop scanning after encountering the BODY token?
	 * @return The {@linkplain ResolvedModuleName resolved module name}.
	 * @throws AvailCompilerException
	 *         If tokenization failed for any reason.
	 */
	static List<A_Token> tokenize (
			final String source,
			final boolean stopAfterBodyToken)
		throws AvailCompilerException
	{
		return AvailScanner.scanString(source, stopAfterBodyToken);
	}

	/**
	 * Map the tree through the (destructive) transformation specified by
	 * aBlock, children before parents. The block takes three arguments: the
	 * node, its parent, and the list of enclosing block nodes. Answer the
	 * resulting tree.
	 *
	 * @param object
	 *        The current {@linkplain ParseNodeDescriptor parse node}.
	 * @param aBlock
	 *        What to do with each descendant.
	 * @param parentNode
	 *        This node's parent.
	 * @param outerNodes
	 *        The list of {@linkplain BlockNodeDescriptor blocks} surrounding
	 *        this node, from outermost to innermost.
	 * @param nodeMap
	 *        The {@link Map} from old {@linkplain ParseNodeDescriptor parse
	 *        nodes} to newly copied, mutable parse nodes.  This should ensure
	 *        the consistency of declaration references.
	 * @return A replacement for this node, possibly this node itself.
	 */
	static A_Phrase treeMapWithParent (
		final A_Phrase object,
		final Transformer3<
				A_Phrase,
				A_Phrase,
				List<A_Phrase>,
				A_Phrase>
			aBlock,
		final A_Phrase parentNode,
		final List<A_Phrase> outerNodes,
		final Map<A_Phrase, A_Phrase> nodeMap)
	{
		if (nodeMap.containsKey(object))
		{
			return object;
		}
		final A_Phrase objectCopy = object.copyMutableParseNode();
		objectCopy.childrenMap(
			new Transformer1<A_Phrase, A_Phrase>()
			{
				@Override
				public A_Phrase value (final @Nullable A_Phrase child)
				{
					assert child != null;
					assert child.isInstanceOfKind(PARSE_NODE.mostGeneralType());
					return treeMapWithParent(
						child,
						aBlock,
						objectCopy,
						outerNodes,
						nodeMap);
				}
			});
		final A_Phrase transformed = aBlock.value(
			objectCopy,
			parentNode,
			outerNodes);
		assert transformed != null;
		transformed.makeShared();
		nodeMap.put(object, transformed);
		return transformed;
	}

	/**
	 * Answer a {@linkplain Generator generator} that will produce the given
	 * string.
	 *
	 * @param string
	 *        The exact string to generate.
	 * @return A generator that produces the string that was provided.
	 */
	Describer generate (final String string)
	{
		return new Describer()
		{
			@Override
			public void describeThen (final Continuation1<String> continuation)
			{
				continuation.value(string);
			}
		};
	}

	/**
	 * Report an error by throwing an {@link AvailCompilerException}. The
	 * exception encapsulates the {@linkplain ResolvedModuleName module name} of
	 * the {@linkplain ModuleDescriptor module} undergoing compilation, the
	 * error string, and the text position. This position is the rightmost
	 * position encountered during the parse, and the error strings in {@link
	 * #greatExpectations} are the things that were expected but not found at
	 * that position. This seems to work very well in practice.
	 */
	void reportError ()
	{
		final A_Token token;
		final List<Describer> expectations;
		synchronized (this)
		{
			token = tokens.get(greatestGuess);
			expectations = new ArrayList<Describer>(greatExpectations);
		}
		reportError(token, "Expected...", expectations);
	}

	/** A bunch of dash characters, wide enough to catch the eye. */
	static final String rowOfDashes;

	static
	{
		final char[] chars = new char[70];
		Arrays.fill(chars, '-');
		rowOfDashes = new String(chars);
	}

	/**
	 * Report an error by throwing an {@link AvailCompilerException}. The
	 * exception encapsulates the {@linkplain ResolvedModuleName module name} of
	 * the {@linkplain ModuleDescriptor module} undergoing compilation, the
	 * error string, and the text position. This position is the rightmost
	 * position encountered during the parse, and the error strings produced by
	 * the {@linkplain Describer describers} in {@link #greatExpectations} are
	 * the things that were expected but not found at that position. This seems
	 * to work very well in practice.
	 *
	 * @param token
	 *        Where the error occurred.
	 * @param banner
	 *        The string that introduces the problem text.
	 * @param problems
	 *        A list of {@linkplain Describer describers} that may be
	 *        invoked to produce problem strings.
	 * @throws AvailCompilerException
	 *         Always thrown.
	 */
	void reportError (
			final A_Token token,
			final String banner,
			final List<Describer> problems)
		throws AvailCompilerException
	{
		assert problems.size() > 0 : "Bug - empty problem list";
		final long charPos = token.start();
		final String sourceUpToError = source.substring(0, (int) charPos);
		final int startOfPreviousLine = sourceUpToError.lastIndexOf('\n') + 1;
		@SuppressWarnings("resource")
		final Formatter text = new Formatter();
		text.format("%n");
		int wedges = 3;
		for (int i = startOfPreviousLine; i < charPos; i++)
		{
			if (source.codePointAt(i) == '\t')
			{
				while (wedges > 0)
				{
					text.format(">");
					wedges--;
				}
				text.format("\t");
			}
			else
			{
				if (wedges > 0)
				{
					text.format(">");
					wedges--;
				}
				else
				{
					text.format(" ");
				}
			}
		}
		text.format("^-- %s", banner);
		text.format("%n>>>%s", rowOfDashes);
		final Set<String> alreadySeen = new HashSet<String>(problems.size());
		final Mutable<Integer> outstanding =
			new Mutable<Integer>(problems.size());
		final Continuation1<String> decrement = new Continuation1<String>()
		{
			@Override
			public void value (final @Nullable String message)
			{
				assert message != null;
				synchronized (outstanding)
				{
					// Ignore duplicate messages.
					if (!alreadySeen.contains(message))
					{
						alreadySeen.add(message);
						text.format(
							"%n>>>\t%s",
							message.replace("\n", "\n>>>\t"));
					}
					// Decrement the count of outstanding describers. When
					// the count reaches zero, then produce the remainder of
					// the message.
					outstanding.value--;
					if (outstanding.value == 0)
					{
						text.format(
							"%n(file=\"%s\", line=%d)",
							moduleHeader.moduleName.qualifiedName(),
							token.lineNumber());
						text.format("%n>>>%s", rowOfDashes);
						int endOfLine = source.indexOf('\n', (int) charPos);
						if (endOfLine == -1)
						{
							endOfLine = source.length();
						}
						final String textString = text.toString();
						final AvailCompilerException killer =
							new AvailCompilerException(
								moduleHeader.moduleName,
								charPos,
								endOfLine,
								textString);
						killer.fillInStackTrace();
						compilationFailed(killer);
					}
				}
			}
		};
		// Generate the error strings in parallel.
		for (final Describer describer : problems)
		{
			eventuallyDo(new Continuation0()
			{
				@Override
				public void value ()
				{
					describer.describeThen(decrement);
				}
			});
		}
	}

	/**
	 * A statement was parsed correctly in two different ways. There may be more
	 * ways, but we stop after two as it's already an error. Report the error.
	 *
	 * @param where
	 *        Where the expressions were parsed from.
	 * @param interpretation1
	 *        The first interpretation as a {@linkplain ParseNodeDescriptor
	 *        parse node}.
	 * @param interpretation2
	 *        The second interpretation as a {@linkplain ParseNodeDescriptor
	 *        parse node}.
	 */
	@InnerAccess void reportAmbiguousInterpretations (
		final ParserState where,
		final A_Phrase interpretation1,
		final A_Phrase interpretation2)
	{
		final Mutable<A_Phrase> node1 =
			new Mutable<A_Phrase>(interpretation1);
		final Mutable<A_Phrase> node2 =
			new Mutable<A_Phrase>(interpretation2);
		findParseTreeDiscriminants(node1, node2);
		where.expected(
			new Describer()
			{
				@Override
				public void describeThen (final Continuation1<String> c)
				{
					final StringBuilder builder = new StringBuilder(200);
					builder.append("unambiguous interpretation.  ");
					builder.append("Here are two possible parsings...\n");
					builder.append("\t");
					builder.append(node1.value.toString());
					builder.append("\n\t");
					builder.append(node2.value.toString());
					c.value(builder.toString());
				}
			});
		reportError();
	}

	/**
	 * Given two unequal parse trees, find the smallest descendant nodes that
	 * still contain all the differences.  The given {@link Mutable} objects
	 * initially contain references to the root nodes, but are updated to refer
	 * to the most specific pair of nodes that contain all the differences.
	 *
	 * @param node1
	 *            A {@code Mutable} reference to a {@linkplain
	 *            ParseNodeDescriptor parse tree}.  Updated to hold the most
	 *            specific difference.
	 * @param node2
	 *            The {@code Mutable} reference to the other parse tree.
	 *            Updated to hold the most specific difference.
	 */
	private void findParseTreeDiscriminants (
		final Mutable<A_Phrase> node1,
		final Mutable<A_Phrase> node2)
	{
		while (true)
		{
			assert !node1.value.equals(node2.value);
			if (!node1.value.kind().parseNodeKind().equals(
				node2.value.kind().parseNodeKind()))
			{
				// The nodes are different kinds, so present them as what's
				// different.
				return;
			}
			if (node1.value.kind().parseNodeKindIsUnder(SEND_NODE)
				&& !node1.value.bundle().equals(node2.value.bundle()))
			{
				// They're sends of different messages, so don't go any deeper.
				return;
			}
			final List<A_Phrase> parts1 = new ArrayList<A_Phrase>();
			node1.value.childrenDo(new Continuation1<A_Phrase>()
			{
				@Override
				public void value (final @Nullable A_Phrase part)
				{
					parts1.add(part);
				}
			});
			final List<A_Phrase> parts2 = new ArrayList<A_Phrase>();
			node2.value.childrenDo(new Continuation1<A_Phrase>()
				{
					@Override
					public void value (final @Nullable A_Phrase part)
					{
						parts2.add(part);
					}
				});
			final boolean isBlock =
				node1.value.kind().parseNodeKindIsUnder(BLOCK_NODE);
			if (parts1.size() != parts2.size() && !isBlock)
			{
				// Different structure at this level.
				return;
			}
			final List<Integer> differentIndices = new ArrayList<Integer>();
			for (int i = 0; i < min(parts1.size(), parts2.size()); i++)
			{
				if (!parts1.get(i).equals(parts2.get(i)))
				{
					differentIndices.add(i);
				}
			}
			if (isBlock)
			{
				if (differentIndices.size() == 0)
				{
					// Statement or argument lists are probably different sizes.
					// Use the block itself.
					return;
				}
				// Show the first argument or statement that differs.
				// Fall through.
			}
			else if (differentIndices.size() != 1)
			{
				// More than one part differs, so we can't drill deeper.
				return;
			}
			// Drill into the only part that differs.
			node1.value = parts1.get(differentIndices.get(0));
			node2.value = parts2.get(differentIndices.get(0));
		}
	}

	/**
	 * Attempt the zero-argument continuation. The implementation is free to
	 * execute it now or to put it in a bag of continuations to run later <em>in
	 * an arbitrary order</em>. There may be performance and/or scale benefits
	 * to processing entries in FIFO, LIFO, or some hybrid order, but the
	 * correctness is not affected by a choice of order.
	 *
	 * @param continuation
	 *        What to do at some point in the future.
	 */
	void eventuallyDo (final Continuation0 continuation)
	{
		runtime.execute(new AvailTask(
			FiberDescriptor.compilerPriority,
			new Continuation0()
			{
				@Override
				public void value ()
				{
					try
					{
						continuation.value();
					}
					catch (final Throwable e)
					{
						compilationFailed(e);
					}
				}
			}));
	}

	/**
	 * Start a work unit.
	 */
	protected synchronized void startWorkUnit ()
	{
		if (terminator != null)
		{
			// Don't add any new tasks if canceling.
			return;
		}
		workUnitsQueued++;
	}

	/**
	 * Start several work units.
	 *
	 * @param count
	 *        The number of work units to start.
	 */
	protected synchronized void startWorkUnits (final int count)
	{
		if (terminator != null)
		{
			// Don't add any new tasks if canceling.
			return;
		}
		workUnitsQueued += count;
	}

	/**
	 * Construct and answer a {@linkplain Continuation1 continuation} that
	 * wraps the specified continuation in logic that will increment the
	 * {@linkplain #workUnitsCompleted count of completed work units} and
	 * potentially call the {@linkplain #noMoreWorkUnits unambiguous
	 * statement}.
	 *
	 * @param continuation
	 *        What to do as a work unit.
	 * @return A new continuation. It accepts an argument of some kind, which
	 *         will be passed forward to the argument continuation.
	 */
	protected <ArgType> Continuation1<ArgType> workUnitCompletion (
		final Continuation1<ArgType> continuation)
	{
		assert noMoreWorkUnits != null;
		return new Continuation1<ArgType>()
		{
			@Override
			public void value (final @Nullable ArgType value)
			{
				boolean quiescent = false;
				try
				{
					// Don't actually run tasks if canceling.
					if (terminator == null)
					{
						continuation.value(value);
					}
				}
				catch (final Throwable e)
				{
					compilationFailed(e);
					return;
				}
				finally
				{
					synchronized (AbstractAvailCompiler.this)
					{
						workUnitsCompleted++;
						if (workUnitsCompleted == workUnitsQueued)
						{
							quiescent = true;
						}
					}
				}
				try
				{
					if (quiescent)
					{
						final Continuation0 noMore = noMoreWorkUnits;
						assert noMore != null;
						noMoreWorkUnits = null;
						noMore.value();
					}
				}
				catch (final Throwable e)
				{
					compilationFailed(e);
				}
			}
		};
	}

	/**
	 * Eventually execute the specified {@linkplain Continuation0 continuation}
	 * as a {@linkplain AbstractAvailCompiler compiler} work unit.
	 *
	 * @param continuation
	 *        What to do at some point in the future.
	 * @param description
	 *        Debugging information about what is to be parsed.
	 * @param where
	 *        Where the parse is happening.
	 */
	void workUnitDo (
		final Continuation0 continuation,
		final String description,
		final ParserState where)
	{
		startWorkUnit();
		final Continuation1<AvailObject> workUnit = workUnitCompletion(
			new Continuation1<AvailObject>()
			{
				@Override
				public void value (final @Nullable AvailObject ignored)
				{
					continuation.value();
				}
			});
		runtime.execute(new ParsingTask(
			description,
			where,
			new Continuation0()
			{
				@Override
				public void value ()
				{
					workUnit.value(null);
				}
			}));
	}

	/**
	 * Wrap the {@linkplain Continuation1 continuation of one argument} inside a
	 * {@linkplain Continuation0 continuation of zero arguments} and record that
	 * as per {@linkplain #workUnitDo(Continuation0, String, ParserState)}.
	 *
	 * @param <ArgType>
	 *        The type of argument to the given continuation.
	 * @param here
	 *        Where to start parsing when the continuation runs.
	 * @param continuation
	 *        What to execute with the passed argument.
	 * @param argument
	 *        What to pass as an argument to the provided {@linkplain
	 *        Continuation1 one-argument continuation}.
	 */
	<ArgType> void attempt (
		final ParserState here,
		final Con<ArgType> continuation,
		final ArgType argument)
	{
		workUnitDo(
			new Continuation0()
			{
				@Override
				public void value ()
				{
					continuation.value(here, argument);
				}
			},
			continuation.description,
			here);
	}

	/**
	 * Look up a local declaration that has the given name, or null if not
	 * found.
	 *
	 * @param start
	 *        Where to start parsing.
	 * @param name
	 *        The name of the variable declaration for which to look.
	 * @return The declaration or {@code null}.
	 */
	@Nullable A_Phrase lookupLocalDeclaration (
		final ParserState start,
		final A_String name)
	{
		final A_Map scopeMap = start.clientDataMap.mapAt(compilerScopeMapKey);
		if (scopeMap.hasKey(name))
		{
			return scopeMap.mapAt(name);
		}
		return null;
	}

	/**
	 * Start definition of a {@linkplain ModuleDescriptor module}. The entire
	 * definition can be rolled back because the {@linkplain Interpreter
	 * interpreter}'s context module will contain all methods and precedence
	 * rules defined between the transaction start and the rollback (or commit).
	 * Committing simply clears this information.
	 */
	void startModuleTransaction ()
	{
		loader = new AvailLoader(module);
	}

	/**
	 * Rollback the {@linkplain ModuleDescriptor module} that was defined since
	 * the most recent {@link #startModuleTransaction()}.
	 */
	void rollbackModuleTransaction ()
	{
		module.removeFrom(loader());
	}

	/**
	 * Commit the {@linkplain ModuleDescriptor module} that was defined since
	 * the most recent {@link #startModuleTransaction()}.
	 */
	void commitModuleTransaction ()
	{
		runtime.addModule(module);
		module.cleanUpAfterCompile();
	}

	/**
	 * Evaluate the specified {@linkplain FunctionDescriptor function} in the
	 * module's context; lexically enclosing variables are not considered in
	 * scope, but module variables and constants are in scope.
	 *
	 * @param function
	 *        A function.
	 * @param args
	 *        The arguments to the function.
	 * @param shouldSerialize
	 *        {@code true} if the generated function should be serialized,
	 *        {@code false} otherwise.
	 * @param onSuccess
	 *        What to do with the result of the evaluation.
	 * @param onFailure
	 *        What to do with a terminal {@linkplain Throwable throwable}.
	 */
	protected void evaluateFunctionThen (
		final A_Function function,
		final List<? extends A_BasicObject> args,
		final boolean shouldSerialize,
		final Continuation1<AvailObject> onSuccess,
		final Continuation1<Throwable> onFailure)
	{
		synchronized (this)
		{
			if (shouldSerialize)
			{
				serializer.serialize(function);
			}
		}
		final A_Fiber fiber = FiberDescriptor.newLoaderFiber(
			function.kind().returnType(),
			loader());
		fiber.resultContinuation(onSuccess);
		fiber.failureContinuation(onFailure);
		Interpreter.runOutermostFunction(runtime, fiber, function, args);
	}

	/**
	 * Evaluate the specified semantic restriction {@linkplain
	 * FunctionDescriptor function} in the module's context; lexically enclosing
	 * variables are not considered in scope, but module variables and constants
	 * are in scope.
	 *
	 * @param restriction
	 *        A {@linkplain SemanticRestrictionDescriptor semantic restriction}.
	 * @param args
	 *        The arguments to the function.
	 * @param onSuccess
	 *        What to do with the result of the evaluation.
	 * @param onFailure
	 *        What to do with a terminal {@linkplain Throwable throwable}.
	 */
	protected void evaluateSemanticRestrictionFunctionThen (
		final A_SemanticRestriction restriction,
		final List<AvailObject> args,
		final Continuation1<AvailObject> onSuccess,
		final Continuation1<Throwable> onFailure)
	{
		final A_Function function = restriction.function();
		final A_Fiber fiber = FiberDescriptor.newLoaderFiber(
			function.kind().returnType(),
			loader());
		fiber.setGeneralFlag(GeneralFlag.APPLYING_SEMANTIC_RESTRICTION);
		fiber.resultContinuation(onSuccess);
		fiber.failureContinuation(onFailure);
		Interpreter.runOutermostFunction(runtime, fiber, function, args);
	}

	/**
	 * Generate a {@linkplain FunctionDescriptor function} from the specified
	 * {@linkplain ParseNodeDescriptor phrase} and evaluate it in the module's
	 * context; lexically enclosing variables are not considered in scope, but
	 * module variables and constants are in scope.
	 *
	 * @param expressionNode
	 *        A {@linkplain ParseNodeDescriptor parse node}.
	 * @param lineNumber
	 *        The line number on which the expression starts.
	 * @param shouldSerialize
	 *        {@code true} if the generated function should be serialized,
	 *        {@code false} otherwise.
	 * @param onSuccess
	 *        What to do with the result of the evaluation.
	 * @param onFailure
	 *        What to do with a terminal {@linkplain Throwable throwable}.
	 */
	@InnerAccess void evaluatePhraseThen (
		final A_Phrase expressionNode,
		final int lineNumber,
		final boolean shouldSerialize,
		final Continuation1<AvailObject> onSuccess,
		final Continuation1<Throwable> onFailure)
	{
		evaluateFunctionThen(
			FunctionDescriptor.createFunctionForPhrase(
				expressionNode, module, lineNumber),
			Collections.<AvailObject>emptyList(),
			shouldSerialize,
			onSuccess,
			onFailure);
	}

	/**
	 * Evaluate a parse tree node. It's a top-level statement in a module.
	 * Declarations are handled differently - they cause a variable to be
	 * declared in the module's scope.
	 *
	 * @param startState
	 *        The start {@linkplain ParserState state}, for line number
	 *        reporting.
	 * @param expressionOrMacro
	 *        The expression to compile and evaluate as a top-level statement in
	 *        the module.
	 * @param onSuccess
	 *        What to do after success. Note that the result of executing the
	 *        statement must be {@linkplain NilDescriptor#nil() nil}, so there
	 *        is no point accepting in the result. Hence the {@linkplain
	 *        Continuation0 nullary continuation}.
	 * @param onFailure
	 *        What to do with a terminal {@linkplain Throwable throwable}.
	 */
	void evaluateModuleStatementThen (
		final ParserState startState,
		final A_Phrase expressionOrMacro,
		final Continuation0 onSuccess,
		final Continuation1<Throwable> onFailure)
	{
		final A_Phrase expression = expressionOrMacro.stripMacro();
		if (!expression.isInstanceOfKind(DECLARATION_NODE.mostGeneralType()))
		{
			// Only record module statements that aren't declarations. Users of
			// the module don't care if a module variable or constant is only
			// reachable from the module's methods.
			evaluatePhraseThen(
				expression,
				startState.peekToken().lineNumber(),
				true,
				new Continuation1<AvailObject>()
				{
					@Override
					public void value (final @Nullable AvailObject ignored)
					{
						onSuccess.value();
					}
				},
				onFailure);
			return;
		}
		// It's a declaration, but the parser couldn't previously tell that it
		// was at module scope.
		final A_String name = expression.token().string();
		switch (expression.declarationKind())
		{
			case LOCAL_CONSTANT:
			{
				evaluatePhraseThen(
					expression.initializationExpression(),
					expression.token().lineNumber(),
					false,
					new Continuation1<AvailObject>()
					{
						@Override
						public void value (final @Nullable AvailObject val)
						{
							assert val != null;
							final A_Variable var =
								VariableDescriptor.forInnerType(
									AbstractEnumerationTypeDescriptor
										.withInstance(val));
							module.addConstantBinding(name, var);
							// Create a module variable declaration (i.e.,
							// cheat) JUST for this initializing assignment.
							final A_Phrase decl =
								DeclarationNodeDescriptor.newModuleVariable(
									expression.token(),
									var,
									expression.initializationExpression());
							final A_Phrase assign =
								AssignmentNodeDescriptor.from(
									VariableUseNodeDescriptor.newUse(
										expression.token(), decl),
									LiteralNodeDescriptor.syntheticFrom(val),
									false);
							final A_Function function =
								FunctionDescriptor.createFunctionForPhrase(
									assign,
									module,
									expression.token().lineNumber());
							synchronized (AbstractAvailCompiler.this)
							{
								serializer.serialize(function);
							}
							var.setValue(val);
							onSuccess.value();
						}
					},
					onFailure);
				break;
			}
			case LOCAL_VARIABLE:
			{
				final A_Variable var = VariableDescriptor.forInnerType(
					expression.declaredType());
				module.addVariableBinding(name, var);
				if (!expression.initializationExpression().equalsNil())
				{
					final A_Phrase decl =
						DeclarationNodeDescriptor.newModuleVariable(
							expression.token(),
							var,
							expression.initializationExpression());
					final A_Phrase assign = AssignmentNodeDescriptor.from(
						VariableUseNodeDescriptor.newUse(
							expression.token(),
							decl),
						expression.initializationExpression(),
						false);
					final A_Function function =
						FunctionDescriptor.createFunctionForPhrase(
						assign,
						module,
						expression.token().lineNumber());
					synchronized (AbstractAvailCompiler.this)
					{
						serializer.serialize(function);
					}
					evaluatePhraseThen(
						expression.initializationExpression(),
						expression.token().lineNumber(),
						false,
						new Continuation1<AvailObject>()
						{
							@Override
							public void value (final @Nullable AvailObject val)
							{
								assert val != null;
								var.setValue(val);
								onSuccess.value();
							}
						},
						onFailure);
				}
				else
				{
					onSuccess.value();
				}
				break;
			}
			default:
				assert false
					: "Expected top-level declaration to be parsed as local";
		}
	}


	/**
	 * Report that the parser was expecting one of several keywords. The
	 * keywords are keys of the {@linkplain MapDescriptor map} argument
	 * {@code incomplete}.
	 *
	 * @param where
	 *        Where the keywords were expected.
	 * @param incomplete
	 *        A map of partially parsed keywords, where the keys are the strings
	 *        that were expected at this position.
	 * @param caseInsensitive
	 *        {@code true} if the parsed keywords are case-insensitive, {@code
	 *        false} otherwise.
	 */
	void expectedKeywordsOf (
		final ParserState where,
		final A_Map incomplete,
		final boolean caseInsensitive)
	{
		where.expected(
			new Describer()
			{
				@Override
				public void describeThen (final Continuation1<String> c)
				{
					final StringBuilder builder = new StringBuilder(200);
					if (caseInsensitive)
					{
						builder.append(
							"one of the following case-insensitive internal "
							+ "keywords:");
					}
					else
					{
						builder.append(
							"one of the following internal keywords:");
					}
					final List<String> sorted = new ArrayList<String>(
						incomplete.mapSize());
					final boolean detail = incomplete.mapSize() < 10;
					for (final MapDescriptor.Entry entry
						: incomplete.mapIterable())
					{
						final String string = entry.key().asNativeString();
						if (detail)
						{
							final StringBuilder buffer = new StringBuilder();
							buffer.append(string);
							buffer.append(" (");
							boolean first = true;
							for (final MapDescriptor.Entry successorBundleEntry
								: entry.value().allBundles().mapIterable())
							{
								if (first)
								{
									first = false;
								}
								else
								{
									buffer.append(", ");
								}
								buffer.append(
									successorBundleEntry.key().toString());
							}
							buffer.append(")");
							sorted.add(buffer.toString());
						}
						else
						{
							sorted.add(string);
						}
					}
					Collections.sort(sorted);
					boolean startOfLine = true;
					builder.append("\n\t");
					final int leftColumn = 4 + 4; // ">>> " and a tab.
					int column = leftColumn;
					for (final String s : sorted)
					{
						if (!startOfLine)
						{
							builder.append("  ");
							column += 2;
						}
						startOfLine = false;
						final int lengthBefore = builder.length();
						builder.append(s);
						column += builder.length() - lengthBefore;
						if (detail || column + 2 + s.length() > 80)
						{
							builder.append("\n\t");
							column = leftColumn;
							startOfLine = true;
						}
					}
					c.value(builder.toString());
				}
			});
	}

	/**
	 * Parse a send node. To prevent infinite left-recursion and false
	 * ambiguity, we only allow a send with a leading keyword to be parsed from
	 * here, since leading underscore sends are dealt with iteratively
	 * afterward.
	 *
	 * @param start
	 *            Where to start parsing.
	 * @param continuation
	 *            What to do after parsing a complete send node.
	 */
	protected void parseLeadingKeywordSendThen (
		final ParserState start,
		final Con<A_Phrase> continuation)
	{
		parseRestOfSendNode(
			start,
			loader().rootBundleTree(),
			null,
			start,
			false,  // Nothing consumed yet.
			Collections.<A_Phrase>emptyList(),
			Collections.<Integer>emptyList(),
			continuation);
	}

	/**
	 * Parse a send node whose leading argument has already been parsed.
	 *
	 * @param start
	 *            Where to start parsing.
	 * @param leadingArgument
	 *            The argument that was already parsed.
	 * @param initialTokenPosition
	 *            Where the leading argument started.
	 * @param continuation
	 *            What to do after parsing a send node.
	 */
	void parseLeadingArgumentSendAfterThen (
		final ParserState start,
		final A_Phrase leadingArgument,
		final ParserState initialTokenPosition,
		final Con<A_Phrase> continuation)
	{
		assert start.position != initialTokenPosition.position;
		assert leadingArgument != null;
		parseRestOfSendNode(
			start,
			loader().rootBundleTree(),
			leadingArgument,
			initialTokenPosition,
			false,  // Leading argument does not yet count as something parsed.
			Collections.<A_Phrase>emptyList(),
			Collections.<Integer>emptyList(),
			continuation);
	}

	/**
	 * Parse an expression with an optional lead-argument message send around
	 * it. Backtracking will find all valid interpretations.
	 *
	 * @param startOfLeadingArgument
	 *            Where the leading argument started.
	 * @param afterLeadingArgument
	 *            Just after the leading argument.
	 * @param node
	 *            An expression that acts as the first argument for a potential
	 *            leading-argument message send, or possibly a chain of them.
	 * @param continuation
	 *            What to do with either the passed node, or the node wrapped in
	 *            suitable leading-argument message sends.
	 */
	void parseOptionalLeadingArgumentSendAfterThen (
		final ParserState startOfLeadingArgument,
		final ParserState afterLeadingArgument,
		final A_Phrase node,
		final Con<A_Phrase> continuation)
	{
		// It's optional, so try it with no wrapping.
		attempt(afterLeadingArgument, continuation, node);

		// Don't wrap it if its type is top.
		if (node.expressionType().equals(TOP.o()))
		{
			return;
		}

		// Try to wrap it in a leading-argument message send.
		attempt(
			afterLeadingArgument,
			new Con<A_Phrase>("Possible leading argument send")
			{
				@Override
				public void value (
					final @Nullable ParserState afterLeadingArgument2,
					final @Nullable A_Phrase node2)
				{
					assert afterLeadingArgument2 != null;
					assert node2 != null;
					parseLeadingArgumentSendAfterThen(
						afterLeadingArgument2,
						node2,
						startOfLeadingArgument,
						new Con<A_Phrase>("Leading argument send")
						{
							@Override
							public void value (
								final @Nullable ParserState afterSend,
								final @Nullable A_Phrase leadingSend)
							{
								assert afterSend != null;
								assert leadingSend != null;
								parseOptionalLeadingArgumentSendAfterThen(
									startOfLeadingArgument,
									afterSend,
									leadingSend,
									continuation);
							}
						});
				}
			},
			node);
	}

	/**
	 * We've parsed part of a send. Try to finish the job.
	 *
	 * @param start
	 *            Where to start parsing.
	 * @param bundleTree
	 *            The bundle tree used to parse at this position.
	 * @param firstArgOrNull
	 *            Either null or an argument that must be consumed before any
	 *            keywords (or completion of a send).
	 * @param consumedAnything
	 *            Whether any actual tokens have been consumed so far for this
	 *            send node.  That includes any leading argument.
	 * @param initialTokenPosition
	 *            The parse position where the send node started to be
	 *            processed. Does not count the position of the first argument
	 *            if there are no leading keywords.
	 * @param argsSoFar
	 *            The list of arguments parsed so far. I do not modify it. This
	 *            is a stack of expressions that the parsing instructions will
	 *            assemble into a list that correlates with the top-level
	 *            non-backquoted underscores and guillemet groups in the message
	 *            name.
	 * @param marksSoFar
	 *            The stack of mark positions used to test if parsing certain
	 *            subexpressions makes progress.
	 * @param continuation
	 *            What to do with a fully parsed send node.
	 */
	void parseRestOfSendNode (
		final ParserState start,
		final A_BundleTree bundleTree,
		final @Nullable A_Phrase firstArgOrNull,
		final ParserState initialTokenPosition,
		final boolean consumedAnything,
		final List<A_Phrase> argsSoFar,
		final List<Integer> marksSoFar,
		final Con<A_Phrase> continuation)
	{
		bundleTree.expand(module);
		final A_Map complete = bundleTree.lazyComplete();
		final A_Map incomplete = bundleTree.lazyIncomplete();
		final A_Map caseInsensitive =
			bundleTree.lazyIncompleteCaseInsensitive();
		final A_Map actions = bundleTree.lazyActions();
		final A_Map prefilter = bundleTree.lazyPrefilterMap();
		final boolean anyComplete = complete.mapSize() > 0;
		final boolean anyIncomplete = incomplete.mapSize() > 0;
		final boolean anyCaseInsensitive = caseInsensitive.mapSize() > 0;
		final boolean anyActions = actions.mapSize() > 0;
		final boolean anyPrefilter = prefilter.mapSize() > 0;

		if (!(anyComplete
			|| anyIncomplete
			|| anyCaseInsensitive
			|| anyActions
			|| anyPrefilter))
		{
			return;
		}
		if (anyComplete && consumedAnything && firstArgOrNull == null)
		{
			// There are complete messages, we didn't leave a leading argument
			// stranded, and we made progress in the file (i.e., the message
			// send does not consist of exactly zero tokens).  It *should* be
			// powerful enough to parse calls of "_" (i.e., the implicit
			// conversion operation), but only if there is a grammatical
			// restriction to prevent run-away left-recursion.  A type
			// restriction won't be checked soon enough to prevent the
			// recursion.
			for (final MapDescriptor.Entry entry : complete.mapIterable())
			{
				assert marksSoFar.isEmpty();
				completedSendNode(
					initialTokenPosition,
					start,
					argsSoFar,
					entry.value(),
					continuation);
			}
		}
		if (anyIncomplete
			&& firstArgOrNull == null
			&& !start.atEnd())
		{
			final A_Token keywordToken = start.peekToken();
			if (keywordToken.tokenType() == KEYWORD
				|| keywordToken.tokenType() == OPERATOR)
			{
				final A_String keywordString = keywordToken.string();
				if (incomplete.hasKey(keywordString))
				{
					eventuallyParseRestOfSendNode(
						"Continue send after a keyword",
						start.afterToken(),
						incomplete.mapAt(keywordString),
						null,
						initialTokenPosition,
						true,  // Just consumed a token
						argsSoFar,
						marksSoFar,
						continuation);
				}
				else
				{
					expectedKeywordsOf(start, incomplete, false);
				}
			}
			else
			{
				expectedKeywordsOf(start, incomplete, false);
			}
		}
		if (anyCaseInsensitive
			&& firstArgOrNull == null
			&& !start.atEnd())
		{
			final A_Token keywordToken = start.peekToken();
			if (keywordToken.tokenType() == KEYWORD
				|| keywordToken.tokenType() == OPERATOR)
			{
				final A_String keywordString = keywordToken.lowerCaseString();
				if (caseInsensitive.hasKey(keywordString))
				{
					eventuallyParseRestOfSendNode(
						"Continue send after a keyword",
						start.afterToken(),
						caseInsensitive.mapAt(keywordString),
						null,
						initialTokenPosition,
						true,  // Just consumed a token.
						argsSoFar,
						marksSoFar,
						continuation);
				}
				else
				{
					expectedKeywordsOf(start, caseInsensitive, true);
				}
			}
			else
			{
				expectedKeywordsOf(start, caseInsensitive, true);
			}
		}
		if (anyPrefilter)
		{
//			System.out.println("PREFILTER ENCOUNTERED: " + prefilter);
			final A_Phrase latestArgument = last(argsSoFar);
			if (latestArgument.isInstanceOfKind(SEND_NODE.mostGeneralType()))
			{
				final A_Bundle argumentBundle = latestArgument.bundle();
				if (prefilter.hasKey(argumentBundle))
				{
					eventuallyParseRestOfSendNode(
						"Continue send after productive grammatical "
							+ "restriction",
						start,
						prefilter.mapAt(argumentBundle),
						firstArgOrNull,
						initialTokenPosition,
						consumedAnything,
						argsSoFar,
						marksSoFar,
						continuation);
					// Don't allow normal action processing, as it would ignore
					// the restriction which we've been so careful to prefilter.
					assert actions.mapSize() == 1;
					assert ParsingOperation.decode(
							actions.mapIterable().next().key().extractInt())
						== ParsingOperation.CHECK_ARGUMENT;
					return;
				}
			}
		}
		if (anyActions)
		{
			for (final MapDescriptor.Entry entry : actions.mapIterable())
			{
				final A_Number key = entry.key();
				final A_Tuple value = entry.value();
				workUnitDo(
					new Continuation0()
					{
						@Override
						public void value ()
						{
							runParsingInstructionThen(
								start,
								key.extractInt(),
								firstArgOrNull,
								argsSoFar,
								marksSoFar,
								initialTokenPosition,
								consumedAnything,
								value,
								continuation);
						}
					},
					//TODO[MvG]: Reduce back to a string constant at some point.
					"Continue with instruction: "
						+ ParsingOperation.decode(entry.key().extractInt()),
					start);
			}
		}
	}

	/**
	 * Execute one non-keyword-parsing instruction, then run the continuation.
	 *
	 * @param start
	 *            Where to start parsing.
	 * @param instruction
	 *            The {@linkplain MessageSplitter instruction} to execute.
	 * @param firstArgOrNull
	 *            Either the already-parsed first argument or null. If we're
	 *            looking for leading-argument message sends to wrap an
	 *            expression then this is not-null before the first argument
	 *            position is encountered, otherwise it's null and we should
	 *            reject attempts to start with an argument (before a keyword).
	 * @param argsSoFar
	 *            The message arguments that have been parsed so far.
	 * @param marksSoFar
	 *            The parsing markers that have been recorded so far.
	 * @param initialTokenPosition
	 *            The position at which parsing of this message started. If it
	 *            was parsed as a leading argument send (i.e., firstArgOrNull
	 *            started out non-null) then the position is of the token
	 *            following the first argument.
	 * @param consumedAnything
	 *            Whether any tokens or arguments have been consumed yet.
	 * @param successorTrees
	 *            The {@linkplain TupleDescriptor tuple} of {@linkplain
	 *            MessageBundleTreeDescriptor bundle trees} at which to continue
	 *            parsing.
	 * @param continuation
	 *            What to do with a complete {@linkplain SendNodeDescriptor
	 *            message send}.
	 */
	void runParsingInstructionThen (
		final ParserState start,
		final int instruction,
		final @Nullable A_Phrase firstArgOrNull,
		final List<A_Phrase> argsSoFar,
		final List<Integer> marksSoFar,
		final ParserState initialTokenPosition,
		final boolean consumedAnything,
		final A_Tuple successorTrees,
		final Con<A_Phrase> continuation)
	{
		final ParsingOperation op = ParsingOperation.decode(instruction);
//		System.out.format(
//			"OP=%s%s [%s ☞ %s]%n",
//			op.name(),
//			instruction < ParsingOperation.distinctInstructions
//				? ""
//				: " (operand=" + op.operand(instruction) + ")",
//			tokens.get(start.position - 1).string(),
//			tokens.get(start.position).string());
		switch (op)
		{
			case PARSE_ARGUMENT:
			{
				// Parse an argument and continue.
				assert successorTrees.tupleSize() == 1;
				parseSendArgumentWithExplanationThen(
					start,
					" (an argument of some message)",
					firstArgOrNull,
					firstArgOrNull == null
						&& initialTokenPosition.position != start.position,
					new Con<A_Phrase>("Argument of message send")
					{
						@Override
						public void value (
							final @Nullable ParserState afterArg,
							final @Nullable A_Phrase newArg)
						{
							assert afterArg != null;
							assert newArg != null;
							final List<A_Phrase> newArgsSoFar =
								append(argsSoFar, newArg);
							eventuallyParseRestOfSendNode(
								"Continue send after argument",
								afterArg,
								successorTrees.tupleAt(1),
								null,
								initialTokenPosition,
								// The argument counts as something that was
								// consumed if it's not a leading argument...
								firstArgOrNull == null,
								newArgsSoFar,
								marksSoFar,
								continuation);
						}
					});
				break;
			}
			case NEW_LIST:
			{
				// Push an empty list node and continue.
				assert successorTrees.tupleSize() == 1;
				final List<A_Phrase> newArgsSoFar =
					append(argsSoFar, ListNodeDescriptor.empty());
				eventuallyParseRestOfSendNode(
					"Continue send after push empty",
					start,
					successorTrees.tupleAt(1),
					firstArgOrNull,
					initialTokenPosition,
					consumedAnything,
					newArgsSoFar,
					marksSoFar,
					continuation);
				break;
			}
			case APPEND_ARGUMENT:
			{
				// Append the item that's the last thing to the list that's the
				// second last thing. Pop both and push the new list (the
				// original list must not change), then continue.
				assert successorTrees.tupleSize() == 1;
				final A_Phrase value = last(argsSoFar);
				final List<A_Phrase> poppedOnce = withoutLast(argsSoFar);
				final A_Phrase oldNode = last(poppedOnce);
				final A_Phrase listNode = oldNode.copyWith(value);
				final List<A_Phrase> newArgsSoFar =
					append(withoutLast(poppedOnce), listNode);
				eventuallyParseRestOfSendNode(
					"Continue send after append",
					start,
					successorTrees.tupleAt(1),
					firstArgOrNull,
					initialTokenPosition,
					consumedAnything,
					newArgsSoFar,
					marksSoFar,
					continuation);
				break;
			}
			case SAVE_PARSE_POSITION:
			{
				// Push current parse position on the mark stack.
				assert successorTrees.tupleSize() == 1;
				final int marker =
					firstArgOrNull == null
						? start.position
						: initialTokenPosition.position;
				final List<Integer> newMarksSoFar =
					PrefixSharingList.append(marksSoFar, marker);
				eventuallyParseRestOfSendNode(
					"Continue send after push parse position",
					start,
					successorTrees.tupleAt(1),
					firstArgOrNull,
					initialTokenPosition,
					consumedAnything,
					argsSoFar,
					newMarksSoFar,
					continuation);
				break;
			}
			case DISCARD_SAVED_PARSE_POSITION:
			{
				// Pop from the mark stack.
				assert successorTrees.tupleSize() == 1;
				eventuallyParseRestOfSendNode(
					"Continue send after pop mark stack",
					start,
					successorTrees.tupleAt(1),
					firstArgOrNull,
					initialTokenPosition,
					consumedAnything,
					argsSoFar,
					withoutLast(marksSoFar),
					continuation);
				break;
			}
			case ENSURE_PARSE_PROGRESS:
			{
				// Check for parser progress.  Abort this avenue of parsing if
				// the parse position is still equal to the position on the
				// mark stack.  Pop the old mark and push the new mark.
				assert successorTrees.tupleSize() == 1;
				final int oldMarker = last(marksSoFar);
				if (oldMarker == start.position)
				{
					// No progress has been made.  Reject this path.
					return;
				}
				final int newMarker = start.position;
				final List<Integer> newMarksSoFar =
					append(withoutLast(marksSoFar), newMarker);
				eventuallyParseRestOfSendNode(
					"Continue send after check parse progress",
					start,
					successorTrees.tupleAt(1),
					firstArgOrNull,
					initialTokenPosition,
					consumedAnything,
					argsSoFar,
					newMarksSoFar,
					continuation);
				break;
			}
			case PARSE_RAW_TOKEN:
				// Parse a raw token and continue.
				assert successorTrees.tupleSize() == 1;
				if (firstArgOrNull != null)
				{
					// Starting with a parseRawToken can't cause unbounded
					// left-recursion, so treat it more like reading an expected
					// token than like parseArgument.  Thus, if a firstArgument
					// has been provided (i.e., we're attempting to parse a
					// leading-argument message to wrap a leading expression),
					// then reject the parse.
					break;
				}
				final A_Token newToken = parseRawTokenOrNull(start);
				if (newToken != null)
				{
					final ParserState afterToken = start.afterToken();
					final A_Token syntheticToken =
						LiteralTokenDescriptor.create(
							newToken.string(),
							newToken.start(),
							newToken.lineNumber(),
							SYNTHETIC_LITERAL,
							newToken);
					final A_Phrase literalNode =
						LiteralNodeDescriptor.fromToken(syntheticToken);
					final List<A_Phrase> newArgsSoFar =
						append(argsSoFar, literalNode);
					eventuallyParseRestOfSendNode(
						"Continue send after raw token for ellipsis",
						afterToken,
						successorTrees.tupleAt(1),
						null,
						initialTokenPosition,
						true,
						newArgsSoFar,
						marksSoFar,
						continuation);
				}
				break;
			case PARSE_ARGUMENT_IN_MODULE_SCOPE:
			{
				parseArgumentInModuleScopeThen(
					start,
					firstArgOrNull,
					argsSoFar,
					marksSoFar,
					initialTokenPosition,
					successorTrees,
					continuation);
				break;
			}
			case PUSH_TRUE:
			case PUSH_FALSE:
			{
				final A_Atom booleanValue =
					AtomDescriptor.objectFromBoolean(op == PUSH_TRUE);
				final A_Token token = LiteralTokenDescriptor.create(
					StringDescriptor.from(booleanValue.toString()),
					initialTokenPosition.peekToken().start(),
					initialTokenPosition.peekToken().lineNumber(),
					LITERAL,
					booleanValue);
				final A_Phrase literalNode =
					LiteralNodeDescriptor.fromToken(token);
				eventuallyParseRestOfSendNode(
					"Continue send after push boolean literal",
					start,
					successorTrees.tupleAt(1),
					firstArgOrNull,
					initialTokenPosition,
					consumedAnything,
					append(argsSoFar, literalNode),
					marksSoFar,
					continuation);
				break;
			}
			case RESERVED_10:
			case RESERVED_11:
			case RESERVED_12:
			case RESERVED_13:
			case RESERVED_14:
			case RESERVED_15:
			{
				AvailObject.error("Invalid parse instruction: " + op);
				break;
			}
			case BRANCH:
				// $FALL-THROUGH$
				// Fall through.  The successorTrees will be different
				// for the jump versus parallel-branch.
			case JUMP:
				for (int i = successorTrees.tupleSize(); i >= 1; i--)
				{
					final A_BundleTree successorTree =
						successorTrees.tupleAt(i);
					eventuallyParseRestOfSendNode(
						"Continue send after branch or jump (" +
							(i == 1 ? "not taken)" : "taken)"),
						start,
						successorTree,
						firstArgOrNull,
						initialTokenPosition,
						consumedAnything,
						argsSoFar,
						marksSoFar,
						continuation);
				}
				break;
			case PARSE_PART:
				// $FALL-THROUGH$
			case PARSE_PART_CASE_INSENSITIVELY:
				assert false
				: op.name() + " instruction should not be dispatched";
				break;
			case CHECK_ARGUMENT:
			{
				// CheckArgument.  An actual argument has just been parsed (and
				// pushed).  Make sure it satisfies any grammatical
				// restrictions.  The message bundle tree's lazy prefilter map
				// deals with that efficiently.
				assert successorTrees.tupleSize() == 1;
				assert firstArgOrNull == null;
				eventuallyParseRestOfSendNode(
					"Continue send after checkArgument",
					start,
					successorTrees.tupleAt(1),
					firstArgOrNull, // Null
					initialTokenPosition,
					consumedAnything,
					argsSoFar,
					marksSoFar,
					continuation);
				break;
			}
			case CONVERT:
			{
				// Convert the argument.
				assert successorTrees.tupleSize() == 1;
				final A_Phrase input = last(argsSoFar);
				op.conversionRule(instruction).convert(
					input,
					initialTokenPosition,
					new Continuation1<A_Phrase>()
					{
						@Override
						public void value (
							final @Nullable A_Phrase replacementExpression)
						{
							assert replacementExpression != null;
							final List<A_Phrase> newArgsSoFar =
								append(
									withoutLast(argsSoFar),
									replacementExpression);
							eventuallyParseRestOfSendNode(
								"Continue send after conversion",
								start,
								successorTrees.tupleAt(1),
								firstArgOrNull,
								initialTokenPosition,
								consumedAnything,
								newArgsSoFar,
								marksSoFar,
								continuation);
						}
					},
					new Continuation1<Throwable>()
					{
						@Override
						public void value (final @Nullable Throwable arg)
						{
							//TODO[MvG] - Deal with failed conversion (this can
							// only happen during an eval conversion).
							assert false : "Deal with this";
						}
					});
				break;
			}
			case PUSH_INTEGER_LITERAL:
			{
				final A_Number integerValue = IntegerDescriptor.fromInt(
					op.integerToPush(instruction));
				final A_Token token = LiteralTokenDescriptor.create(
					StringDescriptor.from(integerValue.toString()),
					initialTokenPosition.peekToken().start(),
					initialTokenPosition.peekToken().lineNumber(),
					LITERAL,
					integerValue);
				final A_Phrase literalNode =
					LiteralNodeDescriptor.fromToken(token);
				final List<A_Phrase> newArgsSoFar =
					append(argsSoFar, literalNode);
				eventuallyParseRestOfSendNode(
					"Continue send after push integer literal",
					start,
					successorTrees.tupleAt(1),
					firstArgOrNull,
					initialTokenPosition,
					consumedAnything,
					newArgsSoFar,
					marksSoFar,
					continuation);
				break;
			}
			case PREPARE_TO_RUN_PREFIX_FUNCTION:
			{
				List<A_Phrase> stackCopy = argsSoFar;
				// subtract one because zero is an invalid operand.
				for (int i = op.fixupDepth(instruction) - 1; i > 0; i--)
				{
					// Pop the last element and append it to the second last.
					final A_Phrase value = last(stackCopy);
					final List<A_Phrase> poppedOnce = withoutLast(stackCopy);
					final A_Phrase oldNode = last(poppedOnce);
					final A_Phrase listNode = oldNode.copyWith(value);
					stackCopy = append(withoutLast(poppedOnce), listNode);
				}
				// Convert the List to an Avail list node.
				final A_Phrase newListNode =
					ListNodeDescriptor.newExpressions(
						TupleDescriptor.fromList(stackCopy));
				assert successorTrees.tupleSize() == 1;
				eventuallyParseRestOfSendNode(
					"Continue send after preparing to run prefix function (§)",
					start,
					successorTrees.tupleAt(1),
					firstArgOrNull,
					initialTokenPosition,
					consumedAnything,
					append(argsSoFar, newListNode),
					marksSoFar,
					continuation);
				break;
			}
			case RUN_PREFIX_FUNCTION:
			{
				// Extract the list node pushed by the
				// PREPARE_TO_RUN_PREFIX_FUNCTION instruction that should have
				// just run.  Run the indicated prefix function, which will
				// communicate parser state changes via fiber globals.
				// TODO[MvG] We still have to deal with splitting the bundles
				// into synthetic singletons so that only the prefix functions
				// in the same tuple of related functions will have run along
				// any of the paths.  That's essential both for polymorphic
				// macros and just for macros whose names share a common prefix.
				assert successorTrees.tupleSize() == 1;
				final A_BundleTree successorTree = successorTrees.tupleAt(1);
				final A_Map bundlesMap = successorTree.allBundles();
				assert bundlesMap.mapSize() == 1;
				final A_Bundle bundle =
					bundlesMap.valuesAsTuple().tupleAt(1);
				final A_Tuple definitions =
					bundle.bundleMethod().definitionsTuple();
				assert definitions.tupleSize() == 1;
				final A_Definition definition = definitions.tupleAt(1);
				assert definition.isMacroDefinition();
				final int prefixFunctionSubscript =
					op.prefixFunctionSubscript(instruction);
				final A_Tuple prefixFunctions = definition.prefixFunctions();
				final A_Function prefixFunction =
					prefixFunctions.tupleAt(prefixFunctionSubscript);

				final A_Phrase listNodeOfArgsSoFar = last(argsSoFar);
				final List<AvailObject> listOfArgs = TupleDescriptor.toList(
					listNodeOfArgsSoFar.expressionsTuple());
				final A_Fiber fiber = FiberDescriptor.newLoaderFiber(
					prefixFunction.kind().returnType(),
					loader());
				A_Map fiberGlobals = fiber.fiberGlobals();
				fiberGlobals = fiberGlobals.mapAtPuttingCanDestroy(
					clientDataGlobalKey,
					start.clientDataMap.makeImmutable(),
					true);
				fiber.fiberGlobals(fiberGlobals);
				fiber.resultContinuation(workUnitCompletion(
					new Continuation1<AvailObject>()
					{
						@Override
						public void value (
							final @Nullable AvailObject ignoredResult)
						{
							// The prefix function ran successfully.
							final A_Map replacementClientDataMap =
								fiber.fiberGlobals().mapAt(clientDataGlobalKey);
							final ParserState newState = new ParserState(
								start.position,
								replacementClientDataMap);
							eventuallyParseRestOfSendNode(
								"Continue after successful prefix function (§)",
								newState,
								successorTrees.tupleAt(1),
								firstArgOrNull,
								initialTokenPosition,
								consumedAnything,
								argsSoFar,
								marksSoFar,
								continuation);
						}
					}));
				fiber.failureContinuation(new Continuation1<Throwable>()
				{
					@Override
					public void value (final @Nullable Throwable throwable)
					{
						assert throwable != null;
						// The prefix function failed in some way.
						start.expected(new Describer()
						{
							@Override
							public void describeThen (
								final Continuation1<String> c)
							{
								c.value(
									"prefix function not to have failed with:\n"
									+ throwable.getLocalizedMessage());
							}
						});
					}
				});
				startWorkUnit();
				Interpreter.runOutermostFunction(
					runtime,
					fiber,
					prefixFunction,
					listOfArgs);
				break;
			}
		}
	}

	/**
	 * Check the proposed message send for validity. Use not only the applicable
	 * {@linkplain MethodDefinitionDescriptor method definitions}, but also any
	 * type restriction functions. The type restriction functions may choose to
	 * {@linkplain P_352_RejectParsing reject the parse}, indicating that the
	 * argument types are mutually incompatible.
	 *
	 * @param bundle
	 *        A {@linkplain MessageBundleDescriptor message bundle}.
	 * @param argTypes
	 *        The argument types.
	 * @param state
	 *        The {@linkplain ParserState parser state} after the function
	 *        evaluates successfully.
	 * @param onSuccess
	 *        What to do with the strengthened return type.
	 * @param onFailure
	 *        What to do if validation fails.
	 */
	private void validateArgumentTypes (
		final A_Bundle bundle,
		final List<A_Type> argTypes,
		final ParserState state,
		final Continuation1<A_Type> onSuccess,
		final Continuation1<Describer> onFailure)
	{
		final MutableOrNull<A_Tuple> definitionsTuple =
			new MutableOrNull<A_Tuple>();
		final MutableOrNull<A_Set> restrictions = new MutableOrNull<A_Set>();
		final A_Method method = bundle.bundleMethod();
		method.lock(new Continuation0()
		{
			@Override
			public void value ()
			{
				definitionsTuple.value = method.definitionsTuple();
				restrictions.value = method.semanticRestrictions();
			}
		});
		// Filter the definitions down to those that are locally most specific.
		// Fail if more than one survives.
		if (definitionsTuple.value().tupleSize() > 0 &&
			!definitionsTuple.value().tupleAt(1).isMacroDefinition())
		{
			// This consists of method definitions.
			for (
				int index = 1, end = argTypes.size();
				index <= end;
				index++)
			{
				final int finalIndex = index;
				final A_Type finalType =
					argTypes.get(finalIndex - 1).makeShared();
				if (finalType.equals(BottomTypeDescriptor.bottom())
					|| finalType.equals(TOP.o()))
				{
					onFailure.value(new Describer()
					{
						@Override
						public void describeThen (final Continuation1<String> c)
						{
							Interpreter.stringifyThen(
								runtime,
								argTypes.get(finalIndex - 1),
								new Continuation1<String>()
								{
									@Override
									public void value (final @Nullable String s)
									{
										assert s != null;
										c.value(String.format(
											"argument #%d of message %s "
											+ " to have a type other than %s",
											finalIndex,
											bundle.message().atomName(),
											s));
									}
								});
						}
					});
					return;
				}
			}
		}
		// Find all method definitions that could match the argument types.
		// Only consider definitions that are defined in the current module or
		// an ancestor.
		final A_Set allAncestors = module.allAncestors();
		final List<A_Definition> filteredByTypes =
			method.filterByTypes(argTypes);
		final List<A_Definition> satisfyingDefinitions = new ArrayList<>();
		for (final A_Definition definition : filteredByTypes)
		{
			if (allAncestors.hasElement(definition.definitionModule()))
			{
				satisfyingDefinitions.add(definition);
			}
		}
		if (satisfyingDefinitions.isEmpty())
		{
			onFailure.value(new Describer()
			{
				@Override
				public void describeThen (final Continuation1<String> c)
				{
					final List<A_Definition> allVisible = new ArrayList<>();
					for (final A_Definition def : definitionsTuple.value())
					{
						if (allAncestors.hasElement(def.definitionModule()))
						{
							allVisible.add(def);
						}
					}
					final List<Integer> allFailedIndices = new ArrayList<>(3);
					each_arg:
					for (int i = 1, end = argTypes.size(); i <= end; i++)
					{
						for (final A_Definition definition : allVisible)
						{
							final A_Type sig = definition.bodySignature();
							if (argTypes.get(i - 1).isSubtypeOf(
								sig.argsTupleType().typeAtIndex(i)))
							{
								continue each_arg;
							}
						}
						allFailedIndices.add(i);
					}
					if (allFailedIndices.size() == 0)
					{
						// Each argument applied to at least one definition,
						// so put the blame on them all instead of none.
						for (int i = 1, end = argTypes.size(); i <= end; i++)
						{
							allFailedIndices.add(i);
						}
					}
					// Don't stringify all the argument types, just the failed
					// ones. And don't stringify the same value twice. Obviously
					// side effects in stringifiers won't work right here…
					final List<A_BasicObject> uniqueValues =
						new ArrayList<A_BasicObject>();
					final Map<A_BasicObject, Integer> valuesToStringify =
						new HashMap<A_BasicObject, Integer>();
					for (final int i : allFailedIndices)
					{
						final A_Type argType = argTypes.get(i - 1);
						if (!valuesToStringify.containsKey(argType))
						{
							valuesToStringify.put(argType, uniqueValues.size());
							uniqueValues.add(argType);
						}
						for (final A_Definition definition : allVisible)
						{
							final A_Type signatureArgumentsType =
								definition.bodySignature().argsTupleType();
							final A_Type sigType =
								signatureArgumentsType.typeAtIndex(i);
							if (!valuesToStringify.containsKey(sigType))
							{
								valuesToStringify.put(
									sigType, uniqueValues.size());
								uniqueValues.add(sigType);
							}
						}
					}
					Interpreter.stringifyThen(
						runtime,
						uniqueValues,
						new Continuation1<List<String>>()
						{
							@Override
							public void value (
								final @Nullable List<String> strings)
							{
								assert strings != null;
								@SuppressWarnings("resource")
								final Formatter builder = new Formatter();
								builder.format(
									"arguments at indices %s of message %s to "
									+ "match a visible method definition:%n",
									allFailedIndices,
									bundle.message().atomName());
								builder.format("\tI got:%n");
								for (final int i : allFailedIndices)
								{
									final A_Type argType = argTypes.get(i - 1);
									final String s = strings.get(
										valuesToStringify.get(argType));
									builder.format("\t\t#%d = %s%n", i, s);
								}
								builder.format(
									"\tI expected%s:",
									allVisible.size() > 1 ? " one of" : "");
								for (final A_Definition definition : allVisible)
								{
									builder.format(
										"%n\t\tFrom module %s @ line #%s,",
										definition
											.definitionModule()
											.moduleName(),
										definition.isMethodDefinition()
											? definition
												.bodyBlock()
												.code()
												.startingLineNumber()
											: "unknown");
									final A_Type signatureArgumentsType =
										definition.bodySignature()
											.argsTupleType();
									for (final int i : allFailedIndices)
									{
										final A_Type sigType =
											signatureArgumentsType
												.typeAtIndex(i);
										final String s = strings.get(
											valuesToStringify.get(sigType));
										builder.format(
											"%n\t\t\t#%d = %s", i, s);
									}
								}
								c.value(builder.toString());
							}
						});
				}
			});
			return;
		}
		// Compute the intersection of the return types of the possible callees.
		final Mutable<A_Type> intersection = new Mutable<A_Type>(
			satisfyingDefinitions.get(0).bodySignature().returnType());
		for (int i = 1, end = satisfyingDefinitions.size(); i < end; i++)
		{
			intersection.value = intersection.value.typeIntersection(
				satisfyingDefinitions.get(i).bodySignature().returnType());
		}
		// Determine which semantic restrictions are relevant.
		final List<A_SemanticRestriction> restrictionsToTry =
			new ArrayList<>(restrictions.value().setSize());
		for (final A_SemanticRestriction restriction : restrictions.value())
		{
			if (allAncestors.hasElement(restriction.definitionModule()))
			{
				if (restriction.function().kind().acceptsListOfArgValues(
					argTypes))
				{
					restrictionsToTry.add(restriction);
				}
			}
		}
		// If there are no relevant semantic restrictions, then just invoke the
		// success continuation with the intersection and exit early.
		if (restrictionsToTry.isEmpty())
		{
			onSuccess.value(intersection.value);
			return;
		}
		// Run all relevant semantic restrictions, in parallel, computing the
		// type intersection of their results.
		final Mutable<Integer> outstanding = new Mutable<Integer>(
			restrictionsToTry.size());
		final Mutable<Boolean> anyFailures = new Mutable<Boolean>(false);
		final Continuation1<AvailObject> intersectAndDecrement =
			workUnitCompletion(
				new Continuation1<AvailObject>()
				{
					@Override
					public void value (
						final @Nullable AvailObject restrictionType)
					{
						assert restrictionType != null;
						synchronized (outstanding)
						{
							if (!anyFailures.value)
							{
								intersection.value =
									intersection.value.typeIntersection(
										restrictionType);
								outstanding.value--;
								if (outstanding.value == 0)
								{
									onSuccess.value(intersection.value);
								}
							}
						}
					}
				});
		final Continuation1<Throwable> failed =
			workUnitCompletion(
				new Continuation1<Throwable>()
				{
					@Override
					public void value (final @Nullable Throwable e)
					{
						assert e != null;
						final boolean alreadyFailed;
						synchronized (outstanding)
						{
							alreadyFailed = anyFailures.value;
							if (!alreadyFailed)
							{
								anyFailures.value = true;
							}
						}
						if (!alreadyFailed)
						{
							if (e instanceof AvailRejectedParseException)
							{
								final AvailRejectedParseException rej =
									(AvailRejectedParseException) e;
								final A_String problem = rej.rejectionString();
								onFailure.value(
									new Describer()
									{
										@Override
										public void describeThen (
											final Continuation1<String> c)
										{
											c.value(
												problem.asNativeString()
												+ " (while parsing send of "
												+ bundle.message().atomName()
												+ ")");
										}
									});
							}
							else if (e instanceof FiberTerminationException)
							{
								onFailure.value(
									new Describer()
									{
										@Override
										public void describeThen (
											final Continuation1<String> c)
										{
											c.value(
												"semantic restriction not to "
												+ "raise an unhandled "
												+ "exception (while parsing "
												+ "send of "
												+ bundle.message().atomName()
												+ "):\n\t"
												+ e.toString());
										}
									});
							}
							else if (e instanceof AvailAssertionFailedException)
							{
								final AvailAssertionFailedException ex =
									(AvailAssertionFailedException) e;
								onFailure.value(
									new Describer()
									{
										@Override
										public void describeThen (
											final Continuation1<String> c)
										{
											c.value(
												"assertion failed "
												+ " (while parsing send of "
												+ bundle.message().atomName()
												+ "):\n\t"
												+ ex.assertionString()
													.asNativeString());
										}
									});
							}
							else
							{
								compilationFailed(e);
							}
						}
					}
				});
		final List<AvailObject> strongArgs =
			new ArrayList<AvailObject>(argTypes.size());
		for (final A_Type argType : argTypes)
		{
			strongArgs.add((AvailObject)argType);
		}
		startWorkUnits(restrictionsToTry.size());
		for (final A_SemanticRestriction restriction : restrictionsToTry)
		{
			evaluateSemanticRestrictionFunctionThen(
				restriction,
				strongArgs,
				intersectAndDecrement,
				failed);
		}
	}

	/**
	 * A complete {@linkplain SendNodeDescriptor send node} has been parsed.
	 * Create the send node and invoke the continuation.
	 *
	 * <p>
	 * If this is a macro, invoke the body immediately with the argument
	 * expressions to produce a parse node.
	 * </p>
	 *
	 * @param stateBeforeCall
	 *            The initial parsing state, prior to parsing the entire
	 *            message.
	 * @param stateAfterCall
	 *            The parsing state after the message.
	 * @param argumentExpressions
	 *            The {@linkplain ParseNodeDescriptor parse nodes} that will
	 *            be arguments of the new send node.
	 * @param bundle
	 *            The {@linkplain MessageBundleDescriptor message bundle}
	 *            that identifies the message to be sent.
	 * @param continuation
	 *            What to do with the resulting send node.
	 */
	void completedSendNode (
		final ParserState stateBeforeCall,
		final ParserState stateAfterCall,
		final List<A_Phrase> argumentExpressions,
		final A_Bundle bundle,
		final Con<A_Phrase> continuation)
	{
		final Mutable<Boolean> valid = new Mutable<Boolean>(true);
		final A_Method method = bundle.bundleMethod();
		final A_Tuple definitionsTuple = method.definitionsTuple();
		assert definitionsTuple.tupleSize() > 0;

		if (definitionsTuple.tupleAt(1).isMacroDefinition())
		{
			// Macro definitions and non-macro definitions are not allowed
			// to mix within a method.
			completedSendNodeForMacro(
				stateBeforeCall,
				stateAfterCall,
				argumentExpressions,
				bundle,
				continuation);
			return;
		}
		// It invokes a method (not a macro).
		final List<A_Type> argTypes =
			new ArrayList<A_Type>(argumentExpressions.size());
		for (final A_Phrase argumentExpression : argumentExpressions)
		{
			argTypes.add(argumentExpression.expressionType());
		}
		// Parsing a method send can't affect the scope.
		assert stateAfterCall.clientDataMap.equals(
			stateBeforeCall.clientDataMap);
		final ParserState afterState = new ParserState(
			stateAfterCall.position,
			stateBeforeCall.clientDataMap);
		// Validate the message send before reifying a send phrase.
		validateArgumentTypes(
			bundle,
			argTypes,
			stateAfterCall,
			new Continuation1<A_Type>()
			{
				@Override
				public void value (final @Nullable A_Type returnType)
				{
					assert returnType != null;
					final A_Phrase sendNode = SendNodeDescriptor.from(
						bundle,
						ListNodeDescriptor.newExpressions(
							TupleDescriptor.fromList(argumentExpressions)),
						returnType);
					attempt(afterState, continuation, sendNode);
				}
			},
			new Continuation1<Describer>()
			{
				@Override
				public void value (
					final @Nullable Describer errorGenerator)
				{
					assert errorGenerator != null;
					valid.value = false;
					stateAfterCall.expected(errorGenerator);
				}
			});
	}

	/**
	 * Parse an argument to a message send. Backtracking will find all valid
	 * interpretations.
	 *
	 * @param start
	 *            Where to start parsing.
	 * @param explanation
	 *            A {@link String} indicating why it's parsing an argument.
	 * @param firstArgOrNull
	 *            Either a parse node to use as the argument, or null if we
	 *            should parse one now.
	 * @param canReallyParse
	 *            Whether any tokens may be consumed.  This should be false
	 *            specifically when the leftmost argument of a leading-argument
	 *            message is being parsed.
	 * @param continuation
	 *            What to do with the argument.
	 */
	void parseSendArgumentWithExplanationThen (
		final ParserState start,
		final String explanation,
		final @Nullable A_Phrase firstArgOrNull,
		final boolean canReallyParse,
		final Con<A_Phrase> continuation)
	{
		if (firstArgOrNull == null)
		{
			// There was no leading argument, or it has already been accounted
			// for.  If we haven't actually consumed anything yet then don't
			// allow a *leading* argument to be parsed here.  That would lead
			// to ambiguous left-recursive parsing.
			if (canReallyParse)
			{
				parseExpressionThen(
					start,
					new Con<A_Phrase>("Argument expression")
					{
						@Override
						public void value (
							final @Nullable ParserState afterArgument,
							final @Nullable A_Phrase argument)
						{
							assert afterArgument != null;
							assert argument != null;
							attempt(afterArgument, continuation, argument);
						}
					});
			}
		}
		else
		{
			// We're parsing a message send with a leading argument, and that
			// argument was explicitly provided to the parser.  We should
			// consume the provided first argument now.
			assert !canReallyParse;
			attempt(start, continuation, firstArgOrNull);
		}
	}

	/**
	 * Parse an argument in the top-most scope.  This is an important capability
	 * for parsing type expressions, and the macro facility may make good use
	 * of it for other purposes.
	 *
	 * @param start
	 *            The position at which parsing should occur.
	 * @param firstArgOrNull
	 *            An optional already parsed expression which, if present, must
	 *            be used as a leading argument.  If it's {@code null} then no
	 *            leading argument has been parsed, and a request to parse a
	 *            leading argument should simply produce no local solution.
	 * @param initialTokenPosition
	 *            The parse position where the send node started to be
	 *            processed. Does not count the position of the first argument
	 *            if there are no leading keywords.
	 * @param argsSoFar
	 *            The list of arguments parsed so far. I do not modify it. This
	 *            is a stack of expressions that the parsing instructions will
	 *            assemble into a list that correlates with the top-level
	 *            non-backquoted underscores and guillemet groups in the message
	 *            name.
	 * @param marksSoFar
	 *            The stack of mark positions used to test if parsing certain
	 *            subexpressions makes progress.
	 * @param successorTrees
	 *            A {@linkplain TupleDescriptor tuple} of {@linkplain
	 *            MessageBundleTreeDescriptor message bundle trees} along which
	 *            to continue parsing if a local solution is found.
	 * @param continuation
	 *            What to do once we have a fully parsed send node (of which
	 *            we are currently parsing an argument).
	 */
	private void parseArgumentInModuleScopeThen (
		final ParserState start,
		final @Nullable A_Phrase firstArgOrNull,
		final List<A_Phrase> argsSoFar,
		final List<Integer> marksSoFar,
		final ParserState initialTokenPosition,
		final A_Tuple successorTrees,
		final Con<A_Phrase> continuation)
	{
		// Parse an argument in the outermost (module) scope and continue.
		assert successorTrees.tupleSize() == 1;
		final A_Map clientDataInGlobalScope =
			start.clientDataMap.mapAtPuttingCanDestroy(
				compilerScopeMapKey,
				MapDescriptor.empty(),
				false);
		final ParserState startInGlobalScope = new ParserState(
			start.position,
			clientDataInGlobalScope);
		parseSendArgumentWithExplanationThen(
			startInGlobalScope,
			" (a global-scoped argument of some message)",
			firstArgOrNull,
			firstArgOrNull == null
				&& initialTokenPosition.position != start.position,
			new Con<A_Phrase>("Global-scoped argument of message")
			{
				@Override
				public void value (
					final @Nullable ParserState afterArg,
					final @Nullable A_Phrase newArg)
				{
					assert afterArg != null;
					assert newArg != null;
					if (firstArgOrNull != null)
					{
						// A leading argument was already supplied.  We
						// couldn't prevent it from referring to
						// variables that were in scope during its
						// parsing, but we can reject it if the leading
						// argument is supposed to be parsed in global
						// scope, which is the case here, and there are
						// references to local variables within the
						// argument's parse tree.
						final A_Set usedLocals =
							usesWhichLocalVariables(newArg);
						if (usedLocals.setSize() > 0)
						{
							// A leading argument was supplied which
							// used at least one local.  It shouldn't
							// have.
							afterArg.expected(new Describer()
							{
								@Override
								public void describeThen (
									final @Nullable Continuation1<String> c)
								{
									assert c != null;
									final List<String> localNames =
										new ArrayList<String>();
									for (final A_Phrase usedLocal : usedLocals)
									{
										final A_String name =
											usedLocal.token().string();
										localNames.add(
											name.asNativeString());
									}
									c.value(
										"A leading argument which "
										+ "was supposed to be parsed in"
										+ "module scope actually "
										+ "referred to some local "
										+ "variables: "
										+ localNames.toString());
								}
							});
							return;
						}
					}
					final List<A_Phrase> newArgsSoFar =
						append(argsSoFar, newArg);
					final ParserState afterArgButInScope =
						new ParserState(
							afterArg.position,
							start.clientDataMap);
					eventuallyParseRestOfSendNode(
						"Continue send after argument in module scope",
						afterArgButInScope,
						successorTrees.tupleAt(1),
						null,
						initialTokenPosition,
						// The argument counts as something that was
						// consumed if it's not a leading argument...
						firstArgOrNull == null,
						newArgsSoFar,
						marksSoFar,
						continuation);
				}
			});
	}

	/**
	 * A macro invocation has just been parsed.  Run it now if macro execution
	 * is supported.
	 *
	 * @param stateBeforeCall
	 *            The initial parsing state, prior to parsing the entire
	 *            message.
	 * @param stateAfterCall
	 *            The parsing state after the message.
	 * @param argumentExpressions
	 *            The {@linkplain ParseNodeDescriptor parse nodes} that will be
	 *            arguments of the new send node.
	 * @param bundle
	 *            The {@linkplain MessageBundleDescriptor message bundle} that
	 *            identifies the message to be sent.
	 * @param continuation
	 *            What to do with the resulting send node.
	 */
	abstract void completedSendNodeForMacro (
		final ParserState stateBeforeCall,
		final ParserState stateAfterCall,
		final List<A_Phrase> argumentExpressions,
		final A_Bundle bundle,
		final Con<A_Phrase> continuation);

	/**
	 * Create a bootstrap primitive method. Use the primitive's type declaration
	 * as the argument types.  If the primitive is fallible then generate
	 * suitable primitive failure code (to invoke the {@link MethodDescriptor
	 * #vmCrashAtom()}'s bundle).
	 *
	 * @param state
	 *        The {@linkplain ParserState state} following a parse of the
	 *        {@linkplain ModuleHeader module header}.
	 * @param methodName
	 *        The name of the primitive method being defined.
	 * @param primitiveNumber
	 *        The {@linkplain Primitive#primitiveNumber primitive number} of the
	 *        {@linkplain MethodDescriptor method} being defined.
	 * @param continuation
	 *        What to do after the operation completes.
	 */
	void bootstrapMethodThen (
		final ParserState state,
		final String methodName,
		final int primitiveNumber,
		final Continuation0 continuation)
	{
		final A_String availName = StringDescriptor.from(methodName);
		final A_Phrase nameLiteral =
			LiteralNodeDescriptor.syntheticFrom(availName);
		final A_Function function =
			MethodDescriptor.newPrimitiveFunction(
				Primitive.byPrimitiveNumberOrFail(primitiveNumber));
		final A_Phrase send = SendNodeDescriptor.from(
			MethodDescriptor.vmMethodDefinerAtom().bundleOrNil(),
			ListNodeDescriptor.newExpressions(TupleDescriptor.from(
				nameLiteral,
				LiteralNodeDescriptor.syntheticFrom(function))),
			TOP.o());
		evaluateModuleStatementThen(
			state,
			send,
			continuation,
			new Continuation1<Throwable>()
			{
				@Override
				public void value (final @Nullable Throwable killer)
				{
					assert killer != null;
					compilationFailed(killer);
				}
			});
	}

	/**
	 * Create a bootstrap primitive {@linkplain MacroDefinitionDescriptor
	 * macro}. Use the primitive's type declaration as the argument types.  If
	 * the primitive is fallible then generate suitable primitive failure code
	 * (to invoke the {@link MethodDescriptor#vmCrashAtom()}'s bundle).
	 *
	 * @param state
	 *        The {@linkplain ParserState state} following a parse of the
	 *        {@linkplain ModuleHeader module header}.
	 * @param macroName
	 *        The name of the primitive macro being defined.
	 * @param primitiveNumbers
	 *        The array of {@linkplain Primitive#primitiveNumber primitive
	 *        numbers} of the bodies of the macro being defined.  These
	 *        correspond to the occurrences of the {@linkplain StringDescriptor
	 *        #sectionSign() section sign} (§) in the macro name, plus a final
	 *        body for the complete macro.
	 * @param continuation
	 *        What to do after the operation completes.
	 */
	void bootstrapMacroThen (
		final ParserState state,
		final String macroName,
		final int[] primitiveNumbers,
		final Continuation0 continuation)
	{
		assert primitiveNumbers.length > 0;
		final A_String availName = StringDescriptor.from(macroName);
		final A_Phrase nameLiteral =
			LiteralNodeDescriptor.syntheticFrom(availName);
		final List<A_Function> functionsList = new ArrayList<A_Function>();
		for (final int primitiveNumber : primitiveNumbers)
		{
			functionsList.add(
				MethodDescriptor.newPrimitiveFunction(
					Primitive.byPrimitiveNumberOrFail(primitiveNumber)));
		}
		final A_Function body = functionsList.remove(functionsList.size() - 1);
		final A_Tuple functionsTuple = TupleDescriptor.fromList(functionsList);
		final A_Phrase send = SendNodeDescriptor.from(
			MethodDescriptor.vmMacroDefinerAtom().bundleOrNil(),
			ListNodeDescriptor.newExpressions(TupleDescriptor.from(
				nameLiteral,
				LiteralNodeDescriptor.syntheticFrom(functionsTuple),
				LiteralNodeDescriptor.syntheticFrom(body))),
			TOP.o());
		evaluateModuleStatementThen(
			state,
			send,
			continuation,
			new Continuation1<Throwable>()
			{
				@Override
				public void value (final @Nullable Throwable killer)
				{
					assert killer != null;
					compilationFailed(killer);
				}
			});
	}

	/**
	 * Serialize a function that will publish all atoms that are currently
	 * public in the module.
	 *
	 * @param isPublic
	 *        {@code true} if the atoms are public, {@code false} if they are
	 *        private.
	 */
	@InnerAccess void serializePublicationFunction (final boolean isPublic)
	{
		// Output a function that publishes the initial public set of atoms.
		final A_Map sourceNames =
			isPublic ? module.importedNames() : module.privateNames();
		A_Set names = SetDescriptor.empty();
		for (final MapDescriptor.Entry entry : sourceNames.mapIterable())
		{
			names = names.setUnionCanDestroy(entry.value(), false);
		}
		final A_Phrase send = SendNodeDescriptor.from(
			MethodDescriptor.vmPublishAtomsAtom().bundleOrNil(),
			ListNodeDescriptor.newExpressions(
				TupleDescriptor.from(
					LiteralNodeDescriptor.syntheticFrom(names),
					LiteralNodeDescriptor.syntheticFrom(
						AtomDescriptor.objectFromBoolean(isPublic)))),
			TOP.o());
		final A_Function function =
			FunctionDescriptor.createFunctionForPhrase(send, module, 0);
		function.makeImmutable();
		synchronized (this)
		{
			serializer.serialize(function);
		}
	}

	/**
	 * Apply a method pragma detected during parse of the {@linkplain
	 * ModuleHeader module header}.
	 *
	 * @param pragmaValue
	 *        The value of the pragma.
	 * @param state
	 *        The {@linkplain ParserState parse state} following a parse of the
	 *        module header.
	 * @param continuation
	 *        What to do after the operation completes.
	 */
	@InnerAccess void applyMethodPragmaThen (
		final String pragmaValue,
		final ParserState state,
		final Continuation0 continuation)
	{
		final String methodName;
		final int primNum;
		try
		{
			final String[] parts = pragmaValue.split("=", 2);
			if (parts.length != 2)
			{
				throw new IllegalArgumentException();
			}
			final String pragmaPrim = parts[0].trim();
			methodName = parts[1].trim();
			primNum = Integer.valueOf(pragmaPrim);
		}
		catch (final IllegalArgumentException e)
		{
			state.expected(String.format(
				"method pragma to have the form "
				+ "%s=<digits>=name",
				PRAGMA_METHOD.lexemeJavaString));
			reportError();
			return;
		}
		bootstrapMethodThen(state, methodName, primNum, continuation);
	}

	/**
	 * Apply a macro pragma detected during parse of the {@linkplain
	 * ModuleHeader module header}.
	 *
	 * @param pragmaValue
	 *        The value of the pragma.
	 * @param state
	 *        The {@linkplain ParserState parse state} following a parse of the
	 *        module header.
	 * @param continuation
	 *        What to do after the operation completes.
	 */
	@InnerAccess void applyMacroPragmaThen (
		final String pragmaValue,
		final ParserState state,
		final Continuation0 continuation)
	{
		final String macroName;
		final int[] primNums;
		try
		{
			final String[] parts = pragmaValue.split("=", 2);
			if (parts.length != 2)
			{
				throw new IllegalArgumentException();
			}
			final String pragmaPrim = parts[0].trim();
			macroName = parts[1].trim();
			final String[] primNumStrings = pragmaPrim.split(",");
			primNums = new int[primNumStrings.length];
			for (int i = 0; i < primNums.length; i++)
			{
				primNums[i] = Integer.valueOf(
					primNumStrings[i]);
			}
		}
		catch (final IllegalArgumentException e)
		{
			state.expected(String.format(
				"macro pragma to have the form "
				+ "%s=<digits‡,>=name",
				PRAGMA_MACRO.lexemeJavaString));
			reportError();
			return;
		}
		bootstrapMacroThen(state, macroName, primNums, continuation);
	}

	/**
	 * Apply a stringify pragma detected during parse of the {@linkplain
	 * ModuleHeader module header}.
	 *
	 * @param pragmaValue
	 *        The value of the pragma.
	 * @param state
	 *        The {@linkplain ParserState parse state} following a parse of the
	 *        module header.
	 * @param continuation
	 *        What to do after the operation completes.
	 */
	@InnerAccess void applyStringifyPragmaThen (
		final String pragmaValue,
		final ParserState state,
		final Continuation0 continuation)
	{
		final A_String availName = StringDescriptor.from(pragmaValue);
		final A_Set atoms = module.trueNamesForStringName(availName);
		if (atoms.setSize() == 0)
		{
			state.expected(String.format(
				"stringification method \"%s\" to be introduced in this module",
				availName.asNativeString()));
			reportError();
			assert false;
		}
		else if (atoms.setSize() > 1)
		{
			state.expected(String.format(
				"stringification method \"%s\" to be unambiguous",
				availName.asNativeString()));
			reportError();
			assert false;
		}
		final A_Atom atom = atoms.asTuple().tupleAt(1);
		final A_Phrase send = SendNodeDescriptor.from(
			MethodDescriptor.vmDeclareStringifierAtom().bundleOrNil(),
			ListNodeDescriptor.newExpressions(TupleDescriptor.from(
				LiteralNodeDescriptor.syntheticFrom(atom))),
			TOP.o());
		evaluateModuleStatementThen(
			state,
			send,
			continuation,
			new Continuation1<Throwable>()
			{
				@Override
				public void value (final @Nullable Throwable killer)
				{
					assert killer != null;
					compilationFailed(killer);
				}
			});
	}

	/**
	 * Apply any pragmas detected during the parse of the {@linkplain
	 * ModuleHeader module header}.
	 *
	 * @param state
	 *        The {@linkplain ParserState parse state} following a parse of the
	 *        module header.
	 * @param continuation
	 *        What to do after the operation completes.
	 */
	private void applyPragmasThen (
		final ParserState state,
		final Continuation0 continuation)
	{
		// If there are no pragmas, then just invoke the success continuation
		// and return.
		final int count = moduleHeader.pragmas.size();
		if (count == 0)
		{
			continuation.value();
			return;
		}
		final Mutable<Integer> outstanding =
			new Mutable<Integer>(moduleHeader.pragmas.size());
		final Continuation0 wrapped =
			new Continuation0()
			{
				@Override
				public void value ()
				{
					synchronized (outstanding)
					{
						outstanding.value--;
						if (outstanding.value == 0)
						{
							continuation.value();
						}
					}
				}
			};
		for (final A_String pragmaString : moduleHeader.pragmas)
		{
			eventuallyDo(new Continuation0()
			{
				@Override
				public void value ()
				{
					final String nativeString = pragmaString.asNativeString();
					final String[] pragmaParts = nativeString.split("=", 2);
					if (pragmaParts.length != 2)
					{
						state.expected("pragma to have the form key=value");
						reportError();
						return;
					}
					final String pragmaKind = pragmaParts[0].trim();
					final String pragmaValue = pragmaParts[1].trim();
					switch (pragmaKind)
					{
						case "method":
							assert pragmaKind.equals(
								PRAGMA_METHOD.lexemeJavaString);
							applyMethodPragmaThen(pragmaValue, state, wrapped);
							break;
						case "macro":
							assert pragmaKind.equals(
								PRAGMA_MACRO.lexemeJavaString);
							applyMacroPragmaThen(pragmaValue, state, wrapped);
							break;
						case "stringify":
							assert pragmaKind.equals(
								PRAGMA_STRINGIFY.lexemeJavaString);
							applyStringifyPragmaThen(
								pragmaValue, state, wrapped);
							break;
						default:
							state.expected(String.format(
								"pragma key to be one of "
								+ "%s, %s, or %s",
								PRAGMA_METHOD.lexemeJavaString,
								PRAGMA_MACRO.lexemeJavaString,
								PRAGMA_STRINGIFY.lexemeJavaString));
							reportError();
							return;
					}
				}
			});
		}
	}

	/**
	 * Parse a {@linkplain ModuleHeader module header} from the {@linkplain
	 * TokenDescriptor token list} and apply any side-effects. Then
	 * {@linkplain #parseModuleBody(ParserState) parse a module body} and apply
	 * any side-effects.
	 */
	@InnerAccess void parseModuleCompletely ()
	{
		final ParserState afterHeader = parseModuleHeader(false);
		if (afterHeader == null)
		{
			reportError();
			return;
		}
		// Update the reporter. This condition just prevents
		// the reporter from being called twice at the end of a
		// file.
		else if (!afterHeader.atEnd())
		{
			final A_Token token = afterHeader.peekToken();
			final Continuation4<ModuleName, Long, Long, Long> reporter =
				progressReporter;
			assert reporter != null;
			reporter.value(
				moduleHeader.moduleName,
				(long) token.lineNumber(),
				(long) token.start(),
				(long) source.length());
		}
		assert afterHeader != null;
		// Run any side-effects implied by this module header
		// against the module.
		final String errorString = moduleHeader.applyToModule(module, runtime);
		if (errorString != null)
		{
			afterHeader.expected(errorString);
			reportError();
			return;
		}
		synchronized (this)
		{
			serializer.serialize(
				AtomDescriptor.moduleHeaderSectionAtom());
			moduleHeader.serializeHeaderOn(serializer);
			serializer.serialize(
				AtomDescriptor.moduleBodySectionAtom());
		}
		loader().createFilteredBundleTree();
		applyPragmasThen(
			afterHeader,
			new Continuation0()
			{
				@Override
				public void value ()
				{
					// Parse the body of the module.
					if (!afterHeader.atEnd())
					{
						eventuallyDo(new Continuation0()
						{
							@Override
							public void value ()
							{
								parseModuleBody(afterHeader);
							}
						});
					}
					else
					{
						final Continuation0 reporter = successReporter;
						assert reporter != null;
						reporter.value();
					}
				}
			});
	}

	/**
	 * Parse a {@linkplain ModuleDescriptor module} from the {@linkplain
	 * TokenDescriptor token} list and install it into the {@linkplain
	 * AvailRuntime runtime}.
	 *
	 * @param afterHeader
	 *        The {@linkplain ParserState parse state} after parsing a
	 *        {@linkplain ModuleHeader module header}.
	 */
	@InnerAccess void parseModuleBody (final ParserState afterHeader)
	{
		final MutableOrNull<Con<A_Phrase>> parseOutermost =
			new MutableOrNull<Con<A_Phrase>>();
		final Mutable<ParserState> lastStart =
			new Mutable<ParserState>(afterHeader);
		parseOutermost.value = new Con<A_Phrase>("Outermost statement")
		{
			@Override
			public void value (
				final @Nullable ParserState afterStatement,
				final @Nullable A_Phrase unambiguousStatement)
			{
				assert afterStatement != null;
				assert unambiguousStatement != null;
				synchronized (AbstractAvailCompiler.this)
				{
					assert workUnitsQueued == workUnitsCompleted;
				}

				if (!unambiguousStatement.expressionType().equals(TOP.o()))
				{
					afterStatement.expected(
						"top-level statement to have type ⊤");
					reportError();
					return;
				}

				// Clear the section of the fragment cache associated with
				// the (outermost) statement just parsed...
				fragmentCache.clear();
				evaluateModuleStatementThen(
					lastStart.value,
					unambiguousStatement,
					new Continuation0()
					{
						@Override
						public void value ()
						{
							// If this was not the last statement, then report
							// progress.
							if (!afterStatement.atEnd())
							{
								final A_Token token =
									tokens.get(afterStatement.position - 1);
								final
									Continuation4<ModuleName, Long, Long, Long>
										reporter = progressReporter;
								assert reporter != null;
								reporter.value(
									moduleHeader.moduleName,
									(long) token.lineNumber(),
									(long) token.start() + 2,
									(long) source.length());
								eventuallyDo(new Continuation0()
								{
									@Override
									public void value ()
									{
										lastStart.value = afterStatement;
										greatestGuess = 0;
										greatExpectations.clear();
										parseOutermostStatement(
											new ParserState(
												afterStatement.position,
												afterHeader.clientDataMap),
											parseOutermost.value());
									}
								});
							}
							// Otherwise, make sure that all forwards were
							// resolved.
							else if (loader().pendingForwards.setSize() != 0)
							{
								@SuppressWarnings("resource")
								final Formatter formatter = new Formatter();
								formatter.format(
									"the following forwards to be resolved:");
								for (final A_BasicObject forward
									: loader().pendingForwards)
								{
									formatter.format("%n\t%s", forward);
								}
								afterStatement.expected(formatter.toString());
								reportError();
								return;
							}
							// Otherwise, report success.
							else
							{
								final Continuation0 reporter = successReporter;
								assert reporter != null;
								reporter.value();
							}
						}
					},
					new Continuation1<Throwable>()
					{
						@Override
						public void value (final @Nullable Throwable killer)
						{
							assert killer != null;
							compilationFailed(killer);
						}
					});
			}
		};
		greatestGuess = 0;
		greatExpectations.clear();
		parseOutermostStatement(afterHeader, parseOutermost.value());
	}

	/**
	 * The {@linkplain Continuation4 continuation} that reports compilation
	 * progress at various checkpoints. It that accepts the {@linkplain
	 * ResolvedModuleName name} of the {@linkplain ModuleDescriptor module}
	 * undergoing {@linkplain AbstractAvailCompiler compilation}, the line
	 * number on which the last complete statement concluded, the position of
	 * the ongoing parse (in bytes), and the size of the module (in bytes).
	 */
	@InnerAccess @Nullable Continuation4<ModuleName, Long, Long, Long>
		progressReporter;

	/**
	 * The {@linkplain Continuation1 continuation} that reports success of
	 * compilation.
	 */
	@InnerAccess @Nullable Continuation0 successReporter;

	/**
	 * The {@linkplain Continuation0 continuation} that reports failure of
	 * compilation.
	 */
	@InnerAccess @Nullable Continuation0 failureReporter;

	/**
	 * Parse a {@linkplain ModuleHeader module header} from the {@linkplain
	 * TokenDescriptor token} list.
	 *
	 * @return A module header.
	 */
	public @Nullable ModuleHeader parseModuleHeader ()
	{
		greatestGuess = -1;
		greatExpectations.clear();
		if (parseModuleHeader(true) == null)
		{
			reportError();
			return null;
		}
		return moduleHeader;
	}

	/**
	 * Parse a {@linkplain ModuleDescriptor module} from the {@linkplain
	 * TokenDescriptor token} list and install it into the {@linkplain
	 * AvailRuntime runtime}.
	 *
	 * @param reporter
	 *        How to report progress to the client who instigated compilation.
	 *        This {@linkplain Continuation4 continuation} that accepts the
	 *        {@linkplain ModuleName name} of the {@linkplain ModuleDescriptor
	 *        module} undergoing {@linkplain AbstractAvailCompiler compilation},
	 *        the line number on which the last complete statement concluded,
	 *        the position of the ongoing parse (in bytes), and the size of the
	 *        module (in bytes).
	 * @param succeed
	 *        What to do after compilation succeeds. This {@linkplain
	 *        Continuation1 continuation} is invoked with the completed module.
	 * @param fail
	 *        What to do after compilation fails. This {@linkplain Continuation1
	 *        continuation} is invoked with the terminating {@linkplain
	 *        Throwable throwable}.
	 */
	public void parseModule (
		final Continuation4<ModuleName, Long, Long, Long> reporter,
		final Continuation1<A_Module> succeed,
		final Continuation1<Throwable> fail)
	{
		progressReporter = reporter;
		successReporter = new Continuation0()
		{
			@Override
			public void value ()
			{
				serializePublicationFunction(true);
				commitModuleTransaction();
				succeed.value(module);
			}
		};
		failureReporter = new Continuation0()
		{
			@Override
			public void value ()
			{
				rollbackModuleTransaction();
				fail.value(terminator);
			}
		};
		startModuleTransaction();
		eventuallyDo(new Continuation0()
		{
			@Override
			public void value ()
			{
				parseModuleCompletely();
			}
		});
	}

	/**
	 * Parse the header of the module from the token stream. If successful,
	 * return the {@link ParserState} just after the header, otherwise return
	 * {@code null}.
	 *
	 * <p>If the {@code dependenciesOnly} parameter is true, only parse the bare
	 * minimum needed to determine information about which modules are used by
	 * this one.</p>
	 *
	 * @param dependenciesOnly
	 *        Whether to do the bare minimum parsing required to determine the
	 *        modules to which this one refers.
	 * @return The state of parsing just after the header, or {@code null} if it
	 *         failed.
	 */
	private @Nullable ParserState parseModuleHeader (
		final boolean dependenciesOnly)
	{
		// Create the initial parser state: no tokens have been seen, and no
		// names are in scope.
		ParserState state = new ParserState(
			0,
			MapDescriptor.empty().mapAtPuttingCanDestroy(
				compilerScopeMapKey,
				MapDescriptor.empty(),
				false));

		// The module header must begin with either SYSTEM MODULE or MODULE,
		// followed by the local name of the module.
		if (isSystemCompiler())
		{
			if (!state.peekToken(SYSTEM, "System keyword"))
			{
				return null;
			}
			state = state.afterToken();
		}
		if (!state.peekToken(ExpectedToken.MODULE, "Module keyword"))
		{
			return null;
		}
		state = state.afterToken();
		final A_Token localNameToken = state.peekStringLiteral();
		if (localNameToken == null)
		{
			state.expected("module name");
			return null;
		}
		if (!dependenciesOnly)
		{
			final A_String localName = localNameToken.literal();
			if (!moduleHeader.moduleName.localName().equals(
				localName.asNativeString()))
			{
				state.expected("declared local module name to agree with "
						+ "fully-qualified module name");
				return null;
			}
		}
		state = state.afterToken();

		// Module header section tracking.
		final List<ExpectedToken> expected = new ArrayList<ExpectedToken>(
			asList(VERSIONS, EXTENDS, USES, NAMES, ENTRIES, PRAGMA, BODY));
		final Set<A_String> seen = new HashSet<A_String>();
		final Describer expectedMessage = new Describer()
		{
			@Override
			public void describeThen (final @Nullable Continuation1<String> c)
			{
				assert c != null;
				final StringBuilder builder = new StringBuilder();
				builder.append(
					expected.size() == 1
					? "module header keyword "
					: "one of the following module header keywords: ");
				boolean first = true;
				for (final ExpectedToken token : expected)
				{
					if (!first)
					{
						builder.append(", ");
					}
					builder.append(token.lexeme().asNativeString());
					first = false;
				}
				c.value(builder.toString());
			}
		};

		// Permit the other sections to appear optionally, singly, and in any
		// order. Parsing of the module header is complete when BODY has been
		// consumed.
		while (true)
		{
			final A_Token token = state.peekToken();
			final A_String lexeme = token.string();
			int tokenIndex = 0;
			for (final ExpectedToken expectedToken : expected)
			{
				if (expectedToken.tokenType() == token.tokenType()
					&& expectedToken.lexeme().equals(lexeme))
				{
					break;
				}
				tokenIndex++;
			}
			// The token was not recognized as beginning a module section, so
			// record what was expected and fail the parse.
			if (tokenIndex == expected.size())
			{
				if (seen.contains(lexeme))
				{
					state.expected(
						lexeme.asNativeString()
						+ " keyword (and related section) to occur only once");
				}
				else
				{
					state.expected(expectedMessage);
				}
				return null;
			}
			expected.remove(tokenIndex);
			seen.add(lexeme);
			state = state.afterToken();
			// When BODY has been encountered, the parse of the module header is
			// complete.
			if (lexeme.equals(BODY.lexeme()))
			{
				return state;
			}
			// On VERSIONS, record the versions.
			else if (lexeme.equals(VERSIONS.lexeme()))
			{
				state = parseStringLiterals(state, moduleHeader.versions);
			}
			// On EXTENDS, record the imports.
			else if (lexeme.equals(EXTENDS.lexeme()))
			{
				state = parseModuleImports(
					state, moduleHeader.importedModules, true);
			}
			// On USES, record the imports.
			else if (lexeme.equals(USES.lexeme()))
			{
				state = parseModuleImports(
					state, moduleHeader.importedModules, false);
			}
			// On NAMES, record the names.
			else if (lexeme.equals(NAMES.lexeme()))
			{
				state = parseStringLiterals(state, moduleHeader.exportedNames);
			}
			// ON ENTRIES, record the names.
			else if (lexeme.equals(ENTRIES.lexeme()))
			{
				state = parseStringLiterals(state, moduleHeader.entryPoints);
			}
			// On PRAGMA, record the pragma strings.
			else if (lexeme.equals(PRAGMA.lexeme()))
			{
				state = parseStringLiterals(state, moduleHeader.pragmas);
			}
			// If the parser state is now null, then fail the parse.
			if (state == null)
			{
				return null;
			}
		}
	}

	/**
	 * Parse an expression. Backtracking will find all valid interpretations.
	 * This method is a key optimization point, so the fragmentCache is used to
	 * keep track of parsing solutions at this point, simply replaying them on
	 * subsequent parses, as long as the variable declarations up to that point
	 * were identical.
	 *
	 * <p>
	 * Additionally, the fragmentCache also keeps track of actions to perform
	 * when another solution is found at this position, so the solutions and
	 * actions can be added in arbitrary order while ensuring that each action
	 * gets a chance to try each solution.
	 * </p>
	 *
	 * @param start
	 *        Where to start parsing.
	 * @param originalContinuation
	 *        What to do with the expression.
	 */
	void parseExpressionThen (
		final ParserState start,
		final Con<A_Phrase> originalContinuation)
	{
		synchronized (fragmentCache)
		{
			// The first time we parse at this position the fragmentCache will
			// have no knowledge about it.
			if (!fragmentCache.hasStartedParsingAt(start))
			{
				fragmentCache.indicateParsingHasStartedAt(start);
				workUnitDo(
					new Continuation0()
					{
						@Override
						public void value ()
						{
							parseExpressionUncachedThen(
								start,
								new Con<A_Phrase>("Uncached expression")
								{
									@Override
									public void value (
										final @Nullable ParserState afterExpr,
										final @Nullable A_Phrase expr)
									{
										assert afterExpr != null;
										assert expr != null;
										synchronized (fragmentCache)
										{
											fragmentCache.addSolution(
												start,
												new AvailCompilerCachedSolution(
													afterExpr,
													expr));
										}
									}
								});
						}
					},
					"Capture expression for caching",
					start);
			}
			fragmentCache.addAction(start, originalContinuation);
		}
	}

	/**
	 * Parse an expression whose type is (at least) someType. There may be
	 * multiple expressions that start at the specified starting point.  Only
	 * evaluate expressions whose static type is as strong as the expected type.
	 *
	 * @param start
	 *        Where to start parsing.
	 * @param someType
	 *        The type that the expression must return.
	 * @param continuation
	 *        What to do with the result of expression evaluation.
	 */
	void parseAndEvaluateExpressionYieldingInstanceOfThen (
		final ParserState start,
		final A_Type someType,
		final Con<AvailObject> continuation)
	{
		final A_Map clientDataInModuleScope =
			start.clientDataMap.mapAtPuttingCanDestroy(
				compilerScopeMapKey,
				MapDescriptor.empty(),
				false);
		final ParserState startWithoutScope = new ParserState(
			start.position,
			clientDataInModuleScope);
		parseExpressionThen(
			startWithoutScope,
			new Con<A_Phrase>("Evaluate expression")
			{
				@SuppressWarnings("null")
				@Override
				public void value (
					final @Nullable ParserState afterExpression,
					final @Nullable A_Phrase expression)
				{
					if (!expression.expressionType().isSubtypeOf(someType))
					{
						afterExpression.expected(
							asList(someType),
							new Transformer1<List<String>, String>()
							{
								@Override
								public @Nullable String value (
									final @Nullable List<String> list)
								{
									assert list != null;
									return String.format(
										"expression to have type %s",
										list.get(0));
								}
							});
						return;
					}
					startWorkUnit();
					evaluatePhraseThen(
						expression,
						start.peekToken().lineNumber(),
						false,
						workUnitCompletion(
							new Continuation1<AvailObject>()
							{
								@Override
								public void value (
									final @Nullable AvailObject value)
								{
									assert value != null;
									if (!value.isInstanceOf(someType))
									{
										afterExpression.expected(
											"expression to respect "
											+ "its own type declaration");
										return;
									}
									assert afterExpression.clientDataMap.equals(
										startWithoutScope.clientDataMap)
									: "Subexpression should not have been able "
										+ "to cause declaration";
									// Make sure we continue at the position
									// after the expression, but using the scope
									// stack we started with. That's because the
									// expression was parsed for execution, and
									// as such was excluded from seeing things
									// that would be in scope for regular
									// subexpressions at this point.
									attempt(
										new ParserState(
											afterExpression.position,
											start.clientDataMap),
										continuation,
										value);
								}
						}),
						new Continuation1<Throwable>()
						{
							@Override
							public void value (
								final @Nullable Throwable killer)
							{
								assert killer != null;
								compilationFailed(killer);
							}
						});
					}
			});
	}

	/**
	 * Parse a top-level statement.  This is the <em>only</em> boundary for the
	 * backtracking grammar (it used to be that <em>all</em> statements had to
	 * be unambiguous, even those in blocks).  The passed continuation will be
	 * invoked at most once, and only if the top-level statement had a single
	 * interpretation.
	 *
	 * @param start
	 *            Where to start parsing a top-level statement.
	 * @param continuation
	 *            What to do with the (unambiguous) top-level statement.
	 */
	abstract void parseOutermostStatement (
		final ParserState start,
		final Con<A_Phrase> continuation);

	/**
	 * Parse an expression, without directly using the
	 * {@linkplain #fragmentCache}.
	 *
	 * @param start
	 *            Where to start parsing.
	 * @param continuation
	 *            What to do with the expression.
	 */
	abstract void parseExpressionUncachedThen (
		final ParserState start,
		final Con<A_Phrase> continuation);

	/**
	 * Parse and return an occurrence of a raw keyword, literal, or operator
	 * token.  If no suitable token is present, answer null.  The caller is
	 * responsible for skipping the token if it was parsed.
	 *
	 * @param start
	 *            Where to start parsing.
	 * @return
	 *            The token or {@code null}.
	 */
	protected @Nullable A_Token parseRawTokenOrNull (
		final ParserState start)
	{
		final A_Token token = start.peekToken();
		switch (token.tokenType())
		{
			case KEYWORD:
			case OPERATOR:
			case LITERAL:
				return token;
			default:
				return null;
		}
	}

	/**
	 * A helper method to queue a parsing activity for continuing to parse a
	 * {@linkplain SendNodeDescriptor send phrase}.
	 *
	 * @param description
	 * @param start
	 * @param bundleTree
	 * @param firstArgOrNull
	 * @param initialTokenPosition
	 * @param consumedAnything
	 * @param argsSoFar
	 * @param marksSoFar
	 * @param continuation
	 */
	@InnerAccess void eventuallyParseRestOfSendNode (
		final String description,
		final ParserState start,
		final A_BundleTree bundleTree,
		final @Nullable A_Phrase firstArgOrNull,
		final ParserState initialTokenPosition,
		final boolean consumedAnything,
		final List<A_Phrase> argsSoFar,
		final List<Integer> marksSoFar,
		final Con<A_Phrase> continuation)
	{
		workUnitDo(
			new Continuation0()
			{
				@Override
				public void value ()
				{
					parseRestOfSendNode(
						start,
						bundleTree,
						firstArgOrNull,
						initialTokenPosition,
						consumedAnything,
						argsSoFar,
						marksSoFar,
						continuation);
				}
			},
			description,
			start);
	}

	/**
	 * Answer the {@linkplain SetDescriptor set} of {@linkplain
	 * DeclarationNodeDescriptor declaration nodes} which are used by this
	 * parse tree but are locally declared (i.e., not at global module scope).
	 *
	 * @param parseTree
	 *            The parse tree to examine.
	 * @return
	 *            The set of the local declarations that were used in the parse
	 *            tree.
	 */
	A_Set usesWhichLocalVariables (
		final A_Phrase parseTree)
	{
		final Mutable<A_Set> usedDeclarations =
			new Mutable<A_Set>(SetDescriptor.empty());
		parseTree.childrenDo(new Continuation1<A_Phrase>()
		{
			@Override
			public void value (final @Nullable A_Phrase node)
			{
				assert node != null;
				if (node.isInstanceOfKind(VARIABLE_USE_NODE.mostGeneralType()))
				{
					final A_Phrase declaration = node.declaration();
					if (!declaration.declarationKind().isModuleScoped())
					{
						usedDeclarations.value =
							usedDeclarations.value.setWithElementCanDestroy(
								declaration,
								true);
					}
				}
			}
		});
		return usedDeclarations.value;
	}
}
