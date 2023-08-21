/*
 * ModuleManifestEntry.kt
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

package avail.compiler

import avail.descriptor.functions.A_Function
import avail.descriptor.functions.A_RawFunction.Companion.originatingPhraseIndex
import avail.descriptor.module.A_Module
import avail.descriptor.module.A_Module.Companion.originatingPhraseAtIndex
import avail.descriptor.numbers.A_Number.Companion.extractInt
import avail.descriptor.numbers.A_Number.Companion.isInt
import avail.descriptor.types.A_Type
import avail.descriptor.types.A_Type.Companion.argsTupleType
import avail.descriptor.types.A_Type.Companion.returnType
import avail.descriptor.types.A_Type.Companion.sizeRange
import avail.descriptor.types.A_Type.Companion.typeAtIndex
import avail.descriptor.types.A_Type.Companion.upperBound
import avail.persistence.cache.record.NameInModule
import avail.utility.unvlqInt
import avail.utility.vlq
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

/**
 * A `ModuleManifestEntry` is a short summary of an interesting definition
 * created by some module.  This includes atoms, method definitions, semantic
 * restrictions, lexers, module variables/constants, etc.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
class ModuleManifestEntry
{
	/** The [kind][SideEffectKind] of manifest entry that this is. */
	val kind: SideEffectKind

	/** The [NameInModule] being declared, defined, or restricted, if any. */
	val nameInModule: NameInModule?

	/**
	 * A short textual description of this entry, to present in a list of
	 * manifest entries.
	 */
	val summaryText: String

	/**
	 * A short textual description of each argument type.  Only valid if the
	 * [returnType] is not `null`.
	*/
	val argumentTypes: Array<String>

	/**
	 * A short textual description of the return type.  If this is `null`, the
	 * [argumentTypes] should be ignored.
	 */
	val returnType: String?

	/**
	 * The first line of the top level statement responsible for adding the
	 * definition described by this manifest entry.
	 */
	val topLevelStartingLine: Int

	/**
	 * The location within this module of the start of the body function for
	 * this definition, or 0 if inapplicable.
	 */
	val definitionStartingLine: Int

	/**
	 * The [A_Function] that best acts as the body of this entry, such as a
	 * method definition's body function, or a lexer definition's body function.
	 * This is `null` if a body is inapplicable or unavailable.  It is *always*
	 * null after reading a [ModuleManifestEntry] from the repository.
	 */
	private var bodyFunction: A_Function?

	/**
	 * The index into the current module's tuple of phrases, accessible via
	 * [A_Module.originatingPhraseAtIndex].  If this has not yet been computed,
	 * it will be `-1`, and the [bodyFunction] will be some [A_Function].  If
	 * there is no suitable body, or if it's not something that was serialized
	 * with the module, it will also be `-1`, but the [bodyFunction] will be
	 * `null`.
	 */
	var bodyPhraseIndexNumber: Int

	/**
	 * Create a [ModuleManifestEntry] from its parts.
	 *
	 * @param kind
	 *   The [kind][SideEffectKind] of manifest entry that this is.
	 * @param nameInModule
	 *   The [NameInModule] being declared, defined, or restricted, if any.
	 * @param summaryText
	 *   A short textual description of this entry, to present in a list of
	 *   manifest entries.
	 * @param signature
	 *   The optional function [A_Type] for this definition.
	 * @param topLevelStartingLine
	 *   The first line of the top level statement responsible for adding the
	 *   definition described by this manifest entry.
	 * @param definitionStartingLine
	 *   The location within this module of the start of the body function for
	 *   this definition, or 0 if inapplicable.
	 * @param bodyFunction
	 *   The [A_Function] that best acts as the body of this entry, such as a
	 *   method definition's body function, or a lexer definition's body
	 *   function. This is `null` if a body is inapplicable or unavailable.  It
	 *   is *always* null after reading a [ModuleManifestEntry] from the
	 *   repository.
	 * @param bodyPhraseIndexNumber
	 *   The index into the current module's tuple of phrases, accessible via
	 *   [A_Module.originatingPhraseAtIndex].  If this has not yet been
	 *   computed, it will be `-1`, and the [bodyFunction] will be some
	 *   [A_Function].  If there is no suitable body, or if it's not something
	 *   that was serialized with the module, it will also be `-1`, but the
	 *   [bodyFunction] will be `null`.
	 */
	constructor(
		kind: SideEffectKind,
		nameInModule: NameInModule?,
		summaryText: String,
		signature: A_Type?,
		topLevelStartingLine: Int,
		definitionStartingLine: Int,
		bodyFunction: A_Function? = null,
		bodyPhraseIndexNumber: Int = -1)
	{
		this.kind = kind
		this.nameInModule = nameInModule
		this.summaryText = summaryText
		this.argumentTypes = signature?.argsTupleType?.run {
			val count = sizeRange.upperBound.run {
				if (isInt) extractInt else 0
			}
			Array(count) { typeAtIndex(it + 1).toString() }
		} ?: emptyArray()
		this.returnType = signature?.returnType?.toString()
		this.topLevelStartingLine = topLevelStartingLine
		this.definitionStartingLine = definitionStartingLine
		this.bodyFunction = bodyFunction
		this.bodyPhraseIndexNumber = bodyPhraseIndexNumber
	}

	/**
	 * Write this entry to the provided [DataOutputStream], in a way that is
	 * naturally delimited for subsequent reading via the secondary constructor
	 * that takes a [DataInputStream].
	 *
	 * @param binaryStream
	 *   A [DataOutputStream] on which to write this entry.
	 * @throws IOException
	 *   If I/O fails.
	 */
	@Throws(IOException::class)
	fun write(binaryStream: DataOutputStream)
	{
		bodyFunction?.let {
			bodyPhraseIndexNumber = it.code().originatingPhraseIndex
			// This was only being held to ask about the index, *after* the
			// function had already been executed and serialized, and its phrase
			// added to the module's tuple of phrases.  Otherwise the function's
			// phrase wouldn't have an index yet.
			bodyFunction = null
		}
		binaryStream.write(kind.ordinal)
		binaryStream.writeBoolean(nameInModule != null)
		nameInModule?.run {
			binaryStream.writeUTF(moduleName)
			binaryStream.writeUTF(atomName)
		}
		binaryStream.writeUTF(summaryText)
		// If the returnType is null, write a VLQ-encoded zero.  Otherwise write
		// the argument count + 1, the arguments, and the return type.
		when (returnType)
		{
			null -> binaryStream.vlq(0)
			else ->
			{
				binaryStream.vlq(argumentTypes.size + 1)
				argumentTypes.forEach(binaryStream::writeUTF)
				binaryStream.writeUTF(returnType)
			}
		}
		binaryStream.writeInt(topLevelStartingLine)
		binaryStream.writeInt(definitionStartingLine)
		binaryStream.writeInt(bodyPhraseIndexNumber)
	}

	/**
	 * Reconstruct an entry that was previously written via [write].
	 *
	 * @param binaryStream
	 *   Where to read the entry from.
	 * @throws IOException
	 *   If I/O fails.
	 */
	@Throws(IOException::class)
	internal constructor(binaryStream: DataInputStream)
	{
		kind = SideEffectKind.all[binaryStream.read()]
		nameInModule = when (binaryStream.readBoolean())
		{
			true -> NameInModule(binaryStream.readUTF(), binaryStream.readUTF())
			false -> null
		}
		summaryText = binaryStream.readUTF()
		when (val count = binaryStream.unvlqInt() - 1)
		{
			// Sentinel value indicates no signature was written by `write`.
			-1 ->
			{
				argumentTypes = emptyArray()
				returnType = null
			}
			else ->
			{
				argumentTypes = Array(count) { binaryStream.readUTF() }
				returnType = binaryStream.readUTF()
			}
		}
		topLevelStartingLine = binaryStream.readInt()
		definitionStartingLine = binaryStream.readInt()
		bodyFunction = null
		bodyPhraseIndexNumber = binaryStream.readInt()
	}
}
