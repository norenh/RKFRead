/*
 * Copyright 2014 Henning Norén
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package se.norenh.rkfread;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.widget.TextView;


public class DisplayMessage extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.display_message);

	Intent intent = getIntent();
	String title = intent.getStringExtra(RKFRead.DISPLAY_TITLE);

	this.setTitle(title);

	String message = intent.getStringExtra(RKFRead.DISPLAY_MESSAGE);

	TextView tv = (TextView) findViewById(R.id.displayMessage);
	tv.setText(message);
	tv.setMovementMethod(new ScrollingMovementMethod());
    }
}
