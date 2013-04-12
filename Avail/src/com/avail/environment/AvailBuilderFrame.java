/**
 * AvailBuilderFrame.java
 * Copyright © 1993-2013, Mark van Gulik and Todd L Smith.
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

package com.avail.environment;

import static java.awt.KeyboardFocusManager.*;
import static java.awt.AWTKeyStroke.*;
import static java.lang.Math.*;
import static java.lang.System.arraycopy;
import static javax.swing.SwingUtilities.*;
import static javax.swing.JScrollPane.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.List;
import java.util.prefs.Preferences;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.text.*;
import javax.swing.tree.*;
import com.avail.AvailRuntime;
import com.avail.annotations.*;
import com.avail.builder.*;
import com.avail.compiler.*;
import com.avail.descriptor.*;
import com.avail.interpreter.Primitive;
import com.avail.persistence.*;
import com.avail.utility.*;

/**
 * {@code AvailBuilderFrame} is a simple user interface for the {@linkplain
 * AvailBuilder Avail builder}.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public class AvailBuilderFrame
extends JFrame
{
	/** The serial version identifier. */
	private static final long serialVersionUID = 5144194637595188046L;

	/** The {@linkplain Style style} to use for standard output. */
	private static final String outputStyleName = "output";

	/** The {@linkplain Style style} to use for standard error. */
	private static final String errorStyleName = "error";

	/** The {@linkplain Style style} to use for standard input. */
	private static final String inputStyleName = "input";

	/** The {@linkplain Style style} to use for notifications. */
	private static final String infoStyleName = "info";

	/**
	 * A {@code BuildAction} launches a {@linkplain BuildTask build task} in a
	 * Swing worker thread.
	 */
	private final class BuildAction
	extends AbstractAction
	{
		/** The serial version identifier. */
		private static final long serialVersionUID = -204031361554497888L;

		@Override
		public void actionPerformed (final @Nullable ActionEvent event)
		{
			final String selectedModule = selectedModule();
			assert selectedModule != null;

			// Update the UI.
			setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
			setEnabled(false);
			moduleProgress.setEnabled(true);
			moduleProgress.setValue(0);
			buildProgress.setEnabled(true);
			buildProgress.setValue(0);
			inputField.setEnabled(true);
			inputField.requestFocusInWindow();
			final StyledDocument doc = transcript.getStyledDocument();
			try
			{
				doc.remove(0, doc.getLength());
			}
			catch (final BadLocationException e)
			{
				// Shouldn't happen.
				assert false;
			}

			// Clear the build input stream.
			inputStream().clear();

			// Build the target module in a Swing worker thread.
			final BuildTask task = new BuildTask(selectedModule);
			buildTask = task;
			task.execute();
			cancelAction.setEnabled(true);
			cleanAction.setEnabled(false);
		}

		/**
		 * Construct a new {@link BuildAction}.
		 */
		public BuildAction ()
		{
			super("Build");
			putValue(
				SHORT_DESCRIPTION,
				"Build the target module.");
			putValue(ACCELERATOR_KEY, KeyStroke.getKeyStroke("control ENTER"));
		}
	}

	/**
	 * A {@code CancelAction} cancels a background {@linkplain BuildTask build
	 * task}.
	 */
	private final class CancelAction
	extends AbstractAction
	{
		/** The serial version identifier. */
		private static final long serialVersionUID = 4866074044124292436L;

		@Override
		public void actionPerformed (final @Nullable ActionEvent event)
		{
			final BuildTask task = buildTask;
			if (task != null)
			{
				task.cancel();
			}
		}

		/**
		 * Construct a new {@link CancelAction}.
		 */
		public CancelAction ()
		{
			super("Cancel Build");
			putValue(
				SHORT_DESCRIPTION,
				"Cancel the current build process.");
			putValue(ACCELERATOR_KEY, KeyStroke.getKeyStroke("control ESCAPE"));
		}
	}

	/**
	 * A {@code CleanAction} empties all compiled module repositories.
	 */
	private final class CleanAction
	extends AbstractAction
	{
		/** The serial version identifier. */
		private static final long serialVersionUID = 5674981650560448737L;

		@Override
		public void actionPerformed (final @Nullable ActionEvent event)
		{
			assert buildTask == null;
			try
			{
				// Clear all repositories.
				for (final ModuleRoot root : resolver.moduleRoots().roots())
				{
					root.repository().clear();
				}
			}
			catch (final IOException e)
			{
				throw new IndexedFileException(e);
			}
			final StyledDocument doc = transcript.getStyledDocument();
			try
			{
				doc.insertString(
					doc.getLength(),
					String.format("Repository has been cleared.%n"),
					doc.getStyle(infoStyleName));
			}
			catch (final BadLocationException e)
			{
				assert false : "This never happens.";
			}
		}

		/**
		 * Construct a new {@link CleanAction}.
		 */
		public CleanAction ()
		{
			super("Clean all");
			putValue(
				SHORT_DESCRIPTION,
				"Unload all code and wipe the compiled module cache.");
		}
	}

	/**
	 * A {@code ReportAction} dumps performance information obtained from
	 * running the primitives.
	 */
	private final class ReportAction
	extends AbstractAction
	{
		/** The serial version identifier. */
		private static final long serialVersionUID = 4345746948972392951L;

		@Override
		public void actionPerformed (final @Nullable ActionEvent event)
		{
			final StyledDocument doc = transcript.getStyledDocument();
			try
			{
				doc.insertString(
					doc.getLength(),
					Primitive.reportReturnCheckTimes(),
					doc.getStyle(infoStyleName));
			}
			catch (final BadLocationException e)
			{
				assert false : "This never happens.";
			}
		}

		/**
		 * Construct a new {@link ReportAction}.
		 */
		public ReportAction ()
		{
			super("Generate VM report");
			putValue(
				SHORT_DESCRIPTION,
				"Report any diagnostic information collected by the VM.");
		}
	}

	/**
	 * A {@code SubmitInputAction} sends a line of text from the {@linkplain
	 * #inputField input field} to standard input.
	 */
	private final class SubmitInputAction
	extends AbstractAction
	{
		/** The serial version identifier. */
		private static final long serialVersionUID = -3755146238252467204L;

		@Override
		public void actionPerformed (final @Nullable ActionEvent event)
		{
			if (inputField.isFocusOwner())
			{
				inputStream().update();
			}
		}

		/**
		 * Construct a new {@link SubmitInputAction}.
		 */
		public SubmitInputAction ()
		{
			super("Submit Input");
			putValue(
				SHORT_DESCRIPTION,
				"Submit the input field (plus a new line) to standard input.");
			putValue(ACCELERATOR_KEY, KeyStroke.getKeyStroke("ENTER"));
		}
	}

	/**
	 * A {@code RefreshAction} updates the {@linkplain #moduleTree module tree}
	 * with new information from the filesystem.
	 */
	private final class RefreshAction
	extends AbstractAction
	{
		/** The serial version identifier. */
		private static final long serialVersionUID = 8414764326820529464L;

		@Override
		public void actionPerformed (final @Nullable ActionEvent event)
		{
			final String selection = selectedModule();
			moduleTree.setModel(new DefaultTreeModel(moduleTree()));
			for (int i = 0; i < moduleTree.getRowCount(); i++)
			{
				moduleTree.expandRow(i);
			}
			if (selection != null)
			{
				final TreePath path = modulePath(selection);
				if (path != null)
				{
					moduleTree.setSelectionPath(path);
				}
			}
		}

		/**
		 * Construct a new {@link RefreshAction}.
		 */
		public RefreshAction ()
		{
			super("Refresh");
			putValue(
				SHORT_DESCRIPTION,
				"Refresh the availability of top-level modules.");
			putValue(ACCELERATOR_KEY, KeyStroke.getKeyStroke("F5"));
		}
	}

	/**
	 * A {@code BuildTask} launches the actual build of the target {@linkplain
	 * ModuleDescriptor module}.
	 */
	private final class BuildTask
	extends SwingWorker<Void, Void>
	{
		/**
		 * The fully-qualified name of the target {@linkplain ModuleDescriptor
		 * module}.
		 */
		private final @Nullable String targetModuleName;

		/**
		 * @return The name of the target module.
		 */
		private String targetModuleName ()
		{
			final String name = targetModuleName;
			assert name != null;
			return name;
		}

		/**
		 * The {@linkplain Thread thread} running the {@linkplain BuildTask
		 * build task}.
		 */
		@InnerAccess @Nullable Thread runner;

		/**
		 * Cancel the {@linkplain BuildTask build task}.
		 */
		public void cancel ()
		{
			final Thread thread = runner;
			if (thread != null)
			{
				thread.interrupt();
			}
		}

		/** The start time. */
		private long startTimeMillis;

		/** The stop time. */
		private long stopTimeMillis;

		/** The {@linkplain Throwable exception} that terminated the build. */
		private @Nullable Throwable terminator;

		@Override
		protected @Nullable Void doInBackground () throws Exception
		{
			runner = Thread.currentThread();
			startTimeMillis = System.currentTimeMillis();
			AvailRuntime runtime = null;
			try
			{
				runtime = new AvailRuntime(resolver);
				// Reopen the repositories if necessary.
				for (final ModuleRoot root : resolver.moduleRoots().roots())
				{
					root.repository().reopenIfNecessary();
				}
				final AvailBuilder builder = new AvailBuilder(runtime);
				builder.build(
					new ModuleName(targetModuleName()),
					new Continuation4<ModuleName, Long, Long, Long>()
					{
						@Override
						public void value (
							final @Nullable ModuleName moduleName,
							final @Nullable Long lineNumber,
							final @Nullable Long position,
							final @Nullable Long moduleSize)
						{
							assert moduleName != null;
							assert lineNumber != null;
							assert position != null;
							assert moduleSize != null;
							if (Thread.currentThread().isInterrupted())
							{
								if (runner == Thread.currentThread())
								{
									throw new CancellationException();
								}
							}
							invokeLater(new Runnable()
							{
								@Override
								public void run ()
								{
									updateModuleProgress(
										moduleName,
										lineNumber,
										position,
										moduleSize);
								}
							});
						}
					},
					new Continuation3<ModuleName, Long, Long>()
					{
						@Override
						public void value (
							final @Nullable ModuleName moduleName,
							final @Nullable Long position,
							final @Nullable Long globalCodeSize)
						{
							assert moduleName != null;
							assert position != null;
							assert globalCodeSize != null;
							if (Thread.currentThread().isInterrupted())
							{
								if (runner == Thread.currentThread())
								{
									throw new CancellationException();
								}
							}
							invokeLater(new Runnable()
							{
								@Override
								public void run ()
								{
									updateBuildProgress(
										moduleName,
										position,
										globalCodeSize);
								}
							});
						}
					});
				// Close all the repositories.
				for (final ModuleRoot root : resolver.moduleRoots().roots())
				{
					root.repository().close();
				}
				return null;
			}
			catch (final FiberTerminationException e)
			{
				terminator = e;
				return null;
			}
			catch (final AvailCompilerException e)
			{
				final ResolvedModuleName resolvedName =
					resolver.resolve(e.moduleName());
				if (resolvedName == null)
				{
					System.err.printf("%s%n", e.getMessage());
					throw e;
				}
				final String source = readSourceFile(
					resolvedName.sourceReference());
				if (source == null)
				{
					System.err.printf("%s%n", e.getMessage());
					throw e;
				}
				final char[] sourceBuffer = source.toCharArray();
				final StringBuilder builder = new StringBuilder();
				System.err.append(new String(
					sourceBuffer, 0, (int) e.endOfErrorLine()));
				System.err.append(e.getMessage());
				builder.append(new String(
					sourceBuffer,
					(int) e.endOfErrorLine(),
					Math.min(
						100, sourceBuffer.length - (int) e.endOfErrorLine())));
				builder.append("...\n");
				System.err.printf("%s%n", builder);
				terminator = e;
				return null;
			}
			catch (final CancellationException e)
			{
				terminator = e;
				return null;
			}
			catch (final Throwable e)
			{
				e.printStackTrace();
				terminator = e;
				return null;
			}
			finally
			{
				if (runtime != null)
				{
					runtime.destroy();
				}
				stopTimeMillis = System.currentTimeMillis();
			}
		}

		/**
		 * Report build completion (and timing) to the {@linkplain #transcript
		 * transcript}.
		 */
		private void reportDone ()
		{
			final StyledDocument doc = transcript.getStyledDocument();
			final long durationMillis = stopTimeMillis - startTimeMillis;
			final String status;
			if (terminator instanceof CancellationException)
			{
				status = "Cancelled";
			}
			else if (terminator != null)
			{
				status = "Aborted";
			}
			else
			{
				status = "Done";
			}
			try
			{
				doc.insertString(
					doc.getLength(),
					String.format(
						"%s (%d.%03ds).%n",
						status,
						durationMillis / 1000,
						durationMillis % 1000),
					doc.getStyle(infoStyleName));
			}
			catch (final BadLocationException e)
			{
				// Shouldn't happen.
				assert false;
			}
		}

		@Override
		protected void done ()
		{
			buildTask = null;
			reportDone();
			moduleProgress.setEnabled(false);
			buildProgress.setEnabled(false);
			inputField.setEnabled(false);
			cancelAction.setEnabled(false);
			buildAction.setEnabled(targetModuleName != null);
			cleanAction.setEnabled(true);
			if (terminator == null)
			{
				moduleProgress.setString("Module Progress: 100%");
				moduleProgress.setValue(100);
			}
			setCursor(null);
		}

		/**
		 * Construct a new {@link BuildTask}.
		 *
		 * @param targetModuleName
		 *        The fully-qualified name of the target {@linkplain
		 *        ModuleDescriptor module}.
		 */
		public BuildTask (final String targetModuleName)
		{
			this.targetModuleName = targetModuleName;
		}
	}

	/**
	 * {@linkplain BuildOutputStream} intercepts writes and updates the UI's
	 * {@linkplain #transcript}.
	 */
	private final class BuildOutputStream
	extends ByteArrayOutputStream
	{
		/**
		 * The {@linkplain StyledDocument styled document} underlying the
		 * {@linkplain #transcript}.
		 */
		final StyledDocument doc;

		/** The print {@linkplain Style style}. */
		final String style;

		/**
		 * Update the {@linkplain #transcript}.
		 */
		private void updateTranscript ()
		{
			final String text = toString();
			reset();
			invokeLater(new Runnable()
			{
				@Override
				public void run ()
				{
					try
					{
						doc.insertString(
							doc.getLength(),
							text,
							doc.getStyle(style));
					}
					catch (final BadLocationException e)
					{
						// Shouldn't happen.
						assert false;
					}
				}
			});
		}

		@Override
		public synchronized void write (final int b)
		{
			super.write(b);
			updateTranscript();
		}

		@Override
		public void write (final @Nullable byte[] b) throws IOException
		{
			super.write(b);
			updateTranscript();
		}

		@Override
		public synchronized void write (
			final @Nullable byte[] b,
			final int off,
			final int len)
		{
			super.write(b, off, len);
			updateTranscript();
		}

		/**
		 * Construct a new {@link BuildOutputStream}.
		 *
		 * @param isErrorStream
		 *        Is this an error stream?
		 */
		public BuildOutputStream (final boolean isErrorStream)
		{
			super(65536);
			this.doc = transcript.getStyledDocument();
			this.style = isErrorStream ? errorStyleName : outputStyleName;
		}
	}

	/**
	 * {@linkplain BuildInputStream} satisfies reads from the UI's {@linkplain
	 * #inputField input field}. It blocks reads unless some data is available.
	 */
	private final class BuildInputStream
	extends ByteArrayInputStream
	{
		/**
		 * The {@linkplain StyledDocument styled document} underlying the
		 * {@linkplain #transcript}.
		 */
		final StyledDocument doc;

		/**
		 * Clear the {@linkplain BuildInputStream input stream}. All pending
		 * data is discarded and the stream position is reset to zero
		 * ({@code 0}).
		 */
		public synchronized void clear ()
		{
			count = 0;
			pos = 0;
		}

		/**
		 * Update the contents of the {@linkplain BuildInputStream stream} with
		 * data from the {@linkplain #inputField input field}.
		 */
		public synchronized void update ()
		{
			final String text = inputField.getText() + "\n";
			final byte[] bytes = text.getBytes();
			if (pos + bytes.length >= buf.length)
			{
				final int newSize = max(
					buf.length << 1, bytes.length + buf.length);
				final byte[] newBuf = new byte[newSize];
				arraycopy(buf, 0, newBuf, 0, buf.length);
				buf = newBuf;
			}
			arraycopy(bytes, 0, buf, count, bytes.length);
			count += bytes.length;
			try
			{
				doc.insertString(
					doc.getLength(),
					text,
					doc.getStyle(inputStyleName));
			}
			catch (final BadLocationException e)
			{
				// Should never happen.
				assert false;
			}
			inputField.setText("");
			notifyAll();
		}

		@Override
		public boolean markSupported ()
		{
			return false;
		}

		@Override
		public void mark (final int readAheadLimit)
		{
			throw new UnsupportedOperationException();
		}

		@Override
		public synchronized void reset ()
		{
			throw new UnsupportedOperationException();
		}

		@Override
		public synchronized int read ()
		{
			// Block until data is available.
			try
			{
				while (pos == count)
				{
					wait();
				}
			}
			catch (final InterruptedException e)
			{
				return -1;
			}
			final int next = buf[pos++] & 0xFF;
			return next;
		}

		@Override
		public synchronized int read (
			final @Nullable byte[] readBuffer,
			final int start,
			final int requestSize)
		{
			if (requestSize <= 0)
			{
				return 0;
			}
			// Block until data is available.
			try
			{
				while (pos == count)
				{
					wait();
				}
			}
			catch (final InterruptedException e)
			{
				return -1;
			}
			final int bytesToTransfer = min(requestSize, count - pos);
			arraycopy(buf, pos, readBuffer, start, bytesToTransfer);
			pos += bytesToTransfer;
			return bytesToTransfer;
		}

		/**
		 * Construct a new {@link BuildInputStream}.
		 */
		public BuildInputStream ()
		{
			super(new byte[1024]);
			this.count = 0;
			this.doc = transcript.getStyledDocument();
		}
	}

	/**
	 * {@code CancellationException} is thrown when the user cancels the
	 * background {@linkplain BuildTask build task}.
	 */
	private final static class CancellationException
	extends RuntimeException
	{
		/** The serial version identifier. */
		private static final long serialVersionUID = -2886473176972814637L;

		/**
		 * Construct a new {@link CancellationException}.
		 */
		public CancellationException ()
		{
			// No implementation required.
		}
	}

	/**
	 * Read and answer the text of the specified {@linkplain ModuleDescriptor
	 * Avail module}.
	 *
	 * @param sourceFile
	 *        A {@linkplain File file reference} to an {@linkplain
	 *        ModuleDescriptor Avail module}.
	 * @return The text of the specified Avail source file, or {@code null} if
	 *         the source could not be retrieved.
	 */
	@InnerAccess @Nullable static String readSourceFile (
		final File sourceFile)
	{
		try
		{
			final char[] sourceBuffer = new char[(int) sourceFile.length()];
			final Reader sourceReader =
				new BufferedReader(new FileReader(sourceFile));

			int offset = 0;
			int bytesRead = -1;
			while ((bytesRead = sourceReader.read(
				sourceBuffer, offset, sourceBuffer.length - offset)) > 0)
			{
				offset += bytesRead;
			}

			sourceReader.close();
			return new String(sourceBuffer, 0, offset);
		}
		catch (final IOException e)
		{
			return null;
		}
	}

	/*
	 * Model components.
	 */

	/** The {@linkplain ModuleNameResolver module name resolver}. */
	@InnerAccess final ModuleNameResolver resolver;

	/** The current {@linkplain BuildTask build task}. */
	@InnerAccess volatile @Nullable BuildTask buildTask;

	/** The {@linkplain BuildInputStream standard input stream}. */
	private @Nullable BuildInputStream inputStream;

	/**
	 * Answer the {@linkplain BuildInputStream standard input stream}.
	 *
	 * @return The input stream.
	 */
	@InnerAccess BuildInputStream inputStream ()
	{
		final BuildInputStream stream = inputStream;
		assert stream != null;
		return stream;
	}

	/*
	 * UI components.
	 */

	/** The {@linkplain ModuleDescriptor module} {@linkplain JTree tree}. */
	@InnerAccess final JTree moduleTree;

	/**
	 * The {@linkplain JProgressBar progress bar} that displays compilation
	 * progress of the current {@linkplain ModuleDescriptor module}.
	 */
	@InnerAccess final JProgressBar moduleProgress;

	/**
	 * The {@linkplain JProgressBar progress bar} that displays the overall
	 * build progress.
	 */
	@InnerAccess final JProgressBar buildProgress;

	/**
	 * The {@linkplain JTextPane text area} that displays the {@linkplain
	 * AvailBuilder build} transcript.
	 */
	@InnerAccess final JTextPane transcript;

	/** The {@linkplain JTextField text field} that accepts standard input. */
	@InnerAccess final JTextField inputField;

	/*
	 * Actions.
	 */

	/** The {@linkplain BuildAction build action}. */
	@InnerAccess final BuildAction buildAction;

	/** The {@linkplain CancelAction cancel action}. */
	@InnerAccess final CancelAction cancelAction;

	/** The {@linkplain CleanAction clean action}. */
	@InnerAccess final CleanAction cleanAction;

	/** The {@linkplain ReportAction report action}. */
	@InnerAccess final ReportAction reportAction;

	/**
	 * Answer the (invisible) root of the {@linkplain #moduleTree module tree}.
	 *
	 * @return The root of the module tree.
	 */
	@InnerAccess TreeNode moduleTree ()
	{
		final ModuleRoots roots = resolver.moduleRoots();
		final DefaultMutableTreeNode treeRoot = new DefaultMutableTreeNode();
		for (final String rootName : roots.rootNames())
		{
			final DefaultMutableTreeNode rootNode =
				new DefaultMutableTreeNode(rootName);
			treeRoot.add(rootNode);
			final String extension = ModuleNameResolver.availExtension;
			final ModuleRoot root = roots.moduleRootFor(rootName);
			assert root != null;
			final File rootDirectory = root.sourceDirectory();
			assert rootDirectory != null;
			final File[] files = rootDirectory.listFiles(new FilenameFilter()
			{
				@Override
				public boolean accept (
					final @Nullable File dir,
					final @Nullable String name)
				{
					assert name != null;
					return name.endsWith(extension);
				}
			});
			for (final File file : files)
			{
				final String fileName = file.getName();
				final String label = fileName.substring(
					0, fileName.length() - extension.length());
				final DefaultMutableTreeNode moduleNode =
					new DefaultMutableTreeNode(label);
				rootNode.add(moduleNode);
			}
		}
		return treeRoot;
	}

	/**
	 * Answer the {@linkplain TreePath path} to the specified module name in the
	 * {@linkplain #moduleTree module tree}.
	 *
	 * @param moduleName A module name.
	 * @return A tree path, or {@code null} if the module name is not present in
	 *         the tree.
	 */
	@InnerAccess @Nullable TreePath modulePath (final String moduleName)
	{
		final String[] path = moduleName.split("/");
		assert path.length == 3;
		final TreeModel model = moduleTree.getModel();
		final DefaultMutableTreeNode treeRoot =
			(DefaultMutableTreeNode) model.getRoot();
		@SuppressWarnings("unchecked")
		final Enumeration<DefaultMutableTreeNode> roots = treeRoot.children();
		while (roots.hasMoreElements())
		{
			final DefaultMutableTreeNode rootNode = roots.nextElement();
			if (path[1].equals(rootNode.getUserObject()))
			{
				@SuppressWarnings("unchecked")
				final Enumeration<DefaultMutableTreeNode> modules =
					rootNode.children();
				while (modules.hasMoreElements())
				{
					final DefaultMutableTreeNode moduleNode =
						modules.nextElement();
					if (path[2].equals(moduleNode.getUserObject()))
					{
						return new TreePath(moduleNode.getPath());
					}
				}
			}
		}
		return null;
	}

	/**
	 * Answer the currently selected {@linkplain ModuleDescriptor module}.
	 *
	 * @return A fully-qualified module name, or {@code null} if no module is
	 *         selected.
	 */
	@InnerAccess @Nullable String selectedModule ()
	{
		final TreePath path = moduleTree.getSelectionPath();
		if (path == null)
		{
			return null;
		}
		final Object[] nodes = path.getPath();
		if (nodes.length != 3)
		{
			return null;
		}
		final StringBuilder builder = new StringBuilder();
		for (int i = 1; i < nodes.length; i++)
		{
			final DefaultMutableTreeNode node =
				(DefaultMutableTreeNode) nodes[i];
			builder.append('/');
			builder.append((String) node.getUserObject());
		}
		return builder.toString();
	}

	/**
	 * Redirect the standard streams.
	 */
	@InnerAccess void redirectStandardStreams ()
	{
		System.setOut(new PrintStream(new BuildOutputStream(false)));
		System.setErr(new PrintStream(new BuildOutputStream(true)));
		inputStream = new BuildInputStream();
		System.setIn(inputStream);
	}

	/**
	 * Update the {@linkplain #moduleProgress module progress bar}.
	 *
	 * @param moduleName
	 *        The {@linkplain ModuleDescriptor module} undergoing compilation.
	 * @param lineNumber
	 *        The current line number.
	 * @param position
	 *        The parse position, in bytes.
	 * @param moduleSize
	 *        The module size, in bytes.
	 */
	@InnerAccess void updateModuleProgress (
		final ModuleName moduleName,
		final Long lineNumber,
		final Long position,
		final Long moduleSize)
	{
		if (lineNumber == -1)
		{
			moduleProgress.setValue(0);
			moduleProgress.setString(String.format(
				"Loading compiled module: %s",
				moduleName.qualifiedName()));
		}
		else
		{
			final int percent = (int) ((position * 100) / moduleSize);
			moduleProgress.setValue(percent);
			moduleProgress.setString(String.format(
				"Compiling module: %s [line %d]: %d / %d bytes (%d%%)",
				moduleName.qualifiedName(),
				lineNumber,
				position,
				moduleSize,
				percent));
		}
	}

	/**
	 * Update the {@linkplain #buildProgress build progress bar}.
	 *
	 * @param moduleName
	 *        The {@linkplain ModuleDescriptor module} undergoing compilation.
	 * @param position
	 *        The parse position, in bytes.
	 * @param globalCodeSize
	 *        The module size, in bytes.
	 */
	@InnerAccess void updateBuildProgress (
		final ModuleName moduleName,
		final Long position,
		final Long globalCodeSize)
	{
		final int percent = (int) ((position * 100) / globalCodeSize);
		buildProgress.setValue(percent);
		buildProgress.setString(String.format(
			"Build Progress: %d / %d bytes (%d%%)",
			position,
			globalCodeSize,
			percent));
	}

	/** The user-specific {@link Preferences} for this application to use. */
	@InnerAccess final Preferences basePreferences =
		Preferences.userNodeForPackage(getClass());

	/** The key under which to organize all placement information. */
	final String placementByMonitorLayoutString = "placementByMonitorLayout";

	/** The leaf key under which to store a single window placement. */
	final String placementLeafKeyString = "placement";

	/**
	 * Answer a {@link List} of {@link Rectangle}s corresponding with the
	 * physical monitors into which {@link Frame}s may be positioned.
	 *
	 * @return The list of rectangles to which physical screens are mapped.
	 */
	@InnerAccess
	List<Rectangle> allMonitorRectangles ()
	{
		final GraphicsEnvironment graphicsEnvironment =
			GraphicsEnvironment.getLocalGraphicsEnvironment();
		final GraphicsDevice[] screens = graphicsEnvironment.getScreenDevices();
		final List<Rectangle> allRectangles = new ArrayList<Rectangle>();
		for (final GraphicsDevice screen : screens)
		{
			for (final GraphicsConfiguration gc : screen.getConfigurations())
			{
				allRectangles.add(gc.getBounds());
			}
		}
		return allRectangles;
	}

	/**
	 * Answer the {@link Preferences} node responsible for holding the default
	 * window position and size for the current monitor configuration.
	 *
	 * @param monitorRectangles
	 *            The relative locations of all physical screens.
	 *
	 * @return The {@code Preferences} node in which placement information for
	 *         the current monitor configuration can be stored and retrieved.
	 */
	@InnerAccess
	Preferences placementPreferencesNodeForRectangles (
		final List<Rectangle> monitorRectangles)
	{
		final StringBuilder allBoundsString = new StringBuilder();
		for (final Rectangle rectangle : monitorRectangles)
		{
			allBoundsString.append(
				String.format(
					"%d,%d,%d,%d;",
					rectangle.x,
					rectangle.y,
					rectangle.width,
					rectangle.height));
		}
		return basePreferences.node(
			placementByMonitorLayoutString + "/" + allBoundsString);
	}

	/**
	 * Figure out where to initially place the frame.  Use the current screen
	 * resolutions/positions to preserve placement information.  Place it on the
	 * primary screen if this configuration has not yet been encountered.
	 *
	 * @return The rectangle into which to position this {@link
	 *         AvailBuilderFrame}, or null if
	 */
	@InnerAccess
	@Nullable Rectangle getInitialRectangle ()
	{
		final List<Rectangle> rectangles = allMonitorRectangles();
		final Preferences preferences =
			placementPreferencesNodeForRectangles(rectangles);

		final String locationString = preferences.get(
			placementLeafKeyString,
			null);
		if (locationString == null)
		{
			return null;
		}
		final String[] substrings = locationString.split(",");
		if (substrings.length != 4)
		{
			return null;
		}
		try
		{
			final int x = Integer.valueOf(substrings[0]);
			final int y = Integer.valueOf(substrings[1]);
			final int w = Integer.valueOf(substrings[2]);
			final int h = Integer.valueOf(substrings[3]);
			return new Rectangle(x, y, w, h);
		}
		catch (final NumberFormatException e)
		{
			return null;
		}
	}

	/**
	 * Construct a new {@link AvailBuilderFrame}.
	 *
	 * @param resolver
	 *        The {@linkplain ModuleNameResolver module name resolver}.
	 * @param initialTarget
	 *        The initial target {@linkplain ModuleName module}, possibly the
	 *        empty string.
	 */
	@InnerAccess AvailBuilderFrame (
		final ModuleNameResolver resolver,
		final String initialTarget)
	{
		// Set module components.
		this.resolver = resolver;

		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		// Set *just* the window title...
		setTitle("Avail Builder");
		setResizable(true);

		// Create the menu bar and menus.
		final JMenuBar menuBar = new JMenuBar();
		final JMenu menu = new JMenu("Build");
		buildAction = new BuildAction();
		final JMenuItem buildItem = new JMenuItem(buildAction);
		menu.add(buildItem);
		cancelAction = new CancelAction();
		cancelAction.setEnabled(false);
		final JMenuItem cancelItem = new JMenuItem(cancelAction);
		menu.add(cancelItem);
		cleanAction = new CleanAction();
		cleanAction.setEnabled(true);
		final JMenuItem cleanItem = new JMenuItem(cleanAction);
		menu.add(cleanItem);
		reportAction = new ReportAction();
		reportAction.setEnabled(true);
		final JMenuItem reportItem = new JMenuItem(reportAction);
		menu.add(reportItem);
		menuBar.add(menu);
		setJMenuBar(menuBar);
		final JPopupMenu buildPopup = new JPopupMenu("Build");
		final JMenuItem popupBuildItem = new JMenuItem(buildAction);
		buildPopup.add(popupBuildItem);
		final RefreshAction refreshAction = new RefreshAction();
		final JMenuItem popupRefreshItem = new JMenuItem(refreshAction);
		buildPopup.add(popupRefreshItem);
		// The refresh item needs a little help ...
		InputMap inputMap = getRootPane().getInputMap(
			JComponent.WHEN_IN_FOCUSED_WINDOW);
		ActionMap actionMap = getRootPane().getActionMap();
		inputMap.put(KeyStroke.getKeyStroke("F5"), "refresh");
		actionMap.put("refresh", refreshAction);

		// Create the left pane.
		final Container leftPane = new Panel();
		leftPane.setLayout(new GridBagLayout());

		// Create the right pane.
		final Container rightPane = new Panel();
		rightPane.setLayout(new GridBagLayout());

		// Create the splitter pane separating left from right.
		final JSplitPane split = new JSplitPane(
			JSplitPane.HORIZONTAL_SPLIT,
			leftPane,
			rightPane);
		split.setDividerLocation(200);
		getContentPane().add(split);

		// Create the constraints.
		final GridBagConstraints c = new GridBagConstraints();
		c.insets = new Insets(5, 5, 5, 5);
		int y = 0;

		// Create the module tree.
		final JScrollPane moduleTreeScrollArea = new JScrollPane();
		moduleTreeScrollArea.setHorizontalScrollBarPolicy(
			HORIZONTAL_SCROLLBAR_AS_NEEDED);
		moduleTreeScrollArea.setVerticalScrollBarPolicy(
			VERTICAL_SCROLLBAR_AS_NEEDED);
		moduleTreeScrollArea.setVisible(true);
		moduleTreeScrollArea.setMinimumSize(new Dimension(100, 0));
		c.fill = GridBagConstraints.BOTH;
		c.gridheight = GridBagConstraints.REMAINDER;
		c.gridx = 0;
		c.gridy = y++;
		c.weightx = 1;
		c.weighty = 1;
		leftPane.add(moduleTreeScrollArea, c);
		c.weightx = 0;
		c.weighty = 0;
		moduleTree = new JTree(moduleTree());
		moduleTree.setToolTipText(
			"All top-level modules, organized by module root.");
		moduleTree.setComponentPopupMenu(buildPopup);
		moduleTree.setEditable(false);
		moduleTree.setEnabled(true);
		moduleTree.setFocusable(true);
		moduleTree.setFocusTraversalKeys(
			FORWARD_TRAVERSAL_KEYS,
			Collections.singleton(getAWTKeyStroke("TAB")));
		moduleTree.setFocusTraversalKeys(
			BACKWARD_TRAVERSAL_KEYS,
			Collections.singleton(getAWTKeyStroke("shift TAB")));
		moduleTree.getSelectionModel().setSelectionMode(
			TreeSelectionModel.SINGLE_TREE_SELECTION);
		moduleTree.setShowsRootHandles(true);
		moduleTree.setRootVisible(false);
		moduleTree.setVisible(true);
		moduleTree.addTreeSelectionListener(new TreeSelectionListener()
		{
			@Override
 			public void valueChanged (final @Nullable TreeSelectionEvent event)
			{
				final boolean newEnablement = selectedModule() != null;
				if (buildAction.isEnabled() != newEnablement)
				{
					buildAction.setEnabled(newEnablement);
				}
			}
		});
		moduleTree.addMouseListener(new MouseAdapter()
		{
			@SuppressWarnings("null")
			@Override
			public void mouseClicked (final @Nullable MouseEvent e)
			{
				if (buildAction.isEnabled()
					&& e.getClickCount() == 2
					&& e.getButton() == MouseEvent.BUTTON1)
				{
					final ActionEvent actionEvent = new ActionEvent(
						moduleTree, -1, "Build");
					buildAction.actionPerformed(actionEvent);
				}
			}
		});
		inputMap = moduleTree.getInputMap(
			JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
		actionMap = moduleTree.getActionMap();
		inputMap.put(KeyStroke.getKeyStroke("ENTER"), "build");
		actionMap.put("build", buildAction);
		for (int i = 0; i < moduleTree.getRowCount(); i++)
		{
			moduleTree.expandRow(i);
		}
		buildAction.setEnabled(false);
		if (!initialTarget.isEmpty())
		{
			final TreePath path = modulePath(initialTarget);
			if (path != null)
			{
				moduleTree.setSelectionPath(path);
				buildAction.setEnabled(true);
			}
		}
		moduleTreeScrollArea.setViewportView(moduleTree);

		// Create the module progress bar.
		moduleProgress = new JProgressBar(0, 100);
		moduleProgress.setToolTipText(
			"Progress indicator for the module undergoing compilation.");
		moduleProgress.setEnabled(false);
		moduleProgress.setFocusable(false);
		moduleProgress.setIndeterminate(false);
		moduleProgress.setStringPainted(true);
		moduleProgress.setString("Module Progress:");
		moduleProgress.setValue(0);
		y = 0;
		c.gridwidth = 1;
		c.gridheight = 1;
		c.gridx = 0;
		c.gridy = y++;
		rightPane.add(moduleProgress, c);

		// Create the build progress bar.
		buildProgress = new JProgressBar(0, 100);
		buildProgress.setToolTipText(
			"Progress indicator for the build.");
		buildProgress.setEnabled(false);
		buildProgress.setFocusable(false);
		buildProgress.setIndeterminate(false);
		buildProgress.setStringPainted(true);
		buildProgress.setString("Build Progress:");
		buildProgress.setValue(0);
		c.gridy = y++;
		rightPane.add(buildProgress, c);

		// Create the transcript.
		final JLabel outputLabel = new JLabel("Build Transcript:");
		c.gridy = y++;
		rightPane.add(outputLabel, c);

		final JScrollPane transcriptScrollArea = new JScrollPane();
		transcriptScrollArea.setHorizontalScrollBarPolicy(
			HORIZONTAL_SCROLLBAR_AS_NEEDED);
		transcriptScrollArea.setVerticalScrollBarPolicy(
			VERTICAL_SCROLLBAR_AS_NEEDED);
		transcriptScrollArea.setVisible(true);
		c.gridy = y++;
		// Make this row and column be where the excess space goes.
		c.weightx = 1.0;
		c.weighty = 1.0;
		rightPane.add(transcriptScrollArea, c);
		// And reset the weights...
		c.weightx = 0.0;
		c.weighty = 0.0;
		transcript = new JTextPane();
		transcript.setBorder(BorderFactory.createEtchedBorder());
		transcript.setEditable(false);
		transcript.setEnabled(true);
		transcript.setFocusable(true);
		transcript.setFocusTraversalKeys(
			FORWARD_TRAVERSAL_KEYS,
			Collections.singleton(getAWTKeyStroke("TAB")));
		transcript.setFocusTraversalKeys(
			BACKWARD_TRAVERSAL_KEYS,
			Collections.singleton(getAWTKeyStroke("shift TAB")));
		transcript.setPreferredSize(new Dimension(0, 500));
		transcript.setVisible(true);
		transcriptScrollArea.setViewportView(transcript);

		// Create the input area.
		final JLabel inputLabel = new JLabel("Console Input:");
		c.gridy = y++;
		rightPane.add(inputLabel, c);
		inputField = new JTextField();
		inputField.setToolTipText(
			"Characters entered here form the standard input of the build "
			+ "process. Characters are not made available until ENTER is "
			+ "pressed.");
		inputField.setAction(new SubmitInputAction());
		inputField.setColumns(60);
		inputField.setEditable(true);
		inputField.setEnabled(false);
		inputField.setFocusable(true);
		inputField.setFocusTraversalKeys(
			FORWARD_TRAVERSAL_KEYS,
			Collections.singleton(getAWTKeyStroke("TAB")));
		inputField.setFocusTraversalKeys(
			BACKWARD_TRAVERSAL_KEYS,
			Collections.singleton(getAWTKeyStroke("shift TAB")));
		inputField.setVisible(true);
		c.gridy = y++;
		rightPane.add(inputField, c);

		// Set up styles for the transcript.
		final StyledDocument doc = transcript.getStyledDocument();
		final SimpleAttributeSet attributes = new SimpleAttributeSet();
		final TabStop[] tabStops = new TabStop[500];
		for (int i = 0; i < tabStops.length; i++)
		{
			tabStops[i] = new TabStop(
				32.0f * (i + 1),
				TabStop.ALIGN_LEFT,
				TabStop.LEAD_DOTS);
		}
		final TabSet tabSet = new TabSet(tabStops);
		StyleConstants.setTabSet(attributes, tabSet);
		doc.setParagraphAttributes(0, doc.getLength(), attributes, false);
		final Style defaultStyle =
			StyleContext.getDefaultStyleContext().getStyle(
				StyleContext.DEFAULT_STYLE);
		StyleConstants.setFontFamily(defaultStyle, "courier");
		final Style outputStyle = doc.addStyle(outputStyleName, defaultStyle);
		StyleConstants.setForeground(outputStyle, Color.BLACK);
		final Style errorStyle = doc.addStyle(errorStyleName, defaultStyle);
		StyleConstants.setForeground(errorStyle, Color.RED);
		final Style inputStyle = doc.addStyle(inputStyleName, defaultStyle);
		StyleConstants.setForeground(inputStyle, Color.GREEN);
		final Style infoStyle = doc.addStyle(infoStyleName, defaultStyle);
		StyleConstants.setForeground(infoStyle, Color.BLUE);

		// Redirect the standard streams.
		redirectStandardStreams();

		// Save placement when closing.
		addWindowListener(new WindowAdapter()
		{
			@Override
			public void windowClosing (final @Nullable WindowEvent e)
			{
				final List<Rectangle> monitorRectangles =
					allMonitorRectangles();
				final Preferences preferences =
					placementPreferencesNodeForRectangles(monitorRectangles);
				final Rectangle rectangle = getBounds();
				final String locationString = String.format(
					"%d,%d,%d,%d",
					rectangle.x,
					rectangle.y,
					rectangle.width,
					rectangle.height);
				preferences.put(placementLeafKeyString, locationString);
				super.windowClosing(e);
			}
		});
	}

	/**
	 * @throws ClassNotFoundException
	 * @throws IllegalAccessException
	 * @throws InvocationTargetException
	 * @throws NoSuchMethodException
	 */
	static void setUpForMac ()
	throws
		ClassNotFoundException,
		IllegalAccessException,
		InvocationTargetException,
		NoSuchMethodException
	{
		System.setProperty("apple.laf.useScreenMenuBar", "true");
		System.setProperty(
			"com.apple.mrj.application.apple.menu.about.name",
			"Avail Builder ZOO");

		System.setProperty("apple.eawt.quitStrategy", "CLOSE_ALL_WINDOWS");
		final Class<?> appClass = Class.forName(
			"com.apple.eawt.Application",
			true,
			AvailBuilderFrame.class.getClassLoader());
		final Object application =
			appClass.getMethod("getApplication").invoke(null);
		final Image image =
			new ImageIcon("images/AvailHammer.png").getImage();

		appClass.getMethod("setDockIconImage", Image.class).invoke(
			application,
			image);
	}

	/**
	 * Launch the {@linkplain AvailBuilder Avail builder} {@linkplain
	 * AvailBuilderFrame UI}.
	 *
	 * @param args
	 *        The command line arguments.
	 * @throws Exception
	 *         If something goes wrong.
	 */
	public static void main (final String[] args) throws Exception
	{
		final String platform = System.getProperty("os.name");
		if (platform.toLowerCase().matches("mac os x.*"))
		{
			// Doesn't work yet.
//			setUpForMac();
		}

		final ModuleRoots roots = new ModuleRoots(
			System.getProperty("availRoots", ""));
		final String renames = System.getProperty("availRenames");
		final String initial;
		if (args.length > 0)
		{
			initial = args[0];
		}
		else
		{
			initial = "";
		}
		final Reader reader;
		if (renames == null)
		{
			reader = new StringReader("");
		}
		else
		{
			final File renamesFile = new File(renames);
			reader = new BufferedReader(new FileReader(renamesFile));
		}
		final RenamesFileParser renameParser = new RenamesFileParser(
			reader, roots);
		final ModuleNameResolver resolver = renameParser.parse();

		// Display the UI.
		invokeLater(new Runnable()
		{
			@Override
			public void run ()
			{
				final AvailBuilderFrame frame = new AvailBuilderFrame(
					resolver, initial);
				frame.pack();
				final @Nullable Rectangle preferredRectangle =
					frame.getInitialRectangle();
				if (preferredRectangle != null)
				{
					frame.setBounds(preferredRectangle);
				}
				frame.setVisible(true);
			}
		});
	}
}
