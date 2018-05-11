/*
 * IndexedFile.java
 * Copyright © 1993-2018, The Avail Foundation, LLC.
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
package com.avail.persistence;

import com.avail.annotations.InnerAccess;
import com.avail.utility.LRUCache;
import com.avail.utility.Pair;
import com.avail.utility.evaluation.Continuation0;
import com.avail.utility.evaluation.Transformer1;
import com.avail.utility.evaluation.Transformer2;

import javax.annotation.Nullable;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.Inflater;

import static com.avail.persistence.IndexedRepositoryManager.log;
import static com.avail.utility.Nulls.stripNull;

/**
 * {@code IndexedFile}s are record journals. Records may be {@linkplain
 * #add(byte[]) appended}, explicitly {@linkplain #commit() committed}, and
 * {@linkplain #get(long) looked up by record number}. A single arbitrary
 * {@linkplain #metaData()} metadata section can be {@linkplain
 * #metaData(byte[]) attached} to an indexed file (and will be replaced by
 * subsequent attachments). Concurrent read access is supported for multiple
 * {@linkplain Thread threads}, drivers, and {@linkplain Process OS processes}.
 * Only one writer is permitted.
 *
 * <p>Only subclasses of {@code IndexedFile} are intended for direct use. A
 * subclass must implement {@link #headerBytes() headerBytes}.</p>
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Skatje Myers &lt;skatje.myers@gmail.com&gt;
 */
public abstract class IndexedFile
{
	/**
	 * The {@linkplain ReentrantReadWriteLock lock} that guards against unsafe
	 * concurrent access.
	 */
	private final ReentrantReadWriteLock lock =
		new ReentrantReadWriteLock();

	/** The {@linkplain File file reference}. */
	private @Nullable File fileReference;

	/** The underlying {@linkplain RandomAccessFile file}. */
	private @Nullable RandomAccessFile file;

	/**
	 * Answer the {@link #file}.
	 * @return The file.
	 */
	private RandomAccessFile file ()
	{
		return stripNull(file);
	}

	/**
	 * An open {@linkplain FileChannel channel} on the underlying {@linkplain
	 * RandomAccessFile file}.
	 */
	private @Nullable FileChannel channel;

	/**
	 * Answer the {@link #channel}.
	 * @return The channel.
	 */
	private FileChannel channel ()
	{
		return stripNull(channel);
	}

	/**
	 * A lock on the last indexable byte of the file.  Not the last byte of the
	 * actual extant file, but the last indexable byte (2^63-1).  This allows
	 * multiple readers or one writer.
	 */
	private @Nullable FileLock longTermLock;

	/**
	 * Answer the existing {@link FileLock} on the last indexable byte of the
	 * file.
	 *
	 * @return The lock.
	 */
	private FileLock longTermLock ()
	{
		return stripNull(longTermLock);
	}

	/** The preferred page size of a {@linkplain IndexedFile indexed file}. */
	public static final int DEFAULT_PAGE_SIZE = 4096;

	/** The page size of the {@linkplain IndexedFile indexed file}. */
	@InnerAccess int pageSize;

	/**
	 * The preferred compression threshold of a {@linkplain IndexedFile indexed
	 * file}.
	 */
	public static final int DEFAULT_COMPRESSION_THRESHOLD = 32768;

	/**
	 * The compression threshold of the {@linkplain IndexedFile indexed file}.
	 */
	@InnerAccess int compressionBlockSize;

	/**
	 * The preferred index node fan-out. The value is small enough that the
	 * orphans all fit on a page (with a trillion records), while large enough
	 * to keep the number of index levels from getting too high. A fan-out of 2
	 * would have 40 levels (for a trillion records), which is probably too slow
	 * for random access. A fan-out of 100 would be 6 levels high, but at 13
	 * bytes per orphan pointer it would take 6*100*13=7800 bytes, which is more
	 * than the usual (4KB) page size. By using a fan-out of 32 there are 8
	 * levels (for a trillion), or 8*32*13 = 3328 bytes for tracking orphans.
	 */
	private static final int DEFAULT_FANOUT = 32;

	/** The index node arity of the {@linkplain IndexedFile indexed file}. */
	private int fanout;

	/** The version of the {@linkplain IndexedFile indexed file}. */
	private int version;

	/**
	 * {@code ByteArrayOutputStream} provides direct (unsafe) access to the
	 * backing byte array (without requiring it to be copied).
	 */
	@InnerAccess static final class ByteArrayOutputStream
	extends java.io.ByteArrayOutputStream
	{
		/**
		 * Answer the backing byte array. Do not copy it.
		 *
		 * @return The backing byte array.
		 */
		public byte[] unsafeBytes ()
		{
			return buf;
		}

		/**
		 * Construct a new {@code ByteArrayOutputStream}.
		 *
		 * @param size
		 *        The initial size of the backing byte array.
		 */
		ByteArrayOutputStream (final int size)
		{
			super(size);
		}
	}

	/**
	 * {@code MasterNode} is a simple abstraction for a {@linkplain IndexedFile
	 * indexed file} master node.
	 */
	final class MasterNode
	{
		/**
		 * The serial number of the current master node. Viewed alternatively,
		 * the <em>next</em> serial number that should be committed to the
		 * {@linkplain IndexedFile indexed file}.
		 */
		@InnerAccess int serialNumber;

		/** The virtual end of file. */
		@InnerAccess long fileLimit;

		/** The raw bytes of <em>uncompressed</em> data. */
		final ByteArrayOutputStream rawBytes;

		/** The <em>uncompressed</em> data. */
		final DataOutputStream uncompressedData;

		/** The {@linkplain RecordCoordinates coordinates} of the metadata. */
		RecordCoordinates metaDataLocation;

		/**
		 * The list of orphans, with lists of orphans locations of orphan level
		 * n, stored at index n.
		 */
		List<List<RecordCoordinates>> orphansByLevel;

		/**
		 * The last (partial) committed page of data. For transactional safety,
		 * we cannot simply append a partial page of data to the end of the
		 * backing store. This partial page will be transactionally written to
		 * the current master node during a commit.
		 */
		byte[] lastPartialBuffer;

