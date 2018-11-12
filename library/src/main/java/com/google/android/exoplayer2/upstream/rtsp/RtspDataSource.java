package com.google.android.exoplayer2.upstream.rtsp;

import android.net.Uri;
import android.support.annotation.IntDef;

import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;

import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;
import java.util.Map;
/**
 * Created by Young on 17/3/17.
 */

public interface RtspDataSource extends DataSource {
    /**
     * A factory for {@link RtspDataSource} instances.
     */
    interface Factory extends DataSource.Factory {
        @Override
        RtspDataSource createDataSource();
    }

    /**
     * Base implementation of {@link Factory}
     */
    abstract class BaseFactory implements Factory {
        @Override
        public RtspDataSource createDataSource() {
            return null;
        }
    }

    /**
     * Thrown when an error is encountered when trying to read from a {@link RtspDataSource}.
     */
    class RtspDataSourceException extends IOException {

        @Retention(RetentionPolicy.SOURCE)
        @IntDef({TYPE_OPEN, TYPE_READ, TYPE_CLOSE})
        public @interface Type {}
        public static final int TYPE_OPEN = 1;
        public static final int TYPE_READ = 2;
        public static final int TYPE_CLOSE = 3;

        @Type
        public final int type;

        /**
         * The {@link DataSpec} associated with the current connection.
         */
        public final DataSpec dataSpec;

        public RtspDataSourceException(DataSpec dataSpec, @Type int type) {
            super();
            this.dataSpec = dataSpec;
            this.type = type;
        }

        public RtspDataSourceException(String message, DataSpec dataSpec, @Type int type) {
            super(message);
            this.dataSpec = dataSpec;
            this.type = type;
        }

        public RtspDataSourceException(IOException cause, DataSpec dataSpec, @Type int type) {
            super(cause);
            this.dataSpec = dataSpec;
            this.type = type;
        }

        public RtspDataSourceException(String message, IOException cause, DataSpec dataSpec,
                                       @Type int type) {
            super(message, cause);
            this.dataSpec = dataSpec;
            this.type = type;
        }

    }


    /**
     * Thrown when the content type is invalid.
     */
    final class InvalidContentTypeException extends RtspDataSourceException {

        public final String contentType;

        public InvalidContentTypeException(String contentType, DataSpec dataSpec) {
            super("Invalid content type: " + contentType, dataSpec, TYPE_OPEN);
            this.contentType = contentType;
        }

    }

    /**
     * Thrown when an attempt to open a connection results in a response code not in the 2xx range.
     */
    final class InvalidResponseCodeException extends RtspDataSourceException {

        /**
         * The response code that was outside of the 2xx range.
         */
        public final int responseCode;

        /**
         * An unmodifiable map of the response header fields and values.
         */
        public final Map<String, List<String>> headerFields;

        public InvalidResponseCodeException(int responseCode, Map<String, List<String>> headerFields,
                                            DataSpec dataSpec) {
            super("Response code: " + responseCode, dataSpec, TYPE_OPEN);
            this.responseCode = responseCode;
            this.headerFields = headerFields;
        }
    }

    @Override
    long open(DataSpec dataSpec) throws IOException;

    @Override
    int read(byte[] buffer, int offset, int readLength) throws IOException;

    @Override
    Uri getUri();

    @Override
    void close() throws IOException;
}
