package com.peergine.www.connectclient;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.res.AssetManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;

import com.peergine.connect.android.pgJniConnect;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static com.peergine.www.connectclient.SessClass.PG_API_READ_MAX_SIZE;

public class MainActivity extends AppCompatActivity {

    public static final int Const_OneMillion = 1048576;
    public static final int Const_OneThousand = 1024;

    public static final int  P2P_Status_NULL =  1 ;
    public static final int  P2P_Status_LoginFail =  P2P_Status_NULL + 1 ;
    public static final int  P2P_Status_Logout = P2P_Status_LoginFail + 1;
    public static final int  P2P_Status_Login = P2P_Status_Logout + 1;
    public static final int  P2P_Status_Close = P2P_Status_Login + 1;
    public static final int  P2P_Status_Offline = P2P_Status_Close + 1;
    public static final int  P2P_Status_Connecting = P2P_Status_Offline + 1;
    public static final int  P2P_Status_ConListener = P2P_Status_Connecting + 1;
    public static final int  P2P_Status_ConP2PServer = P2P_Status_ConListener + 1;
    public static final int  P2P_Status_Speed = P2P_Status_ConP2PServer + 1;
    public static final int  P2P_Status_Stop_Test_Speed = P2P_Status_Speed + 1;
    public static final int  P2P_Status_Connect_Fail = P2P_Status_Stop_Test_Speed + 1;

    public static final int MAX_Ind_Count = 1;
    public static final int Const_MAX_P2PTryTime_NoRelay = 3600;
    private static final String Const_Config_File_Name  = "tstConfig.txt";
    private static final int  Const_MAX_Sess_Count = 16;


    private android.widget.EditText m_editLisDevID;

    private android.widget.TextView m_TryConIntInfoView;
    private android.widget.TextView m_TransInfoView;
    private android.widget.TextView m_ConP2PStatusView;
    private android.widget.TextView m_LoginP2PStatusView;

    private android.widget.Button m_tryIntBtn;
    private android.widget.Button m_LoginBtn;
    private android.widget.Button m_LogoutBtn;
    private android.widget.Button m_StartConListenBtn;
    private android.widget.Button m_StopConP2PBtn;
    private android.widget.Button m_StopTestSpeedBtn;
    private android.widget.Button m_NeedTestSpeedBtn;
    private android.widget.Button m_btnNeedPassiveTestSpeed;


    private android.widget.CheckBox m_CheckBoxNeedRelay;

    public pgJniConnect.OutEvent m_eventOut;
    private boolean m_bLoopThread ;
    private boolean m_bNeedLoopEvent;
    private boolean m_bNeedActiveSend ;
    private boolean m_bNeedRelay ;
    private boolean m_bNeedConSer = false;
    private boolean m_bNeedConLis = false;

    private int m_InstID;
    private int m_iCurSessID ;

    private  int m_Status = P2P_Status_NULL;

    private boolean m_bPassConInterTest ;
    private SessClass []m_SessList;

    private  String m_sEditLisDevID;
    private  String m_sTestIntAddr ;
    private String m_sP2PServerAddr;
    private ThreadPoolExecutor singleThreadPool = null;

    void createThreadPool(){

        singleThreadPool = new ThreadPoolExecutor(1, 1,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<Runnable>(1024), null, new ThreadPoolExecutor.AbortPolicy());
    }


    int searchSessInSessList(int t_iSessID){

        for(int i = 0 ; i < Const_MAX_Sess_Count; i++){

            if( m_SessList[i].iSessID == t_iSessID )
            {
                return i ;
            }
        }
        return  Const_MAX_Sess_Count;
    }


    void addOneSessInSessList(
            int t_iSessID){

        for(int i = 0 ; i < Const_MAX_Sess_Count; i++){

            if(m_SessList[i].iSessID == 0){
                 m_SessList[i].addOneSess(t_iSessID);
            }
        }
    }

    void sessSend(boolean iNeedLoopSend,int t_iSessID, byte[] t_byte  , int t_iPrio){

        if(searchSessInSessList(t_iSessID) >= Const_MAX_Sess_Count ){
            return;
        }

        Log.d("aaaaaaaaaaaa_in ","sessSend\n");

        int iRet = 0;

        if (iNeedLoopSend == true) {
            while(m_bNeedActiveSend == true) {
                iRet = pgJniConnect.jniWrite(m_InstID, t_iSessID, t_byte, t_iPrio);
                if (iRet < pgJniConnect.PG_ERROR_OK) {
                    break;
                }
            }
        }
        else{
            iRet = pgJniConnect.jniWrite(m_InstID, t_iSessID, t_byte, t_iPrio);
            if (iRet < pgJniConnect.PG_ERROR_OK) {
                return;
            }
        }
    }

