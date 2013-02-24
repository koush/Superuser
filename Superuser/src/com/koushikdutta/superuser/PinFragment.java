package com.koushikdutta.superuser;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;

public class PinFragment extends Fragment {
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        View ret = inflater.inflate(R.layout.pin, container, false);

        final EditText password = (EditText)ret.findViewById(R.id.password);
        int[] ids = new int[] { R.id.p1, R.id.p2, R.id.p3, R.id.p4, R.id.p5, R.id.p6, R.id.p7, R.id.p8, R.id.p9, R.id.p0, };
        for (int id : ids) {
            Button b = (Button)ret.findViewById(id);
            final String text = b.getText().toString();
            b.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    password.setText(password.getText().toString() + text);
                }
            });
        }

        ret.findViewById(R.id.pd).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                String curPass = password.getText().toString();
                if (curPass.length() > 0) {
                    curPass = curPass.substring(0, curPass.length() - 1);
                    password.setText(curPass);
                }
            }
        });
        
        ret.findViewById(R.id.cancel).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                onCancel();
            }
        });
        
        ret.findViewById(R.id.ok).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                onEnter(password.getText().toString());
                password.setText("");
            }
        });
        
        return ret;
    }
    
    public void onEnter(String password) {
        
    }
    
    public void onCancel() {
        
    }
}