		/**
		 * Construct a new master node.
		 *
		 * @param serialNumber
		 *        The serial number.
		 * @param fileLimit
		 *        The virtual end of file.
		 */
		MasterNode (final int serialNumber, final long fileLimit)
		{
			this.serialNumber = serialNumber;
			this.fileLimit = fileLimit;
			this.rawBytes = new ByteArrayOutputStream(
				compressionBlockSize * 3 >> 1);
			this.uncompressedData = new DataOutputStream(rawBytes);
			this.lastPartialBuffer = new byte[pageSize];
			this.orphansByLevel = new ArrayList<>();
			this.metaDataLocation = RecordCoordinates.origin();
		}

		/**
		 * Serialize the master node into the specified {@link ByteBuffer}. The
		 * {@link ByteBuffer#position()} of the {@link #masterNodeBuffer} will
		 * be {@code 0} after the call returns.
		 *
		 * @param buffer
		 *        The output buffer into which the master node should be
		 *        serialized.
		 */
		void writeTo (final ByteBuffer buffer)
		{
			assert rawBytes.size() < compressionBlockSize;

			// Compute the orphan count.
			int orphanCount = 0;
			for (final List<?> orphans : orphansByLevel)
			{
				orphanCount += orphans.size();
			}

			// The first four bytes are the CRC32 of the master node (sans the
			// CRC32 field). Write in a zero for now; we will overwrite this
			// with an actual checksum after we have written the remainder of
			// the current master block.
			buffer.rewind();
			buffer.putInt(0);
			buffer.putInt(serialNumber);
			buffer.putLong(fileLimit);
			buffer.putInt(rawBytes.size());
			buffer.putLong(metaDataLocation.filePosition());
			buffer.putInt(metaDataLocation.blockPosition());
			buffer.putInt(orphanCount);
			for (byte level = 0; level < orphansByLevel.size(); level++)
			{
				for (final RecordCoordinates orphanLocation :
					orphansByLevel.get(level))
				{
					buffer.put((byte) (level + 1));
					buffer.putLong(orphanLocation.filePosition());
					buffer.putInt(orphanLocation.blockPosition());
				}
			}
			assert buffer.position() <= pageSize
				: "Too much index orphan information for a page.";
			buffer.put(new byte[pageSize - buffer.position()]);
			assert buffer.position() == pageSize;
			buffer.put(lastPartialBuffer);
			assert buffer.position() == pageSize << 1;
			buffer.put(rawBytes.unsafeBytes(), 0, rawBytes.size());
			buffer.put(new byte[compressionBlockSize - rawBytes.size()]);
			assert buffer.position() == buffer.capacity();
			assert buffer.position() == masterNodeSize();
			// Now write the CRC32 into the first four bytes of the node.
			final CRC32 encoder = new CRC32();
			encoder.update(
				buffer.array(), 4, buffer.position() - 4);
			buffer.rewind();
			buffer.putInt((int) encoder.getValue());
			buffer.rewind();
		}
	}

	/** The current {@linkplain MasterNode master node}. */
	private @Nullable MasterNode master;

	/**
	 * Answer the {@link #master}.
	 * @return The master.
	 */
	private MasterNode master ()
	{
		return stripNull(master);
	}

	/**
	 * Answer the {@linkplain #masterNodeBuffer master node} size.
	 *
	 * @return The {@linkplain #masterNodeBuffer master node} size.
	 */
	@InnerAccess int masterNodeSize ()
	{
		return (pageSize << 1) + compressionBlockSize;
	}

	/**
	 * A master node comprises, in sequence, the following:
	 *
	 * <ul>
	 * <li>A first page containing:
	 * <ul><li>4-byte CRC of rest of data (both pages)</li>
	 * <li>4-byte serial counter (should be other master node's counter
	 * +/- 1</li>
	 * <li>8-byte fileLimit</li>
	 * <li>4-byte positionInCompressionBlock (indicates number of valid
	 * uncompressed bytes below)</li>
	 * <li>12-byte metadata pointer (8-byte file position + 4-byte position in
	 * uncompressed data)</li>
	 * <li>4-byte node count N, followed by N entries. Each entry represents a
	 * record or index node that is not yet reachable from a parent node. This
	 * information is sufficient to rebuild the {@link
	 * MasterNode#orphansByLevel} structure.
	 * <ul><li>1-byte level indicator (1=record, 2=bottom index node, etc).</li>
	 * <li>12-byte pointer (8-byte file position + 4-byte position in
	 * uncompressed data)</li></ul>
	 * </ul></li>
	 * <li>A second page containing:
	 * <ul><li>The last (partial) committed page of data.  It should be
	 * rewritten to the file at the indicated position during recovery.
	 * Subsequent writes will continue at fileLimit, which is somewhere inside
	 * this page.</li></ul>
	 * <li>A compression block of size compressionBlockSize containing
	 * positionInCompressionBlock of valid data.</li>
	 * </ul>
	 */
	private @Nullable ByteBuffer masterNodeBuffer;

	/** The absolute location of the current master node. */
	private long masterPosition;

	/** The absolute location of the previous master node. */
	private long previousMasterPosition;

	/**
	 * {@code RecordCoordinates} are the two-dimension coordinates of an
	 * uncompressed record within a {@linkplain IndexedFile indexed file}. The
	 * first axis is the absolute position within the indexed file of the
	 * compressed block containing the record. The second axis is the position
	 * of the record within the <em>uncompressed</em> block.
	 */
	@InnerAccess static final class RecordCoordinates
	extends Pair<Long, Integer>
	{
		/**
		 * Answer the absolute position within the {@linkplain IndexedFile
		 * indexed file} of the compressed block containing the record.
		 *
		 * @return The file position.
		 */
		long filePosition ()
		{
			return first();
		}

		/**
		 * Answer the position within the <em>uncompressed</em> block of the
		 * record.
		 *
		 * @return The block position.
		 */
		int blockPosition ()
		{
			return second();
		}

		@Override
		public boolean equals (final @Nullable Object other)
		{
			if (!(other instanceof RecordCoordinates))
			{
				return false;
			}
			final RecordCoordinates strongOther = (RecordCoordinates) other;
			return filePosition() == strongOther.filePosition()
				&& blockPosition() == strongOther.blockPosition();
		}

		@Override
		public int hashCode ()
		{
			return (int) ((filePosition() ^ 0x58FC0112)
				* (blockPosition() ^ 0xCACC77F3)
				+ 0x62B02A14);
		}

		/**
		 * Construct a new {@code RecordCoordinates}.
		 *
		 * @param filePosition
		 *        The absolute position within the {@linkplain IndexedFile
		 *        indexed file} of the compressed block containing the record.
		 * @param blockPosition
		 *        The position within the <em>uncompressed</em> block of the
		 *        record.
		 */
		RecordCoordinates (
			final long filePosition,
			final int blockPosition)
		{
			super(filePosition, blockPosition);
		}

		/** The origin. */
		private static final RecordCoordinates origin =
			new RecordCoordinates(0L, 0);

		/**
		 * Answer the origin in the plane defined by {@linkplain
		 * RecordCoordinates}.
		 *
		 * @return The origin (0, 0).
		 */
		static RecordCoordinates origin ()
		{
			return origin;
		}
	}