    boolean sendStopTestSpeedMsg() {
        //send speedInfo
        String sMsg = "stopTestSpeed";
        byte t_byte[] = sMsg.getBytes();
        sessSend( false , m_eventOut.iSessNow ,t_byte ,pgJniConnect.PG_PRIORITY_1);
        return true;
    }

    boolean sendListenerNeedTestSpeedMsg() {
        //send speedInfo
        String sMsg = "ListenerNeedTestSpeed";
        byte t_byte[] = sMsg.getBytes();
        sessSend( false , m_eventOut.iSessNow ,t_byte ,pgJniConnect.PG_PRIORITY_1);
        return true;
    }

    void sessRecv(int t_iSessID){
        int iud = 0;
        iud =  searchSessInSessList(t_iSessID);
        if( iud >= Const_MAX_Sess_Count ){
            return;
        }
        Log.v("sessRecv_start ","\n");
        String t_sStr = "";
        pgJniConnect.OutRead out = new pgJniConnect.OutRead();
        int iRet = pgJniConnect.jniRead(m_InstID , t_iSessID , PG_API_READ_MAX_SIZE  , out);
        if(iRet < pgJniConnect.PG_ERROR_OK){
            Log.v("sessRecv_return in","jniRead/n");
            return ;
        }

        Log.v("out.iPrio= ", String.valueOf(out.iPrio));
        // IF  PG_PRIORITY_2 , testData
        if(out.iPrio == pgJniConnect.PG_PRIORITY_2){

            Log.v("PG_PRIORITY_2 in","/n");
            m_SessList[iud].iSpeedBytes += iRet;
            long lCurTime = m_SessList[iud].getTime();
            int iReadTime = (int) ( (lCurTime - m_SessList[iud].lSpeedStamp) );
            Log.v("iReadTime=",String.valueOf(iReadTime));
            if(iReadTime <= 0){
                m_SessList[iud].lSpeedStamp = lCurTime;
            }

            if( iReadTime < 1000 ) {
                Log.v("iSpeedBytes=",String.valueOf(m_SessList[iud].iSpeedBytes));
                return;
            }

            double dSpeed_byte = m_SessList[iud].iSpeedBytes / (iReadTime / 1000);
            double dSpeed_bite = dSpeed_byte * 8 ;
            float fResultByteSpeed = 0 ;
            float fResultBiteSpeed = 0 ;
            String sNeesSendStr = "";

            if (dSpeed_byte > Const_OneMillion) {
                fResultByteSpeed = (float) (dSpeed_byte / (Const_OneMillion));
                t_sStr = t_sStr + String.format("监听端发送速度： %.1f M字节每秒",fResultByteSpeed);
                sNeesSendStr = sNeesSendStr + String.format("%.1f M",fResultByteSpeed);
            }
            else if (dSpeed_byte > Const_OneThousand) {
                fResultByteSpeed = (float) (dSpeed_byte / (Const_OneThousand));
                t_sStr = t_sStr + String.format("监听端发送速度： %.1f K字节每秒",fResultByteSpeed);
                sNeesSendStr = sNeesSendStr + String.format("%.1f K",fResultByteSpeed);
            }
            else {
                t_sStr = t_sStr + String.format("监听端发送速度： %d 字节每秒",dSpeed_byte);
                sNeesSendStr = sNeesSendStr + String.format("%d ",dSpeed_byte);
            }


            if (dSpeed_bite > Const_OneMillion) {
                fResultBiteSpeed =  (float) (dSpeed_bite / (Const_OneMillion));
                t_sStr = t_sStr +"  "+ String.format("%.1f M比特每秒",fResultBiteSpeed);
                sNeesSendStr = sNeesSendStr +";"+ String.format("%.1f M",fResultBiteSpeed);
            }
            else if (dSpeed_bite > Const_OneThousand) {
                fResultBiteSpeed =  (float) (dSpeed_bite / (Const_OneThousand));
                t_sStr = t_sStr +"  "+ String.format("%.1f K比特每秒",fResultBiteSpeed);
                sNeesSendStr = sNeesSendStr +";"+ String.format("%.1f K",fResultBiteSpeed);
            }
            else {
                t_sStr =  t_sStr +"  "+ String.format("%d  比特每秒",dSpeed_bite);
                sNeesSendStr = sNeesSendStr +";"+ String.format("%d ",dSpeed_bite);
            }

            m_handler.obtainMessage(P2P_Status_Speed, t_sStr).sendToTarget();
            m_SessList[iud].iSpeedBytes = 0;
            m_SessList[iud].lSpeedStamp = m_SessList[iud].getTime();

            byte t_byte[] = sNeesSendStr.getBytes();
            sessSend( false , t_iSessID ,t_byte ,pgJniConnect.PG_PRIORITY_1);

        }
        //IF  PG_PRIORITY_1 , msg
        else if(out.iPrio == pgJniConnect.PG_PRIORITY_1){
            Log.v("PG_PRIORITY_1 in","/n");
            String t_sStrRecv = new String(out.byBuf);

            if("stopTestSpeed".equals(t_sStrRecv)) {
                m_handler.obtainMessage(P2P_Status_Stop_Test_Speed, "").sendToTarget();
                m_bNeedActiveSend = false;
                return ;
            }

            if("ListenerStartTestGetRedy".equals(t_sStrRecv)) {
                //m_bNeedActiveSend = false;
                return ;
            }

            int maxSplit = 2;
            String sByteStr = "";
            String sBiteStr = "";
            String showStr = "";

            String[] t_sParaArray  = t_sStrRecv.split(";",maxSplit);
            sByteStr = t_sParaArray[0] + "字节每秒";
            sBiteStr = t_sParaArray[1] + "比特每秒";
            showStr ="客户端发送速度: " + sByteStr + "  " + sBiteStr;
            m_handler.obtainMessage(P2P_Status_Speed, showStr).sendToTarget();
        }
    }

