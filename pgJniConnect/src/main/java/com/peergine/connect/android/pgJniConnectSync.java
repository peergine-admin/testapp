/**********************************************************
  copyright   : Copyright (C) 2013-2015, chenbichao,
                All rights reserved.
  filename    : pgJniConnect.java
  discription : 
  modify      : create, chenbichao, 2015/12/12
              : 
**********************************************************/

package com.peergine.connect.android;

import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import com.peergine.connect.android.pgJniConnect;


public class pgJniConnectSync {

	///
	// 同步JAVA的API接口读取数据的最大长度。
	// 如果想接收更长的数据，则将此常量改大。
	public static final int PG_API_READ_MAX_SIZE = (64 * 1024);


	///-----------------------------------------------------
	// JAVA版的API接口函数（必须在APP的主线程中调用）。
	// 封装类的内部增加事件接收处理，并通过回调的方式把接收到的事件上报给APP。
	// 函数与pgLibConnect.h中的C接口函数一一对应，请参考C接口的使用说明。

	public int Initialize(int iMode, String sUser, String sPass, String sSvrAddr,
		String sSvrAddrBack, String sRelayList, int iBufSize0, int iBufSize1, int iBufSize2,
		int iBufSize3, int iSessTimeout, int iTryP2PTimeout, int iForwardSpeed, int iForwardUse)
	{
		try {
			// Init P2P instance.
			int iInstID = pgJniConnect.jniInitialize(iMode, sUser, sPass, sSvrAddr,
				sSvrAddrBack, sRelayList, iBufSize0, iBufSize1, iBufSize2,
				iBufSize3, iSessTimeout, iTryP2PTimeout, iForwardSpeed, iForwardUse);

			if (iInstID <= 0) {
				// Error code.
				Log.d("pgJniConnectSync", "Initialize, iErr=" + iInstID);
				return iInstID;
			}

			// Create output queue.
			m_listOutMsgRead = new ArrayList<OutMsgRead>();
			m_listOutMsgSvrReply = new ArrayList<OutMsgSvrReply>();
			m_listOutMsgSvrNotify = new ArrayList<OutMsgSvrNotify>();
			m_listOutMsgLanScanResult = new ArrayList<OutMsgLanScanResult>();

			// Create event queue.
			m_eventHandler = new Handler() {
				public void handleMessage(Message msg) {
					try {
						EventProc(msg);
					}
					catch (Exception ex) {
						Log.d("pgJniConnectSync", "Initialize, handleMessage Exception=" + ex.toString());
					}
				}
			};

			// Store instance id.
			m_iInstID = iInstID;

			// Start p2p event receive thread.
			m_bEventRun = true;
			m_Thread = new ThreadEvent();
			m_Thread.start();

			Log.d("pgJniConnectSync", "Initialize success.");
			return iInstID;
		}
		catch (Exception ex) {
			Log.d("pgJniConnectSync", "Initialize, ex=" + ex.toString());
			return pgJniConnect.PG_ERROR_SYSTEM;
		}
	}

	public void Cleanup() {
		try {
			m_bEventRun = false;
			if (m_Thread != null) {
				m_Thread.join();
				m_Thread = null;
			}

			m_listOutMsgRead = null;
			m_listOutMsgSvrReply = null;
			m_listOutMsgSvrNotify = null;

			if (m_iInstID != 0) {
				pgJniConnect.jniCleanup(m_iInstID);
				m_iInstID = 0;
			}
		}
		catch (Exception ex) {
			Log.d("pgJniConnectSync", "Cleanup, ex=" + ex.toString());
		}
	}

	public String Self() {
		if (m_iInstID != 0) {
			return pgJniConnect.jniSelf(m_iInstID);
		}
		return "";
	}

	public int Open(String sListenID) {
		if (m_iInstID != 0) {
			return pgJniConnect.jniOpen(m_iInstID, sListenID);
		}
		return pgJniConnect.PG_ERROR_INIT;
	}