	/**
	 * The capacity of the {@linkplain LRUCache cache} of uncompressed records.
	 * A number of records equal to the delta between this value and that of
	 * {@link #DEFAULT_STRONG_CACHE_SIZE} will be discarded from the cache by
	 * the garbage collector when a low-water mark is passed.
	 */
	private static final int DEFAULT_SOFT_CACHE_SIZE = 200;

	/**
	 * The memory-insensitive capacity of the {@linkplain LRUCache cache} of
	 * uncompressed records.
	 */
	private static final int DEFAULT_STRONG_CACHE_SIZE = 100;

	/**
	 * A {@linkplain LRUCache cache} of uncompressed records.
	 */
	private final LRUCache<Long, byte[]> blockCache =
		new LRUCache<>(
			DEFAULT_SOFT_CACHE_SIZE,
			DEFAULT_STRONG_CACHE_SIZE,
			new Transformer1<Long, byte[]>()
			{
				@Override
				public byte[] value (final @Nullable Long argument)
				{
					assert argument != null;
					try
					{
						final byte[] block = fetchSizedFromFile(argument);
						final Inflater inflater = new Inflater();
						inflater.setInput(block);
						final List<byte[]> buffers = new ArrayList<>(10);
						int size = 0;
						int bufferPos = -1;
						while (!inflater.needsInput())
						{
							final byte[] buffer =
								new byte[(compressionBlockSize * 3 >> 1)];
							bufferPos = inflater.inflate(buffer);
							size += bufferPos;
							buffers.add(buffer);
						}
						final ByteBuffer inflated = ByteBuffer.wrap(
							new byte[size]);
						for (int i = 0; i < buffers.size() - 1; i++)
						{
							inflated.put(buffers.get(i));
						}
						inflated.put(
							buffers.get(buffers.size() - 1), 0, bufferPos);
						assert inflated.position() == inflated.capacity();
						return inflated.array();
					}
					catch (final Exception e)
					{
						throw new RuntimeException(e);
					}
				}
			});

	/** The client-provided metadata, as a byte array. */
	private @Nullable byte[] metaData;

	/**
	 * Answer the NUL-terminated header bytes that uniquely identify a
	 * particular usage of the core {@code IndexedFile indexed file}
	 * technology.
	 *
	 * @return An array of bytes that uniquely identifies the purpose of the
	 *         indexed file.
	 */
	protected abstract byte[] headerBytes ();

	/**
	 * Acquire an exclusive {@linkplain FileLock file lock} on the last byte of
	 * a logical 64-bit file range. This prevents other conforming {@linkplain
	 * IndexedFile indexed file} drivers (operating in other OS processes) from
	 * deciding that they can also write to the file.
	 *
	 * @param wait
	 *        {@code true} if the lock attempt should block until successful,
	 *        {@code false} if the lock attempt should fail immediately if
	 *        unsuccessful.
	 * @return The {@linkplain FileLock file lock}, or {@code null} if the
	 *         argument was {@code true} but the file is already locked by
	 *         another indexed file driver in another process.
	 * @throws IOException
	 *         If an {@linkplain IOException I/O exception} occurs.
	 */
	private FileLock acquireLockForWriting (final boolean wait)
		throws IOException
	{
		return wait
			? channel().lock(0x7FFFFFFFFFFFFFFEL, 1, false)
			: channel().tryLock(0x7FFFFFFFFFFFFFFEL, 1, false);
	}

	/**
	 * Insert the given orphan location at the given height of the index tree.
	 *
	 * @param orphanLocation
	 *        The location of the orphan to be added.
	 * @param level
	 *        The level at which to add this orphan.
	 * @throws IOException
	 *         If an {@linkplain IOException I/O exception} occurs.
	 */
	private void addOrphan (
			final RecordCoordinates orphanLocation,
			final int level)
		throws IOException
	{
		final MasterNode m = master();
		if (level >= m.orphansByLevel.size())
		{
			m.orphansByLevel.add(new ArrayList<>(fanout));
		}
		final List<RecordCoordinates> orphans = m.orphansByLevel.get(level);
		orphans.add(orphanLocation);
		if (orphans.size() == fanout)
		{
			final RecordCoordinates newOrphanLocation = new RecordCoordinates(
				m.fileLimit,
				m.rawBytes.size());
			for (final RecordCoordinates orphan : orphans)
			{
				m.uncompressedData.writeLong(orphan.filePosition());
				m.uncompressedData.writeInt(orphan.blockPosition());
			}
			orphans.clear();
			compressAndFlushIfFull();
			addOrphan(newOrphanLocation, level + 1);
		}
	}

	/**
	 * Appends the given bytes to the virtual end of the {@code IndexedFile}.
	 *
	 * @param bytes
	 *        The byte array to be appended.
	 * @throws IOException
	 *         If an {@linkplain IOException I/O exception} occurs.
	 */
	private void appendRawBytes (final byte[] bytes)
		throws IOException
	{
		int bufferPos = (int) master().fileLimit % pageSize;
		int start = 0;
		final int end = bytes.length;
		while (start < end)
		{
			final int limit = Math.min(
				bufferPos + end - start,
				master().lastPartialBuffer.length);
			final int count = limit - bufferPos;
			assert count > 0 : "Previous write should have flushed the buffer.";
			System.arraycopy(
				bytes,
				start,
				master().lastPartialBuffer,
				bufferPos,
				count);
			start += count;
			bufferPos += count;
			if (bufferPos >= pageSize)
			{
				assert bufferPos == pageSize;
				channel().position(master().fileLimit / pageSize * pageSize);
				channel().write(ByteBuffer.wrap(master().lastPartialBuffer));
				bufferPos = 0;
			}
			master().fileLimit += count;
		}
	}

