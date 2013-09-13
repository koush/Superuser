package com.koushikdutta.superuser.util;

import com.koushikdutta.superuser.util.exceptions.*;

import android.content.Context;
import android.util.Log;

public final class SuHelper {
    public static String CURRENT_VERSION = "13";
    public static void checkSu(Context context) throws IllegalResultException, 
	    IllegalBinaryException, Exception {
		Process p = Runtime.getRuntime().exec("su -v");
	        String result = Settings.readToEnd(p.getInputStream());
	        Log.i("Superuser", "Result: " + result);
	        final int exitValue = p.waitFor();
	        if (exitValue != 0)
	            throw new IllegalResultException("Expected zero (0) but returned "+exitValue+".");
	        if (result == null)
	            throw new IllegalResultException("Empty result returned.");
	        if (!result.contains(context.getPackageName()))
	            throw new IllegalBinaryException("The su binary is not the appropriate for this app.");
	        
	        String[] parts = result.split(" ");
	        if (!CURRENT_VERSION.equals(parts[0]))
	            throw new IllegalBinaryException("Su binary is out of date.");
	    }
}
