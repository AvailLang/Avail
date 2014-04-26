/**
 * BracketedStacksToken.java
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

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A stacks token representing a bracketed region in the comment.  This region
 * generally contains some sort of action such as a link.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
public class BracketedStacksToken extends RegionStacksToken
{
	/**
	 * The tokens that have been parsed so far.
	 */
	List<AbstractStacksToken> subTokens;


	/**
	 * Construct a new {@link BracketedStacksToken}.
	 *
	 * @param string
	 * 		The string to be tokenized.
	 * @param lineNumber
	 * 		The line number where the token occurs/begins
	 * @param position
	 * 		The absolute start position of the token
	 * @param startOfTokenLinePostion
	 * 		The position on the line where the token starts.
	 * @param moduleName
	 * @throws StacksScannerException
	 */
	public BracketedStacksToken (
		final String string,
		final int lineNumber,
		final int position,
		final int startOfTokenLinePostion,
		final String moduleName) throws StacksScannerException
	{
		super(string, lineNumber, position,
			startOfTokenLinePostion, moduleName, '\"', '\"');
		this.subTokens = StacksBracketScanner.scanBracketString(this);
	}
	/**
	 *  Statically create a new {@link BracketedStacksToken}.
	 * @param string
	 * 		The string to be tokenized.
	 * @param lineNumber
	 * 		The line number where the token occurs/begins
	 * @param position
	 * 		The absolute start position of the token
	 * @param startOfTokenLinePostion
	 * 		The position on the line where the token starts.
	 * @param moduleName
	 * 		The name of the module the token is in.
	 * @return a new {@link BracketedStacksToken stacks token}
	 * @throws StacksScannerException
	 */
	public static BracketedStacksToken create (
		final String string,
		final int lineNumber,
		final int position,
		final int startOfTokenLinePostion,
		final String moduleName) throws StacksScannerException
	{
		return new BracketedStacksToken(
			string, lineNumber, position, startOfTokenLinePostion, moduleName);
	}

	@Override
	public String toHTML(final HTMLFileMap htmlFileMap, final int hashID,
		final StacksErrorLog errorLog)
	{
		//TODO update with parsed tags with appropriate links.

		final StacksKeyword keyword = StacksKeyword.keywordTable
			.get(this.subTokens.get(0).lexeme());
		if (keyword == null)
		{
			//TODO problem
			return "";
		}

		return keyword.toHTML(this, htmlFileMap, hashID, errorLog);
	}

	/**
	 *
	 * The collection of keywords that a comment can have.
	 *
	 * @author Richard Arriaga &lt;rich@availlang.org&gt;
	 */
	private enum StacksKeyword
	{
		/**
		 * The link keyword creates an external web link.
		 */
		CODE("@code")
		{
			@Override
			String toHTML(
				final BracketedStacksToken bracketToken,
				final HTMLFileMap htmlFileMap, final int hashID,
				final StacksErrorLog errorLog)
			{
				//TODO address spacing
				final StringBuilder stringBuilder = new StringBuilder();

				stringBuilder.append("<code>");
				final int tokenCount = bracketToken.subTokens.size();
				for (int i = 1; i < tokenCount - 1; i++)
				{
					stringBuilder
						.append(bracketToken
							.subTokens.get(i)
							.toHTML(htmlFileMap, hashID, errorLog))
						.append(" ");
				}

				stringBuilder
					.append(bracketToken
						.subTokens.get(tokenCount - 1)
							.toHTML(htmlFileMap, hashID, errorLog))
					.append("</code>");
				return stringBuilder.toString();
			}
		},

		/**
		 * The field keyword indicates a field in the class implementation.
		 */
		FIELD("@field")
		{
			@Override
			String toHTML(
				final BracketedStacksToken bracketToken,
				final HTMLFileMap htmlFileMap, final int hashID,
				final StacksErrorLog errorLog)
			{
				if (bracketToken.subTokens.size() == 1)
				{
					final String errorMessage = String.format("\n<li>"
						+ "<strong>%s</strong><em> Line #: %d</em>: "
						+ "Malformed @field tag section; "
						+ "expected link content immediately "
						+ "following the @field tag, but tag was empty.</li>",
						bracketToken.moduleName,
						bracketToken.lineNumber());

					final ByteBuffer errorBuffer = ByteBuffer.wrap(
						errorMessage.toString()
							.getBytes(StandardCharsets.UTF_8));
					errorLog.addLogEntry(errorBuffer,1);

					return "";
				}
				/**
				 * The method keyword indicates the name of the method
				 * implementation.
				 */
				final List<AbstractStacksToken> links =
					bracketToken.subTokens
						.subList(1, bracketToken.subTokens.size());

				if (links.size() == 1)
				{
					try
					{
						final QuotedStacksToken link =
							(QuotedStacksToken) links.get(0);


						final StringBuilder stringBuilder =
							new StringBuilder();
						stringBuilder.append("<a href=\"#")
							.append(link.lexeme).append(hashID)
							.append("\">")
							.append(link.lexeme())
							.append("</a>");
						return stringBuilder.toString();
					}
					catch (final ClassCastException e)
					{
						final String errorMessage = String.format("\n<li>"
							+ "<strong>%s</strong><em> Line #: %d</em>: "
							+ "Malformed @field tag section; "
							+ "expected a quoted link immediately "
							+ "following the @field tag.</li>",
							bracketToken.moduleName,
							bracketToken.lineNumber());

						final ByteBuffer errorBuffer = ByteBuffer.wrap(
							errorMessage.toString()
								.getBytes(StandardCharsets.UTF_8));
						errorLog.addLogEntry(errorBuffer,1);

						return "";
					}
				}

				return "<code>" + links.get(0).lexeme() + "</code>";
			}
		},

		/**
		 * The method keyword indicates the name of the method implementation.
		 */
		GLOBAL("@global")
		{
			@Override
			String toHTML(
				final BracketedStacksToken bracketToken,
				final HTMLFileMap htmlFileMap, final int hashID,
				final StacksErrorLog errorLog)
			{
				if (bracketToken.subTokens.size() == 1)
				{
					final String errorMessage = String.format("\n<li>"
						+ "<strong>%s</strong><em> Line #: %d</em>: "
						+ "Malformed @global tag section; "
						+ "expected link content immediately "
						+ "following the @global tag, but tag was empty.</li>",
						bracketToken.moduleName,
						bracketToken.lineNumber());

					final ByteBuffer errorBuffer = ByteBuffer.wrap(
						errorMessage.toString()
							.getBytes(StandardCharsets.UTF_8));
					errorLog.addLogEntry(errorBuffer,1);

					return "";
				}
				/**
				 * The method keyword indicates the name of the method
				 * implementation.
				 */
				final List<AbstractStacksToken> links =
					bracketToken.subTokens
						.subList(1, bracketToken.subTokens.size());

				if (links.size() == 1)
				{
					try
					{
						final QuotedStacksToken link =
							(QuotedStacksToken) links.get(0);

						if (htmlFileMap.internalLinks()
							.containsKey(link.lexeme()))
						{
							final StringBuilder stringBuilder =
								new StringBuilder();
							stringBuilder
								.append("<a ng-click=\"changeLinkValue('")
								.append(htmlFileMap.internalLinks()
									.get(link.lexeme()))
								.append("')\" href=\"")
								.append(htmlFileMap.internalLinks()
									.get(link.lexeme()))
								.append("\">")
								.append(link.lexeme())
								.append("</a>");
							return stringBuilder.toString();
						}
					}
					catch (final ClassCastException e)
					{
						final String errorMessage = String.format("\n<li>"
							+ "<strong>%s</strong><em> Line #: %d</em>: "
							+ "Malformed @global tag section; "
							+ "expected a quoted link immediately "
							+ "following the @global tag.</li>",
							bracketToken.moduleName,
							bracketToken.lineNumber());

						final ByteBuffer errorBuffer = ByteBuffer.wrap(
							errorMessage.toString()
								.getBytes(StandardCharsets.UTF_8));
						errorLog.addLogEntry(errorBuffer,1);

						return "";
					}
				}

				return "<code>" + links.get(0).lexeme() + "</code>";
			}
		},

		/**
		 * The link keyword creates an external web link.
		 */
		LINK("@link")
		{
			@Override
			String toHTML(
				final BracketedStacksToken bracketToken,
				final HTMLFileMap htmlFileMap, final int hashID,
				final StacksErrorLog errorLog)
			{

				if (bracketToken.subTokens.size() == 1)
				{
					final String errorMessage = String.format("\n<li>"
						+ "<strong>%s</strong><em> Line #: %d</em>: "
						+ "Malformed @link tag section; "
						+ "expected link content immediately "
						+ "following the @link tag, but tag was empty.</li>",
						bracketToken.moduleName,
						bracketToken.lineNumber());

					final ByteBuffer errorBuffer = ByteBuffer.wrap(
						errorMessage.toString()
							.getBytes(StandardCharsets.UTF_8));
					errorLog.addLogEntry(errorBuffer,1);

					return "";
				}
				/**
				 * The method keyword indicates the name of the method
				 * implementation.
				 */
				final List<AbstractStacksToken> links =
					bracketToken.subTokens
						.subList(1, bracketToken.subTokens.size());

				if (links.size() == 1)
				{
					try
					{
						return new StacksLinkTag (
							(QuotedStacksToken) links.get(0))
								.toHTML(htmlFileMap, hashID, errorLog);
					}
					catch (final ClassCastException e)
					{
						final String errorMessage = String.format("\n<li>"
							+ "<strong>%s</strong><em> Line #: %d</em>: "
							+ "Malformed @link tag section; "
							+ "expected a quoted link immediately "
							+ "following the @link tag.</li>",
							bracketToken.moduleName,
							bracketToken.lineNumber());

						final ByteBuffer errorBuffer = ByteBuffer.wrap(
							errorMessage.toString()
								.getBytes(StandardCharsets.UTF_8));
						errorLog.addLogEntry(errorBuffer,1);

						return "";
					}
				}
				try
				{
					final QuotedStacksToken link =
						(QuotedStacksToken) links.get(0);

					return new StacksLinkTag (link,
						links.subList(1, links.size()))
							.toHTML(htmlFileMap, hashID, errorLog);
				}
				catch (final ClassCastException e)
				{
					final String errorMessage = String.format("\n<li>"
						+ "<strong>%s</strong><em> Line #: %d</em>: "
						+ "Malformed @link tag section; "
						+ "expected a quoted link immediately "
						+ "following the @link tag.</li>",
						bracketToken.moduleName,
						bracketToken.lineNumber());

					final ByteBuffer errorBuffer = ByteBuffer.wrap(
						errorMessage.toString()
							.getBytes(StandardCharsets.UTF_8));
					errorLog.addLogEntry(errorBuffer,1);

					return "";
				}
			}
		},

		/**
		 * The method keyword indicates the name of the method implementation.
		 */
		METHOD("@method")
		{
			@Override
			String toHTML(
				final BracketedStacksToken bracketToken,
				final HTMLFileMap htmlFileMap, final int hashID,
				final StacksErrorLog errorLog)
			{
				if (bracketToken.subTokens.size() == 1)
				{
					final String errorMessage = String.format("\n<li>"
						+ "<strong>%s</strong><em> Line #: %d</em>: "
						+ "Malformed @method tag section; "
						+ "expected link content immediately "
						+ "following the @method tag, but tag was empty.</li>",
						bracketToken.moduleName,
						bracketToken.lineNumber());

					final ByteBuffer errorBuffer = ByteBuffer.wrap(
						errorMessage.toString()
							.getBytes(StandardCharsets.UTF_8));
					errorLog.addLogEntry(errorBuffer,1);

					return "";
				}
				/**
				 * The method keyword indicates the name of the method
				 * implementation.
				 */
				final List<AbstractStacksToken> links =
					bracketToken.subTokens
						.subList(1, bracketToken.subTokens.size());

				if (links.size() == 1)
				{
					try
					{
						final QuotedStacksToken link =
							(QuotedStacksToken) links.get(0);

						if (htmlFileMap.internalLinks()
							.containsKey(link.lexeme()))
						{
							final StringBuilder stringBuilder =
								new StringBuilder();
							stringBuilder
								.append("<a ng-click=\"changeLinkValue('")
								.append(htmlFileMap.internalLinks()
									.get(link.lexeme()))
								.append("')\" href=\"")
								.append(htmlFileMap.internalLinks()
									.get(link.lexeme()))
								.append("\">")
								.append(link.lexeme())
								.append("</a>");
							return stringBuilder.toString();
						}
					}
					catch (final ClassCastException e)
					{
						final String errorMessage = String.format("\n<li>"
							+ "<strong>%s</strong><em> Line #: %d</em>: "
							+ "Malformed @method tag section; "
							+ "expected a quoted link immediately "
							+ "following the @method tag.</li>",
							bracketToken.moduleName,
							bracketToken.lineNumber());

						final ByteBuffer errorBuffer = ByteBuffer.wrap(
							errorMessage.toString()
								.getBytes(StandardCharsets.UTF_8));
						errorLog.addLogEntry(errorBuffer,1);

						return "";
					}
				}
				//for links.size() >= 2

					final StringBuilder linkBuilder = new StringBuilder();
					final int listSize = links.size();
					if (listSize >= 2)
					{
						for (int i = 1; i < listSize - 1; i++)
						{
							linkBuilder
								.append(links.get(i).toHTML(htmlFileMap,
									hashID, errorLog))
								.append(" ");
						}
						linkBuilder
							.append(links.get(listSize - 1)
								.toHTML(htmlFileMap, hashID, errorLog));
					}
					final QuotedStacksToken link =
						(QuotedStacksToken) links.get(0);

					if (htmlFileMap.internalLinks()
						.containsKey(link.lexeme()))
					{
						final StringBuilder stringBuilder =
							new StringBuilder();
						stringBuilder
							.append("<a ng-click=\"changeLinkValue('")
							.append(htmlFileMap.internalLinks()
								.get(link.lexeme()))
							.append("')\" href=\"")
							.append(htmlFileMap.internalLinks()
								.get(link.lexeme()))
							.append("\">")
							.append(linkBuilder)
							.append("</a>");
						return stringBuilder.toString();
					}

				return "<code>" + links.get(0).lexeme() + "</code>";
			}
		},

		/**
		 * The parameter keyword indicates an input for the method
		 * implementation.
		 */
		PARAMETER("@param")
		{
			@Override
			String toHTML(
				final BracketedStacksToken bracketToken,
				final HTMLFileMap htmlFileMap, final int hashID,
				final StacksErrorLog errorLog)
			{
				if (bracketToken.subTokens.size() == 1)
				{
					final String errorMessage = String.format("\n<li>"
						+ "<strong>%s</strong><em> Line #: %d</em>: "
						+ "Malformed @param tag section; "
						+ "expected link content immediately "
						+ "following the @param tag, but tag was empty.</li>",
						bracketToken.moduleName,
						bracketToken.lineNumber());

					final ByteBuffer errorBuffer = ByteBuffer.wrap(
						errorMessage.toString()
							.getBytes(StandardCharsets.UTF_8));
					errorLog.addLogEntry(errorBuffer,1);

					return "";
				}
				/**
				 * The method keyword indicates the name of the method
				 * implementation.
				 */
				final List<AbstractStacksToken> links =
					bracketToken.subTokens
						.subList(1, bracketToken.subTokens.size());

				if (links.size() == 1)
				{
					try
					{
						final QuotedStacksToken link =
							(QuotedStacksToken) links.get(0);


						final StringBuilder stringBuilder =
							new StringBuilder();
						stringBuilder.append("<a href=\"#")
							.append(link.lexeme).append(hashID)
							.append("\">")
							.append(link.lexeme())
							.append("</a>");
						return stringBuilder.toString();
					}
					catch (final ClassCastException e)
					{
						final String errorMessage = String.format("\n<li>"
							+ "<strong>%s</strong><em> Line #: %d</em>: "
							+ "Malformed @param tag section; "
							+ "expected a quoted link immediately "
							+ "following the @param tag.</li>\n",
							bracketToken.moduleName,
							bracketToken.lineNumber());

						final ByteBuffer errorBuffer = ByteBuffer.wrap(
							errorMessage.toString()
								.getBytes(StandardCharsets.UTF_8));
						errorLog.addLogEntry(errorBuffer,1);

						return "";
					}
				}

				return "<code>" + links.get(0).lexeme() + "</code>";
			}
		},

		/**
		 * The restricts keyword indicates the input types used by the method
		 * implementation's semantic restriction.
		 */
		RESTRICTS("@restricts")
		{
			@Override
			String toHTML(
				final BracketedStacksToken bracketToken,
				final HTMLFileMap htmlFileMap, final int hashID,
				final StacksErrorLog errorLog)
			{
				if (bracketToken.subTokens.size() == 1)
				{
					final String errorMessage = String.format("\n<li>"
						+ "<strong>%s</strong><em> Line #: %d</em>: "
						+ "Malformed @restricts tag section; "
						+ "expected link content immediately "
						+ "following the @restricts tag, but tag was empty."
						+ "</li>\n",
						bracketToken.moduleName,
						bracketToken.lineNumber());

					final ByteBuffer errorBuffer = ByteBuffer.wrap(
						errorMessage.toString()
							.getBytes(StandardCharsets.UTF_8));
					errorLog.addLogEntry(errorBuffer,1);

					return "";
				}
				/**
				 * The method keyword indicates the name of the method
				 * implementation.
				 */
				final List<AbstractStacksToken> links =
					bracketToken.subTokens
						.subList(1, bracketToken.subTokens.size());

				if (links.size() == 1)
				{
					try
					{
						final QuotedStacksToken link =
							(QuotedStacksToken) links.get(0);


						final StringBuilder stringBuilder =
							new StringBuilder();
						stringBuilder.append("<a href=\"#")
							.append(link.lexeme).append(hashID)
							.append("\">")
							.append(link.lexeme())
							.append("</a>");
						return stringBuilder.toString();
					}
					catch (final ClassCastException e)
					{
						final String errorMessage = String.format("\n<li>"
							+ "<strong>%s</strong><em> Line #: %d</em>: "
							+ "Malformed @restricts tag section; "
							+ "expected a quoted link immediately "
							+ "following the @restricts tag.</li>\n",
							bracketToken.moduleName,
							bracketToken.lineNumber());

						final ByteBuffer errorBuffer = ByteBuffer.wrap(
							errorMessage.toString()
								.getBytes(StandardCharsets.UTF_8));
						errorLog.addLogEntry(errorBuffer,1);

						return "";
					}
				}

				return "<code>" + links.get(0).lexeme() + "</code>";
			}
		},

		/**
		 * The see keyword refers the reader to something else.  Not
		 * inherently linked.
		 */
		SEE("@see")
		{
			@Override
			String toHTML(
				final BracketedStacksToken bracketToken,
				final HTMLFileMap htmlFileMap, final int hashID,
				final StacksErrorLog errorLog)
			{

				if (bracketToken.subTokens.size() == 1)
				{
					final String errorMessage = String.format("\n<li>"
						+ "<strong>%s</strong><em> Line #: %d</em>: "
						+ "Malformed @see tag section; "
						+ "expected link content immediately "
						+ "following the @see tag, but tag was empty.</li>",
						bracketToken.moduleName,
						bracketToken.lineNumber());

					final ByteBuffer errorBuffer = ByteBuffer.wrap(
						errorMessage.toString()
							.getBytes(StandardCharsets.UTF_8));
					errorLog.addLogEntry(errorBuffer,1);

					return "";
				}
				/**
				 * The method keyword indicates the name of the method
				 * implementation.
				 */
				final List<AbstractStacksToken> links =
					bracketToken.subTokens
						.subList(1, bracketToken.subTokens.size());

				if (links.size() == 1)
				{
					try
					{
						return new StacksLinkTag (
							(QuotedStacksToken) links.get(0))
								.toHTML(htmlFileMap, hashID, errorLog);
					}
					catch (final ClassCastException e)
					{
						final String errorMessage = String.format("\n<li>"
							+ "<strong>%s</strong><em> Line #: %d</em>: "
							+ "Malformed @see tag section; "
							+ "expected a quoted link immediately "
							+ "following the @see tag.</li>",
							bracketToken.moduleName,
							bracketToken.lineNumber());

						final ByteBuffer errorBuffer = ByteBuffer.wrap(
							errorMessage.toString()
								.getBytes(StandardCharsets.UTF_8));
						errorLog.addLogEntry(errorBuffer,1);

						return "";
					}
				}
				try
				{
					final RegionStacksToken link =
						(RegionStacksToken) links.get(0);

					return new StacksSeeTag (link)
							.toHTML(htmlFileMap, hashID, errorLog);
				}
				catch (final ClassCastException e)
				{
					final String errorMessage = String.format("\n<li>"
						+ "<strong>%s</strong><em> Line #: %d</em>: "
						+ "Malformed @link tag section; "
						+ "expected a quoted link immediately "
						+ "following the @link tag.</li>",
						bracketToken.moduleName,
						bracketToken.lineNumber());

					final ByteBuffer errorBuffer = ByteBuffer.wrap(
						errorMessage.toString()
							.getBytes(StandardCharsets.UTF_8));
					errorLog.addLogEntry(errorBuffer,1);

					return "";
				}
			}
		},

		/**
		 * The type keyword indicates the name of the class implementation.
		 */
		TYPE("@type")
		{
			@Override
			String toHTML(
				final BracketedStacksToken bracketToken,
				final HTMLFileMap htmlFileMap, final int hashID,
				final StacksErrorLog errorLog)
			{
				if (bracketToken.subTokens.size() == 1)
				{
					final String errorMessage = String.format("\n<li>"
						+ "<strong>%s</strong><em> Line #: %d</em>: "
						+ "Malformed @type tag section; "
						+ "expected link content immediately "
						+ "following the @type tag, but tag was empty.</li>",
						bracketToken.moduleName,
						bracketToken.lineNumber());

					final ByteBuffer errorBuffer = ByteBuffer.wrap(
						errorMessage.toString()
							.getBytes(StandardCharsets.UTF_8));
					errorLog.addLogEntry(errorBuffer,1);

					return "";
				}
				/**
				 * The method keyword indicates the name of the method
				 * implementation.
				 */
				final List<AbstractStacksToken> links =
					bracketToken.subTokens
						.subList(1, bracketToken.subTokens.size());

				if (links.size() == 1)
				{
					try
					{
						final QuotedStacksToken link =
							(QuotedStacksToken) links.get(0);

						if (htmlFileMap.internalLinks()
							.containsKey(link.lexeme()))
						{
							final StringBuilder stringBuilder =
								new StringBuilder();
							stringBuilder
								.append("<a ng-click=\"changeLinkValue('")
								.append(htmlFileMap.internalLinks()
									.get(link.lexeme()))
								.append("')\" href=\"")
								.append(htmlFileMap.internalLinks()
									.get(link.lexeme()))
								.append("\">")
								.append(link.lexeme())
								.append("</a>");
							return stringBuilder.toString();
						}
					}
					catch (final ClassCastException e)
					{
						final String errorMessage = String.format("\n<li>"
							+ "<strong>%s</strong><em> Line #: %d</em>: "
							+ "Malformed @type tag section; "
							+ "expected a quoted link immediately "
							+ "following the @type tag.</li>",
							bracketToken.moduleName,
							bracketToken.lineNumber());

						final ByteBuffer errorBuffer = ByteBuffer.wrap(
							errorMessage.toString()
								.getBytes(StandardCharsets.UTF_8));
						errorLog.addLogEntry(errorBuffer,1);

						return "";
					}
				}

				//for links.size() >= 2

				final StringBuilder linkBuilder = new StringBuilder();
				final int listSize = links.size();
				if (listSize >= 2)
				{
					for (int i = 1; i < listSize - 1; i++)
					{
						linkBuilder
							.append(links.get(i).toHTML(htmlFileMap,
								hashID, errorLog))
							.append(" ");
					}
					linkBuilder
						.append(links.get(listSize - 1)
							.toHTML(htmlFileMap, hashID, errorLog));
				}
				final QuotedStacksToken link =
					(QuotedStacksToken) links.get(0);

				if (htmlFileMap.internalLinks()
					.containsKey(link.lexeme()))
				{
					final StringBuilder stringBuilder =
						new StringBuilder();
					stringBuilder
						.append("<a ng-click=\"changeLinkValue('")
						.append(htmlFileMap.internalLinks()
							.get(link.lexeme()))
						.append("')\" href=\"")
						.append(htmlFileMap.internalLinks()
							.get(link.lexeme()))
						.append("\">")
						.append(linkBuilder)
						.append("</a>");
					return stringBuilder.toString();
				}

			return "<code>" + links.get(0).lexeme() + "</code>";
			}
		};

		/** The string representation of the keyword */
		final String lexeme;

		/**
		 * The constructor of the {@link StacksKeyword}
		 * @param lexeme {@link String} String representation of
		 * the keyword.
		 */
		private StacksKeyword(final String lexeme) {
			this.lexeme = lexeme;
		}

		/**
		 * A {@linkplain Map mapping} from the string lexeme of the keyword to
		 * the {@link Enum StacksKeywords}
		 */
		static final Map<String, StacksKeyword> keywordTable =
			new HashMap<String, StacksKeyword>();

		// Learn the lexeme's of the keywords.
		static
		{
			for (final StacksKeyword keyword : StacksKeyword.values())
			{
				keywordTable.put(keyword.lexeme, keyword);
			}
		}

		/**
		 * Create the appropriate keyword token
		 * @param bracketToken This {@linkplain BracketedStacksToken}
		 * @param htmlFileMap the internal link map
		 * @param hashID The hash id for the implementation this belongs to
		 * @param errorLog The {@linkplain StacksErrorLog}
		 * @return
		 */
		 abstract String toHTML(final BracketedStacksToken bracketToken,
				final HTMLFileMap htmlFileMap, final int hashID,
				final StacksErrorLog errorLog);
	}
}
