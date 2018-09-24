package nl.knokko.xray;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.Arrays;

public class BitBuffer {
	
	private static final byte[] BYTES = new byte[]{64,32,16,8,4,2,1};
	
	private static long get2Power(byte index){
		long l = 1;
		for(byte b = 0; b < index; b++){
			l *= 2;
			if(l < 0)
				l = Long.MAX_VALUE;
		}
		return l;
	}
	
	private static byte getRequiredBits(long number){
		if(number < 0)
			number = -(number + 1);
		long l = 1;
		byte b = 0;
		while(l < number){
			l *= 2;
			b++;
		}
		return b;
	}
	
	private static void checkBitCount(byte bits){
		if(bits < 0)
			throw new IllegalArgumentException("Number of bits ( + " + bits + ") can't be negative!");
		if(bits >= 64)
			throw new IllegalArgumentException("Number of bits ( + " + bits + ") can't be greater than 63!");
	}
	
	private static void checkOverflow(long number, byte bits){
		if(get2Power(bits) <= number || get2Power(bits) < -number)
			throw new IllegalArgumentException("You need more than " + bits + " bits to store the number " + number + "!");
	}
	
	private static boolean[] numberToBinair(long number, byte bits, boolean allowNegative){
		checkBitCount(bits);
		checkOverflow(number, bits);
		byte neg = (byte) (allowNegative ? 1 : 0);
		boolean[] bools = new boolean[bits + neg];
		if(allowNegative){
			if(number >= 0)
				bools[0] = true;
			else {
				//bools[0] will stay false
				number++;
				number = -number;
			}
		}
		for(byte b = 0; b < bits; b++){
			if(number >= get2Power((byte) (bits - b - 1))){
				number -= get2Power((byte) (bits - b - 1));
				bools[b + neg] = true;
			}
		}
		return bools;
	}
	
	private static boolean[] byteToBinary(byte b){
		boolean[] bools = new boolean[8];
		if(b >= 0)
			bools[7] = true;
		else {
			b++;
			b *= -1;
		}
		byte t = 0;
		while(t < 7){
			if(b >= BYTES[t]){
				b -= BYTES[t];
				bools[t] = true;
			}
			++t;
		}
		return bools;
	}
	
	private static long numberFromBinary(boolean[] bools, byte bits, boolean allowNegative){
		checkBitCount(bits);
		long number = 0;
		byte neg = (byte) (allowNegative ? 1 : 0);
		for(byte b = 0; b < bits; b++){
			if(bools[b + neg])
				number += get2Power((byte) (bits - b - 1));
		}
		if(allowNegative){
			if(!bools[0]){
				number = -number;
				number--;
			}
		}
		return number;
	}
	
	private static byte byteFromBinary(boolean[] bools){
		byte b = 0;
		int t = 0;
		while(t < 7){
			if(bools[t])
				b += BYTES[t];
			++t;
		}
		if(!bools[7]){
			b *= -1;
			b--;
		}
		return b;
	}
	
	//copied from java.nio.Bits
	static char makeChar(byte b0, byte b1) {
        return (char)((b1 << 8) | (b0 & 0xff));
    }
	
	private static short makeShort(byte b0, byte b1) {
        return (short)((b1 << 8) | (b0 & 0xff));
    }
	
	static private int makeInt(byte b0, byte b1, byte b2, byte b3) {
        return (((b3       ) << 24) |
                ((b2 & 0xff) << 16) |
                ((b1 & 0xff) <<  8) |
                ((b0 & 0xff)      ));
    }
	
	private static long makeLong(byte b0, byte b1, byte b2, byte b3,byte b4, byte b5, byte b6, byte b7){
		return ((((long)b7       ) << 56) |
				(((long)b6 & 0xff) << 48) |
				(((long)b5 & 0xff) << 40) |
				(((long)b4 & 0xff) << 32) |
				(((long)b3 & 0xff) << 24) |
				(((long)b2 & 0xff) << 16) |
			(((long)b1 & 0xff) <<  8) |
			(((long)b0 & 0xff)      ));
	}
	
	public static byte char1(char x) { return (byte)(x >> 8); }
    public static byte char0(char x) { return (byte)(x     ); }
    
    private static byte short1(short x) { return (byte)(x >> 8); }
    private static byte short0(short x) { return (byte)(x     ); }
    
    private static byte int3(int x) { return (byte)(x >> 24); }
    private static byte int2(int x) { return (byte)(x >> 16); }
    private static byte int1(int x) { return (byte)(x >>  8); }
    private static byte int0(int x) { return (byte)(x      ); }
    
    private static byte long7(long x) { return (byte)(x >> 56); }
    private static byte long6(long x) { return (byte)(x >> 48); }
    private static byte long5(long x) { return (byte)(x >> 40); }
    private static byte long4(long x) { return (byte)(x >> 32); }
    private static byte long3(long x) { return (byte)(x >> 24); }
    private static byte long2(long x) { return (byte)(x >> 16); }
    private static byte long1(long x) { return (byte)(x >>  8); }
    private static byte long0(long x) { return (byte)(x      ); }
    
    private static float fromInt(int i){
    	return Float.intBitsToFloat(i);
    }
    
    private static int fromFloat(float f){
    	return Float.floatToRawIntBits(f);
    }
    
    private static double fromLong(long l){
    	return Double.longBitsToDouble(l);
    }
    
    private static long fromDouble(double d){
    	return Double.doubleToRawLongBits(d);
    }
	
	boolean[] bits;
	
	private int writeIndex;
	int readIndex;
	
	public static BitBuffer combine(BitBuffer b1, BitBuffer b2){
		boolean[] bits = new boolean[b1.writeIndex + b2.writeIndex];
		System.arraycopy(b1.bits, 0, bits, 0, b1.writeIndex);
		System.arraycopy(b2.bits, 0, bits, b1.writeIndex, b2.writeIndex);
		return new BitBuffer(bits);
	}

