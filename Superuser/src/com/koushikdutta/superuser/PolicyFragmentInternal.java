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

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.FragmentActivity;
import android.text.format.DateFormat;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;

import com.koushikdutta.superuser.db.LogEntry;
import com.koushikdutta.superuser.db.SuDatabaseHelper;
import com.koushikdutta.superuser.db.SuperuserDatabaseHelper;
import com.koushikdutta.superuser.db.UidPolicy;
import com.koushikdutta.widgets.FragmentInterfaceWrapper;
import com.koushikdutta.widgets.ListContentFragmentInternal;
import com.koushikdutta.widgets.ListItem;

public class PolicyFragmentInternal extends ListContentFragmentInternal {
    
	private static final String DATA_BUNDLE_KEY = "deleted";
	
	public PolicyFragmentInternal(FragmentInterfaceWrapper fragment) {
        super(fragment);
    }
    
    ContextThemeWrapper mWrapper;
    @Override
    public Context getContext() {
        if (mWrapper != null)
            return mWrapper;
        TypedValue value = new TypedValue();
        super.getContext().getTheme().resolveAttribute(R.attr.largeIconTheme, value, true);
        mWrapper = new ContextThemeWrapper(super.getContext(), value.resourceId);
        return mWrapper;
    }
    
    void showAllLogs() {
        setContent(null, null);
        getListView().clearChoices();
    }
    
    void load() {
        clear();
        final ArrayList<UidPolicy> policies = SuDatabaseHelper.getPolicies(getActivity());
        
        SQLiteDatabase db = new SuperuserDatabaseHelper(getActivity()).getReadableDatabase(); 
        try {
            for (UidPolicy up: policies) {
                int last = 0;
                ArrayList<LogEntry> logs = SuperuserDatabaseHelper.getLogs(db, up, 1);
                if (logs.size() > 0)
                    last = logs.get(0).date;
                addPolicy(up, last);
            }
        }
        finally {
            db.close();
        }
    }
    
    public void onResume() {
        super.onResume();
        load();
    }

    FragmentInterfaceWrapper mContent;

    
    @Override
    public void onCreate(Bundle savedInstanceState, View view) {
        super.onCreate(savedInstanceState, view);

        getFragment().setHasOptionsMenu(true);
        
        setEmpty(R.string.no_apps);
        
        load();

        if ("com.koushikdutta.superuser".equals(getContext().getPackageName())) {
            ImageView watermark = (ImageView)view.findViewById(R.id.watermark);
            if (watermark != null)
                watermark.setImageResource(R.drawable.clockwork512);
        }
        if (!isPaged())
            showAllLogs();
    }
    
    public Date getLastDate(int last) {
        return new Date((long)last * 1000);
    }

    void addPolicy(final UidPolicy up, final int last) {
        java.text.DateFormat df = DateFormat.getLongDateFormat(getActivity());
        String date;
        if (last == 0)
            date = null;
        else
            date = df.format(getLastDate(last));
        ListItem li = addItem(up.getPolicyResource(), new ListItem(this, up.name, date) {
            public void onClick(View view) {
                super.onClick(view);

                setContent(this, up);
            };
            @Override
            public boolean onLongClick() {
            	if(!isTablet(getActivity()))
            		showExtraActions(up, this);
            	else
            		showInfoDialog();
            	return true;
            }
        });
        
        Drawable icon = Helper.loadPackageIcon(getActivity(), up.packageName);
        if (icon == null)
            li.setIcon(R.drawable.ic_launcher);
        else
            li.setDrawable(icon);
    }
    
