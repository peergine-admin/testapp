package com.peergine.www.connectListener;

import android.util.Log;

import com.peergine.connect.android.pgJniConnect;
/**
 * Created by ctkj006 on 2017/7/1.
 */

public class SessClass {
    public int iSessID;
    public int iSendCount;
    public int iRecvCount;
    public int iSpeedBytes;
    public long lSpeedStamp;

    public static final int PG_API_READ_MAX_SIZE = (64 * 1024);

    public  SessClass(){

        iSessID = 0;
        iSendCount = 0;
        iRecvCount = 0;
        iSpeedBytes = 0;
        lSpeedStamp = 0;
    }
//	public native static int jniConnected(int iInstID, int iSessID);

    public  long getTime() {
        long t_lTime = System.currentTimeMillis();
        return t_lTime;  //获取系统时间的10位的时间戳
    }

    public void cleanSees(){

        iSessID = 0;
        iSendCount = 0;
        iRecvCount = 0;
        iSpeedBytes = 0 ;
        lSpeedStamp = 0;
    }

    public void addOneSess(
            int t_iSessID){

        iSessID = t_iSessID;
        iSendCount = 0 ;
        iRecvCount = 0;
        iSpeedBytes = 0;
        lSpeedStamp = getTime();
    }

    public int sessSend(int t_iInstID,byte []t_data ,int t_iPrio){

        int iRet =  pgJniConnect.jniWrite(t_iInstID,iSessID,t_data,t_iPrio);
        return iRet;
    }

}
