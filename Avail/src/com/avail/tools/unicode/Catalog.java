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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.FileChannel;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import com.avail.annotations.InnerAccess;
import com.avail.annotations.Nullable;
import com.avail.utility.IO;
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
	 * The {@linkplain Set set} of all Unicode code points used by the Avail
	 * project.
	 */
	private transient @Nullable Set<Integer> allCodePoints;

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
		final Set<Integer> codePoints = allCodePoints;
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
					codePoints.add(codePoint);
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
	 * Answer the {@linkplain Set set} of all Unicode code points used by the
	 * Avail project.
	 *
	 * @return The set of all Unicode code points in use.
	 * @throws IOException
	 *         If an I/O exception occurs.
	 */
	public synchronized Set<Integer> allCodePoints () throws IOException
	{
		Set<Integer> codePoints = allCodePoints;
		if (codePoints == null)
		{
			codePoints = new HashSet<>();
			allCodePoints = codePoints;
			computeAllPaths();
			computeAllCodePoints();
		}
		assert codePoints != null;
		return Collections.unmodifiableSet(codePoints);
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
		final List<Integer> codePoints = new ArrayList<>(allCodePoints());
		return new JSONFriendly()
		{
			@Override
			public void writeTo (final JSONWriter writer)
			{
				Collections.sort(codePoints);
				writer.startArray();
				for (final int codePoint : codePoints)
				{
					writer.write(codePoint);
				}
				writer.endArray();
			}
		};
	}

	/** The {@linkplain Set set} of all non-ASCII code points. */
	private transient @Nullable Set<Integer> allNonAsciiCodePoints;

	/**
	 * Answer the {@linkplain Set set} of all non-ASCII code points used by the
	 * Avail project.
	 *
	 * @return The set of all non-ASCII code points in use.
	 * @throws IOException
	 *         If an I/O exception occurs.
	 */
	public synchronized Set<Integer> allNonAsciiCodePoints () throws IOException
	{
		Set<Integer> codePoints = allNonAsciiCodePoints;
		if (codePoints == null)
		{
			codePoints = new HashSet<>();
			allNonAsciiCodePoints = codePoints;
			for (final int codePoint : allCodePoints())
			{
				if (codePoint > 127)
				{
					codePoints.add(codePoint);
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
		final List<Integer> codePoints =
			new ArrayList<>(allNonAsciiCodePoints());
		return new JSONFriendly()
		{
			@Override
			public void writeTo (final JSONWriter writer)
			{
				Collections.sort(codePoints);
				writer.startArray();
				for (final int codePoint : codePoints)
				{
					writer.write(codePoint);
				}
				writer.endArray();
			}
		};
	}
}
