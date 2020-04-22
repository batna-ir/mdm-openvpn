/*
 * Copyright (c) 2012-2020 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package ir.batna.openvpn;

import android.content.Context;
import android.content.SharedPreferences;

public class BatnaSharedPreferences {

    private SharedPreferences sharedPreferences;
    private SharedPreferences.Editor editor;

    public BatnaSharedPreferences(Context context) {
        sharedPreferences = context.getSharedPreferences("BatnaSharedPreferences", Context.MODE_PRIVATE);
        editor = sharedPreferences.edit();
    }

    public void saveBoolean(String key, boolean value) {
        editor.putBoolean(key, value);
        editor.apply();
    }

    public boolean getBooleanValue(String key) {
        return sharedPreferences.getBoolean(key, false);
    }
}
