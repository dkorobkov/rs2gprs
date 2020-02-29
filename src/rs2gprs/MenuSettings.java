package rs2gprs;

import java.io.*;
import javax.microedition.io.Connector;

import com.siemens.icm.io.ATCommand;
import com.siemens.icm.io.ATCommandFailedException;
import com.siemens.icm.io.file.*;

/**
 * ���� ��� ��������� ������ ����� ���-����.
 * � ������ ������ ��������� ������ Enter, ����� ����
 * ���������� ������ �����, ���������� �������.
 * @author dvk
 *
 */

/**
 * @author -
 *
 */
public class MenuSettings extends Thread
{
	// ������� �������, �� ������� ��� ����� ������ � ������������
	Tc65SerialPort 	tc65SerialPort;
	ATCommand 		atCommand;
//	Io		io;
	
	// 
	// ��������� ��� ������ � ����
	public final char MS_TIMEOUT 	= 65535; // ��������� ����: �������
	public final char MS_NODATA 	= 0;  // ���������� � ProcessMenu, ����� ��� ������.

	public final int MS_HAVEATCMD_ENTERPIN = 7; // ��� ������������� ��-������ ����� ���  
	public final int MS_EXITMENU	= 6; // ��������� ProcessMenu() - ����� �� ���� 
	public final int MS_SENDTESTSMS = 5;
	public final int MS_TOGGLEINDICATORTEST	= 4; // ����������� ��������� ��� ��� 
	// ��������� ProcessMenu() - ���� ��-������� ��� ����������
	public final int MS_TESTCONNECTION = 3; 
	public final int MS_HAVEATCMD= 2;  
	public final int MS_EXITPROG	= 1; // ��������� ProcessMenu() - ����� �� ����� 
	public final int MS_DONTEXIT	= 0; 
	
	// � ��� ���������� �� ��������� ������ ���� ������������, ���� �� ����� ��
	// ���������.
	public int RetCode = MS_DONTEXIT;
	public boolean PinWasEntered = false;
	
	// ������������ keepalive ��� ���
	public boolean UseKeepalives = false;
	// ���� ��� ������������� � �������� keepalive 
	public int KeepaliveByte = 0xff;
	
	// ������� ������ �� ����
	int MenuTimeout = 0;
	int CurrMenu = 0; // ������� ����� ���� (0 - ��� ����)
	
	boolean bFinished = false;
	
	// ��������� �����
	byte DeviceId = 0; // ������������� ������ ��� ������ � ����������� ��������
	String szAddressToConnectTo; // IP-����� ��� ���������� �� GPRS
	int PortToConnectTo = 7265; // ����� ����� ��� ���������� � �������, "tc65"
	boolean ConnType = Tc65SocketConnection.CONN_TYPE_GPRS; // ��� ������������� ����������: CSD ��� GPRS
	String CSDPhoneNumber/* = "Not set"*/; // ����� ��� ���������� �� CSD
	String szAPN; // APN
	boolean OperationMode = Tc65SocketConnection.OP_MODE_CLIENT; // 0-slave (�� ������������� �� �������) 1-master
	int BaudRate = 9600; // �������� �����
	String szUsername; // ��� ������������ ��� �������� � ����
	String szPassword; // ������ ��� �������� � ����
	String PINCode; // PIN
	boolean DebugInfoToCOM = true; // �������� ���������� ���. � ���-����
	public int DisconnectTimeout = 0; // ������� �� ������������ �� ���������� ������, 0 - �� ����������
	
	
	public boolean UseModbus = true; // ����������� ����� � ������ � �������� �� ���-����� � ����� ���������?
	public int ModbusDelay = 500; // ����� ������� ����������� �������� ������ �������, ��� ���� ����� ������, � ���������� � ���-���� 
	
	boolean FillingBuffer = false; // �� ������ �����-�� ������ �� ������� ENTER
	StringBuffer strBuf; // ����� ��� ����� ��������� ������
	int idxInputBuffer = 0; // ������ ���������� ������� � ���� ������
	int NowFillingValue = 0; // ����� ���������, ������� �� ������ ������ � �����

	// ��-�������, ������� ���� ���������, ���� ProcessMenu() ���������� MS_HAVEATCMD
	String atCmd2Execute;
	
