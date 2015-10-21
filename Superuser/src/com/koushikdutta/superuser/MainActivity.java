/*
 * Copyright (C) 2013 Koushik Dutta (@koush)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.koushikdutta.superuser;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;

import com.koushikdutta.superuser.util.Settings;
import com.koushikdutta.superuser.util.StreamUtility;
import com.koushikdutta.superuser.util.SuHelper;
import com.koushikdutta.widgets.BetterListActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class MainActivity extends BetterListActivity {
    public MainActivity() {
        super(PolicyFragment.class);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater mi = new MenuInflater(this);
        mi.inflate(R.menu.app, menu);
        MenuItem about = menu.findItem(R.id.about);
        about.setOnMenuItemClickListener(new OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                getFragmentManager()
                .beginTransaction()
                .addToBackStack(getString(R.string.about))
                .replace(getListContainerId(), new AboutFragment(), "content")
                .commit();
                return true;
            }
        });

        MenuItem settings = menu.findItem(R.id.settings);
        settings.setOnMenuItemClickListener(new OnMenuItemClickListener() {

            @Override
            public boolean onMenuItemClick(final MenuItem item) {
                getFragmentManager()
                .beginTransaction()
                .addToBackStack(getString(R.string.settings))
                .replace(getListContainerId(), new SettingsFragment(), "content")
                .commit();
                return true;
            }
        });
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Settings.applyDarkThemeSetting(this, R.style.SuperuserDarkActivity);
        super.onCreate(savedInstanceState);
    }
}
