package org.servalproject.dna;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.servalproject.batman.PeerRecord;
import org.servalproject.dna.OpSimple.Code;

import android.util.Log;

public class Dna {
	
	private DatagramSocket s=null;
	private List<SocketAddress> staticPeers=null;
	private List<PeerRecord> dynamicPeers=null; 
	
	private int timeout=300;
	private int retries=5;
	
	public void addStaticPeer(SocketAddress i){
		if (staticPeers==null)
			staticPeers=new ArrayList<SocketAddress>();
		staticPeers.add(i);
	}
	
	public void setDynamicPeers(List<PeerRecord> batmanPeers){
		dynamicPeers=batmanPeers;
	}
	
	// packets that we may need to (re-)send
	List<PeerConversation> resendQueue=new ArrayList<PeerConversation>();
	// responses that we are still waiting for
	Map<Long, PeerConversation> awaitingResponse=new HashMap<Long, PeerConversation>();
	
	private void send(PeerConversation pc) throws IOException{
		DatagramPacket dg=pc.packet.getDatagram();
		dg.setSocketAddress(pc.addr);
		if (s==null){
			s=new DatagramSocket();
			s.setBroadcast(true);
		}
		Log.d("BatPhone", "Sending packet to "+pc.addr);
		Log.v("BatPhone", pc.packet.toString());
		s.send(dg);
		pc.retryCount++;
		
		if (!resendQueue.contains(pc)){
			resendQueue.add(pc);
			awaitingResponse.put(pc.packet.transactionId, pc);
		}
	}
	
	private void send() throws IOException{
		Iterator<PeerConversation> i=resendQueue.iterator();
		while (i.hasNext()){
			PeerConversation pc=i.next();
			if (pc.responseReceived || pc.retryCount>=retries){
				i.remove();
				continue;
			}
			send(pc);
		}
	}
	
	// one packet buffer for all reply's
	DatagramPacket reply=null;
	
	private void receivePacket() throws IOException{
		if (awaitingResponse.isEmpty())
			throw new IllegalStateException("No conversations are expecting a response");
		if (reply==null){
			byte[]buffer = new byte[8000];
			reply=new DatagramPacket(buffer, buffer.length);
		}
		s.setSoTimeout(timeout);
		s.receive(reply);
		Packet p=Packet.parse(reply);
		PeerConversation pc=awaitingResponse.get(p.transactionId);
		if (pc!=null){
			pc.processResponse(p);
			if (pc.conversationComplete)
				awaitingResponse.remove(p.transactionId);
		}else{
			Log.d("BatPhone", "Unexpected packet from "+reply.getSocketAddress());
			Log.v("BatPhone", p.toString());
			throw new IllegalStateException("Unexpected packet from "+reply.getSocketAddress());
		}
	}
	
	private void processAll() throws IOException{
		while(!(resendQueue.isEmpty() && awaitingResponse.isEmpty())){
			try{
				while (!awaitingResponse.isEmpty())
					receivePacket();
				
			}catch(SocketTimeoutException e){
				// remove conversations we are unlikely to hear from again
				Iterator<PeerConversation> i=awaitingResponse.values().iterator();
				while (i.hasNext()){
					PeerConversation pc=i.next();
					if (pc.responseReceived||pc.retryCount>retries)
						i.remove();
				}
				
				// if we're not going to re-send a packet, we can just give up now.
				if (resendQueue.isEmpty())
					return;
			}
			
			if (!resendQueue.isEmpty())
				send();
		}
	}
	
	private void sendParallel(Packet p, OpVisitor v) throws IOException{
		if (staticPeers==null&&dynamicPeers==null)
			throw new IllegalStateException("No peers have been set");
		
		if (staticPeers!=null){
			for (SocketAddress addr:staticPeers){
				send(new PeerConversation(p, addr, v));
			}
		}
		
		if (dynamicPeers!=null){
			for (PeerRecord peer:dynamicPeers){
				send(new PeerConversation(p, peer.getAddress(), v));
			}
		}
		
		processAll();
	}
	
	private boolean sendSerial(PeerConversation pc) throws IOException{
		send(pc);
		processAll();
		return pc.conversationComplete;
	}
	
	private boolean sendSerial(Packet p, OpVisitor v) throws IOException{
		if (staticPeers==null&&dynamicPeers==null)
			throw new IllegalStateException("No peers have been set");
		
		if (staticPeers!=null){
			for (SocketAddress addr:staticPeers){
				if (sendSerial(new PeerConversation(p,addr,v)))
					return true;
			}
		}
		if (dynamicPeers!=null){
			for (PeerRecord peer:dynamicPeers){
				if (sendSerial(new PeerConversation(p,peer.getAddress(),v)))
					return true;
			}
		}
		return false;
	}
	
	public SubscriberId requestNewHLR(String did) throws IOException, IllegalAccessException, InstantiationException{
		Packet p=new Packet();
		p.setDid(did);
		p.operations.add(new OpSimple(OpSimple.Code.Create));
		ClientVisitor v=new ClientVisitor();
		if (!sendSerial(p, v)) 
			throw new IllegalStateException("Create request was not handled by any peers");
		return v.sid;
	}
	
