package rs2gprs;
/*
 * 120806: 	fixed exception while repeated file read/write
 * 			fixed keepalive sendout
 * 			added DisconnectTimeout for possibility of keeping permanent connection.
 */

import javax.microedition.midlet.MIDlet;
import javax.microedition.midlet.MIDletStateChangeException;

import com.siemens.icm.io.ATCommand;
import com.siemens.icm.io.ATCommandFailedException;


public class Main extends MIDlet {

	public static final boolean DebugInfoToCOM = true; //_DEBUG
	
	ATCommand		atCommand; // ������� ��� ����� � ������� ������ ������.
	Tc65SerialPort	tc65SerialPort;
	Io		io;
	MenuSettings	menuSetting;
	Tc65SocketConnection socketConnection;
	byte[] arbMagicSequence = {'k', 'E', 'e', 'P', 'A', 'l', '1', 'v', '@', '_', '6', 'Y', 'T', 'e'};
	
	public Main() {
		try
		{
			tc65SerialPort = new Tc65SerialPort();
			atCommand = new ATCommand(false);
			io = new Io();
			menuSetting = new MenuSettings(tc65SerialPort, atCommand, io);
			// socketConnection �������� �����
		}
		catch(Exception e) 
		{
			System.out.println(e.getMessage());
			serialOut(e.getMessage());
			notifyDestroyed();
		}
	}

	protected void destroyApp(boolean arg0) throws MIDletStateChangeException {
		System.out.println("destroyApp(" + arg0 + ")");

        notifyDestroyed();    

	}

	protected void pauseApp() {

	}
	/**
	 * Wrappers for debug output to serial port
	 * @param str
	 */
	void serialOut(String str)
	{
		tc65SerialPort.serialOut(str.getBytes());
	}
	void serialOut(byte[] ar)
	{
		tc65SerialPort.serialOut(ar);
	}
	void DebugOut(String str)
	{
		if(menuSetting.DebugInfoToCOM)
			tc65SerialPort.serialOut(str.getBytes());
	}
	void DebugOut(byte[] ar)
	{
		if(menuSetting.DebugInfoToCOM)
			tc65SerialPort.serialOut(ar);
	}


