/**
 * AvailBuilderFrame.java
 * Copyright © 1993-2012, Mark van Gulik and Todd L Smith.
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
import java.util.*;
import javax.swing.*;
import javax.swing.text.*;
import com.avail.AvailRuntime;
import com.avail.annotations.*;
import com.avail.compiler.*;
import com.avail.descriptor.*;
import com.avail.utility.*;

/**
 * {@code AvailBuilderFrame} is a simple Ui for the {@linkplain AvailBuilder
 * Avail builder}.
 *
 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
 */
public class AvailBuilderFrame
extends JFrame
{
	/** The serial version identifier. */
	private static final long serialVersionUID = 5144194637595188046L;

	/** The {@linkplain Style style} to use for standard output. */
	private static final @NotNull String outputStyleName = "output";

	/** The {@linkplain Style style} to use for standard error. */
	private static final @NotNull String errorStyleName = "error";

	/** The {@linkplain Style style} to use for standard input. */
	private static final @NotNull String inputStyleName = "input";

	/** The {@linkplain Style style} to use for notifications. */
	private static final @NotNull String infoStyleName = "info";

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
		public void actionPerformed (final @NotNull ActionEvent event)
		{
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
			inputStream.clear();

			// Build the target module in a Swing worker thread.
			buildTask = new BuildTask();
			buildTask.execute();
			cancelAction.setEnabled(true);
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
		public void actionPerformed (final @NotNull ActionEvent event)
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
	 * A {@code SubmitInputAction} sends a line of text from the {@linkplain
	 * #inputField input field} to standard input.
	 */
	private final class SubmitInputAction
	extends AbstractAction
	{
		/** The serial version identifier. */
		private static final long serialVersionUID = -3755146238252467204L;

		@Override
		public void actionPerformed (final @NotNull ActionEvent event)
		{
			if (inputField.isFocusOwner())
			{
				inputStream.update();
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
	 * A {@code BuildTask} launches the actual build of the target {@linkplain
	 * ModuleDescriptor module}.
	 */
	private final class BuildTask
	extends SwingWorker<Void, Void>
	{
		/**
		 * The {@linkplain Thread thread} running the {@linkplain BuildTask
		 * build task}.
		 */
		@InnerAccess Thread runner;

		/**
		 * Cancel the {@linkplain BuildTask build task}.
		 */
		public void cancel ()
		{
			if (runner != null)
			{
				runner.interrupt();
			}
		}

		/** The start time. */
		private long startTimeMillis;

		/** The stop time. */
		private long stopTimeMillis;

		/** The {@linkplain Exception exception} that terminated the build. */
		private Exception terminator;

		@Override
		protected Void doInBackground () throws Exception
		{
			runner = Thread.currentThread();
			startTimeMillis = System.currentTimeMillis();
			try
			{
				AvailObject.clearAllWellKnownObjects();
				AvailObject.createAllWellKnownObjects();
				final AvailBuilder builder = new AvailBuilder(
					new AvailRuntime(resolver),
					new ModuleName(targetField.getText()));
				builder.buildTarget(
					new Continuation4<ModuleName, Long, Long, Long>()
					{
						@Override
						public void value (
							final @NotNull ModuleName moduleName,
							final @NotNull Long lineNumber,
							final @NotNull Long position,
							final @NotNull Long moduleSize)
						{
							if (Thread.currentThread().isInterrupted())
							{
								assert runner == Thread.currentThread();
								throw new CancellationException();
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
							final @NotNull ModuleName moduleName,
							final @NotNull Long position,
							final @NotNull Long globalCodeSize)
						{
							if (Thread.currentThread().isInterrupted())
							{
								assert runner == Thread.currentThread();
								throw new CancellationException();
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
					resolvedName.fileReference());
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
			catch (final Exception e)
			{
				e.printStackTrace();
				terminator = e;
				return null;
			}
			finally
			{
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
						"%s (%d.%03ds).",
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
			buildAction.setEnabled(true);
			if (terminator == null)
			{
				moduleProgress.setString("Module Progress: 100%");
				moduleProgress.setValue(100);
			}
			setCursor(null);
		}

		/**
		 * Construct a new {@link AvailBuilderFrame.BuildTask}.
		 *
		 */
		public BuildTask ()
		{
			// No implementation required.
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
		final @NotNull StyledDocument doc;

		/** The print {@linkplain Style style}. */
		final @NotNull String style;

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
		public void write (final @NotNull byte[] b) throws IOException
		{
			super.write(b);
			updateTranscript();
		}

		@Override
		public synchronized void write (
			final @NotNull byte[] b,
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
		final @NotNull StyledDocument doc;

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
			final byte[] readBuffer,
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
	private final class CancellationException
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
	@InnerAccess static String readSourceFile (
		final @NotNull File sourceFile)
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
	@InnerAccess final @NotNull ModuleNameResolver resolver;

	/** The current {@linkplain BuildTask build task}. */
	@InnerAccess volatile @NotNull BuildTask buildTask;

	/** The {@linkplain BuildInputStream standard input stream}. */
	@InnerAccess @NotNull BuildInputStream inputStream;

	/*
	 * UI components.
	 */

	/** The {@linkplain JTextField text field} that indicates the target. */
	@InnerAccess final @NotNull JTextField targetField;

	/**
	 * The {@linkplain JProgressBar progress bar} that displays compilation
	 * progress of the current {@linkplain ModuleDescriptor module}.
	 */
	@InnerAccess final @NotNull JProgressBar moduleProgress;

	/**
	 * The {@linkplain JProgressBar progress bar} that displays the overall
	 * build progress.
	 */
	@InnerAccess final @NotNull JProgressBar buildProgress;

	/**
	 * The {@linkplain JTextPane text area} that displays the {@linkplain
	 * AvailBuilder build} transcript.
	 */
	@InnerAccess final @NotNull JTextPane transcript;

	/** The {@linkplain JTextField text field} that accepts standard input. */
	@InnerAccess final @NotNull JTextField inputField;

	/*
	 * Actions.
	 */

	/** The {@linkplain BuildAction build action}. */
	@InnerAccess final @NotNull BuildAction buildAction;

	/** The {@linkplain CancelAction cancel action}. */
	@InnerAccess final @NotNull CancelAction cancelAction;

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
		final @NotNull ModuleName moduleName,
		final @NotNull Long lineNumber,
		final @NotNull Long position,
		final @NotNull Long moduleSize)
	{
		final int percent = (int) ((position * 100) / moduleSize);
		moduleProgress.setValue(percent);
		moduleProgress.setString(String.format(
			"Module Progress: %s [line %d]: %d / %d bytes (%d%%)",
			moduleName.qualifiedName(),
			lineNumber,
			position,
			moduleSize,
			percent));
	}

	/**
	 * Update the {@linkplain #moduleProgress module progress bar}.
	 *
	 * @param moduleName
	 *        The {@linkplain ModuleDescriptor module} undergoing compilation.
	 * @param position
	 *        The parse position, in bytes.
	 * @param globalCodeSize
	 *        The module size, in bytes.
	 */
	@InnerAccess void updateBuildProgress (
		final @NotNull ModuleName moduleName,
		final @NotNull Long position,
		final @NotNull Long globalCodeSize)
	{
		final int percent = (int) ((position * 100) / globalCodeSize);
		buildProgress.setValue(percent);
		buildProgress.setString(String.format(
			"Build Progress: %d / %d bytes (%d%%)",
			position,
			globalCodeSize,
			percent));
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
		final @NotNull ModuleNameResolver resolver,
		final String initialTarget)
	{
		// Set module components.
		this.resolver = resolver;

		// Set properties of the frame.
		setTitle("Avail Builder");
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

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
		menuBar.add(menu);
		setJMenuBar(menuBar);

		// Get the content pane.
		final Container contentPane = getContentPane();

		// Create and establish the layout.
		final GridBagLayout layout = new GridBagLayout();
		setLayout(layout);

		// Create the constraints.
		final GridBagConstraints c = new GridBagConstraints();
		c.insets = new Insets(5, 5, 5, 5);
		int y = 0;

		// Create the target module entry field.
		final JLabel targetLabel = new JLabel("Target:");
		c.gridx = 0;
		c.gridy = y++;
		contentPane.add(targetLabel, c);
		targetField = new JTextField(initialTarget);
		targetField.setToolTipText(
			"The fully-qualified name of the module to build.");
		targetField.setAction(buildAction);
		targetField.setColumns(80);
		targetField.setEditable(true);
		targetField.setEnabled(true);
		targetField.setFocusable(true);
		targetField.setFocusTraversalKeys(
			FORWARD_TRAVERSAL_KEYS,
			Collections.singleton(getAWTKeyStroke("TAB")));
		targetField.setFocusTraversalKeys(
			BACKWARD_TRAVERSAL_KEYS,
			Collections.singleton(getAWTKeyStroke("shift TAB")));
		targetField.setVisible(true);
		c.gridx = 0;
		c.gridy = y++;
		contentPane.add(targetField, c);

		// Create the module progress bar.
		moduleProgress = new JProgressBar(0, 100);
		moduleProgress.setToolTipText(
			"Progress indicator for the module undergoing compilation.");
		moduleProgress.setPreferredSize(targetField.getPreferredSize());
		moduleProgress.setEnabled(false);
		moduleProgress.setFocusable(false);
		moduleProgress.setIndeterminate(false);
		moduleProgress.setStringPainted(true);
		moduleProgress.setString("Module Progress:");
		moduleProgress.setValue(0);
		c.gridx = 0;
		c.gridy = y++;
		contentPane.add(moduleProgress, c);

		// Create the build progress bar.
		buildProgress = new JProgressBar(0, 100);
		buildProgress.setToolTipText(
			"Progress indicator for the build.");
		buildProgress.setPreferredSize(targetField.getPreferredSize());
		buildProgress.setEnabled(false);
		buildProgress.setFocusable(false);
		buildProgress.setIndeterminate(false);
		buildProgress.setStringPainted(true);
		buildProgress.setString("Build Progress:");
		buildProgress.setValue(0);
		c.gridy = y++;
		contentPane.add(buildProgress, c);

		// Create the transcript.
		final JLabel outputLabel = new JLabel("Build Transcript:");
		c.gridy = y++;
		contentPane.add(outputLabel, c);
		final JScrollPane scrollArea = new JScrollPane();
		scrollArea.setHorizontalScrollBarPolicy(HORIZONTAL_SCROLLBAR_AS_NEEDED);
		scrollArea.setVerticalScrollBarPolicy(VERTICAL_SCROLLBAR_AS_NEEDED);
		c.gridy = y++;
		contentPane.add(scrollArea, c);
		transcript = new JTextPane();
		transcript.setToolTipText(
			"The build transcript. Intermixes characters written to the "
			+ "standard output and standard error, as well as characters read "
			+ "from the standard input stream.");
		transcript.setPreferredSize(new Dimension(
			targetField.getPreferredSize().width,
			targetField.getPreferredSize().height * 20));
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
		scrollArea.setViewportView(transcript);

		// Create the input area.
		final JLabel inputLabel = new JLabel("Console Input:");
		c.gridy = y++;
		contentPane.add(inputLabel, c);
		inputField = new JTextField();
		inputField.setToolTipText(
			"Characters entered here form the standard input of the build "
			+ "process. Characters are not made available until ENTER is "
			+ "pressed.");
		inputField.setAction(new SubmitInputAction());
		inputField.setColumns(80);
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
		contentPane.add(inputField, c);

		// Set up styles for the transcript.
		final StyledDocument doc = transcript.getStyledDocument();
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
	public static void main (final @NotNull String[] args)
		throws Exception
	{
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
				frame.setVisible(true);
			}
		});
	}
}
