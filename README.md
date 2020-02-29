# rs2gprs
Connect remote RS-232/485-enabled devices over GPRS using Siemens TC65 modem

(from dkorobkov, Feb 2020: I am surprised why this ancient project is not on GitHub yet. In 2020 it still helps some people in Europe to poll their remote equipment etc so I am putting out this mammoth shitcake. For years it is hosted on www.gsmpager.ru so this is just a mirror)

There are lots of tasks in the world around us that need wireless connection to various devices having RS-232 or RS-485 interface (probably these devices speak Modbus/RTU protocol). These can be various water, gas, heat or electric meters, text displays aside roads, remote weather stations and much, much more. You can, for example, install a GPS receiver in your car, connect it to your home PC and see your car's location on any navigation program in realtime, which is very helpful if it is stolen, or you just want to spy your girl's location :)
These tasks can be easily completed using Cinterion TC65 standalone modem, rs2gprs applet loaded into this modemm and com0com project (you will need com2tcp from here ).
The applet can be set up using any serial terminal connected to modem at 115200-8-n-1-none
 
 <img src="https://github.com/dkorobkov/rs2gprs/blob/master/rs2gprs640.gif" align="right" width="640" height="480" border="1" alt="Data exchange over GPRS using Siemens/Cinterion TC65">
 
 
GPRS connection may introduce significant delays into communication. That's why not all ready-to-use programs for querying serial devices will work remotely. Tthis is especially true for devices that use Modbus/RTU for communication as it does not allow long delays between bytes in packet. The latest version of applet supports Modbus/RTU by adding buffers for incoming and outgoing serial data on modem side so it made such communications possible. Delay for filling buffers is adjustable through applet setup menu.
Connecting serial devices over GPRS for free

As said above, this applet can be used for car or other vehicle location. You need to install GPS receiver with RS-232 output connected to Cinterion TC65 with rs2gprs applet running. Modem will send data from GPS receiver to a PC and it will act right as if GPS receiver would being connected directly to a PC. You can run an excellent Ozi Explorer to see all movements and collect tracks of your vehicle. 
