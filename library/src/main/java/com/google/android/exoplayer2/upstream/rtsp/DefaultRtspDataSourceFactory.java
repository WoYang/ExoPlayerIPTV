package com.google.android.exoplayer2.upstream.rtsp;

import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.TransferListener;
/**
 * Created by Young on 17/3/17.
 */

/** A {@link RtspDataSource.Factory} that produces {@link DefaultRtspDataSource} instances. */
public  class DefaultRtspDataSourceFactory extends RtspDataSource.BaseFactory{

    private  String userAgent = null;
    private  TransferListener<? super DataSource> listener;
    private  int connectTimeoutMillis = 0;
    private  int readTimeoutMillis = 0;
    private  boolean allowCrossProtocolRedirects = false;
    /**
     * Constructs a DefaultHttpDataSourceFactory. Sets {@link
     * DefaultRtspDataSource#DEFAULT_CONNECT_TIMEOUT_MILLIS} as the connection timeout, {@link
     * DefaultRtspDataSource#DEFAULT_READ_TIMEOUT_MILLIS} as the read timeout and disables
     * cross-protocol redirects.
     *
     * @param userAgent The User-Agent string that should be used.
     */
    public DefaultRtspDataSourceFactory(String userAgent) {
        this(userAgent, null);
    }

    /**
     * Constructs a DefaultHttpDataSourceFactory. Sets {@link
     * DefaultRtspDataSource#DEFAULT_CONNECT_TIMEOUT_MILLIS} as the connection timeout, {@link
     * DefaultRtspDataSource#DEFAULT_READ_TIMEOUT_MILLIS} as the read timeout and disables
     * cross-protocol redirects.
     *
     * @param userAgent The User-Agent string that should be used.
     * @param listener An optional listener.
     * @see #DefaultRtspDataSourceFactory(String, TransferListener, int, int, boolean)
     */
    public DefaultRtspDataSourceFactory(
            String userAgent, TransferListener<? super DataSource> listener) {
        this(userAgent, listener, DefaultRtspDataSource.DEFAULT_CONNECT_TIMEOUT_MILLIS,
                DefaultRtspDataSource.DEFAULT_READ_TIMEOUT_MILLIS, false);
    }

    /**
     * @param userAgent The User-Agent string that should be used.
     * @param listener An optional listener.
     * @param connectTimeoutMillis The connection timeout that should be used when requesting remote
     *     data, in milliseconds. A timeout of zero is interpreted as an infinite timeout.
     * @param readTimeoutMillis The read timeout that should be used when requesting remote data, in
     *     milliseconds. A timeout of zero is interpreted as an infinite timeout.
     * @param allowCrossProtocolRedirects Whether cross-protocol redirects (i.e. redirects from HTTP
     *     to HTTPS and vice versa) are enabled.
     */
    public DefaultRtspDataSourceFactory(String userAgent,
                                        TransferListener<? super DataSource> listener, int connectTimeoutMillis,
                                        int readTimeoutMillis, boolean allowCrossProtocolRedirects) {
        this.userAgent = userAgent;
        this.listener = listener;
        this.connectTimeoutMillis = connectTimeoutMillis;
        this.readTimeoutMillis = readTimeoutMillis;
        this.allowCrossProtocolRedirects = allowCrossProtocolRedirects;
    }

    @Override
    public RtspDataSource createDataSource() {
        return new DefaultRtspDataSource(userAgent, null, listener, connectTimeoutMillis,
                readTimeoutMillis, allowCrossProtocolRedirects);
    }
}
