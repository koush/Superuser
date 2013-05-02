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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnClickListener;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;

import com.koushikdutta.superuser.util.Settings;
import com.koushikdutta.superuser.util.StreamUtility;
import com.koushikdutta.superuser.util.SuHelper;
import com.koushikdutta.widgets.BetterListActivity;

public class MainActivity extends BetterListActivity {
    public MainActivity() {
        super(PolicyFragment.class);
    }

    public PolicyFragmentInternal getFragment() {
        return (PolicyFragmentInternal)super.getFragment();
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater mi = new MenuInflater(this);
        mi.inflate(R.menu.app, menu);
        MenuItem about = menu.findItem(R.id.about);
        about.setOnMenuItemClickListener(new OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                getFragment().setContent(new AboutFragment(), true, getString(R.string.about));
                return true;
            }
        });
        
        return super.onCreateOptionsMenu(menu);
    }
    
    File extractSu() throws IOException, InterruptedException {
        String arch = "armeabi";
        if (System.getProperty("os.arch").contains("x86") || System.getProperty("os.arch").contains("i686") || System.getProperty("os.arch").contains("i386"))
            arch = "x86";
        ZipFile zf = new ZipFile(getPackageCodePath());
        ZipEntry su = zf.getEntry("assets/" + arch + "/su");
        InputStream zin = zf.getInputStream(su);
        File ret = getFileStreamPath("su");
        FileOutputStream fout = new FileOutputStream(ret);
        StreamUtility.copyStream(zin, fout);
        zin.close();
        zf.close();
        fout.close();
        return ret;
    }
    
    void doRecoveryInstall() {
        final ProgressDialog dlg = new ProgressDialog(this);
        dlg.setTitle(R.string.installing);
        dlg.setMessage(getString(R.string.installing_superuser));
        dlg.setIndeterminate(true);
        dlg.show();
        new Thread() {
            void doEntry(ZipOutputStream zout, String entryName, String dest) throws IOException {
                ZipFile zf = new ZipFile(getPackageCodePath());
                ZipEntry ze = zf.getEntry(entryName);
                zout.putNextEntry(new ZipEntry(dest));
                InputStream in;
                StreamUtility.copyStream(in = zf.getInputStream(ze), zout);
                zout.closeEntry();
                in.close();
                zf.close();
            }
            
            public void run() {
                try {
                    File zip = getFileStreamPath("superuser.zip");
                    ZipOutputStream zout = new ZipOutputStream(new FileOutputStream(zip));
                    doEntry(zout, "assets/update-binary", "META-INF/com/google/android/update-binary");
                    zout.close();

                    ZipFile zf = new ZipFile(getPackageCodePath());
                    ZipEntry ze = zf.getEntry("assets/reboot");
                    InputStream in;
                    FileOutputStream reboot;
                    StreamUtility.copyStream(in = zf.getInputStream(ze), reboot = openFileOutput("reboot", MODE_PRIVATE));
                    reboot.close();
                    in.close();

                    final File su = extractSu();

                    String command =
                            String.format("cat %s > /cache/superuser.zip\n", zip.getAbsolutePath()) +
                            String.format("cat %s > /cache/su\n", su.getAbsolutePath()) +
                            String.format("cat %s > /cache/Superuser.apk\n", getPackageCodePath()) +
                            "mkdir /cache/recovery\n" +
                            "echo '--update_package=CACHE:superuser.zip' > /cache/recovery/command\n" +
                            "chmod 644 /cache/superuser.zip\n" +
                            "chmod 644 /cache/recovery/command\n" +
                            "sync\n" +
                            String.format("chmod 755 %s\n", getFileStreamPath("reboot").getAbsolutePath()) +
                            "reboot recovery\n";
                    Process p = Runtime.getRuntime().exec("su");
                    p.getOutputStream().write(command.getBytes());
                    p.getOutputStream().close();
                    File rebootScript = getFileStreamPath("reboot.sh");
                    StreamUtility.writeFile(rebootScript, "reboot recovery ; " + getFileStreamPath("reboot").getAbsolutePath() + " recovery ;");
                    p.waitFor();
                    Runtime.getRuntime().exec(new String[] { "su", "-c", ". " + rebootScript.getAbsolutePath() });
                    if (p.waitFor() != 0)
                        throw new Exception("non zero result");
                }
                catch (Exception ex) {
                    ex.printStackTrace();
                    dlg.dismiss();

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                            builder.setPositiveButton(android.R.string.ok, null);
                            builder.setTitle(R.string.install);
                            builder.setMessage(R.string.install_error);
                            builder.create().show();
                        }
                    });
                }
            }
        }.start();
    }
    
    void doSystemInstall() {
        final ProgressDialog dlg = new ProgressDialog(this);
        dlg.setTitle(R.string.installing);
        dlg.setMessage(getString(R.string.installing_superuser));
        dlg.setIndeterminate(true);
        dlg.show();
        new Thread() {
            public void run() {
                boolean _error = false;
                try {
                    final File su = extractSu();
                    final String command =
                            "mount -orw,remount /system\n" +
                            "rm /system/xbin/su\n" +
                            "rm /system/bin/su\n" +
                            "rm /system/app/Supersu.*\n" +
                            "rm /system/app/superuser.*\n" +
                            "rm /system/app/supersu.*\n" +
                            "rm /system/app/SuperUser.*\n" +
                            "rm /system/app/SuperSU.*\n" +
                            String.format("cat %s > /system/xbin/su\n", su.getAbsolutePath()) +
                            "chmod 6755 /system/xbin/su\n" +
                            "ln -s /system/xbin/su /system/bin/su\n" +
                            "mount -oro,remount /system\n" +
                            "sync\n";
                    Process p = Runtime.getRuntime().exec("su");
                    p.getOutputStream().write(command.getBytes());
                    p.getOutputStream().close();
                    if (p.waitFor() != 0)
                        throw new Exception("non zero result");
                    SuHelper.checkSu(MainActivity.this);
                }
                catch (Exception ex) {
                    _error = true;
                    ex.printStackTrace();
                }
                dlg.dismiss();
                final boolean error = _error;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                        builder.setPositiveButton(android.R.string.ok, null);
                        builder.setTitle(R.string.install);
                        
                        if (error) {
                            builder.setMessage(R.string.install_error);
                        }
                        else {
                            builder.setMessage(R.string.install_success);
                        }
                        builder.create().show();
                    }
                });
            };
        }.start();
    }
    
    void doInstall() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.install);
        builder.setMessage(R.string.install_superuser_info);
        builder.setPositiveButton(R.string.install, new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                doSystemInstall();
            }
        });
        builder.setNegativeButton(android.R.string.cancel, null);
        builder.setNeutralButton(R.string.recovery_install, new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                doRecoveryInstall();
            }
        });
        builder.create().show();
    }
    
    private void saveWhatsNew() {
        Settings.setString(this, "whats_new", WHATS_NEW);
    }
    
    // this is intentionally not localized as it will change constantly.
    private static final String WHATS_NEW = "Recovery installation fixes for some devices.";
    protected void doWhatsNew() {
        if (WHATS_NEW.equals(Settings.getString(this, "whats_new")))
            return;
        saveWhatsNew();
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.whats_new);
        builder.setIcon(R.drawable.ic_launcher);
        builder.setMessage(WHATS_NEW);
        builder.setPositiveButton(R.string.rate, new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Intent i = new Intent();
                i.setData(Uri.parse("market://details?id=com.koushikdutta.superuser"));
                startActivity(i);
            }
        });
        builder.setNegativeButton(android.R.string.cancel, null);
        builder.create().show();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Settings.applyDarkThemeSetting(this, R.style.SuperuserDarkActivity);
        super.onCreate(savedInstanceState);
        
        if (Settings.getBoolean(this, "first_run", true)) {
            saveWhatsNew();
            Settings.setBoolean(this, "first_run", false);
        }
        
        final ProgressDialog dlg = new ProgressDialog(this);
        dlg.setTitle(R.string.superuser);
        dlg.setMessage(getString(R.string.checking_superuser));
        dlg.setIndeterminate(true);
        dlg.show();
        new Thread() {
            public void run() {
                boolean _error = false;
                try {
                    SuHelper.checkSu(MainActivity.this);
                }
                catch (Exception e) {
                    e.printStackTrace();
                    _error = true;
                }
                final boolean error = _error;
                dlg.dismiss();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (error) {
                            doInstall();
                        }
                        else {
                            doWhatsNew();
                        }
                    }
                });
            };
        }.start();
    }
}
