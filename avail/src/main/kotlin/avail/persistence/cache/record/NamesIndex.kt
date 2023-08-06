/*
 * NamesIndex.kt
 * Copyright © 1993-2023, The Avail Foundation, LLC.
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

package avail.persistence.cache.record

import avail.descriptor.methods.A_Definition
import avail.descriptor.methods.A_GrammaticalRestriction
import avail.descriptor.methods.A_Macro
import avail.descriptor.methods.A_SemanticRestriction
import avail.descriptor.module.A_Module
import avail.descriptor.module.A_Module.Companion.addSeal
import avail.descriptor.parsing.A_Lexer
import avail.interpreter.primitive.methods.P_Alias
import avail.persistence.cache.record.PhrasePathRecord.PhraseNode
import avail.utility.decodeString
import avail.utility.sizedString
import avail.utility.structures.BloomFilter
import avail.utility.unvlqInt
import avail.utility.vlq
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

/**
 * Information for efficiently navigating between declarations, aliases,
 * definitions, and uses of atoms.  This is information bounded by the module in
 * which it occurs, but the referenced atoms may be in ancestor modules.
 */
class NamesIndex
{
	/**
	 * A [Map] from each occurring [NameInModule] to the [NameOccurrences] that
	 * describe where and how the name is used in this module.
	 */
	val occurrences: MutableMap<NameInModule, NameOccurrences>

	/**
	 * Iff this index is for a package, this is a Bloom filter that can be used
	 * to quickly eliminate sections of the module tree from a more expensive
	 * search within each module.  If the filter appears to contain the name in
	 * question, the other fields of this object should be examined, to
	 * determine if the package representative module contained information
	 * about the atom name, and all packages and modules directly within this
	 * package should also be examined for information about the name.
	 */
	private var bloomFilterIfPackage: BloomFilter<NameInModule>?

	/**
	 * The occurrences of things related to some name appearing in this module.
	 *
	 * @property declarations
	 *   A list of the name's [Declaration]s present in this module.
	 * @property definitions
	 *   A list of the name's [Definition]s present in this module.
	 * @property usages
	 *   A list of places that the name has [Usage]s within this module.
	 */
	data class NameOccurrences(
		val declarations: MutableList<Declaration>,
		val definitions: MutableList<Definition>,
		val usages: MutableList<Usage>)
	{
		/**
		 * Write this [NameOccurrences] onto the given [DataOutputStream].  Use
		 * the maps from module name to index and atom name to index for
		 * compression. The same maps will be provided in inverse form (as
		 * [List]s of [String]s) during subsequent decoding (via the
		 * [NameOccurrences] constructor taking a [DataInputStream] and the two
		 * lists).
		 *
		 * @param binaryStream
		 *   The stream on which to serialize this object.
		 * @param moduleNumbering
		 *   A mapping from module names to unique non-negative integers.
		 * @param nameNumbering
		 *   A mapping from atom names to unique non-negative integers.
		 */
		fun write(
			binaryStream: DataOutputStream,
			moduleNumbering: Map<String, Int>,
			nameNumbering: Map<String, Int>)
		{
			binaryStream.vlq(declarations.size)
			declarations.forEach { decl ->
				decl.write(binaryStream, moduleNumbering, nameNumbering)
			}
			binaryStream.vlq(definitions.size)
			definitions.forEach { def ->
				def.write(binaryStream)
			}
			binaryStream.vlq(usages.size)
			usages.forEach { usage ->
				usage.write(binaryStream)
			}
		}

		/**
		 * Reconstruct a [NameOccurrences] from a stream, using the already
		 * constructed lists of module names and atom names.
		 *
		 * @param binaryStream
		 *   The source of data from which to reconstruct a [NameOccurrences].
		 * @param moduleNames
		 *   The [List] of module names used in the reconstruction.
		 * @param atomNames
		 *   The [List] of atom names used in the reconstruction.
		 */
		constructor(
			binaryStream: DataInputStream,
			moduleNames: List<String>,
			atomNames: List<String>
		): this(
			MutableList(binaryStream.unvlqInt()) {
				Declaration(binaryStream, moduleNames, atomNames)
			},
			MutableList(binaryStream.unvlqInt()) {
				Definition(binaryStream)
			},
			MutableList(binaryStream.unvlqInt()) {
				Usage(binaryStream)
			})
	}