	public MenuSettings(Tc65SerialPort tc65SerialPort, ATCommand atCommand, Io io)
	{
		this.tc65SerialPort = tc65SerialPort;
		this.atCommand = atCommand;
//		this.io = io;
		
		strBuf = new StringBuffer(40);
		atCmd2Execute = new String();
		// ���� �� � ������ ���� ������� ������, �� ������ � �������������������� 
		// ������ ������� �����.
		szAddressToConnectTo = new String("gsmpager.ru");
		CSDPhoneNumber = new String("(not set)");
		PINCode = new String("(no)");
		szAPN = new String("internet"); // ��������� ��� ����������� ����������
		szUsername = new String("");
		szPassword = new String("");
		// ��������� �����
//        start(); - ��� �������� �������.
	}

		public void run() 
	{
		tc65SerialPort.serialOut("Press ENTER twice to see menu...\n\r");
		
		if(LoadFromFile() == false)
			tc65SerialPort.serialOut("\n\rError loading config file - please create and save a new one.\n\r");
		
		bFinished = false;
		int nLoops = 0;
		int nDelay = 0;
		// ���� ������� ENT�R, ������ �������, ��� ����� �������� � ����. 
		boolean bCanRunMenu = false;
		boolean bEnterPressed = false;
		
		try 
		{
			do
			{
				Thread.sleep(50);
				nDelay++;
				nLoops++;
				if(nDelay >= 20)
				{
					nDelay = 0;
				}
				if((nLoops%20) == 0 && bCanRunMenu == false)
					tc65SerialPort.serialOut(".");
				if(nLoops > 400 && bCanRunMenu == false)
					bFinished = true;
				int ch;
				if(tc65SerialPort.hasData() > 0)
				{
				    ch = tc65SerialPort.readByte();
					
				    // ���� ������ ENTER ��� ����, �� ������ � ����.
				    // ���� ����� �������� ���-�� ���� - �� ������.
				    if(ch == 0x0d || ch == 0x0a)
				    {
				    	if(bEnterPressed == false)
				    		bEnterPressed = true;
				    	else
				    		bCanRunMenu = true;
				    }
				    else
				    	bEnterPressed = false;
				    
				    if (bCanRunMenu == true) 
				    {
				    	if(ch >= '0' && ch <= '9')
				    		nLoops = 0; // ������ ���� 0-9 - ������ ������...
				    	RetCode = ProcessMenu((char)ch);
				    	
				    	if((RetCode == MS_EXITPROG) || (RetCode == MS_EXITMENU))
				    	{
				    		DebugOut("\nClosing Menu\r\n");
					    	close();
					    	break;
				    	}
				    	else
				    	{
				    		if(RetCode == MS_HAVEATCMD)
				    		{
				    			try {
				    				DebugOut(atCommand.send(atCmd2Execute));
								} catch (IllegalStateException e) {
									e.printStackTrace();
								} catch (IllegalArgumentException e) {
									e.printStackTrace();
								} catch (ATCommandFailedException e) {
									e.printStackTrace();
								}
				    		}
				    		else if(RetCode == MS_HAVEATCMD_ENTERPIN)
				    		{
				    			try {
				    				if(atCommand.send(atCmd2Execute).indexOf("OK") > 0)
				    					PinWasEntered = true;
								} catch (IllegalStateException e) {
									e.printStackTrace();
								} catch (IllegalArgumentException e) {
									e.printStackTrace();
								} catch (ATCommandFailedException e) {
									e.printStackTrace();
								}
				    		}
				    		else if(RetCode == MS_TESTCONNECTION)
				    		{
				    			TestConnection();
				    		}
				    			
				    	}
				    }
				}
				else // ���� ������ ������� �������� ������ ����
					ProcessMenu(MS_NODATA);
				
				// ������������ �� ���� �� ��������
				if(MenuTimeout >= 30)
				{
					if(MenuTimeout == 30)
					{
						ProcessMenu(MS_TIMEOUT);
						bFinished = true;
					}
					if(MenuTimeout >= 100)
						MenuTimeout = 31;
					CurrMenu = 0; // �� ������
				}
				
			}
			while(bFinished == false);
		} 
		catch (InterruptedException e) 
		{
//			e.printStackTrace();
			tc65SerialPort.serialOut("InterruptedException, closing MenuSettings thread");
			close();
		}
		
		tc65SerialPort.serialOut("\r\nClosing Menu.\r\n");
		
		bFinished = true;
		// ��������� ����� �� ��������� ������.
		interrupt(); 
	}
	