	public void Close(int iSessID) {
		if (m_iInstID != 0) {
			pgJniConnect.jniClose(m_iInstID, iSessID);
		}
	}

	public int Info(int iSessID, pgJniConnect.OutInfo out) {
		if (m_iInstID != 0) {
			return pgJniConnect.jniInfo(m_iInstID, iSessID, out);
		}
		return pgJniConnect.PG_ERROR_INIT;
	}

	public int Write(int iSessID, byte[] byData, int iPrio) {
		if (m_iInstID != 0) {
			return pgJniConnect.jniWrite(m_iInstID, iSessID, byData, iPrio);
		}
		return pgJniConnect.PG_ERROR_INIT;
	}

	public int Read(int iSessID, int iTimeout, pgJniConnect.OutRead out) {
		if (m_iInstID != 0) {
			if (iTimeout > 0 && m_listOutMsgRead.size() <= 0) {
				if (!WaitRun(iTimeout)) {
					return pgJniConnect.PG_ERROR_BUSY;
				}
			}
			if (m_listOutMsgRead.size() > 0) {
				OutMsgRead outMsg = m_listOutMsgRead.remove(0);
				out.byBuf = outMsg.out.byBuf;
				out.iPrio = outMsg.out.iPrio;
				return outMsg.iErr;
			}
			return pgJniConnect.PG_ERROR_NODATA;
		}
		return pgJniConnect.PG_ERROR_INIT;
	}

	public int Pend(int iSessID, int iPrio) {
		if (m_iInstID != 0) {
			return pgJniConnect.jniPend(m_iInstID, iSessID, iPrio);
		}
		return pgJniConnect.PG_ERROR_INIT;
	}

	public int Connected(int iSessID) {
		if (m_iInstID != 0) {
			return pgJniConnect.jniConnected(m_iInstID, iSessID);
		}
		return pgJniConnect.PG_ERROR_INIT;
	}

	public int ServerRequest(String sData, int iParam) {
		if (m_iInstID != 0) {
			return pgJniConnect.jniServerRequest(m_iInstID, sData, iParam);
		}
		return pgJniConnect.PG_ERROR_INIT;
	}

	public int ServerReply(int iTimeout, pgJniConnect.OutSvrReply out) {
		if (m_iInstID != 0) {
			if (iTimeout > 0 && m_listOutMsgSvrReply.size() <= 0) {
				if (!WaitRun(iTimeout)) {
					return pgJniConnect.PG_ERROR_BUSY;
				}
			}
			if (m_listOutMsgSvrReply.size() > 0) {
				OutMsgSvrReply outMsg = m_listOutMsgSvrReply.remove(0);
				out.sData = outMsg.out.sData;
				out.iParam = outMsg.out.iParam;
				return outMsg.iErr;
			}
			return pgJniConnect.PG_ERROR_NODATA;
		}
		return pgJniConnect.PG_ERROR_INIT;
	}

	public int ServerNotify(int iTimeout, pgJniConnect.OutSvrNotify out) {
		if (m_iInstID != 0) {
			if (iTimeout > 0 && m_listOutMsgSvrNotify.size() <= 0) {
				if (!WaitRun(iTimeout)) {
					return pgJniConnect.PG_ERROR_BUSY;
				}
			}
			if (m_listOutMsgSvrNotify.size() > 0) {
				OutMsgSvrNotify outMsg = m_listOutMsgSvrNotify.remove(0);
				out.sData = outMsg.out.sData;
				return outMsg.iErr;
			}
			return pgJniConnect.PG_ERROR_NODATA;
		}
		return pgJniConnect.PG_ERROR_INIT;
	}

	public int ServerNetMode(int iMode) {
		if (m_iInstID != 0) {
			return pgJniConnect.jniServerNetMode(m_iInstID, iMode);
		}
		return pgJniConnect.PG_ERROR_INIT;
	}

	public int LanScanStart() {
		if (m_iInstID != 0) {
			return pgJniConnect.jniLanScanStart(m_iInstID);
		}
		return pgJniConnect.PG_ERROR_INIT;
	}

