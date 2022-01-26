/*
 * ModuleManifestEntry.kt
 * Copyright © 1993-2021, The Avail Foundation, LLC.
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
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

/**
 * A `ModuleManifestEntry` is a short summary of an interesting definition
 * created by some module.  This includes atoms, method definitions, semantic
 * restrictions, lexers, module variables/constants, etc.
 *
 * @constructor
 * Create a [ModuleManifestEntry].
 *
 * @property kind
 *   The [Kind] of manifest entry that this is.
 * @property summaryText
 *   A short textual description of the item being defined, without mentioning
 *   the [kind].
 * @property topLevelStartingLine
 *   The first line of the top level statement responsible for adding the
 *   definition described by this manifest entry.
 * @property definitionStartingLine
 *   The location within this module of the start of the body function for this
 *   definition, or 0 if inapplicable.
 * @property bodyFunction
 *   The [A_Function] that best acts as the body of this entry, such as a method
 *   definition's body function, or a lexer definition's body function. This is
 *   null if a body is inapplicable or unavailable.  It is always null after
 *   reading a [ModuleManifestEntry] from the repository.
 * @property bodyPhraseIndexNumber
 *   The index into the current module's tuple of phrases, accessible via
 *   [A_Module.originatingPhraseAtIndex].  If this has not yet been computed,
 *   it will be `-1`, and the [bodyFunction] will be some [A_Function].  If there
 *   is no suitable body, or if it's not something that was serialized with the
 *   module, it will also be `-1`, but the [bodyFunction] will be `null`.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
class ModuleManifestEntry constructor(
	val kind: SideEffectKind,
	val summaryText: String,
	val topLevelStartingLine: Int,
	val definitionStartingLine: Int,
	private var bodyFunction: A_Function? = null,
	var bodyPhraseIndexNumber: Int = -1)
{
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
		binaryStream.writeUTF(summaryText)
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
	internal constructor(binaryStream: DataInputStream) : this(
		SideEffectKind.all[binaryStream.read()],
		binaryStream.readUTF(),
		binaryStream.readInt(),
		binaryStream.readInt(),
		null,
		binaryStream.readInt())
}
