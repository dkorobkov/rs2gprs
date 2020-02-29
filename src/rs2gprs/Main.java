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
	
	ATCommand		atCommand; // Создаем его здесь и раздаем разным членам.
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
			// socketConnection создадим позже
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

		// Показываем, что соединение не установлено
		io.Out(0, false);
		
		// Ждем, пока меню разговаривает
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

		// Показываем, что отладочный вывод включен и будет мешать обмену 
		// данными - на это наступил человек. Если он выключен - ничего не будет видно и всё 
		// будет ОК.
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
			// Вводим PIN. Если ошибка - выходим.
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
		// Настраиваем СОМ-порт на заданную скорость
		tc65SerialPort.SetBaudRate(menuSetting.BaudRate);
		
		DebugOut("Creating Tc65SocketConnection...\r\n");    		
		socketConnection = new Tc65SocketConnection(tc65SerialPort);
		socketConnection.bShowDebug = menuSetting.DebugInfoToCOM;
		
		// Дадим нормально создаться socketConnection-у 
		Thread.yield();
		
		byte ar[] = new byte[2048]; // Массив для передаваемых данных
		int pos = 0; // положение в массиве

//		 Массив для данных из сокета, которые надо послать в СОМ-порт без задержки.
		// Организован как кольцевой буфер.
		byte arFromSocket[] = new byte[256];  
		int posFromSocketR = 0; 
		int posFromSocketW = 0; 
		
		boolean bExit = false;
		int IncomingDataTimer = 0; // Считаем время после последнего пришедшего из сокета байта.
		int IncomingKeepaliveTimer = 0; // Считаем время между пришедшими кипалайвами
		int KeepaliveCount = 0;
		int ReportListeningAtCount = 0;
		
		// *************************************************************************
		// Почему-то здесь нельзя применять тип long, как только его применяешь, прога 
		// перестает говорить через СОМ-порт!!!
		// **************************************************************************

		
		// ставим настройки
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
        	// Если мы сервер, то мы быстро крутимся по этому малому кругу, пока не придёт входящее соединение
        	// Если клиент - то немного ждём.
        	if(menuSetting.OperationMode == Tc65SocketConnection.OP_MODE_CLIENT) {
				try {Thread.sleep(5000);} catch (Exception e) {}				
				// Инициализируем модем
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
	        		
    		// соединяемся
    		boolean bConnected = socketConnection.Connect();
    		// Если соединились, показываем большую букву
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
       		// гоняем данные до разъединения
    		/*
    		 * При работе через жопорез даунстрим (из инета в модем) работает намного быстрее, чем апстрим.
    		 * Так сделано специально - но у нас из-за этого большой геморрой. В частности, канал от модема в инет
    		 * затыкается, если по нему идёт мало данных. 
    		 * Чтобы обойти это недоразумение, будем слать наверх очень много кипалайвов, но не всё время, а 
    		 * только когда нам оттуда что-то придёт (чтобы не тратить трафик) и в течение нескольких секунд после этого.  
    		 * Т.е. когда нас опрашивают, мы шлём наверх кучу хлама, чтобы поддержать канал активным, когда не
    		 * опрашивают - молчим. 
    		 * Также шлём кипалайвы, если к нам идут даныне из СОМ-порта и мы собираемся что-то послать наверх, но 
    		 * ещё не набрали пакет для отправки.
    		 * Если надо послать наверх байт данных, равный кипалайву, вместо него шлём magic packet - длинную корягу
    		 * с известным содержимым, которая вряд ли встретится в настоящем трафике. А на том конце придётся эту 
    		 * корягу отловить и заменить на байт данных (т.е. надо использовать специальную версию com0tcp).
    		 * С того конца кипалайв тоже приходит в виде magic packet - и мы на него отвечаем своим кипалайвом, 
    		 * чтобы подтвердить, что мы живы.
    		 */
   			IncomingDataTimer = millisNow(); // Считаем время после последнего пришедшего из сокета байта.
   			IncomingKeepaliveTimer = IncomingDataTimer; // Считаем время между пришедшими кипалайвами
   			KeepaliveCount = IncomingDataTimer;
   			boolean bPreviousByteWasKeepalive = false; // Был ли предыдущий байт кипалайвом

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
   				
   				// Если недавно пришли даныне из сокета, надо возбудить канал от модема в интернет.
   				// Для этого плюём туда кучу кипалайвов.
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
   					// Пока мы дожидаемся сбора пакета Modbus, шлём немного кипалайвов, чтобы пробудить канал, 
   					// но только если у нас что-то есть в СОМ-порту.
   					if(menuSetting.UseKeepalives == true)
   					{
   						if(tc65SerialPort.hasData() > 0) // мы скоро будем посылать реальные данные...
   						{
   							byte ab[] = {(byte)menuSetting.KeepaliveByte, (byte)menuSetting.KeepaliveByte, 
   									(byte)menuSetting.KeepaliveByte, (byte)menuSetting.KeepaliveByte};
   							socketConnection.write(ab, 4);
   						}
   					}
   					
	   				// Получаем из СОМ-порта модема и пишем в сокет
					while(tc65SerialPort.hasData() > 0)
					{
						IncomingSerialDataTimer = millisNow();
						int j = tc65SerialPort.readByte();
						if(j != -1)
						{
							byte b = (byte)(j);
							ar[pos]=b;
							pos++;
	
							// Если в данных попался кипалайвный байт, передаем magic sequence. На том конце её придётся заменить обратно.
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
	        				
				// Шлем кипалайвы (не 1 байт, который не пролезает через канал, а 10)
				if( (millisNow() - KeepaliveCount) > 30000)  // > 30 секунд - line fixed in 120806
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
	        				
				// Получаем из сокета и шлем в порт, если можно.  
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

					// Разбираемся, либо пришел байт кипалайва 0xYZ, либо байт данных 0xYZ, 
					// котороый передается как 0xYZYZ, либо другой байт.
					
					if(j != menuSetting.KeepaliveByte) // Очередной байт - не ff, забываем о кипалайве
					{
						bPreviousByteWasKeepalive = false;
						IncomingKeepaliveTimer = millisNow();
					}
					
					if(menuSetting.UseKeepalives == true && j == menuSetting.KeepaliveByte)
					{
						// Предыдущий байт был FF и пришел давно (> 5 c)?
						if(bPreviousByteWasKeepalive == true && (millisNow() - IncomingKeepaliveTimer) > 5000) // fixed in 120806 - added millisNow()
						{
							// Возможно, пришел кипалайв либо байт FF
							bPreviousByteWasKeepalive = true; // Это и так стоит.
							IncomingKeepaliveTimer = millisNow(); // Сбрасываем таймер
							bProceed = false;
						}	        						
						// Предыдущий байт - не FF?
						else if(bPreviousByteWasKeepalive == false) 
						{
							// Возможно, пришел кипалайв либо байт FF
							bPreviousByteWasKeepalive = true; // Запоминаем, что это FF
							IncomingKeepaliveTimer = millisNow(); // Сбрасываем таймер
							bProceed = false; // не посылаем
						}
						else if(bPreviousByteWasKeepalive == true && (millisNow() - IncomingKeepaliveTimer) <= 5000) // fixed in 120806 - added millisNow()
						{
							// Вторй раз пришел байт FF - значит, это реальный байт для посылки
							bPreviousByteWasKeepalive = false; 
							bProceed = true;
						}
						
					}
					if(bProceed == true)
					{
						// Раньше мы посылали в порт, сейчас положим в буфер и потом отправим в порт
						// всё вместе. И ЭТО ПОМОГЛО МОДБАСУ НОРМАЛЬНО РАБОТАТЬ В ОБЕ СТОРОНЫ!!!
						arFromSocket[posFromSocketW] = (byte)j;
						posFromSocketW++;
						if(posFromSocketW >= 256) posFromSocketW = 0;
					}

					// Смотрим, есть ли еще что-то в сокете. Нет - цикл прекращается.
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
				
// Дисконнект по таймауту данных и кипалайвов - если из сокета ничего не пришло.
// Если идет много данных, то икпалайвы не идут (это нормально) -
// но вываливаться при этом не надо! 

				if(menuSetting.DisconnectTimeout > 0) // если таймауты разрешены
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
       		
    		// дополнительно разъединяемся руками
    		socketConnection.Disconnect();
 
    		io.Out(0, false);
			DebugOut("*D*");
			
			ReinitModem();
			
			// В даташите написано, что реконнект лучше делать не раньше, 
			// чем через 2 минуты. 
			try {Thread.sleep(60000);} catch (Exception e) {}			
        }
         
        // В принципе, сюда мы не попадем никогда.
        socketConnection.StopListen();
        
		// Настраиваем СОМ-порт обратно на 115200
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
		
		// Если ПИН не цифровой - это не пин (или не настроен).
		// По умолчанию его не надо ставить.
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

	// Чтобы в проге не нужно было использовать глюкавый long, делаем такую функцию.
	public int millisNow()
	{
		return (int)System.currentTimeMillis();
	}
	
	private void ReinitModem()
	{
    	// Здесь можно переинициализировать модем, иногда 
    	// это помогает для более стабильной работы
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