    Boolean writeConfigFile(){
        String t_sWriteStr = "https://www.baidu.com/,,connect.peergine.com:7781";
        try {
            FileOutputStream fops = openFileOutput(Const_Config_File_Name, (Context.MODE_WORLD_READABLE + Context.MODE_WORLD_WRITEABLE ) );
            fops.write(t_sWriteStr.getBytes());
            fops.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return true;
    }

    String readConfigFile(){

        String sReadString = "";
        AssetManager assetMng = getAssets();
        try {
            InputStream in = assetMng.open(Const_Config_File_Name);
     //       //FileInputStream fis = openFileInput(Const_Config_File_Name);
            ByteArrayOutputStream t_byteStream = new ByteArrayOutputStream();

            byte[] buffer=new byte[50];
            int len = -1 ;
            while( (  len = in.read(buffer) ) != -1  ){
                t_byteStream.write(buffer,0,len);
            }

            sReadString =  t_byteStream.toString();
            in.close();
            t_byteStream.close();
            return  sReadString;
        } catch (FileNotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        return "";
    }

    boolean testConInternet() {

        String t_sReadStr = readConfigFile();
        if(t_sReadStr == "" || t_sReadStr == null ){
            m_TryConIntInfoView.setText("没有读取到配置文件");
            return false;
        }

        int maxSplit = 3;
        String []t_sParaArray  = t_sReadStr.split(",",maxSplit);

        for(int i = 0 ; i < t_sParaArray.length ; i++){
            if(i == 0){
                m_sTestIntAddr = t_sParaArray[i];
            }else if(i == 1) {
            }else if(i == 2){
                m_sP2PServerAddr = t_sParaArray[i];
            }
        }


        if("".equals(m_sTestIntAddr) ||  m_sTestIntAddr == null){
            m_TryConIntInfoView.setText("未读到测试网站的内容");
            return false;
        }

        Runnable t_thread = new Runnable() {
            @Override
            public void run() {
                HttpURLConnection connection = null;
                int iRetCode = 0;
                try {
                    URL url = new URL(m_sTestIntAddr);
                    connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("GET");
                    connection.setConnectTimeout(8000);
                    connection.setReadTimeout(8000);


                    iRetCode = connection.getResponseCode();
                    int t = 0;
                    if (iRetCode == 200) {
                        testIntHander.obtainMessage(1,"").sendToTarget();
                        m_bPassConInterTest = true;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    if (iRetCode != 200) {
                        testIntHander.obtainMessage(2, "").sendToTarget();
                        m_bPassConInterTest = false;
                    }
                } finally {
                    if (connection != null) {
                        connection.disconnect();
                    }
                }
            }
        };
        singleThreadPool.execute(t_thread);


        m_TryConIntInfoView.setText("尝试联网中,请稍等");
        return true;

    }

    boolean stopConOthers(){

        stopTestSpeed();

        m_StartConListenBtn.setClickable(true);
        m_StartConListenBtn.setEnabled(true);
        m_StartConListenBtn.setFocusable(true);

        m_TransInfoView.setText("传输速度：未测试");
        m_ConP2PStatusView.setText("连接状态：未连接");

        m_bNeedLoopEvent  = false;
        m_Status = P2P_Status_Close ;
        m_bNeedConLis = false;

        return false;
    }

    boolean stopConSer(){

        stopTestSpeed();

        m_LoginBtn.setClickable(true);
        m_LoginBtn.setEnabled(true);
        m_LoginBtn.setFocusable(true);
        m_StartConListenBtn.setClickable(true);
        m_StartConListenBtn.setEnabled(true);
        m_StartConListenBtn.setFocusable(true);

        m_CheckBoxNeedRelay.setEnabled(true);
        m_CheckBoxNeedRelay.setClickable(true);

        m_LoginP2PStatusView.setText("登录状态：未登录");
        m_ConP2PStatusView.setText("连接状态：未连接");
        m_TransInfoView.setText("传输速度：未测试");

        m_bLoopThread = false;
        m_bNeedLoopEvent  = false;
        m_Status = P2P_Status_NULL;

        return true;

    }
    
    public void delay(int ms){
        try {
            Thread.currentThread();
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void eventLoop() {


        int itryLoginTimes = 0;

        while (m_bNeedLoopEvent && (m_InstID > 0) ) {

            int iErr =  pgJniConnect.jniEvent( m_InstID , 500  , m_eventOut);
            if(iErr !=  pgJniConnect.PG_ERROR_OK ){
                if(iErr != pgJniConnect.PG_ERROR_TIMEOUT){
                        Log.d( "enter eventLoop"," iErr = "  + iErr);
                }
                else{
                    if(itryLoginTimes < 500 ){
                        if( m_Status < P2P_Status_Login) {
                            itryLoginTimes++;
                        }
                    }else{
                        if( m_Status < P2P_Status_Login) {
                            m_handler.obtainMessage(P2P_Status_Logout, "").sendToTarget();
                        }
                    }
                }

                if(m_eventOut.iSessNow != 0)
                {
                    m_iCurSessID = m_eventOut.iSessNow;
                }

                if(m_Status == P2P_Status_Connecting){
                    if(m_iCurSessID != 0) {
                        pgJniConnect.jniClose(m_InstID, m_iCurSessID);
                    }
                    if (pgJniConnect.jniOpen(m_InstID, m_sEditLisDevID) == pgJniConnect.PG_ERROR_OK) {
                        m_iCurSessID = m_eventOut.iSessNow;
                    }
                    continue;
                }

                if( (m_iCurSessID != 0) && ( m_bNeedActiveSend == true ) ){
                    byte []t_bytes = new byte[10 * 1024];
                    sessSend(true ,m_iCurSessID , t_bytes ,pgJniConnect.PG_PRIORITY_2);
                    m_iCurSessID = 0;
                }

                continue;
            }else{
                if(m_eventOut.iSessNow != 0){
                    Log.d( "Receive Ok ","m_eventOut.iSessNow != 0" );
                    m_iCurSessID = m_eventOut.iSessNow;
                }

            }

            String t_Str = "";
            if( m_eventOut.iEventNow == pgJniConnect.PG_EVENT_SVR_LOGIN ){
                m_Status = P2P_Status_Login;
                m_handler.obtainMessage(P2P_Status_Login, t_Str).sendToTarget();
            }
            else if( m_eventOut.iEventNow == pgJniConnect.PG_EVENT_CONNECT ){
                m_Status = P2P_Status_ConListener;
                m_handler.obtainMessage(P2P_Status_ConListener, t_Str).sendToTarget();
                addOneSessInSessList(m_eventOut.iSessNow);

                if(m_bNeedActiveSend == true){
                    byte []t_bytes = new byte[10 * 1024];
                    sessSend(true ,m_eventOut.iSessNow, t_bytes ,pgJniConnect.PG_PRIORITY_2);
                }
            }
            else if( m_eventOut.iEventNow == pgJniConnect.PG_EVENT_CLOSE ){
                m_Status = P2P_Status_Close;
                if(m_eventOut.iSessNow != 0) {
                    pgJniConnect.jniClose(m_InstID, m_eventOut.iSessNow);
                }
                m_handler.obtainMessage(P2P_Status_Close, t_Str).sendToTarget();
            }
            else if( m_eventOut.iEventNow == pgJniConnect.PG_EVENT_OFFLINE ){
                m_Status = P2P_Status_Offline;
                m_handler.obtainMessage(P2P_Status_Offline, t_Str).sendToTarget();
            }
            else if( m_eventOut.iEventNow == pgJniConnect.PG_EVENT_SVR_LOGOUT ){
                m_Status = P2P_Status_Logout;
                m_handler.obtainMessage(P2P_Status_Logout, t_Str).sendToTarget();

            }
            else if( m_eventOut.iEventNow == pgJniConnect.PG_EVENT_WRITE ){
                Log.d("log_PG_EVENT_WRITE","Receive Event\n");

                if(m_bNeedActiveSend == true) {
                    byte[] t_byte = new byte[10 * 1024];
                    sessSend(true, m_eventOut.iSessNow, t_byte, pgJniConnect.PG_PRIORITY_2);
                }
            }
            else if( m_eventOut.iEventNow == pgJniConnect.PG_EVENT_READ ){
                Log.d("log_PG_EVENT_READ","Receive Event\n");
                sessRecv(m_eventOut.iSessNow);
            }

            else if( m_eventOut.iEventNow == pgJniConnect.PG_ERROR_TIMEOUT ){
                t_Str = "操作超时;可能原因有：\n" +
                        "1.检查网络连接断开（请重新进行联网测试）；" +
                        "2.服务器地址错误；" +
                        "3.本地ID输入错误;" +
                        "4.服务器端口被占用；" +
                        "5.防火墙因素\n"+
                        "6.手动注销";
                m_handler.obtainMessage(P2P_Status_LoginFail, t_Str).sendToTarget();
            }
            else if( m_eventOut.iEventNow == pgJniConnect.PG_ERROR_BUSY ){
                t_Str = "系统正忙";
                m_handler.obtainMessage(P2P_Status_LoginFail, t_Str).sendToTarget();
            }
            else if( m_eventOut.iEventNow == pgJniConnect.PG_ERROR_NOLOGIN ){
                t_Str = "还没有登录到P2P服务器。可能原因有：" +
                        "1.检查网络连接断开（请重新进行联网测试）；" +
                        "2.服务器地址错误；" +
                        "3.本地ID输入错误;" +
                        "4.服务器端口被占用；" +
                        "5.防火墙因素\n"+
                        "6.手动注销";

                m_handler.obtainMessage(P2P_Status_LoginFail, t_Str).sendToTarget();
            }
            else if( m_eventOut.iEventNow == pgJniConnect.PG_EVENT_SVR_KICK_OUT ){
                t_Str = "被服务器踢出，因为有另外一个相同ID的节点登录了";
                m_handler.obtainMessage(P2P_Status_LoginFail, t_Str).sendToTarget();
            }//
            else if( m_eventOut.iEventNow == pgJniConnect.PG_ERROR_BADPARAM ){
                t_Str = "登录参数错误";
                m_handler.obtainMessage(P2P_Status_LoginFail, t_Str).sendToTarget();
            }
            //P2P_Status_Connect_Fail
            else if( m_eventOut.iEventNow == pgJniConnect.PG_ERROR_MAXSESS ){
                t_Str =  "连接失败原因："+"会话数限制";
                m_handler.obtainMessage(P2P_Status_Connect_Fail, t_Str).sendToTarget();
            }
            else if( m_eventOut.iEventNow == pgJniConnect.PG_ERROR_NOCONNECT ){
                t_Str =  "会话还没有连接完成,请检查网络是否断开，监听端的设备ID是否正确";
                m_handler.obtainMessage(P2P_Status_Connect_Fail, t_Str).sendToTarget();
            }
            else if( m_eventOut.iEventNow == pgJniConnect.PG_ERROR_MAXINST ){
                t_Str =  "连接失败原因："+"实例数限制";
                m_handler.obtainMessage(P2P_Status_Connect_Fail, t_Str).sendToTarget();
            }
            else if( m_eventOut.iEventNow == pgJniConnect.PG_ERROR_SYSTEM ){
                t_Str =  "连接失败原因："+"系统错误";
                m_handler.obtainMessage(P2P_Status_Connect_Fail, t_Str).sendToTarget();
            }

        }
    }

    @SuppressLint("HandlerLeak")
    public Handler testIntHander = new Handler(){
        @Override
        public  void handleMessage(Message msg){
            if( msg.what == 1){
                String sShowString = "";
                if(m_sTestIntAddr != null &&  !m_sTestIntAddr.equals("") ) {
                    sShowString = "测试成功：成功连接到" + m_sTestIntAddr;
                    m_TryConIntInfoView.setText(sShowString);

                    if(m_bNeedConSer){
                        m_bNeedConSer = false;
                        loginSer();
                    }

                }
            }
            else if( msg.what == 2 ){
                String sShowString = "";
                if(m_sTestIntAddr != null && !m_sTestIntAddr.equals("") ) {
                    sShowString = "测试失败：无法连接到" + m_sTestIntAddr;
                    m_TryConIntInfoView.setText(sShowString);

                    if(m_bNeedConSer ){
                        m_LoginP2PStatusView.setText("连接互联网失败，请检查网络");
                    }
                    if( m_bNeedConLis){
                        m_ConP2PStatusView.setText("连接互联网失败，请检查网络");
                    }
                    m_bNeedConSer = false;
                    m_bNeedConLis = false;

                }
            }
        }
    };

    @SuppressLint("HandlerLeak")
    public Handler m_handler=new Handler()
    {
        @SuppressLint("SetTextI18n")
        @Override
        public void handleMessage(Message msg)
        {
            if(msg.what <= P2P_Status_NULL){
                return;
            }

            switch(msg.what)
            {
                case P2P_Status_Speed:
                    m_TransInfoView.setText((String)(msg.obj));
                    m_ConP2PStatusView.setText("已连接上监听端");
                    m_LoginP2PStatusView.setText("登录状态：已登录");
                    break;
                case P2P_Status_ConListener:
                    m_ConP2PStatusView.setText("已连接上监听端");
                    m_TransInfoView.setText("传输速度：未测试");
                    m_LoginP2PStatusView.setText("登录状态：已登录");
                    m_StartConListenBtn.setClickable(false);
                    m_StartConListenBtn.setEnabled(false);
                    m_StartConListenBtn.setFocusable(false);
                    break;
                case P2P_Status_Connecting:
                    m_LoginP2PStatusView.setText("登录状态：已登录");
                    m_ConP2PStatusView.setText("正在连接");
                    m_TransInfoView.setText("传输速度：未测试");
                    break;
                case P2P_Status_Login:
                    m_LoginP2PStatusView.setText("登录状态：已登录");
                    m_ConP2PStatusView.setText("连接状态：未连接");
                    m_TransInfoView.setText("传输速度：未测试");
                    m_LoginBtn.setClickable(false);
                    m_LoginBtn.setEnabled(false);
                    m_LoginBtn.setFocusable(false);

                    if(m_bNeedConLis){
                        m_bNeedConLis =false;
                        setListenerId_And_ConOther();
                    }
                    break;

                case P2P_Status_Logout:
                    m_LoginP2PStatusView.setText("未登录;可能的原因有：\n" +
                            "1.检查网络连接断开（请重新进行联网测试）；\n" +
                            "2.服务器地址错误；\n" +
                            "3.服务器端口被占用；\n" +
                            "4.防火墙因素;\n" +
                            "5.手动注销 \n");

                    m_ConP2PStatusView.setText("连接状态：未连接");
                    m_TransInfoView.setText("传输速度：未测试");

                    m_LoginBtn.setClickable(true);
                    m_LoginBtn.setEnabled(true);
                    m_LoginBtn.setFocusable(true);
                    m_StartConListenBtn.setClickable(true);
                    m_StartConListenBtn.setEnabled(true);
                    m_StartConListenBtn.setFocusable(true);
                    testConInternet();
                    break;
                case P2P_Status_Close:
                    m_ConP2PStatusView.setText("连接状态：未连接");
                    m_TransInfoView.setText("传输速度：未测试");
                    m_StartConListenBtn.setClickable(true);
                    m_StartConListenBtn.setEnabled(true);
                    m_StartConListenBtn.setFocusable(true);
                    break;
                case P2P_Status_Offline:
                    m_LoginBtn.setClickable(false);
                    m_LoginBtn.setEnabled(false);
                    m_LoginBtn.setFocusable(false);
                    m_ConP2PStatusView.setText("连接失败原因可能有:\n" +
                            "1.对端的ID输入有误;\n" +
                            "2.对端未上线;\n" +
                            "3.网络断开(可以重测网络);");
                    m_TransInfoView.setText("传输速度：未测试");
                    break;
                case P2P_Status_Stop_Test_Speed:
                    m_TransInfoView.setText("传输速度：未测试");
                    break;
                case P2P_Status_LoginFail:
                    m_LoginP2PStatusView.setText((String)(msg.obj));
                    m_ConP2PStatusView.setText("连接状态：未连接");
                    m_TransInfoView.setText("传输速度：未测试");
                    break;
                case P2P_Status_Connect_Fail:
                    m_ConP2PStatusView.setText((String)(msg.obj));
                    break;
                default:
                    break;
            }
            //        super.handleMessage(msg);
        }
    };

    Runnable m_runnerble = new Runnable() {
        @Override
        public void run() {

            while (m_bLoopThread) {

                String sSvrAddr = m_sP2PServerAddr ;//后面再加上了读取配置文件的操作
                String sSvrAddrBack = "";
                String sRelayList  = "";
                int iBufSize0 = 0;
                int iBufSize1 = 0;
                int iBufSize2 = 0;
                int iBufSize3 = 0;
                int iSessTimeout = 0;
                int iTryP2PTimeout = 0;
                int iForwardSpeed = 0;
                int iForwardUse = 0;

                if(m_bNeedRelay == true){
                    sRelayList = "";
                    iTryP2PTimeout = Const_MAX_P2PTryTime_NoRelay + 1;
                }

                 m_bNeedLoopEvent = false;

//                int iInstID = pgJniConnect.jniInitialize(iMode, sUser, sPass, sSvrAddr,
//                        sSvrAddrBack, sRelayList, iBufSize0, iBufSize1, iBufSize2,
//                        iBufSize3, iSessTimeout, iTryP2PTimeout, iForwardSpeed, iForwardUse);
                m_InstID = pgJniConnect.jniInitialize(pgJniConnect.PG_MODE_CLIENT,
                        "ClientTest","",sSvrAddr,sSvrAddrBack,sRelayList,iBufSize0,iBufSize1,iBufSize2,iBufSize3,iSessTimeout,iTryP2PTimeout,iForwardSpeed,iForwardUse);
                if( m_InstID < 0 ) {
                    Log.d("jniInitialize failed","");
                    continue ;
                }

                m_bNeedLoopEvent = true;
                eventLoop();


                for(int i = 0 ; i < Const_MAX_Sess_Count;i++){
                    if(m_SessList[i].iSessID != 0){
                        pgJniConnect.jniClose(m_InstID,m_SessList[i].iSessID);

                    }
                    m_SessList[i].cleanSees();
                }

                m_bNeedLoopEvent = false;
                pgJniConnect.jniCleanup(m_InstID);

            }   //启动消息循环

        }
    };

    @SuppressLint("SetTextI18n")
    public boolean setListenerId_And_ConOther(){

        m_sEditLisDevID = m_editLisDevID.getText().toString();
        if("".equals(m_sEditLisDevID)){
            m_ConP2PStatusView.setText("监听端ID为空，请重新输入");
            return false ;
        }

        if( m_Status < P2P_Status_ConListener ){

            if(m_Status  <  P2P_Status_Connecting) {
                m_Status =  P2P_Status_Connecting;
                m_ConP2PStatusView.setText("正在连接");
            }
        }
        return false;
    }


     public boolean conP2PListener(){

        m_sEditLisDevID  =  m_editLisDevID.getText().toString();

        //if( m_sEditLisDevID == "" )1

         if(!m_bPassConInterTest) {
             m_ConP2PStatusView.setText("连接互联网测试不通过 ，请检查网络连接");
             return false;
         }

         if(m_Status < P2P_Status_Login){
             loginSer();
         }else if( m_Status < P2P_Status_ConListener){
             setListenerId_And_ConOther();
         }



        return true;
    }

    boolean stopTestSpeed(){
        m_bNeedActiveSend = false;
        m_TransInfoView.setText("传输速度：未测试");
        sendStopTestSpeedMsg();
        return true;
    }

    boolean testSpeed(){
        m_bNeedActiveSend = true;
        return true;
    }

    void prepareForTestSpeed(){
        if(!m_bPassConInterTest) {
            m_bNeedConSer = true;
            m_bNeedConLis = true;
            testConInternet();
        }
        else {
            m_bNeedConLis = true;
            conP2PListener();
        }
    }

    private android.view.View.OnClickListener m_OnClickListener = new android.view.View.OnClickListener() {
        @Override
        public void onClick(View view) {
            switch (view.getId()){
                case R.id.btnTestConInternet:
                    testConInternet();
                    break;

                case R.id.btnStopConnectID:
                    stopConOthers();
                    break;

                case R.id.btnLoginID:
                    if( !m_bPassConInterTest ) {
                        m_bNeedConSer = true;
                        testConInternet();
                    }else{
                        loginSer();
                    }
                    break;

                case R.id.btnLogoutID:
                    stopConSer();
                    break;
                case R.id.btnConP2PListenerID:

                    if(!m_bPassConInterTest) {
                        m_bNeedConSer = true;
                        m_bNeedConLis = true;
                        testConInternet();
                    }
                    else {
                        m_bNeedConLis = true;
                        conP2PListener();
                    }
                    break;
                case R.id.btnStopTestSpeedID:
                    stopTestSpeed();
                    break;
                case R.id.btnNeedTestSpeed:
                    prepareForTestSpeed();
                    stopTestSpeed();
                    m_TransInfoView.setText("传输速度：正在测试，请稍候");
                    testSpeed();
                    break;
                case R.id.btnNeedPassiveTestSpeed:
                    prepareForTestSpeed();
                    stopTestSpeed();
                    m_TransInfoView.setText("传输速度：正在测试，请稍候");
                    sendListenerNeedTestSpeedMsg();
                    break;
//                case R.id.btnTestBothSpeed:
//                    prepareForTestSpeed();
//                    stopTestSpeed();
//                    m_TransInfoView.setText("传输速度：正在测试，请稍候");
//                    testSpeed();
//                    sendListenerNeedTestSpeedMsg();
//                    break;

                default:
                    break;
            }
        }
    };

    private void loginSer() {

        m_bNeedRelay = m_CheckBoxNeedRelay.isChecked();
        m_CheckBoxNeedRelay.setEnabled(false);
        m_CheckBoxNeedRelay.setClickable(false);

        if(m_Status < P2P_Status_Login) {
            if(!m_bLoopThread) {
                m_bLoopThread = true;
                singleThreadPool.execute(m_runnerble);
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //设置竖屏， 在xml设置不管用，所以在这里添加设置
        if(getRequestedOrientation()!= ActivityInfo.SCREEN_ORIENTATION_PORTRAIT){
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }

        setContentView(R.layout.activity_main);

        createThreadPool();

        m_editLisDevID = (android.widget.EditText)findViewById(R.id.ediP2PListenerID);

        m_TryConIntInfoView = (android.widget.TextView)findViewById(R.id.tryInterResultTextID);
        m_ConP2PStatusView = (android.widget.TextView)findViewById(R.id.conP2PInfoTextID);
        m_LoginP2PStatusView = (android.widget.TextView)findViewById(R.id.LoginP2PInfoTextID);
        m_TransInfoView = (android.widget.TextView)findViewById(R.id.speedInfoTextID);

        m_tryIntBtn = (android.widget.Button)findViewById(R.id.btnTestConInternet);
        m_LoginBtn = (android.widget.Button)findViewById(R.id.btnLoginID);
        m_LogoutBtn = (android.widget.Button)findViewById(R.id.btnLogoutID);
        m_StartConListenBtn = (android.widget.Button)findViewById(R.id.btnConP2PListenerID);
        m_StopConP2PBtn = (android.widget.Button)findViewById(R.id.btnStopConnectID);
        m_StopTestSpeedBtn = (android.widget.Button)findViewById(R.id.btnStopTestSpeedID);
        m_NeedTestSpeedBtn = (android.widget.Button)findViewById(R.id.btnNeedTestSpeed);
        m_btnNeedPassiveTestSpeed = (android.widget.Button)findViewById(R.id.btnNeedPassiveTestSpeed);
//        m_btnTestBothSpeed = (android.widget.Button)findViewById(R.id.btnTestBothSpeed);

        m_CheckBoxNeedRelay = (android.widget.CheckBox)findViewById(R.id.needRelayCheckBoxID);

        m_tryIntBtn.setOnClickListener(m_OnClickListener);
        m_StartConListenBtn.setOnClickListener(m_OnClickListener);
        m_StopConP2PBtn.setOnClickListener(m_OnClickListener);
        m_StopTestSpeedBtn.setOnClickListener(m_OnClickListener);
        m_NeedTestSpeedBtn.setOnClickListener(m_OnClickListener);
        m_LoginBtn.setOnClickListener(m_OnClickListener);
        m_LogoutBtn.setOnClickListener(m_OnClickListener);
        m_btnNeedPassiveTestSpeed.setOnClickListener(m_OnClickListener);
//        m_btnTestBothSpeed.setOnClickListener(m_OnClickListener);

        //初始化一些需要传递的变量
        m_bPassConInterTest = false ;
        m_bLoopThread = false;
        m_bNeedLoopEvent = false;
        m_bNeedActiveSend  = false;
        m_iCurSessID = 0;

        m_SessList = new SessClass[Const_MAX_Sess_Count];

        for(int iud = 0; iud < Const_MAX_Sess_Count; iud ++ ){
            m_SessList[iud] = new SessClass();
        }

        m_eventOut = new pgJniConnect.OutEvent();
    }
}