    private void showInfoDialog(){
    	AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(R.string.tablet_mode);
        Resources res = getResources();
        builder.setMessage(String.format(res.getString(R.string.tablet_mode_info),
                res.getString(R.string.submit_error)));
        builder.setPositiveButton(R.string.got_it, new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        builder.setNegativeButton(R.string.submit_error, new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
            	Activity act = getActivity();
            	//Get screen width x height in pixels
                DisplayMetrics displayMetrics = new DisplayMetrics();
                WindowManager wm = (WindowManager) act.getApplicationContext().getSystemService(Context.WINDOW_SERVICE); // the results will be higher than using the activity context object or the getWindowManager() shortcut
                wm.getDefaultDisplay().getMetrics(displayMetrics);
                int screenWidth = displayMetrics.widthPixels;
                int screenHeight = displayMetrics.heightPixels;
                //Get screen density
                Map <Float, String> densities = new HashMap<Float, String>(5, 1f);
                densities.put(Float.valueOf(0.75f), "0.75 - ldpi");
                densities.put(Float.valueOf(1f), "1 - mdpi");
                densities.put(Float.valueOf(1.5f), "1.5 - hdpi");
                densities.put(Float.valueOf(2f), "2 - xhdpi");
                densities.put(Float.valueOf(3f), "3 - xxhdpi");
                densities.put(Float.valueOf(4f), "4 - xxxhdpi");
                String screenDensity = densities.get(Float.valueOf(displayMetrics.density));
                //Create the debug report
                StringBuilder data = new StringBuilder();
                data.append("DEBUG DATA:");
                data.append("\nVERSION.RELEASE {"+Build.VERSION.RELEASE+"}");
                data.append("\nVERSION.INCREMENTAL {"+Build.VERSION.INCREMENTAL+"}");
                data.append("\nVERSION.SDK_INT {"+Build.VERSION.SDK_INT+"}");
                data.append("\nFINGERPRINT {"+Build.FINGERPRINT+"}");
                data.append("\nBOARD {"+Build.BOARD+"}");
                data.append("\nBRAND {"+Build.BRAND+"}");
                data.append("\nDEVICE {"+Build.DEVICE+"}");
                data.append("\nMANUFACTURER {"+Build.MANUFACTURER+"}");
                data.append("\nMODEL {"+Build.MODEL+"}");
                data.append("\nDISPLAY {"+Build.DISPLAY+"}");
                data.append("\nHARDWARE {"+Build.HARDWARE+"}");
                data.append("\nPRODUCT {"+Build.PRODUCT+"}");
                data.append("\nWIDTH x HEIGHT (pixels) {"+screenWidth+" x "+screenHeight+"}");
                data.append("\nSCREEN DENSITY {"+screenDensity+"}");
                Intent email = new Intent(Intent.ACTION_SEND);
            	email.putExtra(Intent.EXTRA_EMAIL, new String[]{"littleprog@gmail.com"});		  
            	email.putExtra(Intent.EXTRA_SUBJECT, "Superuser Actions in long-click");
            	email.putExtra(Intent.EXTRA_TEXT, data.toString());
            	email.setType("message/rfc822");
            	act.startActivity(Intent.createChooser(email, "Choice App to send report:"));
            }
        });
        AlertDialog dialog = builder.create();
        dialog.show();
    }
    
    /* This method checks if my device is a tablet. I have to adopt the 
     * xml way to do it which is described also here
     * (http://stackoverflow.com/questions/16784101/how-to-find-tablet-or-phone-in-android-programmatically)
     * but it's ok for now. Maybe later...
     */
    private boolean isTablet(Context context) {
        boolean xlarge = ((context.getResources().getConfiguration().screenLayout & 
        		Configuration.SCREENLAYOUT_SIZE_MASK) == 4);
        boolean large = ((context.getResources().getConfiguration().screenLayout & 
        		Configuration.SCREENLAYOUT_SIZE_MASK) == Configuration.SCREENLAYOUT_SIZE_LARGE);
        return (xlarge || large);
    }
    
    @SuppressLint("HandlerLeak")
    private void showExtraActions(final UidPolicy up, final ListItem item){
    	AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(up.name);
        builder.setIcon(Helper.loadPackageIcon(getActivity(), up.packageName));
        final String permissionChange = (up.policy.equalsIgnoreCase(UidPolicy.ALLOW)) ? 
        		getResources().getText(R.string.deny).toString() : 
        			getResources().getText(R.string.allow).toString();
        String[] items = new String[] {permissionChange, getString(R.string.revoke_permission), 
        		getString(R.string.run), getString(R.string.details)};
        builder.setItems(items, new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch (which) {
                case 0:
                	if(permissionChange.equalsIgnoreCase(
                			getResources().getText(R.string.allow).toString())){
                		up.setPolicy(UidPolicy.ALLOW);
                	}
                	else{
                		up.setPolicy(UidPolicy.DENY);
                	}
                	SuDatabaseHelper.setPolicy(getActivity(), up);
                    //update the adapters
                    /*
                     * TODO I should find a better way but this is ok 
                     * for the moment!
                     */                	
                	Intent i = new Intent(getContext(), MainActivity.class);
                	i.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    getContext().startActivity(i); 
                	break;
                case 1:
                	/* I want the activity to be alive. After all the result will be 
                	 * shown immediately to the user when he/she will see that an 
                	 * application is removed or show the error dialog.
                	 */                	
                	final Handler handler = new Handler(){
                		@Override
                		public void handleMessage(Message msg) {
                			if(msg.getData().getBoolean(DATA_BUNDLE_KEY))
                				removeItem(item);
                			else
                				showErrorDialog(up, R.string.db_delete_error);
                		}
                	};
                    new Thread(){
                    	public void run(){
                    		final boolean done = SuDatabaseHelper.delete(getActivity(), up);
                    		Message msg = handler.obtainMessage();
                    		Bundle bundle = new Bundle();
                    		bundle.putBoolean(DATA_BUNDLE_KEY, done);
                    		msg.setData(bundle);
                    		handler.sendMessage(msg);
                    	}
                    }.start();
                    //dismiss the actions' dialog
                    dialog.dismiss();
                    break;
                case 2:
                	Activity activity = getActivity();
                	PackageManager pm = activity.getPackageManager();
                	Intent launchIntent = pm.getLaunchIntentForPackage(up.packageName);
                	activity.startActivity(launchIntent);
                	break;
                case 3:
                	setContent(item, up);
                    dialog.dismiss();
                    break;
                }
            }
        });
        AlertDialog dialog = builder.create();
        dialog.show();
    }
    
    private void showErrorDialog(UidPolicy policy, int resource){
    	AlertDialog.Builder builder = new AlertDialog.Builder(getActivity())
    			.setTitle(policy.name)
        		.setIcon(Helper.loadPackageIcon(getActivity(), policy.packageName))
        		.setMessage(getResources().getText(resource))
        		.setCancelable(true)
        		.setNeutralButton(android.R.string.ok, new DialogInterface.OnClickListener() {
    	           public void onClick(DialogInterface dialog, int id) {
    	        	   dialog.dismiss();
    	           }
    	       });
    	AlertDialog alert = builder.create();
    	alert.show();
    }
    
    public void onConfigurationChanged(Configuration newConfig) {
    };

    protected LogNativeFragment createLogNativeFragment() {
        return new LogNativeFragment();
    }
    
    protected SettingsNativeFragment createSettingsNativeFragment() {
        return new SettingsNativeFragment();
    }

    FragmentInterfaceWrapper setContentNative(final UidPolicy up) {
        LogNativeFragment l = createLogNativeFragment();
        l.getInternal().setUidPolicy(up);
        if (up != null) {
            Bundle args = new Bundle();
            args.putString("command", up.command);
            args.putInt("uid", up.uid);
            args.putInt("desiredUid", up.desiredUid);
            l.setArguments(args);
        }
        l.getInternal().setListContentId(getFragment().getId());
        return l;
    }
    
    void setContent(final ListItem li, final UidPolicy up) {
        if (getActivity() instanceof FragmentActivity) {
            LogFragment l = new LogFragment();
            l.getInternal().setUidPolicy(up);
            l.getInternal().setListContentId(getFragment().getId());
            mContent = l;
        }
        else {
            mContent = setContentNative(up);
        }
        
        setContent(mContent, up == null, up == null ? getString(R.string.logs) : up.getName());
    }

    
    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        MenuInflater mi = new MenuInflater(getActivity());
        mi.inflate(R.menu.main, menu);
        MenuItem log = menu.findItem(R.id.logs);
        log.setOnMenuItemClickListener(new OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
            	showAllLogs();
                return true;
            }
        });
        
        MenuItem settings = menu.findItem(R.id.settings);
        settings.setOnMenuItemClickListener(new OnMenuItemClickListener() {
            void openSettingsNative(final MenuItem item) {
                setContent(createSettingsNativeFragment(), true, getString(R.string.settings));
            }
            
            @Override
            public boolean onMenuItemClick(final MenuItem item) {
                if (getActivity() instanceof FragmentActivity) {
                    setContent(new SettingsFragment(), true, getString(R.string.settings));
                }
                else {
                    openSettingsNative(item);
                }
                return true;
            }
        });
        /* When you are in LogFragmentInternal screen and you rotate the device 
         * you are directed to the first screen (PolicyFragment). 
         * Then you will notice that the trash menu icon still exists.
         * This is a bug and this must not should happen and below is a way 
         * to extinct this bug.
         */
        menu.removeItem(R.id.delete);
    }

}
