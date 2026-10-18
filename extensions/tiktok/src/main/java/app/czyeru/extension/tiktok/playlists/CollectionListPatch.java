package app.czyeru.extension.tiktok.playlists;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import app.czyeru.extension.shared.Logger;
import app.czyeru.extension.shared.Utils;
import app.czyeru.extension.tiktok.settings.Settings;
import app.czyeru.extension.tiktok.settings.SettingsStatus;

/**
 * Collections list (Profile > Favorites > Collections, and the "save to collection" sheet):
 * remembers loaded collections and sorts them.
 * <p>
 * Both screens use VideoCollectionListViewModel. Its page loader (X.0Xh5) builds the new list as
 * previous list + new page in a fresh ArrayList and then sets it as the state. The hook runs just
 * before that, so the list can be changed in place before any adapter sees it.
 * <p>
 * Remembered collections are kept in server order (most recent activity first) per user id, in
 * memory and in shared preferences, with the paging cursor where they end. On the first page the
 * remembered collections that are not on it are appended and the cursor is moved past them, so
 * loading more continues where the user stopped last time.
 */
@SuppressWarnings({"unused", "rawtypes", "unchecked"})
public final class CollectionListPatch {
    public enum Sort {
        NORMAL("normal", "Normal", "Most recent activity first"),
        COUNT("count", "Count", "Most saved videos first"),
        REVERSE_COUNT("count_reverse", "Reverse count", "Fewest saved videos first");

        public final String key;
        public final String label;
        public final String description;

        Sort(String key, String label, String description) {
            this.key = key;
            this.label = label;
            this.description = description;
        }

        public static Sort fromSetting(String value) {
            for (Sort sort : values()) {
                if (sort.key.equals(value)) {
                    return sort;
                }
            }
            return NORMAL;
        }
    }

    /** Collection item view type in both adapters. */
    private static final int COLLECTION_VIEW_TYPE = 2;

    private static final String PREFERENCES_NAME = "czyeru_collection_cache";
    private static final String URL_MODEL_CLASS = "com.ss.android.ugc.aweme.base.model.UrlModel";

    /** Remembered collections per user id. Guarded by itself. */
    private static final Map<String, Cache> caches = new HashMap<>();

    private CollectionListPatch() {
    }

    private static final class Cache {
        final List<Object> details;
        final Long cursor;
        final Boolean hasMore;

        Cache(List<Object> details, Long cursor, Boolean hasMore) {
            this.details = details;
            this.cursor = cursor;
            this.hasMore = hasMore;
        }
    }

    // region Hook

    /**
     * Called in X.0Xh5.apply before the new list becomes the state. Runs on a background thread.
     *
     * @param items    previous list plus the new page. A new list no adapter holds yet.
     * @param response the CollectionListResponse of the page
     * @param loader   the X.0Xh5 instance. Holds the previous list.
     */
    public static void onCollectionsLoaded(List items, Object response, Object loader) {
        try {
            if (items == null) {
                return;
            }
            boolean keepLoaded = SettingsStatus.keepCollectionsLoadedEnabled
                    && Settings.KEEP_COLLECTIONS_LOADED.get();
            Sort sort = SettingsStatus.collectionSortEnabled
                    ? Sort.fromSetting(Settings.COLLECTION_SORT.get())
                    : Sort.NORMAL;

            if (keepLoaded) {
                keepLoaded(items, response, loader);
            }
            if (sort != Sort.NORMAL) {
                sort(items, sort);
            }
        } catch (Throwable throwable) {
            Logger.printException(() -> "Collections list hook failed", throwable);
        }
    }

    public static void clearCache() {
        synchronized (caches) {
            caches.clear();
        }
        try {
            SharedPreferences preferences = preferences();
            if (preferences != null) {
                preferences.edit().clear().apply();
            }
        } catch (Throwable throwable) {
            Logger.printException(() -> "Clear collections cache failed", throwable);
        }
    }

    // endregion

    // region Keep loaded