	public void close()
	{
		// ������� �����, ������ ��� ����� ������� ���-���� - 
		// � �������� ����� ������.
		// DebugOut("\n\rMenu done\n\r");
		bFinished = true;
	}
	
	public boolean IsFinished()
	{
		return bFinished;
	}

/**
 * � ������� - ��������� ���� (����� �� ������ ��� ���� ��� ������� - 0)
 *  1 - �������� ��������� 
 *  2 - �������� ���������
 *  	1 - �������� DeviceID
 *  	2 - �������� URL (���� �����������)
 *  	3 - �������� ���� (���� �����������)
 *  	4 - �������� ���� keepalive 
 *  	5 - �������� APN
 *  	6 - �������� username
 *  	7 - �������� password
 *  	8 - �������� �������� �����
 *  	9 - �������� PIN-���
 *  	A - �������� �������� ������ Modbus
 *  	B - ���������� ����� ��� ������� CSD
 *  	C - �������� �����: ������/������
 *  	K - ���/���� keepalive
 *  	F - ��������� ��������� � ����
 *  	M - ���/���� Modbus compatibility
 *  	D - �������� ���������� ���������� � ���-���� 
 *  	� - ������� � �������� �� ������� ����� ��� ���������� ������
 *  	0 - ����� �� ����
 *  6 - ����������� ������� PIN-���
 *  9 - ����� �� ����������
 *  0 - ����� �� ����
 *  ? - ������

 * @param NextEntered
 * @return
 */
	int ProcessMenu(char NextEntered)
	{
		int retval = MS_DONTEXIT; // ��� �������
		boolean StopProcessing = false; // ����� ������� false, ����� ����� ��� ���.
		
		if(NextEntered == MS_NODATA) // ���� ����� �� ����
		{
			StopProcessing = true;
			// ����� ���� ������������ ��������
		}
//		if(NextEntered == MS_TIMEOUT) // ���� ����� �� ����
//		{
//			StopProcessing = true;
//		}
		
		while(StopProcessing == false)
		{
			StopProcessing = true;
			MenuTimeout = 0; // ����� ��������, ���� ���-�� ������
			switch(CurrMenu)
			{
			// ******************* Menu 0-0 ******************
			case 0: // �� ���� ����
				if(NextEntered == 0x0d) // ������ Enter
				{
					CurrMenu = 1; // ��������� � ���� 1 � ������������ ������� Enter
					StopProcessing = false;
				}
				break;
			// ******************* Menu 0-1 ******************
			case 1: // �� � �������� ����
				switch(NextEntered)
				{
				case '1': //"1 - �������� ���������\n\r" 
					DisplaySettings();
					break;
				case '2': //"2 - �������� ���������\n\r"
					CurrMenu = 12;
					StopProcessing = false; // �������� ���� ����. ������
					NextEntered = 0x0d; // ���� ������ ENTER
					break;
				case '6': //"6 - try current PIN\n\r"
					CurrMenu = 6;
					StopProcessing = false; // �������� ���� ����. ������
					NextEntered = 0x0d; // ���� ������ ENTER
					break;
				case '9': //"9 - ����� �� ����\n\r"
					CurrMenu = 9;
					tc65SerialPort.serialOut("ARE YOU SURE? Press \"6\" to confirm.\n\r");
					break;
				case '0': //"0 - ����� �� ����\n\r"
					return MS_EXITMENU;
				case 0x0d: // Enter
					tc65SerialPort.serialOut(	"Rs2gprs menu\n\r" +
							"-----------------\n\r" +
							"1 - Display settings\n\r" +
							"2 - Change settings\n\r" +
							"6 - PIN menu\n\r" +
							"9 - Exit program\n\r" +
							"0 - Exit menu and continue normally\n\r\n\r");
					break;
				case '?': // ? - ������
					tc65SerialPort.serialOut(	"Help\n\r" +
							"-----------------\n\r" +
							"Use keyboard to change values. Help follows\n\r\n\r");
					break;
				default:
					tc65SerialPort.serialOut("Unknown command! Press Enter to display menu.\r\n");					
					break;
				}

				break;
				// ******************* Menu 0-6 ******************
			case 6:
				switch(NextEntered)
				{
				case '1': //1 - Enter current PIN
					// ���� ������ ���������� PIN, ��������� ������,
					// ��� ����� ������� ����� ����� ����� 3, � ���������� 
					// ������.
					if(PinWasEntered == true)
					{
						tc65SerialPort.serialOut("PIN code was already entered correctly.\r\n");
					}
					else
					{
						atCmd2Execute = "AT+CPIN=" + PINCode + "\r";
						retval = MS_HAVEATCMD_ENTERPIN;
					}
					break;
				case '0': //"0 - ����� �� ����\n\r"
					CurrMenu = 1; // ������� �� ������� ����
					NextEntered = 0x0d;
					StopProcessing = false;
					break;
				case 0x0d: // Enter
					tc65SerialPort.serialOut(	"PIN menu\n\r" +
							"-----------------------------------------------------------------\n\r" +
							"- ATTENTION. Please use this menu ONLY if your current PIN code -\n\r" +
							"- is NOT accepted. To change PIN code press 0, 2, 9.            -\n\r" +
							"- IMPORTANT. After entering correct PIN code please save it in  -\n\r" +
							"- local file system (press 0, 2, F).                            -\n\r" +
							"-----------------------------------------------------------------\n\r" +
							"Current PIN is: " + PINCode + "\n\r\n\r" +
							"1 - Try current PIN into SIM card\n\r" +
							"0 - Exit menu\n\r\n\r");
					break;
				default:
					tc65SerialPort.serialOut("Unknown command! Press Enter to display menu.\r\n");					
					break;
				}
				break;
			// ******************* Menu 0-9 ******************
			case 9: // ���� ������������� ������ �� �����
				if(NextEntered == '6') // ����� �����������
					return MS_EXITPROG;
				else
					CurrMenu = 1; // ����� �� �����������.
				break;

			// ******************* Menu 1-2 ******************
				
			case 12:
				// ���� �� ��������� ����� �������
				if(FillingBuffer)
				{
					// ���� �� ��������� ������
					if(NextEntered > ' ')
					{
						if(idxInputBuffer < 39) // ������ ������ ����� ������ �� 1
						{
							//InputBuffer[idxInputBuffer] = NextEntered;
							strBuf.append(NextEntered);
							idxInputBuffer++;
							//ByteArrayInputStream bais = new ByteArrayInputStream(InputBuffer); 
							tc65SerialPort.serialOut(NextEntered);
						}
						else // ����� ������ ������������, ����������� ����
						{
							FillingBuffer = false;
							//InputBuffer[idxInputBuffer] = 0;
							NextEntered = 0x0d; // ����� �������� ����
						}
					}
					else // ��������� ������ ��� ENTER
					{
						FillingBuffer = false;
						//InputBuffer[idxInputBuffer] = 0;
						NextEntered = 0x0d; // ����� �������� ����

						DebugOut("\n\rEntered " +  strBuf.toString() + "\n\r");

						// �������� � ������� ��, ��� �����
						switch(NowFillingValue)
						{
						case 1: //1 - �������� DeviceID
							if(idxInputBuffer > 0)
								DeviceId = Integer.valueOf(strBuf.toString(), 10).byteValue();
							break;
						case 2: //2 - �������� IP ������� (GPRS)
							if(idxInputBuffer > 0)
								szAddressToConnectTo = strBuf.toString();
							tc65SerialPort.serialOut("\n\rNew URL to connect to: " + szAddressToConnectTo + "\n\r");
							break;
						case 3://3 - �������� GPRS ����
							if(idxInputBuffer > 0)
								PortToConnectTo = Integer.valueOf(strBuf.toString(), 10).intValue();
							tc65SerialPort.serialOut("\n\rNew connection port to be used: " + 
									PortToConnectTo + "\n\r");
							break;
						case 4://4 - �������� ���� keepalive
							if(idxInputBuffer > 0)
							{
								int NewVal = Integer.valueOf(strBuf.toString(), 10).intValue();
								if(NewVal >= 0 && NewVal < 256)
								{
									KeepaliveByte = NewVal; 
									tc65SerialPort.serialOut("\n\rNew keepalive byte value: " + KeepaliveByte + "\n\r");
								}
								else
								{
									tc65SerialPort.serialOut("\n\rKeepalive byte value must be in range 0...255! Please try again\n\r");
								}
							}
							break;
						case 5://5 - APN
							if(idxInputBuffer > 0)
								szAPN = strBuf.toString();

							tc65SerialPort.serialOut("\n\rNew GPRS APN is: " + szAPN + "\n\r");
							break;
						case 6://6 - username
							if(idxInputBuffer > 0)
								szUsername = strBuf.toString();

							tc65SerialPort.serialOut("\n\rNew username is: " + szUsername + "\n\r");
							break;
						case 7://7 - password
							if(idxInputBuffer > 0)
								szPassword = strBuf.toString();

							tc65SerialPort.serialOut("\n\rNew password is: " + szPassword + "\n\r");
							break;
						case 8://8 - �������� �������� �����
							if(idxInputBuffer > 0)
							{
								int br = Integer.parseInt(strBuf.toString());
								if(br != 1200 && br != 2400 && br != 4800 && br != 9600 &&
										br != 19200 && br != 38400 && br != 57600 && br != 115200)
								{
									tc65SerialPort.serialOut("\n\rEntered baud rate " + br + " is not standard, not accepted.\n\r");
								}
								else
								{
									BaudRate = br;
									tc65SerialPort.serialOut("\n\rNew baud rate to be used: " + 
											BaudRate + "\n\r");
								}
							}
							break;
						case 9: // PIN
							if(idxInputBuffer > 0)
								PINCode = strBuf.toString();
							tc65SerialPort.serialOut("\n\rNew PIN code: " + PINCode + "\n\r");
							break;
						case 10: //10 - �������� �������� ������ Modbus
							if(idxInputBuffer > 0)
								ModbusDelay = Integer.valueOf(strBuf.toString(), 10).intValue();
							tc65SerialPort.serialOut("\n\rNew Modbus delay to be used: " + 
									ModbusDelay + "\n\r");
							break;
						case 11://B - �������� ����� CSD
							if(idxInputBuffer > 0)
								CSDPhoneNumber = /*InputBuffer.*/strBuf.toString();
							tc65SerialPort.serialOut("\n\rNew phone number to dial for CSD connection: " + CSDPhoneNumber + "\n\r");
							break;
						case 12://C - �������� �����: ������/������
							if(idxInputBuffer > 0)
							{
								int br = Integer.parseInt(strBuf.toString());
								if(br == 0)
									OperationMode = Tc65SocketConnection.OP_MODE_CLIENT;
								else OperationMode = Tc65SocketConnection.OP_MODE_SERVER;
							}
							tc65SerialPort.serialOut("\n\rModem will be: " + 
									((OperationMode == Tc65SocketConnection.OP_MODE_CLIENT)?"client":"server") + "\n\r");
							break;
						case 13://T - �������� �������
							if(idxInputBuffer > 0)
								DisconnectTimeout = Integer.parseInt(strBuf.toString());
							if(DisconnectTimeout <= 0)
								tc65SerialPort.serialOut("\n\r Will not disconnect on data timeout\n\r");
							else
								tc65SerialPort.serialOut("\n\rWill disconnect if no data comes in " + DisconnectTimeout + " seconds\n\r");
							break;
						}
					}
				}
				
				if(FillingBuffer == false)
				{
					idxInputBuffer = 0; // ���-�� ���� �������� ������... 
					switch(NextEntered)
					{
					case 0x0d:
						tc65SerialPort.serialOut(	"\n\rChange settings menu\n\r" +
								"-----------------\n\r" +
								"1 - Change device ID\n\r" +
								"2 - Change IP address or URL to connect to\n\r" +
								"3 - Change port number to connect to\n\r" +
								"4 - Change keepalive byte value\n\r" +
								"5 - Change APN\n\r" +
								"6 - Change username\n\r" +
								"7 - Change password\n\r" +
								"8 - Change module port baud rate\n\r" +
								"9 - Change PIN code\n\r" +
								"A - Change MODBUS accumulation delay (used if M=on)\n\r" +
								"B - Change phone number for CSD calls\n\r" +
								"C - Change operation mode: \n\r\t0 - client (connects to external server)," + 
									"\n\r\t1 - server (listens for incoming connections)\n\r" +
								"F - Save settings to file\n\r" +
								"K - Toggle keepalive usage\n\r" +
								"M - Toggle MODBUS compatibility\n\r" +
								"D - Show debug info in terminal\n\r" +
								"T - Timeout (disconnect if no data in this number of seconds)\n\r" + 
								"0 - Exit menu\n\r\n\r");
						break;
					case '1':
						NowFillingValue = 1;
						FillingBuffer = true;
						tc65SerialPort.serialOut("\n\rChange device ID\n\r" +
									 "-----------------------\n\r" +
								"Current device ID:" + DeviceId + 
								"\n\rEnter new device ID (1-127):\n\r");
						break;
					case '2':
						NowFillingValue = 2;
						FillingBuffer = true;
						tc65SerialPort.serialOut("\n\rChange URL to connect to over GPRS\n\r" +
									 "-------------------------------\n\r" +
								"Current URL:" + szAddressToConnectTo + 
						"\n\rEnter new URL (e.g.: www.yandex.ru or 12.34.56.78):\n\r");
						break;
					case '3':
						NowFillingValue = 3;
						FillingBuffer = true;
						tc65SerialPort.serialOut("\n\rChange port number to connect or listen\n\r" +
									 "----------------------------------\n\r" +
								"Current port number:" + PortToConnectTo + 
						"\n\rEnter new port number:\n\r");
						break;
					case '4':
						NowFillingValue = 4;
						FillingBuffer = true;
						tc65SerialPort.serialOut("\n\rChange keepalive byte value\n\r" +
									 "-------------------\n\r" +
								"Current keepalive byte value:" + KeepaliveByte + 
						"\n\rEnter new keepalive byte value:\n\r");
						break;
					case '5':
						NowFillingValue = 5;
						FillingBuffer = true;
						tc65SerialPort.serialOut("\n\rCnahge APN\n\r" +
									 "-------------------\n\r" +
								"Current APN:" + szAPN + 
						"\n\rEnter new APN:\n\r");
						break;
					case '6':
						NowFillingValue = 6;
						FillingBuffer = true;
						tc65SerialPort.serialOut("\n\rCnahge username\n\r" +
									 "-------------------\n\r" +
								"Current username:" + szUsername + 
						"\n\rEnter new username:\n\r");
						break;
					case '7':
						NowFillingValue = 7;
						FillingBuffer = true;
						tc65SerialPort.serialOut("\n\rCnahge password\n\r" +
									 "-------------------\n\r" +
								"Current password:" + szPassword + 
						"\n\rEnter new password:\n\r");
						break;
					case '8':
						NowFillingValue = 8;
						FillingBuffer = true;
						tc65SerialPort.serialOut("\n\rChange module COM port baud rate\n\r" +
									 "-------------------\n\r" +
								"Current baud rate:" + BaudRate + 
						"\n\rEnter new baud rate:\n\r");
						break;
					case '9':
						NowFillingValue = 9;
						FillingBuffer = true;
						tc65SerialPort.serialOut("\n\rChange PIN code\n\r" +
									 "-------------------\n\r" +
								"Current PIN code:" + PINCode + 
								"\n\rTo disable PIN sending, enter \"(no)\"\n\r" + 
						"\n\rEnter new PIN code:\n\r");
						break;
					case 'A': case 'a':
						NowFillingValue = 10;
						FillingBuffer = true;
						tc65SerialPort.serialOut("\n\rChange MODBUS accumulation delay\n\r" +
									                 "--------------------------------\n\r" +
								"Current delay:" + ModbusDelay + 
								"\n\rEnter new delay in ms (approximate):\n\r");
						break;
					case 'B': case 'b':
						NowFillingValue = 11;
						FillingBuffer = true;
						tc65SerialPort.serialOut("\n\rChange phone number for CSD calls\n\r" +
									 "-------------------\n\r" +
								"Current phone number for CSD calls:" + CSDPhoneNumber + 
						"\n\rEnter new phone number:\n\r");
						break;
					case 'C': case 'c':
						NowFillingValue = 12;
						FillingBuffer = true;
						tc65SerialPort.serialOut("\n\rChange operation mode\n\r" +
									 "-------------------\n\r" +
								"Current mode: " + ((OperationMode == Tc65SocketConnection.OP_MODE_CLIENT)?"client":"server") + 
						"\n\rEnter new mode (0=client, 1=server):\n\r");
						break;
					case 'F': case 'f':
						tc65SerialPort.serialOut("\n\rSave settings to file\n\r" +
									 "---------------------\n\r");
						SaveToFile();
						break;
					case 'K': case 'k':
						UseKeepalives = !UseKeepalives;
						tc65SerialPort.serialOut("\n\rToggle keepalive usage\n\r" +
									 "---------------------\n\r" +
									 "Keepalives " + (UseKeepalives == true ?"WILL":"WILL NOT") + " be sent");
						break;
					case 'M': case 'm':
						UseModbus = !UseModbus;
						tc65SerialPort.serialOut("\n\rToggle MODBUS compatibility\n\r" +
									                 "---------------------------\n\r" +
									 "Packet accumulation is " + (UseModbus == true ?"ON":"OFF"));
						break;
					case 'D': case 'd':
						DebugInfoToCOM = !DebugInfoToCOM;
						tc65SerialPort.serialOut("\n\rShow debug info in terminal\n\r" +
									 "-------------------\n\r" +
								"Show debug info = " + DebugInfoToCOM + "\n\r");
						break;
					case '0':
						CurrMenu = 1; // ������� �� ������� ����
						NextEntered = 0x0d;
						StopProcessing = false;
						break;
					case 'T': case 't':
						NowFillingValue = 13;
						FillingBuffer = true;
						tc65SerialPort.serialOut("\n\rChange data timeout\n\r" +
									                 "-------------------\n\r" +
								"Current timeout: " + DisconnectTimeout + 
						"\n\rEnter new timeout in seconds (0=do not disconnect):\n\r");
						break;
					default:
						tc65SerialPort.serialOut("Unknown command! Press Enter to display menu.\r\n\n\r");					
						break;
					}
					// ����� ��������� ����� ������?
					if(FillingBuffer == true)
						strBuf.delete(0, strBuf.length());
						
				} // else if(FillingBuffer)
				break;
			// ******************* default ******************
			default: // � ������ ������
				tc65SerialPort.serialOut("ERROR! Bad CurrMenu value.\n\r");
				CurrMenu = 0; 
				NextEntered = MS_NODATA;
				break;
			}
		}
		return retval;
	}

/**
 * �������� ������� ��������� ���������
 *
 */	
	void DisplaySettings()
	{
		tc65SerialPort.serialOut(	"Current settings\n\r" +
				"-----------------\n\r");
		if(OperationMode == Tc65SocketConnection.OP_MODE_CLIENT) // slave
		{
			tc65SerialPort.serialOut(
					"Acting as: client\n\r" +
					"Connects to " + szAddressToConnectTo + ":" + PortToConnectTo + "\n\r" +
					"APN: " + szAPN +"\n\r" +
					"Phone (CSD): " + CSDPhoneNumber + "\n\r" +
					"Username: " + szUsername + "\n\r" +
					"Password:" + szPassword + "\n\r" +
					"Use keepalives: " + (UseKeepalives?"YES":"NO") + "\n\r" + 
					"Accumulate MODBUS packets: " + (UseModbus?"YES":"NO") + "\n\r" 
					);
			if(UseModbus == true)
				tc65SerialPort.serialOut(
						"Accumulating MODBUS packets for: " + Integer.toString(ModbusDelay) + " ms\n\r");
		}
		else
		{
			tc65SerialPort.serialOut(
					"Acting as: server\n\r" +
					"Listens on port " + PortToConnectTo + "\n\r");
		}

	
		tc65SerialPort.serialOut( "PIN code: " + PINCode + "\n\r" +
			"Baudrate: " + BaudRate + "\n\r" +
			"Disconnect timeout: " + ((DisconnectTimeout <= 0)?"not used": Integer.toString(DisconnectTimeout) ) +
			"\n\r" );
	}
	
/**
 * ��������� ��������� �� �����
 */
	boolean LoadFromFile()
	{
		boolean bResult = false;
		try 
		{
			FileConnection fconn = (FileConnection)Connector.open("file:///a:/rs2gprs.dat", 
					Connector.READ);
			// If no exception is thrown, then the URI is valid, but the file may or may not exist.
			if (fconn.exists())
			{
				long size = fconn.fileSize();
//				DebugOut("\n\rrs2gprs.dat filesize="+size+"\n\r");
				if(size > 30 && size < 100)
				{
					DataInputStream dis = fconn.openDataInputStream();
					DeviceId = dis.readByte();
					szAddressToConnectTo = dis.readUTF();
					PortToConnectTo = dis.readInt();
					PINCode = dis.readUTF();
					OperationMode = dis.readBoolean();
					BaudRate = dis.readInt();
					DebugInfoToCOM = dis.readBoolean();
					szAPN = dis.readUTF();
					szUsername = dis.readUTF();
					szPassword = dis.readUTF();
					UseKeepalives = dis.readBoolean();
					KeepaliveByte = dis.readInt();
					UseModbus = dis.readBoolean();
					ModbusDelay = dis.readInt();
					CSDPhoneNumber = dis.readUTF();
					DisconnectTimeout = dis.readInt();
//					serialOut("DEBUG: Number1="+Number1 +"\n\r");
					dis.close();
					bResult = true;
				}
				else
					tc65SerialPort.serialOut("Probably wrong contents of settings file; using defaults\n\r");
			}
			else
				tc65SerialPort.serialOut("Settings file not found; using defaults\n\r");

			fconn.close();
		}
		catch (EOFException ioe) 
		{
			String s = new String("Premature end-of-file: ");
			s += ioe.getMessage();
			s += "\n\r";
			tc65SerialPort.serialOut(s);
		}
		catch (IOException ioe) 
		{
			tc65SerialPort.serialOut(new String("File read operation error: " + ioe.getMessage() + "\n\r"));
		}

		return bResult;
	}

