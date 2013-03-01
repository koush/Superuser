package com.koushikdutta.superuser.util;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;
import android.text.TextUtils;
import android.util.Base64;

import com.koushikdutta.superuser.Helper;
import com.koushikdutta.superuser.db.SuDatabaseHelper;

public class Settings {
    SQLiteDatabase mDatabase;
    Context mContext;

    public static void setString(Context context, String name, String value) {
        ContentValues cv = new ContentValues();
        cv.put("key", name);
        cv.put("value", value);
        SQLiteDatabase db = new SuDatabaseHelper(context).getWritableDatabase();
        try {
            db.replace("settings", null, cv);
        }
        finally {
            db.close();
        }
    }

    public static String getString(Context context, String name) {
        return getString(context, name, null);
    }

    public static String getString(Context context, String name, String defaultValue) {
        SQLiteDatabase db = new SuDatabaseHelper(context).getReadableDatabase();
        Cursor cursor = db.query("settings", new String[] { "value" }, "key='" + name + "'", null, null, null, null);
        try {
            if (cursor.moveToNext())
                return cursor.getString(0);
        }
        finally {
            cursor.close();
            db.close();
        }
        return defaultValue;
    }

    public static void setInt(Context context, String name, int value) {
        setString(context, name, ((Integer) value).toString());
    }

    public static int getInt(Context context, String name, int defaultValue) {
        try {
            return Integer.parseInt(getString(context, name, null));
        }
        catch (Exception ex) {
            return defaultValue;
        }
    }

    public static void setLong(Context context, String name, long value) {
        setString(context, name, ((Long) value).toString());
    }

    public static long getLong(Context context, String name, long defaultValue) {
        try {
            return Long.parseLong(getString(context, name, null));
        }
        catch (Exception ex) {
            return defaultValue;
        }
    }

    public static void setBoolean(Context context, String name, boolean value) {
        setString(context, name, ((Boolean) value).toString());
    }

    public static boolean getBoolean(Context context, String name, boolean defaultValue) {
        try {
            return Boolean.parseBoolean(getString(context, name, ((Boolean) defaultValue).toString()));
        }
        catch (Exception ex) {
            ex.printStackTrace();
            return defaultValue;
        }
    }
    
    private static final String KEY_LOGGING = "logging";
    public static boolean getLogging(Context context) {
        return getBoolean(context, KEY_LOGGING, true);
    }
    
    public static void setLogging(Context context, boolean logging) {
        setBoolean(context, KEY_LOGGING, logging);
    }
    
    private static final String KEY_TIMEOUT = "timeout";
    public static final int REQUEST_TIMEOUT_DEFAULT = 30;
    public static int getRequestTimeout(Context context) {
        return getInt(context, KEY_TIMEOUT, REQUEST_TIMEOUT_DEFAULT);
    }
    
    public static void setTimeout(Context context, int timeout) {
        setInt(context, KEY_TIMEOUT, timeout);
    }

    private static final String KEY_NOTIFICATION = "notification";
    public static final int NOTIFICATION_TYPE_NONE = 0;
    public static final int NOTIFICATION_TYPE_TOAST = 1;
    public static final int NOTIFICATION_TYPE_NOTIFICATION = 2;
    public static final int NOTIFICATION_TYPE_DEFAULT = NOTIFICATION_TYPE_TOAST;
    public static int getNotificationType(Context context) {
        switch (getInt(context, KEY_NOTIFICATION, NOTIFICATION_TYPE_DEFAULT)) {
        case NOTIFICATION_TYPE_NONE:
            return NOTIFICATION_TYPE_NONE;
        case NOTIFICATION_TYPE_NOTIFICATION:
            return NOTIFICATION_TYPE_NOTIFICATION;
        case NOTIFICATION_TYPE_TOAST:
            return NOTIFICATION_TYPE_TOAST;
        default:
            return NOTIFICATION_TYPE_DEFAULT;
        }
    }
    
    public static void setNotificationType(Context context, int notification) {
        setInt(context, KEY_NOTIFICATION, notification);
    }
    
    public static final String KEY_PIN = "pin";
    public static final boolean isPinProtected(Context context) {
        return Settings.getString(context, KEY_PIN) != null;
    }
    
    private static String digest(String value) {
        // ok, there's honestly no point in digesting the pin.
        // if someone gets a hold of the hash, there's really only like
        // 10^n possible values to brute force, where N is generally
        // 4. Ie, 10000. Yay, security theater. This really ought
        // to be a password.
        if (TextUtils.isEmpty(value))
            return null;
        try {
            MessageDigest digester = MessageDigest.getInstance("MD5");
            return Base64.encodeToString(digester.digest(value.getBytes()), Base64.DEFAULT);
        }
        catch (Exception e) {
            return value;
        }
    }
    
    public static void setPin(Context context, String pin) {
        Settings.setString(context, KEY_PIN, digest(pin));
    }
    
    public static boolean checkPin(Context context, String pin) {
        pin = digest(pin);
        String hashed = Settings.getString(context, KEY_PIN);
        if (TextUtils.isEmpty(pin))
            return TextUtils.isEmpty(hashed);
        return pin.equals(hashed);
    }

