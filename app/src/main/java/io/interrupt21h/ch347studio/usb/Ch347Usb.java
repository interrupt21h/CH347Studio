package io.interrupt21h.ch347studio.usb;

import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;

import io.interrupt21h.ch347studio.protocol.Ch347Packets;
import io.interrupt21h.ch347studio.protocol.Hex;

import java.io.IOException;
import java.util.Arrays;
import java.util.Locale;

/** Android USB host interface for CH347T vendor-class BULK protocol (M1/M3) and CH347F. */
public final class Ch347Usb implements AutoCloseable {
    public interface Trace { void log(String text); }
    private static final int TIMEOUT = 1300;
    private final UsbDevice device;
    private final UsbDeviceConnection connection;
    private final UsbInterface usbInterface;
    private final UsbEndpoint endpointIn, endpointOut;
    private final Trace trace;
    private boolean closed;

    public static boolean isCh347(UsbDevice d) {
        return d.getVendorId() == 0x1A86 && d.getProductId() >= 0x55DA && d.getProductId() <= 0x55DE;
    }
    public static String modeName(int pid) {
        switch (pid) {
            case 0x55DA: return "M0 · Dual UART";
            case 0x55DB: return "M1 · UART / I²C / SPI";
            case 0x55DC: return "M2 · HID UART / I²C / SPI";
            case 0x55DD: return "M3 · UART / JTAG / I²C";
            case 0x55DE: return "CH347F · Multi-interface";
            default: return "Unrecognized";
        }
    }
    public static String descriptor(UsbDevice d) {
        StringBuilder s = new StringBuilder();
        s.append(String.format(Locale.US, "USB %04X:%04X    Device ID %d\n", d.getVendorId(), d.getProductId(), d.getDeviceId()));
        s.append("Mode: ").append(modeName(d.getProductId())).append('\n');
        s.append("Path: ").append(d.getDeviceName()).append('\n');
        for (int i = 0; i < d.getInterfaceCount(); i++) {
            UsbInterface f = d.getInterface(i);
            s.append(String.format(Locale.US, "\nIF%d id=%d class=%02X sub=%02X proto=%02X\n", i, f.getId(), f.getInterfaceClass(), f.getInterfaceSubclass(), f.getInterfaceProtocol()));
            for (int k = 0; k < f.getEndpointCount(); k++) {
                UsbEndpoint e = f.getEndpoint(k);
                String direction = e.getDirection() == UsbConstants.USB_DIR_IN ? "IN" : "OUT";
                String type = e.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK ? "bulk" :
                    e.getType() == UsbConstants.USB_ENDPOINT_XFER_INT ? "interrupt" : "other";
                s.append(String.format(Locale.US, "  EP 0x%02X %s %s maxPacket=%d\n", e.getAddress(), direction, type, e.getMaxPacketSize()));
            }
        }
        return s.toString();
    }

    public static Ch347Usb connect(UsbManager manager, UsbDevice device, Trace trace) throws IOException {
        if (!isCh347(device)) throw new IOException("Not a recognized CH347 adapter");
        if (device.getProductId() == 0x55DA || device.getProductId() == 0x55DC)
            throw new IOException("This operating mode has no supported vendor BULK I²C/SPI transport. Switch to M1 while unplugged, then reconnect.");
        UsbDeviceConnection connection = manager.openDevice(device);
        if (connection == null) throw new IOException("USB openDevice failed (check permission and OTG)");
        UsbInterface selected = null;
        UsbEndpoint in = null, out = null;
        for (int i = 0; i < device.getInterfaceCount(); i++) {
            UsbInterface f = device.getInterface(i);
            if (f.getInterfaceClass() != UsbConstants.USB_CLASS_VENDOR_SPEC) continue;
            UsbEndpoint candidateIn = null, candidateOut = null;
            for (int k = 0; k < f.getEndpointCount(); k++) {
                UsbEndpoint ep = f.getEndpoint(k);
                if (ep.getType() != UsbConstants.USB_ENDPOINT_XFER_BULK) continue;
                if (ep.getDirection() == UsbConstants.USB_DIR_IN) candidateIn = ep;
                else candidateOut = ep;
            }
            if (candidateIn != null && candidateOut != null) {
                // Later interfaces are the MPSSE-like bridge; UART often occupies earlier interfaces.
                if (selected == null || f.getId() > selected.getId()) {
                    selected = f; in = candidateIn; out = candidateOut;
                }
            }
        }
        if (selected == null || !connection.claimInterface(selected, false)) {
            connection.close();
            throw new IOException("Unable to claim CH347 vendor interface with BULK IN/OUT endpoints");
        }
        trace.log(String.format(Locale.US,"Bridge IF%d EP 0x%02X → 0x%02X", selected.getId(), out.getAddress(), in.getAddress()));
        return new Ch347Usb(device, connection, selected, in, out, trace);
    }

