package org.bdj;

import java.awt.Color;
import java.awt.Container;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Shape;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.util.StringTokenizer;
import java.util.Vector;
import org.dvb.ui.FontFactory;

public class Screen extends Container {
    private static final long serialVersionUID = 0x4141414141414141L;

    private static final int DESIGN_W = 1920;
    private static final int DESIGN_H = 1080;
    private static final int SHELL_PAD_X = 72;
    private static final int SHELL_PAD_Y = 48;
    private static final int CONTENT_W = 1740;
    private static final int GAP = 18;

    private static final Color PS_BG = new Color(0x05030f);
    private static final Color PS_PANEL_TOP = new Color(0x1b5fc6);
    private static final Color PS_PANEL_BOTTOM = new Color(0x05083f);
    private static final Color PS_PANEL_DARK = new Color(0, 0, 38, 230);
    private static final Color PS_WHITE = new Color(0xf7f7ff);
    private static final Color PS_GREY = new Color(0xaaa9af);
    private static final Color PS_SHADOW = new Color(0, 0, 0, 155);
    private static final Color PS_YELLOW = new Color(0xf4f251);
    private static final Color PS_CYAN = new Color(0x49a7c6);
    private static final Color PS_RED = new Color(0xe84437);
    private static final Color PS_GREEN = new Color(0x4ea35b);
    private static final Color PS_ORANGE = new Color(0xf19c42);
    private static final Color PS_PINK = new Color(0xd592bd);
    private static final Color PS_TRIANGLE = new Color(0x20af93);
    private static final Color PS_BLUE = new Color(0x9da5d5);
    private static final Color PS_DIM = new Color(247, 247, 255, 150);

    private static final String[] STAGE_MESSAGES = {
        "STANDING BY",
        "RUNNING USERLAND EXPLOIT",
        "CHAIN INITIALIZED",
        "UNSTABLE PRIMITIVE ACHIEVED",
        "RUNNING KERNEL EXPLOIT",
        "KERNEL EXPLOIT FINISHED",
        "AUTOLOAD MANIFEST READY",
        "PAYLOAD QUEUE RUNNING",
        "AUTOLOAD FINISHED"
    };

    private static final Color[] STAGE_COLORS = {
        PS_YELLOW, PS_YELLOW, PS_CYAN, PS_GREEN, PS_ORANGE,
        PS_GREEN, PS_CYAN, PS_YELLOW, PS_GREEN
    };

    private static final int[] STAGE_PCTS = {
        0, 13, 25, 38, 50, 63, 75, 88, 100
    };

    public static class MessageType {
        public static final MessageType INFO = new MessageType(PS_WHITE);
        public static final MessageType SUCCESS = new MessageType(PS_GREEN);
        public static final MessageType ERROR = new MessageType(PS_RED);
        public static final MessageType WARNING = new MessageType(PS_ORANGE);
        final Color color;
        private MessageType(Color c) { color = c; }
    }

    private static class Message {
        final String text;
        final Color color;
        Message(String t, Color c) { text = t; color = c; }
    }

    private final Font titleFont = loadDvbFont("FinalFantasyVII", 82, "Monospaced");
    private final Font badgeFont = loadDvbFont("PixelCyr", 18, "Monospaced");
    private final Font statusFont = loadDvbFont("FinalFantasyVII", 48, "Monospaced");
    private final Font moduleLabelFont = loadDvbFont("PixelCyr", 13, "Monospaced");
    private final Font moduleValueFont = loadDvbFont("FinalFantasyVII", 44, "Monospaced");
    private final Font logFont = loadDvbFont("FinalFantasyVII", 30, "Monospaced");
    private final Font progressFont = loadDvbFont("PixelCyr", 16, "Monospaced");

    private final Vector messages = new Vector();
    private volatile boolean isPainting = false;
    private volatile boolean isDirty = false;

    private int progressPercent = 0;
    private String progressLabel = "BOOTING...";
    private String title = "BDJB AUTOLOADER";
    private int stageIndex = 0;
    private int displayedProgressPercent = 0;
    private int displayedStagePercent = 0;
    private int displayedSandboxPercent = 0;
    private int displayedKernelPercent = 0;
    private int displayedPayloadPercent = 0;

    private final String[] moduleValues = { "INITIALIZING", "STANDING BY", "QUEUE READY" };
    private final Color[] moduleColors = { PS_YELLOW, PS_RED, PS_CYAN };
    private static final String[] MODULE_LABELS = {
        "BD-J CHAIN", "KERNEL PATCH", "SONIC AUTOLOAD"
    };

    private volatile int animTick = 0;
    private volatile boolean animRunning = false;

    private static final Screen instance = new Screen();

    private Screen() {
        super();
        setBackground(PS_BG);
        setForeground(PS_WHITE);
        addComponentListener(new ComponentAdapter() {
            public void componentShown(ComponentEvent e) { isDirty = true; safeRepaint(); }
            public void componentHidden(ComponentEvent e) {}
        });
    }

    public void startAnimations() {
        if (animRunning) return;
        animRunning = true;
        Thread t = new Thread(new Runnable() {
            public void run() {
                while (animRunning) {
                    try { Thread.sleep(120); } catch (InterruptedException e) { break; }
                    synchronized (messages) {
                        animTick++;
                        animateDisplayStateLocked();
                        isDirty = true;
                    }
                    safeRepaint();
                }
            }
        }, "bdjb-anim");
        t.setDaemon(true);
        t.start();
    }

    public static Screen getInstance() { return instance; }

    public static void println(String msg) { println(msg, true, false); }

    public static void println(String msg, boolean repaint, boolean replaceLast) {
        getInstance().print(msg, MessageType.INFO, repaint, replaceLast);
    }

    public void print(String msg, boolean repaint, boolean replaceLast) {
        print(msg, MessageType.INFO, repaint, replaceLast);
    }

    public void print(String msg, MessageType type, boolean repaint, boolean replaceLast) {
        if (msg == null) msg = "null";
        synchronized (messages) {
            if (replaceLast && messages.size() > 0) {
                messages.removeElementAt(messages.size() - 1);
            }
            messages.addElement(new Message(msg, type.color));
            while (messages.size() > 22) {
                messages.removeElementAt(0);
            }
            advanceStageFromMessage(msg, type);
            isDirty = true;
        }
        if (repaint) safeRepaint();
    }

    public void setTitle(String t) {
        synchronized (messages) {
            title = t != null ? t.toUpperCase() : "";
            isDirty = true;
        }
        safeRepaint();
    }

    public void setProgress(int pct, String label) {
        synchronized (messages) {
            setProgressLocked(pct, label != null ? label.toUpperCase() : "");
            isDirty = true;
        }
        safeRepaint();
    }

    private void setProgressLocked(int pct, String label) {
        progressPercent = Math.max(0, Math.min(100, pct));
        progressLabel = label != null && label.length() > 0 ? label : "BOOTING...";
        stageIndex = stageForPercent(progressPercent);
        updateModulesForStage(stageIndex);
    }

    private void animateDisplayStateLocked() {
        int stageSafe = Math.max(0, Math.min(STAGE_MESSAGES.length - 1, stageIndex));
        displayedProgressPercent = approach(displayedProgressPercent, progressPercent);
        displayedStagePercent = approach(displayedStagePercent,
            stageSafe * 100 / Math.max(1, STAGE_MESSAGES.length - 1));
        displayedSandboxPercent = approach(displayedSandboxPercent, sandboxPercent(stageSafe));
        displayedKernelPercent = approach(displayedKernelPercent, kernelPercent(stageSafe));
        displayedPayloadPercent = approach(displayedPayloadPercent, payloadPercent(stageSafe, progressPercent));
    }

    private int approach(int current, int target) {
        if (current == target) return current;
        int diff = target - current;
        int step = Math.max(1, Math.abs(diff) / 4);
        return diff > 0 ? Math.min(target, current + step) : Math.max(target, current - step);
    }

