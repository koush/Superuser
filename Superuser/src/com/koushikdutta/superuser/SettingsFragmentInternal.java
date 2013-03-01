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

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Toast;

import com.koushikdutta.superuser.util.Settings;
import com.koushikdutta.widgets.BetterListFragmentInternal;
import com.koushikdutta.widgets.FragmentInterfaceWrapper;
import com.koushikdutta.widgets.ListItem;

@SuppressLint("ValidFragment")
public class SettingsFragmentInternal extends BetterListFragmentInternal {
    public SettingsFragmentInternal(FragmentInterfaceWrapper fragment) {
        super(fragment);
    }

    @Override
    protected int getListFragmentResource() {
        return R.layout.settings;
    }
    
//    static final int containerId = 100001;
//    public static class MyPinFragment extends DialogFragment {
//        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
//            FrameLayout ret =  new FrameLayout(getActivity());
//            ret.setId(containerId);
//            return ret;
//        };
//        
//        private int title;
//        public void setTitle(int title) {
//            this.title = title;
//        }
//
//        Dialog d;
//        @Override
//        public Dialog onCreateDialog(Bundle savedInstanceState) {
//            d = super.onCreateDialog(savedInstanceState);
//            d.setTitle(title);
//            PinView pf = new PinView() {
//                @Override
//                public void onCancel() {
//                    super.onCancel();
//                    d.dismiss();
//                }
//                
//                @Override
//                public void onEnter(String password) {
//                    super.onEnter(password);
//                    MyPinFragment.this.onEnter(password);
//                }
//            };
//            getChildFragmentManager().beginTransaction().add(containerId, pf).commit();
//
//            return d;
//        }
//        
//        public void onEnter(String password) {
//            d.dismiss();
//        }
//    };