	/**
	 * A declaration of a name.  Note that there may be multiple positions
	 * involved, such as the Names section of the header, the first mention of
	 * the name, or an Alias statement that connects it to another name.
	 *
	 * @property alias
	 *   The existing name, if any, that the new name aliases.
	 * @property phraseIndex
	 *   An index into the canonically ordered [PhraseNode]s in the
	 *   [PhrasePathRecord] for this module compilation.  That [PhraseNode] is
	 *   enough information to identify a region of the module to highlight
	 *   or select to identify the declaration of the name.
	 */
	data class Declaration(
		val alias: NameInModule?,
		val phraseIndex: Int)
	{
		/**
		 * Write this [Declaration] onto the given [DataOutputStream].  Use the
		 * maps from module name to index and atom name to index for
		 * compression. The same maps will be provided in inverse form (as
		 * [List]s of [String]s) during subsequent decoding (via the
		 * [Declaration] constructor taking a [DataInputStream] and the two
		 * lists).
		 *
		 * @param binaryStream
		 *   The stream on which to serialize this object.
		 * @param moduleNumbering
		 *   A mapping from module names to unique non-negative integers.
		 * @param nameNumbering
		 *   A mapping from atom names to unique non-negative integers.
		 */
		fun write(
			binaryStream: DataOutputStream,
			moduleNumbering: Map<String, Int>,
			nameNumbering: Map<String, Int>)
		{
			binaryStream.writeBoolean(alias != null)
			alias?.run { write(binaryStream, moduleNumbering, nameNumbering) }
			binaryStream.vlq(phraseIndex)
		}

		/**
		 * Reconstruct a [Declaration] from a stream, using the already
		 * constructed lists of module names and atom names.
		 *
		 * @param binaryStream
		 *   The source of the data for reconstructing the [Declaration].
		 * @param moduleNames
		 *   The [List] of module names used in the reconstruction.
		 * @param atomNames
		 *   The [List] of atom names used in the reconstruction.
		 */
		constructor(
			binaryStream: DataInputStream,
			moduleNames: List<String>,
			atomNames: List<String>
		): this(
			when (binaryStream.readBoolean())
			{
				true -> NameInModule(binaryStream, moduleNames, atomNames)
				false -> null
			},
			binaryStream.unvlqInt())
	}

	/**
	 * A definition of a method, macro, or restriction in this module.
	 *
	 * @property definitionType
	 *   The [DefinitionType] identifying the nature of this definition.
	 * @property manifestIndex
	 *   An index into the [ManifestRecord] for the current module, indicating
	 *   which manifest entry is responsible for this definition.
	 */
	data class Definition(
		val definitionType: DefinitionType,
		val manifestIndex: Int)
	{
		/**
		 * Serialize the receiver onto the given [DataOutputStream], in a form
		 * suitable for reconstruction via the [Definition] constructor taking
		 * a [DataInputStream].
		 *
		 * @param binaryStream
		 *   The [DataOutputStream] on which to serialize this [Definition].
		 */
		fun write(binaryStream: DataOutputStream)
		{
			binaryStream.vlq(definitionType.ordinal)
			binaryStream.vlq(manifestIndex)
		}

		/**
		 * Reconstruct a [DefinitionType] whose data was previously serialized
		 * onto a stream of bytes that can be read from the [DataInputStream].
		 *
		 * @param binaryStream
		 *   The source of data for deserialization.
		 */
		constructor(
			binaryStream: DataInputStream
		): this(
			DefinitionType.all[binaryStream.unvlqInt()],
			binaryStream.unvlqInt())
	}

	/**
	 * The kind of thing being defined with some [NameInModule].  These are
	 * serialized by their ordinal, so change the repository version number if
	 * these must change.
	 */
	enum class DefinitionType
	{
		/**
		 * The definition is a [method definition][A_Definition].
		 */
		Method,

		/** The definition is a [macro definition][A_Macro]. */
		Macro,

		/**
		 * The definition is a [semantic restriction][A_SemanticRestriction].
		 */
		SemanticRestriction,

		/**
		 * The definition is a
		 * [grammatical restriction[A_GrammaticalRestriction].
		 */
		GrammaticalRestriction,

		/** The definition is of a [lexer][A_Lexer]. */
		Lexer,

		/** The definition of a [seal][A_Module.addSeal] on some method. */
		Seal;

		companion object
		{
			val all = entries.toTypedArray()
		}
	}

	/**
	 * An indication of where a name is used in the file, suitable for
	 * presenting in an itemized list.
	 *
	 * @property usageType
	 *   The [UsageType] that indicates how the name is being used.
	 * @property phraseIndex
	 *   An index into the canonically ordered [PhraseNode]s in the
	 *   [PhrasePathRecord] for this module compilation.  That [PhraseNode] is
	 *   enough information to identify a region of the module to highlight
	 *   or select to identify the usage of the name.
	 */
	data class Usage(
		val usageType: UsageType,
		val phraseIndex: Int)
	{
		/**
		 * Serialize the receiver onto the given [DataOutputStream], in a form
		 * suitable for reconstruction via the [Usage] constructor taking
		 * a [DataInputStream].
		 *
		 * @param binaryStream
		 *   The [DataOutputStream] on which to serialize this [Usage].
		 */
		fun write(binaryStream: DataOutputStream)
		{
			binaryStream.vlq(usageType.ordinal)
			binaryStream.vlq(phraseIndex)
		}

		/**
		 * Reconstruct a [Usage] from a stream.  The data was put there by the
		 * [write] method.
		 *
		 * @param binaryStream
		 *   The source of data for deserialization.
		 */
		constructor(
			binaryStream: DataInputStream
		): this(
			UsageType.all[binaryStream.unvlqInt()],
			binaryStream.unvlqInt())
	}