	protected void startApp() throws MIDletStateChangeException {

		serialOut("\n\rStarting rs2gprs app...\n\r\n\r" + 
				"(build 120826" +
				", Siemens R2 SDK)\n\r\n\r" +
//				", Siemens R3 SDK)\n\r\n\r" +
//				", Cinterion R1 SDK)\n\r\n\r" +
				"This applet is FREE both for usage and for any modification\n\r\n\r" +
				"See www.gsmpager.ru for detail, source code and user manual\n\r\n\r" +
				"\n\r" );

		menuSetting.start();

		// ����������, ��� ���������� �� �����������
		io.Out(0, false);
		
		// ����, ���� ���� �������������
		do
		{
        	try
        	{
        	Thread.sleep(1000);
        	}
        	catch(Exception e)
        	{
        		serialOut(e.toString());
        	}
		}
		while(menuSetting.isAlive());

		// ����������, ��� ���������� ����� ������� � ����� ������ ������ 
		// ������� - �� ��� �������� �������. ���� �� �������� - ������ �� ����� ����� � �� 
		// ����� ��.
 		DebugOut("Menu finished, continuing\n\r");
 		DebugOut("***************************************************\n\r" +
 				 "*             ATTENTION!!!                        *\n\r" +
 				 "* Debug output is ON. It means you will NOT get   *\n\r" +
 				 "* correct data exchange with your device.         *\n\r" +
 				 "* PLEASE TURN DEBUGGING OFF TO WORK WITH HARDWARE *\n\r" +
 				 "***************************************************\n\r" 
 				);
    	// allow sending this text out to avoid stack overflow
 		try
    	{
    	Thread.sleep(500);
    	}
    	catch(Exception e)
    	{
    		serialOut(e.toString());
    	}

 		
DebugOut("brc\r\n");
 		
		if(menuSetting.RetCode == menuSetting.MS_EXITPROG)
		{
			DebugOut("Exiting program...\r\n");
			destroyApp(true);
		}
			
DebugOut("bpwe\r\n");
		
		if(menuSetting.PinWasEntered == false)
		{
			// ������ PIN. ���� ������ - �������.
			if(EnterPIN() == false)
			{
				DebugOut("Error entering PIN, exiting app...\n\r");
		        destroyApp(true);
			}
		}

DebugOut("apwe\r\n");

//		serialOut("\n\rSetting serial port baudrate to " + Integer.toString(menuSetting.BaudRate) + "\n\r" );
		//120805 simple serialOut crashes the app somettimes and somewhy.
		tc65SerialPort.serialOut("\n\rSetting serial port baudrate to " + Integer.toString(menuSetting.BaudRate) + "\n\r" );
		// ����������� ���-���� �� �������� ��������
		tc65SerialPort.SetBaudRate(menuSetting.BaudRate);
		
		DebugOut("Creating Tc65SocketConnection...\r\n");    		
		socketConnection = new Tc65SocketConnection(tc65SerialPort);
		socketConnection.bShowDebug = menuSetting.DebugInfoToCOM;
		
		// ����� ��������� ��������� socketConnection-� 
		Thread.yield();
		
		byte ar[] = new byte[2048]; // ������ ��� ������������ ������
		int pos = 0; // ��������� � �������

//		 ������ ��� ������ �� ������, ������� ���� ������� � ���-���� ��� ��������.
		// ����������� ��� ��������� �����.
		byte arFromSocket[] = new byte[256];  
		int posFromSocketR = 0; 
		int posFromSocketW = 0; 
		
		boolean bExit = false;
		int IncomingDataTimer = 0; // ������� ����� ����� ���������� ���������� �� ������ �����.
		int IncomingKeepaliveTimer = 0; // ������� ����� ����� ���������� �����������
		int KeepaliveCount = 0;
		int ReportListeningAtCount = 0;
		
		// *************************************************************************
		// ������-�� ����� ������ ��������� ��� long, ��� ������ ��� ����������, ����� 
		// ��������� �������� ����� ���-����!!!
		// **************************************************************************

		
		// ������ ���������
DebugOut("Setting up connection...\r\n");    		
   		SetupConnection();

    	if(menuSetting.OperationMode == Tc65SocketConnection.OP_MODE_SERVER) 
    	{
DebugOut("Starting listen...\r\n");    		
    		socketConnection.StartListen();
DebugOut("Started listen, sleep 5s\r\n");    		
			try {Thread.sleep(5000);} catch (Exception e) {}				
        	DebugOut(socketConnection.GetListenAddress());
   	}
   		
        while(bExit == false)
        {
        	// ���� �� ������, �� �� ������ �������� �� ����� ������ �����, ���� �� ����� �������� ����������
        	// ���� ������ - �� ������� ���.
        	if(menuSetting.OperationMode == Tc65SocketConnection.OP_MODE_CLIENT) {
				try {Thread.sleep(5000);} catch (Exception e) {}				
				// �������������� �����
				ReinitModem();
	        	DebugOut("*g*");
			}
        	else
        	{
               	DebugOut("-");
               	ReportListeningAtCount++;
               	if(ReportListeningAtCount > 30)
               	{
               		DebugOut(socketConnection.GetListenAddress());
               		ReportListeningAtCount = 0;
               	}
				try {Thread.sleep(1000);} catch (Exception e) {}               	
        	}
	        		
    		// �����������
    		boolean bConnected = socketConnection.Connect();
    		// ���� �����������, ���������� ������� �����
    		if(bConnected == true)
    		{
       			io.Out(0, true);
               	DebugOut("*G*");
    		}
    		else
    		{
       			io.Out(0, false);
				continue;
    		}
       		// ������ ������ �� ������������
    		/*
    		 * ��� ������ ����� ������� ��������� (�� ����� � �����) �������� ������� �������, ��� �������.
    		 * ��� ������� ���������� - �� � ��� ��-�� ����� ������� ��������. � ���������, ����� �� ������ � ����
    		 * ����������, ���� �� ���� ��� ���� ������. 
    		 * ����� ������ ��� �������������, ����� ����� ������ ����� ����� ����������, �� �� �� �����, � 
    		 * ������ ����� ��� ������ ���-�� ����� (����� �� ������� ������) � � ������� ���������� ������ ����� �����.  
    		 * �.�. ����� ��� ����������, �� ��� ������ ���� �����, ����� ���������� ����� ��������, ����� ��
    		 * ���������� - ������. 
    		 * ����� ��� ���������, ���� � ��� ���� ������ �� ���-����� � �� ���������� ���-�� ������� ������, �� 
    		 * ��� �� ������� ����� ��� ��������.
    		 * ���� ���� ������� ������ ���� ������, ������ ���������, ������ ���� ��� magic packet - ������� ������
    		 * � ��������� ����������, ������� ���� �� ���������� � ��������� �������. � �� ��� ����� ������� ��� 
    		 * ������ �������� � �������� �� ���� ������ (�.�. ���� ������������ ����������� ������ com0tcp).
    		 * � ���� ����� �������� ���� �������� � ���� magic packet - � �� �� ���� �������� ����� ����������, 
    		 * ����� �����������, ��� �� ����.
    		 */
   			IncomingDataTimer = millisNow(); // ������� ����� ����� ���������� ���������� �� ������ �����.
   			IncomingKeepaliveTimer = IncomingDataTimer; // ������� ����� ����� ���������� �����������
   			KeepaliveCount = IncomingDataTimer;
   			boolean bPreviousByteWasKeepalive = false; // ��� �� ���������� ���� ����������

   			// Set DSR (in R3 and Cinterion SDKs, uncomment "R3" lines in tc65SerialPort!)
   			tc65SerialPort.DSR(true);
   			
       		while(socketConnection.IsConnected())
       		{
       			boolean Stop = false;
   				pos = 0;

   				// For outgoing data, do not collect Modbus packets (slows down TCP) - to be done on PC side 
   				// For Modbus delay support
   				boolean AllSerialDataGot = false;
   				int IncomingSerialDataTimer = millisNow(); // Track data arrival time
   				
   				// ���� ������� ������ ������ �� ������, ���� ��������� ����� �� ������ � ��������.
   				// ��� ����� ����� ���� ���� ����������.
   				if(millisNow() - IncomingDataTimer < 10000)
   				{
   					if(menuSetting.UseKeepalives == true)
   					{
						byte ab[] = {(byte)menuSetting.KeepaliveByte, (byte)menuSetting.KeepaliveByte, 
								(byte)menuSetting.KeepaliveByte, (byte)menuSetting.KeepaliveByte};
						socketConnection.write(ab, 4);
   					}
   				}
   				
   				while(AllSerialDataGot == false)
   				{
   					// ���� �� ���������� ����� ������ Modbus, ��� ������� ����������, ����� ��������� �����, 
   					// �� ������ ���� � ��� ���-�� ���� � ���-�����.
   					if(menuSetting.UseKeepalives == true)
   					{
   						if(tc65SerialPort.hasData() > 0) // �� ����� ����� �������� �������� ������...
   						{
   							byte ab[] = {(byte)menuSetting.KeepaliveByte, (byte)menuSetting.KeepaliveByte, 
   									(byte)menuSetting.KeepaliveByte, (byte)menuSetting.KeepaliveByte};
   							socketConnection.write(ab, 4);
   						}
   					}
   					
	   				// �������� �� ���-����� ������ � ����� � �����
					while(tc65SerialPort.hasData() > 0)
					{
						IncomingSerialDataTimer = millisNow();
						int j = tc65SerialPort.readByte();
						if(j != -1)
						{
							byte b = (byte)(j);
							ar[pos]=b;
							pos++;
	
							// ���� � ������ ������� ����������� ����, �������� magic sequence. �� ��� ����� � ������� �������� �������.
							if(j == menuSetting.KeepaliveByte && menuSetting.UseKeepalives == true)
							{
								for(int x=0; x<arbMagicSequence.length; x++)
									ar[pos+x]=arbMagicSequence[x];
	    						pos += arbMagicSequence.length;
	    						DebugOut("*k*");
							}
	//            					socketConnection.writeByte(b);
						}
						if(pos > 2000) // avoid buffer overflow
							break;
					}
					if(pos > 2000) // avoid buffer overflow
						AllSerialDataGot = true;
					// If not using Modbus exit loop immediately.
					if(menuSetting.UseModbus == false)
						AllSerialDataGot = true;
					// If using Modbus exit loop if data are not arriving during last menuSetting.ModbusDelay.
					if((menuSetting.UseModbus == true && ((millisNow()-IncomingSerialDataTimer) > menuSetting.ModbusDelay)))
					{
						AllSerialDataGot = true;
//						DebugOut("*a*");
					}
					// TODO: Analyze Modbus packets more smart.
   				} //while(AllSerialDataGot == false)
   				
				if(pos > 0)
				{
					socketConnection.write(ar, pos);
					DebugOut("*->*");
					KeepaliveCount = millisNow();
					pos = 0;
				}
	        				
				// ���� ��������� (�� 1 ����, ������� �� ��������� ����� �����, � 10)
				if( (millisNow() - KeepaliveCount) > 30000)  // > 30 ������ - line fixed in 120806
				{
					KeepaliveCount = millisNow();
					if(menuSetting.UseKeepalives == true)
					{
						byte ari[] = new byte[10];
						for(int i=0;i<10; i++)
							ari[i] = (byte)menuSetting.KeepaliveByte;
						socketConnection.write(ari, 10);
						DebugOut("*K*");
					}
				}
	        				
				// �������� �� ������ � ���� � ����, ���� �����.  
				int len = socketConnection.hasData();
				
				if(len > 0)
				{
        			IncomingDataTimer = millisNow();
					DebugOut("*s"+Integer.toString(len)+"*");
				}

				while(len > 0)
				{
					int j = socketConnection.readByte();
					boolean bProceed = true;
					if(j<0)
						bProceed = false;
					else
						IncomingDataTimer = millisNow();

					// �����������, ���� ������ ���� ��������� 0xYZ, ���� ���� ������ 0xYZ, 
					// �������� ���������� ��� 0xYZYZ, ���� ������ ����.
					
					if(j != menuSetting.KeepaliveByte) // ��������� ���� - �� ff, �������� � ���������
					{
						bPreviousByteWasKeepalive = false;
						IncomingKeepaliveTimer = millisNow();
					}
					
					if(menuSetting.UseKeepalives == true && j == menuSetting.KeepaliveByte)
					{
						// ���������� ���� ��� FF � ������ ����� (> 5 c)?
						if(bPreviousByteWasKeepalive == true && (millisNow() - IncomingKeepaliveTimer) > 5000) // fixed in 120806 - added millisNow()
						{
							// ��������, ������ �������� ���� ���� FF
							bPreviousByteWasKeepalive = true; // ��� � ��� �����.
							IncomingKeepaliveTimer = millisNow(); // ���������� ������
							bProceed = false;
						}	        						
						// ���������� ���� - �� FF?
						else if(bPreviousByteWasKeepalive == false) 
						{
							// ��������, ������ �������� ���� ���� FF
							bPreviousByteWasKeepalive = true; // ����������, ��� ��� FF
							IncomingKeepaliveTimer = millisNow(); // ���������� ������
							bProceed = false; // �� ��������
						}
						else if(bPreviousByteWasKeepalive == true && (millisNow() - IncomingKeepaliveTimer) <= 5000) // fixed in 120806 - added millisNow()
						{
							// ����� ��� ������ ���� FF - ������, ��� �������� ���� ��� �������
							bPreviousByteWasKeepalive = false; 
							bProceed = true;
						}
						
					}
					if(bProceed == true)
					{
						// ������ �� �������� � ����, ������ ������� � ����� � ����� �������� � ����
						// �� ������. � ��� ������� ������� ��������� �������� � ��� �������!!!
						arFromSocket[posFromSocketW] = (byte)j;
						posFromSocketW++;
						if(posFromSocketW >= 256) posFromSocketW = 0;
					}

					// �������, ���� �� ��� ���-�� � ������. ��� - ���� ������������.
					len = socketConnection.hasData();
				}

				// If Modbus delay not used or exceeded the value set in menu.
				if(
						(menuSetting.UseModbus == false) ||
						(menuSetting.UseModbus == true && ( (millisNow()-IncomingDataTimer) > menuSetting.ModbusDelay))
						) // If no data arrived from socket for a long time
				{
					int nSend = 0;
					byte arSend[] = new byte[256];
					while(posFromSocketR != posFromSocketW)
					{
						arSend[nSend++] = arFromSocket[posFromSocketR];
						posFromSocketR++;
						if(posFromSocketR >= 256) posFromSocketR = 0;
						if(nSend > 255)
							break;
					}
					// 120806 Send all bytes in a single buffer. Waiting between bytes is useless as TC65 f/w adds 
					// delays after each byte sent out (writes next byte into output register on "shift register empty" interrupt
					if(nSend > 0)
						tc65SerialPort.write(arSend, nSend);
				}
				
// ���������� �� �������� ������ � ���������� - ���� �� ������ ������ �� ������.
// ���� ���� ����� ������, �� ��������� �� ���� (��� ���������) -
// �� ������������ ��� ���� �� ����! 

				if(menuSetting.DisconnectTimeout > 0) // ���� �������� ���������
				{
	    			if((millisNow()-IncomingDataTimer) > menuSetting.DisconnectTimeout * 1000) // timeout
	    			{
						DebugOut("*S("+Integer.toString(millisNow()-IncomingDataTimer)+")["+
								Integer.toString(millisNow()-IncomingKeepaliveTimer)+"]*");
	    				Stop = true;
	    			}
				}
    			if(Stop == true)
    				break;
     		} //while(socketConnection.IsConnected())

   			tc65SerialPort.DSR(false);
       		
    		// ������������� ������������� ������
    		socketConnection.Disconnect();
 
    		io.Out(0, false);
			DebugOut("*D*");
			
			ReinitModem();
			
			// � �������� ��������, ��� ��������� ����� ������ �� ������, 
			// ��� ����� 2 ������. 
			try {Thread.sleep(60000);} catch (Exception e) {}			
        }
         
        // � ��������, ���� �� �� ������� �������.
        socketConnection.StopListen();
        
		// ����������� ���-���� ������� �� 115200
        serialOut("\n\rDestroying...\n\r");
        
		io.Out(0, false); // off

		destroyApp(true);
	}

