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

	/*
	 * UI components.
	 */

	/**
	 * The {@linkplain JTextField text field} that indicates the target.
	 */
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
	 * The {@linkplain JTextPane text area} that displays output from the
	 * {@linkplain AvailBuilder builder}.
	 */
	@InnerAccess final @NotNull JTextPane outputArea;

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
			"%s : %d / %d bytes (%d%%)",
			moduleName.qualifiedName(),
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
			"%d / %d bytes (%d%%)",
			position,
			globalCodeSize,
			percent));
	}

	/**
	 * A {@code BuildAction} launches a {@linkplain BuildTask build task} in a
	 * Swing worker thread.
	 */
	private class BuildAction
	extends AbstractAction
	{
		/** The serial version identifier. */
		private static final long serialVersionUID = -204031361554497888L;

		@Override
		public void actionPerformed (final @NotNull ActionEvent event)
		{
			// Update the UI.
			setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
			targetField.setEnabled(false);
			moduleProgress.setEnabled(true);
			moduleProgress.setValue(0);
			buildProgress.setEnabled(true);
			buildProgress.setValue(0);
			final StyledDocument doc = outputArea.getStyledDocument();
			try
			{
				doc.remove(0, doc.getLength());
			}
			catch (final BadLocationException e)
			{
				// Shouldn't happen.
				assert false;
			}

			// Redirect the standard streams.
			System.setOut(new PrintStream(new BuildOutputStream(false)));
			System.setErr(new PrintStream(new BuildOutputStream(true)));

			// Build the target module in a Swing worker thread.
			new BuildTask().execute();
		}

		/**
		 * Construct a new {@link BuildAction}.
		 */
		public BuildAction ()
		{
			super("Build");
			putValue(SHORT_DESCRIPTION, "Build the target module.");
			putValue(ACCELERATOR_KEY, KeyStroke.getKeyStroke("ENTER"));
		}
	}

	/**
	 * A {@code BuildTask} launches the actual build of the target {@linkplain
	 * ModuleDescriptor module}.
	 */
	private class BuildTask
	extends SwingWorker<Void, Void>
	{
		/** The {@linkplain Exception exception} that terminated the build. */
		Exception terminator;

		@Override
		protected Void doInBackground () throws Exception
		{
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
			catch (final Exception e)
			{
				e.printStackTrace();
				terminator = e;
				return null;
			}
		}

		@Override
		protected void done ()
		{
			moduleProgress.setEnabled(false);
			buildProgress.setEnabled(false);
			targetField.setEnabled(true);
			if (terminator == null)
			{
				moduleProgress.setString("100%");
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
	 * {@linkplain #outputArea output area}.
	 */
	private class BuildOutputStream
	extends ByteArrayOutputStream
	{
		/**
		 * The {@linkplain StyledDocument styled document} underlying the
		 * {@linkplain #outputArea output area}.
		 */
		final @NotNull StyledDocument doc;

		/** The print {@linkplain Style style}. */
		final @NotNull String style;

		/**
		 * Update the {@linkplain #outputArea output area}.
		 */
		private void updateOutputArea ()
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
			updateOutputArea();
		}

		@Override
		public void write (final @NotNull byte[] b) throws IOException
		{
			super.write(b);
			updateOutputArea();
		}

		@Override
		public synchronized void write (
			final @NotNull byte[] b,
			final int off,
			final int len)
		{
			super.write(b, off, len);
			updateOutputArea();
		}

		/**
		 * Construct a new {@link AvailBuilderFrame.BuildOutputStream}.
		 *
		 * @param isErrorStream
		 *        Is this an error stream?
		 */
		public BuildOutputStream (final boolean isErrorStream)
		{
			super(65536);
			this.doc = outputArea.getStyledDocument();
			this.style = isErrorStream ? errorStyleName : outputStyleName;
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
		final JMenuItem buildItem = new JMenuItem(new BuildAction());
		menu.add(buildItem);
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
		final JLabel moduleProgressLabel = new JLabel("Module Progress:");
		c.gridx = 0;
		c.gridy = y++;
		contentPane.add(moduleProgressLabel, c);
		moduleProgress = new JProgressBar(0, 100);
		moduleProgress.setPreferredSize(targetField.getPreferredSize());
		moduleProgress.setEnabled(false);
		moduleProgress.setFocusable(false);
		moduleProgress.setIndeterminate(false);
		moduleProgress.setStringPainted(true);
		moduleProgress.setValue(0);
		c.gridy = y++;
		contentPane.add(moduleProgress, c);

		// Create the build progress bar.
		final JLabel buildProgressLabel = new JLabel("Build Progress:");
		c.gridy = y++;
		contentPane.add(buildProgressLabel, c);
		buildProgress = new JProgressBar(0, 100);
		buildProgress.setPreferredSize(targetField.getPreferredSize());
		buildProgress.setEnabled(false);
		buildProgress.setFocusable(false);
		buildProgress.setIndeterminate(false);
		buildProgress.setStringPainted(true);
		buildProgress.setValue(0);
		c.gridy = y++;
		contentPane.add(buildProgress, c);

		// Create the output area.
		final JLabel outputLabel = new JLabel("Build Output:");
		c.gridy = y++;
		contentPane.add(outputLabel, c);
		final JScrollPane scrollArea = new JScrollPane();
		scrollArea.setHorizontalScrollBarPolicy(HORIZONTAL_SCROLLBAR_AS_NEEDED);
		scrollArea.setVerticalScrollBarPolicy(VERTICAL_SCROLLBAR_AS_NEEDED);
		c.gridy = y++;
		contentPane.add(scrollArea, c);
		outputArea = new JTextPane();
		outputArea.setPreferredSize(new Dimension(
			targetField.getPreferredSize().width,
			targetField.getPreferredSize().height * 20));
		outputArea.setBorder(BorderFactory.createEtchedBorder());
		outputArea.setEditable(false);
		outputArea.setEnabled(true);
		outputArea.setFocusable(false);
		scrollArea.setViewportView(outputArea);

		// Set up two styles for the output area: "output" and "error".
		final StyledDocument doc = outputArea.getStyledDocument();
		final Style defaultStyle =
			StyleContext.getDefaultStyleContext().getStyle(
				StyleContext.DEFAULT_STYLE);
		StyleConstants.setFontFamily(defaultStyle, "courier");
		final Style outputStyle = doc.addStyle(outputStyleName, defaultStyle);
		StyleConstants.setForeground(outputStyle, Color.BLACK);
		final Style errorStyle = doc.addStyle(errorStyleName, defaultStyle);
		StyleConstants.setForeground(errorStyle, Color.RED);
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
