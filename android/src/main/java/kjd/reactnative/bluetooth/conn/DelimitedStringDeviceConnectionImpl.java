package kjd.reactnative.bluetooth.conn;

import android.bluetooth.BluetoothSocket;
import android.os.Build;
import android.util.Base64;
import android.util.Log;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Properties;

/**
 * Implements a {@link DeviceConnection} which manages the received data within a
 * {@StringBuffer}.  Incoming data is stored and parsed as "messages", which by definition are
 * delimited.  
 *
 * When no read listener is available the `byte[]` is processed by adding it to the `buffer'.  When there is
 * a listener, the `buffer` is scanned for all instances of the delimiter, which would result in none or many
 * messages being delivered.
 * 
 * Messages can be read {@link #read()} manually based on the delimiter provided, this is also the case
 * for {@link #available()}.  The {@link #available()} method returns the number of delimited messages
 * available for reading (not the number of bytes available as would normally be the case).
 *
 * Previously setting the delimiter to blank or null would result in no messages being read.  After some 
 * requests it's now possible to provide a blank or null delimiter which will just return all the data
 * (as one message) currently in the buffer.
 *
 * @author kendavidson
 *
 */
public class DelimitedStringDeviceConnectionImpl extends AbstractDeviceConnection {

    /**
     * The buffer in which data is stored.
     */
    private final StringBuffer mBuffer;

    /**
     * The delimiter - easy access from properties.
     */
    private final String mDelimiter;

    /**
     * The charset used to decode the inbound data.
     */
    private final Charset mCharset;

    /**
     * Creates a new {@link AbstractDeviceConnection} to the provided NativeDevice, using the provided
     * Properties.
     *
     * @param socket
     * @param properties
     */
    public DelimitedStringDeviceConnectionImpl(BluetoothSocket socket, Properties properties) throws IOException {
        super(socket, properties);

        this.mBuffer = new StringBuffer();
        this.mDelimiter = StandardOption.DELIMITER.get(properties);
        this.mCharset = StandardOption.DEVICE_CHARSET.get(properties);
    }

    /**
     * Receives `byte[]` and either stores the data for later reads or provides the delimited message(s) to
     * the listener. This method is `synchronized` on the `buffer`.
     * 
     * @param bytes recently read byte[] from the device
     */
    @Override
    protected void receivedData(byte[] bytes) {
        Log.d(this.getClass().getSimpleName(),
                String.format("Received %d bytes from device %s", bytes.length, getDevice().getAddress()));

        synchronized(mBuffer) {
            String msg = "";
            for (int i = 0; i < bytes.length; i++) {
                char ch = (char)Byte.toUnsignedInt(bytes[i]);
                msg += ch;
            }
            mBuffer.append(msg);

            if (mOnDataReceived != null) {
                Log.d(this.getClass().getSimpleName(),
                    "BluetoothEvent.READ listener is registered, providing data");

                String message;
                while ((mBuffer.length() > 0) 
                       && ((message = read()) != null)) {
                    mOnDataReceived.accept(getDevice(), message);
                }
            } else {
                Log.d(this.getClass().getSimpleName(),
                    "No BluetoothEvent.READ listeners are registered, skipping handling of the event");
            }   
        }
    }

    /**
     * Provides the number of full messages (delimiters) available within the buffer.  If the delimiter is
     * blank or null the full length of the buffer is returned.
     *
     * @return the number of messages available or the size of the buffer with no delimiter
     */
    @Override
    public int available() {
        synchronized(mBuffer) {
            int count = 0;
            
            if (mDelimiter == null || mDelimiter.isEmpty()) {
                count = mBuffer.length();
            } else {                
                int lastIndex = -1;
                while ((lastIndex = mBuffer.indexOf(mDelimiter, lastIndex+1)) > -1) {
                    count++;
                }
            }
            return count;   
        }        
    }

    @Override
    public boolean clear() {
        synchronized(mBuffer) {
            mBuffer.delete(0, mBuffer.length());
            return true;   
        }
    }

    /**
     * Reads specified amount of characters from the buffer.
     * This only returns the amount of characters specified if the amount is higher than then remaining characters in the buffer. Otherwise the remainder of the buffer is returned.
     * Should be called in conjunction with {@link #available()}.
     * This method is `synchronized` on the `buffer`.
     *
     * @param amount the number of characters to read
     * @return the specified amount of characters or the remainder of the buffer
     * @throws IOException if an error occurs during reading
     */
    @Override
    public String readMultiple(double amount) {
        synchronized(mBuffer) {
            String message = "";
            if (mBuffer.length() > (int)amount) {
                message = mBuffer.substring(0, (int)amount);
                mBuffer.delete(0, (int)amount);
            }
            else {
                message = mBuffer.substring(0, mBuffer.length());
                mBuffer.delete(0, mBuffer.length());
            }
            return message;
        }       
    }

    /**
     * Reads the next character from the buffer. This only returns the first available
     * character and should be called in conjunction with {@link #available()}.
     * 
     * This method is `synchronized` on the `buffer`.
     *
     * @return the next character from the buffer
     * @throws IOException if an error occurs during reading
     */
    @Override
    public String readOne() {
        synchronized(mBuffer) {
            String message = "";
            if (mBuffer.length() > 0) {
                message = mBuffer.substring(0, 1);
                mBuffer.delete(0, 1);
            }
            return message;
        }        
    }

    /**
     * Reads the next message in from the buffer, based on the configured delimiter (dropping the
     * delimiter) and then removing the data from the Buffer.  This only returns the first available
     * message and should be called in conjunction with {@link #available()}.
     * 
     * This method is `synchronized` on the `buffer`.
     *
     * @return the next message from the buffer or the full buffer if blank/null delimiter
     * @throws IOException if an error occurs during reading
     */
    @Override
    public String read() {
        synchronized(mBuffer) {
            String message = null;
            
            if (mDelimiter == null || mDelimiter.isEmpty()) {
                message = mBuffer.substring(0, mBuffer.length());
                mBuffer.delete(0, mBuffer.length());
            } else {
                int eotIndex = mBuffer.indexOf("\u0004\u0004\u0004", 0);
                int canIndex = mBuffer.indexOf("\u0018\u0018\u0018", 0);
                int index = mBuffer.indexOf(mDelimiter, 0);
                if (index > -1) {
                    message = mBuffer.substring(0, index + mDelimiter.length());
                    mBuffer.delete(0, index + mDelimiter.length());
                }
                else if (eotIndex > -1) {
                    message = mBuffer.substring(0, eotIndex + 3);
                    mBuffer.delete(0, eotIndex + 3);
                }
                else if (canIndex > -1) {
                    message = mBuffer.substring(0, canIndex + 3);
                    mBuffer.delete(0, canIndex + 3);
                }
            }
            return message;
        }        
    }
}