    private void advanceStageFromMessage(String msg, MessageType type) {
        String lower = msg.toLowerCase();
        int nextStage = -1;

        if (type == MessageType.ERROR || lower.indexOf("failed") >= 0 || lower.indexOf("error") >= 0) {
            progressLabel = "ERROR";
            moduleColors[1] = PS_RED;
            return;
        }

        // Stage 1: BD-J xlet started, userland / sandbox phase
        if (lower.indexOf("bd-j init") >= 0 || lower.indexOf("userland") >= 0 ||
            lower.indexOf("screen initialized") >= 0 || lower.indexOf("sandbox") >= 0 ||
            lower.indexOf("triggering") >= 0) {
            nextStage = Math.max(nextStage, 1);
        }
        // Stage 2: chain initialized — sandbox escape confirmed
        if (lower.indexOf("exploit success") >= 0 || lower.indexOf("escape achieved") >= 0 ||
            lower.indexOf("chain initialized") >= 0) {
            nextStage = Math.max(nextStage, 2);
        }
        // Stage 3: primitive / pre-kernel prep
        if (lower.indexOf("primitive") >= 0 || lower.indexOf("unstable") >= 0) {
            nextStage = Math.max(nextStage, 3);
        }
        // Stage 4: kernel exploit running (Poopsloit IS the kernel exploit)
        if (lower.indexOf("poopsloit") >= 0 || lower.indexOf("running kernel") >= 0 ||
            lower.indexOf("starting kernel") >= 0 || lower.indexOf("kernel exploit") >= 0) {
            // Only advance to 4, not 5, unless "finished" or "patched" is present
            if (lower.indexOf("finished") < 0 && lower.indexOf("patched") < 0) {
                nextStage = Math.max(nextStage, 4);
            }
        }
        // Stage 5: kernel exploit finished
        if ((lower.indexOf("kernel") >= 0 || lower.indexOf("poopsloit") >= 0) &&
            (lower.indexOf("finished") >= 0 || lower.indexOf("patched") >= 0)) {
            nextStage = Math.max(nextStage, 5);
        }
        // Stage 6: autoload manifest / sonic-loader config ready
        if (lower.indexOf("manifest") >= 0 || lower.indexOf("autoload config") >= 0 ||
            lower.indexOf("autoload.txt") >= 0 || lower.indexOf("sonic-loader") >= 0 ||
            lower.indexOf("sonic loader") >= 0 || lower.indexOf("killdiscplayer") >= 0) {
            nextStage = Math.max(nextStage, 6);
        }
        // Stage 7: payload/ELF queue starting — explicit loading phrases only
        if (lower.indexOf("loading elf") >= 0 || lower.indexOf("elf loader") >= 0 ||
            lower.indexOf("loading jar") >= 0 || lower.indexOf("loading payload") >= 0 ||
            lower.indexOf("launching payload") >= 0 || lower.indexOf("payload queue") >= 0) {
            nextStage = Math.max(nextStage, 7);
        }
        // Stage 8: fully complete — very specific phrases only
        if (lower.indexOf("autoload finished") >= 0 || lower.indexOf("autoload complete") >= 0 ||
            lower.indexOf("all payloads loaded") >= 0 || lower.indexOf("execution completed") >= 0) {
            nextStage = Math.max(nextStage, 8);
        }

        if (nextStage > stageIndex) {
            stageIndex = nextStage;
            progressPercent = STAGE_PCTS[stageIndex];
            progressLabel = STAGE_MESSAGES[stageIndex];
            updateModulesForStage(stageIndex);
        }
    }

    private int stageForPercent(int pct) {
        int stage = 0;
        for (int i = 0; i < STAGE_PCTS.length; i++) {
            if (pct >= STAGE_PCTS[i]) stage = i;
        }
        return stage;
    }

    private void updateModulesForStage(int stage) {
        if (stage >= 8) {
            moduleValues[0] = "COMPLETE";
            moduleValues[1] = "PATCHED";
            moduleValues[2] = "RUNNING";
            moduleColors[0] = PS_GREEN;
            moduleColors[1] = PS_GREEN;
            moduleColors[2] = PS_CYAN;
        } else if (stage >= 6) {
            moduleValues[0] = "BYPASSED";
            moduleValues[1] = "PATCHED";
            moduleValues[2] = "LOADING";
            moduleColors[0] = PS_GREEN;
            moduleColors[1] = PS_GREEN;
            moduleColors[2] = PS_CYAN;
        } else if (stage >= 4) {
            moduleValues[0] = "BYPASSED";
            moduleValues[1] = "PATCHING";
            moduleValues[2] = "QUEUE READY";
            moduleColors[0] = PS_GREEN;
            moduleColors[1] = PS_RED;
            moduleColors[2] = PS_CYAN;
        } else if (stage >= 2) {
            moduleValues[0] = "ACTIVE";
            moduleValues[1] = "STANDING BY";
            moduleValues[2] = "QUEUE READY";
            moduleColors[0] = PS_YELLOW;
            moduleColors[1] = PS_RED;
            moduleColors[2] = PS_CYAN;
        } else {
            moduleValues[0] = "INITIALIZING";
            moduleValues[1] = "STANDING BY";
            moduleValues[2] = "QUEUE READY";
            moduleColors[0] = PS_YELLOW;
            moduleColors[1] = PS_RED;
            moduleColors[2] = PS_CYAN;
        }
    }

    public static void printStackTrace(String msg, Throwable e) {
        println(msg, true, false);
        getInstance().printStackTrace(e);
    }

    public void printStackTrace(Throwable e) {
        if (e == null) { print("null exception", true, false); return; }
        StringTokenizer st; StringBuffer sb;
        try {
            StringWriter sw = new StringWriter();
            try {
                PrintWriter pw = new PrintWriter(sw);
                try { e.printStackTrace(pw); pw.flush(); } finally { pw.close(); }
                st = new StringTokenizer(sw.toString(), "\n", false);
                sb = new StringBuffer();
            } finally { sw.close(); }
            Vector lines = new Vector();
            while (st.hasMoreTokens()) {
                String line = st.nextToken(); sb.setLength(0);
                for (int i = 0; i < line.length(); i++) {
                    char c = line.charAt(i);
                    if (c == '\t') sb.append("   ");
                    else if (c != '\r') sb.append(c);
                }
                lines.addElement(sb.toString());
            }
            synchronized (messages) {
                for (int i = 0; i < lines.size(); i++) {
                    print((String) lines.elementAt(i), false, false);
                }
            }
            safeRepaint();
        } catch (IOException ex) { printThrowable(e); }
    }

    public void printThrowable(Throwable e) {
        if (e == null) { print("null throwable", true, false); return; }
        String m = e.getMessage();
        print(e.getClass().getName() + ": " + (m != null ? m : ""), true, false);
    }

    private void safeRepaint() {
        if (EventQueue.isDispatchThread()) {
            if (isDisplayable()) repaint();
        } else {
            EventQueue.invokeLater(new Runnable() {
                public void run() { if (isDisplayable()) repaint(); }
            });
        }
    }

    public void update(Graphics g) { paint(g); }

    public void paint(Graphics g) {
        if (g == null) return;

        Vector msgCopy;
        int pct;
        String pLabel;
        String ttl;
        String[] modVals;
        Color[] modCols;
        int tick;
        int stage;
        int displayPct;
        int displayStagePct;
        int displaySandboxPct;
        int displayKernelPct;
        int displayPayloadPct;
        boolean needRepaint = false;

        synchronized (messages) {
            if (isPainting) return;
            isPainting = true;
            isDirty = false;
            msgCopy = new Vector(messages);
            pct = progressPercent;
            pLabel = progressLabel;
            ttl = title;
            stage = stageIndex;
            displayPct = displayedProgressPercent;
            displayStagePct = displayedStagePercent;
            displaySandboxPct = displayedSandboxPercent;
            displayKernelPct = displayedKernelPercent;
            displayPayloadPct = displayedPayloadPercent;
            modVals = new String[] { moduleValues[0], moduleValues[1], moduleValues[2] };
            modCols = new Color[] { moduleColors[0], moduleColors[1], moduleColors[2] };
            tick = animTick;
        }

        try {
            int W = getWidth();
            int H = getHeight();
            if (W <= 0 || H <= 0) return;

            g.setColor(PS_BG);
            g.fillRect(0, 0, W, H);

            double sx = W / (double) DESIGN_W;
            double sy = H / (double) DESIGN_H;
            double scale = sx < sy ? sx : sy;
            int shellW = (int) (DESIGN_W * scale);
            int shellH = (int) (DESIGN_H * scale);
            int ox = (W - shellW) / 2;
            int oy = (H - shellH) / 2;

            try {
                drawScaledShell(g, ox, oy, scale, msgCopy, pct, displayPct, displayStagePct,
                                displaySandboxPct, displayKernelPct, displayPayloadPct,
                                pLabel, ttl, modVals, modCols, tick, stage);
            } catch (Throwable t) {
                drawPaintFailure(g, ox, oy, shellW, shellH, t);
            }
        } finally {
            synchronized (messages) {
                isPainting = false;
                if (isDirty) needRepaint = true;
            }
            if (needRepaint) safeRepaint();
        }
    }

    private void drawScaledShell(Graphics g, int ox, int oy, double s, Vector msgCopy, int pct,
                                 int displayPct, int displayStagePct, int displaySandboxPct,
                                 int displayKernelPct, int displayPayloadPct, String pLabel,
                                 String ttl, String[] modVals, Color[] modCols,
                                 int tick, int stage) {
        int shellW = sc(DESIGN_W, s);
        int shellH = sc(DESIGN_H, s);
        drawSafeDashboard(g, ox, oy, shellW, shellH, msgCopy, pct, displayPct, displayStagePct,
                          displaySandboxPct, displayKernelPct, displayPayloadPct, pLabel, stage, tick);
    }