	/**
	 * Append the 32-bit size and the contents of the specified byte array to
	 * the virtual end of the indexed file.
	 *
	 * @param compressedBytes A compressed byte array.
	 * @throws IOException
	 *         If an {@linkplain IOException I/O exception} occurs.
	 */
	private void appendSizedBytes (final byte[] compressedBytes)
		throws IOException
	{
		final byte[] sizePrefix = new byte[4];
		sizePrefix[0] = (byte)   (compressedBytes.length  >> 24);
		sizePrefix[1] = (byte) (((compressedBytes.length) >> 16) & 0xff);
		sizePrefix[2] = (byte) (((compressedBytes.length) >>  8) & 0xff);
		sizePrefix[3] = (byte)   (compressedBytes.length         & 0xff);
		appendRawBytes(sizePrefix);
		appendRawBytes(compressedBytes);
	}

	/**
	 * Answers the block at the given file position.
	 *
	 * @param filePosition
	 *        The absolute position of the file being requested.
	 * @return The block, as a byte array.
	 */
	private byte[] blockAtFilePosition (final long filePosition)
	{
		if (filePosition == master().fileLimit)
		{
			return master().rawBytes.unsafeBytes();
		}
		return blockCache.getNotNull(filePosition);
	}

	/**
	 * If the {@linkplain MasterNode#uncompressedData compression buffer} has
	 * filled up, then actually compress its contents and append them to the
	 * virtual end of the indexed file.
	 *
	 * @throws IOException
	 *         If an {@linkplain IOException I/O exception} occurs.
	 */
	private void compressAndFlushIfFull () throws IOException
	{
		if (master().rawBytes.size() >= compressionBlockSize)
		{
			final ByteArrayOutputStream compressedStream =
				new ByteArrayOutputStream(compressionBlockSize);
			final Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
			try (final DeflaterOutputStream stream =
				     new DeflaterOutputStream(compressedStream, deflater))
			{
				stream.write(
					master().rawBytes.unsafeBytes(),
					0,
					master().rawBytes.size());
			}
			while (master().fileLimit + 4 + compressedStream.size()
				>= file().length())
			{
				channel().position(0);
				final long delta =
					((Math.min(
						master().fileLimit,
						5 << 20) + pageSize - 1) / pageSize) * pageSize;
				file().setLength(file().length() + delta);
			}
			appendSizedBytes(compressedStream.toByteArray());
			master().rawBytes.reset();
		}
	}

	/**
	 * Create the physical indexed file. The initial contents are created in
	 * memory and then written to a temporary file. Once the header and master
	 * blocks have been written, the argument {@linkplain Continuation0 action}
	 * is performed. Finally the temporary file is renamed to the canonical
	 * filename. When the call returns, {@link #file} and {@link #channel} are
	 * live and a write lock is held on the physical indexed file.
	 *
	 * @param action
	 *        An action to perform after the header and master blocks have been
	 *        written, but before the temporary file is renamed.
	 * @throws IOException
	 *         If an {@linkplain IOException I/O exception} occurs.
	 */
	private void createFile (final @Nullable Continuation0 action)
		throws IOException
	{
		// Write the header.
		final byte[] headerBytes = headerBytes();
		previousMasterPosition =
			((headerBytes.length + 16L + pageSize - 1) / pageSize) * pageSize;
		masterPosition = previousMasterPosition + masterNodeSize();
		final long fileLimit = masterPosition + masterNodeSize();
		final long bufferSize = previousMasterPosition + masterNodeSize() << 1;
		assert bufferSize == (int) bufferSize;
		final ByteBuffer buffer = ByteBuffer.allocateDirect((int) bufferSize);
		buffer.order(ByteOrder.BIG_ENDIAN);
		buffer.put(headerBytes);
		buffer.putInt(currentVersion());
		buffer.putInt(pageSize);
		buffer.putInt(compressionBlockSize);
		buffer.putInt(fanout);
		buffer.put(new byte[(int) previousMasterPosition - buffer.position()]);
		assert buffer.position() == previousMasterPosition;

		// Write the master blocks.
		MasterNode m = new MasterNode(1, fileLimit);
		final ByteBuffer b = stripNull(masterNodeBuffer);
		m.writeTo(b);
		buffer.put(b);
		assert buffer.position() == masterPosition;
		m = new MasterNode(2, fileLimit);
		m.writeTo(b);
		buffer.put(b);
		assert buffer.position() == fileLimit;
		buffer.rewind();
		master = m;

		try
		{
			// Transfer the buffer to a temporary file. Perform the nullary
			// action. Close the channel prior to renaming the temporary file.
			final File tempFilename = File.createTempFile(
				"new indexed file", null, fileReference().getParentFile());
			tempFilename.deleteOnExit();
			file = new RandomAccessFile(tempFilename, "rw");
			assert file().length() == 0 : "The file is not empty.";
			file().setLength(pageSize * 100L);
			channel = file().getChannel();
			longTermLock = acquireLockForWriting(true);
			channel().write(buffer);
			channel().force(true);
			if (action != null)
			{
				action.value();
			}
			longTermLock().close();
			channel().close();

			// Rename the temporary file to the canonical target name. Reopen
			// the file and reacquire the write lock.
			final File fileRef = stripNull(fileReference);
			@SuppressWarnings("unused")
			final boolean ignored = tempFilename.renameTo(fileRef);
			file = new RandomAccessFile(fileReference, "rw");
			channel = file().getChannel();
			acquireLockForWriting(true);
		}
		catch (final IOException e)
		{
			close();
			throw e;
		}
	}

	/**
	 * Answer the current version of the indexed file technology used by the
	 * class. This is the version that will be used for new persistent indexed
	 * files.
	 *
	 * @return The current version.
	 */
	protected int currentVersion ()
	{
		final Class<? extends IndexedFile> myClass = getClass();
		final IndexedFileVersion currentVersion =
			myClass.getAnnotation(IndexedFileVersion.class);
		assert currentVersion != null :
			myClass.getName()
			+ " does not declare current version; add @IndexedFileVersion";
		return currentVersion.value();
	}

