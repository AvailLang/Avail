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

import avail.persistence.cache.record.PhrasePathRecord.PhraseNode
import avail.utility.structures.BloomFilter
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
	 * A [MutableMap] from names declared by this module to an optional
	 * [NameInModule] that the name is an alias of, or `null` if the name is not
	 * declared as an alias.
	 */
	private val declaredNames: MutableMap<NameInModule, NameInModule?>

	/**
	 * A [Map] from old names to a non-empty [List] of new names that appear as
	 * values in the [declaredNames].  This is the inverse relationship of
	 * [declaredNames].
	 */
	private val invertedAliases: MutableMap<NameInModule, List<NameInModule>>

	/**
	 * A [Map] from each occurring [NameInModule] to the [NameOccurrences] that
	 * describe where and how the name is used in this module.
	 */
	private val occurrences: MutableMap<NameInModule, NameOccurrences>

	/**
	 * Iff this index is for a package, this is a bloom filter that can be used
	 * to quickly eliminate sections of the module tree from a more expensive
	 * search within each module.  If the filter appears to contain the name in
	 * question, the other fields of this object should be examined, to
	 * determine if the package representative module contained information
	 * about the atom name, and all packages and modules directly within this
	 * package should also be examined for information about the name.
	 */
	private var bloomFilterIfPackage: BloomFilter<NameInModule>?

	/**
	 * The occurrences of things related to a name appearing in this module.
	 */
	data class NameOccurrences(
		val declaration: MutableList<Declaration>,
		val definitions: MutableList<Definition>,
		val usages: MutableList<Usage>)

	/**
	 * A declaration of a name.  Note that there may be multiple positions
	 * involved, such as the Names section of the header, the first mention of
	 * the name, or an Alias statement that connects it to another name.
	 */
	data class Declaration(
		val alias: NameInModule?,
		val position: PositionInfo)

	/**
	 * A definition of a method, macro, or restriction in this module.
	 */
	data class Definition(
		val definitionType: DefinitionType,
		val position: PositionInfo)

	/**
	 * The kind of thing being defined with some [NameInModule].
	 */
	enum class DefinitionType
	{
		Method,
		Macro,
		SemanticRestriction,
		GrammaticalRestriction,
		Lexer
	}

	/**
	 * An indication of where a name is used in the file, suitable for
	 * presenting in an itemized list.
	 */
	data class Usage(
		val usageType: UsageType,
		val position: PositionInfo)

	/**
	 * The way that a [NameInModule] is being used somewhere.
	 */
	enum class UsageType
	{
		NameInHeader,
		AliasInHeader,
		AliasInBody,
		ExplicitCreationInBody,
		ImplicitCreationInBody,
		MethodSend,
		MacroSend
	}

	/**
	 * This captures information about how to find a usage of some name.  For
	 * now, we just capture a line number.
	 *
	 * TODO – It would be more accurate if we captured a way to get to the
	 *  [PhraseNode] in the [PhrasePathRecord] associated with the module.  Even
	 *  capturing the column number might be sufficient.
	 */
	data class PositionInfo(
		val lineNumber: Int)

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
		declaredNames.keys.forEach(filter::add)
		invertedAliases.keys.forEach(filter::add)
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
	 * @param withDeclaration
	 *   What to do if the declaration is present, passing the aliased name
	 *   ([NameInModule]) if any, otherwise `null`.
	 * @param withOccurrences
	 *   What to do with the [NameOccurrences] if an entry was found for the
	 *   name.
	 */
	fun findMentions(
		nameToFind: NameInModule,
		withDeclaration: (NameInModule?) -> Unit,
		withOccurrences: (NameOccurrences) -> Unit)
	{
		declaredNames[nameToFind]?.let(withDeclaration)
		occurrences[nameToFind]?.let(withOccurrences)
	}

	/**
	 * Output this [NamesIndex] onto a [DataOutputStream], so that Anvil can
	 * later access it to determine what names are used, and in what ways,
	 * inside this module.
	 */
	@Throws(IOException::class)
	internal fun write(binaryStream: DataOutputStream)
	{
		// Avoid repeating the names of modules by collecting all references
		// and giving them a unique numbering.
		val allReferencedModules = mutableSetOf<String>()
		declaredNames.values.mapNotNullTo(allReferencedModules) {
			it?.moduleName
		}
		occurrences.keys.mapTo(
			allReferencedModules, NameInModule::moduleName)
		val moduleNumbering = mutableMapOf<String, Int>()
		// Sort them to reduce the entropy for record compression.
		allReferencedModules.sorted().forEach { moduleName ->
			moduleNumbering[moduleName] = moduleNumbering.size
		}
		// Collect
		//TODO
	}

	override fun toString(): String = buildString {
		append("NamesIndex (")
		append(declaredNames.size)
		append(" decls, ")
		append(occurrences.values.sumOf { it.definitions.size })
		append(" defs")
		bloomFilterIfPackage?.run {
			append(", bloom=")
			append(bitCount)
		}
	}

	/**
	 * Reconstruct a [NamesIndex], having previously been written via [write].
	 *
	 * @param bytes
	 *   Where to read the [NamesIndex] from.
	 * @throws IOException
	 *   If I/O fails.
	 */
	@Throws(IOException::class)
	internal constructor(bytes: ByteArray)
	{
		//TODO
		declaredNames = mutableMapOf()
		//TODO
		invertedAliases = mutableMapOf()
		//TODO
		occurrences = mutableMapOf()
		//TODO
		bloomFilterIfPackage = null
	}

	/**
	 * Construct a [NamesIndex] from the provided data.
	 *
	 * @param declaredNames
	 *   A [MutableMap] from names declared by this module to an optional
	 *   [NameInModule] that the name is an alias of, or `null` if the name is
	 *   not declared as an alias.
	 * @param definitions
	 *   A [MutableMap] from each occurring [NameInModule] to the
	 *   [NameOccurrences] that describe where and how the name is used in this
	 *   module.
	 * @param bloomFilterIfPackage
	 *   Iff this index is for a package, this is a bloom filter that can be
	 *   used to quickly eliminate sections of the module tree from a more
	 *   expensive search within each module.  If the filter appears to contain
	 *   the name in question, the other fields of this object should be
	 *   examined, to determine if the package representative module contained
	 *   information about the atom name, and all packages and modules directly
	 *   within this package should also be examined for information about the
	 *   name.
	 */
	constructor(
		declaredNames: MutableMap<NameInModule, NameInModule?>,
		definitions: MutableMap<NameInModule, NameOccurrences>,
		bloomFilterIfPackage: BloomFilter<NameInModule>?)
	{
		this.declaredNames = declaredNames
		this.invertedAliases = declaredNames.entries
			.filter { it.value != null }
			.groupBy(keySelector = { it.key }, valueTransform = { it.value!! })
			.toMutableMap()
		this.occurrences = definitions
		this.bloomFilterIfPackage = bloomFilterIfPackage
	}
}