	boolean EnterPIN()
	{
		boolean bOkFound = false;
		
		if(menuSetting.PINCode.length() == 0)
		{
			DebugOut("PIN empty, will not be sent.\n\r" );
			return true;
		}
		
		// ���� ��� �� �������� - ��� �� ��� (��� �� ��������).
		// �� ��������� ��� �� ���� �������.
		char ar[] = menuSetting.PINCode.toCharArray();
		boolean bIsNotPin = false;
		for(int i=0; i<menuSetting.PINCode.length(); i++)
		{
			if(ar[i] < '0' || ar[i] > '9')
				bIsNotPin = true;
		}
		if(bIsNotPin == true)
		{
			DebugOut("PIN is " + menuSetting.PINCode + "(not a PIN), will not be sent.\n\r" );
			return true;
		}
		
		DebugOut("PIN=" + menuSetting.PINCode + "\r\n" );
		String s = new String("(no command has been sent)");
		try{
		s = atCommand.send("AT+CPIN=" + menuSetting.PINCode + "\r");
		}
		catch(ATCommandFailedException e)
		{
			serialOut("Exception " + e.getMessage() + "\n\r" );
		}
		DebugOut("\r\nCommand:" + s + "\n\r" );
		
		if(s.indexOf("OK") > 0)
			bOkFound = true;
		
		return bOkFound;
	}
	
