package player;

public class Frame {
	
	int length;
	long timeStamp;
	byte[] image;

	public Frame(int length, long timeStamp, byte[] image) {
		this.length = length;
		this.timeStamp = timeStamp;
		this.image = image;
	}

	public int getLength() {
		return length;
	}

	public long getTimeStamp() {
		return timeStamp;
	}

	public byte[] getImage() {
		return image;
	}

}