    private void drawExperimentalShell(Graphics g, int ox, int oy, double s, Vector msgCopy, int pct,
                                       String pLabel, String ttl, String[] modVals, Color[] modCols,
                                       int tick, int stage) {
        int shellW = sc(DESIGN_W, s);
        int shellH = sc(DESIGN_H, s);
        int contentW = sc(CONTENT_W, s);
        int cx = ox + (shellW - contentW) / 2;
        int top = oy + sc(24, s);

        drawBackground(g, ox, oy, shellW, shellH, s, tick);
        drawControllerRails(g, ox, oy, shellW, shellH, s, tick);

        int y = top;
        y = drawTitle(g, ttl, ox, shellW, y, s, tick);
        y += sc(GAP, s);
        y = drawEffectsRow(g, cx, oy, contentW, y, s, tick, stage);
        y += sc(GAP, s);
        y = drawStageRail(g, cx, y, contentW, stage, tick, s);
        y += sc(GAP, s);

        int mainH = sc(240, s);
        int leftW = sc(560, s);
        int mainGap = sc(GAP, s);
        int rightW = contentW - leftW - mainGap;
        drawChainCard(g, cx, y, leftW, mainH, pct, pLabel, stage, tick, s);
        int rightY = y;
        rightY = drawModuleGrid(g, cx + leftW + mainGap, rightY, rightW, modVals, modCols, tick, s, stage);
        rightY += sc(GAP, s);
        drawActionCards(g, cx + leftW + mainGap, rightY, rightW, mainH - (rightY - y), stage, tick, s);
        y += mainH;

        y += sc(GAP, s);
        y = drawLog(g, cx, y, contentW, msgCopy, s);
        y += sc(GAP, s);
        drawProgress(g, cx, y, contentW, pct, pLabel, s, tick);

        drawScanlines(g, ox, oy, shellW, shellH, s, tick);
        drawCornerBrackets(g, ox, oy, shellW, shellH, s, tick);
    }

    private void drawSafeDashboard(Graphics g, int x, int y, int w, int h, Vector msgCopy, int pct,
                                   int displayPct, int displayStagePct, int displaySandboxPct,
                                   int displayKernelPct, int displayPayloadPct,
                                   String label, int stage, int tick) {
        int stageSafe = Math.max(0, Math.min(STAGE_MESSAGES.length - 1, stage));
        int m = Math.max(14, w / 80);
        int gap = Math.max(8, w / 160);
        Font ff = scaleFontForPixels(moduleValueFont, Math.max(18, w / 64));
        Font small = scaleFontForPixels(progressFont, Math.max(11, w / 115));

        g.setColor(Color.black);
        g.fillRect(x, y, w, h);

        int headerY = y + m;
        int headerH = Math.max(76, h / 13);
        drawSafeContainer(g, x + m * 2, headerY, w - m * 4, headerH, "PSone BDJB Autoloader", PS_CYAN);
        drawCssParagraph(g, "StonedModder // Sonic Loader " + Version.SONIC_VERSION,
                         x + m * 3, headerY + headerH - m * 2, PS_WHITE);

        int statusY = headerY + headerH + gap;
        int statusH = Math.max(72, h / 11);
        drawSafeContainer(g, x + m * 2, statusY, w - m * 4, statusH, "ACTIVE STATUS", PS_YELLOW);
        drawCssButton(g, x + m * 3, statusY + statusH / 2, w * 43 / 100, Math.max(24, statusH / 3),
                      STAGE_MESSAGES[stageSafe], STAGE_COLORS[stageSafe]);
        drawPsoneBar(g, x + w / 2 + m, statusY + statusH / 2 - m / 2, w / 2 - m * 5, m * 2,
                     displayStagePct,
                     STAGE_MESSAGES[stageSafe], STAGE_COLORS[stageSafe], tick, false);

        int cardY = statusY + statusH + gap;
        int cardH = Math.max(150, h / 5);
        int leftW = (w - m * 4 - gap) / 2;
        int rightW = w - m * 4 - gap - leftW;
        drawSafeContainer(g, x + m * 2, cardY, leftW, cardH, "CHAIN", PS_CYAN);
        drawSafeContainer(g, x + m * 2 + leftW + gap, cardY, rightW, cardH, "MODULES", PS_GREEN);

        g.setFont(small);
        int bodyTop = cardY + Math.max(m * 3, cardH / 3);
        int bodyBottom = cardY + cardH - m;
        int rowGap = Math.max(m, (bodyBottom - bodyTop) / 3);
        drawSafeField(g, x + m * 3, bodyTop + rowGap / 2, "BD-J USERLAND", sandboxStatus(stageSafe), sandboxColor(stageSafe));
        drawSafeField(g, x + m * 3, bodyTop + rowGap + rowGap / 2, "KERNEL EXPLOIT", kernelStatus(stageSafe), kernelColor(stageSafe));
        drawSafeField(g, x + m * 3, bodyTop + rowGap * 2 + rowGap / 2, "SONIC LOADER", autoloadStatus(stageSafe), autoloadColor(stageSafe));

        int rx = x + m * 2 + leftW + gap + m;
        int rw = rightW - m * 2;
        drawSafeMeter(g, rx, bodyTop + rowGap / 2, rw, m, "SANDBOX", displaySandboxPct, sandboxStatus(stageSafe), sandboxColor(stageSafe), tick);
        drawSafeMeter(g, rx, bodyTop + rowGap + rowGap / 2, rw, m, "KERNEL", displayKernelPct, kernelStatus(stageSafe), kernelColor(stageSafe), tick);
        drawSafeMeter(g, rx, bodyTop + rowGap * 2 + rowGap / 2, rw, m, "PAYLOAD", displayPayloadPct, payloadStatus(stageSafe), payloadColor(stageSafe), tick);

        int miniY = cardY + cardH + gap;
        int miniH = Math.max(76, h / 12);
        int miniW = (w - m * 4 - gap * 3) / 4;
        String[] labs = { "SANDBOX", "KERNEL", "AUTOLOAD", "PAYLOAD" };
        String[] vals = { sandboxStatus(stageSafe), kernelStatus(stageSafe), autoloadStatus(stageSafe), payloadStatus(stageSafe) };
        Color[] cols = { sandboxColor(stageSafe), kernelColor(stageSafe), autoloadColor(stageSafe), payloadColor(stageSafe) };
        for (int i = 0; i < 4; i++) {
            int mx = x + m * 2 + i * (miniW + gap);
            drawSafeContainer(g, mx, miniY, miniW, miniH, labs[i], cols[i]);
            g.setFont(ff);
            g.setColor(cols[i]);
            drawCssParagraph(g, vals[i], mx + m, miniY + miniH - m * 2, cols[i]);
        }

        int logY = miniY + miniH + gap;
        int progressH = Math.max(56, h / 17);
        int logH = h - (logY - y) - progressH - gap - m * 2;
        if (logH < 120) logH = 120;
        drawSafeContainer(g, x + m * 2, logY, w - m * 4, logH, "ACTIVE DISC LOG", PS_WHITE);
        drawSafeLog(g, x + m * 3, logY + m * 3, w - m * 6, logH - m * 4, msgCopy, small);

        int progY = y + h - m * 2 - progressH;
        drawSafeProgress(g, x + m * 2, progY, w - m * 4, progressH, displayPct, label, STAGE_COLORS[stageSafe], tick);
    }

    private void drawBackground(Graphics g, int x, int y, int w, int h, double s, int tick) {
        g.setColor(PS_BG);
        g.fillRect(x, y, w, h);

        fillVerticalGradient(g, x, y, w, h, new Color(0x040313), new Color(0x10115d));

        int starStep = Math.max(1, sc(96, s));
        int drift = (tick * Math.max(1, sc(2, s))) % starStep;
        g.setColor(new Color(255, 255, 255, 35));
        for (int sx = x + sc(48, s) - drift; sx < x + w; sx += starStep) {
            int sy = y + sc(90, s) + ((sx / Math.max(1, starStep)) % 7) * sc(74, s);
            while (sy > y + h) sy -= h;
            g.fillRect(sx, sy, Math.max(1, sc(2, s)), Math.max(1, sc(2, s)));
        }

        g.setColor(new Color(255, 255, 255, 12));
        g.drawOval(x + w / 2 - sc(520, s), y + h - sc(280, s), sc(1040, s), sc(430, s));
        g.drawOval(x + w / 2 - sc(360, s), y + h - sc(225, s), sc(720, s), sc(300, s));

        int glow = tick % 20 < 10 ? 24 : 14;
        g.setColor(new Color(73, 167, 198, glow));
        g.fillOval(x + sc(120, s), y + sc(620, s), sc(520, s), sc(300, s));
        g.setColor(new Color(213, 146, 189, glow));
        g.fillOval(x + w - sc(640, s), y + sc(580, s), sc(520, s), sc(300, s));
    }

