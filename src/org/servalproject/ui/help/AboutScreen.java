/* Copyright (C) 2012 The Serval Project
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
package org.servalproject.ui.help;

import org.servalproject.R;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

/**
 * help screens - about Serval
 *
 * @author Romana Challans <romana@servalproject.org>
 */

/*
 * Help system now embedded HTML
 */

public class AboutScreen extends HtmlHelp {
	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		viewId = R.layout.aboutview;
		super.onCreate(savedInstanceState);

		// Get thee hence to the Donate Screen
		Button btnDonate = (Button) this.findViewById(R.id.btnDonate);
		btnDonate.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View arg0) {
				AboutScreen.this.startActivity(new Intent(AboutScreen.this,
						DonateScreen.class));
			}
		});
	}

}