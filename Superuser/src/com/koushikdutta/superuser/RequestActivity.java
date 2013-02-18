package com.koushikdutta.superuser;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

public class RequestActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        if (intent == null) {
            finish();
            return;
        }
        
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setClass(this, MultitaskSuRequestActivity.class);
        startActivity(intent);
        finish();
        return;
    }
}
