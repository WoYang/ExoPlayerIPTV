package com.google.android.exoplayer2.upstream.rtsp;

import android.content.res.Resources;
import android.net.Uri;

import android.os.Handler;
import android.os.Message;
import android.os.Process;
import android.util.Log;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.extractor.ts.TsExtractor;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.TransferListener;
import com.google.android.exoplayer2.upstream.UdpDataSource;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.IpUtil;
import com.google.android.exoplayer2.util.Predicate;
import com.google.android.exoplayer2.util.SystemClock;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Created by Young on 17/3/17.
 */

public class DefaultRtspDataSource implements RtspDataSource {
    /**
     * The default connection timeout, in milliseconds.
     */
    public static final int DEFAULT_CONNECT_TIMEOUT_MILLIS = 8 * 1000;
    /**
     * The default read timeout, in milliseconds.
     */
    public static final int DEFAULT_READ_TIMEOUT_MILLIS = 8 * 1000;

    private static final int BUFFER_SIZE = 8192;

    private static final String TAG = "DefaultRtspDataSource";

    private final boolean allowCrossProtocolRedirects;
    private final int connectTimeoutMillis;
    private final int readTimeoutMillis;
    private final String userAgent;
    private final Predicate<String> contentTypePredicate;
    private final TransferListener<? super DefaultRtspDataSource> listener;

    //add for bytes read
    private DataSpec dataSpec;

    //add for rtsp init
    private String localIpAddress = null;
    private int localPort = 0;
    private String remoteIpAddress = null;
    private int remotePort = 0;
    private Selector selector;
    private SocketChannel socketChannel;
    private int udpPort = 0;

    private String sessionid;
    private String trackID;
    private int Cseq=1;

    private ByteBuffer sendBuf;
    private ByteBuffer receiveBuf;

    private int read_sqe = -1;
    boolean firstSqe = true;
    private Thread looper_main = null;

    //rtsp status
    private enum RtspStatus {
        init, options, describe, setup, play, pause, teardown
    }
    private RtspStatus mstatus;
    private boolean readEnable = false;

    //for rtsp udp
    private boolean tcpPrefer = false;
    private DatagramSocket udpSockect = null;

    private int packet_buffer_length = 0;
    private int bytesToSkip = 0;

    //for byte cache
    private final static int capacity = 65536;
    private ArrayBlockingQueue<ArrayBlockingQueue> fully_queue = new ArrayBlockingQueue<ArrayBlockingQueue>(capacity);
    private ArrayBlockingQueue<byte[]> queue = null;
    private QueueProducer mProducer = null;

    private byte[] datacopy;

    public static final int MSG_BASE = 0;
    public static final int MSG_OPTION = MSG_BASE + 1;
    public static final int MSG_DESCRIBE = MSG_BASE + 2;
    public static final int MSG_SETUP = MSG_BASE + 3;
    public static final int MSG_PLAY = MSG_BASE + 4;
    public String address_request = "";
    private Handler mainHandler = new Handler(){
        @Override
        public void handleMessage(Message msg) {
            try {
                Log.d(TAG,"handle message:"+msg.what);
                if(msg.obj == null){
                    return;
                }
                address_request = (String)msg.obj;
                switch (msg.what){
                    case MSG_OPTION:
                        startOption(address_request,msg.arg1);
                        checkResponse();
                        break;
                    case MSG_DESCRIBE:
                        startDescribe(address_request,msg.arg1);
                        checkResponse();
                        break;
                    case MSG_SETUP:
                        socketInit();
                        startSetup(address_request,msg.arg1);
                        checkResponse();
                        break;
                    case MSG_PLAY:
                        startPlay(address_request,msg.arg1);
                        checkResponse();
                        break;
                }
            }catch (Exception e){
                e.printStackTrace();
            }
        }
    };

    /**
     * @param userAgent The User-Agent string that should be used.
     * @param contentTypePredicate An optional {@link Predicate}. If a content type is rejected by the
     *     predicate then a {@link RtspDataSource.InvalidContentTypeException} is thrown from
     *     {@link #open(DataSpec)}.
     */
    public DefaultRtspDataSource(String userAgent, Predicate<String> contentTypePredicate) {
        this(userAgent, contentTypePredicate, null);
    }

