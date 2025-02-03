package com.android.settings.custom.misc;

import android.os.Bundle;

import com.android.internal.logging.nano.MetricsProto;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.SystemProperties;
import android.util.Log;
import android.widget.Toast;

import org.json.JSONObject;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;

import android.app.DownloadManager;
import android.content.Context;
import android.os.Environment;
import androidx.preference.Preference;

public class MiscSettings extends SettingsPreferenceFragment implements Preference.OnPreferenceChangeListener {

    private static final String TAG = "Miscellaneous";
    private static final String KEY_PIF_JSON_MANAGE_PREFERENCE = "pif_json_manage_preference";
    private Preference mPifJsonManagePreference;
    private Handler mHandler;
    private Context context;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.misc_settings);

        context = getContext();
        mHandler = new Handler();
        mPifJsonManagePreference = findPreference(KEY_PIF_JSON_MANAGE_PREFERENCE);

        if (mPifJsonManagePreference != null) {
            mPifJsonManagePreference.setOnPreferenceClickListener(preference -> {
                downloadAndLoadPifJson(context);
                return true;
            });
        }
    }

    private void downloadAndLoadPifJson(Context context) {
        DownloadManager downloadManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        Uri uri = Uri.parse(context.getResources().getString(R.string.spoofing_pif_json_download_url));
        String filePath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + "/pif.json";
        
        DownloadManager.Request request = new DownloadManager.Request(uri)
            .setTitle("pif.json")
            .setDescription(context.getResources().getString(R.string.spoofing_pif_json_download_message))
            .setMimeType("application/json")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationUri(Uri.parse("file://" + filePath));
        
        long downloadId = downloadManager.enqueue(request);
        
        new Thread(() -> {
            boolean downloading = true;
            while (downloading) {
                DownloadManager.Query query = new DownloadManager.Query();
                query.setFilterById(downloadId);
                try (android.database.Cursor cursor = downloadManager.query(query)) {
                    if (cursor != null && cursor.moveToFirst()) {
                        int columnIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
                        if (cursor.getInt(columnIndex) == DownloadManager.STATUS_SUCCESSFUL) {
                            downloading = false;
                            mHandler.post(() -> {
                                loadPifJson(Uri.parse("file://" + filePath));
                                deleteFileAfterImport(filePath);
                            });
                        }
                    }
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private void loadPifJson(Uri uri) {
        try (InputStream inputStream = getActivity().getContentResolver().openInputStream(uri)) {
            if (inputStream != null) {
                String json = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                Log.d(TAG, "JSON data: " + json);
                JSONObject jsonObject = new JSONObject(json);
                for (Iterator<String> it = jsonObject.keys(); it.hasNext(); ) {
                    String key = it.next();
                    String value = jsonObject.getString(key);
                    Log.d(TAG, "Setting property: persist.sys.pihooks_" + key + " = " + value);
                    SystemProperties.set("persist.sys.pihooks_" + key, value);
                }
                Toast.makeText(getContext(), R.string.spoofing_pif_json_select_success, Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error reading JSON or setting properties", e);
            Toast.makeText(getContext(), R.string.spoofing_pif_json_select_failure, Toast.LENGTH_LONG).show();
        }
    }

    private void deleteFileAfterImport(String filePath) {
        File file = new File(filePath);
        if (file.exists()) {
            boolean deleted = file.delete();
            Log.d(TAG, "Deleted pif.json: " + deleted);
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        return false;
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.CUSTOM;
    }
}