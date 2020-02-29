package rs2gprs;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.microedition.io.Connector;
import javax.microedition.io.SocketConnection;

/**
 * @author -
 *
 */
public class Tc65SocketConnection {

	SocketConnection socketConn; // For outgoing connections
	//ServerSocketConnection serverSocketConn; // For incoming connections
	Tc65SocketListenerThread tc65SocketListenerThread; 
	InputStream   	inStream;
	OutputStream  	outStream;
	Tc65SerialPort  tc65SerialPort;
	
	public static final boolean CONN_TYPE_GPRS = true;
	public static final boolean CONN_TYPE_CSD = false;
	public static final boolean OP_MODE_CLIENT = false; // Дозваниваемся до сервера
	public static final boolean OP_MODE_SERVER = true; // Принимаем входящие запросы
	
	
	// Строки с параметрами соединения
	boolean bUseGprs=true; //CSD / GPRS
	boolean bOperationMode; // client or server
	String szAPN;
	String szCSDPhoneNumber;
	String szUsername;
	String szPassword;
	String szConnectionString;
	String szURL;
	int Port;
	
	public boolean bShowDebug = false;
	
	boolean bConnected;
	boolean bListening;
	
	/**
	 * 
	 */
	public Tc65SocketConnection(Tc65SerialPort  tc65SerialPort) {
		this.tc65SerialPort = tc65SerialPort;
		szAPN = new String("internet.nw");
		szCSDPhoneNumber = new String("");
		szUsername = new String("");
		szPassword = new String("");
		szConnectionString = new String("");
		szURL = new String("www.gsmpager.ru");
		Port = 7265; // "tc65"
		bConnected = false;
		bListening = false;
		// Create thread for incoming GPRS connections
		tc65SocketListenerThread = new Tc65SocketListenerThread(tc65SerialPort);
	}
	
	private void BuildConnectionString() {
		if(bOperationMode == OP_MODE_CLIENT)
			szConnectionString = "socket://" + szURL + ":" + Integer.toString(Port);
		else
			szConnectionString = "socket://:" + Integer.toString(Port);
		
		szConnectionString += ";bearer_type=";
		if(bUseGprs)
			szConnectionString += "gprs;access_point=" + szAPN;
		else
			szConnectionString += "csd;phone_number=" + szCSDPhoneNumber;
		if(szUsername.length() > 0)
			szConnectionString += ";username=" + szUsername + ";password=" + 
				szPassword;
		if(bOperationMode == OP_MODE_CLIENT)
			szConnectionString += ";timeout=30";
		else
			szConnectionString += ";timeout=864000";
	}

	// Server should listen before connecting
	public boolean StartListen()
	{
		// Create listening thread
		if(bShowDebug)
			tc65SerialPort.serialOut("Creating listener thread...\r\n");
		if(tc65SocketListenerThread.isAlive() == false)
		{
			
			tc65SocketListenerThread.Port = Port;
			tc65SocketListenerThread.bShowDebug = bShowDebug;
			BuildConnectionString();
			tc65SocketListenerThread.szConnectionString = szConnectionString;
			if(bShowDebug)
				tc65SerialPort.serialOut("Before tc65SocketListenerThread.start()\r\n");
			tc65SocketListenerThread.start();
			if(bShowDebug)
				tc65SerialPort.serialOut("After tc65SocketListenerThread.start()\r\n");
			Thread.yield(); // let tc65SocketListenerThread thread initialize 
			return true;
		}
		else
		{
			if(bShowDebug)
				tc65SerialPort.serialOut("tc65SocketListenerThread.isAlive() is true\r\n");
		}
		
		return false;
		
	}
	public boolean StopListen()
	{
		if(tc65SocketListenerThread.isAlive() == true)
		{
			if(bShowDebug)
				tc65SerialPort.serialOut("StopListen(): Stopping tc65SocketListenerThread\r\n");
			tc65SocketListenerThread.stopThread();
			return true;
		}
		else
		{
			if(bShowDebug)
				tc65SerialPort.serialOut("StopListen(): tc65SocketListenerThread is alreay dead\r\n");
		}
		return false;
	}
	public String GetListenAddress()
	{
		if(tc65SocketListenerThread.isAlive() == true)
			return new String("Listening at: " + tc65SocketListenerThread.szListeningAt + ":" +
					tc65SocketListenerThread.ListeningAtPort);
		else return new String("Not listening");
	}
	
