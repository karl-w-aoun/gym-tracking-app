package com.gymtrack.app;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;

import androidx.activity.result.ActivityResult;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.ActivityCallback;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/** Native file handling so exports work in Android's Gallery and Files apps. */
@CapacitorPlugin(name = "GymFiles")
public class GymFilesPlugin extends Plugin {
    @PluginMethod
    public void savePng(PluginCall call) {
        String dataUrl = call.getString("dataUrl");
        String filename = safeFilename(call.getString("filename", "gym-progress.png"), ".png");
        if (dataUrl == null || !dataUrl.startsWith("data:image/png;base64,")) {
            call.reject("A PNG image is required.");
            return;
        }
        try {
            byte[] bytes = Base64.decode(dataUrl.substring(dataUrl.indexOf(',') + 1), Base64.DEFAULT);
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, filename);
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
            values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/GymTrack");
            values.put(MediaStore.Images.Media.IS_PENDING, 1);
            ContentResolver resolver = getContext().getContentResolver();
            Uri uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (uri == null) throw new IllegalStateException("Could not create the Gallery image.");
            try (OutputStream output = resolver.openOutputStream(uri)) {
                if (output == null) throw new IllegalStateException("Could not write the Gallery image.");
                output.write(bytes);
            }
            values.clear();
            values.put(MediaStore.Images.Media.IS_PENDING, 0);
            resolver.update(uri, values, null, null);
            JSObject result = new JSObject();
            result.put("uri", uri.toString());
            call.resolve(result);
        } catch (Exception exception) {
            call.reject("Could not save PNG to Gallery.", exception);
        }
    }

    @PluginMethod
    public void saveBackup(PluginCall call) {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/x-gymtrack-backup");
        intent.putExtra(Intent.EXTRA_TITLE, safeFilename(call.getString("filename", "gymtrack-backup.smt"), ".smt"));
        startActivityForResult(call, intent, "saveBackupResult");
    }

    @ActivityCallback
    private void saveBackupResult(PluginCall call, ActivityResult result) {
        if (call == null || result.getResultCode() != Activity.RESULT_OK || result.getData() == null) {
            if (call != null) call.reject("Backup save was cancelled.");
            return;
        }
        try {
            Uri uri = result.getData().getData();
            String contents = call.getString("contents", "");
            try (OutputStream output = getContext().getContentResolver().openOutputStream(uri)) {
                if (output == null) throw new IllegalStateException("Could not open selected file.");
                output.write(contents.getBytes(StandardCharsets.UTF_8));
            }
            call.resolve();
        } catch (Exception exception) {
            call.reject("Could not save backup.", exception);
        }
    }

    @PluginMethod
    public void pickBackup(PluginCall call) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        // Wildcard keeps custom .smt files visible in every Android file provider.
        intent.setType("*/*");
        startActivityForResult(call, intent, "pickBackupResult");
    }

    @ActivityCallback
    private void pickBackupResult(PluginCall call, ActivityResult result) {
        if (call == null || result.getResultCode() != Activity.RESULT_OK || result.getData() == null) {
            if (call != null) call.reject("Backup import was cancelled.");
            return;
        }
        try {
            Uri uri = result.getData().getData();
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (InputStream input = getContext().getContentResolver().openInputStream(uri)) {
                if (input == null) throw new IllegalStateException("Could not read selected file.");
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) bytes.write(buffer, 0, count);
            }
            JSObject output = new JSObject();
            output.put("contents", bytes.toString(StandardCharsets.UTF_8.name()));
            call.resolve(output);
        } catch (Exception exception) {
            call.reject("Could not read backup.", exception);
        }
    }

    private String safeFilename(String name, String extension) {
        String cleaned = name.replaceAll("[^a-zA-Z0-9._-]", "-");
        return cleaned.toLowerCase().endsWith(extension) ? cleaned : cleaned + extension;
    }
}