	public int LanScanResult(int iTimeout, pgJniConnect.OutLanScanResult out) {
		if (m_iInstID != 0) {
			if (iTimeout > 0 && m_listOutMsgLanScanResult.size() <= 0) {
				if (!WaitRun(iTimeout)) {
					return pgJniConnect.PG_ERROR_BUSY;
				}
			}
			if (m_listOutMsgLanScanResult.size() > 0) {
				OutMsgLanScanResult outMsg = m_listOutMsgLanScanResult.remove(0);
				out.Result = outMsg.out.Result;
				return outMsg.iErr;
			}
			return pgJniConnect.PG_ERROR_TIMEOUT;
		}
		return pgJniConnect.PG_ERROR_INIT;
	}

	public String Version() {
		return pgJniConnect.jniVersion();
	}


	///-----------------------------------------------------
	// 事件侦听接口定义
	public interface OnEventListener {
		void event(int iEventNew, int iSessIDNow, int iPrio);
	}

	// 调用此 SetEventListener() 函数设置事件侦听接口的实例对象。
	private OnEventListener m_eventListener = null;
	public void SetEventListener(OnEventListener eventListener) {
		m_eventListener = eventListener;
	}


	///-----------------------------------------------------
	// Constructor.
	public pgJniConnectSync() {
		m_iInstID = 0;
		m_Thread = null;
	}

	///-----------------------------------------------------
	// The event poll thread.
	private boolean m_bEventRun = false;
	class ThreadEvent extends Thread {
		public void run() {
			while (m_bEventRun && m_iInstID > 0) {
				pgJniConnect.OutEvent out = new pgJniConnect.OutEvent();
				int iErr = pgJniConnect.jniEvent(m_iInstID, 500, out);
				if (iErr != pgJniConnect.PG_ERROR_OK) {
					if (iErr != pgJniConnect.PG_ERROR_TIMEOUT) {
						Log.d("pgJniConnectSync", "jniEvent: iErr=" + iErr);
					}
					continue;
				}

				try {
					// Post the event to UI thread.
					Message msg = Message.obtain(m_eventHandler,
						out.iEventNow, out.iSessNow, out.iPrio, this);
					m_eventHandler.sendMessage(msg);
				}
				catch (Exception ex) {
					Log.d("pgJniConnectSync", "EventPost Exception");
				}
			}
		}
	}
	
	private void EventProc(Message msg) {
		Log.d("pgJniConnectSync", "EventProc: iEvent=" + msg.what + ", iSessID=" + msg.arg1 + ", iPrio=" + msg.arg2);
		
		switch (msg.what) {
		case pgJniConnect.PG_EVENT_CONNECT:
		case pgJniConnect.PG_EVENT_CLOSE:
		case pgJniConnect.PG_EVENT_WRITE:
		case pgJniConnect.PG_EVENT_OFFLINE:
		case pgJniConnect.PG_EVENT_INFO:
		case pgJniConnect.PG_EVENT_SVR_LOGIN:
		case pgJniConnect.PG_EVENT_SVR_LOGOUT:
			break;

		case pgJniConnect.PG_EVENT_READ:
			{
				OutMsgRead outMsg = new OutMsgRead();
				outMsg.iErr = pgJniConnect.jniRead(m_iInstID, msg.arg1, PG_API_READ_MAX_SIZE, outMsg.out);
				if (outMsg.iErr == pgJniConnect.PG_ERROR_NODATA) {
					return;
				}
				m_listOutMsgRead.add(outMsg);
				WaitEnd();
			}
			break;

		case pgJniConnect.PG_EVENT_SVR_REPLY:
			{
				OutMsgSvrReply outMsg = new OutMsgSvrReply();
				outMsg.iErr = pgJniConnect.jniServerReply(m_iInstID, outMsg.out);
				if (outMsg.iErr == pgJniConnect.PG_ERROR_NODATA) {
					return;
				}
				m_listOutMsgSvrReply.add(outMsg);
				WaitEnd();
			}
			break;

		case pgJniConnect.PG_EVENT_SVR_NOTIFY:
			{
				OutMsgSvrNotify outMsg = new OutMsgSvrNotify();
				outMsg.iErr = pgJniConnect.jniServerNotify(m_iInstID, outMsg.out);
				if (outMsg.iErr == pgJniConnect.PG_ERROR_NODATA) {
					return;
				}
				m_listOutMsgSvrNotify.add(outMsg);
				WaitEnd();
			}
			break;

		case pgJniConnect.PG_EVENT_LAN_SCAN:
			{
				OutMsgLanScanResult outMsg = new OutMsgLanScanResult();
				outMsg.iErr = pgJniConnect.jniLanScanResult(m_iInstID, outMsg.out);
				m_listOutMsgLanScanResult.add(outMsg);
				WaitEnd();
			}
			break;
		}

		// Call event listener.
		if (m_eventListener != null) {
			m_eventListener.event(msg.what, msg.arg1, msg.arg2);
		}
	}

