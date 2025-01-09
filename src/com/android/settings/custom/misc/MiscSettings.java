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
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;

import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.DialogInterface;
import android.os.Environment;

import android.content.Context;
import android.content.res.Resources;
import android.content.ContentResolver;

import androidx.preference.Preference;

public class MiscSettings extends SettingsPreferenceFragment implements Preference.OnPreferenceChangeListener {

    private static final String TAG = "Miscellaneous";

    private static final String KEY_PIF_JSON_MANAGE_PREFERENCE = "pif_json_manage_preference";
    private Preference mPifJsonManagePreference;
    private Handler mHandler;

    private Context context;
    private Resources resources;
    private ContentResolver resolver;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.misc_settings);

        context = getContext();
        resources = context.getResources();
        resolver = context.getContentResolver();

        mHandler = new Handler();

        mPifJsonManagePreference = findPreference(KEY_PIF_JSON_MANAGE_PREFERENCE);

        if (mPifJsonManagePreference != null) {
            mPifJsonManagePreference.setOnPreferenceChangeListener(this);
        }
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        if (preference == mPifJsonManagePreference) {
            onPifManageScreen(context);
            return true;
        }
        return super.onPreferenceTreeClick(preference);
    }

    private void onPifManageScreen(Context context) {
        final String[] items = {
            resources.getString(R.string.spoofing_pif_json_download_title),
            resources.getString(R.string.spoofing_pif_json_select_title),
        };
        new AlertDialog.Builder(context)
            .setTitle(resources.getString(R.string.spoofing_pif_manage_title))
            .setItems(items, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    if (which == 0) {
                        downloadPifJson(context);
                    } else if (which == 1) {
                        selectPifJson(context);
                    }
                }
            })
            .show();
    }
    private void downloadPifJson(Context context) {
        DownloadManager downloadManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        Uri uri = Uri.parse(resources.getString(R.string.spoofing_pif_json_download_url));
        String mes = resources.getString(R.string.spoofing_pif_json_download_message);
        DownloadManager.Request request = new DownloadManager.Request(uri)
            .setTitle("pif.json")
            .setDescription(mes)
            .setMimeType("application/json")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "pif.json");
        downloadManager.enqueue(request);
        Toast.makeText(context, resources.getString(R.string.spoofing_pif_json_download_message), Toast.LENGTH_LONG).show();
    }
    private void selectPifJson(Context context) {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("application/json");
        startActivityForResult(intent, 10001);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 10001 && resultCode == Activity.RESULT_OK) {
            Uri uri = data.getData();
            Log.d(TAG, "URI received: " + uri.toString());
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
                    Toast.makeText(
                        getContext(),
                        getContext().getResources().getString(R.string.spoofing_pif_json_select_success),
                        Toast.LENGTH_LONG
                    ).show();
                }
            } catch (Exception e) {
                Log.e(TAG, "Error reading JSON or setting properties", e);
                Toast.makeText(
                        getContext(),
                        getContext().getResources().getString(R.string.spoofing_pif_json_select_failure),
                        Toast.LENGTH_LONG
                    ).show();
            }
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        // final Context context = getContext();
        context = getContext();
        final ContentResolver resolver = context.getContentResolver();
        return false;
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.CUSTOM;
    }
}