	/**
	 * Construct and answer a {@linkplain MasterNode master node} from the data
	 * at the given file position. If the node has a bad CRC, then answer
	 * {@code null}.
	 *
	 * @param nodePosition
	 *        The position within the {@linkplain #file file} of the desired
	 *        master node.
	 * @return A master node, or {@code null} if the data was corrupt.
	 * @throws IOException
	 *         If an {@linkplain IOException I/O exception} occurs.
	 */
	private @Nullable MasterNode decodeMasterNode (final long nodePosition)
		throws IOException
	{
		// Verify the CRC32.
		channel().position(nodePosition);
		final ByteBuffer b = stripNull(masterNodeBuffer);
		b.rewind();
		channel().read(b);
		final CRC32 encoder = new CRC32();
		encoder.update(
			b.array(), 4, b.position() - 4);
		b.rewind();
		if (b.getInt() != (int) encoder.getValue())
		{
			return null;
		}

		// Construct the master node state tuple.
		final MasterNode node = new MasterNode(
			b.getInt(),
			b.getLong());
		final int compressionBlockPosition = b.getInt();
		node.metaDataLocation = new RecordCoordinates(
			b.getLong(),
			b.getInt());
		final List<List<RecordCoordinates>> orphans =
			new ArrayList<>();
		for (int left = b.getInt(); left > 0; left--)
		{
			final int level = b.get() - 1;
			final RecordCoordinates orphan = new RecordCoordinates(
				b.getLong(),
				b.getInt());
			while (level >= orphans.size())
			{
				orphans.add(new ArrayList<>(fanout));
			}
			orphans.get(level).add(orphan);
		}
		assert b.position() <= pageSize
			: "Too much index orphan information for a page.";
		node.orphansByLevel = orphans;
		b.position(pageSize);
		final byte[] lastPageContents = new byte[pageSize];
		b.get(lastPageContents);
		assert b.position() == pageSize << 1;
		node.lastPartialBuffer = lastPageContents;
		final byte[] uncompressed = new byte[compressionBlockSize];
		b.get(uncompressed);
		assert b.position() == b.capacity();
		assert b.position() == masterNodeSize();
		node.rawBytes.reset();
		node.uncompressedData.write(uncompressed, 0, compressionBlockPosition);
		return node;
	}

	/**
	 * Read size-prefixed data from the specified absolute file position.
	 *
	 * @param startFilePosition An absolute file position.
	 * @return A byte array.
	 * @throws IOException
	 *         If an {@linkplain IOException I/O exception} occurs.
	 */
	@InnerAccess byte[] fetchSizedFromFile (
			final long startFilePosition)
		throws IOException
	{
		final byte[] sizePrefix = new byte[4];
		fillBuffer(sizePrefix, startFilePosition);
		final int size =
			  ((sizePrefix[0] & 0xFF) << 24)
			| ((sizePrefix[1] & 0xFF) << 16)
			| ((sizePrefix[2] & 0xFF) <<  8)
			|  (sizePrefix[3] & 0xFF);
		final byte[] content = new byte[size];
		fillBuffer(content, startFilePosition + 4);
		return content;
	}

	/**
	 * Fills the specified buffer with the bytes at the position given.
	 *
	 * @param bytes
	 *        The byte array to be filled.
	 * @param startFilePosition
	 *        The position in the file at which to begin reading bytes.
	 * @throws IOException
	 *         If an {@linkplain IOException I/O exception} occurs.
	 */
	private void fillBuffer (
			final byte[] bytes,
			final long startFilePosition)
		throws IOException
	{
		final long writtenLimit = (master().fileLimit / pageSize) * pageSize;
		final long endFilePosition = startFilePosition + bytes.length;
		assert endFilePosition <= master().fileLimit;
		if (startFilePosition < writtenLimit)
		{
			channel().position(startFilePosition);
			if (endFilePosition <= writtenLimit)
			{
				// Entirely within the file.
				final int bytesRead = channel().read(ByteBuffer.wrap(bytes));
				assert bytesRead == bytes.length;
			}
			else
			{
				// Split between file and unwritten buffer.
				final int split = (int) (writtenLimit - startFilePosition);
				final int bytesRead =
					channel().read(ByteBuffer.wrap(bytes, 0, split));
				assert bytesRead == split;
				System.arraycopy(
					master().lastPartialBuffer,
					0,
					bytes,
					split,
					bytes.length - split);
			}
		}
		else
		{
			// Entirely within the unwritten buffer.
			final long startInLastPartialBuffer =
				startFilePosition - writtenLimit;
			assert startInLastPartialBuffer == (int) startInLastPartialBuffer;
			assert startInLastPartialBuffer + bytes.length
				<= master().lastPartialBuffer.length;
			System.arraycopy(
				master().lastPartialBuffer,
				(int) startInLastPartialBuffer,
				bytes,
				0,
				bytes.length);
		}
	}

	/**
	 * Read the header page from the underlying {@linkplain #file file}.
	 *
	 * @param versionCheck
	 *        How to check if the file's version is acceptable, relative to the
	 *        code's current version.
	 * @throws IOException
	 *         If an {@linkplain IOException I/O exception} occurs.
	 * @throws IndexedFileException
	 *         If something else goes wrong.
	 */
	private void readHeaderData (
		final Transformer2<Integer, Integer, Boolean> versionCheck)
	throws IOException, IndexedFileException
	{
		boolean finished = false;
		try
		{
			assert file().length() > 0;
			final byte[] expectedHeader = headerBytes();
			final int bufferSize = expectedHeader.length + 16;
			final ByteBuffer buffer = ByteBuffer.allocateDirect(bufferSize);
			channel().read(buffer);
			final byte[] header = new byte[expectedHeader.length];
			buffer.rewind();
			buffer.get(header);
			if (!Arrays.equals(header, expectedHeader))
			{
				throw new IndexedFileException(
					"indexed file header is not valid.");
			}
			version = buffer.getInt();
			final Boolean okVersion =
				stripNull(versionCheck.value(version, currentVersion()));
			if (!okVersion)
			{
				throw new IndexedFileException(
					"Unsupported indexed file version: " + version);
			}
			pageSize = buffer.getInt();
			compressionBlockSize = buffer.getInt();
			fanout = buffer.getInt();
			previousMasterPosition =
				((long) bufferSize + pageSize - 1) / pageSize * pageSize;
			masterPosition = previousMasterPosition + masterNodeSize();
			finished = true;
		}
		finally
		{
			if (!finished)
			{
				close();
			}
		}
	}