	///
	// Output message classes.
	private class OutMsgRead {
		public int iErr = 0;
		public pgJniConnect.OutRead out;
		public OutMsgRead() {
			out = new pgJniConnect.OutRead();
		}
	}

	private class OutMsgSvrReply {
		public int iErr = 0;
		public pgJniConnect.OutSvrReply out;
		public OutMsgSvrReply() {
			out = new pgJniConnect.OutSvrReply();
		}
	}

	private class OutMsgSvrNotify {
		public int iErr = 0;
		public pgJniConnect.OutSvrNotify out;
		public OutMsgSvrNotify() {
			out = new pgJniConnect.OutSvrNotify();
		}
	}

	private class OutMsgLanScanResult {
		public int iErr = 0;
		public pgJniConnect.OutLanScanResult out;
		public OutMsgLanScanResult() {
			out = new pgJniConnect.OutLanScanResult();
		}
	}

	private ArrayList<OutMsgRead> m_listOutMsgRead = null;
	private ArrayList<OutMsgSvrReply> m_listOutMsgSvrReply = null;
	private ArrayList<OutMsgSvrNotify> m_listOutMsgSvrNotify = null;
	private ArrayList<OutMsgLanScanResult> m_listOutMsgLanScanResult = null;
	

	///-----------------------------------------------------
	// Message wait handler, convert asynchroize to synchroize.
	private Handler m_waitHandler = null;
	private TimerTask m_TimerTask = null;
	private Timer m_Timer = null;
	private boolean m_bWaitRunning = false;

	boolean WaitRun(int iTimeout) {

		if (m_Timer != null) {
			Log.d("pgJniConnectSync", "WaitRun: in waitting!");
			return false;
		}

		boolean bResult = false;

		try {
			m_waitHandler = new Handler() {  
				@Override  
				public void handleMessage(Message msg) { 
					if (m_bWaitRunning) { 
						throw new RuntimeException();
					}
				}
			};

			m_bWaitRunning = true;

			m_Timer = new Timer();
			m_TimerTask = new TimerTask() {
				@Override  
				public void run() {
					WaitEnd();
				}
			};
			m_Timer.schedule(m_TimerTask, iTimeout);

			Looper.getMainLooper().loop();
			
			m_bWaitRunning = false;
		}
		catch (Exception ex) {

			m_bWaitRunning = false;

			if (ex.toString().equals("java.lang.RuntimeException")) {
				bResult = true;
			}
			else {
				Log.d("pgJniConnectSync", "WaitRun: ex=" + ex.toString());
				bResult = false;
			}
		}

		m_Timer = null;
		m_TimerTask = null;
		m_waitHandler = null;

		return bResult;
	}

	void WaitEnd() {
		if (m_Timer != null) {
			m_Timer.cancel();
		}
		if (m_waitHandler != null) {
			m_waitHandler.sendMessage(m_waitHandler.obtainMessage());
		}	
	}


	///-----------------------------------------------------
	// this P2P instance.
	private int m_iInstID = 0;
	private ThreadEvent m_Thread = null;
	private Handler m_eventHandler = null;
}