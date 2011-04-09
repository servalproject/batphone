package org.servalproject.dna;

import java.nio.ByteBuffer;

public class OpWrote implements Operation {
	VariableRef varRef;
	
	OpWrote(){}
	
	static byte getCode(){return (byte)0x83;}
	
	@Override
	public void parse(ByteBuffer b, byte code) {
		varRef=new VariableRef(b);
		
		// work around bug in C dna
		if (!varRef.varType.hasMultipleValues())
			b.get();
	}

	@Override
	public void write(ByteBuffer b) {
		b.put(getCode());
		varRef.write(b);
		
		// work around bug in C dna
		if (!varRef.varType.hasMultipleValues())
			b.put((byte)0);
	}

	@Override
	public boolean visit(Packet packet, OpVisitor v) {
		return v.onWrote(packet, varRef);
	}

	@Override
	public String toString() {
		return "Wrote: "+varRef;
	}
}
