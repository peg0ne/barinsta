package awais.instagrabber.models;

import awais.instagrabber.models.enums.MediaItemType;

public class PostModel extends BasePostModel {
    protected final String thumbnailUrl;
    protected String endCursor;
    protected boolean hasNextPage;

    public PostModel(final String shortCode) {
        this.shortCode = shortCode;
        this.thumbnailUrl = null;
    }

    public PostModel(final MediaItemType itemType, final String postId, final String displayUrl, final String thumbnailUrl,
                     final String shortCode, final CharSequence postCaption, long timestamp) {
        this.itemType = itemType;
        this.postId = postId;
        this.displayUrl = displayUrl;
        this.thumbnailUrl = thumbnailUrl;
        this.shortCode = shortCode;
        this.postCaption = postCaption;
        this.timestamp = timestamp;
    }

    public String getThumbnailUrl() {
        return thumbnailUrl;
    }

    public String getEndCursor() {
        return endCursor;
    }

    public boolean hasNextPage() {
        return endCursor != null && hasNextPage;
    }

    public void setPostCaption(final CharSequence postCaption) {
        this.postCaption = postCaption;
    }

    public void setTimestamp(final long timestamp) {
        this.timestamp = timestamp;
    }

    public void setPageCursor(final boolean hasNextPage, final String endCursor) {
        this.endCursor = endCursor;
        this.hasNextPage = hasNextPage;
    }
}