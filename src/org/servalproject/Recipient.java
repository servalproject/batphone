package org.servalproject;


/**
 *
 * @author brendon
 *
 */
public class Recipient {

	private Type type;
	private String name;
	private String number;
	private String phoneType;

	public Recipient() {
	}

	public Recipient(Type type, String name, String number, String phoneType) {
		super();
		this.type = type;
		this.name = name;
		this.number = number;
		this.phoneType = phoneType;
	}

	public String getDisplayName() {
		if (name == null) {
			return "";
		}
		return name;
	}

	public Type getType() {
		return type;
	}

	public void setType(Type type) {
		this.type = type;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getNumber() {
		return number;
	}

	public void setNumber(String number) {
		this.number = number;
	}

	public String getPhoneType() {
		return phoneType;
	}

	public void setPhoneType(String type) {
		this.phoneType = type;
	}

	@Override
	public String toString() {
		return getDisplayName();
	}

	public enum Type {
		Serval(R.string.recipient_type_serval),
		Phone(R.string.recipient_type_phone);

		private int resourceId;

		Type(int resourceId) {
			this.resourceId = resourceId;
		}

		public int getResourceId() {
			return resourceId;
		}
	}

}
