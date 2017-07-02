/**
 * FXWindowTask.java
 * Copyright © 1993-2017, The Avail Foundation, LLC. All rights reserved.
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

package com.avail.environment.tasks;
import com.avail.builder.ResolvedModuleName;
import com.avail.environment.AvailWorkbench;
import com.avail.environment.AvailWorkbench.AbstractWorkbenchTask;
import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Scene;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;

/**
 * An {@code FXWindowTask} is a an abstract extension of {@link
 * AbstractWorkbenchTask} that is used to open JavaFX {@link Scene}s.
 *
 * @author Rich Arriaga &lt;rich@availlang.org&gt;
 */
public abstract class FXWindowTask
extends AbstractWorkbenchTask
implements WindowListener
{
	/**
	 * The {@link JFrame} that the {@link Scene} is in.
	 */
	private final @NotNull JFrame frame;

	/**
	 * The width of the {@link JFrame}.
	 */
	private final int width;

	/**
	 * The height of the {@link JFrame}.
	 */
	private final int height;

	/**
	 * Indicates that closing the {@link #frame} happened as expected. {@code
	 * true} indicates the close is clean; {@code false} otherwise.
	 */
	private boolean isCleanClose = true;

	/**
	 * Indicates a cancellation of the task is requested.
	 */
	private boolean canceled = true;

	/**
	 * Cancel this {@link FXWindowTask}.
	 */
	public void cancelTask ()
	{
		canceled = true;
		closeCleanly();
	}

	/**
	 * Clear the cancel flag.
	 */
	public void clearCanceTask ()
	{
		canceled = false;
	}

	/**
	 * A message to go with the close if {@link #isCleanClose} is {@code false}.
	 */
	private @Nullable String closeMessage = null;

	/**
	 * Close the {@link #frame} normally.
	 */
	public void closeCleanly ()
	{
		EventQueue.invokeLater(() ->
		{
			frame.setVisible(false);
			frame.dispose();
		});
	}

	/**
	 * A method that creates a new {@link Scene} specific to this task.
	 *
	 * @return A {@link Scene}.
	 */
	public abstract @NotNull Scene newScene ();

	/**
	 * A method that positions the {@link #frame} appropriately at creation.
	 */
	public void positionFrame ()
	{
		Dimension dim = Toolkit.getDefaultToolkit().getScreenSize();
		frame.setLocation(
			(dim.width >> 1) - (frame.getSize().width >> 1),
			(dim.height >> 1) - (frame.getSize().height >> 1));
	}

	/**
	 * The task that is run when the close method is fired. Override this
	 * for special functionality.
	 */
	public void cleanCloseTask ()
	{
		//Do nothing
	};

	/**
	 * Close the frame with an error message popup.
	 *
	 * @param message
	 *        The error message.
	 */
	public void erroredClose (final @NotNull String message)
	{
		EventQueue.invokeLater(() ->
		{
			isCleanClose = false;
			closeMessage = message;
			frame.setVisible(false);
			frame.dispose();
		});
	}

	@Override
	protected void executeTask () throws Exception
	{
		EventQueue.invokeLater(() ->
		{
			final JFXPanel fxPanel = new JFXPanel();
			final Scene window = newScene();

			fxPanel.setScene(window);
			//This must be called to circumvent a bug that won't be fixed
			//See https://bugs.openjdk.java.net/browse/JDK-8090517
			Platform.setImplicitExit(false);
			frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

			JPanel panel = new JPanel();
			panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
			panel.setOpaque(true);

			frame.getContentPane().add(BorderLayout.CENTER, panel);
			frame.pack();
			frame.setLocationByPlatform(true);
			frame.add(fxPanel);
			frame.setSize(width, height);
			positionFrame();
			frame.setVisible(true);
			frame.addWindowListener(this);
		});
	}

	@Override
	protected void done ()
	{
		workbench.backgroundTask = null;
		reportDone();
		workbench.availBuilder.checkStableInvariants();
		workbench.setEnablements();
		workbench.setCursor(Cursor.getDefaultCursor());
	}

	/**
	 * Construct a new {@link FXWindowTask}.
	 *
	 * @param workbench
	 *        The owning {@link AvailWorkbench}.
	 * @param title
	 *        The title of the {@link JFrame}.
	 * @param resizable
	 *        Is this resizable? {@code true} indicates yes, {@code false}
	 *        otherwise.
	 * @param width
	 *        The width of the JFrame.
	 * @param height
	 *        The height of the JFrame.
	 */
	public FXWindowTask (
		final @NotNull AvailWorkbench workbench,
		final @NotNull String title,
		final boolean resizable,
		final int width,
		final int height)
	{
		super(workbench, null);
		this.frame = new JFrame(title);
		this.frame.setResizable(resizable);
		this.width = width;
		this.height = height;
	}

	/**
	 * Construct a new {@link FXWindowTask}.
	 *
	 * @param workbench
	 *        The owning {@link AvailWorkbench}.
	 * @param moduleName
	 *        The selected {@link ResolvedModuleName}.
	 * @param title
	 *        The title of the {@link JFrame}.
	 * @param resizable
	 *        Is this resizable? {@code true} indicates yes, {@code false}
	 *        otherwise.
	 * @param width
	 *        The width of the JFrame.
	 * @param height
	 *        The height of the JFrame.
	 */
	public FXWindowTask (
		final AvailWorkbench workbench,
		final @NotNull ResolvedModuleName moduleName,
		final @NotNull String title,
		final boolean resizable,
		final int width,
		final int height)
	{
		super(workbench, moduleName);
		this.frame = new JFrame(title);
		this.frame.setResizable(resizable);
		this.width = width;
		this.height = height;
	}

	@Override
	public void windowOpened (final WindowEvent e)
	{
		//Do Nothing
	}

	@Override
	public void windowClosing (final WindowEvent e)
	{
		//Do Nothing
	}

	@Override
	public void windowClosed (final WindowEvent e)
	{
		if (canceled)
		{
			//Do nothing
		}
		else if (isCleanClose)
		{
			cleanCloseTask();
		}
		else
		{
			JOptionPane.showMessageDialog(workbench, closeMessage);
		}
	}

	@Override
	public void windowIconified (final WindowEvent e)
	{
		//Do Nothing
	}

	@Override
	public void windowDeiconified (final WindowEvent e)
	{
		//Do Nothing
	}

	@Override
	public void windowActivated (final WindowEvent e)
	{
		//Do Nothing
	}

	@Override
	public void windowDeactivated (final WindowEvent e)
	{
		//Do Nothing
	}
}
