package org.servalproject.dna;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

public class OpSimple implements Operation {
	enum Code{
		Create((byte)0x0f),
		Declined((byte)0x80),
		Done((byte)0x7e),
		Ok((byte)0x81),
		Eot((byte)0xff);
		byte code;
		Code(byte code){
			this.code=code;
		}
	}
	private static Map<Byte, Code> codes=new HashMap<Byte, Code>();
	static{
		for (Code c:Code.values()){
			codes.put(c.code, c);
		}
	}
	public static Code getCode(byte b){
		return codes.get(b);
	}
	Code code;
	
	OpSimple(){}
	public OpSimple(Code code){
		this.code=code;
	}
	
	@Override
	public void parse(ByteBuffer b, byte code) {
		this.code=getCode(code);
	}

	@Override
	public void write(ByteBuffer b) {
		b.put(code.code);
	}
	
	@Override
	public boolean visit(Packet packet, OpVisitor v) {
		return v.onSimpleCode(packet, code);
	}

	@Override
	public String toString() {
		return "SimpleCode: "+code.name();
	}
}