	/**
	 * The way that a [NameInModule] is being used somewhere.
	 */
	enum class UsageType
	{
		/**
		 * This is an invocation of the name as a method to invoke at runtime.
		 */
		MethodSend,

		/**
		 * This is an invocation of the name as a macro at compile time.
		 */
		MacroSend,

		/**
		 * The name is listed in the `Names` section of the module header.
		 */
		NameInHeader,

		/**
		 * The name is mentioned (not defined) in an import section of the
		 * module header.
		 */
		AliasInHeader,

		/**
		 * An explicit [aliasing][P_Alias] took place within the module body.
		 */
		AliasInBody,

		/**
		 * The atom (name) was created explicitly within the body.
		 */
		ExplicitCreationInBody,

		/**
		 * The atom (name) was created implicitly within the body.
		 */
		ImplicitCreationInBody;

		companion object
		{
			val all = entries.toTypedArray()
		}
	}

	/**
	 * Look up the [NameOccurrences] for the given [NameInModule], creating an
	 * entry for it if needed.
	 *
	 * @param nameInModule
	 *   The [NameInModule] to look up.
	 * @return
	 *   The found or added [NameOccurrences].
	 */
	private fun occurrences(nameInModule: NameInModule): NameOccurrences =
		occurrences.computeIfAbsent(nameInModule) {
			NameOccurrences(mutableListOf(), mutableListOf(), mutableListOf())
		}

	/**
	 * Add information about a [Declaration].
	 *
	 * @param nameInModule
	 *   The [NameInModule] that is being declared.
	 * @param alias
	 *   The optional existing [NameInModule] that is being aliased.
	 * @param phraseIndex
	 *   The index into this [ModuleCompilation]'s [PhrasePathRecord]'s phrases.
	 *   Note that this uniquely determines a phrase anywhere within the module,
	 *   not just a top level phrase.
	 */
	fun addDeclaration(
		nameInModule: NameInModule,
		alias: NameInModule?,
		phraseIndex: Int)
	{
		occurrences(nameInModule).declarations
			.add(Declaration(alias, phraseIndex))
	}

	/**
	 * Add information about a [Definition].
	 *
	 * @param nameInModule
	 *   The [NameInModule] that is having a [Definition] added.
	 * @param definitionType
	 *   The [DefinitionType] categorizing the definition.
	 * @param manifestIndex
	 *   The index into this [ModuleCompilation]'s [ManifestRecord]'s manifest
	 *   entries, indicating which top-level statement was responsible for the
	 *   adding this [Definition].
	 */
	fun addDefinition(
		nameInModule: NameInModule,
		definitionType: DefinitionType,
		manifestIndex: Int)
	{
		occurrences(nameInModule).definitions
			.add(Definition(definitionType, manifestIndex))
	}

	/**
	 * Add information about a [Usage].
	 *
	 * @param nameInModule
	 *   The [NameInModule] being used.
	 * @param usageType
	 *   The nature of the usage of the [nameInModule].
	 * @param phraseIndex
	 *   The index into this [ModuleCompilation]'s [PhrasePathRecord]'s phrases,
	 *   identifying the exact [PhraseNode] that uses the [nameInModule].
	 */
	fun addUsage(
		nameInModule: NameInModule,
		usageType: UsageType,
		phraseIndex: Int)
	{
		occurrences(nameInModule).usages.add(Usage(usageType, phraseIndex))
	}

	/**
	 * Add all names mentioned by this module or package to the given
	 * [BloomFilter].
	 *
	 * @param filter
	 *   The filter to update with the names mentioned in this module or
	 *   package.
	 */
	fun addToFilter(filter: BloomFilter<NameInModule>)
	{
		bloomFilterIfPackage?.let { thisFilter ->
			// This record's filter already has everything, so do a union.
			filter.addAll(thisFilter)
			return
		}
		occurrences.keys.forEach(filter::add)
	}

