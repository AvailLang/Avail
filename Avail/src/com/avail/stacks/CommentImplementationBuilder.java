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
 * A builder class for an {@link AbstractCommentImplementation}.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
public class CommentImplementationBuilder
{
//TODO go through and transform all ClassCast exceptions to
	//StacksCommentBuilderException by catching them.
	//Still need to handle StacksCommentBuilderException
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
	private ArrayList<AbstractStacksToken> description;

	/**
	 * @param list
	 * @throws ClassCastException
	 */
	public void addStacksCommentDescription(
		 final ArrayList<AbstractStacksToken> list)
			 throws ClassCastException
	{
		description = list;
	}

	/**
	 * The field keyword indicates a field in the class implementation.
	 */
	private final ArrayList<StacksFieldTag> fields;

	/**
	 * @param tagContentTokens
	 * @throws ClassCastException
	 * @throws StacksCommentBuilderException
	 */
	public void addStacksFieldTag(
		 final ArrayList<AbstractStacksToken> tagContentTokens)
			 throws ClassCastException, StacksCommentBuilderException
	{
		final int tokenCount = tagContentTokens.size();

		try
		{
			if (tokenCount < 2)
			{
				final String errorMessage = String.format("Stacks field tag in " +
						"comment on line number, %d, in module, %s, has too few " +
						"@field components", commentStartLine(),
						moduleName().asNativeString());
				throw new StacksCommentBuilderException(errorMessage, this);
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
					new ArrayList<AbstractStacksToken>(
						tagContentTokens.subList(2, tokenCount));
				fields.add(new StacksFieldTag (tempName, tempType,rest));
			}
		}
		catch (final ClassCastException e)
		{
			final String errorMessage = String.format("Token, %s,  " +
				"comment on line number, %d, in module, %s, in the @field " +
				"section failed casting to QuotedStacksToken ",
				tagContentTokens.get(0).lexeme(),
				commentStartLine(),
				moduleName().asNativeString());
			throw new StacksCommentBuilderException(errorMessage, this);
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
	 * @throws StacksCommentBuilderException
	 */
	public void addStacksForbidTag(
		 final ArrayList<AbstractStacksToken> tagContentTokens)
			 throws ClassCastException, StacksCommentBuilderException
	{
		try
		{
			final ArrayList<QuotedStacksToken> tempTokens =
				new ArrayList<QuotedStacksToken>(1);

			for (final AbstractStacksToken token : tagContentTokens)
			{
				tempTokens.add((QuotedStacksToken) token);
			}

			forbids.add(new StacksForbidsTag (tempTokens));
		}
		catch (final ClassCastException e)
		{
			final String errorMessage = String.format("Token, %s,  " +
				"comment on line number, %d, in module, %s, in the @forbids " +
				"section failed casting to QuotedStacksToken ",
				tagContentTokens.get(0).lexeme(),
				commentStartLine(),
				moduleName().asNativeString());
			throw new StacksCommentBuilderException(errorMessage, this);
		}
	}

	/**
	 * The method keyword indicates the name of the method implementation.
	 */
	private final ArrayList<StacksGlobalTag> globalVariables;

	/**
	 * @param tagContentTokens
	 * @throws ClassCastException
	 * @throws StacksCommentBuilderException
	 */
	public void addStacksGlobalTag (
		final ArrayList<AbstractStacksToken> tagContentTokens)
			 throws ClassCastException, StacksCommentBuilderException
	{
		final int tokenCount = tagContentTokens.size();

		try
		{
			if (tokenCount < 2)
			{
				final String errorMessage = String.format("Stacks field tag in " +
					"comment on line number, %d, in module, %s, has too few " +
					"@global components", commentStartLine(),
					moduleName().asNativeString());
				throw new StacksCommentBuilderException(errorMessage, this);
			}

			final QuotedStacksToken tempName =
				(QuotedStacksToken) tagContentTokens.get(0);

			final QuotedStacksToken tempType =
				(QuotedStacksToken) tagContentTokens.get(1);

				globalVariables.add(new StacksGlobalTag (tempName, tempType));
		}
		catch (final ClassCastException e)
		{
			final String errorMessage = String.format("Token, %s,  " +
				"comment on line number, %d, in module, %s, in the @global " +
				"section failed casting to QuotedStacksToken ",
				tagContentTokens.get(0).lexeme(),
				commentStartLine(),
				moduleName().asNativeString());
			throw new StacksCommentBuilderException(errorMessage, this);
		}
	}

	/**
	 * The method keyword indicates the name of the method implementation.
	 */
	private final ArrayList<StacksMethodTag> methods;

	/**
	 * @param tagContentTokens
	 * @throws ClassCastException
	 * @throws StacksCommentBuilderException
	 */
	public void addStacksMethodTag(
		 final ArrayList<AbstractStacksToken> tagContentTokens)
			 throws ClassCastException, StacksCommentBuilderException
	{
		try
		{
			if (tagContentTokens.size() == 1)
			{
				methods.add(new StacksMethodTag (
						(QuotedStacksToken) tagContentTokens.get(0)));
			}
			else
			{
				final String errorMessage = String.format("Stacks field tag in " +
					"comment on line number, %d, in module, %s, has wrong # of " +
					"@method components", commentStartLine(),
					moduleName().asNativeString());
				throw new StacksCommentBuilderException(errorMessage, this);
			}
		}
		catch (final ClassCastException e)
		{
			final String errorMessage = String.format("Token, %s,  " +
				"comment on line number, %d, in module, %s, in the @method " +
				"section failed casting to QuotedStacksToken ",
				tagContentTokens.get(0).lexeme(),
				commentStartLine(),
				moduleName().asNativeString());
			throw new StacksCommentBuilderException(errorMessage, this);
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
	 * @throws StacksCommentBuilderException
	 */
	public void addStacksParameterTag(
		 final ArrayList<AbstractStacksToken> tagContentTokens)
			 throws ClassCastException, StacksCommentBuilderException
	{
		final int tokenCount = tagContentTokens.size();
		if (tokenCount < 2)
		{
			final String errorMessage = String.format("Stacks field tag " +
				"in comment on line number, %d, in module, %s, has too " +
				"few @param components", commentStartLine(),
				moduleName().asNativeString());
			throw new StacksCommentBuilderException(errorMessage, this);
		}

		final QuotedStacksToken tempName;
		try
		{
			tempName = (QuotedStacksToken) tagContentTokens.get(0);
		}
		catch (final ClassCastException e)
		{
			final String errorMessage = String.format("Token, %s,  " +
				"comment on line number, %d, in module, %s, in the @param " +
				"section failed casting to QuotedStacksToken ",
				tagContentTokens.get(0).lexeme(),
				commentStartLine(),
				moduleName().asNativeString());
			throw new StacksCommentBuilderException(errorMessage, this);
		}

		final QuotedStacksToken tempType;
		try
		{
			tempType = (QuotedStacksToken) tagContentTokens.get(1);
		}
		catch (final ClassCastException e)
		{
			final String errorMessage = String.format("Token, %s,  " +
				"comment on line number, %d, in module, %s, in the @param " +
				"section failed casting to QuotedStacksToken ",
				tagContentTokens.get(1).lexeme(),
				commentStartLine(),
				moduleName().asNativeString());
			throw new StacksCommentBuilderException(errorMessage, this);
		}

		if (tokenCount == 2)
		{
			parameters.add(new StacksParameterTag (tempName, tempType,
				new ArrayList<AbstractStacksToken>(0)));
		}
		else
		{
			final ArrayList<AbstractStacksToken> rest =
				new ArrayList<AbstractStacksToken>(
					tagContentTokens.subList(2, tokenCount));
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
	 * @throws StacksCommentBuilderException
	 */
	public void addStacksRaisesTag(
		 final ArrayList<AbstractStacksToken> tagContentTokens)
			 throws ClassCastException, StacksCommentBuilderException
	{
		final int tokenCount = tagContentTokens.size();

		try
		{
			if (tokenCount < 1)
			{
				final String errorMessage = String.format("Stacks field tag " +
					"in comment on line number, %d, in module, %s, has too " +
					"few @raises components", commentStartLine(),
					moduleName().asNativeString());
				throw new StacksCommentBuilderException(errorMessage, this);
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
					new ArrayList<AbstractStacksToken>(
						tagContentTokens.subList(1, tokenCount));
				raises.add(new StacksRaisesTag (tempName,rest));
			}
		}
		catch (final ClassCastException e)
		{
			final String errorMessage = String.format("Token, %s,  " +
				"comment on line number, %d, in module, %s, in the " +
				"@raises section failed final casting to QuotedStacksToken ",
				tagContentTokens.get(0).lexeme(),
				commentStartLine(),
				moduleName().asNativeString());
			throw new StacksCommentBuilderException(errorMessage, this);
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
	 * @throws StacksCommentBuilderException
	 */
	public void addStacksRestrictsTag(
		 final ArrayList<AbstractStacksToken> tagContentTokens)
			 throws ClassCastException, StacksCommentBuilderException
	{
		final int tokenCount = tagContentTokens.size();

		try
		{
			if (tokenCount < 1)
			{
				final String errorMessage = String.format("Stacks field tag in " +
					"comment on line number, %d, in module, %s, has too few " +
					"@restricts components", commentStartLine(),
					moduleName().asNativeString());
				throw new StacksCommentBuilderException(errorMessage, this);
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
					new ArrayList<AbstractStacksToken>(
						tagContentTokens.subList(1, tokenCount));
				restricts.add(new StacksRestrictsTag (tempName,rest));
			}
		}
		catch (final ClassCastException e)
		{
			final String errorMessage = String.format("Token, %s,  " +
				"comment on line number, %d, in module, %s, in the " +
				"@restricts section failed casting to QuotedStacksToken ",
				tagContentTokens.get(0).lexeme(),
				commentStartLine(),
				moduleName().asNativeString());
			throw new StacksCommentBuilderException(errorMessage, this);
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
	 * @throws StacksCommentBuilderException
	 */
	public void addStacksReturnsTag(
		 final ArrayList<AbstractStacksToken> tagContentTokens)
			 throws ClassCastException, StacksCommentBuilderException
	{
		final int tokenCount = tagContentTokens.size();

		try
		{
			if (tokenCount < 1)
			{
				final String errorMessage = String.format("Stacks field tag in " +
					"comment on line number, %d, in module, %s, has too few " +
					"@returns components", commentStartLine(),
					moduleName().asNativeString());
				throw new StacksCommentBuilderException(errorMessage, this);
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
					new ArrayList<AbstractStacksToken>(
						tagContentTokens.subList(1, tokenCount));
				returns.add(new StacksReturnTag (tempName,rest));
			}
		}
		catch (final ClassCastException e)
		{
			final String errorMessage = String.format("Token, %s,  " +
				"comment on line number, %d, in module, %s, in the @returns " +
				"section failed casting to QuotedStacksToken ",
				tagContentTokens.get(0).lexeme(),
				commentStartLine(),
				moduleName().asNativeString());
			throw new StacksCommentBuilderException(errorMessage, this);
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
	 * @throws StacksCommentBuilderException
	 */
	public void addStacksSeesTag(
		 final ArrayList<AbstractStacksToken> tagContentTokens)
			 throws ClassCastException, StacksCommentBuilderException
	{
		try
		{
			if (tagContentTokens.size() == 1)
			{
				sees.add(new StacksSeeTag (
						(RegionStacksToken) tagContentTokens.get(0)));
			}
			else
			{
				final String errorMessage = String.format("Stacks field tag " +
					"in comment on line number, %d, in module, %s, has " +
					"wrong # of @sees components", commentStartLine(),
					moduleName().asNativeString());
				throw new StacksCommentBuilderException(errorMessage, this);
			}
		}
		catch (final ClassCastException e)
		{
			final String errorMessage = String.format("Token, %s,  " +
				"comment on line number, %d, in module, %s, in the @sees " +
				"section failed casting to RegionStacksToken ",
				tagContentTokens.get(0).lexeme(),
				commentStartLine(),
				moduleName().asNativeString());
			throw new StacksCommentBuilderException(errorMessage, this);
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
	 * @throws StacksCommentBuilderException
	 */
	public void addStacksSupertypeTag(
		 final ArrayList<AbstractStacksToken> tagContentTokens)
			 throws ClassCastException, StacksCommentBuilderException
	{
		try
		{
			if (tagContentTokens.size() == 1)
			{
				supertypes.add(new StacksSuperTypeTag (
						(QuotedStacksToken) tagContentTokens.get(0)));
			}
			else
			{
				final String errorMessage = String.format("Stacks field tag in " +
					"comment on line number, %d, in module, %s, has wrong # of " +
					"@supertype components", commentStartLine(),
					moduleName().asNativeString());
				throw new StacksCommentBuilderException(errorMessage, this);
			}
		}
		catch (final ClassCastException e)
		{
			final String errorMessage = String.format("Token, %s,  " +
				"comment on line number, %d, in module, %s, in the " +
				"@supertype section failed casting to QuotedStacksToken ",
				tagContentTokens.get(0).lexeme(),
				commentStartLine(),
				moduleName().asNativeString());
			throw new StacksCommentBuilderException(errorMessage, this);
		}
	}

	/**
	 * The type keyword indicates the name of the class implementation.
	 */
	private final ArrayList<StacksTypeTag> types;

	/**
	 * @param tagContentTokens
	 * @throws ClassCastException
	 * @throws StacksCommentBuilderException
	 */
	public void addStacksTypeTag(
		 final ArrayList<AbstractStacksToken> tagContentTokens)
			 throws ClassCastException, StacksCommentBuilderException
	{
		try
		{
			if (tagContentTokens.size() == 1)
			{
				types.add(new StacksTypeTag (
						(QuotedStacksToken) tagContentTokens.get(0)));
			}
			else
			{
				final String errorMessage = String.format("Stacks field tag " +
					"in comment on line number, %d, in module, %s, has wrong " +
					"# of @type components", commentStartLine(),
					moduleName().asNativeString());
				throw new StacksCommentBuilderException(errorMessage, this);
			}
		}
		catch (final ClassCastException e)
		{
			final String errorMessage = String.format("Token, %s,  " +
				"comment on line number, %d, in module, %s, in the @returns " +
				"section failed casting to QuotedStacksToken ",
				tagContentTokens.get(0).lexeme(),
				commentStartLine(),
				moduleName().asNativeString());
			throw new StacksCommentBuilderException(errorMessage, this);
		}
	}

	/**
	 * The name of the module the comment originates from.
	 */
	private final A_String moduleName;

	/**
	 * The start line in the module the comment being parsed appears.
	 */
	private final int commentStartLine;

	/**
	 * Construct a new {@link CommentImplementationBuilder}.
	 * @param moduleName
	 * 		The name of the module the comment is in.
	 * @param commentStartLine
	 * 		The start line in the module of the comment being built
	 *
	 */
	private CommentImplementationBuilder (final A_String moduleName,
		final int commentStartLine)
	{
		this.moduleName = moduleName;
		this.commentStartLine = commentStartLine;
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
	 *
	 * @param moduleName
	 * 		The name of the module the comment is in.
	 * @param commentStartLine
	 * 		The start line in the module of the comment being built
	 * @return
	 * 		A {@link CommentImplementationBuilder}
	 * @throws StacksCommentBuilderException
	 */
	public static CommentImplementationBuilder createBuilder (
		final A_String moduleName,
		final int commentStartLine)
			throws StacksCommentBuilderException
	{
		return new CommentImplementationBuilder (moduleName, commentStartLine);
	}

	/**
	 * @return the commentStartLine
	 */
	public int commentStartLine ()
	{
		return commentStartLine;
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
	 * @throws StacksCommentBuilderException
	 */
	public AbstractCommentImplementation createStacksComment ()
		throws StacksCommentBuilderException
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
					commentStartLine (),
					authors,
					sees,
					description,
					supertypes,
					fields);
			}
			final String errorMessage = String.format("Stacks class comment " +
				"on line number, %d, in module, %s, has wrong # of " +
				"@types top-level tags", commentStartLine(),
				moduleName().asNativeString());
			throw new StacksCommentBuilderException(errorMessage, this);
		}

		if (types.isEmpty() && !methods.isEmpty() && globalVariables.isEmpty())
		{
			if (methods.size() > 1)
			{
				final String errorMessage = String.format("Stacks commment " +
					"on line number, %d, in module, %s, has wrong # of " +
					"@method top-level tags", commentStartLine(),
					moduleName().asNativeString());
				throw new StacksCommentBuilderException(errorMessage, this);
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
					commentStartLine (), authors, sees,restricts);
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
					commentStartLine (), authors, sees, description, parameters,
					returns.get(0), raises);

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
						signature, commentStartLine (), authors, sees,
						forbids.get(0));
				}
				final String errorMessage = String.format("Stacks " +
					"grammatical restriction commment on line number, %d, " +
					"in module, %s, has wrong # of @forbids top-level tags",
					commentStartLine(),
					moduleName().asNativeString());
				throw new StacksCommentBuilderException(errorMessage, this);
			}

			//TODO throw exception maybe?
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

				return new GlobalCommentImplementation(signature,
					commentStartLine (), authors, sees,description,
					globalVariables.get(0));
			}
			final String errorMessage = String.format("Stacks " +
				"global module variable commment on line number, %d, " +
				"in module, %s, has wrong # of @global top-level tags",
				commentStartLine(),
				moduleName().asNativeString());
			throw new StacksCommentBuilderException(errorMessage, this);
		}
		return null;
	}
}
