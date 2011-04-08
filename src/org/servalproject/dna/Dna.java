package org.servalproject.dna;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.servalproject.dna.OpSimple.Code;

public class Dna {
	
	DatagramSocket s=null;
	List<SocketAddress> peers=new ArrayList<SocketAddress>();
	
	static final short dnaPort=4110;
	
	public void addPeer(SocketAddress i){
		peers.add(i);
	}
	
	private void send(DatagramPacket dg, SocketAddress addr) throws IOException{
		dg.setSocketAddress(addr);
		System.out.println("Sending packet to "+addr.toString());
		s.send(dg);
	}
	
	private InetAddress receive(DatagramPacket reply, ByteBuffer buff, Packet p, OpVisitor v) throws IOException, IllegalAccessException, InstantiationException{
		s.receive(reply);
		
		buff.rewind();
		buff.limit(reply.getLength());
		
		Packet replyPack = Packet.parse(buff);
		if (p!=null){
			if (!p.checkReply(replyPack)) return null;
		}
		for (Operation o:replyPack.operations){
			o.visit(replyPack, v);
		}
		return reply.getAddress();
	}
	
	@SuppressWarnings("unused")
	private void sendParallel(Packet p, OpVisitor v) throws IOException, IllegalAccessException, InstantiationException{
		boolean []received=new boolean[peers.size()];
		int peerCount=peers.size();
		int receivedCount=0;
		
		if (peers.size()==0)
			throw new IllegalStateException("No peers have been set");
		if (s==null){
			s=new DatagramSocket();
			s.setBroadcast(true);
		}
		ByteBuffer b=p.constructPacketBuffer();
		DatagramPacket dg = new DatagramPacket(b.array(), b.limit(), null, dnaPort);
		
		for (int i=0;i<peerCount;i++){
			received[i]=false;
		}
		s.setSoTimeout(100);
		ByteBuffer buff=ByteBuffer.allocate(8000);
		DatagramPacket reply=new DatagramPacket(buff.array(), buff.capacity());
		
		int retryCount=0;
		while(receivedCount<peerCount && retryCount<=5){
			retryCount++;
			
			// keep transmitting until each peer responds or we run out of time
			for (int i=0;i<peerCount;i++){
				if (received[i])continue;
				
				send(dg, peers.get(i));
			}
			try{
				// keep trying to receive packets until a receive times out.
				while(receivedCount<peerCount){
					InetAddress addr = receive(reply, buff, p, v);
					if (addr==null) continue;
					// mark receiving reply from peer
					for (int i=0;i<peerCount;i++){
						if (received[i])continue;
						if (addr.equals(peers.get(i))){
							received[i]=true;
							receivedCount++;
						}
					}
				}
			}catch(SocketTimeoutException e){
			}
		}
	}
	
	private void sendSerial(Packet p, OpVisitor v) throws IOException, IllegalAccessException, InstantiationException{
		if (peers.size()==0)
			throw new IllegalStateException("No peers have been set");
		if (s==null){
			s=new DatagramSocket();
			s.setBroadcast(true);
		}
		ByteBuffer b=p.constructPacketBuffer();
		DatagramPacket dg = new DatagramPacket(b.array(), b.limit(), null, dnaPort);
		
		s.setSoTimeout(100);
		ByteBuffer buff=ByteBuffer.allocate(8000);
		DatagramPacket reply=new DatagramPacket(buff.array(), buff.capacity());
		
		for (SocketAddress addr:peers){
			// try sending a packet to each peer five times
			for (int i=0;i<5;i++){
				try{
					send(dg,addr);
					if (receive(reply, buff, p, v)==null) continue;
					break;
				}catch(SocketTimeoutException e){
				}
			}
		}
	}
	
	public byte[] requestNewHLR(String did) throws IOException, IllegalAccessException, InstantiationException{
		Packet p=new Packet();
		p.setDid(did);
		p.operations.add(new OpSimple(OpSimple.Code.Create));
		clientVis v=new clientVis();
		sendSerial(p, v);
		return v.sid;
	}
	
	private class clientVis extends OpVisitor{
		byte[] sid=null;

		@Override
		public void onSimpleCode(Packet packet, Code code) {
			switch(code){
			case Ok:
				sid=packet.getSid();
				System.out.println("Sid returned: "+Packet.binToHex(sid));
				break;
			default:
				System.out.println("Returned: "+code.name());
			}
		}
	};
	
	public static void usage(String error){
		if (error!=null)
			System.out.println(error);
			
		System.out.println("usage:");
		System.out.println("       -d - Search by Direct Inward Dial (DID) number.");
		System.out.println("       -p - Specify additional DNA nodes to query.");
		System.out.println("       -C - Request the creation of a new subscriber with the specified DID.");
	}
	
	public static void main(String[] args) {
		try {
			Dna dna = new Dna();
//			dna.addPeer(new InetSocketAddress(Inet4Address.getLocalHost(), 4110));
			String did=null;
			
			for (int i=0;i<args.length;i++){
				if ("-d".equals(args[i])){
					did=args[++i];
				}else if ("-p".equals(args[i])){
					String host=args[++i];
					int port=4110;
					if (host.indexOf(':')>=0){
						port=Integer.valueOf(host.substring(host.indexOf(':')));
						host=host.substring(0,host.indexOf(':'));
					}
					dna.addPeer(new InetSocketAddress(host,port));
				}else if ("-C".equals(args[i])){
					if (did==null) throw new IllegalArgumentException("You must specify the DID to register.");
					dna.requestNewHLR(did);
				}
			}
			
		} catch (Exception e) {
			usage(e.toString());
			e.printStackTrace();
		}
	}
}
