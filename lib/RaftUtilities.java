package lib;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

public class RaftUtilities {

	public static byte[] serialize(Object obj) {
		byte[] stream = null;
		ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
		ObjectOutputStream objectOutputStream = null;
		try {
			objectOutputStream = new ObjectOutputStream(byteArrayOutputStream);

			objectOutputStream.writeObject(obj);
			objectOutputStream.flush();
			stream = byteArrayOutputStream.toByteArray();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return stream;
	}

	public static Object deserialize(byte[] stream) {
		Object object = null;
		ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(stream);
		ObjectInputStream objectInputStream = null;

		try {
			objectInputStream = new ObjectInputStream(byteArrayInputStream);
			object = objectInputStream.readObject();
		} catch (IOException | ClassNotFoundException e) {
			e.printStackTrace();
		}
		return object;
	}

}