    private static final String KEY_REQUIRE_PREMISSION = "require_permission";
    public static boolean getRequirePermission(Context context) {
        return getBoolean(context, KEY_REQUIRE_PREMISSION, false);
    }
    
    public static void setRequirePermission(Context context, boolean require) {
        setBoolean(context, KEY_REQUIRE_PREMISSION, require);
    }
    
    private static final String KEY_AUTOMATIC_RESPONSE = "automatic_response";
    public static final int AUTOMATIC_RESPONSE_PROMPT = 0;
    public static final int AUTOMATIC_RESPONSE_ALLOW = 1;
    public static final int AUTOMATIC_RESPONSE_DENY = 2;
    public static final int AUTOMATIC_RESPONSE_DEFAULT = AUTOMATIC_RESPONSE_PROMPT;
    public static int getAutomaticResponse(Context context) {
        switch (getInt(context, KEY_AUTOMATIC_RESPONSE, AUTOMATIC_RESPONSE_DEFAULT)) {
        case AUTOMATIC_RESPONSE_ALLOW:
            return AUTOMATIC_RESPONSE_ALLOW;
        case AUTOMATIC_RESPONSE_PROMPT:
            return AUTOMATIC_RESPONSE_PROMPT;
        case AUTOMATIC_RESPONSE_DENY:
            return AUTOMATIC_RESPONSE_DENY;
        default:
            return AUTOMATIC_RESPONSE_DEFAULT;
        }
    }

    public static void setAutomaticResponse(Context context, int response) {
        setInt(context, KEY_AUTOMATIC_RESPONSE, response);
    }
    
    
    static public String readFile(String filename) throws IOException {
        return readFile(new File(filename));
    }
    
    static public String readFile(File file) throws IOException {
        byte[] buffer = new byte[(int) file.length()];
        DataInputStream input = new DataInputStream(new FileInputStream(file));
        input.readFully(buffer);
        input.close();
        return new String(buffer);
    }
    
    public static void writeFile(File file, String string) throws IOException {
        writeFile(file.getAbsolutePath(), string);
    }
    
    public static void writeFile(String file, String string) throws IOException {
        File f = new File(file);
        f.getParentFile().mkdirs();
        DataOutputStream dout = new DataOutputStream(new FileOutputStream(f));
        dout.write(string.getBytes());
        dout.close();
    }
    
    public static byte[] readToEndAsArray(InputStream input) throws IOException {
        DataInputStream dis = new DataInputStream(input);
        byte[] stuff = new byte[1024];
        ByteArrayOutputStream buff = new ByteArrayOutputStream();
        int read = 0;
        while ((read = dis.read(stuff)) != -1)
        {
            buff.write(stuff, 0, read);
        }
        input.close();
        return buff.toByteArray();
    }

    public static String readToEnd(InputStream input) throws IOException {
        return new String(readToEndAsArray(input));
    }

    public static final int MULTIUSER_MODE_OWNER_ONLY = 0;
    public static final int MULTIUSER_MODE_OWNER_MANAGED = 1;
    public static final int MULTIUSER_MODE_USER = 2;
    public static final int MULTIUSER_MODE_NONE = 3;
    
    private static final String MULTIUSER_VALUE_OWNER_ONLY  = "owner";
    private static final String MULTIUSER_VALUE_OWNER_MANAGED = "managed";
    private static final String MULTIUSER_VALUE_USER = "user";

    public static final int getMultiuserMode(Context context) {
        if (Build.VERSION.SDK_INT < 17)
            return MULTIUSER_MODE_NONE;

        if (!Helper.supportsMultipleUsers(context))
            return MULTIUSER_MODE_NONE;
        
        try {
            String mode;
            if (Helper.isAdminUser(context)) {
                File file = context.getFileStreamPath("multiuser_mode");
                mode = readFile(file);
            }
            else {
                Process p = Runtime.getRuntime().exec("su -u");
                mode = readToEnd(p.getInputStream()).trim();
            }
            
            if (MULTIUSER_VALUE_OWNER_MANAGED.equals(mode))
                return MULTIUSER_MODE_OWNER_MANAGED;
            if (MULTIUSER_VALUE_USER.equals(mode))
                return MULTIUSER_MODE_USER;
            if (MULTIUSER_VALUE_OWNER_ONLY.equals(mode))
                return MULTIUSER_MODE_OWNER_ONLY;
        }
        catch (Exception e) {
        }
        return MULTIUSER_MODE_OWNER_ONLY;
    }
    
    public static void setMultiuserMode(Context context, int mode) {
        if (!Helper.isAdminUser(context))
            return;
        try {
            File file = context.getFileStreamPath("multiuser_mode");
            switch (mode) {
            case MULTIUSER_MODE_OWNER_MANAGED:
                writeFile(file, MULTIUSER_VALUE_OWNER_MANAGED);
                break;
            case MULTIUSER_MODE_USER:
                writeFile(file, MULTIUSER_VALUE_USER);
                break;
            case MULTIUSER_MODE_NONE:
                file.delete();
                break;
            default:
                writeFile(file, MULTIUSER_VALUE_OWNER_ONLY);
                break;
            }
        }
        catch (Exception ex) {
        }
    }
}