    private static void keepLoaded(List items, Object response, Object loader) {
        Object sample = null;
        for (Object item : items) {
            if (getViewType(item) == COLLECTION_VIEW_TYPE && getDetail(item) != null) {
                sample = item;
                break;
            }
        }
        if (sample == null) {
            return;
        }
        Object sampleDetail = getDetail(sample);
        Object uidValue = readField(sampleDetail, "uid");
        String uid = uidValue instanceof String ? (String) uidValue : "";

        List previous = readListField(loader);
        int previousSize = previous == null ? 0 : previous.size();
        if (previousSize > items.size()) {
            return;
        }

        Long cursor = asLong(readField(response, "cursor"));
        Boolean hasMore = asBoolean(readField(response, "hasMore"));

        synchronized (caches) {
            Cache cache = caches.get(uid);
            if (cache == null) {
                cache = loadCache(uid, sampleDetail.getClass());
            }

            Cache next;
            if (previousSize == 0) {
                List<Object> page = details(items, 0);
                next = new Cache(new ArrayList<>(page), cursor, hasMore);

                // Without more pages, the first page is already the whole list.
                if (cache != null && Boolean.TRUE.equals(hasMore)) {
                    Set<String> pageIds = ids(page);
                    List<Object> extras = new ArrayList<>();
                    for (Object detail : cache.details) {
                        String id = getId(detail);
                        if (id != null && pageIds.add(id)) {
                            extras.add(detail);
                        }
                    }

                    if (!extras.isEmpty()) {
                        if (isStale(page.size() + extras.size(), page.size(), response)) {
                            // A remembered collection was deleted. Fall back to normal paging.
                            Logger.printDebug(() -> "Collections cache is stale, dropping it");
                        } else {
                            for (Object detail : extras) {
                                Object item = newItem(sample, detail);
                                if (item != null) {
                                    items.add(item);
                                }
                            }
                            next.details.addAll(extras);
                            next = new Cache(next.details, cache.cursor, cache.hasMore);
                            writeField(response, "cursor", cache.cursor);
                            writeField(response, "hasMore", cache.hasMore);
                        }
                    }
                }
            } else {
                // Load more: the remembered collections are already shown, so drop repeats.
                Set<String> seen = ids(details(items.subList(0, previousSize), 0));
                for (int index = items.size() - 1; index >= previousSize; index--) {
                    String id = getId(getDetail(items.get(index)));
                    if (id != null && seen.contains(id)) {
                        items.remove(index);
                    }
                }
                List<Object> base = cache != null ? new ArrayList<>(cache.details)
                        : details(items.subList(0, previousSize), 0);
                Set<String> baseIds = ids(base);
                for (Object detail : details(items, previousSize)) {
                    String id = getId(detail);
                    if (id != null && baseIds.add(id)) {
                        base.add(detail);
                    }
                }
                next = new Cache(base, cursor, hasMore);
            }

            caches.put(uid, next);
            saveCache(uid, next);
        }
    }

    /**
     * True when the server total says some remembered collections no longer exist.
     * Ignored when the total is missing or smaller than the first page (not a collection count).
     */
    private static boolean isStale(int mergedSize, int pageSize, Object response) {
        Long total = asLong(readField(response, "total"));
        return total != null && total >= pageSize && mergedSize > total;
    }

    // endregion

    // region Sort

    private static void sort(List items, Sort sort) {
        List<Integer> positions = new ArrayList<>();
        List<Object> collections = new ArrayList<>();
        for (int index = 0; index < items.size(); index++) {
            Object item = items.get(index);
            if (getViewType(item) == COLLECTION_VIEW_TYPE) {
                positions.add(index);
                collections.add(item);
            }
        }
        if (collections.size() < 2) {
            return;
        }

        // Stable, so collections with the same count keep their recent activity order.
        Collections.sort(collections, (first, second) -> {
            int compare = Long.compare(getVideoCount(first), getVideoCount(second));
            return sort == Sort.COUNT ? -compare : compare;
        });

        for (int index = 0; index < positions.size(); index++) {
            items.set(positions.get(index), collections.get(index));
        }
    }

    private static long getVideoCount(Object item) {
        Object detail = getDetail(item);
        Object statistic = detail == null ? null : readField(detail, "statistic");
        Long total = statistic == null ? null : asLong(readField(statistic, "total"));
        return total == null ? 0 : total;
    }

    // endregion

    // region Persistence

    private static SharedPreferences preferences() {
        Context context = Utils.getContext();
        return context == null ? null : context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
    }

    private static void saveCache(String uid, Cache cache) {
        try {
            SharedPreferences preferences = preferences();
            if (preferences == null) {
                return;
            }

            JSONArray array = new JSONArray();
            for (Object detail : cache.details) {
                JSONObject json = toJson(detail);
                if (json != null) {
                    array.put(json);
                }
            }
            JSONObject root = new JSONObject();
            root.put("items", array);
            if (cache.cursor != null) {
                root.put("cursor", cache.cursor.longValue());
            }
            if (cache.hasMore != null) {
                root.put("hasMore", cache.hasMore.booleanValue());
            }
            preferences.edit().putString(uid, root.toString()).apply();
        } catch (Throwable throwable) {
            Logger.printException(() -> "Save collections cache failed", throwable);
        }
    }