	private class ClientVisitor extends OpVisitor{
		SubscriberId sid=null;
		VariableRef reference;
		short varLen;
		ByteBuffer buffer;
		
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

		@Override
		public boolean onData(Packet packet, VariableRef reference, short varLen, ByteBuffer buffer) {
			this.sid=packet.getSid();
			this.reference=reference;
			this.varLen=varLen;
			this.buffer=buffer;
			return true;
		}
		
	};
	
	public void writeDid(SubscriberId sid, byte instance, String did) throws IOException{
		writeVariable(sid, VariableType.DIDs, instance, ByteBuffer.wrap(Packet.packDid(did)));
	}
	public void writeLocation(SubscriberId sid, byte instance, String value) throws IOException{
		writeVariable(sid, VariableType.Locations, instance, ByteBuffer.wrap(value.getBytes()));
	}
	public void writeVariable(SubscriberId sid, final VariableType var, byte instance, ByteBuffer value) throws IOException{
		OutputStream s = beginWriteVariable(sid, var, instance);
		s.write(value.array(),value.arrayOffset(),value.remaining());
		s.close();
	}
	
	// write variables with an output stream so we can store large values as they are created
	// TODO, send packets asynchronously on calls to write, blocking only when flush() or close() is called.
	public OutputStream beginWriteVariable(SubscriberId sid, VariableType var, byte instance){
		return new WriteOutputStream(sid, var, instance);
	}
	
	class WriteOutputStream extends OutputStream{
		SubscriberId sid;
		VariableType var;
		byte instance;
		short offset=0;
		ByteBuffer buffer=ByteBuffer.allocate(256);
		
		public WriteOutputStream(SubscriberId sid, VariableType var, byte instance) {
			if (sid==null)
				throw new IllegalArgumentException("Subscriber ID cannot be null");
			if (var==null)
				throw new IllegalArgumentException("Variable type cannot be null");
			
			this.sid=sid;
			this.var=var;
			this.instance=instance;
		}

		@Override
		public void close() throws IOException {flush();}

		@Override
		public void flush() throws IOException {
			buffer.flip();
			
			Packet p=new Packet();
			p.setSid(sid);
			p.operations.add(new OpSet(var, instance, offset, offset==0?OpSet.Flag.NoReplace:OpSet.Flag.Fragment,buffer));
			offset+=buffer.remaining();
			sendSerial(p, new OpVisitor(){
				@Override
				public boolean onWrote(Packet packet, VariableRef reference) {
					System.out.println("Wrote "+reference);
					return true;
				}
			});
			buffer.clear();
		}
		
		@Override
		public void write(byte[] b) throws IOException {write(b,0,b.length);}

		@Override
		public void write(byte[] b, int off, int len) throws IOException {
			while(len>0){
				int size=len>256?256:len;
				
				buffer.put(b, off, size);
				if (buffer.remaining()==0) flush();
				
				len-=size;
				off+=size;
			}
		}

		@Override
		public void write(int arg0) throws IOException {
			buffer.put((byte)arg0);
			if (buffer.remaining()==0) flush();
		}
	}
	
	// Send a request to all peers for this variable
	public void readVariable(SubscriberId sid, String did, final VariableType var, final byte instance, final VariableResults results) throws IOException{
		Packet p=new Packet();
		p.setSidDid(sid, did);
		p.operations.add(new OpGet(var, instance, (short)0));
		
		sendParallel(p, new OpVisitor(){
			byte received = 0;
			byte count = -1;

			@Override
			public boolean onDone(Packet packet, byte count) {
				this.count=count;
				return count==received;
			}
			
			@Override
			public boolean onData(Packet packet, VariableRef reference,
					short varLen, ByteBuffer buffer) {
				// inform the caller of this variable, create an input stream for the caller to read the value.
				results.result(packet.addr, packet.getSid(), reference.varType, reference.instance, 
						new ReadInputStream(packet.getSid(), reference.varType, reference.instance, packet.addr, buffer, varLen));
				received++;
				if (var.hasMultipleValues()&&instance==-1)
					return received==count;
				return true;
			}
		});
	}
	
	//TODO pre-fetch remaining fragments asynchronously
	public class ReadInputStream extends InputStream{
		short offset=0;
		ByteBuffer buffer=null;
		short expectedLen;
		SubscriberId sid;
		VariableType var;
		byte instance;
		SocketAddress peer;
		ClientVisitor v=null;
		
		ReadInputStream(SubscriberId sid, VariableType var, byte instance, SocketAddress peer, ByteBuffer firstChunk, short expectedLen){
			this.sid=sid;
			this.var=var;
			this.instance=instance;
			this.peer=peer;
			this.buffer=firstChunk;
			this.expectedLen=expectedLen;
			if (firstChunk!=null){
				offset=(short)firstChunk.remaining();
			}
		}
		