	/**
	 * ��������� ��������� � ����
	 */
	void SaveToFile()
	{
		try 
		{
			FileConnection fconn = (FileConnection)Connector.open("file:///a:/rs2gprs.dat");
			// If no exception is thrown, then the URI is valid, but the file may or may not exist.
			
			if (!fconn.exists())
			{
				fconn.create();  // create the file if it doesn't exist
			}

			if (fconn.exists())
			{
				fconn.truncate(0);
				DataOutputStream dos = fconn.openDataOutputStream();
				dos.writeByte(DeviceId);
				dos.writeUTF(szAddressToConnectTo);
				dos.writeInt(PortToConnectTo);
				dos.writeUTF(PINCode);
				dos.writeBoolean(OperationMode);
				dos.writeInt(BaudRate);
				dos.writeBoolean(DebugInfoToCOM);
				dos.writeUTF(szAPN);
				dos.writeUTF(szUsername);
				dos.writeUTF(szPassword);
				dos.writeBoolean(UseKeepalives);
				dos.writeInt(KeepaliveByte);
				dos.writeBoolean(UseModbus);
				dos.writeInt(ModbusDelay);
				dos.writeUTF(CSDPhoneNumber);
				dos.writeInt(DisconnectTimeout);
				tc65SerialPort.serialOut("\n\rThe \"rs2gprs.dat\" file has been saved to module file system\n\r");
				dos.close();
			}
			else
				tc65SerialPort.serialOut("Could not create output file, settings not stored\n\r");
			
			fconn.close();
		}
		catch (IOException ioe) 
		{
			tc65SerialPort.serialOut("File write operation error: " + ioe.getMessage() + "\n\r");
		}
	}
	
