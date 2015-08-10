package org.servalproject.shell;


import android.util.Log;

import java.io.IOException;
import java.io.OutputStream;

public class CommandLog extends Command {

	public CommandLog(String... command) {
		super(command);
	}

	@Override
	public void writeCommand(OutputStream out) throws IOException {
		Log.v("Command", getCommand());
		super.writeCommand(out);
	}

	@Override
	public void output(String line) {
		Log.v("Command", line);
	}
}