    private void drawSafeContainer(Graphics g, int x, int y, int w, int h, String label, Color accent) {
        fillVerticalGradient(g, x, y, w, h, new Color(0x0d2289), new Color(0x06093b));
        g.setColor(new Color(0, 0, 0, 76));
        g.drawRect(x + 2, y + 2, w - 4, h - 4);
        g.drawRect(x + 3, y + 3, w - 6, h - 6);
        g.setColor(new Color(0xc6c6c6));
        g.drawRoundRect(x, y, w, h, 8, 8);
        g.setColor(PS_WHITE);
        g.drawRoundRect(x + 2, y + 2, w - 4, h - 4, 7, 7);

        Font f = scaleFontForPixels(titleFont, Math.max(18, Math.min(32, h / 5)));
        g.setFont(f);
        int ty = y + Math.max(f.getSize(), h / 6);
        g.setColor(Color.black);
        g.drawString(label.toUpperCase(), x + 14, ty + 4);
        g.setColor(new Color(0xaaa9af));
        g.drawString(label.toUpperCase(), x + 10, ty);
    }

    private void drawSafeTimeline(Graphics g, int x, int y, int w, int stage, int tick) {
        int railY = y;
        int count = STAGE_MESSAGES.length;
        g.setColor(Color.black);
        g.fillRect(x, railY - 4, w, 8);
        for (int i = 0; i < count; i++) {
            int px = x + w * i / (count - 1);
            int box = i == stage && tick % 10 < 5 ? 18 : 12;
            g.setColor(i < stage ? PS_GREEN : (i == stage ? STAGE_COLORS[i] : PS_GREY));
            if (i > 0 && i <= stage) {
                int prev = x + w * (i - 1) / (count - 1);
                g.fillRect(prev, railY - 2, px - prev, 4);
            }
            g.fillRect(px - box / 2, railY - box / 2, box, box);
            g.setColor(PS_WHITE);
            g.drawRect(px - box / 2, railY - box / 2, box, box);
        }
    }

    private void drawSafeField(Graphics g, int x, int y, String label, String value, Color c) {
        drawCssParagraph(g, label, x, y, PS_GREY);
        drawCssParagraph(g, value, x + 230, y, c);
    }

    private void drawSafeMeter(Graphics g, int x, int y, int w, int h, String label, int pct, String status, Color c, int tick) {
        drawCssParagraph(g, label, x, y - 4, PS_GREY);
        int barX = x + 180;
        int barW = Math.max(20, w - 180);
        drawPsoneBar(g, barX, y - h / 2, barW, h, pct, status, c, tick, false);
    }

    private void drawCssParagraph(Graphics g, String text, int x, int y, Color color) {
        Font f = scaleFontForPixels(titleFont, 30);
        g.setFont(f);
        g.setColor(Color.black);
        g.drawString(text, x + 2, y + 2);
        g.setColor(color);
        g.drawString(text, x, y);
    }

    private void drawCssButton(Graphics g, int x, int y, int w, int h, String text, Color c) {
        int top = y - h / 2;
        fillCssHorizontalFade(g, x, top, w, h, c);
        g.setColor(new Color(255, 255, 255, 51));
        g.fillRect(x + w / 5, top, w * 3 / 5, 3);
        g.fillRect(x + w / 5, top + h - 3, w * 3 / 5, 3);

        Font f = scaleFontForPixels(progressFont, Math.max(14, Math.min(20, h - 4)));
        g.setFont(f);
        FontMetrics fm = g.getFontMetrics();
        String upper = text.toUpperCase();
        while (upper.length() > 4 && fm.stringWidth(upper) > w - 24) {
            upper = upper.substring(0, upper.length() - 1);
        }
        int tx = x + (w - fm.stringWidth(upper)) / 2;
        int ty = top + (h + fm.getAscent()) / 2 - 2;
        g.setColor(Color.black);
        g.drawString(upper, tx + 2, ty + 2);
        g.setColor(PS_WHITE);
        g.drawString(upper, tx, ty);
    }

    private void drawSafeLog(Graphics g, int x, int y, int w, int h, Vector msgCopy, Font font) {
        g.setFont(font);
        FontMetrics fm = g.getFontMetrics();
        int lineH = Math.max(18, fm.getHeight() + 4);
        int max = Math.max(1, h / lineH);
        int start = Math.max(0, msgCopy.size() - max);
        int ly = y + fm.getAscent();
        for (int i = start; i < msgCopy.size(); i++) {
            Message m = (Message) msgCopy.elementAt(i);
            if (((i - start) & 1) == 0) {
                g.setColor(new Color(0x10145f));
                g.fillRect(x - 6, ly - fm.getAscent(), w, lineH);
            }
            g.setColor(PS_YELLOW);
            g.drawString(">", x, ly);
            g.setColor(m.color);
            g.drawString(shorten(m.text, fm, w - 28), x + 28, ly);
            ly += lineH;
        }
    }

    private void drawSafeProgress(Graphics g, int x, int y, int w, int h, int pct, String label, Color c, int tick) {
        drawSafeContainer(g, x, y, w, h, "PROGRESS", c);
        int pad = Math.max(10, h / 5);
        drawPsoneBar(g, x + pad, y + h / 2 - pad / 2, w - pad * 2, Math.max(24, h / 2), pct,
                     (label != null ? label : "BOOTING") + " " + pct + "%",
                     c, tick, true);
    }

    private void drawPsoneBar(Graphics g, int x, int y, int w, int h, int pct, String text, Color c, int tick, boolean large) {
        int clamped = Math.max(0, Math.min(100, pct));
        int scale = large ? Math.max(1, Math.min(2, h / 30)) : 1;
        int pad = 3 * scale;
        int barH = 12 * scale;
        int progressH = barH + pad * 2;
        int progressY = y + Math.max(0, (h - progressH) / 2);
        int barX = x + pad;
        int barY = progressY + pad;
        int barW = w - pad * 2;

        g.setColor(new Color(0, 0, 0, 128));
        g.fillRect(x, progressY, w, progressH);

        int fillW = barW * clamped / 100;
        if (fillW > 0) {
            Color start = barStartColor(c);
            Color end = barEndColor(c, clamped);
            fillHorizontalGradient(g, barX, barY, fillW, barH, start, end);
            if (tick != 0 && clamped < 100) {
                drawCssIndeterminateSweep(g, barX, barY, barW, barH, tick);
            }
        }

        Font f = scaleFontForPixels(progressFont, Math.max(12, 20 * scale));
        g.setFont(f);
        FontMetrics fm = g.getFontMetrics();
        int subX = x + w * (large ? 15 : 6) / 100;
        int subY = progressY + Math.max(0, progressH / 2 - (10 * scale));
        int subH = 20 * scale;
        String upper = shorten(text.toUpperCase(), fm, w - (subX - x) - pad * 2);
        int subW = Math.min(w - (subX - x) - pad, fm.stringWidth(upper) + 8 * scale);
        g.setColor(new Color(0, 0, 0, 102));
        g.fillRect(subX, subY, subW, subH);
        int tx = subX;
        int ty = subY + (subH + fm.getAscent()) / 2 - 2 * scale;
        g.setColor(PS_WHITE);
        g.drawString(upper, tx, ty);
    }

    private void drawCssIndeterminateSweep(Graphics g, int x, int y, int w, int h, int tick) {
        if (w <= 0 || h <= 0) return;
        Shape clip = g.getClip();
        g.setClip(x, y, w, h);
        int sweepW = Math.max(1, w * 30 / 100);
        int travel = w + sweepW;
        int sx = x - sweepW + ((tick * Math.max(1, w / 28)) % Math.max(1, travel));
        fillHorizontalGradient(g, sx, y, sweepW, h, new Color(255, 255, 255, 0), new Color(255, 255, 255, 80));
        fillHorizontalGradient(g, sx + sweepW, y, sweepW, h, new Color(255, 255, 255, 80), new Color(255, 255, 255, 0));
        g.setClip(clip);
    }

    private Color barStartColor(Color c) {
        if (c == PS_RED) return new Color(0xb62f28);
        if (c == PS_ORANGE) return new Color(0xf19c42);
        if (c == PS_CYAN) return new Color(0x3e8ca5);
        if (c == PS_GREEN) return new Color(0x4ea35b);
        if (c == PS_YELLOW) return new Color(0x2c6776);
        return new Color(0x484a48);
    }

    private Color barEndColor(Color c, int pct) {
        if (pct >= 100) return new Color(0x5dc66d);
        if (c == PS_RED) return new Color(0xe93f36);
        if (c == PS_ORANGE) return new Color(0xffb544);
        if (c == PS_CYAN) return new Color(0x49a7c6);
        if (c == PS_GREEN) return new Color(0x5dc66d);
        if (c == PS_YELLOW) return new Color(0x4ea35b);
        return new Color(0x707070);
    }

    private void drawPaintFailure(Graphics g, int x, int y, int w, int h, Throwable t) {
        g.setColor(Color.black);
        g.fillRect(x, y, w, h);
        g.setColor(PS_RED);
        g.drawRect(x + 32, y + 32, w - 64, h - 64);
        g.setFont(scaleFontForPixels(progressFont, Math.max(16, w / 60)));
        g.drawString("PSone renderer error", x + 72, y + 110);
        g.setColor(PS_WHITE);
        String name = t != null ? t.getClass().getName() : "unknown";
        String msg = t != null && t.getMessage() != null ? t.getMessage() : "";
        g.drawString(name, x + 72, y + 160);
        g.drawString(msg, x + 72, y + 205);
    }

