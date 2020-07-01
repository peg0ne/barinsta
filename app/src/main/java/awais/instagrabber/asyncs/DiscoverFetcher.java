package awais.instagrabber.asyncs;

import android.os.AsyncTask;
import android.os.Environment;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;

import awais.instagrabber.BuildConfig;
import awais.instagrabber.interfaces.FetchListener;
import awais.instagrabber.models.DiscoverItemModel;
import awais.instagrabber.models.enums.MediaItemType;
import awais.instagrabber.utils.Constants;
import awais.instagrabber.utils.Utils;
import awaisomereport.LogCollector;

import static awais.instagrabber.utils.Constants.FOLDER_PATH;
import static awais.instagrabber.utils.Constants.FOLDER_SAVE_TO;
import static awais.instagrabber.utils.Utils.logCollector;
import static awais.instagrabber.utils.Utils.settingsHelper;

public final class DiscoverFetcher extends AsyncTask<Void, Void, DiscoverItemModel[]> {
    private final String maxId;
    private final FetchListener<DiscoverItemModel[]> fetchListener;
    private int lastId = 0;
    private boolean isFirst, moreAvailable;
    private String nextMaxId;

    public DiscoverFetcher(final String maxId, final FetchListener<DiscoverItemModel[]> fetchListener, final boolean isFirst) {
        this.maxId = maxId == null ? "" : "&max_id=" + maxId;
        this.fetchListener = fetchListener;
        this.isFirst = isFirst;
    }

    @Nullable
    @Override
    protected final DiscoverItemModel[] doInBackground(final Void... voids) {
        // to check if file exists
        final File downloadDir = new File(Environment.getExternalStorageDirectory(), "Download");
        File customDir = null;
        if (settingsHelper.getBoolean(FOLDER_SAVE_TO)) {
            final String customPath = settingsHelper.getString(FOLDER_PATH);
            if (!Utils.isEmpty(customPath)) customDir = new File(customPath);
        }

        DiscoverItemModel[] result = null;

        final ArrayList<DiscoverItemModel> discoverItemModels = fetchItems(downloadDir, customDir, null, maxId);
        if (discoverItemModels != null) {
            result = discoverItemModels.toArray(new DiscoverItemModel[0]);
            if (result.length > 0) {
                final DiscoverItemModel lastModel = result[result.length - 1];
                if (lastModel != null) lastModel.setMore(moreAvailable, nextMaxId);
            }
        }

        return result;
    }

