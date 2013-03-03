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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;

import com.koushikdutta.superuser.util.Settings;
import com.koushikdutta.superuser.util.StreamUtility;
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
        MenuItem about = menu.add(R.string.about);
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
        Process p = Runtime.getRuntime().exec("cat /proc/cpuinfo");
        String contents = StreamUtility.readToEnd(p.getInputStream());
        p.getInputStream().close();
        p.waitFor();
        contents = contents.toLowerCase();
        String arch = "armeabi";
        if (contents.contains("x86"))
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
                            "reboot recovery\n";
                    Process p = Runtime.getRuntime().exec("su");
                    p.getOutputStream().write(command.getBytes());
                    p.getOutputStream().close();
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
                            "chmod 6777 /system/xbin/su\n" +
                            "ln -s /system/xbin/su /system/bin/su\n" +
                            "mount -oro,remount /system\n" +
                            "sync\n";
                    Process p = Runtime.getRuntime().exec("su");
                    p.getOutputStream().write(command.getBytes());
                    p.getOutputStream().close();
                    if (p.waitFor() != 0)
                        throw new Exception("non zero result");
                    checkSu();
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
    
    void checkSu() throws Exception {
        Process p = Runtime.getRuntime().exec("su -v");
        String result = Settings.readToEnd(p.getInputStream());
        Log.i("Superuser", "Result: " + result);
        if (0 != p.waitFor())
            throw new Exception("non zero result");
        if (result == null)
            throw new Exception("no data");
        if (!result.contains(getPackageName()))
            throw new Exception("unknown su");
        // TODO: upgrades herp derp
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        final ProgressDialog dlg = new ProgressDialog(this);
        dlg.setTitle(R.string.superuser);
        dlg.setMessage(getString(R.string.checking_superuser));
        dlg.setIndeterminate(true);
        dlg.show();
        new Thread() {
            public void run() {
                boolean error = false;
                try {
                    checkSu();
                }
                catch (Exception e) {
                    e.printStackTrace();
                    error = true;
                }
                dlg.dismiss();
                if (error) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            doInstall();
                        }
                    });
                }
            };
        }.start();
    }
}
