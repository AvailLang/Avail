/**
 * AvailBuilderFrame.java
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
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
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
import com.avail.compiler.AbstractAvailCompiler.*;
import com.avail.descriptor.*;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;
import com.avail.interpreter.levelTwo.L2Operation;
import com.avail.persistence.*;
import com.avail.persistence.IndexedRepositoryManager.ModuleVersion;
import com.avail.stacks.StacksGenerator;
import com.avail.utility.Mutable;
import com.avail.utility.evaluation.*;

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
			assert documentationTask == null;
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
			documentAction.setEnabled(false);
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
	 * A {@code GenerateDocumentationAction} instructs the {@linkplain
	 * AvailBuilder Avail builder} to recursively {@linkplain StacksGenerator
	 * generate Stacks documentation}.
	 */
	private final class GenerateDocumentationAction
	extends AbstractAction
	{
		/** The serial version identifier. */
		private static final long serialVersionUID = -8808148989587783276L;

		@Override
		public void actionPerformed (final @Nullable ActionEvent event)
		{
			assert buildTask == null;
			final String selectedModule = selectedModule();
			assert selectedModule != null;

			// Update the UI.
			setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
			setEnabled(false);
			moduleProgress.setEnabled(true);
			moduleProgress.setValue(0);
			buildProgress.setEnabled(true);
			buildProgress.setValue(0);
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

			// Generate documentation for the target module in a Swing worker
			// thread.
			final DocumentationTask task =
				new DocumentationTask(selectedModule);
			documentationTask = task;
			task.execute();
			buildAction.setEnabled(false);
			cleanAction.setEnabled(false);
			setDocumentationPathAction.setEnabled(false);
			cancelAction.setEnabled(true);
		}

		/**
		 * Construct a new {@link GenerateDocumentationAction}.
		 */
		public GenerateDocumentationAction ()
		{
			super("Generate documentation");
			putValue(
				SHORT_DESCRIPTION,
				"Generate API documentation for the selected module and "
				+ "its ancestors.");
			putValue(ACCELERATOR_KEY, KeyStroke.getKeyStroke("control G"));
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
			BuilderTask task = buildTask;
			if (task != null)
			{
				task.cancel();
			}
			task = documentationTask;
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
			super("Cancel build");
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
			assert documentationTask == null;
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
	 * running.
	 */
	private final class ReportAction
	extends AbstractAction
	{
		/** The serial version identifier. */
		private static final long serialVersionUID = 4345746948972392951L;

		@Override
		public void actionPerformed (final @Nullable ActionEvent event)
		{
			final StringBuilder builder = new StringBuilder();
			L2Operation.reportStatsOn(builder);
			builder.append("\n");
			Interpreter.reportDynamicLookups(builder);
			builder.append("\n");
			Primitive.reportRunTimes(builder);
			builder.append("\n");
			Primitive.reportReturnCheckTimes(builder);
			final StyledDocument doc = transcript.getStyledDocument();
			try
			{
				doc.insertString(
					doc.getLength(),
					builder.toString(),
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
	 * A {@code SetDocumentationPathAction} displays a {@linkplain
	 * JOptionPane modal dialog} that prompts the user for the Stacks
	 * documentation path.
	 */
	private final class SetDocumentationPathAction
	extends AbstractAction
	{
		/** The serial version identifier. */
		private static final long serialVersionUID = -1485162395297266607L;

		@Override
		public void actionPerformed (final @Nullable ActionEvent event)
		{
			final JTextField pathField =
				new JTextField(documentationPath.toString());
			final JComponent[] widgets = new JComponent[]
			{
				new JLabel("Path:"),
				pathField
			};
			JOptionPane.showMessageDialog(
				AvailBuilderFrame.this,
				widgets,
				"Set documentation path",
				JOptionPane.PLAIN_MESSAGE);
			documentationPath = Paths.get(pathField.getText());
		}

		/**
		 * Construct a new {@link SetDocumentationPathAction}.
		 */
		public SetDocumentationPathAction ()
		{
			super("Set documentation path…");
			putValue(
				SHORT_DESCRIPTION,
				"Set the Stacks documentation path.");
		}
	}

	/**
	 * A {@code ClearReportAction} clears performance information obtained from
	 * running.
	 */
	private final class ClearReportAction
	extends AbstractAction
	{
		/** The serial version identifier. */
		private static final long serialVersionUID = -8233767636511326637L;

		@Override
		public void actionPerformed (final @Nullable ActionEvent event)
		{
			L2Operation.clearAllStats();
			Primitive.clearAllStats();
			Interpreter.clearDynamicLookupStats();
			final StyledDocument doc = transcript.getStyledDocument();
			try
			{
				doc.insertString(
					doc.getLength(),
					"Statistics cleared.\n",
					doc.getStyle(infoStyleName));
			}
			catch (final BadLocationException e)
			{
				assert false : "This never happens.";
			}
		}

		/**
		 * Construct a new {@link ClearReportAction}.
		 */
		public ClearReportAction ()
		{
			super("Clear VM report");
			putValue(
				SHORT_DESCRIPTION,
				"Clear any diagnostic information collected by the VM.");
		}
	}

	/**
	 * An {@code InsertEntryPointAction} inserts a string based on the currently
	 * selected entry point into the user input field.
	 */
	private final class InsertEntryPointAction
	extends AbstractAction
	{
		/** The serial version identifier. */
		private static final long serialVersionUID = 5141044753199192996L;

		@Override
		public void actionPerformed (final @Nullable ActionEvent event)
		{
			final String selectedEntryPoint = selectedEntryPoint();
			assert selectedEntryPoint != null;
			inputField.replaceSelection(selectedEntryPoint);
			inputField.requestFocusInWindow();
		}

		/**
		 * Construct a new {@link InsertEntryPointAction}.
		 */
		public InsertEntryPointAction ()
		{
			super("Insert entry point");
			putValue(
				SHORT_DESCRIPTION,
				"Insert this entry point's name in the input area.");
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
			final TreeNode modules = newModuleTree();
			moduleTree.setModel(new DefaultTreeModel(modules));
			for (int i = moduleTree.getRowCount() - 1; i >= 0; i--)
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

			final TreeNode entryPoints = newEntryPointsTree();
			entryPointsTree.setModel(new DefaultTreeModel(entryPoints));
			for (int i = entryPointsTree.getRowCount() - 1; i >= 0; i--)
			{
				entryPointsTree.expandRow(i);
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
	 * {@code BuilderTask} is a foundation for long running {@link AvailBuilder}
	 * operations.
	 */
	private abstract class BuilderTask
	extends SwingWorker<Void, Void>
	{
		/**
		 * The fully-qualified name of the target {@linkplain ModuleDescriptor
		 * module}.
		 */
		protected final String targetModuleName;

		/**
		 * The {@linkplain Thread thread} running the {@linkplain BuildTask
		 * build task}.
		 */
		protected @Nullable Thread runnerThread;

		/**
		 * Cancel the {@linkplain BuildTask build task}.
		 */
		public final void cancel ()
		{
			availBuilder.cancel();
		}

		/** The start time. */
		private long startTimeMillis;

		/** The stop time. */
		private long stopTimeMillis;

		/** The {@linkplain Throwable exception} that terminated the build. */
		protected @Nullable Throwable terminator;

		/**
		 * Report completion (and timing) to the {@linkplain #transcript
		 * transcript}.
		 */
		protected void reportDone ()
		{
			final StyledDocument doc = transcript.getStyledDocument();
			final long durationMillis = stopTimeMillis - startTimeMillis;
			final String status;
			if (terminator instanceof CancellationException)
			{
				status = "Canceled";
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
		protected final @Nullable Void doInBackground () throws Exception
		{
			runnerThread = Thread.currentThread();
			startTimeMillis = System.currentTimeMillis();
			try
			{
				// Reopen the repositories if necessary.
				for (final ModuleRoot root : resolver.moduleRoots().roots())
				{
					root.repository().reopenIfNecessary();
				}
				executeTask();
				return null;
			}
			finally
			{
				// Close all the repositories.
				for (final ModuleRoot root : resolver.moduleRoots().roots())
				{
					root.repository().close();
				}
				stopTimeMillis = System.currentTimeMillis();
			}
		}

		/**
		 * Execute the {@link BuilderTask}.
		 *
		 * @throws Exception
		 *         If anything goes wrong.
		 */
		protected abstract void executeTask () throws Exception;

		/**
		 * Construct a new {@link BuildTask}.
		 *
		 * @param targetModuleName
		 *        The fully-qualified name of the target {@linkplain
		 *        ModuleDescriptor module}.
		 */
		protected BuilderTask (final String targetModuleName)
		{
			this.targetModuleName = targetModuleName;
		}
	}

	/**
	 * A {@code BuildTask} launches the actual build of the target {@linkplain
	 * ModuleDescriptor module}.
	 */
	private final class BuildTask
	extends BuilderTask
	{
		/**
		 * Answer a suitable {@linkplain CompilerProgressReporter compiler
		 * progress reporter}.
		 *
		 * @return A compiler progress reporter.
		 */
		private CompilerProgressReporter compilerProgressReporter ()
		{
			return new CompilerProgressReporter()
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
						if (Thread.currentThread() == buildTask().runnerThread)
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
			};
		}

		/**
		 * Answer a suitable {@linkplain Continuation3 global tracker}.
		 *
		 * @return A global tracker.
		 */
		private Continuation3<ModuleName, Long, Long> globalTracker ()
		{
			return new Continuation3<ModuleName, Long, Long>()
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
						if (Thread.currentThread() == buildTask().runnerThread)
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
			};
		}

		@Override
		protected void executeTask () throws Exception
		{
			availBuilder.buildTarget(
				new ModuleName(targetModuleName),
				compilerProgressReporter(),
				globalTracker());
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
			buildAction.setEnabled(true);
			cleanAction.setEnabled(true);
			documentAction.setEnabled(true);
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
			super(targetModuleName);
		}
	}

	/**
	 * A {@code DocumentationTask} initiates and manages documentation
	 * generation for the target {@linkplain ModuleDescriptor module}.
	 */
	private final class DocumentationTask
	extends BuilderTask
	{
		@Override
		protected void executeTask () throws Exception
		{
			availBuilder.generateDocumentation(
				new ModuleName(targetModuleName),
				documentationPath);
		}

		@Override
		protected void done ()
		{
			documentationTask = null;
			reportDone();
			moduleProgress.setEnabled(false);
			buildProgress.setEnabled(false);
			cancelAction.setEnabled(false);
			buildAction.setEnabled(true);
			cleanAction.setEnabled(true);
			setDocumentationPathAction.setEnabled(true);
			documentAction.setEnabled(true);
			if (terminator == null)
			{
				moduleProgress.setString("Module Progress: 100%");
				moduleProgress.setValue(100);
			}
			setCursor(null);
		}

		/**
		 * Construct a new {@link DocumentationTask}.
		 *
		 * @param targetModuleName
		 *        The fully-qualified name of the target {@linkplain
		 *        ModuleDescriptor module}.
		 */
		public DocumentationTask (final String targetModuleName)
		{
			super(targetModuleName);
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

	/*
	 * Model components.
	 */

	/** The {@linkplain ModuleNameResolver module name resolver}. */
	@InnerAccess final ModuleNameResolver resolver;

	/** The current {@linkplain BuildTask build task}. */
	@InnerAccess volatile @Nullable BuildTask buildTask;

	/**
	 * Answer the current {@link BuildTask} in progress.  Only applicable during
	 * a build.
	 *
	 * @return The current build task.
	 */
	@InnerAccess BuildTask buildTask ()
	{
		final BuildTask task = buildTask;
		assert task != null;
		return task;
	}

	/** The current {@linkplain DocumentationTask documentation task}. */
	@InnerAccess volatile @Nullable DocumentationTask documentationTask;

	/**
	 * Answer the current {@link DocumentationTask} in progress. Only applicable
	 * during documentation generation.
	 *
	 * @return THe current documentation task.
	 */
	@InnerAccess DocumentationTask documentationTask ()
	{
		final DocumentationTask task = documentationTask;
		assert task != null;
		return task;
	}

	/**
	 * The documentation {@linkplain Path path} for the {@linkplain
	 * StacksGenerator Stacks generator}.
	 */
	@InnerAccess Path documentationPath =
		StacksGenerator.defaultDocumentationPath;

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

	/** The {@linkplain JTree tree} of module {@linkplain A_Module#entryPoints()
	 * entry points}. */
	@InnerAccess final JTree entryPointsTree;

	/**
	 * The {@link AvailBuilder} used by this user interface.
	 */
	@InnerAccess final AvailBuilder availBuilder;

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

	/** The {@linkplain RefreshAction refresh action}. */
	@InnerAccess final RefreshAction refreshAction;

	/** The {@linkplain BuildAction build action}. */
	@InnerAccess final BuildAction buildAction;

	/** The {@linkplain CancelAction cancel action}. */
	@InnerAccess final CancelAction cancelAction;

	/** The {@linkplain CleanAction clean action}. */
	@InnerAccess final CleanAction cleanAction;

	/**
	 * The {@linkplain GenerateDocumentationAction generate documentation
	 * action}.
	 */
	@InnerAccess final GenerateDocumentationAction documentAction;

	/**
	 * The {@linkplain SetDocumentationPathAction documentation path dialog
	 * action}.
	 */
	@InnerAccess final SetDocumentationPathAction setDocumentationPathAction;

	/** The {@linkplain ReportAction report action}. */
	@InnerAccess final ReportAction reportAction;

	/** The {@linkplain ClearReportAction clear report action}. */
	@InnerAccess final ClearReportAction clearReportAction;

	/** The {@linkplain InsertEntryPointAction insert entry point action}. */
	@InnerAccess final InsertEntryPointAction insertEntryPointAction;

	/**
	 * Answer a {@link FileVisitor} suitable for recursively exploring an
	 * Avail root. A new {@code FileVisitor} should be obtained for each Avail
	 * root.
	 *
	 * @param stack
	 *        The stack on which to place Avail roots and packages.
	 * @return A {@code FileVisitor}.
	 */
	private FileVisitor<Path> moduleTreeVisitor (
		final Deque<DefaultMutableTreeNode> stack)
	{
		final String extension = ModuleNameResolver.availExtension;
		final Mutable<Boolean> isRoot = new Mutable<Boolean>(true);
		final FileVisitor<Path> visitor = new FileVisitor<Path>()
		{
			@Override
			public FileVisitResult preVisitDirectory (
					final @Nullable Path dir,
					final @Nullable BasicFileAttributes unused)
				throws IOException
			{
				assert dir != null;
				if (isRoot.value)
				{
					isRoot.value = false;
					final String rootName = dir.getFileName().toString();
					final DefaultMutableTreeNode node =
						new DefaultMutableTreeNode(rootName);
					// Add the new node to the children of the parent node, then
					// push the new node onto the stack.
					stack.peekFirst().add(node);
					stack.addFirst(node);
					return FileVisitResult.CONTINUE;
				}
				final String fileName = dir.getFileName().toString();
				if (fileName.endsWith(extension))
				{
					final String moduleName = fileName.substring(
						0, fileName.length() - extension.length());
					final DefaultMutableTreeNode node =
						new DefaultMutableTreeNode(moduleName);
					// Add the new node to the children of the parent node, then
					// push the new node onto the stack.
					stack.peekFirst().add(node);
					stack.addFirst(node);
					return FileVisitResult.CONTINUE;
				}
				return FileVisitResult.SKIP_SUBTREE;
			}

			@Override
			public FileVisitResult postVisitDirectory (
					final @Nullable Path dir,
					final @Nullable IOException ex)
				throws IOException
			{
				assert dir != null;
				// Pop the node from the stack.
				stack.removeFirst();
				if (ex != null)
				{
					ex.printStackTrace();
				}
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFile (
					final @Nullable Path file,
					final @Nullable BasicFileAttributes attrs)
				throws IOException
			{
				assert file != null;
				final String fileName = file.getFileName().toString();
				if (fileName.endsWith(extension))
				{
					final String moduleName = fileName.substring(
						0, fileName.length() - extension.length());
					final DefaultMutableTreeNode node =
						new DefaultMutableTreeNode(moduleName);
					// Add the new node to the children of the parent node.
					stack.peekFirst().add(node);
				}
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFileFailed (
					final @Nullable Path file,
					final @Nullable IOException ex)
				throws IOException
			{
				if (ex != null)
				{
					System.err.printf("couldn't visit \"%s\"", file);
					ex.printStackTrace();
				}
				return FileVisitResult.CONTINUE;
			}
		};
		return visitor;
	}

	/**
	 * Answer a {@linkplain TreeNode tree node} that represents the (invisible)
	 * root of the Avail module tree.
	 *
	 * @return The (invisible) root of the module tree.
	 */
	@InnerAccess TreeNode newModuleTree ()
	{
		final ModuleRoots roots = resolver.moduleRoots();
		final DefaultMutableTreeNode treeRoot = new DefaultMutableTreeNode(
			"(all roots)");
		// Put the invisible root onto the work stack.
		final Deque<DefaultMutableTreeNode> stack = new ArrayDeque<>();
		stack.add(treeRoot);
		for (final String rootName : roots.rootNames())
		{
			// Obtain the path associated with the module root.
			final ModuleRoot root = roots.moduleRootFor(rootName);
			assert root != null;
			final File rootDirectory = root.sourceDirectory();
			assert rootDirectory != null;
			try
			{
				Files.walkFileTree(
					Paths.get(rootDirectory.getAbsolutePath()),
					EnumSet.of(FileVisitOption.FOLLOW_LINKS),
					Integer.MAX_VALUE,
					moduleTreeVisitor(stack));
			}
			catch (final IOException e)
			{
				e.printStackTrace();
				stack.clear();
				stack.add(treeRoot);
			}
		}
		return treeRoot;
	}

	/**
	 * Answer a {@linkplain TreeNode tree node} that represents the (invisible)
	 * root of the Avail entry points tree.
	 *
	 * @return The (invisible) root of the entry points tree.
	 */
	@InnerAccess TreeNode newEntryPointsTree ()
	{
		final Object mutex = new Object();
		final Map<String, DefaultMutableTreeNode> moduleNodes = new HashMap<>();
		availBuilder.traceDirectories(
			new Continuation2<ResolvedModuleName, ModuleVersion>()
			{
				@Override
				public void value (
					final @Nullable ResolvedModuleName resolvedName,
					final @Nullable ModuleVersion moduleVersion)
				{
					assert resolvedName != null;
					assert moduleVersion != null;
					final List<String> entryPoints =
						moduleVersion.getEntryPoints();
					if (!entryPoints.isEmpty())
					{
						final String moduleLabel = resolvedName.qualifiedName();
						final DefaultMutableTreeNode moduleNode =
							new DefaultMutableTreeNode(moduleLabel);
						for (final String entryPoint : entryPoints)
						{
							final DefaultMutableTreeNode entryPointNode =
								new DefaultMutableTreeNode(entryPoint);
							moduleNode.add(entryPointNode);
						}
						synchronized (mutex)
						{
							moduleNodes.put(moduleLabel, moduleNode);
						}
					}
				}
			});
		final String [] mapKeys = moduleNodes.keySet().toArray(
			new String [moduleNodes.size()]);
		Arrays.sort(mapKeys);
		final DefaultMutableTreeNode entryPointsTreeRoot =
			new DefaultMutableTreeNode();
		for (final String moduleLabel : mapKeys)
		{
			entryPointsTreeRoot.add(moduleNodes.get(moduleLabel));
		}
		return entryPointsTreeRoot;
	}

	/**
	 * Answer the {@linkplain TreePath path} to the specified module name in the
	 * {@linkplain #moduleTree module tree}.
	 *
	 * @param moduleName A module name.
	 * @return A tree path, or {@code null} if the module name is not present in
	 *         the tree.
	 */
	@SuppressWarnings("unchecked")
	@InnerAccess @Nullable TreePath modulePath (final String moduleName)
	{
		final String[] path = moduleName.split("/");
		final TreeModel model = moduleTree.getModel();
		final DefaultMutableTreeNode treeRoot =
			(DefaultMutableTreeNode) model.getRoot();
		Enumeration<DefaultMutableTreeNode> nodes = treeRoot.children();
		int index = 1;
		while (nodes.hasMoreElements())
		{
			final DefaultMutableTreeNode node = nodes.nextElement();
			if (path[index].equals(node.getUserObject()))
			{
				index++;
				if (index == path.length)
				{
					return new TreePath(node.getPath());
				}
				nodes = node.children();
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
	 * Answer the currently selected entry point, or {@code null} if none.
	 *
	 * @return An entry point name, or {@code null} if no entry point is
	 *         selected.
	 */
	@InnerAccess @Nullable String selectedEntryPoint ()
	{
		final TreePath path = entryPointsTree.getSelectionPath();
		if (path == null)
		{
			return null;
		}
		final Object[] nodes = path.getPath();
		if (nodes.length != 3)
		{
			return null;
		}
		final DefaultMutableTreeNode node =
			(DefaultMutableTreeNode)nodes[nodes.length - 1];
		return (String)node.getUserObject();
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
		final List<Rectangle> allRectangles = new ArrayList<>();
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
	 * Information about the window layout.
	 */
	private static class LayoutConfiguration
	{
		/** The preferred location and size of the window, if specified. */
		@Nullable Rectangle placement = null;

		/**
		 * The width of the left region of the builder frame in pixels, if
		 * specified
		 */
		@Nullable Integer leftSectionWidth = null;

		/**
		 * Answer this configuration's recommended width in pixels for the left
		 * region of the window, supplying a suitable default if necessary.
		 *
		 * @return The recommended width of the left part.
		 */
		int leftSectionWidth ()
		{
			final Integer w = leftSectionWidth;
			return w != null ? w : 200;
		}

		/**
		 * The proportion, if specified, as a float between {@code 0.0} and
		 * {@code 1.0} of the height of the top left module region in relative
		 * proportional to the height of the entire builder frame.
		 */
		@Nullable Double moduleVerticalProportion = null;

		/**
		 * Add this configuration's recommended proportion of height of the
		 * modules list versus the entire frame's height, supplying a default
		 * if necessary.  It must be between 0.0 and 1.0 inclusive.
		 *
		 * @return The vertical proportion of the modules area.
		 */
		double moduleVerticalProportion ()
		{
			final Double h = moduleVerticalProportion;
			return h != null ? max(0.0, min(1.0, h)) : 0.5;
		}

		/**
		 * Answer a string representation of this configuration that is suitable
		 * for being stored and restored via the {@link
		 * LayoutConfiguration#LayoutConfiguration(String)} constructor.
		 *
		 * <p>
		 * The layout should be fairly stable to avoid treating older versions
		 * as malformed.  To that end, we use a simple list of strings, adding
		 * entries for new purposes to the end, and never removing or changing
		 * the meaning of existing entries.
		 * </p>
		 *
		 * @return A string.
		 */
		public String stringToStore ()
		{
			final String [] strings = new String [6];
			final Rectangle p = placement;
			if (p != null)
			{
				strings[0] = Integer.toString(p.x);
				strings[1] = Integer.toString(p.y);
				strings[2] = Integer.toString(p.width);
				strings[3] = Integer.toString(p.height);
			}
			final Integer w = leftSectionWidth;
			if (w != null)
			{
				strings[4] = Integer.toString(w);
			}
			final Double h = moduleVerticalProportion;
			if (h != null)
			{
				strings[5] = Double.toString(h);
			}
			final StringBuilder builder = new StringBuilder();
			boolean first = true;
			for (final String string : strings)
			{
				if (!first)
				{
					builder.append(',');
				}
				if (string != null)
				{
					builder.append(string);
				}
				first = false;
			}
			return builder.toString();
		}

		/**
		 * Construct a new {@link AvailBuilderFrame.LayoutConfiguration} with
		 * no preferences specified.
		 */
		public LayoutConfiguration ()
		{
			// all null
		}

		/**
		 * Construct a new {@link AvailBuilderFrame.LayoutConfiguration} with
		 * preferences specified by some private encoding in the provided {@link
		 * String}.
		 *
		 * @param input
		 *        A string in some encoding compatible with that produced
		 *        by {@link #stringToStore()}.
		 */
		public LayoutConfiguration (final String input)
		{
			final String [] substrings = input.split(",");
			try
			{
				if (substrings.length >= 4)
				{
					final int x = Integer.parseInt(substrings[0]);
					final int y = Integer.parseInt(substrings[1]);
					final int w = Integer.parseInt(substrings[2]);
					final int h = Integer.parseInt(substrings[3]);
					placement = new Rectangle(x, y, w, h);
				}
			}
			catch (final NumberFormatException e)
			{
				// ignore
			}
			try
			{
				if (substrings.length >= 5)
				{
					leftSectionWidth = Integer.parseInt(substrings[4]);
				}
			}
			catch (final NumberFormatException e)
			{
				// ignore
			}
			try
			{
				if (substrings.length >= 6)
				{
					moduleVerticalProportion =
						Double.parseDouble(substrings[5]);
				}
			}
			catch (final NumberFormatException e)
			{
				// ignore
			}
		}
	}

	/**
	 * Figure out how to initially lay out the frame, based on previously saved
	 * preference information.
	 *
	 * @return The initial {@link LayoutConfiguration}.
	 */
	@InnerAccess LayoutConfiguration getInitialConfiguration ()
	{
		final Preferences preferences =
			placementPreferencesNodeForRectangles(allMonitorRectangles());
		final String configurationString = preferences.get(
			placementLeafKeyString,
			null);
		if (configurationString == null)
		{
			return new LayoutConfiguration();
		}
		return new LayoutConfiguration(configurationString);
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
		final AvailRuntime runtime = new AvailRuntime(resolver);
		availBuilder = new AvailBuilder(runtime);

		// Get the existing preferences early for plugging in at the right
		// times during construction.
		final LayoutConfiguration configuration = getInitialConfiguration();

		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

		// Set *just* the window title...
		setTitle("Avail Builder");
		setResizable(true);

		// Create the actions.
		buildAction = new BuildAction();
		buildAction.setEnabled(false);
		cancelAction = new CancelAction();
		cancelAction.setEnabled(false);
		cleanAction = new CleanAction();
		cleanAction.setEnabled(true);
		documentAction = new GenerateDocumentationAction();
		documentAction.setEnabled(true);
		setDocumentationPathAction = new SetDocumentationPathAction();
		setDocumentationPathAction.setEnabled(true);
		refreshAction = new RefreshAction();
		reportAction = new ReportAction();
		reportAction.setEnabled(true);
		clearReportAction = new ClearReportAction();
		clearReportAction.setEnabled(true);
		insertEntryPointAction = new InsertEntryPointAction();
		insertEntryPointAction.setEnabled(false);

		// Create the menu bar and its menus.
		final JMenuBar menuBar = new JMenuBar();
		final JMenu buildMenu = new JMenu("Build");
		buildMenu.add(new JMenuItem(buildAction));
		buildMenu.add(new JMenuItem(cancelAction));
		buildMenu.add(new JMenuItem(cleanAction));
		buildMenu.addSeparator();
		buildMenu.add(new JMenuItem(refreshAction));
		buildMenu.addSeparator();
		buildMenu.add(new JMenuItem(reportAction));
		buildMenu.add(new JMenuItem(clearReportAction));
		menuBar.add(buildMenu);
		final JMenu documentationMenu = new JMenu("Document");
		documentationMenu.add(new JMenuItem(documentAction));
		documentationMenu.addSeparator();
		documentationMenu.add(new JMenuItem(setDocumentationPathAction));
		menuBar.add(documentationMenu);
		final JMenu runMenu = new JMenu("Run");
		runMenu.add(new JMenuItem(insertEntryPointAction));
		menuBar.add(runMenu);
		setJMenuBar(menuBar);

		final JPopupMenu buildPopup = new JPopupMenu("Modules");
		buildPopup.add(new JMenuItem(buildAction));
		buildPopup.add(new JMenuItem(documentAction));
		buildPopup.addSeparator();
		buildPopup.add(new JMenuItem(refreshAction));
		// The refresh item needs a little help ...
		InputMap inputMap = getRootPane().getInputMap(
			JComponent.WHEN_IN_FOCUSED_WINDOW);
		ActionMap actionMap = getRootPane().getActionMap();
		inputMap.put(KeyStroke.getKeyStroke("F5"), "refresh");
		actionMap.put("refresh", refreshAction);

		final JPopupMenu entryPointsPopup = new JPopupMenu("Entry points");
		entryPointsPopup.add(new JMenuItem(refreshAction));
		entryPointsPopup.add(new JMenuItem(insertEntryPointAction));

		// Collect the modules and entry points.
		final TreeNode modules = newModuleTree();
		final TreeNode entryPoints = newEntryPointsTree();

		// Create the module tree.
		final JScrollPane moduleTreeScrollArea = new JScrollPane();
		moduleTreeScrollArea.setHorizontalScrollBarPolicy(
			HORIZONTAL_SCROLLBAR_AS_NEEDED);
		moduleTreeScrollArea.setVerticalScrollBarPolicy(
			VERTICAL_SCROLLBAR_AS_NEEDED);
		moduleTreeScrollArea.setVisible(true);
		moduleTreeScrollArea.setMinimumSize(new Dimension(100, 0));
		moduleTree = new JTree(modules);
		moduleTree.setToolTipText(
			"All top-level modules, organized by module root.");
		moduleTree.setComponentPopupMenu(buildPopup);
		moduleTree.setEditable(false);
		moduleTree.setEnabled(true);
		moduleTree.setFocusable(true);
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
					e.consume();
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
		if (!initialTarget.isEmpty())
		{
			final TreePath path = modulePath(initialTarget);
			if (path != null)
			{
				moduleTree.setSelectionPath(path);
			}
		}
		moduleTreeScrollArea.setViewportView(moduleTree);

		// Create the entry points tree.
		final JScrollPane entryPointsScrollArea = new JScrollPane();
		entryPointsScrollArea.setHorizontalScrollBarPolicy(
			HORIZONTAL_SCROLLBAR_AS_NEEDED);
		entryPointsScrollArea.setVerticalScrollBarPolicy(
			VERTICAL_SCROLLBAR_AS_NEEDED);
		entryPointsScrollArea.setVisible(true);
		entryPointsScrollArea.setMinimumSize(new Dimension(100, 0));
		entryPointsTree = new JTree(entryPoints);
		entryPointsTree.setToolTipText(
			"All entry points, organized by defining module.");
		entryPointsTree.setComponentPopupMenu(entryPointsPopup);
		entryPointsTree.setEditable(false);
		entryPointsTree.setEnabled(true);
		entryPointsTree.setFocusable(true);
		entryPointsTree.getSelectionModel().setSelectionMode(
			TreeSelectionModel.SINGLE_TREE_SELECTION);
		entryPointsTree.setShowsRootHandles(true);
		entryPointsTree.setRootVisible(false);
		entryPointsTree.setVisible(true);
		entryPointsTree.addTreeSelectionListener(new TreeSelectionListener()
		{
			@Override
 			public void valueChanged (final @Nullable TreeSelectionEvent event)
			{
				final boolean newEnablement = selectedEntryPoint() != null;
				if (insertEntryPointAction.isEnabled() != newEnablement)
				{
					insertEntryPointAction.setEnabled(newEnablement);
				}
			}
		});
		entryPointsTree.addMouseListener(new MouseAdapter()
		{
			@SuppressWarnings("null")
			@Override
			public void mouseClicked (final @Nullable MouseEvent e)
			{
				if (insertEntryPointAction.isEnabled()
					&& e.getClickCount() == 2
					&& e.getButton() == MouseEvent.BUTTON1)
				{
					final ActionEvent actionEvent = new ActionEvent(
						entryPointsTree, -1, "Insert entry point");
					insertEntryPointAction.actionPerformed(actionEvent);
				}
			}
		});
		inputMap = entryPointsTree.getInputMap(
			JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
		actionMap = entryPointsTree.getActionMap();
		inputMap.put(KeyStroke.getKeyStroke("ENTER"), "build");
		actionMap.put("build", buildAction);
		for (int i = 0; i < entryPointsTree.getRowCount(); i++)
		{
			entryPointsTree.expandRow(i);
		}
		entryPointsScrollArea.setViewportView(entryPointsTree);

		// Create the module progress bar.
		moduleProgress = new JProgressBar(0, 100);
		moduleProgress.setToolTipText(
			"Progress indicator for the module undergoing compilation.");
		moduleProgress.setDoubleBuffered(true);
		moduleProgress.setEnabled(false);
		moduleProgress.setFocusable(false);
		moduleProgress.setIndeterminate(false);
		moduleProgress.setStringPainted(true);
		moduleProgress.setString("Module Progress:");
		moduleProgress.setValue(0);

		// Create the build progress bar.
		buildProgress = new JProgressBar(0, 100);
		buildProgress.setToolTipText(
			"Progress indicator for the build.");
		buildProgress.setDoubleBuffered(true);
		buildProgress.setEnabled(false);
		buildProgress.setFocusable(false);
		buildProgress.setIndeterminate(false);
		buildProgress.setStringPainted(true);
		buildProgress.setString("Build Progress:");
		buildProgress.setValue(0);

		// Create the transcript.
		final JLabel outputLabel = new JLabel("Build Transcript:");

		final JScrollPane transcriptScrollArea = new JScrollPane();
		transcriptScrollArea.setHorizontalScrollBarPolicy(
			HORIZONTAL_SCROLLBAR_AS_NEEDED);
		transcriptScrollArea.setVerticalScrollBarPolicy(
			VERTICAL_SCROLLBAR_AS_NEEDED);
		transcriptScrollArea.setVisible(true);
		// Make this row and column be where the excess space goes.
		// And reset the weights...
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
		StyleConstants.setFontFamily(defaultStyle, "Monospaced");
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
		runtime.setStandardStreams(System.out, System.err, System.in);

		final JSplitPane leftPane = new JSplitPane(
			JSplitPane.VERTICAL_SPLIT,
			true,
			moduleTreeScrollArea,
			entryPointsScrollArea);
		leftPane.setDividerLocation(configuration.moduleVerticalProportion());
		leftPane.setResizeWeight(configuration.moduleVerticalProportion());
//		addPropertyChangeListener(
//			JSplitPane.DIVIDER_LOCATION_PROPERTY,
//			new PropertyChangeListener()
//			{
//				@Override
//				public void propertyChange (
//					final @Nullable PropertyChangeEvent evt)
//				{
//					if (isValid())
//					{
//						final double proportion = leftPane.getDividerLocation()
//							/ max(leftPane.getWidth(), 1.0);
//						leftPane.setResizeWeight(proportion);
//					}
//				}
//			});
		final JPanel rightPane = new JPanel();
		final GroupLayout rightPaneLayout = new GroupLayout(rightPane);
		rightPane.setLayout(rightPaneLayout);
		rightPaneLayout.setAutoCreateGaps(true);
		rightPaneLayout.setHorizontalGroup(
			rightPaneLayout.createParallelGroup()
				.addComponent(moduleProgress)
				.addComponent(buildProgress)
				.addComponent(outputLabel)
				.addComponent(transcriptScrollArea)
				.addComponent(inputLabel)
				.addComponent(inputField));
		rightPaneLayout.setVerticalGroup(
			rightPaneLayout.createSequentialGroup()
				.addGroup(rightPaneLayout.createSequentialGroup()
					.addComponent(moduleProgress)
					.addComponent(buildProgress))
				.addGroup(rightPaneLayout.createSequentialGroup()
					.addComponent(outputLabel)
					.addComponent(transcriptScrollArea))
				.addGroup(rightPaneLayout.createSequentialGroup()
					.addComponent(inputLabel)
					.addComponent(
						inputField,
						GroupLayout.PREFERRED_SIZE,
						GroupLayout.PREFERRED_SIZE,
						GroupLayout.PREFERRED_SIZE)));

		final JSplitPane mainSplit = new JSplitPane(
			JSplitPane.HORIZONTAL_SPLIT,
			true,
			leftPane,
			rightPane);
		mainSplit.setDividerLocation(configuration.leftSectionWidth());
		getContentPane().add(mainSplit);
		pack();
		if (configuration.placement != null)
		{
			setBounds(configuration.placement);
		}

		// Save placement when closing.
		addWindowListener(new WindowAdapter()
		{
			@Override
			public void windowClosing (final @Nullable WindowEvent e)
			{
				final Preferences preferences =
					placementPreferencesNodeForRectangles(
						allMonitorRectangles());
				final LayoutConfiguration saveConfiguration =
					new LayoutConfiguration();
				saveConfiguration.placement = getBounds();
				saveConfiguration.leftSectionWidth =
					mainSplit.getDividerLocation();
				saveConfiguration.moduleVerticalProportion =
					leftPane.getDividerLocation()
						/ max(leftPane.getHeight(), 1.0);
				preferences.put(
					placementLeafKeyString,
					saveConfiguration.stringToStore());
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
			// TODO: [MvG] Doesn't work yet.
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
				frame.setVisible(true);
			}
		});
	}
}
