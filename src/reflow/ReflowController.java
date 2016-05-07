package reflow;

import java.io.IOException;

import com.fazecast.jSerialComm.*;

public class ReflowController {

	private static SerialPort port;
	private static Thread poller;
	
	private enum ReadState { RS_HEADER1, RS_HEADER2, RS_COMMAND, RS_DATA};

	private static final byte HDR_BYTE_1 = (byte)0xAA;
	private static final byte HDR_BYTE_2 = (byte)0x55;
	
	public enum Command { CMD_NONE, CMD_READ_TEMPERATURE, CMD_SET_DUTY_CYCLE, CMD_SET_SENSOR_OFFSET, CMD_SET_LCD_BACKLIGHT, CMD_SET_LCD_CONTRAST, CMD_READ_SETTINGS };   
	public enum Ack { ACK_ACK, ACK_NACK };
	public enum TempStatus { TS_UNKNOWN, TS_OK, TS_SCV_FAULT, TS_SCG_FAULT, TS_OC_FAULT };

	private byte[] packet_data = new byte[9];
	private int byte_idx = 0;
	private ReadState read_state = ReadState.RS_HEADER1;
	
	private int temperature = 0;
	private TempStatus temp_status = TempStatus.TS_UNKNOWN;
	private int sensor_offset;
	private int lcd_backlight;
	private int lcd_contrast;

	private int poll_interval = 1000;
	
	public static class CommandCallback {
	    void onResponse(Command cmd) {};
	}

	private CommandCallback on_response_cb = null;
	
	public ReflowController(String port_name) throws IOException {
		port = SerialPort.getCommPort(port_name);
		port.setBaudRate(9600);
		port.setFlowControl(SerialPort.FLOW_CONTROL_DISABLED);
		port.setNumDataBits(8);
		port.setNumStopBits(1);
		port.setParity(SerialPort.NO_PARITY);
		if (!port.openPort())
			throw new IOException("Can't open serial port");
		
		// Listen for incoming serial port data -------------------------------------------------------------------
		port.addDataListener(new SerialPortDataListener() {
			   @Override
			   public int getListeningEvents() { return SerialPort.LISTENING_EVENT_DATA_AVAILABLE; }
			   @Override
			   public void serialEvent(SerialPortEvent event)
			   {
			      if (event.getEventType() != SerialPort.LISTENING_EVENT_DATA_AVAILABLE)
			         return;
			      processSerialData();
			   }
		});
		
		// Poll temperature to keep the link up ------------------------------------------------------------------
		poller = new Thread() {
			public void run() {
				while (!isInterrupted()) {
					try {
						sendCommand(Command.CMD_READ_TEMPERATURE, (short)0);
						Thread.sleep(poll_interval);
					} catch (Exception e) {
					
					}
				}
	        }
		};
		
		sendCommand(Command.CMD_READ_SETTINGS, (short)0);
		
		poller.start();
	}
	
	public void disconnect() {
		poller.stop();
		port.closePort();
	}
	
	public void setDutyCycle(int c) {
		if (c < 0 || c > 100)
			return;
		sendCommand(Command.CMD_SET_DUTY_CYCLE, (short)0, (byte)c);
	}

	public int getSensorOffset() {
		return sensor_offset;
	}

	public void setSensorOffset(int sensor_offset) {
		if (sensor_offset < -127 || sensor_offset > 127)
			return;
		sendCommand(Command.CMD_SET_SENSOR_OFFSET, (short)0, (byte)sensor_offset);
		this.sensor_offset = sensor_offset;
	}

	public int getLcdBacklight() {
		return lcd_backlight;
	}

	public void setLcdBacklight(int lcd_backlight) {
		if (lcd_backlight < 0 || lcd_backlight > 100)
			return;
		sendCommand(Command.CMD_SET_LCD_BACKLIGHT, (short)0, (byte)lcd_backlight);
		this.lcd_backlight = lcd_backlight;
	}

	public int getLcdContrast() {
		return lcd_contrast;
	}

	public void setLcdContrast(int lcd_contrast) {
		if (lcd_contrast < 0 || lcd_contrast > 100)
			return;
		sendCommand(Command.CMD_SET_LCD_CONTRAST, (short)0, (byte)lcd_contrast);
		this.lcd_contrast = lcd_contrast;
	}

	public int getPollInterval() {
		return poll_interval;
	}

	public void setPollInterval(int poll_interval) {
		this.poll_interval = poll_interval;
	}

	public int getTemperature() {
		return temperature;
	}

	public TempStatus getTempStatus() {
		return temp_status;
	}

	public void setCommandCallback(CommandCallback on_response_cb) {
		this.on_response_cb = on_response_cb;
	}

	private void processSerialData() {
		byte[] db = new byte[1];
		while (port.bytesAvailable() > 0) {
			port.readBytes(db, 1);
			packet_data[byte_idx++] = db[0];
			switch(read_state) {
				case RS_HEADER1:
					if (packet_data[0] == HDR_BYTE_1) {
						read_state = ReadState.RS_HEADER2;
					} else {
						byte_idx = 0;
					}
				break;
				case RS_HEADER2:
					if (packet_data[1] == HDR_BYTE_2) {
						read_state = ReadState.RS_COMMAND;
					} else {
						read_state = ReadState.RS_HEADER1;
						byte_idx = 0;
					}
				break;
				case RS_COMMAND:
					if (byte_idx == 6) { // 6 bytes of command
						if (packet_data[2] == Command.CMD_READ_TEMPERATURE.ordinal() ||
							packet_data[2] == Command.CMD_READ_SETTINGS.ordinal()) {
							read_state = ReadState.RS_DATA;
						} else {
							onPacketRx();
							byte_idx = 0;
							read_state = ReadState.RS_HEADER1;
						}
					}
				break;
				case RS_DATA:
					if (byte_idx == 9) {
						onPacketRx();
						byte_idx = 0;
						read_state = ReadState.RS_HEADER1;
					}
				break;
			}
		}
	}
	
	private void onPacketRx() {
		System.out.format("Got Packet %02X, data: %02X\n", packet_data[2], packet_data[6]);
		if (Ack.values()[packet_data[5]] == Ack.ACK_NACK) 
			return;
		
		Command cmd = Command.values()[packet_data[2]]; 
		
		switch (cmd) {
			case CMD_READ_TEMPERATURE:
				temperature = (((int)packet_data[6]) & 0xFF) + ((((int)packet_data[7]) & 0xFF) << 8);
				temp_status = TempStatus.values()[packet_data[8]];
			break;
			case CMD_READ_SETTINGS:
				sensor_offset = packet_data[6];
				lcd_backlight = packet_data[7];
				lcd_contrast = packet_data[8];
			break;
			default:
		}
		
		if (on_response_cb != null)
			on_response_cb.onResponse(cmd);
	}

	private void sendCommand(Command cmd, short seq) {
		byte[] command_bytes = {HDR_BYTE_1, HDR_BYTE_2, (byte)cmd.ordinal(), (byte)(seq & 0xFF), (byte)(seq >> 8)};
		port.writeBytes(command_bytes, 5);
	}

	private void sendCommand(Command cmd, short seq, byte param) {
		byte[] command_bytes = {HDR_BYTE_1, HDR_BYTE_2, (byte)cmd.ordinal(), (byte)(seq & 0xFF), (byte)(seq >> 8), param};
		port.writeBytes(command_bytes, 6);
	}
}