    private static Font loadDvbFont(String name, int size, String fallback) {
        try {
            FontFactory factory = new FontFactory();
            return factory.createFont(name, Font.PLAIN, size);
        } catch (Throwable t) {
            try {
                FontFactory factory = new FontFactory(new URL("file:/disc/BDMV/AUXDATA/dvb.fontindex"));
                return factory.createFont(name, Font.PLAIN, size);
            } catch (Throwable ignored) {
                return new Font(fallback, Font.BOLD, size);
            }
        }
    }

    private String sandboxStatus(int stage) {
        if (stage >= 2) return "ESCAPED";
        if (stage >= 1) return "RUNNING";
        return "STANDBY";
    }

    private Color sandboxColor(int stage) {
        if (stage >= 2) return PS_GREEN;
        if (stage >= 1) return PS_YELLOW;
        return PS_GREY;
    }

    private int sandboxPercent(int stage) {
        if (stage >= 2) return 100;
        if (stage >= 1) return 55;
        return 0;
    }

    private String kernelStatus(int stage) {
        if (stage >= 5) return "PATCHED";
        if (stage >= 4) return "PATCHING";
        return "LOCKED";
    }

    private Color kernelColor(int stage) {
        if (stage >= 5) return PS_GREEN;
        if (stage >= 4) return PS_ORANGE;
        return PS_GREY;
    }

    private int kernelPercent(int stage) {
        if (stage >= 5) return 100;
        if (stage >= 4) return 60;
        return 0;
    }

    private String autoloadStatus(int stage) {
        if (stage >= 8) return "COMPLETE";
        if (stage >= 7) return "LAUNCHING";
        if (stage >= 6) return "READY";
        return "WAITING";
    }

    private Color autoloadColor(int stage) {
        if (stage >= 8) return PS_GREEN;
        if (stage >= 6) return PS_CYAN;
        return PS_GREY;
    }

    private String payloadStatus(int stage) {
        if (stage >= 8) return "COMPLETE";
        if (stage >= 7) return "RUNNING";
        return "QUEUED";
    }

    private Color payloadColor(int stage) {
        if (stage >= 8) return PS_GREEN;
        if (stage >= 7) return PS_YELLOW;
        return PS_GREY;
    }

    private int payloadPercent(int stage, int overallPct) {
        if (stage >= 8) return 100;
        if (stage >= 7) return Math.max(35, Math.min(90, overallPct));
        return 0;
    }

    private int drawTitle(Graphics g, String ttl, int ox, int shellW, int y, double s, int tick) {
        Font f = scaleFont(titleFont, s);
        g.setFont(f);
        String text = ttl.toUpperCase();
        int tracking = sc(4, s);
        int width = trackedWidth(g, text, tracking);
        int x = ox + (shellW - width) / 2;
        int baseline = y + sc(72, s);

        int halo = tick % 18 < 9 ? 90 : 55;
        g.setColor(new Color(0, 0, 0, 210));
        drawTrackedString(g, text, x + sc(5, s), baseline + sc(5, s), tracking);
        g.setColor(new Color(73, 167, 198, halo));
        drawTrackedString(g, text, x - sc(3, s), baseline, tracking);
        g.setColor(new Color(255, 255, 255, 245));
        drawTrackedString(g, text, x, baseline, tracking);

        g.setFont(scaleFont(progressFont, s));
        String sub = "PSone BDJB Disc // Sonic Loader " + Version.SONIC_VERSION + " // StonedModder";
        FontMetrics sfm = g.getFontMetrics();
        int subX = ox + (shellW - sfm.stringWidth(sub)) / 2;
        int subY = baseline + sc(34, s);
        g.setColor(new Color(0, 0, 0, 185));
        g.drawString(sub, subX + sc(2, s), subY + sc(2, s));
        g.setColor(PS_GREY);
        g.drawString(sub, subX, subY);
        g.setColor(PS_YELLOW);
        g.fillRect(ox + sc(72, s), y + sc(86, s), sc(180, s), sc(8, s));
        g.setColor(PS_RED);
        g.fillRect(ox + shellW - sc(252, s), y + sc(86, s), sc(180, s), sc(8, s));
        return y + sc(92, s);
    }

    private int drawEffectsRow(Graphics g, int x, int oy, int w, int y, double s, int tick, int stage) {
        int rowH = sc(72, s);
        int gap = sc(18, s);
        int baseline = y + sc(46, s);

        String status = STAGE_MESSAGES[stage];
        Color statusColor = STAGE_COLORS[stage];
        String[] texts = {
            "v" + Version.VERSION,
            "StonedModder",
            "Sonic Loader " + Version.SONIC_VERSION,
            status
        };
        int[] widths = {
            badgeWidth(g, texts[0], s),
            badgeWidth(g, texts[1], s),
            badgeWidth(g, texts[2], s),
            trackedWidthWithFont(g, texts[3], scaleFont(statusFont, s), sc(2, s))
        };

        int statusMaxW = sc(640, s);
        if (widths[3] > statusMaxW) widths[3] = statusMaxW;
        int clusterW = widths[0] + widths[1] + widths[2] + widths[3] + gap * 3;
        int startX = x + (w - clusterW) / 2;
        if (startX < x) startX = x;
        fillVerticalGradient(g, x, y, w, rowH, new Color(0, 0, 60, 180), new Color(0, 0, 18, 180));
        g.setColor(new Color(255, 255, 255, 175));
        g.drawRect(x, y, w, rowH);
        g.setColor(new Color(0, 0, 0, 120));
        g.drawRect(x + sc(4, s), y + sc(4, s), w - sc(8, s), rowH - sc(8, s));

        int bx = startX;
        drawBadge(g, bx, y + sc(14, s), texts[0], PS_CYAN, s, tick, 0);
        bx += widths[0] + gap;
        drawBadge(g, bx, y + sc(14, s), texts[1], PS_YELLOW, s, tick, 1);
        bx += widths[1] + gap;
        drawBadge(g, bx, y + sc(14, s), texts[2], PS_RED, s, tick, 2);
        bx += widths[2] + gap;

        int statusX = Math.min(bx, x + w - widths[3] - sc(18, s));
        g.setFont(scaleFont(statusFont, s));
        drawStatusText(g, shorten(texts[3], g.getFontMetrics(), widths[3]), statusX, baseline, statusColor, s, tick);
        return y + rowH;
    }

    private int drawModuleGrid(Graphics g, int x, int y, int w, String[] values, Color[] colors, int tick, double s, int stage) {
        int arrowW = sc(42, s);
        int cardW = (w - arrowW * 2) / 3;
        int cardH = sc(128, s);
        for (int i = 0; i < 3; i++) {
            int mx = x + i * (cardW + arrowW);
            drawModule(g, mx, y, cardW, cardH, MODULE_LABELS[i], values[i], colors[i], i, tick, s);
            if (i < 2) {
                drawPipelineArrow(g, mx + cardW, y, arrowW, cardH, i, stage, tick, s);
            }
        }
        return y + cardH;
    }

    private int drawStageRail(Graphics g, int x, int y, int w, int stage, int tick, double s) {
        int h = sc(82, s);
        int pad = sc(18, s);
        drawPsonePanel(g, x, y, w, h, STAGE_COLORS[stage], false, s);

        g.setFont(scaleFont(moduleLabelFont, s));
        FontMetrics fm = g.getFontMetrics();
        g.setColor(PS_DIM);
        g.drawString("CHAIN TIMELINE", x + pad, y + sc(24, s));

        int railX = x + pad;
        int railY = y + sc(50, s);
        int railW = w - pad * 2;
        g.setColor(new Color(0, 0, 0, 155));
        g.fillRect(railX, railY - sc(4, s), railW, sc(8, s));

        int count = STAGE_MESSAGES.length;
        for (int i = 0; i < count; i++) {
            int px = railX + railW * i / (count - 1);
            boolean done = i < stage;
            boolean active = i == stage;
            Color c = done ? PS_GREEN : (active ? STAGE_COLORS[i] : PS_GREY);
            if (i > 0 && i <= stage) {
                int prev = railX + railW * (i - 1) / (count - 1);
                g.setColor(done || active ? PS_GREEN : PS_GREY);
                g.fillRect(prev, railY - sc(2, s), px - prev, sc(4, s));
            }
            int box = active && tick % 10 < 5 ? sc(18, s) : sc(14, s);
            g.setColor(new Color(0, 0, 0, 180));
            g.fillRect(px - box / 2 + sc(2, s), railY - box / 2 + sc(2, s), box, box);
            g.setColor(c);
            g.fillRect(px - box / 2, railY - box / 2, box, box);
            g.setColor(PS_WHITE);
            g.drawRect(px - box / 2, railY - box / 2, box, box);
        }

        String current = "NOW: " + STAGE_MESSAGES[stage];
        g.setColor(STAGE_COLORS[stage]);
        g.drawString(current, x + w - pad - fm.stringWidth(current), y + sc(24, s));
        return y + h;
    }