    private static Cache loadCache(String uid, Class<?> detailClass) {
        try {
            SharedPreferences preferences = preferences();
            String text = preferences == null ? null : preferences.getString(uid, null);
            if (text == null) {
                return null;
            }

            JSONObject root = new JSONObject(text);
            JSONArray array = root.optJSONArray("items");
            List<Object> details = new ArrayList<>();
            if (array != null) {
                for (int index = 0; index < array.length(); index++) {
                    Object detail = fromJson(array.getJSONObject(index), detailClass);
                    if (detail != null) {
                        details.add(detail);
                    }
                }
            }
            Long cursor = root.has("cursor") ? root.getLong("cursor") : null;
            Boolean hasMore = root.has("hasMore") ? root.getBoolean("hasMore") : null;
            Cache cache = new Cache(details, cursor, hasMore);
            caches.put(uid, cache);
            return cache;
        } catch (Throwable throwable) {
            Logger.printException(() -> "Load collections cache failed", throwable);
            return null;
        }
    }

    /** Only the fields the list cells read. The collection page loads the rest itself. */
    private static JSONObject toJson(Object detail) throws Exception {
        String id = getId(detail);
        if (id == null) {
            return null;
        }

        JSONObject json = new JSONObject();
        json.put("id", id);
        putIfString(json, "name", readField(detail, "name"));
        putIfString(json, "uid", readField(detail, "uid"));
        putIfString(json, "creatorName", readField(detail, "creatorName"));
        putIfString(json, "extra", readField(detail, "extra"));
        Object type = readField(detail, "type");
        if (type instanceof Integer) {
            json.put("type", ((Integer) type).intValue());
        }

        Object statistic = readField(detail, "statistic");
        if (statistic != null) {
            Long total = asLong(readField(statistic, "total"));
            if (total != null) {
                json.put("total", total.longValue());
            }
            Boolean hasVideos = asBoolean(readField(statistic, "hasVideos"));
            json.put("hasVideos", hasVideos != null && hasVideos);
        }

        Object status = readField(detail, "status");
        Object state = status == null ? null : readField(status, "state");
        if (state instanceof Integer) {
            json.put("state", ((Integer) state).intValue());
        }

        Object covers = readField(detail, "covers");
        if (covers instanceof List) {
            JSONArray coverArray = new JSONArray();
            for (Object cover : (List) covers) {
                Object urls = cover == null ? null : readField(cover, "urlList");
                JSONArray urlArray = new JSONArray();
                if (urls instanceof List) {
                    for (Object url : (List) urls) {
                        if (url instanceof String) {
                            urlArray.put(url);
                        }
                    }
                }
                coverArray.put(urlArray);
            }
            json.put("covers", coverArray);
        }

        Object share = readField(detail, "shareInfo");
        if (share != null) {
            JSONObject shareJson = new JSONObject();
            putIfString(shareJson, "desc", readField(share, "desc"));
            putIfString(shareJson, "descInfo", readField(share, "descInfo"));
            putIfString(shareJson, "title", readField(share, "title"));
            putIfString(shareJson, "url", readField(share, "url"));
            json.put("share", shareJson);
        }
        return json;
    }

    private static Object fromJson(JSONObject json, Class<?> detailClass) throws Exception {
        Object detail = detailClass.getDeclaredConstructor().newInstance();
        writeField(detail, "id", json.getString("id"));
        writeField(detail, "name", json.optString("name", ""));
        writeField(detail, "uid", json.optString("uid", null));
        writeField(detail, "creatorName", json.optString("creatorName", null));
        writeField(detail, "extra", json.optString("extra", null));
        if (json.has("type")) {
            writeField(detail, "type", json.getInt("type"));
        }

        Field statisticField = findField(detailClass, "statistic");
        if (statisticField != null) {
            Constructor<?> constructor = statisticField.getType().getDeclaredConstructor(Long.class, boolean.class);
            constructor.setAccessible(true);
            Long total = json.has("total") ? json.getLong("total") : null;
            statisticField.set(detail, constructor.newInstance(total, json.optBoolean("hasVideos", false)));
        }

        Field statusField = findField(detailClass, "status");
        if (statusField != null && json.has("state")) {
            Object status = statusField.getType().getDeclaredConstructor().newInstance();
            writeField(status, "state", json.getInt("state"));
            statusField.set(detail, status);
        }

        JSONArray coverArray = json.optJSONArray("covers");
        if (coverArray != null) {
            Class<?> urlModelClass = Class.forName(URL_MODEL_CLASS, false, detailClass.getClassLoader());
            List<Object> covers = new ArrayList<>();
            for (int index = 0; index < coverArray.length(); index++) {
                JSONArray urlArray = coverArray.optJSONArray(index);
                List<String> urls = new ArrayList<>();
                if (urlArray != null) {
                    for (int urlIndex = 0; urlIndex < urlArray.length(); urlIndex++) {
                        urls.add(urlArray.getString(urlIndex));
                    }
                }
                Object cover = urlModelClass.getDeclaredConstructor().newInstance();
                writeField(cover, "urlList", urls);
                covers.add(cover);
            }
            writeField(detail, "covers", covers);
        }

        JSONObject shareJson = json.optJSONObject("share");
        Field shareField = findField(detailClass, "shareInfo");
        if (shareJson != null && shareField != null) {
            Object share = shareField.getType().getDeclaredConstructor().newInstance();
            writeField(share, "desc", shareJson.optString("desc", null));
            writeField(share, "descInfo", shareJson.optString("descInfo", null));
            writeField(share, "title", shareJson.optString("title", null));
            writeField(share, "url", shareJson.optString("url", null));
            shareField.set(detail, share);
        }
        return detail;
    }

