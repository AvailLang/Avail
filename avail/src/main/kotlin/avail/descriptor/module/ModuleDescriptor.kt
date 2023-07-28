/*
 * ModuleDescriptor.kt
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
package avail.descriptor.module

import avail.AvailRuntime
import avail.builder.ModuleName
import avail.builder.ResolvedModuleName
import avail.builder.UnresolvedDependencyException
import avail.compiler.ModuleHeader
import avail.compiler.ModuleManifestEntry
import avail.compiler.splitter.MessageSplitter
import avail.descriptor.atoms.A_Atom
import avail.descriptor.atoms.A_Atom.Companion.atomName
import avail.descriptor.atoms.A_Atom.Companion.bundleOrCreate
import avail.descriptor.atoms.A_Atom.Companion.bundleOrNil
import avail.descriptor.atoms.A_Atom.Companion.setAtomBundle
import avail.descriptor.atoms.AtomDescriptor
import avail.descriptor.atoms.AtomWithPropertiesSharedDescriptor
import avail.descriptor.bundles.A_Bundle
import avail.descriptor.bundles.A_Bundle.Companion.bundleMethod
import avail.descriptor.bundles.A_Bundle.Companion.definitionParsingPlans
import avail.descriptor.bundles.A_Bundle.Companion.macrosTuple
import avail.descriptor.bundles.A_Bundle.Companion.message
import avail.descriptor.bundles.A_Bundle.Companion.messageSplitter
import avail.descriptor.bundles.A_BundleTree
import avail.descriptor.bundles.A_BundleTree.Companion.addPlanInProgress
import avail.descriptor.bundles.MessageBundleDescriptor
import avail.descriptor.bundles.MessageBundleDescriptor.Companion.newBundle
import avail.descriptor.bundles.MessageBundleTreeDescriptor
import avail.descriptor.bundles.MessageBundleTreeDescriptor.Companion.newBundleTree
import avail.descriptor.fiber.FiberDescriptor
import avail.descriptor.functions.A_Function
import avail.descriptor.functions.A_RawFunction
import avail.descriptor.functions.FunctionDescriptor
import avail.descriptor.maps.A_Map
import avail.descriptor.maps.A_Map.Companion.forEach
import avail.descriptor.maps.A_Map.Companion.hasKey
import avail.descriptor.maps.A_Map.Companion.keysAsSet
import avail.descriptor.maps.A_Map.Companion.mapAtOrNull
import avail.descriptor.maps.A_Map.Companion.mapAtPuttingCanDestroy
import avail.descriptor.maps.A_Map.Companion.mapAtReplacingCanDestroy
import avail.descriptor.maps.A_Map.Companion.mapIterable
import avail.descriptor.maps.A_Map.Companion.mapWithoutKeyCanDestroy
import avail.descriptor.maps.A_Map.Companion.valuesAsTuple
import avail.descriptor.maps.MapDescriptor
import avail.descriptor.maps.MapDescriptor.Companion.emptyMap
import avail.descriptor.methods.A_Definition
import avail.descriptor.methods.A_GrammaticalRestriction
import avail.descriptor.methods.A_Macro
import avail.descriptor.methods.A_Method
import avail.descriptor.methods.A_Method.Companion.lexer
import avail.descriptor.methods.A_Method.Companion.methodRemoveBundle
import avail.descriptor.methods.A_Method.Companion.updateStylers
import avail.descriptor.methods.A_SemanticRestriction
import avail.descriptor.methods.A_Sendable.Companion.bodyBlock
import avail.descriptor.methods.A_Styler
import avail.descriptor.methods.A_Styler.Companion.stylerMethod
import avail.descriptor.methods.DefinitionDescriptor
import avail.descriptor.methods.ForwardDefinitionDescriptor
import avail.descriptor.methods.MethodDescriptor
import avail.descriptor.methods.SemanticRestrictionDescriptor
import avail.descriptor.module.A_Module.Companion.addImportedNames
import avail.descriptor.module.A_Module.Companion.addPrivateName
import avail.descriptor.module.A_Module.Companion.addPrivateNames
import avail.descriptor.module.A_Module.Companion.allAncestors
import avail.descriptor.module.A_Module.Companion.importedNames
import avail.descriptor.module.A_Module.Companion.introduceNewName
import avail.descriptor.module.A_Module.Companion.methodDefinitions
import avail.descriptor.module.A_Module.Companion.moduleName
import avail.descriptor.module.A_Module.Companion.moduleState
import avail.descriptor.module.A_Module.Companion.newNames
import avail.descriptor.module.A_Module.Companion.stylers
import avail.descriptor.module.A_Module.Companion.trueNamesForStringName
import avail.descriptor.module.A_Module.Companion.versions
import avail.descriptor.module.A_Module.Companion.visibleNames
import avail.descriptor.module.ModuleDescriptor.ObjectSlots.ALL_BLOCK_PHRASES
import avail.descriptor.module.ModuleDescriptor.ObjectSlots.BUNDLES
import avail.descriptor.module.ModuleDescriptor.ObjectSlots.CACHED_EXPORTED_NAMES
import avail.descriptor.module.ModuleDescriptor.ObjectSlots.CONSTANT_BINDINGS
import avail.descriptor.module.ModuleDescriptor.ObjectSlots.IMPORTED_NAMES
import avail.descriptor.module.ModuleDescriptor.ObjectSlots.LEXERS
import avail.descriptor.module.ModuleDescriptor.ObjectSlots.METHOD_DEFINITIONS_SET
import avail.descriptor.module.ModuleDescriptor.ObjectSlots.NEW_NAMES
import avail.descriptor.module.ModuleDescriptor.ObjectSlots.PRIVATE_NAMES
import avail.descriptor.module.ModuleDescriptor.ObjectSlots.SEALS
import avail.descriptor.module.ModuleDescriptor.ObjectSlots.STYLERS
import avail.descriptor.module.ModuleDescriptor.ObjectSlots.UNLOAD_FUNCTIONS
import avail.descriptor.module.ModuleDescriptor.ObjectSlots.VARIABLE_BINDINGS
import avail.descriptor.module.ModuleDescriptor.ObjectSlots.VISIBLE_NAMES
import avail.descriptor.module.ModuleDescriptor.State.Loaded
import avail.descriptor.module.ModuleDescriptor.State.Loading
import avail.descriptor.module.ModuleDescriptor.State.Unloaded
import avail.descriptor.module.ModuleDescriptor.State.Unloading
import avail.descriptor.numbers.A_Number
import avail.descriptor.numbers.A_Number.Companion.extractLong
import avail.descriptor.numbers.A_Number.Companion.isLong
import avail.descriptor.parsing.A_Lexer
import avail.descriptor.parsing.A_Lexer.Companion.lexerMethod
import avail.descriptor.parsing.ParsingPlanInProgressDescriptor.Companion.newPlanInProgress
import avail.descriptor.phrases.A_Phrase
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.AbstractDescriptor.DebuggerObjectSlots.DUMMY_DEBUGGER_SLOT
import avail.descriptor.representation.AbstractSlotsEnum
import avail.descriptor.representation.AvailObject
import avail.descriptor.representation.AvailObject.Companion.combine2
import avail.descriptor.representation.AvailObjectFieldHelper
import avail.descriptor.representation.Descriptor
import avail.descriptor.representation.Mutability
import avail.descriptor.representation.Mutability.SHARED
import avail.descriptor.representation.NilDescriptor.Companion.nil
import avail.descriptor.representation.ObjectSlotsEnum
import avail.descriptor.sets.A_Set
import avail.descriptor.sets.A_Set.Companion.hasElement
import avail.descriptor.sets.A_Set.Companion.setIntersects
import avail.descriptor.sets.A_Set.Companion.setMinusCanDestroy
import avail.descriptor.sets.A_Set.Companion.setSize
import avail.descriptor.sets.A_Set.Companion.setUnionCanDestroy
import avail.descriptor.sets.A_Set.Companion.setWithElementCanDestroy
import avail.descriptor.sets.A_Set.Companion.setWithoutElementCanDestroy
import avail.descriptor.sets.SetDescriptor
import avail.descriptor.sets.SetDescriptor.Companion.emptySet
import avail.descriptor.sets.SetDescriptor.Companion.setFromCollection
import avail.descriptor.sets.SetDescriptor.Companion.singletonSet
import avail.descriptor.tuples.A_String
import avail.descriptor.tuples.A_String.Companion.asNativeString
import avail.descriptor.tuples.A_Tuple
import avail.descriptor.tuples.A_Tuple.Companion.appendCanDestroy
import avail.descriptor.tuples.A_Tuple.Companion.asSet
import avail.descriptor.tuples.A_Tuple.Companion.tupleAt
import avail.descriptor.tuples.A_Tuple.Companion.tupleReverse
import avail.descriptor.tuples.A_Tuple.Companion.tupleSize
import avail.descriptor.tuples.StringDescriptor
import avail.descriptor.tuples.StringDescriptor.Companion.stringFrom
import avail.descriptor.tuples.TupleDescriptor
import avail.descriptor.tuples.TupleDescriptor.Companion.emptyTuple
import avail.descriptor.types.A_Type
import avail.descriptor.types.A_Type.Companion.isSubtypeOf
import avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.PARSE_PHRASE
import avail.descriptor.types.PrimitiveTypeDescriptor.Types
import avail.descriptor.types.TypeTag
import avail.descriptor.types.VariableTypeDescriptor.Companion.mostGeneralVariableType
import avail.descriptor.variables.A_Variable
import avail.exceptions.AvailErrorCode.E_MODULE_IS_CLOSED
import avail.exceptions.AvailRuntimeException
import avail.exceptions.MalformedMessageException
import avail.interpreter.execution.AvailLoader
import avail.interpreter.execution.LexicalScanner
import avail.persistence.cache.Repository
import avail.persistence.cache.record.ManifestRecord
import avail.persistence.cache.record.NamesIndex
import avail.persistence.cache.record.NameInModule
import avail.persistence.cache.record.PhrasePathRecord
import avail.persistence.cache.record.StylingRecord
import avail.serialization.Deserializer
import avail.serialization.SerializerOperation
import avail.utility.safeWrite
import avail.utility.structures.BloomFilter
import org.availlang.json.JSONWriter
import org.availlang.persistence.IndexedFile.Companion.validatedBytesFrom
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
 * @property runtime
 *   The [AvailRuntime] that will eventually hold the module using this
 *   descriptor.
 */
