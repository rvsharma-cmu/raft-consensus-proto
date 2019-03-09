import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

public class BytesUtil {
	
	    /* Object to Bytes */
	    public static byte[] serialize(Object obj) {
	        byte[] stream = null;
	        try (
	                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
	                ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream)
	        ) {
	            objectOutputStream.writeObject(obj);
	            /* For Safety */
	            objectOutputStream.flush();
	            stream = byteArrayOutputStream.toByteArray();
	        } catch (IOException e) {
	            // Error in serialization
	            e.printStackTrace();
	        }
	        return stream;
	    }

	    /* Bytes to Object */
	    // need cast after deserialization
	    public static Object deserialize(byte[] stream) {
	        Object obj = null;

	        try (
	                ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(stream);
	                ObjectInputStream objectInputStream = new ObjectInputStream(byteArrayInputStream)
	             ) {
	            obj =  objectInputStream.readObject();
	        } catch (IOException | ClassNotFoundException e) {
	            // Error in de-serialization
	            e.printStackTrace();
	        }
	        return obj;
	    }



	}

