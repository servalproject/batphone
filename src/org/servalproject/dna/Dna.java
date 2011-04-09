package org.servalproject.dna;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
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
	int timeout=500;
	int retries=1;
	
	public void addPeer(SocketAddress i){
		peers.add(i);
	}
	
	private void send(DatagramPacket dg, SocketAddress addr) throws IOException{
		dg.setSocketAddress(addr);
		System.out.println("Sending packet to "+addr.toString());
		s.send(dg);
	}
	
	private Packet receive(DatagramPacket reply, ByteBuffer buff, Packet p) throws IOException, IllegalAccessException, InstantiationException{
		s.receive(reply);
		
		buff.rewind();
		buff.limit(reply.getLength());
		
		Packet replyPack = Packet.parse(buff);
		if (p!=null){
			if (!p.checkReply(replyPack)) return null;
		}
		return replyPack;
	}
	
	private boolean processResponse(Packet reply, OpVisitor v){
		for (Operation o:reply.operations){
			if (o.visit(reply, v)) return true;
		}
		return false;
	}
	
	private boolean sendParallel(Packet p, OpVisitor v) throws IOException, IllegalAccessException, InstantiationException{
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
		s.setSoTimeout(timeout);
		ByteBuffer buff=ByteBuffer.allocate(8000);
		DatagramPacket reply=new DatagramPacket(buff.array(), buff.capacity());
		
		int retryCount=0;
		while(receivedCount<peerCount && retryCount<=retries){
			retryCount++;
			
			// keep transmitting until each peer responds or we run out of time
			for (int i=0;i<peerCount;i++){
				if (received[i])continue;
				
				send(dg, peers.get(i));
			}
			try{
				// keep trying to receive packets until a receive times out.
				while(receivedCount<peerCount){
					Packet replyPack=receive(reply, buff, p);
					if (replyPack==null) continue;
					InetAddress addr=reply.getAddress();
					if (addr==null) continue;
					// mark receiving reply from peer
					for (int i=0;i<peerCount;i++){
						if (received[i])continue;
						if (addr.equals(peers.get(i))){
							received[i]=true;
							receivedCount++;
						}
					}
					if (processResponse(replyPack, v))
						return true;
				}
			}catch(SocketTimeoutException e){
			}
		}
		return false;
	}
	
	private boolean sendSerial(Packet p, OpVisitor v) throws IOException, IllegalAccessException, InstantiationException{
		if (peers.size()==0)
			throw new IllegalStateException("No peers have been set");
		if (s==null){
			s=new DatagramSocket();
			s.setBroadcast(true);
		}
		ByteBuffer b=p.constructPacketBuffer();
		DatagramPacket dg = new DatagramPacket(b.array(), b.limit(), null, dnaPort);
		
		s.setSoTimeout(timeout);
		ByteBuffer buff=ByteBuffer.allocate(8000);
		DatagramPacket reply=new DatagramPacket(buff.array(), buff.capacity());
		
		for (SocketAddress addr:peers){
			// try sending a packet to each peer five times
			for (int i=0;i<retries;i++){
				try{
					send(dg,addr);
					Packet replyPack=receive(reply, buff, p);
					if (replyPack==null)continue;
					if (processResponse(replyPack,v)) return true;
					break;
				}catch(SocketTimeoutException e){
				}
			}
		}
		return false;
	}
	
	public SubscriberId requestNewHLR(String did) throws IOException, IllegalAccessException, InstantiationException{
		Packet p=new Packet();
		p.setDid(did);
		p.operations.add(new OpSimple(OpSimple.Code.Create));
		CreateVisitor v=new CreateVisitor();
		if (!sendSerial(p, v)) 
			throw new IllegalStateException("Create request was not handled by any peers");
		return v.sid;
	}
	
	private class CreateVisitor extends OpVisitor{
		SubscriberId sid=null;

		@Override
		public boolean onSimpleCode(Packet packet, Code code) {
			switch(code){
			case Ok:
				sid=packet.getSid();
				return true;
			default:
				System.out.println("Response: "+code.name());
			}
			return false;
		}
	};
	
	public void writeVariable(SubscriberId sid, final VariableType var, byte instance, ByteBuffer value) throws IOException, IllegalAccessException, InstantiationException{
		short offset=0;
		while (value.remaining()>0){
			Packet p=new Packet();
			p.setSid(sid);
			p.operations.add(new OpSet(var, instance, offset, offset==0?OpSet.Flag.NoReplace:OpSet.Flag.Fragment, 
					Packet.slice(value,value.remaining()>255?255:value.remaining())));
			sendSerial(p, new OpVisitor(){
				@Override
				public boolean onWrote(Packet packet, OpWrote wrote) {
					System.out.println("Wrote "+var.name());
					return true;
				}
			});
		}
	}
	
	public static void usage(String error){
		if (error!=null)
			System.out.println(error);
			
		System.out.println("usage:");
		
		System.out.println("       -i - Specify the instance of variable to set.");
		System.out.println("       -d - Search by Direct Inward Dial (DID) number.");
		System.out.println("       -p - Specify additional DNA nodes to query.");
		System.out.println("       -W - Write a variable value, keeping previous values.\n");
		System.out.println("       -C - Request the creation of a new subscriber with the specified DID.");
	}
	
	public static void main(String[] args) {
		try {
			Dna dna = new Dna();
//			dna.addPeer(new InetSocketAddress(Inet4Address.getLocalHost(), 4110));
			String did=null;
			SubscriberId sid=null;
			int instance=-1;
			
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
				}else if ("-i".equals(args[i])){
					instance=Integer.valueOf(args[++i]);
					if (instance <-1 || instance>255) throw new IllegalArgumentException("Instance value must be between -1 and 255");
				}else if ("-C".equals(args[i])){
					if (did==null) throw new IllegalArgumentException("You must specify the DID to register.");
					sid = dna.requestNewHLR(did);
					if (sid!=null)
						System.out.println("Sid returned: "+sid);
				}else if ("-W".equals(args[i])){
					VariableType var=VariableType.valueOf(args[++i]);
					String value=args[++i];
					dna.writeVariable(sid, var, (byte)instance, ByteBuffer.wrap(value.getBytes()));
				}else{
					throw new IllegalArgumentException("Unhandled argument "+args[i]+".");
				}
			}
			
		} catch (Exception e) {
			e.printStackTrace();
			usage(null);
		}
	}
}
