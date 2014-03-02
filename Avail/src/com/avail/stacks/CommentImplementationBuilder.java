/**
 * CommentImplementationBuilder.java
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

import java.util.ArrayList;
import com.avail.descriptor.A_String;

/**
 * TODO: Document CommentImplementationBuilder!
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
public class CommentImplementationBuilder
{

	/**
	 * The author keyword indicates the method implementation author.
	 */
	private final ArrayList<StacksAuthorTag> authors;

	/**
	 * @param tagContentTokens
	 * @throws ClassCastException
	 */
	public void addStacksAuthorTag(
		 final ArrayList<AbstractStacksToken> tagContentTokens)
			 throws ClassCastException
	{
		authors.add(new StacksAuthorTag (tagContentTokens));
	}


	/**
	 * The category keyword provides a category to which the method
	 * implementation belongs.
	 */
	private final ArrayList<StacksCategoryTag> categories;

	/**
	 * @param tagContentTokens
	 * @throws ClassCastException
	 */
	public void addStacksCategoryTag(
		 final ArrayList<AbstractStacksToken> tagContentTokens)
			 throws ClassCastException
	{
		final ArrayList<QuotedStacksToken> tempTokens =
			new ArrayList<QuotedStacksToken>(1);

		for (final AbstractStacksToken token : tagContentTokens)
		{
			tempTokens.add((QuotedStacksToken) token);
		}

		categories.add(new StacksCategoryTag (tempTokens));
	}

	/**
	 * The general description provided by the comment
	 */
	private final ArrayList<AbstractStacksToken> description;

	/**
	 * @param tagContentTokens
	 * @throws ClassCastException
	 */
	public void addStacksCommentDescription(
		 final ArrayList<AbstractStacksToken> tagContentTokens)
			 throws ClassCastException
	{
		description.addAll(tagContentTokens);
	}

	/**
	 * The field keyword indicates a field in the class implementation.
	 */
	private final ArrayList<StacksFieldTag> fields;

	/**
	 * @param tagContentTokens
	 * @throws ClassCastException
	 */
	public void addStacksFieldTag(
		 final ArrayList<AbstractStacksToken> tagContentTokens)
			 throws ClassCastException
	{
		final int tokenCount = tagContentTokens.size();

		if (tokenCount < 2)
		{
			//TODO Make an Error
		}

		final QuotedStacksToken tempName =
			(QuotedStacksToken) tagContentTokens.get(0);

		final QuotedStacksToken tempType =
			(QuotedStacksToken) tagContentTokens.get(1);

		if (tokenCount == 2)
		{
			fields.add(new StacksFieldTag (tempName, tempType,
				new ArrayList<AbstractStacksToken>(0)));
		}
		else
		{
			final ArrayList<AbstractStacksToken> rest =
				(ArrayList<AbstractStacksToken>)
					tagContentTokens.subList(2, tokenCount);
			fields.add(new StacksFieldTag (tempName, tempType,rest));
		}
	}

	/**
	 * The forbids keyword indicates the methods forbidden by a
	 * Grammatical Restriction for the method implementation.
	 */
	private final ArrayList<StacksForbidsTag> forbids;

	/**
	 * @param tagContentTokens
	 * @throws ClassCastException
	 */
	public void addStacksForbidTag(
		 final ArrayList<AbstractStacksToken> tagContentTokens)
			 throws ClassCastException
	{
		final ArrayList<QuotedStacksToken> tempTokens =
			new ArrayList<QuotedStacksToken>(1);

		for (final AbstractStacksToken token : tagContentTokens)
		{
			tempTokens.add((QuotedStacksToken) token);
		}

		forbids.add(new StacksForbidsTag (tempTokens));
	}

	/**
	 * The method keyword indicates the name of the method implementation.
	 */
	private final ArrayList<StacksGlobalTag> globalVariables;

	/**
	 * @param tagContentTokens
	 * @throws ClassCastException
	 */
	public void addStacksGlobalTag (
		final ArrayList<AbstractStacksToken> tagContentTokens)
			 throws ClassCastException
	{
		final int tokenCount = tagContentTokens.size();

		if (tokenCount < 2)
		{
			//TODO Make an Error
		}

		final QuotedStacksToken tempName =
			(QuotedStacksToken) tagContentTokens.get(0);

		final QuotedStacksToken tempType =
			(QuotedStacksToken) tagContentTokens.get(1);

			globalVariables.add(new StacksGlobalTag (tempName, tempType));
	}

	/**
	 * The method keyword indicates the name of the method implementation.
	 */
	private final ArrayList<StacksMethodTag> methods;

	/**
	 * @param tagContentTokens
	 * @throws ClassCastException
	 */
	public void addStacksMethodTag(
		 final ArrayList<AbstractStacksToken> tagContentTokens)
			 throws ClassCastException
	{
		if (tagContentTokens.size() == 1)
		{
			methods.add(new StacksMethodTag (
					(QuotedStacksToken) tagContentTokens.get(0)));
		}
		else
		{
			//TODO throw error
		}
	}

	/**
	 * The parameter keyword indicates an input for the method
	 * implementation.
	 */
	private final ArrayList<StacksParameterTag> parameters;

	/**
	 * @param tagContentTokens
	 * @throws ClassCastException
	 */
	public void addStacksParameterTag(
		 final ArrayList<AbstractStacksToken> tagContentTokens)
			 throws ClassCastException
	{
		final int tokenCount = tagContentTokens.size();

		if (tokenCount < 2)
		{
			//TODO Make an Error
		}

		final QuotedStacksToken tempName =
			(QuotedStacksToken) tagContentTokens.get(0);

		final QuotedStacksToken tempType =
			(QuotedStacksToken) tagContentTokens.get(1);

		if (tokenCount == 2)
		{
			parameters.add(new StacksParameterTag (tempName, tempType,
				new ArrayList<AbstractStacksToken>(0)));
		}
		else
		{
			final ArrayList<AbstractStacksToken> rest =
				(ArrayList<AbstractStacksToken>)
					tagContentTokens.subList(2, tokenCount);
			parameters.add(new StacksParameterTag (tempName, tempType,rest));
		}
	}

	/**
	 * The raises keyword indicates the exceptions thrown by the method
	 * implementation.
	 */
	private final ArrayList<StacksRaisesTag> raises;

	/**
	 * @param tagContentTokens
	 * @throws ClassCastException
	 */
	public void addStacksRaisesTag(
		 final ArrayList<AbstractStacksToken> tagContentTokens)
			 throws ClassCastException
	{
		final int tokenCount = tagContentTokens.size();

		if (tokenCount < 1)
		{
			//TODO Make an Error
		}

		final QuotedStacksToken tempName =
			(QuotedStacksToken) tagContentTokens.get(0);

		if (tokenCount == 1)
		{
			raises.add(new StacksRaisesTag (tempName,
				new ArrayList<AbstractStacksToken>(0)));
		}
		else
		{
			final ArrayList<AbstractStacksToken> rest =
				(ArrayList<AbstractStacksToken>)
					tagContentTokens.subList(1, tokenCount);
			raises.add(new StacksRaisesTag (tempName,rest));
		}
	}


	/**
	 * The restricts keyword indicates the input types used by the method
	 * implementation's semantic restriction.
	 */
	private final ArrayList<StacksRestrictsTag> restricts;

	/**
	 * @param tagContentTokens
	 * @throws ClassCastException
	 */
	public void addStacksRestrictsTag(
		 final ArrayList<AbstractStacksToken> tagContentTokens)
			 throws ClassCastException
	{
		final int tokenCount = tagContentTokens.size();

		if (tokenCount < 1)
		{
			//TODO Make an Error
		}

		final QuotedStacksToken tempName =
			(QuotedStacksToken) tagContentTokens.get(0);

		if (tokenCount == 1)
		{
			restricts.add(new StacksRestrictsTag (tempName,
				new ArrayList<AbstractStacksToken>(0)));
		}
		else
		{
			final ArrayList<AbstractStacksToken> rest =
				(ArrayList<AbstractStacksToken>)
					tagContentTokens.subList(1, tokenCount);
			restricts.add(new StacksRestrictsTag (tempName,rest));
		}
	}

	/**
	 * The returns keyword indicates the output for the method
	 * implementation.
	 */
	private final ArrayList<StacksReturnTag> returns;

	/**
	 * @param tagContentTokens
	 * @throws ClassCastException
	 */
	public void addStacksReturnsTag(
		 final ArrayList<AbstractStacksToken> tagContentTokens)
			 throws ClassCastException
	{
		final int tokenCount = tagContentTokens.size();

		if (tokenCount < 1)
		{
			//TODO Make an Error
		}

		final QuotedStacksToken tempName =
			(QuotedStacksToken) tagContentTokens.get(0);

		if (tokenCount == 1)
		{
			returns.add(new StacksReturnTag (tempName,
				new ArrayList<AbstractStacksToken>(0)));
		}
		else
		{
			final ArrayList<AbstractStacksToken> rest =
				(ArrayList<AbstractStacksToken>)
					tagContentTokens.subList(1, tokenCount);
			returns.add(new StacksReturnTag (tempName,rest));
		}
	}

	/**
	 * The see keyword refers the reader to something else.  Not
	 * inherently linked.
	 */
	private final ArrayList<StacksSeeTag> sees;

	/**
	 * @param tagContentTokens
	 * @throws ClassCastException
	 */
	public void addStacksSeesTag(
		 final ArrayList<AbstractStacksToken> tagContentTokens)
			 throws ClassCastException
	{
		if (tagContentTokens.size() == 1)
		{
			sees.add(new StacksSeeTag (
					(RegionStacksToken) tagContentTokens.get(0)));
		}
		else
		{
			//TODO throw error
		}
	}

	/**
	 * The supertype keyword indicates the supertype of the class
	 * implementation.
	 */
	private final ArrayList<StacksSuperTypeTag> supertypes;

	/**
	 * @param tagContentTokens
	 * @throws ClassCastException
	 */
	public void addStacksSupertypeTag(
		 final ArrayList<AbstractStacksToken> tagContentTokens)
			 throws ClassCastException
	{
		if (tagContentTokens.size() == 1)
		{
			supertypes.add(new StacksSuperTypeTag (
					(QuotedStacksToken) tagContentTokens.get(0)));
		}
		else
		{
			//TODO throw error
		}
	}

	/**
	 * The type keyword indicates the name of the class implementation.
	 */
	private final ArrayList<StacksTypeTag> types;

	/**
	 * @param tagContentTokens
	 * @throws ClassCastException
	 */
	public void addStacksTypeTag(
		 final ArrayList<AbstractStacksToken> tagContentTokens)
			 throws ClassCastException
	{
		if (tagContentTokens.size() == 1)
		{
			types.add(new StacksTypeTag (
					(QuotedStacksToken) tagContentTokens.get(0)));
		}
		else
		{
			//TODO throw error
		}
	}

	/**
	 * The name of the module the comment originates from.
	 */
	private final A_String moduleName;

	/**
	 * Construct a new {@link CommentImplementationBuilder}.
	 * @param moduleName
	 * 		The name of the module the comment is in.
	 *
	 */
	public CommentImplementationBuilder (final A_String moduleName)
	{
		this.moduleName = moduleName;
		this.authors = new ArrayList<StacksAuthorTag>(0);
		this.categories = new ArrayList<StacksCategoryTag>(0);
		this.description = new ArrayList<AbstractStacksToken>(0);
		this.fields = new ArrayList<StacksFieldTag>(0);
		this.forbids = new ArrayList<StacksForbidsTag>(0);
		this.globalVariables = new ArrayList<StacksGlobalTag>(0);
		this.methods = new ArrayList<StacksMethodTag>(0);
		this.parameters = new ArrayList<StacksParameterTag>(0);
		this.raises = new ArrayList<StacksRaisesTag>(0);
		this.restricts = new ArrayList<StacksRestrictsTag>(0);
		this.returns = new ArrayList<StacksReturnTag>(0);
		this.sees = new ArrayList<StacksSeeTag>(0);
		this.supertypes = new ArrayList<StacksSuperTypeTag>(0);
		this.types = new ArrayList<StacksTypeTag>(0);
	}

	/**
	 * @return the moduleName
	 */
	public A_String moduleName ()
	{
		return moduleName;
	}

	/**
	 * @return
	 * 		The appropriately built {@link AbstractCommentImplementation
	 * 		Comment Implementation}
	 */
	public AbstractCommentImplementation createStacksComment ()
	{

		if (!types.isEmpty() && methods.isEmpty() && globalVariables.isEmpty())
		{
			if (types.size() == 1)
			{
				final CommentSignature signature =
					new CommentSignature(
							types.get(0).typeName().lexeme(),
						moduleName());
				return new ClassCommentImplementation (
					signature,
					authors,
					sees,
					description,
					supertypes,
					fields);
			}
			//TODO Throw Exception
		}

		if (types.isEmpty() && !methods.isEmpty() && globalVariables.isEmpty())
		{
			if (methods.size() > 1)
			{
				//TODO Throw exception
			}
			if (!restricts.isEmpty() && parameters.isEmpty() &&
				forbids.isEmpty())
			{
				final ArrayList<String> orderedInputTypes =
					new ArrayList<String>(0);
				for (final StacksParameterTag param : parameters)
				{
					orderedInputTypes.add(param.paramType().lexeme());
				}

				final SemanticRestrictionCommentSignature signature =
					new SemanticRestrictionCommentSignature(
						methods.get(0).methodName().lexeme(),
						moduleName(),
						orderedInputTypes);

				return new SemanticRestrictionCommentImplementation(signature,
					authors, sees,restricts);
			}

			if (restricts.isEmpty() && !parameters.isEmpty() &&
				forbids.isEmpty())
			{
				final ArrayList<String> orderedInputTypes =
					new ArrayList<String>(0);
				for (final StacksParameterTag param : parameters)
				{
					orderedInputTypes.add(param.paramType().lexeme());
				}

				final MethodCommentSignature signature =
					new MethodCommentSignature(
						methods.get(0).methodName().lexeme(),
						moduleName(),
						orderedInputTypes,
						returns.get(0).returnType().lexeme());

				return new MethodCommentImplementation(signature,
					authors, sees,description,parameters,returns.get(0),raises);

			}

			if (restricts.isEmpty() && parameters.isEmpty() &&
				!forbids.isEmpty())
			{
				if (forbids.size() == 1)
				{
					final CommentSignature signature =
						new CommentSignature(
							methods.get(0).methodName().lexeme(),
							moduleName());

					return new GrammaticalRestrictionCommentImplementation(
						signature, authors, sees, forbids.get(0));
				}
				//TODO throws exception.
			}

			//TODO throw exception
		}

		if (types.isEmpty() && methods.isEmpty() && !globalVariables.isEmpty())
		{
			if (globalVariables.size() == 1)
			{
				final GlobalCommentSignature signature =
					new GlobalCommentSignature(
						globalVariables.get(0).globalName().lexeme(),
						moduleName(),
						globalVariables.get(0).globalType().lexeme());

				return new GlobalCommentImplementation(signature, authors, sees,
					description, globalVariables.get(0));
			}
			//TODO Throw error
		}
		return null;

	}
}