	/**
	 * Answers the record located in the given node of the given level, at
	 * the specified index.
	 *
	 * @param startingIndex
	 *        The index within the tree.
	 * @param startingNodePosition
	 *        The search tree.
	 * @param startingLevel
	 *        The height of the search tree (0 for leaves).
	 * @return A record.
	 */
	private byte[] recordAtZeroBasedIndex (
		final long startingIndex,
		final RecordCoordinates startingNodePosition,
		final int startingLevel)
	{
		long pow = (long) Math.pow(fanout, startingLevel);
		assert startingIndex < pow : "Arithmetic error traversing perfect tree";
		RecordCoordinates node = new RecordCoordinates(
			startingNodePosition.filePosition(),
			startingNodePosition.blockPosition());
		ByteBuffer buffer = ByteBuffer.wrap(
			blockAtFilePosition(node.filePosition()));
		buffer.position(node.blockPosition());
		long zIndex = startingIndex;
		int level = startingLevel;
		while (level != 0)
		{
			pow /= fanout;
			final int zSubscript = (int) (zIndex / pow);
			zIndex %= pow;
			buffer.position(12 * zSubscript + node.blockPosition());
			node = new RecordCoordinates(
				buffer.getLong(),
				buffer.getInt());
			level--;
			buffer = ByteBuffer.wrap(
				blockAtFilePosition(node.filePosition()));
			buffer.position(node.blockPosition());
		}
		final byte[] result = new byte[buffer.getInt()];
		buffer.get(result);
		return result;
	}

	/**
	 * Add a portion of the given record to the indexed file. <em>Do not
	 * {@linkplain #commit() commit} the data.</em>
	 *
	 * @param record
	 *        The record which contains data that should be added to the indexed
	 *        file.
	 * @param start
	 *        The start position within the record of the source data.
	 * @param length
	 *        The size of the source data, in bytes.
	 * @throws IndexOutOfBoundsException
	 *         If the specified index is not equal to the size of the indexed
	 *         file.
	 * @throws IndexedFileException
	 *         If something else goes wrong.
	 */
	public void add (
		final byte[] record,
		final int start,
		final int length)
	throws IndexOutOfBoundsException, IndexedFileException
	{
		lock.writeLock().lock();
		try
		{
			final RecordCoordinates coordinates = new RecordCoordinates(
				master().fileLimit, master().rawBytes.size());
			master().uncompressedData.writeInt(length);
			master().uncompressedData.write(record, start, length);
			compressAndFlushIfFull();
			addOrphan(coordinates, 0);
		}
		catch (final IOException e)
		{
			throw new IndexedFileException(e);
		}
		finally
		{
			lock.writeLock().unlock();
		}
	}

	/**
	 * Add the given record to the end of the indexed file. <em>Do not
	 * {@linkplain #commit() commit} the data.</em>
	 *
	 * @param record
	 *        The record which should be added to the indexed file.
	 * @throws IndexedFileException
	 *         If something else goes wrong.
	 */
	public boolean add (final byte[] record)
		throws IndexedFileException
	{
		add(record, 0, record.length);
		return true;
	}

	/**
	 * Close the indexed file. No further API calls are permitted.
	 */
	public void close ()
	{
		log(Level.INFO, "Close: %s", fileReference);
		lock.writeLock().lock();
		try
		{
			if (longTermLock != null)
			{
				try
				{
					longTermLock().release();
				}
				catch (final IOException e)
				{
					// Ignore.
				}
				finally
				{
					longTermLock = null;
				}
			}

			if (channel != null)
			{
				try
				{
					channel().close();
				}
				catch (final IOException e)
				{
					// Ignore.
				}
				finally
				{
					channel = null;
				}
			}

			if (file != null)
			{
				try
				{
					file().close();
				}
				catch (final IOException e)
				{
					// Ignore.
				}
				finally
				{
					file = null;
				}
			}

			try
			{
				blockCache.clear();
			}
			catch (final InterruptedException e)
			{
				// Do nothing.
			}
		}
		finally
		{
			lock.writeLock().unlock();
		}
	}

	/**
	 * Commit the indexed file. In particular, write out the current master node
	 * to the underlying {@linkplain File file} and force a synchronization of
	 * the file's data and metadata buffers to disk.
	 *
	 * @throws IOException
	 *         If an {@linkplain IOException I/O exception} occurs.
	 */
	public void commit () throws IOException
	{
		lock.writeLock().lock();
		try
		{
			final ByteBuffer b = stripNull(masterNodeBuffer);
			channel().force(true);
			final long exchange = masterPosition;
			masterPosition = previousMasterPosition;
			previousMasterPosition = exchange;
			master().serialNumber++;
			master().writeTo(b);
			final FileLock shortTermLock = channel().lock(
				pageSize, (long) masterNodeSize() << 1L, false);
			try
			{
				channel().position(masterPosition);
				channel().write(b);
				channel().force(true);
			}
			finally
			{
				shortTermLock.release();
			}
		}
		finally
		{
			lock.writeLock().unlock();
		}
	}

	/**
	 * Answer the minimum number of uncompressed bytes at the virtualized end of
	 * an indexed file. Once this many uncompressed bytes have accumulated, then
	 * they will be compressed. Since uncompressed data must be written to a
	 * master node during a commit, this value should not be too large; but
	 * since compression efficiency improves as block size increases, this value
	 * should not be too small. A small multiple of the {@linkplain #pageSize
	 * page size} is optimal.
	 *
	 * @return The compression threshold of the indexed file.
	 */
	public int compressionBlockSize ()
	{
		return compressionBlockSize;
	}

	/**
	 * Answer the index node arity of the indexed file. The index node arity is
	 * the maximum number of children that an index node may possess. Higher
	 * arity reduces the depth of an index tree but increases the linear extent
	 * of a master node, i.e. it will require more space dedicated to tracking
	 * orphans at various levels of the index tree.
	 *
	 * @return The index node arity of the indexed file.
	 */
	public int fanout ()
	{
		return fanout;
	}