	/**
	 * Find all occurrences of the given [nameToFind], invoking the functions as
	 * they're found.  Ignore the [bloomFilterIfPackage], letting the caller to
	 * decide how to use it to navigate.  Don't search inside submodules.  The
	 * caller should ensure the version of the file on disk corresponds with
	 * this [NamesIndex].
	 *
	 * @param nameToFind
	 *   The [NameInModule] to search for in the index.
	 * @return
	 *   The [NameOccurrences] or `null` if not found.
	 */
	fun findMentions(nameToFind: NameInModule) = occurrences[nameToFind]

	/**
	 * Output this [NamesIndex] onto a [DataOutputStream], so that Anvil can
	 * later access it to determine what names are used, and in what ways,
	 * inside this module.
	 *
	 * @param binaryStream
	 *   The [DataOutputStream] on which to serialize this [NamesIndex].
	 * @throws IOException
	 *   If the stream cannot be written to, which is impossible for an
	 *   in-memory stream.
	 */
	@Throws(IOException::class)
	internal fun write(binaryStream: DataOutputStream)
	{
		// Find all names declared or mentioned in the module.
		val fullNames = mutableSetOf<NameInModule>()
		fullNames.addAll(occurrences.keys)
		fullNames.addAll(
			occurrences.values
				.flatMap(NameOccurrences::declarations)
				.mapNotNull(Declaration::alias))
		// Avoid repeating the names of modules by collecting all references and
		// giving them a unique numbering.
		val moduleNames = fullNames.map(NameInModule::moduleName).toSortedSet()
		// Write the unique module names.
		binaryStream.vlq(moduleNames.size)
		moduleNames.forEach(binaryStream::sizedString)
		val moduleNumbering = moduleNames.withIndex()
			.associate { it.value to it.index }

		// Write the unique, sorted atom names.
		val sortedNames = fullNames.map(NameInModule::atomName).toSortedSet()
		// Write the unique module names.
		binaryStream.vlq(sortedNames.size)
		sortedNames.forEach(binaryStream::sizedString)
		val nameNumbering = sortedNames.withIndex()
			.associate { it.value to it.index }

		// Write all NameOccurrences.
		binaryStream.vlq(occurrences.size)
		occurrences.entries.sortedBy { it.key }.forEach { (key, occurrences) ->
			key.write(binaryStream, moduleNumbering, nameNumbering)
			occurrences.write(binaryStream, moduleNumbering, nameNumbering)
		}

		// Write Bloom filter if present.
		binaryStream.writeBoolean(bloomFilterIfPackage != null)
		bloomFilterIfPackage?.run { write(binaryStream) }
	}

	override fun toString(): String = buildString {
		append("NamesIndex (")
		append(occurrences.values.sumOf { it.definitions.size })
		append(" defs")
		bloomFilterIfPackage?.run {
			append(", Bloom=")
			append(bitCount)
		}
		append(")")
	}

	/**
	 * Reconstruct a [NamesIndex], having previously been written via [write].
	 *
	 * @param binaryStream
	 *   The source of the bytes from which to read a [NamesIndex].
	 * @throws IOException
	 *   If I/O fails.
	 */
	@Throws(IOException::class)
	internal constructor(binaryStream: DataInputStream)
	{
		// Read the module names,
		val moduleNames = List(binaryStream.unvlqInt()) {
			binaryStream.decodeString()
		}
		// Read the atom names.
		val atomNames = List(binaryStream.unvlqInt()) {
			binaryStream.decodeString()
		}

		// Read all NameOccurrences.
		occurrences = mutableMapOf()
		repeat(binaryStream.unvlqInt()) {
			occurrences[NameInModule(binaryStream, moduleNames, atomNames)] =
				NameOccurrences(binaryStream, moduleNames, atomNames)
		}

		bloomFilterIfPackage = when (binaryStream.readBoolean())
		{
			true -> BloomFilter(binaryStream)
			false -> null
		}
	}

	/**
	 * Construct a [NamesIndex] from the provided data.
	 *
	 * @param occurrences
	 *   A [MutableMap] from each occurring [NameInModule] to the
	 *   [NameOccurrences] that describe where and how the name is used in this
	 *   module.
	 * @param bloomFilterIfPackage
	 *   Iff this index is for a package, this is a Bloom filter that can be
	 *   used to quickly eliminate sections of the module tree from a more
	 *   expensive search within each module.  If the filter appears to contain
	 *   the name in question, the other fields of this object should be
	 *   examined, to determine if the package representative module contained
	 *   information about the atom name, and all packages and modules directly
	 *   within this package should also be examined for information about the
	 *   name.
	 */
	constructor(
		occurrences: MutableMap<NameInModule, NameOccurrences>,
		bloomFilterIfPackage: BloomFilter<NameInModule>?)
	{
		this.occurrences = occurrences
		this.bloomFilterIfPackage = bloomFilterIfPackage
	}
}
