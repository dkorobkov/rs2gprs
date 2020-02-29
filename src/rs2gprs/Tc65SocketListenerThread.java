package rs2gprs;

/*
 * Tc65SocketListenerThread is put into a separate class to avoid app crashes 
 * caused by exceptions in ServerSocketConnection. 
 * On run() the thread starts listening. On connection, it creates an instance of 
 * SocketConnection and temporarily stores it. Parent thread should call 
 * hasActiveConnection() and, if true, get it by calling getActiveConnection(). 
 * Since this moment Tc65SocketListenerThread is ready to accept next incoming 
 * connection.   
 */

import javax.microedition.io.*;

public class Tc65SocketListenerThread extends Thread {

	ServerSocketConnection ssConn;
	Tc65SerialPort	tc65SerialPort;
	String szConnectionString;
	SocketConnection sc; // Will be passed to caller by getActiveConnection()
	boolean bHasActiveConnection;

	//	 чтобы не сразу принимать следующее соединение, а только когда сервер будет готов его обработать
	boolean bCanAcceptNewConnection; 
	
	int Port = 80; 
	int NetworkTimeout = 864000;
	public String szListeningAt;
	public int ListeningAtPort;

	public volatile boolean bStop = false; // Set it to true to stop thread
	
	public boolean bShowDebug = false;
	
	public boolean hasActiveConnection()
	{
		return bHasActiveConnection;
	}

	public void allowAcceptNewConnection()
	{
		bCanAcceptNewConnection = true;
	}
	
	public SocketConnection getActiveConnection() 
	{
		if(bHasActiveConnection == true)
		{
			// Remember that we've sent out the connected socket
			bHasActiveConnection = false;
			return sc;
		}
		return null;
	}
	
	private void DbgPrint(String s)
	{
		if(bShowDebug == true)
			tc65SerialPort.serialOut(s);
	}

	public Tc65SocketListenerThread(Tc65SerialPort tc65SerialPort) {
		super(); // call parent class constructor
		
		this.tc65SerialPort = tc65SerialPort;
		bHasActiveConnection = false;
		bCanAcceptNewConnection = false;
		
		szListeningAt = new String("(not set)");
		ListeningAtPort = 0;
		
		DbgPrint("Server::Server()\r\n");
	}

	public void run()
	{
//		 Create the server listening socket for port 1234 
//		   ServerSocketConnection scn = (ServerSocketConnection)
//		                            Connector.open("socket://:1234");

		   // Wait for a connection.
//		   SocketConnection sc = (SocketConnection) scn.acceptAndOpen();

		DbgPrint("Server.run()\r\n");
		
//		 create the server                      
		while (bStop == false) 
		{
			try {
				DbgPrint("ServerSocketConnection open(" + szConnectionString + ")\r\n");
				ssConn = (ServerSocketConnection) Connector
						.open(szConnectionString); // avoid "Network idle timeout" exception
//				.open("socket://:" + Port + ";timeout=" + NetworkTimeout); // avoid "Network idle timeout" exception
		 
				 while (bStop == false) 
			    {                       
					 // If we did not process existing connection do nothing.
					 if(bHasActiveConnection == true || bCanAcceptNewConnection == false)
					 {
						try {sleep(1000);} catch (Exception e) {DbgPrint("Tc65SocketListenerThread:sleep() failed\r\n");}						 
					 }
					 else
					 {
						try {
							// wait for incoming connection   
							DbgPrint("Server listens at " + ssConn.getLocalAddress() +
									":" + ssConn.getLocalPort() + "\r\n");
							szListeningAt = ssConn.getLocalAddress();
							ListeningAtPort = ssConn.getLocalPort();
							
							sc = (SocketConnection) ssConn.acceptAndOpen();
							DbgPrint("SocketConnection: acceptAndOpen() worked out\r\n");
							bHasActiveConnection = true;
							bCanAcceptNewConnection = false;
						} catch (Exception e) {
							DbgPrint("SocketConnection.acceptAndOpen(): exception " + 
									e.getMessage() + "\r\n");
							DbgPrint("Closing ssConn\r\n"); 
							ssConn.close();
							if(bStop == false)
							{
								DbgPrint("Waiting 60 sec to release socket\r\n");
								// Waiting to completely release socket 
								Thread.sleep(60000);
							}
							if(bStop == false)
							{
								DbgPrint("Reopening ServerSocketConnection\r\n"); 
							ssConn = (ServerSocketConnection) Connector.open(szConnectionString); 
							// avoid "Network idle timeout" exception
							}
						}	 
					 }
			    }
			} catch (Exception e) {
				DbgPrint("Server.Connector.open(): exception " + e.getMessage() + "\r\n");
			}		 
			if(bStop == false)
			{
				try {Thread.sleep(10000);} catch (Exception e) {}
			}
		} //while (bStop == false)

		try {ssConn.close();} catch (Exception e) {}
		
		try {
			DbgPrint("ServerThread: calling interrupt()\r\n");
			interrupt();
			DbgPrint("ServerThread: called interrupt()\r\n");
			DbgPrint("ServerThread: calling join()\r\n");
			join();
			DbgPrint("ServerThread: called join()\r\n");
		} catch (Exception e) {
			DbgPrint("Server.run(): cannot join(), exception " + 
					e.getMessage() + "\r\n");
		}
		DbgPrint("Server.run(): returning\r\n");
	}
	
	public void stopThread()
	{
		DbgPrint("Called Server.stopThread()\r\n");
		bStop = true;
		try {
			ssConn.close();
		} catch (Exception e) {
			DbgPrint("Server.stop(): exception " + e.getMessage() + "\r\n");
		}	
	}
}

