package org.servalproject.dna;

import java.util.HashMap;
import java.util.Map;

public enum VariableType {
	EOR((byte) 0x00, "eor","Marks end of record"),
	CreateTime((byte) 0x01,"createtime","Time HLR record was created"),
	Creator((byte) 0x02,"creator","Device that created this HLR record"),
	Revision((byte) 0x03,"revision","Revision number of this HLR record"),
	Revisor((byte) 0x04,"revisor","Device that revised this HLR record"),
	Pin((byte) 0x05,"pin","Secret PIN for this HLR record"),

	  /* GSM encoded audio, so a 16KB MPHLR maximum size shouldn't
	     pose a problem.  8KB = ~4.5 seconds, which is a long time 
	     to say your name in, leaving 8KB for other variables. */
	VoiceSig((byte) 0x08,"voicesig","Voice signature of this subscriber"),

	HlrMaster((byte) 0x0f,"hlrmaster","Location where the master copy of this HLR record is maintained."),

	  /* Variables that can take multiple values */
	DIDs((byte) 0x80,"dids","Numbers claimed by this subscriber"),
	Locations((byte) 0x81,"locations","Locations where this subscriber wishes to receive calls"),
	IEMIs((byte) 0x82,"iemis","GSM IEMIs claimed by this subscriber"),
	TEMIs((byte) 0x83,"temis","GSM TEMIs claimed by this subscriber"),

	  /* Each entry here has a flag byte (unread, ...) */
	CallsIn((byte) 0x90,"callsin","Calls received by this subscriber"),
	CallsMissed((byte) 0x91,"callsmissed","Calls missed by this subscriber"),
	CallsOut((byte) 0x92,"callsout","Calls made by this subscriber"),

	SMSMessages((byte) 0xa0,"smessages","SMS received by this subscriber"),

	DID2Subscriber((byte) 0xb0,"did2subscriber","Preferred subscribers for commonly called DIDs"),

	HLRBackups((byte) 0xf0,"hlrbackups","Locations where backups of this HLR record are maintained."),

	Note((byte) 0xff,"note","Free-form notes on this HLR record");
	
	
	byte varId;
	String name;
	String description;
	VariableType(byte varId, String name, String description){
		this.varId=varId;
		this.name=name;
		this.description=description;
	}
	public boolean hasMultipleValues(){ return (varId&(byte)0x80)!=0;}
	
	static Map<Byte, VariableType> varByByte;
	static Map<String, VariableType> varByName;
	static{
		varByByte=new HashMap<Byte, VariableType>();
		varByName=new HashMap<String, VariableType>();
		for (VariableType v:VariableType.values()){
			varByByte.put(v.varId, v);
			varByName.put(v.name.toLowerCase(), v);
		}
	}
	
	static VariableType getVariableType(byte b){
		VariableType ret=varByByte.get(b);
		if (ret==null)
			throw new IllegalArgumentException("Variable type not found for byte "+b);
		return ret;
	}
	public static VariableType getVariableType(String b){
		VariableType ret=varByName.get(b.toLowerCase());
		if (ret==null)
			throw new IllegalArgumentException("Variable type not found for name "+b);
		return ret;
	}
}