    private static void putIfString(JSONObject json, String name, Object value) throws Exception {
        if (value instanceof String) {
            json.put(name, value);
        }
    }

    // endregion

    // region Reflection helpers

    /** List items are X.0XhM: a CollectionDetail and an int view type. */
    private static Object getDetail(Object item) {
        if (item == null) {
            return null;
        }
        for (Field field : item.getClass().getDeclaredFields()) {
            if (field.getType().getName().endsWith(".CollectionDetail")) {
                try {
                    field.setAccessible(true);
                    return field.get(item);
                } catch (Throwable ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    private static int getViewType(Object item) {
        if (item == null) {
            return -1;
        }
        for (Field field : item.getClass().getDeclaredFields()) {
            if (field.getType() == int.class) {
                try {
                    field.setAccessible(true);
                    return field.getInt(item);
                } catch (Throwable ignored) {
                    return -1;
                }
            }
        }
        return -1;
    }

    private static Object newItem(Object sample, Object detail) {
        try {
            Constructor<?> constructor = sample.getClass().getDeclaredConstructor(detail.getClass(), int.class);
            constructor.setAccessible(true);
            return constructor.newInstance(detail, COLLECTION_VIEW_TYPE);
        } catch (Throwable throwable) {
            Logger.printException(() -> "Collections cache: item creation failed", throwable);
            return null;
        }
    }

    /** Details of the collection items from the given index on, in list order. */
    private static List<Object> details(List items, int from) {
        List<Object> result = new ArrayList<>();
        for (int index = from; index < items.size(); index++) {
            Object item = items.get(index);
            Object detail = getViewType(item) == COLLECTION_VIEW_TYPE ? getDetail(item) : null;
            if (detail != null) {
                result.add(detail);
            }
        }
        return result;
    }

    private static Set<String> ids(List<Object> details) {
        Set<String> result = new HashSet<>();
        for (Object detail : details) {
            String id = getId(detail);
            if (id != null) {
                result.add(id);
            }
        }
        return result;
    }

    private static String getId(Object detail) {
        Object id = detail == null ? null : readField(detail, "id");
        return id instanceof String && !((String) id).isEmpty() ? (String) id : null;
    }

    /** The previous list captured by the loader: its only List field. */
    private static List readListField(Object target) {
        if (target == null) {
            return null;
        }
        for (Field field : target.getClass().getDeclaredFields()) {
            if (field.getType() == List.class) {
                try {
                    field.setAccessible(true);
                    Object value = field.get(target);
                    return value instanceof List ? (List) value : null;
                } catch (Throwable ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    private static Field findField(Class<?> type, String name) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                // Check the superclass.
            }
        }
        return null;
    }

    private static Object readField(Object target, String name) {
        Field field = target == null ? null : findField(target.getClass(), name);
        try {
            return field == null ? null : field.get(target);
        } catch (Throwable throwable) {
            return null;
        }
    }

    /** Also sets final fields, which ART allows through reflection. */
    private static void writeField(Object target, String name, Object value) {
        Field field = target == null ? null : findField(target.getClass(), name);
        if (field == null) {
            return;
        }
        try {
            field.set(target, value);
        } catch (Throwable throwable) {
            Logger.printDebug(() -> String.format(Locale.ROOT, "Collections cache: cannot set %s", name));
        }
    }

    private static Long asLong(Object value) {
        return value instanceof Long ? (Long) value : null;
    }

    private static Boolean asBoolean(Object value) {
        return value instanceof Boolean ? (Boolean) value : null;
    }

    // endregion
}
