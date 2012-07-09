package org.servalproject.shell;


import android.util.Log;

public class CommandLog extends Command {

	public CommandLog(String... command) {
		super(command);
	}

	@Override
	public void output(String line) {
		Log.v("Command", line);
	}
}