		private boolean readMore() throws IOException{
			if (offset>=expectedLen)
				offset=-1;
			if (offset==-1)
				return false;
			
			Packet p=new Packet();
			p.setSid(sid);
			p.operations.add(new OpGet(var, instance, offset));
			if (v==null)
				v=new ClientVisitor();
			
			send(new PeerConversation(p, peer, v));
			processAll();
			
			expectedLen=v.varLen;
			this.buffer=v.buffer;
			this.offset+=v.reference.len;
			return true;
		}
		
		@Override
		public void close() throws IOException {
			offset=-1;
			buffer=null;
		}

		@Override
		public int read() throws IOException {
			if (offset==-1) return -1;
			if (buffer==null||buffer.remaining()==0)
				if (!readMore()) return-1;
			return ((int)buffer.get())&0xff;
		}

		@Override
		public int available() throws IOException {
			if (buffer==null) return 0;
			return buffer.remaining();
		}

		@Override
		public int read(byte[] bytes, int offset, int len) throws IOException {
			if (offset==-1) return -1;	
			if (len==0) return 0;
			if (buffer==null||buffer.remaining()==0)
				if (!readMore()) return-1;
			
			int size=len>buffer.remaining()?buffer.remaining():len;
			buffer.get(bytes,offset,size);
			return size;
		}

		@Override
		public int read(byte[] bytes) throws IOException {
			return read(bytes,0,bytes.length);
		}

		@Override
		public long skip(long len) throws IOException {
			int size=0;
			if (offset==-1) return -1;
			if (buffer!=null&&buffer.remaining()>0){
				size=(int)(len>buffer.remaining()?buffer.remaining():len);
				buffer.position(buffer.position()+size);
			}
			offset+=len - size;
			if (offset>expectedLen){
				offset=-1;
				return -1;
			}
				
			return len;
		}
	}
	/*
	@SuppressWarnings("unused")
	private void locateSubscriber(SubscriberId sid, String did) throws IOException, IllegalAccessException, InstantiationException{
		Packet p=new Packet();
		p.setSidDid(sid, did);
		p.operations.add(new OpGet(VariableType.Locations, (byte)0, (short)0));
		LocateVisitor v=new LocateVisitor();
		if (!sendParallel(p, v)){
			throw new IllegalStateException("Unable to locate subscriber"); 
		}
		
		// TODO
	}
	
	private class LocateVisitor extends OpVisitor{
		@Override
		public boolean onSimpleCode(Packet packet, Code code) {
			System.out.println("Response: "+code.name());
			return false;
		}
		
	};
	*/
	
	public static void usage(String error){
		if (error!=null)
			System.out.println(error);
			
		System.out.println("usage:");
		
		System.out.println("       -i - Specify the instance of variable to set.");
		System.out.println("       -d - Search by Direct Inward Dial (DID) number.");
		System.out.println("       -s - Search by Subscriber ID (SID) number.");
		System.out.println("       -p - Specify additional DNA node to query.");
		System.out.println("       -t - Specify the request timeout period.");
		System.out.println("       -R - Read a variable value.\n");
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
					
				}else if ("-s".equals(args[i])){
					sid=new SubscriberId(args[++i]);
					
				}else if ("-p".equals(args[i])){
					String host=args[++i];
					int port=4110;
					if (host.indexOf(':')>=0){
						port=Integer.valueOf(host.substring(host.indexOf(':')));
						host=host.substring(0,host.indexOf(':'));
					}
					dna.addStaticPeer(new InetSocketAddress(host,port));
					
				}else if ("-t".equals(args[i])){
					dna.timeout=Integer.valueOf(args[++i]);
					
				}else if ("-i".equals(args[i])){
					instance=Integer.valueOf(args[++i]);
					if (instance <-1 || instance>255) throw new IllegalArgumentException("Instance value must be between -1 and 255");
					
				}else if ("-C".equals(args[i])){
					if (did==null) throw new IllegalArgumentException("You must specify the DID to register.");
					sid = dna.requestNewHLR(did);
					if (sid!=null)
						System.out.println("Sid returned: "+sid);
					
				}else if ("-W".equals(args[i])){
					VariableType var=VariableType.getVariableType(args[++i]);
					String value=args[++i];
					dna.writeVariable(sid, var, (byte)instance, ByteBuffer.wrap(value.getBytes()));
					
				}else if ("-R".equals(args[i])){
					VariableType var=VariableType.getVariableType(args[++i]);
					dna.readVariable(sid, did, var, (byte) instance, new VariableResults(){
						
						@Override
						public void result(SocketAddress peer, SubscriberId sid, VariableType varType, byte instance, InputStream value) {
							try {
								StringBuilder sb=new StringBuilder();
								byte[] bytes=new byte[256];
								int len;
								while((len=value.read(bytes))>=0){
									sb.append(new String(bytes,0,len));
								}
								System.out.println(sid+" ("+peer+")\n"+varType.name()+"["+instance+"]: "+sb.toString());
							} catch (IOException e) {
								e.printStackTrace();
							}
						}
					});
					
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