    private ArrayList<DiscoverItemModel> fetchItems(final File downloadDir, final File customDir,
                                                    ArrayList<DiscoverItemModel> discoverItemModels, final String maxId) {
        try {
            final String url = "https://www.instagram.com/explore/grid/?is_prefetch=false&omit_cover_media=true&module=explore_popular" +
                    "&use_sectional_payload=false&cluster_id=explore_all%3A0&include_fixed_destinations=true" + maxId;

            final HttpURLConnection urlConnection = (HttpURLConnection) new URL(url).openConnection();

            urlConnection.setUseCaches(false);
            urlConnection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 8.1.0; motorola one Build/OPKS28.63-18-3; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/70.0.3538.80 Mobile Safari/537.36 Instagram 72.0.0.21.98 Android (27/8.1.0; 320dpi; 720x1362; motorola; motorola one; deen_sprout; qcom; pt_BR; 132081645)");

            if (urlConnection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                final JSONObject discoverResponse = new JSONObject(Utils.readFromConnection(urlConnection));

                moreAvailable = discoverResponse.getBoolean("more_available");
                nextMaxId = discoverResponse.getString("next_max_id");

                final JSONArray sectionalItems = discoverResponse.getJSONArray("sectional_items");
                if (discoverItemModels == null) discoverItemModels = new ArrayList<>(sectionalItems.length() * 2);

                for (int i = 0; i < sectionalItems.length(); ++i) {
                    final JSONObject sectionItem = sectionalItems.getJSONObject(i);

                    final String feedType = sectionItem.getString("feed_type");
                    final String layoutType = sectionItem.getString("layout_type");

                    if (sectionItem.has("layout_content") && feedType.equals("media")) {
                        final JSONObject layoutContent = sectionItem.getJSONObject("layout_content");

                        if ("media_grid".equals(layoutType)) {
                            final JSONArray medias = layoutContent.getJSONArray("medias");
                            for (int j = 0; j < medias.length(); ++j)
                                discoverItemModels.add(makeDiscoverModel(downloadDir, customDir,
                                        medias.getJSONObject(j).getJSONObject("media")));

                        } else {
                            final boolean isOneSide = "one_by_two_left".equals(layoutType);
                            if (isOneSide || "two_by_two_right".equals(layoutType)) {

                                final JSONObject layoutItem = layoutContent.getJSONObject(isOneSide ? "one_by_two_item" : "two_by_two_item");
                                if (layoutItem.has("media"))
                                    discoverItemModels.add(makeDiscoverModel(downloadDir, customDir,
                                            layoutItem.getJSONObject("media")));

                                if (layoutContent.has("fill_items")) {
                                    final JSONArray fillItems = layoutContent.getJSONArray("fill_items");
                                    for (int j = 0; j < fillItems.length(); ++j)
                                        discoverItemModels.add(makeDiscoverModel(downloadDir, customDir,
                                                fillItems.getJSONObject(j).getJSONObject("media")));
                                }
                            }
                        }
                    }
                }

                discoverItemModels.trimToSize();
                urlConnection.disconnect();

                // hack to fetch 50+ items
                if (this.isFirst) {
                    final int size = discoverItemModels.size();
                    if (size > 50) this.isFirst = false;
                    discoverItemModels = fetchItems(downloadDir, customDir, discoverItemModels,
                            "&max_id=" + (lastId++));
                }
            } else {
                urlConnection.disconnect();
            }
        } catch (final Exception e) {
            if (logCollector != null)
                logCollector.appendException(e, LogCollector.LogFile.ASYNC_DISCOVER_FETCHER, "fetchItems",
                        new Pair<>("maxId", maxId),
                        new Pair<>("lastId", lastId),
                        new Pair<>("isFirst", isFirst),
                        new Pair<>("nextMaxId", nextMaxId));
            if (BuildConfig.DEBUG) Log.e("AWAISKING_APP", "", e);
        }

        return discoverItemModels;
    }

    @NonNull
    private DiscoverItemModel makeDiscoverModel(final File downloadDir, final File customDir,
                                                @NonNull final JSONObject media) throws Exception {
        final JSONObject user = media.getJSONObject(Constants.EXTRAS_USER);
        final String username = user.getString(Constants.EXTRAS_USERNAME);
        // final ProfileModel userProfileModel = new ProfileModel(user.getBoolean("is_private"),
        //         user.getBoolean("is_verified"),
        //         String.valueOf(user.get("pk")),
        //         username,
        //         user.getString("full_name"),
        //         null,
        //         user.getString("profile_pic_url"), null,
        //         0, 0, 0);

        // final String comment;
        // if (!media.has("caption")) comment = null;
        // else {
        //     final Object caption = media.get("caption");
        //     comment = caption instanceof JSONObject ? ((JSONObject) caption).getString("text") : null;
        // }

        final MediaItemType mediaType = Utils.getMediaItemType(media.getInt("media_type"));

        final DiscoverItemModel model = new DiscoverItemModel(mediaType,
                media.getString(Constants.EXTRAS_ID),
                media.getString("code"),
                Utils.getThumbnailUrl(media, mediaType));

        Utils.checkExistence(downloadDir, customDir, username,
                mediaType == MediaItemType.MEDIA_TYPE_SLIDER, -1, model);

        return model;
    }

    @Override
    protected void onPreExecute() {
        if (fetchListener != null) fetchListener.doBefore();
    }

    @Override
    protected void onPostExecute(final DiscoverItemModel[] discoverItemModels) {
        if (fetchListener != null) fetchListener.onResult(discoverItemModels);
    }
}