	/**
	 * Answer the {@linkplain File file reference} of the underlying
	 * {@linkplain RandomAccessFile file}.
	 *
	 * @return The underlying {@linkplain File file}.
	 */
	public File fileReference ()
	{
		return stripNull(fileReference);
	}

	/**
	 * Answer the requested record.
	 *
	 * @param index
	 *        The index of the requested record.
	 * @return The record bytes.
	 * @throws IndexOutOfBoundsException
	 *         If the index is out of bounds.
	 * @throws IndexedFileException
	 *         If something else goes wrong.
	 */
	public byte[] get (final long index)
		throws IndexOutOfBoundsException, IndexedFileException
	{
		lock.readLock().lock();
		try
		{
			if (index < 0)
			{
				throw new IndexOutOfBoundsException();
			}
			long residue = index;
			long power = (long) Math.pow(
				fanout, master().orphansByLevel.size() - 1);
			for (
				int level = master().orphansByLevel.size() - 1;
				level >= 0;
				level--)
			{
				final List<RecordCoordinates> orphans =
					master().orphansByLevel.get(level);
				final long subscript = residue / power;
				if (subscript < orphans.size())
				{
					return recordAtZeroBasedIndex(
						residue % power,
						orphans.get((int) subscript),
						level);
				}
				residue -= orphans.size() * power;
				power /= fanout;
			}
			throw new IndexOutOfBoundsException();
		}
		finally
		{
			lock.readLock().unlock();
		}
	}

	/**
	 * Answer the size of the indexed file, in records.
	 *
	 * @return The number of records contained in the indexed file.
	 */
	public long size ()
	{
		lock.readLock().lock();
		try
		{
			long power = 1;
			long sum = 0;
			for (int i = 0; i < master().orphansByLevel.size() ; i++)
			{
				sum += master().orphansByLevel.get(i).size() * power;
				power *= fanout;
			}
			return sum;
		}
		finally
		{
			lock.readLock().unlock();
		}
	}

	/**
	 * Answer the client-provided metadata, as a byte array.
	 *
	 * @return The client-provided metadata, as a byte array, or {@code null}
	 *         if no metadata has ever been specified.
	 */
	public @Nullable byte[] metaData ()
	{
		// Note that it is okay for multiple readers to destructively update
		// the metaData field: they will all write the same answer. This is why
		// we only grab a read lock.
		lock.readLock().lock();
		try
		{
			if (RecordCoordinates.origin().equals(master().metaDataLocation))
			{
				return null;
			}
			if (metaData == null)
			{
				final byte[] block = blockAtFilePosition(
					master().metaDataLocation.filePosition());
				final ByteBuffer buffer = ByteBuffer.wrap(block);
				buffer.position(master().metaDataLocation.blockPosition());
				final int size = buffer.getInt();
				metaData = new byte[size];
				buffer.get(metaData);
			}
			return metaData;
		}
		finally
		{
			lock.readLock().unlock();
		}
	}

	/**
	 * Set and write the new {@linkplain #metaData() metadata}. <em>Do not
	 * {@linkplain #commit() commit} the new metadata.</em>
	 *
	 * @param newMetaData
	 *        The new client-provided metadata, as a byte array.
	 * @throws IOException
	 *         If an {@linkplain IOException I/O exception} occurs.
	 */
	public void metaData (final byte[] newMetaData)
		throws IOException
	{
		assert newMetaData != null;
		lock.writeLock().lock();
		try
		{
			metaData = newMetaData;
			master().metaDataLocation = new RecordCoordinates(
				master().fileLimit, master().rawBytes.size());
			master().uncompressedData.writeInt(newMetaData.length);
			master().uncompressedData.write(newMetaData);
			compressAndFlushIfFull();
		}
		finally
		{
			lock.writeLock().unlock();
		}
	}

	/**
	 * Answer the page size of the {@linkplain IndexedFile indexed file}. This
	 * should be a multiple of the disk page size for good performance; for
	 * best performance, it should be a common multiple of the disk page size
	 * and the memory page size.
	 *
	 * @return The page size of the {@linkplain IndexedFile indexed file}.
	 */
	public int pageSize ()
	{
		return pageSize;
	}

	/**
	 * Update the state of the {@linkplain IndexedFile indexed file driver} from
	 * the physical contents of the indexed file.
	 *
	 * @throws IOException
	 *         If an {@linkplain IOException I/O exception} occurs.
	 * @throws IndexedFileException
	 *         If something else goes wrong.
	 */
	public void refresh () throws IOException, IndexedFileException
	{
		lock.writeLock().lock();
		try
		{
			final FileLock fileLock = channel().lock(
				pageSize, (long) masterNodeSize() << 1L, false);
			try
			{
				// Determine the newest valid master node.
				final MasterNode previous =
					decodeMasterNode(previousMasterPosition);
				MasterNode current = decodeMasterNode(masterPosition);
				if (previous == null && current == null)
				{
					throw new IndexedFileException(
						"Invalid indexed file -- "
						+ "both master nodes are corrupt.");
				}
				Integer delta = null;
				if (previous != null && current != null)
				{
					delta = current.serialNumber - previous.serialNumber;
					if (Math.abs(delta) != 1)
					{
						throw new IndexedFileException(
							"Invalid indexed file -- master nodes are valid "
							+ "but have non-consecutive serial numbers.");
					}
				}
				// Swap the previous and current nodes if necessary.
				if (previous != null && !Integer.valueOf(1).equals(delta))
				{
					current = previous;
					// previous is unused after this point.
					final long tempPos = previousMasterPosition;
					previousMasterPosition = masterPosition;
					masterPosition = tempPos;
				}
				if (master != null
					&& master.serialNumber != current.serialNumber)
				{
					// Clear the cached metadata if it has changed.
					if (!master.metaDataLocation.equals(
						current.metaDataLocation))
					{
						metaData = null;
					}
				}
				master = current;
			}
			catch (final IOException e)
			{
				close();
				throw e;
			}
			catch (final Throwable e)
			{
				close();
				throw new IndexedFileException(e);
			}
			finally
			{
				fileLock.release();
			}
		}
		finally
		{
			lock.writeLock().unlock();
		}
	}

	@Override
	public String toString ()
	{
		return String.format(
			"%s[%d] (for %s)",
			getClass().getSimpleName(),
			size(),
			fileReference);
	}

