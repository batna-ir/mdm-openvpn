/*
 * Copyright (c) 2012-2020 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package ir.batna.openvpn;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;

import java.io.File;

public class FileAccess {

    private Context context;
    private ContentResolver contentResolver;
    private OnProfileImportListener onProfileImportListener;

    public FileAccess(Context context, ContentResolver contentResolver, OnProfileImportListener onProfileImportListener) {
        this.context = context;
        this.contentResolver = contentResolver;
        this.onProfileImportListener = onProfileImportListener;
        File vpnFile = null;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            String path = context.getDataDir().getAbsolutePath() + "/files/openvpn.ovpn";
            vpnFile = new File(path);
        } else {

        }
        Uri uri = new Uri.Builder().path(vpnFile.getAbsolutePath()).scheme("file").build();
        OpenVPNConfig openVPNConfig = new OpenVPNConfig();
        openVPNConfig.doImportUri(uri, contentResolver, context, onProfileImportListener);
    }
}