	void SetupConnection()
	{
		socketConnection.SetConnType(menuSetting.ConnType, menuSetting.OperationMode);

		socketConnection.setAPN(menuSetting.szAPN);
		socketConnection.setURL(menuSetting.szAddressToConnectTo);
		socketConnection.setPort(menuSetting.PortToConnectTo);

		socketConnection.setUsername(menuSetting.szUsername);
		socketConnection.setPassword(menuSetting.szPassword);
		socketConnection.setCSDPhoneNumber(menuSetting.CSDPhoneNumber);
	}

	// ����� � ����� �� ����� ���� ������������ �������� long, ������ ����� �������.
	public int millisNow()
	{
		return (int)System.currentTimeMillis();
	}
	
	private void ReinitModem()
	{
    	// ����� ����� �������������������� �����, ������ 
    	// ��� �������� ��� ����� ���������� ������
    	try{atCommand.send("ATE1&C1&D0&S0X4Q0S0=1V1\r");}
		catch(ATCommandFailedException e)
		{
			DebugOut("ATCommandFailedException: " + e.getMessage() + "\n\r");
		}
		catch(IllegalStateException e)
		{
			DebugOut("IllegalStateException: " + e.getMessage() + "\n\r");
		}
    	// Wait here even after "continue" below.
		try{Thread.sleep(5000); // Datasheet says to reconnect after 600 s delay!
    	}catch(Exception e){DebugOut(e.toString());}
	}
}
