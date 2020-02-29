package rs2gprs;

import com.siemens.icm.io.ATCommand;
import com.siemens.icm.io.ATCommandFailedException;


 public class Io {
	public final boolean CONN_TYPE_GPRS = true;
	// ����������� ����� ����� �����-������
	final boolean arDirOut[] = {
			true, true, true, false, false, // IO0-4 
			false, true, true, true, false  // IO5-10
			};
	
	ATCommand atCommand;

	/**
	 * @param atCommand
	 */
	public Io() {
		try
		{
			atCommand = new ATCommand(false);
			Init();
		}
		catch(ATCommandFailedException e)
		{
			System.out.print(e.toString());
		}
	}
	
	protected void Init()
	{
		try
		{
			atCommand.send("AT^SPIO=0\r"); // ��������� ���� I/O (� ��������� ���������)
			Thread.sleep(200);
			atCommand.send("AT^SPIO=1\r"); // �������� ���� I/O
			Thread.sleep(200);
			for(int i=0; i<10; i++)
			{
				if(arDirOut[i] == true)
					atCommand.send("AT^SCPIN=1,"+i+",1,0\r"); // ���. GPIO(i+1), out, init_state=0
				else
					atCommand.send("AT^SCPIN=1,"+i+",0\r"); // ���. GPIO(i+1), in
				Thread.sleep(200);
			}
		}
		catch(Exception e)
		{
			System.out.print(e.toString());
		}
	}
	
	// ���������� ��������� GPIO8 - ��������� �� ����������
	public boolean In(int IoNum)
	{
		// ��������� �������� ������
		if(IoNum < 0 || IoNum > 9)
			return false; 
		// ���� ���� ��������� ��� ����� - ������ �� ������.
		if(arDirOut[IoNum] == true)
			return false;
		
		String strRet = new String();
		String at = new String();
		at = "AT^SGIO=" + IoNum + "\r";

		try
		{
			strRet = atCommand.send(at);
		}
		catch(Exception e)
		{
			System.out.print(e.toString());
		}
		if(strRet.indexOf('1') > 0)
			return true;
		return false;
	}

	public void Out(int IoNum, boolean bState)
	{
		// ���������, ����� �� �������� ������ � ���� ����
		if(IoNum < 0 || IoNum > 9)
			return;
		// ���� ���� ��������� ��� ���� - ������ �� ������.
		if(arDirOut[IoNum] == false)
			return;

		String at;
		at = new String();
		
		at = "AT^SSIO=" + IoNum + "," + ((bState==true)?"1":"0") + "\r";
		try
		{
			atCommand.send(at);
		}
		catch (ATCommandFailedException e) {e.printStackTrace();}		
	}
}
