/**********************************************************
  copyright   : Copyright (C) 2013-2014, chenbichao,
                All rights reserved.
  filename    : pgJniConnect.java
  discription : 
  modify      : create, chenbichao, 2014/4/4
              :
              : modify, chenbichao, 2014/11/14
              : 1. 增加LAN扫描API，可以搜索出局域网内部的侦听端
              :
              : modify, chenbichao, 2015/12/12
              : 1. 增加jniPeek API
              : 2. 把同步操作接口API分离到一个独立的JAVA类中。
              :
**********************************************************/

package com.peergine.connect.android;


public class pgJniConnect {

	///----------------------------------------------------
	// 常量定义

	///
	// *.so lib name. 
	// 工程生成的动态库的名称，必须修改成与Android.mk中的LOCAL_MODULE指定的名称一致，
	// 例如: 动态库的名称为“libDEMO.so”，则PG_MODE_LIB = "DEMO"。
	public static final String PG_MODE_LIB = "pgJniConnect";

	/// 
	// P2P运行模式：客户端或侦听端
	public static final int PG_MODE_CLIENT = 0;          // 客户端
	public static final int PG_MODE_LISTEN = 1;          // 侦听端（通常是设备端）

	///
	// 错误码定义
	public static final int PG_ERROR_OK = 0;             // 成功
	public static final int PG_ERROR_INIT = -1;          // 没有调用Initialize()或者已经调用Cleanup()清理模块
	public static final int PG_ERROR_CLOSE = -2;         // 会话已经关闭（会话已经不可恢复）
	public static final int PG_ERROR_BADPARAM = -3;      // 传递的参数错误
	public static final int PG_ERROR_NOBUF = -4;         // 会话发送缓冲区已满
	public static final int PG_ERROR_NODATA = -5;        // 会话没有数据到达
	public static final int PG_ERROR_NOSPACE = -6;       // 传递的接收缓冲区太小
	public static final int PG_ERROR_TIMEOUT = -7;       // 操作超时
	public static final int PG_ERROR_BUSY = -8;          // 系统正忙
	public static final int PG_ERROR_NOLOGIN = -9;       // 还没有登录到P2P服务器
	public static final int PG_ERROR_MAXSESS = -10;      // 会话数限制
	public static final int PG_ERROR_NOCONNECT = -11;    // 会话还没有连接完成
	public static final int PG_ERROR_MAXINST = -12;      // 实例数限制
	public static final int PG_ERROR_SYSTEM = -127;      // 系统错误

	///
	// 事件ID定义
	// 对应 OnEventListener 接口的 iEventNew 参数的值
	public static final int PG_EVENT_NULL = 0;           // NULL
	public static final int PG_EVENT_CONNECT = 1;        // 会话连接成功了，可以调用Write()发送数据
	public static final int PG_EVENT_CLOSE = 2;          // 会话被对端关闭，需要调用Close()才能彻底释放会话资源
	public static final int PG_EVENT_WRITE = 3;          // 会话的底层发送缓冲区的空闲空间增加了，可以调用Write()发送新数据
	public static final int PG_EVENT_READ = 4;           // 会话的底层接收缓冲区有数据到达，可以调用Read()接收新数据
	public static final int PG_EVENT_OFFLINE = 5;        // 会话的对端不在线了，调用Open()后，如果对端不在线，则上报此事件
	public static final int PG_EVENT_INFO = 6;           // 会话的连接方式或NAT类型检测有变化了，可以调用Info()获取最新的连接信息
	public static final int PG_EVENT_SVR_LOGIN = 16;     // 登录到P2P服务器成功（上线）
	public static final int PG_EVENT_SVR_LOGOUT = 17;    // 从P2P服务器注销或掉线（下线）
	public static final int PG_EVENT_SVR_REPLY = 18;     // P2P服务器应答事件，可以调用ServerReply()接收应答
	public static final int PG_EVENT_SVR_NOTIFY = 19;    // P2P服务器推送事件，可以调用ServerNotify()接收推送
	public static final int PG_EVENT_SVR_ERROR = 20;     // pgServerRequest返回错误。
	public static final int PG_EVENT_SVR_KICK_OUT = 21;  // 被服务器踢出，因为有另外一个相同ID的节点登录了。
	public static final int PG_EVENT_LAN_SCAN = 32;      // 扫描局域网的P2P节点返回事件。可以调用pgLanScanResult()去接收结果。

	///
	// 数据发送/接收优先级
	public static final int PG_PRIORITY_0 = 0;           // 优先级0, 最高优先级。（这个优先级上不能发送太大流量的数据，因为可能会影响P2P模块本身的握手通信。）
	public static final int PG_PRIORITY_1 = 1;           // 优先级1
	public static final int PG_PRIORITY_2 = 2;           // 优先级2
	public static final int PG_PRIORITY_3 = 3;           // 优先级3, 最低优先级

