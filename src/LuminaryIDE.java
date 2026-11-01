
import javax.imageio.ImageIO;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.plaf.LayerUI;
import javax.swing.plaf.basic.*;
import javax.swing.text.*;
import javax.swing.text.rtf.RTFEditorKit;
import javax.swing.undo.UndoManager;

import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.prefs.Preferences;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public class LuminaryIDE implements ActionListener {

    /* =======================================================================
     * 1. CONSTANTS / FIELDS
     * ======================================================================= */

    private static final int CTRL       = InputEvent.CTRL_DOWN_MASK;
    private static final int CTRL_SHIFT = InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK;
    private static final int ALT        = InputEvent.ALT_DOWN_MASK;

    private static final String CARD_LOGIN   = "LOGIN";
    private static final String CARD_WELCOME = "WELCOME";
    private static final String CARD_EDITOR  = "EDITOR";
    private static final String OBJ_REPLACEMENT = "\uFFFC";

    private static final int TEXT_TOP_PAD          = 10;
    private static final int TEXT_BOTTOM_PAD       = 10;
    private static final int TEXT_RIGHT_PAD        = 14;
    private static final int TEXT_LEFT_PAD_NUMS    = 4;
    private static final int TEXT_LEFT_PAD_NO_NUMS = 40;

    private static String UI_FAMILY;
    private static String MONO_FAMILY;

    private static final Path DRAFT_PATH =
            Paths.get(System.getProperty("user.home"), ".luminary", "draft.txt");

    /* Frame */
    private JFrame frame;
    private FlatMenuBar menuBar;
    private JMenu fileMenu, editMenu, selectionMenu, viewMenu, runMenu, codeMenu,
            themeMenu, helpMenu, appearanceMenu, tabSizeMenu, fontSizeMenu, recentMenu, langMenu;

    private JMenuItem newFile, newWindow, openFile, saveFile, saveAsFile,
            insertFilePath, insertImageItem, clearRecent, exitApp, nextThemeItem;
    private JMenuItem undoItem, redoItem, cut, copy, paste, deleteItem, selectAll;
    private JMenuItem chooseFont;
    private JMenuItem[] fontSizeItems;
    private JMenuItem zoomIn, zoomOut, zoomReset;
    private JMenuItem duplicateLine, moveLineUp, moveLineDown, toggleComment;
    private JMenuItem gotoLine, findItem, replaceItem, timeDate;
    private JMenuItem showWelcomeItem, volumeItem, about;
    private JMenuItem runCodeItem, stopCodeItem, clearTerminalItem;
    private JCheckBoxMenuItem wordWrap, statusBarItem, lineNumbersItem, autoIndentItem,
            autoCloseItem, bracketMatchItem, syntaxItem, rainbowItem, currentLineItem,
            soundItem, keyClickItem, terminalItem;
    private JRadioButtonMenuItem[] themeItems;
    private ButtonGroup themeGroup;

    private JButton signOutBtn;

    /* Toolbar */
    private JToolBar toolBar;
    private final List<Component> toolbarButtons = new ArrayList<>();
    private JComboBox<String> themeCombo;
    private JComboBox<String> langCombo;
    private JLabel fontSizeLabel;
    private IDEToggleButton tbWrap, tbLineNumbers, tbTerminal;
    private IDEButton tbRun, tbStop;

    /* Editor */
    private JTextPane textArea;
    private JScrollPane scrollPane;
    private JSplitPane editorSplitPane;
    private ConsolePanel consolePanel;
    private LineNumberView lineNumberView;
    private JPanel statusBar;
    private JLabel statLnCol, statSel, statLines, statWords, statEnc, statTheme, statUser, statLang, statTerminal;
    private JPanel statusBarInner, statusBarLeft, statusBarRight;

    /* Cards */
    private CardLayout cardLayout;
    private JPanel cardPanel;
    private JLayer<JComponent> cardLayer;
    private FadeLayerUI cardFadeLayerUI;
    private JPanel editorCard;
    private JPanel findReplacePanel;
    private WelcomePanel welcomePanel;
    private JLayer<JComponent> welcomeLayer;
    private FadeLayerUI fadeLayerUI;
    private boolean showingWelcome = false;
    private boolean cardSwitching = false;

    /* Login */
    private LoginPanel loginPanel;
    private JLayer<JComponent> loginLayer;
    private FadeLayerUI loginFadeLayerUI;
    private boolean showingLogin = false;
    private String loggedInUser = null;

    /* Editor state */
    private UndoManager undoManager;
    private File currentFile = null;
    private boolean isDirty = false;
    private int fontSize = 14;
    private String fontFamily;
    private boolean autoIndentEnabled = true;
    private boolean autoCloseEnabled = true;
    private boolean bracketMatchEnabled = true;
    private boolean syntaxEnabled = true;
    private boolean rainbowBracketsEnabled = true;
    private boolean currentLineEnabled = true;
    private int tabSize = 4;
    private boolean soundEnabled = true;
    private boolean keyClickEnabled = false;
    private float soundVolume = 0.35f;
    private boolean showWelcomeOnStartup = true;

    private boolean wrapEnabled = true;
    private File pendingFile = null;

    private Theme theme;
    private SoundManager sound;

    private final Preferences prefs = Preferences.userNodeForPackage(LuminaryIDE.class);

    private static final String PREF_RECENT       = "recentFiles";
    private static final String PREF_THEME_NAME   = "themeName";
    private static final String PREF_FONT         = "fontFamily";
    private static final String PREF_SIZE         = "fontSize";
    private static final String PREF_AUTOINDENT   = "autoIndent";
    private static final String PREF_AUTOCLOSE    = "autoClose";
    private static final String PREF_BRACKETMATCH = "bracketMatch";
    private static final String PREF_SYNTAX       = "syntax";
    private static final String PREF_RAINBOW      = "rainbow";
    private static final String PREF_CURLINE      = "currentLine";
    private static final String PREF_TABSIZE      = "tabSize";
    private static final String PREF_DRAFT_DIRTY  = "unsavedDraftDirty";
    private static final String PREF_SOUND        = "soundEnabled";
    private static final String PREF_KEYCLICK     = "keyClickEnabled";
    private static final String PREF_VOLUME       = "soundVolume";
    private static final String PREF_WELCOME      = "showWelcome";
    private static final String PREF_REMEMBER     = "rememberUser";
    private static final String PREF_USERNAME     = "lastUsername";
    private static final String PREF_USERS        = "userAccounts";
    private static final String PREF_TIP          = "tipIndex";
    private static final String PREF_IMGWARN      = "imageWarningShown";
    private static final String PREF_WINX = "winX", PREF_WINY = "winY",
            PREF_WINW = "winW", PREF_WINH = "winH",
            PREF_WINMAX = "winMax";

    /* Find panel */
    private JTextField findField, replaceField;
    private JToggleButton matchCaseToggle, regexToggle;
    private JLabel findCounterLabel;
    private JButton findNextBtn, findPrevBtn, replaceBtn, replaceAllBtn;

    /* Timers */
    private javax.swing.Timer autoSaveTimer, highlightTimer;
    private boolean docLock = false;
    private boolean imageWarningShown;
    private boolean syncingThemeCombo = false;

    private Object bracketTagA, bracketTagB;

    private SimpleAttributeSet stylePlain, styleKeyword, styleControlKeyword, styleMethod,
            styleType, styleConstant, styleString, styleComment,
            styleNumber, styleAnnotation, styleOperator;

    private static final int[] FONT_SIZES = { 8, 9, 10, 11, 12, 14, 16, 18, 20, 22, 24, 28, 32, 40, 48, 56, 64, 72 };

    /* ---- Per-language syntax patterns ---- */

    private static final Pattern JAVA_PATTERN = Pattern.compile(
            "(?<comment>//[^\\n]*|/\\*[\\s\\S]*?\\*/)" +
            "|(?<string>\"(?:\\\\.|[^\"\\\\\\n])*\"|'(?:\\\\.|[^'\\\\\\n])*')" +
            "|(?<annotation>@[A-Za-z_][A-Za-z0-9_]*)" +
            "|(?<control>\\b(?:if|else|for|while|do|switch|case|default|break|continue|return|try|catch|finally|throw|throws|yield|goto)\\b)" +
            "|(?<keyword>\\b(?:abstract|assert|boolean|byte|char|class|const|double|enum|extends|final|float|implements|import|instanceof|int|interface|long|native|new|package|private|protected|public|record|sealed|short|static|strictfp|super|synchronized|this|transient|var|void|volatile|permits)\\b)" +
            "|(?<constant>\\b(?:true|false|null|[A-Z_][A-Z0-9_]{2,})\\b)" +
            "|(?<method>\\b[a-zA-Z_$][a-zA-Z0-9_$]*(?=\\s*\\())" +
            "|(?<type>\\b[A-Z][A-Za-z0-9_$]*\\b)" +
            "|(?<number>\\b0[xX][0-9a-fA-F_]+[lL]?|\\b\\d[\\d_]*(?:\\.\\d+)?(?:[eE][+-]?\\d+)?[fFdDlL]?\\b)" +
            "|(?<operator>[+\\-*/%&|^!~=<>?:;]+)");

    private static final Pattern PY_PATTERN = Pattern.compile(
            "(?<comment>#[^\\n]*)" +
            "|(?<string>\"\"\"[\\s\\S]*?\"\"\"|'''[\\s\\S]*?'''|\"(?:\\\\.|[^\"\\\\\\n])*\"|'(?:\\\\.|[^'\\\\\\n])*')" +
            "|(?<annotation>@[A-Za-z_][A-Za-z0-9_]*)" +
            "|(?<control>\\b(?:if|elif|else|for|while|break|continue|return|try|except|finally|raise|with|as|yield)\\b)" +
            "|(?<keyword>\\b(?:def|class|import|from|global|nonlocal|pass|lambda|del|and|or|not|in|is|async|await)\\b)" +
            "|(?<constant>\\b(?:True|False|None|self|cls|[A-Z_][A-Z0-9_]{2,})\\b)" +
            "|(?<method>\\b[a-zA-Z_][a-zA-Z0-9_]*(?=\\s*\\())" +
            "|(?<type>\\b[A-Z][A-Za-z0-9_]*\\b)" +
            "|(?<number>\\b0[xX][0-9a-fA-F_]+|\\b\\d[\\d_]*(?:\\.\\d+)?(?:[eE][+-]?\\d+)?\\b)" +
            "|(?<operator>[+\\-*/%&|^!~=<>:]+)");

    private static final Pattern JS_PATTERN = Pattern.compile(
            "(?<comment>//[^\\n]*|/\\*[\\s\\S]*?\\*/)" +
            "|(?<string>\"(?:\\\\.|[^\"\\\\\\n])*\"|'(?:\\\\.|[^'\\\\\\n])*'|`(?:\\\\.|[^`\\\\])*`)" +
            "|(?<annotation>@[A-Za-z_][A-Za-z0-9_]*)" +
            "|(?<control>\\b(?:if|else|for|while|do|switch|case|default|break|continue|return|try|catch|finally|throw|yield|await)\\b)" +
            "|(?<keyword>\\b(?:const|let|var|function|class|extends|interface|type|export|import|from|as|default|new|this|super|typeof|instanceof|void|delete|in|of|async)\\b)" +
            "|(?<constant>\\b(?:true|false|null|undefined|NaN|Infinity|[A-Z_][A-Z0-9_]{2,})\\b)" +
            "|(?<method>\\b[a-zA-Z_$][a-zA-Z0-9_$]*(?=\\s*\\())" +
            "|(?<type>\\b[A-Z][A-Za-z0-9_$]*\\b)" +
            "|(?<number>\\b0[xX][0-9a-fA-F_]+|\\b\\d[\\d_]*(?:\\.\\d+)?(?:[eE][+-]?\\d+)?\\b)" +
            "|(?<operator>[+\\-*/%&|^!~=<>?:]+|=>)");

    private static final Pattern HTML_PATTERN = Pattern.compile(
            "(?<comment><!--[\\s\\S]*?-->)" +
            "|(?<string>\"(?:\\\\.|[^\"\\\\\\n])*\"|'(?:\\\\.|[^'\\\\\\n])*')" +
            "|(?<keyword><\\/?[a-zA-Z0-9_\\-:]+)" +
            "|(?<type>[a-zA-Z_\\-:]+(?=\\s*=))" +
            "|(?<operator>[<>/=])");

    private static final Pattern CSS_PATTERN = Pattern.compile(
            "(?<comment>/\\*[\\s\\S]*?\\*/)" +
            "|(?<string>\"(?:\\\\.|[^\"\\\\\\n])*\"|'(?:\\\\.|[^'\\\\\\n])*')" +
            "|(?<annotation>@[a-zA-Z\\-]+)" +
            "|(?<keyword>[a-zA-Z\\-]+(?=\\s*:))" +
            "|(?<constant>#[0-9a-fA-F]{3,8}|\\b(?:transparent|currentColor)\\b)" +
            "|(?<number>\\b\\d+(?:\\.\\d+)?(?:px|em|rem|vh|vw|%|s|ms|deg|pt)?\\b)" +
            "|(?<type>\\.[a-zA-Z0-9_\\-]+|#[a-zA-Z0-9_\\-]+)" +
            "|(?<method>\\b[a-zA-Z\\-]+(?=\\s*\\())");

    private static final Pattern SQL_PATTERN = Pattern.compile(
            "(?<comment>--[^\\n]*|/\\*[\\s\\S]*?\\*/)" +
            "|(?<string>'(?:''|[^'])*')" +
            "|(?<control>\\b(?:IF|THEN|ELSE|ELSEIF|END|BEGIN|CASE|WHEN|WHILE|LOOP|RETURN)\\b)" +
            "|(?<keyword>\\b(?:SELECT|FROM|WHERE|INSERT|INTO|UPDATE|DELETE|JOIN|LEFT|RIGHT|INNER|OUTER|FULL|CROSS|ON|GROUP|BY|ORDER|HAVING|LIMIT|OFFSET|CREATE|TABLE|DATABASE|INDEX|VIEW|DROP|ALTER|ADD|COLUMN|PRIMARY|KEY|FOREIGN|REFERENCES|NOT|NULL|UNIQUE|DEFAULT|CHECK|AND|OR|IN|BETWEEN|LIKE|IS|EXISTS|UNION|ALL|AS|DISTINCT)\\b)" +
            "|(?<type>\\b(?:INT|INTEGER|BIGINT|SMALLINT|TINYINT|FLOAT|DOUBLE|DECIMAL|NUMERIC|VARCHAR|CHAR|TEXT|BLOB|BOOLEAN|DATE|DATETIME|TIMESTAMP|TIME)\\b)" +
            "|(?<constant>\\b(?:TRUE|FALSE|NULL)\\b)" +
            "|(?<method>\\b[a-zA-Z_][a-zA-Z0-9_]*(?=\\s*\\())" +
            "|(?<number>\\b\\d+(?:\\.\\d+)?\\b)");

    private static final Pattern MD_PATTERN = Pattern.compile(
            "(?<comment><!--[\\s\\S]*?-->)" +
            "|(?<keyword>^#{1,6}\\s+.*$)" +
            "|(?<string>`[^`\\n]+`|```[\\s\\S]*?```)" +
            "|(?<control>^\\s*[-*+]\\s+.*$|^\\s*\\d+\\.\\s+.*$)" +
            "|(?<annotation>\\[.*?\\]\\(.*?\\))" +
            "|(?<constant>\\*\\*.*?\\*\\*|__.*?__)");

    private static final String[] SUPPORTED_LANGUAGES = {
            "Java", "Python", "JavaScript", "C/C++", "HTML/XML", "CSS", "JSON", "SQL", "Markdown", "Plain Text"
    };

    private String currentLanguage = "Java";

    private static Icon langIcon(String lang) {
        return IDEIcons.forLanguage(lang);
    }

    private String detectLanguage(File file) {
        if (file == null) return currentLanguage != null ? currentLanguage : "Java";
        String n = file.getName().toLowerCase();
        if (n.endsWith(".java")) return "Java";
        if (n.endsWith(".py") || n.endsWith(".pyw")) return "Python";
        if (n.endsWith(".js") || n.endsWith(".jsx") || n.endsWith(".ts") || n.endsWith(".tsx")
                || n.endsWith(".mjs") || n.endsWith(".cjs")) return "JavaScript";
        if (n.endsWith(".json")) return "JSON";
        if (n.endsWith(".c") || n.endsWith(".cpp") || n.endsWith(".h") || n.endsWith(".hpp") || n.endsWith(".cs")) return "C/C++";
        if (n.endsWith(".html") || n.endsWith(".htm") || n.endsWith(".xml") || n.endsWith(".svg")) return "HTML/XML";
        if (n.endsWith(".css") || n.endsWith(".scss")) return "CSS";
        if (n.endsWith(".sql")) return "SQL";
        if (n.endsWith(".md") || n.endsWith(".markdown")) return "Markdown";
        return "Plain Text";
    }

    public String getEffectiveLanguage() {
        if (currentFile != null) return detectLanguage(currentFile);
        return currentLanguage != null ? currentLanguage : "Java";
    }

    public void setLanguage(String lang) {
        if (lang == null) return;
        this.currentLanguage = lang;
        if (statLang != null) {
            statLang.setIcon(IDEIcons.forLanguage(lang));
            statLang.setText(" " + lang);
        }
        if (langCombo != null && !lang.equals(langCombo.getSelectedItem())) {
            langCombo.setSelectedItem(lang);
        }
        highlightSyntax();
    }

    private Pattern patternForLanguage(String lang) {
        if (lang == null) return JAVA_PATTERN;
        switch (lang) {
            case "Java": case "C/C++": return JAVA_PATTERN;
            case "Python": return PY_PATTERN;
            case "JavaScript": case "JSON": return JS_PATTERN;
            case "HTML/XML": return HTML_PATTERN;
            case "CSS": return CSS_PATTERN;
            case "SQL": return SQL_PATTERN;
            case "Markdown": return MD_PATTERN;
            case "Plain Text": return null;
            default: return JAVA_PATTERN;
        }
    }

    private static boolean matchGroup(Matcher m, String name) {
        try {
            return m.group(name) != null;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private final DocumentListener documentListener = new DocumentListener() {
        public void insertUpdate(DocumentEvent e)  { documentChanged(true); }
        public void removeUpdate(DocumentEvent e)  { documentChanged(true); }
        public void changedUpdate(DocumentEvent e) { documentChanged(false); }
    };

    /* =======================================================================
     * 2. ANIMATION HELPERS
     * ======================================================================= */

    private static float easeOutCubic(float p) {
        return 1f - (float) Math.pow(1 - p, 3);
    }

    static void installSmoothHover(JComponent c,
                                   Supplier<Color> normal,
                                   Supplier<Color> hover) {
        c.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) {
                Color h = hover.get();
                if (h != null) c.setBackground(h);
                c.repaint();
            }
            @Override public void mouseExited(MouseEvent e) {
                Color n = normal.get();
                if (n != null) c.setBackground(n);
                c.repaint();
            }
        });
        c.addMouseMotionListener(new MouseMotionAdapter() {
            @Override public void mouseMoved(MouseEvent e) {
                Color h = hover.get();
                if (h != null && !h.equals(c.getBackground())) {
                    c.setBackground(h);
                    c.repaint();
                }
            }
            @Override public void mouseDragged(MouseEvent e) {
                boolean inside = c.contains(e.getPoint());
                Color target = inside ? hover.get() : normal.get();
                if (target != null && !target.equals(c.getBackground())) {
                    c.setBackground(target);
                    c.repaint();
                }
            }
        });
    }

    /* =======================================================================
     * 3. THEME
     * ======================================================================= */

    static final class Theme {
        final String name; final boolean dark;
        Color bg, fg, caret, selection, selectionFg, currentLine, accent, accentSoft;
        Color gutterBg, gutterFg, gutterCurrent, gutterFgCur;
        Color keyword, controlKeyword, method, type, constant, string, comment, number, annotation, operator;
        Color bracket1, bracket2, bracket3, bracket4;
        Color chromeBg, panelBg, border, hover, textMuted;
        Theme(String name, boolean dark) { this.name = name; this.dark = dark; }
    }

    static final ArrayList<Theme> THEMES = new ArrayList<>();
    private static Color c(int rgb) { return new Color(rgb); }

    static {
        Theme t;

        // 1. Light Theme
        t = new Theme("Light", false);
        t.bg = c(0xFFFFFF); t.fg = c(0x1F2328); t.caret = c(0x0969DA);
        t.selection = c(0xB6E3FF); t.selectionFg = c(0x1F2328);
        t.currentLine = c(0xF6F8FA); t.accent = c(0x0969DA); t.accentSoft = c(0xDDF4FF);
        t.chromeBg = c(0xF6F8FA); t.panelBg = c(0xF6F8FA);
        t.border = c(0xD0D7DE); t.hover = c(0xEAEEF2); t.textMuted = c(0x57606A);
        t.gutterBg = c(0xF8FAFC); t.gutterFg = c(0x6E7781);
        t.gutterCurrent = c(0xEAEEF2); t.gutterFgCur = c(0x0969DA);
        t.keyword = c(0x0550AE); t.controlKeyword = c(0xCF222E); t.method = c(0x8250DF);
        t.type = c(0x953800); t.constant = c(0x0550AE); t.string = c(0x0A3069);
        t.comment = c(0x57606A); t.number = c(0x0550AE); t.annotation = c(0x8250DF);
        t.operator = c(0x24292F);
        t.bracket1 = c(0xCF222E); t.bracket2 = c(0x0969DA);
        t.bracket3 = c(0x1A7F37); t.bracket4 = c(0x9A6700);
        THEMES.add(t);

        // 2. Dark Theme (VS Code Modern Dark+)
        t = new Theme("Dark", true);
        t.bg = c(0x1E1E1E); t.fg = c(0xD4D4D4); t.caret = c(0xAEAFAD);
        t.selection = c(0x264F78); t.selectionFg = c(0xFFFFFF);
        t.currentLine = c(0x2A2A2A); t.accent = c(0x4C9EFF); t.accentSoft = c(0x1F3A5F);
        t.chromeBg = c(0x252526); t.panelBg = c(0x252526);
        t.border = c(0x3C3C3C); t.hover = c(0x333333); t.textMuted = c(0x9E9E9E);
        t.gutterBg = c(0x1E1E1E); t.gutterFg = c(0x858585);
        t.gutterCurrent = c(0x282828); t.gutterFgCur = c(0xC6C6C6);
        t.keyword = c(0x569CD6); t.controlKeyword = c(0xC586C0); t.method = c(0xDCDCAA);
        t.type = c(0x4EC9B0); t.constant = c(0x4FC1FF); t.string = c(0xCE9178);
        t.comment = c(0x6A9955); t.number = c(0xB5CEA8); t.annotation = c(0xDCDCAA);
        t.operator = c(0xD4D4D4);
        t.bracket1 = c(0xFFD700); t.bracket2 = c(0xDA70D6);
        t.bracket3 = c(0x179FFF); t.bracket4 = c(0x00E676);
        THEMES.add(t);

        // 3. Ocean Theme (Deep Cobalt / Mariana)
        t = new Theme("Ocean", true);
        t.bg = c(0x0F172A); t.fg = c(0xE2E8F0); t.caret = c(0x38BDF8);
        t.selection = c(0x1E40AF); t.selectionFg = c(0xF1F5F9);
        t.currentLine = c(0x1E293B); t.accent = c(0x38BDF8); t.accentSoft = c(0x0C4A6E);
        t.chromeBg = c(0x1E293B); t.panelBg = c(0x1E293B);
        t.border = c(0x334155); t.hover = c(0x273549); t.textMuted = c(0x94A3B8);
        t.gutterBg = c(0x0F172A); t.gutterFg = c(0x8FA2BC);
        t.gutterCurrent = c(0x1E293B); t.gutterFgCur = c(0xF1F5F9);
        t.keyword = c(0x38BDF8); t.controlKeyword = c(0xF472B6); t.method = c(0xFDE047);
        t.type = c(0x6EE7B7); t.constant = c(0x38BDF8); t.string = c(0xA7F3D0);
        t.comment = c(0x7E99B8); t.number = c(0xFCA5A5); t.annotation = c(0xC084FC);
        t.operator = c(0xE2E8F0);
        t.bracket1 = c(0xF472B6); t.bracket2 = c(0x60A5FA);
        t.bracket3 = c(0x34D399); t.bracket4 = c(0xFBBF24);
        THEMES.add(t);

        // 4. Sunset Theme (Warm Charcoal / Terracotta)
        t = new Theme("Sunset", true);
        t.bg = c(0x1A1417); t.fg = c(0xFCECE4); t.caret = c(0xF97316);
        t.selection = c(0x6B3A1F); t.selectionFg = c(0xFFEEE4);
        t.currentLine = c(0x281A1F); t.accent = c(0xF97316); t.accentSoft = c(0x5C3418);
        t.chromeBg = c(0x2B1A20); t.panelBg = c(0x2B1A20);
        t.border = c(0x4A2E34); t.hover = c(0x3D262B); t.textMuted = c(0xBD9E9C);
        t.gutterBg = c(0x1A1417); t.gutterFg = c(0xA2848B);
        t.gutterCurrent = c(0x2B1A20); t.gutterFgCur = c(0xFCECE4);
        t.keyword = c(0xF472B6); t.controlKeyword = c(0xFB7185); t.method = c(0xFBBF24);
        t.type = c(0x34D399); t.constant = c(0xF97316); t.string = c(0xFDE68A);
        t.comment = c(0xB09096); t.number = c(0xFB923C); t.annotation = c(0xE879F9);
        t.operator = c(0xFCECE4);
        t.bracket1 = c(0xF0879F); t.bracket2 = c(0xE8C170);
        t.bracket3 = c(0x7ECBA1); t.bracket4 = c(0xC4A0E8);
        THEMES.add(t);

        // 5. Forest Theme (Emerald Dark)
        t = new Theme("Forest", true);
        t.bg = c(0x0D1612); t.fg = c(0xDCFCE7); t.caret = c(0x22C55E);
        t.selection = c(0x166534); t.selectionFg = c(0xF0FDF4);
        t.currentLine = c(0x14251D); t.accent = c(0x4ADE80); t.accentSoft = c(0x14532D);
        t.chromeBg = c(0x13221A); t.panelBg = c(0x13221A);
        t.border = c(0x273E31); t.hover = c(0x1E3528); t.textMuted = c(0x86EFAC);
        t.gutterBg = c(0x0D1612); t.gutterFg = c(0x729881);
        t.gutterCurrent = c(0x13221A); t.gutterFgCur = c(0xDCFCE7);
        t.keyword = c(0x38BDF8); t.controlKeyword = c(0x4ADE80); t.method = c(0xFACC15);
        t.type = c(0x5EEAD4); t.constant = c(0x86EFAC); t.string = c(0xBBF7D0);
        t.comment = c(0x7FA890); t.number = c(0xFDE047); t.annotation = c(0xC084FC);
        t.operator = c(0xDCFCE7);
        t.bracket1 = c(0xA7E8A0); t.bracket2 = c(0x7DD3FC);
        t.bracket3 = c(0xF0C674); t.bracket4 = c(0xC4A0E8);
        THEMES.add(t);

        // 6. Dracula Theme
        t = new Theme("Dracula", true);
        t.bg = c(0x282A36); t.fg = c(0xF8F8F2); t.caret = c(0xFF79C6);
        t.selection = c(0x44475A); t.selectionFg = c(0xFFFFFF);
        t.currentLine = c(0x323545); t.accent = c(0xBD93F9); t.accentSoft = c(0x44475A);
        t.chromeBg = c(0x21222C); t.panelBg = c(0x21222C);
        t.border = c(0x44475A); t.hover = c(0x343746); t.textMuted = c(0x95A3CD);
        t.gutterBg = c(0x282A36); t.gutterFg = c(0x7E90BF);
        t.gutterCurrent = c(0x343746); t.gutterFgCur = c(0xF8F8F2);
        t.keyword = c(0x8BE9FD); t.controlKeyword = c(0xFF79C6); t.method = c(0x50FA7B);
        t.type = c(0xBD93F9); t.constant = c(0xBD93F9); t.string = c(0xF1FA8C);
        t.comment = c(0x8DA0D4); t.number = c(0xBD93F9); t.annotation = c(0x50FA7B);
        t.operator = c(0xF8F8F2);
        t.bracket1 = c(0xFF79C6); t.bracket2 = c(0xF1FA8C);
        t.bracket3 = c(0x8BE9FD); t.bracket4 = c(0x50FA7B);
        THEMES.add(t);

        // 7. Nord Theme
        t = new Theme("Nord", true);
        t.bg = c(0x2E3440); t.fg = c(0xECEFF4); t.caret = c(0x88C0D0);
        t.selection = c(0x434C5E); t.selectionFg = c(0xECEFF4);
        t.currentLine = c(0x363D4B); t.accent = c(0x88C0D0); t.accentSoft = c(0x434C5E);
        t.chromeBg = c(0x272C36); t.panelBg = c(0x272C36);
        t.border = c(0x4C566A); t.hover = c(0x3B4252); t.textMuted = c(0x9BA7BC);
        t.gutterBg = c(0x2E3440); t.gutterFg = c(0x9AAAC5);
        t.gutterCurrent = c(0x3B4252); t.gutterFgCur = c(0xECEFF4);
        t.keyword = c(0x81A1C1); t.controlKeyword = c(0x5E81AC); t.method = c(0x88C0D0);
        t.type = c(0x8FBCBB); t.constant = c(0xD08770); t.string = c(0xA3BE8C);
        t.comment = c(0x8EA0C0); t.number = c(0xB48EAD); t.annotation = c(0xEBCB8B);
        t.operator = c(0xECEFF4);
        t.bracket1 = c(0x88C0D0); t.bracket2 = c(0xEBCB8B);
        t.bracket3 = c(0xA3BE8C); t.bracket4 = c(0xB48EAD);
        THEMES.add(t);

        // 8. Monokai Pro Theme
        t = new Theme("Monokai", true);
        t.bg = c(0x1E1F1C); t.fg = c(0xF8F8F2); t.caret = c(0xF8F8F0);
        t.selection = c(0x49483E); t.selectionFg = c(0xFFFFFF);
        t.currentLine = c(0x2B2C26); t.accent = c(0xA6E22E); t.accentSoft = c(0x3E4A18);
        t.chromeBg = c(0x272822); t.panelBg = c(0x272822);
        t.border = c(0x3E3D32); t.hover = c(0x35362E); t.textMuted = c(0xB0ABA0);
        t.gutterBg = c(0x1E1F1C); t.gutterFg = c(0x888370);
        t.gutterCurrent = c(0x2B2C26); t.gutterFgCur = c(0xF8F8F2);
        t.keyword = c(0x66D9EF); t.controlKeyword = c(0xF92672); t.method = c(0xA6E22E);
        t.type = c(0x66D9EF); t.constant = c(0xAE81FF); t.string = c(0xE6DB74);
        t.comment = c(0x96907C); t.number = c(0xAE81FF); t.annotation = c(0xFD971F);
        t.operator = c(0xF8F8F2);
        t.bracket1 = c(0xF92672); t.bracket2 = c(0xE6DB74);
        t.bracket3 = c(0xA6E22E); t.bracket4 = c(0x66D9EF);
        THEMES.add(t);

        // 9. Cyberpunk Theme
        t = new Theme("Cyberpunk", true);
        t.bg = c(0x0D0B1F); t.fg = c(0xF0F0FF); t.caret = c(0xF72585);
        t.selection = c(0x3A0CA3); t.selectionFg = c(0xFFFFFF);
        t.currentLine = c(0x181434); t.accent = c(0xF72585); t.accentSoft = c(0x4A0E3A);
        t.chromeBg = c(0x14112E); t.panelBg = c(0x14112E);
        t.border = c(0x2E2A4D); t.hover = c(0x221E3D); t.textMuted = c(0xB39DDB);
        t.gutterBg = c(0x0D0B1F); t.gutterFg = c(0x8C78BD);
        t.gutterCurrent = c(0x1E1B3A); t.gutterFgCur = c(0xF0F0FF);
        t.keyword = c(0x4CC9F0); t.controlKeyword = c(0xF72585); t.method = c(0xFEE440);
        t.type = c(0x00BBF9); t.constant = c(0xF15BB5); t.string = c(0x4CC9F0);
        t.comment = c(0x9E86CC); t.number = c(0xFEE440); t.annotation = c(0xF15BB5);
        t.operator = c(0xF0F0FF);
        t.bracket1 = c(0xF72585); t.bracket2 = c(0x4CC9F0);
        t.bracket3 = c(0xFEE440); t.bracket4 = c(0xF15BB5);
        THEMES.add(t);

        // 10. Solarized Light
        t = new Theme("Solarized Light", false);
        t.bg = c(0xFDF6E3); t.fg = c(0x586E75); t.caret = c(0x268BD2);
        t.selection = c(0xEEE8D5); t.selectionFg = c(0x073642);
        t.currentLine = c(0xF5EFDC); t.accent = c(0x268BD2); t.accentSoft = c(0xD6E9F5);
        t.chromeBg = c(0xEEE8D5); t.panelBg = c(0xEEE8D5);
        t.border = c(0xD8D2C0); t.hover = c(0xE4DEC8); t.textMuted = c(0x586E75);
        t.gutterBg = c(0xFDF6E3); t.gutterFg = c(0x586E75);
        t.gutterCurrent = c(0xEEE8D5); t.gutterFgCur = c(0x073642);
        t.keyword = c(0x268BD2); t.controlKeyword = c(0x859900); t.method = c(0xB58900);
        t.type = c(0x2AA198); t.constant = c(0xCB4B16); t.string = c(0x2AA198);
        t.comment = c(0x4A6168); t.number = c(0xD33682); t.annotation = c(0x6C71C4);
        t.operator = c(0x586E75);
        t.bracket1 = c(0xDC322F); t.bracket2 = c(0x268BD2);
        t.bracket3 = c(0x859900); t.bracket4 = c(0xD33682);
        THEMES.add(t);

        // 11. Solarized Dark
        t = new Theme("Solarized Dark", true);
        t.bg = c(0x002B36); t.fg = c(0x93A1A1); t.caret = c(0x268BD2);
        t.selection = c(0x073642); t.selectionFg = c(0xFDF6E3);
        t.currentLine = c(0x073642); t.accent = c(0x268BD2); t.accentSoft = c(0x0D3D5E);
        t.chromeBg = c(0x073642); t.panelBg = c(0x073642);
        t.border = c(0x0E4452); t.hover = c(0x0A3F4D); t.textMuted = c(0x93A1A1);
        t.gutterBg = c(0x002B36); t.gutterFg = c(0x889DA5);
        t.gutterCurrent = c(0x073642); t.gutterFgCur = c(0xFDF6E3);
        t.keyword = c(0x268BD2); t.controlKeyword = c(0x859900); t.method = c(0xB58900);
        t.type = c(0x2AA198); t.constant = c(0xCB4B16); t.string = c(0x2AA198);
        t.comment = c(0x889DA5); t.number = c(0xD33682); t.annotation = c(0x6C71C4);
        t.operator = c(0x93A1A1);
        t.bracket1 = c(0xDC322F); t.bracket2 = c(0x268BD2);
        t.bracket3 = c(0x859900); t.bracket4 = c(0xD33682);
        THEMES.add(t);

        // 12. Rosé Pine Theme
        t = new Theme("Rosé Pine", true);
        t.bg = c(0x191724); t.fg = c(0xE0DEF4); t.caret = c(0xEB6F92);
        t.selection = c(0x403D52); t.selectionFg = c(0xFFFFFF);
        t.currentLine = c(0x211F30); t.accent = c(0xEBBCBA); t.accentSoft = c(0x3E2B3A);
        t.chromeBg = c(0x1F1D2E); t.panelBg = c(0x1F1D2E);
        t.border = c(0x26233A); t.hover = c(0x2B273F); t.textMuted = c(0xB4ADCC);
        t.gutterBg = c(0x191724); t.gutterFg = c(0x8E88A6);
        t.gutterCurrent = c(0x26233A); t.gutterFgCur = c(0xE0DEF4);
        t.keyword = c(0x31748F); t.controlKeyword = c(0xEB6F92); t.method = c(0xEBBCBA);
        t.type = c(0x9CCFD8); t.constant = c(0xF6C177); t.string = c(0xF6C177);
        t.comment = c(0x9E97B8); t.number = c(0xEB6F92); t.annotation = c(0xC4A7E7);
        t.operator = c(0xE0DEF4);
        t.bracket1 = c(0xEB6F92); t.bracket2 = c(0xF6C177);
        t.bracket3 = c(0x9CCFD8); t.bracket4 = c(0xC4A7E7);
        THEMES.add(t);
    }

    /* =======================================================================
     * 4. SOUND MANAGER
     * ======================================================================= */

    static final class SoundManager {
        static final String STARTUP = "startup", CLICK = "click", SAVE = "save",
                ERROR = "error", NEW = "new", THEME = "theme", IMAGE = "image", KEY = "key";
        private static final int SR = 44100;
        private final Map<String, byte[]> data = new HashMap<>();
        private final Map<String, Clip>   clips = new HashMap<>();
        private final ExecutorService pool = Executors.newSingleThreadExecutor(r -> {
            Thread th = new Thread(r, "luminary-sound"); th.setDaemon(true); return th;
        });
        private volatile boolean enabled = true, available = true;
        private volatile float volume = 0.35f;

        SoundManager() {
            try {
                data.put(STARTUP, sequence(new double[]{523.25, 659.25, 783.99}, new int[]{90, 90, 200}, 0.4));
                data.put(CLICK,   sequence(new double[]{1400}, new int[]{28}, 0.25));
                data.put(SAVE,    sequence(new double[]{659.25, 987.77}, new int[]{80, 140}, 0.35));
                data.put(ERROR,   sequence(new double[]{180, 140}, new int[]{130, 180}, 0.35));
                data.put(NEW,     sequence(new double[]{880, 1174.66}, new int[]{60, 100}, 0.3));
                data.put(THEME,   sweep(420, 1180, 220, 0.22));
                data.put(IMAGE,   sweep(380, 980, 80, 0.32));
                data.put(KEY,     sequence(new double[]{2100}, new int[]{9}, 0.08));
            } catch (Throwable t) { available = false; }
        }

        private static byte[] sequence(double[] freqs, int[] ms, double amp) {
            int total = 0; for (int m : ms) total += SR * m / 1000;
            byte[] out = new byte[total * 2]; int idx = 0;
            for (int n = 0; n < freqs.length; n++) {
                int len = SR * ms[n] / 1000;
                double fi = Math.max(1, SR * 0.008), fo = Math.max(1, SR * 0.030);
                for (int i = 0; i < len; i++) {
                    double t = (double) i / SR;
                    double env = Math.min(1.0, Math.min(i / fi, (len - i) / fo));
                    if (env < 0) env = 0;
                    short v = (short) (Math.sin(2 * Math.PI * freqs[n] * t) * env * amp * Short.MAX_VALUE);
                    out[idx++] = (byte) (v & 0xFF); out[idx++] = (byte) ((v >> 8) & 0xFF);
                }
            }
            return out;
        }

        private static byte[] sweep(double f0, double f1, int ms, double amp) {
            int len = SR * ms / 1000; byte[] out = new byte[len * 2];
            double phase = 0, fi = Math.max(1, SR * 0.006), fo = Math.max(1, SR * 0.030);
            for (int i = 0; i < len; i++) {
                double p = (double) i / len;
                phase += 2 * Math.PI * (f0 + (f1 - f0) * p) / SR;
                double env = Math.min(1.0, Math.min(i / fi, (len - i) / fo));
                if (env < 0) env = 0;
                short v = (short) (Math.sin(phase) * env * amp * Short.MAX_VALUE);
                out[2 * i] = (byte) (v & 0xFF); out[2 * i + 1] = (byte) ((v >> 8) & 0xFF);
            }
            return out;
        }

        private Clip clipFor(String name) {
            Clip cached = clips.get(name);
            if (cached != null && cached.isOpen()) return cached;
            byte[] d = data.get(name); if (d == null) return null;
            try {
                Clip clip = AudioSystem.getClip();
                clip.open(new AudioFormat(SR, 16, 1, true, false), d, 0, d.length);
                applyGain(clip); clips.put(name, clip); return clip;
            } catch (Throwable t) { available = false; return null; }
        }

        private void applyGain(Clip clip) {
            try {
                FloatControl g = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
                double v = Math.max(0.0001, volume);
                float dB = (float) (Math.log10(v) * 20.0);
                dB = Math.max(g.getMinimum(), Math.min(g.getMaximum(), dB));
                g.setValue(dB);
            } catch (Throwable ignored) { }
        }

        void play(String name) {
            if (!enabled || !available) return;
            try {
                pool.execute(() -> {
                    try {
                        Clip clip = clipFor(name); if (clip == null) return;
                        synchronized (clip) { clip.stop(); clip.setFramePosition(0); clip.start(); }
                    } catch (Throwable ignored) { }
                });
            } catch (Throwable ignored) { }
        }
        void setEnabled(boolean e) { this.enabled = e; }
        void setVolume(float v) {
            this.volume = Math.max(0f, Math.min(1f, v));
            try { pool.execute(() -> { for (Clip cc : clips.values()) if (cc != null && cc.isOpen()) applyGain(cc); }); }
            catch (Throwable ignored) { }
        }
        void shutdown() {
            try {
                for (Clip cc : clips.values()) { try { cc.stop(); cc.close(); } catch (Throwable ignored) { } }
                clips.clear(); pool.shutdownNow();
            } catch (Throwable ignored) { }
        }
    }

    /* =======================================================================
     * 5. FONT HELPERS
     * ======================================================================= */

    private static synchronized void initFontFamilies() {
        if (UI_FAMILY != null) return;
        Set<String> available = new HashSet<>(Arrays.asList(
                GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
        String[] uiCands = {"Segoe UI", "SF Pro Text", "Helvetica Neue", "Inter", "Roboto", "SansSerif"};
        UI_FAMILY = "SansSerif";
        for (String cf : uiCands) if (available.contains(cf)) { UI_FAMILY = cf; break; }
        String[] monoCands = {"JetBrains Mono", "Cascadia Code", "Cascadia Mono", "Consolas",
                "Menlo", "Monaco", "DejaVu Sans Mono", "Monospaced"};
        MONO_FAMILY = "Monospaced";
        for (String cf : monoCands) if (available.contains(cf)) { MONO_FAMILY = cf; break; }
    }
    private static Font uiFont(int style, int size)   { initFontFamilies(); return new Font(UI_FAMILY, style, size); }
    private static Font monoFont(int style, int size) { initFontFamilies(); return new Font(MONO_FAMILY, style, size); }

    /* =======================================================================
     * 6. FLAT MENU BAR
     * ======================================================================= */

    static class FlatMenuBar extends JMenuBar {
        private Theme theme;
        FlatMenuBar(Theme t) {
            this.theme = t;
            setOpaque(true);
            setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, t.border));
        }
        void setTheme(Theme t) {
            this.theme = t;
            setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, t.border));
            repaint();
        }
        @Override protected void paintComponent(Graphics g) {
            g.setColor(theme != null ? theme.chromeBg : getBackground());
            g.fillRect(0, 0, getWidth(), getHeight());
        }
    }

    /* =======================================================================
     * 6B. IDE ICONS (RESOLUTION-INDEPENDENT VECTOR DRAWING)
     * ======================================================================= */

    static class IDEIcons {
        static class VectorIcon implements Icon {
            private final int width, height;
            private final BiConsumer<Graphics2D, Component> painter;

            VectorIcon(int width, int height, BiConsumer<Graphics2D, Component> painter) {
                this.width = width;
                this.height = height;
                this.painter = painter;
            }

            VectorIcon(BiConsumer<Graphics2D, Component> painter) {
                this(14, 14, painter);
            }

            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.translate(x, y);
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
                painter.accept(g2, c);
                g2.dispose();
            }

            @Override public int getIconWidth() { return width; }
            @Override public int getIconHeight() { return height; }
        }

        private static Color iconFg(Component c) {
            Color fg = (c != null) ? c.getForeground() : null;
            return fg != null ? fg : Color.DARK_GRAY;
        }

        public static final Icon NEW = new VectorIcon((g2, c) -> {
            Color fg = iconFg(c);
            g2.setColor(fg);
            g2.setStroke(new BasicStroke(1.2f));
            Polygon poly = new Polygon();
            poly.addPoint(2, 1);
            poly.addPoint(8, 1);
            poly.addPoint(12, 5);
            poly.addPoint(12, 13);
            poly.addPoint(2, 13);
            g2.drawPolygon(poly);
            g2.drawLine(8, 1, 8, 5);
            g2.drawLine(8, 5, 12, 5);
            g2.drawLine(4, 8, 10, 8);
            g2.drawLine(4, 10, 8, 10);
        });

        public static final Icon OPEN = new VectorIcon((g2, c) -> {
            Color fg = iconFg(c);
            g2.setColor(fg);
            g2.setStroke(new BasicStroke(1.2f));
            Polygon tab = new Polygon();
            tab.addPoint(1, 4);
            tab.addPoint(1, 2);
            tab.addPoint(5, 2);
            tab.addPoint(7, 4);
            tab.addPoint(12, 4);
            tab.addPoint(12, 6);
            tab.addPoint(1, 6);
            g2.drawPolygon(tab);
            Polygon flap = new Polygon();
            flap.addPoint(1, 6);
            flap.addPoint(11, 6);
            flap.addPoint(13, 12);
            flap.addPoint(3, 12);
            g2.drawPolygon(flap);
        });

        public static final Icon SAVE = new VectorIcon((g2, c) -> {
            Color fg = iconFg(c);
            g2.setColor(fg);
            g2.setStroke(new BasicStroke(1.2f));
            Polygon poly = new Polygon();
            poly.addPoint(2, 1);
            poly.addPoint(10, 1);
            poly.addPoint(12, 3);
            poly.addPoint(12, 13);
            poly.addPoint(2, 13);
            g2.drawPolygon(poly);
            g2.drawRect(4, 2, 4, 3);
            g2.drawRect(4, 8, 5, 4);
        });

        public static final Icon RUN = new VectorIcon((g2, c) -> {
            g2.setColor(new Color(0x22C55E));
            Polygon p = new Polygon();
            p.addPoint(3, 2);
            p.addPoint(12, 7);
            p.addPoint(3, 12);
            g2.fillPolygon(p);
        });

        public static final Icon STOP = new VectorIcon((g2, c) -> {
            g2.setColor(new Color(0xEF4444));
            g2.fillRoundRect(3, 3, 8, 8, 2, 2);
        });

        public static final Icon FIND = new VectorIcon((g2, c) -> {
            Color fg = iconFg(c);
            g2.setColor(fg);
            g2.setStroke(new BasicStroke(1.4f));
            g2.drawOval(2, 2, 7, 7);
            g2.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.drawLine(8, 8, 12, 12);
        });

        public static final Icon GOTO = new VectorIcon((g2, c) -> {
            Color fg = iconFg(c);
            g2.setColor(fg);
            g2.setStroke(new BasicStroke(1.2f));
            g2.drawLine(2, 11, 12, 11);
            g2.drawLine(7, 2, 7, 9);
            g2.drawLine(4, 6, 7, 9);
            g2.drawLine(10, 6, 7, 9);
        });

        public static final Icon COMMENT = new VectorIcon((g2, c) -> {
            Color fg = iconFg(c);
            g2.setColor(fg);
            g2.setStroke(new BasicStroke(1.2f));
            g2.drawRoundRect(1, 2, 11, 7, 4, 4);
            Polygon tail = new Polygon();
            tail.addPoint(3, 9);
            tail.addPoint(1, 12);
            tail.addPoint(6, 9);
            g2.drawPolygon(tail);
            g2.drawLine(4, 5, 8, 5);
        });

        public static final Icon DUPLICATE = new VectorIcon((g2, c) -> {
            Color fg = iconFg(c);
            g2.setColor(fg);
            g2.setStroke(new BasicStroke(1.2f));
            g2.drawRect(5, 1, 7, 8);
            g2.setColor(c != null ? c.getBackground() : Color.WHITE);
            g2.fillRect(2, 4, 7, 9);
            g2.setColor(fg);
            g2.drawRect(2, 4, 7, 9);
        });

        public static final Icon UP = new VectorIcon((g2, c) -> {
            Color fg = iconFg(c);
            g2.setColor(fg);
            g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.drawLine(7, 11, 7, 3);
            g2.drawLine(3, 7, 7, 3);
            g2.drawLine(11, 7, 7, 3);
        });

        public static final Icon DOWN = new VectorIcon((g2, c) -> {
            Color fg = iconFg(c);
            g2.setColor(fg);
            g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.drawLine(7, 3, 7, 11);
            g2.drawLine(3, 7, 7, 11);
            g2.drawLine(11, 7, 7, 11);
        });

        public static final Icon WRAP = new VectorIcon((g2, c) -> {
            Color fg = iconFg(c);
            g2.setColor(fg);
            g2.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.drawLine(2, 4, 9, 4);
            g2.drawArc(6, 4, 6, 6, 90, -180);
            g2.drawLine(9, 10, 4, 10);
            g2.drawLine(6, 8, 3, 10);
            g2.drawLine(6, 12, 3, 10);
        });

        public static final Icon LINE_NUMBERS = new VectorIcon((g2, c) -> {
            Color fg = iconFg(c);
            g2.setColor(fg);
            g2.setStroke(new BasicStroke(1.2f));
            g2.drawLine(5, 1, 5, 13);
            g2.drawLine(7, 3, 12, 3);
            g2.drawLine(7, 7, 12, 7);
            g2.drawLine(7, 11, 12, 11);
            g2.fillRect(2, 3, 2, 2);
            g2.fillRect(2, 7, 2, 2);
            g2.fillRect(2, 11, 2, 2);
        });

        public static final Icon TERMINAL = new VectorIcon((g2, c) -> {
            Color fg = iconFg(c);
            g2.setColor(fg);
            g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.drawLine(3, 3, 7, 7);
            g2.drawLine(7, 7, 3, 11);
            g2.drawLine(9, 11, 13, 11);
        });

        public static final Icon ZOOM_IN = new VectorIcon((g2, c) -> {
            Color fg = iconFg(c);
            g2.setColor(fg);
            g2.setStroke(new BasicStroke(1.2f));
            g2.drawOval(1, 1, 8, 8);
            g2.drawLine(3, 5, 7, 5);
            g2.drawLine(5, 3, 5, 7);
            g2.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.drawLine(8, 8, 12, 12);
        });

        public static final Icon ZOOM_OUT = new VectorIcon((g2, c) -> {
            Color fg = iconFg(c);
            g2.setColor(fg);
            g2.setStroke(new BasicStroke(1.2f));
            g2.drawOval(1, 1, 8, 8);
            g2.drawLine(3, 5, 7, 5);
            g2.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.drawLine(8, 8, 12, 12);
        });

        public static final Icon RESET = new VectorIcon((g2, c) -> {
            Color fg = iconFg(c);
            g2.setColor(fg);
            g2.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.drawArc(2, 2, 9, 9, 45, 270);
            g2.drawLine(7, 1, 9, 3);
            g2.drawLine(11, 1, 9, 3);
        });

        public static final Icon THEME = new VectorIcon((g2, c) -> {
            Color fg = iconFg(c);
            g2.setColor(fg);
            g2.setStroke(new BasicStroke(1.2f));
            g2.drawOval(1, 1, 12, 12);
            g2.fillOval(4, 3, 2, 2);
            g2.fillOval(8, 4, 2, 2);
            g2.fillOval(3, 7, 2, 2);
            g2.fillOval(7, 9, 2, 2);
        });

        public static final Icon CLOSE = new VectorIcon((g2, c) -> {
            Color fg = iconFg(c);
            g2.setColor(fg);
            g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.drawLine(3, 3, 11, 11);
            g2.drawLine(11, 3, 3, 11);
        });

        public static final Icon CLEAR = new VectorIcon((g2, c) -> {
            Color fg = iconFg(c);
            g2.setColor(fg);
            g2.setStroke(new BasicStroke(1.2f));
            g2.drawLine(2, 4, 12, 4);
            g2.drawRect(5, 2, 4, 2);
            Polygon bin = new Polygon();
            bin.addPoint(3, 4);
            bin.addPoint(4, 12);
            bin.addPoint(10, 12);
            bin.addPoint(11, 4);
            g2.drawPolygon(bin);
            g2.drawLine(6, 6, 6, 10);
            g2.drawLine(8, 6, 8, 10);
        });

        public static final Icon SEND = new VectorIcon((g2, c) -> {
            Color fg = iconFg(c);
            g2.setColor(fg);
            g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.drawLine(2, 7, 11, 7);
            g2.drawLine(7, 3, 11, 7);
            g2.drawLine(7, 11, 11, 7);
        });

        public static final Icon CHECK = new VectorIcon((g2, c) -> {
            g2.setColor(new Color(0x22C55E));
            g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.drawLine(2, 7, 5, 11);
            g2.drawLine(5, 11, 12, 3);
        });

        public static final Icon USER = new VectorIcon((g2, c) -> {
            Color fg = iconFg(c);
            g2.setColor(fg);
            g2.setStroke(new BasicStroke(1.2f));
            g2.drawOval(5, 2, 4, 4);
            g2.drawArc(2, 7, 10, 8, 0, 180);
        });

        public static final Icon KEY = new VectorIcon((g2, c) -> {
            Color fg = iconFg(c);
            g2.setColor(fg);
            g2.setStroke(new BasicStroke(1.2f));
            g2.drawOval(2, 4, 5, 5);
            g2.drawLine(7, 7, 12, 7);
            g2.drawLine(10, 7, 10, 9);
            g2.drawLine(12, 7, 12, 10);
        });

        public static final Icon SIGN_OUT = new VectorIcon((g2, c) -> {
            Color fg = iconFg(c);
            g2.setColor(fg);
            g2.setStroke(new BasicStroke(1.2f));
            g2.drawRect(2, 2, 5, 10);
            g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.drawLine(6, 7, 12, 7);
            g2.drawLine(9, 4, 12, 7);
            g2.drawLine(9, 10, 12, 7);
        });

        public static final Icon IMAGE = new VectorIcon((g2, c) -> {
            Color fg = iconFg(c);
            g2.setColor(fg);
            g2.setStroke(new BasicStroke(1.2f));
            g2.drawRect(1, 2, 12, 10);
            g2.fillOval(3, 4, 2, 2);
            Polygon m = new Polygon();
            m.addPoint(2, 11);
            m.addPoint(6, 6);
            m.addPoint(9, 9);
            m.addPoint(12, 11);
            g2.drawPolygon(m);
        });

        public static final Icon UNDO = new VectorIcon((g2, c) -> {
            Color fg = iconFg(c);
            g2.setColor(fg);
            g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.drawArc(4, 4, 8, 8, 90, 180);
            g2.drawLine(2, 4, 5, 4);
            g2.drawLine(5, 1, 2, 4);
            g2.drawLine(5, 7, 2, 4);
        });

        public static final Icon REDO = new VectorIcon((g2, c) -> {
            Color fg = iconFg(c);
            g2.setColor(fg);
            g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.drawArc(2, 4, 8, 8, 90, -180);
            g2.drawLine(9, 4, 12, 4);
            g2.drawLine(9, 1, 12, 4);
            g2.drawLine(9, 7, 12, 4);
        });

        public static final Icon CUT = new VectorIcon((g2, c) -> {
            Color fg = iconFg(c);
            g2.setColor(fg);
            g2.setStroke(new BasicStroke(1.2f));
            g2.drawOval(2, 8, 3, 3);
            g2.drawOval(9, 8, 3, 3);
            g2.drawLine(4, 8, 10, 3);
            g2.drawLine(10, 8, 4, 3);
        });

        public static final Icon COPY = DUPLICATE;

        public static final Icon PASTE = new VectorIcon((g2, c) -> {
            Color fg = iconFg(c);
            g2.setColor(fg);
            g2.setStroke(new BasicStroke(1.2f));
            g2.drawRect(3, 3, 8, 10);
            g2.drawRect(5, 1, 4, 3);
            g2.drawLine(5, 7, 9, 7);
            g2.drawLine(5, 9, 9, 9);
        });

        public static Icon forLanguage(String lang) {
            if (lang == null) return NEW;
            switch (lang) {
                case "Java":
                    return new VectorIcon((g2, c) -> {
                        g2.setColor(new Color(0xE76F51));
                        g2.setStroke(new BasicStroke(1.3f));
                        g2.drawRoundRect(2, 5, 7, 7, 3, 3);
                        g2.drawArc(7, 6, 4, 5, -90, 180);
                        g2.drawLine(1, 13, 10, 13);
                        g2.drawLine(4, 2, 4, 3);
                        g2.drawLine(7, 1, 7, 3);
                    });
                case "Python":
                    return new VectorIcon((g2, c) -> {
                        g2.setColor(new Color(0x3B82F6));
                        g2.fillRoundRect(2, 2, 6, 5, 2, 2);
                        g2.setColor(new Color(0xEAB308));
                        g2.fillRoundRect(6, 7, 6, 5, 2, 2);
                        g2.setColor(Color.WHITE);
                        g2.fillOval(4, 3, 2, 2);
                        g2.fillOval(8, 9, 2, 2);
                    });
                case "JavaScript":
                    return new VectorIcon((g2, c) -> {
                        g2.setColor(new Color(0xF7DF1E));
                        g2.fillRoundRect(1, 1, 12, 12, 3, 3);
                        g2.setColor(Color.BLACK);
                        g2.setFont(new Font("SansSerif", Font.BOLD, 8));
                        g2.drawString("JS", 2, 10);
                    });
                case "TypeScript":
                    return new VectorIcon((g2, c) -> {
                        g2.setColor(new Color(0x3178C6));
                        g2.fillRoundRect(1, 1, 12, 12, 3, 3);
                        g2.setColor(Color.WHITE);
                        g2.setFont(new Font("SansSerif", Font.BOLD, 8));
                        g2.drawString("TS", 2, 10);
                    });
                case "C":
                case "C/C++":
                case "C++":
                    return new VectorIcon((g2, c) -> {
                        g2.setColor(new Color(0x659AD2));
                        g2.fillRoundRect(1, 1, 12, 12, 3, 3);
                        g2.setColor(Color.WHITE);
                        g2.setFont(new Font("SansSerif", Font.BOLD, 8));
                        g2.drawString("C", 4, 10);
                    });
                case "C#":
                    return new VectorIcon((g2, c) -> {
                        g2.setColor(new Color(0x9B4F96));
                        g2.fillRoundRect(1, 1, 12, 12, 3, 3);
                        g2.setColor(Color.WHITE);
                        g2.setFont(new Font("SansSerif", Font.BOLD, 8));
                        g2.drawString("C#", 1, 10);
                    });
                case "HTML":
                case "HTML/XML":
                    return new VectorIcon((g2, c) -> {
                        g2.setColor(new Color(0xE44D26));
                        g2.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                        g2.drawLine(4, 4, 1, 7);
                        g2.drawLine(1, 7, 4, 10);
                        g2.drawLine(9, 4, 12, 7);
                        g2.drawLine(12, 7, 9, 10);
                        g2.drawLine(8, 3, 5, 11);
                    });
                case "CSS":
                    return new VectorIcon((g2, c) -> {
                        g2.setColor(new Color(0x264DE4));
                        g2.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                        g2.drawLine(2, 5, 12, 5);
                        g2.drawLine(2, 9, 12, 9);
                        g2.drawLine(5, 2, 4, 12);
                        g2.drawLine(9, 2, 8, 12);
                    });
                case "SQL":
                    return new VectorIcon((g2, c) -> {
                        g2.setColor(new Color(0x0284C7));
                        g2.setStroke(new BasicStroke(1.2f));
                        g2.drawOval(2, 2, 10, 4);
                        g2.drawLine(2, 4, 2, 10);
                        g2.drawLine(12, 4, 12, 10);
                        g2.drawArc(2, 5, 10, 4, 0, -180);
                        g2.drawArc(2, 8, 10, 4, 0, -180);
                    });
                case "JSON":
                    return new VectorIcon((g2, c) -> {
                        g2.setColor(new Color(0x10B981));
                        g2.setFont(new Font("SansSerif", Font.BOLD, 9));
                        g2.drawString("{}", 2, 10);
                    });
                case "Markdown":
                    return new VectorIcon((g2, c) -> {
                        g2.setColor(new Color(0x6366F1));
                        g2.drawRoundRect(1, 2, 12, 10, 2, 2);
                        g2.setFont(new Font("SansSerif", Font.BOLD, 7));
                        g2.drawString("M", 3, 10);
                        g2.setStroke(new BasicStroke(1.2f));
                        g2.drawLine(10, 5, 10, 9);
                        g2.drawLine(9, 8, 10, 10);
                        g2.drawLine(11, 8, 10, 10);
                    });
                default:
                    return NEW;
            }
        }

        public static Icon forTheme(String themeName) {
            Color primary, accent;
            if (themeName == null) themeName = "Light";
            switch (themeName) {
                case "Light":
                    primary = new Color(0xFFFFFF); accent = new Color(0x007ACC); break;
                case "Dark":
                    primary = new Color(0x1E1E1E); accent = new Color(0x007ACC); break;
                case "Ocean":
                    primary = new Color(0x0A192F); accent = new Color(0x38BDF8); break;
                case "Sunset":
                    primary = new Color(0x2D1515); accent = new Color(0xF97316); break;
                case "Forest":
                    primary = new Color(0x0F201B); accent = new Color(0x10B981); break;
                case "Dracula":
                    primary = new Color(0x282A36); accent = new Color(0xBD93F9); break;
                case "Nord":
                    primary = new Color(0x2E3440); accent = new Color(0x88C0D0); break;
                case "Monokai":
                    primary = new Color(0x272822); accent = new Color(0xE6DB74); break;
                case "Cyberpunk":
                    primary = new Color(0x181825); accent = new Color(0x06B6D4); break;
                case "Solarized Light":
                    primary = new Color(0xFDF6E3); accent = new Color(0x268BD2); break;
                case "Solarized Dark":
                    primary = new Color(0x002B36); accent = new Color(0x2AA198); break;
                case "Rosé Pine":
                    primary = new Color(0x191724); accent = new Color(0xEB6F92); break;
                default:
                    primary = new Color(0x1E1E1E); accent = new Color(0x007ACC); break;
            }
            final Color p = primary, a = accent;
            return new VectorIcon((g2, c) -> {
                g2.setColor(a);
                g2.fillOval(1, 1, 12, 12);
                g2.setColor(p);
                g2.fillOval(3, 3, 8, 8);
                g2.setColor(a);
                g2.fillArc(3, 3, 8, 8, 45, 180);
            });
        }
    }

    /* =======================================================================
     * 6C. IDE BUTTON & TOGGLE BUTTON (HIGH-CONTRAST HOVER / DRAG)
     * ======================================================================= */

    static class IDEButton extends JButton {
        private final Supplier<Theme> themeSupplier;
        private boolean isHovered = false;
        private boolean isPressed = false;
        private Color customAccent = null;

        IDEButton(String text, Supplier<Theme> themeSupplier) {
            super(text);
            this.themeSupplier = themeSupplier;
            setFocusable(false);
            setOpaque(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setFont(uiFont(Font.PLAIN, 12));
            setBorder(new EmptyBorder(5, 10, 5, 10));

            MouseAdapter ma = new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { isHovered = true; repaint(); }
                @Override public void mouseExited(MouseEvent e)  { isHovered = false; isPressed = false; repaint(); }
                @Override public void mousePressed(MouseEvent e) { isPressed = true; repaint(); }
                @Override public void mouseReleased(MouseEvent e) { isPressed = false; repaint(); }
                @Override public void mouseDragged(MouseEvent e) {
                    boolean inside = contains(e.getPoint());
                    if (inside != isHovered) { isHovered = inside; repaint(); }
                }
                @Override public void mouseMoved(MouseEvent e) {
                    if (!isHovered) { isHovered = true; repaint(); }
                }
            };
            addMouseListener(ma);
            addMouseMotionListener(ma);
        }

        IDEButton(Icon icon, String text, Supplier<Theme> themeSupplier) {
            this(text, themeSupplier);
            if (icon != null) {
                setIcon(icon);
                setIconTextGap(6);
            }
        }

        void setCustomAccent(Color c) { this.customAccent = c; repaint(); }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
            Theme t = themeSupplier.get();
            int w = getWidth(), h = getHeight();

            Color accentCol = customAccent != null ? customAccent : (t != null ? t.accent : new Color(0x007ACC));
            Color bgBase = t != null ? t.panelBg : new Color(0x252526);

            if (isPressed) {
                g2.setColor(mix(bgBase, accentCol, 0.40f));
                g2.fillRoundRect(2, 2, w - 4, h - 4, 6, 6);
                g2.setColor(accentCol);
                g2.drawRoundRect(2, 2, w - 5, h - 5, 6, 6);
            } else if (isHovered) {
                g2.setColor(mix(bgBase, accentCol, 0.24f));
                g2.fillRoundRect(2, 2, w - 4, h - 4, 6, 6);
                g2.setColor(mix(t != null ? t.border : Color.GRAY, accentCol, 0.75f));
                g2.drawRoundRect(2, 2, w - 5, h - 5, 6, 6);
            } else if (customAccent != null) {
                g2.setColor(mix(bgBase, accentCol, 0.16f));
                g2.fillRoundRect(2, 2, w - 4, h - 4, 6, 6);
                g2.setColor(mix(t != null ? t.border : Color.GRAY, accentCol, 0.45f));
                g2.drawRoundRect(2, 2, w - 5, h - 5, 6, 6);
            }

            g2.dispose();
            super.paintComponent(g);
        }
    }

    static class IDEToggleButton extends JToggleButton {
        private final Supplier<Theme> themeSupplier;
        private boolean isHovered = false;

        IDEToggleButton(String text, Supplier<Theme> themeSupplier) {
            super(text);
            this.themeSupplier = themeSupplier;
            setFocusable(false);
            setOpaque(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setFont(uiFont(Font.PLAIN, 12));
            setBorder(new EmptyBorder(5, 10, 5, 10));

            MouseAdapter ma = new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { isHovered = true; repaint(); }
                @Override public void mouseExited(MouseEvent e)  { isHovered = false; repaint(); }
                @Override public void mouseDragged(MouseEvent e) {
                    boolean inside = contains(e.getPoint());
                    if (inside != isHovered) { isHovered = inside; repaint(); }
                }
                @Override public void mouseMoved(MouseEvent e) {
                    if (!isHovered) { isHovered = true; repaint(); }
                }
            };
            addMouseListener(ma);
            addMouseMotionListener(ma);
        }

        IDEToggleButton(Icon icon, String text, Supplier<Theme> themeSupplier) {
            this(text, themeSupplier);
            if (icon != null) {
                setIcon(icon);
                setIconTextGap(6);
            }
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
            Theme t = themeSupplier.get();
            int w = getWidth(), h = getHeight();

            Color accentCol = t != null ? t.accent : new Color(0x007ACC);
            Color bgBase = t != null ? t.panelBg : new Color(0x252526);

            if (isSelected()) {
                g2.setColor(mix(bgBase, accentCol, isHovered ? 0.38f : 0.28f));
                g2.fillRoundRect(2, 2, w - 4, h - 4, 6, 6);
                g2.setColor(accentCol);
                g2.drawRoundRect(2, 2, w - 5, h - 5, 6, 6);
            } else if (isHovered) {
                g2.setColor(mix(bgBase, accentCol, 0.20f));
                g2.fillRoundRect(2, 2, w - 4, h - 4, 6, 6);
                g2.setColor(mix(t != null ? t.border : Color.GRAY, accentCol, 0.65f));
                g2.drawRoundRect(2, 2, w - 5, h - 5, 6, 6);
            }

            g2.dispose();
            super.paintComponent(g);
        }
    }

    public static Icon getLanguageIcon(String lang) {
        return IDEIcons.forLanguage(lang);
    }

    public static Icon getThemeIcon(String themeName) {
        return IDEIcons.forTheme(themeName);
    }

    static class IDEComboBox<T> extends JComboBox<T> {
        private final Supplier<Theme> themeSupplier;
        private boolean isHovered = false;

        IDEComboBox(T[] items, Supplier<Theme> themeSupplier, Function<T, Icon> iconMapper) {
            super(items);
            this.themeSupplier = themeSupplier;
            setOpaque(false);
            setFocusable(false);
            setFont(uiFont(Font.PLAIN, 12));
            setRenderer(new IDEComboRenderer<>(themeSupplier, iconMapper));
            setUI(new IDEComboBoxUI(themeSupplier));

            MouseAdapter ma = new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { isHovered = true; repaint(); }
                @Override public void mouseExited(MouseEvent e)  { isHovered = false; repaint(); }
                @Override public void mouseMoved(MouseEvent e)   { if (!isHovered) { isHovered = true; repaint(); } }
                @Override public void mouseDragged(MouseEvent e) {
                    boolean inside = contains(e.getPoint());
                    if (inside != isHovered) { isHovered = inside; repaint(); }
                }
            };
            addMouseListener(ma);
            addMouseMotionListener(ma);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Theme t = themeSupplier.get();
            boolean isDark = t != null && t.dark;
            int w = getWidth(), h = getHeight();

            Color bg = isDark ? (t != null ? t.bg : new Color(0x1E1E1E)) : Color.WHITE;
            Color border = isHovered 
                    ? (t != null ? t.accent : new Color(0x007ACC)) 
                    : (t != null ? t.border : Color.LIGHT_GRAY);

            g2.setColor(bg);
            g2.fillRoundRect(1, 1, w - 2, h - 2, 6, 6);
            g2.setColor(border);
            g2.drawRoundRect(1, 1, w - 3, h - 3, 6, 6);
            g2.dispose();

            super.paintComponent(g);
        }
    }

    static class IDEComboBoxUI extends BasicComboBoxUI {
        private final Supplier<Theme> themeSupplier;

        IDEComboBoxUI(Supplier<Theme> themeSupplier) {
            this.themeSupplier = themeSupplier;
        }

        @Override
        protected JButton createArrowButton() {
            JButton b = new JButton() {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    Theme t = themeSupplier.get();
                    boolean isDark = t != null && t.dark;
                    g2.setColor(isDark ? Color.WHITE : Color.BLACK);
                    int w = getWidth(), h = getHeight();
                    int cx = w / 2 - 1, cy = h / 2;
                    int[] x = {cx - 4, cx, cx + 4};
                    int[] y = {cy - 2, cy + 2, cy - 2};
                    g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g2.drawPolyline(x, y, 3);
                    g2.dispose();
                }
            };
            b.setName("ComboBox.arrowButton");
            b.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 6));
            b.setOpaque(false);
            b.setContentAreaFilled(false);
            b.setFocusable(false);
            return b;
        }

        @Override
        public void paintCurrentValueBackground(Graphics g, Rectangle bounds, boolean hasFocus) {
            // Let the rounded background of IDEComboBox show through cleanly
        }

        @Override
        public void paintCurrentValue(Graphics g, Rectangle bounds, boolean hasFocus) {
            ListCellRenderer<Object> renderer = comboBox.getRenderer();
            Component c = renderer.getListCellRendererComponent(listBox, comboBox.getSelectedItem(), -1, false, false);
            c.setFont(comboBox.getFont());
            Theme t = themeSupplier.get();
            boolean isDark = t != null && t.dark;
            c.setForeground(isDark ? Color.WHITE : Color.BLACK);
            c.setBackground(isDark ? (t != null ? t.bg : new Color(0x1E1E1E)) : Color.WHITE);
            if (c instanceof JComponent) {
                ((JComponent) c).setOpaque(false);
            }
            int pad = 6;
            Rectangle r = new Rectangle(bounds.x + pad, bounds.y, Math.max(0, bounds.width - pad), bounds.height);
            currentValuePane.paintComponent(g, c, comboBox, r.x, r.y, r.width, r.height, true);
        }

        @Override
        protected ComboPopup createPopup() {
            BasicComboPopup popup = new BasicComboPopup(comboBox) {
                @Override
                protected void configureList() {
                    super.configureList();
                    Theme t = themeSupplier.get();
                    boolean isDark = t != null && t.dark;
                    list.setBackground(isDark ? (t != null ? t.panelBg : new Color(0x252526)) : Color.WHITE);
                    list.setForeground(isDark ? Color.WHITE : Color.BLACK);
                    list.setSelectionBackground(t != null ? t.accentSoft : new Color(0x3B82F6));
                    list.setSelectionForeground(isDark ? Color.WHITE : Color.BLACK);
                }

                @Override
                protected void configurePopup() {
                    super.configurePopup();
                    Theme t = themeSupplier.get();
                    boolean isDark = t != null && t.dark;
                    setBackground(isDark ? (t != null ? t.panelBg : new Color(0x252526)) : Color.WHITE);
                    setBorder(BorderFactory.createCompoundBorder(
                            BorderFactory.createLineBorder(t != null ? t.border : Color.GRAY, 1),
                            BorderFactory.createEmptyBorder(2, 0, 2, 0)));
                }

                @Override
                public void show(Component invoker, int x, int y) {
                    Theme t = themeSupplier.get();
                    boolean isDark = t != null && t.dark;
                    Color listBg = isDark ? (t != null ? t.panelBg : new Color(0x252526)) : Color.WHITE;
                    Color listFg = isDark ? Color.WHITE : Color.BLACK;
                    list.setBackground(listBg);
                    list.setForeground(listFg);
                    list.setSelectionBackground(t != null ? t.accentSoft : new Color(0x3B82F6));
                    list.setSelectionForeground(isDark ? Color.WHITE : Color.BLACK);
                    setBackground(listBg);
                    setBorder(BorderFactory.createCompoundBorder(
                            BorderFactory.createLineBorder(t != null ? t.border : Color.GRAY, 1),
                            BorderFactory.createEmptyBorder(2, 0, 2, 0)));
                    super.show(invoker, x, y);
                }
            };
            return popup;
        }
    }

    static class IDEComboRenderer<T> extends DefaultListCellRenderer {
        private final Supplier<Theme> themeSupplier;
        private final Function<T, Icon> iconMapper;

        IDEComboRenderer(Supplier<Theme> themeSupplier, Function<T, Icon> iconMapper) {
            this.themeSupplier = themeSupplier;
            this.iconMapper = iconMapper;
            setOpaque(true);
            setBorder(new EmptyBorder(5, 8, 5, 8));
        }

        @Override
        @SuppressWarnings("unchecked")
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            Theme t = themeSupplier.get();
            boolean isDark = t != null && t.dark;

            String raw = (value != null) ? value.toString() : "";
            setText(raw);
            if (iconMapper != null && value != null) {
                Icon icon = iconMapper.apply((T) value);
                setIcon(icon);
                setIconTextGap(8);
            } else {
                setIcon(null);
            }

            setFont(uiFont(Font.PLAIN, 12));

            if (isSelected) {
                Color selBg = t != null ? t.accentSoft : new Color(0x007ACC);
                setBackground(selBg);
                setForeground(isDark ? Color.WHITE : Color.BLACK);
            } else {
                Color itemBg = isDark ? (t != null ? t.panelBg : new Color(0x252526)) : Color.WHITE;
                Color itemFg = isDark ? Color.WHITE : Color.BLACK;
                setBackground(itemBg);
                setForeground(itemFg);
            }
            return this;
        }
    }

    /* =======================================================================
     * 6C. INTEGRATED CODE RUNNER & CONSOLE PANEL
     * ======================================================================= */

    static class ConsolePanel extends JPanel {
        private final LuminaryIDE ide;
        private final JLabel titleLabel, statusLabel;
        private final IDEButton runBtn, stopBtn, clearBtn, closeBtn;
        private final JTextPane outputArea;
        private final JTextField inputField;
        private final IDEButton sendBtn;
        private final JScrollPane scrollPane;
        private Process currentProcess = null;
        private BufferedWriter processStdin = null;
        private long startTime = 0;

        ConsolePanel(LuminaryIDE ide) {
            super(new BorderLayout());
            this.ide = ide;
            setPreferredSize(new Dimension(800, 220));

            // Header bar
            JPanel header = new JPanel(new BorderLayout(8, 0));
            header.setBorder(new EmptyBorder(4, 10, 4, 8));

            JPanel leftHeader = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
            leftHeader.setOpaque(false);
            titleLabel = new JLabel("TERMINAL / OUTPUT");
            titleLabel.setIcon(IDEIcons.TERMINAL);
            titleLabel.setIconTextGap(6);
            titleLabel.setFont(uiFont(Font.BOLD, 11));

            statusLabel = new JLabel("Ready");
            statusLabel.setIcon(IDEIcons.CHECK);
            statusLabel.setIconTextGap(4);
            statusLabel.setFont(uiFont(Font.PLAIN, 11));

            leftHeader.add(titleLabel);
            leftHeader.add(new JSeparator(SwingConstants.VERTICAL));
            leftHeader.add(statusLabel);

            JPanel rightHeader = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
            rightHeader.setOpaque(false);

            runBtn = new IDEButton(IDEIcons.RUN, "Run", () -> ide.theme);
            runBtn.setCustomAccent(new Color(0x22C55E)); // Emerald
            runBtn.setToolTipText("Run Current Code (F5 / Ctrl+R)");
            runBtn.addActionListener(e -> executeCurrentBuffer());

            stopBtn = new IDEButton(IDEIcons.STOP, "Stop", () -> ide.theme);
            stopBtn.setCustomAccent(new Color(0xEF4444)); // Crimson
            stopBtn.setToolTipText("Stop Running Process (Shift+F5)");
            stopBtn.setEnabled(false);
            stopBtn.addActionListener(e -> stopProcess());

            clearBtn = new IDEButton(IDEIcons.CLEAR, "Clear", () -> ide.theme);
            clearBtn.setToolTipText("Clear Output (Ctrl+L)");
            clearBtn.addActionListener(e -> clearOutput());

            closeBtn = new IDEButton(IDEIcons.CLOSE, "Close", () -> ide.theme);
            closeBtn.setToolTipText("Hide Terminal (Ctrl+`)");
            closeBtn.addActionListener(e -> ide.toggleTerminalDrawer());

            rightHeader.add(runBtn);
            rightHeader.add(stopBtn);
            rightHeader.add(clearBtn);
            rightHeader.add(closeBtn);

            header.add(leftHeader, BorderLayout.WEST);
            header.add(rightHeader, BorderLayout.EAST);

            // Output text area
            outputArea = new JTextPane();
            outputArea.setEditable(false);
            outputArea.setFont(monoFont(Font.PLAIN, 12));
            scrollPane = new JScrollPane(outputArea,
                    JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                    JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
            scrollPane.setBorder(BorderFactory.createEmptyBorder());

            // Stdin input field at bottom
            JPanel inputBar = new JPanel(new BorderLayout(6, 0));
            inputBar.setBorder(new EmptyBorder(4, 8, 4, 8));
            JLabel prompt = new JLabel("> ");
            prompt.setFont(monoFont(Font.BOLD, 13));
            inputField = new JTextField();
            inputField.setFont(monoFont(Font.PLAIN, 12));
            inputField.addActionListener(e -> sendInput());
            sendBtn = new IDEButton(IDEIcons.SEND, "Send", () -> ide.theme);
            sendBtn.setToolTipText("Send input line to running process");
            sendBtn.addActionListener(e -> sendInput());

            inputBar.add(prompt, BorderLayout.WEST);
            inputBar.add(inputField, BorderLayout.CENTER);
            inputBar.add(sendBtn, BorderLayout.EAST);

            add(header, BorderLayout.NORTH);
            add(scrollPane, BorderLayout.CENTER);
            add(inputBar, BorderLayout.SOUTH);

            applyTheme(ide.theme);
        }

        void applyTheme(Theme t) {
            if (t == null) return;
            setBackground(t.panelBg);
            setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, t.border));

            titleLabel.setForeground(t.accent);
            statusLabel.setForeground(t.textMuted);

            outputArea.setBackground(t.bg);
            outputArea.setForeground(t.fg);
            outputArea.setCaretColor(t.accent);

            scrollPane.setBackground(t.bg);
            scrollPane.getViewport().setBackground(t.bg);

            inputField.setBackground(t.bg);
            inputField.setForeground(t.fg);
            inputField.setCaretColor(t.accent);
            inputField.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(t.border),
                    new EmptyBorder(3, 6, 3, 6)));

            runBtn.setForeground(t.fg);
            stopBtn.setForeground(t.fg);
            clearBtn.setForeground(t.fg);
            closeBtn.setForeground(t.fg);
            sendBtn.setForeground(t.fg);

            repaint();
        }

        void appendText(String text, Color color) {
            SwingUtilities.invokeLater(() -> {
                StyledDocument doc = outputArea.getStyledDocument();
                SimpleAttributeSet set = new SimpleAttributeSet();
                StyleConstants.setForeground(set, color != null ? color : ide.theme.fg);
                StyleConstants.setFontFamily(set, ide.fontFamily);
                StyleConstants.setFontSize(set, 12);
                try {
                    doc.insertString(doc.getLength(), text, set);
                    outputArea.setCaretPosition(doc.getLength());
                } catch (BadLocationException ignored) { }
            });
        }

        void clearOutput() {
            outputArea.setText("");
            statusLabel.setIcon(IDEIcons.CHECK);
            statusLabel.setText(" Ready");
        }

        void sendInput() {
            String text = inputField.getText();
            inputField.setText("");
            if (processStdin != null && currentProcess != null && currentProcess.isAlive()) {
                try {
                    appendText(text + "\n", ide.theme.accent);
                    processStdin.write(text + "\n");
                    processStdin.flush();
                } catch (IOException ex) {
                    appendText("[Stdin Error: " + ex.getMessage() + "]\n", ide.theme.keyword);
                }
            }
        }

        void stopProcess() {
            if (currentProcess != null && currentProcess.isAlive()) {
                currentProcess.destroyForcibly();
                appendText("\n[Process terminated by user]\n", ide.theme.keyword);
                statusLabel.setIcon(IDEIcons.STOP);
                statusLabel.setText(" Stopped");
                runBtn.setEnabled(true);
                stopBtn.setEnabled(false);
            }
        }

        void executeCurrentBuffer() {
            if (currentProcess != null && currentProcess.isAlive()) {
                stopProcess();
            }

            clearOutput();
            String lang = ide.getEffectiveLanguage();
            String code = ide.textArea.getText();
            if (code.trim().isEmpty()) {
                appendText("[Warning: Buffer is empty]\n", ide.theme.keyword);
                return;
            }

            statusLabel.setIcon(IDEIcons.RUN);
            statusLabel.setText(" Preparing " + lang + "...");
            runBtn.setEnabled(false);
            stopBtn.setEnabled(true);
            startTime = System.currentTimeMillis();

            Thread execThread = new Thread(() -> {
                try {
                    switch (lang) {
                        case "Java": executeJava(code); break;
                        case "Python": executePython(code); break;
                        case "JavaScript": case "JSON": executeNode(code); break;
                        default:
                            appendText("[Runner supports Java, Python, and JavaScript/Node.js. Current mode: " + lang + "]\n", ide.theme.accent);
                            finishExecution(-1);
                            break;
                    }
                } catch (Throwable t) {
                    appendText("\n[Execution Error: " + t.getMessage() + "]\n", ide.theme.keyword);
                    finishExecution(-1);
                }
            }, "luminary-runner");
            execThread.setDaemon(true);
            execThread.start();
        }

        private void executeJava(String code) throws Exception {
            File workDir;
            String className = "Main";
            File sourceFile;

            Matcher m = Pattern.compile("public\\s+class\\s+([A-Za-z0-9_]+)").matcher(code);
            if (m.find()) {
                className = m.group(1);
            }

            if (ide.currentFile != null && ide.currentFile.getName().endsWith(".java")) {
                ide.saveFile();
                sourceFile = ide.currentFile;
                workDir = sourceFile.getParentFile();
                className = sourceFile.getName().substring(0, sourceFile.getName().length() - 5);
            } else {
                File runDir = new File(System.getProperty("java.io.tmpdir"), "LuminaryRunner");
                runDir.mkdirs();
                sourceFile = new File(runDir, className + ".java");
                Files.writeString(sourceFile.toPath(), code, StandardCharsets.UTF_8);
                workDir = runDir;
            }

            appendText("[Compiling " + sourceFile.getName() + "...]\n", ide.theme.accent);
            statusLabel.setText("Compiling Java...");

            ProcessBuilder pbCompile = new ProcessBuilder("javac", "-encoding", "UTF-8", sourceFile.getName());
            pbCompile.directory(workDir);
            Process compileProcess = pbCompile.start();

            try (BufferedReader r = new BufferedReader(new InputStreamReader(compileProcess.getErrorStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) appendText(line + "\n", ide.theme.keyword);
            }
            int compileExit = compileProcess.waitFor();
            if (compileExit != 0) {
                appendText("\n[Compilation failed with exit code " + compileExit + "]\n", ide.theme.keyword);
                finishExecution(compileExit);
                return;
            }

            appendText("[Compilation successful. Running " + className + "...]\n\n", ide.theme.string);
            statusLabel.setText("Running Java...");

            ProcessBuilder pbRun = new ProcessBuilder("java", "-Dfile.encoding=UTF-8", className);
            pbRun.directory(workDir);
            startAndStreamProcess(pbRun);
        }

        private void executePython(String code) throws Exception {
            File runFile;
            if (ide.currentFile != null && (ide.currentFile.getName().endsWith(".py") || ide.currentFile.getName().endsWith(".pyw"))) {
                ide.saveFile();
                runFile = ide.currentFile;
            } else {
                File runDir = new File(System.getProperty("java.io.tmpdir"), "LuminaryRunner");
                runDir.mkdirs();
                runFile = new File(runDir, "script.py");
                Files.writeString(runFile.toPath(), code, StandardCharsets.UTF_8);
            }

            appendText("[Running Python: " + runFile.getName() + "...]\n\n", ide.theme.accent);
            statusLabel.setText("Running Python...");

            ProcessBuilder pb = new ProcessBuilder("python", "-u", runFile.getAbsolutePath());
            pb.directory(runFile.getParentFile());
            startAndStreamProcess(pb);
        }

        private void executeNode(String code) throws Exception {
            File runFile;
            if (ide.currentFile != null && (ide.currentFile.getName().endsWith(".js") || ide.currentFile.getName().endsWith(".mjs"))) {
                ide.saveFile();
                runFile = ide.currentFile;
            } else {
                File runDir = new File(System.getProperty("java.io.tmpdir"), "LuminaryRunner");
                runDir.mkdirs();
                runFile = new File(runDir, "index.js");
                Files.writeString(runFile.toPath(), code, StandardCharsets.UTF_8);
            }

            appendText("[Running Node.js: " + runFile.getName() + "...]\n\n", ide.theme.accent);
            statusLabel.setText("Running Node.js...");

            ProcessBuilder pb = new ProcessBuilder("node", runFile.getAbsolutePath());
            pb.directory(runFile.getParentFile());
            startAndStreamProcess(pb);
        }

        private void startAndStreamProcess(ProcessBuilder pb) throws Exception {
            currentProcess = pb.start();
            processStdin = new BufferedWriter(new OutputStreamWriter(currentProcess.getOutputStream(), StandardCharsets.UTF_8));

            Thread stdoutThread = new Thread(() -> {
                try (BufferedReader r = new BufferedReader(new InputStreamReader(currentProcess.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = r.readLine()) != null) appendText(line + "\n", ide.theme.fg);
                } catch (IOException ignored) { }
            }, "runner-stdout");
            stdoutThread.setDaemon(true);
            stdoutThread.start();

            Thread stderrThread = new Thread(() -> {
                try (BufferedReader r = new BufferedReader(new InputStreamReader(currentProcess.getErrorStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = r.readLine()) != null) appendText(line + "\n", ide.theme.keyword);
                } catch (IOException ignored) { }
            }, "runner-stderr");
            stderrThread.setDaemon(true);
            stderrThread.start();

            int exitCode = currentProcess.waitFor();
            stdoutThread.join(500);
            stderrThread.join(500);
            finishExecution(exitCode);
        }

        private void finishExecution(int exitCode) {
            long elapsedMs = System.currentTimeMillis() - startTime;
            double sec = elapsedMs / 1000.0;
            String msg = String.format("\n[Process finished with exit code %d (in %.2fs)]\n", exitCode, sec);
            appendText(msg, exitCode == 0 ? ide.theme.string : ide.theme.keyword);

            SwingUtilities.invokeLater(() -> {
                statusLabel.setIcon(exitCode == 0 ? IDEIcons.CHECK : IDEIcons.STOP);
                statusLabel.setText(" Exited (" + exitCode + ") in " + String.format("%.2fs", sec));
                runBtn.setEnabled(true);
                stopBtn.setEnabled(false);
                processStdin = null;
            });
        }
    }

    public void toggleTerminalDrawer() {
        if (consolePanel == null) return;
        boolean nowVisible = !consolePanel.isVisible();
        consolePanel.setVisible(nowVisible);
        if (tbTerminal != null) tbTerminal.setSelected(nowVisible);
        if (terminalItem != null) terminalItem.setSelected(nowVisible);
        if (editorSplitPane != null) {
            editorSplitPane.setDividerSize(nowVisible ? 6 : 0);
            if (nowVisible) {
                editorSplitPane.setDividerLocation(0.72);
            }
        }
        editorCard.revalidate();
        editorCard.repaint();
    }

    public void runCurrentCode() {
        if (consolePanel == null) return;
        if (!consolePanel.isVisible()) toggleTerminalDrawer();
        consolePanel.executeCurrentBuffer();
    }

    public void stopRunningCode() {
        if (consolePanel != null) {
            consolePanel.stopProcess();
        }
    }

    public void clearTerminalOutput() {
        if (consolePanel != null) {
            consolePanel.clearOutput();
        }
    }

    /* =======================================================================
     * 7. CONSTRUCTOR
     * ======================================================================= */

    public LuminaryIDE() {
        this(null);
    }

    public LuminaryIDE(File initialFile) {
        if (initialFile != null && initialFile.isFile()) this.pendingFile = initialFile;
        String themeName = prefs.get(PREF_THEME_NAME, "Dark");
        fontFamily = prefs.get(PREF_FONT, null);
        if (fontFamily == null || fontFamily.isEmpty()) { initFontFamilies(); fontFamily = MONO_FAMILY; }
        fontSize   = prefs.getInt(PREF_SIZE, 14);
        autoIndentEnabled   = prefs.getBoolean(PREF_AUTOINDENT, true);
        autoCloseEnabled    = prefs.getBoolean(PREF_AUTOCLOSE, true);
        bracketMatchEnabled = prefs.getBoolean(PREF_BRACKETMATCH, true);
        syntaxEnabled       = prefs.getBoolean(PREF_SYNTAX, true);
        rainbowBracketsEnabled = prefs.getBoolean(PREF_RAINBOW, true);
        currentLineEnabled  = prefs.getBoolean(PREF_CURLINE, true);
        tabSize             = prefs.getInt(PREF_TABSIZE, 4);
        soundEnabled        = prefs.getBoolean(PREF_SOUND, true);
        keyClickEnabled     = prefs.getBoolean(PREF_KEYCLICK, false);
        soundVolume         = prefs.getFloat(PREF_VOLUME, 0.35f);
        showWelcomeOnStartup = prefs.getBoolean(PREF_WELCOME, true);
        imageWarningShown   = prefs.getBoolean(PREF_IMGWARN, false);

        theme = THEMES.get(1);
        for (Theme t : THEMES) if (t.name.equals(themeName)) theme = t;

        sound = new SoundManager();
        sound.setEnabled(soundEnabled);
        sound.setVolume(soundVolume);

        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
        catch (Throwable ignored) { }
        installUIManagerDefaults(theme);

        frame = new JFrame();
        frame.setLayout(new BorderLayout());
        frame.setMinimumSize(new Dimension(800, 500));
        try { frame.setIconImages(getAppIcons()); } catch (Throwable ignored) { }

        JRootPane rp = frame.getRootPane();
        rp.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_N, CTRL), "welcomeNew");
        rp.getActionMap().put("welcomeNew", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { if (showingWelcome) newDocument(); }
        });
        rp.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_O, CTRL), "welcomeOpen");
        rp.getActionMap().put("welcomeOpen", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { if (showingWelcome) openFile(); }
        });
        rp.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_I, CTRL_SHIFT), "welcomeImage");
        rp.getActionMap().put("welcomeImage", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { if (showingWelcome) openImageViewer(); }
        });

        // Global execution & terminal shortcuts
        rp.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0), "runCodeAction");
        rp.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_R, CTRL), "runCodeAction");
        rp.getActionMap().put("runCodeAction", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { runCurrentCode(); }
        });

        rp.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_F5, InputEvent.SHIFT_DOWN_MASK), "stopCodeAction");
        rp.getActionMap().put("stopCodeAction", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { stopRunningCode(); }
        });

        rp.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_BACK_QUOTE, CTRL), "toggleTerminalAction");
        rp.getActionMap().put("toggleTerminalAction", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { toggleTerminalDrawer(); }
        });

        textArea = new JTextPane() {
            @Override protected void paintComponent(Graphics g) {
                if (currentLineEnabled && theme != null) {
                    try {
                        Rectangle2D r = modelToView2D(getCaretPosition());
                        if (r != null) {
                            g.setColor(theme.currentLine);
                            g.fillRect(0, (int) r.getY(), getWidth(), (int) r.getHeight());
                        }
                    } catch (BadLocationException ignored) { }
                }
                super.paintComponent(g);
            }

            @Override public boolean getScrollableTracksViewportWidth() {
                if (wrapEnabled) return true;
                Container parent = getParent();
                if (parent == null) return false;
                return getUI().getPreferredSize(this).width <= parent.getSize().width;
            }
        };
        textArea.setOpaque(false);
        textArea.setEditorKit(new StyledEditorKit());
        textArea.setFont(monoFont(Font.PLAIN, fontSize));
        applyTextPadding();

        undoManager = new UndoManager();
        undoManager.setLimit(500);
        textArea.getDocument().addUndoableEditListener(undoManager);
        textArea.getDocument().addDocumentListener(documentListener);

        textArea.addCaretListener(e -> {
            updateStatusBar();
            updateCurrentLineHighlight();
            if (bracketMatchEnabled) highlightMatchingBracket();
            if (lineNumberView != null) lineNumberView.repaint();
        });

        installSmartTyping();
        installDropTarget();

        lineNumberView = new LineNumberView(this, textArea);

        scrollPane = new JScrollPane(textArea,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setRowHeaderView(lineNumberView);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getVerticalScrollBar().setUnitIncrement(24);
        scrollPane.getHorizontalScrollBar().setUnitIncrement(24);
        installModernScrollBars(scrollPane);

        textArea.addPropertyChangeListener("font", evt -> {
            lineNumberView.setFont(textArea.getFont());
            lineNumberView.revalidate();
            lineNumberView.repaint();
            applyParagraphAttrs();
            updateFontSizeLabel();
        });

        buildStatusBar();
        buildMenuBar();
        buildToolBar();

        editorCard = new JPanel(new BorderLayout());
        consolePanel = new ConsolePanel(this);
        consolePanel.setVisible(false);

        editorSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, scrollPane, consolePanel);
        editorSplitPane.setResizeWeight(0.72);
        editorSplitPane.setDividerSize(0);
        editorSplitPane.setBorder(BorderFactory.createEmptyBorder());
        editorSplitPane.setContinuousLayout(true);

        editorCard.add(editorSplitPane, BorderLayout.CENTER);

        buildFindReplacePanel();

        welcomePanel = new WelcomePanel(this);
        fadeLayerUI  = new FadeLayerUI();
        welcomeLayer = new JLayer<>(welcomePanel, fadeLayerUI);

        loginPanel       = new LoginPanel(this);
        loginFadeLayerUI = new FadeLayerUI();
        loginLayer       = new JLayer<>(loginPanel, loginFadeLayerUI);

        cardLayout = new CardLayout();
        cardPanel  = new JPanel(cardLayout);
        cardPanel.add(loginLayer,   CARD_LOGIN);
        cardPanel.add(welcomeLayer, CARD_WELCOME);
        cardPanel.add(editorCard,   CARD_EDITOR);

        cardFadeLayerUI = new FadeLayerUI();
        cardLayer = new JLayer<JComponent>(cardPanel, cardFadeLayerUI);

        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(toolBar, BorderLayout.CENTER);

        frame.setJMenuBar(menuBar);
        frame.add(topPanel, BorderLayout.NORTH);
        frame.add(cardLayer, BorderLayout.CENTER);
        frame.add(statusBar, BorderLayout.SOUTH);

        restoreWindowState();

        frame.setTitle("Untitled - Luminary IDE");
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { closeWindow(); }
        });

        highlightTimer = new javax.swing.Timer(160, e -> highlightSyntax());
        highlightTimer.setRepeats(false);

        rebuildStyles();
        applyTheme(theme);
        applyParagraphAttrs();

        startAutoSaveTimer();

        setChromeVisible(false);

        frame.setVisible(true);
        menuBar.revalidate();
        menuBar.repaint();
        updateStatusBar();
        updateTitle();

        showingLogin   = true;
        showingWelcome = false;
        cardLayout.show(cardPanel, CARD_LOGIN);
        loginPanel.refresh();
        loginPanel.startFadeIn();
    }

    private void applyTextPadding() {
        boolean numsShown = (lineNumbersItem == null) || lineNumbersItem.isSelected();
        int left = numsShown ? TEXT_LEFT_PAD_NUMS : TEXT_LEFT_PAD_NO_NUMS;
        textArea.setBorder(new EmptyBorder(TEXT_TOP_PAD, left,
                TEXT_BOTTOM_PAD, TEXT_RIGHT_PAD));
        textArea.revalidate();
        textArea.repaint();
    }

    private void saveWindowState() {
        try {
            boolean max = (frame.getExtendedState() & Frame.MAXIMIZED_BOTH) == Frame.MAXIMIZED_BOTH;
            prefs.putBoolean(PREF_WINMAX, max);
            if (!max) {
                prefs.putInt(PREF_WINX, frame.getX());
                prefs.putInt(PREF_WINY, frame.getY());
                prefs.putInt(PREF_WINW, frame.getWidth());
                prefs.putInt(PREF_WINH, frame.getHeight());
            }
        } catch (Throwable ignored) { }
    }

    private void restoreWindowState() {
        try {
            int x = prefs.getInt(PREF_WINX, Integer.MIN_VALUE);
            int y = prefs.getInt(PREF_WINY, Integer.MIN_VALUE);
            int w = prefs.getInt(PREF_WINW, 1280);
            int h = prefs.getInt(PREF_WINH, 800);
            boolean max = prefs.getBoolean(PREF_WINMAX, false);
            if (w < 800) w = 800; if (h < 500) h = 500;
            frame.setSize(w, h);
            if (x != Integer.MIN_VALUE && y != Integer.MIN_VALUE) {
                Dimension scr = Toolkit.getDefaultToolkit().getScreenSize();
                if (x > -100 && y > -100 && x < scr.width - 100 && y < scr.height - 100)
                    frame.setLocation(x, y);
                else frame.setLocationRelativeTo(null);
            } else frame.setLocationRelativeTo(null);
            if (max) frame.setExtendedState(Frame.MAXIMIZED_BOTH);
        } catch (Throwable ignored) { frame.setSize(1280, 800); frame.setLocationRelativeTo(null); }
    }

    static List<Image> getAppIcons() {
        List<Image> icons = new ArrayList<>();
        int[] sizes = { 16, 24, 32, 48, 64, 128, 256 };
        Image base = null;
        for (String path : new String[]{ "assets/logo.png", "src/assets/logo.png" }) {
            File f = new File(path);
            if (f.exists() && f.isFile()) {
                try {
                    base = ImageIO.read(f);
                    if (base != null) break;
                } catch (Throwable ignored) { }
            }
        }
        if (base == null) {
            try {
                InputStream in = LuminaryIDE.class.getResourceAsStream("/assets/logo.png");
                if (in != null) {
                    base = ImageIO.read(in);
                    in.close();
                }
            } catch (Throwable ignored) { }
        }

        for (int sz : sizes) {
            if (base != null) {
                icons.add(base.getScaledInstance(sz, sz, Image.SCALE_SMOOTH));
            } else {
                icons.add(makeAppIcon(sz));
            }
        }
        return icons;
    }

    static Image makeAppIcon() {
        return makeAppIcon(64);
    }

    static Image makeAppIcon(int size) {
        // Try reading pre-rendered high-res asset first
        try {
            File f = new File("assets/icon-" + size + ".png");
            if (f.exists() && f.isFile()) {
                Image img = ImageIO.read(f);
                if (img != null) return img;
            }
        } catch (Throwable ignored) { }

        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            double s = size;
            double pad = s * 0.08;
            double boxSize = s - 2 * pad;
            double corner = boxSize * 0.24;

            g2.setPaint(new GradientPaint(
                    (float) pad, (float) pad, new Color(0x2563EB),
                    (float) (pad + boxSize), (float) (pad + boxSize), new Color(0x7C3AED)));
            g2.fill(new RoundRectangle2D.Double(pad, pad, boxSize, boxSize, corner, corner));

            double cx = s / 2.0, cy = s / 2.0;
            g2.setColor(new Color(255, 255, 255, 60));
            double glowR = boxSize * 0.35;
            g2.fill(new Ellipse2D.Double(cx - glowR, cy - glowR, glowR * 2, glowR * 2));

            AffineTransform tx = g2.getTransform();
            g2.rotate(Math.PI / 4, cx, cy);
            double diagR = boxSize * 0.22, diagIn = boxSize * 0.06;
            Path2D diag = new Path2D.Double();
            diag.moveTo(cx, cy - diagR); diag.lineTo(cx + diagIn, cy - diagIn);
            diag.lineTo(cx + diagR, cy); diag.lineTo(cx + diagIn, cy + diagIn);
            diag.lineTo(cx, cy + diagR); diag.lineTo(cx - diagIn, cy + diagIn);
            diag.lineTo(cx - diagR, cy); diag.lineTo(cx - diagIn, cy - diagIn);
            diag.closePath();
            g2.setColor(new Color(186, 230, 253, 190));
            g2.fill(diag);
            g2.setTransform(tx);

            double starR = boxSize * 0.35;
            Path2D mainStar = new Path2D.Double();
            mainStar.moveTo(cx, cy - starR);
            mainStar.quadTo(cx, cy, cx + starR, cy);
            mainStar.quadTo(cx, cy, cx, cy + starR);
            mainStar.quadTo(cx, cy, cx - starR, cy);
            mainStar.quadTo(cx, cy, cx, cy - starR);
            mainStar.closePath();
            g2.setPaint(new GradientPaint((float) cx, (float) (cy - starR), Color.WHITE,
                    (float) cx, (float) (cy + starR), new Color(199, 210, 254)));
            g2.fill(mainStar);

            double dotR = Math.max(1.0, s * 0.035);
            g2.setColor(Color.WHITE);
            g2.fill(new Ellipse2D.Double(cx - dotR, cy - dotR, dotR * 2, dotR * 2));
        } finally { g2.dispose(); }
        return img;
    }

    /* =======================================================================
     * 8. UI MANAGER DEFAULTS
     * ======================================================================= */

    private void installUIManagerDefaults(Theme t) {
        try {
            Color chrome = t.chromeBg, panel = t.panelBg, hover = t.hover, fg = t.fg;
            UIManager.put("MenuBar.background", chrome);
            UIManager.put("MenuBar.foreground", fg);
            UIManager.put("MenuBar.borderColor", t.border);
            UIManager.put("Menu.background", chrome);
            UIManager.put("Menu.foreground", fg);
            UIManager.put("Menu.selectionBackground", hover);
            UIManager.put("Menu.selectionForeground", fg);
            UIManager.put("MenuItem.background", panel);
            UIManager.put("MenuItem.foreground", fg);
            UIManager.put("MenuItem.selectionBackground", hover);
            UIManager.put("MenuItem.selectionForeground", fg);
            UIManager.put("MenuItem.acceleratorForeground", t.textMuted);
            UIManager.put("MenuItem.acceleratorSelectionForeground", t.textMuted);
            UIManager.put("CheckBoxMenuItem.background", panel);
            UIManager.put("CheckBoxMenuItem.foreground", fg);
            UIManager.put("CheckBoxMenuItem.selectionBackground", hover);
            UIManager.put("CheckBoxMenuItem.selectionForeground", fg);
            UIManager.put("RadioButtonMenuItem.background", panel);
            UIManager.put("RadioButtonMenuItem.foreground", fg);
            UIManager.put("RadioButtonMenuItem.selectionBackground", hover);
            UIManager.put("RadioButtonMenuItem.selectionForeground", fg);
            UIManager.put("PopupMenu.background", panel);
            UIManager.put("PopupMenu.foreground", fg);
            UIManager.put("Separator.background", panel);
            UIManager.put("Separator.foreground", t.border);
            UIManager.put("ToolTip.background", t.dark ? c(0x2B2B2B) : c(0xFFFFFF));
            UIManager.put("ToolTip.foreground", fg);
            UIManager.put("ToolTip.border", BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(t.border),
                    new EmptyBorder(4, 8, 4, 8)));
            UIManager.put("OptionPane.background", panel);
            UIManager.put("OptionPane.messageForeground", fg);
            UIManager.put("Panel.background", panel);
            UIManager.put("Label.foreground", fg);
            UIManager.put("Button.background", t.dark ? c(0x3A3A3A) : c(0xE8E8E8));
            UIManager.put("Button.foreground", fg);
            UIManager.put("CheckBox.background", panel);
            UIManager.put("CheckBox.foreground", fg);
            UIManager.put("RadioButton.background", panel);
            UIManager.put("RadioButton.foreground", fg);
            UIManager.put("TextField.background", t.bg);
            UIManager.put("TextField.foreground", fg);
            UIManager.put("TextField.caretForeground", t.accent);
            UIManager.put("TextField.selectionBackground", t.selection);
            UIManager.put("TextField.selectionForeground", t.selectionFg);
            UIManager.put("TextArea.background", t.bg);
            UIManager.put("TextArea.foreground", fg);
            boolean isDark = t.dark;
            Color comboText = isDark ? Color.WHITE : Color.BLACK;
            Color comboBg = isDark ? t.bg : Color.WHITE;
            UIManager.put("List.background", comboBg);
            UIManager.put("List.foreground", comboText);
            UIManager.put("List.selectionBackground", t.accentSoft);
            UIManager.put("List.selectionForeground", comboText);
            UIManager.put("ComboBox.background", comboBg);
            UIManager.put("ComboBox.foreground", comboText);
            UIManager.put("ComboBox.disabledForeground", comboText);
            UIManager.put("ComboBox.selectionBackground", t.accentSoft);
            UIManager.put("ComboBox.selectionForeground", comboText);
            UIManager.put("ScrollPane.background", t.bg);
            UIManager.put("Viewport.background", t.bg);
            UIManager.put("Slider.background", panel);
            UIManager.put("SplitPane.background", panel);
            UIManager.put("SplitPane.dividerSize", 1);
            UIManager.put("SplitPane.border", BorderFactory.createEmptyBorder());
            UIManager.put("ToolBar.background", t.panelBg);
            UIManager.put("ToolBar.foreground", t.fg);
            UIManager.put("ToolBar.border", BorderFactory.createEmptyBorder());
        } catch (Throwable ignored) { }
    }

    /* =======================================================================
     * 9. CARD SWITCHING
     * ======================================================================= */

    private void setChromeVisible(boolean visible) {
        if (menuBar != null) menuBar.setVisible(visible);
        if (toolBar != null) toolBar.setVisible(visible);
        if (statusBar != null)
            statusBar.setVisible(visible && (statusBarItem == null || statusBarItem.isSelected()));
        if (frame != null) {
            frame.getRootPane().revalidate();
            frame.getRootPane().repaint();
        }
    }

    private void showLoginCard() {
        loginPanel.refresh();
        setChromeVisible(false);
        if (!showingLogin) {
            crossFadeTo(CARD_LOGIN);
            loginPanel.startFadeIn();
            showingLogin   = true;
            showingWelcome = false;
        }
    }

    private void showWelcomeCard() {
        welcomePanel.refresh();
        if (!showingWelcome) {
            setChromeVisible(false);
            crossFadeTo(CARD_WELCOME);
            welcomePanel.startFadeIn();
            showingWelcome = true;
            showingLogin   = false;
        }
    }

    private void showEditorCard() {
        if (!showingWelcome && !showingLogin) { textArea.requestFocusInWindow(); return; }
        setChromeVisible(true);
        crossFadeTo(CARD_EDITOR);
        showingWelcome = false;
        showingLogin   = false;
        SwingUtilities.invokeLater(textArea::requestFocusInWindow);
    }

    void onLoginSuccess(String user, boolean remember) {
        loggedInUser = (user == null || user.trim().isEmpty()) ? "Guest" : user.trim();

        if (remember) {
            prefs.putBoolean(PREF_REMEMBER, true);
            prefs.put(PREF_USERNAME, loggedInUser);
        } else {
            prefs.putBoolean(PREF_REMEMBER, false);
            prefs.remove(PREF_USERNAME);
        }

        if (statUser != null) statUser.setText("  " + loggedInUser + "  ");

        showingLogin   = false;
        showingWelcome = false;

        sound.play(SoundManager.STARTUP);

        if (offerDraftRecovery()) return;

        if (pendingFile != null) {
            File f = pendingFile;
            pendingFile = null;
            loadFile(f);
            return;
        }

        if (showWelcomeOnStartup) {
            showWelcomeCard();
        } else {
            setChromeVisible(true);
            crossFadeTo(CARD_EDITOR);
            SwingUtilities.invokeLater(textArea::requestFocusInWindow);
        }
    }

    private void signOut() {
        if (!confirmSaveIfDirty()) return;
        loggedInUser = null;
        if (statUser != null) statUser.setText("");
        sound.play(SoundManager.CLICK);
        showLoginCard();
    }

    private void crossFadeTo(String cardName) {
        if (cardSwitching) { cardLayout.show(cardPanel, cardName); return; }
        cardSwitching = true;
        final long startOut = System.currentTimeMillis();
        final int outDur = 140;
        final javax.swing.Timer outTimer = new javax.swing.Timer(16, null);
        outTimer.addActionListener(e -> {
            long el = System.currentTimeMillis() - startOut;
            float p = Math.min(1f, (float) el / outDur);
            cardFadeLayerUI.setAlpha(1f - easeOutCubic(p));
            cardLayer.repaint();
            if (p >= 1f) {
                outTimer.stop();
                cardLayout.show(cardPanel, cardName);
                cardPanel.revalidate();
                cardPanel.repaint();
                final long startIn = System.currentTimeMillis();
                final int inDur = 200;
                final javax.swing.Timer inTimer = new javax.swing.Timer(16, null);
                inTimer.addActionListener(e2 -> {
                    long el2 = System.currentTimeMillis() - startIn;
                    float p2 = Math.min(1f, (float) el2 / inDur);
                    cardFadeLayerUI.setAlpha(easeOutCubic(p2));
                    cardLayer.repaint();
                    if (p2 >= 1f) { inTimer.stop(); cardSwitching = false; }
                });
                inTimer.start();
            }
        });
        outTimer.start();
    }

    static class FadeLayerUI extends LayerUI<JComponent> {
        private float alpha = 1f;
        void setAlpha(float a) { this.alpha = Math.max(0f, Math.min(1f, a)); }
        @Override public void paint(Graphics g, JComponent c) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                if (alpha < 1f)
                    g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
                super.paint(g2, c);
            } finally { g2.dispose(); }
        }
    }

    /* =======================================================================
     * 10. LOGIN PANEL  (FIXED)
     * ======================================================================= */

    /** Small scalable eye icon used by the password visibility toggle. */
    static final class EyeIcon implements Icon {
        private final Color color;
        EyeIcon() { this(new Color(0x858585)); }
        EyeIcon(Color color) { this.color = color; }
        @Override public int getIconWidth() { return 20; }
        @Override public int getIconHeight() { return 20; }
        @Override public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(color);
                g2.setStroke(new BasicStroke(1.7f, BasicStroke.CAP_ROUND,
                        BasicStroke.JOIN_ROUND));
                int cy = y + 10;
                g2.draw(new java.awt.geom.Arc2D.Float(x + 1, y + 4, 18, 12,
                        0, 180, java.awt.geom.Arc2D.OPEN));
                g2.draw(new java.awt.geom.Arc2D.Float(x + 1, y + 4, 18, 12,
                        180, 180, java.awt.geom.Arc2D.OPEN));
                g2.drawOval(x + 7, y + 7, 6, 6);
                g2.fillOval(x + 9, y + 9, 3, 3);
            } finally { g2.dispose(); }
        }
    }

    static class LoginPanel extends JPanel {
        private final LuminaryIDE ed;
        private final LogoMark logoMark;

        private final JLabel appName   = new JLabel("Luminary IDE");
        private final JLabel subtitle  = new JLabel("Sign in to continue");
        private final JLabel userLabel = new JLabel("Username");
        private final JLabel passLabel = new JLabel("Password");
        private final JLabel hintLabel = new JLabel("Pehli baar? Pehle 'Create Account' par click karein.");
        private final JLabel ruleLabel = new JLabel(
                "Password: min 8 chars, A-Z, a-z, 0-9 aur ek special character");

        private final JTextField     userField = new JTextField();
        private final JPasswordField passField = new JPasswordField();
        private final JPanel         passwordPanel;
        private final JToggleButton showPasswordBtn;
        private final char passwordEchoChar;
        private final JCheckBox      rememberBox =
                new JCheckBox("Remember me on this computer");
        private final JLabel         errorLabel = new JLabel(" ");

        private final JButton signInBtn;
        private final JButton signUpBtn;
        private final JButton guestBtn;

        private javax.swing.Timer fadeTimer;

        LoginPanel(LuminaryIDE ed) {
            this.ed = ed;
            setOpaque(true);
            setLayout(new BorderLayout(0, 0));
            setBorder(new EmptyBorder(0, 0, 0, 0));

            /* ---- The actual form ---- */
            JPanel box = new JPanel(new GridBagLayout());
            box.setOpaque(false);
            box.setBorder(new EmptyBorder(24, 32, 24, 32));

            GridBagConstraints c = new GridBagConstraints();
            c.gridx = 0;
            c.gridwidth = GridBagConstraints.REMAINDER;
            c.anchor = GridBagConstraints.CENTER;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.weightx = 1.0;

            c.gridy = 0;
            c.fill = GridBagConstraints.NONE;
            c.weightx = 0;
            c.insets = new Insets(0, 0, 12, 0);
            logoMark = new LogoMark(ed.theme);
            logoMark.setPreferredSize(new Dimension(52, 52));
            box.add(logoMark, c);

            c.gridy = 1;
            c.insets = new Insets(0, 0, 4, 0);
            appName.setFont(uiFont(Font.BOLD, 20));
            appName.setHorizontalAlignment(SwingConstants.CENTER);
            box.add(appName, c);

            c.gridy = 2;
            c.insets = new Insets(0, 0, 22, 0);
            subtitle.setFont(uiFont(Font.PLAIN, 12));
            subtitle.setHorizontalAlignment(SwingConstants.CENTER);
            box.add(subtitle, c);

            c.gridy = 3;
            c.anchor = GridBagConstraints.WEST;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.weightx = 1.0;
            c.insets = new Insets(0, 0, 4, 0);
            userLabel.setFont(uiFont(Font.PLAIN, 11));
            box.add(userLabel, c);

            c.gridy = 4;
            c.insets = new Insets(0, 0, 12, 0);
            styleField(userField);
            userField.setPreferredSize(new Dimension(300, 36));
            box.add(userField, c);

            c.gridy = 5;
            c.insets = new Insets(0, 0, 4, 0);
            passLabel.setFont(uiFont(Font.PLAIN, 11));
            box.add(passLabel, c);

            c.gridy = 6;
            c.insets = new Insets(0, 0, 8, 0);
            passwordEchoChar = passField.getEchoChar();
            passField.setFont(uiFont(Font.PLAIN, 13));
            passField.setBorder(new EmptyBorder(6, 10, 6, 6));
            passField.setPreferredSize(new Dimension(260, 36));

            showPasswordBtn = new JToggleButton(new EyeIcon());
            showPasswordBtn.setToolTipText("Show password");
            showPasswordBtn.getAccessibleContext().setAccessibleName("Show password");
            showPasswordBtn.setFocusable(false);
            showPasswordBtn.setOpaque(false);
            showPasswordBtn.setContentAreaFilled(false);
            showPasswordBtn.setBorder(new EmptyBorder(4, 8, 4, 8));
            showPasswordBtn.addActionListener(e -> {
                boolean visible = showPasswordBtn.isSelected();
                passField.setEchoChar(visible ? (char) 0 : passwordEchoChar);
                showPasswordBtn.setToolTipText(visible ? "Hide password" : "Show password");
                showPasswordBtn.getAccessibleContext().setAccessibleName(
                        visible ? "Hide password" : "Show password");
                passField.requestFocusInWindow();
            });

            passwordPanel = new JPanel(new BorderLayout());
            passwordPanel.setPreferredSize(new Dimension(300, 36));
            passwordPanel.setBorder(BorderFactory.createLineBorder(ed.theme.border));
            passwordPanel.setBackground(ed.theme.bg);
            passwordPanel.add(passField, BorderLayout.CENTER);
            passwordPanel.add(showPasswordBtn, BorderLayout.EAST);
            box.add(passwordPanel, c);

            userField.addFocusListener(new FocusAdapter() {
                @Override public void focusGained(FocusEvent e) {
                    userField.setBorder(BorderFactory.createCompoundBorder(
                            BorderFactory.createLineBorder(ed.theme.accent),
                            new EmptyBorder(6, 10, 6, 10)));
                }
                @Override public void focusLost(FocusEvent e) {
                    userField.setBorder(BorderFactory.createCompoundBorder(
                            BorderFactory.createLineBorder(ed.theme.border),
                            new EmptyBorder(6, 10, 6, 10)));
                }
            });
            passField.addFocusListener(new FocusAdapter() {
                @Override public void focusGained(FocusEvent e) {
                    passwordPanel.setBorder(BorderFactory.createLineBorder(ed.theme.accent));
                }
                @Override public void focusLost(FocusEvent e) {
                    passwordPanel.setBorder(BorderFactory.createLineBorder(ed.theme.border));
                }
            });

            c.gridy = 7;
            c.anchor = GridBagConstraints.WEST;
            c.fill = GridBagConstraints.NONE;
            c.weightx = 0;
            c.insets = new Insets(0, 0, 6, 0);
            rememberBox.setOpaque(false);
            rememberBox.setFocusable(false);
            rememberBox.setFont(uiFont(Font.PLAIN, 11));
            box.add(rememberBox, c);

            /* Error label - bigger & bold so users can actually see validation */
            c.gridy = 8;
            c.anchor = GridBagConstraints.WEST;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.weightx = 1.0;
            c.insets = new Insets(0, 0, 10, 0);
            errorLabel.setFont(uiFont(Font.BOLD, 12));
            errorLabel.setBorder(new EmptyBorder(2, 2, 2, 2));
            box.add(errorLabel, c);

            /* Sign In */
            c.gridy = 9;
            c.insets = new Insets(0, 0, 8, 0);
            signInBtn = new JButton("Sign In");
            signInBtn.setIcon(IDEIcons.KEY);
            signInBtn.setIconTextGap(8);
            signInBtn.setFont(uiFont(Font.BOLD, 13));
            signInBtn.setBorder(new EmptyBorder(9, 18, 9, 18));
            signInBtn.setFocusable(false);
            signInBtn.setEnabled(true);
            signInBtn.setOpaque(true);
            signInBtn.setContentAreaFilled(true);
            signInBtn.setBorderPainted(false);
            signInBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            signInBtn.addActionListener(e -> attemptLogin());
            installSmoothHover(signInBtn,
                    () -> ed.theme.accent, () -> brighten(ed.theme.accent, 0.12f));
            box.add(signInBtn, c);

            /* Create Account - add subtle border so it looks like a clickable button */
            c.gridy = 10;
            c.insets = new Insets(0, 0, 8, 0);
            signUpBtn = new JButton("Create Account");
            signUpBtn.setIcon(IDEIcons.NEW);
            signUpBtn.setIconTextGap(8);
            signUpBtn.setFont(uiFont(Font.BOLD, 12));
            signUpBtn.setToolTipText("Create a new local account");
            signUpBtn.setBorder(new EmptyBorder(8, 18, 8, 18));
            signUpBtn.setFocusable(false);
            signUpBtn.setEnabled(true);
            signUpBtn.setOpaque(true);
            signUpBtn.setContentAreaFilled(true);
            signUpBtn.setBorderPainted(true);
            signUpBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            signUpBtn.addActionListener(e -> {
                try { attemptSignUp(); }
                catch (Throwable t) {
                    JOptionPane.showMessageDialog(LoginPanel.this,
                            "Sign up failed:\n" + t,
                            "Error", JOptionPane.ERROR_MESSAGE);
                }
            });
            installSmoothHover(signUpBtn,
                    () -> ed.theme.bg, () -> ed.theme.hover);
            box.add(signUpBtn, c);

            /* Guest */
            c.gridy = 11;
            c.insets = new Insets(0, 0, 16, 0);
            guestBtn = new JButton("Continue as Guest");
            guestBtn.setIcon(IDEIcons.USER);
            guestBtn.setIconTextGap(8);
            guestBtn.setFont(uiFont(Font.PLAIN, 12));
            guestBtn.setBorder(new EmptyBorder(8, 18, 8, 18));
            guestBtn.setFocusable(false);
            guestBtn.setEnabled(true);
            guestBtn.setOpaque(true);
            guestBtn.setContentAreaFilled(true);
            guestBtn.setBorderPainted(true);
            guestBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            guestBtn.addActionListener(e -> ed.onLoginSuccess("Guest", false));
            installSmoothHover(guestBtn,
                    () -> ed.theme.bg, () -> ed.theme.hover);
            box.add(guestBtn, c);

            c.gridy = 12;
            c.anchor = GridBagConstraints.CENTER;
            c.fill = GridBagConstraints.NONE;
            c.weightx = 0;
            c.insets = new Insets(0, 0, 0, 0);
            hintLabel.setFont(uiFont(Font.PLAIN, 10));
            hintLabel.setHorizontalAlignment(SwingConstants.CENTER);
            box.add(hintLabel, c);

            c.gridy = 13;
            c.insets = new Insets(4, 0, 0, 0);
            ruleLabel.setFont(uiFont(Font.PLAIN, 10));
            ruleLabel.setHorizontalAlignment(SwingConstants.CENTER);
            box.add(ruleLabel, c);

            /* ---- Wrapper + scroll so buttons are never clipped ---- */
            JPanel wrapper = new JPanel(new GridBagLayout());
            wrapper.setOpaque(false);
            GridBagConstraints gc = new GridBagConstraints();
            gc.gridx = 0; gc.gridy = 0;
            gc.weightx = 1.0; gc.weighty = 1.0;
            gc.anchor = GridBagConstraints.CENTER;
            gc.fill = GridBagConstraints.NONE;
            wrapper.add(box, gc);

            JScrollPane sp = new JScrollPane(wrapper);
            sp.setBorder(null);
            sp.setOpaque(false);
            sp.getViewport().setOpaque(false);
            sp.getVerticalScrollBar().setUnitIncrement(24);
            sp.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
            sp.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
            try {
                sp.getVerticalScrollBar().setUI(new ModernScrollBarUI(ed));
                sp.getVerticalScrollBar().setPreferredSize(new Dimension(10, 10));
            } catch (Throwable ignored) { }
            add(sp, BorderLayout.CENTER);

            userField.addActionListener(e -> passField.requestFocusInWindow());
            passField.addActionListener(e -> attemptLogin());

            applyTheme(ed.theme);
        }

        private void styleField(JTextField f) {
            f.setFont(uiFont(Font.PLAIN, 13));
            f.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(ed.theme.border),
                    new EmptyBorder(6, 10, 6, 10)));
        }

        private Map<String, String> loadUsers() {
            Map<String, String> users = new HashMap<>();
            String raw = ed.prefs.get(PREF_USERS, "");
            if (raw == null || raw.isEmpty()) return users;
            for (String entry : raw.split("\u001F")) {
                int idx = entry.indexOf(':');
                if (idx > 0) users.put(entry.substring(0, idx), entry.substring(idx + 1));
            }
            return users;
        }

        private void saveUsers(Map<String, String> users) {
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, String> e : users.entrySet()) {
                if (sb.length() > 0) sb.append('\u001F');
                sb.append(e.getKey()).append(':').append(e.getValue());
            }
            ed.prefs.put(PREF_USERS, sb.toString());
        }

        private boolean hasAnyUser() {
            return !loadUsers().isEmpty();
        }

        private void attemptLogin() {
            try {
                String user = userField.getText().trim();
                String pass = new String(passField.getPassword());

                if (user.isEmpty()) {
                    setError("Please enter a username.");
                    userField.requestFocusInWindow();
                    return;
                }
                if (pass.isEmpty()) {
                    setError("Please enter a password.");
                    passField.requestFocusInWindow();
                    return;
                }

                Map<String, String> users = loadUsers();

                if (users.isEmpty()) {
                    setError("Koi account nahi hai. Pehle 'Create Account' par click karein.");
                    return;
                }

                String stored = users.get(user);
                if (stored == null) {
                    setError("User \"" + user + "\" nahi mila. 'Create Account' try karein.");
                    passField.setText("");
                    return;
                }

                if (!verifyPassword(pass, stored)) {
                    setError("Galat password. Dobara koshish karein.");
                    passField.setText("");
                    passField.requestFocusInWindow();
                    return;
                }

                setError(" ");
                ed.onLoginSuccess(user, rememberBox.isSelected());
            } catch (Throwable t) {
                JOptionPane.showMessageDialog(LoginPanel.this,
                        "Login failed:\n" + t,
                        "Sign In Error", JOptionPane.ERROR_MESSAGE);
            }
        }

        private void attemptSignUp() {
            String user = userField.getText().trim();
            String pass = new String(passField.getPassword());

            if (user.isEmpty()) {
                setError("Username daalein.");
                userField.requestFocusInWindow();
                return;
            }
            if (user.length() < 3) {
                setError("Username kam se kam 3 characters ka hona chahiye.");
                userField.requestFocusInWindow();
                return;
            }
            if (user.contains(":") || user.contains("\u001F")) {
                setError("Username me ':' ya control characters allowed nahi.");
                return;
            }

            String passErr = validatePassword(user, pass);
            if (passErr != null) {
                setError(passErr);
                passField.requestFocusInWindow();
                return;
            }

            Map<String, String> users = loadUsers();
            if (users.containsKey(user)) {
                setError("Yeh username already exist karta hai. Sign In karein.");
                return;
            }

            users.put(user, hashPassword(pass));
            saveUsers(users);
            setError(" ");

            JOptionPane.showMessageDialog(this,
                    "Account ban gaya: " + user + "\nAb aap sign in ho chuke hain.",
                    "Account Created", JOptionPane.INFORMATION_MESSAGE);

            ed.onLoginSuccess(user, rememberBox.isSelected());
        }

        /** Password valid hai to null, warna error message return karta hai. */
        private String validatePassword(String user, String pass) {
            if (pass.length() < 8)
                return "Password kam se kam 8 characters ka hona chahiye.";
            if (pass.length() > 64)
                return "Password 64 characters se zyada nahi ho sakta.";
            if (!pass.matches(".*[a-z].*"))
                return "Password me kam se kam ek small letter (a-z) chahiye.";
            if (!pass.matches(".*[A-Z].*"))
                return "Password me kam se kam ek capital letter (A-Z) chahiye.";
            if (!pass.matches(".*\\d.*"))
                return "Password me kam se kam ek number (0-9) chahiye.";
            if (!pass.matches(".*[^A-Za-z0-9].*"))
                return "Password me kam se kam ek special character chahiye (@, #, $, ! ...).";
            if (pass.contains(" "))
                return "Password me space allowed nahi hai.";
            if (pass.equalsIgnoreCase(user))
                return "Password username jaisa nahi ho sakta.";
            String lower = pass.toLowerCase();
            String[] common = { "password", "12345678", "qwerty123", "admin123", "iloveyou" };
            for (String cc : common)
                if (lower.contains(cc)) return "Yeh password bahut common hai, dusra chuno.";
            return null;
        }

        private void setError(String msg) {
            boolean real = msg != null && !msg.trim().isEmpty();
            errorLabel.setText(real ? "[!] " + msg : " ");
            if (real) ed.sound.play(SoundManager.ERROR);
        }

        void refresh() {
            boolean rem   = ed.prefs.getBoolean(PREF_REMEMBER, false);
            String saved  = ed.prefs.get(PREF_USERNAME, "");

            rememberBox.setSelected(rem);
            passField.setText("");

            if (rem && !saved.isEmpty()) {
                userField.setText(saved);
                SwingUtilities.invokeLater(passField::requestFocusInWindow);
            } else {
                userField.setText("");
                SwingUtilities.invokeLater(userField::requestFocusInWindow);
            }

            hintLabel.setText(hasAnyUser()
                    ? "Naya user? 'Create Account' click karein."
                    : "Pehla user — 'Create Account' se shuru karein.");

            errorLabel.setText(" ");
            applyTheme(ed.theme);
            revalidate();
            repaint();
        }

        void applyTheme(Theme t) {
            setBackground(t.bg);

            logoMark.setTheme(t);

            appName.setForeground(t.fg);
            subtitle.setForeground(t.textMuted);
            userLabel.setForeground(t.textMuted);
            passLabel.setForeground(t.textMuted);
            hintLabel.setForeground(t.textMuted);
            ruleLabel.setForeground(t.textMuted);
            rememberBox.setForeground(t.fg);
            errorLabel.setForeground(t.dark ? c(0xFF8080) : c(0xCF222E));

            userField.setBackground(t.bg);
            userField.setForeground(t.fg);
            userField.setCaretColor(t.accent);
            userField.setSelectionColor(t.selection);
            userField.setSelectedTextColor(t.selectionFg);
            userField.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(t.border),
                    new EmptyBorder(6, 10, 6, 10)));

            passField.setBackground(t.bg);
            passField.setForeground(t.fg);
            passField.setCaretColor(t.accent);
            passField.setSelectionColor(t.selection);
            passField.setSelectedTextColor(t.selectionFg);
            passField.setBorder(new EmptyBorder(6, 10, 6, 6));
            if (passwordPanel != null) {
                passwordPanel.setBackground(t.bg);
                passwordPanel.setBorder(BorderFactory.createLineBorder(t.border));
            }
            showPasswordBtn.setForeground(t.textMuted);
            showPasswordBtn.setBackground(t.bg);
            showPasswordBtn.setIcon(new EyeIcon(t.textMuted));
            showPasswordBtn.setSelectedIcon(new EyeIcon(t.accent));

            signInBtn.setBackground(t.accent);
            signInBtn.setForeground(contrast(t.accent));

            signUpBtn.setBackground(t.bg);
            signUpBtn.setForeground(t.fg);
            signUpBtn.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(t.border),
                    new EmptyBorder(8, 18, 8, 18)));

            guestBtn.setBackground(t.bg);
            guestBtn.setForeground(t.fg);
            guestBtn.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(t.border),
                    new EmptyBorder(8, 18, 8, 18)));

            repaint();
        }

        void startFadeIn() {
            if (fadeTimer != null) fadeTimer.stop();
            if (ed.loginFadeLayerUI != null) ed.loginFadeLayerUI.setAlpha(0f);
            if (ed.loginLayer != null) ed.loginLayer.repaint();

            final long start = System.currentTimeMillis();
            final int dur = 550;
            fadeTimer = new javax.swing.Timer(16, null);
            fadeTimer.addActionListener(e -> {
                long elapsed = System.currentTimeMillis() - start;
                float p = Math.min(1f, (float) elapsed / dur);
                if (ed.loginFadeLayerUI != null) ed.loginFadeLayerUI.setAlpha(easeOutCubic(p));
                if (ed.loginLayer != null) ed.loginLayer.repaint();
                if (p >= 1f) fadeTimer.stop();
            });
            fadeTimer.start();
        }
    }

    /* =======================================================================
     * 11. WELCOME PANEL
     * ======================================================================= */

    static class WelcomePanel extends JPanel {
        private static final String[] TIPS = {
                "Ctrl+D duplicates the current line.",
                "Alt+Up / Alt+Down move the current line.",
                "Ctrl+/ toggles a line comment.",
                "Ctrl+Shift+I inserts an image inline.",
                "Drop an image file straight onto the editor.",
                "Ctrl+ + / Ctrl+- / Ctrl+0 change zoom.",
                "F5 inserts the current date and time.",
                "Ctrl+G jumps to a line number.",
                "Rainbow brackets show nesting depth by colour.",
                "Try the Cyberpunk theme for a change."
        };

        private final LuminaryIDE ed;
        private final LogoMark logoMark;
        private final JLabel greeting = new JLabel();
        private final JLabel appName = new JLabel("Luminary IDE");
        private final JLabel startHead = new JLabel("Start");
        private final JLabel recentHead = new JLabel("Recent");
        private final JLabel tipLabel = new JLabel();
        private final JPanel recentList = new JPanel();
        private final JCheckBox showOnStart;
        private final List<Component> themed = new ArrayList<>();
        private javax.swing.Timer fadeTimer;

        WelcomePanel(LuminaryIDE ed) {
            this.ed = ed;
            setLayout(new GridBagLayout());
            setOpaque(true);
            setBorder(new EmptyBorder(24, 32, 24, 32));

            JPanel container = new JPanel();
            container.setOpaque(false);
            container.setLayout(new BoxLayout(container, BoxLayout.Y_AXIS));
            container.setPreferredSize(new Dimension(760, 480));

            JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
            header.setOpaque(false);
            header.setAlignmentX(Component.LEFT_ALIGNMENT);
            logoMark = new LogoMark(ed.theme);
            logoMark.setPreferredSize(new Dimension(34, 34));
            header.add(logoMark);

            JPanel titleBlock = new JPanel();
            titleBlock.setLayout(new BoxLayout(titleBlock, BoxLayout.Y_AXIS));
            titleBlock.setOpaque(false);
            appName.setFont(uiFont(Font.BOLD, 16));
            greeting.setFont(uiFont(Font.PLAIN, 12));
            titleBlock.add(appName);
            titleBlock.add(Box.createVerticalStrut(2));
            titleBlock.add(greeting);
            header.add(titleBlock);
            container.add(header);
            container.add(Box.createVerticalStrut(28));

            JPanel columns = new JPanel(new GridBagLayout());
            columns.setOpaque(false);
            columns.setAlignmentX(Component.LEFT_ALIGNMENT);

            GridBagConstraints gc = new GridBagConstraints();
            gc.fill = GridBagConstraints.BOTH;
            gc.weightx = 1; gc.weighty = 1;
            gc.insets = new Insets(0, 0, 0, 24);
            gc.gridx = 0; gc.gridy = 0;

            JPanel left = new JPanel();
            left.setOpaque(false);
            left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
            startHead.setFont(uiFont(Font.BOLD, 11));
            startHead.setAlignmentX(Component.LEFT_ALIGNMENT);
            left.add(startHead);
            left.add(Box.createVerticalStrut(10));

            JButton btnNew   = actionRow(IDEIcons.NEW, "New File",     "Ctrl+N",       e -> ed.newDocument());
            JButton btnOpen  = actionRow(IDEIcons.OPEN, "Open File...",   "Ctrl+O",       e -> ed.openFile());
            JButton btnImage = actionRow(IDEIcons.IMAGE, "Open Image...",  "Ctrl+Shift+I", e -> ed.openImageViewer());
            JButton btnTheme = actionRow(IDEIcons.THEME, "Change Theme", null,           e -> showThemePopup(btnFindThemeHolder[0]));
            btnFindThemeHolder[0] = btnTheme;
            JButton btnSignOut = actionRow(IDEIcons.SIGN_OUT, "Sign Out",   null,           e -> ed.signOut());
            btnNew.setAlignmentX(Component.LEFT_ALIGNMENT);
            btnOpen.setAlignmentX(Component.LEFT_ALIGNMENT);
            btnImage.setAlignmentX(Component.LEFT_ALIGNMENT);
            btnTheme.setAlignmentX(Component.LEFT_ALIGNMENT);
            btnSignOut.setAlignmentX(Component.LEFT_ALIGNMENT);
            left.add(btnNew);   left.add(Box.createVerticalStrut(3));
            left.add(btnOpen);  left.add(Box.createVerticalStrut(3));
            left.add(btnImage); left.add(Box.createVerticalStrut(3));
            left.add(btnTheme); left.add(Box.createVerticalStrut(3));
            left.add(btnSignOut);
            columns.add(left, gc);

            gc.gridx = 1; gc.insets = new Insets(0, 0, 0, 0);
            JPanel right = new JPanel();
            right.setOpaque(false);
            right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));
            recentHead.setFont(uiFont(Font.BOLD, 11));
            recentHead.setAlignmentX(Component.LEFT_ALIGNMENT);
            right.add(recentHead);
            right.add(Box.createVerticalStrut(10));
            recentList.setOpaque(false);
            recentList.setLayout(new BoxLayout(recentList, BoxLayout.Y_AXIS));
            recentList.setAlignmentX(Component.LEFT_ALIGNMENT);
            right.add(recentList);
            columns.add(right, gc);

            container.add(columns);
            container.add(Box.createVerticalStrut(20));

            tipLabel.setFont(uiFont(Font.PLAIN, 11));
            tipLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            container.add(tipLabel);
            container.add(Box.createVerticalStrut(20));

            showOnStart = new JCheckBox("Show welcome page on startup");
            showOnStart.setOpaque(false);
            showOnStart.setFocusable(false);
            showOnStart.setFont(uiFont(Font.PLAIN, 11));
            showOnStart.setAlignmentX(Component.LEFT_ALIGNMENT);
            showOnStart.addActionListener(e -> {
                ed.showWelcomeOnStartup = showOnStart.isSelected();
                ed.prefs.putBoolean(PREF_WELCOME, ed.showWelcomeOnStartup);
                ed.showWelcomeItem.setSelected(ed.showWelcomeOnStartup);
            });
            container.add(showOnStart);

            GridBagConstraints outerGc = new GridBagConstraints();
            outerGc.gridx = 0;
            outerGc.gridy = 0;
            outerGc.weightx = 1.0;
            outerGc.weighty = 1.0;
            outerGc.anchor = GridBagConstraints.CENTER;
            outerGc.fill = GridBagConstraints.NONE;
            add(container, outerGc);

            refresh();
        }

        private final JButton[] btnFindThemeHolder = new JButton[1];

        private void showThemePopup(JButton anchor) {
            JPopupMenu pm = new JPopupMenu();
            for (int i = 0; i < THEMES.size(); i++) {
                final int idx = i;
                JMenuItem mi = new JMenuItem(THEMES.get(i).name, IDEIcons.forTheme(THEMES.get(i).name));
                mi.setIconTextGap(8);
                mi.addActionListener(ev -> {
                    ed.themeItems[idx].setSelected(true);
                    ed.applyTheme(THEMES.get(idx));
                    ed.sound.play(SoundManager.THEME);
                });
                pm.add(mi);
            }
            pm.show(anchor, 0, anchor.getHeight() + 2);
        }

        private JButton actionRow(Icon icon, String label, String shortcut, ActionListener action) {
            JButton b = new JButton();
            b.setLayout(new BorderLayout());
            b.setBorder(new EmptyBorder(9, 12, 9, 12));
            b.setFocusable(false);
            b.setOpaque(true);
            b.setContentAreaFilled(true);
            b.setBorderPainted(false);
            b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
            b.setHorizontalAlignment(SwingConstants.LEFT);
            JLabel left = new JLabel(label);
            if (icon != null) {
                left.setIcon(icon);
                left.setIconTextGap(8);
            }
            left.setFont(uiFont(Font.PLAIN, 13));
            b.add(left, BorderLayout.WEST);
            if (shortcut != null) {
                JLabel right = new JLabel(shortcut);
                right.setFont(uiFont(Font.PLAIN, 11));
                right.setName("shortcut");
                b.add(right, BorderLayout.EAST);
            }
            b.addActionListener(action);
            installSmoothHover(b, () -> ed.theme.panelBg, () -> ed.theme.hover);
            themed.add(b);
            return b;
        }

        private JButton actionRow(String label, String shortcut, ActionListener action) {
            return actionRow(null, label, shortcut, action);
        }

        void refresh() {
            int hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
            String part = hour < 12 ? "Good morning" : (hour < 18 ? "Good afternoon" : "Good evening");
            String who = ed.loggedInUser == null ? "" : ed.loggedInUser.trim();
            if (!who.isEmpty()) part = part + ", " + who;
            greeting.setText(part + " — what would you like to build?");

            recentList.removeAll();
            String[] recent = ed.getRecentFiles();
            if (recent.length == 0) {
                JLabel none = new JLabel("No recent files yet");
                none.setFont(uiFont(Font.PLAIN, 11));
                none.setBorder(new EmptyBorder(8, 12, 8, 12));
                recentList.add(none);
            } else {
                int shown = 0;
                for (String path : recent) {
                    if (shown++ >= 8) break;
                    File f = new File(path);
                    recentList.add(recentItem(f, path));
                }
            }

            int idx = ed.prefs.getInt(PREF_TIP, 0);
            ed.prefs.putInt(PREF_TIP, (idx + 1) % TIPS.length);
            tipLabel.setText("Tip  ·  " + TIPS[Math.abs(idx) % TIPS.length]);

            showOnStart.setSelected(ed.showWelcomeOnStartup);
            applyTheme(ed.theme);
            revalidate();
            repaint();
        }

        private JButton recentItem(File f, String path) {
            JButton b = new JButton();
            b.setLayout(new BorderLayout());
            b.setBorder(new EmptyBorder(8, 12, 8, 12));
            b.setFocusable(false);
            b.setOpaque(true);
            b.setContentAreaFilled(true);
            b.setBorderPainted(false);
            b.setHorizontalAlignment(SwingConstants.LEFT);
            b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));

            JPanel txt = new JPanel();
            txt.setLayout(new BoxLayout(txt, BoxLayout.Y_AXIS));
            txt.setOpaque(false);
            JLabel name = new JLabel(f.getName());
            name.setFont(uiFont(Font.PLAIN, 13));
            JLabel pathLbl = new JLabel(shortenPath(path, 52));
            pathLbl.setFont(uiFont(Font.PLAIN, 11));
            pathLbl.setName("muted");
            txt.add(name);
            txt.add(pathLbl);
            JLabel iconLbl = new JLabel();
            iconLbl.setIcon(IDEIcons.NEW);
            iconLbl.setBorder(new EmptyBorder(0, 0, 0, 6));
            b.add(iconLbl, BorderLayout.WEST);
            b.add(txt, BorderLayout.CENTER);

            installSmoothHover(b, () -> ed.theme.panelBg, () -> ed.theme.hover);
            b.addActionListener(e -> {
                ed.sound.play(SoundManager.CLICK);
                if (!f.exists()) {
                    JOptionPane.showMessageDialog(this, "File no longer exists:\n" + path,
                            "Open Recent", JOptionPane.WARNING_MESSAGE);
                    ed.sound.play(SoundManager.ERROR);
                    ed.removeRecentFile(path); ed.rebuildRecentMenu(); refresh(); return;
                }
                if (!ed.confirmSaveIfDirty()) return;
                ed.loadFile(f);
            });
            themed.add(b);
            return b;
        }

        private static String shortenPath(String p, int max) {
            if (p == null) return "";
            if (p.length() <= max) return p;
            return "…" + p.substring(p.length() - max + 1);
        }

        void applyTheme(Theme t) {
            setBackground(t.bg);
            logoMark.setTheme(t);
            appName.setForeground(t.fg);
            greeting.setForeground(t.textMuted);
            startHead.setForeground(t.textMuted);
            recentHead.setForeground(t.textMuted);
            tipLabel.setForeground(t.textMuted);
            showOnStart.setForeground(t.fg);
            showOnStart.setBackground(t.bg);

            for (Component comp : allComponents(this)) {
                if (comp instanceof JButton) {
                    JButton b = (JButton) comp;
                    b.setBackground(t.panelBg);
                    b.setForeground(t.fg);
                    for (Component sub : b.getComponents()) {
                        if (sub instanceof JLabel) {
                            JLabel l = (JLabel) sub;
                            if ("muted".equals(l.getName()) || "shortcut".equals(l.getName()))
                                l.setForeground(t.textMuted);
                            else l.setForeground(t.fg);
                        }
                        if (sub instanceof JPanel) {
                            for (Component inner : ((JPanel) sub).getComponents()) {
                                if (inner instanceof JLabel) {
                                    JLabel l = (JLabel) inner;
                                    l.setForeground("muted".equals(l.getName()) ? t.textMuted : t.fg);
                                }
                            }
                        }
                    }
                }
            }
            repaint();
        }

        private static List<Component> allComponents(Container root) {
            List<Component> out = new ArrayList<>();
            collectAll(root, out);
            return out;
        }
        private static void collectAll(Container root, List<Component> out) {
            for (Component cc : root.getComponents()) {
                out.add(cc);
                if (cc instanceof Container) collectAll((Container) cc, out);
            }
        }

        void startFadeIn() {
            if (fadeTimer != null) fadeTimer.stop();
            if (ed.fadeLayerUI != null) ed.fadeLayerUI.setAlpha(0f);
            if (ed.welcomeLayer != null) ed.welcomeLayer.repaint();
            final long start = System.currentTimeMillis();
            final int dur = 550;
            fadeTimer = new javax.swing.Timer(16, null);
            fadeTimer.addActionListener(e -> {
                long elapsed = System.currentTimeMillis() - start;
                float p = Math.min(1f, (float) elapsed / dur);
                if (ed.fadeLayerUI != null) ed.fadeLayerUI.setAlpha(easeOutCubic(p));
                if (ed.welcomeLayer != null) ed.welcomeLayer.repaint();
                if (p >= 1f) fadeTimer.stop();
            });
            fadeTimer.start();
        }
    }

    /* =======================================================================
     * 12. LOGO MARK
     * ======================================================================= */

    static class LogoMark extends JComponent {
        private Theme theme;
        private Image cachedLogo;

        LogoMark(Theme t) {
            this.theme = t;
            setOpaque(false);
            try {
                File f = new File("assets/logo.png");
                if (f.exists() && f.isFile()) {
                    cachedLogo = ImageIO.read(f);
                } else {
                    InputStream in = LuminaryIDE.class.getResourceAsStream("/assets/logo.png");
                    if (in != null) {
                        cachedLogo = ImageIO.read(in);
                        in.close();
                    }
                }
            } catch (Throwable ignored) { }
        }

        void setTheme(Theme t) { this.theme = t; repaint(); }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                int w = getWidth(), h = getHeight();
                int s = Math.min(w, h);
                int x = (w - s) / 2, y = (h - s) / 2;

                if (cachedLogo != null) {
                    g2.drawImage(cachedLogo, x, y, s, s, null);
                    return;
                }

                double corner = s * 0.24;
                g2.setPaint(new GradientPaint(x, y, theme.accent, x + s, y + s,
                        mix(theme.accent, theme.dark ? Color.BLACK : Color.WHITE, 0.35f)));
                g2.fill(new RoundRectangle2D.Double(x, y, s, s, corner, corner));

                double cx = x + s / 2.0, cy = y + s / 2.0;
                double starR = s * 0.32;
                Path2D star = new Path2D.Double();
                star.moveTo(cx, cy - starR);
                star.quadTo(cx, cy, cx + starR, cy);
                star.quadTo(cx, cy, cx, cy + starR);
                star.quadTo(cx, cy, cx - starR, cy);
                star.quadTo(cx, cy, cx, cy - starR);
                star.closePath();
                g2.setColor(contrast(theme.accent));
                g2.fill(star);

                double dot = Math.max(1.0, s * 0.04);
                g2.setColor(theme.accent);
                g2.fill(new Ellipse2D.Double(cx - dot, cy - dot, dot * 2, dot * 2));
            } finally { g2.dispose(); }
        }
    }

    /* =======================================================================
     * 13. COLOUR HELPERS
     * ======================================================================= */

    static Color brighten(Color col, float f) {
        return new Color(clamp((int) (col.getRed() + (255 - col.getRed()) * f)),
                clamp((int) (col.getGreen() + (255 - col.getGreen()) * f)),
                clamp((int) (col.getBlue() + (255 - col.getBlue()) * f)));
    }
    static Color darken(Color col, float f) {
        return new Color(clamp((int) (col.getRed() * (1 - f))),
                clamp((int) (col.getGreen() * (1 - f))),
                clamp((int) (col.getBlue() * (1 - f))));
    }
    static Color mix(Color a, Color b, float f) {
        return new Color(clamp((int) (a.getRed() + (b.getRed() - a.getRed()) * f)),
                clamp((int) (a.getGreen() + (b.getGreen() - a.getGreen()) * f)),
                clamp((int) (a.getBlue() + (b.getBlue() - a.getBlue()) * f)));
    }
    static Color contrast(Color col) {
        double lum = (0.299 * col.getRed() + 0.587 * col.getGreen() + 0.114 * col.getBlue()) / 255.0;
        return lum > 0.6 ? new Color(0x101010) : Color.WHITE;
    }
    private static int clamp(int v) { return v < 0 ? 0 : (v > 255 ? 255 : v); }

    /* ---- PBKDF2 password hashing ---- */

    private static final int PBKDF2_ITERATIONS = 120_000;
    private static final int PBKDF2_KEYLEN_BITS = 256;
    private static final int PBKDF2_SALT_LEN = 16;

    private static String hashPassword(String password) {
        try {
            SecureRandom sr = new SecureRandom();
            byte[] salt = new byte[PBKDF2_SALT_LEN];
            sr.nextBytes(salt);
            byte[] hash = pbkdf2(password, salt);
            return toHex(salt) + ':' + toHex(hash);
        } catch (Exception e) {
            return Integer.toHexString(password.hashCode());
        }
    }

    private static boolean verifyPassword(String password, String stored) {
        try {
            int idx = stored.indexOf(':');
            if (idx <= 0) {
                return stored.equals(legacyHash(password));
            }
            byte[] salt     = fromHex(stored.substring(0, idx));
            byte[] expected = fromHex(stored.substring(idx + 1));
            byte[] actual   = pbkdf2(password, salt);
            if (actual.length != expected.length) return false;
            int diff = 0;
            for (int i = 0; i < actual.length; i++) diff |= actual[i] ^ expected[i];
            return diff == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static byte[] pbkdf2(String password, byte[] salt) throws Exception {
        SecretKeyFactory f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        return f.generateSecret(
                        new PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, PBKDF2_KEYLEN_BITS))
                .getEncoded();
    }

    private static String legacyHash(String password) {
        try {
            java.security.MessageDigest md =
                    java.security.MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(password.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) sb.append(String.format("%02x", b & 0xFF));
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(password.hashCode());
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format("%02x", b & 0xFF));
        return sb.toString();
    }

    private static byte[] fromHex(String hex) {
        int len = hex.length();
        byte[] out = new byte[len / 2];
        for (int i = 0; i < out.length; i++)
            out[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        return out;
    }

    /* =======================================================================
     * 14. STATUS BAR
     * ======================================================================= */

    private void buildStatusBar() {
        statusBar = new JPanel(new BorderLayout());
        statusBar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, theme.border));

        statusBarLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        statusBarLeft.setOpaque(false);

        statLnCol  = plainStatus("Ln 1, Col 1");
        statSel    = plainStatus("");
        statLines  = plainStatus("Lines: 1");
        statWords  = plainStatus("Words: 0");

        statusBarLeft.add(statLnCol);
        statusBarLeft.add(statSel);
        statusBarLeft.add(divider());
        statusBarLeft.add(statLines);
        statusBarLeft.add(divider());
        statusBarLeft.add(statWords);

        statusBarRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        statusBarRight.setOpaque(false);

        statLang     = statusPill(IDEIcons.forLanguage(getEffectiveLanguage()), getEffectiveLanguage(), "Click to change language mode", () -> showLanguagePopup(statLang));
        statTerminal = statusPill(IDEIcons.TERMINAL, "Terminal", "Toggle output terminal (Ctrl+`)", () -> toggleTerminalDrawer());
        statEnc      = plainStatus("UTF-8");
        statTheme    = statusPill(IDEIcons.THEME, (theme != null ? theme.name : ""), "Click to change theme", () -> showThemeStatusPopup(statTheme));
        statUser     = plainStatus(IDEIcons.USER, loggedInUser != null ? loggedInUser : "");

        statusBarRight.add(statLang);
        statusBarRight.add(divider());
        statusBarRight.add(statTerminal);
        statusBarRight.add(divider());
        statusBarRight.add(statEnc);
        statusBarRight.add(divider());
        statusBarRight.add(statTheme);
        statusBarRight.add(divider());
        statusBarRight.add(statUser);

        statusBar.add(statusBarLeft, BorderLayout.WEST);
        statusBar.add(statusBarRight, BorderLayout.EAST);
    }

    private void showLanguagePopup(Component anchor) {
        JPopupMenu pm = new JPopupMenu();
        for (String lang : SUPPORTED_LANGUAGES) {
            JMenuItem mi = new JMenuItem(lang, IDEIcons.forLanguage(lang));
            mi.setIconTextGap(8);
            mi.addActionListener(e -> setLanguage(lang));
            pm.add(mi);
        }
        pm.show(anchor, 0, -pm.getPreferredSize().height);
    }

    private void showThemeStatusPopup(Component anchor) {
        JPopupMenu pm = new JPopupMenu();
        for (int i = 0; i < THEMES.size(); i++) {
            final int idx = i;
            JMenuItem mi = new JMenuItem(THEMES.get(i).name, IDEIcons.forTheme(THEMES.get(i).name));
            mi.setIconTextGap(8);
            mi.addActionListener(e -> {
                themeItems[idx].setSelected(true);
                applyTheme(THEMES.get(idx));
                sound.play(SoundManager.THEME);
            });
            pm.add(mi);
        }
        pm.show(anchor, 0, -pm.getPreferredSize().height);
    }

    private JLabel plainStatus(Icon icon, String text) {
        JLabel l = new JLabel(text);
        if (icon != null) {
            l.setIcon(icon);
            l.setIconTextGap(5);
        }
        l.setFont(uiFont(Font.PLAIN, 11));
        l.setBorder(new EmptyBorder(4, 10, 4, 10));
        return l;
    }

    private JLabel plainStatus(String text) {
        return plainStatus(null, text);
    }

    private JLabel statusPill(Icon icon, String text, String tip, Runnable onClick) {
        JLabel l = new JLabel(text);
        if (icon != null) {
            l.setIcon(icon);
            l.setIconTextGap(5);
        }
        l.setFont(uiFont(Font.PLAIN, 11));
        l.setBorder(new EmptyBorder(3, 8, 3, 8));
        l.setToolTipText(tip);
        if (onClick != null) {
            l.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            l.addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) { onClick.run(); }
                @Override public void mouseEntered(MouseEvent e) {
                    l.setOpaque(true);
                    l.setBackground(mix(theme.chromeBg, theme.accent, 0.22f));
                    l.repaint();
                }
                @Override public void mouseExited(MouseEvent e) {
                    l.setOpaque(false);
                    l.setBackground(theme.chromeBg);
                    l.repaint();
                }
            });
        }
        return l;
    }

    private JLabel statusPill(String text, String tip, Runnable onClick) {
        return statusPill(null, text, tip, onClick);
    }

    private JComponent divider() {
        JPanel d = new JPanel();
        d.setOpaque(false);
        d.setPreferredSize(new Dimension(1, 14));
        d.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, theme.border));
        return d;
    }

    /* =======================================================================
     * 15. MENU BAR
     * ======================================================================= */

    private JMenuItem menuItem(Icon icon, String text, String command, int keyCode, int modifiers) {
        JMenuItem item = new JMenuItem(text);
        if (icon != null) {
            item.setIcon(icon);
            item.setIconTextGap(8);
        }
        item.setActionCommand(command);
        if (keyCode != 0) item.setAccelerator(KeyStroke.getKeyStroke(keyCode, modifiers));
        item.addActionListener(this);
        item.setBorder(new EmptyBorder(4, 8, 4, 8));
        item.setOpaque(true);
        return item;
    }

    private JMenuItem menuItem(String text, String command, int keyCode, int modifiers) {
        return menuItem(null, text, command, keyCode, modifiers);
    }

    private JCheckBoxMenuItem checkItem(Icon icon, String text, String command, boolean selected) {
        JCheckBoxMenuItem item = new JCheckBoxMenuItem(text, selected);
        if (icon != null) {
            item.setIcon(icon);
            item.setIconTextGap(8);
        }
        item.setActionCommand(command);
        item.addActionListener(this);
        item.setBorder(new EmptyBorder(4, 8, 4, 8));
        item.setOpaque(true);
        return item;
    }

    private JCheckBoxMenuItem checkItem(String text, String command, boolean selected) {
        return checkItem(null, text, command, selected);
    }

    private void buildMenuBar() {
        menuBar = new FlatMenuBar(theme);
        menuBar.setOpaque(true);

        fileMenu      = styledMenu("File", 'F');
        editMenu      = styledMenu("Edit", 'E');
        selectionMenu = styledMenu("Selection", 'S');
        viewMenu      = styledMenu("View", 'V');
        runMenu       = styledMenu("Run", 'R');
        codeMenu      = styledMenu("Code", 'C');
        themeMenu     = styledMenu("Themes", 'T');
        helpMenu      = styledMenu("Help", 'H');

        // File Menu
        newFile        = menuItem(IDEIcons.NEW, "New File",          "new",        KeyEvent.VK_N, CTRL);
        newWindow      = menuItem(IDEIcons.NEW, "New Window",        "newWindow",  KeyEvent.VK_N, CTRL_SHIFT);
        openFile       = menuItem(IDEIcons.OPEN, "Open File...",      "open",       KeyEvent.VK_O, CTRL);
        saveFile       = menuItem(IDEIcons.SAVE, "Save",              "save",       KeyEvent.VK_S, CTRL);
        saveAsFile     = menuItem(IDEIcons.SAVE, "Save As...",        "saveAs",     KeyEvent.VK_S, CTRL_SHIFT);
        clearRecent    = menuItem(IDEIcons.CLEAR, "Clear Recent Files","clearRecent",0, 0);
        exitApp        = menuItem(IDEIcons.SIGN_OUT, "Exit",          "exit",       KeyEvent.VK_Q, CTRL);
        recentMenu     = styledMenu("Open Recent", (char) 0);
        recentMenu.setIcon(IDEIcons.OPEN);
        recentMenu.setIconTextGap(8);

        fileMenu.add(newFile); fileMenu.add(newWindow); fileMenu.addSeparator();
        fileMenu.add(openFile); fileMenu.add(recentMenu); fileMenu.addSeparator();
        fileMenu.add(saveFile); fileMenu.add(saveAsFile); fileMenu.addSeparator();
        fileMenu.add(exitApp);
        rebuildRecentMenu();

        // Edit Menu
        undoItem   = menuItem(IDEIcons.UNDO, "Undo",       "undo",      KeyEvent.VK_Z, CTRL);
        redoItem   = menuItem(IDEIcons.REDO, "Redo",       "redo",      KeyEvent.VK_Y, CTRL);
        cut        = menuItem(IDEIcons.CUT, "Cut",        "cut",       KeyEvent.VK_X, CTRL);
        copy       = menuItem(IDEIcons.COPY, "Copy",       "copy",      KeyEvent.VK_C, CTRL);
        paste      = menuItem(IDEIcons.PASTE, "Paste",      "paste",     KeyEvent.VK_V, CTRL);
        deleteItem = menuItem(IDEIcons.CLEAR, "Delete",     "delete",    0, 0);
        selectAll  = menuItem(IDEIcons.CHECK, "Select All", "selectAll", KeyEvent.VK_A, CTRL);
        findItem    = menuItem(IDEIcons.FIND, "Find...",     "find",    KeyEvent.VK_F, CTRL);
        replaceItem = menuItem(IDEIcons.DUPLICATE, "Replace...",  "replace", KeyEvent.VK_H, CTRL);

        editMenu.add(undoItem); editMenu.add(redoItem); editMenu.addSeparator();
        editMenu.add(cut); editMenu.add(copy); editMenu.add(paste); editMenu.add(deleteItem);
        editMenu.addSeparator();
        editMenu.add(findItem); editMenu.add(replaceItem);
        editMenu.addSeparator();
        editMenu.add(selectAll);

        // Selection Menu
        duplicateLine = menuItem(IDEIcons.DUPLICATE, "Duplicate Line",      "duplicateLine", KeyEvent.VK_D, CTRL);
        moveLineUp    = menuItem(IDEIcons.UP, "Move Line Up",        "moveLineUp",    KeyEvent.VK_UP, ALT);
        moveLineDown  = menuItem(IDEIcons.DOWN, "Move Line Down",      "moveLineDown",  KeyEvent.VK_DOWN, ALT);
        toggleComment = menuItem(IDEIcons.COMMENT, "Toggle Line Comment", "toggleComment", KeyEvent.VK_SLASH, CTRL);

        selectionMenu.add(duplicateLine);
        selectionMenu.add(moveLineUp);
        selectionMenu.add(moveLineDown);
        selectionMenu.addSeparator();
        selectionMenu.add(toggleComment);

        // View Menu
        terminalItem     = checkItem(IDEIcons.TERMINAL, "Terminal / Output",          "toggleTerminal", false);
        terminalItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_BACK_QUOTE, CTRL));
        wordWrap         = checkItem(IDEIcons.WRAP, "Word Wrap",                  "wordWrap",     true);
        wordWrap.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Z, ALT));
        lineNumbersItem  = checkItem(IDEIcons.LINE_NUMBERS, "Line Numbers",               "lineNumbers",  true);
        syntaxItem       = checkItem(null, "Syntax Highlighting",        "syntax",       syntaxEnabled);
        rainbowItem      = checkItem(null, "Rainbow Brackets",           "rainbow",      rainbowBracketsEnabled);
        currentLineItem  = checkItem(null, "Highlight Current Line",     "currentLine",  currentLineEnabled);
        bracketMatchItem = checkItem(null, "Highlight Matching Bracket", "bracketMatch", bracketMatchEnabled);
        statusBarItem    = checkItem(null, "Status Bar",                 "statusBar",    true);
        showWelcomeItem  = menuItem(IDEIcons.NEW, "Show Welcome Page",           "welcome",      0, 0);

        appearanceMenu = styledMenu("Appearance", (char) 0);
        appearanceMenu.setIcon(IDEIcons.THEME);
        appearanceMenu.setIconTextGap(8);
        chooseFont = menuItem(null, "Choose Font...", "chooseFont", 0, 0);
        fontSizeMenu = styledMenu("Font Size", (char) 0);
        fontSizeItems = new JMenuItem[FONT_SIZES.length];
        ButtonGroup fsGroup = new ButtonGroup();
        for (int i = 0; i < FONT_SIZES.length; i++) {
            final int sz = FONT_SIZES[i];
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(String.valueOf(sz) + " pt", sz == fontSize);
            item.setActionCommand("setFontSize_" + sz);
            item.addActionListener(this);
            item.setBorder(new EmptyBorder(4, 8, 4, 8));
            fsGroup.add(item);
            fontSizeMenu.add(item);
            fontSizeItems[i] = item;
        }
        zoomIn    = menuItem(IDEIcons.ZOOM_IN, "Zoom In",     "zoomIn",    KeyEvent.VK_EQUALS, CTRL);
        zoomOut   = menuItem(IDEIcons.ZOOM_OUT, "Zoom Out",    "zoomOut",   KeyEvent.VK_MINUS,  CTRL);
        zoomReset = menuItem(IDEIcons.RESET, "Reset Zoom",  "zoomReset", KeyEvent.VK_0,      CTRL);

        tabSizeMenu = styledMenu("Tab Size", (char) 0);
        ButtonGroup tabGroup = new ButtonGroup();
        for (int s : new int[]{2, 4, 8}) {
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(s + " spaces", s == tabSize);
            item.setActionCommand("tabSize_" + s);
            item.addActionListener(this);
            item.setBorder(new EmptyBorder(4, 8, 4, 8));
            tabGroup.add(item);
            tabSizeMenu.add(item);
        }

        appearanceMenu.add(chooseFont);
        appearanceMenu.add(fontSizeMenu);
        appearanceMenu.addSeparator();
        appearanceMenu.add(zoomIn); appearanceMenu.add(zoomOut); appearanceMenu.add(zoomReset);
        appearanceMenu.addSeparator();
        appearanceMenu.add(tabSizeMenu);

        viewMenu.add(terminalItem);
        viewMenu.addSeparator();
        viewMenu.add(wordWrap);
        viewMenu.add(lineNumbersItem);
        viewMenu.add(syntaxItem);
        viewMenu.add(rainbowItem);
        viewMenu.add(currentLineItem);
        viewMenu.add(bracketMatchItem);
        viewMenu.addSeparator();
        viewMenu.add(appearanceMenu);
        viewMenu.add(statusBarItem);
        viewMenu.addSeparator();
        viewMenu.add(showWelcomeItem);

        // Run Menu
        runCodeItem       = menuItem(IDEIcons.RUN, "Run Code",       "runCode",       KeyEvent.VK_F5, 0);
        stopCodeItem      = menuItem(IDEIcons.STOP, "Stop Execution", "stopCode",      KeyEvent.VK_F5, InputEvent.SHIFT_DOWN_MASK);
        clearTerminalItem = menuItem(IDEIcons.CLEAR, "Clear Terminal Output", "clearTerminal", 0, 0);

        langMenu = styledMenu("Language Mode", (char) 0);
        langMenu.setIcon(IDEIcons.forLanguage("Java"));
        langMenu.setIconTextGap(8);
        ButtonGroup langGroup = new ButtonGroup();
        for (String lang : SUPPORTED_LANGUAGES) {
            JRadioButtonMenuItem mi = new JRadioButtonMenuItem(lang, IDEIcons.forLanguage(lang), lang.equals(getEffectiveLanguage()));
            mi.setIconTextGap(8);
            mi.setActionCommand("setLang_" + lang);
            mi.addActionListener(this);
            mi.setBorder(new EmptyBorder(4, 8, 4, 8));
            langGroup.add(mi);
            langMenu.add(mi);
        }

        runMenu.add(runCodeItem);
        runMenu.add(stopCodeItem);
        runMenu.addSeparator();
        runMenu.add(clearTerminalItem);
        runMenu.addSeparator();
        runMenu.add(langMenu);

        // Code Menu
        gotoLine        = menuItem(IDEIcons.GOTO, "Go To Line...",        "gotoLine",    KeyEvent.VK_G, CTRL);
        autoIndentItem  = checkItem(null, "Auto-Indent",        "autoIndent",  autoIndentEnabled);
        autoCloseItem   = checkItem(null, "() Auto-Close Pairs",   "autoClose",   autoCloseEnabled);
        timeDate        = menuItem(null, "Insert Date & Time",  "timeDate",    KeyEvent.VK_D, CTRL_SHIFT);
        insertImageItem = menuItem(IDEIcons.IMAGE, "Insert Image Asset...", "insertImage", KeyEvent.VK_I, CTRL_SHIFT);
        insertFilePath  = menuItem(IDEIcons.OPEN, "Insert File Path",    "insertPath",  KeyEvent.VK_P, CTRL_SHIFT);

        codeMenu.add(gotoLine);
        codeMenu.addSeparator();
        codeMenu.add(autoIndentItem);
        codeMenu.add(autoCloseItem);
        codeMenu.addSeparator();
        codeMenu.add(timeDate);
        codeMenu.add(insertImageItem);
        codeMenu.add(insertFilePath);

        // Themes Menu
        themeGroup = new ButtonGroup();
        themeItems = new JRadioButtonMenuItem[THEMES.size()];
        for (int i = 0; i < THEMES.size(); i++) {
            Theme t = THEMES.get(i);
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(t.name, IDEIcons.forTheme(t.name));
            item.setIconTextGap(8);
            item.setActionCommand("theme_" + i);
            item.addActionListener(this);
            item.setSelected(t == theme);
            item.setBorder(new EmptyBorder(4, 8, 4, 8));
            themeGroup.add(item);
            themeItems[i] = item;
            themeMenu.add(item);
        }
        themeMenu.addSeparator();
        nextThemeItem = menuItem(IDEIcons.THEME, "Next Theme", "nextTheme", KeyEvent.VK_F12, 0);
        themeMenu.add(nextThemeItem);

        // Help Menu
        about = menuItem(IDEIcons.NEW, "About Luminary IDE", "about", 0, 0);
        soundItem    = checkItem(null, "Sound Effects",   "sound",    soundEnabled);
        keyClickItem = checkItem(null, "Key Click Sound", "keyClick", keyClickEnabled);
        volumeItem   = menuItem(null, "Sound Volume...",    "volume",   0, 0);

        helpMenu.add(about);
        helpMenu.addSeparator();
        helpMenu.add(soundItem);
        helpMenu.add(keyClickItem);
        helpMenu.add(volumeItem);

        menuBar.add(fileMenu);
        menuBar.add(editMenu);
        menuBar.add(selectionMenu);
        menuBar.add(viewMenu);
        menuBar.add(runMenu);
        menuBar.add(codeMenu);
        menuBar.add(themeMenu);
        menuBar.add(helpMenu);

        menuBar.add(Box.createHorizontalGlue());

        signOutBtn = new JButton("Sign Out");
        signOutBtn.setIcon(IDEIcons.SIGN_OUT);
        signOutBtn.setIconTextGap(6);
        signOutBtn.setFont(uiFont(Font.PLAIN, 12));
        signOutBtn.setFocusable(false);
        signOutBtn.setOpaque(true);
        signOutBtn.setContentAreaFilled(true);
        signOutBtn.setBorderPainted(false);
        signOutBtn.setBorder(new EmptyBorder(5, 14, 5, 14));
        signOutBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        signOutBtn.setToolTipText("Sign out of current account session");
        signOutBtn.setBackground(theme.chromeBg);
        signOutBtn.setForeground(theme.dark ? Color.WHITE : Color.BLACK);
        signOutBtn.addActionListener(e -> signOut());
        installSmoothHover(signOutBtn, () -> theme.chromeBg, () -> mix(theme.chromeBg, theme.accent, 0.22f));
        menuBar.add(signOutBtn);
    }

    private JMenu styledMenu(String text, char mnemonic) {
        JMenu m = new JMenu(text);
        if (mnemonic != 0) m.setMnemonic(mnemonic);
        m.setBorder(new EmptyBorder(4, 8, 4, 8));
        m.setOpaque(true);
        return m;
    }

    /* =======================================================================
     * 16. TOOLBAR
     * ======================================================================= */

    private void buildToolBar() {
        toolBar = new JToolBar();
        toolBar.setFloatable(false);
        toolBar.setRollover(true);
        toolBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, theme.border),
                new EmptyBorder(3, 8, 3, 8)));
        toolBar.setBackground(theme.panelBg);

        // Group 1: Core File
        toolBar.add(tbButton(IDEIcons.NEW, "New",  "new",  "New File (Ctrl+N)"));
        toolBar.add(tbButton(IDEIcons.OPEN, "Open", "open", "Open File (Ctrl+O)"));
        toolBar.add(tbButton(IDEIcons.SAVE, "Save", "save", "Save File (Ctrl+S)"));
        toolBar.add(tbSeparator());

        // Group 2: Execution / Run
        tbRun = new IDEButton(IDEIcons.RUN, "Run", () -> theme);
        tbRun.setCustomAccent(new Color(0x22C55E)); // Emerald
        tbRun.setToolTipText("Run Current Code (F5 / Ctrl+R)");
        tbRun.addActionListener(e -> runCurrentCode());
        toolBar.add(tbRun);
        toolbarButtons.add(tbRun);

        tbStop = new IDEButton(IDEIcons.STOP, "Stop", () -> theme);
        tbStop.setCustomAccent(new Color(0xEF4444)); // Crimson
        tbStop.setToolTipText("Stop Running Process (Shift+F5)");
        tbStop.addActionListener(e -> stopRunningCode());
        toolBar.add(tbStop);
        toolbarButtons.add(tbStop);

        langCombo = new IDEComboBox<>(SUPPORTED_LANGUAGES, () -> theme, IDEIcons::forLanguage);
        langCombo.setSelectedItem(getEffectiveLanguage());
        langCombo.setPreferredSize(new Dimension(135, 28));
        langCombo.setMaximumSize(new Dimension(145, 28));
        langCombo.setToolTipText("Select Language Mode");
        langCombo.addActionListener(e -> {
            String selected = (String) langCombo.getSelectedItem();
            if (selected != null && !selected.equals(currentLanguage)) {
                setLanguage(selected);
            }
        });
        toolBar.add(langCombo);
        toolBar.add(tbSeparator());

        // Group 3: Search & Navigation
        toolBar.add(tbButton(IDEIcons.FIND, "Find",  "find",     "Find & Replace (Ctrl+F)"));
        toolBar.add(tbButton(IDEIcons.GOTO, "Go to", "gotoLine", "Go to Line (Ctrl+G)"));
        toolBar.add(tbSeparator());

        // Group 4: Code Tools
        toolBar.add(tbButton(IDEIcons.COMMENT, "Comment",   "toggleComment", "Toggle Line Comment (Ctrl+/)"));
        toolBar.add(tbButton(IDEIcons.DUPLICATE, "Duplicate", "duplicateLine", "Duplicate Line (Ctrl+D)"));
        toolBar.add(tbButton(IDEIcons.UP, "Up",         "moveLineUp",    "Move Line Up (Alt+Up)"));
        toolBar.add(tbButton(IDEIcons.DOWN, "Down",       "moveLineDown",  "Move Line Down (Alt+Down)"));
        toolBar.add(tbSeparator());

        // Group 5: View Toggles
        tbWrap        = tbToggle(IDEIcons.WRAP, "Wrap",     "wordWrap",       true,  "Toggle Word Wrap (Alt+Z)");
        tbLineNumbers = tbToggle(IDEIcons.LINE_NUMBERS, "Line #",   "lineNumbers",    true,  "Toggle Line Numbers");
        tbTerminal    = tbToggle(IDEIcons.TERMINAL, "Terminal", "toggleTerminal", false, "Toggle Output Terminal (Ctrl+`)");
        toolBar.add(tbWrap);
        toolBar.add(tbLineNumbers);
        toolBar.add(tbTerminal);
        toolBar.add(tbSeparator());

        // Group 6: Zoom
        toolBar.add(tbButton(IDEIcons.ZOOM_OUT, "Zoom -", "zoomOut",   "Zoom Out (Ctrl+-)"));
        fontSizeLabel = new JLabel(fontSize + " pt");
        fontSizeLabel.setFont(uiFont(Font.BOLD, 12));
        fontSizeLabel.setForeground(theme.dark ? Color.WHITE : Color.BLACK);
        fontSizeLabel.setBorder(new EmptyBorder(0, 6, 0, 6));
        fontSizeLabel.setHorizontalAlignment(SwingConstants.CENTER);
        fontSizeLabel.setPreferredSize(new Dimension(50, 28));
        toolBar.add(fontSizeLabel);
        toolBar.add(tbButton(IDEIcons.ZOOM_IN, "Zoom +", "zoomIn", "Zoom In (Ctrl++)"));
        toolBar.add(tbButton(IDEIcons.RESET, "Reset", "zoomReset", "Reset Zoom to 14 pt (Ctrl+0)"));
        toolBar.add(tbSeparator());

        // Group 7: Themes
        themeCombo = new IDEComboBox<>(THEMES.stream().map(th -> th.name).toArray(String[]::new), () -> theme, IDEIcons::forTheme);
        themeCombo.setSelectedItem(theme.name);
        themeCombo.setPreferredSize(new Dimension(160, 28));
        themeCombo.setMaximumSize(new Dimension(175, 28));
        themeCombo.setToolTipText("Switch Color Theme");
        themeCombo.addActionListener(e -> {
            if (syncingThemeCombo) return;
            String name = (String) themeCombo.getSelectedItem();
            for (int i = 0; i < THEMES.size(); i++) {
                if (THEMES.get(i).name.equals(name)) {
                    themeItems[i].setSelected(true);
                    applyTheme(THEMES.get(i));
                    sound.play(SoundManager.THEME);
                    break;
                }
            }
        });
        toolBar.add(themeCombo);

        applyToolbarTheme(theme);
    }

    private IDEButton tbButton(Icon icon, String text, String cmd, String tip) {
        IDEButton b = new IDEButton(icon, text, () -> theme);
        b.setActionCommand(cmd);
        b.setToolTipText(tip);
        b.addActionListener(this);
        toolbarButtons.add(b);
        return b;
    }

    private IDEButton tbButton(String text, String cmd, String tip) {
        return tbButton(null, text, cmd, tip);
    }

    private IDEToggleButton tbToggle(Icon icon, String text, String cmd, boolean selected, String tip) {
        IDEToggleButton b = new IDEToggleButton(icon, text, () -> theme);
        b.setActionCommand(cmd);
        b.setToolTipText(tip);
        b.setSelected(selected);
        b.addActionListener(this);
        toolbarButtons.add(b);
        return b;
    }

    private IDEToggleButton tbToggle(String text, String cmd, boolean selected, String tip) {
        return tbToggle(null, text, cmd, selected, tip);
    }

    private Component tbSeparator() {
        JSeparator sep = new JSeparator(SwingConstants.VERTICAL);
        sep.setPreferredSize(new Dimension(1, 20));
        sep.setMaximumSize(new Dimension(1, 20));
        sep.setForeground(theme.border);
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setOpaque(false);
        wrap.setBorder(new EmptyBorder(0, 4, 0, 4));
        wrap.add(sep, BorderLayout.CENTER);
        wrap.setMaximumSize(new Dimension(9, 20));
        return wrap;
    }

    private void applyToolbarTheme(Theme t) {
        if (toolBar == null) return;
        boolean isDark = t.dark;
        Color textCol = isDark ? Color.WHITE : Color.BLACK;
        toolBar.setBackground(t.panelBg);
        toolBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, t.border),
                new EmptyBorder(3, 8, 3, 8)));
        for (Component comp : toolBar.getComponents()) {
            if (comp instanceof IDEButton) {
                ((IDEButton) comp).setForeground(textCol);
            } else if (comp instanceof IDEToggleButton) {
                ((IDEToggleButton) comp).setForeground(textCol);
            } else if (comp instanceof JComboBox) {
                comp.setBackground(isDark ? t.bg : Color.WHITE);
                comp.setForeground(textCol);
                comp.repaint();
            } else if (comp instanceof JLabel) {
                comp.setForeground(textCol);
            } else if (comp instanceof JSeparator) {
                comp.setForeground(t.border);
            }
        }
        if (tbRun != null) tbRun.setForeground(textCol);
        if (tbStop != null) tbStop.setForeground(textCol);
        toolBar.revalidate();
        toolBar.repaint();
    }

    private void updateFontSizeLabel() {
        if (fontSizeLabel != null) {
            fontSizeLabel.setText(fontSize + " pt");
            fontSizeLabel.setForeground(theme.dark ? Color.WHITE : Color.BLACK);
        }
        if (fontSizeItems != null) {
            for (int i = 0; i < FONT_SIZES.length; i++)
                if (FONT_SIZES[i] == fontSize) { fontSizeItems[i].setSelected(true); break; }
        }
    }

    /* =======================================================================
     * 17. RECENT FILES
     * ======================================================================= */

    private void rebuildRecentMenu() {
        recentMenu.removeAll();
        String[] recent = getRecentFiles();
        if (recent.length == 0) {
            JMenuItem empty = new JMenuItem("(no recent files)");
            empty.setEnabled(false);
            empty.setBorder(new EmptyBorder(4, 8, 4, 8));
            recentMenu.add(empty);
            return;
        }
        for (String path : recent) {
            JMenuItem item = new JMenuItem(path);
            item.setBorder(new EmptyBorder(4, 8, 4, 8));
            item.addActionListener(e -> {
                File f = new File(path);
                if (!f.exists()) {
                    JOptionPane.showMessageDialog(frame, "File no longer exists:\n" + path,
                            "Open Recent", JOptionPane.WARNING_MESSAGE);
                    sound.play(SoundManager.ERROR);
                    removeRecentFile(path); rebuildRecentMenu();
                    if (welcomePanel != null) welcomePanel.refresh();
                    return;
                }
                if (!confirmSaveIfDirty()) return;
                loadFile(f);
            });
            recentMenu.add(item);
        }
        recentMenu.addSeparator();
        recentMenu.add(clearRecent);
    }

    private String[] getRecentFiles() {
        String joined = prefs.get(PREF_RECENT, "");
        if (joined.isEmpty()) return new String[0];
        return joined.split("\u001F");
    }

    private void addRecentFile(String path) {
        ArrayList<String> list = new ArrayList<>(Arrays.asList(getRecentFiles()));
        list.remove(path); list.add(0, path);
        while (list.size() > 10) list.remove(list.size() - 1);
        prefs.put(PREF_RECENT, String.join("\u001F", list));
        rebuildRecentMenu();
        if (welcomePanel != null) welcomePanel.refresh();
    }

    private void removeRecentFile(String path) {
        ArrayList<String> list = new ArrayList<>(Arrays.asList(getRecentFiles()));
        list.remove(path);
        prefs.put(PREF_RECENT, String.join("\u001F", list));
    }

    private void clearRecentFiles() {
        prefs.put(PREF_RECENT, "");
        rebuildRecentMenu();
        if (welcomePanel != null) welcomePanel.refresh();
    }

    /* =======================================================================
     * 18. SMART TYPING / DROP TARGET
     * ======================================================================= */

    private void installSmartTyping() {
        textArea.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER && autoIndentEnabled) {
                    SwingUtilities.invokeLater(() -> {
                        try {
                            int caret = textArea.getCaretPosition();
                            int line  = getLineOfOffset(caret);
                            int prevLine = line - 1;
                            if (prevLine >= 0) {
                                int ps = getLineStartOffset(prevLine);
                                int pe = getLineEndOffset(prevLine);
                                String pt = textArea.getText(ps, pe - ps);
                                StringBuilder indent = new StringBuilder();
                                for (char ch : pt.toCharArray()) {
                                    if (ch == ' ' || ch == '\t') indent.append(ch); else break;
                                }
                                if (indent.length() > 0)
                                    textArea.getDocument().insertString(caret, indent.toString(), null);
                            }
                        } catch (BadLocationException ignored) { }
                    });
                }
                if (e.getKeyCode() == KeyEvent.VK_TAB) {
                    e.consume();
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < tabSize; i++) sb.append(' ');
                    textArea.replaceSelection(sb.toString());
                }
            }
            @Override public void keyTyped(KeyEvent e) {
                char ch = e.getKeyChar();
                if (keyClickEnabled && ch >= 32 && ch != 127) sound.play(SoundManager.KEY);
                if (!autoCloseEnabled) return;

                if (ch == ')' || ch == ']' || ch == '}' || ch == '"' || ch == '\'') {
                    try {
                        int pos = textArea.getCaretPosition();
                        Document d = textArea.getDocument();
                        if (pos < d.getLength() && d.getText(pos, 1).charAt(0) == ch) {
                            e.consume();
                            textArea.setCaretPosition(pos + 1);
                            return;
                        }
                    } catch (BadLocationException ignored) { }
                }

                if (ch == '\'' || ch == '"') {
                    try {
                        int pos = textArea.getCaretPosition();
                        if (pos > 0) {
                            char prev = textArea.getDocument().getText(pos - 1, 1).charAt(0);
                            if (Character.isLetterOrDigit(prev)) return;
                        }
                    } catch (BadLocationException ignored) { }
                }

                String closing = closingFor(ch);
                if (closing != null && textArea.getSelectedText() == null) {
                    e.consume();
                    int pos = textArea.getCaretPosition();
                    try {
                        textArea.getDocument().insertString(pos, String.valueOf(ch) + closing, null);
                        textArea.setCaretPosition(pos + 1);
                    } catch (BadLocationException ignored) { }
                }
            }
        });
    }

    private String closingFor(char open) {
        switch (open) {
            case '(': return ")";
            case '[': return "]";
            case '{': return "}";
            case '\'': return "'";
            case '"': return "\"";
            default: return null;
        }
    }

    private void installDropTarget() {
        textArea.setTransferHandler(new TransferHandler() {
            @Override public int getSourceActions(JComponent c) {
                return COPY_OR_MOVE;
            }

            @Override protected Transferable createTransferable(JComponent c) {
                String sel = textArea.getSelectedText();
                if (sel == null || sel.isEmpty()) return null;
                return new StringSelection(sel);
            }

            @Override protected void exportDone(JComponent source,
                                                Transferable data, int action) {
                if (action == MOVE) textArea.replaceSelection("");
            }

            @Override public boolean canImport(TransferSupport s) {
                return s.isDataFlavorSupported(DataFlavor.javaFileListFlavor)
                        || s.isDataFlavorSupported(DataFlavor.imageFlavor)
                        || s.isDataFlavorSupported(DataFlavor.stringFlavor);
            }

            @Override public boolean importData(TransferSupport s) {
                try {
                    if (s.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                        @SuppressWarnings("unchecked")
                        List<File> files = (List<File>) s.getTransferable()
                                .getTransferData(DataFlavor.javaFileListFlavor);
                        if (files != null && !files.isEmpty()) {
                            boolean anyImage = false;
                            for (File f : files)
                                if (isImageExt(f)) { insertImageFromFile(f, false); anyImage = true; }
                            if (anyImage) return true;
                            File first = files.get(0);
                            if (first.isFile() && confirmSaveIfDirty()) {
                                loadFile(first);
                                return true;
                            }
                        }
                    }
                    if (s.isDataFlavorSupported(DataFlavor.imageFlavor)) {
                        Object o = s.getTransferable().getTransferData(DataFlavor.imageFlavor);
                        if (o instanceof Image) { insertImage((Image) o, 600); return true; }
                    }
                    if (s.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                        String str = (String) s.getTransferable()
                                .getTransferData(DataFlavor.stringFlavor);
                        if (str == null) return false;
                        textArea.replaceSelection(str);
                        return true;
                    }
                } catch (Throwable t) {
                    JOptionPane.showMessageDialog(frame,
                            "Could not import dropped data:\n" + t,
                            "Drop Error", JOptionPane.ERROR_MESSAGE);
                    sound.play(SoundManager.ERROR);
                }
                return false;
            }
        });
    }

    /* =======================================================================
     * 19. SYNTAX HIGHLIGHTING / BRACKETS / CURRENT LINE
     * ======================================================================= */

    private void rebuildStyles() {
        stylePlain = new SimpleAttributeSet();
        StyleConstants.setForeground(stylePlain, theme.fg);
        StyleConstants.setFontFamily(stylePlain, fontFamily);
        StyleConstants.setFontSize(stylePlain, fontSize);

        styleKeyword = new SimpleAttributeSet();
        StyleConstants.setForeground(styleKeyword, theme.keyword != null ? theme.keyword : theme.accent);
        StyleConstants.setBold(styleKeyword, true);

        styleControlKeyword = new SimpleAttributeSet();
        StyleConstants.setForeground(styleControlKeyword, theme.controlKeyword != null ? theme.controlKeyword : theme.keyword);
        StyleConstants.setBold(styleControlKeyword, true);

        styleMethod = new SimpleAttributeSet();
        StyleConstants.setForeground(styleMethod, theme.method != null ? theme.method : theme.annotation);

        styleConstant = new SimpleAttributeSet();
        StyleConstants.setForeground(styleConstant, theme.constant != null ? theme.constant : theme.number);

        styleType = new SimpleAttributeSet();
        StyleConstants.setForeground(styleType, theme.type != null ? theme.type : theme.accent);

        styleString = new SimpleAttributeSet();
        StyleConstants.setForeground(styleString, theme.string);

        styleComment = new SimpleAttributeSet();
        StyleConstants.setForeground(styleComment, theme.comment);
        StyleConstants.setItalic(styleComment, true);

        styleNumber = new SimpleAttributeSet();
        StyleConstants.setForeground(styleNumber, theme.number);

        styleAnnotation = new SimpleAttributeSet();
        StyleConstants.setForeground(styleAnnotation, theme.annotation);

        styleOperator = new SimpleAttributeSet();
        StyleConstants.setForeground(styleOperator, theme.operator != null ? theme.operator : theme.fg);
    }

    private void applyParagraphAttrs() {
        if (textArea == null) return;
        StyledDocument doc = textArea.getStyledDocument();
        if (doc == null) return;
        boolean undoDetached = false;
        docLock = true;
        try {
            FontMetrics fm = textArea.getFontMetrics(textArea.getFont());
            if (fm == null) return;
            int charW = fm.charWidth('m');
            if (charW <= 0) charW = fm.charWidth(' ') * 2;
            if (charW <= 0) return;
            int tabPx = charW * tabSize;
            TabStop[] stops = new TabStop[200];
            for (int i = 0; i < stops.length; i++) stops[i] = new TabStop((i + 1) * tabPx);
            SimpleAttributeSet attrs = new SimpleAttributeSet();
            StyleConstants.setTabSet(attrs, new TabSet(stops));
            StyleConstants.setLineSpacing(attrs, 0.32f);
            doc.removeUndoableEditListener(undoManager);
            undoDetached = true;
            int len = doc.getLength();
            doc.setParagraphAttributes(0, Math.max(len, 1), attrs, false);
        } catch (Throwable ignored) {
        } finally {
            if (undoDetached) {
                try { doc.addUndoableEditListener(undoManager); } catch (Throwable ignored) { }
            }
            docLock = false;
        }
    }

    private void highlightSyntax() {
        if (!syntaxEnabled || textArea == null) return;
        StyledDocument doc = textArea.getStyledDocument();
        if (doc == null) return;
        int len = doc.getLength();
        if (len == 0 || len > 250_000) return;
        if (stylePlain == null) rebuildStyles();

        docLock = true;
        boolean undoDetached = false;
        try {
            doc.removeUndoableEditListener(undoManager);
            undoDetached = true;
            ArrayList<int[]> icons = new ArrayList<>();
            collectIconRanges(doc.getDefaultRootElement(), icons);
            String text = doc.getText(0, len);
            applyStyleRange(doc, 0, len, stylePlain, icons);

            Pattern p = patternForLanguage(getEffectiveLanguage());
            if (p == null) return;

            Matcher m = p.matcher(text);
            while (m.find()) {
                int s = m.start(), l = m.end() - s;
                if (l <= 0 || s + l > doc.getLength()) continue;
                if (overlapsIcon(icons, s, s + l)) continue;
                AttributeSet st = null;
                if      (matchGroup(m, "comment")    && m.group("comment")    != null) st = styleComment;
                else if (matchGroup(m, "string")     && m.group("string")     != null) st = styleString;
                else if (matchGroup(m, "control")    && m.group("control")    != null) st = styleControlKeyword;
                else if (matchGroup(m, "keyword")    && m.group("keyword")    != null) st = styleKeyword;
                else if (matchGroup(m, "constant")   && m.group("constant")   != null) st = styleConstant;
                else if (matchGroup(m, "method")     && m.group("method")     != null) st = styleMethod;
                else if (matchGroup(m, "annotation") && m.group("annotation") != null) st = styleAnnotation;
                else if (matchGroup(m, "type")       && m.group("type")       != null) st = styleType;
                else if (matchGroup(m, "number")     && m.group("number")     != null) st = styleNumber;
                else if (matchGroup(m, "operator")   && m.group("operator")   != null) st = styleOperator;
                if (st != null) doc.setCharacterAttributes(s, l, st, true);
            }
            if (rainbowBracketsEnabled) applyRainbowBrackets(doc, text);
        } catch (Throwable ignored) {
        } finally {
            if (undoDetached) {
                try { doc.addUndoableEditListener(undoManager); } catch (Throwable ignored) { }
            }
            docLock = false;
        }
    }

    private void collectIconRanges(Element e, ArrayList<int[]> out) {
        if (e == null) return;
        if (e.isLeaf()) {
            AttributeSet as = e.getAttributes();
            if (as != null && StyleConstants.getIcon(as) != null)
                out.add(new int[]{ e.getStartOffset(), e.getEndOffset() });
            return;
        }
        for (int i = 0; i < e.getElementCount(); i++) collectIconRanges(e.getElement(i), out);
    }

    private boolean overlapsIcon(ArrayList<int[]> icons, int s, int e) {
        for (int[] r : icons) if (s < r[1] && e > r[0]) return true;
        return false;
    }

    private void applyStyleRange(StyledDocument doc, int start, int len,
                                 AttributeSet style, ArrayList<int[]> icons) {
        if (len <= 0) return;
        int end = start + len, cur = start;
        for (int[] r : icons) {
            if (r[1] <= cur) continue;
            if (r[0] >= end) break;
            int s = Math.max(cur, r[0]);
            if (s > cur) doc.setCharacterAttributes(cur, s - cur, style, true);
            cur = Math.max(cur, r[1]);
        }
        if (cur < end) doc.setCharacterAttributes(cur, end - cur, style, true);
    }

    private void applyRainbowBrackets(StyledDocument doc, String text) {
        int[] depth = new int[text.length()];
        int d = 0;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '(' || ch == '[' || ch == '{') depth[i] = d++;
            else if (ch == ')' || ch == ']' || ch == '}') depth[i] = Math.max(0, --d);
            else depth[i] = -1;
        }
        Color[] pal = { theme.bracket1, theme.bracket2, theme.bracket3, theme.bracket4 };
        for (int i = 0; i < text.length(); i++) {
            if (depth[i] < 0) continue;
            SimpleAttributeSet as = new SimpleAttributeSet();
            StyleConstants.setForeground(as, pal[depth[i] % pal.length]);
            doc.setCharacterAttributes(i, 1, as, true);
        }
    }

    private void highlightMatchingBracket() {
        Highlighter hl = textArea.getHighlighter();
        if (bracketTagA != null) { hl.removeHighlight(bracketTagA); bracketTagA = null; }
        if (bracketTagB != null) { hl.removeHighlight(bracketTagB); bracketTagB = null; }

        String text;
        try { text = textArea.getText(); } catch (Exception e) { return; }
        if (text.isEmpty()) return;
        int caret = textArea.getCaretPosition();
        int checkPos = -1;
        if (caret > 0 && isBracket(text.charAt(caret - 1))) checkPos = caret - 1;
        else if (caret < text.length() && isBracket(text.charAt(caret))) checkPos = caret;
        if (checkPos < 0) return;

        char ch = text.charAt(checkPos);
        char open, close; boolean forward;
        switch (ch) {
            case '(': open = '('; close = ')'; forward = true;  break;
            case '[': open = '['; close = ']'; forward = true;  break;
            case '{': open = '{'; close = '}'; forward = true;  break;
            case ')': open = '('; close = ')'; forward = false; break;
            case ']': open = '['; close = ']'; forward = false; break;
            case '}': open = '{'; close = '}'; forward = false; break;
            default: return;
        }
        int matchPos = findMatch(text, checkPos, open, close, forward);
        if (matchPos < 0) return;

        try {
            Highlighter.HighlightPainter painter =
                    new DefaultHighlighter.DefaultHighlightPainter(theme.accentSoft);
            bracketTagA = hl.addHighlight(checkPos, checkPos + 1, painter);
            bracketTagB = hl.addHighlight(matchPos, matchPos + 1, painter);
        } catch (BadLocationException ignored) { }
    }

    private boolean isBracket(char ch) {
        return ch == '(' || ch == ')' || ch == '[' || ch == ']' || ch == '{' || ch == '}';
    }

    private int findMatch(String text, int from, char open, char close, boolean forward) {
        int depth = 0;
        if (forward) {
            for (int i = from; i < text.length(); i++) {
                char ch = text.charAt(i);
                if (ch == open) depth++;
                else if (ch == close && --depth == 0) return i;
            }
        } else {
            for (int i = from; i >= 0; i--) {
                char ch = text.charAt(i);
                if (ch == close) depth++;
                else if (ch == open && --depth == 0) return i;
            }
        }
        return -1;
    }

    private void updateCurrentLineHighlight() {
        if (textArea != null) textArea.repaint();
    }

    /* =======================================================================
     * 20. ACTION DISPATCH
     * ======================================================================= */

    @Override
    public void actionPerformed(ActionEvent ev) {
        String cmd = ev.getActionCommand();
        if (cmd == null) return;

        if (cmd.startsWith("theme_")) {
            applyTheme(THEMES.get(Integer.parseInt(cmd.substring(6))));
            sound.play(SoundManager.THEME);
            return;
        }
        if (cmd.startsWith("tabSize_")) {
            tabSize = Integer.parseInt(cmd.substring(8));
            prefs.putInt(PREF_TABSIZE, tabSize);
            applyParagraphAttrs();
            return;
        }
        if (cmd.startsWith("setFontSize_")) {
            int newSize = Integer.parseInt(cmd.substring(12));
            if (newSize != fontSize) {
                fontSize = newSize;
                applyFontSize();
            }
            return;
        }
        if (cmd.startsWith("setLang_")) {
            setLanguage(cmd.substring(8));
            return;
        }

        switch (cmd) {
            case "runCode": runCurrentCode(); break;
            case "stopCode": stopRunningCode(); break;
            case "clearTerminal": clearTerminalOutput(); break;
            case "toggleTerminal": toggleTerminalDrawer(); break;
            case "new": newDocument(); break;
            case "newWindow": new LuminaryIDE(); break;
            case "open": openFile(); break;
            case "save": saveFile(); break;
            case "saveAs": saveFileAs(); break;
            case "signOut": signOut(); break;
            case "insertPath": insertFilePath(); break;
            case "clearRecent": clearRecentFiles(); break;
            case "exit": closeWindow(); break;
            case "nextTheme": cycleTheme(); break;
            case "welcome": showWelcomeCard(); break;

            case "undo": if (undoManager.canUndo()) undoManager.undo(); break;
            case "redo": if (undoManager.canRedo()) undoManager.redo(); break;
            case "cut": textArea.cut(); break;
            case "copy": textArea.copy(); break;
            case "paste": pasteWithImageSupport(); break;
            case "delete": textArea.replaceSelection(""); break;
            case "selectAll": textArea.selectAll(); break;
            case "duplicateLine": duplicateLine(); break;
            case "moveLineUp": moveLine(-1); break;
            case "moveLineDown": moveLine(+1); break;
            case "toggleComment": toggleLineComment(); break;
            case "gotoLine": goToLine(); break;
            case "find": case "replace": showFindPanel(); break;
            case "timeDate": insertDateTime(); break;
            case "insertImage": chooseAndInsertImage(); break;

            case "zoomIn": zoomBy(+2); break;
            case "zoomOut": zoomBy(-2); break;
            case "zoomReset": resetZoom(); break;
            case "chooseFont": chooseFont(); break;
            case "wordWrap":
                if (ev.getSource() == tbWrap) {
                    wordWrap.setSelected(tbWrap.isSelected());
                } else if (tbWrap != null) {
                    tbWrap.setSelected(wordWrap.isSelected());
                }
                toggleWordWrap();
                break;
            case "statusBar": toggleStatusBar(); break;
            case "lineNumbers":
                if (ev.getSource() == tbLineNumbers) {
                    lineNumbersItem.setSelected(tbLineNumbers.isSelected());
                } else if (tbLineNumbers != null) {
                    tbLineNumbers.setSelected(lineNumbersItem.isSelected());
                }
                toggleLineNumbers();
                break;
            case "autoIndent": toggleAutoIndent(); break;
            case "autoClose": toggleAutoClose(); break;
            case "bracketMatch": toggleBracketMatch(); break;
            case "syntax": toggleSyntax(); break;
            case "rainbow": toggleRainbow(); break;
            case "currentLine": toggleCurrentLine(); break;
            case "sound": toggleSound(); break;
            case "keyClick":
                keyClickEnabled = keyClickItem.isSelected();
                prefs.putBoolean(PREF_KEYCLICK, keyClickEnabled);
                break;
            case "volume": showVolumeDialog(); break;

            case "about": showAboutDialog(); break;
            default: break;
        }
    }

    private void cycleTheme() {
        int idx = THEMES.indexOf(theme);
        idx = (idx + 1) % THEMES.size();
        themeItems[idx].setSelected(true);
        applyTheme(THEMES.get(idx));
        sound.play(SoundManager.THEME);
    }

    /* =======================================================================
     * 21. LINE HELPERS
     * ======================================================================= */

    private Element lineRoot() { return textArea.getDocument().getDefaultRootElement(); }
    private int getLineCount() { return lineRoot().getElementCount(); }
    private int getLineOfOffset(int offset) throws BadLocationException {
        Element root = lineRoot();
        int idx = root.getElementIndex(offset);
        if (idx < 0 || idx >= root.getElementCount())
            throw new BadLocationException("Invalid offset", offset);
        return idx;
    }
    private int getLineStartOffset(int line) throws BadLocationException {
        Element root = lineRoot();
        if (line < 0 || line >= root.getElementCount())
            throw new BadLocationException("Invalid line", line);
        return root.getElement(line).getStartOffset();
    }
    private int getLineEndOffset(int line) throws BadLocationException {
        Element root = lineRoot();
        if (line < 0 || line >= root.getElementCount())
            throw new BadLocationException("Invalid line", line);
        return root.getElement(line).getEndOffset();
    }

    /* =======================================================================
     * 22. NO-WRAP EDITOR KIT
     * ======================================================================= */

    static class NoWrapEditorKit extends StyledEditorKit {
        private final ViewFactory factory = elem -> {
            String kind = elem.getName();
            if (kind != null) {
                switch (kind) {
                    case AbstractDocument.ContentElementName:   return new LabelView(elem);
                    case AbstractDocument.ParagraphElementName: return new NoWrapParagraphView(elem);
                    case AbstractDocument.SectionElementName:   return new BoxView(elem, View.Y_AXIS);
                    case StyleConstants.ComponentElementName:   return new ComponentView(elem);
                    case StyleConstants.IconElementName:        return new IconView(elem);
                }
            }
            return new LabelView(elem);
        };
        @Override public ViewFactory getViewFactory() { return factory; }
    }

    static class NoWrapParagraphView extends ParagraphView {
        NoWrapParagraphView(Element elem) { super(elem); }
        @Override public float getMinimumSpan(int axis) {
            return axis == View.X_AXIS ? 0 : super.getMinimumSpan(axis);
        }
        @Override public float getMaximumSpan(int axis) {
            return axis == View.X_AXIS ? Integer.MAX_VALUE : super.getMaximumSpan(axis);
        }
        @Override public void layout(int width, int height) {
            super.layout(Short.MAX_VALUE, height);
        }
    }

    /* =======================================================================
     * 23. FILE OPERATIONS
     * ======================================================================= */

    private void newDocument() {
        if (!confirmSaveIfDirty()) return;
        docLock = true;
        try { textArea.setText(""); } finally { docLock = false; }
        currentFile = null; isDirty = false;
        undoManager.discardAllEdits();

        MutableAttributeSet inputAttrs = textArea.getInputAttributes();
        if (inputAttrs != null) {
            inputAttrs.removeAttributes(inputAttrs);
            StyleConstants.setForeground(inputAttrs, theme.fg);
            StyleConstants.setFontFamily(inputAttrs, fontFamily);
            StyleConstants.setFontSize(inputAttrs, fontSize);
        }

        updateTitle(); updateStatusBar(); clearDraft();
        highlightSyntax(); applyParagraphAttrs();
        showEditorCard();
        sound.play(SoundManager.NEW);
    }

    private void openFile() {
        if (!confirmSaveIfDirty()) return;
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Open File");
        fc.setFileFilter(new FileNameExtensionFilter(
                "Text / source / RTF (*.txt, *.java, *.rtf, ...)",
                "txt", "java", "md", "csv", "json", "xml", "html", "css", "js",
                "py", "c", "cpp", "h", "rtf", "log", "ini", "yml", "yaml"));
        fc.setAcceptAllFileFilterUsed(true);
        if (currentFile != null) fc.setCurrentDirectory(currentFile.getParentFile());
        if (fc.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) loadFile(fc.getSelectedFile());
    }

    private void loadFile(File file) {
        boolean rtf = file.getName().toLowerCase().endsWith(".rtf");
        if (rtf) {
            if (!loadRtf(file)) return;
        } else {
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
                String line; boolean first = true;
                while ((line = r.readLine()) != null) {
                    if (!first) sb.append('\n');
                    sb.append(line); first = false;
                }
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(frame, "Could not open the file:\n" + ex.getMessage(),
                        "Open Error", JOptionPane.ERROR_MESSAGE);
                sound.play(SoundManager.ERROR);
                return;
            }
            docLock = true;
            try { textArea.setText(sb.toString()); } finally { docLock = false; }
        }

        try { textArea.setCaretPosition(0); } catch (Exception ignored) { }
        currentFile = file; isDirty = false;
        undoManager.discardAllEdits();
        addRecentFile(file.getAbsolutePath());

        setLanguage(detectLanguage(file));

        MutableAttributeSet inputAttrs = textArea.getInputAttributes();
        if (inputAttrs != null) {
            inputAttrs.removeAttributes(inputAttrs);
            StyleConstants.setForeground(inputAttrs, theme.fg);
            StyleConstants.setFontFamily(inputAttrs, fontFamily);
            StyleConstants.setFontSize(inputAttrs, fontSize);
        }

        updateTitle(); updateStatusBar(); clearDraft();
        applyParagraphAttrs(); highlightSyntax();
        showEditorCard();
    }

    private boolean loadRtf(File file) {
        try {
            StyledDocument doc = textArea.getStyledDocument();
            docLock = true;
            doc.remove(0, doc.getLength());
            try (Reader r = new InputStreamReader(new FileInputStream(file), StandardCharsets.US_ASCII)) {
                new RTFEditorKit().read(r, doc, 0);
            }
            return true;
        } catch (Throwable t) {
            JOptionPane.showMessageDialog(frame, "Could not read the RTF file:\n" + t.getMessage(),
                    "Open Error", JOptionPane.ERROR_MESSAGE);
            sound.play(SoundManager.ERROR);
            try { textArea.setText(""); } catch (Exception ignored) { }
            return false;
        } finally { docLock = false; }
    }

    private boolean saveFile() { return currentFile == null ? saveFileAs() : writeToFile(currentFile); }

    private boolean saveFileAs() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Save As");
        fc.setSelectedFile(currentFile != null ? currentFile : new File("Untitled.txt"));
        fc.setFileFilter(new FileNameExtensionFilter("Text file (*.txt)", "txt"));
        if (fc.showSaveDialog(frame) != JFileChooser.APPROVE_OPTION) return false;
        File target = applyDefaultExtension(fc.getSelectedFile());
        if (target.exists()) {
            int answer = JOptionPane.showConfirmDialog(frame,
                    "\"" + target.getName() + "\" already exists.\nReplace it?",
                    "Confirm Save As", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (answer != JOptionPane.YES_OPTION) return false;
        }
        if (writeToFile(target)) {
            currentFile = target;
            setLanguage(detectLanguage(target));
            addRecentFile(target.getAbsolutePath());
            updateTitle();
            return true;
        }
        return false;
    }

    private File applyDefaultExtension(File file) {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        if (dot > 0 && dot < name.length() - 1) return file;
        if (name.endsWith(".")) return new File(file.getParentFile(), name + "txt");
        return new File(file.getParentFile(), name + ".txt");
    }

    private boolean writeToFile(File file) {
        boolean rtf = file.getName().toLowerCase().endsWith(".rtf");
        boolean hasImages = documentContainsImages();

        if (!rtf && hasImages) {
            if (!imageWarningShown) { imageWarningShown = true; prefs.putBoolean(PREF_IMGWARN, true); }
            int choice = JOptionPane.showConfirmDialog(frame,
                    "This document contains inline images.\n" +
                            "A plain .txt file cannot store them.\n\n" +
                            "Save as RTF instead (keeps the images)?\n" +
                            "(Note: images are saved, but this editor cannot display\n" +
                            "them when the RTF file is reopened.)",
                    "Images Cannot Be Saved As Text",
                    JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
            if (choice == JOptionPane.CANCEL_OPTION || choice == JOptionPane.CLOSED_OPTION) return false;
            if (choice == JOptionPane.YES_OPTION) {
                File rtfTarget = new File(file.getParentFile(), stripExtension(file.getName()) + ".rtf");
                if (writeRtf(rtfTarget)) {
                    currentFile = rtfTarget;
                    addRecentFile(rtfTarget.getAbsolutePath());
                    updateTitle(); isDirty = false; clearDraft();
                    sound.play(SoundManager.SAVE);
                    return true;
                }
                return false;
            }
        }
        boolean ok = rtf ? writeRtf(file) : writePlainText(file);
        if (ok) { isDirty = false; updateTitle(); clearDraft(); sound.play(SoundManager.SAVE); }
        return ok;
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private boolean writePlainText(File file) {
        try (BufferedWriter w = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
            String t = textArea.getText();
            w.write(t == null ? "" : t.replace(OBJ_REPLACEMENT, ""));
            return true;
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(frame, "Could not save the file:\n" + ex.getMessage(),
                    "Save Error", JOptionPane.ERROR_MESSAGE);
            sound.play(SoundManager.ERROR);
            return false;
        }
    }

    private boolean writeRtf(File file) {
        try (Writer w = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.US_ASCII)) {
            StyledDocument doc = textArea.getStyledDocument();
            w.write("{\\rtf1\\ansi\\deff0\\uc1{\\fonttbl{\\f0 " +
                    escapeRtfFont(fontFamily) + ";}}\\f0\\fs" + (fontSize * 2) + " ");
            Element root = doc.getDefaultRootElement();
            int paras = root.getElementCount();
            for (int i = 0; i < paras; i++) {
                Element para = root.getElement(i);
                for (int j = 0; j < para.getElementCount(); j++)
                    writeRtfRun(w, doc, para.getElement(j));
                if (i < paras - 1) w.write("\\par\n");
            }
            w.write("}");
            return true;
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(frame, "Could not save the RTF file:\n" + ex.getMessage(),
                    "Save Error", JOptionPane.ERROR_MESSAGE);
            sound.play(SoundManager.ERROR);
            return false;
        }
    }

    private void writeRtfRun(Writer w, StyledDocument doc, Element run) throws Exception {
        AttributeSet as = run.getAttributes();
        Icon icon = as != null ? StyleConstants.getIcon(as) : null;
        if (icon != null) { writeRtfIcon(w, icon); return; }
        int s = run.getStartOffset(), e = run.getEndOffset();
        String text = doc.getText(s, e - s);
        while (text.endsWith("\n") || text.endsWith("\r"))
            text = text.substring(0, text.length() - 1);
        w.write(escapeRtf(text));
    }

    private void writeRtfIcon(Writer w, Icon icon) throws IOException {
        int iw = Math.max(1, icon.getIconWidth()), ih = Math.max(1, icon.getIconHeight());
        BufferedImage bi = new BufferedImage(iw, ih, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = bi.createGraphics();
        try { icon.paintIcon(new JLabel(), g2, 0, 0); } finally { g2.dispose(); }
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(bi, "png", bos);
        byte[] png = bos.toByteArray();
        StringBuilder hex = new StringBuilder(png.length * 2);
        for (byte b : png) hex.append(String.format("%02x", b & 0xFF));
        w.write("{\\pict\\pngblip\\picw" + iw + "\\pich" + ih +
                "\\picwgoal" + (iw * 15) + "\\pichgoal" + (ih * 15) + "\n");
        int chunk = 128;
        for (int i = 0; i < hex.length(); i += chunk) {
            w.write(hex.substring(i, Math.min(hex.length(), i + chunk)));
            w.write("\n");
        }
        w.write("}\n");
    }

    private static String escapeRtfFont(String s) {
        StringBuilder sb = new StringBuilder();
        for (char ch : s.toCharArray())
            sb.append((ch == ';' || ch == '\\' || ch == '{' || ch == '}') ? '_' : ch);
        return sb.toString();
    }

    private static String escapeRtf(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '\\': sb.append("\\\\"); break;
                case '{': sb.append("\\{"); break;
                case '}': sb.append("\\}"); break;
                case '\n': sb.append("\\par "); break;
                case '\r': break;
                case '\t': sb.append("\\tab "); break;
                default:
                    if (ch < 128) sb.append(ch);
                    else sb.append("\\u").append((int) ch).append('?');
            }
        }
        return sb.toString();
    }

    private void insertFilePath() {
        String path = currentFile != null ? currentFile.getAbsolutePath() : "(file not yet saved)";
        textArea.replaceSelection(path);
    }

    private boolean confirmSaveIfDirty() {
        if (!isDirty) return true;
        int choice = JOptionPane.showConfirmDialog(frame,
                "Do you want to save changes to " + getDocumentName() + "?",
                "Luminary IDE", JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
        if (choice == JOptionPane.YES_OPTION) return saveFile();
        return choice == JOptionPane.NO_OPTION;
    }

    private void closeWindow() {
        if (!confirmSaveIfDirty()) return;
        savePreferences();
        saveWindowState();
        if (autoSaveTimer != null) autoSaveTimer.stop();
        if (highlightTimer != null) highlightTimer.stop();
        clearDraft();
        if (sound != null) sound.shutdown();
        frame.dispose();
    }

    private void savePreferences() {
        prefs.put(PREF_THEME_NAME, theme.name);
        prefs.put(PREF_FONT, fontFamily);
        prefs.putInt(PREF_SIZE, fontSize);
        prefs.putBoolean(PREF_AUTOINDENT, autoIndentEnabled);
        prefs.putBoolean(PREF_AUTOCLOSE, autoCloseEnabled);
        prefs.putBoolean(PREF_BRACKETMATCH, bracketMatchEnabled);
        prefs.putBoolean(PREF_SYNTAX, syntaxEnabled);
        prefs.putBoolean(PREF_RAINBOW, rainbowBracketsEnabled);
        prefs.putBoolean(PREF_CURLINE, currentLineEnabled);
        prefs.putInt(PREF_TABSIZE, tabSize);
        prefs.putBoolean(PREF_SOUND, soundEnabled);
        prefs.putBoolean(PREF_KEYCLICK, keyClickEnabled);
        prefs.putFloat(PREF_VOLUME, soundVolume);
        prefs.putBoolean(PREF_WELCOME, showWelcomeOnStartup);
    }

    /* =======================================================================
     * 24. CRASH RECOVERY
     * ======================================================================= */

    private void startAutoSaveTimer() {
        autoSaveTimer = new javax.swing.Timer(20000, e -> saveDraft());
        autoSaveTimer.start();
    }

    private void saveDraft() {
        if (!isDirty) return;
        try {
            Files.createDirectories(DRAFT_PATH.getParent());
            String t = textArea.getText();
            Files.writeString(DRAFT_PATH, t == null ? "" : t);
            prefs.putBoolean(PREF_DRAFT_DIRTY, true);
        } catch (IOException ignored) { }
    }

    private void clearDraft() {
        try { Files.deleteIfExists(DRAFT_PATH); } catch (IOException ignored) { }
        prefs.putBoolean(PREF_DRAFT_DIRTY, false);
    }

    private boolean offerDraftRecovery() {
        boolean hadDraft = prefs.getBoolean(PREF_DRAFT_DIRTY, false);
        String draft = "";
        try {
            if (Files.exists(DRAFT_PATH)) draft = Files.readString(DRAFT_PATH);
        } catch (IOException ignored) { }
        if (!hadDraft || draft.isEmpty()) return false;

        int choice = JOptionPane.showConfirmDialog(frame,
                "An unsaved draft was found from a previous session.\nWould you like to restore it?",
                "Recover Unsaved Work", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (choice == JOptionPane.YES_OPTION) {
            docLock = true;
            try { textArea.setText(draft); } finally { docLock = false; }
            try { textArea.setCaretPosition(0); } catch (Exception ignored) { }
            isDirty = true; updateTitle();
            applyParagraphAttrs(); highlightSyntax();
            setChromeVisible(true);
            crossFadeTo(CARD_EDITOR);
            showingWelcome = false;
            showingLogin   = false;
            showWelcomeOnStartup = false;
            return true;
        } else {
            clearDraft();
            return false;
        }
    }

    /* =======================================================================
     * 25. IMAGE HANDLING
     * ======================================================================= */

    private static final String[] IMAGE_EXT = { ".png", ".jpg", ".jpeg", ".gif", ".bmp" };
    private static boolean isImageFile(File f) { return f != null && f.isFile() && isImageExt(f); }
    private static boolean isImageExt(File f) {
        String n = f.getName().toLowerCase();
        for (String e : IMAGE_EXT) if (n.endsWith(e)) return true;
        return false;
    }

    private boolean documentContainsImages() {
        try {
            StyledDocument doc = textArea.getStyledDocument();
            ArrayList<int[]> icons = new ArrayList<>();
            collectIconRanges(doc.getDefaultRootElement(), icons);
            return !icons.isEmpty();
        } catch (Throwable t) { return false; }
    }

    private void chooseAndInsertImage() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Insert Image");
        fc.setFileFilter(new FileNameExtensionFilter(
                "Images (*.png, *.jpg, *.jpeg, *.gif, *.bmp)", "png", "jpg", "jpeg", "gif", "bmp"));
        fc.setAcceptAllFileFilterUsed(false);
        if (fc.showOpenDialog(frame) != JFileChooser.APPROVE_OPTION) return;
        File f = fc.getSelectedFile();
        if (!isImageExt(f)) {
            JOptionPane.showMessageDialog(frame, "Unsupported image type.",
                    "Insert Image", JOptionPane.WARNING_MESSAGE);
            sound.play(SoundManager.ERROR); return;
        }
        insertImageFromFile(f, true);
    }

    private void insertImageFromFile(File file, boolean askSize) {
        try {
            Image img = readImage(file);
            if (img == null) {
                JOptionPane.showMessageDialog(frame, "Could not read the image file:\n" + file.getAbsolutePath(),
                        "Insert Image", JOptionPane.ERROR_MESSAGE);
                sound.play(SoundManager.ERROR); return;
            }
            if (askSize) {
                int maxW = askImageWidth(img);
                if (maxW <= 0) return;
                insertImage(img, maxW);
            } else insertImage(img, 600);
        } catch (Throwable t) {
            JOptionPane.showMessageDialog(frame, "Image error:\n" + t,
                    "Insert Image", JOptionPane.ERROR_MESSAGE);
            sound.play(SoundManager.ERROR);
        }
    }

    private Image readImage(File f) {
        try { BufferedImage bi = ImageIO.read(f); if (bi != null) return bi; }
        catch (Throwable ignored) { }
        ImageIcon ic = new ImageIcon(f.getAbsolutePath());
        return ic.getIconWidth() > 0 ? ic.getImage() : null;
    }

    private int askImageWidth(Image img) {
        int w = img.getWidth(null), h = img.getHeight(null);
        Object[] options = { "Small (240 px)", "Medium (600 px)",
                "Original (" + w + " × " + h + ")", "Cancel" };
        int choice = JOptionPane.showOptionDialog(frame,
                "How large should the image be inserted?",
                "Insert Image", JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE,
                null, options, options[1]);
        switch (choice) {
            case 0: return 240;
            case 1: return 600;
            case 2: return Math.max(1, w);
            default: return -1;
        }
    }

    private void insertImage(Image img, int maxWidth) {
        if (img == null) return;
        try {
            int w = img.getWidth(null), h = img.getHeight(null);
            if (w <= 0 || h <= 0) return;
            Image toUse = img;
            if (maxWidth > 0 && w > maxWidth) {
                int nh = Math.max(1, (int) Math.round(h * (double) maxWidth / w));
                toUse = img.getScaledInstance(maxWidth, nh, Image.SCALE_SMOOTH);
            }
            ImageIcon icon = new ImageIcon(toUse);
            SimpleAttributeSet attrs = new SimpleAttributeSet();
            StyleConstants.setIcon(attrs, icon);
            StyledDocument doc = textArea.getStyledDocument();
            int pos = Math.min(textArea.getCaretPosition(), doc.getLength());
            doc.insertString(pos, OBJ_REPLACEMENT, attrs);
            textArea.setCaretPosition(Math.min(pos + 1, doc.getLength()));
            textArea.requestFocusInWindow();
            sound.play(SoundManager.IMAGE);
            SwingUtilities.invokeLater(this::highlightSyntax);
        } catch (Throwable t) {
            JOptionPane.showMessageDialog(frame, "Could not insert the image:\n" + t,
                    "Insert Image", JOptionPane.ERROR_MESSAGE);
            sound.play(SoundManager.ERROR);
        }
    }

    private void pasteWithImageSupport() {
        try {
            Clipboard cb = Toolkit.getDefaultToolkit().getSystemClipboard();
            if (cb.isDataFlavorAvailable(DataFlavor.imageFlavor)) {
                Transferable tr = cb.getContents(null);
                if (tr != null && tr.isDataFlavorSupported(DataFlavor.imageFlavor)) {
                    Object o = tr.getTransferData(DataFlavor.imageFlavor);
                    if (o instanceof Image) { insertImage((Image) o, 600); return; }
                }
            }
        } catch (Throwable ignored) { }
        textArea.paste();
    }

    private void openImageViewer() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Open Image");
        fc.setFileFilter(new FileNameExtensionFilter(
                "Images (*.png, *.jpg, *.jpeg, *.gif, *.bmp)", "png", "jpg", "jpeg", "gif", "bmp"));
        fc.setAcceptAllFileFilterUsed(false);
        if (fc.showOpenDialog(frame) != JFileChooser.APPROVE_OPTION) return;
        Image img = readImage(fc.getSelectedFile());
        if (img == null) {
            JOptionPane.showMessageDialog(frame, "Could not read that image.",
                    "Open Image", JOptionPane.ERROR_MESSAGE);
            sound.play(SoundManager.ERROR); return;
        }
        new ImageViewer(frame, img, fc.getSelectedFile().getName(), theme).setVisible(true);
    }

    static class ImageViewer extends JDialog {
        private final Image source;
        private final JLabel canvas = new JLabel("", SwingConstants.CENTER);
        private double zoom = 1.0;

        ImageViewer(Window owner, Image image, String title, Theme theme) {
            super(owner, "Image — " + title, ModalityType.MODELESS);
            this.source = image;
            setLayout(new BorderLayout());
            JScrollPane sp = new JScrollPane(canvas);
            sp.getViewport().setBackground(theme.bg);
            sp.setBorder(BorderFactory.createEmptyBorder());
            add(sp, BorderLayout.CENTER);

            JPanel bar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 8));
            bar.setBackground(theme.panelBg);
            JButton zout = viewerButton(IDEIcons.ZOOM_OUT, "Zoom Out", theme);
            JButton zin  = viewerButton(IDEIcons.ZOOM_IN, "Zoom In", theme);
            JButton fit  = viewerButton(IDEIcons.RESET, "Fit", theme);
            JButton one  = viewerButton(IDEIcons.CHECK, "100%", theme);
            JButton cl   = viewerButton(IDEIcons.CLOSE, "Close", theme);
            zin.addActionListener(e -> { zoom = Math.min(6.0, zoom * 1.25); render(); });
            zout.addActionListener(e -> { zoom = Math.max(0.05, zoom / 1.25); render(); });
            one.addActionListener(e -> { zoom = 1.0; render(); });
            fit.addActionListener(e -> {
                int vw = sp.getViewport().getWidth() - 8, vh = sp.getViewport().getHeight() - 8;
                int iw = Math.max(1, source.getWidth(null)), ih = Math.max(1, source.getHeight(null));
                if (vw > 0 && vh > 0) zoom = Math.min((double) vw / iw, (double) vh / ih);
                render();
            });
            cl.addActionListener(e -> dispose());
            bar.add(zout); bar.add(zin); bar.add(one); bar.add(fit); bar.add(cl);
            add(bar, BorderLayout.SOUTH);

            canvas.setBackground(theme.bg); canvas.setOpaque(true);
            setSize(820, 620); setLocationRelativeTo(owner);
            render();
        }

        private JButton viewerButton(Icon icon, String text, Theme theme) {
            JButton b = new JButton(text);
            if (icon != null) {
                b.setIcon(icon);
                b.setIconTextGap(6);
            }
            b.setFocusable(false);
            b.setFont(uiFont(Font.PLAIN, 12));
            b.setBackground(theme.panelBg);
            b.setForeground(theme.dark ? Color.WHITE : Color.BLACK);
            b.setBorder(new EmptyBorder(6, 12, 6, 12));
            b.setOpaque(true);
            b.setContentAreaFilled(true);
            b.setBorderPainted(false);
            b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            installSmoothHover(b, () -> theme.panelBg, () -> theme.hover);
            return b;
        }

        private JButton viewerButton(String text, Theme theme) {
            return viewerButton(null, text, theme);
        }

        private void render() {
            int iw = Math.max(1, source.getWidth(null));
            int ih = Math.max(1, source.getHeight(null));
            int nw = Math.max(1, (int) Math.round(iw * zoom));
            int nh = Math.max(1, (int) Math.round(ih * zoom));
            canvas.setIcon(new ImageIcon(source.getScaledInstance(nw, nh, Image.SCALE_SMOOTH)));
            canvas.revalidate(); canvas.repaint();
        }
    }

    /* =======================================================================
     * 26. EDIT OPERATIONS
     * ======================================================================= */

    private void duplicateLine() {
        try {
            int start = textArea.getSelectionStart(), end = textArea.getSelectionEnd();
            int sL = getLineOfOffset(start), eL = getLineOfOffset(end);
            if (end > start && end == getLineStartOffset(eL) && eL > sL) eL--;
            int bs = getLineStartOffset(sL), be = getLineEndOffset(eL);
            String block = textArea.getText(bs, be - bs);
            String ins = block.endsWith("\n") ? block : "\n" + block;
            textArea.getDocument().insertString(be, ins, null);
        } catch (BadLocationException ignored) { }
    }

    private void moveLine(int direction) {
        try {
            int caret = textArea.getCaretPosition();
            int line  = getLineOfOffset(caret);
            if (direction < 0 && line == 0) return;
            if (direction > 0 && line == getLineCount() - 1) return;
            int startA = getLineStartOffset(line), endA = getLineEndOffset(line);
            int other = line + direction;
            int startB = getLineStartOffset(other), endB = getLineEndOffset(other);
            String lineA = textArea.getText(startA, endA - startA);
            String lineB = textArea.getText(startB, endB - startB);
            int minStart = Math.min(startA, startB);
            int maxEnd   = Math.max(endA, endB);
            String first  = direction < 0 ? lineA : lineB;
            String second = direction < 0 ? lineB : lineA;
            if (!first.endsWith("\n") && second.endsWith("\n")) {
                first += "\n";
                second = second.substring(0, second.length() - 1);
            }
            textArea.getDocument().remove(minStart, maxEnd - minStart);
            textArea.getDocument().insertString(minStart, first + second, null);
            int target = direction < 0 ? caret - lineB.length() : caret + lineB.length();
            textArea.setCaretPosition(Math.max(0, Math.min(textArea.getDocument().getLength(), target)));
        } catch (BadLocationException ignored) { }
    }

    private String commentPrefix() {
        if (currentFile == null) return "// ";
        String n = currentFile.getName().toLowerCase();
        if (n.endsWith(".py") || n.endsWith(".ini") || n.endsWith(".yml") || n.endsWith(".yaml"))
            return "# ";
        return "// ";
    }

    private void toggleLineComment() {
        String prefix = commentPrefix();
        String bare   = prefix.trim();
        try {
            int start = textArea.getSelectionStart(), end = textArea.getSelectionEnd();
            int sL = getLineOfOffset(start), eL = getLineOfOffset(end);
            if (end > start && end == getLineStartOffset(eL) && eL > sL) eL--;
            boolean allCommented = true;
            for (int l = sL; l <= eL; l++) {
                String line = textArea.getText(getLineStartOffset(l),
                        getLineEndOffset(l) - getLineStartOffset(l));
                if (!line.trim().startsWith(bare)) { allCommented = false; break; }
            }
            for (int l = sL; l <= eL; l++) {
                int s = getLineStartOffset(l);
                int e = getLineEndOffset(l);
                String line = textArea.getText(s, e - s);
                if (allCommented) {
                    int idx = line.indexOf(bare);
                    if (idx >= 0) {
                        int removeLen = line.startsWith(prefix, idx) ? prefix.length() : bare.length();
                        textArea.getDocument().remove(s + idx, removeLen);
                    }
                } else {
                    textArea.getDocument().insertString(s, prefix, null);
                }
            }
        } catch (BadLocationException ignored) { }
    }

    private void goToLine() {
        String input = JOptionPane.showInputDialog(frame,
                "Go to line (1 - " + getLineCount() + "):", "Go To Line", JOptionPane.PLAIN_MESSAGE);
        if (input == null) return;
        try {
            int line = Integer.parseInt(input.trim());
            if (line < 1 || line > getLineCount()) {
                JOptionPane.showMessageDialog(frame, "Line number out of range.",
                        "Go To Line", JOptionPane.WARNING_MESSAGE); return;
            }
            textArea.setCaretPosition(getLineStartOffset(line - 1));
            textArea.requestFocusInWindow();
        } catch (NumberFormatException | BadLocationException ex) {
            JOptionPane.showMessageDialog(frame, "Please enter a valid number.",
                    "Go To Line", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void insertDateTime() {
        String stamp = new java.text.SimpleDateFormat("dd-MM-yyyy HH:mm:ss")
                .format(new java.util.Date());
        textArea.replaceSelection(stamp);
    }

    /* =======================================================================
     * 27. VIEW OPERATIONS
     * ======================================================================= */

    private void zoomBy(int delta) { fontSize = Math.max(8, Math.min(72, fontSize + delta)); applyFontSize(); }
    private void resetZoom() { fontSize = 14; applyFontSize(); }

    private void applyFontSize() {
        Font f = textArea.getFont();
        textArea.setFont(monoFont(f.getStyle(), fontSize));
        prefs.putInt(PREF_SIZE, fontSize);
        updateStatusBar();
        updateFontSizeLabel();
        applyParagraphAttrs();
        highlightSyntax();
    }

    private void chooseFont() {
        JDialog dialog = new JDialog(frame, "Choose Font", true);
        dialog.setLayout(new BorderLayout(10, 10));
        dialog.setSize(520, 380);
        dialog.setLocationRelativeTo(frame);

        String[] families = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getAvailableFontFamilyNames();
        JList<String> familyList = new JList<>(families);
        familyList.setSelectedValue(textArea.getFont().getFamily(), true);
        String[] styles = { "Plain", "Bold", "Italic", "Bold Italic" };
        JList<String> styleList = new JList<>(styles);
        styleList.setSelectedIndex(textArea.getFont().getStyle());
        String[] sizes = { "8","10","11","12","14","16","18","20","24","28","32","40","48","56","64","72" };
        JList<String> sizeList = new JList<>(sizes);
        sizeList.setSelectedValue(String.valueOf(textArea.getFont().getSize()), true);

        JPanel lists = new JPanel(new GridLayout(1, 3, 8, 8));
        lists.setBorder(new EmptyBorder(16, 16, 0, 16));
        lists.setBackground(theme.panelBg);
        lists.add(styleScroll(familyList));
        lists.add(styleScroll(styleList));
        lists.add(styleScroll(sizeList));

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        btns.setBackground(theme.panelBg);
        JButton ok = primaryButton(IDEIcons.CHECK, "Apply Font");
        JButton cancel = secondaryButton(IDEIcons.CLOSE, "Cancel");
        ok.addActionListener(e -> {
            String family = familyList.getSelectedValue();
            int style = styleList.getSelectedIndex();
            int size;
            try { size = Integer.parseInt(sizeList.getSelectedValue()); }
            catch (Exception ex) { size = textArea.getFont().getSize(); }
            if (family != null) {
                fontFamily = family; fontSize = size;
                textArea.setFont(new Font(family, style, size));
                prefs.put(PREF_FONT, family);
                prefs.putInt(PREF_SIZE, size);
                updateStatusBar(); updateFontSizeLabel();
                applyParagraphAttrs(); highlightSyntax();
            }
            dialog.dispose();
        });
        cancel.addActionListener(e -> dialog.dispose());
        btns.add(cancel); btns.add(ok);

        dialog.add(lists, BorderLayout.CENTER);
        dialog.add(btns, BorderLayout.SOUTH);
        dialog.getContentPane().setBackground(theme.panelBg);
        dialog.setVisible(true);
    }

    private JScrollPane styleScroll(JComponent cc) {
        JScrollPane sp = new JScrollPane(cc);
        sp.setBorder(BorderFactory.createLineBorder(theme.border));
        installModernScrollBars(sp);
        return sp;
    }

    private void toggleWordWrap() {
        boolean wrap = wordWrap.isSelected();
        wrapEnabled = wrap;
        int caret = textArea.getCaretPosition();
        int selStart = textArea.getSelectionStart(), selEnd = textArea.getSelectionEnd();
        StyledDocument oldDoc = textArea.getStyledDocument();
        textArea.setEditorKit(wrap ? new StyledEditorKit() : new NoWrapEditorKit());
        StyledDocument newDoc = textArea.getStyledDocument();
        undoManager.discardAllEdits();
        newDoc.addUndoableEditListener(undoManager);
        newDoc.addDocumentListener(documentListener);
        docLock = true;
        try { copyStyledDocument(oldDoc, newDoc); } catch (Throwable ignored) { }
        finally { docLock = false; }
        int len = newDoc.getLength();
        try { textArea.setCaretPosition(Math.min(caret, len)); } catch (Exception ignored) { }
        if (selStart != selEnd) {
            try { textArea.select(Math.min(selStart, len), Math.min(selEnd, len)); } catch (Exception ignored) { }
        }
        applyTheme(theme); applyParagraphAttrs(); highlightSyntax();
        updateStatusBar(); updateTitle();
    }

    private static void copyStyledDocument(StyledDocument src, StyledDocument dst) throws BadLocationException {
        Element root = src.getDefaultRootElement();
        for (int i = 0; i < root.getElementCount(); i++) {
            Element para = root.getElement(i);
            for (int j = 0; j < para.getElementCount(); j++) {
                Element run = para.getElement(j);
                int s = run.getStartOffset(), e = run.getEndOffset();
                String t = src.getText(s, e - s);
                if (t.isEmpty()) continue;
                dst.insertString(dst.getLength(), t, run.getAttributes());
            }
        }
    }

    private void toggleStatusBar() {
        statusBar.setVisible(statusBarItem.isSelected());
        frame.revalidate();
    }

    private void toggleLineNumbers() {
        boolean show = lineNumbersItem.isSelected();
        if (show) {
            scrollPane.setRowHeaderView(lineNumberView);
            lineNumberView.setVisible(true);
            lineNumberView.setPreferredSize(null);
        } else {
            lineNumberView.setVisible(false);
            if (scrollPane.getRowHeader() != null) {
                scrollPane.getRowHeader().setPreferredSize(new Dimension(0, 0));
                scrollPane.getRowHeader().setMinimumSize(new Dimension(0, 0));
            }
        }
        applyTextPadding();
        scrollPane.revalidate();
        scrollPane.repaint();
    }

    private void toggleAutoIndent() { autoIndentEnabled = autoIndentItem.isSelected();
        prefs.putBoolean(PREF_AUTOINDENT, autoIndentEnabled); }
    private void toggleAutoClose()  { autoCloseEnabled = autoCloseItem.isSelected();
        prefs.putBoolean(PREF_AUTOCLOSE, autoCloseEnabled); }

    private void toggleBracketMatch() {
        bracketMatchEnabled = bracketMatchItem.isSelected();
        prefs.putBoolean(PREF_BRACKETMATCH, bracketMatchEnabled);
        if (!bracketMatchEnabled) {
            Highlighter hl = textArea.getHighlighter();
            if (bracketTagA != null) { hl.removeHighlight(bracketTagA); bracketTagA = null; }
            if (bracketTagB != null) { hl.removeHighlight(bracketTagB); bracketTagB = null; }
        } else highlightMatchingBracket();
    }

    private void toggleSyntax() {
        syntaxEnabled = syntaxItem.isSelected();
        prefs.putBoolean(PREF_SYNTAX, syntaxEnabled);
        if (syntaxEnabled) highlightSyntax();
        else {
            StyledDocument doc = textArea.getStyledDocument();
            if (doc != null && stylePlain != null) {
                ArrayList<int[]> icons = new ArrayList<>();
                collectIconRanges(doc.getDefaultRootElement(), icons);
                applyStyleRange(doc, 0, doc.getLength(), stylePlain, icons);
            }
        }
    }

    private void toggleRainbow() {
        rainbowBracketsEnabled = rainbowItem.isSelected();
        prefs.putBoolean(PREF_RAINBOW, rainbowBracketsEnabled);
        highlightSyntax();
    }

    private void toggleCurrentLine() {
        currentLineEnabled = currentLineItem.isSelected();
        prefs.putBoolean(PREF_CURLINE, currentLineEnabled);
        updateCurrentLineHighlight();
        lineNumberView.repaint();
    }

    private void toggleSound() {
        soundEnabled = soundItem.isSelected();
        sound.setEnabled(soundEnabled);
        prefs.putBoolean(PREF_SOUND, soundEnabled);
        if (soundEnabled) sound.play(SoundManager.CLICK);
    }

    private void showVolumeDialog() {
        JSlider slider = new JSlider(0, 100, Math.round(soundVolume * 100));
        slider.setMajorTickSpacing(25);
        slider.setMinorTickSpacing(5);
        slider.setPaintTicks(true);
        slider.setPaintLabels(true);
        slider.setBackground(theme.panelBg);
        slider.setForeground(theme.fg);

        JCheckBox mute = new JCheckBox("Mute sound effects", !soundEnabled);
        mute.setBackground(theme.panelBg); mute.setForeground(theme.fg);
        JCheckBox kclick = new JCheckBox("Key-click sound", keyClickEnabled);
        kclick.setBackground(theme.panelBg); kclick.setForeground(theme.fg);

        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setBackground(theme.panelBg);
        panel.setBorder(new EmptyBorder(16, 16, 16, 16));
        JLabel l = new JLabel("Master volume");
        l.setForeground(theme.fg);
        panel.add(l, BorderLayout.NORTH);
        panel.add(slider, BorderLayout.CENTER);
        JPanel checks = new JPanel();
        checks.setLayout(new BoxLayout(checks, BoxLayout.Y_AXIS));
        checks.setBackground(theme.panelBg);
        checks.add(mute); checks.add(kclick);
        panel.add(checks, BorderLayout.SOUTH);

        int result = JOptionPane.showConfirmDialog(frame, panel, "Sound Volume",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) return;

        soundVolume = slider.getValue() / 100f;
        sound.setVolume(soundVolume);
        prefs.putFloat(PREF_VOLUME, soundVolume);

        soundEnabled = !mute.isSelected();
        sound.setEnabled(soundEnabled);
        soundItem.setSelected(soundEnabled);
        prefs.putBoolean(PREF_SOUND, soundEnabled);

        keyClickEnabled = kclick.isSelected();
        keyClickItem.setSelected(keyClickEnabled);
        prefs.putBoolean(PREF_KEYCLICK, keyClickEnabled);
        sound.play(SoundManager.CLICK);
    }

    /* =======================================================================
     * 28. FIND & REPLACE
     * ======================================================================= */

    private void buildFindReplacePanel() {
        findReplacePanel = new JPanel(new BorderLayout(0, 6));
        findReplacePanel.setBorder(new EmptyBorder(10, 14, 10, 14));

        JPanel row1 = new JPanel(new BorderLayout(8, 0));
        row1.setOpaque(false);
        JLabel findLbl = new JLabel("Find");
        findLbl.setFont(uiFont(Font.PLAIN, 11));
        findLbl.setPreferredSize(new Dimension(48, 24));
        findField = new JTextField();
        findField.setFont(uiFont(Font.PLAIN, 12));
        findField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(theme.border),
                new EmptyBorder(4, 8, 4, 8)));
        findField.addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent e) {
                findField.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(theme.accent),
                        new EmptyBorder(4, 8, 4, 8)));
            }
            @Override public void focusLost(FocusEvent e) {
                findField.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(theme.border),
                        new EmptyBorder(4, 8, 4, 8)));
            }
        });

        matchCaseToggle = findToggle("Aa", "Match Case (Alt+C)");
        regexToggle     = findToggle(".*", "Regular Expression (Alt+R)");
        findPrevBtn     = findSmallButton(IDEIcons.UP, "Prev", "Previous Match (Shift+Enter / Shift+F3)");
        findNextBtn     = findSmallButton(IDEIcons.DOWN, "Next", "Next Match (Enter / F3)");
        JButton closeBtn = findSmallButton(IDEIcons.CLOSE, "Close", "Close Find Bar (Esc)");

        JPanel findRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        findRight.setOpaque(false);
        findRight.add(matchCaseToggle);
        findRight.add(regexToggle);
        findRight.add(findPrevBtn);
        findRight.add(findNextBtn);
        findRight.add(closeBtn);

        JPanel findLeft = new JPanel(new BorderLayout(8, 0));
        findLeft.setOpaque(false);
        findLeft.add(findLbl, BorderLayout.WEST);
        findLeft.add(findField, BorderLayout.CENTER);
        findLeft.add(findRight, BorderLayout.EAST);
        row1.add(findLeft, BorderLayout.CENTER);

        JPanel row2 = new JPanel(new BorderLayout(8, 0));
        row2.setOpaque(false);
        JLabel repLbl = new JLabel("Replace:");
        repLbl.setFont(uiFont(Font.PLAIN, 11));
        repLbl.setPreferredSize(new Dimension(68, 24));
        replaceField = new JTextField();
        replaceField.setFont(uiFont(Font.PLAIN, 12));
        replaceField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(theme.border),
                new EmptyBorder(4, 8, 4, 8)));
        replaceField.addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent e) {
                replaceField.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(theme.accent),
                    new EmptyBorder(4, 8, 4, 8)));
            }
            @Override public void focusLost(FocusEvent e) {
                replaceField.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(theme.border),
                    new EmptyBorder(4, 8, 4, 8)));
            }
        });

        replaceBtn    = findSmallButton(IDEIcons.DUPLICATE, "Replace", "Replace Current Match (Alt+1)");
        replaceAllBtn = findSmallButton(IDEIcons.SEND, "Replace All", "Replace All Matches (Alt+A)");

        JPanel repRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        repRight.setOpaque(false);
        repRight.add(replaceBtn); repRight.add(replaceAllBtn);

        JPanel repLeft = new JPanel(new BorderLayout(8, 0));
        repLeft.setOpaque(false);
        repLeft.add(repLbl, BorderLayout.WEST);
        repLeft.add(replaceField, BorderLayout.CENTER);
        repLeft.add(repRight, BorderLayout.EAST);
        row2.add(repLeft, BorderLayout.CENTER);

        findCounterLabel = new JLabel(" ");
        findCounterLabel.setFont(uiFont(Font.PLAIN, 11));

        JPanel center = new JPanel();
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        center.setOpaque(false);
        row1.setAlignmentX(Component.LEFT_ALIGNMENT);
        row2.setAlignmentX(Component.LEFT_ALIGNMENT);
        findCounterLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        center.add(row1);
        center.add(Box.createVerticalStrut(6));
        center.add(row2);
        center.add(Box.createVerticalStrut(4));
        center.add(findCounterLabel);

        findReplacePanel.add(center, BorderLayout.CENTER);

        findField.addActionListener(e -> findNext());
        findField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e)  { updateFindCounter(); }
            public void removeUpdate(DocumentEvent e)  { updateFindCounter(); }
            public void changedUpdate(DocumentEvent e) { updateFindCounter(); }
        });
        findNextBtn.addActionListener(e -> findNext());
        findPrevBtn.addActionListener(e -> findPrev());
        replaceBtn.addActionListener(e -> replaceCurrent());
        replaceAllBtn.addActionListener(e -> replaceAll());
        closeBtn.addActionListener(e -> hideFindPanel());

        findReplacePanel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "closeFind");
        findReplacePanel.getActionMap().put("closeFind", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { hideFindPanel(); }
        });
    }

    private IDEToggleButton findToggle(String text, String tip) {
        IDEToggleButton b = new IDEToggleButton(text, () -> theme);
        b.setToolTipText(tip);
        b.setFont(uiFont(Font.PLAIN, 11));
        b.setBorder(new EmptyBorder(4, 8, 4, 8));
        b.addActionListener(e -> updateFindCounter());
        return b;
    }

    private IDEButton findSmallButton(Icon icon, String text, String tip) {
        IDEButton b = new IDEButton(icon, text, () -> theme);
        b.setToolTipText(tip);
        b.setFont(uiFont(Font.PLAIN, 11));
        b.setBorder(new EmptyBorder(4, 8, 4, 8));
        return b;
    }

    private IDEButton findSmallButton(String text, String tip) {
        return findSmallButton(null, text, tip);
    }

    private void showFindPanel() {
        if (findReplacePanel.getParent() == null)
            editorCard.add(findReplacePanel, BorderLayout.NORTH);
        findReplacePanel.setVisible(true);
        editorCard.revalidate(); editorCard.repaint();
        String sel = textArea.getSelectedText();
        if (sel != null && !sel.isEmpty() && !sel.contains("\n")) findField.setText(sel);
        SwingUtilities.invokeLater(() -> {
            findField.requestFocusInWindow();
            findField.selectAll();
            updateFindCounter();
        });
    }

    private void hideFindPanel() {
        findReplacePanel.setVisible(false);
        editorCard.revalidate(); editorCard.repaint();
        textArea.requestFocusInWindow();
    }

    private void styleFindPanel(Theme t) {
        if (findReplacePanel == null) return;
        boolean isDark = t.dark;
        Color textCol = isDark ? Color.WHITE : Color.BLACK;
        findReplacePanel.setBackground(t.panelBg);
        findReplacePanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, t.border),
                new EmptyBorder(10, 14, 10, 14)));
        for (Component comp : allComponents(findReplacePanel)) {
            if (comp instanceof JTextField) {
                JTextField f = (JTextField) comp;
                f.setBackground(t.bg); f.setForeground(textCol);
                f.setCaretColor(t.accent);
                f.setSelectionColor(t.selection);
                f.setSelectedTextColor(t.selectionFg);
                f.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(t.border),
                        new EmptyBorder(4, 8, 4, 8)));
            } else if (comp instanceof JLabel) {
                ((JLabel) comp).setForeground(textCol);
            } else if (comp instanceof IDEToggleButton) {
                comp.setForeground(textCol);
            } else if (comp instanceof IDEButton) {
                comp.setForeground(textCol);
            } else if (comp instanceof JToggleButton) {
                JToggleButton b = (JToggleButton) comp;
                b.setBackground(b.isSelected() ? t.accentSoft : t.panelBg);
                b.setForeground(b.isSelected() ? t.accent : textCol);
                b.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(b.isSelected() ? t.accent : t.border),
                        new EmptyBorder(2, 5, 2, 5)));
            } else if (comp instanceof JButton) {
                JButton b = (JButton) comp;
                b.setBackground(t.panelBg); b.setForeground(textCol);
                b.setBorder(new EmptyBorder(4, 8, 4, 8));
            } else if (comp instanceof JPanel) {
                ((JPanel) comp).setOpaque(false);
            }
        }
    }

    private static List<Component> allComponents(Container root) {
        List<Component> out = new ArrayList<>();
        collectAll(root, out);
        return out;
    }

    private static void collectAll(Container root, List<Component> out) {
        for (Component cc : root.getComponents()) {
            out.add(cc);
            if (cc instanceof Container) collectAll((Container) cc, out);
        }
    }

    private void updateFindCounter() {
        if (findCounterLabel == null) return;
        String needle = findField.getText();
        if (needle.isEmpty()) { findCounterLabel.setText(" "); return; }
        try {
            int total = countMatches(needle);
            if (total == 0) { findCounterLabel.setText("No matches"); return; }
            int cur = currentMatchIndex();
            if (cur > 0) findCounterLabel.setText(cur + " of " + total + " matches");
            else findCounterLabel.setText(total + " match" + (total == 1 ? "" : "es"));
        } catch (Throwable ignored) { findCounterLabel.setText(" "); }
    }

    private int countMatches(String needle) {
        String hay = textArea.getText();
        if (hay == null || hay.isEmpty()) return 0;
        boolean mc = matchCaseToggle.isSelected();
        if (regexToggle.isSelected()) {
            try {
                Pattern p = Pattern.compile(needle, mc ? 0 : Pattern.CASE_INSENSITIVE);
                Matcher m = p.matcher(hay);
                int n = 0; while (m.find() && n < 10000) n++;
                return n;
            } catch (Exception e) { return 0; }
        } else {
            String h = mc ? hay : hay.toLowerCase();
            String nd = mc ? needle : needle.toLowerCase();
            int n = 0, i = 0;
            while ((i = h.indexOf(nd, i)) >= 0) { n++; i += nd.length(); if (n > 10000) break; }
            return n;
        }
    }

    private int currentMatchIndex() {
        String hay = textArea.getText();
        String needle = findField.getText();
        if (hay.isEmpty() || needle.isEmpty()) return 0;
        int selStart = textArea.getSelectionStart();
        int selEnd = textArea.getSelectionEnd();
        if (selStart == selEnd) return 0;
        boolean mc = matchCaseToggle.isSelected();

        if (regexToggle.isSelected()) {
            try {
                Pattern p = Pattern.compile(needle, mc ? 0 : Pattern.CASE_INSENSITIVE);
                Matcher m = p.matcher(hay);
                int n = 0;
                while (m.find() && n < 10000) {
                    n++;
                    if (m.start() == selStart && m.end() == selEnd) return n;
                }
                return 0;
            } catch (Exception e) { return 0; }
        }

        String h = mc ? hay : hay.toLowerCase();
        String nd = mc ? needle : needle.toLowerCase();
        int n = 0, i = 0;
        while ((i = h.indexOf(nd, i)) >= 0) {
            n++;
            if (i == selStart) return n;
            i += Math.max(1, nd.length());
        }
        return 0;
    }

    private void findNext() {
        String needle = findField.getText();
        if (needle.isEmpty()) return;
        boolean mc = matchCaseToggle.isSelected();
        String hay = textArea.getText();
        int from = textArea.getSelectionEnd();

        if (regexToggle.isSelected()) {
            try {
                Pattern p = Pattern.compile(needle, mc ? 0 : Pattern.CASE_INSENSITIVE);
                Matcher m = p.matcher(hay);
                if (m.find(from)) { textArea.select(m.start(), m.end()); textArea.requestFocusInWindow(); }
                else if (m.find(0)) { textArea.select(m.start(), m.end()); textArea.requestFocusInWindow(); }
                else JOptionPane.showMessageDialog(findReplacePanel, "Cannot find \"" + needle + "\"",
                            "Find", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(findReplacePanel,
                        "Invalid regex:\n" + ex.getMessage(), "Find", JOptionPane.ERROR_MESSAGE);
            }
            updateFindCounter(); return;
        }

        String hCmp = mc ? hay : hay.toLowerCase();
        String nCmp = mc ? needle : needle.toLowerCase();
        int idx = hCmp.indexOf(nCmp, from);
        if (idx < 0) idx = hCmp.indexOf(nCmp, 0);
        if (idx >= 0) {
            textArea.select(idx, idx + needle.length());
            textArea.requestFocusInWindow();
        } else {
            JOptionPane.showMessageDialog(findReplacePanel, "Cannot find \"" + needle + "\"",
                    "Find", JOptionPane.INFORMATION_MESSAGE);
        }
        updateFindCounter();
    }

    private void findPrev() {
        String needle = findField.getText();
        if (needle.isEmpty()) return;
        boolean mc = matchCaseToggle.isSelected();
        String hay = textArea.getText();
        int from = Math.max(0, textArea.getSelectionStart() - 1);

        if (regexToggle.isSelected()) {
            try {
                Pattern p = Pattern.compile(needle, mc ? 0 : Pattern.CASE_INSENSITIVE);
                Matcher m = p.matcher(hay);
                int lastStart = -1, lastEnd = -1;
                int wrapStart = -1, wrapEnd = -1;
                while (m.find()) {
                    wrapStart = m.start();
                    wrapEnd = m.end();
                    if (m.start() <= from) {
                        lastStart = m.start();
                        lastEnd = m.end();
                    }
                }
                if (lastStart >= 0) {
                    textArea.select(lastStart, lastEnd);
                    textArea.requestFocusInWindow();
                } else if (wrapStart >= 0) {
                    textArea.select(wrapStart, wrapEnd);
                    textArea.requestFocusInWindow();
                } else {
                    JOptionPane.showMessageDialog(findReplacePanel, "Cannot find \"" + needle + "\"",
                            "Find", JOptionPane.INFORMATION_MESSAGE);
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(findReplacePanel,
                        "Invalid regex:\n" + ex.getMessage(), "Find", JOptionPane.ERROR_MESSAGE);
            }
            updateFindCounter();
            return;
        }

        String hCmp = mc ? hay : hay.toLowerCase();
        String nCmp = mc ? needle : needle.toLowerCase();
        int idx = hCmp.lastIndexOf(nCmp, from);
        if (idx < 0) idx = hCmp.lastIndexOf(nCmp);
        if (idx >= 0) { textArea.select(idx, idx + needle.length()); textArea.requestFocusInWindow(); }
        else {
            JOptionPane.showMessageDialog(findReplacePanel, "Cannot find \"" + needle + "\"",
                    "Find", JOptionPane.INFORMATION_MESSAGE);
        }
        updateFindCounter();
    }

    private void replaceCurrent() {
        String needle = findField.getText();
        if (needle.isEmpty()) return;
        String selected = textArea.getSelectedText();
        if (selected == null) { findNext(); return; }
        boolean mc = matchCaseToggle.isSelected();
        boolean ok;
        if (regexToggle.isSelected()) {
            try { ok = selected.matches(mc ? needle : "(?i)" + needle); }
            catch (Exception e) { ok = false; }
        } else {
            ok = mc ? selected.equals(needle) : selected.equalsIgnoreCase(needle);
        }
        if (ok) textArea.replaceSelection(replaceField.getText());
        findNext();
    }

    private void replaceAll() {
        String needle = findField.getText();
        if (needle.isEmpty()) return;
        boolean mc = matchCaseToggle.isSelected();
        String regex = regexToggle.isSelected() ? needle : Pattern.quote(needle);
        if (!mc) regex = "(?i)" + regex;
        String repl = Matcher.quoteReplacement(replaceField.getText());
        try {
            String old = textArea.getText();
            String neu = old.replaceAll(regex, repl);
            if (old.equals(neu)) {
                JOptionPane.showMessageDialog(findReplacePanel, "Cannot find \"" + needle + "\"",
                        "Replace All", JOptionPane.INFORMATION_MESSAGE); return;
            }
            docLock = true;
            try { textArea.setText(neu); } finally { docLock = false; }
            isDirty = true;
            updateTitle();
            updateStatusBar();
            if (lineNumberView != null) { lineNumberView.revalidate(); lineNumberView.repaint(); }
            highlightSyntax();
            updateFindCounter();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(findReplacePanel, "Replace failed:\n" + ex.getMessage(),
                    "Replace All", JOptionPane.ERROR_MESSAGE);
        }
    }

    /* =======================================================================
     * 29. DOCUMENT / STATUS / ABOUT
     * ======================================================================= */

    private void documentChanged(boolean content) {
        if (docLock) return;
        try {
            if (content) {
                isDirty = true;
                updateTitle();
                if (showingWelcome) showEditorCard();
            }
            updateStatusBar();
            if (lineNumberView != null) { lineNumberView.revalidate(); lineNumberView.repaint(); }
            if (highlightTimer != null) highlightTimer.restart();
        } catch (Throwable ignored) { }
    }

    private void updateTitle() {
        frame.setTitle((isDirty ? "* " : "") + getDocumentName() + " — Luminary IDE");
    }

    private String getDocumentName() {
        return currentFile == null ? "Untitled" : currentFile.getName();
    }

    private void updateStatusBar() {
        String text;
        try { text = textArea.getText(); } catch (Exception e) { return; }
        if (text == null) text = "";
        int lineCount = getLineCount();
        int wordCount = text.trim().isEmpty() ? 0 : text.trim().split("\\s+").length;

        int caretLine = 1, caretCol = 1;
        try {
            int caret = textArea.getCaretPosition();
            caretLine = getLineOfOffset(caret) + 1;
            caretCol  = caret - getLineStartOffset(caretLine - 1) + 1;
        } catch (BadLocationException ignored) { }

        statLnCol.setText("  Ln " + caretLine + ", Col " + caretCol + "  ");
        String sel = textArea.getSelectedText();
        if (sel != null && !sel.isEmpty()) statSel.setText("  Sel " + sel.length() + "  ");
        else statSel.setText("");
        statLines.setText("  Lines " + lineCount + "  ");
        statWords.setText("  Words " + wordCount + "  ");
        statEnc.setText("  UTF-8  ");
        statTheme.setText("  " + theme.name + "  ");
        statusBar.revalidate(); statusBar.repaint();
    }

    private void showAboutDialog() {
        JDialog d = new JDialog(frame, "About Luminary IDE", true);
        try { d.setIconImages(getAppIcons()); } catch (Throwable ignored) { }
        d.setLayout(new BorderLayout());
        d.setSize(560, 560);
        d.setLocationRelativeTo(frame);

        JPanel main = new JPanel();
        main.setLayout(new BoxLayout(main, BoxLayout.Y_AXIS));
        main.setBorder(new EmptyBorder(20, 24, 16, 24));
        main.setBackground(theme.panelBg);

        JPanel hdr = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        hdr.setOpaque(false);
        hdr.setAlignmentX(Component.LEFT_ALIGNMENT);
        LogoMark mark = new LogoMark(theme);
        mark.setPreferredSize(new Dimension(44, 44));
        hdr.add(mark);
        JPanel ht = new JPanel();
        ht.setLayout(new BoxLayout(ht, BoxLayout.Y_AXIS));
        ht.setOpaque(false);
        JLabel title = new JLabel("Luminary IDE");
        title.setFont(uiFont(Font.BOLD, 18));
        title.setForeground(theme.fg);
        JLabel sub = new JLabel("Colorful Edition  ·  Single-file Swing editor");
        sub.setFont(uiFont(Font.PLAIN, 12));
        sub.setForeground(theme.textMuted);
        ht.add(title); ht.add(Box.createVerticalStrut(2)); ht.add(sub);
        hdr.add(ht);
        main.add(hdr);
        main.add(Box.createVerticalStrut(16));

        JSeparator sep = new JSeparator();
        sep.setForeground(theme.border);
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        sep.setAlignmentX(Component.LEFT_ALIGNMENT);
        main.add(sep);
        main.add(Box.createVerticalStrut(12));

        JTextPane content = new JTextPane();
        content.setEditable(false);
        content.setBackground(theme.panelBg);
        content.setBorder(null);
        StyledDocument doc = content.getStyledDocument();
        SimpleAttributeSet hd = new SimpleAttributeSet();
        StyleConstants.setBold(hd, true);
        StyleConstants.setForeground(hd, theme.fg);
        StyleConstants.setFontFamily(hd, UI_FAMILY);
        StyleConstants.setFontSize(hd, 11);
        SimpleAttributeSet bd = new SimpleAttributeSet();
        StyleConstants.setForeground(bd, theme.fg);
        StyleConstants.setFontFamily(bd, UI_FAMILY);
        StyleConstants.setFontSize(bd, 12);
        SimpleAttributeSet dim = new SimpleAttributeSet();
        StyleConstants.setForeground(dim, theme.textMuted);
        StyleConstants.setFontFamily(dim, UI_FAMILY);
        StyleConstants.setFontSize(dim, 12);

        try {
            doc.insertString(doc.getLength(), "SHORTCUTS\n", hd);
            appendRow(doc, bd, dim, "F5 / Ctrl+R",           "Run Code (Java, Python, JS)");
            appendRow(doc, bd, dim, "Shift+F5",              "Stop Running Process");
            appendRow(doc, bd, dim, "Ctrl+`",                "Toggle Output Terminal");
            appendRow(doc, bd, dim, "Ctrl+N / Ctrl+Shift+N", "New / New Window");
            appendRow(doc, bd, dim, "Ctrl+O / Ctrl+S",       "Open / Save");
            appendRow(doc, bd, dim, "Ctrl+Shift+S",          "Save As");
            appendRow(doc, bd, dim, "Ctrl+Z / Ctrl+Y",       "Undo / Redo");
            appendRow(doc, bd, dim, "Ctrl+X / C / V",        "Cut / Copy / Paste");
            appendRow(doc, bd, dim, "Ctrl+A / Ctrl+D",       "Select All / Duplicate Line");
            appendRow(doc, bd, dim, "Alt+Up / Alt+Down",     "Move Line Up / Down");
            appendRow(doc, bd, dim, "Ctrl+/",                "Toggle Line Comment");
            appendRow(doc, bd, dim, "Ctrl+G",                "Go To Line");
            appendRow(doc, bd, dim, "Ctrl+F / Ctrl+H",       "Find / Replace");
            appendRow(doc, bd, dim, "Ctrl+ + / - / 0",       "Zoom In / Out / Reset");
            appendRow(doc, bd, dim, "Ctrl+Shift+D",          "Insert Date & Time");
            appendRow(doc, bd, dim, "Ctrl+Shift+I",          "Insert Image Asset");
            doc.insertString(doc.getLength(), "\n", bd);
            doc.insertString(doc.getLength(), "FEATURES\n", hd);
            appendBullet(doc, bd, "Integrated Code Runner (compile & run Java, Python, Node.js with real-time stdout, stderr & stdin)");
            appendBullet(doc, bd, "Full VS Code-caliber colorful syntax engine (methods, types, control flow, keywords, constants, operators)");
            appendBullet(doc, bd, "Interactive mouse drag & hover highlighting on buttons, toolbar tools, and menu items");
            appendBullet(doc, bd, "12 high-contrast themes (Light, Dark, Ocean, Sunset, Forest, Dracula, Nord, Monokai, Cyberpunk, Solarized, Rosé Pine)");
            appendBullet(doc, bd, "No-wrap editor with line numbers, current-line highlight, rainbow brackets, auto-indent, auto-close");
            appendBullet(doc, bd, "Login with strong password policy + PBKDF2 hashing");
            appendBullet(doc, bd, "Find & Replace with regex, match case, live match count");
            doc.insertString(doc.getLength(), "\n", bd);
            doc.insertString(doc.getLength(), "DEVELOPER\n", hd);
            doc.insertString(doc.getLength(), "  Developed by Hari Khatri (BCA 3rd Semester)\n", bd);
            doc.insertString(doc.getLength(), "  Adarsh College of Professional Studies, Aburoad\n", dim);
            doc.insertString(doc.getLength(), "  Contact: hariacharyakhatri@gmail.com\n", dim);
            doc.insertString(doc.getLength(), "  GitHub: https://github.com/HariAcharyaKhatri/LuminaryIDE\n\n", dim);
            doc.insertString(doc.getLength(), "© 2026 Hari Khatri · Made with Java Swing\n", dim);
        } catch (BadLocationException ignored) { }

        JScrollPane sp = new JScrollPane(content);
        sp.setBorder(null);
        sp.setOpaque(false);
        sp.getViewport().setOpaque(false);
        installModernScrollBars(sp);
        sp.setAlignmentX(Component.LEFT_ALIGNMENT);
        main.add(sp);

        main.add(Box.createVerticalStrut(12));
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        footer.setOpaque(false);
        footer.setAlignmentX(Component.LEFT_ALIGNMENT);
        JButton ok = primaryButton(IDEIcons.CLOSE, "Close");
        ok.addActionListener(e -> d.dispose());
        footer.add(ok);
        main.add(footer);

        d.add(main, BorderLayout.CENTER);
        d.getRootPane().setDefaultButton(ok);
        d.getRootPane().registerKeyboardAction(e -> d.dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        d.setVisible(true);
    }

    private void appendRow(StyledDocument doc, AttributeSet bd, AttributeSet dim,
                           String key, String desc) throws BadLocationException {
        SimpleAttributeSet k = new SimpleAttributeSet(bd);
        StyleConstants.setFontFamily(k, MONO_FAMILY);
        doc.insertString(doc.getLength(), "  " + pad(key, 22), k);
        doc.insertString(doc.getLength(), desc + "\n", dim);
    }

    private void appendBullet(StyledDocument doc, AttributeSet bd, String text)
            throws BadLocationException {
        doc.insertString(doc.getLength(), "  • " + text + "\n", bd);
    }

    private static String pad(String s, int len) {
        if (s.length() >= len) return s;
        StringBuilder sb = new StringBuilder(s);
        while (sb.length() < len) sb.append(' ');
        return sb.toString();
    }

    private JButton primaryButton(Icon icon, String text) {
        JButton b = primaryButton(text);
        if (icon != null) {
            b.setIcon(icon);
            b.setIconTextGap(6);
        }
        return b;
    }

    private JButton primaryButton(String text) {
        JButton b = new JButton(text);
        b.setFont(uiFont(Font.BOLD, 12));
        b.setBackground(theme.accent);
        b.setForeground(contrast(theme.accent));
        b.setBorder(new EmptyBorder(7, 18, 7, 18));
        b.setFocusPainted(false);
        b.setBorderPainted(false);
        b.setOpaque(true);
        b.setContentAreaFilled(true);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        installSmoothHover(b, () -> theme.accent, () -> brighten(theme.accent, 0.12f));
        return b;
    }

    private JButton secondaryButton(Icon icon, String text) {
        JButton b = secondaryButton(text);
        if (icon != null) {
            b.setIcon(icon);
            b.setIconTextGap(6);
        }
        return b;
    }

    private JButton secondaryButton(String text) {
        JButton b = new JButton(text);
        b.setFont(uiFont(Font.PLAIN, 12));
        b.setBackground(theme.panelBg);
        b.setForeground(theme.dark ? Color.WHITE : Color.BLACK);
        b.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(theme.border),
                new EmptyBorder(6, 16, 6, 16)));
        b.setFocusPainted(false);
        b.setOpaque(true);
        b.setContentAreaFilled(true);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        installSmoothHover(b, () -> theme.panelBg, () -> theme.hover);
        return b;
    }

    /* =======================================================================
     * 30. THEME APPLICATION
     * ======================================================================= */

    private void applyTheme(Theme t) {
        this.theme = t;
        prefs.put(PREF_THEME_NAME, t.name);

        installUIManagerDefaults(t);

        textArea.setBackground(t.bg);
        textArea.setForeground(t.fg);
        textArea.setCaretColor(t.accent);
        textArea.setSelectionColor(t.selection);
        textArea.setSelectedTextColor(t.selectionFg);

        MutableAttributeSet inputAttrs = textArea.getInputAttributes();
        if (inputAttrs != null) {
            inputAttrs.removeAttributes(inputAttrs);
            StyleConstants.setForeground(inputAttrs, t.fg);
            StyleConstants.setFontFamily(inputAttrs, fontFamily);
            StyleConstants.setFontSize(inputAttrs, fontSize);
        }

        StyledDocument doc = textArea.getStyledDocument();
        if (doc != null && doc.getLength() > 0) {
            SimpleAttributeSet baseAttrs = new SimpleAttributeSet();
            StyleConstants.setForeground(baseAttrs, t.fg);
            StyleConstants.setFontFamily(baseAttrs, fontFamily);
            StyleConstants.setFontSize(baseAttrs, fontSize);
            doc.setCharacterAttributes(0, doc.getLength(), baseAttrs, false);
        }

        rebuildStyles();
        if (lineNumberView != null) lineNumberView.applyTheme(t);

        scrollPane.getViewport().setBackground(t.bg);
        scrollPane.setBackground(t.bg);
        if (editorSplitPane != null) editorSplitPane.setBackground(t.panelBg);
        editorCard.setBackground(t.bg);
        cardPanel.setBackground(t.bg);

        if (menuBar != null) {
            menuBar.setTheme(t);
            for (int i = 0; i < menuBar.getMenuCount(); i++) {
                JMenu m = menuBar.getMenu(i);
                if (m != null) styleMenuComponent(m, t);
            }
            if (signOutBtn != null) {
                signOutBtn.setBackground(t.chromeBg);
                signOutBtn.setForeground(t.fg);
            }
            menuBar.revalidate();
            menuBar.repaint();
        }

        applyToolbarTheme(t);
        if (consolePanel != null) consolePanel.applyTheme(t);

        statusBar.setBackground(t.chromeBg);
        statusBar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, t.border));
        if (statusBarLeft != null) statusBarLeft.setBackground(t.chromeBg);
        if (statusBarRight != null) statusBarRight.setBackground(t.chromeBg);
        for (JLabel l : new JLabel[]{ statLnCol, statSel, statLines, statWords, statEnc, statTheme, statUser, statLang, statTerminal }) {
            if (l == null) continue;
            l.setForeground(t.textMuted);
            l.setBackground(t.chromeBg);
        }
        if (statTheme != null) {
            statTheme.setIcon(IDEIcons.THEME);
            statTheme.setText(" " + t.name);
        }
        if (statLang != null) {
            statLang.setIcon(IDEIcons.forLanguage(getEffectiveLanguage()));
            statLang.setText(" " + getEffectiveLanguage());
        }

        frame.getContentPane().setBackground(t.bg);

        for (int i = 0; i < THEMES.size(); i++)
            if (THEMES.get(i) == t) { themeItems[i].setSelected(true); break; }

        if (themeCombo != null && !t.name.equals(themeCombo.getSelectedItem())) {
            syncingThemeCombo = true;
            themeCombo.setSelectedItem(t.name);
            syncingThemeCombo = false;
        }

        if (langCombo != null) {
            langCombo.setBackground(t.bg);
            langCombo.setForeground(t.fg);
        }

        if (welcomePanel != null) welcomePanel.applyTheme(t);
        if (loginPanel != null)   loginPanel.applyTheme(t);
        styleFindPanel(t);

        highlightSyntax();
        updateCurrentLineHighlight();
        updateStatusBar();
        if (lineNumberView != null) lineNumberView.repaint();
        frame.repaint();
    }

    private void styleMenuComponent(Component comp, Theme t) {
        if (comp instanceof JMenu) {
            JMenu m = (JMenu) comp;
            m.setForeground(t.fg);
            m.setBackground(t.chromeBg);
            m.setOpaque(true);
            JPopupMenu pop = m.getPopupMenu();
            pop.setBackground(t.panelBg);
            pop.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(t.border),
                    new EmptyBorder(4, 0, 4, 0)));
            for (Component sub : m.getMenuComponents()) styleMenuComponent(sub, t);
        } else if (comp instanceof JMenuItem) {
            JMenuItem mi = (JMenuItem) comp;
            mi.setForeground(t.fg);
            mi.setBackground(t.panelBg);
            mi.setOpaque(true);
            mi.setBorder(new EmptyBorder(5, 12, 5, 12));
        } else if (comp instanceof JComponent) {
            JComponent jc = (JComponent) comp;
            jc.setForeground(t.fg);
            jc.setBackground(t.panelBg);
            jc.setOpaque(true);
            for (Component sub : jc.getComponents()) styleMenuComponent(sub, t);
        }
    }

    /* =======================================================================
     * 31. CUSTOM SCROLLBAR UI
     * ======================================================================= */

    private void installModernScrollBars(JScrollPane sp) {
        try {
            sp.getVerticalScrollBar().setUI(new ModernScrollBarUI(this));
            sp.getHorizontalScrollBar().setUI(new ModernScrollBarUI(this));
            sp.getVerticalScrollBar().setPreferredSize(new Dimension(10, 10));
            sp.getHorizontalScrollBar().setPreferredSize(new Dimension(10, 10));
            sp.getVerticalScrollBar().setUnitIncrement(24);
            sp.getHorizontalScrollBar().setUnitIncrement(24);
        } catch (Throwable ignored) { }
    }

    static class ModernScrollBarUI extends BasicScrollBarUI {
        private final LuminaryIDE ed;
        ModernScrollBarUI(LuminaryIDE ed) { this.ed = ed; }

        @Override protected JButton createDecreaseButton(int o) { return zeroButton(); }
        @Override protected JButton createIncreaseButton(int o) { return zeroButton(); }

        private JButton zeroButton() {
            JButton b = new JButton();
            b.setPreferredSize(new Dimension(0, 0));
            b.setMinimumSize(new Dimension(0, 0));
            b.setMaximumSize(new Dimension(0, 0));
            b.setFocusable(false);
            return b;
        }

        @Override protected void paintTrack(Graphics g, JComponent c, Rectangle r) {
            g.setColor(ed.theme.bg);
            g.fillRect(r.x, r.y, r.width, r.height);
        }

        @Override protected void paintThumb(Graphics g, JComponent c, Rectangle r) {
            if (r.width <= 0 || r.height <= 0) return;
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color thumb;
                if (isDragging) thumb = ed.theme.accent;
                else if (isThumbRollover()) thumb = ed.theme.textMuted;
                else thumb = mix(ed.theme.border, ed.theme.bg, 0.25f);
                g2.setColor(thumb);
                int thickness = 6;
                if (scrollbar.getOrientation() == Adjustable.VERTICAL) {
                    int x = r.x + (r.width - thickness) / 2;
                    g2.fillRoundRect(x, r.y + 2, thickness, Math.max(4, r.height - 4), thickness, thickness);
                } else {
                    int y = r.y + (r.height - thickness) / 2;
                    g2.fillRoundRect(r.x + 2, y, Math.max(4, r.width - 4), thickness, thickness, thickness);
                }
            } finally { g2.dispose(); }
        }

        @Override protected Dimension getMinimumThumbSize() { return new Dimension(10, 40); }
    }

    /* =======================================================================
     * 32. LINE NUMBER GUTTER
     * ======================================================================= */

    static class LineNumberView extends JComponent {
        private final LuminaryIDE editor;
        private final JTextPane area;

        private final int margin = 6;

        private Theme theme;

        LineNumberView(LuminaryIDE editor, JTextPane area) {
            this.editor = editor;
            this.area = area;
            setFont(area.getFont());
        }

        void applyTheme(Theme t) {
            this.theme = t;
            setBackground(t.bg);
            repaint();
        }

        @Override public Dimension getPreferredSize() {
            int lines = Math.max(lineCount(), 1);
            int digits = Math.max(3, String.valueOf(lines).length());
            int w = getFontMetrics(getFont()).charWidth('0') * digits + 2 * margin;
            return new Dimension(w + 1, area.getHeight());
        }

        private int lineCount() {
            return area.getDocument().getDefaultRootElement().getElementCount();
        }

        private int lineStart(int line) throws BadLocationException {
            Element root = area.getDocument().getDefaultRootElement();
            if (line < 0 || line >= root.getElementCount())
                throw new BadLocationException("bad line", line);
            return root.getElement(line).getStartOffset();
        }

        private int lineOf(int offset) {
            return area.getDocument().getDefaultRootElement().getElementIndex(offset);
        }

        @Override protected void paintComponent(Graphics g) {
            if (theme == null) return;
            Rectangle clip = g.getClipBounds();
            g.setColor(theme.gutterBg);
            g.fillRect(clip.x, clip.y, clip.width, clip.height);

            g.setColor(theme.border);
            g.drawLine(getWidth() - 1, clip.y, getWidth() - 1, clip.y + clip.height);

            FontMetrics fm = g.getFontMetrics(getFont());
            int caretLine = lineOf(area.getCaretPosition());

            try {
                int startOffset = area.viewToModel2D(new Point2D.Double(0, clip.y));
                int endOffset   = area.viewToModel2D(new Point2D.Double(0, clip.y + clip.height));
                if (startOffset < 0 || endOffset < 0) return;
                int startLine = lineOf(startOffset), endLine = lineOf(endOffset);

                if (caretLine >= startLine && caretLine <= endLine) {
                    int ls = lineStart(caretLine);
                    Rectangle2D r = area.modelToView2D(ls);
                    if (r != null) {
                        g.setColor(theme.gutterCurrent);
                        g.fillRect(0, (int) r.getY(), getWidth() - 1, (int) r.getHeight());
                    }
                }

                for (int line = startLine; line <= endLine; line++) {
                    int ls = lineStart(line);
                    Rectangle2D r = area.modelToView2D(ls);
                    if (r == null) continue;
                    String num = String.valueOf(line + 1);
                    int x = getWidth() - margin - fm.stringWidth(num);
                    int y = (int) r.getY() + fm.getAscent();
                    if (line == caretLine) {
                        g.setColor(theme.gutterFgCur);
                        g.setFont(getFont().deriveFont(Font.BOLD));
                    } else {
                        g.setColor(theme.gutterFg);
                        g.setFont(getFont().deriveFont(Font.PLAIN));
                    }
                    g.drawString(num, x, y);
                }
            } catch (BadLocationException ignored) { }
        }
    }

    /* =======================================================================
     * 33. MAIN
     * ======================================================================= */

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            File init = null;
            if (args != null && args.length > 0) {
                File f = new File(args[0]);
                if (f.exists() && f.isFile()) init = f;
            }
            new LuminaryIDE(init);
        });
    }
}