    private void drawChainCard(Graphics g, int x, int y, int w, int h, int pct, String label, int stage, int tick, double s) {
        drawPsonePanel(g, x, y, w, h, STAGE_COLORS[stage], false, s);
        int pad = sc(24, s);
        g.setFont(scaleFont(moduleLabelFont, s));
        g.setColor(PS_YELLOW);
        g.drawString("DISC CHAIN STATUS", x + pad, y + sc(30, s));

        g.setFont(scaleFont(statusFont, s));
        FontMetrics sfm = g.getFontMetrics();
        String big = STAGE_MESSAGES[stage];
        g.setColor(new Color(0, 0, 0, 180));
        g.drawString(shorten(big, sfm, w - pad * 2), x + pad + sc(3, s), y + sc(88, s) + sc(3, s));
        g.setColor(STAGE_COLORS[stage]);
        g.drawString(shorten(big, sfm, w - pad * 2), x + pad, y + sc(88, s));

        int meterY = y + sc(112, s);
        int meterW = w - pad * 2;
        g.setColor(new Color(0, 0, 0, 180));
        g.fillRect(x + pad, meterY, meterW, sc(16, s));
        fillHorizontalGradient(g, x + pad, meterY, meterW * pct / 100, sc(16, s), PS_YELLOW, STAGE_COLORS[stage]);
        g.setColor(PS_WHITE);
        g.drawRect(x + pad, meterY, meterW, sc(16, s));

        g.setFont(scaleFont(progressFont, s));
        drawFieldLine(g, x + pad, y + sc(150, s), "BD-J USERLAND", stage >= 1 ? "ACTIVE" : "STANDBY", stage >= 1 ? PS_GREEN : PS_GREY, s);
        drawFieldLine(g, x + pad, y + sc(180, s), "KERNEL EXPLOIT", stage >= 5 ? "PATCHED" : (stage >= 4 ? "PATCHING" : "WAITING"), stage >= 5 ? PS_GREEN : (stage >= 4 ? PS_ORANGE : PS_GREY), s);
        drawFieldLine(g, x + pad, y + sc(210, s), "SONIC LOADER", stage >= 7 ? "LAUNCHING" : (stage >= 6 ? "MANIFEST READY" : "QUEUED"), stage >= 6 ? PS_CYAN : PS_GREY, s);

        int spinX = x + w - pad - sc(46, s);
        int spinY = y + h - pad - sc(46, s);
        drawSpinner(g, spinX, spinY, sc(42, s), tick, STAGE_COLORS[stage]);
    }

    private void drawActionCards(Graphics g, int x, int y, int w, int h, int stage, int tick, double s) {
        int gap = sc(16, s);
        int cardW = (w - gap * 3) / 4;
        String[] labels = { "SANDBOX", "KERNEL", "AUTOLOAD", "PAYLOAD" };
        String[] values = {
            stage >= 2 ? "ESCAPED" : (stage >= 1 ? "RUNNING" : "READY"),
            stage >= 5 ? "PATCHED" : (stage >= 4 ? "PATCHING" : "LOCKED"),
            stage >= 6 ? "READY" : "WAITING",
            stage >= 8 ? "DONE" : (stage >= 7 ? "RUNNING" : "QUEUED")
        };
        Color[] colors = {
            stage >= 2 ? PS_GREEN : PS_YELLOW,
            stage >= 5 ? PS_GREEN : (stage >= 4 ? PS_ORANGE : PS_RED),
            stage >= 6 ? PS_CYAN : PS_GREY,
            stage >= 8 ? PS_GREEN : (stage >= 7 ? PS_YELLOW : PS_GREY)
        };
        for (int i = 0; i < 4; i++) {
            drawMiniCard(g, x + i * (cardW + gap), y, cardW, h, labels[i], values[i], colors[i], i, tick, s);
        }
    }

    private void drawMiniCard(Graphics g, int x, int y, int w, int h, String label, String value, Color c, int idx, int tick, double s) {
        drawPsonePanel(g, x, y, w, h, c, false, s);
        int pad = sc(18, s);
        g.setFont(scaleFont(moduleLabelFont, s));
        g.setColor(PS_DIM);
        g.drawString(label, x + pad, y + sc(26, s));
        g.setFont(scaleFont(moduleValueFont, s));
        FontMetrics fm = g.getFontMetrics();
        g.setColor(c);
        g.drawString(shorten(value, fm, w - pad * 2 - sc(34, s)), x + pad, y + sc(76, s));
        drawControllerGlyph(g, x + w - sc(34, s), y + h - sc(32, s), sc(24, s), idx, c);
        if (tick % 12 < 6 && !value.equals("WAITING") && !value.equals("LOCKED") && !value.equals("QUEUED")) {
            g.setColor(PS_WHITE);
            g.fillRect(x + w - sc(18, s), y + sc(14, s), sc(7, s), sc(7, s));
        }
    }