	public BitBuffer(int expectedBits) {
		if(expectedBits == Integer.MAX_VALUE)
			throw new IllegalArgumentException("too many expected bits!");
		bits = new boolean[expectedBits];
	}
	
	public BitBuffer(){
		this(800);
	}
	
	public BitBuffer(boolean[] bits){
		this.bits = bits;
		writeIndex = bits.length;
	}
	
	public BitBuffer(InputStream input) throws IOException{
		bits = new boolean[input.available() * 8];
		int b = input.read();
		while(b >= 0){
			addByte((byte)b);
			b = input.read();
		}
	}
	
	public BitBuffer(byte[] bytes){
		bits = new boolean[bytes.length * 8];
		for(int i = 0; i < bytes.length; i++)
			addByte(bytes[i]);
	}
	
	public BitBuffer(File file) throws IOException {
		byte[] bytes = Files.readAllBytes(file.toPath());
		bits = new boolean[bytes.length * 8];
		for(byte b : bytes)
			addByte(b);
	}
	
	public byte[] toBytes(){
		byte[] value;
		int test = bits.length - ((bits.length / 8) * 8);
		if(test == 0)
			value = new byte[bits.length / 8];
		else
			value = new byte[bits.length / 8 + 1];
		for(int index = 0; index < value.length; index++)
			value[index] = readByte(index * 8);
		return value;
	}
	
	public void save(OutputStream stream) throws IOException {
		for(int index = 0; index < bits.length; index += 8)
			stream.write(readByte(index));
	}
	
	public void save(File file) throws FileNotFoundException, IOException {
		save(new FileOutputStream(file));
	}
	
	public void addBoolean(boolean bool){
		ensureCapacity(writeIndex + 1);
		bits[writeIndex] = bool;
		writeIndex++;
	}
	
	public void addByte(byte b){
		ensureCapacity(writeIndex + 8);
		boolean[] bools = byteToBinary(b);
		for(boolean bool : bools)
			addBoolean(bool);
	}
	
	public void addChar(char c){
		ensureCapacity(writeIndex + 16);
		addByte(char0(c));
		addByte(char1(c));
	}
	
	public void addShort(short s){
		ensureCapacity(writeIndex + 16);
		addByte(short0(s));
		addByte(short1(s));
	}
	
	public void addInt(int i){
		ensureCapacity(writeIndex + 32);
		addByte(int0(i));
		addByte(int1(i));
		addByte(int2(i));
		addByte(int3(i));
	}
	
	public void addFloat(float f){
		addInt(fromFloat(f));
	}
	
	public void addLong(long l){
		ensureCapacity(writeIndex + 64);
		addByte(long0(l));
		addByte(long1(l));
		addByte(long2(l));
		addByte(long3(l));
		addByte(long4(l));
		addByte(long5(l));
		addByte(long6(l));
		addByte(long7(l));
	}
	
	public void addDouble(double d){
		addLong(fromDouble(d));
	}
	
	public void addNumber(long number, byte bitCount, boolean allowNegative){
		boolean[] bools = numberToBinair(number, bitCount, allowNegative);
		ensureCapacity(writeIndex + bools.length);
		System.arraycopy(bools, 0, bits, writeIndex, bools.length);
		writeIndex += bools.length;
	}
	
	public void addNumber(long number, boolean allowNegative){
		if(!allowNegative && number < 0)
			throw new IllegalArgumentException("Number (" + number + " can't be negative!");
		byte bitCount = getRequiredBits(number);
		if(allowNegative)
			bitCount++;
		addNumber(bitCount, (byte) 6, false);
		addNumber(number, bitCount, allowNegative);
	}
	
	public boolean readBoolean(){
		boolean value = bits[readIndex];
		readIndex++;
		return value;
	}
	
	public boolean[] read8Booleans(){
		boolean[] value = new boolean[8];
		for(int i = 0; i < 8; i++)
			value[i] = bits[readIndex + i];
		readIndex += 8;
		return value;
	}
	
	private byte readByte(int index){
		boolean[] value = new boolean[8];
		for(int i = 0; i < 8 && index + i < bits.length; i++)
			value[i] = bits[index + i];
		return byteFromBinary(value);
	}
	
	public byte readByte(){
		return byteFromBinary(read8Booleans());
	}
	
	public char readChar(){
		return makeChar(readByte(), readByte());
	}
	
	public short readShort(){
		return makeShort(readByte(), readByte());
	}
	
	public int readInt(){
		return makeInt(readByte(), readByte(), readByte(), readByte());
	}
	
	public float readFloat(){
		return fromInt(readInt());
	}
	
	public long readLong(){
		return makeLong(readByte(), readByte(), readByte(), readByte(), readByte(), readByte(), readByte(), readByte());
	}
	
	public double readDouble(){
		return fromLong(readLong());
	}
	
	public long readNumber(byte bitCount, boolean allowNegative){
		byte size = bitCount;
		if(allowNegative)
			size++;
		long number = numberFromBinary(Arrays.copyOfRange(bits, readIndex, readIndex + size), bitCount, allowNegative);
		readIndex += size;
		return number;
	}
	
	public long readNumber(boolean allowNegative){
		byte bitCount = (byte) readNumber((byte) 6, false);
		return readNumber(bitCount, allowNegative);
	}
	
	public void skipReading(int amount){
		readIndex += amount;
	}
	
	public int available(){
		return bits.length - readIndex;
	}
	
	private void ensureCapacity(int newCapacity){
		if(bits.length < newCapacity)
			bits = Arrays.copyOf(bits, newCapacity);
	}
}