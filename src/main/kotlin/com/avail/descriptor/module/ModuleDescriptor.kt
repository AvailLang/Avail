/*
 * ModuleDescriptor.kt
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

import com.avail.AvailRuntime
import com.avail.builder.ModuleName
import com.avail.builder.ResolvedModuleName
import com.avail.builder.UnresolvedDependencyException
import com.avail.compiler.ModuleHeader
import com.avail.compiler.splitter.MessageSplitter
import com.avail.descriptor.atoms.A_Atom
import com.avail.descriptor.atoms.A_Atom.Companion.atomName
import com.avail.descriptor.atoms.A_Atom.Companion.bundleOrCreate
import com.avail.descriptor.atoms.A_Atom.Companion.bundleOrNil
import com.avail.descriptor.atoms.A_Atom.Companion.setAtomBundle
import com.avail.descriptor.atoms.AtomDescriptor
import com.avail.descriptor.atoms.AtomWithPropertiesSharedDescriptor
import com.avail.descriptor.bundles.A_Bundle
import com.avail.descriptor.bundles.A_Bundle.Companion.bundleMethod
import com.avail.descriptor.bundles.A_Bundle.Companion.definitionParsingPlans
import com.avail.descriptor.bundles.A_Bundle.Companion.macrosTuple
import com.avail.descriptor.bundles.A_Bundle.Companion.message
import com.avail.descriptor.bundles.A_Bundle.Companion.messageSplitter
import com.avail.descriptor.bundles.A_BundleTree
import com.avail.descriptor.bundles.A_BundleTree.Companion.addPlanInProgress
import com.avail.descriptor.bundles.MessageBundleDescriptor
import com.avail.descriptor.bundles.MessageBundleDescriptor.Companion.newBundle
import com.avail.descriptor.bundles.MessageBundleTreeDescriptor
import com.avail.descriptor.bundles.MessageBundleTreeDescriptor.Companion.newBundleTree
import com.avail.descriptor.functions.A_Function
import com.avail.descriptor.functions.A_RawFunction
import com.avail.descriptor.functions.FunctionDescriptor
import com.avail.descriptor.maps.A_Map
import com.avail.descriptor.maps.A_Map.Companion.forEach
import com.avail.descriptor.maps.A_Map.Companion.hasKey
import com.avail.descriptor.maps.A_Map.Companion.keysAsSet
import com.avail.descriptor.maps.A_Map.Companion.mapAt
import com.avail.descriptor.maps.A_Map.Companion.mapAtPuttingCanDestroy
import com.avail.descriptor.maps.A_Map.Companion.mapAtReplacingCanDestroy
import com.avail.descriptor.maps.A_Map.Companion.mapIterable
import com.avail.descriptor.maps.A_Map.Companion.mapWithoutKeyCanDestroy
import com.avail.descriptor.maps.A_Map.Companion.valuesAsTuple
import com.avail.descriptor.maps.MapDescriptor
import com.avail.descriptor.maps.MapDescriptor.Companion.emptyMap
import com.avail.descriptor.methods.A_Definition
import com.avail.descriptor.methods.A_GrammaticalRestriction
import com.avail.descriptor.methods.A_Macro
import com.avail.descriptor.methods.A_Method
import com.avail.descriptor.methods.A_SemanticRestriction
import com.avail.descriptor.methods.DefinitionDescriptor
import com.avail.descriptor.methods.ForwardDefinitionDescriptor
import com.avail.descriptor.methods.MethodDescriptor
import com.avail.descriptor.methods.SemanticRestrictionDescriptor
import com.avail.descriptor.module.A_Module.Companion.addImportedNames
import com.avail.descriptor.module.A_Module.Companion.addPrivateName
import com.avail.descriptor.module.A_Module.Companion.addPrivateNames
import com.avail.descriptor.module.A_Module.Companion.allAncestors
import com.avail.descriptor.module.A_Module.Companion.importedNames
import com.avail.descriptor.module.A_Module.Companion.introduceNewName
import com.avail.descriptor.module.A_Module.Companion.methodDefinitions
import com.avail.descriptor.module.A_Module.Companion.moduleName
import com.avail.descriptor.module.A_Module.Companion.moduleState
import com.avail.descriptor.module.A_Module.Companion.newNames
import com.avail.descriptor.module.A_Module.Companion.setModuleState
import com.avail.descriptor.module.A_Module.Companion.trueNamesForStringName
import com.avail.descriptor.module.A_Module.Companion.versions
import com.avail.descriptor.module.A_Module.Companion.visibleNames
import com.avail.descriptor.module.ModuleDescriptor.ObjectSlots.ALL_BLOCK_PHRASES
import com.avail.descriptor.module.ModuleDescriptor.ObjectSlots.ALL_TOP_PHRASE_STYLES
import com.avail.descriptor.module.ModuleDescriptor.ObjectSlots.BUNDLES
import com.avail.descriptor.module.ModuleDescriptor.ObjectSlots.CACHED_EXPORTED_NAMES
import com.avail.descriptor.module.ModuleDescriptor.ObjectSlots.CONSTANT_BINDINGS
import com.avail.descriptor.module.ModuleDescriptor.ObjectSlots.IMPORTED_NAMES
import com.avail.descriptor.module.ModuleDescriptor.ObjectSlots.LEXERS
import com.avail.descriptor.module.ModuleDescriptor.ObjectSlots.METHOD_DEFINITIONS_SET
import com.avail.descriptor.module.ModuleDescriptor.ObjectSlots.NEW_NAMES
import com.avail.descriptor.module.ModuleDescriptor.ObjectSlots.PRIVATE_NAMES
import com.avail.descriptor.module.ModuleDescriptor.ObjectSlots.SEALS
import com.avail.descriptor.module.ModuleDescriptor.ObjectSlots.UNLOAD_FUNCTIONS
import com.avail.descriptor.module.ModuleDescriptor.ObjectSlots.VARIABLE_BINDINGS
import com.avail.descriptor.module.ModuleDescriptor.ObjectSlots.VISIBLE_NAMES
import com.avail.descriptor.module.ModuleDescriptor.State.Loaded
import com.avail.descriptor.module.ModuleDescriptor.State.Loading
import com.avail.descriptor.module.ModuleDescriptor.State.Unloaded
import com.avail.descriptor.module.ModuleDescriptor.State.Unloading
import com.avail.descriptor.numbers.A_Number
import com.avail.descriptor.numbers.A_Number.Companion.extractLong
import com.avail.descriptor.numbers.IntegerDescriptor.Companion.fromInt
import com.avail.descriptor.parsing.A_Lexer
import com.avail.descriptor.parsing.A_Lexer.Companion.lexerMethod
import com.avail.descriptor.parsing.ParsingPlanInProgressDescriptor.Companion.newPlanInProgress
import com.avail.descriptor.phrases.A_Phrase
import com.avail.descriptor.representation.A_BasicObject
import com.avail.descriptor.representation.AbstractSlotsEnum
import com.avail.descriptor.representation.AvailObject
import com.avail.descriptor.representation.AvailObject.Companion.multiplier
import com.avail.descriptor.representation.AvailObjectFieldHelper
import com.avail.descriptor.representation.Descriptor
import com.avail.descriptor.representation.Mutability
import com.avail.descriptor.representation.Mutability.SHARED
import com.avail.descriptor.representation.NilDescriptor.Companion.nil
import com.avail.descriptor.representation.ObjectSlotsEnum
import com.avail.descriptor.sets.A_Set
import com.avail.descriptor.sets.A_Set.Companion.hasElement
import com.avail.descriptor.sets.A_Set.Companion.setIntersects
import com.avail.descriptor.sets.A_Set.Companion.setMinusCanDestroy
import com.avail.descriptor.sets.A_Set.Companion.setSize
import com.avail.descriptor.sets.A_Set.Companion.setUnionCanDestroy
import com.avail.descriptor.sets.A_Set.Companion.setWithElementCanDestroy
import com.avail.descriptor.sets.A_Set.Companion.setWithoutElementCanDestroy
import com.avail.descriptor.sets.SetDescriptor
import com.avail.descriptor.sets.SetDescriptor.Companion.emptySet
import com.avail.descriptor.sets.SetDescriptor.Companion.setFromCollection
import com.avail.descriptor.sets.SetDescriptor.Companion.singletonSet
import com.avail.descriptor.tuples.A_String
import com.avail.descriptor.tuples.A_Tuple
import com.avail.descriptor.tuples.A_Tuple.Companion.appendCanDestroy
import com.avail.descriptor.tuples.A_Tuple.Companion.asSet
import com.avail.descriptor.tuples.A_Tuple.Companion.tupleAt
import com.avail.descriptor.tuples.A_Tuple.Companion.tupleReverse
import com.avail.descriptor.tuples.A_Tuple.Companion.tupleSize
import com.avail.descriptor.tuples.StringDescriptor
import com.avail.descriptor.tuples.StringDescriptor.Companion.stringFrom
import com.avail.descriptor.tuples.TupleDescriptor
import com.avail.descriptor.tuples.TupleDescriptor.Companion.emptyTuple
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.A_Type.Companion.isSubtypeOf
import com.avail.descriptor.types.TypeDescriptor.Types
import com.avail.descriptor.types.TypeTag
import com.avail.descriptor.types.VariableTypeDescriptor.Companion.mostGeneralVariableType
import com.avail.descriptor.variables.A_Variable
import com.avail.exceptions.AvailErrorCode.E_MODULE_IS_CLOSED
import com.avail.exceptions.AvailRuntimeException
import com.avail.exceptions.MalformedMessageException
import com.avail.interpreter.execution.AvailLoader
import com.avail.interpreter.execution.AvailLoader.LexicalScanner
import com.avail.persistence.IndexedFile.Companion.validatedBytesFrom
import com.avail.serialization.Deserializer
import com.avail.serialization.SerializerOperation
import com.avail.utility.json.JSONWriter
import com.avail.utility.safeWrite
import com.avail.utility.structures.BloomFilter
import java.util.IdentityHashMap
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.withLock

/**
 * A [module][ModuleDescriptor] is the mechanism by which Avail code is
 * organized.  Modules are parsed from files with the extension ".avail" which
 * contain information about:
 *
 *  * the module's name,
 *  * the set of version strings for which this module claims to be compatible,
 *  * the module's prerequisites,
 *  * the names to be exported from the module,
 *  * methods and macros defined in this module,
 *  * negative-precedence rules to help disambiguate complex expressions,
 *  * variables and constants private to the module.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @constructor
 * Construct a new `ModuleDescriptor`.
 *
 * @param mutability
 *   The [mutability][Mutability] of the new descriptor.
 * @property moduleName
 *   The [A_String] name of the module.
 */
