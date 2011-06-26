package org.servalproject.dna;

import java.nio.ByteBuffer;

public class OpGet implements Operation {
	VariableRef varRef;
	
	OpGet(){}
	public OpGet(VariableType varType, byte instance, short offset){
		this.varRef=new VariableRef(varType,instance,offset,(short)0);
	}
	
	static byte getCode(){return (byte)0x00;}
	
	public void parse(ByteBuffer b, byte code) {
		this.varRef=new VariableRef(b);
		this.varRef.len=0;
	}

	public void write(ByteBuffer b) {
		b.put(getCode());
		this.varRef.write(b);
	}

	public boolean visit(Packet packet, OpVisitor v) {
		return v.onGet(packet, this.varRef);
	}

	@Override
	public String toString() {
		return "Get: "+varRef;
	}
}
