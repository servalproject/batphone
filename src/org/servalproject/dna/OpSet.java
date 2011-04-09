package org.servalproject.dna;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

public class OpSet implements Operation {
	VariableType varType;
	byte instance;
	short offset;
	short len;
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
		this.varType=varType;
		this.instance=instance;
		this.offset=offset;
		this.len=(short)value.remaining();
		this.flag=flag;
		this.value=value;
	}
	
	static byte getCode(){return (byte)0x01;}
	
	@Override
	public void parse(ByteBuffer b, byte code) {
		this.varType=VariableType.getVariableType(b.get());
		if (this.varType.hasMultipleValues()){
			this.instance=b.get();
		}else
			this.instance=-1;
		this.offset=b.getShort();
		this.len=b.getShort();
		this.flag=flags.get(b.get());
		this.value=Packet.slice(b,(int)len & 0xffff);
	}

	@Override
	public void write(ByteBuffer b) {
		b.put(getCode());
		b.put(this.varType.varId);
		if (this.varType.hasMultipleValues()){
			b.put(this.instance);
		}
		b.putShort(this.offset);
		b.putShort(this.len);
		b.put(this.flag.code);
		this.value.rewind();
		b.put(this.value);
	}

	@Override
	public boolean visit(Packet packet, OpVisitor v) {
		return v.onSet(packet, this);
	}

	@Override
	public String toString() {
		return "Set: "+varType.name+", "+(this.varType.hasMultipleValues()?instance+", ":"")+offset+", "+len+", "+flag.name()+", "+(value==null?"null":"\n"+Test.hexDump(value));
	}
}
