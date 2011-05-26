package org.servalproject.dna;

import java.net.InetAddress;

public class Address {
	public InetAddress addr;
	public int port;
	
	public Address(InetAddress addr, int port){
		this.addr=addr;
		this.port=port;
	}
	@Override
	public String toString() {
		return addr.toString()+":"+port;
	}
	
	@Override
	public boolean equals(Object o) {
		if (o instanceof Address){
			Address a=(Address) o;
			return a.addr.equals(this.addr)&&a.port==this.port;
		}
		return false;
	}
	@Override
	public int hashCode() {
		return this.port ^ addr.hashCode();
	}
	
}
