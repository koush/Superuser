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


import android.support.v4.app.Fragment;

import com.koushikdutta.widgets.SupportFragment;

public class LogFragment extends SupportFragment<LogFragmentInternal> {

    @Override
    public LogFragmentInternal createFragmentInterface() {
        return new LogFragmentInternal(this) {
            @Override
            void onDelete() {
                super.onDelete();
                LogFragment.this.onDelete(getListContentId());
            }
        };
    }

    void onDelete(int id) {
//        getFragmentManager().beginTransaction().remove(this).commit();
//        getFragmentManager().popBackStack("content", FragmentManager.POP_BACK_STACK_INCLUSIVE);
        Fragment f = getFragmentManager().findFragmentById(id);
        if (f != null && f instanceof PolicyFragment) {
            PolicyFragment p = (PolicyFragment)f;
            ((PolicyFragmentInternal)p.getInternal()).load();
            ((PolicyFragmentInternal)p.getInternal()).showAllLogs();
        }
    }
}