	/**
	 * Answer the version of the {@linkplain IndexedFile indexed file}.
	 *
	 * @return The version of the {@linkplain IndexedFile indexed file}.
	 */
	public int version ()
	{
		return version;
	}

	/**
	 * Create a new {@linkplain IndexedFile indexed file}. The resultant object
	 * is backed by a physical (i.e., disk-based) indexed file.
	 *
	 * @param subclass
	 *        The subclass of {@code IndexedFile} that should be created. This
	 *        indicates the purpose of the indexed file.
	 * @param fileReference
	 *        The location of the backing store.
	 * @param pageSize
	 *        The page size. A good page size is a multiple of both the disk
	 *        and memory page sizes.
	 * @param compressionThreshold
	 *        The compression threshold. A good compression threshold is a
	 *        multiple of the page size.
	 * @param initialMetaData
	 *        Client-provided {@linkplain #metaData() metadata}, or {@code null}
	 *        for none.
	 * @return The new indexed file.
	 * @throws IOException
	 *         If an {@linkplain IOException I/O exception} occurs.
	 */
	@SuppressWarnings("unchecked")
	public static <F extends IndexedFile> F newFile (
			final Class<F> subclass,
			final File fileReference,
			final int pageSize,
			final int compressionThreshold,
			final @Nullable byte[] initialMetaData)
		throws IOException
	{
		log(Level.INFO, "New: %s", fileReference);
		assert compressionThreshold % pageSize == 0;
		final IndexedFile indexedFile;
		try
		{
			indexedFile = subclass.newInstance();
		}
		catch (InstantiationException | IllegalAccessException e)
		{
			assert false : "This should never happen!";
			throw new RuntimeException(e);
		}
		indexedFile.fileReference = fileReference;
		indexedFile.version = indexedFile.currentVersion();
		indexedFile.pageSize = pageSize;
		indexedFile.compressionBlockSize = compressionThreshold;
		indexedFile.fanout = DEFAULT_FANOUT;
		indexedFile.masterNodeBuffer = ByteBuffer.allocate(
			indexedFile.masterNodeSize());
		indexedFile.createFile(() ->
		{
			if (initialMetaData != null)
			{
				try
				{
					indexedFile.metaData(initialMetaData);
					indexedFile.commit();
				}
				catch (final IOException e)
				{
					throw new IndexedFileException(e);
				}
			}
		});
		return (F) indexedFile;
	}

	/**
	 * Create a new {@linkplain IndexedFile indexed file}, using reasonable
	 * defaults for {@linkplain #DEFAULT_PAGE_SIZE page size} and {@linkplain
	 * #DEFAULT_COMPRESSION_THRESHOLD compression threshold}. The resultant
	 * object is backed by a physical (i.e., disk-based) indexed file.
	 *
	 * @param subclass
	 *        The subclass of {@code IndexedFile} that should be created. This
	 *        indicates the purpose of the indexed file.
	 * @param fileReference
	 *        The location of the backing store.
	 * @param initialMetaData
	 *        Client-provided {@linkplain #metaData() metadata}, or {@code null}
	 *        for none.
	 * @return The new indexed file.
	 * @throws IOException
	 *         If an {@linkplain IOException I/O exception} occurs.
	 */
	public static <F extends IndexedFile> F newFile (
			final Class<F> subclass,
			final File fileReference,
			final @Nullable byte[] initialMetaData)
		throws IOException
	{
		return newFile(
			subclass,
			fileReference,
			DEFAULT_PAGE_SIZE,
			DEFAULT_COMPRESSION_THRESHOLD,
			initialMetaData);
	}

	/**
	 * Open the specified {@linkplain IndexedFile indexed file}.
	 *
	 * <p>Note that there may be any number of readers <em>and</em> up to one
	 * writer accessing the file safely simultaneously.  Only the master blocks
	 * are ever updated (all other blocks are written exactly once ever), and
	 * those writes occur inside an exclusive lock of that region.  They're also
	 * forced to disk before releasing the lock, to guarantee coherence.  The
	 * reads of the master blocks happen inside a shared lock of the same
	 * region.  A reader uses {@link #refresh()} to detect newly appended
	 * records.</p>
	 *
	 * @param subclass
	 *        The subclass of {@code IndexedFile} that should be created. This
	 *        indicates the purpose of the indexed file. The {@linkplain
	 *        #headerBytes() header data} contained within the file must agree
	 *        with that specified by the subclass.
	 * @param fileReference
	 *        The location of the indexed file.
	 * @param forWriting
	 *        {@code true} if the indexed file should be opened for writing,
	 *        {@code false} otherwise.
	 * @param versionCheck
	 *        How to check for a compatible version.
	 * @return The indexed file.
	 * @throws IOException
	 *         If an {@linkplain IOException I/O exception} occurs.
	 * @throws IndexedFileException
	 */
	public static <F extends IndexedFile> F openFile (
			final Class<F> subclass,
			final File fileReference,
			final boolean forWriting,
			final Transformer2<Integer, Integer, Boolean> versionCheck)
	throws
		IOException,
		IndexedFileException
	{
		log(Level.INFO, "Open: {0}", fileReference);
		final F strongIndexedFile;
		final IndexedFile indexedFile;
		try
		{
			strongIndexedFile = subclass.newInstance();
			indexedFile = strongIndexedFile;
		}
		catch (final InstantiationException | IllegalAccessException e)
		{
			assert false : "This should never happen!";
			throw new RuntimeException(e);
		}
		indexedFile.fileReference = fileReference;
		if (!fileReference.exists())
		{
			throw new IndexedFileException("No such index file");
		}
		indexedFile.file = new RandomAccessFile(
			fileReference, forWriting ? "rw" : "r");
		if (fileReference.length() == 0)
		{
			throw new IndexedFileException(
				"Index file has no header (0 bytes)");
		}
		indexedFile.channel = indexedFile.file().getChannel();
		indexedFile.readHeaderData(versionCheck);
		indexedFile.masterNodeBuffer = ByteBuffer.allocate(
			indexedFile.masterNodeSize());
		if (forWriting)
		{
			indexedFile.longTermLock = indexedFile.acquireLockForWriting(true);
		}
		indexedFile.refresh();
		return strongIndexedFile;
	}
}