    /**
     * @param userAgent The User-Agent string that should be used.
     * @param contentTypePredicate An optional {@link Predicate}. If a content type is rejected by the
     *     predicate then a {@link RtspDataSource.InvalidContentTypeException} is thrown from
     *     {@link #open(DataSpec)}.
     * @param listener An optional listener.
     */
    public DefaultRtspDataSource(String userAgent, Predicate<String> contentTypePredicate,
                                 TransferListener<? super DefaultRtspDataSource> listener) {
        this(userAgent, contentTypePredicate, listener, DEFAULT_CONNECT_TIMEOUT_MILLIS,
                DEFAULT_READ_TIMEOUT_MILLIS);
    }

    /**
     * @param userAgent The User-Agent string that should be used.
     * @param contentTypePredicate An optional {@link Predicate}. If a content type is rejected by the
     *     predicate then a {@link RtspDataSource.InvalidContentTypeException} is thrown from
     *     {@link #open(DataSpec)}.
     * @param listener An optional listener.
     * @param connectTimeoutMillis The connection timeout, in milliseconds. A timeout of zero is
     *     interpreted as an infinite timeout.
     * @param readTimeoutMillis The read timeout, in milliseconds. A timeout of zero is interpreted
     *     as an infinite timeout.
     */
    public DefaultRtspDataSource(String userAgent, Predicate<String> contentTypePredicate,
                                 TransferListener<? super DefaultRtspDataSource> listener, int connectTimeoutMillis,
                                 int readTimeoutMillis) {
        this(userAgent, contentTypePredicate, listener, connectTimeoutMillis, readTimeoutMillis, false);
    }

    /**
     * @param userAgent The User-Agent string that should be used.
     * @param contentTypePredicate An optional {@link Predicate}. If a content type is rejected by the
     *     predicate then a {@link RtspDataSource.InvalidContentTypeException} is thrown from
     *     {@link #open(DataSpec)}.
     * @param listener An optional listener.
     * @param connectTimeoutMillis The connection timeout, in milliseconds. A timeout of zero is
     *     interpreted as an infinite timeout. Pass {@link #DEFAULT_CONNECT_TIMEOUT_MILLIS} to use
     *     the default value.
     * @param readTimeoutMillis The read timeout, in milliseconds. A timeout of zero is interpreted
     *     as an infinite timeout. Pass {@link #DEFAULT_READ_TIMEOUT_MILLIS} to use the default value.
     * @param allowCrossProtocolRedirects Whether cross-protocol redirects (i.e. redirects from HTTP
     *     to HTTPS and vice versa) are enabled.
     */
    public DefaultRtspDataSource(String userAgent, Predicate<String> contentTypePredicate,
                                 TransferListener<? super DefaultRtspDataSource> listener, int connectTimeoutMillis,
                                 int readTimeoutMillis, boolean allowCrossProtocolRedirects) {
        this.userAgent = Assertions.checkNotEmpty(userAgent);
        this.contentTypePredicate = contentTypePredicate;
        this.listener = listener;
        this.connectTimeoutMillis = connectTimeoutMillis;
        this.readTimeoutMillis = readTimeoutMillis;
        this.allowCrossProtocolRedirects = allowCrossProtocolRedirects;
        this.firstSqe = true;
    }

    @Override
    public long open(DataSpec dataSpec) throws IOException {
        Log.d(TAG," open ");
        this.dataSpec = dataSpec;

        if(queue != null){
            queue.clear();
        }

        initLocalAddress();
        initRemoteAddress();

        rtspStart();

        //callback
        if (listener != null) {
            listener.onTransferStart(this, dataSpec);
        }
        return packet_buffer_length - bytesToSkip;
    }

    @Override
    public int read(byte[] buffer, int offset, int readLength) throws IOException {
        if(readEnable){
            return readDataFromUdpSocket(buffer,offset,readLength);
        }
        return 0;
    }

    @Override
    public Uri getUri() {
        Log.d(TAG," getUri ");
        return this.dataSpec == null ? null : Uri.parse(this.dataSpec.uri.toString());
    }

