/**
 * ClassCommentImplementation.java
 * Copyright © 1993-2015, The Avail Foundation, LLC.
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

import java.util.ArrayList;
import com.avail.descriptor.A_String;
import com.avail.descriptor.StringDescriptor;
import com.avail.utility.json.JSONWriter;

/**
 * A comment that describes a particular class.
 *
 * @author Richard Arriaga &lt;Rich@availlang.org&gt;
 */
public class ClassCommentImplementation extends AbstractCommentImplementation
{
	/**
	 * The {@link ArrayList} of the class's {@link StacksSuperTypeTag supertypes}
	 */
	final ArrayList<StacksSuperTypeTag> supertypes;

	/**
	 * The {@link ArrayList} of the class's {@link StacksFieldTag fields}
	 */
	final ArrayList<StacksFieldTag> fields;

	/**
	 * The hash id for this implementation
	 */
	final private int hashID;

	/**
	 * Construct a new {@link ClassCommentImplementation}.
	 *
	 * @param signature
	 * 		The {@link CommentSignature signature} of the class/method the
	 * 		comment describes.
	 * @param commentStartLine
	 * 		The start line in the module the comment being parsed appears.
	 * @param author
	 * 		The {@link StacksAuthorTag author} of the implementation.
	 * @param sees
	 * 		A {@link ArrayList} of any {@link StacksSeeTag "@sees"} references.
	 * @param description
	 * 		The overall description of the implementation
	 * @param categories
	 * 		The categories the implementation appears in
	 * @param aliases
	 * 		The aliases the implementation is known by
	 * @param supertypes
	 * 		The {@link ArrayList} of the class's
	 * 		{@link StacksSuperTypeTag supertypes}
	 * @param fields
	 * 		The {@link ArrayList} of the class's {@link StacksFieldTag fields}
	 * @param sticky
	 * 		Whether or not the method should be documented regardless of
	 * 		visibility
	 */
	public ClassCommentImplementation (
		final CommentSignature signature,
		final int commentStartLine,
		final ArrayList<StacksAuthorTag> author,
		final ArrayList<StacksSeeTag> sees,
		final StacksDescription description,
		final ArrayList<StacksCategoryTag> categories,
		final ArrayList<StacksAliasTag> aliases,
		final ArrayList<StacksSuperTypeTag> supertypes,
		final ArrayList<StacksFieldTag> fields,
		final boolean sticky)
	{
		super(signature, commentStartLine, author, sees, description,
			categories, aliases, sticky);
		this.supertypes = supertypes;
		this.fields = fields;

		this.hashID = StringDescriptor.from(
			signature.name()).hash();
	}

	@Override
	public void addToImplementationGroup(
		final ImplementationGroup implementationGroup)
	{
		implementationGroup.classImplemenataion(this);
	}

	@Override
	public void addImplementationToImportModule (
		final A_String name, final StacksImportModule importModule)
	{
		//Do nothing as new implentations can't be introduced for classes

	}

	@Override
	public void toJSON (
		final LinkingFileMap linkingFileMap,
		final String nameOfGroup,
		final StacksErrorLog errorLog,
		final JSONWriter jsonWriter)
	{
		signature().toJSON(nameOfGroup, isSticky(), jsonWriter);
		jsonWriter.write("supertypes");
		jsonWriter.startArray();
		for(final StacksSuperTypeTag supertype : supertypes)
		{
			supertype.toJSON(linkingFileMap, hashID, errorLog, 1, jsonWriter);
		}
		jsonWriter.endArray();

		if (categories.size() > 0)
		{
			categories.get(0).toJSON(linkingFileMap,
				hashID, errorLog, 1, jsonWriter);
		} else
		{
			jsonWriter.write("categories");
			jsonWriter.startArray();
			jsonWriter.endArray();
		}

		if (aliases.size() > 0)
		{
			aliases.get(0).toJSON(linkingFileMap,
				hashID, errorLog, 1, jsonWriter);
		} else
		{
			jsonWriter.write("aliases");
			jsonWriter.startArray();
			jsonWriter.endArray();
		}

		jsonWriter.write("sees");
		jsonWriter.startArray();
		for (final StacksSeeTag see : sees)
		{
			jsonWriter.write(see.thingToSee().toJSON(linkingFileMap, hashID,
				errorLog, jsonWriter));
		}
		jsonWriter.endArray();

		jsonWriter.write("description");
		description.toJSON(linkingFileMap, hashID, errorLog, jsonWriter);


		//The ordered position of the parameter in the method signature.
		int position = 1;
		jsonWriter.write("parameters");
		jsonWriter.startArray();
		for (final StacksFieldTag fieldTag : fields)
		{
			fieldTag.toJSON(linkingFileMap, hashID, errorLog, position++,
				jsonWriter);
		}
		jsonWriter.endArray();
	}

	@Override
	public String toString ()
	{
		return signature().toString();
	}
}
