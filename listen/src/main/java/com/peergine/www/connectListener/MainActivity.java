package com.peergine.www.connectListener;
/*
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }
}
*/

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.res.AssetManager;
import android.icu.util.Output;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;

import com.peergine.connect.android.pgJniConnect;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.Buffer;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static com.peergine.www.connectListener.SessClass.PG_API_READ_MAX_SIZE;


//#define ONE_MILLION  1048576//(1024 * 1024)
//        #define ONE_THOUSAND  1024

public class MainActivity extends AppCompatActivity {

    public static final int MAX_Ind_Count = 1;
    public static final int Const_MAX_P2PTryTime_NoRelay = 3600;
    public static final int Const_OneMillion = 1048576;
    public static final int Const_OneThousand = 1024;

    static final int  P2P_Status_NULL =  1 ;
    static final int  P2P_Status_LoginFail =  P2P_Status_NULL + 1 ;
    static final int  P2P_Status_Logout = P2P_Status_LoginFail + 1;
    static final int  P2P_Status_Login = P2P_Status_Logout + 1;
    static final int  P2P_Status_Connecting = P2P_Status_Login + 1;
    static final int  P2P_Status_Close = P2P_Status_Connecting + 1;
    static final int  P2P_Status_Offline = P2P_Status_Close + 1;
    static final int  P2P_Status_ConListener = P2P_Status_Offline + 1;
    static final int  P2P_Status_ConP2Client = P2P_Status_ConListener + 1;
    static final int  P2P_Status_Speed = P2P_Status_ConP2Client + 1;
    static final int  P2P_Status_Stop_Test_Speed = P2P_Status_Speed + 1;

    static final int  Const_MAX_Sess_Count = 16;

    private final static String PG_METH_PING_REQ = "PREQ";
    private final static String PG_METH_PING_RES = "PRES";

    private static final String Const_Config_File_Name  = "tstConfig.txt";

    private android.widget.EditText m_editSelfDevID;

    private android.widget.TextView m_TryConIntInfoView;
    private android.widget.TextView m_TransInfoView;
    private android.widget.TextView m_ConP2PStatusView;
    private android.widget.TextView m_LoginP2PStatusView;

    private android.widget.TextView m_debugInfoView;

    private android.widget.Button m_tryIntBtn;
    private android.widget.Button m_StartConP2PServerBtn;
    private android.widget.Button m_StopConP2PBtn;
    private android.widget.Button m_StopTestSpeedBtn;

    private android.widget.CheckBox m_CheckBoxNeedRelay;

    public pgJniConnect.OutEvent m_eventOut;
    private boolean m_loopThread ;
    private boolean m_needLoopEvent;
    private boolean m_bNeedRelay ;
    private boolean m_bNeedCon = false;

    private int m_InstID;
    private boolean m_bNeedSend = false;
    private String m_sTestIntAddr ;
    private String m_sP2PServerAddr;
    private String m_sP2PRelayAddr;

    private  int m_Status = P2P_Status_NULL;


    private SessClass []m_SessList;
    private String m_sDevID;
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

