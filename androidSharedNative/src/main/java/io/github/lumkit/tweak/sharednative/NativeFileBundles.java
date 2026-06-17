package io.github.lumkit.tweak.sharednative;

import android.os.Bundle;

import java.util.ArrayList;
import java.util.List;

public final class NativeFileBundles {
    public static final String KEY_SUCCESS = "success";
    public static final String KEY_MESSAGE = "message";
    public static final String KEY_BOOLEAN = "boolean";
    public static final String KEY_BYTES = "bytes";
    public static final String KEY_STRING = "string";
    public static final String KEY_STRING_LIST = "string_list";

    private NativeFileBundles() {
    }

    public static Bundle successUnit() {
        Bundle bundle = new Bundle();
        bundle.putBoolean(KEY_SUCCESS, true);
        return bundle;
    }

    public static Bundle successBoolean(boolean value) {
        Bundle bundle = successUnit();
        bundle.putBoolean(KEY_BOOLEAN, value);
        return bundle;
    }

    public static Bundle successBytes(byte[] value) {
        Bundle bundle = successUnit();
        bundle.putByteArray(KEY_BYTES, value);
        return bundle;
    }

    public static Bundle successString(String value) {
        Bundle bundle = successUnit();
        bundle.putString(KEY_STRING, value);
        return bundle;
    }

    public static Bundle successStringList(List<String> value) {
        Bundle bundle = successUnit();
        bundle.putStringArrayList(KEY_STRING_LIST, new ArrayList<>(value));
        return bundle;
    }

    public static Bundle failure(Throwable throwable) {
        Bundle bundle = new Bundle();
        bundle.putBoolean(KEY_SUCCESS, false);
        bundle.putString(KEY_MESSAGE, throwable.getMessage() != null ? throwable.getMessage() : throwable.toString());
        return bundle;
    }
}
