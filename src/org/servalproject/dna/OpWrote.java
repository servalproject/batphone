package org.servalproject.dna;

import java.nio.ByteBuffer;

public class OpWrote implements Operation {
	VariableRef varRef;
	
	OpWrote(){}
	public OpWrote(VariableRef varRef){
		this.varRef=varRef;
	}
	
	static byte getCode(){return (byte)0x83;}
	
	public void parse(ByteBuffer b, byte code) {
		varRef=new VariableRef(b);
		
		// work around bug in C dna
		if (!varRef.varType.hasMultipleValues())
			b.get();
	}

	public void write(ByteBuffer b) {
		b.put(getCode());
		varRef.write(b);
		
		// work around bug in C dna
		if (!varRef.varType.hasMultipleValues())
			b.put((byte)0);
	}

	public boolean visit(Packet packet, OpVisitor v) {
		return v.onWrote(packet, varRef);
	}

	@Override
	public String toString() {
		return "Wrote: "+varRef;
	}
}