class ModuleDescriptor private constructor(
	mutability: Mutability,
	val moduleName: A_String
) : Descriptor(mutability, TypeTag.MODULE_TAG, ObjectSlots::class.java, null)
{
	/**
	 * The layout of object slots for my instances.
	 */
	enum class ObjectSlots : ObjectSlotsEnum
	{
		/**
		 * A [map][A_Map] from [strings][A_String] to [atoms][A_Atom] which act
		 * as true names. The true names are identity-based identifiers that
		 * prevent or at least clarify name conflicts. This field holds only
		 * those names that are newly added by this module.
		 */
		NEW_NAMES,

		/**
		 * A [map][A_Map] from [strings][A_String] to [atoms][A_Atom] which act
		 * as true names. The true names are identity-based identifiers that
		 * prevent or at least clarify name conflicts. This field holds only
		 * those names that have been imported from other modules.
		 */
		IMPORTED_NAMES,

		/**
		 * A [map][A_Map] from [strings][A_String] to [atoms][A_Atom] which act
		 * as true names. The true names are identity-based identifiers that
		 * prevent or at least clarify name conflicts. This field holds only
		 * those names that are neither imported from another module nor
		 * exported from the current module.
		 */
		PRIVATE_NAMES,

		/**
		 * A [set][A_Set] of [true&#32;names][A_Atom] that are visible within
		 * this module.
		 */
		VISIBLE_NAMES,

		/**
		 * A redundant cached [set][A_Set] of [atoms][A_Atom] that have been
		 * exported. These are precisely the [imported names][IMPORTED_NAMES]
		 * less the [private names][PRIVATE_NAMES].  This is [nil] during module
		 * loading or compiling, but can be computed and cached if requested
		 * afterward.
		 */
		CACHED_EXPORTED_NAMES,

		/**
		 * The [set][A_Set] of [bundles][A_Bundle] defined within this module.
		 */
		BUNDLES,

		/**
		 * A [set][SetDescriptor] of [definitions][DefinitionDescriptor] which
		 * implement methods (and forward declarations, abstract declarations,
		 * etc.).
		 */
		METHOD_DEFINITIONS_SET,

		/**
		 * A [map][A_Map] from [strings][A_String] to
		 * [module&#32;variables][A_Variable].
		 */
		VARIABLE_BINDINGS,

		/**
		 * A [map][A_Map] from [string][A_String] to an [AvailObject].
		 */
		CONSTANT_BINDINGS,

		/**
		 * A [map][MapDescriptor] from [true&#32;names][AtomDescriptor] to
		 * [tuples][TupleDescriptor] of seal points.
		 */
		SEALS,

		/**
		 * A [tuple][TupleDescriptor] of [functions][FunctionDescriptor] that
		 * should be applied when this [module][ModuleDescriptor] is unloaded.
		 */
		UNLOAD_FUNCTIONS,

		/**
		 * The [A_Set] of [A_Lexer]s defined by this module.
		 */
		LEXERS,

		/**
		 * An [A_Tuple] of all block phrases produced during compilation, in the
		 * order in which the block phrases were produced.  Each [A_RawFunction]
		 * contains an index into this tuple, as well as a reference to this
		 * [A_Module].
		 *
		 * This field is populated during module compilation, and is written to
		 * the repository immediately after, at which time the field is set to
		 * an [A_Number] containing the [Long] index into the repository where
		 * the block phrase tuple is written.  Upon fast-loading, this field is
		 * set by the loading mechanism to that same [A_Number], allowing the
		 * block phrases to be fetched only if needed.
		 */
		ALL_BLOCK_PHRASES,

		/**
		 * An [A_Tuple] of all phrase styles produced during compilation, which
		 * is when styling is produced.  Each entry indicates the starting line,
		 * starting column, ending line, and ending column that is to be styled.
		 * This is necessary because multiple top-level statements may occur on
		 * the same line.
		 *
		 * The phrase style information *does not* include a way to get to the
		 * corresponding phrases in [ALL_BLOCK_PHRASES] (which includes an entry
		 * for each raw function, not just top-level ones).
		 *
		 * This field is populated during module compilation, and is written to
		 * the repository immediately after, at which time the field is set to
		 * an [A_Number] containing the [Long] index into the repository where
		 * this tuple of styles is written.  It is expected that a development
		 * environment will request this style tuple at the same time that the
		 * source code is requested and displayed.
		 */
		ALL_TOP_PHRASE_STYLES
	}

	/**
	 * The [set][A_Set] of all ancestor modules of this module, but *not*
	 * including the module itself.  The provided set must always be [SHARED].
	 */
	val allAncestors = AtomicReference<A_Set>(emptySet)

	/**
	 * The [set][A_Set] of [versions][A_String] that this module alleges to
	 * support.
	 */
	@Volatile
	var versions: A_Set = emptySet

	/**
	 * An [A_Map] from the [textual&#32;names][StringDescriptor] of entry point
	 * [methods][MethodDescriptor] to their [true&#32;names][AtomDescriptor].
	 */
	@Volatile
	var entryPoints: A_Map = emptyMap

	/**
	 * An [A_Set] of [macros][A_Macro] which implement macros defined in this
	 * module.
	 */
	@Volatile
	var macroDefinitions: A_Set = emptySet

	/**
	 * The [A_Set] of [grammatical&#32;restrictions][A_GrammaticalRestriction]
	 * defined within this module.
	 */
	@Volatile
	var grammaticalRestrictions: A_Set = emptySet

	/**
	 * The [A_Set] of [semantic&#32;restrictions][SemanticRestrictionDescriptor]
	 * defined within this module.
	 */
	@Volatile
	var semanticRestrictions: A_Set = emptySet

	/**
	 * The lock used to control access to this module's modifiable parts.
	 */
	val lock = ReentrantReadWriteLock()

	/**
	 * An enumeration describing the loading state of a module.  Modules are
	 * created in the [Loading] state, and reach the [Loaded] state when all of
	 * its statements have executed.  If the module is asked to unload, its
	 * descendants must first arrange to be unloaded, then the module state
	 * transitions to [Unloading], and ultimately [Unloaded].
	 */
	enum class State(
		privateSuccessors: ()->Set<State>)
	{
		/**
		 * The module has not yet been fully loaded, and additional changes to
		 * it are still permitted.
		 */
		Loading({ setOf(Loaded, Unloading) }),

		/**
		 * The module is fully loaded, and additional changes are not permitted.
		 */
		Loaded({ setOf(Unloading) }),

		/**
		 * The module is in the process of unloading.  Unloading activities are
		 * permitted, but new definitions are not.
		 */
		Unloading({ setOf(Unloaded) }),

		/**
		 * The module has been fully unloaded.
		 */
		Unloaded({ setOf() });

		/**
		 * The permitted successors of this state.  Lazily computed *only*
		 * because of the design choices of Java and Kotlin.
		 */
		val successors by lazy(privateSuccessors)
	}

	/** The current [state](State) of this module. */
	private val stateField = AtomicReference(Loading)

	/** Assert that the module is still open. */
	private fun assertState(expectedState: State)
	{
		if (stateField.get() != expectedState)
		{
			throw AvailRuntimeException(E_MODULE_IS_CLOSED)  //TODO too specific.
		}
	}

	/**
	 * When set to non-[nil], this is the [A_Tuple] of objects that were
	 * serialized or deserialized for the body of this module.
	 */
	@Volatile
	var serializedObjects: A_Tuple = nil

	/**
	 * When set to non-[nil], this is the [A_Map] of objects that were
	 * serialized or deserialized for the body of this module.  The values are
	 * the one-based indices of the objects.
	 */
	@Volatile
	var serializedObjectsMap: A_Map = nil

	/**
	 * A map of the negative offsets of the ancestor modules'
	 * [serializedObjects].  The ancestors should first be ordered in a stable,
	 * reproducible way, and then the offsets should be assigned such that the
	 * effective spans of negative indices abut, and have the last element at
	 * index -1.
	 *
	 * To convert an object to a global negative index, find the module that
	 * it's in, locate the object's one-based index in that module's tuple of
	 * objects (using a cached map that inverts the tuple), and add the offset
	 * associated with that module.  The largest index reachable this way should
	 * be -1.
	 */
	@Volatile
	var ancestorOffsetMap: A_Map = nil

	/**
	 * The union of all ancestor filters.  If there are no ancestors, this is
	 * simply [nil].
	 */
	@Volatile
	var unionFilter: BloomFilter? = null

	/**
	 * A filter to detect uses of objects defined in this module.  A miss of the
	 * filter always indicates the object was not defined in this module, but
	 * the filter can sometimes indicate a hit even when the object is not
	 * defined in this module.
	 *
	 * This filter is lazily constructed only when needed.
	 */
	@Volatile
	var localFilter: BloomFilter? = null

	/**
	 * The union of [unionFilter] and [localFilter].  This is created lazily.
	 */
	@Volatile
	var aggregateFilter: BloomFilter? = null

	override fun allowsImmutableToMutableReferenceInField(
		e: AbstractSlotsEnum
	): Boolean = true

	override fun printObjectOnAvoidingIndent(
		self: AvailObject,
		builder: StringBuilder,
		recursionMap: IdentityHashMap<A_BasicObject, Void>,
		indent: Int)
	{
		builder.append("module ")
		builder.append(self.moduleName())
	}

	override fun o_ApplyModuleHeader(
		self: AvailObject,
		loader: AvailLoader,
		moduleHeader: ModuleHeader): String?
	{
		assertState(Loading)
		val runtime = AvailRuntime.currentRuntime()
		val resolver = runtime.moduleNameResolver
		versions = setFromCollection(moduleHeader.versions).makeShared()
		val newAtoms = moduleHeader.exportedNames.fold(emptySet) { set, name ->
			val trueName =
				AtomWithPropertiesSharedDescriptor.shared.createInitialized(
					name, self, nil, 0)
			self.introduceNewName(trueName)
			set.setWithElementCanDestroy(trueName, true)
		}
		self.addImportedNames(newAtoms)

		var ancestors = allAncestors.get()

		for (moduleImport in moduleHeader.importedModules)
		{
			val ref: ResolvedModuleName
			try
			{
				ref = resolver.resolve(
					moduleHeader.moduleName.asSibling(
						moduleImport.moduleName.asNativeString()))
			}
			catch (e: UnresolvedDependencyException)
			{
				assert(false) { "This never happens" }
				throw RuntimeException(e)
			}

			val availRef = stringFrom(ref.qualifiedName)
			if (!runtime.includesModuleNamed(availRef))
			{
				return "module \"${ref.qualifiedName}\" to be loaded already"
			}

			val mod = runtime.moduleAt(availRef)
			val reqVersions = moduleImport.acceptableVersions
			if (reqVersions.setSize() > 0)
			{
				val modVersions = mod.versions()
				if (!modVersions.setIntersects(reqVersions))
				{
					return (
						"version compatibility; module "
							+ "\"${ref.localName}\" guarantees versions "
							+ "$modVersions but the current module requires "
							+ "$reqVersions")
				}
			}
			ancestors = ancestors.setUnionCanDestroy(mod.allAncestors(), true)
			ancestors = ancestors.setWithElementCanDestroy(mod, true)

			// Figure out which strings to make available.
			var stringsToImport: A_Set
			val importedNamesMultimap = mod.importedNames()
			if (moduleImport.wildcard)
			{
				val renameSourceNames =
					moduleImport.renames.valuesAsTuple().asSet()
				stringsToImport = importedNamesMultimap.keysAsSet()
				stringsToImport = stringsToImport.setMinusCanDestroy(
					renameSourceNames, true)
				stringsToImport = stringsToImport.setUnionCanDestroy(
					moduleImport.names, true)
				stringsToImport = stringsToImport.setMinusCanDestroy(
					moduleImport.excludes, true)
			}
			else
			{
				stringsToImport = moduleImport.names
			}

			// Look up the strings to get existing atoms.  Don't complain
			// about ambiguity, just export all that match.
			var atomsToImport = emptySet
			for (string in stringsToImport)
			{
				if (!importedNamesMultimap.hasKey(string))
				{
					return (
						"module \"${ref.qualifiedName}\" to export $string")
				}
				atomsToImport = atomsToImport.setUnionCanDestroy(
					importedNamesMultimap.mapAt(string), true)
			}

			// Perform renames.
			for ((newString, oldString) in moduleImport.renames.mapIterable())
			{
				// Find the old atom.
				if (!importedNamesMultimap.hasKey(oldString))
				{
					return (
						"module \"${ref.qualifiedName}\" to export "
							+ "$oldString for renaming to $newString")
				}
				val oldCandidates = importedNamesMultimap.mapAt(oldString)
				if (oldCandidates.setSize() != 1)
				{
					return (
						"module \"${ref.qualifiedName}\" to export a unique "
							+ "name $oldString for renaming to $newString")
				}
				val oldAtom = oldCandidates.single()
				// Find or create the new atom.
				val newAtom: A_Atom
				val newNames = self.newNames()
				if (newNames.hasKey(newString))
				{
					// Use it.  It must have been declared in the
					// "Names" clause.
					newAtom = self.newNames().mapAt(newString)
				}
				else
				{
					// Create it.
					newAtom = AtomWithPropertiesSharedDescriptor.shared
						.createInitialized(newString, self, nil, 0)
					self.introduceNewName(newAtom)
				}
				// Now tie the bundles together.
				assert(newAtom.bundleOrNil().isNil)
				val newBundle: A_Bundle
				try
				{
					val oldBundle = oldAtom.bundleOrCreate()
					val method = oldBundle.bundleMethod()
					newBundle =
						newBundle(newAtom, method, MessageSplitter(newString))
					newAtom.setAtomBundle(newBundle)
					atomsToImport =
						atomsToImport.setWithElementCanDestroy(newAtom, true)
					val copyMacros =
						!oldBundle.messageSplitter().recursivelyContainsReorders
							&& !newBundle.messageSplitter()
							.recursivelyContainsReorders
					if (copyMacros)
					{
						// Neither bundle uses reordering.  Copy all macros.
						for (macro in oldBundle.macrosTuple())
						{
							loader.addMacroBody(
								newAtom,
								macro.bodyBlock(),
								macro.prefixFunctions(),
								true)
						}
					}
				}
				catch (e: MalformedMessageException)
				{
					return (
						"well-formed signature for $newString, a rename of "
							+ "$oldString from \"${ref.qualifiedName}\"")
				}
			}

			// Actually make the atoms available in this module.
			if (moduleImport.isExtension)
			{
				self.addImportedNames(atomsToImport)
			}
			else
			{
				self.addPrivateNames(atomsToImport)
			}
		}

		allAncestors.set(ancestors.makeShared())

		moduleHeader.entryPoints.forEach { name ->
			assert(name.isString)
			try
			{
				val trueNames = self.trueNamesForStringName(name)
				val trueName: AvailObject
				when (trueNames.setSize())
				{
					0 ->
					{
						trueName = AtomWithPropertiesSharedDescriptor.shared
							.createInitialized(name, self, nil, 0)
						self.addPrivateName(trueName)
					}
					1 ->
					{
						// Just validate the name.
						MessageSplitter(name)
						trueName = trueNames.single()
					}
					else -> return (
						"entry point $name to be unambiguous")
				}
				entryPoints = entryPoints.mapAtPuttingCanDestroy(
					name, trueName, true
				).makeShared()
			}
			catch (e: MalformedMessageException)
			{
				return "entry point $name to be a valid name"
			}
		}
		return null
	}

	/**
	 * {@inheritDoc}
	 *
	 * Show the types of local variables and outer variables.
	 */
	override fun o_DescribeForDebugger(
		self: AvailObject
	): Array<AvailObjectFieldHelper> {
		val fields = mutableListOf(*super.o_DescribeForDebugger(self))
		fields.add(
			0,
			AvailObjectFieldHelper(
				self,
				DebuggerObjectSlots("Descriptor"),
				-1,
				this@ModuleDescriptor))
		return fields.toTypedArray()
	}

	override fun o_NameForDebugger(self: AvailObject): String =
		(super.o_NameForDebugger(self) + " = "
			+ self.moduleName().asNativeString())

	override fun o_ModuleName(self: AvailObject): A_String = moduleName

	override fun o_Versions(self: AvailObject): A_Set =
		lock.read { versions }

	override fun o_NewNames(self: AvailObject): A_Map =
		lock.read { self.slot(NEW_NAMES) }

	override fun o_ImportedNames(self: AvailObject): A_Map =
		lock.read { self.slot(IMPORTED_NAMES) }

	override fun o_PrivateNames(self: AvailObject): A_Map =
		lock.read { self.slot(PRIVATE_NAMES) }

	override fun o_EntryPoints(self: AvailObject): A_Map = entryPoints

	override fun o_VisibleNames(self: AvailObject): A_Set =
		lock.read { self.slot(VISIBLE_NAMES) }

	override fun o_ExportedNames(self: AvailObject): A_Set
	{
		lock.read {
			self.slot(CACHED_EXPORTED_NAMES).let {
				if (it.notNil) return it
			}
		}
		return lock.safeWrite {
			var exportedNames: A_Set = self.slot(CACHED_EXPORTED_NAMES)
			if (exportedNames.isNil)
			{
				// Compute it.
				exportedNames = emptySet
				self.slot(IMPORTED_NAMES).forEach { _, value ->
					exportedNames = exportedNames.setUnionCanDestroy(
						value.makeShared(), true)
				}
				self.slot(PRIVATE_NAMES).forEach { _, value ->
					exportedNames = exportedNames.setMinusCanDestroy(
						value.makeShared(), true)
				}
				exportedNames = exportedNames.makeShared()
				if (self.moduleState() != Loading)
				{
					// The module is closed, so cache it for next time.
					self.setSlot(CACHED_EXPORTED_NAMES, exportedNames)
				}
			}
			exportedNames
		}
	}

	override fun o_MethodDefinitions(self: AvailObject): A_Set =
		lock.read { self.slot(METHOD_DEFINITIONS_SET) }

	override fun o_VariableBindings(self: AvailObject): A_Map =
		lock.read { self.slot(VARIABLE_BINDINGS) }

	override fun o_ConstantBindings(self: AvailObject): A_Map =
		lock.read { self.slot(CONSTANT_BINDINGS) }

	override fun o_AddConstantBinding(
		self: AvailObject,
		name: A_String,
		constantBinding: A_Variable
	) = lock.safeWrite {
		assert(constantBinding.kind().isSubtypeOf(mostGeneralVariableType()))
		assertState(Loading)
		self.updateSlotShared(CONSTANT_BINDINGS) {
			mapAtPuttingCanDestroy(name, constantBinding, true)
		}
	}

	override fun o_ModuleAddDefinition(
		self: AvailObject,
		definition: A_Definition
	) = lock.safeWrite {
		assertState(Loading)
		self.updateSlotShared(METHOD_DEFINITIONS_SET) {
			setWithElementCanDestroy(definition, false)
		}
	}

	override fun o_ModuleAddMacro(
		self: AvailObject,
		macro: A_Macro
	) = lock.safeWrite {
		assertState(Loading)
		macroDefinitions =
			macroDefinitions.setWithElementCanDestroy(macro, false).makeShared()
	}

	override fun o_AddSeal(
		self: AvailObject,
		methodName: A_Atom,
		argumentTypes: A_Tuple
	) = lock.safeWrite {
		assertState(Loading)
		self.updateSlotShared(SEALS) {
			var tuple: A_Tuple = when
			{
				hasKey(methodName) -> mapAt(methodName)
				else -> emptyTuple
			}
			tuple = tuple.appendCanDestroy(argumentTypes, true)
			mapAtPuttingCanDestroy(methodName, tuple, true)
		}
	}

	override fun o_ModuleAddSemanticRestriction(
		self: AvailObject,
		semanticRestriction: A_SemanticRestriction
	) = lock.safeWrite {
		assertState(Loading)
		semanticRestrictions = semanticRestrictions.setWithElementCanDestroy(
			semanticRestriction, true
		).makeShared()
	}

	override fun o_AddVariableBinding(
		self: AvailObject,
		name: A_String,
		variableBinding: A_Variable
	) = lock.safeWrite {
		assert(variableBinding.kind().isSubtypeOf(mostGeneralVariableType()))
		assertState(Loading)
		self.updateSlotShared(VARIABLE_BINDINGS) {
			mapAtPuttingCanDestroy(name, variableBinding, true)
		}
	}

	override fun o_AddImportedName(
		self: AvailObject,
		trueName: A_Atom
	) = lock.safeWrite {
		// Add the atom to the current public scope.
		assertState(Loading)
		val string: A_String = trueName.atomName()
		self.updateSlotShared(IMPORTED_NAMES) {
			mapAtReplacingCanDestroy(
				string,
				emptySet,
				{ _, set: A_Set ->
					set.setWithElementCanDestroy(trueName, true)
				},
				true)
		}
		var privateNames: A_Map = self.slot(PRIVATE_NAMES)
		if (privateNames.hasKey(string)
			&& privateNames.mapAt(string).hasElement(trueName))
		{
			// Inclusion has priority over exclusion, even along a
			// different chain of modules.
			val set: A_Set = privateNames.mapAt(string)
			privateNames = if (set.setSize() == 1)
			{
				privateNames.mapWithoutKeyCanDestroy(string, true)
			}
			else
			{
				privateNames.mapAtPuttingCanDestroy(
					string,
					set.setWithoutElementCanDestroy(trueName, true),
					true)
			}
			self.setSlot(PRIVATE_NAMES, privateNames.makeShared())
		}
		self.updateSlotShared(VISIBLE_NAMES) {
			setWithElementCanDestroy(trueName, true)
		}
	}

	override fun o_AddImportedNames(
		self: AvailObject,
		trueNames: A_Set
	) = lock.safeWrite {
		// Add the set of atoms to the current public scope.
		assertState(Loading)
		var importedNames: A_Map = self.slot(IMPORTED_NAMES)
		var privateNames: A_Map = self.slot(PRIVATE_NAMES)
		for (trueName in trueNames)
		{
			val string: A_String = trueName.atomName()
			importedNames = importedNames.mapAtReplacingCanDestroy(
				string,
				emptySet,
				{ _, set: A_Set ->
					set.setWithElementCanDestroy(trueName, true)
				},
				true)
			if (privateNames.hasKey(string)
				&& privateNames.mapAt(string).hasElement(trueName))
			{
				// Inclusion has priority over exclusion, even along a
				// different chain of modules.
				val set: A_Set = privateNames.mapAt(string)
				privateNames = if (set.setSize() == 1)
				{
					privateNames.mapWithoutKeyCanDestroy(string, true)
				}
				else
				{
					privateNames.mapAtPuttingCanDestroy(
						string,
						set.setWithoutElementCanDestroy(trueName, true),
						true)
				}
			}
		}
		self.setSlot(IMPORTED_NAMES, importedNames.makeShared())
		self.setSlot(PRIVATE_NAMES, privateNames.makeShared())
		self.updateSlotShared(VISIBLE_NAMES) {
			setUnionCanDestroy(trueNames, true)
		}
	}

	override fun o_IntroduceNewName(
		self: AvailObject,
		trueName: A_Atom
	) = lock.safeWrite {
		// Set up this true name, which is local to the module.
		assertState(Loading)
		val string: A_String = trueName.atomName()
		self.updateSlotShared(NEW_NAMES) {
			assert(!hasKey(string)) {
				"Can't define a new true name twice in a module"
			}
			mapAtPuttingCanDestroy(string, trueName, true)
		}
		self.updateSlotShared(VISIBLE_NAMES) {
			setWithElementCanDestroy(trueName, true)
		}
	}

	override fun o_AddPrivateName(
		self: AvailObject,
		trueName: A_Atom
	) = lock.safeWrite {
		// Add the atom to the current private scope.
		assertState(Loading)
		val string: A_String = trueName.atomName()
		self.updateSlotShared(PRIVATE_NAMES) {
			mapAtReplacingCanDestroy(
				string,
				emptySet,
				{ _, set: A_Set ->
					set.setWithElementCanDestroy(trueName, true)
				},
				true)
		}
		self.updateSlotShared(VISIBLE_NAMES) {
			setWithElementCanDestroy(trueName, true)
		}
	}

	override fun o_AddPrivateNames(
		self: AvailObject,
		trueNames: A_Set
	) = lock.safeWrite {
		// Add the set of atoms to the current private scope.
		assertState(Loading)
		var privateNames: A_Map = self.slot(PRIVATE_NAMES)
		var visibleNames: A_Set = self.slot(VISIBLE_NAMES)
		for (trueName in trueNames)
		{
			val string: A_String = trueName.atomName()
			privateNames = privateNames.mapAtReplacingCanDestroy(
				string,
				emptySet,
				{ _, set: A_Set ->
					set.setWithElementCanDestroy(trueName, true)
				},
				true)
			visibleNames = visibleNames.setWithElementCanDestroy(trueName, true)
		}
		self.setSlot(PRIVATE_NAMES, privateNames.makeShared())
		self.setSlot(VISIBLE_NAMES, visibleNames.makeShared())
	}

	override fun o_AddLexer(
		self: AvailObject,
		lexer: A_Lexer
	) = lock.safeWrite {
		// Only call this when the module is compiling or loading.
		assertState(Loading)
		self.updateSlotShared(LEXERS) {
			setWithElementCanDestroy(lexer, false)
		}
	}

	override fun o_AddUnloadFunction(
		self: AvailObject,
		unloadFunction: A_Function
	) = lock.safeWrite {
		assertState(Loading)
		self.updateSlotShared(UNLOAD_FUNCTIONS) {
			appendCanDestroy(unloadFunction, true)
		}
	}

	override fun o_Equals(self: AvailObject, another: A_BasicObject): Boolean =
		another.traversed().sameAddressAs(self)

	override fun o_Hash(self: AvailObject): Int =
		moduleName.hash() * multiplier xor -0x20c7c074

	override fun o_Kind(self: AvailObject): A_Type = Types.MODULE.o

	// Modules are always shared, never immutable.
	override fun o_MakeImmutable(self: AvailObject): AvailObject =
		when {
			isMutable -> self.makeShared()
			else -> self
		}

	override fun o_ModuleAddGrammaticalRestriction(
		self: AvailObject,
		grammaticalRestriction: A_GrammaticalRestriction
	) = lock.safeWrite {
		assertState(Loading)
		grammaticalRestrictions =
			grammaticalRestrictions.setWithElementCanDestroy(
				grammaticalRestriction, true
			).makeShared()
	}

	override fun o_OriginatingPhraseAtIndex(
		self: AvailObject,
		index: Int
	): A_Phrase
	{
		var phrases = self.volatileSlot(ALL_BLOCK_PHRASES)
		if (phrases.isLong)
		{
			val phrasesKey = phrases.extractLong
			val runtime = AvailRuntime.currentRuntime()
			val moduleName = ModuleName(self.moduleName().asNativeString())
			val resolved = runtime.moduleNameResolver.resolve(moduleName, null)
			val record = resolved.repository.run {
				reopenIfNecessary()
				lock.withLock { repository!![phrasesKey] }
			}
			val bytes = validatedBytesFrom(record)
			val delta = serializedObjects.tupleSize() + 1
			val deserializer = Deserializer(bytes, runtime) {
				serializedObjects.tupleAt(it - delta)
			}
			deserializer.currentModule = self
			phrases = deserializer.deserialize()!!.makeShared()
			assert(phrases.isTuple)
			self.setVolatileSlot(ALL_BLOCK_PHRASES, phrases)
			assert(deserializer.deserialize() === null)
		}
		return phrases.tupleAt(index)
	}

	override fun o_RecordBlockPhrase(
		self: AvailObject,
		blockPhrase: A_Phrase
	): A_Number = lock.safeWrite {
		assertState(Loading)
		val newTuple = self.atomicUpdateSlot(ALL_BLOCK_PHRASES) {
			assert(isTuple)
			appendCanDestroy(blockPhrase, false)
		}
		fromInt(newTuple.tupleSize())
	}

	override fun o_RemoveFrom(
		self: AvailObject,
		loader: AvailLoader,
		afterRemoval: () -> Unit
	) = lock.safeWrite {
		self.setModuleState(Unloading)
		val unloadFunctions =
			self.getAndSetVolatileSlot(UNLOAD_FUNCTIONS, nil).tupleReverse()
		// Run unload functions, asynchronously but serially, in reverse
		// order.
		loader.runUnloadFunctions(unloadFunctions) {
			finishUnloading(self, loader)
			// The module may already be closed, but ensure that it is closed
			// following removal.
			self.setModuleState(Unloaded)
			afterRemoval()
		}
	}

	override fun o_SerializedObjects(
		self: AvailObject,
		serializedObjects: A_Tuple)
	{
		this.serializedObjects = serializedObjects.makeShared()
	}

	override fun o_SerializedObjectsMap(
		self: AvailObject,
		serializedObjectsMap: A_Map)
	{
		this.serializedObjectsMap = serializedObjectsMap.makeShared()
	}

	/**
	 * Now that the unload functions have completed, perform any other unloading
	 * actions necessary for this module.
	 *
	 * @param self
	 *   The module being unloaded.
	 * @param loader
	 *   The [AvailLoader] through which the module is being unloaded.
	 */
	@Synchronized
	private fun finishUnloading(self: AvailObject, loader: AvailLoader)
	{
		val runtime = loader.runtime()
		// Remove method definitions.
		self.methodDefinitions().forEach { loader.removeDefinition(it) }
		macroDefinitions.forEach { loader.removeMacro(it) }
		// Remove semantic restrictions.
		semanticRestrictions.forEach { runtime.removeTypeRestriction(it) }
		grammaticalRestrictions.forEach {
			runtime.removeGrammaticalRestriction(it)
		}
		// Remove seals.
		self.slot(SEALS).forEach { methodName, values ->
			values.forEach { seal ->
				try
				{
					runtime.removeSeal(methodName, seal)
				}
				catch (e: MalformedMessageException)
				{
					assert(false) { "This should not happen!" }
					throw AvailRuntimeException(e.errorCode)
				}
			}
		}
		// Remove lexers.  Don't bother adjusting the loader, since it's not
		// going to parse anything again.  Don't even bother removing it from
		// the module, since that's being unloaded.
		self.slot(LEXERS).forEach { it.lexerMethod().setLexer(nil) }
		// Remove bundles created by this module.
		self.slot(BUNDLES).forEach { bundle ->
			// Remove the bundle from the atom.
			bundle.message().setAtomBundle(nil)
			// Remove the bundle from the method.
			bundle.bundleMethod().methodRemoveBundle(bundle)
		}
		// Tidy up the module to make it easier for the garbage collector to
		// clean things up piecemeal.
		allAncestors.set(nil)
		macroDefinitions = nil
		grammaticalRestrictions = nil
		semanticRestrictions = nil
		serializedObjects = nil
		serializedObjectsMap = nil
		ancestorOffsetMap = nil
		unionFilter = null
		localFilter = null
		aggregateFilter = null
		self.setSlot(NEW_NAMES, nil)
		self.setSlot(IMPORTED_NAMES, nil)
		self.setSlot(PRIVATE_NAMES, nil)
		self.setSlot(VISIBLE_NAMES, nil)
		self.setSlot(CACHED_EXPORTED_NAMES, nil)
		self.setSlot(BUNDLES, nil)
		self.setSlot(METHOD_DEFINITIONS_SET, nil)
		self.setSlot(VARIABLE_BINDINGS, nil)
		self.setSlot(CONSTANT_BINDINGS, nil)
		self.setSlot(SEALS, nil)
		self.setSlot(UNLOAD_FUNCTIONS, nil)
		self.setSlot(LEXERS, nil)
		self.setSlot(ALL_BLOCK_PHRASES, nil)
		self.setSlot(ALL_TOP_PHRASE_STYLES, nil)
	}

	/**
	 * The interpreter is in the process of resolving this forward declaration.
	 * Record the fact that this definition no longer needs to be cleaned up
	 * if the rest of the module compilation fails.
	 *
	 * @param self
	 *   The module.
	 * @param forwardDefinition
	 *   The [forward&#32;declaration][ForwardDefinitionDescriptor] to be
	 *   removed.
	 */
	override fun o_ResolveForward(
		self: AvailObject,
		forwardDefinition: A_BasicObject
	) = lock.safeWrite {
		// Asserted because only the compiler should do this directly, and
		// never for a closed module.
		assertState(Loading)
		assert(forwardDefinition.isInstanceOfKind(Types.FORWARD_DEFINITION.o))
		self.updateSlotShared(METHOD_DEFINITIONS_SET) {
			assert(hasElement(forwardDefinition))
			setWithoutElementCanDestroy(forwardDefinition, false)
		}
	}

	override fun o_ShowValueInNameForDebugger(self: AvailObject) = false

	/**
	 * Check what true names are visible in this module under the given string
	 * name.
	 *
	 * @param self
	 *   The module.
	 * @param stringName
	 *   A string whose corresponding [true&#32;names][AtomDescriptor] are to be
	 *   looked up in this module.
	 * @return
	 *   The [set][SetDescriptor] of [true&#32;names][AtomDescriptor] that have
	 *   the given stringName and are visible in this module.
	 */
	override fun o_TrueNamesForStringName(
		self: AvailObject,
		stringName: A_String
	): A_Set
	{
		lock.read {
			self.slot(NEW_NAMES).let { newNames ->
				if (newNames.hasKey(stringName))
				{
					return singletonSet(self.slot(NEW_NAMES).mapAt(stringName))
				}
			}
		}
		lock.safeWrite {
			self.slot(NEW_NAMES).let { newNames ->
				if (newNames.hasKey(stringName))
				{
					return singletonSet(self.slot(NEW_NAMES).mapAt(stringName))
				}
			}
			val publicNames: A_Set = self.slot(IMPORTED_NAMES).let { imported ->
				when
				{
					imported.hasKey(stringName) -> imported.mapAt(stringName)
					else -> emptySet
				}
			}
			self.slot(PRIVATE_NAMES).let { privateNames ->
				if (privateNames.hasKey(stringName))
				{
					val privates: A_Set = privateNames.mapAt(stringName)
					return when (publicNames.setSize())
					{
						0 -> privates
						else -> publicNames.setUnionCanDestroy(privates, false)
					}
				}
			}
			return publicNames
		}
	}

	/**
	 * Create a [bundle&#32;tree][MessageBundleTreeDescriptor] to have the
	 * [message&#32;bundles][MessageBundleDescriptor] that are visible in the
	 * current module.
	 *
	 * @param self
	 *   The module.
	 */
	override fun o_BuildFilteredBundleTree(self: AvailObject): A_BundleTree
	{
		val filteredBundleTree = newBundleTree(nil)
		val ancestors: A_Set = allAncestors.get()
		lock.safeWrite {
			self.visibleNames().forEach { visibleName ->
				val bundle: A_Bundle = visibleName.bundleOrNil()
				if (bundle.notNil)
				{
					bundle.definitionParsingPlans().forEach { key, plan ->
						val definitionModule = key.definitionModule()
						if (ancestors.hasElement(definitionModule)
							|| definitionModule.equals(self))
						{
							filteredBundleTree.addPlanInProgress(
								newPlanInProgress(plan, 1))
						}
					}
				}
			}
		}
		return filteredBundleTree
	}

	/**
	 * Create a [LexicalScanner] to have the [lexers][A_Lexer] that are visible
	 * in the current module.
	 *
	 * As a nicety, since the bundle name isn't actually used during lexing,
	 * and since lexers can interfere with each other, de-duplicate lexers that
	 * might be from different bundles but the same method.
	 *
	 * @param self
	 *   The module.
	 */
	override fun o_CreateLexicalScanner(self: AvailObject): LexicalScanner
	{
		val lexers = mutableSetOf<A_Lexer>()
		lock.read {
			for (visibleName in self.visibleNames())
			{
				val bundle: A_Bundle = visibleName.bundleOrNil()
				if (bundle.notNil)
				{
					val method: A_Method = bundle.bundleMethod()
					val lexer = method.lexer()
					if (lexer.notNil)
					{
						lexers.add(lexer)
					}
				}
			}
		}
		val lexicalScanner = LexicalScanner()
		lexers.forEach(lexicalScanner::addLexer)
		return lexicalScanner
	}

	override fun o_AllAncestors(self: AvailObject): A_Set = allAncestors.get()

	override fun o_ModuleState(self: AvailObject): State = stateField.get()

	override fun o_SetModuleState(self: AvailObject, newState: State)
	{
		val oldState = stateField.get()
		assert(newState in oldState.successors)
		stateField.set(newState)
	}

	override fun o_SerializerOperation(self: AvailObject): SerializerOperation =
		SerializerOperation.MODULE

	override fun o_WriteTo(self: AvailObject, writer: JSONWriter) =
		writer.writeObject {
			at("kind") { write("module") }
			at("name") { moduleName.writeTo(writer) }
			at("versions") { versions.writeTo(writer) }
			at("entry points") { entryPoints.writeTo(writer) }
		}

	override fun o_AddBundle(self: AvailObject, bundle: A_Bundle): Unit =
		self.updateSlotShared(BUNDLES) {
			setWithElementCanDestroy(bundle, true)
		}

	override fun o_Bundles(self: AvailObject): A_Set = self.slot(BUNDLES)

	override fun o_GetAndSetTupleOfBlockPhrases(
		self: AvailObject,
		newValue: AvailObject
	): AvailObject = self.getAndSetVolatileSlot(ALL_BLOCK_PHRASES, newValue)

	override fun o_HasAncestor(
		self: AvailObject,
		potentialAncestor: A_Module
	): Boolean =
		when
		{
			potentialAncestor.equals(self) -> true
			allAncestors.get().hasElement(potentialAncestor) -> true
			else -> false
		}

	@Deprecated("Not supported", ReplaceWith("newModule()"))
	override fun mutable() = unsupported

	@Deprecated("Not supported", ReplaceWith("newModule()"))
	override fun immutable() = unsupported

	@Deprecated("Not supported", ReplaceWith("newModule()"))
	override fun shared() = unsupported

	companion object
	{
		/**
		 * Construct a new empty `module`.  Pre-add the module itself to its
		 * [set][SetDescriptor] of ancestor modules.
		 *
		 * @param moduleName
		 *   The fully qualified [name][StringDescriptor] of the module.
		 * @return
		 *   The new module.
		 */
		fun newModule(moduleName: A_String): A_Module =
			initialMutableDescriptor.create {
				setSlot(NEW_NAMES, emptyMap)
				setSlot(IMPORTED_NAMES, emptyMap)
				setSlot(PRIVATE_NAMES, emptyMap)
				setSlot(VISIBLE_NAMES, emptySet)
				setSlot(CACHED_EXPORTED_NAMES, nil) // Only valid after loading.
				setSlot(BUNDLES, emptySet)
				setSlot(METHOD_DEFINITIONS_SET, emptySet)
				setSlot(VARIABLE_BINDINGS, emptyMap)
				setSlot(CONSTANT_BINDINGS, emptyMap)
				setSlot(VARIABLE_BINDINGS, emptyMap)
				setSlot(SEALS, emptyMap)
				setSlot(UNLOAD_FUNCTIONS, emptyTuple)
				setSlot(LEXERS, emptySet)
				setSlot(ALL_BLOCK_PHRASES, emptyTuple)
				setSlot(ALL_TOP_PHRASE_STYLES, emptyTuple)
				setDescriptor(ModuleDescriptor(SHARED, moduleName.makeShared()))
			}

		/**
		 * The mutable [ModuleDescriptor], used only during construction of a
		 * new [A_Module], prior to replacing it with a new shared descriptor.
		 */
		private val initialMutableDescriptor = ModuleDescriptor(
			Mutability.MUTABLE, emptyTuple)

		/**
		 * Create an empty [BloomFilter] for use as a module serialization
		 * filter.  When populated with the hashes of values that have been
		 * created or deserialized for the module, it can be used to quickly
		 * determine if an object with a particular hash value is present in the
		 * module's objects.  At that point, the module's objects can be
		 * inverted to form a map from object to index, cached in the module,
		 * and a lookup can then take place.  Due to the conservative nature of
		 * Bloom filters, sometimes the object will not be found even if the
		 * filter indicates the value might be present.
		 *
		 * Since each module has a (lazily populated) Bloom filter, and since we
		 * also keep track of the union of those filters in each non-leaf
		 * module, we're able to first test the union filter, and only if it's a
		 * hit do we need to examine the module's local filter (and the module's
		 * objects themselves if indicated), or continue searching predecessor
		 * modules.  If a union filter produces a miss, the module and its
		 * predecessors can be ignored, as they can't contain the requested
		 * object.
		 *
		 * Because we have to compute the union of modules' filters, the filter
		 * sizes and count of hash functions must agree, so only this function
		 * should be used to create such filters.
		 */
		fun newEmptyBloomFilter(): BloomFilter
		{
			return BloomFilter(20000, 5)
		}
	}
}

