package org.servalproject.shell;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import android.os.SystemClock;
import android.util.Log;

public class Shell {
	private final Process proc;
	private final DataInputStream in;
	private final OutputStream out;
	private final List<Command> commands = new ArrayList<Command>();
	private boolean close = false;
	private static final String token = "F*D^W@#FGF";

	private static Shell rootShell;
	private static Shell shell;

	public static Shell startRootShell() throws IOException {
		if (rootShell == null) {
			String cmd = "/system/bin/su";
			if (!new File(cmd).exists()) {
				cmd = "/system/xbin/su";
				if (!new File(cmd).exists())
					throw new IOException("Unable to locate su binary");
			}
			// keep prompting the user until they accept, we hit 10 retries, or
			// the attempt fails quickly
			int retries = 0;
			while (rootShell == null) {
				long start = SystemClock.elapsedRealtime();
				try {
					rootShell = new Shell(cmd, true);
				} catch (IOException e) {
					long delay = SystemClock.elapsedRealtime() - start;
					if (delay < 500 || retries++ >= 10)
						throw e;
				}
			}
		}
		return rootShell;
	}

	public static Shell startShell() throws IOException {
		if (shell == null) {
			shell = new Shell("/system/bin/sh", false);
		}
		return shell;
	}

	public static void runRootCommand(Command command) throws IOException {
		startRootShell().add(command);
	}

	public static void runCommand(Command command) throws IOException {
		startShell().add(command);
	}

	public static void closeRootShell() throws IOException {
		if (rootShell == null)
			return;
		rootShell.close();
	}

	public static void closeShell() throws IOException {
		if (shell == null)
			return;
		shell.close();
	}

	public final String cmd;
	public final boolean isRoot;

	public Shell(String cmd, boolean isRoot) throws IOException {
		this.cmd = cmd;
		this.isRoot = isRoot;

		Log.v("Shell", "Starting shell: " + cmd);

		proc = new ProcessBuilder(cmd).redirectErrorStream(true).start();
		in = new DataInputStream(proc.getInputStream());
		out = proc.getOutputStream();

		out.write("echo Started\n".getBytes());
		out.flush();

		while (true) {
			String line = in.readLine();
			if (line == null)
				throw new EOFException();
			if ("".equals(line))
				continue;
			if ("Started".equals(line))
				break;

			proc.destroy();
			throw new IOException("Unable to start shell, unexpected output \""
					+ line + "\"");
		}

		new Thread(input, "Shell Input").start();
		new Thread(output, "Shell Output").start();
	}

	private Runnable input = new Runnable() {
		@Override
		public void run() {
			try {
				writeCommands();
			} catch (IOException e) {
				Log.e("Shell", e.getMessage(), e);
			}
		}
	};

	private void writeCommands() throws IOException {
		try {
			int write = 0;
			while (true) {
				OutputStream out;
				synchronized (commands) {
					while (!close && write >= commands.size()) {
						commands.wait();
					}
					out = this.out;
				}
				if (write < commands.size()) {
					Command next = commands.get(write);
					next.writeCommand(out);
					String line = "\necho " + token + " " + write + " $?\n";
					out.write(line.getBytes());
					out.flush();
					write++;
				} else if (close) {
					out.write("\nexit 0\n".getBytes());
					out.flush();
					out.close();
					Log.v("Shell", "Closing shell");
					return;
				}
			}
		} catch (InterruptedException e) {
			Log.e("Shell", e.getMessage(), e);
		}
	}

	private Runnable output = new Runnable() {
		@Override
		public void run() {
			try {
				readOutput();
			} catch (IOException e) {
				Log.e("Shell", e.getMessage(), e);
			} catch (InterruptedException e) {
				Log.e("Shell", e.getMessage(), e);
			}
		}
	};

	private void readOutput() throws IOException, InterruptedException {
		Command command = null;
		int read = 0;
		while (true) {
			String line = in.readLine();

			// terminate on EOF
			if (line == null)
				break;

			// Log.v("Shell", "Out; \"" + line + "\"");
			if (command == null) {
				if (read >= commands.size()) {
					if (close)
						break;
					continue;
				}
				command = commands.get(read);
			}

			int pos = line.indexOf(token);
			if (pos > 0)
				command.output(line.substring(0, pos));
			if (pos >= 0) {
				line = line.substring(pos);
				String fields[] = line.split(" ");
				int id = Integer.parseInt(fields[1]);
				if (id == read) {
					command.exitCode(Integer.parseInt(fields[2]));
					read++;
					command = null;
					continue;
				}
			}
			command.output(line);
		}
		Log.v("Shell", "Read all output");
		proc.waitFor();
		proc.destroy();
		Log.v("Shell", "Shell destroyed");

		while (read < commands.size()) {
			if (command == null)
				command = commands.get(read);
			command.terminated();
			command = null;
			read++;
		}
	}

	public void add(Command command) {
		if (close)
			throw new IllegalStateException(
					"Unable to add commands to a closed shell");
		synchronized (commands) {
			commands.add(command);
			commands.notifyAll();
		}
	}

	public int run(Command command) throws IOException, InterruptedException {
		add(command);
		return command.exitCode();
	}

	public int countCommands() {
		return commands.size();
	}

	public void close() throws IOException {
		if (this == rootShell)
			rootShell = null;
		if (this == shell)
			shell = null;
		synchronized (commands) {
			this.close = true;
			commands.notifyAll();
		}
	}

	public void waitFor() throws IOException, InterruptedException {
		close();
		if (commands.size() > 0)
			commands.get(commands.size() - 1).exitCode();
	}
}
