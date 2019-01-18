package nl.knokko.antixray.settings;

import java.nio.ByteBuffer;

public class WorldState {
	
	private State state;
	
	private int minY;
	private int maxY;
	
	public WorldState() {
		state = State.INITIAL;
		minY = -1;
		maxY = -1;
	}
	
	public WorldState(ByteBuffer input) {
		
		// A single byte is more than enough for an enum with a few constants
		state = State.values()[input.get()];
		
		if (state == State.ACTIVE) {
			minY = input.get() & 0xFF;
			maxY = input.get() & 0xFF;
		}
	}
	
	public byte[] save() {
		if (state == State.ACTIVE) {
			return new byte[] {(byte) State.ACTIVE.ordinal(), (byte) minY, (byte) maxY};
		} else {
			return new byte[] {(byte) state.ordinal()};
		}
	}
	
	public void start(int minY, int maxY) {
		this.minY = minY;
		this.maxY = maxY;
		state = State.ACTIVE;
	}
	
	public State getState() {
		return state;
	}
	
	public int getMinY() {
		return minY;
	}
	
	public int getMaxY() {
		return maxY;
	}
}