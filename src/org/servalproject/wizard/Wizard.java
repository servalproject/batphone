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

package org.servalproject.wizard;

import org.servalproject.R;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;

public class Wizard extends Activity {

	@Override
	public boolean equals(Object o) {
		boolean didsMatch = false;
		boolean sidsMatch = false;
		boolean namesMatch = false;

		if (!(o instanceof DidResult))
			return false;
		DidResult other = (DidResult) o;

		if (other.did == null && this.did == null)
			didsMatch = true;
		if (other.sid == null && this.sid == null)
			sidsMatch = true;
		if (other.name == null && this.name == null)
			namesMatch = true;

		if (other.sid != null & this.sid != null)
			if (this.sid.equals(other.sid))
				sidsMatch = true;
		if (other.did != null & this.did != null)
			if (this.did.equals(other.did))
				sidsMatch = true;
		if (other.name != null & this.name != null)
			if (this.name.equals(other.name))
				namesMatch = true;

		return sidsMatch & didsMatch & namesMatch;
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.wizard);

		Button b = (Button) this.findViewById(R.id.btnwizard);
		b.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {

				startActivity(new Intent(Wizard.this, Instructions.class));

			}
		});
	}

}