    ListItem pinItem;
    void confirmPin(final String pin) {
        final Dialog d = new Dialog(getContext());
        d.setTitle(R.string.confirm_pin);
        d.setContentView(new PinViewHelper((LayoutInflater)getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE), null, null) {
            public void onEnter(String password) {
                super.onEnter(password);
                if (pin.equals(password)) {
                    Settings.setPin(getActivity(), password);
                    pinItem.setSummary(Settings.isPinProtected(getActivity()) ? R.string.pin_set : R.string.pin_protection_summary);
                    if (password != null && password.length() > 0)
                        Toast.makeText(getActivity(), getString(R.string.pin_set), Toast.LENGTH_SHORT).show();
                    d.dismiss();
                    return;
                }
                Toast.makeText(getActivity(), getString(R.string.pin_mismatch), Toast.LENGTH_SHORT).show();
            };
            
            public void onCancel() {
                super.onCancel();
                d.dismiss();
            };
        }.getView());
        d.show();
    }
    
    void setPin() {
        final Dialog d = new Dialog(getContext());
        d.setTitle(R.string.enter_new_pin);
        d.setContentView(new PinViewHelper((LayoutInflater)getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE), null, null) {
            public void onEnter(String password) {
                super.onEnter(password);
                confirmPin(password);
                d.dismiss();
            };
            
            public void onCancel() {
                super.onCancel();
                d.dismiss();
            };
        }.getView());
        d.show();
    }

    void checkPin() {
        if (Settings.isPinProtected(getActivity())) {
            final Dialog d = new Dialog(getContext());
            d.setTitle(R.string.enter_pin);
            d.setContentView(new PinViewHelper((LayoutInflater)getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE), null, null) {
                public void onEnter(String password) {
                    super.onEnter(password);
                    if (Settings.checkPin(getActivity(), password)) {
                        super.onEnter(password);
                        setPin();
                        d.dismiss();
                        return;
                    }
                    Toast.makeText(getActivity(), getString(R.string.incorrect_pin), Toast.LENGTH_SHORT).show();
                };
                
                public void onCancel() {
                    super.onCancel();
                    d.dismiss();
                };
            }.getView());
            d.show();
        }
        else {
            setPin();
        }
    }
    
    @Override
    protected void onCreate(Bundle savedInstanceState, View view) {
        super.onCreate(savedInstanceState, view);
        
        if (Settings.getMultiuserMode(getActivity()) != Settings.MULTIUSER_MODE_NONE) {
            addItem(R.string.security, new ListItem(this, R.string.multiuser_policy, 0) {
                void update() {
                    int res = -1;
                    switch (Settings.getMultiuserMode(getActivity())) {
                    case Settings.MULTIUSER_MODE_OWNER_MANAGED:
                        res = R.string.multiuser_owner_managed_summary;
                        break;
                    case Settings.MULTIUSER_MODE_OWNER_ONLY:
                        res = R.string.multiuser_owner_only_summary;
                        break;
                    case Settings.MULTIUSER_MODE_USER:
                        res = R.string.multiuser_user_summary;
                        break;
                    }
                    
                    if (!Helper.isAdminUser(getActivity())) {
                        setEnabled(false);
                        String s = "";
                        if (res != -1)
                            s = getString(res) + "\n";
                        setSummary(s + getString(R.string.multiuser_require_owner));
                    }
                    else {
                        if (res != -1)
                            setSummary(res);
                    }
                }
                
                {
                    update();
                }
                
                @Override
                public void onClick(View view) {
                    super.onClick(view);
                    
                    AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                    builder.setTitle(R.string.multiuser_policy);
                    String[] items = new String[] { getString(R.string.multiuser_owner_only), getString(R.string.multiuser_owner_managed), getString(R.string.multiuser_user) };
                    builder.setItems(items, new OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            switch (which) {
                            case 0:
                                Settings.setMultiuserMode(getActivity(), Settings.MULTIUSER_MODE_OWNER_ONLY);
                                break;
                            case 1:
                                Settings.setMultiuserMode(getActivity(), Settings.MULTIUSER_MODE_OWNER_MANAGED);
                                break;
                            case 2:
                                Settings.setMultiuserMode(getActivity(), Settings.MULTIUSER_MODE_USER);
                                break;
                            }
                            update();
                        }
                    });
                    builder.create().show();
                }
            }).setAttrDrawable(R.attr.multiuserIcon);
        }
        
        addItem(R.string.security, new ListItem(this, R.string.declared_permission, R.string.declared_permission_summary) {
            @Override
            public void onClick(View view) {
                super.onClick(view);
                Settings.setRequirePermission(getActivity(), getChecked());
            }
        })
        .setAttrDrawable(R.attr.declaredPermissionsIcon)
        .setCheckboxVisible(true)
        .setChecked(Settings.getRequirePermission(getActivity()));

        addItem(R.string.security, new ListItem(this, R.string.automatic_response, 0) {
            void update() {
                switch (Settings.getAutomaticResponse(getActivity())) {
                case Settings.AUTOMATIC_RESPONSE_ALLOW:
                    setSummary(R.string.automatic_response_allow_summary);
                    break;
                case Settings.AUTOMATIC_RESPONSE_DENY:
                    setSummary(R.string.automatic_response_deny_summary);
                    break;
                case Settings.AUTOMATIC_RESPONSE_PROMPT:
                    setSummary(R.string.automatic_response_prompt_summary);
                    break;
                }
            }
            
            {
                update();
            }
            @Override
            public void onClick(View view) {
                super.onClick(view);
                
                AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                builder.setTitle(R.string.automatic_response);
                String[] items = new String[] { getString(R.string.prompt), getString(R.string.deny), getString(R.string.allow) };
                builder.setItems(items, new OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        switch (which) {
                        case 0:
                            Settings.setAutomaticResponse(getActivity(), Settings.AUTOMATIC_RESPONSE_PROMPT);
                            break;
                        case 1:
                            Settings.setAutomaticResponse(getActivity(), Settings.AUTOMATIC_RESPONSE_DENY);
                            break;
                        case 2:
                            Settings.setAutomaticResponse(getActivity(), Settings.AUTOMATIC_RESPONSE_ALLOW);
                            break;
                        }
                        update();
                    }
                });
                builder.create().show();
            }
        })
        .setAttrDrawable(R.attr.automaticResponseIcon);

        pinItem = addItem(R.string.security, new ListItem(this, R.string.pin_protection, Settings.isPinProtected(getActivity()) ? R.string.pin_set : R.string.pin_protection_summary) {
            @Override
            public void onClick(View view) {
                super.onClick(view);
                checkPin();
            }            
        })
        .setAttrDrawable(R.attr.pinProtectionIcon);

        addItem(R.string.security, new ListItem(this, getString(R.string.request_timeout), getString(R.string.request_timeout_summary, Settings.getRequestTimeout(getActivity()))) {
            @Override
            public void onClick(View view) {
                super.onClick(view);
                AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                builder.setTitle(R.string.request_timeout);
                String[] seconds = new String[3];
                for (int i = 0; i < seconds.length; i++) {
                    seconds[i] = getString(R.string.number_seconds, (i + 1) * 10);
                }
                builder.setItems(seconds, new OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Settings.setTimeout(getActivity(), (which + 1) * 10);
                        setSummary(getString(R.string.request_timeout_summary, Settings.getRequestTimeout(getActivity())));
                    }
                });
                builder.create().show();
            }
        })
        .setAttrDrawable(R.attr.requestTimeoutIcon);

        addItem(R.string.settings, new ListItem(this, R.string.logging, R.string.logging_summary) {
            @Override
            public void onClick(View view) {
                super.onClick(view);
                Settings.setLogging(getActivity(), getChecked());
            }
        })
        .setAttrDrawable(R.attr.loggingIcon)
        .setCheckboxVisible(true)
        .setChecked(Settings.getLogging(getActivity()));

        addItem(R.string.settings, new ListItem(this, R.string.notifications, 0) {
            void update() {
                switch (Settings.getNotificationType(getActivity())) {
                case Settings.NOTIFICATION_TYPE_NONE:
                    setSummary(getString(R.string.no_notification));
                    break;
                case Settings.NOTIFICATION_TYPE_NOTIFICATION:
                    setSummary(getString(R.string.notifications_summary, getString(R.string.notification)));
                    break;
                case Settings.NOTIFICATION_TYPE_TOAST:
                    setSummary(getString(R.string.notifications_summary, getString(R.string.toast)));
                    break;
                }
            }
            
            {
                update();
            }
            @Override
            public void onClick(View view) {
                super.onClick(view);
                
                AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                builder.setTitle(R.string.notifications);
                String[] items = new String[] { getString(R.string.none), getString(R.string.toast), getString(R.string.notification) };
                builder.setItems(items, new OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        switch (which) {
                        case 0:
                            Settings.setNotificationType(getActivity(), Settings.NOTIFICATION_TYPE_NONE);
                            break;
                        case 2:
                            Settings.setNotificationType(getActivity(), Settings.NOTIFICATION_TYPE_NOTIFICATION);
                            break;
                        case 1:
                            Settings.setNotificationType(getActivity(), Settings.NOTIFICATION_TYPE_TOAST);
                            break;
                        }
                        update();
                    }
                });
                builder.create().show();
            }
        })
        .setAttrDrawable(R.attr.notificationsIcon);
    }
}
