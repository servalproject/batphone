package org.servalproject.shell;


public class CommandCapture extends Command {
	private StringBuilder sb = new StringBuilder();

	public CommandCapture(String... command) {
		super(command);
	}

	@Override
	public void output(String line) {
		sb.append(line).append('\n');
	}

	@Override
	public String toString() {
		return sb.toString();
	}
}