	void DebugOut(String strMsg)
	{
		if(DebugInfoToCOM)
			tc65SerialPort.serialOut(strMsg);
	}
	
	public void TestConnection()
	{
		Tc65SocketConnection Sc;
		Sc = new Tc65SocketConnection(tc65SerialPort);
		boolean bConnType = ConnType;
		Sc.SetConnType(bConnType, Tc65SocketConnection.OP_MODE_CLIENT); // Testing connection as client always
		Sc.setPort(PortToConnectTo);
		Sc.setAPN(szAPN);
		Sc.setURL(szAddressToConnectTo);
		Sc.setUsername(szUsername);
		Sc.setPassword(szPassword);
		Sc.setCSDPhoneNumber(CSDPhoneNumber);
		Sc.Connect();

		if(Sc.IsConnected())
			tc65SerialPort.serialOut("Connected. Enter data or wait for timeout...\r\n");
		else 
		{
			tc65SerialPort.serialOut("Not connected\r\n");
			return;
		}
		
		int TimeCount = 0;
		while(Sc.IsConnected())
		{
			boolean Stop = false;
			try
			{
				Thread.sleep(50);
				while(tc65SerialPort.hasData() > 0)
				{
					int i = tc65SerialPort.readByte();
					if(i != -1)
					{
						byte b = (byte)(i);
						Sc.writeByte(b);
					}
				}
				while(Sc.hasData() > 0)
				{
					int i = Sc.readByte();
					if(i != -1)
					{
						byte b = (byte)i;
						tc65SerialPort.writeByte(b);
					}
				}
			}
			catch (InterruptedException e)
			{
				tc65SerialPort.serialOut("InterruptedException: " + e.getMessage());
				Stop = true;
			}
			TimeCount++;
			if(TimeCount > 500)
				Stop=true;
			if(Stop == true)
				break;
		}
		Sc.Disconnect();
		tc65SerialPort.serialOut("Disconnected\r\n");
	}
}