    @Override
    public void close() throws IOException {
        Log.d(TAG," close ");
        if (isConnected()) {
            try {
                socketChannel.close();
                udpSockect.disconnect();
                udpSockect.close();
                looper_main.interrupt();
                if(mProducer != null){
                    mProducer.interrupt = true;
                }
            } catch (final Exception e) {
                e.printStackTrace();
            } finally {
                socketChannel = null;
                udpSockect = null;
                mProducer = null;
                looper_main = null;
            }
        }
    }

    private void socketInit() {
        try {
            if(!tcpPrefer){
                udpSockect = new DatagramSocket(0); //get a valid random port by system
                udpPort = udpSockect.getLocalPort();
                Log.d(TAG,"creat udp port:"+udpPort);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //get physical hardware ip
    private void initLocalAddress(){
        this.localIpAddress = IpUtil.getHostIP();
        this.localPort = 0;
        Log.d(TAG,"localIpAddress:"+localIpAddress+" localPort:"+localPort);
    }

    private void initRemoteAddress(){
        String url = this.dataSpec.uri.toString();
        //rtsp://11.2.3.4:554/index/1
        if (url == null || "".equals(url)) {
            Log.d(TAG, "url is null");
            return;
        }
        Log.d(TAG, "url:"+url);
        int startIndex = url.indexOf("rtsp://", 0);
        int endIndex = url.indexOf("/", url.indexOf("rtsp://") + "rtsp://".length());
        String match = url.substring(startIndex+"rtsp://".length(),endIndex);
        if(match.contains(":")){
            String[] data = match.split(":");
            this.remoteIpAddress = data[0];
            this.remotePort = Integer.valueOf(data[1]);
        }else{
            // url not contains port , use the default 554
            this.remoteIpAddress = match;
            this.remotePort = 554;
        }
        Log.d(TAG, "remoteIpAddress:"+remoteIpAddress + " remotePort:"+remotePort);
    }

    //add for rtsp check
    //check rtsp server ip and port
    private void rtspStart(){
        try {
            String address = this.dataSpec.uri.toString();
            socketChannel = SocketChannel.open();
            socketChannel.socket().setSoTimeout(connectTimeoutMillis);
            socketChannel.configureBlocking(false);
            InetSocketAddress localAddress = new InetSocketAddress(this.localIpAddress, this.localPort);
            InetSocketAddress remoteAddress = new InetSocketAddress(this.remoteIpAddress, this.remotePort);
            socketChannel.socket().bind(localAddress);
            socketChannel.connect(remoteAddress);
            while(!socketChannel.finishConnect()) {
                //wait connect finish
            }
            Log.d(TAG,"rtsp socket connect success");
            if(selector == null){
                selector = Selector.open();
            }
            socketChannel.register(selector, SelectionKey.OP_CONNECT
                    | SelectionKey.OP_READ | SelectionKey.OP_WRITE, this);

            //sync exc rtsp connect
            Message msg = mainHandler.obtainMessage();
            msg.what = MSG_OPTION;
            msg.arg1 = Cseq;
            msg.obj = address;
            mainHandler.sendMessage(msg);

            while(!readEnable){
                Thread.sleep(1000);
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }
    //just for ts | do what for other packet?
    private void peekFirstPacket(){
        try {
            if(isConnected()) {
                Log.d(TAG,"socketConnect success");
                byte[] data = new byte[BUFFER_SIZE];
                DatagramPacket  dp = new DatagramPacket(data, data.length);
                udpSockect.receive(dp);
                packet_buffer_length = dp.getLength();
                Log.d(TAG,"first packet length:"+ packet_buffer_length);
                if(dp.getLength() < 0){
                    return;
                }
                //rtp packet info?
                if(data[0] == 0x80){

                }
                for(int i = 0;i < packet_buffer_length;i++) {
                    //we need to kown this packet is whether rtp header
                    //find ts sync packet byte
                    if (data[i] == TsExtractor.TS_SYNC_BYTE) {
                        Log.d(TAG,"match ts sync byte at "+ i);
                        bytesToSkip = packet_buffer_length%TsExtractor.TS_PACKET_SIZE;
                        Log.d(TAG,"packet need skip byte length:"+ bytesToSkip);
                        if(bytesToSkip != i){
                            Log.d(TAG,"packet maybe not valid");
                        }
                        int copy_length = packet_buffer_length - bytesToSkip;
                        Log.d(TAG,"vaild ts packet:"+ copy_length);
                        datacopy = new byte[copy_length];
                        TsExtractor.setBufferSize(copy_length);
                        // first check
                        System.arraycopy(data, i, datacopy, 0, copy_length);
                        //for cache size
                        if(queue == null){
                            queue = new ArrayBlockingQueue<byte[]>(capacity*copy_length/TsExtractor.TS_PACKET_SIZE);
                        }else{
                            queue.clear();
                        }
                        break;
                    }
                }
            }else{
                Log.d(TAG,"socketConnect fail");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private int readDataFromUdpSocket(byte[] buffer,int offset,int length){
        try {
            if (udpSockect != null) {
                if(offset == 0){
                    //first extractor check
                    if(length == 4){
                        //we can use the rtsp first check data to offset check
                        System.arraycopy(datacopy,0, buffer, 0, datacopy.length);
                    }else{
                        long cur_readtime = System.currentTimeMillis();
                        byte[] oridata = queueConsumer();
                        long last_readtime = System.currentTimeMillis();
                        //never do this
                        if(oridata == null){
                            Log.d(TAG,"no cache data");
//                            return C.LENGTH_UNSET;
                            return 0;
                        }
                        // Sequence number
                        int seq_num = byte2int(oridata[2],oridata[3]);
                        if(firstSqe){
                            read_sqe = seq_num;
                            firstSqe = false;
                        }else{
                            if(read_sqe >= 65535){
                                read_sqe = 0;
                            }else{
                                read_sqe++;
                            }
                        }
                        if(seq_num != read_sqe){
                            // maybe loss some packet
                            Log.d(TAG,"maybe loss packet,this sequence number :"+seq_num);
                        }else{
                            Log.d(TAG,"read sequence number :"+seq_num);
                        }
                        read_sqe = seq_num;
                        // maybe dropframes wait more than 30ms ,but this the most efficient way to copy bytes
                        System.arraycopy(oridata,bytesToSkip, buffer, 0, packet_buffer_length-bytesToSkip);
                    }
                }else{
                    //we can use the rtsp first check data to offset check
                    System.arraycopy(datacopy,0, buffer, 0, datacopy.length);
                }
                if (listener != null) {
                    listener.onBytesTransferred(this, length);
                }
                return length;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return C.LENGTH_UNSET;
    }

    private boolean isConnected() {
         return socketChannel != null && socketChannel.isConnected();
    }

    private void channelConnect(SelectionKey key){
        try {
            if(isConnected()) {
                return;
            }
            socketChannel.finishConnect();
            while(!socketChannel.isConnected()){
                Thread.sleep(200);
                socketChannel.finishConnect();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void checkResponse(){
        try {
            while(true){
                if(selector == null){
                    Log.d(TAG,"selector is null");
                    return;
                }
                if(selector.select(1000) > 0){
                    for(Iterator<SelectionKey> keys = selector.selectedKeys().iterator();keys.hasNext();){
                        SelectionKey key = keys.next();
                        keys.remove();
                        if(!key.isValid()){
                            Log.d(TAG,"key is not valid");
                            continue;
                        }
                        if(key.isConnectable()){
                            Log.d(TAG,"do channelConnect");
                            channelConnect(key);
                        }else if(key.isReadable()){
                            if(channelRead(key)){
                                Log.d(TAG,"get 200/302 response");
                                return;
                            }
                        }
                    }
                }else{
                    Log.d(TAG,"selector select not valid");
                }
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    private boolean channelRead(SelectionKey key){
        try {
            if(isConnected()){
                int length = 0;
                int readnum = 0;
                if(receiveBuf == null){
                    receiveBuf = ByteBuffer.allocateDirect(BUFFER_SIZE);
                }
                synchronized (receiveBuf){
                    receiveBuf.clear();
                    while ((length =socketChannel.read(receiveBuf)) > 0){
                        readnum += length;
                    }
                    receiveBuf.flip();
                    if(readnum > 0){
                        return responseParse();
                    }else{
                        Log.d(TAG,"channel read null buffer");
                        key.cancel();
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    private boolean responseParse(){
        String response = ByteBuffer2String(receiveBuf);
        Log.d(TAG, "read response:\r\n"+response);
        if(true){
            //add 302 20170626
            switch (mstatus) {
                case init:
                    //nothing to do
                    break;
                case options:
                    if(response.startsWith("RTSP/1.0 200 OK")) {
                        Message msg = mainHandler.obtainMessage();
                        msg.what = MSG_DESCRIBE;
                        msg.arg1 = ++Cseq;
                        msg.obj = address_request;
                        mainHandler.sendMessage(msg);
                        return true;
                    }else if(response.startsWith("RTSP/1.0 302 Found")){
                        String location = response.substring(response.indexOf("Location:")+"Location:".length());
                        if(location != null && location.length() > 0) {
                            location = location.substring(0, location.indexOf('\n')).trim();
                            Message msg = mainHandler.obtainMessage();
                            msg.what = MSG_OPTION;
                            msg.arg1 = Cseq;
                            msg.obj = location;
                            mainHandler.sendMessage(msg);
                            return true;
                        }
                    }
                    break;
                case describe:
                    if(response.startsWith("RTSP/1.0 302 Found")){
                        Log.d(TAG,"describe 302");
                        String location = response.substring(response.indexOf("Location:")+"Location:".length());
                        Log.d(TAG,"location:"+location);
                        if(location != null && location.length() > 0) {
                            location = location.substring(0, location.indexOf('\n')).trim();
                            Log.d(TAG,"location check:"+location);

                            Message msg = mainHandler.obtainMessage();
                            msg.what = MSG_DESCRIBE;
                            msg.arg1 = Cseq;
                            msg.obj = location;
                            mainHandler.sendMessage(msg);
                            return true;
                        }
                    }
                    //for vlc server transport 2 times
                    if(response.startsWith("RTSP/1.0 200 OK")){
                        Message msg = mainHandler.obtainMessage();
                        msg.what = MSG_SETUP;
                        msg.arg1 = ++Cseq;
                        msg.obj = address_request;
                        mainHandler.sendMessage(msg);
                        return true;
                    }
                    if(response.contains("trackID=")){
                        trackID = response.substring(response.indexOf("trackID="));
                        if(trackID != null && trackID.length() > 0){
                            Log.d(TAG,"trackID:"+trackID);
                            return true;
                        }
                    }
                    break;
                case setup:
                    if(response.startsWith("RTSP/1.0 302 Found")){
                        String location = response.substring(response.indexOf("Location:")+"Location:".length());
                        if(location != null && location.length() > 0) {
                            location = location.substring(0, location.indexOf('\n')).trim();
                            Message msg = mainHandler.obtainMessage();
                            msg.what = MSG_SETUP;
                            msg.arg1 = Cseq;
                            msg.obj = location;
                            mainHandler.sendMessage(msg);
                            return true;
                        }
                    }
                    if(response.contains("Transport:")){
                        //check tcp/udp && udp default
                        tcpPrefer = false;
                        //if tcp prefer we need to retry rtspStart
                    }
                    if(response.contains("Session:")){
                        sessionid = response.substring(response.indexOf("Session:")+"Session:".length());
                        if(sessionid != null && sessionid.length() > 0) {
                            sessionid = sessionid.substring(0,sessionid.indexOf('\n')).trim();
                            Log.d(TAG,"sessionid:"+sessionid);
                            Message msg = mainHandler.obtainMessage();
                            msg.what = MSG_PLAY;
                            msg.arg1 = ++Cseq;
                            msg.obj = address_request;
                            mainHandler.sendMessage(msg);
                            return true;
                        }
                    }
                    break;
                case play:
                    if(response.startsWith("RTSP/1.0 200 OK")) {
                        peekFirstPacket();
                        if(queue == null){
                            //default this udp packet size is 1316
                            queue = new ArrayBlockingQueue<byte[]>(capacity*7);
                        }else{
                            queue.clear();
                        }
                        //if can get Sequence number ,use multi thread to read sockect
                        mProducer = new QueueProducer(TAG);
                        looper_main = new Thread(mProducer);
                        looper_main.start();
                        readEnable = true;
                        return true;
                    }else if(response.startsWith("RTSP/1.0 302 Found")){
                        String location = response.substring(response.indexOf("Location:")+"Location:".length());
                        if(location != null && location.length() > 0) {
                            location = location.substring(0, location.indexOf('\n')).trim();
                            Message msg = mainHandler.obtainMessage();
                            msg.what = MSG_PLAY;
                            msg.arg1 = Cseq;
                            msg.obj = location;
                            mainHandler.sendMessage(msg);
                            return true;
                        }
                    }
                    break;
                case pause:
                    break;
                case teardown:
                    break;
                default:
                    break;
            }
        }
        return false;
    }

    public void send(byte[] datas) {
        try {
            if (datas == null || datas.length < 1) {
                return;
            }
            write(datas);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void write(byte[] datas) throws IOException {
        try {
            if(sendBuf == null){
                sendBuf = ByteBuffer.allocateDirect(BUFFER_SIZE);
            }
            sendBuf.clear();
            sendBuf.put(datas);
            sendBuf.flip();
            if (isConnected()) {
                socketChannel.write(sendBuf);
            } else {
                Log.d(TAG,"rtsp sockect not ready");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void startOption(String address,int Cseq){
        Log.d(TAG,"do startOption");
        StringBuilder builder = RtspCommand.getOptionsCommand(address,Cseq);
        send(builder.toString().getBytes());
        mstatus = RtspStatus.options;
    }

    private void startDescribe(String address,int Cseq){
        Log.d(TAG,"do startDescribe");
        StringBuilder builder = RtspCommand.getDescribeCommand(address,Cseq);
        send(builder.toString().getBytes());
        mstatus = RtspStatus.describe;
    }

    private void startSetup(String address,int Cseq){
        Log.d(TAG,"do startSetup");
        StringBuilder builder = RtspCommand.getSetupCommand(address,Cseq,localIpAddress,udpPort);
        send(builder.toString().getBytes());
        mstatus = RtspStatus.setup;
    }

    private void startPlay(String address,int Cseq){
        Log.d(TAG,"do startPlay");
        StringBuilder builder = RtspCommand.getPlayCommand(address,Cseq,sessionid,1,0);
        send(builder.toString().getBytes());
        mstatus = RtspStatus.play;
    }

    public static String ByteBuffer2String(ByteBuffer buffer){
        try {
            Charset charset = Charset.forName("UTF-8");
            CharsetDecoder decoder = charset.newDecoder();
            CharBuffer charBuffer = decoder.decode(buffer.asReadOnlyBuffer());
            return charBuffer.toString();
        }catch (Exception e){
            e.printStackTrace();
        }
        return null;
    }

    //rtsp rtp message
    public static int byte2int(byte high,byte low) {
        int targets = (low & 0xff) | ((high << 8) & 0xff00);
        return targets;
    }

    public class QueueProducer implements Runnable{
        private String Log_Tag = TAG;
        public boolean interrupt = false;
        public QueueProducer(String Tag){
            Log_Tag = Tag;
        }

        @Override
        public void run() {
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);
            try {
                while (!interrupt) {
                    byte[] data = new byte[packet_buffer_length];
                    DatagramPacket dp = new DatagramPacket(data,0,data.length);
                    udpSockect.receive(dp);
                    if(dp.getLength() > 0){
                        queueProduce(data);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private int packet_num = 0;
    ArrayBlockingQueue<byte[]> tmp_read;
    public void queueProduce(byte[] buffer){
        try {
            Log.d(TAG,"put a packet buffer");
            if(packet_num == 0){
                tmp_read = new ArrayBlockingQueue<byte[]>(capacity);
            }
            tmp_read.put(buffer);
            Log.d(TAG,"put a packet buffer finish");
            packet_num++;
            if(packet_num > 2000){
                fully_queue.put(tmp_read);
                packet_num = 0;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    ArrayBlockingQueue<byte[]> tmp_input = null;
    public byte[] queueConsumer(){
        try {
            Log.d(TAG,"read a packet buffer");
            if(tmp_input == null || tmp_input.isEmpty()){
                tmp_input = fully_queue.take();
                Log.d(TAG,"creat a packet buffer queue");
            }
            if(!tmp_input.isEmpty()){
                Log.d(TAG,"poll a packet buffer");
                return tmp_input.poll();
            }
            Log.d(TAG,"tmp_input is null");
//            return queue.take();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
