package rs2gprs;

import java.io.IOException;

import java.io.InputStream;
import java.io.OutputStream;

import javax.microedition.io.CommConnection;
import javax.microedition.io.Connector;

// If you use Siemens R3 or Cinterion WTK you can uncomment lines starting with "//R3" 
// to get DSR on/off functionality.

public class Tc65SerialPort 
{
	CommConnection	commConn;
	InputStream   	inStream;
	OutputStream  	outStream;
//R3		
	com.siemens.icm.io.CommConnectionControlLines ccControlLines;

	public Tc65SerialPort()
	{
        try 
        {
//			String strCOM = "comm:com0;blocking=on;baudrate=115200";
			String strCOM = "comm:com0;blocking=on;baudrate=115200;autocts=off;autorts=off";
			commConn = (CommConnection)Connector.open(strCOM);
			inStream  = commConn.openInputStream();
			outStream = commConn.openOutputStream();
//R3			
			ccControlLines = (com.siemens.icm.io.CommConnectionControlLines)commConn;
		} 
        catch (IOException e) 
        {
			e.printStackTrace();
		}
        serialOut('#'); // show we are alive
	}

	public boolean Open()
	{
		boolean bOpen = false;
        try 
        {
			String strCOM = "comm:com0;blocking=on;baudrate=115200;autocts=off;autorts=off";
			commConn = (CommConnection)Connector.open(strCOM);
			inStream  = commConn.openInputStream();
			outStream = commConn.openOutputStream();
			bOpen = true;
		} 
        catch (IOException e) 
        {
			e.printStackTrace();
		}
        
        return bOpen;
	}

	public void SetBaudRate(int baudrate)
	{
		commConn.setBaudRate(baudrate);
	}
	
	public void Close()
	{
		try{inStream.close();}catch (IOException e){e.printStackTrace();}
		try{outStream.close();}catch (IOException e){e.printStackTrace();}
		try{commConn.close();}catch (IOException e){e.printStackTrace();}
	}
	
	/**
	 * Вывод информации в СОМ-порт
	 * @param s - String
	 */
	public void serialOut(String s)
	{
		serialOut(s.getBytes());
	}
	/**
	 * Вывод информации в СОМ-порт
	 * @param b - byte[] array
	 */
	public void serialOut(byte[] b)
	{
		try
		{
			outStream.write(b);
		}
		catch(IOException e)
		{
			System.out.println(e);
		}
	}

	/**
	 * Вывод отладочной информации в СОМ-порт
	 * @param b - byte[] array
	 */
	public void serialOut(char c)
	{
		try
		{
			outStream.write(c);
		}
		catch(IOException e)
		{
			System.out.println(e);
		}
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
		}
		
		return n;
	}
	
	public void DSR(boolean bOn)
	{
//R3 		
		try {ccControlLines.setDSR(bOn);} catch (IOException e) {}
	}
	
	int readByte()
	{
		int NextByte = -1;
		try {
			NextByte = inStream.read();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
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
			e.printStackTrace();
			bOk = false;
		}
		return bOk;
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
		}
		return bOk;
	}
}