    private Ch347Usb(UsbDevice d, UsbDeviceConnection c, UsbInterface f, UsbEndpoint in, UsbEndpoint out, Trace t) {
        this.device=d; this.connection=c; this.usbInterface=f; this.endpointIn=in; this.endpointOut=out; this.trace=t;
    }

    private synchronized byte[] command(byte[] data, int responseCount) throws IOException {
        if (closed) throw new IOException("Adapter disconnected");
        trace.log("TX " + Hex.format(data));
        int written = connection.bulkTransfer(endpointOut, data, data.length, TIMEOUT);
        if (written != data.length) throw new IOException("USB OUT sent " + written + "/" + data.length + " bytes");
        if (responseCount == 0) return new byte[0];
        byte[] response = new byte[responseCount];
        int received = connection.bulkTransfer(endpointIn, response, responseCount, TIMEOUT);
        if (received != responseCount) throw new IOException("USB IN received " + received + "/" + responseCount + " bytes; check mode, bus wiring and protocol");
        trace.log("RX " + Hex.format(response));
        return response;
    }

    public void i2cSpeed(int speed) throws IOException { command(Ch347Packets.i2cSpeed(speed), 0); }
    public boolean i2cProbe(int addr) throws IOException {
        return command(Ch347Packets.i2cProbe(addr), 1)[0] == 1;
    }
    public void i2cWrite(int addr, byte[] payload) throws IOException {
        byte[] ack = command(Ch347Packets.i2cWrite(addr, payload), 1 + payload.length);
        if (!Ch347Packets.allAck(ack, ack.length))
            throw new IOException(String.format(Locale.US,"I²C NACK at 0x%02X: %s", addr, Hex.format(ack)));
    }
    public byte[] i2cRead(int addr, byte[] registerPrefix, int count) throws IOException {
        byte[] raw = command(Ch347Packets.i2cWriteRead(addr, registerPrefix, count), registerPrefix.length + count + (registerPrefix.length > 0 ? 2 : 1));
        int ackCount = registerPrefix.length + (registerPrefix.length > 0 ? 2 : 1);
        for (int i = 0; i < ackCount; i++) {
            if (raw[i] != 1) throw new IOException(String.format(Locale.US,"I²C NACK at 0x%02X (phase %d)", addr, i));
        }
        return Arrays.copyOfRange(raw, ackCount, raw.length);
    }
    public boolean allowsSpi() { return device.getProductId() == 0x55DB || device.getProductId() == 0x55DE; }

    public byte[] spiTransfer(int mode, int prescaler, int cs, byte[] data) throws IOException {
        if (!allowsSpi()) throw new IOException("SPI is not available in the current operating mode");
        if (data.length < 1 || data.length > 507) throw new IOException("SPI transaction length: 1..507 bytes");
        byte[] current = command(Ch347Packets.getSpiConfig(), 29);
        if ((current[0] & 0xff) != 0xCA || (current[1] & 0xff) != 26 || current[2] != 0)
            throw new IOException("Unexpected SPI configuration response: " + Hex.format(current));
        byte[] config = Arrays.copyOfRange(current, 3, 29);
        byte[] configured = command(Ch347Packets.spiConfig(config, mode, prescaler), 4);
        if ((configured[0] & 0xff) != 0xC0 || configured[3] != 0)
            throw new IOException("SPI configuration rejected: " + Hex.format(configured));
        command(Ch347Packets.spiCs(cs, true), 0);
        try {
            byte[] reply = command(Ch347Packets.spiDuplex(data), data.length + 3);
            return Ch347Packets.parseSpiReply(reply, data.length);
        } finally {
            try { command(Ch347Packets.spiCs(cs, false), 0); }
            catch (IOException ex) { trace.log("WARNING: SPI CS release failed: " + ex.getMessage()); }
        }
    }

    @Override public synchronized void close() {
        if (closed) return;
        closed = true;
        connection.releaseInterface(usbInterface);
        connection.close();
    }
}
