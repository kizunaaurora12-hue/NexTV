package com.nextv.app.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class ChannelRepository {

    private static final String TAG = "ChannelRepository";

    public static final String DEFAULT_CHANNELS_URL =
        "https://raw.githubusercontent.com/aurorasekai15-hub/SymphogearTV/main/channels.json";

    private static final String PREFS_CACHE    = "nextv_cache";
    private static final String PREFS_SETTINGS = "nextv_settings";
    private static final String KEY_CACHE_JSON = "channels_json";
    private static final String KEY_CACHE_TIME = "channels_cache_time";
    private static final String KEY_CUSTOM_URL = "channels_url";
    private static final long   CACHE_TTL_MS   = 30 * 60 * 1000;

    private static ChannelRepository instance;
    private final Context context;
    private final OkHttpClient http;
    private final ExecutorService executor;
    private final Handler mainThread;

    public interface Callback {
        void onSuccess(List<Channel> channels);
        void onError(String message);
    }

    private ChannelRepository(Context ctx) {
        context    = ctx.getApplicationContext();
        http       = new OkHttpClient.Builder()
                        .connectTimeout(15, TimeUnit.SECONDS)
                        .readTimeout(20, TimeUnit.SECONDS)
                        .build();
        executor   = Executors.newCachedThreadPool();
        mainThread = new Handler(Looper.getMainLooper());
    }

    public static synchronized ChannelRepository getInstance(Context ctx) {
        if (instance == null) instance = new ChannelRepository(ctx);
        return instance;
    }

    private String getActiveUrl() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE);
        return prefs.getString(KEY_CUSTOM_URL, DEFAULT_CHANNELS_URL);
    }

    public void loadChannels(Callback cb) {
        executor.execute(() -> {
            SharedPreferences cache = context.getSharedPreferences(PREFS_CACHE, Context.MODE_PRIVATE);
            String cachedJson = cache.getString(KEY_CACHE_JSON, null);
            long   cacheTime  = cache.getLong(KEY_CACHE_TIME, 0);
            boolean valid     = cachedJson != null &&
                                (System.currentTimeMillis() - cacheTime) < CACHE_TTL_MS;

            if (valid) {
                Log.d(TAG, "Pakai cache channels");
                deliver(cb, parseChannels(cachedJson));
                return;
            }

            String url = getActiveUrl();
            Log.d(TAG, "Fetch dari: " + url);
            try {
                Request req = new Request.Builder().url(url).build();
                try (Response res = http.newCall(req).execute()) {
                    if (!res.isSuccessful()) throw new IOException("HTTP " + res.code());
                    String json = res.body().string();
                    cache.edit()
                         .putString(KEY_CACHE_JSON, json)
                         .putLong(KEY_CACHE_TIME, System.currentTimeMillis())
                         .apply();
                    deliver(cb, parseChannels(json));
                }
            } catch (Exception e) {
                Log.e(TAG, "Error fetch", e);
                if (cachedJson != null) {
                    deliver(cb, parseChannels(cachedJson));
                } else {
                    mainThread.post(() -> cb.onError("Gagal memuat channel: " + e.getMessage()));
                }
            }
        });
    }

    private void deliver(Callback cb, List<Channel> list) {
        mainThread.post(() -> cb.onSuccess(list));
    }

    private List<Channel> parseChannels(String json) {
        List<Channel> list = new ArrayList<>();
        Gson gson = new Gson();
        try {
            String trimmed = json.trim();
            JsonArray arr;
            if (trimmed.startsWith("[")) {
                arr = JsonParser.parseString(trimmed).getAsJsonArray();
            } else {
                JsonObject obj = JsonParser.parseString(trimmed).getAsJsonObject();
                if      (obj.has("channels")) arr = obj.getAsJsonArray("channels");
                else if (obj.has("data"))     arr = obj.getAsJsonArray("data");
                else                          arr = new JsonArray();
            }
            for (int i = 0; i < arr.size(); i++) {
                Channel ch = gson.fromJson(arr.get(i), Channel.class);
                if (ch != null && ch.getUrl() != null && !ch.getUrl().isEmpty()) {
                    if (ch.getNumber() == 0) ch.setNumber(i + 1);
                    if (ch.getQuality() == null || ch.getQuality().isEmpty()) ch.setQuality("HD");
                    list.add(ch);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Parse error", e);
        }
        Log.d(TAG, "Parsed " + list.size() + " channels");
        return list;
    }

    public void clearCache() {
        context.getSharedPreferences(PREFS_CACHE, Context.MODE_PRIVATE).edit().clear().apply();
    }
}
