package org.servalproject.shell;

import java.io.IOException;
import java.io.OutputStream;

import android.util.Log;

public abstract class Command {
	final String command[];
	boolean finished = false;
	int exitCode;

	public Command(String... command) {
		this.command = command;
	}

	public String getCommand() {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < command.length; i++) {
			sb.append(command[i]);
			if (i + 1 == command.length)
				sb.append('\n');
			else
				sb.append(' ');
		}
		return sb.toString();
	}

	public void writeCommand(OutputStream out) throws IOException {
		out.write(getCommand().getBytes());
	}

	public abstract void output(String line);

	public void exitCode(int code) {
		synchronized (this) {
			exitCode = code;
			finished = true;
			this.notifyAll();
		}
	}

	public void terminated() {
		exitCode(-1);
		Log.v("Command", getCommand() + " did not finish.");
	}

	// waits for this command to finish and returns the exit code
	public int exitCode() throws InterruptedException {
		synchronized (this) {
			while (!finished) {
				this.wait();
			}
		}
		return exitCode;
	}

	public boolean hasFinished() {
		return finished;
	}
}