	public boolean Connect()
	{
		bConnected = false;
		try
		{
			BuildConnectionString();
//			tc65SerialPort.serialOut("Connection string: " + szConnectionString + "\r\n");
			if(bOperationMode == OP_MODE_CLIENT)
			{
				socketConn = (SocketConnection)Connector.open(szConnectionString);
			}
			else
			{
				// If we are server
				if(bUseGprs)
				{
					// Check if we have incoming connection
					if(tc65SocketListenerThread.hasActiveConnection() == false)
					{
						// Говорим, что мы готовы принять следующее соединение. Иначе оно будет подвисать
						// и отваливаться по таймауту до того, как мы его обработаем. 
						tc65SocketListenerThread.allowAcceptNewConnection();
						return false;
					}
					// Get this connection and let server know it can accept more.
					socketConn = (SocketConnection) tc65SocketListenerThread.getActiveConnection();
					if(socketConn == null)
						return false;
				}
				else
				{
					// TODO: answer dialin
				}
			}
			
// Дефолтные размеры буферов - 5200.			
//			int v = socketConn.getSocketOption(SocketConnection.RCVBUF);
//			int vv = socketConn.getSocketOption(SocketConnection.SNDBUF);
//			tc65SerialPort.serialOut("RX=" + Integer.toString(v)+",TX="+Integer.toString(vv)+"\r\n");
			
			socketConn.setSocketOption(SocketConnection.LINGER, 5);
			socketConn.setSocketOption(SocketConnection.KEEPALIVE, 1);
			socketConn.setSocketOption(SocketConnection.DELAY, 1);
//			socketConn.setSocketOption(SocketConnection.RCVBUF, 1024);
//			socketConn.setSocketOption(SocketConnection.SNDBUF, 1024);

//			tc65SerialPort.serialOut("Connector.open\r\n");
			//socketConn.setSocketOption(SocketConnection.LINGER, 5);
			inStream = socketConn.openInputStream();
//			tc65SerialPort.serialOut("openInputStream\r\n");
			outStream = socketConn.openOutputStream();
//			tc65SerialPort.serialOut("openOutputStream\r\n");

			bConnected = true;
		}
		catch (IOException e) {
			tc65SerialPort.serialOut("Tc65SocketConnection.Connect(): IOException " + e.getMessage() + "\r\n");
			bConnected = false;
		}
		return bConnected;
	}

	public boolean IsConnected()
	{
		return bConnected;
	}
	
	public void Disconnect()
	{
		try
		{inStream.close();}
		catch(IOException ioe){}
		try
		{outStream.close();}
		catch(IOException ioe){}
		try
		{socketConn.close();}
		catch(IOException ioe){}
		bConnected = false;
	}
	
	public void SetConnType(boolean bUseGPRS, boolean bOperationMode)
	{
		this.bUseGprs = bUseGPRS;
		this.bOperationMode = bOperationMode;
	}

	public void setAPN(String szAPN) {
		this.szAPN = szAPN;
	}

	public void setURL(String szURL) {
		this.szURL = szURL;
	}

	public void setCSDPhoneNumber(String szCSDPhoneNumber) {
		this.szCSDPhoneNumber = szCSDPhoneNumber;
	}

	public void setUsername(String szUsername) {
		this.szUsername = szUsername;
	}

	public void setPassword(String szPassword) {
		this.szPassword = szPassword;
	}

	public void setPort(int port) {
		Port = port;
	}
	
	public int hasData()
	{
		int n = 0;
		try 
		{
			n = inStream.available();
		} 
		catch (IOException e) 
		{
			bConnected = false;
		}
		
		return n;
	}
	
	int readByte()
	{
		int NextByte = -1;
		try {
			NextByte = inStream.read();
		} catch (IOException e) {
			tc65SerialPort.serialOut("readByte():IOException " + e.getMessage() + "\r\n");
			bConnected = false;
		}
		return NextByte;
	}

	boolean writeByte(byte b)
	{
		boolean bOk = false;
		try {
			outStream.write(b);
			bOk = true;
		} catch (IOException e) {
//			e.printStackTrace();
			bOk = false;
			tc65SerialPort.serialOut("writeByte():IOException " + e.getMessage() + "\r\n");
			bConnected = false;
		}
		return bOk;
	}
	
	int read(byte[] array, int len)
	{
		int nBytes = 0;
		if(len > array.length)
			len = array.length;
		try {
			nBytes = inStream.read(array, 0, len);
		} catch (IOException e) {
//			e.printStackTrace();
			tc65SerialPort.serialOut("read():IOException " + e.getMessage() + "\r\n");
			bConnected = false;
		}
		return nBytes;
	}
	boolean write(byte[] array, int len)
	{
		boolean bOk = false;
		try {
			outStream.write(array, 0, len);
			bOk = true;
		} catch (IOException e) {
			e.printStackTrace();
			bOk = false;

			tc65SerialPort.serialOut("write():IOException " + e.getMessage() + "\r\n");
			
			bConnected = false;
		}
		return bOk;
	}
	
}
