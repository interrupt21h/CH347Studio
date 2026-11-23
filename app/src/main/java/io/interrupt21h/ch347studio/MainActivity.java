package io.interrupt21h.ch347studio;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.WindowInsets;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.app.Activity;

import io.interrupt21h.ch347studio.devices.BusMap;
import io.interrupt21h.ch347studio.devices.DeviceCatalog;
import io.interrupt21h.ch347studio.devices.DeviceScripts;
import io.interrupt21h.ch347studio.devices.EpaperFrames;
import io.interrupt21h.ch347studio.devices.Gc9a01Frames;
import io.interrupt21h.ch347studio.devices.MuxTools;
import io.interrupt21h.ch347studio.ui.BusMatrixView;
import io.interrupt21h.ch347studio.ui.EpaperPreviewView;
import io.interrupt21h.ch347studio.ui.Gc9a01PreviewView;
import io.interrupt21h.ch347studio.protocol.Hex;
import io.interrupt21h.ch347studio.usb.CdcUart;
import io.interrupt21h.ch347studio.usb.Ch347Usb;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Native Android USB-host workbench. No cloud services, telemetry, or permissions beyond USB. */
public final class MainActivity extends Activity {
    private static final String USB_PERMISSION = "io.interrupt21h.ch347studio.USB_PERMISSION";
    private static final int BG = 0xFF0B1120, SURFACE = 0xFF152239, MINT = 0xFF4FE3B5;
    private static final int WHITE = 0xFFEAF1FA, MUTED = 0xFF9AAFC8, BLUE = 0xFF293D58;
    private static final String[] TABS = {"USB", "I²C BUS", "DISPLAYS", "RADIOS", "SENSORS", "SPI", "UART", "JTAG"};

    private UsbManager usbManager;
    private UsbDevice selected;
    private Ch347Usb bridge;
    private volatile CdcUart uart;
    private Thread uartReader;
    private volatile boolean running;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final List<String> events = new ArrayList<>();
    private LinearLayout root, panel, tabStrip;
    private TextView status, eventView, taskView;
    private int currentTab;
    private int selectedAddress = 0x3C;
    private final BusMap busMap = new BusMap();
    private BusMatrixView addressMatrix;
    private EditText selectedAddressField;
    private final AtomicBoolean busy = new AtomicBoolean(false);
    private int spiMode = 0, spiClock = 6, spiCs = 0, spiHardwareAddress = 0;
    // Mux channel maps are kept separate from the upstream bus and from one another.
    private final Map<Integer,BusMap[]> muxMaps=new HashMap<>();
    private int activeMuxAddress=-1,activeMuxChannel=-1;