        for(int i = 0 ; i < m_SessList.length; i++) {

            if (m_SessList[i].iSessID == 0) {
                m_SessList[i].addOneSess(t_iSessID);
                break;
            }
        }
    }


    void sessSend(boolean iNeedLoopSend,int t_iSessID, byte[] t_byte  , int t_iPrio){

        if(searchSessInSessList(t_iSessID) >= Const_MAX_Sess_Count ){
            return;
        }

        Log.d("aaaaaaaaaaaa_in ","sessSend\n");

        if (iNeedLoopSend == true) {


            while(m_bNeedSend) {
                int iRet = pgJniConnect.jniWrite(m_InstID, t_iSessID, t_byte, t_iPrio);
                if (iRet < pgJniConnect.PG_ERROR_OK) {
                    break;
                }
            }
        }
        else{


            int iRet = pgJniConnect.jniWrite(m_InstID, t_iSessID, t_byte, t_iPrio);
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

    boolean sendStartTestSpeedRedyMsg() {
        //send speedInfo
        String sMsg = "ListenerStartTestGetRedy";
        byte t_byte[] = sMsg.getBytes();
        sessSend( false , m_eventOut.iSessNow ,t_byte ,pgJniConnect.PG_PRIORITY_1);
        return true;
    }

    void sessRecv(int t_iSessID){
        int iud = 0;
        String t_sStr = "";
        iud =  searchSessInSessList(t_iSessID);
        if( iud >= Const_MAX_Sess_Count ){
            return;
        }

        Log.d("in ","sessRecv\n");

        pgJniConnect.OutRead out = new pgJniConnect.OutRead();
        int iRet = pgJniConnect.jniRead(m_InstID , t_iSessID , PG_API_READ_MAX_SIZE  , out);
        if(iRet < pgJniConnect.PG_ERROR_OK){
            return ;
        }

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
                t_sStr = t_sStr + String.format("客户端发送速度： %.1f M字节每秒",fResultByteSpeed);
                sNeesSendStr = sNeesSendStr + String.format("%.1f M",fResultByteSpeed);
            }
            else if (dSpeed_byte > Const_OneThousand) {
                fResultByteSpeed = (float) (dSpeed_byte / (Const_OneThousand));
                t_sStr = t_sStr + String.format("客户端发送速度： %.1f K字节每秒",fResultByteSpeed);
                sNeesSendStr = sNeesSendStr + String.format("%.1f K",fResultByteSpeed);
            }
            else {
                t_sStr = t_sStr + String.format("客户端发送速度： %d 字节每秒",dSpeed_byte);
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

            String t_sStrRecv = new String(out.byBuf);
            if(t_sStrRecv.equals("stopTestSpeed")) {
                m_handler.obtainMessage(P2P_Status_Stop_Test_Speed, t_sStrRecv).sendToTarget();
                m_bNeedSend = false;
                return;
            }

            if(t_sStrRecv.equals("ListenerNeedTestSpeed")) {
                sendStartTestSpeedRedyMsg();
                m_bNeedSend = true;
                return;
            }

            int maxSplit = 2;
            String sByteStr = "";
            String sBiteStr = "";
            String showStr = "";

            String []t_sParaArray  = t_sStrRecv.split(";",maxSplit);
            sByteStr = t_sParaArray[0] + "字节每秒";
            sBiteStr = t_sParaArray[1] + "比特每秒";
            showStr ="监听端发送速度: " + sByteStr + "  " + sBiteStr;
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
         //   FileInputStream fis = openFileInput(Const_Config_File_Name);
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

        String sTestIPAddr = "";
        String sP2PRelayAddr = "";
        String sShowResInfo = "";

        String t_sReadStr = readConfigFile();
        if(t_sReadStr == "" || t_sReadStr == null ){
            m_TryConIntInfoView.setText("没有读取到配置文件");
            return false;
        }

        int maxSplit = 3;
        String []t_sParaArray  = t_sReadStr.split(",",maxSplit);
        m_sTestIntAddr = t_sParaArray[0];
        sP2PRelayAddr = t_sParaArray[1];
        m_sP2PServerAddr = t_sParaArray[2];
//        m_sP2PServerAddr = "111.47.8.190:7781"; //tangchao

        if(m_sTestIntAddr == "" ||  m_sTestIntAddr == null){
            m_TryConIntInfoView.setText("未读到测试网站的内容");
            return false;
        }

        Runnable t_thread = new Runnable() {
            @Override
            public void run() {;
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
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    if (iRetCode != 200) {
                        testIntHander.obtainMessage(2,"").sendToTarget();
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
    //    m_tryIntBtn.setEnabled(false);
        return true;

    }

    boolean stopConP2P(){
//        writeConfigFile();
        sendStopTestSpeedMsg();
        m_loopThread = false;
        m_needLoopEvent  = false;
        m_StartConP2PServerBtn.setClickable(true);
        m_StartConP2PServerBtn.setEnabled(true);
        m_StartConP2PServerBtn.setFocusable(true);

        m_LoginP2PStatusView.setText("登录状态：未登录");
        m_ConP2PStatusView.setText("连接状态：未连接");
        m_TransInfoView.setText("传输速度：未测试");

        m_CheckBoxNeedRelay.setEnabled(true);
        m_CheckBoxNeedRelay.setClickable(true);
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
        while (m_needLoopEvent && (m_InstID > 0) ) {

            int iErr =  pgJniConnect.jniEvent( m_InstID , 500  , m_eventOut);
            int t_iSessNow = m_eventOut.iSessNow;

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
                continue;
            }



            String t_Str = "";
            if( m_eventOut.iEventNow == pgJniConnect.PG_EVENT_SVR_LOGIN ){
                m_handler.obtainMessage(P2P_Status_Login, t_Str).sendToTarget();
                m_Status = P2P_Status_Login;
            }
            else if( m_eventOut.iEventNow == pgJniConnect.PG_EVENT_CONNECT ){

                m_handler.obtainMessage(P2P_Status_ConP2Client, t_Str).sendToTarget();
                addOneSessInSessList(t_iSessNow);
                m_Status = P2P_Status_ConP2Client;

                if(m_bNeedSend == true){
                    byte []t_bytes = new byte[10 * 1024];
                    sessSend(true, t_iSessNow, t_bytes ,pgJniConnect.PG_PRIORITY_2);
                }
            }
            else if( m_eventOut.iEventNow == pgJniConnect.PG_EVENT_CLOSE ){
                m_handler.obtainMessage(P2P_Status_Close, t_Str).sendToTarget();
            }
            else if( m_eventOut.iEventNow == pgJniConnect.PG_EVENT_OFFLINE ){
                m_handler.obtainMessage(P2P_Status_Offline, t_Str).sendToTarget();

            }
            else if( m_eventOut.iEventNow == pgJniConnect.PG_EVENT_SVR_LOGOUT ){
                m_handler.obtainMessage(P2P_Status_Logout, t_Str).sendToTarget();
                m_Status = P2P_Status_Logout;
            }
            else if( m_eventOut.iEventNow == pgJniConnect.PG_EVENT_WRITE ){
                Log.d("log_PG_EVENT_WRITE","Receive Event\n");
				if(m_bNeedSend)
				{
                	byte []t_bytes = new byte[10 * 1024];
                	sessSend(true, t_iSessNow, t_bytes ,pgJniConnect.PG_PRIORITY_2);
            	}
			}
            else if( m_eventOut.iEventNow == pgJniConnect.PG_EVENT_READ ){
                Log.d("log_PG_EVENT_READ","Receive Event\n");
                sessRecv(m_eventOut.iSessNow);
            }

            else if( m_eventOut.iEventNow == pgJniConnect.PG_ERROR_TIMEOUT ){
                t_Str = "操作超时。可能原因有：\n" +
                        "1.检查网络连接断开（请重新进行联网测试）；\n" +
                        "2.服务器地址错误；\n" +
                        "3.本地ID输入错误;\n" +
                        "4.服务器端口被占用；\n" +
                        "5.防火墙因素";
                m_handler.obtainMessage(P2P_Status_LoginFail, t_Str).sendToTarget();
            }
            else if( m_eventOut.iEventNow == pgJniConnect.PG_ERROR_BUSY ){
                t_Str = "系统正忙";
                m_handler.obtainMessage(P2P_Status_LoginFail, t_Str).sendToTarget();
            }
            else if( m_eventOut.iEventNow == pgJniConnect.PG_ERROR_NOLOGIN ){
                t_Str = "还没有登录到P2P服务器。可能原因有：\n" +
                        "1.检查网络连接断开（请重新进行联网测试）；\n" +
                        "2.服务器地址错误；\n" +
                        "3.本地ID输入错误;\n" +
                        "4.服务器端口被占用；\n" +
                        "5.防火墙因素";
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
        }
    }

    @SuppressLint("HandlerLeak")
    public Handler testIntHander = new Handler(){
        @Override
        public  void handleMessage(Message msg){
            if( msg.what == 1){
                String sShowString = "";
                if(m_sTestIntAddr != null && !m_sTestIntAddr.equals("")) {
                    sShowString = "测试成功：成功连接到" + m_sTestIntAddr;
                    m_TryConIntInfoView.setText(sShowString);

                    if(m_bNeedCon){
                        m_bNeedCon = false;
                        conP2PServer();
                    }

                }
            }
            else if( msg.what == 2 ){
                String sShowString = "";
                if(m_sTestIntAddr != null && !m_sTestIntAddr.equals("")) {
                    sShowString = "测试失败：无法连接到" + m_sTestIntAddr;
                    m_TryConIntInfoView.setText(sShowString);

                    if(m_bNeedCon){
                        m_bNeedCon = false;
                        m_LoginP2PStatusView.setText("连接互联网失败，请检查网络");
                    }
                }
            }
        }
    };

    @SuppressLint("HandlerLeak")
    public Handler m_handler=new Handler()
    {
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
                    m_ConP2PStatusView.setText("已连接到客户端");
                    m_LoginP2PStatusView.setText("登录状态：已登录");
                    break;
                case P2P_Status_Stop_Test_Speed:
                    m_ConP2PStatusView.setText("已连接到客户端");
                    m_LoginP2PStatusView.setText("登录状态：已登录");
                    m_TransInfoView.setText("传输速度：未测试");
                    break;
                case P2P_Status_ConP2Client:
                    m_ConP2PStatusView.setText("已连接到客户端");
                    m_TransInfoView.setText("传输速度：未测试");
                    m_LoginP2PStatusView.setText("登录状态：已登录");
                    break;
                case P2P_Status_Login:
                    m_LoginP2PStatusView.setText("登录状态：已登录");
                    m_ConP2PStatusView.setText("连接状态：未连接");
                    m_TransInfoView.setText("传输速度：未测试");
                    break;
                case P2P_Status_Logout:
                    m_LoginP2PStatusView.setText("未登录。可能的原因有：\n" +
                            "1.检查网络连接断开（请重新进行联网测试）；\n" +
                            "2.服务器地址错误；\n" +
                            "3.本端设备ID错误； \n" +
                            "4.服务器端口被占用；\n" +
                            "5.防火墙因素;\n" +
                            "6.手动注销");
                    m_ConP2PStatusView.setText("连接状态：未连接");
                    m_TransInfoView.setText("传输速度：未测试");
                    break;

                case P2P_Status_Close:
                    m_LoginP2PStatusView.setText("登录状态：已登录");
                    m_ConP2PStatusView.setText("连接状态：未连接");
                    m_TransInfoView.setText("传输速度：未测试");
                    break;

                case P2P_Status_LoginFail:
                    m_LoginP2PStatusView.setText((String)(msg.obj));
                    m_ConP2PStatusView.setText("连接状态：未连接");
                    m_TransInfoView.setText("传输速度：未测试");
                    break;
//                case P2P_Status_Offline:
//                    m_ConP2PStatusView.setText("连接状态：未连接");
//                    m_TransInfoView.setText("传输速度：未测试");
//                    break;

                default:
                    break;
            }
        }
    };

    Runnable m_runnerble = new Runnable() {
        @Override
        public void run() {

            while (m_loopThread) {

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


                if(m_bNeedRelay){
                     sRelayList = "";
                     iTryP2PTimeout = Const_MAX_P2PTryTime_NoRelay + 1;
                 }

                 m_needLoopEvent = false;

                m_InstID = pgJniConnect.jniInitialize(pgJniConnect.PG_MODE_LISTEN,
                     m_sDevID ,"", sSvrAddr ,sSvrAddrBack,sRelayList,iBufSize0,iBufSize1,iBufSize2,iBufSize3,iSessTimeout,iTryP2PTimeout,iForwardSpeed,iForwardUse);

                if( m_InstID < 0 ) {
                    Log.d("jniInitialize failed","");
                    continue ;
                }


                m_needLoopEvent = true;
                eventLoop();


                for(int i = 0 ; i < Const_MAX_Sess_Count;i++){
                    if(m_SessList[i].iSessID != 0){
                        pgJniConnect.jniClose(m_InstID,m_SessList[i].iSessID);
                    }
                    m_SessList[i].cleanSees();
                }

                pgJniConnect.jniCleanup(m_InstID);

            }   //启动消息循环

        }
    };

     public boolean conP2PServer(){

         m_sDevID = m_editSelfDevID.getText().toString();
       //  m_sDevID = "lcz061401";
        if(m_sDevID.equals("") ){
            m_LoginP2PStatusView.setText("请输入本身设备ID");
            return false;
        }

         m_loopThread = true;

         m_bNeedRelay = m_CheckBoxNeedRelay.isChecked();
         m_CheckBoxNeedRelay.setEnabled(false);
         m_CheckBoxNeedRelay.setClickable(false);

        singleThreadPool.execute(m_runnerble);

         m_StartConP2PServerBtn.setClickable(false);
         m_StartConP2PServerBtn.setEnabled(false);
         m_StartConP2PServerBtn.setFocusable(false);

        return true;
    }

    boolean stopTestSpeed(){
        m_bNeedSend = false;
        return true;
    }

    boolean testSpeed(){
        m_bNeedSend = true;
        return true;
    }

    private android.view.View.OnClickListener m_OnClickListener = new android.view.View.OnClickListener() {
        @Override
        public void onClick(View view) {
            switch (view.getId()){
                case R.id.btnTestConInternet:
                    testConInternet();
                    break;
                case R.id.btnStopConnectID:
                    stopConP2P();
                    break;
                case R.id.btnConP2PServerID:
                    m_bNeedCon = true;
                    testConInternet();
                    break;

                default:
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //设置竖屏， 在xml设置不管用，所以在这里添加设置
        if(getRequestedOrientation()!= ActivityInfo.SCREEN_ORIENTATION_PORTRAIT){
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }
        setContentView(R.layout.activity_main);
        createThreadPool();
        m_editSelfDevID = (android.widget.EditText)findViewById(R.id.editP2PSelfID);

        m_TryConIntInfoView = (android.widget.TextView)findViewById(R.id.tryInterResultTextID);
        m_ConP2PStatusView = (android.widget.TextView)findViewById(R.id.conP2PInfoTextID);
        m_LoginP2PStatusView = (android.widget.TextView)findViewById(R.id.LoginP2PInfoTextID);
        m_TransInfoView = (android.widget.TextView)findViewById(R.id.speedInfoTextID);
        m_debugInfoView = (android.widget.TextView)findViewById(R.id.debugInfoTextView);

        m_tryIntBtn = (android.widget.Button)findViewById(R.id.btnTestConInternet);
        m_StartConP2PServerBtn = (android.widget.Button)findViewById(R.id.btnConP2PServerID);
        m_StopConP2PBtn = (android.widget.Button)findViewById(R.id.btnStopConnectID);

        m_CheckBoxNeedRelay = (android.widget.CheckBox)findViewById(R.id.needRelayCheckBoxID);

        m_tryIntBtn.setOnClickListener(m_OnClickListener);
        m_StartConP2PServerBtn.setOnClickListener(m_OnClickListener);
        m_StopConP2PBtn.setOnClickListener(m_OnClickListener);
        //初始化一些需要传递的变量
        m_loopThread = false;
        m_needLoopEvent = false;
        m_bNeedSend  = false;
        m_SessList = new SessClass[Const_MAX_Sess_Count];

        for(int iud = 0 ; iud < Const_MAX_Sess_Count;iud++){
            m_SessList[iud] = new SessClass();
        }

        m_eventOut = new pgJniConnect.OutEvent();

    }
}