    private void drawPipelineArrow(Graphics g, int x, int y, int w, int h, int arrowIdx, int stage, int tick, double s) {
        boolean done   = (arrowIdx == 0) ? (stage >= 5) : (stage >= 7);
        boolean active = (arrowIdx == 0) ? (stage >= 2 && stage < 5) : (stage >= 5 && stage < 7);

        Color base = done ? PS_GREEN : PS_WHITE;
        int alpha;

        if (done) {
            alpha = 165;
        } else if (active) {
            double wave = 0.5 + 0.5 * Math.sin(tick * 1.078 + arrowIdx * 3.14159);
            alpha = (int)(80 + 175 * wave);
        } else {
            alpha = 36;
        }

        int clampedAlpha = Math.max(0, Math.min(255, alpha));
        g.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), clampedAlpha));
        int cx2 = x + w / 2;
        int cy = y + h / 2;
        int r = sc(active ? 10 : 8, s);
        int[] xs = { cx2 - r, cx2 - r, cx2 + r };
        int[] ys = { cy - r, cy + r, cy };
        g.fillPolygon(xs, ys, 3);

        if (active) {
            g.setColor(new Color(PS_YELLOW.getRed(), PS_YELLOW.getGreen(), PS_YELLOW.getBlue(), clampedAlpha / 2));
            g.drawOval(cx2 - sc(18, s), cy - sc(18, s), sc(36, s), sc(36, s));
        }

        int lineY = cy;
        int lineAlpha = clampedAlpha / 4;
        g.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), Math.max(0, lineAlpha)));
        g.drawLine(x + sc(3, s), lineY, cx2 - sc(13, s), lineY);
        g.drawLine(cx2 + sc(13, s), lineY, x + w - sc(3, s), lineY);
    }

    private void drawFieldLine(Graphics g, int x, int y, String label, String value, Color c, double s) {
        g.setColor(PS_DIM);
        g.drawString(label, x, y);
        g.setColor(c);
        g.drawString(value, x + sc(230, s), y);
    }

    private void drawSpinner(Graphics g, int x, int y, int size, int tick, Color c) {
        int cx = x + size / 2;
        int cy = y + size / 2;
        g.setColor(new Color(0, 0, 0, 160));
        g.fillOval(x, y, size, size);
        g.setColor(PS_WHITE);
        g.drawOval(x, y, size, size);
        int arm = tick % 4;
        g.setColor(c);
        if (arm == 0) g.fillRect(cx - 2, y + 4, 4, size / 2);
        else if (arm == 1) g.fillRect(cx, cy - 2, size / 2 - 4, 4);
        else if (arm == 2) g.fillRect(cx - 2, cy, 4, size / 2 - 4);
        else g.fillRect(x + 4, cy - 2, size / 2, 4);
    }

    private int drawLog(Graphics g, int x, int y, int w, Vector msgCopy, double s) {
        int headerH = sc(42, s);
        int padX = sc(28, s);
        int padY = sc(16, s);
        int streamH = sc(252, s);
        int panelH = headerH + padY + streamH + padY;

        drawPsonePanel(g, x, y, w, panelH, PS_WHITE, true, s);

        g.setFont(scaleFont(moduleLabelFont, s));
        FontMetrics hfm = g.getFontMetrics();
        int headerBaseline = y + (headerH + hfm.getAscent()) / 2;
        g.setColor(PS_YELLOW);
        g.drawString("ACTIVE DISC LOG", x + padX, headerBaseline);
        g.setColor(PS_DIM);
        String hint = "SELECT: WATCH  START: AUTOLOAD";
        g.drawString(hint, x + w - padX - hfm.stringWidth(hint), headerBaseline);

        g.setColor(new Color(255, 255, 255, 95));
        g.drawLine(x + sc(16, s), y + headerH, x + w - sc(16, s), y + headerH);

        g.setFont(scaleFont(logFont, s));
        FontMetrics fm = g.getFontMetrics();
        int lineH = Math.max(1, sc(30, s));
        int maxLines = Math.max(1, streamH / lineH);
        int start = Math.max(0, msgCopy.size() - maxLines);

        Shape clip = g.getClip();
        int streamTop = y + headerH + padY;
        g.setClip(x + padX, streamTop, w - padX * 2, streamH);
        int ly = streamTop + fm.getAscent();
        for (int i = start; i < msgCopy.size(); i++) {
            Message m = (Message) msgCopy.elementAt(i);
            int row = i - start;
            if (row % 2 == 0) {
                g.setColor(new Color(255, 255, 255, 16));
                g.fillRect(x + padX - sc(6, s), ly - fm.getAscent(), w - padX * 2, lineH);
            }
            g.setColor(PS_YELLOW);
            g.drawString(">", x + padX, ly);
            g.setColor(m.color);
            g.drawString(shorten(m.text, fm, w - padX * 2 - sc(26, s)), x + padX + sc(26, s), ly);
            ly += lineH;
        }
        g.setClip(clip);
        return y + panelH;
    }

    private void drawProgress(Graphics g, int x, int y, int w, int pct, String label, double s, int tick) {
        int h = sc(42, s);
        int labelW = sc(340, s);
        fillVerticalGradient(g, x, y, w, h, new Color(0, 0, 50), new Color(0, 0, 16));
        g.setColor(PS_WHITE);
        g.drawRect(x, y, w, h);
        g.setColor(new Color(0, 0, 0, 160));
        g.fillRect(x + sc(7, s), y + sc(7, s), w - sc(14, s), h - sc(14, s));

        int barX = x + sc(12, s);
        int barY = y + sc(12, s);
        int barW = w - labelW - sc(28, s);
        int barH = h - sc(24, s);
        int fillW = (int) (barW * pct / 100.0);
        if (fillW > 0) {
            Color end = pct >= 100 ? PS_GREEN : (pct >= 50 ? PS_CYAN : PS_YELLOW);
            fillHorizontalGradient(g, barX, barY, fillW, barH, PS_YELLOW, end);
            drawMovingStripes(g, barX, barY, fillW, barH, new Color(255, 255, 255, 65), tick, s);
        }
        g.setColor(new Color(255, 255, 255, 120));
        g.drawRect(barX, barY, barW, barH);

        g.setFont(scaleFont(progressFont, s));
        FontMetrics fm = g.getFontMetrics();
        String text = (label != null && label.length() > 0 ? label.toUpperCase() : "BOOTING...") + "  " + pct + "%";
        int tx = x + w - fm.stringWidth(text) - sc(18, s);
        int by = y + (h + fm.getAscent()) / 2 - sc(1, s);
        g.setColor(PS_WHITE);
        g.drawString(text, tx, by);
    }

    private void drawBadge(Graphics g, int x, int y, String text, Color c, double s, int tick, int style) {
        Font f = scaleFont(badgeFont, s);
        g.setFont(f);
        FontMetrics fm = g.getFontMetrics();
        int padX = sc(18, s);
        int padY = sc(7, s);
        int bw = fm.stringWidth(text) + padX * 2;
        int bh = fm.getHeight() + padY * 2;

        int bgAlpha = style == 1 && tick % 16 < 8 ? 96 : 60;
        g.setColor(new Color(0, 0, 0, 150));
        g.fillRect(x + sc(3, s), y + sc(3, s), bw, bh);
        g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), bgAlpha));
        g.fillRect(x, y, bw, bh);
        g.setColor(PS_WHITE);
        g.drawRect(x, y, bw, bh);

        if (style == 0 && tick % 24 < 4) {
            g.setColor(new Color(73, 167, 198, 110));
            g.drawRect(x - sc(3, s), y - sc(3, s), bw + sc(6, s), bh + sc(6, s));
        }
        if (style == 2) {
            int pulse = tick % 10 < 5 ? 85 : 35;
            g.setColor(new Color(PS_RED.getRed(), PS_RED.getGreen(), PS_RED.getBlue(), pulse));
            g.drawRect(x - sc(2, s), y - sc(2, s), bw + sc(4, s), bh + sc(4, s));
        }

        g.setColor(PS_WHITE);
        g.drawString(text, x + padX, y + padY + fm.getAscent());
    }

    private void drawStatusText(Graphics g, String text, int x, int baseline, Color c, double s, int tick) {
        Font f = scaleFont(statusFont, s);
        int tracking = sc(2, s);
        g.setFont(f);
        if (tick % 25 < 6) {
            g.setColor(new Color(PS_CYAN.getRed(), PS_CYAN.getGreen(), PS_CYAN.getBlue(), 130));
            drawTrackedString(g, text, x - sc(2, s), baseline, tracking);
            g.setColor(new Color(PS_RED.getRed(), PS_RED.getGreen(), PS_RED.getBlue(), 130));
            drawTrackedString(g, text, x + sc(2, s), baseline, tracking);
        }
        g.setColor(new Color(0, 0, 0, 190));
        drawTrackedString(g, text, x + sc(3, s), baseline + sc(3, s), tracking);
        g.setColor(c);
        drawTrackedString(g, text, x, baseline, tracking);
    }

    private void drawModule(Graphics g, int x, int y, int w, int h, String label, String value,
                            Color accent, int index, int tick, double s) {
        boolean active = isModuleActive(value);
        drawPsonePanel(g, x, y, w, h, active && tick % 12 < 6 ? PS_WHITE : accent, false, s);

        int padX = sc(24, s);
        int top = y + sc(18, s);

        g.setFont(scaleFont(moduleLabelFont, s));
        g.setColor(PS_DIM);
        g.drawString(label, x + padX, top + g.getFontMetrics().getAscent());

        g.setFont(scaleFont(moduleValueFont, s));
        g.setColor(tick % 42 == 40 ? new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 110) : accent);
        g.drawString(value, x + padX, top + sc(54, s));

        if (active) {
            int dot = sc(9, s);
            int pulse = tick % 10 < 5 ? sc(2, s) : 0;
            g.setColor(tick % 10 < 5 ? PS_WHITE : PS_GREY);
            g.fillOval(x + w - sc(30, s) - pulse, y + sc(20, s) - pulse, dot + pulse * 2, dot + pulse * 2);
        }

        drawControllerGlyph(g, x + w - sc(48, s), y + h - sc(54, s), sc(30, s), index, accent);

        int meterX = x + padX;
        int meterY = y + h - sc(22, s);
        int meterW = w - padX * 2 - sc(42, s);
        int meterH = sc(6, s);
        g.setColor(new Color(0, 0, 0, 120));
        g.fillRect(meterX, meterY, meterW, meterH);

        int fill = moduleFillWidth(value, meterW, tick, index);
        fillHorizontalGradient(g, meterX, meterY, fill, meterH, PS_WHITE, accent);
        drawMovingStripes(g, meterX, meterY, fill, meterH, new Color(255, 255, 255, 35), tick, s);
    }

    private void drawPsonePanel(Graphics g, int x, int y, int w, int h, Color accent, boolean logStyle, double s) {
        int arc = sc(12, s);
        g.setColor(new Color(0, 0, 0, 180));
        g.fillRect(x + sc(6, s), y + sc(6, s), w, h);
        fillVerticalGradient(g, x, y, w, h, PS_PANEL_TOP, PS_PANEL_BOTTOM);
        g.setColor(PS_WHITE);
        g.drawRect(x, y, w, h);
        g.drawRect(x + sc(4, s), y + sc(4, s), w - sc(8, s), h - sc(8, s));
        g.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 140));
        g.drawLine(x + sc(18, s), y + sc(2, s), x + w - sc(18, s), y + sc(2, s));
        if (logStyle) drawDottedHighlight(g, x + sc(16, s), y + h - sc(14, s), w - sc(32, s), s);
    }

    private void drawControllerRails(Graphics g, int x, int y, int w, int h, double s, int tick) {
        int railH = sc(26, s);
        fillHorizontalGradient(g, x, y, w, railH, new Color(0x06093b), new Color(0x1b5fc6));
        fillHorizontalGradient(g, x, y + h - railH, w, railH, new Color(0x1b5fc6), new Color(0x06093b));
        g.setColor(new Color(255, 255, 255, 150));
        g.drawLine(x, y + railH, x + w, y + railH);
        g.drawLine(x, y + h - railH, x + w, y + h - railH);

        int glyphY = y + sc(8, s);
        int step = sc(64, s);
        int offset = (tick * Math.max(1, sc(2, s))) % step;
        for (int gx = x + sc(40, s) - offset; gx < x + w; gx += step) {
            drawControllerGlyph(g, gx, glyphY, sc(12, s), ((gx / Math.max(1, step)) & 3), PS_GREY);
        }
    }

    private void drawScanlines(Graphics g, int x, int y, int w, int h, double s, int tick) {
        int spacing = Math.max(2, sc(4, s));
        int offset = tick % spacing;
        g.setColor(new Color(0, 0, 0, tick % 2 == 0 ? 38 : 28));
        for (int sy = y + offset; sy < y + h; sy += spacing) {
            g.drawLine(x, sy, x + w, sy);
        }
    }

    private void drawCornerBrackets(Graphics g, int x, int y, int w, int h, double s, int tick) {
        int len = sc(46, s);
        int inset = sc(32, s);
        Color c = tick % 18 < 9 ? new Color(255, 255, 255, 145) : new Color(255, 255, 255, 75);
        g.setColor(c);
        g.drawLine(x + inset, y + inset, x + inset + len, y + inset);
        g.drawLine(x + inset, y + inset, x + inset, y + inset + len);
        g.drawLine(x + w - inset - len, y + inset, x + w - inset, y + inset);
        g.drawLine(x + w - inset, y + inset, x + w - inset, y + inset + len);
        g.drawLine(x + inset, y + h - inset, x + inset + len, y + h - inset);
        g.drawLine(x + inset, y + h - inset - len, x + inset, y + h - inset);
        g.drawLine(x + w - inset - len, y + h - inset, x + w - inset, y + h - inset);
        g.drawLine(x + w - inset, y + h - inset - len, x + w - inset, y + h - inset);
    }

    private void drawControllerGlyph(Graphics g, int x, int y, int size, int type, Color c) {
        g.setColor(new Color(0, 0, 0, 135));
        g.fillOval(x - size / 2 + 2, y - size / 2 + 2, size, size);
        g.setColor(new Color(95, 94, 96, 230));
        g.fillOval(x - size / 2, y - size / 2, size, size);
        g.setColor(c);
        if (type == 0) {
            g.drawRect(x - size / 4, y - size / 4, size / 2, size / 2);
        } else if (type == 1) {
            g.drawOval(x - size / 4, y - size / 4, size / 2, size / 2);
        } else if (type == 2) {
            g.drawLine(x - size / 4, y - size / 4, x + size / 4, y + size / 4);
            g.drawLine(x + size / 4, y - size / 4, x - size / 4, y + size / 4);
        } else {
            int[] xs = { x, x - size / 4, x + size / 4 };
            int[] ys = { y - size / 4, y + size / 4, y + size / 4 };
            g.drawPolygon(xs, ys, 3);
        }
    }

    private void drawDottedHighlight(Graphics g, int x, int y, int w, double s) {
        int dot = Math.max(1, sc(2, s));
        int step = Math.max(3, sc(14, s));
        g.setColor(new Color(255, 255, 255, 90));
        for (int dx = x; dx < x + w; dx += step) {
            g.fillRect(dx, y, dot, dot);
        }
    }

    private void fillVerticalGradient(Graphics g, int x, int y, int w, int h, Color top, Color bottom) {
        if (w <= 0 || h <= 0) return;
        int bands = Math.min(24, Math.max(4, h / 24));
        for (int band = 0; band < bands; band++) {
            int i = band * h / bands;
            int next = (band + 1) * h / bands;
            int denom = Math.max(1, bands - 1);
            int r = top.getRed() + (bottom.getRed() - top.getRed()) * band / denom;
            int gr = top.getGreen() + (bottom.getGreen() - top.getGreen()) * band / denom;
            int b = top.getBlue() + (bottom.getBlue() - top.getBlue()) * band / denom;
            int a = top.getAlpha() + (bottom.getAlpha() - top.getAlpha()) * band / denom;
            g.setColor(new Color(r, gr, b, a));
            g.fillRect(x, y + i, w, Math.max(1, next - i));
        }
    }

    private void fillHorizontalGradient(Graphics g, int x, int y, int w, int h, Color left, Color right) {
        if (w <= 0 || h <= 0) return;
        int bands = Math.min(24, Math.max(4, w / 48));
        for (int band = 0; band < bands; band++) {
            int i = band * w / bands;
            int next = (band + 1) * w / bands;
            int denom = Math.max(1, bands - 1);
            int r = left.getRed() + (right.getRed() - left.getRed()) * band / denom;
            int gr = left.getGreen() + (right.getGreen() - left.getGreen()) * band / denom;
            int b = left.getBlue() + (right.getBlue() - left.getBlue()) * band / denom;
            int a = left.getAlpha() + (right.getAlpha() - left.getAlpha()) * band / denom;
            g.setColor(new Color(r, gr, b, a));
            g.fillRect(x + i, y, Math.max(1, next - i), h);
        }
    }

    private void fillCssHorizontalFade(Graphics g, int x, int y, int w, int h, Color center) {
        if (w <= 0 || h <= 0) return;
        int bands = Math.min(32, Math.max(8, w / 40));
        for (int band = 0; band < bands; band++) {
            int i = band * w / bands;
            int next = (band + 1) * w / bands;
            int mid = bands / 2;
            int alpha = band <= mid
                ? center.getAlpha() * band / Math.max(1, mid)
                : center.getAlpha() * (bands - band - 1) / Math.max(1, bands - mid - 1);
            g.setColor(new Color(center.getRed(), center.getGreen(), center.getBlue(), Math.max(0, alpha)));
            g.fillRect(x + i, y, Math.max(1, next - i), h);
        }
    }

    private void drawMovingStripes(Graphics g, int x, int y, int w, int h, Color c, int tick, double s) {
        if (w <= 0 || h <= 0) return;
        Shape clip = g.getClip();
        g.setClip(x, y, w, h);
        g.setColor(c);
        int step = Math.max(4, sc(12, s));
        int offset = (tick * Math.max(1, sc(2, s))) % step;
        for (int sx = x - h - step + offset; sx < x + w + h; sx += step) {
            g.drawLine(sx, y + h, sx + h, y);
        }
        g.setClip(clip);
    }

    private int moduleFillWidth(String value, int meterW, int tick, int index) {
        if (value.equals("COMPLETE") || value.equals("PATCHED") || value.equals("RUNNING") || value.equals("BYPASSED")) {
            return meterW;
        }
        if (value.equals("ACTIVE") || value.equals("EXECUTING") || value.equals("LOADING") || value.equals("PATCHING")) {
            int base = index == 0 ? 38 : (index == 1 ? 18 : 72);
            int wobble = tick % 14 < 7 ? 4 : 0;
            return meterW * Math.min(100, base + wobble) / 100;
        }
        return meterW * 8 / 100;
    }

    private boolean isModuleActive(String value) {
        return value.equals("ACTIVE") || value.equals("EXECUTING") || value.equals("LOADING")
            || value.equals("PATCHING") || value.equals("BYPASSED") || value.equals("PATCHED")
            || value.equals("COMPLETE") || value.equals("RUNNING");
    }

    private int badgeWidth(Graphics g, String text, double s) {
        Font old = g.getFont();
        g.setFont(scaleFont(badgeFont, s));
        int width = g.getFontMetrics().stringWidth(text) + sc(44, s);
        g.setFont(old);
        return width;
    }

    private int trackedWidthWithFont(Graphics g, String text, Font f, int tracking) {
        Font old = g.getFont();
        g.setFont(f);
        int width = trackedWidth(g, text, tracking);
        g.setFont(old);
        return width;
    }

    private int trackedWidth(Graphics g, String text, int tracking) {
        FontMetrics fm = g.getFontMetrics();
        int width = 0;
        for (int i = 0; i < text.length(); i++) {
            width += fm.charWidth(text.charAt(i));
            if (i < text.length() - 1) width += tracking;
        }
        return width;
    }

    private void drawTrackedString(Graphics g, String text, int x, int y, int tracking) {
        FontMetrics fm = g.getFontMetrics();
        int cx = x;
        for (int i = 0; i < text.length(); i++) {
            String ch = text.substring(i, i + 1);
            g.drawString(ch, cx, y);
            cx += fm.charWidth(text.charAt(i)) + tracking;
        }
    }

    private String shorten(String text, FontMetrics fm, int maxWidth) {
        if (fm.stringWidth(text) <= maxWidth) return text;
        String ellipsis = "...";
        int limit = text.length();
        while (limit > 0 && fm.stringWidth(text.substring(0, limit) + ellipsis) > maxWidth) {
            limit--;
        }
        return limit > 0 ? text.substring(0, limit) + ellipsis : ellipsis;
    }

    private Font scaleFont(Font base, double s) {
        int size = Math.max(1, (int) Math.round(base.getSize() * s));
        return scaleFontForPixels(base, size);
    }

    private Font scaleFontForPixels(Font base, int pixels) {
        int size = Math.max(1, pixels);
        if (base == progressFont || base == moduleLabelFont || base == badgeFont) {
            return loadDvbFont("PixelCyr", size, "Monospaced");
        }
        return loadDvbFont("FinalFantasyVII", size, "Monospaced");
    }

    private int sc(int value, double s) {
        return (int) Math.round(value * s);
    }

    public static void clear() { getInstance().clearMessages(); }

    public void clearMessages() {
        synchronized (messages) {
            messages.removeAllElements();
            setProgressLocked(0, "BOOTING...");
            isDirty = true;
        }
        safeRepaint();
    }
}