    private BusMap activeBusMap() {
        if(activeMuxAddress<0 || activeMuxChannel<0) return busMap;
        BusMap[] channels=muxMaps.get(activeMuxAddress);
        if(channels==null){
            channels=new BusMap[8];for(int i=0;i<8;i++)channels[i]=new BusMap();
            muxMaps.put(activeMuxAddress,channels);
        }
        return channels[activeMuxChannel];
    }
    private String routeLabel(){return activeMuxAddress<0?"upstream":
            String.format(Locale.US,"0x%02X / CH%d",activeMuxAddress,activeMuxChannel);}



    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            UsbDevice eventDevice = getExtraDevice(intent);
            if (USB_PERMISSION.equals(action)) {
                boolean allowed = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);
                if (allowed && eventDevice != null) {
                    selected = eventDevice;
                    log("USB permission granted for " + eventDevice.getDeviceName());
                    connect();
                } else { log("USB permission denied"); render(); }
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                if (selected != null && eventDevice != null && selected.getDeviceId() == eventDevice.getDeviceId()) {
                    log("CH347 removed; closing interfaces");
                    disconnect(); selected = null;
                }
                busMap.reset();muxMaps.clear();
                render();
            } else if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                log("USB device attached"); refreshDevices(); render();
            }
        }
    };

    @SuppressWarnings("deprecation")
    private UsbDevice getExtraDevice(Intent intent) {
        if (Build.VERSION.SDK_INT >= 33) return intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice.class);
        return intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        usbManager = (UsbManager) getSystemService(USB_SERVICE);
        IntentFilter f = new IntentFilter();
        f.addAction(USB_PERMISSION);
        f.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        f.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(usbReceiver, f, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(usbReceiver, f);
        refreshDevices();
        log("CH347 Studio ready · Android USB Host");
        render();
        if (getIntent() != null && UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(getIntent().getAction()))
            requestUsb();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        refreshDevices(); render(); requestUsb();
    }

    @Override protected void onDestroy() {
        unregisterReceiver(usbReceiver);
        worker.shutdownNow();
        disconnect();
        super.onDestroy();
    }

    private void refreshDevices() {
        if (usbManager == null) return;
        List<UsbDevice> found = new ArrayList<>();
        for (UsbDevice d : usbManager.getDeviceList().values()) if (Ch347Usb.isCh347(d)) found.add(d);
        Collections.sort(found, (a,b) -> a.getDeviceName().compareTo(b.getDeviceName()));
        if (selected != null) {
            boolean stillHere = false;
            for (UsbDevice d : found) if (d.getDeviceId() == selected.getDeviceId()) stillHere = true;
            if (!stillHere) { disconnect(); selected = null; }
        }
        if (selected == null && !found.isEmpty()) {
            // Prefer vendor-mode hardware for I2C/SPI when several CH347s are attached.
            selected = found.get(0);
            for (UsbDevice d : found) if (d.getProductId() == 0x55DB) { selected = d; break; }
        }
    }

    private void requestUsb() {
        refreshDevices();
        if (selected == null) { log("No CH347 found · attach adapter with USB OTG"); render(); return; }
        if (usbManager.hasPermission(selected)) { connect(); return; }
        Intent permission = new Intent(USB_PERMISSION).setPackage(getPackageName());
        PendingIntent pi = PendingIntent.getBroadcast(this, 0, permission,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
        usbManager.requestPermission(selected, pi);
        log("Requested USB permission");
    }

    private void connect() {
        final UsbDevice d = selected;
        if (d == null || !usbManager.hasPermission(d)) return;
        runWork("Connecting USB bridge", () -> {
            closeBridge();
            busMap.reset();muxMaps.clear();
            int pid = d.getProductId();
            if (pid == 0x55DA || pid == 0x55DC) {
                log("Detected " + Ch347Usb.modeName(pid) + "; native vendor BULK I²C/SPI not available");
            } else {
                bridge = Ch347Usb.connect(usbManager, d, this::log);
                log("Vendor BULK bridge connected");
                bridge.i2cSpeed(1); // Safe default 100 kHz.
                log("I²C speed initialized at 100 kHz");
            }
        });
    }

    private synchronized void closeBridge() {
        if (bridge != null) { bridge.close(); bridge = null; }
    }

    private void disconnect() {
        running = false;
        if (uart != null) { uart.close(); uart = null; }
        closeBridge();
    }

    private interface Work { void run() throws Exception; }
    private void runWork(String title, Work task) {
        if (worker.isShutdown()) return;
        if (!busy.compareAndSet(false, true)) { log("BUSY · Finish the current operation first"); return; }
        setTask(title + "…");
        worker.execute(() -> {
            try { task.run(); setTask("✓ " + title + " complete"); }
            catch (InterruptedException ex) { Thread.currentThread().interrupt(); log("Cancelled · " + title); setTask("Cancelled"); }
            catch (Exception ex) { log("ERROR · " + title + " · " + ex.getMessage()); setTask("⚠ " + ex.getMessage()); }
            finally { busy.set(false); runOnUiThread(this::updateHeader); }
        });
    }
    private void setTask(String value) {
        runOnUiThread(() -> { if (taskView != null) taskView.setText(value); });
    }
    private void log(String message) {
        runOnUiThread(() -> {
            String time = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(new Date());
            events.add(time + "  " + message);
            while (events.size() > 85) events.remove(0);
            if (eventView != null) eventView.setText(String.join("\n", events));
        });
    }

    private void render() {
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(BG);
        root.setPadding(0, dp(12), 0, 0);
        setContentView(root);
        applySystemBarInsets(root);
        LinearLayout heading = new LinearLayout(this);
        heading.setOrientation(LinearLayout.VERTICAL); heading.setPadding(dp(20), dp(8), dp(20), dp(14));
        TextView eyebrow = text("CH347  /  HARDWARE LAB  ·  v0.3.0", 10, MINT, true);
        eyebrow.setLetterSpacing(0.16f); heading.addView(eyebrow);
        TextView title = text("CH347 Studio", 27, WHITE, true); title.setPadding(0, dp(4), 0, dp(6)); heading.addView(title);
        status = text("", 12, MUTED, false); heading.addView(status);
        BusMap shown=activeBusMap();
        TextView overview = text("BUS " + routeLabel().toUpperCase(Locale.US) + "   " +
                shown.count(BusMap.ACK) + " ACK     ·     " + shown.scanned() + "/112 checked", 11, MINT, true);
        overview.setPadding(0, dp(8), 0, 0); heading.addView(overview);
        root.addView(heading);
        HorizontalScrollView tabScroller = new HorizontalScrollView(this);
        tabScroller.setHorizontalScrollBarEnabled(false);
        tabStrip = new LinearLayout(this); tabStrip.setOrientation(LinearLayout.HORIZONTAL);
        tabStrip.setPadding(dp(12), 0, dp(12), dp(10));
        tabScroller.addView(tabStrip); root.addView(tabScroller);
        makeTabs();
        tabScroller.post(() -> {
            if (currentTab >= 0 && currentTab < tabStrip.getChildCount()) {
                View chosen=tabStrip.getChildAt(currentTab);
                tabScroller.smoothScrollTo(Math.max(0,chosen.getLeft()-dp(30)),0);
            }
        });
        ScrollView body = new ScrollView(this); body.setFillViewport(true);
        body.setVerticalScrollBarEnabled(false);
        panel = new LinearLayout(this); panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(17), dp(4), dp(17), dp(26));
        body.addView(panel); root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
        showCurrentPanel(); updateHeader();
    }
    /** API 35+ edge-to-edge: safe areas, display cutouts and keyboard (no cumulative padding). */
    @SuppressWarnings("deprecation")
    private void applySystemBarInsets(View content) {
        final int baseline = dp(12);
        content.setOnApplyWindowInsetsListener((v, insets) -> {
            int l, t, r, b;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                Insets ime = insets.getInsets(WindowInsets.Type.ime());
                l = bars.left; t = bars.top; r = bars.right; b = Math.max(bars.bottom, ime.bottom);
            } else {
                l = insets.getSystemWindowInsetLeft(); t = insets.getSystemWindowInsetTop();
                r = insets.getSystemWindowInsetRight(); b = insets.getSystemWindowInsetBottom();
            }
            v.setPadding(l, baseline + t, r, b);
            return insets;
        });
        content.requestApplyInsets();
    }

    private void updateHeader() {
        if (status == null) return;
        if (selected == null) status.setText("●  NO ADAPTER   ·   Plug in CH347 via OTG");
        else status.setText("●  " + (bridge != null ? "BRIDGE CONNECTED" : "USB DETECTED") + "   ·   " +
                Ch347Usb.modeName(selected.getProductId()));
    }
    private void makeTabs() {
        tabStrip.removeAllViews();
        for (int i = 0; i < TABS.length; i++) {
            final int index = i;
            TextView item = text(TABS[i], 12, currentTab == i ? BG : MUTED, true);
            item.setGravity(Gravity.CENTER);
            item.setPadding(dp(15), dp(11), dp(15), dp(11));
            item.setBackground(shape(currentTab == i ? MINT : SURFACE, 16));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.setMargins(dp(4), 0, dp(4), 0); tabStrip.addView(item, lp);
            item.setOnClickListener(v -> { currentTab = index; render(); });
        }
    }
    private void showCurrentPanel() {
        panel.removeAllViews();
        eventView = null;
        switch (currentTab) {
            case 0: devicePanel(); break;
            case 1: i2cPanel(); break;
            case 2: profilePanel("Display"); break;
            case 3: profilePanel("Radio"); break;
            case 4: profilePanel("Sensor"); break;
            case 5: spiPanel(); break;
            case 6: uartPanel(); break;
            case 7: jtagPanel(); break;
        }
        addLogs();
    }

    private void devicePanel() {
        blockTitle("Device & connectivity", "Inspect the adapter's USB configuration and connect to supported interfaces.");
        LinearLayout c = card();
        if (selected == null) {
            c.addView(text("No CH347 detected", 18, WHITE, true));
            detail(c,"Use a USB-C OTG cable or powered USB hub. Make sure USB Host is enabled on the phone.");
        } else {
            c.addView(text(Ch347Usb.modeName(selected.getProductId()), 18, WHITE, true));
            detail(c, String.format(Locale.US, "VID 1A86  /  PID %04X  /  %d USB interfaces", selected.getProductId(), selected.getInterfaceCount()));
            detail(c, usbManager.hasPermission(selected) ? "USB access authorized" : "USB permission required");
        }
        action(c, bridge == null ? "Connect / Authorize USB" : "Reconnect", true, this::requestUsb);
        action(c, "Rescan USB devices", false, () -> { refreshDevices(); render(); });
        action(c, "Disconnect", false, () -> { disconnect(); render(); });
        LinearLayout modes = card();
        modes.addView(text("Mode selector (physical DIP switches)", 16, WHITE, true));
        detail(modes, "M0  ·  UART0 + UART1\nM1  ·  UART1 + I²C + SPI (bulk USB)\nM2  ·  UART1 + I²C + SPI (HID USB)\nM3  ·  UART1 + JTAG + I²C");
        detail(modes, "Power must be disconnected before changing DIP settings. Mode M1 is recommended for this version of the app.");
        LinearLayout diagnostics = card();
        diagnostics.addView(text("USB interface inspector", 16, WHITE, true));
        String report = selected == null ? "No USB device selected" : Ch347Usb.descriptor(selected);
        TextView dump = text(report, 11, MUTED, false); dump.setTypeface(Typeface.MONOSPACE);
        diagnostics.addView(dump);
        action(diagnostics, "Copy USB descriptors", false, () -> {
            ClipboardManager cb = (ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
            cb.setPrimaryClip(ClipData.newPlainText("CH347 USB descriptors", report)); log("Copied descriptors");
        });
    }

    private void i2cPanel() {
        blockTitle("I²C bus analyzer", "Tap an address to inspect candidates. Green cells ACK; red cells had transfer errors. Address ≠ chip ID.");
        LinearLayout route=card();
        route.addView(text("Bus topology / TCA9548A channels",17,WHITE,true));
        detail(route,"Both photographed TCA9548A modules are the same chip family. Select upstream or an individual switch branch. Downstream scans temporarily isolate that channel and restore the original switch mask even on error.");
        String[] muxAddresses=new String[8];
        for(int i=0;i<8;i++)muxAddresses[i]=String.format(Locale.US,"TCA9548A at 0x%02X  %s",0x70+i,
            busMap.get(0x70+i)==BusMap.ACK?"● detected ACK":"○ unverified");
        Spinner mux=spinner(route,muxAddresses,activeMuxAddress<0?0:activeMuxAddress-0x70);
        Spinner ch=spinner(route,new String[]{"Upstream bus (direct)","Channel 0","Channel 1","Channel 2","Channel 3","Channel 4","Channel 5","Channel 6","Channel 7"},activeMuxChannel<0?0:activeMuxChannel+1);
        action(route,"Switch matrix to selected route",false,()->{
            if(busy.get()) {log("A bus operation is running; switch routes after it finishes");return;}
            int c=ch.getSelectedItemPosition();
            activeMuxAddress=c==0?-1:0x70+mux.getSelectedItemPosition();
            activeMuxChannel=c-1;
            render();
        });
        detail(route,"Current view: "+routeLabel()+". The root scan may include devices behind channels previously enabled by other controllers; address ACK is not a verified chip ID.");
        LinearLayout matrixCard = card();
        BusMap matrix=activeBusMap();
        matrixCard.addView(text("7-bit matrix  /  "+routeLabel()+"  /  0x00–0x7F", 16, WHITE, true));
        detail(matrixCard, matrix.count(BusMap.ACK) + " responding  ·  " + matrix.count(BusMap.ERROR) +
                " errors  ·  " + matrix.scanned() + "/112 scanned   ·   reserved addresses dimmed");
        addressMatrix = new BusMatrixView(this, matrix);
        addressMatrix.setSelectedAddress(selectedAddress);
        matrixCard.addView(addressMatrix, new LinearLayout.LayoutParams(-1, -2));
        TextView selection = text(String.format(Locale.US,"0x%02X • %s",selectedAddress,
                 DeviceCatalog.hint(selectedAddress)), 12, MINT, true);
        selection.setPadding(0, dp(12), 0, dp(6));matrixCard.addView(selection);
        addressMatrix.setOnAddressSelected(address -> {
            selectedAddress = address;
            if (selectedAddressField != null)
                selectedAddressField.setText(String.format(Locale.US,"%02X",address));
            selection.setText(String.format(Locale.US,"0x%02X • %s   [%s]",address,
                DeviceCatalog.hint(address),matrix.get(address)==BusMap.ACK?"ACK": "unverified"));
        });
        action(matrixCard, "Explore profiles for selected address", false, () -> {
            List<DeviceCatalog.Profile> candidates=DeviceCatalog.candidates(selectedAddress);
            if(candidates.isEmpty()) { log("No profile for 0x" + Integer.toHexString(selectedAddress)); return; }
            String[] options=new String[candidates.size()];
            for(int n=0;n<candidates.size();n++)options[n]=candidates.get(n).name;
            new AlertDialog.Builder(this).setTitle(String.format(Locale.US,"0x%02X  ·  candidate profiles",selectedAddress))
                .setItems(options,(dialog,position)->{
                    String category=candidates.get(position).category;
                    currentTab=category.equals("Display")?2:category.equals("Radio")?3:4;
                    render();
                }).setNegativeButton("Cancel",null).show();
        });
        LinearLayout settings = card();
        settings.addView(text("Bus configuration", 16, WHITE, true));
        Spinner speed = spinner(settings, new String[] {"20 kHz", "100 kHz", "400 kHz", "750 kHz"}, 1);
        action(settings, "Apply clock speed", false, () -> runWork("Set I²C speed", () -> {
            Ch347Usb b = requireBridge(); b.i2cSpeed(speed.getSelectedItemPosition());
            log("I²C clock: " + speed.getSelectedItem());
        }));
        LinearLayout scan = card();
        scan.addView(text("Address discovery", 16, WHITE, true));
        detail(scan, "Probes 0x08–0x77 using an address-only write. NACKs are normal; transfer failures are reported separately.");
        TextView addresses = text("No scan performed", 13, MUTED, false); scan.addView(addresses);
        action(scan, "Scan selected route", true, () -> {
            int selectedSpeed = speed.getSelectedItemPosition();
            runWork("I²C scan", () -> {
                Ch347Usb b = requireBridge(); b.i2cSpeed(selectedSpeed);
                List<String> found = new ArrayList<>();
                BusMap scanMap=activeBusMap();
                if(activeMuxAddress>=0) {
                    int muxAddr=activeMuxAddress,muxChannel=activeMuxChannel;
                    MuxTools.scanChannel(rootTransport(),muxAddr,muxChannel,scanMap,(address,state)->{
                        if(state==BusMap.ACK)found.add(String.format(Locale.US,"0x%02X  •  %s",address,DeviceCatalog.hint(address)));
                        if((address&7)==0) {
                            setTask(String.format(Locale.US,"Mux 0x%02X CH%d scan… 0x%02X",muxAddr,muxChannel,address));
                            runOnUiThread(()->{if(addressMatrix!=null)addressMatrix.invalidate();});
                        }
                    });
                } else {
                scanMap.reset();
                for (int a = 0x08; a <= 0x77; a++) {
                    if (Thread.currentThread().isInterrupted() || b != bridge)
                        throw new InterruptedException("Scan interrupted / adapter detached");
                    try {
                        if (b.i2cProbe(a)) {
                            scanMap.record(a, BusMap.ACK);
                            found.add(String.format(Locale.US,"0x%02X  •  %s",a,DeviceCatalog.hint(a)));
                        } else scanMap.record(a, BusMap.EMPTY);
                    } catch (IOException probeError) {
                        scanMap.record(a, BusMap.ERROR);
                        log(String.format(Locale.US,"I²C 0x%02X transfer error: %s",a,probeError.getMessage()));
                    }
                    if ((a & 7) == 0) {
                        setTask(String.format(Locale.US,"Scanning I²C… 0x%02X / 0x77",a));
                        runOnUiThread(() -> { if (addressMatrix != null) addressMatrix.invalidate(); });
                    }
                }
                }
                final String summary=found.isEmpty()?"No ACKs detected. Check SDA/SCL, pull-ups, voltage and adapter mode.":String.join("\n", found);
                log("Scan " + routeLabel() + " complete: " + found.size() + " ACKs, " + scanMap.count(BusMap.ERROR) + " errors");
                runOnUiThread(() -> { addresses.setText(summary); render(); });
            });
        });
        LinearLayout transfer = card();
        transfer.addView(text("Addressed transactions", 16, WHITE, true));
        EditText addr = field(transfer, "7-bit address (hex)", String.format(Locale.US,"%02X",selectedAddress));
        selectedAddressField=addr;
        EditText out = field(transfer, "Write bytes (hex, optional)", "00");
        EditText read = field(transfer, "Read length (1–63)", "8");
        TextView result = text("Response will appear here", 12, MINT, false);
        result.setTypeface(Typeface.MONOSPACE);
        action(transfer, "Write bytes", false, () -> runWork("I²C write", () -> {
            int a = hexAddress(addr.getText().toString());
            byte[] payload = Hex.parse(out.getText().toString());
            Ch347Usb b = requireBridge(); b.i2cSpeed(speed.getSelectedItemPosition());
            transport().write(a, payload);
            log(String.format(Locale.US,"I²C write 0x%02X TX: %s (ACK)", a, Hex.format(payload)));
            runOnUiThread(() -> result.setText("Write ACK  /  " + payload.length + " payload bytes"));
        }));
        action(transfer, "Read bytes", false, () -> runWork("I²C read", () -> {
            int a = hexAddress(addr.getText().toString());
            int count = Integer.parseInt(read.getText().toString().trim());
            Ch347Usb b = requireBridge(); b.i2cSpeed(speed.getSelectedItemPosition());
            byte[] bytes = transport().read(a, new byte[0], count);
            log(String.format(Locale.US,"I²C read 0x%02X RX: %s", a, Hex.format(bytes)));
            runOnUiThread(() -> result.setText("RX  " + Hex.format(bytes)));
        }));
        action(transfer, "Write register → repeated START → read", true, () -> runWork("I²C combined transfer", () -> {
            int a = hexAddress(addr.getText().toString());
            int count = Integer.parseInt(read.getText().toString().trim());
            byte[] prefix = Hex.parse(out.getText().toString());
            Ch347Usb b = requireBridge(); b.i2cSpeed(speed.getSelectedItemPosition());
            byte[] bytes = transport().read(a, prefix, count);
            log(String.format(Locale.US,"I²C combined 0x%02X prefix %s → %s", a, Hex.format(prefix), Hex.format(bytes)));
            runOnUiThread(() -> result.setText("RX  " + Hex.format(bytes)));
        }));
        transfer.addView(result);
        detail(transfer, "A scan or register read may alter the state of some slave devices. Do not connect 5 V signals to a 3.3 V target.");
    }


    private void profilePanel(String category) {
        String title = category.equals("Display") ? "I²C display laboratory" :
                category.equals("Radio") ? "FM radio diagnostics" : "Sensors, muxes & GPIO";
        blockTitle(title,"Bus route: "+routeLabel()+". Select exact module/address; ACK matches are candidate profiles only.");
        if (activeBusMap().scanned() == 0) {
            LinearLayout intro=card();
            intro.addView(text("Start with a bus scan",16,WHITE,true));
            detail(intro,"Open I²C BUS and run the full scan; modules can also be tested with manually selected addresses before scanning.");
            action(intro,"Open bus analyzer",true,()->{currentTab=1;render();});
        }
        if(category.equals("Radio")) {
            LinearLayout c=card();
            c.addView(text("Receiver identification caveat",15,WHITE,true));
            detail(c,"Si4703 and RDA5807M may both acknowledge 0x10. RDA5807M 0x11 chip-ID probing and Si4713 command writes require deliberate selection. No automatic tuning or RF transmission occurs.");
        }
        int num=0;
        for (DeviceCatalog.Profile profile : DeviceCatalog.I2C) {
            if (category.equals("Sensor") ? profile.category.equals("Radio") || profile.category.equals("Display") : !category.equals(profile.category)) continue;
            num++;
            LinearLayout c=card();
            c.addView(text(profile.name,16,WHITE,true));
            detail(c,profile.notes);
            String[] addrLabels=new String[profile.addresses.length];
            int prefer=0;
            for(int k=0;k<profile.addresses.length;k++){
                int address=profile.addresses[k];
                boolean found=activeBusMap().get(address)==BusMap.ACK;
                addrLabels[k]=String.format(Locale.US,"0x%02X  %s",address,found?"● ACK":"○ unverified");
                if(found)prefer=k;
                if(address==selectedAddress)prefer=k;
            }
            Spinner addr=spinner(c,addrLabels,prefer);
            for (DeviceScripts.Script test : DeviceScripts.forProfile(profile.key)) {
                action(c,"▶  " + test.title + (test.risk==DeviceScripts.Risk.READ_ONLY?"":"  ⚠"),
                       test.risk==DeviceScripts.Risk.READ_ONLY,()-> {
                    int index=addr.getSelectedItemPosition();
                    if(index<0)return;
                    selectedAddress=profile.addresses[index];
                    executeI2cScript(test,selectedAddress);
                });
                detail(c,test.detail);
            }
        }
        if(num==0)detail(card(),"No profiles for this category");
    }

    private DeviceScripts.I2c rootTransport() {
        return new DeviceScripts.I2c() {
            @Override public boolean probe(int a) throws IOException {return requireBridge().i2cProbe(a);}
            @Override public byte[] read(int a,byte[] prefix,int count) throws IOException {return requireBridge().i2cRead(a,prefix,count);}
            @Override public void write(int a,byte[] bytes) throws IOException {requireBridge().i2cWrite(a,bytes);}
        };
    }
    private interface I2cOp<T> { T run() throws Exception; }
    private <T> T routed(I2cOp<T> operation) throws IOException {
        try {
            if(activeMuxAddress<0) return operation.run();
            return MuxTools.withChannel(rootTransport(),activeMuxAddress,activeMuxChannel,operation::run);
        } catch(IOException e) {throw e;}
          catch(Exception e) {throw new IOException("Mux operation failed: "+e.getMessage(),e);}
    }
    private DeviceScripts.I2c transport() {
        DeviceScripts.I2c raw=rootTransport();
        return new DeviceScripts.I2c() {
            @Override public boolean probe(int address) throws IOException {return routed(()->raw.probe(address));}
            @Override public byte[] read(int address,byte[] prefix,int count) throws IOException {
                return routed(()->raw.read(address,prefix,count));
            }
            @Override public void write(int address,byte[] bytes) throws IOException {
                routed(()->{raw.write(address,bytes);return null;});
            }
        };
    }

    private void executeI2cScript(DeviceScripts.Script test,int address) {
        Runnable go=()-> runWork(test.title,()-> {
            DeviceScripts.I2c testBus=test.profile.equals("tca9548a")?rootTransport():transport();
            String outcome=DeviceScripts.runI2c(test.id,address,testBus,this::log);
            log(String.format(Locale.US,"%s @ 0x%02X\n%s",test.title,address,outcome));
            runOnUiThread(()-> new AlertDialog.Builder(this).setTitle(test.title)
                    .setMessage(outcome).setPositiveButton("Close",null).show());
        });
        if(test.risk==DeviceScripts.Risk.READ_ONLY)go.run();
        else new AlertDialog.Builder(this).setTitle("Confirm: " + test.title)
                .setMessage(test.detail + "\n\nThis operation may change hardware state. Confirm the exact chip, address, I/O voltage and wiring. Si4713 scripts do not initiate RF transmission.")
                .setNegativeButton("Cancel",null).setPositiveButton("Run test",(dialog,which)->go.run()).show();
    }

    private void executeSpiScript(DeviceScripts.Script test) {
        Runnable go=()->runWork(test.title,()-> {
            String output=DeviceScripts.runSpi(test.id,
                  data -> requireBridge().spiTransfer(spiMode,spiClock,spiCs,data),spiHardwareAddress);
            log(test.title + " • " + output);
            runOnUiThread(()-> new AlertDialog.Builder(this).setTitle(test.title)
                    .setMessage(output).setPositiveButton("Close",null).show());
        });
        if(test.risk==DeviceScripts.Risk.READ_ONLY)go.run();
        else new AlertDialog.Builder(this).setTitle("Verify SPI setup")
                .setMessage(test.detail + "\n\nFor isolated loopback, disconnect ALL targets and connect MOSI to MISO. Confirm 3.3 V device compatibility.")
                .setNegativeButton("Cancel",null).setPositiveButton("Run test",(dialog,which)->go.run()).show();
    }

    private void spiPanel() {
        blockTitle("SPI Console", "Full-duplex SPI transactions with configurable clock phase, polarity and chip select.");
        LinearLayout c = card();
        c.addView(text("Master / transaction settings", 16, WHITE, true));
        Spinner mode = spinner(c, new String[] {"Mode 0 · CPOL 0 / CPHA 0", "Mode 1 · CPOL 0 / CPHA 1", "Mode 2 · CPOL 1 / CPHA 0", "Mode 3 · CPOL 1 / CPHA 1"}, spiMode);
        Spinner clock = spinner(c, new String[] {"60 MHz", "30 MHz", "15 MHz", "7.5 MHz", "3.75 MHz", "1.875 MHz", "937.5 kHz", "468.75 kHz"}, spiClock);
        Spinner cs = spinner(c, new String[] {"CS0", "CS1"}, spiCs);
        EditText bytes = field(c, "MOSI bytes (hex, 1–507)", "9F 00 00 00");
        TextView response = text("MISO response will appear here", 12, MINT, false);
        response.setTypeface(Typeface.MONOSPACE);
        action(c, "Transfer SPI bytes", true, () -> runWork("SPI transfer", () -> {
            Ch347Usb b = requireBridge();
            spiMode = mode.getSelectedItemPosition();spiClock = clock.getSelectedItemPosition();spiCs = cs.getSelectedItemPosition();
            byte[] tx = Hex.parse(bytes.getText().toString());
            byte[] rx = b.spiTransfer(spiMode, spiClock, spiCs, tx);
            log("SPI TX " + Hex.format(tx) + "  RX " + Hex.format(rx));
            runOnUiThread(() -> response.setText("MISO  " + Hex.format(rx)));
        }));
        c.addView(response);
        detail(c, "The adapter handles SPI master mode. Each transfer asserts CS, shifts bytes simultaneously, then deasserts CS. Mode M1 or CH347F required; no SPI in M3.");
        LinearLayout tip = card();
        tip.addView(text("Hardware safety", 16, WHITE, true));
        detail(tip, "Begin at 937.5 kHz with short wires. Ensure GND is shared. Do not connect an unpowered target or exceed its voltage rating. Bytes such as 9F can issue commands to attached flash chips.");
        LinearLayout suites=card();
        suites.addView(text("Hardware-aware SPI diagnostics",17,WHITE,true));
        detail(suites,"SPI devices have NO I²C-style address scan. Read-only register diagnostics use manually selected CS, mode and clock. No automatic writes to a radio or display.");
        Spinner hwAddr=spinner(suites,new String[]{"MCP23S17 address 0","MCP23S17 address 1","MCP23S17 address 2","MCP23S17 address 3","MCP23S17 address 4","MCP23S17 address 5","MCP23S17 address 6","MCP23S17 address 7"},spiHardwareAddress);
        String[][] groups={
            {"spi-nrf24","nRF24L01+PA+LNA  ·  Aideepen", "Read configuration, FIFO/FEATURE and addresses only. Requires stable 3.3 V with adequate current for PA/LNA, CSN/SCK/MOSI/MISO, CE held LOW. TX/RX need a dedicated CE control; no RF transmitted by these scripts."},
            {"spi-mcp23s17","MCP23S17 16-bit SPI GPIO expander", "Only the SPI-labeled chip, NOT MCP23017. SCK/MOSI/MISO/CS, VDD/GND. Hardware A0–A2 selected above; IOCON.HAEN may need external setup. Register snapshot assumes BANK=0."},
            {"spi-flash","SPI NOR flash", "JEDEC / status / SFDP discovery requires actual NOR flash; do not use it on e-paper or TFT controllers."},
            {"spi-tools","Isolated SPI diagnostic", "Loopback MOSI→MISO with ALL external devices disconnected."}
        };
        for(String[] group:groups){
            LinearLayout deviceCard=card();
            deviceCard.addView(text(group[1],16,WHITE,true));
            detail(deviceCard,group[2]);
            for(DeviceScripts.Script test:DeviceScripts.forProfile(group[0])){
                action(deviceCard,"▶  "+test.title,test.risk==DeviceScripts.Risk.READ_ONLY,()->{
                    spiMode=mode.getSelectedItemPosition();spiClock=clock.getSelectedItemPosition();spiCs=cs.getSelectedItemPosition();
                    spiHardwareAddress=hwAddr.getSelectedItemPosition();
                    executeSpiScript(test);
                });
                detail(deviceCard,test.detail);
            }
        }
        LinearLayout gc=card();
        gc.addView(text("1.28-inch GC9A01 round TFT  ·  240×240",18,WHITE,true));
        detail(gc,"From the photographed ShengYang board. It is 4-wire SPI, not I²C. It needs D/C, RESET and optionally BLK GPIO beyond SPI; arbitrary SPI probes cannot identify it safely.");
        detail(gc,"WIRING   SCL→SCK, SDA→MOSI, CS→CS0; D/C, RES, BLK need GPIO. Verify module power requirements; do not connect to 5 V I/O without level shifting.");
        Spinner gcPattern=spinner(gc,new String[]{"Solid black","RGB / CMY bars","Grayscale gradient","Crosshair / checker"},1);
        Gc9a01PreviewView gcPreview=new Gc9a01PreviewView(this);
        gc.addView(gcPreview,new LinearLayout.LayoutParams(-1,-2));
        action(gc,"Preview round TFT pattern",false,()->gcPreview.setPattern(gcPattern.getSelectedItemPosition()));
        action(gc,"Copy GC9A01 wiring / command plan",false,()->{
            ClipboardManager cb=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
            cb.setPrimaryClip(ClipData.newPlainText("GC9A01 SPI wiring",Gc9a01Frames.wiring()));
        });
        detail(gc,"A 240×240 RGB565 frame is 115,200 bytes. This local preview is functional; panel initialization and live pixel transfer are disabled until the required GPIO wiring/controller initialization are validated.");
        LinearLayout epaper = card();
        epaper.addView(text("LAFVIN 2.13-inch e-paper HAT  ·  V4", 18, WHITE, true));
        detail(epaper, "122 × 250 mono pixels. SPI alone is insufficient: D/C, RESET and BUSY must also be controlled and observed. The CH347 cannot identify this write-only display through a JEDEC ID request.");
        detail(epaper, "WIRING   3.3V / GND / MOSI(DIN) / SCLK / CS + separate D/C / RST / BUSY. Verify voltage on your exact breakout. Full refresh is disabled until GPIO control is implemented and validated.");
        Spinner patterns = spinner(epaper, new String[]{"Blank white", "Checkerboard", "Vertical bars", "Alignment / crosshair"}, 1);
        EpaperPreviewView preview = new EpaperPreviewView(this);
        epaper.addView(preview, new LinearLayout.LayoutParams(-1,-2));
        action(epaper, "Preview pattern locally", false, () -> preview.setPattern(patterns.getSelectedItemPosition()));
        action(epaper, "Copy 4,000-byte framebuffer (hex)", false, () -> {
            byte[] frame=EpaperFrames.make(patterns.getSelectedItemPosition());
            ClipboardManager cb=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
            cb.setPrimaryClip(ClipData.newPlainText("LAFVIN 122x250 1bpp frame",Hex.format(frame)));
            log("Copied simulated e-paper frame: " + frame.length + " bytes");
        });
        action(epaper, "Copy e-paper command plan", false, () -> {
            String plan="LAFVIN 2.13 V4 122x250: RESET pulse, wait BUSY idle; initialize controller per revision; D/C LOW command 0x24; D/C HIGH send 4000 frame bytes; D/C LOW 0x22 then D/C HIGH 0xF7; command 0x20 and wait BUSY. Do not run without verified GPIO timing/pin map.";
            ClipboardManager cb=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
            cb.setPrimaryClip(ClipData.newPlainText("e-paper SPI + GPIO control plan",plan));
        });
    }

    private void uartPanel() {
        blockTitle("UART Terminal", "Simple 8N1 serial console for standards-compatible CDC ACM interfaces.");
        LinearLayout c = card();
        c.addView(text("Port settings", 16, WHITE, true));
        Spinner port = spinner(c, new String[] {"CDC port 0", "CDC port 1"}, 0);
        Spinner baud = spinner(c, new String[] {"9600", "19200", "38400", "57600", "115200", "230400", "460800", "921600"}, 4);
        action(c, uart == null ? "Open CDC UART" : "Close UART", true, () -> {
            if (uart != null) { closeUart(); log("UART closed"); render(); return; }
            runWork("Open UART", () -> {
                if (selected == null || !usbManager.hasPermission(selected)) throw new IOException("Authorize USB first");
                uart = CdcUart.tryConnect(usbManager, selected, port.getSelectedItemPosition(), Integer.parseInt(baud.getSelectedItem().toString()));
                log("CDC UART opened at " + baud.getSelectedItem() + " 8N1");
                startUartReader();
                runOnUiThread(this::render);
            });
        });
        EditText text = field(c, "Text to send (UTF-8)", "help");
        action(c, "Send text + newline", false, () -> runWork("UART send", () -> {
            CdcUart p = requireUart();
            String message = text.getText().toString() + "\n";
            p.send(message.getBytes(StandardCharsets.UTF_8));
            log("UART TX " + message.replace("\n", "\\n"));
        }));
        EditText hex = field(c, "Hex bytes to send", "00 FF");
        action(c, "Send raw hex", false, () -> runWork("UART hex send", () -> {
            CdcUart p = requireUart(); byte[] data = Hex.parse(hex.getText().toString());
            p.send(data); log("UART TX " + Hex.format(data));
        }));
        detail(c, "CDC only in this release. WCH-specific CH343 UART controls and M2 HID UART require a separate backend; an absent CDC interface is reported, not treated as success.");
    }

    private void startUartReader() {
        running = true;
        uartReader = new Thread(() -> {
            while (running && uart != null) {
                try {
                    byte[] data = uart.read();
                    if (data.length > 0) log("UART RX " + Hex.format(data) + "  |  " + new String(data, StandardCharsets.UTF_8).replace("\n", "↵"));
                } catch (IOException ex) {
                    log("UART receive error: " + ex.getMessage()); running = false;
                }
            }
        }, "CH347-UART-RX");
        uartReader.setDaemon(true); uartReader.start();
    }
    private void closeUart() { running = false; if (uart != null) { uart.close(); uart = null; } }

    private void jtagPanel() {
        blockTitle("JTAG & protocol modes", "Connector inspection and software capability overview.");
        LinearLayout c = card();
        c.addView(text("JTAG — M3", 18, WHITE, true));
        detail(c,"The adapter supports TCK, TMS, TDI, TDO and optional TRST. Disconnect power before selecting M3. Android can enumerate the M3 vendor interface, but this release does not execute JTAG scans or program devices.");
        detail(c,"For JTAG programming/debugging, the CH347 currently has a dedicated OpenOCD driver on desktop platforms. A validated Android TAP-state and scan implementation is required before enabling these controls.");
        LinearLayout mode = card();
        mode.addView(text("Transport status", 16, WHITE, true));
        detail(mode,"M0   USB serial descriptor inspection\nM1   I²C / SPI vendor BULK implemented\nM2   HID descriptors only; no commands sent\nM3   I²C vendor BULK implemented; JTAG pending\nCH347F   Vendor BULK bridge experimental\nUART   CDC-ACM where a compatible interface exists");
        detail(mode,"All I²C/SPI operations happen locally over USB. No cloud services, root access, or network permissions are requested.");
    }

    private void addLogs() {
        LinearLayout c = card();
        c.addView(text("Activity & USB trace", 16, WHITE, true));
        detail(c, "Commands and responses appear here, including hardware errors. Last 85 events retained in memory.");
        taskView = text("Ready", 12, MINT, false); c.addView(taskView);
        eventView = text(String.join("\n", events), 11, MUTED, false);
        eventView.setTypeface(Typeface.MONOSPACE); eventView.setTextIsSelectable(true);
        eventView.setPadding(0, dp(8), 0, dp(7)); c.addView(eventView);
        action(c, "Copy event log", false, () -> {
            ClipboardManager cb = (ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
            cb.setPrimaryClip(ClipData.newPlainText("CH347 Studio events", String.join("\n", events)));
        });
        action(c, "Clear log", false, () -> { events.clear(); eventView.setText(""); });
    }

    private Ch347Usb requireBridge() throws IOException {
        Ch347Usb b = bridge;
        if (b == null) throw new IOException("Bridge not connected. Use Device → Connect, and M1 for I²C/SPI.");
        return b;
    }
    private CdcUart requireUart() throws IOException {
        CdcUart p = uart;
        if (p == null) throw new IOException("No CDC UART open");
        return p;
    }
    private static int hexAddress(String s) {
        String v = s.trim().toLowerCase(Locale.US).replace("0x", "");
        if (v.isEmpty()) throw new IllegalArgumentException("Specify I²C 7-bit address");
        return Integer.parseInt(v, 16);
    }
    private static String commonI2cHint(int address) {
        switch (address) {
            case 0x10: return "  · common: Si4703 / other";
            case 0x48: return "  · common: ADC / temperature";
            case 0x50: return "  · common: 24xx EEPROM";
            case 0x68: return "  · common: RTC / IMU";
            case 0x76: case 0x77: return "  · common: environmental sensor";
            default: return "";
        }
    }

    private void blockTitle(String heading, String summary) {
        TextView h = text(heading, 21, WHITE, true); h.setPadding(dp(3), dp(8), 0, dp(5)); panel.addView(h);
        TextView s = text(summary, 13, MUTED, false); s.setPadding(dp(3), 0, dp(2), dp(10)); panel.addView(s);
    }
    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this); c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(16), dp(17), dp(16), dp(17)); c.setBackground(shape(SURFACE, 18));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.setMargins(0, dp(7), 0, dp(11));
        panel.addView(c, lp); return c;
    }
    private void detail(LinearLayout into, String contents) {
        TextView t = text(contents, 13, MUTED, false);
        t.setLineSpacing(dp(3), 1f); t.setPadding(0, dp(9), 0, dp(6)); into.addView(t);
    }
    private EditText field(LinearLayout into, String hint, String value) {
        TextView label = text(hint, 12, MUTED, true); label.setPadding(0, dp(11), 0, dp(4)); into.addView(label);
        EditText e = new EditText(this); e.setSingleLine(true); e.setTextSize(14); e.setTextColor(WHITE);
        e.setSelectAllOnFocus(true); e.setHintTextColor(MUTED);
        e.setBackground(shape(BLUE, 10)); e.setText(value); e.setPadding(dp(12), dp(9), dp(12), dp(9));
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        e.setImeOptions(EditorInfo.IME_ACTION_DONE);
        into.addView(e, new LinearLayout.LayoutParams(-1, dp(47))); return e;
    }
    private Spinner spinner(LinearLayout into, String[] options, int selected) {
        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> a = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, options) {
            @Override public View getView(int position, View convertView, ViewGroup parent) {
                TextView t = text(getItem(position), 14, WHITE, false); t.setPadding(dp(12), dp(10), dp(10), dp(10)); return t;
            }
            @Override public View getDropDownView(int position, View convertView, ViewGroup parent) {
                TextView t = text(getItem(position), 14, WHITE, false); t.setBackgroundColor(BLUE);
                t.setPadding(dp(16), dp(12), dp(8), dp(12)); return t;
            }
        };
        spinner.setAdapter(a); spinner.setSelection(selected); spinner.setBackground(shape(BLUE, 10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(48)); lp.setMargins(0, dp(7), 0, dp(7));
        into.addView(spinner, lp); return spinner;
    }
    private void action(LinearLayout into, String title, boolean primary, Runnable click) {
        Button button = new Button(this);
        button.setText(title); button.setTextSize(13); button.setAllCaps(false);
        button.setTextColor(primary ? BG : WHITE); button.setTypeface(null, Typeface.BOLD);
        button.setBackground(shape(primary ? MINT : BLUE, 11));
        button.setPadding(dp(8), dp(8), dp(8), dp(8)); button.setMinHeight(dp(44));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(46)); lp.setMargins(0, dp(6), 0, dp(2));
        into.addView(button, lp); button.setOnClickListener(v -> click.run());
    }
    private TextView text(String value, int sp, int color, boolean bold) {
        TextView t = new TextView(this); t.setText(value); t.setTextSize(sp); t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }
    private GradientDrawable shape(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color); drawable.setCornerRadius(dp(radiusDp)); return drawable;
    }
    private int dp(int d) { return (int)(getResources().getDisplayMetrics().density * d + 0.5f); }
}