	///
	// P2P连接类型定义
	public static final int PG_CNNT_Unknown = 0;           // 未知，还没有检测到连接类型
	public static final int PG_CNNT_IPV4_Pub = 4;          // 公网IPv4地址
	public static final int PG_CNNT_IPV4_NATConeFull = 5;  // 完全锥形NAT
	public static final int PG_CNNT_IPV4_NATConeHost = 6;  // 主机限制锥形NAT
	public static final int PG_CNNT_IPV4_NATConePort = 7;  // 端口限制锥形NAT
	public static final int PG_CNNT_IPV4_NATSymmet = 8;    // 对称NAT
	public static final int PG_CNNT_IPV4_Private = 12;     // 私网直连
	public static final int PG_CNNT_IPV4_NATLoop = 13;     // 私网NAT环回
	public static final int PG_CNNT_IPV4_TunnelTCP = 16;   // TCPv4转发
	public static final int PG_CNNT_IPV4_TunnelHTTP = 17;  // HTTPv4转发
	public static final int PG_CNNT_IPV4_PeerFwd = 24;     // 节点转发
	public static final int PG_CNNT_IPV6_Pub = 32;         // 公网IPv6地址
	public static final int PG_CNNT_IPV6_TunnelTCP = 40;   // TCPv6转发
	public static final int PG_CNNT_IPV6_TunnelHTTP = 41;  // HTTPv6转发
	public static final int PG_CNNT_Offline = 0xffff;      // 对端不在线
	
	///
	// 选择网络的方式
	public static final int PG_NET_MODE_Auto = 0;          // 自动选择网络
	public static final int PG_NET_MODE_P2P = 1;           // 只使用P2P穿透
	public static final int PG_NET_MODE_Relay = 2;         // 只使用Relay转发


	///---------------------------------------------------
	//  API接口函数的输出参数类型定义

	/// 
	// jniEvent()函数的输出参数
	public static class OutEvent {
		public int iEventNow = 0;
		public int iSessNow = 0;
		public int iPrio = 0;
		public OutEvent() {
		}
	}

	// jniInfo()函数的输出参数
	public static class OutInfo {
		public String sPeerID = "";
		public String sAddrPub = "";
		public String sAddrPriv = "";
		public String sListenID = "";
		public int iCnntType = 0;
		public OutInfo() {
		}
	}

	// jniRead()函数的输出参数
	public static class OutRead {
		public byte[] byBuf = null;
		public int iPrio = 0;
		public OutRead() {
		}
	}

	// jniPeek()函数的输出参数
	public static class OutPeek {
		public int iPrio = 0;
		public OutPeek() {
		}
	}

	// jniServerReply()函数的输出参数
	public static class OutSvrReply {
		public String sData = "";
		public int iParam = 0;
		public OutSvrReply() {
		}
	}

	// jniServerNotify()函数的输出参数
	public static class OutSvrNotify {
		public String sData = "";
		public OutSvrNotify() {
		}
	}

	// jniLanScanResult()函数的输出参数
	public static class OutLanScanResult {
		public class Item {
			public String sID;
			public String sAddr;
			public Item() {
			}
		}
		public Item[] Result = null;
		public OutLanScanResult() {
		}
	}


	///-----------------------------------------------------
	// Constructor.
	public pgJniConnect() {
	}

	///-----------------------------------------------------
	// JNI API函数，一对一封装C的API函数。
	// APP也可以直接使用这些JNI封装的API函数，但需要自己调用jniEvent()处理事件。
	
	public native static int jniInitialize(int iMode, String sUser, String sPass, String sSvrAddr,
		String sSvrAddrBack, String sRelayList, int iBufSize0, int iBufSize1, int iBufSize2,
		int iBufSize3, int iSessTimeout, int iTryP2PTimeout, int iForwardSpeed, int iForwardUse);

	public native static void jniCleanup(int iInstID);

	public native static String jniSelf(int iInstID);

	public native static int jniEvent(int iInstID, int iTimeout, OutEvent out);

	public native static int jniOpen(int iInstID, String sListenID);

	public native static void jniClose(int iInstID, int iSessID);

	public native static int jniInfo(int iInstID, int iSessID, OutInfo out);

	public native static int jniWrite(int iInstID, int iSessID, byte[] byData, int iPrio);

	public native static int jniRead(int iInstID, int iSessID, int iSize, OutRead out);

	public native static int jniPend(int iInstID, int iSessID, int iPrio);

	public native static int jniPeek(int iInstID, int iSessID, OutPeek out);

	public native static int jniConnected(int iInstID, int iSessID);

	public native static int jniServerRequest(int iInstID, String sData, int iParam);

	public native static int jniServerReply(int iInstID, OutSvrReply out);

	public native static int jniServerNotify(int iInstID, OutSvrNotify out);

	public native static int jniServerNetMode(int iInstID, int iMode);

	public native static int jniLanScanStart(int iInstID);

	public native static int jniLanScanResult(int iInstID, OutLanScanResult out);

	public native static String jniVersion();

	static {
		try {
			System.loadLibrary(PG_MODE_LIB);
		}
		catch (Exception ex) {
			System.out.println("Load " + PG_MODE_LIB + ": " + ex.toString());
		}
	}
}