package org.servalproject.dna;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import org.servalproject.dna.OpSimple.Code;

public class OpDT implements Operation {
	enum DTtype{
		SMS((byte)0x00),
		TwitterMessage((byte)0x01);
		
		byte dtType;
		DTtype(byte dtType){
			this.dtType=dtType;
		}
	}
	private static Map<Byte, DTtype> dtTypes=new HashMap<Byte, DTtype>();
	static{
		for (DTtype t:DTtype.values()){
			dtTypes.put(t.dtType, t);
		}
	}
	public static DTtype getType(byte b){
		return dtTypes.get(b);
	}
	
	DTtype messageType;
	String message;
	String emitterPhoneNumber;
	String recipientPhoneNumber;
	
	OpDT(){}
	public OpDT(String message, String emitterPhoneNumber, DTtype messageType){
		this.messageType=messageType;
		// Length of the 2 next strings are stored in a byte, so we cut them if their length > 255
		if (emitterPhoneNumber.length()>255)
			this.emitterPhoneNumber=emitterPhoneNumber.substring(0, 255);
		else
			this.emitterPhoneNumber=emitterPhoneNumber;
		if (message.length()>255)
			this.message=message.substring(0, 255);
		else
			this.message=message;
	}
	
	static byte getCode(){return (byte)0x04;}
	
	@Override
	public void parse(ByteBuffer b, byte code) {
		this.messageType = getType(b.get());
		int emitterPhoneNumberLen =		((int)b.get())&0xff;
		int messageLen =				((int)b.get())&0xff;
		this.emitterPhoneNumber = new String(b.array(), b.arrayOffset()+b.position(), emitterPhoneNumberLen);
		b.position(b.position()+emitterPhoneNumberLen);
		this.message = new String(b.array(), b.arrayOffset()+b.position(), messageLen);
		b.position(b.position()+messageLen);
	}

	@Override
	public void write(ByteBuffer b) {
		b.put(getCode());
		b.put(messageType.dtType);
		b.put((byte)emitterPhoneNumber.length());
		b.put((byte)message.length());
		b.put(emitterPhoneNumber.getBytes()); 
		b.put(message.getBytes());
	}

	@Override
	public boolean visit(Packet packet, OpVisitor v) {
		return v.onDT(packet, this.messageType, this.emitterPhoneNumber, this.message);
	}

	@Override
	public String toString() {
		return "DT type "+messageType+" from "+emitterPhoneNumber+" : '"+message+"'";
	}
}
