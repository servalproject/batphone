package org.servalproject.dna;

import java.nio.ByteBuffer;
import java.util.Date;

import org.servalproject.Instrumentation;

public class OpStat implements Operation {
	Instrumentation.Variable field;
	int value;
	Date modified;
	
	OpStat(){}
	public OpStat(Date modified, Instrumentation.Variable field, int value){
		this.modified=modified;
		this.field=field;
		this.value=value;
	}
	
	static byte getCode(){return (byte)0x40;}
	
	@Override
	public void parse(ByteBuffer b, byte code) {
		this.field=Instrumentation.getVariable(b.getShort());
		this.value=b.getInt();
	}

	@Override
	public void write(ByteBuffer b) {
		b.put(getCode());
		b.putShort(field.code);
		b.putInt(value);
	}

	@Override
	public boolean visit(Packet packet, OpVisitor v) {
		return v.onStat(packet, this.field, this.value);
	}

	@Override
	public String toString() {
		return "Stat: "+field+"="+value;
	}
}
