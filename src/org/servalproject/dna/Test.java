package org.servalproject.dna;

import java.nio.ByteBuffer;
import java.util.Random;

import org.servalproject.dna.OpDT.DTtype;

public class Test {

	public static String hexDump(byte[] data){
		return hexDump(data,0,data.length);
	}
	public static String hexDump(byte[] data, int len){
		return hexDump(data,0,len);
	}
	public static String hexDump(ByteBuffer b){
		return hexDump(b.array(),b.arrayOffset()+b.position(),b.remaining());
	}
	public static String hexDump(byte[] data, int start, int len)
	{
		byte byte_value;

		StringBuilder str = new StringBuilder(data.length * 3);

		for (int i = 0; i < len; i += 16)
		{
			// dump the header: 00000000: 
			String offset = Integer.toHexString(i);

			// "0" left pad offset field so it is always 8 char's long.
			for (int offlen = offset.length(); offlen < 8; offlen++) 
				str.append("0");
			str.append(offset);
			str.append(":");

			// dump hex version of 16 bytes per line.
			for (int j = 0; (j < 16) && ((i + j) < len); j++)
			{
				byte_value = data[start + i + j];

				str.append(" ");

				// dump a single byte.
				byte high_nibble = (byte) ((byte_value & 0xf0) >>> 4); 
				byte low_nibble  = (byte) (byte_value & 0x0f); 

				str.append(Character.forDigit(high_nibble, 16));
				str.append(Character.forDigit(low_nibble, 16));
			}

			// dump ascii version of 16 bytes
			str.append("  ");

			for (int j = 0; (j < 16) && ((i + j) < len); j++)
			{
				char char_value = (char) data[start + i + j]; 

				// RESOLVE (really want isAscii() or isPrintable())
				if (Character.isLetterOrDigit(char_value))
					str.append(String.valueOf(char_value));
				else
					str.append(".");
			}

			// new line
			str.append("\n");
		}
		return(str.toString());

	}


	public static void main(String[] args) {
		
		try {
			String []testDids=new String[]{"1234", "12+34*56#78", "05283"};
			System.out.println("Testing did packing");
			for (String testDid:testDids){
				System.out.println("Before: "+testDid);
				byte []did=Packet.packDid(testDid);
				System.out.println("Packed: "+Packet.binToHex(did));
				System.out.println("Unpacked: "+Packet.unpackDid(did));
			}
			
			for (int i=0;i<100;i++){
				Packet p=new Packet();
				p.setDid("12+34*56#78");
				p.operations.add(new OpSimple(OpSimple.Code.Create));
				p.operations.add(new OpSimple(OpSimple.Code.Declined));
				p.operations.add(new OpGet(VariableType.Creator, (byte)-1, (short)0));
				p.operations.add(new OpError("Error text"));
				p.operations.add(new OpSimple(OpSimple.Code.Ok));
				p.operations.add(new OpDone((byte)5));
				p.operations.add(new OpDT("hello", "123", OpDT.DTtype.SMS));
				ByteBuffer b=ByteBuffer.allocate(64);
				Random r=new Random();
				r.nextBytes(b.array());
				p.operations.add(new OpSet(VariableType.Creator, (byte)-1, (short)0, OpSet.Flag.Replace, b));
				
				String before=p.toString();
				System.out.println(before);
				ByteBuffer bytebuff=p.constructPacketBuffer();
				Packet n = Packet.parse(bytebuff, null, 0);
				String after=n.toString();
				
				if (!before.equals(after)){
					System.out.println("Parsing failed.\nBefore: "+before+"\nAfter: "+after);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
