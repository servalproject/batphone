package org.servalproject.dna;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

public class OpSet implements Operation {
	VariableRef varRef;
	Flag flag;
	ByteBuffer value=null;
	
	enum Flag{
		None((byte)0),
		NoReplace((byte)1),
		Replace((byte)2),
		NoCreate((byte)3),
		Fragment((byte)0x80);
		
		byte code;
		Flag(byte code){
			this.code=code;
		}
	}
	
	private static Map<Byte, Flag> flags=new HashMap<Byte, Flag>();
	static{
		for (Flag f:Flag.values()){
			flags.put(f.code, f);
		}
	}
	OpSet(){}
	public OpSet(VariableType varType, byte instance, short offset, Flag flag, ByteBuffer value){
		if (varType.hasMultipleValues()&&instance==(byte)-1)
			throw new IllegalArgumentException("You must specify an instance for variable "+varType.name());
		this.varRef=new VariableRef(varType,instance,offset,(short)value.remaining());
		this.flag=flag;
		this.value=value;
	}
	
	static byte getCode(){return (byte)0x01;}
	
	@Override
	public void parse(ByteBuffer b, byte code) {
		this.varRef=new VariableRef(b);
		this.flag=flags.get(b.get());
		this.value=Packet.slice(b,(int)varRef.len & 0xffff);
	}

	@Override
	public void write(ByteBuffer b) {
		b.put(getCode());
		varRef.write(b);
		b.put(this.flag.code);
		this.value.rewind();
		b.put(this.value);
	}

	@Override
	public boolean visit(Packet packet, OpVisitor v) {
		return v.onSet(packet, varRef, flag, value);
	}

	@Override
	public String toString() {
		return "Set: "+varRef+", "+flag.name()+", "+(value==null?"null":"\n"+Test.hexDump(value));
	}
}
