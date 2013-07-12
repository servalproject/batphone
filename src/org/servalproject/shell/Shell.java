package org.servalproject.shell;

import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import android.util.Log;

public class Shell {
	private final Process proc;
	private final DataInputStream in;
	private final OutputStream out;
	private final List<Command> commands = new ArrayList<Command>();
	private boolean close = false;
	private static final String token = "F*D^W@#FGF";

	private static Shell shell;

	public static Shell startRootShell() throws IOException {
		String cmd = "/system/bin/su";
		if (!new File(cmd).exists()) {
			cmd = "/system/xbin/su";
			if (!new File(cmd).exists())
				throw new IOException("Root shell was not found");
		}
		return new Shell(cmd, true);
	}

	public final String cmd;
	public final boolean isRoot;

	public Shell() throws IOException {
		this("/system/bin/sh", false);
	}

	private Shell(String cmd, boolean isRoot) throws IOException {
		this.cmd = cmd;
		this.isRoot = isRoot;

		Log.v("Shell", "Starting shell: " + cmd);

		proc = new ProcessBuilder(cmd).redirectErrorStream(true).start();
		in = new DataInputStream(proc.getInputStream());
		out = proc.getOutputStream();

		out.write("echo Started\n".getBytes());
		out.flush();
		boolean started = false;
		StringBuilder sb = new StringBuilder();
		while (!started) {
			String line = in.readLine();
			if (line == null)
				break;

			if ("Started".equals(line))
				started = true;

			sb.append('\n').append(line);
		}

		if (!started) {
			try {
				proc.waitFor();
			} catch (Throwable e) {
			}
			throw new IOException("Unable to start shell, exit code "
					+ proc.exitValue() + sb.toString());
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
		if (this == shell)
			shell = null;
		synchronized (commands) {
			this.close = true;
			commands.notifyAll();
		}
	}

	public boolean isClosed() {
		return this.close;
	}

	public void waitFor() throws IOException, InterruptedException {
		close();
		if (commands.size() > 0)
			commands.get(commands.size() - 1).exitCode();
	}
}
