/**
 * Catalog.java
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

package com.avail.tools.unicode;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.avail.annotations.InnerAccess;
import com.avail.annotations.Nullable;
import com.avail.utility.IO;
import com.avail.utility.json.JSONArray;
import com.avail.utility.json.JSONData;
import com.avail.utility.json.JSONFriendly;
import com.avail.utility.json.JSONWriter;

/**
 * A {@code Catalog} represents the complete catalog of Unicode characters used
 * by the Avail project.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public class Catalog
{
	/**
	 * The file name extensions of files that should be searched for Unicode
	 * characters.
	 */
	@InnerAccess static final Set<String> extensions =
		new HashSet<>(Arrays.asList(
			"java",
			"avail",
			"properties"));

	/** The source paths to search for matching files. */
	@InnerAccess static final List<Path> rootPaths =
		Arrays.asList(
			// The Java search path.
			Paths.get("src"),
			// The Avail search path.
			Paths.get("distro", "src"));

	/**
	 * The {@linkplain List list} of all {@linkplain Path paths} whose targets
	 * should be searched for Unicode characters.
	 */
	@InnerAccess final List<Path> allPaths = new LinkedList<>();

	/**
	 * Accumulate into {@link #allPaths} every path under the specified root
	 * whose file name extension belongs to {@link #extensions}.
	 *
	 * @param root
	 *        The {@linkplain Path path} beneath which to search for matching
	 *        files.
	 * @throws IOException
	 *         If an I/O exception occurs.
	 */
	private void computeAllPathsBeneath (final Path root) throws IOException
	{
		final Set<FileVisitOption> visitOptions =
			EnumSet.of(FileVisitOption.FOLLOW_LINKS);
		Files.walkFileTree(
			root,
			visitOptions,
			Integer.MAX_VALUE,
			new SimpleFileVisitor<Path>()
			{
				@Override
				public FileVisitResult visitFile (
						final @Nullable Path path,
						final @Nullable BasicFileAttributes attrs)
					throws IOException
				{
					assert path != null;
					final String name = path.getFileName().toString();
					final String[] components = name.split("\\.");
					try
					{
						final String extension =
							components[components.length - 1];
						if (extensions.contains(extension))
						{
							allPaths.add(path);
						}
					}
					catch (final ArrayIndexOutOfBoundsException e)
					{
						// Do nothing.
					}
					return FileVisitResult.CONTINUE;
				}
			});
	}

	/**
	 * Accumulate into {@link #allPaths} every matching path that resides
	 * beneath a {@linkplain #rootPaths root path}.
	 *
	 * @throws IOException
	 *         If an I/O exception occurs.
	 */
	private void computeAllPaths () throws IOException
	{
		for (final Path root : rootPaths)
		{
			computeAllPathsBeneath(root);
		}
	}

	/**
	 * The {@linkplain Set set} of all {@linkplain CharacterInfo Unicode code
	 * points} used by the Avail project.
	 */
	private transient @Nullable Set<CharacterInfo> allCodePoints;

	/**
	 * Accumulate into {@link #allCodePoints} every Unicode code point
	 * encountered within the specified UTF-8 encoded file.
	 *
	 * @param path
	 *        The path to the file that should be scanned.
	 * @throws IOException
	 *         If an I/O exception occurs.
	 */
	private void computeAllCodePointsIn (final Path path) throws IOException
	{
		final Set<CharacterInfo> codePoints = allCodePoints;
		assert codePoints != null;
		final EnumSet<StandardOpenOption> options = EnumSet.of(
			StandardOpenOption.READ);
		final FileChannel in = FileChannel.open(path, options);
		final CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder();
		final ByteBuffer encoded = ByteBuffer.allocateDirect(4096);
		final CharBuffer decoded = CharBuffer.allocate(4096);
		boolean atEnd = false;
		try
		{
			while (!atEnd)
			{
				final int bytesRead = in.read(encoded);
				atEnd = bytesRead == -1;
				encoded.flip();
				CoderResult result = decoder.decode(
					encoded, decoded, atEnd);
				if (atEnd)
				{
					result = decoder.flush(decoded);
				}
				assert !result.isOverflow();
				if (result.isError())
				{
					result.throwException();
				}
				if (encoded.hasRemaining())
				{
					final int delta = encoded.limit() - encoded.position();
					for (int i = 0; i < delta; i++)
					{
						final byte b = encoded.get(encoded.position() + i);
						encoded.put(i, b);
					}
					encoded.limit(encoded.capacity());
					encoded.position(delta);
				}
				else
				{
					encoded.clear();
				}
				decoded.flip();
				final String string = decoded.toString();
				decoded.clear();
				// Right! We finally have decoded data as a string, so we can
				// now extract code points and populate the accumulate.
				for (int i = 0, size = string.length(); i < size; )
				{
					final int codePoint = string.codePointAt(i);
					final CharacterInfo info = new CharacterInfo(codePoint);
					// Don't lose information already accumulated about this
					// code point. (The equals operation only checks the code
					// point, not any ancillary information!)
					if (!codePoints.contains(info))
					{
						codePoints.add(info);
					}
					i += Character.charCount(codePoint);
				}
			}
		}
		finally
		{
			IO.close(in);
		}
	}

	/**
	 * Accumulate into {@link #allCodePoints} every Unicode code point
	 * encountered within a {@linkplain #allPaths file of interest}.
	 *
	 * @throws IOException
	 *         If an I/O exception occurs.
	 */
	private void computeAllCodePoints () throws IOException
	{
		for (final Path path : allPaths)
		{
			computeAllCodePointsIn(path);
		}
	}

	/**
	 * Answer the {@linkplain URL} of the <a href="http://www.fileformat.info">
	 * FileFormat.Info</a> page that describes the requested code point.
	 *
	 * @param info
	 *        A {@link CharacterInfo code point}.
	 * @return The appropriate URL.
	 * @throws MalformedURLException
	 *         If the URL is malformed (but this should never happen).
	 */
	private static final URL urlFor (final CharacterInfo info)
		throws MalformedURLException
	{
		final String urlString = String.format(
			"http://www.fileformat.info/info/unicode/char/%04x/index.html",
			info.codePoint());
		final URL url = new URL(urlString);
		return url;
	}

	/**
	 * The {@linkplain Pattern pattern} for extracting the HTML entity name of a
	 * code point.
	 */
	private static final Pattern htmlEntityNamePattern =
		Pattern.compile("(?s)&amp;(\\w+?);");

	/**
	 * Refresh the {@linkplain Catalog catalog} from the file system and the
	 * Internet.
	 *
	 * @throws IOException
	 *         If an I/O exception occurs.
	 */
	public void refresh () throws IOException
	{
		computeAllPaths();
		computeAllCodePoints();
		populateAllCharacterInfo();
	}

	/**
	 * Answer the {@linkplain Set set} of all Unicode code points used by the
	 * Avail project.
	 *
	 * @return The set of all Unicode code points in use.
	 * @throws IOException
	 *         If an I/O exception occurs.
	 */
	public synchronized Set<CharacterInfo> allCodePoints () throws IOException
	{
		Set<CharacterInfo> codePoints = allCodePoints;
		if (codePoints == null)
		{
			codePoints = new HashSet<>();
			allCodePoints = codePoints;
			refresh();
			codePoints = new TreeSet<>(codePoints);
			allCodePoints = codePoints;
		}
		assert codePoints != null;
		return Collections.unmodifiableSet(codePoints);
	}

	/**
	 * Populate the specified {@link CharacterInfo} with data obtained from
	 * <a href="http://www.fileformat.info">FileFormat.Info</a>. (Thanks, guys!)
	 *
	 * @param info
	 *        The {@code CharacterInfo} to populate.
	 * @throws IOException
	 *         If an I/O exception occurs.
	 */
	private void populateCharacterInfo (final CharacterInfo info)
		throws IOException
	{
		final URL url = urlFor(info);
		System.err.printf("Fetching content from %s…%n", url);
		final URLConnection connection = url.openConnection();
		String encoding = connection.getContentEncoding();
		if (encoding == null)
		{
			encoding = "UTF-8";
		}
		final Charset characterSet = Charset.forName(encoding);
		final Reader reader = new BufferedReader(
			new InputStreamReader(
				connection.getInputStream(),
				characterSet));
		final StringBuilder builder = new StringBuilder(4096);
		try
		{
			final CharBuffer buffer = CharBuffer.allocate(4096);
			while (reader.read(buffer) != -1)
			{
				buffer.flip();
				builder.append(buffer.toString());
				buffer.clear();
			}
		}
		finally
		{
			IO.close(reader);
		}
		final String content = builder.toString();
		// We have the HTML content now. Let's mine it for the information that
		// we want. First exact the HTML entity name. There may not be one.
		final Matcher matcher = htmlEntityNamePattern.matcher(content);
		if (matcher.find())
		{
			final String entityName = matcher.group(1);
			info.htmlEntityName = String.format("&%s;", entityName);
		}
	}

	/**
	 * Populate all {@link CharacterInfo character info} using data obtained
	 * from <a href="http://www.fileformat.info">FileFormat.Info</a>.
	 * (Thanks, guys!)
	 *
	 * @throws IOException
	 *         If an I/O exception occurs.
	 */
	private void populateAllCharacterInfo () throws IOException
	{
		final Set<CharacterInfo> codePoints = allCodePoints;
		assert codePoints != null;
		for (final CharacterInfo info : codePoints)
		{
			if (!info.previouslyFetched())
			{
				populateCharacterInfo(info);
				try
				{
					// Don't beat the crap out of the site. Don't do more than
					// 20 requests per second.
					Thread.sleep(50);
				}
				catch (final InterruptedException e)
				{
					// Give up.
					return;
				}
			}
		}
	}

	/**
	 * Answer a {@linkplain JSONFriendly JSON-friendly representative} of the
	 * {@linkplain #allCodePoints() complete set of code points}.
	 *
	 * @return The representative.
	 * @throws IOException
	 *         If an I/O exception occurs.
	 */
	public JSONFriendly jsonFriendlyCodePoints () throws IOException
	{
		final Set<CharacterInfo> codePoints = allCodePoints();
		return new JSONFriendly()
		{
			@Override
			public void writeTo (final JSONWriter writer)
			{
				writer.startArray();
				for (final CharacterInfo info : codePoints)
				{
					writer.write(info);
				}
				writer.endArray();
			}
		};
	}

	/**
	 * The {@linkplain Set set} of all non-ASCII {@linkplain CharacterInfo code
	 * points}.
	 */
	private transient @Nullable Set<CharacterInfo> allNonAsciiCodePoints;

	/**
	 * Answer the {@linkplain Set set} of all non-ASCII code points used by the
	 * Avail project.
	 *
	 * @return The set of all non-ASCII code points in use.
	 * @throws IOException
	 *         If an I/O exception occurs.
	 */
	public synchronized Set<CharacterInfo> allNonAsciiCodePoints ()
		throws IOException
	{
		Set<CharacterInfo> codePoints = allNonAsciiCodePoints;
		if (codePoints == null)
		{
			codePoints = new TreeSet<>();
			allNonAsciiCodePoints = codePoints;
			for (final CharacterInfo info : allCodePoints())
			{
				if (info.codePoint() > 127)
				{
					codePoints.add(info);
				}
			}
		}
		assert codePoints != null;
		return Collections.unmodifiableSet(codePoints);
	}

	/**
	 * Answer a {@linkplain JSONFriendly JSON-friendly representative} of the
	 * {@linkplain #allNonAsciiCodePoints() complete set of non-ASCII code
	 * points}.
	 *
	 * @return The representative.
	 * @throws IOException
	 *         If an I/O exception occurs.
	 */
	public JSONFriendly jsonFriendlyNonAsciiCodePoints () throws IOException
	{
		final Set<CharacterInfo> codePoints = allNonAsciiCodePoints();
		return new JSONFriendly()
		{
			@Override
			public void writeTo (final JSONWriter writer)
			{
				writer.startArray();
				for (final CharacterInfo info : codePoints)
				{
					writer.write(info);
				}
				writer.endArray();
			}
		};
	}

	/**
	 * The {@linkplain Set set} of all non-ASCII, non-alphanumeric {@linkplain
	 * CharacterInfo code points}.
	 */
	private transient @Nullable Set<CharacterInfo> allSymbolicCodePoints;

	/**
	 * Answer the {@linkplain Set set} of all non-ASCII, non-alphanumeric code
	 * points used by the Avail project.
	 *
	 * @return The set of all non-ASCII, non-alphanumeric code points in use.
	 * @throws IOException
	 *         If an I/O exception occurs.
	 */
	public synchronized Set<CharacterInfo> allSymbolicCodePoints ()
		throws IOException
	{
		Set<CharacterInfo> codePoints = allSymbolicCodePoints;
		if (codePoints == null)
		{
			codePoints = new TreeSet<>();
			allSymbolicCodePoints = codePoints;
			for (final CharacterInfo info : allNonAsciiCodePoints())
			{
				if (!Character.isLetterOrDigit(info.codePoint()))
				{
					codePoints.add(info);
				}
			}
		}
		assert codePoints != null;
		return Collections.unmodifiableSet(codePoints);
	}

	/**
	 * Answer a {@linkplain JSONFriendly JSON-friendly representative} of the
	 * {@linkplain #allSymbolicCodePoints() complete set of non-ASCII,
	 * non-alphanumeric code points}.
	 *
	 * @return The representative.
	 * @throws IOException
	 *         If an I/O exception occurs.
	 */
	public JSONFriendly jsonFriendlySymbolicCodePoints () throws IOException
	{
		final Set<CharacterInfo> codePoints = allSymbolicCodePoints();
		return new JSONFriendly()
		{
			@Override
			public void writeTo (final JSONWriter writer)
			{
				writer.startArray();
				for (final CharacterInfo info : codePoints)
				{
					writer.write(info);
				}
				writer.endArray();
			}
		};
	}

	/**
	 * Construct a new {@link Catalog}.
	 *
	 * @param data
	 *        A {@code JSONData} that contains the catalog data. May be {@code
	 *        null}.
	 */
	private Catalog (final @Nullable JSONData data)
	{
		final Set<CharacterInfo> set = new HashSet<>();
		final JSONArray array = (JSONArray) data;
		if (array != null)
		{
			for (final JSONData element : array)
			{
				set.add(CharacterInfo.readFrom(element));
			}
		}
		allCodePoints = new TreeSet<>(set);
	}

	/**
	 * Construct a new {@link Catalog}. Populate it with character information.
	 *
	 * @throws IOException
	 *         If an I/O exception occurs.
	 */
	public Catalog () throws IOException
	{
		// Populate all code point information.
		allCodePoints();
	}

	/**
	 * Decode a {@link Catalog} from the specified {@link JSONData}.
	 *
	 * @param data
	 *        A {@code JSONData}. May be {@code null}.
	 * @return A {@code Catalog}.
	 */
	public static final Catalog readFrom (final @Nullable JSONData data)
	{
		return new Catalog(data);
	}

	@Override
	public String toString ()
	{
		// Don't load the catalog!
		final Set<CharacterInfo> codePoints = allCodePoints;
		if (codePoints == null)
		{
			return "«empty catalog»";
		}
		final StringBuilder builder = new StringBuilder();
		builder.append(String.format("%d code point(s):\n", codePoints.size()));
		boolean first = true;
		for (final CharacterInfo info : codePoints)
		{
			if (!first)
			{
				builder.append(",\n");
			}
			builder.append(info);
			first = false;
		}
		builder.append("\n");
		return builder.toString();
	}
}
