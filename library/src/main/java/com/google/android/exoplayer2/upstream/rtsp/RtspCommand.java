package com.google.android.exoplayer2.upstream.rtsp;

/**
 * Created by Young on 17/3/17.
 */

public class RtspCommand {

    public static StringBuilder getOptionsCommand(String address,long Cseq) {
        StringBuilder sb = new StringBuilder();
        sb.append("OPTIONS "+address+" RTSP/1.0\r\n");
        sb.append("CSeq: "+Cseq+"\r\n");
        sb.append("User-Agent: CTC RTSP 1.0\r\n");
        sb.append("\r\n");
        return sb;
    }

    public static StringBuilder getDescribeCommand(String address,long Cseq) {
        StringBuilder sb = new StringBuilder();
        sb.append("DESCRIBE "+address+" RTSP/1.0\r\n");
        sb.append("CSeq: "+Cseq+"\r\n");
        sb.append("Accept: application/sdp \r\n");
        sb.append("User-Agent: CTC RTSP 1.0\r\n");
        sb.append("\r\n");
        return sb;
    }

    public static StringBuilder getSetupCommand(String address,long Cseq,String ip,int port) {
        StringBuilder sb = new StringBuilder();
        sb.append("SETUP "+address+" RTSP/1.0\r\n");
        sb.append("CSeq: "+Cseq+"\r\n");
        sb.append("Transport: " +
                "MP2T/RTP/UDP;unicast;destination="+ip+";client_port="+port+"," +
                "MP2T/UDP;unicast;destination="+ip+";client_port="+port+","+
                "MP2T/TCP;unicast;destination="+ip+";interleaved=0-1;mode=PLAY," +
                "MP2T/RTP/TCP;unicast;destination="+ip+";interleaved=0-1;mode=PLAY" +
                "\r\n");
        sb.append("x-NAT:"+ip+":"+port+"\r\n");
        sb.append("User-Agent: CTC RTSP 1.0\r\n");
        sb.append("\r\n");
        return sb;
    }

    public static StringBuilder getPlayCommand(String address,long Cseq,String sessionid,long scale,long mBeginTime) {
        StringBuilder sb = new StringBuilder();
        sb.append("PLAY "+address+" RTSP/1.0\r\n");
        sb.append("CSeq: "+Cseq+"\r\n");
        sb.append("Session: "+sessionid+"\r\n");
        sb.append("Scale: "+scale+"\r\n");
        //sb.append("Range: clock=now- \r\n");
        sb.append("Range: npt=end- \r\n");
        sb.append("User-Agent: CTC RTSP 1.0\r\n");
        sb.append("\r\n");
        return sb;
    }

    public static StringBuilder getPauseCommand(String address,long Cseq,long sessionid) {
        StringBuilder sb = new StringBuilder();
        sb.append("PAUSE "+address+" RTSP/1.0 \r\n");
        sb.append("CSeq: "+Cseq+"\r\n");
        sb.append("Session: "+sessionid+"\r\n");
        sb.append("User-Agent: CTC RTSP 1.0\r\n");
        sb.append("\r\n");
        return sb;
    }

    public static StringBuilder getStopCommand(String address,long Cseq,long sessionid) {
        StringBuilder sb = new StringBuilder();
        sb.append("TEARDOWN "+address+" RTSP/1.0 \r\n");
        sb.append("CSeq: "+Cseq+"\r\n");
        sb.append("Session: "+sessionid+"\r\n");
        sb.append("User-Agent: CTC RTSP 1.0\r\n");
        sb.append("\r\n");
        return sb;
    }

    public static StringBuilder getGetParameterCommand(String address,long Cseq,long sessionid) {
        StringBuilder sb = new StringBuilder();
        sb.append("GET_PARAMETER "+address+" RTSP/1.0 \r\n");
        sb.append("CSeq: "+Cseq+"\r\n");
        sb.append("Session: "+sessionid+"\r\n");
        sb.append("User-Agent: CTC RTSP 1.0\r\n");
        sb.append("x-Timeshift_Range\r\n");
        sb.append("x-Timeshift_Current\r\n");
        sb.append("\r\n");
        return sb;
    }
}
