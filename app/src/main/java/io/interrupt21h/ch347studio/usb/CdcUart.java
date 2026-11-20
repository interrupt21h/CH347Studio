package io.interrupt21h.ch347studio.usb;

import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import java.io.IOException;
import java.util.Arrays;

/** Only for ports presenting a standards-compatible USB CDC ACM data/control pair. */
public final class CdcUart implements AutoCloseable {
    private final UsbDeviceConnection connection;
    private final UsbInterface control, data;
    private final UsbEndpoint in, out;
    private volatile boolean closed;

    public static CdcUart tryConnect(UsbManager manager, UsbDevice d, int index, int baud) throws IOException {
        UsbInterface comm = null;
        UsbInterface selected = null;
        UsbEndpoint in = null, out = null;
        int number = 0;
        for (int i = 0; i < d.getInterfaceCount(); i++) {
            UsbInterface f = d.getInterface(i);
            if (f.getInterfaceClass() == UsbConstants.USB_CLASS_COMM) comm = f;
            if (f.getInterfaceClass() != UsbConstants.USB_CLASS_CDC_DATA || comm == null) continue;
            UsbEndpoint cIn = null, cOut = null;
            for (int k = 0; k < f.getEndpointCount(); k++) {
                UsbEndpoint ep = f.getEndpoint(k);
                if (ep.getType() != UsbConstants.USB_ENDPOINT_XFER_BULK) continue;
                if (ep.getDirection() == UsbConstants.USB_DIR_IN) cIn = ep;
                else cOut = ep;
            }
            if (cIn != null && cOut != null && number++ == index) {
                selected = f; in = cIn; out = cOut; break;
            }
        }
        if (selected == null) throw new IOException("No standards-compatible CDC-ACM serial port " + index + " found. CH343 vendor UART and M2 HID UART are not implemented.");
        UsbDeviceConnection connection = manager.openDevice(d);
        if (connection == null) throw new IOException("Unable to open USB device for UART");
        if (!connection.claimInterface(comm, false)) { connection.close(); throw new IOException("Cannot claim CDC control interface"); }
        if (!connection.claimInterface(selected, false)) {
            connection.releaseInterface(comm); connection.close();
            throw new IOException("Cannot claim CDC data interface");
        }
        CdcUart port = new CdcUart(connection, comm, selected, in, out);
        try { port.configure(baud); return port; }
        catch (IOException ex) { port.close(); throw ex; }
    }
    private CdcUart(UsbDeviceConnection c, UsbInterface cm, UsbInterface d, UsbEndpoint i, UsbEndpoint o) {
        connection=c; control=cm; data=d; in=i; out=o;
    }
    private void configure(int baud) throws IOException {
        if (baud < 1200 || baud > 9000000) throw new IOException("UART baud outside 1200..9000000");
        byte[] coding = { (byte)baud, (byte)(baud>>8), (byte)(baud>>16), (byte)(baud>>24), 0, 0, 8 }; // 8N1
        int changed = connection.controlTransfer(0x21, 0x20, 0, control.getId(), coding, 7, 1000);
        if (changed != 7) throw new IOException("CDC SET_LINE_CODING failed (" + changed + ")");
        int enabled = connection.controlTransfer(0x21, 0x22, 3, control.getId(), null, 0, 1000);
        if (enabled < 0) throw new IOException("CDC SET_CONTROL_LINE_STATE failed");
    }
    public byte[] read() throws IOException {
        if (closed) return new byte[0];
        byte[] buffer = new byte[256];
        int received = connection.bulkTransfer(in, buffer, buffer.length, 200);
        if (received < 0) return new byte[0]; // Includes normal read timeout.
        return Arrays.copyOf(buffer, received);
    }
    public synchronized void send(byte[] bytes) throws IOException {
        if (closed) throw new IOException("UART disconnected");
        int offset = 0;
        while (offset < bytes.length) {
            int n = Math.min(256, bytes.length - offset);
            byte[] part = Arrays.copyOfRange(bytes, offset, offset+n);
            int written = connection.bulkTransfer(out, part, part.length, 1000);
            if (written != n) throw new IOException("UART write failed (" + written + "/" + n + ")");
            offset += written;
        }
    }
    @Override public synchronized void close() {
        if (closed) return;
        closed = true;
        connection.releaseInterface(data);
        connection.releaseInterface(control);
        connection.close();
    }
}
