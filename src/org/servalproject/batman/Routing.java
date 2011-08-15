/**
 * Copyright (C) 2011 The Serval Project
 *
 * This file is part of Serval Software (http://www.servalproject.org)
 *
 * Serval Software is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * This source code is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this source code; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package org.servalproject.batman;

import java.io.IOException;
import java.util.ArrayList;

import org.servalproject.system.CoreTask;

public abstract class Routing implements PeerParser {
	CoreTask coretask;

	Routing(CoreTask coretask) {
		this.coretask = coretask;
	}

	public abstract void start() throws IOException;

	public abstract void stop() throws IOException;

	public abstract boolean isRunning();

	@Override
	public abstract ArrayList<PeerRecord> getPeerList() throws IOException;
}
