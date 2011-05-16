package org.servalproject.dna;

import java.io.IOException;
import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class Packet {
	static final short dnaPort=4110;
	
	final long transactionId;
	long timestamp;
	
	private SubscriberId sid=null;
	private String did=null;
	private boolean didFlag;
	public List<Operation> operations=new ArrayList<Operation>();
	public SocketAddress addr;
	private final static int SID_SIZE=32;
	private final static short magicNumber=0x4110;
	private final static short packetVersion=1;
	
	static Random rand = new Random();
	
	private static Map<Byte, Class<? extends Operation>> opTypes;
	static{
		opTypes=new HashMap<Byte, Class<? extends Operation>>();
		opTypes.put(OpGet.getCode(), OpGet.class);
		opTypes.put(OpSet.getCode(), OpSet.class);
		opTypes.put(OpError.getCode(), OpError.class);
		opTypes.put(OpPad.getCode(), OpPad.class);
		opTypes.put(OpData.getCode(), OpData.class);
		opTypes.put(OpWrote.getCode(), OpWrote.class);
		opTypes.put(OpDone.getCode(), OpDone.class);
		opTypes.put(OpDT.getCode(), OpDT.class);
		
		for (OpSimple.Code c:OpSimple.Code.values()){
			opTypes.put(c.code, OpSimple.class);
		}
		
		/* not implemented
		Del((byte)0x03),
		Insert((byte)0x04), //?? SendSMS is 0x04
		SendSMS((byte)0x05),// SendSMS is 0x04 (cf. mphlr.h) 
		SMSReceived((byte)0x84),
		XFer((byte)0xf0)*/
	}
	
	private static Class<? extends Operation> getOpClass(byte b) throws IllegalArgumentException{
		Class<? extends Operation> ret=opTypes.get(b);
		if (ret==null) throw new IllegalArgumentException("Unknown operation "+b);
		return ret;
	}
	
	static ByteBuffer slice(ByteBuffer b, int len){
		int oldLimit=b.limit();
		int newPos=b.position()+len;
		b.limit(newPos);
		ByteBuffer ret = b.slice();
		b.limit(oldLimit);
		b.position(newPos);
		return ret;
	}
	
	public Packet(){
		this.transactionId=rand.nextLong();
	}
	
	private Packet(long transactionId){
		this.transactionId=transactionId;
	}
	
	public void setDid(String did){
		if (did==null)
			throw new IllegalArgumentException("Did cannot be null");
		this.did=did;
		this.didFlag=true;
		this.sid=null;
	}
	
	public String getDid(){
		if (!didFlag)
			return null;
		return did;
	}
	
	public void setSid(SubscriberId sid){
		if (sid==null)
			throw new IllegalArgumentException("Sid cannot be null");
		didFlag=false;
		did=null;
		this.sid=sid;
	}
	
	public SubscriberId getSid(){
		if (didFlag)
			return null;
		return this.sid;
	}
	
	public void setSidDid(SubscriberId sid, String did){
		if (sid!=null)
			setSid(sid);
		else if (did!=null)
			setDid(did);
		else
			throw new IllegalArgumentException("Must suppy subscriber id or direct dial number.");
	}
	
	static byte[] packDid(String did){
		boolean halfByte=false;
		int len=did.length();
		
		if (len>=32) throw new IllegalArgumentException("Did is too long");
		
		ByteBuffer b = ByteBuffer.allocate(32);
		
		// pre-fill the buffer with random data
		rand.nextBytes(b.array());
		
		for (int i=0;i<len;i++){
			byte nextByte;
			char x=did.charAt(i);

			if (Character.isDigit(x))
				nextByte=(byte)(Character.digit(x, 10) << 4);
			else{
				switch(x){
				case '*': nextByte=(byte) 0xa0; break;
				case '#': nextByte=(byte) 0xb0; break;
				case '+': nextByte=(byte) 0xc0; break;
				default:
					throw new IllegalArgumentException("Illegal digit "+x+" in DID number");
				}
			}
			i++;
			if (i>=len){
				nextByte|=(byte)0x0f;
				halfByte=true;
			}else{
				x=did.charAt(i);
				if (Character.isDigit(x))
					nextByte|=(byte)Character.digit(x, 10);
				else{
					switch(x){
					case '*': nextByte|=(byte) 0x0a; break;
					case '#': nextByte|=(byte) 0x0b; break;
					case '+': nextByte|=(byte) 0x0c; break;
					default:
						throw new IllegalArgumentException("Illegal digit "+x+" in DID number");
					}
				}
			}
			b.put(nextByte);
		}
		if (b.remaining()>0&&!halfByte) b.put((byte)0xff);
		return b.array();
	}
	
	public static String unpackDid(InputStream value) throws IOException{
		byte []buff=new byte[256];
		int offset=0;
		
		// read up to 256 bytes from the stream, or up to the end (should be 32 bytes though)
		while(offset<buff.length){
			int len=value.read(buff,offset,buff.length - offset);
			if (len==-1) break;
			offset+=len;
		}
		
		return unpackDid(buff);
	}
	
	public static String unpackDid(byte[] buff){
		StringBuilder sb = new StringBuilder();
		for (int i=0; i<buff.length; i++){
			int val=(((int)buff[i])&0xf0) >> 4;
			if (val==0x0f) break;
			switch (val){
			case 0x0a: sb.append('*'); break;
			case 0x0b: sb.append('#'); break;
			case 0x0c: sb.append('+'); break;
			default:
				sb.append(Character.forDigit(val,10));
			}
			
			val=((int)buff[i])&0x0f;
			if (val==0x0f) break;
			switch (val){
			case 0x0a: sb.append('*'); break;
			case 0x0b: sb.append('#'); break;
			case 0x0c: sb.append('+'); break;
			default:
				sb.append(Character.forDigit(val,10));
			}
		}
		return sb.toString();
	}
	
	static String binToHex(byte[] buff){
		return binToHex(buff,0,buff.length);
	}
	static String binToHex(byte[] buff, int len){
		return binToHex(buff,0,len);
	}
	static String binToHex(ByteBuffer b){
		return binToHex(b.array(),b.arrayOffset()+b.position(),b.remaining());
	}
	static String binToHex(byte[] buff, int offset, int len){
		StringBuilder sb=new StringBuilder();
		for (int i=0; i<len; i++){
			sb.append(Character.forDigit((((int)buff[i+offset])&0xf0) >> 4,16));
			sb.append(Character.forDigit(((int)buff[i+offset])&0x0f,16));
		}
		return sb.toString();
	}
	
	static OpPad pad = new OpPad();
	static OpSimple eot = new OpSimple(OpSimple.Code.Eot);
	
	private void safeZero(ByteBuffer b, int len){
		byte[] bytes=new byte[len -1];
		rand.nextBytes(bytes);
		int mod=0;
		for (int i=0;i<len -1;i++){
			mod=(mod + ((int)bytes[i])&0xff)&0xff;
		}
		b.put(bytes);
		b.put((byte)(0x100 - mod));
	}
	
	DatagramPacket dg=null;
	public DatagramPacket getDatagram(){
		if (dg==null){
			ByteBuffer buff=constructPacketBuffer();
			dg = new DatagramPacket(buff.array(), buff.limit(), null, dnaPort);
		}
		return dg;
	}
	
	public ByteBuffer constructPacketBuffer(){
		// Note that ByteBuffers default to BIG_ENDIAN so we can use (get|put)<type> functions without needing to do any byte order manipulations.
		ByteBuffer b = ByteBuffer.allocate(8000);
		
		// write header
		b.putShort(magicNumber);
		b.putShort(packetVersion);
		b.putShort((short)0); // length isn't known yet
		b.putShort((short)0); // cypher method
		b.putLong(transactionId);
		b.put((byte)0); // rotation
		int hdrLen=b.position();
		
		b.put((byte)(didFlag?0:1));
		if (didFlag){
			if (did==null)
				throw new IllegalStateException("Did is null");
			b.put(packDid(did));
		}else if (sid==null)
			safeZero(b, 32);
		else
			b.put(sid.getSid());
		
		safeZero(b,16);//salt
		safeZero(b,16);//hash
		
		// write out all actions
		for (Operation o:operations){
			o.write(b);
		}
		pad.write(b);
		eot.write(b);
		
		// finalise packet
		b.flip();
		int len = b.limit();
		int payloadLen = len - hdrLen;
		b.putShort(4, (short)len); // length
		
		// rotate
		int rotation = payloadLen;
		if (rotation>0xff)rotation=0xff;
		rotation = rand.nextInt(rotation);
		if (rotation!=0){
			b.put(hdrLen -1,(byte)rotation);
			byte temp[] = new byte[payloadLen];
			b.position(hdrLen);
			b.get(temp, payloadLen - rotation, rotation);
			b.get(temp, 0, payloadLen - rotation);
			b.position(hdrLen);
			b.put(temp);
			b.rewind();
		}
		return b;
	}
	
	public static Packet reply(Packet p){
		return new Packet(p.transactionId);
	}
	
	public static Packet parse(DatagramPacket dg, long timestamp) throws IOException{
		ByteBuffer b=ByteBuffer.wrap(dg.getData(), dg.getOffset(), dg.getLength());
		return parse(b, dg.getSocketAddress(), timestamp);
	}
	
	public static Packet parse(ByteBuffer b, SocketAddress addr, long timestamp) throws IOException{
		// Force this ByteBuffers to BIG_ENDIAN so we can use (get|put)<type> functions without needing to do any byte order manipulations.
		b.order(ByteOrder.BIG_ENDIAN);
		
		try{
			short magic = b.getShort();
			if (magic!=magicNumber) throw new IllegalArgumentException("Incorrect magic value "+magic);
			
			short version = b.getShort();
			if (version!=packetVersion) throw new IllegalArgumentException("Unknown format version "+version);
			
			short payloadLen = b.getShort();
			if (payloadLen!=b.limit()) throw new IllegalArgumentException("Expected packet length of "+b.limit());
			short cipherMethod = b.getShort();
			if (cipherMethod!=0) throw new IllegalArgumentException("Unknown packet cipher "+cipherMethod);
			Packet p=new Packet(b.getLong());
			p.timestamp=timestamp;
			p.addr=addr;
			
			int rotation = (int)b.get() & 0xff;
			if (rotation!=0){
				// undo packet rotation
				int remain=b.remaining();
				byte temp[]=new byte[remain];
				b.mark();
				b.get(temp,rotation,remain - rotation);
				b.get(temp,0,rotation);
				b.reset();
				b.put(temp);
				b.reset();
			}
			
			p.didFlag=b.get()==0;
			if (p.didFlag){
				byte buff[]=new byte[SID_SIZE];
				b.get(buff);
				p.did=unpackDid(buff);
			}else{
				p.sid=new SubscriberId(b);
			}
			
			byte salt[]=new byte[16];
			b.get(salt);
			// TODO test zero...
			//if (salt!=0)  throw new IllegalArgumentException("salt not implemented");
			
			byte hash[]=new byte[16];
			b.get(hash);
			//if (hash!=0)  throw new IllegalArgumentException("hash not implemented");
			
			while(b.remaining()>0){
				byte opType=b.get();
				Class<? extends Operation> opClass=getOpClass(opType);
				if (opClass==null) throw new IllegalArgumentException("Operation type "+opType+" not implemented");
				Operation o=opClass.newInstance();
				o.parse(b, opType);
				if (o instanceof OpPad) continue;
				if (o instanceof OpSimple)
					if (((OpSimple)o).code==OpSimple.Code.Eot)
						continue;
				p.operations.add(o);
			}
			return p;
		}catch (Exception e){
			System.out.println("Failed to parse packet;");
			b.rewind();
			System.out.println(Test.hexDump(b));
			throw new IllegalStateException(e);
		}
	}
	
	@Override
	public int hashCode() {
		return (int)transactionId&0xFFFFFFFF;
	}

	@Override
	public String toString() {
		StringBuilder sb=new StringBuilder();
		sb
			.append("Packet{\n  TransId: ")
			.append(Long.toHexString(transactionId))
			.append("\n  ");
		if (didFlag)
			sb.append("Did: ")
			.append(this.did);
		else
			sb.append("Sid: ")
			.append(this.sid);
		sb.append("\n");
		for (Operation o:this.operations){
			sb
				.append("  ")
				.append(o.toString())
				.append("\n");
		}
		sb.append("}\n");
		return sb.toString();
	}
}