class ModuleDescriptor private constructor(
	mutability: Mutability,
	val moduleName: A_String,
	val runtime: AvailRuntime?
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
		 * less the [private][PRIVATE_NAMES] names.  This is [nil] during module
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
		 * A [map][A_Map] from [strings][A_String] to module
		 * [variables][A_Variable].
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

		/** The [A_Set] of [A_Lexer]s defined by this module. */
		LEXERS,

		/** The [set][A_Set] of [stylers][A_Styler] installed by this module. */
		STYLERS,

		/**
		 * A [tuple][TupleDescriptor] of [functions][FunctionDescriptor] that
		 * should be applied when this [module][ModuleDescriptor] is unloaded.
		 */
		UNLOAD_FUNCTIONS,

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
		ALL_BLOCK_PHRASES;
	}

	/**
	 * A Kotlin [String] holding this module's name (converted from an
	 * [A_String]), to reduce the need for conversions.
	 */
	private val moduleNameNative = moduleName.asNativeString()

	/**
	 * Produce an [A_String] describing this module.  Leave off the module path.
	 */
	private val shortModuleNameNative = moduleNameNative.split("/").last()

	/**
	 * The [set][A_Set] of all ancestor modules of this module, but *not*
	 * including the module itself.  The provided set must always be [SHARED].
	 */
	private val allAncestors = AtomicReference(emptySet)

	/**
	 * The [set][A_Set] of [versions][A_String] that this module alleges to
	 * support.
	 */
	@Volatile
	private var versions: A_Set = emptySet

	/**
	 * An [A_Map] from the [textual&#32;names][StringDescriptor] of entry point
	 * [methods][MethodDescriptor] to their [true&#32;names][AtomDescriptor].
	 */
	@Volatile
	private var entryPoints: A_Map = emptyMap

	/**
	 * An [A_Set] of [macros][A_Macro] which implement macros defined in this
	 * module.
	 */
	@Volatile
	private var macroDefinitions: A_Set = emptySet

	/**
	 * The [A_Set] of [grammatical&#32;restrictions][A_GrammaticalRestriction]
	 * defined within this module.
	 */
	@Volatile
	private var grammaticalRestrictions: A_Set = emptySet

	/**
	 * The [A_Set] of [semantic&#32;restrictions][SemanticRestrictionDescriptor]
	 * defined within this module.
	 */
	@Volatile
	private var semanticRestrictions: A_Set = emptySet

	/**
	 * The repository's record number of the encoded module manifest
	 * [entries][ModuleManifestEntry], captured after the module is compiled.
	 */
	@Volatile
	private var manifestEntriesRecordIndex: Long = -1

	/**
	 * A [List] of all module manifest [entries][ModuleManifestEntry] created
	 * by compilation of this module.  Immediately after compilation, the
	 * entries are written to a DataOutputStream, converted to a [ByteArray],
	 * and written directly as a record to the repository, and the record number
	 * is written to [manifestEntriesRecordIndex].  This field remains null
	 * until requested, at which point it's cached here.
	 */
	@Volatile
	private var manifestEntries: List<ModuleManifestEntry>? = null

	/**
	 * This field is initially -1, but when a module has been compiled, the
	 * [StylingRecord] is written to the repository, and this field is set to
	 * that record number.
	 */
	@Volatile
	private var stylingRecordIndex: Long = -1

	/**
	 * The [StylingRecord] for this module, used for syntax coloring and other
	 * things.  During compilation, the styling record is written to the
	 * [Repository], setting the [stylingRecordIndex] to the record number, and
	 * this field remains null.  Any subsequent request for the styling record
	 * will look it up in the repository, and cache it here.
	 */
	@Volatile
	private var stylingRecord: StylingRecord? = null

	/**
	 * This field is initially -1, but when a module has been compiled, the
	 * [PhrasePathRecord] is written to the repository, and this field is set to
	 * that record number.
	 */
	@Volatile
	private var phrasePathRecordIndex: Long = -1

	/**
	 * The [PhrasePathRecord] for this module, used for explanation and
	 * navigation and possibly other things.  During compilation, the
	 * [PhrasePathRecord] is written to the [Repository], setting the
	 * [phrasePathRecordIndex] to the record number, and this field remains
	 * null.  Any subsequent request for the phrase path record will look it up
	 * in the repository, and cache it here.
	 */
	@Volatile
	var phrasePathRecord: PhrasePathRecord? = null

	/**
	 * The lock used to control access to this module's modifiable parts.
	 */
	private val lock = ReentrantReadWriteLock()

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
	@Suppress("SameParameterValue")
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
	private var serializedObjects: A_Tuple = nil

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
	private var ancestorOffsetMap: A_Map = nil

	/**
	 * The union of all ancestor filters.  If there are no ancestors, this is
	 * simply [nil].
	 */
	@Volatile
	private var unionFilter: BloomFilter<NameInModule>? = null

	/**
	 * A filter to detect uses of objects defined in this module.  A miss of the
	 * filter always indicates the object was not defined in this module, but
	 * the filter can sometimes indicate a hit even when the object is not
	 * defined in this module.
	 *
	 * This filter is lazily constructed only when needed.
	 */
	@Volatile
	private var localFilter: BloomFilter<NameInModule>? = null

	/**
	 * The union of [unionFilter] and [localFilter].  This is created lazily.
	 */
	@Volatile
	private var aggregateFilter: BloomFilter<NameInModule>? = null

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
		builder.append(self.moduleName)
	}

	override fun o_ApplyModuleHeader(
		self: AvailObject,
		loader: AvailLoader,
		moduleHeader: ModuleHeader): String?
	{
		assertState(Loading)
		val runtime = loader.runtime
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
			if (reqVersions.setSize > 0)
			{
				val modVersions = mod.versions
				if (!modVersions.setIntersects(reqVersions))
				{
					return (
						"version compatibility; module "
							+ "\"${ref.localName}\" guarantees versions "
							+ "$modVersions but the current module requires "
							+ "$reqVersions")
				}
			}
			ancestors = ancestors.setUnionCanDestroy(mod.allAncestors, true)
			ancestors = ancestors.setWithElementCanDestroy(mod, true)

			// Figure out which strings to make available.
			var stringsToImport: A_Set
			val importedNamesMultimap = mod.importedNames
			if (moduleImport.wildcard)
			{
				val renameSourceNames =
					moduleImport.renames.valuesAsTuple.asSet
				stringsToImport = importedNamesMultimap.keysAsSet
				stringsToImport = stringsToImport.setMinusCanDestroy(
					renameSourceNames, true)
				stringsToImport = stringsToImport.setUnionCanDestroy(
					moduleImport.names, true)
				val absentExclusions = moduleImport.excludes.setMinusCanDestroy(
					stringsToImport, false)
				if (absentExclusions.setSize > 0)
				{
					return "these excluded names to have been exported from " +
						"$availRef: $absentExclusions"
				}
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
				val names: A_Set = importedNamesMultimap.mapAtOrNull(string) ?:
					return (
						"module \"${ref.qualifiedName}\" to export $string")
				atomsToImport = atomsToImport.setUnionCanDestroy(names, true)
			}

			// Perform renames.
			for ((newString, oldString) in moduleImport.renames.mapIterable)
			{
				// Find the old atom.
				val oldCandidates: A_Set =
					importedNamesMultimap.mapAtOrNull(oldString) ?:
						return (
							"module \"${ref.qualifiedName}\" to export "
								+ "$oldString for renaming to $newString")
				if (oldCandidates.setSize != 1)
				{
					return (
						"module \"${ref.qualifiedName}\" to export a unique "
							+ "name $oldString for renaming to $newString")
				}
				val oldAtom = oldCandidates.single()
				// Find or create the new atom.
				val newAtom: A_Atom = self.newNames.mapAtOrNull(newString) ?:
					AtomWithPropertiesSharedDescriptor.shared
						.createInitialized(newString, self, nil, 0)
						.also { self.introduceNewName(it) }
				// Now tie the bundles together.
				assert(newAtom.bundleOrNil.isNil)
				val newBundle: A_Bundle
				try
				{
					val oldBundle = oldAtom.bundleOrCreate()
					val method = oldBundle.bundleMethod
					newBundle = newBundle(
						newAtom, method, MessageSplitter.split(newString))
					newAtom.setAtomBundle(newBundle)
					atomsToImport =
						atomsToImport.setWithElementCanDestroy(newAtom, true)
					val copyMacros =
						!oldBundle.messageSplitter.recursivelyContainsReorders
							&& !newBundle.messageSplitter
								.recursivelyContainsReorders
					if (copyMacros)
					{
						// Neither bundle uses reordering.  Copy all macros.
						for (macro in oldBundle.macrosTuple)
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
			when
			{
				moduleImport.isExtension -> self.addImportedNames(atomsToImport)
				else -> self.addPrivateNames(atomsToImport)
			}
		}

		allAncestors.set(ancestors.makeShared())

		moduleHeader.entryPoints.forEach { name ->
			assert(name.isString)
			try
			{
				val trueNames = self.trueNamesForStringName(name)
				val trueName: AvailObject
				when (trueNames.setSize)
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
						MessageSplitter.split(name)
						trueName = trueNames.single()
					}
					else -> return ("entry point $name to be unambiguous")
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
				DUMMY_DEBUGGER_SLOT,
				-1,
				this,
				slotName = "Descriptor"))
		return fields.toTypedArray()
	}

	override fun o_NameForDebugger(self: AvailObject): String =
		super.o_NameForDebugger(self) + " = " + moduleNameNative

	override fun o_ModuleName(self: AvailObject): A_String = moduleName

	override fun o_ShortModuleNameNative(self: AvailObject): String =
		shortModuleNameNative

	override fun o_ModuleNameNative(self: AvailObject): String =
		moduleNameNative

	override fun o_Versions(self: AvailObject): A_Set =
		lock.read { versions }

	override fun o_NewNames(self: AvailObject): A_Map =
		lock.read { self[NEW_NAMES] }

	override fun o_ImportedNames(self: AvailObject): A_Map =
		lock.read { self[IMPORTED_NAMES] }

	override fun o_PrivateNames(self: AvailObject): A_Map =
		lock.read { self[PRIVATE_NAMES] }

	override fun o_EntryPoints(self: AvailObject): A_Map = entryPoints

	override fun o_VisibleNames(self: AvailObject): A_Set =
		lock.read { self[VISIBLE_NAMES] }

	override fun o_ExportedNames(self: AvailObject): A_Set
	{
		lock.read {
			self[CACHED_EXPORTED_NAMES].let {
				if (it.notNil) return it
			}
		}
		return lock.safeWrite {
			var exportedNames: A_Set = self[CACHED_EXPORTED_NAMES]
			if (exportedNames.isNil)
			{
				// Compute it.
				exportedNames = emptySet
				self[IMPORTED_NAMES].forEach { _, value ->
					exportedNames = exportedNames.setUnionCanDestroy(
						value.makeShared(), true)
				}
				self[PRIVATE_NAMES].forEach { _, value ->
					exportedNames = exportedNames.setMinusCanDestroy(
						value.makeShared(), true)
				}
				exportedNames = exportedNames.makeShared()
				if (self.moduleState != Loading)
				{
					// The module is closed, so cache it for next time.
					self[CACHED_EXPORTED_NAMES] = exportedNames
				}
			}
			exportedNames
		}
	}

	override fun o_MethodDefinitions(self: AvailObject): A_Set =
		lock.read { self[METHOD_DEFINITIONS_SET] }

	override fun o_VariableBindings(self: AvailObject): A_Map =
		lock.read { self[VARIABLE_BINDINGS] }

	override fun o_ConstantBindings(self: AvailObject): A_Map =
		lock.read { self[CONSTANT_BINDINGS] }

	override fun o_AddConstantBinding(
		self: AvailObject,
		name: A_String,
		constantBinding: A_Variable
	) = lock.safeWrite {
		assert(constantBinding.kind().isSubtypeOf(mostGeneralVariableType))
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
			mapAtPuttingCanDestroy(
				methodName,
				(mapAtOrNull(methodName) ?: emptyTuple).appendCanDestroy(
					argumentTypes, true),
				true)
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
		assert(variableBinding.kind().isSubtypeOf(mostGeneralVariableType))
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
		val string: A_String = trueName.atomName
		self.updateSlotShared(IMPORTED_NAMES) {
			mapAtReplacingCanDestroy(string, emptySet, true) { _, set: A_Set ->
				set.setWithElementCanDestroy(trueName, true)
			}
		}
		var privateNames: A_Map = self[PRIVATE_NAMES]
		val set: A_Set? = privateNames.mapAtOrNull(string)
		if (set !== null && set.hasElement(trueName))
		{
			// Inclusion has priority over exclusion, even along a
			// different chain of modules.
			privateNames = if (set.setSize == 1)
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
			self[PRIVATE_NAMES] = privateNames.makeShared()
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
		var importedNames: A_Map = self[IMPORTED_NAMES]
		var privateNames: A_Map = self[PRIVATE_NAMES]
		for (trueName in trueNames)
		{
			val string: A_String = trueName.atomName
			importedNames = importedNames.mapAtReplacingCanDestroy(
				string, emptySet, true
			) { _, set: A_Set ->
				set.setWithElementCanDestroy(trueName, true)
			}
			val set: A_Set? = privateNames.mapAtOrNull(string)
			if (set !== null && set.hasElement(trueName))
			{
				// Inclusion has priority over exclusion, even along a
				// different chain of modules.
				privateNames = if (set.setSize == 1)
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
		self[IMPORTED_NAMES] = importedNames.makeShared()
		self[PRIVATE_NAMES] = privateNames.makeShared()
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
		val string: A_String = trueName.atomName
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
		val string: A_String = trueName.atomName
		self.updateSlotShared(PRIVATE_NAMES) {
			mapAtReplacingCanDestroy(string, emptySet, true) { _, set: A_Set ->
				set.setWithElementCanDestroy(trueName, true)
			}
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
		var privateNames: A_Map = self[PRIVATE_NAMES]
		var visibleNames: A_Set = self[VISIBLE_NAMES]
		visibleNames = visibleNames.setUnionCanDestroy(trueNames, true)
		for (trueName in trueNames)
		{
			val string: A_String = trueName.atomName
			privateNames = privateNames.mapAtReplacingCanDestroy(
				string, emptySet, true
			) { _, set: A_Set ->
				set.setWithElementCanDestroy(trueName, true)
			}
		}
		self[PRIVATE_NAMES] = privateNames.makeShared()
		self[VISIBLE_NAMES] = visibleNames.makeShared()
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
		combine2(moduleName.hash(), -0x20c7c074)

	override fun o_Kind(self: AvailObject): A_Type = Types.MODULE.o

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
			if (serializedObjects.isNil)
			{
				// The module is in the process of being loaded.  The phrase
				// serialization has to be pumped by the complete tuple of
				// serializedObjects, because it must be allowed to refer to
				// module constants and variables in an accurate way.  However,
				// we can't have the complete tuple of serializedObjects yet,
				// because we're in the process of loading them!  Answer nil to
				// indicate the phrase is temporarily unavailable.
				return nil
			}
			val phrasesKey = phrases.extractLong
			val moduleName = ModuleName(moduleNameNative)
			val resolved =
				runtime!!.moduleNameResolver.resolve(moduleName, null)
			val record = resolved.repository.run {
				reopenIfNecessary()
				lock.withLock { this[phrasesKey] }
			}
			val bytes = validatedBytesFrom(record)
			val delta = serializedObjects.tupleSize + 1
			val deserializer = Deserializer(bytes, runtime) {
				serializedObjects.tupleAt(it + delta)
			}
			deserializer.currentModule = self
			phrases = deserializer.deserialize()!!.makeShared()
			assert(phrases.isTuple)
			self.setVolatileSlot(ALL_BLOCK_PHRASES, phrases)
			assert(deserializer.deserialize() === null)
		}
		return phrases.tupleAt(index)
	}

	override fun o_SetManifestEntriesIndex(
		self: AvailObject,
		recordNumber: Long)
	{
		manifestEntriesRecordIndex = recordNumber
	}

	override fun o_ManifestEntries(
		self: AvailObject
	): List<ModuleManifestEntry>
	{
		if (manifestEntries === null)
		{
			val moduleName = ModuleName(moduleNameNative)
			val resolved =
				runtime!!.moduleNameResolver.resolve(moduleName, null)
			val bytes = resolved.repository.run {
				reopenIfNecessary()
				lock.withLock { this[manifestEntriesRecordIndex] }
			}
			val record = ManifestRecord(bytes)
			manifestEntries = record.manifestEntries
		}
		return manifestEntries!!
	}

	override fun o_RecordBlockPhrase(
		self: AvailObject,
		blockPhrase: A_Phrase
	): Int = lock.safeWrite {
		assertState(Loading)
		assert(blockPhrase.isInstanceOfKind(PARSE_PHRASE.mostGeneralType))
		val newTuple = self.atomicUpdateSlot(ALL_BLOCK_PHRASES) {
			assert(isTuple)
			appendCanDestroy(blockPhrase.makeShared(), false)
		}
		newTuple.tupleSize
	}

	override fun o_SetStylingRecordIndex(self: AvailObject, recordNumber: Long)
	{
		stylingRecordIndex = recordNumber
	}

	override fun o_StylingRecord(self: AvailObject): StylingRecord
	{
		stylingRecord?.let { return it }
		val moduleName = ModuleName(moduleNameNative)
		val resolved = runtime!!.moduleNameResolver.resolve(moduleName, null)
		stylingRecord = if (stylingRecordIndex == -1L)
		{
			StylingRecord(emptyList(), emptyList())
		}
		else
		{
			val bytes = resolved.repository.run {
				reopenIfNecessary()
				lock.withLock { this[stylingRecordIndex] }
			}
			StylingRecord(bytes)
		}
		return stylingRecord!!
	}

	override fun o_SetPhrasePathRecordIndex(
		self: AvailObject,
		recordNumber: Long)
	{
		phrasePathRecordIndex = recordNumber
	}

	override fun o_PhrasePathRecord(self: AvailObject): PhrasePathRecord
	{
		phrasePathRecord?.let { return it }
		val moduleName = ModuleName(moduleNameNative)
		val resolved = runtime!!.moduleNameResolver.resolve(moduleName, null)
		phrasePathRecord = if (phrasePathRecordIndex == -1L)
		{
			PhrasePathRecord()
		}
		else
		{
			val bytes = resolved.repository.run {
				reopenIfNecessary()
				lock.withLock { this[phrasePathRecordIndex] }
			}
			PhrasePathRecord(bytes)
		}
		return phrasePathRecord!!
	}

	override fun o_RemoveFrom(
		self: AvailObject,
		loader: AvailLoader,
		afterRemoval: () -> Unit
	) = lock.safeWrite {
		self.moduleState = Unloading
		val unloadFunctions =
			self.getAndSetVolatileSlot(UNLOAD_FUNCTIONS, nil).tupleReverse()
		// Run unload functions, asynchronously but serially, in reverse
		// order.
		loader.runUnloadFunctions(unloadFunctions) {
			// The final cleanup for the module has to happen in a safe point,
			// because it causes chunk invalidations.
			loader.runtime.whenSafePointDo(FiberDescriptor.loaderPriority) {
				finishUnloading(self, loader)
				// The module may already be closed, but ensure that it is
				// closed following removal.
				self.moduleState = Unloaded
				// Run the post-action outside of the safe point.
				loader.runtime.execute(
					FiberDescriptor.loaderPriority, afterRemoval)
			}
		}
	}

	override fun o_SerializedObjects(
		self: AvailObject,
		serializedObjects: A_Tuple)
	{
		this.serializedObjects = serializedObjects.makeShared()
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
		val runtime = loader.runtime
		// Remove stylers.
		(self as A_Module).stylers.forEach { styler ->
			styler.stylerMethod.updateStylers {
				setWithoutElementCanDestroy(styler, true)
			}
		}
		// Remove method definitions.
		self.methodDefinitions.forEach(loader::removeDefinition)
		macroDefinitions.forEach(loader::removeMacro)
		// Remove semantic restrictions.
		semanticRestrictions.forEach(runtime::removeSemanticRestriction)
		grammaticalRestrictions.forEach(runtime::removeGrammaticalRestriction)
		// Remove seals.
		self[SEALS].forEach { methodName, values ->
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
		self[LEXERS].forEach { lexer ->
			lexer.lexerMethod.lexer = nil
		}
		// Remove bundles created by this module.
		self[BUNDLES].forEach { bundle ->
			// Remove the bundle from the atom.
			bundle.message.setAtomBundle(nil)
			// Remove the bundle from the method.
			bundle.bundleMethod.methodRemoveBundle(bundle)
		}
		// Tidy up the module to make it easier for the garbage collector to
		// clean things up piecemeal.
		allAncestors.set(nil)
		macroDefinitions = nil
		grammaticalRestrictions = nil
		semanticRestrictions = nil
//		serializedObjects = nil
		ancestorOffsetMap = nil
		unionFilter = null
		localFilter = null
		aggregateFilter = null
		self.run {
			setSlot(NEW_NAMES, nil)
			setSlot(IMPORTED_NAMES, nil)
			setSlot(PRIVATE_NAMES, nil)
			setSlot(VISIBLE_NAMES, nil)
			setSlot(CACHED_EXPORTED_NAMES, nil)
			setSlot(BUNDLES, nil)
			setSlot(METHOD_DEFINITIONS_SET, nil)
			setSlot(VARIABLE_BINDINGS, nil)
			setSlot(CONSTANT_BINDINGS, nil)
			setSlot(SEALS, nil)
			setSlot(UNLOAD_FUNCTIONS, nil)
			setSlot(LEXERS, nil)
			setSlot(STYLERS, nil)
		}
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
			self[NEW_NAMES].let { newNames ->
				newNames.mapAtOrNull(stringName)?.let {
					return singletonSet(it)
				}
			}
		}
		lock.safeWrite {
			self[NEW_NAMES].let { newNames ->
				newNames.mapAtOrNull(stringName)?.let {
					return singletonSet(it)
				}
			}
			val publicNames: A_Set =
				self[IMPORTED_NAMES].mapAtOrNull(stringName) ?: emptySet
			self[PRIVATE_NAMES].mapAtOrNull(stringName)?.let { privates ->
				return when (publicNames.setSize)
				{
					0 -> privates
					else -> publicNames.setUnionCanDestroy(privates, false)
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
			self.visibleNames.forEach { visibleName ->
				val bundle: A_Bundle = visibleName.bundleOrNil
				if (bundle.notNil)
				{
					bundle.definitionParsingPlans.forEach { key, plan ->
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
			for (visibleName in self.visibleNames)
			{
				val bundle: A_Bundle = visibleName.bundleOrNil
				if (bundle.notNil)
				{
					val method: A_Method = bundle.bundleMethod
					val lexer = method.lexer
					if (lexer.notNil)
					{
						lexers.add(lexer)
					}
				}
			}
		}
		val lexicalScanner = LexicalScanner { shortModuleNameNative }
		lexers.forEach(lexicalScanner::addLexer)
		return lexicalScanner
	}

	override fun o_AllAncestors(self: AvailObject): A_Set = allAncestors.get()

	override fun o_ModuleAddStyler(self: AvailObject, styler: A_Styler) =
		lock.safeWrite {
			self.updateSlotShared(STYLERS) {
				setWithElementCanDestroy(styler, true)
			}
		}

	override fun o_ModuleStylers(self: AvailObject): A_Set =
		lock.read { self[STYLERS] }

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

	override fun o_Bundles(self: AvailObject): A_Set = self[BUNDLES]

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
		 * @param runtime
		 *   The current [AvailRuntime] that will eventually have this module
		 *   added to it.  Capturing this now makes it easier to ensure we can
		 *   access needed repository structures from any thread.
		 * @param moduleName
		 *   The fully qualified [name][StringDescriptor] of the module.
		 * @return
		 *   The new module.
		 */
		fun newModule(
			runtime: AvailRuntime,
			moduleName: A_String
		): A_Module =
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
				setSlot(SEALS, emptyMap)
				setSlot(LEXERS, emptySet)
				setSlot(STYLERS, emptySet)
				setSlot(UNLOAD_FUNCTIONS, emptyTuple)
				setSlot(ALL_BLOCK_PHRASES, emptyTuple)
				// Create a new shared descriptor.
				setDescriptor(
					ModuleDescriptor(
						SHARED,
						moduleName.makeShared(),
						runtime))
			}

		/**
		 * The mutable [ModuleDescriptor], used only during construction of a
		 * new [A_Module], prior to replacing it with a new shared descriptor.
		 */
		private val initialMutableDescriptor = ModuleDescriptor(
			Mutability.MUTABLE, emptyTuple, null)

		/**
		 * Create an empty [BloomFilter] for use as a package's serialization
		 * filter.  It contains the hashes of every [NameInModule] that was
		 * declared or referenced within the package representative or any of
		 * the modules and packages recursively inside this package.
		 *
		 * @see NamesIndex
		 */
		fun newEmptyBloomFilter(): BloomFilter<NameInModule>
		{
			return BloomFilter(20000, 5)
		}
	}
}
